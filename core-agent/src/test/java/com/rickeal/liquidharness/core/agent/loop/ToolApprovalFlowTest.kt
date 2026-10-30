package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.approval.ToolApprovalDecision
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalHandler
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.tools.ClipboardTool
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.ToolCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 审批闸门单测：无通道 fail-closed、参数级闸门（`clipboard` 的 get/set/坏 JSON）、
 * 投递超时。
 *
 * 判据都是「**该弹卡的弹了 / 不该弹的没弹**」+「工具没被执行」——
 * 只看返回值不够：一个先执行后判审批的实现能返回同样的 Decision。
 */
class ToolApprovalFlowTest {

    @Test
    fun dangerousToolWithoutChannelIsSkippedAndNotExecuted() {
        val events = mutableListOf<AgentEvent>()
        val tool = FakeTool(fakeSpec(name = "danger", dangerous = true))
        val ctx = testContext(events, registered = listOf(tool))
        val call = ToolCall(id = "c1", name = "danger", argumentsJson = "{}")

        val outcome = runBlocking { ToolCallExecutor().execute(ctx, listOf(call)) }

        assertEquals(ToolCallExecutor.Outcome.Continue, outcome)
        assertEquals(0, tool.invocations)
        assertEquals(1, events.count { it is AgentEvent.ToolSkipped })
        assertTrue(events.none { it is AgentEvent.ToolResultReceived })
        assertTrue(events.none { it is AgentEvent.ApprovalRequested })
    }

    @Test
    fun paramGateAsksOnlyForWriteActionsAndFailsClosedOnBadJson() {
        val events = mutableListOf<AgentEvent>()
        var asked = 0
        val handler = ToolApprovalHandler { _, _ ->
            asked += 1
            ToolApprovalDecision.Deny
        }
        val ctx = testContext(events, approval = handler)
        val flow = ToolApprovalFlow()
        val tool = ClipboardTool(null)

        val get = ToolCall(id = "g1", name = "clipboard", argumentsJson = "{\"action\":\"get\"}")
        assertEquals(ToolApprovalFlow.Decision.Allowed, runBlocking { flow.decide(ctx, get, tool) })
        assertEquals(0, asked)

        val set = ToolCall(id = "s1", name = "clipboard", argumentsJson = "{\"action\":\"set\",\"text\":\"x\"}")
        val setDecision = runBlocking { flow.decide(ctx, set, tool) }
        assertEquals(ToolApprovalFlow.Decision.Denied("denied by user"), setDecision)
        assertEquals(1, asked)

        // 坏 JSON：参数级闸门必须 fail-closed 弹卡（W2 判据 #4）。
        val bad = ToolCall(id = "b1", name = "clipboard", argumentsJson = "{not json")
        runBlocking { flow.decide(ctx, bad, tool) }
        assertEquals(2, asked)
        assertEquals(2, events.count { it is AgentEvent.ApprovalRequested })
    }

    @Test
    fun undeliverableApprovalCardIsDeniedInsteadOfHanging() {
        val events = mutableListOf<AgentEvent>()
        val policy = AgentPolicy(approvalEmitTimeoutMillis = 10L)
        val tool = FakeTool(fakeSpec(name = "gated", requiresConfirmation = true))
        val ctx = testContext(
            events = events,
            registered = listOf(tool),
            approval = { _, _ -> ToolApprovalDecision.AllowOnce },
            policy = policy,
            emitOverride = { event ->
                // 只让审批卡挂住：模拟「用户切后台，收集方停止」⇒ channelFlow.send 挂起。
                if (event is AgentEvent.ApprovalRequested) delay(1_000)
                events += event
            },
        )
        val call = ToolCall(id = "c1", name = "gated", argumentsJson = "{}")

        val outcome = runBlocking { ToolCallExecutor().execute(ctx, listOf(call)) }

        assertEquals(ToolCallExecutor.Outcome.Continue, outcome)
        assertEquals(0, tool.invocations)
        val result = events.filterIsInstance<AgentEvent.ToolResultReceived>().single().result
        assertFalse(result.ok)
        assertEquals("approval request could not be delivered", result.errorMessage)
    }
}
