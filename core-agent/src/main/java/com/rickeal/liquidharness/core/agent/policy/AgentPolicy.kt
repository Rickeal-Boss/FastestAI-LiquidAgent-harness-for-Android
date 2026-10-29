package com.rickeal.liquidharness.core.agent.policy

import kotlinx.serialization.Serializable

/**
 * 一次 run 的策略。全部带默认值。
 *
 * ## 纪律：每个数字都要能指出出处
 *
 * 旧仓 `AgentPolicy.kt` 41 行、9 个字段，全部是端侧实测调出来的。搬到 API 路径后
 * **没有一个数字可以原样沿用而不重新取证** —— 端侧的成本是「慢」，远程的成本是「钱」，
 * 两者的最优阈值不同。因此每个字段的 KDoc 都写「旧仓值 + 为什么改」，
 * 改数字的人必须先读那一段（`AGENTS.md` §二.1：论断要能指到出处）。
 *
 * `【推断】`标记的字段是**尚未真机校准**的值，W2/W3 实测后按实测改，并在改的那次
 * commit 里把「旧值 → 新值 + 实测依据」写进 KDoc。
 */
@Serializable
data class AgentPolicy(

    /** 最大轮次（一轮 = 一次模型生成 + 一批工具执行）。旧仓 8。远程侧单轮更快但工具链更长 ⇒ 12。【推断，W2 实测校准】 */
    val maxRounds: Int = 12,

    /** 单个工具超时。旧仓 15s（端侧工具全是本地 IO）。MCP 工具是远端调用 ⇒ 60s。 */
    val toolTimeoutMillis: Long = 60_000L,

    /** 读类工具输出截断上限。旧仓统一 4000；远程侧按 effect 分开：读是工作材料，写是回执。 */
    val maxReadToolOutputChars: Int = 16_000,

    /** 写类工具输出截断上限（回执类，几百字符足够）。 */
    val maxWriteToolOutputChars: Int = 2_000,

    /** 是否自动放行 dangerous 工具。**默认 false = fail-closed**（旧仓同）。 */
    val autoApproveDangerous: Boolean = false,

    /** 是否启用每轮前的上下文压缩（旧仓同）。 */
    val compressContext: Boolean = true,

    /**
     * 压缩阈值。旧仓 0.75，沿用 —— 但**分母换了**：旧仓分母是 litertlm 的 KV 预算，
     * 新仓分母是「端点声明窗口与 [maxContextTokens] 取小」（见 [com.rickeal.liquidharness.core.agent.context.contextBudgetTokens]）。
     */
    val compressThreshold: Float = 0.75f,

    /**
     * 本应用愿意发出的上下文上限（token）。
     *
     * **新仓新增，理由是这个字段省钱**：没有它，1M 窗口的模型会真的按 0.75 发 75 万 token
     * 出去 —— 一次几美元（`00-brief.md` A8 理由 3）。
     */
    val maxContextTokens: Int = 32_000,

    /** 墙钟 SOFT：到点只记证据 + 提醒模型收尾，不打断。旧仓 180s，沿用。 */
    val wallClockSoftMillis: Long = 180_000L,

    /** 墙钟 HARD：到点熔断。旧仓 300s；远程侧重试会拉长链路 ⇒ 600s。 */
    val wallClockHardMillis: Long = 600_000L,

    /** 单轮生成超时（旧仓 300s，端侧 2s/token）。远程侧首字节前 120s 足够。【推断】 */
    val generationTimeoutMillis: Long = 120_000L,

    /**
     * 单 run 累计 prompt token 硬上限。
     *
     * **新仓新增**：API 路径下「无上限的 agent loop = 无上限的账单」。
     * 超限 ⇒ `TerminationReason.BreakerTripped`（见 `03-contracts.md` §五 #10 的改判说明：
     * 旧仓 W73-01 把账本判死为「只做读侧投影」，新仓复活为门控真值源）。
     */
    val maxRunPromptTokens: Int = 400_000,

    /** 子代理递归深度上限。旧仓靠「子 run 继承父 run 墙钟」间接约束，新仓改显式预算（W3）。 */
    val maxSubagentDepth: Int = 2,

    /** 工具总数超过此值时自动切 ON_DEMAND 披露（`03-contracts.md` §五 #24）。W2 接线。 */
    val disclosureThreshold: Int = 24,
)
