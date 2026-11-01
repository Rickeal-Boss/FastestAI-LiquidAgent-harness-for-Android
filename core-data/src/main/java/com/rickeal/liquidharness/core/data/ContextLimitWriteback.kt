package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import kotlinx.coroutines.CancellationException

/**
 * 把服务端在 **400 超窗**里回报的真实窗口写回端点（`verifiedContextLength`）。
 *
 * ## 为什么要有这个类（W6 收口）
 *
 * 整条链早就做完了：`GenerationEvent.Failed.contextLimitHint`（adapter 只认
 * `maximum context length is (\d+)`，W5 观测 9 次同一句）→ `RunState` →
 * `AgentEvent.Failed.contextLimitHint`，而 [AppContainer.recordContextLimit] 也已实现。
 * **缺的只是最后一个消费者** —— 没有它，学到了也不落盘，下一轮仍按超报的窗口算预算，
 * 于是同一个 400 每轮重演一次（每次都付一次往返 + 一次计费）。
 *
 * 这是本仓反复出现的第 4 类形态：「链路都在、只差一跳」，与 `compressContext` 的零写入点、
 * `EngineCapabilities` 的零消费点同源。
 *
 * ## 为什么落在 `:core-data`（而不是 UI 层）
 *
 * 「学到的窗口写回端点」是**数据层**的事：它写的是 `EndpointStore` 的
 * `verifiedContextLength`，与 [AppContainer.recordContextLimit] 同源同层。
 * 放在 UI 层会变成「只有某个 ViewModel 记得写回」，第二个 run 入口（将来的会话恢复 /
 * 子代理）就又会漏 —— 而漏的表现是「同一 400 反复付费」，极难归因。
 * 放这里的第二个好处是模块行数：`feature-chat` 贴着 3 500 行预算。
 *
 * ## 三条语义边界（不要扩大）
 *
 * - **只有真的 400 超窗才带值**。`emitBreaker` 的 `context_overflow_prevented` **不带**
 *   hint：那是「本地拦住、请求根本没发出去」，没有服务端真值可写回。所以 `verified == null`
 *   时静默返回，不要「猜一个值」。
 * - `verified <= 0` 同样丢弃：网关噪声不许写进预算（[AppContainer.recordContextLimit]
 *   的 KDoc 同一口径）。
 * - 端点 id 由调用方给（run 的端点）。拿不到（run 期间端点被删）就什么都不做。
 *
 * ## 为什么失败要静默、只记 WARN
 *
 * 用户此刻盯着的是「这次请求为什么失败」。写回失败（端点被删 / 磁盘满）盖上去只会
 * 让错误面板变成「记账失败」—— 那是给排障者看的，不是给用户看的。
 *
 * ## 为什么用注入的 `record` 而不是直接持有 [AppContainer]
 *
 * 为了**可测**。`AppContainer` 是具体类（DataStore + Keystore），JVM 单测里造不出来；
 * 而「`Failed(hint)` ⇒ 写回被调用一次且参数正确」是本项唯一的判据。注入一个
 * `suspend (String, Int) -> Unit` 之后，单测用一个记账 lambda 就能精确断言调用次数与参数。
 */
class ContextLimitWriteback(
    private val record: suspend (endpointId: String, verified: Int) -> Unit,
) {

    /**
     * 尝试写回。**不抛异常**（取消除外）。
     *
     * ⚠️ 不用 `runCatching`：它会吞掉 `CancellationException`，让取消的协程变成正常完成
     * （`AGENTS.md` §五.4）。这里显式前置重抛。
     */
    suspend fun offer(endpointId: String?, verified: Int?) {
        if (endpointId.isNullOrBlank() || verified == null || verified <= 0) return
        try {
            record(endpointId, verified)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AgentLogStore.warn(
                "context_limit_writeback_failed endpoint=" + endpointId + " verified=" + verified,
            )
        }
    }
}
