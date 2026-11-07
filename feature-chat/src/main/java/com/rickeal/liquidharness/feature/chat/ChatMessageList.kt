package com.rickeal.liquidharness.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.rickeal.liquidharness.core.design.GlassButton
import com.rickeal.liquidharness.core.design.GlassCard
import com.rickeal.liquidharness.core.design.GlassEmptyState
import com.rickeal.liquidharness.core.design.GlassEmptyStateAction
import com.rickeal.liquidharness.core.design.GlassMaterial
import com.rickeal.liquidharness.core.design.LocalGlassColors
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
 * ## 手动压缩区（W6）
 *
 * [compression] 非空时，列表顶部插一张**分隔卡**，并且被压缩区段的气泡默认**收起**
 * （可展开回看）。三条理由：
 * - 收起是「点压缩」这个动作的可见结果 —— 点了之后屏幕上什么都没变，用户会以为按钮坏了；
 * - 气泡仍然保留（数据一条没删），所以「展开查看」是真实的、不是装饰；
 * - 收起状态是**纯 UI 状态**（`remember`），不进 `ChatUiState`：它不影响任何业务判定。
 *
 * 分隔卡的位置固定在列表顶部而不是「切点处」：切点之前的全部气泡正好是列表开头那一段，
 * 收起后列表顶部就是切点，语义等价，且不需要为「插在中间」维护额外的 key 顺序。
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
    compression: SessionCompression? = null,
    onUndoCompress: (() -> Unit)? = null,
    /** W8：定稿气泡是否走 Markdown 渲染（false = 纯文本，现役行为）。单一真值源 = DataStore。 */
    renderMarkdown: Boolean = false,
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
    // 压缩区默认收起；压缩状态一变（新的压缩 / 撤销）就回到默认态。
    var compressedExpanded by remember(compression) { mutableStateOf(false) }
    val cutIndex = remember(bubbles, compression) {
        compression?.let { c -> bubbles.indexOfFirst { it.id == c.droppedFromId } } ?: -1
    }
    // W10/S3：收起视图 = 头锚 + 压缩区之后的气泡（压缩区段隐藏），头锚保序 ⇒ 前缀缓存命中。
    // 🔸 待办（R2，UX 顺延）：分隔卡现仍在列表顶部；压缩形态为「头锚 + 压缩区 + 尾部」后，
    // 卡的视觉位置与压缩区边界错位。改到「头锚之后」会把 ChatMessageList 方法推过 G11
    // 上限 120（需拆子组件再下沉），本波按「触顶先下沉、UX 顺延」纪律记为下一车待办。
    val droppedRange = remember(bubbles, compression) {
        compression?.let { c ->
            val from = bubbles.indexOfFirst { it.id == c.droppedFromId }
            val to = bubbles.indexOfFirst { it.id == c.droppedToId }
            if (from < 0 || to < 0 || to < from) null else from..to
        }
    }
    // 分隔卡占一个 item，所有「第几个 item」的换算都要带上它。
    val dividerItems = if (cutIndex >= 0) 1 else 0
    val visible = if (droppedRange != null && !compressedExpanded) {
        bubbles.filterIndexed { index, _ -> index !in droppedRange }
    } else bubbles
    val streamingLength = streaming.text.length + streaming.reasoning.length

    LaunchedEffect(bubbles.size) {
        val last = bubbles.lastOrNull() ?: return@LaunchedEffect
        val index = visible.lastIndex + dividerItems
        if (index < 0) return@LaunchedEffect
        if (atBottom || last.role == Role.USER) {
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
        if (cutIndex >= 0) {
            item(key = "compressed") {
                CompressedDivider(
                    count = compression?.droppedBubbleCount ?: 0,
                    savingTokens = compression?.savingTokens ?: 0,
                    expanded = compressedExpanded,
                    onToggle = { compressedExpanded = !compressedExpanded },
                    onUndo = onUndoCompress,
                )
            }
        }
        items(
            items = visible,
            key = { it.id },
            contentType = { if (it.role == Role.USER) 0 else 1 },
        ) { bubble ->
            ChatBubbleRow(
                bubble = bubble,
                thinkingExpanded = expandedThinking[bubble.id] == true,
                onToggleThinking = {
                    expandedThinking[bubble.id] = expandedThinking[bubble.id] != true
                },
                renderMarkdown = renderMarkdown,
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
 * 手动压缩分隔卡：`已压缩 N 条` + 展开/收起 + 撤销。
 *
 * 用 `GlassCard(THIN)` 而不是气泡：它不是对话内容，是**元信息**（一段历史被移出了
 * 请求体）。视觉上必须比气泡轻，否则用户会以为模型说了这句话。
 */
@Composable
private fun CompressedDivider(
    count: Int,
    savingTokens: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUndo: (() -> Unit)?,
) {
    val colors = LocalGlassColors.current
    val tokens = LocalGlassTokens.current
    GlassCard(
        material = GlassMaterial.THIN,
        cornerRadius = tokens.radiusMd,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.chat_compress_separator, count),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onGlassMuted,
            )
            if (savingTokens > 0) {
                Text(
                    text = stringResource(R.string.chat_compress_saving, savingTokens),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onGlassSubtle,
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(tokens.gapSm),
            ) {
                GlassButton(
                    text = stringResource(
                        if (expanded) R.string.chat_compress_collapse else R.string.chat_compress_expand,
                    ),
                    onClick = onToggle,
                    material = GlassMaterial.THIN,
                )
                if (onUndo != null) {
                    GlassButton(
                        text = stringResource(R.string.chat_compress_undo),
                        onClick = onUndo,
                        material = GlassMaterial.THIN,
                    )
                }
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
