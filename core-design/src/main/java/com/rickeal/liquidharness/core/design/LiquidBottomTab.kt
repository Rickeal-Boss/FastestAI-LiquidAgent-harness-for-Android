package com.rickeal.liquidharness.core.design

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.rickeal.liquidharness.core.design.liquid.EmptyBackdrop
import com.rickeal.liquidharness.core.design.liquid.LocalBackdrop
import com.rickeal.liquidharness.core.design.liquid.backdrops.layerBackdrop
import com.rickeal.liquidharness.core.design.liquid.backdrops.rememberCombinedBackdrop
import com.rickeal.liquidharness.core.design.liquid.backdrops.rememberLayerBackdrop
import com.rickeal.liquidharness.core.design.liquid.drawBackdrop
import com.rickeal.liquidharness.core.design.liquid.effects.blur
import com.rickeal.liquidharness.core.design.liquid.effects.lens
import com.rickeal.liquidharness.core.design.liquid.effects.vibrancy
import com.rickeal.liquidharness.core.design.liquid.highlight.Highlight
import com.rickeal.liquidharness.core.design.liquid.interactive.DampedDragAnimation
import com.rickeal.liquidharness.core.design.liquid.interactive.InteractiveHighlight
import com.rickeal.liquidharness.core.design.liquid.shadow.InnerShadow
import com.rickeal.liquidharness.core.design.liquid.shadow.Shadow
import com.rickeal.liquidharness.core.design.liquid.shapes.Capsule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 单个页签项：图标 + 文本，单击改内部选中态。
 *
 * `indication = null`：液态玻璃的高光 / 折射反馈已足够，再叠 M3 ripple 就是
 * "原生按钮贴玻璃纸"。触摸目标 56dp（容器高 64 - 上下 4×2），高于 48dp 标准。
 */
@Composable
internal fun RowScope.LiquidBottomTab(
    tab: TabSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    /**
     * 是否参与「按压注册缩放」（吃 [LocalLiquidBottomTabScale]）。
     *
     * ⚠️ 与 [selected] **必须是两个布尔**（2026-09-26 用户真机裁定）：
     *  - [selected] 驱动**颜色 / 语义**（accent 高亮、TalkBack）——拖动中跟随胶囊
     *    （highlightIndex），让高亮随胶囊走；
     *  - [scaleWithPress] 驱动**几何缩放**——只允许「真选中格且胶囊压在其上」为 true。
     *    若沿用 selected（= highlightIndex），拖动经过未选中格时其深色图标/文字会被
     *    compound 放大 ~1.25x（真机录屏 22:55 实锤："黑色字体 icon 异常放大"）——
     *    用户裁定：**黑色内容永远恒定大小与位置**。折射蓝拷贝与真实内容的瞬时错位
     *    是上游同款观感（上游可见行从不缩放），不再是注册缺口。
     */
    scaleWithPress: Boolean = false,
) {
    val colors = LocalGlassColors.current
    val scale = LocalLiquidBottomTabScale.current
    // 局部别名：semantics 块里 `selected = isSelected` 的赋值目标（语义属性）与
    // 取值来源（函数参数）同名，不借用中间变量极易误读。
    val isSelected = selected
    Column(
        modifier
            .clip(Capsule)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            // Role.Tab 此前只有 role 没有 selected：TalkBack 播报不出「已选中」
            // （三线审查 Wave10）。加在 clickable 同一布局节点上，两个语义配置
            // 合并进同一节点；echo 层（:474）的 clearAndSetSemantics {} 是整层
            // 清空、本来就不播报，不受影响。
            // 注意必须写 this.selected：裸名 selected 会被同名函数参数遮蔽
            // （局部作用域优先于隐式 receiver 的扩展属性），赋值就落在 val 参数上。
            .semantics { this.selected = isSelected }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                // 入参 scaleWithPress（非 selected！两个布尔的分工见其 KDoc）：
                // 只有「真选中格且胶囊压在其上」做 compound 注册缩放，其余格恒 1f。
                val s = scale(scaleWithPress)
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (selected) colors.accent else colors.onGlassSubtle,
            modifier = Modifier.size(22.dp),
        )
        // 窄容器（compactTabs）下不渲染文字：4 字标签在 ~48dp 单项宽里会被 Clip 成
        // 半个字，宁可只剩图标（contentDescription 仍保留无障碍语义）。
        if (showLabel) {
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.onGlass else colors.onGlassSubtle,
                maxLines = 1,
                // 必须显式 Ellipsis：默认 Clip 在中文下是"切半个字"，比省略号观感差得多
                //（六路审查 B-P1-3）。
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
