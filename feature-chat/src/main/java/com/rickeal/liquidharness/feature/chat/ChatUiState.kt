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

    /**
     * 请求已发出、**首个字节还没到**。
     *
     * 与 [RETRYING] 分开的理由：重试是「已经在动，只是重来一次」，等待是「一个字节都还没回」。
     * 前者用户能做的是等，后者用户可能需要检查网络 —— 而且等待会一直涨秒数，
     * 涨到 `generationTimeoutMillis` 就会变成失败。混成一句话会让「卡死」看起来像「在重试」。
     *
     * 进入：`RoundStarted`。离开：首个 `TextDelta` / `ReasoningDelta` / `ToolCallStarted`，
     * 或任何终态事件。
     *
     * ## `UsageUpdated` 不解除等待态 —— 这是**有意**的（W6 钉住）
     *
     * 两个口径刻意不同，混用会各自错一半：
     * - **UI 语义 = 「模型开口」**：用户要看到的是「回答开始出来了」。`UsageUpdated` 是
     *   计费账（`prompt` / `completion` 数字），它在有些端点上**先于正文**到达，
     *   此刻屏幕上一个字都还没有 —— 把它当「开口」会让状态行提前消失，用户看到的是
     *   「圈没了、也没内容」，比一直转圈更像死机。
     * - **看门狗语义 = 「连接产出」**：首字节超时判的是「这条连接到底有没有吐东西」，
     *   任何帧（含 usage 帧）都算产出，所以内核侧看到 usage 就该重置超时计时。
     *
     * 归约器只行使前一条（见 `EventReducer` 的 `UsageUpdated` 分支原样返回 `status`），
     * 后一条归内核看门狗。**改这一条前先想清楚改的是哪个语义**：让 usage 解除等待态，
     * 等于让 UI 替看门狗做判断，而 UI 拿不到超时时间。
     */
    WAITING_FIRST_BYTE,

    /** 用户主动停止且本轮没有任何正文产出 —— 需要一个「已停止」的落点，
     *  否则提问气泡下面空着，看起来像回答丢失。 */
    CANCELLED_EMPTY,
}

/**
 * 等待态状态行的秒数：毫秒 → 整秒（向下取整，下限 0）。
 *
 * 抽成纯函数是为了能在 JVM 单测里断言「秒数怎么跳」—— Compose 侧只负责每秒累加
 * 1000ms 再喂给它。文案本体（含中文）在 `strings.xml`（守卫 G16），本函数只产出数字。
 */
internal fun waitingSeconds(elapsedMillis: Long): Int =
    (elapsedMillis / 1_000L).toInt().coerceAtLeast(0)

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
    /** 非空 = 输入区上方渲染一行状态提示（重试中 / 等待首字节 / 已停止）。 */
    val status: ChatStatus? = null,
    /**
     * 手动压缩切点（气泡 id）。非空 = 该气泡**及其之前**的气泡不再进 history。
     *
     * 只影响「发回模型的字节」：气泡本体仍留在 [bubbles] 里，用户可以展开回看。
     * 真值源仍只有一处 —— [bubbles]；本字段只是一个**指针**，不是第二份内容。
     * 撤销 = 清空本字段（`ChatViewModel.undoCompress`），不需要回滚任何数据。
     */
    val compressedUpTo: String? = null,
    /** 手动压缩省下的估算 token。0 = 未压缩 / 没省下。只用于分隔卡上的副标题。 */
    val compressedSavingTokens: Int = 0,
) {
    /** 发送按钮可用性：非流式、有输入或有附件、已配置端点。 */
    val canSend: Boolean
        get() = !isStreaming &&
            (input.isNotBlank() || attachments.isNotEmpty()) &&
            endpointLabel != null

    /**
     * 被切点移出 history 的气泡数（分隔卡上的「已压缩 N 条」）。
     *
     * 派生而不是另存一个计数：多存一个数就多一处会与 [bubbles] 漂移的真值。
     * 切点失效（id 不在列表里）时返回 0，与 `toHistoryMessages(fromId)` 的回落一致。
     */
    val compressedBubbleCount: Int
        get() {
            val id = compressedUpTo ?: return 0
            val index = bubbles.indexOfFirst { it.id == id }
            return if (index < 0) 0 else index + 1
        }
}
