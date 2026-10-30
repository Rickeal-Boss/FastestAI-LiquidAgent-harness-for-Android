package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.model.EngineErrorKind
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.ProviderKind
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.net.HttpTransport
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 「测试连接」判据的单测。
 *
 * 事故背景：把「收到 `Finished`」当通过 ⇒ 一个用 HTTP 200 返回 HTML 错误页的网关
 * 会让 probe 报绿（零事件 → 引擎补 `Finished(STOP)`），用户带着坏配置进对话页。
 *
 * 两层判据都测：
 * 1. [judgeProbe] 纯函数（确定性，不依赖网络）；
 * 2. 真 HTTP（JDK 自带 `HttpServer`，绑 127.0.0.1:0）：200 + 非 SSE 体必须判失败，
 *    200 + 合法 SSE 帧必须判通过（防「收紧过头，所有端点都报红」）。
 */
class EndpointProbeTest {

    // ---------------------------------------------------------------- 纯函数判据

    /** 只有 Finished、零内容 = 空流，必须判失败。 */
    @Test
    fun finishedWithoutAnyContentIsFailure() {
        val detail = judgeProbe(GenerationEvent.Finished(FinishReason.STOP), sawContent = false)
        assertEquals(EMPTY_STREAM_DETAIL, detail)
    }

    /** 见过内容再收到 Finished：通过。 */
    @Test
    fun finishedAfterContentPasses() {
        assertNull(judgeProbe(GenerationEvent.Finished(FinishReason.STOP), sawContent = true))
    }

    /** 失败事件：保留错误类，不受内容判据影响。 */
    @Test
    fun failedTerminalKeepsItsKind() {
        val failed = GenerationEvent.Failed(EngineErrorKind.PROTOCOL, "boom", retryable = false)
        assertEquals("PROTOCOL: boom", judgeProbe(failed, sawContent = true))
    }

    // ---------------------------------------------------------------- 真 HTTP

    /** 200 + text/html：零 SSE 事件 ⇒ ok=false，detail 指向空流。 */
    @Test
    fun htmlPageReturnedWith200FailsTheProbe() {
        val body = "<html><body>gateway error</body></html>"
        withServer("text/html; charset=utf-8", body) { port ->
            val outcome = runBlocking { EndpointProbe(HttpTransport()).test(endpointFor(port), null) }
            assertFalse(outcome.ok, "HTML 200 必须判失败，detail=" + outcome.detail)
            assertTrue(outcome.detail.contains("PROTOCOL"), "detail=" + outcome.detail)
        }
    }

    /** 200 + text/event-stream 且带一条正文增量：必须判通过（防止收紧成假红）。 */
    @Test
    fun validSseStreamPasses() {
        val body = "data: {\"choices\":[{\"delta\":{\"content\":\"p\"}}]}\n\n" +
            "data: [DONE]\n\n"
        withServer("text/event-stream; charset=utf-8", body) { port ->
            val outcome = runBlocking { EndpointProbe(HttpTransport()).test(endpointFor(port), null) }
            assertTrue(outcome.ok, "合法 SSE 必须判通过，detail=" + outcome.detail)
        }
    }

    private fun endpointFor(port: Int): RemoteEndpoint = RemoteEndpoint(
        id = "probe-test",
        name = "probe",
        kind = ProviderKind.OPENAI_COMPATIBLE,
        baseUrl = "http://127.0.0.1:$port/v1",
        modelId = "test-model",
        supportsTools = false,
    )

    /** 起一个只回一条固定响应的本地服务（绑 127.0.0.1 的随机端口），用完即停。 */
    private fun <T> withServer(contentType: String, body: String, block: (Int) -> T): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return try {
            block(server.address.port)
        } finally {
            server.stop(0)
        }
    }
}
