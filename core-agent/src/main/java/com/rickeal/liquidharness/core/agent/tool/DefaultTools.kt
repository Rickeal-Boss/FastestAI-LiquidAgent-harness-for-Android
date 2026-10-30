package com.rickeal.liquidharness.core.agent.tool

import com.rickeal.liquidharness.core.agent.tool.tools.CalculatorTool
import com.rickeal.liquidharness.core.agent.tool.tools.ClipboardTool
import com.rickeal.liquidharness.core.agent.tool.tools.DateTimeTool
import com.rickeal.liquidharness.core.agent.tool.tools.FileListTool
import com.rickeal.liquidharness.core.agent.tool.tools.FileReadTool
import com.rickeal.liquidharness.core.agent.tool.tools.FileWriteTool

/**
 * 内置工具装配（6 个）：`calculator` / `datetime` / `file_read` / `file_write` /
 * `file_list` / `clipboard`。
 *
 * ## 工具面为什么**没有**网络类工具（旧仓注释必须重写）
 *
 * 旧仓是纯端侧，理由是「进程里根本没有网络栈」。新仓有网络 —— 但那是**模型 API 的
 * 传输通道**（`core-net`），不是工具能力。这里刻意不提供任何「按 URL 抓取 / 搜索」的
 * 工具：那等于给模型开一条**任意出站**的数据外发通道，而审批卡上只能显示一个 URL，
 * 用户无法判断背后带走了什么（prompt 注入 + 外发是 API 路径最现实的攻击面，
 * `04-risk-register.md`）。所以模型的 I/O 边界仍然是：沙箱目录 + 剪贴板。
 *
 * ## 本文件不做任何审批判断
 *
 * 审批（静态标志 / 参数级闸门 / 效果声明 / 档位）全部在循环层的 `ToolApprovalFlow`。
 * 在这里顺手判一次会形成第二个真值源 —— 两处口径漂移时，安全的那一处不会生效。
 */
fun builtinTools(context: ToolContext): List<Tool> = listOf(
    CalculatorTool(),
    DateTimeTool(context),
    FileReadTool(context),
    FileWriteTool(context),
    FileListTool(context),
    ClipboardTool(context),
)

/**
 * 把 6 个内置工具注册进 [registry]（允许覆盖：重复装配是合法场景）。
 *
 * 返回每个工具的注册结果而不是 `Unit`：注册失败必须是调用方**能分支**的数据
 * （`ToolRegistry` 的既有纪律：Boolean/Unit → sealed 消灭静默失败）。
 */
fun registerBuiltins(registry: ToolRegistry, context: ToolContext): List<ToolRegistry.RegisterResult> =
    registry.registerAll(builtinTools(context))
