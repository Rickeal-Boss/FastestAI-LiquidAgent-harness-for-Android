package com.rickeal.liquidharness.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.rickeal.liquidharness.core.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `SettingsRepository` 的真实持久化行为（挂账 W4-06 的主盲区）。
 *
 * ## 为什么这个文件在 `:feature-settings` 而不是 `:core-data`
 *
 * 设置页是这些键**唯一**的读写方：窗口档位（32K/128K/512K/1M）选完之后要能跨重启
 * 存活，否则「用户选了 1M」在下一次冷启动时静默回到 32K —— 而 UI 上仍然显示选中，
 * 属于没有编译错误、没有异常、只有账单不对的那类回归。所以判据放在消费方。
 *
 * ## 判据一律是「写 → **重读**」
 *
 * `setMaxContextTokens(x)` 返回 Unit，不读回来就无法区分「写了」与「只在内存里记了」。
 * 本文件的每个 round-trip 用例都走 `first()` 重新取值 —— 与 UI 的读取路径同源。
 *
 * ## fake 为什么是手写的
 *
 * 本仓禁 mock 框架（AGENTS.md §四），而 `SettingsRepository` 的构造参数是
 * `DataStore<Preferences>` 这个**接口** —— 手写一个内存实现比引 mock 便宜，也比
 * 「只测常量」诚实：它走的是真实的 `dataStore.edit { }` 写入路径。
 */
class SettingsRepositoryPersistenceTest {

    /** 内存版 `DataStore<Preferences>`：`updateData` 的语义与真实现一致（transform 后落定）。 */
    private class FakeDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {

        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    /** 读盘失败形态：`data` 一被收集就抛 `IOException`（磁盘损坏 / 文件被截断）。 */
    private class BrokenDataStore : DataStore<Preferences> {

        override val data: Flow<Preferences> = flow { throw IOException("simulated corrupt file") }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw IOException("write must not be attempted on a corrupt store")
    }

    private fun repo(store: DataStore<Preferences> = FakeDataStore()): SettingsRepository =
        SettingsRepository(store)

    // ------------------------------------------------------------------ 窗口档位

    /**
     * 未设置过 ⇒ 32 000（与 `AgentPolicy.maxContextTokens` 同源）。
     *
     * 这个数是**账单护栏**：默认值被悄悄调大，等于所有用户的下一次请求都变贵。
     */
    @Test
    fun unsetWindowFallsBackToThirtyTwoK() {
        val read = runBlocking { repo().maxContextTokens.first() }

        assertEquals(SettingsRepository.DEFAULT_MAX_CONTEXT_TOKENS, read)
        assertEquals(32_000, read)
    }

    /**
     * 四档各自「选中 → 重读 → 仍为选中」。
     *
     * 四档一次性跑完，是因为它们的失败模式相同但**边界不同**：1M 恰好等于
     * `MAX_MAX_CONTEXT_TOKENS`，一旦上限被改成 512K，只有最后一档会红。
     */
    @Test
    fun everyWindowTierSurvivesRoundTrip() {
        val repository = repo()
        val tiers = listOf(32_000, 128_000, 512_000, 1_000_000)

        for (tier in tiers) {
            runBlocking { repository.setMaxContextTokens(tier) }
            val reread = runBlocking { repository.maxContextTokens.first() }
            assertEquals(tier, reread, "档位 $tier 写入后读回不一致")
        }
    }

    /**
     * 下边界：8000 合法、7999 被钳到 8000。
     *
     * 这是 `SettingsRepository` KDoc 声称的行为（`coerceIn(MIN, MAX)`），
     * 靠读代码「相信」它是不够的 —— 写路径与读路径各钳一次，两处都可能漏。
     */
    @Test
    fun windowBelowMinimumIsClampedUp() {
        val repository = repo()

        runBlocking { repository.setMaxContextTokens(SettingsRepository.MIN_MAX_CONTEXT_TOKENS) }
        assertEquals(
            SettingsRepository.MIN_MAX_CONTEXT_TOKENS,
            runBlocking { repository.maxContextTokens.first() },
        )

        runBlocking { repository.setMaxContextTokens(SettingsRepository.MIN_MAX_CONTEXT_TOKENS - 1) }
        assertEquals(
            SettingsRepository.MIN_MAX_CONTEXT_TOKENS,
            runBlocking { repository.maxContextTokens.first() },
        )
    }

    /** 上边界：1 000 000 合法、1 000 001 被钳到 1 000 000（防「手滑多打一个 0」的账单）。 */
    @Test
    fun windowAboveMaximumIsClampedDown() {
        val repository = repo()

        runBlocking { repository.setMaxContextTokens(SettingsRepository.MAX_MAX_CONTEXT_TOKENS) }
        assertEquals(
            SettingsRepository.MAX_MAX_CONTEXT_TOKENS,
            runBlocking { repository.maxContextTokens.first() },
        )

        runBlocking { repository.setMaxContextTokens(SettingsRepository.MAX_MAX_CONTEXT_TOKENS + 1) }
        assertEquals(
            SettingsRepository.MAX_MAX_CONTEXT_TOKENS,
            runBlocking { repository.maxContextTokens.first() },
        )
    }

    // ------------------------------------------------------------ 其它设置项默认值

    /** 活跃端点未选择 ⇒ null（UI 走空态引导），写入后能读回，清空后回到 null。 */
    @Test
    fun activeEndpointIdDefaultsToNullAndRoundTrips() {
        val repository = repo()

        assertNull(runBlocking { repository.activeEndpointId.first() })

        runBlocking { repository.setActiveEndpointId("ep-1") }
        assertEquals("ep-1", runBlocking { repository.activeEndpointId.first() })

        // 空白 id 与 null 同义：都表示「没有活跃端点」，不能写进一个空串让 UI 以为选中了。
        runBlocking { repository.setActiveEndpointId("   ") }
        assertNull(runBlocking { repository.activeEndpointId.first() })
    }

    /**
     * 停用集合：默认空；关掉 ⇒ 进集合；打开 ⇒ 出集合；空名字被忽略。
     *
     * 存「停用集合」而不是「启用集合」，所以**新装技能默认可用** —— 这条不变量一旦
     * 反转，用户每次导入技能都要额外授权一次，而漏授权表现为「装了但用不了」。
     */
    @Test
    fun disabledSkillsStartEmptyAndToggle() {
        val repository = repo()

        assertTrue(runBlocking { repository.disabledSkills.first() }.isEmpty())

        runBlocking { repository.setSkillEnabled("pdf", false) }
        assertEquals(setOf("pdf"), runBlocking { repository.disabledSkills.first() })

        runBlocking { repository.setSkillEnabled("pdf", true) }
        assertTrue(runBlocking { repository.disabledSkills.first() }.isEmpty())

        runBlocking { repository.setSkillEnabled("   ", false) }
        assertTrue(runBlocking { repository.disabledSkills.first() }.isEmpty())
    }

    /** 引导完成标记：默认 false（未完成），写 true 后读回 true。 */
    @Test
    fun onboardingFlagDefaultsToFalseAndRoundTrips() {
        val repository = repo()

        assertFalse(runBlocking { repository.hasCompletedOnboarding.first() })

        runBlocking { repository.setHasCompletedOnboarding(true) }
        assertTrue(runBlocking { repository.hasCompletedOnboarding.first() })
    }

    /**
     * 读盘失败（文件损坏 / 磁盘 IO 错）必须**降级成默认值**而不是把异常抛给 UI。
     *
     * 理由：设置页在 `init` 里就读它，一次 IO 抖动不该让整个设置页打不开。
     * 判据是「拿到默认值」—— 只看「没崩」不够：吞掉异常后返回一个错误的值同样是坏行为。
     */
    @Test
    fun corruptStoreDegradesToDefaults() {
        val repository = repo(BrokenDataStore())

        assertEquals(
            SettingsRepository.DEFAULT_MAX_CONTEXT_TOKENS,
            runBlocking { repository.maxContextTokens.first() },
        )
        assertNull(runBlocking { repository.activeEndpointId.first() })
        assertFalse(runBlocking { repository.hasCompletedOnboarding.first() })
    }
}
