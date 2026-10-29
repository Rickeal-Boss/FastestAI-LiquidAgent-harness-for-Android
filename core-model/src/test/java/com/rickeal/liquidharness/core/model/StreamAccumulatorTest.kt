package com.rickeal.liquidharness.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 流式累积的单测。
 *
 * 三个用例直接对应 `docs/01-architecture.md` §3.2 记录的旧仓 P1 缺陷形态：
 * 累积式 content 被当增量拼接（文本翻倍）、工具参数碎片按到达顺序拼（JSON 坏掉）、
 * id 只在首片导致后续片把 id 覆盖成空。
 */
class StreamAccumulatorTest {

    /** arguments 分 5 片，拼出来必须是合法 JSON 原文。 */
    @Test
    fun toolCallArgumentsAccumulateAcrossFiveFragments() {
        val accumulator = StreamAccumulator()
        accumulator.accept(GenerationEvent.ToolCallDelta(0, "call_1", "get_weather", "{\"ci"))
        accumulator.accept(GenerationEvent.ToolCallDelta(0, null, null, "ty\":"))
        accumulator.accept(GenerationEvent.ToolCallDelta(0, null, null, "\"beijing"))
        accumulator.accept(GenerationEvent.ToolCallDelta(0, null, null, "\",\""))
        accumulator.accept(GenerationEvent.ToolCallDelta(0, null, null, "unit\":\"c\"}"))

        val call = accumulator.toGeneration().toolCalls.single()
        assertEquals("call_1", call.id)
        assertEquals("get_weather", call.name)
        assertEquals("{\"city\":\"beijing\",\"unit\":\"c\"}", call.argumentsJson)
    }

    /** id 只在首片出现；后续片的 null 不得把已有 id 清掉。 */
    @Test
    fun toolCallIdOnlyInFirstFragment() {
        val accumulator = StreamAccumulator()
        accumulator.accept(GenerationEvent.ToolCallDelta(0, "call_abc", "f", ""))
        accumulator.accept(GenerationEvent.ToolCallDelta(0, null, null, "{}"))

        assertEquals("call_abc", accumulator.toGeneration().toolCalls.single().id)
    }

    /** index 乱序到达：输出必须按 index 排，不是按到达顺序。 */
    @Test
    fun outOfOrderIndexesAreSortedByIndex() {
        val accumulator = StreamAccumulator()
        accumulator.accept(GenerationEvent.ToolCallDelta(1, "id_b", "second_tool", "{}"))
        accumulator.accept(GenerationEvent.ToolCallDelta(0, "id_a", "first_tool", "{}"))

        val calls = accumulator.toGeneration().toolCalls
        assertEquals(listOf("first_tool", "second_tool"), calls.map { it.name })
        assertEquals(listOf("id_a", "id_b"), calls.map { it.id })
    }

    /** 累积式 content（每帧是完整前缀）：不得把文本拼成翻倍。 */
    @Test
    fun cumulativeContentFramesAreNotDuplicated() {
        val accumulator = StreamAccumulator()
        accumulator.accept(GenerationEvent.TextDelta("你"))
        accumulator.accept(GenerationEvent.TextDelta("你好"))
        accumulator.accept(GenerationEvent.TextDelta("你好世界"))

        assertEquals("你好世界", accumulator.toGeneration().text)
    }

    /** 增量式 content：原样拼接。 */
    @Test
    fun incrementalContentFramesAreConcatenated() {
        val accumulator = StreamAccumulator()
        accumulator.accept(GenerationEvent.TextDelta("Hello"))
        accumulator.accept(GenerationEvent.TextDelta(" world"))

        assertEquals("Hello world", accumulator.toGeneration().text)
    }

    /** 重复帧（累积式代理重发同一前缀）产出空增量，不改变全文。 */
    @Test
    fun duplicatedFrameAddsNothing() {
        val accumulator = StreamAccumulator()
        accumulator.accept(GenerationEvent.TextDelta("abc"))
        accumulator.accept(GenerationEvent.TextDelta("abc"))

        assertEquals("abc", accumulator.toGeneration().text)
    }

    /** 思考通道与正文分道累积，不得互相污染。 */
    @Test
    fun reasoningChannelIsSeparateFromText() {
        val accumulator = StreamAccumulator()
        accumulator.accept(GenerationEvent.ReasoningDelta("先想"))
        accumulator.accept(GenerationEvent.TextDelta("答案"))

        val generation = accumulator.toGeneration()
        assertEquals("先想", generation.reasoning)
        assertEquals("答案", generation.text)
    }

    /** usage 与 finishReason 必须被记录（`RunTokenLedger` 的唯一真值来源）。 */
    @Test
    fun usageAndFinishReasonAreCaptured() {
        val accumulator = StreamAccumulator()
        accumulator.accept(GenerationEvent.Usage(prompt = 120, completion = 30, cachedPrompt = 96))
        accumulator.accept(GenerationEvent.Finished(FinishReason.TOOL_CALLS))

        val generation = accumulator.toGeneration()
        assertEquals(120, generation.usage?.prompt)
        assertEquals(150, generation.usage?.total)
        assertEquals(96, generation.usage?.cachedPromptTokens)
        assertEquals(FinishReason.TOOL_CALLS, generation.finishReason)
    }

    /** 空流 ⇒ 全空结果，不是 null、不是异常。 */
    @Test
    fun emptyStreamYieldsEmptyResult() {
        val generation = StreamAccumulator().toGeneration()

        assertEquals("", generation.text)
        assertEquals(0, generation.toolCalls.size)
        assertNull(generation.usage)
        assertNull(generation.finishReason)
        assertNull(generation.failed)
    }

    /** Failed 事件被记住（用于内核的失败分类）。 */
    @Test
    fun failedEventIsCaptured() {
        val accumulator = StreamAccumulator()
        accumulator.accept(
            GenerationEvent.Failed(EngineErrorKind.RATE_LIMIT, "rate limited", retryable = true)
        )

        assertEquals(EngineErrorKind.RATE_LIMIT, accumulator.toGeneration().failed?.kind)
        assertTrue(accumulator.toGeneration().failed?.retryable == true)
    }

    /** DeltaTracker 的判定是显式的：两帧之后必须能说出「累积式还是增量式」。 */
    @Test
    fun deltaTrackerReportsDetectedMode() {
        val cumulative = DeltaTracker()
        cumulative.feed("ab")
        cumulative.feed("abcd")
        assertEquals(true, cumulative.isCumulative)

        val incremental = DeltaTracker()
        incremental.feed("ab")
        incremental.feed("cd")
        assertEquals(false, incremental.isCumulative)

        val single = DeltaTracker()
        single.feed("ab")
        assertNull(single.isCumulative)
    }

    /** 工具名分片到达（`get_` + `weather`）也要拼对。 */
    @Test
    fun toolNameFragmentsAreConcatenated() {
        val accumulator = StreamAccumulator()
        accumulator.accept(GenerationEvent.ToolCallDelta(0, "id", "get_", ""))
        accumulator.accept(GenerationEvent.ToolCallDelta(0, null, "weather", "{}"))

        assertEquals("get_weather", accumulator.toGeneration().toolCalls.single().name)
    }
}
