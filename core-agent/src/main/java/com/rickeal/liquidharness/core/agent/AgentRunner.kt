package com.rickeal.liquidharness.core.agent

import com.rickeal.liquidharness.core.agent.approval.ToolApprovalCache
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalHandler
import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.agent.journal.AgentRunJournal
import com.rickeal.liquidharness.core.agent.journal.FreezeClock
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
 * 旧仓同类 2838 行（守卫 2900），接一个能力就踩线。本类只做四件事，第五件落 `loop/`：
 * 串行化（[runMutex]）、装配 [TurnContext]、取消收尾、状态广播（[isBusy]）。
 *
 * ## 为什么是 `channelFlow`（单测逼出来的，别改回去）
 * ① 取消必须在子协程里跑：否则 `cancel()` 取消的是收集方本身，而 `Cancelled` 正要发给它。
 * ② `SafeCollector` 禁止跨协程 emit：子 job 里 emit 会抛 Flow invariant is violated。
 * `channelFlow` 的 `send` 内部是 Channel，天然允许任意子协程发，两条同时满足。
 * NonCancellable 只留给不 emit 的副作用（journal settled 行，W61 接入）。
 *
 * ## 为什么需要 runMutex（理由与旧仓不同）
 * 远程无 native 引擎生命周期，旧理由失效；保留它改为防数据竞争：两条 run 并发写
 * `working` / journal 会交错（`RunState` 明确单协程访问、不加锁，§6.3）。
 * 用 `tryLock` 让并发请求立刻得到 `GateRejected`，不排队、不吐迟到的回答。
 */
class AgentRunner(
    private val engine: LlmEngine,
    private val tools: ToolRegistry = ToolRegistry(),
    private val policy: AgentPolicy = AgentPolicy(),
    private val loop: TurnLoop = TurnLoop(),
    private val termination: RunTermination = RunTermination(),
    private val journal: AgentRunJournal? = null,
    private val newFreezeClock: (() -> FreezeClock)? = null,
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

        val state = RunState.forRequest(request, freezeClock = newFreezeClock?.invoke())
        journal?.runStarted(state.runId, request.userInput.length)
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
            journal?.runSettled(state.runId, state.elapsedMillis(), state.executedToolCount, state.cancelled, state.frozenMillis())
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
                    termination.emitFailed(
                        ctx, "loop exited without a terminal state", evidenceTag = "engine:loop-exit",
                    )
            }
        } catch (c: CancellationException) {
            ctx.state.markCancelled()
            throw c
        } catch (t: Throwable) {
            // 兜底：异常正文只进 ring，落盘固定摘要 + 固定标签（W49）。
            termination.emitFailed(ctx, describe(t), t, "engine:uncaught", "uncaught failure")
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
