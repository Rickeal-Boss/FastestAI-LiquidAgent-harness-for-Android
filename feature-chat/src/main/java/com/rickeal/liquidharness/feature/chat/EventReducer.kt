package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TerminationReason
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.newId
import java.util.Locale

/**
 * `AgentEvent` → [ChatUiState] 的**纯函数**归约。
 *
 * 为什么必须是纯函数：流式路径的 bug（重复气泡、半截文本落库、取消后气泡不收敛）
 * 只有在能构造任意事件序列时才测得出。旧仓 `ChatViewModel` 把归约和协程副作用写在一起，
 * 这些分支只能靠真机点。
 *
 * ## 不变量（每条都有单测钉住）
 *
 * 1. **在途文本只有一个真源**：`TextDelta` / `ReasoningDelta` 只改
 *    [ChatUiState.streaming]，不碰 [ChatUiState.bubbles]；定稿事件才生成气泡。
 * 2. `Finished` / `Failed` / `Cancelled` 三个终态**必定**清空 streaming 并把
 *    `isStreaming` 置 false（否则 UI 会永远显示「正在输入」）。
 * 3. `ToolResultReceived` / `ToolSkipped` **只更新不新建**轨迹（`AGENTS.md` §五.3）：
 *    找不到对应 `ToolCallStarted` 时**原样返回**，绝不补一条没有参数的轨迹。
 * 4. `ApprovalRequested` **归约器不碰** [ChatUiState.pendingApproval]（原样返回 state）：
 *    唯一写者是 `ChatViewModel` 的审批 handler。理由见下方 `ApprovalRequested` 分支的注释 ——
 *    两个写者等于两个真值源，而真正决定「这张卡能不能被点掉」的是 handler 手里那个
 *    `CompletableDeferred`，它归约器看不见。**内核仍然必须发这个事件**（它是「该弹卡了」
 *    的唯一信号，W3 起的审计 / 日志收集方也要消费它），只是 UI 归约器不再据此写状态。
 * 5. `Retrying` 与 `StreamReset` **语义不同**（同上）：前者「同一 round 重发请求」，
 *    后者「主循环丢弃本轮已流出的文本」。UI 侧两者都清缓冲，但分支刻意分开写，
 *    将来 Retrying 要加「重试中」提示时不需要动 StreamReset。
 */
object EventReducer {

    fun reduce(state: ChatUiState, event: AgentEvent): ChatUiState = when (event) {
        is AgentEvent.RoundStarted -> state.copy(round = event.round, maxRounds = event.maxRounds)
        is AgentEvent.TextDelta -> appendText(state, event.text)
        is AgentEvent.ReasoningDelta -> appendReasoning(state, event.text)
        is AgentEvent.ToolCallStarted -> startTrace(state, event.call)
        is AgentEvent.ToolResultReceived -> finishTrace(state, event.result)
        is AgentEvent.ToolSkipped -> skipTrace(state, event.call, event.reason)
        // 审批卡**不在归约器里写**（外审 §四.1 的「双写者」）：唯一写者是
        // `ChatViewModel` 的审批 handler —— 它写完 state 还要把同一个 `CompletableDeferred`
        // 存进字段，两者是配对的。归约器在这里再写一遍会形成**隐式顺序依赖**：
        // `onEvent` 先跑 handler（写卡 + 存句柄）再跑 reduce，谁最后写谁赢；
        // 将来 handler 的 PendingApproval 多一个字段（风险档位 / spec），归约器这份
        // 不带该字段的副本就会把它抹掉，而两边内容「看起来一样」，极难定位。
        // 单写者纪律：卡片只能有一个产地。事件本身保留（内核照发，见类头不变量 #4）。
        is AgentEvent.ApprovalRequested -> state
        is AgentEvent.UsageUpdated -> state.copy(
            contextTokens = event.prompt,
            usageLabel = buildUsageLabel(event.prompt, event.completion, event.cachedPrompt),
        )
        is AgentEvent.Finished -> finish(state, event.text, event.terminatedBy)
        is AgentEvent.Failed -> fail(state, event.message, event.terminatedBy)
        is AgentEvent.Cancelled -> finish(state, event.partialText, null)
        // 重发请求 ⇒ 服务端会从头再发一遍，旧文本必须丢弃，否则同一句话出现两遍。
        // 重试必须可见：真机实测「只显示三个点」时，用户无法区分慢与死。
        is AgentEvent.Retrying -> state.copy(
            streaming = StreamingState(isStreaming = true),
            status = ChatStatus.RETRYING,
        )
        // 丢弃本轮已流出文本 ⇒ 同上清空，但不动「是否在流式」。
        is AgentEvent.StreamReset -> state.copy(
            streaming = state.streaming.copy(text = "", reasoning = ""),
        )
        // 以下事件本仓 W1 无对应 UI：plan 工具 / 技能系统 / MCP 都还没接，
        // 显式列出而不是 `else`，新增事件时编译期强制处理。
        is AgentEvent.MessageCommitted,
        is AgentEvent.SkillLoaded,
        is AgentEvent.PlanUpdated,
        is AgentEvent.ToolSourceUpdated,
        -> state
    }

    /**
     * 追加流式正文。节流由调用方（`ChatViewModel`）负责：它把 120ms 内的增量合并成一次
     * 调用，本函数只做纯追加 —— 这样「节流窗口多大」不进入归约逻辑，可独立调整。
     */
    fun appendText(state: ChatUiState, text: String): ChatUiState {
        if (text.isEmpty()) return state
        return state.copy(
            streaming = state.streaming.copy(
                text = state.streaming.text + text,
                isStreaming = true,
            ),
            isStreaming = true,
            errorMessage = null,
            // 有正文产出 ⇒ 重试已恢复，提示必须消失，否则会一直挂着。
            status = null,
        )
    }

    /** 追加思考通道内容。与正文分开存，避免 reasoning 被当成回答正文发回模型。 */
    fun appendReasoning(state: ChatUiState, text: String): ChatUiState {
        if (text.isEmpty()) return state
        return state.copy(
            streaming = state.streaming.copy(
                reasoning = state.streaming.reasoning + text,
                isStreaming = true,
            ),
            isStreaming = true,
        )
    }

    /** 追加用户气泡并清空输入框。 */
    fun appendUser(
        state: ChatUiState,
        text: String,
        attachments: List<com.rickeal.liquidharness.core.model.Attachment> = emptyList(),
    ): ChatUiState =
        if (text.isBlank() && attachments.isEmpty()) {
            state
        } else {
            state.copy(
                bubbles = state.bubbles + ChatBubble(
                    id = newId(),
                    role = Role.USER,
                    text = text,
                    attachments = attachments,
                ),
                input = "",
                // 附件随用户消息一起「消耗」：留着会跟着下一条消息再发一次（重复计费）。
                attachments = emptyList(),
                errorMessage = null,
                terminatedBy = null,
                toolTraces = emptyList(),
            )
        }

    /**
     * 新建轨迹。**唯一的创建点** —— [finishTrace] / [skipTrace] 只更新。
     *
     * id 取 `ToolCall.id`：它就是内核侧 `ToolResult.callId` 的回传键。
     * 为空时补一个 `newId()`（坏 JSON 场景下模型可能给出空 id，补 id 好过让轨迹
     * 永远停在 RUNNING —— 那样它既更新不到也折叠不了）。
     */
    private fun startTrace(state: ChatUiState, call: ToolCall): ChatUiState {
        val trace = ToolTrace(
            id = call.id.ifBlank { newId() },
            name = call.name,
            arguments = call.argumentsJson,
        )
        return state.copy(toolTraces = state.toolTraces + trace)
    }

    /**
     * 回填结果。**只更新不新建**：找不到轨迹就原样返回（`AGENTS.md` §五.3 的第一条）。
     *
     * 上屏一律用 [ToolResult.shown]（脱敏后的展示文本），不直接读 `output` ——
     * `output` 可能含 API Key 之类的原文（见 `ToolResult` KDoc）。
     */
    private fun finishTrace(state: ChatUiState, result: ToolResult): ChatUiState {
        val index = state.toolTraces.indexOfLast { it.id == result.callId }
        if (index < 0) return state
        val next = state.toolTraces.toMutableList()
        next[index] = next[index].copy(
            status = if (result.ok) ToolTraceStatus.OK else ToolTraceStatus.FAILED,
            result = result.shown,
            elapsedMillis = result.elapsedMillis,
        )
        return state.copy(toolTraces = next)
    }

    /** 跳过（策略拒绝 / 审批拒绝）。同样**只更新不新建**。 */
    private fun skipTrace(state: ChatUiState, call: ToolCall, reason: String): ChatUiState {
        val index = state.toolTraces.indexOfLast { it.id == call.id }
        if (index < 0) return state
        val next = state.toolTraces.toMutableList()
        next[index] = next[index].copy(status = ToolTraceStatus.SKIPPED, result = reason)
        return state.copy(toolTraces = next)
    }

    /**
     * 收敛：把在途内容定稿成一条气泡。
     *
     * [finalText] 为空时保留累积文本（服务端没给 `finish_reason` 的半截流场景，
     * `AGENTS.md` §五.6）；正文、思考、工具轨迹**全空**时不生成气泡 ——
     * 空气泡会让用户以为「模型回了但内容丢了」，而实际是什么都没发生。
     *
     * 工具轨迹在这里搬进气泡：run 结束后流式区整体消失，轨迹留在状态里就再也没人渲染。
     */
    private fun finish(
        state: ChatUiState,
        finalText: String,
        terminatedBy: TerminationReason?,
    ): ChatUiState {
        val text = finalText.ifEmpty { state.streaming.text }
        val reasoning = state.streaming.reasoning
        val traces = state.toolTraces
        val bubbles = if (text.isBlank() && reasoning.isBlank() && traces.isEmpty()) {
            state.bubbles
        } else {
            state.bubbles + ChatBubble(
                id = newId(),
                role = Role.MODEL,
                text = text,
                reasoning = reasoning,
                traces = traces,
            )
        }
        return state.copy(
            bubbles = bubbles,
            streaming = StreamingState(),
            toolTraces = emptyList(),
            // 一个字都没产出就被停止 ⇒ 明确告诉用户「已停止」，而不是留一个空落点。
            // 有内容时不提示：气泡本身就是落点。
            status = if (text.isBlank() && reasoning.isBlank() && traces.isEmpty()) {
                ChatStatus.CANCELLED_EMPTY
            } else {
                null
            },
            isStreaming = false,
            pendingApproval = null,
            terminatedBy = terminatedBy,
        )
    }

    /**
     * 失败：保留已收到的部分文本（用户至少能看到模型说到哪了），并置错误文案。
     *
     * 部分文本不设 `errorMessage` 到气泡上 —— 错误条已经在状态区显示一次，
     * 同一句话在气泡里再出现一次只是噪声。
     */
    private fun fail(
        state: ChatUiState,
        message: String,
        terminatedBy: TerminationReason?,
    ): ChatUiState {
        val text = state.streaming.text
        val reasoning = state.streaming.reasoning
        val traces = state.toolTraces
        val bubbles = if (text.isBlank() && reasoning.isBlank() && traces.isEmpty()) {
            state.bubbles
        } else {
            state.bubbles + ChatBubble(
                id = newId(),
                role = Role.MODEL,
                text = text,
                reasoning = reasoning,
                traces = traces,
            )
        }
        return state.copy(
            bubbles = bubbles,
            streaming = StreamingState(),
            toolTraces = emptyList(),
            isStreaming = false,
            errorMessage = message,
            pendingApproval = null,
            terminatedBy = terminatedBy,
        )
    }

    /** `1.2k in / 340 out (200 cached)`。纯 ASCII：中文文案一律走 `strings.xml`。 */
    /**
     * 上下文用量标签。
     *
     * ## 为什么 `cached` 恒显示（含 0）
     *
     * 旧实现只在 `cached > 0` 时追加 "(N cached)" ⇒ 「缓存没命中」与「provider 没报这个字段」
     * 在界面上**完全同形**，而这两件事的处置相反（前者查前缀，后者查端点）。所以：
     * - `prompt > 0` ⇒ 恒输出 `(N cached, R%)`，0 也输出 `(0 cached, 0.0%)`；
     * - `prompt <= 0` ⇒ 只输出 `(N cached)`（分母未知时算不出比率，不编一个 0.0%）。
     *
     * ⚠️ 能力边界：本标签只回答「**这次请求有没有命中**」，不回答「为什么没命中」。
     * 缓存命中的最小前缀长度是**端点侧性质**（GLM-4.7 实测 ≈4096 token），短前缀下
     * `0.0%` 是**正确值**，不是缺陷。诊断时要先看上下文总量再看命中率。
     *
     * ⚠️ **不许**用本标签区分「命中 0」与「provider 未报告该字段」：
     * `OpenAiCompatibleAdapter` 在解析层就把缺失的 `cached_tokens` 压成了 `0`（`:136`），
     * 类型一路是 `Int`。要能区分必须把契约改成 `Int?` / `cachedReported`，属 W6；
     * 在那之前，任何「0 cached 就是没命中」的断言都不成立。
     */
    private fun buildUsageLabel(prompt: Int, completion: Int, cached: Int): String {
        if (prompt <= 0 && completion <= 0) return ""
        val cachePart = if (prompt > 0) {
            val rate = cached * 100.0 / prompt
            " (" + cached + " cached, " + String.format(Locale.US, "%.1f", rate) + "%)"
        } else {
            " (" + cached + " cached)"
        }
        return compact(prompt) + " in / " + compact(completion) + " out" + cachePart
    }

    private fun compact(value: Int): String = when {
        value >= 1000 -> ((value / 100) / 10.0).toString() + "k"
        else -> value.toString()
    }
}
