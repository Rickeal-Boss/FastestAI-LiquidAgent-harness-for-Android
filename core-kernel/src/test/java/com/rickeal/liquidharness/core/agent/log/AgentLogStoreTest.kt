package com.rickeal.liquidharness.core.agent.log

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 环形缓冲的驱逐优先级单测。
 *
 * 为什么要有这一类：`AgentLogStore` 是「出事后唯一能回看的东西」，而它的 KDoc 承诺
 * 「优先丢 INFO」。原实现把这条承诺实现反了（INFO 无条件 `removeFirst()`），
 * 后果是熔断 / 压缩这类关键证据被高频 INFO 顶掉，且**没有任何测试会红** ——
 * 这类「文档说 A、代码做 B、测试全绿」的缺陷只能由钉住语义的用例抓到。
 *
 * 三条用例各自钉一档优先级（见 `dropOneFor` 的 KDoc）：
 * 1. INFO 满缓冲 + 新 INFO ⇒ 丢最旧 INFO（不是丢最旧条目）；
 * 2. 满缓冲全 WARN/ERROR + 新 WARN ⇒ 丢最旧一条（确实一直在报错）；
 * 3. 满缓冲全 WARN/ERROR + 新 INFO ⇒ **丢弃这条 INFO**，既有条目一条不动。
 */
class AgentLogStoreTest {

    @AfterTest
    fun tearDown() {
        AgentLogStore.clear()
    }

    private fun fill(level: AgentLogStore.Level, count: Int, tag: String) {
        repeat(count) { i -> AgentLogStore.record(level, "$tag-$i") }
    }

    @Test
    fun infoEvictsOldestInfoFirst() {
        AgentLogStore.clear()
        // 头部一条 WARN，其余用 INFO 填满：新 INFO 必须吃掉最旧的 INFO，WARN 必须还在。
        AgentLogStore.warn("crash-evidence")
        fill(AgentLogStore.Level.INFO, AgentLogStore.DEFAULT_CAPACITY - 1, "i")
        assertEquals(AgentLogStore.DEFAULT_CAPACITY, AgentLogStore.size())

        AgentLogStore.info("newcomer")

        assertEquals(AgentLogStore.DEFAULT_CAPACITY, AgentLogStore.size())
        val kept = AgentLogStore.recent()
        assertTrue(kept.any { it.message == "crash-evidence" }, "WARN 被 INFO 顶掉了")
        assertTrue(kept.any { it.message == "newcomer" })
        assertTrue(kept.none { it.message == "i-0" }, "应丢最旧 INFO，实际仍在")
    }

    @Test
    fun warnEvictsOldestInfoWhenBufferIsMixed() {
        AgentLogStore.clear()
        AgentLogStore.info("oldest-info")
        fill(AgentLogStore.Level.WARN, AgentLogStore.DEFAULT_CAPACITY - 1, "w")

        AgentLogStore.warn("newcomer")

        val kept = AgentLogStore.recent()
        assertEquals(AgentLogStore.DEFAULT_CAPACITY, AgentLogStore.size())
        assertTrue(kept.none { it.message == "oldest-info" }, "WARN 进来时应优先牺牲 INFO")
        assertTrue(kept.any { it.message == "newcomer" })
    }

    @Test
    fun infoDoesNotEvictWarnOrError() {
        AgentLogStore.clear()
        // 缓冲被 WARN/ERROR 填满 —— 模拟「一直在报错」，此时一条 INFO 不得挤掉任何一条。
        fill(AgentLogStore.Level.ERROR, 100, "e")
        fill(AgentLogStore.Level.WARN, AgentLogStore.DEFAULT_CAPACITY - 100, "w")
        val before = AgentLogStore.recent()

        AgentLogStore.info("noise")

        val after = AgentLogStore.recent()
        assertEquals(AgentLogStore.DEFAULT_CAPACITY, AgentLogStore.size())
        assertEquals(before.map { it.message }, after.map { it.message }, "INFO 挤掉了 WARN/ERROR")
        assertTrue(after.none { it.message == "noise" }, "满缓冲无 INFO 可牺牲时应丢弃这条 INFO")
    }

    @Test
    fun errorStaysWhenOnlyInfoFloods() {
        AgentLogStore.clear()
        AgentLogStore.error("breaker-tripped")
        // 200 条 INFO 灌入后 ERROR 必须仍在缓冲里（外审 F4 的原始判据）。
        fill(AgentLogStore.Level.INFO, AgentLogStore.DEFAULT_CAPACITY, "flood")

        assertTrue(
            AgentLogStore.recent().any { it.message == "breaker-tripped" },
            "200 条 INFO 之后 ERROR 已被顶掉",
        )
    }

    /**
     * W44（W12-02 出口判据）：落盘镜像必须收到 `cached=` 四态探针行。
     *
     * 这一条钉的是「端口被调用」而不是「端口存在」：`cached=` 只在 INFO 级写入，
     * W44 之前它只进环形缓冲、重启即失，设备侧无从复核四态。镜像端口若不转交
     * INFO，本用例 RED。注入「sink 只转 WARN/ERROR」⇒ RED。
     */
    @Test
    fun fileSinkReceivesInfoProbeLines() {
        AgentLogStore.clear()
        val seen = mutableListOf<String>()
        AgentLogStore.installFileSink { _, message -> seen.add(message) }
        try {
            AgentLogStore.info("prefix fp=abc changed=none prompt=1234 cached=n/a-usage")
            AgentLogStore.info("prefix fp=abc changed=none prompt=1300 cached=0(cumulative)")

            assertEquals(2, seen.size, "INFO 探针行必须逐条转交落盘镜像：seen=$seen")
            assertTrue(seen[0].contains("cached=n/a-usage"), "四态标签不得在镜像里丢失：seen=$seen")
            assertTrue(seen[1].contains("cached=0(cumulative)"), "累计口径后缀不得丢：seen=$seen")
        } finally {
            AgentLogStore.installFileSink(null)
        }
    }

    /**
     * W44：落盘镜像抛异常不得影响缓冲写入与调用方（日志失败 ≠ run 失败）。
     *
     * 注入「sink 抛异常后 record 提前返回」⇒ 本用例 RED（`survives` 不在缓冲里）。
     */
    @Test
    fun fileSinkFailureDoesNotBreakRecord() {
        AgentLogStore.clear()
        AgentLogStore.installFileSink { _, _ -> throw IllegalStateException("disk full") }
        try {
            AgentLogStore.info("survives")
            assertTrue(
                AgentLogStore.recent().any { it.message == "survives" },
                "sink 抛异常后缓冲仍须写入：${AgentLogStore.recent().map { it.message }}",
            )
        } finally {
            AgentLogStore.installFileSink(null)
        }
    }
}
