# W6 开波方案（设计，不含实现）

> 基线：`90c5f29`（main）。核实方式：逐条回现役树 grep / 读源码，**不转述交接与外审**。
> 计数复核：`grep -rnE "^\s*@Test\b" --include=*.kt .` = **593**，与 `build.yml:115` 的
> `EXPECTED_TESTS: '593'` **一致** ⇒ 外审「594 vs 593 差 1」**不成立**（已证伪，见 §零）。
> 纪律：本文每条断言给 `文件:行`，无出处者标【推断】。不提出任何"调高上限"型方案。

## 落地状态（2026-11-02 更新，别把本文当"待办清单"读）

| 流 | 状态 | 证据 |
|---|---|---|
| A 上下文正确性 | **代码已落地**（工作树，未提交） | `core-agent/loop/PreStepBudget.kt`（判定点在 `TurnLoop.kt:60` 循环体顶部）、`core-kernel/context/{BudgetFormula,HardBudgetShrinker}.kt`、`GenerationRound.kt:219` `recordContextLimitHint`、`RunTermination.kt:114` 透传、`ContextBudget.kt:249-252` 锚点 |
| A 的判据 | **T1–T7 全部落地** | `PreStepBudgetTest`（T1 字符/T2 占位/T3 `generateCount==0`/T7 标定）、`BudgetFormulaTest:33`（T4 `995_904`）、`OpenAiCompatibleAdapterTest:336` + `RemoteLlmEngineContextLimitTest:58`（T6 第一跳）、`EndpointStoreTest:30`（T6 第二跳）、`ContextBudgetTest:316`（T5 锚点） |
| A 的**宿主写回跳** | ✅ **四跳全接**，第四跳**判据待补** | `ChatViewModel.kt:394` → `ConversationOps.kt:50` → `AppContainer.recordContextLimit` → `EndpointStore`；语义边界在 `ContextLimitWriteback.kt:20-32`。前三跳有判据（见上一行 T6），**第四跳零 `@Test`** |
| A 的 **UI 同源口径**（§2.7） | ✅ **已修**，一处待改 | 分母已换 `hardContextBudgetTokens`（`ChatContextWindow.kt:50-55`，`ChatContextWindowTest.kt` 6 条 JVM 用例含 `nearFullTriggersExactlyAtSoftBudget`）；`:25` 的 `runCatching { …first() }` 仍会吞 `CancellationException`（AGENTS.md §五.4），已报属主 |
| B 台账 / G20 / VACUOUS | 进行中（`build.yml` 已有改动） | — |
| C 手动压缩 / 会话导入 | 内核壳已接（`ChatViewModel.kt:303,312` → `ConversationOps`） | 页面入口与真机待验 |
| D 矩阵 | 部分开跑（长上下文/散文比值已有数据，见 `w6-provider-matrix.md` §六.2） | **判定"超没超窗"的格仍必须等 A 合并** |

> 本文的 §2 是**设计依据**（含根因证伪），不是实施记录。实施与本文的偏差请回写本表。
> 收口复核（2026-11-02）：T1/T2/T3/T7 四条**通过**；T7 的 `5 750` 门槛已改 `20 000`（`PreStepBudgetTest.kt:294`）。
> 剩余三项待办：§2.4「已知残余」（team-lead 裁决为"接受 + 写上界"，已落 KDoc）、§2.7 的 `runCatching`、
> 以及下面的「第四跳判据」与「core-kernel 余量」。
>
> 🔴 **第四跳判据：已补，一条待验**（2026-11-02 更正 —— 本文初稿说"测试没写"是**错的**，当时只 grep 了
> `feature-chat/src/test`，而该类已迁到 `:core-data`）。现状：`core-data/src/test/.../ContextLimitWritebackTest.kt`
> **6 条**（原有 5 条覆盖「正常值恰调一次 / `null`·非正值·空白 id 零调用 / 普通异常只 WARN」，W6 补第 6 条
> `cancellationIsRethrownNotSwallowed` 用**引用同一性**钉住 `CancellationException` 原样重抛）。
> 第 6 条**尚未跑验证**（写者正在让出构建窗口）⇒ 记「已写、待验」，**不许记成已验证**。
> 方法论教训：判"某个缝有没有判据"必须**全仓 grep 类名**，不能只 grep 记忆中的那个模块 ——
> 这正是 `AGENTS.md` §二.2「报告查的是它那一版的树」的同款错误。
>
> ⚠️ **`core-kernel` 1790 / 1800 —— 只剩 10 行余量**：`context/` 1 387 + `policy/` 235 + `log/` 168
> （cap 见 `arch-guard.sh:135`）。这正是 `AGENTS.md` §五.1 点名要避免的死结形态（旧仓 2838/2900、
> W5 的 core-agent 6495/6500）。处置按纪律只能是拆分、不是加 cap；但拆分要同动 G9/`LINE_CAPS`/
> `TOTAL_LINE_CAP` 三处基线。**建议：本波冻结 core-kernel 新增 + 登记台账，拆分排下一波**；
> 拆分方案见 §九。
>
> 🔴 **构建假红（收口期必读）**：本波 4 个 agent 并发跑 Gradle ⇒ Kotlin daemon 互杀
> （日志原文 `Connection to the Kotlin daemon has been unexpectedly lost … killed by another process`）
> ⇒ 增量缓存损坏 ⇒ **幻影 `unresolved reference`，随机落在不同模块**。
> 2026-11-02 两次实例：`feature-settings` 报两个 string 未解析（`strings.xml:42-43` 明明存在，
> 单模块编译 rc=0）、`core-data` 报 `judgeProbe` 未解析（`EndpointProbe.kt:115` 存在，两个文件均未改动）。
> **判据：先 `-Pkotlin.incremental=false` 复验，不要改代码**；仍红再清该模块 `build/kotlin` 后重跑。
> 结论：任何"全仓红"的报告在串行复验前**不算证据**。

---

## 零、先证伪（三处与交接/外审不一致，必须先钉死）

| 来源声明 | 现役核实 | 处置 |
|---|---|---|
| 外审：测试登记差 1（594 vs 593） | 实跑 `@Test` = **593**，CI 锚 = 593 | **证伪**，不改 |
| 交接 §五.1：W5-01 根因 =「判定点只在轮头」 | 不成立（**单靠它无法产生该现象**）。见 §2.1 | **根因订正**，否则 W6 会做错 |
| 交接 §五.1：「窗口 32K ⇒ 预算 32 000，水位线 0.6 ⇒ 19 200」 | 实际预算 = `(32000−4096)×0.75 = 20 928`（`ContextCompressor.kt:37-41` + `AgentPolicy.kt:142,150`），**水位线 0.6 是压缩目标不是触发线** | 口径订正，写进 `AgentPolicy` KDoc |
| 外审：`supportsVision` 硬编码 false（W3-07） | 成立：`RemoteLlmEngine.kt:92` | 本波可选（§1.2） |
| 外审：Skill 导入未接线（W3-02） | 成立：`SettingsRoute.kt:206` `onImport = null` | 本波可选（§1.2） |
| 外审：`autoApproveDangerous` 零消费（W5-05） | 成立 | 推 W7（§1.3） |
| 三份外审均未提 | **`EngineCapabilities` 全仓零消费点**：`capabilities()` 只有定义（`LlmEngine.kt:47`）与 override，无 `main` 调用点 | 登记进台账（§1.1 B 流）—— 这是「声明了没消费者」的第 4 例 |

---

## 一、波次切分与优先级

### 1.1 五个工作流与文件域

| 流 | 内容 | 文件域（**写权限边界**） |
|---|---|---|
| **A · 上下文正确性**（P0） | W5-01 判定点下沉 + 硬预算兜底；W5-02 预算算式与实测上限写回；W26-03 关闭 | `core-agent/loop/{ContextBudget,GenerationRound,ToolResultCommit,TurnLoop,RunState}.kt`；`core-kernel/context/*`；`core-kernel/policy/AgentPolicy.kt`；`core-model/{RemoteEndpoint,GenerationEvent,AgentEvent}.kt`；`core-engine/provider/OpenAiCompatibleAdapter.kt`；`core-data/{AppContainer,EndpointStore}.kt` |
| **B · 挂账台账与守卫**（P0） | `06-deferred-ledger.md`；G20 挂账不丢失；`VACUOUS` 输出态；`EXPECTED_TESTS` 单一写口 | `docs/06-deferred-ledger.md`（新）；`scripts/arch-guard.sh`；`scripts/arch-guard-selftest.sh`；`.github/workflows/build.yml` |
| **C · UI 复用 + 导入 + 手动压缩**（P1） | 旧仓 UI 搬运（三档见 §5）；会话导入通道；手动压缩入口 | `feature-chat/*`；`feature-settings/*`；`app/src/main/java/.../ui/*`；`core-data/{SessionImportParser,LegalDocuments}.kt`（新） |
| **D · 多模型矩阵实测**（P1） | §7 矩阵逐格跑分，只读代码 | `docs/notes/w6-provider-matrix.md`（新）；`scripts/matrix/*`（新） |
| **E · 内核硬化（W3-05）** | journal / FreezeClock / 前台服务 / 恢复卡 | **本波不开**（§1.3） |

### 1.2 三档

**本波必做**：A 全部（W5-01/02 + W26-03 + P2 + P4 + P6 注释订正）；B 全部；C 的**导入通道 + 手动压缩**；D 的**长上下文/缓存/视觉/工具四类格**。
**本波可选**（A/B/C/D 收口后有余量才做）：旧仓 `StorageScreen` / `LegalScreen` / `OnboardingScreen` 搬运；W3-02 Skill 导入接线；W3-07 逐端点 `supportsVision`；W3-01 平台 TTS 真机复验；P1 pager 同源。
**推到下一波**：W3-05 内核硬化（独立 W6.5 波，见 §1.3）；gallery 端侧 TTS/ASR（独立 W6V 波，§6）；Anthropic/Gemini adapter（原 W6 语义）；W5-03 MCP 明文裁决；W5-05 / W5-06（W7）。

**取舍理由（按"用户本轮验收标准"排序）**：用户的三条验收是「多模型全面测试 → UI 复用 → 先本地真机再推仓」。W5-01/02 是**多模型长上下文测试的前置**：预算算式不改，1M 档测出来的每个数字都不可信（§2.5）。所以 A 与 D 串行、且 A 必须最先合。UI 与导入是"用户点得到"的面，排在 P1。W3-05 不在这三条验收里，且它要动 `AgentRunner` 装配与 `app` 前台服务，与 A 的 `core-agent/loop` 文件域重叠 —— 同波做必然互相踩。

### 1.3 为什么这样切（写冲突最小化）

- **A 独占 `core-agent/loop/` 与 `core-kernel/`**：这是全仓最热的两处，任何第二个流进来就会冲突。P2（`AppContainer.kt:327`）与 P4（`GenerationRound.kt` catch 分支）**刻意划进 A**，因为它们与 A 同文件。
- **B 与 A 零源文件重叠**，可**完全并行**，唯一耦合是 `EXPECTED_TESTS`：**B 是唯一写口，A/C/D 只报告自己新增的 `@Test` 数**，B 收口时一次改。若反过来，三流同时改 `build.yml:115` 必冲突。
- **C 独占 `feature-*` 与 `app/ui`**：A 不碰 UI（A 只改 `AgentEvent` 字段，C 只读不写）。唯一接缝是 `AgentEvent.Failed` 若新增字段，C 的 `EventReducer` 必须同步 —— 约定：**A 新增字段一律给默认值**，C 的编译不受影响。
- **D 只写 `docs/` 与 `scripts/matrix/`**（新目录），不碰 `scripts/arch-guard*`（B 的域）。
- **E 不开**：W3-05 的 journal 要给 `RunState` 加落账点、给 `AgentRunner` 加 `NonCancellable` 收尾（`AgentRunner.kt:55-57` 已留话），与 A 对 `RunState` 的改动直接冲突。第三方外审的终裁也是「W6 之后、W7 之前独立成波」。
- **CI 口径提醒**：`build.yml:51-53` 的 `paths-ignore` 含 `docs/` 与 `*.md` ⇒ **纯文档推送 0 run**。B 与 D 若只改文档，必须夹一个代码改动或走 `workflow_dispatch`，否则"0 run 看起来和通过一样"（AGENTS.md §八）。

---

## 二、W5-01 + W5-02 落地设计

### 2.1 根因订正：判定点位置不是充分原因

`prepare` **每轮都跑**：`TurnLoop.kt:62` → `GenerationRound.run` → `runAttempt` → `buildMessages`（`GenerationRound.kt:104-105`）→ `budget.prepare`（`ContextBudget.kt:147`）。重试也每 attempt 跑一次（`GenerationRound.kt:69`）。所以那个"发出 33 800 token"的第 2 轮请求**必然经过 `prepare`**。它能被发出去，只可能因为下面四条之一（M1/M2 两条**同时成立也完全相容**，无需择一）：

| # | 机制 | 证据链 | 能否产生"33.8K 且无压缩日志" |
|---|---|---|---|
| **M1** | 估算器系统性低估代码/JSON | `TokenEstimator.kt:23` 非 CJK 一律 `4 字符/token`；而 W5 真机反推 ≈ **2.0 字符/token**（64 000 字符聚合上限 ÷ ≈32 000 实发 token，`AgentPolicy.kt:73`） | ✅ 估算 ≈16k < 预算 20 928 ⇒ 在 `ContextBudget.kt:158` 直接 `return full`，**连压缩分支都没进** |
| **M2** | 压缩器"无损放弃" | 单轮历史 = `[USER, MODEL(calls), TOOL(results)]` ⇒ `safeCutIndex` 回溯到 index 1（`ContextCompressor.kt:225-236`），`alignToTurn` 再走到 USER@0（`:192-212`）⇒ 保留区 = 全部 ⇒ `keptBody.size >= body.size` ⇒ `ContextBudget.kt:165-169` 放弃并 `return full` | ✅ 有一条 `WARN not compressible`，但**没有** `context compressed` 行 —— 与"没有触发任何压缩"的观测一致 |
| **M3** | 判定点位置 | —— | ❌ **单独不成立**（上面已证 prepare 必跑） |
| **M4** | 压缩总开关被关 | `ContextBudget.kt:157` `if (!ctx.policy.compressContext) return full`；`AgentPolicy.kt:136` 默认 `true`，**全仓零写入点**（无 UI、无 preset 设置它） | 本波不成立（默认开），但它是第 4 个"声明了没消费者"的开关 —— 一旦有人接进设置页并默认关，**全部压缩静默消失**且无任何日志。登记进台账，并在 W6 给它加一条 WARN（关掉时打一行 `compress=disabled_by_policy`） |

**结论**：`判定点下沉` 是**必要的结构改进**，但它**不是这次事故的解**。三份文档把它写成唯一根因，会让实施者做出"下沉 + 复用同一个估算器 + 用同一个估算器写单测"这一套，而那条单测**永远绿**。这是本方案认为最容易被做错的一条（§八）。

### 2.2 判定点下沉到哪里

**落点：新文件 `core-agent/loop/PreStepBudget.kt`，由 `TurnLoop` 的 `while` 体**顶部**一行委托调用。**

> **落地偏差（已接受，实现比本文初稿更好）**：初稿写"`TurnLoop.kt:74`（工具批之后）"，实现放在
> `TurnLoop.kt:60`（`while` 体顶部，`gate.check` 之前）。理由成立且更强：**工具批执行完循环就回到顶部，
> 所以顶部天然覆盖"工具结果提交后"这个时点；同时它还能覆盖首轮** —— 一条 100 万字符的用户输入
> 必须在发出第一个请求之前被拦下，而判据 T3 断言 `generateCount == 0`，挂在工具批之后**永远满足不了**。
> 本文 §2.6 T3 就是按"顶部"设计的，初稿的落点写法与自己的判据不自洽 —— 以代码为准。

不选另外两个候选：

- **不选 `ToolResultCommit.commit`（`ToolResultCommit.kt:69-79`）**：它今天是纯写入器（三件事：截断 / 聚合 / 落副本），让它反向依赖 `ContextBudget` 会把"写历史"与"判预算"耦合进同一个类，且 `commit` 只覆盖工具这一条增量来源（记忆注入 / 技能正文 / MCP 动态注册都不走它）。
- **不选 `GenerationRound.buildMessages`**：那是现有 `prepare`，就是被判"太晚"的位置；在同一函数里再加一层只是把两个判定挤在一起。

选 `TurnLoop` 的理由（三条，都可验证）：① 它是**唯一**知道"一次 run 里所有增量步骤"的地方（`TurnLoop.kt:43-82` 的 `while` 体）；② 后面接 `ctx.state.beginRound()`（`:59`），是天然的"pre-step 单点"；③ 它已经是纯控制流（类头 KDoc `:7-13` 明令 `while` 体内零业务逻辑）⇒ 新增调用必须是**一行委托**，判据逻辑全在 `PreStepBudget` 里。

> 与现有 `prepare` 的分工**不是"更早 vs 更晚"**，而是**"软 vs 硬"**：`prepare` 用软预算（水位线）做**丢历史**（优雅压缩，会破前缀缓存）；`PreStepBudget` 用硬预算做**原地收缩**（不丢消息，只在单条工具结果内换占位）。两个动作必须分开，因为丢历史在"单轮 + 大批工具"形态下**无东西可丢**（M2）。

### 2.3 三层判据（同一处实现，三个阈值三个动作）

| 层 | 量纲 | 阈值 | 动作 | 缓存代价 |
|---|---|---|---|---|
| L1 软 | token（估算） | `softBudget = (window−output)×compressThreshold` = 20 928（32K 档） | 丢最老历史（现役 `WindowContextCompressor`） | **破**（切点后移） |
| L2 硬 | token（估算） | `hardBudget = window−output` = **27 904**（32K 档：32 000−4 096） | 原地收缩最大的 TOOL 结果正文 | **不破**（前缀字节不变，只改尾段内容） |
| L3 字符 | **字符**（与估算器无关） | `hardCharCap = hardBudget × effectiveCharsPerToken`；floor 档 = **50 227**（27 904×1.8），有 provider 真值后按 `measured` 在 [1.8, 6.0] 内标定 | 同 L2；L2/L3 都压不下去 ⇒ **不发请求**，`BreakerTripped` | 不破 |

> ⚠️ **算术订正（2026-11-02）**：本文初稿把 32K 档硬预算写成 28 904（错）。正确值 32 000−4 096 = **27 904**，
> 字符硬上限 27 904×1.8 = **50 227**。已在 `PreStepBudgetTest` 里钉成常量断言。

**为什么必须补 L3**：L1/L2 都建立在同一个 `TokenEstimator` 上，M1 证明它对代码/JSON 有 ~2× 的低估。**用有偏的尺子量自己，等于没量。** L3 换量纲：只要"发出去的字符数 ≤ 硬预算 × 下界字符/token"，无论对端 tokenizer 多激进都超不了窗口。这是本方案里唯一**不依赖估算器正确性**的判据。

**⚠️ 设计订正（2026-11-02，被真机数据推翻了一半）**：本文初稿写"`HARD_CHARS_PER_TOKEN` 取 1.8 常数"。
**这个常数不能固定**——chars/token 是**内容相关**的：英文散文 **5.73～5.75**（dots 五档，误差 <0.5%），
代码/JSON ≈**2.0**（W5 反推）。固定 1.8 在散文会话里会把可用字符空间砍到 1/3 —— 那不是"保守"，是**可感知的信息损失**。
正确形态是**区间标定**（已落地 `core-kernel/context/BudgetFormula.kt`）：
```
effective = if (measured <= 0) floor else measured.coerceIn(floor, ceiling)   // floor 1.8 / ceiling 6.0
measured  = charsSent / prompt_tokens      // RunState.measuredCharsPerToken，每请求更新
```
`measured` 只算 `messages` 字符而 `prompt_tokens` 含 `body.tools` ⇒ 分母偏大 ⇒ **measured 偏低 ⇒ cap 偏小 ⇒ 站在安全侧**（这条方向性论证必须留在 KDoc 里，否则下一波会"修正"它）。
⇒ 由此得一条更普适的结论：**M1 不是恒定偏差，是内容相关偏差**——这也正是"拿同一个估算器写单测会永远绿"的第二层原因。

### 2.4 硬预算兜底动作：原地收缩（不是丢消息）

新纯函数 `core-kernel/context/HardBudgetShrinker.kt`：`shrinkToCharCap(messages, charCap): List<ChatMessage>`

四条不变量（与 `ToolResultCommit.boundAggregate` 同源，`ToolResultCommit.kt:90-101` 已给出论证，直接复用）：
1. **只改 `ToolResult.output`，不删消息、不动 `callId`** ⇒ 不会让 `sanitizeForProvider`（`ContextCompressor.kt:250+`）把"被省略"补成"工具失败"。
2. **从最旧开始砍**（`ToolResultCommit.kt:109` 同一理由：模型继续干活依据最近那批）。
3. **最后一条保持完整**（循环上界 `size-1`，同上 `:110`）。
4. 占位措辞用 `[omitted: hard budget, N chars]`，**绝不用 error/failed**（`ToolResultCommit.kt:123` 的口径）。

L2/L3 都失败（历史里没有可收缩的 TOOL 结果，例如单个巨型 USER 输入）⇒ **不发请求**，走 `RunTermination.emitBreaker`（`RunTermination.kt:86`）并带 `detail = "context_overflow_prevented: chars=X cap=Y"`。比发一个注定 400 的请求便宜（一次往返 + 一次计费），且**用户看得见**。

**已知残余（低危，需在 KDoc 里写明或补一次终检）**：权威的字符判定跑在 `working()` + `systemText` 上，而**真正发出去的**是 `ContextBudget.prepare` 里 `sanitizeForProvider` 之后的 `full`。后者会为"声明了却没结果"的调用**补合成结果**（`ContextCompressor.kt:283-301`，每条 `MISSING_RESULT_MESSAGE` ≈110 字符）⇒ 实发字符可略超 `charCap`。上界 = 缺失条数 × ~110 字符（≤12 并发调用 ⇒ ≈1.3 KB，相对 50 KB 上限可忽略），且只在"压缩丢了宿主 / 调用被中断"时才出现。
两个处置二选一：① 在 `prepare` 返回前对 `full` 复用一次 `HardBudgetShrinker.promptChars` 做终检；② 接受并写进 KDoc + 给上界。**不许既不写也不测**——那会让"字符上限"在下一次排障时被当成精确值。

### 2.5 预算算式（W5-02）

`ContextCompressor.kt:37-41` 现役已经**减了 `maxOutputTokens`**（外审说"要减"是**已经做了**的），真正缺的是两件事：

**(a) 拆成两个函数**（现役只有一个，语义混着用）：
```
windowEffective = min(endpoint.verifiedContextLength ?: endpoint.contextLength, policy.maxContextTokens)
hardContextBudgetTokens = windowEffective − endpoint.maxOutputTokens      // 不许乘水位线
contextBudgetTokens     = hardContextBudgetTokens × policy.compressThreshold  // 现役语义不变
```
拆分的理由：`ContextBudget.kt:162` 已经拿 `budget − toolTokens` 当"消息预算"喂给压缩器，若 `budget` 里含 0.75，压缩器的"装不下"判定与硬上限差 25% —— L2 一旦用同一个数就会**少算 25% 的可用空间**，等于硬预算形同虚设。

**(b) `verifiedContextLength` 与"学习式写回"**（不加任何探测请求）：
- `RemoteEndpoint` 新增 `verifiedContextLength: Int? = null`（`@Serializable` 默认 null ⇒ 反序列化兼容；字段名不含 `token`/`key`，G17 不受影响）。
- 数据来源：**真实运行的 400**。`OpenAiCompatibleAdapter` 解析 `maximum context length is (\d+)`（原文在 `w5-provider-matrix.md:89-93`，9 次观测字符串完全一致），把数字塞进 `GenerationEvent.Failed` 的新字段 `contextLimitHint: Int? = null`。
- 传递链（**不新增任何模块边**）：adapter（core-engine）→ `GenerationEvent.Failed`（core-model）→ `RunState` → `AgentEvent.Failed`（core-model）→ `ChatViewModel` → `AppContainer.recordContextLimit(endpointId, n)` → `EndpointStore`。
- 为什么不复用 `EndpointProbe`：它发的是 `"ping"` + `maxTokens=8`（`EndpointProbe.kt:54-60`），探不出窗口；要探就必须**故意发一个超窗请求**（1M ≈ 115 s + 一次计费，`w5-provider-matrix.md:49`）——不可接受。
- 为什么不信任 `EngineCapabilities.maxContextTokens`：它**零消费点**（§零 末行），拿它当写回通道等于给第 4 个空转声明找第 5 个家。

**口径订正（写进 KDoc，防下一波照抄）**：1 000 000 是**输入+输出共享**的上限，`hardContextBudgetTokens` 必须再减 `maxOutputTokens`；不得把 1 000 000 直接当输入预算。

### 2.6 JVM 单测判据（假 engine，零真机、零网络）

`FakeEngine` 已支持多轮脚本（`FakeEngine.kt:29-48`），`TurnLoop` 可端到端驱动（`TurnLoopTest` 已有先例）。

| # | 判据（全部断言**可观测结果**，不断言内部实现） | 反 vacuous 条款 |
|---|---|---|
| T1 | 脚本 `[toolCalls×5, textOnly]`，端点 `contextLength=32_000,maxOutputTokens=4_096` ⇒ **第 2 次 `lastRequest.messages` 的字符总量 ≤ hardCharCap** | 断言的是**字符**，不是 token ⇒ 与 `TokenEstimator` 解耦。若有人用 token 写这条，它会因为 M1 永远绿 |
| T2 | 同 T1 ⇒ 至少一条 `ToolResult.output` 以 `[omitted: hard budget` 开头，且 `callId` 非空、条数与调用数相等 | 断言占位**真的发生**；只断言"没超"无法区分"没超"与"没检查" |
| T3 | 构造"无可收缩项"（单条 USER 100 万字符）⇒ 终态是 `Failed(terminatedBy=BreakerTripped)`，且 `FakeEngine.generateCount == 0` | 断言**没发请求**。若只断言"有失败事件"，路径可能来自别的熔断 |
| T4 | 端点 `contextLength=1_048_576`、`verifiedContextLength=1_000_000`、`maxOutputTokens=4_096` ⇒ `hardContextBudgetTokens == 995_904` | 断言**具体数值**；W5-02 的判据是算式，不是"能跑通" |
| T5 | 假引擎第 1 轮 emit `Usage(prompt=33_800)`；第 2 轮断言 `ContextBudget` 的估算值 **≥ 33 800**（锚点生效） | 断言"估算器被真实值纠正"。这是 §2.1 的 M1 修复的**唯一**可判定证据 |
| T6 | 假 adapter 返回 400 + `maximum context length is 1000000` ⇒ `GenerationEvent.Failed.contextLimitHint == 1_000_000`，且经 `AppContainer` 后端点页读到的 `verifiedContextLength` 为 1 000 000 | 断言解析 + 落盘**两跳**。只测解析器会漏掉"解析出来没人存" |

**T5 的补充设计（锚点+增量）**：`RunState` 加 `lastReportedPrompt: Int = 0` 与 `lastReportedAtMessageCount: Int`；`prepare` 的估算改为 `lastReportedPrompt + estimateMessages(body.drop(lastReportedAtMessageCount)) + toolTokens`，压缩后锚点**失效置 0**（否则拿压缩前真值去估压缩后历史）。注意：**它只修静态部分**，本例的增量本身就是被低估的那 32 000 token，所以 T1/T2 的 L3 才是主修。这条必须写进 KDoc，否则下一个 agent 会以为锚点就够了。

### 2.7 UI 侧的同一口径必须同源（现役缺陷，随 A 流一起修）

`ChatViewModel.kt:144` 把 **声明值** `endpoint.contextLength` 塞进 `ChatUiState.contextLimit`，而 `ChatContextMeter.kt:51` 用它当分母、阈值取 `AgentPolicy().compressThreshold`（`:29`）。

**后果**：32K 窗口档 + 端点声明 `1,048,576`（W5 真机就是这个组合，D3 截图 `1.9K / 1048.6K`）⇒ 变色点在 **786K**，而压缩早在 **20 928** 发生 ⇒ **状态条永远不会变色**，直接违反该组件自己 KDoc 的判据（`:26-27`「阈值比压缩点还晚的话，用户看到变色时其实已经晚了」）。`verifiedContextLength` 写回后它也不知道 —— 同一个数在三处各有各的口径，正是 §2.5 要消灭的形态。

**最小改法（组件一行不改）**：分母换成 `hardContextBudgetTokens(endpoint, policy)`，`CONTEXT_WARN_RATIO` 保持 0.75。
理由：`contextBudgetTokens = hardContextBudgetTokens × compressThreshold` ⇒ `used/hard ≥ 0.75` ⟺ `used ≥ softBudget` = **压缩触发点本身**。那个"借 ratio 表达阈值"的写法只在这个分母下精确，换任何别的分母都只是巧合。
判据（JVM）：`(contextLength=1_048_576, verified=1_000_000), maxContextTokens=32_000` ⇒ `contextLimit == 27_904`；`maxContextTokens=1_000_000` ⇒ `995_904`。只断言"渲染出来了"是 vacuous。

---

## 三、手动压缩

**现役有没有入口：没有。** 三处证据：① `feature-chat`/`feature-settings`/`app` 对 `compress` 的引用只有 `ChatContextMeter.kt:25`（读 `compressThreshold` 画水位条）；② 内核不暴露压缩 API —— `ContextBudget` 是 `GenerationRound` 的构造参数（`GenerationRound.kt:51`），`AgentRunner` 的构造参数里没有它（`AgentRunner.kt:68-74`）；③ `RunState` 每 run 新建（`AgentRunner.kt:116`），`working` 从 `request.history` 起（`RunState.kt:218-238`）⇒ **内核里没有任何跨 run 的上下文可压**。

**关键约束（决定最小设计的形状）**：history 的真值源是 **UI 气泡**（`ChatViewModel.kt:263` `snapshot.bubbles.flatMap { it.toHistoryMessages() }`，实现 `ChatHistoryMapper.kt:38`）。压缩产物**必须落到气泡侧**，否则下一次 run 又从全量气泡重建，压缩等于没做。

**最小可行设计（5 件，全在 C 流）**：
1. `ChatUiState`（`ChatUiState.kt:150`）加 `compressedUpTo: String? = null`（气泡 id）+ `compressedSavingTokens: Int = 0`。
2. `ChatHistoryMapper.toHistoryMessages` 的入口改成 `bubbles.toHistoryMessages(fromId = state.compressedUpTo)`：`compressedUpTo` 之前的气泡**不进 history**，但仍留在列表里给用户读。
3. 内核加一个**纯函数** API：`core-agent/context/ManualCompress.kt` 的 `suspend fun compressHistory(history, endpoint, policy, systemText, toolSpecs): CompressionResult`。复用现役 `WindowContextCompressor.compress`（`ContextCompressor.kt:121`）与 `sanitizeForProvider`，**不新建算法**。`systemText` 由调用方传（它不在 history 里，见 `ContextBudget.kt:31-37`）。
4. UI 入口：`ChatTopBar` 溢出菜单「压缩上下文」→ 二次确认对话框（不可逆）。
5. 上屏：历史里插入一条**分隔卡**「已压缩 N 条（展开查看 / 撤销）」。撤销 = 清空 `compressedUpTo`。

**"前缀缓存必然失效"怎么向用户表达**：确认对话框里写死一句「压缩后本会话的提示缓存会从头重建，下一次请求的缓存命中为 0、费用更高」。**不要写"不影响"**，也不要只说"节省 token" —— 只说收益不说代价是本仓明令禁止的形态（`ContextBudget.kt:40-45` 同一纪律：账单上只表现为"贵了"，是最难发现的回归）。落盘一条 INFO 账：`manual_compress dropped=N kept=M cache_invalidated=true`。

**判据（JVM）**：20 条历史 + `hardBudget` ⇒ `kept.size < 20`；system 段 SHA-256 压缩前后相同（钉住 `ContextCompressor.kt:41-44` 的不变量）；`compressedUpTo` 生效后 `toHistoryMessages()` 条数 == `kept.size`。

---

## 四、会话导入通道

**格式**：主 `JSONL`（每行 `{"role":"user"|"assistant","text":"..."}`），备 `markdown`（`## user` / `## assistant` 分段）。选 JSONL 为主：可脚本生成（§7 的 990k token 干草堆要靠脚本产出）、无分段歧义。

**入口**：`feature-settings` 诊断组「导入会话（调试）」，**仅 debug 变体可见**（`BuildConfig.DEBUG`）。

**解析落点**：`core-data/SessionImportParser.kt`（纯函数，零 Android、零 IO ⇒ JVM 可测，符合 AGENTS.md §七「纯逻辑放可单测位置」）→ `List<ChatMessage>` → `ChatViewModel.importConversation(messages)`：复用 `newConversation()`（`ChatViewModel.kt:219`，它已处理"取消在途 run + 清审批缓存"），再把 messages 物化成 bubbles。

**三条硬约束（都是踩过 400 的形态）**：
- **只接受 `user` / `assistant`**。出现 `role=tool` 一律丢弃并计数上报：导入的是"题面"，不是"工具往返"；手写 JSONL 无法保证 `tool_call_id` 配对，塞进去会被 `sanitizeForProvider` 洗掉或直接 400。
- 上限：单文件 ≤ 8 MiB、消息 ≤ 5 000 条、单条 ≤ 200 000 字符；超限**拒绝并给具体原因**（与 `SkillImportValidator` 的拒绝形态一致）。
- 附件一律不带（`ChatHistoryMapper.kt:33-36` 已定"历史图片不回灌"）。

**它同时解锁的 vacuous 判据（这是它值 P1 的全部理由）**：
| 被解锁的判据 | 现状态 | 出处 |
|---|---|---|
| 设备侧 1M 行使（W5-04） | ⛔ vacuous「无批量题面注入通道」 | `handoff-20261101-wave5.md:135` |
| L2 摘要真机触发 | 默认启用（`ContextBudget.kt:94-96`）但从未真机触发 | 同上 D11 |
| W26-03 压缩链路真机行使 | ⛔ 挂账丢了三波 | 外审 §一 |
| 手动压缩真机行使（§3） | 本波新增，无入口即无法验 | — |
| 原 W6 判据 4「40 轮长会话不崩、末轮仍答第 1 轮事实」 | 无脚本化通道 | `02-roadmap.md:780` |
| §7 矩阵的长上下文 / 缓存命中格 | 只有 PC 侧脚本 | `w5-provider-matrix.md:101-113` |

---

## 五、旧仓 UI 差距清单

新仓已有：`EndpointList/Edit`、`McpServerList/Edit`、`MemoryScreen`、`SkillListScreen`、`SettingsScreen`、`ToolsScreen`、`SandboxFilesScreen`、`ChatScreen`（已拆 11 文件）。
**设计系统不是缺口**：`GlassCard/GlassScaffold/GlassTopBar/GlassEmptyState/GlassWallpaper/LocalBottomBarOverlay/LocalGlassColors/LocalGlassTokens/rememberGlassHaptics/GlassSegmented/GlassMaterial/GlassChip/GlassFab/GlassTextField/CyberJoystickSwitch/LiquidDialog` 全部在 `core-design` 现役（`GlassScaffold.kt:68`、`GlassSurface.kt:174`、`GlassIndicators.kt:83` 等）。⇒ **搬运阻力在"数据源"，不在"外观"。**

| 旧仓页面（行数） | 三档 | 理由与风险点 |
|---|---|---|
| `DiagnosticsScreen`（1084） | **需改造**（只搬 3 段） | 依赖 `FrameStatsCollector` / `ThermalGovernor` / `ModelHealthProbe`（端侧推理与性能采样，本仓不存在）；且诊断页最值钱的"崩溃恢复 / attempt 账 / 熔断明细"数据源 = journal，**W3-05 未落地**。本波只搬**日志段**（数据源 `AgentLogFileStore.kt:42` 已有）、**连接测试段**（`EndpointProbe.kt:44`）、**用量段**（`RunState.usage()`，`RunState.kt:196`）。其余段推到 W3.5。**风险**：搬全页 = 搬进一堆读不到数据的卡片，正是"装饰"形态 |
| `StorageScreen`（397） | **该搬（需改造）** | 数据源 = 沙箱目录 + `filesDir/harness`（`AppContainer.kt:53,129` 已有）；但新仓 `feature-tools/SandboxFiles.kt` 已实现同一套枚举 ⇒ **必须复用而不是并置**，否则两处目录口径漂移。**风险**：`feature-settings ↛ feature-tools` 是硬边（`arch-guard.sh:132`）⇒ 复用要靠下沉到 `core-data`，不能直接 import |
| `LegalScreen`（217） | **该搬（需改造）** | 纯静态文案 + `LegalDocuments`。**风险**：① 新仓无 `LegalDocuments`，需新建；② G16 中文硬编码基线 = **0**（`arch-guard.sh:151`）⇒ 所有文案必须进 `strings.xml`，照抄旧仓源码会直接判红 |
| `ModelsScreen`（939） | **不该搬** | 整页是"下载/激活/删除端侧模型"，与 `AGENTS.md:17`「本仓不含任何本地推理代码、不下载模型」正面冲突。新仓对应物是 `EndpointListScreen`/`EndpointEditScreen`（已存在）。**唯一可搬**的是"能力位展示"块（视觉/工具/窗口），落进 `EndpointEditScreen` |
| `OnboardingScreen`（188）+ `ConsentScreens`（252）+ `FirstRunGate` | **该搬（改造小）** | 数据源**已经就位**：`SettingsRepository.kt:35` `hasCompletedOnboarding` + `:83` setter —— 但**生产消费点 0**（只有 `SettingsRepositoryPersistenceTest.kt:190-193` 用）。搬这页同时把这条空转声明变成真接线。**风险**：`FirstRunGate` 依赖旧仓 `AppContainer` 的具体方法，需按新 `AppContainer` 重写装配；排在**本波可选**（原 roadmap W8 交付物，`02-roadmap.md:807`） |
| `ChatScreen`（817） | **已搬完** | 新仓拆成 11 文件守住 500 行闸门（`arch-guard.sh:95`）。无需动作 |

**一条总纪律**：搬任何一页前先问「这条判据在 API 路径下还触发吗」（`AGENTS.md:21-22`）。`DiagnosticsScreen` 的端侧性能段、`ModelsScreen` 的下载段，答案都是"不触发"⇒ 删，不留装饰。

---

## 六、语音 TTS/ASR 裁决

**事实三层**：
1. 本仓**已有平台 TTS**：`SpeakTool`（`SpeakTool.kt:62`）+ `SpeechEngine` 端口（`:25`）+ `AndroidSpeechEngine`（`:131`，含 iflytek 真机教训与 5 s 硬超时）。它**不是端侧模型推理**，是系统 `TextToSpeech` 绑定。挂账 W3-01 是"真机未验证 / 默认关闭"，**不是"未实现"**。
2. 云端 TTS 已被裁决**不做**（`w3-provider-matrix.md:57`「语音输出用本地 TTS，不做云端 TTS 适配器」），理由是同文件 `:46-47`：`lyria` 元数据声明 audio 但本区 403，且它是音乐生成不是 TTS。
3. gallery 的 TTS/ASR 是 **litertlm / MediaPipe 端侧推理** ⇒ 与 `AGENTS.md:17` 的定位声明**正面冲突**。

**裁决：本波不做端侧语音。**
- **本波做**：平台 TTS 真机复验 + 设置页语音输出开关（零新模块、零新依赖、零权限），**显式关闭 W3-01**（要么验过转 `CLOSED`，要么写明"本仓永不做云端 TTS，平台 TTS 仅在用户开启时可用"）。成本约半天。
- **端侧语音独立成 W6V 波，且顺序必须是「先改定位声明，再动代码」**：`AGENTS.md` §一 加一条例外条款（"零本地**LLM**推理；语音合成/识别允许端侧小模型，锁在 `:core-speech-mediapipe` 单模块内"）。理由：本仓最忌的形态是"注释写着已接、实际没有"，其镜像形态是"定位写着零本地推理、代码里塞进一个 litertlm" —— 两者都是声明与实现不一致。

**若 W6V 开波，模块边界这样划才不破 G9**：
- `:core-speech`：**纯端口**（`SpeechEngine` / `Transcriber`）+ 平台实现。`SpeakTool` 里的端口与 `AndroidSpeechEngine` 原样下沉（`SpeakTool.kt:25-45,131-238`），`core-agent` 只保留 `Tool` 壳。
- `:core-speech-mediapipe`：litertlm / MediaPipe / Kokoro **全部锁死在这一个模块**，任何其他模块**不得**依赖它（否则"零本地推理"就散开了）。
- 守卫连带（三处必须同一次改）：`arch-guard.sh:74-77` `MODULES` 13→15；`:82-102` `LINE_CAPS` 加两行；`:110` `TOTAL_LINE_CAP` **必须显式抬高并写明"这是新增能力，不是拆分"**（与 W5 铁律 3 的区分：W5 那 1557 行是搬的，所以不许涨；这里是净新增，必须涨，但按实测预算给，不给"先给足"）。`FORBIDDEN_EDGES`（`:119-139`）加两条：`core-agent ↛ core-speech-mediapipe`、`core-speech ↛ core-agent`。
- **不本波做的第二条理由**：G9 / 行数 / CI 三处基线要同时动，与用户"先本地真机、再推仓 + CI"的顺序冲突；且真机上多一套麦克风权限 + ColorOS 后台限制弹窗，风险面与本波的主线（上下文正确性）不同源。

---

## 七、多模型矩阵测试设计

模型（全部免费/零成本档，W5 已实测可用）：`nemotron-3.5-super-vl-120b-a12b`(NV)、`mistral-large-4-1t-a52b`(NV)、`step-5-preview`(NV)、`dots-3-note-preview:free`(OR)、`north-mini-code:free`(OR)。

每格判据必须**可判定**（机读、可复算），不许"看起来正常"。`✅` = 有判据且可跑；`⛔` = 本波做不到（如实标注，不许写成 vacuous 通过）。

| 能力 | nemotron | mistral | step | dots | north-mini | 判据（机读） |
|---|---|---|---|---|---|---|
| chat | ✅ | ✅ | ✅ | ✅ | ✅ | `finish_reason=stop` 且 `content` 非空 |
| tools | ✅ | ✅ | ✅ | ✅ | ✅ | `finish_reason=tool_calls` 且参数 JSON 可解析且工具名在白名单 |
| vision | ✅ | ✅ | ⛔ 未测 | ✅ | ⛔ 404 | 素材 **≥200×120 真实图形**（1×1 已证假阴性，`w5-provider-matrix.md:166-170`）；断言形状+颜色+底部文字三项逐字命中 |
| vision+tools 同轮 | ✅ | ✅ | ⛔ | ⛔ vacuous | ⛔ | 同一响应里 `content` 描述正确 **且** `tool_calls` 非空。dots 上 W5 已证 vacuous（工具起了、读图未证），**本波不许升级为通过** |
| 长上下文 ≤256k | ✅ | ✅ | ✅ | ✅ | ⛔ 未测 | 干草堆取针（针在 50% 深度），`content` **与** `reasoning_content` 双扫（`w5-provider-matrix.md:170`） |
| 长上下文 512k | ✅ | ✅ | ⛔ | ⛔ 未复测 | ⛔ | 同上；dots 只测到 254 771，512k 是声明值 |
| 长上下文 ~1M | ✅ 到 994 998 | ⛔ | ⛔ | ⛔ 声明 256k | ⛔ | **必须设备侧**（§4 导入通道）；`prompt_tokens ≤ 995 904`（=1 000 000−4 096）且取针命中；`finish_reason=length` 判 **vacuous**，不是通过 |
| 前缀缓存 | ✅ | ✅ | ⛔ 未测 | ✅ 有写入延迟 | ⛔ 恒 0 | 同前缀第 2 次 `cached_tokens>0`；**改首字节后必须归零**（双向断言，只测前半段会把"恒定命中"误判成缓存） |
| **分块缓存命中** | ✅ | ✅ | ⛔ | ✅ | ⛔ | 3 段递增前缀（8k→16k→32k），断言 `cached_tokens` **单调不减**且第 3 次 ≥ 第 2 次；尾部不命中量单独记（NV 实测尾部 ~2 060，`w5-provider-matrix.md:147`） |
| 工作空间文件读写 | ✅ | ✅ | ✅ | ✅ | ✅ | `file_write` 后 `file_read` 逐字节相同（含中文/emoji 各 1 例）；写类必须过审批卡（`ApprovalChokepointAuditTest` 已钉） |
| 记忆 | ✅ | ✅ | ✅ | ✅ | ✅ | 写入 → **新会话**提问 → 答案含记忆值（W5 D8 同口径） |
| skill | ✅ | ✅ | ✅ | ✅ | ✅ | `skill_load` 后正文以 `role=USER` 追加 **且** system 段 SHA-256 不变（`02-roadmap.md:707`） |
| MCP | ⛔ **平台阻塞** | ⛔ | ⛔ | ⛔ | ⛔ | `usesCleartextTraffic=false`（G15b）拒明文 http（`handoff:136` D13）。本波只验**降级路径**：无网 ⇒ 磁盘缓存清单 + 「缓存于 <时间>」标注；401 ⇒ 工具下线。**不许**把降级验成"MCP 已联调" |
| 自动压缩 | ✅ | ✅ | ✅ | ✅ | ✅ | §2.6 T1–T3 + 真机：32K 档并行读 5 个大文件 ⇒ 下一次请求 `prompt_tokens ≤ 27 904`（对照 W5 实测 33 800） |
| 手动压缩 | ✅ | ✅ | ✅ | ✅ | ✅ | §3 判据 + 真机点一次 ⇒ 历史条数下降、system SHA-256 不变、账行有 `cache_invalidated=true` |
| **音频输出** | ⛔ | ⛔ | ⛔ | ⛔ | ⛔ | **免费池无 TTS 模型**（`lyria` 403、且是音乐生成）。本格只能用**平台 TTS**（`SpeakTool`）在真机上验：断言 `ToolResult.output` 以 `speaking` 开头、设备扬声器实际发声（人工确认 + logcat 无 `tts_enqueue_failed`）。**不许**用"模型返回了音频 base64"当通过 |
| 音频输入（ASR） | ⛔ | ⛔ | ⛔ | ⛔ | ⛔ | 本仓无音频输入通道（`AttachmentImporter.kt` 只认 image/jpeg 与 text/plain）⇒ 归 W6V |

**矩阵执行纪律**：
1. **顺序**：先 A 流合并 → 再 D 跑分。理由：预算算式不改，1M 档测出的每个 `prompt_tokens` 都不能用来判定"超没超窗"。
2. **先跑 1k 判据自证档**（W5 的 `w5-provider-matrix.md:42` 已立此规矩）：1k 档不通过就不许上探 —— 否则测的是脚本 bug，不是模型能力。
3. **原始响应 JSON 落盘可复算**，密钥不入库（W5 同口径）。
4. 每格三态：`✅ 通过` / `⛔ 做不到（写明阻塞物）` / `🟡 vacuous（写明为什么不算通过）`。**`@Test` 只增不减**，B 流统一对账 `EXPECTED_TESTS`。

---

## 八、本波验收判据汇总 + 最容易做错的三条

**A 流**：§2.6 T1–T6 全绿（+6 `@Test`）；真机 32K 档重放 W5 场景，`prompt_tokens ≤ 27 904`；1M 档 `hardContextBudgetTokens == 995 904`；W26-03 显式 `CLOSED` 并在台账留证据行。
**B 流**：`06-deferred-ledger.md` 回填 27 条 + 新开项；G20 变异自证（从台账删一条 ⇒ 判红）；`arch-guard.sh` 输出含 `VACUOUS=n`，且 `VACUOUS_BASELINE` 只许减（与 G14/G16 同款棘轮 —— 否则"装在没有火的房间里的烟感"会从 1 条变成 N 条）。
**C 流**：导入通道 + 手动压缩的真机三态；旧仓 UI 按 §5 三档执行，搬了的页面各自带一条"数据源非空"断言。
**D 流**：矩阵表逐格填三态，`⛔` 格必须写阻塞物（不许留空）。

**最容易做错的一条（本方案的核心警告）**：
> **"判定点下沉 + 复用同一个 `TokenEstimator` + 用同一个 `TokenEstimator` 写单测" —— 这一套会全绿，而真机上的 33.8K 一次都不会被拦住。**

原因在 §2.1：本例的估算偏差（4 字符/token vs 实测 ≈2.0）**恰好等于**要拦的那段增量，所以下沉后的判定看到的仍是"没超预算"。而用它自己的估算器断言"没超预算"，是**同义反复**，不是判据。**唯一的解是把判据换量纲（§2.3 的 L3 字符硬上限）并让单测断言字符与占位事实（§2.6 T1/T2）。**

**第二易错**：把"压缩器无损放弃"（`ContextBudget.kt:165-169`）的 WARN 当成"没有压缩逻辑"。它今天就在日志里，只是没人 grep `not compressible`。W6 必须把这条 WARN 改成可机读的计数（`compress_skipped=not_compressible`），否则下一次排障还会误判。

**第三易错**：给 `EndpointProbe` 加"探窗口"功能。那是**故意发超窗请求**（1M ≈ 115 s + 计费），在免费池上还会撞 429。写回只能走"从真实 400 学习"（§2.5b）。

---

## 九、`core-kernel` 触顶的拆分方案（下一波用，本波只登记）

**现状**：1 790 / 1 800（`arch-guard.sh:135`），余量 **10 行**。构成：`context/` 1 387 + `policy/` 235 + `log/` 168。

**方案 A（推荐，本波执行）**：冻结 core-kernel 新增 + 台账登记。W6 剩余改动一律不往 core-kernel 加行；拆分写成带 ID 的台账项，下一波第一件事。理由：拆分要同动 G9 `MODULES` + `LINE_CAPS` 两处基线、并额外确认 `TOTAL_LINE_CAP` 不动，与"先真机、再推仓 + CI"的顺序冲突；而登记能防住 W3-05 那种"被挤掉四波无人发现"。

**方案 B（拆分，下一波执行）**：`core-kernel/context/` 1 387 行整体下沉为 `:core-context`。

**先把账算清（初稿的 1 850 与"两模块之和 ≤ 1 800"都不成立）**：

| 分法 | core-kernel 实/上限 | core-context 实/上限 | 上限之和 |
|---|---|---|---|
| log 留在 kernel | 403 / **450**（余 47） | 1 387 / **1 450**（余 63） | **1 900** |
| log 随 context 走 | 235 / **300**（余 65） | 1 555 / **1 600**（余 45） | **1 900** |
| 强行凑 1 800 | 403 / 450 | 1 387 / **1 400**（余 **13**） | 1 850 |
| 强行凑 1 800（另一分法） | 235 / 300 | 1 555 / **1 500** | ✗ 实际已超上限 |

⇒ 两种合理分法都是 **1 900**；能凑到 1 800 的那个分法只剩 **13 行**余量，**等于把死结从一处搬到另一处**（正是本节要避免的形态）。

**真正的判据是 `TOTAL_LINE_CAP`，不是"per-module 上限之和"**：全仓 main 实测 **34 983** / 上限 **42 500**（`arch-guard.sh:156`），**余量 7 517**。W5 铁律 3 的实质内容是"**不要抬高 `TOTAL_LINE_CAP`**"，而不是"per-module 上限之和必须不增" —— 后者在模块数增加时数学上不可能（每个模块至少要有几十行头部余量）。所以下一波拆分的正确写法：

> ⚠️ **这个数必须带口径，否则会算错一倍**（2026-11-02 实测）：守卫 G10 的口径是
> `find <mod>/src/main -type f -name '*.kt'` **逐文件** `wc -l < "$f"` 再累加（`arch-guard.sh:700-717`），
> **不是** `xargs wc -l`。本树用 `xargs wc -l` 会被切成 **2 批**，输出 **2 个 `total` 行**
> （17 074 + 17 909）；`| tail -1` 只取到 **17 909** —— 这就是"全仓 18 473"那个数的来源（漏了第一批）。
> 复算必须用两种独立方式对齐：逐文件累加 = **34 983**，`find … -print0 | xargs -0 cat | wc -l` = **34 983**。

- `TOTAL_LINE_CAP` **保持 42 500 不变**；
- per-module 上限之和 1 800 → 1 900，**+100 是拆分开销**（两个模块各自的文件头 + 各自的 KDoc），
  在台账项与提交信息里写明；
- **不许**用"凑 1 800"的分法（余量 13 行 = 立刻复现同一个死结）。

**方向合法性**：`core-context → core-kernel`（context 依赖 policy/log，不反向），无环。W5 只搬 `context/` 时编译红，是因为当时 `policy`/`log` 还在 `core-agent`；现在它们已在 kernel 里，同样的搬法这次能过。
**二次风险**：`ContextCompressor.kt` 已 337 行（单文件上限 400，余 63）；拆分不解决单文件余量，下一波若还要动它，先按"拆不调上限"处理。
