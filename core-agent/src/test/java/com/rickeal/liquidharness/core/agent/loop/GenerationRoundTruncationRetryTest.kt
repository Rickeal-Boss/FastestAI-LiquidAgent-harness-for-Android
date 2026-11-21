package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.EngineErrorKind
import com.rickeal.liquidharness.core.model.GenerationEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 首字节后**截断** ⇒ 内核整段回滚 + 重试一次（W24-02 方案 B）。
 *
 * ## 四条判据各自钉住一条边界
 *
 * | 用例 | 钉住的边界 |
 * |---|---|
 * | A1 | 回滚真的发生：`StreamReset` 恰 1 个、位置在两次 `TextDelta` **之间**、最终文本 = 第二段 |
 * | A2 | 两次都断 ⇒ 恰一个 `Failed`、零 `Finished`，且**放弃前不回滚**（第二段仍在） |
 * | A3 | `truncatedStream == false` 时**旧闸门原样生效**（已吐正文 + 可重试错误 ⇒ 不重试） |
 * | A4 | 无产出的普通重试**不发** `StreamReset`（reset 与「有没有东西要回滚」绑定） |
 *
 * ## 为什么用 `FakeEngine` 而不是真 HTTP
 *
 * 本组判据落在**内核**的时序上（回滚 ↔ emit 顺序 ↔ 重试计数），与传输形态无关；
 * 真 HTTP 的同一组判据在 `:core-data` 的 `StreamTruncationRetryTest`（端到端 2 条）。
 * `FakeEngine` 的脚本让「第一次断、第二次成功」这类形态精确可复现。
 *
 * `AttemptObserver` 在本仓**零实现**（`docs/02-roadmap.md:571` ⛔），所以 A3 的
 * 「旧闸门仍拦」只能靠脚本构造，不能指望引擎日志。
 */
class GenerationRoundTruncationRetryTest {

    /** 首字节后被掐断的引擎事件（与 `RemoteLlmEngine` 的产物同形）。 */
    private fun truncated(message: String = "stream truncated before completion") =
        GenerationEvent.Failed(
            kind = EngineErrorKind.NETWORK,
            message = message,
            retryable = true,
            truncatedStream = true,
        )

    /** 可重试但**不是**截断（旧闸门的输入）。 */
    private fun retryableNotTruncated() =
        GenerationEvent.Failed(
            kind = EngineErrorKind.NETWORK,
            message = "network error",
            retryable = true,
        )

    /** 走**真实主循环**：`Finished` 由 `RunTermination` 发（`GenerationRound` 本身不发终态），
     *  「两次都断 ⇒ 零 `Finished`」只有在 TurnLoop 层才是可证伪的。 */
    private fun loop(): TurnLoop = TurnLoop(generation = GenerationRound(backoff = {}))

    private fun context(engine: LlmEngine, events: MutableList<AgentEvent>): TurnContext {
        val request = testRequest()
        return TurnContext(
            request = request,
            policy = AgentPolicy(),
            engine = engine,
            tools = ToolRegistry(),
            state = RunState.forRequest(request),
            emit = { events += it },
        )
    }

    // ------------------------------------------------------------------ A1

    @Test
    fun truncatedStreamRollsBackTextBeforeRetry() {
        val engine = FakeEngine(
            scripts = listOf(
                listOf(GenerationEvent.TextDelta("AB"), truncated()),
                FakeEngine.textOnly("CD"),
            )
        )
        val events = mutableListOf<AgentEvent>()
        val ctx = context(engine, events)

        val outcome = runBlocking { loop().run(ctx) }

        assertEquals(2, engine.generateCount, "截断必须触发**恰好一次**重试")
        assertEquals(
            listOf("AB", "CD"),
            events.filterIsInstance<AgentEvent.TextDelta>().map { it.text },
            "两次尝试的增量都要经 emit 上抛（回滚只改 RunState / UI 缓冲）：events=$events",
        )
        val resetIndexes = events.withIndex()
            .filter { it.value is AgentEvent.StreamReset }
            .map { it.index }
        assertEquals(1, resetIndexes.size, "StreamReset 必须恰好 1 个：events=$events")
        val firstDelta = events.indexOfFirst { it is AgentEvent.TextDelta }
        val lastDelta = events.indexOfLast { it is AgentEvent.TextDelta }
        assertTrue(
            resetIndexes.single() in (firstDelta + 1) until lastDelta,
            "StreamReset 必须夹在两次 TextDelta 之间（先回滚、再重试）：events=$events",
        )
        assertEquals(1, events.count { it is AgentEvent.Retrying }, "events=$events")
        assertEquals(1, events.count { it is AgentEvent.Finished })
        assertEquals(0, events.count { it is AgentEvent.Failed })
        // 终帧载荷也必须是回滚后的文本（否则 UI 会拿旧前缀覆盖气泡）。
        val finished = events.filterIsInstance<AgentEvent.Finished>().single()
        assertEquals("CD", finished.text, "Finished(text) 不得包含被回滚的前缀：events=$events")
        // 真值源被回滚：最终文本不含被丢弃的前缀。
        assertEquals("CD", ctx.state.partialText(), "回滚必须发生在 RunState，不只是 UI 缓冲")
        assertEquals(Step.Done, outcome)
    }

    // ------------------------------------------------------------------ A2

    @Test
    fun secondTruncationGivesUpWithoutRollingBackAgain() {
        val engine = FakeEngine(
            scripts = listOf(
                listOf(GenerationEvent.TextDelta("AB"), truncated()),
                listOf(GenerationEvent.TextDelta("CD"), truncated("still truncated")),
            )
        )
        val events = mutableListOf<AgentEvent>()
        val ctx = context(engine, events)

        val outcome = runBlocking { loop().run(ctx) }

        assertEquals(2, engine.generateCount)
        assertEquals(1, events.count { it is AgentEvent.Failed }, "恰一个终态失败：events=$events")
        assertEquals(0, events.count { it is AgentEvent.Finished }, "不许出现 Finished：events=$events")
        assertEquals(
            1,
            events.count { it is AgentEvent.StreamReset },
            "只有第一次回滚；放弃路径不回滚：events=$events",
        )
        // 放弃前不回滚 ⇒ 第二段仍留在 RunState（用户能看到半截回答 + 一条失败）。
        assertEquals("CD", ctx.state.partialText())
        assertEquals(Step.Terminated, outcome)
    }

    // ------------------------------------------------------------------ A3

    @Test
    fun nonTruncatedRetryableErrorStillBlockedByPartialOutputGate() {
        val engine = FakeEngine(
            scripts = listOf(
                listOf(GenerationEvent.TextDelta("AB"), retryableNotTruncated()),
                FakeEngine.textOnly("CD"),
            )
        )
        val events = mutableListOf<AgentEvent>()
        val ctx = context(engine, events)

        val outcome = runBlocking { loop().run(ctx) }

        assertEquals(1, engine.generateCount, "旧闸门必须仍然拦住：已吐正文 + 非截断 ⇒ 不重试")
        assertEquals(1, events.count { it is AgentEvent.Failed }, "events=$events")
        assertEquals(0, events.count { it is AgentEvent.StreamReset }, "events=$events")
        assertEquals(0, events.count { it is AgentEvent.Retrying }, "events=$events")
        assertEquals(Step.Terminated, outcome)
    }

    // ------------------------------------------------------------------ A4

    @Test
    fun emptyOutputRetryEmitsNoStreamReset() {
        val engine = FakeEngine(
            scripts = listOf(
                emptyList(),
                FakeEngine.textOnly("X"),
            )
        )
        val events = mutableListOf<AgentEvent>()
        val ctx = context(engine, events)

        val outcome = runBlocking { loop().run(ctx) }

        assertEquals(2, engine.generateCount, "空输出仍走原有的一次重试")
        assertEquals(1, events.count { it is AgentEvent.Retrying }, "events=$events")
        assertEquals(
            0,
            events.count { it is AgentEvent.StreamReset },
            "没有可回滚的内容 ⇒ 不发 StreamReset（reset 与「有没有东西要丢」绑定）：events=$events",
        )
        val finished = events.filterIsInstance<AgentEvent.Finished>().single()
        assertEquals("X", finished.text)
        assertEquals(Step.Done, outcome)
    }

    // ------------------------------------------------------------------ A5（W40 补完 W24-02 已知边界）

    /**
     * A5（W40）：截断回滚**同时回退思考计数**（`RunState.truncateReasoning`）。
     *
     * 「先吐 reasoning 再断流」形态下，若只回滚正文、不回滚 `reasoningChars`，
     * 第一次的 "THINK1" 计数会残留 ⇒ 终态 `reasoningLength()` 变成两段之和
     * （"THINK1"+"THINK2"），与 UI 侧 `StreamReset` 已清空思考的状态撕裂。
     * 思考只计数不存文本 ⇒ 回滚 = 数字回退到 `reasoningBefore`（此处 0）。
     *
     * 必红变异：删掉 `GenerationRound.prepareRetry` 的 `truncateReasoning(reasoningBefore)`
     * ⇒ 本用例断言失败（最终计数 = 11 而非 6）。
     */
    @Test
    fun truncatedStreamRollsBackReasoningCountToo() {
        val secondThink = "THINK2"
        val engine = FakeEngine(
            scripts = listOf(
                listOf(
                    GenerationEvent.ReasoningDelta("THINK1"),
                    GenerationEvent.TextDelta("AB"),
                    truncated(),
                ),
                listOf(
                    GenerationEvent.ReasoningDelta(secondThink),
                    GenerationEvent.TextDelta("CD"),
                    GenerationEvent.Finished(com.rickeal.liquidharness.core.model.FinishReason.STOP),
                ),
            )
        )
        val events = mutableListOf<AgentEvent>()
        val ctx = context(engine, events)

        val outcome = runBlocking { loop().run(ctx) }

        assertEquals(2, engine.generateCount, "截断必须触发一次重试：events=$events")
        assertEquals(1, events.count { it is AgentEvent.StreamReset }, "events=$events")
        assertEquals("CD", ctx.state.partialText(), "正文回滚仍生效")
        assertEquals(
            secondThink.length,
            ctx.state.reasoningLength(),
            "思考计数必须随整段回滚退到 reasoningBefore（0），只剩第二段：" +
                "残留第一段会与 UI 已清空的思考状态撕裂（events=$events）",
        )
        assertEquals(Step.Done, outcome)
    }
}
