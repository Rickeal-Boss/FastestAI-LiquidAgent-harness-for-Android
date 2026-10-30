package com.rickeal.liquidharness.feature.chat

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rickeal.liquidharness.core.design.GlassIconButton
import com.rickeal.liquidharness.core.design.GlassThinkingIndicator
import com.rickeal.liquidharness.core.design.GlassTopBar
import com.rickeal.liquidharness.core.design.LocalGlassColors

/**
 * 对话页顶栏。
 *
 * 沿用旧仓的形态：标题 + 端点副标题，右侧「轮次指示器 → 设置 → 新对话」。
 * 顶栏图标一律 `pressOnly = true` —— 它们本来就贴在顶栏自己的玻璃上，
 * 再叠一层玻璃会浑浊，且会复现「镜面高光溢出盖住标题」的已知问题。
 *
 * 刻意不做居中标题（旧仓实读结论）：右侧 actions 恒有设置键、生成中还多一个
 * 宽度可变的轮次指示器，左侧为空 —— 两侧不等宽时居中会把标题挤向左侧。
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
