package com.rickeal.liquidharness.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassButton
import com.rickeal.liquidharness.core.design.GlassCard
import com.rickeal.liquidharness.core.design.GlassIconButton
import com.rickeal.liquidharness.core.design.GlassMaterial
import com.rickeal.liquidharness.core.design.GlassScaffold
import com.rickeal.liquidharness.core.design.GlassTopBar
import com.rickeal.liquidharness.core.design.LiquidDialog
import com.rickeal.liquidharness.core.design.LocalBottomBarOverlay
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.design.LocalGlassTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一条长期记忆。
 *
 * [title] 是**键**（删除 / 编辑都按它定位），[id] 只用于列表项身份。
 */
data class MemoryRow(
    val id: String,
    val title: String,
    val content: String,
    val updatedAtMillis: Long,
)

/**
 * 长期记忆子页。
 *
 * 列表默认三行截断 —— 记忆条目动辄几百字，全展开会把"有哪些记忆"这件事淹没在
 * 正文里。点卡片就地展开，展开状态只活在本屏（`mutableStateMapOf`，key = id），
 * 不回写数据层：它是纯粹的浏览态，不该变成需要持久化的用户偏好。
 *
 * 无状态：条目与动作全走参数，仅"展开集合"和"待删除项"是局部 UI 状态。
 */
@Composable
fun MemoryScreen(
    entries: List<MemoryRow>,
    onDelete: (title: String) -> Unit,
    onEdit: (title: String, content: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val tokens = LocalGlassTokens.current
    val colors = LocalGlassColors.current
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var pendingDelete by remember { mutableStateOf<MemoryRow?>(null) }

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.settings_memory_title),
                subtitle = stringResource(R.string.settings_memory_subtitle),
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { _ ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 12.dp,
                bottom = 12.dp + LocalBottomBarOverlay.current,
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.cardGap),
        ) {
            when {
                entries.isEmpty() && loading -> item {
                    NoticeCard(
                        text = stringResource(R.string.settings_memory_loading),
                        color = colors.onGlassMuted,
                    )
                }
                entries.isEmpty() -> item {
                    NoticeCard(
                        text = stringResource(R.string.settings_memory_empty),
                        color = colors.onGlassMuted,
                    )
                }
                else -> items(entries, key = { it.id }) { row ->
                    MemoryCard(
                        row = row,
                        expanded = expanded[row.id] == true,
                        onToggle = { expanded[row.id] = expanded[row.id] != true },
                        onEdit = { onEdit(row.title, row.content) },
                        onRequestDelete = { pendingDelete = row },
                    )
                }
            }
        }
    }

    val target = pendingDelete
    if (target != null) {
        LiquidDialog(
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.settings_memory_delete_title),
            subtitle = stringResource(R.string.settings_memory_delete_message, target.title),
            actions = { dismiss ->
                GlassButton(
                    text = stringResource(R.string.settings_action_cancel),
                    onClick = dismiss,
                    material = GlassMaterial.THIN,
                )
                GlassButton(
                    text = stringResource(R.string.settings_action_delete),
                    onClick = {
                        pendingDelete = null
                        dismiss()
                        onDelete(target.title)
                    },
                )
            },
            content = {},
        )
    }
}

/**
 * 单条记忆：标题 + 正文（收起三行 / 展开全文）+ 更新时间 + 编辑 / 删除。
 *
 * 整卡可点 = 展开切换；编辑与删除是独立点击目标，先于整卡命中（与
 * [EndpointListScreen] 的行内动作同一套命中顺序）。
 */
@Composable
private fun MemoryCard(
    row: MemoryRow,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val colors = LocalGlassColors.current
    val updatedText = remember(row.updatedAtMillis) { formatTimestamp(row.updatedAtMillis) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        contentPadding = PaddingValues(14.dp),
    ) {
        Column {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onGlass,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = row.content,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onGlassMuted,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_memory_updated, updatedText),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onGlassSubtle,
                )
                Spacer(Modifier.weight(1f))
                GlassIconButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.settings_memory_edit),
                    onClick = onEdit,
                    size = 36.dp,
                    iconSize = 16.dp,
                    contentPadding = PaddingValues(10.dp),
                )
                GlassIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.settings_action_delete),
                    onClick = onRequestDelete,
                    size = 36.dp,
                    iconSize = 16.dp,
                    contentPadding = PaddingValues(10.dp),
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String, color: Color) {
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
