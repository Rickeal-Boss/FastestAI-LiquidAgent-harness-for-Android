package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EventReducer] 的纯函数单测。
 *
 * 这些用例覆盖的是「流式路径的静默失败」——它们不会崩溃、不会报错，只会让界面
 * 停在错误状态（永远转圈 / 空回答 / 半截文本）。旧仓这类 bug 只能靠真机点出来，
 * 所以这里把每条不变量都钉成断言。
 */
class EventReducerTest {

    private val initial = ChatUiState()

    @Test
    fun textDeltaCreatesStreamingBubble() {
        val next = EventReducer.reduce(initial, AgentEvent.TextDelta("he"))
        assertEquals(1, next.bubbles.size)
        assertEquals(Role.MODEL, next.bubbles[0].role)
        assertTrue(next.bubbles[0].streaming)
        assertTrue(next.isStreaming)
    }

    @Test
    fun textDeltaAppendsToSameBubble() {
        var state = EventReducer.reduce(initial, AgentEvent.TextDelta("he"))
        state = EventReducer.reduce(state, AgentEvent.TextDelta("llo"))
        assertEquals(1, state.bubbles.size)
        assertEquals("hello", state.bubbles[0].text)
    }

    @Test
    fun finishedFinalizesAndClearsStreaming() {
        var state = EventReducer.reduce(initial, AgentEvent.TextDelta("hi"))
        state = EventReducer.reduce(state, AgentEvent.Finished(text = "hi", rounds = 1))
        assertFalse(state.isStreaming)
        assertFalse(state.bubbles[0].streaming)
        assertEquals("hi", state.bubbles[0].text)
    }

    /**
     * 回归：服务端没给 finish_reason 但流里有内容（半截流）时，`Finished.text` 可能是空串。
     * 此时必须保留累积文本，否则用户看到的是「回答突然消失」。
     */
    @Test
    fun finishedWithEmptyTextKeepsAccumulated() {
        var state = EventReducer.reduce(initial, AgentEvent.TextDelta("partial"))
        state = EventReducer.reduce(state, AgentEvent.Finished(text = "", rounds = 1))
        assertEquals("partial", state.bubbles[0].text)
        assertFalse(state.bubbles[0].streaming)
    }

    /** 空气泡比「没有回答」更糟：它让用户以为模型回了但内容丢了。 */
    @Test
    fun finishedWithNoContentRemovesEmptyBubble() {
        var state = EventReducer.reduce(initial, AgentEvent.TextDelta(""))
        state = EventReducer.reduce(state, AgentEvent.Finished(text = "", rounds = 1))
        assertEquals(0, state.bubbles.size)
        assertFalse(state.isStreaming)
    }

    @Test
    fun failedClearsStreamingAndSetsError() {
        var state = EventReducer.reduce(initial, AgentEvent.TextDelta("half"))
        state = EventReducer.reduce(state, AgentEvent.Failed(message = "http 401"))
        assertFalse(state.isStreaming)
        assertFalse(state.bubbles[0].streaming)
        assertEquals("http 401", state.errorMessage)
    }

    @Test
    fun cancelledKeepsPartialText() {
        var state = EventReducer.reduce(initial, AgentEvent.TextDelta("abc"))
        state = EventReducer.reduce(state, AgentEvent.Cancelled(partialText = "abc"))
        assertFalse(state.isStreaming)
        assertEquals("abc", state.bubbles[0].text)
        assertFalse(state.bubbles[0].streaming)
    }

    @Test
    fun appendUserClearsInputAndKeepsOrder() {
        var state = initial.copy(input = "你好")
        state = EventReducer.appendUser(state, "你好")
        assertEquals("", state.input)
        assertEquals(1, state.bubbles.size)
        assertEquals(Role.USER, state.bubbles[0].role)
        assertEquals("你好", state.bubbles[0].text)
    }

    @Test
    fun blankInputIsIgnored() {
        val state = EventReducer.appendUser(initial, "   ")
        assertEquals(0, state.bubbles.size)
    }

    @Test
    fun canSendRequiresEndpointAndInput() {
        assertFalse(initial.copy(input = "hi").canSend)
        assertTrue(initial.copy(input = "hi", endpointLabel = "OpenAI").canSend)
        assertFalse(initial.copy(input = "  ", endpointLabel = "OpenAI").canSend)
        assertFalse(initial.copy(input = "hi", endpointLabel = "OpenAI", isStreaming = true).canSend)
    }

    @Test
    fun errorIsClearedOnNextDelta() {
        val errored = initial.copy(errorMessage = "boom")
        val next = EventReducer.reduce(errored, AgentEvent.TextDelta("x"))
        assertNull(next.errorMessage)
    }

    @Test
    fun reasoningDoesNotPolluteText() {
        var state = EventReducer.reduce(initial, AgentEvent.ReasoningDelta("thinking"))
        state = EventReducer.reduce(state, AgentEvent.TextDelta("answer"))
        assertEquals("answer", state.bubbles[0].text)
        assertEquals("thinking", state.bubbles[0].reasoning)
    }
}
