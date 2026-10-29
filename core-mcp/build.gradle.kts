import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :core-mcp — MCP（Model Context Protocol）客户端：JSON-RPC over HTTP / SSE
//
// 不引第三方 MCP SDK（避免注解处理器与 JitPack），手写 JSON-RPC 帧。
// =============================================================================

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rickeal.liquidharness.core.mcp"

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
}
