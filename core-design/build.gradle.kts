import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :core-design — Liquid Glass 设计系统（tokens / 颜色 / 动效 / 组件）
//
// 纯 Compose 模块：不依赖任何 project，这样 @Preview 不需要构造领域对象，
// 且 ModelDescriptor 变更不会触发 UI 全量重编译。
//
// 依赖用 api：feature 模块要能直接使用 core-design 暴露的 Compose 类型
// （Theme / Modifier 扩展 / 组件签名里出现 Material3 类型）。
// =============================================================================

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.rickeal.liquidharness.core.design"

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
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)

    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    // GlassImageThumb 用 androidx.core.net.toUri（Uri → File），旧仓同款依赖。
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
