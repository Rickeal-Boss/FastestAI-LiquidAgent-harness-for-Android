package com.rickeal.liquidharness.feature.settings

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rickeal.liquidharness.core.data.AppContainer

/**
 * 设置一级目的地。
 *
 * route 前缀刻意保持 `settings`：`app` 的 `routeTop()` 按**最长前缀**把子路由归到
 * 所属页签，因此 `settings/endpoints`、`settings/endpoints/edit` 天然继承设置的选中态
 * 与滑动方向，不需要在子路由上再登记一次。
 */
object SettingsRoute {
    const val ROUTE = "settings"
}

/** 端点列表（设置子页）。 */
object EndpointListRoute {
    const val ROUTE = "settings/endpoints"
}

/**
 * 端点编辑 / 新增（设置子页）。
 *
 * 可选参数用**查询段模式**（`?id={id}`）+ `nullable = true`：新增与编辑共用一条路由，
 * `id` 缺省即「新增」。注册时必须用 [PATTERN]（带 `{id}` 占位），导航时用 [build]
 * —— 两者不同源会让 `id` 参数静默解析不出来，编辑页永远以「新增」打开。
 */
object EndpointEditRoute {
    const val ARG_ID = "id"
    private const val PATH = "settings/endpoints/edit"
    val PATTERN: String = "$PATH?$ARG_ID={$ARG_ID}"

    fun build(id: String?): String = if (id.isNullOrBlank()) PATH else "$PATH?$ARG_ID=$id"
}

/**
 * 设置模块的路由图。
 *
 * 由 `:app` 的 NavHost 调用（外壳持有唯一的 NavHostController）。图定义放在 feature
 * 模块里而不是塞进 `:app`：新增设置子页时只动本文件，外壳的导航文件不需要知道
 * 「设置下面有几页」。
 */
fun NavGraphBuilder.settingsGraph(
    navController: NavHostController,
    container: AppContainer,
    appVersion: String,
) {
    composable(route = SettingsRoute.ROUTE) {
        val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
        val state by viewModel.state.collectAsStateWithLifecycle()
        // 从端点页返回时活跃端点可能变了，必须重读，否则「当前：xxx」还显示旧值。
        LaunchedEffect(Unit) { viewModel.refresh() }

        SettingsScreen(
            state = state,
            appVersion = appVersion,
            onManageEndpoints = { navController.navigate(EndpointListRoute.ROUTE) },
        )
    }

    composable(route = EndpointListRoute.ROUTE) {
        val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.refresh() }

        EndpointListScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onAdd = { navController.navigate(EndpointEditRoute.build(null)) },
            onEdit = { id -> navController.navigate(EndpointEditRoute.build(id)) },
            onSetActive = viewModel::setActive,
            onDelete = viewModel::delete,
        )
    }

    composable(
        route = EndpointEditRoute.PATTERN,
        arguments = listOf(
            navArgument(EndpointEditRoute.ARG_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { entry ->
        val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
        val state by viewModel.state.collectAsStateWithLifecycle()
        val endpointId = entry.arguments?.getString(EndpointEditRoute.ARG_ID)
        LaunchedEffect(endpointId) { viewModel.refresh() }
        // 保存成功才退栈：失败必须留在本页把错误码渲染出来（见 SettingsUiState.saveCompleted）。
        LaunchedEffect(state.saveCompleted) {
            if (state.saveCompleted) {
                viewModel.consumeSaveCompleted()
                navController.popBackStack()
            }
        }

        val initial = remember(state.endpoints, endpointId) {
            endpointId?.let { id -> state.endpoints.firstOrNull { it.id == id } }
        }
        val hasSecret = remember(endpointId) {
            endpointId != null && viewModel.secretFor(endpointId)
        }

        EndpointEditScreen(
            initial = initial,
            probing = state.probing,
            probeResult = state.probeResult,
            saveError = state.saveError,
            hasStoredSecret = hasSecret,
            onBack = { navController.popBackStack() },
            onSave = { endpoint, apiKey -> viewModel.save(endpoint, apiKey) },
            onTest = viewModel::test,
            onDelete = { id ->
                viewModel.delete(id)
                navController.popBackStack()
            },
        )
    }
}
