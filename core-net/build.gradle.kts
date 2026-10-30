import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :core-net — 远程引擎 / MCP 的 HTTP 传输层（OkHttp + 手写 SSE）
//
// 硬约束：不引 Retrofit（本仓禁止），SSE 由 OkHttp 的流式 response 手写解析。
// =============================================================================

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rickeal.liquidharness.core.net"

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
    api(project(":core-model"))

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    // 单测要直接构造 okhttp3.Dns / 断言 HttpUrl 的规范化结果，故显式声明
    // （implementation 对 test 源集本就可见，这里写明是为了不依赖该隐式规则）。
    testImplementation(libs.okhttp)
}
