package com.rickeal.liquidharness.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rickeal.liquidharness.core.design.GlassIconButton
import com.rickeal.liquidharness.core.design.GlassThinkingIndicator
import com.rickeal.liquidharness.core.design.GlassTopBar
import com.rickeal.liquidharness.core.design.LocalGlassColors

/**
 * 对话页顶栏。
 *
 * 沿用旧仓的形态：标题 + 端点副标题，右侧「轮次指示器 → 溢出菜单 → 设置 → 新对话」。
 * 顶栏图标一律 `pressOnly = true` —— 它们本来就贴在顶栏自己的玻璃上，
 * 再叠一层玻璃会浑浊，且会复现「镜面高光溢出盖住标题」的已知问题。
 *
 * 刻意不做居中标题（旧仓实读结论）：右侧 actions 恒有设置键、生成中还多一个
 * 宽度可变的轮次指示器，左侧为空 —— 两侧不等宽时居中会把标题挤向左侧。
 *
 * @param onCompressContext 非空才渲染溢出菜单（W6 手动压缩的唯一入口）。null =
 *   宿主不提供该能力，不摆一个点了没反应的菜单项 —— 与 [onNewConversation] 同一条纪律。
 * @param compressEnabled 菜单项可用性。在途 run 时置 false：压缩会改写「本次 run 已发出
 *   的历史」而 run 手里是另一份副本，允许它只会造出「界面已压缩、这次请求没压」的不一致
 *   （见 `ChatViewModel.compressContext` 的前置拒绝）。
 */
@Composable
internal fun ChatTopBar(
    title: String,
    subtitle: String,
    isStreaming: Boolean,
    round: Int,
    maxRounds: Int,
    onOpenSettings: () -> Unit,
    onNewConversation: (() -> Unit)?,
    onOpenDrawer: (() -> Unit)? = null,
    onCompressContext: (() -> Unit)? = null,
    compressEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    GlassTopBar(
        title = title,
        subtitle = subtitle,
        modifier = modifier.statusBarsPadding(),
        // 抽屉入口放在标题左侧：这是旧仓的位置，也是用户唯一能「看见」的入口
        // （只剩边缘滑手势 = 功能被藏起来）。
        navigationIcon = onOpenDrawer?.let { open ->
            {
                GlassIconButton(
                    icon = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.chat_open_drawer),
                    onClick = open,
                    contentColor = colors.onGlassMuted,
                    pressOnly = true,
                )
            }
        },
        actions = {
            if (isStreaming && maxRounds > 0) {
                GlassThinkingIndicator(
                    label = stringResource(R.string.chat_round_indicator, round, maxRounds),
                )
            }
            if (onCompressContext != null) {
                ChatOverflowMenu(
                    onCompressContext = onCompressContext,
                    compressEnabled = compressEnabled,
                )
            }
            GlassIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.chat_settings),
                onClick = onOpenSettings,
                contentColor = colors.onGlassMuted,
                pressOnly = true,
            )
            if (onNewConversation != null) {
                GlassIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.chat_action_new_conversation),
                    onClick = onNewConversation,
                    contentColor = colors.onGlassMuted,
                    pressOnly = true,
                )
            }
        },
    )
}

/**
 * 顶栏溢出菜单（W6 首个下拉菜单）。
 *
 * ## 为什么用 Material3 的 `DropdownMenu` 而不是自绘玻璃面板
 *
 * 一个只装一项的菜单不值得再引一套玻璃浮层：`LiquidDialog` 是**模态**（带 scrim），
 * 用它当菜单会把「选一项」变成「确认一次」；而自绘玻璃浮层要处理定位、锚点、出场动画，
 * 那是 core-design 的活，不是本波（P1）的活。`DropdownMenu` 直接吃 `MaterialTheme`
 * 的 `surface` 系列颜色，在深色主题下与玻璃面板的对比度足够。
 * 若将来要玻璃化，替换点只有本函数一处。
 */
@Composable
private fun ChatOverflowMenu(
    onCompressContext: () -> Unit,
    compressEnabled: Boolean,
) {
    val colors = LocalGlassColors.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        GlassIconButton(
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.chat_more_actions),
            onClick = { expanded = true },
            contentColor = colors.onGlassMuted,
            pressOnly = true,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                enabled = compressEnabled,
                text = {
                    Text(
                        text = stringResource(R.string.chat_action_compress),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = {
                    // 先收菜单再回调：确认对话框是模态窗口，菜单若还开着会叠在它下面。
                    expanded = false
                    onCompressContext()
                },
            )
        }
    }
}
