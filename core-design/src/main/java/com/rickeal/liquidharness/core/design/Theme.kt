package com.rickeal.liquidharness.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 设计系统的最小落点（W1）。
 *
 * 旧仓 `core-design` 有 11 749 行液态玻璃实现（`GlassSurface` / 折射 / 阻尼拖拽），
 * 那是 W3 的活。W1 只保证：**颜色与排版有唯一来源**，业务模块不自己写 `Color(0xFF...)`。
 * 这样 W3 换皮时只改本文件，不用全仓搜色值。
 *
 * 配色取向：深色优先（长会话在深色下更省电、对比更稳），亮色作为跟随系统的分支。
 */
private val Seed = Color(0xFF4F7CFF)
private val SurfaceDark = Color(0xFF0E1014)
private val SurfaceDarkElevated = Color(0xFF171A21)
private val SurfaceLight = Color(0xFFF7F8FA)
private val SurfaceLightElevated = Color(0xFFFFFFFF)

private val DarkColors = darkColorScheme(
    primary = Seed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E2A4A),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFF8FA3C8),
    background = SurfaceDark,
    onBackground = Color(0xFFE6E8EE),
    surface = SurfaceDark,
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = Color(0xFFA8AFBF),
    outline = Color(0xFF3A404D),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
)

private val LightColors = lightColorScheme(
    primary = Seed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF10214A),
    secondary = Color(0xFF4A5A78),
    background = SurfaceLight,
    onBackground = Color(0xFF14171E),
    surface = SurfaceLight,
    onSurface = Color(0xFF14171E),
    surfaceVariant = SurfaceLightElevated,
    onSurfaceVariant = Color(0xFF4C5566),
    outline = Color(0xFFC7CCD6),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/**
 * 应用主题。
 *
 * @param dark 传 null 表示跟随系统。显式传值用于截图与 UI 测试的确定性。
 */
@Composable
fun HarnessTheme(
    dark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val useDark = dark ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
