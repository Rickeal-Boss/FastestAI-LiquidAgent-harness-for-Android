package com.rickeal.liquidharness.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前会话已物化上下文的**字符数**快照（W7）。
 *
 * ## 为什么需要它
 *
 * 模型切换守卫（`SwitchGuard.evaluateSwitch`）与会话级自动压缩都要 `committedChars`，
 * 但唯一真值源是 `feature-chat/ChatUiState.bubbles`，而 `AGENTS.md` §四禁止
 * `feature-*` 互相依赖 ⇒ `feature-settings/SettingsViewModel` 读不到它。
 * 本类放在两端都依赖的 `:core-data`，由 `AppContainer` 单例持有：
 * `ChatViewModel` 写、`SettingsViewModel` 读。DI 仍只走 `AppContainer`，不引第三方框架。
 *
 * ## 为什么是字符数而不是 token
 *
 * W6 已把权威量纲换成字符（`PreStepBudget` 的 L3 硬预算）：`TokenEstimator` 对代码 / JSON
 * 乐观、对散文保守，拿同一把尺子自证等于没量（W5-01 的 33.8K 事故）。
 *
 * ## 语义边界（写错就会变成静默放行）
 *
 * - **进程级、非持久化**：进程被杀后回到 0，此时守卫判 `empty_session` ⇒ `VACUOUS`
 *   而不是「通过」。这是刻意的：没有证据时不许硬绿。
 * - 它只是体积观测通道，**不存任何消息内容、不存密钥**；气泡仍是唯一真值源。
 * - 写入方必须按 `bubbles.toHistoryMessages(compression)` 重建后逐条求和；
 *   **压缩 / 撤销 / 新对话后必须重写**，否则守卫会拿旧体积误判（压缩后本该放行却被禁）。
 * - 多个写者会互相覆盖：本仓只有 `ChatViewModel` 一处写，其余全为读。
 */
class SessionFootprintStore {

    private val _chars = MutableStateFlow(0)

    val chars: StateFlow<Int> = _chars.asStateFlow()

    /** 覆盖式写入；负值钳到 0（字符数没有负数语义）。 */
    fun update(committedChars: Int) {
        _chars.value = committedChars.coerceAtLeast(0)
    }

    /** 同步读，供设置页做一次性容量判定（不需要订阅）。 */
    fun current(): Int = _chars.value
}
