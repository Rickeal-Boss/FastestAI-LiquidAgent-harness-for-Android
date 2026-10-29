package com.rickeal.liquidharness.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.design.HarnessTheme
import com.rickeal.liquidharness.feature.chat.ChatScreen
import com.rickeal.liquidharness.feature.chat.ChatViewModel
import com.rickeal.liquidharness.feature.settings.EndpointEditScreen
import com.rickeal.liquidharness.feature.settings.EndpointListScreen
import com.rickeal.liquidharness.feature.settings.SettingsViewModel

private object Routes {
    const val CHAT = "chat"
    const val ENDPOINTS = "endpoints"
    const val ENDPOINT_EDIT = "endpoints/edit"
    const val ARG_ID = "id"
    fun edit(id: String?) = if (id == null) "$ENDPOINT_EDIT?$ARG_ID=" else "$ENDPOINT_EDIT?$ARG_ID=$id"
}

/**
 * 应用导航壳。
 *
 * 只有两个目的地（对话 / 端点管理），刻意不引入抽屉与多 Tab —— W1 的目标是
 * 「填端点 → 对话 → 看到流式」，多一个入口就多一条未验证路径。
 */
@Composable
fun HarnessApp(container: AppContainer) {
    val navController = rememberNavController()

    HarnessTheme {
        NavHost(navController = navController, startDestination = Routes.CHAT) {
            composable(Routes.CHAT) {
                ChatRoute(
                    container = container,
                    onOpenSettings = { navController.navigate(Routes.ENDPOINTS) },
                )
            }
            composable(Routes.ENDPOINTS) {
                SettingsRoute(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Routes.edit(id)) },
                )
            }
            composable(
                route = Routes.edit("{id}"),
                arguments = listOf(
                    navArgument(Routes.ARG_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                EditRoute(
                    container = container,
                    endpointId = entry.arguments?.getString(Routes.ARG_ID),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun ChatRoute(
    container: AppContainer,
    onOpenSettings: () -> Unit,
) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 从设置页返回时端点可能变了（换了活跃端点 / 改了模型名），必须重读，
    // 否则顶栏还显示旧模型，而请求已经用新端点发出 —— 用户会以为「换模型没生效」。
    LaunchedEffect(Unit) { viewModel.refreshEndpoint() }

    ChatScreen(
        state = state,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onOpenSettings = onOpenSettings,
        onDismissError = viewModel::dismissError,
    )
}

@Composable
private fun SettingsRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onEdit: (String?) -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    EndpointListScreen(
        state = state,
        onBack = onBack,
        onAdd = { onEdit(null) },
        onEdit = { id -> onEdit(id) },
        onSetActive = viewModel::setActive,
        onDelete = viewModel::delete,
    )
}

@Composable
private fun EditRoute(
    container: AppContainer,
    endpointId: String?,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(endpointId) { viewModel.refresh() }

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
        hasStoredSecret = hasSecret,
        onBack = onBack,
        onSave = { endpoint, apiKey ->
            viewModel.save(endpoint, apiKey)
            onBack()
        },
        onTest = viewModel::test,
        onDelete = { id ->
            viewModel.delete(id)
            onBack()
        },
    )
}
