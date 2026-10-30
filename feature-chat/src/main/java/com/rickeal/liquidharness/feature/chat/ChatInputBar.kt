package com.rickeal.liquidharness.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassBottomBar
import com.rickeal.liquidharness.core.design.GlassIconButton
import com.rickeal.liquidharness.core.design.GlassMaterial
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.design.LocalGlassTokens
import com.rickeal.liquidharness.core.design.liquidGlass

/**
 * 底部输入栏。
 *
 * 与旧仓同一形态：一枚浮玻璃底栏，里面是「输入框 + 发送/停止」。输入框自己再叠一层
 * `ULTRA_THIN` 玻璃（底栏是 THICK），两级材质让输入框在栏里「陷下去」一格。
 *
 * 附件入口（旧仓的「+」）**刻意没有搬**：本仓 `ChatMessage` 没有 attachments 字段、
 * `ChatUiState` 也没有附件通道，画出来就是一个点完什么都没发生的键（W6 接线）。
 * 选择器本体（`ChatAttachmentPicker.kt`）已就位，届时把入口加回来即可。
 *
 * @param canSend 由 [ChatUiState.canSend] 传入（非流式 + 输入非空 + 已配置端点）。
 *   不能只看 `draft.isNotBlank()`：没端点时按下发送什么都不会发生。
 */
@Composable
internal fun ChatInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    canSend: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    val tokens = LocalGlassTokens.current
    GlassBottomBar(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .padding(horizontal = 6.dp)
                .liquidGlass(
                    material = GlassMaterial.ULTRA_THIN,
                    cornerRadius = tokens.radiusMd,
                    // 输入框聚焦后逐帧重绘，色散（7 次采样）在这里最贵 —— 显式关掉，
                    // 不依赖默认值（默认值将来翻回去时这里不会跟着打开）。
                    dispersion = false,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (draft.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_input_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onGlassSubtle,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onGlass),
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { innerTextField -> innerTextField() },
            )
        }
        SendButton(
            isStreaming = isStreaming,
            enabled = canSend,
            onSend = onSend,
            onStop = onStop,
        )
    }
}

/**
 * 发送 / 停止二合一。
 *
 * `containerColor != null` 会自动关掉玻璃（强调色不透明，叠了看不见），但仍保留跟手形变
 * —— 这正是「液态」的部分。流式中恒可点（停止必须随时可用）。
 */
@Composable
private fun SendButton(
    isStreaming: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = LocalGlassColors.current
    val active = if (isStreaming) true else enabled
    GlassIconButton(
        icon = if (isStreaming) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
        contentDescription = stringResource(
            if (isStreaming) R.string.chat_action_stop else R.string.chat_action_send,
        ),
        onClick = { if (isStreaming) onStop() else onSend() },
        enabled = active,
        containerColor = if (active) colors.accent else colors.accentMuted,
        contentColor = colors.onAccent,
    )
}
