package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.context.HardBudgetShrinker
import com.rickeal.liquidharness.core.model.ChatMessage

/**
 * 本轮为 chars/token 标定贡献的分子（W12 / 外审 P1-2）。
 *
 * ## 为什么带图轮次要跳过标定
 *
 * `RunState.recordReportedPrompt` 用 `charsSent / prompt` 反标定 `measuredCharsPerToken`：
 * 分子 [HardBudgetShrinker.calibrationChars] **只算文本**（不含图片当量），而分母
 * `prompt_tokens` **含视觉 token**（provider 一张图只计 85–255 token）。带图轮次若照常
 * 标定，比值被分母的视觉 token 压低 ⇒ 下一轮纯文本开局 `charCap = hard × cpt` 偏小
 * ⇒ **过度收缩**、可能砍掉本可保留的工具结果。且这个偏低值会**跨轮继承**（只在压缩时
 * `invalidatePromptAnchor()` 归零）。
 *
 * 跳过方式：返回 `0`，而 `recordReportedPrompt` 只在 `charsSent > 0` 时覆盖
 * `measuredCharsPerToken` ⇒ 本轮不覆盖，沿用上一轮纯文本标定；首轮即带图则保持 `0`
 * （未标定，走 `policy.maxCalibratedCharsPerToken` 默认上界，安全侧）。
 *
 * 注意与 W11「标定放大 3.3 倍」的对偶关系：W11 修的是**高估方向**（图片当量进分子把
 * cpt 顶到 ceiling），本条修的是**低估方向**（视觉 token 进分母把 cpt 压低）。两条合起来
 * 才是完整的「尺子校准」面。
 */
internal fun calibrationCharsForRound(messages: List<ChatMessage>): Int =
    if (messages.any { it.sendableAttachments.isNotEmpty() }) {
        0
    } else {
        HardBudgetShrinker.calibrationChars(messages)
    }