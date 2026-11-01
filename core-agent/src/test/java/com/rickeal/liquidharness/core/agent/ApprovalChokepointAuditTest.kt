package com.rickeal.liquidharness.core.agent

import com.rickeal.liquidharness.core.agent.approval.ToolApprovalCache
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalDecision
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalHandler
import com.rickeal.liquidharness.core.agent.loop.FakeEngine
import com.rickeal.liquidharness.core.agent.loop.FakePolicyGatedTool
import com.rickeal.liquidharness.core.agent.loop.FakeTool
import com.rickeal.liquidharness.core.agent.loop.ToolApprovalFlow
import com.rickeal.liquidharness.core.agent.loop.ToolCallExecutor
import com.rickeal.liquidharness.core.agent.loop.TurnContext
import com.rickeal.liquidharness.core.agent.loop.fakeSpec
import com.rickeal.liquidharness.core.agent.loop.testContext
import com.rickeal.liquidharness.core.agent.loop.testRequest
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.agent.tool.tools.ClipboardTool
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.ToolCall
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 审批**咽喉点**审计：全仓产生审批决策的入口必须收敛到 [ToolApprovalFlow] 一处。
 *
 * ## 为什么需要这条测试（它防的是什么）
 *
 * 审批的失效形态不是「判错了」，而是「**某个入口根本不经过它**」。那种漏洞在单点上
 * 永远测不出来：`ToolApprovalFlowTest` 全绿、`AgentApprovalRunTest` 全绿，而新增的
 * 第二条路径（一个新工具、一次策略短路、一个"优化"掉的早退）静默放行。
 * 所以本类的判据是**穷举表**：表里每一行是一个「会产生审批决策」的真实入口，
 * 每行都必须观测到 `ApprovalRequested` —— 只要有一条路径绕过闸门，它就没有事件。
 *
 * ## 入口是怎么穷举出来的（可复算，不是凭想象）
 *
 * ```
 * grep -rn "ApprovalDecision\|approvalHandler\|ApprovalRequested" --include=*.kt core-agent feature-chat
 * ```
 *
 * 实测结论（Verified as of 本次作业）：
 * - 决策类型只有 `ToolApprovalDecision`（4 值 sealed），**唯一**的生产者是
 *   `ToolApprovalFlow.decide`；唯一调用点是 `ToolCallExecutor.execute`（第 4 步）；
 *   宿主面入口是 `AgentRunner.run(request, approval)` → `TurnContext.approval`。
 * - `feature-chat` 只有**消费方**：`ChatViewModel` 造一个 `CompletableDeferred` 句柄、
 *   `ChatErrorPanel` 把按钮点击翻译成 `ToolApprovalDecision`。它们不产生 ask，
 *   只回答 ask —— 故不进表（进了就是「测 UI 按钮」，与本判据无关）。
 * - **`AgentPolicy.autoApproveDangerous` 在全仓 0 个消费点**（只有声明）。
 *   这正是它安全的原因，也是 [autoApproveDangerousDoesNotExemptClipboardRead] 等
 *   三条用例要钉住的东西：一旦有人把它接进闸门，那三条会立刻变红。
 * - **MCP sampling / roots 在本仓尚不存在**（`grep -rn "sampling|roots" core-mcp`
 *   只命中 `SamplingParams` 这个采样参数类型，与 MCP 协议的 `sampling/createMessage`
 *   无关）。所以 bypass-immune 清单里的 sampling / roots 两行**当前无法注入**，
 *   本类只钉已有的第三行（MCP 远端工具）。等它们落地时必须补进 [askSourceTable]，
 *   否则那张表就从「穷举」退化成「抽样」。
 *
 * ## 判据写法
 *
 * 每行都断言三件事：**卡发了（恰好一张）/ handler 被问了（恰好一次）/ 工具没执行**。
 * 只看「有 ApprovalRequested」不够：一个「先执行、后补一张卡」的实现能骗过事件断言。
 */
class ApprovalChokepointAuditTest {

    /**
     * 表里的一行 = 一个会产生审批决策的真实入口。
     *
     * [prep] 让「前置状态不同」的入口也能进同一张表（例如「被拒 1 次后仍在阈值内」
     * 必须先制造一次拒绝）。它跑在**被测调用之前**，其产生的事件会被清掉。
     */
    private class AskRow(
        val id: String,
        val tool: Tool,
        val call: ToolCall,
        val cache: ToolApprovalCache? = null,
        val prep: suspend (ToolApprovalFlow, TurnContext) -> Unit = { _, _ -> },
    )

    /** 被所有行共享的「同意」句柄：只想确认闸门问了人，不关心问完怎么走。 */
    private fun countingHandler(counter: IntArray): ToolApprovalHandler =
        ToolApprovalHandler { _, _ ->
            counter[0] += 1
            ToolApprovalDecision.Deny
        }

    /**
     * 穷举表。**每一行都必须能指到现役代码里的一个判定分支**：
     *
     * | 行 id | 代码出处 | 为什么它会弹卡 |
     * |---|---|---|
     * | `static-dangerous` | `ToolApprovalFlow.needsApproval` 的 `spec.dangerous` | 静态危险声明 |
     * | `static-requires-confirmation` | 同上，`spec.requiresConfirmation` | 静态显式声明 |
     * | `param-gate-write` | `needsApproval` 的 `ParamGatedTool` 分支 + `ClipboardTool.requiresConfirmationFor` | `set` 是写 |
     * | `param-gate-bad-json` | 同上（实现约定 fail-closed 返回 true） | 参数解析失败 |
     * | `contributed-mcp` | `ToolRegistry.registerContributed` 强制 `requiresConfirmation=true` | 不可信来源 |
     * | `session-cache-miss` | `ToolApprovalFlow.decide` 的 `cache.isAllowed` 分支（**未命中**） | 指纹不同 ⇒ 仍要问 |
     * | `denial-throttle-below-limit` | 同函数的 `denials` 计数（**未达阈值**） | 第 2 次拒绝前仍要问 |
     *
     * 后两行不是「新的 ask 源」，而是两条**会抑制弹卡**的路径。把它们放进表里，
     * 是因为「抑制」最容易被写成「绕过」：缓存按 `name + 参数指纹` 命中、拒绝节流
     * 按次数命中，任何一处判定写松，卡就静默消失。断言它们在**不该命中时仍然弹卡**，
     * 等价于给这两条捷径钉上边界。
     */
    private fun askSourceTable(): List<AskRow> {
        val clipboard = ClipboardTool(null)

        // MCP / 第三方工具：注册时被强制要求审批（ToolRegistry.registerContributed）。
        val contributedRegistry = ToolRegistry()
        contributedRegistry.registerContributed(FakeTool(fakeSpec(name = "mcp_echo")))
        val contributed = requireNotNull(contributedRegistry.get("mcp_echo"))

        // 会话缓存的「未命中」形态：先放行**另一个**参数指纹，被测调用仍须弹卡。
        val cache = ToolApprovalCache()
        cache.allowForSession(ToolCall(id = "x0", name = "mcp_echo", argumentsJson = "{\"text\":\"other\"}"))

        return listOf(
            AskRow(
                id = "static-dangerous",
                tool = FakeTool(fakeSpec(name = "danger", dangerous = true)),
                call = ToolCall(id = "c1", name = "danger", argumentsJson = "{}"),
            ),
            AskRow(
                id = "static-requires-confirmation",
                tool = FakeTool(fakeSpec(name = "confirm", requiresConfirmation = true)),
                call = ToolCall(id = "c2", name = "confirm", argumentsJson = "{}"),
            ),
            AskRow(
                id = "param-gate-write",
                tool = clipboard,
                call = ToolCall(id = "c3", name = "clipboard", argumentsJson = "{\"action\":\"set\",\"text\":\"x\"}"),
            ),
            AskRow(
                id = "param-gate-bad-json",
                tool = clipboard,
                call = ToolCall(id = "c4", name = "clipboard", argumentsJson = "{not json"),
            ),
            AskRow(
                id = "contributed-mcp",
                tool = contributed,
                call = ToolCall(id = "c5", name = "mcp_echo", argumentsJson = "{\"text\":\"hi\"}"),
            ),
            AskRow(
                id = "session-cache-miss",
                tool = contributed,
                call = ToolCall(id = "c6", name = "mcp_echo", argumentsJson = "{\"text\":\"hi\"}"),
                cache = cache,
            ),
            AskRow(
                id = "denial-throttle-below-limit",
                tool = FakeTool(fakeSpec(name = "confirm", requiresConfirmation = true)),
                call = ToolCall(id = "c7", name = "confirm", argumentsJson = "{}"),
                prep = { flow, ctx -> flow.decide(ctx, ToolCall(id = "c7a", name = "confirm", argumentsJson = "{}"), FakeTool(fakeSpec(name = "confirm", requiresConfirmation = true))) },
            ),
        )
    }

    /**
     * 主判据：表里每个入口都必须在事件序列里留下一张 `ApprovalRequested`。
     *
     * 断言「恰好一张」而不是「至少一张」：`ApprovalRequested` 在内核里**无去重**
     * （见 `EventReducer` 的注释），重复发就是真的弹两张卡，用户会看到同一个工具问两次。
     */
    @Test
    fun everyAskSourceRoutesThroughTheSingleApprovalChokepoint() {
        val rows = askSourceTable()
        assertTrue(rows.size >= 6, "穷举表至少 6 行（少一行就可能漏掉一条入口）")

        for (row in rows) {
            val events = mutableListOf<AgentEvent>()
            val asked = IntArray(1)
            val ctx = testContext(events, approval = countingHandler(asked))
            val flow = ToolApprovalFlow(row.cache ?: ToolApprovalCache())

            runBlocking {
                row.prep(flow, ctx)
                // 前置动作（制造拒绝计数 / 缓存状态）的事件不计入被测调用的观测窗口。
                events.clear()
                asked[0] = 0

                val decision = flow.decide(ctx, row.call, row.tool)

                assertEquals(1, events.count { it is AgentEvent.ApprovalRequested }, "${row.id}: 必须恰好弹一张卡")
                assertEquals(1, asked[0], "${row.id}: handler 必须被问恰好一次")
                assertTrue(decision is ToolApprovalFlow.Decision.Denied, "${row.id}: 拒绝应走返回值通道")
            }
        }
    }

    /**
     * 宿主面入口（`AgentRunner.run`）也必须走同一个咽喉点。
     *
     * 表里那些行测的是 `ToolApprovalFlow` 内部；这一条测的是**门面**：
     * 如果哪天有人在 `AgentRunner` / `TurnLoop` 里加一条「危险工具直接执行」的捷径，
     * 表仍然全绿，只有这条会红。
     */
    @Test
    fun runnerFacadeRoutesApprovalThroughTheSameChokepoint() {
        val tool = FakeTool(fakeSpec(name = "gated", requiresConfirmation = true))
        val events = mutableListOf<AgentEvent>()
        var asked = 0
        val runner = AgentRunner(
            engine = FakeEngine(
                scripts = listOf(
                    listOf(
                        GenerationEvent.ToolCallDelta(0, "c1", "gated", "{}"),
                        GenerationEvent.Finished(FinishReason.TOOL_CALLS),
                    ),
                    FakeEngine.textOnly("done"),
                ),
            ),
            tools = ToolRegistry().apply { register(tool) },
        )

        runBlocking {
            runner.run(
                testRequest(),
                approval = ToolApprovalHandler { _, _ ->
                    asked += 1
                    ToolApprovalDecision.Deny
                },
            ).collect { events += it }
        }

        assertEquals(1, events.count { it is AgentEvent.ApprovalRequested })
        assertEquals(1, asked)
        assertEquals(0, tool.invocations)
    }

    /**
     * bypass-immune 第一行：**剪贴板读不受 `autoApproveDangerous` 豁免**。
     *
     * `clipboard get` 不弹卡（参数级闸门判它无害），所以它的约束只能是策略闸门：
     * `allowClipboardRead=false` ⇒ 工具体一次都不许被调用。这条通道是提示注入的
     * 外带路径（读一眼剪贴板 → 拼进下一次请求），任何"自动放行"开关都不该打开它。
     */
    @Test
    fun autoApproveDangerousDoesNotExemptClipboardRead() {
        val events = mutableListOf<AgentEvent>()
        // 用 FakePolicyGatedTool 而不是真 ClipboardTool：本用例要钉的是「策略闸门不受
        // autoApproveDangerous 影响」，与剪贴板的具体实现无关；真 ClipboardTool 还要
        // 一个 ClipboardManager，在 JVM 单测里没有。
        val tool = FakePolicyGatedTool(fakeSpec(name = "clipboard")) { policy ->
            if (policy.allowClipboardRead) null else "clipboard read disabled by policy"
        }
        val ctx = testContext(
            events = events,
            registered = listOf(tool),
            approval = { _, _ -> ToolApprovalDecision.AllowOnce },
            policy = AgentPolicy(autoApproveDangerous = true, allowClipboardRead = false),
        )
        val call = ToolCall(id = "g1", name = "clipboard", argumentsJson = "{\"action\":\"get\"}")

        val outcome = runBlocking { ToolCallExecutor().execute(ctx, listOf(call)) }

        assertEquals(ToolCallExecutor.Outcome.Continue, outcome)
        assertEquals(0, tool.invocations)
        val result = events.filterIsInstance<AgentEvent.ToolResultReceived>().single().result
        assertFalse(result.ok)
        assertEquals("clipboard read disabled by policy", result.errorMessage)
        // `get` 本来就不该弹卡；这里同时钉住「策略拦截不是靠审批实现的」。
        assertEquals(0, events.count { it is AgentEvent.ApprovalRequested })
    }

    /**
     * bypass-immune 第二行：**MCP / 第三方工具不受 `autoApproveDangerous` 豁免**。
     *
     * 不可信来源的工具由 `registerContributed` 强制 `requiresConfirmation=true`；
     * 自动放行开关只（可能）作用于内置危险工具，不能把这条强制声明吃掉。
     * 这里同时验证「无通道 ⇒ fail-closed」：没有 handler 时它必须被跳过而不是放行。
     */
    @Test
    fun autoApproveDangerousDoesNotExemptContributedTool() {
        val registry = ToolRegistry()
        registry.registerContributed(FakeTool(fakeSpec(name = "mcp_echo")))
        val tool = requireNotNull(registry.get("mcp_echo"))
        assertTrue(tool.spec.requiresConfirmation, "registerContributed 必须强制 requiresConfirmation")

        val events = mutableListOf<AgentEvent>()
        val ctx = testContext(events, approval = null, policy = AgentPolicy(autoApproveDangerous = true))
        val call = ToolCall(id = "m1", name = "mcp_echo", argumentsJson = "{}")

        runBlocking { ToolApprovalFlow().decide(ctx, call, tool) }

        assertEquals(1, events.count { it is AgentEvent.ToolSkipped })
        assertEquals(0, events.count { it is AgentEvent.ApprovalRequested })
    }

    /**
     * bypass-immune 第三行：**危险工具本身也不受豁免**（开关当前无消费点）。
     *
     * 这条是「开关被接线」的第一道警报：`autoApproveDangerous=true` 时若危险工具
     * 不再弹卡，本用例立刻红。注意判据是**事件**而不是返回值 ——
     * 一个「先放行、再补一张卡」的实现返回值也对。
     */
    @Test
    fun autoApproveDangerousStillAsksForDangerousTool() {
        val events = mutableListOf<AgentEvent>()
        val tool = FakeTool(fakeSpec(name = "danger", dangerous = true))
        val ctx = testContext(
            events,
            approval = { _, _ -> ToolApprovalDecision.Deny },
            policy = AgentPolicy(autoApproveDangerous = true),
        )
        val call = ToolCall(id = "d1", name = "danger", argumentsJson = "{}")

        runBlocking { ToolApprovalFlow().decide(ctx, call, tool) }

        assertEquals(1, events.count { it is AgentEvent.ApprovalRequested })
        assertEquals(0, tool.invocations)
    }
}
