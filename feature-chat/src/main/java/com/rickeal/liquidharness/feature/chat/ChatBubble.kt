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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassAttachmentKind
import com.rickeal.liquidharness.core.design.GlassBubble
import com.rickeal.liquidharness.core.design.GlassBubbleAttachment
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.model.AttachmentKind
import com.rickeal.liquidharness.core.model.JsonCheckResult
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.checkJsonFormat

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
    renderMarkdown: Boolean,
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
            // 气泡存储的是装饰后文本（send 存装饰版 ⇒ wire=存储，跨轮请求体字节稳定），
            // 显示时剥掉加载引导还原用户原文（ChatSkillInput.displayText）。
            text = ChatSkillInput.displayText(bubble.text),
            isUser = true,
            modifier = modifier,
            // 附件缩略图。uri 用导入时落到 cacheDir 的本地路径 —— `GlassImageThumb`
            // 只认路径/uri，不认 base64；缓存被清时退化成占位图（请求体仍用内存里的字节）。
            attachments = bubble.attachments.map { attachment ->
                GlassBubbleAttachment(
                    id = attachment.id,
                    label = attachment.name,
                    kind = when (attachment.kind) {
                        AttachmentKind.IMAGE -> GlassAttachmentKind.IMAGE
                        AttachmentKind.AUDIO -> GlassAttachmentKind.AUDIO
                        AttachmentKind.TEXT -> GlassAttachmentKind.TEXT
                    },
                    uri = attachment.localPath,
                )
            },
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
                        renderMarkdown = renderMarkdown,
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
    renderMarkdown: Boolean,
) {
    val context = LocalContext.current
    // W8：JSON 检查结果只存本条气泡的局部 UI 状态（不进 ChatViewModel：余 13 行且 G23 钉调用点）。
    var jsonCheck by remember(bubble.id, bubble.text) { mutableStateOf<JsonCheckResult?>(null) }
    Column {
    GlassBubble(
        text = bubble.text,
        // W8：定稿气泡走 Markdown；流式分支由 GlassBubble 内部保持纯文本。
        annotatedText = if (bubble.text.isNotBlank()) bubbleText(bubble.text, renderMarkdown) else null,
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
            // W8：仍只有一枚「复制」（正文不经展示层改写，第二枚复制键会复制出相同字符串 = 装饰）；
            // 新增的是**检查 JSON**（语义不同、产出不同结果，不是装饰）。
            if (bubble.text.isNotBlank()) {
                BubbleCopyAction(
                    label = stringResource(R.string.chat_action_copy),
                    icon = Icons.Filled.ContentCopy,
                    onClick = { copyToClipboard(context, bubble.text) },
                )
                // 需求 ③ A 路：只读校验返回文本是否合法 JSON。不做 agent 工具（触 G18），
                // 不做全局自动检查（LazyColumn 一屏十几条代价高且无法只查一条）。
                BubbleCopyAction(
                    label = stringResource(R.string.chat_action_check_json),
                    icon = Icons.Filled.Check,
                    onClick = { jsonCheck = checkJsonFormat(bubble.text) },
                )
            }
        },
    )
        // 🔴 只给行列号，不回显原文片段（上屏风险 > 落盘风险）。
        jsonCheck?.let { result ->
            Text(
                text = when (result) {
                    JsonCheckResult.Valid -> stringResource(R.string.chat_json_ok)
                    is JsonCheckResult.Invalid ->
                        stringResource(R.string.chat_json_invalid, result.line, result.column)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (result is JsonCheckResult.Valid) {
                    LocalGlassColors.current.accent
                } else {
                    LocalGlassColors.current.danger
                },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
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
