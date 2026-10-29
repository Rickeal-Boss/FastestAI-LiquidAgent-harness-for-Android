package com.rickeal.liquidharness.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.model.RemoteEndpoint

/**
 * 端点列表。
 *
 * 无状态：列表来自 [SettingsUiState]，所有动作走回调 —— 这样 UI 测试不需要 ViewModel。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_action_cancel))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_add_endpoint))
            }
        },
    ) { padding ->
        if (state.endpoints.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.settings_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(state.endpoints, key = { it.id }) { endpoint ->
                EndpointRow(
                    endpoint = endpoint,
                    active = endpoint.id == state.activeId,
                    onClick = { onEdit(endpoint.id) },
                    onSetActive = { onSetActive(endpoint.id) },
                    onDelete = { onDelete(endpoint.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EndpointRow(
    endpoint: RemoteEndpoint,
    active: Boolean,
    onClick: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = endpoint.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (active) {
                AssistChip(
                    onClick = onSetActive,
                    label = { Text(stringResource(R.string.settings_active_badge)) },
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = endpoint.baseUrl + "  ·  " + endpoint.modelId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!active) {
                TextButton(onClick = onSetActive) { Text(stringResource(R.string.settings_action_set_active)) }
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.settings_action_delete)) }
        }
    }
}
