package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 上下文压缩与发送前清洗单测。纯 JVM。
 *
 * 两条判据来自真实 400（`ContextCompressor.kt` 类头 KDoc）：
 * - 压缩切点**不许拆散** `MODEL(toolCalls)` + `TOOL(toolResult)` 组；
 * - 压缩失败必须无损（原样返回），不许「尽力而为删一半」。
 */
class ContextCompressorTest {

    private val compressor = WindowContextCompressor()
    private val huge = "x".repeat(4_000)

    private fun user(text: String) = ChatMessage(role = Role.USER, text = text)

    private fun modelWithCall(callId: String) = ChatMessage(
        role = Role.MODEL,
        toolCalls = listOf(ToolCall(id = callId, name = "file_read", argumentsJson = "{}")),
    )

    private fun toolResultFor(callId: String) = ChatMessage(
        role = Role.TOOL,
        toolResults = listOf(ToolResult(callId = callId, name = "file_read", output = "")),
    )

    @Test
    fun withinBudgetReturnsSameInstance() {
        val messages = listOf(user("a"), user("b"))
        val result = runBlocking { compressor.compress(messages, 10_000) }

        // 未超预算必须**原样返回同一对象**：调用方可据此判定「没压缩」。
        assertSame(messages, result)
    }

    @Test
    fun overBudgetKeepsSystemAndMostRecentMessages() {
        val system = ChatMessage(role = Role.SYSTEM, text = "sys")
        val first = user(huge)
        val second = user("b")
        val third = user("c")
        val messages = listOf(system, first, second, third)

        val result = runBlocking { compressor.compress(messages, 20) }

        assertEquals(Role.SYSTEM, result.first().role)
        assertSame(third, result.last())
        assertTrue(result.none { it === first })
    }

    @Test
    fun cutPointNeverSplitsToolCallGroup() {
        val old = user(huge)
        val model = modelWithCall("c1")
        val toolResult = toolResultFor("c1")

        // budget=5 只能装下 toolResult；候选切点落在 TOOL 上，必须回溯到它的 MODEL 宿主。
        val result = runBlocking { compressor.compress(listOf(old, model, toolResult), 5) }

        assertEquals(2, result.size)
        assertEquals(Role.MODEL, result[0].role)
        assertEquals("c1", result[0].toolCalls.single().id)
        assertEquals(Role.TOOL, result[1].role)
        assertTrue(result.none { it === old })
    }

    @Test
    fun compressorGivesUpLosslesslyWhenNoSafeCutExists() {
        // 候选切点落在一条**孤儿** toolResult 上（找不到声明它的 MODEL 宿主）
        // ⇒ 无法保证「工具组不拆散」⇒ 必须无损放弃（原样返回）。
        val messages = listOf(user(huge), toolResultFor("c1"), user("a"))
        val result = runBlocking { compressor.compress(messages, 10) }

        assertSame(messages, result)
    }

    @Test
    fun sanitizeDropsOrphanToolResults() {
        val messages = listOf(user("a"), toolResultFor("missing"))
        val result = sanitizeForProvider(messages)

        assertEquals(1, result.size)
        assertEquals(Role.USER, result.single().role)
    }

    @Test
    fun sanitizePatchesMissingResultWithStableDerivedId() {
        val messages = listOf(modelWithCall("c1"))

        val first = sanitizeForProvider(messages)
        val second = sanitizeForProvider(messages)

        assertEquals(2, first.size)
        assertEquals(Role.TOOL, first[1].role)
        assertEquals("c1", first[1].toolResults.single().callId)
        assertTrue(first[1].toolResults.single().ok.not())
        // 稳定派生 id：两轮生成的补丁 id 必须一致，否则 prompt cache 每轮失效。
        assertEquals(first[1].id, second[1].id)
        assertEquals("patch:c1", first[1].id)
    }

    @Test
    fun sanitizeMergesAdjacentSameRoleMessages() {
        val first = user("a")
        val second = user("b")
        val result = sanitizeForProvider(listOf(first, second))

        assertEquals(1, result.size)
        assertEquals("a\n\nb", result.single().text)
        assertEquals(first.id, result.single().id)
    }

    @Test
    fun sanitizeDoesNotMergeModelMessagesCarryingToolCalls() {
        val messages = listOf(modelWithCall("c1"), toolResultFor("c1"))
        val result = sanitizeForProvider(messages)

        // 已配对 ⇒ 无变化 ⇒ 返回同一对象（不改写历史）。
        assertSame(messages, result)
    }

    @Test
    fun contextBudgetIsCappedByPolicyNotByEndpointWindow() {
        val endpoint = RemoteEndpoint(contextLength = 1_000_000, maxOutputTokens = 4_096)
        val policy = AgentPolicy(maxContextTokens = 32_000, compressThreshold = 0.75f)

        // (32000 - 4096) * 0.75 = 20928：1M 窗口不得让预算跟着涨到 75 万。
        assertEquals(20_928, contextBudgetTokens(endpoint, policy))
    }

    @Test
    fun contextBudgetFallsBackWhenOutputReservationExceedsWindow() {
        val endpoint = RemoteEndpoint(contextLength = 8_000, maxOutputTokens = 9_000)
        val policy = AgentPolicy(maxContextTokens = 32_000)

        assertTrue(contextBudgetTokens(endpoint, policy) > 0)
    }
}
