package com.rickeal.liquidharness.core.engine.provider

import com.rickeal.liquidharness.core.model.AgentJson
import com.rickeal.liquidharness.core.model.AttachmentKind
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.credentialishExtraBodyViolation
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
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
        val messages = request.messages.filterNot { it.isEmpty }.flatMap { messageObjects(it) }
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
            if (endpoint.jsonMode) {
                // 强制 JSON 模式（端点 jsonMode 开关，用户 W8 需求 B 路）。
                // north 无 response_format ⇒ 由 UI 侧降级标 ⚠️，adapter 不做能力探测请求。
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
            applyReasoningStyle(endpoint, this)
            // 自定义协议**最后** merge：可追加新键，RESERVED_BODY_KEYS 一律跳过（不许覆盖上面已定的键）。
            applyCustomProtocol(endpoint, this)
        }
    }

    /**
     * 族内覆写少量 body 键（用户「自定义协议」）。
     *
     * 运行时**复用保存链同一张校验表**（[credentialishExtraBodyViolation]）逐键兜底：
     * 保留键 / 凭据词表 / 值里的凭据形态。保存链之外再兜一层，是因为端点可能被
     * 老配置或设备侧注入绕过 UI 直接带进来 —— 组件写完不等于组件被行使（W9-00 F-1）。
     */
    private fun applyCustomProtocol(endpoint: RemoteEndpoint, body: JsonObjectBuilder) {
        val cp = endpoint.customProtocol
        if (!cp.enabled || cp.extraBody.isEmpty()) return
        for ((key, raw) in cp.extraBody) {
            val k = key.trim()
            if (k.isEmpty() || credentialishExtraBodyViolation(key, raw) != null) continue
            body.put(k, coerceScalar(raw))
        }
    }

    /** 把 `Map<String,String>` 的值转成原始 JSON 标量（数字 / 布尔 / 字符串）。 */
    private fun coerceScalar(raw: String): JsonElement = when {
        raw.equals("true", ignoreCase = true) -> JsonPrimitive(true)
        raw.equals("false", ignoreCase = true) -> JsonPrimitive(false)
        // NaN / Inf / Infinity 经 toDoubleOrNull() 会成非有限 Double，
        // JsonPrimitive(Double.NaN) 在 encodeToString 时抛异常 ⇒ 一条 extraBody 让整次 run 崩（P1-3）。
        raw.trim().lowercase() in NON_FINITE_SCALARS -> JsonPrimitive(raw)
        raw.toLongOrNull() != null -> JsonPrimitive(raw.toLong())
        // 科学计数法溢出（"1e999" ⇒ +Infinity）经 toDouble() 得非有限 Double，
        // 编码时抛异常 ⇒ 整次 run 崩（W9-NEW-1）。isFinite 守卫把溢出值也按字符串保留，
        // 与 NON_FINITE_SCALARS 形成双保险（后者只挡字面的 "inf"/"nan"）。
        raw.toDoubleOrNull()?.isFinite() == true -> JsonPrimitive(raw.toDouble())
        else -> JsonPrimitive(raw)
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
            // 缺失 ⇒ null（未上报），不是 0（未命中）：这两件事的处置相反，
            // 在解析层压平之后下游再也不可能分开（design §2.4 的三处失真之一）。
            val cached = AgentJson.intOrNull(usage["prompt_tokens_details"] as? JsonObject, "cached_tokens")
            out.add(GenerationEvent.Usage(prompt, completion, cached))
        }
        return out
    }

    override fun isTerminal(event: SseEvent): Boolean = event.data.trim() == DONE_MARKER

    /**
     * 从 400 错误体里解析真实窗口（W5-02）。
     *
     * 文案出处：`docs/notes/w5-provider-matrix.md:89-93`，9 次观测字符串完全一致 ——
     * `This model's maximum context length is 1000000 tokens ...`。所以只钉这一句，
     * 不做「猜任意数字」的宽松匹配：把 `max_tokens` 之类的数字当窗口会写出**更错**的预算，
     * 比不写回更糟（预算一旦写错，下一次请求的判据全部失真，且没有任何报错）。
     *
     * 大小写与多余空白放宽（部分网关会改写措辞），数字必须紧跟 `context length is`。
     */
    override fun contextLimitHint(errorBody: String): Int? {
        if (errorBody.isEmpty()) return null
        val match = CONTEXT_LIMIT_PATTERN.find(errorBody) ?: return null
        val value = match.groupValues[1].toIntOrNull() ?: return null
        return if (value > 0) value else null
    }

    override fun finish(state: AdapterStreamState): GenerationEvent.Finished =
        GenerationEvent.Finished(terminationReason(state))

    // ------------------------------------------------------------------ 请求体

    /**
     * 四种思考参数风格（[ReasoningStyle.NONE] = 不写任何字段）。
     *
     * 后两种（Anthropic / Gemini 的形态）在这里**故意不写**：它们不是本族协议，
     * 由 W2 的 AnthropicAdapter / GeminiAdapter 消费。写在这里只会造出一个
     * 「看起来支持、实际被服务端忽略」的字段。
     *
     * W8 新增 [ReasoningStyle.OPENROUTER_REASONING_OBJECT]：OpenRouter 统一 `reasoning` 对象，
     * 由 `ReasoningBody.buildReasoningObject` 按端点 `thinkingMode` 决定线上形态；
     * 构造器单独成文件是为了不把 [applyReasoningStyle] 顶过 G11 的 120 行上限。
     *
     * 🔴 [ReasoningStyle.REASONING_EFFORT] 与新值都走 [RemoteEndpoint.resolvedEffort]：
     * 档位经 `normalizeEffort` 归一，第五档 UI 档位 `EXTREME` **永不发 `max`**（显式降级并落 reason）。
     * 降级 `reason` 由 `RemoteEndpoint.resolvedEffort()` 暴露给 UI / 单测消费，
     * 本 adapter **不打日志**（G19 扫 `core-engine/src/main` 的日志调用点，保持其 VACUOUS 基线不变）。
     */
    private fun applyReasoningStyle(endpoint: RemoteEndpoint, body: JsonObjectBuilder) {
        when (endpoint.reasoningStyle) {
            ReasoningStyle.NONE -> Unit
            ReasoningStyle.ENABLE_THINKING_BOOL -> body.put("enable_thinking", true)
            ReasoningStyle.REASONING_EFFORT -> {
                val effort = endpoint.resolvedEffort()
                if (effort.wire != null) body.put("reasoning_effort", effort.wire)
            }
            ReasoningStyle.CHAT_TEMPLATE_KWARGS ->
                body.put("chat_template_kwargs", buildJsonObject { put("enable_thinking", true) })
            ReasoningStyle.ANTHROPIC_THINKING_BUDGET,
            ReasoningStyle.GEMINI_INCLUDE_THOUGHTS,
            -> Unit
            ReasoningStyle.OPENROUTER_REASONING_OBJECT -> buildReasoningObject(endpoint, body)
        }
    }

    /**
     * 一条内核消息 → 0..n 条线上消息。
     *
     * **为什么 TOOL 消息要展开**：内核的 `ChatMessage.toolResults` 允许一条消息携带
     * N 个结果（契约 §一，Anthropic 形态就是这样）。OpenAI 兼容端点要求
     * **每条 tool 消息恰好对应一个 `tool_call_id`**，且每个 assistant 的 tool_call
     * 都必须有对应的 tool 消息 —— 原样只取第一个会让 N−1 个结果静默消失，
     * 服务端随即以「tool_call 无对应 result」400（外审 P1-4）。
     *
     * 责任钉在 adapter 而不是约束内核：内核表示法必须保持供应商无关。
     */
    private fun messageObjects(message: ChatMessage): List<JsonObject> {
        if (message.role == Role.TOOL && message.toolResults.size > 1) {
            return message.toolResults.map { result ->
                buildJsonObject {
                    put("role", wireRole(Role.TOOL))
                    put("tool_call_id", result.callId)
                    put("content", result.output.ifBlank { result.errorMessage.orEmpty() })
                }
            }
        }
        return listOf(messageObject(message))
    }

    private fun messageObject(message: ChatMessage): JsonObject = buildJsonObject {
        put("role", wireRole(message.role))
        put("content", contentElement(message))
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

    /**
     * `content` 字段的线上形态。
     *
     * ## 为什么图片必须走数组（W3 多模态）
     *
     * OpenAI 兼容族的视觉输入是 `content: [{type:"text"}, {type:"image_url",...}]`；
     * 纯字符串形态**不会**被解释成多模态，模型只会看到一段文本（实测：把 base64 塞进
     * 字符串，模型回答「我没有看到图片」）。
     *
     * ## 三条纪律
     *
     * 1. **只有 USER 消息可带附件**（`sendableAttachments` 已按 role 过滤）——
     *    给 assistant 消息塞 image part 会被部分端点 400。
     * 2. **文本附件拼进正文，图片走 part**：文本附件（txt/md）没有对应的 part 类型，
     *    拼进 `text` 段是唯一通用形态；拼的时候带上文件名，模型才知道来源。
     * 3. **无附件时逐字节等于旧行为**（返回 `JsonPrimitive(String)`），
     *    保证既有单测与 prompt cache 的前缀字节不变。
     */
    private fun contentElement(message: ChatMessage): JsonElement {
        val text = contentText(message)
        val attachments = message.sendableAttachments
        if (attachments.isEmpty()) return JsonPrimitive(text)

        val textParts = StringBuilder(text)
        for (attachment in attachments) {
            if (attachment.kind == AttachmentKind.TEXT) {
                if (textParts.isNotEmpty()) textParts.append("\n\n")
                textParts.append("[attached file: ").append(attachment.name).append("]\n")
                textParts.append(attachment.textContent)
            }
        }
        val images = attachments.filter { it.kind == AttachmentKind.IMAGE }

        return buildJsonArray {
            if (textParts.isNotEmpty()) {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", textParts.toString())
                    }
                )
            }
            for (image in images) {
                add(
                    buildJsonObject {
                        put("type", "image_url")
                        put(
                            "image_url",
                            buildJsonObject {
                                put("url", "data:" + image.mimeType + ";base64," + image.base64)
                            }
                        )
                    }
                )
            }
        }
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
        /** 非有限数值字面量：一律按字符串保留，绝不转成 Double（P1-3）。 */
        val NON_FINITE_SCALARS = setOf(
            "nan", "inf", "+inf", "-inf", "infinity", "+infinity", "-infinity",
        )

        const val CHAT_COMPLETIONS_PATH = "/chat/completions"

        /** 与 `core-net` 的 `SseEventParser.DONE_MARKER` 同值；此处独立声明避免反向依赖语义。 */
        const val DONE_MARKER = "[DONE]"

        /**
         * 超窗 400 的真实窗口。`maximum` 前缀可选（部分网关只写 `context length is N`），
         * 其余逐字钉死 —— 宽松匹配会把别的数字（`max_tokens` / `requested N tokens`）
         * 误当成窗口，写出一个**更错**的预算。
         */
        val CONTEXT_LIMIT_PATTERN = Regex(
            """(?:maximum\s+)?context length is (\d+)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
