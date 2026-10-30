package com.rickeal.liquidharness.core.net

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress

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
     * 云元数据地址（`169.254.169.254` / `fd00:ec2::254`）与 link-local。
     * **即使端点显式允许内网也拒绝** —— 这是 SSRF 拿云凭据的标准路径，
     * 没有任何合法的 agent 用途需要访问它。
     */
    METADATA_ADDRESS,

    /**
     * URL 里带 userinfo（`http://user:pass@host/`）。
     *
     * 拒绝理由不是「明文传密码」（那由 scheme 判定管），而是**解析器歧义**：
     * `http://127.0.0.1@evil.com/` 的 host 是 `evil.com`，但人眼（以及某些宽松解析器）
     * 会读成 `127.0.0.1`。带 userinfo 的端点 URL 没有任何合法用途，直接拒。
     */
    CREDENTIALS_IN_URL,
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
 * 0. URL 带 userinfo ⇒ 拒绝（先于一切 host 逻辑）；
 * 1. scheme 只允许 `https` / `http`；
 * 2. 云元数据地址与 link-local（`169.254.0.0/16`、`fe80::/10`）**永远拒绝**；
 * 3. loopback 与模拟器宿主 `10.0.2.2` 允许（本地 Ollama / vLLM 是明确支持的场景）；
 * 4. 其余私有网段（RFC1918 / ULA / CGNAT / `0.0.0.0`）仅在端点 `allowPrivateNetwork = true` 时允许；
 * 5. 明文 `http` 指向公网主机仅在 `allowPrivateNetwork = true` 时允许（默认 fail-closed）。
 *
 * ## 为什么解析器换成 `okhttp3.HttpUrl`
 *
 * 判定与**实际连接**必须用同一个解析器。旧实现用 `java.net.URI` 判、OkHttp 连，
 * 两者对反斜杠、多个 `@`、非十进制 IP 的处理并不一致 —— 差异本身就是绕过通道
 * （`http://a.com\@b.com/` 在 URI 眼里是一回事，在 HttpUrl 眼里是另一回事）。
 * 现在两边同源：`HttpUrl` 认为的 host 就是我们判定的 host，也就是真正会连的 host。
 *
 * ## 非十进制 IP 的真实行为（2026-10-29 实测 okhttp 4.12.0）
 *
 * `HttpUrl` **不会**把 `2130706433` / `0x7f.1` / `127.1` 规范化成点分十进制
 * （实测 host 原样保留）。所以这类绕过面必须在本类自己兜：见 [parseLooseIpv4]
 * —— 按 `inet_aton` 的 1~4 段规则把数字形态还原成 32 位地址再判定。
 * 而 `[::ffff:192.168.1.1]` 实测会被规范化成 `192.168.1.1`，与点分十进制同路。
 *
 * ## 已知残余风险
 *
 * 本类只看**字面量**主机名 / IP。公网域名解析到内网地址（DNS rebinding）由
 * [HttpTransport] 的 `GuardedDns` 在**解析后**用 [checkResolvedIp] 兜第二道，
 * 两道闸共用同一套 IP 判据（`04-risk-register.md` R7）。
 */
object UrlGuard {

    private const val METADATA_IPV4 = "169.254.169.254"
    private const val METADATA_IPV6 = "fd00:ec2::254"

    /** [METADATA_IPV4] 的 32 位形态，用于十进制/十六进制字面量（如 `2852039166`）的比对。 */
    private const val METADATA_IPV4_VALUE: Long = 0xA9FEA9FEL

    /** 模拟器访问宿主机 loopback 的固定地址（Android 官方约定）。 */
    private const val EMULATOR_HOST = "10.0.2.2"

    /**
     * 「形似 IPv4」的字符串：1~4 段，每段是十进制数字或 `0x` 十六进制。
     *
     * 用途只有一个：**解析失败时 fail-closed**。`08.1.1.1` 这类在 Java 里按十进制、
     * 在 libc 里按八进制解释的写法必须落到「内网」而不是「公网域名」，
     * 否则解析器差异就成了绕过通道。注意不能放宽到 `[0-9a-f.]` ——
     * `cafe.com` / `abc.be` 这类正常域名会被误判成内网（假红比漏报更伤守卫）。
     */
    private val LOOSE_IPV4_SHAPE =
        Regex("^([0-9]+|0[xX][0-9a-fA-F]+)(\\.([0-9]+|0[xX][0-9a-fA-F]+)){0,3}$")

    /** IPv6 字面量允许的字符集。用它挡「不是字面量却进了 getByName」的解析（避免触发真 DNS）。 */
    private val IPV6_LITERAL_CHARS = Regex("^[0-9a-f:.]+$")

    /** 只用于「解析失败时能不能认出 scheme」这一件事。 */
    private val SCHEME_PREFIX = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):")

    private enum class HostKind { LOOPBACK, METADATA, LINK_LOCAL, PRIVATE, PUBLIC }

    fun check(url: String, allowPrivateNetwork: Boolean = false): UrlVerdict {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return UrlVerdict.Rejected(UrlRejectReason.MALFORMED, "empty url")
        }
        // 主解析器 = 传输层解析器（OkHttp）。解析失败时只剩「scheme 能不能识别」这一个信息。
        val parsed = trimmed.toHttpUrlOrNull()
            ?: return UrlVerdict.Rejected(rejectReasonForUnparsable(trimmed), "unparsable url")

        // 凭据走私必须先判，早于任何 host 逻辑：带 userinfo 的 URL 一律拒绝。
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return UrlVerdict.Rejected(UrlRejectReason.CREDENTIALS_IN_URL, "credentials in url")
        }

        val scheme = parsed.scheme.lowercase()
        if (scheme != "http" && scheme != "https") {
            return UrlVerdict.Rejected(UrlRejectReason.NOT_HTTP_SCHEME, "scheme=$scheme")
        }

        val host = parsed.host.lowercase()
        if (host.isEmpty()) {
            return UrlVerdict.Rejected(UrlRejectReason.MALFORMED, "missing host")
        }

        // 模拟器宿主：本地 Ollama / vLLM 的默认落点，明文 http 也放行（与 loopback 同等对待）。
        if (host == EMULATOR_HOST) return UrlVerdict.Allowed(trimmed)

        when (classifyHost(host)) {
            // 元数据 / link-local：无条件拒绝，allowPrivateNetwork 也不解锁。
            HostKind.METADATA, HostKind.LINK_LOCAL ->
                return UrlVerdict.Rejected(
                    UrlRejectReason.METADATA_ADDRESS,
                    "metadata/link-local host=$host",
                )

            HostKind.LOOPBACK -> return UrlVerdict.Allowed(trimmed)

            HostKind.PRIVATE -> if (!allowPrivateNetwork) {
                return UrlVerdict.Rejected(UrlRejectReason.PRIVATE_NETWORK, "private host=$host")
            }

            HostKind.PUBLIC, null -> Unit
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

    /**
     * 对**已经解析出来的 IP** 做同一套判定（`HttpTransport` 的 `GuardedDns` 用）。
     *
     * @param allowLoopback 字面量 URL 的 loopback 是白名单（本地服务）；
     *   但**域名解析到 loopback** 是 rebinding 的典型形态，调用方传 `false` 收紧。
     */
    internal fun checkResolvedIp(
        ip: String,
        allowPrivateNetwork: Boolean,
        allowLoopback: Boolean = true,
    ): UrlVerdict {
        val normalized = ip.trim().lowercase()
        if (normalized.isEmpty()) {
            return UrlVerdict.Rejected(UrlRejectReason.MALFORMED, "empty ip")
        }
        return when (classifyHost(normalized)) {
            HostKind.METADATA, HostKind.LINK_LOCAL ->
                UrlVerdict.Rejected(UrlRejectReason.METADATA_ADDRESS, "metadata/link-local ip=$normalized")

            HostKind.LOOPBACK -> if (allowLoopback || allowPrivateNetwork) {
                UrlVerdict.Allowed(normalized)
            } else {
                UrlVerdict.Rejected(UrlRejectReason.PRIVATE_NETWORK, "loopback ip=$normalized")
            }

            HostKind.PRIVATE -> if (allowPrivateNetwork) {
                UrlVerdict.Allowed(normalized)
            } else {
                UrlVerdict.Rejected(UrlRejectReason.PRIVATE_NETWORK, "private ip=$normalized")
            }

            HostKind.PUBLIC -> UrlVerdict.Allowed(normalized)

            null -> UrlVerdict.Rejected(UrlRejectReason.MALFORMED, "not an ip literal: $normalized")
        }
    }

    /** 是否是 IP 字面量（含 `localhost`）。DNS 层据此区分「上游已裁决过的字面量」与「真域名」。 */
    internal fun isIpLiteral(host: String): Boolean {
        val normalized = host.trim().lowercase()
        if (normalized == "localhost") return true
        if (parseLooseIpv4(normalized) != null) return true
        return normalized.contains(':') && IPV6_LITERAL_CHARS.matches(normalized)
    }

    /** 解析失败时的兜底分类：能识别出非 http(s) scheme 就报 [UrlRejectReason.NOT_HTTP_SCHEME]。 */
    private fun rejectReasonForUnparsable(raw: String): UrlRejectReason {
        val scheme = SCHEME_PREFIX.find(raw)?.groupValues?.get(1)?.lowercase()
            ?: return UrlRejectReason.MALFORMED
        return if (scheme == "http" || scheme == "https") {
            UrlRejectReason.MALFORMED
        } else {
            UrlRejectReason.NOT_HTTP_SCHEME
        }
    }

    /** @return null = 不是 IP 字面量（按域名处理）。 */
    private fun classifyHost(host: String): HostKind? {
        if (host == "localhost") return HostKind.LOOPBACK
        parseLooseIpv4(host)?.let { return classifyIpv4(it) }
        if (host.contains(':')) {
            // 解析不了的 IPv6 字面量按内网处理（fail-closed），不当成域名。
            return classifyIpv6(host) ?: HostKind.PRIVATE
        }
        // 形似 IPv4 但解析失败（如 `08.1.1.1`）：十进制 / 八进制解释不一致，一律按内网。
        if (LOOSE_IPV4_SHAPE.matches(host)) return HostKind.PRIVATE
        return null
    }

    /**
     * 把 `inet_aton` 接受的 1~4 段数字形态还原成 32 位地址。
     *
     * 规则：段数 n<4 时，最后一段填充剩余字节（`127.1` = `127.0.0.1`，
     * `2130706433` = `127.0.0.1`）。**不支持前导零的八进制**：Java 按十进制、
     * libc 按八进制，两种解释会给出不同地址，故这类输入直接判「解析失败」，
     * 由 [classifyHost] 落到内网（fail-closed）。
     */
    private fun parseLooseIpv4(host: String): Long? {
        val parts = host.split('.')
        if (parts.size !in 1..4) return null
        val values = LongArray(parts.size)
        for (i in parts.indices) {
            values[i] = parseIpv4Part(parts[i]) ?: return null
        }
        return when (parts.size) {
            1 -> values[0].takeIf { it <= 0xFFFFFFFFL }
            2 -> if (values[0] <= 0xFFL && values[1] <= 0xFFFFFFL) {
                (values[0] shl 24) or values[1]
            } else {
                null
            }

            3 -> if (values[0] <= 0xFFL && values[1] <= 0xFFL && values[2] <= 0xFFFFL) {
                (values[0] shl 24) or (values[1] shl 16) or values[2]
            } else {
                null
            }

            else -> if (values.all { it <= 0xFFL }) {
                (values[0] shl 24) or (values[1] shl 16) or (values[2] shl 8) or values[3]
            } else {
                null
            }
        }
    }

    private fun parseIpv4Part(part: String): Long? {
        if (part.isEmpty()) return null
        val value = when {
            part.startsWith("0x", ignoreCase = true) -> part.substring(2).toLongOrNull(16)
            // 前导零：八进制 / 十进制歧义，交给 fail-closed 分支。
            part.length > 1 && part[0] == '0' -> null
            else -> part.toLongOrNull()
        } ?: return null
        return value.takeIf { it >= 0L }
    }

    private fun classifyIpv4(value: Long): HostKind {
        if (value == 0L) return HostKind.PRIVATE // 0.0.0.0
        val a = ((value ushr 24) and 0xFFL).toInt()
        val b = ((value ushr 16) and 0xFFL).toInt()
        return when {
            value == METADATA_IPV4_VALUE -> HostKind.METADATA
            a == 169 && b == 254 -> HostKind.LINK_LOCAL
            a == 127 -> HostKind.LOOPBACK
            a == 10 -> HostKind.PRIVATE
            a == 172 && b in 16..31 -> HostKind.PRIVATE
            a == 192 && b == 168 -> HostKind.PRIVATE
            a == 100 && b in 64..127 -> HostKind.PRIVATE // CGNAT 100.64.0.0/10，运营商内网同样是内网
            else -> HostKind.PUBLIC
        }
    }

    private fun classifyIpv6(host: String): HostKind? {
        if (host == METADATA_IPV6) return HostKind.METADATA
        // 只允许纯字面量字符集：否则 getByName 会退化成真 DNS 查询（这里不能出网）。
        if (!IPV6_LITERAL_CHARS.matches(host)) return null
        val address = try {
            InetAddress.getByName(host)
        } catch (t: Throwable) {
            return null
        }
        val bytes = address.address
        // IPv4-mapped（`::ffff:192.168.1.1`）在 JVM 上就是 Inet4Address，直接复用 IPv4 判据。
        if (bytes.size == 4) return classifyIpv4(toIpv4Value(bytes))
        return when {
            address.isAnyLocalAddress -> HostKind.PRIVATE // ::
            address.isLoopbackAddress -> HostKind.LOOPBACK
            address.isLinkLocalAddress -> HostKind.LINK_LOCAL
            address.isSiteLocalAddress -> HostKind.PRIVATE
            isUniqueLocal(bytes) -> HostKind.PRIVATE // fc00::/7
            else -> HostKind.PUBLIC
        }
    }

    private fun toIpv4Value(bytes: ByteArray): Long =
        ((bytes[0].toLong() and 0xFFL) shl 24) or
            ((bytes[1].toLong() and 0xFFL) shl 16) or
            ((bytes[2].toLong() and 0xFFL) shl 8) or
            (bytes[3].toLong() and 0xFFL)

    private fun isUniqueLocal(bytes: ByteArray): Boolean =
        bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
}
