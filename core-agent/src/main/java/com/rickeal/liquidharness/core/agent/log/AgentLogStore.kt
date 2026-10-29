package com.rickeal.liquidharness.core.agent.log

import com.rickeal.liquidharness.core.model.SecretRedactor

/**
 * 进程内诊断日志的**环形缓冲**（本仓的「黑匣子」）。
 *
 * ## 为什么需要它
 *
 * 验证通道只有「编译 + 单测 + 一台真机」：编译只能证明能编译，证明不了行为对。
 * 真机上出问题时（工具结果被丢弃、半截回答落库、重试后重复输出……）这些是**静默缺陷** ——
 * 不崩不报错，只是结果错，用户不会去抓 logcat。把关键决策点留在进程内，
 * 至少能在出事后回看「刚才发生了什么」。
 *
 * ## 设计取舍
 *
 * - **绝不无限增长**：容量固定 [DEFAULT_CAPACITY] 条，写满丢最旧（端侧内存不允许扩容）。
 * - **不落盘**：日志不进会话 JSON、不写文件；杀进程即清空。代价是重启后现场丢失，
 *   换来的是会话文件不被撑大。落盘由 `core-data` 的 `AgentLogFileStore` 负责（W2）。
 * - **进程级单例**：写入方（内核 / 引擎）与读取方（设置页）横跨多个模块，
 *   走单例不需要任何 DI 装配。
 * - **线程安全**：run 跑在 Default/IO 线程，UI 在主线程读，全部经同一把锁。
 *
 * ## 脱敏（这是本类唯一不能省的部分）
 *
 * 正则**复用 `core-model` 的 [SecretRedactor]**，不在这里写第二份。两份正则的结局是
 * 「改一处漏一处」，而漏的那一处正好是上屏路径 —— 上屏风险 > 落盘风险
 * （会进截图与 bug report，`AGENTS.md` §六）。
 *
 * 与旧仓 `AgentLogStore`（core-model，214 行）的差别：旧仓自带 `secretPattern` 正则，
 * 新仓把它移到 [SecretRedactor]（8 类扩容 + `redactHeaders` / `redactUrl`），
 * 本类只负责「出口先脱敏再截断」。
 */
object AgentLogStore {

    /** 环形缓冲容量上限。超出后丢弃最旧的一条。 */
    const val DEFAULT_CAPACITY: Int = 200

    /**
     * 单条消息保留上限：防止把整段模型输出 / 工具输出塞进日志导致内存膨胀。
     *
     * 它同时是「崩溃现场单行预算」的口径（`core-data` 的崩溃留痕按它反推），
     * 所以**不要**在别处再写一个数字。
     */
    const val MAX_MESSAGE_CHARS: Int = 400

    private val lock = Any()

    // 用 kotlin.collections.ArrayDeque：带初始容量的主构造器是 internal（跨模块不可见），
    // 所以只能无参构造；容量上限由 DEFAULT_CAPACITY 在写入处强制。
    private val buffer = ArrayDeque<Entry>()

    fun info(message: String) = record(Level.INFO, message)

    fun warn(message: String) = record(Level.WARN, message)

    fun error(message: String) = record(Level.ERROR, message)

    fun record(level: Level, message: String) {
        val entry = Entry(level = level, message = sanitize(message), atMillis = System.currentTimeMillis())
        synchronized(lock) {
            if (buffer.size >= DEFAULT_CAPACITY) dropOneFor(entry)
            buffer.addLast(entry)
        }
    }

    /**
     * 缓冲满时腾一个位置给 [entry]，**优先丢 INFO**（调用方必须持有 [lock]）。
     *
     * 为什么不是一律丢最旧：工具调用、上下文压缩都会写 INFO，200 条缓冲很快被 INFO 填满；
     * 若一律丢最旧，**崩溃前刚发生的 WARN/ERROR 会被 INFO 挤掉** —— 恰恰是用户来诊断页
     * 最该看到的那几条没了。WARN/ERROR 进来时先找最旧的一条 INFO 丢掉；只有缓冲里
     * 全是 WARN/ERROR（说明确实一直在报错）才退化成丢最旧一条。
     */
    private fun dropOneFor(entry: Entry) {
        if (entry.level == Level.INFO) {
            buffer.removeFirst()
            return
        }
        var victim = -1
        for (i in buffer.indices) {
            if (buffer[i].level == Level.INFO) {
                victim = i
                break
            }
        }
        if (victim < 0) {
            buffer.removeFirst()
            return
        }
        val kept = ArrayList<Entry>(buffer.size - 1)
        for (i in buffer.indices) if (i != victim) kept.add(buffer[i])
        buffer.clear()
        buffer.addAll(kept)
    }

    /** 按时间**正序**（最旧 → 最新）返回最近 [limit] 条。`limit <= 0` 返回空。 */
    fun recent(limit: Int = DEFAULT_CAPACITY): List<Entry> {
        if (limit <= 0) return emptyList()
        return synchronized(lock) {
            if (buffer.isEmpty()) emptyList() else buffer.drop((buffer.size - limit).coerceAtLeast(0))
        }
    }

    /** 当前已保留条数（上限 [DEFAULT_CAPACITY]）。 */
    fun size(): Int = synchronized(lock) { buffer.size }

    /** 清空（供诊断页 / 单测使用）。 */
    fun clear() = synchronized(lock) { buffer.clear() }

    /**
     * 给**要显示给用户看**的文本做脱敏 + 截断 —— 与进日志的是同一套规则。
     *
     * 为什么必须单独开一个入口：上屏的文本和进日志的文本风险等级不同。日志留在设备
     * 私有目录，上屏文案会进截图、录屏、用户发来的 bug report —— 相当于主动外发。
     *
     * 这个方法是**纯函数**：不写缓冲，可以在任意线程调。
     */
    fun sanitizeUserFacing(raw: String): String = sanitize(raw)

    /** 脱敏 + 代理对安全截断。两条都是为了「日志本身不能变成新的内存 / 安全风险」。 */
    private fun sanitize(raw: String): String {
        val redacted = SecretRedactor.redact(raw)
        return if (redacted.length > MAX_MESSAGE_CHARS) {
            truncateSafe(redacted, MAX_MESSAGE_CHARS) + "..."
        } else {
            redacted
        }
    }

    /**
     * 代理对安全截断：绝不在一个 surrogate pair 中间切开。
     *
     * 切开一个 emoji / 生僻字（U+1F600 之类）会得到孤立代理项，落进日志与诊断面板
     * 就是 U+FFFD 乱码 —— 而乱码本身会让人误判「日志编码坏了」。
     *
     * ⚠️ `core-model` 若日后提供 `TruncateSafe`，本函数应改为复用它（同「正则不写两份」
     * 的理由）。当前 `core-model` 尚未提供，故就近实现，不外泄。
     */
    private fun truncateSafe(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        var end = maxChars
        if (end > 0 && Character.isHighSurrogate(text[end - 1])) end -= 1
        return text.substring(0, end)
    }

    /** 日志级别。刻意只有三档：再细的分级在 200 条缓冲里没有意义。 */
    enum class Level { INFO, WARN, ERROR }

    /** 一条日志。不可变。 */
    data class Entry(
        val level: Level,
        val message: String,
        val atMillis: Long,
    )
}
