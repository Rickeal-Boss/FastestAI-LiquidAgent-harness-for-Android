package com.rickeal.liquidharness.feature.chat

import androidx.compose.runtime.Immutable
import com.rickeal.liquidharness.core.model.Attachment
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.TerminationReason

/**
 * 一条**已定稿**的上屏气泡。
 *
 * 与 `core-model.ChatMessage` 刻意分开：`ChatMessage` 是**线上契约**（含 toolCalls /
 * toolResults，会被序列化进请求），本类是**视图状态**（含 reasoning / 折叠用的工具轨迹）。
 * 混用会导致「把半截流式文本当成历史发回模型」这类 bug。
 *
 * @param streaming 流式标记。历史气泡恒 false；true 只出现在 UI 侧用 [StreamingState]
 *   临时拼出来的那条在途气泡上（见 `ChatMessageList` 的 streaming item）。
 * @param reasoning 思考通道内容。与正文分开展示，不混进 [text]，也**不回灌**给模型。
 * @param traces 本气泡产出的工具轨迹。run 结束由归约器从 `ChatUiState.toolTraces`
 *   搬进来 —— 否则工具过程会随「流式区消失」一起从屏幕上蒸发（旧仓靠 message.toolCalls
 *   承担同一职责）。
 */
@Immutable
data class ChatBubble(
    val id: String,
    val role: Role,
    val text: String,
    val streaming: Boolean = false,
    val reasoning: String = "",
    val traces: List<ToolTrace> = emptyList(),
    /**
     * 本气泡的附件（W3）。只有 USER 气泡会非空。
     *
     * 只用于**展示缩略图**与回灌 history：图片本体在 `Attachment.base64` 里，
     * 随 `ChatMessage` 一起走请求体，不依赖这里的 `localPath`（缓存可能被系统清掉）。
     */
    val attachments: List<Attachment> = emptyList(),
)

/**
 * 顶栏/输入区上方的一行状态提示。
 *
 * 用枚举而不是字符串：本模块不许出现中文硬编码（守卫 G16），
 * 文案由 `ChatScreen` 用 `stringResource` 映射；同时枚举让「哪些状态值得提示」
 * 成为可枚举的清单，而不是散落的布尔。
 */
enum class ChatStatus {
    /** 同一 round 正在重发请求（网络抖动 / 5xx）。真机实测：没有这行提示时，
     *  用户看到的是「三个点永远在跳」，无法区分「慢」与「死」。 */
    RETRYING,

    /** 用户主动停止且本轮没有任何正文产出 —— 需要一个「已停止」的落点，
     *  否则提问气泡下面空着，看起来像回答丢失。 */
    CANCELLED_EMPTY,
}

/** 工具轨迹状态。RUNNING 与 FAILED **永不折叠**（见 `ToolTraceGroup`）。 */
enum class ToolTraceStatus { RUNNING, OK, FAILED, SKIPPED }

/**
 * 一条工具轨迹。
 *
 * [id] 直接取 `ToolCall.id`：`ToolResultReceived` / `ToolSkipped` 只按这个 id 更新，
 * 不新建（`AGENTS.md` §五.3）。
 */
@Immutable
data class ToolTrace(
    val id: String,
    val name: String,
    val arguments: String,
    val status: ToolTraceStatus = ToolTraceStatus.RUNNING,
    val result: String? = null,
    val elapsedMillis: Long = 0L,
)

/**
 * 流式通道内容。
 *
 * 与 [ChatUiState.bubbles] 分开的理由：气泡列表是**低频**的（一轮一条），流式文本是
 * **高频**的（120ms 一次 flush）。放在同一个字段里会让每次 flush 都换掉气泡列表的引用，
 * LazyColumn 的 item 跳过判定全部失效。
 *
 * 单一真源：在途文本只存在这里，定稿时才变成 [ChatBubble]；绝不两边各存一份。
 */
@Immutable
data class StreamingState(
    val text: String = "",
    val reasoning: String = "",
    val isStreaming: Boolean = false,
) {
    val isEmpty: Boolean get() = text.isEmpty() && reasoning.isEmpty()
}

/**
 * 一次等待用户裁决的工具调用。
 *
 * 只承载**展示所需**的字段；裁决句柄（`CompletableDeferred<ToolApprovalDecision>`）
 * **刻意不放在这里**，而是留在 [ChatViewModel] 的私有字段 —— 状态对象会被复制、
 * 进 Compose 快照、被日志打印，把一个挂起句柄塞进来既泄漏又没有任何展示价值。
 *
 * ## 唯一写者 = [ChatViewModel] 的审批 handler（外审 §四.1）
 *
 * `EventReducer` **不再**根据 `AgentEvent.ApprovalRequested` 写这个字段：写卡与存句柄
 * 必须发生在同一处，否则会出现「卡在、句柄不在」（点不掉的卡）或反过来的分家状态。
 * 归约器只处理轨迹类事件（`ToolCallStarted` / `ToolResultReceived` / `ToolSkipped`）。
 *
 * 历史：W2U 时本 KDoc 写的是「本仓内核尚无审批回传通道，放一个永远没人 complete
 * 的句柄比不显示这张卡更误导」。**W2 已把通道接通**（`AgentRunner.run(request, approval)`
 * → `TurnContext.approval` → `ToolApprovalFlow`），那条判断的前提不再成立，
 * 已按现状改写 —— 过期注释在本仓等同于错误注释。
 */
@Immutable
data class PendingApproval(
    val callId: String,
    val toolName: String,
    val arguments: String,
)

/**
 * 对话页状态。
 *
 * 全部字段不可变 + `copy` 更新，使得 [EventReducer] 可以是纯函数 —— 旧仓把归约与副作用
 * 缠在 `ChatViewModel`（901 行）里，导致流式节流这类逻辑只能靠真机验证。
 *
 * 刻意**不含**旧仓的 `availableModels` / `activeModel` / `capabilityMode` /
 * `disclosureMode` / `config`：本仓是纯 API 路径，没有「本机加载哪个模型」「能力档位」
 * 这些概念，留着就是永远不生效的字段。
 */
@Immutable
data class ChatUiState(
    /** 已定稿气泡。在途内容在 [streaming]。 */
    val bubbles: List<ChatBubble> = emptyList(),
    /** 在途流式内容（低频流的另一半：高频，只被流式 item 读）。 */
    val streaming: StreamingState = StreamingState(),
    /** 本次 run 的工具轨迹；终态时整体搬进气泡的 [ChatBubble.traces]。 */
    val toolTraces: List<ToolTrace> = emptyList(),
    /** 非空 = 渲染一张等待授权的提示卡。唯一写者是 `ChatViewModel` 的审批 handler（见 [PendingApproval]）。 */
    val pendingApproval: PendingApproval? = null,
    /** 输入框草稿。 */
    val input: String = "",
    /** 是否有在途 run。发送按钮与停止按钮都看它。 */
    val isStreaming: Boolean = false,
    /** 用户可见的错误文案。null = 无错误。 */
    val errorMessage: String? = null,
    /** 顶栏副标题：当前端点名 + 模型。null = 未配置端点。 */
    val endpointLabel: String? = null,
    /** 最近一次 usage 的 prompt 侧 token 数（上下文占用量）。0 = 尚无数据。 */
    val contextTokens: Int = 0,
    /** 端点声明的上下文窗口（`RemoteEndpoint.contextLength`）。0 = 未知。 */
    val contextLimit: Int = 0,
    /** 上一轮的用量摘要（如 `1.2k in / 340 out`）。 */
    val usageLabel: String? = null,
    /** 当前轮次 / 轮次上限（顶栏「3/12 轮」）。 */
    val round: Int = 0,
    val maxRounds: Int = 0,
    /** 非正常终止原因。只用于渲染一行小字；null = 正常结束。 */
    val terminatedBy: TerminationReason? = null,
    /**
     * 输入框待发送的附件（W3 多模态）。发送成功后清空。
     *
     * 与 [ChatBubble.attachments] 是两个阶段：这里是「还没发」，那边是「已经发过」。
     * 发送失败时**不清空**（否则用户要重新选一次图）。
     */
    val attachments: List<Attachment> = emptyList(),
    /** 非空 = 输入区上方渲染一行状态提示（重试中 / 已停止）。 */
    val status: ChatStatus? = null,
) {
    /** 发送按钮可用性：非流式、有输入或有附件、已配置端点。 */
    val canSend: Boolean
        get() = !isStreaming &&
            (input.isNotBlank() || attachments.isNotEmpty()) &&
            endpointLabel != null
}
