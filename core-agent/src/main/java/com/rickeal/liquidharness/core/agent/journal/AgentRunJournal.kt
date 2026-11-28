package com.rickeal.liquidharness.core.agent.journal

import kotlinx.coroutines.CancellationException

/**
 * journal v1（R5 / W61）：一次 run 的**事件溯源最小集**。
 *
 * ## 为什么只记 run 边界，不记每一轮
 *
 * 恢复判据只需要回答一个问题：**进程被杀时，是否存在一个 `run_started` 没有对应的
 * `run_settled`**。`round` / `tool` 是诊断信息，不是恢复判据的必要条件 —— 把它们塞进
 * 最小集只会扩大落盘面与隐私面（工具名可以固定，轮次是数字，但每一条都是一次 IO）。
 * 工具执行次数由 [com.rickeal.liquidharness.core.agent.loop.RunState.executedToolCount]
 * 在 settled 行以 `tools=N` 一次性带出，既不丢信息也不增加写点。
 *
 * ## 落盘是端口，不是依赖
 *
 * `:core-agent` 不得依赖 `:core-data`（G6 硬边），所以本类只持有 `sink: (String) -> Unit`
 * 端口；真正的落盘由 `:core-data` 的 `FileRunJournalStore` 在 `:app` 装配时注入。
 * `sink == null`（单测 / 无宿主）时 journal **只活在内存在环形缓冲里**，零 IO。
 *
 * ## 三条纪律
 *
 * 1. **固定形态、不含用户内容**：每行都是 `kind key=value ... at=<millis>`，`value` 只可能是
 *   数字 / 枚举 / uuid / 固定工具名，不含 prompt、工具参数、模型输出。这样落盘行天然
 *   满足脱敏门禁，不需要事后过滤。
 * 2. **写盘失败不改变主流程**：`sink` 抛异常被吞，与 `AgentLogStore` 的镜像出口同一契约。
 * 3. **有界**：环形缓冲 [capacity] 条，写满丢最旧。落盘文件侧另有轮转上限（core-data 侧）。
 */
class AgentRunJournal(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val sink: ((String) -> Unit)? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val buffer = ArrayDeque<String>()

    /** run 开始。判据起点：`run_started` 出现即进入「未结算」集合。 */
    fun runStarted(runId: String, inputChars: Int) {
        record("run_started", "runId=$runId inputChars=$inputChars")
    }

    /**
     * run 结算。判据终点：`run_settled` 出现即从「未结算」集合移除。
     *
     * 恢复判据只看 settled **是否存在**，不依赖原因枚举；[cancelled] 仅作诊断布尔，
     * [activeMillis] 是 [FreezeClock] 扣除设备冻结后的活跃时长（不是裸墙钟），
     * [tools] 是本 run 已执行工具数。全部是数字 / 布尔，无自由文本。
     */
    fun runSettled(runId: String, activeMillis: Long, tools: Int, cancelled: Boolean, frozenMillis: Long = 0L) {
        record("run_settled", "runId=$runId activeMs=$activeMillis frozenMs=$frozenMillis tools=$tools cancelled=$cancelled")
    }

    /**
     * 找一个**未结算**的 run（有 `run_started` 无 `run_settled`），返回最新的一条。
     *
     * 「最新」按事件顺序反查：进程被杀前的最后一次 run 才是需要恢复的那次；更早的
     * 未结算 run 若存在，说明上一次恢复也没结算 —— 仍以最新为准，避免把用户带回
     * 一个更旧的现场。
     *
     * @return runId；无未结算 run 时 `null`。
     */
    fun findUnsettled(): String? = synchronized(lock) {
        val settled = HashSet<String>()
        for (line in buffer) {
            if (wellFormed(line, "run_settled")) {
                field(line, "runId")?.let { settled.add(it) }
            }
        }
        for (line in buffer.asReversed()) {
            if (wellFormed(line, "run_started")) {
                val id = field(line, "runId") ?: continue
                if (id !in settled) return id
            }
        }
        null
    }

    /** 最近 [limit] 条事件（最旧 → 最新），供诊断页与真机取证读取；`limit<=0` 返回空。 */
    fun recent(limit: Int = DEFAULT_CAPACITY): List<String> = synchronized(lock) {
        if (limit <= 0 || buffer.isEmpty()) emptyList() else buffer.drop((buffer.size - limit).coerceAtLeast(0))
    }

    /** 当前保留条数（上限 [capacity]）。 */
    fun size(): Int = synchronized(lock) { buffer.size }

    /** 清空（供「丢弃恢复卡」与单测使用）。清空即放弃所有未结算 run。 */
    fun clear() = synchronized(lock) { buffer.clear() }

    private fun record(kind: String, detail: String) {
        val line = "$kind $detail at=${nowMillis()}"
        synchronized(lock) {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(line)
        }
        if (sink != null) {
            try {
                sink(line)
            } catch (c: CancellationException) {
                // CE 原样上抛（`AGENTS.md` §五.4）：sink 是**同步阻塞 IO**（:app 侧
                // FileRunJournalStore.append），取消可能发生在写盘中途；吞掉会让 Job
                // 从 cancelled 变 completed —— 与 W54 修掉的 10 处 `catch(Throwable)` 同型。
                throw c
            } catch (_: Throwable) {
                // 落盘失败不改变主流程（与 AgentLogStore.installFileSink 同契约）。
            }
        }
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 200

        /** 从 `key=value` 行里取一个 ASCII 值；不存在或含空白返回 null。 */
        fun field(line: String, key: String): String? {
            val needle = "$key="
            val at = line.indexOf(needle)
            if (at < 0) return null
            val start = at + needle.length
            var end = start
            while (end < line.length && line[end] != ' ') end++
            if (end == start) return null
            val value = line.substring(start, end)
            return if (value.any { it.isWhitespace() }) null else value
        }

        /**
         * 行形态完整判定：kind 前缀 + **runId 形态**（32 位小写 hex）+ 结尾 `at=<纯数字>`。
         *
         * 内存 buffer 的行恒为完整行，判据真正的用武之地是**落盘**侧（`RunRecoveryStore`）：
         * 非原子的 `appendText` 会在文件尾留截断行，旧写法只判 `startsWith` ⇒ 截断 runId
         * 直接进恢复卡（P2-2）。落盘侧与 `findUnsettled` 共用本函数 = 消掉双侧判据漂移（P3-1）。
         *
         * 三条限定（写实，防下一波误当缺陷）：
         * 1. `at=17`（时间戳数字被截断）仍算合格 —— 此时 runId 已完整，行为正确；
         * 2. **合流形态**（截断行吃掉 `\n`、下一次 append 接在同一物理行）由 runId 形态校验拦下：
         *    `field("runId")` 会把后一条记录的开头併进值里 ⇒ 非 32 位 hex ⇒ 判不合格，后一条
         *    `run_settled` 不会因此被整条吞掉；
         * 3. 截断落在 `cancelled=` 等尾部字段时，该条 `run_settled` 被丢弃 ⇒ 方向 fail-safe：
         *    宁可多弹一次恢复卡，不会少弹（少弹 = 用户丢现场且无提示）。
         */
        fun wellFormed(line: String, kind: String): Boolean {
            if (!line.startsWith("$kind ")) return false
            // runId 由 `RunState.runId → Ids.newId()` 生成 = UUID 去横线 ⇒ 恒 32 位小写 hex。
            val id = field(line, "runId") ?: return false
            if (id.length != 32 || id.any { it !in '0'..'9' && it !in 'a'..'f' }) return false
            val at = line.lastIndexOf(" at=")
            if (at < 0) return false
            val tail = line.substring(at + 4)
            return tail.isNotEmpty() && tail.all { it in '0'..'9' }
        }
    }
}
