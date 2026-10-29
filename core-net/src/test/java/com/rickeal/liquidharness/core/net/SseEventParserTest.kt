package com.rickeal.liquidharness.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SSE 字节级分帧的单测。
 *
 * 这些用例全部对应真实踩过的缺陷形态，不是「覆盖率凑数」：
 * 跨 chunk 汉字截断、多行 data、[DONE] 后的垃圾字节、两种换行。
 */
class SseEventParserTest {

    /** 用例 ①：一个汉字被切在两个 chunk 之间 ⇒ 不得出现 U+FFFD。 */
    @Test
    fun chineseCharSplitAcrossChunksHasNoReplacementChar() {
        val frame = """data: {"text":"中文测试"}""" + "\n\n"
        val bytes = frame.toByteArray(Charsets.UTF_8)

        // 切点落在「中」这个 3 字节字符的中间。
        val prefixBytes = frame.substring(0, frame.indexOf('中')).toByteArray(Charsets.UTF_8).size
        val splitAt = prefixBytes + 1

        val parser = SseEventParser()
        val first = parser.feed(bytes, 0, splitAt)
        val second = parser.feed(bytes, splitAt, bytes.size - splitAt)

        assertTrue(first.isEmpty(), "半个字符不得产出帧")
        assertEquals(1, second.size)
        assertEquals("""{"text":"中文测试"}""", second[0].data)
        assertFalse(second[0].data.contains('\uFFFD'))
    }

    /** 用例 ②：`data:` 跨 3 行 ⇒ 用 \n 拼接。 */
    @Test
    fun multiLineDataIsJoinedWithNewline() {
        val parser = SseEventParser()
        val events = parser.feed("data: line1\ndata: line2\ndata: line3\n\n".toByteArray(Charsets.UTF_8))

        assertEquals(1, events.size)
        assertEquals("line1\nline2\nline3", events[0].data)
    }

    /** 用例 ③：`[DONE]` 之后不再产生事件，后续垃圾字节不抛异常。 */
    @Test
    fun doneMarkerTerminatesAndSwallowsTrailingGarbage() {
        val parser = SseEventParser()

        val done = parser.feed("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
        assertEquals(1, done.size)
        assertEquals("[DONE]", done[0].data)
        assertTrue(parser.isTerminated)

        // 半截 JSON、心跳、随机二进制都必须被静默丢弃。
        assertTrue(parser.feed("data: {\"broken".toByteArray(Charsets.UTF_8)).isEmpty())
        assertTrue(parser.feed(byteArrayOf(0x00, 0x7F, 0xFF.toByte())).isEmpty())
        assertTrue(parser.feed("\n\n\n".toByteArray(Charsets.UTF_8)).isEmpty())
        assertTrue(parser.finish().isEmpty())
    }

    /** 用例 ④：`\r\n\r\n` 与 `\n\n` 都支持，且混用时也不串帧。 */
    @Test
    fun supportsBothLfAndCrlfFrameTerminators() {
        val parser = SseEventParser()
        val events = parser.feed(
            "data: one\r\n\r\ndata: two\n\ndata: three\r\n\n".toByteArray(Charsets.UTF_8)
        )

        assertEquals(3, events.size)
        assertEquals("one", events[0].data)
        assertEquals("two", events[1].data)
        assertEquals("three", events[2].data)
    }

    /** 注释行（`: ping`）是心跳，整行丢弃，不得产出一个空 data 帧。 */
    @Test
    fun commentLinesAreIgnored() {
        val parser = SseEventParser()
        val events = parser.feed(": ping\n\ndata: real\n\n".toByteArray(Charsets.UTF_8))

        assertEquals(1, events.size)
        assertEquals("real", events[0].data)
    }

    /** `event:` / `id:` / `retry:` 字段按规范解析，且冒号后只剥一个空格。 */
    @Test
    fun namedFieldsAreParsed() {
        val parser = SseEventParser()
        val events = parser.feed(
            "event: content_block_delta\nid: 42\nretry: 3000\ndata:  two-spaces\n\n"
                .toByteArray(Charsets.UTF_8)
        )

        assertEquals(1, events.size)
        assertEquals("content_block_delta", events[0].event)
        assertEquals("42", events[0].id)
        assertEquals(3000L, events[0].retryMillis)
        assertEquals(" two-spaces", events[0].data)
    }

    /** 流末尾没有空行时，finish() 必须把残留字节当最后一帧，不能丢。 */
    @Test
    fun finishEmitsTrailingFrameWithoutBlankLine() {
        val parser = SseEventParser()
        assertTrue(parser.feed("data: tail".toByteArray(Charsets.UTF_8)).isEmpty())

        val tail = parser.finish()
        assertEquals(1, tail.size)
        assertEquals("tail", tail[0].data)
    }

    /** 孤立 \r 在缓冲末尾时不能立刻判帧（可能是 \r\n 的前半），等下一块再判。 */
    @Test
    fun loneCarriageReturnAtChunkBoundaryWaitsForMoreBytes() {
        val parser = SseEventParser()
        assertTrue(parser.feed("data: x\r".toByteArray(Charsets.UTF_8)).isEmpty())

        val events = parser.feed("\n\r\n".toByteArray(Charsets.UTF_8))
        assertEquals(1, events.size)
        assertEquals("x", events[0].data)
    }

    /** 没有 data 字段的帧（只有注释 / 只有 event）不产出事件。 */
    @Test
    fun frameWithoutDataProducesNothing() {
        val parser = SseEventParser()
        assertTrue(parser.feed("event: ping\n\n".toByteArray(Charsets.UTF_8)).isEmpty())
        assertTrue(parser.feed(": keep-alive\n\n".toByteArray(Charsets.UTF_8)).isEmpty())
        // `data:` 后为空值是合法事件（data 为空串），不是「无 data 字段」。
        val emptyData = parser.feed("data: \n\n".toByteArray(Charsets.UTF_8))
        assertEquals(1, emptyData.size)
        assertEquals("", emptyData[0].data)
        assertNull(emptyData[0].event)
    }
}
