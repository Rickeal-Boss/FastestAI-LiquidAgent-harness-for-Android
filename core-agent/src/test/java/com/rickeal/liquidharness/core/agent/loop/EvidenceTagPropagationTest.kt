package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.EngineCapabilities
import com.rickeal.liquidharness.core.model.EngineErrorKind
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `evidenceTag` 链路全覆盖（W49 P1-4）。
 *
 * W48 只覆盖了「正常 HTTP 失败」这一条路（`GenerationEvent.Failed` → `RunState` →
 * `emitFailed`）。其余四类路径的实现缺口：
 *
 * 1. 看门狗超时构造 `Failed(evidenceTag = "engine:watchdog")` 后转 `Attempt.Fatal`
 *    ——旧版 `Fatal` 没有 tag 字段，标签在此丢弃；
 * 2. 重试放弃 / 半截输出不重试直接 `emitFailed`，不带 tag，且 message 里拼了
 *    provider 正文（落盘白名单必然拦掉整行，标签也就读不到）；
 * 3. `AgentRunner` 兜底 `emitFailed(describe(t))` 同理；
 * 4. `RunState.evidenceTag` 旧版只在非空时写 ⇒ **同类内先有带标签失败、后有
 *    不带标签失败时，落盘读到的是上一次的陈旧标签**，与真实失败原因不符。
 *
 * 这四条各配一条必红：旧实现下断言会响（`staleTag` 落盘会带 `[http:rate]`；
 * 看门狗落盘行没有 `[engine:watchdog]`；放弃行会拼 provider 正文；兜底行会带路径）。
 */
class EvidenceTagPropagationTest {

    private val sinkLines = mutableListOf<String>()

    private fun captureSink(): () -> Unit {
        AgentLogStore.clear()
        AgentLogStore.installFileSink { _, message -> sinkLines += message }
        return {
            AgentLogStore.installFileSink(null)
            AgentLogStore.clear()
        }
    }

    private fun context(
        engine: LlmEngine,
        events: MutableList<AgentEvent>,
        policy: AgentPolicy = AgentPolicy(),
    ): TurnContext {
        val request = testRequest()
        return TurnContext(
            request = request,
            policy = policy,
            engine = engine,
            tools = ToolRegistry(),
            state = RunState.forRequest(request),
            emit = { events += it },
        )
    }

    /** 永不 emit，也不结束 —— 触发首字节看门狗。 */
    private class SilentEngine : LlmEngine {
        override val endpointId: String = "silent"
        override suspend fun capabilities(): EngineCapabilities = EngineCapabilities()
        override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
            awaitCancellation()
        }

        override suspend fun stop() = Unit
    }

    private fun failureLines(): List<String> =
        sinkLines.filter { it.contains("run failed:") }

    /**
     * 1. 同 run 内「先带标签失败、后不带标签失败」⇒ 落盘行**不得**残留旧标签。
     *
     * 旧实现 `if (event.evidenceTag.isNotEmpty()) state.evidenceTag = ...` 会保留
     * `http:rate`，落盘写成 `run failed: server error (HTTP 500) [http:rate]` ——
     * 一个 500 被标注成限流，排障方向直接反了。
     */
    @Test
    fun taglessFailureDoesNotCarryPreviousRoundTag() {
        val restore = captureSink()
        try {
            val engine = FakeEngine(
                scripts = listOf(
                    listOf(
                        GenerationEvent.Failed(
                            EngineErrorKind.RATE_LIMIT, "rate limited (HTTP 429)", retryable = true,
                            evidenceTag = "http:rate",
                        ),
                    ),
                    listOf(
                        GenerationEvent.Failed(
                            EngineErrorKind.SERVER, "server error (HTTP 500)", retryable = false,
                            evidenceTag = "",
                        ),
                    ),
                ),
            )
            val events = mutableListOf<AgentEvent>()
            val ctx = context(engine, events)

            runBlocking { GenerationRound(backoff = {}).run(ctx, "sys") }

            val line = failureLines().single()
            assertEquals("run failed: server error (HTTP 500)", line)
            assertFalse("陈旧标签不得跨轮残留：$line", line.contains("http:rate"))
        } finally {
            restore()
        }
    }

    /**
     * 2. 看门狗路径必须把 `engine:watchdog` 带到落盘行。
     *
     * 旧实现里 `Failed(evidenceTag = "engine:watchdog")` 在转 `Attempt.Fatal` 时
     * 被丢弃，落盘行没有任何标签。
     */
    @Test
    fun watchdogFailureCarriesEngineWatchdogTag() {
        val restore = captureSink()
        try {
            val events = mutableListOf<AgentEvent>()
            val ctx = context(SilentEngine(), events, AgentPolicy(generationTimeoutMillis = 60L))

            runBlocking { GenerationRound(backoff = {}).run(ctx, "sys") }

            val line = failureLines().single()
            assertTrue("看门狗标签必须随失败行落盘：$line", line.contains("[engine:watchdog]"))
            assertTrue("失败行仍要说明是超时：$line", line.contains("timeout"))
        } finally {
            restore()
        }
    }

    /**
     * 3. 重试放弃必须落**固定摘要**：`giving up after N retries` 不带 provider 正文，
     *    但标签要保留（标签是这次放弃的判据依据）。
     *
     * 旧实现 message 是 `giving up after 1 retries: rate limited (HTTP 429)` ——
     * 拼接了 provider 正文，落盘白名单拦掉整行，真机上连标签都读不到。
     */
    @Test
    fun retryGiveUpDigestKeepsTagWithoutProviderText() {
        val restore = captureSink()
        try {
            val engine = FakeEngine(
                scripts = listOf(
                    listOf(
                        GenerationEvent.Failed(
                            EngineErrorKind.RATE_LIMIT, "rate limited (HTTP 429)", retryable = true,
                            evidenceTag = "http:rate",
                        ),
                    ),
                ),
            )
            val events = mutableListOf<AgentEvent>()
            val ctx = context(engine, events)

            runBlocking { GenerationRound(backoff = {}).run(ctx, "sys") }

            val line = failureLines().single()
            assertEquals("run failed: giving up after 1 retries [engine:give-up]", line)
            assertFalse("provider 正文不得进落盘摘要：$line", line.contains("429"))
            assertFalse("放弃原因不得沿用上一轮限流标签：$line", line.contains("http:rate"))
        } finally {
            restore()
        }
    }

    /**
     * W50 P2-1：半截输出不重试的**真实原因**是「已吐过正文」，不能沿用导致重试的
     * 上一轮标签（`[http:rate]`）。旧实现 `prepareRetry` 不传标签，回落
     * `ctx.state.evidenceTag` ⇒ 500 / 限流语义漂移。
     */
    @Test
    fun partialOutputFailureCarriesExplicitTag() {
        val restore = captureSink()
        try {
            val engine = FakeEngine(
                scripts = listOf(
                    listOf(
                        GenerationEvent.TextDelta("partial answer"),
                        GenerationEvent.Failed(
                            EngineErrorKind.RATE_LIMIT, "rate limited (HTTP 429)", retryable = true,
                            evidenceTag = "http:rate",
                        ),
                    ),
                ),
            )
            val events = mutableListOf<AgentEvent>()
            val ctx = context(engine, events)

            runBlocking { GenerationRound(backoff = {}).run(ctx, "sys") }

            val line = failureLines().single()
            assertEquals("run failed: cannot retry after partial output [engine:partial-output]", line)
            assertFalse("半截输出路径不得沿用限流标签：$line", line.contains("http:rate"))
        } finally {
            restore()
        }
    }

    /**
     * W50 P2-3：`GenerationEvent.Failed` 的 message 兜底支可能带 provider 自由文本
     * （含用户内容 / 路径）。旧实现 `Attempt.Fatal(failed.message, null, failed.evidenceTag)`
     * 不传摘要 ⇒ `emitFailed` 用原文落盘，白名单整行拦掉，标签随之读不到。
     */
    @Test
    fun providerRawMessageUsesFixedDigestAndKeepsTag() {
        val restore = captureSink()
        try {
            val engine = FakeEngine(
                scripts = listOf(
                    listOf(
                        GenerationEvent.Failed(
                            EngineErrorKind.PROTOCOL,
                            "provider says: hunter2 at /data/local/tmp/private.log",
                            retryable = false,
                            evidenceTag = "frame:protocol-fallback",
                        ),
                    ),
                ),
            )
            val events = mutableListOf<AgentEvent>()
            val ctx = context(engine, events)

            runBlocking { GenerationRound(backoff = {}).run(ctx, "sys") }

            val line = failureLines().single()
            assertEquals("run failed: provider error [frame:protocol-fallback]", line)
            assertFalse("provider 原文不得进落盘行：$line", line.contains("hunter2"))
            assertFalse("provider 原文路径不得进落盘行：$line", line.contains("/data/local/tmp"))
        } finally {
            restore()
        }
    }

    /**
     * 4. 兜底路径：动态异常正文只进 ring，落盘是固定摘要 + 固定标签。
     *
     * 旧实现 `emitFailed(describe(t), t)` 把异常正文（可能含路径 / 用户内容）写进
     * 落盘行首，白名单拦掉 ⇒ 真机既看不到失败也看不到标签。
     */
    @Test
    fun uncaughtFailureDigestStripsDynamicText() {
        val restore = captureSink()
        try {
            val events = mutableListOf<AgentEvent>()
            val ctx = context(FakeEngine(scripts = emptyList()), events)
            ctx.state.evidenceTag = "http:rate"

            runBlocking {
                RunTermination().emitFailed(
                    ctx,
                    "boom at /data/local/tmp/private.log",
                    IllegalStateException("disk"),
                    evidenceTag = "engine:uncaught",
                    digest = "uncaught failure",
                )
            }

            val line = failureLines().single()
            assertEquals("run failed: uncaught failure [engine:uncaught]", line)
            assertFalse("动态正文不得进落盘行：$line", line.contains("/data/local/tmp"))
            assertFalse("显式标签必须覆盖 state 里的旧值：$line", line.contains("http:rate"))
            val failed = events.filterIsInstance<AgentEvent.Failed>().single()
            assertEquals("engine:uncaught", failed.evidenceTag)
        } finally {
            restore()
        }
    }
}
