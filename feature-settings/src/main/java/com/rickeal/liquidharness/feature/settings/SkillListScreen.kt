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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassCard
import com.rickeal.liquidharness.core.design.GlassIconButton
import com.rickeal.liquidharness.core.design.GlassScaffold
import com.rickeal.liquidharness.core.design.GlassSwitch
import com.rickeal.liquidharness.core.design.GlassTopBar
import com.rickeal.liquidharness.core.design.LocalBottomBarOverlay
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.design.LocalGlassTokens

/**
 * 技能列表的一行（纯数据，供无状态界面渲染）。
 *
 * [allowedTools] 为空 = 该技能不限制工具（SKILL.md 未声明 `allowed-tools`）。
 */
data class SkillRow(
    val name: String,
    val description: String,
    val allowedTools: List<String>,
    val source: String,
    val enabled: Boolean,
)

/**
 * 技能管理子页。
 *
 * 骨架照 [EndpointListScreen]：`GlassScaffold` 铺壁纸 → `GlassTopBar` 顶栏（返回箭头
 * + 右侧动作）→ 玻璃卡片列表。开关直接落在卡片右侧，因为"启用/停用"是这一屏唯一
 * 的写操作，比进二级页再改少两次点击。
 *
 * 无状态：列表与加载/错误都由参数给，动作全走回调 —— UI 测试不需要 ViewModel，
 * 也不读任何全局单例。
 */
@Composable
fun SkillListScreen(
    skills: List<SkillRow>,
    onToggle: (name: String, enabled: Boolean) -> Unit,
    onRefresh: () -> Unit,
    // null = 本构建没有导入能力（W3 未接 SAF）。**不渲染按钮**，而不是给一个点了没反应的键。
    onImport: (() -> Unit)? = null,
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
                title = stringResource(R.string.settings_skills_title),
                subtitle = stringResource(R.string.settings_skills_subtitle),
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
                        icon = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.settings_skills_refresh),
                        onClick = onRefresh,
                        enabled = !loading,
                    )
                    if (onImport != null) {
                        GlassIconButton(
                            icon = Icons.Filled.FileDownload,
                            contentDescription = stringResource(R.string.settings_skills_import),
                            onClick = { onImport?.invoke() },
                        )
                    }
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
                // 末尾卡片能滚出悬浮页签区（见 LocalBottomBarOverlay 的 KDoc）。
                bottom = 12.dp + LocalBottomBarOverlay.current,
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.cardGap),
        ) {
            if (error != null) {
                item { NoticeCard(text = error, color = colors.danger) }
            }
            when {
                skills.isEmpty() && loading -> item {
                    NoticeCard(
                        text = stringResource(R.string.settings_skills_loading),
                        color = colors.onGlassMuted,
                    )
                }
                skills.isEmpty() -> item {
                    NoticeCard(
                        text = stringResource(R.string.settings_skills_empty),
                        color = colors.onGlassMuted,
                    )
                }
                else -> items(skills) { row ->
                    SkillCard(row = row, onToggle = onToggle)
                }
            }
        }
    }
}

/**
 * 单条技能：名称 + 两行描述 + 来源/工具小字 + 启用开关。
 *
 * 描述与元信息都给 `maxLines`：技能描述由模型作者自由书写，可以长达数屏；
 * 列表里只做"这是干什么的"的速览，全文留给未来的详情页。
 */
@Composable
private fun SkillCard(
    row: SkillRow,
    onToggle: (name: String, enabled: Boolean) -> Unit,
) {
    val colors = LocalGlassColors.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onGlass,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = row.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onGlassMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (row.allowedTools.isEmpty()) {
                        stringResource(R.string.settings_skills_meta_unrestricted, row.source)
                    } else {
                        stringResource(
                            R.string.settings_skills_meta,
                            row.source,
                            row.allowedTools.joinToString(", "),
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onGlassSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GlassSwitch(
                checked = row.enabled,
                onCheckedChange = { onToggle(row.name, it) },
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** 空态 / 加载态 / 错误态共用的提示卡。 */
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
