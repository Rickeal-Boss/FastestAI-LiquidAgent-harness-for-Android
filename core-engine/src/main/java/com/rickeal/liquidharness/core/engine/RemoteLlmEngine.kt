package com.rickeal.liquidharness.core.engine

import com.rickeal.liquidharness.core.engine.provider.AdapterStreamState
import com.rickeal.liquidharness.core.engine.provider.ProviderAdapter
import com.rickeal.liquidharness.core.engine.provider.looksLikeContextOverflowMessage
import com.rickeal.liquidharness.core.model.AgentJson
import com.rickeal.liquidharness.core.model.EngineCapabilities
import com.rickeal.liquidharness.core.model.EngineErrorKind
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.SecretRedactor
import com.rickeal.liquidharness.core.model.TokenUsage
import com.rickeal.liquidharness.core.net.HttpTransport
import com.rickeal.liquidharness.core.net.SseEventParser
import com.rickeal.liquidharness.core.net.SseStream
import com.rickeal.liquidharness.core.net.UrlGuard
import com.rickeal.liquidharness.core.net.UrlVerdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonObject
import java.io.IOException

/**
 * 远程 LLM 引擎。
 *
 * ## 与内核端口的关系（W1 过渡态）
 *
 * `LlmEngine` 接口由 `core-agent` 定义（`agent/engine/LlmEngine.kt`），
 * 依赖方向是 `core-agent ← core-engine`（00-brief A2 拓扑），所以本模块**不能**
 * import 它 —— 那会变成反向依赖。因此 W1 先落成：
 *
 * ```kotlin
 * class RemoteLlmEngine(...) {
 *     val endpointId: String
 *     suspend fun capabilities(): EngineCapabilities
 *     fun generate(request: GenerationRequest): Flow<GenerationEvent>
 *     suspend fun stop()
 * }
 * ```
 *
 * **W1 集成时改为 implements core-agent 的 `LlmEngine`** —— 签名已按
 * `docs/01-architecture.md` §3.1 逐字对齐（成员名、参数类型、返回类型一致），
 * 届时只需在类声明上加 `: LlmEngine` 与 `override`，不需要改任何调用点。
 *
 * ## 取消语义（硬契约，§3.1 四条）
 *
 * 1. 取消 collect ⇒ `finally` 关流 ⇒ 底层 `call.cancel()`（不依赖协程恢复）；
 * 2. 已 emit 的 TextDelta 由调用方保留（用于 `Cancelled(partialText)`）；
 * 3. 已完整收到但未执行的 ToolCall **不得**被执行 —— 这是内核的责任，但引擎
 *    保证取消后不再 emit 任何 `ToolCallDelta`；
 * 4. 取消幂等：对已结束的流再 `stop()` 是 no-op（`SseStream.close()` 用 CAS 挡重复）。
 *
 * ## 重试的边界（W24-02 修订，取代「首字节后一律不重试」）
 *
 * - **引擎内**只在尚未 emit 任何内容时重试（否则用户会看到两段拼接的回答）；
 * - **首字节后截断**不再落成「带 LENGTH 的完整回答」：转成
 *   `Failed(retryable=true, truncatedStream=true)`，由**内核**整段回滚
 *   （`RunState.truncateText` + `AgentEvent.StreamReset`）后重试一次 —— 回滚权只在内核手里
 *   （只有它持有跨 attempt 的文本缓冲），引擎不做第二套拼接 / 回滚；
 * - `Retry-After` 优先于本地退避，上限 60s；
 * - `CONTEXT_OVERFLOW` 不在这里重试 —— 正确动作是内核压缩后重发，引擎重发同样长度必然再溢出。
 */
class RemoteLlmEngine(
    private val endpoint: RemoteEndpoint,
    private val adapter: ProviderAdapter,
    private val transport: HttpTransport,
    private val secrets: SecretProvider = SecretProvider.None,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {

    /** 端点标识（= `RemoteEndpoint.id`）。用于日志与 UI 归属，不参与路由。 */
    val endpointId: String get() = endpoint.id

    /**
     * 当前在途流。`@Volatile`：请求跑在 IO 线程，`stop()` 可能来自主线程。
     * 不持有 `Call` 而是 `SseStream` —— 关流要同时关响应体与取消 call，缺一个会留下僵尸连接。
     */
    @Volatile
    private var activeStream: SseStream? = null

    /** `stop()` 置位，用于把「用户主动停止」与「网络真断了」区分开（两者都是 IOException）。 */
    @Volatile
    private var stopRequested: Boolean = false

    /** 能力声明。**不发网络请求**：由端点配置派生，设置页可以随时调。 */
    suspend fun capabilities(): EngineCapabilities = EngineCapabilities(
        supportsTools = endpoint.supportsTools,
        supportsReasoning = endpoint.supportsReasoning,
        // W8：不再硬编码 false（那是「实现已发图、声明却说不支持」的能力声明打架，台账 W3-07）。
        // 真实闸门在 AppNavHost 的图片入口（`supportsVision` 为 false 时保留禁用态入口 + 原因提示）。
        supportsVision = endpoint.supportsVision,
        nativeToolChannel = endpoint.supportsTools,
        maxContextTokens = endpoint.contextLength,
        maxOutputTokens = endpoint.maxOutputTokens,
        label = endpoint.name,
    )

    /**
     * 冷流：collect 才开始发请求。
     *
     * 阻塞 IO 跑在 [Dispatchers.IO]（OkHttp `execute()` 是阻塞的），
     * 所以 `flowOn` 放在最后 —— 它只影响上游，不影响 collector 的上下文。
     */
    fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        stopRequested = false
        // 请求自带的 endpoint 可能来自上层拼装（baseUrl 未填）；以实例端点为权威。
        val effective = if (request.endpoint.baseUrl.isBlank()) request.copy(endpoint = endpoint) else request
        val target = effective.endpoint

        val url = adapter.endpointUrl(target)
        if (url.isBlank()) {
            emit(configFailure("endpoint base URL is empty"))
            return@flow
        }
        when (val verdict = UrlGuard.check(url, target.allowPrivateNetwork)) {
            is UrlVerdict.Rejected -> {
                emit(configFailure("endpoint URL rejected: ${verdict.reason}"))
                return@flow
            }
            is UrlVerdict.Allowed -> Unit
        }

        val secret = secrets.secretFor(target.id)
        if (target.requiresApiKey && secret.isNullOrBlank()) {
            emit(
                GenerationEvent.Failed(
                    EngineErrorKind.AUTH, "missing API key for endpoint", false, evidenceTag = "engine:config",
                ),
            )
            return@flow
        }

        val body = AgentJson.json.encodeToString(JsonObject.serializer(), adapter.buildBody(effective))
        val headers = adapter.headers(target, secret)

        var attempt = 0
        var emittedContent = false
        while (true) {
            val outcome = runAttempt(url, headers, body, target.allowPrivateNetwork)
            // 「已 emit 过内容」的判据必须含工具碎片：工具优先的模型只吐 tool_calls 时
            // textChars 恒 0，只看它就会把「已吐过碎片的流」判成可重试（外审 P0-3）。
            emittedContent = emittedContent || outcome.state.hasEmittedAny
            when (outcome) {
                is AttemptOutcome.Completed -> return@flow
                is AttemptOutcome.Fatal -> {
                    emit(outcome.failure)
                    return@flow
                }
                is AttemptOutcome.Retryable -> {
                    if (!retryPolicy.shouldRetry(attempt, outcome.failure.kind, emittedContent)) {
                        emit(outcome.failure)
                        return@flow
                    }
                    delay(retryPolicy.backoffMillis(attempt, outcome.retryAfterMillis))
                    attempt++
                }
            }
        }
    }.flowOn(Dispatchers.IO).cancellable()

    /**
     * 主动停止。等价于取消当前流；无在途流时 no-op。
     *
     * **必须**主动 `cancel()` 而不是只等协程取消：`stop()` 是显式调用，
     * 若只关响应体，阻塞中的 `read()` 会一直等到 60s readTimeout 才返回，
     * 用户点「停止」表现为毫无反应（AGENTS.md §五.5）。
     */
    suspend fun stop() {
        stopRequested = true
        activeStream?.close()
    }

    // ------------------------------------------------------------------ 单次尝试

    /** 一次尝试的结果。带 [AdapterStreamState] 是为了让上层判断「这次到底产出了什么」。 */
    private sealed interface AttemptOutcome {
        val state: AdapterStreamState

        data class Completed(override val state: AdapterStreamState) : AttemptOutcome

        data class Retryable(
            override val state: AdapterStreamState,
            val failure: GenerationEvent.Failed,
            val retryAfterMillis: Long?,
        ) : AttemptOutcome

        data class Fatal(
            override val state: AdapterStreamState,
            val failure: GenerationEvent.Failed,
        ) : AttemptOutcome
    }

    private suspend fun FlowCollector<GenerationEvent>.runAttempt(
        url: String,
        headers: Map<String, String>,
        body: String,
        allowPrivateNetwork: Boolean,
    ): AttemptOutcome {
        val state = AdapterStreamState()
        var stream: SseStream? = null
        try {
            // 把端点的内网许可透传给传输层：`GuardedDns` 用它决定解析出的私网 IP 是否放行
            // （DNS rebinding 的第二道闸；第一道是 generate() 里的 UrlGuard.check）。
            stream = transport.post(url, headers, body, allowPrivateNetwork)
            activeStream = stream

            if (!stream.isSuccessful) {
                // 错误体先脱敏再截断：它会被写进日志与诊断卡（AGENTS.md §六「先脱敏再判 400」）。
                val detail = SecretRedactor.redact(stream.errorBodyText())
                val failure = classifyHttpFailure(stream.statusCode, detail)
                return if (retryPolicy.isRetryable(failure.kind)) {
                    AttemptOutcome.Retryable(state, failure, stream.retryAfterMillis)
                } else {
                    AttemptOutcome.Fatal(state, failure)
                }
            }

            // byteStream() 而不是 charStream()：字符流会在 chunk 边界上解码，中文必坏。
            stream.byteStream().use { input ->
                for (event in SseEventParser.parse(input, closeOnDone = true)) {
                    if (adapter.isTerminal(event)) {
                        state.sawTerminalMarker = true
                        break
                    }
                    // W24-02：错误帧先于 decodeEvent 判定。命中即收尾，**不经过 adapter.finish**
                    // （它会在下面再补一个 Finished ⇒ 双终态）。`finishReason != null` 时忽略：
                    // 供应商在正常收尾后追打的错误帧不得改判已定的结论（E6）。
                    // W40（P0-3）：已吐内容后供应商掐流发 error 帧（限流 / 拥堵）与网络截断
                    // **同构** —— 已产出半截、重发都会从头再答 ⇒ 整段回滚后重试一次是正确处置。
                    // 置 truncatedStream 让内核 prepareRetry 走回滚分支；零产出不置
                    // （无内容可回滚，走原有「零产出可重试」通路，语义不变）。
                    if (state.finishReason == null) {
                        val failure = adapter.decodeFailure(event)
                        if (failure != null) {
                            emitFlushed(state)
                            val truncated = state.hasEmittedAny && failure.retryable
                            return if (failure.retryable) {
                                AttemptOutcome.Retryable(state, failure.copy(truncatedStream = truncated), null)
                            } else {
                                AttemptOutcome.Fatal(state, failure)
                            }
                        }
                    }
                    for (generationEvent in adapter.decodeEvent(event)) {
                        // 先归一化再 emit：累积式 provider 的重复帧在这里被吸收，
                        // 下游（内核 / UI）只会看到真实增量（R19 的接线点）。
                        val normalized = state.normalize(generationEvent) ?: continue
                        track(state, normalized)
                        emit(normalized)
                    }
                }
            }

            // 两击确认会延迟一帧：流结束前必须补出待决帧，否则末帧永久丢失。
            // 位置在 adapter.finish() **之前** —— 终帧裁决要用补发后的 textChars。
            emitFlushed(state)

            // ① 零事件（HTTP 200 但一帧 SSE 都没解析出来）= 端点不是 SSE（HTML 错误页 /
            //    网关改写的 JSON）。保持 PROTOCOL 且**不可重试**：重发只会再拿一份 HTML，
            //    而探针（`EndpointProbeTest.htmlPageReturnedWith200FailsTheProbe`）靠这条判红。
            if (state.eventCount == 0 && !state.hasEmittedAny) {
                return AttemptOutcome.Fatal(
                    state,
                    GenerationEvent.Failed(
                        EngineErrorKind.PROTOCOL,
                        "stream ended without any event (endpoint is not SSE?)",
                        retryable = false,
                        evidenceTag = "engine:no-events",
                    ),
                )
            }

            // 供应商没给 finish_reason ⇒ 补终帧。这条 if 是「半截回答不被当完整回答落库」
            // 的唯一防线（AGENTS.md §五.6）。
            if (state.finishReason == null) {
                // ② 有产出、却既没 finish_reason 也没结束标记 ⇒ 连接被掐断（W24-02）：
                //    **不补 Finished(LENGTH)**，改成可重试的截断失败，交内核整段回滚后重试一次。
                //    判据用 hasEmittedAny（含工具碎片）：只吐 tool_calls 的流同样不许叠加。
                if (state.hasEmittedAny && !state.sawTerminalMarker) {
                    return AttemptOutcome.Retryable(
                        state = state,
                        failure = GenerationEvent.Failed(
                            kind = EngineErrorKind.NETWORK,
                            message = "stream truncated before completion",
                            retryable = true,
                            truncatedStream = true,
                            evidenceTag = "engine:truncated",
                        ),
                        retryAfterMillis = null,
                    )
                }
                val terminal = adapter.finish(state)
                state.finishReason = terminal.reason
                emit(terminal)
            }
            return AttemptOutcome.Completed(state)
        } catch (c: CancellationException) {
            // 协程取消必须原样上抛，否则 Job 会从 cancelled 变 completed（AGENTS.md §五.4）。
            throw c
        } catch (io: IOException) {
            if (stopRequested) {
                // 用户主动停止：不能当成错误上报，否则「用户停止」会显示成「出错了」。
                emitFlushed(state)
                if (state.finishReason == null) emit(GenerationEvent.Finished(FinishReason.CANCELLED))
                return AttemptOutcome.Completed(state)
            }
            // 先补出待决帧再判「有没有产出」：两击确认会把第二帧暂存，
            // 只收一帧就被掐断时 `hasEmittedAny` 仍为 true（首帧已 emit），但
            // 「只收到一帧 + 砍在第二帧」的形态必须先把待决帧补出来，否则它永久丢失。
            emitFlushed(state)
            if (state.hasEmittedAny) {
                // 首字节后中断（Content-Length 提前断 / RST）：**引擎不重发**（会重复输出），
                // 但也不许当完整回答（W24-02）：报 truncatedStream，由内核整段回滚后重试一次。
                // 判据含工具碎片：只有 tool_calls 碎片、没有正文的流同样不许叠加。
                return AttemptOutcome.Retryable(
                    state = state,
                    failure = GenerationEvent.Failed(
                        kind = EngineErrorKind.NETWORK,
                        message = "network error after partial output: " + io.javaClass.simpleName,
                        retryable = true,
                        truncatedStream = true,
                        evidenceTag = "engine:truncated",
                    ),
                    retryAfterMillis = null,
                )
            }
            return AttemptOutcome.Retryable(
                state = state,
                failure = GenerationEvent.Failed(
                    kind = EngineErrorKind.NETWORK,
                    message = "network error: " + io.javaClass.simpleName,
                    retryable = true,
                    evidenceTag = "engine:network",
                ),
                retryAfterMillis = null,
            )
        } finally {
            // 只清理自己这一个句柄：避免并发两条流时误清新流的引用。
            if (activeStream === stream) activeStream = null
            stream?.close()
        }
    }

    /**
     * 补出因「两击确认」而暂缓的帧（[AdapterStreamState.flushPending]）。
     *
     * 必须先 [track] 再 emit：`track` 累计 `textChars`，而终帧裁决
     * （`terminationReason`）用 `textChars` 判「有没有内容」—— 漏记会把
     * 「第二帧后就被掐断」的流判成 `STOP`（半截回答被当完整回答落库）。
     */
    private suspend fun FlowCollector<GenerationEvent>.emitFlushed(state: AdapterStreamState) {
        for (event in state.flushPending()) {
            track(state, event)
            emit(event)
        }
    }

    /**
     * 把事件回填进流状态。
     *
     * `textChars` 同时累计正文与思考通道：截断判定关心的是「模型有没有产出过东西」，
     * 只思考不正文的流被掐断同样不该被当成正常结束。
     */
    private fun track(state: AdapterStreamState, event: GenerationEvent) {
        state.eventCount++
        when (event) {
            is GenerationEvent.TextDelta -> state.textChars += event.text.length
            is GenerationEvent.ReasoningDelta -> state.textChars += event.text.length
            is GenerationEvent.Finished -> state.finishReason = event.reason
            is GenerationEvent.Usage -> state.usage = TokenUsage(
                prompt = event.prompt,
                completion = event.completion,
                total = event.prompt + event.completion,
                cachedPromptTokens = event.cachedPrompt,
                cacheWriteTokens = event.cacheWrite,
            )
            is GenerationEvent.ToolCallDelta -> Unit
            is GenerationEvent.Failed -> Unit
        }
    }

    // ------------------------------------------------------------------ 错误分类

    private fun configFailure(detail: String): GenerationEvent.Failed =
        GenerationEvent.Failed(EngineErrorKind.CONFIG, detail, false, evidenceTag = "engine:config")

    /**
     * HTTP 状态码 → 错误类。分类决定「重试 / 改配置 / 压缩后重发 / 直接报错」。
     *
     * 所有 message 都是英文常量，由 UI 层映射为本地化文案（守卫 N3 基线 0）。
     *
     * W48 判序范式：**HTTP status 是唯一权威**（429/401/403/408/5xx 不看正文措辞）；
     * 只有 400 走 message 词表兜底（`looksLikeContextOverflow`，词表冻结见
     * `OpenAiErrorFrame` W48 段）。每支携带 `http:*` 证据标签供真机诊断。
     */
    private fun classifyHttpFailure(status: Int, bodyText: String): GenerationEvent.Failed = when {
        status == 401 || status == 403 ->
            GenerationEvent.Failed(
                EngineErrorKind.AUTH, "authentication failed (HTTP $status)", false, evidenceTag = "http:auth",
            )

        status == 429 ->
            GenerationEvent.Failed(
                EngineErrorKind.RATE_LIMIT, "rate limited (HTTP $status)", true, evidenceTag = "http:rate",
            )

        status == 408 ->
            GenerationEvent.Failed(
                EngineErrorKind.NETWORK, "request timeout (HTTP $status)", true, evidenceTag = "http:timeout",
            )

        status == 400 && looksLikeContextOverflow(bodyText) ->
            // 顺手把服务端回报的真实窗口带出去（W5-02 的学习式写回通道）。
            // 只有这一条分支解析：`contextLimitHint` 的匹配串本身就含 overflow 措辞，
            // 在别的分支上它必然返回 null —— 放在这里是为了让「为什么会有这个数」可读。
            GenerationEvent.Failed(
                EngineErrorKind.CONTEXT_OVERFLOW,
                "context window exceeded",
                false,
                contextLimitHint = adapter.contextLimitHint(bodyText),
                evidenceTag = "http:overflow",
            )

        status >= 500 ->
            GenerationEvent.Failed(
                EngineErrorKind.SERVER, "server error (HTTP $status)", true, evidenceTag = "http:server",
            )

        else ->
            GenerationEvent.Failed(
                EngineErrorKind.PROTOCOL, "request rejected (HTTP $status)", false, evidenceTag = "http:rejected",
            )
    }

    /**
     * 400 的文案是否指向「上下文超窗」。
     *
     * 只看关键词组合，不解析具体格式：三家的错误体格式完全不同，
     * 而且都可能在版本间改措辞。判错的代价是「该压缩时没压缩」（用户看到一次失败）
     * 或「不该压缩时压缩了」（prompt cache 失效，比前者的代价大）——
     * 所以要求「context/token」与「超限词」**同时**命中，宁可漏判。
     */
    private fun looksLikeContextOverflow(bodyText: String): Boolean {
        if (bodyText.isEmpty()) return false
        // 词表只定义一处（`provider/OpenAiErrorFrame.kt`）：HTTP 400 与「流内错误帧」是同一族
        // 文案的两条入口，各写一份必然漂移（W24-02）。
        return looksLikeContextOverflowMessage(bodyText.lowercase())
    }
}
