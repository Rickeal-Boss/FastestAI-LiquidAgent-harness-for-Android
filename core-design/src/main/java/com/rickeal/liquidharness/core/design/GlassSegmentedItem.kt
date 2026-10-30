package com.rickeal.liquidharness.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 单个分段项：纯文字 + 单击改内部选中态。
 *
 * "选中"的玻璃表达由顶层滑动胶囊承担，item 自身不再挂 liquidGlass
 * （旧实现 REGULAR vs ULTRA_THIN 的厚薄区分会让胶囊滑到时出现双层玻璃）。
 *
 * `indication = null`：液态玻璃的高光 / 折射反馈已足够，再叠 M3 ripple 就是
 * "原生按钮贴玻璃纸"。触摸目标必须是完整 48dp。
 */
@Composable
internal fun SegmentItem(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    val tokens = LocalGlassTokens.current
    val motion = LocalLiquidMotion.current

    // ⚠️ 语义订正（W88）：旧代码把这个值叫 `pressScale`，但它的 targetValue 是
    // `if (selected) 1f else 0.94f` —— 那是**选中态**缩放，与按压无关。
    // 名字骗人 + 点未选中项毫无反馈，是本轮用户反馈"按钮没有回弹"的一部分。
    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = LiquidMotion.floatSpring(motion),
        label = "segmentSelectedScale",
    )
    // 真·按压反馈：按下 0.97、松手回弹。挂在 interactionSource 上而不是再挂一层
    // InteractiveHighlight.gestureModifier —— 分段控件的拖拽手势已经独占在
    // matchParentSize 的静态宿主上（见本文件上方"坐标反馈"注释），
    // 再加一层手势会与它抢事件（历史坑：InteractiveHighlight.kt:97-111）。
    val pressInteraction = remember { MutableInteractionSource() }
    val pressed by pressInteraction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.97f else 1f,
        animationSpec = LiquidMotion.floatSpring(motion),
        // 量程只有 0.03：默认阈值 0.01 会截掉最后 1/3 的回弹行程。
        visibilityThreshold = 0.001f,
        label = "segmentPressScale",
    )
    // 双影注册值（见 LocalPressScaleRegistration 的 KDoc）。**在 graphicsLayer 里调用**
    // 而不是组合期读：它逐帧变化，组合期读会让这个 Text 每帧重组。
    val pressScaleRegistration = LocalPressScaleRegistration.current
    val textColor = if (selected) colors.onGlass else colors.onGlassSubtle

    Box(
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = pressInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            // 触摸目标必须是完整 48dp（分段控件是主要操作入口）。
            .heightIn(min = tokens.minTouchTarget)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            // Wave 51 F2：补 Ellipsis（默认 Clip 在中文下是「切半个字」，见 LiquidBottomTabs.kt:1086）；
            // 补后仍会截断（变「CP…」），只是不再切半个字。
            overflow = TextOverflow.Ellipsis,
            // 选中/取消的弹簧缩放挂在 **Text**（链最内层）而不是 Box 外层：
            // graphicsLayer 缩放的是其后的绘制，若挂外层会把 48dp 命中区也缩掉
            //（0.94 → 实际命中 45dp，违反触摸目标标准）；挂内层只缩视觉不缩命中。
            modifier = Modifier.graphicsLayer {
                // 三个量相乘：选中态（0.94/1.0）× 按压（0.97/1.0）× 双影注册（胶囊 scaleY）。
                // 注册项在 draw 阶段读 ⇒ 按压逐帧变化只失效绘制，不重组。
                val s = selectedScale * pressScale * pressScaleRegistration(selected)
                scaleX = s
                scaleY = s
            },
            textAlign = TextAlign.Center,
        )
    }
}
