package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.model.RemoteEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * runner 缓存的失效判据（W4-07）。
 *
 * 事故形态：缓存只按 endpointId 命中，而窗口档位是在**建 runner 时**读一次的 ——
 * 用户在设置页改档位后，不换端点不重启就永远不生效，且没有任何报错。
 *
 * 这里用 `RunnerCache<String>` 而不是真 [AppContainer]：`AppContainer` 需要 Android
 * `Context`，JVM 单测造不出来；而失效判据的全部逻辑都在缓存里，与实例类型无关。
 * 「policy 是 run 级快照」那一半由构造保证（重建不触碰旧实例），用引用同一性断言。
 */
class RunnerCacheTest {

    private val endpoint = RemoteEndpoint(id = "e1", name = "test", baseUrl = "https://api.example.com/v1")

    private fun key(window: Int, ep: RemoteEndpoint = endpoint) = RunnerKey(ep, window)

    /** 窗口档位变了 ⇒ 下一次取用重建（这正是 W4-07 缺的那条判据）。 */
    @Test
    fun windowChangeRebuildsOnNextCall() {
        val cache = RunnerCache<String>()
        val first = cache.getOrBuild(key(32_000)) { "runner-32000" }

        val second = cache.getOrBuild(key(64_000)) { "runner-64000" }

        assertEquals("runner-32000", first)
        assertEquals("runner-64000", second)
        assertTrue(first !== second)
    }

    /** 端点配置变了（同窗口）同样要重建：旧 runner 的引擎还指着旧 baseUrl / modelId。 */
    @Test
    fun endpointChangeRebuildsEvenWithSameWindow() {
        val cache = RunnerCache<String>()
        val first = cache.getOrBuild(key(32_000)) { "old-model" }

        val second = cache.getOrBuild(key(32_000, endpoint.copy(modelId = "new-model"))) { "new-model" }

        assertTrue(first !== second)
        assertEquals("new-model", second)
    }

    /** 配置没变 ⇒ 必须复用：`runMutex` 在实例里，每次 new 等于每次换一把锁。 */
    @Test
    fun unchangedConfigReusesTheSameInstance() {
        val cache = RunnerCache<String>()
        val first = cache.getOrBuild(key(32_000)) { "runner" }

        val again = cache.getOrBuild(key(32_000)) { "another-runner" }

        assertTrue(first === again)
        assertEquals(1, cache.size)
    }

    /**
     * 在途 run 不受影响：旧实例正忙时不换（换掉等于换掉 `runMutex`），
     * 跑完之后的下一次取用才换 —— 新配置不丢，只是晚一轮生效。
     */
    @Test
    fun inFlightRunKeepsTheOldInstanceUntilItFinishes() {
        val cache = RunnerCache<String>()
        val inFlight = cache.getOrBuild(key(32_000)) { "runner-32000" }

        val duringRun = cache.getOrBuild(key(64_000), keepWhile = { it == "runner-32000" }) { "runner-64000" }
        assertEquals("runner-32000", inFlight)
        assertEquals("runner-32000", duringRun)

        val afterRun = cache.getOrBuild(key(64_000)) { "runner-64000" }
        assertEquals("runner-64000", afterRun)
    }

    /** 显式失效（保存 / 删除端点）之后必然重建，哪怕配置一模一样。 */
    @Test
    fun explicitRemovalForcesRebuild() {
        val cache = RunnerCache<String>()
        cache.getOrBuild(key(32_000)) { "first" }

        cache.remove(endpoint.id)
        val rebuilt = cache.getOrBuild(key(32_000)) { "second" }

        assertEquals("second", rebuilt)
    }
}
