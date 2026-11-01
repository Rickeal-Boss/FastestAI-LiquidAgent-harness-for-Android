package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.agent.context.ContextCompressor
import com.rickeal.liquidharness.core.agent.context.CutPolicy
import com.rickeal.liquidharness.core.agent.context.DEFAULT_COMPRESS_TARGET_RATIO
import com.rickeal.liquidharness.core.agent.context.WindowContextCompressor
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TokenEstimator

/**
 * 手动压缩：把「气泡映射出的 history」压到预算以内，并给出一个**气泡级**切点。
 *
 * ## 为什么压缩必须在 UI 侧做（这条决定了整个设计的形状）
 *
 * history 的真值源是 UI 气泡（`ChatViewModel.send` 里 `bubbles.toHistoryMessages()`）。
 * 内核侧**没有**跨 run 的上下文可压：`RunState` 每 run 新建（`working` 从 `request.history`
 * 起），`ContextBudget` 是 `GenerationRound` 的私有构造参数，内核不暴露压缩 API。
 * 所以压缩产物必须落回气泡侧，否则下一次 run 又从全量气泡重建 —— 压缩等于没做。
 *
 * ## 不新建算法
 *
 * 压缩算法就是现役的 [WindowContextCompressor]，配置与 `ContextBudget` 的默认值同源
 * （水位线 [DEFAULT_COMPRESS_TARGET_RATIO] + [CutPolicy.TURN_ALIGNED]）。本文件只做三件
 * 现役压缩器不做的事：
 *
 * 1. **气泡对齐**：压缩器给的是「消息下标」，而 `compressedUpTo` 是「气泡 id」。
 *    气泡与消息不是一对一（一条 MODEL 气泡带 N 个轨迹 ⇒ 1 + N 条消息），所以切点必须
 *    吸附到**气泡边界**上，否则下一次 `toHistoryMessages(fromId)` 会多保留半条气泡。
 * 2. **无损校验**：压缩器只允许「丢最老的一段」。这里显式验证返回值确实是输入的后缀
 *    （`all.subList(cut) == compressed`）；不是就**放弃**，绝不接受一个被改写的历史。
 * 3. **代价可见**：返回省下的 token 与丢掉的条数，供 UI 与日志使用。
 *
 * ## 前缀缓存必然失效（不许写成「不影响」）
 *
 * 压缩把 history 的最老一段整体拿掉 ⇒ 下一次请求的提示前缀**从第一个字节起**就变了 ⇒
 * provider 侧的前缀缓存整段重建，下一次 `cached_tokens = 0`、账单更贵。这是压缩的固有
 * 代价，不是实现缺陷。`ChatViewModel` 为此落一条
 * `manual_compress dropped=N kept=M cache_invalidated=true` 的 INFO 账，确认对话框里也
 * 写死了同一句话。
 *
 * ## 纯函数
 *
 * 零 IO、零 Android、零 ViewModel 依赖，只有 `suspend`（压缩器端口是 suspend）。
 * 因此可以在 JVM 单测里用固定预算断言「丢了几条 / 留下什么 / system 段有没有被动过」。
 */
internal data class ManualCompressOutcome(
    /**
     * 气泡级切点：**这个 id 及其之前**的气泡不再进 history（仍留在列表里给用户读）。
     * `null` = 没有压缩（无损放弃 / 没有可丢的内容）。
     */
    val compressedUpTo: String?,
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
    val applied: Boolean get() = compressedUpTo != null
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
    // 每条气泡先映射一次并存下来：既算全量 history，又能在后面把消息切点换算成气泡切点。
    val perBubble = bubbles.map { bubble -> bubble to bubble.toHistoryMessages() }
    val all = perBubble.flatMap { it.second }
    val untouched = ManualCompressOutcome(null, 0, 0, all, 0, MANUAL_COMPRESS_NOOP)
    if (all.size < 2) return untouched

    val historyBudget = (budgetTokens - systemTokens(systemText)).coerceAtLeast(1)
    val compressed = compressor.compress(all, historyBudget)
    if (compressed.size >= all.size) return untouched

    // 压缩器只允许「丢最老的一段」。返回值不是输入的后缀 ⇒ 说明有人改写了历史（例如把
    // system 摘要塞进来），一律放弃：宁可什么都不压，也不要发一份内容被悄悄改过的历史。
    val cutMessages = all.size - compressed.size
    if (all.subList(cutMessages, all.size) != compressed) return untouched

    val cutBubbles = bubbleCut(perBubble, cutMessages)
    if (cutBubbles <= 0) return untouched

    val kept = perBubble.drop(cutBubbles).flatMap { it.second }
    val saving = TokenEstimator.estimateMessages(all) - TokenEstimator.estimateMessages(kept)
    return ManualCompressOutcome(
        compressedUpTo = bubbles[cutBubbles - 1].id,
        droppedBubbles = cutBubbles,
        droppedMessages = cutMessages,
        keptMessages = kept,
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
 *
 * 注意「映射出 0 条消息的气泡」（空正文且无轨迹）会被自然吸进丢弃区 —— 它本来就不进
 * history，留在列表里继续可读。
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
