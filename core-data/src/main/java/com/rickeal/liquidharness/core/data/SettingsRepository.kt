package com.rickeal.liquidharness.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 轻量设置。W1 只存两个键：当前活跃端点 id、首启引导是否完成。
 *
 * 刻意**不**用 `SharedPreferences`：它没有「读失败」的表达（`getString` 只能给默认值），
 * 而 DataStore 的 `IOException` 可以显式降级成默认值（见 [safeData]）。
 *
 * 密钥**不在这里** —— 那是 [EndpointSecretStore] 的职责（Keystore 信封）。
 * 这里出现任何密钥形态的字段都算回归。
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    /** 当前活跃端点 id。null = 未选择（UI 走空态引导）。 */
    val activeEndpointId: Flow<String?> = safeData().map { it[KEY_ACTIVE_ENDPOINT] }

    val hasCompletedOnboarding: Flow<Boolean> = safeData().map { it[KEY_ONBOARDED] ?: false }

    /**
     * 被用户**停用**的技能名集合（W3）。
     *
     * 存「停用集合」而不是「启用集合」：新装技能默认可用，用户只需要关掉不想要的。
     * 反过来的话，每次导入新技能都要额外写一次启用记录，而漏写就表现为「装了但用不了」。
     */
    val disabledSkills: Flow<Set<String>> = safeData().map { it[KEY_DISABLED_SKILLS] ?: emptySet() }

    /**
     * 单次请求允许消耗的**输入窗口**上限（token）。W4 新增。
     *
     * ## 为什么必须可配
     *
     * 预算算式是 `min(endpoint.contextLength, maxContextTokens)`。`maxContextTokens` 写死 32 000 时，
     * 一个声明 1M 窗口的端点在设备上最多只能发 ≈24k —— 窗口能力永远行使不到，
     * 而这不是模型限制，是本应用的**钱包上限**。把它做成设置项，才谈得上「1M 窗口可行使」。
     *
     * 默认仍是 32 000（与 W3 基线一致，不改变既有用户的账单行为）。
     * 取值在 [MIN_MAX_CONTEXT_TOKENS]..[MAX_MAX_CONTEXT_TOKENS] 之间钳制：
     * 上限 1 000 000 是「别让一个手滑的输入产生四位数账单」的硬闸，不是模型能力上限。
     */
    val maxContextTokens: Flow<Int> = safeData().map { prefs ->
        (prefs[KEY_MAX_CONTEXT_TOKENS] ?: DEFAULT_MAX_CONTEXT_TOKENS)
            .coerceIn(MIN_MAX_CONTEXT_TOKENS, MAX_MAX_CONTEXT_TOKENS)
    }

    suspend fun setMaxContextTokens(tokens: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_MAX_CONTEXT_TOKENS] = tokens.coerceIn(MIN_MAX_CONTEXT_TOKENS, MAX_MAX_CONTEXT_TOKENS)
        }
    }

    suspend fun setSkillEnabled(name: String, enabled: Boolean) {
        if (name.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_DISABLED_SKILLS].orEmpty()
            prefs[KEY_DISABLED_SKILLS] = if (enabled) current - name else current + name
        }
    }

    suspend fun setActiveEndpointId(id: String?) {
        dataStore.edit { prefs ->
            if (id.isNullOrBlank()) prefs.remove(KEY_ACTIVE_ENDPOINT) else prefs[KEY_ACTIVE_ENDPOINT] = id
        }
    }

    suspend fun setHasCompletedOnboarding(done: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDED] = done }
    }

    /**
     * 读盘失败（文件损坏 / 磁盘 IO 错）降级成空 Preferences。
     *
     * `catch` 里必须先把 [CancellationException] 放行：它是协程取消的正常路径，
     * 吞掉会让 Job 从 cancelled 变 completed（`AGENTS.md` §五.4 的老坑）。
     */
    private fun safeData(): Flow<Preferences> = dataStore.data.catch { t ->
        if (t is CancellationException) throw t
        if (t is IOException) emit(emptyPreferences()) else throw t
    }

    companion object {
        private val KEY_ACTIVE_ENDPOINT = stringPreferencesKey("active_endpoint_id")
        private val KEY_ONBOARDED = booleanPreferencesKey("has_completed_onboarding")
        private val KEY_DISABLED_SKILLS = stringSetPreferencesKey("disabled_skills")
        private val KEY_MAX_CONTEXT_TOKENS = intPreferencesKey("max_context_tokens")

        /** 与 `AgentPolicy.maxContextTokens` 的默认值同源（32 000），改一处必须改两处并加断言。 */
        const val DEFAULT_MAX_CONTEXT_TOKENS: Int = 32_000

        const val MIN_MAX_CONTEXT_TOKENS: Int = 8_000

        /** 硬闸：单请求输入窗口不允许超过 1M（模型能力上限，也是账单失控的护栏）。 */
        const val MAX_MAX_CONTEXT_TOKENS: Int = 1_000_000

        /**
         * 生产装配入口。文件落在 `filesDir/datastore/settings.preferences_pb`
         * （`preferencesDataStore` 的默认位置），与端点 / 日志文件分属不同子目录。
         */
        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.applicationContext.settingsDataStore)
    }
}

/** 顶层委托：一个进程只允许一个同名的 DataStore 实例，重复创建会抛异常。 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)
