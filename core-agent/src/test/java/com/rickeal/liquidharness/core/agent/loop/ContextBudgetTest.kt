package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.AgentRequest
import com.rickeal.liquidharness.core.agent.context.contextBudgetTokens
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TokenEstimator
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 上下文预算单测：**system 必须计入预算**（外审点名的系统性低估）+
 * **压缩后 system 逐字节不变**（prompt cache 的硬前提）。
 *
 * 场景是按数字搭出来的，改阈值前先算一遍：
 * window = min(16_000, 32_000) = 16_000；usable = 16_000 - 2_000 = 14_000；
 * budget = 14_000 × 0.75 = **10_500** token。
 * system 32_000 个 ASCII 字符 ≈ 8_000 token；4 条历史各 4_000 字符 ≈ 4_000 token。
 */
class ContextBudgetTest {

    private val endpoint = RemoteEndpoint(id = "ep-budget", name = "budget", contextLength = 16_000, maxOutputTokens = 2_000)
    private val policy = AgentPolicy()

    /** 交替角色：相邻同角色会被 `sanitizeForProvider` 合并，那样就数不清「丢了几条」。 */
    private fun body(): List<ChatMessage> = listOf(
        ChatMessage(id = "m1", role = Role.USER, text = "a".repeat(4_000)),
        ChatMessage(id = "m2", role = Role.MODEL, text = "b".repeat(4_000)),
        ChatMessage(id = "m3", role = Role.USER, text = "c".repeat(4_000)),
        ChatMessage(id = "m4", role = Role.MODEL, text = "d".repeat(4_000)),
    )

    private fun context(messages: List<ChatMessage>): TurnContext = testContext(
        events = mutableListOf<AgentEvent>(),
        request = AgentRequest(endpoint = endpoint),
        state = RunState(messages),
    )

    @Test
    fun systemTokensAreCountedInTheBudget() {
        val messages = body()
        val systemText = "s".repeat(32_000)
        val budget = contextBudgetTokens(endpoint, policy)
        // 只算 working 时**不超预算** —— 这正是被外审点名的低估形态：
        // 不含 system 的判断会直接放行，而真实请求（system + working）已经超窗。
        assertTrue(TokenEstimator.estimateMessages(messages) <= budget)
        assertTrue(TokenEstimator.estimateMessages(messages) + TokenEstimator.estimate(systemText) > budget)

        val ctx = context(messages)
        val prepared = runBlocking { ContextBudget().prepare(ctx, systemText) }

        // 含 system 的判断触发了压缩：历史真的被改写了，且不是全丢（保留最近若干条）。
        assertTrue(ctx.state.working().size < messages.size)
        assertTrue(ctx.state.working().isNotEmpty())
        assertTrue(prepared.count { it.role != Role.SYSTEM } < messages.size)
    }

    @Test
    fun compressionLeavesSystemBytesUntouched() {
        val systemText = "s".repeat(32_000)
        val ctx = context(body())

        val prepared = runBlocking { ContextBudget().prepare(ctx, systemText) }

        val system = prepared.first { it.role == Role.SYSTEM }
        assertEquals(sha256(systemText), sha256(system.text))
        assertEquals("sys:" + ctx.state.runId, system.id)
        // 压缩只动历史：system 仍在最前。
        assertEquals(Role.SYSTEM, prepared.first().role)
    }

    @Test
    fun withinBudgetKeepsEveryMessage() {
        val messages = body()
        val ctx = context(messages)

        val prepared = runBlocking { ContextBudget().prepare(ctx, "short system") }

        assertEquals(messages.size, ctx.state.working().size)
        assertEquals(messages.size + 1, prepared.size)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
