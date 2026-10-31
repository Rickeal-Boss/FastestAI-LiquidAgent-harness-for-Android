import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :feature-tools — 文件浏览页 + 工具页（W4 从 :feature-settings 拆出）
//
// 为什么要单独成模块（而不是调大 :feature-settings 的 G10 上限）：
//   :feature-settings 在 W4 移植两页（文件浏览 478 行 + 工具页 311 行）后总量
//   3451 行 > G10 上限 3000。G10 的纪律是「触顶 ⇒ 拆分，不是调大上限」
//   （docs/01-architecture.md §二），故按「设置」与「工具/文件」两类职责拆开。
//
// 依赖一次给足（业务实现下一单不需要再改构建脚本）。
//
// ⚠️ 依赖边界（本模块的**硬约束**，改之前先看 G6）：
//   只允许 → :core-model / :core-design / :core-agent
//   刻意**不声明** :core-data：文件浏览走 java.io / SAF，工具页只读
//   core-agent 的 ToolRegistry / ToolSpec。若将来确实需要 :core-data，
//   那是「新增一条模块边界」，必须同时改 scripts/arch-guard.sh 的
//   FORBIDDEN_EDGES 并在 01-architecture.md §一 登记，不能只在这里加一行
//   implementation(project(...))。
// =============================================================================

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rickeal.liquidharness.feature.tools"

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

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core-model"))
    api(project(":core-design"))
    // 工具页直接读 ToolRegistry / ToolSpec（只读展示），与 :feature-settings 同一口径：
    // 传递依赖不该被当成「别人会一直替我们兜着」的承诺，故显式声明。
    api(project(":core-agent"))

    // 本模块自己也要声明 BOM：catalog 里的 compose 坐标不带版本。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
