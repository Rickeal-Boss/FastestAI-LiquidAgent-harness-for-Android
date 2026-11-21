# W42 交接 — 老号注记 / 四态时序重修 / 超窗判序反向必红 / 3b-ext 入闸 + 真机 13 格

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `3637057617f4c22207412c36dda0033866398412`（W41 tip）；台账权威版 = `docs/06-deferred-ledger.md`；
> 本波验收物 = `docs/analysis/w42-acceptance.md`；判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；
> 「未触发」≠「已验证通过」；推断带 caveat、不写成事实。守恒式两数只在派单卡抬头写一次。

---

## 一、本波做了什么（一段话）

先裁决用户提供的 6 份审查分歧，再把账实/隐私订正与代码同车落地，跑四闸门全绿、编译装机、
真机 13 格取证（四态齐现），最后推 CI。核心代码订正三件：① `cachedLabel` 四态生产路径从
「每轮 beginRound 重置标志」改「run 级持久标志 `usageEverObserved`」+ 累计值加 `(cumulative)`
后缀，恢复 HIT/MISS/n/a-fields 在生产链可达；② message-only 错误帧判序重排，显式限流措辞先于
超窗强组合、弱 server/rate 词退后，共享超窗词表补 `prompt`，补三条反向必红；③ Lens 零尺寸守卫
补源码形态协作用例（删调用点必红）。第 0 车把 W41 入仓的 22 行本机路径脱敏重出、把判据 3b-ext
写进 secret-scan workflow、补齐 W24-01/W12-02/W24-02 三行注记与 W40/W41 文档四字段/SHA 订正。

---

## 二、审查分歧裁决（5 条）

| # | 分歧 | 裁决 |
|---|---|---|
| 1 | P0-2 修法：报告 A 方案 1（持久标志）/ 报告 B 方案 1（同）/ 草案（快照或移日志点） | 采**持久标志 `usageEverObserved`**：三份同指、最小且语义直白；`cachedPromptTokens` 累计值加 `(cumulative)` 后缀解决口径错位 |
| 2 | P1-1 判序：限流强词 vs 超窗组合的先后 | 采**显式限流措辞（`rate limit`/`too many tokens`/429/quota/throttl…）最前 → 超窗强组合 → 弱 server/rate**；E5e 的 token×limit 歧义只有显式限流支能保住 |
| 3 | E5j `prompt too long, server rejected` 的窗口词 | 采**共享超窗词表补 `prompt`**（外审样本表优先；该函数是 HTTP 400 与 SSE 帧单一真值源，改一处同步、不生第二把尺） |
| 4 | P1-5 Lens 协作用例 | 采**源码形态判据**（JVM 无 Compose/AGSL 运行时，按 `AgentRunnerTest` 同款读源码钉调用点）；删调用点必红 |
| 5 | 三个"待拍板" + 1M 双硬限 / MCP https / journal | 保守项：W24-01 维持 OPEN 补注记、W24-02 两边界不回开、LENGTH 端到端按余力收口；1M 双硬限/MCP https/journal **不代裁** |

**调度事实**：本波未派并行子席（前波 429 前科按纪律处理）；主理人亲自落代码、闸门、真机与文档，不冒充子席产出。派单前 `git status` 仅历史 `_w3x/` 取证目录 untracked。

---

## 三、账本与守恒

- 台账三数不变、零 `W42-` 行、G20 141 ID 不变；判据 1 `W42 rows = 0`、判据 7 `declared=0`（空销账波锚本波、不回落 W41）。
- 三处行内注记：`W24-01`（已落地 2 项 / 未做 4 项 / 状态 OPEN）、`W12-02`（时序缺陷订正）、`W24-02`（判序边界），整行状态均不动。
- 两数只在派单卡抬头；W40/W41 历史数字作时点记录不回改。

---

## 四、第 0 车（文档账实 / 隐私）

- P0-1：7 份证据日志 22 处本机绝对路径 → 占位符；判据 3b-ext 全树 0 命中 + 注入必红；`secret-scan.yml` 新增 3b-ext 步（无 continue-on-error、覆盖 `evidence/raw/**`）。
- P0-3：`W24-01` 行补 W42 注记（W41 已落地实底容器 + Lens 守卫 + 四态截图；余 4 项未做、不销账）。
- P1-2：补两条 CI run 四字段（`44890089061`/`44898959485`）；W40/W41 四文档补完整 40 位 SHA。
- P1-3/4/6：checklist 补零新增段波锚点规则；W41 验收物制品清单 15→16 文件；交接补波次归属说明。
- **裁决留痕**：草案要求制品补 `K_privacy_no_path` 未采纳（回源 k6_check 证实不扫路径、补 ID 会空转）；真实载体 = 3b-ext 入闸，详见验收物步 2。

---

## 五、第 1 车（代码面）

| 改动 | 文件 | 判据 |
|---|---|---|
| P0-2 持久 usage 标志 + `cached(cumulative)` | `RunState.kt`、`ContextBudget.kt` | 链上必红按 TurnLoop 顺序；注入每轮重置 ⇒ RED |
| P1-1 判序重排 + 词表补 `prompt` | `OpenAiErrorFrame.kt` | E5h/E5i/E5j 反向必红；E5e 保住；E5f 补 truncatedStream |
| P1-5 Lens 守卫协作用例 | `LensCanvasGuardTest.kt` | 删 `Lens.kt` 守卫调用点 ⇒ RED |
| 测试数对账 | `build.yml` | `EXPECTED_TESTS 856→862`（+6） |

`LINE_CAPS` / `TOTAL_LINE_CAP` 未调；G10/G11/G12 全绿。

---

## 六、真机矩阵（13 格，四态齐现）

设备先唤醒 + 三步法装机；冷启 `Status: ok / COLD / TotalTime 1316ms`。密钥留于真机 Keystore。

| 结论 | 格 |
|---|---|
| ✅ 7 格 | E01 Lightning / E02 Nano Omni / E04 SuperVL 1M 逐字回声；F1 视觉输入（W41 vacuous 回补，红矩形+蓝圆逐字回读）；F2 file_write 落盘 W42OK；F3 两技能可见；T01 1M 档位回读 995.9K |
| ⚠️ 3 格 | E03 Bolt（端点通 + HIT 1376 cached，但 4B 未逐字回显）；F5 手动压缩 noop；F6 自动压缩 noop below_trigger |
| ⛔ 2 格 | E05 dots3 / E06 north 均 HTTP 404（端点侧，用户声明"可用"被设备真相证伪） |
| ⛔vacuous 1 格 | F4 MCP server 数 0（G15b 外部 https 独立裁决未解禁） |

**回灌级别**：只回灌脱敏 UI dump 文本、自生成测试图判据结论与 `deviceLogTail`；不回灌整段 logcat / 凭据 / 未打码账号 / 设备序列号 / 本机路径 / 用户私人图片。
**P0-2 真机限定**：`cached=` 走内存 ring 不落文件，真机只间接验证（usage 到账 + UI 四态）；直接判据 = JVM 链上必红，不冒充真机读值。

制品：`evidence/w42-matrix-summary.json`（34 键冻结）+ `evidence/raw/w42/`（逐格 JSON、判据输出、设备日志尾）。

---

## 七、四闸门 + CI

| 闸 | 结果 | 四态 |
|---|---|---|
| G1 编译 | `localbuild.sh` rc=0、APK 70.6MB | ✅ |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 141 ID） | ✅ |
| G3 selftest | `30/30 PASS（384s）` | ✅ |
| G4 fulltest | `tests=862 failures=0 errors=0 skipped=0`（14 模块） | ✅ |
| 判据 1/6/7/3b-ext | 正控 OK + 必红 RED（见验收物步 2/3） | ✅ |
| CI | 待推送后回填 Build / Secret 双 workflow run 链接与四字段 | — |

---

## 八、明确未做（逐条带号）

`W12-02`（探针按四态落盘、`cached=` 值不落文件）｜`W9-01`（DEFAULT_ratios / B1 余面 / 单指针）｜
`W20-02`（本体再分层）｜`W20-05`/`W9-11`（1M 双硬限注入，本波只档位回读）｜
`W5-03/05/06`（MCP 外部 https）｜`W9-05`/`W24-03`（journal 二选一）｜
`W24-02`（`retry:` 透传 / 可点重试入口，维持 CLOSED 已知边界）｜显式 LENGTH 端到端用例、F5/F6 applied 分支、OR dots3/north 端点侧 404。
逐条触发信号与缓解动作见验收物 §「OPEN 挂账」。

## 九、OPEN 挂账

逐条（触发信号 / 缓解动作 / 挂账号）见 `docs/analysis/w42-acceptance.md` §「OPEN 挂账」表。
零销账、零新开，不注水；台账三数不变。

## 十、交给外部审查团队的制品 + 边界声明

**制品**：`docs/analysis/w42-acceptance.md`、本交接、`docs/06-deferred-ledger.md`（三行注记）、
`docs/notes/w29-closeout-checklist.md`（锚点规则 + 3b-ext 入闸）、`evidence/w42-matrix-summary.json` +
`evidence/raw/w42/`、`.github/workflows/build.yml`（EXPECTED_TESTS 856→862）、
`.github/workflows/secret-scan.yml`（3b-ext 步）、代码主体逐文件 diff。

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / 本机路径 / 用户私人图片 /「建议下一链做什么」。

**边界**：① P0-2 真机只间接验证，`cached=` 值不落文件，直接判据为 JVM 链上必红；② E05/E06 404 与用户声明
"可用"矛盾，以设备真相为准、未查供给方公告；③ E03 HIT 来自 EventReducer UI 路径非 `cachedLabel` 日志；
④ 判据 7 只查指路与当波号并存、不校验归属当波（注入 W24-02 仍 OK、W31-02 才 RED），本波不新增判据、如实交审；
⑤ 34 键零增删、未新建 schema/checklist 段/CI job（3b-ext 是既有判据扫描面入闸）；
⑥ 矩阵 13 格由 cells_total 自证、不沿用历史 PASS；⑦ CI 绿以 run 四字段为准，本地四闸门不代替 CI。

> 本波止于交接，交由外部团队审查。
