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
}
