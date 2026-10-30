package com.rickeal.liquidharness.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.liquid.Backdrop
import com.rickeal.liquidharness.core.design.liquid.backdrops.rememberBackdrop
import com.rickeal.liquidharness.core.design.liquid.backdrops.rememberCombinedBackdrop
import com.rickeal.liquidharness.core.design.liquid.drawBackdrop
import com.rickeal.liquidharness.core.design.liquid.effects.blur
import com.rickeal.liquidharness.core.design.liquid.effects.lens
import com.rickeal.liquidharness.core.design.liquid.highlight.Highlight
import com.rickeal.liquidharness.core.design.liquid.interactive.DampedDragAnimation
import com.rickeal.liquidharness.core.design.liquid.shadow.InnerShadow
import com.rickeal.liquidharness.core.design.liquid.shadow.Shadow
import com.rickeal.liquidharness.core.design.liquid.shapes.Capsule

/**
 * [GlassSlider] 的玻璃 thumb（视觉层，不含手势）。
 *
 * 从 `GlassSlider.kt` 原样外提：手势仍在 `GlassSlider` 的 48dp 静态宿主上（坐标反馈
 * 修复），本节点只做 `translationX` 平移；尺寸由调用方传入，与起手门禁共用同一组几何
 * 常量。`animation` 在函数体内别名为 `dampedDragAnimation`，使三块绘制代码与旧实现逐字一致。
 */
@Composable
internal fun GlassSliderThumb(
    backdrop: Backdrop,
    trackBackdrop: Backdrop,
    animation: DampedDragAnimation,
    trackWidth: Float,
    isLtr: Boolean,
    thumbWidth: Dp,
    thumbHeight: Dp,
) {
    val dampedDragAnimation = animation
    // Thumb：真玻璃 + 跟手各向异性拉伸。
    // ⚠️ 手势**不在**这层 —— 它在 `GlassSlider` 的 48dp 静态容器上。若本节点同时被
    // `graphicsLayer { translationX }` 平移又承载手势 ⇒ 手势节点 == 被驱动节点 ⇒
    // 一阶坐标反馈 ⇒ thumb 恒走手指一半（与 LiquidBottomTabs / GlassSegmented 同根因）。
    // 这里只留视觉平移；尺寸与门禁共用同一组几何常量（由调用方传入）。
    Box(
        Modifier
            .graphicsLayer {
                translationX =
                    (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                        .coerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                        if (isLtr) 1f else -1f
            }
            .drawBackdrop(
                backdrop = rememberCombinedBackdrop(
                    backdrop,
                    rememberBackdrop(trackBackdrop) { drawBackdrop ->
                        val progress = dampedDragAnimation.pressProgress
                        val scaleX = 2f / 3f + (1f - 2f / 3f) * progress
                        val scaleY = progress
                        scale(scaleX, scaleY) {
                            drawBackdrop()
                        }
                    }
                ),
                shape = { Capsule },
                effects = {
                    val progress = dampedDragAnimation.pressProgress
                    blur(8f.dp.toPx() * (1f - progress))
                    lens(
                        10f.dp.toPx() * progress,
                        14f.dp.toPx() * progress,
                        chromaticAberration = true
                    )
                },
                highlight = {
                    val progress = dampedDragAnimation.pressProgress
                    Highlight.Ambient.copy(
                        width = Highlight.Ambient.width / 1.5f,
                        blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                        alpha = progress
                    )
                },
                shadow = {
                    Shadow(
                        radius = 4f.dp,
                        color = Color.Black.copy(alpha = 0.05f)
                    )
                },
                innerShadow = {
                    val progress = dampedDragAnimation.pressProgress
                    InnerShadow(
                        radius = 4f.dp * progress,
                        alpha = progress
                    )
                },
                layerBlock = {
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                    // 快速拖动时沿拖动方向拉长、垂直方向压扁 —— 液态感最强的一处。
                    val velocity = dampedDragAnimation.velocity / 10f
                    scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                    scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                },
                onDrawSurface = {
                    val progress = dampedDragAnimation.pressProgress
                    drawRect(Color.White.copy(alpha = 1f - progress))
                }
            )
            .size(thumbWidth, thumbHeight),
    )
}
