package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.newId

/*
 * 从 UI 气泡重建线上 history。
 *
 * 单独成文件（而不是留在 ChatViewModel 里）是为了守住 feature-chat 的
 * 单文件 500 行闸门（arch-guard G10）。同时它本身是**纯函数**：
 * 输入气泡列表、输出 ChatMessage 列表，零 IO、零协程、零 Android 依赖，
 * 因此可以脱离 ViewModel 单独做 JVM 单测。
 */

/**
 * 视图气泡 → 线上消息（**可能多条**）。
 *
 * ## 这是 W26-04（I1）的修复点
 *
 * W2.6 实测证据：跨轮从 UI 气泡重建 history 时，`assistant(tool_calls)` 与
 * `tool(result)` 整段消失 —— 模型在下一轮**看不到自己调过什么、拿到过什么**，
 * 于是重复调用同一工具、或凭最终答复「回忆」事实。危害在语义，不在缓存。
 *
 * 现在的重建规则：
 * - MODEL 气泡有轨迹 ⇒ 产出 `MODEL(text + toolCalls)` 再跟若干 `TOOL(result)`，
 *   顺序与线上协议一致（声明在前、结果在后）；
 * - SKIPPED 的轨迹**不重建调用声明**（它没有结果，重建会让 `sanitizeForProvider`
 *   补一条假的「结果缺失」，等于告诉模型「你调了但失败了」，与事实不符）；
 * - 失败轨迹保留（`ok=false` + 错误文本）：模型需要知道「这条路上次不通」。
 *
 * ## 附件只在**当前轮**发送
 *
 * 历史里的图片不回灌：一是请求体体积会随轮次线性膨胀（每张 ~2 MB base64），
 * 二是多数端点对历史图片没有额外信息增益。历史里保留的只有文本。
 */
internal fun ChatBubble.toHistoryMessages(): List<ChatMessage> {
    if (role == Role.TOOL) return emptyList()
    if (role == Role.SYSTEM) return emptyList()

    val calls = traces
        .filter { it.status != ToolTraceStatus.SKIPPED }
        .map { trace ->
            ToolCall(
                id = trace.id,
                name = trace.name,
                argumentsJson = trace.arguments.ifBlank { "{}" },
            )
        }
    if (text.isBlank() && calls.isEmpty()) return emptyList()

    val head = ChatMessage(role = role, text = text, toolCalls = calls)
    if (calls.isEmpty()) return listOf(head)

    val results = traces
        .filter { it.status != ToolTraceStatus.SKIPPED }
        .map { trace ->
            ToolResult(
                callId = trace.id,
                name = trace.name,
                ok = trace.status == ToolTraceStatus.OK,
                output = if (trace.status == ToolTraceStatus.OK) trace.result.orEmpty() else "",
                errorMessage = if (trace.status == ToolTraceStatus.FAILED) {
                    trace.result.orEmpty().ifBlank { "tool failed" }
                } else {
                    null
                },
                elapsedMillis = trace.elapsedMillis,
            )
        }
    // 每个 tool_call_id 一条 TOOL 消息：OpenAI 兼容族要求一一对应
    // （`OpenAiCompatibleAdapter.messageObjects` 对多结果也是拆成多条）。
    return listOf(head) + results.map { ChatMessage(role = Role.TOOL, toolResults = listOf(it)) }
}

/**
 * 整段气泡 → 线上 history，并支持「手动压缩」的切点。
 *
 * @param fromId 非空 = 该气泡**及其之前**的气泡不再进 history（手动压缩的产物）。
 *   它们仍留在 [ChatUiState.bubbles] 里给用户读 —— 压缩改变的是「发回模型的字节」，
 *   不是「用户能看到的内容」，这两件事必须分开。
 *
 * 切点是**气泡级**的，不是消息级：一条 MODEL 气泡可能映射成 `1 + N` 条消息，
 * 从消息中间切会把 `assistant.tool_calls` 与它的 `tool` 结果拆散 ⇒ 端点 400
 * （`WindowContextCompressor` 的类头同一论证）。气泡边界天然是安全边界。
 *
 * **失效 id 回落到全量**：`fromId` 找不到只有一种成因 —— 气泡列表被整体换掉了
 * （新对话 / 会话导入），而那时 `compressedUpTo` 本来就被重置。真出现不一致时宁可多发
 * 一段历史（可见、可诊断），也不要静默少发一段（模型凭空忘事，且界面上看不出来）。
 */
internal fun List<ChatBubble>.toHistoryMessages(fromId: String? = null): List<ChatMessage> {
    if (fromId == null) return flatMap { it.toHistoryMessages() }
    val cut = indexOfFirst { it.id == fromId }
    if (cut < 0) return flatMap { it.toHistoryMessages() }
    return drop(cut + 1).flatMap { it.toHistoryMessages() }
}

/**
 * 线上消息 → 视图气泡（**导入通道的反向映射**）。
 *
 * 只映射 [Role.USER] 与 [Role.MODEL]：`SessionImportParser` 已经保证只产出这两种，
 * 其余角色（SYSTEM / TOOL）在这里被丢弃而不是硬塞成气泡 —— 一条 `role=tool` 气泡在
 * 列表里既没有工具卡也没有宿主声明，只会显示成一句无主的文本。
 *
 * **不带附件**（`attachments` 恒空）：历史图片不回灌（[ChatBubble.toHistoryMessages] 的
 * 同一口径），导入的是题面文本。
 *
 * 空正文**不过滤**：调用方（导入）已经过滤过；这里再过滤一次会让「气泡数 == 消息数」
 * 这条判据在别处悄悄不成立。
 */
internal fun List<ChatMessage>.toChatBubbles(): List<ChatBubble> = mapNotNull { message ->
    when (message.role) {
        Role.USER -> ChatBubble(id = newId(), role = Role.USER, text = message.text)
        Role.MODEL -> ChatBubble(id = newId(), role = Role.MODEL, text = message.text)
        else -> null
    }
}
