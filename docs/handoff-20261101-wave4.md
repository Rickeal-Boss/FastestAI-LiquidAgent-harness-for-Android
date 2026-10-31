# W4 交接 — 上下文工程二期（分块前缀缓存 / L2 摘要压缩 / 1M 窗口）+ UI 补齐（2026-11-01）

> 仓库：`FastestAI-LiquidAgent-harness-for-Android`（本地 `harness-v2/`）
> 基线：`a2a5fac`（W3 收口）→ 本波在工作树完成
> 设计：`docs/notes/w4-context-design.md`　供应商实测：`docs/notes/w4-provider-matrix.md`
> 闸门清单：`docs/notes/w4-gate-checklist.md`

---

## 一、本波结论（先看这段）

| 项 | 结果 |
|---|---|
| 单元测试 | **544 / 0 / 0 / 0**（12 模块；W3 基线 480 → +64） |
| 架构守卫 | **23 / 0 / 0**（G9 模块数 11 → 12 已同步） |
| 守卫自测 | `selftest` 全通过（见 §三 数字） |
| 模块数 | 11 → **12**（新增 `:feature-tools`） |
| 真机 | 工具页 / 沙箱文件页 / 窗口档位 ✅；聊天往返与 MCP 联调 **⛔ 环境阻塞**（见 §四） |
| 长上下文 | PC 侧 **760,812 token 取针命中（40.8s）**；设备侧**未行使**（依赖成功请求） |

**一句话**：前缀指纹、压缩水位线、L2 摘要压缩三件核心能力已落地并有判据钉住；
UI 补齐落地并被真机验证；但**设备到 OpenRouter 的请求仍然不收敛**（W3-08 复现），
所以「真机上跑一次长上下文」这一条本波仍是 vacuous。

---

## 二、交付清单

### 2.1 A 组 — 分块前缀缓存（`core-agent/context/`）

| 文件 | 作用 |
|---|---|
| `context/PromptPrefix.kt`（新，217 行） | 四段前缀指纹（SYSTEM / SKILLS / TOOLS_SCHEMA / HISTORY），SHA-256 前 12 位，`SegmentStability` 四档契约 |
| `context/ContextCompressor.kt` | 压缩**水位线**（目标 0.6×budget）+ `CutPolicy.TURN_ALIGNED`（切点对齐 USER，超 25% 额外丢弃则退回 MINIMAL）+ `canMerge` 豁免摘要消息 |
| `loop/ContextBudget.kt` | 工具面 run 内快照、指纹账行、WARN 级「prefix changed」、工具声明计入预算、L2 懒装配 |
| `prompt/AgentPromptComposer.kt` | `sectionsTyped()` —— 段序只留一份定义，`sections()` 委托 |
| `core-model/TokenEstimator.kt` | `estimateToolSpecs()`（W2.5 的 M2 终于落地） |

**关键判断（与设计一致）**：A 的真正缺口不是「缺缓存断点」，而是**压缩几乎每轮复发** ——
`WindowContextCompressor` 原本把保留区填满到 `budgetTokens`，余量恒小于「被丢第一条消息的代价」，
而一条满额读类工具结果 ≈4 000 token ⇒ 下一轮必然再越门槛。加水位线（0.6）后，
压缩从「每轮一次」变成「隔多轮一次」，前缀失效频率随之下降。

### 2.2 B 组 — L2 摘要压缩

| 文件 | 作用 |
|---|---|
| `context/StructuredSummary.kt`（新，292 行） | 五段结构化摘要（GOAL / FACTS / DECISIONS / DONE / OPEN TODOS），`parseStructuredSummary` 三态，首行必须是 `[CONTEXT SUMMARY]` 段头 |
| `context/LayeredContextCompressor.kt`（新，314 行） | L2 包 L1：**切点只由 L1 决定**（配对由构造保证），摘要以 `role=USER` 插 body 首位、id 稳定派生、失败回落 L1 |
| `loop/EngineBackedSummarizer.kt`（新，120 行） | 一次廉价调用做摘要：**不带工具、不重试、硬超时**，超时正常返回失败而不是升级取消 |

**为什么放在 `loop/` 而不是 `context/`**：架构守卫 G18 规定「只有 `core-agent/loop/` 可以驱动引擎」。
摘要器要调 `engine.generate()`，所以它必须住在 `loop/`。这不是妥协 ——
端口（`ContextSummarizer`）留在 `context/`，适配器在 `loop/`，方向与 `ContextBudget` 一致。

**接线（本波补的关键一步）**：`ContextBudget` 新增 `summarizerFactory`，按 run 懒构建
`LayeredContextCompressor`（引擎来自 `ctx.engine`）。这条接线有**独立判据**
`ContextBudgetTest.layeredCompressionInsertsSummaryWhenSummarizerConfigured` ——
因为「组件有 9 条单测」与「组件真的被使用」是两件事，前者全绿也证明不了后者。

### 2.3 窗口上限可配置（`maxContextTokens`）

| 文件 | 改动 |
|---|---|
| `core-data/SettingsRepository.kt` | 新增 `maxContextTokens` 流 + setter，钳制 8 000..1 000 000 |
| `core-data/AppContainer.kt` | `agentRunnerFor` 建 runner 时读取，注入 `AgentPolicy(maxContextTokens=…)` |
| `feature-settings/SettingsScreen.kt` + `SettingsViewModel.kt` + `strings.xml` | 「上下文窗口」分组：32K / 128K / 512K / 1M 四档，当前档标「已选」 |

**为什么必须有它**：预算算式是 `min(contextLength, maxContextTokens)`，`maxContextTokens` 写死 32 000 时，
一个声明 1M 窗口的端点在设备上最多只能发 ≈24k。**窗口能力行使不到，不是模型限制，是本应用的钱包上限。**

### 2.4 UI 补齐 — 新建 `:feature-tools` 模块

`feature-settings` 移植两个页面后模块总量 3 451 > G10 上限 3 000 ⇒ **新建模块而不是调上限**。

| 文件 | 作用 |
|---|---|
| `feature-tools/ToolsScreen.kt` + `ToolsViewModel.kt` | 工具清单（名称/描述/副作用档位/是否需审批），数据来自**真实 `ToolRegistry`**，不写死 |
| `feature-tools/SandboxFiles.kt` + `SandboxFilesScreen.kt` | 沙箱目录浏览：进入/返回/大小/时间/文本预览（截断）/删除（二次确认） |
| `feature-tools/ToolsGraph.kt` | 两条路由，由 `:app` 注入 `root: File` 与 `registryProvider` |
| `app/AppNavHost.kt` + `feature-settings/SettingsRoute.kt` | 入口经回调注入（feature-settings 不依赖 feature-tools） |
| `scripts/arch-guard.sh` + `settings.gradle.kts` + 两份 docs | MODULES / LINE_CAPS / G9 模块数 11 → 12 |

**`feature-tools` 刻意不依赖 `:core-data`**：沙箱根路径由 `:app` 算好传进去，
路径来源收敛在一处，而不是每个 feature 模块各自去 `AppContainer` 里摸。

### 2.5 供应商矩阵 v2（`docs/notes/w4-provider-matrix.md`）

- 14 个 `:free` 模型里 **12 个被账号级隐私开关挡住**（`Free model training violation`），
  只有 `dots-studio/dots-3-note-preview:free`（512k，工具+视觉双通）与
  `cohere/north-mini-code:free`（256k，工具通）可用。
- 1M 阶梯（付费 `nvidia/nemotron-3.5-super-vl`）：6k → 760,812 token 全过，延迟 2.6s → 40.8s。
- **缓存命中 = 逐字节相同的最长前缀**：阶梯里 `cached_tokens` 精确等于「上一档干草堆的一半」。
- 音频输出**本区不可用**（两个模型均 403 Geo Restriction），与 W3 一致。

---

## 三、闸门

```
fulltest      : tests=544 failures=0 errors=0 skipped=0（12/12 模块）
arch-guard    : OK=23 VIOLATION=0 SELF_FAULT=0 → PASS
arch-guard-selftest : 全通过
@Test 分布    : core-agent 200+ / core-model 47 / core-engine 42 / feature-chat 41 /
                core-net 39 / core-mcp 36 / core-skills 32 / core-design 31 /
                core-data 21+ / feature-tools 8 / feature-settings 1 / app 8
src/androidTest 的 @Test : 0（新增测试全部在 src/test）
```

---

## 四、真机验收（逐条三态，不合并）

设备：OPPO `PDRM00`，Android 13，1080×2400。包名 `com.rickeal.liquidharness.debug`。

| # | 判据 | 三态 | 证据 |
|---|---|---|---|
| D1 | 冷启 + 新对话页渲染 | ✅ | 截图：标题栏 `OpenRouter-VL · nvidi…`、空态「开始一段对话」 |
| D2 | 设置页出现两个新入口（Agent 工具 / 沙箱文件） | ✅ | 截图：能力分组 5 行全部渲染 |
| D3 | 工具页显示真实注册表 | ✅ | 截图：`共 11 个 · 已启用 11 个`，`calculator / 只读 / utility·免审批·内置` |
| D4 | 沙箱文件页列出真实文件 | ✅ | 截图：`根层 · 共 3 项`，`a.txt 1 B`、`b.txt 1 B`、`notes.txt 13 B` |
| D5 | 窗口档位可切换并持久 | ✅ | 截图：点 1M 后 `1M / 当前 1M / 已选`，离开再回来仍是 1M |
| D6 | 聊天一次往返（工具循环） | ⛔ **环境阻塞** | `giving up after 1 retries: network error: SocketTimeoutException`，两次复现 |
| D7 | 重试可见性 | ✅ | 截图：`网络抖动，正在重试…` 状态行出现（W3 修复未回归） |
| D8 | MCP 真机联调 | ⛔ **平台策略阻塞** | `error: transport_error`；根因 = manifest `usesCleartextTraffic="false"`，明文 `http://127.0.0.1` 被平台拒绝 |
| D9 | 1M 窗口真机行使 | ⛔ **vacuous** | 依赖 D6 成功；D6 不通则本项**不得**写成「未通过」 |
| D10 | 缓存命中行 / 压缩事件上屏 | ⛔ **vacuous** | 同上，需要一次成功请求 |

**D6 的排查过程（结论不是「网络不好」）**：同一台设备用浏览器打开
`https://openrouter.ai/api/v1/models` **能正常加载 JSON**，说明 DNS 与 HTTPS 可达；
而应用内请求稳定 `SocketTimeoutException`。因此不是「设备没网」，
而是**应用侧的连接/流式路径在这个网络下不收敛**。这与 W3-08 记录的现象一致，
本波**未能消除**，只是把「浏览器可达 / 应用不可达」这个对照补上了。

---

## 五、本轮 vacuous（不许写成通过）

| 项 | 为什么 |
|---|---|
| 1M 窗口设备侧行使 | 需要一次成功的 LLM 请求，D6 不通 |
| 缓存命中率上屏 | 同上（本波只有 PC 侧实测数据） |
| MCP 真机联调 | 明文 HTTP 被平台策略拒绝；本仓 `usesCleartextTraffic=false` 是 G15b 守卫要求的，**不为测试放开** |
| L2 摘要在真机上的实际触发 | 单测覆盖了接线，但真机没有跑过一次压缩事件 |
| 免费池「全面测试」 | 14 个里 12 个被账号隐私开关挡住，只覆盖 2 个 |

---

## 六、本波四条新铁律

1. 🔴 **「浏览器能开」不等于「应用能连」**。D6 的对照实验（浏览器加载同一域名成功、
   应用超时）把「网络问题」这个笼统归因拆成了「平台可达但应用路径不收敛」。
   下次遇到「请求失败」，先做这一步对照，再谈网络。
2. 🔴 **平台策略会先于你的代码拒绝你**。MCP 的 `transport_error` 花了三轮才定位到
   `usesCleartextTraffic=false` —— 而这条恰好是本仓守卫 G15b 自己在检查的。
   **判据依赖平台行为时，先把平台开关列出来**，不要从协议层开始排查。
3. 🔴 **「组件有测试」≠「组件被使用」**。`LayeredContextCompressor` 有 9 条单测，
   但 `ContextBudget` 根本没把它接进去时，那 9 条照样全绿。
   新增组件的交付定义必须包含**一条「被调用」的判据**，否则它只是躺在仓库里的代码。
4. 🔴 **守卫的边界是包目录，不是「意图」**。G18 只允许 `core-agent/loop/` 驱动引擎，
   `EngineBackedSummarizer` 一开始放在 `context/` 就直接判红。**新增引擎调用点前先想它住哪个包**，
   而不是先写完再让守卫告诉你。

---

## 七、挂账

| # | 项 | 备注 |
|---|---|---|
| W4-01 | 设备→OpenRouter 请求不收敛（承接 W3-08） | 已确认浏览器可达、应用超时；下一步换端点/换网络/看 TLS 握手 |
| W4-02 | MCP 真机联调 | 需要 https 夹具或显式 cleartext 例外；两者都是安全取舍，需单独裁决 |
| W4-03 | 1M 设备侧行使 | 阻塞于 W4-01 |
| W4-04 | 免费池 12/14 不可用 | 需账号持有人在 `openrouter.ai/settings/privacy` 打开「允许免费模型训练」 |
| W4-05 | C 组（首字节看门狗 / 等待期进度 / `cachedReported` 三态） | 设计在 `w4-context-design.md` §3.C，**本波未实施**（并行开发被 429 中断） |
| W4-06 | `feature-settings` 测试仍只有 1 条 | 本波新页已移到 `feature-tools` 并有 8 条；settings 自身仍是回归盲区 |
| W4-07 | 会话级 `maxContextTokens` 改动对已缓存 runner 不生效 | 需换端点或重启；实时生效要做 runner 失效机制 |
