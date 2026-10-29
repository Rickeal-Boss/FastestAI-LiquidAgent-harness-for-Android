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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 「测试连接」的落地实现。
 *
 * 为什么不能只发一个 `HEAD /`：多数兼容端点的根路径返回 404 或 HTML，
 * 而真正要验的是**密钥 + 模型 id + 流式解码**三件事同时成立。所以发一条
 * 极短的 chat 请求，收到第一个事件就主动取消（`first {}` 会取消上游，
 * 引擎的 `finally` 负责关连接）—— 不花用户的 token 钱去等完整回答。
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
                val terminal = engine.generate(request).first { event ->
                    event is GenerationEvent.TextDelta ||
                        event is GenerationEvent.Finished ||
                        event is GenerationEvent.Failed
                }
                ProbeOutcome(
                    ok = terminal !is GenerationEvent.Failed,
                    detail = (terminal as? GenerationEvent.Failed)?.let { it.kind.name + ": " + it.message } ?: "",
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
