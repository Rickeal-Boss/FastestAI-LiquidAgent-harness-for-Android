package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.mcp.McpProtocol
import com.rickeal.liquidharness.core.mcp.McpTransport
import com.rickeal.liquidharness.core.mcp.TransportResponse
import com.rickeal.liquidharness.core.net.HttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InterruptedIOException

/**
 * MCP 的 HTTP 传输，复用 `:core-net` 的 [HttpTransport]。
 *
 * ## 为什么不在 `:core-mcp` 里自己建 OkHttp（W3 的架构裁决）
 *
 * arch-guard G1 规定「网络栈只允许出现在 core-net/」。这条规则不是洁癖：
 * 两套 OkHttp 实例意味着**两套代理配置、两套 DNS 闸、两套超时策略**。
 * 旧仓真实事故就是「模型请求走代理正常、工具请求不走代理全超时」，
 * 而日志上两件事都只是「网络错误」。
 *
 * 复用之后，MCP 与 LLM 请求共享连接池与代理设置 —— 诊断只需看一处。
 *
 * ## 与 SSE 的关系
 *
 * `HttpTransport.post` 返回的是 [com.rickeal.liquidharness.core.net.SseStream]，
 * 它按「响应头已到」建模。MCP 的一问一答只需要把 body 一次读完，
 * 所以这里直接读字节流 —— 同一套传输，两种消费方式。
 */
class NetMcpTransport(
    private val http: HttpTransport,
) : McpTransport {

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        sessionId: String?,
    ): TransportResponse = withContext(Dispatchers.IO) {
        try {
            val stream = http.post(url, mergedHeaders(headers, sessionId), body)
            stream.use {
                TransportResponse(
                    status = it.statusCode,
                    body = runCatching { it.byteStream().readBytes().toString(Charsets.UTF_8) }
                        .getOrDefault(""),
                    contentType = it.contentType.orEmpty(),
                    sessionId = it.header(McpProtocol.SESSION_HEADER),
                )
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            // status=0 = 没拿到 HTTP 响应。超时与其它传输错误分开，UI 才能给出可行动的提示。
            TransportResponse(
                status = 0,
                body = if (e is InterruptedIOException) "timeout" else "transport_error",
            )
        }
    }

    /** 默认头铺底，调用方同名头（大小写不敏感）覆盖，最后追加 session。 */
    private fun mergedHeaders(headers: Map<String, String>, sessionId: String?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        out["Content-Type"] = JSON_CONTENT_TYPE
        out["Accept"] = ACCEPT
        for ((name, value) in headers) {
            val existing = out.keys.firstOrNull { it.equals(name, ignoreCase = true) }
            if (existing != null) out.remove(existing)
            out[name] = value
        }
        if (!sessionId.isNullOrBlank()) out[McpProtocol.SESSION_HEADER] = sessionId
        return out
    }

    private companion object {
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
        const val ACCEPT = "application/json, text/event-stream"
    }
}
