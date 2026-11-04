import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :core-agent — Agent 循环、工具注册中心、内置工具、上下文压缩
// =============================================================================

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rickeal.liquidharness.core.agent"

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
    // W5：策略 / 日志 / 上下文工程下沉到 :core-kernel（core-agent 触顶 G10 6500 行的
    // 处置是拆分，不是调大上限）。用 api 而非 implementation：TurnContext /
    // AgentPolicy / 压缩器的类型出现在下游模块的公开签名里，必须可见。
    api(project(":core-kernel"))
    // W7（关闭挂账 W6-07）：kernel 1790/1800 触顶 ⇒ 再把 context/ 下沉为 :core-context，
    // kernel 只留 policy/ + log/。core-agent 与 feature-chat 直接 import core.agent.context，
    // 故这里必须 api() 暴露新模块（下游靠 api 传递可见）。:core-context 自身
    // api(:core-kernel)，依赖链 core-model → kernel → context → agent 单向无环。
    api(project(":core-context"))

    // ⚠️ 刻意**不依赖** :core-net / :core-engine / :core-skills / :core-mcp。
    // 方向是「适配器 → 端口」：那四个模块依赖 :core-agent（或只依赖 :core-model），
    // 内核不认识 HTTP、不认识 SKILL.md、不认识 JSON-RPC（01-architecture.md §一 三条不可逆的边）。
    // 此前骨架里有一行 `implementation(project(":core-net"))`，已删除 —— 它会让内核
    // 可测性依赖网络栈，且守卫 N7（模块边界）必然判红。

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
