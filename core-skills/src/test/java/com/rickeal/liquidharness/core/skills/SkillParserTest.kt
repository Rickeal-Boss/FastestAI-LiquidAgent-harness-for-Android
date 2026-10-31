package com.rickeal.liquidharness.core.skills

import com.rickeal.liquidharness.core.agent.skill.SkillBody
import com.rickeal.liquidharness.core.agent.skill.SkillCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [SkillParser] 的纯 JVM 单测：不碰文件系统，也不依赖 Android。 */
class SkillParserTest {

    @Test
    fun parsesFrontmatterAndBody() {
        val raw = """
            ---
            name: pdf-toolkit
            description: Extract text from PDF files.
            allowed-tools: file_read, file_list
            ---
            # Title

            Step one.
        """.trimIndent()

        val body = ok(SkillParser.parse(raw, "asset"))
        assertEquals("pdf-toolkit", body.meta.name)
        assertEquals("Extract text from PDF files.", body.meta.description)
        assertEquals(listOf("file_read", "file_list"), body.meta.allowedTools)
        assertEquals("asset", body.meta.source)
        assertTrue(body.body.startsWith("# Title"))
        assertTrue(body.body.endsWith("Step one."))
    }

    @Test
    fun parsesAllowedToolsSeparatedByCommas() {
        val raw = "---\nname: a1\ndescription: d\nallowed-tools: file_read,file_list,shell_exec\n---\nbody"

        val body = ok(SkillParser.parse(raw))
        assertEquals(listOf("file_read", "file_list", "shell_exec"), body.meta.allowedTools)
    }

    @Test
    fun parsesAllowedToolsSeparatedBySpaces() {
        val raw = "---\nname: a2\ndescription: d\nallowed-tools: file_read   file_list\tshell_exec\n---\nbody"

        val body = ok(SkillParser.parse(raw))
        assertEquals(listOf("file_read", "file_list", "shell_exec"), body.meta.allowedTools)
    }

    @Test
    fun rejectsMissingFrontmatter() {
        val result = SkillParser.parse("# just markdown\n\nno frontmatter here")
        assertEquals(SkillParser.REASON_MISSING_FRONTMATTER, invalid(result))
    }

    @Test
    fun rejectsUnclosedFrontmatter() {
        val result = SkillParser.parse("---\nname: a3\ndescription: d\nbody without closing fence")
        assertEquals(SkillParser.REASON_MISSING_FRONTMATTER, invalid(result))
    }

    @Test
    fun rejectsMissingName() {
        val result = SkillParser.parse("---\ndescription: d\n---\nbody")
        assertEquals(SkillParser.REASON_MISSING_NAME, invalid(result))
    }

    @Test
    fun rejectsInvalidName() {
        val result = SkillParser.parse("---\nname: Bad Name!\ndescription: d\n---\nbody")
        assertEquals(SkillParser.REASON_INVALID_NAME, invalid(result))
    }

    @Test
    fun truncatesOverlongBody() {
        val longBody = "x".repeat(SkillCatalog.MAX_BODY_CHARS + 10)
        val raw = "---\nname: a4\ndescription: d\n---\n" + longBody

        val body = ok(SkillParser.parse(raw))
        assertEquals(SkillCatalog.MAX_BODY_CHARS + SkillParser.TRUNCATION_MARK.length, body.body.length)
        assertTrue(body.body.endsWith(SkillParser.TRUNCATION_MARK))
    }

    @Test
    fun normalizesCrlfLineEndings() {
        val raw = "---\r\nname: a5\r\ndescription: d\r\n---\r\nline1\r\nline2"

        val body = ok(SkillParser.parse(raw))
        assertEquals("line1\nline2", body.body)
    }

    private fun ok(result: ParseResult): SkillBody {
        assertTrue(result is ParseResult.Ok, "expected Ok but was $result")
        return (result as ParseResult.Ok).body
    }

    private fun invalid(result: ParseResult): String {
        assertTrue(result is ParseResult.Invalid, "expected Invalid but was $result")
        return (result as ParseResult.Invalid).reasonCode
    }
}
