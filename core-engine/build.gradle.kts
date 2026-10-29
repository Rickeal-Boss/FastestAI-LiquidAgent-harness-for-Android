import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :core-engine — 供应商适配三族 + 远程引擎
//
// 约束：
//   * 依赖方向只向下：core-model（契约）+ core-net（HTTP/SSE）。
//     **不许**依赖 core-agent —— `LlmEngine` 端口由 core-agent 定义、本模块实现，
//     所以依赖边是 core-agent ← core-engine（00-brief A2 依赖拓扑）。
//   * 不引 Compose、不引注解处理器。
//   * 单文件上限 500 行（01-architecture §二）。
// =============================================================================

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rickeal.liquidharness.core.engine"

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
    // api：GenerationRequest / GenerationEvent / RemoteEndpoint 是本模块签名的一部分，
    // 上层（core-agent、core-data）通过本模块用到它们时不应再单独声明 core-model。
    api(project(":core-model"))
    implementation(project(":core-net"))

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
