package com.rickeal.liquidharness.core.engine.provider

import com.rickeal.liquidharness.core.model.DeltaTracker
import com.rickeal.liquidharness.core.model.FinishReason
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.ProviderKind
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.TokenUsage
import com.rickeal.liquidharness.core.net.SseEvent
import kotlinx.serialization.json.JsonObject

/**
 * 一条流的解码侧状态。
 *
 * 为什么由引擎持有、而不是 adapter 内部持有：adapter 必须是**无状态**的
 * （按 `endpointId` 缓存复用，见 01-architecture §6.3）。状态放在这里，
 * 一个 adapter 实例就能同时服务多条并发流。
 *
 * 引擎负责在收到事件后回填本对象（见 `RemoteLlmEngine.track`），
 * 再交给 [ProviderAdapter.finish] 做终帧裁决。
 */
class AdapterStreamState {
    /** 供应商显式回报的 finish_reason。非 null ⇒ 不再补终帧。 */
    var finishReason: FinishReason? = null

    var usage: TokenUsage? = null

    /** 是否见到本族的结束标记（OpenAI `[DONE]` / Anthropic `message_stop`）。 */
    var sawTerminalMarker: Boolean = false

    /** 已产出的正文字符数。**不是 token 数** —— 这里只判「有没有内容」。 */
    var textChars: Int = 0

    var eventCount: Int = 0

    /**
     * 正文增量判定器（每次尝试一个实例）。
     *
     * 存在理由：部分 OpenAI 兼容代理返回**累积式** content（每帧是从开头到现在的
     * 完整前缀）。原样透出会让下游按增量拼接 ⇒ 文本按 O(n²) 膨胀
     * （旧仓 `StreamAccumulator` KDoc 记录的 P1）。
     *
     * 放在引擎层而不是内核层：内核只应看到**真实增量**，UI 与 `RunState`
     * 都不该知道供应商用了哪种语义（`docs/04-risk-register.md` R19）。
     */
    val textTracker: DeltaTracker = DeltaTracker()

    /** 思考通道的独立判定器。与正文分开：两条通道的帧序列互不相干。 */
    val reasoningTracker: DeltaTracker = DeltaTracker()

    /**
     * 已收到的工具调用碎片数。
     *
     * **必须计入「已向上游 emit 过内容」**：工具优先的模型常常只吐
     * `tool_calls` 碎片、一个正文字符都没有。若重试判据只看 `textChars`，
     * 这类流中断后会被判「可重试」，而内核侧 `fragments` 是 per-attempt 的、
     * 跨引擎内部重试**不重置** ⇒ 两次尝试的 arguments 碎片被拼进同一个 slot
     * ⇒ `{"path":"a"}{"path":"a"}` 坏 JSON（外审 P0-3）。
     */
    var toolCallFragments: Int = 0

    /** 是否已经向上游 emit 过任何内容（正文 / 思考 / 工具碎片）。重试判据的唯一真值。 */
    val hasEmittedAny: Boolean
        get() = textChars > 0 || toolCallFragments > 0

    /**
     * 归一化一条待 emit 的事件。
     *
     * - `TextDelta` / `ReasoningDelta`：先喂 [DeltaTracker]，只放行真实增量；
     *   累积式的重复帧 ⇒ 返回 null（不 emit 空串，避免 UI 收到无意义事件）。
     * - `ToolCallDelta`：累计碎片数（判定用），原样放行。
     * - 其余事件原样放行。
     *
     * 归一化必须在 `emit` **之前**：一旦上游看到重复文本，回滚就不可能了。
     */
    fun normalize(event: GenerationEvent): GenerationEvent? = when (event) {
        is GenerationEvent.TextDelta -> {
            val delta = textTracker.feed(event.text)
            if (delta.isEmpty()) null else GenerationEvent.TextDelta(delta)
        }

        is GenerationEvent.ReasoningDelta -> {
            val delta = reasoningTracker.feed(event.text)
            if (delta.isEmpty()) null else GenerationEvent.ReasoningDelta(delta)
        }

        is GenerationEvent.ToolCallDelta -> {
            toolCallFragments++
            event
        }

        else -> event
    }
}

/**
 * 供应商适配器。
 *
 * 三族（OpenAI 兼容 / Anthropic / Gemini）在六处结构不同（01-architecture §3.2 表），
 * 所以是**三个实现**，不是三个 `if`。W1 只落地 [OpenAiCompatibleAdapter]。
 *
 * 实现约定：
 * - **无状态**：所有可变状态进 [AdapterStreamState]；
 * - 不持有密钥：密钥由调用方作为参数传入，用完即弃；
 * - 不发网络请求：URL 与 header 只是「怎么发」的声明。
 */
interface ProviderAdapter {

    val kind: ProviderKind

    /** 端点 → 实际 URL。取代旧仓 `RemoteEndpoint.chatCompletionsUrl()` 的硬编码。 */
    fun endpointUrl(endpoint: RemoteEndpoint): String

    /**
     * 请求头。`Authorization` 在这里生成。
     * @param secret 现取的密钥（**不进任何 data class**）。null / 空 = 无鉴权端点。
     */
    fun headers(endpoint: RemoteEndpoint, secret: String?): Map<String, String>

    /**
     * 请求体。
     *
     * 用 `buildJsonObject` 手搭而不是 `@Serializable` 中介类：三族的 body 差异太大，
     * 中介类会退化成一堆 nullable 字段 + 一堆「这族不填这个」的注释，
     * 而 nullable 字段的默认值会静默混进别族的请求体。
     */
    fun buildBody(request: GenerationRequest): JsonObject

    /** 一帧 SSE → 0..n 个事件。**这是唯一需要为每族单独写死的部分。** */
    fun decodeEvent(event: SseEvent): List<GenerationEvent>

    /** 该帧是否为本族的结束标记。引擎据此停止读流（不必等 TCP FIN）。 */
    fun isTerminal(event: SseEvent): Boolean

    /**
     * 收尾裁决。**所有族共用同一条截断规则**（[terminationReason]）：
     * 供应商没给 finish_reason、也不是正常结束 ⇒ 有内容就是 [FinishReason.LENGTH]。
     * 这条规则不能让各 adapter 自己写，否则「半截回答被当完整回答落库」
     * 会在某一族上复活（AGENTS.md §五.6）。
     */
    fun finish(state: AdapterStreamState): GenerationEvent.Finished
}

/**
 * 终帧裁决的唯一实现。三族共用。
 *
 * 判定顺序（每一步都有现实动因）：
 * 1. 供应商给了 finish_reason ⇒ 以它为准（最权威）；
 * 2. 没给但见到了结束标记 ⇒ [FinishReason.STOP]（服务端显式宣告结束）；
 * 3. 一个正文字符都没产出 ⇒ [FinishReason.STOP]（空回答是「模型不想说」，不是截断）；
 * 4. 有内容、既没 finish_reason 也没结束标记 ⇒ [FinishReason.LENGTH]（连接被掐断）。
 */
internal fun terminationReason(state: AdapterStreamState): FinishReason {
    state.finishReason?.let { return it }
    if (state.sawTerminalMarker) return FinishReason.STOP
    if (state.textChars == 0) return FinishReason.STOP
    return FinishReason.LENGTH
}
