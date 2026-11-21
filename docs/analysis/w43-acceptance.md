# W43 验收物 — 判序反噬双修复 / 四态口径收口 / 3b-ext 全树加固 + 真机多模型回归

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 tip `2e69dae333f6ed5ec5bd62600f75a2014fe50c1e`（W42 tip）→ 本波 tip `fa06c0c0858bc7215db0fba9d6ab4554da4725db`。
> 台账权威版 = `docs/06-deferred-ledger.md`；交接文档 = `docs/handoff-20261122-wave43.md`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 守恒式两数只在派单卡抬头写一次；本波零新增 `W43-` 行、零状态字段改动、三数不变、G20 141 ID 不变。
> 回灌级别：只回灌脱敏判据文本与 `deviceLogTail` 结论；不回灌整段 logcat / 凭据 / 未打码账号 / 设备序列号 / 本机路径 / 用户私人图片。

---

## 步 1 · 读记忆 / 交接 / 挂账 + 裁决 5 份审查分歧 ✅

- 读本仓 `harness-v2/docs/handoff-20261121-wave42.md` + `docs/06-deferred-ledger.md` + 用户提供的 5 份 W42 审查/草案/派单卡。未读旧仓 `Android-Agent/docs/`。工作树动手前 `git status` 仅历史 `_wXX/` 取证目录 untracked，无 stash、无并发写入方。
- 三份报告在**代码修复严重性**上分歧：报告 A（深度审查与判序回归）把判序反噬列为 P2「残留风险、建议后续」；报告 C（排期裁决）与老号草案列为 P0「本波必修」。

**裁决（回源现役树实测，不采信时点差）**：

| # | 分歧 | 裁决与依据 |
|---|---|---|
| 1 | P0-2 判序反噬定级 | 采 **P0 本波必修**。Python 逐字复刻现役 `OpenAiErrorFrame` 判序：`context length exceeded: too many tokens` → RATE_LIMIT（应 CONTEXT_OVERFLOW）、HTTP400 `prompt limit reached` → CONTEXT_OVERFLOW（应 PROTOCOL），两条 RED；E5e/h/i/j/E6 全保住。真 JUnit 新增两条必红在**未修代码**上实测 `2 failed`（`evidence/raw/w43/red_p02_mutants.txt`）。非时点差、非推断。 |
| 2 | 反噬 a 修法 | `too many tokens` 从无条件强词收为**限流语境内强词**（`rate limit` 连写 / quota / throttl / 429 / slow down / too many requests）；**不含裸 `rate`**（否则 E5i `request rate too large: maximum context length exceeded` 重引 W42 已修反向回归，复刻实测过一次 RED 后修正）。 |
| 3 | 反噬 b 修法 | `prompt` 窗口词加**长度语义护栏**：context/token 仍是窗口词，prompt 仅在搭配 `too long`/`maximum`/`exceed`/`length`（明确**不含**裸 limit）时算。HTTP400 与 SSE 帧共用单一真值源 `looksLikeContextOverflowMessage`，不生第二把尺子。 |
| 4 | P1-3 四态口径 | 采**统一 run 级累计口径 + 订正 KDoc**，不采草案的「改 per-request 快照」。理由：W42 刚建立累计口径且值带 `(cumulative)` 明示，改快照让四态语义反复横跳；草案「先命中后缺字段应判 n/a-fields」建立在快照口径上，与累计口径冲突。新增用例钉住累计口径。 |
| 5 | 3b-ext 加固 | 采「泛化 `<盘符>\<用户名目录>\`/`<盘符>/<用户名目录>/`/`/<用户名目录>/`/`/<家目录>/` + 全树无豁免 + 显式判 grep rc」。报告 A 点名的 `g3_selftest.txt:3` 真残留实测存在（非时点差），CI 对该形态空过成立。两处历史注记的豁免字面量一并脱敏，纳入全树无豁免。 |
| 6 | 三件「待拍板」 | 按保守项承接、不代裁：W24-01 维持 OPEN（协作用例是文本形态、判别力有限，不写「已全行使」）；W24-02 两边界维持 CLOSED 行内注记；LENGTH 端到端按余力未落位。1M 双硬限注入 / MCP 外部 https / journal 二选一仍独立裁决、不解禁。 |

- 调度事实：本波未派并行子席（前波 429 前科按纪律处理），主理人亲自落代码、闸门、真机与文档，不冒充子席产出。

## 步 2 · 第 0 车：证据回补 + 3b-ext 加固 + 收尾订正 ✅

| 项 | 结果 | 证据路径 |
|---|---|---|
| P0-1 证据回补 | ✅ W42 缺 G1–G4 / RED / GREEN 原始产物；本波实跑并按 W41 脱敏口径入仓 7 件（g1–g4 + 2 必红 RED + 2 GREEN） | `evidence/raw/w43/g1_build.txt`、`g2_archguard.txt`、`g3_selftest.txt`、`g4_fulltest.txt`、`red_p02_mutants.txt`、`green_p02_engine.txt`、`green_p13_contextbudget.txt` |
| P1-2 3b-ext 加固 | ✅ 从 `docs evidence` 两目录扩到 `git grep` 全树 tracked（无豁免）；补四类用户目录形态；显式判 rc（0 命中红 / 1 无命中绿 / ≥2 故障红，不吞）；分段拼装避免 workflow 自命中 | `.github/workflows/secret-scan.yml`；正常态 rc=1 + 八类注入 rc=0 本环境实测；CI step #6 `completed/success` |
| P1-2 脱敏闭环 | ✅ `g3_selftest.txt:3` C 盘路径、w21 验收物与 W36-01 台账两处历史字面量全部脱敏；全树 tracked 六类形态 0 命中 | `evidence/raw/w41/g3_selftest.txt`、`docs/analysis/w21-acceptance.md`、`docs/06-deferred-ledger.md` |
| P1-4 注释链 | ✅ `build.yml` 补 W42 的 `+6`（856→862）注释行，此前断在 W41 | `.github/workflows/build.yml` |
| 收尾订正留痕 | ✅ W43 抬头用真 tip `fa06c0c…`；W42 历史数字作时点记录不回改 | 本验收物抬头、交接 |

**裁决留痕（如实交外审）**：草案 P0-1 要求把 W42 历史缺口的闸门/RED 日志「按 W41 规格补入」——W42 当时并未生成/入仓这些原始输出，**无法事后补造**。本波裁决：补本波实跑产物为可复核基线（已入仓），W42/W40 历史缺口作时点记录、不伪造历史日志。3b-ext 加固属既有判据扫描面完善，非新增判据编号（判据冻结）。

## 步 3 · 第 1 车：代码修复 + 四闸门 ✅

| 改动 | 文件 | 判据 / 证据 |
|---|---|---|
| P0-2 反噬 a：too many tokens 收为限流语境内强词 | `OpenAiErrorFrame.kt` | E5k 必红（未修 RED / 修复 GREEN）；E5e 保住；`red_p02_mutants.txt` + `green_p02_engine.txt` |
| P0-2 反噬 b：prompt 长度语义护栏 | `OpenAiErrorFrame.kt`（单一真值源，HTTP400 与 SSE 同步） | HTTP400 `prompt limit reached` 必红（未修 RED / 修复 GREEN）；E5j 保住；同上证据 |
| P1-3 四态口径收口：统一 run 级累计 + KDoc 订正 | `RunState.kt`、`Generation.kt`、`ContextBudgetTest.kt` | 新增累计口径钉用例；原 W42 用例注释「上一条请求」失实措辞订正；`green_p13_contextbudget.txt` |
| 测试数对账 | `build.yml` | `EXPECTED_TESTS 862→865`（+3：engine 2 / core-agent 1），同提交对账 |

**判据先行实证**：两条新必红先加测试、在**未修代码**上跑 `2 tests completed, 2 failed`（行号 334/107），再改代码跑 `BUILD SUCCESSFUL`。全程 10 个复刻样本（E5e/h/i/j/k、E5c、E6、两条 HTTP400、400-overflow、b2）在修后全绿。

| 闸 | 结果 | 四态 | 证据 |
|---|---|---|---|
| G1 编译 | `localbuild.sh :app:assembleDebug` rc=0、APK 68M | ✅ | `evidence/raw/w43/g1_build.txt` |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 141 ID 不变，空转项 G19） | ✅ | `evidence/raw/w43/g2_archguard.txt` |
| G3 selftest | `30/30 PASS（296s）` | ✅ | `evidence/raw/w43/g3_selftest.txt` |
| G4 fulltest | `tests=865 failures=0 errors=0 skipped=0`（14 模块） | ✅ | `evidence/raw/w43/g4_fulltest.txt` |
| 3b-ext 正控 + 必红 | 正常态 rc=1、注入八类 rc=0、workflow 自命中 0 | ✅ | 本环境实跑 + CI step #6 success |
| 行数闸门 | OpenAiErrorFrame 209/400、RunState 383/400（+3 KDoc）、Generation 150/300；`LINE_CAPS`/`TOTAL_LINE_CAP` 未调 | ✅ | G10/G11/G12 全绿（G2 证据） |

## 步 4 · 真机回归（多模型 / 多上下文 / 功能入口）⚠️

设备先唤醒（`SCREEN_STATE_ON`、亮度 138 非黑）+ `adb install -r -t` 装机（本 adb 版本无 `install-create`，设备已唤醒故单步法不挂起）；冷启 `Status: ok / COLD / TotalTime 1319ms`。密钥留于真机 Keystore，未回灌。

| 格 | 四态 | 判据与证据 |
|---|---|---|
| E-SuperVL（1M） | ✅ | `NV SuperVL 120B 1M` 端点发 `W43Ok` → 正常回复 + 思考过程；`1.8k in / 168 out (0 cached, 0.0%)`、`上下文 1.9K / 995.9K`（1M 档位回读）；脱敏 UI dump 文本结论 |
| E-Lightning | ⚠️ | `NV Lightning 30B` 端点通、`1.8k in / 62 out`、`上下文 1.9K / 127K`；模型答 "Understood. How can I assist you today?" 未逐字回显 LIGHTOK（模型行为波动，非端点故障） |
| E-Dots3（OR） | ⛔ | 用户声明 `dots-3-note-preview:free` 可用（512k，工具+视觉双通）；真机发 `DOTSOK` 与「测试连接」均 `HTTP 404`（URL 与 model id 见端点编辑页确认，上下文 524288）。设备真相证伪用户声明，端点侧、非 harness |
| E-North（OR） | ⛔ | 用户声明 `north-mini-code:free` 可用；真机发 `NSOK` → `HTTP 404`。同上，端点侧 |
| 多模型切换 + 缓存失效提示 | ✅ | 每次「设为当前」弹确认框「切换端点或窗口会让提示前缀缓存从头重建…诊断 unverified_window」；切换后端点名/上下文随之更新 |
| 多模态输入（图片） | ⚠️ | 自生成 240×140 测试图（红矩形+蓝圆）push 到 `/sdcard/Download/` 成功（761 bytes），SuperVL「添加图片」选择器打开于「下载」目录；测试图未被媒体索引收录 → 未做到逐字回读判据。本波无视觉代码改动，W42 F1 已 PASS，按判据冻结不重复取证 |
| 上下文/档位 | ✅ | 设置页档位说明可见；1M 档位回读 `995.9K`（真压受双硬限阻断，不写真压 PASS） |
| 功能入口存在性 | ✅ | 设置页见「技能 / MCP 服务器 / Agent 工具 / 沙箱文件 / 长期记忆 / 端点管理 / 上下文档位」；本波无相关代码改动，W42 F2/F3/F4/F5/F6 结论沿用时点记录、不重复取证 |
| 手动/自动压缩 | ⚠️ | 短对话期间 `deviceLogTail` 无 `auto_compact` 触发行（below_trigger）；未造出安全截断点，applied 分支未行使（沿用 W42 F5/F6 口径） |

**回灌级别**：只写脱敏判据结论与 deviceLogTail 关键字结论；不回灌 uiautomator 噪声（含用户应用列表/私人目录名）、整段 logcat、凭据、未打码账号、设备序列号、本机路径、用户私人图片。题面一律英文 ASCII（`input text` 末字符偶被吞，每次发送前 dump 复核）。

## 步 5 · 推送 + CI ✅

- 提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；单一提交 `fa06c0c0858bc7215db0fba9d6ab4554da4725db`（18 文件，代码+证据同车）；`git push --dry-run` 真判据通过；PAT 仅 URL inline 一次性使用、remote 配置干净无残留。
- CI（按 head_sha 完整 40 位实取，`2e69dae..fa06c0c main` 单次推送单提交触发一轮、两 workflow）：
  - Build run `44922539579`（`head_sha=fa06c0c0858bc7215db0fba9d6ab4554da4725db`、`status=completed` / `conclusion=success`、`updated_at=2026-11-21T16:06:59Z`）✅，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44922539579`。jobs API 逐 job/step 全绿：Unit tests（含 `Reconcile EXPECTED_TESTS` success，865 对账通过 + `Run unit tests (aggregate + real XML counts)` success）、Arch guard（26 条判据 + mutation selftest success）、Assemble Debug（JDK 21）、Lint（baseline gate），失败诊断 step 均 skipped。
  - Secret scan run `44922539697`（同 head_sha、`status=completed` / `conclusion=success`）✅，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44922539697`；jobs API 逐 step 确认 gitleaks / trufflehog / **3b-ext（全树无豁免，step #6）** 均 `completed/success`。
  - 本地四闸门不代替 CI；CI 绿以两 run 四字段为准。WebFetch 对 run 级端点有 15 分钟缓存，终态以带查询参数的新请求（`conclusion=success`、`updated_at=16:06:59Z`）为准。

---

## 明确未做（逐条带挂账号前缀）

1. `W12-02` 出口判据未全行使 — 设备侧缓存探针按四态落盘未做；本波未重跑真机 `cached=` 间接验证（W42 已留档），直接判据为 JVM 链上必红（本波保住）。
2. `W9-01` 余面 — `DEFAULT_*_RATIO` 搬 `AgentPolicy`、B1 其余三条、`alreadyCompressed` 单指针未改。
3. `W20-02` 本体再分层未做；`feature-chat` 行数水位未动（本波未改该模块）。
4. `W20-05` / `W9-11` 1M 真压双硬限注入（PreStepBudget 400k + RoundHeadGate）未做；本波只做档位回读 `995.9K`。
5. `W5-03/05/06` MCP 外部 https（G15b）未做；F4 server 数 0，明文测试不构成门禁通过证据。
6. `W9-05` / `W24-03` journal 二选一裁决未拍板，不代裁。
7. `W24-02` 剩余两条边界（`retry:` 纯帧透传、可点重试入口）维持 CLOSED 行内注记，不回开。
8. 显式 `finishReason=LENGTH` ⇒ `TRUNCATED` 端到端用例未落位；F5/F6 `applied` 分支未造出安全截断点。
9. OR dots3 / north 真机复测仍 HTTP 404（端点侧）；用户声明「可用」被设备真相证伪，未查供给方公告。
10. 多模态逐字回读本波未行使（测试图未被媒体索引收录）；W42 F1 已 PASS，本波无视觉代码改动、按判据冻结不重复取证。
11. 凭据轮换：本波 PAT 以 URL inline 推送一次即视为暴露，轮换属用户侧动作，仓内无法闭环。
12. W42 / W40 历史缺口（四闸门与 RED 日志当时未入仓）作时点记录，本波不伪造历史日志；以本波入仓产物为新基线。

## OPEN 挂账（触发信号 / 缓解动作 / 挂账号）

| 挂账号 | 触发信号 | 缓解动作 | 状态 |
|---|---|---|---|
| `W12-02` | 外部按四态出口判据回查探针落盘；`cached=` 日志被误读为恒 n/a-usage | 本波 P1-3 统一 run 级累计口径 + KDoc 订正 + 累计口径必红；仍需设备侧探针按四态落盘 | OPEN（注记沿用） |
| `W9-01` | 按旧顺序读台账或改压缩比例 | 命题②不重做；单指针 / DEFAULT_ratios / B1 余面待代码 | OPEN（沿用） |
| `W20-02` | 下一次 `.kt` 增量触 G10 | 本体再分层与层级归属待裁 | OPEN（沿用） |
| `W20-05` / `W9-11` | 1M 满窗真压验收 | 双硬限注入方案保持独立裁决；本波档位回读留档 | OPEN（沿用） |
| `W5-03/05/06` | 外部 https MCP 验收 | G15b 明文门禁 + 端点可用性，单独裁决 | OPEN（沿用） |
| `W9-05` / `W24-03` | 用户给 journal 二选一裁决 | 用户拍板后统一代数口径，不代裁 | OPEN（沿用） |
| `W24-01` | 展开态仍现黑面板 / 零尺寸进 AGSL 复现 | W41 已落地实底 + Lens 守卫 + 四态截图；协作用例是文本形态、判别力有限，余项维持 OPEN | OPEN（沿用） |
| `W24-02` 已知边界 | 拥堵断流 / SSE `retry:` 帧复现 | 两条体验边界维持 CLOSED 行内注记，不回开 | CLOSED 行内注记 |

## 交给外部审查团队的制品清单

1. `docs/analysis/w43-acceptance.md`（本文件）
2. `docs/handoff-20261122-wave43.md`（交接文档）
3. `docs/06-deferred-ledger.md`（W36-01 行脱敏重出，零状态改动）
4. `.github/workflows/secret-scan.yml`（3b-ext 全树加固）、`.github/workflows/build.yml`（EXPECTED_TESTS 862→865 + W42 注释补录）
5. `evidence/raw/w43/`（g1–g4 四闸门 + 2 必红 RED + 2 GREEN，共 7 件脱敏原始日志）
6. 代码主体逐文件 diff：`OpenAiErrorFrame.kt`（判序双修复 + 单一真值源护栏）、`RunState.kt`/`Generation.kt`（四态口径 KDoc）、`ContextBudgetTest.kt`/`RemoteLlmEngineStreamFailureTest.kt`/`RemoteLlmEngineContextLimitTest.kt`（3 条新增判据）、`evidence/raw/w41/g3_selftest.txt` 与 `docs/analysis/w21-acceptance.md`（脱敏重出）

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / 本机路径 / 用户私人图片 / uiautomator 噪声 / 「建议下一链做什么」。

## 边界声明

- ① P0-2 两条新必红在未修代码上实测 RED（2 failed），修复后定向 GREEN；这是「写 X 未发生前先自证判据会响」的直接证据，不是文档自述。
- ② 反噬 a 修法刻意不含裸 `rate`，否则 E5i 重引 W42 已修反向回归（本波复刻实测过一次 RED 后修正）；外审若按「rate 也该算限流语境」复核会误判，此处以真样本为准。
- ③ P1-3 采 run 级累计口径（非草案的 per-request 快照）；「先命中后缺字段显示累计 HIT」是设计后果、由新增用例钉住，不是缺陷。
- ④ 3b-ext 从两目录扩到全树 tracked；本地 `git add -f` 的新文件因 git grep 索引缓存不命中是本地环境坑，CI checkout 全 tracked 文件不受影响（已用真实 tracked 文件注入验证 rc=0）。
- ⑤ 判据冻结：34 键矩阵零增删、无新 schema / checklist 段 / CI job；3b-ext 是既有判据扫描面加固，非新判据；新增 3 条是既有测试类里的行为判据、同提交对账 `EXPECTED_TESTS`。
- ⑥ 守恒式两数只在派单卡抬头；台账零新增行、状态字段零改动、三数不变、G20 141 ID 不变；W42 及更早历史数字作时点记录不回改。
- ⑦ CI 绿以 run 四字段为准，本地四闸门不代替 CI；Build 与 Secret scan 两 run 四字段及逐 step 复核均经公开 API 实取、均 `completed/success`。
- ⑧ OR 两端点 404 与用户声明「可用」矛盾，以设备真相为准；未查供给方公告，不归因 harness。

> 本波止于交接，交由外部团队审查。
