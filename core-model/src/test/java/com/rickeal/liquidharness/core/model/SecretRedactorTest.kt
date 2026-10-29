package com.rickeal.liquidharness.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 密钥脱敏的单测。
 *
 * 每个用例对应一类真实泄漏面（用户输入 / 模型输出 / 工具参数 / HTTP 头）。
 * 最后一条「正常长 base64 不被误伤」是**反向判据** —— 没有它，脱敏会被开发者关掉，
 * 于是所有正向用例一起失效。
 */
class SecretRedactorTest {

    private val openAiKey = "sk-proj-AbCdEfGhIjKlMnOpQrStUvWxYz012345"
    private val anthropicKey = "sk-ant-api03-AbCdEfGhIjKlMnOpQrStUvWxYz"
    private val awsKey = "AKIAIOSFODNN7EXAMPLE"
    private val googleKey = "AIza" + "B".repeat(35)
    private val githubToken = "ghp_" + "c".repeat(36)
    private val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dBjftJeZ4CVPmB92K27uhbUJU1p1r"

    /** 类 1：OpenAI 系 `sk-` 前缀。 */
    @Test
    fun openAiStyleKeyIsRedacted() {
        val redacted = SecretRedactor.redact("key=$openAiKey")
        assertFalse(redacted.contains(openAiKey))
        assertTrue(redacted.contains("sk-***"))
    }

    /** 类 2：Anthropic `sk-ant-` 前缀（必须先于 `sk-` 规则，否则只剩 `ant-***` 无法辨认）。 */
    @Test
    fun anthropicKeyIsRedacted() {
        val redacted = SecretRedactor.redact("ANTHROPIC_API_KEY=$anthropicKey")
        assertFalse(redacted.contains(anthropicKey))
        assertTrue(redacted.contains("sk-ant-***"))
    }

    /** 类 3：`Authorization: Bearer xxx`。 */
    @Test
    fun bearerTokenIsRedacted() {
        val redacted = SecretRedactor.redact("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9abcdefgh")
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiJ9abcdefgh"))
        assertTrue(redacted.contains("Bearer ***"))
    }

    /** 类 4：AWS Access Key ID。 */
    @Test
    fun awsAccessKeyIsRedacted() {
        val redacted = SecretRedactor.redact("aws_access_key_id = $awsKey")
        assertFalse(redacted.contains(awsKey))
        assertTrue(redacted.contains("AKIA***"))
    }

    /** 类 5：Google API Key。 */
    @Test
    fun googleApiKeyIsRedacted() {
        // 刻意不写成 `?key=...` 形态：URL 查询参数规则会二次覆盖成 `?key=***`，
        // 那样断言就看不出是哪条规则命中的。这里单独验 Google 前缀规则。
        val redacted = SecretRedactor.redact("genai credential $googleKey configured")
        assertFalse(redacted.contains(googleKey))
        assertTrue(redacted.contains("AIza***"))
    }

    /** 类 6：GitHub token。 */
    @Test
    fun githubTokenIsRedacted() {
        val redacted = SecretRedactor.redact("remote: https://$githubToken@github.com/x/y.git")
        assertFalse(redacted.contains(githubToken))
        assertTrue(redacted.contains("gh_***"))
    }

    /** 类 7：JWT 三段式。 */
    @Test
    fun jwtIsRedacted() {
        val redacted = SecretRedactor.redact("token=$jwt")
        assertFalse(redacted.contains(jwt))
        assertTrue(redacted.contains(SecretRedactor.MASK))
    }

    /** 类 8：`X-Api-Key:` / `api_key=` 这类「名字+值」形态，**保留参数名**。 */
    @Test
    fun namedSecretFieldKeepsItsName() {
        val redacted = SecretRedactor.redact("X-Api-Key: abcdef1234567890")
        assertFalse(redacted.contains("abcdef1234567890"))
        assertTrue(redacted.contains("X-Api-Key"))
        assertTrue(redacted.contains(SecretRedactor.MASK))
    }

    /** 类 9：PEM 私钥块整块替换（块内行数不定，逐行正则拦不住）。 */
    @Test
    fun pemPrivateKeyBlockIsRedacted() {
        val block = "-----BEGIN RSA PRIVATE KEY-----\nMIIEow\nAAAA\n-----END RSA PRIVATE KEY-----"
        val redacted = SecretRedactor.redact("pem:\n$block\nend")
        assertFalse(redacted.contains("MIIEow"))
        assertTrue(redacted.contains("[REDACTED PRIVATE KEY]"))
    }

    /** 类 10：URL 查询参数，只替换值保留参数名。 */
    @Test
    fun urlQuerySecretKeepsParameterName() {
        val redacted = SecretRedactor.redactUrl("https://api.example.com/v1?api_key=SECRET123&model=gpt-4o")
        assertEquals("https://api.example.com/v1?api_key=***&model=gpt-4o", redacted)
    }

    /** 🔴 反向判据：正常的长 base64（图片 / 哈希）**不得**被打码。 */
    @Test
    fun ordinaryLongBase64IsNotRedacted() {
        val base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAE" +
            "hQGAhKmMIQAAAABJRU5ErkJggg=="
        assertEquals(base64, SecretRedactor.redact(base64))
        assertFalse(SecretRedactor.containsSecret(base64))
    }

    /** 脱敏必须幂等：日志管道可能重复过一遍。 */
    @Test
    fun redactionIsIdempotent() {
        val once = SecretRedactor.redact("Authorization: Bearer abcdefgh12345678")
        assertEquals(once, SecretRedactor.redact(once))
    }

    /** 敏感 header 名一律整值打码，不看值的形态（值可能没有可识别前缀）。 */
    @Test
    fun sensitiveHeaderNamesAreMaskedWholesale() {
        val headers = mapOf(
            "Authorization" to "opaque-value-without-prefix",
            "Content-Type" to "application/json",
        )
        val redacted = SecretRedactor.redactHeaders(headers)

        assertEquals(SecretRedactor.MASK, redacted["Authorization"])
        assertEquals("application/json", redacted["Content-Type"])
    }

    /** 空文本不命中（避免每条日志都走一遍正则替换）。 */
    @Test
    fun emptyTextIsNotTouched() {
        assertEquals("", SecretRedactor.redact(""))
        assertFalse(SecretRedactor.containsSecret(""))
    }
}
