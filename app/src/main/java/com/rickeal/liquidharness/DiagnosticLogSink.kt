package com.rickeal.liquidharness

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.data.AgentLogFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 把 `AgentLogStore` 的记录**按探针行白名单**镜像到落盘日志（W44 建立镜像，W45 落盘最小化）。
 *
 * ## 为什么需要它（W12-02 的原始缺口）
 *
 * `cached=` 四态探针是 INFO 级，只进进程内 200 条环形缓冲 —— 设备侧
 * `files/harness/logs/agent-*.log` 里没有它，「探针按四态落盘」这条出口判据一直未行使：
 * 真机只能间接验证 UI 四态，重启后现场消失。这份镜像让 `cached=n/a-usage` /
 * `cached=0(cumulative)` / `cached=N(cumulative)` 真正落到设备文件上，可用
 * `run-as <pkg>` 取出复核。
 *
 * ## W45 落盘最小化（外审 P1-4：隐私面收窄）
 *
 * W44 版把 `record` 的**全部** INFO/WARN/ERROR 镜像落盘；`sanitize()` 只认凭据形态，
 * 用户 prompt 正文 / 工具参数 / 模型输出片段会随普通 WARN 行（`tool threw`、
 * `unknown tool requested` 等）进入 `run-as` 可取的持久化文件 —— 隐私面从
 * 「内存 ring 200 条、重启即消失」扩到持久化文件。W45 起 sink 只镜像**探针 / 账本行**
 * （[isProbeLine] 白名单），非探针行仍进环形缓冲（诊断页可见）、不落盘。白名单只认
 * **固定诊断前缀**，这些行的正文由常量模板 + 数字 / ASCII 诊断码构成，不含用户内容。
 *
 * ## 架构位置（为什么在 :app 而不是 core-kernel 里直接写文件）
 *
 * `:core-kernel` 不得依赖 `:core-data`（模块边界），所以端口开在
 * `AgentLogStore.installFileSink`，由本类在进程启动时装配。**单一出口**保证
 * 端口语义（锁外调用、异常不影响缓冲）只有一处实现。
 *
 * ## 三条纪律
 *
 * 1. **不阻塞调用方**：sink 只做 `trySend`，真正的 IO 在单个写协程里串行执行
 *    —— 模型流/工具循环线程不得因为日志磁盘慢而卡住。
 * 2. **有界**：队列 512 条 DROP_OLDEST；文件侧 256KB × 3 轮转（`AgentLogFileStore`
 *    自身的上限），总量不随会话时长增长。**探针行优先**：非探针行在入队前就被
 *    白名单滤掉、不占队列容量，高频普通 WARN 不会把 `cached=` 探针挤出队列。
 * 3. **失败只丢一行**：队列满 / 写盘失败都不影响主流程（`AgentLogFileStore.append`
 *    内部已吞 IO 异常且放行取消）。
 */
class DiagnosticLogSink(private val store: AgentLogFileStore) {

    private val queue = Channel<Pair<String, String>>(
        capacity = QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installed = false

    /** 幂等安装：重复调用不会起第二个写协程。 */
    fun install() {
        if (installed) return
        installed = true
        scope.launch {
            for ((level, message) in queue) {
                store.append(level, message)
            }
        }
        AgentLogStore.installFileSink { level, message ->
            // W45 落盘最小化：非探针行只进环形缓冲（诊断页可见），不落盘、不占队列。
            if (!isProbeLine(message)) return@installFileSink
            // DROP_OLDEST 下 trySend 只在通道已关闭时失败 ⇒ 忽略返回值即可。
            queue.trySend(level.name to message)
        }
    }

    companion object {
        /** 512 条 ≈ 一次长 run 的探针行量级；满了丢最旧，不扩内存。 */
        const val QUEUE_CAPACITY: Int = 512

        /**
         * 探针 / 账本行白名单（W45 落盘最小化）。
         *
         * 只认**固定诊断前缀**，正文由常量模板 + 数字 / ASCII 诊断码构成、不含用户
         * prompt / 工具参数 / 模型输出正文。新增探针行时把它的稳定前缀加进来 ——
         * 前缀必须与写点字面量同源（漏了只损失落盘、不损失 ring，方向安全）。
         * `context limit writeback` 对应 `core-data/ContextLimitWriteback` 的账本行。
         */
        private val PROBE_PREFIXES = arrayOf(
            "cached=",
            "auto_compact",
            "manual_compress",
            "compress_align",
            "compress=disabled_by_policy",
            "context_overflow_prevented",
            "run cancelled",
            "run failed",
            "context compressed",
            "summary truncated",
            "layered compress",
            "context limit writeback",
        )

        /** 落盘白名单判据（internal 供 JVM 单测直接钉行为）。 */
        internal fun isProbeLine(message: String): Boolean {
            val m = message.trim()
            return PROBE_PREFIXES.any { m.startsWith(it) } ||
                // `prefix fp=… prompt=… cached=…` 行的固定开头（ContextBudget.observePrefix）。
                (m.startsWith("prefix ") && m.contains("cached="))
        }
    }
}
