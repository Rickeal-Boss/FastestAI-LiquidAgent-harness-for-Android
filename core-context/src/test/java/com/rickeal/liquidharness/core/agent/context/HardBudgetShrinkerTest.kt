package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 硬预算收缩器的四条不变量。
 *
 * 它们全部来自「端点 400」的真实形态：`assistant.tool_calls` 与 `tool` 结果被拆散、
 * 或占位被模型读成「工具失败」而触发原样重试。所以判据不是「变短了」，
 * 而是「**结构没变、措辞不像失败、最新的那条还在**」。
 */
class HardBudgetShrinkerTest {

    private fun call(id: String) = ToolCall(id = id, name = "file_read", argumentsJson = "{}")

    private fun toolMessage(vararg sizes: Int): ChatMessage = ChatMessage(
        id = "t",
        role = Role.TOOL,
        toolResults = sizes.mapIndexed { index, size ->
            ToolResult(callId = "c$index", name = "file_read", output = "x".repeat(size))
        },
    )

    private fun history(): List<ChatMessage> = listOf(
        ChatMessage(id = "u", role = Role.USER, text = "read them"),
        ChatMessage(id = "m", role = Role.MODEL, toolCalls = listOf(call("c0"), call("c1"), call("c2"))),
        toolMessage(4_000, 4_000, 4_000),
    )

    /**
     * 收缩只换正文：**消息条数不变、callId 全在、最新的那条保持完整**。
     *
     * `callId` 丢了会被 `sanitizeForProvider` 补成「工具失败」的合成结果 ——
     * 模型看到的是「工具坏了」，而真相是「结果被省略了」，处置完全不同。
     */
    @Test
    fun shrinkingKeepsMessagesAndCallIdsAndTheNewestResult() {
        val messages = history()
        val before = HardBudgetShrinker.promptChars(messages)

        val result = HardBudgetShrinker.shrinkToCharCap(messages, 4_000)

        assertTrue(result.chars < before, "chars must actually drop: $before -> ${result.chars}")
        assertTrue(result.shrunk > 0, "shrunk count is the only proof the action happened")
        assertEquals(messages.size, result.messages.size, "no message may be dropped")
        assertEquals(
            messages.flatMap { it.toolResults }.map { it.callId },
            result.messages.flatMap { it.toolResults }.map { it.callId },
        )
        // 最后一条结果保持完整：这一轮至少要回一条可用证据。
        val last = result.messages.flatMap { it.toolResults }.last()
        assertEquals("x".repeat(4_000), last.output)
    }

    /** 占位措辞**绝不像失败**（`omitted` 而不是 error / failed）：像失败会让模型重试同一次调用。 */
    @Test
    fun placeholderNeverLooksLikeFailure() {
        val result = HardBudgetShrinker.shrinkToCharCap(history(), 1)

        val placeholder = result.messages
            .flatMap { it.toolResults }
            .first { it.output.startsWith("[omitted:") }
            .output
        assertTrue(placeholder.startsWith("[omitted: hard budget, "), placeholder)
        assertTrue(placeholder.contains("4000 chars"), placeholder)
        assertFalse(placeholder.contains("error"), placeholder)
        assertFalse(placeholder.contains("fail"), placeholder)
        assertTrue(result.messages.flatMap { it.toolResults }.any { it.truncated })
    }

    /** 已经在预算内 ⇒ **返回同一个对象**且 `shrunk == 0`（调用方据此判「没变化」）。 */
    @Test
    fun withinCapIsANoOp() {
        val messages = history()
        val result = HardBudgetShrinker.shrinkToCharCap(messages, 1_000_000)

        assertEquals(0, result.shrunk)
        assertSame(messages, result.messages)
    }

    /**
     * 只有一个可收缩结果时不许动它。
     *
     * 「最后一条保持完整」若实现成「跳过整个最后一条消息」，单轮 + 单工具时一条都砍不动；
     * 若实现成「一条也不留」，这一轮等于什么都没回。两个极端都是缺陷，
     * 所以只有 1 条可收缩结果时**明确不动**（由调用方走熔断）。
     */
    @Test
    fun singleResultIsNeverShrunk() {
        val messages = listOf(toolMessage(9_000))
        val result = HardBudgetShrinker.shrinkToCharCap(messages, 10)

        assertEquals(0, result.shrunk)
        assertSame(messages, result.messages)
    }

    /** 占位不许被二次包裹：再包一层会把「省了 4000 字符」抹成「省了 40 字符」。 */
    @Test
    fun alreadyOmittedResultsAreNotWrappedAgain() {
        val messages = listOf(
            toolMessage(4_000, 4_000),
            toolMessage(4_000, 4_000),
        )
        val first = HardBudgetShrinker.shrinkToCharCap(messages, 100)
        val before = first.messages.flatMap { it.toolResults }
            .filter { it.output.startsWith("[omitted:") }
            .map { it.output }
        assertTrue(before.isNotEmpty(), "first pass must produce placeholders")

        val second = HardBudgetShrinker.shrinkToCharCap(first.messages, 100)
        val after = second.messages.flatMap { it.toolResults }
            .filter { it.output.startsWith("[omitted:") }
            .map { it.output }

        // 已经存在的占位必须**逐字保留**：套娃的形态是「占位的占位」，
        // 一旦发生，原有那条 `[omitted: hard budget, 4000 chars]` 就会消失。
        assertTrue(after.containsAll(before), "placeholder was rewrapped: $after")
    }
}
