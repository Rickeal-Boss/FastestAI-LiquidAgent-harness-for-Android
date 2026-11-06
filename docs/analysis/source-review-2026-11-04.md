# LiquidAgent harness-v2 源码日析 — 2026-11-04

> 只读静态分析 + 改进推演。**全程未改动任何源码/配置文件**，仅在 `docs/analysis/` 新建本文档。
> 分析对象：`D:/WBfil/2026-09-18-18-27-37/harness-v2`（现役仓）。

## 本次依据（版本与水位）

- **工作记忆**：`D:/WBfil/2026-09-18-18-27-37/.workbuddy-ai/memory/MEMORY.md` + 最新日志 `2026-11-02.md`（自动化建档日）。
- **项目文档**：`docs/02-roadmap.md`（W1–W8 / 能力线 X1–X6）、`docs/06-deferred-ledger.md`（42 条挂账，W7 版）、`handoff-20261101-wave6.md`、`docs/notes/w7-*.md`。
- **架构闸门**：`scripts/arch-guard.sh` 的 `LINE_CAPS`（W7 口径，约 144–204 行）。
- **arch-guard 当时行数水位**（本次 `find` 实扫 `**/src/main/**/*.{kt,kts}`，逐模块 `wc -l`）：

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
| feature-chat | 3615 | 3900 | 285 | 93% |
| feature-settings | 3227 | 3400 | 173 | 95% |
| feature-tools | 947 | 2600 | 1653 | 36% |
| app | 1338 | 1600 | 262 | 84% |
| **TOTAL** | **≈ 35695** | **42500** | 6805 | 84% |

（注：用 `find … | xargs wc -l | tail -1` 会少算，故取逐模块 `wc -l` 求和 ≈ 35695；与台账 W7-07 记载的现役全仓 main 35233/42500 两法对拍一致，量级吻合。）

## 〇、分析基线 / 状态事实（本日最重要的一条）

**当前工作树处于「W7 已落地但未提交」的大变更集状态**（已 `git status` 核实）：

- `core-context/` 是**未跟踪**的新模块（即 W7 把 `core-kernel/context/` 六支整体下沉的目标模块）；同时 `core-kernel/.../context/*` 六支源码 + 六支测试被删除（移到 core-context）。
- 8 个 `SmokeTest.kt` 占位测试被删除（`app`/`core-agent`/`core-data`/`core-design`/`core-mcp`/`core-skills`/`feature-chat`/`feature-settings`，即 W7-04 的 8 条 `assertTrue(true)` 占位）。
- 新增未跟踪组件 `core-data/.../SessionFootprintStore.kt`（45 行）。
- 源码改动：`ContextBudget.kt`、`AppContainer.kt`、`ChatViewModel.kt`、`ConversationOps.kt`、`EndpointListScreen.kt`、`SettingsRoute.kt`、`SettingsViewModel.kt`、`settings.gradle.kts`、两个 `build.gradle.kts`。
- 守护/CI 改动（均未提交）：`scripts/arch-guard.sh`（新增 G22/G23）、`scripts/arch-guard-selftest.sh`、`.github/workflows/build.yml`（`EXPECTED_TESTS` 已改 **696**）。
- 新增未跟踪文档 `docs/notes/w7-context-cache-roadmap.md`、`docs/notes/w7-line-budget-ruling.md`。

这解释了「台账把 W7 写成已收口、而 `git log 9714986..HEAD` 只见 3 个 docs 提交」的表面矛盾：**代码与守卫已进工作树，但 HEAD 仍是 W6 收口点 `c67f37d`**。本文所有行数水位、组件接线结论均基于**真实工作树**（含未提交内容），与台账记述互为印证。

---

## 一、本次新发现

### (a) 需要改进的实现

**A1 — 工作树存在未提交的 W7 大变更集（过程/稳定性风险）**
- 文件：`git status` 全量；`scripts/arch-guard.sh` G22/G23；`.github/workflows/build.yml:132` `EXPECTED_TESTS='696'`。
- 现象：模块拆分（core-context）+ 8 个占位测试删除 + 守卫新增 + 测试数常量改动 + 多处源码，**全部散落在工作树、未一次性提交**。
- 风险：W7-04 / W7-07 已明订「删占位 / 改常量 / 全量对账」三件必须同一次提交，否则中间态必红（先删文件 ⇒ 基线 696/实跑 688；先改基线 ⇒ 基线 688/实跑 696）。当前状态若被中途 `git push` 或 CI 在不同点跑，必然红。
- 建议：尽快按纪律**原子提交**；提交前在仓库根实跑 `bash scripts/arch-guard.sh`（预期 `OK=25→27`、G22/G23 随调用点落地而转绿/VACUOUS 合规）并确认 `EXPECTED_TESTS=696` 与严格 `@Test` 数双向对账通过。

**A2 — `core-data` 存在两处 `engine.generate` 调用/委托，落在 `core-agent/loop/` 之外**
- 文件：`core-data/RemoteEngineBridge.kt:35`（`delegate.generate(request)`）、`core-data/EndpointProbe.kt:65`（`engine.generate(request).firstOrNull { … }`）。
- 现象：G18 守卫文本匹配的是字面量 `LlmEngine.generate(`；而这两处是变量名 `delegate.generate(` / `engine.generate(`，**文本守卫漏判**。其中 `EndpointProbe` 在 `:core-data` 内**真实驱动一次生成**做连通性探测，语义上已越过「只有 `loop/` 才驱动生成」的意图边界（另：`RemoteEngineBridge` 是薄委托层，可接受，但同样不被字面守卫捕获）。
- 风险：守卫的覆盖是「文本形态」而非「意图」—— 后续若有人在 `core-data` 加真正的生成驱动逻辑，G18 不会告警（与铁律「守卫边界是包目录，不是意图」同源隐患）。
- 建议：把 G18 正则扩到「非 `core-agent/loop/` 模块内的 `\.generate(` 调用」；或将 `RemoteEngineBridge`/`EndpointProbe` 两处**显式登记为受制裁的委托/探测点**并在注释锚定，避免形态漂移后失守。

**A3 — `SecretRedactor` 仅接在 2/5 计划出口**
- 文件：`core-kernel/.../AgentLogStore.kt:135`（`SecretRedactor.redact(raw)`）、`core-engine/RemoteLlmEngine.kt:206`（`SecretRedactor.redact(stream.errorBodyText())`）。
- 现象：roadmap X5 设计 5 个脱敏出口（日志 / 工具参数回显 / HTTP 请求头 / 文件内容展示 / UI 气泡），当前主源集只有**日志**与**引擎错误体**两处消费。`redactHeaders` 函数已存在（`SecretRedactor.kt:103`）但 grep 不到请求头脱敏的调用点；工具参数、UI 气泡脱敏未见接线。
- 风险：密钥仍可能从 HTTP 请求头日志、工具参数回显、气泡文本三处泄漏——X5 安全收口闭环未达成（该挂账仍 `OPEN`）。
- 建议：X5 开波时按「8 类密钥 × 5 出口」矩阵逐项接线 + 单测；并考虑把「出口脱敏」也纳入一条守卫（grep 各出口是否调用 `redact`/`redactHeaders`），从根上防止「声明了脱敏能力却没接满」。

**A4 — 新增未跟踪组件 `SessionFootprintStore` 缺调用判据与测试**
- 文件：`core-data/.../SessionFootprintStore.kt`（45 行，untracked）；引用点 `core-data/AppContainer.kt:74`（实例化）、`feature-chat/SessionFootprint.kt:17/39/65`（仅 KDoc 引用）。
- 现象：组件被 `AppContainer` 持有，但**运行期是否真被读取未确认**；`core-data/src/test` 下**无对应测试**。
- 风险：属「新增组件缺被调用判据」的潜在新例——若从未被读取，就是 45 行死代码；若有读取点但无测试，则是「有测试≠被使用」逆面的「被使用但无判据」。
- 建议：提交 W7 前补一条「被调用」判据（grep 运行期消费点）或一条纯 JVM 用例；若确认无消费点，则连组件带 KDoc 引用一起删，避免累积死代码。

### (b) 不合理的功能设计

**B1 — W7 抬高 per-module 上限后，多个叶模块进入 82–99% 高位带（「拆分当扩容」的 per-module 反模式）**
- 文件：`scripts/arch-guard.sh` `LINE_CAPS`（core-context 1450→1650、feature-chat 3500→3900、feature-settings 3000→3400，W7 功能增量 +1000 分开记账）。
- 现象：见顶部水位表——`core-design` 12439/12600(99%)、`core-context` 1584/1650(96%)、`feature-settings` 3227/3400(95%)、`feature-chat` 3615/3900(93%)、`core-kernel` 403/450(90%)、`app` 1338/1600(84%)。
- 风险：**`TOTAL_LINE_CAP=42500` 守住（约 84%）满足「不许变胖」铁律，但 per-module 天花板已成为新瓶颈**——这些模块再新增任何功能即触顶，被迫再拆。这正是 W5 铁律 3「拆分不该让全仓变胖」警惕点的 per-module 翻版：量纲从全仓挪到了单模块。尤其 `core-context` 是 W7 刚为缓解压力而从 `core-kernel` 拆出的，结果 +200 功能一落地就只剩 66 行余量。
- 建议：在触顶前**主动拆分**而非再次抬高上限——优先把 `core-design` 的 `LiquidBottomTabs` 等拆出独立组件库、把 `feature-settings` 的子页（MCP/端点/开关）抽成子模块；把「per-module 余量 < 10%」也纳入一条可见性/告警判据（呼应 W6-08 的「可见性先于判红」纪律）。

**B2 — `LiquidBottomTabs.kt` 1012 行 vs 单文件上限 600，已登记例外多年未拆**
- 文件：`core-design/LiquidBottomTabs.kt:1`（1012 行）。
- 现象：单文件上限 600（G10），该文件自 W2U-2 起以「已登记例外」存在，至今多波未拆；是 `core-design` 唯一越过单文件上限的文件。
- 风险：千行级 `@Composable` 文件是评审/改动/冲突的高负债点，也是 W6-07「拆分不调上限」同型隐患的活样本；`core-design` 现已 99% 触顶，继续往里加 UI 只能靠拆或破例。
- 建议：本波或下波把 `LiquidBottomTabs` 拆成若干子组件文件，撤销该登记例外；与 B1 的 core-design 拆分一并处理。

**B3 — 「声明了没消费者」的开关已成反复出现的缺陷类，无自动拦截**
- 文件（当前在账）：`core-kernel/.../AgentPolicy.kt:115` `autoApproveDangerous`（W5-05，0 消费点）、`core-agent/engine/LlmEngine.kt:47` `capabilities()`（W6-02，main 零调用）、`core-kernel/.../AgentPolicy.kt` `allowClipboardRead`（W6-05，无 UI/setter）、`core-engine/RemoteLlmEngine.kt:92` `supportsVision=false`（W3-07，硬编码）。
- 现象：台账里**已立账 4 条**同型缺陷（「声明了没消费者」第 3、4 例 + 两个无编号补登），全部靠人工发现 + 立账，无编译期/守卫期拦截。本次 A4（`SessionFootprintStore`）疑似同型新例仍在发生。
- 风险：该类缺陷不会让 CI 红，只会让「看起来有了的能力」实际无效（如 `autoApproveDangerous` 的危险工具自动放行永不发生），与铁律「组件有测试≠组件被使用」同源。
- 建议：新增一条守卫（G-new），对 `AgentPolicy` 的 `val` 字段与 `LlmEngine` 接口方法做「main 源集零消费点」扫描（白名单已知合法项），从根上阻止该类缺陷复现；把现有 4 条在账项纳入其白名单/修复路径。

### (c) 缺失的功能

**C1 — 缺少「声明但未使用」静态检测器（即 B3 的自动化形态）**：同类端侧 Agent 项目通常用编译期/守卫期检查未接线端口；本仓目前完全靠人工。

**C2 — journal / FreezeClock（挂账 `W3-05`）仍 `OPEN`，已挂 4+ 波**：全仓零实现（仅 5 处注释），是 roadmap「从能跑到跑不死」支柱的唯一支柱，被「能跑更多东西」这条线挤掉多波。最高价值缺失能力。

**C3 — Skill 导入链路（挂账 `W3-02`）仍 `OPEN`**：`core-skills/SkillImportValidator.kt` 已就绪，但 `feature-settings/SettingsRoute.kt:206` 仍 `onImport = null`（传 null 让 screen 不渲染按钮），导入能力从未接线。

**C4 — MCP 真机联调（挂账 `W5-03`）仍被平台策略挡**：`app/.../AndroidManifest.xml` `usesCleartextTraffic="false"`（G15b 要求，不为测试放开）⇒ 明文 http 被平台拒（`transport_error`）；需 https 夹具或 cleartext 例外裁决。

**C5 — 前缀缓存命中率验证（挂账 `W7-02`）前提未立**：W6 多模型实测 `nv-vl` 三连发 `0→0→4336`、dots/north 恒 0 ⇒ 缓存不可依赖。继续为缓存布局投入前应先证「前缀逐字节稳定时第 3 次 `cached_tokens>0`」，并**重申「禁止为凑缓存加长 system」**（800 全价 vs 4096×0.5 当量反而贵 2.5×）。耦合点：`core-model/Generation.kt:48` 有一处 `TODO(W6)` 关于 DeepSeek `prompt_cache_hit_tokens` 字段落点，建议同波处理。

---

## 二、与架构铁律的偏离风险清单

| 铁律 | 现状 | 偏离风险 |
|---|---|---|
| TOTAL_LINE_CAP 不许变胖（W5 铁律 3） | 守住（≈35695/42500=84%） | ✅ 满足；但见 B1 的 per-module 翻版 |
| 拆分不调上限 | W7 抬高了 3 个 per-module 上限（功能增量，已分开记账） | ⚠️ B1：per-module 天花板成新瓶颈 |
| 守卫边界是包目录不是意图 | G18 文本匹配 `LlmEngine.generate(` | ⚠️ A2：变量名 `engine.generate(` 漏判 |
| 组件有测试≠组件被使用 / 新增组件须配被调用判据 | EngineCapabilities、autoApproveDangerous、allowClipboardRead、supportsVision 同型在账；新增 `SessionFootprintStore` 无判据 | ⚠️ B3 / A4：无自动拦截 |
| 已登记例外须按期消除 | `LiquidBottomTabs` 1012>600 例外自 W2U-2 未拆 | ⚠️ B2 |
| 平台策略先于代码拒绝你 | MCP cleartext 仍被 G15b 拒 | ⚠️ C4：需裁决，非代码可解 |
| DI 只走 AppContainer | `by lazy` 集中且只在 `core-data/AppContainer.kt` + 两处局部（SpeakTool 线程、AppNavigationBars 图标） | ✅ 未见散落单例/手动 new |
| 模块边界（G6/G8） | core-model 不引 engine/net/data/design；core-agent 不引 engine/net | ✅ grep 零命中 |
| 禁注解处理器/Retrofit/图片库（G4） | 仅注释中声明禁止，无实际依赖 | ✅ 干净 |
| `@Serializable` 禁密钥字段（G17） | `RemoteEndpoint` 无 apiKey 字段（仅 KDoc 提及旧规格） | ✅ 干净 |
| `WindowInsets.ime` 不进 `derivedStateOf` 普通 lambda | `ChatScreen.kt:107`、`GlassScaffold.kt:129` 均先取出为 `val` | ✅ 已正确处理 |

---

## 三、建议优先级

- **P0（无功能阻塞）**：W7 未提交变更集须按纪律**原子提交**（过程优先级最高，见 A1），否则 CI 必红。
- **P1（本波/下波应处理）**：
  - B1 — per-module 行数天花板：在触顶前主动拆 `core-design` / `feature-settings`。
  - C2 — journal / FreezeClock（W3-05）开波，补「跑不死」支柱。
  - A3 — `SecretRedactor` 补满 5 出口 + 单测，闭环 X5。
  - B3 — 新增「声明但未使用」守卫（G-new），从根拦截该类缺陷。
- **P2（可排期）**：
  - B2 — `LiquidBottomTabs` 拆分、撤销 1012>600 例外。
  - C3 — Skill 导入链路接线。
  - C4 — MCP 真机联调（需 https 夹具 / cleartext 例外裁决）。
  - C5 — 前缀缓存命中率验证 + `Generation.kt:48` 的 DeepSeek 字段 TODO。
  - A2 — G18 正则扩面或显式登记委托/探测点。
  - A4 — `SessionFootprintStore` 补调用判据 / 测试或删除。

**是否进 roadmap / deferred-ledger**：B3（G-new 守卫）与 A4（`SessionFootprintStore` 判据缺口）建议**新增挂账条目**进 `docs/06-deferred-ledger.md`（并同步 `handoff-*` 以过 G20）；其余多为既有挂账（W3-05/W5-03/W5-05/W6-02/W6-05/W7-02）的状态重申，不重复立账。

---

## 四、未覆盖 / 需真机验证项（不可仅靠静态判定）

- **W7-01 压缩真机重放**：32K 档 + 并行读 5×16KB ⇒ 实发 ≤ `hardContextBudgetTokens=27904`；当前免费模型在该规模 `SocketTimeoutException`，须换 NV 端点（已核实可用）。
- **W7-02 前缀缓存命中率**：三供应商实测不一致（nv 0→0→4336，dots/north 恒 0），缓存不可依赖，须真机取数。
- **W7-03 手动压缩 / 会话导入真机验收**：当前仅单测（ManualCompress 10 / ChatImport 6 / SessionImportParser 19），真机双向证据（压缩后 `cached_tokens=0` 既归零又恢复）未取。
- **W5-03 MCP cleartext**：需 https 夹具或 cleartext 例外**安全裁决**（平台行为，非代码可解）。
- **W3-05 FreezeClock API 路径**：旧仓只在本地路径验过，真机 API 路径未验证（风险：判据 4 不通过则降级为只服务墙钟 SOFT）。
- **OEM 差异**：唯一直设备 OPPO PDRM00 / Android 13；其他 OEM（华为/HarmonyOS、小米、vivo）的 `usesCleartextTraffic`、Keystore 行为、屏幕密度未验。
- **W3-01 `speak` TTS**：默认关闭、未验证；须换 TTS 正常的设备复验或显式裁决「永不启用」。
- **端侧模型行为**：仅 NV-VL 实测 1M 窗口三通；其余供应商/模型在设备侧的长上下文、视觉、工具行为未系统验证。

---

## 结论

本次为 **harness-v2 首份日析**（`docs/analysis/` 此前为空，无前次可比；结论与 `docs/06-deferred-ledger.md` W7 版一致并补充了行数水位实测）。**全程只读，未改动任何源码/配置文件、未 `git add`/`commit`/`push`、未执行任何会改动设备的 adb 命令。**

本轮最重要的事实是：**W7 代码与守卫已落地工作树但未提交**，且 **per-module 行数上限在 W7 抬高后已有 6 个模块进入 82–99% 高位带**——`TOTAL_LINE_CAP` 守住了「不许变胖」，但压力已转移到单模块天花板，是「拆分当扩容」铁律的 per-module 翻版，建议主动拆分而非再抬上限。架构铁律（DI/模块边界/G4 黑名单/G17/`WindowInsets.ime` 处理）静态核对均干净；`SecretRedactor` 出口、journal/FreezeClock、Skill 导入、MCP 联调等既有挂账仍 `OPEN`，与台账一致。

> 与 N 天前结论的演进：首份日析，无前次；相对挂账台账（W7 版）为「状态重申 + 行数水位实测 + 两处新观察（A4 未跟踪组件、B1 per-module 高位带）」，未推翻任何在账结论。
