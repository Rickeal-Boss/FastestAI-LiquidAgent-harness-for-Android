package com.rickeal.liquidharness.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
/**
 * token 估算的单测。
 *
 * 判据是**量级正确**而不是精确值：估算只在「发送前预算」与「usage 缺失兜底」
 * 两处使用，其余以 provider 回报为准（00-brief A5）。
 */
class TokenEstimatorTest {

    @Test
    fun emptyTextIsZero() {
        assertEquals(0, TokenEstimator.estimate(""))
    }

    /** CJK 近似 1 token/字。 */
    @Test
    fun chineseIsAboutOneTokenPerChar() {
        val estimate = TokenEstimator.estimate("你好世界你好世界")
        assertTrue(estimate >= 8, "actual=$estimate")
        assertTrue(estimate <= 12, "actual=$estimate")
    }

    /** 英文/代码近似 4 字符/token。 */
    @Test
    fun asciiIsAboutOneTokenPerFourChars() {
        val text = "a".repeat(400)
        assertEquals(100, TokenEstimator.estimate(text))
    }

    /** 中英混排：两类分别计数，不互相污染。 */
    @Test
    fun mixedTextCountsBothClasses() {
        val estimate = TokenEstimator.estimate("你好" + "a".repeat(40))
        // 2 个汉字 + 40/4 = 2 + 10
        assertEquals(12, estimate)
    }

    /** 非空文本至少 1 token（预算判断里 0 会让「空回答」与「极短回答」无法区分）。 */
    @Test
    fun nonEmptyTextIsAtLeastOneToken() {
        assertEquals(1, TokenEstimator.estimate("a"))
    }

    /** 消息估算包含工具参数与工具结果 —— 它们同样是发出去的字节。 */
    @Test
    fun messageEstimateIncludesToolPayloads() {
        val messages = listOf(
            ChatMessage(role = Role.USER, text = "hi"),
            ChatMessage(
                role = Role.MODEL,
                toolCalls = listOf(ToolCall(id = "c1", name = "f", argumentsJson = "a".repeat(40))),
            ),
            ChatMessage(
                role = Role.TOOL,
                toolResults = listOf(ToolResult(callId = "c1", output = "a".repeat(400))),
            ),
        )
        // 3 条消息 × 4 开销 + "hi"(1) + "f"(1) + 40/4(10) + 400/4(100)
        assertEquals(12 + 1 + 1 + 10 + 100, TokenEstimator.estimateMessages(messages))
    }

    // ── W4：工具声明计入预算（design §2.3 / 判据 A6）────────────────────────

    private fun spec(
        name: String = "file_read",
        description: String = "d".repeat(200),
        parameters: List<ToolParameter> = emptyList(),
    ): ToolSpec = ToolSpec(name = name, description = description, parameters = parameters)

    /**
     * 判据 A6③（回归钉）：空工具面返回 0。
     *
     * 它保证 `system + tools + history` 在 tools 为空时与「改动前只算 system + history」
     * 逐值相同 —— 否则本波会静默改掉所有既有阈值。
     */
    @Test
    fun emptyToolSpecsEstimateZero() {
        assertEquals(0, TokenEstimator.estimateToolSpecs(emptyList()))
    }

    /** 判据 A6①：6 个内置量级的工具在 (0, 2000) 之间（量级正确，不是精确值）。 */
    @Test
    fun sixToolSpecsStayUnderTwoThousandTokens() {
        val specs = (1..6).map { spec(name = "tool_$it") }

        val estimate = TokenEstimator.estimateToolSpecs(specs)

        assertTrue(estimate > 0, "actual=$estimate")
        assertTrue(estimate < 2_000, "actual=$estimate")
    }

    /** 参数名 / 参数描述 / 枚举值都是发出去的字节。 */
    @Test
    fun toolSpecEstimateGrowsWithParameters() {
        val withoutParams = spec()
        val withParams = spec(
            parameters = listOf(
                ToolParameter(name = "path", description = "file path", enumValues = listOf("a", "b")),
            )
        )

        val delta = TokenEstimator.estimateToolSpecs(listOf(withParams)) -
            TokenEstimator.estimateToolSpecs(listOf(withoutParams))

        // "path"(1) + "file path"(3) + "a"(1) + "b"(1) = 6
        assertEquals(6, delta)
    }

    /** 描述变长 ⇒ 估算变高（工具面是 W3 接 MCP 后最容易被低估的一段）。 */
    @Test
    fun toolSpecEstimateTracksDescriptionLength() {
        val short = TokenEstimator.estimateToolSpecs(listOf(spec(description = "d".repeat(40))))
        val long = TokenEstimator.estimateToolSpecs(listOf(spec(description = "d".repeat(4_000))))

        // 只有描述不同：40/4=10 vs 4000/4=1000。
        assertEquals(1_000 - 10, long - short)
    }
}
