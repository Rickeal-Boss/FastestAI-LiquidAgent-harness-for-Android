package com.rickeal.liquidharness.core.model

import kotlinx.serialization.Serializable

/**
 * 一条工具调用。
 *
 * [argumentsJson] 是**未解析的原始 JSON 文本**，不是 `Map`：
 * 模型产出的参数经常是坏 JSON 或缺必填字段，必须在 `ToolArgsValidator` 里
 * 带着原始文本报错（回灌给模型让它自己改），解析成 Map 会丢掉「它到底写了什么」。
 *
 * [raw] 保留供应商原始片段，用于排查「参数碎片拼接错位」类缺陷。
 */
@Serializable
data class ToolCall(
    val id: String = newId(),
    val name: String = "",
    val argumentsJson: String = "{}",
    val raw: String = "",
)

/**
 * 工具执行结果。
 *
 * 相对旧仓加两个字段，动机都是 API 路径的新风险（03-contracts §二）：
 *
 * - [displayOutput]：脱敏后的展示文本。脱敏必须在「上屏」与「回灌模型」两处取不同值 ——
 *   模型让 `file_write` 写一个 key 到文件是合法需求（执行必须用原文），
 *   但审批卡 / 气泡里不能显示原文（会进截图与 bug report）。
 * - [indeterminate]：结果不确定（超时 / 取消后孤儿协程仍可能完成副作用）。
 *   模型据此不应假设「没执行」。
 */
@Serializable
data class ToolResult(
    val callId: String = "",
    val name: String = "",
    val ok: Boolean = true,
    val output: String = "",
    val errorMessage: String? = null,
    val elapsedMillis: Long = 0L,
    val truncated: Boolean = false,
    /** null = 与 [output] 相同（无需脱敏）。 */
    val displayOutput: String? = null,
    val indeterminate: Boolean = false,
) {
    /** 给 UI / 日志用。上屏一律走这里，不要直接读 [output]。 */
    val shown: String get() = displayOutput ?: output
}
