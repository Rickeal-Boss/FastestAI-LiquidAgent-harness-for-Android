package com.rickeal.liquidharness.core.net

import java.net.URI

/** URL 被拒的原因。分类而不是只有布尔，是因为 UI 文案与排查方向不同。 */
enum class UrlRejectReason {
    /** 无法解析 / 没有 host。 */
    MALFORMED,

    /** 不是 http/https（`file:` / `ftp:` / `content:` 等）。 */
    NOT_HTTP_SCHEME,

    /** 明文 http 指向公网主机 —— 密钥与内容会裸奔。 */
    INSECURE_SCHEME,

    /** RFC1918 / 唯一本地地址 / 0.0.0.0，且端点未显式允许。 */
    PRIVATE_NETWORK,

    /**
     * 云元数据地址（`169.254.169.254` / `fd00:ec2::254`）。
     * **即使端点显式允许内网也拒绝** —— 这是 SSRF 拿云凭据的标准路径，
     * 没有任何合法的 agent 用途需要访问它。
     */
    METADATA_ADDRESS,
}

sealed interface UrlVerdict {
    data class Allowed(val url: String) : UrlVerdict

    data class Rejected(val reason: UrlRejectReason, val detail: String) : UrlVerdict
}

/** URL 不安全时抛出。文案为英文常量，由 UI 层映射为本地化文案（守卫 N3 基线 0）。 */
class UnsafeUrlException(
    val reason: UrlRejectReason,
    message: String,
) : IllegalArgumentException(message)

/**
 * 出网目标校验（SSRF 第一道闸）。
 *
 * 规则（`docs/03-contracts.md` §一 的 `allowPrivateNetwork` + 00-brief D10）：
 * 1. scheme 只允许 `https` / `http`；
 * 2. 云元数据地址与 link-local（`169.254.0.0/16`、`fe80::/10`）**永远拒绝**；
 * 3. loopback 与模拟器宿主 `10.0.2.2` 允许（本地 Ollama / vLLM 是明确支持的场景）；
 * 4. 其余私有网段（RFC1918 / ULA / `0.0.0.0`）仅在端点 `allowPrivateNetwork = true` 时允许；
 * 5. 明文 `http` 指向公网主机仅在 `allowPrivateNetwork = true` 时允许（默认 fail-closed）。
 *
 * ⚠️ **已知残余风险（未覆盖）**：本类只看**字面量**主机名 / IP。一个公网域名
 * 解析到 `192.168.x.x`（DNS rebinding / 内网域名）不会被这里拦下 ——
 * 要拦必须在**连接建立后**取 socket 的实际对端地址再校验一次。
 * 这一点在 `04-risk-register.md` R7 登记，W5 接 MCP 时补连接后校验。
 */
object UrlGuard {

    private const val METADATA_IPV4 = "169.254.169.254"
    private const val METADATA_IPV6 = "fd00:ec2::254"

    /** 模拟器访问宿主机 loopback 的固定地址（Android 官方约定）。 */
    private const val EMULATOR_HOST = "10.0.2.2"

    fun check(url: String, allowPrivateNetwork: Boolean = false): UrlVerdict {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return UrlVerdict.Rejected(UrlRejectReason.MALFORMED, "empty url")
        }
        val uri = try {
            URI(trimmed)
        } catch (t: Exception) {
            return UrlVerdict.Rejected(UrlRejectReason.MALFORMED, "unparsable url")
        }
        val scheme = uri.scheme?.lowercase()
            ?: return UrlVerdict.Rejected(UrlRejectReason.MALFORMED, "missing scheme")
        if (scheme != "https" && scheme != "http") {
            return UrlVerdict.Rejected(UrlRejectReason.NOT_HTTP_SCHEME, "scheme=$scheme")
        }
        val host = normalizeHost(uri.host)
            ?: return UrlVerdict.Rejected(UrlRejectReason.MALFORMED, "missing host")

        // 元数据 / link-local：无条件拒绝，allowPrivateNetwork 也不解锁。
        if (host == METADATA_IPV4 || host == METADATA_IPV6 || isLinkLocal(host)) {
            return UrlVerdict.Rejected(UrlRejectReason.METADATA_ADDRESS, "metadata/link-local host=$host")
        }
        // loopback 与模拟器宿主：明文 http 也允许（本地推理服务）。
        if (isLoopback(host) || host == EMULATOR_HOST) {
            return UrlVerdict.Allowed(trimmed)
        }
        // 其余私有网段：需要端点显式声明。
        if (isPrivate(host) && !allowPrivateNetwork) {
            return UrlVerdict.Rejected(UrlRejectReason.PRIVATE_NETWORK, "private host=$host")
        }
        // 明文 http 到公网：默认拒绝。
        if (scheme == "http" && !allowPrivateNetwork) {
            return UrlVerdict.Rejected(UrlRejectReason.INSECURE_SCHEME, "plaintext http to public host")
        }
        return UrlVerdict.Allowed(trimmed)
    }

    /** 校验并在不安全时抛 [UnsafeUrlException]。返回值是 trim 后的 URL，调用方应使用它。 */
    fun requireAllowed(url: String, allowPrivateNetwork: Boolean = false): String =
        when (val verdict = check(url, allowPrivateNetwork)) {
            is UrlVerdict.Allowed -> verdict.url
            is UrlVerdict.Rejected -> throw UnsafeUrlException(
                verdict.reason,
                "unsafe endpoint url: ${verdict.reason} (${verdict.detail})",
            )
        }

    /** IPv6 的 host 带方括号（`[::1]`），统一剥掉并小写。 */
    private fun normalizeHost(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val host = raw.trim().lowercase()
        return host.removePrefix("[").removeSuffix("]").ifBlank { null }
    }

    private fun isLoopback(host: String): Boolean =
        host == "localhost" ||
            host == "::1" ||
            host == "0:0:0:0:0:0:0:1" ||
            host.startsWith("127.")

    private fun isLinkLocal(host: String): Boolean =
        host.startsWith("169.254.") || host.startsWith("fe80:")

    private fun isPrivate(host: String): Boolean {
        if (host == "0.0.0.0" || host == "::") return true
        // IPv6 唯一本地地址 fc00::/7。必须带冒号才算 IPv6 字面量 ——
        // 否则 "fc-host.example.com" 这类域名会被误判成内网（假红比漏报更伤守卫）。
        if (host.contains(':') && (host.startsWith("fc") || host.startsWith("fd"))) return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        return when (a) {
            10 -> true
            172 -> b in 16..31
            192 -> b == 168
            100 -> b in 64..127 // CGNAT 100.64.0.0/10，运营商内网同样是内网
            else -> false
        }
    }
}
