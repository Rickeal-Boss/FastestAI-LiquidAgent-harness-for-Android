package com.rickeal.liquidharness.core.agent.journal

/**
 * 冻结感知时钟（FreezeClock，R5 / W61）：把**设备冻结**的时间从墙钟里扣掉。
 *
 * ## 要解决的真实缺陷
 *
 * `RunState.elapsedMillis()` 用单调时钟（`System.nanoTime`）。进程被系统挂起期间
 * run 的协程整体停摆、模型什么都没做，但计时仍在走；解冻后一算，「墙钟 HARD
 * （`wallClockHardMillis`）」可能把一次正常的长请求误熔断 —— 用户回来看到一条
 * 「超时」，而 provider 其实一个字节都还没回。
 *
 * ## 判据：两个时间源之差（**W62 订正：源与目标场景的语义边界**）
 *
 * `activeElapsed = (wallNow - wallStart) - (frozenNow - frozenStart)`，下限截到 0。
 *
 * - [wallNanos]：`System::nanoTime`（`CLOCK_MONOTONIC`）。
 * - [frozenNanos]：Android 侧由宿主注入 `elapsedRealtimeNanos() - uptimeMillis()×1e6`
 *   （`CLOCK_BOOTTIME - CLOCK_MONOTONIC`）。JVM 单测注入可控 lambda。
 *
 * ⚠️ **两者测的不是同一件事，W62 如实登记（W61 外审 P2 疑难，不合并成「已解决」）**：
 *
 * | 场景 | `nanoTime`（wall） | 上述注入源（frozen） | 结果 |
 * | --- | --- | --- | --- |
 * | 系统 suspend（深度睡眠） | **停走**（MONOTONIC 不含 suspend） | 增长 | 双重扣减，active 偏小（方向安全，但语义重复） |
 * | 进程 cgroup 冻结（系统未 suspend） | 照走 | **不变**（两源都照走） | 扣不到，防护对本场景**无效** |
 *
 * 也就是说：当前注入源测的是「系统 suspend」，而 suspend 下 `nanoTime` 本就不走 ⇒
 * 这条链在真机上退化为 no-op（`frozenMs=0` 与之一致）。**cgroup 冻结没有公开时钟
 * 可观测量**，本类不冒充覆盖它；保留端口是为了将来有可靠源时一行换入，而不是宣称
 * 已解决。W61 验收物 e1 的「113s 不误熔」是**裸墙钟**（113s ≪ HARD 600s）的结论，
 * 与 FreezeClock 无关，不作本链证据。
 *
 * ## 为什么不直接改 [RunState] 的墙钟源
 *
 * 本类以**可选注入**的形式接进 `RunState`：`freezeClock: FreezeClock? = null`，null 时
 * `elapsedMillis()` 保持原单调墙钟口径（既有 904 个用例零改动、行为零漂移）；
 * 宿主装配时才传入本实例。这样顶格对象零触碰、904 冻结不动。
 *
 * ## 误用边界
 *
 * 首字节看门狗（`generationTimeoutMillis`）**刻意不接**本时钟：它防的是「远端不响应」，
 * 冻结时协程与 `delay` 一起停摆，看门狗本就不会提前开火；扣掉冻结时长反而会漏掉
 * 「对端真 120s 不吐字节」。本时钟只服务墙钟 SOFT / HARD 的 run 级计时。
 */
class FreezeClock(
    private val wallNanos: () -> Long = System::nanoTime,
    private val frozenNanos: () -> Long = { 0L },
) {
    private val wallStart = wallNanos()
    private val frozenStart = frozenNanos()

    /** run 起点的冻结采样值，供诊断行落「冻结时长」证据。 */
    val frozenStartNanos: Long = frozenStart

    /**
     * 扣除冻结后的活跃时长（毫秒）。
     *
     * 截零避免「宿主注入的冻结源回退 / 精度抖动」导致负数跑进熔断文案。
     */
    fun activeElapsedMillis(): Long {
        val wall = (wallNanos() - wallStart) / NANOS_PER_MILLI
        val frozen = (frozenNanos() - frozenStart) / NANOS_PER_MILLI
        return (wall - frozen).coerceAtLeast(0L)
    }

    /** 本 run 至今累计冻结时长（毫秒），真机诊断行用。 */
    fun frozenMillis(): Long = ((frozenNanos() - frozenStart) / NANOS_PER_MILLI).coerceAtLeast(0L)

    companion object {
        const val NANOS_PER_MILLI: Long = 1_000_000L
    }
}
