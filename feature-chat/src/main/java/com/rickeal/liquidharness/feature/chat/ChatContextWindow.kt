package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.agent.context.hardContextBudgetTokens
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/* 上下文窗口的两个纯函数（W6）：VM 贴着 G10 闸门，且这两段要能 JVM 单测。 */

/**
 * 读「本应用的窗口上限」档位（`SettingsRepository.maxContextTokens`）。
 *
 * 兜底用 `AgentPolicy().maxContextTokens`（32 000）而不是 0：读设置失败时宁可退回默认档位，
 * 也不要让状态条分母变 0（0 会让整条 meter 直接不渲染 —— 用户失去唯一的前兆信号）。
 *
 * ⚠️ **不要写成 `runCatching { ... .first() }`**：`first()` 是挂起点（DataStore Flow 收集），
 * scope 被取消时抛 `CancellationException`，`runCatching` 会把它一起吞掉 ⇒ Job 从 cancelled
 * 变 completed（`AGENTS.md` §五.4）。同一纪律的正例见 `ContextLimitWriteback.offer`
 * （它有一条钉「原样重抛」的用例）。`catch (_: Exception)` 而不是 `Throwable`：
 * `Error` 不该被静默降级成默认档位。
 */
internal suspend fun contextWindowCap(container: AppContainer): Int =
    try {
        container.settings.maxContextTokens.first()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AgentPolicy().maxContextTokens
    }

/**
 * 状态条的**分母**：生效硬预算（token），不是端点声明窗口。
 *
 * ## 为什么必须是硬预算（W6 修的真实缺陷）
 *
 * 修前用 `endpoint.contextLength`（声明值）：端点 1M + 档位 32K 时占用率永远到不了 4%
 * ⇒ **状态条永不报警**，而内核其实已在 20 928 token 处压缩。这与 `ChatContextMeter`
 * 类头「一变色就精确对应压缩已经开始」正面矛盾；根因是档位在 settings、窗口在 endpoint。
 *
 * ## 为什么换成硬预算在数学上恰好对
 *
 * `contextBudgetTokens = hard × compressThreshold`，meter 的阈值也是 `compressThreshold`
 * ⇒ `used / hard >= 0.75` ⟺ `used >= softBudget` = **压缩触发点本身**。换别的分母只是巧合对齐。
 * 白拿的副作用：`hardContextBudgetTokens` 内部已是 `verifiedContextLength ?: contextLength`，
 * 400 学到的真实窗口写回后分母自动跟着变，不需要刷新代码。
 *
 * @param maxContextTokens 档位（见 [contextWindowCap]）。端点未配置时返回 0（meter 不渲染）。
 */
internal fun contextLimitOf(endpoint: RemoteEndpoint?, maxContextTokens: Int): Int =
    if (endpoint == null) {
        0
    } else {
        hardContextBudgetTokens(endpoint, AgentPolicy(maxContextTokens = maxContextTokens))
    }
