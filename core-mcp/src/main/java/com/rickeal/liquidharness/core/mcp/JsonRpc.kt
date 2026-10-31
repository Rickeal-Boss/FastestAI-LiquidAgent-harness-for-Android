package com.rickeal.liquidharness.core.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * JSON-RPC 2.0 帧的构造与解析。**纯函数、无 IO、无线程状态** ——
 * 全部协议细节都必须能在 JVM 单测里被钉死，不允许埋进 [McpClient] 的私有分支。
 */
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = false
}

private const val JSONRPC_VERSION = "2.0"
private const val SSE_DONE = "[DONE]"

/** 一次响应的解析结果。 */
sealed interface RpcOutcome {
    data class Ok(val id: Long, val resultJson: String) : RpcOutcome
    data class Err(val id: Long?, val code: Int, val message: String) : RpcOutcome

    /** 非 JSON / 缺 `jsonrpc` / 结构不合法。[detail] 是 ASCII 原因码。 */
    data class Malformed(val detail: String) : RpcOutcome
}

/** 请求帧。`params` 缺省时**不写该字段**（不是 `null`）。 */
fun requestFrame(id: Long, method: String, paramsJson: String? = null): String {
    val frame = buildJsonObject {
        put("jsonrpc", JSONRPC_VERSION)
        put("id", JsonPrimitive(id))
        put("method", method)
        paramsElement(paramsJson)?.let { put("params", it) }
    }
    return frame.toString()
}

/** 通知帧：没有 `id`，服务端不得回响应。 */
fun notificationFrame(method: String, paramsJson: String? = null): String {
    val frame = buildJsonObject {
        put("jsonrpc", JSONRPC_VERSION)
        put("method", method)
        paramsElement(paramsJson)?.let { put("params", it) }
    }
    return frame.toString()
}

/**
 * 解析单帧。
 *
 * `id` 允许是数字或数字字符串（部分 server 会回字符串）；`error` 优先于 `result`
 * —— 两者同时出现是服务端缺陷，按错误处理更安全（不能把错误当成功）。
 */
fun parseResponse(raw: String): RpcOutcome {
    val trimmed = raw.trim()
    // 形态先判：JSON-RPC 帧只可能是对象或批量数组。`parseToJsonElement` 对某些
    // 非 JSON 文本（如网关返回的 `<html>...`）会解析成一个字符串 primitive，
    // 于是错误码会从「不是 JSON」退化成「不是对象」—— 诊断时少一层信息。
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
        return RpcOutcome.Malformed("not_json")
    }
    val element = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
        ?: return RpcOutcome.Malformed("not_json")
    val obj = element as? JsonObject ?: return RpcOutcome.Malformed("not_object")
    val version = obj.primitive("jsonrpc")
    if (version != JSONRPC_VERSION) return RpcOutcome.Malformed("missing_jsonrpc")

    val id = idOf(obj["id"])
    val error = obj["error"]
    if (error != null && error !is JsonNull) {
        if (error !is JsonObject) return RpcOutcome.Malformed("bad_error")
        val code = error.primitive("code")?.toIntOrNull()
            ?: return RpcOutcome.Malformed("bad_error_code")
        val message = error.primitive("message").orEmpty()
        return RpcOutcome.Err(id, code, message)
    }

    val result = obj["result"] ?: return RpcOutcome.Malformed("missing_result")
    val okId = id ?: return RpcOutcome.Malformed("missing_id")
    return RpcOutcome.Ok(okId, result.toString())
}

/**
 * 从一次 HTTP 响应体里取出所有 JSON-RPC 帧。
 *
 * Streamable HTTP 允许两种载体：
 * - `application/json`：单个对象，或（批量）数组；
 * - `text/event-stream`：多帧 `data:`，**每帧一个 JSON-RPC 消息**，需要逐帧拆。
 *
 * `contentType` 缺失时按体形态猜：以 `data:` / `event:` 开头即当 SSE。
 * 解析失败**不丢内容** —— 原样作为单条 payload 返回，让 [parseResponse] 判 `not_json`。
 */
fun extractPayloads(raw: String, contentType: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return if (isEventStream(contentType, raw)) parseSsePayloads(raw) else parseJsonPayloads(raw)
}

private fun paramsElement(paramsJson: String?): JsonElement? {
    if (paramsJson == null) return null
    return runCatching { json.parseToJsonElement(paramsJson) }.getOrNull()
}

private fun idOf(element: JsonElement?): Long? {
    val primitive = element as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.contentOrNull?.toLongOrNull()
}

private fun JsonObject.primitive(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun isEventStream(contentType: String, raw: String): Boolean {
    val ct = contentType.lowercase()
    if (ct.contains("text/event-stream")) return true
    if (ct.contains("application/json")) return false
    val head = raw.trimStart()
    return head.startsWith("data:") || head.startsWith("event:") || head.startsWith(":")
}

private fun parseJsonPayloads(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyList()
    val element = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
        ?: return listOf(trimmed)
    return if (element is JsonArray) element.map { it.toString() } else listOf(element.toString())
}

/**
 * SSE 分帧：空行为帧边界，`data:` 多行用 `\n` 拼接，`:` 开头是注释 / 心跳。
 * `[DONE]` 不是 JSON-RPC 帧，丢弃。
 */
private fun parseSsePayloads(raw: String): List<String> {
    val out = ArrayList<String>(4)
    val data = StringBuilder()
    var hasData = false

    fun flush() {
        if (hasData) {
            val payload = data.toString()
            if (payload.isNotBlank() && payload.trim() != SSE_DONE) out.add(payload)
        }
        data.setLength(0)
        hasData = false
    }

    for (line in raw.split("\r\n", "\n", "\r")) {
        if (line.isEmpty()) {
            flush()
            continue
        }
        if (line.startsWith(":")) continue
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        if (field != "data") continue
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        if (hasData) data.append('\n')
        data.append(value)
        hasData = true
    }
    flush()
    return out
}
