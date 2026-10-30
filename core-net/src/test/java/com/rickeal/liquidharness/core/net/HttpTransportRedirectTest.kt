package com.rickeal.liquidharness.core.net

import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 重定向不跟随的**往返**证据（外审 P1）。
 *
 * 缺陷形态：`UrlGuard.check` 只在请求入口对用户填的 baseUrl 判一次，`GuardedDns`
 * 又对字面量 host 早退 —— 若 OkHttp 跟随 302，跳转后的新 URL 两道闸都不过，
 * 攻击链是「合法域名 + 302 → `169.254.169.254`」读回云元数据。
 *
 * 这里用 `java.net.ServerSocket` 起一个**只回 302** 的本地 server，让
 * [HttpTransport.post] 真的走一遍 TCP：若实现跟随重定向，本用例会去连
 * `169.254.169.254`（连不上就抛 IOException，连得上则状态码不是 302），两种都判红。
 * 因此这是往返证据，不是结构断言。
 */
class HttpTransportRedirectTest {

    /** 302 的目标故意选云元数据地址：它是两条闸都拦不住的那种 host（字面量 IP）。 */
    @Test
    fun redirectToMetadataAddressIsNotFollowed() {
        ServerSocket(0).use { server ->
            val worker = thread(isDaemon = true, name = "redirect-302-server") {
                serveOneRedirect(server, METADATA_URL)
            }
            val stream = HttpTransport().post(
                url = "http://127.0.0.1:${server.localPort}/v1/chat/completions",
                headers = emptyMap(),
                body = "{}",
            )
            stream.use {
                assertEquals(302, it.statusCode)
                assertFalse(it.isSuccessful)
            }
            worker.join(SERVER_JOIN_MILLIS)
            assertFalse(worker.isAlive, "302 已回完，本地 server 线程不应还挂着")
        }
    }

    /** 非流式 client 同一策略：它服务 MCP 的单次 JSON 调用，同样不需要重定向。 */
    @Test
    fun defaultClientDoesNotFollowRedirects() {
        val client = HttpTransport.defaultClient()
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    /**
     * 接受**一次**连接并回 302。只读请求头（不读 body 会让内核发 RST，OkHttp 可能在
     * 读到状态行前就报错），读完 `Content-Length` 声明的字节后再写响应。
     */
    private fun serveOneRedirect(server: ServerSocket, location: String) {
        runCatching {
            server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                var contentLength = 0
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                    }
                }
                repeat(contentLength) { input.read() }

                val head = "HTTP/1.1 302 Found\r\n" +
                    "Location: $location\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
                val output = socket.getOutputStream()
                output.write(head.toByteArray(Charsets.US_ASCII))
                output.flush()
            }
        }
    }

    private companion object {
        const val METADATA_URL = "http://169.254.169.254/latest/meta-data/"
        const val SERVER_JOIN_MILLIS = 5_000L
    }
}
