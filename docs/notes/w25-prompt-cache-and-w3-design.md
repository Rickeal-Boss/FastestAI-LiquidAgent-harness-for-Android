# W2.5 前置调研：GLM 上下文缓存 + 现役树缓存友好性审计 + W3 范围建议

> **性质**：调研与设计笔记，**不含任何生产代码改动**。
> **基线**：`HEAD 37465b4`（`docs(ci): 架构守卫 job 标注 18 条 -> 23 条判据`）。
> 工作树当时正被 T-1..T-6 修补（`git status` 15 个 M），因此**凡引用被改过的文件，行号一律取
> `git show HEAD:<path>`**；未被改动的文件行号即当前工作树行号。行号漂移时以符号名 grep 为准。
> **🔴 修订记录（2026-10-31 第三次，**优先级最高**）**：第二次修订里「最小可缓存前缀 = 4096」**被新数据推翻**。
> 20 题真机跑分出现 `prompt=928 / cached=896`，与 4096 门槛直接冲突 ⇒ 复测（`probe_cache2.py`，控制 tools / 间隔 / 长度）：
>
> | prompt_tokens | 第 2 次 cached |
> |---|---|
> | 574 / 586 / 1691 / 1923 / 2292 / **2303** | **0** |
> | **2502** | **2432** |
> | 4111 / 6426 / 7352 | 4096 / 6400 / 7296 |
> | 真机 928（会话内第 2 轮） | **896** |
>
> **修正后的模型**：门槛在 **(2303, 2502]**，它约束的是「**创建缓存条目**」；**命中不受门槛约束** ——
> 一个 928 token 的请求可以命中一条更长条目的前 896 token。粒度仍是 64。
> 因此：现役前缀 ~800 **首轮必 0**（正确），但会话里一旦出现 ≥2.5k 的请求，后续短请求就能吃到共享前缀的折扣
> （20 题整体 Σcached/Σprompt = 1792/32657 = **5.5%**，首次命中 seq=42）。
> **正文中凡出现「门槛 4096」的表述，一律以本块为准**（原文保留以留痕，不逐句改写）。
>
> **修订记录（2026-10-30 第二次）**：① 主理人 GLM-4.7 实测数据回填（§1.2b：门槛 4096 / 粒度 64 /
> 同步生效），据此订正了全篇的阈值与 I2 的严重度；② T-1 的实际落点由实施者复核更正为
> 「只切 `tool.invoke`，事件线程不变」（§3.1(h)-2）—— 内核循环整体仍在主线程，此结论不变。
> **外部文档取证时间**：2026-10-30。
>
> **判据纪律**（本笔记的每一条都遵守）：
> 1. 结论必须能指到 `文件:行` 或 URL；
> 2. 推断标【推断】，并给出**可证伪的方法**（不是"我认为"，是"这样测就能推翻我"）；
> 3. 写进方案的判据必须给出**触发条件 + 空命中语义**——"0 命中 ≠ 通过"；
> 4. 「未触发」≠「已验证通过」，没跑到的写 `⛔ 未取证`。

---

## 0. 五条最关键结论（先看这个）

| # | 结论 | 证据 |
|---|---|---|
| 1 | **GLM 是纯隐式前缀缓存，没有显式缓存创建/复用 API、没有 `cache_control` 断点**；命中量走 `usage.prompt_tokens_details.cached_tokens`，命中价 = 标准价 **50%** | `https://docs.bigmodel.cn/cn/guide/capabilities/cache` |
| 2 | **实测：GLM-4.7 的最小可缓存前缀是 4096 token、粒度 64 token、秒级同步生效**（主理人对照实验，§1.2b）；本仓现役静态前缀（system + 6 个工具 schema）≈**700–800 token** ⇒ **必然 0 命中**，不是"灰带"、不是"GLM 没缓存"。文档写的"建议 500"低了一个数量级 | §1.2b 实测表；前缀长度测算见 §3.4 |
| 3 | **跨轮（turn）前缀在"有工具调用的轮次"处分叉**：`ChatViewModel` 每次发送都从 **UI 气泡**重建 history（工具轨迹不进 history），而内核 `RunState.working` 里是另一份（含 `MODEL(toolCalls)` + `TOOL`）⇒ **从会话中第一个含工具调用的轮次起**，重建出的序列与上一 run 实际发出的序列必然不同，该轮及其之后全部按全价重算。**边界**：纯文本轮（无工具调用）仍是严格前缀、仍可命中 | `feature-chat/.../ChatViewModel.kt:184`（HEAD）+ `:355-359`（HEAD）；`ToolResultCommit.kt:67` |
| 4 | **GLM-4.7 明文要求「交错思考 + 工具」时把 `reasoning_content` 原样回传**，否则降低效果并影响缓存命中；本仓契约是「reasoning 不进 messages」（只计字符数）⇒ **当前实现直接违反 GLM-4.7 的推荐用法**，这是 20 题止损跑分的一个**混淆变量** | `https://docs.bigmodel.cn/cn/guide/capabilities/thinking-mode`；`OpenAiCompatibleAdapter.kt:193-222`（无 reasoning 字段）；`RunState.kt:141-143` |
| 5 | 现役树**没有**时间戳/随机量污染 system 段，工具顺序也稳定（按 name 排序）；真正的问题不在"抖动"，而在**跨轮真值源分裂**与**观测面缺失** | `AgentPromptComposer.kt:113-130`；`ToolRegistry.kt:91`；`EventReducer.kt:230`（HEAD） |

---

## 1. BigModel / GLM 的上下文缓存机制（外部调研）

### 1.1 取证来源与可靠性

| 来源 | URL | 结论可用性 |
|---|---|---|
| 上下文缓存（官方能力页） | `https://docs.bigmodel.cn/cn/guide/capabilities/cache` | ✅ 一手，机制/计费/示例齐全 |
| 思考模式（官方能力页） | `https://docs.bigmodel.cn/cn/guide/capabilities/thinking-mode` | ✅ 一手，含 preserved / interleaved thinking 与**回传要求** |
| GLM-4.7 模型页 | `https://docs.bigmodel.cn/cn/guide/models/text/glm-4.7` | ✅ 窗口 200K / 最大输出 128K |
| Chat Completions **API 参考页** | `https://docs.bigmodel.cn/cn/api/paas/v4/chat-completions` | ⛔ **404**（页面路径已变，索引页只给 `/cn/api/introduction`）⇒ `stream_options` 的官方参数表**未取证** |
| 价格页 | `https://docs.bigmodel.cn/cn/guide/start/pricing` | ⛔ 未取到 GLM-4.7 单价数字（cache 页给的"50%"是唯一口径） |
| **真机对照实验**（主理人，2026-10-30） | GLM-4.7 直连 API，唯一前缀 + uuid salt，`thinking.type=disabled` | ✅ **最高优先级证据**：直接推翻文档的"建议 500"与"异步生效"两条，见 §1.2b。凡与本节实测冲突的文档说法一律以实测为准 |

> ⚠️ cache 页的示例正文里那段"知识库"是**模型输出示例文本**，其中的"usage 在最后一个 chunk 返回"
> 等句子属于**示例内容**而非规范条文 —— 引用它等于引用模型自己编的话。本笔记不采信那几句。
>
> 📌 **证据等级纪律**：本笔记 §1.2 的"文档事实"只用于描述**机制形态**（隐式/显式、字段名、计费口径），
> 凡涉及**数字阈值**（最小前缀、块大小、生效延迟）一律以 §1.2b 的实测为准。原因见 §1.2b：
> 文档给的 500 与实测的 4096 差一个数量级。

### 1.2 机制事实（逐条指到 cache 页原文）

| 问题 | 事实 | 原文依据 |
|---|---|---|
| 是否自动前缀缓存？ | **是**。"隐式缓存，智能识别重复的上下文内容，**无需手动配置**" | 功能特性 §自动缓存识别 |
| 是否有显式创建/复用 API？ | **没有**。页面通篇没有 endpoint / `cache_id` / `cache_control` / TTL 参数；只有"第一次请求建立缓存 / 第二次请求复用缓存"的两次普通 chat completions | 代码示例两段 |
| TTL | ⛔ **文档未给**。只说"缓存为异步生效，首次请求后稍等片刻再发起后续请求效果更好" | 最佳实践 §2 |
| 最小前缀 | 文档："重复的前缀内容必须足够长（**建议 500 Token 以上**）"；**实测门槛 4096**（见 §1.2b）。文档的建议值**严重偏低**，按它设计会得到"以为该命中、实际恒 0" | 代码示例开头提示 + §1.2b 实测 |
| 命中粒度 | 文档示例 `prompt_tokens=1075` / `cached_tokens=1024`（单点）；**实测粒度 64 token**（4096/6400/7296 全为 64 整数倍，见 §1.2b）⇒ 文档示例的 1024 只是"恰好被 64 整除且接近上限"的一个观测值，不能当块大小 | 响应示例 + §1.2b 实测 |
| 命中条件（字节级？） | 【推断】是 token 前缀逐一致（"识别出与之前请求中相同内容"），与 OpenAI 同族；文档未写"逐字节" | 功能特性首段 |
| `cached_tokens` 语义 | 本次请求的 prompt 中**被缓存复用的 token 数**（计入 `prompt_tokens`，不是额外附加） | 计费示例：总输入 2000 = 命中 1200 + 新内容 800 |
| 计费口径 | 新内容 = 标准价；**命中 = 标准价 50%**；输出 = 标准价。**仅适用标准 API**，资源包与 Coding Plan 套餐不适用 | 计费说明 |

### 1.2b 实测订正（2026-10-30，主理人在 GLM-4.7 直连 API 上的对照实验）

> 方法：**唯一前缀**（每次请求加 uuid salt，避免多次实验之间共享前缀互相污染），
> `thinking.type=disabled`，同一前缀**连续发两次**，读第 2 次的 `cached_tokens`。
> 这组数据把文档的两个说法都推翻了，**以实测为准**（本仓纪律：文档只作线索）。

| prompt_tokens（第 1 / 2 次） | 第 2 次 `cached_tokens` | 判定 |
|---|---|---|
| 157 / 185 / 202 / 434 / 1231 | 0 | 短前缀，不命中 |
| 1691 / 1702 | 0 | 不命中 |
| 1923 / 1934 | 0 | 不命中 |
| 2292 / 2303 | 0 | **不命中（2303 仍不够）** |
| **4111 / 4122** | **4096** | **命中，且恰为 4096** |
| 6426 / 6437 | 6400 | 命中 |
| 7352 / 7363 | 7296 | 命中 |

**三条可写死的结论**：

1. ~~最小可缓存前缀 ≈ 4096 token~~ **（2026-10-31 订正）**：新增 2502 → 2432 的样本后，门槛落在
   **(2303, 2502]**；且真机 928 → 896 证明「**命中不受门槛限制**」（可命中更长条目的前缀）。
   门槛约束的是**写入**，不是**读取**。文档的"建议 500"仍然偏低，但正确量级是 ~2.5k，不是 4096。
2. **粒度 = 64 token**。2432 / 4096 / 6400 / 7296 全是 64 的整数倍，且都约等于 `prompt − 70~120`
   （尾部未对齐部分不计）⇒ 向下按 64 对齐，**不是** 1024 块。
3. **同步生效**。4111 那组的第 2 次请求是**紧接着**发的，立刻命中 ⇒ 文档的"缓存为异步生效"
   在**秒级连续请求**这一场景下不构成障碍，**不需要**给 20 题加题间隔。

**关键推论（对止损跑分直接相关，已按实测订正）**：

- 本仓现役静态前缀 ≈700–800 token（§3.4 测算）**远低于 4096 门槛** ⇒ 跨题命中**必然为 0**，
  这不是"GLM 没缓存"，也不是"前缀在灰带"，而是**算术上的不可能**。见 §3.3 I2。
- 缓存是**账号 × 模型 × 前缀**维度（【推断】，文档未写作用域）⇒ 20 题若在同一端点/同一 system 下跑，
  从第 2 题起**才有**命中可能（但因上一条，现役前缀下仍然是 0）；第 1 题 `cached_tokens` 必为 0，
  **0 是正确值不是缺陷**。
- ⚠️ **这组数据同时说明"20 题跑分里 `cached=0` 不能用来证明任何事"**：它是现役前缀长度的必然结果。
  任何"缓存无效"的结论，都必须先在**前缀 ≥4096 的条件下**观测过（诊断实验；**不是**去生产里灌长提示词
  —— 那是负优化，算式见 §3.4 末）。

### 1.3 四家 prompt cache 对照

| 维度 | GLM（BigModel） | DeepSeek | OpenAI | Anthropic |
|---|---|---|---|---|
| 显式 / 隐式 | **隐式**（无 API） | 隐式（磁盘 KV cache，自动） | 隐式（自动前缀缓存） | **显式**（`cache_control: {type:"ephemeral"}` 断点，最多 4 个） |
| 命中条件 | 重复前缀够长（文档建议 ≥500 tok，**实测门槛 4096**）+ 内容相同 | 前缀完全相同（按 64 token 块对齐，公开口径） | 前缀 ≥1024 token 且逐一致 | 打到断点的前缀逐字节一致 |
| 命中粒度 | **64 token 块（实测）** | 64 token 块 | 1024 token 块 | 断点位置决定 |
| TTL | ⛔ 未公布。文档说"异步生效"但**实测秒级即生效**（§1.2b），故 TTL 上界仍未知 | 数小时～数天（官方描述为"不保证"） | 5–10 分钟（官方为"几分钟"） | 5 分钟（刷新则续期），1h 档另计 |
| 折扣 | 命中 **50%** 标准价 | 命中价约为标准价 1/10（`cache_hit` 档） | 命中 25%~50% 标准价（按模型档） | 写入 1.25×，读取 0.1× |
| 观测字段 | `usage.prompt_tokens_details.cached_tokens` | `usage.prompt_cache_hit_tokens` / `prompt_cache_miss_tokens` | `usage.prompt_tokens_details.cached_tokens` | `usage.cache_read_input_tokens` / `cache_creation_input_tokens` |
| 本仓现状 | 读 `prompt_tokens_details.cached_tokens` ✅ | **读不到**（字段名不同）⚠️ | ✅ | ⛔ 适配器未实现 |

> ⚠️ **DeepSeek 那一行是本仓的真实缺口**：`OpenAiCompatibleAdapter.kt:136` 只读
> `usage["prompt_tokens_details"]["cached_tokens"]`，DeepSeek 用 `prompt_cache_hit_tokens` 平铺在
> `usage` 顶层 ⇒ 换 DeepSeek 端点时 `cached` 恒 0，与"没命中"不可区分。这条不进 W2.5（属多供应商 W6），
> 但现在就要登记，否则 W6 会把它当"缓存不生效"再查一遍。

### 1.4 本仓可观测判据：一次 20 题跑分里怎么判"缓存真的命中了"

**唯一权威字段**：`AgentEvent.UsageUpdated(prompt, completion, cachedPrompt)`
→ `GenerationRound.kt:134-137`（HEAD）← `OpenAiCompatibleAdapter.kt:132-138`（读 `prompt_tokens_details.cached_tokens`）
→ 累积进 `RunState.cachedPromptTokens`（`RunState.kt:171-175`）→ `Finished.usage.cachedPromptTokens`。

判据表（每条都可机械判定）：

> ⚠️ **前置事实（实测，§1.2b）**：现役静态前缀 ≈700–800 token < 门槛 4096
> ⇒ **C2/C3 在本轮必然全 0**。所以本轮跑分里 `cached=0` **不构成任何证据**；
> 这五条判据的真正用途是"换前缀 / 换模型 / 修完 I2 之后"的验收，以及**防止有人把 0 读成"缓存无效"**。

| # | 判据 | 通过条件 | 空命中语义（0 命中时**不能**下的结论） |
|---|---|---|---|
| C1 | **字段确实到达** | 任一题的第 ≥2 轮出现 `prompt > 0`（说明 usage 帧被解析） | 若 `prompt` 也恒 0 ⇒ provider 没回 usage，走 `recordEstimatedUsage` 兜底（`GenerationRound.kt:174-179`），此时**缓存完全不可观测**，判 `⛔ vacuous`，不许写"未命中" |
| C2 | **同 run 内跨轮命中** | 同一 run 第 N 轮（N≥2）的 `cached > 0`，且 `cached ≤ 上一轮 prompt`。**前置条件：该轮 prompt 已 ≥4096** | `cached=0` 且 `prompt < 4096` ⇒ **算术必然**，不构成证据；只有 `prompt ≥ 4096` 时 `cached=0` 才说明真没命中 |
| C3 | **跨题命中** | 第 2 题起 `cached > 0`（同端点、同 system、同工具面），**且前缀 ≥4096** | 第 1 题 `cached=0` 是**必然**；现役前缀下第 2..N 题 `cached=0` 也是**必然**（§1.2b） |
| C4 | **命中率量级** | `Σcached / Σprompt ≥ 0.5` 才算"缓存真的在工作" | 命中率 < 0.1 ⇒ 先确认前缀是否 ≥4096，再查 §3.2 的阻断项，**不要**先怀疑供应商 |
| C5 | **归因排除** | 若 C2/C3 全 0，把 `systemInstruction` 灌到 **≥4096 token**（实测门槛，见 §1.2b；**不是 2000**）的固定文本再跑一次：`cached` 变正 ⇒ 原前缀低于门槛，问题在本仓前缀太短，不在 GLM | 若加长后仍 0 ⇒ 才可怀疑账号/端点/字段解析。⚠️ 本实验**本轮不执行**（决策 D-1，§5.3）：§1.2b 已给出更硬的结论 |

**会让命中率归零的外部侧行为**（与代码无关，但会污染跑分归因）：

1. 每题之间改 `systemInstruction` / 换模型 / 换端点 ⇒ 前缀不同；
2. ~~每题间隔太短（缓存异步生效）~~ ⇒ **已由实测推翻**（§1.2b：4111 那组紧接着发就命中）⇒ **不需要加题间隔**；
3. 题面被拼进 **system**（本仓不会，题面走 `userInput`）；
4. 关掉/打开某个工具（工具面变了）⇒ 前缀变；
5. **前缀本身不足 4096**（现役即是，见 §3.4）⇒ 这是本轮唯一真正生效的那一条；
6. `temperature`/`top_p` 变化 —— 【推断】不影响前缀缓存（不进 token 序列），但未取证。

---

## 2. 旧仓（LiquidAgent）关于 prompt cache / 上下文分块的既有判据

### 2.1 直接命中：只有一条，而且是"判死"

| 旧仓出处 | 原文 | 对本仓是否成立 |
|---|---|---|
| `Android-Agent/docs/11-harness-blueprint.md:150` | `#4 prompt cache 分层注入` → **判死（留痕）**："被 native 角色通道播种 + KV 复用取代（引擎侧能力演进），注入分层已无对应接口面" | ❌ **不成立**。判死理由是**端侧引擎演进**（litertlm 的原生角色通道 + KV 复用），API 路径下这个"接口面"恰好是**唯一存在**的：HTTP 请求体就是接口面。这条判死的**前提已被本仓的定位变更整体移除**（`AGENTS.md:5` "凡旧仓文档里与『模型跑在设备上』绑定的判据，在本仓一律不适用"） |
| `Android-Agent/docs/14-user-actions.md:178` | 同上裁决的引用（#3 history_v2 流式切块 / #4 prompt cache 分层注入已判死） | ❌ 同上 |

> **裁决**：旧仓"prompt cache 分层注入判死"是**端侧假设**，在 API 路径下**必须翻案**。
> 这不是我一个人的判断——本仓 `00-brief.md` A8 已经翻案并写成三条硬规则（见 2.2）。
> 但**翻案的对象要精确**：旧仓判死的是"分层注入"（把提示词按缓存断点分块），
> 而本仓 A8 要的是"前缀字节稳定"。前者在 GLM 上没有对应 API（隐式缓存），
> 所以 **W6 计划里的 `PromptCacheLayout`（稳定段前置 + 断点标记）在 GLM 上是空转**，
> 只在 Anthropic 上有意义。这条必须写进 W6 的范围修正。

### 2.2 间接命中：本仓已吸收的旧仓判据（逐条核到现役代码）

| 旧仓判据 | 旧仓位置 | 本仓落点 | 状态 |
|---|---|---|---|
| system 每 run 只渲染一次（循环外） | `AgentRunner.kt:638`（旧） | `TurnLoop.kt:37` + `:90-95`（`RunState.systemText` 缓存） | ✅ 已落地 |
| 技能正文以 **user 角色**注入，正文永不进 system | `00-brief.md` A6 | `AgentRequest.kt:56`（`skillsSection` 只放元数据）+ `AgentPromptComposer.kt:96-99` | ✅ 接口就位（W4 填内容） |
| `sanitizeForProvider` 补丁消息用**稳定派生 id** | `ContextCompressor.kt:159-166`（旧，动机是"引擎增量水印去重"） | `ContextCompressor.kt:198-204`（`"patch:" + missing.joinToString("_"){it.id}`），KDoc 已改写成"保 prompt cache" | ✅ 已落地，**动机已换** |
| 压缩只丢最老、system 逐字节不变 | 旧 `SummarizingContextCompressor` 判死（W35） | `ContextCompressor.kt:68-69`（SYSTEM 单独摘出）+ `:89`（`system + rest.subList(...)`）+ `ContextBudget.kt:61`（只把非 SYSTEM 写回） | ✅ 已落地，并有 SHA-256 单测 |
| 压缩切点必须落在工具组边界 | 旧仓 400 事故 | `ContextCompressor.kt:108-129`（`safeCutIndex` + `ownerIndex`），失败**无损放弃** | ✅ 已落地 |
| 摘要式压缩（L2）判死 → **翻案** | 旧仓 W35 | `02-roadmap.md:455-457`（W6 `ContextSummarizer` 翻案，理由：端侧贵/API 便宜） | 🕐 W6 |
| Microcompact（清旧 tool result 保 KV 前缀） | `11-harness-blueprint.md:147` **保留（parked）** | 本仓**无对应实现**；`ContextCompressor` 只做整条丢弃，不做"清空旧 tool result 但保留结构" | 🕐 未立项 |
| history_v2 流式切块 | `11-harness-blueprint.md:149` **判死** | 本仓无 history 层 | ❌ 端侧假设（"恢复接管"路径），不适用 |

### 2.3 端侧结论 vs API 路径结论（必须分清的一张表）

| 旧仓结论 | 端侧为什么成立 | API 路径是否仍成立 |
|---|---|---|
| "prompt cache 分层注入"判死 | litertlm 内部 KV 复用，应用层没有断点接口 | ❌ **不成立**：HTTP 请求体就是断点接口，且 GLM 是隐式缓存（连断点都不需要） |
| "改一个字只多一次 re-prefill（秒级）" | 端侧不计费 | ❌ **不成立**：改一个字 = 整段前缀按全价重算（钱） |
| "压缩是性能事件" | 端侧压缩省时间 | ❌ **不成立**：压缩是**计费事件**（`ContextBudget.kt:28-31` 已写明） |
| "Microcompact 保 KV 前缀" | KV 是显存里的对象 | ⚠️ **部分成立**：API 侧对应的收益是"保住前缀命中"，机制不同（要保的是**字节**，不是 KV 句柄） |
| "补丁 id 稳定是为了引擎水印去重" | 引擎按 id 判增量 | ❌ **动机不成立，结论仍成立**：新仓无引擎水印，但 id 抖动会改前缀字节 ⇒ 仍必须稳定（`ContextCompressor.kt:145-149` 已改写动机） |
| "技能正文进 system 会让缓存失效" | — | ✅ **成立且更强**：GLM/OpenAI 都是前缀缓存，system 在最前 ⇒ 改它等于全废 |
| "推理内容不进历史" | 端侧 thinking 通道是独立预算，回灌会污染 | ⚠️ **在 GLM 上反转**：GLM-4.7 明文要求交错思考回传 `reasoning_content`（§1.2、§3.5 B3） |

---

## 3. 现役树缓存友好性审计

> 方法：逐项 `grep`/`read` 取证；每项给 `文件:行`。**审计对象是 `HEAD 37465b4`**。

### 3.1 逐项核对

#### (a) `AgentPromptComposer`：system 段有没有时间戳 / 随机量 / 可变排序？

| 检查项 | 结论 | 证据 |
|---|---|---|
| 时间戳 | **无**。`sections()` 只拼 5 个常量段 + 3 个请求字段 | `AgentPromptComposer.kt:113-126` |
| 随机量 | **无**。全文件零 `Random` / `newId()` | 同文件；全仓 `core-agent/src/main` 的 `currentTimeMillis/nanoTime/Random` 命中只有日志时间戳、`RunState` 时钟、工具计时、`FileWriteTool` 临时名（均不进提示词） |
| 可变排序 | **无**。段顺序是硬编码 `if` 序列，注释明确"本仓唯一权威定义" | `AgentPromptComposer.kt:102-112` |
| 段间分隔符 | 固定 `"\n\n"` | `AgentPromptComposer.kt:129-130` |
| 技能元数据排序 | 现役 `skillsSection` 恒空串（W4 才填）；**W4 若按"最近使用"排序就会破坏缓存** | `AgentRequest.kt:50-56` |
| `memoryText` | 现役恒 null（W3 接线）；KDoc 已写明"必须在整个 run 内字节稳定" | `AgentRequest.kt:41-47` |

✅ **结论：现役 system 段字节稳定（run 内）。** 这条是本仓做得最干净的一处，不需要改。

#### (b) `ToolRegistry.specs()` 的顺序稳定吗？

| 检查项 | 结论 | 证据 |
|---|---|---|
| 底层容器 | `ConcurrentHashMap` ⇒ **迭代顺序不稳定** | `ToolRegistry.kt:26` |
| 出口是否排序 | **是**。`all()` 返回 `tools.values.sortedBy { it.spec.name }`，`specs()` 经 `enabledTools()` 走 `all()` | `ToolRegistry.kt:91`、`:93`、`:96` |
| 排序键是否稳定 | 稳定（工具名是常量） | `DefaultTools.kt:27-34` 六个固定名字 |
| 现役工具面 | 全局单例 `tools`（`by lazy`），所有 runner 共用 ⇒ 同一进程内工具集不变 | `AppContainer.kt:105-135`、`:143-155` |
| 潜在风险 | ① `setEnabled` 中途改（设置页）⇒ 列表少一项，**后面所有工具定义整体前移** ⇒ 从该位置起缓存失效；② W5 的 MCP 动态注册会在**排序中间插入** | `ToolRegistry.kt:80-82`；`02-roadmap.md:439-441` |

⚠️ **结论：顺序确定（不是"注册顺序"而是"名字序"），现役安全；但这是"脆弱稳定"** ——
任何"中途增删一个工具"都会造成**中段插入**，而中段插入正是 A8 硬规则 2 明令禁止的形态。
现役没有中段插入的路径，所以记 **降级（latent）**，W5 前必须补一条守卫。

#### (c) `ContextBudget.prepare` / `ContextCompressor`：切点、system 不变性、重排

| 检查项 | 结论 | 证据 |
|---|---|---|
| system 是否逐字节不变 | **是**。压缩器把 SYSTEM 摘出来原样拼回；`ContextBudget` 只把非 SYSTEM 写回 `RunState` | `ContextCompressor.kt:68-69`、`:89`；`ContextBudget.kt:61-67` |
| system 的 id 是否稳定 | **是**。`"sys:" + runId`，run 内恒定 | `ContextBudget.kt:82-83` |
| 压缩切点 | 从**最新往回**贪心求最长后缀，切点再回溯到工具组边界 | `ContextCompressor.kt:72-88`、`:108-119` |
| 压缩是否重排历史 | **否**。`system + rest.subList(safe, size)`，保持原序，只丢前缀 | `ContextCompressor.kt:89` |
| 压缩失败语义 | **无损放弃**（返回原列表，不改写历史） | `ContextCompressor.kt:85`、`:88`；`ContextBudget.kt:62-66` |
| 压缩是"缓存失效事件"吗 | **是，且只失效切点之后**。`system + 工具面` 这一段仍是共同前缀 ⇒ 仍可命中 | 由 `:89` 的形态直接推出 |
| 多次压缩的累积效应 | 每次压缩都把保留区起点往后移 ⇒ **每次压缩都是一次前缀位移**。门槛 0.75 让它稀有，但没有"压缩次数上限" | `ContextCompressor.kt:37-41` |

✅ **结论：压缩的缓存语义是"设计内正确"**（system+tools 段保住，切点后重算）。
⚠️ 唯一要补的是**次数可观测**：现在只在 `AgentLogStore.info` 记一条（`:90-93`），200 条环形缓冲
很快被冲掉，跨 run 不可追溯。

#### (d) `ToolResultCommit`：截断稳定性、`sanitizeForProvider` 合成 id

| 检查项 | 结论 | 证据 |
|---|---|---|
| 同一结果两次截断是否同字节 | **是**。`substring(0, limit)` + 高位代理退一格，纯函数、无状态 | `ToolResultCommit.kt:51-56`、`:84-87` |
| 不超限时是否改动 | **否**，返回原对象（便于判"没变化"） | `ToolResultCommit.kt:51` |
| 截断发生在"落库前"还是"发送前" | **落库前**（`ToolCallExecutor.run` 里 `commit.truncate(...)` 后才 `commit.commit(...)`）⇒ 历史里的字节一次定型，**后续轮次不会重新截断** | HEAD `ToolCallExecutor.kt:131` |
| 补丁 id 是否稳定派生 | **是**。`"patch:" + missing.joinToString("_"){it.id}` | `ContextCompressor.kt:198-204` |
| 补丁消息插在哪 | **紧跟在宿主 MODEL 之后**（中段插入） | `ContextCompressor.kt:184-207` |
| 相邻同角色合并 | 确定性（顺序扫描 + 用第一条 id） | `ContextCompressor.kt:226-248` |

⚠️ **`sanitizeForProvider` 的中段插入是唯一"合法但伤缓存"的路径**：它只在
「MODEL 声明了 toolCall 但没有结果」时触发（审批跳过 / 熔断中断 / 压缩丢宿主）。
正常路径不发生 ⇒ 记 **降级**。但它**静默**：`AgentLogStore.warn` 一条（`:205`），
在 200 条缓冲里基本看不到。

#### (e) `RunState`：working 是 append-only 吗？

| 检查项 | 结论 | 证据 |
|---|---|---|
| 追加 | `appendMessage` 只 `add` 到尾部 | `RunState.kt:121-123` |
| 改写 | `replaceWorking` 是**唯一**改写入口，只有压缩调用 | `RunState.kt:126-129`；`ContextBudget.kt:67` |
| 是否有中段插入 | **无**（`appendMessage` 没有 index 参数） | `RunState.kt:121-123` |
| 初始构造 | `history + userInput`（userInput 追加尾部） | `RunState.kt:210-220` |
| 消息 id | 初始 USER 消息用默认 `newId()`（随机）—— **但不进线上报文**，见 (g) | `RunState.kt:217`；`ChatMessage.kt:21` |

✅ **结论：working 是 append-only（压缩除外），符合 A8 硬规则 2。**

#### (f) `ChatViewModel`：每次发送把 UI 气泡重建成 history ⇒ 前缀一致性？

**这是本次审计最重的一条。** 机制如下：

```
发送时：history = snapshot.bubbles.mapNotNull { it.toHistoryMessage() }
        toHistoryMessage: text.isBlank() -> null（丢弃）
                          role == TOOL     -> null（丢弃）
                          其余            -> ChatMessage(role, text)
                                                          ← ChatViewModel.kt:184, :355-359（HEAD）

内核侧：working = history + USER(userInput)              ← RunState.kt:210-220
        每轮追加 MODEL(toolCalls) + TOOL(results)         ← ToolResultCommit.kt:65-74
        最终答复**不**落 working（只作为 Finished(text) 上屏）← RunTermination.kt:27-51
```

于是同一个会话的两份真值源：

| | 上一 run 实际发出的 token 序列 | 下一 run 重建出的 token 序列 |
|---|---|---|
| 第 1 条 USER | `USER("A")` | `USER("A")` ✅ 相同 |
| 第 2 段 | `MODEL(toolCalls, text="")` + `TOOL(result)` | `MODEL("answer text")` ❌ **分叉** |
| 后续 | 第 2..N 轮全部以 `[sys, USER A, MODEL(toolCalls), TOOL, …]` 为前缀 | 以 `[sys, USER A, MODEL(answer), USER B]` 为前缀 |

**后果（精确表述 —— 这段会被写进 roadmap，措辞须经得起 grep）**：

- **run 内**：第 N 轮的请求是第 N−1 轮请求的**严格前缀扩展** ⇒ 缓存照常命中。✅
- **跨 run 且上一 run 有工具调用**：**从那一轮起必然分叉**。该轮实际发出的是
  `MODEL(toolCalls, text="")` + `TOOL(result)`，而重建出的 history 里是 `MODEL(answer text)`
  ⇒ 两者从该轮第 1 个 token 就不同，且**分叉会传播到它之后的一切**。
- **跨 run 且上一 run 是纯文本轮（无工具调用）**：`working = [USER]`，下一 run 的 history 是
  `[USER, MODEL(answer)]`，而上一 run 发出的就是 `[sys, USER]` ⇒ **严格前缀，仍可命中**。✅

⇒ 一句话口径：**有工具调用的轮次 = 分叉点；纯文本轮不破坏前缀。**
共同前缀的上界是「`system + 工具面 + 到第一个含工具调用的轮次为止`」。

严重度：**阻断「跨轮缓存」，但只在会话里出现过工具调用时成立**（对单 run 内多轮无害）。
这同时解释了一个反直觉现象：**纯闲聊多轮比"带工具的会话"缓存友好**；
而"20 题各开新会话"与"同一会话连问 20 题"的差别，取决于题面是否触发工具。

#### (g) `OpenAiCompatibleAdapter.buildBody`：请求体稳定性

| 检查项 | 结论 | 证据 |
|---|---|---|
| 字段顺序 | 固定（`buildJsonObject` 按插入序，`JsonObject` 保序） | `OpenAiCompatibleAdapter.kt:70-88` |
| 字段顺序是否影响缓存 | **不影响**。前缀缓存按 **token 序列**匹配，不按 JSON 字节 | 【推断】四家同族；文档未明说 |
| `stream_options` 是否影响缓存 | **不影响**。它只决定"要不要在末帧回 usage" | `OpenAiCompatibleAdapter.kt:74-76` |
| 是否把消息 `id` 发出去 | **不发**。`messageObject` 只发 role/content/tool_calls/tool_call_id | `OpenAiCompatibleAdapter.kt:193-222` |
| 空消息过滤 | 发请求前再过滤一次 `isEmpty`（与 `ContextBudget` 重复，结果一致） | `OpenAiCompatibleAdapter.kt:68`；`ContextBudget.kt:52` |
| 单条 TOOL 多结果展开 | 按 `toolResults` 逐条展开成 N 条 tool 消息（顺序=列表序，稳定） | `OpenAiCompatibleAdapter.kt:180-191` |
| **是否回传 `reasoning_content`** | **否**。assistant 消息只有 `content` / `tool_calls` | `OpenAiCompatibleAdapter.kt:193-222` |
| `max_tokens` | `sampling.maxTokens ?: endpoint.maxOutputTokens`，run 内恒定 | `OpenAiCompatibleAdapter.kt:79` |

✅ 请求体构造本身**不制造抖动**。随机消息 id 不进报文 ⇒ **不是缓存问题**（避免误报）。

#### (h) 顺带审计出的两个非缓存缺陷（与本节同源，必须一起记）

1. **`TokenEstimator` 不计工具声明**。`ContextBudget.prepare` 传给估算的是
   `sanitizeForProvider(listOf(system) + body)`，**不含 `tools`**
   （`ContextBudget.kt:53-58`；`TokenEstimator.kt:47-61` 只遍历 messages）。
   现役 6 工具 ≈ 400–500 token（占 32000 预算 1.4%，可忽略），**W5 接 MCP 后会到数千**。
   → 这是 `ContextBudget` 类头点名的那个"系统性低估"缺陷的**残留半段**（system 修了，tools 没修）。

2. **内核循环跑在主线程（现状；T-1 只收窄了最危险的一段）**。
   `AgentRunner.run` 的 `channelFlow { launch(start=LAZY){ executeLoop(ctx) } }` 继承收集方上下文
   （`viewModelScope` = `Main.immediate`）⇒ `TurnLoop` / `ContextBudget.prepare` /
   `TokenEstimator`（全量字符扫描）/ `sanitizeForProvider`（多次全量扫描）/ `WindowContextCompressor`
   / `ToolResultCommit.commit`（`truncate` + 落工作副本）**全部在主线程**
   （`AgentRunner.kt:120`）。

   **T-1 的实际落点（2026-10-30，工作树，已由实施者复核）**：只把**最窄的一段**切到 IO ——
   `ToolCallExecutor.invoke` 里 `withTimeoutOrNull(timeout) { withContext(ioDispatcher) { tool.invoke(args) } }`
   （工作树 `ToolCallExecutor.kt:174` 是 `core-agent/src/main` 唯一的实质 `withContext` 调用）。
   ⇒ **`ctx.emit` / `commit` / `ToolApprovalFlow` 仍留在收集方上下文（Main）**
   ⇒ **事件发射线程不变，UI 侧 `_state.update` 与 Compose 快照无需适配**（这条判据我曾写反，此处更正）。

   **仍然成立的两条（不要因为 T-1 落地就以为已收口）**：
   - `ToolResultCommit.commit`（`truncate` + 落工作副本）仍在 Main；
   - `ContextBudget.prepare`（`TokenEstimator` + `sanitizeForProvider`）仍在 Main。
   这两处要收口得单独立项（commit 切 IO 会动**事件顺序语义**：`MessageCommitted` 与
   `ToolResultReceived` 的先后是 UI 侧依赖的）。

   **对本次调研的约束（不因 T-1 放松）**：任何新增的"前缀指纹 / hash / 日志"都**不许**
   往 `prepare` 这条主线程路径上加 O(n) 扫描。

### 3.2 「会让缓存失效的行为」清单

> 严重度定义：**阻断** = 该段前缀在本设计下**永远不可能**命中；
> **降级** = 可命中但覆盖变窄 / 需额外条件 / 静默退化；**无害** = 不影响命中。

| # | 行为 | 位置 | 影响段 | 严重度 | 备注 |
|---|---|---|---|---|---|
| I1 | 跨轮从 UI 气泡重建 history，工具轨迹与 MODEL 正文缺失 | `ChatViewModel.kt:184`、`:355-359`（HEAD） | **从第一个含工具调用的轮次起**，其后全部 | 🔴 **阻断**（仅当会话出现过工具调用） | 见 §3.1(f)：纯文本轮仍是严格前缀、可命中；有工具调用的轮 = 分叉点 |
| I2 | 静态前缀（system+6 工具）≈700–800 tok，**低于实测 4096 门槛** | `AgentPromptComposer.kt:42-66` + `DefaultTools.kt` | 整条前缀 | 🔴 **阻断（已实测，非推断）** | 见 §1.2b / §3.4：2303 不命中、4111 命中 ⇒ 现役前缀**必然 0 命中** |
| I3 | GLM-4.7 要求回传 `reasoning_content`，本仓丢弃 | `OpenAiCompatibleAdapter.kt:193-222`；`RunState.kt:141-143` | assistant 段 + GLM 保留式思考 | 🔴 **阻断**（GLM 专属） | 见 §3.5 B3；同时是质量缺陷 |
| I4 | MODEL(toolCalls) 消息 `text` 留空（本轮正文不进历史） | `ToolResultCommit.kt:67` | 本轮之后 | 🟡 降级 | 对"上一轮请求是严格前缀"这一点**无害**；害处是模型看不到自己说过什么 |
| I5 | 压缩丢最老消息（切点位移） | `ContextCompressor.kt:89` | 切点之后 | 🟡 降级（设计内） | system+tools 段仍命中；门槛 0.75 使其稀有 |
| I6 | `sanitizeForProvider` 补丁消息中段插入 | `ContextCompressor.kt:184-207` | 补丁位置之后 | 🟡 降级 | 只在异常路径；静默（warn 易被环形缓冲冲掉） |
| I7 | 中途 `setEnabled` / MCP 动态注册 ⇒ 工具面中段增删 | `ToolRegistry.kt:80-82`、`:91` | 工具面该位置之后 | 🟡 降级（latent，W5 触发） | 现役无此路径 |
| I8 | 跨 run 改 `systemInstruction` / `memoryText` / `skillsSection` | `AgentRequest.kt:39-56` | system 段 | 🟡 降级（W3/W4 触发） | 现役后两者恒空 |
| I9 | 换端点 / 换模型 / 换 `contextLength` | `AppContainer.kt:143-155`（runner 按 endpointId 缓存） | 全部 | 🟡 降级 | 缓存作用域按账号×模型【推断】 |
| I10 | 相邻同角色合并（`"\n\n"` 拼接，沿用第一条 id） | `ContextCompressor.kt:226-240` | 合并处 | ⚪ 无害 | 确定性；id 不进报文 |
| I11 | 消息随机 id（`newId()`） | `ChatMessage.kt:21`；`RunState.kt:217` | — | ⚪ 无害 | **不发到线上**，避免误报 |
| I12 | `buildBody` 字段顺序 / `stream_options` | `OpenAiCompatibleAdapter.kt:70-88` | — | ⚪ 无害 | 前缀按 token 匹配 |
| I13 | 截断（READ 16000 / WRITE 2000） | `ToolResultCommit.kt:44-56` | — | ⚪ 无害 | 纯函数 + 落库前定型 |
| I14 | 重试（同 run 同轮重发同一 body） | `GenerationRound.kt:39`；`RetryPolicy.kt:31` | — | ⚪ 无害（钱上近零） | 同一 body ⇒ 前缀命中；**但**每次尝试仍发一次完整请求，延迟不省 |
| I15 | 系统提示词里的时间戳/随机量 | — | — | ⚪ **不存在** | 已逐项核对，见 §3.1(a) |

### 3.3 三条阻断项的因果链（为什么它们真的是阻断）

**I1（跨轮分裂）**：缓存是"前缀匹配"，不是"内容去重"。设上一 run 第 2 轮含工具调用 ——
它实际发出的是 `MODEL(toolCalls)+TOOL`，而下一 run 从气泡重建出的是 `MODEL(answer)`
⇒ 两者从该轮第 1 个 token 就不同，**没有任何办法**让这一段及其之后命中。
**边界（必须一起写）**：若上一 run 是**纯文本轮**（无工具调用），则下一 run 的 history
`[USER, MODEL(answer)]` 是上一 run 已发序列 `[sys, USER]` 的**严格扩展** ⇒ 仍可命中。
⇒ 判据口径：**分叉点 = 会话中第一个含工具调用的轮次**，不是"第一条 USER 之后"。

**I2（前缀低于实测门槛）**：实测最小可缓存前缀 **4096 token**、粒度 64（§1.2b：
2303 不命中、4111 命中且恰为 4096）。本仓静态前缀测算 ≈700–800 token（§3.4）
⇒ **确定低于门槛，跨题命中必然 0**。
这条**不是"灰带"**（我最初的判断被实测推翻）：灰带意味着"可能命中也可能不命中"，
而 700–800 vs 4096 是算术上的不可能。它的危害恰恰在于**看起来像"缓存不工作"** ——
20 题跑分里 `cached=0` 会被误读成"GLM 没缓存"，进而把"每题全价"的成本归因给供应商。

**I3（reasoning 不回传）**：GLM 文档原文两条：
- "当您在使用『交错思考 + 工具』时，**必须显式保留 Reasoning content，并在返回工具结果时一并返回**"；
- 保留式思考"提高缓存命中率……**需要将完整、未修改的 reasoning content 传回 API**……
  **不要重新排序或修改**……否则会降低效果并**影响缓存命中**"。
本仓 `RunState.appendReasoning` 只累加字符数（`RunState.kt:141-143`），
`ChatMessage` 没有 reasoning 字段（`ChatMessage.kt:20-31`），adapter 也不发
⇒ 在 GLM-4.7 上，**工具轮的思考块永久丢失**。

### 3.4 静态前缀长度测算（I2 的证据）

| 组成 | 字符数 | ≈token | 依据 |
|---|---|---|---|
| `TOOL_GUARDRAILS` | 258 | ~65 | `AgentPromptComposer.kt:62-66` |
| `STOP_CONDITIONS` | 943 | ~236 | `AgentPromptComposer.kt:42-50` |
| 段间 `"\n\n"` | 2 | ~1 | `:130` |
| **system 小计** | **1203** | **~300** | |
| 6 个工具 schema（name+description+params+JSON 壳） | ~1500–2000 | ~380–500 | `DefaultTools.kt:27-34` 六个 `ToolSpec` |
| **合计** | | **≈700–800** | |

（`MEMORY_MAINTENANCE` 668 字符**不计入**：它门控在 `memory_write` 在不在工具面里
（`AgentPromptComposer.kt:122`），而 `memory_write` 不在内置 6 个工具里 ⇒ 该段现役不出现。）

**结论（按实测改写，不再是推断）**：静态前缀 ≈700–800 token vs 实测门槛 **4096**（§1.2b）
⇒ **确定低于门槛，跨题缓存必然 0 命中**。差额约 3300 token，靠"微调提示词"补不上 ——
这不是调参能解决的量级差。

**证伪法（C5 的落地形态，已按实测门槛修正）**：
1. 用同一端点跑第 1 题，记 `prompt` / `cached`；
2. 在设置页把 `systemInstruction` 灌到 **≥4096 token**（同一份固定文本，不要改），再跑第 2 题；
   ⚠️ 阈值是 4096 不是 2000 —— 实测 2303 仍不命中，灌到 2000 会得到假阴性；
3. `cached` 从 0 变成正数 ⇒ **I2 确认**（原前缀低于门槛）；仍是 0 ⇒ 去查字段解析 / 账号 / 端点。
   这一步不改一行代码，是纯配置实验 ⇒ 成本最低、结论最硬。

> 📌 **但本实验不另做**（主理人决策，见 §5.3 决策 1）：§1.2b 的对照实验已经用唯一前缀
> 把门槛直接测出来了，比"配置灌长再猜"更硬。本节保留实验法只作为**将来换模型/换供应商**时的复用手册。

#### 🔴 由 4096 门槛推出的一个反直觉结论：**不要为了凑缓存去"加长前缀"**

算一下就知道这是负优化。设前缀 P token、命中价 0.5：

| 方案 | 每次请求的前缀成本（token 当量） |
|---|---|
| 现役 P=800，不命中 | 800 × 1 = **800** |
| 人为灌到 P=4096，命中 | 4096 × 0.5 = **2048** |

⇒ **为了命中而灌长提示词，成本从 800 涨到 2048，反而贵 2.5 倍**。
缓存是"**本来就要发那么长的前缀**"时的折扣，不是"发得越长越省"。

正确的结论只有一条：**现役 700–800 token 的前缀就别指望缓存**，
它的收益要等到**前缀本身自然变长**才会出现 —— 即 W4 技能元数据 + W3 长期记忆
+ W5 MCP 工具面（`disclosureThreshold = 24` 之后工具 schema 会到数千 token）。
在那之前，**唯一正确的动作是"别为缓存改提示词"**。

### 3.5 与 GLM-4.7 的对接缺口（本次调研的额外收获）

| # | 缺口 | 现役代码 | GLM 要求 | 后果 |
|---|---|---|---|---|
| B1 | 思考参数形态 | `ReasoningStyle` 六档里**没有 GLM 形态**（`thinking: {type, clear_thinking}`） | GLM-4.7 默认**开启** thinking；关闭要 `thinking.type="disabled"` | 现役若配 `ReasoningStyle.NONE` ⇒ 什么都不发 ⇒ GLM-4.7 **默认开思考** ⇒ 会吐 `reasoning_content`。不致命，但"关不掉思考" |
| B2 | `reasoning_content` 回传 | `OpenAiCompatibleAdapter.kt:193-222` 不发 | 交错思考 + 工具**必须**回传 | 质量 + 缓存双降 |
| B3 | `clear_thinking: false`（保留式思考） | 无对应字段 | 标准 API 端点**默认关闭**，要显式开 | 现役拿不到"提升缓存命中率"那部分收益 |
| B4 | 思考 token 计费 | `RunState.completionTokens` 只累加 `usage.completion_tokens` | GLM 把思考计入输出 token（cache 页知识库段落提到，属**示例文本**，⛔ 不算一手） | ⛔ 未取证；若成立则"输出贵"的归因要改写 |
| B5 | 窗口声明 | `RemoteEndpoint.contextLength` 默认 32_768、`maxOutputTokens` 4_096 | GLM-4.7 = 200K / 128K | 用户若照抄默认值 ⇒ 只用 32K 窗口（`maxContextTokens` 也是 32K，HEAD `AgentPolicy.kt:77`）⇒ **不省钱但也没坏事**；若把 `maxOutputTokens` 填成 128K ⇒ `contextBudgetTokens` 的 `window - maxOutputTokens` 变负 ⇒ 走 `window/2` 兜底（`ContextCompressor.kt:39`），行为正确但会意外激进压缩 |

> **对正在跑的 20 题止损的即时影响**：B2 是**混淆变量**。若止损判据 2（8 轮不收敛）挂了，
> 在修 B2 之前**不能**归因给"模型能力"。理由：模型看不到自己上一轮的思考与正文，
> 是"重复调同一工具 / 不收敛"的高概率机制性原因。

---

## 4. W2.5 可落地的最小改动（3 项，低风险、可单测）

> 三项都**不改** `core-*/src/main` 的机制，只补"观测面"与"一处估算漏记"。
> 明确**不在 W2.5 做**的事见 §4.4。

### 4.1 M1 — 把"缓存命中"变成可判定的证据（观测面）

**改哪**：
1. `feature-chat/.../EventReducer.kt:228-232`（HEAD）`buildUsageLabel`：
   - `cached` **恒显示**（含 `0 cached`），并追加命中率 `NN.N%`；
   - 现役 `if (cached > 0)` 让"0 命中"与"字段缺失"**不可区分** —— 这是知识库 §4
     「WARN 判据有闩 ⇒ 0 命中 ≠ 通过」的同一形态。
2. `core-agent/.../GenerationRound.kt:134-137`（HEAD）`GenerationEvent.Usage` 分支：
   加一条 `AgentLogStore.info("usage: prompt=… completion=… cached=… hit=…%")`。
   **理由**：UI 只显示最后一轮且被覆盖；`AgentLogStore` 是进程级环形缓冲 + `AgentLogFileStore`
   可落盘（`AgentLogStore.kt:34-45`、`:53-65`），是跨 run 唯一可 grep 的证据面。

**验收判据（机械可判）**：
- `EventReducerTest` 新增两例：
  - `UsageUpdated(prompt=1000, completion=10, cachedPrompt=0)` ⇒ `usageLabel` 含 `"0 cached"` 且含 `"0.0%"`；
  - `UsageUpdated(prompt=1000, completion=10, cachedPrompt=600)` ⇒ 含 `"600 cached"` 且含 `"60.0%"`。
- `GenerationRound` 侧：`AgentLogStore.clear()` → 跑一轮 FakeEngine（发一个 `Usage` 事件）→
  `AgentLogStore.recent().any { it.message.contains("cached=") }` 为 true。
  （`AgentLogStore` 有 `clear()` / `recent()`，**可测**，不需要注入端口 —— `AgentLogStore.kt:98-109`。）

**风险**：极低。纯展示 + 一条日志。**唯一注意**：日志走 `SecretRedactor.redact` + 400 字符截断，
不要把整段 messages 打进去（会挤爆 200 条缓冲）。

**为什么它是第一件**：实测（§1.2b）已判定现役前缀**必然 0 命中**，所以 M1 在本轮的价值
不是"发现命中"，而是三件更基础的事：① 让 `cached=0` **可见**（现在 `cached=0` 时 UI 什么都不显示，
与"provider 没回 usage"不可分辨）；② 证明 **usage 帧确实到达**（C1 —— 这是判定"字段有没有解析"的唯一入口）；
③ 一旦有人把前缀加长 / W6 换供应商，**同一个界面就能直接验收**，不必再改一次代码。

### 4.2 M2 — `TokenEstimator` 补上工具声明（估算漏记，外审 T 系列同一族）

**改哪**：
1. `core-model/.../TokenEstimator.kt` 新增 `fun estimateToolSpecs(specs: List<ToolSpec>): Int`
   —— 逐项估 `name + description + 每个参数(name/type/description/enum)`，每条工具 +4 token 协议开销
   （与 `estimateMessages` 的 `PER_MESSAGE_OVERHEAD` 同口径，`TokenEstimator.kt:24-25`）。
2. `core-agent/.../ContextBudget.kt:57` 改为
   `val estimated = TokenEstimator.estimateMessages(full) + TokenEstimator.estimateToolSpecs(ctx.tools.specs())`，
   并把 `toolTokens` 写进 `:68-71` 的 info 日志与 `:64` 的 warn 日志。

**验收判据**：
- 单测 1：6 个内置工具 `estimateToolSpecs` > 0 且 < 2000（上界防"算成天文数字"）；
- 单测 2：构造 `budget` 落在「messages 估算 < budget < messages+tools 估算」之间 ⇒
  `prepare` **触发压缩**（现役不触发）。这一条是"漏记确实被修掉"的机械判据；
- 单测 3（回归）：`toolTokens = 0` 时 `estimated` 与改动前**逐值相同**（保证不动既有 `ContextBudgetTest` 的语义）。

**风险**：低–中。**副作用是压缩会提前触发**（估算变大 ⇒ 更早过 0.75 门槛 ⇒ 更多"缓存失效事件"）。
因此 KDoc 必须写清"这是把低估修正为准确，不是调门槛"，且**不改** `compressThreshold`。
现役 6 工具 ≈1.4% 偏差 ⇒ 实际行为变化可忽略；W5 接 MCP 后才是它真正生效的场合。

### 4.3 M3 — 一条"前缀指纹"诊断日志（为 I1 提供可机械证明的取证工具）

**改哪**：`ContextBudget.prepare` 返回前记一条 info：
`"prefix: sys=<sha256(systemText).take(12)> tools=<sha256(join(spec.name+desc)).take(12)> msgs=<n> est=<estimated>"`。

**理由**：`02-roadmap.md:467` 已经预写了 W6 判据 2 的兜底方案
（"`cachedPromptTokens` 三家都拿不到 ⇒ 降级为『日志记录请求前缀的 SHA-256，人工比对两次是否一致』"）。
现在做，成本一条日志，收益是 **I1（跨轮分叉）可以被机械证明而不是靠推断**：
同一会话连问两次，两次的 `sys=` 相同而 `msgs=` 与后续内容不同 ⇒ 分叉点定位。
（I2 已由 §1.2b 实测解决，不再需要 M3 去证明；M3 现在的目标只剩 I1 与"前缀被谁改了"的取证。）

**验收判据**：
- 单测：同一 `AgentRequest` + 同一工具面，两次 `prepare` 的 `sys=` 前缀相同；
- 单测：`tools` 少一个 ⇒ `tools=` 变；
- 单测：`systemText` 差一个字符 ⇒ `sys=` 变。
（把 hash 计算抽成 `internal fun prefixFingerprint(...)` 才能纯 JVM 单测 —— 不要在 `prepare` 里内联。）

**风险**：低，但**有一条硬约束**：`prepare` 跑在**主线程**（§3.1(h)-2；**T-1 只切了 `tool.invoke`，
没有搬 `prepare`**）⇒ hash 只能对 **systemText（~1.2KB）+ 工具名/描述（~1.5KB）** 算，**绝不许**对整个 `messages` 算
（那是每轮 O(历史长度)，长会话会掉帧）。要算 `messages` 指纹，得等 `prepare` / `commit`
也搬离主线程（W3 内核硬化）之后再说 —— **T-1 已经落地了但它只切了 `tool.invoke`，`prepare` 没动**。

### 4.4 明确**不要**在 W2.5 做的事

| 不做 | 为什么 |
|---|---|
| **重构 history 真值源**（让 `working` 成为唯一历史，UI 气泡降级为投影） | 这是 I1 的正解，但它同时改 `ChatViewModel` / `AgentRequest.history` 语义 / `RunState` 生命周期 / 会话持久化，**影响面覆盖 W3 的 journal 与恢复**。W2.5 是"止损判定前的观测波"，改真值源会让 20 题跑分的口径与 W2 基线不可比 |
| 给 `ChatMessage` 加 `reasoning` 字段并回传（修 I3/B2） | 看着像一行字段，实际要动：契约（`@Serializable` 加字段 = 落盘格式变）、`RunState`（要从"只计数"改成"按轮存文本"，与 `MODEL 正文留空` 是同一笔账）、adapter（三族形态不同）、压缩（reasoning 算不算 token）、脱敏（思考里可能带密钥）。**属 W3 的账本活，与 `MODEL 正文留空` 同批**。已由决策 **D-2**（§5.3）定为本轮不修 |
| **为凑缓存而加长 system 提示词** | ❌ **明令禁止（反优化）** | 实测门槛 4096（§1.2b），但"灌到 4096 命中"的成本 = 2048 token 当量 > 现役 800 token 当量 ⇒ **贵 2.5 倍**（算式见 §3.4 末）。缓存是"本来就要发那么长"时的折扣，不是"越长越省"。前缀的自然增长来自 W4 技能元数据 / W3 记忆 / W5 MCP 工具面 |
| 加 `PromptCacheLayout` / 缓存断点 | GLM 无断点 API（§1.2），OpenAI 隐式 ⇒ **在 GLM/OpenAI 上是空转实现**。只有 Anthropic 需要，属 W6 |
| 调 `compressThreshold` / `maxContextTokens` | 门槛是"压缩稀有度"的唯一旋钮，改它会让 20 题跑分与 W2 基线不可比。且没有实测数据支撑 |
| 把内核循环**整体**搬离主线程 | 正确但**不是 W2.5**：T-1 已落地的是最窄的一段（`tool.invoke`，事件线程不变、UI 无需适配），剩下的 `ToolResultCommit.commit` + `ContextBudget.prepare` 要收口就得动**事件顺序语义**（`MessageCommitted` 与 `ToolResultReceived` 的先后是 UI 侧依赖的），属 W3 内核硬化。W2.5 只**登记**，并用 M3 的"不许加 O(n)"约束绕开 |
| 加 DeepSeek 的 `prompt_cache_hit_tokens` 解析 | 属 W6 多供应商；现在加等于给一个跑不到的分支写代码（`00-brief.md` A5 的"空转实现"教训） |

---

## 5. W3 范围建议（基于两份外审）

### 5.1 外审 §四/§五 的 W3 建议：进 / 不进

| # | 建议 | 判断 | 理由（指到本仓机制） |
|---|---|---|---|
| W3-1 | **journal 恢复**（`AgentRunJournal` + 杀进程重启弹恢复卡） | ✅ **进，且是 W3 第一件** | 它是唯一"用户可见的正确性"缺口：现在杀进程 = 本次 run 的所有已付 token 与工具副作用全部无痕（`RunTermination` 只发事件、不落盘，`RunTermination.kt:27-51`）。且它是 I1 真值源重构的**前置**——没有 journal，"上一 run 到底发了什么"无从取证 |
| W3-2 | **`FreezeClock`**（灭屏冻结剔除） | 🕐 **进，但降级为"只服务 SOFT"** | `RunState.elapsedMillis` 用单调时钟（`RunState.kt:206`），而 `RoundHeadGate` 的 HARD=600s 是熔断点（`RoundHeadGate.kt:36-40`）。灭屏 60s 就熔断一个正常长任务，是**真缺陷**；但 `02-roadmap.md:393` 已自陈"API 路径未验证" ⇒ 按 W3 原判据 4 单独立项，不通过就只降级 SOFT |
| W3-3 | **`maxRunPromptTokens` 首次启用门控** | ✅ **进** | 已接线（`RoundHeadGate.kt:42-46`），但**有洞**：门只在**轮头**判，一轮内的 6 次 HTTP 请求（见 §5.2）不受约束；中途中断的请求只靠估算补记（且 `TokenEstimator` 自陈低估 2~3 倍）。**修门控时机比修门槛数字更急** |
| W3-4 | **`PostStreamSignals` / `NoToolCallHandler`** | ✅ 进 | 与 `RunTermination` 同族（"零调用收尾"已在 `TurnLoop.kt:63-66` 有出口），补的是"模型说了要做但没调工具"这类信号。低风险 |
| W3-5 | **`StreamRepetitionDetector`（砍 prompt-echo 半边）** | ⚠️ **推后到 W3 尾** | 它依赖"提示词明确禁止复述"作为判定前提（`AgentPromptComposer.kt:34-41` 的 KDoc 自己写了）。止损判据未过时，重复输出的根因更可能是 I3/I4（模型看不到自己上一轮）⇒ 先修信息回灌，再判"是不是复读"。否则会误伤 |
| W3-6 | **`DiagnosticsScreen`** | ✅ **进，且提前到 W3 第二件** | M1/M3 的日志要有地方看。现在 `AgentLogFileStore` 落盘但**无 UI 入口**（`AppContainer.kt:64`），等于证据只有 adb 能取 |
| W3-7 | **`RunTokenLedger` 校准 warn** | ✅ 进 | 与 W3-3 同批；它是"估算 vs 真实"的唯一校准面，而 M2 刚把估算修正过一次，正需要校准 |
| W3-8 | **审批缓存会话级装配**（外审 T-4） | ⚠️ **推后（但必须销账）** | `TurnContext.approvalCache` 字段已存在（`TurnContext.kt:64`），工作树已在接线（`TurnLoop.kt:42`、`ChatViewModel.kt:97/289`）。**等 T-4 落地后只需验收，不需要 W3 再做**。W3 只负责"跨会话不串授权"的回归判据 |
| W3-9 | **clipboard 读侧开关上设置页**（外审 T-3） | ⚠️ **改判：已被 T-3 补上，W3 只需验收** | 关键事实：**HEAD 里没有 `allowClipboardRead`**（`git show HEAD:AgentPolicy.kt` 零命中），它是工作树里 T-3 新增的（工作树 `AgentPolicy.kt:74`，默认 `true`）。所以 W3 的活只剩两件：① 设置页给一个真开关（现在只有内核侧字段）；② 一条"关掉后 `clipboard get` 必失败且工具体未被调用"的机械判据 |
| W3-10 | **D2D 迁移**（外审 T-6） | ❌ **不进 W3** | 与内核硬化正交；它会动持久化格式，与 journal 落盘格式**同批改会互相污染**。排在 journal 稳定之后 |
| W3-11 | **`PromptCacheLayout`** | ❌ **从 W6 删掉 / 改成"Anthropic 专属"** | GLM 隐式缓存无断点 API（§1.2）；OpenAI 隐式。写成"稳定段前置"是**已经在做的事**（`AgentPromptComposer` 的段顺序），再包一层类就是空转（`00-brief.md` A5 的"空转实现"教训） |
| W3-12 | **`ContextSummarizer`（L2 摘要）** | 🕐 **保持 W6** | 前提是"W3 的 usage 账本满足"（`02-roadmap.md:457`）。账本现在有洞（§5.2），提前做等于在错的账本上做成本决策 |

### 5.2 三笔账：是否与 W3 同批？

#### 账 1：`generationTimeoutMillis` 未接线

**事实**：`AgentPolicy.generationTimeoutMillis = 120_000L`（HEAD `AgentPolicy.kt:86`；T-3 加了
`allowClipboardRead` 后工作树行号变 `:100`），全仓 grep **零消费点**（除声明外 0 命中）⇒ **声明即装饰**。
（2026-10-30 由实施者复核确认，本波不接。）

**后果**：单轮生成没有"首字节前超时"。现在的唯一时间边界是：
- OkHttp 的 `readTimeout = 60s`（**每次 read** 的超时，不是"整轮生成"级）+ `callTimeout = 0`
  （`HttpTransport.kt:93-97`、`:158-160`）⇒ 只要端点持续发心跳，一次生成可以无限长；
- 墙钟 HARD 600s，但**只在轮头判**（`RoundHeadGate.kt:36`）⇒ 一轮里挂死 600s 也无人管；
- 用户点停止（`AgentRunner.cancel()` → `engine.stop()`）是**唯一**的主动出口。

**判断**：✅ **与 W3 同批，但优先级低于 journal**。
理由：它不是"钱"的问题（挂住的请求不产生新 token），是"UI 卡在 isStreaming"的问题。

**接线的三条实现约束**（前两条来自 `ToolApprovalFlow` 陷阱 1，第三条来自 `GenerationRound` 的重试结构）：

1. **必须 `withTimeoutOrNull` + 转 `Failed`，不能 `withTimeout`** ——
   `TimeoutCancellationException` 是 `CancellationException` 子类，会被 `TurnContext.checkActive()`
   与 `AgentRunner.executeLoop` 的 `catch (c: CancellationException)` 接走，
   把"单轮超时"升级成"整 run 取消"（`ToolApprovalFlow.kt:37-46` 同形态）。
2. **落点在 `GenerationRound.attemptOnce` 的 `collect` 外层**（包住 `ctx.engine.generate(request).collect{...}`），
   不是包 `attemptOnce` 本身 —— 否则 `Attempt` 的类型信息与 `recordEstimatedUsage` 的时机都会被打乱。
3. **重试判据要能区分"超时"与"真取消"**：若把超时直接归成 `Attempt.Fatal`，
   它会绕过 `prepareRetry` 直接终止 run；正确做法是新增一个可重试的超时分类
   （与 `Attempt.Retryable` 同路），否则「首字节前超时」这条最该重试的路径反而不会重试。

#### 账 2：每轮请求预算上限（引擎 3 × 内核 2 的乘法）

**事实**：
- 引擎侧 `RetryPolicy.maxAttempts = 3`（首次 + 2 次重试）— `RetryPolicy.kt:31`；
- 内核侧 `GenerationRound.maxRetries = 1`（重试一次）— HEAD `GenerationRound.kt:39`；
- ⇒ **单轮最多 6 次完整 HTTP 请求**（2 × 3），每次都是全量 prompt。
- `maxRunPromptTokens` 只在轮头判（`RoundHeadGate.kt:42`）⇒ 这 6 次在**同一轮内不受任何门控**。
- 账本侧要分清两种"漏"（不要混成一句"全漏了"）：
  - **HTTP 层失败**（429 / 5xx / 连接失败）通常不消耗推理 token ⇒ 不计是**合理的**；
  - **首字节后中断**（`IOException` + `hasEmittedAny`）已被引擎判成 `Completed`
    （`RemoteLlmEngine.kt:254-260`），此时已消耗的 prompt token **没有任何 `Usage` 事件**，
    只能由 `recordEstimatedUsage` 补一次估算（`GenerationRound.kt:174-179`）——
    而 `TokenEstimator` 自陈"系统性低估中文 2~3 倍"（`TokenEstimator.kt:12-13`）⇒ 这部分是**真漏记**。
  - ⇒ 结论收紧为：**乘法本身**（一轮 6 次全价请求、前 5 次不受门控）是主要问题；
    "账本系统性少记"只在中途中断的路径上成立，量级有限但方向是少记。

**判断**：✅ **与 W3 同批，且优先级仅次于 journal**。
修法（不新增类）：给 `TurnContext` 加一个"本轮已发请求数"计数，`GenerationRound.prepareRetry`
与 engine 的 `RetryPolicy.shouldRetry` 共用同一个上限；`RoundHeadGate` 的 token 判据改成
"轮头 + 每次 `Usage` 之后"两处判（`RunState.recordUsage` 里加一个回调，或 `RoundHeadGate` 暴露
`checkTokensOnly()`）。
**不要**简单把 `maxAttempts` 调小 —— 那是把"瞬时故障自愈"也一起砍掉。

#### 账 3：`MODEL` 消息正文留空（需 `RunState` 本轮文本区间）

**事实**：`ToolResultCommit.commit` 落的是 `ChatMessage(role = MODEL, toolCalls = calls)`，
`text` 默认空串（`ToolResultCommit.kt:67`）；KDoc 自己登记了这是 W2 既有限制
（`:32-34`："修它要给 `RunState` 加『本轮文本区间』，属 W3 的账本活"）。
`RunState.textBuffer` 是 **run 级**（`RunState.kt:85`），不按轮切分。

**后果（比外审说的更重）**：
1. 模型看不到自己上一轮说过什么（只看到 toolCalls）⇒ "重复宣告同一工具 / 不收敛"的**机制性**原因；
2. 与 I3（reasoning 不回传）叠加 ⇒ 在 GLM-4.7 上，"工具轮里模型的全部思考 + 全部正文"都丢失；
3. 它同时是 **I1（跨轮分裂）的另一半**：即使将来 history 从 `working` 重建，
   这一段仍然是空的 ⇒ 重建出来的历史依然与"实际发出的序列"不同。

**判断**：✅ **与 W3 同批，且必须与 `reasoning` 回传同一批做**（两者都要"本轮文本区间"这一个机制）。
理由：分开做要给 `RunState` 加两次"按轮切分"的逻辑，第二次必然与第一次打架。

> **三笔账的排序**：账 2（钱/账本）> 账 3（正确性/质量）> 账 1（体验）。
> 但**账 3 是止损判据 2 的直接嫌疑犯**，若止损未过，它插队到第一。

### 5.3 W3 的「第一件事」排序

#### 决策（主理人已定，2026-10-30）

| # | 决策 | 依据 |
|---|---|---|
| D-1 | **I2 的配置证伪实验不另做** | §1.2b 的唯一前缀对照实验已经把门槛（4096）、粒度（64）、生效时机（秒级同步）直接测出来了，结论比"灌长前缀再猜"更硬。§3.4 的实验法保留为**换模型/换供应商时的复用手册**，不在本轮执行 |
| D-2 | **I3（`reasoning_content` 回传）不在本轮修** | GLM-4.7 是本轮目标模型，回传要动 `ChatMessage` 契约（`@Serializable` 加字段 = 落盘格式变）+ `RunState` 按轮切分文本，属 **W3 账 3** 的同一笔账。本轮若 20 题出现 `not_converged` 聚集，**先记为混淆变量**，按下方分支 B 的顺序处理（补观测面 → 修账 3 + I3 → 重跑），**不许**直接归因"模型能力" |

> D-2 的一个必要推论：本轮跑分结论的表述必须带一句限定 ——
> "在未回传 `reasoning_content` 的前提下"。否则 W3 修好 I3 后重跑若变好，原结论就站不住。

#### 分支 A：止损判据**已过**（≥18/20 且 0 条 `MaxRounds`）

1. **`DiagnosticsScreen` + M1/M3 的证据面落地**（半天，风险最低，后面所有诊断都靠它）；
2. **journal**（`AgentRunJournal` + 恢复卡）—— W3 的正门；
3. **账 2**（请求预算上限 + 账本补漏）—— 与 journal 同批，因为 journal 要记"每次请求"；
4. **账 3**（本轮文本区间 + `reasoning` 回传，一起做）；
5. **账 1**（生成超时，`withTimeoutOrNull` 形态）；
6. `FreezeClock`（只服务 SOFT，判据 4 单独硬门）；
7. `PostStreamSignals` / `NoToolCallHandler`；
8. `StreamRepetitionDetector`（尾部）。

#### 分支 B：止损判据**未过**（<18/20 或出现 `MaxRounds`）

**不进 W3。** 转一个"归因波"（W2.6），因为止损判据存在的意义就是"证伪『根因是模型能力』"。
顺序：

1. **先补 M1（观测面）**——否则归因没有数据；
2. **用 `scripts/fixtures/w2-tool-prompts.json` 的 `category` 字段分类失败**
   （`param` / `tool_name` / `not_called` / `not_converged`），**先看分布再动手**：
   - `not_converged` 占多数 ⇒ **先修账 3 + I3**（模型看不到自己的正文与思考），
     然后**重跑**再判 —— 这一条极重要：不修信息回灌就归因"模型能力"，
     等于用"我们把模型的眼睛蒙上"的结果去证明"模型看不见"；
   - `param` 占多数 ⇒ 查 `ToolArgsValidator` + 工具 schema 的 description 是否够（不改缓存面）；
   - `not_called` 占多数 ⇒ 查 `TOOL_GUARDRAILS`（`AgentPromptComposer.kt:62-66`）与 GLM 的
     tool_choice 语义，属提示词/schema 面；
3. **I2 不再做实验**（决策 D-1）：§1.2b 已实测 4096 门槛、本仓前缀 700–800 ⇒ 现役
   **每题都全价**是既定事实。归因时只需注意：**"贵"与"效果差"是两件事**，
   前者已由 I2 解释完，不要拿它去解释 `not_converged`；
4. 只有在 1–3 之后仍 <90%，才回到 `00-brief.md` A10 的原判：重估 MCP/Skill 的收益。

> 分支 B 的一个硬纪律：**不许**在归因波里改 `compressThreshold` / `maxRounds` / `maxAttempts` 这类
> 数字。改数字 = 改判据口径 ⇒ 重跑的结果与第一次不可比，等于把止损判定作废。

---

## 6. 未能取证的点（⛔ 与原因）

| # | 未取证项 | 原因 | 替代判据 / 下一步 |
|---|---|---|---|
| ⛔1 | GLM 缓存的 **TTL（上界）** | 文档未公布；实测只证明了"秒级即生效"（§1.2b），没测过期 | 实测：同一 system 间隔 1/5/30 分钟各跑一次，看 `cached` 是否仍正 |
| ~~⛔2~~ | ~~精确命中粒度~~ | ✅ **已由 §1.2b 实测解决**：门槛 4096、粒度 64、同步生效 | 保留行号以便对照历史；不再是缺口 |
| ⛔3 | GLM 是否**接受 `stream_options.include_usage`** | API 参考页 404（`/cn/api/paas/v4/chat-completions`） | 现役 20 题跑分若 `prompt>0` 即证明 usage 能拿到（无论是否需要该字段）；若 `prompt` 恒 0 ⇒ 拆掉该字段再试 |
| ⛔4 | GLM 是否把**思考 token 计入 `completion_tokens`** | 只有 cache 页的**示例文本**提到，属模型生成内容，非规范条文 | 实测：同一 prompt 开/关思考各一次，比 `completion_tokens` 与可见输出长度 |
| ⛔5 | 缓存作用域是否"账号 × 模型" | 文档未写 | 同 system 换 `model`（glm-4.7 ↔ glm-4.7-flashx）看 `cached` 是否归零 |
| ⛔6 | `temperature` / `top_p` 是否影响前缀命中 | 文档未写 | 【推断】不影响（不进 token 序列），未验证 |
| ⛔7 | 两份外审报告的**原文** | 工作区里没有这两份文件（`find -iname "*review*" -newermt 2026-10-29` 零命中），本笔记的"外审 §四/§五"条目全部来自主理人的转述 | 引用外审结论处已按"中转"处理；正式进 roadmap 前需回原文核 |
| ⛔8 | 20 题跑分的**实际结果** | 用户正在真机上跑，本笔记写作时未回传 | 回传后按 §1.4 的 C1–C5 判据填表；在此之前一切"命中率"结论都是 `⛔ vacuous` |
| ⛔9 | `ContextBudget.prepare` 主线程开销的**实测毫秒数** | 需要真机 systrace / 长历史构造；**T-1 只切了 `tool.invoke`，`prepare` 仍在主线程** | 属 W3「`prepare`/`commit` 搬离主线程」的取证项，本笔记只证明"它在主线程上"（`AgentRunner.kt:120`） |

---

## 附：本笔记的自我审查（写完之后用证伪视角过一遍）

1. **"现役 system 段字节稳定"这个结论有没有可能是假绿？**
   我核的是"没有时间戳/随机量/可变排序"。但 `request.systemInstruction` 来自设置页
   （`AgentRequest.kt:38-39`），**用户改它就会变** —— 这是设计允许的（改了就该失效）。
   真正的风险是 W4 的"技能按最近使用排序"，已登记为 I8。

2. **"跨轮必然分叉"有没有反例？**
   有：如果上一 run **没有**任何工具调用（纯文本对话），则 `working = [USER]`，
   下一 run 的 history = `[USER, MODEL(answer)]`，而上一 run 发出的就是 `[sys, USER]`
   ⇒ **严格前缀，能命中**。所以准确表述是"**有工具调用的轮次**必然分叉"。
   §0 / §3.1(f) / §3.2 I1 / §3.3 I1 四处措辞已统一为"分叉点 = 会话中第一个含工具调用的轮次"。

2b. **本笔记初稿里有一条结论被实测推翻，保留记录**：
   我最初写的是"静态前缀 700–800 token **可能**卡在缓存粒度之下，属灰带，需做配置实验证伪"，
   依据是文档的"建议 500 token"与示例 `1075 → 1024`。主理人的唯一前缀对照实验（§1.2b）证明
   **门槛是 4096、粒度是 64、秒级同步生效** ⇒ 初稿的"灰带"判断是**错的**（差一个数量级），
   正确结论是"**必然 0 命中**"。
   教训与 `AGENTS.md` §二.2 一致：**供应商文档给的数字不能直接当判据**，
   尤其"建议值"与"单点示例"——单点示例（1075→1024）会让人把巧合当成块大小。
   本笔记因此新增 §1.1 的证据等级纪律：机制形态取文档、数字阈值取实测。

3. **M2 会不会破坏既有 `ContextBudgetTest`？**
   会，如果测试断言的是具体数值。所以验收判据里加了"单测 3：`toolTokens = 0` 时逐值相同"，
   并要求实施者先读 `ContextBudgetTest`（`core-agent/src/test/.../loop/ContextBudgetTest.kt`）再动手。

4. **M3 的 hash 会不会成为新的泄漏面？**
   SHA-256 截断 12 位十六进制，不可逆；且 `AgentLogStore.record` 出口仍过 `SecretRedactor`
   （`AgentLogStore.kt:59-65`）。**但** hash 的是 system 正文 —— 若将来 `memoryText` 里有密钥，
   hash 值本身不泄漏内容。可接受。

5. **我有没有把"判据写进方案"当成"判据被执行"？**
   §1.4 的 C1–C5 全部标了"空命中语义"，§4 每项都有"机械可判"的验收条目，
   ⛔ 表里把没取到证的全列了出来。**仍然悬空的是 C2/C3/C4** —— 但性质变了：
   不是"等数据"，而是**已由实测判定本轮必然为 0**（§1.2b）。
   因此在 roadmap 里**不许**写"缓存已生效"，也不许写"缓存无效"—— 两者都缺证据，
   能写的是"**现役前缀低于实测门槛 4096，故本轮不产生缓存收益**"。

6. **我有没有把"别人正在做的修法"写错？**（第三次修订，2026-10-30）
   有。我向 android-developer 提的第 3 条里写了「若你的修法是把 `executeLoop` 包
   `withContext(Dispatchers.IO)`，注意事件线程会变、UI 侧要适配」—— **这是我假设的修法，不是他的修法**。
   他实际只切了最窄的一段（`ToolCallExecutor.invoke` 里的 `tool.invoke`），
   **`ctx.emit` / `commit` / `ToolApprovalFlow` 全留在 Main ⇒ 事件线程不变、UI 无需适配**。
   §3.1(h)-2 已按此更正，并明确写出"仍成立的两条"（`commit` 与 `prepare` 还在 Main），
   避免读者因为"T-1 已落地"就以为主线程问题已收口。
   教训：**对他人改动的描述必须回读工作树，不能从"我提的建议"外推成"他做了什么"**——
   与 `AGENTS.md` §二.2「外审报告/上一位 agent 的结论都可能不成立」同一条纪律，
   只不过这次不成立的是我自己的转述。
