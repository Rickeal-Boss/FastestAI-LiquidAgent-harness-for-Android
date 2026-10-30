package com.rickeal.liquidharness.core.engine

import com.rickeal.liquidharness.core.engine.provider.AdapterStreamState
import com.rickeal.liquidharness.core.engine.provider.ProviderAdapter
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
 * ## 重试的三条边界
 *
 * - 只在**尚未 emit 任何内容**时重试（否则用户会看到两段拼接的回答）；
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
        supportsVision = false,
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
            emit(GenerationEvent.Failed(EngineErrorKind.AUTH, "missing API key for endpoint", false))
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

            // 供应商没给 finish_reason ⇒ 补终帧。这条 if 是「半截回答不被当完整回答落库」
            // 的唯一防线（AGENTS.md §五.6）。
            if (state.finishReason == null) {
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
            if (state.hasEmittedAny) {
                // 首字节后中断：**不重试**（重发会重复输出），保留已收到内容并标记截断。
                // 判据含工具碎片：只有 tool_calls 碎片、没有正文的流同样不许重发。
                emitFlushed(state)
                if (state.finishReason == null) emit(adapter.finish(state))
                return AttemptOutcome.Completed(state)
            }
            return AttemptOutcome.Retryable(
                state = state,
                failure = GenerationEvent.Failed(
                    kind = EngineErrorKind.NETWORK,
                    message = "network error: " + io.javaClass.simpleName,
                    retryable = true,
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
            )
            is GenerationEvent.ToolCallDelta -> Unit
            is GenerationEvent.Failed -> Unit
        }
    }

    // ------------------------------------------------------------------ 错误分类

    private fun configFailure(detail: String): GenerationEvent.Failed =
        GenerationEvent.Failed(EngineErrorKind.CONFIG, detail, false)

    /**
     * HTTP 状态码 → 错误类。分类决定「重试 / 改配置 / 压缩后重发 / 直接报错」。
     *
     * 所有 message 都是英文常量，由 UI 层映射为本地化文案（守卫 N3 基线 0）。
     */
    private fun classifyHttpFailure(status: Int, bodyText: String): GenerationEvent.Failed = when {
        status == 401 || status == 403 ->
            GenerationEvent.Failed(EngineErrorKind.AUTH, "authentication failed (HTTP $status)", false)

        status == 429 ->
            GenerationEvent.Failed(EngineErrorKind.RATE_LIMIT, "rate limited (HTTP $status)", true)

        status == 408 ->
            GenerationEvent.Failed(EngineErrorKind.NETWORK, "request timeout (HTTP $status)", true)

        status == 400 && looksLikeContextOverflow(bodyText) ->
            GenerationEvent.Failed(EngineErrorKind.CONTEXT_OVERFLOW, "context window exceeded", false)

        status >= 500 ->
            GenerationEvent.Failed(EngineErrorKind.SERVER, "server error (HTTP $status)", true)

        else ->
            GenerationEvent.Failed(EngineErrorKind.PROTOCOL, "request rejected (HTTP $status)", false)
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
        val lower = bodyText.lowercase()
        val mentionsWindow = lower.contains("context") || lower.contains("token")
        val mentionsOverflow = lower.contains("too long") ||
            lower.contains("maximum") ||
            lower.contains("exceed") ||
            lower.contains("limit") ||
            lower.contains("length")
        return mentionsWindow && mentionsOverflow
    }
}
