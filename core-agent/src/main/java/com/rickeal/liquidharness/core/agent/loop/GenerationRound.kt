package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.context.sanitizeForProvider
import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolCallFragmentTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * 单轮生成：组装请求 → 收流 → 转发事件 → 失败重试一次 → 给出本轮结论。
 *
 * ## 与旧仓 `runGenerationRound`（`AgentRunner.kt:1425-1694`，270 行）的关系
 *
 * 引擎交互换成 `LlmEngine.generate` + `GenerationEvent`；旧仓的
 * 「生成失败 → **重建引擎** + 重试」整段删除 —— 远程没有引擎可重建
 * （`00-brief.md` A4：远程实现只能把重建写成 no-op，那就是空转判据）。
 * 取而代之的是「可重试错误 → 指数退避重试」。
 *
 * ## 重试判据（三条同时成立才重试）
 *
 * 1. `Failed.retryable == true`（协议属性，由 adapter 判定）；
 * 2. **本轮尚未向用户吐出任何正文** —— 已吐过正文还重试，用户会看到重复输出
 *    （`01-architecture.md` §六.1「超时（首字节后）不重试」的同一理由）；
 * 3. 重试次数未超 [maxRetries]。
 *
 * 另外「空输出」也走重试：模型既不调工具也不吐字，通常是一次抖动（旧仓实测：
 * 端侧偶发空流，重试一次就好）。两次都空 ⇒ 判失败，不许静默当成正常结束。
 *
 * @param maxRetries 重试次数上限。默认 1（任务口径：重试一次）。
 * @param backoff 退避注入点 —— 单测传 `{}` 即零等待，不必用虚拟时钟。
 */
class GenerationRound(
    private val maxRetries: Int = 1,
    private val backoff: suspend (attempt: Int) -> Unit = { attempt -> delay(500L * attempt) },
    private val termination: RunTermination = RunTermination(),
) {

    /** 跑完一轮。**保证返回时已 emit 终态事件**（`Terminated` 分支内已 emit `Failed`）。 */
    suspend fun run(ctx: TurnContext, systemText: String): RoundOutcome {
        var attempt = 0
        while (true) {
            val outcome = runAttempt(ctx, systemText, attempt)
            if (outcome != null) return outcome
            attempt += 1
        }
    }

    /** 一次尝试；返回 null 表示「已安排好重试，请再来一次」。 */
    private suspend fun runAttempt(ctx: TurnContext, systemText: String, attempt: Int): RoundOutcome? {
        ctx.checkActive()
        val textBefore = ctx.state.textLength()
        val reasoningBefore = ctx.state.reasoningLength()
        return when (val result = attemptOnce(ctx, buildMessages(ctx, systemText), attempt)) {
            is Attempt.Completed -> {
                // 「空输出」必须把思考通道算进去：只吐 thinking 的流不是空输出
                // （外审 P0-2：引擎侧已判「有内容」，内核侧不能判「空」）。
                val produced = (ctx.state.textLength() - textBefore) +
                    (ctx.state.reasoningLength() - reasoningBefore)
                when {
                    produced == 0 && result.calls.isEmpty() ->
                        if (prepareRetry(ctx, attempt, textBefore, reasoningBefore, EMPTY_OUTPUT)) null
                        else RoundOutcome.Terminated

                    result.calls.isEmpty() -> RoundOutcome.TextOnly(result.finishReason)
                    else -> RoundOutcome.ToolCalls(result.calls, result.finishReason)
                }
            }

            is Attempt.Retryable ->
                if (prepareRetry(ctx, attempt, textBefore, reasoningBefore, result.message)) null
                else RoundOutcome.Terminated

            is Attempt.Fatal -> {
                termination.emitFailed(ctx, result.message, result.cause)
                RoundOutcome.Terminated
            }
        }
    }

    /**
     * 组装本轮的请求消息：`system` 固定在最前（**id 稳定派生**），其后是工作副本。
     *
     * - `system` 的 id 用 `sys:<runId>` 而不是 `newId()`：每轮生成复用同一个 id，
     *   保证前缀字节稳定（旧仓 `ContextCompressor.kt:159-166` 的补丁消息用稳定派生 id
     *   是同一手法，但动机变了：那边是引擎水印去重，这边是保住 prompt cache）。
     * - 空消息在发送前过滤：部分端点遇到空 content 直接 400（`ChatMessage.isEmpty` 的 KDoc）。
     * - [sanitizeForProvider] 每次都过：工具组配对是 400 的直接原因，不能只在压缩后过。
     */
    private fun buildMessages(ctx: TurnContext, systemText: String): List<ChatMessage> {
        val system = ChatMessage(id = "sys:" + ctx.state.runId, role = Role.SYSTEM, text = systemText)
        val body = ctx.state.working().filterNot { it.isEmpty }
        return sanitizeForProvider(listOf(system) + body)
    }

    /** 单次尝试。**所有流式事件在这里转发**，事件顺序即 provider 的到达顺序。 */
    private suspend fun attemptOnce(
        ctx: TurnContext,
        messages: List<ChatMessage>,
        attempt: Int,
    ): Attempt {
        val fragments = ToolCallFragmentTracker()
        var finished: FinishReason? = null
        var failure: GenerationEvent.Failed? = null
        val request = GenerationRequest(
            messages = messages,
            tools = ctx.tools.specs(),
            sampling = ctx.request.sampling,
            endpoint = ctx.request.endpoint,
            // 同一 run 同一轮的重试共用一个值（attempt 不进 key），便于服务端日志关联。
            idempotencyKey = ctx.request.endpoint.id + "#" + ctx.state.runId + "#" + ctx.state.round,
        )
        try {
            ctx.engine.generate(request).collect { event ->
                ctx.checkActive()
                when (event) {
                    is GenerationEvent.TextDelta -> {
                        ctx.state.appendText(event.text)
                        ctx.emit(AgentEvent.TextDelta(event.text))
                    }

                    is GenerationEvent.ReasoningDelta -> {
                        // 思考也计入「已产出」：只计数不落历史（见 RunState.appendReasoning）。
                        ctx.state.appendReasoning(event.text)
                        ctx.emit(AgentEvent.ReasoningDelta(event.text))
                    }

                    is GenerationEvent.ToolCallDelta ->
                        fragments.onDelta(event.index, event.id, event.name, event.argumentsFragment)

                    is GenerationEvent.Usage -> {
                        ctx.state.recordUsage(event.prompt, event.completion, event.cachedPrompt)
                        ctx.emit(AgentEvent.UsageUpdated(event.prompt, event.completion, event.cachedPrompt))
                    }

                    is GenerationEvent.Finished -> finished = event.reason

                    is GenerationEvent.Failed -> failure = event
                }
            }
        } catch (c: CancellationException) {
            // 挂起点必须前置：`runCatching` / `catch (t: Throwable)` 吞掉它会让 Job
            // 从 cancelled 变 completed（AGENTS.md §五.4，旧仓真实事故）。
            throw c
        } catch (t: Throwable) {
            val message = "stream failed: " + (t.message ?: t::class.simpleName ?: "unknown")
            AgentLogStore.error(message + " (attempt=" + attempt + ")")
            return Attempt.Fatal(message, t)
        }

        failure?.let { failed ->
            return if (failed.retryable) Attempt.Retryable(failed.message)
            else Attempt.Fatal(failed.message, null)
        }

        val produced = fragments.snapshot()
        val reason = finished ?: truncationFallback(ctx, produced.isNotEmpty())
        return Attempt.Completed(reason, produced)
    }

    /**
     * 「有内容但无 `finish_reason`」= 被截断，不是正常结束（`AGENTS.md` §五.6）。
     *
     * 正常情况下 adapter 会补终帧（`ProviderAdapter.finish`）；这里再兜一层是因为
     * 半截回答被当完整回答落库是**静默缺陷** —— 不崩不报错，只是内容错。
     */
    private fun truncationFallback(ctx: TurnContext, hasToolCalls: Boolean): FinishReason? {
        val hasText = ctx.state.textLength() > 0
        if (!hasText && !hasToolCalls) return null
        AgentLogStore.warn("stream ended without finish_reason; treating as truncated (chars=" + ctx.state.textLength() + ")")
        return FinishReason.LENGTH
    }

    /**
     * 决定「重试还是放弃」。返回 true = 已 emit `Retrying` 并退避，调用方可以再试一次。
     *
     * 放弃的三条路径都会 emit `Failed`（**不许静默**）：次数用尽 / 已吐过正文 / 非可重试错误。
     */
    private suspend fun prepareRetry(
        ctx: TurnContext,
        attempt: Int,
        textBefore: Int,
        reasoningBefore: Int,
        reason: String,
    ): Boolean {
        if (attempt >= maxRetries) {
            termination.emitFailed(ctx, "giving up after " + attempt + " retries: " + reason)
            return false
        }
        // 已吐过正文**或思考**都不许重试：重发会让模型从头再答一遍，
        // 用户看到两段思考 / 两段正文拼接（旧仓 P1 级缺陷的同一形态）。
        if (ctx.state.textLength() != textBefore || ctx.state.reasoningLength() != reasoningBefore) {
            termination.emitFailed(ctx, "cannot retry after partial output: " + reason)
            return false
        }
        ctx.emit(AgentEvent.Retrying(reason))
        backoff(attempt + 1)
        return true
    }

    private sealed interface Attempt {
        /** 流正常结束（含「无 finish_reason 但有内容」的截断兜底）。 */
        data class Completed(val finishReason: FinishReason?, val calls: List<ToolCall>) : Attempt

        /** 可重试失败（由 adapter 判定 retryable）。 */
        data class Retryable(val message: String) : Attempt

        /** 不可重试失败 / 本地异常。 */
        data class Fatal(val message: String, val cause: Throwable?) : Attempt
    }

    private companion object {
        const val EMPTY_OUTPUT = "empty output"
    }
}

/**
 * 本轮生成的结论。
 *
 * 三态而不是 Boolean：`Terminated` 与 `TextOnly` 在旧仓曾共用「继续」语义，
 * 结果失败分支被静默当成正常收尾（`AgentRunner.kt:955-959` 的同类事故）。
 */
sealed interface RoundOutcome {

    /** 只出了文本，没有工具调用 ⇒ 本轮即终点。 */
    data class TextOnly(val finishReason: FinishReason?) : RoundOutcome

    /** 有工具调用（W2 起由 `ToolCallExecutor` 执行；W1 无工具时该分支不可达）。 */
    data class ToolCalls(val calls: List<ToolCall>, val finishReason: FinishReason?) : RoundOutcome

    /** 已 emit 终态事件，调用方直接返回 `Step.Terminated`。 */
    data object Terminated : RoundOutcome
}
