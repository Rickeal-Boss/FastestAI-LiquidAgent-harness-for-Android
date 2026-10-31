package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * I1 修复的回归钉（W26-04）。
 *
 * W2.6 的实测证据：跨轮从 UI 气泡重建 history 时，`assistant(tool_calls)` 与
 * `tool(result)` 整段消失 —— 模型下一轮看不到自己调过什么。这条测试就是那个缺陷的
 * 反向断言：**轨迹必须进 history，且必须成对**。
 */
class ChatHistoryMapperTest {

    private fun trace(
        id: String = "c1",
        name: String = "file_read",
        args: String = """{"path":"a.txt"}""",
        status: ToolTraceStatus = ToolTraceStatus.OK,
        result: String? = "hello",
        elapsed: Long = 5L,
    ) = ToolTrace(id = id, name = name, arguments = args, status = status, result = result, elapsedMillis = elapsed)

    @Test
    fun plainUserBubbleMapsToOneMessage() {
        val messages = ChatBubble(id = "b1", role = Role.USER, text = "hi").toHistoryMessages()
        assertEquals(1, messages.size)
        assertEquals(Role.USER, messages[0].role)
        assertEquals("hi", messages[0].text)
    }

    @Test
    fun emptyBubbleMapsToNothing() {
        assertTrue(ChatBubble(id = "b1", role = Role.USER, text = "").toHistoryMessages().isEmpty())
    }

    @Test
    fun systemAndToolBubblesAreNotRebuilt() {
        assertTrue(ChatBubble(id = "b1", role = Role.SYSTEM, text = "sys").toHistoryMessages().isEmpty())
        assertTrue(ChatBubble(id = "b2", role = Role.TOOL, text = "x").toHistoryMessages().isEmpty())
    }

    @Test
    fun modelBubbleWithTraceRebuildsCallAndResult() {
        val messages = ChatBubble(
            id = "b1",
            role = Role.MODEL,
            text = "let me read it",
            traces = listOf(trace()),
        ).toHistoryMessages()

        assertEquals(2, messages.size)
        assertEquals(Role.MODEL, messages[0].role)
        assertEquals(1, messages[0].toolCalls.size)
        assertEquals("file_read", messages[0].toolCalls[0].name)
        assertEquals("""{"path":"a.txt"}""", messages[0].toolCalls[0].argumentsJson)
        assertEquals(Role.TOOL, messages[1].role)
        assertEquals("c1", messages[1].toolResults[0].callId)
        assertTrue(messages[1].toolResults[0].ok)
        assertEquals("hello", messages[1].toolResults[0].output)
    }

    @Test
    fun failedTraceKeepsErrorAndMarksNotOk() {
        val messages = ChatBubble(
            id = "b1",
            role = Role.MODEL,
            text = "",
            traces = listOf(trace(status = ToolTraceStatus.FAILED, result = "boom")),
        ).toHistoryMessages()

        assertEquals(2, messages.size)
        val result: ToolResult = messages[1].toolResults[0]
        assertTrue(!result.ok)
        assertEquals("boom", result.errorMessage)
    }

    @Test
    fun skippedTraceIsDroppedEntirely() {
        // SKIPPED 没有结果；重建声明会让 sanitizeForProvider 补一条「结果缺失」的假失败。
        val messages = ChatBubble(
            id = "b1",
            role = Role.MODEL,
            text = "no tool needed",
            traces = listOf(trace(status = ToolTraceStatus.SKIPPED, result = null)),
        ).toHistoryMessages()

        assertEquals(1, messages.size)
        assertTrue(messages[0].toolCalls.isEmpty())
    }

    @Test
    fun blankArgumentsFallBackToEmptyObject() {
        val messages = ChatBubble(
            id = "b1",
            role = Role.MODEL,
            text = "",
            traces = listOf(trace(args = "")),
        ).toHistoryMessages()
        assertEquals("{}", messages[0].toolCalls[0].argumentsJson)
    }

    @Test
    fun multipleTracesProduceOneToolMessagePerResult() {
        val messages = ChatBubble(
            id = "b1",
            role = Role.MODEL,
            text = "two calls",
            traces = listOf(trace(id = "c1"), trace(id = "c2", name = "file_list")),
        ).toHistoryMessages()

        assertEquals(3, messages.size)
        assertEquals(2, messages[0].toolCalls.size)
        assertEquals("c1", messages[1].toolResults[0].callId)
        assertEquals("c2", messages[2].toolResults[0].callId)
    }
}
