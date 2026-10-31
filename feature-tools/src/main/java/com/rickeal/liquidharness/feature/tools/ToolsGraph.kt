package com.rickeal.liquidharness.feature.tools

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import java.io.File

/**
 * 工具清单子页（只读）。
 *
 * route 前缀刻意保持 `settings`：`:app` 的 `routeTop()` 按**最长前缀**把子路由归到
 * 所属页签（见 `AppNavigation.routeTop`），因此 `settings/tools` 天然继承设置的选中态
 * 与滑动方向 —— 它本来就是设置页的一个入口。
 *
 * 路由常量住在本模块而不是 `:feature-settings`：feature ↛ feature 是 G6 的硬边，
 * 「谁拥有页面谁拥有路由」，`:app` 只做两边的粘合（把入口回调传给设置页）。
 */
object ToolsRoute {
    const val ROUTE = "settings/tools"
}

/** 沙箱文件浏览子页。前缀理由同 [ToolsRoute]。 */
object SandboxFilesRoute {
    const val ROUTE = "settings/sandbox"
}

/**
 * 工具 / 沙箱子图。
 *
 * 由 `:app` 的 NavHost 调用（外壳持有唯一的 NavHostController）。
 *
 * ## 两个入参为什么是「lambda + File」而不是 `AppContainer`
 *
 * 本模块**刻意不依赖 `:core-data`**（见 `feature-tools/build.gradle.kts` 的模块边界注释）。
 * 外壳负责把 core-data 的东西翻译成两个最小输入：
 *  - [registryProvider]：延迟求值，因为 `AppContainer.toolRegistry()` 首次调用会
 *    `mkdirs()` + `getSystemService()`（阻塞），必须在 ViewModel 的 IO 段内触发，
 *    不能在组装导航时触发；
 *  - [sandboxRoot]：沙箱根目录（`filesDir/harness/workspace`），本模块只当普通 `File` 用。
 *
 * ## ViewModel 的生命周期
 *
 * 两个 VM 都用 `viewModel(factory = ...)` 挂在**各自目的地**上：离开即销毁，
 * 再进来重新读 —— 沙箱是 Agent 随时在写的目录，缓存一份列表只会显示过期内容。
 */
fun NavGraphBuilder.toolsGraph(
    navController: NavHostController,
    registryProvider: () -> ToolRegistry,
    sandboxRoot: File,
) {
    composable(route = ToolsRoute.ROUTE) {
        val viewModel: ToolsViewModel = viewModel(factory = ToolsViewModel.Factory(registryProvider))
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.load() }

        ToolsScreen(
            tools = state.tools,
            onBack = { navController.popBackStack() },
            loading = state.loading,
            error = state.toolsError,
        )
    }

    composable(route = SandboxFilesRoute.ROUTE) {
        val viewModel: SandboxFilesViewModel = viewModel(
            factory = SandboxFilesViewModel.Factory(sandboxRoot),
        )
        val state by viewModel.state.collectAsStateWithLifecycle()

        SandboxFilesScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onRefresh = viewModel::refresh,
            onUp = viewModel::up,
            onEnter = viewModel::enter,
            onPreview = viewModel::onPreview,
            onDelete = viewModel::delete,
            onDismissPreview = viewModel::dismissPreview,
        )
    }
}
