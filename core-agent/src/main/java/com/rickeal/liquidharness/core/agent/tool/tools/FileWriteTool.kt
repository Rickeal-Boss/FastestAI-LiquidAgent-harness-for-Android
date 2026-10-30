package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.agent.tool.ToolContext
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * `file_write`：把内容写入沙箱内的文件（覆盖）。
 *
 * 双标 `dangerous + requiresConfirmation`：写文件是**不可逆**副作用，执行前必须过审批
 * （静态标志由循环层的审批闸门消费；本类不做任何审批判断）。
 *
 * 原子写（tmp + move）：直接 `writeText` 若进程死在写一半，会留下半截文件污染沙箱。
 *
 * ⚠️ 相对旧仓的**唯一实现变更**：`File.renameTo` 换成 `Files.move(REPLACE_EXISTING,
 * ATOMIC_MOVE)`。旧仓自己在注释里承认 `renameTo` 在 Windows 上**不能覆盖**已存在的目标，
 * 桌面 JVM 单测的「重复写同名文件」必失败；NIO 的 `REPLACE_EXISTING` 在两个平台都是
 * 覆盖语义，`ATOMIC_MOVE` 不支持时（少数文件系统）降级为普通 move。
 */
class FileWriteTool internal constructor(private val sandboxDir: File) : Tool {

    constructor(context: ToolContext) : this(context.sandboxDir)

    companion object {
        /**
         * 单次写入字符上限。模型失控时的「无限写」是最廉价的资源耗尽攻击：
         * 一次 writeText 可以把磁盘填满。256K 字符（≈512KB~768KB UTF-8）对笔记/代码
         * 片段绰绰有余，超出就该让模型分批或自行收敛。
         */
        private const val MAX_WRITE_CHARS = 262_144
    }

    override val spec: ToolSpec = ToolSpec(
        name = "file_write",
        description = "Write text to a file inside the sandbox (overwrites the file)",
        parameters = listOf(
            ToolParameter("path", ToolParamType.STRING, "path relative to the sandbox root"),
            ToolParameter("content", ToolParamType.STRING, "text to write"),
        ),
        dangerous = true,
        requiresConfirmation = true,
        category = "file",
        effect = ToolEffect.WRITE,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val path = stringArg(argumentsJson, "path")
        val content = stringArg(argumentsJson, "content")
        return try {
            if (content.length > MAX_WRITE_CHARS) {
                return failure(
                    "content too long (" + content.length + " chars, limit " + MAX_WRITE_CHARS +
                        "): split the write or compress the content",
                )
            }
            val file = Sandbox.resolve(sandboxDir, path)
            if (file.isDirectory) return failure("target is a directory: " + path)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp_" + System.nanoTime())
            try {
                tmp.writeText(content)
                moveReplacing(tmp, file)
            } finally {
                if (tmp.exists()) tmp.delete()
            }
            ToolResult(
                name = spec.name,
                ok = true,
                output = "wrote " + content.length + " chars to " + path,
                indeterminate = false,
            )
        } catch (t: Throwable) {
            failure(t.message ?: "write failed")
        }
    }

    /** 覆盖式移动：优先原子，文件系统不支持时降级（内容已完整落盘，仅非原子）。 */
    private fun moveReplacing(from: File, to: File) {
        try {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (t: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun failure(message: String): ToolResult =
        ToolResult(name = spec.name, ok = false, errorMessage = message)
}
