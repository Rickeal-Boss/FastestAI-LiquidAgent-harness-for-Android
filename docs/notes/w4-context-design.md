# W4 上下文工程二期 — 设计方案（分块前缀缓存 / L2 摘要压缩 / 长上下文异步）

> **性质**：设计文档，**不含任何生产代码改动**。源码改动由开发执行。
> **基线**：`docs/handoff-20261031-wave3.md`（W3 收口，471 测试 / 23 守卫）+ 工作树。
> **实测数据源**：`docs/notes/w4-provider-matrix.md`（本波新测，**优先于** W3 矩阵）+
> `docs/notes/w3-provider-matrix.md`（历史）。两份数字不一致处以 W4 为准，冲突点见 §2.9。
> **取证纪律**：本文件每条论断都指到 `文件:行` 或本仓既有实测表。凡引用旧文档结论处，均已回源码复核；
> 与当前代码冲突的地方**以代码为准并显式标注**（见 §2.9）。
> **判据纪律**：§4 每条验收判据都给出「触发条件 + 空命中语义」。`0 命中 ≠ 通过`，
> 「判据写进方案」≠「判据被执行」—— 本文件 §2.7 就是两个现成反例。

---

## 0. 与 roadmap 的偏差登记（先销账，否则后面全是无出处设计）

`docs/02-roadmap.md:677` 把 `ContextSummarizer`（L2）与 `PromptCacheLayout` 排在 **W6**，
W4 原定是「Skill 系统」。而 W3 交接显示 Skill 系统**已在 W3 交付**
（`docs/handoff-20261031-wave3.md` §2.3），W4 的槽位因此空出，本波改放上下文工程二期。

| 项 | roadmap 原排 | 本波（W4） | 处置 |
|---|---|---|---|
| `ContextSummarizer`（L2） | W6 | **W4 做** | 本文件即其设计。**✅ 已销账**：`docs/02-roadmap.md` W6 交付物已删该项，并留「W4 迁出项的去处」段指向本文件 |
| `PromptCacheLayout`（稳定段前置 + 断点标记） | W6 | **W4 只做「稳定段 + 指纹账」，断点标记明确不做**（§3.A.3） | **✅ 已销账**：同上；`cache_control` 渲染归 W6 的 `AnthropicAdapter`，落点即本波已显式化的段边界 |
| `AnthropicAdapter` / `GeminiAdapter` / `ReasoningBlock` | W6 | 不做 | 与 A/B/C 正交，不占本波 |

> 销账前若不同步删 roadmap，W6 会按旧清单再实现一遍 L2 —— 两份实现必然打架。
> **本项已于 2026-10-31 完成**（`docs/02-roadmap.md` 的 W6 标题/目标/交付物同步收敛为
> 「多供应商 + 思考通道」）。

---

## 1. 结论先行

1. **A 的真正缺口不是「缺缓存断点」，而是「压缩几乎每轮复发」。**
   `WindowContextCompressor` 贪心把保留区**填满到 `budgetTokens`**（`ContextCompressor.kt:78-83`），
   留下的余量恒 `< 被丢第一条消息的代价`（通常只有半条消息量级）；而一条满额读类工具结果
   ≈4 000 token（`AgentPolicy.kt:28`）⇒ 下一轮追加它**几乎必然**再次越过门槛 ⇒
   从第一次压缩起，压缩按「每轮一次」的节奏复发，每轮都把切点往后推一次，即每轮前缀失效一次。
   修法是压缩到**水位线**（目标 0.6×budget），不是加断点标记。
2. **没有前缀指纹，`cached=0` 无法解释。** 现役 `cached_tokens` 缺失被压成 `0`
   （`OpenAiCompatibleAdapter.kt:139`），`EventReducer` 自己已承认「0 命中」与「未上报」同形
   （`EventReducer.kt:272-275`）。所以 A 的核心交付是**分段指纹 + 每请求一行账**，
   不是缓存 API —— OpenAI 兼容族与 GLM 都没有断点字段，加 `cache_control` 是空转实现。
3. **B 的 L2 必须复用 L1 的切点，不得自己算切点。** 这样 `assistant.tool_calls` ↔ `tool` 的配对
   由**构造**保证（不是靠事后 `sanitizeForProvider` 补丁），摘要消息以 `role=USER` 插在 body 首位、
   id 稳定派生、禁止被相邻合并；摘要失败回落 L1，**不许让 run 失败**。
4. **C 的主要阻塞不是网络等待，是 `ContextBudget.prepare` 每轮在主线程做 O(历史) 全量扫描。**
   815k token 的工作副本约 3.2M 字符，`prepare` 每轮至少跑两遍全量扫描
   （`TokenEstimator.estimateMessages` + `sanitizeForProvider`，`ContextBudget.kt:53-58`），
   而内核循环继承收集方上下文 = `Main.immediate`（`AgentRunner.kt:109-130`）。
   异步 = ① `prepare` 移出 Main ② 首字节看门狗 ③ 可见进度，三件缺一不可。
5. **两个「声明了但没接线」的现成反例必须本波销账**：`AgentPolicy.generationTimeoutMillis`
   （`AgentPolicy.kt:104`，全仓零消费点）与 `EngineErrorKind.CONTEXT_OVERFLOW` 的 KDoc 承诺
   「触发压缩后重发一次」（`GenerationEvent.kt:72`，代码里走 `Attempt.Fatal` 直接失败，
   `GenerationRound.kt:80-83`）。不接这两条，本波新增的判据会以同样方式变成装饰。

---

## 2. 现状缺口（对着代码说）

### 2.1 压缩：填满即必然复发

| 事实 | 证据 |
|---|---|
| 从最新往回贪心，**装到刚好不超 `budgetTokens` 为止** | `ContextCompressor.kt:76-83` |
| 压缩后余量恒 `< 被丢第一条消息的代价`（贪心的终止条件），通常只有半条消息量级 ⇒ 下一轮追加一条满额读类工具结果（16 000 字符 ≈ 4 000 token，`AgentPolicy.kt:28`）**几乎必然**再次越门槛 | `ContextCompressor.kt:80`；`AgentPolicy.kt:28` |
| 一旦越过门槛，压缩按「每轮一次」复发，每次把保留区起点往后挪一条 | 由上两条直接推出；`ContextBudget.kt:56-72` 每轮重算 budget |
| 压缩次数**无上限、无冷却** | 全仓 grep `compressCount` / 冷却相关：零命中 |
| 压缩切点只保证「非 TOOL」，**不优先落在 USER（turn 边界）** | `ContextCompressor.kt:108-119` |

后果：用户感知到的「前缀老在变」不是技能列表顺序或记忆注入造成的，而是**压缩自身的抖动**。
这是本波最贵的一条，且它不需要任何新能力就能修。

### 2.2 工具面每轮重取（未登记的前缀抖动源）

| 事实 | 证据 |
|---|---|
| `TurnLoop` 只快照了 system 文本，**没快照工具声明** | `TurnLoop.kt:92-97`（`ctx.state.systemText` 一个字段） |
| `GenerationRound` 每轮重新调 `ctx.tools.specs()` 建请求 | `GenerationRound.kt:109` |
| 工具顺序按名字排序（稳定），但 `setEnabled` / MCP 动态注册会**中段增删** | `ToolRegistry.kt:80-82`、`:91`、`:96` |
| MCP 注册发生在 `ChatViewModel.init` 的协程里，**与在途 run 无互斥** | `ChatViewModel.kt:100-121` |

⇒ 一个 run 内工具面可以变；变一次，`tools` 数组从该位置起全部重排 ⇒ 前缀失效。
W2.5 把它记成 I7「latent，W5 触发」（`w25-prompt-cache-and-w3-design.md:384`），
但 W3 已把 MCP 接上（`handoff-20261031-wave3.md` §2.4），**它不再是 latent**。

### 2.3 预算估算不含工具声明（M2 从未落地）

| 事实 | 证据 |
|---|---|
| `TokenEstimator` 只有 `estimate` / `estimateMessages` | `TokenEstimator.kt:28-61` |
| `estimateToolSpecs` 全仓零命中 | grep 结果为空 |
| W2.5 §4.2 曾把它列为 M2，W3 交接未提 ⇒ **未交付** | `w25-prompt-cache-and-w3-design.md:512-530` |

现役 6 工具 ≈ 400–500 token（可忽略）；W3 接 MCP 后工具面可到数千 token ⇒ 低估会变成
「以为没超、实际超窗」。本波要算前缀 token，绕不过这一条。

### 2.4 `cached_tokens` 的三处失真

| 事实 | 证据 |
|---|---|
| 缺失字段被 `?: 0` 压成 0 | `OpenAiCompatibleAdapter.kt:139` |
| 契约类型是 `Int`（非 `Int?`），一路无「是否上报」标记 | `GenerationEvent.kt:34`；`AgentEvent.kt:94`；`Generation.kt:46-56` |
| UI 已承认无法区分，登记为 W6 | `EventReducer.kt:272-275` |
| DeepSeek 用 `prompt_cache_hit_tokens`（顶层），本仓读不到 ⇒ 换端点时 `cached` 恒 0 且不可分辨 | `w25-prompt-cache-and-w3-design.md:130-133` |

⇒ 「命中率」这个数字现在**不具备可解释性**。A 若只做断点不做这一条，等于在错的账上做优化。

### 2.5 长上下文：超时与进度

| 事实 | 证据 |
|---|---|
| `generationTimeoutMillis = 120_000` **声明了但零消费点** | `AgentPolicy.kt:104`；grep 仅此一处 |
| 唯一的单次 read 超时是 OkHttp `readTimeout = 60s`（每次 read，不是整轮） | `HttpTransport.kt:168`、`:177` |
| `callTimeout = 0`（永不总超时） | `HttpTransport.kt:169` |
| 墙钟 HARD 600s **只在轮头判**，一轮内挂死无人管 | `RoundHeadGate.kt:36-40` |
| `ChatStatus` 只有 `RETRYING` / `CANCELLED_EMPTY`，**没有等待态** | `ChatUiState.kt:46-54` |
| 状态行只渲染一句静态文案，无秒数、无 token 数 | `ChatScreen.kt:147-160` |

实测：760k token 单请求 **40.8s**、385k 19.1s、192k 10.0s（`w4-provider-matrix.md:62-69`；
W3 的 815k=57.1s 是另一组干草堆，量级一致）。120s 上限 ≈ 2.9× 余量，
数值上够用，但**代码里没有任何地方读它**。

### 2.6 长上下文：`maxContextTokens` 把 1M 窗口锁死在 32k

| 事实 | 证据 |
|---|---|
| `budget = (min(contextLength, maxContextTokens) - maxOutput) × 0.75` | `ContextCompressor.kt:37-41` |
| `maxContextTokens` 默认 32 000，且**不可配置**（`AgentRunner` 用默认 policy 构造） | `AgentPolicy.kt:95`；`AppContainer.kt:243-250` |
| ⇒ 端点声明 1M，应用最多发 ≈24k | 算式直接推出 |
| **公式少了 10% 余量**：实测 chars/token 在同一模型的不同 provider 路由上是 5.3 与 4.0（753k vs 1,250k 两次），客户端**不能**用字符数线性外推 token 数 | `w4-provider-matrix.md:87-90` |
| **窗口上限必须先扣输出预留**：512k 窗口 + `max_tokens=1024` ⇒ 513,030 被拒 | `w4-provider-matrix.md:79`、`:84-86` |

⇒ 本波 C 的 815k 真机判据**在现役装配下不可行使**。必须给 `maxContextTokens` 一条配置通路，
且预算公式要补 10% 余量（§3.A.9 的 A-8），否则「估算没超」的请求会被端点 400。
若不做 C-6，C 的真机验收只能是 `⛔ vacuous`（§4 已如实标注）。

### 2.7 「判据写进方案但没被执行」的两个现成实例

1. `EngineErrorKind.CONTEXT_OVERFLOW` 的 KDoc 写「不可重试，触发压缩后重发一次」
   （`GenerationEvent.kt:72`），而 `GenerationRound` 对 `Attempt.Fatal` 一律 `emitFailed`
   （`GenerationRound.kt:80-83`）⇒ **服务端报超窗时没有压缩重发，直接失败**。
2. `generationTimeoutMillis`（见 §2.5）。

这两条是本波所有新判据的前置示范：**判据必须有一个能被执行的点，且该点要有单测走过。**

### 2.8 压缩链路的可观测面

| 事实 | 证据 |
|---|---|
| 压缩只在 `AgentLogStore.info` 记一句自由文本 | `ContextCompressor.kt:90-93`；`ContextBudget.kt:68-71` |
| 缓冲 200 条、满时优先丢 INFO | `AgentLogStore.kt:37`、`:89-107` |
| ⇒ 跨轮/跨 run 的压缩证据**基本取不到** | 由上两条推出 |
| 无 `DiagnosticsScreen`（roadmap W3-6 计划过，W3 交接未交付） | `find -name "*Diagnostic*"` 零命中 |

### 2.9 与既有笔记的冲突（以代码 + 新实测为准）

| 旧说法 | 现状 | 处置 |
|---|---|---|
| **W3**：NVIDIA 端点 64k/256k 命中 >97%（`w3-provider-matrix.md:83-84`） | **W4 同模型实测：64k = 0.0%、256k = 0.6%、512k = 5.7%、1M = 12.6%**（`w4-provider-matrix.md:62-69`）。差异来自干草堆内容不同（W3 的阶梯前缀共享，W4 的不共享），**不是矛盾，是不同实验** | 本文件采用 W4 口径：**命中率是供应商 + 前缀共享度的性质，不是客户端指标**；客户端只保证逐字节稳定 |
| **W3**：815k 延迟 57.1s | **W4：760k = 40.8s、385k = 19.1s、192k = 10.0s**（`w4-provider-matrix.md:62-69`）。仍超线性（2× 长度 ≈ 2× 延迟 + 大常数） | 本文件用 W4 数字；结论不变（数十秒 ⇒ 必须异步 + 进度） |
| **W4 矩阵 §四**：「压缩应尽量**从尾部**丢弃」 | 与代码相反：`WindowContextCompressor` 丢**最老**（`ContextCompressor.kt:88-89`）。丢尾部 = 丢最新，会把当前任务上下文扔掉 | **以代码为准**：丢最老。原文若指「从历史前端丢」，则与本设计一致；若真指丢尾部，属笔误，需在该笔记里订正 |
| **W4 矩阵 §六**：「缓存记账以 `cached_tokens / prompt_tokens` 为准」 | 与本设计一致 | 采纳，见 §3.A.6 |
| **W2.5** I1「跨轮从 UI 气泡重建 history，工具轨迹缺失」列为 🔴 阻断 | **W3 已修**：`ChatHistoryMapper.kt:38-75` 重建 `MODEL(toolCalls)` + 逐条 `TOOL(result)`，SKIPPED 不重建 | 本文件不再把它当缺口；但**跨 run 前缀仍会变**，因为重建出的消息 id 是新 `newId()`（不进报文，无害）而**内容**与原请求不同（`text` 顺序、合并方式）⇒ 记为「跨 run 前缀只能做到语义等价，不能做到逐字节」 |
| **W2.5** I2「静态前缀 700–800 tok < 门槛 4096」 | GLM 实测门槛修正为 (2303, 2502]；NVIDIA 上 6k 前缀即可命中（`w4-provider-matrix.md:96-101`） | 采用**供应商相关**口径：门槛与粒度都是供应商性质，客户端只保证逐字节稳定 |
| **W2.5** M2（工具 token 计入预算） | 未交付 | 本波 A-6 补 |
| **W2.5** M3（前缀指纹日志） | 未交付 | 本波 A-1/A-8 补，并加上「分段」维度 |

---

## 3. A / B / C 三块设计

### A. 分块前缀缓存（chunked prefix cache）

#### A.1 设计立场（先回答「这是不是空转」）

W2.5 明确判过：「加 `PromptCacheLayout` / 缓存断点，在 GLM/OpenAI 上是空转实现，
只有 Anthropic 需要」（`w25-prompt-cache-and-w3-design.md:561`）。这条判断**成立**，
本设计**不推翻它**，而是把它作为约束：

> **本设计不新增任何"缓存层"抽象，也不向请求体加任何字段。**
> 交付物是两件已有的东西：① 把「段边界」从隐式变显式（供审计与将来的 Anthropic 渲染用）；
> ② 给每次请求算一份**分段指纹**并落一行账，让「前缀变没变、谁让它变的」可机械判定。

判据：如果本波做完，除了日志和一行 UI 文案之外**没有任何线上报文变化** —— 这是**正确**的，
不是「没做出东西」。反证条件见 §5 第 1 条。

#### A.2 数据结构

新增 `core-agent/.../context/PromptPrefix.kt`（纯 JVM，零 IO，零协程）：

```kotlin
/** 前缀段的身份。顺序即线上顺序，与 AgentPromptComposer.sections 的段序一一对应。 */
enum class PrefixSegmentKind {
    SYSTEM_INSTRUCTION,   // 用户自定义人格
    TOOL_GUARDRAILS,
    SKILLS_META,
    MEMORY,
    MEMORY_MAINTENANCE,
    STOP_CONDITIONS,
    TOOLS_SCHEMA,         // 线上是 body.tools，不是 messages，但同属"前缀"（见 A.4 注）
    HISTORY,              // messages[1..]（system 之后、本轮新增之前）
    VOLATILE_TAIL,        // 本轮新增（tool 结果 / 新 user 输入）
}

/** 一段的稳定性契约。这不是注释，是测试断言的依据。 */
enum class SegmentStability { BUILD_STABLE, RUN_STABLE, APPEND_ONLY, EVERY_ROUND }

data class PrefixSegment(val kind: PrefixSegmentKind, val stability: SegmentStability, val text: String)

/** 分段指纹。每个 hash 取 SHA-256 前 12 位十六进制。 */
data class PrefixFingerprint(
    val combined: String,
    val perSegment: Map<PrefixSegmentKind, String>,
) {
    /** 与上一次相比变了的段（空 = 逐字节相同）。 */
    fun changedFrom(previous: PrefixFingerprint?): List<PrefixSegmentKind>
}

/** 纯函数。只对稳定段算（BUILD_STABLE + RUN_STABLE），HISTORY/VOLATILE 不参与。 */
fun fingerprintOf(segments: List<PrefixSegment>): PrefixFingerprint
```

**为什么 `TOOLS_SCHEMA` 算前缀段但不参与 `messages`**：OpenAI 兼容族把工具声明放在 body 顶层
（`OpenAiCompatibleAdapter.kt:86-89`），不是 messages。但供应商的前缀缓存是对**整个请求**的
token 序列做的（【推断】，四家同族；W2.5 §3.1(g) 已论证「按 token 序列匹配，不按 JSON 字节」）。
把它当独立段，是为了让「工具面变了」在指纹里**可分辨**，而不是与「system 变了」混成同一个信号。

**为什么不给 `HISTORY` 算指纹**：它每轮都在追加，必然变；算它只会把「正常追加」误报成「前缀被改」。
账里用 `historyMessages=<n>` 与 `historyStartId=<id>` 记录它，变化即压缩（A.5）。

#### A.3 缓存断点标记：明确不做

| 供应商 | 断点字段 | 本波处置 |
|---|---|---|
| OpenAI 兼容族 | 无 | 不加（未知字段虽被多数端点忽略，但加了就是无人消费的字段 = 空转） |
| GLM（BigModel） | 无，纯隐式 | 不加 |
| Anthropic | `cache_control: {type:"ephemeral"}`，最多 4 个 | **W6 的 AnthropicAdapter 做**；本波的段边界即天然落点 |
| Gemini | `cachedContent` 显式资源 | W6 |

反证条件：若有人主张「加了 `cache_control` 也没坏处」—— 对照 `00-brief.md` A5 的判死标准
（无消费点的字段 = 空转实现），以及本仓守卫的既有取向（`RemoteEndpoint` 里被删掉的
`apiKey` 字段同理）。

#### A.4 控制流：一次 `prepare` 做什么

```
GenerationRound.buildMessages
  └─ ContextBudget.prepare(ctx, systemText)            [withContext(prepareDispatcher)]
       ├─ 1. sections = ctx.state.promptSections ?: composer.sectionsTyped(request, toolSpecs)
       ├─ 2. toolSpecs = ctx.state.toolSpecs ?: ctx.tools.specs()      ← run 内快照（A-5）
       ├─ 3. full = sanitizeForProvider(system + body)
       ├─ 4. estimated = estimateMessages(full) + estimateToolSpecs(toolSpecs)   ← A-6
       ├─ 5. fingerprint = fingerprintOf(稳定段 + HISTORY 的 (n, firstId))
       ├─ 6. 与 ctx.state.lastPrefixFingerprint 比 ⇒ 变了就记一条 WARN（带 changed=）
       ├─ 7. estimated <= budget ⇒ 返回（记一行 INFO：prefix=<12hex> est=.. tools=..）
       └─ 8. 超预算 ⇒ 压缩（B 的级联）⇒ 再 sanitize ⇒ 记压缩行 ⇒ 更新指纹
```

关键约束（每条都有理由）：
- **步骤 1/2 用 run 内快照，不用 `ctx.tools.specs()` 的实时值**。理由：`TurnLoop.systemTextFor`
  已经把 system 快照进 `RunState`（`TurnLoop.kt:92-97`），工具声明漏了同一步 ⇒ §2.2 的抖动源。
- **指纹只对稳定段 + HISTORY 的 (条数, 首条 id) 计算**，**绝不对整个 `messages` 算**。
  理由：O(历史) 在长会话上是掉帧级开销（W2.5 §4.3 已登记此硬约束）。
  代价：历史**内容**变化（同条数同首 id 但内容不同）检测不到 —— 记为已知边界（§5 反证第 3 条）。
- **指纹变化必须 WARN，不能 INFO**。理由：`AgentLogStore` 满时优先丢 INFO（`AgentLogStore.kt:89-107`），
  而「前缀被谁改了」恰恰是排查时最该留下的那条。

#### A.5 压缩与断点对齐：让「压缩一次只失效一次」成立

两件事，分开做，缺一不可：

**(1) 水位线（治本，A-7）**
`WindowContextCompressor` 增加 `targetRatio` 参数：压缩后估算 ≤ `budgetTokens × targetRatio`。
`AgentPolicy.compressTargetRatio = 0.6f`。理由：读类工具结果上限 16 000 字符 ≈ 4 000 token
（`AgentPolicy.kt:28`），budget 24k 时 0.6 ⇒ 留 9.6k 余量 ≈ 2.4 条满额工具结果
⇒ 连续 2–3 轮不会复发。这是「稀有度」的唯一旋钮，`compressThreshold`（0.75）不动。

**(2) 切点对齐到 turn 边界（治标，A-8）**
`WindowContextCompressor` 增加 `cutPolicy` 参数：
- `MINIMAL`（默认值，**保持现役行为**，既有单测不受影响）：回溯到第一个非 TOOL 即可。
- `TURN_ALIGNED`（`ContextBudget` 用这个）：在 `MINIMAL` 的结果基础上，继续往回走到最近的
  `Role.USER`；若走不到（或走到 index 0 仍非 USER）则退回 `MINIMAL` 的结果。
  **额外丢弃上限**：对齐后丢弃的估算 token 不得超过 `budgetTokens × 0.25`，超了退回 `MINIMAL`
  并记 `compress_align=skipped`。

为什么值得：保留区若以 `MODEL` 开头，模型看到的是「一段没有对应问题的回答」——
这是语义退化（比丢 token 更贵）；同时对齐后压缩边界落在 turn 上，`historyStartId` 变化可解释。

为什么必须有上限：单个 turn 可能很长（一条 user + 30 轮工具），无上限对齐会把整段 turn 丢掉 ⇒
压缩完仍超预算 ⇒ 下一轮再压 ⇒ 退化成 §2.1 的病态。

**「压缩一次只失效一次」的机械表述**：压缩后记 `compressCount += 1`，并要求
「从压缩发生的那一轮起，接下来 `min(2, 剩余轮数)` 轮的 `prefixFingerprint.combined` 完全相同」。
这是 §4 的 A5 判据。

#### A.6 `cached_tokens` 的读取、分母与出口

**读**：`OpenAiCompatibleAdapter.decodeEvent` 已经读 `prompt_tokens_details.cached_tokens`
（`OpenAiCompatibleAdapter.kt:139`）。补一个**是否存在**的标记：

```kotlin
// GenerationEvent.kt
data class Usage(
    val prompt: Int,
    val completion: Int,
    val cachedPrompt: Int,
    /** provider 是否真的回了 cached 字段。false ⇒ cachedPrompt=0 不构成"没命中"的证据。 */
    val cachedReported: Boolean = false,
) : GenerationEvent
```

`AgentEvent.UsageUpdated` 同步加 `cachedReported: Boolean = false`。
**加法而非改 `Int` 为 `Int?`**：`TokenUsage` 是 `@Serializable`（`Generation.kt:44`），
改成 `Int?` 会改落盘格式；加一个带默认值的 `Boolean` 对 kotlinx.serialization 是向后兼容的。
`EventReducer.kt:272-275` 登记的「要能区分必须把契约改成 `Int?` / `cachedReported`」——
本设计选后者，因此**不需要**等 W6。

**命中率分母 = `prompt_tokens`（provider 回报）**，不是「可缓存前缀长度」。理由：
- `cached_tokens` 计入 `prompt_tokens`（GLM 计费示例：总输入 2000 = 命中 1200 + 新增 800），
  所以 `cached / prompt` = 「本次输入里有多少走了折扣价」，是唯一能用**权威值**算出的比率；
- 用前缀长度当分母必须自己估前缀 token（`TokenEstimator` 自陈低估中文 2–3 倍，
  `TokenEstimator.kt:12-13`）⇒ 会造出第二个非权威数字，且分母随压缩变化 ⇒ 跨轮不可比。

**另记 `prefixTokensEst`（分段估算，标注 estimated）只做诊断，不进比率。**
它的用途是一条**自洽性判据**：`prefixTokensEst > prompt` 说明估算器或前缀拼装有 bug
（前缀是 prompt 的子集）。这条比「命中率高低」更能早发现错。

**UI**：`EventReducer.buildUsageLabel` 在 `cachedReported == false` 时输出 `(n/a cached)`；
`true` 时维持现役 `(N cached, R%)`。这消灭了「未上报」伪装成「0 命中」的失真。

**日志**：`GenerationRound` 的 `Usage` 分支一行（`AgentLogStore.info`）：
```
usage: prompt=815017 completion=1024 cached=26496 reported=true hit=3.3% prefix=<12hex> changed=none
```
`prefix` / `changed` 由 `RunState.lastPrefixFingerprint` 与本次比对得出。

#### A.7 可观测点（三处出口，缺一不可）

| 出口 | 内容 | 为什么是这里 |
|---|---|---|
| `AgentLogStore`（WARN） | `prefix changed: <seg>... combined=<12hex>`；`prefix: sys=<12hex> tools=<12hex> skills=<12hex> memory=<12hex> est=<n> toolsEst=<n> msgs=<n>`（**不变时走 INFO**） | 200 条环形缓冲满时优先丢 INFO（`AgentLogStore.kt:89-107`）⇒ 「前缀被谁改了」必须 WARN 才留得住 |
| `AgentLogStore`（INFO，每次 usage） | `usage: prompt=.. completion=.. cached=.. reported=.. hit=..% prefix=<12hex> changed=none` | 跨 run 唯一可 grep 的证据面（`AgentLogFileStore` 落盘后 `adb pull` 可读） |
| UI `usageLabel` | `1.2k in / 340 out (200 cached, 16.7%)` 或 `(n/a cached)` | 与日志同源（`AgentEvent.UsageUpdated`），避免「界面一个数、日志另一个数」 |

**不做**：不在 UI 上显示指纹本身（12 位十六进制对用户无意义，且它是「前缀内容」的哈希 —— 
虽然不可逆，但没有展示价值，只增加泄漏面）。

#### A.8 失败语义

| 情形 | 行为 |
|---|---|
| 指纹计算异常（理论不可达：纯函数） | 不 try/catch —— 纯函数没有 IO，兜底只会掩盖 bug |
| `ctx.state.promptSections` 为 null（直接调 `prepare` 的单测） | 现场从 `request` + `toolSpecs` 重算，并记 `prefix_sections=lazy` |
| 工具面在 run 中途变化 | **run 内不可见**（快照语义）；下一 run 才生效，日志 `tools=<12hex>` 会变 |
| `cachedReported=false` | 记 `reported=false`，UI 显示 `n/a`，**不参与命中率统计** |

#### A.9 文件级任务清单

| # | 文件 | 新/改 | 内容 | 预估行数 |
|---|---|---|---|---|
| A-1 | `core-agent/.../context/PromptPrefix.kt` | **新增** | 3 个枚举/data class + `fingerprintOf` | ~150 |
| A-2 | `core-agent/.../prompt/AgentPromptComposer.kt` | 改 | 新增 `sectionsTyped()` 返回 `List<PromptSection>`；`sections()` 委托它（**段序只有一份定义**） | +50（134→184，cap 400） |
| A-3 | `core-agent/.../loop/RunState.kt` | 改 | 加 `promptSections` / `toolSpecs` / `lastPrefixFingerprint` / `compressCount` | +30（228→258） |
| A-4 | `core-agent/.../loop/TurnLoop.kt` | 改 | `systemTextFor` 同时快照 sections 与 toolSpecs | +15（98→113） |
| A-5 | `core-agent/.../loop/GenerationRound.kt` | 改 | `attemptOnce` 用 `ctx.state.toolSpecs ?: ctx.tools.specs()`；Usage 分支加账行 | +15（279→294，cap 400） |
| A-6 | `core-model/.../TokenEstimator.kt` | 改 | 新增 `estimateToolSpecs(specs)`（逐项 name/description/参数，每条 +4 协议开销） | +35（79→114） |
| A-7 | `core-agent/.../context/ContextCompressor.kt` | 改 | `targetRatio` 水位线 + `CutPolicy`（MINIMAL 默认 / TURN_ALIGNED）+ 对齐丢弃上限 | +55（253→308） |
| A-8 | `core-agent/.../loop/ContextBudget.kt` | 改 | 用 `TURN_ALIGNED` + 水位线；**预算补 `contextSafetyMarginRatio`（0.9）**；算指纹并比对；`withContext(prepareDispatcher)`；日志 | +80（85→165） |
| A-9 | `core-agent/.../policy/AgentPolicy.kt` | 改 | `compressTargetRatio=0.6f`、`compressAlignMaxExtraDropRatio=0.25f`、`contextSafetyMarginRatio=0.9f` | +25（120→145） |
| A-10 | `core-model/.../GenerationEvent.kt` | 改 | `Usage.cachedReported` | +6（124→130） |
| A-11 | `core-model/.../AgentEvent.kt` | 改 | `UsageUpdated.cachedReported`；新增 `ContextCompressed(...)` | +25（136→161） |
| A-12 | `core-engine/.../provider/OpenAiCompatibleAdapter.kt` | 改 | 解析 `cached_tokens` 时置 `cachedReported` | +8（359→367，cap 500） |
| A-13 | `feature-chat/.../EventReducer.kt` | 改 | `usageLabel` 支持 `n/a`；`ContextCompressed` 分支 | +30（293→323，cap 500） |
| A-14 | `feature-chat/.../ChatUiState.kt` | 改 | `ChatStatus.WAITING`；`lastCompression: CompressionNotice?` | +25（172→197） |
| A-15 | `feature-chat/src/main/res/values/strings.xml` | 改 | `chat_status_waiting` / `chat_context_compressed` | +2 |

**不改**：`AgentRunner.kt`（194 行，G12 上限 200 —— **只剩 6 行，本波一个参数都不许加**；
装配改由 `AppContainer` 传 `TurnLoop`，见 B-6）。

---

### B. 上下文压缩二期（L2 摘要压缩）

#### B.1 翻案理由（这段必须逐字进 `ContextSummarizer` 的 KDoc）

> 旧仓 Wave 35 把 `SummarizingContextCompressor` **判死**，理由是「摘要需要一次额外推理，
> 端侧 4B 上这笔开销换不来等价收益」。这条判断的前提是**推理在设备上跑**：
> 一次额外推理 = 数百毫秒到数秒的设备占用 + 电量 + 发热，而收益只是「少丢几条历史」。
>
> **API 路径下前提整体消失**：一次摘要调用是一次小请求（输入 = 被丢弃的那段历史，
> 输出 ≤ 512 token），成本以「千 token 的几分钱」计；而**替代方案是把整段对话丢掉**。
> 用户看到的是「模型忘了前面说过的」。⇒ 成本收益反转。
>
> 旧仓判死的**重启前提**是「出现估算失准事故」。W3 已交付 usage 账本
> （`RunState.recordUsage` / `recordEstimatedPrompt`，`RunState.kt:171-181`），
> 前提满足。
>
> ⚠️ 仍然成立的部分：**摘要不省钱**。它是「用一次廉价调用换回一段语义」，不是「压缩成本更低」。
> 所以它有严格的启用门槛（`summarizeMinTokens`）与硬失败回落（B.5），
> 不允许变成「每轮都摘一次」。

#### B.2 数据结构

新增 `core-agent/.../context/StructuredSummary.kt`（纯 JVM）：

```kotlin
/**
 * 结构化摘要。**不是散文**：五个固定段、固定顺序、固定 ASCII 头。
 * 定长格式的唯一目的是「可机械校验」—— 散文无法判定"有没有丢掉待办"。
 */
data class StructuredSummary(
    val goal: String,
    val confirmedFacts: List<String>,
    val decisions: List<String>,
    val openTodos: List<String>,
    val toolEffects: List<String>,
    val droppedMessageCount: Int,
) {
    companion object {
        // 段头常量：渲染与解析共用同一份，避免两处手写漂移
        const val HEADER = "[CONTEXT SUMMARY]"
        const val GOAL = "GOAL:"
        const val FACTS = "CONFIRMED FACTS:"
        const val DECISIONS = "DECISIONS:"
        const val TODOS = "OPEN TODOS:"
        const val EFFECTS = "TOOL SIDE EFFECTS:"
    }
}

/** 渲染成一条可直接插进 messages 的文本。纯函数，确定性（同输入同字节）。 */
fun StructuredSummary.render(): String

/** 校验模型输出。 */
sealed interface SummaryParse {
    data class Ok(val summary: StructuredSummary) : SummaryParse
    /** 缺必需段（GOAL / CONFIRMED FACTS）/ 无段头（散文）/ 超长截断。 */
    data class Rejected(val reason: String) : SummaryParse
}
fun parseStructuredSummary(raw: String, maxChars: Int): SummaryParse
```

**为什么必须有「拒绝」这一态**：若接受散文，`sanitizeForProvider` 与后续压缩都会把它当普通文本，
而「摘要里有没有待办」就永远无法判定 —— 那时 L2 的收益是**不可验证**的，
等于用一个不可证伪的机制换掉了可证伪的 L1。这是本仓最贵的失败形态
（`AGENTS.md` §二.3「未触发 ≠ 已验证通过」）。

#### B.3 端口与实现

```kotlin
// core-agent/.../context/ContextSummarizer.kt
interface ContextSummarizer {
    suspend fun summarize(messages: List<ChatMessage>, targetTokens: Int): SummaryResult
}

sealed interface SummaryResult {
    data class Ok(val text: String, val summaryTokens: Int, val elapsedMillis: Long) : SummaryResult
    data class Failed(val reason: String) : SummaryResult   // 超时/格式拒绝/引擎失败/取消外的异常
}

/**
 * 用 LlmEngine 直接发一次一次性请求。
 * **不进 TurnLoop、不带工具、不压缩、不重试** —— 任何一条违反都会造出递归或双倍账单。
 */
class EngineBackedSummarizer(
    private val engine: LlmEngine,
    private val timeoutMillis: Long = 20_000L,
    private val maxOutputTokens: Int = 512,
) : ContextSummarizer
```

三条硬约束：
1. **不带工具**（`tools = emptyList()`）。否则模型可能回 `tool_calls`，摘要为空。
2. **不重试**。失败即回落 L1；重试会让「压缩一次」变成「压缩 1+N 次」。
3. **`withTimeoutOrNull` 而不是 `withTimeout`**。`TimeoutCancellationException` 是
   `CancellationException` 子类，会被 `TurnContext.checkActive()` / `AgentRunner.executeLoop`
   接走，把「摘要超时」升级成「整 run 取消」（`AGENTS.md` §五.4；W2.5 §5.2 账 1 同一形态）。

#### B.4 级联控制流

新增 `core-agent/.../context/LayeredContextCompressor.kt`（实现 `ContextCompressor` 端口，
**不改 `WindowContextCompressor` 的对外行为**）：

```
compress(messages, budget):
  1. 若 estimate <= budget ⇒ 原样返回（无损，与 L1 同语义）
  2. l1 = WindowContextCompressor(TURN_ALIGNED, targetRatio).compress(...)   ← 先算 L1 切点
  3. 若 l1 与入参相同（L1 也压不动）⇒ 原样返回 + WARN（不试 L2：L2 也要切点）
  4. dropped = messages 减去 l1 的保留区（按 index 切片，不按内容比对）
  5. 若 estimate(dropped) < policy.summarizeMinTokens（默认 2000）⇒ 直接返回 l1，记 mode=L1
  6. r = summarizer?.summarize(dropped, targetTokens = policy.summarizeMaxTokens)
  7. r is Ok ⇒ 构造 summaryMessage（role=USER，id="summary:<runId>:<compressCount>"）
              ⇒ 返回 [summary] + l1 的保留区
  8. r is Failed / summarizer == null ⇒ 返回 l1，记 mode=L1_FALLBACK reason=..
```

**第 2 步复用 L1 切点是本设计的核心**：摘要消息插在 L1 保留区之前，而 L1 保留区的首条
已被 `safeCutIndex` 保证「不是孤儿 toolResult」⇒ `assistant.tool_calls ↔ tool` 配对
**由构造保证**，不依赖 `sanitizeForProvider` 事后补丁。这也意味着
`ContextBudget.kt:72` 的第二次 `sanitizeForProvider` 在本路径上是 no-op（可断言）。

**摘要消息的三个约束**：
1. `role = USER` —— **不能是 SYSTEM**。SYSTEM 段压缩前后逐字节相同是有 SHA-256 单测钉住的
   硬不变量（`ContextBudgetTest.compressionLeavesSystemBytesUntouched:65-76`），
   摘要进 system 会直接把它打破。
2. `id = "summary:<runId>:<compressCount>"` —— 稳定派生（不是 `newId()`）。
   理由与 `sanitizeForProvider` 的补丁 id 同源：id 抖动会改前缀字节（`ContextCompressor.kt:145-149`）。
3. **禁止被 `mergeAdjacentSameRole` 合并**。`canMerge` 加一条 `previous.id.startsWith("summary:")`
   → false。理由：若保留区首条是 USER，合并会把摘要与用户问题揉成一条，
   摘要的段头（`[CONTEXT SUMMARY]`）会被埋进正文中段 —— 模型对「这是压缩摘要」的识别会退化。

**为什么摘要不放尾部**：放尾部会让它每轮都随 HISTORY 位移，且模型会把「早先的摘要」
读成「最新的用户输入」。放首位 = 它扮演「被折叠的对话」的位置。

#### B.5 失败语义（一张表说清）

| 情形 | 行为 | 用户可见后果 |
|---|---|---|
| 摘要超时（20s） | 回落 L1 | 无（历史被丢弃，与现役一致） |
| 摘要格式拒绝（散文/缺段） | 回落 L1 + `WARN summary_rejected=<reason>` | 无 |
| 摘要超长（> `summarizeMaxTokens` 估算） | **截断到上限**后接受（`summary_truncated=true`），不回落 | 无；摘要仍优于全丢 |
| 引擎返回 `Failed`（401/429/5xx） | 回落 L1 | 无 |
| 协程取消 | `CancellationException` **原样上抛** | 用户点了停止 |
| `summarizer == null`（端点不支持工具/未装配） | 纯 L1 | 无 |
| L1 也压不动 | 原样返回 + `WARN`（现役行为，`ContextBudget.kt:62-66`） | 可能发出超预算请求（端点报错，可见） |

**一条不变量**：`LayeredContextCompressor` 返回的列表，其**保留区内容必须是入参的后缀**
（只允许在前面加一条摘要）。这条有单测钉住（§4 B4）。

#### B.6 `CONTEXT_OVERFLOW` 的压缩后重发（销 §2.7 第 1 条账）

现状：`GenerationRound` 把 `Attempt.Fatal` 一律 `emitFailed`，而 `EngineErrorKind.CONTEXT_OVERFLOW`
的 KDoc 承诺「触发压缩后重发一次」。

设计：
- `GenerationRound.runAttempt` 增加一条分支：`Attempt.Fatal` 且
  `failureKind == CONTEXT_OVERFLOW` 且 `!ctx.state.overflowRetried` ⇒ 置 `overflowRetried = true`，
  **强制一次压缩**（把 budget 按 0.5 打折传进去，逼它真的丢东西），然后走 `prepareRetry`
  的「重发一次」路径（不发 `Retrying` 事件？—— 要发，否则用户看到停顿无解释）。
- 只重发一次（`overflowRetried` 一次性闩）。第二次仍溢出 ⇒ 失败，文案带
  `context window exceeded`。
- 为什么打折到 0.5：现役估算低估（`TokenEstimator.kt:12-13`），按 1.0 压完可能仍然超。

⚠️ 这条会新增一条「失败分类参与控制流」的路径，属**语义变更**，必须在 §4 有独立判据（B6）。

#### B.7 可观测

压缩发生时（`ContextBudget` 内）emit `AgentEvent.ContextCompressed`：

```kotlin
data class ContextCompressed(
    val mode: String,            // "L1" / "L2" / "L1_FALLBACK"
    val reason: String,          // 回落原因（空 = 无）
    val droppedMessages: Int,
    val droppedTokens: Int,      // 估算
    val summaryTokens: Int,      // 0 = 无摘要
    val keptMessages: Int,
    val keptTokens: Int,         // 估算
    val elapsedMillis: Long,
) : AgentEvent
```
字段刻意用 `String` + 原始数值而不是枚举：与 `BottleneckReport` 同款理由
（`AgentEvent.kt:48-52`）—— 新增压缩模式不该要求改契约。

出口三处：
1. `AgentLogStore.warn`（**WARN 不是 INFO**，理由同 A.7）；
2. `EventReducer` 写入 `ChatUiState.lastCompression`；
3. `ChatContextMeter` 下方多渲染一行（`feature-chat`，不进 `feature-settings` ——
   本波不新建 `DiagnosticsScreen`，理由见 §6）。

#### B.8 文件级任务清单

| # | 文件 | 新/改 | 内容 | 预估行数 |
|---|---|---|---|---|
| B-1 | `core-agent/.../context/StructuredSummary.kt` | **新增** | 数据类 + `render()` + `parseStructuredSummary()` + 段头常量 | ~180 |
| B-2 | `core-agent/.../context/ContextSummarizer.kt` | **新增** | 端口 + `SummaryResult` + 摘要提示词常量（ASCII） | ~90 |
| B-3 | `core-agent/.../context/EngineBackedSummarizer.kt` | **新增** | `LlmEngine` 一次性调用实现（无工具/不重试/`withTimeoutOrNull`） | ~140 |
| B-4 | `core-agent/.../context/LayeredContextCompressor.kt` | **新增** | L2→L1 级联 + 摘要消息构造 | ~170 |
| B-5 | `core-agent/.../context/ContextCompressor.kt` | 改 | `canMerge` 加 `summary:` 前缀豁免 | +6（→314） |
| B-6 | `core-agent/.../loop/ContextBudget.kt` | 改 | 默认压缩器换 `LayeredContextCompressor`；emit `ContextCompressed` | +25（→180） |
| B-7 | `core-agent/.../loop/GenerationRound.kt` | 改 | `CONTEXT_OVERFLOW` 强制压缩 + 重发一次（`overflowRetried` 闩） | +35（→329） |
| B-8 | `core-agent/.../loop/RunState.kt` | 改 | `overflowRetried: Boolean` | +4（→262） |
| B-9 | `core-agent/.../policy/AgentPolicy.kt` | 改 | `summarizeMinTokens=2000`、`summarizeMaxTokens=512`、`summarizeTimeoutMillis=20000`、`overflowRetryBudgetRatio=0.5f` | +30（→170） |
| B-10 | `core-data/.../AppContainer.kt` | 改 | 装配：`TurnLoop(generation = GenerationRound(budget = ContextBudget(compressor = LayeredContextCompressor(summarizer = EngineBackedSummarizer(bridge)))))` 传给 `AgentRunner(loop = ...)` | +20（`AgentRunner` **零改动**） |
| B-11 | `feature-chat/.../EventReducer.kt` | 改 | `ContextCompressed` 写入 `lastCompression` | +12（→335） |
| B-12 | `feature-chat/.../ChatContextMeter.kt` | 改 | 第二行渲染压缩摘要（L2 / 丢弃 N 条） | +25（95→120） |

---

### C. 长上下文异步与进度

#### C.1 判断：现在到底哪里阻塞

| 环节 | 线程 | 长上下文下的代价 | 结论 |
|---|---|---|---|
| 网络等待（57.1s） | `Dispatchers.IO`（`RemoteLlmEngine.kt:156` `flowOn(IO)`） | 不占主线程 | **不阻塞** |
| `ContextBudget.prepare`（两次全量字符扫描 + 压缩器 costs 数组） | **Main**（`AgentRunner.kt:109-130` 的 `launch` 继承 `viewModelScope` 的 `Main.immediate`） | 815k token ≈ 3.2M 字符，每轮至少两遍 | **阻塞**，秒级 |
| `ToolResultCommit.commit`（截断 + 落工作副本） | Main | 单条上限 16k 字符 | 可接受，本波不动 |
| `ctx.emit` / `_state.update` | Main | 高频 delta | 设计如此（节流已做） |

⇒ **C 的第一件事是把 `prepare` 移出 Main**，不是加进度条。顺序反了就是「在掉帧的界面上
加一个更平滑的转圈」。

#### C.2 `prepare` 移出 Main

`ContextBudget` 增加构造参数 `prepareDispatcher: CoroutineDispatcher = Dispatchers.Default`，
`prepare` 全体包进 `withContext(prepareDispatcher)`。

安全性三条：
1. **事件顺序不变**：`prepare` 内 emit 的 `ContextCompressed` 走 `channelFlow.send`（线程安全），
   且发生在**任何生成事件之前**（它是本轮第一个动作）⇒ UI 观察到的顺序不变。
2. **`RunState` 无并发**：`withContext` 保持同一协程，`RunState` 仍是「单协程访问」
   （`RunState.kt:9-11` 的契约）。
3. **可测**：单测注入单线程 dispatcher（`newSingleThreadContext` 的 JVM 等价物），
   断言 `prepare` 的日志/事件在**非**测试主线程上发生 —— 或用更稳的形态：
   注入一个记录「被调用线程名」的 dispatcher 包装器。

⚠️ `core-agent` 不许碰 Android API（本仓铁律）。`Dispatchers.Default` 来自
`kotlinx-coroutines-core`，不是 Android API ⇒ 允许。同款先例：`ToolCallExecutor` 的
`ioDispatcher` 注入（W2 T-1）。

#### C.3 首字节看门狗 + 等待进度

新增 `core-agent/.../loop/FirstByteWatchdog.kt`：

```kotlin
/**
 * 首字节看门狗。**只管"一个字都没到"的等待期**，不管流式过程。
 *
 * 三条设计约束：
 * 1. 用 withTimeoutOrNull，不用 withTimeout —— 后者抛 TimeoutCancellationException，
 *    会被 checkActive / executeLoop 的 CancellationException 分支接走，
 *    把"单轮超时"升级成"整 run 取消"。
 * 2. 超时必须归类为**可重试**（Attempt.Retryable），不是 Fatal ——
 *    "首字节前超时"恰恰是最该重试的一类。
 * 3. 只在 sawAnyEvent == false 时计时；收到任一事件即停表。
 */
class FirstByteWatchdog(
    private val timeoutMillis: Long,
    private val tickMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
)
```

控制流（`GenerationRound.attemptOnce` 内，包住 `ctx.engine.generate(request).collect{...}`）：

```
coroutineScope {
  val ticker = launch { while (true) { delay(tick); if (!sawAnyEvent) emit(GenerationWaiting(elapsed, estPrompt)) } }
  try {
    val completed = withTimeoutOrNull(timeout) { collect { ... } }
    if (completed == null && !sawAnyEvent) return Attempt.Retryable("first byte timeout after ${timeout}ms")
  } finally { ticker.cancel() }
}
```

`AgentEvent.GenerationWaiting(elapsedMillis: Long, estimatedPromptTokens: Int)`。

#### C.4 UI：等待态

`ChatStatus.WAITING`（新增枚举值）+ `ChatUiState.waiting: WaitingNotice?`
（`elapsedMillis` + `promptTokens`）。`ChatScreen` 的状态行在 `WAITING` 时渲染
`等待响应 12s · 已发 228k token`（两个数字都是活的，1s 刷新一次）。

`estimatedPromptTokens` 从哪来：`TokenEstimator.estimateMessages(messages) + estimateToolSpecs(...)`，
**在 `prepare` 里顺手算好存进 `RunState.lastPromptEstimate`**（`prepare` 本来就要算，零额外开销）。

刷新频率：`waitingTickMillis = 1000`。不跟流式 120ms 节流混（那是文本通道）。

状态互斥：`WAITING` 在第一个 `TextDelta`/`ReasoningDelta` 到达时清掉
（`EventReducer.appendText` 已把 `status` 置 null，`appendReasoning` **没有** —— 这是一个真实缺口，
本波要补，否则只吐思考的模型会一直显示"等待响应"）。

#### C.5 超时/取消后的收敛

| 路径 | 现役 | 本波 |
|---|---|---|
| 首字节超时 | 无（只有 OkHttp 60s/read） | `Attempt.Retryable` → `Retrying` 事件 → 重试一次 → 仍超时则 `emitFailed` |
| 用户点停止 | `AgentRunner.cancel()` 三步（`AgentRunner.kt:175-181`） | 不变；看门狗 ticker 随 `coroutineScope` 取消 |
| 半截轨迹 | `EventReducer.finish/fail` 把 `toolTraces` 搬进气泡（`EventReducer.kt:192`、`:236`） | 不变 |
| `isStreaming` 卡死 | `runOnce` 的 `finally` 兜底（`ChatViewModel.kt:354-362`） | 不变 |

**一条要新增的不变量**：`WAITING` 态必须在**三个终态事件**（`Finished`/`Failed`/`Cancelled`）
里都被清掉。`EventReducer.finish`/`fail` 目前只清 `pendingApproval`，不清 `status`
（`finish` 会覆盖成 `CANCELLED_EMPTY` 或 null，`fail` **完全不碰 `status`**）⇒
若失败发生在等待期，`WAITING` 会残留。本波要补（`fail` 里 `status = null`）。

#### C.6 `maxContextTokens` 配置通路（C 的真机判据前提）

| # | 文件 | 内容 |
|---|---|---|
| C-1 | `core-data/.../SettingsStore`（或既有 DataStore 封装） | 新增 `maxContextTokens: Flow<Int>`，默认 32 000 |
| C-2 | `core-data/.../AppContainer.kt` | `agentRunnerFor` 构造 `AgentPolicy(maxContextTokens = settings...)` |
| C-3 | `feature-settings/.../SettingsScreen.kt` + `SettingsViewModel.kt` | 一行数值输入（上限 1 000 000，下限 4 000） |
| C-4 | `feature-settings/src/main/res/values/strings.xml` | 标题/说明两条 |

**若 C-6 不做**：§4 的 C5（815k 真机）**必须标 `⛔ vacuous`**，不得写成「未通过」，
也不得用 32k 下的现象外推 815k。

#### C.7 文件级任务清单

| # | 文件 | 新/改 | 内容 | 预估行数 |
|---|---|---|---|---|
| C-1 | `core-agent/.../loop/FirstByteWatchdog.kt` | **新增** | 看门狗 + `withTimeoutOrNull` 语义 | ~110 |
| C-2 | `core-agent/.../loop/GenerationRound.kt` | 改 | 接入看门狗；`sawAnyEvent` 复用既有变量（`GenerationRound.kt:118`） | +30（→359，cap 400 ⚠️ 余量 41） |
| C-3 | `core-agent/.../policy/AgentPolicy.kt` | 改 | `firstByteTimeoutMillis`（**复用/重命名** `generationTimeoutMillis`，见 §5 风险 4）、`waitingTickMillis=1000` | +10 |
| C-4 | `core-model/.../AgentEvent.kt` | 改 | `GenerationWaiting(elapsedMillis, estimatedPromptTokens)` | +10（→171） |
| C-5 | `feature-chat/.../ChatUiState.kt` | 改 | `ChatStatus.WAITING` + `WaitingNotice` | +18（→215） |
| C-6 | `feature-chat/.../EventReducer.kt` | 改 | `GenerationWaiting` 分支；`fail` 清 `status`；`appendReasoning` 清 `status` | +18（→353） |
| C-7 | `feature-chat/.../ChatScreen.kt` | 改 | `WAITING` 文案（带秒数/token） | +20（~200→220） |
| C-8 | `feature-chat/src/main/res/values/strings.xml` | 改 | `chat_status_waiting`（带两个占位符） | +2 |
| C-9 | `core-agent/.../loop/ContextBudget.kt` | 改 | `withContext(prepareDispatcher)`；存 `lastPromptEstimate` | +15（→195） |
| C-10 | 见 C.6 的 4 个文件 | 改 | `maxContextTokens` 配置通路 | +60 |

⚠️ `GenerationRound.kt` 在本波被 A-5 / B-7 / C-2 三次加长：279 → 294 → 329 → **359**。
上限 400，余量 41。**若实施中超出 400，必须拆文件（不是调上限）**：
建议拆出 `core-agent/.../loop/AttemptLoop.kt` 承载 `runAttempt` / `prepareRetry`
（`AGENTS.md` §五.1「超了就拆，不要调上限」）。

---

## 4. 验收判据

> 每条都能用**一条命令**或**一条单测**判定。带「空命中语义」列的是容易假绿的判据。

### A 组

| # | 判据 | 判定方式 | 空命中语义 / 反例 |
|---|---|---|---|
| A1 | 压缩后**不会**下一轮立刻复发 | 单测：`compress(msgs, budget=B)` 返回 `kept`；`estimate(kept) ≤ 0.6B`；再 `appendMessage(一条 0.2B 的消息)` 后 `estimate ≤ B` ⇒ 第二次 `compress` 返回**同一对象** | 若 `kept` 估算恰等于 `B` ⇒ 判红（水位线没生效） |
| A2 | 段顺序只有一份定义 | `grep -c "fun sections" AgentPromptComposer.kt` = 1 个权威实现 + `sections()` 是委托（单测：`sections(req, tools) == sectionsTyped(req, tools).map{it.text}`） | 两条独立实现 ⇒ 判红 |
| A3 | 同 run 内前缀指纹逐字节稳定 | 单测：同一 `ctx` 连调两次 `prepare`（中间只 `appendMessage` 一条 tool 结果）⇒ 两次 `fingerprint.combined` 相同 | 若两次都返回 null（未计算）⇒ `⛔ vacuous`，判红 |
| A4 | 工具面变化**在 run 内不可见** | 单测：`prepare` 之后 `ctx.tools.unregister("calculator")`，再 `prepare` ⇒ `fingerprint` 的 `TOOLS_SCHEMA` 段 hash **不变** | 变了 ⇒ 快照没接上，判红 |
| A5 | 压缩一次只失效一次 | 单测：构造 3 轮，第 2 轮触发压缩；断言第 2、3 轮的 `fingerprint.combined` 相同，且第 1、2 轮不同 | 若第 3 轮又不同 ⇒ 水位线/对齐有一处失效，判红 |
| A6 | 工具声明计入预算 | 单测（W2.5 §4.2 的三条）：① 6 内置工具 `estimateToolSpecs ∈ (0, 2000)`；② 构造 `messages < budget < messages+tools` ⇒ `prepare` **触发压缩**（现役不触发）；③ `specs=emptyList()` 时 `estimated` 与改动前逐值相同 | ③ 不成立 ⇒ 改了既有语义，判红 |
| A7 | `cached_tokens` 未上报可分辨 | 单测：adapter 解码一份**无** `prompt_tokens_details` 的 usage 帧 ⇒ `cachedReported=false`；解码一份 `cached_tokens=0` 的 ⇒ `cachedReported=true, cachedPrompt=0` | 两者结果相同 ⇒ 判红 |
| A8 | UI 不再把「未上报」显示成「0 命中」 | 单测：`EventReducer.reduce(UsageUpdated(prompt=1000, completion=10, cachedPrompt=0, cachedReported=false))` ⇒ `usageLabel` 含 `n/a`；`cachedReported=true` ⇒ 含 `0.0%` | 两条输出相同 ⇒ 判红 |
| A9 | 命中率分母是 `prompt_tokens` | 单测：`cachedPrompt=600, prompt=1000` ⇒ 标签含 `60.0%`（**不是** 600/prefixEst） | 出现第二个分母 ⇒ 判红 |
| A10 | 压缩切点对齐到 USER（当且仅当不超额外丢弃上限） | 单测两条：① 构造「USER + 5 条 MODEL/TOOL 交替」，`TURN_ALIGNED` 的保留区首条是 USER；② 构造单个巨型 turn，对齐会多丢 > 25% ⇒ 退回 MINIMAL 且日志含 `compress_align=skipped` | ② 未退回 ⇒ 判红（会退化成每轮压缩） |
| A11 | 指纹变化是 WARN 级 | 单测：`AgentLogStore.clear()` → 触发一次指纹变化 → `recent().any { it.level == WARN && it.message.contains("prefix changed") }` | 只有 INFO ⇒ 判红（会被环形缓冲冲掉） |
| A12 | 预算留 10% 安全余量 | 单测：`contextBudgetTokens(endpoint(contextLength=200_000, maxOutput=4_000), policy(maxContextTokens=200_000))` ⇒ 结果 ≤ `(200_000-4_000) × 0.75 × 0.9` | 结果 > 该上界 ⇒ 判红（实测 chars/token 在 4.0–5.3 间浮动，不留余量会被端点 400） |
| A13 | `prefixTokensEst > prompt` 时报警 | 单测：构造 `prefixTokensEst` 明显大于 provider 回报 `prompt` 的场景 ⇒ `AgentLogStore` 出现 `prefix estimate exceeds prompt` | 无告警 ⇒ 判红（说明自洽性判据没接） |

### B 组

| # | 判据 | 判定方式 | 空命中语义 / 反例 |
|---|---|---|---|
| B1 | 结构化摘要格式可机械校验 | 单测：`parseStructuredSummary` 对 5 个固定段齐全的文本返回 `Ok`；对纯散文返回 `Rejected`；缺 `OPEN TODOS` 仍 `Ok`（可选段）但 `missing` 可见 | 散文也被接受 ⇒ 判红 |
| B2 | 摘要消息不破坏 tool 配对 | 单测：`LayeredContextCompressor` 输出 `sanitizeForProvider` 后**与输入长度相同**（即 sanitize 是 no-op） | 若 sanitize 补了补丁消息 ⇒ 判红（说明切点拆了配对） |
| B3 | 摘要消息进不了 system | 单测：压缩后 `first { it.role == SYSTEM }` 的 SHA-256 与压缩前相同 | 变了 ⇒ 判红 |
| B4 | 保留区必须是入参后缀 | 单测：`out.drop(1)`（去掉摘要）与 `input.takeLast(n)` 逐条 `id` 相同 | 顺序被重排 ⇒ 判红 |
| B5 | 摘要失败回落 L1，run 不失败 | 单测：注入一个恒 `Failed` 的 summarizer ⇒ `compress` 返回非空列表、`mode=L1_FALLBACK`、**不抛异常** | 抛异常 ⇒ 判红 |
| B6 | `CONTEXT_OVERFLOW` 触发压缩后重发一次 | 单测（FakeEngine）：第一次 `generate` 回 `Failed(CONTEXT_OVERFLOW, retryable=false)`，第二次回正常文本 ⇒ run 以 `Finished` 结束，且 `AgentLogStore` 出现 `overflow retry`；`FakeEngine` 的请求计数 = 2 | 计数 = 1（没重发）或 > 2（无限重发）⇒ 判红 |
| B7 | 摘要调用不带工具、不重试 | 单测：`EngineBackedSummarizer` 收到的 `GenerationRequest.tools` 为空；引擎抛一次可重试错误后**不再发第二次** | 有第二次 ⇒ 判红（账单翻倍） |
| B8 | 摘要超时不升级成整 run 取消 | 单测：summarizer 的 `delay` 超过 `summarizeTimeoutMillis` ⇒ `compress` 正常返回 L1，**不抛 `CancellationException`** | 抛了 ⇒ 判红（这是 `withTimeout` vs `withTimeoutOrNull` 的分界） |
| B9 | 摘要长度有硬上限 | 单测：summarizer 返回 5000 字符 ⇒ 接受后 `summaryTokens ≤ summarizeMaxTokens` 且 `summary_truncated` 可见 | 无上限 ⇒ 判红 |
| B10 | `mergeAdjacentSameRole` 不吞摘要 | 单测：`[summary(USER), USER("hi")]` 经 `sanitizeForProvider` 后仍是 2 条 | 合成 1 条 ⇒ 判红 |

### C 组

| # | 判据 | 判定方式 | 空命中语义 / 反例 |
|---|---|---|---|
| C1 | `prepare` 不在调用方线程执行 | 单测：注入记录线程名的 dispatcher ⇒ `prepare` 内记录的线程名 ≠ 测试线程名 | 若 dispatcher 是 `Dispatchers.Unconfined` ⇒ `⛔ vacuous`，判红 |
| C2 | 首字节超时归类为**可重试** | 单测（FakeEngine 永不发事件）：`GenerationRound(maxRetries=1)` ⇒ 事件序列含 `Retrying`，**不含** `Failed`（第一次）；第二次仍超时 ⇒ 含 `Failed` | 直接 `Failed` ⇒ 判红（最该重试的路径反而没重试） |
| C3 | 首字节超时不升级成整 run 取消 | 单测：同上，`run` 正常返回 `RoundOutcome.Terminated` 且**不抛 `CancellationException`** | 抛了 ⇒ 判红 |
| C4 | 等待期有可见进度 | 单测（FakeEngine `delay(3 * tickMillis)` 后发一个 delta）：事件序列含 ≥2 个 `GenerationWaiting`，且**第一个 `TextDelta` 之后不再有** `GenerationWaiting` | 0 个 ⇒ 判红；delta 之后还有 ⇒ 判红（表没停） |
| C5 | 815k 真机可行使（**需 C-6 先落地**） | 真机：端点 `contextLength=1000000`、设置页 `maxContextTokens=900000`；发一次 ≈800k token 的请求（`file_read` 分页累加）；设备侧 logcat 落盘后 `grep "usage: prompt="` | **C-6 未落地 ⇒ `⛔ vacuous`**，不得写「未通过」 |
| C6 | 815k 等待期 UI 有秒数与 token | 真机截图：等待期间状态行显示 `等待响应 NN s · 已发 NNNk token` 且秒数在增长 | 只显示静态文案 ⇒ 判红 |
| C7 | 停止后 5s 内收敛 | 真机：等待期点停止 ⇒ 5s 内 `isStreaming=false`、出现 `已停止` 或气泡落定；`grep` 设备日志有 `Cancelled` | 5s 后仍 `isStreaming` ⇒ 判红 |
| C8 | `WAITING` 不残留 | 单测：`WAITING` 后接 `Failed` ⇒ `state.status == null`；接 `appendReasoning` ⇒ `status == null` | 残留 ⇒ 判红 |

### X 组（跨切面，缺一即返工）

| # | 判据 | 判定方式 |
|---|---|---|
| X1 | 11 模块不变、无新依赖 | `grep -c "^include" settings.gradle.kts` = 11；`git diff build.gradle.kts */build.gradle.kts` 无新增 `implementation` |
| X2 | 架构守卫全绿 | `bash scripts/arch-guard.sh` ⇒ `23 OK / 0 VIOLATION / 0 SELF_FAULT`（**必须在仓库根目录跑**） |
| X3 | 单文件行数闸门 | `bash scripts/arch-guard.sh --verbose` 的 G10 全绿；特别复核 `GenerationRound.kt` ≤ 400、`AgentRunner.kt` ≤ 200 |
| X4 | `@Test` 只增不减且双向对账 | `grep -rnE "^\s*@Test\b" --include=*.kt . \| wc -l` = **556**；`.github/workflows/build.yml` 的 `EXPECTED_TESTS` = `'556'`（同一次提交） |
| X5 | 无中文硬编码 | G16 全绿（新增 KDoc 可以有中文，**字符串字面量不行**；`core-*/src/main` 与 `feature-*/src/main` 都扫） |
| X6 | `core-agent` 仍纯 JVM | `./scripts/localbuild.sh :core-agent:test` 能跑（不依赖 Android 运行时）；`grep -rn "android\." core-agent/src/main` 零命中 |
| X7 | 纯文档提交不触发 CI 是既定口径 | 本设计文件单独提交时 CI 0 run **不是**失败（`AGENTS.md` §八） |

---

## 5. 风险与反证条件

> 每条都写「出现什么现象 ⇒ 说明设计错了 ⇒ 怎么退」。

| # | 风险 | 反证条件（出现即回退） | 回退动作 |
|---|---|---|---|
| 1 | **A 退化成纯观测**：指纹账跑通了，但缓存命中率没变（供应商根本没做前缀缓存，或前缀仍低于其写入门槛） | 连续 3 个 run 的 `prefix changed=none` 且 `cachedReported=true` 且 `cached=0` 且 `prompt ≥ 4000` | 接受「本供应商不给缓存」这一事实，**不再投入**；保留指纹账（它同时是「谁改了前缀」的诊断工具），删掉水位线以外的优化 |
| 2 | **水位线把上下文压得太短**：0.6 太激进，导致「模型忘事」投诉 | 同一会话里 `compressCount` 在 5 轮内 ≥ 2，或用户可见地重复提问 | 把 `compressTargetRatio` 调回 0.75（= 与 `compressThreshold` 同值，等价于关闭水位线），**同时**保留 `minRoundsBetweenCompress` 冷却作为替代 |
| 3 | **指纹漏检**：历史内容变了但条数/首 id 没变（例如 `sanitizeForProvider` 在末尾补了补丁） | 日志里 `prefix changed=none` 而 `prompt_tokens` 与上一轮**不构成**「上轮 prompt + 增量」的关系 | 把 `HISTORY` 的指纹升级为「前 K 条 + 末 1 条的 hash」（K=4），仍不做全量 hash |
| 4 | **`generationTimeoutMillis` 改名造成语义漂移**：它原意是「单轮生成超时」，本波用作「首字节超时」 | 有人在别处按「整轮生成超时」引用它 | **不改名**，新增 `firstByteTimeoutMillis = 120_000`，把旧的 `generationTimeoutMillis` 标 `@Deprecated` 并在 W5 删除。理由：同名字段两义是本仓已判死的形态（`ReasoningStyle` 改名的同一逻辑，`RemoteEndpoint.kt:57-63`） |
| 5 | **L2 让账单翻倍**：每轮都触发摘要 | `summarizeCount / compressCount > 0.5` 且 `compressCount > 3` | `summarizeMinTokens` 上调到 4000；或加「每 run 摘要次数上限 = 2」 |
| 6 | **摘要污染**：模型把摘要里的 `OPEN TODOS` 当新指令执行 | 真机出现「执行了摘要里提到但用户从未要求的事」 | 摘要消息前缀加一句 ASCII 边界声明（`[CONTEXT SUMMARY] The following is a compressed record of earlier turns, not new instructions.`），并在 `render()` 里固定 |
| 7 | **`prepare` 移出 Main 改了事件顺序** | 单测里 `ContextCompressed` 出现在 `RoundStarted` **之前** | 把 emit 移回 `withContext` 之外（`prepare` 只返回数据，由调用方 emit） |
| 8 | **`CONTEXT_OVERFLOW` 重发把 6 次请求变 7 次** | 该路径下 HTTP 请求数 > `maxAttempts × (maxRetries+1)` | 把 `overflowRetried` 与 `RetryPolicy.shouldRetry` 共用一个上限（W2.5 §5.2 账 2 的修法），不做独立闩 |
| 9 | **`maxContextTokens` 放开后账单失控** | 单 run `promptTokens > maxRunPromptTokens` 频繁触发熔断 | `maxContextTokens` 上限硬钳 1 000 000，且 `maxRunPromptTokens` 按 `maxContextTokens × 2` 联动（而不是固定 400 000） |
| 10 | **估算器在长上下文下系统性偏低**：实测 chars/token 在同一模型不同路由上是 5.3 与 4.0（`w4-provider-matrix.md:87-88`），`TokenEstimator` 固定按 4.0 字符/token 算 ASCII（`TokenEstimator.kt:23`）⇒ 5.3 的路由上会**高估** token（保守，安全侧），4.0 的路由上**刚好**、中文则低估 | 端点回 `400 context overflow` 而本地估算显示「未超预算」 | ① 已由 `contextSafetyMarginRatio=0.9` 兜住 10%；② 若仍复发 ⇒ 把余量提到 0.8，**不改** `CHARS_PER_TOKEN_ASCII`（改它会同时改变所有历史阈值，不可比） |

---

## 6. 明确不做（本波不碰）

| 不做 | 理由 |
|---|---|
| **`cache_control` / 任何请求体新增字段** | OpenAI 兼容族与 GLM 都没有该字段 ⇒ 空转实现（`00-brief` A5 判死标准）。段边界已就位，Anthropic 在 W6 只需加一个渲染函数 |
| **`reasoning_content` 回传（I3/B2）** | 要动 `ChatMessage` 契约（`@Serializable` 加字段 = 落盘格式变）+ `RunState` 按轮切分文本。W2.5 决策 D-2 已定不修；本波不做，但**必须在 §5 风险 1 的归因里记住它**：GLM 上它会同时压低质量与命中率 |
| **`working` 作为唯一历史真值源**（删掉 UI 气泡重建） | 影响面覆盖 journal 与恢复；W2.5 §4.4 已判「W2.5 不做」，本波理由相同 |
| **`journal` / `FreezeClock`** | W3 挂账 W3-05，与上下文工程正交 |
| **`DiagnosticsScreen`** | 新建一个设置页入口要改 `SettingsRoute` + 新 Screen + 新 ViewModel，收益是「能看日志」—— 而日志已能 `adb` 取。本波把压缩信息放 `ChatContextMeter` 第二行（离用户最近的地方），**不新建设置页** |
| **`ToolResultCommit.commit` 移出 Main** | 会动 `MessageCommitted` 与 `ToolResultReceived` 的事件顺序语义（W2.5 §3.1(h)-2 已登记）。`prepare` 移出 Main 的收益大一个量级（O(历史) vs 单条） |
| **DeepSeek `prompt_cache_hit_tokens` 解析** | 属 W6 多供应商。本波用 `cachedReported=false` 让它**可见**（而不是静默 0），这已足够 |
| **`Microcompact`（清旧 tool result 但保留结构）** | 旧仓 parked 项（`w25-prompt-cache-and-w3-design.md:192`）。它要动 `ChatMessage` 形态（把 output 清空但保留 callId），与 B 的摘要消息是两套「压缩表示」，同波做会打架 |
| **调 `compressThreshold`（0.75）** | 它是「压缩稀有度」的既有旋钮，改它会让本波验收与 W3 基线不可比。本波用**新增**的 `compressTargetRatio` 做水位线，不动旧值 |
| **`@Test` 数「≥N」式判据** | 本仓已判死（roadmap §W3 判据 6 的注：写死「≥200」已失去意义）。本波用精确值 556 双向对账 |

---

## 7. 测试增量预算

当前严格口径 `@Test` = **480**（`grep -rnE "^\s*@Test\b" --include=*.kt . | wc -l`），
`build.yml` 的 `EXPECTED_TESTS = '480'`（`:115`）。

| 组 | 文件 | 新/改 | 新增 @Test |
|---|---|---|---|
| A | `core-agent/src/test/.../context/PromptPrefixTest.kt` | **新增** | 8 |
| A | `core-agent/src/test/.../context/ContextCompressorTest.kt` | 改 | 7（水位线 2 / TURN_ALIGNED 2 / 对齐上限 1 / MINIMAL 回归 1 / **10% 安全余量 1**） |
| A | `core-agent/src/test/.../loop/ContextBudgetTest.kt` | 改 | 7（指纹稳定 2 / 工具快照 1 / 指纹 WARN 1 / 压缩一次失效一次 1 / prepareDispatcher 1 / **prefixEst 超 prompt 告警 1**） |
| A | `core-agent/src/test/.../prompt/AgentPromptComposerTest.kt` | 改 | 3（sectionsTyped 与 sections 逐项等价 / 段 kind 顺序 / 空段不出现） |
| A | `core-model/src/test/.../TokenEstimatorTest.kt` | 改 | 4（W2.5 §4.2 三条 + 空工具面） |
| A | `feature-chat/src/test/.../EventReducerTest.kt` | 改 | 4（n/a 1 / 0.0% 1 / 分母 1 / ContextCompressed 1） |
| **A 小计** | | | **33** |
| B | `core-agent/src/test/.../context/StructuredSummaryTest.kt` | **新增** | 10 |
| B | `core-agent/src/test/.../context/LayeredContextCompressorTest.kt` | **新增** | 9 |
| B | `core-agent/src/test/.../context/EngineBackedSummarizerTest.kt` | **新增** | 6 |
| B | `core-agent/src/test/.../loop/GenerationRoundTest.kt` | **新增** | 4（overflow 重发 / 只重发一次 / 失败文案 / 与 Retrying 事件共存） |
| B | `core-agent/src/test/.../loop/ContextBudgetTest.kt` | 改 | 3（L2 走通 / L1 回落 / summary 消息不进 system） |
| **B 小计** | | | **32** |
| C | `core-agent/src/test/.../loop/FirstByteWatchdogTest.kt` | **新增** | 5 |
| C | `core-agent/src/test/.../loop/GenerationRoundTest.kt` | 同 B 的新文件 | 4（超时可重试 / 不升级取消 / 收到事件即停表 / 二次超时失败） |
| C | `feature-chat/src/test/.../EventReducerTest.kt` | 改 | 2（WAITING 渲染 / fail 清 status） |
| **C 小计** | | | **11** |
| **合计** | | | **76** |

⇒ 目标 `@Test = 480 + 76 = 556`，`build.yml` 的 `EXPECTED_TESTS` 同步改为 `'556'`（同一次提交）。

**新增测试文件 6 个**：`PromptPrefixTest` / `StructuredSummaryTest` /
`LayeredContextCompressorTest` / `EngineBackedSummarizerTest` /
`FirstByteWatchdogTest` / `GenerationRoundTest`。

**JUnit4 纪律**（本仓已踩过）：测试方法返回 `Unit`（写 `fun x()`，不写表达式体）；
方法名禁 `. ; [ /`（中文名里「A / B」改「A 与 B」）；`assertFailsWith` 只在 `@Test` 内且其后无裸调用。

---

## 8. 实施顺序建议（依赖关系决定，不是偏好）

```
1. A-6 TokenEstimator.estimateToolSpecs     ← 无依赖，且 B 的 summarizeMinTokens 判定要用它
2. A-1 PromptPrefix + A-2 sectionsTyped     ← 纯函数，可独立单测
3. A-7 ContextCompressor（水位线 + CutPolicy）← A 的最大收益，独立可测
4. A-3/A-4/A-5/A-8 快照 + 指纹 + 装配       ← 依赖 1/2/3
5. B-1/B-2/B-3 摘要三件（纯逻辑 + 端口）    ← 依赖 1（估算）
6. B-4/B-5/B-6 级联 + 装配                  ← 依赖 3/5
7. B-7 CONTEXT_OVERFLOW 重发                ← 依赖 6
8. C-1/C-2 看门狗                           ← 无依赖（可与 5/6 并行）
9. C-9 prepareDispatcher                    ← 依赖 4（同一文件）
10. C-6 maxContextTokens 通路               ← 无依赖，但 C5 真机判据依赖它
11. A-10..A-15 / C-3..C-8 UI 与契约         ← 最后，一次改完避免多次改 `when` 分支
```

**每一步都能独立编译 + 独立单测通过**。禁止「先全改完再一起跑」——
本仓 30+ 处实证说明「报告说通过了」不等于通过。
