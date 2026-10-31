package com.rickeal.liquidharness.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassButton
import com.rickeal.liquidharness.core.design.GlassEmptyState
import com.rickeal.liquidharness.core.design.GlassEmptyStateAction
import com.rickeal.liquidharness.core.design.GlassIconButton
import com.rickeal.liquidharness.core.design.GlassMaterial
import com.rickeal.liquidharness.core.design.GlassScaffold
import com.rickeal.liquidharness.core.design.GlassSettingRow
import com.rickeal.liquidharness.core.design.GlassTopBar
import com.rickeal.liquidharness.core.design.LiquidDialog
import com.rickeal.liquidharness.core.design.LocalBottomBarOverlay
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.design.LocalGlassTokens

/**
 * 沙箱文件子页（移植自旧仓 `SandboxFilesScreen`，只保留浏览主线）。
 *
 * ## 相对旧仓删掉的东西
 *
 *  - **`FileProvider` + `ACTION_VIEW`「打开」出口**：需要 `:app` 声明 provider 路径表，
 *    且会把沙箱文件交给外部 App（旧仓自己就把它记成「出应用边界」待裁决项）。本波不引入。
 *  - **`SandboxFileScanner` / 旧仓 `SandboxFilesViewModel`（core-data）**：本仓没有这套
 *    扫描器，且本波禁止改 core-data ⇒ 列目录就近落在 [SandboxFiles.kt] 的 `listDir`。
 *  - **入口卡的计数摘要 / 存储页分桶**：那是旧仓工具页与存储页的东西，本仓沙箱有独立路由。
 *
 * 新增（旧仓没有）：**删除**，必须过二次确认弹层（`LiquidDialog`）。
 *
 * 无状态：目录内容与动作全走参数；只有「待删除项」是局部 UI 状态。
 */
@Composable
fun SandboxFilesScreen(
    state: SandboxUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUp: () -> Unit,
    onEnter: (String) -> Unit,
    onPreview: (SandboxEntry) -> Unit,
    onDelete: (SandboxEntry) -> Unit,
    onDismissPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<SandboxEntry?>(null) }

    // 系统返回键与顶栏返回钮**同语义**：子目录里先上溯一层。根层时本回调 disabled，
    // 返回键回落到 app 级两段式（与其它设置子页一致）—— 那两个 BackHandler 的 enabled
    // 条件互斥，本回调在目的地组合期注册得更晚，LIFO 下优先生效。
    BackHandler(enabled = state.path.isNotEmpty()) { onUp() }

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.sandbox_title),
                subtitle = sandboxSubtitle(state),
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.sandbox_back),
                        onClick = { if (state.path.isEmpty()) onBack() else onUp() },
                    )
                },
            )
        },
    ) { _ ->
        SandboxFilesBody(
            state = state,
            onRefresh = onRefresh,
            onEnter = onEnter,
            onPreview = onPreview,
            onRequestDelete = { pendingDelete = it },
        )
    }

    val target = pendingDelete
    if (target != null) {
        LiquidDialog(
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.sandbox_delete_title),
            subtitle = stringResource(R.string.sandbox_delete_message, target.name),
            actions = { dismiss ->
                GlassButton(
                    text = stringResource(R.string.sandbox_action_cancel),
                    onClick = dismiss,
                    material = GlassMaterial.THIN,
                )
                GlassButton(
                    text = stringResource(R.string.sandbox_action_delete),
                    onClick = {
                        pendingDelete = null
                        dismiss()
                        onDelete(target)
                    },
                )
            },
            content = {},
        )
    }

    val preview = state.preview
    if (preview != null) {
        SandboxPreviewDialog(preview = preview, onDismiss = onDismissPreview)
    }
}

/**
 * 内容块：面包屑 + 错误/加载/空态 + 条目列表。
 *
 * 从 [SandboxFilesScreen] 原位抽出只为守 G11（方法 ≤120 行），没有别的分层含义。
 */
@Composable
private fun SandboxFilesBody(
    state: SandboxUiState,
    onRefresh: () -> Unit,
    onEnter: (String) -> Unit,
    onPreview: (SandboxEntry) -> Unit,
    onRequestDelete: (SandboxEntry) -> Unit,
) {
    val colors = LocalGlassColors.current
    val tokens = LocalGlassTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .padding(bottom = LocalBottomBarOverlay.current),
        verticalArrangement = Arrangement.spacedBy(tokens.cardGap),
    ) {
        if (state.path.isNotEmpty()) {
            Text(
                text = state.path.joinToString("/"),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onGlassSubtle,
            )
        }
        val error = state.error
        when {
            error != null -> GlassEmptyState(
                title = stringResource(errorTitle(error)),
                subtitle = stringResource(errorHint(error)),
                actions = listOf(
                    GlassEmptyStateAction(
                        label = stringResource(R.string.sandbox_retry),
                        onClick = onRefresh,
                    )
                ),
            )
            state.entries.isEmpty() && state.loading -> GlassEmptyState(
                title = stringResource(R.string.sandbox_loading),
            )
            state.entries.isEmpty() -> GlassEmptyState(
                title = stringResource(
                    if (state.path.isEmpty()) {
                        R.string.sandbox_empty_root
                    } else {
                        R.string.sandbox_empty_dir
                    }
                ),
                subtitle = stringResource(
                    if (state.path.isEmpty()) {
                        R.string.sandbox_empty_hint_root
                    } else {
                        R.string.sandbox_empty_hint_dir
                    }
                ),
            )
            else -> for (entry in state.entries) {
                SandboxRow(
                    entry = entry,
                    onEnter = onEnter,
                    onPreview = onPreview,
                    onRequestDelete = { onRequestDelete(entry) },
                )
            }
        }
    }
}

/**
 * 一个文件 / 目录行。
 *
 *  - 目录：整行点击 → 下钻一层（不显示子项计数，那要额外扫一层目录）。
 *  - 文本文件：整行点击 → 应用内预览。
 *  - 其它（二进制 / 白名单外）：**整行不可点**，只剩行尾删除。旧仓给的是「交给外部
 *    应用打开」，本波没有那个出口（见类头），所以宁可不可点，也不留点了没反应的整行。
 */
@Composable
private fun SandboxRow(
    entry: SandboxEntry,
    onEnter: (String) -> Unit,
    onPreview: (SandboxEntry) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val onClickAction: (() -> Unit)? = when {
        entry.isDirectory -> ({ onEnter(entry.name) })
        isPreviewable(entry) -> ({ onPreview(entry) })
        else -> null
    }
    GlassSettingRow(
        title = entry.name,
        subtitle = if (entry.isDirectory) {
            stringResource(R.string.sandbox_directory)
        } else {
            stringResource(
                R.string.sandbox_file_meta,
                formatBytes(entry.sizeBytes),
                formatTime(entry.lastModifiedMillis),
            )
        },
        onClick = onClickAction,
        trailing = {
            GlassIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.sandbox_action_delete),
                onClick = onRequestDelete,
                size = 36.dp,
                iconSize = 16.dp,
                contentPadding = PaddingValues(10.dp),
            )
        },
    )
}

/**
 * 应用内预览弹层。
 *
 * 长文本限高滚动：弹窗不是阅读器，8000 字符一次性铺开会把动作区顶出视野。
 * 二进制 / 读不出来的文件只给一句说明 + 关闭，不给假入口。
 */
@Composable
private fun SandboxPreviewDialog(preview: SandboxPreview, onDismiss: () -> Unit) {
    val colors = LocalGlassColors.current
    LiquidDialog(
        onDismissRequest = onDismiss,
        title = preview.name,
        subtitle = formatBytes(preview.sizeBytes) + " · " + formatTime(preview.lastModifiedMillis),
        actions = { dismiss ->
            GlassButton(
                text = stringResource(R.string.sandbox_preview_close),
                onClick = dismiss,
                material = GlassMaterial.THIN,
            )
        },
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val text = preview.text
                if (text == null) {
                    Text(
                        text = stringResource(R.string.sandbox_preview_binary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onGlassSubtle,
                    )
                } else {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onGlass,
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                    if (preview.truncated) {
                        Text(
                            text = stringResource(
                                R.string.sandbox_preview_truncated,
                                PREVIEW_LIMIT_CHARS,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onGlassSubtle,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun sandboxSubtitle(state: SandboxUiState): String = when {
    state.loading -> stringResource(R.string.sandbox_loading)
    state.path.isEmpty() -> stringResource(R.string.sandbox_subtitle_root, state.entries.size)
    else -> stringResource(
        R.string.sandbox_subtitle_dir,
        state.path.last(),
        state.entries.size,
    )
}

private fun errorTitle(error: SandboxError): Int = when (error) {
    SandboxError.READ -> R.string.sandbox_error_read
    SandboxError.DELETE -> R.string.sandbox_error_delete
}

private fun errorHint(error: SandboxError): Int = when (error) {
    SandboxError.READ -> R.string.sandbox_error_read_hint
    SandboxError.DELETE -> R.string.sandbox_error_delete_hint
}
