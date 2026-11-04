package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 分段前缀指纹单测。纯函数，零 IO。
 *
 * 四条判据（design §3.A.2 / §4 A3·A11 / §5 风险 3）：
 * 1. 同输入同指纹（确定性 —— 否则账本每天自相矛盾）；
 * 2. 变了的段必须能**指名**（「前缀变了」但不说是哪一段，等于没有信息）；
 * 3. 正常追加**不许**报变化（否则信号恒真 = 没有信号）；
 * 4. 工具面只看模型可见字段（改一个 UI 分组名不该报「前缀变了」）。
 */
class PromptPrefixTest {

    private fun user(id: String, text: String) = ChatMessage(id = id, role = Role.USER, text = text)

    private fun spec(description: String = "read a file", category: String = "general") = ToolSpec(
        name = "file_read",
        description = description,
        category = category,
        parameters = listOf(ToolParameter(name = "path", description = "file path")),
    )

    private fun segments(
        system: String = "you are a harness",
        skills: String = "",
        tools: List<ToolSpec> = emptyList(),
        history: List<ChatMessage> = emptyList(),
    ): List<PrefixSegment> = listOf(
        systemSegment(system),
        skillsSegment(skills),
        toolSchemaSegment(tools),
        historySegment(history),
    )

    @Test
    fun sameSegmentsProduceSameFingerprint() {
        val first = fingerprintOf(segments(tools = listOf(spec()), history = listOf(user("u1", "hi"))))
        val second = fingerprintOf(segments(tools = listOf(spec()), history = listOf(user("u1", "hi"))))

        assertEquals(first.combined, second.combined)
        assertEquals(first.perSegment, second.perSegment)
        // 12 位十六进制（48 bit）：账行要能一眼读完，碰撞概率可忽略。
        assertEquals(12, first.combined.length)
        assertTrue(first.combined.all { it in "0123456789abcdef" })
    }

    @Test
    fun changedSegmentChangesCombinedFingerprint() {
        val before = fingerprintOf(segments(system = "a"))
        val after = fingerprintOf(segments(system = "b"))

        assertNotEquals(before.combined, after.combined)
        assertNotEquals(before.perSegment[PrefixSegmentKind.SYSTEM], after.perSegment[PrefixSegmentKind.SYSTEM])
        assertEquals(before.perSegment[PrefixSegmentKind.SKILLS], after.perSegment[PrefixSegmentKind.SKILLS])
    }

    @Test
    fun changedFromNamesOnlyTheChangedSegments() {
        val before = fingerprintOf(segments(system = "a", skills = "- pdf: read", tools = listOf(spec())))
        val after = fingerprintOf(segments(system = "a", skills = "- pdf: read pdf files", tools = listOf(spec())))

        // 技能段变了，SYSTEM / TOOLS_SCHEMA 逐字节未变 ⇒ 只报 SKILLS。
        assertEquals(listOf(PrefixSegmentKind.SKILLS), after.changedFrom(before))
        assertEquals("SKILLS", after.changedLabel(before))
    }

    @Test
    fun changedFromNullReportsNothing() {
        val fingerprint = fingerprintOf(segments())

        // 本 run 第一次算：没有「上一次」可比。报「变了」只会让每个 run 的首轮刷一条假 WARN。
        assertEquals(emptyList(), fingerprint.changedFrom(null))
        assertEquals("none", fingerprint.changedLabel(null))
    }

    @Test
    fun segmentInputOrderDoesNotMatter() {
        val ordered = segments(system = "s", skills = "- k: v", tools = listOf(spec()))
        val shuffled = ordered.reversed()

        // 段序是「线上顺序」，由 kind 的声明序归一 —— 调用方拼错顺序不该改指纹。
        assertEquals(fingerprintOf(ordered).combined, fingerprintOf(shuffled).combined)
    }

    @Test
    fun everyRoundSegmentIsExcludedFromFingerprint() {
        val volatile = PrefixSegment(PrefixSegmentKind.SYSTEM, SegmentStability.EVERY_ROUND, "round 7 output")
        val stable = skillsSegment("- pdf: read")
        val withVolatile = fingerprintOf(listOf(volatile, stable))
        val withoutVolatile = fingerprintOf(listOf(stable))

        // 每轮必变的段进指纹 ⇒ 信号恒真。故它既不参与合并，也不出现在分段表里。
        assertEquals(withoutVolatile.combined, withVolatile.combined)
        assertTrue(withVolatile.perSegment.keys.none { it == PrefixSegmentKind.SYSTEM })
        assertEquals(withoutVolatile.perSegment, withVolatile.perSegment)
    }

    @Test
    fun historyHeadIgnoresAppendedTail() {
        val history = (1..6).map { user("u$it", "m$it") }
        val before = fingerprintOf(segments(history = history))
        val after = fingerprintOf(segments(history = history + user("u7", "m7")))

        // 只取前 HISTORY_HEAD_SIZE 条：正常追加（尾部）不得报「前缀变了」（判据 A3 的单测基础）。
        assertEquals(before.combined, after.combined)

        // 但丢头部必须报（压缩就是丢头部）——否则「压缩一次只失效一次」无从判定。
        val dropped = fingerprintOf(segments(history = history.drop(1)))
        assertNotEquals(before.combined, dropped.combined)
    }

    @Test
    fun toolSchemaOnlyTracksModelVisibleFields() {
        val base = renderToolSchema(listOf(spec()))

        // 同一份 spec 渲染确定性：账本的「没变」必须真的是同一串字节。
        assertEquals(base, renderToolSchema(listOf(spec())))
        // 描述进模型可见的工具面 ⇒ 必须影响指纹。
        assertNotEquals(base, renderToolSchema(listOf(spec(description = "write a file"))))
        // category 只用于 UI 分组，不进工具面 ⇒ 不得制造噪音。
        assertEquals(base, renderToolSchema(listOf(spec(category = "filesystem"))))
        // 空工具面渲染成空串 ⇒ 该段不参与哈希（`fingerprintOf` 跳过空段）。
        assertEquals("", renderToolSchema(emptyList()))
    }
}
