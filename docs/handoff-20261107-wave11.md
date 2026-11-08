# W11 交接 — 第 0 车：口径回填 + W9-01 补完 + W9-04 四态扩容 + 跨免费模型真机矩阵（2026-11-07）

> 基线 `98916eaa`（W10 关波，已推 `main`，CI run `42365515962` 五 job 全 success）。
> 上游依据：`docs/handoff-20261107-wave10.md` §九、`docs/notes/w11-plan.md`、桌面
> `外部研究对账与W11排期裁决.md`、`裁决/LiquidHarness启发与落地建议.md`（报告1）、
> `裁决/纯视觉路线评估与LiquidHarness搬迁建议.md`（报告2），以及 6 份 W9-01/W10 外审。
> 判定四态 ✅ / ⚠️ / ⛔ / ⛔vacuous。**「未触发」≠「已验证通过」；PC 侧结论不得写成设备侧可用。**
> 🔴 **行号一律锚 `HEAD:98916eaa`**；本波工作树已漂移，定位以**符号名**为准（审查席时点更正）。
> 提交身份 `-c user.name="Rickeal-Boss" -c user.email="263587600+Rickeal-Boss@users.noreply.github.com"`；
> PAT 只以 URL inline 一次性使用、绝不落盘；免费节点 key 只从用户文件读入内存注入。

---

## 一、本波定位与结论

W11 = **W10 收尾核对**（不是重做）+ **第 0 车正确性/口径回填** + **跨免费模型真机功能矩阵**。
裁决文档给的「三车结构」中，本波只落**第 0 车**，第 1 车（L2 摘要请求复用父前缀）与第 2 车
（守卫专项 + `feature-settings` 拆分）**未开工**，按台账顺序留给 W12，不预写结论。

| 项 | 结论 | 四态 |
|---|---|---|
| W10 口径回填 | handoff §九 / w11-plan 顶部 / 台账 §二-bis 四行全部落笔；`98916eaa` 已推、CI 全绿由 GitHub API 独立复核（本地 `origin/main` ref 陈旧停在 `767fd47`，不采信） | ✅ |
| W9-01 补完 | `HardBudgetShrinker.promptChars` 补 `sendableAttachments` 遍历 + 失实 KDoc 订正 + 2 用例；并修审查席证伪的**标定放大路径** | ✅ 一处，整体仍 `OPEN` |
| W9-04 扩容 | usage 四态 + 三套厂商字段 + `cacheWrite` 全链路透传 + 分母一次钉死 + 6 条新 fixture/回归用例 | ✅ `CLOSED` |
| id 假漂移 | 回源判第三份：`message.id` 不进 wire ⇒ 改指纹而非派生稳定 id；连带修 2 处依赖 id 的假判据 | ✅ `W11-02` 关闭 |
| 严格 @Test 对账 | **798** ↔ `.github/workflows/build.yml:132` 同提交一次改（主理人独占） | ✅ |
| 本地闸门 | G1 出包 70,309,888 B；G2 `tests=798/0/0/0`；G3 `arch-guard PASS ok=27 vacuous=1 violation=0`；G4 selftest 30/30 | ✅ |
| 真机矩阵 | 冷启动 + 往返 + 工具循环 + 审批 + 文件读写 + 设备侧 KV cache 命中全部取到正向证据；详见 §六 | ✅ 部分 |
| W9-05 journal/FreezeClock | **未开波（第 7 次顺延，如实登记）**，见 §八 | ⛔ |
| 推送 / CI | commit **`504ed40`** 已推 `main`；CI run **`42389047452`** `completed/success`，**5 job 全 success**（Unit tests / Assemble Debug / Arch guard / Lint / Secret scan） | ✅ |

---

## 二、输入对账：三份裁决 × 6 份外审，先证伪再采纳

### 2.1 时点差（本仓铁律：采纳外审处方前必须 grep 现役树）

报告1 / 报告2 都基于 **2026-11-06 W8 收口快照**（409 文件 / 741 单测 / W9 承接 11 项），
比现役 `98916eaa`（430 文件 / 778 单测 / W10 已关波）落后 3 个有效提交。逐条回树结果：

| 外审断言 | 回现役结论 |
|---|---|
| 报告2 缺口1「附件三处盲」（估算/字符闸/指纹） | ⚠️ **三处只剩一处**：W10/S4 已补 `TokenEstimator.estimateMessages` + `PromptPrefixTest` 回归钉；0 车已补 `ContextCompressor.canMerge` 守卫；**仅 `HardBudgetShrinker.promptChars` 仍盲** ⇒ 本波补完（W9-01，不新开号） |
| 报告1 §四「W9-03 append-only 不省体积，不是压缩」 | 🔄 **对计划态正确、对会话级实现已过时**：W10/S3 真删中段（头锚 4 + 尾部机械摘要）。但该定律在**请求级 L2** 上完全 live（摘要插进稳定前缀），两套压缩必须分开记 |
| 报告1 增量一 usage 四态 / 增量二 摘要请求复用父前缀 / 增量三 `prompt_cache_key` | 前两条 ⛔ **仍 live**（四态本波落地、增量二留 W12）；第三条受 `feature-settings` 余 8 卡住，排在拆分之后 |
| 报告2 缺口3 无 `core-device` | ✅ 仍成立（范围判断，非 bug），W11 不登记设备波 |
| 报告2 阶段1 cleartext 裁决 | ⛔ 前置未过（`W5-03` OPEN + G15b 钉死 `usesCleartextTraffic=false`），本波不为测试放开 |
| 报告1 §一「稳定派生 id 是领先项」 | ⚠️ 只对 L2 摘要成立；W10 会话级摘要另用 `newId()`，且**本地指纹含 `message.id` 是假漂移源**（见 §2.3） |

### 2.2 审查席证伪清单（6 份外审逐条回树）

- **`"parallel_"` 笔误**：三份外审同时抄错，现役无此符号 ⇒ 不采纳。
- **「代码仍在未提交态」**：已由 `98916eaa` + CI run `42365515962` 证伪（外审读的是 W10 交接的「进行中快照」抬头）。
- **「R5-P0：随机 `message.id` 伤 provider 缓存」**：`OpenAiCompatibleAdapter.messageObject()` 只写
  `role / content / tool_calls[].id`，**不写 `message.id`** ⇒ 伤的是**本地指纹判据**，不是 provider 前缀缓存。
- **「触发线插队 / `COMPACT_TRIGGER_CHARS` 半迁移」**：本波不碰该常量，施工单无此项。
- **`jsonMode` 勘误**：审查席初报「仅 `OpenAiCompatibleAdapterTest:534` 一处」，被其全量复核自证伪 ——
  现役是 **11 处字样 / 10 行**，集中在 `OpenAiCompatibleAdapter.kt` 的两个并列 `if` 分支。
  结论「`jsonMode × tools` 互斥未做」更硬，登记 `W11-04`，本波不塞进单波。
- **两份外审矛盾时回源判第三份**：开发席 R1 主张给 history 派生稳定 id、审查席 R3/R4 主张指纹去 id
  ⇒ 回 `messageObject()` 源码裁决：**去 id**（派生稳定 id 属无收益改动）。见 `W11-02`。

### 2.3 本波自己抓到的三处「假判据」（改机制后的连带失效）

1. `ManualCompressTest` 用「双剥 `role:id:` 前缀」比内容段；去 id 后新格式 `ROLE:text` 会把 text
   一起剥掉 ⇒ 恒空串 ⇒ ⛔vacuous。改为整段逐字节比 + 扰动自证（改一个字必判不等）。
2. `ContextBudgetTest.compressionInvalidatesThePrefixOnlyOnce` 的 `line()` 所有正文恒为
   `"x".repeat(200)`，断言「压缩后前缀变了」**只靠 id 抖动通过** ⇒ 去 id 后恒假。
   改为正文尾部带 id、**长度仍恒 200**（预算不变、内容真变）。
3. 本波初版把图片当量（4096 字符本地估算常量）留给 `GenerationRound` 做 chars/token 标定分子，
   主理人判「方向保守」⇒ **审查席证伪**：provider 一张图只计 85–255 视觉 token 时
   `measuredCharsPerToken` 被顶到 `ceiling=6.0`，`charCap = hard × cpt` 相对 floor 1.8
   **放大 3.3 倍 = 超窗方向**。修法见 §四。

---

## 三、Droidance / 纯视觉路线：如何仿照落地（用户点名）

**许可证先裁决**：`github.com/ybblab/Droidance` 实为 **Apache-2.0**（探活席读 LICENSE 核实），
与本仓同许可；但本仓铁律仍是 **零代码搬运**（Operit LGPL / Operit2 AGPL 系对照项目更严），
只取结构思想、措辞自写。报告2 点名的 `CacheableRequestHistory` / `TaskContextCompactor` /
`PromptCacheUsage` / `ResponsesPromptCacheKey` 四类真实存在。

**「纯视觉」要拆三层**：执行底座（libadb 自连 → Sidecar → VirtualDisplay → 注入输入）在
「不 root / 不 PC / 后台不占主屏 / 覆盖自绘界面」四条同时成立时接近最优；感知层「只截图不读树」
不是最优（树 p50≈6ms、截图+VLM 贵得多），但 2026 年 API 34 `accessibilityDataSensitive`、
API 36 连 `dispatchGesture` 一起挡、Play 把 `isAccessibilityTool` 限给残障辅助类 App
⇒ **「不开无障碍」是想上架的必要条件**。⚠️ 本仓真机是 OPPO PDRM00 / **SDK 33**，
不受 API 34/36 限制 ⇒ 「平台封死」**不是本波取证阻塞**，别拿它当借口；但 targetSdk 升级 /
上架 / API34+ 设备必须走非无障碍路线。

**四阶段路线（照报告2，按投入产出，W11 只走阶段 0 的一部分）**：

| 阶段 | 内容 | 本波状态 |
|---|---|---|
| 0 账本铺平（零设备工程） | 修 attachments 三处盲 + `canMerge` 守卫 + usage 四态/多厂商字段 + 命中率分母钉死 | ✅ **本波完成**：W9-01 补完 `promptChars`、W9-04 四态扩容；`canMerge` 0 车已销 |
| 1 不自建设备层，走 MCP 接外部服务 | 先出 `W5-03` cleartext 裁决（HTTPS 夹具 / 回环例外，**不改 G15b**），把 mobile-mcp / Droidance Sidecar / Shizuku 作为 tool provider 接 `core-mcp`；`W9-07` 安全收口先行（DecisionGate 式 allow/confirm/block，超时或裁决失败**降级人工确认**，只读工具不入门） | ⛔ 前置未过，W12+ |
| 2 视觉兜底 | 图片预算四条（帧指纹去重 / 历史图 4 张 16MiB / 长边 1600·JPEG78 / `observation_ref`）+「图片独立 USER + 决策尾」接 `AgentPromptComposer.sectionsTyped`；先接零图片的「结构化优先 / 原生能力优先」工具 | ⛔ 未做；红线不变：**图片算进预算、不进 `PromptPrefix` 指纹** |
| 3 `core-device` | Sidecar + VirtualDisplay + 输入注入 + 配对 UI + 前台服务 + 音频/诊断治理 | ⛔ **独立波 / 独立仓**，仅在真机证据表明「必须自建」后开；全仓 `AccessibilityService`/`MediaProjection`/`VirtualDisplay`/`dispatchGesture` 零命中 |

**明确不照搬**：整搬纯视觉；依赖 `AccessibilityService` 做自主执行；把「是否暂停交给用户」
交给模型规则判断（Droidance README 自承「并非硬性拦截」）——本波真机已实证审批硬闸门
（§六 F3），必须保持；为凑缓存加长 system；把图片字节算进指纹。
`prompt_cache_key`（报告1 增量三，`SHA256(model+instructions+tools)` 严格 host 白名单、
**不进 `RunnerKey`**）排在 `feature-settings` 拆分之后（余 8），本波不提前做。

---

## 四、代码改动（文件域 + 红态判据）

| 文件（模块） | 改动 | 判据 / 红态 |
|---|---|---|
| `core-context/HardBudgetShrinker.kt` | `promptChars()` 拆成 `textChars + attachmentChars`，补 `sendableAttachments`（图片按 `ESTIMATED_IMAGE_TOKENS × CHARS_PER_TOKEN_ASCII` 同源折算、文本按真实字符）；新增 `calibrationChars()` 供标定；失实 KDoc 订正 | 删附件遍历 ⇒ `attachmentsAreCountedOnBothBudgetScales` 必红；`calibrationChars` 误用 `promptChars` ⇒ `calibrationCharsExcludeTheEstimatedImageAllowance` 必红 |
| `core-agent/loop/GenerationRound.kt` | 标定 `charsSent` 改用 `calibrationChars`；`recordUsage` / `UsageUpdated` 透传 `cacheWrite` | 单行紧凑写法守住 G10（392/400） |
| `core-model/Generation.kt` | 新增 `PromptCacheStatus` 四态 + `promptCacheStatus()` + `cacheHitRatio()`（分母 `prompt + cacheWrite`，`coerceIn(0,1)` 仅作显示上界）+ `TokenUsage.cacheWriteTokens`；销 `TODO(W6)` | `PromptCacheUsageTest` 9 条；`hitRatioIsClampedWhenProviderPromptExcludesTheRead` 钉 >1 失真 |
| `core-model/GenerationEvent.kt` / `AgentEvent.kt` | `Usage` / `UsageUpdated` 加 `cacheWrite: Int? = null`（默认值 ⇒ 既有构造点零改动） | 旧 journal 缺字段反序列化为 `null` |
| `core-engine/.../OpenAiCompatibleAdapter.kt` | usage 归一：OpenAI `prompt_tokens_details.cached_tokens` / Anthropic `cache_read_input_tokens` / DeepSeek 平铺 `prompt_cache_hit_tokens` / Gemini `usageMetadata.cachedContentTokenCount`；write 三处字段 | 3 fixture：DeepSeek 平铺 / 三套 write / usage 末帧缺失 |
| `core-model/StreamAccumulator.kt` | `cacheStatus` 用**计算属性**（`usage==null ⇒ USAGE_NOT_OBSERVED`）⇒ 四态有生产消费点，`reset()` 无独立状态可漏 | `StreamAccumulatorTest` 四态全可达 + `cacheWriteTokensSurviveTheStreamAccumulator`；299/300 行守住 G10 |
| `core-engine/RemoteLlmEngine.kt` / `core-agent/loop/RunState.kt` | `cacheWriteTokens` 全链路透传 + 累加 + `usage()` 快照 | 审查席逐构造点复核，无半迁移、无 `null→0` |
| `core-context/PromptPrefix.kt` | `renderHistoryHead()` 去掉**不进 wire** 的 `message.id`，保留 `call.id`/`result.callId` | 去 id ⇒ 假漂移消失；`historyHeadStillTracksToolCallIdsThatReachTheWire` 证灵敏度未丢 |
| `feature-chat/EventReducer.kt` | `buildUsageLabel` 由独立算式 `cached*100/prompt` 改调 `cacheHitRatio`，写入量进分母 | `usageLabelKeepsCacheWriteInTheSingleDenominator`（删分母 write ⇒ 必红） |
| `PromptPrefixTest` / `ManualCompressTest` / `ContextBudgetTest` | 三处「依赖 id 假漂移」的判据修成真判据（见 §2.3） | 首轮 fulltest 真实抓到 `compressionInvalidatesThePrefixOnlyOnce` 红态 |
| `.github/workflows/build.yml` | `EXPECTED_TESTS` 778 → **798**（主理人独占，一次改） | 严格 `grep -rnE '^\s*@Test\b'` = 798 双向对账 |
| `docs/06-deferred-ledger.md` | §二-bis 四行回填 + 新增 §二-ter `W11-01..05` | G20 复跑 OK |
| `docs/handoff-20261107-wave10.md` / `docs/notes/w11-plan.md` | 跑后口径回填 + 硬伤订正（已推 CI / F8·F9 预写 ✅ 收回 / 余量模块写错 / launcher 实测） | 纯文档 0 run ⇒ 必须与代码同提交 |

## 五、本地闸门（跑后回填）

| 闸门 | 结果 |
|---|---|
| G1 出包 `./gradlew :app:assembleDebug` | ✅ `BUILD SUCCESSFUL`，`app-debug.apk` **70,309,888 B** |
| G2 全量单测 `bash scripts/fulltest.sh` | ✅ **`tests=798 failures=0 errors=0 skipped=0`**（82 个 `TEST-*.xml`、14 模块全覆盖、XML mtime 全本轮） |
| G3 架构守卫 `bash scripts/arch-guard.sh`（仓库根） | ✅ **`result=PASS ok=27 vacuous=1 violation=0 self_fault=0`**（`VACUOUS=1` = G19，≤ 基线） |
| G4 守卫自证 `bash scripts/arch-guard-selftest.sh` | ✅ **30/30 PASS（277s）**；本波零新增守卫/selftest（W9-10 未开）、守卫脚本未改 ⇒ 结果有效 |
| G5 LF 归一 `_w11/normalize_lf.py` | ✅ `EventReducer.kt` 一处 CRLF 归一；19 个改动 `.kt` 其余全 LF |

🔴 **两处 G10 触顶按「先下沉/收回，禁调 `LINE_CAPS`」处理**，不是改阈值：第 1 轮 `GenerationRound` 404>400
（`UsageUpdated` 拆多行）、第 2 轮 `StreamAccumulator` 307>300（`cacheStatus` 字段式）。第 3 轮因会话中断
只跑了 fulltest；**第 4 轮为最终口径（全绿）**。前三轮红态全部留痕在 `_w11/fulltest*.log` / `archguard*.log`。

### 5.1 推送与 CI（跑后回填）

| 项 | 值 |
|---|---|
| commit | `504ed40` `feat(w11): 第 0 车口径回填 + W9-01 补完 + W9-04 四态扩容 + 跨免费模型真机矩阵` |
| push | `98916ea..504ed40 HEAD -> main`（`--dry-run` 先验连通，PAT 只 URL inline 一次性、不落盘） |
| CI run | **`42389047452`** = `completed / success` |
| job 1 | Unit tests — `success` |
| job 2 | Assemble Debug (JDK 21) — `success` |
| job 3 | Arch guard (26 guards + mutation selftest) — `success` |
| job 4 | Lint (baseline gate) — `success` |
| job 5 | Secret scan (gitleaks + trufflehog) — `success` |

证据：GitHub API `actions/runs/42389047452` + `.../jobs`（本地 `origin/main` ref 陈旧，一律以 API 为准）。

## 六、真机功能矩阵（OPPO PDRM00 / A13 / SDK33，`adb -P 15037`）

**装包与冷启动**：`adb push` + `pm install -r` ⇒ `Success`；`am start -W -n
com.rickeal.liquidharness.debug/com.rickeal.liquidharness.MainActivity` ⇒ `LaunchState=COLD /
TotalTime=1360ms`、pid 存活、logcat 无 FATAL。launcher 由 `cmd package resolve-activity` 实测裁决
（`...debug/com.rickeal.liquidharness.MainActivity`）。

| # | 功能 | 设备侧证据 | 四态 |
|---|---|---|---|
| F1 多模态输入 | 本波未挂图；PC 侧 W10 已证 nv-vl/dots 双通（200×120 真图、禁 1×1） | ⛔ 未行使（PC 侧结论不得写成设备侧可用） |
| F2 音频输出 | 探活席复证 `https://integrate.api.nvidia.com/v1/audio/speech` = **404 / supported_voices=null** | ⛔ 平台阻断（只复核一次） |
| F3 工作空间文件读写 | 题面 `Please write a file probe.txt containing HELLO then read it back` ⇒ 审批框 `工具「file_write」请求授权 {"path":"probe.txt","content":"HELLO"}` → 点「本会话允许相同调用」→ `file_write ✅19ms / wrote 5 chars` → `file_read ✅51ms / HELLO` → 模型 `Done — wrote 'probe.txt' with 'HELLO' and read it back`；`run-as cat .../workspace/probe.txt` = **`HELLO`** | ✅ 完整闭环（含审批硬闸门） |
| F4 记忆 | 设备侧 `memory.json` 存在，未走 write→冷启→read | ⛔vacuous |
| F5 skill | 设备侧 `skills/` 存在，未触发 `skill_load` | ⛔vacuous |
| F6 MCP | `usesCleartextTraffic=false`（G15b）+ `W5-03` 未裁决 | ⛔ 平台阻断 |
| F7 KV cache | 用量行连续两轮 **`1.6k in / 57 out (1600 cached, 97.9%)`** → **`1.6k in / 49 out (1600 cached, 95.4%)`**，在「工具调用 + 审批」真实会话形态下取得 | ✅ 设备侧单点正向；⚠️ **双向探针（改首字节归零）未取** ⇒ `W7-02` 保持 `OPEN` |
| F8 手动压缩 | 溢出菜单「压缩上下文」→ 确认框「压缩这段对话的上下文?」（含缓存重建说明）→ 点「压缩」 | ⚠️ 确认框已行使；**分隔卡 / `dropped=N` 未取证** ⇒ 主体 ⛔vacuous |
| F9 自动压缩 | `agent-0.log` 本轮 `INFO auto_compact=noop reason=below_trigger` | ⚠️ 判据被行使但**未触发**；0 命中 ≠ 通过 |
| F10 长上下文阶梯 | 未灌阶梯题面；用量行上下文 `1.7K / 507.9K` | ⛔ 未行使（PC 侧 W10 4k–512k 全通、1M 不稳，不写成设备侧） |
| F11 分块装配 | 未行使（工作区有 W5 的 `chunk1..5.txt` 夹具） | ⛔vacuous |

**端点探活（PC 侧，探活席）**：Droidance `LICENSE` = Apache-2.0；OR `dots-3-note-preview:free` /
`north-mini-code:free`、NV `nemotron-3.5-super-vl-120b-a12b` / `mistral-large-4-1t-a52b` /
`stepfun-ai/step-5-preview` **5/5 chat 200**；OR 两模型 usage 字段最全（含 `cache_write_tokens`），
NV prompt 侧只到 `cached_tokens`，step5 无 `details`，**无任何模型返回 `cache_read_input_tokens`**
（该解析分支为 Anthropic 预留，本波无正向样本）；第 6 个 `deepseek-v4.1-flash` 超时不计入。
⚠️ `build.nvidia.com` 是网页非 API，NV 一律 `https://integrate.api.nvidia.com/v1`。
step5 的 M8 在 W10 记 `partial`、本波判据层面记 ⛔，两者不混。

取证：`_w11/screen_*.png`、`_w11_probe/probe-report.md`、设备侧 `files/harness/logs/agent-0.log`。

## 七、挂账状态（唯一真值源 = `docs/06-deferred-ledger.md`）

| ID | 状态 | 本波动作 |
|---|---|---|
| W9-01 | `OPEN` | 补完 `promptChars` 附件盲区 + 标定放大路径修复；**整体仍 OPEN**（`DEFAULT_*_RATIO` 搬进 `AgentPolicy` 未做） |
| W9-03 | `OPEN` | 会话级形态 W10 已落地、真机 M9 未验收；两套压缩拆清后 L2 前缀复用留 W12 |
| W9-04 | **`CLOSED`** | 四态 + 三套字段 + 分母钉死 + 全链路透传 + 6 用例；`W7-02` 仍 `OPEN` 不连带关闭 |
| W9-05 | `OPEN` | **第 7 次顺延（未开波），W12 第 0 车硬截止**，见 §八 |
| W11-01 | `OPEN` | R2 分隔卡顺延，先拆 `ChatMessageList` 子组件再移卡 |
| W11-02 | **`CLOSED`** | id 假漂移：修指纹（去 `message.id`）而非派生稳定 id；连带修 3 处假判据 |
| W11-03 | `OPEN` | `cache_invalidated` 二值过度声明，W12 与 M9/M8 同车 |
| W11-04 | `OPEN` | `jsonMode × tools` 互斥未做（外审勘误：11 字样 / 10 行，集中在 `OpenAiCompatibleAdapter`） |
| W11-05 | `OPEN` | `cacheHitRatio` 分母按 provider 族归一（`coerceIn` 只是显示上界） |
| W7-02 | `OPEN` | 设备侧单点正向 `1600 cached` 到手，但双向探针未取 ⇒ 不 CLOSED |
| G20 / G21 | OK / OK | 59 个 handoff ID 全在台账内、CLOSED 行证据非空；`VACUOUS=1 ≤ 基线 1`（G19） |

## 八、未做 / 明确不做（防误读为漏项）

- **W9-05 journal / FreezeClock 未开波（第 7 次顺延）**。裁决文档把「W11 必须开波」列为先还债项，
  **本波未满足，不美化**。理由：用户指令优先「跨免费模型真机矩阵 + 推 CI」；journal v1 + FreezeClock +
  前台服务 + 恢复卡是独立内核硬化波，塞进同车会破坏单波归因并撞 `core-agent` 行数面。
  **硬截止：W12 第 0 车第一件事**；若 W12 再顺延，须用户显式裁决「永不实现」并改状态。
- 裁决文档的**第 1 车**（L2 摘要请求复用父前缀，`ContextSummarizer` 端口收父前缀 + tools、
  `tool_calls ⇒ SummaryResult.Failed`）与**第 2 车**（`W9-10` 守卫专项 + `feature-settings` 拆分）
  未开工；第 1 车排在四态之后是**依赖决定**（灰度需 `cached_tokens>0` 判据）。
- 设备侧 1M 真压（`RoundHeadGate` 400k 阻断 = `W9-11` 未解锁）；MCP 明文联调（G15b）；音频输出（404）。
- R2 分隔卡、`W11-03`、`W11-04`、`W11-05`、`feature-settings` 拆分、`prompt_cache_key`、
  报告1 增量三~六：全部未做，已在台账登记编号与落点。
- 本波**零新增 arch-guard 守卫 / 零新增 selftest case**（`W9-10` 未开；新守卫扫空集 ⇒ G21 判红）。

## 九、环境坑位（本波实证）

- **G10 两次触顶**：`GenerationRound` 404>400、`StreamAccumulator` 307>300。`StreamAccumulator` 改用
  计算属性派生 `cacheStatus` 既省行数又顺带消掉「`reset()` 漏复位」隐患 —— 一改两得。
- **首轮 fulltest 红是真判据**：`ContextBudgetTest.compressionInvalidatesThePrefixOnlyOnce`
  靠 id 抖动通过，去 id 后恒假 —— 修测试（正文带 id、长度恒 200）而不是放宽断言。
- **adb**：`-P 15037`（5037 无设备）；daemon 每命令后退出 ⇒ 相关命令放**同一 Bash 调用**连发；
  `MSYS_NO_PATHCONV=1` + 源路径 `<Windows 形态源路径>`；`pm install -r`（直接 `adb install` 报 -99）；
  **灭屏会挂起 `pm install-commit`** ⇒ 先 `KEYCODE_WAKEUP` + `input swipe` 解锁再装。
- **UI 坐标 ×2**：截图按 540×1200 读，设备 1080×2400；**键盘弹出后输入框/发送键上移**
  （发送键收起态 ≈ (946,1964) → 弹起态 ≈ (940,1354)），按旧坐标点会落空导致「题面没发出去」。
- **`input text` 末尾吞字符**：`"OK"` 只进 `O` ⇒ 发送前先补一字符并截图确认题面完整。
- **launcher**：`cmd package resolve-activity` 实测 `...debug/com.rickeal.liquidharness.MainActivity`，
  两份文档冲突已由实测裁决。
- **`origin/main` 本地 ref 陈旧**（停在 `767fd47`）⇒ 判「已推 / CI 绿」只认 GitHub API
  `branches/main` + `actions/runs`，不采信本地 ref。
- **子代理边界**：审查席/盘点席全程只读；探活席仅写 `_w11_probe/`；**有一个子代理违规在仓内
  `docs/analysis/source-review-2026-11-07.md` 落了分析文档**，按纪律**不纳入本次提交**（只 `git add` 显式路径）。
