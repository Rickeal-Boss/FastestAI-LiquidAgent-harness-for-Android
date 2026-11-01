package com.rickeal.liquidharness.core.data

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 「实测窗口写回」的落盘判据（W5-02 / 判据 T6 的第二跳）。
 *
 * ## 为什么这条判据必须在**落盘层**测，而不是只测解析器
 *
 * 解析器绿而「解析出来没人存」是本仓踩过的形态（声明了没消费者）。T6 要求
 * 「解析 + 落盘两跳」，这里钉的是第二跳：写进去、**换一个 store 实例再读出来**。
 * 用同一个实例读是 vacuous 的 —— 它可能只是读了内存态。
 *
 * ## `AppContainer.recordContextLimit` 为什么没有单测
 *
 * `AppContainer` 的构造需要 Android `Context`（`filesDir` / `getSystemService` /
 * `AndroidKeyStoreCipher`），JVM 单测造不出来（本仓无 Robolectric）。
 * 它对本类的调用是三行纯委托，真正的持久化逻辑全在这里 ——
 * 如实登记：**AppContainer 那一跳只有编译期保证，没有 JVM 判据**。
 */
class EndpointStoreTest {

    private fun newStore(dir: java.io.File): EndpointStore = EndpointStore(JsonFileStore(dir))

    @Test
    fun verifiedContextLengthSurvivesAProcessRestart() {
        val dir = Files.createTempDirectory("endpoint-store").toFile()
        val id = EndpointStore.SEED_OPENAI

        runBlocking { newStore(dir).recordVerifiedContextLength(id, 1_000_000) }

        // 换一个 store 实例读：走的是真的文件，不是内存态。
        val reopened = runBlocking { newStore(dir).get(id) }
        assertEquals(1_000_000, reopened?.verifiedContextLength)
        // 声明值不许被改写：实测值只在预算算式里压过它，不能覆盖用户填的窗口。
        assertEquals(128_000, reopened?.contextLength)
    }

    /** 非正数一律忽略：`maximum context length is 0` 是网关噪声，写进去会让预算算式归零。 */
    @Test
    fun nonPositiveHintIsIgnored() {
        val dir = Files.createTempDirectory("endpoint-store").toFile()
        val store = newStore(dir)

        val written = runBlocking { store.recordVerifiedContextLength(EndpointStore.SEED_OPENAI, 0) }

        assertNull(written)
        assertNull(runBlocking { store.get(EndpointStore.SEED_OPENAI) }?.verifiedContextLength)
    }

    /** 端点不存在时什么都不做（迟到的失败事件不许把已删的端点复活）。 */
    @Test
    fun unknownEndpointIsNotResurrected() {
        val dir = Files.createTempDirectory("endpoint-store").toFile()
        val store = newStore(dir)

        val written = runBlocking { store.recordVerifiedContextLength("no-such-endpoint", 1_000_000) }

        assertNull(written)
        assertNull(runBlocking { store.get("no-such-endpoint") })
    }
}
