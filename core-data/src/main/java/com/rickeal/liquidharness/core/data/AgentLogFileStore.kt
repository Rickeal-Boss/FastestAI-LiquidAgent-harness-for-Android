package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * 诊断日志落盘：**滚动文件 + 出口脱敏**。
 *
 * 与 `core-agent` 的 `AgentLogStore`（进程内 200 条环形缓冲）分工明确：
 * 那个是「崩溃前最后 200 条」的黑匣子，本类负责**跨进程存活**的那一份 ——
 * 用户报障时能取到上次启动的现场。
 *
 * ## 三条硬约束
 *
 * 1. **出口先脱敏再截断**，且正则只有一份（`core-model` 的 `SecretRedactor`，
 *    经 `AgentLogStore.sanitizeUserFacing` 复用）。日志文件在设备私有目录，
 *    但它会被用户主动导出、也会进 bug report —— 等同于外发。
 * 2. **总量有上限**：[maxFiles] 个文件 × [maxFileBytes]，超出滚掉最旧的。
 *    端侧存储不是无限的，一次 SSE 调试能刷出几十 MB。
 * 3. **绝不因为日志失败影响主流程**：所有异常在内部吞掉（但取消必须放行）。
 */
class AgentLogFileStore(
    private val dir: File,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
) {

    suspend fun append(level: String, message: String) = withContext(Dispatchers.IO) {
        try {
            appendBlocking(level, message)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            // 日志写不进去不该让 run 失败
        }
    }

    /** 最近 [limit] 行，**时间正序**（最旧 → 最新）。供诊断页 / 用户导出使用。 */
    suspend fun recentLines(limit: Int = 200): List<String> = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext emptyList()
        val all = ArrayList<String>()
        // 从最旧的文件读到最新：文件名序号越大越旧
        for (index in maxFiles - 1 downTo 0) {
            val file = fileFor(index)
            if (!file.exists()) continue
            try {
                file.forEachLine { all.add(it) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                // 跳过读不出的旧文件
            }
        }
        if (all.size <= limit) all else all.subList(all.size - limit, all.size).toList()
    }

    private fun appendBlocking(level: String, message: String) {
        if (!dir.exists() && !dir.mkdirs()) return
        val sanitized = AgentLogStore.sanitizeUserFacing(message)
        val line = Instant.now().toString() + " " + level + " " + sanitized + "\n"
        val bytes = line.toByteArray(Charsets.UTF_8)
        val current = fileFor(0)
        if (current.exists() && current.length() + bytes.size > maxFileBytes) rotate()
        fileFor(0).appendBytes(bytes)
    }

    /**
     * 滚动：`agent-0.log` → `agent-1.log` → … 最旧的被删除。
     *
     * 用「序号文件名」而不是按日期命名：日期命名在时钟回拨时会产生同名文件，
     * 于是「滚动」变成「追加」，上限失效。
     */
    private fun rotate() {
        val oldest = fileFor(maxFiles - 1)
        if (oldest.exists()) oldest.delete()
        for (index in maxFiles - 2 downTo 0) {
            val from = fileFor(index)
            if (from.exists()) from.renameTo(fileFor(index + 1))
        }
    }

    private fun fileFor(index: Int): File = File(dir, "agent-$index.log")

    companion object {
        /** 256 KB × 3 ≈ 768 KB 上限：够放下一次完整 run 的关键决策点。 */
        const val DEFAULT_MAX_FILE_BYTES: Long = 256L * 1024L
        const val DEFAULT_MAX_FILES: Int = 3
    }
}
