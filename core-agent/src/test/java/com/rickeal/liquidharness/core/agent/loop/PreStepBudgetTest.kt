package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.AgentRequest
import com.rickeal.liquidharness.core.agent.context.HardBudgetShrinker
import com.rickeal.liquidharness.core.agent.context.hardCharCap
import com.rickeal.liquidharness.core.agent.context.hardContextBudgetTokens
import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.prompt.AgentPromptComposer
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.TerminationReason
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 硬预算兜底的三条判据（w6-plan §2.6 T1–T3）。
 *
 * ## 为什么这里的断言是**字符**与**占位事实**，而不是 token
 *
 * W5-01 事故的根因是估算器对代码 / JSON 低估约 2 倍。任何「用同一个估算器断言没超预算」
 * 的用例都会**永远绿**（同义反复），而真机上 33 800 token 一次都拦不住。
 * 所以：
 *  - T1 断言 `messages` 的**字符**总量 ≤ `hardCharCap`（量纲与估算器无关）；
 *  - T2 断言占位**真的发生**（只断言「没超」无法区分「没超」与「没检查」）；
 *  - T3 断言 `generateCount == 0`（请求根本没发出去）。
 */
class PreStepBudgetTest {

    /** 32K 档：`hard = 32 000 − 4 096 = 27 904`，`hardCharCap = 27 904 × 1.8 = 50 227`。 */
    private val endpoint = RemoteEndpoint(
        id = "ep-32k",
        name = "32k",
        contextLength = 32_000,
        maxOutputTokens = 4_096,
    )
    private val policy = AgentPolicy()

    private fun loop(): TurnLoop = TurnLoop(generation = GenerationRound(backoff = {}))

    /**
     * 一批 `count` 个 `file_read` 调用 + 收尾帧。
     *
     * ⚠️ 每个调用的参数**必须不同**：`ToolExecutionGuard` 的同参护栏对同一签名只放行
     * 3 次，第 4 次起返回「repeated call ignored」（`output` 为空、只有 errorMessage）。
     * 用 5 个 `{}` 会让 3 条结果变成重复护栏的产物，于是「占位」这条判据**看起来通过**
     * 却什么也没验证 —— 本用例第一次就是这么红的（T1 绿、T2 红）。
     */
    private fun toolBatch(count: Int): List<GenerationEvent> {
        val events = ArrayList<GenerationEvent>(count + 1)
        for (index in 0 until count) {
            events.add(
                GenerationEvent.ToolCallDelta(index, "call-$index", "file_read", """{"path":"/f$index"}""")
            )
        }
        events.add(GenerationEvent.Finished(FinishReason.TOOL_CALLS))
        return events
    }

    /** 每个结果 15 000 字符：5 个 = 75 000，超过聚合上限 64 000，但残余仍远超字符上限。 */
    private fun bigReader(): FakeTool = FakeTool(
        spec = fakeSpec(
            name = "file_read",
            parameters = listOf(ToolParameter("path", ToolParamType.STRING, "file path", required = true)),
        ),
        behavior = {
            ToolResult(name = "file_read", ok = true, output = "x".repeat(15_000))
        },
    )

    private fun charsOf(messages: List<com.rickeal.liquidharness.core.model.ChatMessage>): Int =
        HardBudgetShrinker.promptChars(messages)

    /**
     * T1：脚本 `[toolCalls×5, textOnly]`，32K 档 ⇒ **第 2 次请求的 messages 字符总量 ≤ hardCharCap**。
     *
     * 断言的是**字符**：token 断言会因为 M1（估算器低估）永远绿。
     */
    @Test
    fun secondRequestStaysWithinTheHardCharCap() {
        AgentLogStore.clear()
        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(scripts = listOf(toolBatch(5), FakeEngine.textOnly("done")))
        val request = AgentRequest(endpoint = endpoint, userInput = "read these five files")
        val ctx = testContext(
            events = events,
            registered = listOf(bigReader()),
            engine = engine,
            request = request,
            policy = policy,
            state = RunState.forRequest(request),
        )

        val step = runBlocking { loop().run(ctx) }

        assertEquals(Step.Done, step)
        val sent = assertNotNull(engine.lastRequest).messages
        val cap = hardCharCap(endpoint, policy)
        assertTrue(
            charsOf(sent) <= cap,
            "2nd request chars=" + charsOf(sent) + " must be <= hardCharCap=" + cap,
        )
        // 反 vacuous：这条请求确实存在工具结果（否则「没超」只是因为它没什么内容）。
        assertTrue(sent.any { it.toolResults.isNotEmpty() }, "request must carry tool results")
        // 反 vacuous 第二条：收缩动作**真的发生过**。只断言「没超」时，
        // 一个把硬预算判成「永远通过」的实现也能绿 —— 那就退化成同义反复了。
        assertTrue(
            AgentLogStore.recent().any { it.message.contains("hard_budget_shrunk") },
            AgentLogStore.recent().joinToString("\n") { it.message },
        )
    }

    /**
     * T2：同一场景 ⇒ 至少一条 `ToolResult.output` 以 `[omitted: hard budget` 开头，
     * `callId` 非空，且**结果条数与调用数相等**（一条都没丢）。
     *
     * 只断言 T1 的「没超」无法区分「没超」与「没检查」；占位是「动作真的发生了」的唯一证据。
     */
    @Test
    fun oversizedToolResultsAreReplacedByHardBudgetPlaceholders() {
        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(scripts = listOf(toolBatch(5), FakeEngine.textOnly("done")))
        val request = AgentRequest(endpoint = endpoint, userInput = "read these five files")
        val ctx = testContext(
            events = events,
            registered = listOf(bigReader()),
            engine = engine,
            request = request,
            policy = policy,
            state = RunState.forRequest(request),
        )

        runBlocking { loop().run(ctx) }

        val results = assertNotNull(engine.lastRequest).messages.flatMap { it.toolResults }
        assertEquals(5, results.size, "every call must still have a result (nothing dropped)")
        assertTrue(results.all { it.callId.isNotEmpty() }, "callId must survive shrinking")
        assertTrue(
            results.any { it.output.startsWith("[omitted: hard budget") },
            "no placeholder: " + results.map { it.output.take(40) },
        )
        // 占位措辞不许像失败（像失败会让模型重试同一次调用，把省钱变成多花一次往返）。
        assertTrue(
            results.none { it.output.startsWith("[omitted") && it.output.contains("fail") },
            "placeholder must not look like a failure",
        )
    }

    /**
     * T3：单条 USER 100 万字符（**无可收缩项**）⇒ 终态 `BreakerTripped`，且**一个请求都没发**。
     *
     * 断言 `generateCount == 0` 是关键：只断言「有失败事件」的话，路径可能来自别的熔断，
     * 而真正的缺陷（发一个注定 400 的请求、白花一次往返与一次计费）依然存在。
     */
    @Test
    fun oversizedUserInputIsPreventedBeforeAnyRequestIsSent() {
        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(scripts = listOf(FakeEngine.textOnly("never")))
        val request = AgentRequest(endpoint = endpoint, userInput = "u".repeat(1_000_000))
        val ctx = testContext(
            events = events,
            engine = engine,
            request = request,
            policy = policy,
            state = RunState.forRequest(request),
        )

        val step = runBlocking { loop().run(ctx) }

        assertEquals(Step.Terminated, step)
        assertEquals(0, engine.generateCount, "no request may be sent when the hard budget cannot be met")
        val failed = events.filterIsInstance<AgentEvent.Failed>().single()
        assertEquals(TerminationReason.BreakerTripped, failed.terminatedBy)
        assertTrue(
            failed.message.startsWith("context_overflow_prevented: chars="),
            "detail must be machine readable: " + failed.message,
        )
        // 证据里要能读出「超了多少」与「上限是多少」，否则排障只能靠猜。
        assertTrue(failed.message.contains("cap=" + hardCharCap(endpoint, policy)), failed.message)
    }

    // ── T7：L3 的 chars/token 必须按 provider 真值标定 ──────────────────────────

    /** 端点 8 096 / 4 096 ⇒ `hard = 4 000`；floor cap = 7 200，标定后约 5.8× = 23 200。 */
    private val calibratedEndpoint = RemoteEndpoint(
        id = "ep-calibrated",
        name = "calibrated",
        contextLength = 8_096,
        maxOutputTokens = 4_096,
    )

    /** 账本里最后一条 `hard_budget` 行的 `cap=` 值。**这是标定是否生效的唯一可观测出口。** */
    private fun lastLoggedCap(): Int? {
        val line = AgentLogStore.recent().lastOrNull { it.message.startsWith("hard_budget ") }?.message
            ?: return null
        return Regex("""cap=(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * T7：provider 回报的真值必须把 `hardCharCap` 从 floor（`hard × 1.8`）**抬高到实测比值**。
     *
     * ## 为什么这条判据必须存在
     *
     * chars/token 是**内容相关**的：英文散文实测 5.73～5.75、代码 / JSON ≈2.0。
     * 固定 1.8 会把散文会话的可用字符空间砍到 1/3 —— 这是可感知的信息损失。
     * 只写 `max(1.8, measured)` 而不测，下一个人一定会把它当成「顺手加的」删掉。
     *
     * ## 场景（第一轮在 floor 之内，第二轮才越过 floor）
     *
     * - 第 1 轮：`hard = 4 000`，floor cap = 7 200；实际发 ≈6 000 字符，provider 回报
     *   `prompt = chars / 5.8` ⇒ `measured ≈ 5.8`；
     * - 第 2 轮：追加 15 000 字符的工具结果 ⇒ 总量 ≈21 000，
     *   **超过 floor cap 7 200，但低于标定后的 cap ≈23 200**。
     *
     * 判据三条（第二条是行为判据，换回固定 1.8 必红）：
     * 1. 账本 `cap=` **≥ 20 000**（= `hard 4 000 × 5.0` 标定值；实测 ≈23 200，留了余量）；
     * 2. 第 2 轮**真的把 21 000 字符发出去了**（> 7 200）且账本**没有** `hard_budget_shrunk`；
     * 3. 循环正常收尾（没有被误熔断）。
     *
     * ## 🔴 反例锚：为什么下界是 20 000 而不是「比 floor 大一点」
     *
     * 退回固定 floor 1.8 时 `cap = 4 000 × 1.8 = 7 200`。所以任何 `cap >= 5 750`
     * 之类的下界**根本挡不住回归**（7 200 也满足）—— 那会是一条假阳性判据，
     * 是本仓最忌的形态。20 000 是「标定真的发生」的最低可信值（`measured ≥ 5.0`）。
     *
     * **调低这个阈值前先读这句**：低于 7 200 它就不再是判据，而是一句装饰。
     */
    @Test
    fun hardCharCapIsCalibratedFromTheProviderReportedRatio() {
        AgentLogStore.clear()
        val reader = bigReader()
        val registry = ToolRegistry().apply { register(reader) }
        val hard = hardContextBudgetTokens(calibratedEndpoint, policy)
        val floorCap = hardCharCap(calibratedEndpoint, policy)
        assertEquals(4_000, hard)
        assertEquals(7_200, floorCap)

        // 先算 system 段长度，把第 1 轮的总字符数控制在 floor cap 之内。
        val probe = AgentRequest(endpoint = calibratedEndpoint, userInput = "x")
        val systemChars = AgentPromptComposer.compose(probe, registry.specs()).length
        val userChars = (6_000 - systemChars).coerceAtLeast(200)
        val round1Chars = systemChars + userChars
        assertTrue(
            round1Chars <= floorCap,
            "round 1 must fit under the floor cap: chars=" + round1Chars + " floorCap=" + floorCap,
        )
        // provider 回报一个 ≈5.8 的比值（真实散文实测 5.73～5.75，这里取略高的值以便断言有余量）。
        val promptTokens = (round1Chars / 5.8f).toInt().coerceAtLeast(1)

        val events = mutableListOf<AgentEvent>()
        val engine = FakeEngine(
            scripts = listOf(
                listOf(
                    GenerationEvent.ToolCallDelta(0, "call-0", "file_read", """{"path":"/a"}"""),
                    GenerationEvent.Finished(FinishReason.TOOL_CALLS),
                    GenerationEvent.Usage(prompt = promptTokens, completion = 5, cachedPrompt = null),
                ),
                FakeEngine.textOnly("done"),
            ),
        )
        val request = AgentRequest(endpoint = calibratedEndpoint, userInput = "x".repeat(userChars))
        val ctx = testContext(
            events = events,
            registered = listOf(reader),
            engine = engine,
            request = request,
            policy = policy,
            state = RunState.forRequest(request),
        )

        val step = runBlocking { loop().run(ctx) }

        assertEquals(Step.Done, step)
        assertEquals(2, engine.generateCount)
        // 第 2 轮的请求就是最后一次请求：它带着那条 15 000 字符的工具结果。
        val sentChars = charsOf(assertNotNull(engine.lastRequest).messages)
        assertTrue(
            sentChars > floorCap,
            "round 2 must exceed the floor cap, otherwise the judgement is vacuous: " + sentChars,
        )
        val cap = lastLoggedCap()
        assertNotNull(cap, "hard_budget line must be logged every round")
        // 20 000 而不是「> floor cap 7 200」：后者在退回固定 1.8 时同样成立 ⇒ 假阳性。
        assertTrue(
            cap!! >= 20_000,
            "cap=" + cap + " must be raised by the measured ratio (floor would give " + floorCap + ")",
        )
        assertTrue(
            AgentLogStore.recent().none { it.message.contains("hard_budget_shrunk") },
            "prose content must not be shrunk once the ratio is calibrated: " +
                AgentLogStore.recent().joinToString("\n") { it.message },
        )
    }
}
