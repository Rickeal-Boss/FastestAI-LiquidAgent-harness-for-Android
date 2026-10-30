package com.rickeal.liquidharness.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [DeltaTracker] 的「两击确认 + 单帧延迟 + 显式 flush」单测（外审 P2）。
 *
 * 缺陷形态：旧实现在**第二帧**就锁模式，凡 `frame.startsWith(first)` 即判累积式。
 * 增量流里第二帧恰好以第一帧开头并不罕见（`哈` + `哈哈`、`the` + `theatre`），
 * 误判后第二帧只放行后缀 ⇒ 重叠前缀被丢一次，第三帧才自愈 —— 净效果是正文丢字。
 *
 * 每个用例都同时断言「每帧返回值」与「累计全文」：只看其中一个会漏掉
 * 「tracker 自己拼得对、但喂给下游的增量少了」这种半对半错的形态。
 */
class DeltaTrackerTwoHitTest {

    /** 累积式：第二击成立后才确认，两帧的增量一次补出。 */
    @Test
    fun cumulativeModeIsConfirmedOnSecondHit() {
        val tracker = DeltaTracker()

        assertEquals("哈", tracker.feed("哈"))
        // 第二帧是歧义帧：暂缓输出，不判定。
        assertEquals("", tracker.feed("哈哈"))
        assertNull(tracker.isCumulative)
        // 第三帧以待决帧为前缀 ⇒ 确认累积式，补出两帧增量。
        assertEquals("哈世界", tracker.feed("哈哈世界"))

        assertEquals("哈哈世界", tracker.text)
        assertEquals(true, tracker.isCumulative)
    }

    /** 增量式（歧义帧）：第三帧不满足前缀 ⇒ 待决帧整帧补出，一个字都不许丢。 */
    @Test
    fun ambiguousFrameFallsBackToIncremental() {
        val tracker = DeltaTracker()

        assertEquals("哈", tracker.feed("哈"))
        assertEquals("", tracker.feed("哈哈"))
        assertEquals("哈哈世界", tracker.feed("世界"))

        // 三帧全计入：哈 + 哈哈 + 世界。
        assertEquals("哈哈哈世界", tracker.text)
        assertEquals(false, tracker.isCumulative)
    }

    /** 增量式（无歧义）：第二帧不以首帧开头 ⇒ 立即判定，不延迟。 */
    @Test
    fun unambiguousIncrementalFrameIsNotDelayed() {
        val tracker = DeltaTracker()

        assertEquals("你好", tracker.feed("你好"))
        assertEquals("世界", tracker.feed("世界"))

        assertEquals("你好世界", tracker.text)
        assertEquals(false, tracker.isCumulative)
    }

    /** 完全重复帧 = 累积式特征（增量流不会重发整段），第二帧即可确认。 */
    @Test
    fun duplicatedFrameConfirmsCumulative() {
        val tracker = DeltaTracker()

        assertEquals("a", tracker.feed("a"))
        assertEquals("", tracker.feed("a"))

        assertEquals("a", tracker.text)
        assertEquals(true, tracker.isCumulative)
    }

    /** flush：流在歧义帧处结束，待决帧必须补出（否则末帧永久丢失）。 */
    @Test
    fun flushEmitsPendingFrame() {
        val tracker = DeltaTracker()
        tracker.feed("哈")
        assertEquals("", tracker.feed("哈哈"))

        assertEquals("哈哈", tracker.flush())
        assertEquals("哈哈哈", tracker.text)
        // 幂等：第二次 flush 没有待决帧，不得重复补字。
        assertEquals("", tracker.flush())
        assertEquals("哈哈哈", tracker.text)
    }

    /** reset 必须一并清掉待决帧，否则下一条流会吃到上一条流的尾巴。 */
    @Test
    fun resetClearsPendingFrame() {
        val tracker = DeltaTracker()
        tracker.feed("哈")
        tracker.feed("哈哈")

        tracker.reset()

        assertEquals("", tracker.text)
        assertNull(tracker.isCumulative)
        assertEquals("", tracker.flush())
    }

    /** 累积式确认后前缀被破坏：切回增量，已有全文保留（不得清 buffer）。 */
    @Test
    fun cumulativeModeSelfHealsWhenPrefixBreaks() {
        val tracker = DeltaTracker()

        assertEquals("ab", tracker.feed("ab"))
        assertEquals("", tracker.feed("abc"))
        // 第二击成立 ⇒ 累积式，补出 "c" 与 "X"。
        assertEquals("cX", tracker.feed("abcX"))
        assertEquals(true, tracker.isCumulative)
        // 前缀被破坏 ⇒ 切回增量，整帧追加。
        assertEquals("Y", tracker.feed("Y"))

        assertEquals("abcXY", tracker.text)
        assertEquals(false, tracker.isCumulative)
    }

    /** StreamAccumulator 接线：收到 Finished 时补出待决帧，不得让末帧消失。 */
    @Test
    fun accumulatorFlushesPendingFrameOnFinished() {
        val accumulator = StreamAccumulator()
        accumulator.accept(GenerationEvent.TextDelta("哈"))
        accumulator.accept(GenerationEvent.TextDelta("哈哈"))
        accumulator.accept(GenerationEvent.Finished(FinishReason.STOP))

        assertEquals("哈哈哈", accumulator.toGeneration().text)
        assertEquals(FinishReason.STOP, accumulator.toGeneration().finishReason)
    }

    /** StreamAccumulator 接线：Failed 同样是终局，思考通道的待决帧也要补出。 */
    @Test
    fun accumulatorFlushesReasoningOnFailed() {
        val accumulator = StreamAccumulator()
        accumulator.accept(GenerationEvent.ReasoningDelta("想"))
        accumulator.accept(GenerationEvent.ReasoningDelta("想想"))
        accumulator.accept(
            GenerationEvent.Failed(EngineErrorKind.NETWORK, "network error", retryable = true)
        )

        assertEquals("想想想", accumulator.toGeneration().reasoning)
    }

    /** reset 之后两条通道都不许残留待决帧。 */
    @Test
    fun accumulatorResetClearsFlushedState() {
        val accumulator = StreamAccumulator()
        accumulator.accept(GenerationEvent.TextDelta("哈"))
        accumulator.accept(GenerationEvent.TextDelta("哈哈"))
        accumulator.reset()

        assertEquals("", accumulator.toGeneration().text)
        assertEquals("", accumulator.toGeneration().reasoning)
    }
}
