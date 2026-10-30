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
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalDecision
import com.rickeal.liquidharness.core.design.GlassButton
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
    onDecision: (ToolApprovalDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.cardGap),
    ) {
        val approval = state.pendingApproval
        if (approval != null) {
            ApprovalCard(approval = approval, onDecision = onDecision)
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
 * 工具授权卡：**三个键**，一一对应内核的三种裁决。
 *
 * - 允许一次 ⇒ [ToolApprovalDecision.AllowOnce]：只放行这一次；
 * - 本会话允许相同调用 ⇒ [ToolApprovalDecision.AllowForSession]：内核按「同工具 + 同参数
 *   指纹」记进会话级缓存，后续相同调用不再弹卡；
 * - 拒绝 ⇒ [ToolApprovalDecision.Deny]：只拒**这一个工具**，run 继续 —— 模型会收到一条
 *   ok=false 的结果，有机会改用别的办法。它不是「停止整轮」，所以不叫「取消」。
 *
 * ⚠️ 本卡此前只有告知、没有按钮，KDoc 写的理由是「本仓内核尚无审批回传通道，
 * 按钮点了不会有任何效果，所以宁可不画」。那句话**已过期**：通道是
 * `ToolApprovalHandler`（按 run 传给 `AgentRunner.run`），裁决经
 * `ChatViewModel.resolveApproval` 用 `CompletableDeferred` 回传，三个键都有真实效果。
 */
@Composable
private fun ApprovalCard(
    approval: PendingApproval,
    onDecision: (ToolApprovalDecision) -> Unit,
) {
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
            Spacer(modifier = Modifier.height(tokens.gapMd))
            // 两行而不是一行三个：卡片内宽约 280dp，三枚胶囊按钮横排会把中文标签
            // 挤成省略号（GlassButton 是 maxLines=1）。允许 / 拒绝并排，会话级授权独占一行。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.gapSm),
            ) {
                GlassButton(
                    text = stringResource(R.string.chat_approval_allow_once),
                    onClick = { onDecision(ToolApprovalDecision.AllowOnce) },
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = stringResource(R.string.chat_approval_deny),
                    onClick = { onDecision(ToolApprovalDecision.Deny) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(tokens.gapSm))
            GlassButton(
                text = stringResource(R.string.chat_approval_allow_session),
                onClick = { onDecision(ToolApprovalDecision.AllowForSession) },
                modifier = Modifier.fillMaxWidth(),
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
