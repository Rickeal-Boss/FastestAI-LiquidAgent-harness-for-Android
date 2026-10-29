package com.rickeal.liquidharness.core.engine.provider

import com.rickeal.liquidharness.core.model.AgentJson
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.ProviderKind
import com.rickeal.liquidharness.core.model.ReasoningStyle
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolSpec
import com.rickeal.liquidharness.core.net.SseEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * OpenAI Chat Completions 兼容族适配器（W1 唯一实现）。
 *
 * 覆盖：OpenAI / DeepSeek / Ollama / vLLM / SiliconFlow / 一切「换个 baseUrl 就能用」的端点。
 *
 * 三族的差异见 `01-architecture.md` §3.2；本族的关键特征：
 * - `system` 是 `messages[0]` 的一条（Anthropic 是 body 顶层字段）；
 * - 工具声明是 `tools[{type:function, function:{...}}]`；
 * - 工具调用参数是 **JSON 碎片**（`delta.tool_calls[i].function.arguments`）；
 * - 思考通道是 `delta.reasoning_content`（DeepSeek / vLLM）或 `delta.reasoning`（部分代理）；
 * - `usage` 只在 `stream_options.include_usage = true` 时才有，且位于**末帧**；
 * - 结束标记是 `data: [DONE]`（没有 `event:` 行）。
 *
 * 无状态：所有可变状态在 [AdapterStreamState]，本类可被多条并发流复用。
 */
class OpenAiCompatibleAdapter : ProviderAdapter {

    override val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE

    /**
     * `baseUrl` 不含路径后缀（契约规定），这里补 `/chat/completions`。
     * 若用户已经把完整路径粘进 baseUrl（常见误操作），不再重复追加 ——
     * 否则会得到 `/chat/completions/chat/completions` 的 404，而报错信息完全不指向病因。
     */
    override fun endpointUrl(endpoint: RemoteEndpoint): String {
        val base = endpoint.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return ""
        if (base.endsWith(CHAT_COMPLETIONS_PATH)) return base
        return base + CHAT_COMPLETIONS_PATH
    }

    override fun headers(endpoint: RemoteEndpoint, secret: String?): Map<String, String> {
        val out = LinkedHashMap<String, String>(4 + endpoint.extraHeaders.size)
        out["Content-Type"] = "application/json; charset=utf-8"
        out["Accept"] = "text/event-stream"
        if (!secret.isNullOrBlank()) out["Authorization"] = "Bearer $secret"
        // extraHeaders 放最后：允许用户覆盖上面三条（部分自建网关用 X-Api-Key 而不是 Bearer）。
        for ((name, value) in endpoint.extraHeaders) {
            if (name.isNotBlank()) out[name] = value
        }
        return out
    }

    override fun buildBody(request: GenerationRequest): JsonObject {
        val endpoint = request.endpoint
        val messages = request.messages.filterNot { it.isEmpty }.map { messageObject(it) }
        val tools = if (endpoint.supportsTools && request.tools.isNotEmpty()) request.tools else emptyList()
        return buildJsonObject {
            put("model", endpoint.modelId)
            put("messages", buildJsonArray { messages.forEach { add(it) } })
            put("stream", true)
            // 没有这个字段，OpenAI 在流式下**完全不返回 usage** ——
            // 而 usage 是本仓唯一权威的 token 判据（00-brief A5）。
            put("stream_options", buildJsonObject { put("include_usage", true) })
            put("temperature", request.sampling.temperature)
            put("top_p", request.sampling.topP)
            put("max_tokens", request.sampling.maxTokens ?: endpoint.maxOutputTokens)
            if (request.sampling.stop.isNotEmpty()) {
                put("stop", buildJsonArray { request.sampling.stop.forEach { add(it) } })
            }
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray { tools.forEach { add(toolObject(it)) } })
                put("tool_choice", "auto")
            }
            applyReasoningStyle(endpoint, this)
        }
    }

    override fun decodeEvent(event: SseEvent): List<GenerationEvent> {
        val payload = event.data.trim()
        if (payload.isEmpty() || payload == DONE_MARKER) return emptyList()
        // 心跳 / 半截 JSON / 非 JSON 的 keep-alive 一律跳过，不抛异常：
        // 抛异常会把「端点不是 OpenAI 兼容接口」和「网络抖了一下」混成同一个错误。
        val obj = AgentJson.decodeObjectOrNull(payload) ?: return emptyList()

        val out = ArrayList<GenerationEvent>(4)
        val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        val delta = choice?.get("delta") as? JsonObject
        if (delta != null) {
            // 思考通道与正文分道：混在一起会让 UI 把「思考过程」当回答上屏。
            val reasoning = AgentJson.stringOrNull(delta, "reasoning_content")
                ?: AgentJson.stringOrNull(delta, "reasoning")
            if (!reasoning.isNullOrEmpty()) out.add(GenerationEvent.ReasoningDelta(reasoning))

            val text = AgentJson.stringOrNull(delta, "content")
            if (!text.isNullOrEmpty()) out.add(GenerationEvent.TextDelta(text))

            val toolCalls = delta["tool_calls"] as? JsonArray
            if (toolCalls != null) {
                for (element in toolCalls) {
                    val call = element as? JsonObject ?: continue
                    val fn = call["function"] as? JsonObject
                    out.add(
                        GenerationEvent.ToolCallDelta(
                            index = AgentJson.intOrNull(call, "index") ?: 0,
                            id = AgentJson.stringOrNull(call, "id"),
                            name = AgentJson.stringOrNull(fn, "name"),
                            argumentsFragment = AgentJson.stringOrNull(fn, "arguments").orEmpty(),
                        )
                    )
                }
            }
        }

        AgentJson.stringOrNull(choice, "finish_reason")?.let { reason ->
            out.add(GenerationEvent.Finished(mapFinishReason(reason)))
        }

        // usage 可能在「choices 为空数组」的末帧里单独到达，所以不能嵌在 choice 判断内。
        val usage = obj["usage"] as? JsonObject
        if (usage != null) {
            val prompt = AgentJson.intOrNull(usage, "prompt_tokens") ?: 0
            val completion = AgentJson.intOrNull(usage, "completion_tokens") ?: 0
            val cached = AgentJson.intOrNull(usage["prompt_tokens_details"] as? JsonObject, "cached_tokens") ?: 0
            out.add(GenerationEvent.Usage(prompt, completion, cached))
        }
        return out
    }

    override fun isTerminal(event: SseEvent): Boolean = event.data.trim() == DONE_MARKER

    override fun finish(state: AdapterStreamState): GenerationEvent.Finished =
        GenerationEvent.Finished(terminationReason(state))

    // ------------------------------------------------------------------ 请求体

    /**
     * 三种思考参数风格（第四种 [ReasoningStyle.NONE] = 不写任何字段）。
     *
     * 后两种（Anthropic / Gemini 的形态）在这里**故意不写**：它们不是本族协议，
     * 由 W2 的 AnthropicAdapter / GeminiAdapter 消费。写在这里只会造出一个
     * 「看起来支持、实际被服务端忽略」的字段。
     */
    private fun applyReasoningStyle(endpoint: RemoteEndpoint, body: JsonObjectBuilder) {
        when (endpoint.reasoningStyle) {
            ReasoningStyle.NONE -> Unit
            ReasoningStyle.ENABLE_THINKING_BOOL -> body.put("enable_thinking", true)
            ReasoningStyle.REASONING_EFFORT -> body.put("reasoning_effort", endpoint.reasoningEffort)
            ReasoningStyle.CHAT_TEMPLATE_KWARGS ->
                body.put("chat_template_kwargs", buildJsonObject { put("enable_thinking", true) })
            ReasoningStyle.ANTHROPIC_THINKING_BUDGET,
            ReasoningStyle.GEMINI_INCLUDE_THOUGHTS,
            -> Unit
        }
    }

    private fun messageObject(message: ChatMessage): JsonObject = buildJsonObject {
        put("role", wireRole(message.role))
        put("content", contentText(message))
        if (message.toolCalls.isNotEmpty()) {
            put(
                "tool_calls",
                buildJsonArray {
                    for (call in message.toolCalls) {
                        add(
                            buildJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                put(
                                    "function",
                                    buildJsonObject {
                                        put("name", call.name)
                                        put("arguments", call.argumentsJson)
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }
        if (message.role == Role.TOOL) {
            val callId = message.toolResults.firstOrNull()?.callId
            if (!callId.isNullOrEmpty()) put("tool_call_id", callId)
        }
    }

    /**
     * TOOL 消息的内容就是工具执行结果。
     *
     * 这里**必须**取 `output` 而不是 `displayOutput`：脱敏是给上屏用的，
     * 回灌模型的是原文 —— 模型让工具写一个 key 到文件是合法需求
     * （00-brief A7「只脱展示不脱执行」）。
     */
    private fun contentText(message: ChatMessage): String {
        if (message.role == Role.TOOL) {
            val result = message.toolResults.firstOrNull() ?: return message.text
            return result.output.ifBlank { result.errorMessage.orEmpty() }
        }
        return message.text
    }

    private fun toolObject(spec: ToolSpec): JsonObject = buildJsonObject {
        put("type", "function")
        put(
            "function",
            buildJsonObject {
                put("name", spec.name)
                put("description", spec.description)
                put("parameters", parametersSchema(spec.parameters))
            }
        )
    }

    private fun parametersSchema(parameters: List<ToolParameter>): JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                for (parameter in parameters) {
                    put(
                        parameter.name,
                        buildJsonObject {
                            put("type", parameter.type.jsonName)
                            put("description", parameter.description)
                            if (parameter.enumValues.isNotEmpty()) {
                                put("enum", buildJsonArray { parameter.enumValues.forEach { add(it) } })
                            }
                        }
                    )
                }
            }
        )
        put(
            "required",
            buildJsonArray { parameters.filter { it.required }.forEach { add(it.name) } }
        )
    }

    private fun wireRole(role: Role): String = when (role) {
        Role.SYSTEM -> "system"
        Role.USER -> "user"
        Role.MODEL -> "assistant"
        Role.TOOL -> "tool"
    }

    private fun mapFinishReason(raw: String): FinishReason = when (raw) {
        "stop" -> FinishReason.STOP
        "length" -> FinishReason.LENGTH
        "tool_calls", "function_call" -> FinishReason.TOOL_CALLS
        "content_filter" -> FinishReason.FILTER
        // 未知值按 STOP 处理：把它当 LENGTH 会让内核误判「被截断」并触发无谓重试。
        else -> FinishReason.STOP
    }

    private companion object {
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"

        /** 与 `core-net` 的 `SseEventParser.DONE_MARKER` 同值；此处独立声明避免反向依赖语义。 */
        const val DONE_MARKER = "[DONE]"
    }
}
