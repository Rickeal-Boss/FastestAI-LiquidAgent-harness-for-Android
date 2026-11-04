package com.rickeal.liquidharness.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 切换守卫的两个上屏出口（W7）。
 *
 * 文案全部走 `stringResource`，本文件不含中文字面量（守卫 G16 只扫 main 源集的
 * **字符串字面量**，注释里的中文不计）。`SettingsUiState` 只放错误码 / 诊断码，
 * 与 `saveError` 的既有做法一致（见 `SettingsUiState` 类头 KDoc）。
 *
 * [SwitchDialogs] 由 `SettingsRoute` 的三个 composable 目的地各挂一次：
 * 设置首页（档位切换）、端点列表（设为当前）、端点编辑（保存）。
 */
@Composable
internal fun SwitchDialogs(state: SettingsUiState, viewModel: SettingsViewModel) {
    state.switchNotice?.let { notice ->
        SwitchBlockedDialog(notice) { viewModel.consumeSwitchNotice() }
    }
    state.pendingSwitch?.let { pending ->
        SwitchConfirmDialog(
            pending = pending,
            onConfirm = viewModel::confirmSwitch,
            onDismiss = viewModel::dismissSwitch,
        )
    }
}

/** BLOCKED：不写盘，给出 N/M 与三条恢复路径；诊断串一并上屏便于真机取证。 */
@Composable
private fun SwitchBlockedDialog(notice: SwitchNotice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_switch_blocked_title)) },
        text = {
            val cap = notice.capChars
            Text(
                if (cap != null) {
                    stringResource(
                        R.string.settings_switch_blocked_body,
                        notice.committedChars,
                        cap,
                        notice.reason,
                    )
                } else {
                    stringResource(R.string.settings_switch_blocked_body_fallback, notice.reason)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_action_close))
            }
        },
    )
}

/** 任何放行都二次确认，明写缓存代价；VACUOUS 额外给一条「窗口未验证」提示。 */
@Composable
private fun SwitchConfirmDialog(
    pending: PendingSwitch,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_switch_confirm_title)) },
        text = {
            val base = stringResource(R.string.settings_switch_confirm_body)
            Text(
                if (pending.vacuous) {
                    base + "\n" + stringResource(R.string.settings_switch_vacuous_hint, pending.reason)
                } else {
                    base
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_switch_confirm_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_action_cancel))
            }
        },
    )
}
