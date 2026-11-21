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
        // W44（W12-02 出口判据）：把环形缓冲镜像到落盘日志 —— `cached=` 四态探针
        // 从此在设备 `files/harness/logs/agent-*.log` 可复核（此前只有 INFO→ring）。
        DiagnosticLogSink(container.logs).install()
    }
}
