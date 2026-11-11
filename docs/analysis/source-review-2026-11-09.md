# LiquidAgent harness-v2 源码日析 — 2026-11-09（W14 已合 main，HEAD `b0a6e44`）

> 只读静态分析 + 改进推演。**全程未改动任何源码/配置文件**，仅在 `docs/analysis/` 新建本文档。
> 分析对象：`<workspace>/harness-v2`（现役仓，已提交 `main`，`HEAD = b0a6e44`）。
> ⚠️ 本日分析对象 = **已提交 main（W14 收尾态）**，与前次同日文档 `source-review-2026-11-07.md` 分析的 **W11 未提交工作树（HEAD `98916ea` + 在途 W11 改动）** 存在实质差异：W11 已提交（`504ed40`）、W12（`53d1465`）+ W14（`04eb0b5`/`b0a6e44`）已合入 main。

## 本次依据（版本与水位）

- **工作记忆**：`MEMORY.md`（含 W6 硬预算 / W9-04 四态判据 / W11 三判据 / W12 判据 / 隐私卫生铁律）+ `2026-11-09.md`（W14 跨免费模型 × 跨上下文档位真机矩阵收尾、`04eb0b5`+`b0a6e44` 已推 main、CI `42613109392` 五 job success）+ `2026-11-08.md`（W12 `53d1465`+`feb2988` 已推 main）+ 自动化记忆 `automations/9eac1478…/memory.md`。
- **项目文档**：`docs/06-deferred-ledger.md`（W9-04 `CLOSED`、W12-02 `OPEN`、W11-03 `OPEN`、W9-05 第 8 顺延、W11-05 `OPEN`）；`docs/02-roadmap.md`；`docs/handoff-*.md`。
- **架构闸门**：`scripts/arch-guard.sh` 的 `LINE_CAPS`（约 152–195 行），`TOTAL_LINE_CAP=42500` 未动；新增 W12 `53d1465` 未改 `LINE_CAPS`/`TOTAL_LINE_CAP`（W14 仅 `docs/**` + `scripts/fixtures/` 非源码）。
- **git 状态核实**：工作树干净，仅 `?? docs/analysis/source-review-2026-11-07.md`（前次未跟踪文档）。`HEAD = b0a6e44`，来源 `98916ea`（W10 基线）的 `merge-base --is-ancestor` 校验为 **YES**。
- **源码增量核实**：`git diff 98916ea HEAD -- '*/src/main/**'` 仅 13 文件 / +238/−22；其中 W11（`504ed40`）含 W9-04 四态全链路 + W11-02 指纹去 `message.id`，W12（`53d1465`，唯一代码改动 = P1-2 带图轮次跳过标定）新增 `core-agent/.../loop/CalibrationPolicy.kt`（31 行）+ `GenerationRound.kt` 改调 + 红态用例 + `EXPECTED_TESTS` 798→799。**W14 无 `src/main` 改动**（仅 fixtures/文档）。
- **前次文档**：`source-review-2026-11-07.md`（分析 W11 未提交树，a/b/c=1/3/1；其 B-2 `feature-chat` 余 12、B-1 四态装饰性、A-1/C-1 四态消费端缺失）。

### arch-guard 当前行数水位（本日 `find …/src/main/**/*.kt` 逐模块 `wc -l`，全仓现求和；已提交 HEAD `b0a6e44`）

| 模块 | 实测行 | 上限 | 余量 | 水位 | 备注 |
|---|---:|---:|---:|---:|---|
| core-model | 2170 | 2600 | 430 | 83% | |
| core-net | 790 | 1200 | 410 | 66% | |
| core-engine | 1207 | 2600 | 1393 | 46% | |
| core-kernel | 403 | 450 | 47 | 90% | |
| **core-context** | **1646** | **1650** | **4** | **99.8%** | 🔴 **NEW 临界**：较 11-07 文档同模块 1622/余 28 进一步收紧至余 4 |
| core-agent | 5370 | 6500 | 1130 | 83% | |
| core-skills | 419 | 1000 | 581 | 42% | |
| core-mcp | 758 | 1400 | 642 | 54% | |
| core-data | 2470 | 4500 | 2030 | 55% | |
| core-design | 12450 | 12600 | 150 | 99% | ⚠️ `LiquidBottomTabs` 1012>600 例外自 W2U-2 未拆 |
| **feature-chat** | **3888** | **3900** | **12** | **99.7%** | 🔴 全仓第二紧（与 11-07 文档持平） |
| **feature-settings** | **3492** | **3500** | **8** | **99.8%** | 🔴 全仓最紧（与 11-07 文档持平） |
| feature-tools | 947 | 2600 | 1653 | 36% | |
| app | 1355 | 1600 | 245 | 85% | |
| **TOTAL** | **37365** | **42500** | 5135 | **87.9%** | 较 11-07 文档 37316 +49（W12 小增 + W14 纯文档） |

> 全仓总量 42500 守住（87.9%，满足 W5 铁律 3「不许变胖」）。但 **per-module 高位带继续成为瓶颈**：现出现 **三个** 距天花板 ≤12 行的模块（core-context 余 4、feature-settings 余 8、feature-chat 余 12），这正是自动化记忆已预警的「per-module 天花板反复成为瓶颈（拆分当扩容的 per-module 翻版）」。

---

## 一、本次新发现

> 全部来自对 `HEAD = b0a6e44`（已提交 main）的逐模块 `wc -l` + 全仓 Grep 静态核对，并与 11-07 文档水位逐项比对。
> 本日 **新发现 a/b/c = 0 / 1 / 0**（唯一新项 = core-context 行数水位临界）；另有 **延续项**（四态未接消费端、feature-chat/feature-settings 余量）在「二、三」与文末「与 N 天前结论的演进」中重申，不重复立账。

### （a）需要改进的实现

- **（无新增）** 本波源码增量（W12 `CalibrationPolicy.kt` + `GenerationRound.kt` 改调）经核查设计自洽：`calibrationCharsForRound` 在带图轮次返回 0 使 `recordReportedPrompt` 沿用上一轮纯文本标定、首轮即带图保持默认上界（安全侧），与 W11「标定放大 3.3 倍」对偶修低估方向；`HardBudgetShrinker.promptChars` 与 `calibrationChars` 共用 `textChars`/`attachmentChars`（非两把独立尺），无量纲分裂。未引入新实现缺陷。

### （b）不合理的功能设计（与架构铁律 / 设备现实冲突处）

- **B-1（NEW）— `core-context` 行数水位已至 1646/1650（余 4），成为全仓最紧的有效模块（临界升级）**
  - 文件/水位：`core-context/src/main/**`（9 文件，结构见下）。
  - 现象：本日实测 **1646 / 1650**，余 **4**（11-07 文档分析 W11 未提交树时为 1622/1650 余 28；无论其中多少是工作树漂移或 W12/W14 净增，当前已实质逼近 1650 天花板）。其中 `SettledCompressPolicy.kt`（89 行，承载 W11-03 `cache_invalidated` 二值过度声明修复）、`LayeredContextCompressor.kt`(314)/`ContextCompressor.kt`(340)/`StructuredSummary.kt`(292) 等压缩/摘要核心均在本模块。
  - 风险：余量仅 4 行，**任何** core-context 后续改动（含 W11-03 真机验收后的 `partial / head_anchor_preserved` 三态改写、`SettledCompressPolicy.kt:14-18` 预写逻辑修正、W9-03 会话级压缩真机回填）都会立刻撞 1650 红线。而 `LINE_CAPS` 注释明确「触顶处理永远是拆分，不是调大 `TOTAL_LINE_CAP`」。属「拆分当扩容」per-module 翻版的临界预警——总闸门安全，但单模块已无呼吸空间。
  - 建议：在 W11-03 / W9-03 等任何 core-context 增量开工**前**，先制定拆分/下沉方案（如把 `ContextCompressor`/`LayeredContextCompressor` 的纯函数下沉 core-model，或把 `StructuredSummary` 子组件外提），不要等到触顶再拆。优先级高于这些功能本身的 UX/语义改动。

### （c）缺失的功能

- **（无新增）** 见延续项 C-1（四态消费端缺失，属 W12-02 未消项）。

---

## 二、与架构铁律的偏离风险清单（本日重新核对，静态全绿处标注 ✅）

| 铁律 | 现状（HEAD `b0a6e44` 核对） | 偏离风险 |
|---|---|---|
| TOTAL_LINE_CAP 不许变胖（W5 铁律 3） | 守住（37365/42500=87.9%）；W12/W14 未抬上限 | ✅ 满足；但 **per-module 三处 ≤12 行余量**（B-1 / feature-settings / feature-chat） |
| 拆分不调上限 | W12 未改 `LINE_CAPS`/`TOTAL_LINE_CAP`；W14 纯文档 | ✅ 合规；⚠️ B-1：core-context 余 4 已临界 |
| 组件有测试≠组件被使用 / 新增组件须配被调用判据 | **W9-04 四态 `PromptCacheStatus`/`cacheStatus` 仍测全绿但无生产消费者**（W12-02 `OPEN`）；`cacheHitRatio` 已接 `EventReducer.buildUsageLabel`（正面实例） | ⚠️ 延续（A-1/B-1/C-1）：四态观测半落地；`cacheHitRatio` 是正面实例 |
| 已登记例外须按期消除 | `LiquidBottomTabs` 1012>600（core-design）例外自 W2U-2 未拆；feature-settings +100（W8）一次性例外已用尽 | ⚠️ 延续 |
| 平台策略先于代码拒绝你 | MCP cleartext 仍被 `usesCleartextTraffic=false` 挡（W5-03）；设备侧 1M 双内核阻断（W9-11 未解锁）；音频 NV `/v1/audio/speech` 404 | ⚠️ 需裁决，非代码可解 |
| DI 只走 AppContainer | `object` 声明均为无状态工具持有者（`HardBudgetShrinker`/`EventReducer`/`TokenEstimator`/`CalibrationPolicy` 仅 `internal fun`/`AgentPromptComposer` 等），未出现散落服务定位单例或手动 `new` 依赖；`AppContainer` 仍是唯一依赖装配点 | ✅ 未见违规 |
| 模块边界（G6/G8） | `core-model` 未反向引 net/engine/data/feature；`core-agent` 未引 engine/net/data/design；`core-context→core-model`/`core-kernel` 单向 | ✅ grep 零命中违规边 |
| 禁注解处理器/Retrofit/图片库（G4） | `grep` Retrofit/Hilt/Koin/Room/Coil/Dagger/Glide/Picasso = 0 命中；仅注释禁止 | ✅ 干净 |
| `@Serializable` 禁密钥字段（G17） | main 源集 `@Serializable` 数据类无 apiKey/secret/token 字段；`EndpointSecretStore`/`SecretProvider` 是运行时 DI 对象非序列化数据类；`TokenUsage`/`PromptCacheStatus` 为普通序列化数据类 | ✅ 干净 |
| `WindowInsets.ime` 不进 `derivedStateOf` 普通 lambda | `GlassScaffold.kt:129` 与 `ChatScreen.kt:111` 均先取 `val imeInsets = WindowInsets.ime` 再使用（KDoc 自注「@ReadOnlyComposable 必须先取出」） | ✅ 维持 |
| `runCatching` 不吞 `CancellationException`（W84） | 本波无新增协程 IO 路径；既有调用点（`AppContainer`/`TurnContext`/`NetMcpTransport`/`SessionImportParser` 等）沿用既有纪律 | ✅ 合规（沿用前次核查，本轮无新增挂起点） |
| 裸 `assertFailsWith{}` 收尾致 initializationError（W80） | 测试源集 `grep assertFailsWith` = 0 命中 | ✅ 无风险 |

---

## 三、建议优先级（与是否进 ledger）

- **P0**：无新增。
- **P1**：
  - **B-1（NEW）** — `core-context` 1646/1650（余 4）：W11-03 / W9-03 等任何 core-context 增量开工前先定拆分/下沉方案，不要触顶再拆。建议作为 **W11-03 的前置约束**写入台账（与既有「触顶先下沉」同口径，不另开号）。
  - **延续 A-1 / B-1(旧) / C-1（四态观测半落地）**：把 `PromptCacheStatus` 四态接进 `ContextBudget` 用量行（或诊断日志 / 设备侧探针），使 W9-04「唯一口径」真正落到展示层；或确认永不展示则删枚举/字段。**依 ledger 已拆为 `W12-02`（`OPEN`），不新开挂账**。
- **P2**：
  - **延续 B-2（旧）/ 本波同型预警** — `feature-chat` 3888/3900（余 12）+ `feature-settings` 3492/3500（余 8）：后续增量优先外提纯函数/拆分，禁抬上限。
  - **延续** — `LiquidBottomTabs` 1012>600 例外、`W9-05` journal/FreezeClock（第 8 顺延，`OPEN`）、`W11-03` `cache_invalidated` 二值过度声明（`OPEN`）、`W11-05` `cacheHitRatio` 分母按 provider 族归一（`OPEN`）。

**是否进 roadmap / deferred-ledger**：本日 **新增唯一建议立账项 = B-1 的「core-context 余 4 临界」**，建议作为 W11-03 前置约束写入台账/roadmap（不另开号）；四态半落地沿用既有 `W12-02`（`OPEN`）。其余为既有挂账状态重申。

---

## 四、未覆盖 / 需真机验证项（不可仅靠静态判定）

- **W12-02 四态消费端缺失的真机影响**：即便接上四态，OR 免费池 `cached_tokens` 恒 0（W11 风险 2）⇒ 四态在 OR 上会稳定落 `CACHE_FIELDS_NOT_REPORTED` 或 `USAGE_NOT_OBSERVED`，需真机多供应商取数验证四态区分是否真能区分「不报字段」与「传输截断」。当前 `promptCacheStatus(` 在 main 仅定义 + 文档注释、`.cacheStatus` 零读者（grep 证实），`ContextBudget.cachedLabel` 实现仍为三态（源码 `if (ctx.state.promptTokens == 0) "n/a" else (ctx.state.cachedPromptTokens?.toString() ?: "n/a")`）。
- **W14 真机矩阵引申的未取证据**：`W11-03` `cache_invalidated` 二值过度声明（`SettledCompressPolicy.kt:14-18` 预写「头锚段仍 > 0」）需真机 M9 验证尾置 SYSTEM 摘要是否被端点接受；`W7-03` 手动压缩 / 会话导入真机验收（`dropped=N` 首次真值已取但分隔卡/`cache_invalidated=true` 未取全）；`W7-01` 32K 档压缩真机重放（`cap = 27904 × floor 1.8 = 50227` 前置熔断已证，但免费模型该规模 `SocketTimeoutException` 须换 NV 端点）。
- **W7-02 前缀缓存命中率**：三供应商实测不一致（nv 0→0→4336，dots/north 恒 0）；W11-02 改指纹后须按新语义重测，不得沿用旧基线。
- **OEM 差异**：唯一直设备 OPPO PDRM00 / Android 13；华为/HarmonyOS、小米、vivo 的 `usesCleartextTraffic`、Keystore、屏幕密度未验。
- **端侧模型行为**：仅 NV-VL 实测 1M 窗口三通；设备侧 1M 受 RoundHeadGate 400k 阻断（W9-11 未解锁）；多模型 × 功能交叉格未取新证据。

---

## 结论

本日（2026-11-09，已提交 main `b0a6e44`）与前次同日文档（2026-11-07，W11 未提交树）的**实质差异 = 工作树已推进到 W11 提交 + W12 + W14 合入 main**，核心变化是 **W9-04 缓存四态 + 厂商字段归一 + `cacheHitRatio` 分母钉死**（W11 已落）、**W11-02 `renderHistoryHead` 指纹去 `message.id`**（W11 已落）、**W12 `CalibrationPolicy` 带图轮次跳过标定**（W12 已落）。静态核对结论：

1. **架构铁律静态全绿**：DI 只走 AppContainer（无状态 `object` 工具持有者 ≠ 服务定位单例）；模块边界无违规边（G6/G8）；G4 黑名单/G17 密钥字段/`WindowInsets.ime`/`CancellationException` 重抛/`assertFailsWith` 均合规；行数总闸门 42500 守住（87.9%）。**W12/W14 未引入任何新违规。**
2. **本日新发现 a/b/c = 0/1/0**：唯一新项 = **B-1：`core-context` 1646/1650（余 4）临界升级**（较 11-07 文档同模块余 28 进一步收紧），`SettledCompressPolicy`(W11-03) 等压缩核心居于此模块，任何后续增量须先拆分。
3. **延续项重申**：四态 `PromptCacheStatus`/`cacheStatus` 仍**生产但零读者**（W12-02 `OPEN`，A-1/B-1/C-1 同源根因仍未消）；`feature-chat` 3888/3900（余 12）、`feature-settings` 3492/3500（余 8）维持全仓最紧两档。
4. **正面演进**：W11-02 已 `CLOSED`（指纹去 id，修正前次「message.id 假漂移」观察）；W9-04 数据层（`cacheWrite` 全链路透传 + 厂商字段归一 + `cacheHitRatio` 已接 `buildUsageLabel`）已 `CLOSED`，`cacheHitRatio` 是四态之外的正面实例；W12 `CalibrationPolicy` 经本地闸门 + CI 全绿。

**全程只读，未改动任何源码/配置文件、未 `git add`/`commit`/`push`、未执行任何会改动设备的 adb 命令。**

> 与 N 天前结论的演进：相对前次同日文档（2026-11-07，a/b/c=1/3/1）——
> - **行数水位表由 W11 未提交态更新为已提交 main（`b0a6e44`）**（全仓 37365/42500=87.9%，+49）；**新出现第三个 ≤12 行余量模块 = `core-context` 余 4**（前次仅 feature-settings 余 8、feature-chat 余 12 两档，core-context 当时报余 28），本波升级为全仓最紧有效模块；
> - 前次 **A-1（`PromptCacheStatus` 四态已生产、`ContextBudget` 用量行却仍走老三态、无人读取）/ B-1（四态装饰性设计）/ C-1（四态消费端缺失）** 经 W12 复核 **仍 `OPEN`（拆为 `W12-02`）**，本日 grep 再次证实（`.cacheStatus` main 零读者、`cachedLabel` 实现仍三态）；
> - 前次 B-2 `feature-chat` 余 12、feature-settings 余 8、《LiquidBottomTabs》例外、`W9-05`(第 8 顺延)/`W11-03`/`W11-05` **均延续未解**；
> - W11-02(`renderHistoryHead` 去 id) 已由「新观察项」升级为 `CLOSED` 正面实例；W9-04 数据层 `CLOSED` 且 `cacheHitRatio` 接 `buildUsageLabel` 为正面实例。
