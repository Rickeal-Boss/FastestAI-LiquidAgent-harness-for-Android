package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.model.RemoteEndpoint
import java.util.concurrent.ConcurrentHashMap

// =============================================================================
// W5 从 AppContainer.kt 拆出（该文件 411 行 > G10 单文件上限 400）。
// 拆的是「按配置键缓存重型实例」这一件事：它有自己的并发语义与失效规则，
// 与容器装配是两件不同的事。**没有调大上限**——触顶的处理永远是拆分。
// =============================================================================

/**
 * runner 缓存键：**端点配置 + 窗口档位**。
 *
 * 整个 [RemoteEndpoint] 参与相等判定（它是 data class）：换模型 / 换 baseUrl / 开关工具面
 * 都算配置变更，都得重建 —— 旧 runner 的引擎还指着旧值，复用它等于让用户改完设置看不到效果。
 *
 * [maxContextTokens] 单独成键而不是并进 policy：它是「用户在设置页能改、且改了必须生效」
 * 的唯一 policy 字段（W4-07）。其余 policy 字段目前没有 UI 入口，进键只会让缓存永远不命中。
 */
internal data class RunnerKey(
    val endpoint: RemoteEndpoint,
    val maxContextTokens: Int,
    /**
     * 自动压缩开关（W6 调试项）。
     *
     * 它必须进键，理由与 [maxContextTokens] **完全相同**：这是「用户在设置页能改、
     * 且改了必须生效」的字段。不进键 ⇒ 用户关掉压缩后，缓存的旧 runner 仍带着
     * `compressContext = true`，开关看起来点了没反应（正是 W4-07 的缺陷形态）。
     */
    val compressContext: Boolean = true,
)

/**
 * 按 endpointId 缓存的重型实例（当前只有 [AgentRunner]），**键变了就重建**。
 *
 * ## 为什么需要它（W4-07 的缺陷形态）
 *
 * 原先用 `ConcurrentMap.getOrPut(id)`：第一次建 runner 时读一次窗口档位，之后永远命中，
 * 于是「改设置 → 不换端点不重启 → 永远不生效」。缓存本身不能删（`runMutex` 在实例里，
 * 每次 new 等于每次换一把锁），所以失效判据必须进缓存 —— 就是这里的 key 比对。
 *
 * ## 不做热更新
 *
 * 重建只影响**下一次**取用；已经在跑的 run 手里仍是旧实例、旧 policy 快照。
 * 在途 run 被换掉一半状态比「新配置晚一轮生效」危险得多。调用方还会用
 * [getOrBuild] 的 `keepWhile` 把「旧实例正忙」显式挡在替换之外（见 [AppContainer.agentRunnerFor]）。
 *
 * ## 并发
 *
 * [getOrBuild] 在锁外构造（构造里有磁盘 IO 与系统服务握手，不该占着 map 的桶锁），
 * 因此并发首建可能构造两次、只有一个赢家 —— 输的那份直接丢弃。这与 `ConcurrentMap.getOrPut`
 * 的语义一致，代价是多造一个对象，收益是不让 IO 进锁。
 */
internal class RunnerCache<T> {

    private class Entry<T>(val key: RunnerKey, val value: T)

    private val entries = ConcurrentHashMap<String, Entry<T>>()

    /**
     * 键相同则复用；键不同则重建并替换 —— **除非 [keepWhile] 说旧实例正忙**。
     *
     * [keepWhile] 是为「在途 run 不受影响」准备的：重建会换掉实例里的 `runMutex`，
     * 若旧实例正在跑，换掉它等于把互斥也换掉了（新实例的锁是空闲的）。所以忙的时候
     * 宁可让新配置晚一轮生效。返回的实例一定是与 [key] 匹配、或正忙被保留下来的那个。
     */
    fun getOrBuild(key: RunnerKey, keepWhile: (T) -> Boolean = { false }, build: () -> T): T {
        val id = key.endpoint.id
        val hit = entries[id]
        if (hit != null && (hit.key == key || keepWhile(hit.value))) return hit.value
        val fresh = Entry(key, build())
        val winner = entries.compute(id) { _, old ->
            if (old != null && (old.key == key || keepWhile(old.value))) old else fresh
        }
        return winner!!.value
    }

    /** 显式失效（保存 / 删除端点时调，让「配置已落盘」与「缓存已清」同一时刻发生）。 */
    fun remove(endpointId: String) {
        entries.remove(endpointId)
    }

    /**
     * 失效 —— **但旧实例正忙时不失效**（W6 / P2）。
     *
     * ## 为什么不能无条件 [remove]
     *
     * `remove` 之后下一次 [getOrBuild] 必然重建实例，而重建会换掉 `runMutex`：
     * 旧实例正跑着一条 run，新实例的锁是空闲的 ⇒ 此时再发一次就是**两条并发 run**
     * 写同一份历史（`RunState` 明确「单协程访问、不加锁」）。旧实现在
     * `AppContainer.saveEndpoint` 里无条件 `remove`，等于「保存端点」这个动作本身
     * 打开了一个双 runner 并发窗口。
     *
     * ## 为什么「不失效」不会让新配置丢失
     *
     * `RunnerKey` 含**整个** `RemoteEndpoint`：配置一变，key 就不等，`getOrBuild` 自己会
     * 重建。本方法只是让「配置已落盘」与「缓存已清」在同一时刻发生（少一次 key 比对），
     * 顺便把**已经没用的实例**从缓存里摘掉。忙的时候推迟到这一轮跑完 —— 与
     * `getOrBuild` 的 `keepWhile` 同一条纪律：**新配置晚一轮生效，好过并发写同一份状态**。
     *
     * @return true = 已失效；false = 旧实例正忙，保留（调用方不必处理，只是可断言）。
     */
    fun removeIfIdle(endpointId: String, keepWhile: (T) -> Boolean): Boolean {
        var removed = false
        entries.compute(endpointId) { _, old ->
            if (old != null && keepWhile(old.value)) {
                old
            } else {
                removed = old != null
                null
            }
        }
        return removed
    }

    /** 当前缓存条目数（诊断与单测用）。 */
    val size: Int get() = entries.size
}
