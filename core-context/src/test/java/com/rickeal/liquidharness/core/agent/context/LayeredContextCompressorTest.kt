package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * L2 包 L1 的级联压缩单测。纯 JVM（FakeSummarizer，无网络）。
 *
 * 钉住的判据：
 * - B2：摘要消息不破坏 tool 配对 ⇒ 压缩后 `sanitizeForProvider` 是 no-op；
 * - B4：去掉摘要后，保留区必须是入参后缀（不许重排）；
 * - B5 / B8：摘要失败 / 抛异常 / 超时 ⇒ 回落 L1，**不抛异常**；
 * - B10：摘要消息带可识别前缀（`canMerge` 的放行依据）。
 */
class LayeredContextCompressorTest {

    private val huge = "x".repeat(20_000)

    private fun user(text: String) = ChatMessage(role = Role.USER, text = text)

    private fun modelWithCall(callId: String) = ChatMessage(
        role = Role.MODEL,
        toolCalls = listOf(ToolCall(id = callId, name = "file_read", argumentsJson = "{}")),
    )

    private fun toolResultFor(callId: String, output: String = "") = ChatMessage(
        role = Role.TOOL,
        toolResults = listOf(ToolResult(callId = callId, name = "file_read", output = output)),
    )

    /** 预置摘要器：只回一个固定结果，并记录调用次数与入参。 */
    private class ScriptedSummarizer(private val result: SummaryResult) : ContextSummarizer {
        var calls: Int = 0
            private set
        var lastTargetTokens: Int = -1
            private set
        var lastMessages: List<ChatMessage> = emptyList()
            private set

        override suspend fun summarize(messages: List<ChatMessage>, targetTokens: Int): SummaryResult {
            calls += 1
            lastMessages = messages
            lastTargetTokens = targetTokens
            return result
        }
    }

    private class ThrowingSummarizer : ContextSummarizer {
        override suspend fun summarize(messages: List<ChatMessage>, targetTokens: Int): SummaryResult =
            throw IllegalStateException("boom")
    }

    private fun summaryText(): String = listOf(
        StructuredSummary.BOUNDARY_LINE,
        "GOAL: keep the task alive",
        "FACTS:",
        "- the file is at /tmp/a.txt",
        "DECISIONS:",
        "- use the l1 cut point",
        "DONE:",
        "- read the file",
        "OPEN TODOS:",
        "- write the report",
    ).joinToString("\n")

    private fun compressor(summarizer: ContextSummarizer?) = LayeredContextCompressor(
        delegate = WindowContextCompressor(),
        summarizer = summarizer,
    )

    @Test
    fun withinBudgetReturnsSameInstance() {
        val messages = listOf(user("a"), user("b"))
        val summarizer = ScriptedSummarizer(SummaryResult.Ok(summaryText(), 50, 1L))

        val outcome = runBlocking { compressor(summarizer).compressDetailed(messages, 1_000_000) }

        assertSame(messages, outcome.messages)
        assertEquals(CompressionMode.NONE, outcome.mode)
        assertEquals(0, summarizer.calls)
    }

    @Test
    fun summaryIsInsertedAfterSystemAndBeforeKeptBody() {
        val system = ChatMessage(role = Role.SYSTEM, text = "sys")
        val messages = listOf(system, user(huge), user(huge), user("tail"))
        val summarizer = ScriptedSummarizer(SummaryResult.Ok(summaryText(), 50, 1L))

        val outcome = runBlocking { compressor(summarizer).compressDetailed(messages, 6_000) }

        assertEquals(CompressionMode.L2, outcome.mode)
        // system 仍在最前（摘要绝不能进 system，也不能排到它前面）。
        assertEquals(Role.SYSTEM, outcome.messages.first().role)
        assertEquals("sys", outcome.messages.first().text)
        // 摘要紧随 system，且是 USER。
        val summary = outcome.messages[1]
        assertEquals(Role.USER, summary.role)
        assertTrue(summary.text.startsWith(StructuredSummary.BOUNDARY_LINE))
        assertTrue(LayeredContextCompressor.isSummaryMessage(summary))
        // 摘要以最后一条消息收尾之前，保留区还在尾部。
        assertEquals("tail", outcome.messages.last().text)
        assertEquals(1, summarizer.calls)
        assertEquals(512, summarizer.lastTargetTokens)
        assertTrue(outcome.droppedMessages > 0)
    }

    @Test
    fun keptRegionIsSuffixOfInput() {
        val messages = listOf(user(huge), user(huge), user("tail"))
        val summarizer = ScriptedSummarizer(SummaryResult.Ok(summaryText(), 50, 1L))

        val outcome = runBlocking { compressor(summarizer).compressDetailed(messages, 9_000) }

        assertEquals(CompressionMode.L2, outcome.mode)
        assertTrue(outcome.messages.size >= 3, "expected summary + at least two kept messages")
        val kept = outcome.messages.drop(1)
        val expected = messages.takeLast(kept.size)
        assertEquals(expected.map { it.id }, kept.map { it.id })
        // 顺序也不许变（id 相同 + 顺序相同）。
        assertEquals(expected.map { it.text }, kept.map { it.text })
    }

    @Test
    fun summaryFailureFallsBackToL1() {
        val messages = listOf(user(huge), user(huge), user("tail"))
        val summarizer = ScriptedSummarizer(SummaryResult.Failed("engine:NETWORK"))

        val outcome = runBlocking { compressor(summarizer).compressDetailed(messages, 6_000) }

        assertEquals(CompressionMode.L1_FALLBACK, outcome.mode)
        assertEquals("engine:NETWORK", outcome.reason)
        assertEquals(0, outcome.summaryTokens)
        assertTrue(outcome.messages.isNotEmpty())
        // 回落就是纯 L1 的结果：没有摘要消息。
        assertTrue(outcome.messages.none { LayeredContextCompressor.isSummaryMessage(it) })
        assertEquals("tail", outcome.messages.last().text)
    }

    @Test
    fun summarizerThrowsFallsBackToL1() {
        val messages = listOf(user(huge), user(huge), user("tail"))

        val outcome = runBlocking { compressor(ThrowingSummarizer()).compressDetailed(messages, 6_000) }

        assertEquals(CompressionMode.L1_FALLBACK, outcome.mode)
        assertTrue(outcome.reason.startsWith("summarizer-error"))
        assertTrue(outcome.messages.isNotEmpty())
    }

    @Test
    fun belowMinTokensSkipsSummarizer() {
        val messages = listOf(user("a".repeat(100)), user("b"))
        val summarizer = ScriptedSummarizer(SummaryResult.Ok(summaryText(), 50, 1L))

        val outcome = runBlocking { compressor(summarizer).compressDetailed(messages, 10) }

        assertEquals(CompressionMode.L1, outcome.mode)
        assertEquals("below-min", outcome.reason)
        // 门槛的作用就是省掉这次调用：低于门槛一次都不许发。
        assertEquals(0, summarizer.calls)
    }

    @Test
    fun nullSummarizerKeepsL1() {
        val messages = listOf(user(huge), user(huge), user("tail"))

        val outcome = runBlocking { compressor(null).compressDetailed(messages, 6_000) }

        assertEquals(CompressionMode.L1, outcome.mode)
        assertEquals("no-summarizer", outcome.reason)
        assertEquals(0, outcome.summaryTokens)
        assertEquals("tail", outcome.messages.last().text)
    }

    @Test
    fun summaryIdIsStableForSameDroppedSegment() {
        val messages = listOf(user(huge), user(huge), user("tail"))
        val summarizer = ScriptedSummarizer(SummaryResult.Ok(summaryText(), 50, 1L))

        val first = runBlocking { compressor(summarizer).compressDetailed(messages, 6_000) }
        val second = runBlocking { compressor(summarizer).compressDetailed(messages, 6_000) }

        // 稳定派生 id：同一段被丢历史在任何一轮得到同一个 id（id 抖动会让前缀失效）。
        assertEquals(first.messages.first().id, second.messages.first().id)
        assertTrue(first.messages.first().id.startsWith(LayeredContextCompressor.SUMMARY_ID_PREFIX))
        // 渲染也必须逐字节相同。
        assertEquals(first.messages.first().text, second.messages.first().text)
    }

    @Test
    fun sanitizeAfterCompressionIsNoOp() {
        // 判据 B2：切点来自 L1，所以工具组不可能被拆散 ⇒ sanitize 不得补任何消息。
        val messages = listOf(user(huge), modelWithCall("c1"), toolResultFor("c1", huge), user("tail"))
        val summarizer = ScriptedSummarizer(SummaryResult.Ok(summaryText(), 50, 1L))

        val outcome = runBlocking { compressor(summarizer).compressDetailed(messages, 9_000) }
        val sanitized = sanitizeForProvider(outcome.messages)

        assertEquals(CompressionMode.L2, outcome.mode)
        assertEquals(outcome.messages.size, sanitized.size)
        assertEquals(outcome.messages.map { it.id }, sanitized.map { it.id })
        // 配对仍完整：MODEL(toolCalls) 后面紧跟它声明的 TOOL 结果。
        val modelIndex = sanitized.indexOfFirst { it.toolCalls.isNotEmpty() }
        assertTrue(modelIndex >= 0)
        assertEquals("c1", sanitized[modelIndex + 1].toolResults.single().callId)
    }

    @Test
    fun summaryMessageCarriesMergeGuardPrefix() {
        val messages = listOf(user(huge), user(huge), user("tail"))
        val summarizer = ScriptedSummarizer(SummaryResult.Ok(summaryText(), 50, 1L))

        val outcome = runBlocking { compressor(summarizer).compressDetailed(messages, 6_000) }

        // 判据 B10 的可判定部分：摘要消息必须能被识别（`canMerge` 据此放行）。
        assertTrue(LayeredContextCompressor.isSummaryMessage(outcome.messages.first()))
        assertFalse(LayeredContextCompressor.isSummaryMessage(user("plain")))
        // 摘要消息必须是 USER：进 system 会打破「system 段逐字节不变」的硬不变量。
        assertEquals(Role.USER, outcome.messages.first().role)
    }
}
