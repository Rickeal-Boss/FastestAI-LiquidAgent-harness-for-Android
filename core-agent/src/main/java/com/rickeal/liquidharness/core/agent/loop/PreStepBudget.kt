package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.context.HardBudgetShrinker
import com.rickeal.liquidharness.core.agent.context.charCapOf
import com.rickeal.liquidharness.core.agent.context.effectiveCharsPerToken
import com.rickeal.liquidharness.core.agent.context.hardContextBudgetTokens
import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TokenEstimator

/**
 * 每一步生成之前的**硬预算**判定：原地收缩 + 熔断。
 *
 * ## 与 [ContextBudget.prepare] 的分工是「软 vs 硬」，不是「早 vs 晚」
 *
 * | 层 | 量纲 | 阈值 | 动作 | 缓存代价 |
 * |---|---|---|---|---|
 * | L1 软（`prepare`） | token 估算 | `hard × compressThreshold` = 20 928（32K 档） | 丢最老历史 | **破**（切点后移） |
 * | L2 硬（本类） | token 估算 | `hard` = 27 904 | 原地收缩最大的工具结果 | 不破 |
 * | L3 字符（本类，**权威**） | 字符，与估算器无关 | `hard × effectiveCpt`（无真值时 50 227） | 同上；压不下去 ⇒ 不发请求 | 不破 |
 *
 * **为什么必须有 L3**：L1/L2 都建立在同一个 `TokenEstimator` 上，而它对代码 / JSON 有
 * 约 2× 的低估（`w6-plan.md` §2.1 M1）。用有偏的尺子量自己等于没量 —— 所以最终判据
 * 换量纲。单测（T1/T2）断言的是**字符**与**占位真的发生**，不是 token：
 * 用 token 写那条断言，它会因为同一个低估而永远绿。
 *
 * ## L3 的 chars/token 是**可标定**的，不是常数 1.8
 *
 * 真机实测：英文散文 5.73～5.75 字符/token、代码 / JSON ≈2.0 ⇒ 偏差是**内容相关**的。
 * 固定 1.8 在散文会话里会把可用字符空间砍到 1/3（可感知的信息损失）。
 * 所以 `effectiveCpt = max(floor, min(ceiling, measured))`，`measured` 来自
 * `RunState.measuredCharsPerToken`（provider 回报的真值），压缩后置 0 回落 floor。
 * **首轮没有真值 ⇒ 1.8（安全侧）。**
 *
 * 对应地，L2 的第二刀（把 token 预算换算成字符目标）也必须跟着走：
 * **有真值时按真值换算（退化成 L3，不再额外收紧）；无真值时才按最坏密度 1 字符/token
 * 收紧（CJK 保底）**。不这么写，散文场景会被 L2 又砍回去，标定等于白做。
 *
 * **为什么动作必须是「收缩」而不是「丢消息」**：单轮 + 一大批工具的形态下，
 * 历史是 `[USER, MODEL(calls), TOOL(results)]`，压缩器的安全切点会一路回溯到 USER@0
 * ⇒ 保留区 = 全部 ⇒ 它只能无损放弃（`ContextBudget.kt` 的 `compress_skipped=not_compressible`
 * 就是那条路径的证据）。此时唯一还能做的事是改单条结果的正文，而不是删消息。
 *
 * ## 判定点在 `TurnLoop` 的 while 体顶部（而不是工具批之后）
 *
 * 工具批执行完，循环回到顶部 —— 所以顶部位置**天然覆盖**了「工具结果提交后」这个时点，
 * 同时还能覆盖**首轮**：一条 100 万字符的用户输入必须在发出第一个请求之前就被拦下
 * （判据 T3 断言 `generateCount == 0`，挂在工具批之后永远不可能满足）。
 *
 * ## 与 `policy.compressContext` 无关
 *
 * 关掉压缩（软预算）不该关掉兜底：这个开关的语义是「不要为了省 token 丢历史」，
 * 不是「允许发出必然 400 的请求」。所以本类**不读**那个开关。
 *
 * ## 已知上界：本类量的是 working，实发的是 sanitize 之后（**接受，不改结构**）
 *
 * 本类量 `state.working() + systemText`，而真正发出去的是 `ContextBudget.prepare` 里
 * `sanitizeForProvider(listOf(system) + body)` 的结果 —— 后者会给「声明了却没结果」的调用
 * **补一条合成错误结果**（模板 `MISSING_RESULT_MESSAGE`，约 110 字符/条）。所以：
 *
 * ```
 * 实发字符数 ≤ charCap + 未配对调用数 × 合成模板长度      // ≤ 12 并发 ⇒ 约 +1.3 KB
 * ```
 *
 * 这条偏差只在「压缩丢了宿主 / 调用被中断」时出现，且方向固定（只会多算不会少算）。
 * **不改结构**（不在 prepare 之后再加一次终检）：真正兜底靠 L2/L3 的**余量** ——
 * floor 1.8 对散文有约 3× 余量，1.3 KB 在 50 KB 量级上不构成风险。
 *
 * 🔴 **耦合**：一旦把 `policy.hardCharsPerToken` 调到接近 1.0（余量消失），
 * 上面这个不等式就不再可忽略，必须改成「prepare 之后对 `full` 再跑一次 `promptChars` 终检」。
 * 换句话说：**这个上界的安全性由 floor 的余量买来，不是由判据本身保证。**
 *
 * @param termination 熔断出口。用与 `TurnLoop` 相同的 [RunTermination]（默认值即新实例，
 *   它无状态），保证「终态事件只有一个出口」这条纪律不被本类破坏。
 */
class PreStepBudget(
    private val termination: RunTermination = RunTermination(),
) {

    /**
     * 判一次。返回 `true` = 已熔断（**已 emit 终态事件**），调用方必须立刻终止循环。
     *
     * 返回 `false` = 可以发请求（可能已经原地收缩过工作副本）。
     */
    suspend fun enforce(ctx: TurnContext): Boolean {
        val endpoint = ctx.request.endpoint
        val policy = ctx.policy
        val hard = hardContextBudgetTokens(endpoint, policy)
        val measured = ctx.state.measuredCharsPerToken
        val cpt = effectiveCharsPerToken(policy, measured)
        val charCap = charCapOf(hard, cpt)

        val body = ctx.state.working().filterNot { it.isEmpty }
        val systemChars = ctx.state.systemText?.length ?: 0
        val toolTokens = TokenEstimator.estimateToolSpecs(ctx.tools.specs())
        val chars = systemChars + HardBudgetShrinker.promptChars(body)
        val estimated = estimate(ctx, body) + toolTokens
        // 每轮一行：真机排障时「这一轮按什么量纲判了多少」是第一手数据。
        // 判据 T7 也读这一行（`cap=` 是否已被标定值抬高）。
        AgentLogStore.info(
            "hard_budget chars=" + chars + " cap=" + charCap + " cpt=" + cpt +
                " hard=" + hard + " estimated=" + estimated
        )
        if (chars <= charCap && estimated <= hard) return false

        // 第一刀：字符量纲（权威判据）。目标只给工作副本，system 不可收缩。
        var kept = body
        kept = shrink(ctx, body, (charCap - systemChars).coerceAtLeast(0), "chars", chars)
        if (systemChars + HardBudgetShrinker.promptChars(kept) > charCap) {
            return trip(ctx, systemChars + HardBudgetShrinker.promptChars(kept), charCap)
        }

        // 第二刀（token 维度）：把 token 预算换算成字符目标，只在**没有真值**时收紧。
        //   有真值 ⇒ 目标 = `hard × measured` = 与 charCap 相同 ⇒ 这一步不动作（L3 主导）；
        //   无真值 ⇒ 目标 = `hard × 1`（最坏密度：1 字符/token，CJK 保底）⇒ 收紧。
        // 不这么写，散文会话会被这一刀按 1 字符/token 砍回去，L3 的标定等于白做。
        if (estimate(ctx, kept) + toolTokens > hard) {
            val worstCaseCpt = if (measured > 0f) cpt else 1.0f
            val tokenTarget = charCapOf(hard, worstCaseCpt)
            kept = shrink(ctx, kept, (tokenTarget - systemChars).coerceAtLeast(0), "tokens", chars)
            if (systemChars + HardBudgetShrinker.promptChars(kept) > tokenTarget) {
                return trip(ctx, systemChars + HardBudgetShrinker.promptChars(kept), tokenTarget)
            }
        }
        return false
    }

    /**
     * 收缩一轮并写回工作副本。返回收缩后的 body（没变化时原样返回）。
     *
     * 写回用 [RunState.replaceWorking] —— 与压缩同一个入口，语义是「这是被允许的改写」，
     * 不是「谁都能改历史」。
     */
    private fun shrink(
        ctx: TurnContext,
        body: List<ChatMessage>,
        bodyCharCap: Int,
        dimension: String,
        charsBefore: Int,
    ): List<ChatMessage> {
        val result = HardBudgetShrinker.shrinkToCharCap(body, bodyCharCap)
        if (result.shrunk == 0) return body
        ctx.state.replaceWorking(result.messages)
        AgentLogStore.warn(
            "hard_budget_shrunk dimension=" + dimension + " results=" + result.shrunk +
                " body_chars=" + HardBudgetShrinker.promptChars(body) + " -> " + result.chars +
                " request_chars=" + charsBefore + " cap=" + bodyCharCap
        )
        return result.messages
    }

    /**
     * 压不下去 ⇒ **不发请求**。
     *
     * 比发一个注定 400 的请求便宜（一次往返 + 一次计费），且用户看得见
     * （`TerminationReason.BreakerTripped` + 诊断卡）。
     */
    private suspend fun trip(ctx: TurnContext, chars: Int, cap: Int): Boolean {
        val detail = "context_overflow_prevented: chars=" + chars + " cap=" + cap
        AgentLogStore.error(detail)
        termination.emitBreaker(ctx, detail)
        return true
    }

    /**
     * 工作副本的 token 估算（含 system 一条）。
     *
     * 刻意**不**复用 `ContextBudget` 的锚点估算：锚点回答的是「软预算够不够」，
     * 它会被 provider 的真值拉低；而硬预算要的是「最坏情况下这条请求多大」，
     * 用启发式原值更保守。两个判据的数字来源不同是**有意的**，不要合并。
     */
    private fun estimate(ctx: TurnContext, body: List<ChatMessage>): Int {
        val system = ChatMessage(
            id = "sys:" + ctx.state.runId,
            role = Role.SYSTEM,
            text = ctx.state.systemText.orEmpty(),
        )
        return TokenEstimator.estimateMessages(listOf(system) + body)
    }
}
