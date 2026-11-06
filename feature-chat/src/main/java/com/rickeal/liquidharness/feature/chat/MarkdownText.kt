package com.rickeal.liquidharness.feature.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * 轻量 Markdown → `AnnotatedString`（用户 W8 需求 ②，**零新依赖**）。
 *
 * ## 判据口径（写在这里就是判据本身）
 *
 * 支持子集（仅此五种，其余语法**原样按纯文本显示**，不吞字符、不改写）：
 * `#` / `##` 标题、`**粗体**`、`` `行内代码` ``、``` 围栏代码块 ```、`- ` 列表；
 * 链接 `[t](url)` **只显示锚文本**（不导航、不加载图片）。
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
        if (trimmed.startsWith("# ") || trimmed.startsWith("## ")) {
            withStyle(HEADING_STYLE) { appendInline(trimmed.removePrefix("## ").removePrefix("# ")) }
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

/**
 * 行内三段式：`**粗体**` / `` `代码` `` / `[锚文本](url)`。
 * 顺序匹配、拼接未匹配段；链接**只 append 锚文本**（第 3 组），url 丢弃。
 */
private fun AnnotatedString.Builder.appendInline(line: String) {
    var cursor = 0
    for (match in INLINE_PATTERN.findAll(line)) {
        if (match.range.first > cursor) append(line.substring(cursor, match.range.first))
        val bold = match.groupValues[1]
        val code = match.groupValues[2]
        val anchor = match.groupValues[3]
        when {
            bold.isNotEmpty() -> withStyle(BOLD_STYLE) { append(bold) }
            code.isNotEmpty() -> withStyle(CODE_STYLE) { append(code) }
            anchor.isNotEmpty() -> append(anchor)
        }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) append(line.substring(cursor))
}

private val INLINE_PATTERN = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\([^)]*\)""")
