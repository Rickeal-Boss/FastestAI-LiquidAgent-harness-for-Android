package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolResult

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
