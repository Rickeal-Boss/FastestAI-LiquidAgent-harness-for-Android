package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.model.RemoteEndpoint

/**
 * 切换端点 / 模型 / 窗口档位前的**容量判据**（W7）。
 *
 * ## 用户裁决（2026-11-04）
 *
 * 「实际上下文 320k，目标模型只有 256k ⇒ 直接禁止切换；超过部分除非上下文能够压缩至
 * 范围内」。⇒ 判的是**装不装得下**，不是「目标窗口是否更大」。切到 1M 端点只要装得下
 * 就放行 —— 那正是长程测试的必经路径；先压缩到目标窗口内再切也是合法恢复路径，
 * 由 UI 在压缩后重新调用本函数（纯函数、同输入同输出）实现。
 *
 * ## 为什么不自己写预算算式
 *
 * [hardContextBudgetTokens] 已经包含三件事：`verifiedContextLength` 优先于手填声明值、
 * 减掉 `maxOutputTokens`（1 000 000 是输入 + 输出共享上限）、以及 `window/2` 兜底。
 * 在这里再写一遍 `min(verified ?: contextLength, maxContextTokens) - maxOutputTokens`
 * 就是**第二个真值源**：W5-01 的 33.8K 事故正是「用同一把有偏的尺子证明自己没超」。
 * 连「窗口有效值」也不自己算 —— 用 `maxOutputTokens = 0` 的影子端点调同一个函数即可
 * 取回 `windowEffective`（`usable = window - 0 > 0` 时返回的正是 window）。
 *
 * ## 容量判据必须排在兜底分支之前
 *
 * `window/2` 兜底会让 cap 变小，但它**不能成为放行的理由**：一个已经装不下的会话，
 * 无论目标端点是否走了兜底，都不该允许切换。首版曾把兜底分支排在 `BLOCKED` 之前，
 * 结果是「装不下 + 输出预留过大」时给出 VACUOUS 放行 —— 与用户容量判据直接冲突。
 * 现由 `SwitchGuardTest` 的交叉态用例钉住顺序。
 *
 * ## 字符量纲必须二档
 *
 * `TokenEstimator` 对散文 5.4–6.5 字符/token、对代码 / JSON ≈2.0，偏差是**内容相关**的。
 * [effectiveCharsPerToken] 在「本轮还没有 provider 真值」时回落 floor 1.8（保守，宁可判红），
 * 有真值时用 `measured.coerceIn(1.8, 6.0)`（标定档，避免把散文会话的可用空间砍到 1/3）。
 * `measured` 现役是 **per-run**（`RunState.measuredCharsPerToken`），切换发生在设置页通常
 * 没有在途 run ⇒ 守卫大概率走 floor 保守档【推断】，故 [SwitchVerdict.reason] 带出
 * `char_floor_unmeasured` 供日志诊断。本波不新增会话级持久化标定值（避免动 `AppContainer`）。
 *
 * ## 「被调用」判据
 *
 * 消费者是 `feature-settings/SettingsViewModel` 的三处写入点（`save` / `setActive` /
 * `setMaxContextTokens`）与切换确认框；守卫 G22 由 CI 席在调用点成形后补，钉**赋值形态**
 * 而非裸类名。调用点落地前，本文件只是可测的策略，**不构成「切换已被拦下」的证据**。
 */
enum class SwitchState {
    /** 判定式真的被行使过且装得下。 */
    ALLOWED,

    /** 当前会话装不进目标生效硬预算 ⇒ 禁止写盘。 */
    BLOCKED,

    /** 判据未被行使（空会话 / 窗口未验证 / 兜底分支）⇒ 不得记为通过。 */
    VACUOUS,
}

data class SwitchVerdict(
    val allowed: Boolean,
    /** ASCII 诊断码，只进日志与 UI 提示的原因串，不做字符串解析判据。 */
    val reason: String,
    val state: SwitchState,
)

/**
 * @param committedChars 当前会话已物化的上下文字符数，由 UI 侧按
 *   `bubbles.toHistoryMessages(compression)` 重建后逐条求和。**字符量纲**，与 L3 同源。
 * @param target 切换后的目标端点。`RemoteEndpoint.maxOutputTokens` 是输出预留的唯一来源。
 * @param maxContextTokens 切换后的会话档位（`SettingsRepository.maxContextTokens`）。
 * @param calibratedCharsPerToken 本轮 provider 实测 chars/token；`<= 0` = 无真值 ⇒ floor 档。
 */
fun evaluateSwitch(
    committedChars: Int,
    target: RemoteEndpoint,
    maxContextTokens: Int,
    calibratedCharsPerToken: Float,
): SwitchVerdict {
    val policy = AgentPolicy(maxContextTokens = maxContextTokens)
    val hardBudget = hardContextBudgetTokens(target, policy)
    val ratio = effectiveCharsPerToken(policy, calibratedCharsPerToken)
    val cap = charCapOf(hardBudget, ratio)
    val tier = if (calibratedCharsPerToken <= 0f) "char_floor_unmeasured" else "char_calibrated"

    // ① 容量判据优先：装不下就禁止，无论窗口是否已验证、是否走 window/2 兜底。
    if (committedChars > cap) {
        return SwitchVerdict(
            false,
            "blocked: committed=$committedChars cap=$cap $tier",
            SwitchState.BLOCKED,
        )
    }

    // ② window/2 兜底：`BudgetFormula.kt:34` 在 maxOutputTokens >= window 时按窗口一半算，
    // 此时预算不再对应任何真实窗口。这是**可达**分支（`coerceAtLeast(1)` 只保证 hardBudget
    // 为正，不保证没走兜底）⇒ 标 vacuous，而不是构造一个永远不触发的「预算非正」判据。
    val window = hardContextBudgetTokens(target.copy(maxOutputTokens = 0), policy)
    if (target.maxOutputTokens >= window) {
        return SwitchVerdict(true, "window_half_fallback $tier", SwitchState.VACUOUS)
    }

    if (committedChars <= 0) {
        return SwitchVerdict(true, "empty_session $tier", SwitchState.VACUOUS)
    }
    if (target.verifiedContextLength == null) {
        // 没有 400 学到的真实窗口 ⇒ 放行但不硬绿，UI 必须走显式二次确认。
        return SwitchVerdict(true, "unverified_window $tier", SwitchState.VACUOUS)
    }
    return SwitchVerdict(true, tier, SwitchState.ALLOWED)
}
