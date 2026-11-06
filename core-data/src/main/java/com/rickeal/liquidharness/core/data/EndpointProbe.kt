package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.engine.RemoteLlmEngine
import com.rickeal.liquidharness.core.engine.SecretProvider
import com.rickeal.liquidharness.core.engine.provider.OpenAiCompatibleAdapter
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.ProviderKind
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.SamplingParams
import com.rickeal.liquidharness.core.model.newId
import com.rickeal.liquidharness.core.net.HttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext

/**
 * 「测试连接」的落地实现。
 *
 * 为什么不能只发一个 `HEAD /`：多数兼容端点的根路径返回 404 或 HTML，
 * 而真正要验的是**密钥 + 模型 id + 流式解码**三件事同时成立。所以发一条
 * 极短的 chat 请求，收到终止帧（含其后一格 usage）就停 —— 引擎的 `finally` 负责关连接，
 * 不花用户的 token 钱去等完整回答（W9-01 P1-2：不再收「完整流」）。
 *
 * ## 判据为什么是「必须收到内容」而不是「没报错」
 *
 * 事故：旧实现把「收到 `Finished`」当成通过。一个把错误页以 **HTTP 200** 返回的网关
 * （HTML 体）会让 SSE 解析零事件，引擎按「空流 = STOP」补一个 `Finished` ——
 * 于是 probe 报绿，用户带着坏配置进对话页，第一次提问才炸。
 * 引擎侧「空流=STOP」的语义是合理的（半截流不该报错），所以收紧只能落在 probe：
 * 没有正文 / 思考 / usage 任何一条内容证据的 `Finished` 一律判失败。
 */
class EndpointProbe(
    private val transport: HttpTransport = HttpTransport(),
) {

    /**
     * @param apiKey 刚在输入框里填的明文（可能还没落盘）。null = 用已保存的密钥。
     *   传 `""` 表示「明确没有密钥」（本地 Ollama）。
     */
    suspend fun test(endpoint: RemoteEndpoint, apiKey: String?): ProbeOutcome =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val engine = RemoteLlmEngine(
                endpoint = endpoint,
                adapter = OpenAiCompatibleAdapter(),
                transport = transport,
                // 显式传入的 key 优先；null 时回落到「没有密钥」，由引擎按 requiresApiKey 判定
                secrets = SecretProvider { if (apiKey.isNullOrBlank()) null else apiKey },
            )
            val request = GenerationRequest(
                messages = listOf(ChatMessage(role = Role.USER, text = "ping")),
                tools = emptyList(),
                // W8（Q15）预算 8 → 256：dots 默认开思考，8-token 预算会被思考吃光 ⇒ 之前
                // 「思考帧算内容证据」+「8 预算」叠加，出现「关思考时正文空 ⇒ 假红」与
                // 「开思考时 rtok 算证据 ⇒ 假绿」两个方向的误判。256 覆盖思考 + 短回答。
                sampling = SamplingParams(maxTokens = 256, temperature = 0.0),
                endpoint = endpoint,
                idempotencyKey = newId(),
            )
            try {
                // W9-01 P1-2：终帧之后只再收一格 Usage 即取消上游，不再把后续增量（含重放的
                // `TextDelta`）一并收进来 —— 旧实现 `takeWhile` 在终帧后仍放行 Usage / TextDelta，
                // 异常流会拖垮探针（token / 延迟回归）。Q15 口径不变：ReasoningDelta **不算**
                // 内容证据（思考吃满预算时不得假绿）。
                val events = collectProbeEvents(engine.generate(request))
                val detail = judgeProbeSequence(events)
                ProbeOutcome(
                    ok = detail == null,
                    detail = detail ?: "",
                    latencyMillis = System.currentTimeMillis() - startedAt,
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                ProbeOutcome(
                    ok = false,
                    detail = t.javaClass.simpleName + ": " + (t.message ?: ""),
                    latencyMillis = System.currentTimeMillis() - startedAt,
                )
            }
        }

    /** W1 只实现了 OpenAI 兼容族（Anthropic / Gemini 的 adapter 是 W2）。 */
    fun supports(endpoint: RemoteEndpoint): Boolean = endpoint.kind == ProviderKind.OPENAI_COMPATIBLE
}

/** 测试连接的结果。`detail` 是诊断细节（英文常量 / 服务端原文），不是用户文案。 */
data class ProbeOutcome(
    val ok: Boolean,
    val detail: String,
    val latencyMillis: Long,
)

/** 空流（HTTP 200 但零事件）的失败详情。英文常量，UI 侧映射文案。 */
internal const val EMPTY_STREAM_DETAIL: String = "PROTOCOL: empty stream"

/**
 * 事件序列 → 连接测试结论（纯函数，JVM 单测直接驱动，不需要真 HTTP）。
 *
 * 「内容证据」= 正文 / usage 任一。🔴 W8（Q15）**思考帧不算**：dots 默认开思考，
 * 把 rtok 算进去会在「思考吃满输出预算」时假绿、在 `reasoning:{enabled:false}` 时假红。
 * 只有 `Finished` 而无任何内容证据，说明服务端
 * 回了 200 但没吐出可解析的 SSE（HTML 错误页、被网关改写的 JSON 都是这种形态）。
 *
 * @return null = 通过；非 null = 失败原因（英文诊断串）。
 */
internal fun judgeProbe(terminal: GenerationEvent, sawContent: Boolean): String? = when {
    terminal is GenerationEvent.Failed -> terminal.kind.name + ": " + terminal.message
    sawContent -> null
    else -> EMPTY_STREAM_DETAIL
}

/**
 * 事件序列 → 结论（纯函数，JVM 单测直接驱动，不需要真 HTTP）。
 *
 * 与 [judgeProbe] 的差别：内容证据由**序列**归约，而不是由调用方在 `firstOrNull` 谓词里
 * 累积 —— 这样「`Finished` 之后同帧的 `Usage`」才算得进去（P1-5 的关键反例：
 * `decodeEvent` 就是先 Finished 后 Usage）。无终帧 = 空流，同样判失败。
 */
internal fun judgeProbeSequence(events: List<GenerationEvent>): String? {
    if (events.isEmpty()) return EMPTY_STREAM_DETAIL
    val sawContent = events.any {
        it is GenerationEvent.TextDelta || it is GenerationEvent.Usage
    }
    // 没有终止帧但**已有内容证据**也算通过：现役 firstOrNull 的谓词本就包含
    // TextDelta / Usage，它们会作为 terminal 返回并经 sawContent 判绿，不能收紧成假红。
    val terminal = events.lastOrNull {
        it is GenerationEvent.Finished || it is GenerationEvent.Failed
    } ?: return if (sawContent) null else EMPTY_STREAM_DETAIL
    return judgeProbe(terminal, sawContent)
}

/** 「测试连接」事件流的安全上限（W9-01 P1-2）。探针是极短的 ping，远不需要更多帧；
 *  异常流（终帧后不收敛）靠它兜底熔断，不靠等完整流。 */
private const val MAX_PROBE_EVENTS: Int = 256

/**
 * 收束「测试连接」的事件流（纯函数，JVM 单测直接驱动，不需要真 HTTP）。
 *
 * - 未到终帧：所有事件正常收（正文 / 思考 / 工具增量都是「这端点是活的」证据）；
 * - 收到终帧（[GenerationEvent.Finished] / [GenerationEvent.Failed]）后，**只**再接纳一格
 *   [GenerationEvent.Usage]（作「有内容」判据）；其后任何增量（含重放的 `TextDelta`）立即
 *   判 false ⇒ `takeWhile` 取消上游、停止收集；
 * - 整条流另有 [MAX_PROBE_EVENTS] 硬上限兜底。
 *
 * 与旧实现「收至终帧且终帧后仍放行 Usage / TextDelta」的差别：旧实现在 `finish_reason=stop`
 * 之后仍把后续 `TextDelta` 全收 ⇒ 异常 / 重放流会让探针空等、token 与延迟回归。
 */
internal suspend fun collectProbeEvents(stream: Flow<GenerationEvent>): List<GenerationEvent> {
    val events = ArrayList<GenerationEvent>(8)
    var terminalSeen = false
    var usageAfterTerminal = false
    stream
        .takeWhile { event ->
            val keep = when {
                event is GenerationEvent.Finished || event is GenerationEvent.Failed -> {
                    terminalSeen = true
                    true
                }
                terminalSeen && event is GenerationEvent.Usage && !usageAfterTerminal -> {
                    usageAfterTerminal = true
                    true
                }
                terminalSeen -> false
                else -> true
            }
            keep && events.size < MAX_PROBE_EVENTS
        }
        .collect { events.add(it) }
    return events
}
