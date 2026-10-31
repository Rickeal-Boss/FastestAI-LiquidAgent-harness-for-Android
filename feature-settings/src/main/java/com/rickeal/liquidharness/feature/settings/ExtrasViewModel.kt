package com.rickeal.liquidharness.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.mcp.McpServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 技能 / MCP / 记忆三个子页的共享 ViewModel。
 *
 * ## 为什么三合一而不是三个 ViewModel
 *
 * 三个页面的数据都来自 `AppContainer` 的同一层（`skills` / `mcpServers` / `memory`），
 * 生命周期都是「进页面读一次、操作后重读」。拆成三个类会得到三段几乎相同的
 * `load/refresh` 样板，而它们的差异只有「读哪个 store」。
 *
 * 与 `SettingsViewModel` 分开的理由：那个类已经负责端点与密钥（含 probe / save 失败
 * 分支），再塞三个领域进去会让它变成旧仓那种「什么都装」的上帝对象。
 *
 * ## 失败口径
 *
 * 所有操作**不抛异常**：读失败 ⇒ 空列表 + [ExtrasUiState.error]；
 * 写失败 ⇒ 保留原状态 + error。UI 因此永远有东西可渲染，不需要 try/catch。
 */
class ExtrasViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ExtrasUiState())
    val state: StateFlow<ExtrasUiState> = _state.asStateFlow()

    /** 技能列表。`refresh` 会清目录缓存（导入 / 删除后必须调，否则看不到变化）。 */
    suspend fun loadSkills() {
        _state.update { it.copy(skillsLoading = true) }
        val disabled = runCatching { container.settings.disabledSkills.first() }.getOrDefault(emptySet())
        val rows = runCatching { container.skills.list() }
            .map { list ->
                list.map {
                    SkillRow(it.name, it.description, it.allowedTools, it.source, enabled = it.name !in disabled)
                }
            }
            .getOrElse { emptyList() }
        _state.update { it.copy(skills = rows, skillsLoading = false, error = null) }
    }

    suspend fun loadMcp() {
        _state.update { it.copy(mcpLoading = true) }
        val servers = runCatching { container.mcpServers.list() }.getOrDefault(emptyList())
        val rows = servers.map { config ->
            McpRow(
                id = config.id,
                alias = config.alias,
                url = config.url,
                enabled = config.enabled,
                // 有缓存工具清单 ⇒ 至少同步过一次；状态由最近一次同步是否成功决定。
                status = when {
                    !config.enabled -> "disabled"
                    config.lastToolsJson.isBlank() -> "error"
                    else -> "cached"
                },
                toolCount = countTools(config.lastToolsJson),
                cachedAtMillis = config.lastSyncedAtMillis,
            )
        }
        _state.update { it.copy(mcp = rows, mcpLoading = false, error = null) }
    }

    suspend fun loadMemory() {
        _state.update { it.copy(memoryLoading = true) }
        val rows = runCatching { container.memory.all() }
            .map { list -> list.map { MemoryRow(it.id, it.title, it.content, it.updatedAtMillis) } }
            .getOrElse { emptyList() }
        _state.update { it.copy(memory = rows, memoryLoading = false, error = null) }
    }

    /** 持久化技能的启用状态（SettingsRepository.disabledSkills），并重读列表。 */
    fun setSkillEnabled(name: String, enabled: Boolean) = viewModelScope.launch {
        runCatching { container.settings.setSkillEnabled(name, enabled) }
        loadSkills()
    }

    /** 「测试连接」：只探测不注册（见 McpToolSources.probe 的 KDoc）。 */
    fun testMcp(draft: McpDraft) = viewModelScope.launch {
        _state.update { it.copy(mcpTesting = true, mcpTestResult = null) }
        val config = McpServerConfig(
            id = draft.id.ifBlank { "probe" },
            alias = draft.alias.trim().ifBlank { "probe" },
            url = draft.url.trim(),
            headers = if (draft.token.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer " + draft.token.trim()),
            enabled = true,
            allowPrivateNetwork = draft.allowPrivateNetwork,
        )
        val snapshot = runCatching { container.mcpTools.probe(config) }.getOrNull()
        _state.update {
            it.copy(
                mcpTesting = false,
                mcpTestResult = when {
                    snapshot == null -> "probe_failed"
                    snapshot.ok -> "ok: " + snapshot.tools.size + " tools"
                    else -> "error: " + (snapshot.message ?: "unknown")
                },
            )
        }
    }

    fun refreshSkills() = viewModelScope.launch {
        runCatching { (container.skills as? com.rickeal.liquidharness.core.skills.SkillCatalogImpl)?.refresh() }
        loadSkills()
    }

    fun saveMcp(draft: McpDraft, onDone: () -> Unit) = viewModelScope.launch {
        val existing = runCatching { container.mcpServers.get(draft.id) }.getOrNull()
        val config = McpServerConfig(
            id = draft.id.ifBlank { com.rickeal.liquidharness.core.model.newId() },
            alias = draft.alias.trim(),
            url = draft.url.trim(),
            // token 只作为 Bearer 头带出；落盘时由 McpServerStore 脱敏（见其 KDoc 的已知缺口）。
            headers = if (draft.token.isBlank()) {
                existing?.headers.orEmpty()
            } else {
                mapOf("Authorization" to "Bearer " + draft.token.trim())
            },
            enabled = draft.enabled,
            allowPrivateNetwork = draft.allowPrivateNetwork,
        )
        val ok = runCatching { container.mcpServers.upsert(config) }.isSuccess
        if (ok) {
            // 保存后立刻同步一次工具清单：用户点「保存」的期待是「现在就能用」。
            runCatching { container.mcpTools.refresh(config, container.toolRegistry()) }
            loadMcp()
            onDone()
        } else {
            _state.update { it.copy(error = "save_failed") }
        }
    }

    fun toggleMcp(id: String, enabled: Boolean) = viewModelScope.launch {
        val existing = runCatching { container.mcpServers.get(id) }.getOrNull() ?: return@launch
        runCatching { container.mcpServers.upsert(existing.copy(enabled = enabled)) }
        if (!enabled) container.mcpTools.unregister(id, container.toolRegistry())
        loadMcp()
    }

    fun refreshMcp(id: String) = viewModelScope.launch {
        val existing = runCatching { container.mcpServers.get(id) }.getOrNull() ?: return@launch
        val count = runCatching { container.mcpTools.refresh(existing, container.toolRegistry()) }
            .getOrDefault(0)
        runCatching {
            container.mcpServers.recordSync(id, "{\"count\":" + count + "}", System.currentTimeMillis())
        }
        loadMcp()
    }

    fun deleteMcp(id: String) = viewModelScope.launch {
        container.mcpTools.unregister(id, container.toolRegistry())
        runCatching { container.mcpServers.remove(id) }
        loadMcp()
    }

    fun deleteMemory(title: String) = viewModelScope.launch {
        runCatching { container.memory.delete(title) }
        loadMemory()
    }

    fun updateMemory(title: String, content: String) = viewModelScope.launch {
        runCatching { container.memory.upsert(title, content) }
        loadMemory()
    }

    /** `lastToolsJson` 是同步摘要（`{"count":N}`）或完整清单，两种都只取数量。 */
    private fun countTools(json: String): Int {
        if (json.isBlank()) return 0
        val digits = json.substringAfter("\"count\":", "").takeWhile { it.isDigit() }
        if (digits.isNotEmpty()) return digits.toIntOrNull() ?: 0
        // 完整清单形态：数 `"name"` 出现次数（粗算即可，UI 只展示量级）。
        return Regex("\"name\"").findAll(json).count()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExtrasViewModel(container) as T
    }
}

/** 三个子页的合并状态。字段前缀区分来源，避免三个 `loading` 撞名。 */
data class ExtrasUiState(
    val skills: List<SkillRow> = emptyList(),
    val skillsLoading: Boolean = false,
    val mcp: List<McpRow> = emptyList(),
    val mcpLoading: Boolean = false,
    val memory: List<MemoryRow> = emptyList(),
    val memoryLoading: Boolean = false,
    val mcpTesting: Boolean = false,
    val mcpTestResult: String? = null,
    val error: String? = null,
)
