package com.rickeal.liquidharness.core.agent

import com.rickeal.liquidharness.core.agent.approval.ToolApprovalDecision
import com.rickeal.liquidharness.core.agent.loop.FakeEngine
import com.rickeal.liquidharness.core.agent.loop.FakeTool
import com.rickeal.liquidharness.core.agent.loop.fakeSpec
import com.rickeal.liquidharness.core.agent.loop.testRequest
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 端到端审批语义：**「拒绝」与「停止」必须分道**（外审的协程陷阱 3）。
 *
 * 这三个用例只能走 `AgentRunner`：`Deny` 要求 run **继续并正常 Finished**，
 * `Aborted` 要求 run **Cancelled 且保留 partialText** —— 两条收尾路径都在门面里，
 * 直接用 `TurnLoop` 断言不到「不是 Cancelled」这一条。
 */
class AgentApprovalRunTest {

    private fun gatedRegistry(tool: FakeTool): ToolRegistry =
        ToolRegistry().apply { register(tool) }

    /** 第一轮要工具，第二轮收尾。 */
    private fun twoRoundScript(name: String): FakeEngine = FakeEngine(
        scripts = listOf(
            listOf(
                GenerationEvent.ToolCallDelta(0, "c1", name, "{}"),
                GenerationEvent.Finished(FinishReason.TOOL_CALLS),
            ),
            FakeEngine.textOnly("done"),
        ),
    )

    @Test
    fun denyYieldsFailedResultAndRunStillFinishes() {
        val tool = FakeTool(fakeSpec(name = "gated", requiresConfirmation = true))
        val events = mutableListOf<AgentEvent>()
        val runner = AgentRunner(engine = twoRoundScript("gated"), tools = gatedRegistry(tool))

        runBlocking {
            runner.run(testRequest(), approval = { _, _ -> ToolApprovalDecision.Deny }).collect { events += it }
        }

        val result = events.filterIsInstance<AgentEvent.ToolResultReceived>().single().result
        assertFalse(result.ok)
        assertEquals("denied by user", result.errorMessage)
        assertEquals(0, tool.invocations)
        // 拒绝只拒这一个工具：run 继续，第二轮正常收尾。
        val finished = events.filterIsInstance<AgentEvent.Finished>().single()
        assertEquals("done", finished.text)
        assertEquals(2, finished.rounds)
        assertTrue(events.none { it is AgentEvent.Cancelled })
    }

    @Test
    fun approvalTimeoutDeniesOnlyThatTool() {
        val tool = FakeTool(fakeSpec(name = "gated", requiresConfirmation = true))
        val events = mutableListOf<AgentEvent>()
        val runner = AgentRunner(
            engine = twoRoundScript("gated"),
            tools = gatedRegistry(tool),
            policy = AgentPolicy(approvalTimeoutMillis = 10L),
        )

        runBlocking {
            runner.run(testRequest(), approval = { _, _ ->
                delay(1_000)
                ToolApprovalDecision.AllowOnce
            }).collect { events += it }
        }

        val result = events.filterIsInstance<AgentEvent.ToolResultReceived>().single().result
        assertFalse(result.ok)
        assertEquals(0, tool.invocations)
        // 陷阱 1：超时必须降级成「拒绝这一个工具」，不能升级成「整轮被杀」。
        assertTrue(events.none { it is AgentEvent.Cancelled })
        assertEquals("done", events.filterIsInstance<AgentEvent.Finished>().single().text)
    }

    @Test
    fun abortCancelsRunAndKeepsPartialText() {
        val tool = FakeTool(fakeSpec(name = "gated", requiresConfirmation = true))
        val engine = FakeEngine(
            scripts = listOf(
                listOf(
                    GenerationEvent.TextDelta("partial"),
                    GenerationEvent.ToolCallDelta(0, "c1", "gated", "{}"),
                    GenerationEvent.Finished(FinishReason.TOOL_CALLS),
                ),
            ),
        )
        val events = mutableListOf<AgentEvent>()
        val runner = AgentRunner(engine = engine, tools = gatedRegistry(tool))

        runBlocking {
            runner.run(testRequest(), approval = { _, _ -> ToolApprovalDecision.Aborted }).collect { events += it }
        }

        val cancelled = events.filterIsInstance<AgentEvent.Cancelled>().single()
        assertEquals("partial", cancelled.partialText)
        assertEquals(0, tool.invocations)
        assertTrue(events.none { it is AgentEvent.Finished })
        assertFalse(runner.isBusy)
    }
}
