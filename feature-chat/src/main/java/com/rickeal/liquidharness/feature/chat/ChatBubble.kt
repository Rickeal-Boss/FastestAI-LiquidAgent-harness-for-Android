package com.rickeal.liquidharness.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassBubble
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.model.Role

/**
 * 一条气泡。
 *
 * 模型气泡上方先铺它自己产出的工具轨迹（[ChatBubble.traces]），再铺正文 ——
 * 旧仓 `MessageRow` 的同一顺序：先看「它做了什么」，再看「它说了什么」。
 *
 * 用户气泡与模型气泡都走 [GlassBubble]：材质、强调色、附件条、流式光标都由它统一
 * 提供，本文件只负责「哪一种角色该带什么附加物」。
 */
@Composable
internal fun ChatBubbleRow(
    bubble: ChatBubble,
    thinkingExpanded: Boolean,
    onToggleThinking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (bubble.role) {
        Role.SYSTEM -> {
            // 系统提示词只读展示，弱化到接近背景：它不是对话内容。
            Text(
                text = bubble.text,
                style = MaterialTheme.typography.labelSmall,
                color = LocalGlassColors.current.onGlassSubtle,
                textAlign = TextAlign.Center,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }

        Role.USER -> GlassBubble(
            text = bubble.text,
            isUser = true,
            modifier = modifier,
        )

        Role.MODEL, Role.TOOL -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (trace in bubble.traces) {
                    ChatToolCard(trace = trace)
                }
                if (bubble.text.isNotBlank() || bubble.reasoning.isNotBlank()) {
                    ModelBubble(
                        bubble = bubble,
                        thinkingExpanded = thinkingExpanded,
                        onToggleThinking = onToggleThinking,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelBubble(
    bubble: ChatBubble,
    thinkingExpanded: Boolean,
    onToggleThinking: () -> Unit,
) {
    val context = LocalContext.current
    GlassBubble(
        text = bubble.text,
        isUser = false,
        thinking = bubble.reasoning.ifBlank { null },
        // 文案由使用方传入：core-design 里不留任何用户可见文案（G16 与 i18n 同一条纪律）。
        thinkingLabel = stringResource(R.string.chat_thinking),
        thinkingExpanded = thinkingExpanded,
        onToggleThinking = onToggleThinking,
        isStreaming = bubble.streaming,
        // 操作行贴在气泡**外**、不进气泡玻璃：LazyColumn 一屏十几条，
        // 每条再叠一层玻璃控件，模糊成本 ×N。
        actions = {
            // 只有一枚「复制」：旧仓的第二枚「复制为 Markdown」是为了拿**未经
            // `<think>` 拆分与 U+FFFD 清洗**的原文，本仓正文不经展示层改写，
            // 两枚键会复制出完全相同的字符串 —— 那就成了装饰。
            if (bubble.text.isNotBlank()) {
                BubbleCopyAction(
                    label = stringResource(R.string.chat_action_copy),
                    icon = Icons.Filled.ContentCopy,
                    onClick = { copyToClipboard(context, bubble.text) },
                )
            }
        },
    )
}

/**
 * 气泡下方的轻量文字操作键（复制类）。
 *
 * 刻意不做玻璃控件：视觉层级明显低于气泡本体 —— 操作是附属能力，不是内容。
 * 触控热区 ≥48dp（本仓最小触控目标纪律）：`heightIn(min = 48.dp)` 挂在可点击的
 * Box 上，**可点击区域就是 48dp 热区**，不是视觉大小。
 */
@Composable
private fun BubbleCopyAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = LocalGlassColors.current
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            // padding 在 clickable 之后：热区含水平内边距，点字边也能命中；
            // 高度方向由 heightIn(min = 48.dp) 保证，不再叠 vertical padding。
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onGlassSubtle,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onGlassSubtle,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
    }
}

/**
 * 复制到剪贴板。
 *
 * 用平台 `ClipboardManager` 而不是 Compose 的 `LocalClipboardManager`：
 * 后者在新版 Compose 里已改挂起式 `LocalClipboard`，直接用平台 API 少一层版本耦合。
 */
private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("", text))
}
