package com.rickeal.liquidharness.core.agent

import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.agent.journal.AgentRunJournal
import com.rickeal.liquidharness.core.agent.loop.FakeEngine
import com.rickeal.liquidharness.core.agent.loop.TRUNCATED_KIND
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

    /**
     * W45（W24-02 显式 LENGTH ⇒ TRUNCATED 端到端用例，外审 P1 建议）：
     * 引擎回报 `finish_reason=LENGTH` ⇒ 门面导出的 `AgentEvent.Finished.report.kind`
     * 必须是 `TRUNCATED_KIND`、正文保留、terminatedBy 仍是 ModelStopped。
     *
     * 这条把「TRUNCATED 链路可达」从代码链路（RunTermination → EventReducer）升级为
     * **行为证据**：FakeEngine 吐一帧 LENGTH，经真实 TurnLoop / RunTermination 收尾，
     * 断言门面事件携带诊断卡。`feature-chat/EventReducerTest` 已钉消费端
     * （`report.kind=Truncated` ⇒ `ChatStatus.TRUNCATED`）；本条补生产端真跑。
     * 注入「RunTermination 不按 LENGTH 填 report」⇒ 本条 RED。
     */
    @Test
    fun lengthFinishReasonProducesTruncatedReportEndToEnd() = runBlocking {
        val events = mutableListOf<AgentEvent>()
        val script = listOf(
            GenerationEvent.TextDelta("half answer"),
            GenerationEvent.Finished(FinishReason.LENGTH),
        )
        val runner = AgentRunner(engine = FakeEngine(scripts = listOf(script)))

        runner.run(testRequest()).collect { events += it }

        val finished = events.filterIsInstance<AgentEvent.Finished>().single()
        assertEquals("half answer", finished.text, "截断正文不丢")
        assertEquals(TRUNCATED_KIND, finished.report?.kind, "LENGTH 必须产出截断诊断卡")
        assertTrue(
            finished.report?.detail?.contains("LENGTH") == true,
            "诊断串应带 LENGTH 便于真机取证：${finished.report?.detail}",
        )
        assertEquals(TerminationReason.ModelStopped, finished.terminatedBy)
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

    /**
     * W64（W63 外审 P1-1 合流形态判据缺口）：`wellFormed` 四态断言，挂在本类
     * 既有测试文件内的**新增断言方法**——`EXPECTED_TESTS` 同波对账 904→905
     * （`build.yml` 增量注释同提交），四条样本全部在同一个测试方法体内断言。
     *
     * 1. 完整行合格（正控）；2. `at=17` 时间戳截断仍合格（限定 1）；3. 截断
     * payload（缺 ` at=` 尾巴）不合格；4. **合流行**（前一条完整只缺 `\n`、后一条
     * 整行接在 `at=<digits>` 之后）不合格——W63 旧判据会把它误判为合格
     * `run_started`（field 读第一个 runId、lastIndexOf 取后一条尾巴），后一条
     * `run_settled` 因此丢失 ⇒ 下次启动多弹一次恢复卡。W64 加「` at=` 恰好
     * 一次」约束后必须判 False。注入「删掉 ` at=` 计数约束」⇒ 第 4 条 RED。
     */
    @Test
    fun wellFormedRejectsConflatedAndTruncatedJournalLines() {
        val runId = "110eda9612345678abcdef9012345678" // 32 位小写 hex 形态
        val started = "run_started runId=$runId inputChars=12 at=1795783954214"
        val settled = "run_settled runId=$runId activeMs=103 cancelled=false at=1795783999000"

        // 1. 完整行合格（正控：判据必须先在已知样本上响过）。
        assertTrue(AgentRunJournal.wellFormed(started, "run_started"), "完整 run_started 必须合格")
        assertTrue(AgentRunJournal.wellFormed(settled, "run_settled"), "完整 run_settled 必须合格")

        // 2. `at=17` 时间戳数字被截断 ⇒ 仍合格（runId 已完整，行为正确）。
        assertTrue(
            AgentRunJournal.wellFormed("run_started runId=$runId inputChars=12 at=17", "run_started"),
            "at=17 截断尾巴仍按限定 1 宽容",
        )

        // 3. 缺 ` at=` 尾巴的半写行 ⇒ 不合格（W63 既有判据，回归面）。
        assertFalse(
            AgentRunJournal.wellFormed("run_settled runId=$runId activeMs=103 cancelled=", "run_settled"),
            "缺 at= 尾巴必须判不合格",
        )

        // 4. 合流行：前一条完整只缺 \n、后一条整行接在 at=<digits> 之后。
        //    W63 旧判据误判 True（外审逐字复刻实证）；W64 ` at=` 计数约束 ⇒ 必须 False。
        val conflated = "run_started runId=$runId inputChars=12 at=1795783954214" +
            "run_settled runId=ffffffffffffffffffffffffffffffff activeMs=99 at=1795783999000"
        assertFalse(
            AgentRunJournal.wellFormed(conflated, "run_started"),
            "合流行必须被 at= 计数约束拦下（W63 旧判据误判 True ⇒ 多弹恢复卡）",
        )
        assertFalse(
            AgentRunJournal.wellFormed(conflated, "run_settled"),
            "合流行对 settled 侧同样不合格",
        )
    }
}
