import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :core-skills — 技能（Skill）声明、发现与调度元数据
//
// 只实现 :core-agent 的 SkillCatalog 端口：解析 SKILL.md、扫描本地/内置来源、
// 校验导入包。技能仍是纯文本，**不引入任何执行语义**。
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
    // 端口（SkillCatalog / SkillBody / SkillMeta）在 :core-agent，core-agent 已 api 暴露 :core-model。
    api(project(":core-agent"))

    // 仅用于 SkillSource.scan() 的 withContext(Dispatchers.IO)：文件扫描不能占用调用方线程。
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
