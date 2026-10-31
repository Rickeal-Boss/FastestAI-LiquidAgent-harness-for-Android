package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.tool.PolicyGatedTool
import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.agent.tool.ToolArgsValidator
import com.rickeal.liquidharness.core.agent.tool.ToolExecutionGuard
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 一批工具调用的执行流水线。**逐个串行**执行（并发执行会让审批卡同时弹 N 张，
 * 且工具副作用互相可见性无法推理）。
 *
 * ## 单个调用的顺序（每一步都有不可交换的理由）
 *
 * 1. `ctx.checkActive()` —— 取消检查点，**所有挂起点之前**都要有（`AGENTS.md` §五.4）。
 * 2. 工具不存在 ⇒ `ToolSkipped(call, "unknown tool")`，**不发 `ToolResultReceived`**。
 *    没有 spec 就没有轨迹创建点（`ToolCallStarted` 需要 spec），所以这里只能发 skip；
 *    UI 侧对「没有轨迹的 skip」是原样返回，不会伪造一条空轨迹。
 * 3. 发 `ToolCallStarted(call, spec)` —— **轨迹创建点，必须先于审批**。
 *    审批卡要显示工具名与参数，UI 得先有这条轨迹；`ToolResultReceived` / `ToolSkipped`
 *    在 UI 侧「只更新不新建」，漏了这一步等于整批事件静默丢失。
 * 4. 审批（[ToolApprovalFlow]）。可能 emit `ApprovalRequested` / `ToolSkipped`。
 * 5. 策略闸门（[PolicyGatedTool]）：**不看用户意愿的硬禁止**（如策略关闭后的 `clipboard get`）。
 *    **在审批之后**：审批是「用户同不同意」，策略是「这件事本来就不许」，先让人看见再拒。
 * 6. 参数校验。**在审批之后**（见下「顺序选择」）。
 * 7. 执行（带超时，跑在 [ioDispatcher] 上）。
 * 8. 护栏记账（同参 / 失败连击 / 振荡），熔断信号返回给调用方。
 * 9. 发 `ToolResultReceived`，成功则 `recordToolExecuted()`。
 * 10. 整批结束后落工作副本（[ToolResultCommit.commit]）。
 *
 * ## 顺序选择：**先审批，后校验**（显式声明，外审要求二选一）
 *
 * W2 判据 #4 要求「坏 JSON 仍要弹卡」。若先校验并提前 return，参数级闸门
 * （`ParamGatedTool.requiresConfirmationFor`，约定 fail-closed 返回 true）就**永远跑不到** ——
 * 坏参数的 `clipboard set` 会绕过审批直接拿到一条校验错误，而模型下一步很可能把参数
 * 修好再发一次（此时用户已经被训练成「这个工具不弹卡」）。所以审批在前：
 * 参数坏 ⇒ 弹卡 ⇒ 用户看到「模型想写剪贴板但参数是坏的」⇒ 拒绝或放行后由校验拦下。
 * 两种顺序在「不会执行」这一点上等价，差别只在**用户是否看见**，而看见才是审批的意义。
 *
 * ## 拒绝 / 跳过 / 熔断三条出口
 *
 * | 出口 | 事件 | 工作副本 | 循环 |
 * |---|---|---|---|
 * | 拒绝（`Denied`） | `ToolResultReceived(ok=false)` | 有结果 | 继续下一轮 |
 * | 跳过（`Skipped` / 未知工具） | `ToolSkipped` | 无结果（发送前由 sanitize 补丁） | 继续下一轮 |
 * | 熔断（护栏） | `ToolResultReceived` + 返回值 `Tripped` | 有结果 | 调用方终止 |
 */
class ToolCallExecutor(
    private val approvalFlow: ToolApprovalFlow = ToolApprovalFlow(),
    private val commit: ToolResultCommit = ToolResultCommit(),
    /**
     * 工具执行用的调度器。**默认 `Dispatchers.IO`，不要改回默认上下文**：
     * 调用链源头是 `ChatViewModel.viewModelScope.launch`（`Dispatchers.Main`），
     * 而工具体内部是 `canonicalFile` / `writeText` / `listFiles` 这类阻塞 IO ——
     * 不切走就是「主线程做磁盘读写」，真机上表现为掉帧甚至 ANR。
     *
     * 单测注入单线程 executor（`Executors.newSingleThreadExecutor().asCoroutineDispatcher()`）
     * 来钉住「执行确实发生了另一条线程上」，比断言协程上下文更难被假通过。
     */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** 一次执行的结论。熔断必须由调用方终止 run（`TerminationReason.BreakerTripped`）。 */
    sealed interface Outcome {

        /** 本批处理完毕，进下一轮。 */
        data object Continue : Outcome

        /** 护栏熔断。[detail] 是 ASCII 证据串（进日志与诊断卡）。 */
        data class Tripped(val detail: String) : Outcome
    }

    /**
     * 护栏实例。**惰性创建**：阈值来自 `policy`，而 policy 在构造本类时还拿不到；
     * 生命周期 = 本实例（一次 run），所以计数跨轮有效 —— 失败连击判据本来就是跨轮的。
     */
    private var guard: ToolExecutionGuard? = null

    /** 执行一批调用。**保证：进入时 `ToolCallStarted` 先发，收尾时结果已落工作副本。** */
    suspend fun execute(ctx: TurnContext, calls: List<ToolCall>): Outcome {
        if (calls.isEmpty()) return Outcome.Continue
        val results = ArrayList<ToolResult>(calls.size)
        var trip: String? = null
        for (call in calls) {
            ctx.checkActive()
            val tool = ctx.tools.get(call.name)
            if (tool == null) {
                AgentLogStore.error("unknown tool requested: " + call.name)
                ctx.emit(AgentEvent.ToolSkipped(call, UNKNOWN_TOOL))
                continue
            }
            ctx.emit(AgentEvent.ToolCallStarted(call, tool.spec))

            val decision = approvalFlow.decide(ctx, call, tool)
            if (decision is ToolApprovalFlow.Decision.Skipped) continue
            if (decision is ToolApprovalFlow.Decision.Aborted) {
                // 陷阱 3：只有「停止」走异常通道，且保留已收正文（AgentRunner 补 Cancelled）。
                throw CancellationException(ABORTED_BY_USER)
            }
            val execution = if (decision is ToolApprovalFlow.Decision.Denied) {
                // 陷阱 3：拒绝走返回值 —— 产出 ok=false 的结果，循环继续。
                Execution(denied(call, decision.reason), null)
            } else {
                run(ctx, call, tool)
            }
            results.add(execution.result)
            ctx.emit(AgentEvent.ToolResultReceived(execution.result))
            if (execution.result.ok) ctx.state.recordToolExecuted()
            if (execution.trip != null) {
                trip = execution.trip
                break
            }
        }
        commit.commit(ctx, calls, results)
        val breaker = trip
        return if (breaker != null) Outcome.Tripped(breaker) else Outcome.Continue
    }

    /** 已获批的调用：护栏 → 校验 → 执行 → 记账。 */
    private suspend fun run(ctx: TurnContext, call: ToolCall, tool: Tool): Execution {
        val guard = guardFor(ctx)
        when (val verdict = guard.beforeCall(call.name, call.argumentsJson)) {
            is ToolExecutionGuard.Verdict.RepeatedCall ->
                // 同参护栏：不执行，但**仍要回一条结果**（有 call 无 result 会被端点拒绝）。
                return Execution(repeated(call, verdict.streak), null)

            is ToolExecutionGuard.Verdict.BreakerTripped ->
                return Execution(failure(call, verdict.evidence), verdict.evidence)

            ToolExecutionGuard.Verdict.Proceed -> Unit
        }

        // 策略闸门：不看用户意愿的硬禁止（例：策略关闭后的 `clipboard get`）。
        // 放在校验**之前**：策略禁止是「这件事本来就不许」，比「参数写错了」更根本 ——
        // 命中就照常记一次失败连击（模型反复重发被禁止的调用，也该走到熔断）。
        // ⚠️ 已知取舍：本检查在**审批之后**，所以「spec.dangerous/requiresConfirmation + 策略禁止」
        // 的工具会先弹一张卡、用户点了同意再被拒（卡片点了没用）。当前无此形态的工具
        // （`clipboard get` 的 `needsApproval` 为 false，根本不会弹卡），故不在 `ToolApprovalFlow`
        // 里前置策略判断 —— 那会把「策略」耦合进审批类。**这是取舍，不是漏判。**
        if (tool is PolicyGatedTool) {
            val reason = tool.blockedBy(ctx.policy, call.argumentsJson)
            if (reason != null) {
                AgentLogStore.warn("tool blocked by policy: " + call.name + " (" + reason + ")")
                return finish(guard, call, failure(call, reason))
            }
        }

        val validated = ToolArgsValidator.validate(tool.spec, call.argumentsJson)
        if (validated is ToolArgsValidator.Result.Invalid) {
            // 校验失败也算一次失败：模型连续发坏参数正是失败连击该抓的形态。
            return finish(guard, call, failure(call, validated.reason))
        }
        val raw = invoke(ctx, call, tool)
        return finish(guard, call, commit.truncate(call, raw, tool, ctx.policy))
    }

    /**
     * 执行 + 超时。超时结果标 `indeterminate`：工具可能已经在后台产生了副作用。
     *
     * **阻塞 IO 跑在 [ioDispatcher] 上**（见构造参数 KDoc）。这里有一条不可修的语义缺口，
     * 如实写明：阻塞 IO **不可被协程取消打断** —— `withTimeoutOrNull` 到点只是让调用方
     * 先返回，工具线程会把 `writeText` / `delete` 做完。所以超时结果的
     * `indeterminate=true` 不是保守修辞，而是**唯一正确的描述**：副作用可能已经落地。
     * 真要能中断，得让工具体自己在 IO 之间查 `ensureActive()`，那是工具实现的事。
     */
    private suspend fun invoke(ctx: TurnContext, call: ToolCall, tool: Tool): ToolResult {
        val timeout = tool.spec.timeoutMillisOverride ?: ctx.policy.toolTimeoutMillis
        val startedAt = System.currentTimeMillis()
        val result = withTimeoutOrNull(timeout) {
            withContext(ioDispatcher) {
                try {
                    tool.invoke(call.argumentsJson)
                } catch (c: CancellationException) {
                    // 前置原样抛：吞掉它会让 Job 从 cancelled 变 completed（AGENTS.md §五.4）。
                    throw c
                } catch (t: Throwable) {
                    AgentLogStore.error("tool " + call.name + " threw: " + (t.message ?: t::class.simpleName))
                    failure(call, "tool failed: " + (t.message ?: t::class.simpleName ?: "unknown"))
                }
            }
        }
        val elapsed = System.currentTimeMillis() - startedAt
        if (result == null) {
            return ToolResult(
                callId = call.id,
                name = call.name,
                ok = false,
                errorMessage = "tool timed out after " + timeout + "ms",
                elapsedMillis = elapsed,
                indeterminate = true,
            )
        }
        return result.copy(
            callId = call.id,
            name = result.name.ifEmpty { call.name },
            elapsedMillis = if (result.elapsedMillis > 0) result.elapsedMillis else elapsed,
        )
    }

    /** 执行后记账。返回的熔断证据由调用方决定「终止还是继续」。 */
    private fun finish(guard: ToolExecutionGuard, call: ToolCall, result: ToolResult): Execution {
        val verdict = guard.afterCall(call.name, result.ok)
        val trip = (verdict as? ToolExecutionGuard.Verdict.BreakerTripped)?.evidence
        return Execution(result, trip)
    }

    private fun guardFor(ctx: TurnContext): ToolExecutionGuard =
        guard ?: ToolExecutionGuard(failureStreakLimit = ctx.policy.maxConsecutiveToolFailures)
            .also { guard = it }

    /** 拒绝结果。`errorMessage` 与 `ToolApprovalFlow` 的 reason 同源（ASCII 常量）。 */
    private fun denied(call: ToolCall, reason: String): ToolResult = ToolResult(
        callId = call.id,
        name = call.name,
        ok = false,
        errorMessage = reason,
    )

    /** 同参护栏结果：措辞可执行（告诉模型**怎么改**），否则它只会原样重发。 */
    private fun repeated(call: ToolCall, streak: Int): ToolResult = ToolResult(
        callId = call.id,
        name = call.name,
        ok = false,
        errorMessage = "repeated call ignored (" + streak + "x same arguments); " +
            "change the arguments or try another approach",
    )

    private fun failure(call: ToolCall, message: String): ToolResult = ToolResult(
        callId = call.id,
        name = call.name,
        ok = false,
        errorMessage = message,
    )

    /** 一条调用的执行产物：结果 + 可选的熔断证据。 */
    private data class Execution(val result: ToolResult, val trip: String?)

    private companion object {
        /** ASCII 常量（守卫 G16）；用户文案由 UI 按 reason 映射。 */
        const val UNKNOWN_TOOL = "unknown tool"
        const val ABORTED_BY_USER = "run aborted from the approval card"
    }
}
