package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.AgentRequest
import com.rickeal.liquidharness.core.agent.context.ContextSummarizer
import com.rickeal.liquidharness.core.agent.context.LayeredContextCompressor
import com.rickeal.liquidharness.core.agent.context.PrefixSegmentKind
import com.rickeal.liquidharness.core.agent.context.SummaryResult
import com.rickeal.liquidharness.core.agent.context.contextBudgetTokens
import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TokenEstimator
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolResult
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

    // ── W4：分段前缀指纹 ────────────────────────────────────────────────────

    /** 200 个 ASCII 字符 = 50 token，+4 协议开销 = 54 token/条。 */
    private fun line(id: String, role: Role): ChatMessage =
        ChatMessage(id = id, role = role, text = "x".repeat(200))

    /** 交替角色：相邻同角色会被 `sanitizeForProvider` 合并，那样就数不清「丢了几条」。 */
    private fun history(count: Int): List<ChatMessage> =
        (0 until count).map { line("m$it", if (it % 2 == 0) Role.USER else Role.MODEL) }

    /** 判据 A3：同 run 内追加一条工具结果后，前缀指纹逐字节不变。 */
    @Test
    fun fingerprintIsStableWhenOnlyTheTailGrows() {
        val ctx = context(history(6))
        val budget = ContextBudget()

        val first = runBlocking { budget.prepare(ctx, "sys") }
        assertTrue(first.isNotEmpty())
        val before = budget.lastPrefixFingerprint
        assertTrue(before != null, "fingerprint must be computed, not null (vacuous)")

        ctx.state.appendMessage(
            ChatMessage(id = "call", role = Role.MODEL, toolCalls = listOf(ToolCall(id = "c1", name = "file_read", argumentsJson = "{}")))
        )
        ctx.state.appendMessage(
            ChatMessage(id = "result", role = Role.TOOL, toolResults = listOf(ToolResult(callId = "c1", name = "file_read", output = "ok")))
        )
        runBlocking { budget.prepare(ctx, "sys") }

        val after = budget.lastPrefixFingerprint
        assertEquals(before.combined, after?.combined)
    }

    /** 判据 A4：run 内工具面变化**不可见**（快照语义）。 */
    @Test
    fun toolSchemaSegmentIgnoresRegistryChangesWithinRun() {
        val tool = FakeTool(spec = fakeSpec(name = "calculator"))
        val ctx = testContext(
            events = mutableListOf<AgentEvent>(),
            registered = listOf(tool),
            request = AgentRequest(endpoint = endpoint),
            state = RunState(history(4)),
        )
        val budget = ContextBudget()

        runBlocking { budget.prepare(ctx, "sys") }
        val before = budget.lastPrefixFingerprint?.perSegment?.get(PrefixSegmentKind.TOOLS_SCHEMA)
        assertTrue(before != null, "TOOLS_SCHEMA segment must be present")

        ctx.tools.unregister("calculator")
        runBlocking { budget.prepare(ctx, "sys") }
        val after = budget.lastPrefixFingerprint?.perSegment?.get(PrefixSegmentKind.TOOLS_SCHEMA)

        assertEquals(before, after)
    }

    /** 判据 A11：指纹变化必须 WARN，且指名哪一段变了。 */
    @Test
    fun prefixChangeIsWarnedWithTheSegmentName() {
        val ctx = context(history(4))
        val budget = ContextBudget()
        AgentLogStore.clear()

        runBlocking { budget.prepare(ctx, "first system") }
        runBlocking { budget.prepare(ctx, "second system") }

        val warns = AgentLogStore.recent().filter { it.level == AgentLogStore.Level.WARN }
        assertTrue(
            warns.any { it.message.contains("prefix changed") && it.message.contains("SYSTEM") },
            AgentLogStore.recent().joinToString("\n") { it.level.name + " " + it.message },
        )
    }

    /** 判据 A5：压缩一次只失效一次（压缩轮与下一轮的指纹相同，且与压缩前不同）。 */
    @Test
    fun compressionInvalidatesThePrefixOnlyOnce() {
        val narrow = RemoteEndpoint(id = "ep-narrow", name = "narrow", contextLength = 8_000, maxOutputTokens = 2_000)
        val ctx = testContext(
            events = mutableListOf<AgentEvent>(),
            request = AgentRequest(endpoint = narrow),
            state = RunState(history(20)),
        )
        val budget = ContextBudget()
        val system = "s".repeat(400)

        // 第 1 轮：20 条 × 54 = 1080 ≤ 4500，不压缩。
        runBlocking { budget.prepare(ctx, system) }
        val round1 = budget.lastPrefixFingerprint?.combined

        // 第 2 轮：追加 90 条 ⇒ 5940 > 4500，触发压缩（保留区头部换人）。
        for (index in 20 until 110) ctx.state.appendMessage(line("m$index", if (index % 2 == 0) Role.USER else Role.MODEL))
        runBlocking { budget.prepare(ctx, system) }
        val round2 = budget.lastPrefixFingerprint?.combined
        assertTrue(ctx.state.working().size < 110, "compression must have happened")

        // 第 3 轮：再追加一条 ⇒ 2754 ≤ 4500，不压缩，指纹必须与第 2 轮相同。
        ctx.state.appendMessage(line("m110", Role.USER))
        runBlocking { budget.prepare(ctx, system) }
        val round3 = budget.lastPrefixFingerprint?.combined

        assertNotEquals(round1, round2)
        assertEquals(round2, round3)
    }

    /** 判据 A6②：工具声明计入预算 —— 历史没超、加上工具就超 ⇒ 必须压缩。 */
    @Test
    fun toolSpecsCountTowardsTheBudget() {
        val hugeTool = FakeTool(spec = fakeSpec(name = "bulk").copy(description = "d".repeat(40_000)))
        val ctx = testContext(
            events = mutableListOf<AgentEvent>(),
            registered = listOf(hugeTool),
            request = AgentRequest(endpoint = endpoint),
            state = RunState(listOf(
                ChatMessage(id = "a", role = Role.USER, text = "x".repeat(4_000)),
                ChatMessage(id = "b", role = Role.MODEL, text = "y".repeat(4_000)),
                ChatMessage(id = "c", role = Role.USER, text = "z".repeat(4_000)),
            )),
        )
        val budgetTokens = contextBudgetTokens(endpoint, policy)
        // 只有历史时不超预算（现役实现会直接放行）；加上工具声明后必须超。
        assertTrue(TokenEstimator.estimateMessages(ctx.state.working()) <= budgetTokens)
        assertTrue(TokenEstimator.estimateToolSpecs(ctx.tools.specs()) > budgetTokens - TokenEstimator.estimateMessages(ctx.state.working()))

        runBlocking { ContextBudget().prepare(ctx, "sys") }

        assertTrue(ctx.state.working().size < 3, "tool tokens must push the history over the budget")
    }

    /** 判据 A6③（回归钉）：工具面为空时估算值与改动前**逐值相同**。 */
    @Test
    fun emptyToolSpecsKeepTheEstimateIdentical() {
        val ctx = context(history(4))
        AgentLogStore.clear()

        val prepared = runBlocking { ContextBudget().prepare(ctx, "sys") }
        val expected = TokenEstimator.estimateMessages(prepared)

        assertTrue(
            AgentLogStore.recent().any { it.message.contains("prompt=" + expected) },
            AgentLogStore.recent().joinToString("\n") { it.message },
        )
    }

    /**
     * 判据 B（接线证明）：装了摘要器时，被 L1 丢掉的那段必须变成**一条摘要消息**，而不是凭空消失。
     *
     * ## 为什么这条必须存在
     *
     * `LayeredContextCompressor` 有自己的 9 条单测，但它们全部直接构造它 ——
     * 即使 `ContextBudget` 根本没把摘要器接进去，那 9 条也照样全绿。
     * 「组件有测试」与「组件被使用」是两件事，这条钉的是后者。
     *
     * 场景：system 8 000 token + 6 条历史 ≈ 6 000 token = 14 000 > budget 10 500
     * ⇒ 必须丢掉约 3 500 token，超过 `DEFAULT_SUMMARIZE_MIN_TOKENS`(2 000) ⇒ 触发 L2。
     */
    @Test
    fun layeredCompressionInsertsSummaryWhenSummarizerConfigured() {
        val messages = (1..6).map { index ->
            ChatMessage(
                id = "h$index",
                role = if (index % 2 == 1) Role.USER else Role.MODEL,
                text = "x".repeat(4_000),
            )
        }
        val systemText = "s".repeat(32_000)
        val ctx = context(messages)
        val budget = ContextBudget(summarizerFactory = { FixedSummarizer(SAMPLE_SUMMARY) })

        val prepared = runBlocking { budget.prepare(ctx, systemText) }

        assertTrue(
            prepared.any { LayeredContextCompressor.isSummaryMessage(it) },
            "summary message missing: " + prepared.map { it.id },
        )
        // 摘要进的是 body 首位，system 段必须逐字节不变（判据 B3）。
        assertEquals(systemText, prepared.first { it.role == Role.SYSTEM }.text)
        // 摘要消息不得被相邻同角色合并吞掉（判据 B10）。
        assertEquals(1, prepared.count { LayeredContextCompressor.isSummaryMessage(it) })
    }

    /** 固定返回一段合法结构化摘要的假实现：本测试只验证接线，不验证摘要质量。 */
    private class FixedSummarizer(private val text: String) : ContextSummarizer {
        override suspend fun summarize(messages: List<ChatMessage>, targetTokens: Int): SummaryResult =
            SummaryResult.Ok(text, TokenEstimator.estimate(text), 1L)
    }

    // ── W6：估算锚点 / 可机读计数 / 零消费开关 ──────────────────────────────

    /** 账本里最后一条 `prompt=` 估算值（这是「估算器看到了多少」的唯一可观测出口）。 */
    private fun lastPromptEstimate(): Int? {
        val line = AgentLogStore.recent().lastOrNull { it.message.contains("prompt=") }?.message
            ?: return null
        return Regex("""prompt=(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * T5：第 1 轮 provider 回报 `prompt_tokens = 33 800` ⇒ 第 2 轮的估算值必须 **≥ 33 800**。
     *
     * ## 为什么这条断言能证明锚点生效
     *
     * 第 2 轮的真实内容只有几百个字符（约 100 token）。**不用锚点**时估算必然是个三位数，
     * 与 33 800 差两个数量级；所以 `>= 33 800` 只可能来自「上次真值 + 其后增量」。
     *
     * ## 它**不能**证明什么（必须写清，否则下一个 agent 会以为 M1 已修）
     *
     * 锚点只修**静态部分**。W5-01 事故里被低估的那段增量（工具结果正文）仍然被低估，
     * 所以本用例绿不代表「32K 档不会再发出 33 800 token」—— 那条由
     * `PreStepBudgetTest` 的字符判据兜。两件事，别合并。
     */
    @Test
    fun promptEstimateUsesProviderTruthAsAnchor() {
        AgentLogStore.clear()
        val events = mutableListOf<AgentEvent>()
        val probe = FakeTool(
            spec = fakeSpec(
                name = "probe",
                parameters = listOf(ToolParameter("path", ToolParamType.STRING, "path", required = true)),
            ),
            behavior = { ToolResult(name = "probe", ok = true, output = "ok") },
        )
        val engine = FakeEngine(
            scripts = listOf(
                listOf(
                    GenerationEvent.ToolCallDelta(0, "c1", "probe", """{"path":"/a"}"""),
                    GenerationEvent.Finished(FinishReason.TOOL_CALLS),
                    // usage 在末帧单独到达，顺序与真实 adapter 一致。
                    GenerationEvent.Usage(prompt = 33_800, completion = 12, cachedPrompt = null),
                ),
                FakeEngine.textOnly("done"),
            ),
        )
        val request = AgentRequest(endpoint = endpoint)
        val ctx = testContext(
            events = events,
            registered = listOf(probe),
            engine = engine,
            request = request,
            state = RunState.forRequest(request),
        )

        val step = runBlocking { TurnLoop(generation = GenerationRound(backoff = {})).run(ctx) }

        assertEquals(Step.Done, step)
        assertEquals(2, engine.generateCount)
        val estimate = lastPromptEstimate()
        assertTrue(estimate != null, "estimate must be logged: " + AgentLogStore.recent().size)
        assertTrue(
            estimate!! >= 33_800,
            "estimate=" + estimate + " must be anchored on the provider truth 33800",
        )
    }

    /**
     * M2 可观测性：压缩器「无损放弃」必须留下**可机读**的计数。
     *
     * 旧文案 `context over budget but not compressible` 在 W5 排障时被误读成
     * 「没有压缩逻辑」，而它其实正是「压缩跑了、但没有可丢的东西」这条结论本身。
     * 判据是可 grep 的 token：`compress_skipped=not_compressible`。
     */
    @Test
    fun notCompressibleIsReportedAsAMachineReadableCounter() {
        AgentLogStore.clear()
        val narrow = RemoteEndpoint(id = "ep-narrow", contextLength = 8_000, maxOutputTokens = 2_000)
        // 单轮形态 [USER, MODEL(calls), TOOL]：安全切点只能落在 USER@0 ⇒ 保留区 = 全部。
        val singleTurn = listOf(
            ChatMessage(id = "u", role = Role.USER, text = "x".repeat(4_000)),
            ChatMessage(
                id = "m",
                role = Role.MODEL,
                toolCalls = listOf(ToolCall(id = "c1", name = "f", argumentsJson = "{}")),
            ),
            ChatMessage(
                id = "t",
                role = Role.TOOL,
                toolResults = listOf(ToolResult(callId = "c1", name = "f", output = "y".repeat(16_000))),
            ),
        )
        val ctx = testContext(
            events = mutableListOf<AgentEvent>(),
            request = AgentRequest(endpoint = narrow),
            state = RunState(singleTurn),
        )

        runBlocking { ContextBudget().prepare(ctx, "s".repeat(2_000)) }

        assertTrue(
            AgentLogStore.recent().any { it.message.contains("compress_skipped=not_compressible") },
            AgentLogStore.recent().joinToString("\n") { it.message },
        )
        // 无损放弃 ⇒ 历史一条都不许被改写（这是它「无损」的含义）。
        assertEquals(singleTurn.size, ctx.state.working().size)
    }

    /**
     * M4：压缩总开关被关时必须留一行证据，**而且每次 prepare 都要留**。
     *
     * `compressContext` W6 已全链接线（设置页 → SettingsRepository → AppContainer →
     * AgentPolicy，台账 W6-01 已 CLOSED），不再是「声明了没消费者」。关掉后压缩会
     * 静默跳过，这行日志仍是排障时唯一的可见证据。
     *
     * 「每次」是主理人补的口径：只打一次的日志会让用户「关了开关又打开」或
     * 「长会话跑到第 8 轮」时看不出这一轮到底压没压。这里连续 prepare 两次断言两行。
     */
    @Test
    fun disabledCompressionLeavesATraceOnEveryPrepare() {
        AgentLogStore.clear()
        val ctx = testContext(
            events = mutableListOf<AgentEvent>(),
            request = AgentRequest(endpoint = endpoint),
            policy = AgentPolicy(compressContext = false),
            state = RunState(history(4)),
        )
        val budget = ContextBudget()

        runBlocking { budget.prepare(ctx, "sys") }
        runBlocking { budget.prepare(ctx, "sys") }

        val lines = AgentLogStore.recent().count { it.message.contains("compress=disabled_by_policy") }
        assertTrue(
            lines >= 2,
            "expected one line per prepare, got " + lines + ":\n" +
                AgentLogStore.recent().joinToString("\n") { it.message },
        )
    }

    private companion object {
        /**
         * 首行必须是段头 `[CONTEXT SUMMARY]`，否则 `parseStructuredSummary` 直接 `Rejected("no-header")`。
         * 这条不是测试的怪癖：它保证「模型自由发挥的散文」不会被当成摘要塞进历史。
         */
        const val SAMPLE_SUMMARY: String =
            "[CONTEXT SUMMARY]\n" +
                "GOAL: keep the conversation context\n" +
                "FACTS: the user asked about budget accounting\n" +
                "DECISIONS: compression drops the oldest turns\n" +
                "DONE: counted system tokens in the budget"
    }
}

