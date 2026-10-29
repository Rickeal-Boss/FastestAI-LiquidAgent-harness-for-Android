package com.rickeal.liquidharness.core.model

/**
 * 密钥脱敏。
 *
 * 覆盖五处泄漏面里的**四处**（00-brief A7）：用户输入 / 模型输出 / 工具参数（仅展示侧）/
 * HTTP 头。第五处「截图上屏」不是文本问题，由 UI 层保证（审批卡只显示 [ToolResult.shown]）。
 *
 * 设计取舍：**只认前缀锚定的形态，不认「看起来像 base64 的长串」**。
 * 通用长串规则会把正常的图片 base64、哈希、签名一起打码，导致工具输出不可读 ——
 * 而这类误伤会让开发者关掉脱敏。所以宁可漏掉「无前缀的裸密钥」，
 * 也不能误伤「合法长 base64」。
 *
 * 空命中语义：不含任何匹配 ⇒ `redact` 原样返回**同一个 String 实例**（不新建对象），
 * 便于调用方用引用比较做「有没有命中」的快速判断。
 */
object SecretRedactor {

    /** 替换标记。英文常量，避免守卫 N3（中文硬编码）判红。 */
    const val MASK: String = "***"

    // ---- 1. PEM 私钥块（必须先跑：块内可能含其它形态） ----
    private val PRIVATE_KEY_BLOCK = Regex(
        """-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----"""
    )

    // ---- 2. Anthropic ----
    private val ANTHROPIC_KEY = Regex("""\bsk-ant-[A-Za-z0-9_\-]{8,}""")

    // ---- 3. OpenAI / DeepSeek / SiliconFlow 系（在 Anthropic 之后跑，故不会误吞 sk-ant-） ----
    private val OPENAI_KEY = Regex("""\bsk-[A-Za-z0-9_\-]{16,}""")

    // ---- 4. HTTP 头里的 Bearer ----
    private val BEARER = Regex("""(?i)\bBearer\s+[A-Za-z0-9._\-]{8,}""")

    // ---- 5. AWS Access Key ID ----
    private val AWS_ACCESS_KEY = Regex("""\b(?:AKIA|ASIA)[0-9A-Z]{16}\b""")

    // ---- 6. Google API Key ----
    private val GOOGLE_API_KEY = Regex("""\bAIza[0-9A-Za-z_\-]{35}\b""")

    // ---- 7. GitHub token ----
    private val GITHUB_TOKEN = Regex("""\bgh[pousr]_[A-Za-z0-9]{36,}\b""")

    // ---- 8. JWT（header.payload.signature） ----
    private val JWT = Regex("""\beyJ[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}""")

    // ---- 9. `Authorization: xxx` / `X-Api-Key: xxx` / `api_key=xxx` ----
    private val AUTH_FIELD = Regex(
        """(?i)\b(Authorization|X-Api-Key|X-Goog-Api-Key|api[-_]?key)(\s*[:=]\s*)["']?[^\s"',;]{8,}"""
    )

    // ---- 10. URL 查询参数（只替换值，保留参数名 —— AGENTS.md §六） ----
    private val URL_QUERY_SECRET = Regex(
        """(?i)([?&](?:key|api_key|apikey|access_token|token|secret|password)=)([^&\s#]+)"""
    )

    /** header 名命中即整值打码（值本身可能不带任何可识别前缀）。 */
    private val SENSITIVE_HEADER_NAMES = setOf(
        "authorization",
        "proxy-authorization",
        "x-api-key",
        "x-goog-api-key",
        "api-key",
        "cookie",
        "set-cookie",
    )

    /** 文本脱敏。幂等：对已脱敏的文本再跑一次结果不变。 */
    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        out = PRIVATE_KEY_BLOCK.replace(out, "[REDACTED PRIVATE KEY]")
        out = ANTHROPIC_KEY.replace(out, "sk-ant-$MASK")
        out = OPENAI_KEY.replace(out, "sk-$MASK")
        out = BEARER.replace(out, "Bearer $MASK")
        out = AWS_ACCESS_KEY.replace(out, "AKIA$MASK")
        out = GOOGLE_API_KEY.replace(out, "AIza$MASK")
        out = GITHUB_TOKEN.replace(out, "gh_$MASK")
        out = JWT.replace(out, MASK)
        out = AUTH_FIELD.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + MASK }
        out = URL_QUERY_SECRET.replace(out) { m -> m.groupValues[1] + MASK }
        return out
    }

    /** 只用于「日志出口先脱敏再判 400」这类分支判断，不要用它替代 [redact]。 */
    fun containsSecret(text: String): Boolean = text.isNotEmpty() && redact(text) != text

    /**
     * URL 脱敏：`?k=x` → `?k=***`，**保留参数名**（否则排查时不知道是哪个参数）。
     * 不处理 path 里的密钥（没有可靠判据，且路径里的密钥应视为配置错误）。
     */
    fun redactUrl(url: String): String = URL_QUERY_SECRET.replace(url) { m -> m.groupValues[1] + MASK }

    /**
     * HTTP 头脱敏。返回新 Map（不原地改，调用方的 map 可能是不可变的）。
     * 敏感 header 名（[SENSITIVE_HEADER_NAMES]）一律整值打码，不看值的形态。
     */
    fun redactHeaders(headers: Map<String, String>): Map<String, String> {
        if (headers.isEmpty()) return headers
        val out = LinkedHashMap<String, String>(headers.size)
        for ((key, value) in headers) {
            out[key] = if (key.lowercase() in SENSITIVE_HEADER_NAMES) MASK else redact(value)
        }
        return out
    }
}
