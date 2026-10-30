package com.rickeal.liquidharness.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * SSRF 第一道闸的单测。
 *
 * 判据不是「能拦住所有 SSRF」（拦不住 DNS rebinding，见 UrlGuard KDoc），
 * 而是「每一条放行都必须有明确理由，默认拒绝」。
 */
class UrlGuardTest {

    /** https 公网端点：放行。 */
    @Test
    fun httpsPublicEndpointIsAllowed() {
        val verdict = UrlGuard.check("https://api.openai.com/v1/chat/completions")
        assertTrue(verdict is UrlVerdict.Allowed)
        assertEquals("https://api.openai.com/v1/chat/completions", verdict.url)
    }

    /** 明文 http 指向 RFC1918 且未显式允许：拒绝（默认 fail-closed）。 */
    @Test
    fun plainHttpToRfc1918IsRejectedByDefault() {
        val verdict = UrlGuard.check("http://192.168.1.10:8000/v1/chat/completions")
        assertTrue(verdict is UrlVerdict.Rejected)
        assertEquals(UrlRejectReason.PRIVATE_NETWORK, verdict.reason)
    }

    /** 端点显式 allowPrivateNetwork = true（局域网 vLLM 场景）：放行。 */
    @Test
    fun privateNetworkIsAllowedWhenEndpointOptsIn() {
        val verdict = UrlGuard.check("http://192.168.1.10:8000/v1", allowPrivateNetwork = true)
        assertTrue(verdict is UrlVerdict.Allowed)
    }

    /** 白名单：loopback 与模拟器宿主 10.0.2.2，即使明文 http 也放行（本地 Ollama）。 */
    @Test
    fun loopbackAndEmulatorHostAreAllowlisted() {
        assertTrue(UrlGuard.check("http://127.0.0.1:11434/v1") is UrlVerdict.Allowed)
        assertTrue(UrlGuard.check("http://10.0.2.2:11434/v1") is UrlVerdict.Allowed)
        assertTrue(UrlGuard.check("http://localhost:8080/v1") is UrlVerdict.Allowed)
        assertTrue(UrlGuard.check("http://[::1]:11434/v1") is UrlVerdict.Allowed)
    }

    /** 云元数据地址：即使 allowPrivateNetwork = true 也必须拒绝。 */
    @Test
    fun metadataAddressIsRejectedEvenWithPrivateNetworkAllowed() {
        val verdict = UrlGuard.check("http://169.254.169.254/latest/meta-data/", allowPrivateNetwork = true)
        assertTrue(verdict is UrlVerdict.Rejected)
        assertEquals(UrlRejectReason.METADATA_ADDRESS, verdict.reason)
    }

    /** 非 http(s) scheme：拒绝（file / ftp / content 都能读到本机数据）。 */
    @Test
    fun nonHttpSchemeIsRejected() {
        val verdict = UrlGuard.check("file:///data/data/com.rickeal.liquidharness/databases/x.db")
        assertTrue(verdict is UrlVerdict.Rejected)
        assertEquals(UrlRejectReason.NOT_HTTP_SCHEME, verdict.reason)

        val ftp = UrlGuard.check("ftp://example.com/x")
        assertEquals(UrlRejectReason.NOT_HTTP_SCHEME, (ftp as UrlVerdict.Rejected).reason)
    }

    /** 明文 http 到公网主机：拒绝（密钥与内容会裸奔）。 */
    @Test
    fun plainHttpToPublicHostIsRejected() {
        val verdict = UrlGuard.check("http://api.example.com/v1")
        assertTrue(verdict is UrlVerdict.Rejected)
        assertEquals(UrlRejectReason.INSECURE_SCHEME, verdict.reason)
    }

    /** 空串 / 无 host：拒绝而不是抛异常（配置页可能随时传空）。 */
    @Test
    fun blankOrHostlessUrlIsRejected() {
        assertEquals(UrlRejectReason.MALFORMED, (UrlGuard.check("") as UrlVerdict.Rejected).reason)
        assertEquals(UrlRejectReason.MALFORMED, (UrlGuard.check("   ") as UrlVerdict.Rejected).reason)
    }

    /** requireAllowed 在不安全时抛 UnsafeUrlException，且带上可判定的 reason。 */
    @Test
    fun requireAllowedThrowsWithReason() {
        try {
            UrlGuard.requireAllowed("http://169.254.169.254/")
            fail("expected UnsafeUrlException")
        } catch (e: UnsafeUrlException) {
            assertEquals(UrlRejectReason.METADATA_ADDRESS, e.reason)
        }
    }

    /** https 到 RFC1918 同样要拦 —— SSRF 判据与 scheme 无关。 */
    @Test
    fun httpsToPrivateAddressIsAlsoRejected() {
        val verdict = UrlGuard.check("https://10.1.2.3/v1")
        assertTrue(verdict is UrlVerdict.Rejected)
        assertEquals(UrlRejectReason.PRIVATE_NETWORK, verdict.reason)
    }

    /** userinfo 走私：`http://user:pass@host/` 一律拒（解析器歧义 + 凭据外泄面）。 */
    @Test
    fun credentialsInUrlAreRejected() {
        val verdict = UrlGuard.check("http://user:pass@example.com/v1")
        assertTrue(verdict is UrlVerdict.Rejected)
        assertEquals(UrlRejectReason.CREDENTIALS_IN_URL, verdict.reason)
    }

    /**
     * `http://127.0.0.1@evil.com/` 的真实 host 是 evil.com（人眼会读成 127.0.0.1）。
     * 必须在任何 host 判定之前就按凭据拒掉。
     */
    @Test
    fun userInfoLookingLikeLoopbackIsRejectedBeforeHostLogic() {
        val verdict = UrlGuard.check("http://127.0.0.1@evil.com/v1")
        assertTrue(verdict is UrlVerdict.Rejected)
        assertEquals(UrlRejectReason.CREDENTIALS_IN_URL, verdict.reason)
    }

    /** https 公网 + 无 userinfo：放行（确保凭据判定没有误伤正常 URL）。 */
    @Test
    fun cleanHttpsUrlIsStillAllowed() {
        assertTrue(UrlGuard.check("https://api.openai.com/v1") is UrlVerdict.Allowed)
    }

    /** 模拟器宿主明文 http：放行（预置 Ollama 端点就是它）。 */
    @Test
    fun emulatorHostIsAllowedOnPlainHttp() {
        assertTrue(UrlGuard.check("http://10.0.2.2:11434/v1") is UrlVerdict.Allowed)
    }

    /** 局域网明文：默认拒；端点显式允许后放行。 */
    @Test
    fun lanAddressFollowsTheExplicitOptIn() {
        val rejected = UrlGuard.check("http://192.168.1.50:8000/v1")
        assertEquals(UrlRejectReason.PRIVATE_NETWORK, (rejected as UrlVerdict.Rejected).reason)
        assertTrue(UrlGuard.check("http://192.168.1.50:8000/v1", allowPrivateNetwork = true) is UrlVerdict.Allowed)
    }

    /** 云元数据：即使 allowPrivateNetwork = true 也拒（不带 allowPrivateNetwork 的裸调用同样拒）。 */
    @Test
    fun metadataAddressIsRejectedWithAndWithoutOptIn() {
        val plain = UrlGuard.check("http://169.254.169.254/latest/meta-data")
        assertEquals(UrlRejectReason.METADATA_ADDRESS, (plain as UrlVerdict.Rejected).reason)
    }

    /**
     * 十进制形态的元数据地址（`2852039166` = 169.254.169.254）。
     * 实测 okhttp 4.12.0 的 HttpUrl **不**做规范化（host 原样保留），
     * 所以这条只能靠 UrlGuard 自己的 1~4 段数字还原。
     */
    @Test
    fun decimalEncodedMetadataAddressIsRejected() {
        val verdict = UrlGuard.check("http://2852039166/latest/meta-data", allowPrivateNetwork = true)
        assertEquals(UrlRejectReason.METADATA_ADDRESS, (verdict as UrlVerdict.Rejected).reason)
    }

    /** IPv4-mapped IPv6：HttpUrl 会规范化成 192.168.1.1，判据必须落到 PRIVATE_NETWORK。 */
    @Test
    fun ipv4MappedIpv6IsTreatedAsPrivate() {
        val verdict = UrlGuard.check("http://[::ffff:192.168.1.1]:8000/v1")
        assertEquals(UrlRejectReason.PRIVATE_NETWORK, (verdict as UrlVerdict.Rejected).reason)
    }

    /** `0.0.0.0` / `::`：通配地址按内网处理（连它等于连本机所有接口）。 */
    @Test
    fun wildcardAddressesArePrivate() {
        assertEquals(
            UrlRejectReason.PRIVATE_NETWORK,
            (UrlGuard.check("http://0.0.0.0:8000/v1") as UrlVerdict.Rejected).reason,
        )
        assertEquals(
            UrlRejectReason.PRIVATE_NETWORK,
            (UrlGuard.check("http://[::]:8000/v1") as UrlVerdict.Rejected).reason,
        )
    }

    /** 反斜杠 / 多 @ 这类解析器差异：以 HttpUrl 的 host 为准，且带 userinfo 的必须拒。 */
    @Test
    fun backslashAndExtraAtDoNotSmuggleAHost() {
        val verdict = UrlGuard.check("http://example.com\\@evil.com/v1")
        assertEquals(UrlRejectReason.INSECURE_SCHEME, (verdict as UrlVerdict.Rejected).reason)

        val at = UrlGuard.check("http://a@b@evil.com/v1")
        assertTrue(at is UrlVerdict.Rejected)
    }

    /** checkResolvedIp：DNS 解析结果复用同一套判据。 */
    @Test
    fun resolvedIpUsesTheSameJudgement() {
        assertEquals(
            UrlRejectReason.PRIVATE_NETWORK,
            (UrlGuard.checkResolvedIp("192.168.1.5", allowPrivateNetwork = false) as UrlVerdict.Rejected).reason,
        )
        assertTrue(UrlGuard.checkResolvedIp("192.168.1.5", allowPrivateNetwork = true) is UrlVerdict.Allowed)
        assertEquals(
            UrlRejectReason.METADATA_ADDRESS,
            (UrlGuard.checkResolvedIp("169.254.169.254", allowPrivateNetwork = true) as UrlVerdict.Rejected).reason,
        )
        assertTrue(UrlGuard.checkResolvedIp("1.2.3.4", allowPrivateNetwork = false) is UrlVerdict.Allowed)
    }

    /** 域名解析到 loopback 属于 rebinding 形态：allowLoopback = false 时必须拒。 */
    @Test
    fun resolvedLoopbackIsRejectedForDomainNames() {
        val verdict = UrlGuard.checkResolvedIp("127.0.0.1", allowPrivateNetwork = false, allowLoopback = false)
        assertEquals(UrlRejectReason.PRIVATE_NETWORK, (verdict as UrlVerdict.Rejected).reason)
    }
}
