# W45 验收物 — 判序镜像反噬收口 / 落盘最小化 / 门禁双路 / 真机多模型回归

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `6e4a7f048f0c74245d02c3ce2a869ff1cfec4a54`（W44 tip）。
> 台账权威版 = `docs/06-deferred-ledger.md`；交接 = `docs/handoff-20261122-wave45.md`；派单卡 = `docs/w45-dispatch.md`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 回灌级别：只回灌脱敏判据文本与 `deviceLogTail` 结论；不回灌整段 logcat / 凭据 / 端点标识 / 序列号 / 本机路径 / 用户私人图片。

---

## 步 1 · 读记忆 / 交接 / 挂账 + 五份审查分歧裁决

- 读本仓 `docs/handoff-20261122-wave44.md`、`docs/06-deferred-ledger.md`（`W24-01`/`W24-02`/`W12-02`/`W36-01`/`W9-01` 五行）、用户提供的五份 W44 审查/草案/派单卡；未读旧仓 `Android-Agent/docs/`。三份 skill（harness-android-dev / android-verify-loop / harness-subagent-write-isolation）已加载。动手前 `git status` 干净（tip `6e4a7f0`）。
- 五份报告核心分歧：**判序 P0-1 镜像反噬**（两份独立外审列出 4+ 反例）、**3b-ext 精确场景空过**（外审指 `--exclude-standard` 排除被 ignore 文件、g0 自证 12 泄漏文件判绿）、**落盘隐私面扩大**（W44 把全部 INFO/WARN/ERROR 落盘）、**收尾契约第 5 次复发**（tip SHA 与第 4 条 run 漏记）、**RATIO 搬迁与 LENGTH 端到端**（余力项）。

**裁决（方向）**：

| # | 分歧 | 裁决与依据 |
|---|---|---|
| 1 | 判序镜像反噬定级 | 采 **P0 本波真修**：窗口词分强/弱两级（`context`/`prompt` 强、`token` 弱须无限流语境），补 3 条新必红未修版实跑 RED（变异注入 7 failed，含 3 条新必红 + 3 条 W44 回归 + 1 条 HTTP400）。 |
| 2 | 3b-ext 精确场景空过 | 采 **双路补扫**：`--exclude-standard` + `--ignored --exclude-standard`，探针改被 ignore 的 `.jsonl` + `git add -f`，g0 四态重出（正常 0 / ignored 1 / add-f tracked 1 / 移除 0）。 |
| 3 | 落盘隐私面扩大 | 采 **探针行白名单**：sink 只镜像固定诊断前缀 12 类，非探针行不落盘；补正向/反向/洪水三必红，洪水用例变异实跑 RED。 |
| 4 | 收尾契约 | 采 **订正入仓**：W44 验收物/交接补真 tip 与两文档提交 SHA、第 4 条 run `44936219342` 四字段、W29 降级规则原文（补链 + 挂老号，降级≠不记录）。 |
| 5 | `explicitRateLimit` 冗余项（P2-1） | 采 **真删**：`rateContext || (too many tokens && rateContext)` → `rateContext`（`A || (B&&A)=A`，行为不变），订正 W44「已简化」失实措辞。 |
| 6 | RATIO 搬迁 / 单指针 / 三待拍板 | 采 **让位 + 不代裁**：RATIO 涉 core-context→core-kernel 依赖与接近 G10 上限的 `ContextBudget`，本波让位 P0 与真机，维持 `W9-01` OPEN；三个待拍板用户未答复，维持现状（W24-01 OPEN / W24-02 CLOSED 行内边界 / 矩阵照常回归要点格）。LENGTH 端到端用例本波补（core-agent +1）。 |

**调度事实**：本波未派并行子席（429 前科按纪律处理），主理人亲自落代码、闸门、真机与文档，不冒充子席产出。动手前 `git status` 干净、无并发写入方；涉及源码编辑的变异验证用仓外 `cp` 备份精确还原，未用任何 git 写命令。

## 步 2 · 第 0 车（账实 / 隐私 / 兜底）

| 项 | 结果 | 四态 | 证据路径 |
|---|---|---|---|
| 五行老号 W45 注记 | `W24-02` 镜像反噬、`W12-02` 落盘最小化、`W36-01` 门禁双路、`W9-01` 代码面未动、`W24-01` 维持 | ✅ | `docs/06-deferred-ledger.md`（pipe 自检均 7） |
| 收尾契约订正 | W44 验收物/交接补真 tip `6e4a7f0…`、`327b314aa9…` 完整 SHA、第 4 条 run `44936219342`、W29 降级规则原文 | ✅ | `docs/analysis/w44-acceptance.md`、`docs/handoff-20261122-wave44.md` |
| `explicitRateLimit` 真删 | 冗余合取项删除，W44「已简化」失实措辞以 W45 订正为准 | ✅ | `OpenAiErrorFrame.kt` |
| 两行 W43 未来断言作废 | `W24-02` / `W12-02` 的 W43 注记加「（W44 已修/已销，本句作废）」 | ✅ | `docs/06-deferred-ledger.md` |
| 3b-ext 双路改造 | `--exclude-standard` 之外补 `--ignored --exclude-standard` 扫描（排除构建产物二进制，不排除 `_w*/` 与 `*.jsonl`） | ✅ | `.github/workflows/secret-scan.yml` |
| g0 四态实测 | 干净 clone：正常 0 / ignored `.jsonl` 未 -f 命中 1 / add-f 后 tracked 命中 1 / 移除回 0 | ✅ | `evidence/raw/w45/g0_untracked_scan_v2.txt`（含 12 个历史泄漏文件必红清单） |
| 守恒式纪律 | 派单卡恢复、两数只在抬头；台账 W45 段只写「销账集合 = 0 行」 | ✅ | `docs/w45-dispatch.md`、`docs/06-deferred-ledger.md` |

## 步 3 · 第 1 车（代码 + 四闸门）

| 改动 | 文件 | 判据 / 证据 |
|---|---|---|
| 窗口词强弱分级：强 `context`/`prompt` × 长度语义无条件优先；弱 `token` 须长度语义**且无限流语境** | `core-engine/.../provider/OpenAiErrorFrame.kt` | 3 条新必红（SSE `token rate limit exceeded` / SSE `too many tokens: rate limit exceeded` / HTTP400 `token rate limit exceeded`）；等价复刻 19 样本全绿；变异 W44 旧逻辑实跑 7 failed |
| `mentionsRateLimitContext` 提取单一真值源（SSE 帧与 HTTP400 同源） | 同上 | E5e/h/i/j/k/l/m/n 与 W44 四条一条不回退 |
| 落盘白名单：12 类探针/账本前缀 | `app/.../DiagnosticLogSink.kt` | 正向 15 形态 / 反向 8 形态 / 洪水 600 条必红（变异无白名单实跑 1 failed） |
| LENGTH 端到端诊断卡 | `core-agent/.../AgentRunnerTest.kt` | `Finished(LENGTH)` ⇒ `report.kind=Truncated`，正文保留、ModelStopped |
| 测试数对账 | `.github/workflows/build.yml` | `EXPECTED_TESTS 871→878`（+3 engine / +3 app / +1 core-agent，实测 `@Test` 878） |

**判据先行实证**：
- 判序 3 条：等价复刻 19 样本证明未修版 6 处反噬、修后 0 偏差；JVM 变异「token 无条件窗口词」实跑 `28 tests completed, 7 failed`（3 新必红 + E5h/i/m 全红），修后 GREEN（`green_p01_engine.txt`）。
- 落盘 3 条：变异「sink 不过滤」实跑 `3 tests completed, 1 failed`（洪水用例探针被挤出），修后 GREEN（`green_p14_sink.txt`）。
- 正控先行：等价复刻先跑已知必通过的 E5e/E5l/E5j/E6 等样本确认判据会响，再采信反例 0 命中。

| 闸 | 结果 | 四态 | 证据 |
|---|---|---|---|
| G1 编译 | `assembleDebug` rc=0、APK 68M | ✅ | `evidence/raw/w45/g1_build.txt` |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 141 ID） | ✅ | `evidence/raw/w45/g2_archguard.txt` |
| G3 selftest | `30/30`（290s） | ✅ | `evidence/raw/w45/g3_selftest.txt` |
| G4 fulltest | `tests=878 failures=0 errors=0 skipped=0`（14 模块） | ✅ | `evidence/raw/w45/g4_fulltest.txt` |
| 行数闸门 | `LINE_CAPS`/`TOTAL_LINE_CAP` 未调；`OpenAiErrorFrame` 291/400 | ✅ | G2 输出（G10/G11/G12） |

## 步 4 · 真机（多模型 / 多上下文 / 功能入口 / 落盘白名单）

设备先唤醒（`mWakefulness=Awake` + 非黑主屏截图）→ `adb install -r -t` Success → 冷启正常。密钥留于真机 Keystore/端点存储，未回灌。

| 格 | 四态 | 判据与证据 |
|---|---|---|
| E1 NV SuperVL 1M 测试连接 | ✅ | `连接正常（1048 ms）`；编辑页显示窗口 1048576 |
| E2 NV SuperVL 1M 会话 | ✅ | `W45SUPERVL1M` 逐字回声 + 思考块；`1.8k in / 94 out (0 cached)` |
| E3 工作空间文件读写 | ✅ | `file_write {"path":"w45probe.txt","content":"W45FILE"}` 审批卡 → 允许一次 → `wrote 7 chars` → `file_read` → 回复 `W45FILE`；设备侧 `cat` 复核内容一致 |
| E4 多模态（SuperVL） | ✅ | 320×180 红矩形+蓝圆真图；回复逐字「Red rectangle: A solid red rectangle positioned on the left side」+「Blue circle: A solid blue circle positioned on the right side」 |
| E5 skill | ✅ | `LoadSkillNotesOrganizerAndFollowItsSteps` ⇒ 遵循 skill 流程、列 workspace 文件、要求只收集点名的笔记文件 |
| E6 MCP | ⛔vacuous | `设置 → MCP 服务器` = 「还没有 MCP 服务器」（0 个）；外部 https 仍受既有阻塞项限制 |
| E7 手动压缩 | ⚠️ | 确认框（缓存重建 + 较早消息不再发）走完；日志 `manual_compress=noop reason=manual compress: nothing to drop`（会话体积小，正确行为） |
| E8 落盘白名单（本波改动） | ✅ | `run-as` 取回 `agent-0.log`：本波 11-22 时间窗 5+ 行**全部**探针形态（`cached=n/a-usage` / `cached=0(cumulative)` / `auto_compact` / `manual_compress`），非探针 0 行；历史残留行（11-21 `hard_budget`）按时间窗分离、不冒充本波 |
| E9 端点切换守卫 | ✅ | 每次「设为当前」弹确认框 + 诊断串（`empty_session` / `char_floor_unmeasured` / `unverified_window`） |
| E10 OR Dots3（用户声明可用） | ⛔ | 会话请求 `本次请求失败: request rejected (HTTP 404)`（连续第四波设备真相，端点侧非 harness） |
| E11 自动压缩 `applied` 分支 | ⚠️ | 本会话短、只观察到 `auto_compact=noop reason=below_trigger`；`applied` 未行使，不以推断代替证据 |

**回灌级别**：截图判据（e_1m_echo / e_file_rw / e_multimodal / e_skill / e_mcp_vacuous / e_dots3_404）与 `w45-probe-whitelist.txt` 均为脱敏件；不含密钥 / 端点完整标识 / 序列号 / SSID / 本机路径 / 私人图片。题面一律英文、无空格单词（`input text` 连续 `%s` 在本机被吞，已规避）。

## 步 5 · 推送 + CI

<!-- CI-PENDING: 推送后回填 commit SHA / run 四字段 / 链接 -->

- 提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；PAT 仅 URL inline 一次性使用，未新增 remote、无凭据落盘。
- 本地四闸门不代替 CI；CI 绿以 run 四字段为准。

---

## 明确未做（逐条带挂账号）

1. `W9-01` 余面（`DEFAULT_*_RATIO` 搬 `AgentPolicy`、`alreadyCompressed` 单指针、压缩触发线与字符闸同尺化）未改；本波让位 P0 与真机。
2. `W20-02` 本体再分层未做；`feature-chat`/`RunState` 行数水位未动。
3. `W20-05` / `W9-11` 1M 真压双硬限注入未做；本波只做 1M 档常规请求与档位回读。
4. `W5-03/05/06` MCP 外部 https（G15b）未做；E6 vacuous。
5. `W9-05` / `W24-03` journal 二选一未拍板，不代裁。
6. `W24-01` 余项四件未动，维持 OPEN；协作用例文本形态、flaky 风险维持已知边界（判据冻结）。
7. `W24-02` 两条已知边界（`retry:` 透传 / 可点重试入口）维持 CLOSED 行内；本波只补 LENGTH 端到端用例。
8. OR Dots3 第四波 HTTP 404（端点侧）；north 未单独会话轮（与 Dots3 同域名、同波状态）；未查供给方公告。
9. 自动压缩 `applied` 分支未行使；Bolt/Nano 会话轮本波未做。
10. 凭据轮换：本波 PAT 以 URL inline 推送一次即视为暴露，轮换属用户侧动作，仓内无法闭环。
11. `W37` 判据 7 波次盲区与协作用例文本形态未修（判据冻结）。
12. 历史缺口不补造。

## OPEN 挂账（触发信号 / 缓解动作 / 挂账号）

| 挂账号 | 触发信号 | 缓解动作 | 状态 |
|---|---|---|---|
| `W9-01` | 32K 档长历史被字符闸熔断且手动压缩 `nothing to drop` | RATIO 搬 policy / 单指针 / 同尺化评估（本波让位） | OPEN（沿用 + W45 补录） |
| `W20-02` | 下一次 `.kt` 增量触 G10 | 本体再分层待裁 | OPEN（沿用） |
| `W20-05` / `W9-11` | 1M 满窗真压验收 | 双硬限注入独立裁决 | OPEN（沿用） |
| `W5-03/05/06` | 外部 https MCP 验收 | G15b 明文门禁 + 端点可用性 | OPEN（沿用） |
| `W9-05` / `W24-03` | 用户给 journal 二选一裁决 | 拍板后统一代数口径，不代裁 | OPEN（沿用） |
| `W24-01` | 展开态黑面板 / 零尺寸进 AGSL 复现 | 余项四件待做；截图+协作用例销否待裁 | OPEN（沿用） |
| `W24-02` 已知边界 | 拥堵断流 / SSE `retry:` 帧复现 | 两条体验边界维持 CLOSED 行内 | CLOSED 行内注记 |
| `W36-01` | 被 ignore 文件绕过本地提交前闸门 | 3b-ext 双路补扫 + g0 四态实证（本波落地） | CLOSED（W45 精确场景收口） |
| `W12-02` | —（已销账） | 落盘白名单缩窄隐私面 + 设备侧探针实证 | CLOSED（W45 最小化注记） |

## 交给外部审查团队的制品清单

1. `docs/analysis/w45-acceptance.md`（本文件）
2. `docs/handoff-20261122-wave45.md`（交接）
3. `docs/w45-dispatch.md`（派单卡，两数只在此抬头）
4. `docs/06-deferred-ledger.md`（五行 W45 注记 + W45 段声明 + W44 未来断言作废括注）
5. `docs/analysis/w44-acceptance.md`、`docs/handoff-20261122-wave44.md`（收尾契约订正：真 tip / 第 4 条 run / W29 降级规则）
6. `.github/workflows/secret-scan.yml`（3b-ext 双路）、`.github/workflows/build.yml`（`EXPECTED_TESTS 871→878`）
7. `evidence/raw/w45/`：`g0_untracked_scan_v2.txt`、`g1_build.txt`、`g2_archguard.txt`、`g3_selftest.txt`、`g4_fulltest.txt`、`red_p01_sse.txt`、`green_p01_engine.txt`、`red_p14_sink_mutant.txt`、`green_p14_sink.txt`、`w45-probe-whitelist.txt`、六张脱敏判据截图
8. 代码逐文件 diff：`OpenAiErrorFrame.kt`（强弱分级 + 冗余项真删 + 单一真值源）、`DiagnosticLogSink.kt`（白名单 + 洪水防护）、`DiagnosticLogSinkTest.kt`（新）、三既有测试类新增必红

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / uiautomator 噪声 /「建议下一链做什么」。

## 边界声明

- ① 判序以等价复刻 19 样本 + JVM 变异双证据为准，不采「所有含长度词的限流文案皆限流」的过度推广：强窗口词 `context`/`prompt` 文案仍无条件判超窗（保住 E5h/i/l/m）。
- ② 弱窗口词 `token` 的新规则是「长度语义且无限流语境」，`rate limit: maximum 100 tokens per minute`（限流文案带 maximum）落回限流——这是比外审处方更保守的一档，19 样本全绿为其依据。
- ③ 落盘白名单只认固定诊断前缀；新增探针行须同步前缀，漏了只损失落盘、不损失 ring（安全方向）。
- ④ 四态探针只证明「四态标签可在设备文件复核」；`HIT`/`n/a-fields` 端点未上报属行为边界，W12-02 销账按组合判据成立。
- ⑤ OR Dots3 404 为设备真相，连续第四波；未查供给方公告，不把「用户声明可用」写成事实。
- ⑥ `W9-01` RATIO 未做是主动让位（涉依赖与行数上限），不写成「已搬」；三待拍板用户未答复，维持现状不代裁。
- ⑦ CI 绿以 run 四字段为准，本地四闸门不代替 CI；tip 文档 run 降级补链、不作 P0。

> 本波止于交接，交由外部团队审查。
