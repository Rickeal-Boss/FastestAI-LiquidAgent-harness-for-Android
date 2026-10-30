package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.AgentRequest
import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.prompt.AgentPromptComposer
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.EngineErrorKind
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TerminationReason
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 主循环单测：`FakeEngine` 返回预置 `Flow<GenerationEvent>`，纯 JVM 跑通
 * 「文本 → Finished」与「空输出 → 重试一次 → 失败」两条路径。
 *
 * 这两个用例就是 `00-brief.md` A3 里「新仓可端到端验证」的落地证据。
 */
class TurnLoopTest {

    private fun loop(): TurnLoop = TurnLoop(generation = GenerationRound(backoff = {}))

    private fun context(
        engine: LlmEngine,
        events: MutableList<AgentEvent>,
        policy: AgentPolicy = AgentPolicy(),
        request: AgentRequest = testRequest(),
        state: RunState = RunState.forRequest(request),
    ): TurnContext = TurnContext(
        request = request,
        policy = policy,
        engine = engine,
        tools = ToolRegistry(),
        state = state,
        emit = { events += it },
    )

    @Test
    fun textOnlyRunFinishesWithAccumulatedText() {
        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(scripts = listOf(FakeEngine.textOnly("hi there")))
        val ctx = context(engine, events)

        val step = runBlocking { loop().run(ctx) }

        assertEquals(Step.Done, step)
        val finished = events.filterIsInstance<AgentEvent.Finished>().single()
        assertEquals("hi there", finished.text)
        assertEquals(TerminationReason.ModelStopped, finished.terminatedBy)
        assertEquals(1, finished.rounds)
        assertTrue(events.first() is AgentEvent.RoundStarted)
        assertTrue(events.any { it is AgentEvent.TextDelta })
    }

    @Test
    fun emptyOutputRetriesOnceThenFails() {
        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(scripts = listOf(emptyList(), emptyList()))
        val ctx = context(engine, events)

        val step = runBlocking { loop().run(ctx) }

        assertEquals(Step.Terminated, step)
        // 重试一次 ⇒ generate 被调用两次，且恰好一条 Retrying。
        assertEquals(2, engine.generateCount)
        assertEquals(1, events.count { it is AgentEvent.Retrying })
        val failed = events.filterIsInstance<AgentEvent.Failed>().single()
        assertTrue(failed.message.contains("empty output"))
    }

    @Test
    fun retryableFailureIsRetriedOnce() {
        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(
            scripts = listOf(
                listOf(GenerationEvent.Failed(EngineErrorKind.SERVER, "boom", retryable = true)),
                FakeEngine.textOnly("ok"),
            ),
        )
        val ctx = context(engine, events)

        val step = runBlocking { loop().run(ctx) }

        assertEquals(Step.Done, step)
        assertEquals(2, engine.generateCount)
        assertEquals("ok", events.filterIsInstance<AgentEvent.Finished>().single().text)
    }

    @Test
    fun partialOutputIsNeverRetried() {
        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(
            scripts = listOf(
                listOf(
                    GenerationEvent.TextDelta("half"),
                    GenerationEvent.Failed(EngineErrorKind.NETWORK, "reset", retryable = true),
                ),
            ),
        )
        val ctx = context(engine, events)

        val step = runBlocking { loop().run(ctx) }

        assertEquals(Step.Terminated, step)
        // 已向用户吐出正文 ⇒ 重试会造成重复输出，必须直接失败。
        assertEquals(1, engine.generateCount)
        assertTrue(events.none { it is AgentEvent.Retrying })
        // 已收内容不丢：RunState 仍保留，供 UI 渲染「响应中断，已保留已收到的内容」。
        assertEquals("half", ctx.state.partialText())
    }

    @Test
    fun truncatedFinishReasonProducesDiagnosticReport() {
        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(
            scripts = listOf(
                listOf(GenerationEvent.TextDelta("half"), GenerationEvent.Finished(FinishReason.LENGTH)),
            ),
        )
        val ctx = context(engine, events)

        runBlocking { loop().run(ctx) }

        val finished = events.filterIsInstance<AgentEvent.Finished>().single()
        assertEquals("Truncated", finished.report?.kind)
    }

    @Test
    fun missingFinishReasonWithContentIsTreatedAsTruncated() {
        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(scripts = listOf(listOf(GenerationEvent.TextDelta("cut off"))))
        val ctx = context(engine, events)

        runBlocking { loop().run(ctx) }

        val finished = events.filterIsInstance<AgentEvent.Finished>().single()
        // 「有内容但无 finish_reason」= 被截断（AGENTS.md §五.6），不是正常结束。
        assertEquals("Truncated", finished.report?.kind)
        assertEquals("cut off", finished.text)
    }

    @Test
    fun zeroMaxRoundsFinishesWithoutCallingEngine() {
        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(scripts = listOf(FakeEngine.textOnly("never")))
        val ctx = context(engine, events, policy = AgentPolicy(maxRounds = 0))

        val step = runBlocking { loop().run(ctx) }

        assertEquals(Step.Done, step)
        assertEquals(0, engine.generateCount)
        assertEquals(TerminationReason.MaxRounds, events.filterIsInstance<AgentEvent.Finished>().single().terminatedBy)
    }

    @Test
    fun systemMessageIsFirstAndUsesStableDerivedId() {
        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(scripts = listOf(FakeEngine.textOnly("ok")))
        val ctx = context(engine, events)

        runBlocking { loop().run(ctx) }

        val system = assertNotNull(engine.lastRequest).messages.first()
        assertEquals(Role.SYSTEM, system.role)
        // 稳定派生 id：同一 run 的每一轮都复用它，保证前缀字节稳定（prompt cache）。
        assertEquals("sys:" + ctx.state.runId, system.id)
        assertTrue(system.text.contains(AgentPromptComposer.STOP_CONDITIONS))
        // 用户输入在工作副本尾部，不插在 system 之前。
        assertEquals(Role.USER, engine.lastRequest?.messages?.last()?.role)
    }

    @Test
    fun toolCallToAnUnregisteredToolIsSkippedAndTheLoopContinues() {
        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(
            scripts = listOf(
                listOf(
                    GenerationEvent.ToolCallDelta(0, "c1", "file_read", "{}"),
                    GenerationEvent.Finished(FinishReason.TOOL_CALLS),
                ),
                // W2 起工具批执行完是「进下一轮」，不是直接收尾：第二轮才是终点。
                FakeEngine.textOnly("done"),
            ),
        )
        val ctx = context(engine, events)

        val step = runBlocking { loop().run(ctx) }

        assertEquals(Step.Done, step)
        assertEquals(1, events.count { it is AgentEvent.ToolSkipped })
        // 注册表里没有 file_read ⇒ 没有 spec ⇒ 没有轨迹创建点 ⇒ 只能发 skip，不许发 result。
        assertTrue(events.none { it is AgentEvent.ToolResultReceived })
        assertEquals("done", events.filterIsInstance<AgentEvent.Finished>().single().text)
    }
}
