package com.rickeal.liquidharness.core.model

import kotlinx.serialization.json.Json

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
     * 非法 JSON。⚠️ **只给行列号与描述段，不回显原文片段**（`AGENTS.md` §六：
     * 上屏风险 > 落盘风险，模型输出可能含密钥）。[message] 是 kotlinx 异常里
     * `JSON input:` **之前**的描述段，原文整段被丢弃，再裁到 120 字符。
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
        // 严格实例自身就拒绝「解析成功后还有尾随内容」；原先
        // `JsonNull -> Valid; else -> Valid` 两分支同值 = 恒真空判据，删掉不丢任何防线（kotlinx②）。
        STRICT_JSON.parseToJsonElement(body)
        JsonCheckResult.Valid
    } catch (e: Exception) {
        val raw = e.message.orEmpty()
        // kotlinx 新版异常 message 常带 `JSON input: <整段原文>`；只取它之前的描述段，
        // 否则模型输出里的凭据会被原样回显到气泡上屏（kotlinx①）。
        val message = raw.substringBefore("JSON input:").ifBlank { "invalid json" }.take(120)
        val offset = Regex("offset (\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
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
    // 结束围栏必须是**独立一行**：字符串值里含 ``` 时不能把它当围栏裁掉（kotlinx③）。
    // 逐行累加**真实分隔符长度**、取最后一个整行恰为 ``` 的起点作为裁断点：
    // CRLF 的分隔符是 \r\n 两个字符，旧实现 `offset += line.length + 1` 漏算 \r，
    // 多行 JSON 会被提前裁断 / 内容错位（W9-01 P1-1）。`endFence = cursor` 记的是
    // 该独立行的**起点**（不含前面的换行分隔符），故 substring(0, endFence) 正好截到
    // 内容尾、不含围栏与任何尾随分隔符。
    var cursor = 0
    var endFence = -1
    while (cursor < s.length) {
        val nl = s.indexOf('\n', cursor)
        val lineEnd = if (nl < 0) s.length else nl
        val line = s.substring(cursor, lineEnd)
        if (line.trim() == "```") endFence = cursor
        // 真实分隔符长度：行尾为 \r\n 算 2，单 \n 算 1，末行无分隔算 0。
        val delim = if (nl < 0) 0 else if (lineEnd > cursor && s[lineEnd - 1] == '\r') 2 else 1
        cursor = if (nl < 0) s.length else nl + 1
    }
    if (endFence >= 0) s = s.substring(0, endFence)
    return s.trim()
}
