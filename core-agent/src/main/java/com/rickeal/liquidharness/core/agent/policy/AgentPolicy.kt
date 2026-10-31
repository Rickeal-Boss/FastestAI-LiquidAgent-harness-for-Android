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

    /**
     * 同一工具**连续**失败多少次触发熔断（`ToolExecutionGuard` 的 `failureStreakLimit`）。
     *
     * 4 = 旧仓 `DEFAULT_FAILURE_STREAK_LIMIT`。低于 3 会把「换参重试」误杀（模型常常
     * 第一次参数写错、第二次就好）；高于 5 会让一轮 run 白烧 5 次调用费。
     */
    val maxConsecutiveToolFailures: Int = 4,

    /**
     * 审批卡等待上限。超时按 [com.rickeal.liquidharness.core.agent.approval.ToolApprovalDecision.Deny]
     * 处理（只拒这一个工具，**不终止 run**）。
     *
     * 5 分钟：用户可能正在开会 / 切走了应用，卡不能无限等（否则 run 永远挂着、token 账不停）；
     * 也不该太短（30s 会让用户来不及读完参数就超时）。
     */
    val approvalTimeoutMillis: Long = 300_000L,

    /**
     * 审批卡**事件本身**的投递上限。
     *
     * 存在理由（外审点名的协程陷阱）：`AgentRunner` 的 emit 是 `channelFlow.send`，
     * 收集方退后台后 send 会挂起 ⇒ 卡发不出去、只能干等 [approvalTimeoutMillis]。
     * 5s 内投递不出去就按拒绝处理（fail-closed，绝不静默放行）。
     */
    val approvalEmitTimeoutMillis: Long = 5_000L,

    /** 是否自动放行 dangerous 工具。**默认 false = fail-closed**（旧仓同）。 */
    val autoApproveDangerous: Boolean = false,

    /**
     * 是否允许工具读系统剪贴板（`clipboard get`）。
     *
     * **默认 true = 保持 W2 的既有行为**（`get` 不弹卡、直接读）。
     *
     * 关掉之后 `clipboard get` **一律失败**（`ToolCallExecutor` 经 `PolicyGatedTool` 拦下，
     * 工具体根本不会被调用），理由是这个工具是一条**提示注入的外带通道**：
     * `get` 不需要审批、不进审批卡，于是「模型读一眼剪贴板再把内容拼进下一次请求」
     * 可以在用户完全看不见的情况下把剪贴板里的密码 / 验证码带出设备。写侧（`set`）本来
     * 就弹卡，读侧的这条通道此前是静默放行的 —— 默认保留是为了不改变现网行为，
     * 但设置页需要一个真开关（排 W7，`feature-settings`）。
     *
     * **W7 设置页开关的落点**：`feature-settings` 读这个字段时必须是**保存后重启 run 才生效**
     * （policy 是 run 级快照，不是热更新）—— 别把它做成「改了就立刻生效」的假承诺：
     * 在途 run 用的是开跑时那份 `AgentPolicy`，改字段不会回灌。
     */
    val allowClipboardRead: Boolean = true,

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
