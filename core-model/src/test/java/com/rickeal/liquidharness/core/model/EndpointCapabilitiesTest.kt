package com.rickeal.liquidharness.core.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * W8 端点高级配置的纯函数判据（JVM，不需要 Android）。
 *
 * 判据自身先被验证（AGENTS.md §二.3）：每组先跑一条**已知必然通过 / 必然失败**的样本，
 * 再跑边界。所有档位判据都钉住「`wire != "max"`」这条硬不变量 —— 第五档 UI 档位
 * `EXTREME` 永远不能直接落网（实测 `effort:"max"` 的 rtok 落在 high 与 medium 之间，不单调）。
 */
class EndpointCapabilitiesTest {

    private val lenient = AgentJson.json

    // ------------------------------------------------------------ V1 老配置兼容

    /**
     * 老 `endpoints.json`（W7 的 16 个键）缺全部新键 ⇒ 必须读到**默认值**，
     * 而不是抛异常（`JsonFileStore.read()` 会吞异常回落 seeds ⇒ 静默清空用户端点）。
     */
    @Test
    fun oldConfigDecodesWithNewFieldDefaults() {
        val legacy = """
            [{"id":"ep-1","name":"nv1","kind":"OPENAI_COMPATIBLE","preset":"CUSTOM",
              "baseUrl":"https://integrate.api.nvidia.com/v1","modelId":"nvidia/nemotron",
              "contextLength":1048576,"maxOutputTokens":4096,
              "supportsTools":true,"supportsReasoning":false,
              "reasoningStyle":"NONE","reasoningEffort":"medium",
              "extraHeaders":{},"allowPrivateNetwork":false,"hasSecret":false,
              "addedAtMillis":1730000000000}]
        """.trimIndent()
        val decoded = Json.decodeFromString(ListSerializer(RemoteEndpoint.serializer()), legacy).single()

        assertEquals("ep-1", decoded.id)
        assertEquals(ReasoningStyle.NONE, decoded.reasoningStyle)
        assertEquals(ThinkingMode.FOLLOW_PROVIDER, decoded.thinkingMode, "默认不发思考开关 ⇒ 现役行为零回归")
        assertFalse(decoded.allowDisableThinking)
        assertTrue(decoded.supportedEfforts.isEmpty(), "空集 = 端点未声明，不是「全支持」")
        assertFalse(decoded.jsonMode)
        assertEquals(setOf(Modality.TEXT), decoded.inputModalities, "默认 fail-closed：不自动开图片输入")
        assertEquals(setOf(Modality.TEXT), decoded.outputModalities)
        assertFalse(decoded.customProtocol.enabled)
    }

    // ------------------------------------------------------------ 派生能力

    @Test
    fun supportsVisionDerivesFromImageModality() {
        val base = RemoteEndpoint()
        assertFalse(base.supportsVision, "默认 fail-closed")
        assertTrue(base.copy(inputModalities = setOf(Modality.TEXT, Modality.IMAGE)).supportsVision)
        assertFalse(base.copy(outputModalities = setOf(Modality.AUDIO)).supportsVision, "输出音频不等于输入图片")
    }

    @Test
    fun effectiveThinkingModeForcesFollowProviderWithoutReasoningCap() {
        val noCap = RemoteEndpoint(supportsReasoning = false, thinkingMode = ThinkingMode.ON)
        assertEquals(ThinkingMode.FOLLOW_PROVIDER, noCap.effectiveThinkingMode)
        val withCap = RemoteEndpoint(supportsReasoning = true, thinkingMode = ThinkingMode.ON)
        assertEquals(ThinkingMode.ON, withCap.effectiveThinkingMode)
        // FOLLOW_PROVIDER 永不被动改写
        assertEquals(
            ThinkingMode.FOLLOW_PROVIDER,
            RemoteEndpoint(supportsReasoning = false, thinkingMode = ThinkingMode.FOLLOW_PROVIDER)
                .effectiveThinkingMode,
        )
    }

    @Test
    fun canHardOffRequiresAllThreeConditions() {
        val all = RemoteEndpoint(
            supportsReasoning = true,
            allowDisableThinking = true,
            reasoningStyle = ReasoningStyle.OPENROUTER_REASONING_OBJECT,
        )
        assertTrue(all.canHardOff)
        assertFalse(
            all.copy(allowDisableThinking = false).canHardOff,
            "未声明 mandatory:false ⇒ 不许真关",
        )
        assertFalse(
            all.copy(reasoningStyle = ReasoningStyle.REASONING_EFFORT).canHardOff,
            "旧 style 无 enabled:false 通道",
        )
        assertFalse(
            all.copy(supportsReasoning = false).canHardOff,
            "端点不能思考 ⇒ 无从关闭",
        )
    }

    // ------------------------------------------------------------ 档位归一

    private val allSupported = setOf(
        ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH, ReasoningEffort.XHIGH,
    )

    @Test
    fun extremeNeverEmitsMaxDegradesToXhighWithReason() {
        val r = normalizeEffort(
            desired = "extreme",
            supported = allSupported,
            style = ReasoningStyle.OPENROUTER_REASONING_OBJECT,
            maxOut = 8000,
        )
        assertEquals("xhigh", r.wire)
        assertEquals(ReasoningEffort.EXTREME, r.degradedFrom)
        assertEquals("unsupported_effort_max_ignored", r.reason)
    }

    @Test
    fun extremeDropsToHighWhenOutputBudgetTooSmall() {
        val r = normalizeEffort(
            desired = "extreme",
            supported = allSupported,
            style = ReasoningStyle.OPENROUTER_REASONING_OBJECT,
            maxOut = 512,
        )
        assertEquals("high", r.wire)
        assertEquals("output_budget_insufficient:512", r.reason)
    }

    @Test
    fun unsupportedEffortFallsBackToLowerNeighborWithReason() {
        // 端点只声明到 high，用户要 xhigh ⇒ 降 high 且**必须落 reason**，不许静默。
        val r = normalizeEffort(
            desired = "xhigh",
            supported = setOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            style = ReasoningStyle.OPENROUTER_REASONING_OBJECT,
            maxOut = 8000,
        )
        assertEquals("high", r.wire)
        assertEquals(ReasoningEffort.XHIGH, r.degradedFrom)
        assertTrue(r.reason!!.startsWith("effort_unsupported:"))
    }

    @Test
    fun xhighWithinBudgetPassesThroughUnchanged() {
        val r = normalizeEffort(
            desired = "xhigh",
            supported = allSupported,
            style = ReasoningStyle.OPENROUTER_REASONING_OBJECT,
            maxOut = 4000,
        )
        assertNull(r.degradedFrom, "无降级时不落 reason（否则 UI 把正常档位当异常）")
        assertNull(r.reason)
        assertEquals("xhigh", r.wire)
    }

    @Test
    fun emptySupportedMeansUndeclaredNotAllSupported() {
        // 空集按已实测四档处理，但**不得**因为「未声明」就放行第五档的 max。
        val r = normalizeEffort(
            desired = "extreme",
            supported = emptySet(),
            style = ReasoningStyle.OPENROUTER_REASONING_OBJECT,
            maxOut = 8000,
        )
        assertEquals("xhigh", r.wire)
    }

    @Test
    fun styleWithoutEffortCarriesNoWireValue() {
        for (style in listOf(
            ReasoningStyle.NONE,
            ReasoningStyle.ENABLE_THINKING_BOOL,
            ReasoningStyle.CHAT_TEMPLATE_KWARGS,
            ReasoningStyle.ANTHROPIC_THINKING_BUDGET,
            ReasoningStyle.GEMINI_INCLUDE_THOUGHTS,
        )) {
            assertNull(
                normalizeEffort("high", allSupported, style, 8000).wire,
                "style=$style 不承载 effort",
            )
        }
    }

    @Test
    fun unrecognizedDesiredFallsBackToMedium() {
        val r = normalizeEffort(
            desired = "yolo",
            supported = allSupported,
            style = ReasoningStyle.OPENROUTER_REASONING_OBJECT,
            maxOut = 8000,
        )
        assertEquals("medium", r.wire)
        assertTrue(r.reason!!.startsWith("effort_unrecognized"))
    }

    @Test
    fun wireNeverEqualsMaxAcrossAllEfforts() {
        for (effort in ReasoningEffort.DISPLAY_ORDER) {
            for (maxOut in listOf(256, 1500, 4096, 100000)) {
                val r = normalizeEffort(effort.name.lowercase(), allSupported, ReasoningStyle.OPENROUTER_REASONING_OBJECT, maxOut)
                assertTrue(r.wire != "max", "effort=$effort maxOut=$maxOut")
            }
        }
    }

    // ------------------------------------------------------------ 自定义协议校验

    @Test
    fun validateAdvancedRejectsReservedAndCredentialKeys() {
        val bad = RemoteEndpoint(
            customProtocol = CustomProtocol(
                enabled = true,
                extraBody = mapOf(
                    "top_k" to "40",              // 合法：非保留键
                    "max_tokens" to "1",          // 保留键 ⇒ 拒
                    "api_key" to "x",             // 凭据语义 ⇒ 拒
                    "Authorization" to "Bearer y", // 凭据语义 ⇒ 拒
                ),
            ),
        )
        val errors = validateAdvanced(bad)
        assertEquals(3, errors.size, "只允许 top_k 通过：$errors")
        assertTrue(errors.any { it.contains("max_tokens") })
        assertTrue(errors.any { it.contains("api_key") })
        assertTrue(errors.any { it.contains("Authorization") })
    }

    @Test
    fun validateAdvancedPassesWhenDisabledOrClean() {
        assertTrue(validateAdvanced(RemoteEndpoint()).isEmpty(), "未开启 ⇒ 无错误")
        assertTrue(
            validateAdvanced(
                RemoteEndpoint(customProtocol = CustomProtocol(enabled = true, extraBody = mapOf("top_k" to "40"))),
            ).isEmpty(),
        )
    }

    // ------------------------------------------------------------ W9-00 防线接线

    @Test
    fun credentialishKeyFragmentTokenIsRejectedAtRuntime() {
        // P2 词表补 token：G17 词表含 token，运行时词表曾漏 ⇒ access_token=leak 必红。
        val bad = RemoteEndpoint(
            customProtocol = CustomProtocol(enabled = true, extraBody = mapOf("access_token" to "leak")),
        )
        assertTrue(validateAdvanced(bad).isNotEmpty(), "access_token 必须被拒")
        // 已知必通过样本：同形态但键名不含凭据语义。
        assertTrue(
            validateAdvanced(
                RemoteEndpoint(customProtocol = CustomProtocol(enabled = true, extraBody = mapOf("top_k" to "40"))),
            ).isEmpty(),
        )
    }

    @Test
    fun credentialishValueAndAllowlistAreEnforced() {
        // F-1 / A-3：值里塞 sk- 必被拒（含值的凭据形态扫描），未知键被白名单拦下。
        val withSecretValue = RemoteEndpoint(
            customProtocol = CustomProtocol(enabled = true, extraBody = mapOf("top_k" to "sk-live-xxxx")),
        )
        assertTrue(validateAdvanced(withSecretValue).any { it.contains("value") })
        val unknownKey = RemoteEndpoint(
            customProtocol = CustomProtocol(enabled = true, extraBody = mapOf("foo" to "plain")),
        )
        assertTrue(validateAdvanced(unknownKey).any { it.contains("allowlisted") })
        // 干净样本 0 命中（判据自身先验证，避免恒绿断言）。
        assertTrue(
            validateAdvanced(
                RemoteEndpoint(customProtocol = CustomProtocol(enabled = true, extraBody = mapOf("seed" to "7"))),
            ).isEmpty(),
        )
    }

    @Test
    fun extremeDegradationNeverEmitsAnUnsupportedEffort() {
        // P1-4 反例：端点只声明 LOW，旧实现无条件发 "high"。
        val r = normalizeEffort(
            desired = "extreme",
            supported = setOf(ReasoningEffort.LOW),
            style = ReasoningStyle.OPENROUTER_REASONING_OBJECT,
            maxOut = 8000,
        )
        assertTrue(r.wire != "high", "wire=" + r.wire)
        assertEquals("low", r.wire)
        assertTrue(r.reason != null, "降级必须落 reason 供上屏")
        // 无任何可下发档位 ⇒ wire=null，绝不硬发未声明档位。
        val none = normalizeEffort(
            desired = "extreme",
            supported = setOf(ReasoningEffort.EXTREME),
            style = ReasoningStyle.OPENROUTER_REASONING_OBJECT,
            maxOut = 8000,
        )
        assertNull(none.wire)
    }

    @Test
    fun hardOffDowngradeIsVisibleSignal() {
        // P0-2 / P1-1：用户选 OFF 但不可真关 ⇒ 必须有「已降级」信号上屏（真/假两态）。
        val unsupported = RemoteEndpoint(
            supportsReasoning = true,
            allowDisableThinking = false,
            reasoningStyle = ReasoningStyle.OPENROUTER_REASONING_OBJECT,
            thinkingMode = ThinkingMode.OFF,
        )
        assertTrue(unsupported.hardOffDowngraded)
        assertFalse(unsupported.copy(allowDisableThinking = true).hardOffDowngraded, "可真关时不得谎报降级")
    }

    @Test
    fun parseExtraBodyKeepsCommaSeparatedPairs() {
        // P1-2：输入 "a=1, b=2" 两键都在（吞 ", " 后缀会只剩 1 键）。
        val parsed = parseExtraBody("a=1, b=2")
        assertEquals(2, parsed.size)
        assertEquals("1", parsed["a"])
        assertEquals("2", parsed["b"])
    }

    @Test
    fun openAiAdvancedFlagsTrackProviderKind() {
        // P2 kind 收窄：非 OPENAI_COMPATIBLE 端点上 jsonMode / customProtocol 不静默可用。
        assertTrue(RemoteEndpoint().supportsOpenAiAdvanced)
        assertFalse(RemoteEndpoint(kind = ProviderKind.ANTHROPIC).supportsOpenAiAdvanced)
        assertFalse(RemoteEndpoint(kind = ProviderKind.GEMINI).supportsOpenAiAdvanced)
    }
}
