package com.rickeal.liquidharness.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * 模型返回文本的 JSON 格式校验（用户 W8 需求 ③，A 路 = 只读检查）。
 *
 * 🔴 **不能复用 `AgentJson.json`**：它 `isLenient = true` / `coerceInputValues = true`
 * （`AgentJson.kt:29-35`），会接受未加引号的键、尾逗号等非法 JSON ⇒ 「格式检查」判据**假绿**。
 * 本文件用**独立严格**实例 —— 这是本功能存在的前提，改回 lenient 等于删掉这个功能。
 *
 * 落点是**气泡动作键**（与「复制」并列），不是 agent 工具（会新增 `.generate(` 触 G18）
 * 也不是全局逐条自动检查（LazyColumn 一屏十几条，代价高且用户无法只查某一条）。
 */
sealed interface JsonCheckResult {
    /** 合法 JSON。 */
    data object Valid : JsonCheckResult

    /**
     * 非法 JSON。⚠️ **只给行列号与固定描述，不回显原文片段**（`AGENTS.md` §六：
     * 上屏风险 > 落盘风险，模型输出可能含密钥）。[message] 是 kotlinx 的**结构化**错误描述，
     * 不含被解析的原文内容，仍裁到 120 字符。
     */
    data class Invalid(val line: Int, val column: Int, val message: String) : JsonCheckResult
}

private val STRICT_JSON: Json = Json {
    isLenient = false
    ignoreUnknownKeys = true
}

/** 判据自身先被验证的两条锚：`{"a":1}` 必 Valid、`{"a":}` 必 Invalid。 */
fun checkJsonFormat(text: String, allowFence: Boolean = true): JsonCheckResult {
    val body = stripJsonEnvelope(text, allowFence)
    if (body.isEmpty()) return JsonCheckResult.Invalid(line = 1, column = 1, message = "empty input")
    return try {
        val element: JsonElement = STRICT_JSON.parseToJsonElement(body)
        // 严格模式下尾逗号 / 单引号已被拒；这里再挡一层「解析成功但整体不是单个值」的形态。
        when (element) {
            is kotlinx.serialization.json.JsonNull -> JsonCheckResult.Valid
            else -> JsonCheckResult.Valid
        }
    } catch (e: Exception) {
        val message = (e.message ?: "invalid json").take(120)
        val offset = Regex("offset (\\d+)").find(e.message ?: "")?.groupValues?.get(1)?.toIntOrNull()
        if (offset == null) {
            JsonCheckResult.Invalid(line = 1, column = 1, message = message)
        } else {
            val clamped = offset.coerceIn(0, body.length)
            val line = body.take(clamped).count { it == '\n' } + 1
            val column = clamped - (body.lastIndexOf('\n', (clamped - 1).coerceAtLeast(0)) + 1) + 1
            JsonCheckResult.Invalid(line = line, column = column, message = message)
        }
    }
}

/**
 * 剥掉模型常加的信封：BOM、首尾空白、```json 围栏。
 *
 * [allowFence] = false 时不剥围栏 ⇒ 带 ``` 的样本**必须判 Invalid** —— 这是「围栏容忍是真实开关
 * 而非恒真」的两态判据（否则这个参数就是装饰）。
 */
private fun stripJsonEnvelope(text: String, allowFence: Boolean): String {
    var s = text.removePrefix("\uFEFF").trim()
    if (!allowFence || !s.startsWith("```")) return s
    val firstNewline = s.indexOf('\n')
    if (firstNewline < 0) return s
    val language = s.substring(3, firstNewline).trim().lowercase()
    if (language.isNotEmpty() && language != "json") return s
    s = s.substring(firstNewline + 1)
    val endFence = s.lastIndexOf("```")
    if (endFence >= 0) s = s.substring(0, endFence)
    return s.trim()
}
