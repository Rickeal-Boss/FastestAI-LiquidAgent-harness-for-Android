package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.model.BottleneckReport
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.TerminationReason
import com.rickeal.liquidharness.core.model.AgentEvent

/**
 * 一次 run 的**唯一收尾出口**。所有终态事件（`Finished` / `Failed` / `Cancelled`）都从这里发，
 * 别处不许自己 emit 终态。
 *
 * 为什么必须唯一：终态事件是 UI 的「停止流式」信号，也是 `TurnPersister` 的落库触发点。
 * 旧仓有 3 处 emit `Finished`（正常 / 轮次耗尽 / 熔断降级），其中一处的
 * `terminatedBy` 忘了填 ⇒ UI 把它当正常结束，用户看不到「已达最大轮次」的提示。
 * 收口到一处后，这类漂移在结构上不可能发生。
 */
class RunTermination {

    /**
     * 正常结束（含「模型自己停」与「轮次耗尽」两种 `terminatedBy`）。
     *
     * 截断证据（`AGENTS.md` §五.6）：`finishReason == LENGTH` 说明回答**可能不完整**。
     * 这里必须留证据 + 填诊断卡，否则半截回答会被当完整回答落库 —— 用户看到的是
     * 「答到一半突然结束」而没有任何提示。
     */
    suspend fun emitFinished(ctx: TurnContext, finishReason: FinishReason?) {
        val state = ctx.state
        val text = state.partialText()
        val report = if (finishReason == FinishReason.LENGTH) {
            val detail = "finish reason LENGTH: output may be truncated (chars=" + text.length + ")"
            AgentLogStore.warn(detail)
            BottleneckReport(
                kind = TRUNCATED_KIND,
                detail = detail,
                round = state.round,
                elapsedMillis = state.elapsedMillis(),
            )
        } else {
            null
        }
        ctx.emit(
            AgentEvent.Finished(
                text = text,
                rounds = state.round,
                usage = state.usage(),
                terminatedBy = TerminationReason.ModelStopped,
                report = report,
            )
        )
    }

    /**
     * 轮次耗尽：**不是失败**，是「任务可能未完成」。
     *
     * 与 [emitBreaker] 的分工：轮次耗尽说明模型还在正常工作，只是没收敛；
     * 熔断说明 harness 主动判定继续下去没有意义。两者给用户的文案完全不同。
     */
    suspend fun emitMaxRounds(ctx: TurnContext) {
        val state = ctx.state
        val detail = "max rounds reached: rounds=" + state.round + " limit=" + ctx.policy.maxRounds
        AgentLogStore.warn(detail)
        ctx.emit(
            AgentEvent.Finished(
                text = state.partialText(),
                rounds = state.round,
                usage = state.usage(),
                terminatedBy = TerminationReason.MaxRounds,
                report = BottleneckReport(
                    kind = MAX_ROUNDS_KIND,
                    detail = detail,
                    round = state.round,
                    elapsedMillis = state.elapsedMillis(),
                ),
            )
        )
    }

    /**
     * 熔断。`detail` 是证据串（英文，进日志与诊断卡），不是用户文案。
     *
     * 已收到的正文**不丢**：`Failed` 事件不带 text，但 `RunState` 里的文本仍完整，
     * UI 侧按「失败 + 已收到内容」渲染（`01-architecture.md` §六.1 传输超时那条：
     * 「响应中断，已保留已收到的内容」）。
     */
    suspend fun emitBreaker(ctx: TurnContext, detail: String) {
        ctx.emit(
            AgentEvent.Failed(
                message = detail,
                report = BottleneckReport(
                    kind = BREAKER_KIND,
                    detail = detail,
                    round = ctx.state.round,
                    elapsedMillis = ctx.state.elapsedMillis(),
                ),
                terminatedBy = TerminationReason.BreakerTripped,
            )
        )
    }

    /** 不可恢复的失败（协议错误 / 鉴权 / 配置错误 / 未知异常）。 */
    suspend fun emitFailed(ctx: TurnContext, message: String, cause: Throwable? = null) {
        AgentLogStore.error("run failed: " + message)
        ctx.emit(AgentEvent.Failed(message = message, cause = cause))
    }

    /**
     * 用户取消。**必须保留已收到的正文**（`partialText`）—— 这是取消语义的第 2 条硬契约：
     * 用户点停止后看到半截回答是预期的，看到空白则意味着内容被丢了。
     *
     * ⚠️ 调用方必须在 `withContext(NonCancellable)` 里调它：取消后普通挂起调用会
     * 立即再抛 `CancellationException`，收尾逻辑根本执行不到（旧仓 Wave2 缺陷，
     * `AgentRunner.kt:522-537` 那段 KDoc 是真实事故换来的）。这一条由
     * [com.rickeal.liquidharness.core.agent.AgentRunner] 保证。
     */
    suspend fun emitCancelled(ctx: TurnContext) {
        val text = ctx.state.partialText()
        AgentLogStore.info("run cancelled: chars=" + text.length + " rounds=" + ctx.state.round)
        ctx.emit(AgentEvent.Cancelled(partialText = text))
    }

    private companion object {
        /** 诊断卡 kind 常量。UI 按它选文案，所以必须是稳定字符串（不是类名反射）。 */
        const val TRUNCATED_KIND = "Truncated"
        const val MAX_ROUNDS_KIND = "MaxRounds"
        const val BREAKER_KIND = "BreakerTripped"
    }
}
