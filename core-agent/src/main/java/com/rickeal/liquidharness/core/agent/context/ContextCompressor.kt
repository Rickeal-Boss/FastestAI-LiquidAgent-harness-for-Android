package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TokenEstimator
import com.rickeal.liquidharness.core.model.ToolResult

/** 上下文压缩器端口。实现方只允许「无损放弃」，不允许「尽力而为删一半」（理由见下）。 */
interface ContextCompressor {
    suspend fun compress(messages: List<ChatMessage>, budgetTokens: Int): List<ChatMessage>
}

/**
 * 发送前的上下文预算。
 *
 * ## 算式（`00-brief.md` A8 理由 3）
 *
 * ```
 * window = min(endpoint.contextLength, policy.maxContextTokens)
 * budget = (window - endpoint.maxOutputTokens) * policy.compressThreshold
 * ```
 *
 * 三处都不能省：
 * - **`min(...)`**：没有它，1M 窗口的模型会真的按 0.75 发 75 万 token 出去 —— 一次几美元。
 *   `maxContextTokens` 是本应用愿意付的钱的上限，不是模型能力的上限。
 * - **减 `maxOutputTokens`**：输入 + 输出共享同一个窗口，不扣掉输出预留，
 *   真实请求会在输出阶段撞窗口。
 * - **乘 `compressThreshold`（0.75，旧仓值）**：留 25% 余量给「压缩判断之后又追加的
 *   工具结果」。压缩是缓存失效事件，要稀有（A8），门槛太松会变成每轮都压。
 *
 * 下限兜底：`window - maxOutputTokens` 可能 ≤ 0（用户把 maxOutputTokens 填得比窗口还大）。
 * 那时按窗口一半算 —— 宁可压缩得激进，也不要发出一个必然 400 的请求。
 */
fun contextBudgetTokens(endpoint: RemoteEndpoint, policy: AgentPolicy): Int {
    val window = minOf(endpoint.contextLength, policy.maxContextTokens).coerceAtLeast(1)
    val usable = (window - endpoint.maxOutputTokens).let { if (it > 0) it else window / 2 }
    return (usable * policy.compressThreshold).toInt().coerceAtLeast(1)
}

/**
 * 朴素滑动窗口压缩。
 *
 *  - 系统消息与「最近若干条」优先保留；
 *  - 从最新往回贪心装填，装不下就丢最老的；
 *  - **切点必须落在「工具调用原子组」的边界上**，找不到安全切点就放弃压缩。
 *
 * ## 为什么必须这么严（这段 KDoc 是踩过 400 换来的，逐字保留动机）
 *
 * OpenAI 兼容协议要求每条 `role=tool` 的结果都能对应到声明它的 `assistant.tool_calls`。
 * 只按 token 贪心切，会把「assistant(toolCalls) + 紧随其后的 toolResult」从中间截断，
 * 发出去就是 400。
 * 因此压缩失败必须**无损**（原样返回），绝不「尽力而为地删一半」。
 *
 * @param keepRecent 旧语义「保留最近 N 条」。现在切点完全由「预算贪心 + 安全切点调整」
 *   决定，该参数不再影响结果，仅作为构造参数保留，以免破坏既有调用方的具名参数写法。
 */
class WindowContextCompressor(
    keepRecent: Int = 8,
) : ContextCompressor {

    override suspend fun compress(messages: List<ChatMessage>, budgetTokens: Int): List<ChatMessage> {
        if (messages.isEmpty()) return messages
        if (TokenEstimator.estimateMessages(messages) <= budgetTokens) return messages

        val system = messages.filter { it.role == Role.SYSTEM }
        val rest = messages.filter { it.role != Role.SYSTEM }
        if (rest.isEmpty()) return messages

        // 1) 从最新往回贪心，求「不超预算的最长后缀」的起点下标。
        // 每条 cost 先算一次并存起来：在贪心循环里逐条 estimate 等于把整段历史全量字符
        // 遍历两遍（O(n²)），长会话时用户感知为「卡住」（旧仓实测）。
        val costs = IntArray(rest.size) { TokenEstimator.estimateMessages(listOf(rest[it])) }
        var used = TokenEstimator.estimateMessages(system)
        var candidate = rest.size
        for (index in rest.indices.reversed()) {
            val cost = costs[index]
            if (used + cost > budgetTokens) break
            used += cost
            candidate = index
        }
        // 连最近一条都装不下：没有任何可保留的东西，无损放弃
        if (candidate >= rest.size) return messages

        // 2) 把候选切点调整到工具组边界；调不到安全位置就无损放弃
        val safe = safeCutIndex(rest, candidate) ?: return messages
        val kept = system + rest.subList(safe, rest.size)
        AgentLogStore.info(
            "context compressed: " + messages.size + " -> " + kept.size +
                " messages (budget=" + budgetTokens + " tokens, dropped=" + safe + ")"
        )
        return kept
    }

    /**
     * 把候选切点 [candidate] 调整到安全的「工具组边界」，返回调整后的下标；无法保证安全时返回 null。
     *
     * 规则：
     *  - 落点是 toolResult（[Role.TOOL]）→ 按 `callId` 回溯到声明它的 MODEL(toolCalls)，
     *    让整组一起进保留区。
     *  - 落点是其它任何消息（USER / 普通 MODEL / SYSTEM）→ 天然安全，直接采用。
     *    这里**选择允许 USER 作为硬边界**：工具组的结构固定为「MODEL(toolCalls) 紧跟若干
     *    TOOL」，USER 不可能出现在组内部，截在它之前不会拆散配对，比一律回溯更省 token。
     *  - 回溯不到宿主，或回溯到下标 0 后首条仍是 TOOL → 返回 null，让调用方放弃压缩。
     */
    private fun safeCutIndex(messages: List<ChatMessage>, candidate: Int): Int? {
        var index = candidate
        while (true) {
            if (index <= 0) {
                // 保留区首条仍是 toolResult 说明这段历史缺宿主，满足不了不变量 → 放弃
                return if (messages.firstOrNull()?.role == Role.TOOL) null else 0
            }
            val message = messages[index]
            if (message.role != Role.TOOL) return index
            index = ownerIndex(messages, index) ?: return null
        }
    }

    /** 回溯查找声明了 `messages[at]` 那条 toolResult 的 MODEL(toolCalls) 下标。 */
    private fun ownerIndex(messages: List<ChatMessage>, at: Int): Int? {
        val callId = messages[at].toolResults.firstOrNull()?.callId
        if (callId.isNullOrEmpty()) return null
        for (index in at - 1 downTo 0) {
            if (messages[index].toolCalls.any { it.id == callId }) return index
        }
        return null
    }
}

/**
 * 发送前的最后一道清洗：保证 `MODEL(toolCalls)` 与 `TOOL(toolResult)` 严格成对，
 * 并把相邻同角色消息合并。
 *
 * ### 三件事，各有各的 400 来源
 *
 * 1. **孤儿 toolResult**（其 `callId` 找不到任何 MODEL 声明）→ 丢弃。
 *    OpenAI 兼容端点遇到「有 tool 消息但没有对应 tool_call」直接 400。
 * 2. **声明了却没有结果的 toolCall** → 补一条合成的错误结果。
 *    端点对「有 tool_call 却没结果」的容忍度更低，通常拒绝整个请求。
 * 3. **相邻同角色合并** → Anthropic 族要求 user / assistant 交替，连续两条同角色
 *    会被拒。旧仓只做 1+2，是因为只有 OpenAI 兼容一条路；新仓三族并存，必须补这一条。
 *
 * ### 补丁消息为什么用稳定派生 id
 *
 * 旧仓的理由是「本地引擎的增量水印（sentMessageIds）会把新 UUID 的补丁当成新消息逐轮重发」。
 * 新仓没有引擎水印，但**理由换了一个仍然成立**：每轮生成都发同一段历史，补丁 id 抖动
 * 会让前缀字节变化 ⇒ prompt cache 失效（`00-brief.md` A8）。所以稳定派生 id 必须保留。
 *
 * 空命中语义：已经成对且无相邻同角色 ⇒ **返回同一个对象**（调用方可据此判「没变化」）。
 */
fun sanitizeForProvider(messages: List<ChatMessage>): List<ChatMessage> {
    if (messages.isEmpty()) return messages

    // 先整体扫描：一次拿到「声明过的 id」与「已被回应的 id」，
    // 这样补合成结果时不会把「结果其实在更后面」的调用误判成缺失。
    val declared = HashSet<String>()
    for (message in messages) for (call in message.toolCalls) declared.add(call.id)
    val answered = HashSet<String>()
    for (message in messages) {
        if (message.role != Role.TOOL) continue
        for (result in message.toolResults) answered.add(result.callId)
    }

    var changed = false
    val paired = ArrayList<ChatMessage>(messages.size)
    for (message in messages) {
        if (message.role == Role.TOOL) {
            val kept = message.toolResults.filter { it.callId in declared }
            if (kept.isEmpty()) {
                changed = true
                continue                    // 整条都是孤儿结果 → 丢弃
            }
            if (kept.size != message.toolResults.size) {
                changed = true
                paired.add(message.copy(toolResults = kept))
            } else {
                paired.add(message)
            }
            continue
        }

        paired.add(message)
        val missing = message.toolCalls.filter { it.id !in answered }
        if (missing.isNotEmpty()) {
            changed = true
            val patch = missing.map { call ->
                ToolResult(
                    callId = call.id,
                    name = call.name,
                    ok = false,
                    output = "",
                    errorMessage = MISSING_RESULT_MESSAGE,
                )
            }
            paired.add(
                ChatMessage(
                    // 稳定派生 id：同一组缺失补丁在任何一轮都得到同一个 id（保 prompt cache）。
                    id = "patch:" + missing.joinToString("_") { it.id },
                    role = Role.TOOL,
                    toolResults = patch,
                )
            )
            AgentLogStore.warn("missing tool results patched: " + missing.joinToString { it.name })
        }
    }

    val merged = mergeAdjacentSameRole(paired)
    if (merged !== paired) changed = true
    return if (changed) merged else messages
}

/**
 * 合并相邻同角色消息（Anthropic 族的交替要求）。
 *
 * 三条保守限制，每条都有理由：
 * - 只合并 [Role.USER] 与 [Role.MODEL]；
 * - 带 `toolCalls` 的 MODEL 不参与合并 —— 合并会把它与下一条 MODEL 的调用声明揉在一起，
 *   破坏 `tool_call` ↔ `tool_result` 的配对关系；
 * - 带 `toolResults` 的 TOOL 一律不合并 —— OpenAI 兼容族要求一条 tool 消息对应一个
 *   `tool_call_id`，合并会让两个 id 挤进一条消息。
 *
 * 合并后的消息沿用**第一条**的 id：id 抖动同样会让前缀字节变化。
 */
private fun mergeAdjacentSameRole(messages: List<ChatMessage>): List<ChatMessage> {
    if (messages.size < 2) return messages
    val out = ArrayList<ChatMessage>(messages.size)
    var mergedAny = false
    for (message in messages) {
        val previous = out.lastOrNull()
        if (previous != null && canMerge(previous, message)) {
            out[out.size - 1] = previous.copy(text = previous.text + "\n\n" + message.text)
            mergedAny = true
        } else {
            out.add(message)
        }
    }
    return if (mergedAny) out else messages
}

private fun canMerge(previous: ChatMessage, next: ChatMessage): Boolean {
    if (previous.role != next.role) return false
    if (previous.role != Role.USER && previous.role != Role.MODEL) return false
    if (previous.toolCalls.isNotEmpty() || next.toolCalls.isNotEmpty()) return false
    if (previous.toolResults.isNotEmpty() || next.toolResults.isNotEmpty()) return false
    return previous.text.isNotEmpty() && next.text.isNotEmpty()
}

/** 补丁结果的错误文案。ASCII（守卫 N3），由 UI 决定是否展示。 */
private const val MISSING_RESULT_MESSAGE =
    "tool result missing (context was compressed or the call was interrupted); continue with what you have."
