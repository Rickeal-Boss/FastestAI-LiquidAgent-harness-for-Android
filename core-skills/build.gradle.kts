import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :core-skills — 技能（Skill）声明、发现与调度元数据
//
// 骨架阶段：只依赖 :core-model，保持零外部依赖（技能描述是纯数据）。
// =============================================================================

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.rickeal.liquidharness.core.skills"

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

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
