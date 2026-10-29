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
}
