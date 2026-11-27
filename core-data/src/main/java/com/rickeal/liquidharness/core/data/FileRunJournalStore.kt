package com.rickeal.liquidharness.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * run journal 的落盘端（R5 / W61）：把 `AgentRunJournal` 的固定形态事件行写到设备私有目录。
 *
 * ## 与 `AgentLogFileStore` 的分工
 *
 * 日志与 journal 是两份**不同生命周期**的证据：
 * - `AgentLogFileStore`：诊断流水（256KB × 3 轮转），大量、可丢、给人看；
 * - 本类：run 边界（run_started / run_settled），少量、不可丢、给**恢复判据**用。
 *
 * 因此不用日志的「探针白名单」过滤 —— journal 行本身已是固定形态（core-agent 侧保证
 * 只有数字 / 布尔 / uuid / 枚举），不含用户内容，可直接整行落盘。
 *
 * ## 有界与失败语义
 *
 * 单个文件超过 [MAX_BYTES] 时**整文件截断到最近 N 行**，避免长会话无限增长；
 * 写失败返回 false 但**不影响主流程**（内核 `AgentRunJournal.sink` 已吞异常）。
 * `CancellationException` 原样上抛（与 `JsonFileStore` 同纪律：不能把取消读成 IO 失败）。
 */
class FileRunJournalStore(
    private val file: File,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /** 同步追加一行：内核的 sink 是同步 lambda（`AgentLogStore` 同款），IO 不能挂起。 */
    fun append(line: String): Boolean = try {
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false
        file.appendText(line + "\n")
        maybeRotate()
        true
    } catch (c: CancellationException) {
        throw c
    } catch (_: Throwable) {
        false
    }

    /** 读全部行（供恢复判据与真机取证）；文件不存在返回空。 */
    suspend fun readAll(): List<String> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            file.readLines()
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** 同步清空（对话框 onClick 用；[clear] 供协程侧调用）。 */
    fun clearBlocking(): Boolean = try {
        if (file.exists()) file.delete() else true
    } catch (_: Throwable) {
        false
    }

    /** 清空（对应「丢弃恢复卡」：放弃所有未结算 run）。 */
    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) file.delete()
        } catch (_: Throwable) {
            // 删除失败不阻塞用户操作
        }
    }

    /** 超限保留最近 [KEEP_LINES] 行。用临时文件 + rename，避免半写。 */
    private fun maybeRotate() {
        if (file.length() <= MAX_BYTES) return
        val lines = try {
            file.readLines()
        } catch (_: Throwable) {
            return
        }
        if (lines.size <= KEEP_LINES) return
        val tmp = File(file.parentFile, file.name + ".tmp." + nowMillis())
        try {
            tmp.writeText(lines.takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
            if (!tmp.renameTo(file)) {
                tmp.delete()
            }
        } catch (_: Throwable) {
            tmp.delete()
        }
    }

    companion object {
        const val FILE_NAME: String = "run-journal.jsonl"
        const val MAX_BYTES: Long = 256L * 1024L
        const val KEEP_LINES: Int = 200
    }
}
