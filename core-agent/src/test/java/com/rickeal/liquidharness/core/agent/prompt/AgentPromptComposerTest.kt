package com.rickeal.liquidharness.core.agent.prompt

import com.rickeal.liquidharness.core.agent.AgentRequest
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 提示词组装单测。
 *
 * 三条硬判据（`00-brief.md` A8 + A6）：
 * 1. 段顺序固定，**停止条件恒在末尾**（尾部权重最高，是让模型「自己会停」的主要手段）；
 * 2. `skillsSection` 为空 ⇒ 技能段**完全不出现**（空段会白占 token 且改字节）；
 * 3. 技能正文（二级披露）永远不进 system —— 本类的入参只有 tier-1 元数据。
 */
class AgentPromptComposerTest {

    private fun request(
        systemInstruction: String = "",
        skillsSection: String = "",
        memoryText: String? = null,
    ): AgentRequest = AgentRequest(
        endpoint = RemoteEndpoint(),
        systemInstruction = systemInstruction,
        skillsSection = skillsSection,
        memoryText = memoryText,
    )

    private fun tool(name: String): ToolSpec = ToolSpec(name = name, description = "d")

    @Test
    fun stopConditionsIsAlwaysTheLastSection() {
        val sections = AgentPromptComposer.sections(request(systemInstruction = "be brief"), emptyList())

        assertEquals(AgentPromptComposer.STOP_CONDITIONS, sections.last())
    }

    @Test
    fun stopConditionsIsLastEvenWithEveryOptionalSection() {
        val sections = AgentPromptComposer.sections(
            request(systemInstruction = "be brief", skillsSection = "- pdf: read pdf", memoryText = "- user likes kotlin"),
            listOf(tool("memory_write")),
        )

        assertEquals(AgentPromptComposer.STOP_CONDITIONS, sections.last())
        assertTrue(sections.size >= 5)
    }

    @Test
    fun skillsSectionIsAbsentWhenEmpty() {
        val sections = AgentPromptComposer.sections(request(skillsSection = ""), emptyList())
        val text = AgentPromptComposer.compose(request(skillsSection = ""), emptyList())

        assertFalse(sections.any { it.startsWith(AgentPromptComposer.SKILLS_SECTION_PREFIX) })
        assertFalse(text.contains(AgentPromptComposer.SKILLS_SECTION_PREFIX))
    }

    @Test
    fun sectionsFollowDocumentedOrder() {
        val sections = AgentPromptComposer.sections(
            request(systemInstruction = "be brief", skillsSection = "- pdf: read pdf", memoryText = "- memo"),
            listOf(tool("memory_write")),
        )

        assertEquals("be brief", sections[0])
        assertEquals(AgentPromptComposer.TOOL_GUARDRAILS, sections[1])
        assertTrue(sections[2].startsWith(AgentPromptComposer.SKILLS_SECTION_PREFIX))
        assertTrue(sections[3].startsWith(AgentPromptComposer.MEMORY_SECTION_PREFIX))
        assertEquals(AgentPromptComposer.MEMORY_MAINTENANCE, sections[4])
        assertEquals(AgentPromptComposer.STOP_CONDITIONS, sections[5])
    }

    @Test
    fun toolGuardrailsOnlyAppearWhenToolsExist() {
        val withoutTools = AgentPromptComposer.sections(request(), emptyList())
        val withTools = AgentPromptComposer.sections(request(), listOf(tool("calculator")))

        assertFalse(withoutTools.contains(AgentPromptComposer.TOOL_GUARDRAILS))
        assertTrue(withTools.contains(AgentPromptComposer.TOOL_GUARDRAILS))
    }

    @Test
    fun memoryMaintenanceIsGatedOnMemoryWriteTool() {
        val withoutMemoryTool = AgentPromptComposer.sections(request(), listOf(tool("calculator")))
        val withMemoryTool = AgentPromptComposer.sections(request(), listOf(tool("memory_write")))

        assertFalse(withoutMemoryTool.contains(AgentPromptComposer.MEMORY_MAINTENANCE))
        assertTrue(withMemoryTool.contains(AgentPromptComposer.MEMORY_MAINTENANCE))
    }

    @Test
    fun composeJoinsSectionsWithBlankLine() {
        val text = AgentPromptComposer.compose(request(systemInstruction = "be brief"), emptyList())

        assertEquals("be brief\n\n" + AgentPromptComposer.STOP_CONDITIONS, text)
    }

    @Test
    fun promptTextHasNoNonAsciiCharacters() {
        // 守卫 N3（中文硬编码棘轮）基线为 0：core-* 的字符串字面量必须是 ASCII。
        // 这条测试是「改文案时手滑粘了中文」的机械兜底。
        val all = AgentPromptComposer.compose(
            request(systemInstruction = "x", skillsSection = "- s: d", memoryText = "- m"),
            listOf(tool("memory_write")),
        )
        assertTrue(all.all { it.code in 0x09..0x7E || it == '\n' })
    }
}
