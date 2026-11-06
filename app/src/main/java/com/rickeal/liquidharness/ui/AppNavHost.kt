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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
 * 导航宿主。
 *
 * 只注册两个一级目的地（对话 / 设置）+ 设置模块自己的子路由图（[settingsGraph]）。
 * 旧仓的 modelsGraph / toolsGraph / memoryGraph 随功能整块删除 —— **不保留空路由**：
 * 一条指向空屏的路由比没有路由更糟（用户点了会看到白屏，而不是"这个入口不存在"）。
 *
 * @param recordBackdrop 非 null 时把整张 NavHost 录进该 LayerBackdrop，供悬浮页签条
 *   做真实背景模糊 / 折射（各屏自己的壁纸与滚动内容都在其中）。竖栏形态没有悬浮条，
 *   传 null 即不录制 —— 录制是逐帧开销，没人消费就不该挂。
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
    // 会话导入的一次性信箱（W6）。**刻意不放进 AppContainer**：
    // - 它是「一次 UI 动作的载荷」，不是长生命周期依赖 —— 放进容器会让最多 8 MiB 的
    //   解析结果挂在容器上，直到进程结束；
    // - 它只在两个目的地之间传递一次，消费即清空（见 ChatRoute 的 LaunchedEffect）。
    // 提升到 NavHost 之上是必须的：设置页发布时对话页还没组合，值必须活得比它久。
    val pendingImport = remember { mutableStateOf<List<ChatMessage>?>(null) }
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
            // lambda 而非实例：container.toolRegistry() 首次求值会 mkdirs + getSystemService，
            // 必须由 ToolsViewModel 在 IO 段内触发（见其 KDoc）。
            registryProvider = container::toolRegistry,
            // 沙箱根 = AppContainer 的 dataDir（filesDir/harness）下的 workspace/，
            // 与 ToolContext.sandboxDir 同源。本模块有 core-data，故这一句留在这里。
            sandboxRoot = container.jsonFiles.file("workspace"),
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
    // W9-00 P0-3：不再拿 state.endpointLabel 作 effect key —— 编辑当前端点只勾图片时
    // 显示名不变 ⇒ effect 不重跑 ⇒ 返回对话页入口仍禁用。改成由端点快照直接派生：
    // key ≥ endpointId + supportsVision，重返本页 / 活跃端点变化都会重读端点内容。
    val activeId by container.settings.activeEndpointId.collectAsStateWithLifecycle(null)
    val endpointGate = produceState<RemoteEndpoint?>(initialValue = null, activeId, container) {
        value = container.activeEndpoint()
    }
    val visionEnabled = endpointGate.value?.supportsVision == true

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
