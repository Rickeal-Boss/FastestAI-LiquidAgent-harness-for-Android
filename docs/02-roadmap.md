# 02 — 波次路线图（W1–W8）

> 每波三段式：**目标 / 交付物 / 验收判据（可量化）**，另加**风险**与**不做清单**。
> 「验收判据」必须能在真机或 CI 上跑出真假，不接受「功能正常」这种话。
> 真机 = OPPO PDRM00 / Android 13（SDK 33），唯一设备。

---

## 零、总览

| 波 | 主题 | 止损性质 | 结束时的可演示状态 |
|---|---|---|---|
| **W1** | 垂直切片：一个端点、纯文本、流式上屏 | **S1（报告的止损点）** | 真机输入「你好」看到流式输出 |
| **W2U** | UI 原样搬 + 网络安全 + 外审 P0 收口（插入波） | — | 真机看到旧仓那套液态玻璃界面，出网安全在保存时拦截 |
| **W2** | 工具循环 + 审批 + 上下文压缩 | **S2（核心假设证伪点）** | 真机让模型读文件、弹审批卡、回灌结果、给出答案 |
| W3 | 内核硬化：journal 恢复、熔断、token 上限、诊断 | — | 杀进程重启弹恢复卡；注入超预算触发熔断 |
| W4 | Skill 系统 | — | 内置技能生效；技能正文以 user 注入且 system 不变 |
| W5 | MCP Client（双 era） | — | 接 1 个真实 server，拉到工具并调用成功 |
| W6 | 多供应商 + 上下文工程二期 | — | 三家 provider 各跑通一次工具调用；长会话不丢上下文 |
| W7 | 安全收口 | — | 8 种密钥形态在 5 个出口全脱敏；内网 baseUrl 被拒 |
| W8 | 打磨与发布 | — | release APK 真机冷启动 < 2s，i18n 100% |

**W1 + W2 = 报告的 S1 + S2。W2 未达标不得进 W3**（见 §一 W2 的止损判据）。

---

## 一、W1 — 垂直切片（能跑起来的，不是脚手架）

### 目标

真机上：填一个端点 → 输入一句话 → 看到流式输出。**没有工具、没有 Skill、没有 MCP、没有历史持久化**。
一切「以后要加的东西」在这一波**只留接口，不留实现**。

### 交付物

**Gradle 骨架（**11 个模块目录全部建齐**，其中 3 个只有 build 文件 + 空 res）**

```
settings.gradle.kts        include 11 个（按依赖拓扑）：
                             core-model core-net core-engine
                             core-agent core-skills core-mcp
                             core-data core-design
                             feature-chat feature-settings
                             app
gradle/libs.versions.toml  Kotlin 2.3.0 / AGP 9.3.2 / Compose BOM / kotlinx-serialization
                           / okhttp3（仅 core-net 引用）
                           （禁：KSP/Room/Hilt/Koin/Retrofit/Coil；repositoriesMode = FAIL_ON_PROJECT_REPOS）
build.gradle.kts (root)    统一 compileSdk 36 / minSdk 31 / JVM target 17
                           applicationId = com.rickeal.liquidharness（debug 加 .debug）
```

**W1 建齐 11 个模块目录，不建空壳以外的任何东西。** 其中 W1 只有 build 文件 + 空 `res/` 的三个模块：

| 模块 | W1 状态 | 理由 |
|---|---|---|
| `core-mcp` | 只有 `build.gradle.kts` + `AndroidManifest`（空） | W5 才实现，但**目录必须先在**：守卫 G9（模块数一致）要求 `settings.gradle.kts` 的 include 数与实际目录数、脚本 `MODULES` 数组三者相等；晚建一天，G9 就要多改一次 |
| `core-skills` | 同上 | W4 才实现，同上 |
| `feature-settings` | `build.gradle.kts` + 空 `res/values/strings.xml` | W1 的设置页只有「端点编辑」一个入口，放在 `app` 里；W2 起迁入 |

> **口径说明（与 A2 对齐）**：`00-brief.md` A2 的反证条件写的是「若 core-skills + core-mcp 合计 < 1200 行且零独立测试需求 ⇒ 合并进 core-agent」。**「W1 就建目录」不等于「W1 就写实现」** —— 建空目录是为了让模块覆盖从第一天起就是完整的（CI 的模块矩阵、守卫 G9、`assembleDebug` 的依赖图都不会在 W4/W5 突然变宽），这与「不留空壳」不冲突：空壳是「有代码但没人用」，这里的空目录是「结构已就位、实现待填」。
>
> **不要为了「消灭空壳」而删这三个目录** —— 删了 CI 的模块覆盖反而变窄，且 G9 会在 W4 重报一次红。

### 文件级任务清单

#### `:core-model`（目标 ≤ 900 行）

| 文件 | 内容 | 上限 |
|---|---|---|
| `Role.kt` | `enum class Role { SYSTEM, USER, MODEL, TOOL }` | 15 |
| `Ids.kt` | `newId()`（UUID） | 15 |
| `ChatMessage.kt` | 照搬旧仓（`core-model/ChatMessage.kt`，26 行，字段不变） | 40 |
| `ToolSpec.kt` | 照搬 + 新增 `source: ToolSourceKind`、`namespace: String?` | 130 |
| `Generation.kt` | `FinishReason` / `TokenUsage`（+`cachedPromptTokens`）/ `SamplingParams` | 120 |
| `RemoteEndpoint.kt` | 新写（A4）：`id/name/baseUrl/modelId/providerKind/preset/contextLength/maxOutputTokens/supportsTools/supportsReasoning/extraHeaders/hasSecret`。**无 apiKey 字段** | 130 |
| `AgentEvent.kt` | UI 层事件（见 `03-contracts.md` §三） | 150 |
| `StreamAccumulator.kt` | 照搬（含 `DeltaTracker` 的模式锁定，旧仓 119 行） | 150 |
| `TokenEstimator.kt` | 照搬（CJK 分语种，旧仓 61 行） | 70 |
| `SecretRedactor.kt` | 新写：8 类正则 + `redact()` | 120 |
| `AgentJson.kt` | 照搬（`Json { ignoreUnknownKeys = true; explicitNulls = false }`） | 30 |
| `TextRef.kt` | 新写：i18n 资源引用 | 30 |

#### `:core-net`（目标 ≤ 450 行）

| 文件 | 内容 | 上限 |
|---|---|---|
| `HttpTransport.kt` | **OkHttp** 封装：`post(url, headers, body): ResponseStream`；connect/read 超时；`Call.cancel()` 幂等；自定义 `Dns` 实现做解析后 IP 校验（防 DNS rebinding） | 180 |
| `SseEventParser.kt` | **字节级**分帧：按 `\n\n` / `\r\n\r\n` 切帧，帧内按 `\n` 切字段，`data:` 拼接多行，`:` 开头为注释。**每帧完整后再 UTF-8 解码** | 150 |
| `UrlGuard.kt` | SSRF 校验：scheme 必须 https（或 localhost/10.0.2.2 白名单），host 不得是 RFC1918/link-local 除非端点显式允许 | 120 |

> 🔴 `SseEventParser` 是 W1 唯一「写错了会静默出错」的组件。三条必须有单测：
> ① 一个 UTF-8 汉字被切在两个 TCP chunk 之间 ⇒ 输出完整汉字，无 U+FFFD；
> ② 一条 `data:` 跨 3 行 ⇒ 拼接正确；
> ③ `data: [DONE]` 后不再产生事件，且后续垃圾字节不抛异常。

#### `:core-engine`（目标 ≤ 700 行）

| 文件 | 内容 | 上限 |
|---|---|---|
| `ProviderKind.kt` | 枚举 + `fromPreset()` | 50 |
| `ProviderAdapter.kt` | 接口（见 `01-architecture.md` §3.2） | 90 |
| `OpenAiCompatibleAdapter.kt` | 唯一的 W1 adapter：URL、headers、body、`decodeEvent`、`finish` | 280 |
| `RemoteLlmEngine.kt` | 实现 `LlmEngine`：`generate()` = buildBody → post → parse → decode → emit；重试与取消 | 250 |
| `RetryPolicy.kt` | 可重试判定 + 退避（500/1500ms + jitter）+ `Retry-After` | 90 |

#### `:core-agent`（目标 ≤ 1200 行，只做纯文本路径）

| 文件 | 内容 | 上限 |
|---|---|---|
| `engine/LlmEngine.kt` | 端口（见 §3.1） | 120 |
| `engine/EngineCapabilities.kt` | 能力声明 | 40 |
| `policy/AgentPolicy.kt` | 照搬旧仓 41 行 + 新增 `maxContextTokens` / `wallClockSoftMillis` / `wallClockHardMillis` / `maxRunPromptTokens` | 90 |
| `tool/Tool.kt` | 照搬（`Tool` / `ToolContext` / `ParamGatedTool` / `EffectAwareTool`） | 90 |
| `tool/ToolRegistry.kt` | 照搬 64 行 + `registerContributed` 改为**同名拒绝**（MCP 防抢注，见 A5） | 90 |
| `tool/ToolSpecSource.kt` | `ToolSource` 端口 | 60 |
| `loop/TurnContext.kt` | 见 §3.4 | 120 |
| `loop/RunState.kt` | 可变状态 | 150 |
| `loop/TurnLoop.kt` | 主循环（W1 只有「生成 → 无工具 → 收尾」） | 200 |
| `loop/GenerationRound.kt` | 单轮生成 + 重试 | 280 |
| `loop/RunTermination.kt` | `emitFinished` / `emitFailed` | 150 |
| `prompt/AgentPromptComposer.kt` | 照搬旧仓 202 行，去掉 `nativeTools` 分支（远程恒原生）、去掉 `DISCLOSURE_GUIDE`、加 `SKILLS_SECTION`（W1 传空） | 180 |
| `AgentRunner.kt` | 门面：`run()` / `runUnlocked()` / `isBusy` / 取消收尾 | 200 |
| `log/AgentLogStore.kt` | 照搬（`secretPattern` + `sanitizeUserFacing`，旧仓 `AgentLogStore.kt:61-66`） | 150 |

#### `:core-data`（目标 ≤ 700 行）

| 文件 | 内容 | 上限 |
|---|---|---|
| `AppContainer.kt` | 手写 DI：`engineFactory` / `settings` / `secrets` / `logStore` | 200 |
| `SettingsRepository.kt` | DataStore 封装（照搬旧仓 391 行的核心子集，只留端点相关） | 200 |
| `EndpointStore.kt` | 端点 CRUD + 预置三组（OpenAI / DeepSeek / Ollama 本机） | 150 |
| `EndpointSecretStore.kt` | **Keystore 信封**：AES-256-GCM，`SecretCipher` 接口 + `AndroidKeyStoreCipher` 实现 | 150 |
| `AgentLogFileStore.kt` | 照搬核心子集（滚动文件 + 脱敏） | 150 |

#### `:core-design`（目标：搬）

直接搬旧仓 `core-design` 全部（11749 行）。W1 只需要 `GlassSurface` / `GlassSegmented` / `LiquidBottomTabs` 三个能编译，其余随目录进来但不接线。

#### `:feature-chat`（目标 ≤ 900 行）

| 文件 | 内容 | 上限 |
|---|---|---|
| `ChatScreen.kt` | 顶栏 + 消息列表 + 输入栏 | 250 |
| `ChatMessageList.kt` | 气泡渲染 + `StreamingState` 订阅 | 250 |
| `ChatViewModel.kt` | `ChatUiState` + 发送/停止 | 200 |
| `RunLauncher.kt` | 组装 `AgentRequest`、开 run | 150 |
| `EventReducer.kt` | 纯函数归约 + 120ms 节流 | 200 |

#### `:app`（目标 ≤ 500 行）

| 文件 | 内容 | 上限 |
|---|---|---|
| `LiquidAgentApplication.kt` | 照搬骨架（121 行） | 130 |
| `MainActivity.kt` | 照搬（37 行） | 50 |
| `AppScaffold.kt` | 底栏 + 内容区（**不要**像旧仓 `LiquidAgentApp.kt` 那样长到 1342 行） | 200 |
| `AppNavHost.kt` | 两个目的地：chat / settings（W1 settings 只放端点编辑） | 150 |
| `res/values/strings.xml` | W1 全部文案（目标 ~60 条） | — |

#### 脚本与 CI

| 文件 | 内容 |
|---|---|
| `scripts/arch-guard.sh` | W1 版：12 条（见 §五），每条带 selftest case |
| `scripts/arch-guard-selftest.sh` | W1 版：12 个 case（现为 23 个，见 §5.2），batch 模式跑完 < 60s（旧仓 93 case 跑 52 分钟，不可接受） |
| `scripts/fulltest.sh` | 全模块单测 + 汇总口径（`grep -rnE "^\s*@Test\b"`） |
| `.github/workflows/ci.yml` | assembleDebug + test + arch-guard + lint + 密钥扫描 |

### 验收判据（W1 全绿才算完成）

| # | 判据 | 验证方式 | 阈值 |
|---|---|---|---|
| 1 | 真机流式输出 | 手动 + `adb logcat` 抓 TTFT | 输入「你好」→ 3s 内出现首字，全文完整 |
| 2 | 多行 `data:` 拼接 | JVM 单测 | 一个事件跨 2 个 `data:` 行 ⇒ 拼成一条 JSON 解析成功（**不是**被 `catch { continue }` 吞掉） |
| 3 | `event:` 字段可见 | JVM 单测 | `event: content_block_delta` + `data: {...}` ⇒ `SseEvent.event` 非空（为 W6 Anthropic 预留） |
| 4 | 长输出不截断 | JVM 单测 | 单测喂 3000 个 SSE 帧 ⇒ `accumulator.text` 长度 == 预期 |
| 5 | 中途取消 | 真机 | 点停止 ⇒ 连接断开（服务端侧无残留请求）、`Cancelled(partialText)` 保留已收内容 |
| 6 | 补终帧（半截流） | JVM 单测（假 server 在第 N 帧后直接断连） | 收到内容但无 `[DONE]` ⇒ 补 `LENGTH` 终帧 + warn，**不得**落库成完整回答 |
| 7 | TOOL 消息内容 | JVM 单测 | `role=tool` 消息的 `content` 取自 `toolResults.output`，不是空串 |
| 8 | 工具 schema 的 enum | JVM 单测 | 带 `enumValues` 的 ToolSpec ⇒ 序列化后 JSON 里有 `enum` 数组（恢复代码丢了这段） |
| 9 | 密钥加密落盘 | JVM 单测 + `adb shell` 查文件 | 端点 JSON 里 `grep` 不到 key 原文；密文文件存在 |
| 10 | 错误文案 | JVM 单测（`FakeEngine` 注入 5 类错误） | 5 类错误的用户文案与 `01-architecture.md` §6.1 表逐字一致 |
| 11 | 单测数量 | `scripts/fulltest.sh` | `@Test` 严格口径 ≥ **45** |
| 12 | 守卫 | `arch-guard.sh` | 12/12 绿，selftest 12/12 PASS |
| 13 | CI | GitHub Actions | 全 job 绿 |
| 14 | 构建矩阵 | `./gradlew :app:assembleDebug` | 无 warning-as-error 失败；APK 可 `pm install -r`（applicationId `com.rickeal.liquidharness.debug`） |

### 风险

| 风险 | 触发信号 | 缓解 |
|---|---|---|
| SSE 字节级分帧写错，只有 ASCII 能跑 | 中文输出偶发 `U+FFFD` 或截断 | 判据 #2/#3 是硬门，不通过不许进 W2 |
| Keystore 在 OPPO 上行为异常 | `KeyStore.getKey` 抛 `UnrecoverableKeyException` | 降级：`setUserAuthenticationRequired(false)` + 不设 `setInvalidatedByBiometricEnrollment`；再不行退到「应用内密钥 + 文件权限」并如实申报（不是加密） |
| Gradle 9.7.1 + AGP 9.3.2 组合有未知坑 | 同步阶段就失败 | 旧仓同矩阵已实证可编译 ⇒ 复制其 `gradle.properties` / `libs.versions.toml` 的版本值，不自己猜 |

### W1 明确不做

历史持久化（只存内存）、工具、Skill、MCP、多端点、审批、压缩、journal、i18n 完整化（只做 W1 用到的 60 条）、深色模式切换。

---

## 一bis、W2U — UI 原样搬 + 网络安全 + 外审 P0 收口（2026-10-29）

> 本波是 W1 与 W2 之间的**插入波**：W1 交付的是「能跑」的 Material3 简易界面，
> 而旧仓的液态玻璃界面是已经过 90 波真机打磨的资产。在接工具循环之前先把界面搬正，
> 理由有二：① 后续 W2/W3 的审批卡、工具轨迹、诊断卡都要挂在同一套视觉语言上，
> 界面晚搬一次就要把每个新组件重写两遍；② 外审两份报告指出的 P0 全部是「判据没接线」，
> 它们与 UI 无关但与 W2 的止损归因强相关 —— 带着空转判据去跑 20 条题面，
> 失败分不清是模型不行还是 harness 有 bug。

### 目标

真机上打开 App 看到的是旧仓那套液态玻璃界面（底栏 / 横屏 rail / 抽屉 / 玻璃气泡 /
工具轨迹组），对话链路走的是新仓的纯 API 内核；出网安全在**保存时**就拦，
不再等到发请求才报错。

### 交付物

| 面 | 内容 | 来源 |
|---|---|---|
| `:core-design` | 设计系统全量搬入（Glass* / Liquid* / 动效 / 交互），包名改 `com.rickeal.liquidharness.core.design` | 旧仓 `core-design` 全量 |
| `:feature-chat` | 对话页 UI 原样搬：顶栏 / 玻璃气泡 / 流式光标 / 思考折叠块 / 工具轨迹组 / 上下文占用条 / 输入栏 / 错误卡 | 旧仓 `ChatScreen` / `ChatMessageList` / `ChatInputBar` / `ToolTraceGroup` / `ChatContextMeter` |
| `:app` | 外壳：竖屏底栏 / 横屏顶部条 / 中间档 rail、抽屉、转场；设置页玻璃化 | 旧仓 `LiquidAgentApp` / `ConversationDrawer` / `SettingsScreen` |
| `:core-net` | `UrlGuard` 换 `HttpUrl` 同源解析、拒 `userInfo`、连接后 IP 校验（`GuardedDns`）；`HttpTransport` 支持 `allowPrivateNetwork` | 外审 P1-3 + R7 |
| `:app` 声明面 | `usesCleartextTraffic=false` + `network_security_config.xml`（仅 `10.0.2.2` / `127.0.0.1` / `localhost` 允许明文）+ `allowBackup=false`（G15c，备份会带走 Keystore 密文与端点清单） | 外审 P0-1 G15b / gallery#1325 G15c |
| `:core-data` | 保存端点时 `UrlGuard.requireAllowed`；密钥写失败上抛；`hasSecret` 回写；`EndpointProbe` 收紧（HTML-200 不再判通过）；runner 按 endpointId 缓存 | 外审 P0-4 / P1-2 / P1-5 |
| `:core-engine` | 引擎层接 `DeltaTracker`（累积式 provider 不再翻倍）；工具碎片计入「已 emit」；TOOL 多结果展开成 N 条线上消息 | 外审 P0-2 / P0-3 / P1-4 |
| `:core-agent` | 思考通道计入「空输出」判定（两层口径同源） | 外审 P0-2（第二份报告） |
| 门禁 | `build.yml` 增 `arch-guard`（含 selftest）与 `secret-scan` job；`EXPECTED_TESTS` 双向对账；G6 表按文档修正；G18 过滤 bug 修复 + 合法调用点登记 | 外审 P0-1 |

### 验收判据

| # | 判据 | 验证方式 | 阈值 |
|---|---|---|---|
| 1 | 设计系统可编译且零业务依赖 | `:core-design:compileDebugKotlin` + G7 | 0 违规 |
| 2 | 单文件行数闸门 | G10 | 全部 ≤ 表内上限 |
| 3 | 中文硬编码 | G16 | 业务模块 main 源集 = 0 |
| 4 | 对话页视觉一致 | 真机截图对比旧仓 | 底栏/气泡/输入栏形态一致 |
| 5 | 明文流量 | G15b + 真机 Ollama 端点连接测试 | 声明存在；`10.0.2.2` 可达 |
| 6 | SSRF 三类绕过 | 单测 | `user:pass@` / `127.0.0.1@evil.com` / IPv4-mapped 全拒 |
| 7 | 累积式 provider 不翻倍 | 单测（`AdapterStreamStateTest`） | 重复帧被吸收，`textTracker.text` 与下游一致 |
| 8 | 工具碎片后断流不重试 | 单测 | `hasEmittedAny=true` |
| 9 | TOOL 多结果 | 单测 | 3 结果 ⇒ 3 条线上消息、id 一一对应 |
| 10 | 守卫在 CI 跑 | `build.yml` arch-guard job | 绿；selftest 全 PASS |
| 11 | `@Test` | `fulltest.sh` | 只增不减（对账进 CI） |

### 状态（2026-10-30，commit `baeb574`）

CI run `41051392490` 五个 job 全绿：Assemble Debug / Unit tests（`@Test` 228 = `EXPECTED_TESTS`）/
Lint / Secret scan / **Arch guard（22 守卫 0 违规 + selftest 22/22 PASS）**。
（上面的 22 是该 run 在 `baeb574` 时点的真实条数，是**证据记录**，不改写；加 G15c 后当前为 23 条 / selftest 23 个 case。）

| # | 结论 | 证据 |
|---|---|---|
| 1 / 2 / 3 | ✅ | `:core-design:compileDebugKotlin` 通过；G7=0、G16=0；G10 仅剩 1 条**已登记例外**（`LiquidBottomTabs.kt` 1012 行 > 600，拆分排 W2U-2） |
| 6 / 7 / 8 / 9 | ✅ | 单测（`UrlGuardTest` / `AdapterStreamStateTest` / `ToolEventEmitterTest` 等），CI 单测 job 绿 |
| 10 / 11 | ✅ | arch-guard job 绿；`@Test` 228 双向对账通过 |
| 4 / 5（真机部分） | ⚠️ 未做 | 需要 OPPO PDRM00 上跑：截图对比旧仓、`10.0.2.2` Ollama 端点连通、NSC 对 IP 字面量放行、`GuardedDns` 在真机 OkHttp 路径生效 |

**本波顺带修掉的两个守卫结构性失明**（都是「进了 CI 也永远绿」，selftest 抓到）：

1. `guard_violation` 把多行违规块当**一个**实参 ⇒ 例外正则命中块里任意一行就整块跳过。
   G11 / G18 因此对 core-model 的注入完全失明，且**掩盖了两处真实存量**
   （`LiquidDialog()` 227 行、`CyberJoystickSwitch()` 124 行，本轮已拆）。
2. `guard_absent` 只扫 `*.kt`，而 G4a / G5a 的判据钉的是 Gradle 脚本行 ⇒
   `build.gradle.kts` 里的 retrofit、`settings.gradle.kts` 里的 jitpack 永远看不见。
   `KTS_GLOB` 早就定义但从未被使用。

判据本身的经验：**「守卫在 CI 里跑」不等于「守卫在判」**。区别只能由变异自证给出
—— 注入违规后必须看到该守卫转红，且输出区段里出现本次注入的证据。

### 不做清单

- 会话持久化 / 抽屉真实历史（W3）
- 审批回传通道（内核尚无 `ApprovalDecision` 入口，UI 只预留卡片；W2 接工具循环时一起做）
- 上下文压缩接线（W2）
- 附件上传 / 图片输入（W6）
- 模型库、端侧模型管理（整块删除，永不回归）
- MCP / Skill 页面（W4 / W5）

---

## 二、W2 — 工具循环 + 审批 + 压缩（**止损点**）

### 目标

模型能调工具、用户能审批、结果能回灌、上下文能压缩。6 个内置工具 + 1 条执行路径。

### 交付物

**`core-agent` 新增（目标 ≤ 2600 行）**

| 文件 | 内容 | 上限 |
|---|---|---|
| `tool/tools/CalculatorTool.kt` | 照搬旧仓 137 行 | 140 |
| `tool/tools/DateTimeTool.kt` | 照搬 46 行 | 60 |
| `tool/tools/FileTools.kt` | 照搬 581 行，**拆三文件**：`FileReadTool` / `FileWriteTool` / `FileListTool` | 各 ≤ 220 |
| `tool/tools/ClipboardTool.kt` | 照搬（含 `ParamGatedTool` 实现） | 120 |
| `tool/ToolArgsValidator.kt` | 照搬 173 行 | 180 |
| `tool/ToolExecutionGuard.kt` | 照搬 93 行（同参计数 / 失败连击 / 振荡） | 100 |
| `approval/ToolApprovalDecision.kt` | 照搬 | 30 |
| `approval/ToolApprovalHandler.kt` | 照搬 32 行 | 40 |
| `approval/ToolApprovalCache.kt` | 照搬 120 行 | 130 |
| `loop/ToolCallExecutor.kt` | 单次调用流水线编排 | 350 |
| `loop/ToolApprovalFlow.kt` | 审批闸门 + 拒绝熔断（N=2） | 250 |
| `loop/ToolResultCommit.kt` | 截断（READ 16000 / WRITE 2000）+ 落 working + journal 占位 | 150 |
| `loop/ToolEventEmitter.kt` | `emit*` 辅助 | 150 |
| `context/ContextCompressor.kt` | 照搬 174 行（`WindowContextCompressor` + `sanitizeForProvider`） | 190 |
| `loop/ContextBudget.kt` | 压缩 + 版本 bump + 窗口回写 working | 200 |

**`feature-chat` 新增**：`ApprovalCard.kt`（三按钮：允许一次 / 本会话允许相同调用 / 拒绝，≤ 200 行）、`ToolTraceGroup.kt`（照搬旧仓 190 行）。

### 验收判据

| # | 判据 | 验证方式 | 阈值 |
|---|---|---|---|
| 1 | **单工具调用成功率** | 20 条脚本化题面，默认端点模型 | **≥ 18/20（90%）** |
| 2 | **无 8 轮不收敛** | 同上 20 条 | 0 条触发 `MaxRounds` |
| 3 | 审批闸门 fail-closed | 单测：`approvalHandler == null` + 危险工具 | 一律拒绝，`ToolSkipped` 事件带原因 |
| 4 | 参数级审批 | 单测：`clipboard` 的 `set` vs `get`；坏 JSON | `set` ⇒ 弹卡；`get` ⇒ 不弹；坏 JSON ⇒ **弹卡**（fail-closed） |
| 5 | 工具组配对 | 单测：3 种不配对形态（孤儿 result / 缺 result / 两者都有） | `sanitizeForProvider` 输出可被 OpenAI 兼容端点接受（用本地假 server 断言 body 结构） |
| 6 | 压缩不破配对 | 单测：切点落在 TOOL 上 | 切点回溯到宿主 `assistant(toolCalls)`，或整体放弃压缩 |
| 7 | 失败连击熔断 | 单测：同一工具失败 4 次 | 第 4 次 ⇒ `BreakerTripped` |
| 8 | 拒绝熔断 | 单测：同一工具被拒 2 次 | 第 3 次跳过审批直接拒，不再弹卡 |
| 9 | 工具输出截断 | 单测：READ 工具返回 50KB | 截断到 16000 字符，`truncated=true` |
| 10 | 真机端到端 | 真机 | 「读 sandbox/notes.txt 并总结」⇒ 弹卡 → 批准 → 结果回灌 → 有总结 |
| 11 | 单测数量 | fulltest | `@Test` ≥ **130** |
| 12 | 守卫 | arch-guard | **23 条全绿**（`bash scripts/arch-guard.sh` 实跑 `OK=23 VIOLATION=0 SELF_FAULT=0`；W2 当时的口径是 14 条，后续波次陆续加满，见 §六） |

### 🔴 止损判据（未达标不得进 W3）

> **判据 1 + 2 同时成立才继续。**
> 判据 1 < 90% 或判据 2 出现任意一条 8 轮不收敛 ⇒ **停**，重估「工具调用不稳的根因是模型能力」这个假设（报告 §三 核心假设），并重估 MCP/Skill 的收益。
>
> 参照基线：旧仓 W74-01 记录「Qwen2.5-1.5B 在单工具上即 8 轮重复」⇒ 端侧多轮工具调用成功率 ≈ 0。若远程模型也在 50% 以下，说明问题在 harness（提示词/工具 schema/回灌格式），不在模型。

### 风险

| 风险 | 触发信号 | 缓解 |
|---|---|---|
| 模型把工具参数写成自然语言 | `ToolArgsValidator` 大量失败 | 先在 W2 内加一次「参数错误后带错误信息重试一次」的宽容路径，仍失败则记入 W6 |
| 审批卡阻塞主循环导致 ANR | 真机弹卡后 UI 卡 | 审批等待必须挂起协程而非阻塞线程；超时 5 分钟自动拒绝 |
| 压缩后模型失忆 | 用户反馈「忘了前面说的」 | 压缩日志已记录条数变化，先看日志再改策略 |

### 状态（2026-10-30，W2 落地）

本地实测：`:app:assembleDebug` 通过；`fulltest` **tests=349 failures=0 errors=0 skipped=0（11/11 模块）**；
`arch-guard` **OK=23 VIOLATION=0 SELF_FAULT=0**；`arch-guard-selftest` **23/23 PASS（117s）**。

| # | 结论 | 证据 |
|---|---|---|
| 3 / 4 / 7 / 8 / 9 | ✅ | `ToolApprovalFlowTest` / `ToolCallExecutorTest` / `ToolExecutionGuardTest` / `ToolResultCommitTest` |
| 5 / 6 | ✅ | `ToolResultCommitTest`（MODEL+TOOL 配对、callId 一一对应）+ `ContextCompressorTest`（切点回溯宿主） |
| 10 | ✅ **真机** | OPPO PDRM00：`file_write` ⇒ 弹审批卡（三按钮）⇒ 允许一次 ⇒ `files/harness/workspace/notes.txt` 内容 = `hello harness` ⇒ 结果回灌（第二轮 msgs=6）⇒ 出最终答复 |
| 11 | ✅ | `@Test` **349**（≥130），`build.yml` 的 `EXPECTED_TESTS` 已同步 349 |
| 12 | ✅ | 23 条守卫（W2U 22 + G15c） |
| 1 / 2 | ⚠️ **未跑** | 题面已固化在 `scripts/fixtures/w2-tool-prompts.json`（20 条 + 机械成功判定 + 归因分类），**但需要真实模型算力**，当前环境无 API Key ⇒ 不得记为通过 |

**本波顺带修掉的两条真机缺陷（CI 抓不到，只有真机看得出）**：

1. `ChatScreen` 的 bottomBar 漏了 `LocalBottomBarOverlay` 让位 ⇒ 竖屏下输入栏被悬浮页签**整个盖住**
   （uiautomator 里 EditText 节点数为 0；横屏 Top 形态 overlay=0 则正常）。旧仓同一处有这段 padding，
   W2U 原样搬入时漏了。
2. `GlassScaffold` 的 snackbar 槽只有 `padding(bottom = 104.dp + overlay)`，落点在键盘区**之内**
   ⇒ 错误卡被 IME 完全盖住。真机现场：发送一个会 302 的请求，节点树里读得到
   「本次请求失败：…(HTTP 302)」，截图上一个字都看不见。已改为键盘可见时垫 12dp + `imePadding()`。

**W2 的 `run(request, approval)` 注入点已按外审建议落地**：审批 handler 按 **run** 传入、
不进按 endpointId 缓存的长生命周期 `AgentRunner`、不进 `@Serializable` 的 `AgentRequest`，
转发进 `TurnContext` 第 6 个字段。

> 一处**已登记的口径缺口**：`ToolApprovalFlow` 的 `ToolApprovalCache` 实例随 run 创建
> ⇒ 「本会话允许相同调用」的作用域实际是**一次 run**，不是一次会话。会话级装配需要
> `AppContainer` 持有 cache 并注入，排在 W2-2。

---

## 三、W3–W8 概要

### W3 — 内核硬化

**目标**：从「能跑」到「跑不死」。

交付物：`journal/AgentRunJournal.kt`（照搬 368 行）、`loop/RoundHeadGate.kt`（墙钟 SOFT 180s / HARD 600s + token 硬上限）、`FreezeClock`（照搬 108 行）、`loop/PostStreamSignals.kt`、`loop/NoToolCallHandler.kt`、`StreamRepetitionDetector`（照搬，砍掉 prompt-echo 半边）、`token/RunTokenLedger.kt`（改造，**首次启用门控**）、`feature-settings/DiagnosticsScreen`。

**验收判据**
1. 杀进程后重启 ⇒ 弹恢复卡（journal 有 `run_started` 无 `settled`）；点「继续」能接上。
2. 注入 `wallClockHardMillis = 5000` + 一个慢工具 ⇒ `BreakerTripped`，诊断卡列出 tripped 项。
3. 单 run 累计 prompt token 超 `maxRunPromptTokens` ⇒ 熔断，文案含实际用量。
4. 灭屏 60s 后解锁 ⇒ run 未被误熔断（冻结时钟生效）；日志有冻结时长。
5. 空输出 3 连 ⇒ 注入提醒后重跑，不直接失败。
6. `@Test` ≥ 200。

**风险**：FreezeClock 在 API 路径**未验证**（旧仓只在本地路径验过）⇒ 判据 4 是本波唯一真机硬门；不通过就把 FreezeClock 降级为「只服务墙钟 SOFT，不服务 HARD」。

### W4 — Skill 系统

**目标**：三级披露生效，且 system 段字节稳定。

交付物：新建 `:core-skills`（`SkillParser` / `SkillCatalogImpl` / `AssetSkillSource` / `FileSkillSource` / `SkillImportValidator`）、`core-agent/skill/SkillCatalog.kt`（端口）、`tool/SkillTools.kt`（`skill_load` / `skill_read` / `skill_search`）、`AgentPromptComposer` 加 `SKILLS_SECTION`、`feature-settings/SkillListScreen`。内置 2 个示例技能（`pdf-toolkit` / `notes-organizer`，纯文本，各 ~40 行）。

**验收判据**
1. 启用技能后，system prompt 的 `【可用技能】`段出现 `name: description`（单测断言逐字）。
2. 模型调 `skill_load` ⇒ 正文以 **`role=USER`** 消息追加；**system 段 SHA-256 与加载前逐字节相同**（单测硬断言）。
3. `skill_read` 路径逃逸 3 形态（`../`、绝对路径、符号链接）全部拒绝（各 1 单测）。
4. `allowed-tools` 只收窄：技能声明 `[file_read]` 时，`file_write` 不在本轮工具面（单测）。
5. 导入非法 zip（> 2 MiB / > 50 文件 / 含 `.dex`）被拒并给出具体原因（4 单测）。
6. 真机：装一个技能 → 提问命中 → 日志有 `SkillLoaded` → 回答体现了技能步骤。
7. `@Test` ≥ 260。

**风险**：技能正文注入 user 角色后，模型可能把它当用户指令 ⇒ 若判据 6 出现越权（跳过审批），改为 `<skill>` 标签 + system 固定边界声明（A6 反证条件）。

### W5 — MCP Client

**目标**：接 1 个真实 server，拉到工具并调用成功；双 era 都能连。

交付物：新建 `:core-mcp`（`McpClient` / `StreamableHttpTransport` / `EraNegotiator` / `ToolCatalogCache` / `McpToolBridge`）、`core-agent/tool/ToolSource.kt` 实现、`feature-settings/McpServerListScreen` + `McpServerEditScreen`。

**要实现的 JSON-RPC 方法（客户端 → 服务端）**：

| 方法 | era | 必需 | 说明 |
|---|---|---|---|
| `server/discover` | 2026-07-28 | ✅ | 新 era 的能力发现入口（取代 initialize） |
| `initialize` | 2025-11-25 | ✅ | legacy 握手 |
| `notifications/initialized` | 2025-11-25 | ✅ | 无 id 通知 |
| `tools/list` | 两 era | ✅ | 支持 `cursor` 分页 |
| `tools/call` | 两 era | ✅ | 核心 |
| `ping` | 两 era | ✅ | 连接测试按钮用它 |
| `notifications/cancelled` | 两 era | ✅ | 长调用取消 |
| `resources/list` / `resources/read` | 两 era | ⛔ W5 不做 | Skill 的 `skill_read` 已覆盖「读文档」需求，两套机制并存只会让模型选错 |
| `prompts/list` / `prompts/get` | 两 era | ⛔ 丢弃 | Skill 取代 prompts |
| `logging/setLevel` | legacy | ⛔ 丢弃 | 新 era 已弃用 |
| `completion/complete` | 两 era | ⛔ 丢弃 | 无 UI 需求 |
| `sampling/createMessage`（服务端→客户端） | 两 era | ⛔ **拒绝** | 允许远端 server 花用户的 API 预算。收到即回 `-32601` |
| `roots/list`（服务端→客户端） | 两 era | ⛔ **拒绝** | Android 沙箱无 roots 概念 |

**era 协商**：连新 era 优先 —— 先 POST `server/discover`；返回 `-32601`（method not found）或 HTTP 404/405 ⇒ 回落 legacy `initialize`。协商结果按 server 持久化，`protocolVersionMismatch` 错误时重探。

**验收判据**
1. 接 1 个真实 server ⇒ `tools/list` 拿到 ≥ 1 工具，命名空间为 `mcp__<alias>__<tool>`。
2. legacy server（2025-11-25）⇒ 自动回落，日志有 `era=legacy`。
3. 该工具被调用 ⇒ **强制过审批**（`registerContributed` 语义），审批卡显示 server alias。
4. 同名冲突 ⇒ 拒绝注册并记 error（不是覆盖）。
5. 无网 ⇒ 工具清单从磁盘缓存读出，UI 标注「缓存于 <时间>」。
6. server 返回 401 ⇒ 该 server 全部工具下线，UI 出「去配置」入口。
7. 超时（60s）⇒ `ToolResult(ok=false)`，计入失败连击。
8. 内网 baseUrl（`192.168.x.x`）未勾选「允许本地网络」⇒ 拒绝保存。
9. `@Test` ≥ 320。

**风险**：找不到可用的真实 server 做真机验证 ⇒ 允许自建一个 30 行的本地 HTTP MCP echo server 作为测试夹具（不进 APK），但**必须**在文档里标注「已验证对象是自建 server，不是生态里的真实 server」，不得含糊成「MCP 已验证」。

### W6 — 多供应商 + 上下文工程二期

**目标**：Anthropic / Gemini 各跑通一次工具调用；长会话不丢上下文；prompt cache 布局就位。

交付物：`AnthropicAdapter` / `GeminiAdapter`、`ContextSummarizer`（L2 摘要压缩，旧仓 Wave 35 判死的 `SummarizingContextCompressor` **翻案重建**）、`PromptCacheLayout`（稳定段前置 + 断点标记）、`ReasoningBlock`（思考内容独立折叠块）。

**翻案理由（必须写进代码 KDoc）**：旧仓判死原文是「摘要需要一次额外推理，端侧 4B 上这笔开销换不来等价收益」。API 路径下这笔开销是**一次廉价调用**，而替代方案是整段对话丢失 ⇒ 成本收益反转。旧仓判死的重启前提「出现估算失准事故」由 W3 的 usage 账本满足。

**验收判据**
1. 三家 provider 各完成一次「读文件 → 总结」（真机，各 1 次）。
2. 同一会话连续 3 轮，日志显示 `cachedPromptTokens > 0`（至少一家）。
3. 压缩后 system 段 SHA-256 与压缩前相同（压缩只动 history）。
4. 40 轮长会话（脚本驱动）⇒ 不崩、不 OOM、最后仍能回答第 1 轮的事实问题。
5. 思考通道内容进 `ThinkingDelta` 并上屏为独立折叠块，不混进正文。
6. `@Test` ≥ 380。

**风险**：`cachedPromptTokens` 三家都拿不到 ⇒ 判据 2 降级为「日志记录请求前缀的 SHA-256，人工比对两次是否一致」。

### W7 — 安全收口

**目标**：五处泄漏面全兜；SSRF 阻断；CI 密钥扫描不可绕过。

交付物：`SecretRedactor` 扩容到 8 类（JWT / PEM / `sk-ant-` / `sk-proj-` / `AIza` / `AKIA` / `ghp_|gho_|ghs_` / `xox`）、五处接入点、`UrlGuard` 完整化、`ToolResult.displayOutput` 双字段、CI 加 gitleaks + trufflehog、`feature-settings/ApprovalPolicyScreen`（危险工具清单）。

**验收判据**
1. 8 种密钥形态 × 5 个出口 = 40 个断言（单测矩阵）。
2. 真机截图：把 key 贴进输入框 ⇒ 气泡里显示 `sk-****abcd`，日志里 `grep` 不到原文。
3. 工具参数：`file_write` 写入含 key 的内容 ⇒ **文件内容是原文**（执行不脱敏），审批卡显示脱敏（展示脱敏）。
4. HTTP 头：日志中任何 `Authorization` / `x-api-key` 值都不出现（`grep -c` 为 0）。
5. `UrlGuard`：`http://192.168.1.1/v1` 未勾选允许 ⇒ 拒绝；`https://api.openai.com` ⇒ 通过；`http://10.0.2.2:11434/v1`（Ollama 模拟器）⇒ 白名单通过。
6. CI：`git push` 一个含假 `sk-` 的 commit ⇒ `secret-scan` job 判红（用测试分支验证后删除）。
7. `@Test` ≥ 440。

**风险**：脱敏正则误伤（用户正常输入的 base64 被当密钥）⇒ 判据 2 需同时验证「正常长字符串不被脱敏」。

### W8 — 打磨与发布

**目标**：可发布。

交付物：首启引导（照搬旧仓 `OnboardingScreen` / `ConsentScreens` / `FirstRunGate`，共 614 行）、`strings.xml` 完整化、深色模式、诊断页、性能（帧率）、release 构建 + 签名配置、README + 三份用户文档。

**验收判据**
1. 守卫 G16 中文硬编码基线 = **0**（`grep -cP '"[^"]*[\x{4e00}-\x{9fff}]'` 在 main 源集）。
2. `strings.xml` 条目数 ≥ 全部用户可见文案数（用一个脚本对比 `TextRef.Res` 引用集与 `strings.xml` key 集，双向无差集）。
3. release APK 冷启动 < 2s（`adb shell am start -W` 的 `TotalTime`）。
4. 生成期滚动帧率 ≥ 55fps（沿用旧仓 `FrameStatsCollector` 口径）。
5. `lint` 0 error，baseline 只减不增。
6. `@Test` ≥ 500。

---

## 四、W1 的细化排期（按 commit 粒度）

| # | commit | 内容 | 可独立验证 |
|---|---|---|---|
| 1 | `chore: gradle skeleton` | **11 模块目录**（3 个只有 build + 空 res）+ 版本目录 + 根 build + `applicationId = com.rickeal.liquidharness` | `./gradlew projects` 输出 11 个 |
| 2 | `feat(model): contracts` | `:core-model` 全部文件 + 单测（含 `TokenEstimator` / `DeltaTracker` 照搬） | 单测 15 |
| 3 | `feat(net): sse parser` | `SseEventParser`（多行 `data:` / `event:`）+ `HttpTransport`（OkHttp）+ `UrlGuard` + 3 条边界单测 | 单测 25 |
| 4 | `feat(engine): openai adapter` | `OpenAiCompatibleAdapter` + `RemoteLlmEngine` + `RetryPolicy`；**从 `_recovered_remote/OpenAiCompatibleEngine.kt` 恢复**（`01-architecture.md` §11） | 单测 32 |
| 5 | `feat(agent): turn loop` | `TurnContext` / `TurnLoop` / `GenerationRound` / `AgentRunner` | 单测 38 |
| 6 | `feat(data): container + keystore` | `AppContainer`（≤200）+ 三个 Graph + `EndpointSecretStore` + `EndpointStore`（从恢复的 `EndpointRepository` 改造） | 单测 45 |
| 7 | `feat(ui): chat screen` | `:core-design` 搬入 + `ChatScreen` / `EventReducer` + `EndpointsScreen`（从恢复的 UI 改造） | 真机可跑 |
| 8 | `ci: gates` | `arch-guard.sh` 12 条 + selftest + `fulltest.sh` + `ci.yml` | CI 绿 |

每个 commit 都必须 `arch-guard` 全绿 —— 旧仓教训：「CI 只验证编译故每 commit 须静态自查可过」（`.recon-harness/wave3-proposal.md` 裁决原则）。

> commit 4/6/7 都标注了「从 `_recovered_remote/` 恢复」—— 恢复不等于照抄：`01-architecture.md` §11 列了 785 行可复用 / 320 行必改（密钥来源、删生命周期、补 `enum`、移出 `Bitmap`）。**必改项如果漏了，W7 的安全判据会当场判红**（端点 JSON 里 grep 到 key 原文）。

---

## 五、测试与门禁基线

### 5.1 单测口径

| 项 | 定义 | 理由 |
|---|---|---|
| 计数口径 | `grep -rnE "^\s*@Test\b" --include=*.kt .` | 宽松 `grep "@Test"` 会多算 KDoc 引用（知识库 §5） |
| 框架 | JUnit 4 | 硬约束；**测试方法必须返回 `void`**（否则整类 `initializationError`） |
| 方法名 | 禁 `. ; [ /`；中文名用「A 与 B」不用「A / B」 | 同上 |
| 断言 | 只用 `assertEquals` / `assertTrue` / `assertFalse` / `assertNull` | `assertNotNull` 等有返回值，当表达式用会炸 |
| 基线同步 | `build.yml` 的 `EXPECTED_TESTS` ↔ 实际 `@Test` 数，双向相等 | 旧仓守卫 #26 |
| 隔离 | `core-*` 的单测不得触 Android API（`SecretCipher` 用 fake） | 保证 JVM 可跑 |
| 无设备 | 任何单测不得连真机（旧仓 W73-04 P0 事故：测试把夹具推进真机 datastore 导致崩溃循环） | 知识库 §5 |

### 5.2 架构守卫（23 条判据，表驱动）

> **条数以 `bash scripts/arch-guard.sh` 的实跑输出（`OK=nn`）为准。** 本文档里任何数字若与之不符，以脚本为准。
> 加这句话的原因：2026-10-30 外审抓到本节标题写「18 条」、W2 判据 12 写「14 条」、实跑却是 22 条 —— 三处不一致，且没有任何一处是「权威源」。守卫条数只能有一个真值源，就是脚本自己数出来的那个数。
> 计数口径：`G1`/`G4`/`G5`/`G15` 四个编号各拆成 a/b/c 子判据（`G1`+`G1b`、`G4a`+`G4b`、`G5a`+`G5b`、`G15a`+`G15b`+`G15c` = 9 条），其余 14 个编号各 1 条 ⇒ **9 + 14 = 23**。子判据是独立断言，必须各自有 selftest case。

| # | 名称 | 判据 | 旧仓来源 |
|---|---|---|---|
| G1 | 网络栈收口 | `okhttp3` / `java.net` / `URL(` 只允许出现在 `core-net/`；`core-net` 不得出现 `com.rickeal.liquidharness.core.engine`（传输层不认识供应商） | 旧仓 #5 **反向** |
| G2 | 禁进程执行 | 全仓禁 `Runtime.getRuntime` / `.exec(` / `ProcessBuilder` | 旧仓 #6 **保留** |
| G3 | 禁动态代码加载 | 禁 `DexClassLoader` / `PathClassLoader` / `loadClass` | 旧仓 #9 保留 |
| G4 | 禁依赖黑名单 | `.kts` 与 `libs.versions.toml` 双扫：KSP/kapt/Room/Hilt/Dagger/Koin/Retrofit/Coil/sqldelight | 旧仓 #4 |
| G5 | 禁 jitpack | `settings.gradle.kts` 无 `jitpack`；`repositoriesMode = FAIL_ON_PROJECT_REPOS` | 旧仓 settings 注释 |
| G6 | 模块边界 | `core-agent` ↛ `core-net`/`core-engine`/`core-data`/`core-design`/`core-skills`/`core-mcp` | 旧仓 #10 |
| G7 | 设计系统零业务 | `core-design` 无 `com.rickeal.liquidharness.core.model` / `.core.agent` 引用 | 旧仓 #2 |
| G8 | 契约层纯 Kotlin | `core-model` 无 `android.` / `androidx.` / `java.net` | 旧仓 #3 |
| G9 | 模块数一致 | `settings.gradle.kts` 的 include 数 == 守卫脚本 `MODULES` 数组长度 == 实际目录数 | 旧仓「模块存在性」断言 |
| G10 | **行数闸门（表驱动）** | 一张 `path<TAB>cap` 表 + 循环；缺文件判红（不是跳过） | 旧仓 #25 家族（21 条压成 1 条） |
| G11 | 方法体积 | 单方法 ≤ 120 行（`fun` 到下一条 4 空格 `}` 的行差） | 旧仓 #12（阈值从 550 降到 120） |
| G12 | `AgentRunner` 门面 | `AgentRunner.kt` ≤ 200 行 | 旧仓 #31（阈值 2900→200） |
| G13 | 测试方法形态 | `@Test` 方法返回 void；反引号名无 JVM 非法字符 | 旧仓 #15 |
| G14 | lint baseline 只缩 | baseline 行数 ≤ 冻结值 | 旧仓 #14 |
| G15 | 声明面 | `INTERNET` 权限必须存在；`usesCleartextTraffic=false`；`allowBackup=false`（备份会带走 Keystore 密文与端点清单） | 旧仓 #7/#7b |
| G16 | **中文硬编码棘轮** | main 源集含中文的字符串字面量数 ≤ 基线（W1 起为 0，只许不增） | 新建（A9） |
| G17 | **`@Serializable` 禁密钥字段** | `core-model` 的 `@Serializable data class` 内不得有 `apiKey`/`secret`/`password`/`token` 字段名（白名单：`maxOutputTokens` 等含 token 的计数名） | 新建（A7） |
| G18 | **引擎调用点收口** | `LlmEngine.generate(` 的调用点只允许在 `core-agent/loop/` | 新建（A3） |

**守卫自身的纪律（照搬旧仓，逐条都是踩过的坑）**

1. `check()` 三态：`0=OK` / `1=违规` / `≥2=守卫自身故障（判红）`。旧仓原文：「目录被改名 → 空守卫永远绿」。
2. **「必须存在」型断言必须自己 `echo`**：`grep -q` 无输出，失败时退出 1 被判 OK（旧仓 #7 曾因此成僵尸规则，实测删掉整行权限守卫仍全绿）。
3. **「必须不存在」型断言用 `grep -rn`**（命中即输出），天然合规。
4. 带 `| grep -vE` 的守卫在目标目录缺失时会**静默判绿**（管道把 rc=2 洗成 1）⇒ G9 的模块存在性断言必须显式兜。
5. 每条守卫必须有 selftest case，且 selftest 要包含「变异自证」（把违规注入一次，必须判红）。
6. selftest 总耗时 ≤ 60s（旧仓 93 case 跑 52 分钟，实际上等于没人跑）。

### 5.3 CI 流水线

```yaml
jobs:
  build:          # ./gradlew :app:assembleDebug
  unit-tests:     # bash scripts/fulltest.sh + EXPECTED_TESTS 对账
  arch-guard:     # bash scripts/arch-guard.sh && bash scripts/arch-guard-selftest.sh
  lint:           # ./gradlew lint + baseline 只缩
  secret-scan:    # gitleaks --exit-code 1 ; trufflehog --only-verified
  strings:        # 守卫 G16 + TextRef↔strings.xml 双向差集
```

**为什么 `secret-scan` 必须在 CI 而不是 pre-commit**：`git commit --no-verify` 能绕过 pre-commit，CI 才是不可绕过的兜底（报告 §一.3）。
**为什么 gitleaks + trufflehog 都跑**：两者漏的方向相反 —— gitleaks 是正则+熵（快、误报多），trufflehog 是 live verification（慢、只说真需要轮换的）。只跑一个就有系统性盲区。

---

## 六、波次与守卫/单测基线的对应

| 波结束 | 守卫条数 | `@Test` 下限 | 关键新增守卫 |
|---|---|---|---|
| W1 | 12 | 45 | G1~G9 / G12 / G13 / G17（网络栈收口、禁进程、禁动态加载、禁依赖黑名单、禁 jitpack、模块边界、设计系统零业务、契约层纯 Kotlin、模块数一致、`AgentRunner` 门面、测试方法形态、`@Serializable` 禁密钥字段） |
| W2 | 14 | 130 | +G10（行数表驱动）、+G11（方法体积） |
| W3 | 14 | 200 | — |
| W4 | 15 | 260 | +G16（中文硬编码棘轮，基线置 0） |
| W5 | 16 | 320 | +G18（引擎调用点收口） |
| W6 | 18 | 380 | +G14（lint baseline 只缩）、+G15（声明面） |
| W7 | 18 | 440 | — |
| W8 | 18 | 500 | — |

> ⚠️ 上表是**每波结束时的历史序列**（W1 落地 12 条、W6 计划到 18 条），不要拿它当现状。
> **截至 W2U（2026-10-30）实际 23 条判据**：`G1/G4/G5/G15` 各拆了子判据（G1b、G4a/G4b、G5a/G5b、G15a/G15b/G15c），另加 W2U 补的 G15c（禁备份）。
> 历史值保持原样是为了能回溯「哪一波加了哪条」；现状只认 `bash scripts/arch-guard.sh` 的 `OK=` 输出。
