package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.prompt.AgentPromptComposer
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.ToolCall

/**
 * 主循环：**纯控制流**，不含任何内联业务逻辑。
 *
 * ## 纪律（`01-architecture.md` §四 验收判据 3）
 *
 * `while` 体内零 `if` 嵌套深度 > 2。任何一个分支超过「一个 `when` 分支 + 一次调用」
 * 就应该拆成独立类 —— 旧仓 `executeBodyUnchecked` 464 行就是这么长出来的。
 *
 * ## 与旧仓 `executeBodyUnchecked`（`AgentRunner.kt:541-1004`）的对应关系
 *
 * | 旧仓步骤 | 新仓落点 |
 * |---|---|
 * | 组装 system 提示词（循环外一次） | [systemTextFor] |
 * | 轮头闸门 | [RoundHeadGate] |
 * | 单轮生成 + 重试 | [GenerationRound] |
 * | 工具批执行 | W2 `ToolCallExecutor`（W1 未接线） |
 * | 零调用收尾 | [RunTermination.emitFinished] |
 *
 * ## W1 的能力边界（如实申报）
 *
 * W1 只跑「纯文本 + 无工具」：`RoundOutcome.ToolCalls` 分支**不可达**（请求里
 * `tools = []`，模型没有可调用的工具）。它仍然存在且显式处理，是为了让 W2 接
 * `ToolCallExecutor` 时只改这一处，而不是「先留个 TODO 空分支」——
 * 空分支在 W2 之前是死代码，但它有确定的收尾行为（跳过 + 正常结束），不是空转。
 */
class TurnLoop(
    private val gate: RoundHeadGate = RoundHeadGate(),
    private val generation: GenerationRound = GenerationRound(),
    private val termination: RunTermination = RunTermination(),
) {

    suspend fun run(ctx: TurnContext): Step {
        val systemText = systemTextFor(ctx)
        while (true) {
            ctx.checkActive()

            when (val verdict = gate.check(ctx)) {
                is GateVerdict.Trip -> {
                    termination.emitBreaker(ctx, verdict.detail)
                    return Step.Terminated
                }

                GateVerdict.Proceed -> Unit
            }

            if (ctx.state.round >= ctx.policy.maxRounds) {
                termination.emitMaxRounds(ctx)
                return Step.Done
            }
            val round = ctx.state.beginRound()
            ctx.emit(AgentEvent.RoundStarted(round, ctx.policy.maxRounds))

            when (val outcome = generation.run(ctx, systemText)) {
                is RoundOutcome.Terminated -> return Step.Terminated

                is RoundOutcome.TextOnly -> {
                    termination.emitFinished(ctx, outcome.finishReason)
                    return Step.Done
                }

                is RoundOutcome.ToolCalls -> {
                    // W2 接 ToolCallExecutor 后本分支改成「逐个执行 → Step.Continue」。
                    skipUnwiredToolCalls(ctx, outcome.calls)
                    termination.emitFinished(ctx, outcome.finishReason)
                    return Step.Done
                }
            }
        }
    }

    /**
     * 本 run 的 system 文本。**每 run 只渲染一次**，结果缓存进 [RunState.systemText]。
     *
     * 缓存不是性能优化，是正确性：一旦每轮重渲染，任何一处提示词内容的抖动（技能元数据
     * 按最近使用排序、记忆正文变化）都会让整段前缀的 prompt cache 失效
     * （`00-brief.md` A8 硬规则 1）。
     */
    private fun systemTextFor(ctx: TurnContext): String {
        ctx.state.systemText?.let { return it }
        val text = AgentPromptComposer.compose(ctx.request, ctx.tools.specs())
        ctx.state.systemText = text
        return text
    }

    /**
     * 工具执行器未接线时的显式跳过。
     *
     * 先发 [AgentEvent.ToolCallStarted] 再发 [AgentEvent.ToolSkipped]：UI 侧对
     * `ToolSkipped` 是「**只更新不新建**」（`AGENTS.md` §五.3）—— 没有轨迹创建点就先发
     * skip，UI 会拿到一个不存在的调用的更新，表现是「什么都没发生」。
     */
    private suspend fun skipUnwiredToolCalls(ctx: TurnContext, calls: List<ToolCall>) {
        AgentLogStore.error("model requested tools but no executor is wired; skipping " + calls.size + " call(s)")
        for (call in calls) {
            val tool = ctx.tools.get(call.name)
            if (tool != null) ctx.emit(AgentEvent.ToolCallStarted(call, tool.spec))
            ctx.emit(AgentEvent.ToolSkipped(call, SKIP_REASON))
        }
    }

    private companion object {
        /** ASCII 常量：`core-*` 模块零中文字符串字面量（守卫 N3）。 */
        const val SKIP_REASON = "tool execution is not wired in this build"
    }
}
