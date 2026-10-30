package com.rickeal.liquidharness.feature.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassCard
import com.rickeal.liquidharness.core.design.GlassMaterial
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.design.LocalGlassTokens

/**
 * 工具过程折叠组。
 *
 * 分组规则（[toGroups]）：RUNNING 与 FAILED **永不折叠**（当前活动与错误必须醒目独立）；
 * 连续 ≥ [MIN_GROUP_SIZE] 条已完结（OK / SKIPPED）折叠为一组。
 * 组 id = 首成员 trace.id（稳定：后续成员追加不换 id，展开状态不丢）。
 *
 * 纯 UI 层：`toolTraces` 仍是唯一数据源，内核零改动。
 */
@Immutable
data class ToolTraceGroup(
    val id: String,
    val traces: List<ToolTrace>,
    val okCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
)

/** 连续完结轨迹折叠成组的阈值。 */
private const val MIN_GROUP_SIZE = 3

/** 渲染序列元素：折叠组或独立轨迹。 */
@Immutable
internal sealed interface ToolTraceOrGroup {
    data class Group(val group: ToolTraceGroup) : ToolTraceOrGroup
    data class Single(val trace: ToolTrace) : ToolTraceOrGroup
}

/**
 * 把流式区轨迹折叠成渲染序列：可折叠组与必须独立展示的轨迹交错排列。
 * 组内顺序保持原序；RUNNING / FAILED 截断分组（它们前后的完结段各自成组）。
 *
 * 纯函数（无 Compose、无资源读取），组标题的本地化在 [ChatTraceGroupCard] 里做 ——
 * 否则这个函数就得是 `@Composable`，单测也就没了。
 */
internal fun List<ToolTrace>.toGroups(): List<ToolTraceOrGroup> {
    val out = ArrayList<ToolTraceOrGroup>()
    var buffer = ArrayList<ToolTrace>()

    fun flush() {
        if (buffer.isEmpty()) return
        if (buffer.size >= MIN_GROUP_SIZE) {
            out += ToolTraceOrGroup.Group(
                ToolTraceGroup(
                    id = buffer.first().id,
                    traces = buffer.toList(),
                    okCount = buffer.count { it.status == ToolTraceStatus.OK },
                    skippedCount = buffer.count { it.status == ToolTraceStatus.SKIPPED },
                    failedCount = buffer.count { it.status == ToolTraceStatus.FAILED },
                ),
            )
        } else {
            for (trace in buffer) out += ToolTraceOrGroup.Single(trace)
        }
        buffer = ArrayList()
    }

    for (trace in this) {
        val foldable = trace.status == ToolTraceStatus.OK || trace.status == ToolTraceStatus.SKIPPED
        if (foldable) {
            buffer += trace
        } else {
            flush()
            out += ToolTraceOrGroup.Single(trace)
        }
    }
    flush()
    return out
}

/** 流式区轨迹渲染序列（remember 包裹：toolTraces 引用不变时跳过分组计算）。 */
@Composable
internal fun rememberTraceRenderItems(toolTraces: List<ToolTrace>): List<ToolTraceOrGroup> =
    remember(toolTraces) { toolTraces.toGroups() }

/**
 * 工具名 → 过程动词。未命中回退原工具名（新工具先以标识上屏，比不显示好）。
 *
 * 文案全部走 `strings.xml`（守卫 G16）：`feature-chat` 的 main 源集不允许中文字面量。
 */
@Composable
private fun toolVerb(name: String): String = when (name) {
    "file_read" -> stringResource(R.string.chat_tool_file_read)
    "file_write" -> stringResource(R.string.chat_tool_file_write)
    "file_list" -> stringResource(R.string.chat_tool_file_list)
    "calculator" -> stringResource(R.string.chat_tool_calculator)
    "datetime" -> stringResource(R.string.chat_tool_datetime)
    "clipboard" -> stringResource(R.string.chat_tool_clipboard)
    "memory_read" -> stringResource(R.string.chat_tool_memory_read)
    "memory_write" -> stringResource(R.string.chat_tool_memory_write)
    "memory_search" -> stringResource(R.string.chat_tool_memory_search)
    "memory_delete" -> stringResource(R.string.chat_tool_memory_delete)
    "ask_actor" -> stringResource(R.string.chat_tool_ask_actor)
    else -> name
}

/**
 * 折叠组卡片：头行 = 图标 + 任务动词 + 完成计数 + 展开/收起符号；展开体逐条复用
 * [ChatToolCard]。`animateContentSize` 让展开/收起平滑（纯尺寸动画，无时钟开销）。
 */
@Composable
internal fun ChatTraceGroupCard(
    group: ToolTraceGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    val title = toolVerb(group.traces.firstOrNull()?.name.orEmpty())
    val skipped = if (group.skippedCount > 0) {
        stringResource(R.string.chat_trace_skipped, group.skippedCount)
    } else {
        null
    }
    val failed = if (group.failedCount > 0) {
        stringResource(R.string.chat_trace_failed, group.failedCount)
    } else {
        null
    }
    val counts = buildString {
        append(group.okCount)
        append('/')
        append(group.traces.size)
        if (skipped != null) append(" · ").append(skipped)
        if (failed != null) append(" · ").append(failed)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onToggle),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = null,
                tint = colors.onGlassMuted,
                modifier = Modifier
                    .padding(2.dp)
                    .size(14.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onGlassMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = counts,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onGlassSubtle,
            )
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onGlassSubtle,
            )
        }
        if (expanded) {
            for (trace in group.traces) {
                ChatToolCard(trace = trace)
            }
        }
    }
}

/**
 * 单条工具卡：名称 + 状态 + 耗时 + 参数（≤3 行）+ 输出（≤6 行）。
 *
 * 材质取 THIN 而不是 REGULAR：本卡是 LazyColumn 的 item，模糊成本会 ×N，
 * 下限达标即止（判据见 `GlassMaterial` 文件头 KDoc）。
 */
@Composable
internal fun ChatToolCard(trace: ToolTrace, modifier: Modifier = Modifier) {
    val colors = LocalGlassColors.current
    val tokens = LocalGlassTokens.current
    val running = trace.status == ToolTraceStatus.RUNNING
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        material = GlassMaterial.THIN,
        cornerRadius = tokens.radiusMd,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    tint = colors.onGlassMuted,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = trace.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onGlass,
                    modifier = Modifier
                        .padding(start = 7.dp)
                        .weight(1f),
                )
                if (running) {
                    Text(
                        text = stringResource(R.string.chat_tool_running),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onGlassSubtle,
                    )
                } else {
                    Icon(
                        imageVector = if (trace.status == ToolTraceStatus.FAILED) {
                            Icons.Filled.ErrorOutline
                        } else {
                            Icons.Filled.Check
                        },
                        contentDescription = null,
                        tint = if (trace.status == ToolTraceStatus.FAILED) colors.danger else colors.success,
                        modifier = Modifier.size(14.dp),
                    )
                    if (trace.elapsedMillis > 0L) {
                        Text(
                            text = trace.elapsedMillis.toString() + "ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onGlassSubtle,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
            val arguments = trace.arguments
            if (arguments.isNotBlank() && arguments != "{}") {
                Text(
                    text = arguments,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onGlassSubtle,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            val output = trace.result
            if (!output.isNullOrBlank()) {
                Text(
                    text = output,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onGlassMuted,
                    maxLines = 6,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
