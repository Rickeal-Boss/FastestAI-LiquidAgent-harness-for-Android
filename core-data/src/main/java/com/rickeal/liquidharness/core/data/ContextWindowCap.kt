package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/*
 * W39 拆分（G10 前置）：本函数原在 `feature-chat/.../ChatContextWindow.kt`。
 * 为什么落在 :core-data —— 它是「读 SettingsRepository + 失败回落默认档位 + 取消必须透传」
 * 的设置读取，零 UI / 零 Compose 类型；与同模块的 `ContextLimitWriteback`（同一套取消纪律）
 * 同居一处，settings 的真值源（`AppContainer.settings`）就在本模块。
 * 另一半（纯预算算式 `contextLimitOf`）去了 :core-context —— 那里才是预算算式的家。
 * 拆开后 feature-chat 的 4 个调用点各加一行 import（见 W39 交接），逻辑零改动。
 */

/**
 * 读「本应用的窗口上限」档位（`SettingsRepository.maxContextTokens`）。
 *
 * 兜底用 `AgentPolicy().maxContextTokens`（32 000）而不是 0：读设置失败时宁可退回默认档位，
 * 也不要让状态条分母变 0（0 会让整条 meter 直接不渲染 —— 用户失去唯一的前兆信号）。
 *
 * ⚠️ **不要写成 `runCatching { ... .first() }`**：`first()` 是挂起点（DataStore Flow 收集），
 * scope 被取消时抛 `CancellationException`，`runCatching` 会把它一起吞掉 ⇒ Job 从 cancelled
 * 变 completed（`AGENTS.md` §五.4）。同一纪律的正例见 [ContextLimitWriteback.offer]
 * （它有一条钉「原样重抛」的用例）。`catch (_: Exception)` 而不是 `Throwable`：
 * `Error` 不该被静默降级成默认档位。
 */
suspend fun contextWindowCap(container: AppContainer): Int =
    try {
        container.settings.maxContextTokens.first()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AgentPolicy().maxContextTokens
    }
