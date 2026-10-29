package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.AgentRequest
import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.model.AgentEvent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 一次 run 的全部可变状态与不可变输入的**唯一**载体。
 *
 * ## 存在理由（这是本仓最重要的一处设计决定）
 *
 * 旧仓 `AgentRunner` 2838 行拆不动的根因**不是文件太长**，是传参范式：
 * 每个方法都是 `private suspend fun FlowCollector<AgentEvent>.xxx(10~12 个参数)`，
 * 抽任何一个方法出来都要把全部参数再传一遍（实测调用点 `AgentRunner.kt:960-972`
 * 有 11 个实参）⇒ 谁都抽不出去，只能往同一个方法里继续加分支。
 *
 * 新仓铁律（`00-brief.md` A3）：**任何方法参数 ≤ 5 个，超出的一律进 TurnContext**。
 * 于是方法签名恒为 `(ctx) -> Step`，拆分不再需要「先造接缝」。
 *
 * ## 为什么 [emit] 是构造参数而不是 `FlowCollector` 接收者
 *
 * 旧仓把事件收集器当接收者（`FlowCollector<AgentEvent>.xxx(...)`），导致每个方法都
 * 绑定在收集器上、只能从 `collect { }` 内部调用。改成函数字段后，`loop/` 里任何类都能
 * 发事件，且单测可以传 `{ events += it }` 直接断言事件序列（不需要起 Flow）。
 *
 * 参数是 `suspend (AgentEvent) -> Unit` 而不是 `FlowCollector`：收集器是 Flow 的实现细节，
 * 内核只需要「发出去」这一个动作。
 */
class TurnContext(
    /** 不可变输入。 */
    val request: AgentRequest,
    /** 本次 run 的策略（数字全在这里，`loop/` 内不许出现魔数）。 */
    val policy: AgentPolicy,
    /** 引擎端口。内核不知道它是 HTTP 还是别的什么。 */
    val engine: LlmEngine,
    /** 工具注册中心。W1 无工具时为空注册表，但工具面从第一天就存在。 */
    val tools: ToolRegistry,
    /** 可变运行状态（轮次 / token / 已收文本 / 取消标记）。 */
    val state: RunState,
    /** 事件出口。**内核发出的每一个 AgentEvent 都必须经过它**，便于单测断言序列。 */
    val emit: suspend (AgentEvent) -> Unit,
) {

    /**
     * 取消检查点。**所有协程切换后、所有工具执行前必须先调它。**
     *
     * 用 `ensureActive()` 而不是自查标记：它同时覆盖「Job 被取消」与「线程被中断」，
     * 且抛的是标准 `CancellationException`，不会像布尔标记那样被 `runCatching` 静默吞掉
     * （`AGENTS.md` §五.4）。
     */
    suspend fun checkActive() {
        currentCoroutineContext().ensureActive()
        if (state.cancelled) {
            // 取消标记由 `AgentRunner.cancel()` 置位：`stop()` 之后即便 Job 还没被取消，
            // 也不许再进下一轮（用户点停止 → 引擎断开 → 事件流可能还剩几帧）。
            throw kotlinx.coroutines.CancellationException("run cancelled")
        }
    }
}

/**
 * 每一步的返回值。**用 sealed 而不是 Boolean** —— 旧仓纪律：
 * 「Boolean → sealed 是消灭静默失败的范式」（知识库 §5）。
 *
 * 旧仓真实事故：`ToolCallStep` 从二态扩到三态时，`if (== NextCall) continue`
 * 把 `Terminal` 静默落成了 `Proceed`，编译不报错，行为回归（`AgentRunner.kt:955-959`）。
 * sealed 的 `when` 在新增分支时强制编译报错，这是它唯一的、也是足够的价值。
 */
sealed interface Step {

    /** 本轮结束，进下一轮。 */
    data object Continue : Step

    /** 同一轮内继续（如：一批工具里还有下一个）。 */
    data object Proceed : Step

    /** 正常终止，**已 emit `Finished`**。 */
    data object Done : Step

    /** 异常终止，**已 emit `Failed` / `Cancelled`**。 */
    data object Terminated : Step
}
