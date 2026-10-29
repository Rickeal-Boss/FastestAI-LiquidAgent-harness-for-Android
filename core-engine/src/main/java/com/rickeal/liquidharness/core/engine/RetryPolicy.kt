package com.rickeal.liquidharness.core.engine

import com.rickeal.liquidharness.core.model.EngineErrorKind
import kotlin.random.Random

/**
 * 重试策略。
 *
 * 两个决定分开，是因为它们是**两类属性**：
 * - [isRetryable]：协议属性 —— 「这类错误重发一次有没有意义」；
 * - [backoffMillis]：节奏属性 —— 「等多久再发」。
 *
 * 混在一起写（旧仓 `if (e is IOException) delay(500)`）的后果是：
 * 想给 429 加 `Retry-After` 支持时，得在调用点判断错误类型，而调用点拿不到响应头。
 *
 * ## 数字的来源
 *
 * 500ms / 1500ms 两档（`01-architecture.md` §五「backoff(500ms / 1500ms + jitter)」）：
 * - 首档 500ms：连接被 RST、DNS 抖动这类瞬时故障，半秒后重试的成功率已很高；
 * - 次档 1500ms：给服务端一点恢复时间，但总等待仍远小于用户耐心阈值；
 * - 第三档起 ×2 递增并封顶（本仓默认 `maxAttempts = 3`，实际到不了第三档）。
 *
 * jitter ±20%：**必须**有。同一时刻大量设备同时重试会形成重试风暴，
 * 而固定间隔会让它们一直同步。±20% 足以打散，又不会让退避时长不可预期。
 *
 * `Retry-After` 优先于本地退避（服务端知道得比我们多），但上限 60s ——
 * 服务端要求等一小时时应该报错让用户决定，而不是静默挂住 UI。
 */
class RetryPolicy(
    /** 总尝试次数（含首次）。3 = 首次 + 2 次重试。 */
    val maxAttempts: Int = 3,
    val firstBackoffMillis: Long = 500L,
    val secondBackoffMillis: Long = 1_500L,
    val maxRetryAfterMillis: Long = 60_000L,
    /** ±比例。0 = 关闭抖动（**只允许在单测里这么做**）。 */
    val jitterRatio: Double = 0.2,
) {

    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
        require(jitterRatio in 0.0..1.0) { "jitterRatio must be in [0, 1]" }
    }

    /**
     * 这一类错误重发有没有意义。
     *
     * 不可重试的三类各有理由：
     * - [EngineErrorKind.AUTH]：重试只会再被拒一次，还会让用户以为「在努力」；
     * - [EngineErrorKind.PROTOCOL]：同一份畸形响应重发大概率还是畸形；
     * - [EngineErrorKind.CONTEXT_OVERFLOW]：重发同样长度的上下文必然再次溢出，
     *   正确动作是压缩后重发（由内核做，不是这里）；
     * - [EngineErrorKind.CONFIG]：本地配置错，重试一万次也一样。
     */
    fun isRetryable(kind: EngineErrorKind): Boolean = when (kind) {
        EngineErrorKind.RATE_LIMIT,
        EngineErrorKind.SERVER,
        EngineErrorKind.NETWORK,
        -> true

        EngineErrorKind.AUTH,
        EngineErrorKind.PROTOCOL,
        EngineErrorKind.CONTEXT_OVERFLOW,
        EngineErrorKind.CONFIG,
        EngineErrorKind.UNKNOWN,
        -> false
    }

    /**
     * 第 [attempt] 次失败后应等待多久（attempt 从 0 开始）。
     *
     * @param retryAfterMillis 服务端 `Retry-After`；非 null 时**优先**且不加抖动
     *   （服务端给的是确定值，抖它没有意义）。
     */
    fun backoffMillis(attempt: Int, retryAfterMillis: Long? = null): Long {
        if (retryAfterMillis != null) {
            return retryAfterMillis.coerceIn(0L, maxRetryAfterMillis)
        }
        val safeAttempt = attempt.coerceAtLeast(0)
        val base = when (safeAttempt) {
            0 -> firstBackoffMillis
            1 -> secondBackoffMillis
            else -> secondBackoffMillis shl (safeAttempt - 1).coerceAtMost(MAX_SHIFT)
        }
        if (jitterRatio <= 0.0) return base
        val span = (base * jitterRatio).toLong().coerceAtLeast(1L)
        val delta = Random.nextLong(-span, span + 1)
        return (base + delta).coerceAtLeast(0L)
    }

    /**
     * 是否还应该重试。
     *
     * @param emittedText 已向上游 emit 过 TextDelta ⇒ **一律不重试**：
     *   重发会让模型从头再答一遍，用户看到两段内容拼接（旧仓 P1 级缺陷）。
     */
    fun shouldRetry(attempt: Int, kind: EngineErrorKind, emittedText: Boolean): Boolean =
        !emittedText && attempt + 1 < maxAttempts && isRetryable(kind)

    private companion object {
        /** 指数退避的位移上限，防 `shl` 溢出成负数。 */
        const val MAX_SHIFT = 4
    }
}
