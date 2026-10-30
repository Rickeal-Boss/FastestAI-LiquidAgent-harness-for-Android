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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassCard
import com.rickeal.liquidharness.core.design.GlassChip
import com.rickeal.liquidharness.core.design.GlassFab
import com.rickeal.liquidharness.core.design.GlassIconButton
import com.rickeal.liquidharness.core.design.GlassScaffold
import com.rickeal.liquidharness.core.design.GlassTopBar
import com.rickeal.liquidharness.core.design.LocalBottomBarOverlay
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.design.LocalGlassTokens
import com.rickeal.liquidharness.core.model.RemoteEndpoint

/**
 * 端点列表（设置子页）。
 *
 * 视觉换成玻璃：`GlassScaffold` 铺壁纸 → `GlassTopBar` 顶栏 → 玻璃卡片列表，
 * 新增入口用 `GlassFab`（落在 Scaffold 的 FAB 槽，自动让开导航栏与悬浮页签）。
 * 旧实现是 M3 `Scaffold + TopAppBar + FloatingActionButton`，在玻璃壁纸上会浮出
 * 一块纯色横条与实心圆钮，与其余页面不是一套语言。
 *
 * 无状态：列表来自 [SettingsUiState]，所有动作走回调 —— UI 测试不需要 ViewModel。
 */
@Composable
fun EndpointListScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onSetActive: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.settings_manage_endpoints),
                subtitle = stringResource(R.string.settings_section_endpoints),
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
        floatingActionButton = {
            GlassFab(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.settings_add_endpoint),
                    tint = LocalGlassColors.current.onGlass,
                )
            }
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
                // 末尾卡片能滚出悬浮页签区（竖屏 84dp，横屏 0dp）。
                bottom = 12.dp + LocalBottomBarOverlay.current,
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.cardGap),
        ) {
            if (state.endpoints.isEmpty()) {
                item {
                    GlassCard(contentPadding = PaddingValues(16.dp)) {
                        Column {
                            Text(
                                text = stringResource(R.string.settings_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalGlassColors.current.onGlass,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.settings_empty_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalGlassColors.current.onGlassSubtle,
                            )
                        }
                    }
                }
            } else {
                items(state.endpoints, key = { it.id }) { endpoint ->
                    EndpointCard(
                        endpoint = endpoint,
                        active = endpoint.id == state.activeId,
                        onClick = { onEdit(endpoint.id) },
                        onSetActive = { onSetActive(endpoint.id) },
                        onDelete = { onDelete(endpoint.id) },
                    )
                }
            }
        }
    }
}

/**
 * 单个端点卡片：名称 + 活跃徽标 + 地址/模型 + 行内动作。
 *
 * 整卡可点 = 编辑；行内「设为当前」「删除」是独立点击目标，先于整卡命中
 * （Compose 命中测试的自然后果），不会冒泡成"点了删除却打开了编辑页"。
 */
@Composable
private fun EndpointCard(
    endpoint: RemoteEndpoint,
    active: Boolean,
    onClick: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalGlassColors.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = endpoint.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onGlass,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (active) {
                    GlassChip(
                        text = stringResource(R.string.settings_active_badge),
                        selected = true,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = endpoint.baseUrl + "  ·  " + endpoint.modelId,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onGlassSubtle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!active) {
                    GlassChip(
                        text = stringResource(R.string.settings_action_set_active),
                        onClick = onSetActive,
                    )
                }
                GlassChip(
                    text = stringResource(R.string.settings_action_delete),
                    onClick = onDelete,
                )
            }
        }
    }
}
