package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.agent.AgentRequest
import com.rickeal.liquidharness.core.agent.AgentRunner
import com.rickeal.liquidharness.core.engine.RemoteLlmEngine
import com.rickeal.liquidharness.core.engine.RetryPolicy
import com.rickeal.liquidharness.core.engine.provider.OpenAiCompatibleAdapter
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.net.HttpTransport
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 首字节后截断 → **整段回滚 + 重试一次**的端到端判据（W24-02）。
 *
 * ## 为什么这 2 条必须落在 `:core-data`
 *
 * 判据要同时成立三件事：**真 HTTP**（两条断流通路）、**真内核循环**（回滚时序）、
 * **真引擎**（请求计数）。`:core-engine` 看不到内核、`:core-agent` 只有 `FakeEngine`；
 * 本模块 `api(:core-agent)` + `api(:core-engine)`（`build.gradle.kts` 的既有依赖），
 * 是唯一能同时 `new AgentRunner` + `new RemoteLlmEngine` 的 JVM 测试位。
 *
 * ## 断流夹具 = JDK `HttpServer`
 *
 * 首次请求回 `data: {"choices":[{"delta":{"content":"Hel"}}]}` 后直接关流（无 `[DONE]`、
 * 无 `finish_reason`）⇒ 引擎报 `truncatedStream`。**不引 MockWebServer**：本仓零依赖该库，
 * 加测试依赖会破「测试面与生产面同构」。判据是「200 + delta 后关流 ⇒ 请求数 / 终态唯一」，
 * 与派单卡里「MockWebServer」的代称等价。
 *
 * ## 两条判据的分工
 *
 * | 用例 | 形态 | 期望 |
 * |---|---|---|
 * | D1 | 第 1 次截断、第 2 次正常收尾 | `hits == 2`；终态**唯一**且是 `Finished(STOP)`（不是 `LENGTH`）；`Finished.text == 第 2 段` |
 * | D2 | 两次都截断 | `hits == 2`；终态**唯一**是 `Failed`，零 `Finished`；`StreamReset` 恰 1 个（放弃前不回滚） |
 */
class StreamTruncationRetryTest {

    private val firstSegment = "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n"
    private val secondSegment = "data: {\"choices\":[{\"delta\":{\"content\":\"World\"}}]}\n\n"

    @Test
    fun truncatedThenCompleteEndsWithOneFinishedCarryingOnlyTheSecondSegment() {
        withScriptedSseServer(
            scripts = listOf(
                firstSegment,
                secondSegment + "data: [DONE]\n\n",
            )
        ) { port, hits, _ ->
            val events = runOnce(port)

            assertEquals(2, hits.get(), "截断必须恰好重试一次（共两次 HTTP 请求）")
            val terminals = events.filter {
                it is AgentEvent.Finished || it is AgentEvent.Failed
            }
            assertEquals(1, terminals.size, "终态必须唯一：events=$events")
            val finished = terminals.single() as? AgentEvent.Finished
            assertTrue(finished != null, "重试成功后必须是 Finished，events=$events")
            assertEquals(
                com.rickeal.liquidharness.core.model.TerminationReason.ModelStopped,
                finished!!.terminatedBy,
                "重试成功是正常结束（不是 MaxRounds / Breaker）",
            )
            assertEquals(null, finished.report, "不带截断诊断卡 ⇒ 不是 LENGTH 收尾")
            assertEquals(
                "World",
                finished.text,
                "Finished(text) 只能是第二段（第一段已被回滚）：events=$events",
            )
            // 回滚之后才允许出现第二段：`StreamReset` 之前的增量属于被丢弃的那一段。
            val resetIndex = events.indexOfFirst { it is AgentEvent.StreamReset }
            assertTrue(resetIndex >= 0, "截断回滚必须发 StreamReset：events=$events")
            val afterReset = events.drop(resetIndex + 1)
                .filterIsInstance<AgentEvent.TextDelta>()
                .joinToString("") { it.text }
            assertEquals("World", afterReset, "StreamReset 之后只应有第二段：events=$events")
        }
    }

    @Test
    fun bothAttemptsTruncatedEndsWithSingleFailureAndNoFinished() {
        withScriptedSseServer(
            scripts = listOf(firstSegment, secondSegment)
        ) { port, hits, _ ->
            val events = runOnce(port)

            assertEquals(2, hits.get(), "预算内只重试一次（不许打第三次）")
            val failures = events.filterIsInstance<AgentEvent.Failed>()
            assertEquals(1, failures.size, "终态失败必须恰好一个：events=$events")
            assertEquals(
                0,
                events.count { it is AgentEvent.Finished },
                "两次都断不许落 Finished（半截回答不得当完整回答）：events=$events",
            )
            assertEquals(
                1,
                events.count { it is AgentEvent.StreamReset },
                "只有第一次回滚；放弃路径不回滚：events=$events",
            )
        }
    }

    // ------------------------------------------------------------------ 夹具

    private fun endpointFor(port: Int): RemoteEndpoint = RemoteEndpoint(
        id = "ep-truncation",
        name = "truncation",
        baseUrl = "http://127.0.0.1:$port/v1",
        modelId = "test-model",
        contextLength = 1_048_576,
        maxOutputTokens = 4_096,
        allowPrivateNetwork = true,
    )

    /** 真引擎 + 真内核；`RetryPolicy(maxAttempts = 1)` 关掉**引擎内**重试。 */
    private fun runOnce(port: Int): List<AgentEvent> {
        val endpoint = endpointFor(port)
        val engine = RemoteLlmEngine(
            endpoint = endpoint,
            adapter = OpenAiCompatibleAdapter(),
            transport = HttpTransport(),
            retryPolicy = RetryPolicy(maxAttempts = 1, jitterRatio = 0.0),
        )
        // `RemoteLlmEngine` 是「签名对齐但未 implements 端口」的过渡态 ⇒ 必须过装配层的桥
        // （`:core-engine` 不能 import `:core-agent`，见 RemoteEngineBridge 类头）。
        val runner = AgentRunner(engine = RemoteEngineBridge(engine))
        return runBlocking {
            runner.run(AgentRequest(userInput = "hi", endpoint = endpoint)).toList()
        }
    }

    /**
     * 按 `hits` 顺序回放脚本；脚本用尽后重复最后一条。
     *
     * 每次响应都是 `200` + `text/event-stream` + 写完后**直接关流**（不补 `[DONE]`）——
     * 这正是「首字节后干净 EOF」的形态。第 2 条脚本自带 `[DONE]`，模拟服务端恢复正常。
     */
    private fun <T> withScriptedSseServer(
        scripts: List<String>,
        block: (port: Int, hits: AtomicInteger, server: HttpServer) -> T,
    ): T {
        val hits = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange: HttpExchange ->
            val index = hits.getAndIncrement().coerceAtMost(scripts.lastIndex)
            val payload = scripts[index].toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/event-stream; charset=utf-8")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                out.write(payload)
                out.flush()
            }
            exchange.close()
        }
        server.start()
        return try {
            block(server.address.port, hits, server)
        } finally {
            server.stop(0)
        }
    }
}
