package com.rickeal.liquidharness.feature.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * 轻量 Markdown → `AnnotatedString`（用户 W8 需求 ②，**零新依赖**）。
 *
 * ## 判据口径（写在这里就是判据本身）
 *
 * 支持子集（其余语法**原样按纯文本显示**，不吞字符、不改写）：
 * `#`..`######` 标题（MD-1）、`**粗体**`、行内代码、围栏代码块、`- ` 列表；
 * 链接 `[t](url)` 显示锚文本 + **灰色小字 URL 尾注**（MD-2，让目标可见，不导航、不加载图片）。
 *
 * 显式**不处理**：HTML、表格、图片、有序列表编号重排。
 *
 * ## 为什么自绘而不引库
 *
 * `gradle/libs.versions.toml` 明写「版本矩阵锁死」、G4a/G4b 扫 `.kts`+toml 黑名单、G5 禁 JitPack，
 * 而 `core-design` 仅余 161 行装不下任何渲染层 ⇒ 引第三方 markdown 是唯一会被守卫拦死的路。
 * `AnnotatedString` + `SpanStyle` 属 `androidx.compose.ui.text`，**现有依赖**。
 *
 * ## 为什么只在定稿气泡渲染
 *
 * 流式分支保持 `text + "▍"` 纯文本：`StreamFlushBuffer` 120ms 刷新，逐帧重解析会掉帧
 * 且光标错位。真机判据必须等 `finish` 后截图，避免把「流式没变」误读成切换失效。
 */
internal fun bubbleText(text: String, renderMarkdown: Boolean): AnnotatedString =
    if (renderMarkdown) parseMarkdown(text) else AnnotatedString(text)

/** 已知必然通过的锚：无 markdown 语法时两态逐字相等。 */
internal fun parseMarkdown(source: String): AnnotatedString = buildAnnotatedString {
    var inFence = false
    source.lineSequence().forEachIndexed { index, rawLine ->
        if (index > 0) append('\n')
        val line = rawLine
        if (line.trimStart().startsWith("```")) {
            // 围栏开关：开围栏时补一个换行语义（用 \n 已在上方 append）。
            inFence = !inFence
            return@forEachIndexed
        }
        if (inFence) {
            withStyle(FENCE_STYLE) { append(line) }
            return@forEachIndexed
        }
        val trimmed = line.trimStart()
        // MD-1：一到六级标题统一按 `^#{1,6}\s+` 识别，不再只认 "# " / "## "。
        val heading = HEADING_PATTERN.find(trimmed)
        if (heading != null) {
            withStyle(HEADING_STYLE) { appendInline(trimmed.substring(heading.value.length)) }
            return@forEachIndexed
        }
        if (trimmed.startsWith("- ")) {
            append("• ")
            appendInline(trimmed.removePrefix("- "))
            return@forEachIndexed
        }
        appendInline(line)
    }
}

private val HEADING_STYLE = SpanStyle(fontWeight = FontWeight.Bold)
private val BOLD_STYLE = SpanStyle(fontWeight = FontWeight.Bold)
private val CODE_STYLE = SpanStyle(fontFamily = FontFamily.Monospace)
private val FENCE_STYLE = SpanStyle(fontFamily = FontFamily.Monospace)
/** MD-2：URL 尾注用灰色小字，和正文锚文本在视觉上分得开，但目标仍可见。 */
private val LINK_URL_STYLE = SpanStyle(color = Color.Gray, fontSize = 10.sp)
private val HEADING_PATTERN = Regex("""^#{1,6}\s+""")

/**
 * 行内三段式：`**粗体**` / 代码 / `[锚文本](url)`。
 * 顺序匹配、拼接未匹配段；链接 append 锚文本（第 3 组）+ 灰色小字 URL 尾注（第 4 组）。
 */
private fun AnnotatedString.Builder.appendInline(line: String) {
    var cursor = 0
    for (match in INLINE_PATTERN.findAll(line)) {
        if (match.range.first > cursor) append(line.substring(cursor, match.range.first))
        val bold = match.groupValues[1]
        val code = match.groupValues[2]
        val anchor = match.groupValues[3]
        val url = match.groupValues[4]
        when {
            bold.isNotEmpty() -> withStyle(BOLD_STYLE) { append(bold) }
            code.isNotEmpty() -> withStyle(CODE_STYLE) { append(code) }
            anchor.isNotEmpty() -> {
                append(anchor)
                if (url.isNotEmpty()) withStyle(LINK_URL_STYLE) { append(" (" + url + ")") }
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) append(line.substring(cursor))
}

private val INLINE_PATTERN = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\(([^)]*)\)""")
