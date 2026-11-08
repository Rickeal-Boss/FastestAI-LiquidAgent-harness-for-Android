# LiquidAgent harness-v2 源码日析 — 2026-11-06

> 只读静态分析 + 改进推演。**全程未改动任何源码/配置文件**，仅在 `docs/analysis/` 新建本文档。
> 分析对象：`<workspace>/harness-v2`（现役仓）。

## 本次依据（版本与水位）

- **工作记忆**：`MEMORY.md` + 最新日志 `2026-11-05.md`（W8 波，端点高级配置 / Markdown / JSON 检查，实现并行中）+ `2026-11-04.md`（W7 收口）。
- **项目文档**：`docs/02-roadmap.md`、`docs/06-deferred-ledger.md`（当前工作树为 W8 未提交版）、`handoff-20261104-wave7.md`、`docs/notes/w8-plan.md`。
- **架构闸门**：`scripts/arch-guard.sh` 的 `LINE_CAPS`（W7 口径，约 152–193 行），`TOTAL_LINE_CAP=42500` 未动。
- **前次日析**：`docs/analysis/source-review-2026-11-04.md`（首份，基于当时 W7 未提交工作树）。
- **git 状态核实**：`HEAD = 578f7e4`（W7 已提交并推 main），工作树仅 `docs/06-deferred-ledger.md`（M）、`docs/analysis/`（??，含 w8 三份分析 + 本目录前份）、`docs/notes/w8-plan.md`（??）为未提交；**`**/src/main/**` 的 `.kt` 无任何未提交改动** ⇒ 本日源码即 W7 提交态。

### arch-guard 当时行数水位（本次 `find …/**/src/main/**/*.{kt,kts}` 逐模块 `wc -l`，与 11-04 表逐项对拍）

| 模块 | 实测行 | 上限 | 余量 | 水位 |
|---|---:|---:|---:|---:|
| core-model | 1439 | 2600 | 1161 | 55% |
| core-net | 790 | 1200 | 410 | 66% |
| core-engine | 1081 | 2600 | 1519 | 42% |
| core-kernel | 403 | 450 | 47 | **90%** |
| core-context | 1584 | 1650 | 66 | **96%** |
| core-agent | 5326 | 6500 | 1174 | 82% |
| core-skills | 419 | 1000 | 581 | 42% |
| core-mcp | 758 | 1400 | 642 | 54% |
| core-data | 2329 | 4500 | 2171 | 52% |
| core-design | 12439 | 12600 | 161 | **99%** |
| feature-chat | 3619 | 3900 | 281 | 93% |
| feature-settings | 3229 | 3400 | 171 | 95% |
| feature-tools | 947 | 2600 | 1653 | 36% |
| app | 1338 | 1600 | 262 | 84% |
| **TOTAL** | **≈ 35701** | **42500** | 6799 | 84% |

（feature-chat +4、feature-settings +2 相对 11-04 表，系 W7 提交态与当日前份"未提交预览"的微小口径差；其余完全一致。逐模块求和，非 `find | wc -l | tail -1`，后者会少算。）

---

## 一、本次新发现

> **结论先行：自 2026-11-04 首份日析以来，现役仓 `**/src/main/**` 源码未发生变化（W7 已提交，W8 仅文档未提交），故本日无新增 a/b/c 代码级问题。** 以下为对前次日析结论的**核实订正**（非新问题），以及延续清单，均经本日重新 `grep`/`Read` 现役树核对。

### 对前次日析（11-04）的两处核实订正

**D1 — 前次 A2（"G18 漏判 `engine.generate(`/`delegate.generate(`"）经核实为误判，撤回该风险等级**
- 核对待处：`scripts/arch-guard.sh` G18 实现（约 1160–1184 行 + 白名单 281–285 行）。
- 事实：G18 并非"文本匹配 `LlmEngine.generate(`"。其实现是 `grep '\.generate\('` 全仓 main 源集，再 `grep -vE '^core-agent/.../loop/'` 排除主循环；同时**显式把 `core-data/.../RemoteEngineBridge.kt` 与 `EndpointProbe.kt` 列为"架构上合法的引擎调用点"白名单**（注释明言"两个架构上合法的引擎调用点"）。
- 结论：前次 A2 担忧的"变量名 `engine.generate(`/`delegate.generate(` 不被文本守卫捕获"不实——守卫本来就用 `.generate\(` 宽匹配，且这两处恰是被白名单**有意制裁**的委托/探测点（薄委托层 + 连通性探针，语义上允许驱动一次生成）。**G18 实际合规，A2 风险等级撤回**。残留仅有：白名单按文件名写死，若未来新增"合法第三委托点"需手工扩白名单——属 consciously-maintained 列表，不构成隐患，不立账。

**D2 — 前次 B3 中 `allowClipboardRead` 实为"已接线但无 UI 开关"，非"声明未使用"**
- 核对待处：`AgentPolicy.kt:133`、`ClipboardTool.kt:78`。
- 事实：`ClipboardTool` 第 78 行 `if (!policy.allowClipboardRead && actionOf(argumentsJson) == Action.GET) READ_DISABLED else null` **真实消费**了该策略字段；读侧受 `allowClipboardRead` 约束、写侧本就要审批。
- 结论：前次将其与 `autoApproveDangerous`/`capabilities()`/`supportsVision` 并列归为"声明未使用"不准确。其真实缺口是**没有 UI/setter 让用户改默认值（默认 true）**，属"配置不可见"而非"死开关"。B3 同型家族应修正为三条真"零消费/零派生"（`autoApproveDangerous` 无消费点、`capabilities()` 零调用方、`supportsVision` 硬编码 false 未派生）+ 一条"无 UI 开关"（`allowClipboardRead`）。

### 延续前次、经本日复核仍成立的发现（a/b/c 逐项）

**（a）需要改进的实现（均延续 11-04，未变）**
- A3 — `SecretRedactor` 仅接 2/5 出口：本日 `grep` 仍只命中 `RemoteLlmEngine.kt:206`、`AgentLogStore.kt:135`；`redactHeaders` 函数存在但无调用方（X5 出口矩阵未闭环）。
- A4 — `SessionFootprintStore` 仍仅 `AppContainer.kt:74` 持有 + `SessionFootprint.kt` 三处 KDoc 引用，运行期读取点未确认、无对应测试（"新增组件缺被调用判据"候选仍成立）。

**（b）不合理的功能设计（均延续 11-04，未变）**
- B1 — per-module 天花板高位带：`core-design` 99%、`core-context` 96%、`feature-settings` 95%、`feature-chat` 93%、`core-kernel` 90%、`app` 84%。`TOTAL_LINE_CAP=42500` 守住（84%，满足"不许变胖"），但压力已转移到单模块上限（"拆分当扩容"的 per-module 翻版）。
- B2 — `LiquidBottomTabs.kt` 1012 行 vs 单文件上限 600，自 W2U-2 登记例外未拆，`core-design` 唯一越界文件。
- B3 — "声明未使用/未派生"同型缺陷无自动拦截（修正后家族见 D2）。

**（c）缺失的功能（均延续 11-04，未变）**
- C1 — 缺"声明但未使用"静态检测器（B3 的自动化形态）。
- C2 — journal / FreezeClock（W3-05）仍 OPEN，零实现。
- C3 — Skill 导入链路（W3-02）仍 OPEN，`SettingsRoute.kt` 仍 `onImport = null`。
- C4 — MCP 真机联调（W5-03）仍被 `usesCleartextTraffic=false` 挡。
- C5 — 前缀缓存命中率验证（W7-02）前提未立；`Generation.kt` 的 DeepSeek `prompt_cache_hit_tokens` 字段 `TODO(W6)` 仍在。

### 待 W8 落地后下次日析复核（非本日发现，仅预警）
- W8 实现并行中（端点高级配置 / Markdown 渲染 / JSON 检查），源码尚未进 main。据 `2026-11-05.md` 已知风险点，待落地后重点复核：
  - **Q15**：`EndpointProbe.kt:57 maxTokens=8` + `judgeProbe` 把 `ReasoningDelta` 计入内容 ⇒ 测试连接可能假绿/假红；预期修复为预算 ≥64 + 谓词改 `TextDelta||Usage`。
  - **W3-07 同型**：`RemoteLlmEngine.kt:92 supportsVision=false` 硬编码，需"字段派生 + capabilities + UI 闸门"三处同改，默认 false 会 regression。
  - **JSON 严格性**：JSON 检查不能复用 `AgentJson`（isLenient=true 会把非法 JSON 判绿），须独立严格 `Json` 实例。

---

## 二、与架构铁律的偏离风险清单（本日重新核对，静态全绿处标注 ✅）

| 铁律 | 现状（本日核对） | 偏离风险 |
|---|---|---|
| TOTAL_LINE_CAP 不许变胖（W5 铁律 3） | 守住（≈35701/42500=84%） | ✅ 满足；B1 的 per-module 翻版仍存 |
| 拆分不调上限 | W7 抬高 3 个 per-module 上限（功能增量，分开记账） | ⚠️ B1：per-module 天花板成瓶颈 |
| 守卫边界是包目录不是意图 | G18 宽匹配 `.generate\(` + core-data 两文件白名单（**非前次所误判的漏判**） | ✅ 合规（D1 订正）；白名单按文件名写死，新增合法委托点需手工扩 |
| 组件有测试≠组件被使用 / 新增组件须配被调用判据 | `autoApproveDangerous`/`capabilities()`/`supportsVision` 零消费/零派生；`allowClipboardRead` 实为已消费但无 UI（D2 订正）；`SessionFootprintStore` 无判据 | ⚠️ B3 / A4：无自动拦截 |
| 已登记例外须按期消除 | `LiquidBottomTabs` 1012>600 例外自 W2U-2 未拆 | ⚠️ B2 |
| 平台策略先于代码拒绝你 | MCP cleartext 仍被 G15b 拒 | ⚠️ C4：需裁决，非代码可解 |
| DI 只走 AppContainer | `by lazy` 仅 `AppContainer` + `AppNavigationBars` 的 `ChatTabIcon`（ImageVector 资源，可接受）；`object` 多为 `data object`（密封子类）与无状态工具对象，无散落依赖单例 | ✅ 未见散落单例/手动 new |
| 模块边界（G6/G8） | core-model 不引 engine/net/data/design；core-agent 不引 engine/net | ✅ grep 零命中 |
| 禁注解处理器/Retrofit/图片库（G4） | 仅注释声明禁止，无实际依赖 | ✅ 干净 |
| `@Serializable` 禁密钥字段（G17） | main 源集无 `@Serializable` 含 apiKey/secret 字段（`tokens`/`KEY_MAX_CONTEXT_TOKENS` 为误匹配，非序列化密钥） | ✅ 干净 |
| `WindowInsets.ime` 不进 `derivedStateOf` 普通 lambda | `GlassScaffold.kt:129`、`ChatScreen.kt:107` 均先取出为 `val imeInsets` | ✅ 已正确处理 |
| `runCatching` 不吞 `CancellationException`（W84） | `AgentRunner.kt:158`、`GenerationRound.kt:234`、`EngineBackedSummarizer.kt:78` 均 `catch (c: CancellationException) { throw c }` 前置 | ✅ 合规 |
| 裸 `assertFailsWith{}` 收尾致 initializationError（W80） | 测试源集无 `assertFailsWith` 收尾用法 | ✅ 无风险 |

---

## 三、建议优先级

- **P0（前次，已收口）**：前次 A1「W7 未提交变更集」**已于 2026-11-04 提交 `578f7e4` 并推 main** ⇒ 本日核实已解决，不再列为待办。
- **P1（应处理）**：
  - B1 — per-module 行数天花板：触顶前主动拆 `core-design` / `feature-settings`（与 B2 同波）。
  - C2 — journal / FreezeClock（W3-05）开波，补"跑不死"支柱。
  - A3 — `SecretRedactor` 补满 5 出口 + 单测，闭环 X5。
  - B3 — 新增"声明但未使用/未派生"守卫（G-new），从根拦截该类缺陷（白名单已知合法项：`allowClipboardRead` 已消费、`RemoteEngineBridge`/`EndpointProbe` 已白名单）。
- **P2（可排期）**：
  - B2 — `LiquidBottomTabs` 拆分、撤销 1012>600 例外。
  - C3 — Skill 导入链路接线。
  - C4 — MCP 真机联调（需 https 夹具 / cleartext 例外裁决）。
  - C5 — 前缀缓存命中率验证 + `Generation.kt` DeepSeek 字段 TODO。
  - A4 — `SessionFootprintStore` 补调用判据/测试或删除。
  - W8 落地复核项（Q15 / supportsVision 派生 / JSON 严格性）。

**是否进 roadmap / deferred-ledger**：本日**无新增挂账**；B3（G-new 守卫）仍建议新增条目进 `docs/06-deferred-ledger.md`（并过 G20），A4 同。其余均为既有挂账（W3-05/W3-02/W3-07/W5-03/W5-05/W6-02/W7-02）状态重申，不重复立账。

---

## 四、未覆盖 / 需真机验证项（不可仅靠静态判定）

- **W7-01 压缩真机重放**：32K 档 + 并行读 5×16KB ⇒ 实发 ≤ `hardContextBudgetTokens=27904`；免费模型该规模 `SocketTimeoutException`，须换 NV 端点。
- **W7-02 前缀缓存命中率**：三供应商实测不一致（nv 0→0→4336，dots/north 恒 0），缓存不可依赖，须真机取数。
- **W7-03 手动压缩 / 会话导入真机验收**：当前仅单测，真机双向证据未取。
- **W5-03 MCP cleartext**：需 https 夹具或 cleartext 例外**安全裁决**（平台行为，非代码可解）。
- **W3-05 FreezeClock API 路径**：真机 API 路径未验证。
- **OEM 差异**：唯一直设备 OPPO PDRM00 / Android 13；华为/HarmonyOS、小米、vivo 的 `usesCleartextTraffic`、Keystore、屏幕密度未验。
- **W3-01 `speak` TTS**：默认关闭、未验证。
- **端侧模型行为**：仅 NV-VL 实测 1M 窗口三通；其余供应商/模型长上下文、视觉、工具行为未系统验证。
- **W8 设备侧**：端点高级配置 / Markdown 渲染 / JSON 检查落地后，须真机验证连接探针（Q15）、视觉能力派生（W3-07）、JSON 严格性判绿准确性。

---

## 结论

本日（2026-11-06）源码自 11-04 首份日析以来**未发生变化**——W7 已提交（`578f7e4`），W8 仅文档未提交，故**无新增 a/b/c 代码级问题**。本日价值在于**对前次日析的两处核实订正**（D1：G18 实际宽匹配 + core-data 两文件白名单，"漏判"误判撤回；D2：`allowClipboardRead` 实为已接线仅缺 UI 开关），并重新核对全部架构铁律——DI/模块边界/G4 黑名单/G17/`WindowInsets.ime`/`CancellationException` 重抛/`assertFailsWith` 静态全绿，与 11-04 一致且更精确。行数水位不变（≈35701/42500=84%，per-module 高位带仍存）。既有挂账（W3-05/W3-02/W3-07/W5-03/W5-05/W6-02/W7-02）及 A3/A4/B1/B2/C1–C5 延续有效。

**全程只读，未改动任何源码/配置文件、未 `git add`/`commit`/`push`、未执行任何会改动设备的 adb 命令。**

> 与 N 天前结论的演进：相对首份日析（2026-11-04）为「源码未变 ⇒ 无新增；两处误判订正（A2 撤销、allowClipboardRead 重新归类）；A1 已收口；其余延续」。本日新增唯一实质内容 = 对前次结论的自我纠错，符合项目"时点差/现役树核对"纪律。
