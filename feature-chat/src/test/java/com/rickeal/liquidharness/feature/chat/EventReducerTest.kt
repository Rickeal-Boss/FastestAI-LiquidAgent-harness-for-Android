package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TerminationReason
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EventReducer] 的纯函数单测。
 *
 * 这些用例覆盖的是「流式路径的静默失败」—— 它们不会崩溃、不会报错，只会让界面
 * 停在错误状态（永远转圈 / 空回答 / 半截文本 / 工具卡永远转）。旧仓这类 bug 只能
 * 靠真机点出来，所以这里把每条不变量都钉成断言。
 *
 * ## 相对 W1 版本增删的用例与理由
 *
 * 删：
 * - `textDeltaCreatesStreamingBubble` / `textDeltaAppendsToSameBubble` /
 *   `finishedFinalizesAndClearsStreaming`：W1 把在途文本塞进 `bubbles` 并打
 *   `streaming` 标记，本轮改为 `StreamingState` 单一真源（气泡列表只放定稿内容），
 *   旧断言的对象已不存在。对应行为由下面三条 `streaming*` 用例接管。
 *
 * 增：
 * - `toolResultWithoutStartedTraceIsIgnored` / `toolSkippedWithoutStartedTraceIsIgnored`：
 *   钉 `AGENTS.md` §五.3「ToolResultReceived / ToolSkipped 只更新不新建」。
 * - `approvalRequestedOverwritesPrevious`：钉同节的「ApprovalRequested 无去重」。
 * - `retryingClearsStreamingText` / `streamResetClearsStreamingText`：钉「两条事件
 *   语义不同但都要清缓冲」—— 分开测是为了将来给 Retrying 加「重试中」提示时，
 *   StreamReset 的行为不被顺手改掉。
 * - `bubbleIdsAreUnique`：气泡 id 改用 `newId()` 后，同角色连发两条不得撞 key。
 * - `finishedAttachesTracesToBubble`：run 结束后轨迹必须跟着气泡走，否则工具过程
 *   随流式区一起从屏幕上消失。
 */
class EventReducerTest {

    private val initial = ChatUiState()

    @Test
    fun textDeltaCreatesStreamingText() {
        val next = EventReducer.reduce(initial, AgentEvent.TextDelta("he"))
        assertEquals("he", next.streaming.text)
        assertTrue(next.streaming.isStreaming)
        assertTrue(next.isStreaming)
        assertEquals(0, next.bubbles.size)
    }

    @Test
    fun textDeltaAppendsToSameStream() {
        var state = EventReducer.reduce(initial, AgentEvent.TextDelta("he"))
        state = EventReducer.reduce(state, AgentEvent.TextDelta("llo"))
        assertEquals("hello", state.streaming.text)
        assertEquals(0, state.bubbles.size)
    }

    @Test
    fun finishedCommitsBubble() {
        var state = EventReducer.reduce(initial, AgentEvent.TextDelta("hi"))
        state = EventReducer.reduce(state, AgentEvent.Finished(text = "hi", rounds = 1))
        assertEquals(1, state.bubbles.size)
        assertEquals(Role.MODEL, state.bubbles[0].role)
        assertEquals("hi", state.bubbles[0].text)
        assertFalse(state.bubbles[0].streaming)
        assertFalse(state.isStreaming)
        assertEquals("", state.streaming.text)
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
        assertFalse(state.isStreaming)
    }

    /** 空气泡比「没有回答」更糟：它让用户以为模型回了但内容丢了。 */
    @Test
    fun finishedWithNoContentAddsNoBubble() {
        val state = EventReducer.reduce(initial, AgentEvent.Finished(text = "", rounds = 1))
        assertEquals(0, state.bubbles.size)
        assertFalse(state.isStreaming)
    }

    @Test
    fun failedKeepsPartialTextAndSetsError() {
        var state = EventReducer.reduce(initial, AgentEvent.TextDelta("half"))
        state = EventReducer.reduce(state, AgentEvent.Failed(message = "http 401"))
        assertFalse(state.isStreaming)
        assertEquals("half", state.bubbles[0].text)
        assertEquals("http 401", state.errorMessage)
    }

    @Test
    fun cancelledKeepsPartialText() {
        var state = EventReducer.reduce(initial, AgentEvent.TextDelta("abc"))
        state = EventReducer.reduce(state, AgentEvent.Cancelled(partialText = "abc"))
        assertFalse(state.isStreaming)
        assertEquals("abc", state.bubbles[0].text)
        assertEquals("", state.streaming.text)
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

    /**
     * 气泡 id 不能用「前缀 + 列表长度」：同角色连发两条、或将来中段插入一条，
     * 都会算出同一个 key，LazyColumn 会复用错误的 item 状态。
     */
    @Test
    fun bubbleIdsAreUnique() {
        var state = EventReducer.appendUser(initial, "one")
        state = EventReducer.appendUser(state, "two")
        assertEquals(2, state.bubbles.size)
        assertFalse(state.bubbles[0].id == state.bubbles[1].id)
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
        assertEquals("answer", state.streaming.text)
        assertEquals("thinking", state.streaming.reasoning)
    }

    @Test
    fun toolCallStartedCreatesRunningTrace() {
        val next = EventReducer.reduce(initial, AgentEvent.ToolCallStarted(call = readCall(), spec = readSpec()))
        assertEquals(1, next.toolTraces.size)
        assertEquals("call-1", next.toolTraces[0].id)
        assertEquals(ToolTraceStatus.RUNNING, next.toolTraces[0].status)
    }

    /**
     * `AGENTS.md` §五.3：`ToolResultReceived` 只更新不新建。
     * 没有 `ToolCallStarted` 就凭空造一条轨迹，会得到一张「参数为空、状态 OK」的卡，
     * 用户无从判断模型到底调了什么。
     */
    @Test
    fun toolResultWithoutStartedTraceIsIgnored() {
        val next = EventReducer.reduce(
            initial,
            AgentEvent.ToolResultReceived(ToolResult(callId = "ghost", ok = true, output = "x")),
        )
        assertEquals(0, next.toolTraces.size)
    }

    @Test
    fun toolResultUpdatesTrace() {
        var state = EventReducer.reduce(initial, AgentEvent.ToolCallStarted(call = readCall(), spec = readSpec()))
        state = EventReducer.reduce(
            state,
            AgentEvent.ToolResultReceived(
                ToolResult(callId = "call-1", ok = false, output = "denied", elapsedMillis = 7L),
            ),
        )
        assertEquals(1, state.toolTraces.size)
        assertEquals(ToolTraceStatus.FAILED, state.toolTraces[0].status)
        assertEquals("denied", state.toolTraces[0].result)
        assertEquals(7L, state.toolTraces[0].elapsedMillis)
    }

    @Test
    fun toolSkippedWithoutStartedTraceIsIgnored() {
        val next = EventReducer.reduce(
            initial,
            AgentEvent.ToolSkipped(call = readCall(), reason = "blocked"),
        )
        assertEquals(0, next.toolTraces.size)
    }

    @Test
    fun toolSkippedMarksTrace() {
        var state = EventReducer.reduce(initial, AgentEvent.ToolCallStarted(call = readCall(), spec = readSpec()))
        state = EventReducer.reduce(state, AgentEvent.ToolSkipped(call = readCall(), reason = "blocked"))
        assertEquals(ToolTraceStatus.SKIPPED, state.toolTraces[0].status)
        assertEquals("blocked", state.toolTraces[0].result)
    }

    /**
     * `AGENTS.md` §五.3：`ApprovalRequested` **无去重** —— 同一调用重复发就覆盖成新的，
     * 不做「已经问过就不问」的合并（那会让第二次的参数变更被吞掉）。
     */
    @Test
    fun approvalRequestedOverwritesPrevious() {
        var state = EventReducer.reduce(
            initial,
            AgentEvent.ApprovalRequested(call = readCall(), spec = readSpec()),
        )
        state = EventReducer.reduce(
            state,
            AgentEvent.ApprovalRequested(
                call = ToolCall(id = "call-2", name = "file_write", argumentsJson = "{\"p\":1}"),
                spec = readSpec(),
            ),
        )
        assertTrue(state.pendingApproval != null)
        assertEquals("call-2", state.pendingApproval?.callId)
        assertEquals("file_write", state.pendingApproval?.toolName)
    }

    @Test
    fun retryingClearsStreamingText() {
        var state = EventReducer.reduce(initial, AgentEvent.TextDelta("half"))
        state = EventReducer.reduce(state, AgentEvent.Retrying(reason = "timeout"))
        assertEquals("", state.streaming.text)
        assertTrue(state.isStreaming)
    }

    @Test
    fun streamResetClearsStreamingText() {
        var state = EventReducer.reduce(initial, AgentEvent.TextDelta("half"))
        state = EventReducer.reduce(state, AgentEvent.StreamReset(reason = "truncated"))
        assertEquals("", state.streaming.text)
        assertEquals(0, state.bubbles.size)
    }

    @Test
    fun finishedAttachesTracesToBubble() {
        var state = EventReducer.reduce(initial, AgentEvent.ToolCallStarted(call = readCall(), spec = readSpec()))
        state = EventReducer.reduce(state, AgentEvent.TextDelta("done"))
        state = EventReducer.reduce(state, AgentEvent.Finished(text = "done", rounds = 2))
        assertEquals(1, state.bubbles.size)
        assertEquals(1, state.bubbles[0].traces.size)
        assertEquals(0, state.toolTraces.size)
    }

    @Test
    fun breakerTrippedIsRecorded() {
        val state = EventReducer.reduce(
            initial,
            AgentEvent.Finished(
                text = "x",
                rounds = 3,
                terminatedBy = TerminationReason.BreakerTripped,
            ),
        )
        assertEquals(TerminationReason.BreakerTripped, state.terminatedBy)
    }

    private fun readCall(): ToolCall =
        ToolCall(id = "call-1", name = "file_read", argumentsJson = "{\"path\":\"a.txt\"}")

    private fun readSpec(): ToolSpec =
        ToolSpec(name = "file_read", description = "read a file")
}
