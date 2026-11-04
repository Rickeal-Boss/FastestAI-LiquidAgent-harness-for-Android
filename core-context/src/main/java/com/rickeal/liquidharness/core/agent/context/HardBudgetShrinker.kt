package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role

/**
 * 硬预算兜底动作：**原地收缩**工具结果正文，不删消息、不动 `callId`。
 *
 * ## 为什么需要它（W5-01 事故里唯一有效的自救手段）
 *
 * 现役压缩器的动作是「丢最老的若干条消息」。在「单轮 + 一大批工具」的形态下
 * （历史 = `[USER, MODEL(calls), TOOL(results)]`），安全切点会一路回溯到 USER@0
 * ⇒ 保留区 = 全部 ⇒ 压缩器**无损放弃**，而请求已经超窗。本类换了一个维度：
 * 消息一条不删，只把单条工具结果的正文换成占位。
 *
 * ## 四条不变量（与 `ToolResultCommit.boundAggregate` 同源，逐条都有 400 的出处）
 *
 * 1. **只改 `ToolResult.output`，不删消息、不动 `callId`** ⇒ 不会让
 *    `sanitizeForProvider` 把「被省略」补成「工具失败」（那会让模型重试同一次调用）。
 * 2. **从最旧开始砍**：模型继续干活依据的是最近那批结果。
 * 3. **最新的一条保持完整**（见 [shrinkToCharCap] 的 KDoc）。
 * 4. **占位措辞绝不像失败**：`[omitted: hard budget, N chars]`，不含 error / failed。
 *
 * 本类只认**字符**（token 估算有约 2× 的内容相关偏差，见 `BudgetFormula.kt`）。
 */
object HardBudgetShrinker {

    /**
     * 收缩结果。
     *
     * @param messages 收缩后的列表（`shrunk == 0` 时**是同一个对象**，调用方可据此判「没变化」）。
     * @param shrunk 被换成占位的条数。**这是「动作真的发生了」的唯一判据** —— 只断言
     *   「字符没超」无法区分「没超」与「没检查」。
     * @param chars 收缩后的字符总量（含文本 / 工具声明 / 结果正文）。
     */
    data class Result(
        val messages: List<ChatMessage>,
        val shrunk: Int,
        val chars: Int,
    )

    /**
     * 把 [messages] 的字符总量压到 [charCap] 以内，只动工具结果正文。
     *
     * ## 为什么「保持完整」的是最后一条**工具结果**，不是最后一条**消息**
     *
     * `boundAggregate` 的循环上界是 `size - 1`（消息内最后一个结果）。照搬成「跳过整个最后
     * 一条消息」会让本类在**最主要的事故形态**下完全失效：单轮 + 一批工具时，工作副本的
     * 最后一条**就是**那条 TOOL 消息 ⇒ 一条都不砍 ⇒ 兜底变成「永远熔断」。
     *
     * @param charCap 整个请求（含 system）的字符上限里，留给**工作副本**的那一份。
     *   `<= 0` 表示 system 已经吃满预算 ⇒ 全部工具结果都换成占位。
     */
    fun shrinkToCharCap(messages: List<ChatMessage>, charCap: Int): Result {
        val before = promptChars(messages)
        if (before <= charCap) return Result(messages, 0, before)

        val slots = shrinkableSlots(messages)
        // 只剩一条（或零条）可收缩结果：它就是「最近那批证据」，砍它等于这一轮什么都没回。
        if (slots.size <= 1) return Result(messages, 0, before)

        var total = before
        var shrunk = 0
        var out = messages
        for (index in 0 until slots.size - 1) {
            if (total <= charCap) break
            val slot = slots[index]
            val message = out[slot.message]
            val result = message.toolResults[slot.result]
            val placeholder = placeholderOf(result.output.length)
            // 已经比占位还短（含「上一轮已被占位过」）⇒ 跳过，绝不把占位再包一层。
            if (placeholder.length >= result.output.length) continue
            // 已经是占位（`[omitted: aggregated limit, ...]` 之类）⇒ **不许再包一层**。
            // 再包一次会把「省了 15000 字符」这个信息抹成「省了 40 字符」，
            // 模型据此无法判断该不该重新读一遍文件 —— 静默降级比不收缩更糟。
            if (result.output.startsWith(OMITTED_PREFIX)) continue
            total += placeholder.length - result.output.length
            out = replaceResult(out, slot, placeholder)
            shrunk += 1
        }
        return Result(out, shrunk, total)
    }

    /**
     * 请求里 `messages` 的字符总量。**量纲是字符，不是 token**。
     *
     * 覆盖范围与 `TokenEstimator.estimateMessages` 一致（文本 / 工具名 / 参数 / 结果正文），
     * 否则「字符达标」与「真的发出去的字节」会脱节。`output` 为空时线上发的是
     * `errorMessage`，所以两者取长。
     */
    fun promptChars(messages: List<ChatMessage>): Int {
        var total = 0
        for (message in messages) {
            total += message.text.length
            for (call in message.toolCalls) {
                total += call.name.length + call.argumentsJson.length
            }
            for (result in message.toolResults) {
                total += if (result.output.isNotEmpty()) {
                    result.output.length
                } else {
                    result.errorMessage.orEmpty().length
                }
            }
        }
        return total
    }

    /** 可收缩位置，**最旧优先**。只有 TOOL 消息里的结果能换占位。 */
    private fun shrinkableSlots(messages: List<ChatMessage>): List<Slot> {
        val slots = ArrayList<Slot>()
        for (index in messages.indices) {
            val message = messages[index]
            if (message.role != Role.TOOL) continue
            for (result in message.toolResults.indices) slots.add(Slot(index, result))
        }
        return slots
    }

    private fun replaceResult(messages: List<ChatMessage>, slot: Slot, placeholder: String): List<ChatMessage> {
        val out = ArrayList(messages)
        val message = out[slot.message]
        val results = ArrayList(message.toolResults)
        results[slot.result] = results[slot.result].copy(output = placeholder, truncated = true)
        out[slot.message] = message.copy(toolResults = results)
        return out
    }

    /** 占位文本（ASCII，守卫 G16）：说明「被省略」与「省了多少」，绝不用错误类措辞。 */
    private fun placeholderOf(chars: Int): String = OMITTED_PREFIX + chars + " chars]"

    /**
     * 占位前缀。与 `ToolResultCommit` 的 `[omitted: aggregated limit, ` 同族 ——
     * 两者都必须以 `[omitted:` 开头，判据（T2）与「不许二次包裹」的守卫都靠它。
     */
    private const val OMITTED_PREFIX = "[omitted: hard budget, "

    /** 位置对。私有：外部只该看 [Result]。 */
    private data class Slot(val message: Int, val result: Int)
}
