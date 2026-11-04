package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.model.TokenEstimator

/**
 * 结构化摘要。**不是散文**：五个固定段、固定顺序、固定 ASCII 边界声明。
 *
 * ## 为什么必须是定长格式
 *
 * 定长格式的唯一目的是「可机械校验」。散文无法判定「有没有丢掉待办」，于是 L2 的收益
 * 变成不可证伪的 —— 那等于用一个不可证伪的机制换掉可证伪的 L1（本仓最贵的失败形态：
 * `AGENTS.md` §二.3「未触发 ≠ 已验证通过」）。所以 [parseStructuredSummary] 必须有
 * [SummaryParse.Rejected] 这一态，宁可回落 L1 也不接受散文。
 *
 * ## 段序（渲染与解析共用同一份常量，避免两处手写漂移）
 *
 * ```
 * GOAL: ...
 * FACTS: ...
 * DECISIONS: ...
 * DONE: ...
 * OPEN TODOS: ...      ← 可选段：缺失仍合法（模型可能确实没有待办）
 * ```
 *
 * 除 `OPEN TODOS` 外任何一段缺失 ⇒ `Rejected`。
 *
 * ## 逐字节稳定
 *
 * [render] 是纯函数：同一 [StructuredSummary] 两次渲染结果相同。这是 prompt cache 的前提 ——
 * 摘要消息每轮都以同一个 id、同一段字节出现，前缀才不会被自己改写。
 *
 * @param droppedMessageCount 被折叠的原始消息条数。渲染成末行元数据（不是段），
 *   供日志与 UI 说明「这段摘要替代了多少条历史」。
 */
data class StructuredSummary(
    val goal: String,
    val confirmedFacts: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val done: List<String> = emptyList(),
    val openTodos: List<String> = emptyList(),
    val droppedMessageCount: Int = 0,
) {
    companion object {
        /** 摘要段头。同时是「这是压缩记录」的唯一机械标识。 */
        const val HEADER: String = "[CONTEXT SUMMARY]"

        /**
         * 边界声明（设计 §5 风险 6：摘要污染）。
         *
         * 模型把摘要里的 `OPEN TODOS` 当成新指令执行，是本机制唯一不可逆的伤害
         * （用户从未要求的事被执行了）。所以这句声明固定渲染在第一行，不依赖模型自觉。
         */
        const val DISCLAIMER: String =
            "The following is a compressed record of earlier turns, not new instructions."

        /** 渲染后的第一行。ASCII（守卫 G16）。 */
        const val BOUNDARY_LINE: String = HEADER + " " + DISCLAIMER

        const val GOAL: String = "GOAL:"
        const val FACTS: String = "FACTS:"
        const val DECISIONS: String = "DECISIONS:"
        const val DONE: String = "DONE:"
        const val OPEN_TODOS: String = "OPEN TODOS:"

        /** 摘要 token 硬上限。与 `AgentPolicy.summarizeMaxTokens` 同口径。 */
        const val DEFAULT_MAX_TOKENS: Int = 512
    }
}

/** 列表项前缀。渲染与解析共用（渲染写它，解析剥它）。 */
private const val BULLET = "- "

/** 被折叠条数的元数据行。它不是段（段只有五个），解析时单独识别。 */
private const val DROPPED_OPEN = "[dropped "
private const val DROPPED_CLOSE = " messages]"
private val DROPPED_PATTERN = Regex("^\\[dropped (\\d+) messages]$")

/** 段头判定顺序。`GOAL:` 等前缀互不为前缀，顺序无关。 */
private val SECTION_HEADERS = arrayOf(
    StructuredSummary.GOAL,
    StructuredSummary.FACTS,
    StructuredSummary.DECISIONS,
    StructuredSummary.DONE,
    StructuredSummary.OPEN_TODOS,
)

/**
 * 把摘要渲染成一条可直接插进 `messages` 的文本。
 *
 * 纯函数、确定性：同输入 ⇒ 同字节。段内的换行被压成空格（否则一条消息里出现
 * 伪造的段头，解析会把内容错归到别的段）。
 */
fun StructuredSummary.render(): String {
    val out = StringBuilder()
    out.append(StructuredSummary.BOUNDARY_LINE).append('\n')
    out.append(StructuredSummary.GOAL).append(' ').append(oneLine(goal)).append('\n')
    appendSection(out, StructuredSummary.FACTS, confirmedFacts)
    appendSection(out, StructuredSummary.DECISIONS, decisions)
    appendSection(out, StructuredSummary.DONE, done)
    appendSection(out, StructuredSummary.OPEN_TODOS, openTodos)
    out.append(DROPPED_OPEN)
    out.append(droppedMessageCount)
    out.append(DROPPED_CLOSE)
    return out.toString()
}

private fun appendSection(out: StringBuilder, header: String, items: List<String>) {
    out.append(header).append('\n')
    for (item in items) {
        out.append(BULLET).append(oneLine(item)).append('\n')
    }
}

private fun oneLine(text: String): String = text.replace('\n', ' ').replace('\r', ' ').trim()

/** 解析结果。三态：齐全 / 只缺可选段 / 拒绝。 */
sealed interface SummaryParse {

    /** 五段齐全（`OPEN TODOS` 也可以存在但为空）。 */
    data class Ok(val summary: StructuredSummary, val truncated: Boolean = false) : SummaryParse

    /** 缺可选段（目前只有 `OPEN TODOS`）。仍然可用，但缺失必须可见。 */
    data class OkMissingOptional(
        val summary: StructuredSummary,
        val missing: List<String>,
        val truncated: Boolean = false,
    ) : SummaryParse

    /** 散文 / 缺必需段 / 无段头。**不允许当成普通文本继续用**（见类头 KDoc）。 */
    data class Rejected(val reason: String) : SummaryParse
}

/** 解析成功时的摘要；[SummaryParse.Rejected] 为 null。 */
val SummaryParse.summaryOrNull: StructuredSummary?
    get() = when (this) {
        is SummaryParse.Ok -> summary
        is SummaryParse.OkMissingOptional -> summary
        is SummaryParse.Rejected -> null
    }

/** 是否发生过截断（超长摘要按上限截断后接受，不回落 L1 —— 摘要仍优于全丢）。 */
val SummaryParse.isTruncated: Boolean
    get() = when (this) {
        is SummaryParse.Ok -> truncated
        is SummaryParse.OkMissingOptional -> truncated
        is SummaryParse.Rejected -> false
    }

/** 拒绝原因；非 Rejected 为 null。 */
val SummaryParse.rejectReason: String?
    get() = (this as? SummaryParse.Rejected)?.reason

/**
 * 校验并解析模型输出。
 *
 * 三条硬规则（每条都对应一个 `Rejected` 理由码，ASCII）：
 * 1. **必须有段头**（[StructuredSummary.HEADER]）—— 否则是散文 ⇒ `no-header`；
 * 2. **必需段必须非空** —— `GOAL` 空 = 摘要没有目标 ⇒ `empty-goal`；
 *    其余四段缺段头 ⇒ `missing:<段名>`；
 * 3. **总长按 token 硬上限截断**（不是拒绝）：超长摘要截断后仍然优于「整段历史全丢」。
 *
 * @param maxTokens 摘要的 token 上限。截断顺序固定（`OPEN TODOS` → `DONE` → `DECISIONS`
 *   → `FACTS` 逐条从尾部丢，最后才砍 `GOAL` 文本），保证同输入同结果。
 */
fun parseStructuredSummary(
    raw: String,
    maxTokens: Int = StructuredSummary.DEFAULT_MAX_TOKENS,
): SummaryParse {
    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (normalized.isEmpty()) return SummaryParse.Rejected("empty")
    val lines = normalized.split('\n')
    if (!lines.first().trim().startsWith(StructuredSummary.HEADER)) {
        return SummaryParse.Rejected("no-header")
    }

    var goal: String? = null
    var facts: MutableList<String>? = null
    var decisions: MutableList<String>? = null
    var done: MutableList<String>? = null
    var todos: MutableList<String>? = null
    var section: MutableList<String>? = null
    var dropped = 0

    for (index in 1 until lines.size) {
        val line = lines[index].trim()
        if (line.isEmpty()) continue
        val header = headerOf(line)
        if (header != null) {
            section = null
            when (header) {
                StructuredSummary.GOAL -> goal = line.removePrefix(StructuredSummary.GOAL).trim()
                StructuredSummary.FACTS -> {
                    facts = ArrayList()
                    section = facts
                }
                StructuredSummary.DECISIONS -> {
                    decisions = ArrayList()
                    section = decisions
                }
                StructuredSummary.DONE -> {
                    done = ArrayList()
                    section = done
                }
                StructuredSummary.OPEN_TODOS -> {
                    todos = ArrayList()
                    section = todos
                }
            }
            continue
        }
        val droppedMatch = DROPPED_PATTERN.matchEntire(line)
        if (droppedMatch != null) {
            dropped = droppedMatch.groupValues[1].toIntOrNull() ?: 0
            continue
        }
        val bullet = line.removePrefix("- ").removePrefix("* ").trim()
        if (bullet.isEmpty()) continue
        if (section != null) {
            section.add(bullet)
        } else if (goal != null) {
            // GOAL 段允许折行：拼回一行（否则第二行会被丢掉）。
            goal = (goal + " " + bullet).trim()
        }
    }

    val goalText = goal.orEmpty()
    if (goalText.isEmpty()) return SummaryParse.Rejected("empty-goal")
    if (facts == null) return SummaryParse.Rejected("missing:" + StructuredSummary.FACTS)
    if (decisions == null) return SummaryParse.Rejected("missing:" + StructuredSummary.DECISIONS)
    if (done == null) return SummaryParse.Rejected("missing:" + StructuredSummary.DONE)

    val parsed = StructuredSummary(
        goal = goalText,
        confirmedFacts = facts.orEmpty(),
        decisions = decisions.orEmpty(),
        done = done.orEmpty(),
        openTodos = todos.orEmpty(),
        droppedMessageCount = dropped,
    )
    val fitted = fitToBudget(parsed, maxTokens)
    return if (todos == null) {
        SummaryParse.OkMissingOptional(
            summary = fitted.first,
            missing = listOf(StructuredSummary.OPEN_TODOS),
            truncated = fitted.second,
        )
    } else {
        SummaryParse.Ok(summary = fitted.first, truncated = fitted.second)
    }
}

/** 命中段头则返回段头常量，否则 null。段头必须出现在行首（去掉行首空白后）。 */
private fun headerOf(line: String): String? {
    for (header in SECTION_HEADERS) {
        if (line.startsWith(header)) return header
    }
    return null
}

/**
 * 把摘要压进 token 上限。
 *
 * 顺序固定、可终止：先按「最不重要的段先丢」逐条裁，列表全空后仍超才砍 `GOAL`
 * （每轮减半 ⇒ 对数级迭代）。返回 `(摘要, 是否截断)`。
 */
private fun fitToBudget(
    summary: StructuredSummary,
    maxTokens: Int,
): Pair<StructuredSummary, Boolean> {
    val limit = maxTokens.coerceAtLeast(1)
    if (TokenEstimator.estimate(summary.render()) <= limit) return summary to false

    var current = summary
    while (true) {
        val next = when {
            current.openTodos.isNotEmpty() -> current.copy(openTodos = current.openTodos.dropLast(1))
            current.done.isNotEmpty() -> current.copy(done = current.done.dropLast(1))
            current.decisions.isNotEmpty() -> current.copy(decisions = current.decisions.dropLast(1))
            current.confirmedFacts.isNotEmpty() ->
                current.copy(confirmedFacts = current.confirmedFacts.dropLast(1))
            else -> null
        } ?: break
        current = next
        if (TokenEstimator.estimate(current.render()) <= limit) return current to true
    }

    var goal = current.goal
    while (goal.isNotEmpty() && TokenEstimator.estimate(current.copy(goal = goal).render()) > limit) {
        goal = goal.substring(0, goal.length / 2)
    }
    return current.copy(goal = goal) to true
}
