package com.rickeal.liquidharness.core.agent.tool

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.model.ToolSpec
import com.rickeal.liquidharness.core.model.ToolSourceKind
import java.util.concurrent.ConcurrentHashMap

/**
 * 工具注册中心。用 `ConcurrentHashMap`，允许在生成过程中动态开关（模型跑到一半
 * 用户去设置页关掉一个工具，下一轮工具面就少一个 —— 不需要重启 run）。
 *
 * ## 相对旧仓 `ToolRegistry.kt`（64 行）的两处变更
 *
 * 1. **[registerContributed] 从「同名 warn + 覆盖」改为「同名拒绝 + error 日志」。**
 *    旧仓做法把「名字抢注」从攻击面降级成了一行 warn：第三方贡献者注册一个 `file_read`
 *    就能顶掉内置版，而宿主唯一的证据是一条日志。MCP 接入后这个面从「本机贡献者」
 *    扩大到「任意远端 server」，所以改成**拒绝**：先到先得，后来者拿不到这个名字。
 *    （`00-brief.md` A5 表：`ToolRegistry` 原样搬，唯 `registerContributed` 改造。）
 *
 * 2. **返回值改 `sealed` 而不是 `Unit` / `Boolean`。** 注册失败必须是可判定的结果，
 *    不能靠「调用方记得去看日志」。旧仓纪律：Boolean → sealed 是消灭静默失败的范式
 *    （`AGENTS.md` §五 / 知识库 §5）。
 */
class ToolRegistry {

    private val tools = ConcurrentHashMap<String, Tool>()
    private val disabled = ConcurrentHashMap.newKeySet<String>()

    /**
     * 注册内置工具。**允许覆盖**（同一工具热更新 / 重复装配是合法场景）。
     * 覆盖只在同名且**来源相同**时静默；跨来源覆盖会被 [registerContributed] 拒绝。
     */
    fun register(tool: Tool): RegisterResult {
        val name = tool.spec.name
        val previous = tools.put(name, tool)
        if (previous != null && previous.spec.source != tool.spec.source) {
            AgentLogStore.error("内置工具「$name」覆盖了来源不同的已注册工具，请确认装配顺序")
        }
        return RegisterResult.Ok(name, replaced = previous != null)
    }

    /**
     * 第三方（`ToolContributor` / MCP）工具注册：**强制过审批闸门 + 同名拒绝**。
     *
     * 两条纪律的理由：
     * - **强制 `requiresConfirmation = true`**：贡献者无法自证可信，闸门由宿主决定 ——
     *   未声明审批属性的工具若直接 register，等于把信任边界建立在贡献者的自觉上。
     *   `dangerous` 不代改：贡献者若声明了 dangerous，按更严的一条走（两标志都进闸门）。
     * - **同名拒绝**：名字抢注是真实攻击面（注册一个 `file_read` 顶掉内置版），
     *   拒绝比覆盖安全，且「拒绝」可被调用方感知（返回值），不像 warn 只能事后翻日志。
     */
    fun registerContributed(tool: Tool): RegisterResult {
        val name = tool.spec.name
        val existing = tools[name]
        if (existing != null) {
            AgentLogStore.error(
                "拒绝注册第三方工具「$name」：同名工具已存在（来源=${existing.spec.source}），" +
                    "不允许覆盖（防名字抢注）"
            )
            return RegisterResult.RejectedDuplicate(name, existing.spec.source)
        }
        tools[name] = object : Tool by tool {
            override val spec: ToolSpec = tool.spec.copy(requiresConfirmation = true)
        }
        return RegisterResult.Ok(name, replaced = false)
    }

    fun registerAll(list: List<Tool>): List<RegisterResult> = list.map { register(it) }

    fun registerAllContributed(list: List<Tool>): List<RegisterResult> =
        list.map { registerContributed(it) }

    /** 注销。同时清掉启停标记 —— 否则同名工具重新注册后会「继承」上一次的禁用状态。 */
    fun unregister(name: String) {
        tools.remove(name)
        disabled.remove(name)
    }

    fun setEnabled(name: String, enabled: Boolean) {
        if (enabled) disabled.remove(name) else disabled.add(name)
    }

    /** 未注册的名字按「未启用」处理（fail-closed：不存在的工具不可能被调用）。 */
    fun isEnabled(name: String): Boolean = tools.containsKey(name) && !disabled.contains(name)

    /** 取工具。**已禁用的返回 null** —— 调用侧因此不需要再判一次启停。 */
    fun get(name: String): Tool? = if (isEnabled(name)) tools[name] else null

    /** 全部已注册工具（含被禁用的），按名字排序（提示词与 UI 的顺序必须稳定）。 */
    fun all(): List<Tool> = tools.values.sortedBy { it.spec.name }

    fun enabledTools(): List<Tool> = all().filter { isEnabled(it.spec.name) }

    /** 下发给引擎的工具声明。只含启用项 —— 禁用工具不能出现在模型可见的工具面上。 */
    fun specs(): List<ToolSpec> = enabledTools().map { it.spec }

    fun size(): Int = tools.size

    /** 注册结果。用 sealed 而非 Boolean：失败原因必须是调用方**能分支**的数据。 */
    sealed interface RegisterResult {
        val name: String

        data class Ok(override val name: String, val replaced: Boolean) : RegisterResult

        /** 同名拒绝。`existingSource` 用于日志与 UI 提示「谁占了这个名字」。 */
        data class RejectedDuplicate(
            override val name: String,
            val existingSource: ToolSourceKind,
        ) : RegisterResult
    }
}
