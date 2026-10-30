# 05 — 搬迁清单（Port Manifest）

> 目的：关掉 `04-risk-register.md` 的 **R16（新旧仓分叉）**。
> 一次性冻结「从旧仓搬了哪些文件、搬的是哪个版本、改了什么」，之后旧仓进入维护冻结。
> 旧仓基线：`Android-Agent` @ 分支 `final-cut`。行数为实测（`wc -l`）。
> 四个动作：**照搬**（逐字节）/ **改造**（结构保留、逻辑有明确变更）/ **拆分**（一文件拆多）/ **丢弃**。

---

## 一、`:core-model`（旧 32 文件 4360 行 → 新约 12 文件 1400 行）

| 旧文件 | 行 | 动作 | 新位置 / 变更 |
|---|---|---|---|
| `Role.kt` | 11 | 照搬 | `Role.kt` |
| `Ids.kt` | 8 | 照搬 | `Ids.kt` |
| `ChatMessage.kt` | 26 | 照搬 | `ChatMessage.kt`（字段不变） |
| `Generation.kt` | 48 | 改造 | `Generation.kt`：`TokenUsage` 加 `cachedPromptTokens`；`GenerationChunk` 被 `GenerationEvent` 取代（见 `03-contracts.md` §3.1） |
| `SamplingParams.kt` | 40 | 改造 | 去掉端侧字段（`topK` 保留，`repPen` 保留但默认关闭；`seed` 保留） |
| `TokenEstimator.kt` | 61 | **照搬** | `TokenEstimator.kt` —— CJK 分语种是硬挣来的（KDoc 记录了「系统性低估中文 2~3 倍」的实证），一字不改 |
| `StreamAccumulator.kt` | 119 | **照搬** | `StreamAccumulator.kt` —— 含 `DeltaTracker` 的模式锁定，注释里那段「累积式 vs 增量式判不出来」的推演直接搬 |
| `ToolSpec.kt` | 103 | 改造 | `ToolSpec.kt`：加 `source: ToolSourceKind` / `namespace: String?`；`ToolResult` 加 `displayOutput` / `indeterminate` |
| `ToolDisclosureMode.kt` | 44 | 照搬 | `ToolDisclosureMode.kt` |
| `ToolDisclosure.kt` | 475 | 改造 | 拆 `ToolDisclosure.kt`（元工具 spec + 检索评分）+ `DisclosureGuide.kt`（提示词文本，走 i18n）。触发从「用户选」改「工具数 > 24 自动」（`03-contracts.md` §五 #24） |
| `AiCapabilityMode.kt` | 57 | 照搬 | `AiCapabilityMode.kt` |
| `TruncateSafe.kt` | 44 | 照搬 | `TruncateSafe.kt`（代理对安全截断，会话列表预览要用） |
| `AgentLogStore.kt` | 214 | 改造 | `AgentLogStore.kt`：**`secretPattern` + `sanitizeUserFacing()` 原样保留并扩容到 8 类**（旧 `:61-66`） |
| `AgentLog.kt` / `AgentLogSink.kt` | 34+27 | 照搬 | 同 |
| `Conversation.kt` | 41 | 照搬 | 同（`toMeta()` 里的代理对安全截断注释一并搬） |
| `Attachment.kt` / `Base64ByteArraySerializer.kt` | 74+25 | 照搬 | 同（图片输入 W6 才接线，先留） |
| `InferenceConfig.kt` | 62 | 改造 | 删端侧字段（`backend`/`nativeToolChannel`/`thinkingTokenBudget`），留 `systemInstruction`/`contextLength`/`maxTokens`/`sampling` |
| `AgentJson.kt` | 26 | 照搬 | 同 |
| `ModelDescriptor.kt` | 145 | ⛔ 丢弃 | 无模型可管 |
| `ModelPresets.kt` | 756 | ⛔ 丢弃 | 14 条端侧预设 |
| `ModelHealthCriteria.kt` | 315 | ⛔ 丢弃 | 端侧自检 |
| `ModelHeuristics.kt` | 313 | ⛔ 丢弃 | 容器 section 推断 |
| `ModelSamplingProfiles.kt` | 300 | ⛔ 丢弃 | 按模型档案钳采样（端侧 KV 预算驱动） |
| `ModelPresetSchema.kt` / `ModelGpuSupport.kt` / `InferenceBackend.kt` / `ModelModality.kt` / `ThinkingMode.kt` | 64+46+21+31+15 | ⛔ 丢弃 | 端侧概念 |
| `PersonaPresets.kt` | 79 | ⛔ 丢弃 | 人格档位，W8 若要再做（当前不在路线图） |
| `StreamRepetitionDetector.kt` | 736 | **拆分** | `StreamRepetitionDetector.kt`（句级重复，约 300 行）+ ⛔ 丢弃 prompt-echo 指纹半边（约 430 行，0.5B–4B 病理） |
| — | — | 新增 | `RemoteEndpoint.kt` / `ProviderKind.kt` / `ReasoningStyle.kt` / `AgentEvent.kt` / `SkillManifest.kt` / `McpServerConfig.kt` / `SecretRedactor.kt` / `TextRef.kt` |

**丢弃小计：约 2 620 行**（端侧 model）+ 430（echo 指纹）= **3 050 行**。

---

## 二、`:core-agent`（旧 41 文件 8989 行 → 新约 30 文件 6500 行）

### 2.1 主循环（旧 `AgentRunner.kt` 2838 行 → 16 文件）

| 旧区间（`AgentRunner.kt`） | 行数 | 动作 | 新位置 |
|---|---|---|---|
| `146-178` `freezeAwareTimeout` | 33 | 照搬 | `loop/FreezeAwareTimeout.kt` |
| `208-440` 证据/判定纯函数（`wallClockElapsedPhrase` / `wallClockSoftEvidence` / `wallClockHardEvidence` / `repeatSignatureVerdict` / `intraLoopReminderFor` / `thinkingOnlyEmptyDiagnostic`） | 233 | 照搬 | `loop/RunEvidence.kt` |
| `442-539` 类头 / `run` / `runUnlocked` / `executeBody` | 98 | 改造 | `AgentRunner.kt`（门面）：`engineFactory` 参数换成 `LlmEngine`；取消收尾的 `NonCancellable` 逻辑**原样保留**（`522-537` 那段 KDoc 是 Wave2 真实缺陷换来的） |
| `541-1004` `executeBodyUnchecked` | 464 | **拆分** | `loop/TurnLoop.kt`（骨架 ≤250）+ 其余按下方各行拆出 |
| `1045-1048` `nativeToolsDrifted` | 4 | ⛔ 丢弃 | 远程无「会话级 native 通道证伪」概念 |
| `1052-1172` `gateRoundHead` | 121 | 改造 | `loop/RoundHeadGate.kt`：墙钟 SOFT/HARD 保留，热闸删除，加 token 硬上限 |
| `1173-1424` `accountSendTokens` | 252 | 改造 | `loop/TokenAccounting.kt`：保留发送侧记账，**加 usage 校准与门控**（推翻 W73-01，见 `03-contracts.md` §五 #10） |
| `1425-1694` `runGenerationRound` | 270 | 改造 | `loop/GenerationRound.kt`：引擎交互换成 `LlmEngine.generate` + `GenerationEvent`；「生成失败 → 重建引擎 + 重试一次」改为「可重试错误 → backoff 重试」（**无引擎可重建**） |
| `1695-2179` `executeSingleToolCall` | 485 | **拆分** | `loop/ToolCallExecutor.kt`（编排 ≤350）+ `loop/ToolApprovalFlow.kt`（审批 + 拒绝熔断 ≤250）+ `loop/ToolResultCommit.kt`（截断 + 落盘 ≤150） |
| `2180-2361` `handlePostStreamSignals` | 182 | 照搬 | `loop/PostStreamSignals.kt` |
| `2362-2497` `handleNoToolCalls` | 136 | 照搬 | `loop/NoToolCallHandler.kt` |
| `2498-2639` `emitFinished` / `emitBreakerFailed` / `buildBottleneckReportFor` | 142 | 照搬 | `loop/RunTermination.kt` |
| `2640-2654` `rebuildEngine` | 15 | ⛔ 丢弃 | 无引擎生命周期 |
| `2655-2688` `commitToolMessage` | 34 | 照搬 | `loop/ToolResultCommit.kt` |
| `2689-2747` `runDisclosureSearch` / `resolveDisclosureCall` | 59 | 照搬 | `loop/ToolDisclosureFlow.kt` |
| `2748-2823` `emitDisclosureSearch` / `emitUnregisteredTool` / `emitRepeatedCallIgnored` / `emitToolFailure` | 76 | 照搬 | `loop/ToolEventEmitter.kt` |
| `2824-2838` `toolCallSignature` / `canonicalizeJson` | 15 | 照搬 | `loop/ToolSignature.kt` |

**改造的总原则（一句话）**：`AgentRunner` 里凡是「因为引擎在进程内」而存在的分支（重建引擎、native 通道漂移、会话诊断、生成超时重建），**全部删除而不是改成 no-op**。这是 A4 与 R4 的同一条纪律。

### 2.2 工具与审批

| 旧文件 | 行 | 动作 | 新位置 / 变更 |
|---|---|---|---|
| `Tool.kt` | 56 | 照搬 | `tool/Tool.kt`（`ToolContext` 去掉 `agentMemory` 字段，改由工具实现注入端口） |
| `ToolRegistry.kt` | 64 | 改造 | `tool/ToolRegistry.kt`：`registerContributed` 从「同名 warn 覆盖」改「**同名拒绝 + error 日志**」（MCP 防抢注，见 `03-contracts.md` §五） |
| `ToolArgsValidator.kt` | 173 | 照搬 | `tool/ToolArgsValidator.kt` |
| `ToolArgs.kt` / `FileArgNormalizer.kt` | 23+21 | 照搬 | 同 |
| `ToolExecutionGuard.kt` | 93 | 照搬 | `tool/ToolExecutionGuard.kt` |
| `approval/ToolApprovalCache.kt` | 120 | **照搬** | `approval/ToolApprovalCache.kt`（key 含档位维度的理由、拒绝不缓存、`argsDigest` 规范化重编码 —— 全部原样） |
| `approval/ToolApprovalHandler.kt` / `ToolApprovalDecision.kt` | 32+? | 改造 | 加 `ApprovalRequest`（含 `argsDigest` / `displaySummary`）与 `ApprovedForSession`（见 `03-contracts.md` §四） |
| `tools/CalculatorTool.kt` | 137 | 照搬 | `tool/tools/CalculatorTool.kt` |
| `tools/DateTimeTool.kt` | 46 | 照搬 | 同 |
| `tools/FileTools.kt` | 581 | **拆分** | `tool/tools/FileReadTool.kt` / `FileWriteTool.kt` / `FileListTool.kt`（各 ≤220） |
| `tools/SystemTools.kt` | 72 | 照搬 | `tool/tools/ClipboardTool.kt`（含 `ParamGatedTool`） |
| `DefaultTools.kt` | 24 | 改造 | `tool/DefaultTools.kt`：6 个内置工具不变（注释里「没有任何网络类工具」的理由**必须重写** —— 新仓有网络，理由是「工具面不含任意 URL 抓取」） |
| `schema/…`（`ToolArgsValidator` 所在包） | 173 | 照搬 | 见上 |

### 2.3 上下文 / 账本 / 熔断

| 旧文件 | 行 | 动作 | 新位置 / 变更 |
|---|---|---|---|
| `ContextCompressor.kt` | 174 | **照搬** | `context/ContextCompressor.kt` —— `WindowContextCompressor` 的「工具组原子切点」与 `sanitizeForProvider` 的「稳定派生 id」两段 KDoc 必须逐字搬（前者防 400，后者顺带保 prompt cache） |
| `token/RunTokenLedger.kt` | 167 | 改造 | `token/RunTokenLedger.kt`：从「读侧投影」升为「门控真值源」（见 `03-contracts.md` §五 #10，含旧 KDoc 里 W73-01 判死结论的**订正说明**） |
| `breaker/BreakerKind.kt` | 58 | 改造 | `breaker/BreakerKind.kt`：删 `Thermal`，加 `TokenBudget` |
| `breaker/BreakerLedger.kt` | 142 | 照搬 | 同 |
| `breaker/ToolOscillationDetector.kt` | 109 | 照搬 | 同 |
| `breaker/BottleneckReport.kt` | 252 | 改造 | 同（删端侧专属归因项） |
| `FreezeClock.kt` | 108 | 照搬 | `loop/FreezeClock.kt` —— ⚠️ 搬但**未在 API 路径验证**（`03-contracts.md` §五 #19） |
| `thermal/RunThermalGate.kt` | 44 | ⛔ 丢弃 | 无发热源 |
| `TextToolProtocol.kt` | 286 | ⛔ 丢弃 | 远程全有原生 tool 通道；保留 = 造第二条执行路径 |
| `AgentRunnerLoopHelpers.kt` | 235 | 照搬 | `loop/LoopHelpers.kt`（提醒文案常量族；i18n 化后改为 `TextRef`） |
| `AgentRunnerToolSetup.kt` | 108 | 改造 | `tool/ToolSetup.kt`：删 native 通道三重门，加「工具数 > 阈值 ⇒ ON_DEMAND」 |
| `AgentPolicy.kt` | 41 | 改造 | `policy/AgentPolicy.kt`：数字全部重定（见 `03-contracts.md` §四），旧值写进 KDoc 便于审计 |

### 2.4 记忆 / 计划 / 子代理

| 旧文件 | 行 | 动作 | 新位置 / 变更 |
|---|---|---|---|
| `memory/AgentMemory.kt` | 422 | 照搬 | `memory/AgentMemory.kt` |
| `memory/MemorySearch.kt` | 232 | 照搬 | 同 |
| `memory/MemoryTools.kt` | 224 | 照搬 | 同 |
| `plan/AgentPlanStore.kt` | 342 | 照搬 | `plan/AgentPlanStore.kt` |
| `subagent/AskSubagentTool.kt` | 288 | **改造** | `subagent/AskSubagentTool.kt`：去掉 `runUnlocked` 绕锁技巧，改显式 `depth + 1` 与独立 token 预算；`approvalCache = null` / `tokenLedger = null` 的「子 run 不继承」纪律**保留** |
| `subagent/SubagentDefinition.kt` / `BuiltInSubagents.kt` / `SubagentSessionStore.kt` | 125+59+151 | 照搬 | 同 |
| `journal/AgentRunJournal.kt` | 368 | **照搬** | `journal/AgentRunJournal.kt` —— append-only JSONL，零引擎假设。但「已完成推理轮复用」语义**不启用**（远程无 re-prefill 成本可摊），在类头 KDoc 里加一句说明 |

### 2.5 丢弃

| 旧文件 | 行 | 理由 |
|---|---|---|
| `history/SegmentedHistoryStore.kt` | 134 | 零生产调用点（KDoc `:16-22` 自陈 + grep 确认 test 源集也零引用） |
| `history/ContentAddressedPool.kt` | 64 | 同上 |
| `history/TurnRecord.kt` | 59 | 同上 |
| `history/TurnFold.kt` | 76 | 同上 |
| `thermal/RunThermalGate.kt` | 44 | 无发热源 |

**core-agent 丢弃小计：约 1 100 行**（文本协议 286 + history 333 + 热保护 44 + echo 指纹半边 430 + native 漂移/重建引擎 19）。

---

## 三、`:core-data`（旧 30 文件 6818 行 → 新约 10 文件 4500 行）

| 旧文件 | 行 | 动作 | 新位置 / 变更 |
|---|---|---|---|
| `AppContainer.kt` | 861 | **拆分** | `AppContainer.kt`（≤200 只持有与生命周期）+ `EngineGraph.kt` / `AgentGraph.kt` / `UiGraph.kt`（各 ≤200） |
| `SettingsRepository.kt` | 391 | 改造 | 只留端点相关；DataStore 键名沿用（避免迁移） |
| `ConversationRepository.kt` | 217 | 照搬 | 同 |
| `ConversationCache.kt` | 84 | 照搬 | 同 |
| `JsonFileStore.kt` | 200 | 照搬 | 同 |
| `PersistenceGuard.kt` | 94 | 照搬 | 同（旧仓 W73-04 P0 事故的产物：整文件结构闸 + 写前闸） |
| `AgentLogFileStore.kt` | 255 | 照搬 | 同（滚动 + 脱敏） |
| `AttachmentStore.kt` | 87 | 照搬 | 同 |
| `StorageUsageStore.kt` | 127 | 照搬 | 同 |
| `LegalDocuments.kt` | 136 | 照搬 | 同 |
| `Sha256Verifier.kt` | 94 | 照搬 | 同（端点/技能包校验也会用） |
| `DeviceCapability.kt` | 110 | 改造 | 只留「内存/ABI/SDK」查询，删模型相关 |
| `notify/GenerationNotifier.kt` | 243 | 照搬 | 同 |
| `background/*`（8 文件） | 223+161+155+132+114+95+75+… | **改造** | 只留 `AgentForegroundService` + `RunWakeLockKeeper` + `ScreenStateMonitor`（FreezeClock 需要）；**OEM 引导 4 文件丢弃**（`OemBackgroundPolicy` / `BackgroundRestrictionStatus` / `oem/OemBackgroundData` 等）—— MCP 无常驻连接，不需要厂商保活引导 |
| `perf/*`（4 文件） | 479+208+70+… | 照搬 | 帧统计（W8 判据 4 要用） |
| `thermal/ThermalGovernor.kt` | 320 | ⛔ 丢弃 | 无发热源 |
| `ModelRepository.kt` / `ModelDownloader.kt` / `SandboxFileScanner.kt` / `WallpaperStore.kt` | 475+463+357+345 | ⛔ 丢弃 | 端侧模型管理 / 下载 / 壁纸 |
| — | — | 新增 | `EndpointStore.kt` / `EndpointSecretStore.kt`（Keystore 信封）/ `SkillInstallStore.kt` / `McpServerStore.kt` |

---

## 四、`:core-design`（旧 40+ 文件 11749 行 → 基本原样）

**全量搬。** 这是本仓最大的免费资产，也是唯一一块「不改一行就能用」的。

保留清单（按旧仓行数）：`LiquidBottomTabs` 1095 / `DampedDragAnimation` 745 / `GlassSegmented` 609 / `GlassSlider` 604 / `GlassSurface` 533 / `CyberJoystickSwitch` 478 / `OverlayBackdropBlur` 399 / `DrawBackdropModifier` 392 / `InteractiveHighlight` 379 / 其余。

唯一改动：守卫 G7（设计系统零业务依赖）继续生效 ⇒ 搬的时候如果发现它引用了 `core.model` 的类型（例如颜色令牌里嵌了 `ModelModality`），把那部分换成 `core-design` 自己的枚举。

**行数台账（实测，Verified as of 2026-10-30）**：`core-design` main 源集**实测 12 417 行**（旧仓 11 749 行，差额来自拆 import / 补 KDoc / 包结构调整）。
- G10 的模块总量上限 **12 600 是按这个实测值校准的**，**不再上调**（上限跟着实测走 = 上限失去意义）。
- 当前余量 **183 行**（12 600 − 12 417）。
- **W2U-2 拆分 `LiquidBottomTabs.kt` 之前，本模块不得再进新代码。** 再进就意味着有人要为它挤掉别人的余量，而余量只剩 183 行 —— 那不是预算，是事故倒计时。

---

## 五、`:feature-chat`（旧 5650 行 → 新约 3500 行）

| 旧文件 | 行 | 动作 | 新位置 / 变更 |
|---|---|---|---|
| `ChatRunCoordinator.kt` | 1253 | **拆分** | `RunLauncher.kt`（≤300）+ `EventReducer.kt`（≤350）+ `TurnPersister.kt`（≤200） |
| `ChatViewModel.kt` | 901 | **拆分** | `ChatViewModel.kt`（≤300）+ `ChatUiState.kt`（≤250）+ `StreamingState.kt`（≤150） |
| `ChatScreen.kt` | 817 | 拆分 | `ChatScreen.kt`（≤250）+ `ChatInputBar.kt`（搬 225 行）+ `ChatTopBar.kt` |
| `ChatMessageList.kt` | 622 | 照搬 | 同（≤400 需再拆一次气泡渲染） |
| `ToolTraceGroup.kt` | 190 | **照搬** | 同（折叠组设计 + 「RUNNING/FAILED 永不折叠」的判据一并搬） |
| `ChatContextMeter.kt` | 143 | 改造 | 数据源从「估算」换成 provider usage（A8） |
| `ProcessHistory.kt` | 180 | 照搬 | 同 |
| `AssistantPersistence.kt` | 174 | 照搬 | 同 |
| `ChatRunRecovery.kt` | 129 | 照搬 | 同（journal 恢复卡） |
| `ChatAttachmentPicker.kt` | 108 | 照搬 | 同 |
| `ChatParamsSheet.kt` / `ChatParamsPanel.kt` | 466+307 | ⛔ 丢弃 | 端侧采样参数面板（temperature/topK/repPen 全量暴露），API 路径只需 systemInstruction + maxTokens，进设置页 |
| `ChatSpeedIndicator.kt` | 82 | ⛔ 丢弃 | 「tokens/s」在网络流上是**服务端速度**，对用户无意义且会误导 |
| — | — | 新增 | `ApprovalCard.kt`（三按钮）/ `ReasoningBlock.kt`（思考折叠块）/ `EndpointPicker.kt` |

---

## 六、`:app`（旧 3100 行 → 新约 1600 行）

| 旧文件 | 行 | 动作 | 新位置 / 变更 |
|---|---|---|---|
| `LiquidAgentApp.kt` | 1342 | **拆分** | `AppScaffold.kt`（≤250）+ `AppNavHost.kt`（≤200）+ 各页面入口（≤150 每个） |
| `ConversationDrawer.kt` | 449 | 照搬 | 同 |
| `WorkspaceOverlay.kt` | 328 | 照搬 | 同（沙箱文件浏览） |
| `onboarding/ConsentScreens.kt` | 252 | 改造 | 加「数据会发送到第三方 API」的显式同意（R5：叙事让渡必须让用户知情） |
| `onboarding/OnboardingScreen.kt` / `FirstRunGate.kt` | 188+174 | 改造 | 首启流程改为「选预设 → 填 key → 连接测试 → 进对话」 |
| `CrashSceneLogger.kt` | 209 | 照搬 | 同（但要脱敏） |
| `LiquidAgentApplication.kt` / `MainActivity.kt` | 121+37 | 照搬 | 同 |

---

## 七、`:feature-models`（旧 3250 行）→ ⛔ 整模块丢弃

无模型可管。端点管理并入 `:feature-settings`。

---

## 七bis、第五个来源：从 git 历史恢复的远程引擎（1102 行）

不是「旧仓现有文件」，是「旧仓已删除、从 `71dc6e5^` 挖回来的」⇒ 单独列，避免与上面的搬迁清单混淆。

落点：`_recovered_remote/`。逐行裁决见 `01-architecture.md` §十一，这里只给汇总。

| 文件 | 行 | 可复用 | 必改 | 去向 |
|---|---|---|---|---|
| `OpenAiCompatibleEngine.kt` | 547 | ~380 | ~170 | 拆 `core-engine`（adapter + `RemoteLlmEngine` + `RetryPolicy`）+ `core-net`（SSE） |
| `RemoteEndpoint.kt` | 96 | ~55 | ~40 | `core-model`（数据表照搬，类型改名拆分） |
| `EndpointRepository.kt` | 75 | ~50 | ~25 | `core-data`（并发模型照搬，密钥落盘必改） |
| `EndpointsScreen.kt` | 384 | ~300 | ~85 | `feature-settings`（UI 结构照搬） |
| **合计** | **1 102** | **785（71%）** | **320（29%）** | — |

**四段必须逐字保留（KDoc 比代码值钱）**：
1. `activeCall.cancel()` 的 `stop()` —— 不主动 cancel 则 `readLine()` 阻塞到 60s readTimeout，点停止无反应。
2. `readTimeout(60s)` 不能设 0 —— 设 0 时「服务端接受连接后不再返回字节」会永久阻塞。
3. `sentTerminal` / `sawDone` / `chunkCount` 三态补终帧 —— 防「半截回答被当完整回答落库」。
4. `isCancellation()` 区分 `CancellationException` 与 OkHttp 的 `IOException("Canceled")` —— 不区分则「用户停止」显示成「出错了」。

**四个必改（漏一个就有一条判据当场判红）**：

| 必改 | 漏了会怎样 |
|---|---|
| `apiKey` 从 `EndpointSecretStore` 现取，不进 `@Serializable` | W7 判据 2 判红：端点 JSON 里 grep 到 key 原文 |
| 删 `load/unload/close/isLoaded/kind` | 审查判红：A4 的空转判据 |
| 补 `ToolSpec.toDto()` 的 `enum` 字段 | W1 判据 8 判红：带枚举的参数约束丢失，模型产出非法值 |
| 移出 `android.graphics.BitmapFactory`（改到 `core-data`） | `core-engine` 不能 JVM 单测 ⇒ 判据 11 判红 |

---

## 八、汇总

旧仓 main 源集逐模块实测（`find <mod>/src/main/java -name "*.kt" | xargs wc -l`）：

| 模块 | 旧行数 | 照搬 | 改造 | 拆分 | 丢弃 | 新仓目标 |
|---|---|---|---|---|---|---|
| `core-model` | 4 360 | 700 | 400 | — | 3 260 | 1 400 |
| `core-engine` | 4 662 | 0 | 0 | — | 4 200 | 700（**全部新写**） |
| `core-agent` | 8 989 | 3 800 | 1 300 | 2 300 | 1 589 | 6 500 |
| `core-data` | 6 818 | 1 900 | 1 300 | 800 | 2 818 | 4 500 |
| `core-design` | 11 749 | 11 700 | 49 | — | 0 | **12 417（实测）** |
| `feature-chat` | 5 650 | 800 | 350 | 2 400 | 2 100 | 3 500 |
| `feature-models` | 3 250 | 0 | 0 | — | **3 250** | 0（整模块消失） |
| `feature-settings` | 5 636 | 1 200 | 2 400 | 1 000 | 1 036 | 3 000 |
| `app` | 3 100 | 1 000 | 700 | 1 400 | 0 | 1 600 |
| **合计** | **54 214** | **21 100** | **7 499** | **7 900** | **18 253** | **≈ 33 600** |

（行数为实测；「拆分」列与「新仓目标」列之和会大于旧行数，因为拆文件要加 import / KDoc / 参数解构 —— `01-architecture.md` §二 的 39 900 是**上限**，本表 33 600 是**按文件清单估的实际值**（其中 `core-design` 是实测 12 417，其余仍是目标值），两者不矛盾。）

### 两个必须记住的结论

1. **「照搬」的 21 100 行里，`core-design` 占 11 700（56%）。** 也就是说非 UI 代码真正能逐字节搬的只有约 **9 350 行**。
   报告 §一.1 说「可平移面 = core-agent 8 989 + core-model 4 360 = 13 349 行」，高估约 4 000 行 —— 差额正是端侧专属的 `core-model` 3 260 行与 `core-agent` 里随引擎假设一起死掉的 1 589 行。
2. **「丢弃」的 18 253 行里没有一行是「先留着以后可能有用」。** 每一行都有明确理由写在上面第 一~七 节。新仓不接受「暂时留着」的代码 —— 旧仓 91 个 doc / 21 831 行的负债就是这么长出来的（报告 §二）。

### 给实施者的一句话

**照搬优先级 = 有 KDoc 记录「真实炸过」的文件优先。** 按这个标准，W1/W2 必须先搬的是：
`TokenEstimator`（CJK 低估 2~3 倍）→ `StreamAccumulator`+`DeltaTracker`（回答翻倍 P1）→ `ContextCompressor`+`sanitizeForProvider`（400 报错）→ `ToolApprovalCache`（档位越权 P1）→ `ToolRegistry.registerContributed`（名字抢注）→ `FreezeClock`（锁屏误掐）。
这六个文件是本仓唯一「重写一定会重犯同样错误」的部分。

---

## 九、旧仓冻结宣告

从本清单冻结之日起：

1. 新仓是**唯一活跃开发线**。
2. 旧仓只接受 P0（崩溃 / 安全 / 数据损坏）修复，不再接受功能开发。
3. 旧仓的 P0 修复以**文字描述 + commit 哈希**记录在本文件末尾，由新仓决定是否采纳；**不搬代码**（代码已分叉，直接 cherry-pick 会带入端侧假设）。

### 旧仓 P0 修复采纳台账

| 日期 | 旧仓 commit | 问题 | 新仓是否已含 | 处置 |
|---|---|---|---|---|
| （初始为空） | | | | |
