package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.EffectAwareTool
import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolResult

/**
 * 工具结果的两件事：**截断**（发送前）与**落工作副本**（发送后）。
 *
 * ## 为什么截断按 effect 分档而不是一刀切
 *
 * 读与写产出的东西性质不同（`ToolSpec.effect` 的 KDoc）：读是**工作材料**，模型要拿它继续
 * 干活 ⇒ 16000 字符；写是**回执**，几百字符足够 ⇒ 2000 字符。一刀切 4000（旧仓值）会让
 * 「读一个 40KB 的配置文件」被截成 1/10，模型只能反复分页读 —— 白烧 token 还更慢。
 *
 * 分档的**真值源是调用时**：`clipboard get` 是读、`clipboard set` 是写，同一个工具两种效果。
 * 因此 [EffectAwareTool.effectFor] 优先于静态的 `spec.effect`（后者只是保守默认）。
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

    /**
     * 按 effect 截断 [result]。**超限才 copy**（不超限返回原对象，便于调用方判「没变化」）。
     *
     * 截断后 `truncated = true` 是给模型与 UI 的证据：模型据此知道「后面还有」，
     * 可以换 offset 续读，而不是以为自己看到了全文（W2 判据 #9）。
     */
    fun truncate(call: ToolCall, result: ToolResult, tool: Tool, policy: AgentPolicy): ToolResult {
        val effect = effectOf(call, tool)
        val limit = if (effect == ToolEffect.READ) {
            policy.maxReadToolOutputChars
        } else {
            policy.maxWriteToolOutputChars
        }
        if (result.output.length <= limit) return result
        AgentLogStore.info(
            "tool output truncated: " + call.name + " " + result.output.length + " -> " + limit + " chars"
        )
        return result.copy(output = safeCut(result.output, limit), truncated = true)
    }

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
        val tool = ChatMessage(role = Role.TOOL, toolResults = results)
        ctx.state.appendMessage(tool)
        ctx.emit(AgentEvent.MessageCommitted(tool))
    }

    /** 本次调用的效果：参数级声明优先，静态字段兜底（见类头第二段）。 */
    private fun effectOf(call: ToolCall, tool: Tool): ToolEffect =
        if (tool is EffectAwareTool) tool.effectFor(call.argumentsJson) else tool.spec.effect

    /**
     * 按字符上限截断，且**不切断代理对**：切点落在高位代理上时退一格，
     * 否则会产出半个 emoji（JSON 编码后是非法 UTF-16 转义，部分端点直接 400）。
     */
    private fun safeCut(text: String, limit: Int): String {
        val cut = text.substring(0, limit)
        return if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
    }
}
