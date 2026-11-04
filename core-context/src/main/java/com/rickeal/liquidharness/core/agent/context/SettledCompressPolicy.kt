package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.TerminationReason

/**
 * 「对话正常运行结束、告一段落」时是否压缩会话历史的策略（W7，用户裁决 (a)）。
 *
 * ## 为什么是「评估一次」而不是「每轮都压」
 *
 * 现役压缩（`ConversationOps.compressContext`）的产物落在 `ChatUiState.compressedUpTo`
 * **单指针**上，`compressedUpTo != null` 时重复压缩静默返回 ⇒ 无条件每轮压从第 2 轮起
 * 就是空操作。更关键的是压缩丢掉最老一段 ⇒ 前缀从第一个字节起变化 ⇒ 下一次
 * `cached_tokens` 必归 0。用户同一次需求里还要「分块缓存命中」，两条不能照字面同时成立。
 * 裁决：**正常结束只作触发时点**，是否真压由体积阈值 + 收益阈值决定；只有
 * `applied=true` 才落 `cache_invalidated=true`，未压轮次保留严格前缀单调性。
 *
 * ## 与内核自动压缩是两套机制（复用同一个开关，不复用语义）
 *
 * - 内核压缩：`AgentPolicy.compressContext` → `ContextBudget.prepare`，按软预算压**单次请求的
 *   工作副本**，`RunState` 每 run 新建，产物不落 UI 气泡。
 * - 本策略：压的是**跨 run 的会话历史**（UI 气泡 → `compressedUpTo`），落点只能是
 *   `ConversationOps`（内核不暴露跨 run 压缩 API）。
 *
 * `SettingsRepository.compressContext` 这一个开关同时控制两套：`false` ⇒ 两套都停，
 * 但**手动压缩仍可用**（显式用户动作，不受自动策略约束）。「被调用」判据必须两套分别给，
 * 不许因共用开关合并成一条（W4 事故：9 条纯函数单测全绿但组件根本没接进链路）。
 *
 * ## 路径矩阵（改事件序列前必须枚举）
 *
 * `EventReducer.kt:62/64` 的 `Finished` 与 `Cancelled` **共用 `finish(...)`**，
 * `ChatViewModel.runOnce` 的 `finally` 无条件置 `isStreaming=false` ⇒ 自动压缩只能挂在
 * `collect` 内按事件类型分流（[shouldEvaluate]），**绝不能挂 `finally`、不能只看 streaming 归零**。
 * 内核正常收尾唯一出口是 `RunTermination.emitFinished`，其余 `emitMaxRounds` / `emitBreaker` /
 * `emitFailed` / `emitCancelled` 是另四条。
 *
 * ## 量纲：与 L3 同源，不另起估算器
 *
 * 全部用**字符**，与 W6 的 L3 字符硬预算同源。不引入 `TokenEstimator.estimateMessages`
 * —— 它对代码 / JSON 乐观、对散文保守，拿它自证等于没量（W5-01）。
 * - [COMPACT_TRIGGER_CHARS] = `50 227` = 32K 档 floor 字符 cap `(32 000 − 4 096) × 1.8`，
 *   与 `BudgetFormulaTest.kt:52` 的 `50 227` 同值 —— 触发线贴着现役硬预算，不拍脑袋。
 * - [MIN_SAVINGS_CHARS] = `460` = 草稿的 256 token × `hardCharsPerToken` floor 1.8，
 *   与字符 cap 用同一个换算因子。省不够就不动历史，避免小收益造前缀断点。
 */
const val COMPACT_TRIGGER_CHARS: Int = 50_227

/** 净收益低于此值不压（256 token × floor 1.8，见类头量纲说明）。 */
const val MIN_SAVINGS_CHARS: Int = 460

data class CompactDecision(
    val applied: Boolean,
    /** ASCII 诊断码。`nothing_to_drop` 一类空转必须有可见原因，不许静默。 */
    val reason: String,
)

/**
 * 是否该在本次终态事件上**评估一次**自动压缩。
 *
 * 绿：只有 `Finished(ModelStopped)` —— 模型自己停了，才叫「正常运行结束、告一段落」。
 * 红：`MaxRounds` / `BreakerTripped` / `GateRejected` / `Interrupted` / `Failed` /
 * `Cancelled` / 任何非终态事件一律 false。
 */
fun shouldEvaluate(event: AgentEvent): Boolean =
    event is AgentEvent.Finished && event.terminatedBy == TerminationReason.ModelStopped

/**
 * 评估通过后再判是否**真的压**。
 *
 * @param committedChars 当前会话已物化的上下文字符数（与 `evaluateSwitch` 同一取数口径）。
 * @param savingChars 压缩预计能省下的字符数。
 * @param compactCostChars 压缩本身的代价（切点计算 / 摘要 / 落账等字符当量）。
 * @param alreadyCompressed `ChatUiState.compressedUpTo != null`。
 */
fun shouldApply(
    committedChars: Int,
    savingChars: Int,
    compactCostChars: Int,
    alreadyCompressed: Boolean,
): CompactDecision {
    if (alreadyCompressed) return CompactDecision(false, "already_compressed")
    if (committedChars <= COMPACT_TRIGGER_CHARS) return CompactDecision(false, "below_trigger")
    val net = savingChars - compactCostChars
    if (net < MIN_SAVINGS_CHARS) {
        return CompactDecision(false, "insufficient_savings:net=$net")
    }
    return CompactDecision(true, "applied:net=$net")
}
