package com.rickeal.liquidharness.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.data.EndpointSaveError
import com.rickeal.liquidharness.core.data.EndpointSaveException
import com.rickeal.liquidharness.core.data.ProbeOutcome
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

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
    /**
     * 最近一次保存的失败原因。**错误码不是文案** —— UI 用
     * `stringResource(settings_save_error_<code>)` 这类映射上屏（守卫 G16：main 源集不许中文字面量）。
     * null = 没有错误 / 已被下一次保存清掉。
     */
    val saveError: EndpointSaveError? = null,
    /**
     * 一次保存**成功**的完成信号。路由用它决定是否退栈。
     *
     * 为什么不让 `onSave` 直接 `popBackStack()`：保存是异步的，先退栈再失败
     * 就等于「页面关了、错误没人看见」—— 而 `UNSAFE_URL` / `SECRET_WRITE_FAILED`
     * 恰恰是「用户以为存上了其实没有」的高危静默态（外审 P0-2）。所以退栈必须
     * 由**保存结果**驱动，不能由点击驱动。
     */
    val saveCompleted: Boolean = false,
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

    /**
     * 保存端点。
     *
     * 失败**必须上屏**：URL 被安全策略拒绝、密钥落盘失败都是「用户以为存上了但其实没有」
     * 的高危静默态。这里只把 [EndpointSaveError] 错误码放进状态，英文异常串只进日志
     * （它含 host 等诊断信息，直接上屏既不本地化也会泄漏内部细节）。
     */
    fun save(endpoint: RemoteEndpoint, apiKey: String?) {
        viewModelScope.launch {
            _state.update { it.copy(saveError = null, saveCompleted = false) }
            try {
                container.saveEndpoint(endpoint, apiKey)
                load()
                // 只有走到这里才算「真的存上了」：saveEndpoint 内部先把 URL 过
                // UrlGuard、再写密钥、最后写清单，任一步失败都抛异常。
                _state.update { it.copy(saveCompleted = true) }
            } catch (e: EndpointSaveException) {
                _state.update { it.copy(saveError = e.error) }
            } catch (e: IOException) {
                _state.update { it.copy(saveError = EndpointSaveError.SAVE_FAILED) }
            }
        }
    }

    /** 路由退栈后清掉完成信号，避免下次进入编辑页立刻被「已完成」弹回去。 */
    fun consumeSaveCompleted() {
        _state.update { it.copy(saveCompleted = false) }
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

    /**
     * 「这个端点存过密钥吗」—— 只判**文件存在**，不解密。
     *
     * 列表页/输入框占位只需要这个弱判据；解密要过 Keystore，冷启动可能几百毫秒
     * （R11），不该出现在渲染路径上。真正的「能不能用」判据是
     * `EndpointSecretStore.hasUsable`，由 `AppContainer.saveEndpoint` 在 IO 线程行使，
     * 结果落进 `RemoteEndpoint.hasSecret`。
     */
    fun secretFor(id: String): Boolean = container.secrets.hasFile(id)

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
