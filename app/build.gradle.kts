import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :app — 壳：Application / MainActivity / NavHost / DI 组装
//
// 签名契约（CI 席约定，release.yml 传四个 -P）：
//   signing.storeFile / signing.storePassword / signing.keyAlias / signing.keyPassword
//   四者齐备才创建 release signingConfig；否则 release 退回 debug 签名 ——
//   保证无密钥时 assembleRelease 也能跑通（构建不许失败）。
// =============================================================================

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rickeal.liquidharness"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.rickeal.liquidharness"
        minSdk {
            version = release(31)
        }
        targetSdk {
            version = release(36)
        }
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // ---------------------------------------------------------------------
    // 签名：secrets 缺失时静默退回 debug 签名（构建不许失败）
    // ---------------------------------------------------------------------
    signingConfigs {
        val storeFilePath = project.findProperty("signing.storeFile")?.toString()
        val storePasswordValue = project.findProperty("signing.storePassword")?.toString()
        val keyAliasValue = project.findProperty("signing.keyAlias")?.toString()
        val keyPasswordValue = project.findProperty("signing.keyPassword")?.toString()

        val hasSigning = !storeFilePath.isNullOrBlank() &&
            storePasswordValue != null &&
            keyAliasValue != null &&
            keyPasswordValue != null

        if (hasSigning) {
            create("release") {
                storeFile = file(storeFilePath!!)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                // ⚠️ 只写 enableV3Signing，**不要**加 enableV2Signing（旧仓 CI 实测）：
                //   AGP 9 按「minSdk 需要的最低方案」选签名方案，不是叠加 ——
                //   开启 v3 会取代 v2（写了 enableV2Signing 也是 v2=false）。
                //   minSdk 31 的设备全部支持 v3（Android 9 / API 28 引入），v3-only 合法，
                //   且相对 v2 多了密钥轮换（key rotation）能力。
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 无密钥 -> findByName 返回 null -> 退回 debug 签名，构建不会失败
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // ---------------------------------------------------------------------
    // Lint（AGP 自带 Android Lint，不引入 detekt / ktlint）
    //
    // TODO(首轮 CI)：baseline 文件由首轮 CI 的 :app:lintDebug 产出
    //   （workflow 会以 artifact `lint-baseline-<sha>` 上传 app/lint-baseline.xml），
    //   取回并提交后把 abortOnError 翻成 true。
    //   翻 true 之前保持 false：首轮没有 baseline 就开闸只会「开局即红」，
    //   且红灯不携带新信息（存量问题全部是尚未落地的业务代码带来的）。
    //   ⛔ 不要手工从 lint-results XML 合成 baseline —— 路径形态与 id 匹配规则由 lint 决定。
    // ---------------------------------------------------------------------
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false // TODO: 首轮 CI 产出 baseline 后翻 true
        checkDependencies = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 全部 feature（业务实现落在 feature 模块；app 只做壳与 DI 组装）
    implementation(project(":feature-chat"))
    implementation(project(":feature-settings"))
    // W4 从 :feature-settings 拆出（G10 行数闸门：设置模块触顶 3000 ⇒ 拆分而非调大上限）
    implementation(project(":feature-tools"))

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    // 外壳的页签 / 抽屉 / 顶栏图标用 material-icons-extended（旧仓同一依赖）。
    // 不放进 core-design 的 api：设计系统的公开签名不暴露 ImageVector，图标是使用方的事。
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // JVM 单测（src/test/java，AGP 默认源集）
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
