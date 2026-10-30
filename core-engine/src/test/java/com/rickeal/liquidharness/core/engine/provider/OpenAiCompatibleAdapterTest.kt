package com.rickeal.liquidharness.core.engine.provider

import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.ReasoningStyle
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.SamplingParams
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec
import com.rickeal.liquidharness.core.net.SseEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OpenAI 兼容族的请求体与流解码单测。
 *
 * 请求体是**逐字段断言**而不是「能序列化就算过」：一个字段名写错
 * （`max_tokens` vs `max_completion_tokens`、`top_p` vs `topP`）不会报错，
 * 只会让参数静默失效 —— 那种缺陷只能靠逐字段断言抓住。
 */
class OpenAiCompatibleAdapterTest {

    private val adapter = OpenAiCompatibleAdapter()

    private fun endpoint(
        baseUrl: String = "https://api.example.com/v1",
        reasoningStyle: ReasoningStyle = ReasoningStyle.NONE,
        extraHeaders: Map<String, String> = emptyMap(),
    ) = RemoteEndpoint(
        id = "ep-1",
        name = "test",
        baseUrl = baseUrl,
        modelId = "gpt-test",
        maxOutputTokens = 4096,
        supportsTools = true,
        reasoningStyle = reasoningStyle,
        extraHeaders = extraHeaders,
    )

    private fun request(
        tools: List<ToolSpec> = emptyList(),
        messages: List<ChatMessage> = listOf(ChatMessage(role = Role.USER, text = "hi")),
        sampling: SamplingParams = SamplingParams(),
        endpoint: RemoteEndpoint = endpoint(),
    ) = GenerationRequest(
        messages = messages,
        tools = tools,
        sampling = sampling,
        endpoint = endpoint,
        idempotencyKey = "run-1-round-1",
    )

    // ------------------------------------------------------------ 请求体字段

    @Test
    fun requestBodyContainsAllCoreFields() {
        val body = adapter.buildBody(
            request(sampling = SamplingParams(temperature = 0.3, topP = 0.9, maxTokens = 256))
        )

        assertEquals("gpt-test", body["model"]!!.jsonPrimitive.content)
        assertTrue(body["stream"]!!.jsonPrimitive.boolean)
        // 没有 include_usage，OpenAI 在流式下完全不返回 usage。
        val streamOptions = body["stream_options"]!!.jsonObject
        assertTrue(streamOptions["include_usage"]!!.jsonPrimitive.boolean)
        assertEquals(0.3, body["temperature"]!!.jsonPrimitive.double, 0.0001)
        assertEquals(0.9, body["top_p"]!!.jsonPrimitive.double, 0.0001)
        assertEquals(256, body["max_tokens"]!!.jsonPrimitive.int)
        assertEquals(1, (body["messages"] as JsonArray).size)
    }

    /** 未给 maxTokens 时回落到端点的 maxOutputTokens。 */
    @Test
    fun maxTokensFallsBackToEndpointValue() {
        val body = adapter.buildBody(request())
        assertEquals(4096, body["max_tokens"]!!.jsonPrimitive.int)
    }

    /** tools 必须编码成 `[{type:function, function:{name,description,parameters}}]` + tool_choice。 */
    @Test
    fun toolsAreEncodedAsFunctionObjects() {
        val spec = ToolSpec(
            name = "get_weather",
            description = "Get weather for a city",
            parameters = listOf(
                ToolParameter("city", ToolParamType.STRING, "city name", required = true),
                ToolParameter("days", ToolParamType.INTEGER, "forecast days", required = false),
            ),
        )
        val body = adapter.buildBody(request(tools = listOf(spec)))

        val tool = (body["tools"] as JsonArray).single().jsonObject
        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        val function = tool["function"]!!.jsonObject
        assertEquals("get_weather", function["name"]!!.jsonPrimitive.content)
        assertEquals("Get weather for a city", function["description"]!!.jsonPrimitive.content)

        val parameters = function["parameters"]!!.jsonObject
        assertEquals("object", parameters["type"]!!.jsonPrimitive.content)
        val properties = parameters["properties"]!!.jsonObject
        assertEquals("string", properties["city"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("integer", properties["days"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("city"), (parameters["required"] as JsonArray).map { it.jsonPrimitive.content })
        assertEquals("auto", body["tool_choice"]!!.jsonPrimitive.content)
    }

    /** 端点声明不支持工具时，即使请求里带了 tools 也不得发出（否则部分端点直接 400）。 */
    @Test
    fun toolsAreOmittedWhenEndpointDoesNotSupportThem() {
        val body = adapter.buildBody(
            request(
                tools = listOf(ToolSpec(name = "x", description = "d")),
                endpoint = endpoint().copy(supportsTools = false),
            )
        )
        assertNull(body["tools"])
        assertNull(body["tool_choice"])
    }

    // ------------------------------------------------------------ 三种思考风格

    @Test
    fun enableThinkingBoolStyle() {
        val body = adapter.buildBody(
            request(endpoint = endpoint(reasoningStyle = ReasoningStyle.ENABLE_THINKING_BOOL))
        )
        assertTrue(body["enable_thinking"]!!.jsonPrimitive.boolean)
        assertNull(body["reasoning_effort"])
    }

    @Test
    fun reasoningEffortStyle() {
        val body = adapter.buildBody(
            request(
                endpoint = endpoint(reasoningStyle = ReasoningStyle.REASONING_EFFORT)
                    .copy(reasoningEffort = "high")
            )
        )
        assertEquals("high", body["reasoning_effort"]!!.jsonPrimitive.content)
        assertNull(body["enable_thinking"])
    }

    @Test
    fun chatTemplateKwargsStyle() {
        val body = adapter.buildBody(
            request(endpoint = endpoint(reasoningStyle = ReasoningStyle.CHAT_TEMPLATE_KWARGS))
        )
        val kwargs = body["chat_template_kwargs"]!!.jsonObject
        assertTrue(kwargs["enable_thinking"]!!.jsonPrimitive.boolean)
    }

    /** NONE 以及非本族的两种风格都不得写出任何思考字段。 */
    @Test
    fun noneAndForeignStylesEmitNoReasoningField() {
        for (style in listOf(
            ReasoningStyle.NONE,
            ReasoningStyle.ANTHROPIC_THINKING_BUDGET,
            ReasoningStyle.GEMINI_INCLUDE_THOUGHTS,
        )) {
            val body = adapter.buildBody(request(endpoint = endpoint(reasoningStyle = style)))
            assertNull(body["enable_thinking"], "style=$style")
            assertNull(body["reasoning_effort"], "style=$style")
            assertNull(body["chat_template_kwargs"], "style=$style")
        }
    }

    // ------------------------------------------------------------ URL / 头

    @Test
    fun endpointUrlAppendsChatCompletionsOnce() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            adapter.endpointUrl(endpoint()),
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            adapter.endpointUrl(endpoint("https://api.example.com/v1/")),
        )
        // 用户把完整路径粘进 baseUrl（常见误操作）时不得重复追加。
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            adapter.endpointUrl(endpoint("https://api.example.com/v1/chat/completions")),
        )
    }

    @Test
    fun headersIncludeBearerOnlyWhenSecretPresent() {
        val withSecret = adapter.headers(endpoint(), "k-123")
        assertEquals("Bearer k-123", withSecret["Authorization"])
        assertEquals("text/event-stream", withSecret["Accept"])

        val withoutSecret = adapter.headers(endpoint(), null)
        assertNull(withoutSecret["Authorization"])
    }

    /** extraHeaders 覆盖内建头（自建网关常用 X-Api-Key 而不是 Bearer）。 */
    @Test
    fun extraHeadersOverrideBuiltInHeaders() {
        val headers = adapter.headers(
            endpoint(extraHeaders = mapOf("Authorization" to "X-Custom abc")),
            "k-123",
        )
        assertEquals("X-Custom abc", headers["Authorization"])
    }

    // ------------------------------------------------------------ 流解码

    @Test
    fun textAndReasoningDeltasAreSeparated() {
        val events = adapter.decodeEvent(
            SseEvent("""{"choices":[{"delta":{"content":"hi","reasoning_content":"think"}}]}""")
        )
        assertEquals(2, events.size)
        assertEquals("think", (events[0] as GenerationEvent.ReasoningDelta).text)
        assertEquals("hi", (events[1] as GenerationEvent.TextDelta).text)
    }

    @Test
    fun toolCallFragmentsAreDecoded() {
        val events = adapter.decodeEvent(
            SseEvent(
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"f","arguments":"{\"a\""}}]}}]}"""
            )
        )
        val delta = events.single() as GenerationEvent.ToolCallDelta
        assertEquals(0, delta.index)
        assertEquals("call_1", delta.id)
        assertEquals("f", delta.name)
        assertEquals("{\"a\"", delta.argumentsFragment)
    }

    /** usage 在「choices 为空数组」的末帧单独到达，且要取出 cached_tokens。 */
    @Test
    fun usageIsParsedFromEmptyChoicesFrame() {
        val events = adapter.decodeEvent(
            SseEvent(
                """{"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":5,""" +
                    """"prompt_tokens_details":{"cached_tokens":8}}}"""
            )
        )
        val usage = events.single() as GenerationEvent.Usage
        assertEquals(10, usage.prompt)
        assertEquals(5, usage.completion)
        assertEquals(8, usage.cachedPrompt)
    }

    @Test
    fun finishReasonIsMapped() {
        fun reason(raw: String): FinishReason {
            val events = adapter.decodeEvent(SseEvent("""{"choices":[{"finish_reason":"$raw"}]}"""))
            return (events.single() as GenerationEvent.Finished).reason
        }
        assertEquals(FinishReason.STOP, reason("stop"))
        assertEquals(FinishReason.LENGTH, reason("length"))
        assertEquals(FinishReason.TOOL_CALLS, reason("tool_calls"))
        assertEquals(FinishReason.FILTER, reason("content_filter"))
        // 未知值按 STOP：当 LENGTH 处理会触发无谓重试。
        assertEquals(FinishReason.STOP, reason("something_new"))
    }

    /** 心跳 / 半截 JSON / 非 JSON 一律跳过，不抛异常。 */
    @Test
    fun malformedPayloadsAreSkippedWithoutThrowing() {
        assertTrue(adapter.decodeEvent(SseEvent("")).isEmpty())
        assertTrue(adapter.decodeEvent(SseEvent("[DONE]")).isEmpty())
        assertTrue(adapter.decodeEvent(SseEvent("{\"choices\":[")).isEmpty())
        assertTrue(adapter.decodeEvent(SseEvent("ping")).isEmpty())
    }

    @Test
    fun doneMarkerIsTerminal() {
        assertTrue(adapter.isTerminal(SseEvent(" [DONE] ")))
        assertFalse(adapter.isTerminal(SseEvent("""{"choices":[]}""")))
    }

    // ------------------------------------------------------------ 终帧裁决

    @Test
    fun truncationIsReportedAsLength() {
        // 有内容、无 finish_reason、无 [DONE] ⇒ 连接被掐断。
        val truncated = AdapterStreamState().apply { textChars = 42 }
        assertEquals(FinishReason.LENGTH, adapter.finish(truncated).reason)

        // 见到 [DONE] ⇒ 服务端显式宣告结束。
        val done = AdapterStreamState().apply {
            textChars = 42
            sawTerminalMarker = true
        }
        assertEquals(FinishReason.STOP, adapter.finish(done).reason)

        // 一个字符都没产出 ⇒ STOP（空回答不是截断）。
        assertEquals(
            FinishReason.STOP,
            adapter.finish(AdapterStreamState()).reason,
        )

        // 供应商给了 finish_reason ⇒ 以它为准。
        val explicit = AdapterStreamState().apply {
            textChars = 42
            finishReason = FinishReason.TOOL_CALLS
        }
        assertEquals(
            FinishReason.TOOL_CALLS,
            adapter.finish(explicit).reason,
        )
    }

    // ------------------------------------------------------------ 消息编码

    /** TOOL 消息回灌的是 `output` 原文，不是脱敏后的 displayOutput（只脱展示不脱执行）。 */
    @Test
    fun toolMessageCarriesRawOutputAndCallId() {
        val message = ChatMessage(
            role = Role.TOOL,
            toolResults = listOf(
                ToolResult(callId = "call_9", output = "sk-real-key", displayOutput = "sk-***")
            ),
        )
        val body = adapter.buildBody(request(messages = listOf(message)))
        val encoded = (body["messages"] as JsonArray).single().jsonObject

        assertEquals("tool", encoded["role"]!!.jsonPrimitive.content)
        assertEquals("call_9", encoded["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("sk-real-key", encoded["content"]!!.jsonPrimitive.content)
    }

    /** 空消息必须被过滤：部分端点对 `{"role":"user","content":""}` 直接 400。 */
    @Test
    fun emptyMessagesAreFilteredOut() {
        val body = adapter.buildBody(
            request(messages = listOf(ChatMessage(role = Role.USER, text = ""), ChatMessage(role = Role.USER, text = "x")))
        )
        assertEquals(1, (body["messages"] as JsonArray).size)
    }

    /** MODEL 消息带工具调用时，tool_calls 要按线上形态编码。 */
    @Test
    fun assistantToolCallsAreEncoded() {        val message = ChatMessage(
            role = Role.MODEL,
            text = "",
            toolCalls = listOf(ToolCall(id = "call_1", name = "f", argumentsJson = "{\"a\":1}")),
        )
        val body = adapter.buildBody(request(messages = listOf(message)))
        val encoded = (body["messages"] as JsonArray).single().jsonObject

        assertEquals("assistant", encoded["role"]!!.jsonPrimitive.content)
        val call = (encoded["tool_calls"] as JsonArray).single().jsonObject
        assertEquals("call_1", call["id"]!!.jsonPrimitive.content)
        assertEquals("function", call["type"]!!.jsonPrimitive.content)
        assertEquals("{\"a\":1}", call["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)
    }

    /**
     * 一条 TOOL 消息携带 N 个结果 ⇒ 线上必须展开成 N 条 tool 消息，id 一一对应。
     *
     * 回归用例（外审 P1-4）：旧实现只取 `firstOrNull()`，N−1 个结果静默消失，
     * OpenAI 兼容端点随即因「tool_call 无对应 result」400。
     */
    @Test
    fun multipleToolResultsExpandToMultipleWireMessages() {
        val message = ChatMessage(
            role = Role.TOOL,
            toolResults = listOf(
                ToolResult(callId = "call_a", output = "A"),
                ToolResult(callId = "call_b", output = "B"),
                ToolResult(callId = "call_c", output = "C"),
            ),
        )
        val body = adapter.buildBody(request(messages = listOf(message)))
        val encoded = body["messages"] as JsonArray

        assertEquals(3, encoded.size)
        assertEquals(
            listOf("call_a", "call_b", "call_c"),
            encoded.map { it.jsonObject["tool_call_id"]!!.jsonPrimitive.content },
        )
        assertEquals(
            listOf("A", "B", "C"),
            encoded.map { it.jsonObject["content"]!!.jsonPrimitive.content },
        )
        assertEquals(
            listOf("tool", "tool", "tool"),
            encoded.map { it.jsonObject["role"]!!.jsonPrimitive.content },
        )
    }
}
