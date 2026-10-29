package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.model.Role

/**
 * 一条上屏气泡。
 *
 * 与 `core-model.ChatMessage` 刻意分开：`ChatMessage` 是**线上契约**（含 toolCalls /
 * toolResults，会被序列化进请求），本类是**视图状态**（含 streaming 标记、可能被节流
 * 合并）。混用会导致「把半截流式文本当成历史发回模型」这类 bug。
 */
data class ChatBubble(
    val id: String,
    val role: Role,
    val text: String,
    val streaming: Boolean = false,
    /** 思考通道内容（reasoning）。与正文分开展示，不混进 [text]。 */
    val reasoning: String = "",
)

/**
 * 对话页状态。
 *
 * 全部字段不可变 + `copy` 更新，使得 [EventReducer] 可以是纯函数 —— 旧仓把归约与副作用
 * 缠在 `ChatViewModel`（901 行）里，导致流式节流这类逻辑只能靠真机验证。
 */
data class ChatUiState(
    val bubbles: List<ChatBubble> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    /** 用户可见的错误文案。null = 无错误。 */
    val errorMessage: String? = null,
    /** 顶栏副标题：当前端点名 + 模型。null = 未配置端点。 */
    val endpointLabel: String? = null,
    /** 上一轮的用量摘要（如 `1.2k → 340 tok · 28 tok/s`）。 */
    val usageLabel: String? = null,
) {
    /** 发送按钮可用性：非流式、输入非空、已配置端点。 */
    val canSend: Boolean
        get() = !isStreaming && input.isNotBlank() && endpointLabel != null
}
