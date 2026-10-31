package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.approval.ToolApprovalCache
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalDecision
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalHandler
import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.tool.ParamGatedTool
import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 审批闸门：判定**单个** [ToolCall] 要不要弹卡、弹卡后怎么收尾。
 *
 * ## 判定顺序（顺序本身是判据，别重排）
 *
 * 1. 静态判据 + 参数级闸门：`spec.dangerous || spec.requiresConfirmation`，
 *    或 [ParamGatedTool.requiresConfirmationFor]（`clipboard` 的 `get` 不弹 / `set` 弹 /
 *    坏 JSON 弹）。**不需要审批 ⇒ 直接放行**，连通道存在与否都不看 ——
 *    `calculator` 不该因为没有审批卡就不能算数。
 * 2. 需要审批但 `ctx.approval == null` ⇒ **一律拒绝**（fail-closed）。发 `ToolSkipped`。
 *    理由：没有通道就没人能批准，此时放行等于「安全闸门因未装配而失效」——
 *    这是最容易被误当成「W2 还没接线所以先跑通」的静默漏洞。
 *    范围**只到危险工具**：判据 #3 的原文是「`approvalHandler == null` + 危险工具 ⇒ 拒绝」，
 *    把它扩到全部工具会让无宿主的 run 连只读工具都调不动（实测被 3 条单测抓到）。
 * 3. 需要审批但 [ToolApprovalCache] 命中 ⇒ 放行、**不弹卡**（用户点过「本会话不再询问」）。
 * 4. 同一工具**被拒满 [MAX_DENIALS_BEFORE_SILENT] 次** ⇒ 后续直接拒、不弹卡。
 *    这不是缓存（[ToolApprovalCache] 明确「拒绝永不缓存」），而是**弹卡节流**：
 *    模型在死循环里重发同一个调用时，反复弹卡等于训练用户无脑点同意。
 * 5. 其余 ⇒ 发 `ApprovalRequested` 并等待裁决。
 *
 * ## 🔴 三个协程陷阱（外审逐条点名，改这段前先读完）
 *
 * ### 陷阱 1：审批超时**不能**升级成「取消整个 run」
 *
 * `withTimeout` 抛的 `TimeoutCancellationException` 是 `CancellationException` 的子类；
 * 而内核的取消语义是「整轮终止」（`TurnContext.checkActive` 用 `ensureActive()`，
 * `RunTermination.emitCancelled` 收尾）。用 `withTimeout` 包住 `requestApproval`，
 * 用户只是**没点卡**，run 却会被整轮杀掉 —— 一个「拒绝这一个工具」被误升级成「停止」。
 *
 * 修法：`withTimeoutOrNull(...)`，`null` ⇒ 转成 [ToolApprovalDecision.Deny]（只拒这一个）。
 * `catch (c: CancellationException) { throw c }` 只处理**真取消**（用户点停止 / Job 被取消），
 * 且必须前置 —— `withTimeoutOrNull` 已吃掉自己的超时异常，能漏到这里的一定是真取消。
 *
 * ### 陷阱 2：收集方退后台时 `send` 挂起 ⇒ 卡发不出去
 *
 * `AgentRunner` 的 emit 是 `channelFlow` 的 `send`（挂起语义）。用户切后台 ⇒ 收集停止 ⇒
 * `send` 挂起 ⇒ `ApprovalRequested` 永远发不出去，只能等满 [TurnContext.policy] 的
 * `approvalTimeoutMillis`（默认 5 分钟）。两处一起修：
 * ① `AgentRunner.run()` 的 Flow 追加 `.buffer(Channel.BUFFERED)`（吸收突发）；
 * ② 发 `ApprovalRequested` 这一步用 `approvalEmitTimeoutMillis`（5s）包住，
 * 投递失败 ⇒ 按拒绝处理 + `AgentLogStore.warn`。**绝不静默放行**。
 *
 * ### 陷阱 3：「拒绝」与「停止」在代码里必须走两条通道
 *
 * `Deny` 走**返回值**（本类的 [Decision.Denied]），由执行器产出
 * `ToolResult(ok=false, errorMessage="denied by user")`，循环继续 —— 模型还有机会换个办法。
 * 只有 `Aborted` 才向上抛取消（执行器里 `throw CancellationException`），由
 * `AgentRunner` 补发 `Cancelled(partialText)`。若把拒绝实现成异常，内核的
 * `catch (CancellationException)` 会把它当取消 ⇒ 用户看到「点了拒绝，回答直接没了」。
 *
 * ## 状态归属
 *
 * 两类状态，**作用域不同，别混在一起**：
 *
 * - **拒绝计数**（[denials]）是本实例字段，实例随 run 创建 ⇒ 生命周期 = 一次 run。
 *   这是对的：它是弹卡节流，跨 run 累积会让用户在新会话里莫名其妙不再被询问。
 * - **[ToolApprovalCache] 的生命周期由调用方决定**（`TurnContext.approvalCache`）：
 *   传 `null` ⇒ `TurnLoop` 建一个 run 级实例，授权只在本 run 内有效（单测 / 无宿主）；
 *   传会话级实例 ⇒ 授权跨 run 生效，这才对得上 UI 文案「本会话相同调用不再询问」。
 *   装配点在宿主（`AppContainer`），属接线的一步，不在本类里做。
 *
 * 🔴 **禁止把 cache 挂在进程级单例上**：那是「跨会话授权」，上一个对话里点过的
 * 「不再询问」会被下一个对话继承 —— 用户在新会话里第一次遇到写操作就不弹卡，
 * 而他从没为这个会话做过授权。
 */
class ToolApprovalFlow(
    private val cache: ToolApprovalCache = ToolApprovalCache(),
) {

    /** 一次调用的裁决。**用 sealed 而不是 Boolean**：四种结局的处理路径完全不同。 */
    sealed interface Decision {

        /** 放行，去执行。 */
        data object Allowed : Decision

        /** 拒绝这一次；调用方产出 `ok=false` 的 tool result，run 继续。 */
        data class Denied(val reason: String) : Decision

        /** 不执行且**不回 result**（已发 `ToolSkipped`）；调用方跳过该调用。 */
        data class Skipped(val reason: String) : Decision

        /** 用户停掉整个 run；调用方抛取消。 */
        data object Aborted : Decision
    }

    /** 同一工具被拒次数（弹卡节流用，见类头第 6 条）。 */
    private val denials = HashMap<String, Int>()

    /**
     * 判定一次调用。**可能 emit `ApprovalRequested` 或 `ToolSkipped`** ——
     * 这是本类唯一的副作用，两条事件都在此发出（调用方不要再发一遍，`ApprovalRequested`
     * 无去重，重复发就弹两次卡）。
     */
    suspend fun decide(ctx: TurnContext, call: ToolCall, tool: Tool): Decision {
        // ⚠️ 顺序不可换：**先判「要不要审批」，再判「有没有通道」**。
        // 反过来（先 `ctx.approval ?: skip`）会让「无通道」把**所有**工具都拒掉 ——
        // 包括 calculator / datetime 这类根本不需要授权的工具。真机上表现为
        // 「宿主没接审批卡 ⇒ 模型连算个数都不行」，而判据 #3 的原文只要求
        // 「`approvalHandler == null` + **危险工具** ⇒ 一律拒绝」。
        // 这条被三条单测抓到过（ToolCallExecutorTest 的 breaker / invalid-args /
        // paired-messages 全部拿到空结果列表），改回去会立刻复现。
        if (!needsApproval(call, tool)) return Decision.Allowed
        val handler = ctx.approval ?: return skip(ctx, call, NO_CHANNEL)
        val spec = tool.spec
        if (cache.isAllowed(call)) {
            AgentLogStore.info("approval satisfied by session cache: " + call.name)
            return Decision.Allowed
        }
        val deniedSoFar = denials[call.name] ?: 0
        if (deniedSoFar >= MAX_DENIALS_BEFORE_SILENT) {
            AgentLogStore.warn("approval card suppressed after " + deniedSoFar + " denials: " + call.name)
            return Decision.Denied(REPEATED_DENY)
        }
        if (!deliver(ctx, call, spec)) return Decision.Denied(EMIT_TIMEOUT)

        val decision = awaitDecision(ctx, call, tool, handler)
        return when (decision) {
            ToolApprovalDecision.AllowOnce -> Decision.Allowed

            ToolApprovalDecision.AllowForSession -> {
                cache.allowForSession(call)
                Decision.Allowed
            }

            ToolApprovalDecision.Deny -> {
                denials[call.name] = deniedSoFar + 1
                Decision.Denied(DENIED_BY_USER)
            }

            ToolApprovalDecision.Aborted -> Decision.Aborted
        }
    }

    /** 静态判据 + 参数级闸门。见类头第 2、3 条。 */
    private fun needsApproval(call: ToolCall, tool: Tool): Boolean {
        val spec = tool.spec
        if (spec.dangerous || spec.requiresConfirmation) return true
        // 参数级闸门必须 fail-closed：实现方约定解析失败返回 true（Tool.kt 的 KDoc）。
        return tool is ParamGatedTool && tool.requiresConfirmationFor(call.argumentsJson)
    }

    /**
     * 投递审批卡。**陷阱 2 的 ②**：投递超时 ⇒ false（调用方按拒绝处理）。
     *
     * 这里不用 `withTimeout`：超时不该变成异常，否则会被上层的取消分支接走。
     */
    private suspend fun deliver(ctx: TurnContext, call: ToolCall, spec: ToolSpec): Boolean {
        val sent = withTimeoutOrNull(ctx.policy.approvalEmitTimeoutMillis) {
            ctx.emit(AgentEvent.ApprovalRequested(call, spec))
            true
        }
        if (sent == null) {
            AgentLogStore.warn(
                "approval card could not be delivered within " +
                    ctx.policy.approvalEmitTimeoutMillis + "ms; denying " + call.name
            )
            return false
        }
        return true
    }

    /**
     * 等待用户裁决。**陷阱 1 的落点**：超时 ⇒ [ToolApprovalDecision.Deny]（返回值通道），
     * 真取消 ⇒ 原样抛出（交给 `AgentRunner` 收尾成 `Cancelled`）。
     */
    private suspend fun awaitDecision(
        ctx: TurnContext,
        call: ToolCall,
        tool: Tool,
        handler: ToolApprovalHandler,
    ): ToolApprovalDecision {
        val answer = try {
            withTimeoutOrNull(ctx.policy.approvalTimeoutMillis) {
                handler.requestApproval(call, tool.spec)
            }
        } catch (c: CancellationException) {
            // 前置且原样抛：只有真取消（用户点停止 / Job 取消）能走到这里，
            // 吞掉它会让 Job 从 cancelled 变 completed（AGENTS.md §五.4）。
            throw c
        }
        if (answer == null) {
            AgentLogStore.warn(
                "approval timed out after " + ctx.policy.approvalTimeoutMillis + "ms; denying " + call.name
            )
            return ToolApprovalDecision.Deny
        }
        return answer
    }

    /** 无审批通道：发 `ToolSkipped` 并跳过。见类头第 1 条。 */
    private suspend fun skip(ctx: TurnContext, call: ToolCall, reason: String): Decision {
        AgentLogStore.error("tool call refused without approval channel: " + call.name)
        ctx.emit(AgentEvent.ToolSkipped(call, reason))
        return Decision.Skipped(reason)
    }

    private companion object {
        /** 被拒满这么多次后不再弹卡（见类头第 6 条）。 */
        const val MAX_DENIALS_BEFORE_SILENT = 2

        /** ASCII 常量（守卫 G16）：用户文案由 `:app` / `feature-*` 按 reason 映射。 */
        const val NO_CHANNEL = "no approval channel"
        const val EMIT_TIMEOUT = "approval request could not be delivered"
        const val DENIED_BY_USER = "denied by user"
        const val REPEATED_DENY = "denied by user (repeated)"
    }
}
