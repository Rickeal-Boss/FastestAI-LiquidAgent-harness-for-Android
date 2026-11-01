package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.model.TokenEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 结构化摘要的格式校验单测。纯 JVM。
 *
 * 钉住三件事（判据 B1 / B9）：
 * - 五段齐全 ⇒ `Ok`；纯散文 ⇒ `Rejected`；缺 `OPEN TODOS` ⇒ `OkMissingOptional` 且缺失可见；
 * - `render()` 逐字节稳定（同一摘要两次渲染同字节，这是 prompt cache 的前提）；
 * - 超长摘要按 token 硬上限截断后仍然**格式合法**（截断不回落 L1）。
 */
class StructuredSummaryTest {

    private fun fullText(): String = listOf(
        StructuredSummary.BOUNDARY_LINE,
        "GOAL: ship the context summarizer",
        "FACTS:",
        "- repo is kotlin",
        "- endpoints are openai compatible",
        "DECISIONS:",
        "- reuse the l1 cut point",
        "DONE:",
        "- wrote the parser",
        "OPEN TODOS:",
        "- wire into context budget",
        "[dropped 12 messages]",
    ).joinToString("\n")

    private fun sample(): StructuredSummary = StructuredSummary(
        goal = "ship it",
        confirmedFacts = listOf("a", "b"),
        decisions = listOf("c"),
        done = listOf("d"),
        openTodos = listOf("e"),
        droppedMessageCount = 3,
    )

    @Test
    fun parsesAllFiveSections() {
        val parsed = parseStructuredSummary(fullText())

        assertTrue(parsed is SummaryParse.Ok, "expected Ok, got $parsed")
        val ok = parsed as SummaryParse.Ok
        val summary = ok.summary
        assertEquals("ship the context summarizer", summary.goal)
        assertEquals(listOf("repo is kotlin", "endpoints are openai compatible"), summary.confirmedFacts)
        assertEquals(listOf("reuse the l1 cut point"), summary.decisions)
        assertEquals(listOf("wrote the parser"), summary.done)
        assertEquals(listOf("wire into context budget"), summary.openTodos)
        assertEquals(12, summary.droppedMessageCount)
        assertFalse(ok.truncated)
    }

    @Test
    fun rejectsProse() {
        val parsed = parseStructuredSummary(
            "The user asked me to write a parser and then I did it. Everything looks fine."
        )

        assertTrue(parsed is SummaryParse.Rejected, "prose must be rejected, got $parsed")
        assertEquals("no-header", (parsed as SummaryParse.Rejected).reason)
    }

    @Test
    fun acceptsMissingOpenTodos() {
        val text = fullText().lines()
            .filterNot { it == "OPEN TODOS:" || it == "- wire into context budget" }
            .joinToString("\n")

        val parsed = parseStructuredSummary(text)

        assertTrue(parsed is SummaryParse.OkMissingOptional, "expected OkMissingOptional, got $parsed")
        val missing = parsed as SummaryParse.OkMissingOptional
        assertEquals(listOf(StructuredSummary.OPEN_TODOS), missing.missing)
        assertTrue(missing.summary.openTodos.isEmpty())
        // 缺失仍算成功：可选段缺失不得把整次摘要判死。
        assertEquals("ship the context summarizer", missing.summary.goal)
    }

    @Test
    fun rejectsMissingRequiredSection() {
        val text = fullText().lines()
            .filterNot { it == "DECISIONS:" || it == "- reuse the l1 cut point" }
            .joinToString("\n")

        val parsed = parseStructuredSummary(text)

        assertTrue(parsed is SummaryParse.Rejected, "missing DECISIONS must be rejected, got $parsed")
        assertTrue((parsed as SummaryParse.Rejected).reason.contains(StructuredSummary.DECISIONS))
    }

    @Test
    fun rejectsEmptyGoal() {
        val text = listOf(
            StructuredSummary.BOUNDARY_LINE,
            "GOAL:",
            "FACTS:",
            "- a",
            "DECISIONS:",
            "- b",
            "DONE:",
            "- c",
        ).joinToString("\n")

        val parsed = parseStructuredSummary(text)

        assertTrue(parsed is SummaryParse.Rejected, "empty GOAL must be rejected, got $parsed")
        assertEquals("empty-goal", (parsed as SummaryParse.Rejected).reason)
    }

    @Test
    fun renderIsByteStable() {
        val first = sample().render()
        val second = sample().render()
        val third = sample().copy().render()

        assertEquals(first, second)
        assertEquals(first, third)
        // 段序固定：五段头按 GOAL → FACTS → DECISIONS → DONE → OPEN TODOS 出现。
        val order = listOf(
            StructuredSummary.GOAL,
            StructuredSummary.FACTS,
            StructuredSummary.DECISIONS,
            StructuredSummary.DONE,
            StructuredSummary.OPEN_TODOS,
        ).map { first.indexOf(it) }
        assertEquals(order.sorted(), order)
        assertTrue(order.all { it > 0 })
    }

    @Test
    fun renderStartsWithBoundaryDeclaration() {
        val rendered = sample().render()

        assertTrue(rendered.startsWith(StructuredSummary.BOUNDARY_LINE + "\n"))
        assertTrue(rendered.startsWith(StructuredSummary.HEADER))
        // 声明必须是 ASCII（守卫 G16 的口径），不能靠本地化文案。
        assertTrue(StructuredSummary.BOUNDARY_LINE.all { it.code < 128 })
    }

    @Test
    fun parseRoundTripsRenderedSummary() {
        val original = sample()

        val parsed = parseStructuredSummary(original.render())

        assertTrue(parsed is SummaryParse.Ok, "expected Ok, got $parsed")
        assertEquals(original, (parsed as SummaryParse.Ok).summary)
    }

    @Test
    fun truncatesOverlongSummaryToTokenLimit() {
        val overlong = StructuredSummary(
            goal = "keep the goal",
            confirmedFacts = listOf("x".repeat(5_000)),
            decisions = listOf("y".repeat(2_000)),
            done = listOf("z".repeat(2_000)),
        )

        val parsed = parseStructuredSummary(overlong.render(), maxTokens = 512)

        assertTrue(parsed.isTruncated, "overlong summary must be flagged as truncated")
        val summary = parsed.summaryOrNull
        assertTrue(summary != null)
        assertTrue(
            TokenEstimator.estimate(summary!!.render()) <= 512,
            "rendered summary must fit the hard cap",
        )
        // 截断后仍必须是合法格式（可被再次解析），否则等于把不可校验的东西塞回上下文。
        val reparsed = parseStructuredSummary(summary.render(), maxTokens = 512)
        assertTrue(reparsed.summaryOrNull != null, "truncated summary must stay parseable")
        assertEquals("keep the goal", summary.goal)
    }

    @Test
    fun parsesCrlfAndSurroundingBlankLines() {
        val text = "\n\n" + fullText().replace("\n", "\r\n") + "\n\n"

        val parsed = parseStructuredSummary(text)

        assertTrue(parsed is SummaryParse.Ok, "expected Ok, got $parsed")
        val ok = parsed as SummaryParse.Ok
        assertEquals(2, ok.summary.confirmedFacts.size)
        assertEquals(12, ok.summary.droppedMessageCount)
    }
}
