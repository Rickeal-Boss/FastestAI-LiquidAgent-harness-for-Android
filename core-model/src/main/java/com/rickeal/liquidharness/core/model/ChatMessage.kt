package com.rickeal.liquidharness.core.model

import kotlinx.serialization.Serializable

/**
 * 一条会话消息。
 *
 * [toolCalls] 与 [toolResults] 同时存在是因为同一 `ChatMessage` 承载两种线上形态：
 * - `role = MODEL` + `toolCalls`：模型宣告要调哪些工具（OpenAI `assistant.tool_calls`）；
 * - `role = TOOL` + `toolResults`：工具结果回灌（OpenAI 一条消息一个 `tool_call_id`，
 *   Anthropic 是把多个 `tool_result` block 塞进**一条 user 消息**）。
 *
 * ⚠️ Anthropic 的差异意味着：`core-engine` 侧**可能要把一条 MODEL 消息拆成多条**，
 * 所以本类不能假设「一条 ChatMessage = 一个线上消息」。这一点在 W2 接 Anthropic 时验证。
 *
 * 不含 attachments：W1 只做纯文本流式。视觉能力需要 Android `Bitmap` 解码，
 * 那是 `core-data` 的职责，契约层不引 Android API。
 */
@Serializable
data class ChatMessage(
    val id: String = newId(),
    val role: Role = Role.USER,
    val text: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResults: List<ToolResult> = emptyList(),
    val createdAtMillis: Long = 0L,
) {
    /** 空消息（无文本、无工具）在发请求前应被过滤，否则部分端点直接 400。 */
    val isEmpty: Boolean
        get() = text.isEmpty() && toolCalls.isEmpty() && toolResults.isEmpty()
}
