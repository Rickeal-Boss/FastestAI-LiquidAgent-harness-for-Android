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

/** 技能管理子页（W3）。 */
object SkillListRoute {
    const val ROUTE = "settings/skills"
}

/** MCP 服务器列表子页（W3）。 */
object McpListRoute {
    const val ROUTE = "settings/mcp"
}

/** MCP 服务器编辑子页（W3）。`id` 缺省 = 新增。 */
object McpEditRoute {
    const val ARG_ID = "id"
    private const val PATH = "settings/mcp/edit"
    val PATTERN: String = "$PATH?$ARG_ID={$ARG_ID}"

    fun build(id: String?): String = if (id.isNullOrBlank()) PATH else "$PATH?$ARG_ID=$id"
}

/** 长期记忆子页（W3）。 */
object MemoryRoute {
    const val ROUTE = "settings/memory"
}

/**
 * 设置模块的路由图。
 *
 * 由 `:app` 的 NavHost 调用（外壳持有唯一的 NavHostController）。图定义放在 feature
 * 模块里而不是塞进 `:app`：新增设置子页时只动本文件，外壳的导航文件不需要知道
 * 「设置下面有几页」。
 *
 * ## 两个「借来的」入口：[onOpenTools] / [onOpenSandbox]
 *
 * 工具页与沙箱页在 W4 搬去了 `:feature-tools`（G10 行数闸门：本模块触顶 ⇒ 拆分）。
 * 那两个页面的路由常量归 `:feature-tools` 所有 —— feature ↛ feature 是 G6 的硬边，
 * 本模块**不能**引用它的符号。所以入口动作由外壳注入：`:app` 同时知道两边，
 * 把「跳到 tools / sandbox」这件事作为回调传进来，本模块只负责渲染入口行。
 * null = 本构建没有这两个页面（与其它入口同一条纪律：不摆假入口）。
 */
fun NavGraphBuilder.settingsGraph(
    navController: NavHostController,
    container: AppContainer,
    appVersion: String,
    onOpenTools: (() -> Unit)? = null,
    onOpenSandbox: (() -> Unit)? = null,
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
            onManageSkills = { navController.navigate(SkillListRoute.ROUTE) },
            onManageMcp = { navController.navigate(McpListRoute.ROUTE) },
            onManageMemory = { navController.navigate(MemoryRoute.ROUTE) },
            onManageTools = onOpenTools,
            onManageSandbox = onOpenSandbox,
            onSetContextWindow = viewModel::setMaxContextTokens,
        )
    }
    // 子图拆分只为守 G11（单方法 ≤120 行）；路由语义不变，仍挂在同一个 graph 下。
    endpointGraph(navController, container)
    extrasGraph(navController, container)
}

/**
 * 端点子图（列表 + 编辑）。
 *
 * 单独成函数是 G11 的要求（方法 ≤120 行），不是分层：它仍挂在同一个 graph 下。
 */
private fun NavGraphBuilder.endpointGraph(
    navController: NavHostController,
    container: AppContainer,
) {
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

/**
 * 技能 / MCP / 记忆子图（W3 新增的三页）。
 */
private fun NavGraphBuilder.extrasGraph(
    navController: NavHostController,
    container: AppContainer,
) {
    composable(route = SkillListRoute.ROUTE) {
        val viewModel: ExtrasViewModel = viewModel(factory = ExtrasViewModel.Factory(container))
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.loadSkills() }

        SkillListScreen(
            skills = state.skills,
            onToggle = { name, enabled ->
                // 停用是持久化的（SettingsRepository.disabledSkills），不只是 UI 状态。
                viewModel.setSkillEnabled(name, enabled)
            },
            onRefresh = { viewModel.refreshSkills() },
            // 导入能力在 W3 **未接**（SAF 选择 zip → SkillImportValidator → 解压落盘）。
            // 传 null 让 screen 不渲染该按钮 —— 假按钮比缺按钮更糟。
            onImport = null,
            onBack = { navController.popBackStack() },
            loading = state.skillsLoading,
            error = state.error,
        )
    }

    composable(route = McpListRoute.ROUTE) {
        val viewModel: ExtrasViewModel = viewModel(factory = ExtrasViewModel.Factory(container))
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.loadMcp() }

        McpServerListScreen(
            servers = state.mcp,
            onToggle = viewModel::toggleMcp,
            onEdit = { id -> navController.navigate(McpEditRoute.build(id)) },
            onAdd = { navController.navigate(McpEditRoute.build(null)) },
            onRefresh = viewModel::refreshMcp,
            onDelete = viewModel::deleteMcp,
            onBack = { navController.popBackStack() },
            loading = state.mcpLoading,
            error = state.error,
        )
    }

    composable(
        route = McpEditRoute.PATTERN,
        arguments = listOf(
            navArgument(McpEditRoute.ARG_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { entry ->
        val viewModel: ExtrasViewModel = viewModel(factory = ExtrasViewModel.Factory(container))
        val state by viewModel.state.collectAsStateWithLifecycle()
        val serverId = entry.arguments?.getString(McpEditRoute.ARG_ID)
        LaunchedEffect(serverId) { viewModel.loadMcp() }

        val initial = remember(state.mcp, serverId) {
            val row = serverId?.let { id -> state.mcp.firstOrNull { it.id == id } }
            if (row == null) {
                McpDraft()
            } else {
                McpDraft(
                    id = row.id,
                    alias = row.alias,
                    url = row.url,
                    // token 不回填（与端点编辑同一条纪律）。
                    token = "",
                    allowPrivateNetwork = false,
                    enabled = row.enabled,
                )
            }
        }

        McpServerEditScreen(
            initial = initial,
            isNew = serverId == null,
            onSave = { draft -> viewModel.saveMcp(draft) { navController.popBackStack() } },
            onTest = { draft -> viewModel.testMcp(draft) },
            onBack = { navController.popBackStack() },
            testing = state.mcpTesting,
            testResult = state.mcpTestResult,
            error = state.error,
        )
    }

    composable(route = MemoryRoute.ROUTE) {
        val viewModel: ExtrasViewModel = viewModel(factory = ExtrasViewModel.Factory(container))
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.loadMemory() }

        MemoryScreen(
            entries = state.memory,
            onDelete = viewModel::deleteMemory,
            onEdit = viewModel::updateMemory,
            onBack = { navController.popBackStack() },
            loading = state.memoryLoading,
        )
    }
}
