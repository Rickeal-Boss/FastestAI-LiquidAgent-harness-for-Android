package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.Role

/**
 * `AgentEvent` → [ChatUiState] 的**纯函数**归约。
 *
 * 为什么必须是纯函数：流式路径的 bug（重复气泡、半截文本落库、取消后气泡不收敛）
 * 只有在能构造任意事件序列时才测得出。旧仓 `ChatViewModel` 把归约和协程副作用写在一起，
 * 这些分支只能靠真机点。
 *
 * 不变量（每条都有单测钉住）：
 * 1. **同一时刻最多一个 streaming 气泡**；`TextDelta` 到达时若无 streaming 气泡则新建。
 * 2. `Finished` / `Failed` / `Cancelled` 三个终态**必定**把 streaming 标记清掉
 *    （否则 UI 会永远显示「正在输入」）。
 * 3. 终态只收敛**最后一条** streaming 气泡，不动历史。
 * 4. `Cancelled` 保留已收到的部分文本（`partialText` 与累积文本取非空者）。
 */
object EventReducer {

    fun reduce(state: ChatUiState, event: AgentEvent): ChatUiState = when (event) {
        is AgentEvent.TextDelta -> appendDelta(state, event.text)
        is AgentEvent.ReasoningDelta -> appendReasoning(state, event.text)
        is AgentEvent.Finished -> finalize(state, event.text.ifEmpty { streamingText(state) })
        is AgentEvent.Failed -> state.copy(
            bubbles = closeStreaming(state.bubbles),
            isStreaming = false,
            errorMessage = event.message,
        )
        is AgentEvent.Cancelled -> finalize(state, event.partialText.ifEmpty { streamingText(state) })
        is AgentEvent.UsageUpdated -> state.copy(
            usageLabel = buildUsageLabel(event.prompt, event.completion, event.cachedPrompt),
        )
        // 以下事件 W1 不渲染，但要显式列出（`when` 穷尽 = 新增事件时编译期强制处理）
        is AgentEvent.RoundStarted,
        is AgentEvent.ToolCallStarted,
        is AgentEvent.ToolResultReceived,
        is AgentEvent.ToolSkipped,
        is AgentEvent.MessageCommitted,
        is AgentEvent.SkillLoaded,
        is AgentEvent.Retrying,
        is AgentEvent.StreamReset,
        is AgentEvent.ApprovalRequested,
        is AgentEvent.PlanUpdated,
        is AgentEvent.ToolSourceUpdated,
        -> state
    }

    /**
     * 追加流式文本。节流由调用方（`ChatViewModel`）负责：它把 120ms 内的增量合并成一次
     * 调用，本函数只做纯追加 —— 这样「节流窗口多大」不进入归约逻辑，可独立调整。
     */
    fun appendDelta(state: ChatUiState, text: String): ChatUiState {
        if (text.isEmpty()) return state
        val index = state.bubbles.indexOfLast { it.streaming }
        val next = if (index < 0) {
            state.bubbles + ChatBubble(
                id = "stream-" + state.bubbles.size,
                role = Role.MODEL,
                text = text,
                streaming = true,
            )
        } else {
            state.bubbles.toMutableList().also {
                it[index] = it[index].copy(text = it[index].text + text)
            }
        }
        return state.copy(bubbles = next, isStreaming = true, errorMessage = null)
    }

    /** 追加思考通道内容。与正文分开存，避免 `reasoning` 被当成回答正文发回模型。 */
    fun appendReasoning(state: ChatUiState, text: String): ChatUiState {
        if (text.isEmpty()) return state
        val index = state.bubbles.indexOfLast { it.streaming }
        val next = if (index < 0) {
            state.bubbles + ChatBubble(
                id = "stream-" + state.bubbles.size,
                role = Role.MODEL,
                text = "",
                streaming = true,
                reasoning = text,
            )
        } else {
            state.bubbles.toMutableList().also {
                it[index] = it[index].copy(reasoning = it[index].reasoning + text)
            }
        }
        return state.copy(bubbles = next, isStreaming = true)
    }

    /**
     * 收敛：把 streaming 气泡定稿为 [finalText]。
     *
     * `finalText` 为空且已有累积文本时保留累积文本（服务端没给 `finish_reason` 的
     * 半截流场景，见 `AGENTS.md` §五.6）；两者都空则**删掉**该气泡，
     * 不留下一个空壳（空气泡会让用户以为「模型没回答」而实际是网络失败）。
     */
    fun finalize(state: ChatUiState, finalText: String): ChatUiState {
        val index = state.bubbles.indexOfLast { it.streaming }
        if (index < 0) {
            return if (finalText.isEmpty()) {
                state.copy(isStreaming = false)
            } else {
                state.copy(
                    bubbles = state.bubbles + ChatBubble(
                        id = "final-" + state.bubbles.size,
                        role = Role.MODEL,
                        text = finalText,
                    ),
                    isStreaming = false,
                )
            }
        }
        val target = state.bubbles[index]
        val text = finalText.ifEmpty { target.text }
        val next = state.bubbles.toMutableList()
        if (text.isEmpty()) {
            next.removeAt(index)
        } else {
            next[index] = target.copy(text = text, streaming = false)
        }
        return state.copy(bubbles = next, isStreaming = false)
    }

    fun appendUser(state: ChatUiState, text: String): ChatUiState =
        if (text.isBlank()) {
            state
        } else {
            state.copy(
                bubbles = state.bubbles + ChatBubble(
                    id = "user-" + state.bubbles.size,
                    role = Role.USER,
                    text = text,
                ),
                input = "",
                errorMessage = null,
            )
        }

    private fun streamingText(state: ChatUiState): String =
        state.bubbles.lastOrNull { it.streaming }?.text.orEmpty()

    private fun closeStreaming(bubbles: List<ChatBubble>): List<ChatBubble> =
        bubbles.map { if (it.streaming) it.copy(streaming = false) else it }

    private fun buildUsageLabel(prompt: Int, completion: Int, cached: Int): String {
        if (prompt <= 0 && completion <= 0) return ""
        val cachePart = if (cached > 0) " (" + cached + " cached)" else ""
        return compact(prompt) + " in / " + compact(completion) + " out" + cachePart
    }

    private fun compact(value: Int): String = when {
        value >= 1000 -> ((value / 100) / 10.0).toString() + "k"
        else -> value.toString()
    }
}
