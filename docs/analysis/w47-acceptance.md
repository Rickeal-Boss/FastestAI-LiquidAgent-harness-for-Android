# W47 验收物 — 门禁默认全扫 / 判据接闸门 / 判序最后一轮 / 白名单写点收窄 / W9-01 拆分 / 真机多模型回归

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `9d05a64b996002b6006a230e4eb3a6ea1525c524`（W46 tip）；台账权威版 = `docs/06-deferred-ledger.md`；
> 派单卡 = `docs/w47-dispatch.md`；交接 = `docs/handoff-20261123-wave47.md`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 回灌级别：只回灌脱敏截图判据与 deviceLogTail 探针行；不回灌整段 logcat / 凭据 / 端点完整标识 / 序列号 / 本机路径 / 私人图片。

---

## 步 1 · 读记忆 / 交接 / 挂账 + 八份审查分歧裁决

读本仓 `docs/handoff-20261122-wave46.md`、`docs/06-deferred-ledger.md` 五行老号、用户提供的八份外审；未读旧仓 `Android-Agent/docs/`。三个 skill（harness-subagent-write-isolation / harness-android-dev / android-verify-loop）已加载。动手前 `git status` 干净。

**报告间打架数字的回源勘定（采现役树实测，不采转述）**：

| 争议项 | 报告说法 | 本环境实测 | 裁决 |
|---|---|---|---|
| 台账三数 | 216/80C/209O（harness-w46-review）；216/41C/168O/13CARRIED（莫斯线） | **216 行 / 39 CLOSED / 166 OPEN / 11 CARRIED** | 采实测，其余为口径/时点差 |
| OpenAiErrorFrame 行数 | 286/400（莫斯线 §七） | **316/400** | 采实测（W46 已订正） |
| ContextBudget 路径 / 行数 | core-context（省钱复核引文） | **core-agent/loop/ 395/400** | 路径订正，行数与裁决一致 |
| W47 主线 | 多选题（省钱波 / 三按钮 / 先还债） | 派单卡第 0 车 + 第 1 车 | 采派单卡；价格维度方向采纳但本波不开（先还债、判据冻结） |

**八份审查的方向裁决（带依据）**：

| # | 分歧 | 裁决与依据 |
|---|---|---|
| 1 | 门禁默认空过（深度审查 P0-1） | 采 **默认全扫 + 显式 `--ci-semantics`**：精确场景（被 ignore 的 `_w*/.jsonl` 含 serial 未 -f）默认 rc=0 空过，本地全扫才是正确默认；顶层补 `--local-full` 入口。 |
| 2 | 判序超窗侧强限流词盲区（深度审查 P0-2） | 采 **最后一轮词表调整**：`context` 恢复无条件强窗口词、让位只留 `prompt`，样本 21→25；判据先行 4 条 RED → 修后 GREEN，注入旧逻辑 RED。W48 起改范式不再扩词表。 |
| 3 | 白名单短 ASCII 洞（深度审查 P1-1） | 采 **形态判据收窄**：`run failed:`/`reason=` 从 ASCII+160 收为固定诊断码白名单正则；未修版必红 RED → 修后全绿。 |
| 4 | 三脚本零闸门接入（深度审查 P1-2） | 采 **接进既有 step**：symbol-check 进 secret-scan、replay 进 build，零新 G 编号 / 零新 job；各注入必红已实证。 |
| 5 | 收尾契约第 7 次（深度审查 P1-3） | 采 **补真 tip + 第 4 条 run 四字段**：公开 API 实取 `45014733519`，删自指措辞、统一「实际 4 条 run」。 |
| 6 | 台账两处失实（深度审查 P1-1 台账项） | 采 **行内订正 + 去历史变量名反引号**：`W24-02`/`W12-02`/`W36-01` 补 W47 补录，状态不回开。 |
| 7 | 省钱外审「价格维度」 | 采复核裁决：**方向采纳、清单重写、本波不开**——先还 W47 的两个 P0，价格表不进 `RemoteEndpoint`/`RunnerKey`（会失效缓存命名空间），且 `EXPECTED_TESTS` 等判据冻结下不夹带跨模块新维度。 |
| 8 | W9-01 连续两波让位 | 采 **带行数预算的真拆分**：两个压缩常量从 core-context 搬入 `AgentPolicy`（唯一真值源），4 消费方改 import；不动 `ContextBudget` 承重文件；G10 触顶时精简 KDoc（不调上限）。 |

**调度事实**：主理人按纪律串行落代码、闸门、真机与文档（429 前科与高度耦合的「改→编译→真机→再看」链内循环决定的调度形态，不冒充子席产出）。动手前 `git status` 干净、无并发写入方；变异验证用仓外 `cp` 备份精确还原，未用 git 写命令恢复。

## 步 2 · 第 0 车（门禁接电 + 账实订正，先于代码）

| 项 | 结果 | 四态 | 证据路径 |
|---|---|---|---|
| P0-1 门禁默认全扫 | 未修版注入 rc=0 空过 → 修后默认 RED rc=1 / `--ci-semantics` GREEN rc=0 / `--local-full` 与默认同义 / 未知参数判故障红；selftest 四态逐 STATE 注明参数 | ✅ | `scripts/w46-local-secret-gate.sh`、`evidence/raw/w47/g0_four_states_v2.txt`、`g0_scan_v2.txt` |
| P1-2 符号自检接 secret-scan | 注入虚构 `FunNameNeverImplementedTest` ⇒ rc=1 RED、还原 rc=0 GREEN（20 类 / 59 方法 / 322 .kt） | ✅ | `.github/workflows/secret-scan.yml`、`evidence/raw/w46/red_ledger_symbol.txt` |
| P1-2 判序复刻接 build | 改坏样本 expected ⇒ rc=1 RED、还原 rc=0 GREEN（25 样本） | ✅ | `.github/workflows/build.yml`、`evidence/raw/w47/p02_classification_evidence.txt` |
| P1-1 台账两处失实订正 | `W24-02`/`W12-02`/`W36-01` 补 W47 行内注记，pipe 均 7；历史变量名去反引号后符号自检回 20/59 | ✅ | `docs/06-deferred-ledger.md`、`scripts/w46-ledger-symbol-check.sh` |
| P1-3 收尾契约 | 补真 tip `9d05a64b996002b6006a230e4eb3a6ea1525c524` 与 run `45014733519` 四字段（公开 API 实取），删「不再第四次推送」自指措辞，统一「实际 4 条 run」 | ✅ | `docs/handoff-20261122-wave46.md` §七、`docs/analysis/w46-acceptance.md` 步 5 |
| P2 旧符号数字 | W46 验收物「21 类名 + 60 方法名」按正常态实测订正为 20/59；g0 证据注明参数切换 | ✅ | 本文件步 2、`g0_four_states_v2.txt` |

## 步 3 · 第 1 车（代码 + 四闸门）

| 改动 | 文件 | 判据 / 证据 |
|---|---|---|
| 判序最后一轮：`context` 无条件强窗口词、让位只留 `prompt` | `core-engine/.../OpenAiErrorFrame.kt` | 4 条扩展边界未修版全 RED → 修后 GREEN；JVM 注入「context 也让位」⇒ 2 tests failed（AssertionError :72/:96）；`evidence/raw/w47/p02_classification_evidence.txt`、`red_p02_context_yield_mutant.txt`、`red_p02_jvm_mutant.txt` |
| 样本集 21→25（C11–C14 超窗侧强限流词）| `scripts/fixtures/w46-error-classification-samples.json` + `w46-classification-replay.py` + `OpenAiErrorFrameConfusionTest.kt` | 三处共用同一 JSON；legacy 5 RED / --w46 25 全绿；E5e/h/i/j/k/l/m/n 与 W44/W45/W46 既有必红不回退 |
| 白名单收窄：ASCII+160 → 固定诊断码白名单正则 | `app/.../DiagnosticLogSink.kt` + `DiagnosticLogSinkTest.kt` | 未修版 `shortAsciiFreeTextIsNotMirrored` 1 failed RED → 修后 4 用例全 GREEN；`evidence/raw/w47/red_p11_short_ascii.txt`、`green_p11_sink.txt` |
| 测试数对账 | `.github/workflows/build.yml` | `881→882`（app +1；判序只扩 JSON 方法数不变）；`EXPECTED_TESTS: '882'` |
| W9-01 带预算拆分 | `core-kernel/.../AgentPolicy.kt`、`core-context/.../ContextCompressor.kt`、3 消费方 import | 唯一真值源迁至 AgentPolicy（242/400，core-kernel 总量回 450 内）；G10 触顶时精简 KDoc、不调上限；行为等价（常量值不变） |

**判据先行实证**：三处新增/收紧判据均「写 X 未发生前先自证会响」——P0-1 空过 rc=0、P0-2 旧逻辑 4 条 RED、P1-1 短 ASCII 洞 1 failed RED，全部在落代码前实测。既有口径原样沿用，未逐波追加判据编号（G20 仍 141 ID）。

| 闸 | 结果 | 四态 | 证据 |
|---|---|---|---|
| G1 编译 | `assembleDebug` rc=0、APK 70.6 MB | ✅ | `evidence/raw/w47/g1_build.txt` |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 ID 不变；拆分后 G10 曾 461>450，精简 KDoc 后转绿）| ✅ | `evidence/raw/w47/g2_archguard.txt` |
| G3 selftest | `30/30 PASS（554s）` | ✅ | `evidence/raw/w47/g3_selftest.txt` |
| G4 fulltest | `tests=882 failures=0 errors=0 skipped=0`（14 模块），与 `EXPECTED_TESTS 882` 对账 | ✅ | `evidence/raw/w47/g4_fulltest.txt` |
| 行数闸门 | `LINE_CAPS`/`TOTAL_LINE_CAP` 未调；AgentPolicy 242/400、ContextBudget 395/400 | ✅ | G2 输出 |

## 步 4 · 真机（多模型 / 多上下文直至 1M / 功能面 / 落盘白名单）

设备先唤醒（`mWakefulness=Awake` + 非黑主屏截图）→ 冷启正常。密钥留于真机，未回灌。

| 格 | 四态 | 判据与证据 |
|---|---|---|
| E1 NV SuperVL 120B 1M 回声 | ✅ | `W47ECHO` 被逐字回引两次 + 思考过程块；1.8k in / 360 out；顶部「NV SuperVL 12...」；`evidence/raw/w47/e_1m_echo.png` |
| E2 NV Lightning 30B 回声 | ✅ | 切换后 `W47LIGHTNING30B` 逐字回引 + 思考块；1.8k in / 960 out；`evidence/raw/w47/e_lightning30b.png` |
| E3 工作空间文件读写 | ✅ | `file_write {"path":"output.txt","content":"W47FILEOK"}` 审批卡 → 允许一次 → 回复确认写+读回 `W47FILEOK`；设备侧 `run-as cat files/harness/workspace/output.txt` 逐字返回 `W47FILEOK`；`evidence/raw/w47/e_file_rw.png` |
| E4 多模态（320×180 真图） | ✅ | 白底左红矩形右蓝圆真图；回读 "plain white background" + "Red square...left side...solid red" + "Blue circle...right side...solid blue" + 不重叠；2.1k in / 221 out；`evidence/raw/w47/e_multimodal.png` |
| E5 skill | ✅ | `skill_load 5/5` 工具轨迹 + 思考块；列 workspace 文件（big60k.txt 等）、识别为测试数据、询问指向要整理的笔记、承诺遵循 skill 流程先出草稿再写；6.9k in / 572 out；`evidence/raw/w47/e_skill.png` |
| E6 MCP | ⛔vacuous | 「还没有 MCP 服务器」0 个；外部 https 受 G15b 阻塞（`W5-03/05/06`）；`evidence/raw/w47/e_mcp_vacuous.png` |
| E7 手动压缩 | ⚠️ | 确认框走完；本波日志 `manual_compress=noop reason=manual compress: nothing to drop`（06:09Z，可压内容不足，正确行为）；`evidence/raw/w47/e_manual_compress.png`、`deviceLogTail.txt` |
| E8 落盘白名单（本波收窄的真机复核） | ✅ | 本波时间窗（05:38Z–06:18Z）24 条探针行全部为固定诊断码形态（`cached=n/a-usage`/`cached=0(cumulative)`/`auto_compact=noop reason=below_trigger`/`manual_compress=noop reason=manual compress: nothing to drop`），收窄后合法摘要仍正确落盘、零形态外行；`evidence/raw/w47/deviceLogTail.txt` |
| E9 端点切换守卫 | ✅ | Lightning 切换弹 `unverified_window`/`char_floor_unmeasured`；SuperVL 切换弹 `empty_session`/`char_floor_unmeasured`；1M 档切换弹 `unverified_window`/`char_floor_unmeasured` |
| E10 OR Dots3（用户第三次声明可用） | ⛔vacuous | 本波进端点编辑页并执行「测试连接」，但会话级请求 404 未取得可靠证据（toast 未落盘、logcat 无 app 侧 HTTP 细节），不写成「本波复测 404」也不写成「可用」；沿用 W42–W46 连续五波设备真相 404 作为历史事实。供给方公告未查。 |
| E11 1M 档常规请求 | ✅ | 切 1M 档（确认框 + 诊断串）后发 `W47ONEMODE`，逐字回引 + 思考块，底部 `上下文 1.9K / 995.9K`；1.8k in / 406 out；`evidence/raw/w47/e_1m_tier.png`。**常规请求，非满窗真压**（W9-11 双硬限注入仍挂账）。 |
| E12 自动压缩 `applied` 分支 | ⚠️ | 本会话只观察 `auto_compact=noop reason=below_trigger`，未触发 applied，不写成已验证 |

**回灌级别**：8 张 PNG 二进制可打印串抽样对 `sk-`/Bearer/端点域名/serial/本机路径 0 命中；deviceLogTail 只含探针行；题面 ASCII 无空格或 `%s` 还原空格（连续 `%s` 吞词坑已规避）。

## 步 5 · CI（公开 API 四字段实取）

- 提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；PAT 仅 URL inline 一次性使用，remote URL 保持干净、无凭据落盘。
- **实际 4 条 run（代码两提交各 2，tip 双绿）**，完整四字段与链接见交接 §七：
  - 主体 `4fc96e183461503eca94d03b9a0ca33680f069e0`：Build `45124200338` `completed/success`（`2026-11-23T06:30:07Z`）；Secret `45124200326` `completed/failure`（`2026-11-23T06:27:32Z`）——3b-ext 命中 G3 selftest 证据未脱敏的用户目录形态，已由下一提交修正，红态如实记录。
  - 脱敏修正（最终代码 tip）`6e8751f7e200d47007e0c99d032534bfa2d46ace`：Build `45125222158` `completed/success`（`2026-11-23T06:37:30Z`）；Secret `45125222248` `completed/success`（`2026-11-23T06:36:39Z`）。**交付判据 = 最终 tip 双 run 全绿**。
- 本地四闸门不代替 CI；CI 绿以最终 tip 的 run 四字段为准。纯文档 tip 触发的 Secret run 按 W29 降级仅记录、不作 P0。

---

## 明确未做（逐条带挂账号）

1. `W9-01` 余面（压缩触发线与字符闸同尺化）本波只做常量拆分，触发线/字符闸两把尺未统一，维持 `OPEN`；32K 档 59 064 字符熔断 + `nothing to drop` 触发信号仍有效。
2. `W20-02` 内核本体再分层未做。
3. `W20-05` / `W9-11` 1M 满窗真压双硬限注入（`PreStepBudget` 400k + `RoundHeadGate`）未做；本波只做 1M 档常规请求。
4. `W5-03/05/06` MCP 外部 https（G15b）未做；E6 vacuous。
5. `W9-05` / `W24-03` journal 二选一未拍板，不代裁。
6. `W24-01` 余项四件（`overlayPanel=true` 接线 / `asOverlayColors()` 恒白族 / `AnimatedVisibility` 移出玻璃或 `heightIn(min=1.dp)` / AGSL `normalize` epsilon）未动；E1/E2 思考块正常显示为顺带观察，不代裁销账。
7. `W24-02` 两条已知边界（`retry:` 透传 / 可点重试入口）维持 CLOSED 行内；本波只做判序最后一轮，未收口这两条体验边界。
8. OR Dots3 本波未取得会话级 404 可靠证据，也未证伪「可用」；未查供给方公告。
9. 自动压缩 `applied` 分支、Bolt/Nano 会话轮本波未做。
10. 价格维度（省钱外审）方向采纳但本波未开：`PricePolicy` 未建、`cost=` 探针未加、档位切换本地规则未做。
11. 凭据轮换：PAT 以 URL inline 推送一次即视为暴露，轮换属用户侧动作。
12. 判据 7 波次盲区 / 协作用例文本形态未修（判据冻结）；历史缺口不补造。

## OPEN 挂账（触发信号 / 缓解动作 / 挂账号）

| 挂账号 | 触发信号 | 缓解动作 | 状态 |
|---|---|---|---|
| `W9-01` | 32K 档长历史被字符闸熔断且手动压缩 `nothing to drop` | 本波完成常量拆分（唯一真值源迁 AgentPolicy），同尺化余项留下一车 | OPEN |
| `W20-02` | 下一次 `.kt` 增量触 G10 | 本体再分层待裁 | OPEN |
| `W20-05` / `W9-11` | 1M 满窗真压验收 | 双硬限注入独立裁决 | OPEN |
| `W5-03/05/06` | 外部 https MCP 验收 | G15b 明文门禁 + 端点可用性 | OPEN |
| `W9-05` / `W24-03` | 用户给 journal 二选一裁决 | 拍板后统一代数口径，不代裁 | OPEN |
| `W24-01` | 展开态黑面板 / 零尺寸进 AGSL 复现 | 余项四件待做；四态截图 + 协作用例销否待用户裁决 | OPEN |
| `W24-02` 已知边界 | 拥堵断流 / SSE `retry:` 帧复现 | 两条体验边界维持 CLOSED 行内 | CLOSED 行内注记 |
| `W36-01` | 被 ignore 文件绕过本地提交前闸门 | 默认全扫 + 显式 `--ci-semantics`，四态实证 | CLOSED（W47 订正注记） |
| `W12-02` | —（已销账） | 白名单正文形态收窄为固定码白名单 | CLOSED（W47 注记） |

## 交给外部审查团队的制品清单

1. `docs/analysis/w47-acceptance.md`（本文件）
2. `docs/handoff-20261123-wave47.md`（交接）
3. `docs/w47-dispatch.md`（派单卡，两数只在此抬头）
4. `docs/06-deferred-ledger.md`（`W36-01`/`W24-02`/`W12-02` 三处 W47 补录 + W47 段声明）
5. `docs/analysis/w46-acceptance.md`、`docs/handoff-20261122-wave46.md`（收尾契约订正）
6. `.github/workflows/secret-scan.yml`（symbol-check step）、`build.yml`（replay step + `EXPECTED_TESTS 882`）
7. `evidence/raw/w47/`：G1–G4 原始脱敏输出、判据先行 RED/GREEN（门禁四态 / 判序 legacy+注入 / 白名单短 ASCII）、8 张脱敏判据截图、deviceLogTail
8. 代码逐文件 diff：`OpenAiErrorFrame.kt`（判序最后一轮）、`DiagnosticLogSink.kt`+测试（白名单收窄）、`ContextCompressor.kt`/`AgentPolicy.kt`/`ContextBudget.kt`/`ManualCompress.kt`+测试（W9-01 拆分）、样本 JSON、`scripts/w46-*`（门禁默认全扫）

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / uiautomator 噪声 /「建议下一链做什么」。

## 边界声明

- ① 判序最后一轮只改 `context`/`prompt` 让位语义，21→25 样本共用一份 JSON；W48 起改判据范式（结构化字段唯一权威 + message 兜底带证据标签），不再扩自然语言词表。
- ② 白名单收窄是**形态白名单**（固定诊断码正则），非语义判断：新增 reason 码若漏登记只损失落盘、不损失 ring（方向安全）；动态 provider message 仍在诊断页可见，只是不进设备持久化文件。
- ③ 门禁默认全扫会命中本地历史取证目录（W44 g0 已登记的 MUST-RED 样本），属预期；`--ci-semantics` 才是 CI checkout 语义，两者不可混用。
- ④ 真机 E6 MCP / E10 Dots3 / E12 applied 分支四态如实 vacuous / ⚠️；E10 本波未取得会话级 404 证据，不写成已复测也不写成可用。
- ⑤ 1M 档为常规请求（`上下文 1.9K / 995.9K`），非满窗真压；W9-11 双硬限注入未做。
- ⑥ 三待拍板（W24-01 销否 / W24-02 两边界 / 矩阵收官）本波未获用户字面裁决：W24-01 维持 OPEN、W24-02 维持 CLOSED 行内、矩阵按「全面推进」做要点格，代裁未发生。
- ⑦ CI 绿以 run 四字段为准，本地四闸门不代替 CI；tip 文档 run 按 W29 降级只记录不作 P0。

> 本波止于交接，交由外部团队审查。
