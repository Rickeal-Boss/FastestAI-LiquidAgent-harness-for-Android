package com.rickeal.liquidharness.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassCard
import com.rickeal.liquidharness.core.design.GlassIconButton
import com.rickeal.liquidharness.core.design.GlassMaterial
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.design.LocalGlassTokens
import com.rickeal.liquidharness.core.model.TerminationReason

/**
 * 宿主级状态信息区（自上而下）：工具授权 > 错误 > 终止原因小字。
 *
 * 都是**非模态**卡片：run 不被弹窗打断，但「现在发生了什么 / 需要你做什么」永远可见。
 * 卡片间距走 `tokens.cardGap`（列表节奏），不用 `gapSm` —— 后者在别处大量用于卡内行距。
 *
 * 刻意**没有**重试按钮：旧仓的重试要先把历史剪到最后一条 user 消息再重发
 * （避免历史里留下两条连续 USER 消息），这条历史改写能力本仓 ViewModel 还没有。
 * 留一个点了没反应的键比没有这个键更糟。
 */
@Composable
internal fun ChatStatusColumn(
    state: ChatUiState,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.cardGap),
    ) {
        val approval = state.pendingApproval
        if (approval != null) {
            ApprovalCard(approval = approval)
        }
        val error = state.errorMessage
        if (error != null) {
            ErrorCard(message = error, onDismiss = onDismissError)
        }
        val hint = terminationHintOf(state.terminatedBy)
        if (hint != null) {
            Text(
                text = stringResource(hint),
                style = MaterialTheme.typography.labelSmall,
                color = LocalGlassColors.current.onGlassSubtle,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

/**
 * 工具授权提示卡。
 *
 * ⚠️ **只有「告知」，没有「授权 / 拒绝」两个键**：本仓内核尚无审批回传通道
 * （`AgentEvent.ApprovalRequested` 已定义，但没有把裁决送回 `TurnLoop` 的入口，
 * 见 `ChatUiState.PendingApproval` 的 KDoc）。按钮点了不会有任何效果，
 * 所以宁可不画 —— 等 `ToolApprovalGate` 落地时，这里补两个 `GlassButton` 即可。
 */
@Composable
private fun ApprovalCard(approval: PendingApproval) {
    val colors = LocalGlassColors.current
    val tokens = LocalGlassTokens.current
    GlassCard(
        material = GlassMaterial.THICK,
        cornerRadius = tokens.radiusMd,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier.padding(horizontal = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.chat_approval_title, approval.toolName),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onGlass,
            )
            Spacer(modifier = Modifier.height(tokens.gapSm))
            // 参数是模型给的 JSON 原文：等宽字体对齐结构，扫一眼就能看出要写哪个路径。
            // maxLines 截断 + 不做内嵌滚动 —— 浮层区内嵌 scroll 会抢走外层手势。
            Text(
                text = approval.arguments,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = colors.onGlassSubtle,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(tokens.gapSm))
            Text(
                text = stringResource(R.string.chat_approval_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onGlassMuted,
            )
        }
    }
}

/**
 * 错误卡。
 *
 * 不设 `maxLines`：失败原因可能很长（含原始异常），截断成一行会让用户彻底看不懂。
 * `NO_ENDPOINT` 是内核侧的 ASCII 错误码，上屏前换成完整句子（守卫 G16 的同一纪律）。
 */
@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    val colors = LocalGlassColors.current
    val tokens = LocalGlassTokens.current
    val text = if (message == NO_ENDPOINT) {
        stringResource(R.string.chat_error_no_endpoint)
    } else {
        stringResource(R.string.chat_error_generic, message)
    }
    GlassCard(
        material = GlassMaterial.THICK,
        cornerRadius = tokens.radiusMd,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier.padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
                modifier = Modifier.weight(1f),
            )
            // 触摸目标由组件内的 size 撑满 48dp；图标保持 16dp。
            GlassIconButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.chat_action_close),
                onClick = onDismiss,
                contentColor = colors.onGlassSubtle,
                iconSize = 16.dp,
            )
        }
    }
}

/**
 * 终止原因 → 一行小字（旧仓 Wave 31 流2 的同一口径）。
 *
 * 只对「用户需要知道、且当前有表达力」的三档渲染；`ModelStopped` / `Interrupted`
 * 与 null 返回 null = **不渲染任何东西**（正常结束路径零 UI 变化）。
 * `when` 带 `else`：枚举将来加值时不崩、不渲染。
 */
private fun terminationHintOf(reason: TerminationReason?): Int? = when (reason) {
    TerminationReason.BreakerTripped -> R.string.chat_termination_breaker
    TerminationReason.GateRejected -> R.string.chat_termination_gate
    TerminationReason.MaxRounds -> R.string.chat_termination_max_rounds
    else -> null
}
