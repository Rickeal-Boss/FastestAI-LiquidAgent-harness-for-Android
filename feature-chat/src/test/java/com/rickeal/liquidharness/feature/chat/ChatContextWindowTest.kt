package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.model.RemoteEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上下文状态条的分母与阈值（W6 收口）。
 *
 * ## 这一组用例钉的是一个**真实缺陷**，不是新功能
 *
 * 修前：`contextLimit = endpoint.contextLength`（声明值）。端点声明 1M + 策略档位 32K 时，
 * 占用率永远到不了 4% ⇒ 状态条**永不报警**，而内核其实早在 20 928 token 就压缩了。
 * 这与组件自己 KDoc 写的「一变色就精确对应压缩已经开始」正面矛盾。
 *
 * 修后分母 = 生效硬预算，于是 `used / hard >= 0.75` ⟺ `used >= 软预算` = 压缩触发点。
 *
 * ## 为什么两条边界都要断言
 *
 * 只断言「32K 档 + 高占用 ⇒ true」会把「恒为真」当成通过（分母写错也能绿）。
 * 所以同时断言：
 * - 32K 档、占用**略低于**软预算 ⇒ false（变色点没有被提前）；
 * - 1M 档、**同样占用** ⇒ false（分母真的跟着档位走）。
 */
class ChatContextWindowTest {

    private fun endpoint(
        contextLength: Int,
        maxOutputTokens: Int = 4_096,
        verified: Int? = null,
    ) = RemoteEndpoint(
        id = "ep-1",
        name = "test",
        baseUrl = "https://example.com/v1",
        modelId = "m",
        contextLength = contextLength,
        maxOutputTokens = maxOutputTokens,
        verifiedContextLength = verified,
    )

    /** 32K 档：`min(1_000_000, 32_000) − 4_096 = 27_904`（不是交接文档里的 28 904）。 */
    @Test
    fun contextLimitUsesHardBudgetFor32kTier() {
        val limit = contextLimitOf(endpoint(contextLength = 1_048_576, verified = 1_000_000), 32_000)
        assertEquals(27_904, limit)
    }

    /** 1M 档：`min(1_000_000, 1_000_000) − 4_096 = 995_904`。 */
    @Test
    fun contextLimitUsesHardBudgetFor1MTier() {
        val limit = contextLimitOf(endpoint(contextLength = 1_048_576, verified = 1_000_000), 1_000_000)
        assertEquals(995_904, limit)
    }

    /** 没有 verified 时回落声明值：`min(1_048_576, 32_000) − 4_096` 仍是 27 904。 */
    @Test
    fun contextLimitFallsBackToDeclaredWindow() {
        val limit = contextLimitOf(endpoint(contextLength = 1_048_576), 32_000)
        assertEquals(27_904, limit)
    }

    /** 未配置端点 ⇒ 0（meter 不渲染；0 是「还不知道」，不是「已满」）。 */
    @Test
    fun contextLimitIsZeroWithoutEndpoint() {
        assertEquals(0, contextLimitOf(null, 32_000))
    }

    /**
     * 32K 档 + 占用**正好等于软预算**（27 904 × 0.75 = 20 928）⇒ 变色。
     *
     * 这是「变色 ⟺ 压缩触发」的等号那一侧，所以用 20 928 而不是 20 000。
     */
    @Test
    fun nearFullTriggersExactlyAtSoftBudget() {
        val limit = contextLimitOf(endpoint(contextLength = 1_048_576, verified = 1_000_000), 32_000)
        assertEquals(20_928, (limit * 0.75f).toInt())
        assertTrue(nearFullContext(20_928, limit))
    }

    /**
     * 32K 档 + 占用**略低于**软预算（20 000 = 71.7%）⇒ **不变色**。
     *
     * 此时压缩还没触发，变色会把「前兆」变成「假警报」，也会破坏组件 KDoc 的语义。
     * （主理人原口径写的是「20 000 ⇒ true」，那是把 20 000 当成了触发点；
     * 按 27 904 × 0.75 = 20 928 算，20 000 仍在触发点之下。）
     */
    @Test
    fun nearFullDoesNotTriggerBelowSoftBudget() {
        val limit = contextLimitOf(endpoint(contextLength = 1_048_576, verified = 1_000_000), 32_000)
        assertFalse(nearFullContext(20_000, limit))
    }

    /** 1M 档 + 同样的 20 000 ⇒ 远未到（2%）：证明分母真的跟着档位走。 */
    @Test
    fun nearFullIsFalseOn1MTierAtSameUsage() {
        val limit = contextLimitOf(endpoint(contextLength = 1_048_576, verified = 1_000_000), 1_000_000)
        assertEquals(995_904, limit)
        assertFalse(nearFullContext(20_000, limit))
    }

    /** 任一侧为 0 ⇒ false：0 是「还不知道」，不能当「已满」。 */
    @Test
    fun nearFullIsFalseWhenEitherSideIsUnknown() {
        assertFalse(nearFullContext(0, 27_904))
        assertFalse(nearFullContext(1_000, 0))
    }
}
