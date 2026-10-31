package com.rickeal.liquidharness.feature.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 沙箱浏览的纯逻辑单测（JVM，不碰 Android）。
 *
 * 只钉三件会**静默出错**的事：
 *  1. 预览截断的边界（少截一个字符看不出来，多读整个文件就是 OOM）；
 *  2. 可预览扩展名白名单（放错会让二进制文件被当文本读）；
 *  3. 大小格式化的三档边界（1023 / 1024 / 1MB）。
 *
 * 目录列取的排序与越界防护不在这里：`listDir` 依赖真实文件系统语义（canonical、
 * 符号链接），在 Windows 上跑符号链接用例会假红（旧仓 `SandboxFileScannerTest` 的
 * 已知失败同源），故只做人工/真机核验。
 */
class SandboxFilesTest {

    @Test
    fun previewHeadKeepsShortFileWhole() {
        val file = tempFile("short.txt", "hello sandbox")
        try {
            val head = readPreviewHead(file)
            assertEquals("hello sandbox", head.first)
            assertFalse(head.second)
        } finally {
            file.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun previewHeadTruncatesAtLimit() {
        val payload = "a".repeat(PREVIEW_LIMIT_CHARS + 500)
        val file = tempFile("long.log", payload)
        try {
            val head = readPreviewHead(file)
            assertEquals(PREVIEW_LIMIT_CHARS, head.first.length)
            assertTrue(head.second)
            assertEquals(payload.take(PREVIEW_LIMIT_CHARS), head.first)
        } finally {
            file.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun previewHeadDoesNotFlagExactLimitAsTruncated() {
        val payload = "b".repeat(PREVIEW_LIMIT_CHARS)
        val file = tempFile("exact.txt", payload)
        try {
            val head = readPreviewHead(file)
            assertEquals(PREVIEW_LIMIT_CHARS, head.first.length)
            assertFalse(head.second)
        } finally {
            file.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun previewableCoversTextExtensionsOnly() {
        assertTrue(isPreviewable(entry("notes.md")))
        assertTrue(isPreviewable(entry("run.log")))
        assertTrue(isPreviewable(entry("DATA.JSON")))
        assertFalse(isPreviewable(entry("photo.png")))
        assertFalse(isPreviewable(entry("archive.zip")))
        assertFalse(isPreviewable(entry("noextension")))
        assertFalse(isPreviewable(entry("folder", isDirectory = true)))
    }

    @Test
    fun formatBytesUsesThreeBuckets() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("1023 KB", formatBytes(1024L * 1023))
        assertEquals("1.0 MB", formatBytes(1024L * 1024))
    }

    private fun entry(name: String, isDirectory: Boolean = false): SandboxEntry =
        SandboxEntry(name, isDirectory, 0L, 0L)

    /** 每个用例独立目录，避免并发写同一文件（`@Test` 方法可能并行执行）。 */
    private fun tempFile(name: String, content: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "sandbox-test-" + System.nanoTime())
        assertTrue(dir.mkdirs())
        val file = File(dir, name)
        file.writeText(content)
        return file
    }
}
