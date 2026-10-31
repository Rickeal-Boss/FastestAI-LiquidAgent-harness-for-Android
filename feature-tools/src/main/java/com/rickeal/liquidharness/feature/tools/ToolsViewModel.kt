package com.rickeal.liquidharness.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 读注册表失败的原因码（文案由 strings.xml 映射，本模块禁中文字面量）。 */
private const val TOOLS_UNAVAILABLE = "tools_registry_unavailable"

/** 工具页状态。[toolsError] 非 null 时界面显示错误态，而不是「0 个工具」。 */
data class ToolsUiState(
    val tools: List<ToolRow> = emptyList(),
    val loading: Boolean = false,
    val toolsError: String? = null,
)

/**
 * 工具清单的 ViewModel。
 *
 * ## 为什么吃 `registryProvider` 而不是 `AppContainer`
 *
 * 本模块**刻意不依赖 `:core-data`**（见 `feature-tools/build.gradle.kts` 的模块边界注释）：
 * 工具页只需要一个 `ToolRegistry`，而注册表由 `:app` 装配好、以 lambda 形式传进来。
 * 好处是这条边永远只有一条（app → core-data），不会在新模块里长出第二份 DI 认知。
 *
 * 传 lambda 而不是实例：`AppContainer.toolRegistry()` 首次求值会 `mkdirs()` +
 * `getSystemService()`（阻塞），必须由本类在 IO 段内触发，不能在 app 组装导航时触发。
 */
class ToolsViewModel(private val registryProvider: () -> ToolRegistry) : ViewModel() {

    private val _state = MutableStateFlow(ToolsUiState())
    val state: StateFlow<ToolsUiState> = _state.asStateFlow()

    /**
     * 读一次注册表快照。
     *
     * 失败降级成 `toolsError` 原因码 + 空清单：读不出来不该让整页崩掉，
     * 但也不能显示成「0 个工具」（那会被读成「一个都没注册」）。
     */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, toolsError = null) }
            val rows = try {
                withContext(Dispatchers.IO) { toolRows(registryProvider()) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                null
            }
            _state.update {
                if (rows == null) {
                    it.copy(tools = emptyList(), loading = false, toolsError = TOOLS_UNAVAILABLE)
                } else {
                    it.copy(tools = rows, loading = false, toolsError = null)
                }
            }
        }
    }

    class Factory(private val registryProvider: () -> ToolRegistry) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ToolsViewModel(registryProvider) as T
    }
}
