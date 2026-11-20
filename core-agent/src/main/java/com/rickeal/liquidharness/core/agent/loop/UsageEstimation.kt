package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.TokenEstimator

/*
 * W39 外提：本函数原为 `GenerationRound` 的私有方法。外提的唯一理由是 G10 单文件
 * 400 行上限 —— W24-02 的「截断后整段回滚再重试」要在 `GenerationRound.prepareRetry`
 * 里落几行时序代码，而那个文件当时已 391/400。判据与逻辑**逐字未改**（含全部 KDoc）。
 */

/**
 * provider 未回报 usage 时的估算兜底。**在 [attemptOnce] 的 `finally` 里调用**，
 * 条件是「本次尝试确实把请求发出去了」—— 流正常结束、中途抛 IOException、被取消、
 * 首字节看门狗到点四条路径都会记账（取消与超时时请求已经发出去了，token 已经花了，
 * 不记就是漏账）。
 *
 * 只在「本轮**确实发了请求**（`promptTokens` 没有增长）」时记一次：
 * [RunState.recordEstimatedPrompt] 是**累加**，每轮都记会把同一条历史重复计进账本，
 * `maxRunPromptTokens` 门控会提前误触发 —— 那是「钱没花完但 run 被停」的假熔断。
 *
 * 每次尝试（含重试）各记一次是对的：每一次尝试都是一次真实请求。
 *
 * **残余不精确（如实记录，别当 bug 修）**：引擎先 emit 了 `Failed` 再抛异常 / 再断流的
 * 路径，旧代码一次都不记，现在会多记一次。方向是**宁可多记** —— budget 闸是 fail-safe
 * 设计（多记 ⇒ 提前熔断；少记 ⇒ 账单失控），这个偏差站在安全侧。
 *
 * @param attempt **内核**重试序号（[prepareRetry] 那条链，≤ [maxRetries]+1），写进日志只为
 *   让「内核侧重试链」可 grep。它**不是 HTTP 请求数**：`RemoteLlmEngine` 内部还有一层重试，
 *   一次内核 attempt 可能打出多次 HTTP（引擎侧每 attempt 一行日志由 `AttemptObserver`
 *   端口补，见 W3 账 2）。别拿本日志的条数反推请求数。
 */
internal fun recordEstimatedUsage(
    ctx: TurnContext,
    messages: List<ChatMessage>,
    promptBefore: Int,
    attempt: Int,
) {
    if (ctx.state.promptTokens != promptBefore) return
    val estimated = TokenEstimator.estimateMessages(messages)
    ctx.state.recordEstimatedPrompt(estimated)
    AgentLogStore.warn(
        "provider reported no usage; estimated prompt tokens: " + estimated +
            " (attempt=" + attempt + ")"
    )
}
