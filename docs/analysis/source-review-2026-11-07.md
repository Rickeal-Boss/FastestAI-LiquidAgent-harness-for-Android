# LiquidAgent harness-v2 源码日析 — 2026-11-07（W11 未提交工作树）

> 只读静态分析 + 改进推演。**全程未改动任何源码/配置文件**，仅在 `docs/analysis/` 新建本文档。
> 分析对象：`<workspace>/harness-v2`（现役仓）。
> ⚠️ 本日分析对象 = **W11 未提交工作树**（HEAD=`98916ea` 即 W10 已推 `main`，其上还有 23 文件 +493/−48 的 W11 改动未提交）。
> 前次同系列文档 = `source-review-2026-11-06-2.md`（当时 a/b/c=3/2/1，最高优先 B1 = feature-settings 余 8）。

## 本次依据（版本与水位）

- **工作记忆**：`MEMORY.md` + `2026-11-07.md`（W10 收口 `98916ea` / CI `42365515962` 五 job success；W11 第 0 车补完 W9-01/W9-04、W11-02 关、W9-05 第 7 顺延）+ `2026-11-06.md` + 自动化记忆 `automations/9eac1478…/memory.md`。
- **项目文档**：`docs/06-deferred-ledger.md`（W9-04 标 `CLOSED`、W11-02 `CLOSED`、W11-03 `OPEN`、W9-05 第 7 顺延）、`docs/notes/w11-plan.md`（定位以符号为准，行号锚 `HEAD:98916ea`、工作树已漂移）、`docs/handoff-20261107-wave10.md` §九。
- **架构闸门**：`scripts/arch-guard.sh` 的 `LINE_CAPS`（152–195 行），`TOTAL_LINE_CAP=42500` 未动；`VACUOUS_BASELINE=1`。
- **git 状态核实**：`HEAD = 98916ea`；工作树含 **W11 未提交改动** —— 改动落在 `core-model`/`core-engine`/`core-agent`(loop)/`core-context`/`feature-chat` 的 `**/src/main/**`，以及若干 `*Test.kt`、`docs/`、`build.yml`(M)。**本日分析对象即该 W11 未提交工作树**，与前次文档（W9 工作树）存在实质差异（W10 已合入 + W11 在途）。
- **前次文档**：`source-review-2026-11-06-2.md`（a/b/c=3/2/1；延续项 A1/A2/A3/B1/B2/C1 + 已闭环 A-3/F-1）。

### arch-guard 当前行数水位（本日 `find …/src/main/**/*.kt` 逐模块 `wc -l`，全仓现求和；W11 未提交态）

| 模块 | 实测行 | 上限 | 余量 | 水位 | 备注 |
|---|---:|---:|---:|---:|---|
| core-model | 2174 | 2600 | 426 | 84% | W10+W11 +136 |
| core-net | 790 | 1200 | 410 | 66% | |
| core-engine | 1207 | 2600 | 1393 | 46% | |
| core-kernel | 403 | 450 | 47 | 90% | |
| **core-context** | **1622** | **1650** | **28** | **98%** | ⚠️ W11 +38，比前次 1584/余66 收紧；比 w11-plan 陈旧锚「余62」更紧 |
| core-agent | 5341 | 6500 | 1159 | 82% | |
| core-skills | 419 | 1000 | 581 | 42% | |
| core-mcp | 758 | 1400 | 642 | 54% | |
| core-data | 2470 | 4500 | 2030 | 55% | W10 +82 |
| core-design | 12450 | 12600 | 150 | 99% | |
| **feature-chat** | **3888** | **3900** | **12** | **99.7%** | 🔴 **NEW 临界**：比前次 3777/余123 收紧；比 w11-plan 陈旧锚「余27」更紧 |
| **feature-settings** | **3492** | **3500** | **8** | **99.8%** | 🔴 全仓最紧（W11 零改动，维持前次） |
| feature-tools | 947 | 2600 | 1653 | 36% | |
| app | 1355 | 1600 | 245 | 85% | |
| **TOTAL** | **37316** | **42500** | 5184 | **87.8%** | W10+W11 净 +393（前次 36923） |

> 相对前次同日文档（W9 口径）：全仓总量仍守住 42500（87.8%，满足 W5 铁律 3「不许变胖」）；
> 但 **per-module 高位带进一步收紧** —— 现出现 **两个** 距天花板 ≤12 行的模块（feature-settings 余 8、feature-chat 余 12），
> 而前次文档只报了 feature-settings 一个。core-context 也逼近（余 28）。
> 注意：本表数值比 `w11-plan.md` §六 C 写的「feature-chat 余 27 / core-context 余 62」**更紧**，印证该计划文件自身声明的「工作树已漂移、行号锚以符号为准」。

---

## 一、本次新发现

> 全部来自对 W11 未提交工作树的 `git diff` + 全仓 Grep 静态核对。
> 每条含文件:行号、现象、风险、建议（绝不附代码改动）。新发现 a/b/c = **1 / 3 / 1**（其中 A-1/B-1/C-1 同源 = 「四态缓存观测建而未用」，按三视角分列）。

### （a）需要改进的实现

- **A-1 — 四态缓存观测 `PromptCacheStatus` 已生产、却无读取点；用量行仍输出老三态字符串**
  - 文件：`core-model/.../StreamAccumulator.kt:230,254`（生产 `cacheStatus = promptCacheStatus(...)`，默认 `USAGE_NOT_OBSERVED`）与 `core-agent/.../loop/ContextBudget.kt:343-352`（`cachedLabel()` 仍输出 `"n/a"` / 命中数 三态字符串，KDoc 仅 *提及*「要区分二者请读 `promptCacheStatus(cached, usageObserved)`」但代码从不调用它）。
  - 现象：全仓 Grep `.cacheStatus` / `promptCacheStatus(` / `PromptCacheStatus.` 在 **main 源集** 仅出现在「定义 + StreamAccumulator 生产点」；`ContextBudget.cachedLabel`（agent 循环里真正渲染用量行的函数）完全没接四态。即：开发者**自己已在 KDoc 里写明「应改用四态区分」，但实现仍走老三态**。
  - 风险：W9-04 的核心价值（区分「整个 usage 未到」与「usage 到了但没报缓存字段」）只停留在枚举与单测里，`cacheStatus` 字段被赋值后**无任何 main 代码读取**。这正是 W4 铁律「组件有测试 ≠ 被使用 / 新增组件须配被调用判据」的同型缺口。
  - 建议：把 `ContextBudget.cachedLabel` 从「`null`→`n/a` / 命中数」三态字符串，改为真正消费 `promptCacheStatus(cached, usageObserved)` 的四态渲染（HIT / MISS / CACHE_FIELDS_NOT_REPORTED / USAGE_NOT_OBSERVED 各自不同文案），让 W9-04 的「唯一口径」落到展示层。属低风险改进，不触架构闸门（core-agent 余 1159）。

### （b）不合理的功能设计（与架构铁律 / 设备现实冲突处）

- **B-1 — `PromptCacheStatus` 四态是「声明了 observability 却未交付」的装饰性设计**
  - 文件：`core-model/.../Generation.kt:96-104`（`enum PromptCacheStatus` + `fun promptCacheStatus`）+ `StreamAccumulator.kt:230,254`。
  - 现象：`PromptCacheUsageTest` 8 条 + `StreamAccumulatorTest` 2 条把四态测全绿，但**生产消费点缺失**（见 A-1）：`cacheStatus` 被写、从未被读。台账 `W9-04` 写「四态只是探针，台账:99 按事实表验收」，却未在任何 UI / 日志 / 台账输出四态。
  - 风险：与 W4 铁律同型 ——「测试全绿」≠「口径被验证落地」。四态枚举给人「缓存可观测性已具备」的错觉，实际任何运维/真机排查者看到的仍是老三态，无法区分「端点不报字段」与「传输/末帧截断」。
  - 建议：要么按 A-1 把四态接进 `ContextBudget` 用量行（推荐，零新增架构风险），要么若确认永不展示则**删除该枚举与 `cacheStatus` 字段**（避免「声明但未消费」的装饰字段，与 C1 `SamplingParams.reasoningEffort` 同处理）。不要保留「测过却不被读」的中间态。

- **B-2 — `feature-chat` 行数水位已至 3888/3900（余 12），成为全仓第二紧模块（NEW 临界）**
  - 文件/水位：`feature-chat` 实测 **3888 / 3900**，余 **12**（前次文档为 3777/3900 余 123；W11 `EventReducer.kt` 等逻辑压入致 +111）。`w11-plan.md` §六 C 写的「余 27」是漂移前的陈旧锚，当前实测更紧。
  - 风险：余量仅 12 行，任何 feature-chat 改动（含 W11-01 R2 分隔卡、`ChatMessageList` 子组件拆分）都会立刻撞 3900 红线。`w11-plan` 已明确「触顶先下沉，禁调 `LINE_CAPS`」；但当前水位下，连「先拆」的余地都极小。
  - 建议：W11-01（R2 分隔卡）开工前**必须**先制定 feature-chat 的拆分/下沉方案（如把 `ChatMessageList` 子组件抽到新文件、或把 `EventReducer` 的部分纯函数下沉 core-model），不要等到触顶再拆。优先级高于 W11-01 本身的 UX 改动。

- **B-3 — `core-context` 行数水位收紧至 1622/1650（余 28）**
  - 文件/水位：`core-context` 实测 **1622 / 1650**，余 **28**（前次文档 1584/1650 余 66；W11 `HardBudgetShrinker.kt`+32、`PromptPrefix.kt`+14 共 +46，净 +38）。比 `w11-plan` 锚「余 62」更紧。
  - 风险：虽未触顶，但余量已低于 2%；W11 后续的 `SettledCompressPolicy` / `SwitchGuard` 相关增量若落 core-context，会迅速顶到 1650。属「拆分当扩容」的 per-module 翻版预警。
  - 建议：core-context 后续增量优先复用既有文件、外提纯函数到 core-model，或预先规划子组件拆分；不要为容纳新功能临时抬高 `LINE_CAPS`（TOTAL_LINE_CAP=42500 一律不动）。

### （c）缺失的功能

- **C-1 — 四态缓存观测的「消费端」完全缺失**
  - 文件：`core-agent/.../loop/ContextBudget.kt:343-352`（`cachedLabel` 仅为三态字符串）+ 全仓无任何 UI / 日志 / 台账渲染 `PromptCacheStatus`。
  - 现象：W9-04 交付了四态的**数据生产**（`cacheWriteTokens` 全链路透传 + 厂商字段归一 + `cacheHitRatio` 分母钉死，且 `cacheHitRatio` 已接进 `EventReducer.buildUsageLabel` 的命中率百分比），但四态**语义**（`USAGE_NOT_OBSERVED` vs `CACHE_FIELDS_NOT_REPORTED`）从不在任何用户可见/运维可读处出现。
  - 风险：`cacheHitRatio` 把「usage 没到」和「字段没报」都塌缩成 `null`（0.0%），于是排查时仍无法区分「传输/末帧问题」与「端点不支持缓存字段」—— 这正是 W9-04 立项要解决的 W7-02 痛点，但只解决了一半。
  - 建议：在用量行 / 调试日志 / 台账中至少一处渲染四态（复用 A-1 的接线点）。这是 W9-04 名义已 `CLOSED` 但仍未真正交付的最后一环，建议作为 W9-04 的「收口残留」回填台账，而非新开挂账。

---

## 二、与架构铁律的偏离风险清单（本日重新核对，静态全绿处标注 ✅）

| 铁律 | 现状（W11 未提交工作树核对） | 偏离风险 |
|---|---|---|
| TOTAL_LINE_CAP 不许变胖（W5 铁律 3） | 守住（37316/42500=87.8%）；W11 未抬上限 | ✅ 满足；但 per-module 高位带再收紧（B-2/B-3） |
| 拆分不调上限 | W11 未改 `LINE_CAPS`/`TOTAL_LINE_CAP`；新增逻辑均落既有模块 | ✅ 合规；⚠️ B-2/B-3：feature-chat 余12 / core-context 余28 已临界 |
| 组件有测试≠组件被使用 / 新增组件须配被调用判据 | **W9-04 四态 `PromptCacheStatus`/`cacheStatus` 测全绿但无生产消费者**（A-1/B-1/C-1）；`cacheHitRatio` 已接 `buildUsageLabel`（正面实例） | ⚠️ A-1/B-1/C-1：四态观测半落地；`cacheHitRatio` 是正面实例 |
| 已登记例外须按期消除 | `LiquidBottomTabs` 1012>600（core-design）例外自 W2U-2 未拆；feature-settings +100（W8）一次性例外已用尽 | ⚠️ 延续（前次 B2） |
| 平台策略先于代码拒绝你 | MCP cleartext 仍被 `usesCleartextTraffic=false` 挡（W5-03）；设备侧 1M 双内核阻断（W9-11 未解锁）；音频 NV `/v1/audio/speech` 404 | ⚠️ 需裁决，非代码可解 |
| DI 只走 AppContainer | 本波无散落 `object` 依赖单例/手动 `new`；`OpenAiCompatibleAdapter` 常量仍 companion object（非服务定位）；`EventReducer`/`RunState` 经既有 `container`/`ctx` 取值 | ✅ 未见违规 |
| 模块边界（G6/G8） | 本波新增 import 均向下：`core-context→core-model`（`AttachmentKind`/`TokenEstimator`/`ChatMessage`）、`feature-chat→core-model`（`TokenUsage`/`cacheHitRatio`）；core-model 未反向引 engine/net/data/feature | ✅ grep 零命中违规边 |
| 禁注解处理器/Retrofit/图片库（G4） | 仅注释禁止，无实际依赖 | ✅ 干净 |
| `@Serializable` 禁密钥字段（G17） | main 源集无新增 `@Serializable` 含 apiKey/secret 字段；`TokenUsage`/`PromptCacheStatus` 是普通序列化数据类 | ✅ 干净 |
| `WindowInsets.ime` 不进 `derivedStateOf` 普通 lambda | 本波未改动相关文件，维持 `GlassScaffold`/`ChatScreen` 先取 `val imeInsets` | ✅ 维持 |
| `runCatching` 不吞 `CancellationException`（W84） | 本波未改协程 IO 路径；既有调用点仍 `catch (c: CancellationException){throw c}` 前置 | ✅ 合规 |
| 裸 `assertFailsWith{}` 收尾致 initializationError（W80） | 测试源集无此用法 | ✅ 无风险 |

---

## 三、建议优先级（与是否进 ledger）

- **P0**：无新增（W11 红线项已由开发席在树内施工，未集成收口，不在本日静态判定范围）。
- **P1**：
  - **B-2** — `feature-chat` 3888/3900（余 12）：W11-01（R2 分隔卡）开工前先定拆分/下沉方案，不要触顶再拆（比前次 B1 的 feature-settings 更紧急，因余量更小）。
  - **A-1 / B-1 / C-1**（同源，四态观测半落地）：把 `PromptCacheStatus` 四态接进 `ContextBudget` 用量行，使 W9-04「唯一口径」真正落到展示层；或确认永不展示则删枚举/字段。**建议作为 W9-04 的「收口残留」回填台账 §二-bis，不新开挂账**（W9-04 已 `CLOSED`，但消费端缺失属其名义闭环的未交付部分）。
- **P2**：
  - **B-3** — `core-context` 1622/1650（余 28）：后续增量优先外提纯函数/拆分，禁抬上限。
  - **延续项（非新立账）**：A1 `supportsAudioOutput` 零消费、A2 `REASONING_ONLY` 标签死分支、A3 `judgeProbe` 命名、前次 B1 `feature-settings` 3492/3500（余 8，全仓最紧）、前次 B2 `LiquidBottomTabs` 例外、C1 `SamplingParams.reasoningEffort` 装饰字段、W11-03 `cache_invalidated` 二值过度声明（台账已 `OPEN`）、W9-05 journal/FreezeClock（第 7 顺延，`OPEN`）。

**是否进 roadmap / deferred-ledger**：本日**新增唯一建议立账项 = B-2 的「feature-chat 余 12 临界」**，建议作为 W11-01 的前置约束写入台账/roadmap（与既有 W11-01「触顶先下沉」同口径，不另开号）；A-1/B-1/C-1 建议回填 W9-04 收口残留，不新开挂账。其余为既有挂账状态重申。

---

## 四、未覆盖 / 需真机验证项（不可仅靠静态判定）

- **W11-02 已 `CLOSED`（正向）但引出重测需求**：`renderHistoryHead()` 去掉 `message.id` 段（因 `OpenAiCompatibleAdapter.messageObject()` 不写 `message.id`，旧实现让本地指纹假漂移）。这是前次文档「新观察项：message.id 跨重建不稳」的修复。**后果**：真机缓存命中率基线可能因此变化 —— W7-02（前缀缓存命中率）的历史 `cached_tokens` 观测需按新指纹语义**重新取数**，不得沿用旧基线当结论。
- **W9-04 四态消费端缺失（C-1）的真机影响**：即便接上四态，OR 免费池 `cached_tokens` 恒 0（W11 风险 2）⇒ 四态在 OR 上会稳定落 `CACHE_FIELDS_NOT_REPORTED` 或 `USAGE_NOT_OBSERVED`，需真机多供应商取数验证四态区分是否真能区分「不报字段」与「传输截断」。
- **W7-01 压缩真机重放**：32K 档 + 并行读 5×16KB ⇒ 实发 ≤ `hardContextBudgetTokens=27904`；免费模型该规模 `SocketTimeoutException`，须换 NV 端点。
- **W7-02 前缀缓存命中率**：三供应商实测不一致（nv 0→0→4336，dots/north 恒 0）；W11-02 改指纹后须重测。
- **W7-03 手动压缩 / 会话导入真机验收**：当前仅单测（含 W10 S3 尾部摘要前置），真机双向证据未取。
- **W11-03 `cache_invalidated` 二值过度声明**：S3 砍中段后 `ConversationOps.kt:126,186` 落 `false`、`undoCompress():205` 仍落 `true`、`SettledCompressPolicy.kt:14-18` 预写「头锚段仍 > 0」—— 需真机 M9 验证尾置 SYSTEM 摘要是否被端点接受，再决定改 `partial / head_anchor_preserved` 三态。
- **OEM 差异**：唯一直设备 OPPO PDRM00 / Android 13；华为/HarmonyOS、小米、vivo 的 `usesCleartextTraffic`、Keystore、屏幕密度未验。
- **端侧模型行为**：仅 NV-VL 实测 1M 窗口三通；W11 矩阵（F1–F11）多数设备侧未行使，预登记各项（1M/记忆/Skill/MCP/KV cache/压缩）须按四态取证，不得写成已验证；设备侧 1M 受 RoundHeadGate 400k 阻断（W9-11 未解锁）。

---

## 结论

本日（2026-11-07，W11 未提交工作树）与前次同日文档（2026-11-06-2，W9 工作树）的**实质差异 = 工作树推进到 W10 已合入 + W11 在途**，核心变化是 **W9-04 缓存四态 + 厂商字段归一 + `cacheHitRatio` 分母钉死** 与 **W11-02 `renderHistoryHead` 指纹去 id** 两处落地。静态核对结论：

1. **架构铁律静态全绿**：DI 只走 AppContainer；模块边界无违规边（G6/G8）；G4 黑名单/G17 密钥字段/`WindowInsets.ime`/`CancellationException` 重抛/`assertFailsWith` 均合规；行数总闸门 42500 守住（87.8%）。**W11 未引入任何新违规。**
2. **新发现 a/b/c = 1/3/1**：A-1（`PromptCacheStatus` 四态已生产、`ContextBudget` 用量行却仍走老三态、无人读取）；B-1（四态枚举是「声明了 observability 却未交付」的装饰性设计，与 W4 铁律同型）；B-2（**feature-chat 3888/3900 余 12，全仓第二紧、新临界**）；B-3（core-context 1622/1650 余 28 收紧）；C-1（四态消费端完全缺失）。A-1/B-1/C-1 同源 = 「W9-04 四态观测半落地」。
3. **最高优先风险 = B-2 + A-1/B-1/C-1 同源根因**：per-module 水位继 feature-settings（余 8）后，**feature-chat 也逼近天花板（余 12）**，且 W9-04 的四态语义从未被任何展示/日志/台账消费 —— 前者要求「先拆再动」，后者要求「先接线再算闭环」。
4. **正面演进**：W11-02 已 `CLOSED`（指纹去 id，修正前次「message.id 假漂移」观察）；W9-04 数据层（cacheWrite 全链路透传 + 厂商字段归一 + `cacheHitRatio` 已接 `buildUsageLabel`）已 `CLOSED`；W9-01 附件进硬预算（`HardBudgetShrinker.promptChars` 补 `sendableAttachments`）关闭外审缺口 1。

**全程只读，未改动任何源码/配置文件、未 `git add`/`commit`/`push`、未执行任何会改动设备的 adb 命令。**

> 与 N 天前结论的演进：相对前次同日文档（2026-11-06-2，a/b/c=3/2/1）——
> - 行数水位表由 W9 口径更新为 **W11 未提交态**（全仓 37316/42500=87.8%）；**新出现两个 ≤12 行余量的模块**（feature-settings 余 8、feature-chat 余 12），前次仅报 feature-settings 一个；
> - 前次 **A-3/F-1「组件被使用」缺口已闭环**（正面实例）；但本波发现 **新的同类缺口 = W9-04 四态观测建而未用**（A-1/B-1/C-1），且 `w11-plan.md` 的行号锚（feature-chat 余27 / core-context 余62）已被工作树漂移证伪，本表给出更紧的实测值；
> - 前次 A1(`supportsAudioOutput` 零消费)/A2(`REASONING_ONLY` 标签死分支)/A3(`judgeProbe` 命名)/C1(`SamplingParams.reasoningEffort` 装饰字段)/B1(feature-settings 余8)/B2(`LiquidBottomTabs` 例外) **均延续未解**；
> - W11-02(`renderHistoryHead` 去 id) 已由「新观察项」升级为 `CLOSED` 正面实例；W11-03(`cache_invalidated` 二值过度声明) 为本期新浮出、台账已 `OPEN` 的关联项。
