package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.agent.context.renderHistoryHead
import com.rickeal.liquidharness.core.agent.context.sanitizeForProvider
import com.rickeal.liquidharness.core.model.Role
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * 手动压缩（`docs/notes/w6-plan.md` §三 + W10/S3 尾部摘要前置）的 JVM 判据。
 *
 * ## 判据，各自防一种失真
 *
 * 1. **真的变小了**：20 条历史 + 紧预算 ⇒ `kept.size < 20`。只断言「没报错」区分不了
 *    「压成功」与「压缩器无损放弃了但按钮看起来正常」。
 * 2. **system 段逐字节不变**：SHA-256 比对。压缩只许丢历史，不许改写前缀 ——
 *    改写前缀会让 prompt cache 整段失效，而账单上只表现为「贵了」，是最难发现的回归。
 * 3. **压缩区可复现**：`bubbles.toHistoryMessages(compression)` 的条数必须**等于**
 *    `keptMessages` 的条数。否则下一次 `send()` 重建出的 history 与这里算的不是同一份，
 *    「压缩了」和「实际发出去的」就是两个数。
 * 4. **工具组不被拆散**：`sanitizeForProvider` 不给压缩后的历史补任何 `patch:` 合成结果。
 *    补丁出现即意味着某条 `assistant.tool_calls` 的宿主被切掉 ⇒ 真实请求 400。
 * 5. **头锚保序（W10/S3 核心钉）**：压缩后重建 history 的头锚段与压缩前**逐字节相同**
 *    （缓存前缀命中延续）；且摘要只追加在尾部、不改写任何保留消息。
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
        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 200) }

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
            compressBubbles(bubbles, budgetTokens = 200, systemText = systemText)
        }

        assertTrue(outcome.applied)
        // W10/S3：尾部摘要（SYSTEM）是唯一合法 SYSTEM 消息；systemText 不得被注入/改写。
        val systemMessages = outcome.keptMessages.filter { it.role == Role.SYSTEM }
        assertEquals(1, systemMessages.size)
        assertTrue(systemMessages.single().text.startsWith("[context summary]"))
        assertEquals(before, sha256(systemText))
    }

    @Test
    fun compressedRangeReproducesKeptMessages() {
        val bubbles = twentyBubbles()
        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 200) }

        assertTrue(outcome.applied)
        val rebuilt = bubbles.toHistoryMessages(outcome.compression)
        assertEquals(outcome.keptMessages.size, rebuilt.size)
        // 内容也要一致：只比条数的话，切错一条也会「看起来对」。
        assertEquals(outcome.keptMessages.map { it.text }, rebuilt.map { it.text })
    }

    /** W10/S3 核心钉：压缩后头锚段（最老 HEAD_ANCHOR_BUBBLES 条消息）逐字节不变。 */
    @Test
    fun headAnchorIsByteStableAfterCompression() {
        val bubbles = twentyBubbles()
        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 200) }

        assertTrue(outcome.applied)
        val before = bubbles.toHistoryMessages()
        val after = bubbles.toHistoryMessages(outcome.compression)
        val headBefore = before.take(HEAD_ANCHOR_BUBBLES)
        val headAfter = after.take(HEAD_ANCHOR_BUBBLES)
        assertEquals(headBefore.map { it.text }, headAfter.map { it.text })
        // 头锚条数本身不变（保序、不插入摘要到头锚区）。
        assertEquals(HEAD_ANCHOR_BUBBLES, headAfter.size)
        // 缓存稳定判据真值：renderHistoryHead（k=HISTORY_HEAD_SIZE=4）压缩前后**逐字节**相同
        // （审查席 R1：头锚须 ≥ k 才覆盖稳定窗）。
        // W11：`renderHistoryHead` 已只渲染进 wire 的字段（去掉 message.id），
        // `toHistoryMessages()` 每次重建的新 id 不再造成假漂移 ⇒ 整段直接比，
        // 不再剥前缀。🔴 判据自身先被验证：旧写法「双剥 `role:id:`」在新格式
        // `ROLE:text` 下会把 text 也剥掉 ⇒ 恒为空串 ⇒ 判据退化成 ⛔vacuous，
        // 故下面补一条灵敏度自证（改动一个字必须判不等）。
        assertEquals(renderHistoryHead(before), renderHistoryHead(after))
        val perturbed = listOf(before.first().copy(text = before.first().text + "!")) + before.drop(1)
        assertNotEquals(renderHistoryHead(before), renderHistoryHead(perturbed))
        // 摘要消息只在尾部追加，且是 SYSTEM 角色。
        assertTrue(after.last().role == Role.SYSTEM)
        assertTrue(after.last().text.startsWith("[context summary]"))
        // 被压区确实被移出 history（头锚之后不应再出现被压区首条气泡的正文）。
        val droppedFirstText = bubbles.toHistoryMessages()[HEAD_ANCHOR_BUBBLES].text
        assertTrue(after.map { it.text }.none { it == droppedFirstText })
    }

    /** W10/S3 + W17（外审 MC-2）：摘要模板不含被压段任何原文片段（结构事实只有条数/角色分布）。 */
    @Test
    fun summaryDoesNotEchoDroppedBodies() {
        val bubbles = twentyBubbles()
        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 200) }
        assertTrue(outcome.applied)
        val summaryText = outcome.keptMessages.last().text
        val droppedFirstText = bubbles.toHistoryMessages()[HEAD_ANCHOR_BUBBLES].text
        // 摘要是结构事实（条数/角色分布），不含「message number N」完整正文，
        // 也不含首条片段回显（snippet 已移除 —— 片段可能是密钥形态或注入指令，外审 MC-2）。
        assertTrue(summaryText.length < 200)
        assertFalse(summaryText.contains(droppedFirstText.take(32)))
    }

    @Test
    fun hugeBudgetIsNoop() {
        val bubbles = twentyBubbles()
        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 1_000_000) }

        assertNull(outcome.compression)
        assertEquals(MANUAL_COMPRESS_NOOP, outcome.reason)
        assertEquals(20, outcome.keptMessages.size)
        assertEquals(0, outcome.savingTokens)
    }

    @Test
    fun unknownCutIdFallsBackToFullHistory() {
        val bubbles = twentyBubbles()
        val stale = SessionCompression(
            headBubbleCount = 2,
            droppedFromId = "no-such-bubble",
            droppedToId = "no-such-bubble",
            summaryText = "x",
            droppedBubbleCount = 1,
            savingTokens = 0,
        )
        assertEquals(20, bubbles.toHistoryMessages(stale).size)
    }

    @Test
    fun nullCompressionKeepsEverything() {
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
        // 保留区不能以 TOOL 开头（摘要之后的第一个非摘要消息）：那说明宿主声明被切掉了。
        val nonSummary = outcome.keptMessages.filter { it.role != Role.SYSTEM }
        assertTrue(nonSummary.first().role != Role.TOOL)
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
        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 200) }
        val state = ChatUiState(
            bubbles = bubbles,
            compression = outcome.compression,
        )

        assertNotNull(state.compression)
        assertEquals(outcome.droppedBubbles, state.compressedBubbleCount)
    }

    @Test
    fun compressedBubbleCountIsZeroWithoutCompression() {
        assertEquals(0, ChatUiState(bubbles = twentyBubbles()).compressedBubbleCount)
    }

    @Test
    fun emptyBubblesAreNoop() {
        val outcome = runBlocking { compressBubbles(emptyList(), budgetTokens = 10) }
        assertNull(outcome.compression)
        assertTrue(outcome.keptMessages.isEmpty())
    }
}
