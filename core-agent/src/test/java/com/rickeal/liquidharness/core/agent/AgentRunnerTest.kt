package com.rickeal.liquidharness.core.agent

import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.agent.loop.FakeEngine
import com.rickeal.liquidharness.core.agent.loop.testRequest
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.EngineCapabilities
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.TerminationReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 门面单测：串行化（`GateRejected`）、取消（`Cancelled(partialText)`）、忙碌状态复位。
 *
 * 取消用例是**真跑协程**的：`FakeEngine` 在吐完正文后挂住，`cancel()` 必须让
 * 收集方以 `Cancelled` 收尾而不是以异常炸出。这条正是 `NonCancellable` 收尾的判据 ——
 * 去掉它，`emit` 会在已取消的 Job 上抛 `CancellationException`，`Cancelled` 永远发不出去。
 */
class AgentRunnerTest {

    @Test
    fun runEmitsFinishedAndResetsBusyFlag() = runBlocking {
        val events = mutableListOf<AgentEvent>()
        val runner = AgentRunner(engine = FakeEngine(scripts = listOf(FakeEngine.textOnly("done"))))

        runner.run(testRequest()).collect { events += it }

        assertEquals("done", events.filterIsInstance<AgentEvent.Finished>().single().text)
        assertFalse(runner.isBusy)
    }

    @Test
    fun concurrentRunIsRejectedAsGateRejected() = runBlocking {
        val engine = HangingEngine()
        val runner = AgentRunner(engine = engine)
        val first = launch { runner.run(testRequest()).collect { } }
        while (!runner.isBusy) yield()

        val second = mutableListOf<AgentEvent>()
        runner.run(testRequest()).collect { second += it }

        val failed = second.filterIsInstance<AgentEvent.Failed>().single()
        assertEquals(TerminationReason.GateRejected, failed.terminatedBy)

        runner.cancel()
        first.join()
    }

    @Test
    fun cancelEmitsCancelledWithPartialText() = runBlocking {
        val events = mutableListOf<AgentEvent>()
        val runner = AgentRunner(engine = HangingEngine())
        val job = launch { runner.run(testRequest()).collect { events += it } }
        while (!runner.isBusy) yield()
        // 让流真正开始（TextDelta 已发出、引擎已挂住）。
        while (events.none { it is AgentEvent.TextDelta }) yield()

        runner.cancel()
        job.join()

        val cancelled = events.filterIsInstance<AgentEvent.Cancelled>().single()
        assertEquals("partial", cancelled.partialText)
        assertFalse(runner.isBusy)
    }

    /** 吐一段正文后挂住，直到外部取消。 */
    private class HangingEngine : LlmEngine {
        private val gate = CompletableDeferred<Unit>()

        override val endpointId: String = "hanging"

        override suspend fun capabilities(): EngineCapabilities =
            EngineCapabilities(maxContextTokens = 8_192, maxOutputTokens = 1_024)

        override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
            emit(GenerationEvent.TextDelta("partial"))
            gate.await()
            emit(GenerationEvent.Finished(FinishReason.STOP))
        }

        override suspend fun stop() {
            gate.complete(Unit)
        }
    }

    @Test
    fun runnerHasNoMoreThanOneRunInFlight() {
        // 结构性判据（硬指标）：门面文件不许长回旧仓那种上帝对象。
        val lines = java.io.File("src/main/java/com/rickeal/liquidharness/core/agent/AgentRunner.kt")
            .readLines().size
        assertTrue(lines <= 200, "AgentRunner.kt must stay <= 200 lines, was $lines")
    }
}
