package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.mcp.McpClient
import com.rickeal.liquidharness.core.mcp.McpTransport
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec

/**
 * MCP 工具装配：把远端 server 的 `tools/list` 结果注册进内核注册表。
 *
 * ## 为什么这里要重新包一层 [Tool]
 *
 * `McpClient` 实现的是 [com.rickeal.liquidharness.core.agent.tool.ToolSource]，
 * 它只产出**声明**（`ToolSpec`）。注册表要的是 `Tool`（带 `invoke`）。
 * 这一层薄适配把「声明」和「调用」接起来，同时保证：
 *
 * - 走 [ToolRegistry.registerContributed]（**同名拒绝 + 强制审批**），
 *   不是 `register` —— 远端 server 是不可信来源，不能覆盖内置工具；
 * - 失败返回 `ToolResult(ok=false)` 而不是抛异常（工具层不许把网络错误变成崩溃）；
 * - 输出截断到 [MAX_OUTPUT_CHARS]：远端 server 可以返回任意长文本，不截断会直接
 *   把上下文撑爆（一次 `tools/call` 返回 200 KB 是真实存在的）。
 *
 * ## 刷新语义
 *
 * `refresh()` 是**全量重建**：先 `unregister` 本来源上次注册的名字，再重新注册。
 * 增量更新会留下「server 已删掉但注册表里还在」的僵尸工具 —— 模型会去调一个
 * 必然 404 的名字，而 UI 上完全看不出来。
 */
class McpToolSources(
    // 必须注入：网络栈在 :core-net，本模块只做适配（见 NetMcpTransport 的 KDoc）。
    private val transport: McpTransport,
) {

    /** 上一次成功注册的工具名（按 sourceId 分组），供刷新时精确下线。 */
    private val registered = LinkedHashMap<String, MutableSet<String>>()

    /**
     * 拉取并注册一个 server 的全部工具。
     *
     * @return 本次注册成功的工具数；失败时 0 且 [lastMessage] 有原因码。
     */
    suspend fun refresh(config: com.rickeal.liquidharness.core.mcp.McpServerConfig, registry: ToolRegistry): Int {
        // 先下线旧工具：即使这次拉取失败，也不该让模型看到过期清单。
        registered.remove(config.id)?.forEach { registry.unregister(it) }

        val client = McpClient(config, transport)
        val snapshot = client.load()
        if (!snapshot.ok) {
            lastMessage = snapshot.message ?: "load_failed"
            return 0
        }
        val names = LinkedHashSet<String>()
        for (spec in snapshot.tools) {
            val adapter = McpToolAdapter(client, spec)
            when (registry.registerContributed(adapter)) {
                is ToolRegistry.RegisterResult.Ok -> names.add(spec.name)
                is ToolRegistry.RegisterResult.RejectedDuplicate -> Unit
            }
        }
        registered[config.id] = names
        lastMessage = null
        return names.size
    }

    /**
     * 只探测、不注册（设置页「测试连接」按钮用）。
     *
     * 与 [refresh] 分开是刻意的：用户点「测试」时**不该**改动当前工具面 ——
     * 一个配错地址的 server 不该把正在用的工具顶下线。
     */
    suspend fun probe(config: com.rickeal.liquidharness.core.mcp.McpServerConfig) =
        McpClient(config, transport).load()

    /** 下线一个 server 的全部工具（删除 / 禁用时调用）。 */
    fun unregister(configId: String, registry: ToolRegistry) {
        registered.remove(configId)?.forEach { registry.unregister(it) }
    }

    /** 最近一次失败原因（UI 用）。null = 上次成功。 */
    var lastMessage: String? = null
        private set

    /** 已注册的工具名（诊断 / UI 展示用）。 */
    fun registeredNames(): Map<String, Set<String>> = registered.mapValues { it.value.toSet() }

    private class McpToolAdapter(
        private val source: McpClient,
        override val spec: ToolSpec,
    ) : Tool {
        override suspend fun invoke(argumentsJson: String): ToolResult {
            val outcome = source.call(spec.name, argumentsJson)
            val output = if (outcome.output.length > MAX_OUTPUT_CHARS) {
                outcome.output.substring(0, MAX_OUTPUT_CHARS) + "\n[truncated]"
            } else {
                outcome.output
            }
            return ToolResult(
                name = spec.name,
                ok = outcome.ok,
                output = output,
                errorMessage = outcome.errorMessage,
            )
        }
    }

    private companion object {
        /** 单次远端调用输出上限。与 `AgentPolicy.maxReadToolOutputChars` 同量级。 */
        const val MAX_OUTPUT_CHARS = 16_000
    }
}
