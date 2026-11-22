package com.rickeal.liquidharness

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.data.AgentLogFileStore
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * W45 落盘最小化（外审 P1-4）：`DiagnosticLogSink` 探针行白名单的行为判据。
 *
 * ## 钉的是什么
 *
 * W44 版镜像把**全部** INFO/WARN/ERROR 落盘 —— 普通 WARN 行（`tool threw`、
 * `unknown tool requested`）会携带用户 prompt 正文 / 工具参数 / 模型输出片段进入
 * `run-as` 可取的持久化文件，隐私面比「内存 ring、重启即消失」大得多。
 * W45 白名单只在落盘收窄；环形缓冲（诊断页）行为不变。
 *
 * 三条判据互为方向：
 * - 正向：探针 / 账本行必须落盘（`cached=` 四态是 W12-02 销账的出口判据，不得丢）；
 * - 反向：含用户内容的普通行不得落盘（注入「去掉白名单过滤」⇒ 反向用例 RED）；
 * - 洪水：**先写探针，再灌 600 条普通行**（> 512 队列容量）—— 白名单生效时普通行
 *   不占队列，探针必然落盘；注入「白名单失效」时普通行把探针从队头挤出 ⇒ RED。
 */
class DiagnosticLogSinkTest {

    @Test
    fun probeAndLedgerLinesAreMirrored() {
        // 四态探针（W12-02 出口判据的四类形态）与压缩 / 熔断账本行必须落盘。
        assertTrue(DiagnosticLogSink.isProbeLine("prefix fp=ab12 changed=none prompt=1234 cached=n/a-usage"))
        assertTrue(DiagnosticLogSink.isProbeLine("prefix fp=ab12 changed=none prompt=1300 cached=0(cumulative)"))
        assertTrue(DiagnosticLogSink.isProbeLine("prefix fp=ab12 changed=none prompt=13 cached=4096(cumulative)"))
        assertTrue(DiagnosticLogSink.isProbeLine("prefix fp=ab12 changed=history cached=24(cumulative)"))
        assertTrue(DiagnosticLogSink.isProbeLine("auto_compact=noop reason=below_trigger"))
        assertTrue(DiagnosticLogSink.isProbeLine("auto_compact dropped=9 kept=11 cache_invalidated=false"))
        assertTrue(DiagnosticLogSink.isProbeLine("manual_compress dropped=9 kept=11 cache_invalidated=false"))
        assertTrue(DiagnosticLogSink.isProbeLine("context_overflow_prevented: chars=59064 cap=50227"))
        assertTrue(DiagnosticLogSink.isProbeLine("run cancelled: chars=120 rounds=3"))
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: request rejected (HTTP 400)"))
        assertTrue(DiagnosticLogSink.isProbeLine("context compressed: 30 -> 12 messages (budget=20928 target=12556)"))
        assertTrue(DiagnosticLogSink.isProbeLine("summary truncated: max=1024"))
        assertTrue(DiagnosticLogSink.isProbeLine("layered compress: mode=L1_FALLBACK reason=over-hard-budget"))
        assertTrue(DiagnosticLogSink.isProbeLine("context limit writeback: 131072 -> 65536"))
        // 前后空白不影响前缀判据（写点侧无空白，防御性断言）。
        assertTrue(DiagnosticLogSink.isProbeLine("  cached=4096(cumulative)  "))
    }

    @Test
    fun userContentLinesAreNotMirrored() {
        // 普通 WARN 行会携带用户 prompt / 工具参数 / 模型输出片段 —— 白名单必须拦下。
        assertFalse(DiagnosticLogSink.isProbeLine("tool file_read threw: permission denied for /sdcard/secret.txt"))
        assertFalse(DiagnosticLogSink.isProbeLine("unknown tool requested: run_shell"))
        assertFalse(DiagnosticLogSink.isProbeLine("tool blocked by policy: clipboard_get (dangerous)"))
        assertFalse(DiagnosticLogSink.isProbeLine("approval card suppressed after 2 denials: file_write"))
        assertFalse(DiagnosticLogSink.isProbeLine("session import: 120 messages"))
        assertFalse(DiagnosticLogSink.isProbeLine("stream ended without finish_reason; treating as truncated (chars=88)"))
        // 与探针同前缀开头但不是探针的句子不得因子串误入白名单。
        assertFalse(DiagnosticLogSink.isProbeLine("runaway prompt detected (demo)"))
        assertFalse(DiagnosticLogSink.isProbeLine("prefix fp=ab12 changed=none prompt=1234"))
    }

    /**
     * W45 必红（队列满时探针优先保留）：真实落盘 + 真实 512 条 DROP_OLDEST 队列。
     *
     * 先写探针、再灌 600 条普通 WARN（> QUEUE_CAPACITY）。白名单生效时普通行在入队前
     * 被滤掉、探针仍落盘；注入「sink 不过滤、全部入队」⇒ 600 条把先前的探针从队头
     * 挤出 ⇒ 文件里找不到探针 ⇒ 本条 RED。
     */
    @Test
    fun probeLineSurvivesFloodOfUserContentLines() {
        val tmp = Files.createTempDirectory("w45-sink-flood").toFile()
        try {
            val fileStore = AgentLogFileStore(tmp)
            val sink = DiagnosticLogSink(fileStore)
            sink.install()
            try {
                AgentLogStore.clear()
                AgentLogStore.info("prefix fp=abc changed=none prompt=100 cached=n/a-usage")
                repeat(600) { i -> AgentLogStore.warn("tool file_read threw: user payload line $i") }
            } finally {
                AgentLogStore.installFileSink(null)
            }
            runBlocking { delay(600) }
            val lines = runBlocking { fileStore.recentLines(1_000) }
            assertTrue(
                lines.any { it.contains("cached=n/a-usage") },
                "探针行必须先写后仍落盘，不能被 600 条普通行挤出队列：落盘 ${lines.size} 行",
            )
            assertFalse(
                lines.any { it.contains("user payload line") },
                "普通用户内容行不得落盘",
            )
        } finally {
            tmp.deleteRecursively()
        }
    }
}
