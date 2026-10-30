package com.rickeal.liquidharness.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.R
import com.rickeal.liquidharness.core.design.GlassMaterial
import com.rickeal.liquidharness.core.design.LiquidBottomTabs
import com.rickeal.liquidharness.core.design.LiquidGlassSurface
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.design.LocalGlassConfig
import com.rickeal.liquidharness.core.design.LocalGlassTokens
import com.rickeal.liquidharness.core.design.TabSpec
import com.rickeal.liquidharness.core.design.WindowSizeClass
import com.rickeal.liquidharness.core.design.WindowWidthClass
import com.rickeal.liquidharness.core.design.WindowHeightClass
import com.rickeal.liquidharness.core.design.liquid.interactive.InteractiveHighlight
import com.rickeal.liquidharness.core.design.liquidGlass
import com.rickeal.liquidharness.core.design.pressLayerBlock

/**
 * 对话页签图标。
 *
 * 自绘而不是 `Icons.Filled.Chat`：`material-icons-extended` 在 `:app` 里**没有**声明
 * （两个 feature 模块用的是 `implementation`，不会传递过来），而 `:app` 的构建脚本
 * 不在本单文件域内。用 `material-icons-core` 里没有的图标会直接编译失败，
 * 所以在本地用 24dp 视口画一个 Material 同形气泡，零新增依赖。
 */
private val ChatTabIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ChatBubble",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 2f)
            horizontalLineTo(4f)
            curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
            verticalLineTo(22f)
            lineTo(6f, 18f)
            horizontalLineTo(20f)
            curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
            verticalLineTo(4f)
            curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
            close()
        }
    }.build()
}

/** 一级目的地 → 图标。枚举加项时这里的 `when` 不穷尽会**编译失败**（天然守卫）。 */
internal fun iconOf(destination: TopDestination): ImageVector = when (destination) {
    TopDestination.CHAT -> ChatTabIcon
    TopDestination.SETTINGS -> Icons.Filled.Settings
}

/**
 * 底部 / 顶部悬浮页签条（COMPACT 竖屏与横屏手机共用同一枚组件）。
 *
 * 真·LiquidBottomTabs：选中指示胶囊随选中项滑动（Kyant0 四层结构），不用"材质厚薄"
 * 区分选中态。四层结构与材质/折射参数全部收在 `core-design`，这里只做
 * TopDestination → [TabSpec] 的映射。拖动胶囊可直接换页，单击未选中页签同样触发；
 * 外部选中态回流（返回键回到对话页等）会让胶囊滑回正确位置。
 *
 * @param verticalPadding 条的外层纵向内边距（W89）：竖屏底部 10dp、横屏顶部 4dp。
 *   只压内边距、**不动 64dp 条高**（横屏可用高度只有 308~344dp）。
 *   ⚠️ 改它必须同步 [AppScaffold] 里 `LocalBottomBarOverlay` / `LocalTopBarOverlay`
 *   的 84.dp / 72.dp —— 两处是"条高 + 上下内边距"的手工同源。
 */
@Composable
internal fun GlassNavBar(
    selected: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 10.dp,
) {
    LiquidBottomTabs(
        tabs = TopDestination.entries.map { destination ->
            TabSpec(label = stringResource(destination.labelRes), icon = iconOf(destination))
        },
        selectedIndex = TopDestination.entries.indexOf(selected),
        onSelected = { index -> onSelect(TopDestination.entries[index]) },
        modifier = modifier.padding(horizontal = 14.dp, vertical = verticalPadding),
    )
}

/**
 * 左侧导航栏（MEDIUM / EXPANDED）——「桌面端」分类排版的容器。
 *
 * 宽度按窗口档位参数化：MEDIUM 维持 88dp 图标栏，EXPANDED 加宽到 132dp 让
 * 「图标 + 文字」完全展开。
 *
 * 胶囊盖的竖向侵入量：`LiquidGlassSurface` 开 `capsule = true` 后圆角半径 = 宽/2
 * （88dp 档 r=44dp，132dp 档 r=66dp）。圆角弧线在条目左缘 x≈8dp 处的竖向侵入量
 * ≈19dp / 35dp，所以 `vertical` 取 28dp / 36dp（覆盖侵入后再留一档呼吸空间）。
 * 用显式 `when` 而不是 `railWidth * k`：这两个值是从胶囊几何推出来的离散档位，
 * 写成系数会让人误以为连续可调，反而藏住推导。
 *
 * 横屏手机高度预算（h=COMPACT，实测 344dp）：可用 = 344 − 外层 24 − 2×12 = 296dp，
 * 需要 2 项 × 48（触控纪律，不因横屏缩小）+ 间距 ≈ 100dp ⇒ 全部可见，品牌字仍保留。
 * 本仓只有两个页签，旧仓"5 项挤爆"的取舍（横屏隐藏文字 / 标题）不再需要。
 */
@Composable
internal fun GlassNavRail(
    selected: TopDestination,
    onSelect: (TopDestination) -> Unit,
    windowSize: WindowSizeClass,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    val tokens = LocalGlassTokens.current
    val railWidth = if (windowSize.width == WindowWidthClass.EXPANDED) 132.dp else 88.dp
    val compactHeight = windowSize.height == WindowHeightClass.COMPACT
    val contentVerticalPad = when {
        compactHeight -> 12.dp
        windowSize.width == WindowWidthClass.EXPANDED -> 36.dp
        else -> 28.dp
    }
    // 只有 EXPANDED（平板 / 桌面双栏）高度充裕到能放「图标 + 文字」。
    val showLabel = windowSize.width == WindowWidthClass.EXPANDED
    LiquidGlassSurface(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .width(railWidth),
        material = GlassMaterial.THIN,
        capsule = true,
        cornerRadius = tokens.radiusLg,
        dispersion = false,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = contentVerticalPad),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 4.dp else 6.dp),
        ) {
            if (!compactHeight) {
                Text(
                    text = stringResource(R.string.nav_rail_brand),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onGlass,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                )
            }
            for (destination in TopDestination.entries) {
                NavDestinationItem(
                    destination = destination,
                    selected = destination == selected,
                    onClick = { onSelect(destination) },
                    showLabel = showLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 单个页签项：胶囊玻璃 + 跟手形变。
 *
 * 三件事缺一就不是"液态"（对齐 `GlassSegmented.SegmentItem`）：
 *  1. 按下时玻璃"变实"（pressProgress → 模糊减弱 / 折射增强 / 高光变亮）
 *  2. tanh 阻尼的跟手位移（[pressLayerBlock]）—— 拖多远都不会飞出去，松手回弹
 *  3. 各向异性拉伸 —— 沿拖动方向拉长、垂直方向压扁；等比缩放就是"原生按钮"手感
 *
 * 每个页签项各自持有 [InteractiveHighlight]，**不能共用**：共用一个对象会导致
 * 按 A 时 B 也跟着形变。
 *
 * @param showLabel 是否显示文字标签。false = 仅图标：Icon 的 `contentDescription`
 *   已是 label，TalkBack 语义不丢；48dp 触控纪律由 `heightIn` 保持。
 */
@Composable
private fun NavDestinationItem(
    destination: TopDestination,
    selected: Boolean,
    onClick: () -> Unit,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    val tokens = LocalGlassTokens.current
    val animationScope = rememberCoroutineScope()
    // reduceMotion 必须进 remember key：翻开关时已有实例不会重建，按压仍走弹簧。
    val reduceMotion = LocalGlassConfig.current.reduceMotion
    val interactiveHighlight = remember(animationScope, reduceMotion) {
        InteractiveHighlight(animationScope, reduceMotion = reduceMotion)
    }
    val label = stringResource(destination.labelRes)
    // 局部别名：semantics 块里 `selected` 会被同名参数遮蔽，显式区分。
    val isSelected = selected

    Column(
        modifier = modifier
            // indication = null：液态玻璃自己有高光 / 内阴影反馈，
            // 再叠一层 M3 ripple 就变成"原生按钮贴玻璃纸"了。
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            // 与 LiquidBottomTabs 页签同一口径：Role.Tab 必须带 selected，
            // TalkBack 才播报得出「已选中」。
            .semantics { this.selected = isSelected }
            .liquidGlass(
                // 选中项给到 REGULAR 才有"浮起来"的厚度差；未选中压到最薄，让位给容器。
                material = if (selected) GlassMaterial.REGULAR else GlassMaterial.ULTRA_THIN,
                capsule = true,
                // 对齐 Kyant0 分段项：blur(8) + lens(24, 24)。
                // 模糊必须轻到能看见折射把背景像素"掰弯"，否则就是磨砂塑料。
                blurRadius = 8.dp,
                refractionHeight = 24.dp,
                refractionAmount = 24.dp,
                dispersion = false,
                pressProgress = { interactiveHighlight.pressProgress },
                layerBlock = pressLayerBlock(interactiveHighlight, maxScale = 16.dp),
            )
            .then(
                // 顺序不能交换：clickable 在前、手势在后（Kyant0 原序）。
                // 交换后 clickable 会先吃掉手势，跟手位移就没了。
                Modifier
                    .then(interactiveHighlight.modifier)
                    .then(interactiveHighlight.gestureModifier),
            )
            // 触摸目标必须是完整 48dp（项目自己在 GlassTokens.minTouchTarget 定的标准）。
            .heightIn(min = tokens.minTouchTarget)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = iconOf(destination),
            contentDescription = label,
            tint = if (selected) colors.accent else colors.onGlassSubtle,
            modifier = Modifier.size(22.dp),
        )
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.onGlass else colors.onGlassSubtle,
            )
        }
    }
}
