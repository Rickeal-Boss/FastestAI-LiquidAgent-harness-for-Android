package com.rickeal.liquidharness.core.mcp

import com.rickeal.liquidharness.core.agent.tool.ToolCallOutcome
import com.rickeal.liquidharness.core.agent.tool.ToolSource
import com.rickeal.liquidharness.core.agent.tool.ToolSourceSnapshot
import com.rickeal.liquidharness.core.model.ToolSpec
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/** 一次握手的结果。失败必须带 ASCII 原因码（`http_401` / `timeout` / `malformed` / `rpc_-32602`）。 */
sealed interface ConnectResult {
    data class Ok(val era: String) : ConnectResult
    data class Failed(val reason: String) : ConnectResult
}

/**
 * MCP 客户端：JSON-RPC 2.0 over Streamable HTTP。
 *
 * ## 为什么握手要「先探后降」
 *
 * 新协议（2026-07-28）用 `server/discover` 探测，legacy（2025-11-25）用 `initialize`。
 * 没有版本头可以问，只能**打一枪看回什么**：`-32601` / 404 / 405 是「这个 server 不认新方法」，
 * 此时降级是正确行为；401 不是 —— 那是凭据问题，降级只会把 401 变成第二个 401，
 * 让用户以为是协议不兼容。
 *
 * ## 状态机只有三个字段
 *
 * [era]（协商结果）、[sessionId]（服务端下发的会话）、[connected]（本次实例是否已握手成功）。
 * 不落盘、不跨进程 —— 配置持久化是 :core-data 的事，客户端只管一次运行期。
 *
 * ## 失败即数据
 *
 * 所有 `suspend` 出口都返回 `ok = false` 的数据。唯一例外是 `CancellationException`
 * （协程取消必须穿透，否则 Job 从 cancelled 变 completed）。抛异常会让「MCP 连不上」
 * 和「harness 崩了」在 UI 上长成同一个样子。
 */
class McpClient(
    private val config: McpServerConfig,
    private val transport: McpTransport,
) : ToolSource {

    override val id: String get() = config.id

    override val displayName: String get() = config.alias

    /** 协商出的协议版本；未握手时为 [McpProtocol.ERA_AUTO]。 */
    var era: String = config.protocolEra.takeIf { it != McpProtocol.ERA_AUTO } ?: McpProtocol.ERA_AUTO
        private set

    /** 服务端下发的 `Mcp-Session-Id`；legacy 与无状态 server 可能一直为 null。 */
    var sessionId: String? = null
        private set

    private var connected: Boolean = false
    private var requestId: Long = 0L

    /** 命名空间名 → 远端原名。远端名可能含 `.` / 空格，反推会失真，必须记原值。 */
    private val remoteNames = ConcurrentHashMap<String, String>()

    // ---------------------------------------------------------------- 握手

    suspend fun connect(): ConnectResult {
        if (connected) return ConnectResult.Ok(era)
        val result = if (config.protocolEra == McpProtocol.ERA_LEGACY) initialize() else discover()
        if (result is ConnectResult.Ok) connected = true
        return result
    }

    private suspend fun discover(): ConnectResult =
        when (val call = invoke("server/discover", "{}")) {
            is Call.Ok -> {
                era = McpProtocol.ERA_MODERN
                ConnectResult.Ok(era)
            }
            is Call.RpcErr -> {
                if (call.code == McpProtocol.METHOD_NOT_FOUND && canFallback()) {
                    initialize()
                } else {
                    ConnectResult.Failed("rpc_${call.code}")
                }
            }
            is Call.Http -> {
                if ((call.status == 404 || call.status == 405) && canFallback()) {
                    initialize()
                } else {
                    ConnectResult.Failed("http_${call.status}")
                }
            }
            is Call.Bad -> ConnectResult.Failed(call.detail)
        }

    private suspend fun initialize(): ConnectResult {
        val params = buildJsonObject {
            put("protocolVersion", McpProtocol.ERA_LEGACY)
            put("capabilities", buildJsonObject { })
            put("clientInfo", buildJsonObject {
                put("name", McpProtocol.CLIENT_NAME)
                put("version", McpProtocol.CLIENT_VERSION)
            })
        }.toString()
        return when (val call = invoke("initialize", params)) {
            is Call.Ok -> {
                era = McpProtocol.ERA_LEGACY
                // 规范要求：initialize 成功后必须发通知，否则部分 server 会拒绝后续请求。
                // 通知的响应（通常是 202 空体）没有语义，失败也不影响握手结论。
                try {
                    transport.post(
                        config.url,
                        config.headers,
                        notificationFrame("notifications/initialized"),
                        sessionId,
                    )
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    // 通知是 best-effort：握手已经成功，这里失败不值得把整次连接判死。
                }
                ConnectResult.Ok(era)
            }
            is Call.RpcErr -> ConnectResult.Failed("rpc_${call.code}")
            is Call.Http -> ConnectResult.Failed("http_${call.status}")
            is Call.Bad -> ConnectResult.Failed(call.detail)
        }
    }

    private fun canFallback(): Boolean = config.protocolEra == McpProtocol.ERA_AUTO

    // ---------------------------------------------------------------- ToolSource

    override suspend fun load(): ToolSourceSnapshot {
        return when (val handshake = connect()) {
            is ConnectResult.Failed -> failure(handshake.reason)
            is ConnectResult.Ok -> fetchTools()
        }
    }

    private suspend fun fetchTools(): ToolSourceSnapshot {
        val descriptors = ArrayList<McpToolDescriptor>()
        var cursor: String? = null
        var pages = 0
        while (pages < McpProtocol.MAX_PAGES) {
            pages++
            val current = cursor
            val params = if (current == null) "{}" else buildJsonObject { put("cursor", current) }.toString()
            when (val call = invoke("tools/list", params)) {
                is Call.Ok -> {
                    descriptors += parseToolList(call.resultJson)
                    cursor = parseNextCursor(call.resultJson)
                    if (cursor == null) break
                }
                is Call.RpcErr -> return failure("rpc_${call.code}")
                is Call.Http -> return failure("http_${call.status}")
                is Call.Bad -> return failure(call.detail)
            }
        }

        // 同名去重：远端重复声明时保留第一条，保证两次 load 的清单逐字一致。
        val specs = ArrayList<ToolSpec>(descriptors.size)
        val seen = HashSet<String>(descriptors.size)
        for (descriptor in descriptors) {
            val spec = buildToolSpec(config.alias, descriptor)
            if (!seen.add(spec.name)) continue
            remoteNames[spec.name] = descriptor.name
            specs += spec
        }
        return ToolSourceSnapshot(sourceId = config.id, ok = true, tools = specs)
    }

    override suspend fun call(toolName: String, argumentsJson: String): ToolCallOutcome {
        return when (val handshake = connect()) {
            is ConnectResult.Failed -> ToolCallOutcome(false, errorMessage = handshake.reason)
            is ConnectResult.Ok -> invokeTool(toolName, argumentsJson)
        }
    }

    private suspend fun invokeTool(toolName: String, argumentsJson: String): ToolCallOutcome {
        val remoteName = remoteNames[toolName] ?: stripNamespace(toolName)
        val arguments = parseArguments(argumentsJson)
            ?: return ToolCallOutcome(false, errorMessage = "invalid_arguments")
        val params = buildJsonObject {
            put("name", remoteName)
            put("arguments", arguments)
        }.toString()
        return when (val call = invoke("tools/call", params)) {
            is Call.Ok -> parseCallResult(call.resultJson)
            is Call.RpcErr -> ToolCallOutcome(false, errorMessage = "rpc_${call.code}")
            is Call.Http -> ToolCallOutcome(false, errorMessage = "http_${call.status}")
            is Call.Bad -> ToolCallOutcome(false, errorMessage = call.detail)
        }
    }

    private fun stripNamespace(toolName: String): String {
        val prefix = namePrefix(config.alias)
        return if (toolName.startsWith(prefix)) toolName.substring(prefix.length) else toolName
    }

    /** 空串当 `{}`（模型常省略参数）；非法 JSON 判 `invalid_arguments`，不静默改写成空对象。 */
    private fun parseArguments(argumentsJson: String): JsonObject? {
        if (argumentsJson.isBlank()) return JsonObject(emptyMap())
        return runCatching {
            json.parseToJsonElement(argumentsJson) as? JsonObject
        }.getOrNull()
    }

    private fun failure(reason: String): ToolSourceSnapshot =
        ToolSourceSnapshot(sourceId = config.id, ok = false, message = reason)

    // ---------------------------------------------------------------- 单次往返

    /**
     * 一次请求 / 响应。
     *
     * Streamable HTTP 的响应可能是 JSON 单对象，也可能是 SSE 多帧（中间夹心跳或通知），
     * 所以这里**逐帧比对 id**：拿到匹配的那一帧才结束。只看第一帧会在
     * 「server 先发一条 log 通知」时误判成空响应。
     */
    private suspend fun invoke(method: String, paramsJson: String?): Call {
        val id = ++requestId
        // 传输端口可能被替换成任意实现（测试替身 / 未来别的 HTTP 栈），
        // 因此这里必须兜住「实现不守约、直接抛异常」的形态。
        val response = try {
            transport.post(
                config.url,
                config.headers,
                requestFrame(id, method, paramsJson),
                sessionId,
            )
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            return Call.Bad("transport_error")
        }
        val nextSession = response.sessionId
        if (!nextSession.isNullOrBlank()) sessionId = nextSession

        if (response.status == 0) {
            return Call.Bad(if (response.body == "timeout") "timeout" else "transport_error")
        }
        if (response.status !in 200..299) return Call.Http(response.status)

        val payloads = extractPayloads(response.body, response.contentType)
        if (payloads.isEmpty()) return Call.Bad("empty_body")

        var malformed = false
        for (payload in payloads) {
            when (val outcome = parseResponse(payload)) {
                is RpcOutcome.Ok -> if (outcome.id == id) return Call.Ok(outcome.resultJson)
                is RpcOutcome.Err -> if (outcome.id == null || outcome.id == id) {
                    return Call.RpcErr(outcome.code, outcome.message)
                }
                is RpcOutcome.Malformed -> malformed = true
            }
        }
        return Call.Bad(if (malformed) "malformed" else "no_matching_response")
    }

    /** 内部往返分类：与 [RpcOutcome] 的区别是**带 HTTP 层失败**。 */
    private sealed interface Call {
        data class Ok(val resultJson: String) : Call
        data class RpcErr(val code: Int, val message: String) : Call
        data class Http(val status: Int) : Call
        data class Bad(val detail: String) : Call
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
