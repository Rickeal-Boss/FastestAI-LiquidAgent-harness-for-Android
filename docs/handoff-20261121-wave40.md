# W40 交接 — P0-3 断流重试 + 老号代码面 + 真机矩阵补格

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`），
> 基线 `0cb87ee` → 主体 `e900f44`（feat）+ docs 收尾提交 `b66c8b5`（纯文档）。
> 台账权威版 = `docs/06-deferred-ledger.md`；本波验收物 = `docs/analysis/w40-acceptance.md`；
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 本交接只引用台账 §一已有行首编号，未引入任何不在台账行首的复合编号。

---

## 一、本波做了什么（一段话）

**先裁决分歧、再收口文档、再落代码、再真机验证、再推 CI**。第 0 车把四份外审的三个 P0 按用户裁决落地：
`W20-02` 补 W39 已腾行数的行内注记（避免下波把已做的再做一遍）；`W24-02` 采案 A（不回开、CLOSED 行内
收窄原契约③为显式 LENGTH 老债、`strings.xml` 删「或被中断」）；error 帧口径确认为「已吐字 ⇒ 置
truncatedStream 走整段回滚重试」。第 1 车落代码面：`decodeFailure` 对已吐内容的可重试错误帧置位、
`OpenAiErrorFrame` 去 payload 全文 + 结构化 type/code 优先（修限流文案含 token/limit 误判超窗）、
`ContextBudget.cachedLabel` 接四态、`autoCompactOnSettled` 挪到 `onEvent` 之后、`truncateReasoning`
补完 W24-02 已知边界。`EXPECTED_TESTS 845→849`（4 条新用例，含两条交叉必红 + reasoning 必红变异）。
真机 6 端点预检、E01 echo 逐字 PASS、F2 file_write 审批+落盘 `W40OK` PASS、F3 @ 菜单两 skill 可见
PASS（推翻 W39 vacuous）、F5 确认框+noop PARTIAL；E02/E03 端点侧 404 如实标注。四闸门全绿、
CI 三 run success（主体 Build `44881943856` + Secret `44881943855`；docs 收尾 Secret `44883294189`）。

---

## 二、四份审查报告分歧裁决（3 条主裁 + 1 条沿用）

| # | 分歧 | 裁决 |
|---|---|---|
| 1 | W24-02 整行 CLOSED 与原契约③不符（报告 D/A）：案 A 不回开 vs 案 B 回开 | 采**案 A**（用户指令「全面推进 + 推荐案 A 即执行」）：不回开，CLOSED 行内收窄③、`strings.xml` 删「或被中断」、已知边界 2→4 条 |
| 2 | error 帧已吐字后不重试（报告 D P0-3）：置 truncatedStream vs 「仅零产出重试」注记 | 采**置 truncatedStream**：限流/服务端掐流与网络截断同构（都已产出半截、重发都从头再答），回滚重试是唯一合理处置；零产出不置位 |
| 3 | OpenAiErrorFrame haystack 含 payload 全文误判超窗 | 采结构化优先（type/code 直判）+ 去 payload 全文 + 2 条交叉必红样本 |
| 4 | E03 404 四态语义（FAIL vs PARTIAL） | 沿用 W39：端点侧不可用、非 harness 回归；本波 Dots3 也 404 同型，不新开裁决 |

**调度层事实**：按专家团 SOP 建团队（`w40-harness`）派 solution-strategist + android-developer 两席并行；
两席先后因 **429 rpm exhausted** 中断（4m19s / 11m36s）。按铁律「429 ⇒ 主理人接手补完、不重试 spawn」，
主理人核实磁盘：android-developer 中断前已完成 G1 编译（APK 15:25:07 在盘），solution-strategist 无落盘产出；
真机链与全部代码改动由主理人亲自落地。这不是「子席产出」冒充。

---

## 三、守恒式两数

- 第 0 车：新开 0 / 销账 0（判据链内容订正）。
- 第 1 车：新开 0 / 销账 0（老号行内注记，出口判据未全行使，不注水销账）。
- **全波：新开 0 / 销账 0**（`0 ≤ 0` ✅）；台账 216 行零增、无 `W40-` 行；
  CLOSED 38 / OPEN 167 / CARRIED 11 不变。

---

## 四、第 0 车（文档收口）

- `W20-02` 行补 W39 注记：三方已腾行数（`core-context` 1649→1477 余 173 / `feature-chat` 3996→3978 余 22 /
  `feature-settings` 3495→3417 余 83 / `ChatViewModel` 500→447），本体再分层仍未做、`LINE_CAPS` 未调。
- `W24-02` 行补案 A 注记：③收窄为显式 LENGTH 老债（断流的两种结局都不产生 TRUNCATED 状态，结构不可达
  是设计结果、不是缺陷）；`strings.xml` 删「或被中断」；已知边界 2→4 条（retry:/重试入口/error 帧/reasoning）。
- 判据 1/6/7 双态实跑闭合；判据 7 自身修复：首版 W40 段解释文字含 `W24-02` 被正则误抓 ⇒ `declared=1` 假绿，
  删编号后重跑 `declared=0` 才是本波真值（**判据要被验证，不是读出来就采信**）。

---

## 五、第 1 车（代码面）

| 改动 | 文件 | 用例 |
|---|---|---|
| P0-3 error 帧已吐字置 truncatedStream | `RemoteLlmEngine.kt` decodeFailure 分支；`GenerationEvent.Failed` KDoc 扩义 | E5 改向 / E5b 零产出 |
| 错误分类订正 | `OpenAiErrorFrame.kt`：haystack 去 payload、结构化 type/code 优先、词表兜底 | E5c 限流含 token+limit / E5d 服务端含 context（两条交叉必红） |
| W12-02 四态读者 | `ContextBudget.kt` cachedLabel 接 `promptCacheStatus` 四态 | 真机 E01/F2 输出兼容 |
| W9-01 命题②时机 | `ChatViewModel.kt` collect：onEvent 提前、autoCompact 挪后 | G23 唯一调用点仍成立、真机日志在案 |
| W24-02 边界补完 | `RunState.truncateReasoning` + `GenerationRound.prepareRetry` 正文/思考一起回滚 | A5（删调用 ⇒ 计数 11≠6 必红） |

`EXPECTED_TESTS 845→849`；`LINE_CAPS` / `TOTAL_LINE_CAP=42500` 未调；行数未触顶
（core-agent 5895/6500、core-engine 1426/2600、feature-chat 3984/4000）。

---

## 六、真机矩阵

6 端点预检全在（key 走 Keystore 加密、无明文落盘）。四态：
E01 NV Lightning echo ✅（逐字 `OK_W40_ECHO`）｜E02/E03 Dots3+North 404 ⛔（端点侧，非 harness 回归）｜
F2 workspace ✅（审批框 + `probe_w40.txt` 落盘 `W40OK`）｜F3 skill @ 菜单 ✅（两 asset skill 可见，推翻 W39 vacuous）｜
F5 手动压缩 ⚠️（确认框走完 + noop「找不到安全截断点」）｜W9-01 autoCompact 本波日志 ✅。
未行使：T01（无载体）/ T03 满窗真压 / F1 本波重跑 / F4（0 server）/ F6 applied 分支——不冒充通过。

**回灌级别**：只回灌脱敏 UI dump 文本判据 + `agent-0.log` 尾；不回灌整段 logcat / 凭据 / 未打码账号 / 设备序列号。

---

## 七、四闸门 + CI

| 闸 | 结果 | 四态 |
|---|---|---|
| G1 编译 | `localbuild.sh` rc=0、APK 产出 | ✅ |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0`（G20 141 ID、G23 唯一调用点） | ✅ |
| G3 selftest | `30/30 PASS（262s）` | ✅ |
| G4 fulltest | `tests=849 failures=0`（14 模块） | ✅ |
| CI | Build `44881943856` + Secret `44881943855` success @ `e900f44`；docs 收尾 `b66c8b5` Secret `44883294189` success（四字段 API 复核） | ✅ |

链接：`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44881943856`

---

## 八、明确未做（逐条带号）

`W12-02`（探针按四态落盘）｜`W9-01`（B1 其余三条 + DEFAULT_ratios + alreadyCompressed 单指针）｜
`W20-02`（本体再分层）｜`W20-05`/`W9-11`（1M 真压）｜`W5-03/05/06`（MCP 外部 https）｜
`W9-05`/`W24-03`（journal）｜E02/E03 端点 404 恢复｜`retry:` 帧透传与可点重试入口（W24-02 CLOSED 行已知边界）。

## 九、OPEN 挂账

逐条（触发信号 / 缓解动作 / 挂账号）见 `docs/analysis/w40-acceptance.md` §「OPEN 挂账」表。本波零销账、零新开，
不注水；台账 216 行三数不变。

## 十、交给外部审查团队的制品 + 边界声明

**制品**：`docs/analysis/w40-acceptance.md`、本交接、`docs/06-deferred-ledger.md`（W40 段 +
四行老号注记）、`docs/notes/w29-closeout-checklist.md`（判据 1/6/7 双态实跑摘录）、
`.github/workflows/build.yml`（EXPECTED_TESTS）、12 文件源码 diff（`e900f44` 主体 + `b66c8b5` 文档收尾）。

**不含**：会话历史 / 凭据（含 GitHub PAT，需轮换）/ 端点完整标识（模型名仅公开名）/ 账号字段 /
设备序列号 / 本机路径 / 「建议下一链做什么」。

**边界**：① 两席 429 中断、主理人接手（G1 编译由中断前完成、其余主理人亲自落地）；
② E02/E03 404 是端点侧观察、未查供给方公告；③ F3 @ 菜单选中时文本顺序异常属 W17-02 已销账行
drv.text() 残余形态、本波不新开行；④ 本波未新建制品（判据冻结，34 键零增删）；
⑤ CI Build run `44881943856` completed/success（由 in_progress 转 completed 后实取复核）；⑥ `SwitchNotice` 层级口径仍待裁（沿用 W39）。

> 本波止于交接，交由外部团队审查。
