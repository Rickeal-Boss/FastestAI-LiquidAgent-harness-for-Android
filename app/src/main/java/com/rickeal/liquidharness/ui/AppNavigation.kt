package com.rickeal.liquidharness.ui

import androidx.annotation.StringRes
import androidx.navigation.NavHostController
import com.rickeal.liquidharness.R
import com.rickeal.liquidharness.feature.settings.SettingsRoute

/**
 * 对话页路由。
 *
 * 刻意定义在 `:app` 而不是 `:feature-chat`：一级导航的起点必须由外壳决定（见
 * [AppNavHost] 的 `startDestination`），feature 模块只提供屏幕，不提供"我是第一页"
 * 这种全局事实。`navigateTop` 的 `popUpTo(graph.startDestinationId)` 依赖
 * **startDestination 与 composable 注册的 route 字符串同源** —— 两处不同源时
 * `popUpTo` 会静默失效、回退栈随切页签无限增长（旧仓 UI-01 的根因）。
 */
internal const val CHAT_ROUTE = "chat"

/**
 * 一级目的地。
 *
 * 旧仓有五个（对话 / 模型 / 工具 / 记忆 / 设置）。本仓把端侧模型管理整块删除、
 * 工具与记忆尚未立项，因此只剩两个 —— 枚举就是这一事实的**唯一**表达，
 * 页签条、竖栏、选中态、滑动方向全部从 [entries] 派生，不写第二份清单。
 *
 * `labelRes` 而不是字面量：`app` 模块虽不在 G16 扫描面内，但 W8 判据要求文案资源化。
 */
internal enum class TopDestination(
    @StringRes val labelRes: Int,
    val route: String,
) {
    CHAT(R.string.tab_chat, CHAT_ROUTE),
    SETTINGS(R.string.tab_settings, SettingsRoute.ROUTE),
}

/**
 * route → 一级目的地的**最长前缀**匹配。
 *
 * 子路由（`settings/endpoints`、`settings/endpoints/edit`）按前缀归到所属页签，
 * 于是「进入设置子页」天然继承设置的选中态与滑动方向，不需要在每个子路由上再登记一次。
 * 取最长前缀而不是首个命中：将来若出现 `settings2` 这类同前缀路由，短前缀不会抢走它。
 */
internal fun routeTop(route: String?): TopDestination? {
    if (route == null) return null
    return TopDestination.entries
        .filter {
            route == it.route ||
                route.startsWith("${it.route}?") ||
                route.startsWith("${it.route}/")
        }
        .maxByOrNull { it.route.length }
}

/** 一级目的地在一级导航里的序号；未知 route 归 0（对话）。 */
internal fun topIndex(route: String?): Int =
    routeTop(route)?.let { TopDestination.entries.indexOf(it) } ?: 0

/**
 * 页签切换的滑动方向：+1 = 新页从右入（序号增大），-1 = 从左入。
 * 子路由按其所属一级目的地计（见 [routeTop]），因此「进入设置子页」也天然从右侧滑入。
 */
internal fun slideDirection(initialRoute: String?, targetRoute: String?): Int =
    if (topIndex(targetRoute) >= topIndex(initialRoute)) 1 else -1

/**
 * 当前 route 是否指向对话页。
 *
 * 对话页目前只有一条无参路由，但保留 `?` 前缀判据：将来接入会话 id（W3 持久化）时，
 * 这里不需要再改一次 —— 判据形态与旧仓一致，避免"高亮丢失 / 返回键先关抽屉失效"。
 */
internal fun isChatRoute(route: String): Boolean =
    route == CHAT_ROUTE || route.startsWith("$CHAT_ROUTE?")

/**
 * 顶层页签跳转。
 *
 * 必须 `popUpTo` 到起始目的地，否则回退栈会随切页签无限增长（`[chat, settings, chat, …]`），
 * 按返回键变成"在上一个页签间倒着走"；更糟的是每个条目都有自己的 ViewModelStore，
 * 每次切回对话页都是一个全新的 ChatViewModel —— 草稿、滚动位置、正在流式的回答全消失。
 *
 * `saveState` + `restoreState` 成对出现才有意义：前者在 pop 时保存目的地的 SavedState，
 * 后者在重新 navigate 时还回去。`inclusive = false` 保留起始目的地本身。
 */
internal fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) {
            saveState = true
            inclusive = false
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * 返回键兜底收敛的 pop 次数上限。
 *
 * 正常路径下一次 `navigateTop` 就把栈收敛成 `[chat]`，这里跑 0 次。只有 startDestination
 * 与 composable route 失配导致 `popUpTo` 静默失效时才会真跑；用固定上限而不是
 * `while (true)`：正确性不该押在 Navigation 内部是否同步移除 backQueue 上。
 */
internal const val MAX_BACK_STACK_DRAIN = 12

/**
 * 顶层页签切换的 iOS push/pop 视差参数（原样搬自旧仓）。
 *
 * 视差比：新页入场 32%、旧页退场 14% —— 新页"推入"、旧页"让位"，而不是两页等速
 * 把整块屏幕拖走。slide 统一 260ms（FastOutSlowIn）：必须**大于**底部指示胶囊的
 * TabSwitch（≈120ms 临界阻尼收敛）—— 指示器快于内容层是正确次序。
 * fade 内外错开（入 180 / 出 200）：同长同相会让切页瞬间两页都半透明叠在一起。
 */
internal const val PUSH_ENTER_PARALLAX = 0.32f
internal const val PUSH_EXIT_PARALLAX = 0.14f
internal const val PUSH_SLIDE_MS = 260
internal const val PUSH_FADE_IN_MS = 180
internal const val PUSH_FADE_OUT_MS = 200
