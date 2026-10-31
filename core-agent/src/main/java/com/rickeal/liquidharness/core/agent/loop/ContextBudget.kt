package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.context.ContextCompressor
import com.rickeal.liquidharness.core.agent.context.ContextSummarizer
import com.rickeal.liquidharness.core.agent.context.CutPolicy
import com.rickeal.liquidharness.core.agent.context.DEFAULT_COMPRESS_TARGET_RATIO
import com.rickeal.liquidharness.core.agent.context.LayeredContextCompressor
import com.rickeal.liquidharness.core.agent.context.PrefixFingerprint
import com.rickeal.liquidharness.core.agent.context.PrefixSegmentKind
import com.rickeal.liquidharness.core.agent.context.WindowContextCompressor
import com.rickeal.liquidharness.core.agent.context.contextBudgetTokens
import com.rickeal.liquidharness.core.agent.context.fingerprintOf
import com.rickeal.liquidharness.core.agent.context.historySegment
import com.rickeal.liquidharness.core.agent.context.sanitizeForProvider
import com.rickeal.liquidharness.core.agent.context.skillsSegment
import com.rickeal.liquidharness.core.agent.context.systemSegment
import com.rickeal.liquidharness.core.agent.context.toolSchemaSegment
import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.prompt.AgentPromptComposer
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TokenEstimator
import com.rickeal.liquidharness.core.model.ToolSpec

/**
 * 发送前的上下文预算与压缩。**每轮生成前唯一允许改写工作副本的地方。**
 *
 * ## 存在理由：外审点名的一处系统性低估
 *
 * `contextBudgetTokens(endpoint, policy)` 只被喂 `working`，而 `system` 是每轮**临时**拼在
 * 最前的（不在工作副本里）。于是预算判断永远少算一整段 system —— 技能元数据 + 工具清单 +
 * 停止条件在 FULL 披露档位下可以到几千 token。低估的后果不是「少压缩一点」，
 * 而是**发出的请求真的超窗口**：输入 + 输出共享同一个窗口，system 又恰恰是最不可压缩的
 * 那一段（压它 = prompt cache 全失效，且模型丢失行为约束）。
 *
 * 所以 [prepare] 的估算是**全量**（`system + tools + working`），压缩只动 `working`。
 *
 * ## 不变量：system 段压缩前后**逐字节相同**
 *
 * 压缩是「丢最老的历史」，不是「改写前缀」。`WindowContextCompressor` 把 SYSTEM 单独摘出来
 * 再原样拼回，本类也只把非 SYSTEM 部分写回 `RunState`。这条不变量有单测钉住
 * （SHA-256 比对）—— 一旦有人把 system 也喂进压缩器做摘要，prompt cache 会整段失效，
 * 而账单上只表现为「贵了」，不表现为「错了」，属于最难发现的回归。
 *
 * ## 压缩失败必须无损
 *
 * 压缩器返回原列表（装不下 / 找不到工具组安全切点）时**不改写历史**：宁可发一个超预算的
 * 请求（端点会报错，可见），也不要把 `assistant.tool_calls` 与 `tool` 结果拆散
 * （端点 400，且现场已经被破坏）。
 *
 * ## W4 新增的三件事
 *
 * 1. **工具声明计入预算**（`system + tools + working`）。工具面为空时估算值与改动前
 *    逐值相同（判据 A6③ 的回归钉）。
 * 2. **工具面 run 内快照**（[toolSpecsFor]）。`TurnLoop` 已经快照了 system 文本，工具声明
 *    漏了同一步 ⇒ run 中途 MCP 注册 / 开关工具会让 `body.tools` 中段增删 ⇒ 前缀失效。
 * 3. **分段前缀指纹**（[observePrefix]）。每请求一行 INFO 账；**变化记 WARN** —— 缓冲满时
 *    `AgentLogStore` 优先丢 INFO，而「前缀被谁改了」恰恰是排查时最该留下的那条。
 *
 * ## 指纹状态为什么放在本类而不是 `RunState`（本波如实登记的妥协）
 *
 * design §3.A.4 把 `promptSections` / `toolSpecs` / `lastPrefixFingerprint` 记在 `RunState`。
 * 本波 A 组的文件域不含 `loop/RunState.kt`（另一路开发正在改相邻文件，同改必然冲突），
 * 故状态就近放在本类，**按 `runId` 划界**：runId 变了就整份重置（[toolSpecsFor]）。
 * 语义等价性：`AgentRunner.runMutex` 保证同一时刻只有一个 run，因此「按 runId 重置」
 * 与「每 run 新建一份」在可观测行为上一致；代价是 `GenerationRound` 若被并发复用会串账，
 * 而现役装配不会。**W5 应把它搬进 `RunState`**，与 design 对齐。
 *
 * @param compressor 压缩器。默认 = 水位线 0.6 + `TURN_ALIGNED`（理由见
 *   `DEFAULT_COMPRESS_TARGET_RATIO` 与 `CutPolicy`）。
 */
class ContextBudget(
    private val compressor: ContextCompressor = WindowContextCompressor(
        targetRatio = DEFAULT_COMPRESS_TARGET_RATIO,
        cutPolicy = CutPolicy.TURN_ALIGNED,
    ),
    /**
     * L2 摘要器的工厂。**按 run 懒构建**，`null` = 本波不启用 L2（退化成纯 L1）。
     *
     * ## 为什么是工厂而不是实例
     *
     * 摘要需要 `LlmEngine`，而引擎是**按 run 传进来的**（`TurnContext.engine`），
     * `ContextBudget` 在构造时拿不到它。把引擎塞进构造函数会让这个类持有一次 run 的
     * 引用（生命周期泄漏面），而在这里懒构建既拿到了引擎，又保证「一个 run 一个摘要器」。
     *
     * ## 默认值为什么是「启用」
     *
     * 用户可感知的收益是「长会话不再整段丢历史」，代价是一次**不带工具、不重试**的
     * 廉价调用，且仅在「被丢的那段 ≥ `summarizeMinTokens`」时才触发。
     * 摘要失败一律回落 L1（`LayeredContextCompressor` 保证不抛），所以默认启用不会
     * 让任何一次 run 因为摘要而失败。
     */
    private val summarizerFactory: ((LlmEngine) -> ContextSummarizer)? = { engine ->
        EngineBackedSummarizer(engine)
    },
) {

    /** 本 run 的分层压缩器。按 `runId` 失效，见 [activeCompressor]。 */
    private var layeredForRun: ContextCompressor? = null

    /**
     * 取本次 run 实际使用的压缩器：装了摘要器就是 `LayeredContextCompressor`，否则纯 L1。
     *
     * **按 run 缓存**而不是每次重建：`LayeredContextCompressor` 无状态，但重建会丢掉
     * 未来可能加进去的 run 级计数（摘要次数上限等）。缓存键与 [toolSpecsFor] 同源。
     */
    private fun activeCompressor(ctx: TurnContext): ContextCompressor {
        val existing = layeredForRun
        if (existing != null) return existing
        val factory = summarizerFactory
        val built: ContextCompressor = if (factory == null) {
            compressor
        } else {
            LayeredContextCompressor(delegate = compressor, summarizer = factory(ctx.engine))
        }
        layeredForRun = built
        return built
    }

    /** 快照所属的 run。与当前 `ctx.state.runId` 不等 ⇒ 视为新 run，整份重置。 */
    private var snapshotRunId: String? = null

    /** run 内工具面快照。**首次 [prepare] 取一次，之后不再读 `ctx.tools.specs()`**。 */
    private var toolSpecsSnapshot: List<ToolSpec> = emptyList()

    /** 上一次（或本轮压缩后）的前缀指纹。诊断与单测都从这里读。 */
    private var lastFingerprint: PrefixFingerprint? = null

    /**
     * 本 run 最近一次算出的前缀指纹。**未算过返回 null**（例如 [prepare] 还没被调过）。
     *
     * 公开读口不是给测试专用的：`GenerationRound` 的 usage 账行需要把
     * `fp` / `changed` 与 provider 回报的 `prompt_tokens` 并排写在一行里
     * （design §3.A.6 的 `usage: prompt=.. cached=.. prefix=.. changed=..`）。
     */
    val lastPrefixFingerprint: PrefixFingerprint? get() = lastFingerprint

    /**
     * 组装本轮要发的消息：`system` 在最前（id 稳定派生），其后是清洗过的工作副本。
     *
     * 顺序：**先 sanitize，再判预算**。反过来的话，估算的是「未补丁」的历史 ——
     * 缺结果的 tool_call 会由 `sanitizeForProvider` 补上一条合成结果，那段字节也要算钱。
     *
     * 超预算才压缩；压缩后**再 sanitize 一次**（压缩可能丢掉某条结果的宿主声明）。
     */
    suspend fun prepare(ctx: TurnContext, systemText: String): List<ChatMessage> {
        val specs = toolSpecsFor(ctx)
        val system = systemMessage(ctx, systemText)
        val body = ctx.state.working().filterNot { it.isEmpty }
        val full = sanitizeForProvider(listOf(system) + body)

        val budget = contextBudgetTokens(ctx.request.endpoint, ctx.policy)
        val toolTokens = TokenEstimator.estimateToolSpecs(specs)
        val estimated = TokenEstimator.estimateMessages(full) + toolTokens
        observePrefix(ctx, systemText, full, specs, estimated)
        if (!ctx.policy.compressContext) return full
        if (estimated <= budget) return full

        // 工具声明也占窗口，而压缩器只看 messages ⇒ 先把工具那一段从预算里扣掉，
        // 否则「工具面几千 token」时压缩器会以为还很宽松、一条都不丢（假压缩）。
        val messageBudget = (budget - toolTokens).coerceAtLeast(1)
        val compressed = activeCompressor(ctx).compress(listOf(system) + body, messageBudget)
        val keptBody = compressed.filter { it.role != Role.SYSTEM }
        if (keptBody.size >= body.size) {
            // 无损放弃：压缩器没能安全丢掉任何一条 ⇒ 不改写历史（见类头最后一段）。
            AgentLogStore.warn("context over budget but not compressible: " + estimated + " > " + budget)
            return full
        }
        ctx.state.replaceWorking(keptBody)
        AgentLogStore.info(
            "context compressed before round " + ctx.state.round + ": " + body.size + " -> " +
                keptBody.size + " messages (estimated " + estimated + " > budget " + budget + ")"
        )
        val keptFull = sanitizeForProvider(listOf(system) + keptBody)
        // 压缩本身就是一次前缀失效事件：立刻用**压缩后**的指纹覆盖账本，
        // 否则下一轮会把「压缩造成的变化」记成「这一轮的新变化」（判据 A5）。
        observeCompressedPrefix(ctx, systemText, keptFull, specs)
        return keptFull
    }

    /**
     * run 内工具面快照。
     *
     * 三条约束：
     *  - **只在 runId 变化时取一次**：run 中途 `unregister` / `register` / MCP 动态注册
     *    都不改变本次 run 的指纹（判据 A4）。理由与 `RunState.systemText` 同源：
     *    前缀一旦在中段增删，从该位置起整段 `body.tools` 重排 ⇒ prompt cache 全失效。
     *  - 取到的是 `ToolSpec` 不可变列表，不是注册中心的活视图。
     *  - 新 run 重置 [lastFingerprint]：跨 run 比指纹没有意义（历史已被 UI 气泡重建，
     *    内容只能做到语义等价，不能逐字节）。
     */
    private fun toolSpecsFor(ctx: TurnContext): List<ToolSpec> {
        val runId = ctx.state.runId
        if (snapshotRunId != runId) {
            snapshotRunId = runId
            toolSpecsSnapshot = ctx.tools.specs()
            lastFingerprint = null
            // 新 run 换新的摘要器：旧 run 的 `LlmEngine` 引用不该被带过来（同 run 同引擎）。
            layeredForRun = null
        }
        return toolSpecsSnapshot
    }

    /**
     * 算本轮指纹、记一行账，并在变化时补一条 WARN。
     *
     * @param full 已 sanitize 的整条请求（`full[0]` 是本轮的 system）。
     */
    private fun observePrefix(
        ctx: TurnContext,
        systemText: String,
        full: List<ChatMessage>,
        specs: List<ToolSpec>,
        estimated: Int,
    ) {
        val fingerprint = fingerprintFor(ctx, systemText, full, specs)
        val changed = fingerprint.changedLabel(lastFingerprint)
        if (changed != "none") warnPrefixChanged(ctx, changed, fingerprint, "request")
        lastFingerprint = fingerprint
        AgentLogStore.info(
            "prefix fp=" + fingerprint.combined.take(FP_LOG_CHARS) +
                " changed=" + changed +
                " prompt=" + estimated +
                " cached=" + cachedLabel(ctx)
        )
    }

    /** 压缩后刷新账本。只记 WARN（不重复记 INFO：每请求一行账的纪律）。 */
    private fun observeCompressedPrefix(
        ctx: TurnContext,
        systemText: String,
        keptFull: List<ChatMessage>,
        specs: List<ToolSpec>,
    ) {
        val fingerprint = fingerprintFor(ctx, systemText, keptFull, specs)
        val changed = fingerprint.changedLabel(lastFingerprint)
        if (changed != "none") warnPrefixChanged(ctx, changed, fingerprint, "compress")
        lastFingerprint = fingerprint
    }

    /**
     * 把四段折成指纹。
     *
     * - `SYSTEM` 段用**真正上线的** `systemText`（`TurnLoop` 每 run 缓存一份），不是重渲染；
     * - `SKILLS` 段从 [AgentPromptComposer.sectionsTyped] 取（它物理上也在 system 里，
     *   单列只为让「技能变了」在账里可分辨 —— 技能变化会同时命中 SYSTEM 与 SKILLS）；
     * - `TOOLS_SCHEMA` 段来自 run 内快照；
     * - `HISTORY` 段只取头部 K 条（`full.drop(1)` = 去掉 system 的线上 body），
     *   取尾部会让每次正常追加都报「前缀变了」（见 `fingerprintOf` 的 KDoc）。
     */
    private fun fingerprintFor(
        ctx: TurnContext,
        systemText: String,
        full: List<ChatMessage>,
        specs: List<ToolSpec>,
    ): PrefixFingerprint {
        val typed = AgentPromptComposer.sectionsTyped(ctx.request, specs)
        val skills = typed.filter { it.kind == PrefixSegmentKind.SKILLS }.joinToString("\n\n") { it.text }
        return fingerprintOf(
            listOf(
                systemSegment(systemText),
                skillsSegment(skills),
                toolSchemaSegment(specs),
                historySegment(full.drop(1)),
            )
        )
    }

    private fun warnPrefixChanged(
        ctx: TurnContext,
        changed: String,
        fingerprint: PrefixFingerprint,
        cause: String,
    ) {
        AgentLogStore.warn(
            "prefix changed segments=" + changed +
                " fp=" + fingerprint.combined.take(FP_LOG_CHARS) +
                " round=" + ctx.state.round +
                " cause=" + cause
        )
    }

    /**
     * `cached=` 字段。
     *
     * ⚠️ 它是**本 run 累计**的命中量，不是本请求的：`prepare` 发生在生成之前，此时本请求的
     * `usage` 还不存在。本请求的命中量由 `GenerationRound` 的 `Usage` 分支记（那一行是
     * per-request 真值）。本字段在这里的价值是「这个 run 至今有没有任何命中」。
     * 一个 token 都没回报过 ⇒ `n/a`，不伪装成 0（「未上报」与「0 命中」不可分辨是本仓
     * 已判死的失真形态，design §2.4）。
     */
    private fun cachedLabel(ctx: TurnContext): String =
        if (ctx.state.promptTokens == 0) "n/a" else ctx.state.cachedPromptTokens.toString()

    /**
     * system 消息：**id 用 `sys:<runId>` 稳定派生**，每轮复用同一个 id。
     *
     * 不是性能优化：id 抖动会让这段前缀的字节变化 ⇒ prompt cache 失效
     * （`00-brief.md` A8）。同一个 run 内 system 文本本身也必须字节稳定，
     * 由 `TurnLoop.systemTextFor` 的缓存保证。
     */
    private fun systemMessage(ctx: TurnContext, systemText: String): ChatMessage =
        ChatMessage(id = "sys:" + ctx.state.runId, role = Role.SYSTEM, text = systemText)

    private companion object {
        /** 账行里展示的指纹长度（前 8 位即可人工比对，全量 12 位在日志里太占宽）。 */
        const val FP_LOG_CHARS = 8
    }
}
