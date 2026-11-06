package com.rickeal.liquidharness.core.engine.provider

import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.CustomProtocol
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.ThinkingMode
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

    /**
     * `cached_tokens` 的**三态透传**：缺字段 ⇒ `null`（未上报），显式 0 ⇒ 0（未命中），
     * 有值 ⇒ 值。
     *
     * 旧实现在这里写 `?: 0`，把「供应商没报」压成「0 命中」—— 两者处置相反
     * （查端点是否支持该字段 vs 查前缀是否稳定），压平之后下游再也不可能分开。
     */
    @Test
    fun cachedTokensArePassedThroughAsThreeStates() {
        fun cachedOf(usageJson: String): Int? {
            val events = adapter.decodeEvent(SseEvent("""{"choices":[],"usage":$usageJson}"""))
            return (events.single() as GenerationEvent.Usage).cachedPrompt
        }

        // 1) 整个 prompt_tokens_details 缺失 ⇒ 未上报。
        assertNull(cachedOf("""{"prompt_tokens":10,"completion_tokens":5}"""))
        // 2) 明细对象在、但里面没有 cached_tokens ⇒ 同样算未上报（不是 0）。
        assertNull(cachedOf("""{"prompt_tokens":10,"completion_tokens":5,"prompt_tokens_details":{}}"""))
        // 3) 显式 0 ⇒ 上报了，且确实没命中。
        assertEquals(
            0,
            cachedOf(
                """{"prompt_tokens":10,"completion_tokens":5,"prompt_tokens_details":{"cached_tokens":0}}"""
            ),
        )
        // 4) 命中值原样透传。
        assertEquals(
            64,
            cachedOf(
                """{"prompt_tokens":10,"completion_tokens":5,"prompt_tokens_details":{"cached_tokens":64}}"""
            ),
        )
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

    // ------------------------------------------------------------ 实测窗口解析（W5-02 / T6）

    /**
     * 400 错误体里的真实窗口必须被解析出来，且**不许误抓别的数字**。
     *
     * 文案出处 `w5-provider-matrix.md:89-93`（9 次观测完全一致）。把 `max_tokens` /
     * `requested N tokens` 里的数字当成窗口，会写回一个**更错**的预算 ——
     * 那比不写回更糟：预算一旦错，后续每次请求的判据全部失真且没有任何报错。
     */
    @Test
    fun contextLimitHintIsParsedFromTheOverflowBody() {
        val real = """{"error":{"message":"This model's maximum context length is 1000000 """ +
            """tokens. However, you requested 1200000 tokens (1195904 in the messages, 4096 in """ +
            """the completion). Please reduce the length of the messages or completion."}}"""
        assertEquals(1_000_000, adapter.contextLimitHint(real))

        // 大小写 / 多余空白放宽（部分网关会改写措辞）。
        assertEquals(200_000, adapter.contextLimitHint("Maximum   Context Length Is 200000 tokens"))

        // 反 vacuous：不含该句时必须是 null（否则「解析」等于「随便抓一个数字」）。
        assertNull(adapter.contextLimitHint(""))
        assertNull(adapter.contextLimitHint("rate limited, retry later"))
        assertNull(adapter.contextLimitHint("""{"error":{"message":"maximum tokens is 4096"}}"""))
        assertNull(adapter.contextLimitHint("""{"error":{"message":"context length is abc"}}"""))
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

    // ------------------------------------------------------------ W8 高级配置

    /**
     * Q11：请求体键集合**恰好**等于白名单 —— 多一个键或漏一个键都要红。
     * 这条是「逐字段断言」的总闸：以后有人加字段忘了同步白名单，这里先红。
     */
    @Test
    fun bodyKeysMatchWhitelistWithoutJsonMode() {
        val body = adapter.buildBody(
            request(
                tools = listOf(ToolSpec(name = "x", description = "d")),
                sampling = SamplingParams(temperature = 0.5, topP = 0.9, maxTokens = 128, stop = listOf("STOP")),
                endpoint = endpoint(reasoningStyle = ReasoningStyle.OPENROUTER_REASONING_OBJECT)
                    .copy(supportsReasoning = true, thinkingMode = ThinkingMode.ON),
            )
        )
        assertEquals(
            setOf(
                "model", "messages", "stream", "stream_options", "temperature", "top_p",
                "max_tokens", "stop", "tools", "tool_choice", "reasoning",
            ),
            body.keys,
        )
    }

    /** jsonMode 两态：false 不发 response_format；true 发且 type=json_object。 */
    @Test
    fun jsonModeTogglesResponseFormat() {
        val off = adapter.buildBody(request(endpoint = endpoint().copy(jsonMode = false)))
        assertNull(off["response_format"], "关闭时不得发该字段（部分端点会 400）")
        val on = adapter.buildBody(request(endpoint = endpoint().copy(jsonMode = true)))
        assertEquals("json_object", on["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    /** 思考四态线上形态（dots 流式实测口径）。 */
    @Test
    fun openRouterReasoningObjectEmitsPerThinkingMode() {
        val orEndpoint = endpoint(reasoningStyle = ReasoningStyle.OPENROUTER_REASONING_OBJECT)
            .copy(supportsReasoning = true, allowDisableThinking = true)

        val off = adapter.buildBody(request(endpoint = orEndpoint.copy(thinkingMode = ThinkingMode.OFF)))
        assertFalse(off["reasoning"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
        assertNull(off["include_reasoning"], "OFF 与 HIDE_ONLY 不得同发")

        val hide = adapter.buildBody(request(endpoint = orEndpoint.copy(thinkingMode = ThinkingMode.HIDE_ONLY)))
        assertFalse(hide["include_reasoning"]!!.jsonPrimitive.boolean)
        assertNull(hide["reasoning"], "隐藏 ≠ 关闭：不得发 reasoning 对象")

        val on = adapter.buildBody(
            request(endpoint = orEndpoint.copy(thinkingMode = ThinkingMode.ON, reasoningEffort = "high"))
        )
        assertEquals("high", on["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertNull(on["reasoning"]!!.jsonObject["max_tokens"], "effort 与 max_tokens 绝不共存")

        val follow = adapter.buildBody(
            request(endpoint = orEndpoint.copy(thinkingMode = ThinkingMode.FOLLOW_PROVIDER))
        )
        assertNull(follow["reasoning"])
        assertNull(follow["include_reasoning"])
    }

    /** 真关不可达 ⇒ 降级 HIDE_ONLY（不静默发一个网关不认的字段）。 */
    @Test
    fun hardOffDegradesToHideOnlyWhenUnsupported() {
        val e = endpoint(reasoningStyle = ReasoningStyle.OPENROUTER_REASONING_OBJECT)
            .copy(supportsReasoning = true, allowDisableThinking = false, thinkingMode = ThinkingMode.OFF)
        val body = adapter.buildBody(request(endpoint = e))
        assertFalse(body["include_reasoning"]!!.jsonPrimitive.boolean, "降级为 HIDE_ONLY")
        assertNull(body["reasoning"], "不可达的真关绝不发 reasoning 对象")
    }

    /** 第五档 EXTREME 永不落 max：预算足降 xhigh、不足降 high。 */
    @Test
    fun extremeEffortNeverEmitsMax() {
        val orEndpoint = endpoint(reasoningStyle = ReasoningStyle.OPENROUTER_REASONING_OBJECT)
            .copy(supportsReasoning = true, thinkingMode = ThinkingMode.ON)
        val enough = adapter.buildBody(
            request(endpoint = orEndpoint.copy(reasoningEffort = "extreme", maxOutputTokens = 8000))
        )
        assertEquals("xhigh", enough["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        val tight = adapter.buildBody(
            request(endpoint = orEndpoint.copy(reasoningEffort = "extreme", maxOutputTokens = 512))
        )
        assertEquals("high", tight["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
    }

    /** 自定义协议：非保留键可追加（值转原始标量），保留键一律跳过。 */
    @Test
    fun customProtocolAddsNewKeysButNeverOverridesReserved() {
        val e = endpoint().copy(
            customProtocol = CustomProtocol(
                enabled = true,
                extraBody = mapOf("top_k" to "40", "flag" to "true", "note" to "hi"),
            ),
        )
        val body = adapter.buildBody(request(endpoint = e))
        assertEquals(40, body["top_k"]!!.jsonPrimitive.int)
        assertTrue(body["flag"]!!.jsonPrimitive.boolean)
        assertEquals("hi", body["note"]!!.jsonPrimitive.content)
        // max_tokens 仍由 sampling / endpoint 决定，不得被 extraBody 覆盖。
        assertEquals(4096, body["max_tokens"]!!.jsonPrimitive.int)
    }

    /** 关闭时 extraBody 完全不参与请求体（fail-closed）。 */
    @Test
    fun disabledCustomProtocolEmitsNothing() {
        val e = endpoint().copy(
            customProtocol = CustomProtocol(enabled = false, extraBody = mapOf("top_k" to "40")),
        )
        assertNull(adapter.buildBody(request(endpoint = e))["top_k"])
    }

    // ------------------------------------------------------------ W9-00 运行时防线

    /** P1-3 反例：NaN / Inf / Infinity 转成非有限 Double 会让 buildBody 抛异常。 */
    @Test
    fun nonFiniteExtraBodyValuesNeverBreakBuildBody() {
        for (value in listOf("NaN", "Inf", "Infinity", "-Infinity")) {
            val e = endpoint().copy(
                customProtocol = CustomProtocol(enabled = true, extraBody = mapOf("foo" to value)),
            )
            val body = adapter.buildBody(request(endpoint = e))
            assertEquals(value, body["foo"]!!.jsonPrimitive.content, "非有限值必须按字符串保留")
        }
        // 已知必通过样本：收紧不得把普通数值也挡掉（防恒绿 / 假红）。
        val clean = endpoint().copy(
            customProtocol = CustomProtocol(enabled = true, extraBody = mapOf("top_k" to "40")),
        )
        assertEquals(40, adapter.buildBody(request(endpoint = clean))["top_k"]!!.jsonPrimitive.int)
    }

    /** F-1：运行时与保存链复用同一张表，值里含凭据形态的键不得进请求体。 */
    @Test
    fun runtimeCustomProtocolSkipsCredentialishValues() {
        val e = endpoint().copy(
            customProtocol = CustomProtocol(
                enabled = true,
                extraBody = mapOf("foo" to "sk-live-xxxx", "top_k" to "40"),
            ),
        )
        val body = adapter.buildBody(request(endpoint = e))
        assertNull(body["foo"], "凭据形态值不得进请求体")
        assertEquals(40, body["top_k"]!!.jsonPrimitive.int, "干净键仍照常追加")
    }
}
