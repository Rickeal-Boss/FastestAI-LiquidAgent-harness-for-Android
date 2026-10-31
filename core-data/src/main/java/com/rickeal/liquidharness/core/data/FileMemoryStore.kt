package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.agent.memory.MemoryEntry
import com.rickeal.liquidharness.core.agent.memory.MemoryStore
import com.rickeal.liquidharness.core.model.newId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer

/**
 * 文件版长期记忆（`memory.json`，一份 JSON 数组）。
 *
 * ## 为什么整份重写而不是 append-only 日志
 *
 * 记忆的读路径是**每 run 一次**（进 system），写路径是「模型偶尔写一条」。
 * 数据量上限被 [MemoryStore.MAX_PROMPT_CHARS] 间接锁死在几十条，整份重写（< 100 KB）
 * 的代价可以忽略，换来的是「不存在需要压缩/回放的日志格式」。
 *
 * ## 并发
 *
 * 一轮里模型可能连发两个 `memory_write`（并行工具批）。所有读改写都在 [mutex] 内，
 * 否则两次 upsert 各写一次文件会丢掉其中一次 —— 与 `EndpointStore` 同一处理。
 *
 * ## 读损坏时的取舍
 *
 * 文件损坏 ⇒ 返回空列表（不是抛异常）：记忆是「锦上添花」，让 App 起不来是本末倒置。
 * 但**不覆盖**损坏文件 —— 下一次写入才覆盖，给用户留一次手工抢救的机会。
 */
class FileMemoryStore(
    private val files: JsonFileStore,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : MemoryStore {

    private val mutex = Mutex()

    override suspend fun all(): List<MemoryEntry> = mutex.withLock { load() }

    override suspend fun upsert(
        title: String,
        content: String,
        conversationId: String,
    ): MemoryEntry = mutex.withLock {
        val key = MemoryStore.normalizeTitle(title)
        val current = load()
        val existing = current.firstOrNull { MemoryStore.normalizeTitle(it.title) == key }
        val entry = MemoryEntry(
            id = existing?.id ?: newId(),
            title = title.trim(),
            content = content.trim(),
            updatedAtMillis = nowMillis(),
            conversationId = conversationId,
        )
        val next = current.filterNot { MemoryStore.normalizeTitle(it.title) == key } + entry
        persist(next)
        entry
    }

    override suspend fun delete(title: String): Boolean = mutex.withLock {
        val key = MemoryStore.normalizeTitle(title)
        val current = load()
        val next = current.filterNot { MemoryStore.normalizeTitle(it.title) == key }
        if (next.size == current.size) return@withLock false
        persist(next)
        true
    }

    override suspend fun search(query: String, limit: Int): List<MemoryEntry> = mutex.withLock {
        val q = query.trim().lowercase()
        val all = load().sortedByDescending { it.updatedAtMillis }
        if (q.isEmpty()) return@withLock all.take(limit)
        all.filter { entry ->
            entry.title.lowercase().contains(q) || entry.content.lowercase().contains(q)
        }.take(limit)
    }

    private suspend fun load(): List<MemoryEntry> =
        files.read(FILE_NAME, ListSerializer(MemoryEntry.serializer())).orEmpty()

    private suspend fun persist(entries: List<MemoryEntry>) {
        files.write(FILE_NAME, ListSerializer(MemoryEntry.serializer()), entries)
    }

    private companion object {
        const val FILE_NAME = "memory.json"
    }
}
