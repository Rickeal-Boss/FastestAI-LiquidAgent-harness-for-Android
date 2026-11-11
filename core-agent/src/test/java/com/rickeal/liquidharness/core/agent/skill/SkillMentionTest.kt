package com.rickeal.liquidharness.core.agent.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对话框 `/` 指令与 `@` 引用（W16 后用户追加：手动触发 skill）的解析判据。
 *
 * 判据自身先被验证：每个用例都喂一个**已知必然通过**的样本，再喂边界样本。
 */
class SkillMentionTest {

    @Test
    fun extractMentionsFindsSingleAtReference() {
        assertEquals(listOf("notes-organizer"), SkillMention.extractMentions("@notes-organizer"))
    }

    @Test
    fun extractMentionsDedupesAndKeepsOrder() {
        assertEquals(
            listOf("a", "b"),
            SkillMention.extractMentions("用 @a 和 @b，再看 @a 一次"),
        )
    }

    @Test
    fun extractMentionsReturnsEmptyWithoutReference() {
        assertEquals(emptyList<String>(), SkillMention.extractMentions("普通句子没有引用"))
        assertEquals(emptyList<String>(), SkillMention.extractMentions(""))
    }

    @Test
    fun isTriggerOnlyForSlashOrAtPrefix() {
        assertTrue(SkillMention.isTrigger("@notes"))
        assertTrue(SkillMention.isTrigger("/notes"))
        assertFalse(SkillMention.isTrigger("看看 @notes 在正文中间"))
        assertFalse(SkillMention.isTrigger(""))
    }

    @Test
    fun decorateAppendsGuideOnlyWhenReferenced() {
        val decorated = SkillMention.decorate("@notes-organizer 帮我整理")
        assertTrue(decorated.startsWith("@notes-organizer 帮我整理"))
        assertTrue(decorated.contains("skill_load"))
        // 无引用时原样返回，不附加引导（否则每条消息都被撑长）。
        assertEquals("普通问题", SkillMention.decorate("普通问题"))
    }
}
