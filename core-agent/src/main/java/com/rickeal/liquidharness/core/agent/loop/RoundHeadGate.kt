package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.log.AgentLogStore

/**
 * 轮头闸门：每一轮生成**之前**的最后一道预算检查。
 *
 * ## 三条判据（每条都能指到出处）
 *
 * | 判据 | 阈值来源 | 触发后 |
 * |---|---|---|
 * | 墙钟 SOFT | `AgentPolicy.wallClockSoftMillis`（旧仓 180s） | 记 warn 证据 + 继续（不打断） |
 * | 墙钟 HARD | `AgentPolicy.wallClockHardMillis`（旧仓 300s → 600s） | 熔断 |
 * | token 硬上限 | `AgentPolicy.maxRunPromptTokens`（新仓新增） | 熔断 |
 *
 * ## 为什么 SOFT 只记证据不打断
 *
 * 旧仓 `gateRoundHead`（`AgentRunner.kt:1052-1172`）把 SOFT 当「提醒模型收尾」的信号，
 * 而不是熔断点。理由：长任务里 3 分钟很常见，熔断会误杀正常的长工具链；真正该杀的是
 * HARD。新仓保留这个分工，**并且把「SOFT 只提醒一次」写死**（`RunState.softWarned`）——
 * 旧仓每轮都记一条，日志里 8 条一样的 warn 等于没有 warn（`AGENTS.md` §七：
 * 告警的量级本身就是判据）。
 *
 * ## 为什么 token 上限是熔断而不是提醒
 *
 * API 路径下 token 直接等于钱，「提醒模型省着点」对它没有约束力（模型看不见账单）。
 * 唯一有效的止损是断掉。见 `03-contracts.md` §五 #10 对旧仓 W73-01 判死的改判说明。
 */
class RoundHeadGate {

    fun check(ctx: TurnContext): GateVerdict {
        val state = ctx.state
        val policy = ctx.policy
        val elapsed = state.elapsedMillis()

        if (elapsed >= policy.wallClockHardMillis) {
            val detail = "wall clock hard limit: elapsed=" + elapsed + "ms limit=" + policy.wallClockHardMillis + "ms"
            AgentLogStore.error(detail)
            return GateVerdict.Trip(detail)
        }

        if (state.promptTokens > policy.maxRunPromptTokens) {
            val detail = "prompt token budget exceeded: used=" + state.promptTokens + " limit=" + policy.maxRunPromptTokens
            AgentLogStore.error(detail)
            return GateVerdict.Trip(detail)
        }

        if (elapsed >= policy.wallClockSoftMillis && !state.softWarned) {
            state.softWarned = true
            // 只记一次：SOFT 是「可能该收尾了」的信号，不是错误。重复记会让诊断页被噪声填满。
            AgentLogStore.warn(
                "wall clock soft limit reached: elapsed=" + elapsed + "ms limit=" + policy.wallClockSoftMillis + "ms"
            )
        }

        return GateVerdict.Proceed
    }
}

/**
 * 轮头判定结果。**sealed 而不是 Boolean**：Trip 必须带「为什么 trip」的证据，
 * 否则诊断卡只能写「熔断了」，用户与开发者都不知道是哪条判据。
 */
sealed interface GateVerdict {

    /** 继续本轮。 */
    data object Proceed : GateVerdict

    /**
     * 熔断。`detail` 是**英文证据串**（进日志与诊断卡），不是用户文案 ——
     * 用户文案由 `:app` 按 `TerminationReason` 给（守卫 N3：`core-*` 零中文字面量）。
     */
    data class Trip(val detail: String) : GateVerdict
}
