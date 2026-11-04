import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// :core-kernel — Agent 的纯逻辑基座：策略 / 日志
//
// ## 为什么单独成模块（W5 历史）
//
// W5 收口时 `:core-agent` 实测 6495 / 6500（G10 模块预算），再加一行就判红。
// 按本仓既定纪律「触顶就拆，不调大上限」（W4 从 :feature-settings 拆出
// :feature-tools 是同一处置），把不需要引擎/工具/循环的那三层搬出来。
//
// ## 为什么当时是三个包，而不是只搬 context（W5 的历史判断，仍成立）
//
// 第一版只搬了 `context/`，编译直接红：`ContextCompressor` 反向依赖
// `AgentPolicy`（读 `maxContextTokens` / `compressThreshold`）与 `AgentLogStore`
// （记压缩账）。要么新模块反向依赖 `:core-agent`（成环），要么把这两个也一起下沉。
// 选后者——它们本来就比 context 更低层：`AgentPolicy` 是纯 `@Serializable`
// 数据类，`AgentLogStore` 只依赖 `core-model.SecretRedactor`。
//
// 这条经验值得记住：**「哪个包看起来独立」和「它真的独立」是两件事**，
// 判据只有一次真实编译，不是读 import 列表。W7 拆 :core-context 时又用到它：
// 见下段，这次是把 policy/log **留在原处**、给新模块一条 kernel 依赖边。
//
// ## W7 二次拆分（关闭挂账 W6-07）
//
// W6 收口时本模块 1790 / 1800（余 10 行），而 W7 要往内核加自动压缩 / 缓存指标 /
// 切换守卫 ⇒ 不先拆必定判红。`context/` 六支文件（1387 行）连同 6 支 JVM 单测
// 整体下沉为 `:core-context`，本模块只留 `policy/`(235) + `log/`(168) = 403 行。
// 上限改为 450；新模块 W6-07 给 1450，W7 一次性裁决再 +200（容纳两个策略类）→ 1650，
// kernel+context 之和 2100 = 原 1800 + 100 拆分开销 + 200 W7 新功能（两类分开记账）；
// **TOTAL_LINE_CAP 42500 未动**（拆分不是扩容）。详见 docs/notes/w7-line-budget-ruling.md。
//
// ## 包名刻意保持不变
//
// `com.rickeal.liquidharness.core.agent.{policy,log,context}` 原样保留：W5 与 W7
// 两次搬迁都是纯机械移动，`core-agent` / `feature-chat` 里 20 余处 import 一行都不用改，
// 风险只在构建图。包名与模块名不一致是有意的取舍——换包名会制造一个大而无当的 diff，
// 让拆分的真实影响不可审。
//
// ## 依赖方向（单向，不得成环）
//
// kernel → core-model（纯契约）。不认识引擎、不认识网络、不认识工具。
// **:core-context → kernel**（新模块反依赖 policy/log），kernel **绝不**反向依赖
// :core-context —— 这条反向边由 arch-guard.sh 的 G6 禁止边钉住。
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

    // W7：LayeredContextCompressor（suspend）已随 context/ 迁入 :core-context，
    // 它才是 kotlinx-coroutines-core 的唯一消费者（CancellationException /
    // currentCoroutineContext / isActive）⇒ 该依赖一并迁走，本模块不再需要。
    // policy/AgentPolicy 的 @Serializable 仍由 plugins 块的 kotlin.serialization 提供。
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
