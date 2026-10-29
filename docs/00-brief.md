# 00 — 项目定位与裁决清单

> 仓库：`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`（新仓，空仓起步）
> 旧仓基线：`D:/WBfil/2026-09-18-18-27-37/Android-Agent`（分支 `final-cut`）
> 外审输入：`C:\Users\WB\Desktop\难度与收益评估.md`（下称「报告」）
> 本文所有数字都可回溯到「旧仓文件:行号 / 报告 §x / 实测命令」，推断一律标【推断】。

---

## 〇、文档索引

| 文件 | 内容 | 谁该读 |
|---|---|---|
| `00-brief.md`（本文） | 定位、非目标、十条裁决、与外审报告的分歧 | 所有人先读这一份 |
| `01-architecture.md` | 模块图、依赖拓扑、行数预算、接口签名、时序、错误与取消语义 | 实施者 |
| `02-roadmap.md` | W1–W8 波次、W1 文件级清单、验收判据、测试与门禁基线 | 实施者 + CI 负责人 |
| `03-contracts.md` | Kotlin 契约草案 + **迁移触发矩阵**（哪条判据在新路径下仍可触发） | 实施者 + 审查者 |
| `04-risk-register.md` | 20 条风险，每条带触发信号与缓解动作 | 主理人 |
| `05-port-manifest.md` | 逐文件的搬迁清单（照搬/改造/拆分/丢弃）+ 旧仓冻结宣告 | 实施者 |

---

## 一、定位

**一个跑在 Android 上的 Agent Harness，模型一律来自云端 API。**

三个词要同时成立：

| 词 | 含义 | 反例（不做什么） |
|---|---|---|
| Harness | 提供工具循环、审批、上下文工程、崩溃恢复、可观测性 | 不做「聊天 App」，聊天只是 harness 的一个视图 |
| Android | 真机可用、后台被杀可恢复、OEM 冻结可容忍 | 不做多设备矩阵（只有 1 台真机，见 §五） |
| API-only | 一切推理发生在远端，本机只做编排 | **不做任何端侧推理**（见裁决 A1） |

### 与旧仓的关系

**不是 fork，是重写；旧仓是零件库。**

三个来源，别混：

| 来源 | 是什么 | 裁决 |
|---|---|---|
| 旧仓 `final-cut` 现存文件 | 9 模块 54 214 行 | 逐文件裁决，见 `05-port-manifest.md` |
| 旧仓 git 历史 `71dc6e5^` 恢复的远程引擎 | 4 文件 1 102 行，落在 `_recovered_remote/` | 785 行可复用 / 320 行必改，见 `01-architecture.md` §十一 |
| 行业 harness 调研（`.recon-harness/`） | Operit / Operit2 / Octop / ZCode | **零代码搬运，只取结构思想** |

- 复用方式：同作者同许可（Apache-2.0）⇒ **旧仓自己的 Kotlin 代码可以搬**（含从 git 历史恢复的）。
- 禁止：Operit（LGPL-3.0）/ Operit2（AGPL-3.0）系**零代码搬运**，含改写式、提示词原文、schema、资源；只取结构思想，措辞自写。NVIDIA 参考仓 Apache-2.0，但同样只取思想（避免许可声明链复杂化）。
- `Octop`（`.recon-harness/osrc`）实测为 **MIT**，许可上可搬，但它是 Python/TS 且与本仓风格差异过大 ⇒ 仍只取结构。此判断写在这里是为了避免下一波有人拿「MIT 可以搬」当翻案理由。

### 非目标（明确不做的，写死）

1. **端侧推理**：litertlm / 模型下载 / GPU·NPU 后端 / 模型健康自检 / 14 条端侧预设 —— 全部不进新仓。
2. **MCP Server**：只做 Client。理由见 §三.5。
3. **MCP stdio 传输**：Android 给不了第三方可执行文件 spawn 权限。
4. **插件化代码装载**：Skill 是纯文本，不是代码（这是 W69-09 判死能被翻案的唯一理由，见裁决 A4）。
5. **多设备/OEM 矩阵**：只有 OPPO PDRM00 / Android 13 一台。
6. **定时任务 / 无人值守**：旧仓 W69-08 已判死，新仓不重开。
7. **端侧降级路径**：见裁决 A1。

---

## 二、与外审报告的一致面（采纳，不重复论证）

| 报告条目 | 采纳结论 |
|---|---|
| §三 S1–S5 分阶段、S1–S2 是止损点 | 采纳，映射为 W1 / W2（见 `02-roadmap.md`） |
| §一.3 敏感密钥是收益/成本比最高项 | 采纳，但**提前到 W1**（理由见 A7） |
| §四 风险 2：AgentRunner 余量 62 行，接入即踩线 | 采纳，且给出拆分机制（见 A1/A2） |
| §二 末条：「空转的判据比没有判据更危险」 | **作为全仓第一原则**，逐条申明（见 `03-contracts.md` §五 触发矩阵） |
| §三 裁决 2：中文第一天抽 strings.xml | 采纳，并机械化为守卫 G16（见 A9） |

---

## 三、十条裁决（每条：结论 + 理由 + 反证条件）

> 「反证条件」= 出现什么证据我会改判。没有反证条件的裁决等于信仰。

### A1 — 端侧模型**彻底删除**，不留降级路径

**结论**：新仓不含 litertlm、不含端侧预设、不含 `EngineKind`。

**理由**：
1. 报告 §三 裁决 1 建议「留作无网降级」，**我不同意**。留下的实际代价不是「一个可选路径」，而是 `core-engine` 实测 **4662 行**（其中 `LiteRtLmEngine.kt` 2137 + Loader 717 + Probe 314 + Degrade 216 + Bridge 214）+ `core-model` 端侧专属约 **2600 行**（ModelPresets 756 / StreamRepetitionDetector 736 / ModelHealthCriteria 315 / ModelHeuristics 313 / ModelSamplingProfiles 300 / ModelGpuSupport 46 / InferenceBackend 21 / ModelDescriptor 145）。
2. 旧仓守卫链有 **21 条**行数闸门（`#25` 家族，编号至 `#62`，其 KDoc 自称「#25 家族第 21 员」），其中 3 条钉的是端侧引擎文件（`#25` LiteRtLmEngine 2400 / `#32` Loader 900 / `#36` ThinkingBudget）；另有 `#29` 钉 litertlm 版本 0.17.1。留着降级路径 = 这些守卫全部要搬 = 新仓第一天背上旧仓的版本锁。
3. 最关键：**「留作降级」必然不被测**。报告自己 §二 说「空转的判据比没有判据更危险」。一个从不被执行、从不在真机上验证的降级路径，就是 4662 行 + 2600 行的空转资产。

**反证条件**：用户明确要求「无网可用」并且接受「端侧路径的每次发布都要真机回归」的维护成本 ⇒ 届时以**独立模块** `:core-engine-local` 引入，不与远程路径共享 `LlmEngine` 接口（否则又回到生命周期空转）。

---

### A2 — 模块划分：11 个，`core-agent` 是唯一风险模块

**结论**：

```
core-model  core-net  core-skills  core-mcp  core-design
    ↑          ↑          ↑          ↑         (零业务依赖)
    └──────────┴──────────┴──────────┘
                  ↑
             core-engine
                  ↑
             core-agent      ← 内核（端口在此定义）
                  ↑
             core-data       ← 适配器装配 + 持久化
                  ↑
     feature-chat  feature-settings
                  ↑
                 app
```

**理由**：
1. `core-net` 独立存在，是为了让「全仓禁网络栈」（旧仓守卫 #5）**反向**成「网络栈只允许出现在 core-net」（新守卫 G1）。旧仓 #5 是新仓唯一必须反向的守卫 —— 如果只是删掉它，网络栈会像旧仓注释预言的那样「悄悄回流」到 UI 层。
2. `core-skills` / `core-mcp` 独立，是为了让它们**依赖 core-agent 而不是反过来**（端口与适配器）。这样 `core-agent` 完全不知道 SKILL.md 的格式、也不知道 JSON-RPC 的存在，内核可纯 JVM 测。
3. `feature-models` 消失（无模型可管）；端点/Skill/MCP 管理并入 `feature-settings`。9 → 11 是「-1（feature-models）+3（net/skill/mcp）」。
4. **不用 JitPack、不用注解处理器、不用 DI 框架**（硬约束）：11 个模块的 `build.gradle.kts` 全部手写，`AppContainer` 手写装配。

**反证条件**：若 `core-skills` + `core-mcp` 合计 < 1200 行且零独立测试需求 ⇒ 合并进 `core-agent`（但端口仍必须留在 core-agent，方向不能反）。

---

### A3 — `AgentRunner` 2838 行必须在 W1 就拆开，且拆法不是「切文件」而是「换传参范式」

**结论**：`AgentRunner.kt` 从第一天起 ≤ 200 行（纯门面），主循环拆进 `core-agent/.../agent/loop/` 下 16 个文件，**每文件 ≤ 400 行、每方法 ≤ 120 行**。

**理由**：
1. 旧仓 `AgentRunner.kt` 实测 **2838 行**，守卫 #31 上限 2900，**余量 62 行**。接入 Skill + MCP 必然要动这个文件 ⇒ 第一天踩线。
2. 更紧的是方法级：报告记 `executeBodyUnchecked` **540/550**（守卫 #12，余 10）；我按行号区间量得 `AgentRunner.kt:541-1004`（463 行）。两个数字口径不同（守卫按 `^    }` 边界匹配，会把方法尾部空行与注释一并计入），**但都指向同一结论：余量在两位数以内**。这个方法是 JVM 64KB bytecode 硬上限的近身区（旧仓注释：实测密度约 66 B/行，517 行 ≈ 34.4 KB，见 `arch-guard.sh:199-202`）——**旧仓曾因 `Method too large` 真实炸过两个 job**。
3. 旧仓 W60 自己评估过不拆，理由逐字是「候选接缝 `onSend`/`onToolLoop` 在 `AgentRunner.kt` **实测 0 命中**（须先造）；核心状态机当前不可端到端验证 ⇒ 不拆」。**这两条在新仓都不成立**：
   - 接缝「0 命中」的根因不是缺接缝，是**传参范式错了**：每个 `private suspend fun FlowCollector<AgentEvent>.xxx(...)` 都要 10~12 个参数（实测 `executeSingleToolCall` 的调用点 `AgentRunner.kt:960-972` 有 **11 个实参**），所以任何方法都抽不出去 —— 抽出去就要传 11 个参数。新仓用 `TurnContext` 承载全部上下文，方法签名恒为 `(ctx) -> Step`。
   - 「不可端到端验证」在旧仓成立（native 引擎无法 fake），在新仓**不成立**：引擎边界是一个 HTTP 流，可以用 `FakeEngine`（返回预置 `Flow<GenerationEvent>`）+ 本地假 HTTP server 完全离线验证。

**拆分表**（详见 `01-architecture.md` §四）：`AgentRunner`(≤200) / `TurnLoop`(≤250) / `RunState`(≤150) / `RoundHeadGate`(≤200) / `ContextBudget`(≤200) / `GenerationRound`(≤300) / `StreamSignals`(≤200) / `TokenAccounting`(≤250) / `ToolCallExecutor`(≤350) / `ToolApprovalFlow`(≤250) / `ToolDisclosureFlow`(≤250) / `ToolResultCommit`(≤150) / `PostStreamSignals`(≤250) / `NoToolCallHandler`(≤200) / `RunTermination`(≤250) / `ToolEventEmitter`(≤200)。

**反证条件**：若 W2 结束时任一 `loop/` 文件 > 400 行且无法在不加参数的前提下拆 ⇒ 说明 `TurnContext` 设计不足，先改 `TurnContext` 再拆（不允许「先超一点后面再拆」）。

---

### A4 — `LlmEngine` **重设计**，不保留旧接口；`RemoteEndpoint` 规格改造后沿用

**结论**：新接口去掉 `load/unload/close/evict/sessionDiagnostics/contextVersion/EngineKind`，只留 `capabilities()` + `generate()` + `stop()`。供应商差异用 **`ProviderAdapter` 三族**收敛（OpenAI 兼容 / Anthropic / Gemini）。

**理由**（这是我与报告**分歧最大**的一条 —— 报告 §一.2 说「加 REMOTE = 枚举加一行 + 一个实现类，🟢偏低」）：
1. 报告只看到了「接缝在那儿」，没看到接缝**接错了东西**。旧 `LlmEngine`（`EngineContract.kt:222-275`）的 8 个成员里，`load(EngineLoadConfig)` / `unload()` / `close()` / `sessionDiagnostics` / `EngineCapabilities.supportedBackends` / `supportsSpeculativeDecoding` / `isConcurrentGenerationBusy` 全部是**进程内 native 引擎**的语义。远程实现只能把它们写成 no-op ⇒ 正是报告 §二 自己警告的「空转的判据」。
2. `contextVersion`（`EngineContract.kt:39-49`）的契约原文是「变化时必须关闭旧 Conversation 并全量重建 **KV cache**」。远程侧没有 KV cache ⇒ 契约要么空转要么重新定义。**重新定义的结果恰好等于「无契约」**（HTTP 是无状态的，每次都全量发），所以正确做法是删掉这个字段而不是保留。
3. 「一个实现类」也不成立：OpenAI 兼容族（OpenAI/DeepSeek/Ollama/vLLM/SiliconFlow/自定义）与 Anthropic、Gemini 在**消息编码、工具编码、SSE 事件名、思考通道、usage 位置**五处结构不同。三族 = 三个 adapter，不是三个 `if`。

**`RemoteEndpoint`（报告称存档于 `docs/01-architecture.md:691-776`）的三处必改**：
| 存档字段 | 问题 | 新仓处理 |
|---|---|---|
| `apiKey: String` | 进 `@Serializable` ⇒ 落 DataStore 明文 | **移出**，改 `EndpointSecretStore`（Keystore 信封），`RemoteEndpoint` 只留 `hasSecret: Boolean` |
| `chatCompletionsUrl()` | 硬编码 `/chat/completions` | 改 `ProviderAdapter.endpointUrl(endpoint)`，Anthropic 是 `/v1/messages`、Gemini 是 `/v1beta/models/{m}:streamGenerateContent?alt=sse` |
| `preset: RemotePreset` | UI 概念泄漏进传输层 | 拆成 `providerKind: ProviderKind`（传输）+ `preset: UiPreset?`（只用于填默认值） |

**反证条件**：若 W1 实测 Anthropic/Gemini 可以被 OpenAI 兼容 adapter 以「换个 URL + 加个 header」的方式覆盖 ⇒ 三族降为一族 + 两处 override。

---

### A5 — 内核迁移面：逐类三态 + **每类的触发条件申明**

见 `03-contracts.md` §五 的完整触发矩阵。这里给结论：

| 资产 | 裁决 | API 路径下能否触发 |
|---|---|---|
| `Tool` / `ToolSpec` / `ToolEffect` / `ParamGatedTool` / `EffectAwareTool` / `ToolContributor` / `ToolRegistry` | 原样搬 | ✅ 完全可触发 |
| `ToolArgsValidator`（schema 校验） | 原样搬 | ✅ 可触发（模型产坏参数是常态） |
| `WindowContextCompressor` + `sanitizeForProvider` | 原样搬，**升级为必需** | ✅ 且比旧仓更重要（OpenAI 兼容端点对不配对直接 400，见 `ContextCompressor.kt:19-22`） |
| `TokenEstimator` + `DeltaTracker` | 原样搬 | ✅ `DeltaTracker` 的累积式/增量式模式锁定在代理型 provider 上仍会踩 |
| `ToolApprovalCache` / `ToolApprovalHandler` / `ApprovalDecision` | 原样搬 | ✅ 且因 MCP 更重要 |
| `AgentRunJournal` | 原样搬 | ✅ 进程被杀仍发生；但**「已完成推理轮复用」语义不启用**（远程无 re-prefill 成本可摊） |
| `RunTokenLedger` | **改造后搬，并首次真正启用门控** | ✅ 见下 |
| 断路器：墙钟 SOFT/HARD、同参重复、失败连击、振荡检测 | 改造后搬（数字重定） | ✅ 全部可触发 |
| `FreezeClock` + `freezeAwareTimeout` | 改造后搬（仅服务预算计时） | ⚠️ 待验证（本地路径已验证，远程路径未验证） |
| `memory`（AgentMemory / MemorySearch / MemoryTools） | 原样搬 | ✅ 可触发 |
| `plan`（AgentPlanStore / plan_set / plan_update） | 原样搬 | ✅ 可触发 |
| 子代理（`AskSubagentTool` 等） | 改造后搬（去掉 `runUnlocked` Mutex 技巧，改显式 depth 预算） | ✅ 且更廉价 ⇒ 需新增深度/预算上限 |
| `ToolDisclosure`（FULL/ON_DEMAND + search_tools/call_tool） | 改造后搬（触发从「用户选」改为「工具数 > 24 自动」） | ✅ 且因 MCP 工具爆炸更必要 |
| `TextToolProtocol`（286 行，文本协议兜底） | ⛔ **丢弃** | 远程 provider 全部有原生 tool 通道；保留 = 为工具调用造第二条执行路径，违反旧仓自己的红线（`AgentRunner.kt:776-781`） |
| `RunThermalGate` / `ThermalGovernor`（热保护四档） | ⛔ **丢弃** | 无本地推理 ⇒ 无发热源。留着 = 永久空转 |
| `history_v2`（`SegmentedHistoryStore` / `ContentAddressedPool` / `TurnRecord` / `TurnFold`，333 行） | ⛔ **丢弃** | 旧仓实测**零生产调用点**（`SegmentedHistoryStore.kt:16-22` KDoc 自陈「W87 更新：全仓零生产引用」；`grep` 确认 test 源集也零引用；W80-09 / W83-11 挂账） |
| `StreamRepetitionDetector`（736 行） | 改造后搬，**砍掉 prompt-echo 指纹半边** | 保留句级重复检测；删「模型复述系统提示词」指纹（那是 0.5B–4B 的病理，`AgentRunner.kt:634-650` 有完整实证） |
| 端侧专属 `core-model` 资产（ModelPresets / ModelHealthCriteria / ModelHeuristics / ModelSamplingProfiles / ModelGpuSupport / InferenceBackend / ModelDescriptor） | ⛔ 丢弃 | 无模型可管 |
| `SegmentedHistoryStore` 的「内容寻址池」 | ⛔ 丢弃 | 见上；历史持久化改用 `ConversationRepository` 形态 |

**`RunTokenLedger` 的改判（推翻旧仓 W73-01）**：
- 旧仓 W73-01 终裁「closed-dead：账本只做读侧投影，门控口径不接」，重启前提是「出现估算失准事故」。
- **新仓该前提由构造满足**：API 路径下 provider 回报的 `usage` 是权威值（OpenAI `stream_options.include_usage`、Anthropic `message_delta.usage`、Gemini `usageMetadata`），而 `TokenEstimator` 的 CJK 启发式（`TokenEstimator.kt:26-42`）在代码/中英混排上系统性偏差；且 token 直接等于钱。
- 因此 `RunTokenLedger` 从「读侧投影」升级为「压缩触发 + 单 run 硬上限」的唯一真值源。旧仓判死的另一半理由（接线的 `runMutex` 重入风险）在新仓不存在：账本是 `RunState` 的普通字段，由 `RoundHeadGate` 读，不回调循环。

**反证条件**：若 W3 实测 provider usage 在流式下缺失或明显错误（> 20% 偏差）⇒ 账本退回「估算为主、usage 只做展示」，并把「单 run 硬上限」改为按估算算。

---

### A6 — Skill 系统：纯文本 + 三级披露，**正文永不进 system**

**结论**：Skill = `SKILL.md`（扁平 YAML frontmatter + Markdown 正文）+ 可选 `resources/`。三级披露为：元数据常驻 system → 正文经 `skill_load` 以 **user 角色**注入 → 资源经 `skill_read` 按需读。

**理由**：
1. 报告 §一.4 说「注入点现成（`AgentPromptComposer.sections`），三级披露可直接挂进这套 sections 逻辑」——**注入点确实现成，但「正文挂进 sections」是错的**。`buildSystemSections`（`AgentPromptComposer.kt:132-193`）的产物是本 run 的系统提示词；若技能正文进 system，则每次加载技能都改变 system 前缀 ⇒ **整条会话的 prompt cache 全部失效**。这是本地路径不存在、API 路径必然存在的成本（见 A8）。
2. 「纯文本、零执行语义」是 W69-09 判死能被翻案的唯一支点。旧仓判死原文三条理由里，只有第二条（插件 = 任意代码执行安全面）被 Skill 消除；第一条（端侧无外部生态）和第三条（工具面收窄已由 disclosure 满足）在新仓需要重新面对 —— **MCP 就是新仓的外部生态**，所以第一条不再是反对理由；第三条由 disclosure 继续承担，Skill 不重复解决。
3. `allowed-tools` 只做**交集收窄**，绝不放宽（对齐 `AiCapabilityMode` 的「只收紧不放宽」，`AgentEvents.kt:190-195`）。这是把 Skill 从「权限提升面」降级为「权限收窄面」的关键。

**反证条件**：若 W4 真机实测「正文以 user 角色注入」导致模型把技能当作用户指令而越权 ⇒ 改为 `<skill>` 标签包裹 + 在 user 消息前加一条固定 system 边界声明（不用 system 承载正文的结论不变）。

---

### A7 — 密钥存储提前到 W1；五处泄漏面双侧脱敏

**结论**：API Key 走 `AndroidKeyStore` 的 AES-256-GCM 信封（手写 ~80 行，不引入 `androidx.security:security-crypto`），`RemoteEndpoint` 序列化体不含密钥。脱敏覆盖**五处**：用户输入 / 模型输出 / 工具参数（仅展示侧）/ HTTP 头 / 截图上屏。

**理由**：
1. 报告把密钥排在 S3（第 3 步）——**顺序错了**：S1 本身就要存一个 key 才能跑，没有存储方案 S1 就得把 key 写在源码或明文文件里，之后每多一天都是负债。
2. 不引 `androidx.security:security-crypto`：它是 Jetpack 里公认停止维护的库（1.1.0 之后无更新），且硬约束已禁一切非必需依赖。minSdk 31 ⇒ `KeyGenParameterSpec` / `setRandomizedEncryptionRequired` 全部可用，手写反而更可控、可测（`SecretCipher` 接口 + JVM fake）。
3. 「工具参数脱敏」必须**只脱展示不脱执行**：模型让 `file_write` 写一个 key 到文件是合法需求，脱了执行就坏功能。所以 `ToolResult` 需要 `displayOutput`（脱敏）与 `output`（原文）双字段。

**反证条件**：若 W7 实测 KeyStore 在 OPPO PDRM00 冷启动首次解密耗时 > 300ms 且阻塞首屏 ⇒ 改为「懒解密 + 内存缓存 + 首屏不阻塞」。

---

### A8 — 上下文工程的第一目标不是省 token，是**保住 prompt cache**

**结论**：system 段 + 工具 schema 在**一条会话内必须字节稳定**；压缩是「缓存失效事件」，要稀有且有门槛。

**理由**：
1. 这是 API 路径相对本地路径的**范式差异**：本地没有缓存计费，提示词改一个字只多一次 re-prefill（秒级）；远程改一个字就是整段前缀按全价重算。Anthropic 显式 `cache_control` 断点、OpenAI 自动前缀缓存（≥1024 token）都要求前缀逐字节一致。
2. 由此推出三条硬规则：
   - 系统提示词**每 run 渲染一次**（旧仓在 `executeBodyUnchecked` 内也是循环外，`AgentRunner.kt:638`，行为一致，但要把「每轮重算」这个未来诱惑写死为禁止）。
   - 技能正文、工具结果、提醒消息一律追加在**尾部**，不插入中段。
   - `sanitizeForProvider` 的补丁消息用**稳定派生 id**（旧仓 `ContextCompressor.kt:159-166` 已这么做，但动机是引擎水印去重）—— 这个选择在 API 路径上额外保住了前缀稳定，必须保留并注明理由变了。
3. 压缩门槛：`budget = (min(endpoint.contextLength, policy.maxContextTokens) - maxOutputTokens) × 0.75`。旧仓 `compressThreshold = 0.75`（`AgentPolicy.kt:21`）沿用，但 `contextLength` 的分母从「litertlm KV 预算」变成「端点声明窗口」，且必须叠 `maxContextTokens`（默认 32000）——否则 1M 窗口的模型会真的发 75 万 token 出去。

**反证条件**：若 W6 实测两家 provider 的缓存命中率在「技能正文以 user 注入」下仍为 0 ⇒ 说明缓存判据不是前缀匹配，重估 A6 的注入位置。

---

### A9 — 门禁基线：从 62 条砍到 18 条，行数闸门**表驱动**

**结论**：`arch-guard.sh` 重写，起始 18 条（14 条改造搬 + 4 条新建），每条必须有 selftest case。**不搬**旧仓的端侧专属守卫与「已消失文件」的行数守卫。

**理由**：
1. 旧仓 `scripts/arch-guard.sh` 实测 1745 行、编号到 **#62**。其中 #24/#28/#29/#33（litertlm）、#34/#39/#41（ModelPresets）、#21（CapabilitySource）、#43~#46（后台保活/OEM）在新仓**没有对应对象**，搬过来就是死规则。
2. #25 家族（行数上限）在旧仓有 **20 个成员**，一个文件一条 `check` 调用。新仓改成**表驱动**：一个 `line_cap table` + 一个循环 + 一个 selftest case 覆盖「超一行判红 / 缺文件判红」。20 条 → 1 条，覆盖面不降。
3. 新增 4 条针对新风险的守卫（G1 网络栈收口、G16 中文硬编码棘轮、G17 `@Serializable` 禁密钥字段、G18 引擎调用点收口），详见 `02-roadmap.md` §五。

**反证条件**：若某条新守卫在 W2/W4 出现 2 次以上假红 ⇒ 按旧仓纪律「假红比漏报更伤守卫公信力」（`arch-guard.sh:13-16`）改判据或降为 advisory，不允许「加豁免注释绕过」。

---

### A10 — 波次：W1 是**能跑起来的垂直切片**，W1+W2 = 报告的 S1+S2 止损点

**结论**：W1 真机跑通「一个端点 + 纯文本流式 + 密钥已加密落盘」；W2 跑通「6 个内置工具 + 审批 + 压缩」。**W2 结束是止损判定点**，未达标不进 W3。

**理由**：报告 §三 的核心假设「工具调用不稳的根因是模型能力，不是 harness」若被证伪，MCP 与 Skill 的收益要整体重估。旧仓有现成的对照基线：W74-01 记录「Qwen2.5-1.5B 在单工具上即 8 轮重复」⇒ 端侧多轮工具调用的基线约等于 0。

**可量化的止损判据**（替代报告「若工具调用成功率显著高于端侧」这句不可执行的话）：
> 20 条脚本化单工具题面，用默认端点模型跑，**单工具调用一次成功率 ≥ 90%**（18/20），且**无一条出现 8 轮不收敛**。
> 未达标 ⇒ 停，重估方向与预算。

**反证条件**：若 W1 的流式在真机上就有多字节截断/丢帧等基础问题未解决 ⇒ 止损点后移到 W1 修完为止（不允许带着基础缺陷做 W2 的判定）。

---

## 四、我不同意外审报告的地方（汇总）

| # | 报告原文 | 我的判断 | 依据 |
|---|---|---|---|
| D1 | §一.2「加 REMOTE = 枚举加一行 + 一个实现类，🟢偏低」 | 🟡中：接口必须重设计，供应商是三族不是一族 | A4 理由 1–3。**恢复代码实证支持**：`OpenAiCompatibleEngine.kt:90-118` 的 `load()` 只做三个 if 校验、`unload()` 只置一个布尔、`capabilities()` 里 `supportedBackends = emptySet()` —— 全是为满足旧接口而写的空转实现 |
| D2 | §一.1「可平移面 = core-agent 8989 + core-model 4360 = 13349 行」 | 实际可搬约 **8000 行**；端侧专属 core-model ≈2600 行、core-agent 的文本协议 286 + 热保护 44 + history 333 全丢 | A5 表 |
| D3 | §三 裁决 1「端侧模型留作无网降级」 | **不同意**，删干净 | A1 理由 1–3 |
| D4 | §二「确定收益：保住离线与隐私底线」 | 「离线」这条在新仓不成立（无端侧模型），不该计入收益 | A1 |
| D5 | §一.4「三级披露可直接挂进 sections 逻辑」 | 注入点对，但正文**不能**进 system（prompt cache） | A6 理由 1 |
| D6 | §三 S1 判据「提问→回答这条腿跑通」 | 太弱：ASCII 短句跑通掩盖不了跨 chunk UTF-8 截断。加三条硬判据 | `02-roadmap.md` W1 |
| D7 | §四 风险 5「MCP 双 era 兼容 🟡」 | 降为 🟢：era 差异只在握手（~60 行）；真风险是**工具描述注入**与**SSRF** | `04-risk-register.md` R6/R7 |
| D8 | §三 S3 密钥排在工具之后 | 提前到 W1（S1 本身就需要存 key） | A7 理由 1 |
| D9 | 报告未提 | **新增风险：压缩/补丁消息破坏 prompt cache** | A8 |
| D10 | 报告未提 | **新增风险：MCP baseUrl 指向内网 = SSRF** | `04-risk-register.md` R7 |

---

## 五、设备与验证面（如实申报）

| 项 | 值 | 出处 |
|---|---|---|
| 唯一真机 | OPPO PDRM00 / Android 13（SDK 33） | 任务硬约束 |
| OEM 冻结 | ColorOS 灭屏 ~10s 即 cgroup 级冻结，实测一次冻结 **416 秒**；FGS + WakeLock + 电池豁免**六项控制变量全无效** | 报告 §一.5（L3 已知限制） |
| 安装方式 | 必须 `adb push` + `pm install -r`；`adb install` 报 -99 | 知识库 `real-device-forensics.md` §1 |
| 判据来源 | journal / 设备侧落盘日志；`uiautomator dump` 对 Compose bounds 不可靠 | 同上 §3/§5 |
| 未验证面 | 其他 OEM、Android 14/15/16、多供应商 × MCP × Skill 组合 | 报告 §一.6 |

**由此产生的第一条设计约束**：任何依赖「进程常驻 / 连接常驻」的设计在目标设备上不可行 ⇒ MCP 必须**每次调用独立 HTTP 请求、无常驻连接**（见 `01-architecture.md` §七）。

---

## 六、硬约束清单（违反即方案作废）

- **包名 / applicationId = `com.rickeal.liquidharness`**（不是旧仓那个 `com.rickeal.*` 命名空间）；debug 构建加 `.debug` 后缀（`com.rickeal.liquidharness.debug`），便于与旧 App 同机共存做 A/B
- 各模块 Kotlin 包前缀 = `com.rickeal.liquidharness.<模块名>`，映射表见 `01-architecture.md` §二
- Kotlin 2.3.0 / AGP 9.3.2 / Gradle 9.7.1 / compileSdk 36 / minSdk 31 / JVM target 17
- 禁：KSP / Room / Hilt / Dagger / Koin / Retrofit / Coil / 一切注解处理器 / 一切 JitPack
- 网络层：**OkHttp 传输 + 手写 SSE 帧解析**（不用任何 SSE 库、不用 Retrofit）；JSON 用 `kotlinx.serialization`
- DI 只走 `AppContainer`（手写）
- 许可证：旧仓代码可搬（同作者 Apache-2.0）；Operit 系零代码搬运
- 中文文案第一天进 `strings.xml`，由守卫 G16 机械拦
- 真机 1 台，不得假设多设备矩阵
