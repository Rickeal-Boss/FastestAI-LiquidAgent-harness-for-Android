package com.rickeal.liquidharness.core.model

/**
 * 自定义协议保留键表 + 高级配置**运行时**校验。
 *
 * 从 `EndpointCapabilities.kt` 拆出只为守 G10：该文件写完 308 行 > core-model 单文件上限 300，
 * 按纪律「触顶先拆、不许调上限」外提本文件（纯函数 + 常量，无状态）。
 */

/** 自定义协议 `extraBody` 不许覆盖的保留键。覆盖任何一个都会破坏本仓的请求体契约。 */
val RESERVED_BODY_KEYS: Set<String> = setOf(
    "model",
    "messages",
    "stream",
    "stream_options",
    "max_tokens",
    "tools",
    "tool_choice",
    "temperature",
    "top_p",
    "response_format",
    "reasoning",
    "reasoning_effort",
    "enable_thinking",
    "chat_template_kwargs",
    "include_reasoning",
)

/** 键名含凭据语义的词表（与守卫 G17 同款词，但这是运行时校验 —— G17 只查 `@Serializable` 属性名，查不到 Map 的内容）。 */
private val CREDENTIALISH_KEY_FRAGMENTS: List<String> = listOf(
    "apikey",
    "api-key",
    "api_key",
    "secret",
    "password",
    "passwd",
    "authorization",
    "bearer",
    "token",
)

/** 值里的凭据形态标记：只认固定前缀 / 已知令牌族，不猜任意串（F-1 含值扫描）。 */
private val CREDENTIALISH_VALUE_MARKERS: List<String> = listOf(
    "sk-",
    "bearer ",
    "ghp_",
    "gho_",
    "xox",
    "akia",
    "-----begin ",
)

/**
 * 允许覆写的 body 键白名单（保存链排他）。未列入的键不许写进 `endpoints.json`：
 * 「任意键名 + 任意值」明文落盘正是 P0-1 的失守点。新增合法键必须显式改这里并补用例。
 */
val ALLOWED_EXTRA_BODY_KEYS: Set<String> = setOf(
    "top_k",
    "min_p",
    "seed",
    "repetition_penalty",
    "frequency_penalty",
    "presence_penalty",
    "logprobs",
    "top_logprobs",
    "n",
    "service_tier",
    "parallel_tool_calls",
    "flag",
    "note",
)

/**
 * 单键校验表：保存链 [validateAdvanced] 与 core-engine 的 `applyCustomProtocol`
 * **复用同一份**（W9-00 F-1），避免两处词表漂移。只兜底保留键与凭据形态，
 * 不含白名单排他 —— 白名单是落盘约束，运行时只保证「坏键 / 坏值不进请求体」。
 *
 * @return 违规描述（英文常量，`core-model` 不落中文）；null = 通过。
 */
fun credentialishExtraBodyViolation(key: String, value: String): String? {
    val k = key.trim()
    if (k.isEmpty()) return "extraBody: blank key"
    val lower = k.lowercase()
    if (lower in RESERVED_BODY_KEYS) return "extraBody: reserved key '" + k + "' cannot be overridden"
    if (CREDENTIALISH_KEY_FRAGMENTS.any { lower.contains(it) }) {
        return "extraBody: key '" + k + "' looks like a credential"
    }
    val v = value.trim().lowercase()
    if (CREDENTIALISH_VALUE_MARKERS.any { v.contains(it) }) {
        return "extraBody: value of '" + k + "' looks like a credential"
    }
    return null
}

/**
 * 高级配置的**运行时**校验（G17 查不到 `Map` 内容，这里是唯一防线）。
 *
 * 三道：保留键 / 凭据词表（含 `token`）/ 值凭据形态（复用 [credentialishExtraBodyViolation]），
 * 再加允许键白名单排他。**保存链落盘前调用，非空即 BLOCKED 不写盘**；
 * `applyCustomProtocol` 运行时复用同一张表逐键兜底。
 *
 * @return 错误清单；空 = 通过。错误是**英文常量**（`core-model` 不落中文，G16）。
 */
fun validateAdvanced(endpoint: RemoteEndpoint): List<String> {
    val errors = mutableListOf<String>()
    val cp = endpoint.customProtocol
    if (!cp.enabled) return errors
    for ((key, value) in cp.extraBody) {
        val violation = credentialishExtraBodyViolation(key, value)
        if (violation != null) {
            errors += violation
            continue
        }
        if (key.trim().lowercase() !in ALLOWED_EXTRA_BODY_KEYS) {
            errors += "extraBody: key '" + key.trim() + "' is not allowlisted"
        }
    }
    return errors
}

/** UI 五档键值（`EXTREME` 写盘为 `"extreme"`，线上由 `normalizeEffort` 降级；永不 `"max"`）。 */
val ReasoningEffort.uiKey: String
    get() = when (this) {
        ReasoningEffort.LOW -> "low"
        ReasoningEffort.MEDIUM -> "medium"
        ReasoningEffort.HIGH -> "high"
        ReasoningEffort.XHIGH -> "xhigh"
        ReasoningEffort.EXTREME -> "extreme"
    }

/** 「键=值，键=值」⇒ Map（自定义协议 extraBody 编辑框的解析）。空段 / 无等号忽略。 */
fun parseExtraBody(text: String): Map<String, String> = text
    .split(',', '\n')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .mapNotNull { line ->
        val parts = line.split('=', limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim() to parts[1].trim() else null
    }
    .toMap()
