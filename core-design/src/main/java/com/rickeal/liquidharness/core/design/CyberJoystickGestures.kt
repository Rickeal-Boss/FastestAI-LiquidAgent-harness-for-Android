package com.rickeal.liquidharness.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// 摇杆开关的**手势与语义**（点击 / 水平拖拽 / TalkBack 状态）。
// 从 CyberJoystickSwitch.kt 外移：那边单文件已 612 行 > 上限 600（G10），
// 而这两个扩展函数与视觉绘制无关，是最干净的一刀。逻辑逐字未动，
// 只把可见性从 private 放宽到 internal（同模块跨文件需要）。

internal fun Modifier.joystickDrag(
    progress: Animatable<Float, AnimationVector1D>,
    scope: CoroutineScope,
    springSpec: AnimationSpec<Float>,
    haptics: GlassHaptics,
    currentChecked: Boolean,
    currentOnCheckedChange: (Boolean) -> Unit,
    thumbStartX: Float,
    thumbEndX: Float,
): Modifier = this
        .pointerInput(thumbStartX, thumbEndX) {
            var dragBase = 0f
            var accumulatedPx = 0f
            val rangePx = (thumbEndX - thumbStartX).coerceAtLeast(1f)
            detectHorizontalDragGestures(
                onDragStart = {
                    dragBase = progress.value
                    accumulatedPx = 0f
                    scope.launch { progress.stop() }   // 停弹簧, drag 接管
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    accumulatedPx += dragAmount
                    val p = (dragBase + accumulatedPx / rangePx).coerceIn(0f, 1f)
                    scope.launch { progress.snapTo(p) }
                },
                onDragEnd = {
                    val p = progress.value
                    val newChecked = p > 0.5f
                    if (newChecked != currentChecked) {
                        // 提交分支触感：与回调同一位点（用户提交），不双发。
                        haptics.toggle(newChecked)
                        currentOnCheckedChange(newChecked)  // LaunchedEffect 从拖拽位置弹到目标
                    } else {
                        // 未过半: 从拖拽位置弹回原态（不提交、不发触感）。
                        scope.launch { progress.animateTo(if (currentChecked) 1f else 0f, springSpec) }
                    }
                },
                onDragCancel = {
                    scope.launch { progress.animateTo(if (currentChecked) 1f else 0f, springSpec) }
                },
            )
        }

/**
 * 摇杆的语义与点击（外提，行数闸门 G11）。
 *
 * TalkBack 状态语义（审查 P2-2）：clickable(role = Role.Switch) 只给角色不给状态，
 * 念不出开/关，故补 stateDescription（文案由调用方传，null ⇒ 不设）。role 不重复设：
 * clickable 已挂 Role.Switch，同一节点上重复写只是噪音。stateDescription 读组合期
 * checked：本组件 checked 变化必触发重组，无陈旧快照问题。
 */
internal fun Modifier.joystickToggle(
    checked: Boolean,
    stateDescriptionOn: String?,
    stateDescriptionOff: String?,
    interactionSource: MutableInteractionSource,
    haptics: GlassHaptics,
    currentChecked: Boolean,
    currentOnCheckedChange: (Boolean) -> Unit,
): Modifier = this
    .semantics {
        val stateLabel = if (checked) stateDescriptionOn else stateDescriptionOff
        if (stateLabel != null) stateDescription = stateLabel
    }
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        role = Role.Switch,
        onClick = {
            // 触感前置：haptics.toggle 与 GlassSwitch 同纪律 —— 只在提交位点发，
            // 严禁挂 LaunchedEffect(checked) 回显收集器（外部回流也会震）。
            haptics.toggle(!currentChecked)
            currentOnCheckedChange(!currentChecked)
        },
    )
