package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.EngineCapabilities
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 首字节看门狗与用户取消**并发**时的终态归属（W6 / P4）。
 *
 * ## 事故形态
 *
 * 看门狗到点后 `firstByteTimedOut = true` 并取消内层 `coroutineScope`；同一时刻用户点了停止
 * （`AgentRunner.cancel` 置标记 + 取消子 job）。旧实现只看 `firstByteTimedOut`，
 * 于是把「用户取消」记成 `EngineErrorKind.TIMEOUT` —— 用户看到「对端超时」，
 * 排查方向完全错（去查网络 / 端点，而真相是本地点了停止）。
 *
 * ## 判据为什么是「异常传播 + 没有 timeout 失败事件」
 *
 * 取消的正确语义是**原样上抛 `CancellationException`**（`AGENTS.md` §五.4：
 * 吞掉它会让 Job 从 cancelled 变 completed），由 `AgentRunner` 在
 * `withContext(NonCancellable)` 里补 `Cancelled(partialText)`。所以内核层能观测到的
 * 只有两件事：异常有没有抛出来、以及有没有误发一条 `Failed(timeout)`。
 */
class GenerationRoundCancellationTest {

    private fun loop(): TurnLoop = TurnLoop(generation = GenerationRound(backoff = {}))

    @Test
    fun watchdogTimeoutConcurrentWithCancelIsNotRecordedAsTimeout() {
        val events = mutableListOf<AgentEvent>()
        // 看门狗 40ms；取消标记在 15ms 置位（只置标记、不取消 Job —— 与
        // `AgentRunner.cancel` 的第一步同序）。这样看门狗**确实**会开火，
        // 唯一能救回来的就是「转 Fatal 之前再查一次取消标记」。
        val ctx = testContext(
            events = events,
            engine = SilentEngine(silentMillis = 400),
            policy = AgentPolicy(generationTimeoutMillis = 40),
        )

        var thrown: Throwable? = null
        runBlocking {
            val canceller = launch {
                delay(15)
                ctx.state.markCancelled()
            }
            try {
                loop().run(ctx)
            } catch (c: CancellationException) {
                thrown = c
            }
            canceller.join()
        }

        assertNotNull(thrown, "cancel must propagate; converting it to TIMEOUT hides a user stop")
        assertTrue(
            events.none { it is AgentEvent.Failed && it.message.contains("timeout") },
            "no TIMEOUT failure may be booked: " + events.filterIsInstance<AgentEvent.Failed>()
                .map { it.message },
        )
    }

    /** 一个事件都不吐的引擎：把首字节看门狗逼到点。 */
    private class SilentEngine(private val silentMillis: Long) : LlmEngine {
        override val endpointId: String = "silent"

        override suspend fun capabilities(): EngineCapabilities =
            EngineCapabilities(maxContextTokens = 32_768, maxOutputTokens = 4_096)

        override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
            delay(silentMillis)
        }

        override suspend fun stop() = Unit
    }
}
