package com.rickeal.liquidharness.core.engine

import com.rickeal.liquidharness.core.engine.provider.OpenAiCompatibleAdapter
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.EngineErrorKind
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.net.HttpTransport
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 超窗 400 → `GenerationEvent.Failed.contextLimitHint` 的端到端判据（W5-02 / T6 第一跳）。
 *
 * ## 为什么必须走真 HTTP，而不是「假 adapter 返回 400」
 *
 * 任务书写的是「假 adapter」，但那样测的只是假 adapter 自己的行为 ——
 * 真实链路是 `RemoteLlmEngine.runAttempt` 拿 `stream.errorBodyText()`，
 * 再交给 `classifyHttpFailure` 分类。**解析器与接线点是两个东西**，
 * 只测解析器会漏掉「解析出来没人塞进事件」这条最可能的断点（本仓第 4 例
 * 「声明了没消费者」的同款形态）。
 *
 * 这里用 JDK 自带 `HttpServer` 绑 `127.0.0.1:0`：零网络、零依赖，
 * 与 `EndpointProbeTest` 同一个套路。
 */
class RemoteLlmEngineContextLimitTest {

    @Test
    fun overflow400CarriesTheRealWindowIntoTheFailureEvent() {
        val body = """{"error":{"message":"This model's maximum context length is 1000000 tokens. """ +
            """However, you requested 1200000 tokens"}}"""
        withServer(400, body) { port ->
            val endpoint = endpointFor(port)
            val engine = RemoteLlmEngine(
                endpoint = endpoint,
                adapter = OpenAiCompatibleAdapter(),
                transport = HttpTransport(),
            )
            val events = runBlocking {
                engine.generate(
                    GenerationRequest(
                        messages = listOf(ChatMessage(role = Role.USER, text = "hi")),
                        endpoint = endpoint,
                    )
                ).toList()
            }

            val failed = events.filterIsInstance<GenerationEvent.Failed>().single()
            // 分类与窗口是两条判据：前者说「为什么失败」，后者说「真实上限是多少」。
            assertEquals(EngineErrorKind.CONTEXT_OVERFLOW, failed.kind)
            assertEquals(1_000_000, assertNotNull(failed.contextLimitHint))
        }
    }

    /** 普通 400（不含超窗文案）不许带 hint —— 否则会把别的错误写成窗口。 */
    @Test
    fun ordinary400CarriesNoWindowHint() {
        withServer(400, """{"error":{"message":"invalid request: unknown field"}}""") { port ->
            val endpoint = endpointFor(port)
            val engine = RemoteLlmEngine(endpoint, OpenAiCompatibleAdapter(), HttpTransport())
            val events = runBlocking {
                engine.generate(
                    GenerationRequest(
                        messages = listOf(ChatMessage(role = Role.USER, text = "hi")),
                        endpoint = endpoint,
                    )
                ).toList()
            }

            val failed = events.filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(null, failed.contextLimitHint)
        }
    }

    private fun endpointFor(port: Int): RemoteEndpoint = RemoteEndpoint(
        id = "ep-1m",
        name = "1m",
        baseUrl = "http://127.0.0.1:$port/v1",
        modelId = "test-model",
        contextLength = 1_048_576,
        maxOutputTokens = 4_096,
        supportsTools = false,
        // 回环地址必须显式放行（`UrlGuard` fail-closed）。
        allowPrivateNetwork = true,
    )

    private fun <T> withServer(status: Int, body: String, block: (Int) -> T): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
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
