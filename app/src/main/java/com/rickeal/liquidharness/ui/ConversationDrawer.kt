package com.rickeal.liquidharness.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.R
import com.rickeal.liquidharness.core.design.GlassButton
import com.rickeal.liquidharness.core.design.GlassMaterial
import com.rickeal.liquidharness.core.design.LiquidGlassSurface
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.design.LocalGlassConfig
import com.rickeal.liquidharness.core.design.LocalGlassTokens
import com.rickeal.liquidharness.core.design.asOverlayColors

/**
 * 抽屉里的一条会话。
 *
 * ⚠️ **W3 待接线**：本仓 W1/W2 没有会话持久化（roadmap 明确 W3 才做），所以这里的
 * 数据源是一个**明确标注的内存列表**，当前只有「本次运行的一条对话」。
 * 不要在这里伪造历史记录、也不要接一个假的时间戳 —— 做不到的事不在 UI 上承诺。
 * W3 落地时把 [inMemoryEntries] 换成 `ConversationRepository.metas` 的
 * `collectAsState()`，[DrawerEntry] 换成真实的 `ConversationMeta` 即可，
 * 布局与交互无需改动。
 */
private data class DrawerEntry(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
)

/**
 * 内存数据源（W3 待接线）。
 *
 * 只有一条：当前这次运行正在进行的对话。它**不是**历史记录 —— 重启应用即消失。
 * 空态分支仍然保留（[inMemoryEntries] 为空时渲染 [R.string.drawer_empty_title]），
 * 这样 W3 换成真实数据源后「一条都没有」的路径已经被验证过。
 */
private val inMemoryEntries = listOf(
    DrawerEntry(
        titleRes = R.string.drawer_current_conversation,
        subtitleRes = R.string.drawer_current_subtitle,
    ),
)

/**
 * 左侧会话抽屉（原样搬自旧仓 ConversationDrawer，数据源按本仓现状降级）。
 *
 * 参照 WorkBuddy 的「云端▾ + 新建任务 + 任务列表」结构，但按本仓的真实能力降级，
 * 不照抄：
 *  - 「云端▾」：端侧应用没有可切换的云端工作区 → 做成**静态说明头**，不做假下拉；
 *  - 「新建任务」：本仓是「新建对话」。当前没有多会话，点击的语义就是「关抽屉、
 *    回到当前这条对话」—— 所以它和点会话行走同一个回调，不编造一个"新建"的假动作；
 *  - 「空间(N)」：无云端空间概念，本仓也没有沙箱目录 → **整块删除**，不摆一个点不动的入口。
 *
 * 抽屉本体走液态玻璃（[LiquidGlassSurface]），内容全部由调用方注入的回调驱动 ——
 * 这里不认识导航、不认识 AppContainer，方便单独审视。
 */
@Composable
internal fun ConversationDrawer(
    onNewConversation: () -> Unit,
    onOpenCurrentConversation: () -> Unit,
) {
    // 覆盖层根取色：本组件整块就是覆盖层面板 —— 表面恒深色（THICK 玻璃 + 内 scrim +
    // 平台 dim），深色 onGlass 在其上对比度不足。只换 onGlass 三件套：scrim 的
    // glassShadow、accent 不被 copy 触碰。嵌套组件在自身组合期读 LocalGlassColors，
    // 由下面的 CompositionLocalProvider 继承。
    val colors = LocalGlassColors.current.asOverlayColors()
    val tokens = LocalGlassTokens.current

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxHeight()
            .width(DRAWER_WIDTH),
        material = GlassMaterial.THICK,
        // 贴左缘、通顶到底的抽屉：直角，不做胶囊。
        cornerRadius = 0.dp,
        contentPadding = PaddingValues(0.dp),
        // 覆盖层面板：柔化白描边与高光，消除深色面板上的"白框"隔开感。
        overlayPanel = true,
    ) {
        // 覆盖层 scrim：alpha 由 GlassConfig.overlayOpacity 驱动。
        // 插在表面绘制之后、内容之前：matchParentSize 不占布局测量，纯视觉压暗。
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    colors.glassShadow.copy(alpha = LocalGlassConfig.current.overlayOpacity),
                ),
        )
        CompositionLocalProvider(LocalGlassColors provides colors.asOverlayColors()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // 吃掉抽屉内**空白区**的点击：LiquidGlassSurface 没传 onClick ⇒ 没有
                    // pointerInput 节点 ⇒ 点击会穿透到 M3 的遮罩上，而遮罩的 onClick 就是
                    // 「关抽屉」⇒ 在空白处点一下会误关。空 onClick 只为占住这个节点。
                    // 子项（会话行 / 按钮）先命中，不受影响。
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {},
                    ),
            ) {
                DrawerHeader(colors = colors)
                GlassButton(
                    text = stringResource(R.string.drawer_new_conversation),
                    onClick = onNewConversation,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = colors.onGlass,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(tokens.gapMd))
                Text(
                    text = stringResource(R.string.drawer_section_conversations),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onGlassMuted,
                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
                )
                if (inMemoryEntries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.drawer_empty_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onGlassSubtle,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(inMemoryEntries) { entry ->
                            DrawerEntryRow(
                                entry = entry,
                                onClick = onOpenCurrentConversation,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.drawer_persistence_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onGlassSubtle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun DrawerHeader(colors: com.rickeal.liquidharness.core.design.GlassColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.drawer_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onGlass,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.drawer_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onGlassSubtle,
        )
    }
}

/**
 * 单个会话行：标题 + 一行诚实副标题。
 *
 * 与页签项同一口径：**不给 ripple**（`indication = null`）—— 抽屉本身就是一块玻璃，
 * 再叠材质涟漪就是「玻璃上贴塑料」。点选后抽屉立即关闭，这本身就是反馈。
 */
@Composable
private fun DrawerEntryRow(
    entry: DrawerEntry,
    onClick: () -> Unit,
) {
    val colors = LocalGlassColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(entry.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onGlass,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(entry.subtitleRes),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onGlassSubtle,
            )
        }
    }
}
