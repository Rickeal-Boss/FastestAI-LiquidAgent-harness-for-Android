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
 * - `approvalRequestedDoesNotWritePendingApproval` / `approvalRequestedOnEmptyStateLeavesNoCard`：
 *   钉「审批卡单写者」—— 归约器不再写 `pendingApproval`，唯一产地是 ViewModel 的
 *   handler（外审 §四.1）。**原用例 `approvalRequestedOverwritesPrevious` 已删除**：
 *   它钉的是「归约器覆盖写」，而那正是本轮要去掉的第二个写者。
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
     * `ApprovalRequested` **不由归约器写** `pendingApproval`（外审 §四.1 的「双写者」）。
     *
     * 唯一写者是 `ChatViewModel` 的审批 handler —— 它写 state 的同时要把
     * `CompletableDeferred` 存进字段，两者必须配对。归约器再写一遍就等于第二个真值源，
     * 而它拿不到句柄 ⇒ 会出现「卡在、句柄不在」（点了没反应）或反过来的分家状态。
     *
     * 断言用「等值」而不是「同一实例」：归约器返回 `state` 本身是当前实现，
     * 但真正的契约是**语义不变**（将来为了其他原因 copy 一次也不该红）。
     * 事件本身保留 —— 内核仍必须发它（`ChatViewModel` 的 handler 就是被它触发的）。
     */
    @Test
    fun approvalRequestedDoesNotWritePendingApproval() {
        val before = initial.copy(
            pendingApproval = PendingApproval(
                callId = "call-0",
                toolName = "file_read",
                arguments = "{\"path\":\"before.txt\"}",
            ),
        )
        val next = EventReducer.reduce(
            before,
            AgentEvent.ApprovalRequested(call = readCall(), spec = readSpec()),
        )
        assertEquals(before, next)
        assertEquals("call-0", next.pendingApproval?.callId)
        assertEquals(0, next.toolTraces.size)
    }

    /** 空 state 上收到 `ApprovalRequested` 同样不该凭空造卡（handler 才是产地）。 */
    @Test
    fun approvalRequestedOnEmptyStateLeavesNoCard() {
        val next = EventReducer.reduce(
            initial,
            AgentEvent.ApprovalRequested(call = readCall(), spec = readSpec()),
        )
        assertNull(next.pendingApproval)
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

    @Test
    fun usageLabelShowsZeroCachedExplicitly() {
        // 「0 命中」必须与「字段缺失」在展示层可区分（虽然类型层还分不出）：
        // 只要 prompt > 0，就必须出现 "(0 cached, 0.0%)"，而不是什么都不显示。
        val state = EventReducer.reduce(
            initial,
            AgentEvent.UsageUpdated(prompt = 1000, completion = 10, cachedPrompt = 0),
        )
        assertTrue(state.usageLabel.orEmpty().contains("0 cached"))
        assertTrue(state.usageLabel.orEmpty().contains("0.0%"))
    }

    @Test
    fun usageLabelComputesCacheHitRate() {
        val state = EventReducer.reduce(
            initial,
            AgentEvent.UsageUpdated(prompt = 1000, completion = 10, cachedPrompt = 600),
        )
        assertTrue(state.usageLabel.orEmpty().contains("600 cached"))
        assertTrue(state.usageLabel.orEmpty().contains("60.0%"))
    }

    private fun readCall(): ToolCall =
        ToolCall(id = "call-1", name = "file_read", argumentsJson = "{\"path\":\"a.txt\"}")

    private fun readSpec(): ToolSpec =
        ToolSpec(name = "file_read", description = "read a file")
}
