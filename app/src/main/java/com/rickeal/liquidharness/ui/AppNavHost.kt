package com.rickeal.liquidharness.ui

import android.content.Context
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
import androidx.compose.runtime.getValue
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
import com.rickeal.liquidharness.feature.chat.ChatScreen
import com.rickeal.liquidharness.feature.chat.ChatViewModel
import com.rickeal.liquidharness.feature.settings.SettingsRoute
import com.rickeal.liquidharness.feature.settings.settingsGraph

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
            )
        }
        settingsGraph(
            navController = navController,
            container = container,
            appVersion = appVersion,
        )
    }
}

@Composable
private fun ChatRoute(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
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
        onOpenDrawer = onOpenDrawer,
        onNewConversation = viewModel::newConversation,
    )
}

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
