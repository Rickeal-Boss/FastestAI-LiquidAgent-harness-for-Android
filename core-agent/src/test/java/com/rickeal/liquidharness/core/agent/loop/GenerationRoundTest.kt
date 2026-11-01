package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.EngineCapabilities
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首字节看门狗（`AgentPolicy.generationTimeoutMillis` 的唯一消费点）的单测。
 *
 * ## 这三条用例钉的是三种不同的「慢」
 *
 * 1. **远端不响应**（一个事件都没有）⇒ 超时判死。这是看门狗唯一要抓的形态。
 * 2. **首字节在窗口内到、之后慢** ⇒ 必须 disarm。若没 disarm，正常的长回答会在
 *    首字节之后被自己的看门狗砍掉 —— 那比不接看门狗更糟（把「慢」变成「必失败」）。
 * 3. **首字节后长时间静默** ⇒ 同样不归它管：那一段是 `readTimeout` 与墙钟 HARD 的辖区。
 *    两条超时机制必须各管一段，重叠就会出现「两处都能杀、谁也说不清是谁杀的」。
 *
 * ## 为什么用真实 `delay` 而不是虚拟时钟
 *
 * 本仓 `core-agent` 测试源集没有 `kotlinx-coroutines-test`（不新增依赖）。
 * 所以窗口都取百毫秒级、且留足余量（首字节 10ms vs 窗口 300ms），
 * 断言的是**因果关系**（触没触发），不是精确耗时。
 *
 * 计时本身是**墙钟**，不接 `FreezeClock`：看门狗保护「远端不响应」，
 * 不是「设备冻结后误判」（理由写在 `GenerationRound.attemptOnce` 的 KDoc 里）。
 */
class GenerationRoundTest {

    private fun context(
        engine: LlmEngine,
        events: MutableList<AgentEvent>,
        policy: AgentPolicy,
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

    /** 一个事件都不 emit，也不结束 —— 「连接建起来了，对端不说话」。 */
    private class SilentEngine : LlmEngine {
        override val endpointId: String = "silent"

        /** `generate` 被 collect 的次数。重试判据靠它断言。 */
        var generateCount: Int = 0
            private set

        override suspend fun capabilities(): EngineCapabilities = EngineCapabilities()

        override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
            generateCount += 1
            awaitCancellation()
        }

        override suspend fun stop() = Unit
    }

    /**
     * 按脚本 emit，每个事件前先等 [steps] 里给的毫秒数。
     *
     * 与 `FakeEngine` 的分工：那个是「瞬时吐完」，用来测事件语义；这个用来测**时序**。
     */
    private class TimedEngine(private val steps: List<Pair<Long, GenerationEvent>>) : LlmEngine {
        override val endpointId: String = "timed"

        override suspend fun capabilities(): EngineCapabilities = EngineCapabilities()

        override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
            for ((delayMillis, event) in steps) {
                delay(delayMillis)
                emit(event)
            }
        }

        override suspend fun stop() = Unit
    }

    /**
     * 1. 永不 emit ⇒ 到点取消 collect、判 `Fatal`，且**不重试**。
     *
     * 不重试是刻意的：用户已经干等满一个窗口，再等一个只是把等待翻倍。
     * 断言 `generateCount == 1` 就是在钉这一条 —— 只断言文案的话，
     * 将来有人把它改成 `Attempt.Retryable` 仍然会「文案对、行为错」。
     */
    @Test
    fun noFirstByteFailsFastWithoutRetry() {
        AgentLogStore.clear()
        val events = mutableListOf<AgentEvent>()
        val engine = SilentEngine()
        val ctx = context(engine, events, AgentPolicy(generationTimeoutMillis = 60L))

        val outcome = runBlocking { GenerationRound(backoff = {}).run(ctx, "sys") }

        assertEquals(RoundOutcome.Terminated, outcome)
        val failed = events.filterIsInstance<AgentEvent.Failed>().single()
        // 文案是纯 ASCII（守卫 G16 禁 main 源集中文字面量），中文由 UI 按错误类映射。
        assertTrue(failed.message.contains("timeout"))
        assertTrue(failed.message.contains("60"))
        assertEquals(1, engine.generateCount)
        assertTrue(events.none { it is AgentEvent.Retrying })
        // 看门狗必须留下证据：真机上「一直转圈」时，日志是唯一能区分「没发出去」与
        // 「发出去了但没人应」的东西。
        assertTrue(
            AgentLogStore.recent().any {
                it.level == AgentLogStore.Level.WARN && it.message.contains("first byte timeout")
            }
        )
    }

    /**
     * 2. 首字节在窗口内到达 ⇒ 看门狗 disarm，之后的长流不受影响。
     *
     * 这是本功能最危险的回归形态：没 disarm 的看门狗会在**正常的长回答**中途开火，
     * 把「慢」变成「必失败」，而且失败文案还写着「超时」—— 排查方向直接被带偏。
     */
    @Test
    fun firstByteWithinWindowDisarmsWatchdog() {
        AgentLogStore.clear()
        val events = mutableListOf<AgentEvent>()
        val engine = TimedEngine(
            listOf(
                10L to GenerationEvent.TextDelta("hi"),
                // 远超窗口（300ms）：若看门狗没被解除，这里必然已经被砍断。
                500L to GenerationEvent.Finished(FinishReason.STOP),
            )
        )
        val ctx = context(engine, events, AgentPolicy(generationTimeoutMillis = 300L))

        val outcome = runBlocking { GenerationRound(backoff = {}).run(ctx, "sys") }

        assertEquals(RoundOutcome.TextOnly(FinishReason.STOP), outcome)
        assertEquals("hi", ctx.state.partialText())
        assertTrue(events.none { it is AgentEvent.Failed })
        assertTrue(
            AgentLogStore.recent().none { it.message.contains("first byte timeout") }
        )
    }

    /**
     * 3. 已 emit 过事件之后再无输出 ⇒ **不**触看门狗。
     *
     * 这条静默归 `readTimeout` / 墙钟 HARD 管。看门狗在这里开火的后果是
     * 「模型说了半句、连接还活着、却被判成首字节超时」—— 错误归因。
     */
    @Test
    fun silenceAfterFirstByteDoesNotTripWatchdog() {
        AgentLogStore.clear()
        val events = mutableListOf<AgentEvent>()
        val engine = TimedEngine(
            listOf(
                0L to GenerationEvent.TextDelta("a"),
                // 窗口 200ms，这里静默 400ms：看门狗若还在跑，必在这里开火。
                400L to GenerationEvent.TextDelta("b"),
                0L to GenerationEvent.Finished(FinishReason.STOP),
            )
        )
        val ctx = context(engine, events, AgentPolicy(generationTimeoutMillis = 200L))

        val outcome = runBlocking { GenerationRound(backoff = {}).run(ctx, "sys") }

        assertEquals(RoundOutcome.TextOnly(FinishReason.STOP), outcome)
        assertEquals("ab", ctx.state.partialText())
        assertTrue(events.none { it is AgentEvent.Failed })
        assertTrue(AgentLogStore.recent().none { it.message.contains("first byte timeout") })
        // 半截静默不能把「已收到的内容」弄丢。
        assertTrue(ctx.state.textLength() > 0)
    }
}
