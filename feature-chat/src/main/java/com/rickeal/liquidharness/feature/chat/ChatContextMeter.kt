package com.rickeal.liquidharness.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.design.LocalGlassColors

/**
 * 「上下文将满」的提示阈值。
 *
 * 直接取 `AgentRunner` 触发上下文压缩的同一比例（`AgentPolicy.compressThreshold`），
 * 而不是另写一个魔数：这样状态条一变色，就精确对应「压缩已经开始、模型即将开始忘事」。
 * 阈值比压缩点还晚的话，用户看到变色时其实已经晚了。
 */
private val CONTEXT_WARN_RATIO: Float = AgentPolicy().compressThreshold

/**
 * 一行上下文占用状态：`上下文 12.3K / 32.8K`。
 *
 * 为什么要显示：超出窗口会被内核**静默压缩**，用户只看到「模型怎么突然忘了前面说过的」。
 * 把占用量摆出来，就是给「变傻」一个可解释的前兆。
 *
 * 单一口径：`AgentEvent.UsageUpdated.prompt` 是 provider 回报的权威值（API 路径下
 * 它等于钱）。旧仓并列的「发送前估算」口径没有搬 —— 那是端侧没有 usage 回报时的
 * 兜底，本仓不需要两套数字互相解释。
 *
 * 两个数任一为 0 时不渲染：显示「0 / 4K」比不显示更误导。
 */
@Composable
fun ChatContextMeter(
    usedTokens: Int,
    limitTokens: Int,
    modifier: Modifier = Modifier,
) {
    if (usedTokens <= 0 || limitTokens <= 0) return
    val colors = LocalGlassColors.current
    val nearFull = usedTokens.toFloat() / limitTokens.toFloat() >= CONTEXT_WARN_RATIO
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (nearFull) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = stringResource(R.string.chat_context_near_full),
                tint = colors.warning,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(12.dp),
            )
        }
        Text(
            text = stringResource(
                R.string.chat_context_label,
                formatTokenCount(usedTokens.toLong()),
                formatTokenCount(limitTokens.toLong()),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (nearFull) colors.warning else colors.onGlassMuted,
            // 该行可用宽边际不大，limit 上界可达 "131.1K" ⇒ 补截断，避免「切半个字」。
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 4096 → "4.1K"，2100 → "2.1K"，800 → "800"。
 * 纯整数运算，避开 `String.format` 在部分 Locale 下把小数点写成逗号的问题。
 */
private fun formatTokenCount(value: Long): String {
    if (value < 1000L) return value.toString()
    val tenths = (value * 10 + 500) / 1000
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0L) "${whole}K" else "$whole.${fraction}K"
}
