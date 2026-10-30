package com.rickeal.liquidharness.core.net

import okhttp3.Dns
import java.io.IOException
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * DNS rebinding 第二道闸的单测。
 *
 * 全部用**假 Dns**（固定返回地址）驱动，不连网、不查真 DNS ——
 * 否则用例的成败会取决于 CI 所在网络的 DNS 行为。
 */
class GuardedDnsTest {

    private fun dnsOf(vararg ips: String): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = ips.map { InetAddress.getByName(it) }
    }

    /** 域名解析到 RFC1918 且端点未允许：必须抛 IOException（连接前就断）。 */
    @Test
    fun privateResolvedAddressIsBlockedByDefault() {
        val dns = GuardedDns(allowPrivateNetwork = false, delegate = dnsOf("192.168.1.5"))
        try {
            dns.lookup("example.com")
            fail("expected IOException for private resolved address")
        } catch (e: IOException) {
            // 判据：错误信息带得上原因，便于日志排查（不含 URL 里的密钥）。
            assertTrue(e.message?.contains("PRIVATE_NETWORK") == true)
        }
    }

    /** 公网地址：放行，且返回的地址列表原样透传（不能吞掉任何一个地址）。 */
    @Test
    fun publicResolvedAddressPassesThrough() {
        val dns = GuardedDns(allowPrivateNetwork = false, delegate = dnsOf("1.2.3.4"))
        val resolved = dns.lookup("example.com")
        assertEquals(1, resolved.size)
        assertEquals("1.2.3.4", resolved[0].hostAddress)
    }

    /** 端点显式允许内网：放行（局域网 vLLM / Ollama 场景）。 */
    @Test
    fun privateResolvedAddressPassesWhenEndpointOptsIn() {
        val dns = GuardedDns(allowPrivateNetwork = true, delegate = dnsOf("192.168.1.5"))
        assertEquals(1, dns.lookup("ollama.lan").size)
    }

    /** 云元数据：allowPrivateNetwork = true 也不放行。 */
    @Test
    fun metadataResolvedAddressIsAlwaysBlocked() {
        val dns = GuardedDns(allowPrivateNetwork = true, delegate = dnsOf("169.254.169.254"))
        try {
            dns.lookup("metadata.example.com")
            fail("expected IOException for metadata resolved address")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("METADATA_ADDRESS") == true)
        }
    }

    /** 域名解析到 loopback = rebinding 的典型形态：默认拒。 */
    @Test
    fun loopbackResolvedFromDomainIsBlocked() {
        val dns = GuardedDns(allowPrivateNetwork = false, delegate = dnsOf("127.0.0.1"))
        try {
            dns.lookup("rebind.example.com")
            fail("expected IOException for loopback resolved address")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("PRIVATE_NETWORK") == true)
        }
    }

    /**
     * 字面量 host 跳过 DNS 判定：`10.0.2.2` / `127.0.0.1` 是 UrlGuard 的白名单，
     * 在 DNS 层再判一次会把预置 Ollama 端点自己拦死（回归守卫）。
     */
    @Test
    fun literalHostIsNotRecheckedAtDnsLayer() {
        val dns = GuardedDns(allowPrivateNetwork = false, delegate = dnsOf("10.0.2.2"))
        assertEquals(1, dns.lookup("10.0.2.2").size)
        assertEquals(1, dns.lookup("127.0.0.1").size)
    }
}
