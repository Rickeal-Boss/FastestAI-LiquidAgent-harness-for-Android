package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.agent.tool.ToolContext
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec
import java.io.File

/**
 * `file_list`：列出沙箱内某个目录的直接子项。
 *
 * `path` 可省略（默认沙箱根）—— 归一化由 [fileListPathArg] 负责：空串会被
 * [Sandbox.resolve] fail-closed 拒绝，与「省略 = 根目录」的 spec 承诺正好相反。
 *
 * 条数上限 [MAX_LIST_ENTRIES]：万级条目的目录一次列完会把上下文窗口挤爆，
 * 而且对模型没有任何额外信息量。
 */
class FileListTool internal constructor(private val sandboxDir: File) : Tool {

    constructor(context: ToolContext) : this(context.sandboxDir)

    companion object {
        /** 列目录条数上限。 */
        private const val MAX_LIST_ENTRIES = 200
    }

    override val spec: ToolSpec = ToolSpec(
        name = "file_list",
        description = "List files and directories inside the sandbox",
        parameters = listOf(
            ToolParameter(
                "path",
                ToolParamType.STRING,
                "subdirectory relative to the sandbox root, defaults to the root",
                required = false,
            ),
        ),
        category = "file",
        effect = ToolEffect.READ,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val path = fileListPathArg(stringArg(argumentsJson, "path"))
        return try {
            val dir = Sandbox.resolve(sandboxDir, path)
            if (!dir.exists()) return failure("directory not found: " + path)
            if (!dir.isDirectory) return failure("not a directory: " + path)
            val all = (dir.listFiles() ?: emptyArray()).sortedBy { it.name }
            val listed = all.take(MAX_LIST_ENTRIES)
            val listing = listed.joinToString("\n") { entry ->
                if (entry.isDirectory) "[DIR] " + entry.name else "[FILE] " + entry.name + " (" + entry.length() + " B)"
            }
            val suffix = if (all.size > listed.size) {
                "\n...(" + all.size + " entries total, showing the first " + listed.size +
                    "; use a more specific subdirectory to narrow the result)"
            } else {
                ""
            }
            ToolResult(name = spec.name, ok = true, output = (listing + suffix).ifBlank { "(empty directory)" })
        } catch (t: Throwable) {
            failure(t.message ?: "list failed")
        }
    }

    private fun failure(message: String): ToolResult =
        ToolResult(name = spec.name, ok = false, errorMessage = message)
}
