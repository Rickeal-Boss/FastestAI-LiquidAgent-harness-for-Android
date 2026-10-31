package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.approval.ToolApprovalCache
import com.rickeal.liquidharness.core.agent.prompt.AgentPromptComposer
import com.rickeal.liquidharness.core.model.AgentEvent

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
 * | 单轮生成 + 重试 | [GenerationRound]（内部经 [ContextBudget] 判预算） |
 * | 工具批执行 | [ToolCallExecutor]（W2 已接线） |
 * | 零调用收尾 | [RunTermination.emitFinished] |
 *
 * ## W2 的能力边界
 *
 * `RoundOutcome.ToolCalls` 分支**已接线**：执行整批 → `Step.Continue` 进下一轮；
 * 护栏熔断 ⇒ `emitBreaker` + `Step.Terminated`。循环的收敛由两处保证：轮次上限
 * （`policy.maxRounds`）与熔断（同参 / 失败连击 / 振荡），不在本类里做业务判断。
 */
class TurnLoop(
    private val gate: RoundHeadGate = RoundHeadGate(),
    private val generation: GenerationRound = GenerationRound(),
    private val termination: RunTermination = RunTermination(),
) {

    suspend fun run(ctx: TurnContext): Step {
        val systemText = systemTextFor(ctx)
        // 执行器按 run 创建：护栏计数（失败连击 / 同参）的作用域就是「这一次 run」。
        // 审批缓存**不跟着 run 走**：`ctx.approvalCache` 由调用方持有，`null` 才退化成
        // run 级实例 —— 「本会话不再询问」的语义必须跨 run 才成立（见 ToolApprovalFlow 类头）。
        val executor = ToolCallExecutor(ToolApprovalFlow(ctx.approvalCache ?: ToolApprovalCache()))
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
                    // 执行整批 → 下一轮。熔断信号是**返回值**（不是异常）：本类负责把它
                    // 翻译成终态事件，执行器不认识 `RunTermination`。
                    when (val executed = executor.execute(ctx, outcome.calls)) {
                        ToolCallExecutor.Outcome.Continue -> Unit
                        is ToolCallExecutor.Outcome.Tripped -> {
                            termination.emitBreaker(ctx, executed.detail)
                            return Step.Terminated
                        }
                    }
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
}
