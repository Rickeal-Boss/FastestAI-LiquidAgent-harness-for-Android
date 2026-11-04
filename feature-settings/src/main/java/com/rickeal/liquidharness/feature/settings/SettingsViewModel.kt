package com.rickeal.liquidharness.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rickeal.liquidharness.core.agent.context.SwitchState
import com.rickeal.liquidharness.core.agent.context.evaluateSwitch
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.data.EndpointSaveError
import com.rickeal.liquidharness.core.data.EndpointSaveException
import com.rickeal.liquidharness.core.data.ProbeOutcome
import com.rickeal.liquidharness.core.data.SettingsRepository
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
    /** 单次请求窗口上限（token）。见 `SettingsRepository.maxContextTokens`。 */
    val maxContextTokens: Int = SettingsRepository.DEFAULT_MAX_CONTEXT_TOKENS,
    /**
     * 是否允许内核自动压缩上下文（W6 调试项）。见 `SettingsRepository.compressContext`。
     *
     * 只在 debug 变体的设置页可见（可见性由宿主注入的回调是否为 null 决定，
     * 与其它调试入口同一条纪律）。
     */
    val compressContext: Boolean = SettingsRepository.DEFAULT_COMPRESS_CONTEXT,
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
    /**
     * W7 切换守卫：BLOCKED（当前会话装不进目标生效窗口）拦下时上屏的原因卡。
     * null = 无。消费方调 [SettingsViewModel.consumeSwitchNotice]，由 `SwitchDialogs` 渲染。
     */
    val switchNotice: SwitchNotice? = null,
    /**
     * W7 切换守卫：放行（含 VACUOUS）后的**二次确认待办**。确认（[SettingsViewModel.confirmSwitch]）
     * 才真正写盘；dismiss 丢弃。刻意不含 `SAVE_ENDPOINT` —— 明文 key 不进 state
     * （类头纪律），编辑页保存只做 BLOCKED 硬拦（见 [SettingsViewModel.save]）。
     */
    val pendingSwitch: PendingSwitch? = null,
    /**
     * W7 切换守卫：按当前会话字符数预计算的「装不下」端点 id 集合，列表页据此置灰。
     * `load()` 时用 [blockedEndpointIds] 现算，不持久化。
     */
    val blockedIds: Set<String> = emptySet(),
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
            // W7 切换守卫：仅当这次保存会改变活跃端点（编辑当前端点 / 首存自动激活，
            // `AppContainer.saveEndpoint` 对 activeId==null 会 setActive）时才判容量；
            // 编辑非当前端点不改变本会话目标 ⇒ 不过守卫（否则长会话连「新增端点」都被挡）。
            // CONFIRM 不走 pendingSwitch（明文 key 不进 state，类头纪律）：BLOCKED 硬拦、
            // 放行直存；「编辑当前端点的放行二次确认框」因此裁掉，登记见 W7 波 handoff。
            val activeIdNow = container.settings.activeEndpointId.first()
            if (endpoint.id == activeIdNow || activeIdNow == null) {
                val committed = container.sessionFootprint.current()
                val verdict = evaluateSwitch(committed, endpoint, _state.value.maxContextTokens, 0f)
                if (verdict.state == SwitchState.BLOCKED) {
                    _state.update {
                        it.copy(
                            switchNotice = noticeOf(
                                verdict, SwitchKind.SAVE_ENDPOINT, endpoint.id, committed, true,
                            ),
                        )
                    }
                    return@launch
                }
            }
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
        val target = _state.value.endpoints.firstOrNull { it.id == id }
        if (target == null) {
            // 目标端点不在清单里（竞态：刚被删）。set 一个悬空 id 会让下一次 run 建不出
            // runner，保守拦下并刷新列表。
            refresh()
            return
        }
        // W7：换活跃端点 = 换模型，三形态里最直接的一条。BLOCKED ⇒ 原因卡、不写盘；
        // 其余（含 VACUOUS）⇒ 二次确认后才写（前缀缓存会从头重建，代价必须可见）。
        guardSwitch(SwitchKind.SET_ACTIVE, target, _state.value.maxContextTokens, id)
    }

    /** 二次确认通过，真正写盘。密钥类动作（SAVE_ENDPOINT）不走这条（见 [save]）。 */
    fun confirmSwitch() {
        val pending = _state.value.pendingSwitch ?: return
        _state.update { it.copy(pendingSwitch = null) }
        viewModelScope.launch {
            when (pending.kind) {
                SwitchKind.SET_ACTIVE -> {
                    container.settings.setActiveEndpointId(pending.endpointId)
                    load()
                }
                SwitchKind.SET_WINDOW -> {
                    val tokens = pending.tokens ?: return@launch
                    container.settings.setMaxContextTokens(tokens)
                    _state.update { it.copy(maxContextTokens = tokens) }
                }
                SwitchKind.SAVE_ENDPOINT -> Unit
            }
        }
    }

    fun dismissSwitch() {
        _state.update { it.copy(pendingSwitch = null) }
    }

    fun consumeSwitchNotice() {
        _state.update { it.copy(switchNotice = null) }
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
        val window = container.settings.maxContextTokens.first()
        val compress = container.settings.compressContext.first()
        _state.update {
            it.copy(
                endpoints = list,
                activeId = active,
                maxContextTokens = window,
                compressContext = compress,
                blockedIds = blockedEndpointIds(list, container.sessionFootprint.current(), window),
                loaded = true,
            )
        }
    }

    /**
     * 切换单次请求的窗口上限。
     *
     * 生效时机是**下一次建 runner 时**（换端点或重启），不是立即 ——
     * 这条限制写在 `AppContainer.agentRunnerFor` 的注释里，UI 侧不重复解释，
     * 但改动后立刻回写 state，让用户看到选择已被记住。
     */
    fun setMaxContextTokens(tokens: Int) {
        val activeId = _state.value.activeId
        val target = activeId?.let { id -> _state.value.endpoints.firstOrNull { it.id == id } }
        if (target == null) {
            // 无活跃端点：档位只是本应用的钱包上限，不存在「当前会话装不装得下」语义，直接写。
            viewModelScope.launch {
                container.settings.setMaxContextTokens(tokens)
                _state.update { it.copy(maxContextTokens = tokens) }
            }
            return
        }
        // W7：调档位 = 改当前端点的生效硬预算。降档可能让现有会话装不下 ⇒ 过守卫。
        guardSwitch(SwitchKind.SET_WINDOW, target, tokens, activeId)
    }

    /**
     * 开关自动压缩（W6 调试项）。
     *
     * 与 [setMaxContextTokens] 同一条纪律：写盘后立刻回写 state（否则开关会「弹回去」，
     * 用户以为没生效）。真正生效点在下一次建 runner 时 —— `AppContainer.agentRunnerFor`
     * 用 `AgentPolicy(compressContext = ...)` 读它（W6 已接线，台账 W6-01 CLOSED）。
     */
    fun setCompressContext(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setCompressContext(enabled)
            _state.update { it.copy(compressContext = enabled) }
        }
    }

    /**
     * W7 三处写入点共用的判定 + 分流。`calibratedCharsPerToken = 0f` 是**刻意的**：
     * 设置页没有在途 run，无会话级标定真值 ⇒ floor 1.8 保守档（reason 带
     * `char_floor_unmeasured`），不许伪造真值。BLOCKED ⇒ 原因卡、不写盘；
     * 其余（含 VACUOUS）⇒ 二次确认待办、不硬绿。
     */
    private fun guardSwitch(
        kind: SwitchKind,
        target: RemoteEndpoint,
        maxContextTokens: Int,
        targetId: String?,
    ) {
        val committed = container.sessionFootprint.current()
        val verdict = evaluateSwitch(committed, target, maxContextTokens, 0f)
        when (decideSwitch(verdict).action) {
            SwitchAction.BLOCK -> _state.update {
                it.copy(switchNotice = noticeOf(verdict, kind, targetId, committed, true))
            }
            SwitchAction.CONFIRM -> _state.update {
                it.copy(
                    pendingSwitch = PendingSwitch(
                        kind = kind,
                        endpointId = targetId,
                        tokens = if (kind == SwitchKind.SET_WINDOW) maxContextTokens else null,
                        vacuous = verdict.state == SwitchState.VACUOUS,
                        reason = verdict.reason,
                    ),
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(container) as T
    }
}
