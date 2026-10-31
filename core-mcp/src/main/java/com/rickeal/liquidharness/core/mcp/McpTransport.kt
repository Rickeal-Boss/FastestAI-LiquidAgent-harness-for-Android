package com.rickeal.liquidharness.core.mcp

// 本文件**刻意没有任何 okhttp 导入**：网络栈收口在 :core-net（arch-guard G1）。
// 真实实现见 :core-data 的 NetMcpTransport。

/**
 * 一次 POST 的结果。
 *
 * **非 2xx 不抛异常**：状态码是数据，401（改配置）与 500（重试）在客户端要分不同分支，
 * 抛成异常就把分类信息降级成了字符串。
 *
 * [status] = 0 表示**没拿到 HTTP 响应**（DNS / TLS / 超时 / 断连），[body] 是 ASCII 原因码。
 */
data class TransportResponse(
    val status: Int,
    val body: String,
    val contentType: String = "",
    val sessionId: String? = null,
)

/**
 * 传输端口。存在的唯一理由是可测：协议状态机（[McpClient]）不该知道 OkHttp 存在，
 * 单测也不该起真实网络。
 */
interface McpTransport {
    suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        sessionId: String?,
    ): TransportResponse
}

/**
 * OkHttp 实现。每次请求都是「一问一答」的短连接语义（MCP Streamable HTTP 的无状态形态），
 * 因此不维护长连接、不读流：响应体一次性读完再交给协议层分帧。
 *
 * 60s 总超时是**硬上限**：一次 `tools/call` 卡住 60s 以上，UI 上的等待已经没有意义，
 * 让用户看到 `timeout` 比无限转圈好。`retryOnConnectionFailure = false` 与 :core-net
 * 同策略 —— 静默重试会让「一次失败」在日志里变成两次，且可能重复执行写工具。
 */

/*
 * ⚠️ W3 变更：OkHttp 实现已从本模块**移出**，理由见 arch-guard G1
 * （「网络栈只允许出现在 core-net/」）。
 *
 * 现在的分工：
 *   - 本模块只有 `McpTransport` 端口 + 帧解析（纯逻辑，JVM 可测）；
 *   - 真实实现 `NetMcpTransport` 在 `:core-data`，复用 `:core-net` 的 `HttpTransport`
 *     （连接池、DNS 闸、代理配置、超时策略因此与 LLM 请求**同源**，
 *     不会出现「MCP 能连但模型连不上」这种两套网络栈的诊断分裂）。
 *
 * 这条纪律不是形式主义：旧仓曾因两套 OkHttp 实例导致代理配置只对其中一套生效，
 * 表现为「模型正常、工具全超时」。
 */
