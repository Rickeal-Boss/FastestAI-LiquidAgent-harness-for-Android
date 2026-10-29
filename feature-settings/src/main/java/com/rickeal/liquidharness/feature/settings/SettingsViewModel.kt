package com.rickeal.liquidharness.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.data.ProbeOutcome
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 设置页状态。
 *
 * [probeResult] 单独一块而不是塞进每个端点：测试连接是**一次性动作**，
 * 结果只对「刚测的那个」有意义。挂在列表项上会让用户改完 A 再去测 B 时看到 A 的旧结论。
 */
data class SettingsUiState(
    val endpoints: List<RemoteEndpoint> = emptyList(),
    val activeId: String? = null,
    val probing: Boolean = false,
    val probeTarget: String? = null,
    val probeResult: ProbeOutcome? = null,
    val loaded: Boolean = false,
)

/**
 * 端点管理 ViewModel。
 *
 * 密钥的读写全部走 [AppContainer.saveEndpoint]（它内部同时写 `EndpointSecretStore`）——
 * 本类**永远不持有明文 key 超过一次调用**，也不把它放进 [SettingsUiState]
 * （状态会被 Compose 快照系统持有，且可能进 UI 测试报告）。
 */
class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun save(endpoint: RemoteEndpoint, apiKey: String?) {
        viewModelScope.launch {
            container.saveEndpoint(endpoint, apiKey)
            load()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            container.deleteEndpoint(id)
            _state.update { it.copy(probeResult = null, probeTarget = null) }
            load()
        }
    }

    fun setActive(id: String) {
        viewModelScope.launch {
            container.settings.setActiveEndpointId(id)
            load()
        }
    }

    /**
     * 测试连接。
     *
     * @param apiKey 用户刚在输入框里填的值；null = 用已存的密钥（编辑已有端点时）。
     *   这条语义很重要：编辑页不该强制用户重新输入 key 才能点测试。
     */
    fun test(endpoint: RemoteEndpoint, apiKey: String?) {
        viewModelScope.launch {
            _state.update { it.copy(probing = true, probeTarget = endpoint.id, probeResult = null) }
            val key = apiKey ?: container.secrets.read(endpoint.id)
            val outcome = container.probe.test(endpoint, key)
            _state.update { it.copy(probing = false, probeResult = outcome) }
        }
    }

    fun clearProbe() {
        _state.update { it.copy(probeResult = null, probeTarget = null) }
    }

    fun secretFor(id: String): Boolean = container.secrets.has(id)

    private suspend fun load() {
        val list = container.endpoints.list()
        val active = container.settings.activeEndpointId.first()
        _state.update { it.copy(endpoints = list, activeId = active, loaded = true) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(container) as T
    }
}
