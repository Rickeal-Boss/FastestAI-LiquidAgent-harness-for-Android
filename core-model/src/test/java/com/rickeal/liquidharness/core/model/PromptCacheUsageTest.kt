package com.rickeal.liquidharness.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * W9-04：prompt cache 的**四态**与命中率分母口径。
 *
 * 背景：三态 `[TokenUsage.cachedPromptTokens]` 把「usage 末帧没来」和「usage 来了
 * 但没带缓存字段」都压成 `null`，W7-02 的「OR 免费池恒 0 到底算不算没命中」
 * 因此挂了四波闭不掉。四态把这两件事分开，命中率再把分母一次钉死。
 */
class PromptCacheUsageTest {

    @Test
    fun positiveCachedIsAHit() {
        assertEquals(PromptCacheStatus.HIT, promptCacheStatus(64, usageObserved = true))
    }

    @Test
    fun explicitZeroIsAMissOnlyWhenUsageWasObserved() {
        assertEquals(PromptCacheStatus.MISS, promptCacheStatus(0, usageObserved = true))
    }

    @Test
    fun missingCacheFieldsAreNotAMiss() {
        assertEquals(
            PromptCacheStatus.CACHE_FIELDS_NOT_REPORTED,
            promptCacheStatus(null, usageObserved = true),
        )
    }

    /** 第四态优先于一切：没收到 usage 时，哪怕 cached 有值也不能下缓存结论。 */
    @Test
    fun unobservedUsageWinsOverEveryOtherState() {
        assertEquals(PromptCacheStatus.USAGE_NOT_OBSERVED, promptCacheStatus(null, usageObserved = false))
        assertEquals(PromptCacheStatus.USAGE_NOT_OBSERVED, promptCacheStatus(64, usageObserved = false))
        assertEquals(PromptCacheStatus.USAGE_NOT_OBSERVED, promptCacheStatus(0, usageObserved = false))
    }

    /** 写入量留在分母：600 / (1000 + 200) = 50%。 */
    @Test
    fun hitRatioKeepsCacheWriteInDenominator() {
        val usage = TokenUsage(prompt = 1_000, cachedPromptTokens = 600, cacheWriteTokens = 200)
        assertEquals(0.5, cacheHitRatio(usage) ?: Double.NaN, 1e-9)
    }

    /** OpenAI 兼容族 prompt 已含命中量 ⇒ 不再加一次 read（双计会让口径漂移）。 */
    @Test
    fun hitRatioDoesNotDoubleCountTheRead() {
        val usage = TokenUsage(prompt = 1_000, cachedPromptTokens = 600)
        assertEquals(0.6, cacheHitRatio(usage) ?: Double.NaN, 1e-9)
    }

    /** 未上报 / 分母未知 ⇒ null，绝不伪装成 0%（design §2.4 三态纪律）。 */
    @Test
    fun hitRatioIsNullWhenNotReportedOrDenominatorIsUnknown() {
        assertNull(cacheHitRatio(TokenUsage.Zero))
        assertNull(cacheHitRatio(TokenUsage(prompt = 0, cachedPromptTokens = 0)))
    }

    /** 旧 journal 缺 cacheWriteTokens ⇒ 反序列化/构造默认 null，命中率退回旧口径而非报错。 */
    @Test
    fun legacyUsageWithoutWriteFieldStillComputes() {
        val usage = TokenUsage(prompt = 500, completion = 10, cachedPromptTokens = 125)
        assertNull(usage.cacheWriteTokens)
        assertEquals(0.25, cacheHitRatio(usage) ?: Double.NaN, 1e-9)
    }

    /**
     * 平铺族 `prompt_tokens` 可能不含命中量 ⇒ 原始比值 6.0（600/100）。
     * 钳制到 1.0 只保证 UI 不出现荒谬数字；按族归一留给 W11-05，不把 1.0 写成事实。
     */
    @Test
    fun hitRatioIsClampedWhenProviderPromptExcludesTheRead() {
        val usage = TokenUsage(prompt = 100, cachedPromptTokens = 600)
        assertEquals(1.0, cacheHitRatio(usage) ?: Double.NaN, 1e-9)
    }
}
