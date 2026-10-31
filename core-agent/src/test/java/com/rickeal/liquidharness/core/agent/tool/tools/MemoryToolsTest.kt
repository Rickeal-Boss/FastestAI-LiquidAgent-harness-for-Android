package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.agent.memory.MemoryEntry
import com.rickeal.liquidharness.core.agent.memory.MemoryStore
import com.rickeal.liquidharness.core.agent.memory.renderMemoryForPrompt
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 记忆三件套的行为单测。
 *
 * 用内存实现而不是文件实现：本测试要钉的是**工具语义**（upsert 幂等 / 空参数拒绝 /
 * 删除不存在时如实失败），文件 IO 的正确性由 `FileMemoryStoreTest` 单独覆盖。
 */
class MemoryToolsTest {

    /** 内存版存储。upsert 的规范化口径与真实实现共用 [MemoryStore.normalizeTitle]。 */
    private class FakeStore : MemoryStore {
        val entries = LinkedHashMap<String, MemoryEntry>()
        var nextId = 0

        override suspend fun all(): List<MemoryEntry> = entries.values.toList()

        override suspend fun upsert(title: String, content: String, conversationId: String): MemoryEntry {
            val key = MemoryStore.normalizeTitle(title)
            val entry = MemoryEntry(
                id = entries[key]?.id ?: "m" + (nextId++),
                title = title.trim(),
                content = content,
                updatedAtMillis = (nextId * 1000).toLong(),
                conversationId = conversationId,
            )
            entries[key] = entry
            return entry
        }

        override suspend fun delete(title: String): Boolean =
            entries.remove(MemoryStore.normalizeTitle(title)) != null

        override suspend fun search(query: String, limit: Int): List<MemoryEntry> {
            val q = query.lowercase()
            return entries.values
                .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.content.lowercase().contains(q) }
                .take(limit)
        }
    }

    private val store = FakeStore()
    private val write = MemoryWriteTool(store)
    private val search = MemorySearchTool(store)
    private val delete = MemoryDeleteTool(store)

    @Test
    fun writeThenSearchReturnsEntry() = runBlocking {
        val written = write.invoke("""{"title":"units","content":"user prefers metric"}""")
        assertTrue(written.ok)
        val found = search.invoke("""{"query":"metric"}""")
        assertTrue(found.ok)
        assertTrue(found.output.contains("units"))
        assertTrue(found.output.contains("user prefers metric"))
    }

    @Test
    fun writeIsUpsertByNormalizedTitle() = runBlocking {
        write.invoke("""{"title":"Deploy  Steps","content":"first"}""")
        write.invoke("""{"title":"deploy steps","content":"second"}""")
        assertEquals(1, store.entries.size)
        assertEquals("second", store.entries.values.first().content)
    }

    @Test
    fun writeRejectsEmptyTitle() = runBlocking {
        val result = write.invoke("""{"title":"  ","content":"x"}""")
        assertFalse(result.ok)
        assertEquals("title is required", result.errorMessage)
    }

    @Test
    fun writeRejectsEmptyContent() = runBlocking {
        val result = write.invoke("""{"title":"x","content":""}""")
        assertFalse(result.ok)
        assertEquals("content is required", result.errorMessage)
    }

    @Test
    fun writeTruncatesOverlongContent() = runBlocking {
        val huge = "a".repeat(MemoryStore.MAX_CONTENT_CHARS + 500)
        val result = write.invoke("""{"title":"big","content":"$huge"}""")
        assertTrue(result.ok)
        val stored = store.entries.values.first().content
        assertTrue(stored.length <= MemoryStore.MAX_CONTENT_CHARS + 20)
        assertTrue(stored.endsWith("[truncated]"))
    }

    @Test
    fun searchWithoutQueryListsRecent() = runBlocking {
        write.invoke("""{"title":"a","content":"1"}""")
        write.invoke("""{"title":"b","content":"2"}""")
        val result = search.invoke("""{}""")
        assertTrue(result.ok)
        assertTrue(result.output.contains("a: 1"))
        assertTrue(result.output.contains("b: 2"))
    }

    @Test
    fun searchMissReturnsExplicitText() = runBlocking {
        val result = search.invoke("""{"query":"nothing-matches"}""")
        assertTrue(result.ok)
        assertEquals("no memory matched", result.output)
    }

    @Test
    fun deleteExistingSucceedsAndRemoves() = runBlocking {
        write.invoke("""{"title":"gone","content":"x"}""")
        val result = delete.invoke("""{"title":"gone"}""")
        assertTrue(result.ok)
        assertEquals(0, store.entries.size)
    }

    @Test
    fun deleteMissingReportsFailure() = runBlocking {
        val result = delete.invoke("""{"title":"nope"}""")
        assertFalse(result.ok)
        assertTrue(result.errorMessage.orEmpty().contains("no memory titled"))
    }

    @Test
    fun renderRespectsTotalBudget() {
        val entries = (1..200).map {
            MemoryEntry(id = "m$it", title = "t$it", content = "c".repeat(100), updatedAtMillis = it.toLong())
        }
        val rendered = renderMemoryForPrompt(entries)
        assertTrue(rendered.length <= MemoryStore.MAX_PROMPT_CHARS)
        // 从新到旧装填：第一条必须在，最后一条必然装不下。
        assertTrue(rendered.startsWith("- t1:"))
    }

    @Test
    fun renderEmptyReturnsEmptyString() {
        assertEquals("", renderMemoryForPrompt(emptyList()))
    }
}
