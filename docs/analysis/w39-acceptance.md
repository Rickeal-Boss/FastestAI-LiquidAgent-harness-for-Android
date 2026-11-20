# W39 验收物 — 判据链收口 + 三方拆分 + 断流恒等（W24-02 销账）+ 13 格真机

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`），W39 基线 tip `052ad9c3f`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 台账权威版 = `docs/06-deferred-ledger.md`（本波新增 W39 段，落笔人 = 主理人单人）。
> 真机制品 = `evidence/w39-matrix-summary.json`；证据 = `evidence/raw/w39/`（13 JSON + 1 txt）。

---

## 抬头：三条规约持续 + 守恒式两数

| 规约 | 本波结果 |
|---|---|
| **守恒式** `新开 ≤ 销账` | **新开 0 / 销账 1（`W24-02`）**，`0 ≤ 1` ✅。台账 216 行零增行、无 `W39-` 行；CLOSED 37→38、OPEN 168→167、CARRIED 11 不变。 |
| **判据冻结** | 不新增判据编号 / checklist / 制品 / CI job / 四态枚举。仅「让既有判据真会响」：判据 7 正则容错 + 判据 1 自洽推导。制品 schema 34 键零增删（w39 制品同键集）。 |
| **全面推进** | `.kt` 变更 30 处（6 新增 / 5 迁移 / 1 删除 / 18 修改，含测试文件）；`@Test` **829 → 845**；真机 13 格**全量复测**（0 沿用基线格）。 |

---

## 步 1 — 四份审查报告分歧裁决 ⚠️（裁决全部落地，但两份报告的判据结论互斥）

| # | 分歧 | 裁决 | 依据 |
|---|---|---|---|
| 1 | 报告 C（W38 深度审查）判「判据 7 正常态 RED、本波销账零覆盖」；报告 B 判「判据 1 回显实跑有效」 | **采报告 C**。我在现役树用 `git show HEAD:` 的台账与现役判据脚本双跑，复现出 `RED … W36-01/02/03` 与 `cd harness-v2` stderr；报告 B 的「有效」是显式传参下的结论，未记录传参 | 实跑原文见 `docs/notes/w29-closeout-checklist.md` §「W38 判据内容订正实跑输出摘录」A 段 |
| 2 | 报告 D 称「吞 CE 反模式清零名副其实」；报告 C P1-10 称「全树 52 处 `runCatching`，标题按字面是虚报」 | **两者都对，口径不同**：`suspend` 路径清零成立，全树同步调用 52 处属 §五.4 豁免。本波不重开 `W17-02`，只在台账保留原销账证据 | `W17-02` 行已含 W38 销账原文 |
| 3 | 报告 C P1-8「三方余量一寸没腾」 vs 派单卡「第 1 车前先拆」 | **采纳拆分**，且证明可行：见步 3 | 行数表 |
| 4 | 报告 C P1-3「命名四处漂移」/ 报告 B 低危残留 1「`K_coverage_13` 名实不符」 | **采纳归一**为 `K_coverage`；历史行/历史制品内的旧名保留并加注记 | 步 2 |
| 5 | 报告 C P1-6「INC A/B 对照措辞虚报」 | **采纳，改措辞**：只有新包单侧序列，结论按「单包功能正常 ⇒ 非回归」，不写「已做 A/B」 | `docs/analysis/w38-acceptance.md` 已订正 |
| 6 | 报告 D 建议「W39 主车给 W24-02」 vs 报告 A 建议「先 W20-02 拆分」 | **两者串行**：先拆（解 G10 阻塞）再 W24-02。派单卡原序即此，实测顺序成立 | 步 3 |
| 7 | journal 去留（报告 B P1 称两条用户指令冲突） | **本波不夹带**：journal 需独立裁决（`W9-05`/`W24-03`），不因「全面推进」自动开波；交接如实登记 | 步 6 |

**未采纳的一条**：报告 C 建议「`evidence/w38/` 补 `cells_provenance` 结构化字段」。理由：w38 制品已有 `cells_provenance`（字符串），改成结构体属**改历史制品结构** = 重出历史制品，冻结边界内不做；w39 制品按同键延续。

---

## 步 2 — 第 0 车：判据链收口 ✅

**改的文件**（纯文档/判据/制品，零 `.kt`）：`docs/notes/w29-closeout-checklist.md`、`docs/06-deferred-ledger.md`、`docs/analysis/w37-acceptance.md`、`docs/analysis/w38-acceptance.md`、`evidence/w38-matrix-summary.json`。

| 判据 | 默认正常态（实跑原文） | 注入必红（实跑原文） | 四态 |
|---|---|---|---|
| 判据 1 段唯一性 + 守恒式回显 | `W39 rows = 0` / `守恒式两数回显（W39）：…本波新开挂账 0 / 本波销账 1（W24-02）…`，无 `RED`、无 `can't cd` stderr | `WAVE=W29` 保持可传参覆盖 | ✅ |
| 判据 6 制品断言 | `OK feature/evidence invariants`（w38 与 w39 双制品） | **11 类必红对 w39 全部 RED**（由 CI 席按标准注入集重跑：`k6_pop` / `k6_iter` / `k6_mcp` / `k6_ref` / `k6_privkey` / `k6_face` / `k6_fail` / `k6_nocell` / `k6_nomcp` / `k6_nomut` / `k6_count`，原文 `_w39/mut39x/`） | ✅ |
| 判据 7 销账锚点 | `OK closed rows carry runnable pointers (W30+)` + `OK closed rows carry current-wave pointers (W39, declared=1)` | 删 `W24-02` 的「本波复核指路」⇒ `RED closed without current-wave pointer: W24-02`；删 `W17-02` 锚点 ⇒ `RED … W17-02`（W38 欠账同法复核） | ✅ |

**关键修正（这一波真正修的东西）**：
1. 判据 7 的声明正则从 `本波销账[:：]` 放宽为 `本波销账\s*[:：]?\s*`，且只取**最后一个 `### W<n> 段`** 内的声明（不再 `_decls[-1]` 跨波取到文件尾）。W38 段补标准行 `> **本波销账：W17-02**`。
2. 判据 1 默认 `WAVE` = 台账最大段号、默认 `EXPECT` = 该段「本波新开挂账 N」（与守恒式同源），`cd harness-v2` 改 `REPO_ROOT` 兜底。
3. **两处注入失败的自证**（写进交接当方法论；**含主理人自己第一轮的两处无效变异**：隐私注入形态不匹配 ⇒ 该行留档先是 `OK`、改正后才 `RED`；`state` 注入把键名写成 `FAIL0` 导致红因可解释性差。**判据 6 的权威必红集以 CI 席的 11 类标准注入为准**，主理人那 6 条只作过程留痕）：第一次把锚点替换成「（本波复核指路已删空）」——替换文本仍含 token ⇒ 判据仍双 OK，属**无效变异**；整段截断才真 RED。第二次隐私注入写成 `sk-` + 含连字符的短串（`sk-` 后紧跟另一段单词再接数字），因不匹配 `sk-[A-Za-z0-9]{16,}` ⇒ 假绿；换成 `sk-` 后直接接 16 位以上纯字母数字才 RED（字面值不入仓，只描述形态）。**变异样本自身必须先验**。

内容订正：`K_coverage_13` → `K_coverage`（w38 制品 `assertions[].id` 同步）；`w37-acceptance:91`「拆分后净 −9」→「W36→W37 实测 2489→2506 = **+17**」；`coverage_note` 补「本波取证 PASS 4 / 沿用基线 PASS 2」；F6 note 改「本波仅 `below_trigger`，`nothing_to_drop` 未再触发」；冻结声明②多余右括号；冻结条款补「局部内容订正 ≠ 重出历史制品」。

---

## 步 3 — 第 1 车：三方拆分 + W24-02 断流恒等 ✅（销 W24-02）

### 3.1 三方拆分（G10 前置）

| 模块/文件 | W38 | W39 | 余量 | 处置 |
|---|---|---|---|---|
| `core-context` | 1649 / 1650 | **1477** | 173 | `SwitchGuard`(109) + `SettledCompressPolicy`(92) 迁 `core-agent`（包名不变） |
| `feature-chat` | 3996 / 4000 | **3978** | 22 | `ChatContextWindow` 拆解：`contextLimitOf` → `core-context`，`contextWindowCap` → `core-data` |
| `ChatViewModel` | 500 / 500 | **447** | 53 | 端点/输入/附件/错误条 5 个动作外提 `ConversationOps`，本体只留一行委托 |
| `feature-settings` | 3495 / 3500 | **3417** | 83 | `SwitchDecision` 纯决策层迁 `core-agent` |

`LINE_CAPS` 未调、`TOTAL_LINE_CAP=42500` 未抬；全仓 main **37705 → 38005**（+300 = 拆分开销 +45、W24-02 +255）。**⚠️ caveat**：`SwitchNotice`/`PendingSwitch` 这两个「决策结果值对象」随 `SwitchDecision` 落在 `core-agent`；它们不含 UI 文案（G16 = 0 处中文硬编码实测），但若将来长出文案字段必须迁回 `feature-settings`。

### 3.2 W24-02 三处（设计由 solution-strategist 裁决为方案 B：内核层整段回滚重试）

| 处 | 改动 | 判据 |
|---|---|---|
| ① EOF 无 `[DONE]` | `RemoteLlmEngine.runAttempt` 两条断流通路（循环后 EOF / `IOException` + `hasEmittedAny`）改判 `Retryable(NETWORK, truncatedStream=true)`；零事件 200 仍 `Fatal(PROTOCOL)`（守住 `EndpointProbeTest` 语义） | `RemoteLlmEngineStreamFailureTest` **8 条 0 失败**（E1 在未修版本先跑出 `Completed(LENGTH)`，`AssertionError` at `:76`，原文 `_w39/e1-red.log`） |
| ② SSE `error` 帧被丢弃 | `ProviderAdapter.decodeFailure` 默认 `null`；`OpenAiCompatibleAdapter` 实现 `event: error` / 顶层 `error` 映射（rate/429、overload/5xx 可重试，context 溢出复用 hint，其余 fail-closed）；命中即 break 且先 `emitFlushed`，**终帧后 error 帧忽略**（防双终态） | 同文件 E5–E7 |
| ③ `Finished.report` 在 UI 被丢 | `EventReducer.Finished` 读 `report?.kind == TRUNCATED_KIND` ⇒ `ChatStatus.TRUNCATED`；正常结束回落 `null`（防永久提示）；文案走 `strings.xml` | `EventReducerTest` **36 条 0 失败**（+2） |

内核侧：`prepareRetry` 对 `truncatedStream` **先判预算**（放弃路径绝不回滚）→ `truncateText` → `emit(StreamReset)` → `emit(Retrying)` → 退避。`GenerationRoundTruncationRetryTest` **4 条 0 失败**（generateCount==2、`StreamReset` 恰 1 个且位于两次 TextDelta 之间、失败路径只发 1 个 `Failed` 零 `Finished`、`truncatedStream=false` 仍被旧闸门拦住）。
端到端：`StreamTruncationRetryTest` **2 条 0 失败**（真 HTTP：hits==2、终态唯一且非 `LENGTH`、TextDelta 拼接 == 第二段）。

**实现替换（如实登记）**：派单卡写「MockWebServer 红态必备」，`core-engine` 测试依赖只有 junit + kotlin.test（`build.gradle.kts:54-55`），全树 `mockwebserver` 0 命中 ⇒ 夹具用 JDK `HttpServer`。判据仍是「200 + delta 后关流 ⇒ 请求数 ≥2 且非 `Completed(LENGTH)`」，**机制替换**、不构成注水。

`@Test` **829 → 845**（+16：引擎 8 / 内核 4 / 端到端 2 / UI 2），`build.yml EXPECTED_TESTS` 同提交改 845。

---

## 步 4 — 真机多模型矩阵（13 格）⚠️

`state_counts = {PASS:6, PARTIAL:4, VACUOUS:2, FAIL:1}`（W38 为 6/6/1/0，且 7 格沿用基线）。**本波 13 格全部本波行使，0 沿用**。

| 格 | 状态 | 要点 |
|---|---|---|
| E01_lightning | ✅ | strict 逐字正控：回答体仅 `OK`（1.8k in / 48 out；上下文 1.9K / 127K），矩阵 APK 上复跑一次 |
| E02_dots3 | ✅ | 512K 档 4 轮使用，缓存命中 **0% → 11.4% → 67.9% → 85.0%**（1536 cached）；真实上下文堆到 62.2K |
| E03_north | ⛔ | `north-mini-code:free` 两次均 **HTTP 404**，UI 可见非静默；端点/模型侧不可用，**不推断为 harness 缺陷** |
| E04_nv_alt | ✅ | NV Nano Omni 30B strict 逐字 `OK` |
| T01_32k | ⚠️ | 现役 6 端点最小声明 131072（127K 读数），**32K 档无载体**，不沿用历史 PASS |
| T02_512k | ✅ | 520.2K 读数 + 真实 62.2K 上下文行使；512K 满窗未压 |
| T03_1m | ⚠️ | SuperVL **995.9K 读数 + 请求成功**（W38 的 403 已解除）；1M 满窗真压 `NOT_EXERCISED`（双硬限未解） |
| F1_vision | ✅ | ground truth 左红方块 + 右绿圆；回答逐字命中「A red square on the left / A green circle on the right」⇒ 真 PASS（W38 为 PARTIAL）；未用 1×1 像素 |
| F2_workspace | ✅ | `file_write` 审批「允许一次」→ 执行 → 设备侧回读 **4 字节 `4f4b3339` = OK39**，mtime 本波 |
| F3_skill | ⛔vacuous | 设备侧 `files/harness/skills` 为空，无已安装 SKILL.md ⇒ @ 路径无载体（`none_by_design`） |
| F4_mcp | ⛔vacuous | MCP 服务器页 0 server（`none_by_design`）；外部 https 仍挂 `W5-03/05/06` |
| F5_manual_compact | ⚠️ | 62.2K 上下文下走完「更多操作 → 压缩上下文 → 确认」，toast「没有可压缩的内容…找不到安全的截断点」⇒ 未真压 |
| F6_auto_compact | ⚠️ | 达触发线后日志出现 `auto_compact=noop reason=nothing_to_drop`（W38 只到 `below_trigger`）⇒ **首次触及该分支**；仍无 `applied` |

**回灌级别**：只回灌脱敏 UI dump 文本判据 + `deviceLogTail.txt`（`auto_compact` 行）；不回灌整段 logcat、不回灌凭据 / 未打码账号字段 / 设备序列号 / 本机路径；模型名只写公开名。

**设备侧新坑（记账）**：静置数分钟后设备回锁，`uiautomator dump` 退化为锁屏节点（`充电完成 / 日期 / 天气通知`），点击全部落空。每次取证前必须 `keyevent 224` + 上滑并核 `mWakefulness=Awake`；**不要连按 BACK**（3 次会退出应用进通知栏/快捷设置）。

---

## 步 5 — 四闸门 + G20 + CI

| 闸 | 结果 | 四态 |
|---|---|---|
| G1 编译 | `BUILD SUCCESSFUL`（`:app:assembleDebug`，含 W24-02 全量改动） | ✅ |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`，`G9 include=14 == 数组 14 == 目录 14`，`G10 行数闸门 OK` | ✅ |
| G3 selftest | `selftest: 30/30 PASS（234s）`（含 `G23 自动压缩被调用`、`VAC-G1b`、`VAC-G21` 三条必红演练） | ✅ |
| G4 fulltest | `通过：tests=845 failures=0 errors=0 skipped=0`（14 模块；`feature-chat:104`、`core-agent` 含迁移来的两支） | ✅ |
| G20 | `[G20] 挂账不丢失 OK（141 个 handoff ID 全在台账内，CLOSED 行证据均非空）`；handoff 文件 38 个；主理人独立复算：台账 216 ID、38 handoff、漏登 `[]` | ✅ |
| CI | Build `44775770144` completed/**success**（5 job 全绿）+ Secret scan `44775769489` completed/**success** @ `5bdb383` | ✅ |

**G4 附核**：拆分把 `SwitchGuardTest` / `SettledCompressPolicyTest` 从 `core-context` 搬到 `core-agent`，须确认两支 `TEST-*.xml` 存在且 `tests>0 failures=0`（防「测试在搬家途中丢」）。

---

## 明确未做（逐条带挂账号前缀）

- `W24-01`：深度思考展开框全黑未动（UI 玻璃/AGSL 面，需真机深色/浅色双主题取证）。
- `W12-02`：`ContextBudget.cachedLabel` 与 `promptCacheStatus(usageObserved)` 归一未做（UI 侧读者已在 W38 落地）。
- `W9-01`：命题② `ChatViewModel:407` 与 `alreadyCompressed` 单指针未动（合并设计禁拆）。
- `W20-02`：三方拆分只做了「腾行数」，本体拆分（`ChatViewModel` 再分层 / `ChatScreen` 拆文件）未做；本波已把三模块余量从 1/4/5 抬到 173/22/83。
- `W20-05` / `W9-11`：1M 真压双硬限（`PreStepBudget` 档位闸 27904 + `RoundHeadGate` 400k）未解。
- `W5-03` / `W5-05` / `W5-06`：MCP 外部 https 未行使（G15b 禁明文）。
- `W9-05` / `W24-03`：journal 未开波（独立裁决域）。
- `retry:` 纯帧消费（`SseEventParser:169` 丢弃）未做；`RunState.truncateReasoning` 未做 —— 二者登记为 W24-02 的已知边界，不冒充已修。
- E03（north 404）、T01（32K 无载体）、F3（0 skill）、F5（无安全截断点）四项**未转为 PASS**，原因逐条见步 4。

---

## 步 6 — OPEN 挂账（触发信号 + 缓解动作 + 挂账号）

| 挂账 | 触发信号 | 缓解动作 | 号 |
|---|---|---|---|
| 断流重试只覆盖「整段截断」 | 真机出现「重试后文本重复」或 `StreamReset` 后仍有拼接 | 核 `prepareRetry` 的 `truncateText` 是否漏回滚 reasoning 通道 | `W24-02`（CLOSED 行的已知边界） |
| 32K 档无载体 | 新端点声明 ≤32K 或档位下调后 | 重跑 T01 | `W20-02` / T01 |
| north 404 | OpenRouter 恢复该模型 | 重跑 E03 两次 | `W24-02` 无关联；属端点侧 |
| 视觉端点需手工开「图片输入」 | 新装端点首次视觉测试 | 端点高级配置勾选「图片输入」 | `W3-07` |
| 技能面 vacuous | 导入任一 SKILL.md 后 | 重跑 F3 + @ 菜单 | `W17-02`（已 CLOSED 面） |
| MCP 面 vacuous | 外部 https server 可用 | 跑 F4 + G15b | `W5-03` / `W5-05` / `W5-06` |
| 1M 满窗 | 双硬限解除 | 注入 1M 满窗请求 | `W20-05` / `W9-11` |

---

## §5.6 提交与 CI

- 提交序列：**单次主推送** `052ad9c → 5bdb383`（代码 + 文档 + 证据 + 制品同车）；其后的 docs-only 回填提交（含本段）**不触发 Build**（`build.yml` 的 `paths-ignore` 只对 Build 生效，`secret-scan.yml` **刻意不 ignore 任何路径** ⇒ 每次纯文档提交仍会跑一次 Secret scan）。因此 tip 的 CI 结论是「Secret success + Build 沿用 `5bdb383`」，不写成「tip 构建通过」。
- 本地四闸门（同一 tree，实跑原文在 `_w39/g1_final_localbuild.log` / `g2_archguard_final.log` / `g3_selftest.log` / `g4_fulltest.log`）：
  - G1 `BUILD SUCCESSFUL in 1m 18s`
  - G2 `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0` / `result=PASS`
  - G3 `selftest: 30/30 PASS（234s）`
  - G4 `tests=845 failures=0 errors=0 skipped=0`
- CI 四字段（`GET /actions/runs?head_sha=5bdb383…` 实取）：
  | workflow | run id | status | conclusion |
  |---|---:|---|---|
  | Build | `44775770144` | completed | **success** |
  | Secret scan | `44775769489` | completed | **success** |
  - Build 逐 job：`Arch guard（26 guards + mutation selftest）` success / `Unit tests` success（含 `Reconcile EXPECTED_TESTS with real @Test count`）/ `Lint (baseline gate)` success / `Assemble Debug (JDK 21)` success / `Secret scan (gitleaks + trufflehog)` success。
  - 链接：`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44775770144`（Build）、`…/runs/44775769489`（Secret scan）。

---

## 交给外部审查团队的制品清单

`docs/analysis/w39-acceptance.md`（本文）、`docs/handoff-20261120-wave39.md`、`evidence/w39-matrix-summary.json`、`evidence/raw/w39/`（13 JSON + `deviceLogTail.txt`）、`docs/06-deferred-ledger.md`（W39 段 + `W24-02` 销账）、`docs/notes/w29-closeout-checklist.md`（判据 1/7 修订 + W38 实跑摘录）、`docs/analysis/w37-acceptance.md` 与 `docs/analysis/w38-acceptance.md`（内容订正）、`.github/workflows/build.yml`（`EXPECTED_TESTS` 两行）。

**不含**：会话历史 / 凭据 / 端点完整标识（模型名仅公开名）/ 账号字段 / 设备序列号 / 本机路径 / 「建议下一链做什么」。

---

## 限定（不把推断写成事实）

- 真机矩阵跑在**代码冻结后**的 tree 上；同一 tree 两次 `assembleDebug` 产出的 APK sha 不同（`674e6a3b…` vs `397baa3…`，269 tasks up-to-date），归因打包时点而非代码差异 ⇒ 制品只记「W39 断流恒等增量包」，不给单一 APK sha 当判据。
- 判据 1/6/7 与六类必红全部在**现役树仓根**实跑；G3/G4/G20 的终态数由 CI 席整跑后回填，本文件先写期望值再改实数。
- E03 的 404 是**端点侧观察**，未查 OpenRouter 侧公告；不推断 north 永久下线。
- F1 的 PASS 依赖「端点高级配置里手工开启图片输入」这一前置；该开关是设置面，不是 harness 代码路径。
- F5/F6 的 PARTIAL 原因是「本会话缺可安全截断的中段（仅 6 轮）」，不是压缩机制缺失；`applied=true` 仍未在真机出现过。
- `SwitchNotice` 落 `core-agent` 属**层级口径待复核**，不视为已定论。
- **缓存读数只在同一会话内可比**（设备侧独立观察，非 CI 席主张）：Dots3 首轮 `0 cached` → 同会话第 2–4 轮 `1536 cached 11.4% → 67.9% → 85.0%`；换端点或新建对话后回到 `0 cached` 属**前缀重建的预期行为**，不能据此判端点退化，也不能跨端点/跨会话比较。
- **CI 状态判定要用单 run 端点**：`GET /actions/runs?head_sha=<sha>`（批量列表）有约 15 分钟缓存，会给旧快照（本波实测该端点仍报 `Build in_progress`，而单 run 端点 `runs/<id>` 已是 `completed/success`）⇒ 判绿/判红一律以 `runs/<id>` 与 `runs/<id>/jobs` 为准。
- E03 的 404 在 UI 以「本次请求失败：request rejected (HTTP 404)」上屏（`PROTOCOL` 类、非静默）；FAIL 判定依据是「请求被拒」本身，不是「有无提示」。
