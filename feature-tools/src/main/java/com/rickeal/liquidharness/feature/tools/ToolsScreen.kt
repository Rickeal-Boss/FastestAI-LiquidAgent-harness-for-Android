package com.rickeal.liquidharness.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.design.GlassCard
import com.rickeal.liquidharness.core.design.GlassChip
import com.rickeal.liquidharness.core.design.GlassIconButton
import com.rickeal.liquidharness.core.design.GlassScaffold
import com.rickeal.liquidharness.core.design.GlassSettingRow
import com.rickeal.liquidharness.core.design.GlassTopBar
import com.rickeal.liquidharness.core.design.LocalBottomBarOverlay
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.design.LocalGlassTokens
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolSourceKind

/**
 * 工具列表的一行（纯数据，供无状态界面渲染）。
 *
 * 字段逐项对应 [com.rickeal.liquidharness.core.model.ToolSpec]：这一屏是**注册表的镜像**，
 * 不新增任何派生判断（例如「危险」不折算成「需审批」—— 两个标志在闸门里是并列的，
 * 合并显示会让用户以为只判一条）。
 */
data class ToolRow(
    val name: String,
    val description: String,
    val category: String,
    val effect: ToolEffect,
    val requiresConfirmation: Boolean,
    val dangerous: Boolean,
    val source: ToolSourceKind,
    val enabled: Boolean,
)

/**
 * 把**真实注册表**拍平成 [ToolRow]（本屏没有写死的工具清单）。
 *
 * ⚠️ 必须在 IO 线程调用：`ToolRegistry.all()` 本身是纯内存读，但拿注册表这一步
 * （`AppContainer.toolRegistry()`）首次求值会 `mkdirs()` + `getSystemService()`，
 * 都是阻塞调用（见 `AppContainer.toolRegistry()` 的 KDoc）。调用点在
 * `ToolsViewModel.load` 的 `withContext(Dispatchers.IO)` 段内。
 *
 * `enabled` 读的是注册表的**启停标记**（`isEnabled`），不是「是否注册过」——
 * 被 `setEnabled(false)` 关掉的工具仍在 `all()` 里，但不会下发给模型。
 */
fun toolRows(registry: ToolRegistry): List<ToolRow> = registry.all().map { tool ->
    val spec = tool.spec
    ToolRow(
        name = spec.name,
        description = spec.description,
        category = spec.category,
        effect = spec.effect,
        requiresConfirmation = spec.requiresConfirmation,
        dangerous = spec.dangerous,
        source = spec.source,
        enabled = registry.isEnabled(spec.name),
    )
}

/**
 * Agent 工具子页（只读）。
 *
 * ## 与旧仓 `ToolsScreen`（311 行）的差别：本波只保留「看」
 *
 * 旧仓这一屏还有三块能力，本波**全部不搬**：
 *  - **逐工具开关**（`GlassSwitch` → `registry.setEnabled`）：改工具面语义，会让
 *    「模型看到的工具」与「技能 allowedTools 交集」两处判据同时变，超出「移植 UI」的范围；
 *  - **试跑面板**（参数 JSON + 直接 `invoke`）：那是绕过审批闸门的执行通道，不是展示面；
 *  - **沙箱入口卡**：沙箱有独立路由（[SandboxFilesRoute]），不在这里挂第二个人口。
 *
 * 无状态：工具清单与加载/错误全走参数，UI 测试不需要 ViewModel。
 */
@Composable
fun ToolsScreen(
    tools: List<ToolRow>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: String? = null,
) {
    val tokens = LocalGlassTokens.current
    val colors = LocalGlassColors.current

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.tools_title),
                subtitle = stringResource(
                    R.string.tools_subtitle,
                    tools.size,
                    tools.count { it.enabled },
                ),
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.tools_back),
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
                // 末尾行能滚出悬浮页签区（见 LocalBottomBarOverlay 的 KDoc）。
                bottom = 12.dp + LocalBottomBarOverlay.current,
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.cardGap),
        ) {
            if (error != null) {
                item {
                    NoticeCard(
                        text = stringResource(R.string.tools_error),
                        color = colors.danger,
                    )
                }
            }
            when {
                tools.isEmpty() && loading -> item {
                    NoticeCard(
                        text = stringResource(R.string.tools_loading),
                        color = colors.onGlassMuted,
                    )
                }
                tools.isEmpty() -> item {
                    NoticeCard(
                        text = stringResource(R.string.tools_empty),
                        color = colors.onGlassMuted,
                    )
                }
                else -> items(tools, key = { it.name }) { row -> ToolLine(row) }
            }
        }
    }
}

/**
 * 一个工具：名称 + 描述 + 行尾「副作用档位」与元信息。
 *
 * 复用 [GlassSettingRow] 而不是自绘卡片：本屏与设置页其它列表同一套行样式，
 * 用户不需要在同一个 App 里认两种列表。
 */
@Composable
private fun ToolLine(row: ToolRow) {
    val colors = LocalGlassColors.current
    GlassSettingRow(
        title = row.name,
        subtitle = row.description.ifBlank { null },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                GlassChip(
                    text = stringResource(effectLabel(row.effect)),
                    // WRITE 选中态：写档位是「会动数据」的那一侧，视觉上要能一眼扫出来。
                    selected = row.effect == ToolEffect.WRITE,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = toolMeta(row),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.dangerous) colors.danger else colors.onGlassSubtle,
                )
            }
        },
    )
}

/**
 * 元信息行：类别 · 审批 · 危险 · 停用 · 来源。
 *
 * 全部拼成**一行小字**而不是各占一枚 chip：五个标志各给一个 chip 会把行尾撑到换行，
 * 而这一屏的信息密度本来就是「一屏看全」。
 */
@Composable
private fun toolMeta(row: ToolRow): String {
    val parts = mutableListOf(row.category)
    parts += stringResource(
        if (row.requiresConfirmation) R.string.tools_approval else R.string.tools_no_approval
    )
    if (row.dangerous) parts += stringResource(R.string.tools_dangerous)
    if (!row.enabled) parts += stringResource(R.string.tools_disabled)
    parts += stringResource(sourceLabel(row.source))
    return parts.joinToString(" · ")
}

private fun effectLabel(effect: ToolEffect): Int = when (effect) {
    ToolEffect.READ -> R.string.tools_effect_read
    ToolEffect.WRITE -> R.string.tools_effect_write
}

private fun sourceLabel(source: ToolSourceKind): Int = when (source) {
    ToolSourceKind.BUILTIN -> R.string.tools_source_builtin
    ToolSourceKind.MCP -> R.string.tools_source_mcp
    ToolSourceKind.SUBAGENT -> R.string.tools_source_subagent
}

/** 空态 / 加载态 / 错误态共用的提示卡。 */
@Composable
private fun NoticeCard(text: String, color: Color) {
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
