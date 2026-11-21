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
 * 把 `AgentLogStore` 的**全部**记录镜像到落盘日志（W44，W12-02 出口判据）。
 *
 * ## 为什么需要它（W12-02 的原始缺口）
 *
 * `cached=` 四态探针是 INFO 级，只进进程内 200 条环形缓冲 —— 设备侧
 * `files/harness/logs/agent-*.log` 里没有它，「探针按四态落盘」这条出口判据一直未行使：
 * 真机只能间接验证 UI 四态，重启后现场消失。这份镜像让 `cached=n/a-usage` /
 * `cached=0(cumulative)` / `cached=N(cumulative)` 真正落到设备文件上，可用
 * `run-as <pkg>` 取出复核。
 *
 * ## 架构位置（为什么在 :app 而不是 core-kernel 里直接写文件）
 *
 * `:core-kernel` 不得依赖 `:core-data`（模块边界），所以端口开在
 * `AgentLogStore.installFileSink`，由本类在进程启动时装配。**单一出口**保证
 * 以后新增的日志点自动获得落盘，不需要逐个接线（改一处漏一处的反面）。
 *
 * ## 三条纪律
 *
 * 1. **不阻塞调用方**：sink 只做 `trySend`，真正的 IO 在单个写协程里串行执行
 *    —— 模型流/工具循环线程不得因为日志磁盘慢而卡住。
 * 2. **有界**：队列 512 条 DROP_OLDEST；文件侧 256KB × 3 轮转（`AgentLogFileStore`
 *    自己的上限），总量不随会话时长增长。
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
            // DROP_OLDEST 下 trySend 只在通道已关闭时失败 ⇒ 忽略返回值即可。
            queue.trySend(level.name to message)
        }
    }

    companion object {
        /** 512 条 ≈ 一次长 run 的决策点量级；满了丢最旧，不扩内存。 */
        const val QUEUE_CAPACITY: Int = 512
    }
}
