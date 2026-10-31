package com.rickeal.liquidharness.core.agent.memory

import com.rickeal.liquidharness.core.model.newId
import kotlinx.serialization.Serializable

/**
 * 一条长期记忆。
 *
 * ## 为什么用 `title` 做 upsert 键而不是 id
 *
 * 模型看不见 id（它只写 title），若按 id upsert，同一个标题会不断新增条目，
 * 记忆段迅速膨胀到把 system 撑爆。按 title upsert 是「模型可自证幂等」的唯一形态：
 * 它写两次同一标题，得到一条记忆。
 *
 * [title] 因此必须**规范化后**比较（去首尾空白、连续空白折叠、大小写不敏感）——
 * 否则 `Deploy  steps` 与 `deploy steps` 会是两条。
 */
@Serializable
data class MemoryEntry(
    val id: String = newId(),
    val title: String = "",
    val content: String = "",
    /** 写入时刻（epoch millis）。渲染进 system 时只用于排序，不进正文（省 token）。 */
    val updatedAtMillis: Long = 0L,
    /** 来源会话 id，便于「这条记忆是哪次对话写的」追溯。空 = 未知。 */
    val conversationId: String = "",
) {
    /** 渲染进 system 的一行。ASCII 分隔符，标题与正文都可能含任意字符。 */
    fun toPromptLine(): String = "- " + title + ": " + content
}

/**
 * 长期记忆端口。
 *
 * ## 为什么是端口而不是直接实现
 *
 * `core-agent` 不许依赖 `core-data`（模块边界）。实现落在 `core-data`（文件存储），
 * 单测里用内存实现 —— 记忆工具本身的行为（upsert / 搜索 / 删除）因此可以纯 JVM 测。
 *
 * ## 线程模型
 *
 * 实现必须是**线程安全**的（记忆工具可能被并发调用：一轮里模型连发两个 `memory_write`）。
 * 调用方不做加锁。
 */
interface MemoryStore {

    /** 全部记忆（按 `updatedAtMillis` 降序，最新的在前）。 */
    suspend fun all(): List<MemoryEntry>

    /** 按标题 upsert。返回写入后的条目（含新 id 或原 id）。 */
    suspend fun upsert(title: String, content: String, conversationId: String = ""): MemoryEntry

    /** 删除。返回是否真的删掉了（false = 不存在，调用方应如实回灌给模型）。 */
    suspend fun delete(title: String): Boolean

    /** 关键字搜索（大小写不敏感，匹配标题与正文）。空查询返回全部。 */
    suspend fun search(query: String, limit: Int = 20): List<MemoryEntry>

    companion object {
        /** 单条记忆正文上限（字符）。超出截断 —— 防止一次写入把 system 撑爆。 */
        const val MAX_CONTENT_CHARS: Int = 2_000

        /** 记忆段总预算（字符）。超出时按「最新优先」截断，见 [renderForPrompt]。 */
        const val MAX_PROMPT_CHARS: Int = 6_000

        /** 标题规范化：折叠空白 + 去首尾 + 小写。upsert 与 delete 共用同一口径。 */
        fun normalizeTitle(title: String): String =
            title.trim().replace(Regex("\\s+"), " ").lowercase()
    }
}

/**
 * 把记忆渲染成 system 段正文。
 *
 * 纯函数，放这里而不是 `AgentPromptComposer`：渲染规则与存储上限是同一件事，
 * 分两处写必然漂移（改了一处忘另一处，表现为「记忆被静默截断」）。
 *
 * 截断策略：按传入顺序（调用方按更新时间降序传）**从新到旧**装填，装不下就停。
 * 不按比例截断单条 —— 半条记忆比没有更危险（模型会当作完整事实引用）。
 */
fun renderMemoryForPrompt(entries: List<MemoryEntry>): String {
    if (entries.isEmpty()) return ""
    val out = StringBuilder()
    for (entry in entries) {
        val line = entry.toPromptLine()
        if (out.length + line.length + 1 > MemoryStore.MAX_PROMPT_CHARS) break
        if (out.isNotEmpty()) out.append('\n')
        out.append(line)
    }
    return out.toString()
}
