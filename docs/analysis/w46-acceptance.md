# W46 验收物 — 判序结构性收口 / 白名单正文约束 / 门禁本地闸门 / 真机多模型回归

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `e32a7043b67742a11d54045dd6f320c39f4f3cfb`（W45 tip）；台账权威版 = `docs/06-deferred-ledger.md`；
> 交接 = `docs/handoff-20261122-wave46.md`；派单卡 = `docs/w46-dispatch.md`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 回灌级别：只回灌脱敏截图判据与 deviceLogTail 探针行；不回灌整段 logcat / 凭据 / 端点标识 / 序列号 / 本机路径 / 私人图片。

---

## 步 1 · 读记忆 / 交接 / 挂账 + 七份审查分歧裁决

- 读本仓 `docs/handoff-20261122-wave45.md`、`docs/06-deferred-ledger.md` 五行老号（`W24-02`/`W12-02`/`W36-01`/`W24-01`/`W9-01`）、用户提供的七份外审；未读旧仓 `Android-Agent/docs/`。三个 skill（harness-subagent-write-isolation / harness-android-dev / android-verify-loop）已加载。动手前 `git status` 干净。
- 七份报告核心分歧：台账 4 个虚构符号（P0-1）｜证据链退为摘要卡与 tip run 漏记（P0-2 / P1-4）｜判序第五轮打地鼠（P1-1）｜3b-ext 第三路 CI vacuous + 无本地入口（P1-2）｜白名单正文动态自由文本洞（P1-3）｜W9-01 让位与 W24-01/W24-02 待拍板。

**裁决（方向，带依据）**：

| # | 分歧 | 裁决与依据 |
|---|---|---|
| 1 | 台账虚构符号 P0-1 | 采 **真符号订正 + 机械自检**：4 个虚构符号逐一 grep 定位真实制品（`AgentRunnerTest.lengthFinishReasonProducesTruncatedReportEndToEnd`、`DiagnosticLogSinkTest` 三方法）；虚构名去反引号保留历史引文；`scripts/w46-ledger-symbol-check.sh` 注入虚构类名 ⇒ RED。 |
| 2 | 证据形态 P0-2 / 收尾 P1-4 | 采 **原始脱敏输出重出**：G1 363 行 / G2 35 行 / G3 39 行 / G4 396 行（14 模块 + TEST-XML mtime），RED 含 AssertionError 行号与堆栈；tip `e32a7043…` / `bbdd45e3…` 完整 SHA + run `44999170747` 四字段公开 API 实取；「实际 4 条 run」统一口径。 |
| 3 | 判序打地鼠 P1-1 | 采 **结构性收口**：21 条双向混淆样本共用 JSON + JVM 参数化 + 可跑复刻脚本；词表分层（强/弱限流语境、强/弱长度语义）；legacy 实跑 5 条 RED、W46 全 21 GREEN、JVM 注入 W45 旧逻辑 2 failed。 |
| 4 | 3b-ext 第三路 P1-2 | 采 **本地人工闸门入口**：`scripts/w46-local-secret-gate.sh` 三路四态实证（正常 0 / ignored 命中 / add-f tracked 命中 / 移除 0）；台账注明「CI 两路 + 本地人工闸门」组合判据；路数注释订正。 |
| 5 | 白名单正文洞 P1-3 | 采 **正文形态约束**：`run failed:` / `reason=` 类只认 ASCII 诊断码 + 长度上限 160；反向必红「provider message 含用户内容不落盘」变异实跑 RED；E8 口径改「全部白名单形态（含 1 条 run failed 账本行）」。 |
| 6 | W9-01 / 三待拍板 | 采 **评估后诚实让位 + 授权内裁决**：W9-01 逐文件复核后维持让位（跨三模块重构、ContextBudget 395/400）；W24-01 维持 OPEN 不代裁销账、W24-02 维持 CLOSED 行内边界、矩阵按用户「全面推进」指令做回归要点格。 |

**调度事实**：主理人亲自串行落代码、闸门、真机与文档（429 前科按纪律处理），不冒充子席产出。动手前 `git status` 干净、无并发写入方；变异验证用仓外 `cp` 备份精确还原，未用 git 写命令。

## 步 2 · 第 0 车（账实 / 证据 / 门禁）

| 项 | 结果 | 四态 | 证据路径 |
|---|---|---|---|
| 五行老号 W46 注记 | 4 虚构符号订正 + 弱窗口词合取式 + W9-01 评估让位 + W24-01 维持，pipe 自检均 7 | ✅ | `docs/06-deferred-ledger.md` |
| 台账符号指路自检 | 21 测试类名 + 60 测试方法名全部可复核；注入 `FunNameNeverImplementedTest` ⇒ RED、还原 GREEN | ✅ | `scripts/w46-ledger-symbol-check.sh`、`evidence/raw/w46/red_ledger_symbol.txt` / `green_ledger_symbol.txt` |
| 收尾契约订正 | tip 两完整 SHA + run `44999170747` 四字段（公开 API 实取）+「实际 4 条 run」 | ✅ | `docs/analysis/w45-acceptance.md` 步 5、`docs/handoff-20261122-wave45.md` §七 |
| G1–G4 原始输出重出 | 363 / 35 / 39 / 396 行，14 模块，TEST-XML mtime 在案 | ✅ | `evidence/raw/w46/g1_build.txt` / `g2_archguard.txt` / `g3_selftest.txt` / `g4_fulltest.txt` |
| 小口径订正 | `OpenAiErrorFrame 286/400`；probe 证据注明按形态分组重排 | ✅ | `docs/analysis/w45-acceptance.md` 步 3、`evidence/raw/w45/w45-probe-whitelist.txt` |
| 本地门禁脚本 | 三路四态：正常 0 / ignored 命中 / add-f tracked 命中 / 移除 0；路数注释订正 | ✅ | `scripts/w46-local-secret-gate.sh`、`evidence/raw/w46/g0_four_states.txt` / `g0_scan_v2.txt`、`.github/workflows/secret-scan.yml` |

## 步 3 · 第 1 车（代码 + 四闸门）

| 改动 | 文件 | 判据 / 证据 |
|---|---|---|
| 判序分层启发式（强/弱限流语境、强/弱长度语义）+ 限流词表扩 4 词 | `core-engine/.../OpenAiErrorFrame.kt` | 双向混淆 21 样本：legacy 5 RED（R04/R05/R08/C07/C08）、W46 全绿；JVM 注入 W45 旧逻辑 `2 tests/2 failed`（含 AssertionError 行号）|
| 双向混淆样本集（限流侧 8 / 超窗侧 10 / 协议侧 3）| `scripts/fixtures/w46-error-classification-samples.json` + `scripts/w46-classification-replay.py` + `OpenAiErrorFrameConfusionTest.kt` | 三处共用同一 JSON；参数化 JVM 2 用例；E5e/h/i/j/k/l/m/n 与 W44/W45 七条不回退 |
| 白名单正文形态约束（ASCII 诊断码 + 160 字符上限）| `app/.../DiagnosticLogSink.kt` + `DiagnosticLogSinkTest.kt` | 反向必红「动态 provider message 含用户内容不落盘」变异 `1 failed`（AssertionError at :76）、还原 GREEN；正向固定摘要仍落盘 |
| 测试数对账 | `.github/workflows/build.yml` | `EXPECTED_TESTS 878→881`（+2 engine / +1 app，实跑 881）|

**判据先行实证**：判序 = 等价复刻 21 样本先证明未修版 5 条反例 RED，再落 JVM 变异 RED；落盘 = 变异去掉正文约束后反向用例 RED，还原 GREEN；台账指路 = 注入虚构符号 RED，还原 GREEN。三处均「写 X 未发生前先自证判据会响」。

| 闸 | 结果 | 四态 | 证据 |
|---|---|---|---|
| G1 编译 | `assembleDebug` rc=0、APK 70.6 MB | ✅ | `evidence/raw/w46/g1_build.txt` |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 ID 不变）| ✅ | `evidence/raw/w46/g2_archguard.txt` |
| G3 selftest | `30/30`（289s）| ✅ | `evidence/raw/w46/g3_selftest.txt` |
| G4 fulltest | `tests=881 failures=0 errors=0 skipped=0`（14 模块）| ✅ | `evidence/raw/w46/g4_fulltest.txt` |
| 行数闸门 | `LINE_CAPS`/`TOTAL_LINE_CAP` 未调 | ✅ | G2 输出 |

## 步 4 · 真机（多模型 / 多上下文 / 功能入口 / 落盘白名单）

设备先唤醒（`mWakefulness=Awake` + 非黑主屏截图）→ 装机 Success → 冷启正常。密钥留于真机，未回灌。

| 格 | 四态 | 判据与证据 |
|---|---|---|
| E1 NV SuperVL 120B 1M 回声 | ✅ | `W46SUPERVL1M` 逐字回声 + 思考块；1.8k in / 115 out；设置页「当前：NV SuperVL 120B 1M」 |
| E2 NV Lightning 30B 回声 | ✅ | 切换后 `W46LIGHTNING30B` 逐字回声；8.4k in / 44 out；确认框 + 诊断串 |
| E3 工作空间文件读写 | ✅ | `file_write {"path":"output.txt","content":"W46OKThenReadItBack"}` 审批卡 → 允许一次 → `file_read` → 回复逐字 `W46OKThenReadItBack`；设备侧 `cat` 一致 |
| E4 多模态 | ✅ | 320×180 红矩形+蓝圆真图；模型回读 `#0000FF` 蓝圆右侧居中 + 红矩形 + 无渐变/边框白底平涂 |
| E5 skill | ✅ | `LoadSkillNotesOrganizerAndFollowItsSteps` ⇒ 遵循流程、列 workspace 文件、识别未更新笔记、询问是否写清单；4336 cached / 54.8% |
| E6 MCP | ⛔vacuous | 「还没有 MCP 服务器」0 个；外部 https 受 G15b 阻塞 |
| E7 手动压缩 | ⚠️ | 确认框走完；日志 `manual_compress=noop reason=manual compress: nothing to drop`（会话可压内容不足，正确行为） |
| E8 落盘白名单（本波改动） | ✅ | 本波时间窗（10:14Z+）非白名单 0 行；`run failed` 动态 provider message 未落盘；`manual_compress=noop reason=…` 固定摘要正确落盘；历史 11-21 `hard_budget` 按时间窗分离 |
| E9 端点切换守卫 | ✅ | 每次「设为当前」弹确认框 + `unverified_window` / `char_floor_unmeasured` |
| E10 OR Dots3（用户再次声明可用） | ⛔ | 会话请求 `本次请求失败: request rejected (HTTP 404)`（连续第五波设备真相） |
| E11 自动压缩 `applied` 分支 | ⚠️ | 只观察 `below_trigger` / `nothing_to_drop`，本会话体积未触发 applied，不写成已验证 |

**回灌级别**：截图判据（e_1m_echo / e_lightning30b / e_file_rw / e_multimodal / e_skill / e_mcp_vacuous / e_endpoint_guard / e_dots3_404）与 `deviceLogTail.txt` 均为脱敏件；PNG 二进制可打印串抽样 0 命中（无 `sk-`/Bearer/serial/本机路径）。题面一律英文无空格词（`input text` 连续 `%s` 会被吞，已规避）。

- 提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；单一代码提交 `c69d0ff76da73b32c075d5c6e2a3f6c98676b30b`（含全部代码/文档/证据）；PAT 仅 URL inline 一次性使用，未新增 remote、无凭据落盘。
- **代码提交 2 条 run（公开 API 四字段实取，均 `completed/success`）**：
  - Build `45014327186`：`head_sha=c69d0ff76da73b32c075d5c6e2a3f6c98676b30b` / `status=completed` / `conclusion=success` / `updated_at=2026-11-22T10:52:12Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45014327186`。
  - Secret scan `45014327291`：`head_sha=c69d0ff76da73b32c075d5c6e2a3f6c98676b30b` / `status=completed` / `conclusion=success` / `updated_at=2026-11-22T10:50:16Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45014327291`。
- 本验收物与交接所在文档提交为纯文档 tip：Build 0 run（既定 `paths-ignore` 口径），其 Secret run 按 W29 降级规则（补链 + 挂老号、不作 P0 判据）回填，回填后由公开 API 实取记录（见交接 §七）。代码提交两条 run 四字段才是本波 CI 判据；本地四闸门不代替 CI。

---

## 明确未做（逐条带挂账号）

1. `W9-01` 余面（`DEFAULT_*_RATIO` 搬 `AgentPolicy`、`alreadyCompressed` 单指针、字符闸同尺化）经复核维持让位——跨 core-kernel/core-agent/feature-chat 三模块重构、`ContextBudget` 395/400 贴近 G10，判据冻结下不夹带。
2. `W20-02` 本体再分层未做；`feature-chat`/`RunState` 行数水位未动。
3. `W20-05` / `W9-11` 1M 真压双硬限注入未做；本波只做 1M 档常规请求与档位回读。
4. `W5-03/05/06` MCP 外部 https（G15b）未做；E6 vacuous。
5. `W9-05` / `W24-03` journal 二选一未拍板，不代裁。
6. `W24-01` 余项四件未动，维持 OPEN；用户本轮未给销否字面裁决，不代裁。
7. `W24-02` 两条已知边界（`retry:` 透传 / 可点重试入口）维持 CLOSED 行内。
8. OR Dots3 第五波 HTTP 404、north 未单独会话轮；未查供给方公告。
9. 自动压缩 `applied` 分支未行使；Bolt/Nano 会话轮本波未做。
10. 凭据轮换：本波 PAT 以 URL inline 推送一次即视为暴露，轮换属用户侧动作。
11. `W37` 判据 7 波次盲区与协作用例文本形态未修（判据冻结）。
12. 历史缺口不补造。

## OPEN 挂账（触发信号 / 缓解动作 / 挂账号）

| 挂账号 | 触发信号 | 缓解动作 | 状态 |
|---|---|---|---|
| `W9-01` | 32K 档长历史被字符闸熔断且手动压缩 `nothing to drop` | 本波逐文件复核后维持让位，不写成已搬 | OPEN |
| `W20-02` | 下一次 `.kt` 增量触 G10 | 本体再分层待裁 | OPEN |
| `W20-05` / `W9-11` | 1M 满窗真压验收 | 双硬限注入独立裁决 | OPEN |
| `W5-03/05/06` | 外部 https MCP 验收 | G15b 明文门禁 + 端点可用性 | OPEN |
| `W9-05` / `W24-03` | 用户给 journal 二选一裁决 | 拍板后统一代数口径，不代裁 | OPEN |
| `W24-01` | 展开态黑面板 / 零尺寸进 AGSL 复现 | 余项四件待做；截图+协作用例销否待用户裁决 | OPEN |
| `W24-02` 已知边界 | 拥堵断流 / SSE `retry:` 帧复现 | 两条体验边界维持 CLOSED 行内 | CLOSED 行内注记 |
| `W36-01` | 被 ignore 文件绕过本地提交前闸门 | 三路本地闸门脚本 + 四态实证落地 | CLOSED（组合判据） |
| `W12-02` | —（已销账） | 白名单正文约束收窄动态正文洞 | CLOSED（W46 注记） |

## 交给外部审查团队的制品清单

1. `docs/analysis/w46-acceptance.md`（本文件）
2. `docs/handoff-20261122-wave46.md`（交接）
3. `docs/w46-dispatch.md`（派单卡，两数只在此抬头）
4. `docs/06-deferred-ledger.md`（五行 W46 注记 + W46 段声明）
5. `docs/analysis/w45-acceptance.md`、`docs/handoff-20261122-wave45.md`（收尾契约订正）
6. `.github/workflows/secret-scan.yml`（路数注释）、`.github/workflows/build.yml`（`EXPECTED_TESTS 881`）
7. `evidence/raw/w46/`：G1–G4 原始脱敏输出、RED/GREEN 必红输出（含堆栈与行号）、g0 四态、deviceLogTail、8 张脱敏判据截图、21 样本 JSON 等价复刻输出
8. 代码逐文件 diff：`OpenAiErrorFrame.kt`（分层启发式）、`DiagnosticLogSink.kt`（正文约束）、两测试类、新 `OpenAiErrorFrameConfusionTest.kt`；`scripts/w46-*.sh` / `.py` / `scripts/fixtures/w46-*.json`

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / uiautomator 噪声 /「建议下一链做什么」。

## 边界声明

- ① 判序以 21 样本双向混淆集 + JVM 变异双证据为准，不采「所有含长度词的限流文案皆限流」的过度推广；强限流语境（rate limit / tpm / per-minute / 429 / throttl / too many requests）才能压过窗口词组合，`quota` / `slow down` 属弱词。
- ② 白名单正文约束是 ASCII + 长度上限的形态判据，非语义判断：回显中文 prompt 被拦，但 ASCII 自由文本只要 ≤160 字符仍可能落盘——这是已知边界，不写成「完全不含用户内容」。
- ③ 本地门禁 third route 按 CI checkout 语义排除 `_w*/` 与 `local.properties`（历史取证目录在 CI 不存在）；本地全扫恒 RED 属预期，需看全貌用 `--local-full`。
- ④ 真机 E6 MCP / E11 applied 分支未行使，四态如实 vacuous / ⚠️，不以推断代替证据。
- ⑤ OR Dots3 404 为设备真相连续第五波，未查公告，不把「用户声明可用」写成事实。
- ⑥ W9-01 未做是复核后诚实让位（跨三模块重构 + 承重文件），不写成「已搬」；三待拍板在用户授权范围内按派单卡推荐路径裁决，销否字面裁决不代裁。
- ⑦ CI 绿以 run 四字段为准，本地四闸门不代替 CI；tip 文档 run 按 W29 降级只记录不作 P0。

> 本波止于交接，交由外部团队审查。
