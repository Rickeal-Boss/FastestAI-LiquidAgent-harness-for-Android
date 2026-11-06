package com.rickeal.liquidharness.feature.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * W8 轻量 Markdown 渲染的纯函数判据。
 *
 * 「判据自身先被验证」（AGENTS.md §二.3）：每组先跑一条**已知必然通过**的样本 ——
 * `bubbleText("PONG", false)` 与 `(..., true)` 都逐字 `"PONG"`，不绿就不得继续跑后续组。
 * 「切换真的改变渲染」三层判据里的第 1 层就在本文件；第 2 层是 ChatBubble 只调
 * `bubbleText(`（形态判据），第 3 层是真机两态截图 + 日志。
 */
class MarkdownTextTest {

    // ---- 已知必然通过样本（先跑，不绿不继续） ----

    @Test
    fun plainTextBothModesIdentical() {
        assertEquals(AnnotatedString("PONG"), bubbleText("PONG", false))
        assertEquals(AnnotatedString("PONG"), bubbleText("PONG", true))
        assertEquals(bubbleText("PONG", false).text, bubbleText("PONG", true).text)
    }

    @Test
    fun emptyInputBothModesSafe() {
        assertEquals("", bubbleText("", false).text)
        assertEquals("", bubbleText("", true).text)
    }

    // ---- 两态分叉 ----

    @Test
    fun markdownOffKeepsAsterisksVerbatim() {
        assertEquals("**PONG**", bubbleText("**PONG**", false).text)
        assertFalse(hasBold(bubbleText("**PONG**", false)), "关闭时不加粗体 span")
    }

    @Test
    fun markdownOnStripsAsterisksAndAddsBoldSpan() {
        val rendered = bubbleText("**PONG**", true)
        assertEquals("PONG", rendered.text, "定稿文本不含 ** 标记")
        assertTrue(hasBold(rendered), "恰有粗体 span")
    }

    @Test
    fun unclosedAsterisksKeptVerbatim() {
        // 不吞字符：未闭合的 ** 原样保留（不能把用户文本改写丢字）。
        assertEquals("**PONG", bubbleText("**PONG", true).text)
    }

    // ---- 支持子集 ----

    @Test
    fun headingRenderedBoldWithoutHashes() {
        val rendered = bubbleText("## 标题", true)
        assertFalse(rendered.text.contains("#"), "标题标记不进正文")
        assertTrue(hasBold(rendered))
    }

    @Test
    fun inlineCodeRenderedMonospace() {
        val rendered = bubbleText("前 `code` 后", true)
        assertFalse(rendered.text.contains("`"), "反引号被消费")
        assertTrue(rendered.text.contains("code"))
        assertTrue(rendered.spanStyles.any { it.item.fontFamily != null })
    }

    @Test
    fun listPrefixRendered() {
        val rendered = bubbleText("- 第一项", true)
        assertTrue(rendered.text.contains("•"), "列表项加圆点前缀")
        assertTrue(rendered.text.contains("第一项"))
    }

    @Test
    fun fenceRenderedMonospaceWithoutBackticks() {
        val rendered = bubbleText("```\ncode line\n```", true)
        assertFalse(rendered.text.contains("```"), "围栏标记不进正文")
        assertTrue(rendered.text.contains("code line"))
        assertTrue(rendered.spanStyles.any { it.item.fontFamily != null }, "围栏体用等宽字体")
    }

    @Test
    fun linkShowsAnchorTextOnly() {
        // 链接**只显示锚文本**：不导航、不加载图片（本波不支持）。
        assertEquals("点这里", bubbleText("[点这里](https://example.com/a)", true).text)
    }

    @Test
    fun htmlAndTableKeptVerbatim() {
        // 显式不处理 HTML / 表格 ⇒ 原样按纯文本显示（不吞字符）。
        assertEquals("<b>x</b>", bubbleText("<b>x</b>", true).text)
        assertEquals("|a|b|", bubbleText("|a|b|", true).text)
    }

    private fun hasBold(rendered: AnnotatedString): Boolean =
        rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold }
}
