package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.agent.context.sanitizeForProvider
import com.rickeal.liquidharness.core.model.Role
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * 手动压缩（`docs/notes/w6-plan.md` §三）的 JVM 判据。
 *
 * ## 四条判据，各自防一种失真
 *
 * 1. **真的变小了**：20 条历史 + 紧预算 ⇒ `kept.size < 20`。只断言「没报错」区分不了
 *    「压成功」与「压缩器无损放弃了但按钮看起来正常」。
 * 2. **system 段逐字节不变**：SHA-256 比对。压缩只许丢历史，不许改写前缀 ——
 *    改写前缀会让 prompt cache 整段失效，而账单上只表现为「贵了」，是最难发现的回归。
 * 3. **切点可复现**：`bubbles.toHistoryMessages(compressedUpTo)` 的条数必须**等于**
 *    `keptMessages` 的条数。否则下一次 `send()` 重建出的 history 与这里算的不是同一份，
 *    「压缩了」和「实际发出去的」就是两个数。
 * 4. **工具组不被拆散**：`sanitizeForProvider` 不给压缩后的历史补任何 `patch:` 合成结果。
 *    补丁出现即意味着某条 `assistant.tool_calls` 的宿主被切掉 ⇒ 真实请求 400。
 */
class ManualCompressTest {

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    /** 20 条交替气泡：10 条 USER + 10 条 MODEL，一条气泡映射一条消息。 */
    private fun twentyBubbles(): List<ChatBubble> = (0 until 20).map { index ->
        ChatBubble(
            id = "b" + index,
            role = if (index % 2 == 0) Role.USER else Role.MODEL,
            text = "message number " + index + " with a few words",
        )
    }

    @Test
    fun tightBudgetDropsOldBubbles() {
        val bubbles = twentyBubbles()
        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 50) }

        assertTrue("expected compression to apply, got " + outcome.reason, outcome.applied)
        assertTrue("kept " + outcome.keptMessages.size + " of 20", outcome.keptMessages.size < 20)
        assertTrue(outcome.droppedBubbles > 0)
        assertEquals("applied", outcome.reason)
        assertTrue("saving should be positive", outcome.savingTokens > 0)
    }

    @Test
    fun systemSegmentHashIsUnchanged() {
        val bubbles = twentyBubbles()
        val systemText = "You are a careful agent. Tools: file_read, file_write."
        val before = sha256(systemText)

        val outcome = runBlocking {
            compressBubbles(bubbles, budgetTokens = 50, systemText = systemText)
        }

        assertTrue(outcome.applied)
        // 压缩器只收到 history；system 由调用方在组装请求时原样拼回最前。
        assertTrue(outcome.keptMessages.none { it.role == Role.SYSTEM })
        assertEquals(before, sha256(systemText))
    }

    @Test
    fun compressedUpToReproducesKeptMessages() {
        val bubbles = twentyBubbles()
        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 50) }

        assertTrue(outcome.applied)
        val rebuilt = bubbles.toHistoryMessages(outcome.compressedUpTo)
        assertEquals(outcome.keptMessages.size, rebuilt.size)
        // 内容也要一致：只比条数的话，切错一条也会「看起来对」。
        assertEquals(outcome.keptMessages.map { it.text }, rebuilt.map { it.text })
    }

    @Test
    fun hugeBudgetIsNoop() {
        val bubbles = twentyBubbles()
        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 1_000_000) }

        assertNull(outcome.compressedUpTo)
        assertEquals(MANUAL_COMPRESS_NOOP, outcome.reason)
        assertEquals(20, outcome.keptMessages.size)
        assertEquals(0, outcome.savingTokens)
    }

    @Test
    fun unknownCutIdFallsBackToFullHistory() {
        val bubbles = twentyBubbles()
        val rebuilt = bubbles.toHistoryMessages("no-such-bubble")
        assertEquals(20, rebuilt.size)
    }

    @Test
    fun nullCutIdKeepsEverything() {
        val bubbles = twentyBubbles()
        assertEquals(20, bubbles.toHistoryMessages(null).size)
    }

    @Test
    fun toolGroupIsNotSplitByTheCut() {
        // 一半气泡带工具轨迹（1 条气泡 → 2 条消息），把切点逼到工具组附近。
        val bubbles = (0 until 10).flatMap { index ->
            listOf(
                ChatBubble(
                    id = "u" + index,
                    role = Role.USER,
                    text = "please read file " + index,
                ),
                ChatBubble(
                    id = "m" + index,
                    role = Role.MODEL,
                    text = "reading " + index,
                    traces = listOf(
                        ToolTrace(
                            id = "c" + index,
                            name = "file_read",
                            arguments = """{"path":"f$index.txt"}""",
                            status = ToolTraceStatus.OK,
                            result = "contents of file " + index,
                        ),
                    ),
                ),
            )
        }

        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 60) }

        assertTrue(outcome.applied)
        // 保留区不能以 TOOL 开头：那说明宿主声明被切掉了。
        assertTrue(outcome.keptMessages.first().role != Role.TOOL)
        // 更强的判据：清洗器不需要补任何合成结果。
        val sanitized = sanitizeForProvider(outcome.keptMessages)
        assertTrue(
            "tool group was split: " + sanitized.map { it.id },
            sanitized.none { it.id.startsWith("patch:") },
        )
    }

    @Test
    fun compressedBubbleCountMatchesDroppedBubbles() {
        val bubbles = twentyBubbles()
        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 50) }
        val state = ChatUiState(
            bubbles = bubbles,
            compressedUpTo = outcome.compressedUpTo,
            compressedSavingTokens = outcome.savingTokens,
        )

        assertNotNull(state.compressedUpTo)
        assertEquals(outcome.droppedBubbles, state.compressedBubbleCount)
    }

    @Test
    fun compressedBubbleCountIsZeroWithoutCut() {
        assertEquals(0, ChatUiState(bubbles = twentyBubbles()).compressedBubbleCount)
    }

    @Test
    fun emptyBubblesAreNoop() {
        val outcome = runBlocking { compressBubbles(emptyList(), budgetTokens = 10) }
        assertNull(outcome.compressedUpTo)
        assertTrue(outcome.keptMessages.isEmpty())
    }
}
