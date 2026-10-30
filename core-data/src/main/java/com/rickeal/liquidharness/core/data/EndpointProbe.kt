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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * 「测试连接」的落地实现。
 *
 * 为什么不能只发一个 `HEAD /`：多数兼容端点的根路径返回 404 或 HTML，
 * 而真正要验的是**密钥 + 模型 id + 流式解码**三件事同时成立。所以发一条
 * 极短的 chat 请求，收到第一个事件就主动取消（`firstOrNull {}` 会取消上游，
 * 引擎的 `finally` 负责关连接）—— 不花用户的 token 钱去等完整回答。
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
                sampling = SamplingParams(maxTokens = 8, temperature = 0.0),
                endpoint = endpoint,
                idempotencyKey = newId(),
            )
            try {
                // 「有没有真的收到内容」必须自己记：`first {}` 在**第一个**匹配事件处就取消上游，
                // 拿不到「之前的序列」，所以内容证据要在谓词里累积。
                var sawContent = false
                val terminal = engine.generate(request).firstOrNull { event ->
                    val isContent = event is GenerationEvent.TextDelta ||
                        event is GenerationEvent.ReasoningDelta ||
                        event is GenerationEvent.Usage
                    if (isContent) sawContent = true
                    isContent || event is GenerationEvent.Finished || event is GenerationEvent.Failed
                }
                val detail = if (terminal == null) {
                    EMPTY_STREAM_DETAIL
                } else {
                    judgeProbe(terminal, sawContent)
                }
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
 * 「内容证据」= 正文 / 思考 / usage 任一。只有 `Finished` 而无任何内容，说明服务端
 * 回了 200 但没吐出可解析的 SSE（HTML 错误页、被网关改写的 JSON 都是这种形态）。
 *
 * @return null = 通过；非 null = 失败原因（英文诊断串）。
 */
internal fun judgeProbe(terminal: GenerationEvent, sawContent: Boolean): String? = when {
    terminal is GenerationEvent.Failed -> terminal.kind.name + ": " + terminal.message
    sawContent -> null
    else -> EMPTY_STREAM_DETAIL
}
