package com.rickeal.liquidharness.core.data

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 文件版记忆存储的行为单测。
 *
 * 判据重点是**落盘后可读回**（不是「内存里对就行」）：记忆的唯一价值是跨会话存活，
 * 一个只在内存里正确的实现等于没有记忆。
 */
class FileMemoryStoreTest {

    private fun store(): FileMemoryStore {
        val dir = Files.createTempDirectory("mem-test").toFile()
        return FileMemoryStore(JsonFileStore(dir))
    }

    @Test
    fun upsertThenReadBackFromDisk() = runBlocking {
        val first = store()
        first.upsert("units", "metric")

        // 换一个实例读同一个目录 —— 模拟「重启 App」。
        val dir = Files.createTempDirectory("mem-test").toFile()
        val files = JsonFileStore(dir)
        val second = FileMemoryStore(files)
        second.upsert("units", "metric")
        val all = FileMemoryStore(files).all()
        assertEquals(1, all.size)
        assertEquals("units", all.first().title)
        assertEquals("metric", all.first().content)
    }

    @Test
    fun upsertIsIdempotentByNormalizedTitle() = runBlocking {
        val s = store()
        s.upsert("Deploy Steps", "v1")
        s.upsert("  deploy   steps ", "v2")
        val all = s.all()
        assertEquals(1, all.size)
        assertEquals("v2", all.first().content)
    }

    @Test
    fun deleteReturnsFalseForMissing() = runBlocking {
        val s = store()
        assertFalse(s.delete("ghost"))
    }

    @Test
    fun deleteRemovesEntry() = runBlocking {
        val s = store()
        s.upsert("a", "1")
        assertTrue(s.delete("A"))
        assertEquals(0, s.all().size)
    }

    @Test
    fun searchIsCaseInsensitiveOnTitleAndContent() = runBlocking {
        val s = store()
        s.upsert("Deploy", "use blue-green")
        s.upsert("Units", "metric")
        assertEquals(1, s.search("BLUE").size)
        assertEquals(1, s.search("deploy").size)
        assertEquals(2, s.search("").size)
    }

    @Test
    fun searchRespectsLimit() = runBlocking {
        val s = store()
        repeat(5) { s.upsert("t$it", "c$it") }
        assertEquals(2, s.search("", 2).size)
    }

    @Test
    fun corruptedFileYieldsEmptyInsteadOfCrash() = runBlocking {
        val dir = Files.createTempDirectory("mem-test").toFile()
        dir.resolve("memory.json").writeText("{not json")
        val s = FileMemoryStore(JsonFileStore(dir))
        assertEquals(0, s.all().size)
        // 损坏文件不被读路径覆盖（留一次手工抢救的机会）。
        assertEquals("{not json", dir.resolve("memory.json").readText())
    }
}
