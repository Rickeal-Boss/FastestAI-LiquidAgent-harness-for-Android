package com.rickeal.liquidharness.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassCard
import com.rickeal.liquidharness.core.design.GlassScaffold
import com.rickeal.liquidharness.core.design.GlassSettingRow
import com.rickeal.liquidharness.core.design.GlassTopBar
import com.rickeal.liquidharness.core.design.LocalBottomBarOverlay
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.design.LocalGlassTokens

/**
 * 设置首页（一级目的地，竖屏是底部页签 / 横屏是顶部横条，因此**没有返回钮**）。
 *
 * ## 只有三组，且每组都对应真实能力
 *
 * 旧仓设置页有外观 / 推理参数 / 记忆 / 存储 / 工具 / 通知等十余组，本仓 W1/W2
 * 只有「端点」「关于」「诊断」三组 —— 页面结构沿用旧仓的玻璃分组与行样式
 * （`GlassCard` 包 `GroupTitle` + `GlassSettingRow`），但**不搬做不到的设置项**：
 * 摆一个点了没反应的开关，比没有这个开关更糟。
 *
 *  - **端点**：跳「端点管理」子页，副标题显示当前活跃端点（真实读自 state）。
 *  - **关于**：应用名 + 版本（版本由 `:app` 从 PackageManager 读出后传入 —— 库模块的
 *    `BuildConfig.VERSION_NAME` 是库自己的版本，与安装包无关）。
 *  - **诊断**：日志行**只读展示**（`enabled = false`）。本仓还没有诊断页，这里如实
 *    说明"日志仅保存在本机"，不摆一个点不动的「打开诊断」入口。
 *
 * 无状态：所有数据从 [SettingsUiState] / 参数来，动作走回调 —— UI 测试不需要 ViewModel。
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    appVersion: String,
    onManageEndpoints: () -> Unit,
    modifier: Modifier = Modifier,
    onManageSkills: (() -> Unit)? = null,
    onManageMcp: (() -> Unit)? = null,
    onManageMemory: (() -> Unit)? = null,
    onManageTools: (() -> Unit)? = null,
    onManageSandbox: (() -> Unit)? = null,
    onSetContextWindow: ((Int) -> Unit)? = null,
) {
    val tokens = LocalGlassTokens.current
    val activeName = state.endpoints.firstOrNull { it.id == state.activeId }?.name

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
                modifier = Modifier.statusBarsPadding(),
                titleAlignment = Alignment.CenterHorizontally,
            )
        },
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
                // 悬浮页签占位：加在滚动内容**之内**，末尾条目能滚出页签区；
                // 内容本体仍从玻璃页签底下穿过（见 LocalBottomBarOverlay 的 KDoc）。
                .padding(bottom = LocalBottomBarOverlay.current),
            verticalArrangement = Arrangement.spacedBy(tokens.cardGap),
        ) {
            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Column {
                    GroupTitle(stringResource(R.string.settings_section_endpoints))
                    GlassSettingRow(
                        title = stringResource(R.string.settings_manage_endpoints),
                        subtitle = if (activeName != null) {
                            stringResource(R.string.settings_active_endpoint, activeName)
                        } else {
                            stringResource(R.string.settings_active_endpoint_none)
                        },
                        onClick = onManageEndpoints,
                    )
                }
            }

            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Column {
                    GroupTitle(stringResource(R.string.settings_section_capabilities))
                    // 五个入口都由宿主注入回调：null 表示「本构建没有这个页面」，
                    // 不渲染行 —— 摆一个点了没反应的入口比没有更糟（本文件类头同一纪律）。
                    CapabilityRows(
                        onManageSkills = onManageSkills,
                        onManageMcp = onManageMcp,
                        onManageMemory = onManageMemory,
                        onManageTools = onManageTools,
                        onManageSandbox = onManageSandbox,
                    )
                }
            }

            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Column {
                    GroupTitle(stringResource(R.string.settings_section_context))
                    Text(
                        text = stringResource(R.string.settings_context_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    ContextWindowRows(
                        current = state.maxContextTokens,
                        onPick = onSetContextWindow,
                    )
                }
            }

            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Column {
                    GroupTitle(stringResource(R.string.settings_section_about))
                    GlassSettingRow(
                        title = stringResource(R.string.settings_about_app),
                        subtitle = stringResource(R.string.settings_about_hint),
                        trailing = { ValueText(stringResource(R.string.settings_app_name)) },
                    )
                    GlassSettingRow(
                        title = stringResource(R.string.settings_about_version),
                        modifier = Modifier.padding(top = 8.dp),
                        trailing = { ValueText(appVersion) },
                    )
                }
            }

            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Column {
                    GroupTitle(stringResource(R.string.settings_section_diagnostics))
                    // 只读行：本仓还没有诊断页，如实说明日志的去向，不摆假入口。
                    GlassSettingRow(
                        title = stringResource(R.string.settings_diagnostics_log),
                        subtitle = stringResource(R.string.settings_diagnostics_log_hint),
                        enabled = false,
                    )
                }
            }
        }
    }
}

/**
 * 能力分组里的入口行（技能 / MCP / 记忆 / 工具 / 沙箱）。
 *
 * 抽出成独立 composable 只为守 G11（单方法 ≤120 行）。用 `listOfNotNull` 而不是五段
 * `if (x != null)`：五段重复的模板行只差资源 id，`null = 本构建没有这一页` 的语义
 * 用 `listOfNotNull` 表达更短、也不可能漏判。首行不加顶距，其余行 `top = 8.dp`。
 */
@Composable
private fun CapabilityRows(
    onManageSkills: (() -> Unit)?,
    onManageMcp: (() -> Unit)?,
    onManageMemory: (() -> Unit)?,
    onManageTools: (() -> Unit)?,
    onManageSandbox: (() -> Unit)?,
) {
    val entries = listOfNotNull(
        onManageSkills?.let {
            Triple(R.string.settings_manage_skills, R.string.settings_manage_skills_hint, it)
        },
        onManageMcp?.let {
            Triple(R.string.settings_manage_mcp, R.string.settings_manage_mcp_hint, it)
        },
        onManageMemory?.let {
            Triple(R.string.settings_manage_memory, R.string.settings_manage_memory_hint, it)
        },
        onManageTools?.let {
            Triple(R.string.settings_manage_tools, R.string.settings_manage_tools_hint, it)
        },
        onManageSandbox?.let {
            Triple(R.string.settings_manage_sandbox, R.string.settings_manage_sandbox_hint, it)
        },
    )
    entries.forEachIndexed { index, (titleRes, hintRes, onClick) ->
        GlassSettingRow(
            title = stringResource(titleRes),
            subtitle = stringResource(hintRes),
            modifier = if (index == 0) Modifier else Modifier.padding(top = 8.dp),
            onClick = onClick,
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    val colors = LocalGlassColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = colors.onGlass,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun ValueText(text: String) {
    val colors = LocalGlassColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.onGlassSubtle,
    )
}

/**
 * 上下文窗口档位（W4）。
 *
 * ## 为什么是四个档位而不是一个输入框
 *
 * 这个数字直接决定单次请求的**费用上限**（1M 档在 $0.15/M 的端点上约 ¥1/次）。
 * 自由输入框会让用户在不知道代价的情况下填一个七位数；四个档位把「能力 / 代价」
 * 的取舍摊开在选择面上，且每个档位都能被截图核对。
 *
 * 当前档位用「已选」标出而不是高亮整行：`GlassSettingRow` 没有选中态，
 * 为一个只在此处用到的样式再加一套行样式不划算。
 */
@Composable
private fun ContextWindowRows(current: Int, onPick: ((Int) -> Unit)?) {
    val presets = listOf(32_000, 128_000, 512_000, 1_000_000)
    presets.forEachIndexed { index, value ->
        GlassSettingRow(
            title = windowLabel(value),
            modifier = if (index == 0) Modifier else Modifier.padding(top = 8.dp),
            subtitle = if (value == current) {
                stringResource(R.string.settings_context_current, windowLabel(value))
            } else {
                null
            },
            enabled = onPick != null,
            onClick = onPick?.let { pick -> { pick(value) } },
            trailing = if (value == current) {
                { ValueText(stringResource(R.string.settings_context_selected)) }
            } else {
                null
            },
        )
    }
}

/** `32000 -> 32K`、`1000000 -> 1M`。纯 ASCII，不做本地化（单位不随语言变）。 */
private fun windowLabel(tokens: Int): String = when {
    tokens >= 1_000_000 -> (tokens / 1_000_000).toString() + "M"
    else -> (tokens / 1_000).toString() + "K"
}
