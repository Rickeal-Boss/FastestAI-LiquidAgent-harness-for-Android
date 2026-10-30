package com.rickeal.liquidharness.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.rickeal.liquidharness.core.data.AppContainer
import com.rickeal.liquidharness.core.design.LocalOverlayBlurState
import com.rickeal.liquidharness.core.design.OverlayBlurState

/**
 * 应用导航壳的入口。
 *
 * 只做一件事：在**外壳之上、主题之内**下发覆盖层模糊登记表。
 *
 * 位置很关键（原样搬自旧仓的推导）：
 *  - 在 `MainShell` **之外**下发：抽屉（`ModalNavigationDrawer` 的 drawerContent 是
 *    body 的兄弟，虽同在外壳组合内但层级不同）与各处 `LiquidDialog` 会各自读到不同的
 *    provider —— 必须是同一个实例才能互通「我开着」；
 *  - 在主题**之内**下发：登记表本身不读配色，但它的消费者在 draw 阶段读，保持在主题
 *    之内可以避免将来加「按主题调半径」时踩到静默失效。
 */
@Composable
fun HarnessApp(container: AppContainer) {
    val overlayBlurState = remember { OverlayBlurState() }
    CompositionLocalProvider(LocalOverlayBlurState provides overlayBlurState) {
        AppScaffold(container)
    }
}
