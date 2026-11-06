package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.model.EngineErrorKind
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.ProviderKind
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.net.HttpTransport
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.flowOf
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

    // ---------------------------------------------------------------- W9-00 P1-5 事件序列

    /** 关思考序列：只有 ReasoningDelta + Finished ⇒ 零内容证据，必须失败。 */
    @Test
    fun reasoningOnlySequenceFailsProbe() {
        val events = listOf(
            GenerationEvent.ReasoningDelta("thinking"),
            GenerationEvent.Finished(FinishReason.STOP),
        )
        assertEquals(EMPTY_STREAM_DETAIL, judgeProbeSequence(events))
    }

    /** 开思考序列：有 TextDelta ⇒ 通过；且 Finished 之后同帧的 Usage 也算证据。 */
    @Test
    fun contentSequencePassesProbeIncludingTrailingUsage() {
        assertNull(judgeProbeSequence(listOf(GenerationEvent.TextDelta("p"))))
        // 关键反例：decodeEvent 同帧先 Finished 后 Usage，Usage 不得被 firstOrNull 吞掉。
        assertNull(
            judgeProbeSequence(
                listOf(
                    GenerationEvent.Finished(FinishReason.STOP),
                    GenerationEvent.Usage(10, 5, 0),
                ),
            ),
        )
    }

    // ---------------------------------------------------------------- W9-01 P1-2 收束

    /**
     * 终帧之后泛滥的 `TextDelta` 必须被丢弃：旧实现 `takeWhile` 收至终帧且终帧后仍放行
     * `TextDelta` ⇒ 全收；异常 / 重放流会拖垮探针（token / 延迟回归）。
     *
     * **红态自证**：把 `collectProbeEvents` 改回「收至终帧 + 终帧后放行 TextDelta」的
     * `takeWhile`，本条必红（最后一帧不再是 `Finished`，且 size 远超上限）。
     */
    @Test
    fun probeStopsCollectingAfterTerminalFrame() {
        val stream = flowOf(
            *Array(8) { GenerationEvent.TextDelta("a") },
            GenerationEvent.Finished(FinishReason.STOP),
            *Array(200) { GenerationEvent.TextDelta("b") },
        )
        val events = runBlocking { collectProbeEvents(stream) }

        // 终帧必须被收到，且是序列的最后一帧（其后泛滥的 delta 被丢弃）。
        assertTrue(events.any { it is GenerationEvent.Finished })
        assertTrue(events.last() is GenerationEvent.Finished, "events.last()=" + events.last())
        // 终帧之后只应留极少事件（最多一格 Usage），绝不应把后续 200 个 delta 收进来。
        assertTrue(events.size <= 32, "终帧后只应留极少事件，events=" + events.size)
    }

    /** 终帧同帧的 Usage 仍然被接纳（判据不因收紧而假红）。 */
    @Test
    fun probeStillCollectsTrailingUsageAfterTerminal() {
        val stream = flowOf(
            GenerationEvent.TextDelta("p"),
            GenerationEvent.Finished(FinishReason.STOP),
            GenerationEvent.Usage(10, 5, 0),
        )
        val events = runBlocking { collectProbeEvents(stream) }

        assertTrue(events.any { it is GenerationEvent.Usage }, "终帧后的 Usage 必须被接纳")
        assertEquals(FinishReason.STOP, (events.single { it is GenerationEvent.Finished } as GenerationEvent.Finished).reason)
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

    /**
     * W9-00 P1-5 运行时反例：finish_reason 与 usage **同一帧**、无正文。
     * 旧实现 `firstOrNull` 在 Finished 处就取消上游 ⇒ Usage 不可达 ⇒ 假红。
     */
    @Test
    fun usageInSameFrameAsFinishReasonPassesTheProbe() {
        val body = "data: {\"choices\":[{\"finish_reason\":\"stop\"}]," +
            "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}\n\n" +
            "data: [DONE]\n\n"
        withServer("text/event-stream; charset=utf-8", body) { port ->
            val outcome = runBlocking { EndpointProbe(HttpTransport()).test(endpointFor(port), null) }
            assertTrue(outcome.ok, "同帧 Usage 必须可达，detail=" + outcome.detail)
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
