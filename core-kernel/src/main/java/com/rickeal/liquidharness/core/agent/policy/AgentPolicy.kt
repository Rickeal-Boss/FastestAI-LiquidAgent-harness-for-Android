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

    /**
     * 读类工具输出截断上限。旧仓统一 4000；远程侧按 effect 分开：读是工作材料，写是回执。
     *
     * ⚠️ 实际生效值还要与 [maxToolResultChars] 取小 —— 分档管「读和写不一样」，
     * 硬上限管「谁都不许独吞保留区」，两者不是同一个问题。
     */
    val maxReadToolOutputChars: Int = 16_000,

    /** 写类工具输出截断上限（回执类，几百字符足够）。 */
    val maxWriteToolOutputChars: Int = 2_000,

    /**
     * **单条**工具结果的硬上限（字符），在工具出口处执行（`ToolResultLimiter`）。
     *
     * ## 为什么它与 effect 分档并存，而不是取代它
     *
     * [maxReadToolOutputChars] 的 16 000 字符 ≈ 4 000 token，正是「一条满额读结果」的量级
     * （W4 交接记录里的复发形态：压缩刚做完，下一轮就被它吃光保留区）。所以 16 000
     * **就是设计值**，这里取同值不是巧合 —— 它把「单条结果不许超过 X」从 effect 分档里
     * 独立成一个显式常量，于是有三件事可以挂靠到同一个数字上：
     * ① 写类（2 000）天然低于它，分档继续管「读是材料、写是回执」；
     * ② 运维可以整体调低它（比逐个 effect 上限好讲），而不动分档结构；
     * ③ [toolResultCapExemptTools] 有明确的豁免对象 —— 豁免的是**这一条**，不是分档。
     *
     * 思想出处是 Operit 的 `ToolExecutionLimits` 常量族（读文件 32 000 / 文本结果 5 000）；
     * 本仓**独立取证**后取 16 000，只借「在出口设与工具无关的硬上限」这个判断，
     * 不搬它的数值体系 —— 它的 32 000 是端侧本地读，我们的工具输出每一字符都计费。
     *
     * ⚠️ 不要把它调到低于 [maxReadToolOutputChars]：`file_read` 的**续读指引挂在输出尾部**，
     * 出口比工具自己的读量更狠时，指引会被一起截掉，模型既看不到剩余内容也拿不到续读位置。
     *
     * 超限时尾部追加 `[truncated: N chars omitted]` 并置 `truncated = true`：
     * 不标记的截断会让模型以为自己看到了全文（静默截断比不截断更糟）。
     */
    val maxToolResultChars: Int = 16_000,

    /**
     * **聚合**工具消息的上限（字符）：一条 `role=tool` 里所有结果之和。
     *
     * 单条限住了，整批仍会爆：并发 12 个调用 × 单条 16 000 = 192 000，远超任何模型的
     * 一次合理输入。64 000 ≈ 4 条满额读结果，正好是「模型这一轮真正用得上的证据量」。
     * 量级推算同 [maxToolResultChars] 的出处思想（Operit 的聚合上限常量族），数值本仓独立取证。
     *
     * 超限的处理在 `ToolResultCommit.commit`：**从最旧（列表头部）开始**换成占位，
     * 因为模型最需要的是最近那批结果；最后一条永远保持完整（否则这一轮等于什么都没回）。
     */
    val maxAggregatedToolMessageChars: Int = 64_000,

    /**
     * 免于 [maxToolResultChars] 截断的工具名（按 `ToolSpec.name` 精确匹配）。
     *
     * **默认空**。加入一项就等于放弃该工具的压缩收益，所以必须能回答
     * 「为什么这个工具的半截结果会让模型做错事」—— 典型是 diff / 结构化输出类：
     * 截断后 JSON 不合法，模型会把「解析失败」误判成「工具坏了」而不是「结果被截断」。
     *
     * 豁免只跳过 [maxToolResultChars]，per-effect 上限（读 16 000 / 写 2 000）仍然生效。
     * 在当前默认值下两者相等，所以豁免对读类**不产生可观测差异** —— 它是给「把硬上限
     * 调低」的部署准备的口子，不是给某个工具发的通行证。别因为「顺手」就往里塞工具。
     */
    val toolResultCapExemptTools: Set<String> = emptySet(),

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
     * 压缩阈值（**软预算**的水位线，不是压缩目标）。旧仓 0.75，沿用 —— 但**分母换了**：
     * 旧仓分母是 litertlm 的 KV 预算，新仓分母是「端点声明窗口与 [maxContextTokens] 取小，
     * 再减 [maxOutputTokens]」（见 [com.rickeal.liquidharness.core.agent.context.hardContextBudgetTokens]）。
     *
     * ## 两条口径订正（W5 交接与外审都写错过，别照抄）
     *
     * 1. 32K 窗口档的触发预算是 `(32 000 − 4 096) × 0.75 = 20 928`，**不是 19 200**
     *    （19 200 是 `32 000 × 0.6`，把「压缩目标水位线」当成了触发线）。
     * 2. 0.6 是**压缩目标**（`DEFAULT_COMPRESS_TARGET_RATIO`），与 0.75 不是同一个数，
     *    也不是同一个用途：0.75 决定「要不要压」，0.6 决定「压到多小」。
     *
     * 本字段只影响**软预算**（丢最老历史）。硬预算
     * （`hardContextBudgetTokens`，原地收缩 / 熔断）**不乘本字段** ——
     * 乘了就等于把硬上限砍掉 25%，兜底形同虚设（w6-plan §2.5(a)）。
     */
    val compressThreshold: Float = 0.75f,

    /**
     * 硬预算的**字符量纲下界（floor）**：`hardBudgetTokens × 本值 = hardCharCap`。
     *
     * 为什么需要字符判据：`TokenEstimator` 对代码 / JSON 按 4 字符/token 估、实测 ≈2.0，
     * 于是「估算 16k < 预算 20 928」与「实际 33 800 token」可以同时成立 —— **用有偏的尺子
     * 量自己等于没量**。字符量纲与估算器无关，是本仓唯一不依赖估算器正确性的判据（W5-01）。
     *
     * 取值 **1.8**：实测下界 2.01 向下取整留 ~10% 余量。⚠️ 它是 **floor 不是唯一取值**：
     * chars/token 是**内容相关**的（散文 5.73～5.75、代码 ≈2.0），固定 1.8 会把散文会话
     * 过度收缩约 3 倍。实际生效值由 [maxCalibratedCharsPerToken] 与 provider 真值标定
     * （见 `effectiveCharsPerToken`）；本字段只在「还没有任何真值」时生效。
     */
    val hardCharsPerToken: Float = 1.8f,

    /**
     * 标定后 chars/token 的**上界（ceiling）**：`effective = max(floor, min(ceiling, measured))`。
     *
     * 没有上界时一次异常回报（provider 少报 token）就能把 cap 放大十几倍 = **把兜底关掉**。
     * 6.0 是实测散文值（5.75）之上留的余量。标定只能在 `[floor, ceiling]` 内调整余量，
     * 不能替代 floor 的安全语义：实测值来自**已发出**的那条请求，兜底保护的是**下一条**，
     * 两者内容分布可以完全不同（上一轮散文、下一轮全是 JSON 工具结果）。
     */
    val maxCalibratedCharsPerToken: Float = 6.0f,

    /**
     * 本应用愿意发出的上下文上限（token）。
     *
     * **新仓新增，理由是这个字段省钱**：没有它，1M 窗口的模型会真的按 0.75 发 75 万 token
     * 出去 —— 一次几美元（`00-brief.md` A8 理由 3）。
     *
     * ⚠️ 它是「愿意付的钱的上限」，**不是模型能力的上限**：真实可用的窗口还要与
     * `endpoint.verifiedContextLength ?: endpoint.contextLength` 取小
     * （见 `hardContextBudgetTokens`）。
     */
    val maxContextTokens: Int = 32_000,

    /** 墙钟 SOFT：到点只记证据 + 提醒模型收尾，不打断。旧仓 180s，沿用。 */
    val wallClockSoftMillis: Long = 180_000L,

    /** 墙钟 HARD：到点熔断。旧仓 300s；远程侧重试会拉长链路 ⇒ 600s。 */
    val wallClockHardMillis: Long = 600_000L,

    /**
     * 首字节看门狗：从开始收流到**第一个事件**之间的等待上限。
     *
     * **已接线，消费点 `GenerationRound.attemptOnce`**（W4 之前本字段只有声明、零消费点，
     * 那是「声明了但没接线」的反例，别再让它退化回去）。超时 ⇒ 取消 collect +
     * `Attempt.Fatal`（`EngineErrorKind.TIMEOUT`），不重试。
     *
     * 只覆盖**首字节之前**：首个事件到达即解除。之后的慢流由 `readTimeout` 与
     * [wallClockHardMillis] 管 —— 两者的辖区不能混（`01-architecture.md` §6.1）。
     *
     * 计时用墙钟，**不接冻结感知时钟**：本字段防的是「远端不响应」，不是「设备被冻结」。
     * 冻结时协程与 `delay` 一起停摆，看门狗不会因此提前开火，不需要冻结感知。
     *
     * ⚠️ 那个冻结感知时钟（旧文档里的 `FreezeClock`）**尚未落地，挂账 W3-05**，全仓零实现。
     * 落地之前，[wallClockHardMillis] 在设备被系统冻结时有**误杀风险**（单调时钟照走，
     * 解冻后一算就是超时，而模型其实什么都没做）。这里如实登记，不假装已经处理。
     *
     * `<= 0` = 关闭看门狗（只给明确不要它的调用方 / 单测用）。
     *
     * 120s：旧仓 300s 是端侧 2s/token 的量级；远程侧首字节前 120s 足够【推断，W4 未真机校准】。
     */
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
