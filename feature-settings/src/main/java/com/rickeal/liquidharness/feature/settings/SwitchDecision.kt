package com.rickeal.liquidharness.feature.settings

import com.rickeal.liquidharness.core.agent.context.SwitchState
import com.rickeal.liquidharness.core.agent.context.SwitchVerdict
import com.rickeal.liquidharness.core.agent.context.evaluateSwitch
import com.rickeal.liquidharness.core.model.RemoteEndpoint

/**
 * 切换守卫在 UI 侧的**纯函数判定层**（W7）。
 *
 * `SettingsViewModel` 三处写入点统一写 `val verdict = evaluateSwitch(...)` 的赋值形态
 * （CI 席 G22 扫这个形状），再把 verdict 交给 [decideSwitch] 决定「拦下 / 二次确认」。
 * 抽出来是为了能在 JVM 上单测 —— `SettingsViewModel` 依赖 `AppContainer`，造不出来
 * （W4 铁律：新增组件必须同时给出「被调用」判据，但不能为了可测造假调用点）。
 *
 * ⚠️ **明文 API Key 绝不进 `SettingsUiState`**（`SettingsViewModel` 类头 :62-64）。
 * 所以 [PendingSwitch] 只承载 `SAVE_ENDPOINT` 之外的两种动作；编辑页保存的二次确认
 * 放在 `EndpointEditScreen` 的 Composable 局部 `remember` 状态里，密钥全程不进 state。
 */
/** 三处写入点的动作种类。public：`SettingsUiState`（public 类）的字段要引用它。 */
enum class SwitchKind { SAVE_ENDPOINT, SET_ACTIVE, SET_WINDOW }

internal enum class SwitchAction { BLOCK, CONFIRM }

internal data class SwitchDecision(val action: SwitchAction, val verdict: SwitchVerdict)

/** BLOCKED 上屏内容。`capChars` 解析不出来时为 null，UI 只给诊断串，不编数字。 */
data class SwitchNotice(
    val blocked: Boolean,
    val reason: String,
    val committedChars: Int,
    val capChars: Int?,
    val kind: SwitchKind,
    val targetId: String?,
)

/** 二次确认待办。**不含密钥**（见类头）；SAVE_ENDPOINT 不进这里。 */
data class PendingSwitch(
    val kind: SwitchKind,
    val endpointId: String? = null,
    val tokens: Int? = null,
    val vacuous: Boolean = false,
    val reason: String = "",
)

/** 纯函数：BLOCKED 一律拦下；其余（含 VACUOUS）都走显式二次确认，不硬绿。 */
internal fun decideSwitch(verdict: SwitchVerdict): SwitchDecision =
    SwitchDecision(
        action = if (verdict.state == SwitchState.BLOCKED) SwitchAction.BLOCK else SwitchAction.CONFIRM,
        verdict = verdict,
    )

/** 从 ASCII 诊断串 `blocked: committed=N cap=M <tier>` 取 M，用于上屏「N 字符 > M 字符」。 */
internal fun parseCapChars(reason: String): Int? =
    Regex("cap=(\\d+)").find(reason)?.groupValues?.get(1)?.toIntOrNull()

internal fun noticeOf(
    verdict: SwitchVerdict,
    kind: SwitchKind,
    targetId: String?,
    committedChars: Int,
    blocked: Boolean,
): SwitchNotice = SwitchNotice(
    blocked = blocked,
    reason = verdict.reason,
    committedChars = committedChars,
    capChars = parseCapChars(verdict.reason),
    kind = kind,
    targetId = targetId,
)

/**
 * 列表页「目标项置灰」的预计算：对每个端点用同一个纯函数判一次。
 *
 * `calibratedCharsPerToken = 0f` 是**刻意的**：设置页通常没有在途 run，没有会话级
 * 标定真值 ⇒ 走 floor 1.8 保守档（reason 带 `char_floor_unmeasured`），不许伪造真值。
 * 空会话会得 VACUOUS（不是 BLOCKED）⇒ 不会被置灰，但放行时仍要二次确认。
 */
internal fun blockedEndpointIds(
    endpoints: List<RemoteEndpoint>,
    committedChars: Int,
    maxContextTokens: Int,
): Set<String> = endpoints
    .filter {
        decideSwitch(evaluateSwitch(committedChars, it, maxContextTokens, 0f)).action == SwitchAction.BLOCK
    }
    .mapTo(linkedSetOf()) { it.id }
