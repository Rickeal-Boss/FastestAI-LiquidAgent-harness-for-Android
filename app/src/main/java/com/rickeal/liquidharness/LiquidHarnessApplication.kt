package com.rickeal.liquidharness

import android.app.Application
import com.rickeal.liquidharness.core.data.AppContainer

/**
 * 进程入口。
 *
 * 只做一件事：创建 [AppContainer]。
 *
 * ⚠️ 这里**不许**调任何 `getSystemService()`：旧仓 W78 的真实 P0 事故是
 * Service 字段初始化期取系统服务 ⇒ NPE 打死进程，而三层闸门全绿。
 * `AppContainer` 的构造只持有 `applicationContext`，真正的系统服务在首次使用时才取。
 */
class LiquidHarnessApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
