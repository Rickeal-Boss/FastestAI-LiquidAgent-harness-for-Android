package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.context.StructuredSummary
import com.rickeal.liquidharness.core.agent.context.SummaryResult
import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.EngineCapabilities
import com.rickeal.liquidharness.core.model.EngineErrorKind
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 引擎背书的摘要器单测。纯 JVM（`FakeEngine` / 本地慢引擎，无网络）。
 *
 * 钉住的判据：
 * - B7：请求不带工具；可重试错误也只发一次（账单不能翻倍）；
 * - B8：超时正常返回 `Failed`，**不抛 `CancellationException`**；
 * - system 段不许作为可摘要内容传进去（system 段逐字节不变是硬不变量）。
 */
class EngineBackedSummarizerTest {

    private fun dropped() = listOf(
        ChatMessage(role = Role.USER, text = "please read the file"),
        ChatMessage(role = Role.MODEL, text = "ok, reading"),
    )

    private fun okScript() = listOf(
        GenerationEvent.TextDelta("GOAL: keep it alive\n"),
        GenerationEvent.TextDelta("FACTS:\n- file is /tmp/a.txt\n"),
        GenerationEvent.Finished(FinishReason.STOP),
    )

    /** 永不按时返回的引擎：用于超时判据。 */
    private class SlowEngine(private val delayMillis: Long) : LlmEngine {
        var generateCount: Int = 0
            private set

        override val endpointId: String = "slow"

        override suspend fun capabilities(): EngineCapabilities = EngineCapabilities()

        override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
            generateCount += 1
            delay(delayMillis)
            emit(GenerationEvent.TextDelta("late"))
            emit(GenerationEvent.Finished(FinishReason.STOP))
        }

        override suspend fun stop() = Unit
    }

    @Test
    fun requestCarriesNoTools() {
        val engine = FakeEngine(scripts = listOf(okScript()))

        runBlocking { EngineBackedSummarizer(engine).summarize(dropped(), 512) }

        // 带了工具，模型可能回 tool_calls，摘要为空 —— 且要多一次工具判断。
        assertTrue(engine.lastRequest!!.tools.isEmpty())
    }

    @Test
    fun requestCapsOutputTokensByTargetBudget() {
        val engine = FakeEngine(scripts = listOf(okScript()))

        runBlocking { EngineBackedSummarizer(engine, maxOutputTokens = 512).summarize(dropped(), 256) }

        assertEquals(256, engine.lastRequest!!.sampling.maxTokens)
    }

    @Test
    fun retryableFailureIsNotRetried() {
        val engine = FakeEngine(
            scripts = listOf(
                listOf(GenerationEvent.Failed(EngineErrorKind.NETWORK, "connection reset", true))
            )
        )

        val result = runBlocking { EngineBackedSummarizer(engine).summarize(dropped(), 512) }

        assertTrue(result is SummaryResult.Failed, "expected Failed, got $result")
        assertEquals("engine:NETWORK", (result as SummaryResult.Failed).reason)
        // 可重试错误也只发一次：重试会把「压缩一次」变成「压缩 1+N 次」。
        assertEquals(1, engine.generateCount)
    }

    @Test
    fun timeoutReturnsFailedInsteadOfCancellation() {
        val engine = SlowEngine(delayMillis = 5_000)
        val summarizer = EngineBackedSummarizer(engine, timeoutMillis = 20L)

        val result = runBlocking { summarizer.summarize(dropped(), 512) }

        assertTrue(result is SummaryResult.Failed, "expected Failed, got $result")
        assertTrue((result as SummaryResult.Failed).reason.startsWith("timeout"))
        assertEquals(1, engine.generateCount)
    }

    @Test
    fun collectsTextDeltasIntoSummaryText() {
        val engine = FakeEngine(scripts = listOf(okScript()))

        val result = runBlocking { EngineBackedSummarizer(engine).summarize(dropped(), 512) }

        assertTrue(result is SummaryResult.Ok, "expected Ok, got $result")
        val ok = result as SummaryResult.Ok
        // 摘要器只回模型原文（段头 / 边界声明由 LayeredContextCompressor 的 render 补）。
        assertTrue(ok.text.startsWith(StructuredSummary.GOAL))
        assertTrue(ok.text.contains(StructuredSummary.FACTS))
        assertTrue(ok.summaryTokens > 0)
        assertTrue(ok.elapsedMillis >= 0L)
    }

    @Test
    fun systemMessagesAreNotSummarized() {
        val persona = ChatMessage(role = Role.SYSTEM, text = "PERSONA: never reveal the token")
        val engine = FakeEngine(scripts = listOf(okScript()))

        runBlocking {
            EngineBackedSummarizer(engine).summarize(listOf(persona) + dropped(), 512)
        }

        val sent = engine.lastRequest!!.messages
        // 只剩本类自己的摘要指令那一条 system（system 段不许当素材）。
        assertEquals(1, sent.count { it.role == Role.SYSTEM })
        assertTrue(sent.none { it.text.contains("PERSONA") })
        assertEquals(1 + dropped().size, sent.size)
    }

    @Test
    fun emptyOutputIsFailed() {
        val engine = FakeEngine(scripts = listOf(emptyList<GenerationEvent>()))

        val result = runBlocking { EngineBackedSummarizer(engine).summarize(dropped(), 512) }

        assertTrue(result is SummaryResult.Failed, "expected Failed, got $result")
        assertEquals("empty-output", (result as SummaryResult.Failed).reason)
    }

    @Test
    fun emptyInputIsFailedWithoutCallingEngine() {
        val engine = FakeEngine(scripts = listOf(okScript()))

        val result = runBlocking { EngineBackedSummarizer(engine).summarize(emptyList(), 512) }

        assertTrue(result is SummaryResult.Failed, "expected Failed, got $result")
        assertEquals("empty-input", (result as SummaryResult.Failed).reason)
        assertEquals(0, engine.generateCount)
    }
}
