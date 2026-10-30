package com.rickeal.liquidharness.core.agent.tool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 执行护栏状态机：同参连发 / 失败连击 / 振荡三个维度的阈值边界。
 *
 * 全部是纯内存判定，不触协程与 Android API。
 */
class ToolExecutionGuardTest {

    private val readArgs = "{\"path\":\"a.txt\"}"
    private val otherArgs = "{\"path\":\"b.txt\"}"

    private fun tripKind(verdict: ToolExecutionGuard.Verdict): ToolExecutionGuard.BreakerKind {
        assertTrue(verdict is ToolExecutionGuard.Verdict.BreakerTripped, "expected a trip, got " + verdict)
        return verdict.kind
    }

    // ── 同参连发 ─────────────────────────────────────────────────────────────

    @Test
    fun firstCallProceeds() {
        val guard = ToolExecutionGuard()
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.beforeCall("file_read", readArgs))
    }

    @Test
    fun sameCallIsAllowedTwiceThenIgnored() {
        val guard = ToolExecutionGuard()
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.beforeCall("file_read", readArgs))
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.beforeCall("file_read", readArgs))
        val third = guard.beforeCall("file_read", readArgs)
        assertTrue(third is ToolExecutionGuard.Verdict.RepeatedCall)
        val repeated = third as ToolExecutionGuard.Verdict.RepeatedCall
        assertEquals(3, repeated.streak)
        assertEquals("file_read", repeated.tool)
    }

    @Test
    fun differentArgumentsResetTheSameCallStreak() {
        val guard = ToolExecutionGuard()
        guard.beforeCall("file_read", readArgs)
        guard.beforeCall("file_read", readArgs)
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.beforeCall("file_read", otherArgs))
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.beforeCall("file_read", otherArgs))
    }

    @Test
    fun keyOrderDoesNotBreakTheSignature() {
        val guard = ToolExecutionGuard()
        guard.beforeCall("file_write", "{\"path\":\"a\",\"content\":\"x\"}")
        guard.beforeCall("file_write", "{\"content\":\"x\",\"path\":\"a\"}")
        val third = guard.beforeCall("file_write", "{ \"path\" : \"a\" , \"content\" : \"x\" }")
        assertTrue(third is ToolExecutionGuard.Verdict.RepeatedCall, "key order must not defeat the guard")
    }

    @Test
    fun repeatedCallIsNotReportedAsOscillation() {
        val guard = ToolExecutionGuard()
        // 前两次放行（保留「失败后原样重试一次」的合法路径），第 3 次起忽略。
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.beforeCall("datetime", "{}"))
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.beforeCall("datetime", "{}"))
        for (i in 3..6) {
            val verdict = guard.beforeCall("datetime", "{}")
            assertTrue(verdict is ToolExecutionGuard.Verdict.RepeatedCall, "call " + i + " should be ignored")
        }
    }

    // ── 失败连击 ─────────────────────────────────────────────────────────────

    @Test
    fun fourConsecutiveFailuresTripTheBreaker() {
        val guard = ToolExecutionGuard()
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.afterCall("file_write", false))
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.afterCall("file_write", false))
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.afterCall("file_write", false))
        val fourth = guard.afterCall("file_write", false)
        assertEquals(ToolExecutionGuard.BreakerKind.TOOL_FAILURE_STREAK, tripKind(fourth))
        assertEquals(4, guard.failureStreak("file_write"))
        assertTrue((fourth as ToolExecutionGuard.Verdict.BreakerTripped).evidence.contains("file_write"))
    }

    @Test
    fun successResetsTheFailureStreak() {
        val guard = ToolExecutionGuard()
        guard.afterCall("file_write", false)
        guard.afterCall("file_write", false)
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.afterCall("file_write", true))
        assertEquals(0, guard.failureStreak("file_write"))
        guard.afterCall("file_write", false)
        guard.afterCall("file_write", false)
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.afterCall("file_write", false))
    }

    @Test
    fun failureStreakIsTrackedPerTool() {
        val guard = ToolExecutionGuard()
        repeat(3) { guard.afterCall("file_write", false) }
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.afterCall("file_read", false))
        assertEquals(3, guard.failureStreak("file_write"))
        assertEquals(1, guard.failureStreak("file_read"))
        assertEquals(0, guard.failureStreak("clipboard"))
    }

    // ── 振荡 ─────────────────────────────────────────────────────────────────

    @Test
    fun alternatingCallsTripOscillation() {
        val guard = ToolExecutionGuard()
        val sequence = listOf("file_read", "file_list", "file_read", "file_list", "file_read", "file_list")
        var last: ToolExecutionGuard.Verdict = ToolExecutionGuard.Verdict.Proceed
        for (tool in sequence) {
            last = guard.beforeCall(tool, "{}")
        }
        assertEquals(ToolExecutionGuard.BreakerKind.TOOL_OSCILLATION, tripKind(last))
    }

    @Test
    fun threeStepCycleTripsOscillation() {
        val guard = ToolExecutionGuard()
        val sequence = listOf("a", "b", "c", "a", "b", "c")
        var last: ToolExecutionGuard.Verdict = ToolExecutionGuard.Verdict.Proceed
        for (tool in sequence) {
            last = guard.beforeCall(tool, "{}")
        }
        assertEquals(ToolExecutionGuard.BreakerKind.TOOL_OSCILLATION, tripKind(last))
    }

    @Test
    fun variedCallsDoNotTrip() {
        val guard = ToolExecutionGuard()
        val sequence = listOf("a", "b", "c", "d", "e", "f", "g")
        for (tool in sequence) {
            assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.beforeCall(tool, "{}"))
        }
    }

    @Test
    fun resetClearsAllState() {
        val guard = ToolExecutionGuard()
        guard.beforeCall("file_read", readArgs)
        guard.beforeCall("file_read", readArgs)
        guard.afterCall("file_write", false)

        guard.reset()

        assertEquals(0, guard.failureStreak("file_write"))
        assertEquals(ToolExecutionGuard.Verdict.Proceed, guard.beforeCall("file_read", readArgs))
    }
}
