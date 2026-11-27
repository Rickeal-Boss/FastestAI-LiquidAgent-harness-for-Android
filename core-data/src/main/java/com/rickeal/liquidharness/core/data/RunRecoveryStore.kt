package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.agent.journal.AgentRunJournal

/**
 * 杀进程恢复判据（R5 / W61）：从**落盘** journal 配对 started/settled。
 *
 * 内存 buffer 进程重启即空，恢复卡绝不能读 `AgentRunJournal.findUnsettled()` —— 那是
 * 进程内存态判据，只在同进程内有意义。本类只读文件，与内核的端口分工互不越界。
 */
class RunRecoveryStore(
    private val files: FileRunJournalStore,
    private val journal: AgentRunJournal,
) {
    /** 返回最新未结算 runId；无则 null。 */
    suspend fun findUnsettled(): String? {
        val lines = files.readAll()
        val settled = HashSet<String>()
        for (line in lines) {
            if (line.startsWith("run_settled ")) {
                AgentRunJournal.field(line, "runId")?.let { settled.add(it) }
            }
        }
        for (line in lines.asReversed()) {
            if (line.startsWith("run_started ")) {
                val id = AgentRunJournal.field(line, "runId") ?: continue
                if (id !in settled) return id
            }
        }
        return null
    }

    /** 「继续」：补 settled 标记消除未结算（不还原正文 —— 会话本就不落盘）。 */
    fun continueRun(runId: String) = journal.runSettled(runId, 0L, 0, false)

    /** 「丢弃」：清内存 + 清落盘，下次启动不再弹卡。 */
    fun discard() {
        journal.clear()
        files.clearBlocking()
    }
}
