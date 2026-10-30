package com.rickeal.liquidharness.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一级导航纯判据的单测。
 *
 * 这些函数写错的表现全是「不报错但行为错」：页签高亮丢失、切页签时内容反向滑、
 * 返回键先退出而不是先回对话页。只有把边界钉住才能防回归。
 *
 * 纯 Kotlin / 无 Android 依赖 —— 不需要 Robolectric，JVM 单测直接跑。
 */
class AppNavigationTest {

    @Test
    fun topIndexMapsChatToZeroAndSettingsToOne() {
        assertEquals(0, topIndex(CHAT_ROUTE))
        assertEquals(1, topIndex("settings"))
    }

    @Test
    fun topIndexFallsBackToZeroForUnknownRoute() {
        // 未知 route 归到对话：宁可高亮第一项，也不要出现"没有任何页签高亮"的空态。
        assertEquals(0, topIndex(null))
        assertEquals(0, topIndex("settings-not-really"))
        assertEquals(0, topIndex("chatty"))
    }

    @Test
    fun topIndexTreatsSubRoutesAsTheirTopDestination() {
        assertEquals(1, topIndex("settings/endpoints"))
        assertEquals(1, topIndex("settings/endpoints/edit"))
        assertEquals(1, topIndex("settings?tab=about"))
    }

    @Test
    fun slideDirectionIsPositiveWhenIndexGrows() {
        assertEquals(1, slideDirection(CHAT_ROUTE, "settings"))
        // 子路由按所属一级目的地计：进入设置子页同样从右侧滑入。
        assertEquals(1, slideDirection("settings", "settings/endpoints"))
        // 同一个目的地：方向取 +1（不得为 0，0 会让 slideInHorizontally 退化成原地淡入）。
        assertEquals(1, slideDirection(CHAT_ROUTE, CHAT_ROUTE))
    }

    @Test
    fun slideDirectionIsNegativeWhenIndexShrinks() {
        assertEquals(-1, slideDirection("settings", CHAT_ROUTE))
        assertEquals(-1, slideDirection("settings/endpoints", CHAT_ROUTE))
    }

    @Test
    fun routeTopResolvesLongestPrefix() {
        assertEquals(TopDestination.SETTINGS, routeTop("settings/endpoints/edit"))
        assertEquals(TopDestination.CHAT, routeTop(CHAT_ROUTE))
        assertNull(routeTop(null))
    }

    @Test
    fun isChatRouteAcceptsEveryForm() {
        // 对话页目前只有无参路由，但 `?` 前缀判据必须提前成立 —— W3 接入会话 id 后
        // route 会变成 `chat?conversationId=xxx`，这里不改代码。
        assertTrue(isChatRoute(CHAT_ROUTE))
        assertTrue(isChatRoute("chat?conversationId=abc"))
        assertFalse(isChatRoute("settings"))
        assertFalse(isChatRoute("chatty"))
    }
}
