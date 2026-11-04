package com.rickeal.liquidharness.feature.settings

import com.rickeal.liquidharness.core.agent.context.SwitchState
import com.rickeal.liquidharness.core.agent.context.SwitchVerdict
import com.rickeal.liquidharness.core.agent.context.evaluateSwitch
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UI 侧切换判定层的 JVM 判据。
 *
 * 「BLOCKED 必须拦下」与「VACUOUS 也要二次确认、不得记成通过」是两条独立的判据：
 * 少一条就会把空会话 / 未验证窗口当成硬绿放行（用户裁决里最常见的误判形态）。
 */
class SwitchDecisionTest {

    private val target = RemoteEndpoint(
        id = "t",
        contextLength = 1_048_576,
        verifiedContextLength = 256_000,
        maxOutputTokens = 4_096,
    )

    @Test
    fun blockedVerdictYieldsBlock() {
        val verdict = SwitchVerdict(
            false,
            "blocked: committed=2000000 cap=453427 char_floor_unmeasured",
            SwitchState.BLOCKED,
        )
        assertEquals(SwitchAction.BLOCK, decideSwitch(verdict).action)
    }

    @Test
    fun allowedVerdictYieldsConfirm() {
        val verdict = SwitchVerdict(true, "char_floor_unmeasured", SwitchState.ALLOWED)
        assertEquals(SwitchAction.CONFIRM, decideSwitch(verdict).action)
    }

    @Test
    fun vacuousVerdictYieldsConfirmAndIsNotAllowed() {
        val verdict = SwitchVerdict(true, "unverified_window char_floor_unmeasured", SwitchState.VACUOUS)
        val decision = decideSwitch(verdict)
        assertEquals(SwitchAction.CONFIRM, decision.action)
        assertEquals(SwitchState.VACUOUS, decision.verdict.state)
    }

    @Test
    fun parseCapCharsReadsDiagnosticString() {
        assertEquals(453_427, parseCapChars("blocked: committed=2000000 cap=453427 char_floor_unmeasured"))
        assertTrue(parseCapChars("unverified_window char_floor_unmeasured") == null)
    }

    @Test
    fun blockedEndpointIdsUsesVerifiedWindowNotDeclared() {
        // 1.4M 字符：verified 256k 的 floor cap ≈453k ⇒ 装不下；若误用 1M 声明值会放行。
        assertTrue(blockedEndpointIds(listOf(target), 1_400_000, 1_000_000).contains("t"))
    }

    @Test
    fun emptySessionIsNeverBlocked() {
        // 空会话 = VACUOUS（不是 BLOCKED）⇒ 不置灰，但放行时仍走二次确认。
        assertTrue(blockedEndpointIds(listOf(target), 0, 1_000_000).isEmpty())
    }

    @Test
    fun unmeasuredTierIsDiagnosable() {
        assertTrue(evaluateSwitch(1_000, target, 1_000_000, 0f).reason.contains("char_floor_unmeasured"))
    }
}
