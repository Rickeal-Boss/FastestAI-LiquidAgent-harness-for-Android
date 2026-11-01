package com.rickeal.liquidharness.feature.settings

import com.rickeal.liquidharness.core.data.EndpointSaveError
import com.rickeal.liquidharness.core.data.EndpointStore
import com.rickeal.liquidharness.core.data.JsonFileStore
import com.rickeal.liquidharness.core.data.SettingsRepository
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `:feature-settings` 的契约测试（挂账 W4-06：本模块此前只有 1 条 `SmokeTest`）。
 *
 * ## 这个文件测的是什么（以及**不**测什么）
 *
 * 设置模块的两块行为：
 *
 * 1. **端点持久化**（[EndpointStore] + [JsonFileStore]）：本模块是唯一写端点清单的
 *    消费者，「保存」的语义是「**重启后还在**」，所以判据一律是「换一个 store 实例
 *    读同一目录」，而不是「内存里对就行」。种子只种一次的规则也在这里钉住 ——
 *    它是 `EndpointStore` KDoc 点名的「最容易被写成 bug 的一条」（用户删掉的预置
 *    端点会在下次启动复活）。
 * 2. **状态与路由契约**（[SettingsUiState] / `EndpointEditRoute` / `McpEditRoute` /
 *    [EndpointSaveError]）：这些是 UI 与数据层之间的**稳定形状**，改名的代价是
 *    「错误码静默失去文案」「编辑页永远以新增打开」，都属于没有编译错误的那类回归。
 *
 * ## 覆盖边界（哪一条在哪个文件里）
 *
 * - **窗口档位的真实持久化**（选中 → 写盘 → 重读仍是选中）与**上下边界钳制**
 *   （8000 / 1_000_000 合法，越界被钳到边界）在
 *   [SettingsRepositoryPersistenceTest] —— 那个文件用手写的 `DataStore<Preferences>`
 *   走真实 `dataStore.edit { }` 写入路径。
 * - **非法 baseUrl 被拒**仍**未覆盖**：校验在 `AppContainer.saveEndpoint` →
 *   `core-net` 的 `UrlGuard`，而 `core-net` 是 `:core-data` 的 `implementation` 依赖、
 *   不向上暴露 ⇒ 本模块的测试源集拿不到 `UrlGuard`（也不该为了测它而把网络栈
 *   泄漏到 feature 层）。本文件只钉住错误码集合本身。
 */
class SettingsContractTest {

    // ---------------------------------------------------------------- 端点持久化

    private fun tempStore(): Pair<File, EndpointStore> {
        val dir = Files.createTempDirectory("settings-endpoint-test").toFile()
        return dir to EndpointStore(JsonFileStore(dir))
    }

    /**
     * 保存 → 「重启」（新 store 读同一目录）→ 仍在。
     *
     * 判据是**读回**而不是写返回值：`upsert` 返回的列表来自内存态，一个只改内存
     * 不落盘的实现照样能返回正确列表。
     */
    @Test
    fun savedEndpointSurvivesRestart() {
        val (dir, store) = tempStore()
        val saved = RemoteEndpoint(
            id = "ep-a",
            name = "My gateway",
            baseUrl = "https://api.example.com/v1",
            modelId = "gpt-4o-mini",
        )
        runBlocking { store.upsert(saved) }

        val reopened = EndpointStore(JsonFileStore(dir))
        val loaded = runBlocking { reopened.get("ep-a") }

        val actual = requireNotNull(loaded) { "保存过的端点在新实例里读不回来" }
        assertEquals("https://api.example.com/v1", actual.baseUrl)
        assertEquals("gpt-4o-mini", actual.modelId)
        assertEquals("My gateway", actual.name)
    }

    /** 同 id 再保存 = **覆盖**，不是追加（列表里出现两条同 id 会让「编辑」变成「复制」）。 */
    @Test
    fun upsertWithSameIdOverwritesInsteadOfAppending() {
        val (dir, store) = tempStore()
        runBlocking {
            store.upsert(RemoteEndpoint(id = "ep-b", name = "before", baseUrl = "https://a.example.com"))
            store.upsert(RemoteEndpoint(id = "ep-b", name = "after", baseUrl = "https://b.example.com"))
        }

        val reopened = EndpointStore(JsonFileStore(dir))
        val all = runBlocking { reopened.list() }
        val mine = all.filter { it.id == "ep-b" }

        assertEquals(1, mine.size)
        assertEquals("after", mine.first().name)
        assertEquals("https://b.example.com", mine.first().baseUrl)
    }

    /** 删除后不得复活 —— 这是「预置只种一次」在**用户删除**方向上的判据。 */
    @Test
    fun removedEndpointStaysRemovedAfterRestart() {
        val (dir, store) = tempStore()
        runBlocking {
            store.list()
            store.remove(EndpointStore.SEED_OLLAMA)
        }

        val reopened = EndpointStore(JsonFileStore(dir))
        val all = runBlocking { reopened.list() }

        assertTrue(all.none { it.id == EndpointStore.SEED_OLLAMA })
        assertNull(runBlocking { reopened.get(EndpointStore.SEED_OLLAMA) })
    }

    /**
     * 首次启动写入三组预置；**文件一旦存在就绝不补种**。
     *
     * 反过来（每次启动补齐）会让删除操作永远无效 —— 用户在设置页删掉 Ollama 之后
     * 重启，它又回来了，而且没有任何提示。
     */
    @Test
    fun seedsAreWrittenOnceAndNeverResurrected() {
        val (dir, store) = tempStore()
        val first = runBlocking { store.list() }
        assertEquals(3, first.size)
        assertEquals(
            listOf(EndpointStore.SEED_OPENAI, EndpointStore.SEED_DEEPSEEK, EndpointStore.SEED_OLLAMA),
            first.map { it.id },
        )

        runBlocking { store.remove(EndpointStore.SEED_DEEPSEEK) }

        val reopened = EndpointStore(JsonFileStore(dir))
        val second = runBlocking { reopened.list() }
        assertEquals(2, second.size)
        assertTrue(second.none { it.id == EndpointStore.SEED_DEEPSEEK })
    }

    // ------------------------------------------------------------ 状态与路由契约

    /**
     * 未设置时的窗口初值 = 32 000（与 `AgentPolicy.maxContextTokens` 同源）。
     *
     * 这个数同时是**账单护栏**：默认值被悄悄调大，等于所有用户的下一次请求都变贵。
     * 改它必须同时改 `SettingsRepository.DEFAULT_MAX_CONTEXT_TOKENS` 与 `AgentPolicy`
     * —— 本条会在只改一处时立刻红。
     */
    @Test
    fun settingsStateDefaultsToThirtyTwoKWindow() {
        assertEquals(SettingsRepository.DEFAULT_MAX_CONTEXT_TOKENS, SettingsUiState().maxContextTokens)
        assertEquals(32_000, SettingsUiState().maxContextTokens)
    }

    /** 初始态必须「未加载 + 无错误 + 无端点」：UI 靠 `loaded` 区分空态与骨架。 */
    @Test
    fun settingsStateStartsUnloadedWithoutError() {
        val state = SettingsUiState()

        assertFalse(state.loaded)
        assertFalse(state.probing)
        assertFalse(state.saveCompleted)
        assertNull(state.saveError)
        assertNull(state.activeId)
        assertNull(state.probeTarget)
        assertNull(state.probeResult)
        assertTrue(state.endpoints.isEmpty())
    }

    /**
     * 四档窗口（32K / 128K / 512K / 1M）都必须落在仓储的钳制区间内。
     *
     * 值直接抄自 `SettingsScreen.ContextWindowRows` 的 `presets`（那是 Composable 内的
     * 私有局部量，本模块的测试源集够不到）。这里钉的是**上下界**：
     * `MAX_MAX_CONTEXT_TOKENS` 若被降到 512K，用户选 1M 会被静默钳成 512K ——
     * 而 UI 上仍然显示「1M 已选中」。
     */
    @Test
    fun allFourWindowTiersFitTheRepositoryClamp() {
        val tiers = listOf(32_000, 128_000, 512_000, 1_000_000)
        val min = SettingsRepository.MIN_MAX_CONTEXT_TOKENS
        val max = SettingsRepository.MAX_MAX_CONTEXT_TOKENS

        assertEquals(4, tiers.size)
        for (tier in tiers) {
            assertTrue(tier in min..max, "档位 $tier 落在 $min..$max 之外")
            assertEquals(tier, tier.coerceIn(min, max), "档位 $tier 会被仓储钳制，UI 显示与生效值不一致")
        }
    }

    /** 编辑页带 id ⇒ 走查询段；新增 ⇒ 不带任何查询段。 */
    @Test
    fun endpointEditRouteBuildsIdIntoQuerySegment() {
        assertEquals("settings/endpoints/edit?id=ep-1", EndpointEditRoute.build("ep-1"))
    }

    /** `null` / 空串 / 纯空白都算「新增」—— 三者都必须落到同一条无参路径。 */
    @Test
    fun endpointEditRouteWithoutIdIsTheCreatePath() {
        assertEquals("settings/endpoints/edit", EndpointEditRoute.build(null))
        assertEquals("settings/endpoints/edit", EndpointEditRoute.build(""))
        assertEquals("settings/endpoints/edit", EndpointEditRoute.build("   "))
    }

    /**
     * 注册用的 [EndpointEditRoute.PATTERN] 与导航用的 `build()` **必须同源**。
     *
     * KDoc 明写：两者不同源会让 `id` 参数静默解析不出来，编辑页永远以「新增」打开。
     * 判据钉住路径前缀 + 占位符名，任何一侧改路径都会红。
     */
    @Test
    fun endpointEditRoutePatternAndBuildShareTheSamePath() {
        assertEquals("settings/endpoints/edit?id={id}", EndpointEditRoute.PATTERN)
        assertTrue(EndpointEditRoute.PATTERN.startsWith("settings/endpoints/edit?"))
        assertTrue(EndpointEditRoute.build("x").startsWith("settings/endpoints/edit?"))
    }

    /** MCP 编辑页与端点编辑页同款契约（同一个坑，两处实现，必须一起钉住）。 */
    @Test
    fun mcpEditRouteMirrorsEndpointEditRouteContract() {
        assertEquals("settings/mcp/edit?id={id}", McpEditRoute.PATTERN)
        assertEquals("settings/mcp/edit", McpEditRoute.build(null))
        assertEquals("settings/mcp/edit", McpEditRoute.build(""))
        assertEquals("settings/mcp/edit?id=srv-1", McpEditRoute.build("srv-1"))
    }

    /**
     * 保存失败错误码是 UI 的**映射键**（`stringResource(settings_save_error_<code>)`）。
     *
     * 改名没有编译错误、没有运行时异常 —— 只是那句错误文案变成空白，而它恰恰是
     * 「用户以为存上了其实没有」时唯一的提示（`UNSAFE_URL` / `SECRET_WRITE_FAILED`）。
     * 所以码值集合按契约钉死：增删改都必须显式改这里。
     */
    @Test
    fun endpointSaveErrorCodesAreStable() {
        assertEquals(
            listOf("UNSAFE_URL", "SECRET_WRITE_FAILED", "SAVE_FAILED"),
            EndpointSaveError.values().map { it.name },
        )
    }
}
