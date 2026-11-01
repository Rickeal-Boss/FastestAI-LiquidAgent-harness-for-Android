package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.AgentRequest
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.TokenUsage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 一次 run 的可变状态。**单 run 内单协程访问**（除 [cancelled]），因此不加锁 ——
 * 加锁会掩盖「谁在并发改 working」这个真正的问题（并发由 `AgentRunner.runMutex` 串行化）。
 *
 * 为什么状态要独立成类而不是散在 `TurnLoop` 的局部变量里：旧仓这些字段全部内联在
 * `AgentRunner` 的方法局部作用域，于是任何抽出去的方法都拿不到它们 ⇒ 只能继续内联。
 * 集中到 `RunState` 后，「谁改了轮次 / 谁加了 token」都有唯一落点，也便于单测直接断言。
 */
class RunState(
    /** 工作副本的初始内容（= 历史 + 本次用户输入）。**只读入参**，内部复制。 */
    working: List<ChatMessage> = emptyList(),
    /** 时间源注入点：单测要能冻结墙钟，不能让 [elapsedMillis] 直连 `System.nanoTime()`。 */
    private val nowNanos: () -> Long = System::nanoTime,
) {

    /** 工作副本。**只增不减，除压缩外**（`01-architecture.md` §五 不变量 1）。 */
    private val workingMessages = ArrayList<ChatMessage>(working)

    private val startedAtNanos: Long = nowNanos()

    /**
     * 本次 run 的稳定标识。
     *
     * 两个用途：① 幂等键前缀（同一 run 同一轮的重试共用一个值，便于服务端日志关联）；
     * ② system 消息的**稳定派生 id**（`sys:<runId>`）—— 每轮生成都复用同一个 id，
     * 保证前缀字节稳定（`00-brief.md` A8）。
     */
    val runId: String = com.rickeal.liquidharness.core.model.newId()

    /**
     * 取消标记。
     *
     * `AtomicBoolean` 而不是普通 `var`：置位发生在 UI 线程（`AgentRunner.cancel()`），
     * 读取发生在 run 协程 ⇒ 必须跨线程可见。这里**不做**「取消后立刻停」的承诺 ——
     * 真正打断挂起点的是 Job 取消，本标记只负责「不再进下一轮 / 不再执行下一个工具」。
     */
    private val cancelledFlag = AtomicBoolean(false)

    /** 已完成的轮次数（1 基：第一轮 `beginRound()` 返回 1）。 */
    var round: Int = 0
        private set

    /** 本 run 累计发送侧 prompt token（门控真值，`03-contracts.md` §五 #10）。 */
    var promptTokens: Int = 0
        private set

    /** 本 run 累计 completion token。 */
    var completionTokens: Int = 0
        private set

    /**
     * 本 run 累计 prompt cache 命中量（A8 唯一可观测判据）。
     *
     * `null` = **至今没有任何一次 provider 上报过该字段**；`0` = 上报过、且一次都没命中。
     * 两者不可合并（design §2.4）：合并后「端点不支持缓存字段」会被读成「缓存全失效」。
     */
    var cachedPromptTokens: Int? = null
        private set

    /** 已执行的工具调用计数（W2 起用；W1 恒 0）。 */
    var executedToolCount: Int = 0
        private set

    /**
     * **上一次 provider 真实回报的** `prompt_tokens`（单次请求，不是累计）。0 = 还没有真值。
     *
     * ## 为什么它是独立字段，而不是复用 [promptTokens]
     *
     * [promptTokens] 是**本 run 累计**（门控真值，`maxRunPromptTokens` 用）。而估算校准要的是
     * 「上一次发出去的那条请求实际多大」—— 累计值会把前几轮也算进来，锚点立刻失效。
     *
     * ## 为什么锚点 + 增量仍然不够（W6 写在这里，防下一个 agent 误判）
     *
     * 锚点修的是**静态部分**：历史里那一大段已经被 provider 数过一次，之后不再重复低估。
     * 但 W5-01 事故的增量**本身就是被低估的那一段**（工具结果正文，估算 4 字符/token、
     * 实际 ≈2）⇒ 锚点对本例**一次都拦不住**。真正的判据是字符量纲的
     * `HardBudgetShrinker` / `hardCharsPerToken`。别看到这里就以为修完了。
     */
    var lastReportedPrompt: Int = 0
        private set

    /**
     * [lastReportedPrompt] 那次请求的**消息条数**（已 sanitize 的整条请求，含 system）。
     *
     * 估算式是 `lastReportedPrompt + estimate(本次请求.drop(lastReportedAtMessageCount))`，
     * 所以它必须与真值同源、同一时刻记下。压缩会改写历史 ⇒ 锚点必须置 0
     * （见 [invalidatePromptAnchor]），否则会拿压缩前的真值去估压缩后的历史。
     */
    var lastReportedAtMessageCount: Int = 0
        private set

    /**
     * 最近一次失败携带的「真实窗口」（`maximum context length is N` 里的 N）。
     *
     * 只是**传递载体**：`GenerationRound` 写、`RunTermination.emitFailed` 读、宿主的
     * run 收尾写回端点（`AppContainer.recordContextLimit`）。内核不在这里做任何判断。
     */
    var contextLimitHint: Int? = null
        private set

    /**
     * **实测** chars/token：上一次请求的 `发送字符数 ÷ provider 回报的 prompt_tokens`。
     *
     * `0f` = 本 run 还没有真值（首轮 / 刚压缩过）⇒ 硬预算回落
     * `policy.hardCharsPerToken`（floor 1.8）。
     *
     * ## 为什么这个比值必须被测量，而不是写死一个常数
     *
     * chars/token 是**内容相关**的：英文散文实测 5.73～5.75，代码 / JSON ≈2.0。
     * 固定 1.8 在散文会话里会把可用字符空间砍到 1/3（可感知的信息损失），
     * 固定 5.75 又会在代码会话里超窗。所以只能按**本 run 的真实内容**标定。
     *
     * ## 为什么它偏小（方向安全）
     *
     * 分子只算 `messages` 的字符，分母含 `body.tools` 的 token ⇒ 比值偏低 ⇒ cap 偏小。
     * 已知偏差，由 `policy.maxCalibratedCharsPerToken`（6.0）兜住上界。
     */
    var measuredCharsPerToken: Float = 0f
        private set

    /**
     * 本轮 system 文本缓存。**每 run 只渲染一次**（`00-brief.md` A8 硬规则 1）。
     *
     * 缓存放这里而不是 `TurnLoop` 局部变量：压缩会改 `working`，但**不许**借机重渲染
     * system —— 一旦重渲染，整段前缀的 prompt cache 全部失效。这个字段的存在就是把
     * 「每轮重算」这条路堵死（想重算必须先清它，代码里只有一处能清）。
     */
    var systemText: String? = null

    /** 墙钟 SOFT 是否已提醒过（只提醒一次，避免每轮刷同一条）。 */
    var softWarned: Boolean = false

    /** 本 run 最后一次收到的 finishReason。诊断卡与截断判定要用。 */
    var finishReason: FinishReason? = null

    /** usage 是否来自本地估算兜底（provider 未回报）。 */
    var usageEstimated: Boolean = false

    private val textBuffer = StringBuilder()

    /**
     * 思考通道字符数（只计数不存文本：思考内容不进历史、不回灌模型）。
     *
     * 存在理由：推理模型常常先吐一大段 thinking、正文还没开始就被中断。
     * 引擎侧判「已产出内容」含 reasoning，内核侧若只看 `textLength()` 就会判
     * 「空输出」并重试 —— 两层口径不一致，用户看到思考显示一半 + 一条
     * `empty output` 失败（外审 P0-2）。两个判据必须同源。
     */
    private var reasoningChars: Int = 0

    val cancelled: Boolean get() = cancelledFlag.get()

    /** 置取消标记。幂等。 */
    fun markCancelled() {
        cancelledFlag.set(true)
    }

    /** 进入下一轮并返回轮次号（1 基）。 */
    fun beginRound(): Int {
        round += 1
        return round
    }

    // ── 工作副本 ────────────────────────────────────────────────────────────

    /** 工作副本的**只读快照**（返回新列表，调用方改它不影响内部状态）。 */
    fun working(): List<ChatMessage> = ArrayList(workingMessages)

    /**
     * 追加到工作副本**尾部**。
     *
     * 只允许追加，不允许中段插入 —— 中段插入会让该位置之后的所有字节位移，
     * 前缀 prompt cache 从插入点起全部失效（`00-brief.md` A8 硬规则 2）。
     */
    fun appendMessage(message: ChatMessage) {
        workingMessages.add(message)
    }

    /** 用压缩结果**整体替换**工作副本。**唯一的允许改写历史的地方**（压缩）。 */
    fun replaceWorking(messages: List<ChatMessage>) {
        workingMessages.clear()
        workingMessages.addAll(messages)
    }

    // ── 流式文本 ────────────────────────────────────────────────────────────

    fun appendText(delta: String) {
        textBuffer.append(delta)
    }

    /** 当前已收文本长度。重试前用它记账，重试时用它回滚。 */
    fun textLength(): Int = textBuffer.length

    /** 记一段思考通道文本。只计数 —— 思考不落历史（契约：reasoning 不进 messages）。 */
    fun appendReasoning(delta: String) {
        reasoningChars += delta.length
    }

    /** 思考通道已收字符数。 */
    fun reasoningLength(): Int = reasoningChars

    /**
     * 本轮是否产出过**任何**可见内容（正文或思考）。
     *
     * 「空输出」判定的唯一真值源：引擎层的 `hasEmittedAny` 是同一口径的传输侧镜像。
     */
    fun producedAnyContent(): Boolean = textBuffer.isNotEmpty() || reasoningChars > 0

    /** 回滚到 [length]（重试 / `StreamReset` 用）。越界按空处理，绝不抛异常。 */
    fun truncateText(length: Int) {
        if (length in 0..textBuffer.length) textBuffer.setLength(length) else textBuffer.setLength(0)
    }

    /** 已收到的全部正文。`Cancelled(partialText)` 与 `Finished(text)` 都用它。 */
    fun partialText(): String = textBuffer.toString()

    // ── 记账 ────────────────────────────────────────────────────────────────

    /**
     * 记一次 provider 回报的 usage。
     *
     * 语义是**累加**：一轮一次 `Usage` 事件，一个 run 多轮 ⇒ 账本按轮累加。
     * provider 漏报 usage 时由调用侧走 [recordEstimatedPrompt] 兜底。
     *
     * @param cachedPrompt `null` = provider 未上报该字段 ⇒ **不累加、也不把账本置 0**
     *   （置 0 就是把「不知道」写成「没命中」）。只要有任何一次上报过，账本从此有值。
     */
    fun recordUsage(prompt: Int, completion: Int, cachedPrompt: Int?) {
        promptTokens += prompt
        completionTokens += completion
        if (cachedPrompt != null) cachedPromptTokens = (cachedPromptTokens ?: 0) + cachedPrompt
    }

    /** provider 未回报 usage 时的估算兜底（标记 `estimated = true`，供校准告警使用）。 */
    fun recordEstimatedPrompt(tokens: Int) {
        promptTokens += tokens
        usageEstimated = true
    }

    fun recordToolExecuted() {
        executedToolCount += 1
    }

    /**
     * 记下 provider 回报的真值，作为下一次估算的锚点（W6 / T5）与 chars/token 的标定源（T7）。
     *
     * @param prompt 本次请求的 `prompt_tokens`（**单次**，不是累计）。
     * @param messageCount 本次请求实际发出去的消息条数（含 system），用于算「其后新增的部分」。
     * @param charsSent 本次请求 `messages` 的字符总量。用来标定 [measuredCharsPerToken]。
     *   只算 messages（不含 `body.tools`）⇒ 比值偏低 ⇒ cap 偏小 ⇒ 安全侧。
     *
     * 只接受 `prompt > 0`：provider 漏报时 adapter 会给 0（缺省），拿 0 当锚点会让下一次
     * 估算**从零开始**，比不用锚点更糟（低估得更狠）；标定同理，除零会得到 Inf。
     */
    fun recordReportedPrompt(prompt: Int, messageCount: Int, charsSent: Int = 0) {
        if (prompt <= 0 || messageCount < 0) return
        lastReportedPrompt = prompt
        lastReportedAtMessageCount = messageCount
        if (charsSent > 0) measuredCharsPerToken = charsSent.toFloat() / prompt
    }

    /**
     * 锚点与标定**同时**失效（压缩之后必须调）。
     *
     * 压缩改了历史的前半段，`lastReportedAtMessageCount` 指向的下标随之失去意义 ——
     * 继续用它会让「新增部分」把压缩后重排的整段历史当成增量，估算暴涨到误熔断。
     *
     * chars/token 也一并置 0：压缩会大幅改变内容构成（旧的历史被摘要替换），
     * 上一次测得的比值不再代表这一份内容。回落到 floor 是**保守**方向 ——
     * 宁可多收缩一次，也不要在压缩后的第一轮就超窗。
     */
    fun invalidatePromptAnchor() {
        lastReportedPrompt = 0
        lastReportedAtMessageCount = 0
        measuredCharsPerToken = 0f
    }

    /** 记下失败携带的真实窗口（只在下一次 run 收尾时被读走，不做判断）。 */
    fun recordContextLimitHint(hint: Int?) {
        if (hint == null || hint <= 0) return
        contextLimitHint = hint
    }

    /** 给 `AgentEvent.Finished` 用的用量快照。一个 token 都没记账时返回 null（不伪造 0）。 */
    fun usage(): TokenUsage? {
        if (promptTokens == 0 && completionTokens == 0) return null
        return TokenUsage(
            prompt = promptTokens,
            completion = completionTokens,
            total = promptTokens + completionTokens,
            cachedPromptTokens = cachedPromptTokens,
            estimated = usageEstimated,
        )
    }

    /**
     * 可运行时长（毫秒）。
     *
     * ⚠️ 用**单调时钟**，不扣冻结时间。`FreezeClock`（设备 cgroup 冻结感知时钟）
     * **尚未落地**，挂账 W3-05 —— 全仓零实现，别在别处引用它当成已接机制。
     * 落地之前，墙钟 HARD（`wallClockHardMillis`）在设备被系统冻结时有**误杀风险**：
     * 冻结期间单调时钟照走，解冻后一算就是「超时」，而模型其实什么都没做。
     * 这里如实按单调时钟算，不假装已经做了冻结剔除。
     */
    fun elapsedMillis(): Long = (nowNanos() - startedAtNanos) / 1_000_000L

    companion object {
        /** 从请求构造：历史 + 本次用户输入（用户输入永远追加在尾部）。 */
        fun forRequest(
            request: AgentRequest,
            nowNanos: () -> Long = System::nanoTime,
        ): RunState {
            val initial = ArrayList<ChatMessage>(request.history.size + 1)
            initial.addAll(request.history)
            if (request.userInput.isNotEmpty() || request.userAttachments.isNotEmpty()) {
                initial.add(
                    ChatMessage(
                        role = com.rickeal.liquidharness.core.model.Role.USER,
                        text = request.userInput,
                        attachments = request.userAttachments,
                    )
                )
            }
            return RunState(initial, nowNanos)
        }
    }
}
