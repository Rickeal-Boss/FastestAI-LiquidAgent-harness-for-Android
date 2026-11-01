package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.agent.tool.ToolResultLimiter
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolResult

/**
 * 工具结果的三件事：**单条截断**（发送前）、**聚合降级**（发送前）、**落工作副本**（发送后）。
 *
 * ## 为什么截断按 effect 分档而不是一刀切
 *
 * 读与写产出的东西性质不同（`ToolSpec.effect` 的 KDoc）：读是**工作材料**，模型要拿它继续
 * 干活 ⇒ 16000 字符；写是**回执**，几百字符足够 ⇒ 2000 字符。一刀切 4000（旧仓值）会让
 * 「读一个 40KB 的配置文件」被截成 1/10，模型只能反复分页读 —— 白烧 token 还更慢。
 * （分档之后还有 [com.rickeal.liquidharness.core.agent.policy.AgentPolicy.maxToolResultChars]
 * 这个与 effect 无关的硬上限，见下一节；两者取小才是实际生效值。）
 *
 * 分档的**真值源是调用时**：`clipboard get` 是读、`clipboard set` 是写，同一个工具两种效果。
 * 因此 `EffectAwareTool.effectFor` 优先于静态的 `spec.effect`（后者只是保守默认）。
 *
 * ## 两层限长，别合并成一处
 *
 * | 层 | 判据 | 落点 |
 * |---|---|---|
 * | 单条 | effect 上限 与 `maxToolResultChars` 取小 | [ToolResultLimiter] |
 * | 聚合 | 整批之和 ≤ `maxAggregatedToolMessageChars` | [boundAggregate] |
 *
 * 单条层管「一条结果不许独吞保留区」，聚合层管「一批结果不许独吞一条消息」。
 * 只有单条层，12 个并发调用仍能凑出 19 万字符的一条 TOOL 消息；只有聚合层，
 * 就得在「谁更旧」与「读还是写」之间猜一个，两个判据都会失准。
 *
 * ## 为什么落库顺序不能反
 *
 * OpenAI 兼容协议要求每条 `role=tool` 的结果都能对应到**前一条** assistant 的 `tool_calls`。
 * 先落 TOOL 再落 MODEL 会得到「有结果无声明」的孤儿消息 ⇒ 下一轮请求直接 400
 * （`sanitizeForProvider` 会把孤儿结果丢掉，于是模型永远看不到工具结果 —— 静默失效）。
 *
 * ⚠️ 已知边界：MODEL 消息的 `text` 留空。本轮模型若同时吐了正文与 tool_calls，那段正文
 * 不进历史（`RunState` 的文本缓冲是 run 级、不按轮切分）。这是 W2 的既有限制，
 * 修它要给 `RunState` 加「本轮文本区间」，属 W3 的账本活。
 */
class ToolResultCommit {

    private val limiter = ToolResultLimiter()

    /**
     * 单条限长（effect 上限与 `maxToolResultChars` 取小）。**超限才 copy**
     * （不超限返回原对象，便于调用方判「没变化」）。
     *
     * 判据全部在 [ToolResultLimiter]（含豁免名单与截断标记），这里只是出口别名 ——
     * 调用点（`ToolCallExecutor`）读起来是「落库前先截断」，与聚合降级是两件事。
     */
    fun truncate(call: ToolCall, result: ToolResult, tool: Tool, policy: AgentPolicy): ToolResult =
        limiter.clamp(call, result, tool, policy)

    /**
     * 把本轮的工具往返写进工作副本：**先 MODEL(toolCalls)，后 TOOL(toolResults)**。
     *
     * `results` 为空（整批都被跳过）时仍落 MODEL 消息：模型确实宣告过这些调用，
     * 历史里没有它，下一轮模型会以为自己没调过工具而重复宣告。缺失的结果由
     * `sanitizeForProvider` 在发送前补成合成错误结果（稳定派生 id，不抖前缀）。
     */
    suspend fun commit(ctx: TurnContext, calls: List<ToolCall>, results: List<ToolResult>) {
        if (calls.isEmpty()) return
        val model = ChatMessage(role = Role.MODEL, toolCalls = calls)
        ctx.state.appendMessage(model)
        ctx.emit(AgentEvent.MessageCommitted(model))
        if (results.isEmpty()) return
        val bounded = boundAggregate(results, ctx.policy.maxAggregatedToolMessageChars)
        val tool = ChatMessage(role = Role.TOOL, toolResults = bounded)
        ctx.state.appendMessage(tool)
        ctx.emit(AgentEvent.MessageCommitted(tool))
    }

    /**
     * 聚合上限：整批 `output` 之和超过 [limit] 时，**从最旧（列表头部）开始**换成占位。
     *
     * ## 为什么从最旧开始省
     *
     * 模型继续干活依据的是**最近**那批结果（它刚据此决定下一步）。先砍最近的等于让它
     * 「刚看到的证据消失」，只会触发原样重试；砍最旧的代价是「上下文里的旧细节丢了」，
     * 那正是压缩本来就打算付出的代价。
     *
     * ## 四条不可省的不变量
     *
     * 1. **占位必须保留 `callId`**（这里用 `copy` 天然保住）：丢掉它，下一轮
     *    `sanitizeForProvider` 会把这个调用判成「声明了没结果」，补一条合成错误结果 ——
     *    模型看到的是「工具失败了」，而真相是「结果被省略了」，两件事完全不同。
     * 2. **最后一条永远完整**（循环上界 `size - 1`）：全占位等于这一轮什么都没回，
     *    模型只能重调一遍，白花钱。
     * 3. **单条占位本身也可能仍超限**：极端情况（最后一条自己就超 [limit]）下不继续砍，
     *    因为再砍下去就没有任何可用信息了 —— 单条层（`ToolResultLimiter`）已经先兜过。
     * 4. **占位措辞不得像失败**（`omitted` 而不是 `error` / `failed`，并带上被省略的字符数）：
     *    模型看到像错误的文本会重试同一次调用，把一次省钱的截断变成一次多花的往返。
     *    这条由 `omittedPlaceholderNeverLooksLikeFailure` 钉住。
     */
    private fun boundAggregate(results: List<ToolResult>, limit: Int): List<ToolResult> {
        var total = 0
        for (result in results) total += result.output.length
        if (total <= limit) return results
        val before = total
        val bounded = ArrayList(results)
        for (index in 0 until bounded.size - 1) {
            if (total <= limit) break
            val old = bounded[index]
            val placeholder = placeholderOf(old.output.length)
            total += placeholder.length - old.output.length
            bounded[index] = old.copy(output = placeholder, truncated = true)
        }
        AgentLogStore.warn(
            "aggregated tool message capped: " + before + " -> " + total + " chars, limit " + limit
        )
        return bounded
    }

    /** 占位文本（ASCII，守卫 G16）：说明「被省略」与「省了多少」，绝不用错误类措辞。 */
    private fun placeholderOf(chars: Int): String = "[omitted: aggregated limit, " + chars + " chars]"
}
