package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.approval.ToolApprovalDecision
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TerminationReason
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 执行流水线单测：未知工具、坏参数、拒绝节流、失败连击熔断、工具往返落库。
 *
 * 事件序列纪律（`AGENTS.md` §五.3）在这里有两条钉子：
 * - 未知工具**只能**发 `ToolSkipped`（没有 spec 就没有轨迹创建点）；
 * - 任何走到了执行的调用，`ToolCallStarted` 必须早于 `ToolResultReceived`。
 */
class ToolCallExecutorTest {

    @Test
    fun unknownToolIsSkippedWithoutResultEvent() {
        val events = mutableListOf<AgentEvent>()
        val ctx = testContext(events)
        val call = ToolCall(id = "c1", name = "nope", argumentsJson = "{}")

        val outcome = runBlocking { ToolCallExecutor().execute(ctx, listOf(call)) }

        assertEquals(ToolCallExecutor.Outcome.Continue, outcome)
        assertEquals("unknown tool", events.filterIsInstance<AgentEvent.ToolSkipped>().single().reason)
        assertTrue(events.none { it is AgentEvent.ToolResultReceived })
    }

    @Test
    fun invalidArgumentsAreNotExecutedButStillReported() {
        val events = mutableListOf<AgentEvent>()
        val spec = fakeSpec(
            name = "needs_path",
            parameters = listOf(ToolParameter("path", ToolParamType.STRING, "required path")),
        )
        val tool = FakeTool(spec)
        val ctx = testContext(events, registered = listOf(tool))
        val call = ToolCall(id = "c1", name = "needs_path", argumentsJson = "{}")

        runBlocking { ToolCallExecutor().execute(ctx, listOf(call)) }

        assertEquals(0, tool.invocations)
        val result = events.filterIsInstance<AgentEvent.ToolResultReceived>().single().result
        assertFalse(result.ok)
        assertTrue(result.errorMessage.orEmpty().contains("path"))
        // 轨迹创建点必须在结果之前（UI 只更新不新建）。
        assertTrue(events.indexOfFirst { it is AgentEvent.ToolCallStarted } <
            events.indexOfFirst { it is AgentEvent.ToolResultReceived })
    }

    @Test
    fun twoDenialsSuppressTheThirdApprovalCard() {
        val events = mutableListOf<AgentEvent>()
        val tool = FakeTool(fakeSpec(name = "gated", requiresConfirmation = true))
        val executor = ToolCallExecutor()
        val ctx = testContext(events, registered = listOf(tool), approval = { _, _ -> ToolApprovalDecision.Deny })

        // 同一个执行器 = 同一次 run（TurnLoop 每 run 建一个），计数必须跨调用累积。
        runBlocking {
            repeat(3) { round ->
                executor.execute(ctx, listOf(ToolCall(id = "c" + round, name = "gated", argumentsJson = "{}")))
            }
        }

        assertEquals(2, events.count { it is AgentEvent.ApprovalRequested })
        assertEquals(3, events.count { it is AgentEvent.ToolResultReceived })
        assertEquals(0, tool.invocations)
    }

    @Test
    fun fourConsecutiveFailuresTripTheBreaker() {
        val events = mutableListOf<AgentEvent>()
        val tool = FakeTool(fakeSpec(name = "boom")) { _ ->
            ToolResult(name = "boom", ok = false, errorMessage = "nope")
        }
        // 每轮换参数：否则第 3 次会先被同参护栏拦下，失败连击永远到不了 4。
        val scripts = (1..4).map { n ->
            listOf(
                GenerationEvent.ToolCallDelta(0, "c" + n, "boom", "{\"n\":" + n + "}"),
                GenerationEvent.Finished(FinishReason.TOOL_CALLS),
            )
        }
        val ctx = testContext(
            events = events,
            registered = listOf(tool),
            engine = FakeEngine(scripts = scripts),
        )

        val step = runBlocking { TurnLoop(generation = GenerationRound(backoff = {})).run(ctx) }

        assertEquals(Step.Terminated, step)
        assertEquals(4, tool.invocations)
        val failed = events.filterIsInstance<AgentEvent.Failed>().single()
        assertEquals(TerminationReason.BreakerTripped, failed.terminatedBy)
    }

    @Test
    fun toolRoundIsCommittedAsPairedModelAndToolMessages() {
        val events = mutableListOf<AgentEvent>()
        val tool = FakeTool(fakeSpec(name = "echo"))
        val ctx = testContext(events, registered = listOf(tool))
        val call = ToolCall(id = "call-1", name = "echo", argumentsJson = "{}")

        runBlocking { ToolCallExecutor().execute(ctx, listOf(call)) }

        val working = ctx.state.working()
        val model = working[working.size - 2]
        val toolMessage = working[working.size - 1]
        assertEquals(Role.MODEL, model.role)
        assertEquals("call-1", model.toolCalls.single().id)
        assertEquals(Role.TOOL, toolMessage.role)
        assertEquals("call-1", toolMessage.toolResults.single().callId)
        assertEquals(2, events.count { it is AgentEvent.MessageCommitted })
        assertEquals(1, ctx.state.executedToolCount)
    }
}
