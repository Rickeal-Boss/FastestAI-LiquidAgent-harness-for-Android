package com.rickeal.liquidharness.core.agent.tool

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolResult

/**
 * 工具结果的**出口限长**：结果进回灌消息之前的最后一道体积闸。
 *
 * ## 为什么必须在这里，而不是在压缩器里
 *
 * 压缩器看到的是已经成型的一坨消息。一条 16 000 字符的读结果进了历史之后，它只能选择
 * 「丢掉（模型白读了）」或「留着（保留区被吃光）」—— 两个都是坏答案。在出口限长，
 * 压缩器面对的就是合规输入。病根是**单条结果的体积**，治理点必须在体积产生的那一端。
 *
 * ## 两层，各管各的（别合并成一处）
 *
 * 1. **单条**（本类）：`min(effect 上限, [AgentPolicy.maxToolResultChars])`，超了就截；
 * 2. **聚合**（`ToolResultCommit.commit`）：整批之和仍超 [AgentPolicy.maxAggregatedToolMessageChars]
 *    时从最旧开始换占位。
 *
 * 合并成一处就必然要猜「先截谁」：单条层拿不到「谁更旧」，聚合层拿不到 effect 语义
 * （读是材料、写是回执）。两层各持一半判据才都成立。
 *
 * ## 豁免
 *
 * [AgentPolicy.toolResultCapExemptTools] 里的工具只受 effect 上限约束。加进去就放弃
 * 了本工具的压缩收益 —— 那是**配置**该承担的代价，不是本类该偷偷放宽的判断。
 */
class ToolResultLimiter {

    /**
     * 按 [policy] 截断 [result]。**超限才 copy**（不超限返回原对象，便于调用方判「没变化」）。
     *
     * 截断后 `truncated = true` 是给模型与 UI 的证据：模型据此知道「后面还有」，
     * 可以换 offset 续读，而不是以为自己看到了全文（W2 判据 #9）。
     */
    fun clamp(call: ToolCall, result: ToolResult, tool: Tool, policy: AgentPolicy): ToolResult {
        val effectLimit = if (effectOf(call, tool) == ToolEffect.READ) {
            policy.maxReadToolOutputChars
        } else {
            policy.maxWriteToolOutputChars
        }
        val limit = if (tool.spec.name in policy.toolResultCapExemptTools) {
            effectLimit
        } else {
            minOf(effectLimit, policy.maxToolResultChars)
        }
        if (result.output.length <= limit) return result
        AgentLogStore.info(
            "tool output truncated: " + call.name + " " + result.output.length + " -> " + limit + " chars"
        )
        return result.copy(output = cutWithMark(result.output, limit), truncated = true)
    }

    /** 本次调用的效果：参数级声明优先，静态字段兜底（`EffectAwareTool` 的 KDoc）。 */
    private fun effectOf(call: ToolCall, tool: Tool): ToolEffect =
        if (tool is EffectAwareTool) tool.effectFor(call.argumentsJson) else tool.spec.effect

    /**
     * 截到 [limit] 以内并附上省略量标记。
     *
     * 标记本身也占长度，所以正文先让出 [MARKER_RESERVE_CHARS]：预留量取「标记最坏情况」
     * （五位数的省略量 + 前缀后缀）而不是现算现让 —— 让位数变化去影响切点，会让
     * 「截断后的长度」变成一个难以断言的移动目标。
     */
    private fun cutWithMark(text: String, limit: Int): String {
        val keep = (limit - MARKER_RESERVE_CHARS).coerceAtLeast(0)
        val body = safeCut(text, keep)
        return body + markOf(text.length - body.length)
    }

    private fun markOf(omitted: Int): String = "\n[truncated: " + omitted + " chars omitted]"

    /**
     * 按字符上限截断，且**不切断代理对**：切点落在高位代理上时退一格，
     * 否则会产出半个 emoji（JSON 编码后是非法 UTF-16 转义，部分端点直接 400）。
     */
    private fun safeCut(text: String, limit: Int): String {
        val cut = text.substring(0, limit)
        return if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
    }

    private companion object {
        /** 标记的最坏宽度：`\n[truncated: 99999 chars omitted]` = 33，留到 48 只为整数好看。 */
        const val MARKER_RESERVE_CHARS = 48
    }
}
