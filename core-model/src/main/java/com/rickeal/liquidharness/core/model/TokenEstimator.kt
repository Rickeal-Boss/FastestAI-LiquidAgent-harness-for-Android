package com.rickeal.liquidharness.core.model

import kotlin.math.ceil

/**
 * 无网络条件下的 token 估算。
 *
 * **它只在两个地方被允许当权威值**：
 * 1. 发送前的预算判断（必须先判断才能决定要不要压缩 —— 那时还没有 provider 回报）；
 * 2. provider 未回报 usage 时的兜底（结果标 `estimated = true`）。
 *
 * 其余场合一律以 provider 回报为准：旧仓 KDoc 自陈「折中的方向是系统性低估中文 2~3 倍」，
 * 而 API 路径下 token 直接等于钱（00-brief A5 的 `RunTokenLedger` 改判依据）。
 *
 * 启发式口径：
 * - CJK 字符（汉字 / 假名 / 全角标点）≈ 1 token/字；
 * - 其余字符 ≈ 4 字符/token（英文 + 代码的平均值，代码比英文更省）；
 * - 每条消息固定 +4 token 的协议开销（role / 分隔符），照抄 OpenAI 的公开口径。
 */
object TokenEstimator {

    /**
     * 英文/代码的字符-token 比（**估算面**唯一真值源）。改这个数字前先看
     * `RunTokenLedger` 的校准 warn。
     *
     * ## 量纲同源契约（W10/S1：四面统一，禁两处各写一份比例）
     *
     * 「字符↔token」在全仓只有**两把尺**，分工写死、互为上下界：
     *
     * - **估算面（本常量 4.0）**：近似真实值，用于发送前预算与 usage 兜底。
     *   散文实测 ≈5.7、代码 ≈2.0，4.0 是有意取的中间偏保守值。
     * - **硬闸面（`AgentPolicy.hardCharsPerToken`，floor 1.8）**：**下界**保守值，
     *   用于 L3 字符硬预算 —— 无论对端 tokenizer 多激进都不许超窗。
     *
     * 不变量（`BudgetFormulaTest` 钉死）：`hardCharsPerToken ≤ CHARS_PER_TOKEN_ASCII`，
     * 即硬闸换算出的字符上限换算回 token **恒不超过**硬预算本身（硬闸恒保守）。
     * 任何第三处出现独立比例 = 违反本契约（W9-01 量纲统一）。
     */
    const val CHARS_PER_TOKEN_ASCII = 4.0

    /**
     * 每张可发送图片的估算 token（经验值，W10/S4 缺口1a）。
     *
     * 取 1024：主流供应商视觉 token 计费口径（1568px 内）的经验上界；
     * 用途是**预算面**（`estimateMessages`），让「带图会话」更早触发压缩判定，
     * 方向是保守的。⚠️ 红线的另一半：图片只进预算，**不进** `PromptPrefix` 指纹
     * （`renderHistoryHead` 保持不渲染附件字节，否则 cached_tokens 分母漂移）。
     */
    const val ESTIMATED_IMAGE_TOKENS = 1024

    /** 每条消息的协议开销。 */
    private const val PER_MESSAGE_OVERHEAD = 4

    /**
     * 每个工具声明的协议开销。
     *
     * 与 [PER_MESSAGE_OVERHEAD] 同值不是巧合：两者都是「一层 JSON 包裹 + 一个名字」的
     * 固定代价。分开命名是因为它们的出处不同（消息开销抄 OpenAI 公开口径，工具开销是
     * 按 `body.tools[i]` 的 `type/function/name` 三层包裹估的）。
     */
    private const val PER_TOOL_OVERHEAD = 4

    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isCjk(cp)) cjk++ else other++
            i += Character.charCount(cp)
        }
        val asciiTokens = ceil(other / CHARS_PER_TOKEN_ASCII).toInt()
        return (cjk + asciiTokens).coerceAtLeast(1)
    }

    /**
     * 估算一组消息的总 prompt token。
     *
     * 工具调用的 `argumentsJson` 与工具结果文本都计入 —— 它们同样是发出去的字节。
     *
     * 附件（W10/S4）：`sendableAttachments` 同样计入 —— 图片按 [ESTIMATED_IMAGE_TOKENS]
     * 经验值、文本附件按 [estimate] 同尺；不可发送的附件（`isSendable=false`，如音频）
     * 与 MODEL 消息的附件（编程错误，引擎整段丢弃）不计。只进预算，不进指纹（红线）。
     */
    fun estimateMessages(messages: List<ChatMessage>): Int {
        var total = 0
        for (message in messages) {
            total += PER_MESSAGE_OVERHEAD
            total += estimate(message.text)
            for (call in message.toolCalls) {
                total += estimate(call.name)
                total += estimate(call.argumentsJson)
            }
            for (result in message.toolResults) {
                total += estimate(result.output)
            }
            for (attachment in message.sendableAttachments) {
                total += when (attachment.kind) {
                    AttachmentKind.TEXT -> estimate(attachment.textContent)
                    else -> ESTIMATED_IMAGE_TOKENS
                }
            }
        }
        return total
    }

    /**
     * 估算工具声明的 token。**它们同样是发出去的字节**，且不在 `messages` 里
     * （OpenAI 兼容族放在 `body.tools` 顶层），所以 [estimateMessages] 永远算不到它们。
     *
     * ## 为什么必须补这一条（W2.5 §4.2 的 M2，W3 未交付）
     *
     * 现役 6 个内置工具约 400–500 token，忽略它只是「少算一点」；W3 接上 MCP 之后工具面
     * 可以到数千 token ⇒ 低估会变成「以为没超、实际超窗」：输入 + 输出共享同一个窗口，
     * 端点直接 400，而本地账本显示一切正常（`w4-context-design.md` §2.3）。
     *
     * ## 口径
     *
     * 每个工具 +4 token 的协议开销（与 [PER_MESSAGE_OVERHEAD] 同源：`type/function/name`
     * 这层包裹），再加上 name / description / 每个参数的 name / description / 枚举值的文本估算。
     *
     * **空工具面返回 0**：这是回归钉 —— `system + tools + history` 在 tools 为空时必须
     * 与「改动前只算 system + history」逐值相同（design §4 A6③）。
     */
    fun estimateToolSpecs(specs: List<ToolSpec>): Int {
        if (specs.isEmpty()) return 0
        var total = 0
        for (spec in specs) {
            total += PER_TOOL_OVERHEAD
            total += estimate(spec.name)
            total += estimate(spec.description)
            for (parameter in spec.parameters) {
                total += estimate(parameter.name)
                total += estimate(parameter.description)
                for (value in parameter.enumValues) total += estimate(value)
            }
        }
        return total
    }

    /**
     * CJK 判定：汉字、假名、谚文、CJK 标点、全角形式。
     *
     * 刻意用码点区间而不是正则：正则在这里每字符一次匹配，压缩判定是热路径。
     */
    private fun isCjk(cp: Int): Boolean = when (cp) {
        in 0x3000..0x303F -> true   // CJK 标点
        in 0x3040..0x30FF -> true   // 平假名 / 片假名
        in 0x3400..0x4DBF -> true   // 扩展 A
        in 0x4E00..0x9FFF -> true   // 基本区
        in 0xAC00..0xD7AF -> true   // 谚文音节
        in 0xF900..0xFAFF -> true   // 兼容汉字
        in 0xFF00..0xFFEF -> true   // 全角形式
        in 0x20000..0x2FA1F -> true // 扩展 B~F
        else -> false
    }
}
