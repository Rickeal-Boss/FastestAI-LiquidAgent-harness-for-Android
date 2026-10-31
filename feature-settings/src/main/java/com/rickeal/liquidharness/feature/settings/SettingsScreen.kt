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
                    // 三个入口都由宿主注入回调：null 表示「本构建没有这个页面」，
                    // 不渲染行 —— 摆一个点了没反应的入口比没有更糟（本文件类头同一纪律）。
                    if (onManageSkills != null) {
                        GlassSettingRow(
                            title = stringResource(R.string.settings_manage_skills),
                            subtitle = stringResource(R.string.settings_manage_skills_hint),
                            onClick = onManageSkills,
                        )
                    }
                    if (onManageMcp != null) {
                        GlassSettingRow(
                            title = stringResource(R.string.settings_manage_mcp),
                            subtitle = stringResource(R.string.settings_manage_mcp_hint),
                            modifier = Modifier.padding(top = 8.dp),
                            onClick = onManageMcp,
                        )
                    }
                    if (onManageMemory != null) {
                        GlassSettingRow(
                            title = stringResource(R.string.settings_manage_memory),
                            subtitle = stringResource(R.string.settings_manage_memory_hint),
                            modifier = Modifier.padding(top = 8.dp),
                            onClick = onManageMemory,
                        )
                    }
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
