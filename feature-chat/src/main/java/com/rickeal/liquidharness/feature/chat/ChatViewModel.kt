package com.rickeal.liquidharness.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rickeal.liquidharness.core.agent.AgentRequest
import com.rickeal.liquidharness.core.agent.AgentRunner
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalCache
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalDecision
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalHandler
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 端点未配置时的错误码。上屏文案由 `strings.xml` 按这个 key 映射（守卫 G16）。 */
internal const val NO_ENDPOINT = "no endpoint configured"

/**
 * 对话页 ViewModel。
 *
 * 职责只有三件：**组装请求**、**跑 run**、**把事件喂给归约器**。
 * 归约本身在 [EventReducer]（纯函数），所以本类可以很短 —— 旧仓同名类 901 行，
 * 一半是归约、一半是副作用，改一处要读全文。
 *
 * ## 流式节流为什么在这里而不是归约器里
 *
 * 节流是**调度问题**（多久刷一次 UI），不是**语义问题**（事件意味着什么）。
 * 放进归约器会让「窗口多大」变成不可单独调整的硬编码，也会让归约器的单测必须
 * 携带时间维度。这里用 120ms 合并窗口：一帧 16.7ms 的 7 倍，肉眼无感，
 * 但把 100 tok/s 的重组次数从 100 次/秒降到 8 次/秒。
 */
class ChatViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var activeRunner: AgentRunner? = null

    /**
     * 待刷新的流式文本。只在主线程访问（`viewModelScope` 默认 Main），
     * 因此 [StreamFlushBuffer] 自身不加锁。
     */
    private val flushBuffer = StreamFlushBuffer()
    private var flushJob: Job? = null

    /**
     * run 代次。`newConversation()` 取消在途 run 后递增，旧 run 的迟到事件全部丢弃 ——
     * 否则「新对话」清空列表后，上一条 run 的 `Cancelled(partialText)` 会把半截回答
     * 追加到空白会话里。
     */
    private var runGeneration = 0

    /**
     * 在途审批的等待句柄。`null` = 当前没有任何工具卡在审批上。
     *
     * 与 [flushBuffer] 同款约定：只在主线程访问（`viewModelScope` 默认 Main，
     * 审批回调也跑在同一条 run 的协程链上）。
     *
     * 它是 [ChatUiState.pendingApproval] 的**配对物**：卡片能画出来 ⇔ 有一个真的
     * 在 `await()` 的句柄。两者一旦分家就会出现「点了没反应的卡」。
     */
    private var pendingApprovalDeferred: CompletableDeferred<ToolApprovalDecision>? = null

    /**
     * 会话级审批授权（「本会话允许相同调用」）。
     *
     * ## 为什么必须由 ViewModel 持有，而不是让内核每次 run 自建
     *
     * 内核的 `ToolApprovalFlow` 是**一次 run 一个实例**，它的 `ToolApprovalCache` 也随
     * 实例生灭 ⇒ 用户点了「本会话允许相同调用」，下一个 run 里同一个调用还会再弹一次卡。
     * 卡上的文案说「本会话」，行为却是「本 run」—— 这是**界面在说谎**，比没有这个按钮更糟：
     * 用户会学会「反正每次都要点」，审批闸门就退化成形式。
     *
     * 现在缓存挂在 ViewModel 上（生命周期 = 一段对话），经
     * `runner.run(..., approvalCache = approvalCache)` 注入内核。作用域边界只有一个：
     * [newConversation] 时 [ToolApprovalCache.clear] —— 「新对话 = 新会话授权边界」。
     *
     * ⚠️ 刻意**不**在 [stop] 里清：停止一次 run 不是撤销授权，用户下一条消息里
     * 同一个 `file_write` 仍应免打扰。要收回授权就开新对话。
     *
     * 线程模型见 [ToolApprovalCache] 类头（读无锁、写 synchronized）；本字段只在
     * 主线程被读写，不需要额外同步。
     */
    private val approvalCache = ToolApprovalCache()

    init {
        viewModelScope.launch { refreshEndpoint() }
    }

    /**
     * 重新读取活跃端点。
     *
     * 从设置页返回时必须调：换了端点 / 改了模型名后，顶栏、上下文窗口与 `canSend`
     * 都依赖它，不重读就会出现「顶栏显示旧模型、请求已经打到新端点」的错觉。
     */
    suspend fun refreshEndpoint() {
        val endpoint = container.activeEndpoint()
        _state.update {
            it.copy(
                endpointLabel = endpoint?.let(::labelOf),
                contextLimit = endpoint?.contextLength ?: 0,
            )
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /**
     * 把用户在审批卡上的选择送回内核。
     *
     * 三件事的顺序是**先清句柄 → 再清 state → 最后 complete**：
     * - 先清句柄 ⇒ 同一帧内的第二次点击（快速双击 / 无障碍重复触发）走 [return]，
     *   既不会二次 complete，也不会把**下一次**审批的句柄当成自己的（幂等）。
     * - **清 state 必须在 complete 之前**：`viewModelScope` 是 `Main.immediate`，
     *   `complete()` 可能**同步**恢复内核协程 —— 内核接着发下一个 `ApprovalRequested`
     *   （连续工具调用），新卡已经写进 state；此时再清就会把新卡一起抹掉，
     *   用户看到「点了一次允许，第二张卡闪一下就没了」。
     */
    fun resolveApproval(decision: ToolApprovalDecision) {
        val deferred = pendingApprovalDeferred ?: return
        pendingApprovalDeferred = null
        _state.update { it.copy(pendingApproval = null) }
        deferred.complete(decision)
    }

    /**
     * 开一段新对话。在途 run 先取消，迟到事件由 [runGeneration] 挡掉。
     *
     * ## 为什么必须连节流缓冲一起清
     *
     * 只 `runGeneration++` 只挡得住 `collect { event -> ... }` 这条路径 —— 节流循环
     * （[ensureFlushLoop]）跑在 `viewModelScope` 上，**不经过代次门**。复现时序：
     * 最后一个 `TextDelta` 进缓冲 → 120ms 窗口内点「新对话」→ state 已重置为空白会话
     * → `flushJob` 醒来把旧 run 的残段 append 进新会话。`appendText` 还会把
     * `isStreaming` 置 true，而当前代次已没有任何 run 会送终态事件 ⇒ `canSend` 恒 false，
     * 输入框卡死。这违反 [EventReducer] 的不变量 #1（在途文本只有一个真源）。
     *
     * 只 cancel 不 clear 也不够：`cancel()` 是**异步**的，`flushJob` 可能已经越过
     * `delay` 停在 `_state.update` 前，取消对它不再生效。所以这里 cancel、置空、
     * 清缓冲三件一起做；再加 [flushNow] 的代次守卫兜底（见其 KDoc）。
     *
     * ## 测试覆盖
     *
     * 本方法涉及的跨代次行为**未做自动化测试**：本模块测试源集没有
     * `kotlinx-coroutines-test` / Robolectric，`ChatViewModel` 依赖 `viewModelScope`
     * 无法在 JVM 上实例化。缓冲本身的分段语义由 `StreamFlushBufferTest` 覆盖，
     * 代次守卫在结构上封堵了串台路径；真机验收项见 `docs/02-roadmap.md`。
     */
    fun newConversation() {
        runGeneration++
        // 顺序有意：先递增代次（让任何在途回调立刻失效），再拆掉刷新循环与缓冲，
        // 最后才重置 state。反过来的话，重置与清缓冲之间存在一个「空白会话 + 有残段」
        // 的窗口。
        flushJob?.cancel()
        flushJob = null
        flushBuffer.clear()
        // 旧 run 的审批句柄必须一起取消：只 cancel 协程时内核可能正挂在 await 上，
        // 而重置 state 已经把卡抹掉了 —— 句柄若还活着，一次迟到的点击就会给
        // 一条已作废的 run 回传裁决（旧仓「新对话后点了允许，旧工具真的执行了」）。
        pendingApprovalDeferred?.cancel()
        pendingApprovalDeferred = null
        // 新对话 = 新会话授权边界：用户点过「本会话允许相同调用」的授权到此为止。
        // 只在这里清（不在 stop() 里清）：停止一次 run 不是撤销授权，撤回授权的
        // 唯一入口就是「开新对话」—— 语义单一，用户不需要猜。
        approvalCache.clear()
        viewModelScope.launch { activeRunner?.cancel() }
        _state.update {
            ChatUiState(
                endpointLabel = it.endpointLabel,
                contextLimit = it.contextLimit,
            )
        }
    }

    /**
     * 发送。
     *
     * 「检查 canSend + 置位 isStreaming」必须是**一次原子状态迁移**：分成两步
     * （读 → 判 → 写）时，连点两次发送会两条都通过检查，跑起两条 run —— 第二条被
     * `AgentRunner` 的互斥锁拒成 `GateRejected`，用户看到的是「第一条回答被一句
     * 莫名其妙的失败打断」。用 [MutableStateFlow.compareAndSet] 把判定与写入合成
     * 一次 CAS：抢输的那一次直接 return，连错误都不产生。
     */
    fun send() {
        val snapshot = _state.value
        if (!snapshot.canSend) return
        val text = snapshot.input.trim()
        if (text.isEmpty()) return

        // 历史取**发送前**的气泡（不含本条输入：本次输入走 AgentRequest.userInput，
        // 内核自己追加到工作副本尾部，见 AgentRequest.userInput 的 KDoc）。
        val history = snapshot.bubbles.mapNotNull { it.toHistoryMessage() }

        val claimed = _state.compareAndSet(
            snapshot,
            EventReducer.appendUser(snapshot, text).copy(isStreaming = true),
        )
        if (!claimed) return

        val generation = ++runGeneration
        viewModelScope.launch { runOnce(text, history, generation) }
    }

    /**
     * 停止。先断连接（`engine.stop()`），再让协程自然收敛 —— 顺序见 `AGENTS.md` §五.5。
     *
     * 审批句柄要**先**取消：内核此刻可能正挂在 `await()` 上。`AgentRunner.cancel()` 取消
     * 子 job 也会打断 `await`，但那是另一条协程里的异步动作；先取消句柄让审批回调的
     * `finally` 立刻抹掉卡片 —— 用户点「停止」后不该还看到一张能点的卡。
     */
    fun stop() {
        pendingApprovalDeferred?.cancel()
        pendingApprovalDeferred = null
        viewModelScope.launch { activeRunner?.cancel() }
    }

    private suspend fun runOnce(text: String, history: List<ChatMessage>, generation: Int) {
        // 协程还没被调度就被「新对话」作废：直接退出，不白跑一次请求。
        if (generation != runGeneration) return
        val endpoint = container.activeEndpoint()
        if (endpoint == null) {
            _state.update { it.copy(isStreaming = false, errorMessage = NO_ENDPOINT) }
            return
        }
        val runner = container.agentRunnerFor(endpoint)
        activeRunner = runner
        try {
            runner.run(
                AgentRequest(
                    history = history,
                    userInput = text,
                    endpoint = endpoint,
                ),
                approval = ToolApprovalHandler { call, _ ->
                    // 一次审批 = 一个句柄。`spec` 这里用不上：卡片只展示工具名与参数，
                    // 档位 / 风险说明由内核的 ToolApprovalFlow 判定，UI 不复述（第二个真值源）。
                    val deferred = CompletableDeferred<ToolApprovalDecision>()
                    pendingApprovalDeferred = deferred
                    _state.update {
                        it.copy(
                            pendingApproval = PendingApproval(
                                callId = call.id,
                                toolName = call.name,
                                arguments = call.argumentsJson,
                            ),
                        )
                    }
                    try {
                        deferred.await()
                    } finally {
                        // 内核超时 / 取消时 `await` 会抛 CancellationException，这条 finally
                        // 是「UI 上不留一张永远点不掉的卡」的唯一保证。
                        // ⚠️ **不要**用 runCatching 包住 await：取消必须透传，
                        // 否则用户点停止后内核收不到取消，run 会挂到超时才结束。
                        //
                        // 两处都按 callId 归属清理：已作废 run 的 finally 可能晚于新 run 的
                        // 卡片落地（点「新对话」后立刻发送），无条件清会抹掉**新**卡。
                        if (pendingApprovalDeferred === deferred) pendingApprovalDeferred = null
                        _state.update {
                            if (it.pendingApproval?.callId == call.id) {
                                it.copy(pendingApproval = null)
                            } else {
                                it
                            }
                        }
                    }
                },
                // 会话级授权：跨 run 生效，[newConversation] 时清空（见字段 KDoc）。
                // 不传的话内核每次 run 自建一个空缓存，「本会话允许」退化成「本 run 允许」。
                approvalCache = approvalCache,
            ).collect { event -> if (generation == runGeneration) onEvent(event, generation) }
        } finally {
            // 终态事件到达前必须先排空缓冲，否则最后一段增量会丢（`onEvent` 里已排过一次，
            // 这里兜「收集被取消、没走到终态事件」的路径）。
            if (generation == runGeneration) flushNow(generation)
            if (generation == runGeneration) {
                activeRunner = null
                _state.update { it.copy(isStreaming = false) }
            }
        }
    }

    private fun onEvent(event: AgentEvent, generation: Int) {
        when (event) {
            is AgentEvent.TextDelta -> {
                flushBuffer.append(event.text)
                ensureFlushLoop(generation)
            }
            else -> {
                // 结构事件到达前必须先排空缓冲：否则 `Finished(text)` 会把气泡定稿成
                // 「服务端给的全文」，而缓冲里还没刷出去的增量会被下一条覆盖。
                flushNow(generation)
                _state.update { EventReducer.reduce(it, event) }
            }
        }
    }

    /**
     * 启动（或复用）节流循环。
     *
     * [generation] 在**启动时**捕获，循环内每轮都带着它调 [flushNow]。捕获是安全的：
     * 本方法只由 [onEvent] 调用，而 `onEvent` 的调用点已经过了代次门 —— 能走到这里的
     * 一定是当前代次。
     */
    private fun ensureFlushLoop(generation: Int) {
        if (flushJob?.isActive == true) return
        flushJob = viewModelScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                if (!flushNow(generation)) break
            }
        }
    }

    /**
     * 把缓冲刷进 state。
     *
     * @param generation 调用方所属的 run 代次。
     * @return 本次是否真的刷出了内容（false 表示缓冲已空或代次已失效，循环可以退出）。
     *
     * ## 为什么要代次守卫
     *
     * 只靠 [newConversation] 里的 `cancel()` + `clear()` 仍然留了一条缝：`cancel()`
     * 是异步的，被取消的协程可能已经越过 `delay`、正停在 `_state.update` 之前；
     * 主线程上没有抢占，但「取消信号送达」与「循环体继续执行」的先后顺序不由我们决定。
     * 一旦那个 update 落到**已经重置过**的 state 上，就出现幽灵气泡 + `isStreaming`
     * 永久为 true。
     *
     * 代次不一致时**直接丢弃缓冲**：能留下内容的一定是已死代次的残段（新代次在
     * `newConversation` 里已同步清空过缓冲）。极端时序下最多丢掉新代次刚攒下的
     * 一小段增量，而终态 `Finished(text)` 会带上全文，用户不会看到残缺回答 ——
     * 这比把旧残段串进新会话（且输入框卡死）代价小得多。
     */
    private fun flushNow(generation: Int): Boolean {
        if (generation != runGeneration) {
            flushBuffer.clear()
            return false
        }
        val chunk = flushBuffer.drain()
        if (chunk.isEmpty()) return false
        _state.update { EventReducer.appendText(it, chunk) }
        return true
    }

    private fun labelOf(endpoint: RemoteEndpoint): String =
        if (endpoint.modelId.isBlank()) endpoint.name else endpoint.name + " · " + endpoint.modelId

    /** 手动构造工厂：本项目禁 Hilt / Koin，DI 只走 `AppContainer`。 */
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(container) as T
    }

    private companion object {
        const val FLUSH_INTERVAL_MS = 120L
    }
}

/**
 * 视图气泡 → 线上消息。
 *
 * 只回灌**有正文**的 MODEL / USER 气泡：思考通道与工具轨迹不进 history
 * （前者是过程，后者内核自己维护在 `RunState` 里，重复回灌会让模型看到两遍）。
 */
private fun ChatBubble.toHistoryMessage(): ChatMessage? {
    if (text.isBlank()) return null
    if (role == Role.TOOL) return null
    return ChatMessage(role = role, text = text)
}
