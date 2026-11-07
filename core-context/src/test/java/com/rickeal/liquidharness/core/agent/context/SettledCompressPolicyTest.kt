package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.TerminationReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `SettledCompressPolicy` 的阈值出处、路径矩阵、被调用判据与反例锚。
 *
 * 详细 KDoc 在此归档，理由同 `SwitchGuardTest`。G10 按 `core-context/src/main` 全量求和；
 * W7 一次性裁决把上限 1450→1650（见 `docs/notes/w7-line-budget-ruling.md`），
 * 现 1584 行 < 1650；单文件 400 上限未动。
 * ⚠️ 两个源码文件被并行写入反复覆盖，故本类只钉**行为**（applied / 触发布尔 /
 * 数值边界用常量引用），不钉 reason 串格式 —— 依 AGENTS.md §七「只钉结构不钉措辞」。
 * 唯一例外 `already_compressed`：主理人点名「不静默」，必须留住这个语义。
 *
 * ## 为什么是「评估一次」而不是「每轮都压」
 *
 * 现役压缩产物落在 `ChatUiState.compression` **压缩区**上，`compression != null`
 * 时重复压缩静默返回 ⇒ 无条件每轮压从第 2 轮起就是空操作；W10/S3 起压缩形态 =
 * 保头锚 + 砍中段 + 尾部摘要 ⇒ 头锚字节不动，下一次 `cached_tokens` 对头锚段仍 > 0。
 * 用户裁决 (a)：正常结束只作**触发时点**，是否真压由体积阈值 + 收益阈值决定；
 * 只有 `applied=true` 才落账（W10/S3 起 `cache_invalidated=false`），
 * 未压轮次保留严格前缀单调性。
 *
 * ## 阈值量纲（字符，与 L3 同源）
 *
 * `COMPACT_TRIGGER_CHARS = 50_227` = `(32_000 - 4_096) × 1.8`，即 32K 档硬预算
 * 27 904 token × floor 1.8，与 `BudgetFormulaTest` 的 `hardCharCap` 50 227 同值；
 * `MIN_SAVINGS_CHARS = 460` = 草稿 256 token 按 floor 1.8 换算（460.8 → 460），
 * **不照抄 256**（那是 token 量纲），也不调用 `TokenEstimator.estimateMessages`
 * 自证（W5 33.8K 假绿）。用例一律引用常量而非硬编码数值，阈值改动不会制造假红。
 *
 * ## 路径矩阵（改事件序列前必须枚举）
 *
 * `EventReducer.kt:62/64` 的 `Finished` 与 `Cancelled` **共用 `finish(...)`**，
 * `ChatViewModel.runOnce` 的 `finally` 无条件置 `isStreaming=false` ⇒
 * 自动压缩只能挂在 `collect` 内按事件类型分流（[shouldEvaluate]），
 * **绝不挂 `finally`、不能只看 streaming 归零**。内核正常收尾唯一出口是
 * `RunTermination.emitFinished`，其余四条是 `emitMaxRounds` / `emitBreaker` /
 * `emitFailed` / `emitCancelled`。
 *
 * ## 两套自动压缩与「被调用」判据（W4 铁律；本类不造假调用点）
 *
 * 内核压缩（`AgentPolicy.compressContext` → `ContextBudget.prepare`，压单次请求工作副本）
 * 与本策略（压跨 run 会话历史）是两套，复用 `SettingsRepository.compressContext` 单开关：
 * 关则两套都停、手动压缩仍可用；「被调用」判据必须两套分别给，不许合并成一条。
 * 本策略预计调用点：UI 席 `ChatViewModel.collect` 内事件分流 →
 * `ConversationOps.autoCompactOnSettled` 唯一调用点；CI 席加「调用点 == 1 且位于
 * `collect` 内」的赋值形态扫描 + selftest 变异自证（删调用 ⇒ 判红）。
 * 真机判据：`auto_compact evaluated=1 reason=settled` 只在正常结束轮出现，
 * 失败 / 取消轮 0 命中且必须同时构造一次开开关的正常结束证明路径被走过。
 *
 * ## 反例锚
 *
 * `Finished(BreakerTripped / MaxRounds / GateRejected / Interrupted)`、`Failed`、
 * `Cancelled`、任何非终态事件一律 false；收益省不够不压；
 * `alreadyCompressed` ⇒ `reason = "already_compressed"`，**不静默**
 * （W6 `ConversationOps.kt:88` 的静默返回不许照抄到自动路径）。
 */
class SettledCompressPolicyTest {

    private fun finished(reason: TerminationReason) =
        AgentEvent.Finished(text = "", rounds = 1, terminatedBy = reason)

    @Test
    fun onlyModelStoppedFinishedTriggersEvaluation() {
        assertTrue(shouldEvaluate(finished(TerminationReason.ModelStopped)))
    }

    @Test
    fun breakerTrippedDoesNotEvaluate() {
        assertFalse(shouldEvaluate(finished(TerminationReason.BreakerTripped)))
    }

    @Test
    fun maxRoundsDoesNotEvaluate() {
        assertFalse(shouldEvaluate(finished(TerminationReason.MaxRounds)))
    }

    @Test
    fun gateRejectedAndInterruptedDoNotEvaluate() {
        assertFalse(shouldEvaluate(finished(TerminationReason.GateRejected)))
        assertFalse(shouldEvaluate(finished(TerminationReason.Interrupted)))
    }

    @Test
    fun failedDoesNotEvaluate() {
        assertFalse(shouldEvaluate(AgentEvent.Failed(message = "boom")))
    }

    @Test
    fun cancelledDoesNotEvaluate() {
        assertFalse(shouldEvaluate(AgentEvent.Cancelled(partialText = "partial")))
    }

    @Test
    fun nonTerminalEventsDoNotEvaluate() {
        assertFalse(shouldEvaluate(AgentEvent.TextDelta(text = "x")))
        assertFalse(shouldEvaluate(AgentEvent.UsageUpdated(prompt = 1, completion = 1, cachedPrompt = null)))
        assertFalse(shouldEvaluate(AgentEvent.Retrying(reason = "network")))
    }

    @Test
    fun insufficientNetSavingsDoesNotApply() {
        val decision = shouldApply(COMPACT_TRIGGER_CHARS + 1, MIN_SAVINGS_CHARS - 1, 0, alreadyCompressed = false)
        assertEquals(false, decision.applied)
        assertTrue(decision.reason.isNotBlank())
    }

    @Test
    fun belowTriggerDoesNotApply() {
        val decision = shouldApply(COMPACT_TRIGGER_CHARS, MIN_SAVINGS_CHARS * 10, 0, alreadyCompressed = false)
        assertEquals(false, decision.applied)
        assertTrue(decision.reason.isNotBlank())
    }

    @Test
    fun alreadyCompressedIsNotSilent() {
        val decision = shouldApply(COMPACT_TRIGGER_CHARS + 1, MIN_SAVINGS_CHARS * 10, 0, alreadyCompressed = true)
        assertEquals(false, decision.applied)
        assertEquals("already_compressed", decision.reason)
    }

    @Test
    fun netSavingsExactlyAtMinApplies() {
        val decision = shouldApply(COMPACT_TRIGGER_CHARS + 1, MIN_SAVINGS_CHARS, 0, alreadyCompressed = false)
        assertEquals(true, decision.applied)
        assertTrue(decision.reason.isNotBlank())
    }

    @Test
    fun appliesWhenSavingsMeetThreshold() {
        val decision = shouldApply(COMPACT_TRIGGER_CHARS + 1, MIN_SAVINGS_CHARS * 10, 0, alreadyCompressed = false)
        assertEquals(true, decision.applied)
        assertTrue(decision.reason.startsWith("applied"))
    }
}
