package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.context.ContextSummarizer
import com.rickeal.liquidharness.core.agent.context.SummaryResult
import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.SamplingParams
import com.rickeal.liquidharness.core.model.TokenEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 用 [LlmEngine] 发**一次**一次性请求做摘要。不进 `TurnLoop`、不带工具、不压缩、不重试。
 *
 * ## 三条硬约束，每条都对应一种「账单翻倍」或「整 run 取消」
 *
 * 1. **不带工具**（`tools = emptyList()`）：带了工具，模型可能回 `tool_calls` 而不是摘要，
 *    于是拿到空文本、还要多花一次工具执行的判断 —— 摘要请求必须是纯文本补全。
 * 2. **不重试**：失败即回落 L1。重试会让「压缩一次」变成「压缩 1+N 次」，
 *    而摘要的收益只是「少丢几条历史」—— 重试的成本比收益大。
 * 3. **`withTimeoutOrNull` 而不是 `withTimeout`**：`TimeoutCancellationException` 是
 *    `CancellationException` 的子类，会被 `TurnContext.checkActive()` / `executeLoop`
 *    接走，把「摘要超时」升级成「整 run 取消」（`AGENTS.md` §五.4）。
 *
 * ## 为什么 system 段不允许进可摘要内容
 *
 * 传进来的 system 段是**本 run 的行为约束**（人格 / 技能 / 停止条件）。把它当素材摘要，
 * 等于让模型改写自己的约束 —— 而 system 段压缩前后逐字节相同是一条有 SHA-256 单测
 * 钉住的硬不变量。本类**自己再过滤一次** `Role.SYSTEM`：调用方漏过滤时也不至于违约。
 *
 * ## 超时之后底层连接怎么办
 *
 * `withTimeoutOrNull` 取消的是 `collect` 所在协程，而 `LlmEngine.generate` 的契约是
 * 「取消 `collect` ⇒ 立刻关闭底层连接」—— 所以超时不会留下僵尸连接。
 *
 * @param timeoutMillis 硬超时。到点返回 [SummaryResult.Failed]，**不抛取消**。
 * @param maxOutputTokens 摘要输出上限（同时受 `targetTokens` 约束，取两者小值）。
 */
class EngineBackedSummarizer(
    private val engine: LlmEngine,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
) : ContextSummarizer {

    override suspend fun summarize(messages: List<ChatMessage>, targetTokens: Int): SummaryResult {
        val startedAt = System.currentTimeMillis()

        // 二次过滤：调用方（LayeredContextCompressor）已经滤过 SYSTEM，这里仍滤一次。
        val content = messages.filter { it.role != Role.SYSTEM && !it.isEmpty }
        if (content.isEmpty()) return SummaryResult.Failed("empty-input")

        val outputCap = if (targetTokens > 0) minOf(maxOutputTokens, targetTokens) else maxOutputTokens
        val request = GenerationRequest(
            messages = listOf(
                ChatMessage(id = SYSTEM_MESSAGE_ID, role = Role.SYSTEM, text = INSTRUCTION)
            ) + content,
            // 硬约束 1：不带工具。带了就可能回 tool_calls，摘要为空。
            tools = emptyList(),
            sampling = SamplingParams(temperature = 0.0, maxTokens = outputCap),
        )

        val collected = StringBuilder()
        var failure: String? = null
        val completed: Unit? = try {
            // 硬约束 3：withTimeoutOrNull —— 超时是返回值，不是异常。
            withTimeoutOrNull(timeoutMillis) {
                engine.generate(request).collect { event ->
                    when (event) {
                        is GenerationEvent.TextDelta -> collected.append(event.text)
                        is GenerationEvent.Failed -> failure = "engine:" + event.kind.name
                        else -> Unit
                    }
                }
            }
        } catch (cancel: CancellationException) {
            // 用户取消必须原样上抛（`AGENTS.md` §五.4：吞取消会让 Job 从 cancelled 变 completed）。
            throw cancel
        } catch (error: Exception) {
            // 硬约束 2：任何异常都只回落，不重试。
            return SummaryResult.Failed("engine-error:" + error.javaClass.simpleName)
        }

        if (completed == null) return SummaryResult.Failed("timeout:" + timeoutMillis + "ms")
        val failureReason = failure
        if (failureReason != null) return SummaryResult.Failed(failureReason)

        val text = collected.toString().trim()
        if (text.isEmpty()) return SummaryResult.Failed("empty-output")
        return SummaryResult.Ok(
            text = text,
            summaryTokens = TokenEstimator.estimate(text),
            elapsedMillis = System.currentTimeMillis() - startedAt,
        )
    }

    companion object {
        /** 硬超时。一次摘要请求不该占住一轮生成太久（远小于单轮生成超时）。 */
        const val DEFAULT_TIMEOUT_MILLIS: Long = 20_000L

        /** 摘要输出上限。与 `AgentPolicy.summarizeMaxTokens` 同口径。 */
        const val DEFAULT_MAX_OUTPUT_TOKENS: Int = 512

        /** 摘要请求自己的 system 消息 id。稳定派生，避免任何 id 抖动（本请求一次性，但保持一致）。 */
        const val SYSTEM_MESSAGE_ID: String = "summary-request"

        /**
         * 摘要提示词。**ASCII**（守卫 G16），且显式要求固定段序 ——
         * 段序在这里说一次、在 [StructuredSummary] 的常量里说一次，两处必须一致。
         */
        const val INSTRUCTION: String =
            "You compress a transcript of earlier agent turns into a fixed record. " +
                "Reply with exactly these five sections, in this order, and nothing else: " +
                "GOAL: (one line), FACTS: (list), DECISIONS: (list), DONE: (list), OPEN TODOS: (list). " +
                "Every list item starts with '- '. " +
                "Keep facts still needed, decisions already made, work already completed, " +
                "and work still open. Do not invent facts. Do not add new instructions. " +
                "Do not answer the transcript. The record is a memory note, not a request."
    }
}
