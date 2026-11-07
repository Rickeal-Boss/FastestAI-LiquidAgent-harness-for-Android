package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.TokenEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 预算算式的单测（W5-02）。
 *
 * 判据是**具体数值**，不是「能跑通」：预算算式错了不会抛异常，只会让每一次请求的
 * 判据都失真，而账单上只表现为「贵了」或「莫名 400」（最难发现的回归形态）。
 */
class BudgetFormulaTest {

    /**
     * T4：`verifiedContextLength` 必须**压过**声明值，且硬预算要再减 `maxOutputTokens`。
     *
     * 1 000 000 是「输入 + 输出共享」的上限 —— 直接拿它当输入预算，输出阶段必撞窗口。
     */
    @Test
    fun verifiedWindowWinsOverDeclaredWindowAndSubtractsOutputReserve() {
        val endpoint = RemoteEndpoint(
            id = "ep-1m",
            contextLength = 1_048_576,
            verifiedContextLength = 1_000_000,
            maxOutputTokens = 4_096,
        )
        // maxContextTokens 必须放大，否则 policy 的 32 000 会先把它压下来。
        val policy = AgentPolicy(maxContextTokens = 1_048_576)

        assertEquals(995_904, hardContextBudgetTokens(endpoint, policy))
        // 声明值（1 048 576）若被采用会得到 1 044 480 —— 断言它**没有**被采用。
        assertTrue(hardContextBudgetTokens(endpoint, policy) < 1_048_576 - 4_096)
    }

    /**
     * 回归钉：`verifiedContextLength` 为 null 时，软预算与 W5 之前**逐值相同**。
     *
     * 32K 档的触发预算是 `(32 000 − 4 096) × 0.75 = 27 904 × 0.75 = 20 928`，不是 19 200
     * （19 200 = 32 000 × 0.6，那是压缩**目标**水位线，被交接文档误当成触发线）。
     */
    @Test
    fun softBudgetIsUnchangedWhenNoVerifiedWindowExists() {
        val endpoint = RemoteEndpoint(id = "ep-32k", contextLength = 32_000, maxOutputTokens = 4_096)
        val policy = AgentPolicy()

        assertEquals(27_904, hardContextBudgetTokens(endpoint, policy))
        assertEquals(20_928, contextBudgetTokens(endpoint, policy))
        // 硬预算不许乘水位线：乘了就等于把硬上限砍掉 25%，兜底形同虚设。
        assertEquals(50_227, hardCharCap(endpoint, policy))
    }

    /**
     * W10/S1 量纲同源契约：硬闸 floor（`AgentPolicy.hardCharsPerToken`）恒 ≤ 估算尺
     * （`TokenEstimator.CHARS_PER_TOKEN_ASCII`），且硬闸字符上限按估算尺折回 token 恒不超过
     * 硬预算本身 —— 硬闸恒保守，两把尺互为上下界，禁第三处独立比例。
     */
    @Test
    fun hardGateFloorNeverExceedsEstimatorRuler() {
        val floor = AgentPolicy().hardCharsPerToken
        assertTrue(
            floor <= TokenEstimator.CHARS_PER_TOKEN_ASCII.toFloat(),
            "floor=$floor 必须 ≤ 估算尺 " + TokenEstimator.CHARS_PER_TOKEN_ASCII,
        )
        val hardTokens = 20_000
        val capChars = charCapOf(hardTokens, floor)
        val estimated = TokenEstimator.estimate("a".repeat(capChars))
        assertTrue(estimated <= hardTokens, "estimated=$estimated capChars=$capChars 不许超硬预算")
    }

    /**
     * W10/S2 触发线按档位（补钉非改造）：同一端点、不同 `maxContextTokens` 档位 ⇒
     * 软预算随档位缩放且数值精确 —— 防回归成「写死单一常量」。
     */
    @Test
    fun softBudgetScalesWithWindowTier() {
        val endpoint = RemoteEndpoint(id = "ep-1m", contextLength = 1_000_000, maxOutputTokens = 4_096)

        val tier32k = contextBudgetTokens(endpoint, AgentPolicy(maxContextTokens = 32_000))
        val tier128k = contextBudgetTokens(endpoint, AgentPolicy(maxContextTokens = 128_000))

        assertEquals(20_928, tier32k)   // (32 000 − 4 096) × 0.75
        assertEquals(92_928, tier128k)  // (128 000 − 4 096) × 0.75
        assertTrue(tier128k > tier32k, "档位升 ⇒ 触发线必须升")
    }
}
