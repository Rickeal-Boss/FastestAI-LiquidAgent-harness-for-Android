package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.mcp.McpServerConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer

/**
 * MCP server 配置的持久化（`mcp-servers.json`，一份 JSON 数组）。
 *
 * ## 刻意不存的东西
 *
 * **不存 Bearer token 明文**：`McpServerConfig.headers` 里允许出现 `Authorization`，
 * 但本仓的密钥纪律要求「Key 绝不落明文」（AGENTS.md §六）。所以：
 * - 落盘前由调用方把敏感 header 换成占位（`Authorization: Bearer ***`）；
 * - 真正的 token 走 `EndpointSecretStore` 同一套 Keystore 信封，key 用
 *   `mcp:<serverId>`。
 *
 * W3 的实现**只做前者**（不落明文），后者（Keystore 存 MCP token）留待 W7 与
 * `SecretRedactor` 扩容一起做 —— 现在 UI 上的 token 输入框会把 token 放进
 * `headers` 并在落盘时被剥离，重启后需要重填。这是**已知缺口**，已在
 * `docs/notes/w3-provider-matrix.md` 的挂账里登记，不假装已解决。
 */
class McpServerStore(private val files: JsonFileStore) {

    private val mutex = Mutex()

    suspend fun list(): List<McpServerConfig> = mutex.withLock { load() }

    suspend fun get(id: String): McpServerConfig? = list().firstOrNull { it.id == id }

    suspend fun upsert(config: McpServerConfig): List<McpServerConfig> = mutex.withLock {
        val current = load()
        val sanitized = config.copy(headers = redactHeaders(config.headers))
        val next = if (current.any { it.id == config.id }) {
            current.map { if (it.id == config.id) sanitized else it }
        } else {
            current + sanitized
        }
        persist(next)
        next
    }

    suspend fun remove(id: String): List<McpServerConfig> = mutex.withLock {
        val next = load().filterNot { it.id == id }
        persist(next)
        next
    }

    /** 记录一次同步结果（工具清单快照 + 时间），供离线时 UI 显示「缓存于 …」。 */
    suspend fun recordSync(id: String, toolsJson: String, atMillis: Long): List<McpServerConfig> =
        mutex.withLock {
            val next = load().map {
                if (it.id == id) it.copy(lastToolsJson = toolsJson, lastSyncedAtMillis = atMillis) else it
            }
            persist(next)
            next
        }

    private suspend fun load(): List<McpServerConfig> =
        files.read(FILE_NAME, ListSerializer(McpServerConfig.serializer())).orEmpty()

    private suspend fun persist(servers: List<McpServerConfig>) {
        files.write(FILE_NAME, ListSerializer(McpServerConfig.serializer()), servers)
    }

    private fun redactHeaders(headers: Map<String, String>): Map<String, String> =
        headers.mapValues { (name, value) ->
            if (name.equals("Authorization", ignoreCase = true) || name.contains("key", ignoreCase = true)) {
                REDACTED
            } else {
                value
            }
        }

    private companion object {
        const val FILE_NAME = "mcp-servers.json"
        const val REDACTED = "***"
    }
}
