package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.agent.skill.SkillBody
import com.rickeal.liquidharness.core.agent.skill.SkillCatalog
import com.rickeal.liquidharness.core.agent.skill.SkillMeta
import com.rickeal.liquidharness.core.agent.skill.renderSkillsSection
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 技能两件套 + 一级披露渲染的行为单测。 */
class SkillToolsTest {

    private class FakeCatalog(private val bodies: List<SkillBody>) : SkillCatalog {
        override suspend fun list(): List<SkillMeta> = bodies.map { it.meta }
        override suspend fun load(name: String): SkillBody? = bodies.firstOrNull { it.meta.name == name }
        override suspend fun search(query: String): List<SkillMeta> {
            val q = query.lowercase()
            return bodies.map { it.meta }
                .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
        }
    }

    private fun body(name: String, desc: String = "d", tools: List<String> = emptyList(), text: String = "step 1") =
        SkillBody(SkillMeta(name = name, description = desc, allowedTools = tools), text)

    @Test
    fun listRendersNameAndTools() = runBlocking {
        val tool = SkillListTool(FakeCatalog(listOf(body("pdf-toolkit", "extract pdf", listOf("file_read")))))
        val result = tool.invoke("{}")
        assertTrue(result.ok)
        assertTrue(result.output.contains("pdf-toolkit"))
        assertTrue(result.output.contains("tools: file_read"))
    }

    @Test
    fun listWithoutToolsOmitsToolSuffix() = runBlocking {
        val tool = SkillListTool(FakeCatalog(listOf(body("notes"))))
        val result = tool.invoke("{}")
        assertTrue(result.ok)
        assertFalse(result.output.contains("tools:"))
    }

    @Test
    fun listEmptyIsExplicit() = runBlocking {
        val tool = SkillListTool(FakeCatalog(emptyList()))
        assertEquals("no skill installed", tool.invoke("{}").output)
    }

    @Test
    fun loadReturnsBoundaryNoticeAndBody() = runBlocking {
        val tool = SkillLoadTool(FakeCatalog(listOf(body("pdf-toolkit", text = "1. read the file"))))
        val result = tool.invoke("""{"name":"pdf-toolkit"}""")
        assertTrue(result.ok)
        assertTrue(result.output.contains("[SKILL BODY]"))
        assertTrue(result.output.contains("[SKILL pdf-toolkit]"))
        assertTrue(result.output.contains("1. read the file"))
    }

    @Test
    fun loadMissingSkillFailsExplicitly() = runBlocking {
        val tool = SkillLoadTool(FakeCatalog(emptyList()))
        val result = tool.invoke("""{"name":"ghost"}""")
        assertFalse(result.ok)
        assertTrue(result.errorMessage.orEmpty().contains("skill not found"))
    }

    @Test
    fun loadRequiresName() = runBlocking {
        val tool = SkillLoadTool(FakeCatalog(emptyList()))
        val result = tool.invoke("""{}""")
        assertFalse(result.ok)
        assertEquals("name is required", result.errorMessage)
    }

    @Test
    fun loadTruncatesOverlongBody() = runBlocking {
        val huge = "x".repeat(SkillCatalog.MAX_BODY_CHARS)
        val tool = SkillLoadTool(FakeCatalog(listOf(body("big", text = huge))))
        val result = tool.invoke("""{"name":"big"}""")
        assertTrue(result.ok)
        assertTrue(result.output.endsWith("[skill body truncated]"))
    }

    @Test
    fun sectionIsSortedByName() {
        val section = renderSkillsSection(
            listOf(body("zeta", "z").meta, body("alpha", "a").meta)
        )
        val lines = section.split("\n")
        assertTrue(lines[0].startsWith("- alpha:"))
        assertTrue(lines[1].startsWith("- zeta:"))
    }

    @Test
    fun sectionIsCapped() {
        val many = (1..40).map { body("skill" + it, "d").meta }
        val section = renderSkillsSection(many)
        assertEquals(SkillCatalog.MAX_DISCLOSED, section.split("\n").size)
    }

    @Test
    fun sectionOfEmptyIsEmpty() {
        assertEquals("", renderSkillsSection(emptyList()))
    }
}
