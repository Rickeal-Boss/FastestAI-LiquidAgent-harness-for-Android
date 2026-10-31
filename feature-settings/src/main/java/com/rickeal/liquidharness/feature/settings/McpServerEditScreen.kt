package com.rickeal.liquidharness.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassButton
import com.rickeal.liquidharness.core.design.GlassIconButton
import com.rickeal.liquidharness.core.design.GlassMaterial
import com.rickeal.liquidharness.core.design.GlassScaffold
import com.rickeal.liquidharness.core.design.GlassSwitch
import com.rickeal.liquidharness.core.design.GlassTextField
import com.rickeal.liquidharness.core.design.GlassTopBar
import com.rickeal.liquidharness.core.design.LocalBottomBarOverlay
import com.rickeal.liquidharness.core.design.LocalGlassColors

/** MCP 服务器的编辑草稿。`token` 为空串表示"沿用已存令牌"。 */
data class McpDraft(
    val id: String = "",
    val alias: String = "",
    val url: String = "",
    val token: String = "",
    val allowPrivateNetwork: Boolean = false,
    val enabled: Boolean = true,
)

private const val MAX_ALIAS_LENGTH = 32

/**
 * MCP 服务器新增 / 编辑页。
 *
 * ## 为什么密钥不回填
 *
 * 与 [EndpointEditScreen] 同一条纪律：已存令牌不回填，只提示「留空沿用」。
 * 明文令牌进 Compose 状态会进快照、进 UI 测试报告，而用户改 URL 时不需要看它。
 * 同理 `token` 刻意**不进** `rememberSaveable` —— 旋转一次就丢，是可接受代价。
 *
 * ## 为什么校验放在 UI
 *
 * 别名长度与 `http(s)://` 前缀是纯格式约束，不需要 IO 就能判死。放在这里的好处是
 * 「保存」按钮在非法态直接置灰，用户不必先点一次再等一个错误码回来。真正的
 * 地址安全策略（内网 / 明文 / 账号密码）仍在数据层，本页不重复实现。
 */
@Composable
fun McpServerEditScreen(
    initial: McpDraft,
    isNew: Boolean,
    onSave: (McpDraft) -> Unit,
    onTest: (McpDraft) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    testing: Boolean = false,
    testResult: String? = null,
    error: String? = null,
) {
    val key = initial.id.ifEmpty { "new" }
    var alias by rememberSaveable(key) { mutableStateOf(initial.alias) }
    var url by rememberSaveable(key) { mutableStateOf(initial.url) }
    var allowPrivate by rememberSaveable(key) { mutableStateOf(initial.allowPrivateNetwork) }
    var enabled by rememberSaveable(key) { mutableStateOf(initial.enabled) }
    // 用户动过输入才报红：一进新增页就满屏红字是在骂人，不是在帮人。
    var touched by rememberSaveable(key) { mutableStateOf(false) }
    var token by remember(key) { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }

    val aliasTrimmed = alias.trim()
    val urlTrimmed = url.trim()
    val aliasValid = aliasTrimmed.isNotEmpty() && aliasTrimmed.length <= MAX_ALIAS_LENGTH
    val urlValid = urlTrimmed.startsWith("http://") || urlTrimmed.startsWith("https://")
    val valid = aliasValid && urlValid
    val draft = McpDraft(
        id = initial.id,
        alias = aliasTrimmed,
        url = urlTrimmed,
        token = token,
        allowPrivateNetwork = allowPrivate,
        enabled = enabled,
    )

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(
                title = stringResource(
                    if (isNew) R.string.settings_mcp_new_title else R.string.settings_mcp_edit_title,
                ),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .padding(bottom = LocalBottomBarOverlay.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledField(
                label = stringResource(R.string.settings_mcp_alias),
                value = alias,
                onValueChange = {
                    alias = it
                    touched = true
                },
                error = if (touched && !aliasValid) {
                    stringResource(R.string.settings_mcp_alias_invalid)
                } else {
                    null
                },
            )
            LabeledField(
                label = stringResource(R.string.settings_mcp_url),
                value = url,
                onValueChange = {
                    url = it
                    touched = true
                },
                placeholder = "https://example.com/mcp",
                keyboardType = KeyboardType.Uri,
                error = if (touched && !urlValid) {
                    stringResource(R.string.settings_mcp_url_invalid)
                } else {
                    null
                },
            )
            TokenField(
                token = token,
                onTokenChange = { token = it },
                visible = tokenVisible,
                onToggleVisible = { tokenVisible = !tokenVisible },
                hasStoredToken = !isNew,
            )
            BehaviorSwitches(
                allowPrivate = allowPrivate,
                onAllowPrivateChange = { allowPrivate = it },
                enabled = enabled,
                onEnabledChange = { enabled = it },
            )

            EditActions(
                error = error,
                testResult = testResult,
                valid = valid,
                testing = testing,
                onTestClick = { onTest(draft) },
                onSaveClick = { onSave(draft) },
            )
        }
    }
}

@Composable
private fun EditActions(
    error: String?,
    testResult: String?,
    valid: Boolean,
    testing: Boolean,
    onTestClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    val colors = LocalGlassColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
            )
        }
        if (testResult != null) {
            Text(
                text = testResult,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onGlassMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = stringResource(R.string.settings_action_test),
                onClick = onTestClick,
                enabled = valid && !testing,
                loading = testing,
                material = GlassMaterial.THIN,
            )
            GlassButton(
                text = stringResource(R.string.settings_action_save),
                onClick = onSaveClick,
                enabled = valid,
            )
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(label)
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = LocalGlassColors.current.danger,
            )
        }
    }
}

/**
 * Bearer Token 一格。
 *
 * ⚠️ 刻意用 M3 的 `OutlinedTextField`：`GlassTextField` 没有 `visualTransformation`
 * 形参，而把令牌明文画在屏幕上不可接受（`core-design` 不在本文件域内）。
 * 与 [EndpointEditScreen] 的 API Key 一格同一处理，视觉上的一致性让位给密钥纪律。
 */
@Composable
private fun TokenField(
    token: String,
    onTokenChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    hasStoredToken: Boolean,
) {
    val colors = LocalGlassColors.current
    Column {
        FieldLabel(stringResource(R.string.settings_mcp_token))
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            singleLine = true,
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggleVisible) {
                    Icon(
                        imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (visible) R.string.settings_mcp_hide_token else R.string.settings_mcp_show_token,
                        ),
                        tint = colors.onGlassMuted,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasStoredToken && token.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_mcp_token_kept),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onGlassSubtle,
            )
        }
    }
}

@Composable
private fun BehaviorSwitches(
    allowPrivate: Boolean,
    onAllowPrivateChange: (Boolean) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow(
            title = stringResource(R.string.settings_mcp_allow_private),
            hint = stringResource(R.string.settings_mcp_allow_private_hint),
            checked = allowPrivate,
            onCheckedChange = onAllowPrivateChange,
        )
        SwitchRow(
            title = stringResource(R.string.settings_mcp_enabled),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
) {
    val colors = LocalGlassColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onGlass,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onGlassSubtle,
                )
            }
        }
        GlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalGlassColors.current.onGlassMuted,
    )
}
