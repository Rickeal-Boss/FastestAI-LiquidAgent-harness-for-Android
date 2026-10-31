package com.rickeal.liquidharness.core.agent

import com.rickeal.liquidharness.core.agent.approval.ToolApprovalCache
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalHandler
import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.agent.loop.RunState
import com.rickeal.liquidharness.core.agent.loop.RunTermination
import com.rickeal.liquidharness.core.agent.loop.Step
import com.rickeal.liquidharness.core.agent.loop.TurnContext
import com.rickeal.liquidharness.core.agent.loop.TurnLoop
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.TerminationReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicReference

/**
 * 内核门面。**硬指标：本文件 ≤ 200 行**（`00-brief.md` A3）。
 *
 * 旧仓 `AgentRunner.kt` 实测 **2838 行**，守卫上限 2900 —— 接一个新能力就踩线。
 * 本类只做四件事，任何第五件事都应该落到 `loop/` 下：
 *
 * 1. **串行化**：一条会话的 run 用 [runMutex] 互斥；
 * 2. **装配** [TurnContext] 并交给 [TurnLoop]；
 * 3. **取消收尾**：用户取消后补发 `Cancelled(partialText)`；
 * 4. **状态广播**：[isBusy] 供 UI 禁用发送按钮。
 *
 * ## 为什么是 `channelFlow` 而不是 `flow`（这是单测逼出来的，别改回去）
 *
 * 两个硬约束同时存在：
 *
 * 1. **取消必须在子协程里跑**。若 run 直接跑在收集方协程里，`cancel()` 取消的就是
 *    收集方本身 —— 而 `Cancelled(partialText)` 恰恰要发给这个收集方。取消订阅者
 *    再给它发消息是自相矛盾的。所以循环跑在 `launch { }` 的子 job 里，`cancel()`
 *    只取消子 job，生产者协程仍然活着。
 * 2. **`SafeCollector` 禁止跨协程 emit**。普通 `flow { }` 的收集器带「发射必须在
 *    同一协程」的不变量：子 job 里 `emit` 会直接抛
 *    `IllegalStateException: Flow invariant is violated`；`withContext(NonCancellable)`
 *    也不行（NonCancellable 的 Job 既不是收集协程本身也不是它的子）。实测在
 *    `AgentRunnerTest.cancelEmitsCancelledWithPartialText` 上炸过。
 *
 * `channelFlow` 的 `send` 天生允许从任意子协程发（它内部就是一个 Channel），
 * 两个约束同时满足。旧仓那段「取消后必须 `withContext(NonCancellable)` 收尾」的
 * 教训依然有效，但适用面变了：**emit 走生产者协程，NonCancellable 只留给不 emit 的
 * 副作用**（journal 落 settled 行，W3 接入时用）。
 *
 * ## 为什么需要 runMutex（理由与旧仓不同）
 *
 * 旧仓的约束来自「native 引擎 use-after-free」—— 远程没有引擎生命周期，那条理由失效。
 * 但约束**保留**，理由换成数据竞争：两条 run 并发写同一份 `working` 与 journal 会交错，
 * 而 `RunState` 明确是「单协程访问、不加锁」（`01-architecture.md` §6.3）。
 *
 * 用 `tryLock` 而不是 `withLock`：并发请求应当**立刻**得到 `GateRejected` 的明确失败，
 * 而不是排队等待 —— 排队会让用户点两次发送后看到一个「迟到的回答」。
 */
class AgentRunner(
    private val engine: LlmEngine,
    private val tools: ToolRegistry = ToolRegistry(),
    private val policy: AgentPolicy = AgentPolicy(),
    private val loop: TurnLoop = TurnLoop(),
    private val termination: RunTermination = RunTermination(),
) {

    private val runMutex = Mutex()

    /** 在途 run 的句柄。`null` = 空闲。用于 [cancel] 定位「取消谁」。 */
    private val activeRun = AtomicReference<ActiveRun?>(null)

    private val busyState = MutableStateFlow(false)

    /** 是否有在途 run。UI 用它禁用发送按钮。 */
    val isBusy: Boolean get() = busyState.value

    /** 可订阅的忙碌状态（Compose 侧 `collectAsState()` 用）。 */
    val busy: StateFlow<Boolean> = busyState.asStateFlow()

    /**
     * 开一次 run。冷流：`collect` 才真正开始（不 collect 不花钱，与引擎契约一致）。
     *
     * [approval] 按 run 传入（不进构造参数，理由见 `ToolApprovalHandler` 的 KDoc）；
     * `null` ⇒ 需要审批的工具一律拒绝（fail-closed）。
     *
     * [approvalCache] 是「本会话不再询问」的授权存储。`null` ⇒ 本 run 内新建一个（范围 = 这次
     * run，单测 / 无宿主场景的默认）；宿主传**会话级**实例 ⇒ 跨 run 生效，与 UI 文案一致。
     * 生命周期归调用方，**不得**挂进程级单例（会把上一个会话的授权带进下一个）。
     *
     * `.buffer(BUFFERED)`：收集方退后台时 `send` 会挂起，突发事件（工具批）会被卡住。
     * 缓冲吸收突发，配合 `ToolApprovalFlow` 的投递超时一起兜「审批卡发不出去」。
     *
     * 终态事件由 [TurnLoop] 内部经 [RunTermination] 发出；本类只在**异常与取消路径**补发，
     * 因此正常路径不会出现两条 `Finished`。
     */
    fun run(
        request: AgentRequest,
        approval: ToolApprovalHandler? = null,
        approvalCache: ToolApprovalCache? = null,
    ): Flow<AgentEvent> = channelFlow {
        if (!runMutex.tryLock()) {
            // fail-closed：并发请求明确拒绝，不排队（见类头 KDoc）。
            send(AgentEvent.Failed(message = BUSY_MESSAGE, terminatedBy = TerminationReason.GateRejected))
            return@channelFlow
        }

        val state = RunState.forRequest(request)
        val ctx = TurnContext(
            request = request,
            policy = policy,
            engine = engine,
            tools = tools,
            state = state,
            approval = approval,
            emit = { event -> send(event) },
            approvalCache = approvalCache,
        )
        busyState.value = true

        // LAZY + 先登记再 start：避免「子 job 已经开始跑、cancel() 还看不到它」的竞态。
        val job = launch(start = CoroutineStart.LAZY) { executeLoop(ctx) }
        activeRun.set(ActiveRun(state, job))
        try {
            job.start()
            job.join()
            // 子 job 已结束且**生产者协程仍活跃**（收集方没走），此处发 Cancelled 合法。
            if (state.cancelled) termination.emitCancelled(ctx)
        } finally {
            activeRun.set(null)
            busyState.value = false
            runMutex.unlock()
        }
    }.buffer(Channel.BUFFERED)

    /**
     * 循环体。异常分类只做三件事：取消标记 / 失败事件 / 让出。
     *
     * 注意 `catch (c: CancellationException)` 必须前置：`catch (t: Throwable)` 吞掉它
     * 会让 Job 从 cancelled 变 completed（`AGENTS.md` §五.4，旧仓真实事故）。
     */
    private suspend fun executeLoop(ctx: TurnContext) {
        try {
            when (loop.run(ctx)) {
                Step.Done, Step.Terminated -> Unit
                // 不可达（TurnLoop 的所有分支都返回终态）；写成显式失败而不是静默收尾。
                Step.Continue, Step.Proceed ->
                    termination.emitFailed(ctx, "loop exited without a terminal state")
            }
        } catch (c: CancellationException) {
            ctx.state.markCancelled()
            throw c
        } catch (t: Throwable) {
            termination.emitFailed(ctx, describe(t), t)
        }
    }

    /**
     * 主动停止。幂等；无在途 run 时 no-op。
     *
     * 顺序是**先置状态标记、再取消子 job、最后断连接**：
     * 1. 置标记 ⇒ 即便 job 还没来得及取消，轮头检查点也不会放行下一轮；
     * 2. 取消子 job ⇒ 打断挂起点（退避 `delay` 等）；只取消子 job，**不动收集方**；
     * 3. [LlmEngine.stop] ⇒ 断开底层阻塞读（只取消协程不会让 `readLine()` 立刻返回，
     *    这是旧仓「点停止毫无反应」的根因，`AGENTS.md` §五.5）。
     */
    suspend fun cancel() {
        activeRun.get()?.let { active ->
            active.state.markCancelled()
            active.job?.cancel(CancellationException(CANCELLED_MESSAGE))
        }
        engine.stop()
    }

    /** 一次在途 run 的句柄。 */
    private class ActiveRun(val state: RunState, val job: Job?)

    private companion object {
        /** ASCII 常量（守卫 N3）；用户文案由 `:app` 按 `TerminationReason` 映射。 */
        const val BUSY_MESSAGE = "another run is already in progress"
        const val CANCELLED_MESSAGE = "cancelled by user"

        fun describe(t: Throwable): String =
            "run failed: " + (t.message ?: t::class.simpleName ?: "unknown error")
    }
}
