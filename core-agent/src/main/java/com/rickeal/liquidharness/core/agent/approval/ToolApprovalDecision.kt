package com.rickeal.liquidharness.core.agent.approval

/**
 * 用户对一次工具调用的裁决。
 *
 * ## 为什么是 sealed 而不是 `Boolean` / `Exception`
 *
 * 三种「没执行」在语义上完全不同，混成一种就会静默出错：
 *
 * - [Deny]：**拒绝这一个工具**，run 继续（模型会收到一条 `ok=false` 的结果，
 *   有机会改用别的办法）。若走异常通道，内核的 `catch (CancellationException)`
 *   会把它当成取消 ⇒ 整轮终止，用户看到「点了拒绝，回答直接没了」。
 * - [Aborted]：**用户停掉整个 run**。只有它才向上传播取消，且必须保留 `partialText`。
 * - [AllowForSession]：放行，并把「同工具 + 同参数指纹」记进会话级缓存，
 *   后续同样调用不再弹卡（[ToolApprovalCache]）。
 *
 * 旧仓曾把「拒绝」实现成 `throw ToolDeniedException`，结果被 `runCatching` 吞掉后
 * 变成了「工具静默未执行但模型以为成功了」—— 这就是本类存在的理由。
 */
sealed interface ToolApprovalDecision {

    /** 允许这一次。 */
    data object AllowOnce : ToolApprovalDecision

    /** 允许，且本会话内相同调用不再询问（写入 [ToolApprovalCache]）。 */
    data object AllowForSession : ToolApprovalDecision

    /** 拒绝这一次工具；run 继续。 */
    data object Deny : ToolApprovalDecision

    /** 用户停止了整个 run；向上传播取消。 */
    data object Aborted : ToolApprovalDecision
}

/**
 * 审批通道端口。**每次 run 传入**，不进长生命周期对象。
 *
 * ## 为什么是 `run()` 的参数而不是构造参数（这是架构约束，不是风格偏好）
 *
 * `AgentRunner` 由 `AppContainer.agentRunnerFor(endpointId)` **按端点缓存**，
 * 是长生命周期对象。把 UI 回调塞进构造参数 = 让长生命周期对象持有 Activity，
 * 与 `ToolContext` 的「绝不持有 Activity」是同款泄漏面。
 *
 * 塞进 `AgentRequest` 也不行：它是 `@Serializable` 契约，KDoc 明写「会进日志与
 * journal」，回调进去既污染契约又制造新的泄漏面。
 *
 * 所以签名是 `AgentRunner.run(request, approval)`，每 run 传入、转进 `TurnContext`。
 * 与 runner 缓存天然兼容：缓存的是 runner，审批按 run 走。
 *
 * ## 实现纪律
 *
 * - **挂起而不是阻塞**：实现方（UI 层）必须 `suspend` 等待用户点击，不得 `Thread.sleep`
 *   或忙等 —— 主循环跑在协程里，阻塞会让整个 run 卡死且无法取消。
 * - **超时由调用方兜底**：`ToolApprovalFlow` 用 `withTimeoutOrNull` 包住本调用，
 *   超时按 [ToolApprovalDecision.Deny] 处理（只拒这一个工具，不终止 run）。
 *   实现方**不需要**自己实现超时。
 * - **取消语义**：协程被取消时抛 `CancellationException` 是**正确**的（用户点了停止），
 *   调用方会把它转成 `Aborted`。实现方不要 `runCatching` 吞掉它。
 */
fun interface ToolApprovalHandler {
    suspend fun requestApproval(
        call: com.rickeal.liquidharness.core.model.ToolCall,
        spec: com.rickeal.liquidharness.core.model.ToolSpec,
    ): ToolApprovalDecision
}
