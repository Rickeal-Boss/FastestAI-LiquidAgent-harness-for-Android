package com.rickeal.liquidharness.core.agent.journal

/**
 * 冻结感知时钟（FreezeClock，R5 / W61）：把**设备被系统冻结**的时间从墙钟里扣掉。
 *
 * ## 要解决的真实缺陷
 *
 * `RunState.elapsedMillis()` 用单调时钟（`System.nanoTime`）。Android 在 doze / cgroup
 * 冻结期间**单调时钟照走**，但 run 的协程整体停摆、模型什么都没做。解冻后一算，
 * 「墙钟 HARD（`wallClockHardMillis`）」就会把一次正常的长请求误熔断 —— 用户灭屏 60s
 * 回来看到一条「超时」，而 provider 其实一个字节都还没回。
 *
 * ## 判据：两个时间源之差
 *
 * - [wallNanos]：单调墙钟，冻结期间照走（`System::nanoTime`）。
 * - [frozenNanos]：**设备冻结累计时长**，冻结期间才增长。Android 侧由宿主注入
 *   `SystemClock.elapsedRealtimeNanos()` 与 `uptimeMillis()` 的差在 run 起止的采样值
 *   （`elapsedRealtime` 含深度睡眠、`uptimeMillis` 不含 ⇒ 差值单调不减）。JVM 单测注入
 *   可控 lambda 直接给冻结时长。
 *
 * `activeElapsed = (wallNow - wallStart) - (frozenNow - frozenStart)`，下限截到 0。
 *
 * ## 为什么不直接改 [RunState] 的墙钟源
 *
 * `RunState` 是 `400/400` 之外的 `391/400`（余 9 行），`GenerationRound` 才是顶格
 * `400/400` 零余量。本类以**可选注入**的形式接进 `RunState`：
 * `freezeClock: FreezeClock? = null`，null 时 `elapsedMillis()` 保持原单调墙钟口径
 * （既有 904 个用例零改动、行为零漂移）；宿主装配时才传入本实例。这样顶格对象零触碰、
 * 904 冻结不动，又让真机上墙钟 HARD 不再误熔。
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
