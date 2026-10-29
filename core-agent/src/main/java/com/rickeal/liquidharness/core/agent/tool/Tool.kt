package com.rickeal.liquidharness.core.agent.tool

import android.content.ClipboardManager
import android.content.Context
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec
import java.io.File

/**
 * 工具执行环境。所有 Android 侧能力从这里注入，便于 JVM 测试（测试里不实例化它）。
 *
 * 相对旧仓 `Tool.kt:11-19` 的**唯一变更**：去掉 `agentMemory` 字段。
 * 理由：`AgentMemory` 是 `core-agent` 自己的类型，把它塞进 `ToolContext` 会让「工具」
 * 与「记忆」两个能力互相耦合 —— 记忆工具改为在构造时注入端口（见 W3 的 `MemoryTools`），
 * 其余工具不再被动持有它。
 */
data class ToolContext(
    /** 工具可写的沙箱根目录（App 私有目录下的 workspace）。 */
    val sandboxDir: File,
    /** Application Context —— 绝不持有 Activity（旧仓同款纪律，防泄漏）。 */
    val appContext: Context,
    val clipboard: ClipboardManager? = null,
    /** 时间源注入点：单测要冻结时间，不能让工具直连 `System.currentTimeMillis()`。 */
    val nowMillis: () -> Long = { System.currentTimeMillis() },
)

/** 一个可被模型调用的工具。`invoke` 必须是幂等的，且不得自行捕获 `CancellationException`。 */
interface Tool {
    val spec: ToolSpec
    suspend fun invoke(argumentsJson: String): ToolResult
}

/**
 * 参数级审批：同一工具的读写参数风险不同（剪贴板 `get` / `set` 是典型）时的细粒度闸门。
 *
 * 调用侧在静态判据（`spec.dangerous || spec.requiresConfirmation`）之外，对实现本接口的
 * 工具追加一次参数探测 —— 实现必须 **fail-closed**：参数解析失败返回 true
 * （宁可多弹卡，不可漏弹）。纯函数约定：不得有状态、不得做 IO。
 */
interface ParamGatedTool : Tool {
    fun requiresConfirmationFor(argumentsJson: String): Boolean
}

/**
 * **本次调用**的效果声明（旧仓 Wave 26 / Operit2 `accessSpec(tool)` 语义移植）。
 *
 * 为什么不能只靠 `ToolSpec.effect` 静态字段：同一个工具在不同参数下效果不同 ——
 * 剪贴板 `get` 是读、`set` 是写。静态字段只能是保守默认，**按参数判定必须发生在调用时**。
 *
 * 实现纪律（与 [ParamGatedTool] 同款）：
 * - **纯函数**：不得有状态、不得做 IO、不得有副作用（它只是「声明」，不执行）。
 * - **fail-closed**：参数解析失败必须返回 [ToolEffect.WRITE]（宁可多要一次授权，
 *   不可把写操作误判成读而在只读档位下静默放行）。
 */
interface EffectAwareTool : Tool {
    fun effectFor(argumentsJson: String): ToolEffect
}

/**
 * 扩展点：第三方可注册自定义工具（无需注解处理器、无需 ServiceLoader）。
 *
 * ⚠️ 与 `01-architecture.md` §3.3 的 `ToolSource` 分工：`ToolContributor` 是
 * 「一次性贡献一批」的**内置**扩展点（编译期装配）；MCP 那种「可刷新 + 可整体下线」的
 * 远端来源走 `ToolSource` 端口（W5）。两者不互相替代。
 */
fun interface ToolContributor {
    fun contribute(context: ToolContext): List<Tool>
}
