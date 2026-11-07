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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalDecision
import com.rickeal.liquidharness.core.design.GlassButton
import com.rickeal.liquidharness.core.design.GlassMaterial
import com.rickeal.liquidharness.core.design.GlassScaffold
import com.rickeal.liquidharness.core.design.LiquidDialog
import com.rickeal.liquidharness.core.design.LocalBottomBarOverlay
import com.rickeal.liquidharness.core.design.LocalGlassColors
import kotlinx.coroutines.delay

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
 * @param onCompressContext 手动压缩（W6）。null = 宿主不提供该能力，顶栏不渲染溢出菜单。
 *   本屏只负责「二次确认」这一步：确认对话框是压缩的**唯一**入口，因为压缩有不可忽视的
 *   代价（前缀缓存整段重建），把它做成一步到位的按钮等于把代价藏起来。
 * @param onUndoCompress 撤销压缩（清空切点）。null = 只读展示，不渲染撤销键。
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
    onCompressContext: (() -> Unit)? = null,
    onUndoCompress: (() -> Unit)? = null,
    /** W8：定稿气泡 Markdown 渲染开关（由 AppNavHost 从 DataStore 读入，不进 ChatUiState）。 */
    renderMarkdown: Boolean = false,
    /** W8（NEW-D）：端点未声明图片输入 ⇒ 输入栏保留禁用态入口（点击无操作 + 无障碍原因）。 */
    imageInputLocked: Boolean = false,
) {
    // 确认对话框的开关是纯 UI 状态（不进 ChatUiState）：它不影响任何业务判定，
    // 放进状态机只会让归约器多一个与事件无关的字段。
    var confirmCompress by remember { mutableStateOf(false) }

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
                // 入口非空才渲染菜单项；点击只负责「开确认框」，真正的压缩在确认之后。
                onCompressContext = onCompressContext?.let { { confirmCompress = true } },
                compressEnabled = !state.isStreaming,
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
                    renderMarkdown = renderMarkdown,
                    imageInputLocked = imageInputLocked,
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
            compression = state.compression,
            onUndoCompress = onUndoCompress,
            renderMarkdown = renderMarkdown,
        )
    }

    if (confirmCompress && onCompressContext != null) {
        CompressConfirmDialog(
            onDismiss = { confirmCompress = false },
            onConfirm = {
                confirmCompress = false
                onCompressContext()
            },
        )
    }
}

/**
 * 手动压缩的二次确认框。
 *
 * ## 为什么必须有这一步，且必须写代价
 *
 * 压缩**不是**「免费的省钱操作」：它把 history 的最老一段整体拿掉 ⇒ 下一次请求的提示
 * 前缀从第一个字节起就变了 ⇒ provider 侧缓存整段重建、`cached_tokens` 归 0、账单更贵。
 * 本仓明令禁止「只说收益不说代价」的界面（`ContextCompressor` 的类头同一纪律：账单上
 * 只表现为「贵了」是最难发现的回归），所以确认文案里写死这句话。
 *
 * 第二个代价是**不可逆的信息损失**（对模型而言）：被移出 history 的气泡用户仍能展开
 * 回看，但模型再也看不到它们。撤销能把历史还原，**已经发生过的那次请求的费用不会退**。
 */
@Composable
private fun CompressConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    LiquidDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.chat_compress_title),
        subtitle = stringResource(R.string.chat_compress_warning),
        actions = { dismiss ->
            GlassButton(
                text = stringResource(R.string.chat_compress_cancel),
                onClick = dismiss,
                material = GlassMaterial.THIN,
            )
            GlassButton(
                text = stringResource(R.string.chat_compress_confirm),
                onClick = {
                    dismiss()
                    onConfirm()
                },
            )
        },
        content = {
            Text(
                text = stringResource(R.string.chat_compress_body),
                style = MaterialTheme.typography.bodySmall,
                color = LocalGlassColors.current.onGlassSubtle,
            )
        },
    )
}

/**
 * 输入区上方那一行状态提示。三种状态**共用同一个渲染槽**（不新开 UI 通道）：
 * 两行状态叠在一起时，用户不知道该看哪一行，而且槽位高度会跳。
 */
@Composable
private fun StatusLine(status: ChatStatus) {
    Text(
        text = when (status) {
            ChatStatus.RETRYING -> stringResource(R.string.chat_status_retrying)
            ChatStatus.WAITING_FIRST_BYTE -> waitingStatusText()
            ChatStatus.CANCELLED_EMPTY -> stringResource(R.string.chat_status_cancelled)
        },
        style = MaterialTheme.typography.labelSmall,
        color = LocalGlassColors.current.onGlassSubtle,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 20.dp),
    )
}

/**
 * 「正在等待模型响应… Ns」。秒数**在 UI 侧**每秒跳。
 *
 * 为什么不进 `ChatUiState`：那会给归约器加上时间维度（每个事件都要先算一次秒），
 * 而它现在是纯函数、事件序列可以任意重放。等待秒数是纯展示，随组合存在与消失。
 *
 * 状态离开等待态 ⇒ 本 composable 离开组合 ⇒ 协程被取消、`remember` 归零，
 * 下一轮等待从 0 重新数（不会把上一轮的秒数带过来）。
 */
@Composable
private fun waitingStatusText(): String {
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(WAITING_TICK_MILLIS)
            elapsedMillis += WAITING_TICK_MILLIS
        }
    }
    return stringResource(R.string.chat_status_waiting, waitingSeconds(elapsedMillis))
}

/** 状态行每秒一跳：更快的跳动只是耗电，更慢会让「卡住」看起来像「没在计时」。 */
private const val WAITING_TICK_MILLIS = 1_000L

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
    renderMarkdown: Boolean,
    imageInputLocked: Boolean,
) {
    val status = state.status
    if (status != null) {
        StatusLine(status)
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
        imageInputLocked = imageInputLocked,
    )
}
