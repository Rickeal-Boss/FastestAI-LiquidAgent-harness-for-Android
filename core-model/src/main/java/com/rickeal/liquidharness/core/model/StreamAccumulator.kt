package com.rickeal.liquidharness.core.model

/**
 * 内容增量判定器。
 *
 * **存在理由（旧仓踩过 P1）**：部分 OpenAI 兼容代理返回**累积式** content ——
 * 每帧是「从开头到现在的完整前缀」，而不是新增片段。若一律按增量拼接，
 * 文本会翻倍；若一律按累积处理，Anthropic 的 `partial_json`（纯增量）会丢字。
 * 两种模式在同一份 `TextDelta` 契约下混用必然出错，所以判定必须在这里做一次。
 *
 * ## 判定规则：两击确认 + 单帧延迟
 *
 * 首帧只入 buffer、不判定。之后**必须连续两帧**都满足前缀关系才确认为累积式：
 * - 第一击（第二帧以首帧为前缀，或与首帧完全相同）⇒ 帧进 [pending]，**本帧不输出**；
 * - 第二击（第三帧以 [pending] 为前缀）⇒ 确认累积式，一次补出两帧的增量；
 * - 任一击不成立 ⇒ 增量式，待决帧与当前帧整帧输出。
 *
 * 为什么不在第二帧就锁：增量流里第二帧恰好以第一帧开头并不罕见
 * （重复 token：`哈` + `哈哈`；英文 `the` + `theatre`）。旧实现只看两帧，
 * 会把这类增量流误判成累积式 ⇒ 第二帧只放行后缀、丢掉重叠前缀，第三帧才自愈，
 * 净效果是**正文丢一段**。W2U 把本类接进所有流的生产路径后，这个启发式从死代码
 * 变成了活体风险。
 *
 * 代价是歧义帧会晚一帧到达下游。流结束时调用方**必须**调 [flush]，
 * 否则最后一帧会被永久吞掉（[StreamAccumulator] 与 `AdapterStreamState` 已接线）。
 *
 * 空命中语义：空帧返回空串，不改变内部状态（心跳帧不会污染判定）。
 *
 * 消费者：[StreamAccumulator]、`core-agent` 的流式信号处理。
 */
class DeltaTracker {
    private val buffer = StringBuilder()

    /** `null` = 尚未判定；`true` = 累积式；`false` = 增量式。 */
    private var cumulative: Boolean? = null

    /** 已收到但语义未定的帧（第二击的候选）。 */
    private var pending: String? = null

    /** 已累积的全文（含 [flush] 补入的待决帧）。 */
    val text: String get() = buffer.toString()

    /** 供测试与诊断：null = 尚未判定（只收到 0~1 帧，或第二帧仍是歧义帧）。 */
    val isCumulative: Boolean? get() = cumulative

    /**
     * 喂一帧。
     * @return 本帧**真正新增**的文本（歧义帧返回空串，由 [flush] 或下一帧补出）。
     */
    fun feed(frame: String): String {
        if (frame.isEmpty()) return ""
        if (buffer.isEmpty()) {
            buffer.append(frame)
            return frame
        }
        val current = buffer.toString()
        return when (cumulative) {
            true -> feedCumulative(frame, current)
            false -> {
                buffer.append(frame)
                frame
            }
            null -> feedUndecided(frame, current)
        }
    }

    /**
     * 流结束时补出待决帧：歧义帧按**增量式**处理（更保守：多输出一帧只会重复，
     * 少输出一帧就是永久丢字）。重复调用安全 —— 没有待决帧时返回空串。
     */
    fun flush(): String {
        val waiting = pending ?: return ""
        pending = null
        buffer.append(waiting)
        return waiting
    }

    fun reset() {
        buffer.setLength(0)
        cumulative = null
        pending = null
    }

    private fun feedCumulative(frame: String, current: String): String {
        if (frame.length >= current.length && frame.startsWith(current)) {
            val appended = frame.substring(current.length)
            buffer.setLength(0)
            buffer.append(frame)
            return appended
        }
        // 前缀关系被破坏（服务端重排 / 换模板）：切回增量语义，整帧当新增。
        //
        // ⚠️ 这里**不能** `buffer.setLength(0); buffer.append(frame)`：
        // 那会把此前累积的全文丢掉，而下游按「旧文 + 本帧」拼接 ⇒
        // tracker.text 与下游文本从这一帧起永久发散（旧仓遗留缺陷，
        // 外审 P0-2 实测指出）。正确动作是把本帧**追加**到已有 buffer。
        cumulative = false
        buffer.append(frame)
        return frame
    }

    /** 尚未判定（`cumulative == null`）时的两击状态机。 */
    private fun feedUndecided(frame: String, current: String): String {
        val waiting = pending
        if (waiting == null) {
            if (frame == current) {
                // 完全重复帧 = 累积式特征（增量流不会重发整段）。
                cumulative = true
                return ""
            }
            if (frame.startsWith(current)) {
                // 歧义：既可能是累积式的第二帧，也可能是「以首帧开头」的增量帧。
                pending = frame
                return ""
            }
            cumulative = false
            buffer.append(frame)
            return frame
        }

        if (frame.startsWith(waiting)) {
            // 第二击成立：确认累积式。待决帧的增量现在才补得出来。
            val first = waiting.substring(current.length)
            buffer.setLength(0)
            buffer.append(waiting)
            val second = frame.substring(buffer.length)
            buffer.setLength(0)
            buffer.append(frame)
            cumulative = true
            pending = null
            return first + second
        }

        // 第二击不成立：不是累积式，待决帧与当前帧都整帧输出。
        cumulative = false
        pending = null
        buffer.append(waiting)
        buffer.append(frame)
        return waiting + frame
    }
}

/**
 * 工具调用碎片装配器。
 *
 * 三个必须处理的现实形态（`docs/01-architecture.md` §3.2）：
 * 1. `index` **乱序**到达（并行工具调用时不同供应商的帧顺序不同）；
 * 2. `id` **只在首片**出现，后续帧的 `id` 为 null；
 * 3. `arguments` 是 **JSON 碎片**，可能分几十片到达，拼接后才是一个合法 JSON。
 *
 * 空命中语义：从未收到任何碎片 ⇒ `snapshot()` 返回空列表（不是一条空调用）。
 *
 * ⚠️ Gemini 的 `functionCall` 是**整体到达、无碎片**，它的 adapter 只需喂一次。
 */
class ToolCallFragmentTracker {
    private class Slot {
        var id: String? = null
        var name: String = ""
        val args: StringBuilder = StringBuilder()
    }

    /** 用有序 map：index 是供应商给的槽位号，输出必须按它排（不是按到达顺序）。 */
    private val slots = sortedMapOf<Int, Slot>()

    val size: Int get() = slots.size

    fun onDelta(index: Int, id: String?, name: String?, argumentsFragment: String) {
        val slot = slots.getOrPut(index) { Slot() }
        // id 只在首片可信；后续帧若重复带同一个 id 也只在首片采纳（防覆盖成空串）。
        if (slot.id == null && !id.isNullOrEmpty()) slot.id = id
        if (!name.isNullOrEmpty()) appendName(slot, name)
        if (argumentsFragment.isNotEmpty()) slot.args.append(argumentsFragment)
    }

    fun snapshot(): List<ToolCall> = slots.values.map { slot ->
        ToolCall(
            id = slot.id ?: newId(),
            name = slot.name,
            argumentsJson = slot.args.toString().ifEmpty { "{}" },
        )
    }

    fun reset() = slots.clear()

    /**
     * name 可能是增量分片（`get_` + `weather`），也可能被重复整发（累积式代理）。
     * 两种都要支持：已以该片段结尾 ⇒ 视为重复，跳过。
     */
    private fun appendName(slot: Slot, name: String) {
        when {
            slot.name.isEmpty() -> slot.name = name
            slot.name == name || slot.name.endsWith(name) -> Unit
            else -> slot.name += name
        }
    }
}

/** 一次流的最终装配结果。消费者：`core-agent` 的 `GenerationRound`。 */
data class AccumulatedGeneration(
    val text: String = "",
    val reasoning: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val usage: TokenUsage? = null,
    val finishReason: FinishReason? = null,
    val failed: GenerationEvent.Failed? = null,
)

/**
 * 流式事件累积器。
 *
 * 职责单一：把一串 [GenerationEvent] 折成 [AccumulatedGeneration]。
 * **不做**重复检测（那是 `core-agent` 的 `StreamRepetitionDetector`）、
 * **不做**重试判定（那是 `core-engine` 的 `RetryPolicy`）。
 *
 * 空命中语义：空流 ⇒ 全空结果（text 空串、toolCalls 空列表、finishReason null）。
 */
class StreamAccumulator {
    private val textTracker = DeltaTracker()
    private val reasoningTracker = DeltaTracker()
    private val toolCalls = ToolCallFragmentTracker()

    var usage: TokenUsage? = null
        private set

    var finishReason: FinishReason? = null
        private set

    var failed: GenerationEvent.Failed? = null
        private set

    /** 已收到的帧数，用于「有内容但无 finish_reason」的截断判定。 */
    var eventCount: Int = 0
        private set

    val text: String get() = textTracker.text
    val reasoning: String get() = reasoningTracker.text

    fun accept(event: GenerationEvent) {
        eventCount++
        when (event) {
            is GenerationEvent.TextDelta -> textTracker.feed(event.text)
            is GenerationEvent.ReasoningDelta -> reasoningTracker.feed(event.text)
            is GenerationEvent.ToolCallDelta ->
                toolCalls.onDelta(event.index, event.id, event.name, event.argumentsFragment)
            is GenerationEvent.Usage -> usage = TokenUsage(
                prompt = event.prompt,
                completion = event.completion,
                total = event.prompt + event.completion,
                cachedPromptTokens = event.cachedPrompt,
            )
            is GenerationEvent.Finished -> {
                finishReason = event.reason
                flushTrackers()
            }
            is GenerationEvent.Failed -> {
                failed = event
                flushTrackers()
            }
        }
    }

    /**
     * 流终局补出 [DeltaTracker] 的待决帧。
     *
     * 两击确认会**延迟一帧**输出：若流在「歧义帧」之后直接结束（短回答、
     * 服务端提前断流），不 flush 就会永久丢掉最后一帧。
     *
     * 这里不另设 `flushedText` 字段：[DeltaTracker.flush] 已把待决帧追加进自己的
     * buffer，[text] / [reasoning] 读的就是那份 buffer —— 再单独累加一次会**双计**。
     * flush 幂等（无待决帧返回空串），重复的 Finished / Failed 不会重复补字。
     */
    private fun flushTrackers() {
        textTracker.flush()
        reasoningTracker.flush()
    }

    fun toGeneration(): AccumulatedGeneration = AccumulatedGeneration(
        text = textTracker.text,
        reasoning = reasoningTracker.text,
        toolCalls = toolCalls.snapshot(),
        usage = usage,
        finishReason = finishReason,
        failed = failed,
    )

    fun reset() {
        textTracker.reset()
        reasoningTracker.reset()
        toolCalls.reset()
        usage = null
        finishReason = null
        failed = null
        eventCount = 0
    }
}
