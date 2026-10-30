package com.rickeal.liquidharness.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassScaffold
import com.rickeal.liquidharness.core.design.LocalGlassColors

/**
 * 对话页。
 *
 * 刻意做成**无状态 + 回调**：状态由 [ChatUiState] 传入，所有交互走回调。
 * 这样 Compose Preview 与 UI 测试都不需要 ViewModel / 网络。
 *
 * 布局沿用旧仓的骨架（[GlassScaffold]）：顶栏浮玻璃 → 消息列表 → 底部输入区，
 * 状态卡（授权 / 错误 / 终止提示）走 snackbar 槽浮在输入区上方。
 *
 * @param onNewConversation 非空才渲染「新对话」入口。null = 宿主不提供该能力
 *   （`app` 尚未接线时保持 null，顶栏只留设置键，不留一个点了没反应的图标）。
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onNewConversation: (() -> Unit)? = null,
    onOpenDrawer: (() -> Unit)? = null,
) {
    GlassScaffold(
        modifier = modifier,
        topBar = {
            ChatTopBar(
                title = stringResource(R.string.chat_title),
                subtitle = state.endpointLabel ?: stringResource(R.string.chat_subtitle_empty),
                isStreaming = state.isStreaming,
                round = state.round,
                maxRounds = state.maxRounds,
                onOpenSettings = onOpenSettings,
                onNewConversation = onNewConversation,
                onOpenDrawer = onOpenDrawer,
            )
        },
        bottomBar = {
            // 键盘 / 导航栏内边距挂在整列上：上下文条与用量行要跟着输入框一起被顶上去，
            // 只给输入框加会让它们被键盘盖住。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                val usage = state.usageLabel
                if (!usage.isNullOrBlank()) {
                    Text(
                        text = usage,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalGlassColors.current.onGlassSubtle,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 20.dp),
                    )
                }
                ChatContextMeter(
                    usedTokens = state.contextTokens,
                    limitTokens = state.contextLimit,
                )
                ChatInputBar(
                    draft = state.input,
                    onDraftChange = onInputChange,
                    onSend = onSend,
                    onStop = onStop,
                    isStreaming = state.isStreaming,
                    canSend = state.canSend,
                )
            }
        },
        snackbarHost = {
            ChatStatusColumn(state = state, onDismissError = onDismissError)
        },
    ) { _ ->
        // GlassScaffold 的 padding 只带「悬浮页签占位」；本屏没有页签，
        // 内容由 Column 的 weight 撑满，故不透传（旧仓同一处置）。
        ChatMessageList(
            bubbles = state.bubbles,
            streaming = state.streaming,
            toolTraces = state.toolTraces,
            isStreaming = state.isStreaming,
            hasEndpoint = state.endpointLabel != null,
            onOpenSettings = onOpenSettings,
        )
    }
}
