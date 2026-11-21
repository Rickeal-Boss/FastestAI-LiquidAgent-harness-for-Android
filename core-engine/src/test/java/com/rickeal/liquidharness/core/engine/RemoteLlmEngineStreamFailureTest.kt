package com.rickeal.liquidharness.core.engine

import com.rickeal.liquidharness.core.engine.provider.OpenAiCompatibleAdapter
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.EngineErrorKind
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.net.HttpTransport
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 流失败形态的端到端判据（W24-02）。
 *
 * ## 这一组钉的是什么
 *
 * 「首字节之后」的三条通路以前都落成 `Completed` + 补一个 `Finished(LENGTH)`：
 * 干净 EOF、`IOException`（Content-Length 提前断 / RST）、以及供应商在流里塞的**错误帧**
 * （`event: error` / 顶层 `error` 键）。前者让「半截回答」与「模型正常收尾」在事件序列上
 * 同形；后者更糟 —— 错误帧被 `decodeEvent` 丢掉，于是**一次失败的请求看起来像正常完成**。
 *
 * W24-02 的裁决（方案 B：内核层整段回滚后重试一次）要求引擎只报**事实**：
 * - 有产出的截断 ⇒ `Failed(retryable=true, truncatedStream=true)`，**不补 `Finished`**；
 * - 零事件（HTTP 200 但一帧 SSE 都没解析出来）⇒ `Fatal(PROTOCOL)`，**不可重试**
 *   （否则「端点不是 SSE」会变成 3 次请求 + 两次退避）；
 * - 供应商显式 `finish_reason=length` / `[DONE]` ⇒ 仍然是 `Completed`（这不是截断，
 *   是模型自己撞到输出上限或明确宣告结束）。
 *
 * ## 为什么用真 HTTP（JDK `HttpServer`）而不是假 adapter
 *
 * 判据落在 `runAttempt` 的**分支选择**上（哪条 catch、有没有 emit `Finished`），
 * 而不是 adapter 的解析结果。假 adapter 会把「引擎有没有在正确的位置 break / return」
 * 整段跳过去。JDK `HttpServer` 零依赖（本模块测试依赖只有 junit + kotlin.test），
 * 与 `RemoteLlmEngineContextLimitTest` 同一套路。
 *
 * ⚠️ **不引 MockWebServer**：`core-engine/build.gradle.kts` 没有该依赖，加依赖破 G1
 * 与「测试面与生产面同构」纪律。派单卡里的「MockWebServer」是「真 HTTP 服务器」的代称，
 * 判据是「200 + delta 后关流 ⇒ 请求数 / 终态事件唯一」，机制替换不算注水。
 */
class RemoteLlmEngineStreamFailureTest {

    private val deltaFrame = """data: {"choices":[{"delta":{"content":"Hel"}}]}""" + "\n\n"

    // ------------------------------------------------------------------ E1

    /**
     * E1（先红后绿的那一条）：200 + 一个 delta + 干净 EOF（无 `[DONE]` / 无 finish_reason）。
     *
     * 修复前：`Completed` + 补 `Finished(LENGTH)` —— 用户看到「答到一半突然停了」而事件序列
     * 说这是正常结束。修复后：`Failed(retryable=true, truncatedStream=true)` 且**零 `Finished`**，
     * 引擎自己**不重发**（`hits == 1`）—— 回滚重试只在核心里做。
     */
    @Test
    fun truncatedStreamAfterContentIsRetryableAndCarriesNoFinished() {
        withSseServer(chunks = listOf(deltaFrame), contentType = "text/event-stream") { port, hits ->
            val events = runEngine(port)

            assertEquals(1, hits.get(), "引擎不许自己重发已吐过内容的流（回滚权在内核）")
            assertEquals(
                listOf("Hel"),
                events.filterIsInstance<GenerationEvent.TextDelta>().map { it.text },
                "已收到的增量必须保留（内核回滚前用户还能看到它）：events=" + events,
            )
            val failures = events.filterIsInstance<GenerationEvent.Failed>()
            assertEquals(1, failures.size, "必须恰好一个 Failed（事件序列：$events）")
            val failed = failures.single()
            assertEquals(EngineErrorKind.NETWORK, failed.kind, "failed=$failed")
            assertTrue(failed.retryable, "截断必须报可重试：failed=$failed")
            assertTrue(failed.truncatedStream, "首字节后截断必须置 truncatedStream：failed=$failed")
            assertTrue(
                events.none { it is GenerationEvent.Finished },
                "截断不许补 Finished（否则半截回答被当完整回答落库）：events=" + events,
            )
        }
    }

    // ------------------------------------------------------------------ E2

    /** E2：`[DONE]` 但没有 finish_reason ⇒ 仍是 STOP + `Completed`（服务端显式宣告结束）。 */
    @Test
    fun doneMarkerWithoutFinishReasonStillCompletesAsStop() {
        val body = deltaFrame + "data: [DONE]\n\n"
        withSseServer(chunks = listOf(body), contentType = "text/event-stream") { port, _ ->
            val events = runEngine(port)

            assertTrue(events.none { it is GenerationEvent.Failed }, "events=$events")
            val finished = events.filterIsInstance<GenerationEvent.Finished>().single()
            assertEquals(FinishReason.STOP, finished.reason)
        }
    }

    // ------------------------------------------------------------------ E3

    /**
     * E3：**脏断**（`IOException` 分支）—— 声明 `Content-Length: 9999`，只发 34 字节就关连接。
     *
     * 与 E1 是两条独立通路（干净 EOF vs 读异常）。只修前者的实现会在 FIN/RST 形态上
     * 退回旧行为，红态用例会随网络形态漂移成 flaky —— 所以两条必须各有一条判据。
     */
    @Test
    fun dirtyCloseAfterContentIsAlsoTruncatedStream() {
        withSseServer(
            chunks = listOf(deltaFrame),
            contentType = "text/event-stream",
            declaredLength = 9_999L,
        ) { port, hits ->
            val events = runEngine(port)

            assertEquals(1, hits.get(), "脏断同样不许引擎内重发")
            val failed = events.filterIsInstance<GenerationEvent.Failed>().singleOrNull()
            assertTrue(failed != null, "脏断必须报失败，events=$events")
            assertTrue(failed!!.truncatedStream, "脏断也是截断：failed=$failed")
            assertEquals(EngineErrorKind.NETWORK, failed.kind)
            assertTrue(failed.retryable)
            assertTrue(events.none { it is GenerationEvent.Finished }, "events=$events")
        }
    }

    // ------------------------------------------------------------------ E4

    /** E4：供应商**显式** `finish_reason=length` ⇒ 不是流被掐断，仍是 `Completed(LENGTH)`。 */
    @Test
    fun providerReportedLengthStaysCompleted() {
        val body = deltaFrame + """data: {"choices":[{"delta":{},"finish_reason":"length"}]}""" + "\n\n"
        withSseServer(chunks = listOf(body), contentType = "text/event-stream") { port, _ ->
            val events = runEngine(port)

            assertTrue(events.none { it is GenerationEvent.Failed }, "events=$events")
            val finished = events.filterIsInstance<GenerationEvent.Finished>().single()
            assertEquals(FinishReason.LENGTH, finished.reason)
        }
    }

    // ------------------------------------------------------------------ E5

    /**
     * E5：流内 `error` 帧（限流）⇒ `Failed(RATE_LIMIT, retryable)`，**不补 `Finished`**。
     *
     * W40 P0-3 订正：delta 先到、error 帧后到 = 「供应商已吐内容后掐流」形态，与网络截断
     * 同构（旧断言 `assertFalse(truncatedStream)` 钉的是「限流不是截断」的旧口径——
     * 那个口径让内核旧闸门拦掉了唯一合理的处置：整段回滚后重试一次）。
     * 现口径：**已吐内容 ⇒ truncatedStream=true**，零产出时才是 false（E5b）。
     */
    @Test
    fun rateLimitErrorFrameIsRetryableWithoutFinished() {
        val error = """data: {"error":{"type":"rate_limit_exceeded","message":"slow down"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val events = runEngine(port)

            val failed = events.filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.RATE_LIMIT, failed.kind, "failed=$failed")
            assertTrue(failed.retryable)
            assertTrue(
                failed.truncatedStream,
                "已吐 delta 后的限流掐流与网络截断同构，必须置 truncatedStream（内核回滚重试）：$failed",
            )
            assertTrue(
                events.none { it is GenerationEvent.Finished },
                "错误帧命中后不许再补 Finished（双终态）：events=" + events,
            )
        }
    }

    /** E5b（W40 补）：零产出 + error 帧 ⇒ 不置 truncatedStream（无内容可回滚，走零产出重试）。 */
    @Test
    fun rateLimitErrorFrameBeforeAnyContentIsNotTruncated() {
        val error = """data: {"error":{"type":"rate_limit_exceeded","message":"slow down"}}""" + "\n\n"
        withSseServer(chunks = listOf(error), contentType = "text/event-stream") { port, hits ->
            val events = runEngine(port)

            val failed = events.filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.RATE_LIMIT, failed.kind, "failed=$failed")
            assertTrue(failed.retryable)
            assertFalse(
                failed.truncatedStream,
                "零产出 ⇒ 无内容可回滚 ⇒ 不置截断标志（引擎内按退避重试）：$failed",
            )
            // W41（P1-1 订正）：`>=1` 在「引擎完全不重试」的缺陷形态下仍绿。默认
            // RetryPolicy(maxAttempts=3) 对可重试的零产出限流帧应打满 3 次请求。
            assertEquals(3, hits.get(), "零产出限流在引擎内按策略重试（RetryPolicy maxAttempts=3）")
        }
    }

    // ------------------------------------------------------------------ E5c

    /**
     * E5c（W40 交叉必红样本 ①）：限流文案**含 token/limit**（`token limit reached, slow down`）
     * ⇒ 不得误判 `CONTEXT_OVERFLOW`。旧版 haystack 含 payload 全文 + 先判超窗词表 ⇒
     * `token`×`limit` 同时命中被误判不可重试（把零产出引擎内重试也关掉）。结构化
     * `type=rate_limit_error` 优先 ⇒ 必须判 RATE_LIMIT。
     */
    @Test
    fun rateLimitWordingContainingTokenAndLimitIsStillRateLimit() {
        val error = """data: {"error":{"type":"rate_limit_error","code":"429",""" +
            """"message":"token limit reached, slow down"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val events = runEngine(port)

            val failed = events.filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(
                EngineErrorKind.RATE_LIMIT,
                failed.kind,
                "限流文案含 token/limit 不得误判超窗（结构化 type 优先）：$failed",
            )
            assertTrue(failed.retryable)
        }
    }

    // ------------------------------------------------------------------ E5d

    /**
     * E5d（W40 交叉必红样本 ②）：服务端错误文案**含 context**（`server overloaded while
     * processing context window`）+ 结构化 `type=server_error` ⇒ 不得因文案词表误判
     * CONTEXT_OVERFLOW（不可重试）；结构化 type 优先 ⇒ 判 SERVER 可重试。
     */
    @Test
    fun serverErrorWordingContainingContextIsStillServer() {
        val error = """data: {"error":{"type":"server_error","code":"503",""" +
            """"message":"server overloaded while processing context window"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val events = runEngine(port)

            val failed = events.filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(
                EngineErrorKind.SERVER,
                failed.kind,
                "服务端文案含 context 不得误判超窗（结构化 type 优先）：$failed",
            )
            assertTrue(failed.retryable)
        }
    }

    // ------------------------------------------------------------------ E5e/f/g（W41）

    /**
     * E5e（W41 P0-2 必红样本）：供应商**只给 message**（无 type/code），文案同时含
     * `rate` 与 `token`/`limit` ⇒ 旧版「超窗词表优先」会误判 CONTEXT_OVERFLOW
     * （不可重试），把限流变成静默失败。W41 修法：无结构化字段时限流/服务端措辞
     * 先于超窗词表裁决。
     */
    @Test
    fun messageOnlyRateLimitWordingIsNotContextOverflow() {
        val error = """data: {"error":{"message":"rate limit: too many tokens"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val failed = runEngine(port).filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.RATE_LIMIT, failed.kind, "message-only 限流不得误判超窗：$failed")
            assertTrue(failed.retryable, "限流必须可重试：$failed")
            assertTrue(failed.truncatedStream, "已吐 delta 后掐流必须置位：$failed")
        }
    }

    /** E5f（W41）：数字型 `code: 429`（OpenAI 兼容端点常见）必须按限流读，不能跌回词表判 PROTOCOL。 */
    @Test
    fun numericRateLimitCodeIsClassifiedAsRateLimit() {
        val error = """data: {"error":{"code":429,"message":"provider throttled"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val failed = runEngine(port).filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.RATE_LIMIT, failed.kind, "数字 code 必须兜底：$failed")
            assertTrue(failed.retryable)
            // W42（P1-7）：同一族用例口径对齐 E5e——已吐 delta 后掐流必须置 truncatedStream。
            assertTrue(failed.truncatedStream, "已吐 delta 后掐流必须置位（与 E5e 同口径）：$failed")
        }
    }

    // ------------------------------------------------------------------ E5h/i/j（W42 P1-1）

    /**
     * E5h（W42 反向必红 ①）：无结构化字段，超窗文案**含 `server` 字样**
     * （`server cannot process: maximum context length exceeded`）⇒ 必须判
     * CONTEXT_OVERFLOW（不可重试）。W41 判序（服务端弱词先于超窗词表）会把它判
     * SERVER 可重试 ⇒ 同长度请求重发两次、两次都超窗。注入「把服务端弱词提到
     * 超窗组合之前」⇒ 本用例立即 RED。
     */
    @Test
    fun contextOverflowWordingWithServerIsNotServer() {
        val error = """data: {"error":{"message":"server cannot process: """ +
            """maximum context length exceeded"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val failed = runEngine(port).filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.CONTEXT_OVERFLOW, failed.kind, "超窗文案含 server 不得误判服务端：$failed")
            assertFalse(failed.retryable, "超窗不可重试：$failed")
        }
    }

    /** E5i（W42 反向必红 ②）：`request rate too large: maximum context length exceeded` ⇒ CONTEXT_OVERFLOW。 */
    @Test
    fun contextOverflowWordingWithRateIsNotRateLimit() {
        val error = """data: {"error":{"message":"request rate too large: """ +
            """maximum context length exceeded"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val failed = runEngine(port).filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.CONTEXT_OVERFLOW, failed.kind, "超窗文案含 rate 不得误判限流：$failed")
            assertFalse(failed.retryable, "超窗不可重试：$failed")
        }
    }

    /** E5j（W42 反向必红 ③）：`prompt too long, server rejected` ⇒ CONTEXT_OVERFLOW（token/prompt × too long）。 */
    @Test
    fun promptTooLongWithServerWordIsStillContextOverflow() {
        val error = """data: {"error":{"message":"prompt too long, server rejected"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val failed = runEngine(port).filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.CONTEXT_OVERFLOW, failed.kind, "prompt×too long 是超窗组合，不被 server 字样改判：$failed")
            assertFalse(failed.retryable, "超窗不可重试：$failed")
        }
    }

    /**
     * E5k（W43 P0-2 反噬 a 必红）：message-only 超窗文案自带 `too many tokens`
     * （`context length exceeded: too many tokens`）⇒ 必须判 CONTEXT_OVERFLOW（不可重试）。
     *
     * W42 把 `too many tokens` 提为「无条件显式限流强词」并置于超窗组合之前，本样本
     * 会命中该强词判 RATE_LIMIT（可重试）⇒ 同长度请求重发两次、两次都超窗（W40 KDoc
     * 点名的形态）。E5e `rate limit: too many tokens` 靠 `rate limit` 连写仍须保住限流；
     * 本样本无该连写、有 `context`×`exceeded` 超窗强组合 ⇒ 必须落超窗。
     * 注入「把 too many tokens 放回无条件强词」⇒ 本条 RED。
     */
    @Test
    fun contextOverflowWithTooManyTokensIsNotRateLimit() {
        val error = """data: {"error":{"message":"context length exceeded: too many tokens"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val failed = runEngine(port).filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.CONTEXT_OVERFLOW, failed.kind, "超窗+too many tokens 不得误判限流：$failed")
            assertFalse(failed.retryable, "超窗不可重试：$failed")
        }
    }

    // ------------------------------------------------------------------ E5l/m/n（W44 P0-2 证据强度仲裁）

    /**
     * E5l（W44 必红 ①）：message-only 超窗文案自带 `quota` 强限流词
     * （`maximum context length exceeded (quota: 100000 tokens)`）⇒ 必须判
     * CONTEXT_OVERFLOW（不可重试）。
     *
     * W43 残留（外审 P0-2(c)）：`rateContext` 六词（429/quota/throttl/slow down/
     * too many requests/rate limit 连写）无条件置于超窗组合之前 ⇒ 本样本判
     * RATE_LIMIT 可重试，同长度请求重发两次、两次都超窗。W44 证据强度仲裁：超窗
     * 强组合（窗口词 × 长度语义）优先于强限流词。注入「把 quota 放回无条件强词」⇒ 本条 RED。
     */
    @Test
    fun contextOverflowWithQuotaWordingIsNotRateLimit() {
        val error = """data: {"error":{"message":"maximum context length exceeded """ +
            """(quota: 100000 tokens)"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val failed = runEngine(port).filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.CONTEXT_OVERFLOW, failed.kind, "超窗+quota 不得误判限流：$failed")
            assertFalse(failed.retryable, "超窗不可重试：$failed")
        }
    }

    /** E5m（W44 必红 ②）：`context window exceeded, slow down your requests` ⇒ CONTEXT_OVERFLOW。 */
    @Test
    fun contextOverflowWithSlowDownWordingIsNotRateLimit() {
        val error = """data: {"error":{"message":"context window exceeded, """ +
            """slow down your requests"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val failed = runEngine(port).filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.CONTEXT_OVERFLOW, failed.kind, "超窗+slow down 不得误判限流：$failed")
            assertFalse(failed.retryable, "超窗不可重试：$failed")
        }
    }

    /**
     * E5n（W44 必红 ③）：裸 `limit` 降级后，`token limit reached for this endpoint`
     * （无长度语义）不再是超窗 —— 普通 SSE 错误帧，不得判 CONTEXT_OVERFLOW
     * （不可重试 + 触发压缩 + cache 失效）。
     *
     * W43 残留（外审 P0-2(d)）：`context`/`token` 仍直接配裸 `limit` ⇒ 本样本判
     * 超窗。W44 把裸 `limit` 从 `mentionsOverflow` 降级（与 `prompt` 护栏同一把尺）。
     * 注入「把裸 limit 放回超限词表」⇒ 本条 RED（判成 CONTEXT_OVERFLOW）。
     */
    @Test
    fun bareTokenLimitWithoutLengthSemanticsIsNotContextOverflow() {
        val error = """data: {"error":{"message":"token limit reached for this endpoint"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val failed = runEngine(port).filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.PROTOCOL, failed.kind, "裸 token limit 无长度语义是普通错误帧：$failed")
            assertFalse(failed.retryable, "PROTOCOL fail-closed：$failed")
        }
    }

    /** E5g（W41）：结构化超窗分支保留供应商原始 message，不再硬替换成通用文案。 */
    @Test
    fun contextOverflowKeepsProviderMessage() {
        val original = "maximum context length is 1000000 tokens, reduce your prompt"
        val error = """data: {"error":{"type":"invalid_request_error","message":"$original"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val failed = runEngine(port).filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.CONTEXT_OVERFLOW, failed.kind)
            assertEquals(original, failed.message, "原始可诊断文案不得被硬替换：$failed")
            assertFalse(failed.retryable)
        }
    }

    // ------------------------------------------------------------------ E6

    /** E6：流内 `error` 帧（超窗）⇒ `CONTEXT_OVERFLOW` + 真实窗口，且**不可重试**。 */
    @Test
    fun contextOverflowErrorFrameIsNotRetryable() {
        val error = """data: {"error":{"type":"invalid_request_error","message":""" +
            """"This model's maximum context length is 1000000 tokens"}}""" + "\n\n"
        withSseServer(chunks = listOf(deltaFrame, error), contentType = "text/event-stream") { port, _ ->
            val events = runEngine(port)

            val failed = events.filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.CONTEXT_OVERFLOW, failed.kind, "failed=$failed")
            assertFalse(failed.retryable, "超窗重发同长度必然再溢出 ⇒ 不许在引擎/传输层重试")
            assertEquals(1_000_000, failed.contextLimitHint, "真实窗口必须带出去（学习式写回通道）")
        }
    }

    // ------------------------------------------------------------------ E7

    /** E7：已见到 `finish_reason` 之后追打的错误帧 ⇒ **忽略**，结论不变（`Completed(STOP)`）。 */
    @Test
    fun errorFrameAfterFinishReasonIsIgnored() {
        val body = deltaFrame +
            """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""" + "\n\n" +
            """data: {"error":{"type":"server_error","message":"late failure"}}""" + "\n\n"
        withSseServer(chunks = listOf(body), contentType = "text/event-stream") { port, _ ->
            val events = runEngine(port)

            assertTrue(
                events.none { it is GenerationEvent.Failed },
                "正常收尾后追打的错误帧不得改判：events=" + events,
            )
            val finished = events.filterIsInstance<GenerationEvent.Finished>().single()
            assertEquals(FinishReason.STOP, finished.reason)
        }
    }

    // ------------------------------------------------------------------ E8

    /**
     * E8：HTTP 200 + `text/html`（零 SSE 事件）⇒ **`PROTOCOL` 且不可重试**。
     *
     * 这条同时是 `EndpointProbeTest.htmlPageReturnedWith200FailsTheProbe` 的护栏：
     * 若把「零事件」改判成 retryable，探针会打 3 次请求 + 两次退避，而 detail 里的
     * `PROTOCOL` 也会消失（探针靠 `kind.name` 判红）。
     */
    @Test
    fun html200WithNoEventsStaysProtocolFatal() {
        val html = "<html><body>gateway error</body></html>"
        withSseServer(chunks = listOf(html), contentType = "text/html; charset=utf-8") { port, hits ->
            val events = runEngine(port)

            assertEquals(1, hits.get(), "零事件不许重试（端点不是 SSE，重发还是 HTML）")
            assertTrue(events.none { it is GenerationEvent.Finished }, "events=$events")
            val failed = events.filterIsInstance<GenerationEvent.Failed>().single()
            assertEquals(EngineErrorKind.PROTOCOL, failed.kind)
            assertFalse(failed.retryable)
            assertFalse(failed.truncatedStream)
        }
    }

    // ------------------------------------------------------------------ 夹具

    private fun endpointFor(port: Int): RemoteEndpoint = RemoteEndpoint(
        id = "ep-stream",
        name = "stream",
        baseUrl = "http://127.0.0.1:$port/v1",
        modelId = "test-model",
        contextLength = 1_048_576,
        maxOutputTokens = 4_096,
        allowPrivateNetwork = true,
    )

    private fun runEngine(port: Int): List<GenerationEvent> {
        val endpoint = endpointFor(port)
        val engine = RemoteLlmEngine(
            endpoint = endpoint,
            adapter = OpenAiCompatibleAdapter(),
            transport = HttpTransport(),
            // 刻意用默认 RetryPolicy（maxAttempts=3）：断言「引擎不自己重发已吐内容的流」
            // 才有意义 —— 若实现回退到「可重试」，这里会出现退避（500ms）× 多一次请求。
        )
        return runBlocking {
            engine.generate(
                GenerationRequest(
                    messages = listOf(ChatMessage(role = Role.USER, text = "hi")),
                    endpoint = endpoint,
                )
            ).toList()
        }
    }

    /**
     * 起一个只回一次响应的 SSE 服务器。
     *
     * @param declaredLength `> 0` ⇒ 声明固定长度但只写 [chunks] 的字节就关连接（**脏断**，
     *   客户端读 `IOException`）；`0` ⇒ chunked，正常收尾（**干净 EOF**）。
     */
    private fun <T> withSseServer(
        chunks: List<String>,
        contentType: String,
        declaredLength: Long = 0L,
        block: (port: Int, hits: AtomicInteger) -> T,
    ): T {
        val hits = AtomicInteger(0)
        val payload = chunks.joinToString("").toByteArray(Charsets.UTF_8)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange: HttpExchange ->
            hits.incrementAndGet()
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(200, if (declaredLength > 0) declaredLength else 0)
            exchange.responseBody.use { out ->
                out.write(payload)
                out.flush()
            }
            exchange.close()
        }
        server.start()
        return try {
            block(server.address.port, hits)
        } finally {
            server.stop(0)
        }
    }
}
