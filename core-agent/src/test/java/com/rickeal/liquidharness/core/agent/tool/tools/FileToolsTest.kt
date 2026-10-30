package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.model.ToolEffect
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 三个文件工具（`file_read` / `file_write` / `file_list`）合并测试。
 *
 * 重点两条：
 * 1. **沙箱边界**：`../../etc/passwd` 必须被拒且错误信息含 `escape`（W2 审查报告 §六.5）；
 * 2. **读写往返 + 分页**：写进去的内容能原样读回，大文件能按 offset 续读。
 *
 * 用 [TemporaryFolder] 而非真实沙箱目录：工具主构造器只收 `File`，纯 JVM 可构造。
 */
class FileToolsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var read: FileReadTool
    private lateinit var write: FileWriteTool
    private lateinit var list: FileListTool

    @Before
    fun setUp() {
        root = folder.newFolder("sandbox")
        read = FileReadTool(root)
        write = FileWriteTool(root)
        list = FileListTool(root)
    }

    private fun writeFile(path: String, content: String) =
        runBlocking { write.invoke("{\"path\":\"" + path + "\",\"content\":\"" + content + "\"}") }

    private fun readFile(json: String) = runBlocking { read.invoke(json) }

    private fun listDir(json: String) = runBlocking { list.invoke(json) }

    // ── 往返 ─────────────────────────────────────────────────────────────────

    @Test
    fun writeThenReadRoundTrip() {
        val written = writeFile("a.txt", "hello")
        assertTrue(written.ok)
        assertFalse(written.indeterminate, "a completed write must be determinate")

        val result = readFile("{\"path\":\"a.txt\"}")
        assertTrue(result.ok)
        assertEquals("hello", result.output)
    }

    @Test
    fun writeOverwritesExistingFile() {
        writeFile("a.txt", "first")
        writeFile("a.txt", "second")
        assertEquals("second", readFile("{\"path\":\"a.txt\"}").output)
    }

    @Test
    fun writeCreatesParentDirectories() {
        assertTrue(writeFile("docs/notes/a.md", "note").ok)
        assertTrue(File(root, "docs/notes/a.md").isFile)
    }

    @Test
    fun readMissingFileFails() {
        val result = readFile("{\"path\":\"missing.txt\"}")
        assertFalse(result.ok)
        assertTrue(result.errorMessage!!.contains("file not found"))
    }

    @Test
    fun readDirectoryFails() {
        folder.newFolder("sandbox", "sub")
        val result = readFile("{\"path\":\"sub\"}")
        assertFalse(result.ok)
        assertTrue(result.errorMessage!!.contains("not a file"))
    }

    // ── 沙箱边界 ─────────────────────────────────────────────────────────────

    @Test
    fun readRejectsDotDotEscape() {
        val result = readFile("{\"path\":\"../../etc/passwd\"}")
        assertFalse(result.ok)
        assertTrue(result.errorMessage!!.contains("escape"), "must report a sandbox escape, got: " + result.errorMessage)
    }

    @Test
    fun readRejectsAbsolutePath() {
        val result = readFile("{\"path\":\"/etc/passwd\"}")
        assertFalse(result.ok)
        assertTrue(result.errorMessage!!.contains("escape"))
    }

    @Test
    fun readRejectsEmptyPath() {
        val result = readFile("{\"path\":\"  \"}")
        assertFalse(result.ok)
        assertTrue(result.errorMessage!!.contains("escape"))
    }

    @Test
    fun writeRejectsEscapeAndCreatesNothingOutside() {
        val outside = File(root.parentFile, "escaped.txt")
        val result = writeFile("../escaped.txt", "leak")
        assertFalse(result.ok)
        assertTrue(result.errorMessage!!.contains("escape"))
        assertFalse(outside.exists(), "sandbox escape must not create files outside the root")
    }

    @Test
    fun listRejectsEscape() {
        val result = listDir("{\"path\":\"..\"}")
        assertFalse(result.ok)
        assertTrue(result.errorMessage!!.contains("escape"))
    }

    // ── 列目录 ───────────────────────────────────────────────────────────────

    @Test
    fun listShowsFilesAndDirectories() {
        writeFile("a.txt", "x")
        folder.newFolder("sandbox", "sub")

        val result = listDir("{}")
        assertTrue(result.ok)
        assertTrue(result.output.contains("[FILE] a.txt"))
        assertTrue(result.output.contains("[DIR] sub"))
    }

    @Test
    fun listDefaultsToSandboxRootWhenPathOmitted() {
        writeFile("root.txt", "x")
        val result = listDir("{}")
        assertTrue(result.ok)
        assertTrue(result.output.contains("root.txt"))
    }

    @Test
    fun listMissingDirectoryFails() {
        val result = listDir("{\"path\":\"nope\"}")
        assertFalse(result.ok)
        assertTrue(result.errorMessage!!.contains("directory not found"))
    }

    @Test
    fun listEmptyDirectorySaysSo() {
        val result = listDir("{}")
        assertTrue(result.ok)
        assertEquals("(empty directory)", result.output)
    }

    @Test
    fun fileListPathArgNormalizesBlankToRoot() {
        assertEquals(".", fileListPathArg(""))
        assertEquals(".", fileListPathArg("   "))
        assertEquals("docs", fileListPathArg(" docs "))
    }

    // ── 分页 ─────────────────────────────────────────────────────────────────

    @Test
    fun readHonorsOffsetAndLimit() {
        // 内容以 '1' 开头（1..500 逐位取模）⇒ offset=3 跳过 "123"，从 '4' 起读 4 个字符。
        val content = (1..500).joinToString("") { (it % 10).toString() }
        writeFile("big.txt", content)

        val result = readFile("{\"path\":\"big.txt\",\"offset\":3,\"limit\":4}")
        assertTrue(result.ok)
        assertEquals("4567", result.output)
    }

    @Test
    fun readTruncationPointsAtNextOffset() {
        val content = (1..500).joinToString("") { (it % 10).toString() }
        writeFile("big.txt", content)

        val result = readFile("{\"path\":\"big.txt\",\"limit\":200}")
        assertTrue(result.ok)
        assertTrue(result.output.contains("continue with offset="), "got: " + result.output)
        assertTrue(result.output.length <= 200, "output must respect the limit budget")
    }

    @Test
    fun readOffsetPastEndIsReported() {
        writeFile("small.txt", "abc")
        val result = readFile("{\"path\":\"small.txt\",\"offset\":10}")
        assertTrue(result.ok)
        assertTrue(result.output.contains("past the end"))
    }

    @Test
    fun specEffectsAreDeclaredExplicitly() {
        // ToolSpec.effect 默认 WRITE（fail-closed）⇒ 读类工具必须显式声明 READ。
        assertEquals(ToolEffect.READ, read.spec.effect)
        assertEquals(ToolEffect.READ, list.spec.effect)
        assertEquals(ToolEffect.WRITE, write.spec.effect)
        assertTrue(write.spec.requiresConfirmation)
        assertTrue(write.spec.dangerous)
    }
}
