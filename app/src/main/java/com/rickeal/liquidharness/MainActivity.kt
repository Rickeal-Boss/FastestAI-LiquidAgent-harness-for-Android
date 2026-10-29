package com.rickeal.liquidharness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rickeal.liquidharness.ui.HarnessApp

/**
 * 唯一 Activity。
 *
 * 只做三件事：edge-to-edge、取容器、挂导航壳。业务页面全部在 `feature-*` 模块里，
 * `:app` 不承载任何业务逻辑（旧仓 `LiquidAgentApp.kt` 长到 1342 行的教训）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as LiquidHarnessApplication).container
        setContent {
            HarnessApp(container)
        }
    }
}
