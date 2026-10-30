package com.rickeal.liquidharness.feature.chat

/**
 * 流式文本的节流缓冲：攒增量、按窗口整段取出。
 *
 * 抽成独立类的唯一理由是**可测**：`ChatViewModel` 依赖 `viewModelScope`（默认 Main
 * 调度器），而本模块测试源集只有 `junit` + `kotlin-test`，没有
 * `kotlinx-coroutines-test` 也没有 Robolectric，ViewModel 无法在 JVM 上实例化。
 * 把「攒 / 取 / 清」这三件纯内存操作挪到这里，就能用普通单测钉住分段语义；
 * ViewModel 只负责「什么时候调 drain」。
 *
 * ## 线程约束
 *
 * **非线程安全**。设计上只在 `viewModelScope`（Main）里访问 —— 归约、节流循环、
 * `newConversation` 三处都在主线程，因此不需要加锁。若将来把它挪到别的调度器，
 * 必须先补同步，否则会出现「drain 取到一半又被 append」的撕裂。
 */
internal class StreamFlushBuffer {

    private val pending = StringBuilder()

    /** 追加一段增量。空串是无操作（内核偶尔会发空 delta，不该白白唤醒刷新循环）。 */
    fun append(delta: String) {
        if (delta.isEmpty()) return
        pending.append(delta)
    }

    /**
     * 取出并清空。
     *
     * @return 自上次 drain 以来累积的全部文本；缓冲为空时返回 `""`。
     *   「取走即清空」是必须的：留着内容会让下一窗口把同一段再刷一遍，
     *   屏幕上就会出现重复的半句话。
     */
    fun drain(): String {
        if (pending.isEmpty()) return ""
        val chunk = pending.toString()
        pending.setLength(0)
        return chunk
    }

    /** 丢弃未刷出的内容。换会话 / 重发请求时用。 */
    fun clear() {
        pending.setLength(0)
    }

    val isEmpty: Boolean
        get() = pending.isEmpty()

    val length: Int
        get() = pending.length
}
