package com.rickeal.liquidharness.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.foundation.text.KeyboardOptions
import com.rickeal.liquidharness.core.data.ProbeOutcome
import com.rickeal.liquidharness.core.model.ProviderKind
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.UiPreset

/**
 * 端点编辑页。
 *
 * ## 密钥的显示纪律
 *
 * 输入框永远是密码态，且**已存密钥不回填** —— 只提示「已保存，留空沿用」。
 * 回填需要把明文从 Keystore 读进 Compose 状态，而状态会进快照、进 UI 测试报告、
 * 可能被截图。用户改模型名时不需要看到 key 原文。
 *
 * ## 为什么保存前要本地校验
 *
 * `RemoteEndpoint.baseUrl` 空串会让 `chatCompletionsUrl()` 拼出 `/chat/completions`
 * 这种相对地址，请求直接失败在 OkHttp 层，错误文案对用户毫无信息量。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointEditScreen(
    initial: RemoteEndpoint?,
    probing: Boolean,
    probeResult: ProbeOutcome?,
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
    var contextLength by rememberSaveable(key) { mutableStateOf((initial?.contextLength ?: 32_768).toString()) }
    var allowPrivate by rememberSaveable(key) { mutableStateOf(initial?.allowPrivateNetwork ?: false) }
    var apiKey by rememberSaveable(key) { mutableStateOf("") }

    val draft = remember(initial, name, baseUrl, modelId, contextLength, allowPrivate) {
        (initial ?: RemoteEndpoint(preset = UiPreset.CUSTOM)).copy(
            name = name.trim(),
            baseUrl = baseUrl.trim(),
            modelId = modelId.trim(),
            contextLength = contextLength.toIntOrNull() ?: 32_768,
            allowPrivateNetwork = allowPrivate,
            kind = ProviderKind.OPENAI_COMPATIBLE,
        )
    }
    val valid = name.isNotBlank() && baseUrl.isNotBlank() && modelId.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (initial == null) R.string.settings_add_endpoint else R.string.settings_edit_endpoint,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_action_cancel))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_endpoint_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.settings_endpoint_base_url)) },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = modelId,
                onValueChange = { modelId = it },
                label = { Text(stringResource(R.string.settings_endpoint_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.settings_endpoint_key)) },
                supportingText = {
                    if (hasStoredSecret && apiKey.isEmpty()) {
                        Text(stringResource(R.string.settings_endpoint_key_kept))
                    }
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = contextLength,
                onValueChange = { contextLength = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.settings_endpoint_context)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_endpoint_private),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = allowPrivate, onCheckedChange = { allowPrivate = it })
            }

            if (!valid) {
                Text(
                    text = stringResource(R.string.settings_required_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (probing) {
                Text(
                    text = stringResource(R.string.settings_testing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = if (outcome.ok) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onSave(draft, apiKey.ifBlank { null }) },
                    enabled = valid,
                ) {
                    Text(stringResource(R.string.settings_action_save))
                }
                TextButton(
                    onClick = { onTest(draft, apiKey.ifBlank { null }) },
                    enabled = valid && !probing,
                ) {
                    Text(stringResource(R.string.settings_action_test))
                }
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial.id) }) {
                        Text(
                            text = stringResource(R.string.settings_action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
