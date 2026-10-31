package com.rickeal.liquidharness.core.mcp

import com.rickeal.liquidharness.core.agent.tool.ToolCallOutcome
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolSourceKind
import com.rickeal.liquidharness.core.model.ToolSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * `tools/list` / `tools/call` 结果 → 内核类型的翻译层。全部纯函数。
 *
 * 远端文本（工具名、description、schema）**一律按不可信输入处理**：
 * 名字要被字符集与长度双重收窄，description 要剥控制字符并截断。
 */
private val json = Json { ignoreUnknownKeys = true }

/** 命名空间前缀：`mcp__<alias>__`。调用侧剥离远端名时用同一个函数，避免两套规则。 */
internal fun namePrefix(alias: String): String =
    "mcp__" + sanitizeName(alias).take(McpProtocol.MAX_NAMESPACE) + "__"

/**
 * 非 `[a-zA-Z0-9_-]` 一律替换为 `_`。
 *
 * 不做「去掉」而是「替换」：去掉会让 `read.file` 与 `readfile` 撞名，
 * 替换至少保留长度信息，撞名概率低得多。
 */
internal fun sanitizeName(raw: String): String {
    if (raw.isEmpty()) return "_"
    val out = StringBuilder(raw.length)
    for (ch in raw) {
        val keep = ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '_' || ch == '-'
        out.append(if (keep) ch else '_')
    }
    return out.toString()
}

/** 命名空间化 + 总长 ≤ [McpProtocol.MAX_TOOL_NAME]。 */
internal fun namespacedToolName(alias: String, remoteName: String): String {
    val prefix = namePrefix(alias)
    val budget = (McpProtocol.MAX_TOOL_NAME - prefix.length).coerceAtLeast(1)
    val tool = sanitizeName(remoteName).take(budget)
    return (prefix + tool).take(McpProtocol.MAX_TOOL_NAME)
}

/**
 * 剥控制字符（C0 / DEL / C1），换行制表折成空格，再截断。
 *
 * 为什么必须剥：description 会进 system 提示词。一个带 `\u0000` 或 ANSI 转义
 * （`\u001B[31m`）的描述可以伪造提示词结构，或让日志终端渲染出假行。
 */
internal fun cleanText(raw: String, limit: Int): String {
    if (raw.isEmpty()) return ""
    val out = StringBuilder(raw.length)
    for (ch in raw) {
        when {
            ch == '\n' || ch == '\r' || ch == '\t' -> out.append(' ')
            ch.code < 0x20 || ch.code == 0x7F || ch.code in 0x80..0x9F -> Unit
            else -> out.append(ch)
        }
    }
    return out.toString().trim().take(limit)
}

internal fun parseToolList(resultJson: String): List<McpToolDescriptor> {
    val root = parseObject(resultJson) ?: return emptyList()
    val tools = root["tools"] as? JsonArray ?: return emptyList()
    return tools.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val name = obj.string("name")?.trim().orEmpty()
        if (name.isEmpty()) return@mapNotNull null
        val schema = obj["inputSchema"] ?: obj["input_schema"]
        McpToolDescriptor(
            name = name,
            description = obj.string("description").orEmpty(),
            inputSchemaJson = schema?.toString() ?: "{}",
        )
    }
}

internal fun parseNextCursor(resultJson: String): String? {
    val root = parseObject(resultJson) ?: return null
    val cursor = root.string("nextCursor") ?: root.string("next_cursor")
    return cursor?.takeIf { it.isNotBlank() }
}

/** 一条远端声明 → 内核工具。审批、写效应、MCP 来源都是**恒定**的，不由远端决定。 */
internal fun buildToolSpec(alias: String, descriptor: McpToolDescriptor): ToolSpec = ToolSpec(
    name = namespacedToolName(alias, descriptor.name),
    description = cleanText(descriptor.description, McpProtocol.MAX_DESCRIPTION),
    parameters = parseParameters(descriptor.inputSchemaJson),
    requiresConfirmation = true,
    dangerous = false,
    category = "mcp",
    effect = ToolEffect.WRITE,
    source = ToolSourceKind.MCP,
    namespace = alias,
)

internal fun parseParameters(inputSchemaJson: String): List<ToolParameter> {
    val schema = parseObject(inputSchemaJson) ?: return emptyList()
    val properties = schema["properties"] as? JsonObject ?: return emptyList()
    val required = (schema["required"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?.toSet()
        .orEmpty()
    return properties.map { (name, element) ->
        val obj = element as? JsonObject ?: JsonObject(emptyMap())
        ToolParameter(
            name = name,
            type = paramType(obj),
            description = cleanText(obj.string("description").orEmpty(), McpProtocol.MAX_PARAM_DESCRIPTION),
            required = required.contains(name),
            enumValues = enumValues(obj),
        )
    }
}

/**
 * `content[]` 里只取 `text`。非文本块（image / resource）当前内核没有承载形态，
 * 静默丢弃而不是塞成 JSON —— 塞进去会让模型以为拿到了一段可读文本。
 *
 * `isError = true` 是**工具级失败**（工具跑了但报错），不是协议失败：
 * 输出仍然返回，同时置 `ok = false`，让循环把错误回灌给模型而不是中断整轮。
 */
internal fun parseCallResult(resultJson: String): ToolCallOutcome {
    val root = parseObject(resultJson) ?: return ToolCallOutcome(false, errorMessage = "malformed")
    val text = buildString {
        val content = root["content"] as? JsonArray
        if (content != null) {
            for (item in content) {
                val block = item as? JsonObject ?: continue
                val chunk = block.string("text") ?: continue
                if (isNotEmpty()) append('\n')
                append(chunk)
            }
        }
    }
    val isError = (root["isError"] as? JsonPrimitive)?.booleanOrNull == true ||
        (root["is_error"] as? JsonPrimitive)?.booleanOrNull == true
    return if (isError) {
        ToolCallOutcome(
            ok = false,
            output = text,
            errorMessage = text.take(McpProtocol.MAX_DESCRIPTION).ifBlank { "tool_error" },
        )
    } else {
        ToolCallOutcome(ok = true, output = text)
    }
}

private fun parseObject(raw: String): JsonObject? =
    runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun paramType(obj: JsonObject): ToolParamType {
    val raw: JsonElement? = obj["type"]
    val name = when (raw) {
        is JsonPrimitive -> raw.contentOrNull
        is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .firstOrNull { it != "null" }
        else -> null
    } ?: "string"
    return when (name) {
        "number" -> ToolParamType.NUMBER
        "integer" -> ToolParamType.INTEGER
        "boolean" -> ToolParamType.BOOLEAN
        "array" -> ToolParamType.ARRAY
        "object" -> ToolParamType.OBJECT
        else -> ToolParamType.STRING
    }
}

private fun enumValues(obj: JsonObject): List<String> {
    val raw = obj["enum"] as? JsonArray ?: return emptyList()
    return raw.mapNotNull { element ->
        (element as? JsonPrimitive)?.contentOrNull ?: element.toString()
    }
}
