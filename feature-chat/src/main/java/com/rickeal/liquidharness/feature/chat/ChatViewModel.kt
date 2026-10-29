package com.rickeal.liquidharness.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rickeal.liquidharness.core.agent.AgentRequest
import com.rickeal.liquidharness.core.agent.AgentRunner
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    private var runJob: Job? = null
    private var activeRunner: AgentRunner? = null

    /** 待刷新的流式文本。只在主线程访问（`viewModelScope` 默认 Main）。 */
    private val pendingText = StringBuilder()
    private var flushJob: Job? = null

    init {
        viewModelScope.launch { refreshEndpoint() }
    }

    /**
     * 重新读取活跃端点。
     *
     * 从设置页返回时必须调：换了端点 / 改了模型名后，顶栏与 `canSend` 都依赖这个标签，
     * 不重读就会出现「顶栏显示旧模型、请求已经打到新端点」的错觉。
     */
    suspend fun refreshEndpoint() {
        val endpoint = container.activeEndpoint()
        _state.update { it.copy(endpointLabel = endpoint?.let(::labelOf)) }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** 发送。并发保护在 [AgentRunner] 内部（`tryLock` 失败会给 `GateRejected`）。 */
    fun send() {
        val snapshot = _state.value
        if (!snapshot.canSend) return
        val text = snapshot.input.trim()
        if (text.isEmpty()) return

        _state.update { EventReducer.appendUser(it, text) }
        runJob = viewModelScope.launch {
            val endpoint = container.activeEndpoint()
            if (endpoint == null) {
                _state.update { it.copy(errorMessage = NO_ENDPOINT, isStreaming = false) }
                return@launch
            }
            val history = snapshot.bubbles
                .filter { !it.streaming && it.text.isNotBlank() }
                .map { ChatMessage(role = it.role, text = it.text) }

            _state.update { it.copy(isStreaming = true, errorMessage = null) }
            val runner = container.agentRunnerFor(endpoint)
            activeRunner = runner
            try {
                runner.run(
                    AgentRequest(
                        history = history,
                        userInput = text,
                        endpoint = endpoint,
                    ),
                ).collect(::onEvent)
            } finally {
                flushNow()
                activeRunner = null
                _state.update { it.copy(isStreaming = false) }
            }
        }
    }

    /** 停止。先断连接（`engine.stop()`），再让协程自然收敛 —— 顺序见 `AGENTS.md` §五.5。 */
    fun stop() {
        viewModelScope.launch {
            activeRunner?.cancel()
        }
    }

    fun onEndpointChanged(endpoint: RemoteEndpoint?) {
        _state.update { it.copy(endpointLabel = endpoint?.let(::labelOf)) }
    }

    private fun onEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.TextDelta -> {
                pendingText.append(event.text)
                ensureFlushLoop()
            }
            else -> {
                // 结构事件到达前必须先排空缓冲：否则 `Finished(text)` 会把气泡定稿成
                // 「服务端给的全文」，而缓冲里还没刷出去的增量会被下一条覆盖。
                flushNow()
                _state.update { EventReducer.reduce(it, event) }
            }
        }
    }

    private fun ensureFlushLoop() {
        if (flushJob?.isActive == true) return
        flushJob = viewModelScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                if (!flushNow()) break
            }
        }
    }

    /** @return 本次是否真的刷出了内容（false 表示缓冲已空，循环可以退出）。 */
    private fun flushNow(): Boolean {
        if (pendingText.isEmpty()) return false
        val chunk = pendingText.toString()
        pendingText.setLength(0)
        _state.update { EventReducer.appendDelta(it, chunk) }
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

        /** ASCII 常量（守卫 N3）；上屏文案由 `strings.xml` 按这个 key 映射。 */
        const val NO_ENDPOINT = "no endpoint configured"
    }
}
