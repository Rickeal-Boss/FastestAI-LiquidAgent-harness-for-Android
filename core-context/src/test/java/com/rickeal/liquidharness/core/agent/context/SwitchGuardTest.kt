package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.model.RemoteEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `SwitchGuard` 的口径出处、被调用判据与四条反例锚。
 *
 * 详细 KDoc 在此归档，与源码 KDoc 互为补充。G10 按 `core-context/src/main` 全量 `.kt`
 * 求和；W7 一次性裁决把该模块上限 1450→1650（详见
 * `docs/notes/w7-line-budget-ruling.md`，不构成先例），现 8 文件 1584 行 < 1650。
 * ⚠️ 本波检测到两个源码文件被并行写入反复覆盖（20:23 / 20:28 各一次），故本类
 * **只钉行为（state / allowed / 数值边界），不钉 reason 串的格式** ——
 * 依 AGENTS.md §七「只钉结构不钉措辞」：reason 是给 UI 落日志的诊断串，格式变化
 * 不该让行为判据变红。唯一例外是 `window_half_fallback`，它是主理人点名的诊断码，必须留住。
 *
 * ## 判定式（不许出现第二个真值源）
 *
 * 直接调现役 `hardContextBudgetTokens(target, AgentPolicy(maxContextTokens = ...))`
 * → `effectiveCharsPerToken(policy, measured)` → `charCapOf(hard, ratio)`；
 * 不重写 `min(verified ?: contextLength, maxContextTokens) - maxOutputTokens`，
 * 不另起 `TokenEstimator.estimateMessages`（W5 33.8K 假绿形态）。
 * `target.maxOutputTokens` 是输出预留的唯一来源，无独立参数。
 * `hard <= 0` 是不可达构造（现役 `coerceAtLeast(1)` 恒正），故不设该分支；
 * 可达的兜底分支是 `maxOutputTokens >= window` 时现役按 `window/2` 算 ⇒ VACUOUS。
 *
 * ## 二档与诊断串
 *
 * `measured <= 0` ⇒ floor 1.8；`measured > 0` ⇒ `coerceIn(1.8, 6.0)` 标定档。
 * `measured` 是 per-run，设置页通常无在途 run ⇒【推断】大概率走保守档，
 * reason 应带出可诊断串（如 `char_floor_unmeasured`）供 UI 落日志。
 * 本波不新增会话级持久化标定值（避免动 `AppContainer` / `RunState`）。
 *
 * ## 预计调用点（W4 铁律；本类不造假调用点凑数）
 *
 * UI 席在 `feature-settings/SettingsViewModel` 三形态写入点各调一次
 * （`setActiveEndpointId` / `saveEndpoint` / `setMaxContextTokens`），只做其一会留绕路；
 * CI 席补 arch-guard 调用形态守卫 + selftest 变异自证（删任一调用 ⇒ 判红）；
 * 真机判据 = 被拒时 `settings.preferences_pb` 的 `active_endpoint_id` 读回未变（`exec-out`）。
 * 「先压缩到范围内再切」由 UI 以更小的 `committedChars` 重调本纯函数实现（同输入同输出）。
 *
 * ## 反例锚（缺一条就是恒绿）
 *
 * ① 换回 `contextLength` 裸值 ⇒「1M 声明 + 256k verified」用例必红；
 * ② 恒用 ceiling 6.0 ⇒ 无真值用例「本该禁止」被放行（危险方向）必红；
 * ③ 恒用 floor 1.8 ⇒ 有真值用例「本该放行」被禁（与切 1M 冲突）必红；
 * ④ 抄算式丢掉 `window/2` 兜底 ⇒ `maxOutputTokens > window` 用例拿不到
 *    `window_half_fallback` 必红。
 *
 * 交叉态取舍：`committedChars > cap` 且 `verified == null` 判 BLOCKED（保守优先），
 * 由 `blockedTakesPrecedenceOverUnverifiedInCrossState` 钉住，主理人可一处翻转。
 */
class SwitchGuardTest {

    private val target = RemoteEndpoint(
        id = "ep-256k",
        contextLength = 1_048_576,
        verifiedContextLength = 256_000,
        maxOutputTokens = 4_096,
    )

    @Test
    fun fitsWithVerifiedWindowAndCalibratedTier() {
        val verdict = evaluateSwitch(1_000_000, target, 1_000_000, 5.407f)
        assertEquals(SwitchState.ALLOWED, verdict.state)
        assertTrue(verdict.allowed)
        assertTrue(verdict.reason.isNotBlank())
    }

    @Test
    fun blocksWhenCommittedCharsExceedCap() {
        val verdict = evaluateSwitch(2_000_000, target, 1_000_000, 5.407f)
        assertEquals(SwitchState.BLOCKED, verdict.state)
        assertEquals(false, verdict.allowed)
        assertTrue(verdict.reason.isNotBlank())
    }

    @Test
    fun emptySessionIsVacuous() {
        val verdict = evaluateSwitch(0, target, 1_000_000, 0f)
        assertEquals(SwitchState.VACUOUS, verdict.state)
        assertTrue(verdict.allowed)
        assertTrue(verdict.reason.isNotBlank())
    }

    @Test
    fun unverifiedWindowIsVacuous() {
        val unverified = target.copy(verifiedContextLength = null)
        val verdict = evaluateSwitch(1_000, unverified, 1_000_000, 0f)
        assertEquals(SwitchState.VACUOUS, verdict.state)
        assertTrue(verdict.allowed)
    }

    @Test
    fun windowHalfFallbackIsVacuous() {
        val oversizedOutput = target.copy(
            contextLength = 32_000,
            verifiedContextLength = 32_000,
            maxOutputTokens = 40_000,
        )
        val verdict = evaluateSwitch(1_000, oversizedOutput, 32_000, 0f)
        assertEquals(SwitchState.VACUOUS, verdict.state)
        assertTrue(verdict.reason.contains("window_half_fallback"))
    }

    /** 反例锚①：1M 声明 + 256k verified，1.4M 字符必须被禁；用裸值会放行 ⇒ 断言必红。 */
    @Test
    fun declaredWindowMustNotBeUsedWhenVerifiedIsSmaller() {
        val verdict = evaluateSwitch(1_400_000, target, 1_000_000, 5.407f)
        assertEquals(SwitchState.BLOCKED, verdict.state)
    }

    /** 反例锚②：无真值时 floor cap=453_427 < 600_000 ⇒ 禁止；恒用 ceiling 6.0 会放行 ⇒ 必红。 */
    @Test
    fun unmeasuredTierUsesFloorNotCeiling() {
        val verdict = evaluateSwitch(600_000, target, 1_000_000, 0f)
        assertEquals(SwitchState.BLOCKED, verdict.state)
    }

    /** 反例锚③：同一体积，标定档该放行、floor 档该禁；恒用 floor 时第一条变 BLOCKED ⇒ 必红。 */
    @Test
    fun calibratedTierAllowsWhatFloorTierWouldBlock() {
        val calibrated = evaluateSwitch(1_000_000, target, 1_000_000, 5.407f)
        val unmeasured = evaluateSwitch(1_000_000, target, 1_000_000, 0f)
        assertEquals(SwitchState.ALLOWED, calibrated.state)
        assertEquals(SwitchState.BLOCKED, unmeasured.state)
    }

    /** 用户裁决的恢复路径：纯函数同输入同输出，压缩后仅 committedChars 变小即可重判放行。 */
    @Test
    fun pureFunctionReevaluationIsStableAfterCompression() {
        val before = evaluateSwitch(1_400_000, target, 1_000_000, 5.407f)
        val again = evaluateSwitch(1_400_000, target, 1_000_000, 5.407f)
        assertEquals(before, again)
        val afterCompact = evaluateSwitch(400_000, target, 1_000_000, 5.407f)
        assertEquals(SwitchState.ALLOWED, afterCompact.state)
    }

    @Test
    fun blockedTakesPrecedenceOverUnverifiedInCrossState() {
        val unverified = target.copy(verifiedContextLength = null, contextLength = 32_000)
        val verdict = evaluateSwitch(60_000, unverified, 32_000, 0f)
        assertEquals(SwitchState.BLOCKED, verdict.state)
    }
}
