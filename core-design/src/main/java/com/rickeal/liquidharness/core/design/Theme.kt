package com.rickeal.liquidharness.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * 应用主题门面（`:app` 的唯一入口）。
 *
 * W1 只有一套最小配色；W3 搬入完整 Liquid Glass 设计系统后，本门面改为**委托**
 * [LiquidAgentTheme] —— 颜色 / 排版 / 形状 / 动效的唯一来源都在那边
 * （`GlassColorScheme` + `LiquidTypography` + `LiquidShapes`），调用方一行不用改。
 *
 * @param dark 传 null 表示跟随系统。显式传值用于截图与 UI 测试的确定性。
 */
@Composable
fun HarnessTheme(
    dark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    LiquidAgentTheme(
        darkTheme = dark ?: isSystemInDarkTheme(),
        content = content,
    )
}
