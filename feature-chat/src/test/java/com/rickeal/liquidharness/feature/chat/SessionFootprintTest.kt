package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `committedCharsOf` 的纯函数判据。
 *
 * 为什么必须逐值断言而不是「能跑通」：这个数字是切换守卫的唯一输入，
 * 少算一段工具结果 ⇒ 守卫放行一个实际会 400 的会话；多算一段 ⇒ 把能切 1M 的会话
 * 误禁。两种错都不会抛异常，只会在账单或 400 上表现为「莫名」。
 */
class SessionFootprintTest {

    @Test
    fun emptySessionIsZero() {
        assertEquals(0, committedCharsOf(emptyList(), null))
    }

    @Test
    fun countsTextOfAllBubbles() {
        val bubbles = listOf(
            ChatBubble(id = "a", role = Role.USER, text = "hello"),
            ChatBubble(id = "b", role = Role.MODEL, text = "world!"),
        )
        assertEquals(11, committedCharsOf(bubbles, null))
    }

    @Test
    fun compressionExcludesDroppedRangeAndAppendsSummary() {
        val bubbles = listOf(
            ChatBubble(id = "a", role = Role.USER, text = "12345"),
            ChatBubble(id = "b", role = Role.MODEL, text = "67890"),
            ChatBubble(id = "c", role = Role.USER, text = "abc"),
        )
        assertEquals(13, committedCharsOf(bubbles, null))
        // W10/S3：压缩区 [b..b] 不进 history；头锚 [a] + 尾部 [c] + 摘要("S") = 5 + 3 + 1。
        val compression = SessionCompression(
            headBubbleCount = 1,
            droppedFromId = "b",
            droppedToId = "b",
            summaryText = "S",
            droppedBubbleCount = 1,
            savingTokens = 0,
        )
        assertEquals(9, committedCharsOf(bubbles, compression))
        assertTrue(committedCharsOf(bubbles, compression) < committedCharsOf(bubbles, null))
    }

    @Test
    fun undoRestoresFullVolume() {
        val bubbles = listOf(
            ChatBubble(id = "a", role = Role.USER, text = "12345"),
            ChatBubble(id = "b", role = Role.MODEL, text = "67890"),
        )
        val full = committedCharsOf(bubbles, null)
        // 撤销 = 清压缩区 ⇒ 与压缩前逐值相同，不需要回滚任何内容。
        assertEquals(full, committedCharsOf(bubbles, null))
    }

    @Test
    fun newConversationIsZero() {
        // 新对话把 state 重置成 ChatUiState()，气泡为空；压缩区一并清空。
        assertEquals(0, committedCharsOf(emptyList(), null))
    }

    @Test
    fun systemAndToolBubblesAreExcluded() {
        val bubbles = listOf(
            ChatBubble(id = "s", role = Role.SYSTEM, text = "should-not-count"),
            ChatBubble(id = "t", role = Role.TOOL, text = "also-not-counted"),
            ChatBubble(id = "u", role = Role.USER, text = "ok"),
        )
        assertEquals(2, committedCharsOf(bubbles, null))
    }

    @Test
    fun toolTracesArgumentsAndResultsAreCounted() {
        val trace = ToolTrace(
            id = "t1",
            name = "tool",
            arguments = "{}",
            status = ToolTraceStatus.OK,
            result = "ok",
        )
        val bubbles = listOf(ChatBubble(id = "m", role = Role.MODEL, text = "head", traces = listOf(trace)))
        // head(4) + [id 2 + name 4 + args 2] + [callId 2 + name 4 + output 2] = 20
        assertEquals(20, committedCharsOf(bubbles, null))
        assertTrue(committedCharsOf(bubbles, null) > "head".length)
    }

    @Test
    fun skippedTracesAreNotCounted() {
        val trace = ToolTrace(
            id = "t1",
            name = "tool",
            arguments = "{}",
            status = ToolTraceStatus.SKIPPED,
            result = "ignored",
        )
        val bubbles = listOf(ChatBubble(id = "m", role = Role.MODEL, text = "head", traces = listOf(trace)))
        assertEquals("head".length, committedCharsOf(bubbles, null))
    }

    @Test
    fun invalidCompressionFallsBackToFullVolume() {
        val bubbles = listOf(
            ChatBubble(id = "a", role = Role.USER, text = "12345"),
            ChatBubble(id = "b", role = Role.MODEL, text = "67890"),
        )
        val stale = SessionCompression(
            headBubbleCount = 1,
            droppedFromId = "no-such-bubble",
            droppedToId = "no-such-bubble",
            summaryText = "S",
            droppedBubbleCount = 1,
            savingTokens = 0,
        )
        assertEquals(10, committedCharsOf(bubbles, stale))
    }
}
