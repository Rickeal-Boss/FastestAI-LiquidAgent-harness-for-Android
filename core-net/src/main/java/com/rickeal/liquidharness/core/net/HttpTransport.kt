package com.rickeal.liquidharness.core.net

import okhttp3.Call
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 一条已经建立（响应头已到）的 SSE 流。
 *
 * 为什么把 [statusCode] 暴露出来而不是在非 2xx 时抛异常：
 * 「401 / 429 / 5xx」在引擎侧要分成**不同的错误类**（改配置 / 退避重试 / 重试），
 * 而分类需要状态码与 `Retry-After`。在传输层抛异常就把这些信息变成了字符串。
 *
 * [close] 幂等：`response.close()` + `call.cancel()` 都包了 `runCatching`，
 * 且用 [closed] 挡住重复执行 —— 取消路径上「关两次」是常态（collect 取消 + finally）。
 */
class SseStream internal constructor(
    private val call: Call,
    private val response: Response,
) : Closeable {

    private val closed = AtomicBoolean(false)

    val statusCode: Int get() = response.code

    val isSuccessful: Boolean get() = response.isSuccessful

    val contentType: String? get() = response.header("Content-Type")

    /**
     * 任意响应头（W3 新增）。
     *
     * 唯一消费者是 MCP：Streamable HTTP 用 `Mcp-Session-Id` 响应头下发会话句柄，
     * 客户端必须回传。不暴露这个方法，MCP 就得自己再建一套 OkHttp ——
     * 那正是 G1（网络栈收口）要禁止的两套网络栈。
     */
    fun header(name: String): String? = response.header(name)

    /**
     * `Retry-After` 折算成毫秒。支持秒数与 HTTP-date 两种形态（RFC 9110），
     * 上限 [MAX_RETRY_AFTER_MILLIS]（60s）—— 服务端要求等一小时时，
     * 我们应该报错让用户决定，而不是静默挂住 UI。
     */
    val retryAfterMillis: Long? get() = parseRetryAfter(response.header("Retry-After"))

    /** 原始字节流。**不要**用 `charStream()`：那会在 chunk 边界上做解码，中文会被切坏。 */
    fun byteStream(): InputStream = response.body?.byteStream() ?: throw IOException("empty response body")

    /** 错误响应体，截断到 [limit]。用于把服务端的 400 原因带给用户（已过 SecretRedactor）。 */
    fun errorBodyText(limit: Int = ERROR_BODY_LIMIT): String {
        val body = response.body ?: return ""
        return runCatching { body.string().take(limit) }.getOrDefault("")
    }

    /** 取消在途请求。`stop()` 走这里 —— 只关响应体不会让阻塞中的 read 立刻返回。 */
    fun cancel() {
        runCatching { call.cancel() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { response.close() }
        runCatching { call.cancel() }
    }

    private fun parseRetryAfter(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        trimmed.toLongOrNull()?.let { seconds ->
            return (seconds * 1000L).coerceIn(0L, MAX_RETRY_AFTER_MILLIS)
        }
        return runCatching {
            val target = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
            (target - System.currentTimeMillis()).coerceIn(0L, MAX_RETRY_AFTER_MILLIS)
        }.getOrNull()
    }

    companion object {
        private const val ERROR_BODY_LIMIT = 512
        const val MAX_RETRY_AFTER_MILLIS: Long = 60_000L
    }
}

/**
 * HTTP 传输层。**全仓唯一**允许发 HTTP 的地方（守卫 N1）。
 *
 * 三个超时的取值理由（照抄旧仓真机验证过的结论，数字改了但推理不变）：
 * - `connectTimeout = 15s`：真机弱网下 DNS + TLS 握手超过 15s 基本可判死；
 * - `readTimeout = 60s`：**必须有限**。正常 SSE 会持续有数据（内容 / 心跳 / 空行），
 *   60 秒一个字节都没有 = 连接已死。设 0（永不超时）会让 `read()` 永久阻塞，
 *   UI 表现为「一直转圈、不报错、点停止也没用」（ColorOS 灭屏冻结是这种现场的常客）；
 * - `callTimeout = 0`：一次长回答本身可能很久，总时长由内核的墙钟闸门管，
 *   传输层不该二次裁决。
 *
 * `retryOnConnectionFailure = false`：OkHttp 默认会**静默重试**一次。那会让
 * `RetryPolicy` 的退避与日志对不上（用户看到 2 次重试，实际打了 3 次），
 * 也绕过了「已 emit TextDelta 就不许重试」的内核判定。重试权收归一处。
 */
class HttpTransport(
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * POST 并等待响应头。
     *
     * **阻塞**调用（OkHttp `execute()`），必须跑在 IO 调度器上。
     * 非 2xx 不抛异常 —— 返回带 [SseStream.statusCode] 的流，由引擎分类。
     *
     * @param headers 已经是最终形态（含 `Authorization`），本层不注入任何凭据。
     * @param allowPrivateNetwork 端点是否显式允许内网。默认 `false` = fail-closed：
     *   域名解析出来的私网地址会被 [GuardedDns] 拒掉（DNS rebinding 的第二道闸）。
     *   字面量 IP 的裁决在 [UrlGuard.check]（请求前），这里不重复判。
     *
     * 重定向**不跟随**（见 [streamingClient]）：302 会原样作为 [SseStream.statusCode] 返回，
     * 由引擎按 Fatal 上报。
     */
    fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
        allowPrivateNetwork: Boolean = false,
    ): SseStream {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
        for ((name, value) in headers) builder.addHeader(name, value)
        val call = streamingClient(allowPrivateNetwork).newCall(builder.build())
        return SseStream(call, call.execute())
    }

    /**
     * 每次请求派生一个 client：只有「流式请求」需要 60s readTimeout，
     * 其它用途（如 MCP 的单次 JSON 调用）要的是别的值。
     * OkHttp 的 `newBuilder()` 共享连接池与线程池，派生成本很低。
     *
     * DNS 校验在这里注入而不是在 [post] 里手动解析：`Dns` 是 OkHttp 唯一的
     * 「主机名 → 地址」入口，覆盖 HTTP/2 与连接复用时的重解析。
     *
     * ## 为什么必须关掉重定向（外审 P1）
     *
     * OkHttp 默认 `followRedirects` / `followSslRedirects` 都是 `true`。跟随重定向会
     * 让 [UrlGuard.check] 与 [GuardedDns] 之间出现一个洞：两道闸都只对**原始 URL**
     * 生效，302 之后的新 URL 不过 `UrlGuard.check`，而它的 host 若是字面量 IP，
     * `GuardedDns` 又会早退跳过 —— 于是「保存一个合法域名 + 服务端回 302 到
     * `169.254.169.254`」就能把云元数据读回 UI。
     *
     * `chat/completions` 没有合法的重定向场景：换域名应当改配置，而不是静默跟过去。
     * 关掉之后 302 会作为非 2xx 流返回，由引擎按 Fatal 上报 —— 顺带消灭
     * 「重定向后 SSE 只收到半截」这类脏状态（跨主机重定向会重发请求体，
     * 部分代理此时只回一段普通 JSON）。
     */
    private fun streamingClient(allowPrivateNetwork: Boolean): OkHttpClient = client.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(GuardedDns(allowPrivateNetwork, client.dns))
        .build()

    companion object {
        const val CONNECT_TIMEOUT_SECONDS: Long = 15L
        const val READ_TIMEOUT_SECONDS: Long = 60L

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            // 与 streamingClient 同一策略：非流式调用（MCP 单次 JSON）同样不需要重定向，
            // 跟随会让第二道 DNS 闸对跳转后的目标失效。
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}

/**
 * 连接前对**解析结果**再判一次（DNS rebinding 的兜底）。
 *
 * 为什么必须有这一层：[UrlGuard.check] 只看 URL 里的字面量。一个攻击者控制的域名
 * （或内网 DNS 里的同名记录）可以解析到 `192.168.1.1` / `169.254.169.254`，
 * 字面量检查完全看不见 —— 只有拿到解析后的地址才判得了。
 *
 * 判据不复制：[UrlGuard.checkResolvedIp] 与 [UrlGuard.check] 共用同一套 IP 分类，
 * 避免「两道闸两套规则」这种最难查的不一致。
 *
 * 字面量 host 在这里**跳过**：它已经被 [UrlGuard.check] 按端点策略裁决过，
 * 再判一次会把白名单（`127.0.0.1` / `10.0.2.2`）自己拦死。
 *
 * 残余风险（如实登记）：域名 rebinding 到 loopback 时本类收紧为拒绝（`allowLoopback = false`），
 * 但端点显式 `allowPrivateNetwork = true` 时仍会放行 —— 那是用户主动承担的风险。
 *
 * 重定向通道已由传输层 `followRedirects(false)` / `followSslRedirects(false)` 关闭
 * （见 [HttpTransport.streamingClient]），所以上面的字面量早退**只对原始 URL 成立**：
 * 不存在「302 之后的新 URL 既不过 [UrlGuard.check] 又被本类跳过」的窗口。
 */
internal class GuardedDns(
    private val allowPrivateNetwork: Boolean,
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val resolved = delegate.lookup(hostname)
        if (UrlGuard.isIpLiteral(hostname)) return resolved
        for (address in resolved) {
            val ip = address.hostAddress ?: continue
            val verdict = UrlGuard.checkResolvedIp(
                ip = ip,
                allowPrivateNetwork = allowPrivateNetwork,
                allowLoopback = false,
            )
            if (verdict is UrlVerdict.Rejected) {
                throw IOException("blocked resolved address: ${verdict.reason} ($ip)")
            }
        }
        return resolved
    }
}
