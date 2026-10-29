package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.FinishReason
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 收尾与轮头闸门单测。
 *
 * 取消语义第 2 条硬契约（`01-architecture.md` §6.2）：取消后必须**保留已收到的正文**，
 * 否则用户点停止看到的是空白 —— 那会被当成「内容丢了」。
 */
class RunTerminationTest {

    private fun context(
        events: MutableList<AgentEvent>,
        policy: AgentPolicy = AgentPolicy(),
        state: RunState = RunState.forRequest(testRequest()),
    ): TurnContext = TurnContext(
        request = testRequest(),
        policy = policy,
        engine = FakeEngine(scripts = emptyList()),
        tools = ToolRegistry(),
        state = state,
        emit = { events += it },
    )

    @Test
    fun cancelledKeepsPartialText() {
        val events = mutableListOf<AgentEvent>()
        val ctx = context(events)
        ctx.state.appendText("first half ")
        ctx.state.appendText("of the answer")

        runBlocking { RunTermination().emitCancelled(ctx) }

        val cancelled = events.filterIsInstance<AgentEvent.Cancelled>().single()
        assertEquals("first half of the answer", cancelled.partialText)
    }

    @Test
    fun finishedReportsAccumulatedTextAndUsage() {
        val events = mutableListOf<AgentEvent>()
        val ctx = context(events)
        ctx.state.beginRound()
        ctx.state.appendText("answer")
        ctx.state.recordUsage(prompt = 100, completion = 20, cachedPrompt = 64)

        runBlocking { RunTermination().emitFinished(ctx, FinishReason.STOP) }

        val finished = events.filterIsInstance<AgentEvent.Finished>().single()
        assertEquals("answer", finished.text)
        assertEquals(100, finished.usage?.prompt)
        assertEquals(20, finished.usage?.completion)
        assertEquals(64, finished.usage?.cachedPromptTokens)
        assertEquals(null, finished.report)
    }

    @Test
    fun breakerEmitsFailedWithDiagnosticReport() {
        val events = mutableListOf<AgentEvent>()
        val ctx = context(events)

        runBlocking { RunTermination().emitBreaker(ctx, "token budget exceeded") }

        val failed = events.filterIsInstance<AgentEvent.Failed>().single()
        assertEquals("BreakerTripped", failed.report?.kind)
        assertEquals("token budget exceeded", failed.message)
    }
}
