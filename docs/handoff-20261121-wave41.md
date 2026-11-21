# W41 交接 — 限流分类补全 / 四态 usage 同源 / 思考框实底 + 真机制品入仓

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `c4fc2cf` → 代码主体 `93808be`（feat，本波唯一代码提交）；证据与验收物随其后的纯文档提交落库。
> 台账权威版 = `docs/06-deferred-ledger.md`；本波验收物 = `docs/analysis/w41-acceptance.md`；
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 守恒式两数只在派单卡抬头写一次，本交接不复述；台账零 `W41-` 行、三数不变。

---

## 一、本波做了什么（一段话）

**先裁决七份审查分歧，再按同一趟车落账实订正与代码，再编译、真机闭环、推 CI、最后补证据入仓**。
第 0 车把 `W24-02` 行尾自相矛盾的「未做」改成 W40 第 1 车已落地的真实状态、把显式 LENGTH 端到端用例
如实记为未落位；`W12-02`「同源归一」过度声明作废；`W9-01` / `W20-02` 补当前状态；W40 收尾契约三处
（tip 时点 / 2 次推送 / 15 唯一文件）与 PAT 表述冲突一并订正。第 1 车补 message-only 限流误判超窗
（无结构化字段时限流措辞先于超窗词表）、数字 `code` 兜底、超窗保留原文；`usageObservedThisRound`
替掉累计 token 反推；`W24-01` 思考块去双层玻璃改主题实底 + Lens 零尺寸守卫。三条错误帧必红样本与
估算 usage 必红样本均在**未修版本实跑 RED**、修复后 GREEN；E5b 的 `>=1` 用 `shouldRetry` 恒 false
变异坐实会响。真机 14 格制品首次完整入仓（34 键冻结），含 1M 档位回读、工作区落盘、skill 菜单、
压缩 noop 日志与思考框四态裁剪图；四闸门全绿，CI Build / Secret 两 run 已触发。

---

## 二、审查分歧裁决（4 条主裁 + 1 条沿用）

| # | 分歧 | 裁决 |
|---|---|---|
| 1 | 报告建议「新开重试入口体验债」并回开断流行 | 不采纳：W40 已裁案 A 不回开，两条边界维持 `W24-02` CLOSED 行内注记，守恒式进水管继续关着 |
| 2 | P0-2 修法二选一（删超窗词表的 `limit` vs 无结构化字段时限流先判） | 采后者：保留共享词表不动，避免改 HTTP 400 路径；补数字 `code` 兜底与原始 message 保留 |
| 3 | P0-3 是否把 `cached=` 日志挪到 usage 到达后 | 采「真实 usage 到达标志」：保留发送前日志位置，避免把前缀账拆成两行；必红样本钉住估算不得写成已观测 |
| 4 | 真机制品连续两波缺席与「判据冻结」冲突 | 按冻结 schema **原样补 34 键制品**，不新建 schema / 判据编号 / CI job；缺格写 vacuous，不沿用历史 PASS |
| 5 | 1M 真压 / MCP 外部 https / journal 二选一 | 沿用既有裁决：只做测试与观察，机制改造不自动解禁；本波未拍板的都不代裁 |

**调度事实**：本波未派并行子席。上一波两席 429 中断的前科按纪律处理：主理人亲自落代码、闸门、真机与文档，
不冒充子席产出。派单前无并发写入，工作树仅历史 `_w3x/` 取证目录 untracked。

---

## 三、账本与守恒

- 台账仍 216 行，CLOSED / OPEN / CARRIED 三数与 W39/W40 一致；零 `W41-` 行；G20 141 ID 不变。
- 四处行内注记：`W24-02`（E1 行尾矛盾订正 + 边界 4→2）、`W12-02`（同源归一作废）、`W9-01`（命题②不重做）、
  `W20-02`（G10 余量收窄）；整行状态均不动。
- 两数只在派单卡抬头；W40 段历史数字作为时点记录保留，不回改。checklist 加的是**边界说明**（摘录不算追加、判据 7 解析规则），
  不新增段、不新增编号。

---

## 四、第 0 车（文档账实）

- `W24-02` 行尾「error 帧已吐字后不重试 + truncateReasoning 未做」删除，改为 W40 第 1 车已落地；
  「端到端形态随第 1 车落位」改为「显式 LENGTH ⇒ TRUNCATED 端到端用例仍未落位」，不虚报 diff。
- `W12-02` 原注记把累计 token 反推写成「同源归一」，本波作废并按真实实现重写。
- W40 交接 / 验收物抬头改「基线→主体→docs 收尾，共 2 次推送」；「12 文件」订正为「15 唯一文件」；
  「PAT 需轮换」从「不含凭据」清单里移出，改成用户侧事项、不在仓内闭环。
- 判据 7 的六步解析规则写进 checklist W40 段末尾；判据 1/6/7 正控与负控输出留档 `evidence/raw/w41/`。

---

## 五、第 1 车（代码面）

| 改动 | 文件 | 判据 |
|---|---|---|
| message-only 限流先判 + 数字 code 兜底 + 超窗保留原文 | `core-engine/.../provider/OpenAiErrorFrame.kt` | E5e（修复前 `RATE_LIMIT` expected / `CONTEXT_OVERFLOW` actual）、E5f（`PROTOCOL`→`RATE_LIMIT`）、E5g（原文被硬替换） |
| 本轮真实 usage 标志 | `RunState.kt`（置位/重置）、`ContextBudget.kt`（读取） | 必红样本临时回退修复 ⇒ RED；修复后 GREEN |
| E5b 空转断言收紧 | `RemoteLlmEngineStreamFailureTest.kt` | `shouldRetry=false` 变异 ⇒ hits=1、RED |
| W24-01 思考块实底 + 零尺寸守卫 | `GlassSurface.kt`、`Lens.kt` + `LensCanvasGuardTest.kt`（新增） | 四态真机裁剪图 + `Size(0,0)`/NaN 纯函数断言 |
| 代码卫生 | `AppContainer.kt`、`GenerationRoundTruncationRetryTest.kt`、`ConversationOps.kt` | 死 import 清除、A5 注释 11→12、KDoc 按现役 `EventReducer.finish` 实态 |

`EXPECTED_TESTS 849→856`（+7），同提交对账；`LINE_CAPS` / `TOTAL_LINE_CAP` 未调；`GlassSurface.kt` 587/600 未增行。

---

## 六、真机矩阵（14 格，四态齐现，制品入仓）

设备先唤醒（亮度 231 非黑）+ 三步法装机；冷启 `Status: ok / TotalTime 1330ms`。密钥留于真机 Keystore。

| 结论 | 格 |
|---|---|
| ✅ 5 格 | E01 Lightning 逐字回声、E04 SuperVL 1M 回读 + 缓存链、F2 `W41OK` 落盘 hex、F3 @ 菜单两技能、W24-01 思考块四态 |
| ⚠️ 3 格 | F5 手动压缩 noop、F6 自动压缩 `nothing_to_drop`、T03 1M 真请求至 106.6K 后 400k 预算熔断 |
| ⛔ 1 格 | E02 Dots3 HTTP 404（端点侧，公开清单同日查无 ID） |
| ⛔vacuous 5 格 | E03 North 未复测、F1 视觉输入（拒绝敏感选择器授权）、F4 MCP（0 server）、T01/T02 档位回读 |

**回灌级别**：只回灌脱敏 UI dump 文本、思考块裁剪截图与 `deviceLogTail`；不回灌整段 logcat / 凭据 / 未打码账号 / 设备序列号 / 本机路径。
制品：`evidence/w41-matrix-summary.json`（34 键）+ `evidence/raw/w41/`（逐格 JSON、裁剪图、判据输出、日志尾）。

---

## 七、四闸门 + CI

| 闸 | 结果 | 四态 |
|---|---|---|
| G1 编译 | `localbuild.sh` rc=0、APK 产出 | ✅ |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 141 ID） | ✅ |
| G3 selftest | `30/30 PASS（264s）` | ✅ |
| G4 fulltest | `tests=856 failures=0 errors=0 skipped=0`（14 模块） | ✅ |
| CI | Build run `44898126012` + Secret scan run `44898125963` @ `93808be`（四字段复核见验收物步 5） | ✅ |

CI 链接：`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44898126012`。

---

## 八、明确未做（逐条带号）

`W12-02`（四态探针落盘）｜`W9-01`（DEFAULT_ratios / B1 余面 / 单指针）｜`W20-02`（本体再分层）｜
`W20-05`/`W9-11`（1M 双硬限注入）｜`W5-03/05/06`（MCP 外部 https）｜`W9-05`/`W24-03`（journal 二选一）｜
`W24-02`（`retry:` 透传 / 可点重试入口，维持 CLOSED 已知边界）｜显式 LENGTH 端到端用例、F1/F4、T01/T02 回读、F5/F6 applied 分支。
逐条触发信号与缓解动作见验收物 §「OPEN 挂账」。

## 九、OPEN 挂账

逐条（触发信号 / 缓解动作 / 挂账号）见 `docs/analysis/w41-acceptance.md` §「OPEN 挂账」表。
零销账、零新开，不注水；台账 216 行三数不变。

## 十、交给外部审查团队的制品 + 边界声明

**制品**：`docs/analysis/w41-acceptance.md`、本交接、`docs/06-deferred-ledger.md`（四行注记）、
`docs/notes/w29-closeout-checklist.md`（判据 7 解析规则）、`evidence/w41-matrix-summary.json` +
`evidence/raw/w41/`、`.github/workflows/build.yml`（EXPECTED_TESTS）、`93808be` 逐文件 diff。

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / 本机路径 / 「建议下一链做什么」。

**边界**：① 七份报告中有两份对同一波给出相反建议，按用户「全面推进 + 不回开」裁决，未逐条采纳；
② E02/E03 404 与公开清单是同日观察，未查供给方公告；③ 判据 7 负控显示现役规则只查
「本波复核指路」与当波号**并存**、不校验归属当波（注入 W24-02 仍 OK、W26-01 才 RED），本波不新增判据，
如实交外审；④ 本波未新建 schema / checklist / CI job，34 键零增删；⑤ CI Build 以四字段复核为准，
本地四闸门不代替 CI；⑥ 代码主体之后的纯文档提交只落证据与交接，不改变任何代码判据。

> 本波止于交接，交由外部团队审查。
