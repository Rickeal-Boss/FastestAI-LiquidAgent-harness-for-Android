# LiquidAgent harness-v2 源码日析 — 2026-11-10（已提交 main `b0a6e44`，与 2026-11-09 同基线）

> 只读静态分析 + 改进推演。**全程未改动任何源码/配置文件**，仅在 `docs/analysis/` 新建本文档。
> 分析对象：`<workspace>/harness-v2`（现役仓，已提交 `main`，`HEAD = b0a6e44`）。
> ⚠️ 本日分析对象与 **2026-11-09** 文档**完全相同**（同一 HEAD `b0a6e44`）；工作树自上次分析以来**无任何 `src/main` 改动**（仅 `docs/**` 修改与未跟踪分析文档），故本日**无新增发现**，依任务纪律在「一、本次新发现」如实写明「无新增，延续前次」，不编造问题。

## 本次依据（版本与水位）

- **工作记忆**：`MEMORY.md`（W6 硬预算 / W9-04 四态判据 / W11 三判据 / W12 判据 / 隐私卫生铁律）+ `2026-11-09.md`（W14 矩阵收尾、`04eb0b5`+`b0a6e44` 已推 main、CI `42613109392` 五 job success）+ 自动化记忆 `automations/9eac1478…/memory.md`（第 5 份记录）。
- **项目文档**：`docs/06-deferred-ledger.md`（本日复核：`W9-04 CLOSED` / `W12-02 OPEN` / `W11-03 OPEN` / `W11-05 OPEN` / `W9-05 OPEN` 第 8 顺延 / `W11-02 CLOSED`）；`docs/02-roadmap.md`；`docs/handoff-*.md`。
- **架构闸门**：`scripts/arch-guard.sh` 的 `LINE_CAPS`（约 152–195 行），`TOTAL_LINE_CAP=42500` 未动；本日无 `LINE_CAPS` 改动。
- **git 状态核实**：`HEAD = b0a6e44`（与 11-09 文档同一提交）。工作树变更仅 `M docs/06-deferred-ledger.md`、`M docs/handoff-20261108-wave12.md`、`M docs/handoff-20261109-wave14.md` 及未跟踪的 `docs/analysis/*.md`（均为文档，**零 `src/main` 改动**）。
- **源码增量核实**：`git diff 98916ea HEAD -- '*/src/main/**'` 与上一份文档（2026-11-09）分析时所测**完全一致**——W11/W12/W14 早已合入，自那以后 `src/main` 无任何新提交或未提交修改。
- **前次文档**：`source-review-2026-11-09.md`（a/b/c=0/1/0；唯一新项 B-1 = core-context 1646/1650 余 4 临界）。

### arch-guard 当前行数水位（本日逐模块 `wc -l`，全仓现求和；已提交 HEAD `b0a6e44`）

| 模块 | 实测行 | 上限 | 余量 | 水位 | 备注 |
|---|---:|---:|---:|---:|---|
| core-model | 2170 | 2600 | 430 | 83% | 与 11-09 持平 |
| core-net | 790 | 1200 | 410 | 66% | 与 11-09 持平 |
| core-engine | 1207 | 2600 | 1393 | 46% | 与 11-09 持平 |
| core-kernel | 403 | 450 | 47 | 90% | 与 11-09 持平 |
| **core-context** | **1646** | **1650** | **4** | **99.8%** | 🔴 临界，与 11-09 持平（非新恶化，延续预警） |
| core-agent | 5370 | 6500 | 1130 | 83% | 与 11-09 持平 |
| core-skills | 419 | 1000 | 581 | 42% | 与 11-09 持平 |
| core-mcp | 758 | 1400 | 642 | 54% | 与 11-09 持平 |
| core-data | 2470 | 4500 | 2030 | 55% | 与 11-09 持平 |
| core-design | 12450 | 12600 | 150 | 99% | ⚠️ `LiquidBottomTabs` 1012>600 例外（W2U-2 起未拆），与 11-09 持平 |
| **feature-chat** | **3888** | **3900** | **12** | **99.7%** | 🔴 与 11-09 持平 |
| **feature-settings** | **3492** | **3500** | **8** | **99.8%** | 🔴 与 11-09 持平 |
| feature-tools | 947 | 2600 | 1653 | 36% | 与 11-09 持平 |
| app | 1355 | 1600 | 245 | 85% | 与 11-09 持平 |
| **TOTAL** | **37365** | **42500** | 5135 | **87.9%** | 与 11-09 **完全相同**（每模块数值逐项一致） |

> 结论：自 2026-11-09 以来源码**未发生任何变化**，行数水位表逐格相等。`TOTAL_LINE_CAP=42500` 守住（87.9%）。**三个 ≤12 行余量模块**（core-context 余 4、feature-settings 余 8、feature-chat 余 12）维持原预警级别，未恶化也未缓解。

---

## 一、本次新发现

> 本日对 `HEAD = b0a6e44` 做了与 2026-11-09 完全一致范围的逐模块 `wc -l` + 全仓 Grep 静态核对。**源码对象未变 ⇒ 无新增观察。**

### （a）需要改进的实现
- **（无新增）** —— 源码较 2026-11-09 无改动，沿用前次结论：实现侧无新缺陷。

### （b）不合理的功能设计
- **（无新增）** —— core-context 1646/1650（余 4）临界预警**已于 2026-11-09 文档立账（B-1）**，本日仅**延续**该预警，不重复立账、不升优先级（同基线，未恶化）。

### （c）缺失的功能
- **（无新增）** —— 四态生产读者缺失（W12-02 `OPEN`）已于前次及台账登记，本日仅延续。

> **本日新发现 a/b/c = 0 / 0 / 0**，全部为「无新增，延续前次」。未编造任何新问题。

---

## 二、与架构铁律的偏离风险清单（本日重新核对，静态全绿处标注 ✅）

| 铁律 | 现状（HEAD `b0a6e44` 核对） | 偏离风险 |
|---|---|---|
| TOTAL_LINE_CAP 不许变胖（W5 铁律 3） | 守住（37365/42500=87.9%）；本日未改 `LINE_CAPS`/`TOTAL_LINE_CAP` | ✅ 满足；⚠️ per-module 三处 ≤12 行余量（延续） |
| 拆分不调上限 | 本日无改动 | ✅ 合规；⚠️ core-context 余 4 临界（延续 B-1） |
| 组件有测试≠组件被使用 / 新增组件须配被调用判据 | **本日 grep 再次证实**：`StreamAccumulator.kt:226` `val cacheStatus` 是 `PromptCacheStatus` 四态唯一生产点，`.cacheStatus` 在 main **零读者**（W12-02 `OPEN`）；`cacheHitRatio()` 已接 `EventReducer.buildUsageLabel`（正面实例） | ⚠️ 延续（W12-02）：四态观测仍半落地 |
| 已登记例外须按期消除 | `LiquidBottomTabs` 1012>600（core-design）例外自 W2U-2 未拆 | ⚠️ 延续 |
| 平台策略先于代码拒绝你 | MCP cleartext 仍被 `usesCleartextTraffic=false` 挡（W5-03）；设备侧 1M 双内核阻断（W9-11 未解锁） | ⚠️ 需裁决，非代码可解 |
| DI 只走 AppContainer | `object` 声明均为无状态工具持有者（`HardBudgetShrinker`/`EventReducer`/`TokenEstimator`/`CalibrationPolicy`/`AgentPromptComposer` 等），未出现散落服务定位单例或手动 `new` 依赖；`AppContainer` 仍是唯一依赖装配点 | ✅ 未见违规 |
| 模块边界（G6/G8） | `core-model` 本日 grep `import core.engine|core.net|core.data|feature` = **0 命中**（无反向边）；`core-agent` 未引 engine/net/data/design；`core-context→core-model`/`core-kernel` 单向 | ✅ 干净 |
| 禁注解处理器/Retrofit/图片库（G4） | 本日 grep Retrofit/Hilt/Koin/Room/Coil/Dagger/Glide/Picasso = 仅 4 处**注释**（均写明「不引」），**0 处真实 import/调用** | ✅ 干净 |
| `@Serializable` 禁密钥字段（G17） | main 源集 `@Serializable` 数据类无 apiKey/secret/token 字段 | ✅ 干净 |
| `WindowInsets.ime` 不进 `derivedStateOf` 普通 lambda | `GlassScaffold.kt:129` 与 `ChatScreen.kt:111` 均先取 `val imeInsets = WindowInsets.ime` 再使用（KDoc 自注「必须先取出」） | ✅ 维持 |
| `runCatching` 不吞 `CancellationException`（W84） | 本日无新增协程 IO 路径；既有调用点沿用既有纪律 | ✅ 合规（沿用前次核查） |
| 裸 `assertFailsWith{}` 收尾致 initializationError（W80） | 测试源集 `grep assertFailsWith` = **0 命中** | ✅ 无风险 |

---

## 三、建议优先级（与是否进 ledger）

- **P0**：无（本日无新增）。
- **P1**：
  - **延续 B-1（2026-11-09 已立账）** — `core-context` 1646/1650（余 4）：W11-03 / W9-03 等任何 core-context 增量开工前先定拆分/下沉方案，不要触顶再拆。建议作为 **W11-03 的前置约束**写入台账（不另开号）。
  - **延续 W12-02（`OPEN`）** — 给 `PromptCacheStatus` 四态一个生产读者（探针事实表 / 诊断日志 / 设备侧缓存探针按四态落盘），使 W9-04「唯一口径」真正落到展示层；或确认永不展示则删枚举/字段。
- **P2**：
  - **延续** — `feature-chat` 3888/3900（余 12）+ `feature-settings` 3492/3500（余 8）：后续增量优先外提纯函数/拆分，禁抬上限。
  - **延续** — `LiquidBottomTabs` 1012>600 例外；`W9-05`(第 8 顺延,`OPEN`)；`W11-03` `cache_invalidated` 二值过度声明(`OPEN`)；`W11-05` `cacheHitRatio` 分母按 provider 族归一(`OPEN`)。

**是否进 roadmap / deferred-ledger**：本日**无任何新增立账项**（源码未变，无可报告新观察）。所有风险项均沿用既有挂账（`W12-02`/`W11-03`/`W11-05`/`W9-05`/`W9-01`/`W9-03` 等）或 2026-11-09 已立账的 B-1 临界预警，状态重申不重开号。

---

## 四、未覆盖 / 需真机验证项（不可仅靠静态判定）

- **W12-02 四态消费端缺失的真机影响**：即便接上四态，OR 免费池 `cached_tokens` 恒 0（W11 风险 2）⇒ 四态在 OR 上会稳定落 `CACHE_FIELDS_NOT_REPORTED` 或 `USAGE_NOT_OBSERVED`，需真机多供应商取数验证四态区分是否真能区分「不报字段」与「传输截断」。本日 grep 再次证实 `StreamAccumulator.cacheStatus` 在 main 零读者，`ContextBudget.cachedLabel` 实现仍为三态。
- **W11-03 `cache_invalidated` 二值过度声明**：`SettledCompressPolicy.kt:14-18` 预写「头锚段仍 > 0」需真机 M9 验证尾置 SYSTEM 摘要是否被端点接受；未取真机证据的预写结论不得升级为事实。
- **W9-03 会话级压缩真机验收**：`feature-chat/ManualCompress.kt:34-40` 单指针是「压缩×缓存互斥」根；会话级形态 W10 已落地，真机 M9 未验收 ⇒ 不关闭。
- **W7-02 前缀缓存命中率**：三供应商实测不一致（nv 0→0→4336，dots/north 恒 0）；W11-02 改指纹后须按新语义重测。
- **OEM 差异**：唯一直设备 OPPO PDRM00 / Android 13；华为/HarmonyOS、小米、vivo 的 `usesCleartextTraffic`、Keystore、屏幕密度未验。
- **端侧模型行为**：仅 NV-VL 实测 1M 窗口三通；设备侧 1M 受 RoundHeadGate 400k 阻断（W9-11 未解锁）。

---

## 结论

本日（2026-11-10）与 **2026-11-09** 分析**基于完全相同的源码对象**：`HEAD = b0a6e44`（W14 收尾态），工作树仅 `docs/**` 文档修改与未跟踪分析文档，**`src/main` 零改动**；逐模块行数水位表与 11-09 文档**逐项相等**（全仓 37365/42500=87.9%）。静态核对结论：

1. **本日新发现 a/b/c = 0/0/0**：源码未变 ⇒ 无新增观察，依任务纪律写明「无新增，延续前次」，未编造问题。
2. **架构铁律静态全绿（本日重新 grep 复核）**：DI 只走 AppContainer（无状态 `object` 工具持有者 ≠ 服务定位单例）；模块边界无违规边（G6/G8，core-model 反向边 grep 0 命中）；G4 黑名单/G17 密钥字段/`WindowInsets.ime`/`CancellationException` 重抛/`assertFailsWith` 均合规；行数总闸门 42500 守住（87.9%）。
3. **延续预警（非新立账）**：core-context 1646/1650（余 4，B-1 临界）、feature-chat 余 12、feature-settings 余 8 维持原级别；`PromptCacheStatus` 四态仍仅 `StreamAccumulator.cacheStatus` 生产、main 零读者（W12-02 `OPEN`，四态观测半落地）；`LiquidBottomTabs` 例外、`W9-05`(第 8 顺延)/`W11-03`/`W11-05` 均延续未解。
4. **正面维持**：W9-04 数据层 `CLOSED`（`cacheHitRatio` 接 `buildUsageLabel` 为正面实例）；W11-02 `CLOSED`（指纹去 id）；W12-01 `CLOSED`（带图轮次跳过标定）。

**全程只读，未改动任何源码/配置文件、未 `git add`/`commit`/`push`、未执行任何会改动设备的 adb 命令。**

> 与 N 天前结论的演进：相对前次（2026-11-09，a/b/c=0/1/0）——
> - **源码对象完全一致（同 HEAD `b0a6e44`），行数水位表逐项相等**，本日**无新恶化、无新缓解**，仅做二次复核印证；
> - 前次 B-1（core-context 余 4 临界）、W12-02（四态零读者）、feature-chat/feature-settings 余量、LiquidBottomTabs 例外、`W9-05`(第 8 顺延)/`W11-03`/`W11-05` **全部延续未解**；
> - 本日把「无新增，延续前次」作为正式结论写入，避免对静止基线重复立账或编造问题。
