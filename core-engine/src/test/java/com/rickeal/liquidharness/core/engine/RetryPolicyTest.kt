package com.rickeal.liquidharness.core.engine

import com.rickeal.liquidharness.core.model.EngineErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 重试策略单测。
 *
 * 判据分两类：
 * 1. **哪些错误可重试** —— 判错的代价是「AUTH 被重试三次」或「429 一次都不试」；
 * 2. **退避时长落在区间内** —— 抖动是随机的，所以只能断言区间，不能断言精确值
 *    （断言精确值等于把抖动关掉，那正是重试风暴的来源）。
 */
class RetryPolicyTest {

    private val policy = RetryPolicy()

    @Test
    fun rateLimitServerAndNetworkAreRetryable() {
        assertTrue(policy.isRetryable(EngineErrorKind.RATE_LIMIT))
        assertTrue(policy.isRetryable(EngineErrorKind.SERVER))
        assertTrue(policy.isRetryable(EngineErrorKind.NETWORK))
    }

    @Test
    fun authProtocolOverflowAndConfigAreNotRetryable() {
        assertFalse(policy.isRetryable(EngineErrorKind.AUTH))
        assertFalse(policy.isRetryable(EngineErrorKind.PROTOCOL))
        assertFalse(policy.isRetryable(EngineErrorKind.CONTEXT_OVERFLOW))
        assertFalse(policy.isRetryable(EngineErrorKind.CONFIG))
        assertFalse(policy.isRetryable(EngineErrorKind.UNKNOWN))
    }

    /** 两档基数 500 / 1500，±20% 抖动。 */
    @Test
    fun backoffUsesTwoTiersWithJitter() {
        repeat(50) {
            val first = policy.backoffMillis(0)
            assertTrue(first in 400..600, "first=$first")

            val second = policy.backoffMillis(1)
            assertTrue(second in 1200..1800, "second=$second")
        }
    }

    /** 关掉抖动时必须是精确值（便于排查「退避是不是生效了」）。 */
    @Test
    fun backoffWithoutJitterIsExact() {
        val exact = RetryPolicy(jitterRatio = 0.0)
        assertEquals(500L, exact.backoffMillis(0))
        assertEquals(1_500L, exact.backoffMillis(1))
        // 第三档起指数递增，防退避时长永远停在 1500。
        assertEquals(3_000L, exact.backoffMillis(2))
    }

    /** Retry-After 优先于本地退避，且受 60s 上限约束。 */
    @Test
    fun retryAfterOverridesLocalBackoffAndIsCapped() {
        assertEquals(7_000L, policy.backoffMillis(0, retryAfterMillis = 7_000L))
        assertEquals(60_000L, policy.backoffMillis(0, retryAfterMillis = 3_600_000L))
        assertEquals(0L, policy.backoffMillis(0, retryAfterMillis = -1L))
    }

    /** 已 emit 过内容 ⇒ 一律不重试（否则用户会看到两段拼接的回答）。 */
    @Test
    fun noRetryAfterContentWasEmitted() {
        assertFalse(policy.shouldRetry(attempt = 0, kind = EngineErrorKind.NETWORK, emittedText = true))
        assertTrue(policy.shouldRetry(attempt = 0, kind = EngineErrorKind.NETWORK, emittedText = false))
    }

    /** 次数上限：maxAttempts = 3 ⇒ attempt 0、1 可重试，attempt 2 不再重试。 */
    @Test
    fun attemptLimitIsRespected() {
        assertTrue(policy.shouldRetry(attempt = 0, kind = EngineErrorKind.SERVER, emittedText = false))
        assertTrue(policy.shouldRetry(attempt = 1, kind = EngineErrorKind.SERVER, emittedText = false))
        assertFalse(policy.shouldRetry(attempt = 2, kind = EngineErrorKind.SERVER, emittedText = false))
    }

    /** 不可重试的错误，无论次数都返回 false。 */
    @Test
    fun nonRetryableKindNeverRetries() {
        assertFalse(policy.shouldRetry(attempt = 0, kind = EngineErrorKind.AUTH, emittedText = false))
        assertFalse(policy.shouldRetry(attempt = 0, kind = EngineErrorKind.PROTOCOL, emittedText = false))
    }

    /** 非法构造参数必须早失败，不能等到运行期算出负退避。 */
    @Test
    fun invalidConfigurationFailsFast() {
        try {
            RetryPolicy(maxAttempts = 0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("maxAttempts"))
        }
        try {
            RetryPolicy(jitterRatio = 2.0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("jitterRatio"))
        }
    }
}
