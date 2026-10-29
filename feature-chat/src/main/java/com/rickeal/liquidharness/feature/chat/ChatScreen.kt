package com.rickeal.liquidharness.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.model.Role

/**
 * 对话页（W1：纯文本流式）。
 *
 * 刻意做成**无状态 + 回调**：状态由 [ChatUiState] 传入，所有交互走回调。
 * 这样 Compose Preview 与 UI 测试都不需要 ViewModel / 网络。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.chat_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = state.endpointLabel ?: stringResource(R.string.chat_subtitle_empty),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.chat_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            AnimatedVisibility(visible = state.errorMessage != null) {
                state.errorMessage?.let { message ->
                    ErrorBanner(message = message, onDismiss = onDismissError)
                }
            }

            if (state.bubbles.isEmpty()) {
                EmptyState(
                    hasEndpoint = state.endpointLabel != null,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
            } else {
                MessageList(state = state, modifier = Modifier.weight(1f))
            }

            Composer(
                state = state,
                onInputChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
            )
        }
    }
}

@Composable
private fun MessageList(state: ChatUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // 新气泡 / 流式增量都要求贴底：流式时每帧都可能增长，用 bubbles.size 与最后一条
    // 的文本长度共同作为 key，避免「只在新增气泡时滚动」导致长回答滚出屏幕。
    val tailSize = state.bubbles.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(state.bubbles.size, tailSize) {
        if (state.bubbles.isNotEmpty()) {
            listState.animateScrollToItem(state.bubbles.lastIndex)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.bubbles, key = { it.id }) { bubble ->
            BubbleRow(bubble)
        }
    }
}

@Composable
private fun BubbleRow(bubble: ChatBubble) {
    val isUser = bubble.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            if (bubble.reasoning.isNotBlank()) {
                Text(
                    text = stringResource(R.string.chat_reasoning_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = bubble.reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Surface(
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = bubble.text.ifEmpty { "…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    hasEndpoint: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = if (hasEndpoint) stringResource(R.string.chat_title) else stringResource(R.string.chat_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hasEndpoint) {
                    stringResource(R.string.chat_input_hint)
                } else {
                    stringResource(R.string.chat_empty_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasEndpoint) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.chat_empty_action))
                }
            }
        }
    }
}

@Composable
private fun Composer(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_input_hint)) },
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            shape = RoundedCornerShape(20.dp),
        )
        if (state.isStreaming) {
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.chat_action_stop))
            }
        } else {
            SendButton(enabled = state.canSend, onSend = onSend)
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, onSend: () -> Unit) {
    Surface(
        onClick = onSend,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(48.dp),
    ) {
        Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.chat_action_send),
                color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chat_error_generic, message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

