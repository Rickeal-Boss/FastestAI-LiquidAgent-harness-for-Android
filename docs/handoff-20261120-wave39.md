# W39 交接 — 判据链收口 + 三方拆分 + 断流恒等（W24-02 销账）+ 13 格真机全量复测

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`），基线 tip `052ad9c3f`。
> 台账权威版 = `docs/06-deferred-ledger.md`；本波验收物 = `docs/analysis/w39-acceptance.md`；
> 真机制品 = `evidence/w39-matrix-summary.json`；证据 = `evidence/raw/w39/`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 本交接只引用台账 §一已有行首编号，未引入任何不在台账行首的复合编号。

---

## 一、本波做了什么（一段话）

**先修判据链、再拆行数、再改断流、再全量真机**。第 0 车修的是 W38 审计划出的那条断链：判据 7 的声明正则硬要求冒号 + `_decls[-1]` 跨波取到文件尾 ⇒ W38 段头写成「本波销账 1（W17-02）」时判据串到 W37 段的 W36 三行、本波真销的 `W17-02` 零覆盖；判据 1 默认 `WAVE=W29` + `cd harness-v2` 在仓根必失败 ⇒ 默认态判红而验收物写「实跑有效」。第 1 车先解 G10：`core-context` 1649→1477、`feature-chat` 3996→3978、`feature-settings` 3495→3417、`ChatViewModel` 500→447（`LINE_CAPS` 未调、`TOTAL_LINE_CAP` 未抬），再把 `W24-02` 三处断流一次性修完（引擎 EOF/IOException 两路改 `Retryable(truncatedStream=true)`、SSE `error` 帧不再被丢、`Finished.report` 进 UI 显 `ChatStatus.TRUNCATED`），内核按「先判预算 → `truncateText` → `StreamReset` → `Retrying`」整段回滚重试一次，新增 16 条用例（`EXPECTED_TESTS 829→845`），销 `W24-02`。真机 13 格**全量复测**（0 沿用基线）：`PASS 6 / PARTIAL 4 / VACUOUS 2 / FAIL 1`，其中 F1 视觉口径首次真 PASS（红方块 + 绿圆逐字命中）、T03 的 1M 端点 403 已解除（995.9K 读数 + 请求成功）、E03 north 两次 404 实证、F6 首次触及 `nothing_to_drop` 分支。

---

## 二、四份审查报告分歧裁决（7 条）

| # | 分歧 | 裁决 |
|---|---|---|
| 1 | 判据 7 正常态红、销账零覆盖（报告 C）vs 判据 1 回显有效（报告 B） | 采报告 C；B 的结论是显式传参下成立、未记录传参 |
| 2 | 「吞 CE 清零」名副其实（报告 D）vs 全树 52 处 `runCatching` 属虚报（报告 C） | 两者都对但口径不同：suspend 路径清零成立，同步调用属 §五.4 豁免；不重开 `W17-02` |
| 3 | 三方余量「一寸没腾」（报告 C） | 采纳，真拆（步 3）；这是本波唯一「先拆后改」的硬前置 |
| 4 | 命名四处漂移（报告 C）/ `K_coverage_13` 名实不符（报告 B） | 归一为 `K_coverage`；历史行保留旧名 + 注记 |
| 5 | INC「A/B 对照」措辞虚报（报告 C） | 改措辞「单包功能正常 ⇒ 非回归」 |
| 6 | 主车选 `W24-02`（报告 D）vs 先拆 `W20-02`（报告 A） | 串行：先拆再 `W24-02`，派单卡原序成立 |
| 7 | journal 去留（报告 B 称两条用户指令冲突） | 本波不夹带，保留独立裁决域 |

**调度层面的事实**：4 席并行（开发 / CI / 方案 / 主理人），文件域切分为「`.kt` ∪ `.github`」「`docs/notes` ∪ `docs/analysis` ∪ `evidence/*.json`」「台账 ∪ handoff ∪ 验收物」三块，未发生写冲突；中途一次「CI 席重建 APK 与我装机并行」造成 APK sha 分歧，处理方式是**代码冻结 + 同 tree**，并把「打包 sha 非判据」写进验收物限定。

---

## 三、守恒式两数

- 第 0 车：新开 0 / 销账 0（判据链内容订正，不注水）。
- 第 1 车：新开 0 / 销账 1（`W24-02` 满足出口判据）。
- **全波：新开 0 / 销账 1**（`0 ≤ 1` ✅）；台账 216 行零增行、无 `W39-` 行；CLOSED 37→38、OPEN 168→167。

---

## 四、第 0 车（判据链收口）

- 判据 7：正则 `本波销账\s*[:：]?\s*`（冒号可省）+ 只取最后一个 `### W<n> 段` 的声明；W38 段补标准行 `> **本波销账：W17-02**`；`W17-02` 证据列补「**W38 本波复核指路**」。
- 判据 1：`REPO_ROOT` 兜底替 `cd harness-v2`；默认 `WAVE` = 台账最大段号；默认 `EXPECT` = 该段「本波新开挂账 N」（与守恒式同源，不回落 3）。
- 内容订正：`K_coverage_13`→`K_coverage`（w38 制品 `assertions[].id` 同步）、`w37-acceptance:91` 净 −9 → +17、`coverage_note` 补取证/沿用分布、F6 note 改「本波仅 `below_trigger`」、INC 裁决 #5 改「单包非回归」、冻结声明②括号、冻结条款补「局部内容订正 ≠ 重出历史制品」。
- 三条判据的正常态/注入双态实跑原文见 `docs/notes/w29-closeout-checklist.md` §「W38 判据内容订正实跑输出摘录」与 `_w39/k6_mut_w39.txt`。

---

## 五、第 1 车（拆分 + W24-02）

### 5.1 拆分（只搬不改语义）

| 迁移 | 从 | 到 | 行数 | 说明 |
|---|---|---|---|---|
| `SwitchGuard.kt` | `core-context` | `core-agent` | 109 | 包名保持 `…core.agent.context`；`core-context` 内部零引用（搬前已 grep） |
| `SettledCompressPolicy.kt` | `core-context` | `core-agent` | 92 | 同上；生产调用点在 `feature-chat`（`shouldEvaluate`/`shouldApply`） |
| `SwitchDecision.kt` | `feature-settings` | `core-agent` | ~60 | 纯决策层；Dialogs 留在 feature-settings，只改 import |
| `ChatContextWindow.kt` | `feature-chat` | 拆分 | 56 | `contextLimitOf` → `core-context`（+29）；`contextWindowCap` → `core-data`（新建 `ContextWindowCap.kt`） |
| 附件/端点/输入动作 | `ChatViewModel` | `ConversationOps` | ~110 | `ChatViewModel` 500→447，5 个 public 委托保持 `app` 侧调用零改动 |

全仓 main `37705 → 38005`（+300）：拆分开销 +45、W24-02 +255。`LINE_CAPS`/`TOTAL_LINE_CAP` 未动。

### 5.2 W24-02（方案 B 落地）

**先 RED**：`RemoteLlmEngineStreamFailureTest` 的 E1 在未修版本实跑失败（`AssertionError` at `:76`，`_w39/e1-red.log`），证「200 + delta 后关流」当时被判成 `Completed(LENGTH)`。

| 层 | 改动 | 用例 |
|---|---|---|
| `core-engine` | 两条断流通路改 `Retryable(NETWORK, truncatedStream=true)`；零事件 200 保持 `Fatal(PROTOCOL)`；`decodeFailure` 消费 `event: error`/顶层 `error`（终帧后忽略） | 8 条 |
| `core-model` | `GenerationEvent.Failed` 追加 `truncatedStream: Boolean = false`（非 `@Serializable`，线格式零影响） | — |
| `core-agent` | `prepareRetry`：先判预算（放弃不回滚）→ `truncateText` → `StreamReset` → `Retrying`；无产出不发 `StreamReset`；usage 不回滚 | 4 条 |
| `core-data` | 端到端真 HTTP：hits==2、终态唯一且非 `LENGTH`、文本拼接==第二段 | 2 条 |
| `feature-chat` | `ChatStatus.TRUNCATED` + `EventReducer.Finished` 读 `report?.kind`；正常结束回落 `null` | 2 条 |

`docs/01-architecture.md:528` 的「首字节后不重试」同提交改写为「首字节后不静默 Completed；截断走整段回滚后重试一次」——代码与文档不许打架。

**未做（如实登记）**：`retry:` 纯帧消费（`SseEventParser:169` 丢弃）、`RunState.truncateReasoning`。

---

## 六、真机矩阵（13 格，全量复测）

`state_counts={PASS:6, PARTIAL:4, VACUOUS:2, FAIL:1}`；34 顶层键 / `K_coverage` 自证 / `evidence_ref` 13/13 解析 / `iterations_total=13` / `declared_feature_faces` 7 面全有格。

| 格 | W39 | 要点 |
|---|---|---|
| E01_lightning | ✅ | strict 逐字 `OK`；1.8k in / 48 out；上下文 1.9K / 127K |
| E02_dots3 | ✅ | 缓存命中 0%→11.4%→67.9%→85.0%（1536 cached）；真实上下文 62.2K |
| E03_north | ⛔ | 两次 HTTP 404（端点侧）；UI 可见非静默 |
| E04_nv_alt | ✅ | Nano Omni strict 逐字 `OK` |
| T01_32k | ⚠️ | 无 32K 载体（最小 128K）；不沿用历史 PASS |
| T02_512k | ✅ | 520.2K 读数 + 62.2K 真实行使 |
| T03_1m | ⚠️ | 995.9K 读数 + 请求成功（403 解除）；满窗真压 `NOT_EXERCISED` |
| F1_vision | ✅ | 红方块/绿圆/位置逐字命中（W38 为 PARTIAL）；未用 1×1 |
| F2_workspace | ✅ | 审批「允许一次」+ 设备侧 4 字节 `4f4b3339` |
| F3_skill | ⛔vacuous | 0 已安装 SKILL.md |
| F4_mcp | ⛔vacuous | 0 server |
| F5_manual_compact | ⚠️ | 62.2K 下确认框走完 → 「没有可压缩的内容…找不到安全的截断点」 |
| F6_auto_compact | ⚠️ | `auto_compact=noop reason=nothing_to_drop`（首次触及该分支），无 `applied` |

**回灌级别**：只回灌脱敏 UI dump 文本判据 + `deviceLogTail.txt`；不回灌整段 logcat、不回灌凭据 / 未打码账号字段 / 设备序列号 / 本机路径；模型名仅公开名。

**设备侧两条坑（记账）**：① 静置后回锁，`uiautomator dump` 退化为锁屏节点，点击全部落空 ⇒ 取证前 `keyevent 224` + 上滑核 `mWakefulness=Awake`；② 连按 BACK 3 次会退出应用进快捷设置 ⇒ 回前台用 `monkey -p <pkg>`，不要连按。

---

## 七、四闸门 + CI

| 闸 | 结果 | 四态 |
|---|---|---|
| G1 编译 | `BUILD SUCCESSFUL in 1m 18s` | ✅ |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（`include=14==数组==目录`），`G20 141 handoff ID 全在台账内` | ✅ |
| G3 selftest | `30/30 PASS（234s）` | ✅ |
| G4 fulltest | `tests=845 failures=0 errors=0 skipped=0`（14 模块） | ✅ |
| CI | Build `44775770144` completed/success（5 job 全绿）+ Secret `44775769489` completed/success @ `5bdb383` | ✅ |

链接：`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44775770144`。

---

## 八、明确未做（逐条带号）

`W24-01`（思考框 4 态）｜`W12-02`（`cachedLabel` 与四态归一）｜`W9-01`（命题② + B1 三条 + `alreadyCompressed` 单指针）｜`W20-02`（本体再分层，本波只腾行数）｜`W20-05`/`W9-11`（1M 真压双硬限）｜`W5-03/05/06`（MCP 外部 https）｜`W9-05`/`W24-03`（journal）｜`retry:` 纯帧消费 与 `RunState.truncateReasoning`（W24-02 已知边界）。

真机未转 PASS 的四项：E03（端点 404）、T01（无载体）、F3（0 skill）、F5（无安全截断点）。

---

## 九、OPEN 挂账

逐条（触发信号 / 缓解动作 / 挂账号）见 `docs/analysis/w39-acceptance.md` §步 6。本波销 `W24-02`，其余维持 OPEN，不注水销账。

---

## 十、交给外部审查团队的制品 + 边界声明

**制品**：`docs/analysis/w39-acceptance.md`、本交接、`evidence/w39-matrix-summary.json`、`evidence/raw/w39/`（13 JSON + `deviceLogTail.txt`）、`docs/06-deferred-ledger.md`（W39 段 + `W24-02` 销账）、`docs/notes/w29-closeout-checklist.md`（判据 1/7 修订 + W38 实跑摘录）、`docs/analysis/w37-acceptance.md` 与 `w38-acceptance.md`（内容订正）、`.github/workflows/build.yml`（`EXPECTED_TESTS` 两行）。

**不含**：会话历史 / 凭据 / 端点完整标识（模型名仅公开名）/ 账号字段 / 设备序列号 / 本机路径 / 「建议下一链做什么」。

**边界**：① 矩阵跑在代码冻结后的 tree 上，同 tree 两次打包 sha 不同（269 tasks up-to-date）⇒ APK sha 不作判据；② E03 404 是端点侧观察，未查供给方公告；③ F1 PASS 依赖「端点高级配置手工开启图片输入」的前置；④ F5/F6 PARTIAL 属「缺可安全截断的中段」，不是机制缺失，`applied=true` 仍未在真机出现；⑤ `SwitchNotice` 落 `core-agent` 是层级口径待复核项，不是已定论。

> 本波止于交接，交由外部团队审查。
