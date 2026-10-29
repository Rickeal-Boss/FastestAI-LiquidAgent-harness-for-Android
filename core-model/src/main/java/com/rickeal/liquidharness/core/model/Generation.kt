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
 */
@Serializable
data class TokenUsage(
    val prompt: Int = 0,
    val completion: Int = 0,
    val total: Int = 0,
    val cachedPromptTokens: Int = 0,
    /** true = 本地 `TokenEstimator` 兜底（provider 未回报 usage）。 */
    val estimated: Boolean = false,
) {
    companion object {
        val Zero: TokenUsage = TokenUsage()
    }
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
    /** 仅当端点 `reasoningStyle == REASONING_EFFORT` 时生效。 */
    val reasoningEffort: String = "medium",
    val seed: Long? = null,
)
