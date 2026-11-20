package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.agent.context.shouldApply
import com.rickeal.liquidharness.core.model.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动压缩诊断码（W9-01 命题① / W36 真机 4 次实证）的 JVM 判据。
 *
 * ## 防的失真
 *
 * 1. **空转被记成「净收益不足」**：`compressBubbles` 没压动任何东西时
 *    `savingChars = committed - committed = 0`，`shouldApply` 得
 *    `net = 0 - 256 = -256` ⇒ 日志写 `insufficient_savings:net=-256`。
 *    这句话的语义是「有可压候选但净收益不足」，与「根本无可丢内容」是两种处置：
 *    前者调阈值 / 预算，后者调触发线或根本不该评估。
 *    `ConversationOps.autoCompactOnSettled` 的前置判（`!outcome.applied` ⇒
 *    `nothing_to_drop`）修的就是这条 —— 本文件钉住它的**输入前提**。
 * 2. **自动 / 手动两条路径对同一快照给出不同结论**：手动按钮与自动策略共用
 *    `compressBubbles`，若判据分叉，用户会看到「点了压缩，下一次请求又自动压一次」。
 * 3. **`committedCharsOf` 对压缩产物不缩水**：那意味着压缩区没进 history 重建路径，
 *    「压缩了」与「实际发出去的」是两个数。
 *
 * ⚠️ 本文件**不**能直接测 `ConversationOps`（需要 `AppContainer`：Android Context /
 * Keystore / 文件目录），故第 1 条测的是它前置判的**可测前提**：未压动的 outcome +
 * 旧路径确实会产出 `insufficient_savings`（灵敏度自证）。接线本身由 G23 守卫
 * （`.autoCompactOnSettled(` 恰好一处 + 变异自证）与真机 journal 兜底。
 */
class AutoCompactDiagnosticsTest {

    /**
     * 自动压缩本身的字符成本当量。
     *
     * 镜像 `ConversationOps.AUTO_COMPACT_COST_CHARS`（那里是 `private`）。这里写死不是
     * 第二真值源：本文件只用它复现「旧路径为什么误报」，成本值本身由 G10 与
     * `MIN_SAVINGS_CHARS` 的关系钉住，改那边会让本判据的净收益算错而判红。
     */
    private val autoCompactCostChars = 256

    /** 20 条大体积气泡：字符总量远高于 `COMPACT_TRIGGER_CHARS`（50 227）。 */
    private fun bulkyBubbles(count: Int = 20, textLength: Int = 3_000): List<ChatBubble> =
        (0 until count).map { index ->
            ChatBubble(
                id = "b" + index,
                role = if (index % 2 == 0) Role.USER else Role.MODEL,
                // 默认 3000 字符 × 20 条 = 60 000 字符 > 触发线，且可被软预算压动。
                text = ("message " + index + " ").padEnd(textLength, 'x'),
            )
        }

    /**
     * 无可丢内容的输入：气泡数 ≤ `HEAD_ANCHOR_BUBBLES + 1`（5 条）⇒ `compressBubbles`
     * 第一条前置判直接返回 `MANUAL_COMPRESS_NOOP`，但字符量已越过触发线。
     *
     * 这条「越线但没得压」正是 W36 真机 4 次错配的形态：旧码不区分「没得压」与
     * 「压了但不够省」，两者都落 `insufficient_savings`。
     */
    @Test
    fun untouchedInputDoesNotReportInsufficientSavings() {
        val bubbles = bulkyBubbles(count = HEAD_ANCHOR_BUBBLES + 1, textLength = 12_000)
        val committedChars = committedCharsOf(bubbles, compression = null)
        // 前提自证：确实越过了触发线，否则 shouldApply 会先返回 below_trigger，
        // 下面的「旧路径会误报」就退化成 ⛔vacuous。
        assertTrue("committed=$committedChars", committedChars > 50_227)

        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 200) }

        assertFalse("nothing to drop must not produce a compression", outcome.applied)
        assertTrue(
            "expected nothing-to-drop reason, got " + outcome.reason,
            outcome.reason.contains("nothing to drop"),
        )
        // 旧路径的复现：savingChars 由「未压动」=> 恒 0，净收益 = -256。
        val legacySavingChars = committedChars - committedCharsOf(bubbles, outcome.compression)
        assertEquals(0, legacySavingChars)
        val legacy = shouldApply(committedChars, legacySavingChars, autoCompactCostChars, false)
        assertFalse(legacy.applied)
        // 灵敏度自证：旧路径确实会误报 insufficient_savings —— 所以 ConversationOps 的
        // 前置判（!outcome.applied ⇒ nothing_to_drop）不是「多写一行」。
        assertTrue(
            "legacy path should have misreported, got " + legacy.reason,
            legacy.reason.startsWith("insufficient_savings"),
        )
        // 修复后的归因（= ConversationOps 前置判的分流规则）：`!outcome.applied` 先判，
        // 空转不落进 shouldApply ⇒ `nothing_to_drop` 与 `insufficient_savings` 互斥。
        val attributed = if (!outcome.applied) "nothing_to_drop" else legacy.reason
        assertEquals("nothing_to_drop", attributed)
        assertTrue(legacy.reason.startsWith("insufficient_savings"))
    }

    /** 同一 `bubbles` + 同一 budget：自动判据与手动 `applied` 必须一致。 */
    @Test
    fun autoAndManualAgreeOnSameSnapshot() {
        val bubbles = bulkyBubbles()

        val tight = runBlocking { compressBubbles(bubbles, budgetTokens = 200) }
        val tightCommitted = committedCharsOf(bubbles, compression = null)
        val tightSaving = tightCommitted - committedCharsOf(bubbles, tight.compression)
        val tightAuto = shouldApply(tightCommitted, tightSaving, autoCompactCostChars, false)
        assertEquals(tight.applied, tightAuto.applied)
        assertTrue(tight.applied)
        assertTrue(tightAuto.applied)

        val loose = runBlocking { compressBubbles(bubbles, budgetTokens = 1_000_000) }
        val looseCommitted = committedCharsOf(bubbles, compression = null)
        val looseSaving = looseCommitted - committedCharsOf(bubbles, loose.compression)
        val looseAuto = shouldApply(looseCommitted, looseSaving, autoCompactCostChars, false)
        assertEquals(loose.applied, looseAuto.applied)
        assertFalse(loose.applied)

        // 已压缩态不参与自动评估（重复压缩没有语义），与手动路径的「再压先撤销」同向。
        val already = shouldApply(tightCommitted, tightSaving, autoCompactCostChars, true)
        assertFalse(already.applied)
        assertEquals("already_compressed", already.reason)
    }

    /** 压缩产物必须真的让「会发回模型的字符数」变小，否则压缩等于没做。 */
    @Test
    fun appliedCompressionShrinksCommittedChars() {
        val bubbles = bulkyBubbles()
        val outcome = runBlocking { compressBubbles(bubbles, budgetTokens = 200) }
        assertTrue(outcome.applied)

        val before = committedCharsOf(bubbles, compression = null)
        val after = committedCharsOf(bubbles, outcome.compression)
        assertTrue("before=$before after=$after", after < before)
        // 省下的字符与 outcome 的「丢了几条 / 留了几条」同向：丢 0 条不可能变短。
        assertTrue(outcome.droppedBubbles > 0)
        assertTrue(outcome.droppedMessages >= outcome.droppedBubbles)
    }

    /**
     * ⚠️ **未加**「`AppContainer.promptContext()` 遇取消必须重抛」的用例 —— 如实报告：
     * `AppContainer` 的构造需要 Android `Context`（`filesDir` / `assets`）、
     * `SettingsRepository.create`（DataStore）与 `AndroidKeyStoreCipher`（Keystore），
     * 本仓 `core-data` 测试源集**没有 Robolectric**（只有 junit + kotlin-test）⇒ JVM 单测
     * 里造不出这个对象，硬造只能靠 mock 掉全部装配（那测的是 mock 不是取消透传）。
     *
     * 退而钉住**反例本身**（W17-02 的缺陷形态）：`runCatching` 把
     * `CancellationException` 收进 `Result.failure` 且 `getOrDefault` 返回默认值 ⇒
     * 取消被写成「正常完成 + 空输入」。所以 `promptContext()` 的三处降级必须是
     * `catch (c: CancellationException) { throw c }` 前置，不能退回 `runCatching`。
     * 这条是**模式自证**，不是 `promptContext` 的覆盖声明；接线由真机取消取证兜底。
     */
    @Test
    fun runCatchingSwallowsCancellationSoPromptContextMustNotUseIt() {
        val cancelled = CancellationException("job cancelled")
        // 旧形态（缺陷）：取消被吞，调用方看到的是「空串」而不是取消信号。
        assertEquals("", runCatching { throw cancelled }.getOrDefault(""))
        // 新形态（`promptContext()` 采用的形状）：取消必须原样透传。
        val rethrown = try {
            try {
                throw cancelled
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                "swallowed"
            }
        } catch (c: CancellationException) {
            c
        }
        assertEquals(cancelled, rethrown)
    }
}
