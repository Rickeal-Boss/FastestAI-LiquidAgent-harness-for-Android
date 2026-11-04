package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TokenEstimator
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
        //
        // budget=20 而不是原来的 10：水位线（默认 0.6）把贪心停点提前了，
        // budget=10 时贪心在孤儿之前就停（kept 只剩最后一条 user），根本走不到「切点
        // 落在孤儿上」这条路径。判据本身没变，只是把场景摆回能触发它的位置。
        val messages = listOf(user(huge), toolResultFor("c1"), user("a"))
        val result = runBlocking { compressor.compress(messages, 20) }

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

    // ── W4：水位线 ──────────────────────────────────────────────────────────

    /** 400 个 ASCII 字符 = 100 token，+4 协议开销 = 104 token/条。 */
    private fun line(id: String, role: Role = Role.MODEL) =
        ChatMessage(id = id, role = role, text = "x".repeat(400))

    /** 判据 A1：压缩后**不会**下一轮立刻复发。 */
    @Test
    fun watermarkLeavesHeadroomForTheNextRound() {
        val messages = (1..12).map { line("m$it") }
        val budget = 1_000

        val kept = runBlocking { compressor.compress(messages, budget) }
        val keptTokens = TokenEstimator.estimateMessages(kept)

        // 水位线生效：留出 0.4×budget 的余量（否则下一轮追加一条满额工具结果必然复发）。
        assertTrue(keptTokens <= (budget * DEFAULT_COMPRESS_TARGET_RATIO).toInt(), "kept=$keptTokens")
        assertTrue(keptTokens < budget, "kept=$keptTokens")

        // 追加一条 0.2×budget 的消息后仍在预算内 ⇒ 第二次压缩必须原样返回同一对象。
        val grown = kept + ChatMessage(id = "next", role = Role.USER, text = "y".repeat(800))
        assertTrue(TokenEstimator.estimateMessages(grown) <= budget)
        assertSame(grown, runBlocking { compressor.compress(grown, budget) })
    }

    /**
     * 水位线只能收紧，不能收紧到「连最新一条都装不下」。
     *
     * 最新一条本身就 > 水位线时若死守 0.6，贪心一条都装不下 ⇒ 压缩无条件放弃 ⇒
     * 超预算请求照发（端点 400）。故下限 = 「system + 最新一条」。
     */
    @Test
    fun watermarkKeepsTheNewestMessageWhenItAloneExceedsTheTarget() {
        val messages = listOf(
            ChatMessage(id = "old", role = Role.USER, text = "x".repeat(4_000)),
            ChatMessage(id = "newest", role = Role.MODEL, text = "y".repeat(3_600)),
        )

        val kept = runBlocking { compressor.compress(messages, 1_000) }

        assertEquals(1, kept.size)
        assertEquals("newest", kept.single().id)
    }

    /** `targetRatio >= 1.0` = 关闭水位线（回落到「装到刚好不超预算」的现役行为）。 */
    @Test
    fun watermarkCanBeDisabled() {
        val messages = (1..6).map { line("m$it") }
        val budget = 270
        val full = WindowContextCompressor(targetRatio = 1.0f)

        val kept = runBlocking { full.compress(messages, budget) }

        // 6 × 104 = 624 > 270；装到刚好不超 ⇒ 保留 2 条（208）之后第 3 条 312 超预算。
        assertEquals(2, kept.size)
        assertEquals(208, TokenEstimator.estimateMessages(kept))
    }

    // ── W4：切点对齐 turn 边界 ──────────────────────────────────────────────

    /**
     * 判据 A10①：`TURN_ALIGNED` 的保留区首条是 USER。
     *
     * 算账（每条 104 token = 400 字符 + 4 协议开销）：5 条共 520 > budget 500（必须真的超预算，
     * 否则压缩器在入口就原样返回）；水位线 0.6×500 = 300 ⇒ 贪心装 2 条（208 ≤ 300 < 312），
     * 切点落在 m3(MODEL)。往回走到 u2(USER) 多丢 1 条 = 104 token ≤ 上限 500×0.25=125 ⇒ 允许对齐。
     */
    @Test
    fun turnAlignedCutStartsAtUserMessage() {
        val messages = listOf(
            line("m0", Role.MODEL),
            line("m1", Role.MODEL),
            line("u2", Role.USER),
            line("m3", Role.MODEL),
            line("m4", Role.MODEL),
        )
        val budget = 500

        val aligned = runBlocking {
            WindowContextCompressor(cutPolicy = CutPolicy.TURN_ALIGNED).compress(messages, budget)
        }
        val minimal = runBlocking {
            WindowContextCompressor(cutPolicy = CutPolicy.MINIMAL).compress(messages, budget)
        }

        assertEquals(Role.USER, aligned.first().role)
        assertEquals("u2", aligned.first().id)
        // MINIMAL 保持现役行为：切在第一个非 TOOL 上（这里是一条 MODEL）。
        assertEquals(Role.MODEL, minimal.first().role)
        assertEquals("m3", minimal.first().id)
    }

    /** 判据 A10②：对齐会多丢超过上限 ⇒ 退回 MINIMAL，并留 `compress_align=skipped` 证据。 */
    @Test
    fun turnAlignedFallsBackWhenExtraDropExceedsTheCap() {
        val messages = listOf(
            ChatMessage(id = "u0", role = Role.USER, text = "x".repeat(2_000)),
            line("m1", Role.MODEL),
            line("m2", Role.MODEL),
            line("m3", Role.MODEL),
            line("m4", Role.MODEL),
        )
        AgentLogStore.clear()

        val result = runBlocking {
            WindowContextCompressor(cutPolicy = CutPolicy.TURN_ALIGNED).compress(messages, 500)
        }

        // 对齐到 u0 要多丢 504+104+104=712 token ≫ 500×0.25=125 ⇒ 必须退回 MINIMAL 的切点。
        assertEquals(Role.MODEL, result.first().role)
        assertEquals("m3", result.first().id)
        assertTrue(
            AgentLogStore.recent().any { it.message.contains("compress_align=skipped") },
            AgentLogStore.recent().joinToString("\n") { it.message },
        )
    }

    /** MINIMAL 回归：默认构造**不**做对齐（既有调用点与既有单测的行为不变）。 */
    @Test
    fun defaultConstructorKeepsMinimalCutPolicy() {
        val messages = listOf(
            line("m0", Role.MODEL),
            line("m1", Role.MODEL),
            line("u2", Role.USER),
            line("m3", Role.MODEL),
            line("m4", Role.MODEL),
        )
        AgentLogStore.clear()

        val result = runBlocking { WindowContextCompressor().compress(messages, 500) }

        assertEquals(Role.MODEL, result.first().role)
        assertTrue(AgentLogStore.recent().none { it.message.contains("compress_align") })
    }
}
