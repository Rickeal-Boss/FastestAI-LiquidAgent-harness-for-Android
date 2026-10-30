package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.model.AgentJson
import kotlinx.serialization.json.JsonPrimitive

/**
 * 工具参数的宽容读取（移植自旧仓 `tools/ToolArgs.kt`）。
 *
 * 纪律：**解析失败一律回退默认值**，绝不抛异常。参数校验归
 * `ToolArgsValidator`（它在执行前把坏参数拦下并回灌错误）；到了工具这一层还抛异常，
 * 只会让「模型写了个坏 JSON」变成一条「工具执行异常」，错误信息反而更不可读。
 */
internal fun stringArg(argumentsJson: String, key: String, default: String = ""): String {
    val obj = AgentJson.decodeObjectOrNull(argumentsJson) ?: return default
    val value = obj[key] ?: return default
    return (value as? JsonPrimitive)?.content ?: value.toString()
}

/**
 * 整型参数：数字与数字字符串都接受（模型常把 5 写成 `"5"`）；非法值回退 [default]。
 *
 * 与 `ToolArgsValidator` 的口径差：校验层用 `toDoubleOrNull` 判数字，`"1e5"` 会被放行，
 * 而这里 `toIntOrNull` 必然失败 ⇒ 静默降级。保持降级（不因一个可选分页参数拒整个调用），
 * 但这条差异已被校验层 KDoc 记录，别在别处再放宽。
 */
internal fun intArg(argumentsJson: String, key: String, default: Int): Int {
    val obj = AgentJson.decodeObjectOrNull(argumentsJson) ?: return default
    val primitive = obj[key] as? JsonPrimitive ?: return default
    return primitive.content.toIntOrNull() ?: default
}
