package com.rickeal.liquidharness.core.agent.tool

import com.rickeal.liquidharness.core.model.AgentJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 工具调用护栏：**同参连发 / 失败连击 / 振荡**三个维度的纯状态机。
 *
 * 为什么拆成状态机：旧仓把「超时 / IO / 截断 / 取消」和「三个计数判据」混在一个
 * `suspend` 函数里，改一个阈值要跑真机。新仓执行归 `loop/ToolCallExecutor`，判定归
 * 本类：零 IO、零协程、零 Android ⇒ JVM 直测边界。
 *
 * 三个维度分工（旧仓 §2.4 口径，别混）：
 * - **同参连发**：同一 `(工具, 规范化参数)` 连续第 [DEFAULT_SAME_CALL_EXEC_LIMIT] 次起
 *   返回 [Verdict.RepeatedCall] —— 不执行，但**仍要回一条 result**（丢 result 会让端点
 *   报「有 call 无结果」）。前两次放行是为了保留「失败后原样重试一次」的合法路径。
 * - **失败连击**：同一工具连续失败 [DEFAULT_FAILURE_STREAK_LIMIT] 次（**含换参**）。
 * - **振荡**：判据见 [ToolOscillation]。
 * 线程模型：**非线程安全**，一个实例挂在一个 run 的状态上，只在 run 协程内读写。
 * 不要上提为长生命周期字段（`ask_actor` 子 run 会覆写父 run 的计数，旧仓踩过）。
 */
class ToolExecutionGuard(
    private val sameCallExecLimit: Int = DEFAULT_SAME_CALL_EXEC_LIMIT,
    private val failureStreakLimit: Int = DEFAULT_FAILURE_STREAK_LIMIT,
    private val historyCapacity: Int = HISTORY_CAPACITY,
) {

    /** 一次调用的处置结论。 */
    sealed interface Verdict {
        data object Proceed : Verdict

        /** 同参超限：调用方**不要执行**，回一条「已忽略未执行」的 tool result。 */
        data class RepeatedCall(val tool: String, val streak: Int) : Verdict

        /** 熔断：调用方终止 run（`TerminationReason.BreakerTripped`）。 */
        data class BreakerTripped(val kind: BreakerKind, val tool: String?, val evidence: String) : Verdict
    }

    /** 熔断种类。UI 按它给具体文案（本层只给 ASCII 证据串）。 */
    enum class BreakerKind { TOOL_OSCILLATION, TOOL_FAILURE_STREAK }

    private val history = ArrayDeque<String>()
    private var lastSignature: String? = null
    private var sameCallStreak = 0
    private val failureStreaks = HashMap<String, Int>()

    /**
     * 执行**前**调用：登记签名并判定「同参 / 振荡」。
     * 顺序照搬旧仓：先计数入历史，再判振荡，最后判同参硬护栏。被同参挡下的调用**仍进
     * 历史** —— 振荡判据的「相邻两两不等」闸门会把它排除，归因留在同参重复。
     */
    fun beforeCall(tool: String, argumentsJson: String): Verdict {
        val signature = signatureOf(tool, argumentsJson)
        sameCallStreak = if (signature == lastSignature) sameCallStreak + 1 else 1
        lastSignature = signature
        history.addLast(signature)
        while (history.size > historyCapacity) history.removeFirst()
        ToolOscillation.evidence(history.toList())?.let {
            return Verdict.BreakerTripped(BreakerKind.TOOL_OSCILLATION, tool, it)
        }
        if (sameCallStreak >= sameCallExecLimit) return Verdict.RepeatedCall(tool, sameCallStreak)
        return Verdict.Proceed
    }

    /**
     * 执行**后**调用：登记成败并判定失败连击。成功即清零（「连续」语义）。
     * 阈值处返回熔断；调用方一旦据此终止就不会再调用本方法，故不做 trip 去重。
     */
    fun afterCall(tool: String, ok: Boolean): Verdict {
        if (ok) {
            failureStreaks.remove(tool)
            return Verdict.Proceed
        }
        val streak = (failureStreaks[tool] ?: 0) + 1
        failureStreaks[tool] = streak
        if (streak < failureStreakLimit) return Verdict.Proceed
        return Verdict.BreakerTripped(
            BreakerKind.TOOL_FAILURE_STREAK,
            tool,
            "tool " + tool + " failed " + streak + " times in a row",
        )
    }

    /** 指定工具的当前失败连击数（0 = 无连击）。诊断用。 */
    fun failureStreak(tool: String): Int = failureStreaks[tool] ?: 0

    /** 清空全部状态（新 run 复用同一个实例时调用）。 */
    fun reset() {
        history.clear()
        lastSignature = null
        sameCallStreak = 0
        failureStreaks.clear()
    }

    /** 签名 = `工具名:规范化参数`；规范化排序键序，否则模型换个键序就能绕过同参护栏。 */
    private fun signatureOf(tool: String, argumentsJson: String): String {
        val canonical = try {
            canonicalize(AgentJson.json.parseToJsonElement(argumentsJson)).toString()
        } catch (t: Throwable) {
            argumentsJson.trim()
        }
        return tool + ":" + canonical
    }

    private fun canonicalize(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { it.key to canonicalize(it.value) })
        is JsonArray -> JsonArray(element.map { canonicalize(it) })
        else -> element
    }

    companion object {
        const val DEFAULT_SAME_CALL_EXEC_LIMIT: Int = 3 // 同参第 N 次起不执行（前两次放行 = 允许重试一次）
        const val DEFAULT_FAILURE_STREAK_LIMIT: Int = 4 // 连续失败 N 次 ⇒ 熔断（W2 判据 #7）
        const val HISTORY_CAPACITY: Int = 48 // 签名历史容量（旧仓同值，环形裁剪）
    }
}
