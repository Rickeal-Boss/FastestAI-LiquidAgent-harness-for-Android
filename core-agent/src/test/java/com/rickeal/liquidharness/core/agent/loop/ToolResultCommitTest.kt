package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.context.sanitizeForProvider
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 结果落库的判据：**单条截断**（W2 判据 #9 + W5 的出口硬上限）、**聚合降级**、
 * **MODEL→TOOL 顺序与配对**。
 *
 * 截断断言必须看 `truncated` 而不只看长度：只截断不标记，模型会以为自己看到了全文，
 * 于是不再续读 —— 那是静默截断，比不截断更糟。
 *
 * 聚合层的断言必须同时看「哪一条被占位」与「callId 还在」：只断言总长度变短，
 * 一个「从头砍掉整条结果」的实现也能通过，而那种实现会让下一轮请求 400。
 */
class ToolResultCommitTest {

    private val policy = AgentPolicy()
    private val commit = ToolResultCommit()

    private fun call(id: String = "c1", name: String = "read") =
        ToolCall(id = id, name = name, argumentsJson = "{}")

    // ------------------------------------------------------------ 单条限长（R1）

    /** 50KB 读结果：压到硬上限以内，尾部有省略量标记，且置 truncated。 */
    @Test
    fun hugeOutputIsClampedToTheHardCapWithMarker() {
        val tool = FakeTool(fakeSpec(name = "read", effect = ToolEffect.READ))
        val huge = ToolResult(name = "read", ok = true, output = "a".repeat(50_000))

        val clamped = commit.truncate(call(), huge, tool, policy)

        assertTrue(clamped.output.length <= policy.maxToolResultChars)
        assertTrue(clamped.output.endsWith("chars omitted]"))
        assertTrue(clamped.truncated)
    }

    /** 恰好等于上限：原对象返回（调用方据此判「没变化」，也避免白 copy 一次大字符串）。 */
    @Test
    fun outputExactlyAtTheCapIsReturnedUnchanged() {
        val tool = FakeTool(fakeSpec(name = "read", effect = ToolEffect.READ))
        val exact = ToolResult(name = "read", ok = true, output = "a".repeat(policy.maxToolResultChars))

        val kept = commit.truncate(call(), exact, tool, policy)

        assertTrue(kept === exact)
        assertEquals(policy.maxToolResultChars, kept.output.length)
        assertFalse(kept.truncated)
    }

    /**
     * 豁免名单里的工具不套硬上限。
     *
     * 默认值下硬上限（16 000）与读类上限相等，豁免**看不出差别** —— 所以这里显式把硬上限
     * 调低到 5 000 造出差异，并同时断言「同一个工具不豁免就会被砍」：只断言前者的话，
     * 一个「根本不看豁免名单」的实现也能通过（它只是恰好没超限）。
     */
    @Test
    fun exemptToolIsNotClampedByTheHardCap() {
        val tight = policy.copy(maxToolResultChars = 5_000, toolResultCapExemptTools = setOf("read"))
        val tool = FakeTool(fakeSpec(name = "read", effect = ToolEffect.READ))
        val big = ToolResult(name = "read", ok = true, output = "a".repeat(6_000))

        val kept = commit.truncate(call(), big, tool, tight)
        assertEquals(6_000, kept.output.length)
        assertFalse(kept.truncated)

        val cut = commit.truncate(call(), big, tool, tight.copy(toolResultCapExemptTools = emptySet()))
        assertTrue(cut.output.length <= 5_000)
        assertTrue(cut.truncated)
    }

    /**
     * 工具自己标的 `truncated` 不被出口层覆盖：未超限时原对象返回，标记照旧为 true。
     * 否则「工具在源头截过、出口没截」的结果会丢掉这个证据，模型以为看到了全文。
     */
    @Test
    fun alreadyTruncatedResultKeepsItsFlag() {
        val tool = FakeTool(fakeSpec(name = "read", effect = ToolEffect.READ))
        val marked = ToolResult(name = "read", ok = true, output = "small", truncated = true)

        val kept = commit.truncate(call(), marked, tool, policy)

        assertTrue(kept.truncated)
        assertEquals("small", kept.output)
    }

    /** 写类仍按 effect 分档（2000），硬上限只可能更松，不会把它抬上去。 */
    @Test
    fun writeOutputIsStillCappedByTheEffectLimit() {
        val tool = FakeTool(fakeSpec(name = "write", effect = ToolEffect.WRITE))
        val huge = ToolResult(name = "write", ok = true, output = "b".repeat(5_000))

        val clamped = commit.truncate(call("c1", "write"), huge, tool, policy)

        assertTrue(clamped.output.length <= policy.maxWriteToolOutputChars)
        assertTrue(clamped.output.endsWith("chars omitted]"))
        assertTrue(clamped.truncated)
    }

    // ------------------------------------------------------------ 聚合上限（T6）

    /** 3 条各 25k = 75k 超 64k ⇒ 只把**最旧**的一条换成占位，后两条完整。 */
    @Test
    fun oldestResultIsOmittedWhenAggregateExceedsTheCap() {
        val events = mutableListOf<AgentEvent>()
        val ctx = testContext(events)
        val calls = listOf(call("c1"), call("c2"), call("c3"))
        val results = calls.map { ToolResult(callId = it.id, name = it.name, output = "x".repeat(25_000)) }

        runBlocking { commit.commit(ctx, calls, results) }

        val tool = ctx.state.working().last()
        assertEquals(Role.TOOL, tool.role)
        assertEquals(3, tool.toolResults.size)
        assertEquals("[omitted: aggregated limit, 25000 chars]", tool.toolResults[0].output)
        assertTrue(tool.toolResults[0].truncated)
        assertEquals(25_000, tool.toolResults[1].output.length)
        assertEquals(25_000, tool.toolResults[2].output.length)
        assertFalse(tool.toolResults[2].truncated)
        assertEquals(listOf("c1", "c2", "c3"), tool.toolResults.map { it.callId })
    }

    /** 恰好 64k（2×32k）：不超限 ⇒ 一条都不占位。 */
    @Test
    fun aggregateExactlyAtTheCapIsUntouched() {
        val events = mutableListOf<AgentEvent>()
        val ctx = testContext(events)
        val calls = listOf(call("c1"), call("c2"))
        val results = calls.map { ToolResult(callId = it.id, name = it.name, output = "y".repeat(32_000)) }

        runBlocking { commit.commit(ctx, calls, results) }

        val tool = ctx.state.working().last()
        assertEquals(32_000, tool.toolResults[0].output.length)
        assertEquals(32_000, tool.toolResults[1].output.length)
        assertTrue(tool.toolResults.none { it.truncated })
    }

    /**
     * 占位之后 MODEL(toolCalls) 与 TOOL(toolResults) 仍然成对：
     * `sanitizeForProvider` 不得丢结果、也不得补合成结果（补了就说明 callId 丢了）。
     */
    @Test
    fun omittedPlaceholderKeepsCallIdPairing() {
        val events = mutableListOf<AgentEvent>()
        val ctx = testContext(events)
        val calls = listOf(call("c1"), call("c2"), call("c3"))
        val results = calls.map { ToolResult(callId = it.id, name = it.name, output = "z".repeat(25_000)) }

        runBlocking { commit.commit(ctx, calls, results) }
        val sanitized = sanitizeForProvider(ctx.state.working())

        assertEquals(3, sanitized.size)
        assertEquals(Role.TOOL, sanitized[2].role)
        assertEquals(3, sanitized[2].toolResults.size)
        assertEquals(listOf("c1", "c2", "c3"), sanitized[2].toolResults.map { it.callId })
    }

    /** 只有一条结果时不占位（最后一条永远完整，否则这一轮等于什么都没回）。 */
    @Test
    fun singleOversizedResultIsNeverOmitted() {
        val events = mutableListOf<AgentEvent>()
        val ctx = testContext(events)
        val calls = listOf(call("c1"))
        val results = listOf(ToolResult(callId = "c1", name = "read", output = "w".repeat(90_000)))

        runBlocking { commit.commit(ctx, calls, results) }

        val tool = ctx.state.working().last()
        assertEquals(90_000, tool.toolResults[0].output.length)
        assertFalse(tool.toolResults[0].truncated)
    }

    /**
     * 占位措辞不得像失败：模型看到 `error` / `failed` 会重试同一次调用，
     * 把一次省钱的截断变成一次多花的往返。占位必须读起来像「被省略」。
     */
    @Test
    fun omittedPlaceholderNeverLooksLikeFailure() {
        val events = mutableListOf<AgentEvent>()
        val ctx = testContext(events)
        val calls = listOf(call("c1"), call("c2"), call("c3"))
        val results = calls.map { ToolResult(callId = it.id, name = it.name, output = "v".repeat(25_000)) }

        runBlocking { commit.commit(ctx, calls, results) }

        val text = ctx.state.working().last().toolResults[0].output
        val lowered = text.lowercase()
        assertFalse(lowered.contains("error"))
        assertFalse(lowered.contains("fail"))
        assertTrue(text.startsWith("[omitted: aggregated limit, "))
        assertTrue(text.endsWith(" chars]"))
    }
}
