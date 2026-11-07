package com.rickeal.liquidharness.core.model

import kotlinx.serialization.Serializable

/**
 * 一次生成的结束原因。
 *
 * [LENGTH] 在本仓有**双重语义**，两者都必须被上层当成「回答可能不完整」：
 * 1. 模型确实撞到 `max_tokens`（供应商回报 `finish_reason = "length"`）；
 * 2. 连接被中途掐断 —— 有内容、但既没有 `finish_reason` 也没有 `[DONE]`。
 *    这一条由 `ProviderAdapter.finish(state)` 补发（AGENTS.md §五.6：
 *    「有内容但无 finish_reason」= 被截断，不是正常结束）。
 */
@Serializable
enum class FinishReason {
    /** 模型自己停了 —— 正常。 */
    STOP,

    /** 撞到输出上限，或流被截断。**半截回答不得当完整回答落库。** */
    LENGTH,

    /** 模型要调工具，本轮流结束（调用由内核执行）。 */
    TOOL_CALLS,

    /** 供应商侧内容过滤。 */
    FILTER,

    /** 用户主动停止 / 协程取消。已产出的文本由调用方保留（`Cancelled(partialText)`）。 */
    CANCELLED,
}

/**
 * 用量。API 路径下 token 直接等于钱，所以 provider 回报的值是**权威值**，
 * [estimated] 为 true 才说明是本地估算兜底。
 *
 * [cachedPromptTokens]：prompt cache 命中量。三家位置不同
 * （OpenAI `usage.prompt_tokens_details.cached_tokens`、
 * Anthropic `cache_read_input_tokens`、Gemini `cachedContentTokenCount`），
 * 由 adapter 归一到这里。它是 00-brief A8「保住 prompt cache」唯一可观测的判据 ——
 * 没有这个字段，「缓存有没有命中」就只能靠猜。
 *
 * **三态，不是 Int**：`null` = provider 没上报这个字段，`0` = 上报了且没命中，
 * `>0` = 命中。W4 之前这里是 `Int = 0`，把「没上报」压成了「0 命中」——
 * 两件事的处置相反（前者查端点是否支持该字段，后者查前缀是否稳定），
 * 同形展示会让排查方向直接反掉（design §2.4）。
 * `@Serializable` 兼容性：旧 journal 里缺这个字段 ⇒ 反序列化为 `null`，正是想要的语义。
 *
 * W9-04（2026-11-07）：三态在**整个 usage 都没到达**时也会落成 `null`，与「usage 到了
 * 但缓存字段没上报」同形 ⇒ 由 [promptCacheStatus] 用 `usageObserved` 补出第四态。
 * DeepSeek 平铺字段 / Anthropic 的 write 量由 adapter 归一，不再有 `TODO(W6)`。
 *
 * [cacheWriteTokens]：缓存写入量。它**也是计费输入**，所以进 [cacheHitRatio] 的分母；
 * 与命中量分开存，绝不合并成一个「缓存 token」模糊值。
 */
@Serializable
data class TokenUsage(
    val prompt: Int = 0,
    val completion: Int = 0,
    val total: Int = 0,
    val cachedPromptTokens: Int? = null,
    /** true = 本地 `TokenEstimator` 兜底（provider 未回报 usage）。 */
    val estimated: Boolean = false,
    /** 缓存写入量；`null` = 未上报。旧 journal 缺字段 ⇒ `null`，兼容。 */
    val cacheWriteTokens: Int? = null,
) {
    companion object {
        val Zero: TokenUsage = TokenUsage()
    }
}

/**
 * prompt cache 的**四态观测结果**（W9-04；外部研究增量一）。
 *
 * 三态 `[TokenUsage.cachedPromptTokens]` 回答不了 W7-02「OR 免费池恒 0 到底是没命中
 * 还是字段没上报」，因为「整个 usage 末帧没来」与「usage 来了但没带缓存字段」都会
 * 落成 `null`。两态的处置相反：前者查传输/末帧，后者查端点能力。
 */
@Serializable
enum class PromptCacheStatus {
    /** 上报了命中量且 > 0。 */
    HIT,

    /** 上报了命中量且 == 0 ⇒ 真没命中，查前缀稳定性。 */
    MISS,

    /** usage 到了，但没有任何一家认识的缓存字段 ⇒ 查端点是否支持上报。 */
    CACHE_FIELDS_NOT_REPORTED,

    /** 整个 usage 都没观测到（流被截 / 端点不回报）⇒ 查传输与末帧，不是缓存结论。 */
    USAGE_NOT_OBSERVED,
}

/**
 * 四态派生。**唯一口径**，不要在 UI / 日志 / 台账里各自 `?: 0` 重算。
 *
 * @param usageObserved 本次生成是否收到过 provider 的 usage（不是本地估算兜底）。
 *   本地估算必须传 `false`，否则会把「没测」写成「没命中」。
 */
fun promptCacheStatus(cachedPromptTokens: Int?, usageObserved: Boolean): PromptCacheStatus = when {
    !usageObserved -> PromptCacheStatus.USAGE_NOT_OBSERVED
    cachedPromptTokens == null -> PromptCacheStatus.CACHE_FIELDS_NOT_REPORTED
    cachedPromptTokens > 0 -> PromptCacheStatus.HIT
    else -> PromptCacheStatus.MISS
}

/**
 * 缓存命中率，**分母一次钉死**（W9-04；报告1 §二「分母口径」）。
 *
 * 口径 = `cachedPromptTokens / (prompt + cacheWriteTokens ?: 0)`。
 *
 * - OpenAI 兼容族 / DeepSeek 归一后的 `prompt` **已包含**命中量，所以**不**再加一次
 *   `cached`（加了就是双计，命中率随口径漂移）；
 * - `cacheWriteTokens` 是独立的计费输入，留在分母（deepseek-harness TUI 同口径）；
 * - 未上报命中量 / 分母 <= 0 ⇒ 返回 `null`（**不伪装成 0%**，与三态纪律一致）；
 * - 结果钳制在 `[0, 1]`。⚠️ **这是显示上界，不是「100% 命中」的事实声明**：
 *   DeepSeek 平铺族 / Anthropic 的 `prompt_tokens` 可能**不含**命中量，此时
 *   原始比值会 > 1，说明该族分母尚未归一（W11-05 待办：按 provider 族归一），
 *   钳到 1.0 只保证 UI 不显示 600% 这种荒谬值，不得据此写「命中率 100%」。
 *
 * 返回 `null` 的四态归因请读 [promptCacheStatus]，不要拿 `null` 当「缓存全失效」。
 */
fun cacheHitRatio(usage: TokenUsage): Double? {
    val read = usage.cachedPromptTokens ?: return null
    val denominator = usage.prompt + (usage.cacheWriteTokens ?: 0)
    if (denominator <= 0) return null
    return (read.toDouble() / denominator).coerceIn(0.0, 1.0)
}

/**
 * 采样参数。
 *
 * 远程侧只有 temperature / topP / maxTokens / stop / reasoningEffort 有效；
 * 旧仓的 `repetitionPenalty` / `topK` / `mirostat` 是端侧采样器概念，**已删除** ——
 * 远程端点不接受它们，留着就是永远被忽略的字段（00-brief A1 的同一逻辑）。
 */
@Serializable
data class SamplingParams(
    val temperature: Double = 1.0,
    val topP: Double = 1.0,
    /** 单轮输出上限。null = 用端点的 `maxOutputTokens`。 */
    val maxTokens: Int? = null,
    val stop: List<String> = emptyList(),
    /** 未接线，勿用；唯一真值源 = `RemoteEndpoint.reasoningEffort`。 */
    val reasoningEffort: String = "medium",
    val seed: Long? = null,
)
