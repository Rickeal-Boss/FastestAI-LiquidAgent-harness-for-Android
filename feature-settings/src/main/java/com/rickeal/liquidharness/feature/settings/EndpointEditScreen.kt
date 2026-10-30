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
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.data.EndpointSaveError
import com.rickeal.liquidharness.core.data.ProbeOutcome
import com.rickeal.liquidharness.core.design.GlassButton
import com.rickeal.liquidharness.core.design.GlassIconButton
import com.rickeal.liquidharness.core.design.GlassMaterial
import com.rickeal.liquidharness.core.design.GlassScaffold
import com.rickeal.liquidharness.core.design.GlassSwitch
import com.rickeal.liquidharness.core.design.GlassTextField
import com.rickeal.liquidharness.core.design.GlassTopBar
import com.rickeal.liquidharness.core.design.LocalBottomBarOverlay
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.model.ProviderKind
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.UiPreset

/** 默认上下文窗口：与旧仓 / 服务端常见默认一致（32K）。 */
private const val DEFAULT_CONTEXT_LENGTH = 32_768

/**
 * 端点编辑 / 新增页。
 *
 * ## 密钥的显示纪律
 *
 * 输入框永远是密码态，且**已存密钥不回填** —— 只提示「已保存，留空沿用」。
 * 回填需要把明文从 Keystore 读进 Compose 状态，而状态会进快照、进 UI 测试报告、
 * 可能被截图。用户改模型名时不需要看到 key 原文。
 *
 * ⚠️ 只有 API Key 这一格用 M3 的 `OutlinedTextField`，其余全部用 `GlassTextField`：
 * `GlassTextField` 目前**没有** `visualTransformation` 形参（`core-design` 不在本单
 * 文件域内），而把密钥明文画在屏幕上不可接受。宁可这一格视觉上偏离玻璃语言，
 * 也不拿密钥的可见性做取舍。
 *
 * ## 为什么保存前要本地校验
 *
 * `RemoteEndpoint.baseUrl` 空串会让 `chatCompletionsUrl()` 拼出 `/chat/completions`
 * 这种相对地址，请求直接失败在 OkHttp 层，错误文案对用户毫无信息量。
 */
@Composable
fun EndpointEditScreen(
    initial: RemoteEndpoint?,
    probing: Boolean,
    probeResult: ProbeOutcome?,
    saveError: EndpointSaveError?,
    hasStoredSecret: Boolean,
    onBack: () -> Unit,
    onSave: (RemoteEndpoint, String?) -> Unit,
    onTest: (RemoteEndpoint, String?) -> Unit,
    onDelete: (String) -> Unit,
) {
    val key = initial?.id ?: "new"
    var name by rememberSaveable(key) { mutableStateOf(initial?.name.orEmpty()) }
    var baseUrl by rememberSaveable(key) { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var modelId by rememberSaveable(key) { mutableStateOf(initial?.modelId.orEmpty()) }
    var contextLength by rememberSaveable(key) {
        mutableStateOf((initial?.contextLength ?: DEFAULT_CONTEXT_LENGTH).toString())
    }
    var allowPrivate by rememberSaveable(key) {
        mutableStateOf(initial?.allowPrivateNetwork ?: false)
    }
    // ⚠️ **API Key 不许进 `rememberSaveable`**：它的值会进 `onSaveInstanceState` 的
    // Bundle（系统持久化、进程死亡恢复、可能落进 bug report 附件）—— 这是 AGENTS.md
    // §六「五处泄漏面」之外的第**六**处，外审 P1-1 点名。
    // 配置变更（旋转）时明文丢失是可接受代价：密码框本就不该跨进程死亡恢复。
    var apiKey by remember(key) { mutableStateOf("") }

    val draft = remember(initial, name, baseUrl, modelId, contextLength, allowPrivate) {
        (initial ?: RemoteEndpoint(preset = UiPreset.CUSTOM)).copy(
            name = name.trim(),
            baseUrl = baseUrl.trim(),
            modelId = modelId.trim(),
            contextLength = contextLength.toIntOrNull() ?: DEFAULT_CONTEXT_LENGTH,
            allowPrivateNetwork = allowPrivate,
            kind = ProviderKind.OPENAI_COMPATIBLE,
        )
    }
    val valid = name.isNotBlank() && baseUrl.isNotBlank() && modelId.isNotBlank()

    GlassScaffold(
        topBar = {
            GlassTopBar(
                title = stringResource(
                    if (initial == null) R.string.settings_add_endpoint else R.string.settings_edit_endpoint,
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
                // 键盘升起时必须把可滚动视口压短，否则底部按钮被盖住（edge-to-edge 下
                // 系统不会自动顶）。顺序「先 ime 后 nav」：ime 不含导航栏高度。
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .padding(bottom = LocalBottomBarOverlay.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledField(
                label = stringResource(R.string.settings_endpoint_name),
                value = name,
                onValueChange = { name = it },
            )
            LabeledField(
                label = stringResource(R.string.settings_endpoint_base_url),
                value = baseUrl,
                onValueChange = { baseUrl = it },
                placeholder = "https://api.openai.com/v1",
            )
            LabeledField(
                label = stringResource(R.string.settings_endpoint_model),
                value = modelId,
                onValueChange = { modelId = it },
            )
            ApiKeyField(
                apiKey = apiKey,
                onApiKeyChange = { apiKey = it },
                hasStoredSecret = hasStoredSecret,
            )
            LabeledField(
                label = stringResource(R.string.settings_endpoint_context),
                value = contextLength,
                onValueChange = { contextLength = it.filter(Char::isDigit) },
                keyboardType = KeyboardType.Number,
            )
            PrivateNetworkRow(
                checked = allowPrivate,
                onCheckedChange = { allowPrivate = it },
            )
            EndpointActions(
                valid = valid,
                probing = probing,
                probeResult = probeResult,
                saveError = saveError,
                canDelete = initial != null,
                onSaveClick = { onSave(draft, apiKey.ifBlank { null }) },
                onTestClick = { onTest(draft, apiKey.ifBlank { null }) },
                onDeleteClick = { initial?.let { onDelete(it.id) } },
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
    }
}

/**
 * API Key 一格：密码态 + 「已保存，留空沿用」提示。
 *
 * 见 [EndpointEditScreen] 的 KDoc「密钥的显示纪律」—— 这一格刻意不用 `GlassTextField`。
 */
@Composable
private fun ApiKeyField(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    hasStoredSecret: Boolean,
) {
    Column {
        FieldLabel(stringResource(R.string.settings_endpoint_key))
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasStoredSecret && apiKey.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_endpoint_key_kept),
                style = MaterialTheme.typography.labelSmall,
                color = LocalGlassColors.current.onGlassSubtle,
            )
        }
    }
}

@Composable
private fun PrivateNetworkRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_endpoint_private),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalGlassColors.current.onGlass,
            modifier = Modifier.weight(1f),
        )
        GlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 校验提示 + 探测结果 + 保存 / 测试 / 删除。 */
@Composable
private fun EndpointActions(
    valid: Boolean,
    probing: Boolean,
    probeResult: ProbeOutcome?,
    saveError: EndpointSaveError?,
    canDelete: Boolean,
    onSaveClick: () -> Unit,
    onTestClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val colors = LocalGlassColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!valid) {
            Text(
                text = stringResource(R.string.settings_required_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
            )
        }
        // 保存失败必须压在这一屏：用户点完保存如果只看到「什么都没发生」，
        // 会以为已经存上了（UNSAFE_URL / SECRET_WRITE_FAILED 都是静默高危态）。
        saveError?.let { err ->
            Text(
                text = stringResource(
                    when (err) {
                        EndpointSaveError.UNSAFE_URL -> R.string.settings_save_error_unsafe_url
                        EndpointSaveError.SECRET_WRITE_FAILED ->
                            R.string.settings_save_error_secret_write_failed
                        EndpointSaveError.SAVE_FAILED -> R.string.settings_save_error_save_failed
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
            )
        }
        if (probing) {
            Text(
                text = stringResource(R.string.settings_testing),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onGlassMuted,
            )
        }
        probeResult?.let { outcome ->
            Text(
                text = if (outcome.ok) {
                    stringResource(R.string.settings_test_ok, outcome.latencyMillis.toInt())
                } else {
                    stringResource(R.string.settings_test_fail, outcome.detail)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (outcome.ok) colors.accent else colors.danger,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = stringResource(R.string.settings_action_save),
                onClick = onSaveClick,
                enabled = valid,
            )
            GlassButton(
                text = stringResource(R.string.settings_action_test),
                onClick = onTestClick,
                enabled = valid && !probing,
                loading = probing,
                material = GlassMaterial.THIN,
            )
        }
        if (canDelete) {
            GlassButton(
                text = stringResource(R.string.settings_action_delete),
                onClick = onDeleteClick,
                material = GlassMaterial.THIN,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
