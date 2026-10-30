package com.rickeal.liquidharness.core.agent.tool.tools

import java.io.File

/**
 * 沙箱路径解析 —— **所有文件工具的唯一入口**，任何新增文件工具都必须走它。
 *
 * ## 为什么必须显式做（新仓审查报告 §六.5 的未定义项）
 *
 * 旧仓把它藏在 `SandboxedFileTool` 基类里，新仓拆成三文件后基类没了，边界就必须
 * 有明确的落点 —— 否则「哪个工具忘了检查」是看不见的。
 *
 * ## 三层判据
 *
 * 1. **语义层**：空路径 / 超长路径 / 绝对路径直接拒。`File(base, "/abs")` 在 JVM
 *    语义下会得到 `/abs`（绝对 child 覆盖 base），虽然第 3 层也能拦，但先拒能给模型
 *    一条可行动的提示。
 * 2. **规范化层**：`canonicalFile` 解析 `..` 与符号链接后做前缀比对。**必须比前缀 +
 *    分隔符**：裸 `startsWith(basePath)` 会让 `/sandbox-evil` 通过 `/sandbox` 的检查。
 * 3. **相等放行**：解析结果等于沙箱根本身是合法的（`file_list` 省略 path 即此形态）。
 *
 * 失败一律抛 [SandboxEscapeException]，消息以 `path escapes sandbox` 开头 —— 这条
 * 前缀是调用侧/单测的判定锚点，也是回灌给模型的唯一错误形态（ASCII，UI 层再映射文案）。
 */
internal object Sandbox {

    /** 路径长度护栏：超长路径基本不是正常诉求，直接拒（也是防内存放大的最低成本）。 */
    private const val MAX_PATH_CHARS = 512

    /**
     * 把用户给的相对路径解析成沙箱内的真实文件。
     *
     * @throws SandboxEscapeException 路径为空 / 过长 / 绝对路径 / `..` 逃逸 / 符号链接落到沙箱外。
     */
    fun resolve(root: File, rawPath: String): File {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) throw SandboxEscapeException("path escapes sandbox: empty path")
        if (trimmed.length > MAX_PATH_CHARS) {
            throw SandboxEscapeException("path escapes sandbox: path longer than " + MAX_PATH_CHARS + " chars")
        }
        if (isAbsoluteLike(trimmed)) {
            throw SandboxEscapeException(
                "path escapes sandbox: absolute paths are not allowed, use a path relative to the sandbox root " +
                    "(for example \"note.txt\" or \"docs/a.md\")",
            )
        }
        val base = root.canonicalFile
        val target = base.resolve(trimmed).canonicalFile
        val basePath = base.path
        if (target.path != basePath && !target.path.startsWith(basePath + File.separator)) {
            throw SandboxEscapeException("path escapes sandbox: " + trimmed)
        }
        return target
    }

    /** POSIX 根、Windows 盘符、UNC 前缀都算绝对路径（跨平台同一判据）。 */
    private fun isAbsoluteLike(path: String): Boolean {
        if (path.startsWith("/") || path.startsWith("\\")) return true
        if (path.length >= 3 && path[1] == ':' && (path[2] == '/' || path[2] == '\\')) return true
        return false
    }
}

/** 沙箱边界违规。[message] 以 `path escapes sandbox` 开头，可直接作为 `errorMessage`。 */
internal class SandboxEscapeException(message: String) : Exception(message)
