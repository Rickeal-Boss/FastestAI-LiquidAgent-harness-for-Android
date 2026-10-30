package com.rickeal.liquidharness

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.rickeal.liquidharness.core.design.GlassBackdropBlurOverride
import com.rickeal.liquidharness.core.design.GlassConfig
import com.rickeal.liquidharness.core.design.LiquidAgentTheme
import com.rickeal.liquidharness.ui.HarnessApp

/**
 * 唯一 Activity。
 *
 * 只做四件事：edge-to-edge、取容器、挂主题、挂导航壳。业务页面全部在 `feature-*`
 * 模块里，`:app` 不承载任何业务逻辑（旧仓 `LiquidAgentApp.kt` 长到 1342 行的教训）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as LiquidHarnessApplication).container
        setContent {
            val darkTheme = isSystemInDarkTheme()
            // 玻璃渲染开关：本仓 W1 设置页只有端点 / 关于 / 诊断，没有「背景模糊」开关的
            // 落点，故直接读 core-design 的进程级覆盖值（默认 true）。将来接入主题设置时
            // 把这里换成 `themeState.enableBackdropBlur` 即可 —— 这正是
            // GlassBackdropBlurOverride 的 KDoc 预留的迁移路径。
            val glassConfig = GlassConfig(enableBackdropBlur = GlassBackdropBlurOverride.enabled)
            LiquidAgentTheme(darkTheme = darkTheme, glassConfig = glassConfig) {
                SyncSystemBarIcons(darkTheme = darkTheme)
                HarnessApp(container)
            }
        }
    }
}

/**
 * 浅色主题下把状态栏 / 导航栏图标切成深色。
 *
 * 项目走了 edge-to-edge（`setDecorFitsSystemWindows(false)`），系统不会再自动反色，
 * 只能自己在 [darkTheme] 变化时同步一次 —— 否则浅色壁纸上的白色图标几乎看不见。
 */
@Composable
private fun SyncSystemBarIcons(darkTheme: Boolean) {
    val view = LocalView.current
    LaunchedEffect(darkTheme) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
