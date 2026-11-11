package com.rickeal.liquidharness.core.agent.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对话框 `/` 指令与 `@` 引用（W16 后用户追加：手动触发 skill）的解析判据。
 *
 * W17 收口批（外审 W16-NEW-1 / P0-3 / N1 / A3 / A4 配套判据）：白名单装饰、`/name` 直输归一、
 * `applySkillPick` 只截开头触发 token、`stripDecoration` 剥引导。红态样本直接取外审报告的
 * 等价复现（`foo@bar.com` / `/notes-organizer` 直输 / 正文含邮箱时选中）。
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

    // ------------------------------------------------ W17：白名单装饰（W16-NEW-1 / P0-2）

    @Test
    fun decorateAppendsGuideOnlyForKnownNames() {
        val known = setOf("notes-organizer")
        val decorated = SkillMention.decorate("@notes-organizer 帮我整理", known)
        assertTrue(decorated.startsWith("@notes-organizer 帮我整理"))
        assertTrue(decorated.contains("skill_load"))
    }

    @Test
    fun decorateLeavesUnknownTokensUntouched() {
        val known = setOf("notes-organizer")
        // 外审 W16-NEW-1 等价复现：正文里的邮箱 / handle 不是引用，请求体不得被注入引导。
        val mail = "联系 foo@bar.com 帮我看下"
        assertEquals(mail, SkillMention.decorate(mail, known))
        val override = "@Override 是 Java 注解"
        assertEquals(override, SkillMention.decorate(override, known))
    }

    @Test
    fun decorateWithoutAnyReferenceReturnsTextAsIs() {
        assertEquals("普通问题", SkillMention.decorate("普通问题", setOf("notes-organizer")))
        assertEquals("", SkillMention.decorate("", emptySet()))
    }

    // ------------------------------------------------ W17：`/name` 直输归一（P0-3 / P1-1）

    @Test
    fun normalizeLineCommandTurnsLeadingSlashIntoMention() {
        assertEquals("@notes-organizer 帮我整理", SkillMention.normalizeLineCommand("/notes-organizer 帮我整理"))
        assertEquals("@notes", SkillMention.normalizeLineCommand("/notes"))
        assertEquals("  @notes", SkillMention.normalizeLineCommand("  /notes"))
    }

    @Test
    fun normalizeLineCommandLeavesPathsAndMidTextAlone() {
        // 正文路径不是指令（裁决：正则扩到 [@/] 会把这些误提取，见验收物 §1.4）。
        assertEquals("看 /sdcard/x 文件", SkillMention.normalizeLineCommand("看 /sdcard/x 文件"))
        assertEquals("a@b.com", SkillMention.normalizeLineCommand("a@b.com"))
        // `/` 后没有名字（裸斜杠）不动。
        assertEquals("/ 帮我", SkillMention.normalizeLineCommand("/ 帮我"))
    }

    @Test
    fun slashCommandDirectInputReachesTheGuide() {
        // 端到端钉住「手敲 /name 直发不再静默失效」（外审 P0-3/P1-1 的红态）。
        val known = setOf("notes-organizer")
        val decorated = SkillMention.decorate("/notes-organizer 帮我整理", known)
        assertTrue(decorated.contains("@notes-organizer"))
        assertTrue(decorated.contains("skill_load"))
    }

    // ------------------------------------------------ W17：applySkillPick 只截开头触发 token（A3）

    @Test
    fun applySkillPickReplacesLeadingTriggerOnly() {
        // 外审 A3/P1-1 红态：正文含邮箱时旧实现从邮箱的 @ 处砍掉正文（静默丢字）。
        val picked = SkillMention.applySkillPick("@not 请发到 foo@bar.com", "notes-organizer")
        assertEquals("@notes-organizer 请发到 foo@bar.com", picked)
    }

    @Test
    fun applySkillPickReplacesSlashTriggerKeepingBody() {
        assertEquals("@notes-organizer 剩下的", SkillMention.applySkillPick("/not 剩下的", "notes-organizer"))
        assertEquals("@notes-organizer ", SkillMention.applySkillPick("/notes-organizer", "notes-organizer"))
    }

    @Test
    fun applySkillPickAppendsWhenNotTriggering() {
        assertEquals("@notes-organizer ", SkillMention.applySkillPick("", "notes-organizer"))
        assertEquals("普通正文 @notes-organizer ", SkillMention.applySkillPick("普通正文", "notes-organizer"))
    }

    // ------------------------------------------------ W17：stripDecoration（N1/P2-1 显示侧）

    @Test
    fun stripDecorationRemovesTheGuideTail() {
        val decorated = SkillMention.decorate("@notes-organizer 帮我整理", setOf("notes-organizer"))
        assertEquals("@notes-organizer 帮我整理", SkillMention.stripDecoration(decorated))
    }

    @Test
    fun stripDecorationLeavesUndecoratedTextAlone() {
        assertEquals("普通问题", SkillMention.stripDecoration("普通问题"))
        assertEquals("", SkillMention.stripDecoration(""))
    }

    @Test
    fun decoratedAndStrippedRoundTripIsIdempotent() {
        val known = setOf("notes-organizer")
        val original = "@notes-organizer 帮我整理"
        val stripped = SkillMention.stripDecoration(SkillMention.decorate(original, known))
        // 剥离后再装饰与直接装饰一致（显示/存储分离不改字节语义）。
        assertEquals(SkillMention.decorate(original, known), SkillMention.decorate(stripped, known))
    }
}
