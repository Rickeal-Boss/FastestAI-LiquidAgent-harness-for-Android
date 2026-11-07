package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 当前会话「会发回模型的字节」的**字符数**（W7）。
 *
 * ## 为什么是字符不是 token
 *
 * W6 已把权威量纲换成字符（`PreStepBudget` 的 L3 硬预算）：`TokenEstimator` 对代码 / JSON
 * 乐观、对散文保守，拿同一把尺子自证等于没量（W5-01 的 33.8K 事故）。
 * `SessionFootprintStore`（`:core-data`）是进程级通道：`feature-settings` 拿不到
 * `ChatUiState.bubbles`（`AGENTS.md` §四禁 feature 互依），只能读这里写出的快照。
 *
 * ## 口径（改这里之前先读 `ChatHistoryMapper.kt`）
 *
 * 真值源是 `bubbles.toHistoryMessages(compression)` **重建后的线上消息**，不是气泡本身：
 * - 正文 `text.length`；
 * - `toolCalls`：id + name + argumentsJson（模型宣告要调什么，会随请求发出）；
 * - `toolResults`：callId + name + output + errorMessage（结果回灌，同样计费）；
 * - 附件文本当量 [ATTACHMENT_CHAR_EQUIVALENT]：现役 `ChatBubble.toHistoryMessages()`
 *   **不回灌历史图片**（`ChatHistoryMapper.kt:34-37`：体积会随轮次线性膨胀），
 *   所以重建出的 `ChatMessage.attachments` 恒空 ⇒ 当量恒 0。这不是「忘了算」，
 *   是刻意与线上请求体一致；将来若改回灌口径，必须同步改这里和 KDoc。
 * - `SYSTEM` / `TOOL` / `SKIPPED` 轨迹在 mapper 里就被过滤，不计入。
 *
 * 压缩切点是**气泡级**：`fromId` 及其之前的气泡不进 history（`ChatHistoryMapper.kt:93-98`），
 * 失效 id 回落全量（宁可多发一段可见历史，也不要静默少发）。因此撤销压缩、开新对话、
 * 会话导入后，只要重算本函数就能得到正确体积，不需要单独的「减掉多少」账。
 *
 * ## 单一写者
 *
 * [observeSessionFootprint] 订阅 `ChatUiState`，一处覆盖 send / 终态定稿 / 手动压缩 /
 * 撤销 / 新对话 / 会话导入**全部**气泡变化路径。`SessionFootprintStore` 的 KDoc 明确
 * 「多个写者会互相覆盖」，所以计算与写入一律外提到本文件，`ChatViewModel` 只留一行委托
 * （它 484/500，余 16 行；G10 触顶的处理是拆文件，不是调上限）。
 */
internal const val ATTACHMENT_CHAR_EQUIVALENT = 0

/** 纯函数：按重建后的线上 history 逐条求字符和。 */
internal fun committedCharsOf(bubbles: List<ChatBubble>, compression: SessionCompression?): Int =
    bubbles.toHistoryMessages(compression).sumOf(::messageChars)

private fun messageChars(message: ChatMessage): Int =
    message.text.length +
        message.toolCalls.sumOf { call ->
            call.id.length + call.name.length + call.argumentsJson.length
        } +
        message.toolResults.sumOf { result ->
            result.callId.length + result.name.length + result.output.length +
                (result.errorMessage?.length ?: 0)
        } +
        message.attachments.size * ATTACHMENT_CHAR_EQUIVALENT

/**
 * 唯一的写者订阅：state 每次变化都重算并覆盖写入进程级快照。
 *
 * 放在 `viewModelScope`（Main.immediate）里，`collect` 与 `_state.update` 同线程，
 * 不存在「压缩后守卫拿到旧体积」的竞态；进程被杀后快照回 0 ⇒ 守卫判
 * `empty_session` VACUOUS 而不是硬绿（见 `SessionFootprintStore` KDoc）。
 */
internal fun observeSessionFootprint(
    states: StateFlow<ChatUiState>,
    container: AppContainer,
    scope: CoroutineScope,
): Job = scope.launch {
    states.collect { snapshot ->
        container.sessionFootprint.update(
            committedCharsOf(snapshot.bubbles, snapshot.compression),
        )
    }
}
