package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TokenEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * L2 摘要压缩的端口。实现只允许「一次廉价调用换回一段语义」，不允许递归、不允许重试。
 *
 * ## 为什么旧仓判死的结论在 API 路径下翻案（这段逐字保留动机）
 *
 * 旧仓 Wave 35 把 `SummarizingContextCompressor` 判死，理由是「摘要需要一次额外推理，
 * 端侧 4B 上这笔开销换不来等价收益」。这条判断的前提是**推理在设备上跑**：
 * 一次额外推理 = 数百毫秒到数秒的设备占用 + 电量 + 发热，而收益只是「少丢几条历史」。
 *
 * **API 路径下前提整体消失**：一次摘要调用是一次小请求（输入 = 被丢弃的那段历史，
 * 输出 ≤ 512 token），成本以「千 token 的几分钱」计；而替代方案是把整段对话丢掉。
 * 用户看到的是「模型忘了前面说过的」。⇒ 成本收益反转。
 *
 * 旧仓判死的**重启前提**是「出现估算失准事故」。W3 已交付 usage 账本
 * （`RunState.recordUsage` / `recordEstimatedPrompt`），前提满足。
 *
 * ⚠️ 仍然成立的部分：**摘要不省钱**。它是「用一次廉价调用换回一段语义」，不是「压缩成本更低」。
 * 所以它有严格的启用门槛（`summarizeMinTokens`）与硬失败回落，
 * 不允许变成「每轮都摘一次」。
 *
 * ## 实现侧三条硬约束（违反任何一条都会造出递归或双倍账单）
 *
 * 1. **不带工具** —— 否则模型可能回 `tool_calls`，摘要为空；
 * 2. **不重试** —— 失败即回落 L1，重试会让「压缩一次」变成「压缩 1+N 次」；
 * 3. **超时用 `withTimeoutOrNull`** —— `TimeoutCancellationException` 是
 *    `CancellationException` 子类，会被 `checkActive()` / `executeLoop` 接走，
 *    把「摘要超时」升级成「整 run 取消」。
 */
interface ContextSummarizer {

    /**
     * 把 [messages]（**只含被 L1 丢掉的那段历史，不含 system 段**）压成一段结构化摘要文本。
     *
     * @param targetTokens 摘要的 token 预算。实现必须据此限制 `max_tokens`。
     * @return 永远返回 [SummaryResult]，不抛异常；用户取消时 `CancellationException` 原样上抛。
     */
    suspend fun summarize(messages: List<ChatMessage>, targetTokens: Int): SummaryResult
}

/** 摘要结果。失败是**正常返回值**，不是异常 —— 摘要失败只回落 L1，绝不让 run 失败。 */
sealed interface SummaryResult {

    /** @param text 模型输出的原文（未经格式校验，由调用方 [parseStructuredSummary] 校验）。 */
    data class Ok(val text: String, val summaryTokens: Int, val elapsedMillis: Long) : SummaryResult

    /** @param reason ASCII 理由码，进日志与 UI（不面向最终用户的本地化文案）。 */
    data class Failed(val reason: String) : SummaryResult
}

/** 压缩模式。进日志与 `AgentEvent.ContextCompressed`，所以是稳定字符串。 */
enum class CompressionMode {
    /** 未超预算 / 空输入：原样返回，没压缩。 */
    NONE,

    /** 纯 L1 滑动窗口（摘要未触发或未生效）。 */
    L1,

    /** L2 生效：L1 切点 + 一条结构化摘要。 */
    L2,

    /** 摘要尝试过但失败：回落 L1。 */
    L1_FALLBACK,
}

/**
 * 一次压缩的完整结果。
 *
 * 为什么不只返回 `List<ChatMessage>`：`ContextBudget` 要 emit `AgentEvent.ContextCompressed`
 * 与写 WARN 日志，需要 mode / 丢弃量 / 摘要 token。让调用方自己去反推这些量
 * 只能靠再扫一遍历史 —— 那是同一份计算做两次。
 */
data class CompressionOutcome(
    val messages: List<ChatMessage>,
    val mode: CompressionMode,
    /** 回落原因 / 未压缩原因。ASCII 理由码，空 = 无。 */
    val reason: String = "",
    val droppedMessages: Int = 0,
    val droppedTokens: Int = 0,
    val summaryTokens: Int = 0,
    val summaryTruncated: Boolean = false,
)

/**
 * L2 包 L1 的级联压缩器。**切点由 L1 决定，本类不自己算切点。**
 *
 * ## 为什么必须复用 L1 的切点（这是本设计的核心，不是实现偷懒）
 *
 * `WindowContextCompressor.safeCutIndex` 已经把切点调整到「工具调用原子组」的边界上，
 * 即保留区首条不可能是孤儿 `toolResult`。摘要消息插在保留区**之前**，于是
 * `assistant.tool_calls ↔ tool` 的配对**由构造保证**，不依赖 `sanitizeForProvider`
 * 事后打补丁。自己算切点的版本必然要在两处维护同一套配对规则，
 * 而那两处会漂移 —— 漂移的表现是 400，且现场已被破坏。
 *
 * ## 控制流（每一步的失败都只回落，不抛）
 *
 * ```
 * 1. estimate <= budget          ⇒ 原样返回（mode=NONE）
 * 2. l1 = delegate.compress(...) ⇒ 切点由 L1 定
 * 3. L1 也压不动                 ⇒ 原样返回 + WARN（不试 L2：L2 也要切点）
 * 4. dropped = 入参 - L1 保留区（按位置切，不按内容比对）
 * 5. dropped < summarizeMinTokens ⇒ 直接返回 L1（mode=L1，省一次调用）
 * 6. summarizer.summarize(dropped, summarizeMaxTokens)
 * 7. Ok + 格式通过 ⇒ [system..., summary, ...L1 保留区]，mode=L2
 * 8. Failed / 格式拒绝 / 抛异常 ⇒ 返回 L1，mode=L1_FALLBACK
 * ```
 *
 * ## 摘要消息的三条约束
 *
 * 1. **`role = USER`，不能是 SYSTEM** —— system 段压缩前后逐字节相同是有 SHA-256 单测
 *    钉住的硬不变量（`ContextBudgetTest.compressionLeavesSystemBytesUntouched`），
 *    摘要进 system 会直接把它打破，且 prompt cache 整段失效。
 * 2. **id 稳定派生**（[SUMMARY_ID_PREFIX] + 首条被丢消息 id + 条数）—— 不是 `newId()`。
 *    id 抖动会改前缀字节，与 `sanitizeForProvider` 的补丁 id 同源理由。
 * 3. **不得被相邻同角色合并吞掉** —— 合并会把段头 `[CONTEXT SUMMARY]` 埋进正文中段，
 *    模型对「这是压缩摘要」的识别退化。判定函数 [isSummaryMessage] 供 `canMerge` 使用。
 *
 * ## 不变量（有单测钉住）
 *
 * 返回值去掉摘要消息后，其余必须是入参的**后缀**：只允许在前面加一条摘要，不允许重排。
 *
 * @param delegate L1 压缩器。**切点唯一的权威来源**。
 * @param summarizer null = 端点未装配摘要能力 ⇒ 退化成纯 L1（不报错）。
 * @param summarizeMinTokens 触发摘要的最小被丢 token 数。低于它做摘要不划算。
 * @param summarizeMaxTokens 摘要自身的 token 上限（渲染后估算）。
 */
class LayeredContextCompressor(
    private val delegate: ContextCompressor = WindowContextCompressor(),
    private val summarizer: ContextSummarizer? = null,
    private val summarizeMinTokens: Int = DEFAULT_SUMMARIZE_MIN_TOKENS,
    private val summarizeMaxTokens: Int = StructuredSummary.DEFAULT_MAX_TOKENS,
) : ContextCompressor {

    /** 端口实现：只返回消息，丢掉 mode 等诊断信息。需要诊断走 [compressDetailed]。 */
    override suspend fun compress(messages: List<ChatMessage>, budgetTokens: Int): List<ChatMessage> =
        compressDetailed(messages, budgetTokens).messages

    /** 带诊断信息的压缩。`ContextBudget` 用这个（要 emit `ContextCompressed` 与写 WARN）。 */
    suspend fun compressDetailed(
        messages: List<ChatMessage>,
        budgetTokens: Int,
    ): CompressionOutcome {
        if (messages.isEmpty()) return CompressionOutcome(messages, CompressionMode.NONE)

        val estimated = TokenEstimator.estimateMessages(messages)
        if (estimated <= budgetTokens) return CompressionOutcome(messages, CompressionMode.NONE)

        val l1 = delegate.compress(messages, budgetTokens)
        if (l1.size >= messages.size) {
            // L1 压不动（装不下 / 找不到安全切点）⇒ 不试 L2：L2 的插入点也来自 L1 的切点。
            AgentLogStore.warn(
                "layered compress: l1 could not cut (est=" + estimated + " budget=" + budgetTokens + ")"
            )
            return CompressionOutcome(l1, CompressionMode.L1, reason = "l1-cannot-cut")
        }

        val dropped = droppedPrefix(messages, l1)
        val droppedTokens = TokenEstimator.estimateMessages(dropped)
        if (dropped.isEmpty() || droppedTokens < summarizeMinTokens) {
            return CompressionOutcome(
                messages = l1,
                mode = CompressionMode.L1,
                reason = "below-min",
                droppedMessages = dropped.size,
                droppedTokens = droppedTokens,
            )
        }

        val summarizer = summarizer
            ?: return CompressionOutcome(
                messages = l1,
                mode = CompressionMode.L1,
                reason = "no-summarizer",
                droppedMessages = dropped.size,
                droppedTokens = droppedTokens,
            )

        val result = summarizeSafely(summarizer, dropped)
        if (result is SummaryResult.Failed) {
            return fallback(l1, dropped, droppedTokens, result.reason)
        }
        val text = (result as SummaryResult.Ok).text

        val parsed = parseStructuredSummary(text, summarizeMaxTokens)
        val summary = parsed.summaryOrNull
            ?: return fallback(l1, dropped, droppedTokens, parsed.rejectReason ?: "rejected")

        // 用解析结果重新渲染：把模型输出里的空白 / 折行归一，保证同一摘要逐字节稳定。
        val rendered = summary.render()
        val summaryTokens = TokenEstimator.estimate(rendered)
        val summaryMessage = ChatMessage(
            id = summaryIdOf(dropped),
            role = Role.USER,
            text = rendered,
        )
        // system 段必须仍在最前（摘要绝不能跑到 system 之前，更不能进 system）。
        val systemPrefix = l1.takeWhile { it.role == Role.SYSTEM }
        val body = l1.drop(systemPrefix.size)
        val out = systemPrefix + summaryMessage + body

        if (parsed.isTruncated) {
            AgentLogStore.warn("summary truncated: max=" + summarizeMaxTokens)
        }
        AgentLogStore.info(
            "layered compress: mode=L2 dropped=" + dropped.size +
                " droppedTokens=" + droppedTokens + " summaryTokens=" + summaryTokens
        )
        return CompressionOutcome(
            messages = out,
            mode = CompressionMode.L2,
            droppedMessages = dropped.size,
            droppedTokens = droppedTokens,
            summaryTokens = summaryTokens,
            summaryTruncated = parsed.isTruncated,
        )
    }

    /**
     * 调摘要端口，把「实现抛异常」也收敛成 [SummaryResult.Failed]。
     *
     * 两条例外：
     * - **真正的取消必须上抛**（用户点了停止）；
     * - 嵌套 `withTimeout` 抛出的 `TimeoutCancellationException` **不能**上抛 ——
     *   它会被外层 `checkActive()` 接走，把「摘要超时」升级成「整 run 取消」。
     *   判据：外层 Job 仍是 active ⇒ 这次取消来自内层超时，不是用户取消。
     */
    private suspend fun summarizeSafely(
        summarizer: ContextSummarizer,
        dropped: List<ChatMessage>,
    ): SummaryResult = try {
        summarizer.summarize(dropped, summarizeMaxTokens)
    } catch (cancel: CancellationException) {
        if (!currentCoroutineContext().isActive) throw cancel
        SummaryResult.Failed("nested-cancel")
    } catch (error: Exception) {
        SummaryResult.Failed("summarizer-error:" + error.javaClass.simpleName)
    }

    private fun fallback(
        l1: List<ChatMessage>,
        dropped: List<ChatMessage>,
        droppedTokens: Int,
        reason: String,
    ): CompressionOutcome {
        AgentLogStore.warn("layered compress: mode=L1_FALLBACK reason=" + reason)
        return CompressionOutcome(
            messages = l1,
            mode = CompressionMode.L1_FALLBACK,
            reason = reason,
            droppedMessages = dropped.size,
            droppedTokens = droppedTokens,
        )
    }

    /**
     * 入参中「被 L1 丢掉的那一段」（不含 SYSTEM）。
     *
     * 按**位置**切而不是按内容比对：L1 的契约是「返回入参的后缀（system 另置）」，
     * 所以被丢的一定是前缀。用 id 比对会在重复消息上切错，用内容比对是 O(n²)。
     */
    private fun droppedPrefix(
        input: List<ChatMessage>,
        kept: List<ChatMessage>,
    ): List<ChatMessage> {
        val keptBody = kept.filter { it.role != Role.SYSTEM }
        if (keptBody.isEmpty()) return input.filter { it.role != Role.SYSTEM }
        val anchor = keptBody.first()
        var start = -1
        for (index in input.indices) {
            if (input[index] === anchor) {
                start = index
                break
            }
        }
        if (start < 0) {
            for (index in input.indices) {
                if (input[index].id == anchor.id) {
                    start = index
                    break
                }
            }
        }
        if (start <= 0) return emptyList()
        return input.subList(0, start).filter { it.role != Role.SYSTEM }
    }

    /** 摘要消息的稳定派生 id：同一段被丢历史在任何一轮都得到同一个 id。 */
    private fun summaryIdOf(dropped: List<ChatMessage>): String =
        SUMMARY_ID_PREFIX + dropped.first().id + ":" + dropped.size

    companion object {
        /**
         * 摘要消息的 id 前缀。`sanitizeForProvider` 的 `canMerge` 必须对它放行
         * （`previous.id.startsWith(SUMMARY_ID_PREFIX)` ⇒ 不合并）。
         */
        const val SUMMARY_ID_PREFIX: String = "summary:"

        /** 默认触发门槛（token）。低于它做摘要不划算：一次调用换不回等价语义。 */
        const val DEFAULT_SUMMARIZE_MIN_TOKENS: Int = 2_000

        /** 判定一条消息是不是 L2 摘要（给 `canMerge` 用，避免相邻合并吞掉段头）。 */
        fun isSummaryMessage(message: ChatMessage): Boolean =
            message.id.startsWith(SUMMARY_ID_PREFIX)
    }
}
