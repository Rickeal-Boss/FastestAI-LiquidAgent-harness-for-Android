package com.rickeal.liquidharness.core.agent.tool

import com.rickeal.liquidharness.core.model.AgentJson
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 工具参数 Schema 校验器（移植自旧仓 `schema/ToolArgsValidator.kt`）。
 *
 * 校验依据是 [ToolSpec.parameters] 里已有的声明（类型 + required + enumValues），
 * **零新依赖**。设计取舍（沿用旧仓「宁可放过，不可死锁」）：
 *
 * - **未声明的多余键一律放行**：模型常带说明性字段，因多余字段拒调用会让工具永远调不成；
 * - **显式 null 视同缺失**：模型爱写 `"path": null`；required 判违规、可选项静默跳过；
 * - 只在「必然执行失败」时拦：类型不匹配 / 必填缺失 / 枚举越界。路径合法性、
 *   数值范围这类**语义**校验仍归工具自己。
 *
 * 返回类型是 sealed（旧仓返回 `List<ArgsViolation>`）：调用方只有「放行」与「回灌错误」
 * 两条路，用 sealed 让「拿到了违规列表却忘了判空」这种静默失败不可能发生。
 * [Invalid.reason] 里**必须**带原始参数片段 —— 回灌给模型才能让它定向修复。
 */
object ToolArgsValidator {

    /** 校验结论。 */
    sealed interface Result {

        /** 通过。[arguments] 是解析后的对象，调用方可直接复用（省一次 parse）。 */
        data class Valid(val arguments: JsonObject) : Result

        /** 不通过。[reason] 是给**模型**看的修复指引（含原始参数片段，纯 ASCII）。 */
        data class Invalid(val reason: String) : Result
    }

    /** 回灌给模型的原始参数片段上限：够模型认出「自己写了什么」，又不淹没错误描述。 */
    private const val RAW_PREVIEW_CHARS = 160

    /**
     * 校验一段工具调用参数。
     *
     * 参数声明为空时**不解析**（旧仓同）：无 schema 可依，任何输入都放行，
     * 坏 JSON 也留给工具自己的解析器兜底。
     */
    fun validate(spec: ToolSpec, argumentsJson: String): Result {
        val trimmed = argumentsJson.trim()
        if (spec.parameters.isEmpty()) {
            return Result.Valid(AgentJson.decodeObjectOrNull(trimmed) ?: JsonObject(emptyMap()))
        }
        val root: JsonElement = if (trimmed.isEmpty()) {
            JsonObject(emptyMap())
        } else {
            try {
                AgentJson.json.parseToJsonElement(trimmed)
            } catch (t: Throwable) {
                return Result.Invalid(
                    "tool " + spec.name + " arguments are not valid JSON (" +
                        (t.message ?: t.javaClass.simpleName) + "); raw arguments: " + preview(trimmed),
                )
            }
        }
        if (root !is JsonObject) {
            return Result.Invalid(
                "tool " + spec.name + " arguments must be a JSON object, got " + describe(root) +
                    "; raw arguments: " + preview(trimmed),
            )
        }
        val violations = collect(spec, root)
        if (violations.isEmpty()) return Result.Valid(root)
        return Result.Invalid(render(spec.name, violations, trimmed))
    }

    /** 逐参数比对。缺失与显式 null 同语义（required 判违规，可选项跳过）。 */
    private fun collect(spec: ToolSpec, root: JsonObject): List<Violation> {
        val violations = ArrayList<Violation>()
        for (parameter in spec.parameters) {
            val raw = root[parameter.name]
            if (raw == null || raw is JsonNull) {
                if (parameter.required) {
                    violations.add(Violation(parameter.name, "is required but", "missing"))
                }
                continue
            }
            val typeError = typeMismatch(parameter.type, raw)
            if (typeError != null) {
                violations.add(Violation(parameter.name, "expects " + parameter.type.jsonName + ", got", typeError))
                continue
            }
            if (parameter.type == ToolParamType.STRING &&
                parameter.enumValues.isNotEmpty() &&
                raw is JsonPrimitive &&
                raw.content !in parameter.enumValues
            ) {
                violations.add(
                    Violation(
                        parameter.name,
                        "expects one of [" + parameter.enumValues.joinToString(", ") + "], got",
                        "\"" + raw.content + "\"",
                    ),
                )
            }
        }
        return violations
    }

    /**
     * 渲染给模型的修复指引：逐条列差异 + 原始参数片段 + 明确的下一步动作。
     * 措辞刻意可执行 —— 只说「参数错误」会诱导模型盲猜重试。
     */
    private fun render(toolName: String, violations: List<Violation>, raw: String): String {
        val head = "invalid arguments for tool " + toolName + " (" + violations.size + " problem(s)):"
        val body = violations.mapIndexed { index, v -> " " + (index + 1) + ". " + v.describe() }
        val tail = "raw arguments: " + preview(raw)
        val action = "fix the arguments to match the tool schema, then call the same tool again."
        return (listOf(head) + body + listOf(tail, action)).joinToString(" ")
    }

    /**
     * 类型不匹配时返回实际类型描述；匹配返回 null。
     *
     * 数字字符串口径（沿用旧仓 W84-A9，**本层放宽**）：NUMBER / INTEGER 接受
     * `"5"` / `"1.5"` —— 工具侧读取器明示宽容，校验层不得比它所守的实现更严格。
     * 仍拦的：非数字字符串、INTEGER 位置的浮点（工具侧确实会退化成默认值）。
     */
    private fun typeMismatch(expected: ToolParamType, element: JsonElement): String? {
        val ok = when (expected) {
            ToolParamType.STRING -> element is JsonPrimitive && element.isString
            ToolParamType.NUMBER -> element is JsonPrimitive && isNumeric(element.content)
            ToolParamType.INTEGER -> element is JsonPrimitive && element.content.toLongOrNull() != null
            ToolParamType.BOOLEAN -> element is JsonPrimitive && !element.isString &&
                (element.content == "true" || element.content == "false")
            ToolParamType.ARRAY -> element is JsonArray
            ToolParamType.OBJECT -> element is JsonObject
        }
        return if (ok) null else describe(element)
    }

    private fun isNumeric(content: String): Boolean =
        content.toLongOrNull() != null || content.toDoubleOrNull() != null

    private fun describe(element: JsonElement): String = when (element) {
        is JsonNull -> "null"
        is JsonArray -> "array"
        is JsonObject -> "object"
        is JsonPrimitive -> if (element.isString) "string" else if (isNumeric(element.content)) "number" else element.content
    }

    /** 原文片段：压成单行（错误信息进 tool_result 一行一条），并按代理对安全截断。 */
    private fun preview(raw: String): String {
        val flat = raw.replace('\n', ' ').replace('\r', ' ')
        if (flat.length <= RAW_PREVIEW_CHARS) return flat
        val cut = flat.substring(0, RAW_PREVIEW_CHARS)
        val safe = if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
        return safe + "..."
    }

    /** 一条违规。`expected` / `got` 是片段（由 [describe] 拼成完整句子）。 */
    private data class Violation(val param: String, val expected: String, val got: String) {
        fun describe(): String = "parameter \"" + param + "\" " + expected + " " + got
    }
}
