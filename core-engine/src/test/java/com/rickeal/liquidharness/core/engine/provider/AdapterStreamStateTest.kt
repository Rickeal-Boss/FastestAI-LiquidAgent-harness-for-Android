package com.rickeal.liquidharness.core.engine.provider

import com.rickeal.liquidharness.core.model.GenerationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [AdapterStreamState.normalize] 的回归用例。
 *
 * 这一层是 R19（累积式代理文本翻倍）在生产路径上的**唯一接线点**：
 * 归一化必须在 emit 之前，一旦上游看到重复文本就无法回滚。
 */
class AdapterStreamStateTest {

    /** 累积式：第二帧是「从开头到现在的全文」⇒ 只放行后缀。 */
    @Test
    fun cumulativeFramesEmitOnlyTheNewSuffix() {
        val state = AdapterStreamState()

        assertEquals(
            GenerationEvent.TextDelta("ab"),
            state.normalize(GenerationEvent.TextDelta("ab")),
        )
        assertEquals(
            GenerationEvent.TextDelta("cd"),
            state.normalize(GenerationEvent.TextDelta("abcd")),
        )
        // 重复帧（服务端重发同一前缀）⇒ 不产生事件。
        assertNull(state.normalize(GenerationEvent.TextDelta("abcd")))
        assertEquals("abcd", state.textTracker.text)
    }

    /** 增量式：原样放行，一个字符都不许吞。 */
    @Test
    fun incrementalFramesPassThroughUnchanged() {
        val state = AdapterStreamState()

        assertEquals(
            GenerationEvent.TextDelta("你"),
            state.normalize(GenerationEvent.TextDelta("你")),
        )
        assertEquals(
            GenerationEvent.TextDelta("好"),
            state.normalize(GenerationEvent.TextDelta("好")),
        )
        assertEquals("你好", state.textTracker.text)
    }

    /** 思考通道与正文各用一个 tracker：两条通道互不干扰。 */
    @Test
    fun reasoningAndTextTrackersAreIndependent() {
        val state = AdapterStreamState()

        state.normalize(GenerationEvent.TextDelta("ab"))
        state.normalize(GenerationEvent.ReasoningDelta("ab"))
        assertEquals(
            GenerationEvent.ReasoningDelta("cd"),
            state.normalize(GenerationEvent.ReasoningDelta("abcd")),
        )

        assertEquals("ab", state.textTracker.text)
        assertEquals("abcd", state.reasoningTracker.text)
    }

    /**
     * 工具碎片必须计入「已 emit 过内容」。
     *
     * 工具优先的模型只吐 tool_calls、没有正文：若只看 textChars，
     * 流中断后会被判可重试，两次尝试的 arguments 碎片会拼成坏 JSON（外审 P0-3）。
     */
    @Test
    fun toolFragmentsCountAsEmittedContent() {
        val state = AdapterStreamState()
        assertEquals(false, state.hasEmittedAny)

        state.normalize(GenerationEvent.ToolCallDelta(index = 0, id = "c1", name = "file_read", argumentsFragment = "{\"pa"))
        state.normalize(GenerationEvent.ToolCallDelta(index = 0, id = null, name = null, argumentsFragment = "th\":\"a\"}"))

        assertEquals(0, state.textChars)
        assertEquals(2, state.toolCallFragments)
        assertTrue(state.hasEmittedAny)
    }
}
