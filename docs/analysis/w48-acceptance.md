# W48 验收物 — 门禁去噪 / 白名单全覆盖 / 判序范式转换 / 真机多模型矩阵

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `8e1d986723e200f9ca9854932adf9c04309b86cc`（W47 tip）；台账权威版 = `docs/06-deferred-ledger.md`；
> 派单卡 = `docs/w48-dispatch.md`；判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；
> 推断带 caveat、不写成事实。守恒式两数只在派单卡抬头一份，本文件不复述。

---

## 步 1 · 审查裁决（五份 W47 外审 + 分歧回源）

**输入**：`W47深度审查与W48排期裁决.md`（深度审查）、`harness-w47-review.md`、`W47-深度审查与W901拆分闭环和门禁自证.md`、`W48派单卡.md`、`W48老号行内注记草案.md`（用户桌面件，裁决后落仓为 `docs/w48-dispatch.md` 与台账注记）。

**分歧裁决（数字回源勘定，采现役树实测不采转述）**：

| # | 分歧 | 裁决 | 四态 |
|---|---|---|---|
| 1 | 台账三数：深度审查采 216/39/166/11；「W901 拆分闭环」报告称 816/41/168/13 并指前者陈旧；harness-w47-review 写 216/80/209（自相矛盾） | **回源实测采 216 挂账行 / 39 CLOSED / 166 OPEN / 11 CARRIED**（816 为文件总行数，逐行剥反引号状态列清点，39+166+11=216 自洽）；「41/168/13」与「80/209」均判误（前者疑把行内注记状态字样计入，后者两数之和超总行数） | ✅ |
| 2 | 三待拍板框架：派单卡三问（W24-01 销否 / W24-02 两边界 / 矩阵收官）vs harness-w47-review 三按钮（W24-01 修\|收官 / journal 开\|弃 / F6） | 采派单卡三问为对账口径；用户 kickoff「继续按照计划执行、全面推进」⇒ 矩阵继续全面推进；`W24-01`/`W24-02` 按派单卡推荐路径维持（台账注记已注明依用户指令执行、非主理人代裁） | ✅ |
| 3 | W9-01 同尺化余项排期：「W901 拆分闭环」列 P0 本波正面做 vs 派单卡排在用户裁决后 | 采派单卡：core-kernel 449/450 贴线，`W20-02`/450 处理须先获用户一字裁决，本波不动 core-kernel、不调 `LINE_CAPS` | ✅ |
| 4 | 三脚本接闸门口径（P2-3）与消费方数量 3/4 不一致 | 采「两个判据脚本接 CI + 本机闸门保留本机入口」；「4 个文件改 import（其中 2 个运行时消费方）」，三处文档已统一 | ✅ |
| 5 | 白名单 W47 声明「形态白名单」易读成 12 类全覆盖（P1-1 措辞） | 采订正：W47 实态两类约束、其余 startsWith；W48 本波真修至全覆盖后仍按写点实态记账 | ✅ |
| 6 | 真机格数（harness-w47-review 称 13 格 E1-E12） | 采交接 12 格口径（E1–E12），时点记录不回改 | ✅ |

**调度事实**：主理人按 W47 既定形态串行落代码/闸门/真机/文档（「改→编译→真机→再看」链内循环与 429 前科决定，不冒充子席产出）；动手前 `git status` 干净；变异验证用仓外 `cp` 备份精确还原，未用 git 写命令恢复。

## 步 2 · 第 0 车（账实 / 门禁去噪 / 口径订正）

| 项 | 结果 | 四态 | 证据 |
|---|---|---|---|
| P1-2 门禁去噪 | `w46-local-secret-gate.sh` 路2/路3 改按历史 fixture **哈希白名单**跳过（`scripts/fixtures/w46-gate-legacy-hashes.txt` 注册基线 140 件；命中先查册、在册跳过、不在册照常 RED、tracked 路1 永不豁免）；自证输出只打文件名列不打命中行内容（防字面量进证据） | ✅ | `evidence/raw/w48/g0_four_states_v3.txt` 五态：历史在场默认 GREEN(0/0/0) / 新探针 RED / add-f tracked RED / 移除 GREEN / `--ci-semantics` GREEN |
| P2-1 收尾契约 | W47 交接 §七与验收物步 5 补文档 tip `8e1d9867…` Secret run `45125649179` 四字段（`head_sha=8e1d9867…`/`completed`/`success`/`2026-11-23T06:40:07Z`）+ 链接，W29 降级仅记录；「实际 4 条 run」改「代码提交 4 条 + 文档 tip 1 条（降级）」 | ✅ | `docs/handoff-20261123-wave47.md` §七、`docs/analysis/w47-acceptance.md` 步 5 |
| P2-2 证据名实 | `g0_scan_v2.txt` 标题改「CI checkout 语义态」；另出 `g0_scan_full.txt`（白名单禁用复刻全扫态，ROUTE3_HITS=140 + W47 时点 141 说明） | ✅ | `evidence/raw/w47/g0_scan_v2.txt` / `g0_scan_full.txt` |
| P2-3 口径 | 派单卡「三个脚本接闸门」改「两判据脚本接 CI + 本机闸门保留本机入口」；消费方数量统一「4 个文件改 import（其中 2 个运行时消费方）」 | ✅ | `docs/w47-dispatch.md` / W47 交接 §五 / W47 验收物 / 台账 W47 段 |
| P2-4 输入同步 | 仓内无「勾选状态以 02-roadmap 为准」残留（grep 0 命中，README:32 已是现役口径）；GitHub About 原为空、推送时置台账口径（见步 5）；用户侧旧快照按 `AGENTS.md:10` 不作真值源 | ✅ | 本表 + 步 5 About 变更记录 |
| P1-1 措辞 | W47 交接边界② / W47 验收物边界② 改「两类动态写点已收为固定形态正则、其余 startsWith（判据不对称已登记）」 | ✅ | 同上两文件 |
| 台账注记 | 5 条老号行内注记落笔（`W36-01`/`W12-02`/`W9-01`/`W24-02`/`W24-01`），逐行 pipe=7 自检 + W48 段声明「本波销账集合 = 0 行」 | ✅ | `docs/06-deferred-ledger.md` |

## 步 3 · 第 1 车（代码 + 判据先行）

| 改动 | 文件 | 判据（RED→GREEN） | 四态 |
|---|---|---|---|
| P1-1 白名单形态正则全覆盖 | `app/.../DiagnosticLogSink.kt`（`PROBE_FORMS` 整行正则集：12 类前缀 + `prefix` 双形态；顺带补回 W47 过窄正则拦掉的 `layered compress: l1 could not cut` / `mode=L2` 两条合法账本） | 9 条反向必红（≤160 ASCII/中文/邮箱密码/用户 echo）+ 正向整行模板：**未修版 2 failed RED**（`probePrefixWithUserFreeTextIsNotMirrored` :154 + `fixedFormTemplateLinesStillMirror` :186，`evidence/raw/w48/red_p11_whitelist_unfixed.txt`）→ 修后 7/7 GREEN（`green_p11_whitelist.txt`）→ 注入「cached= 拼用户正文」**1 failed RED**（`red_p11_cached_mutant.txt`）→ 还原 | ✅ |
| 判序 W48 范式转换 | `core-model/GenerationEvent.kt`+`AgentEvent.kt`（`evidenceTag` 默认空串）、`core-engine/OpenAiErrorFrame.kt`（结构化权威 + 8 支标签、词表冻结声明）、`RemoteLlmEngine.kt`（HTTP status 权威 + `http:*` 标签）、`core-agent` RunState/GenerationRound/RunTermination（透传 + ` [tag]` 落盘后缀） | 25 样本 replay `--w46` 全绿 + ConfusionTest 3/3 + ContextLimitTest 6/6 + StreamFailureTest 全绿（`green_p02_paradigm.txt`）；注入「回退到词表优先」⇒ `structuredTypeOutranksOverflowWordingWithEvidenceTag` **1 failed RED**（89 tests 1 failed，`red_p02_paradigm_mutant.txt`）→ 还原 | ✅ |
| HTTP status 权威 | `RemoteLlmEngineContextLimitTest.http429WithOverflowWordingStaysRateLimitWithEvidenceTag`：429 + 超窗正文 ⇒ RATE_LIMIT + `http:rate` | 同上 GREEN 集 | ✅ |
| 证据标签真机落盘 | `FAILURE_DIGEST` 允许可选 ` [a-z0-9:_.-]+` 后缀；真机 `run failed: request rejected (HTTP 404) [http:rejected]`（对比 11-22 旧形态无标签） | `evidence/raw/w48/deviceLogTail.txt` | ✅ |
| 测试数对账 | `build.yml` `EXPECTED_TESTS 882→886`（sink +2、Confusion +1、ContextLimit +1；严格行首 `@Test` 实数 886） | fulltest `tests=886` 三方一致 | ✅ |

## 步 4 · 真机矩阵（OPPO PDRM00 / Android 13，唤醒后安装；题面 ASCII）

| 格 | 内容 | 结果 | 四态 | 证据 |
|---|---|---|---|---|
| E1 | NV SuperVL 120B 1M 回声 | `W48ECHO1` 逐字回引 + 思考块 | ✅ | `e_super_vl_echo.png` |
| E2 | 1M 档常规请求 | `W48ONEMODE` 逐字 + `上下文 2K / 995.9K`（常规请求非满窗真压） | ✅ | `e_1m_tier.png` |
| E3 | NV Lightning 30B | 切换确认框 + 工具循环（file_list/file_read 分段读 `big60k.txt`/calculator）+ 缓存峰值 87.5% + 127K 档 + 轮次上限截断 + **止损熔断**（calculator 连败 4 次 ⇒ 安全熔断红卡）；回声经工具参数/思考块逐字（模型行为偏探查式，终答被轮次上限/熔断截断，不写成「已逐字回引」） | ⚠️ | `e_lightning_tools.png` + deviceLogTail |
| E4 | OR Dots3 Note Free（dots-3-note-preview:free） | **会话级请求成功（W42–W47 六波 404 后首次）**：`W48DOTS3` 逐字终答 + 思考块 + 工具循环（file_read/memory_search）+ 520.2K 窗口读数 + 缓存 99.7% | ✅ | `e_dots3_session.png` |
| E5 | OR North Mini Code Free（north-mini-code:free） | 会话级 **`request rejected (HTTP 404)`**（首个独立会话轮设备真相；失败卡 + `run failed` 落盘带 `[http:rejected]` 标签） | ⛔ | `e_north_404.png` + deviceLogTail |
| E6 | 多模态（320×180 真图：白底左红矩形右蓝圆）| Dots3 逐字语义：「red square on the left and a blue circle on the right, both on a white background」三要素齐 | ✅ | `e_multimodal.png` |
| E7 | 工作空间文件读写 | `file_write` 审批卡 → 允许一次 → 设备侧 `run-as cat files/harness/workspace/w48ok.txt` = `W48FILEOK` → `file_read` 回读 → 终答 `**W48FILEOK**` 逐字 | ✅ | `e_file_rw.png` + deviceLogTail |
| E8 | skill | `skill_load` 轨迹 + 采用语义（列 workspace 文件、按 skill 意图评估、询问指向） | ✅ | `e_skill.png` |
| E9 | MCP | 「还没有 MCP 服务器」0 server（G15b 外部 https 阻塞） | ⛔vacuous | 步 4 UI dump 记录 |
| E10 | 手动压缩 | 确认框走完，`manual_compress=noop reason=manual compress: nothing to drop`（可压内容不足，正确行为） | ⚠️ | deviceLogTail |
| E11 | 自动压缩 | 观察 `auto_compact=noop reason=nothing_to_drop` ×5（评估链真跑）；`applied` 分支未触发 | ⚠️ | deviceLogTail |
| E12 | 落盘白名单复核 | W48 时间窗 **40 条探针行全部固定形态**（prefix 账行含 cached 四态 `n/a-usage`/`0(cumulative)`/`N(cumulative)`）、零形态外行 | ✅ | `deviceLogTail.txt` |
| E13 | 端点切换守卫 | Lightning / Dots3 / North 三次切换均弹「确认切换」框 | ✅ | UI dump 记录 |

**多上下文档位覆盖**：SuperVL 995.9K（1M 档）/ Dots3 520.2K / Lightning 127K 三档真机读数。
**回灌级别**：只写脱敏判据结论与 deviceLogTail 探针行；不回灌整段 logcat / 凭据 / 端点完整标识 / 序列号 / 路径 / 私人图片；8 张 PNG 可打印串抽样 `sk-`/Bearer/域名/serial/路径 0 命中；题面 ASCII 无空格（空格 `%s`、连续 `%s` 吞词坑规避）。

## 步 5 · CI（公开 API 四字段实取，推送后回填）

- 提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；PAT 仅 URL inline 一次性使用，remote URL 保持干净、无凭据落盘。
- **代码提交 2 条 run，tip `8d545577ff6a6818910d01795f74e4528e7499ba` 双绿**（公开 API 四字段实取）：
  - Build `45153831020`（`head_sha=8d545577ff6a6818910d01795f74e4528e7499ba` / `status=completed` / `conclusion=success` / `updated_at=2026-11-23T10:14:12Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45153831020`）。
  - Secret scan `45153830812`（`head_sha=8d545577ff6a6818910d01795f74e4528e7499ba` / `status=completed` / `conclusion=success` / `updated_at=2026-11-23T10:11:59Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45153830812`）。**交付判据 = 最终 tip 双 run 全绿。**
- 本验收物所在文档提交为纯文档 tip：Build 0 run（`paths-ignore` 既定口径），其 Secret run 按 W29 降级仅记录、不作 P0（回填后不再触发进一步回填）。
- 本地四闸门：G1 `assembleDebug` rc=0；G2 `OK=27 VACUOUS=1 VIOLATION=0`（G20 141 ID 不变）；G3 `30/30 PASS（323s）`；G4 `tests=886 failures=0 errors=0 skipped=0`（14 模块）。
- GitHub About 同步（P2-4）：description 已置「API-first Android Agent Harness（模型是可插拔后端）。勾选状态以 `docs/06-deferred-ledger.md` 为准；`docs/02-roadmap.md` 仅历史参照。」（公开 API PATCH 实取确认）。

---

## 明确未做（逐条带挂账号）

压缩触发线与字符闸同尺化（`W9-01` 同尺化余项，行数前置未裁决）｜内核本体再分层（`W20-02`，core-kernel 449/450 须先裁决）｜1M 满窗真压双硬限（`W20-05`/`W9-11`）｜MCP 外部 https（`W5-03`/`W5-05`/`W5-06`，G15b）｜journal 二选一（`W24-03` 及 journal 老号）｜思考块余项四件（`W24-01` 余项）｜拥堵断流两条边界（拥堵断断老号）｜`W24-02` 两条已知边界（`retry:` 透传 / 可点重试入口，维持 CLOSED 行内）｜North 404 供给方公告核查｜自动压缩 `applied` 分支与 Bolt/Nano 会话轮补测｜价格维度（省钱外审，方向采纳本波未开）｜凭据轮换（用户侧）｜判据 7 波次盲区与协作用例文本形态（判据冻结）｜历史缺口不补造。

## OPEN 挂账（触发信号 / 缓解动作 / 挂账号）

| 挂账号 | 触发信号 | 缓解动作 |
|---|---|---|
| `W9-01` | 32K 档大历史首请求熔断 + 手动压缩 `nothing to drop` 并存；或同尺化余项被外审点名 | 压缩触发线与字符闸同尺化评估；须先获用户裁决 `W20-02`/450（core-kernel 449/450 贴线） |
| `W20-02` | 任何需往 core-kernel 加行的改动（余项/journal）触 G10 | 用户一字裁决「再分层」或「450 上限处理」；裁定前不动 |
| `W24-01` | 思考块玻璃节点四余项被外审/真机复现（折叠动画裁切、AGSL 归一化误差等） | 四件加固 + 四态截图销账（须用户裁决） |
| `W24-02` | `retry:` 透传 / 可点重试入口任一被外审要求收口 | 两条边界收口实现（须用户裁决） |
| `W20-05`/`W9-11` | 需要 1M 满窗真压结论 | 双硬限注入（单独裁决，不自动解禁） |
| `W5-03`/`W5-05`/`W5-06` | MCP 外部工具诉求出现 | G15b 明文传输解禁评估（单独裁决） |
| `W24-03`（journal 二选一） | journal 落盘形态被要求定案 | 二选一裁决（单独裁决） |
| OR 供给面老号 | North 持续 404 / dots3 再度不可用 | 供给方公告核查（用户侧确认供给状态） |
| `applied` 分支老号 | 自动压缩 applied 未行使的质疑 | 补测（大历史 + 跨触发线会话），安全截断点单独裁决 |
| 省钱外审老号 | 价格维度被要求开波 | `PricePolicy` 独立表（不进 `RemoteEndpoint`/`RunnerKey`）+ 成本账不落 `ContextBudget` + `cost=` 前缀白名单同步 |

## 边界声明

① 判序范式只动映射与标签、词表冻结；25 样本判级零变化，「回退到词表优先」变异必红已实证。② 白名单是形态白名单非语义判断：新写点漏登记只损失落盘不损失 ring；`context limit writeback` 为测试钉形态（生产写点为 `context_limit_writeback_failed`，不落盘口径维持）。③ 门禁哈希白名单只豁免 **注册基线 140 件**；新文件/被修改文件照常 RED，tracked 永不豁免；新长期取证样本须登记哈希入册。④ E3 Lightning 终答未逐字回引（模型行为 + 轮次上限/熔断），不写成通过；E5 North 404 为设备真相不写成可用；E9/E11 如实 vacuous/⚠️。⑤ 1M 档为常规请求非满窗真压。⑥ 三待拍板依用户 kickoff「按计划执行、全面推进」执行：矩阵全面推进、两老号按推荐路径维持，台账注记已注明非代裁。⑦ CI 绿以 run 四字段为准，tip 文档 run 按 W29 降级只记录不作 P0。⑧ E4 Dots3 会话级成功为本波设备真相；历史 W42–W47 404 记录维持历史事实不回改。

> 本波止于交接，交由外部团队审查。

## 交给外部审查团队的制品

`docs/analysis/w48-acceptance.md`（本文件）、`docs/handoff-20261123-wave48.md`、`docs/w48-dispatch.md`、`docs/06-deferred-ledger.md`（五处 W48 注记 + W48 段）、`docs/handoff-20261123-wave47.md` 与 `docs/analysis/w47-acceptance.md`（收尾订正）、`evidence/raw/w48/`（G1–G4 原始输出 + 判据先行 RED/GREEN 四件 + 门禁五态 + 8 张脱敏判据截图 + deviceLogTail）、`evidence/raw/w47/g0_scan_full.txt` 与 `g0_scan_v2.txt`（名实订正）、代码主体逐文件 diff（DiagnosticLogSink / OpenAiErrorFrame / RemoteLlmEngine / GenerationEvent / AgentEvent / RunState / GenerationRound / RunTermination / 三个测试类 / build.yml）、`scripts/w46-local-secret-gate.sh`（哈希白名单）+ `scripts/fixtures/w46-gate-legacy-hashes.txt`。

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / uiautomator 噪声 /「建议下一链做什么」。
