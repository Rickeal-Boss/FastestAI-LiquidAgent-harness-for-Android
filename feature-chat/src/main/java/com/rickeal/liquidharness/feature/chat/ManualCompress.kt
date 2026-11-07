package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.agent.context.ContextCompressor
import com.rickeal.liquidharness.core.agent.context.CutPolicy
import com.rickeal.liquidharness.core.agent.context.DEFAULT_COMPRESS_TARGET_RATIO
import com.rickeal.liquidharness.core.agent.context.WindowContextCompressor
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TokenEstimator

/**
 * 手动压缩：把「气泡映射出的 history」压到预算以内，并给出一个**压缩区**描述。
 *
 * ## 为什么压缩必须在 UI 侧做（这条决定了整个设计的形状）
 *
 * history 的真值源是 UI 气泡（`ChatViewModel.send` 里 `bubbles.toHistoryMessages()`）。
 * 内核侧**没有**跨 run 的上下文可压：`RunState` 每 run 新建（`working` 从 `request.history`
 * 起），`ContextBudget` 是 `GenerationRound` 的私有构造参数，内核不暴露压缩 API。
 * 所以压缩产物必须落回气泡侧，否则下一次 run 又从全量气泡重建 —— 压缩等于没做。
 *
 * ## 压缩形态：保头锚 + 砍中段 + 尾部追加摘要（W10/S3，台账 W8-01）
 *
 * 现行「砍头 + 单指针」让压缩后请求**第一个字节就变**（丢的是最老段 = 前缀锚）⇒
 * provider 前缀缓存整段重建、`cached_tokens` 归 0 —— 这是「压缩 × 缓存命中互斥」的根。
 * 本形态三原则（`SessionCompression`）：
 * 1. **头锚保序**：最老 [HEAD_ANCHOR_BUBBLES] 条气泡原样保留（字节稳定 ⇒ 缓存前缀命中延续）；
 * 2. **中段砍掉**：`[droppedFromId..droppedToId]` 段不进 history（仍留在气泡里给用户读）；
 * 3. **尾部摘要**：被压段物化成一条**机械摘要消息**（零 LLM、ASCII 模板）追加在 history
 *    尾部，不打断保序段 ⇒ 压缩后 `cache_invalidated=false`（前缀改写只发生在摘要之后）。
 *
 * ## 不新建算法
 *
 * 压缩算法就是现役的 [WindowContextCompressor]，配置与 `ContextBudget` 的默认值同源
 * （水位线 [DEFAULT_COMPRESS_TARGET_RATIO] + [CutPolicy.TURN_ALIGNED]）。本文件只做四件
 * 现役压缩器不做的事：
 *
 * 1. **气泡对齐**：压缩器给的是「消息下标」，压缩区必须吸附到**气泡边界**上；
 * 2. **无损校验**：保留段必须是输入的**保序子序列**（头锚 + 候选后缀），且摘要只追加、
 *    不修改任何保留消息的字节 —— 不是就**放弃**，绝不接受被改写的 history；
 * 3. **代价可见**：返回省下的 token 与丢掉的条数，供 UI 与日志使用；
 * 4. **前缀守恒**：头锚字节不动，`renderHistoryHead` 的头锚段逐字节不变。
 *
 * ## 前缀缓存的语义变化（不许照抄旧 KDoc）
 *
 * 压缩保留头锚 ⇒ 下一次请求的**头锚前缀字节不变** ⇒ provider 侧前缀缓存对头锚段仍命中
 * （`cached_tokens > 0` 可观测）。代价是「中段删除」造成的断点：从被压段之后缓存失效。
 * `ChatViewModel` 为此落 `manual_compress dropped=N kept=M cache_invalidated=false` 的 INFO 账。
 *
 * ## 纯函数
 *
 * 零 IO、零 Android、零 ViewModel 依赖，只有 `suspend`（压缩器端口是 suspend）。
 * 因此可以在 JVM 单测里用固定预算断言「丢了几条 / 留下什么 / 头锚有没有被动过」。
 */

/**
 * W10/S3 会话级压缩的产物描述（尾部摘要前置形态）。
 *
 * 见 [compressBubbles] 类头的「压缩形态」。真值源仍只有 [ChatUiState.bubbles]；
 * 本类是**指针描述**，不是第二份内容。
 *
 * @param headBubbleCount 头锚气泡数（最老 N 条，字节不动 ⇒ 缓存前缀命中延续）。
 * @param droppedFromId 被压段起点气泡 id（含）。
 * @param droppedToId 被压段终点气泡 id（含）。
 * @param summaryText 尾部追加的摘要消息文本（机械模板，零 LLM，ASCII）。
 * @param droppedBubbleCount 被压气泡数（分隔卡「已压缩 N 条」）。
 * @param savingTokens 压缩前 − 压缩后的估算 token。
 */
data class SessionCompression(
    val headBubbleCount: Int,
    val droppedFromId: String,
    val droppedToId: String,
    val summaryText: String,
    val droppedBubbleCount: Int,
    val savingTokens: Int,
)

/**
 * 头锚气泡数：保留最老的 4 条（两问两答）作为缓存前缀锚。
 *
 * 取 4 = `PromptPrefix.HISTORY_HEAD_SIZE`（缓存稳定判据 `renderHistoryHead` 默认读前 4 条
 * 消息）：头锚 < 4 时压缩后 `renderHistoryHead(after)` 会读到头锚之外的已变动 tail ⇒
 * HISTORY 段非逐字节稳定、前缀缓存命中窗被截（审查席 R1）。4 气泡 ≥ 4 消息（一气泡至少
 * 一消息）⇒ 头锚段完整覆盖 k=4 稳定窗。
 */
internal const val HEAD_ANCHOR_BUBBLES: Int = 4

/** 摘要消息本身的估算 token 预留（保守值：ASCII 模板 + 截断片段）。 */
internal const val SUMMARY_TOKENS_RESERVE: Int = 64

internal data class ManualCompressOutcome(
    /**
     * 压缩区描述。`null` = 没有压缩（无损放弃 / 没有可丢的内容）。
     */
    val compression: SessionCompression?,
    /** 被移出 history 的气泡数（= 分隔卡上的「已压缩 N 条」）。 */
    val droppedBubbles: Int,
    /** 被移出 history 的**消息**数（≥ [droppedBubbles]，一条气泡可映射多条消息）。 */
    val droppedMessages: Int,
    /** 压缩后仍进 history 的消息（顺序与压缩前一致）。放弃时为全量。 */
    val keptMessages: List<ChatMessage>,
    /** 省下的估算 token（压缩前 − 压缩后）。放弃时为 0。 */
    val savingTokens: Int,
    /** ASCII 诊断码：`applied` / [MANUAL_COMPRESS_NOOP]。只进日志与错误码，不上屏。 */
    val reason: String,
) {
    val applied: Boolean get() = compression != null
}

/**
 * 无损放弃的诊断码。
 *
 * 单独成常量而不是内联字符串：`ChatViewModel` 要拿它跟 `errorMessage` 比对来决定上屏
 * 哪句话（与 [NO_ENDPOINT] 同一套做法），两处字面量漂移会让提示永远走「未知错误」分支。
 */
internal const val MANUAL_COMPRESS_NOOP = "manual compress: nothing to drop"

/**
 * 手动压缩入口。
 *
 * @param bubbles 当前会话的全部气泡（顺序即时间顺序）。
 * @param budgetTokens 软预算（`contextBudgetTokens` 的返回值）。**不是**硬窗口：
 *   压到软预算以下意味着下一次请求不会立刻再次触发自动压缩。
 * @param systemText 本轮 system 段文本。**只用于扣预算，绝不参与压缩**：它不在 history 里
 *   （`ContextBudget` 每轮临时拼在最前），把它喂进压缩器等于改写前缀 —— 那是比
 *   「丢几条历史」贵得多的代价。传 `null` 表示调用方拿不到（本仓 `ChatViewModel` 就是
 *   这种情况：system 由内核 `TurnLoop` 每 run 组合，UI 侧复制一份就是第二个真值源）。
 * @param compressor 压缩器端口。默认值 = 现役配置，测试可注入。
 */
internal suspend fun compressBubbles(
    bubbles: List<ChatBubble>,
    budgetTokens: Int,
    systemText: String? = null,
    compressor: ContextCompressor = manualCompressor(),
): ManualCompressOutcome {
    val perBubble = bubbles.map { bubble -> bubble to bubble.toHistoryMessages() }
    val all = perBubble.flatMap { it.second }
    val untouched = ManualCompressOutcome(null, 0, 0, all, 0, MANUAL_COMPRESS_NOOP)
    if (all.size < 2 || bubbles.size <= HEAD_ANCHOR_BUBBLES + 1) return untouched

    // 头锚：最老 HEAD_ANCHOR_BUBBLES 条气泡原样保留（字节不动 ⇒ 缓存前缀命中延续）。
    val head = perBubble.take(HEAD_ANCHOR_BUBBLES)
    val candidates = perBubble.drop(HEAD_ANCHOR_BUBBLES)
    val candidateAll = candidates.flatMap { it.second }
    if (candidateAll.size < 2) return untouched

    val historyBudget = (budgetTokens - systemTokens(systemText)).coerceAtLeast(1)
    val headTokens = TokenEstimator.estimateMessages(head.flatMap { it.second })
    val candidatesBudget = (historyBudget - headTokens - SUMMARY_TOKENS_RESERVE).coerceAtLeast(1)
    val compressed = compressor.compress(candidateAll, candidatesBudget)
    if (compressed.size >= candidateAll.size) return untouched

    // 无损校验：保留段必须是候选的**后缀**（WindowContextCompressor 只丢最老一段）。
    val cutMessages = candidateAll.size - compressed.size
    if (candidateAll.subList(cutMessages, candidateAll.size) != compressed) return untouched

    val cutBubbles = bubbleCut(candidates, cutMessages)
    if (cutBubbles <= 0) return untouched

    val droppedBubbles = candidates.take(cutBubbles)
    val droppedMessages = droppedBubbles.flatMap { it.second }
    val headMessages = head.flatMap { it.second }
    val keptCandidates = candidates.drop(cutBubbles).flatMap { it.second }
    val summary = ChatMessage(role = Role.SYSTEM, text = summaryOf(droppedBubbles))
    val keptMessages = headMessages + keptCandidates + listOf(summary)
    val saving = TokenEstimator.estimateMessages(all) - TokenEstimator.estimateMessages(keptMessages)
    val compression = SessionCompression(
        headBubbleCount = HEAD_ANCHOR_BUBBLES,
        droppedFromId = droppedBubbles.first().first.id,
        droppedToId = droppedBubbles.last().first.id,
        summaryText = summary.text,
        droppedBubbleCount = cutBubbles,
        savingTokens = saving.coerceAtLeast(0),
    )
    return ManualCompressOutcome(
        compression = compression,
        droppedBubbles = cutBubbles,
        droppedMessages = droppedMessages.size,
        keptMessages = keptMessages,
        savingTokens = saving.coerceAtLeast(0),
        reason = "applied",
    )
}

/**
 * 消息切点 → 气泡切点。
 *
 * 取**最大**的 k 使得前 k 条气泡映射出的消息数 ≤ [cutMessages]：这样保留区一定覆盖
 * 压缩器要保留的全部消息（`kept.size >= compressed.size`），代价是可能多留半条气泡。
 * 反向取（最小 k）会把压缩器决定保留的那条气泡**整条丢掉**，等于压过头。
 */
private fun bubbleCut(perBubble: List<Pair<ChatBubble, List<ChatMessage>>>, cutMessages: Int): Int {
    var consumed = 0
    var cut = 0
    for (index in perBubble.indices) {
        val count = perBubble[index].second.size
        if (consumed + count > cutMessages) break
        consumed += count
        cut = index + 1
    }
    return cut
}

/**
 * 机械摘要模板（零 LLM，ASCII 安全）。
 *
 * 只提取被压段的**结构事实**：条数、角色分布、首条非空正文片段。不调模型、
 * 不逐条搬运原文（零代码搬运 + 不膨胀请求体）。摘要只追加在 history 尾部，
 * 供模型理解「中间有一段被压缩过」。
 */
private fun summaryOf(dropped: List<Pair<ChatBubble, List<ChatMessage>>>): String {
    val bubbleCount = dropped.size
    val userCount = dropped.count { it.first.role == Role.USER }
    val modelCount = dropped.count { it.first.role == Role.MODEL }
    val firstText = dropped.firstNotNullOfOrNull { it.first.text.takeIf(String::isNotBlank) }
    val snippet = firstText?.let { if (it.length > 64) it.take(64) + "..." else it } ?: ""
    return "[context summary] $bubbleCount earlier bubbles compressed " +
        "($userCount user / $modelCount assistant);" +
        (if (snippet.isNotEmpty()) " first message: \"$snippet\"." else "") +
        " Conversation continues below."
}

/** system 段占的估算 token。拿不到（null）就算 0 —— 见 [compressBubbles] 的 `systemText`。 */
private fun systemTokens(systemText: String?): Int {
    if (systemText.isNullOrEmpty()) return 0
    return TokenEstimator.estimateMessages(listOf(ChatMessage(role = Role.SYSTEM, text = systemText)))
}

/**
 * 手动压缩用的压缩器实例：**与 `ContextBudget` 的默认配置逐字同源**。
 *
 * 刻意不传 `keepRecent`（它已不参与判定，见 `WindowContextCompressor` 的 KDoc），也不
 * 自定义水位线：手动压缩与自动压缩用两套口径时，用户会看到「点了压缩，下一次请求又
 * 自动压一次」—— 那不是两个功能，那是两个数字打架。
 */
private fun manualCompressor(): ContextCompressor = WindowContextCompressor(
    targetRatio = DEFAULT_COMPRESS_TARGET_RATIO,
    cutPolicy = CutPolicy.TURN_ALIGNED,
)
