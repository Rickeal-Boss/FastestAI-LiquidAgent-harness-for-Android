package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.agent.context.contextBudgetTokens
import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.data.ContextLimitWriteback
import com.rickeal.liquidharness.core.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 「整段会话级」的两个动作：**手动压缩**与**会话导入**。
 *
 * ## 为什么从 `ChatViewModel` 里搬出来（这是 G10 的处置，不是分层）
 *
 * `ChatViewModel` 单文件撞到 500 行闸门（`scripts/arch-guard.sh` 的 LINE_CAPS），
 * 本仓的纪律是**拆文件**、不是调上限（`01-architecture.md` §二）。搬出来的判据很简单：
 * 这两个动作与「跑 run / 流式节流 / 审批句柄」那套时序逻辑零耦合 —— 它们只读一次
 * `state` 快照、写一次 `state`，不碰 `runGeneration` / `flushBuffer` /
 * `pendingApprovalDeferred`。放在同一个类里纯属历史原因。
 *
 * 依赖显式注入（`container` / `state` / `scope` / 重置回调）而不是持有 ViewModel：
 * 这样本类不需要 `ChatViewModel` 的任何私有成员，也就不会为了「少一行」而把私有字段
 * 提成 `internal`。
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
     * 手动压缩（W6，`docs/notes/w6-plan.md` §三）。
     *
     * ## 压的是「气泡映射出的 history」，不是内核里的上下文
     *
     * history 的真值源就是 [ChatUiState.bubbles]；内核侧没有跨 run 的上下文可压
     * （`RunState` 每 run 新建）。所以压缩必须在这里做，产物落到
     * [ChatUiState.compressedUpTo] 这个**气泡指针**上 —— 否则下一次 run 又从全量气泡
     * 重建，压缩等于没做。
     *
     * ## 三条前置拒绝（都静默返回，不弹错误）
     *
     * - 在途 run：压缩会改写「本轮已发出的历史」，而正在跑的 run 手里是另一份副本；
     *   允许它只会造出「界面上已压缩、这次请求没压」的不一致。UI 侧同时禁用入口。
     * - 已压缩过：切点是单指针，重复压缩没有语义（要再压就先撤销）。
     * - 没有气泡：没什么可压的。
     *
     * ## 代价必须落账
     *
     * 成功时记 `manual_compress dropped=N kept=M cache_invalidated=true`（INFO，环形缓冲 +
     * 落盘两处，见 [persist]）。`cache_invalidated` 不是装饰字段：压缩让前缀从第一个字节起
     * 变化，下一次请求 provider 侧的缓存必然整段重建、`cached_tokens` 归 0、账单更贵 ——
     * 这是压缩的固有代价，账上必须能查到「哪一次操作把它打掉了」。
     *
     * 无损放弃时记 WARN 并给 [MANUAL_COMPRESS_NOOP] 错误码：用户点了按钮什么都没发生，
     * 必须有一句话解释（否则「压缩按钮坏了」会成为下一个 bug 报告）。
     */
    fun compressContext() {
        val snapshot = state.value
        if (snapshot.isStreaming || snapshot.compressedUpTo != null || snapshot.bubbles.isEmpty()) {
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
            val windowCap = runCatching { container.settings.maxContextTokens.first() }
                .getOrDefault(AgentPolicy().maxContextTokens)
            val budget = contextBudgetTokens(endpoint, AgentPolicy(maxContextTokens = windowCap))
            val outcome = compressBubbles(snapshot.bubbles, budget)
            if (!outcome.applied) {
                AgentLogStore.warn("manual_compress=noop reason=" + outcome.reason)
                state.update { it.copy(errorMessage = MANUAL_COMPRESS_NOOP) }
                return@launch
            }
            val ledger = "manual_compress dropped=" + outcome.droppedMessages +
                " kept=" + outcome.keptMessages.size +
                " cache_invalidated=true"
            AgentLogStore.info(ledger)
            persist(ledger)
            state.update {
                it.copy(
                    compressedUpTo = outcome.compressedUpTo,
                    compressedSavingTokens = outcome.savingTokens,
                )
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
        if (state.value.compressedUpTo == null) return
        state.update { it.copy(compressedUpTo = null, compressedSavingTokens = 0) }
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
