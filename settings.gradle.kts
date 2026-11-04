// =============================================================================
// FastestAI-Harness (LiquidAgent) — 模块声明
//
// 约定：
//   * 全部使用扁平命名，project path 与目录名字面一致（:core-model <-> core-model/）
//   * 仓库里没有 jitpack：任何 JitPack 依赖都会在 FAIL_ON_PROJECT_REPOS 下直接失败
//   * 模块级 repositories {} 一律禁止 —— 仓库只能声明在下面两处
// =============================================================================

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FastestAI-Harness"

// ---- 14 个模块，按依赖拓扑排序（下层在前） ----
include(":core-model")
include(":core-net")
// :core-engine 在 core-net 之后、core-agent 之前：依赖 core-model + core-net，
// 被 core-agent 的 LlmEngine 端口反向实现（core-agent ← core-engine，方向不可逆）。
include(":core-engine")
include(":core-kernel")
// W7 开波第一件事（关闭挂账 W6-07）：context/ 从 :core-kernel 下沉为 :core-context。
// 方向单向固定为 core-context → core-kernel（policy/log 留在原处）：BudgetFormula 依赖
// AgentPolicy、两个压缩器依赖 AgentLogStore，没有这条边编译立刻红。放在 core-agent 之前
// 是因为 core-agent 同时 api() 依赖两者，拓扑上必须先声明下层。
include(":core-context")
include(":core-agent")
include(":core-skills")
include(":core-mcp")
include(":core-data")
include(":core-design")
include(":feature-chat")
include(":feature-settings")
// :feature-tools 在 feature-settings 之后：它是 W4 从 :feature-settings 拆出的
// 「文件浏览 + 工具页」两个页面，**不依赖** :feature-settings（feature ↛ feature，
// G6 硬边），两者是并列关系而非上下层。放在这里只是因为字母/职责相邻，便于阅读。
include(":feature-tools")
include(":app")
