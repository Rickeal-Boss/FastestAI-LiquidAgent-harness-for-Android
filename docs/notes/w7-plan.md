# W7 作战方案 — 上下文分块缓存命中 & 手动/自动上下文压缩

> 编制：方案席 沈思远 ｜ 基线 commit `c67f37d`（W6 收口）｜ 现役树：`harness-v2`（纯 API Android Agent Harness）
> 真机：OPPO PDRM00 / Android 13 / SDK 33 ｜ 包名 `com.rickeal.liquidharness.debug`
> **本文件是 Phase 1 唯一交付物：只写这一个文件，不改任何源码、不动其他文档。**
>
> 证据链纪律（沿用 `AGENTS.md` §二 / 知识库 §5/§6）：
> - 任何处方（含草稿、外审、主理人措辞）落地前先回现役树 grep 核实；引用行号必须回原文 grep + `wc -l` 反查它说的是哪个修订。
> - 「推断」标【推断】，「主理人/开发席转达」标【中转】，「本席实测 grep」标【实测】。三者不许混写。
> - 判定四态：`✅ 通过` / `⚠️ 部分` / `⛔ 不适用` / `⛔ vacuous`（判据没被行使）。「测试全绿」≠「口径被验证」；**「判据写进方案」≠「判据被执行」**。
> - **新增组件必须同时给出「被调用」判据**（W4 事故：`LayeredContextCompressor` 9 条单测全绿但没接进 `ContextBudget`）。
> - 判据的量纲与估算器解耦（W6 铁律 2）：字符硬上限 + 按 provider 真值标定，**不回退** `TokenEstimator` 自证。

---

## 0. 一页结论（裁决速查）

| 议题 | 裁决 | 去向 |
|---|---|---|
| 需求 4「禁止切换更大上下文的模型」歧义 | **解读 A（按「装得下」判定）**；字面「一律禁大」与需求 1「长程直至 1M」冲突，不采纳 | §2 |
| 自动压缩 × 分块缓存冲突 | **裁 (a)**：正常运行结束只是**触发时点**，是否真压由体积阈值 + `minSavings` 收益阈值决定；**✅ 2026-11-04 用户已确认采纳 (a)**，(b) 降级档保留未选用 | §3 |
| 缓存命中率口径（事实 A） | **不改现役实现**；草稿「无 UI/指标」过时，降级为文档口径订正；三条既有用例保持绿 | §4 |
| 两种「自动压缩」 | **复用 `compressContext` 单开关**（不新增第二个）；关掉 ⇒ 内核预算压缩与会话级收尾压缩**都停**，手动压缩不受约束 | §3.3.1 |
| 切换守卫 chars/token 二档 | **无真值用 floor 1.8（保守）、有真值用标定值**（clamp `[1.8, 6.0]`），直接调 `effectiveCharsPerToken`，**不重写算式** | §2.3 |
| 995k / 1M 档结论 | 本波**一次真实通过**（`ptok=994620 / ctok=187 / finish=stop / hit=true`），但 W6 同题面同参数 vacuous ⇒ **重放 ≥2 次才写稳定结论**；不写「新能力」 | §5/§6/§9 |
| 音频输出（TTS） | **⛔ 不适用**（设备仅 iflytek + NV 85 模型 0 音频类 + `/v1/audio/speech` 404） | §5/§6 |
| MCP 真机联调 | **⛔ 平台阻塞**（`usesCleartextTraffic=false` 不放开）；用户需求 2 的这一面本波不可真机行使 | §5/§6/§9 |
| `:core-context` 拆分 | **✅ S0 已完成（2026-11-04 主理人单人串行跑完四闸门全绿）**：`assembleDebug` BUILD SUCCESSFUL 1m4s/270 tasks；`fulltest` 666/0/0/0、覆盖 14 模块、XML mtime 全 19:37；`arch-guard` OK=25 VACUOUS=1 VIOLATION=0（G9 `include=14==14==14`）；`selftest` 28/28 PASS 178s。`TOTAL_LINE_CAP=42500` 不动 | §1.1/§7/§10 |
| W7-01 前置 | **先给 `nv1` 录密钥 + 切 `active_endpoint_id` + 连接自证**，不是「换 NV 端点即可」 | §6.1 |
| 开波顺序 | 本地 JVM/UI + adb → 真机 → 才推 main + CI；**纯文档提交 0 run 不是绿** | §10 |

---

## 1. 现役树核实结果（证伪优先）

### 1.1 基线与拆分时态【实测】

- `git log -1` = `c67f37d docs(w6): 台账纪律 6/7 + W7-05/06 承接；G20 增 ID 唯一性检查`；本席开查时 `git status --short` 只有 `?? docs/notes/w7-context-cache-roadmap.md`（未跟踪的 W7 草稿），与派单一致。
- **核查期间 CI 席（任务 #2）的拆分已落盘为工作树中间态**【实测，第二次 `git status`】：`core-kernel/.../context/*.kt` 显示 `D`（6 个 main + 6 个 test），`?? core-context/`，`M settings.gradle.kts`。
  - 拆分后 `core-kernel/src/main` = `AgentLogStore.kt` 168 + `AgentPolicy.kt` 235 = **403 行**；`core-context/src/main` 六个文件 = 87+337+140+314+217+292 = **1387 行**；包名仍是 `com.rickeal.liquidharness.core.agent.context`（**只换模块不换包**，import 全不变）。
  - `settings.gradle.kts` 现为 **14 模块**，`include(":core-context")` 注释写明「W7 开波第一件事（关闭挂账 W6-07）」、方向 `core-context → core-kernel` 单向。
  - `scripts/arch-guard.sh`【实测】已同步：`MODULES` 加 `core-context`(:145)、`LINE_CAPS` 加 `"core-context|400|1450"`(:166)、`TOTAL_LINE_CAP=42500` **保持不动**(:189)、G9 依赖边 `core-kernel` 禁依赖 `core-context`(:205)、`MAIN_PATHS` 补 `core-context/src/main`(:300)。
  - ⚠️ 派单说「13 模块 / `core-kernel` 1790/1800 余 10 行」是**开波前**时态；本波任何新增代码前必须以拆分后的 `core-kernel` 403 行 / `core-context` 1387 行为基线，**且以 CI 席最终提交 + 四闸门实跑结果为准**（中间态不等于已收口）。
  - ✅ **S0 已完成（2026-11-04，主理人单人串行跑完四闸门全绿；本席回工作区根日志抽验核实）**：`assembleDebug` `BUILD SUCCESSFUL in 1m 4s / 270 actionable tasks`；`fulltest` `tests=666 failures=0 errors=0 skipped=0`、覆盖模块数 14（`settings include = 14`）、`TEST-*.xml` mtime 19:37:02–19:37:14 全为本轮；`arch-guard` `OK=25 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 报 37 个 handoff ID 全在台账内）；`selftest` `28/28 PASS (178s)`。日志在**工作区根** `_w7/{assemble,fulltest,archguard,selftest}-w7.log`，三个 exit code 全 0。⇒ 方案先前标「进行中」的时态已全部改为完成态（§0/§7.2/§10）。⚠️ 工作树里 `D context/* + ?? core-context/` 是**未提交的中间态（本波约定由主理人统一提交）**，不等于「未验证」—— 这正是 W6 交接 §九「`git status` 只反映工作树 vs HEAD」的同一类读错对象，别拿它推翻已跑过的闸门。
  - 🔴 **本席初稿的一处错误已订正（CI 席指出，本席回树核实，主理人复核实属并采纳）**：初稿写「全量单测/CI 的模块数核对口径要从 13 改 14」——**不成立**。`scripts/fulltest.sh:54-67` 是**动态**从 `settings.gradle.kts` 读 `include(...)` 条目数（`grep -hE '^\s*include\s*\(' | grep -oE '"[^"]+"' | wc -l`），**不写死数字**；脚本注释明写「写死数字是旧仓最典型的陈旧注释来源」，且该值只用于 `::warning::`、不改退出码（模块合法 `NO-SOURCE` 不该判红）。⇒ **没有「13 改 14」这件事**，我照知识库 §5 的旧仓口径推断而未回现役树核，违反了自己写在文件头的纪律，登记在 §1.4-12。真正要实跑的是 `arch-guard.sh` 的 **G9 三方一致**，已由四闸门给出 `include=14==14==14`。
  - ✅ `CJK_SCAN_PATHS`（`arch-guard.sh:311-314`）已由 CI 席补成 `core-kernel` + `core-context` + `feature-tools` + 原有 9 个；他按脚本 :297 自述纪律顺手补齐了 W6 的两处历史漏项，并用 G16 等价逻辑预检（55 / 1 / 16 三处候选行 CJK 命中全 0）⇒ 不会判红、G16 基线与 `VACUOUS_BASELINE` 未动。**本席回树 `grep -n -A6` 核实该改动已落盘**（§7.1 同步更新为「已同步，无需补」）。
  - ✅ **G20 与台账快照合计数无关**（CI 席指出，本席核实 `arch-guard.sh:1295-1299,1365-1373,1399`）：它判的是「handoff ID 全在台账内 + `CLOSED` 行证据非空 + 状态三值 + ID 唯一」，**不判合计数**。⇒ §1.4-3 的 38/39/41 口径分歧**不影响** G20/G21 基线，只影响人读台账时的真值；**主理人 2026-11-04 裁定：以表格实际编号行为准，不阻塞**（§8 前言）。

### 1.2 草稿 `w7-context-cache-roadmap.md` 逐条复核（证伪优先）

| 草稿条目（文件:行 / 结论） | 现役核实【实测】 | 判定 |
|---|---|---|
| §1.1「`WindowContextCompressor.kt` 切点回退工具组边界 + `TURN_ALIGNED`、水位 0.6」 | **无此文件**。类定义在 `core-context/.../context/ContextCompressor.kt:83`；`TURN_ALIGNED` 在同文件 `:33`；`DEFAULT_COMPRESS_TARGET_RATIO=0.6f` 在 `:50`；`safeCutIndex` 在 `:162`。`ManualCompress.kt:158` 与 `ContextBudget.kt:74-76` 都按 `TURN_ALIGNED` 构造 | **结论仍成立，文件名引用过时** |
| §1.1「`ContextBudget.prepare` 全量估算 / `observePrefix` 每请求一行含 `cached=` + 分段指纹 SYSTEM/SKILLS/TOOLS/HISTORY」 | `core-agent/loop/ContextBudget.kt:147 prepare`、`:264 observePrefix`、`:275-280` INFO 账行含 `changed=`+`prompt=`+`cached=`；四段在 `:299-303`，实际段名是 **`TOOLS_SCHEMA`** 不是 `TOOLS`；`:341-348` 明确 `cached=` 是**本 run 累计**、不是本请求（本请求真值在 `GenerationRound` 的 Usage 分支） | **基本成立，两处口径要订正**（段名 / 累计 vs 单请求） |
| §1.1「adapter `~@141-142` 解析 `cached_tokens`、`@108` ReasoningDelta」 | `OpenAiCompatibleAdapter.kt:141` 读 `prompt_tokens_details.cached_tokens`、`:142` `GenerationEvent.Usage(prompt, completion, cached)`、`:106-108` ReasoningDelta | **仍成立，行号精确** |
| §1.2 **I1 跨轮分裂**（「`ChatViewModel.send()`~@278 从 UI 气泡重建 history，工具轨迹不进持久 history，真 bug 级阻断」） | `ChatViewModel.kt:268 fun send()`、`:278 val history = snapshot.bubbles.toHistoryMessages(snapshot.compressedUpTo)` 确实从气泡重建。**但** `ChatHistoryMapper.kt:39-76` 已把 `toolCalls` 与逐条 `TOOL(result)` 一一对应重建进 history（`:54` head，`:75` `listOf(head) + results.map{...}`）；台账 `W26-04`（I1 语义修复）记 `CLOSED` 关闭于 W3，回归 8 条 `ChatHistoryMapperTest.kt` | **已被修 / 描述过时**。草稿查的是旧版树。⚠️ 但「语义修好」≠「跨轮逐字节前缀稳定」：重建时用 `trace.result.orEmpty()`（`:64`）而线上发出的是可能被 `HardBudgetShrinker` 收缩过的 `ToolResult.output`、历史图片不回灌（`:34-37`）、SKIPPED 轨迹不重建调用声明（`:30-31,44`）⇒ 严格字节单调性仍**未被证明**，须靠 W7-02 真机取数，不得直接宣称命中成立 |
| §1.2 **I2 前缀过短**（静态前缀 ≈700–800 token；GLM 门槛 `(2303,2502]` 粒度 64） | 数字来自 `w25-prompt-cache-and-w3-design.md`，本席未逐字复核该历史实测【推断：需 android-developer 在 W7-02 用 NV 侧事实表重测】。现役 `AgentEvent.kt:94-96` 对 `cachedPrompt` 三态有明确口径 | **暂标 ⚠️ 待设备侧复核**；「不为凑缓存注水 system」的决策仍成立（W5 §七） |
| §1.2 **I3 reasoning 回传**（「是否持久化进 history 待核实」） | 链路：`OpenAiCompatibleAdapter.kt:108` → `core-agent/loop/GenerationRound.kt:198-201` 转 `AgentEvent.ReasoningDelta` → `EventReducer.kt:46,103-114` 写 `StreamingState.reasoning` → `ChatUiState.kt:122` 气泡 `reasoning` 字段。**`ChatHistoryMapper.toHistoryMessages()` 全文只读 `text` + `traces`，不读 `reasoning`** | **已核实：仅 UI 折叠块展示，不进线上 history**。对跨轮请求前缀无影响（它不是请求字节）；草稿「待核实」可结案 |
| §2 / §4 **L2 摘要器是否复用主前缀**（标 ⚠️ 待核实，W7-A 定为 P0） | `core-agent/loop/EngineBackedSummarizer.kt:56-63`：请求 = 独立 `SYSTEM_MESSAGE_ID` + `INSTRUCTION`(`:113-120`) + 被压区，`tools = emptyList()`；类头 `:20-22` **硬约束 1** 明确「带工具会回 `tool_calls` 而不是摘要」 | **已核实：不复用主前缀、不带 tools**。草稿 §7 铁律 3「摘要请求必复用主前缀 + 带 tools」与现役硬约束直接冲突 ⇒ **W7-A 本波不做**（§9），理由见 §3.4 |
| §1.1 / §2「`LayeredContextCompressor` 零消费者（W4 事故）」 | 已接线：`ContextBudget.kt:7` import、`:115` `LayeredContextCompressor(delegate = compressor, summarizer = factory(ctx.engine))`；`ContextBudgetTest.kt:254` 明写「它有自己的 9 条单测，但它们全部直接构造它」并补了 `:277/:283` 接线断言 | **已被修**（W4 事故已闭环，是「新增组件给被调用判据」的正面样板，本波照抄这个形态） |
| §2 对标表「命中率一等指标 = ⚠️ 有原始值无 UI/指标」 | 见 §1.3 事实 A：`EventReducer.kt:291 buildUsageLabel` **已上屏** | **描述过时**（主理人自检，本席复核成立） |

### 1.3 主理人三批自检事实复核（逐条给归宿）

| 事实 | 复核结果【实测】 | 归宿 |
|---|---|---|
| **A**：`EventReducer.kt:291 buildUsageLabel` 已算并上屏缓存命中率，`"1.9k in / 50 out (600 cached, 60.0%)"`；三态齐 | `EventReducer.kt:291-303`：分母 `cached * 100.0 / prompt`（`:296`）；`cached==null` → `" (cache n/a)"`(:294)；`prompt<=0&&completion<=0` → `""`(:292)。`EventReducerTest.kt:299/311/328` 是三个测试函数**起始行**（主理人行号准确），实测断言在 `:307`（0.0%）、`:317`（60.0%）、`:333-336`（n/a 且不出现 0.0%/cached） | **成立** ⇒ §4 降级为口径订正，不新增功能、不复制判据 |
| **B1**：`ConversationOps.kt:88` 三条前置拒绝，`compressedUpTo != null` ⇒ 重复压缩静默返回 | `ConversationOps.kt:86-90`：`if (snapshot.isStreaming \|\| snapshot.compressedUpTo != null \|\| snapshot.bubbles.isEmpty()) return`。单指针语义见 `:64-67`、`:73` | **成立** ⇒ §3 冲突裁决的核心约束 |
| **B2**：`ManualCompress.kt:34-40` 明写压缩必令 `cached_tokens` 归零；`ConversationOps` 落 `cache_invalidated=true` | `ManualCompress.kt:34-40`「前缀缓存必然失效（不许写成『不影响』）」；`ConversationOps.kt:109-113` 账行 `manual_compress dropped=N kept=M cache_invalidated=true`；`undoCompress():132` 同样落账 | **成立** ⇒ §3 候选 (b) 的必然代价 |
| **B3 路径矩阵**：`EventReducer.kt:62/64` 的 `Finished` 与 `Cancelled` 共用 `finish(...)`；`ChatViewModel` `finally` 无条件置 `isStreaming=false` | `EventReducer.kt:62` `Finished -> finish(state, text, terminatedBy)`、`:64` `Cancelled -> finish(state, partialText, null)`、`:63` `Failed -> fail(...)`；`ChatViewModel.kt:395-403` `finally` 里无条件 `flushNow` + `isStreaming=false`；`stop():297-301` 走 `activeRunner?.cancel()` | **成立** ⇒ §3.3 事件分流路径矩阵（不能只看 streaming） |
| **C**：`RunTermination.emitFinished` 是 `Finished` 唯一发点；`TurnLoop.kt:82`；内核无跨 run 压缩 API | `RunTermination.kt:27 emitFinished`、`:59 emitMaxRounds`、`:86 emitBreaker`、`:108 emitFailed`、`:128 emitCancelled`；`TurnLoop.kt:80-84` `RoundOutcome.TextOnly → termination.emitFinished(...)`；`ConversationOps.kt:64-67` 自述「`RunState` 每 run 新建，压缩必须在这里做」 | **成立** ⇒ 自动压缩**只能落 UI 侧 `ConversationOps`**，文件域见 §7 |
| **切换路径**：三种形态 ①`activeEndpointId` ②`saveEndpoint` ③`maxContextTokens` | `AppContainer.kt:277 activeEndpoint()`、`:304 saveEndpoint()`、`:380 deleteEndpoint()`、`:256-275 agentRunnerFor()`；`RunnerKey(endpoint, windowCap, compress)` 在 `core-data/RunnerCache.kt:21-32`（**`internal`**，跨模块不可直接引用）；UI 唯一写入点 `SettingsViewModel.kt:118-123 setActive()` → `:120 setActiveEndpointId(id)`；档位 `SettingsViewModel.kt:177-182 setMaxContextTokens()` | **成立** ⇒ §2 守卫必须三形态全覆盖 |
| **生效硬预算口径**：`verifiedContextLength` 优先 + 减 `maxOutputTokens` | `RemoteEndpoint.kt:105 contextLength=32_768`（声明值）、`:106 maxOutputTokens=4_096`、`:127 verifiedContextLength: Int?`；`:124-125` 自述唯一消费点 `min(verifiedContextLength ?: contextLength, policy.maxContextTokens)`；`AppContainer.kt:370-377 recordContextLimit` 写回；`AgentPolicy.kt:190 maxContextTokens=32_000` 为会话级档位 | **成立** ⇒ §2 判定式；⚠️ `contextLength` 裸值不可用于守卫（W5-02 超报：1 048 576 vs 实测 1 000 000） |
| **UI 复用现状**（需求 5） | `core-design` 12439 行已搬（液态玻璃全套，台账 W6-07 记 12439/12600 余 161）；`feature-chat` 顶栏/气泡/工具轨迹/上下文占用条/输入栏在位（`ChatTopBar.kt` 149、`ChatUiState.kt` 230、`EventReducer.kt` 309）；`ChatViewModel.kt` 实测 **484 行**（余 16）；`feature-settings` 2921/3000（余 79） | **成立** ⇒ §7 写「复用 = 已搬 + 本波只补三块」，禁止按「从零搬」排工作量 |
| **设备实测**（开发席 `_w7/`，主理人转达） | 本席在仓库根 `ls _w7/` **无输出**【中转】——原始 `device-baseline.json` 本席未见，以下均按「主理人转达 / 开发席 exec-out 读回」标注，**不写成实测** | §6.1 逐条订正 |

### 1.4 主理人指令中**有错或自相矛盾**之处（按纪律必须直陈）

1. **需求 1 与需求 4 字面直接冲突**：需求 1 要「免费节点多模型全面测试长程不同上下文直至 1M」，现役设备 `max_context_tokens=32000` ⇒ 必须切到 1M 端点或 1M 档位；而需求 4 字面「**禁止切换更大上下文的模型**」会把这条必经路径整个禁掉。两者不能照字面同时成立。本方案裁「按装得下判定」而非字面禁令（§2.1），**这一处建议主理人向用户确认一句**（与 §3 的澄清合并问）。
2. **派单背景沿用了 W6 交接 §七的错误设备状态**：交接写「NV-VL 已录入密钥并设为当前」，开发席 exec-out 读回是 `active_endpoint_id=orvl`(OR-DOTS)、`nv1` `hasSecret=False`、`files/harness/secrets/` 只有 `orvl.bin`【中转】。派单「安全真机，密钥可常驻设备，端点建一次即可复用」的前提对 `orvl` 成立、对 `nv1` **不成立**。方案已改（§6.1）。
3. **派单「挂账 39 条」与台账自身三处口径不一致**【实测】：`docs/06-deferred-ledger.md` §二统计合计 **39**（CLOSED 9 + CARRIED 6 + OPEN 24）；§一末尾注释写「**38 条**而不是外审说的 27 条」；而 §一表格实际有 **41 个编号行**（W26×5 + W3×8 + W4×7 + W5×7 + W6×8 + W7×6），多出的 W7-05/06 是统计快照（标题「W6 建立时」）之后补入的。⇒ 逐条裁决以**表格实际编号行**为准（§8），并提醒 CI 席：动 G20/G21 基线前先核这个计数，别拿快照数字当真值。
4. **历史编号冲突**：`handoff-20261101-wave6.md` §六把「`core-kernel` 1790/1800 拆分」写成 **W6-01**，且口径是「`contextBudgetTokens`/`hardContextBudgetTokens` 移入 `core-agent`，两模块上限和 ≤1900」；而台账 **W6-01 = `compressContext` 零写入点**、**W6-07 = 拆 `:core-context`**（两种分法、+100 拆分开销）。派单用 W6-07 与台账一致（**正确**），但 handoff 那份过时开立记录会误导执行席。台账 §零已声明「handoff 挂账表是开立记录，不再是状态来源」，本方案以台账为准。
5. **事实 A 的「降级为口径订正」不能覆盖整个 W7-C**：草稿 §4 的 W7-C 含「`cacheRead/cacheWrite/fresh` 三桶做成会话级一等指标 + 原始 usage JSON 同屏」。现役 `AgentEvent.UsageUpdated`(`:97`) 只有 `prompt/completion/cachedPrompt` 三值，**没有独立 `cacheWrite` 字段**。⇒ 「命中率标签」确实已存在、只做口径订正；但「三桶归一」仍是新功能（属 W8-03 多厂商 usage 适配）。主理人措辞略有过度概括，方案按此拆开（§4）。
6. **行号核对结论（无冲突，记录备查）**：主理人给的 `EventReducerTest.kt:299/311/328` 与 `EventReducer.kt:62/64/291`、`ConversationOps.kt:88`、`ManualCompress.kt:34-40`、`RunTermination.kt:27`、`TurnLoop.kt:82` 全部回原文核过，**准确**。本席一度以为 `EventReducerTest` 行号有偏差，实测 `:299/311/328` 是三个 `@Test` 函数起始行、断言在 `:307/317/335` —— 是两种合法锚点，不是错误。按「证据链逐字核」纪律记录在此，避免后续误报。
7. **「先真机、才推仓 + CI」与纯文档交付的张力**：本波方案文件是 `docs/notes/*.md`，`build.yml` `push.branches:[main]` + `paths-ignore`（`:48-57`）会挡掉纯文档提交 ⇒ **0 run，不是绿**（AGENTS.md §八 / 知识库 §5）。CI 核验（任务 #9）必须等源码提交一起推，且以 `git push --dry-run` 为真判据。
8. **并行度**：派单「任务繁重，可分派多个子 Agent」与知识库 §6「子 Agent ≤4 路并行（7 路实测集中 429）」需同时遵守；上一轮本席即因 429 零产出。§7 按 ≤4 席切分。
9. **🔴 主理人第 1 批补充的 `max_tokens` 归因已被其本人撤回**（本席按撤回后口径落地）：主理人先写「W7 把 `max_tokens` 从 4096 降到 512 ⇒ 995k 档解除 vacuous、属判据参数修正」，随后自行查证撤回。本席复核【实测】：`docs/notes/w7-provider-matrix.md:45` 明写「本波显式把通道请求 `max_tokens` 提到 **512**（`_w7/w7matrix.py:pb_guarded`），**阶梯用 4096**」，§4.1 标题即 `max_tokens=4096`，§4.1 表格 `~995k` 行 `ptok=994620 / ctok=187 / finish=stop`；§十.4 的「`max_tokens≥512` + 1k 取针自证」是 **channel probe 的判据自证**，与 ladder 无关。⇒ **任何结论都不许出现「`max_tokens` 变化导致 1M 档解除 vacuous」**。这是本仓「判据自身要先被验证 + 主理人措辞逐字核」的一次执行样本，按纪律登记。
10. **主理人补充 2 与我原方案 §3.3 / §7.3 冲突**：我初稿写「必须新增独立开关/字段，不许复用 `compressContext` 同一布尔语义」；主理人倾向**复用单开关**。本席按现役树重推后**改判采纳复用**（§3.3.1）：复用不会让 W6 的 T1–T7 脱节（关开关时 `ContextBudget.prepare` 仍按现役返回全量，只是新增的会话级收尾压缩也读同一布尔），且避免用户面对两个「自动压缩」各自解释。保留的纪律是**两套机制的「被调用」判据必须分别给**。
11. **篇幅 / 数字口径要逐字核**：主理人称 `w7-provider-matrix.md` 248 行，本席 `wc -l` 实测 **314 行**（22 306 B，`git status` 为 `??` 未跟踪，开发席 19:45 落盘 ⇒ 主理人读的是较早版本）。主理人给的「chars/token 5.40–5.42、1k 档 5.223」与文档 §4.1 的 **5.223 → 5.407**、`BudgetFormula.kt:73-75` KDoc 的 **5.73～5.75**（W6 批次）是**三批不同实测**，方案按各自出处标注，不合成一个数。
12. **本席自己的错 + 两处指令/派单冲突（CI 席回报后订正，必须提请主理人裁决）**：
    - **(a) 本席初稿有误**：写「模块数核对口径要从 13 改 14」，实为照知识库 §5 的旧仓口径推断、未回现役树核。`scripts/fulltest.sh:54-67` **动态**读 include 条目、不写死数字 ⇒ **没有这件事**（详见 §1.1）。这正是本方案文件头「任何处方落地前先回现役树 grep 核实」纪律的反例，如实登记。
    - **(b) 时态冲突 —— 已闭合**：主理人派单要「假定 CI 席已完成」，CI 席当时回报磁盘是中间态、四闸门未跑，本席据此把 S0 标为进行中。**2026-11-04 主理人已单人串行跑完四闸门全绿并给出原始数字**（§1.1），S0 已完成 ⇒ 方案 §0/§7.2/§10 时态已全部改回完成态。保留本条是为记录「以磁盘/实测为准」在当时的正确性，不是待办。
    - **(c) 文件域冲突 —— 已由主理人裁决（2026-11-04），不再待裁**：台账 §三.6「只由 CI 流落笔」成立 ⇒ **CI 席只改台账 W6-01 那一行**（状态 `CLOSED` + `文件:行` 证据链），**不改任何业务源码**；`SettingsViewModel.kt:189` 过时 KDoc 是可选顺手项、归 UI 席、不是关闭前提。本席 §8「订正台账，不是修代码」的结论被采纳并据此派单 ⇒ **不存在「无人有文件域」**。原判断（CI 席派单只含 W6-07 行）是当时的真实约束，现已通过主理人裁决解决。

### 1.5 主理人第 2–4 批补充的复核与归宿

| 补充 | 本席复核 | 归宿 |
|---|---|---|
| **995k 档真通过**（`ptok=994620 / ctok=187 / finish=stop / hit=true / content="MAGENTA-7731" / 110.57s`） | 已落盘一手文档 `docs/notes/w7-provider-matrix.md:95-111` 实测读到，§4.1 标题 `max_tokens=4096`、表格行 `chars=5 378 151`。**W6 同档 `completion=4096` 全被 reasoning 吃光 ⇒ `finish=length` / content 空 ⇒ `⛔vacuous`**。文档 :109-111 自述「该档位结论依赖单次观测，不可作为产品承诺」 | §5 P5、§6.3、§9 风险 10、§10 S4 |
| **撤回 `max_tokens` 归因** | 见 §1.4-9；**正确归因 = 输出侧 reasoning 消耗的单次差异**（4096 vs 187），既不是模型能力变了，也不是判据参数变了 | §1.4-9、§6.3、§9 |
| **阶梯实测**：nv-vl 1k/8k/32k/128k/256k/512k/995k 全 ✅；dots 1k→256k ✅、512k/995k 被 OR 网关 400 拒；north 只到 32k | 文档 §4.1/§4.2 表格核实（nv-vl 7 档含耗时 1.01→110.57 s；dots 512k/995k 400）。OR 400 错误串 `maximum context length is 512000 tokens` 且**建议用 context-compression 插件压缩** ⇒ 这是本波自动压缩的**现实用例**，写进 §3.1 动机 | §3.1、§6.3 |
| **chars/token 二档**（floor 1.8 / ceiling 6.0，散文真值 5.4–6.5 ⇒ 有真值时 cap 放宽） | `core-context/.../BudgetFormula.kt:59-60 hardCharCap` 只在无真值生效；`:63-64 charCapOf`；`:83-87 effectiveCharsPerToken(policy, measured)` = `measured<=0 → floor` 否则 `measured.coerceIn(floor, ceiling)`；`:31-36 hardContextBudgetTokens` 还有「`window-maxOutputTokens<=0 ⇒ window/2`」+ `coerceAtLeast(1)` 兜底 | §2.3（判定式与二档判据） |
| **两种自动压缩必须区分** | `SettingsRepository.kt:69-97`（默认 true，debug 设置页，「**本开关只影响自动压缩，手动点压缩不受它约束**」）；`ChatUiState.kt:201-210 compressedUpTo` 单指针；`ChatUiState.kt:181-186` 现成 `contextTokens/contextLimit/usageLabel` | §3.3.1 新节、§7.3、§8 W6-01 |
| **台账 W6-01 与 handoff §2.3 C 是否矛盾** | **矛盾，且台账过时**：`SettingsRepository.kt:70-72` 自述「它此前是 `AgentPolicy.compressContext` 里零写入点的声明……**接上它同时清掉这条挂账**」；现役全链已接（§8）。⇒ 以现役树为准，**W6 已解决，台账需回写 `CLOSED`**（台账只由 CI 流落笔，本席只给结论） | §8 W6-01 |
| **缓存三桶同屏优先扩 `usageLabel`** | `ChatUiState.kt:181-186` 三字段已在，`feature-chat` 只剩 41 行余量 ⇒ 不加新状态字段 | §7.3 |

---

## 2. 需求歧义裁决：模型切换守卫（需求 4）

### 2.1 两种解读与推荐

用户原话：「前面如果使用的模型更换上下文大的模型上下文塞不下即禁止切换更大上下文的模型」。

| 解读 | 含义 | 与需求 1 的关系 | 取舍 |
|---|---|---|---|
| **A（推荐）· 容量判据** | 「禁止切换」的触发条件是**目标模型装不下当前会话已物化的上下文**：`当前会话上下文 > 目标生效硬预算` ⇒ 禁止。原话的「上下文塞不下」是判据，「更大上下文的模型」是口语误写（大模型恰恰更装得下） | 兼容：切到 1M 大模型能装下 ⇒ 放行 | ✅ 采纳 |
| **B · 字面禁令** | 只要目标模型窗口 > 当前模型窗口就一律禁止（防「切换导致缓存重建/费用变化/历史被压缩后误以为还在」） | **冲突**：从 32K 档/OR-DOTS 切到 NV 1M 是 1M 行使的必经路径，一律禁掉则需求 1 不可执行 | ⛔ 不采纳 |
| C · 压缩态禁令【推断】 | 会话已经发生过压缩（历史被砍头），禁止切到大窗口模型，避免「窗口看似变大、内容其实已丢」的假象 | 会把 W7-01/W7-03 之后的 1M 测试卡住 | ⛔ 不采纳为硬禁令，降为切换时的显式提示 |

✅ **用户裁决已回填（2026-11-04，主理人转达）**：用户给出自定义示例并裁明语义 ——「实际上下文 **320k**，目标模型只有 **256k** ⇒ **直接禁止切换**（超过部分除非上下文能够压缩至范围内）」。⇒ **确认采用解读 A 容量判据**；解读 B / C 仍不采纳。三点随之定案：① 320k > 256k ⇒ 硬禁止；② **切到更大窗口模型、装得下则放行**（1M 长程测试路径保留，需求 1 可执行）；③ **「先压缩到目标窗口范围内再切」是合法恢复路径**（写进 §2.4，压缩后重跑判定式）。这条同时把「压缩」从纯 UI 动作升格为切换守卫的一等恢复手段，判据见 §2.3 / §2.4。

**推荐 A，保守优先体现在三处**（不是「多放行」而是「宁可判红、宁可显式」）：
1. **硬禁止从严**：用 `verifiedContextLength` 优先、再减 `maxOutputTokens` 的**生效硬预算**，且与 L3 **字符**硬预算对账；不用 `contextLength` 裸值（W5-02 超报教训），不另起 `TokenEstimator.estimateMessages` 口径（W5 33.8K 事故「用同一个估算器证明自己没超」的假绿形态）。
2. **`verified == null` 不放行**：没有 400 学到的真实窗口时，判据标 `⛔vacuous`，走「显式二次确认」而不是硬放行 —— 声明值不能当「装得下」的证据。
3. **切到大窗口也不静默**：任何切换都使 `RunnerKey` 变化 ⇒ runner 重建 ⇒ 前缀缓存整段失效，UI 必须显式提示「切换后缓存从头重建、下一轮 `cached_tokens=0`」，承接用户对切换代价的担忧（对应解读 C 的合理内核）。

### 2.2 现役真实行为（别凭想象）【实测】

- `saveEndpoint()`（`AppContainer.kt:304-345`）：先 `UrlGuard.requireAllowed` → 写密钥 → `endpoints.upsert(normalized.copy(hasSecret=...))` → `runners.removeIfIdle(normalized.id){ it.isBusy }` → 首次保存自动 `setActiveEndpointId`。**它不读、也不记录当前会话上下文长度**，对「塞不塞得下」零感知。
- `activeEndpoint()`(`:277-280`)：`settings.activeEndpointId.first()` → `endpoints.get(id)`。切换活跃端点的 **UI 唯一写入点** = `SettingsViewModel.kt:118-123`。
- `RunnerKey`（`core-data/RunnerCache.kt:21-32`，`internal data class`）：`endpoint`（**整个** data class）+ `maxContextTokens` + `compressContext`。三者任一变 ⇒ `getOrBuild` 重建（`:68-77`）。⚠️ `internal` 按模块隔离（知识库 §3）⇒ 守卫策略类**不能**直接引用 `RunnerKey`，跨模块要抽 public 或只用 `core-model` 的 `RemoteEndpoint`。
- `maxContextTokens`：**会话级档位**（`SettingsRepository.kt:58-63`，四档 32K/128K/512K/1M，默认 32 000），不是端点属性；`agentRunnerFor`(:257) 每次现读。
- `verifiedContextLength`：**端点级** `Int?`（`RemoteEndpoint.kt:127`），由 `GenerationEvent.Failed.contextLimitHint` → `ChatViewModel.kt:389-391` → `ConversationOps.offerContextLimit` → `ContextLimitWriteback` → `AppContainer.recordContextLimit`(:370) 四跳写回；唯一消费点是 `hardContextBudgetTokens` 的 `min(verified ?: contextLength, policy.maxContextTokens)`。
- 三种切换形态：**①** `setActiveEndpointId`（`SettingsViewModel:120`）；**②** `saveEndpoint` 改 `modelId`/`contextLength`/`maxOutputTokens`（`SettingsViewModel:92` → `AppContainer:304`）；**③** `setMaxContextTokens`（`SettingsViewModel:179`）。**只做 ① 会留两条绕路**。

### 2.3 可红可绿判据（写成能跑的形态）

**落点（纯 JVM 可测策略类）**：`:core-context` 新增 `SwitchGuard`（或 `core-agent/context/SwitchGuard.kt`，二选一由内核席定；`core-context` 已被 CI 席拆出且不依赖 `core-data`/UI，最干净）。签名建议：

```kotlin
// 只依赖 core-model 的 RemoteEndpoint/AgentPolicy + :core-context 的现役算式，零 Android/data/feature 依赖
data class SwitchVerdict(val allowed: Boolean, val reason: String, val state: VerdictState)
fun evaluateSwitch(
    committedChars: Int,            // UI 侧按 toHistoryMessages(compressedUpTo) 重建后逐条求和（字符）
    target: RemoteEndpoint,         // 目标端点（或切档位后的同一端点）
    maxContextTokens: Int,          // 切换后的会话档位
    measuredCharsPerToken: Float,   // 本会话的 provider 真值；<=0 = 无真值（回落 floor）
): SwitchVerdict
```

判定式（**直接调用现役算式，不重写、不另起口径** —— 重写就是第二个真值源）：
- `policy = AgentPolicy(maxContextTokens = maxContextTokens)`
- `hardBudgetTokens = hardContextBudgetTokens(target, policy)`（`BudgetFormula.kt:31-36`：已含 `verified ?: contextLength`、`min(.., maxContextTokens)`、**减 `maxOutputTokens`**、`<=0 时按 window/2` 兜底、`coerceAtLeast(1)`）
- `cpt = effectiveCharsPerToken(policy, measuredCharsPerToken)`（`:83-87`）
- `targetCharCap = charCapOf(hardBudgetTokens, cpt)`（`:63-64`）

🔴 **chars/token 二档必须明确**（主理人补充 1 的要求，含糊带过即返工）：

| 档 | 触发条件 | 取值 | 理由 |
|---|---|---|---|
| **保守档（floor）** | `measuredCharsPerToken <= 0`（首轮 / 刚压缩过 / 端点从未回报 usage ⇒ 本 run 无真值） | `policy.hardCharsPerToken` = **1.8** | 无真值时 cap 最小 ⇒ 宁可判红禁止，不冒进放行（W5 33.8K 事故同源） |
| **标定档** | `measuredCharsPerToken > 0` | `measured.coerceIn(1.8, 6.0)`（散文真值 5.2–6.5 ⇒ cap 放宽） | 内容相关偏差已按 provider 真值标定，用 floor 会把本可放行的 1M 切换禁掉 |
| 反向约束 | `measured` 偏大时由 `maxCalibratedCharsPerToken=6.0` 兜住（`BudgetFormula.kt:79-81`：一次异常回报不许把兜底关掉） | — | 方向安全 |

⚠️ `measured` 现役是 **per-run**（`RunState.measuredCharsPerToken`，`BudgetFormula.kt:79-81`），而切换发生在设置页、通常没有在途 run ⇒ **守卫大概率走保守档**【推断】。这是安全方向，但会让「装得下」偏严；本波**不**新增会话级持久化标定值（避免动 `AppContainer`/`RunState`），只在日志里标 `switch_guard=char_floor(unmeasured)`，让偏严可诊断。若后续要放宽，须另开挂账并给「被调用」判据。

| 态 | 触发条件（可执行） | 语义 |
|---|---|---|
| ✅ 绿（放行） | `committedChars <= targetCharCap` **且** `bubbles.isNotEmpty()` **且** `verifiedContextLength != null` | 判定式真的被行使过 |
| ⛔ 红（禁止） | `committedChars > targetCharCap` | 该目标项置灰，且**不写** `setActiveEndpointId` / 不 `saveEndpoint` / 不改档位 |
| `⛔vacuous` | `bubbles.isEmpty()`（空命中） | 无历史可溢出 ⇒ 判据不触发，**不得记为通过**；放行但日志记 `switch_guard=vacuous` |
| `⛔vacuous` | `target.verifiedContextLength == null` | 声明值不可作「装得下」证据 ⇒ 不放行硬绿，转「显式二次确认」通道，日志记 `switch_guard=unverified_window` |
| `⛔vacuous` | `hardContextBudgetTokens` 触发了 `window/2` 兜底（`window - maxOutputTokens <= 0`） | 预算已被兜底改写，不是正常判定 ⇒ 标 vacuous 并给具体原因，不静默。**注**：初稿写的「`hardBudgetTokens<=0` 判红」**不可达**（现役 `coerceAtLeast(1)` 保证恒正），已按现役树订正 —— 又是「判据构造不可达」的一例 |

**二档的可红可绿判据（必须与 `SwitchGuardTest` 同一次提交）**：
- 绿：同一 `committedChars`，无真值 ⇒ 按 1.8 算 cap；有真值 `5.407` ⇒ 按 5.407 算 cap（后者 cap 更大）。
- **反例锚 A（危险方向）**：把 `effectiveCharsPerToken` 换成恒用 ceiling 6.0（或忽略 `measured<=0`）⇒ 无真值用例「本该禁止」被放行 ⇒ **必红**。
- **反例锚 B**：换成恒用 floor（忽略 measured）⇒ 有真值用例「本该放行」被禁 ⇒ **必红**（这条同时防住「切 1M 被误禁」与需求 1 冲突）。
- 保留原锚：换回 `contextLength` 裸值 ⇒「1M 声明 + 256k 实测」用例必红。
- 直接调现役三个函数的形状断言：若有人把算式抄成 `window - maxOutputTokens` 而丢掉 `window/2` 兜底 ⇒ 构造 `maxOutputTokens > window` 的用例必红。

**「被调用」判据（W4 铁律，必须与策略类同一次提交）**：
1. JVM：在 `SettingsViewModel` 三个写入点各注入一个 `SwitchGuard` 调用的形状断言 —— 用 `core-context` 侧一条「变异自证」：把任一写入点绕过 guard 直接写盘 ⇒ 守卫扫描（新增一条 arch-guard 判据，钉**赋值形态**而非裸符号，AGENTS.md §七）报红；`scripts/arch-guard-selftest` 同步新增 case（知识库 §5「每新增守卫必同步 selftest case」）。
2. JVM：`SwitchGuardTest` 至少 5 条：装得下放行 / 装不下禁止 / 空会话 vacuous / verified=null vacuous / `window/2` 兜底触发（构造 `maxOutputTokens > window`）⇒ ⛔vacuous —— `hard<=0` 因现役 `coerceAtLeast(1)` 恒正是**不可达构造**，不许写成判红；反例锚「抄算式丢兜底必红」。（落地实况：`SwitchGuard.kt` 用 `maxOutputTokens=0` 影子端点取 window，且 `committedChars > cap` 的 BLOCKED 排在兜底分支之前，`blockedTakesPrecedenceOverUnverifiedInCrossState` 与 `overflowStillBlocksWhenWindowHalfFallbackWouldApply` 两条钉住。）；**反例锚**：把判定式换回 `contextLength` 裸值 ⇒ 「1M 声明 + 256k 实测」用例必红。
3. 真机：切换被拒时 `settings.preferences_pb` 的 `active_endpoint_id` 读回**未变**（`adb exec-out` + `run-as`，禁止用 `adb shell >` 读二进制），且 UI 显示原因串（§6.4）。
4. ⚠️ **WARN 判据可能有闩，0 命中 ≠ 通过**（知识库 §4）：守卫若以「日志里没出现 `switch_guard_blocked`」判通过，必须同时证明该日志路径本轮被走过（构造一次必然被拒的切换并看到该行）。

### 2.4 UI 表达与恢复路径

- **禁止态**：目标模型/档位在列表中**置灰 + 原因文案**（「当前会话 N 字符 > 该模型可用窗口 M 字符」），不是点了再弹 toast；N/M 与缓存三桶同屏展示（口径同 `EventReducer.buildUsageLabel`）。文案走 `strings.xml`（守卫 G16，main 源集禁中文字面量）。
- **切换大窗口的显式确认**：即使放行，也弹二次确认，明写「切换会使前缀缓存从头重建、下一轮 `cached_tokens=0`、费用更高」（复用 W6 手动压缩对话框的同一句措辞，不新造语义）。
- **恢复三条**（写进对话框，用户不必猜）：① **新对话**（`newConversation()` 清 `bubbles` 与 `compressedUpTo`，`committedChars=0`）；② **先压缩到目标窗口范围内再切**（用户 2026-11-04 裁明的合法恢复路径，对应「320k 上下文 / 256k 目标模型」示例）：手动压缩降 `committedChars` 后，**必须用同一个 `evaluateSwitch` 重新跑判定式 —— 同输入（`target` / `maxContextTokens` / `measuredCharsPerToken`）、同输出（绿/红/vacuous 三态与原因串）**，只允许 `committedChars` 变小；压缩会落 `cache_invalidated=true`，是明确代价不是缺陷。判据：压缩前判红、压缩后同参数判绿，且两次判定式调用可在日志/单测中逐对核对；若压缩后仍红 ⇒ 继续走 ① 或 ③，不许绕过守卫硬切；③ **换更大窗口端点 / 调高档位**（重新跑判定式）。
- **空命中语义**：新对话后 0 气泡 ⇒ 放行但标 vacuous，日志可查，不伪装成「守卫验证通过」。

---

## 3. 自动压缩 × 分块缓存命中：冲突裁决

### 3.1 冲突的硬事实（不复述为推断）

1. `ConversationOps.kt:88`：`compressedUpTo != null` ⇒ 重复压缩**静默返回**。单指针 ⇒ 「每轮结束都压」从第 2 轮起是空操作。
2. `ManualCompress.kt:34-40` + `ConversationOps.kt:78-81,109-113`：压缩丢最老一段 ⇒ 前缀从第一字节变化 ⇒ 下一次 `cached_tokens=0`、账单更贵，每次成功都落 `cache_invalidated=true`。
3. `ContextBudget.kt:303-304`：`HISTORY` 段指纹取**头部 K 条**（取尾部会让每次正常追加都误报）⇒ 砍头压缩必然报 `changed=HISTORY`。
4. ⇒「无条件每轮压」与「分块缓存命中」在现役结构下**不能同时成立**：前者让后者恒 0 命中。
5. **本波压缩的现实动机（新实测，非纸上需求）**：`docs/notes/w7-provider-matrix.md:145-149` 记录 OR 网关对 `dots` 512k / 995k 档返回 **400**，错误串 `maximum context length is 512000 tokens` 并**建议改用 context-compression 插件压缩**。⇒ 长上下文下免费池确实会因超窗被拒，压缩是把它跑通的现实通道。⚠️ 但这属于**反应式 / 溢出压缩**（草稿 W8-05），与本波 (a) 的「正常结束才压」触发时机不同 ⇒ 本波**不**顺带实现溢出触发，只把这条证据写进动机与 W8-05 的移交依据，别把两者混成同一个功能。

### 3.2 三候选对比与裁决

| 候选 | 做法 | 优点 | 缺点 | 设备/架构约束 | 裁决 |
|---|---|---|---|---|---|
| **(a)** | 「正常运行结束」只作**触发时点**；是否真压由体积阈值 + `minSavings` 收益阈值决定，只有 `applied=true` 才失效缓存 | 保住未压轮次的严格前缀 ⇒ 分块缓存命中路径可行；贴合用户「告一段落才处理、不在轮中打断」的时点语义 | **实质偏离用户「就压缩」原话**（用户要无条件，这里是有条件） | 改动集中 UI 侧 `ConversationOps` + `ChatViewModel.collect`，`ChatViewModel` 484/500 余 16 行 ⇒ 逻辑必须外提 | ✅ **采纳** |
| **(b)** | 每轮无条件压 | 字面最贴合 | 缓存命中率**恒为 0**；第 2 轮起因单指针变空操作；账单每次重建 | 需改单指针为可重复压（等于重写会话模型） | ⛔ 不采纳（除非用户明确接受「缓存恒 0」） |
| **(c)** | 改尾部追加摘要 / 不改写已缓存前缀（草稿 §7 铁律 1） | 理论上两全 | 要动 `core-context` 压缩器（砍头→尾部引用）、`ChatHistoryMapper` 重建、`ContextBudget.prepare` 指纹、`ConversationOps` 单指针→范围/事件溯源；且 `EngineBackedSummarizer` 的独立 system + 无 tools 语义要重设计（§1.2） | `feature-chat` 余 41 行、`ChatViewModel` 余 16 行、`feature-settings` 余 79 行 ⇒ **本波做不起** | ⛔ 本波不做，列 W8-01 |

**裁 (a) 的理由**：① 用户给了明确的**时点**语义（「正常运行结束、告一段落」「不是水位线、不是轮头」）—— (a) 完整保留；② 用户对「真压频率」的表述是口语，而同一次需求里又要求「上下文分块缓存命中」—— 只有 (a) 能让两者都非空转；③ 保守优先：把「压不压」交给**可红可绿的收益判据**，而不是交给一次不可撤销的前缀破坏。

> ✅ **澄清已完成（2026-11-04，用户裁决，由主理人回填）**：用户**明确接受 (a)** ——「每次正常运行结束**评估一次**，达到体积 + `minSavings` 收益阈值才真压」。⇒ (a) 为**本波确定实现**，不再作为待确认项；(b)「每轮无条件压」作为可切换的显式降级档**保留但未被选用**，仅当用户改主意时才启用（届时须同步把「缓存命中率恒为 0」写进风险与交接）。第三句亦已回填：关掉自动压缩会**同时停两处**（内核预算压缩 + 会话级收尾压缩）、**手动压缩不受影响**（§3.3.1 / P14）。知识库 §6「记忆 ≠ 交付物」：本节即用户裁决的落盘处，不留在聊天里。

### 3.3 路径矩阵（W84 铁律：改事件序列先枚举路径矩阵）

`ChatViewModel.runOnce` 的 `collect`（`:384-394`）与 `finally`（`:395-403`）：

| 终态事件 | `EventReducer` 行为 | `finally` 行为 | 自动压缩是否触发 | 依据 |
|---|---|---|---|---|
| `AgentEvent.Finished(terminatedBy = ModelStopped)` | `finish(...)`(`:62`) | `isStreaming=false`（无条件） | ✅ **触发评估** | 唯一「正常运行结束」 |
| `AgentEvent.Finished(terminatedBy = MaxRounds / BreakerTripped / GateRejected / Interrupted)` | `finish(...)`(`:62`)，共用 | 同上 | ⛔ 不触发 | `AgentEvent.kt:6-27` 五种 `TerminationReason`，只有 `ModelStopped` 是「模型自己停了」；熔断/轮次耗尽不是正常告一段落 |
| `AgentEvent.Failed` | `fail(...)`(`:63`) | 同上 | ⛔ 不触发 | 且 `contextLimitHint` 走 `offerContextLimit` 写回（`:389-391`），不可与压缩串台 |
| `AgentEvent.Cancelled` | `finish(state, partialText, null)`(`:64`)，**与 Finished 共用 `finish`** | 同上 | ⛔ 不触发 | `stop():297-301` / `newConversation():250` 走此路 |
| 收集被取消、没走到任何终态事件 | — | 无条件 `flushNow` + `isStreaming=false` | ⛔ 不触发 | `finally` 是「兜底」，不是终态 |

**实现处方**：自动压缩**只能挂在 `collect` 内部的事件类型分流上**（`if (event is AgentEvent.Finished && event.terminatedBy == TerminationReason.ModelStopped)` 才调 `ops.autoCompactOnSettled(...)`），**绝不能挂 `finally`、不能只看 `isStreaming` 归零**。内核正常收尾唯一出口是 `RunTermination.emitFinished`(`RunTermination.kt:27`)，其余 `emitMaxRounds`(:59)/`emitBreaker`(:86)/`emitFailed`(:108)/`emitCancelled`(:128) 是另四条；内核 `RunState` 每 run 新建、不暴露跨 run 压缩 API ⇒ **落点只能是 UI 侧 `ConversationOps`**（§7 文件域）。

### 3.3.1 两种「自动压缩」的分层与单开关语义（主理人补充 2 的归宿）

🔴 **别把它们接成同一个机制**。现役已有一套、本波要加的是另一套：

| 维度 | ① 内核自动压缩（**现役**） | ② 会话级自动压缩（**本波新增**） |
|---|---|---|
| 开关 | `AgentPolicy.compressContext` / `SettingsRepository.compressContext`（`:69-97`，默认 `true`，debug 设置页「自动压缩上下文（调试）」） | 待加，落 `ConversationOps` |
| 压的对象 | `ContextBudget.prepare` 按软预算压缩**单次请求的工作副本** | **跨 run 的会话历史**：UI 气泡 → `ChatUiState.compressedUpTo`（`ChatUiState.kt:201-210` 单指针） |
| 触发时机 | 软预算 / 水位线，轮头 pre-step（**正是用户说「不要」的那种**） | `Finished(ModelStopped)` 收尾 + 体积/收益阈值（§3.4） |
| 生命周期 | `RunState` 每 run 新建，产物**不落 UI 气泡** | 落 UI state 指针，跨 run 持续 |
| 失效面 | 切点之后前缀失效（单次） | 整段前缀重建、落 `cache_invalidated=true` |
| W7-06②「关掉压缩后请求接近窗口」指 | ✅ 就是这一套（关后 `prepare` 直接返回全量、无任何可观测输出） | ❌ 不是 |

**开关裁决：复用 `compressContext` 单开关，不新增第二个布尔**（主理人倾向，本席按现役树复核后采纳 —— 见 §1.4-10）：
- `true` ⇒ 两套各自按**自己的**触发条件行使（内核按软预算、会话级按正常结束+收益阈值），互不替代。
- `false` ⇒ **两套都停**：内核不压、会话级不评估；**手动压缩仍可用**（用户显式动作）。这是 `SettingsRepository.kt:91-92` 明文语义「本开关只影响自动压缩，用户手动点『压缩上下文』不受它约束」，复用时**必须保留**，不许顺手把手动压缩也禁掉。
- `RunnerKey` 已含 `compressContext`（`RunnerCache.kt:31`），开关翻转会重建 runner（现役行为），无需改动；也因此**不必**动 `AppContainer`（§7.1 头号双属文件保持只读）。
- 复用付的代价：设置页那一行副标题要扩成「控制内核与会话级两处自动压缩；仅用于长上下文验证」，避免用户以为只关了一处（`feature-settings` 余 79 行够，`strings.xml`，G16）。

**可红可绿判据（P14）**：
1. JVM：`SettledCompressPolicy.shouldEvaluate(event, compressEnabled)` 在 `compressEnabled=false` 时恒 `false`；且 W6 的 T1–T7 在复用后**保持全绿**（证明「复用没让内核判据脱节」）。
2. 真机：关开关后正常结束轮 `auto_compact evaluated=` **0 命中**；**同时**必须构造一次开开关的正常结束看到 `evaluated=1`，证明评估路径本轮被走过 ⇒ 遵循「WARN 有闩、0 命中 ≠ 通过」。
3. 真机：关开关后手动压缩按钮仍可点且落 `manual_compress ... cache_invalidated=true`（保住现役语义）。
4. 红态：关了开关仍触发会话级压缩；或关了开关手动压缩被禁；或两套机制只交一条「被调用」判据（W4 事故原样）。
5. **「被调用」判据两套分别给**：`ContextBudget.prepare` 的关闭路径沿用 W6 既有判据（`compressContext` 已是 `RunnerKey` 成员、有 `RunnerCacheTest` 钉住）；`ConversationOps.autoCompactOnSettled` 用 §3.4 的唯一调用点扫描 + 变异自证。**不许因共用一个开关就合并成一条**。

### 3.4 (a) 的可红可绿判据

- **触发判据**（JVM，`ChatViewModel` 不可 JVM 实例化 ⇒ 把分流逻辑抽成纯函数 `SettledCompressPolicy.shouldEvaluate(event)` 放 `core-context`/`feature-chat` 可测文件）：
  - 绿：`Finished(ModelStopped)` ⇒ `true`；红态 = 把 `ModelStopped` 换成 `BreakerTripped`/`Cancelled`/`Failed` ⇒ 必须 `false`（4 条反例）。
  - 真机：`adb` 设备侧日志落盘后 grep `auto_compact evaluated=1 reason=settled`；失败/取消轮次该串 **0 命中**，且必须同时证明评估路径被走过（构造一次正常结束看 `evaluated=1`）⇒ 遵循「WARN 有闩、0 命中 ≠ 通过」。
- **收益阈值判据**（JVM 纯函数）：
  - `applied = committedChars > COMPACT_TRIGGER_CHARS && (savingChars - compactCostChars) >= MIN_SAVINGS_CHARS && snapshot.compressedUpTo == null`；
  - `MIN_SAVINGS` 草稿建议 256 token，本波按**字符**换算（与 L3 同量纲，不另起估算器）。
  - 绿：`applied=true` ⇒ 落 `auto_compact dropped=N kept=M cache_invalidated=true` 且下一请求 `cached_tokens=0`（**双向断言**：既看归零、也看压缩前确实有过 `>0` 或至少 `changed=none` 的稳定轮）；红：`saving < minSavings` 却落了账 ⇒ 判红。
  - **`compressedUpTo != null` 时记 `auto_compact=noop reason=already_compressed`，不静默**（W6 `ConversationOps:88` 的静默返回在自动路径不许照抄，否则「每轮都评估」变成看不出来的空转）。
- **「被调用」判据**：`ConversationOps.autoCompactOnSettled` 必须在 `ChatViewModel.collect` 有唯一调用点；守卫新增一条「`autoCompactOnSettled` 调用点 == 1 且位于 `collect` 内」的**赋值形态**扫描 + selftest 变异自证（删调用 ⇒ 判红）。**不许只交 9 条纯函数单测就算完**（W4 事故原样）。
- **前缀单调性判据**（见 §5 矩阵 P2）：未压轮次之间 `prefix ... changed=none` 的 INFO 行数 ≥ N-1（不是「0 条 WARN」）；压缩轮必须 `changed≠none` 且 `cached=0`（这是**预期**，不是失败）。

### 3.5 摘要请求 W7-A 不做（顺带裁决）

草稿把「摘要请求复用主前缀 + 带 tools」列 P0。现役 `EngineBackedSummarizer.kt:20-22,56-63` 的硬约束 1 明确不带工具（带工具会回 `tool_calls` 使摘要为空），且用独立 `INSTRUCTION` system。摘要请求是**旁路一次性请求**，它的前缀与主请求本就不同；「压缩不破坏前缀」说的是**主请求序列**（`ContextBudget.prepare` 的 `HISTORY` 段），不是摘要请求。⇒ 本波**不改摘要器**，W7-A 列入不做清单（§9），W7 的分块缓存命中工程全部落主链路（前缀指纹 + 三桶指标 + 真机取数）。

---

## 4. 口径订正：缓存命中率（事实 A 归宿）

**现状**【实测】：`EventReducer.kt:291-303 buildUsageLabel` 已把命中率上屏，三态齐（`:292` 空 / `:294` `cache n/a` / `:296` `cached*100.0/prompt`）；`EventReducerTest.kt:299/311/328` 三条钉住。

**草稿主张**：§5 采用 Octop 口径 `cacheHit% = cache_read/(cache_read + fresh_uncached)`，write 单独展示、三桶同屏，理由是「write 是首次写入付费，不应稀释命中率」。

**算术复核**【推断，算式推演非实测】：OpenAI 兼容族（本仓 `ProviderKind.OPENAI_COMPATIBLE` 是唯一放行族，`AppContainer.kt:115-118`）的 `prompt_tokens` **包含** `cached_tokens` ⇒ `fresh_uncached = prompt - cached`，代入 Octop 得 `cached/(cached + prompt - cached) = cached/prompt`，**与现役恒等**。W5 实测 `23 740 / 21 680 = 91.3%` 两口径同值。⇒ 在本仓当前支持的供应商范围内，改口径**不产生任何信息增益**，只是换写法。

**裁决：不改现役实现，不改三条既有用例，只订正文档口径**：
- `EventReducerTest.kt:311/317` 的 `60.0%` **不会变红**（fixture `prompt=1000, cached=600`，两口径同为 60.0%）；`:299/307` 的 `0.0%`、`:328/333-336` 的 `n/a` 同样不变。
- 草稿 §2 对标表「⚠️ 有原始值无 UI/指标」→ 订正为「✅ 已上屏，`cached/prompt`；Octop 口径在本仓唯一供应商族下与之算术恒等，列为 W8-03 引入独立 `cache_write` 字段时再评估」。**不新增功能、不复制一条判据**（W87 铁律）。
- 若将来切到 DeepSeek `prompt_cache_hit_tokens` / Kimi 三接口（W8-03），才需要独立 `cacheWrite`：届时 `EventReducerTest.kt:311/317` 仍绿，但**必须补一条含 write 的 fixture** 来钉住两口径的分叉（否则「改了口径没用例」又是空转）。
- 「三桶归一 + 原始 usage JSON 同屏」不在本次订正范围内（§1.4 第 5 条），仍属 W8-03。

---

## 5. 逐条验收判据矩阵

> 四态标注是**本波开跑前的预期状态**，跑完才能定案。「怎么跑」列写清 JVM / 真机 / API 三种通道；「红态」必须是能造出来的反例。
> **空命中语义统一**：判据扫描集为空（0 气泡 / 0 请求 / 0 日志行）一律 `⛔vacuous`，不得记 ✅。

| # | 功能项 | 判据（可红可绿） | 怎么跑 | 红态 | 预期四态 |
|---|---|---|---|---|---|
| P1 | **自动压缩触发时机**（正常结束才压） | 见 §3.4：`SettledCompressPolicy.shouldEvaluate(event)` 纯函数 5 条（1 绿 4 红）；真机日志 `auto_compact evaluated=` 仅出现在 `Finished(ModelStopped)` 轮 | JVM 单测 + 真机设备侧 `logcat -f` 落盘后 grep | 失败/取消/`BreakerTripped` 轮出现 `evaluated=1`；或正常结束轮 0 命中 | ✅（待真机行使）/ 未跑=`⛔vacuous` |
| P2 | **压缩不破坏严格前缀单调性** | 未压连续 N 轮：`prefix ... changed=none` 的 **INFO 行数 ≥ N-1**（`ContextBudget.kt:275-280`），且第 3 轮 `cached_tokens>0`（NV 写入延迟 ≥2 次）；压缩轮必须 `changed≠none` + 下一请求 `cached=0`（**预期非失败**） | JVM（纯函数钉 `changedLabel`）+ 真机 NV 三连发（`_w7` 探针）+ 设备侧日志 | 未压轮报 `changed≠none`（组装漂移）；或压缩轮报 `changed=none`（判据没行使） | ⚠️（NV 命中依赖路由，W6 实测「不可作为承诺」） |
| P3 | **分块缓存命中** | 三段递增前缀 `cached_tokens` **单调不减且末段严格大于首段**（W6 matrix §五收紧后的判据，不能用首版「末段>0」）；同时记录 `prompt / cached / prefix_fp / changed` 同行 | API（PC 侧 matrix 脚本）+ 真机 NV 端点 | 恒定值（如 `5120→5120→5120`）被判 ✅ ⇒ 判据自身假阳性；或前缀一变即归零（`4336→0→0`） | ⚠️（W6 三模型全 🟡，本波以「取到事实表」为验收，不承诺命中率） |
| P4 | **模型切换守卫** | §2.3 全套：三形态全覆盖、`verified` 优先、字符量纲、vacuous 三态、被调用判据（变异自证） | JVM 策略类 + arch-guard 守卫 + 真机 `preferences_pb` 读回 | 绕过守卫直写 `activeEndpointId` 不判红；或换回 `contextLength` 裸值后「1M 声明+256k 实测」用例不红 | ✅（判据可造红）/ 真机未跑=`⛔vacuous` |
| P5 | **1M / 995k 档设备侧行使**（W5-04） | `import-1m.jsonl` push → SAF 导入 → 判据 = **针出现在 `content` 且 `finish_reason=stop`**；必须**同时登记 `max_tokens=4096`、`prompt_tokens`、`completion_tokens`、`finish_reason`**（缺一不可复算）。**重放 ≥2 次**才允许写成稳定结论：W6 同 `ptok=994620`、同题面、同 `max_tokens=4096` 却是 `⛔vacuous`（`completion=4096` 全被 reasoning 吃光、`finish=length`、content 空），本波一次为 `ctok=187 / finish=stop / hit=true / 110.57s` ✅。`finish=length` + content 空记 `⛔vacuous`，不记通过也不记失败 | 真机 + API（用量行）+ 设备侧 `exec-out` 读回；同参数重放 ≥2 次 | 只推文件没导入就宣称「1M 已行使」；只看 HTTP 200 忽略 `finish_reason`；**把单次 ✅ 写成「1M 能力上限 / 本波解除了 vacuous / 模型能力变了」**；或把 `max_tokens=512` 写进阶梯结论（它只属 channel probe 自证） | ✅（本波**一次**真实通过）/ 未重放 = `⚠️ 部分`，S4 补 ≥2 次；须先补 §6.1 前置 |
| P6 | **多模态输入回归** | ≥200×120 真实图形（W5 §4.1：1×1 会产假阴性）；视觉+工具同轮在 **NV** 上验（dots 上是 vacuous） | API + 真机 | 用 1×1 图判「视觉不可用」 | ✅（W5/W6 已证，本波回归） |
| P7 | **工作空间文件读写回归** | `file_list` + `file_read` 两轮收敛、`module_{1..5}.kt` 实读；32K 档实发 ≤ **27 904**（W7-01） | 真机（W7-01 夹具已在设备沙箱） | 实发 > 27 904 且无 `hard_budget_shrunk` | W7-01 现状 ⛔（超时未完成）→ 目标 ✅ |
| P8 | **记忆 / Skill 回归** | `promptContext()` 每 run 快照一次、run 内 system 字节稳定（`AppContainer.kt:189-202`）；技能一级披露 + `skill_load` 可用；`SkillLoaded` 事件上屏 | JVM + 真机 | run 内重读导致 system 抖动（前缀指纹 `SYSTEM` 段变化） | ✅（回归） |
| P9 | **MCP 回归** | `toolRegistry` 同步、`ToolSourceUpdated` 连不上必须可见；**真机联调不可行使** | JVM（协议层用例）+ 设备侧配置读回 | 明文 http 被 `usesCleartextTraffic=false` 拒却记成「通过」 | ⛔ 平台阻塞（不放开 cleartext） |
| P10 | **音频输出回归** | 设备上 TTS 引擎清单 + NV `GET /v1/models` 音频类计数 + `POST /v1/audio/speech` 状态码，三项证据齐全 | 真机 `pm list packages` / `settings get secure` + API 探测 | 写成「未测」或「通过」 | ⛔ 不适用（证据见 §6.2） |
| P11 | **手动压缩 / 会话导入**（W7-03） | 压缩后历史条数下降 + 分隔卡 + `manual_compress dropped=N kept=M cache_invalidated=true` 落盘 + 下一请求 `cached_tokens=0` **双向**（既看归零、也验压缩前有稳定前缀）+ 撤销后命中恢复；导入 20 条 ⇒ 20 气泡、`role=tool` 丢弃计数、8 MiB+ 被拒 | JVM（已有 10+6+19 条）+ 真机 | 只看 `cached=0` 不看双向；或用「release 包看不到导入入口」判绿（本仓 `:app` 只有 debug 变体 ⇒ 该判据不可执行） | ⚠️（真机未走） |
| P12 | **口径订正落地**（§4） | `EventReducerTest.kt:299/311/328` 三条保持绿；文档口径已订正；无新增恒绿断言 | JVM 全量 | 有人为「凑命中率指标」新增 `assertTrue(true)` | ✅ |
| P13 | **切换守卫 chars/token 二档**（§2.3） | 无真值（`measured<=0`）⇒ cap 按 floor **1.8**；有真值 `5.407` ⇒ cap 按标定值；两者同一 `committedChars` 下结论可不同。直接调 `hardContextBudgetTokens` / `effectiveCharsPerToken` / `charCapOf` 三个现役函数 | JVM 纯函数 + 反例注入 | 恒用 ceiling 6.0 ⇒ 无真值用例「本该禁止」被放行（危险方向）；恒用 floor ⇒ 有真值用例「本该放行」被禁（与需求 1 冲突）；抄算式丢掉 `window/2` 兜底 ⇒ `maxOutputTokens>window` 用例不红 | ✅（两态可造） |
| P14 | **单开关双停语义**（§3.3.1） | `compressContext=false` ⇒ 内核与会话级自动压缩**都停**、正常结束轮 `auto_compact evaluated=` 0 命中、**手动压缩仍可点**；`true` ⇒ 两套各自按自己的触发条件行使。W6 的 T1–T7 复用后保持全绿 | JVM + 真机设备侧日志 + 手动压缩按钮点按 | 关了开关仍触发会话级压缩；关了开关手动压缩被禁；只交一条合并的「被调用」判据；只凭「0 命中」判通过而不构造一次开开关的 `evaluated=1` | ✅（待真机行使） |

**判据纪律（写进方案的每条都要有）**：
- 每条判据必须给出**可执行触发条件**（哪个事件/哪个文件/哪条命令）与**空命中语义**（0 命中 = `⛔vacuous`，不 = 通过）。
- 「0 条 WARN」不等于通过：`ContextBudget.warnPrefixChanged`(`:324-336`) 只在变化时打；必须同时数 `changed=none` 的 INFO 行。
- 「测试全绿」不等于「口径被验证」：新增组件一律附「被调用」判据 + 变异自证（§2.3、§3.4、§3.3.1）。
- 量纲：P2/P4/P7/P13 全部用**字符硬预算**（`hardCharsPerToken` floor 1.8 / ceiling 6.0 按 provider 真值标定，`PreStepBudget` L3）；**切换守卫的 chars/token 必须走 `effectiveCharsPerToken` 二档**，不许另起 `TokenEstimator.estimateMessages` 口径、也不许重写预算算式（第二个真值源）。
- 🔴 **长档判据必须「重放 ≥2 次」**：W6/W7 同 `ptok=994 620`、同题面、同 `max_tokens=4096` 却一个 `⛔vacuous` 一个 ✅，差别只在输出侧 reasoning 消耗 ⇒ 单次观测**不许**写成稳定结论或产品承诺；`finish_reason` 与 `completion_tokens` 必须同记（`w7-provider-matrix.md` §十.2）。这不是「能力变了」，也**不是**「调判据参数解除 vacuous」（该归因已被撤回，§1.4-9）。

---

## 6. 设备硬件可行性维度（OPPO PDRM00 / Android 13 / SDK 33）

### 6.1 设备实测订正：W7-01 / W5-04 的前置【开发席 exec-out 读回，主理人转达；载体 `_w7/device-baseline.json` 在**工作区根**，本席已核存在（附录 A 证据等级订正）】

| 项 | W6 交接 §七 的说法 | 开发席实测 | 本波处置 |
|---|---|---|---|
| 活跃端点 | 「NV-VL 已录入密钥并设为当前」 | `active_endpoint_id=orvl`(OR-DOTS)；`nv1`(NV-VL) `hasSecret=False`；`files/harness/secrets/` 只有 `orvl.bin` | **交接错误** |
| 密钥常驻 | 「安全真机，密钥可常驻，端点建一次即可复用」 | 对 `orvl` 成立、对 `nv1` 不成立 | 保留裁决，但 `nv1` 需重录 |
| 窗口档位 | 32K | `max_context_tokens=32000` ✅ | 一致，W7-01 不必再改 |
| 夹具 | `module_{1..5}.kt` 5×16.3 KB、`chunk1..3` | `module_1..5.kt` 实测 16261–16303 B ✅；`chunk1..5` 共 **5 个**（交接只写 1..3） | 按 5 个计 |
| 1M 题面 | 「下载目录已有 `import-400k.jsonl`」 | **`import-1m.jsonl` 不在设备下载目录**；本地 `_w6/` 有 5 221 133 B 副本未推 | W5-04 需补 push |

**W7-01 显式前置步骤（缺一不可，写成判据）**：
1. 给 `nv1` 录密钥（UI 或 `saveEndpoint`）⇒ 判据：`files/harness/secrets/nv1.bin` 可读回、`endpoints.json` 中 `nv1.hasSecret=true`。
2. 切 `active_endpoint_id` 到 `nv1` ⇒ 判据：`settings.preferences_pb` 读回 `active_endpoint_id=nv1`。
3. **连接自证**：NV-VL 1k 档取针 `hit=True / finish=stop` + 连接测试 200（开发席已过 1k 取针【中转】，正式重放前再跑一次并落盘）。
4. 才跑 32K 档 5×16 KB 并行读，验「实发 ≤ 27 904」。
> 🔴 少做前两步 ⇒ 重放会静默走 OR-DOTS 再次 `SocketTimeoutException`，然后被误判成「代码缺陷」—— 这正是 W7-01 在 W6 没拿到证据的同类陷阱（那次是模型侧超时，这次是端点根本没切）。

**1M 题面的导入上限核实**【实测】：`core-data/SessionImportParser.kt:53 MAX_BYTES = 8 MiB`（8 388 608 B）、`:56 MAX_MESSAGES=5_000`、`:67 MAX_MESSAGE_CHARS = 4 Mi 字符`（W6 已从 200 000 放宽，注释 `:59-65` 明写为 990k token 干草堆准备）⇒ 5 221 133 B < 8 MiB，**单文件可过**；仍须核单条 JSONL 消息 ≤4 Mi 字符、条数 ≤5000。⚠️ handoff §2.3 写的「单条 ≤4 MiB / 总量 ≤8 MiB」与现役口径**不同**（现役是单文件 8 MiB / 单条 4 Mi **字符**），别照抄旧口径排分片。

### 6.2 TTS / 音频输出：⛔ 不适用（证据齐全，不许写「未测」）

| 证据 | 结论 |
|---|---|
| `AppContainer.kt:162-170`【实测】：OPPO PDRM00 默认 TTS = `com.iflytek.speechsuite`，`speak()` 挂住 40s+ 且点停止不可中断；`speakTools` 刻意**不注册**（注释保留启用方法） | W3-01「`speak` 裁决关闭」仍成立 |
| 【中转】设备 `tts_default_synth=com.iflytek.speechsuite` 且仅此一个 TTS 引擎 | 无备选引擎可换 |
| 【中转】NV `GET /v1/models` 200、85 个模型里 **0 个** audio/TTS/ASR；`POST /v1/audio/speech` **404**；`wan2.2-animate`、`nvidia/vsr` 不在 chat 列表 | 云端 TTS 路径也不存在 |

⇒ 需求 2 的「音频输出」判据 = **⛔ 不适用 / 不可执行**，替代判据：`pm list packages` + `settings get secure tts_default_synth` 读回 + NV models 列表音频类计数 + speech 端点 404，四项同屏落盘。**不许**为它放开 `speak`（会挂死 run），也不许记「未测」。

### 6.3 cleartext / SAF / 内存 / 超时

| 面 | 约束 | 替代判据 |
|---|---|---|
| **MCP 明文 http** | `app/src/main/AndroidManifest.xml:25 usesCleartextTraffic="false"`（G15b），明文被平台拒 `transport_error`；**不为测试放开**（W5-03 裁决） | 真机联调 **⛔ 平台阻塞**；用 JVM 协议层用例 + `ToolSourceUpdated` 可见性 + 配置读回。若必须真机，需提供 **https MCP 夹具**（本波不提供） |
| **SAF 导入** | OPPO/A13：`MEDIA_SCANNER_SCAN_FILE` 广播不生效 ⇒ `content call --method scan_file`；SAF「图片」collection 不索引 `Pictures` 目录（知识库 §2）。题面走 `SessionImportRow` → `SessionImportParser`（纯函数，292 行 / 19 条单测） | 导入后**气泡数 == 消息数**（`toChatBubbles`:113-119）、`role=tool` 丢弃计数、超限提示具体原因；`import-1m.jsonl` 先 push 到设备 Download 再走 SAF |
| **1M 载荷注入 / 内存** | 解析后全驻留内存，`MAX_BYTES=8 MiB` 是「两倍开销」的硬边界（`SessionImportParser.kt:52,129-130` 先判字节再解码，防 OOM）；995k 档本波 **110.57 s**（`w7-provider-matrix.md` §4.1）、W5 PC 侧 115–136 s | 设备侧必须**单次注入、不并发**；观察 `logcat` 有无 `OutOfMemoryError` / GC thrash；超时用 600 s 窗口（W6：「300 s 超时 ≠ 模型不可用」，step-5 36.9s / glm 216.3s） |
| **OR 网关窗口预检** | 字符数 ÷ 4，散文只能用到声明窗口 ~60–75%。本波硬证据：`w7-provider-matrix.md` §十.1 用 400 错误串 `734590 of text input` 与 `chars=2 938 357`（`÷4 = 734 589.25`）精确对上，比 W6 的推断更硬。chars/token 实测：nv-vl **5.223 → 5.407**（文档 §4.1）、dots 5.74、north 6.5【中转：主理人转达；`BudgetFormula.kt:73-75` KDoc 的 5.73–5.75 是 W6 另一批，别混】 | 1M 行使**只用 NV 端点**；`dots` 1k→256k ✅、512k/995k 被 400 拒（错误串 **建议用 context-compression 插件压缩** ⇒ §3.1 动机）；`north` 只到 32k。OR 免费模型不得写成「512k 可用」（W5 matrix §2.3/§八.1） |
| **995k 档同参数分叉（reasoning 消耗）** | W6：`prompt=994 620` + `completion=4096` 全被 reasoning 吃光 ⇒ `finish=length` / content 空 ⇒ `⛔vacuous`；W7：**同 `ptok`、同题面、同 `max_tokens=4096`**，`ctok=187` ⇒ `finish=stop` / 取针命中 ⇒ ✅（`w7-provider-matrix.md:95-111`）。⇒ **一次真实通过，但依赖单次观测**；差别只在输出侧 reasoning 消耗，**不是模型能力变了、也不是判据参数变了**（`max_tokens=512` 只包裹 channel probe 的 `pb.post`，阶梯走 `mx.post` 且标题即 4096，见 `:45` / §4.1）——主理人原有的「调参数解除 vacuous」归因已撤回（§1.4-9） | 1M 判据必须**同时记 `finish_reason` 与 `completion_tokens`**，同时扫 `content` 与 `reasoning_content`；把「输出被截断」单列 `⛔vacuous`；**重放 ≥2 次**才允许写成稳定结论（§5 P5 / §9 风险 10）。**不许**写成「1M 能力上限」或「本波解除了 vacuous」 |

### 6.4 adb 取证通道（硬约束，知识库 §2）

- 一律**有线 adb**；`adb` 唯一路径 `_j2env/sdk/platform-tools/adb.exe`；install/push 加 `MSYS_NO_PATHCONV=1`，push 源路径写 Windows 形态 `D:/...`。
- **读二进制一律 `adb exec-out`**（`adb shell > file` 会 LF→CRLF 翻译损坏二进制）；`preferences_pb` / `nv1.bin` 的读回必须走它。
- **PC 侧 logcat 环形缓冲实测仅 ~256 KiB** ⇒ 跨机取证必须**设备侧 `-f` 落盘**：`adb shell "nohup logcat -b all -f /data/local/tmp/w7.log &"`，事后 pull；adb 命令串行执行（沙箱 server 每命令后退出）。
- **安装前必须唤醒设备**（AGENTS.md §三）：`input keyevent KEYCODE_WAKEUP` + `screencap` 确认非全黑；灭屏 `pm install-commit` 会永久挂起（ColorOS 冻结安装服务）。走 install-create/write/commit 三步法。
- **卸载重装前 `mv` 保全 `/sdcard/Android/data/<pkg>/files`**（12 GB 模型）；本波纯 API 仓虽无本地权重，仍按纪律核目录。
- **UI 自动化**：`uiautomator dump` 对 Compose bounds 不可靠 ⇒ 点按判据以 journal/应用日志为准；`input text` 非 ASCII 设备侧 NPE ⇒ 题面用 ASCII（空格 `%s`）；键盘弹起后发送键上移 ⇒ 每次按截图重定位坐标；`_w6/ui.py` 已封装 `adb -P 15037`、中文不可注入、发送键动态 dump。
- **模型切换的稳健通道**（若 UI 点按不可靠）：改 datastore `active_endpoint_id`（等长 UUID 字节替换 + `run-as cp` + 重启），但⚠️ **翻转复位必须走 UI 开关**（运行中实例不感知外部 datastore 改动）；datastore 直改 JSON 值长度时须同步修正每层 protobuf 长度前缀，改完 `exec-out` 读回核验。

---

## 7. 文件域切分（≤4 席，互不重叠）

> 依据：知识库 §6「子 Agent ≤4 路并行（7 路实测集中 429）」；上一轮本席即因 429 零产出。
> **本席文件域只有 `docs/notes/w7-plan.md`**（主理人已确认 android-developer 纯探测、ci-workflow-specialist 拆 `:core-context`，都不写本文件）。

| 席 | 文件域（独占） | 主要内容 | 不碰 |
|---|---|---|---|
| **A · 内核席** | `:core-context`（`SwitchGuard.kt` / `SettledCompressPolicy.kt` / 对应 test）；`:core-agent/loop/` 只允许改 `ContextBudget` 日志字段（如需 `changed=` 计数） | §2.3 切换判定式（纯 JVM）、§3.4 触发分流纯函数 + 收益阈值、`minSavings` 字符换算；**策略类只依赖 `core-model`** | `feature-*` / `core-data` / UI / 脚本 |
| **B · UI 席** | `feature-chat/`：`ConversationOps.kt`、`ChatViewModel.kt`（**484/500，余 16 行 ⇒ 新逻辑一律外提新文件**）、`EventReducer.kt`（仅 §4 文档口径，不改实现）、`ChatUiState.kt`；`feature-settings/`：`SettingsViewModel.kt`、`SettingsScreen.kt`（余 79 行） | 自动压缩落 `ConversationOps` + `collect` 事件分流；三形态切换各接入 `SwitchGuard`；置灰/原因文案/二次确认（走 `strings.xml`，G16）；缓存指标卡（若需要） | `core-*` / 脚本 / CI |
| **C · 脚本守卫席**（**S1 之后的角色**，与当前 CI 席任务 #2 的派单文件域不同，勿混） | `scripts/arch-guard.sh` + `scripts/arch-guard-selftest`（新增守卫 + 变异自证）、`.github/workflows/build.yml` 的 `EXPECTED_TESTS` 与注释、**`docs/06-deferred-ledger.md`**（唯一台账，单人落笔） | 新增「`SwitchGuard` / `autoCompactOnSettled` 被调用」判据；W7-04 三件同提交；**G9 三方一致实跑**（`include 数 == MODULES 数组长度 == 实际目录数`，不是「改 13→14」——`fulltest.sh:54-67` 动态读、无写死数字，§1.1）。✅ **W6-01 回写文件域已由主理人裁决（2026-11-04）**：CI 席只改台账 W6-01 一行（`CLOSED` + 证据链），不改业务源码；KDoc 归 UI 席可选（§1.4-12c / §8） | 源码 / UI |
| **D · 文档与真机席** | `docs/notes/w7-plan.md`（本文件，本席独占）、`docs/handoff-*`（下一波交接由主理人定）、`_w7/` 设备脚本与夹具、`docs/notes/w6-provider-matrix.md` 之外的**新**矩阵文件 | 真机取证、W7-01/02/03 重放、1M 题面 push、设备侧日志落盘 | 上述三域的任何文件 |

### 7.1 🔴 双属高风险（历史实摔）

| 文件 | 风险 | 处置 |
|---|---|---|
| **`core-data/AppContainer.kt`**（389 行） | **头号双属**：内核席要接 `SwitchGuard` 判定、UI 席要取会话体积、CI 席要动 `RunnerKey`(:266) 与 `saveEndpoint`(:304)；W6 曾因两席同时改它导致工作树被还原（知识库 / 项目 skill `liquidagent-subagent-write-isolation`） | **本波定为「只读 + 单人串行」**：任何对它的改动由 **C 席单人**在 A/B 席都收口后一次性落笔；A/B 席只通过新增纯策略类与 UI 取数接入，**不改 `AppContainer` 一行**。派单前先 `git status` 核实实际产出再采信完成通报 |
| `core-agent/loop/ContextBudget.kt`（364 行） | A 席（指纹/计数）与 D 席（真机取数口径）都可能想改 | 只 A 席有写权；D 席只提需求 |
| `feature-chat/ChatViewModel.kt`（484/500，余 16） | B 席的自动压缩分流、`collect`、切换提示都要挤进来 | **禁止在此新增逻辑**：一律外提新文件（先例 `ConversationOps` / `ContextLimitWriteback` / `ChatContextWindow`）；`collect` 里只留一行委托 |
| `scripts/arch-guard.sh` 的 `MODULES`/`LINE_CAPS`/`MAIN_PATHS`/`CJK_SCAN_PATHS` | ✅ **已同步，无需补**（主理人 2026-11-04 grep 复核 + 本席回树核实）：`CJK_SCAN_PATHS` 现 `:311-314` 已含 `core-context/src/main`，CI 席顺手补齐 W6 历史漏掉的 `core-kernel`/`feature-tools`；扩面前按 G16 等价逻辑预检 55/1/16 行 CJK 命中全 0 ⇒ 不判红，G16 基线 0 与 `VACUOUS_BASELINE` 未动。本席初稿「未包含」为过时判断，已订正 | 保持已补状态，**别重复改这一处**；S0 四闸门已跑完且 `arch-guard` `VIOLATION=0` |
| `.github/workflows/build.yml` `EXPECTED_TESTS` | W7-04 要 666→658，且新增测试必须**同一次提交**改大（`:524` 对账判据） | C 席单人；A/B 席新增 `@Test` 必须提前报数给 C 席，不得各自改常量 |

### 7.2 新模块 `:core-context` 边界（**✅ S0 已完成，2026-11-04 四闸门全绿**）

> 时态：S0 已由主理人单人串行跑完四闸门并给出原始数字（§1.1），以下边界为**已验证的完成态**。工作树 `D context/* + ?? core-context/` 只是**未提交**（本波约定主理人统一提交），不等于未验证（W6 交接 §九）。

- 内容：`context/` 整体下沉（6 文件 1387 行），`core-kernel` 只留 `policy/`(235) + `log/`(168) = 403 行；包名不变。
- 上限（台账 W6-07 分法 A，CI 席已采用）：`core-kernel` 450 / `core-context` **1450**（单文件 400），之和 **1900**；+100 是拆分开销，必须写进台账项与提交信息。
- `TOTAL_LINE_CAP=42500` **一律不动**（全仓 main 34 983 / 42 500，余 7 517）；不许把拆分当扩容，不许「凑 1800」（余 13 行 = 死结搬家）。
- 依赖方向单向 `core-context → core-kernel`（context 依赖 `AgentPolicy`/`AgentLogStore`，不反向）；`core-agent` 同时 api() 依赖两者。
- ⚠️ `ContextCompressor.kt` 337/400（余 63），拆分**不解决单文件余量**；本波若动它按「拆不调上限」处理。

### 7.3 需求 5「UI 旧仓可复用」的准确口径

**复用 = 已搬 + 本波只补三块**，禁止按「从零搬」排工作量：
- 已搬（W2U 波）：`core-design` 12 439 行液态玻璃全套；`feature-chat` 顶栏 / 气泡 / 工具轨迹 / 上下文占用条 / 输入栏；`feature-settings` 端点编辑 / 四档窗口 / 自动压缩开关（**接线已完成**，见 §8 W6-01）。
- 本波补：① **模型切换确认框 + 置灰原因**（复用 W6 手动压缩对话框的措辞与组件）；② **缓存指标卡**（若 §4 裁决后仍要「原始 usage JSON 同屏」，注意这是新功能不是复用）——**优先扩 `usageLabel` 口径，不加新状态字段**：`ChatUiState.kt:181-186` 已有 `contextTokens` / `contextLimit` / `usageLabel` 三个现成字段（`:201-210` 是 `compressedUpTo` / `compressedSavingTokens`），而 `feature-chat` 只剩 41 行余量；③ **自动压缩开关的语义扩展 —— 复用，不新增第二个开关**（§3.3.1，已按主理人补充 2 修订，取代初稿「必须新增独立开关」）：现役 `compressContext` 是内核预算压缩开关，本波把会话级收尾压缩接到同一布尔上（关则双停、手动压缩不受约束），并扩设置页副标题说明「控制两处」，避免用户面对两个「自动压缩」各自解释。W6 的 T1–T7 复用后保持全绿（P14 有判据）。
- 余量红线：`core-design` 12439/12600（余 161）、`feature-chat` 3459/3500（余 41）、`ChatViewModel` 484/500（余 16）、`feature-settings` 2921/3000（余 79）。
- 旧仓 LiquidAgent 同作者 Apache-2.0 可搬，但**每条判据先问「纯 API 路径下还触发吗」**（AGENTS.md §一）；Operit LGPL / Operit2 AGPL ⇒ 零代码搬运，只取结构思想。

---

## 8. 挂账逐条裁决（做 / 不做，不做写理由，不许无限期挂）

> 依据 `docs/06-deferred-ledger.md` 表格实际编号行（§1.4 第 3 条：快照统计 39 / 注释 38 / 表格 41 行不一致）—— **主理人 2026-11-04 裁定：以表格实际编号行为准，不阻塞**；G20 不判合计数（§1.1），该分歧只影响人读真值。

| ID | 裁决 | 理由与判据 |
|---|---|---|
| **W7-01** | ✅ **本波做，但改前置** | 32K 档 5×16 KB 并行读 ⇒ 实发 ≤ **27 904**（= 32 000 − 4 096）。**不是**「换 NV 端点即可」：先给 `nv1` 录密钥 + 切 active + 连接自证（§6.1）。夹具已在设备沙箱。与 W7-02 同批取数 |
| **W7-02** | ✅ **本波做（取事实表，不承诺命中率）** | NV 三连发 `0→0→4336`，写入延迟 ≥2 次；本波验「本仓前缀逐字节稳定时第 3 次 `cached>0`」并产出「NV 真实最小前缀长度 + 当前 ~800 前缀是否够」事实表。⚠️ 不得为凑缓存加长 system（W5 §七/w25 铁律） |
| **W7-03** | ✅ **本波做，删一条不可执行判据** | 手动压缩/会话导入真机验收：历史条数下降 + 分隔卡 + `manual_compress ... cache_invalidated=true` 落盘 + `cached_tokens=0` **双向** + 撤销后恢复；导入 20 条 ⇒ 20 气泡、`role=tool` 丢弃计数、8 MiB+ 被拒。⚠️ 台账里「**release 包看不到导入入口**」与 W7-06① 重复，且 `:app` 只有 debug 变体 ⇒ **不可执行，本波显式裁「不做」，从验收清单划掉** |
| **W7-04** | ✅ **本波做，三件同一次提交** | ① 删 8 个 `SmokeTest.kt`；② `EXPECTED_TESTS` 666→658；③ 跑全量确认 `658/0/0` 且无模块 `NO-SOURCE`。**分两步中间态必红**（先删文件 ⇒ 基线 666/实跑 658；先改基线 ⇒ 基线 658/实跑 666）。由 C 席单人落笔（台账 §三.6）；`build.yml:127-129` 已有同口径注释 |
| **W7-05** | ⚠️ **本波做「补用例」，不改结构** | `PreStepBudget.sanitizeForProvider` 上界式已写进类头但无用例；台账状态 `CARRIED`（推 W7 与 W7-01 同批顺手取数）。本波补 1 条 JVM 用例钉「实发 ≤ charCap + 未配对数 × 模板长度」，**与 W7-01 真机同时取数**；结构不改（W6 裁决「接受 + 写清上界」） |
| **W7-06** | ✅ **本波做，但拆成「做一条、裁掉一条」** | ①「release 包看不到导入入口」**不可执行 ⇒ 显式裁不做**（与 W7-03 同因，`:app` 只有 debug 变体）；②「关压缩后请求接近窗口」**假绿风险高** ⇒ **必须先 grep 出「压缩真的没发生」的独有证据再跑**（`ContextBudget.prepare` 在 `compressContext=false` 时直接返回全量、不写任何可观测输出，而 400 会显示成同一个样子）。顺序：**先确认权限/变体、再看设置项**。本波产出「独有证据清单」+ 一条能红的判据；若拿不到独有证据 ⇒ 标 `⛔vacuous` 并关闭，不许挂着 |
| **W6-01** | ✅ **本波关闭（订正台账，不是修代码）** | 台账称「`AgentPolicy.compressContext` 零写入点」【实测已过时】：`SettingsViewModel.kt:191-196 setCompressContext` → `SettingsRepository` → `AppContainer.kt:261-272`（`val compress = settings.compressContext.first()` + `AgentPolicy(compressContext = compress)`）→ `SettingsScreen.kt:197` 开关 → `SettingsRoute.kt:120` 注入，**全链已接**。**台账（OPEN）与 W6 handoff §2.3 C（开关已进设置页 + RunnerKey）确实矛盾** ⇒ 以现役树为准：`SettingsRepository.kt:70-72` 自述「它此前是零写入点的声明……**接上它同时清掉这条挂账**」，即 **W6 已解决、台账漏回写**。✅ **文件域已由主理人裁决（2026-11-04，已下派 CI 席）**：台账 §三.6「只由 CI 流落笔」成立 ⇒ **CI 席只改台账 W6-01 那一行**（状态列 `CLOSED` + `文件:行` 证据链），**不改任何业务源码**；`SettingsViewModel.kt:189` 过时 KDoc 是可选顺手项、归 UI 席、不是关闭前提。本席「订正台账，不是修代码」结论被采纳 ⇒ 不存在「无人有文件域」，无需再裁（§1.4-12c 已闭合） |
| **W6-02** | ⛔ **本波不做，给明确归宿（W8 限期）** | `EngineCapabilities`/`capabilities()` main 源集零调用点（只有 5 处测试 override）。`w6-plan.md §2.5b` 明确不信任它当窗口上限通道；本波切换守卫用的是 `RemoteEndpoint.verifiedContextLength`，**不依赖 capabilities** ⇒ 本波无用。裁决：**W8 首波二选一**（接线或连字段带用例删），不许无限期挂；本波只在台账「阻塞/关闭于」写 `W8-01` |
| **W5-05** | ⛔ **本波不做，但必须二选一裁决（限期 W8）** | `autoApproveDangerous` 仅定义（`AgentPolicy.kt:115`）+ 三条「一旦接线就红」的警报用例（不是防护已生效）。与本波主题（压缩/缓存/切换守卫）无关，且接 UI 开关要动 `feature-settings`（余 79）。裁决：**W8 同 W6-02 一并二选一**（接线：UI 开关 + 审计账行 + 三用例转正式消费点；或连字段带用例删），本波在台账写明限期，不许再推 |
| W3-01 / W3-07 / W3-02 / W3-04 / W3-05 / W5-03 / W5-04 / W5-06 / W5-07 / W6-03 / W6-05 / W6-06 / W6-08 / W26-02 | 各按原台账状态 | W5-04（1M 设备侧）本波**随 W7 顺带做**（§6.1 补 push + §5 P5）；W5-03（MCP cleartext）维持 ⛔ 平台阻塞裁决；W3-05（journal/FreezeClock）仍建议独立 W6.5 波，本波不开（与 `RunState` 改动冲突，且本波已动 `collect` 事件序列） |

---

## 9. 风险与不做清单

**风险（按严重度）**
1. ✅ **需求 1 × 需求 4 字面冲突**、**压缩 × 缓存冲突**（§1.4-1 / §3）：**两处均已由用户 2026-11-04 裁决闭合**（解读 A 容量判据 + 采纳 (a)，见 §2.1 / §3.2 / §10 S6）；(b) 降级档保留未选用，实现按已确认的兼容解落地。
2. 🔴 **`AppContainer.kt` 双属写入冲突**（§7.1）：并行子 Agent 的 `git stash` / `reset --hard` 会抹掉他人改动（历史实摔 2 次）；派单后一律先 `git status` 核实实际产出再采信完成通报（知识库 §6）。
3. 🔴 **判据空转**：新增 `SwitchGuard` / `SettledCompressPolicy` / `autoCompactOnSettled` 若只有纯函数单测，就是 W4 `LayeredContextCompressor` 事故原样 ⇒ 每个组件必须附「被调用」判据 + 变异自证 + 真机取证。
4. 🔴 **0 run 陷阱**：`build.yml` `push.branches:[main]` + `paths-ignore` 挡纯文档提交 ⇒ 本方案单独推 = 0 run，**不是绿**；CI 核验必须随源码提交，且 `git push --dry-run` 才是真判据。
5. ⚠️ **免费模型不可作 1M 主战场**：OR 网关按字符÷4 预检、散文只能用声明窗口 ~2/3；dots 512k 是**声明值未复测**（实测只到 254 771）；north 缓存恒 0、视觉 404。1M 行使只用 NV-VL。
6. ⚠️ **端点/密钥时态**：`nv1` 无密钥（§6.1）；切换守卫的 `verifiedContextLength` 只有真跑过一次 400 才有值 ⇒ 首次切换大概率走 `⛔vacuous` 的「显式确认」通道，别误读成守卫失效。
7. ⚠️ **行数余量**：`ChatViewModel` 余 16、`feature-chat` 余 41、`feature-settings` 余 79；任何「顺手加两行」都会先撞单文件上限再撞模块上限。`core-context` 1450 上限里 `ContextCompressor.kt` 已 337/400。
8. ⚠️ **全量单测口径**（知识库 §5）：注入 `JAVA_HOME/ANDROID_HOME/GRADLE_USER_HOME` + `--continue`；`fulltest.sh:54-67` **动态**读 `settings.gradle.kts` 的 include 条目数、不写死数字（只出 `::warning::`、不改退出码），故**没有「模块数 13 改 14」这回事**（初稿有误已订正，§1.1/§1.4-12a）；真正要实跑的是 `arch-guard.sh` G9 三方一致。`fulltest` 三假象（残留 XML / 孤儿 gradle 持锁 / `BUILD FAILED` 只是既有基线失败）先排除；多 agent 并发跑 gradle 会产生不可信红（W6 铁律 1）⇒ **收口必须单人串行**。
9. ⚠️ **身份/许可证**：commit 身份以本仓 `AGENTS.md` §九为准（`263587600+Rickeal-Boss@users.noreply.github.com`）—— ⚠️ 与知识库 §5 的 `15992567646@139.com`（「绝不用 noreply」）**冲突**，以**本仓契约**为准并回写知识库；Apache-2.0 本仓 / 旧仓可复用，Operit LGPL / Operit2 AGPL 零搬运。
10. 🔴 **长档结论的单次观测分叉（reasoning 消耗不可控）**：W6 / W7 同 `ptok=994 620`、同题面、同 `max_tokens=4096`，却一个 `⛔vacuous`（`completion=4096` 全被 reasoning 吃光）一个 ✅（`ctok=187 / finish=stop`）。⇒ **995k / 1M 档必须重放 ≥2 次**才允许写稳定结论；`finish_reason` 与 `completion_tokens` 必须同记（`w7-provider-matrix.md` §十.2）。**不许**写成「1M 能力已达上限」「本波解除了 vacuous」「模型能力变了」，也**不许**归因到 `max_tokens` 变化（已撤回，§1.4-9）。
11. ⚠️ **切换守卫字符标定二档误用**：无真值却用 ceiling 6.0 ⇒ 把该禁的切换放行（危险方向，可能发出必然 400 的请求）；有真值却恒用 floor 1.8 ⇒ 把本可放行的 1M 切换禁掉（与需求 1 冲突）。两条反例锚必须**同时**进 `SwitchGuardTest`（§2.3 / P13），只给一条等于没给。

**不做清单（本波明确不碰）**
- W7-A 摘要请求复用主前缀（与 `EngineBackedSummarizer` 硬约束 1 冲突，§3.5）。
- (c) 尾部追加摘要 / 改写压缩器结构（本波行数做不起，列 W8-01）；microcompact（W8-01）、stable/dynamic 分段（W8-02）、多厂商 usage 三桶归一（W8-03）、反应式压缩（W8-05）、append-only 事件溯源（P2）。
- 为 MCP 放开 `usesCleartextTraffic`（W5-03 裁决）；为音频放开 `speak`（W3-01 裁决）；为凑缓存注水 system（w25 铁律）。
- 「release 包看不到导入入口」判据（W7-03/W7-06①，本仓不可执行）。
- 独立 W6.5 的 journal / FreezeClock（W3-05）；`autoApproveDangerous`（W5-05）、`EngineCapabilities`（W6-02）本波只给限期裁决。
- 改 `TOTAL_LINE_CAP`、调任何单文件/模块行数上限；引入注解处理器 / Retrofit / 图片库 / Hilt-Koin；`LlmEngine.generate()` 挪出 `core-agent/loop/`。

**G18 边界核实**【实测】：主体判据 = 引擎调用点只在 `core-agent/loop/`（`arch-guard.sh:1138-1162`，带 VACUOUS 自证），另有**两个显式登记的合法例外** `core-data/.../(RemoteEngineBridge|EndpointProbe).kt`（`:266-270`）。`EngineBackedSummarizer.kt:70` 在 loop/ 内调用合规。派单「只允许在 `core-agent/loop/`」是主体规则的简写，执行时按白名单实况，别把两个例外误判成违规。

---

## 10. 开波顺序（哪些必须串行）

> 用户硬约束：**先本地 UI 及功能测试 + adb 调试 → 真机无问题 → 才推仓库及 CI 构建**。

| 阶段 | 内容 | 串行 / 并行 | 出口判据 |
|---|---|---|---|
| **S0 · 前置 —— ✅ 已完成（2026-11-04，主理人单人串行跑完，全绿）** | `assembleDebug` BUILD SUCCESSFUL 1m4s / 270 tasks；`fulltest` 666/0/0/0、81 XML、覆盖 14 模块（`core-context` 51 条）、XML mtime 全 19:37；`arch-guard` OK=25 VACUOUS=1 VIOLATION=0（G9 `include=14==14==14`）；`selftest` 28/28 PASS 178s；三个 exit code 全 0。日志在工作区根 `_w7/{assemble,fulltest,archguard,selftest}-w7.log`。工作树 `D context/* + ?? core-context/` 为**未提交中间态**（本波由主理人统一提交），不等于未验证 | **已串行完成，S1 可开波** | 全部出口判据已满足：`VIOLATION=0`、`TOTAL_LINE_CAP=42500` 未变、全量单测绿且 XML mtime 全为本轮、G9 三值齐 |
| **S1 · 并行开发（≤4 席）** | A 内核（策略类+纯函数）/ B UI（分流+提示+确认框）/ C 脚本守卫（新守卫+selftest+台账）/ D 文档真机（本文件+设备脚本） | **可并行，文件域互不重叠**；`AppContainer.kt` 全程只读，改动留到 S2 | 各席报「新增 `@Test` 数」给 C 席，别各自改 `EXPECTED_TESTS` |
| **S2 · 本地收口（必须单人串行）** | 合并 → `AppContainer.kt`/`RunnerKey` 若必须改由 C 席单人落笔 → `./scripts/localbuild.sh test` → `./scripts/fulltest.sh` → `arch-guard.sh`（**仓库根目录跑**）→ selftest；W7-04 三件同提交 | **串行**（W6 铁律 1：多 agent 并发 gradle 不可信红） | 全量 `658/0/0`（或新增后同步对账）、守卫 `VIOLATION=0`、无模块 `NO-SOURCE`、行数闸门全绿 |
| **S3 · 本地 UI + adb 调试** | debug 包装到设备（唤醒→install 三步法）；UI 点按 + `logcat -f` 设备侧落盘；验 P1/P4/P11 的 UI 面 | **串行**（adb 命令串行；沙箱 server 每命令后退出） | journal/应用日志判据齐；`input text` 只用 ASCII |
| **S4 · 真机重放（依赖 S3 无问题）** | ① 给 `nv1` 录密钥 + 切 active + 连接自证；② W7-01（32K 实发 ≤ 27 904）、W7-02（三连发+事实表）同批；③ W7-03 手动压缩/导入双向断言；④ P5 1M 题面 push + 设备侧行使；⑤ P6–P14 能力面与守卫回归（音频/MCP 如实标 ⛔）；⑥ **995k / 1M 档同参数重放 ≥2 次**（同 `max_tokens=4096`、记 `finish_reason` + `completion_tokens`）后才写稳定结论 | **串行**（NV 1M 请求 110–136 s，不并发；写入延迟需按第 N 次观测；两次重放之间排除前次污染，知识库 §4） | 每条判据四态落盘；`⛔vacuous` 不得写成 ✅；单次 ✅ 不得写成产品承诺 |
| **S5 · 才推仓库 + CI** | 源码 + 台账 + 文档**一起**推 main（纯文档单独推 = 0 run 不是绿）；`git push --dry-run` 真判据；核 CI 的 `EXPECTED_TESTS` 对账 job | **串行，最后** | CI run 真实存在（不是 0 run）+ Build 成功 + 对账 rc=0 |
| **S6 · 澄清回填 —— ✅ 已完成（2026-11-04）** | **结论来源 = 用户两问裁决，由主理人 2026-11-04 转达并回填**：① §3.2 用户明确接受 (a)，(b) 保留未选用；② §2.1 用户自定义示例「320k 上下文 / 256k 目标模型 ⇒ 直接禁止，除非压缩至范围内」⇒ 确认解读 A，切更大窗口装得下则放行，「先压缩到范围内再切」为合法恢复路径（§2.4）；③ 关自动压缩同时停两处、手动压缩不受影响。已分别写进 §3.2 / §2.1 / §2.4 / §3.3.1 / P14，**不留作待办** | **已完成，不占开波关键路径** | 结论已落盘于本文件（知识库 §6「记忆 ≠ 交付物」）；下一波 handoff 只需引用本节，不必重问 |

**必须串行的三条**：S0（拆分为一切前置，✅ 已完成）、S2（gradle 收口单人）、S4→S5（真机无问题才推仓）。
**可并行的**：S1 四席按 §7 文件域；S6 已完成，不再并行问询。

---

## 附录 A · 证据链索引（本席回原文核过的行号）

| 结论 | 文件:行（实测） | 修订 |
|---|---|---|
| `send()` / history 重建 | `feature-chat/ChatViewModel.kt:268` / `:278` | `c67f37d` |
| 工具轨迹进 history（I1 已修） | `feature-chat/ChatHistoryMapper.kt:39-76`（尤其 `:54`, `:75`） | `c67f37d` |
| 三类终态共用 `finish` | `feature-chat/EventReducer.kt:62/63/64` | `c67f37d` |
| `finally` 无条件置 streaming | `feature-chat/ChatViewModel.kt:395-403` | `c67f37d` |
| 命中率已上屏 / 三态 | `feature-chat/EventReducer.kt:291-303`；`EventReducerTest.kt:299/311/328`（断言 `:307/317/333-336`） | `c67f37d` |
| 手动压缩三条前置拒绝 / 账行 | `feature-chat/ConversationOps.kt:86-90` / `:109-113` / `:129-134` | `c67f37d` |
| 压缩必失效前缀 | `feature-chat/ManualCompress.kt:34-40` | `c67f37d` |
| 内核终态四出口 | `core-agent/loop/RunTermination.kt:27/59/86/108/128`；`TurnLoop.kt:80-84` | `c67f37d` |
| 五种 `TerminationReason` | `core-model/AgentEvent.kt:6-27`；`Finished`:102、`Failed`:121、`Cancelled`:129、`UsageUpdated`:97 | `c67f37d` |
| cached / ReasoningDelta 解析 | `core-engine/provider/OpenAiCompatibleAdapter.kt:106-108` / `:141-142` | `c67f37d` |
| 前缀指纹四段 / INFO 账 / WARN | `core-agent/loop/ContextBudget.kt:264-280`（段 `:299-303`）、`:324-336`、`:341-348` | `c67f37d` |
| L2 摘要器独立 system + 无 tools | `core-agent/loop/EngineBackedSummarizer.kt:20-22,56-63,113-120` | `c67f37d` |
| `LayeredContextCompressor` 已接线 | `core-agent/loop/ContextBudget.kt:7,115`；`ContextBudgetTest.kt:254,277,283` | `c67f37d` |
| `WindowContextCompressor` 类位置 | `core-context/.../ContextCompressor.kt:83`（`:33` TURN_ALIGNED、`:50` 0.6、`:162` safeCutIndex） | 拆分后工作树 |
| `RunnerKey` / 切换写入点 | `core-data/RunnerCache.kt:21-32,68-77`；`AppContainer.kt:256-275,277-280,304-345,370-377`；`feature-settings/SettingsViewModel.kt:118-123,177-182,191-196` | `c67f37d` |
| 窗口字段真实语义 | `core-model/RemoteEndpoint.kt:105-127`（`:124-125` 唯一消费点）；`core-kernel/policy/AgentPolicy.kt:190` | `c67f37d` |
| `compressContext` 全链已接 | `AppContainer.kt:261-272`；`SettingsViewModel.kt:158,191-196`；`SettingsScreen.kt:197`；`SettingsRoute.kt:120` | `c67f37d` |
| 导入上限（8 MiB / 4 Mi 字符 / 5000 条） | `core-data/SessionImportParser.kt:53,56,67,129-139,263-272` | `c67f37d` |
| 占位符字节稳定（无随机/时间戳） | `core-context/.../HardBudgetShrinker.kt:70-78,120-136`（`placeholderOf(chars)` 纯函数） | 拆分后工作树 |
| 拆分后模块/守卫基线 | `settings.gradle.kts`（14 模块）；`scripts/arch-guard.sh:144-166,187-208,266-270,299-304,1138-1162` | CI 席工作树中间态 |
| **模块数是动态口径，不写死** | `scripts/fulltest.sh:54-67`（`grep -hE '^\s*include\s*\(' settings | wc -l`，只出 `::warning::` 不改退出码）；G9 三方一致在 `arch-guard.sh:709-715` | `c67f37d` |
| `CJK_SCAN_PATHS` 已补（含两处历史漏项） | `scripts/arch-guard.sh:311-314`（`core-kernel` + `core-context` + `feature-tools` + 原 9 个）；G16 等价预检 55/1/16 行 CJK 命中全 0【CI 席回报 + 本席回树核】 | CI 席工作树中间态 |
| G20 判据不判合计数 | `scripts/arch-guard.sh:1295-1299,1365-1373,1399`（handoff ID 存在性 / `CLOSED` 证据非空 / 状态三值 / ID 唯一） | `c67f37d` |
| CI 触发与测试基线 | `.github/workflows/build.yml:48-57,99-135,448-526` | `c67f37d` |
| 台账挂账全文 | `docs/06-deferred-ledger.md:56-98`（表格）、`:100-108`（计数注释）、`:112-119`（快照统计） | `c67f37d` |
| 字符硬预算 / 标定二档 | `core-context/.../BudgetFormula.kt:31-36`（`hardContextBudgetTokens` 含 `window/2` 兜底）、`:59-64`（`hardCharCap` / `charCapOf`）、`:73-87`（`effectiveCharsPerToken`） | 拆分后工作树 |
| 两种自动压缩的开关与状态字段 | `core-data/SettingsRepository.kt:69-97`（默认 true、手动压缩不受约束在 `:91-92`）；`feature-chat/ChatUiState.kt:181-186`（`contextTokens`/`contextLimit`/`usageLabel`）、`:201-210`（`compressedUpTo`/`compressedSavingTokens`） | `c67f37d` |
| 本波多模型矩阵（一手落盘文档） | `docs/notes/w7-provider-matrix.md`：`:45`（512 只属 probe、阶梯 4096）、`:95-111`（§4.1 七档表 + 1M 档结论）、`:145-149`（dots 400）、`:183/198`（缓存三连发/分块仍 vacuous）、`:288-290`（§十.2 长档须记 finish + ctok）——**实测 314 行 / 22 306 B，`??` 未跟踪** | 开发席本波落盘 |
| W7 草稿引用 | `docs/notes/w7-context-cache-roadmap.md`（未跟踪，全文 173 行） | 未跟踪 |

> ⚠️ 本附录所有行号均为**本席在 `c67f37d` + CI 席拆分工作树**上 grep/`wc -l` 反查所得。最终提交后行号可能漂移，执行前须再核一次（知识库 §6）。
> 🔴 **证据等级订正（2026-11-04，主理人指出本席路径找错）**：`_w7/matrix-nv-vl-ladder.json`、`_w7/requests.jsonl`、`_w7/w7matrix.py`、`_w6/matrix.py` **不在 `harness-v2/` 仓库根，而在工作区根 `D:/WBfil/2026-09-18-18-27-37/_w7/` 与 `_w6/`**。本席先前在仓库根 `ls` 找不到就一律降级为【中转】，是错的。本席已回**工作区根**核实文件全部存在（`matrix-nv-vl-ladder.json` 1617 B、`requests.jsonl` 23 809 B、`w7matrix.py` 12 815 B、`_w6/matrix.py` 11 048 B、`device-baseline.json` 3219 B）。⇒ 这些标 **【实测·主理人直接读盘】**（主理人逐条核过 `max_tokens=4096`、阶梯七档、OR 预检拒算术、判据自证）+ 本席回盘核存在与抽验（ladder 七档 `hit=true`、四闸门日志原始数字），**不再降级**。`docs/notes/w7-provider-matrix.md` 为开发席落盘的一手文档（实测 314 行 / 22 306 B / `??` 未跟踪；主理人先读的 248 行是较早版本，**以 314 行为准**）。设备基线数字来自开发席 exec-out 读回，其载体 `device-baseline.json` 同在工作区根、已核存在。
