package com.rickeal.liquidharness.core.agent.tool

import com.rickeal.liquidharness.core.model.ToolSpec

/**
 * 一个**可整体下线**的远端工具来源（W3 唯一实现：MCP server）。
 *
 * ## 与 [ToolContributor] 的分工（两者不互相替代）
 *
 * | | `ToolContributor` | `ToolSource` |
 * |---|---|---|
 * | 时机 | 编译期一次性装配 | 运行期可刷新 / 可整体下线 |
 * | 失败 | 编译不过就发现 | 网络 / 401 / 超时，必须**可见** |
 * | 注册语义 | `register`（允许覆盖） | `registerContributed`（同名拒绝 + 强制审批） |
 *
 * ## 为什么 `load()` 返回数据而不是直接写注册表
 *
 * 注册是宿主的行为，不是来源的。来源若直接 `registry.register`，它就能绕过
 * 「同名拒绝 + 强制审批」这两条纪律 —— 把信任边界交给远端 server 是最糟的形态。
 * 所以来源只负责**产出声明**，注册与下线由 `ToolSourceManager` 统一做。
 */
interface ToolSource {

    /** 稳定标识（MCP 用 server id）。同名冲突判定与 UI 分组都用它。 */
    val id: String

    /** 展示名（MCP 用用户起的 alias）。 */
    val displayName: String

    /**
     * 拉取当前工具清单。
     *
     * **不得抛异常**：网络失败必须表达成 `ok=false` 的快照 —— 抛异常会让
     * 「MCP 连不上」和「harness 崩了」在 UI 上长成同一个样子。
     */
    suspend fun load(): ToolSourceSnapshot

    /** 调用一个工具。**不得抛异常**（同上）。 */
    suspend fun call(toolName: String, argumentsJson: String): ToolCallOutcome
}

/**
 * 一次拉取的结果。
 *
 * [tools] 里的 [ToolSpec.name] 必须已经是**命名空间化**的名字
 * （`mcp__<alias>__<tool>`）：冲突判定在注册表里按名字做，来源侧先改名才不会有
 * 「两个 server 都有 `search`」这种必然冲突。
 */
data class ToolSourceSnapshot(
    val sourceId: String,
    val ok: Boolean,
    val tools: List<ToolSpec> = emptyList(),
    /** 失败原因（ASCII 原因码或服务端错误摘要）。成功时为 null。 */
    val message: String? = null,
)

/** 一次远端调用的结果。与 `ToolResult` 分开：来源不认识内核的结果类型。 */
data class ToolCallOutcome(
    val ok: Boolean,
    val output: String = "",
    val errorMessage: String? = null,
)
