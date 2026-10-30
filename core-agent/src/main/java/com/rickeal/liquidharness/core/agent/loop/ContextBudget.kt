package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.context.ContextCompressor
import com.rickeal.liquidharness.core.agent.context.WindowContextCompressor
import com.rickeal.liquidharness.core.agent.context.contextBudgetTokens
import com.rickeal.liquidharness.core.agent.context.sanitizeForProvider
import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TokenEstimator

/**
 * 发送前的上下文预算与压缩。**每轮生成前唯一允许改写工作副本的地方。**
 *
 * ## 存在理由：外审点名的一处系统性低估
 *
 * `contextBudgetTokens(endpoint, policy)` 只被喂 `working`，而 `system` 是每轮**临时**拼在
 * 最前的（不在工作副本里）。于是预算判断永远少算一整段 system —— 技能元数据 + 工具清单 +
 * 停止条件在 FULL 披露档位下可以到几千 token。低估的后果不是「少压缩一点」，
 * 而是**发出的请求真的超窗口**：输入 + 输出共享同一个窗口，system 又恰恰是最不可压缩的
 * 那一段（压它 = prompt cache 全失效，且模型丢失行为约束）。
 *
 * 所以 [prepare] 的估算是**全量**（`system + working`），压缩只动 `working`。
 *
 * ## 不变量：system 段压缩前后**逐字节相同**
 *
 * 压缩是「丢最老的历史」，不是「改写前缀」。`WindowContextCompressor` 把 SYSTEM 单独摘出来
 * 再原样拼回，本类也只把非 SYSTEM 部分写回 `RunState`。这条不变量有单测钉住
 * （SHA-256 比对）—— 一旦有人把 system 也喂进压缩器做摘要，prompt cache 会整段失效，
 * 而账单上只表现为「贵了」，不表现为「错了」，属于最难发现的回归。
 *
 * ## 压缩失败必须无损
 *
 * 压缩器返回原列表（装不下 / 找不到工具组安全切点）时**不改写历史**：宁可发一个超预算的
 * 请求（端点会报错，可见），也不要把 `assistant.tool_calls` 与 `tool` 结果拆散
 * （端点 400，且现场已经被破坏）。
 */
class ContextBudget(
    private val compressor: ContextCompressor = WindowContextCompressor(),
) {

    /**
     * 组装本轮要发的消息：`system` 在最前（id 稳定派生），其后是清洗过的工作副本。
     *
     * 顺序：**先 sanitize，再判预算**。反过来的话，估算的是「未补丁」的历史 ——
     * 缺结果的 tool_call 会由 `sanitizeForProvider` 补上一条合成结果，那段字节也要算钱。
     *
     * 超预算才压缩；压缩后**再 sanitize 一次**（压缩可能丢掉某条结果的宿主声明）。
     */
    suspend fun prepare(ctx: TurnContext, systemText: String): List<ChatMessage> {
        val system = systemMessage(ctx, systemText)
        val body = ctx.state.working().filterNot { it.isEmpty }
        val full = sanitizeForProvider(listOf(system) + body)
        if (!ctx.policy.compressContext) return full

        val budget = contextBudgetTokens(ctx.request.endpoint, ctx.policy)
        val estimated = TokenEstimator.estimateMessages(full)
        if (estimated <= budget) return full

        val compressed = compressor.compress(listOf(system) + body, budget)
        val keptBody = compressed.filter { it.role != Role.SYSTEM }
        if (keptBody.size >= body.size) {
            // 无损放弃：压缩器没能安全丢掉任何一条 ⇒ 不改写历史（见类头最后一段）。
            AgentLogStore.warn("context over budget but not compressible: " + estimated + " > " + budget)
            return full
        }
        ctx.state.replaceWorking(keptBody)
        AgentLogStore.info(
            "context compressed before round " + ctx.state.round + ": " + body.size + " -> " +
                keptBody.size + " messages (estimated " + estimated + " > budget " + budget + ")"
        )
        return sanitizeForProvider(listOf(system) + keptBody)
    }

    /**
     * system 消息：**id 用 `sys:<runId>` 稳定派生**，每轮复用同一个 id。
     *
     * 不是性能优化：id 抖动会让这段前缀的字节变化 ⇒ prompt cache 失效
     * （`00-brief.md` A8）。同一个 run 内 system 文本本身也必须字节稳定，
     * 由 `TurnLoop.systemTextFor` 的缓存保证。
     */
    private fun systemMessage(ctx: TurnContext, systemText: String): ChatMessage =
        ChatMessage(id = "sys:" + ctx.state.runId, role = Role.SYSTEM, text = systemText)
}
