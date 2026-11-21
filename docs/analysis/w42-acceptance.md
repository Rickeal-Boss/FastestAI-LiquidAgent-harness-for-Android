# W42 验收物 — 老号行内注记 / 四态时序重修 / 超窗判序反向必红 / 判据 3b-ext 入闸 + 真机矩阵回补

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 tip `3637057617f4c22207412c36dda0033866398412`（W41 tip）→ 本波按派单卡 `新开 0 / 销账 0` 走老号行内注记 + 一次代码订正。
> 台账权威版 = `docs/06-deferred-ledger.md`；本交接文档 = `docs/handoff-20261121-wave42.md`；
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 守恒式两数只在派单卡抬头写一次，本验收物不复述；台账零 `W42-` 行、三数不变。
> 回灌级别：只回灌脱敏 UI dump 文本、自生成测试图的判据结论与 `deviceLogTail`；不回灌整段 logcat / 凭据 / 未打码账号 / 设备序列号 / 本机路径 / 用户私人图片。

---

## 步 1 · 读记忆 / 交接 / 挂账 + 裁决 6 份审查分歧 ✅

- 读本仓 `harness-v2/docs/handoff-20261121-wave41.md` + `docs/06-deferred-ledger.md` + 用户提供的 6 份外审（W42 老号草案 / 派单卡 / W41 深度审查与 W42 排期裁决 / W41 挂账分析及 W42 路线 / W36–W41 六波增量审查 / W41 四态 usage 时序缺陷）。未读旧仓 `Android-Agent/docs/`。
- 裁决（详见交接 §二）：① P0-2 采持久标志 `usageEverObserved`（三份报告同指）；② P1-1 采「显式限流措辞 → 超窗强组合 → 弱词」判序，共享词表补 `prompt`；③ P1-5 采源码形态协作用例（JVM 无 Compose 环境）；④ 三个"待拍板"按保守项（W24-01 维持 OPEN 补注记 / W24-02 两边界不回开 / LENGTH 端到端用例按余力收口）；⑤ 1M 双硬限、MCP https、journal 二选一不代裁。
- 派单核实：本波未派并行子席（前波 429 前科按纪律处理），工作树动手前仅历史 `_w3x/` 取证目录 untracked。

## 步 2 · 账实与隐私订正（第 0 车硬前置）✅

| 项 | 结果 | 证据路径 |
|---|---|---|
| P0-1 脱敏重出 | ✅ 7 份日志 22 处本机绝对路径换 `<repo>/<jdk>/<sdk>/<gradle-home>/<workspace>`；判据 3b-ext 全树 0 命中 + 注入必红会响 | `evidence/raw/w41/g1_build.txt` 等 7 份、`evidence/raw/w42/privacy_ok.txt`、`privacy_red.txt` |
| P0-1 3b-ext 入闸 | ✅ `secret-scan.yml` 新增 3b-ext 步（无 continue-on-error；覆盖 docs + evidence/**，含 `evidence/raw/**`）；正常态 OK + 注入必红 | `.github/workflows/secret-scan.yml`、上述证据 |
| P0-3 W24-01 账实 | ✅ 行内补 W42 注记：已落地 2 项 / 未做 4 项 / 状态维持 `OPEN`；pipe 数 7 自检 | `docs/06-deferred-ledger.md` W24-01 行 |
| W12-02 / W24-02 注记 | ✅ 分别补时序缺陷订正与判序边界，状态不变 | 同上两行 |
| P1-2 CI 补漏 | ✅ `c4fc2cf` → Secret `44890089061`、`3637057` → Secret `44898959485`（公开 API 实取，均 `completed/success`） | `docs/analysis/w41-acceptance.md` 步 5、`docs/handoff-20261121-wave41.md` §七 |
| P1-3 锚点规则 | ✅ checklist W41 边界段补「零新增段/空销账波 ⇒ SKIP-empty-wave，不回落上一波」 | `docs/notes/w29-closeout-checklist.md` W41 边界说明段 |
| P1-4/6 计数与归属 | ✅ 制品清单 15→16 文件（15 改 + 1 新）；波次归属说明入交接 | `docs/analysis/w41-acceptance.md` 制品清单第 7 条、`docs/handoff-20261121-wave41.md` §三 |
| tip SHA 入仓 | ✅ W41 验收物/交接 + W40 两份文档补完整 40 位 SHA | 同上四文档抬头 |

**裁决（如实交外审，不掩盖）**：草案 P0-1 修补③要求「制品 `mutation_cases` 补 `K_privacy_no_path`」——回源 `_w34/k6_check.py` 证实它只扫 token 形状（`sk-`/`github_pat_`/`nvapi-`），**不扫本机路径**；直接补断言 ID 会触发 `assertion/mutation count mismatch`（6/6 对账）且判据不响，属本仓判死的空转形态。裁决：`K_privacy_no_path` 的真实载体 = 已入 `secret-scan.yml` 的 3b-ext 步（实跑正常态 OK + 注入必红、覆盖 `evidence/raw/**`）；w41 历史制品保持 34 键冻结不回填。

## 步 3 · 代码订正（第 1 车）+ 四闸门 ✅

| 改动 | 文件 | 判据 |
|---|---|---|
| P0-2 `usageEverObserved` 持久标志替 `usageObservedThisRound`；`cached(cumulative)` 口径后缀 | `RunState.kt`、`ContextBudget.kt` | 链上必红 `cachedLabelFollowsRealUsagePerRound` 按 TurnLoop 顺序断言；注入「改回每轮重置」⇒ RED；另补估算不置位 / n/a-fields 两条 |
| P1-1 message-only 判序重排（显式限流 → 超窗强组合 → 弱词），共享超窗词表补 `prompt` | `OpenAiErrorFrame.kt` | E5h/E5i/E5j 三条反向必红（超窗含 server/rate 仍 CONTEXT_OVERFLOW）；E5f 补 `truncatedStream` 断言；E5e 保住（限流不误判超窗） |
| P1-5 Lens 守卫协作用例（源码形态钉调用点在 shader 之前） | `LensCanvasGuardTest.kt` | 删 `Lens.kt` 守卫调用点 ⇒ RED；纯函数 2 条仍保留 |
| 测试数对账 | `build.yml` | `EXPECTED_TESTS 856→862`（+6：context 2 / engine 3 / design 1），CI 双向 Reconcile |

- G1 编译：`localbuild.sh` rc=0、APK 产出 70.6MB ✅
- G2 arch-guard：`OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 141 ID 不变）✅
- G3 selftest：`30/30 PASS（384s）` ✅
- G4 fulltest：`tests=862 failures=0 errors=0 skipped=0`（14 模块）✅
- 判据 6：`OK feature/evidence invariants`（13 格由 cells_total 自证）+ 注入 `BOGUS_STATE` ⇒ `RED state domain` ✅
- 判据 1/7：W42 零新增段波 ⇒ `W42 rows = 0` / `OK closed rows carry current-wave pointers (W42, declared=0)`；必红 `RED closed without current-wave pointer: W31-02` ✅
- 判据 3b-ext：`OK no device-id/path/pat-meta` + 注入必红 `RED sensitive leak hit` ✅

## 步 4 · 真机矩阵（13 格，四态齐现，制品入仓）✅

设备先唤醒（`SCREEN_STATE_ON` / `Awake` / 亮度 151.1 非黑）+ 三步法装机；冷启 `Status: ok / COLD / TotalTime 1316ms`。密钥留于真机 Keystore。

| 格 | 四态 | 判据与证据路径 |
|---|---|---|
| E01 NV Lightning 回声 | ✅ | `LIGHTOK` 逐字；`1.8k in / 72 out (0 cached)`；`evidence/raw/w42/e01_lightning.json` |
| E02 NV Nano Omni 回声 | ✅ | `NANOOK` 逐字 + 思考块；`evidence/raw/w42/e02_nano_omni.json` |
| E03 NV Bolt | ⚠️ | 端点可用且 HIT（`1376 cached, 98.6%`），4B 模型未逐字回显（答 "This is the final answer"）；`e03_bolt.json` |
| E04 NV SuperVL 1M 回声 | ✅ | `W42SEQOK` 逐字；档位回读 `995.9K`；`e04_supervl_1m.json` |
| E05 OR dots3 | ⛔ | HTTP 404（用户声明可用被设备真相证伪，端点侧）；`e05_dots3.json` |
| E06 OR north | ⛔ | HTTP 404（同上，端点侧）；`e06_north.json` |
| F1 视觉输入 | ✅ | SuperVL + 自生成 240×140 图（红矩形+蓝圆）→ 逐字回读 "red rectangle ... blue circle"；W41 vacuous 本波回补；`f1_vision.json` |
| F2 workspace 写 | ✅ | `file_write` 审批 → `probe_w42.txt` 落盘 `W42OK` 设备侧核验；`f2_workspace.json` |
| F3 skill @ 菜单 | ✅ | 设置>技能两技能可见（notes-organizer / pdf-toolkit）；不冒充 `skill_load` 工具调用；`f3_skill.json` |
| F4 MCP | ⛔vacuous | server 数 0（"还没有 MCP 服务器"）；G15b 外部 https 独立裁决未解禁；`f4_mcp.json` |
| F5 手动压缩 | ⚠️ | 确认框走完 → noop（"没有可压缩的内容"）；applied 未行使；`f5_manual_compact.json` |
| F6 自动压缩 | ⚠️ | `deviceLogTail.txt` 末 40 行全 `auto_compact=noop reason=below_trigger`；applied 未行使；`f6_auto_compact.json` |
| T01 1M 档位回读 | ✅ | 状态行 `上下文 x / 995.9K`；真压受双硬限阻断，不写真压 PASS；`t01_1m_tier.json` |

**P0-2 真机限定**：`cached=` 标签走 `AgentLogStore`（内存 ring，诊断页只读展示，不落 `agent-0.log`），真机只能间接验证（真实 usage 到账 + UI 状态行四态正常，如 E03 的 HIT 1376）；**直接判据 = JVM 链上必红用例**（步 3 已绿），不冒充真机读了 `cached=` 值。日志尾只含 WARN/ERROR 级的 auto_compact 行。

制品：`evidence/w42-matrix-summary.json`（34 键冻结）+ `evidence/raw/w42/`（13 逐格 JSON、判据输出、设备日志尾）。

## 步 5 · 推送 + CI ✅

- 提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`。
- 推送分支 `main`（`git push --dry-run` 真判据通过；一次性凭据只走 URL inline、推后清 remote；凭据轮换属用户侧）。
- CI（公开 API 四字段复核，tip `4ba99c055c1a6f9564cad6af25f59a832cc37953`）：
  Build run `44914544403`（`head_sha=4ba99c055c…`、`status=completed` / `conclusion=success`）✅，链接
  `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44914544403`；
  Secret scan run `44914544409`（`head_sha=4ba99c055c…`、`status=completed` / `conclusion=success`）✅，链接
  `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44914544409`。
  本次为**单次推送推两个提交**（`3637057..4ba99c0`），CI 只按 tip 触发一轮；feat 提交 `8fcd30b` 无独立 run，符合单 push 多 commit 口径。
  > Secret scan 的 jobs API 逐 step 复核：`gitleaks` / `trufflehog` / **3b-ext（路径 / 设备标识 / PAT 元信息脱敏；覆盖 evidence/raw/\*\*）** 三步均 `completed/success` —— P0-1 入闸得到 CI 级验证，非本地自证。两 run 均四字段实取复核，本地四闸门不代替 CI。

---

## 明确未做（逐条带挂账号前缀）

1. `W12-02` 出口判据未全行使 — 设备侧缓存探针按四态落盘未做；本波 P0-2 真机只间接验证、`cached=` 值不落文件。
2. `W9-01` 余面 — `DEFAULT_*_RATIO` 搬 `AgentPolicy`、B1 其余三条、`alreadyCompressed` 单指针未改。
3. `W20-02` 本体再分层未做；`feature-chat` 行数余量收窄（arch-guard G10 仍绿）。
4. `W20-05` / `W9-11` 1M 真压双硬限注入未做；本波只做档位回读 `995.9K`。
5. `W5-03/05/06` MCP 外部 https（G15b）未做；F4 server 数 0，明文测试不构成门禁通过证据。
6. `W9-05` / `W24-03` journal 二选一裁决未拍板，不代裁。
7. `W24-02` 剩余两条边界（`retry:` 纯帧透传、可点重试入口）维持 CLOSED 行已知边界，不回开。
8. 显式 `finishReason=LENGTH` ⇒ `TRUNCATED` 端到端用例未落位；F5/F6 `applied` 分支未造出安全截断点。
9. OR dots3 / north 本波仍 HTTP 404（端点侧）；用户声明"可用"未被设备真相支持，交外审知悉。

## OPEN 挂账（触发信号 / 缓解动作 / 挂账号）

| 挂账号 | 触发信号 | 缓解动作 | 状态 |
|---|---|---|---|
| `W12-02` | 外部按四态出口判据回查探针落盘；`cached=` 日志四态被误读为恒 n/a-usage | P0-2 已改持久标志 + 链上必红；仍需设备侧探针按四态落盘 | OPEN（注记已补） |
| `W9-01` | 按旧顺序读台账或改压缩比例 | 命题②不重做；单指针 / DEFAULT_ratios / B1 余面待代码 | OPEN（注记已补） |
| `W20-02` | 下一次 `.kt` 增量触 G10 | 本体再分层与层级归属待裁 | OPEN（注记已补） |
| `W20-05` / `W9-11` | 1M 满窗真压验收 | 双硬限注入方案保持独立裁决；本波档位回读留档 | OPEN（沿用） |
| `W5-03/05/06` | 外部 https MCP 验收 | G15b 明文门禁 + 端点可用性，单独裁决 | OPEN（沿用） |
| `W9-05` / `W24-03` | 用户给 journal 二选一裁决 | 用户拍板后统一代数口径，不代裁 | OPEN（沿用） |
| `W24-01` | 展开态仍现黑面板 / 零尺寸进 AGSL 复现 | W41 已落地实底+Lens 守卫+四态截图；余 4 项缓解动作维持 OPEN | OPEN（注记已补） |
| `W24-02` 已知边界 | 拥堵断流 / SSE `retry:` 帧复现 | 两条体验边界维持 CLOSED 行内注记，不回开 | CLOSED 行内注记 |

## 交给外部审查团队的制品清单

1. `docs/analysis/w42-acceptance.md`（本文件）
2. `docs/handoff-20261121-wave42.md`（交接文档）
3. `docs/06-deferred-ledger.md`（三行 W42 行内注记）
4. `docs/notes/w29-closeout-checklist.md`（零新增段波锚点规则 + 3b-ext 入闸说明）
5. `evidence/w42-matrix-summary.json`（34 键冻结）+ `evidence/raw/w42/`（逐格 JSON、判据输出、设备日志尾）
6. `.github/workflows/build.yml`（EXPECTED_TESTS 856→862）、`.github/workflows/secret-scan.yml`（3b-ext 步）
7. 代码主体逐文件 diff（P0-2 时序 + P1-1 判序 + P1-5 协作用例 + 第 0 车文档订正）

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / 本机路径 / 用户私人图片 / 「建议下一链做什么」。

## 边界声明

- ① P0-2 真机只间接验证（usage 到账 + UI 四态），`cached=` 值不落文件；直接判据为 JVM 链上必红，不冒充真机读了该文件值。
- ② E05/E06 的 404 与用户声明"可用"矛盾，以设备真相为准；未查供给方公告，不归因 harness。
- ③ E03 Bolt HIT 态来自 EventReducer 的 UI 路径，不是 `cachedLabel` 日志路径；两者口径独立。
- ④ 判据 7 负控显示现役规则只查「本波复核指路」与当波号**并存**、不校验归属当波（注入 W24-02 仍 OK、W31-02 才 RED）；本波不新增判据，如实交外审。
- ⑤ 本波未新建 schema / checklist 段 / CI job；34 键零增删；判据 3b-ext 是既有判据的扫描面入闸，非新判据。
- ⑥ 守恒式两数只在派单卡抬头；台账零新增行、三数不变；W40/W41 历史数字作时点记录不回改。
- ⑦ 真机矩阵格数 13（非 W41 的 14），由 `cells_total` 自证；格数变化源于本波实测面覆盖不同（新增 NV Nano/Bolt 两格、无 T01/T02 分档回读），不沿用历史 PASS。

> 本波止于交接，交由外部团队审查。
