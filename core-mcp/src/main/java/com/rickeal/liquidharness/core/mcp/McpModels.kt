package com.rickeal.liquidharness.core.mcp

import com.rickeal.liquidharness.core.model.newId
import kotlinx.serialization.Serializable

/**
 * 一个 MCP server 的持久化配置。
 *
 * [protocolEra] 三态：
 * - `auto`（默认）：先探 `server/discover`，`-32601` / 404 / 405 回落 legacy；
 * - `2026-07-28`：只走新协议，探测失败即失败（不静默降级）；
 * - `2025-11-25`：跳过探测直接 `initialize`。
 *
 * [allowPrivateNetwork] 是用户显式承担的信任扩张（内网 MCP server），默认 fail-closed。
 * 传输层目前不实现 DNS 闸（本模块不依赖 :core-net），该字段只作为策略标记落盘。
 */
@Serializable
data class McpServerConfig(
    val id: String = newId(),
    val alias: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val protocolEra: String = McpProtocol.ERA_AUTO,
    val allowPrivateNetwork: Boolean = false,
    val lastToolsJson: String = "",
    val lastSyncedAtMillis: Long = 0L,
)

/** `tools/list` 返回的一条工具声明（尚未命名空间化）。 */
data class McpToolDescriptor(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
)

/**
 * 失败分类。存在的意义是让上层**按类分支**（改配置 / 换协议 / 修传输），
 * 而不是对一段错误字符串做正则。
 */
sealed interface McpError {
    data class Transport(val message: String) : McpError
    data class Rpc(val code: Int, val message: String) : McpError
    data class Protocol(val message: String) : McpError
}

/** 协议常量集中一处：era 字符串、头部名、预算上限。全部 ASCII。 */
object McpProtocol {
    const val ERA_AUTO: String = "auto"
    const val ERA_MODERN: String = "2026-07-28"
    const val ERA_LEGACY: String = "2025-11-25"

    const val CLIENT_NAME: String = "liquid-harness"
    const val CLIENT_VERSION: String = "1.0"

    const val SESSION_HEADER: String = "Mcp-Session-Id"
    const val METHOD_NOT_FOUND: Int = -32601

    /** 工具名总长上限（OpenAI / Anthropic 两族都能接受的安全值）。 */
    const val MAX_TOOL_NAME: Int = 64

    /** alias 在名字里的最大占位，保证远端工具名仍有预算。 */
    const val MAX_NAMESPACE: Int = 24

    /** 远端 description 是不可信文本，进提示词前截断。 */
    const val MAX_DESCRIPTION: Int = 512

    /** 参数描述更短：它只进 schema，不进提示词正文。 */
    const val MAX_PARAM_DESCRIPTION: Int = 256

    /** `tools/list` 分页上限，防止恶意 server 用无限 cursor 拖死一轮同步。 */
    const val MAX_PAGES: Int = 10
}
