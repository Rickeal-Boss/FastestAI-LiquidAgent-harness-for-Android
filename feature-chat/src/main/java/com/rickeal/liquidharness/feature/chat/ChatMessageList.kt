package com.rickeal.liquidharness.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassBubble
import com.rickeal.liquidharness.core.design.GlassEmptyState
import com.rickeal.liquidharness.core.design.GlassEmptyStateAction
import com.rickeal.liquidharness.core.design.LocalGlassTokens
import com.rickeal.liquidharness.core.model.Role

/**
 * 传给 `scrollToItem` 的 scrollOffset：一个「足够大」的值，把列表顶到最底部。
 *
 * 默认 0 会把目标 item 的**顶部**对齐视口顶部 —— 流式气泡不断变高后，最新那几个
 * token 正好被顶出屏幕外，等于没跟随。不给 `Int.MAX_VALUE`：target 是整数相加算出来的，
 * 极端情况下会溢出成负数，反而跳到列表顶部。1e6 px 远超任何真实会话的内容高度。
 */
private const val SCROLL_TO_TAIL_SLACK = 1_000_000

/**
 * 消息列表。
 *
 * 三段式：空态 → 已定稿气泡 → （流式中）工具轨迹 + 在途气泡。
 * 在途气泡由 [StreamingState] 现场拼出来，不落进 [bubbles] —— 单一真源，
 * 定稿时才由归约器生成真正的气泡。
 *
 * ## 滚动跟随（旧仓同一口径）
 *
 * 只在用户**贴着底部**时跟随；回看历史时收到新消息不被强行拽到底。
 * 例外：最新一条是自己发的 —— 发消息的人必然想看到它出现，无条件滚。
 * 「是否贴底」用 `derivedStateOf` 包住：`layoutInfo` 每次滚动都会更新，
 * 直接在组合里读会让整个列表跟着每一帧滚动重组。
 */
@Composable
fun ChatMessageList(
    bubbles: List<ChatBubble>,
    streaming: StreamingState,
    toolTraces: List<ToolTrace>,
    isStreaming: Boolean,
    hasEndpoint: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val tokens = LocalGlassTokens.current
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            lastVisible == null || lastVisible.index >= info.totalItemsCount - 2
        }
    }
    // 思考块的展开态是纯 UI 状态（不进 ChatUiState）：它不影响任何业务判定，
    // 放进状态机只会让归约器多一个与事件无关的字段。
    val expandedThinking = remember { mutableStateMapOf<String, Boolean>() }
    val streamingLength = streaming.text.length + streaming.reasoning.length

    LaunchedEffect(bubbles.size) {
        val index = bubbles.lastIndex
        if (index < 0) return@LaunchedEffect
        if (atBottom || bubbles[index].role == Role.USER) {
            listState.animateScrollToItem(index, scrollOffset = SCROLL_TO_TAIL_SLACK)
        }
    }

    // 流式跟随用 scrollToItem（瞬时）而不是动画：flush 是 120ms 一次，
    // 动画还没跑完下一次就来了，会堆成一串互相取消的滚动动画。
    LaunchedEffect(isStreaming, streamingLength) {
        if (!isStreaming) return@LaunchedEffect
        if (!atBottom) return@LaunchedEffect
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) {
            listState.scrollToItem(lastIndex, scrollOffset = SCROLL_TO_TAIL_SLACK)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(tokens.cardGap),
    ) {
        if (bubbles.isEmpty() && !isStreaming) {
            item(key = "empty") {
                // fillParentMaxHeight 只能在 LazyItemScope 里解析 —— 所以在 item 作用域内
                // 拼好 modifier 再下传，不要搬进 EmptyChat 内部。
                EmptyChat(
                    hasEndpoint = hasEndpoint,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillParentMaxHeight(0.7f),
                )
            }
        }
        items(
            items = bubbles,
            key = { it.id },
            contentType = { if (it.role == Role.USER) 0 else 1 },
        ) { bubble ->
            ChatBubbleRow(
                bubble = bubble,
                thinkingExpanded = expandedThinking[bubble.id] == true,
                onToggleThinking = {
                    expandedThinking[bubble.id] = expandedThinking[bubble.id] != true
                },
            )
        }
        if (isStreaming) {
            item(key = "streaming", contentType = 1) {
                StreamingTurn(streaming = streaming, toolTraces = toolTraces)
            }
        }
    }
}

/**
 * 在途轮次：工具轨迹 + 流式气泡。
 *
 * 轨迹渲染成「折叠组 / 独立卡」交错序列：连续的已完结轨迹折叠成一组，
 * RUNNING / FAILED 永远独立（当前活动与错误必须醒目）。
 */
@Composable
private fun StreamingTurn(streaming: StreamingState, toolTraces: List<ToolTrace>) {
    var thinkingExpanded by remember { mutableStateOf(false) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val renderItems = rememberTraceRenderItems(toolTraces)
        for (renderItem in renderItems) {
            when (renderItem) {
                is ToolTraceOrGroup.Group -> {
                    val group = renderItem.group
                    ChatTraceGroupCard(
                        group = group,
                        expanded = expandedGroups[group.id] == true,
                        onToggle = { expandedGroups[group.id] = expandedGroups[group.id] != true },
                    )
                }
                is ToolTraceOrGroup.Single -> ChatToolCard(trace = renderItem.trace)
            }
        }
        GlassBubble(
            text = streaming.text,
            isUser = false,
            thinking = streaming.reasoning.ifBlank { null },
            thinkingExpanded = thinkingExpanded,
            onToggleThinking = { thinkingExpanded = !thinkingExpanded },
            isStreaming = true,
        )
    }
}

/**
 * 空态。
 *
 * 高度取视口 0.7 —— 竖屏约 470dp，内容居中很舒服；横屏手机视口矮，靠
 * `GlassEmptyState` 自身的 `verticalPadding` 收敛，不做旧仓那套「按窗口高度分档」的
 * 三档特判（那是为悬浮页签挤占视口写的，本仓没有页签）。
 */
@Composable
private fun EmptyChat(
    hasEndpoint: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        GlassEmptyState(
            title = stringResource(
                if (hasEndpoint) R.string.chat_start_title else R.string.chat_empty_title,
            ),
            subtitle = stringResource(
                if (hasEndpoint) R.string.chat_start_body else R.string.chat_empty_body,
            ),
            actions = if (hasEndpoint) {
                emptyList()
            } else {
                listOf(
                    GlassEmptyStateAction(
                        label = stringResource(R.string.chat_empty_action),
                        onClick = onOpenSettings,
                    ),
                )
            },
        )
    }
}
