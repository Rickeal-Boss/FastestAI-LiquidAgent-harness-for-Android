package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.agent.context.compactTriggerChars
import com.rickeal.liquidharness.core.agent.context.compressTargets
import com.rickeal.liquidharness.core.agent.context.contextLimitOf
import com.rickeal.liquidharness.core.agent.context.shouldApply
import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.data.AttachmentImporter
import com.rickeal.liquidharness.core.data.ContextLimitWriteback
import com.rickeal.liquidharness.core.data.contextWindowCap
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 自动压缩本身的字符成本当量（分隔卡 / 切点指针 / 一行落账诊断串）。
 *
 * 取 256：与 `MIN_SAVINGS_CHARS = 460` 同量级且小于其一半，保证「净收益达阈值」
 * 不会被成本本身吃掉。这是**字符量纲**，与 W6 的 L3 硬预算同源，不是 token 估算，
 * 也不调用 `TokenEstimator` 自证（W5-01 的 33.8K 假绿形态）。
 */
private const val AUTO_COMPACT_COST_CHARS = 256

/** 单条消息最多 4 张图：再多会让单次请求的图片 token 吃掉正文预算。 */
private const val MAX_ATTACHMENTS = 4

/**
 * 「会话级」的写入动作：**手动压缩** / **会话导入** / **端点重读** / **输入与附件**。
 *
 * ## 为什么从 `ChatViewModel` 里搬出来（这是 G10 的处置，不是分层）
 *
 * `ChatViewModel` 单文件撞到 500 行闸门（`scripts/arch-guard.sh` 的 LINE_CAPS），
 * 本仓的纪律是**拆文件**、不是调上限（`01-architecture.md` §二）。搬出来的判据很简单：
 * 这些动作与「跑 run / 流式节流 / 审批句柄」那套时序逻辑零耦合 —— 它们只读一次
 * `state` 快照、写一次 `state`，不碰 `runGeneration` / `flushBuffer` /
 * `pendingApprovalDeferred`。放在同一个类里纯属历史原因。
 *
 * 依赖显式注入（`container` / `state` / `scope` / 重置回调）而不是持有 ViewModel：
 * 这样本类不需要 `ChatViewModel` 的任何私有成员，也就不会为了「少一行」而把私有字段
 * 提成 `internal`。
 *
 * W39：`refreshEndpoint` / `onInputChange` / `attachImage` / `removeAttachment` /
 * `dismissError` 五个写入点也从 `ChatViewModel` 外提到这里（同一判据）。`ChatViewModel`
 * 保留同名 public 方法作**一行委托** ⇒ `app` 侧的调用点与签名零改动。
 *
 * ## 线程模型
 *
 * [compressContext] 的协程跑在调用方传进来的 `scope`（= `viewModelScope`，Main.immediate）；
 * 只有读 `settings` 这一句会挂起。压缩本身是纯 CPU（字符遍历），几万条消息也在毫秒级，
 * 不切线程 —— 切了反而要处理「压缩完时代次已变」的竞态。
 */
internal class ConversationOps(
    private val container: AppContainer,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
) {

    /**
     * 400 超窗学到的真实窗口写回端点（W6 收口）。
     *
     * 依赖用 lambda 注入而不是让 [ContextLimitWriteback] 持有 container：
     * 那条判据（「Failed(hint) ⇒ 写回被调用一次且参数正确」）只能在 JVM 上用一个记账
     * lambda 断言，`AppContainer` 造不出来（见 `ContextLimitWriteback` 的类头）。
     */
    private val limitWriteback = ContextLimitWriteback { endpointId, verified ->
        container.recordContextLimit(endpointId, verified)
    }

    /** 事件里带 `contextLimitHint` 时写回。**异步、静默**，不阻塞事件处理。 */
    fun offerContextLimit(endpointId: String?, verified: Int?) {
        scope.launch { limitWriteback.offer(endpointId, verified) }
    }

    /**
     * 重新读取活跃端点。
     *
     * 从设置页返回时必须调：换了端点 / 改了模型名后，顶栏、上下文窗口与 `canSend`
     * 都依赖它，不重读就会出现「顶栏显示旧模型、请求已经打到新端点」的错觉。
     *
     * 状态条的**分母**取生效硬预算而不是端点声明窗口（理由与算式见 [contextLimitOf]）：
     * 声明 1M + 档位 32K 时用声明值当分母会让状态条永不报警，而压缩早在 20 928 就发生了。
     */
    suspend fun refreshEndpoint() {
        val endpoint = container.activeEndpoint()
        val windowCap = contextWindowCap(container)
        state.update {
            it.copy(
                endpointLabel = endpoint?.let(::labelOf),
                contextLimit = contextLimitOf(endpoint, windowCap),
            )
        }
    }

    fun onInputChange(text: String) {
        state.update { it.copy(input = text) }
    }

    /**
     * 导入一张图片到待发送附件。
     *
     * 三条失败路径都**不静默**：解码失败 / 超过 1.5 MiB / 系统没装解码器，
     * 全部走 `errorMessage`（用户在输入框上方能看到具体原因）。
     *
     * 为什么不在 Composable 里做：解码是阻塞 IO（可能要读 10 MB 原图），
     * Composable 里做会卡主线程；`AttachmentImporter` 内部已切 IO 线程。
     */
    fun attachImage(uri: String, name: String) {
        scope.launch {
            when (val result = container.attachments.importImage(uri, name)) {
                is AttachmentImporter.Result.Ok -> state.update {
                    // 上限 4 张：再多会让单次请求的图片 token 超过正文预算。
                    if (it.attachments.size >= MAX_ATTACHMENTS) it else {
                        it.copy(attachments = it.attachments + result.attachment)
                    }
                }

                is AttachmentImporter.Result.Failed -> state.update {
                    it.copy(errorMessage = "attachment failed: " + result.reason)
                }
            }
        }
    }

    /** 移除一张待发送附件。 */
    fun removeAttachment(id: String) {
        state.update { snapshot -> snapshot.copy(attachments = snapshot.attachments.filterNot { it.id == id }) }
    }

    fun dismissError() {
        state.update { it.copy(errorMessage = null) }
    }

    private fun labelOf(endpoint: RemoteEndpoint): String =
        if (endpoint.modelId.isBlank()) endpoint.name else endpoint.name + " · " + endpoint.modelId

    /**
     * 会话级自动压缩（W7，用户裁决 (a)：正常结束只是**评估时点**，真压由体积 + 收益阈值定）。
     *
     * **唯一调用点**：`ChatViewModel.runOnce` 的 `collect` 内按 `shouldEvaluate(event)` 分流 —
     * 绝不挂 `finally`（无条件置 `isStreaming=false`），也不能只看 streaming 归零：`Finished`
     * 与 `Cancelled` 共用 `finish(...)`，只有 `Finished(ModelStopped)` 才是正常结束。CI 席 G23
     * 钉 `.autoCompactOnSettled(` 恰好一处 + selftest 变异自证（删调用 ⇒ 判红）。
     *
     * **每次评估必落可见账**（noop 也写 reason，含 `nothing_to_drop`）：否则「评估了但没压」
     * 与「根本没接上」在日志上无法区分（W6 教训）。开关关 ⇒ 直接 return，手动压缩仍可用。
     *
     * **量纲分工**：触发线 `compactTriggerChars` 与 `MIN_SAVINGS_CHARS` 都用**字符**（按档位，
     * 不依赖估算器正确性）；压缩目标 `compressBubbles(budgetTokens)` 走**token 软预算**
     * （与手动同源），保证压完不会立刻再触发内核自动压缩。
     */
    fun autoCompactOnSettled() {
        val snapshot = state.value
        // ⚠️ 不判 `isStreaming`：`EventReducer.finish` 已置 false，清缓冲在 runOnce 的 finally
        // 里做 ⇒ 它不能当「气泡已落库」的代理（W7 真机实摔：判了它，自动压缩被静默早退）。
        if (snapshot.bubbles.isEmpty()) return
        scope.launch {
            if (!container.settings.compressContext.first()) return@launch
            val endpoint = container.activeEndpoint() ?: return@launch
            val windowCap = contextWindowCap(container)
            val policy = AgentPolicy(maxContextTokens = windowCap)
            // W54 · P0-2：token 软预算 + 字符硬上限同车给（与内核 PreStepBudget 同尺）。
            val (budget, charCap) = compressTargets(endpoint, policy)
            // W63：触发线按档位给（同一 policy，严格低于 L3 cap）。
            val triggerChars = compactTriggerChars(endpoint, policy)
            // 试算：compressBubbles 是纯 suspend 函数，不改 state；只有 shouldApply 判
            // applied=true 才把产物落到 compression（未压轮次保留严格前缀 ⇒ 缓存可能命中）。
            val outcome = compressBubbles(snapshot.bubbles, budget, charBudget = charCap)
            val committedChars = committedCharsOf(snapshot.bubbles, snapshot.compression)
            // 空转（越线且试算没压动任何东西）单独归因：`insufficient_savings` 只留给
            // 「已产出压缩区但净收益不足」；未越线仍由 shouldApply 给 below_trigger，
            // 已压缩态仍由 already_compressed 负责——三者不互相抢语义。
            if (committedChars > triggerChars &&
                snapshot.compression == null && !outcome.applied
            ) {
                val line = "auto_compact=noop reason=nothing_to_drop"
                AgentLogStore.warn(line)
                persist(line)
                return@launch
            }
            val savingChars = committedChars - committedCharsOf(snapshot.bubbles, outcome.compression)
            val decision = shouldApply(
                committedChars,
                savingChars,
                AUTO_COMPACT_COST_CHARS,
                snapshot.compression != null,
                triggerChars = triggerChars,
            )
            if (!decision.applied) {
                val line = "auto_compact=noop reason=" + decision.reason
                AgentLogStore.warn(line)
                persist(line)
                return@launch
            }
            // W10/S3：尾部摘要前置形态保留头锚 ⇒ 前缀缓存不整段失效（cache_invalidated=false）。
            val ledger = "auto_compact dropped=" + outcome.droppedMessages +
                " kept=" + outcome.keptMessages.size + " cache_invalidated=false"
            AgentLogStore.info(ledger)
            persist(ledger)
            state.update {
                it.copy(compression = outcome.compression)
            }
        }
    }

    /**
     * 手动压缩（W6，`docs/notes/w6-plan.md` §三）。
     *
     * ## 压的是「气泡映射出的 history」，不是内核里的上下文
     *
     * history 的真值源就是 [ChatUiState.bubbles]；内核侧没有跨 run 的上下文可压
     * （`RunState` 每 run 新建）。所以压缩必须在这里做，产物落到
     * [ChatUiState.compression] 这个**压缩区描述**上 —— 否则下一次 run 又从全量气泡
     * 重建，压缩等于没做。
     *
     * ## 三条前置拒绝（都静默返回，不弹错误）
     *
     * - 在途 run：压缩会改写「本轮已发出的历史」，而正在跑的 run 手里是另一份副本；
     *   允许它只会造出「界面上已压缩、这次请求没压」的不一致。UI 侧同时禁用入口。
     * - 已压缩过：切点是压缩区，重复压缩没有语义（要再压就先撤销）。
     * - 没有气泡：没什么可压的。
     *
     * ## 代价必须落账
     *
     * 成功时记 `manual_compress dropped=N kept=M cache_invalidated=false`（INFO，环形缓冲 +
     * 落盘两处，见 [persist]）。W10/S3 起压缩形态 = 保头锚 + 砍中段 + 尾部摘要 ⇒ 头锚
     * 字节不动、前缀缓存对头锚段仍 > 0，故不再整段失效（旧码砍头时此值恒 true，
     * 「压缩 × 缓存命中互斥」已解）。落账意义仍在：可查「哪一次操作改动了前缀」。
     *
     * 无损放弃时记 WARN 并给 [MANUAL_COMPRESS_NOOP] 错误码：用户点了按钮什么都没发生，
     * 必须有一句话解释（否则「压缩按钮坏了」会成为下一个 bug 报告）。
     */
    fun compressContext() {
        val snapshot = state.value
        if (snapshot.isStreaming || snapshot.compression != null || snapshot.bubbles.isEmpty()) {
            return
        }
        scope.launch {
            val endpoint = container.activeEndpoint()
            if (endpoint == null) {
                state.update { it.copy(errorMessage = NO_ENDPOINT) }
                return@launch
            }
            // 预算与自动压缩同源（`contextBudgetTokens` + 同一个窗口档位），只是不扣工具面：
            // 工具声明由内核每轮现算，UI 侧复制一份就是第二个真值源。少扣一点 = 多留一点
            // 历史，方向是保守的（真超了还有内核的自动压缩兜底）。
            val windowCap = contextWindowCap(container)
            // W54 · P0-2：与 PreStepBudget 同一字符 cap，压完不再「立刻熔断」。
            val (budget, charCap) = compressTargets(endpoint, AgentPolicy(maxContextTokens = windowCap))
            val outcome = compressBubbles(snapshot.bubbles, budget, charBudget = charCap)
            if (!outcome.applied) {
                AgentLogStore.warn("manual_compress=noop reason=" + outcome.reason)
                state.update { it.copy(errorMessage = MANUAL_COMPRESS_NOOP) }
                return@launch
            }
            // W10/S3：头锚保留 ⇒ 前缀缓存不整段失效（cache_invalidated=false）。
            val ledger = "manual_compress dropped=" + outcome.droppedMessages +
                " kept=" + outcome.keptMessages.size +
                " cache_invalidated=false"
            AgentLogStore.info(ledger)
            persist(ledger)
            state.update {
                it.copy(compression = outcome.compression)
            }
        }
    }

    /**
     * 撤销手动压缩：只清指针，不回滚任何内容。
     *
     * 撤销同样是一次前缀失效事件（history 又变回全量），所以也落一条账 —— 排查「缓存为什么
     * 又归零了」时，两次操作的先后顺序就是全部线索。
     */
    fun undoCompress() {
        if (state.value.compression == null) return
        state.update { it.copy(compression = null) }
        AgentLogStore.info("manual_compress undone cache_invalidated=true")
        persist("manual_compress undone cache_invalidated=true")
    }

    /**
     * 把一行账**同时**写进两个出口：
     *
     * - `AgentLogStore`（进程内 200 条环形缓冲）—— 诊断页 / 现场取证读这里；
     * - `AppContainer.logs`（`filesDir/harness/logs/agent-*.log`）—— **落盘**，重启后仍在。
     *
     * 为什么两处都要：只写环形缓冲的话「重启后查这次压缩」就没有任何痕迹，而压缩恰恰是
     * 「账单变贵」这类事后才发现的回归的唯一线索；只写文件的话，当场看诊断页又读不到。
     *
     * 写盘失败**只丢这一条账**，绝不影响 UI：磁盘满 / 目录不可写都不该让「点压缩」变成
     * 一次崩溃。⚠️ 这里刻意不用 `runCatching` —— 它会吞掉 `CancellationException`
     * （`AGENTS.md` §五.4），必须显式前置重抛。
     */
    private fun persist(line: String) {
        scope.launch {
            try {
                container.logs.append("INFO", line)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 落盘失败不是用户问题，也不该覆盖内存里的那一条。
            }
        }
    }

    /**
     * 会话导入（W6，`docs/notes/w6-plan.md` §四）：把解析好的题面物化成一整段会话。
     *
     * @param reset 重置会话的回调（= `ChatViewModel.newConversation`）。**必须复用**它，
     *   而不是在这里自己写一份重置：它已经处理了四件必须做的事（递增代次、取消在途 run、
     *   清审批句柄与授权缓存、清节流缓冲）。少做任何一件都会留下一条真实的串台路径 ——
     *   例如旧 run 的 `Cancelled(partialText)` 被追加进新题面。
     *
     * 物化本身是纯函数 [toChatBubbles]（可 JVM 单测：气泡数 == 消息数）。
     * 空列表直接返回：导入 0 条却清空当前会话，是「点一下就把对话弄丢了」。
     */
    fun importConversation(messages: List<ChatMessage>, reset: () -> Unit) {
        if (messages.isEmpty()) return
        reset()
        state.update { it.copy(bubbles = messages.toChatBubbles()) }
        AgentLogStore.info("session import: " + messages.size + " messages")
    }
}
