package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.model.AgentEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 轮头闸门单测。三条判据（墙钟 SOFT / 墙钟 HARD / token 硬上限）**必须各自被行使** ——
 * 只测「没超就继续」等于 vacuous（`AGENTS.md` §二.3：「未触发」≠「已验证通过」）。
 */
class RoundHeadGateTest {

    private var clockNanos: Long = 0L

    private fun context(policy: AgentPolicy, state: RunState): TurnContext = TurnContext(
        request = testRequest(),
        policy = policy,
        engine = FakeEngine(scripts = emptyList()),
        tools = ToolRegistry(),
        state = state,
        emit = { _: AgentEvent -> Unit },
    )

    @Test
    fun proceedsWhenWithinEveryBudget() {
        val state = RunState(nowNanos = { clockNanos })
        val verdict = RoundHeadGate().check(context(AgentPolicy(), state))

        assertEquals(GateVerdict.Proceed, verdict)
    }

    @Test
    fun tripsWhenPromptTokenBudgetExceeded() {
        val policy = AgentPolicy(maxRunPromptTokens = 500)
        val state = RunState(nowNanos = { clockNanos })
        state.recordUsage(prompt = 1_000, completion = 10, cachedPrompt = 0)

        val verdict = RoundHeadGate().check(context(policy, state))

        assertTrue(verdict is GateVerdict.Trip)
        assertTrue(verdict.detail.contains("prompt token budget"))
    }

    @Test
    fun tripsWhenWallClockHardLimitReached() {
        val policy = AgentPolicy(wallClockHardMillis = 600_000L)
        val state = RunState(nowNanos = { clockNanos })
        clockNanos = 601_000L * 1_000_000L

        val verdict = RoundHeadGate().check(context(policy, state))

        assertTrue(verdict is GateVerdict.Trip)
        assertTrue(verdict.detail.contains("wall clock hard"))
    }

    @Test
    fun softLimitWarnsOnlyOnceAndKeepsRunning() {
        AgentLogStore.clear()
        val policy = AgentPolicy(wallClockSoftMillis = 180_000L, wallClockHardMillis = 600_000L)
        val state = RunState(nowNanos = { clockNanos })
        clockNanos = 181_000L * 1_000_000L
        val gate = RoundHeadGate()

        assertEquals(GateVerdict.Proceed, gate.check(context(policy, state)))
        assertEquals(GateVerdict.Proceed, gate.check(context(policy, state)))

        // SOFT 只提醒一次：重复记会让诊断页被同一条噪声填满（告警量级本身就是判据）。
        val softWarnings = AgentLogStore.recent().count { it.message.contains("soft limit") }
        assertEquals(1, softWarnings)
        assertTrue(state.softWarned)
    }
}
