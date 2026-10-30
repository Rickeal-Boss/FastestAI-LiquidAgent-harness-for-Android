package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.agent.tool.ToolContext
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec
import java.io.File

/**
 * `file_read`：读沙箱内的文本文件，支持 offset/limit 分页续读。
 *
 * 分页不是锦上添花：没有它，模型读大文件后半段的唯一手段是**原样重发同一个调用**，
 * 正好撞上同参护栏（第 3 次起不执行），于是「读不到 → 再读 → 被忽略」。
 *
 * 主构造器收 `File` 而非 `ToolContext`：`invoke` 只需要沙箱根，收窄依赖让本类能在
 * 纯 JVM 单测里直接构造（`ToolContext` 含 Android `Context`，JVM 侧无法实例化）。
 */
class FileReadTool internal constructor(private val sandboxDir: File) : Tool {

    constructor(context: ToolContext) : this(context.sandboxDir)

    override val spec: ToolSpec = ToolSpec(
        name = "file_read",
        description = "Read a text file inside the sandbox; large files can be paged with offset/limit",
        parameters = listOf(
            ToolParameter("path", ToolParamType.STRING, "path relative to the sandbox root"),
            ToolParameter(
                "offset",
                ToolParamType.INTEGER,
                "optional, character index to start from (0-based), default 0; negative is treated as 0",
                required = false,
            ),
            ToolParameter(
                "limit",
                ToolParamType.INTEGER,
                "optional, max characters to read this call, default " + FileReadPager.DEFAULT_LIMIT,
                required = false,
            ),
        ),
        category = "file",
        effect = ToolEffect.READ,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val path = stringArg(argumentsJson, "path")
        return try {
            val file = Sandbox.resolve(sandboxDir, path)
            if (!file.exists()) return failure("file not found: " + path)
            if (!file.isFile) return failure("not a file: " + path)
            val offset = FileReadPager.normalizeOffset(intArg(argumentsJson, "offset", 0))
            val limit = FileReadPager.normalizeLimit(intArg(argumentsJson, "limit", FileReadPager.DEFAULT_LIMIT))
            ToolResult(name = spec.name, ok = true, output = FileReadPager.readRange(file, offset, limit))
        } catch (t: Throwable) {
            failure(t.message ?: "read failed")
        }
    }

    private fun failure(message: String): ToolResult =
        ToolResult(name = spec.name, ok = false, errorMessage = message)
}

/**
 * `file_read` 的读取核心（纯逻辑，零 Android 依赖，JVM 直测）。
 *
 * 限量与 `AgentPolicy.maxReadToolOutputChars` **同源**：工具在源头只读这么多，省掉
 * 「读 20 万字符进内存再被循环层砍到 16000」的纯浪费，也保证本工具输出恒 ≤ 限量
 * （循环层的「已截断」标记对 `file_read` 不会触发，两层标记不叠加）。
 *
 * 长度不变量（与旧仓 `readRange` 同口径）：
 * - 内容 ≤ `limit`；完整输出 = 内容 + `\n` + 指引，指引 ≤ [MARKER_BUDGET_CHARS]；
 * - `limit >= MARKER_BUDGET_CHARS + 2` 时先为指引预留预算 ⇒ 输出 ≤ `limit`；
 * - `limit` 极小时不预留，输出可略超 `limit`（仅显式传极小 limit 时可达）；
 * - EOF 且标记放不下时优先保内容（标记纯提示，可省）。
 */
internal object FileReadPager {

    /** 默认与上限相同的读取量（见对象 KDoc 的「同源」）。 */
    val DEFAULT_LIMIT: Int = AgentPolicy().maxReadToolOutputChars

    /** 分页标记的预算字符数：指引含 offset / 总数 / 「继续读请传 offset=」，80 足够。 */
    const val MARKER_BUDGET_CHARS = 80

    /** 剩余字符计数上限：超过就放弃精确计数、改报「超过 N」，不再继续读盘。 */
    private const val COUNT_CAP_CHARS = 5_000_000L

    fun normalizeOffset(raw: Int): Int = raw.coerceAtLeast(0)

    fun normalizeLimit(raw: Int): Int =
        if (raw <= 0) DEFAULT_LIMIT else raw.coerceAtMost(DEFAULT_LIMIT)

    /**
     * 从 [offset] 起读至多 [limit] 个字符。未截断时**原样返回内容**（不加任何标记）——
     * 正常读取不该被元信息污染；只有截断 / offset 越界才追加一行可执行的续读指引。
     */
    fun readRange(file: File, offset: Int, limit: Int): String {
        val buffer = CharArray(limit)
        var filled = 0
        var startPos = offset.toLong()
        var beyondEnd = false
        var totalChars = -1L
        var overCap = false
        file.bufferedReader().use { reader ->
            var remaining = offset.toLong()
            val sink = CharArray(8192)
            while (remaining > 0) {
                val want = minOf(sink.size.toLong(), remaining).toInt()
                val r = reader.read(sink, 0, want)
                if (r < 0) break
                remaining -= r
            }
            if (remaining > 0) {
                startPos = offset - remaining
                beyondEnd = true
            }
            while (filled < limit) {
                val r = reader.read(buffer, filled, limit - filled)
                if (r < 0) break
                filled += r
            }
            if (!beyondEnd && filled == limit && reader.read() >= 0) {
                var rest = 1L
                val countSink = CharArray(8192)
                while (true) {
                    val r = reader.read(countSink)
                    if (r < 0) break
                    rest += r
                    if (rest > COUNT_CAP_CHARS) {
                        overCap = true
                        break
                    }
                }
                totalChars = startPos + limit + rest
            }
        }
        val truncated = totalChars >= 0L
        if (!truncated && !beyondEnd) {
            return if (filled == 0) "(empty file)" else String(buffer, 0, filled)
        }
        val keep = (limit - MARKER_BUDGET_CHARS - 1).coerceAtLeast(0)
        val contentLen = when {
            beyondEnd -> 0
            keep > 0 -> minOf(filled, keep)
            else -> filled
        }
        // 代理对安全：截断落点若切开 UTF-16 代理对，尾部孤立代理上屏会变 U+FFFD；
        // 回退 1 个 code unit 时 shownEnd 必须同步收缩，否则续读 offset 会跳字。
        val adjustedLen = if (contentLen > 0 && Character.isHighSurrogate(buffer[contentLen - 1])) {
            contentLen - 1
        } else {
            contentLen
        }
        val shownEnd = startPos + adjustedLen
        val content = String(buffer, 0, adjustedLen)
        val marker = if (beyondEnd) {
            "...(file has " + startPos + " chars, offset=" + offset + " is past the end)"
        } else {
            val scale = if (overCap) "more than " + COUNT_CAP_CHARS else "about " + totalChars
            "...(read [" + startPos + ", " + shownEnd + ") of " + scale +
                " chars; continue with offset=" + shownEnd + ")"
        }
        return when {
            content.isEmpty() -> marker
            content.length + 1 + marker.length > limit -> content
            else -> content + "\n" + marker
        }
    }
}
