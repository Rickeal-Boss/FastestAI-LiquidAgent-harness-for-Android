package com.rickeal.liquidharness.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.rickeal.liquidharness.core.design.GlassChip
import com.rickeal.liquidharness.core.design.GlassIconButton
import com.rickeal.liquidharness.core.design.GlassMaterial
import com.rickeal.liquidharness.core.design.GlassScaffold
import com.rickeal.liquidharness.core.design.GlassSwitch
import com.rickeal.liquidharness.core.design.GlassTopBar
import com.rickeal.liquidharness.core.design.LiquidDialog
import com.rickeal.liquidharness.core.design.LocalBottomBarOverlay
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.design.LocalGlassTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MCP 服务器列表的一行。
 *
 * [status] 用**英文状态码**（`connected` / `error` / `cached` / `disabled`）传进来，
 * 由 UI 映射成中文 —— 状态是契约层的数据，不是文案（见 TextRef 的分工）。
 */
data class McpRow(
    val id: String,
    val alias: String,
    val url: String,
    val enabled: Boolean,
    val status: String,
    val toolCount: Int,
    val cachedAtMillis: Long = 0L,
)

/**
 * MCP 服务器管理子页。
 *
 * 骨架与 [EndpointListScreen] 一致：顶栏（返回 + 新增）→ 玻璃卡片列表。
 * 差异在于每行多了一个状态 chip 与"刷新工具列表"，因为 MCP 的失败模式是
 * **远端连不上但本地还有缓存** —— 用户必须一眼看出当前工具列表是新拉的还是旧的。
 *
 * 删除走二次确认（[LiquidDialog]，本仓的玻璃弹窗）：删掉一个服务器等于删掉它
 * 带来的全部工具，模型下一轮就会"忘记怎么用"，误触代价高。
 *
 * 无状态：数据与动作全走参数，仅"待删除项"是本屏的局部 UI 状态。
 */
@Composable
fun McpServerListScreen(
    servers: List<McpRow>,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onEdit: (id: String) -> Unit,
    onAdd: () -> Unit,
    onRefresh: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: String? = null,
) {
    val tokens = LocalGlassTokens.current
    val colors = LocalGlassColors.current
    var pendingDelete by remember { mutableStateOf<McpRow?>(null) }

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.settings_mcp_title),
                subtitle = stringResource(R.string.settings_mcp_subtitle),
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                        onClick = onBack,
                    )
                },
                actions = {
                    GlassIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.settings_mcp_add),
                        onClick = onAdd,
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
            if (error != null) {
                item { NoticeCard(text = error, color = colors.danger) }
            }
            when {
                servers.isEmpty() && loading -> item {
                    NoticeCard(
                        text = stringResource(R.string.settings_mcp_loading),
                        color = colors.onGlassMuted,
                    )
                }
                servers.isEmpty() -> item {
                    EmptyCard(
                        title = stringResource(R.string.settings_mcp_empty),
                        hint = stringResource(R.string.settings_mcp_empty_hint),
                    )
                }
                else -> items(servers, key = { it.id }) { row ->
                    McpCard(
                        row = row,
                        onToggle = onToggle,
                        onEdit = onEdit,
                        onRefresh = onRefresh,
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
            title = stringResource(R.string.settings_mcp_delete_title),
            subtitle = stringResource(R.string.settings_mcp_delete_message, target.alias),
            actions = { dismiss ->
                GlassButton(
                    text = stringResource(R.string.settings_action_cancel),
                    onClick = dismiss,
                    material = GlassMaterial.THIN,
                )
                GlassButton(
                    text = stringResource(R.string.settings_action_delete),
                    onClick = {
                        // 先关窗再回调：删除会让本行从列表消失，弹窗若还挂在旧行上，
                        // 出场动画期间会读到已被移除的数据。
                        pendingDelete = null
                        dismiss()
                        onDelete(target.id)
                    },
                )
            },
            content = {},
        )
    }
}

@Composable
private fun McpCard(
    row: McpRow,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onEdit: (id: String) -> Unit,
    onRefresh: (id: String) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val colors = LocalGlassColors.current
    val cachedText = remember(row.cachedAtMillis) { formatTimestamp(row.cachedAtMillis) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onEdit(row.id) },
        contentPadding = PaddingValues(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.alias,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onGlass,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(status = row.status)
                GlassSwitch(
                    checked = row.enabled,
                    onCheckedChange = { onToggle(row.id, it) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = middleEllipsis(row.url),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onGlassSubtle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_mcp_tool_count, row.toolCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onGlassMuted,
                )
                if (row.status == STATUS_CACHED && row.cachedAtMillis > 0L) {
                    Text(
                        text = stringResource(R.string.settings_mcp_cached_at, cachedText),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onGlassSubtle,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                GlassIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.settings_mcp_refresh),
                    onClick = { onRefresh(row.id) },
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

/**
 * 状态 chip：圆点承担颜色语义（绿=连通 / 红=失败 / 黄=缓存 / 灰=停用），
 * 文字仍用中性色 —— `GlassChip` 只有 `selected` 一个强调位，把文字染红会让
 * 整行在深色壁纸上读不清。
 */
@Composable
private fun StatusChip(status: String) {
    val colors = LocalGlassColors.current
    val dot = when (status) {
        STATUS_CONNECTED -> colors.success
        STATUS_ERROR -> colors.danger
        STATUS_CACHED -> colors.warning
        else -> colors.onGlassSubtle
    }
    GlassChip(
        text = stringResource(
            when (status) {
                STATUS_CONNECTED -> R.string.settings_mcp_status_connected
                STATUS_ERROR -> R.string.settings_mcp_status_error
                STATUS_CACHED -> R.string.settings_mcp_status_cached
                else -> R.string.settings_mcp_status_disabled
            },
        ),
        icon = {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color = dot, shape = CircleShape),
            )
        },
    )
}

@Composable
private fun EmptyCard(title: String, hint: String) {
    val colors = LocalGlassColors.current
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onGlass,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onGlassSubtle,
            )
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

/**
 * URL 中段省略：MCP 地址的区分度常在**尾部路径**（`/sse`、`/mcp`），
 * 纯尾部省略会把最有用的那一截切掉。
 */
private fun middleEllipsis(text: String, maxLength: Int = 44): String {
    if (text.length <= maxLength) return text
    val head = maxLength / 2
    val tail = maxLength - head - 1
    return text.take(head) + "\u2026" + text.takeLast(tail)
}

/** 缓存时间：默认 locale 的短格式，够用且不需要额外的格式化依赖。 */
private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

private const val STATUS_CONNECTED = "connected"
private const val STATUS_ERROR = "error"
private const val STATUS_CACHED = "cached"
