import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :core-context — 上下文工程：预算算式 / 压缩器 / 前缀指纹 / 结构化摘要
//
// ## 为什么单独成模块（W7 开波第一件事，关闭挂账 W6-07）
//
// :core-kernel 在 W6 收口时实测 1790 / 1800（余 10 行），而 W7 要往内核新增
// 自动压缩、缓存指标、切换守卫等代码 —— 不先拆就必定判红。按本仓既定纪律
// 「触顶就拆，不调大上限」（W4 拆 :feature-tools、W5 拆 :core-kernel 是同一处置），
// 把 context/ 六支文件整体下沉为 :core-context，:core-kernel 只留 policy/ + log/。
//
// ## 依赖方向：只有一次真实编译算数
//
// 「看起来独立的包 ≠ 真的独立」：只搬 context/ 而不给它 :core-kernel，编译立刻红 ——
//   · BudgetFormula.kt        → policy.AgentPolicy（公开签名里带 AgentPolicy）
//   · ContextCompressor.kt    → log.AgentLogStore（记压缩账）
//   · LayeredContextCompressor.kt → log.AgentLogStore
// 所以方向单向固定为 **:core-context → :core-kernel**，用 api 而非 implementation：
// 压缩器的公开签名暴露 AgentPolicy，必须让下游模块可见。反向（kernel 依赖 context）
// 会成环，由 arch-guard.sh 的 G6 禁止边钉住。
//
// ## 包名刻意保持不变
//
// `com.rickeal.liquidharness.core.agent.context` 原样保留：core-agent / feature-chat
// 里 20 余处 import 一行都不用改，拆分是纯机械搬迁，风险只在构建图。
// 包名与模块名不一致是有意的取舍（沿用 W5 拆 :core-kernel 时的同一口径）——
// 换包名会制造一个大而无当的 diff，让这次拆分的真实影响不可审。
//
// ## 行数预算（W6-07 三条硬约束）
//
//   · :core-kernel 上限 450（policy 235 + log 168 = 403 实占，余 47）
//   · :core-context 上限 1650（W6-07 拆分给 1450；W7 一次性裁决 +200 容纳
//     SwitchGuard / SettledCompressPolicy 两个策略类，详见
//     docs/notes/w7-line-budget-ruling.md，不构成先例）
//   · 两者之和 2100 = 原 1800 + 100 拆分开销 + 200 W7 新功能（两类分开记账）；
//     **TOTAL_LINE_CAP 42500 保持不动** —— 拆分只该让单个模块变轻，不该让全仓变胖。
//   · 禁止「凑 1800」的分法（那会给 core-context 只留 13 行，等于把死结搬家）。
// =============================================================================

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rickeal.liquidharness.core.context"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk {
            version = release(31)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 单向：context → kernel。kernel 已 api(:core-model)，SecretRedactor /
    // ChatMessage / TokenEstimator 等契约类经它传递可见。
    api(project(":core-kernel"))

    // LayeredContextCompressor 是 suspend 函数（摘要器是挂起调用），
    // 且 context 的 6 支 JVM 单测里有 runBlocking —— 需要 kotlinx-coroutines-core。
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
