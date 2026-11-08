# W8 Phase 1 方案分析 — 端点高级配置 / 会话显示 / JSON 检查 / 多模型矩阵

> **只读研究**：本文不改任何源码、配置、脚本或设备状态，仅新建本文档。
> 分析对象：`<workspace>/harness-v2`，`git rev-parse --short HEAD` = **`578f7e4`**，
> `git status --short` = `?? docs/analysis/`（工作树干净）。14 模块，`@Test` 严格口径 **696**，
> `.github/workflows/build.yml:132` `EXPECTED_TESTS: '696'`，`scripts/arch-guard.sh:204` `TOTAL_LINE_CAP=42500`。
> 方案席：沈思远。职责含**设备硬件可行性维度**（本仓唯一真机 OPPO PDRM00 / Android 13 / SDK 33）。
>
> **证据纪律**：凡「现状」结论均指到 `文件:行` 或实测产物；凡推断标【推断】；
> 凡外审/交接结论按 `AGENTS.md §二.2` 先 grep 回树再采信，采信结果标「已核 / 证伪」。
> 四态沿用本仓口径：**✅ 通过** / **⚠️ 部分** / **⛔ 不适用（含失败）** / **⛔ vacuous（判据未被行使）**。
> 「组件写完 ≠ 组件被用上」「判据写进方案 ≠ 判据被执行」「0 命中 ≠ 通过」是本文三条贯穿纪律。

> 🔴🔴 **必读 · 事后订正（2026-11-05 晚）**：本文写成后，主理人补跑 **9 组 `stream:true`** 实测
> （`docs/notes/w8-plan.md §二`、`docs/analysis/w8-provider-probe.md §六`），**证伪了本文 §0.2 / §3.3 /
> §7.2 T0 的「流式断链」判断**——现役 `delta.reasoning → ReasoningDelta` 链路本就通；
> 审查席 `w8-review-triage.md §八` 又改写了本文 §6.4 B4 的 1M 因果与若干数量口径。
> **§12「事后订正」优先于本文前文所有对应表述**；凡与 `docs/notes/w8-plan.md`（派单与验收唯一口径）
> 冲突处，一律以 w8-plan.md 为准。本文保留原推演过程以供复盘，不作静默改写。
> 这正是 `AGENTS.md §二.2`「证伪优先于证实」与知识库 §6「描述/报告都可能不成立」的现场一例。

---

## 0. 基线与证据清单

### 0.1 现役树事实（本席逐条回树核实，非转述）

| # | 事实 | 出处 |
|---|---|---|
| F1 | `RemoteEndpoint` 已有 `kind/preset/contextLength/maxOutputTokens/verifiedContextLength/supportsTools/supportsReasoning/reasoningStyle/reasoningEffort="medium"/extraHeaders/allowPrivateNetwork/hasSecret`；**无 `supportsVision`** | `core-model/.../RemoteEndpoint.kt:97-146` |
| F2 | `EngineCapabilities` 已有 `supportsVision: Boolean = false`（**默认值，不是硬编码**）；台账 W3-07 写 `:136`，现役实为 `:142`，行号已漂移 | `core-model/.../GenerationEvent.kt:139-148`；`docs/06-deferred-ledger.md` W3-07 行 |
| F3 | 真正硬编码是 `capabilities()` 的 `supportsVision = false`；且 `capabilities()` **main 源集零消费点**（W6-02 / W8-03 挂账） | `core-engine/.../RemoteLlmEngine.kt:89-97`；`docs/06-deferred-ledger.md` W6-02 |
| F4 | 端点编辑页只暴露 6 个基础输入；`draft` 用 `.copy()` 且**硬写 `kind = ProviderKind.OPENAI_COMPATIBLE`** | `feature-settings/.../EndpointEditScreen.kt:82-106`（`:104` 硬写 kind） |
| F5 | `AppContainer.engineFor()` 对非 `OPENAI_COMPATIBLE` 直接 `require` 失败 ⇒ ANTHROPIC/GEMINI 端点**建不出引擎** | `core-data/.../AppContainer.kt:123-133`；`docs/02-roadmap.md:702` X4 ⛔ 未开始 |
| F6 | `applyReasoningStyle()` 是**无 else 的穷举 when**：`NONE / ENABLE_THINKING_BOOL / REASONING_EFFORT / CHAT_TEMPLATE_KWARGS / ANTHROPIC_THINKING_BUDGET / GEMINI_INCLUDE_THOUGHTS` | `core-engine/.../OpenAiCompatibleAdapter.kt:178-189` |
| F7 | adapter 恒发 `stream=true`；`decodeEvent()` 只解析 `choices[0].delta`，思考字段只读 `delta.reasoning_content` / `delta.reasoning`，**不读 `delta.reasoning_details`**；图片 part `content:[{type:text},{type:image_url}]` 已在发 | `OpenAiCompatibleAdapter.kt:76,94-145,279-317` |
| F8 | `ReasoningDelta → AgentEvent.ReasoningDelta → appendReasoning → ChatBubble.reasoning → GlassBubble(thinking=…)` 全链已存在；`AdapterStreamState` 有独立 `reasoningTracker` | `core-agent/loop/GenerationRound.kt:198-201`；`feature-chat/.../EventReducer.kt:46,102-107`；`ChatBubble.kt:114`；`ProviderAdapter.kt:47-50,83-85` |
| F9 | `ChatBubble.kt` 只有一枚复制键；类头注释明说旧仓第二枚「复制为 Markdown」是装饰。**现役无 markdown 渲染**（全仓无 `AnnotatedString`/`buildAnnotatedString` 命中） | `feature-chat/.../ChatBubble.kt:110-135`；全仓 grep |
| F10 | `GlassBubble` 正文两路：流式裸 `Text(text+"▍")`；非流式 `SelectionContainer{Text(text)}`。气泡最大宽 `340.dp` | `core-design/.../GlassSurface.kt:225-322,498-545`（`:254` 340dp） |
| F11 | `core-model` 已 `api(libs.kotlinx.serialization.json)`；`AgentJson.json` 是 `isLenient=true / coerceInputValues=true / encodeDefaults=true / ignoreUnknownKeys=true / explicitNulls=false`；`JsonFileStore` 默认用它，且**解析失败静默返回 null → 回落到预置端点** | `core-model/build.gradle.kts`；`core-model/.../AgentJson.kt:29-35`；`core-data/.../JsonFileStore.kt:24,44-50` |
| F12 | `SamplingParams.reasoningEffort: String = "medium"` 是**死字段**（全仓零生产消费，adapter 只用 `endpoint.reasoningEffort`） | `core-model/.../Generation.kt:73-82`；`OpenAiCompatibleAdapter.kt:182` |
| F13 | `EndpointProbe` 探测请求 `maxTokens = 8`；`engine.generate()` 落在已登记的 G18 例外文件 | `core-data/.../EndpointProbe.kt:54-65`；`scripts/arch-guard.sh:281-285` |
| F14 | `RunnerKey` 含整个 `RemoteEndpoint` ⇒ 任何高级配置改动都会重建 runner、前缀缓存从头计 | `core-data/.../RunnerCache.kt:21-32`；`AppContainer.kt:264-283,348` |
| F15 | `AgentPolicy.maxRunPromptTokens = 400_000`，`RoundHeadGate` 在 `promptTokens > 400_000` 时 `Trip` ⇒ **单请求 995k prompt 会在发出前被熔断** | `core-kernel/.../AgentPolicy.kt:228`；`core-agent/loop/RoundHeadGate.kt:42-46` |
| F16 | `SettingsRepository.MAX_MAX_CONTEXT_TOKENS = 1_000_000`；六档 UI 含 1M；`compressContext` 默认 true | `core-data/.../SettingsRepository.kt:58-67,91-97,141-144`；`feature-settings/.../SettingsScreen.kt:290-311` |
| F17 | `SessionImportParser` 上限：`MAX_BYTES=8MiB` / `MAX_MESSAGES=5000` / `MAX_MESSAGE_CHARS=4MiB`；字节入口 `bytes.toString(UTF_8)` 不剥 BOM，`raw.trim()` 不剥 `\uFEFF` | `core-data/.../SessionImportParser.kt:53,56,67,139,156` |
| F18 | 1M 夹具 `_w6/import-1m.jsonl`：**5 221 133 B、30 行、最长行 180 067 字符、无 BOM**（审查席 `splitlines()` 口径；本席初稿 180 068 为含换行口径差 1，非缺陷）⇒ 三道上限都过得去 | 工作区根 `_w6/import-1m.jsonl`；`w8-review-triage.md §八.5`；`handoff-20261104-wave7.md:84,97` |
| F19 | `ChatViewModel.kt` **487 行 / 上限 500（余 13）**；`GlassSurface.kt` **576/600（余 24）**；`EndpointEditScreen.kt` 338/500 | 本席 `wc -l`；`scripts/arch-guard.sh:152-193` |
| F20 | 逐模块 main 行数：`core-model 1439/2600`、`core-engine 1081/2600`、`core-data 2329/4500`、`core-design 12439/12600(余161)`、`feature-chat 3619/3900(余281)`、`feature-settings 3229/3400(余171)`、`app 1338/1600`；合计 **35 701 / 42 500**。Σcaps = **46 000 > TOTAL 42 500**（缺口 3 500，两套约束关系已断，W67 P2 属实） | 本席逐模块 `wc -l`；`scripts/arch-guard.sh:152-204` |
| F21 | `GlassSegmented` 使用 `clearAndSetSemantics` ⇒ **uiautomator dump 读不到分段项文字/bounds** | `core-design/.../GlassSegmented.kt:33`（import） |
| F22 | 无 `compose-ui-test` / `robolectric` / `coroutines-test` 依赖 ⇒ 无法用 Compose UI 单测钉渲染 | `gradle/libs.versions.toml`；全仓 grep |
| F23 | `ChatInputBar` 附件按钮由 `onPickImage != null` 决定渲染；`AppNavHost.ChatRoute` **无条件**传 `pickImage` | `feature-chat/.../ChatInputBar.kt:72`；`app/.../AppNavHost.kt:155-170` |
| F24 | `ChatTopBar` 溢出菜单只在 `onCompressContext != null` 时渲染 | `feature-chat/.../ChatTopBar.kt:80-84,132-146` |
| F25 | 纯文档提交被 `build.yml` `paths-ignore: docs/** , **/*.md` 挡住 = **0 run**，不等于 CI 绿 | `.github/workflows/build.yml:47-53`；`AGENTS.md §八` |
| F26 | 旧仓 `Android-Agent` 有 `ThinkingMode OFF/ON/AUTO` 分段控件（可复用**控件形态**）；但旧仓**无 markdown 渲染器**，且 `rememberAudioPicker/rememberFilePicker` 零消费点 | `Android-Agent/feature-settings/.../SettingsScreen.kt:478-496`；本席全仓 grep |

### 0.2 采信与证伪（外审/交接按铁律①先核）

- **采信**：主理人 Phase 0 dots 实测（`docs/analysis/w8-provider-probe.md`）；审查席回树判定（`docs/analysis/w8-review-triage.md` 507 行，只读）；W7 交接 §四 A1–A7 / §五多模型硬结论；`docs/notes/w7-provider-matrix.md §二` 判据自证；`docs/06-deferred-ledger.md §零` 26% 挂账丢失率。
- **证伪 / 不采信**（审查席已核，本席复核关键处）：
  - 复审41「selftest 30/30」→ 现役 `CASES=28`（`arch-guard-selftest.sh:105-139`），按审查席口径采信；
  - W67「Σcaps 43400」→ 实为 **46 000**（漏 `core-model 2600`，本席复算一致）；
  - W8深度审查 P1-3 编号撞车属实且更重：`docs/notes/w7-context-cache-roadmap.md` 的 W8-01..05 与台账 W8-01..04 **全不同义**，C4 又建议新开 W8-05 ⇒ 三重撞车；G20 只扫 `docs/handoff-*.md`，**不扫 `notes/`**；
  - NEW-F「5 处注释误导」→ 现役注释已写「尚未落地，挂账 W3-05」，**不把改注释列入交付物**；
  - C11.2「KDoc 笔误 3 处」→ 已修；
  - W8-03 二选一**不是 ≤30 行快修**：14 文件、争 `GenerationEvent.kt` / `RemoteLlmEngine.kt`（功能波必改）；
  - W7-02 **不得 CLOSED**（`handoff-20261104-wave7.md:93` 明写不承诺命中率）；
  - C6.a「收敛为单真值源」被夸大：`AppContainer.kt:265`、`SettingsViewModel.kt:229` 仍裸取同一 Flow，修两行仍有拷贝。
- **本席观察（事实成立，但结论已被证伪 → 见 §12.1 C1）**：主理人第一批 20 余份 `_w8_*.json`
  响应**全是 `object:"chat.completion"` + `choices[0].message`（非流式）**，5 份已归档请求体
  `"stream": false`；而现役 adapter 恒 `stream=true` 且只读 `delta`。
  ✅ **保留的事实**：「证据缺口」与「请求体未归档导致不可复算」成立，主理人据此补跑 9 组 `stream:true`
  并按 W7 先例落 `_w8/req-*.json` / `resp-*.sse` / `parse-*.json`。
  ⛔ **撤回的结论**：初稿据此推断「流式存在 `delta.reasoning_details` 断链、App 真实路径只能标 ⚠️」，
  实测**相反**——`delta.reasoning` 与 `reasoning_details` 逐字等长、现役 `:106-108` 回落命中，
  链路本就通（§3.3）。「App 恒流式」本身仍升为**全部请求格式判据的前置条件 M4**（§7.1）。
  措辞订正（审查席）：响应体**无 `stream` 字段**，非流式由 `object` + `message` 判定，`stream=false`
  是请求体字段。

### 0.3 四态与判据用语

`✅` 判据被行使且达阈值；`⚠️` 部分行使/部分达阈值；`⛔` 判据行使后失败或环境不适用；
`⛔vacuous` 判据根本没被行使（含「测试全绿但口径没跑到」「0 命中」）。**`⛔vacuous` 绝不写成 ✅。**

---

## 1. 方案总览

### 1.1 波级方案对比（含设备硬件约束——方案席专属维度）

| 方案 | 优点 | 缺点 | 适用条件 | **设备硬件约束** |
|---|---|---|---|---|
| **A（推荐）功能主波 + 同波只收 ≤30 行重合快修 + 重型正确性挂账 W9** | 与用户 ①–⑦ 指令一致；两席位文件域可切干净；真机验收面收敛；不动任何 cap | 正确性债再延一波（W3-05 欠至第六波）；C1 入账/C13 预登记只能以文档形式同波做 | 用户指令优先、四份外审均把 W8 误设为正确性波 | OPPO PDRM00 单设备可承载新增 UI 与 adb 取证；`core-design/feature-chat/feature-settings` 余量 161/281/171 行够用；1M 设备侧因 `maxRunPromptTokens=400k` 本波仍 ⛔vacuous，需显式登记而非回避 |
| B 外审优先：S0→S3 正确性收口波，功能顺延 W9 | 一次性清 4.0 尺四面 / 尾部摘要 / 三桶归一 / journal | **直接违背用户 W8 功能指令**；与功能波争 `ConversationOps.kt`/`ChatViewModel.kt`/`AppContainer.kt`；C3 的 D9 扩列 + 双件交付物数百行必触 G10 | 仅当用户改口要正确性波 | 真机需重放 A5 的 79.4% 缓存基线，设备侧 1M/压缩链路现无恢复手段（C2「裸奔」）；`ChatViewModel.kt` 余 13 行，重构必先拆 |
| C 同波大爆炸：功能 + 正确性重构 + 快修全上 | 账面一次做完 | W7 双写覆盖 3 次重演（`handoff-20261104-wave7.md:108`）；G10/G21/EXPECTED_TESTS 三处同时红；判据互相污染（改配置 vs 压缩都令 `cached_tokens=0`） | 无 | 单设备排队，无法并行取证；`uiautomator` 对 Compose bounds 不可靠，多变量同时变更无法归因 |

**裁决：采用 A**，附 §9 的 5 条订正。

### 1.2 工作包拆分（每包可独立验收）

依赖：`WP0 → (WP1→WP2→WP3) ∥ WP4 → WP5 → WP6`。WP1–WP4 为两路并行席位；WP0/WP5/WP6 由主理人单人串行（共享禁区）。

#### WP0 · 跑前预登记与基线自证（主理人，纯文档，必须与代码同车提交）

| 输入 | 输出 | 判据（触发条件 → 空命中语义） | 四态 |
|---|---|---|---|
| `AGENTS.md §七`、C13 预登记条款、W7 交接 §四 | `docs/notes/w8-preregistration.md`：波号裁决、四态表、L1/L2/L3 术语映射、A15 修订版双口径、`max_tokens` 前提、C8 思考 id 共设计草案 | 触发：文件中每个判据都有「可判定谓词 + 空命中语义」；样本：`{"a":1}` 必通过、`{"a":` 必失败先跑一遍。空命中：缺任一项 ⇒ 该判据 ⛔vacuous，不进验收 | ✅（文档齐）／⛔vacuous（缺项） |
| 同上 | 台账「外审承接表」节（**写成列表，不写成首列 ID 的表格**，否则 G20 会误判列数，`docs/06-deferred-ledger.md:145-146` 有明训） | 触发：四份外审每个未落地条目在表中出现；空命中：漏一条即视为挂账丢失 | ✅／⛔ |
| 同上 | 编号唯一源裁决：挂账统一 **W9-0x**；`w7-context-cache-roadmap.md` 的 W8-0x 改 **R-xx** | 触发：`grep -rn "W8-0"` 在 `notes/` 只剩 R-xx；空命中：仍存在 ⇒ 撞车未解除 | ✅／⚠️ |

#### WP1 · 端点高级配置数据模型 + 兼容（席位 A）

| 输入 | 输出 | 判据 | 四态 |
|---|---|---|---|
| F1/F11/F12/F14、`03-contracts.md §六`、G17 | `RemoteEndpoint` 新增字段（全带默认值）+ 新文件 `core-model/.../EndpointCapabilities.kt`（枚举 + `resolveReasoning()` 纯函数） | 触发：老 `endpoints.json`（缺全部新键）→ JVM 单测读回**且用户自定义端点不丢**；新值 round-trip 一致。空命中：`JsonFileStore.read()` 静默回落 seeds ⇒ **⛔vacuous 而非通过** | ✅／⛔vacuous |
| 同上 | `EndpointStore.defaultSeeds()` 高级字段默认值 | 触发：`EndpointStoreTest` 断言三组预置的高级字段；空命中：未断言 ⇒ ⛔vacuous | ✅／⛔vacuous |
| 同上 | 删除/裁决 `SamplingParams.reasoningEffort` 死字段（本波只写「不消费 + KDoc 标注」，删字段挂 W9） | 触发：全仓 grep 确认无第二把尺；空命中：出现第二个 effort 真值源 ⇒ ⛔ | ✅／⛔ |

#### WP2 · 协议层（席位 A）

| 输入 | 输出 | 判据 | 四态 |
|---|---|---|---|
| WP1、F6/F7/F8、主理人 dots 口径 | `ReasoningStyle` 扩 `OPENROUTER_REASONING_OBJECT`；`applyReasoningStyle()` 新分支；`include_reasoning` 兼容发法；`decodeEvent()` 支持 `delta.reasoning_details` | 触发：`OpenAiCompatibleAdapterTest` 逐键断言请求体（已知样本先跑：`reasoning.enabled=false` ⇒ `content` 精确 `PONG`、rtok=0）；空命中：只测「不抛异常」⇒ ⛔vacuous | ✅／⛔vacuous |
| 同上 | `stream:true` 对照实测（PC 侧，见 §7.2 T0） | 触发：至少 1 组流式响应含思考 delta 且 `GenerationEvent.ReasoningDelta` 非空；空命中：只做了非流式 ⇒ **全部请求格式结论 ⚠️ 部分** | ✅／⚠️ |
| 同上 | `RemoteLlmEngine.capabilities():92` 改为 `endpoint.supportsVision` | 触发：JVM 用例断言 true/false 两态；空命中：只改了这行但无人消费 ⇒ ⛔vacuous（F3） | ✅／⛔vacuous |
| 同上 | Q11 schema 白名单逐键断言（随 adapter 同车） | 触发：请求体键集合精确断言；空命中：只测白名单函数 ⇒ ⛔vacuous | ✅／⛔vacuous |

#### WP3 · 端点高级配置 UI（席位 A）

| 输入 | 输出 | 判据 | 四态 |
|---|---|---|---|
| WP1、`EndpointEditScreen.kt:82-106`、G11/G16 | 新文件 `feature-settings/.../EndpointAdvancedSection.kt`（≤120 行/方法）；`EndpointEditScreen.kt` 只加 1 个调用 + 状态键；新 `strings_endpoint_advanced.xml` | 触发：**真机**保存后 `adb exec-out` 读回 `endpoints.json`，逐字段比对；切换渲染真读到 `buildBody` 差异日志。空命中：UI 画了但 `draft.copy()` 没带字段 ⇒ **⛔vacuous（装饰组件）** | ✅／⛔vacuous |
| 同上 | `draft` 的 `remember(...)` 键列表同步纳入全部新状态（`:97`） | 触发：旋转屏幕（OPPO 真机）后字段仍在；空命中：丢失 ⇒ ⛔（`Set<enum>` 需自定义 Saver） | ✅／⛔ |

#### WP4 · 会话显示：Markdown + JSON 检查 + 图片入口闸门（席位 B）

| 输入 | 输出 | 判据 | 四态 |
|---|---|---|---|
| F9/F10/F21/F22、WP1 的 `inputModalities` | `core-model/.../MarkdownParser.kt`（纯 JVM）、`core-model/.../JsonCheck.kt`（严格 `Json`）、`feature-chat/.../MarkdownText.kt`、`ChatBubble.kt` 第二枚动作键、`GlassSurface.kt` 增 `annotatedText: AnnotatedString?` 参数 | 触发：纯函数单测「已知必通过 + 已知必失败」样本先跑；**真机截图 A/B**（render on/off）+ 日志行 `markdown_render=on|off`。空命中：只有纯函数单测、无真机 A/B ⇒ ⚠️（本仓无 Compose UI 测试依赖，F22） | ✅／⚠️ |
| 同上 | `ChatTopBar` 溢出菜单新增切换项；`SettingsRepository` 新键 `chat_markdown_render`（默认 false） | 触发：toggle 后截图差异 + `adb exec-out` 读回 DataStore；空命中：菜单项不可点 ⇒ ⛔ | ✅／⛔ |
| 同上 | `AppNavHost.ChatRoute` 按 `supportsVision` 把 `onPickImage` 置 null | 触发：`supportsVision=false` ⇒ `ChatInputBar:72` 不渲染 attach 按钮；`true` ⇒ 渲染 + 200×120 真图回环。空命中：只改 `capabilities()` ⇒ ⛔vacuous | ✅／⛔vacuous |

#### WP5 · 同波快修批 + 闸门同步（主理人单人，共享禁区）

| 输入 | 输出 | 判据 | 四态 |
|---|---|---|---|
| 审查席 §三 Q1–Q14 | Q1/Q2/Q3/Q4/Q5/Q6/Q7/Q11/Q13/Q14 逐条（文件域见 §8.2） | 触发：每条有 `文件:行` + 修法 + 证明判据；空命中：无判据的快修 ⇒ ⛔vacuous | ✅／⛔vacuous |
| `build.yml:132` | 新增 `@Test` 数 → **同一次提交**同步 `EXPECTED_TESTS`（`:445-523` 双向对账） | 触发：`fulltest` 输出用例数 == 常量；空命中：常量没改 ⇒ CI 必红 | ✅／⛔ |
| `scripts/arch-guard.sh` | 不新增守卫、不改 LINE_CAPS / TOTAL_LINE_CAP / VACUOUS_BASELINE | 触发：`arch-guard` `OK=27 VACUOUS=1 VIOLATION=0`；空命中：新增守卫扫空集 ⇒ VACUOUS 1→2，G21 判红 | ✅／⛔ |

#### WP6 · 真机验收 + 多模型矩阵 + push/CI（主理人 + 全员）

| 输入 | 输出 | 判据 | 四态 |
|---|---|---|---|
| WP1–WP5 合并后的 debug APK | 本地 UI/功能测试 → adb 真机四态表（§6.3）→ 四闸门 → push → CI | 触发：用户 ⑤ 的顺序（**真机没问题才推仓库及 CI**）；`gh run list --branch main` 能看到本次提交的 run；空命中：0 run ≠ 绿（F25） | ✅／⛔vacuous |
| `_w8/` 请求体归档 | `_w8/requests.jsonl` + `raw/req-*.json`（照 W7 先例） | 触发：每个结论都能回查到完整请求体；空命中：只有响应 ⇒ ⚠️ 部分 | ✅／⚠️ |

---

## 2. 端点高级配置的数据模型设计

### 2.1 逐字段裁决：复用 / 新增 / 派生

> 用户 ① 的 11 项：工具调用、图片输入、思考模式、仅思考模式、允许关闭思考、自定义协议、
> 默认思考强度、支持的思考强度（五档）、输入、输出。下表逐项落位。

| 用户项 | 裁决 | 字段 / 类型 | 默认值 | 消费者（必须存在，否则 ⛔vacuous） | 依据 |
|---|---|---|---|---|---|
| 工具调用 | **复用** | `supportsTools: Boolean` | `true` | `OpenAiCompatibleAdapter.kt:72`（不发 tools）；`AppContainer.kt:279`（空注册表） | F1 |
| 图片输入 | **新增 + 派生** | `inputModalities: Set<Modality>`；`val supportsVision: Boolean get() = IMAGE in inputModalities` | `setOf(TEXT)` ⇒ `supportsVision=false` | `RemoteLlmEngine.kt:92`；`AppNavHost.ChatRoute:170`；`OpenAiCompatibleAdapter.contentElement()` | F2/F3/F23；NEW-D |
| 思考模式 | **新增** | `reasoningMode: ReasoningMode`（`OFF / AUTO / ON` 三态枚举，存为 String 常量集，见 2.2） | `AUTO` | `resolveReasoning()` → `applyReasoningStyle()` | 主理人口径② |
| 仅思考模式 | **新增** | `reasoningOnly: Boolean`（**展示/终止侧语义**，非请求参数） | `false` | `ChatBubble`（思考块当主内容）+ `ProviderAdapter.terminationReason` 判据；**不进 `RemoteEndpoint`**（避免 RunnerKey 无谓重建，F14），放 `SettingsRepository` 会话显示键 | 主理人口径②；`_w8_dots_tools.json` content=None |
| 允许关闭思考 | **新增（能力位）** | `allowDisableThinking: Boolean` | `false`（fail-closed，同 `allowPrivateNetwork:134` 纪律） | `resolveReasoning()` 校验 `OFF` 合法性；UI 禁用 OFF 档 | OR `/v1/models` `reasoning={"mandatory":false}`（本席解析 `_w8_or_models.json` 两个 OR 模型均如此） |
| 自定义协议 | **复用 + 新增** | 复用 `extraHeaders: Map<String,String>`、`preset: UiPreset.CUSTOM`；新增 `customBodyParams: Map<String,String> = emptyMap()`（**白名单键**） | 空 | `OpenAiCompatibleAdapter.buildBody()`；`EndpointEditScreen` 高级区 | F4/F5；见 2.6 |
| 默认思考强度 | **复用** | `reasoningEffort: String` | `"medium"` | `applyReasoningStyle()`；UI 只提供五档下拉 | F1/F12 |
| 支持的思考强度 | **新增** | `supportedEfforts: Set<ReasoningEffort>`（`LOW/MEDIUM/HIGH/XHIGH/EXTREME`） | 空集 = **未声明**（不是"全支持"） | `resolveEffort()` → UI 档位可用性 + 降级落账 | 主理人口径③；OR 文档 |
| 输入 | **新增** | `inputModalities: Set<Modality>` | `setOf(TEXT)` | 见「图片输入」 | 见 2.3 裁决 |
| 输出 | **新增** | `outputModalities: Set<Modality>` | `setOf(TEXT)` | UI 能力卡；**音频输出标 ⛔ 不适用**（§6.1/§7.1） | `_w8_or_models.json` 两模型 `output_modalities=["text"]`、`supported_voices=None` |
| （配套）思考参数风格 | **扩枚举** | `ReasoningStyle += OPENROUTER_REASONING_OBJECT` | — | `applyReasoningStyle()` | 主理人口径①；审查席硬约束 3 |

新增枚举：`enum class Modality { TEXT, IMAGE, AUDIO, VIDEO, FILE }`、
`enum class ReasoningEffort { LOW, MEDIUM, HIGH, XHIGH, EXTREME }`、
`ReasoningMode { OFF, AUTO, ON }`。
**派生（不持久化）**：`supportsVision`、`requiresApiKey`（已有，`:144-145` 是同款先例）、
`resolveReasoning(endpoint): ReasoningPlan`、`resolveEffort(...): EffortDecision`。

🔴 **三个布尔不能互相矛盾**：`reasoningMode`（请求态）/ `reasoningOnly`（展示态）/
`allowDisableThinking`（能力态）在**三个不同轴**上，用单一纯函数归一；详见 2.5。
🔴 **每个新增字段必须给「被调用」判据**（铁律②、NEW-G 家族）：见 §10 判据表 P1–P9。

### 2.2 `@Serializable` 兼容策略（老 `endpoints.json` 缺键必须能读）

- 现役链路：`EndpointStore.loadOrSeed():84` → `JsonFileStore.read():45` → `AgentJson.json`（F11）。
  `ignoreUnknownKeys=true` 处理「新版读老文件里的未知键」；**缺键靠每个字段带默认值**（`docs/03-contracts.md:4,666`）。
  ⇒ 本波所有新增字段**一律带默认值**，不写 `@Required`，不改 `AgentJson.json` 任何开关。
- **G17**：`scripts/arch-guard.sh` g17 扫描 `core-model/src/main` 中 `@Serializable` 类的
  `val|var NAME:` 声明，精确匹配 `apikey|secret|password|token`（大小写不敏感）。
  ⇒ 新字段**禁止命名为 `token` / `secret` / `password` / `apiKey`**；`customBodyParams`、
  `supportedEfforts`、`inputModalities` 等均安全。`hasSecret`（已有）不是精确名，安全。
  ⇒ KDoc 里也不能写出形如 `val apiKey:` 的行（g17 注释/形态纪律）。
- **🔴 单向兼容的静默陷阱（本席新增风险）**：`ReasoningStyle` 扩枚举后，若新版 App 把
  `OPENROUTER_REASONING_OBJECT` 写进 `endpoints.json`，老版本 App 反序列化未知枚举值会抛，
  而 `JsonFileStore.read():48-50` **catch 所有异常并返回 null** → `EndpointStore.loadOrSeed()`
  回落到预置端点，**用户自定义端点被静默清空**。
  ⇒ 判据不能只测「不崩」，必须测「写入 → 换 store 实例读回 → 用户端点仍在」
  （`SettingsContractTest.savedEndpointSurvivesRestart()` 是现役先例）。
  ⇒ 建议本波在 `EndpointStoreTest` 加一条「未知枚举值 ⇒ 不静默重置」用例；若判红，
  修法落 `JsonFileStore` 的 KDoc + 一条 warn（**不是新增守卫**），并把完整降级挂 W9。
- **G17 / G6 / G8 边界**：新枚举与 `resolveReasoning()` 放 `core-model` 新文件
  `EndpointCapabilities.kt`，只用 kotlin 标准库（G8 禁 `android.|androidx.|java.net.` import）；
  `core-engine` → `core-model` 是既有边（G6 无新增边）；`core-design` 不得 import 任何 `core.*`（G7）。
- **`extraHeaders` / `customBodyParams` 的密钥红线**：二者是 `Map<String,String>`，值会**明文落
  `endpoints.json`**（`AGENTS.md §六` 五处泄漏面之「落盘」）。`SecretRedactor.redactHeaders()`
  现役存在但 `core-engine` 无调用点（`docs/analysis/source-review-2026-11-04.md:66`）。
  ⇒ 自定义协议字段必须：① 键白名单（禁 `Authorization`/`Cookie`/`X-Api-Key` 等凭键名）；
  ② 值走 `EndpointSecretStore` 信封或拒绝保存；③ 一条 JVM 用例钉住「含 `sk-` 的值被拒」。
  否则本波会新开一个明文密钥出口。

### 2.3 「输入 / 输出」裁决：**按模态（input/output modalities）**

| 解释 | 优点 | 缺点 | 适用条件 | **设备硬件约束** |
|---|---|---|---|---|
| **A（裁决）模态集合 text/image/audio/video/file** | 与 OR `/v1/models` 的 `architecture.input_modalities/output_modalities` 同源，可机器回填；与用户 ⑥「多模态输入、音频输出」对齐；与现役 `AttachmentKind{IMAGE,AUDIO,TEXT}` / `GlassAttachmentKind` 一致；`supportsVision` 可由 `IMAGE∈input` 派生 ⇒ 单一真值源 | 需新增枚举 + 老文件默认迁移 | 本波 | OPPO PDRM00 的 SAF/相机/音频采集已验（`ChatAttachmentPicker.kt`）；`output=AUDIO` 在本设备仅 iflytek TTS 且 `speak` 未注册（`AppContainer.kt:170-178`）⇒ 必须标 ⛔ 不适用，不做可点的假开关 |
| B 输入/输出 token 上限 | 直观 | **与 `contextLength`/`maxOutputTokens`/`verifiedContextLength` 重复 ⇒ 第二把尺**（W5-01 的 33.8K 事故同源）；`SwitchGuard` 已用 verified 优先 | 否 | 设备侧 1M 档已有 `SettingsRepository:144`，再加一份必然分叉 |
| C 输入/输出价格 | 与 OR `pricing` 同源 | 本仓无计费 UI、零消费点 ⇒ 死字段（NEW-G 第 6 例） | 否 | — |
| D 输入/输出格式（JSON/文本） | 与 ③ JSON 检查相邻 | 与用户 ③ 重叠；且属 `response_format` 能力，不是端点静态属性 | 否（挂 W9 见 §5.1） | 会改请求体前缀 ⇒ 与 `RunnerKey`/KV cache 打架（F14） |

**裁决 = A**，理由：① 可取证（元数据直读）；② 与用户 ⑥ 对齐；③ 不制造第二真值源；
④ 音频输出按 W7 结论 ⛔ 不适用登记，不摆假入口（`SettingsScreen.kt:36-37` 的既有纪律）。
⚠️ 歧义留痕：若用户原意是「输入/输出格式」，则与 ③ 合并处理，需主理人向用户确认一句；
本方案按 A 落地，确认前不写死为产品承诺。

### 2.4 五档思考强度（低 / 中 / 高 / 超高 / 极致）映射表

**协议族前提**：`AppContainer.engineFor():124` 只放行 `OPENAI_COMPATIBLE`，
ANTHROPIC / GEMINI adapter **未实现**（X4 ⛔ 未开始，`docs/02-roadmap.md:702`）。
⇒ 下表 Anthropic / Gemini 两列是**契约预留**，本波判据一律标 **⛔ 不适用**，不得写成「已支持」。

| 档位 | `reasoning.effort`（OR 统一对象，新增 style） | 顶层 `reasoning_effort`（OpenAI 兼容旧风格） | Anthropic `thinking.budget_tokens`（预留） | Gemini `thinkingConfig`（预留） | 降级 / 备注 |
|---|---|---|---|---|---|
| 低 | `low` | `low` | `0.2 × max_output_tokens`（clamp ≥1024） | `thinkingLevel=LOW` + `includeThoughts=true` | — |
| 中 | `medium` | `medium` | `0.5×` | `MEDIUM`（OR 会把非常规值就近映射） | **默认**（`reasoningEffort="medium"`） |
| 高 | `high` | `high` | `0.8×` | `HIGH` | — |
| 超高 | `xhigh` | `xhigh`（仅部分 o 系；否则降 `high`） | `0.95×` | OR 文档：Gemini 3 把 `xhigh` 映射为 `high` | **必须 `max_tokens ≥ 1500`**：实测 `_w8_eff_xhigh.json` `max_tokens=600` ⇒ `finish=length`、content=null、`reasoning_tokens=688 > completion_tokens=600`（口径异常，需 ≥2 次重放） |
| **极致** | **禁止静默映射 `max`** ⇒ 显式降 `xhigh` + `degraded=true` + UI 提示 + 日志 `effort_degraded from=EXTREME to=xhigh` | 降 `high` | `0.95×`（与 xhigh 同分配） | `HIGH` | 主理人实测同题面 `max=198` rtok 落在 medium 与 high 之间、不服从单调性 ⇒ 疑似非法值被忽略；OR 官方文档明写 `xhigh` 与 `max` 分配相同（≈95%）⇒ **五档在 OR 上实际只有 4 个可区分档，必须如实告知用户，不做"看起来五档其实四档"的装饰** |

**降级规则（写进纯函数 `resolveEffort`，不得只写在文档）**：

1. `reasoningStyle ∈ {NONE, ENABLE_THINKING_BOOL, CHAT_TEMPLATE_KWARGS}`（布尔风格）
   ⇒ **不下发任何 effort**，UI 禁用档位选择，落账 `reason=style_no_effort`（**不静默**）。
   空命中语义：布尔风格下用户选了「极致」但请求体无 effort ⇒ 判 ⛔ 不是 ✅。
2. `supportedEfforts` 非空 ⇒ 只取集合内档位；用户选了集合外档位 ⇒ 取**不高于所请求的最近已支持档**
   （OR 文档："map your requested effort to the nearest supported level"），落 `effort_degraded`。
3. `supportedEfforts` 为空（OR `/v1/models` 对 dots/north 只返回 `reasoning:{"mandatory":false}`，
   **未给出 `supported_efforts`**；OR 文档说省略即"模型不暴露 effort selection"）
   ⇒ 按 W8 实测白名单 `LOW/MEDIUM/HIGH/XHIGH` 四档，`EXTREME` 走第 4 条显式降级。
4. `EXTREME` 一律显式降级（永不发 `max`），`degraded=true` 上屏可见。
5. `effort` 与 `reasoning.max_tokens` **二选一，不能同时发**（OR 文档 "One of the following (not both)"）；
   `enabled:false` 优先于 effort（OR 文档：disabled 即使带 effort 也关思考）。
6. `allowDisableThinking=false`（mandatory）⇒ 禁发 `effort:"none"`，隐藏 OFF 控件（OR 文档）。

**外部依据标注**：OR 官方 `/docs/use-cases/reasoning-tokens`（2026-11-05 抓取，
首 URL `/docs/guides/features/reasoning-tokens` 返回 404）为**外部文档，需实测复核**；
与主理人 W8 PC 实测冲突时以实测为准（`AGENTS.md §二.2`）。

### 2.5 「思考模式 / 仅思考模式 / 允许关闭思考」语义边界与互斥

**问题**：三项若各做一个布尔，会出现 `思考模式=true && 仅思考模式=true && 允许关闭思考=false`
这类自相矛盾组合（本仓「Boolean → sealed 是消灭静默的范式」，知识库 §3）。
**裁决：三者分处三个轴，用单一纯函数 `resolveReasoning(endpoint): ReasoningPlan` 归一。**

| 概念 | 轴 | 语义 | 线上形态 | 消费者 |
|---|---|---|---|---|
| **思考模式** `reasoningMode` | 请求态（用户意图，三态枚举） | `OFF` 显式关 / `AUTO` 跟随模型默认（不发任何 reasoning 字段） / `ON` 显式开 | `OFF`→`reasoning:{enabled:false}`（实测 `_w8_fmt_off.json`：reasoning=null、rtok=0、content 精确 `PONG`）；`ON`→`reasoning:{effort:…}` | `applyReasoningStyle()` |
| **仅思考模式** `reasoningOnly` | **展示/终止侧**（非请求参数） | 模型只吐思考、正文为空/空白；UI 把思考块当主内容；`finish=length + content=null` 不得判成断流或空输出 | **不发任何请求字段** | `ChatBubble` / `ProviderAdapter.terminationReason()` 判据 |
| **允许关闭思考** `allowDisableThinking` | **能力位**（模型/网关声明，非用户开关） | 端点是否接受显式关思考；OR `reasoning.mandatory=false` ⇒ true | 决定 `OFF` 档是否可用；mandatory ⇒ 隐藏禁用控件、不发 `effort:none` | `resolveReasoning()` 校验 + UI 置灰 |
| **仅隐藏思考**（主理人口径②新析出） | 请求态子项 `hideReasoning` | 思考照跑照计费、只是**不回传思考文本** | `include_reasoning:false`（legacy，实测 `_w8_fmt_incl.json`：reasoning 字段消失但 `reasoning_tokens=42`）≡ OR 统一 `reasoning:{exclude:true}` | `applyReasoningStyle()` |

**互斥真值表（`resolveReasoning` 必须实现，JVM 单测逐行钉住）**：

| `supportsReasoning` | `allowDisableThinking` | `reasoningMode` | `reasoningOnly` | `hideReasoning` | 裁决 | 落账 reason（不静默） |
|---|---|---|---|---|---|---|
| false | * | * | * | * | 全部 reasoning 字段忽略，请求体不发任何相关键 | `reasoning_unsupported` |
| true | false | `OFF` | * | * | **拒绝并降级 AUTO**，UI 禁用 OFF 档 | `off_not_allowed_mandatory` |
| true | true | `OFF` | `true` | * | **矛盾组合**：`reasoningOnly` 强制 false | `only_cleared_when_off` |
| true | true | `OFF` | * | `true` | `hideReasoning` 强制 false（OFF 时无思考可隐藏） | `hide_cleared_when_off` |
| true | * | `ON` | `true` | `true` | **矛盾组合**：`reasoningOnly` 要求思考可见 ⇒ `hideReasoning` 强制 false | `hide_cleared_when_only` |
| true | * | `ON` | `false` | `true` | 合法：`reasoning:{exclude:true}`，思考仍计费 | `exclude_billable` |
| true | * | `AUTO` | * | * | 不发 reasoning 字段；`reasoningOnly/hideReasoning` 只在展示侧生效 | `auto_follow_provider` |

**空命中语义**：若 `resolveReasoning()` 未被 `applyReasoningStyle()` 调用，则上表全部行是纸面
⇒ 判 ⛔vacuous。判据 = `buildBody()` 的产物断言，不是纯函数单测（铁律②）。

🔴 **NEW-E 交互提示**：`RemoteLlmEngine.track():301` 把 `ReasoningDelta` 计入 `textChars`，
所以「思考到达 + 正文空」时 `terminationReason():192` 不会误标 STOP；但
`hideReasoning=true`（`exclude`）若把思考也隐掉且正文为空 ⇒ `textChars==0 ⇒ STOP`
（`ProviderAdapter.kt:189-194`，FastestAI NEW-E 已核实未修）。本文裁决 **NEW-E 挂 W9**，
但本波「仅隐藏思考」真机判据必须显式包含 `finish_reason=stop && textChars==0 && 无 [DONE]`
这一形态并标 ⚠️ 部分，不许写成通过。

### 2.6 「自定义协议」裁决

| 解释 | 优点 | 缺点 | 适用条件 | **设备硬件约束** |
|---|---|---|---|---|
| **A（裁决）OpenAI 兼容族内的自定义请求头 / 请求体覆写**（`extraHeaders` + 白名单 `customBodyParams` + `baseUrl` 完整路径） | 与 `ProviderKind` 的"传输族不是 UI 预设"设计一致（`RemoteEndpoint.kt:5-15`）；`UiPreset.CUSTOM` 已存在；`OpenAiCompatibleAdapter.endpointUrl():50-55` 已支持完整路径 | 需要键白名单 + 密钥脱敏（2.2） | 本波 | 不需新权限；`UrlGuard` / `GuardedDns` 既有双闸仍生效，私网开关沿用 `allowPrivateNetwork` |
| B 选择 ANTHROPIC / GEMINI 协议族 | 看起来"支持多协议" | **假入口**：`AppContainer.engineFor():124` `require` 直接抛，端点存了也发不出；X4 adapter 未实现；`EndpointEditScreen.kt:104` 还硬写回 OPENAI_COMPATIBLE | **否（⛔ 不适用）** | 真机点选后必然崩/无响应，且 uiautomator 无法归因 |
| C 完全自由的 JSON 请求体 | 灵活 | 可覆盖 `model`/`messages`/`stream`，破坏 SSE 契约；密钥明文落盘 | 否 | 流式解码路径失控，设备取证不可复现 |

**裁决 = A**，理由：本仓 `SettingsScreen.kt:36-37` 明训「摆一个点了没反应的开关，比没有这个开关更糟」；
B 会在真机上造出"能保存、不能用"的端点。若用户确要多协议族，属 **X4 独立波**，
且必须先做 C8 的「思考回传 id 稳定 + 前缀指纹共设计」预登记（`复审41 C8`）。

---

## 3. 协议层改动点

### 3.1 将被触碰的具体文件 / 函数 + 守卫影响

| 文件 | 函数 / 位置 | 改动 | 包边界 / 守卫影响 |
|---|---|---|---|
| `core-model/.../RemoteEndpoint.kt` | `:64-82` 枚举、`:97-146` 字段 | 扩 `ReasoningStyle` 1 值；新增字段（全带默认值） | **G17**（字段名禁 `token/secret/password/apiKey`）；G8（纯 Kotlin）；单文件 146/300 有余 |
| `core-model/.../EndpointCapabilities.kt`（新） | `Modality` / `ReasoningEffort` / `ReasoningMode` / `resolveReasoning()` / `resolveEffort()` | 纯 JVM，零 Android 依赖 | G8 合规；G17 扫描范围内（无禁名）；**G6 无新增边** |
| `core-engine/.../OpenAiCompatibleAdapter.kt` | `buildBody():69-92`、`applyReasoningStyle():178-189` | 新 reasoning 分支（`reasoning:{"enabled":false}` / `{"effort":wire}` / `{"exclude":true}`）；`response_format`（`jsonMode` 驱动）；`include_reasoning`（**仅 `HIDE_ONLY`**）；`extraBody` 白名单 merge（**不得覆盖** `model/messages/stream/stream_options/max_tokens/tools`）。**🔴 不改 `decodeEvent():94-145`、不改 `contentElement():279-317`**（§3.3 已证伪断链；`contentElement` 已正确发 `image_url` 只补回归用例） | **F6 是无 else 穷举 when** ⇒ 扩枚举编译期强制同步所有分支（知识库 §3「sealed/enum 新增子类型必须同步所有 when」）；`core-engine/src/main` 是 **G19 扫描面**，本波**不要在 core-engine 加日志**（G19 现 VACUOUS，加日志会让其首次开火且 `SecretRedactor` 出口未接满，见 `source-review-2026-11-04.md:66`）；G18 不新增 `.generate(` |
| `core-engine/.../ReasoningBody.kt`（新） | `buildReasoningObject()` | 把 reasoning 对象构造外提，保 `applyReasoningStyle()` ≤ G11 的 120 行；硬不变量 `effort` 与 `max_tokens` **绝不共存**、`wire != "max"`、降级必落 `reason` | 席位 A 独占；`core-engine` 1081/2600 余量充足 |
| `core-engine/.../RemoteLlmEngine.kt` | `capabilities():89-97` | `supportsVision = endpoint.supportsVision` | 与挂账 W8-03 争同文件 ⇒ **W8-03 挂账后本文件单一写者（席位 A）**；F3 提醒：修它本身是 ⛔vacuous，须配 UI 闸门判据 |
| `core-engine/src/test/.../OpenAiCompatibleAdapterTest.kt` | `:141-176`（现有 6 个 reasoning 用例 + `:172-174` 显式枚举列表） | 新增逐键请求体断言、五档降级、exclude、vision 闸门 | **G13**（JUnit4 `fun x()` 返回 Unit）；Q11 逐键白名单断言随本文件同车 |
| `core-data/.../EndpointStore.kt` | `defaultSeeds():107-145` | 三预置端点补高级字段默认值 | 加载只种一次（`:16-18` KDoc）⇒ 老用户走 `@Serializable` 默认值，不靠改 seeds 生效 |
| `core-data/.../EndpointProbe.kt` | `:54-59`、`:65`、`:66-68`、`:115-119` | **Q15（同波收，审查席从本席 T10 挂账升级）**：① 预算 8→**≥64**（与 UI 共用常量）；② `isContent` 谓词改 **`TextDelta \|\| Usage`**（`ReasoningDelta` 单独不算通过）；③ `EndpointProbeTest` 补 2 条 JVM 用例（开思考/关思考两态）。原因：`:65 firstOrNull{}` 第一个事件就取消上游 ⇒ probe 判据本就是「收到任一内容事件」，**只提预算仍留假绿**（纯 `ReasoningDelta` 即可 `:117` 判绿）；`enabled:false` 时正文空 ⇒ 假红。约 20 行 | 在 G18 已登记例外文件内，改动不越界；`core-data` 域与功能波零冲突且**与 ① 重合**（改 `ReasoningStyle` 后「测试连接」必须仍可信） |
| `core-model/.../GenerationEvent.kt` | `:139-148` | **本波零改动**（避免与挂账 W8-03 争文件） | — |
| `core-model/.../Generation.kt` | `:79` KDoc、`:80` 字段 | **Q7'（同波收，1 行、0 新增 @Test）**：`:79` KDoc「仅当 `reasoningStyle==REASONING_EFFORT` 时生效」改成「未接线，勿用；唯一真值源 = `RemoteEndpoint.reasoningEffort`」。审查席判定这是 **C12 注释与实现打架第 7 例**（`SamplingParams.reasoningEffort` 零生产消费点；`OpenAiCompatibleAdapter.kt:182` 消费的是 `endpoint` 那份，测试 `:148/152` 测的是 `RemoteEndpoint.copy`）。**字段本身挂 W9-06 不动** | G17 只扫密钥字段名（`arch-guard.sh:1111`），**判不出语义死字段** ⇒ 不能靠守卫兜底 |

**不触碰**：`ProviderAdapter.kt` 接口（`buildBody(GenerationRequest)` 已带 endpoint，无需扩接口）、
`AppContainer.kt`（DI 零改动）、`OpenAiCompatibleAdapter.decodeEvent()` / `contentElement()`（§3.3）。
⚠️ **§8 文件域订正**：`SettingsRoute.kt` / `SettingsViewModel.kt` / `SettingsScreen.kt` 由**席位 B 独占**
（w8-plan.md §四），本席初稿「零改动」的判断作废；`core-model/JsonFormatCheck.kt` 判归 B，A 不写。

### 3.2 `ReasoningStyle` 是否扩枚举：**是，新增 `OPENROUTER_REASONING_OBJECT`**

- 理由（主理人口径①）：dots `supported_parameters` **无 `reasoning_effort`**，现役
  `REASONING_EFFORT` 分支（`:182`）发的是网关不认的字段；实测 `reasoning_effort:"low"` 不报 400
  但 `reasoning_tokens=33` vs 基础 38、vs `reasoning:{effort:"low"}` 25 ⇒ 疑似被忽略
  （单次观测不能定性，需 ≥2 次重放，W7 §一.7 同款纪律）。
- 新分支线上形态（统一走 `reasoning` 对象，与 OR 官方建议一致）：
  ```
  reasoning = {
    "effort": <resolveEffort 结果>        // 或
    "max_tokens": <N>                     // 与 effort 二选一，不可并存
    "enabled": false                      // reasoningMode=OFF 时，优先于 effort
    "exclude": true                       // hideReasoning=true 时（思考仍计费）
  }
  ```
- `include_reasoning` 兼容发法：OR 文档 `include_reasoning:true ≡ reasoning:{}`、
  `include_reasoning:false ≡ reasoning:{exclude:true}`；**它只隐藏思考文本，不关思考**
  （实测 `_w8_fmt_incl.json` 仍 `reasoning_tokens=42`）。
  ⇒ 裁决：**默认不发 `include_reasoning`**，统一走 `reasoning` 对象；仅当用户打开
  「旧网关兼容」开关时才发，且此时不强发 `reasoning` 对象（避免双发互相覆盖）。
  ⇒ 绝不能用 `include_reasoning:false` 实现「允许关闭思考」——那是两件事（主理人口径②）。
- **单向兼容**：扩枚举后老版本 App 读新 JSON 会静默回落 seeds（见 2.2 陷阱），必须在
  `EndpointStoreTest` 补用例并登记为已知限制。
- **🔴 档位单调性订正（主理人 9 组流式实测，覆盖 §2.4 的「单调递增」表述）**：
  流式 `low 158 / high 441 / xhigh 193` reasoning_tokens 与非流式 `71 / 323 / 688` **排序不一致**
  ⇒ 单次样本**不支持单调性结论**。按 W7「≥2 次重放才写稳定结论」，「五档真实梯度」标
  **⚠️ 部分**；`max` **不再直接判「非法值」**，只登记事实表。
  **设计含义不变**：wire 可识别值仍是 `low/medium/high/xhigh`，UI 第五档必须**显式降级并落 `reason`**，
  禁止静默发未验证值（§2.4 的降级规则、`normalizeEffort()` 硬不变量 `wire != "max"` 全部维持）。

### 3.3 思考内容如何进 `GenerationEvent.ReasoningDelta`（✅ **已证伪的疑似断链点 + 证据**；本波不改 `decodeEvent`）

> **订正说明**：本席初稿据「Phase 0 全是非流式响应」推断流式存在 `delta.reasoning_details` 断链点，
> 并由主理人据此补跑 **9 组 `stream:true` + `stream_options.include_usage=true`** 实测
> （`docs/notes/w8-plan.md §二`、`docs/analysis/w8-provider-probe.md §六`；
> 证据 `_w8/req-*.json` / `resp-*.sse` / `parse-*.json` / `stream_probe.py`）。
> **结论与本席担心相反：断链点不成立**。原推断已在 §12.1 C1 主动撤回，本波**不制造伪工作量**。

**实测事实（逐项可回查）**：

| 观测 | 实测 | 现役代码处置 |
|---|---|---|
| 首帧 `delta` | **同时**含 `reasoning:"The"` 与 `reasoning_details:[{type:"reasoning.text", text:"The"}]`，**逐字等长**（basic 141/141、eff_high 1709/1709）⇒ 同一份思考的**冗余双通道** | `:106-108` = `reasoning_content ?: reasoning` ⇒ **回落命中 `delta.reasoning`**，思考本就能进 `GenerationEvent.ReasoningDelta` |
| `delta.reasoning_content` | **恒 0** | 现役优先取它，取不到回落 `reasoning`，无需改动 |
| `delta.reasoning_details` | 与 `reasoning` 冗余同文 | **不得新增兼容解析**：同帧双发会造成重复 `ReasoningDelta`（`AdapterStreamState.reasoningTracker` 只能吸收累积式重复帧，`ProviderAdapter.kt:83-85`），属伪工作量 |
| usage | 在 `choices:[]` **独立末帧** | `:140-147` 已按顶层 `obj["usage"]` 解析（`:132-141`），已处理 |
| 流尾 | `data: [DONE]` | `isTerminal()` 已判 |
| 流式复证 | 关思考 `rtok=0 / content='PONG'`；`include_reasoning:false` 不回传但 `rtok=32` 照付；tools `finish=tool_calls`/3 碎片/name=ping；视觉 `red, blue`；`json_object` 严格合法 JSON | 与 §7.2 / §6.3 D3–D10 的判据口径一致 |

⇒ **裁决**：本波**不改 `decodeEvent` 的思考 / usage / 终态分支**，不改 `contentElement`
（`w8-plan.md §五.1`）。「流式思考零证据」的前提已消除，§10 P5 改判为
「PC 侧 9 组流式响应已真实行使取值路径；真机 D 判据再验一次端到端」。

**审查席补出的第二个触发面（本波不修，登记 W9-09 证据补充）**：
把 Phase 0 的**非流式**响应喂现役 `decodeEvent` ⇒ `delta == null` ⇒ `out` 空 ⇒
`ProviderAdapter.kt:192` `textChars==0 → FinishReason.STOP` ⇒ **零事件被静默标成正常停止**，
与 `EndpointProbe.kt:30-34` KDoc 的「HTTP 200 HTML 错误页 → 空流=STOP」同型，
是 NEW-E / H9 短路的**第二触发面**。
另需如实标注：响应体**本身没有 `stream` 字段**（非流式由 `object=chat.completion` +
`choices[0].message` 判定）；`stream=false` 是**请求体**字段（审查席对措辞的订正）。

**保留的关联提示**：`reasoningOnly`（仅思考模式）依赖 `RemoteLlmEngine.track():301` 把思考计入
`textChars`，否则 `terminationReason():192` 会把空正文误标 STOP（NEW-E，见 2.5）。

### 3.4 `supportsVision` 联动图片入口开关（外审 NEW-D / 台账 W3-07）

现役状态是**能力声明与实现打架**（审查席判定）：`OpenAiCompatibleAdapter.kt:292-310` 已在发
`image_url` base64 part，`ChatViewModel.attachImage()` 允许挂 4 张图，`AppNavHost.kt:170` 无条件把
`pickImage` 传入 ⇒ 用户能挂图；但 `RemoteLlmEngine.kt:92` 硬编码 `supportsVision=false`，端点无字段。

**三处必须同改，缺一即 ⛔vacuous**（`w8-plan.md §五.2` 定稿）：

1. `RemoteEndpoint.inputModalities` + 派生 `supportsVision`（2.1）；
2. `RemoteLlmEngine.capabilities():92` 不再硬编码；
3. **真正的判据落点**：`AppNavHost.kt:156/170` 图片入口闸门（由**席位 B** 落，A 在回传里给出精确改法由主理人转交）。

🔴 **闸门形态订正**：w8-plan.md 定为 **「保留禁用态入口 + 原因提示，不静默隐藏」**——
本席初稿的「把 `onPickImage` 置 `null` ⇒ `ChatInputBar.kt:72` 不渲染按钮」会造成「点了没反应」
且真机无法取证（uiautomator 对 Compose bounds 不可靠）。真机判据须覆盖**启用前后各一次视觉往返**。
台账 `W3-07` 本波 **CLOSED**。
另：`contentElement()` **本波不改**（已正确发 `image_url`，只补回归用例），不引入 `allowImages` 闸门。

**默认值的用户可见行为变更（必须显式验收）**：
`inputModalities` 默认 `setOf(TEXT)` ⇒ `supportsVision=false` ⇒ **升级后所有老端点的图片入口会消失**。
这是 NEW-D「用户可见性诚实」的有意修复，但属回归面：

| 选项 | 优点 | 缺点 | 适用条件 | **设备硬件约束** |
|---|---|---|---|---|
| **A（推荐）默认 false（fail-closed）+ 高级配置一键开启 + 列表页标记「未启用图片输入」** | 与 `allowPrivateNetwork:134` 同纪律；不再"能挂但声明不支持" | 老用户需手动开一次 | 本波 | 真机必须跑「升级 → 入口消失 → 开启 → 200×120 真图回环」三段判据；`uiautomator` 需按截图重定位（知识库 §2） |
| B 默认 `setOf(TEXT, IMAGE)`（保持现役行为） | 零回归 | 继续对用户说谎，`supportsVision` 变成永远 true 的装饰 | 否 | 视觉判据失去意义，⛔vacuous |

**探测项（1px 图回环）挂 W9**：W7 §二 已禁用 1×1 样本（mistral 在 1×1 上答 Black），
本波统一用 **200×120 左红右蓝真图**（`_w8_vision.png` 本席实测 706 B / 200×120）。
且新增探测若调用 `.generate(` 必须落在 G18 已登记的 `EndpointProbe.kt` 例外内，本波不新增例外
（审查席：本波不新增守卫，避免 VACUOUS 1→2 触发 G21 判红）。

---

## 4. 会话框 Markdown「渲染 / 纯文本」显示选项

### 4.1 方案对比（**不引入新依赖优先**）

| 方案 | 优点 | 缺点 | 适用条件 | **设备硬件约束** |
|---|---|---|---|---|
| **A（推荐）零依赖自绘轻量富文本**：纯解析器放 `core-model`（返回 `MarkdownNode` 列表，JVM 单测），`feature-chat/MarkdownText.kt` 映射 `AnnotatedString`；`GlassSurface.kt` 增一个 `annotatedText: AnnotatedString? = null` 参数 | 不碰 `libs.versions.toml`（版本矩阵锁死）；不触 G4/G5 禁依赖黑名单（`AGENTS.md §四` 禁注解处理器/图片库/JitPack）；纯函数可 JVM 单测；复用现役玻璃气泡与 `SelectionContainer` | 解析器自维护（限制语法子集）；`GlassSurface.kt` 576/600 **只剩 24 行**，改动必须 ≤10 行 | 本波 | Compose BOM 2026.02.00 的 `AnnotatedString`/`SpanStyle` 是稳定 API，SDK 33 无需新权限；气泡 `340.dp` 内长代码行需软换行/`horizontalScroll`；**流式气泡不解析**（120ms 一次重组 + 半截围栏闪烁）；1M 导入后单条 180k 字符气泡必须设解析字符上限防主线程 ANR |
| B 引入第三方 markdown Compose 库 | 功能全 | **违反 G4/G5 + `libs.versions.toml` 锁死 + `AGENTS.md §四`**；可能带注解处理器/抬 minSdk；需改 CI 依赖清单 | **⛔ 排除** | 与 AGP 9 / Kotlin 2.3.0 组合未验，离线沙箱可能拉不到包 |
| C 完全不碰 `core-design`：在 `feature-chat` 复刻一个气泡 | 不动共享模块 | 复制气泡玻璃材质、丢 `SelectionContainer` 长按选择/复制（知识库 §3「自绘替换原生控件 = 默认丢掉原生自带一切」）；两套视觉真值源 | 仅当 A 触 G10 时的退路 | 真机无法用 uiautomator 验证选中；`ChatBubble.kt` 注释里"第二枚复制"的既有纪律被破坏 |

**裁决 = A**。语法子集（写进 KDoc，不支持的原样显示、不静默吞）：
`#/##/###` 标题、`**粗**`、`*斜*`、`` `行内码` ``、```` ``` 围栏代码块 ````、`- ` 列表、`> ` 引用、
`[文字](url)` 只显示文字（不引入 URL 点击）。**不支持表格 / 图片 / HTML**（不引图片库）。

### 4.2 设置项落在哪、状态放哪

- **状态**：`SettingsRepository` 新键 `chat_markdown_render: Boolean`（`booleanPreferencesKey`，
  默认 **false = 纯文本**，保持现役外观不回归），复用现役 `safeData()`（`:123-126` 已正确处理
  `CancellationException`）。**不进 `ChatViewModel.kt`**（487/500 只剩 13 行，F19）。
- **读取**：`AppNavHost.ChatRoute` 用 `collectAsStateWithLifecycle` 读 `container.settings.renderMarkdown`，
  作为普通参数下传 `ChatScreen → ChatMessageList → ChatBubbleRow → ModelBubble`。
  ⇒ `SettingsRoute.kt` / `SettingsViewModel.kt` / `SettingsScreen.kt` / `ChatViewModel.kt` **零改动**
  （共享禁区不被触碰，§8）。
- **切换入口**：`ChatTopBar` 溢出菜单加一项（F24：现条件 `onCompressContext != null` 要改成
  `!= null || onToggleRenderMarkdown != null`）。会话框内即可切换，符合用户「会话框…选项」的措辞。
- **渲染落点**：`ChatBubble.ModelBubble()` 在 `renderMarkdown=true && !bubble.streaming` 时
  构造 `AnnotatedString` 传给 `GlassBubble(annotatedText=…)`；流式气泡恒传 `null`。
  `SelectionContainer` 对 `AnnotatedString` 仍生效（F10）。
- **🔴 `GlassSegmented` 不可用**：`core-design/.../GlassSegmented.kt:33` 用了
  `clearAndSetSemantics` ⇒ uiautomator dump 读不到分段项（F21）。五档强度选择器**改用
  `GlassSettingRow` 列表**（现役 `SettingsScreen.kt:290-311` `ContextWindowRows` 的同款形态），
  否则真机档位切换无法取证。

### 4.3 单测怎么钉「切换真的改变渲染」

本仓**无 Compose UI 测试依赖**（F22），所以不能靠 `@Composable` 单测。三层判据：

1. **纯函数层（JVM）**：`MarkdownParser.parse(text): List<MarkdownNode>` +
   `resolveRenderMode(renderMarkdown, isStreaming, text): RenderMode{PLAIN, RENDERED}`。
   用例必须含**已知必通过样本先跑**（铁律⑦）：`"**bold**"` ⇒ 1 个 BOLD 节点；
   `"plain"` ⇒ 0 节点；`isStreaming=true` ⇒ 恒 PLAIN；`renderMarkdown=false` ⇒ 恒 PLAIN。
   两态表（on/off × streaming/finished）共 4 行逐行断言。
2. **「被调用」层（⚠️ 不能靠新增守卫）**：审查席裁定本波不新增守卫（G21 棘轮）。
   ⇒ 改用**真机 A/B**：同一条含 `**bold**` + `# H` + ``` 围栏的 **ASCII 题面**（中文无法注入，
   知识库 §2），切换前后各截一张图（`adb exec-out screencap -p`），比对像素；同时在切换回调里
   落一行 `markdown_render=on|off` 到 `files/harness/logs/agent-0.log`（`core-data` 日志，
   G19 只扫 `core-engine`，不抬 VACUOUS）。**日志 + 截图双证据**，避免"判据写进方案 ≠ 判据被执行"。
3. **空命中语义**：只有纯函数单测、没有真机 A/B ⇒ **⚠️ 部分**，不许写 ✅。

---

## 5. 模型返回 JSON 格式检查

### 5.1 「检查」的语义裁决

主理人口径⑥：可分「校验返回文本」与「强制 JSON 模式」两路。

| 路径 | 裁决 | 理由 |
|---|---|---|
| **A（本波主交付）校验模型返回文本是否合法 JSON**，允许 ```` ```json ```` 围栏 | **做** | 用户 ③ 的字面语义；不改请求体 ⇒ 不动 `RunnerKey`/前缀缓存（F14）；`core-model` 已有 `kotlinx-serialization-json`（F11），零新依赖 |
| **B 强制 JSON 模式**：`response_format:{"type":"json_object"}` / `structured_outputs` | **🔴 订正：同波做**（`w8-plan.md §五.1/§五.6` 定稿，覆盖本席初稿的「挂 W9」） | 端点 `jsonMode` 驱动 `buildBody` 发 `response_format`（**席位 A** 落请求体），与 A 路的「气泡动作检查」**两路分开、各有判据**；`jsonMode=false` 不发该键（V4）；`north` 的 `supported_parameters` **不含 `response_format`** ⇒ 显式降级 ⛔ 不适用。流式 9 组实测 `json_object` 返回严格合法 JSON（`w8-provider-probe.md §六`），证据链已闭合。本席初稿「会改前缀故挂 W9」的顾虑由主理人裁决覆盖；矩阵 M-10 / §9.2 对应行同步作废 |

**A 的精确语义**（写进 `core-model/.../JsonCheck.kt`）：
- 严格校验：`Json { isLenient = false; allowTrailingComma = false; ignoreUnknownKeys = false }`
  **新建一个实例**，🔴 **绝不能复用 `AgentJson.json`**（F11 是 `isLenient=true /
  coerceInputValues=true`，会把非标准 JSON 判成通过 ⇒ 判据自身失效，铁律⑦）。
- 合法根：任意 JSON 元素（object / array / string / number / bool / null）都算合法，但 UI 显示根类型；
- 围栏：允许剥**一层**成对的 ```` ``` ```` / ```` ```json ````（大小写不敏感、允许语言标注）；
  **不成对 ⇒ `unclosed_fence` 失败，不静默剥**；
- 空白串 / 纯空白（注意 `_w8_fmt_tools512.json` 的 content 是 `"\n\n\n"`）⇒ `EMPTY` 失败；
- 结果 `JsonCheckResult{ OK(kind, rootType, fenced) | FAIL(reason, line, column) }`，
  **只带行列号，不带原文**（`AGENTS.md §六`：模型输出是五处泄漏面之一，不许把整段回显进日志/错误串）。

### 5.2 落点、错误提示

| 候选落点 | 裁决 | 理由 |
|---|---|---|
| **气泡动作**（`ChatBubble.ModelBubble.actions` 加第二枚「检查 JSON」键） | **✔ 采用** | 与现役「一枚复制」并列，**有真实不同语义**（不是旧仓那枚装饰键，F9）；按需触发，不污染每条气泡；复用 48dp 热区（`ChatBubble.kt:141-142`） |
| 设置项（每条气泡自动检查） | ✘ | 会为每条定稿气泡跑解析，1M 导入后主线程压力；且失败提示会刷屏 |
| 工具（模型可调用） | ✘ | 会改工具注册表 / `ApprovalChokepointAuditTest` 穷举表（C5 M2 余件挂账），放大本波文件域 |

- **错误提示**：气泡下方一行小字（复用 `BubbleCopyAction` 的轻量样式），
  文案全部走 `stringResource`（**G16 中文硬编码基线 0**，`CJK_SCAN_PATHS` 含 `feature-chat`），
  错误码用英文常量，格式如 `invalid_json at 1:7 · unclosed_fence`。
- **G17/G8**：`JsonCheck.kt` 在 `core-model`，只用 `kotlinx.serialization.json` + stdlib，合规。

### 5.3 单测判据（判据自身先验证，铁律⑦）

| # | 样本 | 期望 | 空命中语义 |
|---|---|---|---|
| J1 | `{"a":1}` | OK(OBJECT, fenced=false) | **先跑这条已知必通过样本**，若判据不绿则后续全作废 |
| J2 | `{"a":` | FAIL(line/col) | **再跑已知必失败样本**；若也 OK ⇒ 判据自身失效（例如误用 `AgentJson.json`） |
| J3 | ```` ```json\n{"a":1}\n``` ```` | OK(OBJECT, fenced=true) | 围栏只剥一层 |
| J4 | ```` ```json\n{"a":1} ```` | FAIL(unclosed_fence) | 不静默剥 |
| J5 | `null` / `[]` / `42` / `"s"` | OK(对应根类型) | — |
| J6 | `""` / `"\n\n\n"` | FAIL(EMPTY) | 覆盖 `_w8_fmt_tools512.json` 的空白 content |
| J7 | `{'a':1}` / `{a:1}` / `{"a":1,}` | FAIL | 钉住「没有误用 lenient Json」 |
| J8 | UTF-8 BOM 前缀 `\uFEFF{"a":1}` | 明确裁决（本波建议 FAIL + 提示 BOM，与 Q2 同源） | 不静默接受 |

**「被调用」判据**：真机点击「检查 JSON」→ 气泡下出现结果行 + 日志行；
只跑 J1–J8 而没有真机点击 ⇒ ⚠️ 部分。

---

## 6. 设备硬件可行性维度（方案席专属）

### 6.1 目标设备 / OEM / 能力位

| 项 | 事实 | 出处 / 判据 |
|---|---|---|
| 唯一真机 | OPPO PDRM00 / Android 13 / **SDK 33** / 1080×2400；包名 `com.rickeal.liquidharness.debug` | `AGENTS.md:51`；`docs/00-brief.md:284`；`handoff-20261101-wave4.md:114` |
| minSdk | 31（`gradle/libs.versions.toml`）⇒ SDK 33 设备可装；本波新增能力**不引入 SDK 34+ API** | 版本目录锁定 |
| adb | `_j2env/sdk/platform-tools/adb.exe`，**必须 `-P 15037`**；`MSYS_NO_PATHCONV=1` + Windows 形态 `D:/…`；读二进制用 `adb exec-out` | `handoff-20261101-wave6.md:162`；`AGENTS.md:52`；知识库 §2 |
| 安装前置 | 灭屏令 ColorOS 冻结安装服务 ⇒ 安装前 `KEYCODE_WAKEUP` + `screencap` 确认非全黑；三步 `pm install-create/write/commit` | `AGENTS.md:54-78` |
| `adb reverse` | 映射随 daemon 回收失效，**必须与后续命令在同一条 Bash 调用内重建**，且就在「点发送」前一刻 | `handoff-20261031-wave25.md:103`；知识库 §2 |
| 视觉能力位 | 200×120 真图可用（`_w8_vision.png` 本席实测 706 B / 200×120，左红右蓝） | `_w8_vision_body.json` / `_w8_vision_resp.json` "red, blue" |
| **音频输出 ⛔ 不适用** | NV `/v1/models` 85 模型**零音频类**（本席解析 `_w8_nv_models.json`，`audio/speech/tts/voice/whisper/sound` 命中 0）；OR 两免费模型 `supported_voices=None`、`output_modalities=["text"]`；W7 `/v1/audio/speech` 404；设备仅 `com.iflytek.speechsuite` TTS 且 `speak` 默认不注册 | `handoff-20261104-wave7.md:96`；`docs/06-deferred-ledger.md` W3-01；`AppContainer.kt:170-178`；`_w8_nv_models.json`；`_w8_or_models.json` |
| MCP 能力位 | `android:usesCleartextTraffic="false"` ⇒ 明文 http 被平台拒（W5-03 平台阻塞，⛔ 不适用）；本波**不为测试放开** | `app/src/main/AndroidManifest.xml:25`；台账 W5-03 |
| 1M 能力位 | 见 §6.4，**当前设备侧不可达** | F15/F16/F18 |

### 6.2 adb 取证判据（点按位置 / uiautomator 不可靠 / 以 journal 与日志为准）

1. **不信任 Compose bounds**：知识库 §2 明训「uiautomator dump 对 Compose bounds 不可靠」；
   `GlassSegmented.kt:33` `clearAndSetSemantics` 会**主动清掉语义** ⇒ 分段控件在 dump 里既无文字
   也无可靠 bounds（F21，本席新增）。⇒ 五档/开关控件一律改用 `GlassSettingRow` 列表，
   且点按判据以**应用日志 + journal**为准，tap 副作用必须 `pb`/`endpoints.json` 实读验证。
2. **每步落判据**：多步 UI 自动化不可靠；键盘弹出后发送键上移
   （W6 实测：弹起约 `(942,1215)`、收起约 `(930,1969)`），**每次按截图重定位坐标**；
   授权对话框遮挡输入框；`am force-stop com.heytap.market` 防应用商店抢前台吞 tap；
   对话框标题与按钮同名 ⇒ 取「最后一个匹配节点」（W7 交接 §四）。
3. **中文注入不可行**：`input text` 非 ASCII 设备侧 NPE ⇒ **全部题面用 ASCII**，空格写 `%s`；
   单次注入 ~3800 字符截断、uiautomator 的 text 属性 ~4000 字符封顶 ⇒ 长文本追加写，
   1M 题面**不可能靠 `input text` 注入**（⇒ 必须走 §6.4 的 SAF 导入通道）。
4. **落盘取证**：PC 侧 logcat 环形缓冲仅 ~256 KiB、沙箱 adb server 每命令后退出 ⇒
   设备侧 `adb shell "nohup logcat -b all -f /data/local/tmp/w8.log &"` 事后 pull；
   W7 先例证据路径 `files/harness/logs/agent-0.log`。
   `AgentLogStore.dropOneFor()` 先驱逐 INFO（`:89-105`）⇒ 本波新增的证据行要控制数量，
   避免被自身刷掉；判崩溃先核 PID（知识库 §2）。
5. **保屏**：`svc power stayon` 被沙箱拦 ⇒ 周期性 `input keyevent 224`（知识库 §2）。
6. **journal 目录按会话键控**：「新目录」判据只适用首轮，后续轮看新 `run_*.jsonl`（知识库 §2）。
7. **G19 隔离**：新增诊断日志放 `core-data` / `feature-*`，**不要放 `core-engine`**
   （G19 现 VACUOUS=1；在 core-engine 加日志会让其首次开火，且 `SecretRedactor` 出口未接满）。

### 6.3 真机验收清单（每条可跑真假，标四态）

> 触发条件 = 可判定谓词；空命中语义 = 判据没被行使时的状态。安装/唤醒/reverse 前置见 §6.1–6.2。

| # | 判据 | 可判定触发条件 | 证据 | 空命中语义 | 预期四态 |
|---|---|---|---|---|---|
| D1 | 老端点升级后高级字段可读 | 读回 `endpoints.json`，缺键 ⇒ 全默认值且自定义端点未丢 | `adb exec-out` + JVM round-trip | 未读盘只测内存 ⇒ ⛔vacuous | ✅ |
| D2 | 高级配置保存→落盘→请求体消费 | 改 `supportsVision/reasoningMode/effort` ⇒ `buildBody` 产物逐键变化 | `endpoints.json` + 日志 | UI 画了但 draft 没带字段 ⇒ ⛔vacuous | ✅ |
| D3 | 关闭思考 | `reasoning:{enabled:false}` ⇒ content 精确 `PONG`、rtok=0（**流式对照**） | 流式 SSE 逐帧 | 只有非流式 ⇒ ⚠️ | ✅／⚠️ |
| D4 | 仅隐藏思考 | `include_reasoning:false` ⇒ 思考文本不回传但 rtok>0 | 响应 usage | 未测 rtok 只看文本 ⇒ ⚠️ | ✅／⚠️ |
| D5 | 五档强度 | 四档请求体 `effort` 逐档正确；「极致」显式降级且 `degraded=true` 上屏 | 请求体归档 + 截图 | 只测 UI 选中态 ⇒ ⛔vacuous | ✅／⚠️ |
| D6 | 图片入口闸门 | `supportsVision=false` ⇒ attach 按钮不渲染；`true` ⇒ 渲染 | 截图 + dump（辅以日志） | 只改 capabilities ⇒ ⛔vacuous | ✅ |
| D7 | 视觉真图回环 | 200×120 左红右蓝图 + `reasoning.enabled=false` + `max_tokens≥512` ⇒ 命中双色 | `_w8_vision_body/resp` 同款 | 1×1 样本 ⇒ ⛔（W7 §二 禁用） | ✅／⚠️（见 §7.1 双要素） |
| D8 | Markdown 渲染 / 纯文本 A/B | 同 ASCII 题面切换 ⇒ 截图差异 + 日志 `markdown_render=on|off` | `screencap` 两张 + 日志 | 只有纯函数单测 ⇒ ⚠️ | ✅／⚠️ |
| D9 | JSON 检查三态 | 合法 / 围栏 / 非法 各点一次 ⇒ 三种结果行 | 截图 + 日志 | 只有 JVM 单测 ⇒ ⚠️ | ✅／⚠️ |
| D10 | tools@512 | `max_tokens≥512` ⇒ `finish=tool_calls`、tool_calls=1 | 响应 | `max_tokens=64` ⇒ **⛔vacuous 不是失败** | ✅ |
| D11 | 测试连接不受思考吃预算影响 | 开启思考时点「测试连接」仍返回 ok | `ProbeOutcome` | 未测 ⇒ ⚠️（F13 风险） | ✅／⚠️ |
| D12 | 1M 设备侧导入 | SAF 选择器选 `import-1m.jsonl` ⇒ 气泡数 30、状态条 1M | journal 新 `run_*.jsonl` | SAF 未行使 ⇒ **⛔vacuous**（W7 A7） | ⛔vacuous |
| D13 | 音频输出 | `/v1/audio/speech` 或 TTS 工具 | — | 无音频模型 / speak 未注册 ⇒ **⛔ 不适用** | ⛔ |
| D14 | MCP 真机联调 | https MCP server 工具调用 | — | cleartext 被平台拒且未裁决 ⇒ **⛔ 不适用** | ⛔ |
| D15 | 四闸门 + push + CI | `assemble/fulltest/selftest/arch-guard` 全绿 → push main → `gh run list --branch main` 有本次 run | 四份日志 | 0 run ⇒ **⛔vacuous**（F25） | ✅ |
| D16 | 纯文本/渲染切换的旋转保持 | 旋转屏幕后选项仍在 | 截图 + DataStore 读回 | `Set<enum>` 无 Saver ⇒ ⛔ | ✅／⚠️ |

### 6.4 1M 上下文在设备侧的可行路径（W7 A7 ⛔vacuous 未行使）

**通道**：`SessionImportRow`（`feature-settings`，debug 限定，`AppNavHost.kt:106` `importEnabled`）→
SAF `OpenDocument` → `SessionImportParser` → `AppNavHost.pendingImport` → `ChatViewModel.importConversation()`
→ `ChatUiState.bubbles`（`handoff-20261104-wave7.md:84` A7 标 ⛔vacuous：SAF 驱动未行使）。
夹具 `_w6/import-1m.jsonl` 已在设备 Download（**5 221 133 B / 30 行 / 无 BOM / 最长行 180 067 字符**
——本席初稿写 180 068 是含换行口径差 1，非缺陷，依审查席 `w8-review-triage.md §八.5` 订正）。

**🔴 因果重写（覆盖本席初稿 B4）**：审查席回树核实（`w8-review-triage.md §八.1`）证实
`AgentPolicy.kt:228=400_000`、`RoundHeadGate.kt:42-46`、`AppContainer.kt:265-281` 未覆盖该值全部命中，
但「**请求根本发不出去**」**不成立**，且漏了更早的一道闸。设备侧 1M 是 **两条独立阻断 + 一条叠加**：

| 阻断 | 触发顺序 / 机制 | 出处 |
|---|---|---|
| **(a) 档位相关、发请求前**（**第一因**） | `TurnLoop.kt:58-78` 顺序为 `preStep.enforce(:60)` → `gate.check(:62)` → `beginRound(:75)` → `generation.run(:78)`。`PreStepBudget.enforce` 用 `hardContextBudgetTokens` 里的 `policy.maxContextTokens = windowCap`；W7 真机 `settings.preferences_pb` 实测 `max_context_tokens = 32 000`（分母 27.9K）⇒ `hard≈27 904` ⇒ 995k 上下文在**发请求前**被 `PreStepBudget.kt:86/111/123/159-164` 收缩或 `trip`（`context_overflow_prevented` + `emitBreaker`） | `docs/notes/w7-provider-matrix.md:293`；`core-agent/loop/TurnLoop.kt:60`；`PreStepBudget.kt:159-164` |
| **(b) 第 2 轮起熔断**（**非首轮硬闸**） | `RoundHeadGate.check` 判的是**本 run 累计 `promptTokens`**（`RunState.kt:77,237 += prompt`），**首轮 = 0 ⇒ 首轮会真实发出**；usage 回报累加后**第 2 轮**轮头才 `Trip`。`AgentPolicy.kt:228` 的 400 000 不是首轮闸门 | `RoundHeadGate.kt:42-46`；`RunState.kt:77,237` |
| **(c) 叠加** | `compressedUpTo` 单指针压缩（H3/W9-03），且 `compressContext` 默认 true | `ChatUiState.kt:208`；台账 W8-01 |

`_w7/w7matrix.py:3-14` 明写纯 Python 直连 HTTP、不跑 gradle ⇒ **PC 侧绕过 app 内核成立**，
故 W7 的 995k 双通过不构成设备侧证据。主理人裁决 ② 接受「设备侧 1M ⛔ 不适用」，
但理由须写成上面两条而非只归 `maxRunPromptTokens`；新增挂账 **W9-11 设备侧 1M 内核熔断解锁**。

**逐条阻塞（按触发顺序）**：

| # | 阻塞 | 出处 | 处置 |
|---|---|---|---|
| B1 | `SessionImportParser` 三道上限：**都过得去**（8MiB / 5000 条 / 4MiB 单条；5.22MB、30 条、180k 字符） | F17/F18 | 无需改，但 Q2 BOM 修复仍需做（Windows PS5.1 `Out-File` 默认 BOM，`trim()` 不剥 `\uFEFF`） |
| B2 | `input text` 单次 ~3800 字符截断 / uiautomator text ~4000 封顶 ⇒ **1M 题面无法键入** | `handoff-20261104-wave7.md:74` | 必须走 SAF 导入，驱动脚本照 `_w7/accept_a*.py` |
| B3 | OR 网关「字符数 ÷ 4」预检：dots 512k、north 256k ⇒ 995k 干草堆 HTTP 400 | `handoff-20261104-wave7.md:94`；`_w7/matrix-dots-ladder.json` | 1M 档只能用 **NV `nv-vl`**（W7 已实测 995k ✅），不能用 OR 免费池 |
| B4 | 🔴 **两条独立阻断**：(a) 档位仍 32K ⇒ `PreStepBudget` 轮前收缩/trip（第一因）；(b) 切到 1M 且首轮真发 995k ⇒ **第 2 轮起** `RoundHeadGate` 400k 熔断 | 上表；`w8-review-triage.md §八.1` | 显式裁决「设备侧 1M ⛔ 不适用」+ 挂 **W9-11**；1M 结论严格限定「PC 侧单次请求窗口」，矩阵报告不得写「设备侧 1M 可用」 |
| B5 | 🔴 `SwitchGuard` floor 1.8 误拦：5.2M 字符会话切 1M 端点被 1.88M cap 拦（复审41 C3 面3）；叠加 `compressedUpTo` 单指针 | `core-context/.../SwitchGuard.kt:39,81-108`；`ChatUiState.kt:208` | 与 C3 4.0 尺统一同挂 W9；本波不改 |
| B6 | `compressContext` 默认 true，1M 窗口软预算 ≈746k 就触发压缩 ⇒ 测到的不是 1M 请求 | `SettingsRepository.kt:70-90` KDoc 自述 | debug 开关置 **false**（W7-06 ② 提醒此路径易假绿：必须先 grep 到"压缩确实没发生"的独有证据） |
| B7 | `generationTimeoutMillis=120s` 首字节看门狗；W7 995k NV 110.57s、mistral 238.69s ⇒ 后者必触 SOFT(180s) 甚至 TIMEOUT | `core-kernel/.../AgentPolicy.kt`；`handoff-20261104-wave7.md:92` | 1M 档取 NV 单模型，且判据同时登记 `max_tokens/prompt/completion/finish_reason` |
| B8 | `max_tokens` 必须 ≥512（W7 通道 512 / 阶梯 4096），否则 reasoning 吃光预算 ⇒ `finish=length`、content 空 | `docs/notes/w7-provider-matrix.md:55`；主理人口径④ | 每条判据前置条件 |
| B9 | 🔴 设备内存/主线程：30 条 × 180k 字符进 `ChatUiState.bubbles`，`toHistoryMessages()`/`committedCharsOf` 每次 state 变更重算，`viewModelScope` 默认 Main ⇒ ANR 风险 | `ChatViewModel.kt:280`；`SessionFootprint.kt`；`AGENTS.md §四` 旧事故 | 真机判据必须含「导入后 UI 未 ANR、状态条可见」；不为本波重构内核 |
| B10 | 纯 release 包看不到导入入口（只有 debug 可装）⇒ W7-06 ① 判据在本仓不可执行 | `AppNavHost.kt:69,106`；台账 W7-06 | 如实登记 ⛔ 不适用，不写成"已验" |

**结论**：W8 设备侧 1M 仍判 **⛔vacuous**（B2 注入不可行 / B4 两条内核阻断 / B5 切换守卫+单指针 /
B10 release 无入口），主理人裁决 ② 已接受并新增 **W9-11 设备侧 1M 内核熔断解锁**。
handoff 必须写明**两条阻断的因果**（不可只归 `maxRunPromptTokens`），否则下一波会误以为
「只要驱动了 SAF 或只调一个上限就能跑 1M」。⑥ 矩阵的 1M 结论严格限定为
**「PC 侧单次请求窗口」**，不得写成「设备侧 1M 可用」。

---

## 7. 多模型矩阵测试计划

### 7.1 主矩阵（覆盖用户 ⑥ 全部点名项）

> 通用前置 **M0**：每判据必须同时登记
> `max_tokens / prompt_tokens / completion_tokens / reasoning_tokens / finish_reason / cached_tokens / cache_write_tokens`
> （W7 §五.1、复审 P2-3）；`max_tokens<512` 的工具/档位样本一律判 **⛔vacuous**（不是失败）。
> **M1**：OR 免费池前缀缓存 4 连发实测全 0（`_w8_cache_resp1-4.json`：ptok=1853、cached=0、write=0）
> ⇒ 缓存结论**按端点分组**，不得用 OR 免费池否定 NV 直连（W7 A5 79.4%）。
> **M2**：`RunnerKey` 含整个 `RemoteEndpoint`（F14）⇒ 改高级配置与压缩**都会**令 `cached_tokens` 归零，
> 矩阵必须区分 `config_changed` 与 `cache_invalidated` 两种来源，否则归因错误。
> **M3**：1M 档 ≥2 次重放才写稳定结论（W7 §五.1：同参数 W6 曾 vacuous）。
> **🔴 M4（主理人裁决要求写入）：「App 恒流式」是全部请求格式判据的前置条件。**
> `OpenAiCompatibleAdapter.kt:76` 恒 `stream=true`、`:79` 恒带 `stream_options.include_usage=true`；
> 故任何 PC 侧 `stream=false`（`object=chat.completion` + `choices[0].message`）结论迁移到 App 路径时，
> 必须先跑流式复证并标清「非流式 ✅ / 流式 ✅」两栏（`w8-provider-probe.md §六` 已按此回写）。
> 本波 9 组流式对照已完成（§3.3），故此前置条件已满足；但**不写它就会把非流式结论冒充端到端证据**，
> 属于铁律⑦「判据自身要先被验证」。响应体本身**没有 `stream` 字段**，非流式由 `object` + `message` 判定。

| # | 测什么 | 用哪个模型 | 判据（触发条件 → 空命中语义） | 预期四态 |
|---|---|---|---|---|
| M-1 | 上下文分档直至 1M | NV `nvidia/nemotron-3.5-super-vl-120b-a12b`（1M，`max_tokens=4096`）；NV `mistralai/mistral-large-4-1t-a52b`（七档全通）；OR `dots`（512k）、`north`（256k） | 触发：1k/8k/32k/128k/256k/512k/995k 取针 `MAGENTA-7731` 命中且 `finish=stop`；OR 两档因字符÷4 预检 400。空命中：`finish=length` + content 空 ⇒ ⛔vacuous | NV ✅／OR ⛔（网关预检） |
| M-2 | 多模态输入 | `dots` + NV `nv-vl`（`text+image->text`）；`north`（`text->text`） | 触发：**200×120 真图 + 形状×颜色双要素断言**（W7 §二 禁用 1×1）。现 `_w8_vision_resp.json` 只答 `red, blue`（题面只问颜色）⇒ 未覆盖形状 ⇒ **⚠️ 部分**，本波补形状复放。空命中：无图 part ⇒ ⛔vacuous | ⚠️→✅／north ⛔ 不适用 |
| M-3 | 音频输出 | NV 85 模型 + OR 两免费模型 | 触发：`/v1/audio/speech` 或 `output_modalities` 含 audio。实测 0 命中 ⇒ **⛔ 不适用**（§6.1 已列三重证据） | ⛔ |
| M-4 | 工作空间文件读写 | `dots` / `nv-vl`（tools 通道，`max_tokens≥512`） | 触发：`file_read` 5×16KB 并行读 ⇒ `prompt_tokens≈27.2k ≤ 27 904`、`cached_tokens>0`（W7 A5 21 680 / 79.4%）；`file_write` ⇒ 审批卡 → `files/harness/workspace/notes.txt` 实读 | ✅／⚠️（OR 免费池缓存恒 0） |
| M-5 | 记忆 | 同上 + `FileMemoryStore` | 触发：`memory_write` ⇒ `memory.json` 落盘实读 + 下一轮 system 段出现；`memory_read` 回灌 | ✅／⚠️（C5 M2 余件：`MemoryTools.kt:35/80/119` 的 `requiresConfirmation` 三处未补，安全面未钉） |
| M-6 | Skill | 同上 + `SkillCatalogImpl` | 触发：`skill_load` ⇒ `AgentEvent.SkillLoaded` + 二级正文进 system 后缀。**导入链路 `SettingsRoute.kt:225 onImport=null` ⇒ ⛔vacuous**（台账 W3-02 OPEN） | ✅（内置）／⛔vacuous（导入） |
| M-7 | MCP | 任意 | 触发：https server `tools/list` → 调用。现 `usesCleartextTraffic="false"` 平台阻塞（W5-03）⇒ **⛔ 不适用**；不为测试放开 cleartext | ⛔ |
| M-8 | KV cache 分块命中 | OR 免费池（dots/north）vs NV 直连 | 触发：同前缀 ≥2 次写入后 `cached_tokens>0`，且改首字节后归零（**双向**）。W7 免费池 `0→0→4336→0`、分块 `4336→0→0`；本波 `_w8_cache_resp1-4` 全 0 ⇒ **不承诺命中率**。三桶 `cache_write_tokens` 现役未解析（W8-02 挂账） | ⚠️ 部分（OR）／✅（NV 真实长会话，需 ≥2 重放） |
| M-9 | 手动 / 自动上下文压缩 | `nv-vl`（32K 档） | 触发：`auto_compact dropped=N kept=M cache_invalidated=true` 落账 + 下一请求 `cached_tokens=0` 双向。W7 A4 真机全是 `noop reason=below_trigger`、A6 手动「无损放弃」⇒ **applied=true 零次**。W8-01 尾部摘要挂账 | ⚠️ 部分／⛔vacuous（applied 未行使） |
| M-10 | `response_format` 严格 JSON | `dots`（`supported_parameters` 含 `response_format`/`structured_outputs`）；`north` **不含** | 触发：`response_format:{"type":"json_object"}` ⇒ `json.loads` 通过（非流式 `_w8_eff_jsonfmt.json` + **流式 9 组复证**，请求体已按 W7 先例归档）⇒ 证据链已闭合；`north` 未声明支持 ⇒ ⛔ 不适用。**实现为同波**（端点 `jsonMode` 驱动，§5.1 订正） | ✅（dots，同波）／⛔（north） |
| M-11 | `deepseek-v4.1-flash` | NV | W7 两次复验 300s / 600s 均 TimeoutError ⇒ **⛔ 本环境不可用**，不重复调研 | ⛔ |

### 7.2 `dots-3-note-preview:free` 请求格式 — 实测清单（**T1 已完成，结论相反**）

> **编号映射（防派错活）**：主理人 / 审查席口径中的「**§7.3 T1**」= 本文 **§7.2 的 T0/T1 流式对照**；
> 本文 §7.2 是矩阵测试的唯一待补清单，无 §7.3 节。以 `docs/notes/w8-plan.md §二` 为准。
>
> **🔴 T1 状态：✅ 已完成，结论与初稿担心相反。** 主理人补跑 9 组 `stream:true` +
> `stream_options.include_usage=true`（基础 / `reasoning.enabled=false` / `include_reasoning:false` /
> `low·high·xhigh` 三档 / tools@512 / image / `json_object`），逐项统计 `delta.reasoning` /
> `delta.reasoning_content` / `delta.reasoning_details[].text` / `delta.tool_calls` 碎片与 `finish_reason`；
> 请求体 + SSE 原文照 W7 先例归档 `_w8/req-*.json` / `_w8/resp-*.sse` / `_w8/parse-*.json`
> / `_w8/stream_probe.py`，结论回写 `w8-provider-probe.md §六`「非流式 ✅ / 流式 ✅」两栏。
> ⇒ 现役 `delta.reasoning → ReasoningDelta` 链路本就通（§3.3），**本波不改 `decodeEvent`**。
> 本席初稿「流式断链」判断已在 §12.1 C1 主动撤回。
>
> **证据链口径**：响应体**没有 `stream` 字段**，非流式由 `object=chat.completion` + `choices[0].message`
> 判定；`stream=false` 出现在**请求体**。Phase 0 的 21 个带 `object` 的响应中 20 个 `chat.completion`
> （另 1 个 `list` = models），4 份 `_w8_cache_req*.json` 请求体为 `stream=False`（审查席复核）。

| # | 项 | 主理人已测（非流式 / 流式） | 还需补 | 前提（**`max_tokens` 必须给足**） |
|---|---|---|---|---|
| **T1** | **流式全组对照**（原 T0） | ✅ **已完成，结论相反**：`reasoning` 与 `reasoning_details` 逐字等长、`reasoning_content` 恒 0、回落命中 `delta.reasoning`；usage 独立末帧、`[DONE]` 已判 | 真机端到端 D3–D10 再验一次；**不改代码** | `max_tokens≥512`；思考档 ≥1500 |
| T2 | `reasoning:{"enabled":false}` 关思考 | ✅ 非流式 `_w8_fmt_off.json`（rtok=0、content=`PONG`）；✅ 流式复证同值 | 请求体归档（T1 已补） | ≥512 |
| T3 | `include_reasoning:false` 仅隐藏 | ✅ 非流式 `_w8_fmt_incl.json`（rtok=42 照付）；✅ 流式 `rtok=32`、不回传 | 与统一 `reasoning:{exclude:true}` 做 A/B | ≥512 |
| T4 | 五档 effort 接受度 / 梯度 | low/medium/high/xhigh/max 非流式均 200；**流式 `low 158 / high 441 / xhigh 193` 与非流式 `71 / 323 / 688` 排序不一致 ⇒ 单调性 ⚠️ 部分**；`max` **只登记事实表，不判非法值** | ① 每档 **≥2 次重放**同题面；② `xhigh` 给 `max_tokens≥1500`；③ 补 `reasoning:{exclude:true, effort:…}` 组合。**设计含义不变**：可识别 `low/medium/high/xhigh`，第五档显式降级 | ≥1500（档位）／≥512（其他） |
| T5 | 足量 `max_tokens` 的 tools 调用 | ✅ 512 时 `finish=tool_calls`；⛔ 64 时 `finish=length`、content=None；✅ 流式 `finish=tool_calls`/3 碎片/name=ping | 判据读 `tool_calls` 而非 content | **≥512**；`<512` 判 ⛔vacuous |
| T6 | image 输入 | ✅ 200×120 真图、`content:[text,image_url]`、答 `red, blue`；✅ 流式同值 | ① `supportsVision` 闸门联动；② 形状×颜色双要素 | ≥512 |
| T7 | `reasoning_effort` 顶层 legacy | ⚠️ `_w8_dots_reffort_legacy.json` 不报错、rtok=33 | 与基础（38）/ `reasoning.effort=low`（25）**同题面 ≥2 次重放**才能定性「被忽略」 | ≥512 |
| T8 | `response_format` | ✅ 非流式严格合法 JSON；✅ 流式同值 | `north` 对照（不支持）；请求体归档（T1 已补） | ≥512 |
| T9 | 未测形态 | — | `reasoning:{max_tokens:N}`（预算，min 1024）、`effort:"none"`/`"minimal"`、`structured_outputs`、三桶 `cache_write_tokens` 非零样本 | ≥512；`effort` 与 `max_tokens` 二选一 |
| T10 | 缓存四连发 | ✅ `_w8_cache_resp1-4.json` 全 `cached=0/write=0` | 与 NV 直连对照，按端点分组 | ≥512 |
| **Q15** | **`EndpointProbe` 判据自身**（原 T10 挂账 → **同波收**） | 审查席核实：`:57 maxTokens=8`、`:66-68 isContent` 含 `ReasoningDelta`、`:117` 判绿、`:65 firstOrNull` 首事件即取消 | 预算 8→≥64 + 谓词改 `TextDelta‖Usage` + 2 条 JVM 用例；**只提预算仍假绿** | 与功能域重合（改 `ReasoningStyle` 后「测试连接」须可信），约 20 行 |

**判据前提必须写进每条**：W7 §二 因 `max_tokens=128` 被 reasoning 吃光导致整轮 vacuous，
本波把通道请求提到 **512**、阶梯 4096、档位对照 ≥1500；
`_w8_eff_xhigh.json` 出现 `reasoning_tokens(688) > completion_tokens(600)` 的口径异常，
⇒ 判据必须同时记录两者，否则读出相反结论。**流式/非流式样本不支持单调性结论 ⇒ 不做强度排序承诺。**

---

## 8. 文件域切分建议（防 W7 双写覆盖）

> W7 实摔：两个 agent 同时写 `SwitchGuard.kt` 互相覆盖 **3 次**
> （`handoff-20261104-wave7.md:108`）。知识库 §6：派单后先 `git status` 核实实际产出再采信完成通报；
> 「最终口径」压缩成**一条**消息下发；子 Agent ≤4 路并行（本波 2 路 + 主理人 1 路，合规）。

### 8.1 两路并行席位独占文件域（**以 `w8-plan.md §四` 为准，本席初稿作废**）

> 🔴 **订正**：本席初稿把 `SettingsRoute/ViewModel/Screen` 划为「零改动共享禁区」、
> 把 `core-model/MarkdownParser.kt` + `JsonCheck.kt` 划给 B、把 `ChatTopBar.kt` 划给 B，
> 均与派单口径不符。主理人已落锤：**B 独占那三个 feature-settings 文件**；
> `core-model` 里 B **只写 `JsonFormatCheck.kt`**；`MarkdownText.kt`（feature-chat 新文件）自带纯解析器，
> **不新增 `core-model/MarkdownParser.kt`**；`ChatTopBar.kt` 不在派单清单内。
> 下列清单逐字照 `w8-plan.md §四`，冲突一律以该文件为准。

**席位 A（数据模型 + 协议 + 端点高级配置 UI）**

```
core-model/src/main/.../RemoteEndpoint.kt
core-model/src/main/.../EndpointCapabilities.kt            [新]
core-engine/src/main/.../provider/OpenAiCompatibleAdapter.kt
core-engine/src/main/.../RemoteLlmEngine.kt
core-engine/src/main/.../ReasoningBody.kt                  [新，抽 buildReasoningObject 保 G11 ≤120 行]
core-engine/src/test/.../provider/OpenAiCompatibleAdapterTest.kt
core-data/src/main/.../EndpointStore.kt
core-data/src/main/.../EndpointProbe.kt                    （Q15 同波）
feature-settings/src/main/.../EndpointEditScreen.kt
feature-settings/src/main/.../EndpointAdvancedConfig.kt   [新，≤120 行/方法]
feature-settings/src/main/res/values/strings_advanced.xml [新]
```

**席位 B（会话显示 + 图片入口闸门）**

```
core-model/src/main/.../JsonFormatCheck.kt                 [新，B 独占；A 不写]
core-design/src/main/.../GlassSurface.kt                  （仅加 1 参「annotatedText」≤10 行，不新增文件）
feature-chat/src/main/.../ChatBubble.kt / ChatMessageList.kt / ChatScreen.kt
feature-chat/src/main/.../MarkdownText.kt                  [新，含纯解析器 parseMarkdown]
feature-chat/src/main/res/values/strings.xml
feature-chat/src/test/**                                   （新用例）
core-data/src/main/.../SettingsRepository.kt              （markdownRender 键）
feature-settings/src/main/.../SettingsViewModel.kt / SettingsScreen.kt / SettingsRoute.kt
app/src/main/.../ui/AppNavHost.kt                         （ChatRoute 读设置 + 图片入口闸门）
```

**三条主理人硬约束**（`w8-plan.md §四`）：
① `JsonFormatCheck.kt` 判归 B，A 不写（本席初稿二义在此消除）；
② **两席都不改 `build.yml`**：各自报告新增严格 `@Test` 数，主理人在最终集成提交里一次性同步
   `EXPECTED_TESTS`（避免并发改同一文件 + CI 双向对账中间态红）；
③ **不新增守卫**（新守卫扫空集 ⇒ VACUOUS 1→2 ⇒ G21 判红）；不动 `LINE_CAPS`，触顶先拆。
`ChatViewModel.kt`（487/500 余 13）与 `AppContainer.kt` 仍是两席禁区。

### 8.2 共享禁区（单一写者 = 主理人，串行）

```
core-data/.../AppContainer.kt             （DI 唯一装配点，团队点名禁区）
feature-chat/.../ChatViewModel.kt         （487/500 余 13 行，零改动）
feature-chat/.../ConversationOps.kt       （Q1/Q5 快修，主理人）
core-data/.../SessionImportParser.kt      （Q2 BOM / Q3 append 顺序，主理人）
feature-chat/.../ChatAttachmentPicker.kt / ChatInputBar.kt（Q7 KDoc，主理人）
core-model/.../GenerationEvent.kt         （挂账 W8-03 争用文件，本波零改动）
core-model/.../Generation.kt              （Q7' 一行 KDoc，由 A 席顺手改；字段挂 W9-06）
scripts/arch-guard.sh / arch-guard-selftest.sh（不新增守卫）
.github/workflows/build.yml               （两席都不改；主理人集成时一次性同步 EXPECTED_TESTS）
docs/**                                   （台账/roadmap/handoff/契约，主理人 + CI 席）
gradle/libs.versions.toml / settings.gradle.kts
LINE_CAPS / TOTAL_LINE_CAP / VACUOUS_BASELINE / EXCEPTIONS（一律不动）
```

⚠️ **订正**：`SettingsRoute.kt` / `SettingsViewModel.kt` / `SettingsScreen.kt` **从本禁区移出**，
判归**席位 B 独占**（`w8-plan.md §四`）；本席初稿把它们列为「共享禁区/零改动」已作废。
`feature-chat/src/main/res/values/strings.xml` 同样归 B（不是「两席一律用新 strings 文件」）。

**合并纪律**：两席各自只提交自己域内文件；主理人合并前 `git status` 核实实际产出
（知识库 §6「子 Agent 回报可能虚构」）；快修批在主理人分支串行落笔，不与两席并行（避免 W7 双写）。

### 8.3 行数预算（不动任何 cap）

| 模块 | 现役 | 上限 | 余量 | 本波预估增量 | 落点 |
|---|---:|---:|---:|---:|---|
| `core-model` | 1439 | 2600 | 1161 | ≈ +150 | 新枚举/纯函数/解析器/JsonCheck + 测试不计 main |
| `core-engine` | 1081 | 2600 | 1519 | ≈ +55 | 新 reasoning 分支 + `ReasoningBody.kt`；**不改 `decodeEvent`/`contentElement`** |
| `core-design` | 12439 | **12600** | **161** | **≤ +10** | `GlassSurface.kt` 单参数；**不得新增文件**（审查席硬约束） |
| `feature-chat` | 3619 | 3900 | 281 | ≈ +130 | `MarkdownText.kt` + 4 个文件各 1–3 行参数 |
| `feature-settings` | 3229 | 3400 | **171** | ≈ +135 | `EndpointAdvancedSection.kt`（≤120 行）+ `EndpointEditScreen.kt` ≤10 行 |
| `core-data` | 2329 | 4500 | 2171 | ≈ +20 | `SettingsRepository` 新键 + seeds + KDoc |
| `app` | 1338 | 1600 | 262 | ≈ +15 | `AppNavHost.ChatRoute` |
| **合计** | **35 701** | **42 500** | **6 799** | ≈ **+515** | 触顶先拆，**不调 cap、不引 W7 一次性例外**（`handoff-20261104-wave7.md:122`） |

单文件硬闸：`GlassSurface.kt` 576/600（余 24）、`ChatViewModel.kt` 487/500（余 13）、
`EndpointEditScreen.kt` 338/500、`OpenAiCompatibleAdapter.kt` 388/500、`AppNavHost.kt` 259/350。
G11 方法 ≤120 行 ⇒ `EndpointEditScreen()` 现约 112 行，**高级配置必须外提新文件**，
不能内联 10+ 字段。

---

## 9. 波序裁决建议（独立评估）

### 9.1 对主理人裁决的独立结论：**方向 ✅ 通过，附 5 条订正**

**赞成理由（本席独立复核，非照抄审查席）**：

1. **用户指令优先级最高**：W8 明确下了 7 项功能指令，⑤ 还规定「先本地 UI 及功能测试 +
   adb 真机调试，真机没问题才推仓库及 CI 构建」。四份外审**全部**把 W8 默认成正确性收口波
   （W8深度审查 §0「建议先开一个正确性收口波」、W67 §五「W8 主线 A 正确性收尾」、
   复审41 §二「W8-01 同波 4.0 尺四面统一」）⇒ 采信其技术判断，但**不采信其波序前提**
   （`AGENTS.md §二.2` 证伪优先：先核实报告描述的状态是否还成立）。
2. **文件域硬冲突**：重型重构（C3 四面统一、W8-01 尾部摘要、W8-02 三桶归一、C4 L3 工具规格、
   W3-05 journal）落在 `ConversationOps.kt` / `SettledCompressPolicy.kt` / `SwitchGuard.kt` /
   `PreStepBudget.kt` / `EventReducer.kt` / `ChatViewModel.kt` / `AppContainer.kt`；
   本波必改 `core-model` / `core-engine` / `feature-settings` / `feature-chat`。
   同波并行 ⇒ W7 双写覆盖重演，且 `ChatViewModel.kt` 余 13 行必先拆。
3. **行数硬约束**：`feature-settings` 余 171、`core-design` 余 161、`feature-chat` 余 281；
   仅 C3 的 D9 扩列 + `estimator_floor` + W8-01 双件交付物就远超余量 ⇒ 必触 G10，
   而铁律⑤ 禁止调 cap / 引 W7 例外。
4. **判据互相污染**：`RunnerKey` 含整个 `RemoteEndpoint`（F14）⇒ 本波改高级配置就会令
   `cached_tokens=0`；若同波又改压缩/缓存归因（W8-02/C3），两种 `cache_invalidated` 无法区分
   ⇒ 外审自己要求的「双向断言」在本波不可能成立。
5. **正确性重构需要跑前预登记**（C13）与 A15 双口径，本波已有 7 项功能 + 16 项真机判据，
   塞进去会让「判据写进方案 ≠ 判据被执行」第 6 次发生。

**5 条订正**（前 4 条与审查席一致，第 5 条为本席补充）：

| # | 订正 | 理由 |
|---|---|---|
| 1 | **W8-03 二选一挂账 W9**，并在新 handoff 写「因功能波改期」**显式裁决** | 不是 ≤30 行：14 文件、跨 7 个 main 源集与 7 个测试类，且争 `GenerationEvent.kt` / `RemoteLlmEngine.kt`（本波必改）。不做第 4 次「不许再延期」的模糊顺延 |
| 2 | **supportsVision 是功能波主体，不是顺手快修** | 是「能力声明与实现打架」+ 无 UI 闸门（§3.4），须字段 + `capabilities()` + `AppNavHost.kt:156/170` 闸门三处同改 + 销台账 W3-07。**订正**：`contentElement()` 不改（已正确发 `image_url`），本席初稿的「纵深防御」一项作废 |
| 3 | **Q1（C6.a）只当 2 行并发语义修复，不宣称「收敛单真值源」** | `AppContainer.kt:265`、`SettingsViewModel.kt:229` 仍裸取同一 Flow；且加 runCatching 守卫需新增 selftest case（本波不新增守卫） |
| 4 | **台账/roadmap/handoff 三向回填不能单独提交** | `paths-ignore` ⇒ 纯文档 0 run（F25），必须与代码同车；且台账统计表 11/7/24 → 12/7/23 需一并订正（审查席 N1） |
| 5 | **C1 入账机制 / C13 预登记 / C8 思考 id 共设计：以「文档」形式同波做，实现挂 W9** | 本波新挂 5+ 条账，台账 §零 已量化历史 26% 丢失率；不做入账机制等于重蹈。C8 尤其关键：本波虽未开 X4，但已新增 `reasoning` 对象与思考回传通道，X4 开波前必须预登记「思考回传消息 id 稳定策略 + 前缀指纹共设计」，否则前缀一变缓存永不命中。**仅文档、零文件域冲突** |

### 9.2 同波 / 挂账逐条理由

| 条目 | 裁决 | 理由 |
|---|---|---|
| 用户 ①–⑤ 功能（WP1–WP4） | **同波主交付** | 用户最高优先级指令 |
| ⑥ 多模型矩阵（WP6） | **同波** | 用户点名；PC 侧可与开发并行，不占文件域 |
| ⑦ dots 请求格式 + **T1 流式对照** | **同波，T1 ✅ 已完成** | 用户特别要求；9 组 `stream:true` 实测**结论相反**（链路本就通），本波不改 `decodeEvent`（§3.3 / §7.2）；「App 恒流式」升为全部请求格式判据的前置条件 M4 |
| `supportsVision` / W3-07 | **同波**（并入 WP2/WP4） | 与功能 ①「图片输入」同一字段域，边际成本最低，且销一条老化 4 波挂账 |
| Q1/Q2/Q3/Q4/Q5/Q6/Q7/Q13/Q14 | **同波**（WP5，主理人单人） | 各 ≤30 行、文件域可划给单一写者、有独立判据。**Q2 BOM 是 1M 导入通道前置**（§6.4 B1） |
| **Q7'（新增）** `Generation.kt:79` KDoc 订正 | **同波**（1 行、0 新增 @Test、并入 Q7） | 审查席判定 C12 注释与实现打架**第 7 例**：KDoc 宣称「仅当 `reasoningStyle==REASONING_EFFORT` 时生效」，但 `SamplingParams.reasoningEffort` 零生产消费点；字段本身挂 W9-06 不动 |
| **Q15（新增）** `EndpointProbe` 判据自身修复 | **同波**（席位 A，约 20 行） | 审查席从本席 T10 挂账**升级**：预算 8→≥64 + 谓词改 `TextDelta‖Usage` + 2 条 JVM 用例；与功能域重合（改 `ReasoningStyle` 后「测试连接」必须仍可信）。只提预算仍假绿（`:65 firstOrNull` 首事件即取消） |
| Q11 schema 白名单逐键断言 | **同波随 ⑦ adapter 同车**（席位 A） | 审查席建议；动 adapter 时无钉子会重构失守 |
| `jsonMode` → `response_format` 请求体 | **同波**（席位 A） | **订正**：主理人定稿同波做（§5.1），本席初稿「挂 W9」作废 |
| C1 外审承接表 / C13 预登记 / C8 共设计草案 | **同波（纯文档）** | 零代码冲突；防止新挂账再次静默丢失 |
| W8-03 二选一 | **挂 W9-06，显式改号** | 主理人裁决 ① 已落锤：CI 席已在台账改 CARRIED→W9-06，W8 handoff 写「因用户功能波改期」显式裁决（防第 4 次静默顺延） |
| C3 4.0 尺四面统一 + W8-01 尾部摘要 | **挂 W9 合并为一条** | 二者本就正交合并（复审41 §一配套）；分开挂会再撞号 |
| W8-02 三桶归一（`cacheWrite` 独立字段） | **挂 W9** | 台账已裁决「不在 W8-01 前做 W8-02 缓存归因日志（同文件冲突）」；本波 `cache_write_tokens` 实测全 0，缺含 write 的 fixture |
| C4 L3 工具规格盲区（建议 W8-05） | **挂 W9，不用 W8-05 号** | 三重编号撞车；激活器是 MCP 真机（W5-03 平台阻塞 ⛔） |
| W3-05 journal / FreezeClock（C2） | **挂 W9-05，不独立开 W8.5** | 主理人裁决 ④：本波功能+真机+推送已饱和；handoff 明写「**老化五波，下一波必须开波，不得第 5 次顺延**」 |
| **设备侧 1M 内核熔断解锁（新增）** | **挂 W9-11** | 主理人裁决 ②：设备侧 1M ⛔ 不适用，理由是 §6.4 两条阻断；本波不动 `core-agent` 策略；1M 结论严格限定「PC 侧单次请求窗口」，矩阵报告不得写「设备侧 1M 可用」 |
| W8-04 编辑当前端点放行二次确认框 | **挂 W9** | 与「明文 key 不进 state」红线相关，属安全面，不与本波功能域重合 |
| NEW-E 断流误标 STOP | **挂 W9**，但本波「仅隐藏思考」判据显式标注 ⚠️ | 3 行修复却会改 `terminationReason` 语义，与挂账正确性收口同一判据域 ⇒ 避免半迁移（FastestAI NEW-A 教训） |
| 400 自愈闭环（W67 P1） | **挂 W9** | 需串 `contextLimitHint`/`HardBudgetShrinker`/写回三件事，与本波协议改动争 `RemoteLlmEngine.kt` |
| C9 roadmap 回填 / G24 | **C9 同波（文档）／G24 挂 W9** | G24 属新增守卫，本波不新增守卫 |
| A14 思考 id 共设计（C8）/ X4 判据 6 修正 | **文档草案同波／实现挂 X4 开波前** | 见订正 5 |
| W26-02 `no_tool` 裁决 / 夹具 v3 重跑 | **挂 W9** | 与本波功能零重合，避免扩面 |
| 台账 W7-01/02/03 状态回填 | **同波文档，W7-02 保持 OPEN** | 按 A5/A6 四态如实回填，但 `handoff:93` 明写不承诺命中率 |
| 音频输出 / MCP / 1M 设备侧 | **判 ⛔ 不适用 / ⛔vacuous 并登记** | 不摆假入口，不把 vacuous 写成通过 |

**编号统一（主理人裁决 ④ 已落锤）**：挂账一律 **W9-01…W9-11**（W9-01 压缩量纲 /
W9-02 L3 工具规格 / W9-03 尾部摘要承接 W8-01 / W9-04 三桶归一承接 W8-02 /
W9-05 journal 承接 W3-05 / W9-06 死字段二选一承接 W8-03 / W9-07 安全收口 /
W9-08 400 自愈 / W9-09 断流 STOP / W9-10 守卫与口径专项 / **W9-11 设备侧 1M 内核熔断解锁**）。
`W8-04` 保持 OPEN 本波不动；**禁用 `W8-05`**（路线草稿已占用）；
`docs/notes/w7-context-cache-roadmap.md` 的 W8-0x 全改 **R-xx**，
唯一编号源 = `docs/06-deferred-ledger.md`，`docs/notes/` 里的 `W*-*` 与 `R-xx` 不参与派单。
G20 只扫 `docs/handoff-*.md` ⇒「同号多义」的人工核对项写进 WP0 预登记，**不新增守卫**。

### 9.3 裁决的风险（必须写在 handoff，不许略过）

| # | 风险 | 缓解 |
|---|---|---|
| R1 | 正确性债累积至第六波；W3-05 journal 缺位下，本波自动压缩/长上下文真机「裸奔」（C2 原话） | handoff 写死 W9 第一波号 + 显式改期理由；本波不改压缩链路，避免制造新债 |
| R2 | 同波「低成本快修」定义放宽会偷偷带进重型项 | 三条硬门槛：≤30 行、不碰 `ConversationOps/SettledCompressPolicy/SwitchGuard/ChatViewModel` 的结构、有独立判据 |
| R3 | 新挂 5+ 条账复现 26% 丢失率 | 同波做「外审承接表」（列表形态，避开 G20 表格误判） |
| R4 | `supportsVision` 默认 false ⇒ 老端点图片入口消失（用户可见回归） | §3.4 A 方案 + D6 三段真机判据；在设置页写「开启前确认端点支持图片输入」；登记为**有意行为变更**不是 bug |
| R5 | 纯文档提交 0 run 被误读成 CI 绿 | 文档与代码同车提交；push 后 `gh run list --branch main` 核对（F25） |
| R6 | 本波改 `reasoning` 请求体形态 ⇒ 前缀指纹变化，可能令 W7 A5 的 79.4% 基线失效 | C8 预登记草案同波做；矩阵 M8 按端点分组、区分 `config_changed` 与 `cache_invalidated`（M2） |
| R7 | 新增字段 10+ 个若缺「被调用」判据 ⇒ NEW-G 家族第 6 例 | §10 判据表 P1–P9；真机读回 `endpoints.json` + `buildBody` 产物断言 |
| R8 | 扩枚举值的老版本降级是**静默清空端点**（2.2 新发现） | `EndpointStoreTest` 加 round-trip 用例；登记为已知单向兼容限制 |
| R9 | `EXPECTED_TESTS` 不同提交同步 ⇒ CI 必红（`build.yml:445-523`） | 主理人收口前用严格口径 `grep -rnE "^\s*@Test\b" --include=*.kt . | wc -l` 对账（知识库 §5） |
| R10 | selftest / arch-guard 前台跑超时 | 按团队既定：两者 `run_in_background=true`（知识库 §5 长任务后台跑） |

---

## 10. 判据纪律：每条判据的可执行触发条件与空命中语义

> 本仓纪律（`AGENTS.md §二.3`、`docs/03-contracts.md:589-590`、知识库 §4/§6）：
> **触发条件必须写成可判定谓词；空命中语义不许留白**；
> WARN 判据**有闩**（每 run 只报一条）⇒ **0 命中 ≠ 通过**；
> 「判据写进方案」≠「判据被执行」；判据自身要先用「已知必然通过 + 已知必然失败」样本自证（铁律⑦）。

| ID | 判据 | 可判定触发条件（谓词） | 空命中语义 | 自证样本（先跑） | 四态 |
|---|---|---|---|---|---|
| P1 | 新字段被消费 | `buildBody(endpoint).keys` 随 `reasoningMode/supportedEfforts/supportsVision` 翻转而变化 | 只改了 `RemoteEndpoint` 没改 adapter ⇒ ⛔vacuous | `enabled:false` ⇒ `reasoning.enabled=false` 且无 `reasoning_effort` | ✅／⛔vacuous |
| P2 | 老 JSON 缺键可读 | `Json.decodeFromString(ListSerializer(RemoteEndpoint.serializer()), 老JSON)` 成功且自定义端点数不减 | 只测「不抛异常」⇒ ⛔vacuous（`JsonFileStore` 静默回落 seeds） | 不含任何新键的最小 JSON | ✅／⛔ |
| P3 | 五档降级可见 | `resolveEffort(EXTREME, …)` 返回 `effective=XHIGH && degraded=true`，且 UI 出现提示 | 只发 `max` 无提示 ⇒ ⛔（假五档） | `EXTREME` 必降级；`LOW` 必不降级 | ✅／⛔ |
| P4 | 三态互斥 | §2.5 真值表 7 行逐行 `resolveReasoning()` 输出与期望一致 | 只测 happy path ⇒ ⚠️ | `OFF+reasoningOnly` 必被清 | ✅／⚠️ |
| P5 | 流式思考进 `ReasoningDelta` | **✅ 已由 9 组 `stream:true` 实测行使**：`delta.reasoning` 与 `reasoning_details` 逐字等长、`reasoning_content` 恒 0 ⇒ 现役 `:106-108` 回落命中，`ReasoningDelta` 非空 | 前提「没有流式证据」已消除 ⇒ 不再判 ⚠️／⛔vacuous；真机 D3 再验端到端 | `_w8/resp-*.sse` + `parse-*.json` | ✅（不改 `decodeEvent`） |
| P6 | `supportsVision` 闸门 | `supportsVision=false ⇒ onPickImage==null ⇒ ChatInputBar` 不渲染按钮 | 只改 `capabilities()` ⇒ ⛔vacuous | true/false 两态截图 | ✅／⛔vacuous |
| P7 | Markdown 切换改变渲染 | 同一 ASCII 题面 render on/off ⇒ 截图像素差 + 日志两态 | 只有纯函数单测 ⇒ ⚠️（本仓无 Compose UI 测试） | `"**bold**"` 必有 BOLD 节点；`"plain"` 必 0 节点 | ✅／⚠️ |
| P8 | JSON 检查严格 | J1 通过、J2 失败、J7（单引号/尾逗号）失败 | 误用 `AgentJson.json`(lenient) ⇒ 判据自身失效 ⛔ | J1/J2 成对先跑 | ✅／⛔ |
| P9 | 工具/档位判据前提 | 每条记录 `max_tokens≥512`（档位 ≥1500） | `max_tokens=64` ⇒ **⛔vacuous 不是失败** | `@512` tool_calls=1 必通过 | ✅／⛔vacuous |
| P10 | 图片视觉双要素 | 200×120 真图 + 形状×颜色同时命中 | 1×1 样本或只答颜色 ⇒ ⚠️／⛔ | `_w8_vision.png` 200×120 已核 | ⚠️→✅ |
| P11 | 缓存命中双向 | 同前缀 ≥2 次后 `cached_tokens>0` 且改首字节归零 | 只测「归零」单向 ⇒ ⚠️ | NV A5 21 680/79.4% 为正向锚 | ⚠️／✅ |
| P12 | 压缩 `applied` | `auto_compact dropped=N kept=M cache_invalidated=true` 落账 | 全是 `noop` 日志 ⇒ ⚠️ 部分（W7 A4） | `noop reason=below_trigger` 必须能被检出（不静默） | ⚠️／⛔vacuous |
| P13 | 三向文档一致 | 台账 ↔ handoff ↔ roadmap 的波号/状态一致；`grep "W8-0" notes/` 只剩 R-xx | 只改一处 ⇒ ⚠️ | 编号撞车 3 处逐条核 | ✅／⚠️ |
| P14 | 四闸门 | `assemble` / `fulltest` / `selftest` / `arch-guard` 四份日志 exit 0 且 `EXPECTED_TESTS` 对账通过 | 纯文档提交 0 run ⇒ ⛔vacuous（F25） | `fulltest` XML mtime 全为本轮（知识库 §5 三假象） | ✅／⛔vacuous |
| P15 | 真机取证 | 每一步 tap 有 journal/日志证据；截图非全黑；PID 核对 | 只信 `uiautomator` bounds ⇒ ⛔（F21/知识库 §2） | 唤醒后截一张非全黑图 | ✅／⛔ |

---

## 11. 风险与坑点：明确「需规避」清单 + 知识库/历史依据

| # | 需规避 | 依据（知识库章节 / 历史提交 / 文档） |
|---|---|---|
| K1 | **「组件写完 ≠ 组件被用上」**：新增 10+ 字段、新解析器、新 UI 若没有消费点/真机判据，就是 NEW-G 家族第 6 例 | `handoff-20261104-wave7.md:103`（本波第 5 次出现）；`docs/06-deferred-ledger.md` W6-02/W5-05/W6-05/W3-02；铁律② |
| K2 | **「测试全绿 ≠ 口径被验证」**：696 全绿不证明 `stream:true` 路径、`applied=true`、G19 有效 | `AGENTS.md §二.3`；知识库 §5「判定四态」；W7 交接 §四 A7 ⛔vacuous |
| K3 | **WARN 判据有闩 / 0 命中 ≠ 通过**：`noop` 落账、G19、VACUOUS 棘轮 | 知识库 §4：`nativeToolsMismatch` 每 run 只报一条；`scripts/arch-guard.sh:250-256` `VACUOUS_BASELINE=1` |
| K4 | **第二把尺**：再建一个 effort / 窗口 / 能力位真值源 | W5-01 的 33.8K 事故；复审41 C3 四面统一；F12 死字段 `SamplingParams.reasoningEffort`；知识库 §4「能力位替换非并集」 |
| K5 | **双写覆盖**：两席同写 `SwitchGuard.kt` 覆盖 3 次 | `handoff-20261104-wave7.md:108`；知识库 §6「并行写入要指定唯一写者」「最终口径压缩成一条消息」；本方案 §8 |
| K6 | **守卫假绿**：新增守卫扫空集 ⇒ VACUOUS 1→2 ⇒ G21 判红；`grep -vE` 目标缺失静默判绿 | 知识库 §5「守卫假绿陷阱」「每新增守卫必同步 selftest case」；审查席 §六 |
| K7 | **Kotlin/Compose 高频坑**：`WindowInsets.ime` 是 `@Composable @ReadOnlyComposable`，不能在 `derivedStateOf` 普通 lambda 里读（须先取实例）；`kotlin.math.min/max` 不收 `Dp`（用 `coerceAtMost`）；JVM 单方法 64KB；**enum 新增子类型必须同步所有无 else 的 `when`**（F6）；给已有手势的组件加 `toggleable` 会形成双入口 | 知识库 §3；`feature-chat/.../ChatScreen.kt:107-112` 现役正例 |
| K8 | **Python 脚本改 .kt 越界到类闭合 `}` 之后**；删函数后花括号配平；跨文件批量替换漏 import | 知识库 §3（本仓多次实摔）；本波建议两席只用 Edit 逐文件改 |
| K9 | **中文硬编码上屏**：G16 `CJK_LITERAL_BASELINE=0`，`CJK_SCAN_PATHS` 含 `feature-chat/feature-settings/core-model/core-design` | `scripts/arch-guard.sh:239-250,326-327`；全部新文案进 `strings.xml` |
| K10 | **密钥五处泄漏面**：`extraHeaders`/`customBodyParams` 明文落 `endpoints.json`；模型输出不许进日志/错误串 | `AGENTS.md §六`；`docs/analysis/source-review-2026-11-04.md:66`（`redactHeaders` 零调用点）；本文 §2.2/§5.2 |
| K11 | **真机/OEM 坑**：灭屏冻结安装服务、`input text` 中文 NPE、`uiautomator` 对 Compose bounds 不可靠、键盘弹起发送键位移、应用商店弹窗抢前台、PC logcat 仅 256KiB、journal 按会话键控、判崩溃先核 PID | 知识库 §2（CAM-P 设备经验线）；`AGENTS.md:54-78`；`handoff-20261101-wave6.md:162`；`handoff-20261031-wave25.md:103` |
| K12 | **旧仓语义不可照搬**：旧仓 `ThinkingMode.AUTO/OFF` 是端侧 litertlm 语义（`enable_thinking=false` 恒下发、`inThinking` 由增量流决定）；API 路径不触发 ⇒ 只搬 `GlassSegmented`/`GlassSettingRow` 等**控件形态**，不搬判据；且旧仓无 markdown 渲染器 | `AGENTS.md §一`「搬之前必须问这条判据在 API 路径下还触发吗」；知识库 §4 `thinking` 条；F26 |
| K13 | **许可证红线**：本仓 / 旧仓 Apache-2.0 同作者可搬；Operit LGPL-3.0 / Operit2 AGPL-3.0 **零代码搬运**（含改写式、schema、提示词） | 知识库 §5 |
| K14 | **知识库「模块数=9」是旧仓口径**：本仓 14 模块（G9 三方一致），全量单测核对按 14 | 知识库 §5 vs `settings.gradle.kts`（本席核对）；`AGENTS.md §一` |
| K15 | **引用行号前回原文 grep + `wc -l` 反查修订**：台账 W3-07 写 `GenerationEvent.kt:136`，现役为 `:142`；`w7-line-budget-ruling.md:61` 写 `ChatViewModel 484/500`，现役 487/500 | 知识库 §6；F2/F19；审查席订正 |
| K16 | **不为凑缓存命中率加长 system prompt / 不放开 cleartext / 不调 `TOTAL_LINE_CAP` / 不引 W7 一次性例外** | 外审「明确不要做的事」；`handoff-20261104-wave7.md:122`；`AGENTS.md §四/§五` |
| K17 | **记忆 ≠ 交付物**：本方案结论必须同步进用户会读的 handoff / 台账，否则等于没沉淀 | 知识库 §6；`docs/06-deferred-ledger.md §零` 26% 丢失率 |

---

## 附录 A · 关键证据索引（可回查）

| 主题 | 位置 |
|---|---|
| 端点契约 / 枚举 | `core-model/.../RemoteEndpoint.kt:64-146`；`docs/03-contracts.md:1-107,664-670` |
| 能力声明硬编码 | `core-engine/.../RemoteLlmEngine.kt:89-97`；`core-model/.../GenerationEvent.kt:139-148` |
| 请求体 / 思考风格 / 图片 part | `core-engine/.../OpenAiCompatibleAdapter.kt:69-92,178-189,279-317` |
| 流式解码缺口 | `OpenAiCompatibleAdapter.kt:94-145`（`:106-108` 只认 `delta.reasoning_content|delta.reasoning`） |
| 事件链 | `core-agent/loop/GenerationRound.kt:198-201`；`feature-chat/.../EventReducer.kt:46,102-107`；`ChatBubble.kt:110-135` |
| 气泡正文 | `core-design/.../GlassSurface.kt:225-322,498-545` |
| DI / RunnerKey | `core-data/.../AppContainer.kt:123-133,264-283,348`；`RunnerCache.kt:21-32` |
| 引擎调用点例外 | `scripts/arch-guard.sh:281-285`；`core-data/.../EndpointProbe.kt:54-65` |
| 行数 / 守卫 | `scripts/arch-guard.sh:152-204,239-256,306-327,744-800`（g10/g17/g18 实现） |
| CI 对账 | `.github/workflows/build.yml:47-53,132,445-523` |
| 1M 阻塞 | `core-kernel/.../AgentPolicy.kt:228`；`core-agent/loop/RoundHeadGate.kt:42-46`；`SettingsRepository.kt:141-144`；`SessionImportParser.kt:53,56,67` |
| W8 实测底稿 | `docs/analysis/w8-provider-probe.md`；工作区根 `_w8_*.json` / `_w8_vision.png` / `_w6/import-1m.jsonl` |
| 审查席回树判定 | `docs/analysis/w8-review-triage.md`（507 行，只读） |
| W7 基线 | `docs/handoff-20261104-wave7.md`；`docs/notes/w7-provider-matrix.md`；`docs/06-deferred-ledger.md` |
| 知识库 | `<本机用户目录>/.workbuddy-ai/plugins/cache/my-experts/liquidagent-team/1.1.0/references/liquidagent-knowledge-base.md` §1–§6 |

---

## 12. 事后订正（**优先于本文前文**）

> 输入：`docs/notes/w8-plan.md`（主理人裁决，派单与验收唯一口径）、
> `docs/analysis/w8-provider-probe.md §六`（9 组流式实测）、`docs/analysis/w8-review-triage.md §八`（审查席回赠）。
> 本席对这三条逐条回树复核后**主动撤回**自己的两处推断。按 `AGENTS.md §二.2`，证伪结论必须写进文档，
> 不许只留在聊天里；按知识库 §4「判据自身要先被验证」，我那条「流式断链」推断恰恰是自己没先取证就外推的样例。
>
> ✅ **定稿同步状态（2026-11-05 晚，按主理人「四裁决落锤」消息）**：本席已把订正**写回正文**，
> 不再只靠本附录覆盖——§0.2 观察项、§3.1 文件表（删 `decodeEvent`/`contentElement` 改动、加
> `ReasoningBody.kt`/Q15/Q7'）、§3.2 档位单调性、§3.3（改写为「已证伪的疑似断链点 + 证据」）、
> §3.4 闸门形态、§5.1（`jsonMode` 改同波）、§6.4（两条独立阻断 + W9-11 + 夹具 180 067）、
> §7.1 M4（App 恒流式前置）、§7.2（T1 ✅ 已完成结论相反 + 编号映射 + Q15）、§9.1/§9.2/§9.3
> （W9-01…11、Q7'/Q15、W3-05 不独立开 W8.5）、§10 P5 全部同步。
> **凡与 `docs/notes/w8-plan.md` 冲突处，一律以 w8-plan.md 为准**；本节保留复盘价值。
> 审查席对方案席回赠 4 条的独立回树结论：**无一条证伪**，但 R1（1M 因果）⚠️ 部分属实需重写，
> R2 加重两处、R3 由挂账升级为同波 Q15、R4 补 C12 第 7 例（Q7'）——均已落进对应正文。

### 12.1 我撤回的两条（含复盘）

| # | 本文原结论 | 订正后事实 | 证据 | 复盘 |
|---|---|---|---|---|
| C1 | §0.2 / §3.3 / §7.2 T0 / §10 P5：「20 余份探针全非流式 ⇒ App 真实路径 ⚠️ 部分，存在 `delta.reasoning_details` 断链点，本波必须改 `decodeEvent`」 | **撤回断链判断**。主理人补跑 9 组 `stream:true` + `stream_options.include_usage=true`：首帧 `delta` 同时含 `reasoning:"The"` 与 `reasoning_details:[{text:"The"}]` **逐字等长**（basic 141/141、eff_high 1709/1709），`delta.reasoning_content` 恒 0；现役 `:106-108` 的 `reasoning_content ?: reasoning` **回落命中 `delta.reasoning`，思考本就能进 `GenerationEvent.ReasoningDelta`**；usage 在 `choices:[]` 独立末帧（`:140-147` 已处理）、流尾 `[DONE]`（`isTerminal` 已判）。⇒ **本波不改 `decodeEvent`，也不新增 `reasoning_details` 兼容**（同帧双发会重复 `ReasoningDelta`，属伪工作量）。流式复证：关思考 `rtok=0 / content='PONG'`；`include_reasoning:false` 不回传但 `rtok=32` 照付；tools `finish=tool_calls`/3 碎片；视觉 `red, blue`；`json_object` 严格合法 JSON | `docs/notes/w8-plan.md §二`；`docs/analysis/w8-provider-probe.md §六`；`_w8/req-*.json` / `resp-*.sse` / `parse-*.json` / `stream_probe.py` | 我正确指出了「探针全非流式」这个**证据缺口**，但把「缺口」直接推成「实现有洞」，未先跑一组流式样本自证。知识库 §4 第一原则在这里同样成立：输出侧看着像坏，先查输入侧喂了什么。结论方向反了，但补测动作被触发 ⇒ 该条仍有净产出。**订正后 §3.3 / §7.2 T0 / §10 P5 一律以本表为准**；§3.1 文件清单中「`decodeEvent` 增 `reasoning_details` 解析」一项删除 |
| C2 | §6.4 B4：「`maxRunPromptTokens=400_000` ⇒ 995k 请求在发出前被熔断」 | **归因不完整，重写为两条独立阻断 + 一条叠加**：① `PreStepBudget.enforce`（`TurnLoop.kt:60`，在 gate 之前）按档位硬预算收缩/熔断；W7 真机 `settings.preferences_pb` 实测 `max_context_tokens=32 000` ⇒ `hard≈27 904` ⇒ 995k 在**发请求前**被 `PreStepBudget.kt:86/111/123/159-164` 收缩或 `trip`（**第一因、档位相关**）；② `RoundHeadGate.check`（`TurnLoop.kt:58-78`）判的是**本 run 累计 `promptTokens`（首轮为 0，`RunState.kt:77,237`）** ⇒ 首轮会真实发出、usage 累加后**第 2 轮才熔断**，`AgentPolicy.kt:228` 的 400 000 **不是首轮硬闸**；③ 叠加 `compressedUpTo` 单指针压缩（H3）。`_w7/w7matrix.py:3-14` 确认 PC 侧是纯 Python 直连 HTTP、绕过 app 内核 ⇒ **1M 结论仍只限 PC 侧单次请求窗口**。主理人裁决 ② 接受「设备侧 ⛔ 不适用」并新增 **W9-11 设备侧 1M 内核熔断解锁** | `docs/analysis/w8-review-triage.md §八.1`；`docs/notes/w8-plan.md §一/§八.1`；`core-agent/loop/TurnLoop.kt:58-78`；`docs/notes/w7-provider-matrix.md:293`；`_w7/w7matrix.py:3-14` | 我只 grep 到 `RoundHeadGate.kt:42` 就写了「硬阻断」，没走一遍 `TurnLoop` 的调用顺序与 `promptTokens` 初值 ⇒ 正是知识库 §6「主理人/成员给的判据本身会错，先自己把控制流走一遍」的现场。**§6.4 已在正文完整重写（B4 + 因果表 + 结论 + W9-11）** |

### 12.2 数量 / 口径订正（审查席实测，覆盖前文）

| 项 | 本文原值 | 订正值 | 依据 |
|---|---|---|---|
| 五档梯度 | §2.4 写「low/medium/high/xhigh 单调递增、`max` 疑似非法值被忽略」 | 流式 `low 158 / high 441 / xhigh 193` 与非流式 `71 / 323 / 688` **排序不一致** ⇒ 单次样本不支持单调性结论。「五档真实梯度」标 **⚠️ 部分**；`max` **不直接判非法值**，只登记事实表。**设计含义不变**：wire 可识别值仍是 `low/medium/high/xhigh`，UI 第五档必须显式降级并落 `reason`，禁止静默发未验证值 | `w8-plan.md §二`「档位口径订正」；W7「≥2 次重放才写稳定结论」 |
| 1M 夹具最长行 | §0.1 F18 / §6.4 B1 写 `180 068` | **`180 067` 字符**（本文 180 068 是含换行口径差 1）；`5 221 133 B / 30 行 / 无 BOM` 不变；三道上限仍全过得去 | `w8-review-triage.md §八.5` |
| selftest 用例数 | §1.2 WP5 写「不新增守卫」、§9.2 沿用审查席 | 现役 `CASES=28`，最终验收 `selftest 28/28`；`arch-guard OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`，**VACUOUS 只许减** | `w8-plan.md §六 V8` |
| 图片闸门形态 | §3.4 A 方案写「`onPickImage=null` ⇒ 按钮不渲染」 | 改为 **保留禁用态入口 + 原因提示，不静默隐藏**（避免「点了没反应」与真机无法取证）；真机判据覆盖启用前后各一次视觉往返。台账 `W3-07` 本波 **CLOSED** | `w8-plan.md §五.2` |
| probe 修法 | §3.1 / §9.2 只提「`maxTokens=8` 需复核」 | 完整修法（Q15，同波归开发席 A）：预算 8→**≥64** + `judgeProbe` 谓词改 **`TextDelta || Usage`**（`ReasoningDelta` 单独不算通过）+ **2 条 JVM 用例**（开/关思考两态），约 20 行。原因：`:65 firstOrNull{}` 第一个事件就取消上游，只提预算仍留假绿；`enabled:false` 时正文空 ⇒ 假红 | `w8-review-triage.md §八.3`；`EndpointProbe.kt:57,65,115-119` |
| 数据模型归一 | §2.1/§2.5 三轴 + `resolveReasoning()` | 主理人采纳为**单枚举** `ThinkingMode{OFF, FOLLOW_PROVIDER, HIDE_ONLY, ON, REASONING_ONLY}` 消解三布尔互斥；纯函数改名 `normalizeEffort()` + `validateAdvanced()` 落 `core-model/EndpointCapabilities.kt`；硬不变量 `wire != "max"`、降级必落 `reason` 且可被 UI/日志消费 | `w8-plan.md §三` |
| `jsonMode` / `response_format` | §5.1 把「强制 JSON 模式」判挂 W9 | **同波做**：端点 `jsonMode` 驱动 `buildBody` 发 `response_format`（开发席 A 落请求体），与气泡「检查 JSON」两路分开、各有判据；`jsonMode=false` 不发该键（V4）。本文 §9.2 对应行作废 | `w8-plan.md §五.1,§五.6,§六 V4` |
| 文件域 | §8.1 把 `SettingsRoute/ViewModel/Screen` 划为共享禁区、`JsonCheck.kt` 归 B | **B 独占** `feature-settings/SettingsViewModel.kt`、`SettingsScreen.kt`、`SettingsRoute.kt` + `core-model/JsonFormatCheck.kt`（本席留的二义在此消除，A 不写）；A 新增 `core-engine/ReasoningBody.kt`（抽 `buildReasoningObject()` 保 G11 ≤120 行）与 `EndpointProbe.kt`。**两席都不改 `build.yml`**：各自报新增严格 `@Test` 数，主理人在最终集成提交里一次性同步 `EXPECTED_TESTS` | `w8-plan.md §四` |
| 不改项（新增确认） | §3.1 曾列 `contentElement()` 改动 | **不改 `contentElement`**（已正确发 `image_url`，只补回归用例）；**不改 `decodeEvent`**（C1）。避免制造伪工作量 | `w8-plan.md §五.1` |
| 波号 | §9.2 建议 W9-0x | 已落锤：**W9-01…W9-11**（含 **W9-11 设备侧 1M 内核熔断解锁**、W9-06 死字段二选一须写「因用户功能波改期」显式裁决、W9-09 断流 STOP）；`W8-04` 保持 OPEN；**禁用 `W8-05`**；journal 不独立开 W8.5 | `w8-plan.md §一` |

### 12.2b 审查席对本席回赠 4 条的独立回树判定（无一条证伪，均已落进正文）

| 回赠 | 判定 | 加重 / 订正 | 本文落点 |
|---|---|---|---|
| R1 1M 熔断 | **⚠️ 部分属实** | 「请求根本发不出去」不成立（首轮真发、第 2 轮才熔断）；漏掉更早的 `PreStepBudget` 档位闸；夹具最长行 `180 067`（本席 180 068 为含换行口径差 1）；Q2 BOM 仍值得修 | §6.4 因果表 / B4 / 结论；§12.1 C2 |
| R2 解码不同构 | **✅ 属实** | 响应体无 `stream` 字段、`stream=false` 是请求体字段；**加重**：把 Phase 0 非流式响应喂现役 `decodeEvent` ⇒ `delta==null` ⇒ 零事件被 `ProviderAdapter.kt:192` 静默标 STOP，是 NEW-E/H9 **第二触发面**；流式 usage 在 `OpenAiCompatibleAdapter.kt:132-141` 有代码零实测 | §3.3 保留的关联提示 + §0.2 措辞订正 |
| R3 EndpointProbe | **✅ 属实** | `:65 firstOrNull{}` 首事件即取消 ⇒ **只把 8 提到 ≥64 仍留假绿**；完整修法 = 预算 + 谓词 `TextDelta‖Usage` + 2 条 JVM 用例；**从挂账升级为同波 Q15** | §3.1 文件表 / §7.2 Q15 / §9.2 |
| R4 `SamplingParams.reasoningEffort` | **✅ 属实** | `Generation.kt:79` KDoc「仅当 REASONING_EFFORT 时生效」是 **C12 注释与实现打架第 7 例**；G17 只扫密钥字段名、判不出语义死字段；建议本波顺手改 1 行 KDoc（Q7'）、字段挂 W9-06、「禁止第 3 个 effort 字段」背书 | §3.1 Q7' / §9.2 / §11 K4 |

审查席同时确认：其 §0.1 总判定、§一 逐条判定表、6 条证伪、W8-03 快修证伪、编号撞车结论
**全部维持不变**；§三因 Q15/Q7' 由 Q1–Q14 扩为含新增项，§四 H1/H3/H6/H9 各补一层，
§六新增「流式解码对照」出口判据。

### 12.3 仍然成立、且被裁决采纳的部分（不撤回）

- 波序总方向（功能主波 + 同波只收 ≤30 行重合快修 + 重型挂账 W9）与 5 条订正 ⇒ `w8-plan.md §一` 全部采纳；
- 「输入/输出 = 模态集合」「`supportedEfforts` 空集 = 未声明不是全支持」「`reasoningEffort` 单真值源、
  禁止第 3 个 effort 字段」⇒ `w8-plan.md §三` 采纳；
- 「`ReasoningStyle` 扩第 7 值 `OPENROUTER_REASONING_OBJECT` + 同步无 else `when` 与
  `OpenAiCompatibleAdapterTest.kt:172-174` 列表」⇒ 采纳；
- 「零依赖自绘 markdown（纯解析器 + `AnnotatedString`）、`GlassSurface.kt` 仅加 1 参 ≤10 行、
  流式不解析只定稿渲染、`markdownRender` 默认 false 且不进 `ChatViewModel`」⇒ `w8-plan.md §五.5` 采纳；
- 「JSON 检查不能复用 `AgentJson.json`（`isLenient=true` 会把非法 JSON 判绿）、错误只给行列号不回显原文、
  否决工具落点与全局逐条自动检查」⇒ `w8-plan.md §五.6` 采纳；
- 「G17 命名红线 + `extraBody`/`extraHeaders` 是 Map 故键白名单须由 `validateAdvanced()` 钉死 +
  保存后 `endpoints.json` 无明文凭据判据」⇒ `w8-plan.md §三/§六 V9` 采纳；
- 「设备硬件维度：音频输出 ⛔ 不适用、MCP cleartext ⛔ 不适用、1M 设备侧 ⛔vacuous、
  adb `-P 15037` / 中文不可注入 / uiautomator 对 Compose bounds 不可靠 / 以 journal 与日志为准」
  ⇒ `w8-plan.md §六 V9/§六矩阵口径` 采纳；
- 「不新增守卫、`TOTAL_LINE_CAP=42500` 与 `LINE_CAPS`/`VACUOUS_BASELINE` 不动、触顶先拆」
  ⇒ `w8-plan.md §四` 采纳。

### 12.4 对团队的请求

1. 我先前发给主理人的「紧急·全部 dots 实测为非流式」消息，其**事实部分成立**（第一批确为非流式），
   但**结论部分已被 9 组流式实测证伪**；请以 `w8-provider-probe.md §六` 与 `w8-plan.md §二` 为准，
   不要按我原消息去改 `decodeEvent`。
2. 本文 §3.3 / §7.2 T0 / §10 P5 / §6.4 B4 保留原文供复盘，**执行时一律以 §12.1 为准**；
   若后续要把本文转成交接资产，请直接引用 `w8-plan.md`，不要复制本文未订正的段落。
3. 本席教训登记：外推前先补一组最小自证样本（`AGENTS.md §二.3`、知识库 §4）；
   控制流类结论先自己走一遍 `TurnLoop` 顺序再下笔（知识库 §6）。

---

*方案席 沈思远 · W8 Phase 1 · 2026-11-05（含当日晚 §12 事后订正）· 只读研究，未修改任何源码/配置/设备状态*
