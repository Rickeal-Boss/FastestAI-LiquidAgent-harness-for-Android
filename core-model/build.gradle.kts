import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :core-model — 纯领域模型 + 序列化 + 纯算法
//
// 约束：
//   * 用 com.android.library（**不用** org.jetbrains.kotlin.jvm，避免与 AGP 9 的
//     variant 体系冲突），但**不依赖任何 Android API** —— 只用 java.util / java.io，
//     便于后续直接跑 JVM 单测。
//   * 无 Compose。
//   * 只依赖 kotlinx-serialization-json。
// =============================================================================

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rickeal.liquidharness.core.model"

    // AGP 9 块式 DSL（AGP 9.3.2 实测签名：
    //   CompileSdkSpec.release(int) / MinSdkSpec.release(int) / TargetSdkSpec.release(int)）
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
    // api：领域模型要暴露给所有上层模块
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
