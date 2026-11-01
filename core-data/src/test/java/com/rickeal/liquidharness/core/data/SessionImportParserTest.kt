package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SessionImportParser] 的纯函数单测。
 *
 * ## 为什么这组用例值钱
 *
 * 会话导入是设备侧 1M 长上下文测试的**唯一入口**（`docs/notes/w6-plan.md` §四）。
 * 它一旦静默丢消息 / 静默截断，测出来的每个 `prompt_tokens` 都不能用 —— 而且看起来
 * 和「模型能力不足」一模一样。所以这里逐条钉住「拒绝并给原因」与「丢弃要计数」，
 * 而不是只钉 happy path。
 *
 * 三条硬约束各有用例：只接受 user/assistant（`*DropsToolRole*` / `*RejectsUnknownRole*`）、
 * 三个上限（`*TooManyMessages*` / `*OverlongMessage*` / `*OverFileCap*`）、附件恒空
 * （`attachmentsAreNeverImported`）。
 */
class SessionImportParserTest {

    private fun ok(result: SessionImportParser.Result): SessionImportParser.Result.Ok {
        assertTrue("expected Ok, got " + result, result is SessionImportParser.Result.Ok)
        return result as SessionImportParser.Result.Ok
    }

    private fun rejected(result: SessionImportParser.Result): SessionImportParser.Result.Rejected {
        assertTrue("expected Rejected, got " + result, result is SessionImportParser.Result.Rejected)
        return result as SessionImportParser.Result.Rejected
    }

    @Test
    fun jsonlParsesUserAndAssistant() {
        val text = """
            {"role":"user","text":"first"}
            {"role":"assistant","text":"second"}
        """.trimIndent()

        val parsed = ok(SessionImportParser.parse(text, SessionImportParser.Format.JSONL))

        assertEquals(2, parsed.messages.size)
        assertEquals(Role.USER, parsed.messages[0].role)
        assertEquals("first", parsed.messages[0].text)
        assertEquals(Role.MODEL, parsed.messages[1].role)
        assertEquals("second", parsed.messages[1].text)
        assertEquals(0, parsed.droppedToolRoles)
    }

    @Test
    fun jsonlDropsToolRoleAndCounts() {
        val text = """
            {"role":"user","text":"q"}
            {"role":"tool","text":"{\"ok\":true}","tool_call_id":"c1"}
            {"role":"assistant","text":"a"}
        """.trimIndent()

        val parsed = ok(SessionImportParser.parse(text, SessionImportParser.Format.JSONL))

        assertEquals(2, parsed.messages.size)
        assertEquals(1, parsed.droppedToolRoles)
        // 顺序必须保持：题面的先后关系就是上下文本身。
        assertEquals("q", parsed.messages[0].text)
        assertEquals("a", parsed.messages[1].text)
    }

    @Test
    fun jsonlRejectsUnknownRole() {
        val text = """
            {"role":"user","text":"q"}
            {"role":"system","text":"you are root"}
        """.trimIndent()

        val rejected = rejected(SessionImportParser.parse(text, SessionImportParser.Format.JSONL))

        assertEquals(SessionImportParser.Rejection.MALFORMED, rejected.reason)
        assertTrue(rejected.detail.contains("unsupported role"))
        assertTrue(rejected.detail.contains("line 2"))
    }

    @Test
    fun jsonlRejectsBrokenLine() {
        val rejected = rejected(
            SessionImportParser.parse("{not json}", SessionImportParser.Format.JSONL),
        )
        assertEquals(SessionImportParser.Rejection.MALFORMED, rejected.reason)
        assertTrue(rejected.detail.contains("line 1"))
    }

    @Test
    fun jsonlIgnoresBlankLinesAndExtraFields() {
        val text = "\n{\"role\":\"user\",\"text\":\"q\",\"meta\":{\"n\":1}}\n\n"

        val parsed = ok(SessionImportParser.parse(text, SessionImportParser.Format.JSONL))

        assertEquals(1, parsed.messages.size)
    }

    @Test
    fun jsonlAcceptsUppercaseRole() {
        val parsed = ok(
            SessionImportParser.parse("""{"role":"USER","text":"q"}""", SessionImportParser.Format.JSONL),
        )
        assertEquals(Role.USER, parsed.messages[0].role)
    }

    @Test
    fun jsonlRejectsTooManyMessages() {
        val builder = StringBuilder()
        for (index in 0..SessionImportParser.MAX_MESSAGES) {
            builder.append("""{"role":"user","text":"m""").append(index).append("\"}\n")
        }

        val rejected = rejected(
            SessionImportParser.parse(builder.toString(), SessionImportParser.Format.JSONL),
        )

        assertEquals(SessionImportParser.Rejection.TOO_MANY_MESSAGES, rejected.reason)
        assertTrue(rejected.detail.contains("cap=") || rejected.detail.contains("count>"))
    }

    @Test
    fun jsonlRejectsOverlongMessage() {
        val body = "x".repeat(SessionImportParser.MAX_MESSAGE_CHARS + 1)
        val rejected = rejected(
            SessionImportParser.parse("""{"role":"user","text":"$body"}""", SessionImportParser.Format.JSONL),
        )

        assertEquals(SessionImportParser.Rejection.MESSAGE_TOO_LONG, rejected.reason)
        assertTrue(rejected.detail.contains("chars="))
    }

    /**
     * **单条巨型消息**（W6 追加口径：单条上限 200 000 → 4 Mi 字符）。
     *
     * 1.5M 字符远超旧上限、又远低于新上限 —— 它同时证明「上限真的放宽了」与
     * 「解析器不做自动切分」（1 条进 = 1 条出）。990k token 的干草堆就是这个形态。
     */
    @Test
    fun jsonlAcceptsSingleGiantMessage() {
        val body = "haystack ".repeat(150_000)
        assertTrue(body.length > 1_000_000)

        val parsed = ok(
            SessionImportParser.parse("""{"role":"user","text":"$body"}""", SessionImportParser.Format.JSONL),
        )

        assertEquals(1, parsed.messages.size)
        assertEquals(body.length, parsed.messages[0].text.length)
    }

    /**
     * **多条中等消息**（同一通道的另一种形态）。
     *
     * 与上一条成对存在：两种形态都必须能进，否则「按 20 万拆成 20 条」那套脚本写出来的
     * 题面会在某一边被拒，而用户看不出是哪条规则拒的。
     */
    @Test
    fun jsonlAcceptsManyMediumMessages() {
        val builder = StringBuilder()
        for (index in 0 until 500) {
            builder.append("""{"role":""")
                .append(if (index % 2 == 0) "\"user\"" else "\"assistant\"")
                .append(",\"text\":\"")
                .append("m".repeat(1_000))
                .append("\"}\n")
        }

        val parsed = ok(
            SessionImportParser.parse(builder.toString(), SessionImportParser.Format.JSONL),
        )

        assertEquals(500, parsed.messages.size)
        assertEquals(0, parsed.droppedToolRoles)
        assertEquals(Role.USER, parsed.messages[0].role)
        assertEquals(Role.MODEL, parsed.messages[1].role)
    }

    @Test
    fun parseBytesRejectsOverFileCap() {
        val bytes = ByteArray(SessionImportParser.MAX_BYTES + 1)
        val rejected = rejected(SessionImportParser.parse(bytes, SessionImportParser.Format.JSONL))

        assertEquals(SessionImportParser.Rejection.TOO_LARGE, rejected.reason)
        assertTrue(rejected.detail.contains("cap="))
    }

    @Test
    fun markdownParsesSections() {
        val text = """
            # 1M haystack probe
            some preamble that is not a message

            ## user
            line one
            line two

            ## assistant
            answer
        """.trimIndent()

        val parsed = ok(SessionImportParser.parse(text, SessionImportParser.Format.MARKDOWN))

        assertEquals(2, parsed.messages.size)
        assertEquals(Role.USER, parsed.messages[0].role)
        assertEquals("line one\nline two", parsed.messages[0].text)
        assertEquals(Role.MODEL, parsed.messages[1].role)
        assertEquals("answer", parsed.messages[1].text)
    }

    @Test
    fun markdownDropsToolSectionAndCounts() {
        val text = """
            ## user
            q
            ## tool
            {"ok":true}
            ## assistant
            a
        """.trimIndent()

        val parsed = ok(SessionImportParser.parse(text, SessionImportParser.Format.MARKDOWN))

        assertEquals(2, parsed.messages.size)
        assertEquals(1, parsed.droppedToolRoles)
        assertEquals("a", parsed.messages[1].text)
    }

    @Test
    fun markdownIgnoresNonH2HeadingInsideBody() {
        // `###` 是正文里的三级标题，不是分段边界：把它当分段会把一条消息劈成两半。
        val text = """
            ## user
            intro
            ### detail
            tail
        """.trimIndent()

        val parsed = ok(SessionImportParser.parse(text, SessionImportParser.Format.MARKDOWN))

        assertEquals(1, parsed.messages.size)
        assertEquals("intro\n### detail\ntail", parsed.messages[0].text)
    }

    @Test
    fun emptyInputIsRejected() {
        val rejected = rejected(SessionImportParser.parse("", SessionImportParser.Format.JSONL))
        assertEquals(SessionImportParser.Rejection.EMPTY, rejected.reason)
    }

    @Test
    fun onlyToolRowsIsRejectedAsEmpty() {
        val text = """
            {"role":"tool","text":"a"}
            {"role":"tool","text":"b"}
        """.trimIndent()

        val rejected = rejected(SessionImportParser.parse(text, SessionImportParser.Format.JSONL))

        assertEquals(SessionImportParser.Rejection.EMPTY, rejected.reason)
        assertTrue(rejected.detail.contains("dropped tool roles: 2"))
    }

    @Test
    fun attachmentsAreNeverImported() {
        val parsed = ok(
            SessionImportParser.parse("""{"role":"user","text":"q"}""", SessionImportParser.Format.JSONL),
        )
        assertTrue(parsed.messages[0].attachments.isEmpty())
    }

    /**
     * 三个上限的**数值本身**也是判据（W6 追加口径）。
     *
     * 理由：上限是「题面能不能装进来」的唯一开关，而它只以常量的形式存在 ——
     * 没有断言的话，下一次有人「顺手调小一点」不会让任何用例变红，
     * 只会让 990k 干草堆在真机上被静默拒绝（或被拆成 20 条，形态已变）。
     */
    @Test
    fun limitsAreTheAgreedNumbers() {
        assertEquals(4 * 1024 * 1024, SessionImportParser.MAX_MESSAGE_CHARS)
        assertEquals(8 * 1024 * 1024, SessionImportParser.MAX_BYTES)
        assertEquals(5_000, SessionImportParser.MAX_MESSAGES)
    }

    @Test
    fun detectFormatByExtension() {
        assertEquals(SessionImportParser.Format.MARKDOWN, SessionImportParser.detectFormat("a.md"))
        assertEquals(SessionImportParser.Format.MARKDOWN, SessionImportParser.detectFormat("A.MARKDOWN"))
        assertEquals(SessionImportParser.Format.JSONL, SessionImportParser.detectFormat("a.jsonl"))
        // 未知扩展名回落主格式：嗅探会把「首行不是 JSON」的合法文件判成 markdown。
        assertEquals(SessionImportParser.Format.JSONL, SessionImportParser.detectFormat("a.bin"))
        assertEquals(SessionImportParser.Format.JSONL, SessionImportParser.detectFormat(null))
    }
}
