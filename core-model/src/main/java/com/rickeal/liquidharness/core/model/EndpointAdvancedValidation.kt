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

/** 键名含凭据语义的词表（与守卫 G17 同款词，但这是**运行时**校验 —— G17 只查 `@Serializable` 属性名，查不到 Map 的内容）。 */
private val CREDENTIALISH_KEY_FRAGMENTS: List<String> = listOf(
    "apikey",
    "api-key",
    "api_key",
    "secret",
    "password",
    "passwd",
    "authorization",
    "bearer",
)

/**
 * 高级配置的**运行时**校验（G17 查不到 `Map` 内容，这里是唯一防线）。
 *
 * @return 错误清单；空 = 通过。错误是**英文常量**（`core-model` 不落中文，G16）。
 */
fun validateAdvanced(endpoint: RemoteEndpoint): List<String> {
    val errors = mutableListOf<String>()
    val cp = endpoint.customProtocol
    if (cp.enabled) {
        for (key in cp.extraBody.keys) {
            val k = key.trim()
            if (k.isEmpty()) {
                errors += "extraBody: blank key"
                continue
            }
            if (k.lowercase() in RESERVED_BODY_KEYS.map { it.lowercase() }) {
                errors += "extraBody: reserved key '" + k + "' cannot be overridden"
            }
            if (CREDENTIALISH_KEY_FRAGMENTS.any { k.lowercase().contains(it) }) {
                errors += "extraBody: key '" + k + "' looks like a credential"
            }
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
