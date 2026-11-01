import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :core-kernel — Agent 的纯逻辑基座：策略 / 日志 / 上下文工程
//
// ## 为什么单独成模块
//
// W5 收口时 `:core-agent` 实测 6495 / 6500（G10 模块预算），再加一行就判红。
// 按本仓既定纪律「触顶就拆，不调大上限」（W4 从 :feature-settings 拆出
// :feature-tools 是同一处置），把不需要引擎/工具/循环的那三层搬出来。
//
// ## 为什么是这三个包，而不是只搬 context
//
// 第一版只搬了 `context/`，编译直接红：`ContextCompressor` 反向依赖
// `AgentPolicy`（读 `maxContextTokens` / `compressThreshold`）与 `AgentLogStore`
// （记压缩账）。要么新模块反向依赖 `:core-agent`（成环），要么把这两个也一起下沉。
// 选后者——它们本来就比 context 更低层：`AgentPolicy` 是纯 `@Serializable`
// 数据类，`AgentLogStore` 只依赖 `core-model.SecretRedactor`。
//
// 这条经验值得记住：**「哪个包看起来独立」和「它真的独立」是两件事**，
// 判据只有一次真实编译，不是读 import 列表。
//
// ## 包名刻意保持不变
//
// `com.rickeal.liquidharness.core.agent.{policy,log,context}` 原样保留：这样
// `core-agent` 里 20 余处 import 一行都不用改，拆分是纯机械搬迁，风险只在构建图。
// 包名与模块名不一致是有意的取舍——换包名会制造一个大而无当的 diff，
// 让这次拆分的真实影响不可审。
//
// ## 依赖方向
//
// kernel → core-model（纯契约）。不认识引擎、不认识网络、不认识工具。
// 驱动引擎的 `EngineBackedSummarizer` 刻意留在 `:core-agent/loop/` —— G18 规定
// 只有那个包可以调 `engine.generate()`。
// =============================================================================

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rickeal.liquidharness.core.kernel"

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

    // LayeredContextCompressor 是 suspend 函数（摘要器是挂起调用），
    // 需要 kotlinx-coroutines-core 的 CancellationException / currentCoroutineContext。
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
