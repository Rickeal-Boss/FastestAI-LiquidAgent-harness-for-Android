package com.rickeal.liquidharness.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalDecision
import com.rickeal.liquidharness.core.design.GlassScaffold
import com.rickeal.liquidharness.core.design.LocalBottomBarOverlay
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
 * @param onResolveApproval 授权卡上的三个键：允许一次 / 本会话允许相同调用 / 拒绝。
 *   「本会话」= **这段对话**，不是这一次 run：会话级缓存在 ViewModel 上，跨 run 生效，
 *   只有「新对话」才清空（语义与失效点见 `ChatErrorPanel.ApprovalCard` 的 KDoc）。
 *   直接透传给 ViewModel —— 裁决的语义与回传通道都在内核侧，本屏只负责把点击送出去。
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
    onResolveApproval: (ToolApprovalDecision) -> Unit,
    modifier: Modifier = Modifier,
    onNewConversation: (() -> Unit)? = null,
    onOpenDrawer: (() -> Unit)? = null,
    onPickImage: (() -> Unit)? = null,
    onRemoveAttachment: ((String) -> Unit)? = null,
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
            // 悬浮页签占位（2026-10-30 真机实测回归修复）：
            // app 层的 GlassNavBar 是 NavHost 的**兄弟悬浮层**（叠在内容之上，见
            // AppScaffold.ShellContent），而 GlassScaffold 的 bottomBar 槽位不做任何
            // 让位 —— 不给这里加 padding，输入栏会整个压在玻璃页签底下。
            // 真机现场：竖屏下对话页 uiautomator 里 EditText 节点数为 0，横屏（Top 形态、
            // overlay=0dp）同一屏正常 —— 即「被页签遮住」而不是「没渲染」。
            // 旧仓同一处有这段 padding（`ChatScreen.kt:262` 的「输入框会整个压进页签区」），
            // W2U 原样搬入时漏了。
            //
            // 键盘弹出时页签已被 IME 完全盖住 ⇒ 不再垫 overlay，否则输入栏被
            // navigationBarsPadding/imePadding 顶上去之后又多出一份 84dp 的空隙。
            // `WindowInsets.ime` 是 @Composable @ReadOnlyComposable 取值，必须先取出
            // 实例再在 derivedStateOf 的普通 lambda 里读 getBottom（旧仓 Wave 51 F1）。
            val density = LocalDensity.current
            val imeInsets = WindowInsets.ime
            val imeVisible by remember(imeInsets, density) {
                derivedStateOf { imeInsets.getBottom(density) > 0 }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 顺序不可换：先让出悬浮页签，再让出系统导航区 / 键盘。
                    // 反过来会让 overlay 的 84dp 叠在 nav 区之上，输入栏离底边多出一截。
                    .padding(bottom = if (imeVisible) 0.dp else LocalBottomBarOverlay.current)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                ChatBottomArea(
                    state = state,
                    onInputChange = onInputChange,
                    onSend = onSend,
                    onStop = onStop,
                    onPickImage = onPickImage,
                    onRemoveAttachment = onRemoveAttachment,
                )
            }
        },
        snackbarHost = {
            ChatStatusColumn(
                state = state,
                onDismissError = onDismissError,
                onDecision = onResolveApproval,
            )
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

/**
 * 输入区上方的「状态 → 用量 → 上下文表 → 输入栏」四段。
 *
 * 抽出来只为守 G11（单方法 ≤120 行）：加一行重试提示就把 `ChatScreen` 顶到 122 行，
 * 而「超限就拆方法」是本仓的硬纪律（不是调上限、不是加注释）。
 *
 * 顺序有语义：状态在最上（最需要被看见），用量次之，上下文表贴着输入栏。
 */
@Composable
private fun ChatBottomArea(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: (() -> Unit)?,
    onRemoveAttachment: ((String) -> Unit)?,
) {
    val status = state.status
    if (status != null) {
        Text(
            text = stringResource(
                when (status) {
                    ChatStatus.RETRYING -> R.string.chat_status_retrying
                    ChatStatus.CANCELLED_EMPTY -> R.string.chat_status_cancelled
                }
            ),
            style = MaterialTheme.typography.labelSmall,
            color = LocalGlassColors.current.onGlassSubtle,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 20.dp),
        )
    }
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
        attachments = state.attachments,
        onPickImage = onPickImage,
        onRemoveAttachment = onRemoveAttachment,
    )
}
