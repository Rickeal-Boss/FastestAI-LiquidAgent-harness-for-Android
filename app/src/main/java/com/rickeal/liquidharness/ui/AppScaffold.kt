package com.rickeal.liquidharness.ui

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.design.GlassWallpaper
import com.rickeal.liquidharness.core.design.LocalBottomBarOverlay
import com.rickeal.liquidharness.core.design.LocalGlassConfig
import com.rickeal.liquidharness.core.design.LocalOverlayBlurState
import com.rickeal.liquidharness.core.design.LocalTopBarOverlay
import com.rickeal.liquidharness.core.design.TabBarPlacement
import com.rickeal.liquidharness.core.design.WindowSizeClass
import com.rickeal.liquidharness.core.design.overlayBackdropBlur
import com.rickeal.liquidharness.core.design.rememberOverlayBlurProgress
import com.rickeal.liquidharness.core.design.rememberWindowSizeClass
import com.rickeal.liquidharness.core.design.tabBarPlacement
import com.rickeal.liquidharness.core.design.liquid.Backdrop
import com.rickeal.liquidharness.core.design.liquid.LocalBackdrop
import com.rickeal.liquidharness.core.design.liquid.backdrops.LayerBackdrop
import com.rickeal.liquidharness.core.design.liquid.backdrops.rememberLayerBackdrop
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "AppScaffold"

/**
 * 抽屉宽度。与 M3 `ModalDrawerSheet` 的默认最大宽度（360dp）同量级，取 300dp 留出内容余量。
 *
 * ⚠️ **不要"顺手优化"成 `fillMaxWidth()`**：`ModalNavigationDrawer` 的抽屉槽位外层是
 * `fillMaxSize` 的 Box，内容自己撑满宽度会让抽屉**占满整屏**（遮罩退化成一层色，
 * 看不出"抽屉浮在内容之上"）。
 *
 * `internal` 而非 `private`：本文件用它把抽屉的锚点位移换算成「背景模糊强度」进度
 * （Closed 锚 = −宽度、Open 锚 = 0 ⇒ progress = 1 + offset/宽度）。**两处必须同源** ——
 * 宽度一改，模糊进度会立刻错位（糊满一半就不再加深）。
 */
internal val DRAWER_WIDTH = 300.dp

/**
 * 主界面外壳：自适应一级导航 + NavHost + 抽屉 + 覆盖层背景模糊。
 *
 * 自适应策略（W89）：
 *  - [TabBarPlacement.Bottom]（竖屏手机）底部悬浮玻璃条，内容从条底下穿过；
 *  - [TabBarPlacement.Rail]（平板 / 折叠屏展开 / 桌面）左侧常驻竖栏；
 *  - [TabBarPlacement.Top]（横屏手机）**顶部横向条**，正文横向吃满。
 *
 * 三档判定收在纯函数 `WindowSizeClass.tabBarPlacement()` 里（JVM 可测），调用点
 * **不再**用 `useTwoPane` 兜底 —— 那正是横屏手机被当成平板的根因。
 *
 * 旧仓这里是 1342 行的"什么都塞进 MainShell"；本文件只负责布局骨架与返回键策略，
 * 导航注册在 [AppNavHost]、页签形态在 [GlassNavBar] / [GlassNavRail]、
 * 纯判据在 [AppNavigation]。
 */
@Composable
internal fun AppScaffold(container: AppContainer) {
    val windowSize = rememberWindowSizeClass()
    val tabPlacement = windowSize.tabBarPlacement()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selected = routeTop(backStackEntry?.destination?.route) ?: TopDestination.CHAT
    // 用 LocalActivity 而不是 `LocalContext.current as? Activity`：后者一旦解析失败，
    // `finish()` 就变成静默什么都不做 —— 用户按返回没反应，且没有任何报错。
    val activity: Activity? = LocalActivity.current

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    val glassCfg = LocalGlassConfig.current
    // 覆盖层背景深度模糊：关掉「背景模糊」总闸时整体不挂 —— 它与玻璃背景模糊是
    // 同一类开销（全屏离屏录制 + 高斯模糊）。
    val overlayBlurEnabled = glassCfg.enableBackdropBlur
    val overlayBlurState = LocalOverlayBlurState.current
    val drawerWidthPx = with(LocalDensity.current) { DRAWER_WIDTH.toPx() }
    // 抽屉的模糊强度**直接由它的锚点位移换算**，因此拖拽中 / 开合动画中逐帧同步、
    // 完全跟手。Closed 锚点 = −抽屉宽度、Open 锚点 = 0（M3 ModalNavigationDrawer 的
    // updateAnchors），故 progress = 1 + offset/width。
    // ⚠️ 锚点未初始化时 currentOffset 是 NaN（M3 的语义是返回 offset，不抛异常）——
    //    必须显式兜 0：兜 NaN 会污染 max()，兜 1 更糟（一进应用背景就是糊的）。
    val drawerBlurProgress: () -> Float = {
        val offset = drawerState.currentOffset
        if (offset.isNaN()) 0f else (1f + offset / drawerWidthPx).coerceIn(0f, 1f)
    }
    val shellBlurProgress = rememberOverlayBlurProgress(
        active = { overlayBlurState.shellActive },
        enabled = overlayBlurEnabled,
    )

    // 页签玻璃的**动态背景源**（旧仓 2026-09-26 根因修复，原样搬入）：
    // 壁纸由各 feature 屏自己的 GlassScaffold 绘制并各发一份 LocalBackdrop，而页签条是
    // NavHost 的**兄弟悬浮层** —— 它子树里的 LocalBackdrop 一直是默认 EmptyBackdrop，
    // 页签玻璃采样"空"就什么都看不出来。把 NavHost 整体录进一张 LayerBackdrop
    // （各屏壁纸 + 页面内容都在其中），页签玻璃的背景源换成它，滚动内容从页签底下
    // 穿过时磨砂与折射实时跟随。
    // 录制挂在 enableBackdropBlur（性能总闸）与「有悬浮页签条」两个条件下 ——
    // 关掉背景模糊时连录制一起停；竖栏形态没有悬浮条，录了也没人消费。
    val recordContent = glassCfg.enableBackdropBlur && tabPlacement != TabBarPlacement.Rail
    val contentBackdrop = rememberLayerBackdrop()
    val navBarBackdrop = if (recordContent) contentBackdrop else LocalBackdrop.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 抽屉边缘手势只在「对话」页、且非 Rail 形态时触发：宽屏本来就没有抽屉入口
        // （竖栏就是入口），若仍允许边缘手势会在竖栏旁"擦"出一块抽屉，属无谓回归。
        // 横屏手机（Top 形态）必须保留抽屉 —— 它与平板同宽但不是平板。
        gesturesEnabled = tabPlacement != TabBarPlacement.Rail &&
            selected == TopDestination.CHAT,
        drawerContent = {
            // 宽屏也照常 compose（只是永远关着、且禁用侧滑）：ModalNavigationDrawer
            // 内部要 measure 它，给空内容会在取首个子项时炸。
            ConversationDrawer(
                onNewConversation = { drawerScope.launch { drawerState.close() } },
                onOpenCurrentConversation = { drawerScope.launch { drawerState.close() } },
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 兜底壁纸：feature 屏自己的 GlassScaffold 会再铺一层（同色，覆盖即可）。
            // 外壳层必须先有底，否则 NavHost 转场的那一帧会透出纯黑。
            GlassWallpaper(modifier = Modifier.fillMaxSize())
            ShellContent(
                navController = navController,
                container = container,
                windowSize = windowSize,
                tabPlacement = tabPlacement,
                selected = selected,
                reduceMotion = glassCfg.reduceMotion,
                recordBackdrop = if (recordContent) contentBackdrop else null,
                navBarBackdrop = navBarBackdrop,
                // 抽屉入口由外壳持有（drawerState/drawerScope 在这里），ShellContent 只转发。
                onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                modifier = Modifier
                    .fillMaxSize()
                    .overlayBackdropBlur(
                        // 抽屉跟手、对话框走弹簧：两者同时只可能是「抽屉开着时又弹了个对话框」。
                        // 进度只在 draw 阶段读 ⇒ 逐帧变化只失效绘制，不会重组外壳。
                        progress = { max(drawerBlurProgress(), shellBlurProgress()) },
                        radius = glassCfg.overlayBlurRadius.dp,
                        enabled = overlayBlurEnabled,
                    ),
            )
        }
    }

    DrawerBackHandler(
        drawerState = drawerState,
        drawerScope = drawerScope,
        onTwoStageBack = { twoStageBack(navController, activity) },
    )
}

/**
 * 外壳正文：竖栏（Rail 形态）+ NavHost + 悬浮页签条。
 *
 * 拆出来只为守住「单方法 ≤120 行」—— 它没有任何独立状态，参数全部由 [AppScaffold] 传入。
 * 页签条与 NavHost 是**同一层 Box 的兄弟**（不是独立布局槽）：内容与壁纸从玻璃页签
 * 底下穿过，refraction 实时跟随（2026-09-26「Tab 悬浮叠层」需求）。
 */
@Composable
private fun ShellContent(
    navController: NavHostController,
    container: AppContainer,
    windowSize: WindowSizeClass,
    tabPlacement: TabBarPlacement,
    selected: TopDestination,
    reduceMotion: Boolean,
    recordBackdrop: LayerBackdrop?,
    navBarBackdrop: Backdrop,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        if (tabPlacement == TabBarPlacement.Rail) {
            GlassNavRail(
                selected = selected,
                onSelect = { destination ->
                    if (destination != selected) navController.navigateTop(destination.route)
                },
                windowSize = windowSize,
                modifier = Modifier.fillMaxHeight(),
            )
        }
        // 悬浮页签占位：底部 84dp = 条高 64 + 上下内边距 10×2；顶部 72dp = 64 + 4×2。
        // 两处数值与 [GlassNavBar] 的 verticalPadding **手工同源**，改任一侧必须同步另一侧。
        CompositionLocalProvider(
            LocalBottomBarOverlay provides
                if (tabPlacement == TabBarPlacement.Bottom) 84.dp else 0.dp,
            LocalTopBarOverlay provides
                if (tabPlacement == TabBarPlacement.Top) 72.dp else 0.dp,
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AppNavHost(
                    navController = navController,
                    container = container,
                    reduceMotion = reduceMotion,
                    recordBackdrop = recordBackdrop,
                    // 抽屉入口：顶栏左侧菜单键。手势（边缘滑）也在，但只有手势
                    // 等于「隐藏功能」—— 旧仓用户是靠那枚键发现抽屉的。
                    onOpenDrawer = onOpenDrawer,
                )
                // 页签条：底部（竖屏）或**顶部**（横屏手机）。同一枚组件、同一形态，
                // 只有对齐与 inset 方向不同 —— 不做第二套排版。
                if (tabPlacement != TabBarPlacement.Rail) {
                    CompositionLocalProvider(LocalBackdrop provides navBarBackdrop) {
                        GlassNavBar(
                            selected = selected,
                            onSelect = { destination ->
                                if (destination != selected) {
                                    navController.navigateTop(destination.route)
                                }
                            },
                            modifier = when (tabPlacement) {
                                // 横屏：状态栏仍在屏幕上沿，必须 statusBarsPadding，
                                // 否则条会被时钟 / 信号图标压住。
                                TabBarPlacement.Top -> Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                else -> Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            },
                            verticalPadding = if (tabPlacement == TabBarPlacement.Top) {
                                4.dp
                            } else {
                                10.dp
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 抽屉的返回键（原样搬自旧仓 C-3）。
 *
 * ⚠️ **刻意单独抽成一个小 composable**：`drawerState.isOpen` 派生自锚点偏移状态，
 * 拖拽 / 开合动画期间**每帧都会变**。直接在 [AppScaffold] 里读它，失效范围就是整个
 * 外壳 —— 连带 NavHost 与整屏每帧重组。抽出来之后每帧失效的只有本函数，
 * 而它不产出任何布局节点（只注册两个回调）。
 *
 * 两个 `BackHandler` 的 `enabled` 条件相反，任一时刻只有一个生效，因此与注册顺序无关。
 * 之所以要自己接「关抽屉」，是因为 Material3 的 `ModalNavigationDrawer` **不会**处理
 * 返回键 —— 只把两段式那个改成 `enabled = !isOpen` 会让抽屉打开时的返回键落空。
 *
 * 注册位置必须在 `NavHost` **之后**（LIFO，后注册先派发）—— 所以本函数在
 * `ModalNavigationDrawer` 之后调用。
 */
@Composable
private fun DrawerBackHandler(
    drawerState: DrawerState,
    drawerScope: CoroutineScope,
    onTwoStageBack: () -> Unit,
) {
    BackHandler(enabled = drawerState.isOpen) {
        drawerScope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen, onBack = onTwoStageBack)
}

/**
 * 系统返回键：两段式（先回对话页，再一次退出应用）。
 *
 * 修好 `popUpTo` 只解决「栈无限增长」；从「设置 → 端点编辑」返回仍会是逐级 pop
 * （子页 → 设置 → 对话 → 退出），不是两段式。所以这里再加一层显式策略。
 *
 * 兜底收敛用**固定次数上限**而不是 `while (true)`：收敛性不该押在
 * 「popBackStack 是否同步移除 backQueue」这类 Navigation 内部实现上
 * （2.8 起走 popWithTransition，条目在过渡中时 pop 会被忽略）。
 */
private fun twoStageBack(navController: NavHostController, activity: Activity?) {
    val route = navController.currentBackStackEntry?.destination?.route
    if (route != null && isChatRoute(route) && navController.previousBackStackEntry == null) {
        // 第二步：已经在对话页，且栈里只剩它 → 退出应用。
        // 显式判空而不是 `activity?.finish()`：解析不到 Activity 时什么都不做
        // 会让用户「按返回没反应」且无迹可寻，这条日志就是唯一的线索。
        if (activity != null) {
            activity.finish()
        } else {
            Log.w(TAG, "back exit failed: LocalActivity is null, cannot call finish()")
        }
        return
    }
    // 第一步：无论当前在哪个页签、哪个子页，一律先回到对话页。
    navController.navigateTop(CHAT_ROUTE)
    // 兜底收敛：navigateTop 依赖 graph.startDestinationId 命中对话页，一旦将来被改坏，
    // popUpTo 会静默失败、栈继续增长。这里强制 pop 到只剩栈底，让「再按一次就退出」
    // 不依赖 popUpTo 是否生效。
    for (i in 0 until MAX_BACK_STACK_DRAIN) {
        if (navController.previousBackStackEntry == null) break
        if (!navController.popBackStack()) break
    }
    if (navController.previousBackStackEntry != null) {
        Log.w(
            TAG,
            "back-stack drain failed after $MAX_BACK_STACK_DRAIN pops; " +
                "check that startDestination and the composable route are the same string",
        )
    }
}
