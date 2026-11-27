package com.rickeal.liquidharness.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rickeal.liquidharness.R
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.design.liquid.backdrops.LayerBackdrop
import com.rickeal.liquidharness.core.design.liquid.backdrops.layerBackdrop
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.feature.chat.ChatScreen
import com.rickeal.liquidharness.feature.chat.ChatViewModel
import com.rickeal.liquidharness.feature.settings.SettingsRoute
import com.rickeal.liquidharness.feature.settings.settingsGraph
import com.rickeal.liquidharness.feature.tools.SandboxFilesRoute
import com.rickeal.liquidharness.feature.tools.ToolsRoute
import com.rickeal.liquidharness.feature.tools.toolsGraph

/**
 * 导航宿主：两个一级目的地（对话 / 设置）+ 设置子路由图（[settingsGraph]）。
 * 旧仓空路由整块删除 —— 指向空屏的路由比没有路由更糟（用户看到白屏而非「入口不存在」）。
 * @param recordBackdrop 非 null 时录进该 LayerBackdrop 供悬浮页签做背景模糊；竖栏传 null
 *   （录制是逐帧开销，没人消费就不挂）。
 */
@Composable
internal fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
    reduceMotion: Boolean,
    recordBackdrop: LayerBackdrop?,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appVersion = rememberAppVersion(context)
    // 会话导入一次性信箱（W6）：UI 载荷不放容器（否则 8 MiB 挂到进程结束）；提升到 NavHost 之上。
    val pendingImport = remember { mutableStateOf<List<ChatMessage>?>(null) }
    // W61/R5 恢复卡：读落盘 journal 找未结算 run（内存 buffer 重启即空）；正文不落盘，
    // 「继续」只清标记，不冒充还原（诚实四态）。
    val pendingRecovery = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { pendingRecovery.value = container.runRecovery.findUnsettled() }
    val importEnabled = remember(context) { isDebuggable(context) }

    NavHost(
        navController = navController,
        // 必须与 composable 注册的 route 同源，否则 navigateTop 的 popUpTo 静默失效
        // （见 CHAT_ROUTE 的 KDoc）。
        startDestination = CHAT_ROUTE,
        modifier = modifier
            .fillMaxSize()
            .then(
                if (recordBackdrop != null) Modifier.layerBackdrop(recordBackdrop) else Modifier,
            ),
        // 页签切换 = iOS push/pop 视差；四个方向都显式给值，不依赖 NavHost 版本默认值。
        // reduceMotion 时 tween(0) —— 近瞬时切页，保留层级变化、去掉位移。
        enterTransition = { pushEnter(reduceMotion) },
        exitTransition = { pushExit(reduceMotion) },
        popEnterTransition = { popEnter(reduceMotion) },
        popExitTransition = { popExit(reduceMotion) },
    ) {
        composable(CHAT_ROUTE) {
            ChatRoute(
                container = container,
                onOpenSettings = { navController.navigateTop(SettingsRoute.ROUTE) },
                onOpenDrawer = onOpenDrawer,
                pendingImport = pendingImport,
            )
        }
        settingsGraph(
            navController = navController,
            container = container,
            appVersion = appVersion,
            // 工具页 / 沙箱页在 :feature-tools（W4 因 G10 行数闸门从设置模块拆出）。
            // feature ↛ feature 是 G6 硬边 ⇒ 由外壳把「跳到那一页」作为回调注入设置页。
            onOpenTools = { navController.navigate(ToolsRoute.ROUTE) },
            onOpenSandbox = { navController.navigate(SandboxFilesRoute.ROUTE) },
            // 会话导入（W6）：解析在设置页，落点是对话页的 ChatViewModel ⇒ 外壳转交。
            // 仅 debuggable 安装包注入：这是测试通道，不是普通用户功能。
            onImportSession = if (importEnabled) {
                { messages ->
                    pendingImport.value = messages
                    // 落到对话页本身就是「导入成功」的反馈（题面已经在屏幕上）。
                    navController.navigateTop(CHAT_ROUTE)
                }
            } else {
                null
            },
            // 自动压缩开关同理：只在 debuggable 安装包上出现（设置页按回调是否为 null 决定渲染）。
            debugTools = importEnabled,
        )
        toolsGraph(
            navController = navController,
            registryProvider = container::toolRegistry,
            sandboxRoot = container.jsonFiles.file("workspace"),
        )
    }
    if (pendingRecovery.value != null) {
        AlertDialog(
            onDismissRequest = { pendingRecovery.value = null },
            title = { Text(stringResource(R.string.recovery_title)) },
            text = { Text(stringResource(R.string.recovery_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRecovery.value?.let { container.runRecovery.continueRun(it) }
                    pendingRecovery.value = null
                }) {
                    Text(stringResource(R.string.recovery_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    container.runRecovery.discard()
                    pendingRecovery.value = null
                }) { Text(stringResource(R.string.recovery_discard)) }
            },
        )
    }
}

@Composable
private fun ChatRoute(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
    pendingImport: MutableState<List<ChatMessage>?>,
) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    // W16 后用户追加：对话框 `/` 指令与 `@` 引用以手动触发 skill（下拉列表数据源）。
    val skillNames by viewModel.skillNames.collectAsStateWithLifecycle()

    // 从设置页返回时端点可能变了（换了活跃端点 / 改了模型名），必须重读，
    // 否则顶栏还显示旧模型，而请求已经用新端点发出 —— 用户会以为「换模型没生效」。
    LaunchedEffect(Unit) { viewModel.refreshEndpoint() }

    // 消费会话导入。**先取快照再清空**：`importConversation` 会重置整个 state，
    // 若先清信箱再读，遇到重组时序问题就会导入一份空列表（= 清空当前对话）。
    val imported = pendingImport.value
    LaunchedEffect(imported) {
        if (imported == null) return@LaunchedEffect
        viewModel.importConversation(imported)
        pendingImport.value = null
    }

    // SAF 图片选择器。回调里只把 uri 交给 ViewModel —— 解码/压缩在 core-data 的
    // AttachmentImporter 里（IO 线程），Composable 不碰字节。
    val pickImage = com.rickeal.liquidharness.feature.chat.rememberImagePicker { uri, name ->
        viewModel.attachImage(uri, name)
    }

    // W8：Markdown 渲染开关。**单一真值源 = DataStore**，这里直接 collect 作参数下传，
    // 不进 ChatUiState / ChatViewModel（后者余 13 行且 G23 钉死调用点）。
    val renderMarkdown = container.settings.markdownRender.collectAsStateWithLifecycle(
        com.rickeal.liquidharness.core.data.SettingsRepository.DEFAULT_MARKDOWN_RENDER,
    )
    // W8（NEW-D / 台账 W3-07）：图片入口闸门 —— 端点声明支持图片输入才接回调；
    // 未启用时**保留禁用态入口 + 无障碍原因**（不静默隐藏，D6 正确文本）。
    // W9-01 P1-3：活跃端点的能力位（supportsVision）必须进重读判据。旧实现 key=(activeId, container)
    // 漏掉能力位 ⇒ 编辑当前端点只勾图片（activeId 不变、不导航）时入口仍禁用。
    // 改成直接 collect 活跃端点流（EndpointStore 落盘即更新 ⇒ 能力变化立即重读；
    // AppContainer.activeEndpointFlow 由 activeEndpointId + 端点清单组合而成）。
    val endpointGate by container.activeEndpointFlow.collectAsStateWithLifecycle(null)
    val visionEnabled = endpointGate?.supportsVision == true

    ChatScreen(
        state = state,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onOpenSettings = onOpenSettings,
        onDismissError = viewModel::dismissError,
        // 授权卡的三个键：裁决经 CompletableDeferred 回传给内核（见 resolveApproval）。
        onResolveApproval = viewModel::resolveApproval,
        onOpenDrawer = onOpenDrawer,
        onNewConversation = viewModel::newConversation,
        onPickImage = if (visionEnabled) pickImage else null,
        onRemoveAttachment = viewModel::removeAttachment,
        imageInputLocked = !visionEnabled,
        renderMarkdown = renderMarkdown.value,
        // 手动压缩（W6）：入口 → 二次确认（ChatScreen 里）→ ViewModel 压缩。
        onCompressContext = viewModel::compressContext,
        onUndoCompress = viewModel::undoCompress,
        // W16 后用户追加：对话框 `/` 指令与 `@` 引用以手动触发 skill。
        skillNames = skillNames,
    )
}

/**
 * 本安装包是否 debuggable。
 *
 * 用 `ApplicationInfo.FLAG_DEBUGGABLE` 而不是 `BuildConfig.DEBUG`：`:app` 的
 * `buildFeatures` 没开 `buildConfig`（AGP 8 默认关），而**库模块**的 `BuildConfig.DEBUG`
 * 反映的是库自己的变体、与安装包无关（`rememberAppVersion` 的 KDoc 已就同一件事踩过）。
 * manifest 里的 `debuggable` 标志是唯一与「装到设备上的这个包」同源的事实。
 */
@Suppress("DEPRECATION")
private fun isDebuggable(context: Context): Boolean = runCatching {
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}.getOrDefault(false)

/**
 * 取应用版本名（设置页「关于」行展示用）。
 *
 * 不在 `:feature-settings` 里读 `BuildConfig.VERSION_NAME`：那是**库模块**自己的
 * BuildConfig（且 AGP 默认不生成），显示出来会是一个与安装包无关的值。
 */
@Suppress("DEPRECATION")
@Composable
private fun rememberAppVersion(context: Context): String =
    androidx.compose.runtime.remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
    }

/* ---------------------------------------------------------------- 转场 */

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter(
    reduceMotion: Boolean,
): EnterTransition {
    val dir = slideDirection(initialState.destination.route, targetState.destination.route)
    return slideInHorizontally(
        initialOffsetX = { fullWidth -> (fullWidth * PUSH_ENTER_PARALLAX).toInt() * dir },
        animationSpec = tween(
            if (reduceMotion) 0 else PUSH_SLIDE_MS,
            easing = FastOutSlowInEasing,
        ),
    ) + fadeIn(tween(if (reduceMotion) 0 else PUSH_FADE_IN_MS))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit(
    reduceMotion: Boolean,
): ExitTransition {
    val dir = slideDirection(initialState.destination.route, targetState.destination.route)
    return slideOutHorizontally(
        targetOffsetX = { fullWidth -> (-fullWidth * PUSH_EXIT_PARALLAX).toInt() * dir },
        animationSpec = tween(
            if (reduceMotion) 0 else PUSH_SLIDE_MS,
            easing = FastOutSlowInEasing,
        ),
    ) + fadeOut(tween(if (reduceMotion) 0 else PUSH_FADE_OUT_MS))
}

/** pop 方向取反：返回时旧页从右侧让出、新页从左回来。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(
    reduceMotion: Boolean,
): EnterTransition {
    val dir = -slideDirection(initialState.destination.route, targetState.destination.route)
    return slideInHorizontally(
        initialOffsetX = { fullWidth -> (fullWidth * PUSH_ENTER_PARALLAX).toInt() * dir },
        animationSpec = tween(
            if (reduceMotion) 0 else PUSH_SLIDE_MS,
            easing = FastOutSlowInEasing,
        ),
    ) + fadeIn(tween(if (reduceMotion) 0 else PUSH_FADE_IN_MS))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(
    reduceMotion: Boolean,
): ExitTransition {
    val dir = -slideDirection(initialState.destination.route, targetState.destination.route)
    return slideOutHorizontally(
        targetOffsetX = { fullWidth -> (-fullWidth * PUSH_EXIT_PARALLAX).toInt() * dir },
        animationSpec = tween(
            if (reduceMotion) 0 else PUSH_SLIDE_MS,
            easing = FastOutSlowInEasing,
        ),
    ) + fadeOut(tween(if (reduceMotion) 0 else PUSH_FADE_OUT_MS))
}
