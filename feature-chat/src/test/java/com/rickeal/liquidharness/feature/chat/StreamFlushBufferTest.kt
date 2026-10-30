package com.rickeal.liquidharness.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StreamFlushBuffer] 的纯单测。
 *
 * 为什么只测这个类：`ChatViewModel` 依赖 `viewModelScope`（默认 Main 调度器），
 * 而本模块测试源集只有 `junit` + `kotlin-test` —— 没有 `kotlinx-coroutines-test`，
 * 也没有 Robolectric，所以 ViewModel 无法在 JVM 上实例化。缓冲的「攒 / 取 / 清」
 * 是纯内存操作，抽出来就能把这些语义钉死；ViewModel 侧的跨代次行为（新对话清缓冲 +
 * 代次守卫）由结构保证，真机验收项见 `docs/02-roadmap.md`。
 *
 * 这里刻意不断言「线程安全」：本类按设计只在主线程访问，加锁是过度设计。
 */
class StreamFlushBufferTest {

    /** append 之后 drain 必须返回拼接结果，且取走后缓冲归零（否则同一段会被刷两遍）。 */
    @Test
    fun appendThenDrainReturnsConcatenationAndEmpties() {
        val buffer = StreamFlushBuffer()
        buffer.append("你")
        buffer.append("好")
        buffer.append("，世界")

        assertEquals("你好，世界", buffer.drain())
        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.length)
    }

    /** 空缓冲 drain 返回空串而不是 null —— 调用方用 `isEmpty()` 判断，不该再判空引用。 */
    @Test
    fun drainOnEmptyReturnsEmptyString() {
        val buffer = StreamFlushBuffer()

        assertEquals("", buffer.drain())
        assertTrue(buffer.isEmpty)
    }

    /** clear 之后必须 isEmpty，且 length 归零（新对话路径靠它丢弃旧 run 的残段）。 */
    @Test
    fun clearEmptiesBuffer() {
        val buffer = StreamFlushBuffer()
        buffer.append("半截回答")
        assertFalse(buffer.isEmpty)

        buffer.clear()

        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.length)
        assertEquals("", buffer.drain())
    }

    /**
     * 模拟 120ms 窗口：中途 drain 一次之后，下一次 drain 只应包含**新**增量，
     * 不能把已经刷出去的前半段再带一遍。
     */
    @Test
    fun drainSplitsSegmentsAcrossWindows() {
        val buffer = StreamFlushBuffer()

        buffer.append("第一段")
        assertEquals("第一段", buffer.drain())

        buffer.append("第二段")
        buffer.append("收尾")
        assertEquals("第二段收尾", buffer.drain())

        assertEquals("", buffer.drain())
    }

    /** 空 delta 不改变状态：内核偶尔发空串，不该让 length 变化或影响后续拼接。 */
    @Test
    fun appendEmptyStringIsNoOp() {
        val buffer = StreamFlushBuffer()
        buffer.append("")

        assertTrue(buffer.isEmpty)
        assertEquals("", buffer.drain())
    }

    /** length 反映未刷出的字符数，供调用方在不取走内容的前提下判断缓冲深度。 */
    @Test
    fun lengthTracksPendingCharacters() {
        val buffer = StreamFlushBuffer()
        buffer.append("abc")

        assertEquals(3, buffer.length)

        buffer.drain()

        assertEquals(0, buffer.length)
    }
}
