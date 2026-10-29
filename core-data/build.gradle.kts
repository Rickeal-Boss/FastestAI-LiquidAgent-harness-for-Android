import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :core-data — DataStore Preferences + JSON 文件持久化 + 仓库 + AppContainer
//              （手写 DI + CompositionLocal）
//
// 禁 Room / Hilt：持久化走 Preferences + JSON 文件，DI 只走 AppContainer。
// =============================================================================

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rickeal.liquidharness.core.data"

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
    // AppContainer 暴露 AgentRunner / RemoteLlmEngine 工厂，二者出现在公开签名里，故用 api
    api(project(":core-agent"))
    api(project(":core-engine"))
    // HttpTransport 只在装配处（AppContainer）实例化，不外泄类型
    implementation(project(":core-net"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // AppContainer 的 LocalAppContainer 需要 staticCompositionLocalOf，只依赖 runtime（不引 ui）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)

    // ViewModelFactory 需要 ViewModel / ViewModelProvider
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
