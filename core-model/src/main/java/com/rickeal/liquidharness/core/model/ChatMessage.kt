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
 * ## attachments（W3 新增）
 *
 * 只允许挂在 [Role.USER] 上。契约层不引 Android API：字节由 `AttachmentImporter`
 * 在导入时解码好，随消息携带（理由见 `Attachment` 的 KDoc）。
 * 非 USER 消息带 attachments 属于**编程错误**，引擎侧会整段丢弃而不是猜意图。
 */
@Serializable
data class ChatMessage(
    val id: String = newId(),
    val role: Role = Role.USER,
    val text: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResults: List<ToolResult> = emptyList(),
    val createdAtMillis: Long = 0L,
    val attachments: List<Attachment> = emptyList(),
) {
    /** 空消息（无文本、无工具、无附件）在发请求前应被过滤，否则部分端点直接 400。 */
    val isEmpty: Boolean
        get() = text.isEmpty() && toolCalls.isEmpty() && toolResults.isEmpty() && attachments.isEmpty()

    /** 可发送的附件（`isSendable` 过滤）。文本附件由引擎拼进正文，图片走 content 数组。 */
    val sendableAttachments: List<Attachment>
        get() = if (role == Role.USER) attachments.filter { it.isSendable } else emptyList()
}
