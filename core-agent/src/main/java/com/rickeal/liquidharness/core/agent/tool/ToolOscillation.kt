package com.rickeal.liquidharness.core.agent.tool

/**
 * 工具调用振荡判定（旧仓 `breaker/ToolOscillationDetector` 的纯函数部分）。
 *
 * 为什么从 [ToolExecutionGuard] 里拆出来：两条判据各自有阈值与误杀面，独立成文件才能
 * 单独钉边界（旧仓也是独立文件）；护栏本体的 120 行上限也因此守得住。
 *
 * 双判据（阈值与理由逐条沿用旧仓）：
 * - **窗口塌缩**：末尾 [WINDOW] 次 distinct ≤ [COLLAPSE_DISTINCT] **且相邻两两不等**
 *   （抓 A/B 交替）。「两两不等」把同参连发（distinct=1）与换参重试簇（A×n → B×n）
 *   排除在外 —— 那两种是簇状序列，归因应留在同参 / 失败连击，不该被写成「来回打转」。
 * - **周期距离**：尾部 [CYCLE_STREAK] 步各自到「上一个同签名位置」的距离相同且
 *   ≥ [MIN_PERIOD]（抓 A/B/C/A/B/C 这种距离判据才抓得住的展开周期；周期 2 由窗口判据兜住）。
 *
 * 已知误杀面（旧仓 §3.5 已接受）：严格交替的合法读写循环（file_read/file_write）会被
 * 窗口判据判作振荡 —— 需窗口内满 6 次且严格交替，误杀时证据串含签名摘要，用户可从
 * 诊断卡判断。
 */
internal object ToolOscillation {

    /** 窗口大小：6 = 3 个 A/B 周期（与同参护栏「每签名各放行 3 次」对称）。 */
    const val WINDOW: Int = 6

    /** 窗口内 distinct 上限。 */
    const val COLLAPSE_DISTINCT: Int = 2

    /** 周期下限：更短的周期由窗口判据兜住。 */
    const val MIN_PERIOD: Int = 3

    /** 尾部连续等距步数。 */
    const val CYCLE_STREAK: Int = 3

    /** 返回 trip 证据（ASCII，进日志与诊断卡）或 null（未检出）。 */
    fun evidence(history: List<String>): String? {
        if (history.size >= WINDOW) {
            val window = history.takeLast(WINDOW)
            val distinct = window.toSet().size
            val alternating = (1 until window.size).all { window[it] != window[it - 1] }
            if (distinct <= COLLAPSE_DISTINCT && alternating) {
                return "last " + window.size + " tool calls alternate between " + distinct + " distinct calls"
            }
        }
        if (history.size <= CYCLE_STREAK) return null
        var period = 0
        for (k in 1..CYCLE_STREAK) {
            val index = history.size - k
            val previous = (index - 1 downTo 0).firstOrNull { history[it] == history[index] } ?: return null
            val distance = index - previous
            if (distance < MIN_PERIOD) return null
            if (period == 0) period = distance else if (period != distance) return null
        }
        return "tool calls loop with period " + period + " (equal steps back to the same call)"
    }
}
