# W35 验收物 — 零 `.kt` 账实收口（3 行）+ 多模型×上下文档位×功能面真机矩阵

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 起点 tip `ab5148f`（W34）；本波提交见 §5.6。零 `.kt`（310 个 `.kt` 零 diff，以本波收尾实跑为准）。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 台账权威版 = `docs/06-deferred-ledger.md`；本波制品 = `evidence/w35-matrix-summary.json`；证据 = `evidence/raw/w35/`。

---

## §一 做什么 / 明确不做（本波边界，先于一切）

### 1.1 做什么（按用户本单指令与六份外审裁决）

> **W36 时点订正**：本表九项在 W35 波内已全部完成（§三~§六/CI/交接逐节有结果），W35 收尾时误留「进行中/待跑」占位 = 收尾契约第 14 次回退；W36 第 0 车按外审 P0-3 统一回改 ✅，不重写 W35 正文事实。九项结果以 §三~§六、§5.6、handoff 的最终态为准。

| # | 做什么 | 落点 | 结果（W36 订正后） |
|---|---|---|---|
| 1 | 读记忆 + W34 handoff + OPEN 挂账行（只读现役仓 `harness-v2/docs/`，不读旧仓） | 本文件 §二/§三 | ✅ |
| 2 | 六份外审报告逐条裁决、**不改方案大体方向**（转代码波维持 OPEN） | §二（分歧裁决表） | ✅ |
| 3 | W35 第 0 车账实收口：w34 制品补回断言三字段（案 a）+ 判据 6 `K_assertion_presence` + 判据 7 必红分支 + `§2.5a` 悬空引用 + schema 小件 | checklist / w34 制品 / w33-w34 验收物（W35-01、W35-03） | ✅ |
| 4 | `W9-01` 主债行补 W34 F5/F6 真机证据 + F6 确定性根因（两个命题，不写成已修复；W36 命題②降级） | 台账 `W9-01` 行内注记（W35-02） | ✅ |
| 5 | 台账 W35 段 3 行落笔（6 列、无裸竖线、全 `OPEN`）+ 8 行销账证据列补 W34 复核指路 | `docs/06-deferred-ledger.md` | ✅ |
| 6 | 多模型 × 不同上下文（32K/256K/512K/1M）× 功能面（多模态/工作空间读写/skill/MCP/手动自动压缩）真机矩阵；边测边改，每迭代追加一行证据 | §三 + `evidence/raw/w35/iterations.jsonl` | ✅（13 格；1M 满窗 `NOT_EXERCISED`） |
| 7 | 本地闸门 G1–G4（编译/arch-guard/selftest/fulltest） | §四 | ✅ |
| 8 | 推送 main + CI 四字段复核（Build + Secret scan） | §5.6 | ✅（主体 1 次推送触发两条 run） |
| 9 | 验收物 + handoff + 制品 + 证据入仓，止于交接、交外审 | 本文件 + `handoff-20261120-wave35.md` | ✅ |

### 1.2 明确不做（逐条带挂账号）

| 不做 | 理由 | 挂账号 |
|---|---|---|
| 不做转代码波：拆分三方、压缩主线 `.kt` 修复、`ChatViewModel` 拆分 | 用户本单「不改方案大体方向」；`.kt` 授权未落地 | `W20-02`、`W9-01`、`W12-02` |
| 不把 F6 根因写成「已修复」或「已定位唯一分支」 | 只收窄为两个确定性命题；未持有真机气泡快照，不作唯一断言 | `W9-01`（W35-02 只补账不动 `.kt`） |
| 不修 `AppContainer.promptContext()` 三处吞 CE（本轮唯一成立的源码级实质发现） | 属 `.kt` 卫生批；与 `syncMcpTools` 同源，合并修复需用户裁决（第 8 项） | `W17-02` |
| 不改 `LINE_CAPS` / `TOTAL_LINE_CAP` / `EXPECTED_TESTS=818` | 拆分不调上限、拆分不当扩容 | `W20-02` |
| 不新建第 6 份 checklist / 第 8 份制品 / 不重出 W30 及更早正文 | 载体复用纪律 | `W35-01`、`W35-03`（只改现有载体） |
| 不恢复 8 份 `source-review`、不给 32 处引用逐处加抬头、不新增判据 8 | 用户已裁案 b；裁决 #4 定「生即红判据不新增」 | `W34-03`、`W35-03`（残余项登记） |
| 不做 1M tokens 真压（`full_context_1M_stress`） | `adb input` 单次 ~333 字符 + `file_read` 16k/次双通道硬限制；做不到记 `NOT_EXERCISED` | `W20-05`、`W9-11` |
| 不架外部 MCP server（F4 维持 VACUOUS） | 需 https 端点（G15b 禁明文 http），无 server 记 vacuous 不推断「MCP 不可用」 | `W5-03`、`W5-06` |
| 不做 journal / FreezeClock（会话不持久化老债） | 独立内核硬化波 | `W9-05`、`W24-03` |
| 不代做 tip run 逐波判 P0 | 既定降级规则（tip SHA / tip run 0 命中是预期） | 台账 W34 段 §5.6 声明 |
| 不判 SUPER-VL 403 / OR 免费池超时为 harness 缺陷 | 归供给侧 INC，设备 ping 通、NV 可用 | `W21-04`（矩阵 INC） |
| 不引入 MCP sampling/roots、不消费 `autoApproveDangerous` | 老债边界 | `W5-06`、`W5-05` |

---

## §二 六报告分歧裁决（不改方向；逐条给落点）

> 方案席（沈思远）逐条裁决、主理人汇编。判定准则：`改方向?` 恒为「否」= 用户「不改方案大体方向」指令；是/否列只区分「本波收口账实、但不动机制大方向」的 Yes 与「维持现状、顺延」的 No。
> W34 基线锚点（均指现役 main `ab5148f`）：`handoff-w34.md:46`（台账 210 行 / 33 `CLOSED` / 166 `OPEN` / 11 `CARRIED`）、`:91`（CI 主体 Build `44559513390` + Secret `44559513521`，推送 3 次）、`w34-acceptance.md:52`（F5 `dropped=9 kept=11`）、`:146`+`:154`（F6 `net=-256` 与手动 `dropped=9` 对同会话分裂、标「根因待核」）、`06-deferred-ledger.md` W9-01 行。

### 2.0 时点差证伪（先于分歧表）

- **harness-master 数字不自洽，不采信**：`harness-review-master.md:4` 写「台账 210 行 ID / 67 CLOSED / 204 OPEN」，与权威 `handoff-w34.md:46` / `06-deferred-ledger.md` 的 **33 / 166 / 11** 冲突，且 67+204=271≠210、CARRIED 11 也不足补齐。按「回源权威树」口径采信 33/166/11（**W34 时点** `ab5148f`；W36 订正：现役 W35 树已是 **33 `CLOSED` / 169 `OPEN` / 11 `CARRIED` = 213 行**），本报告不再引用 master 的 67/204 组合。
- **报告6「F6 两把尺子」与报告2/总纲链不同解**：报告2 给可复刻三行链（`savingChars=committedChars−committedChars(null)` → 恒 0 → net=−256），报告6 另解「自动 token 估算尺 vs 手动 chars 字符闸」。三者对「F6 是真异常 / 须收窄」结论一致，但 undo 路径不同 ⇒ 按「报告矛盾则回源」取可复刻链为真值源、两把尺子作候选解释。
- **三份外审引用计数为时点快照**：外审 32 处 / 14 文件、方案席复核 31 处 / 13 文件、主理人现役实跑 31 处 / 13 文件（`grep -ro` 出现次数口径）；W34 时点快照 26 处。以现役实跑 31/13 为真值源，其余作版本时点留痕（订正文字用拆分表述避免自指把 31 变 32）。

### 2.1 分歧表（逐条：是/否改方向 + 证据 + W35 落点 + 落地状态）

| # | 所裁报告与焦点 | 是/否改方向 | 裁决（证据：现役 main 文件:行） | W35 落点 | 落地状态 |
|---|---|---|---|---|---|
| 1 | H1/`W34深度审查与W35排期裁决`：是否转代码波（A-2 箱） | **否**（维持 OPEN） | `handoff-w34.md:46`+`:100`+`:111`、台账 W20-02/W9-01/W12-02 仍 OPEN；`arch-guard.sh` 全树 310 `.kt` 零 diff；用户本单「不改方案大体方向」 | `W20-02`/`W9-01`/`W12-02` 不新开行、合并修复须单独授权 | ✅ 已正确落地（不动代码） |
| 2 | H2/`harness-review-master`：压缩责任归 A-3 箱 vs 报告2 的 F-6 箱 | **否**（只统一表述） | `harness-review-master.md:23-32` 与报告2 §P0-2 同源同链；A-3 箱仅称「不计入本表」但压缩未闭合，可并入 F-6 | `W9-01` 行内注记；不新开行（#11 同型） | ✅ 已正确落地（并入 W9-01） |
| 3 | H2 与报告6：F6 根因「已定位」vs「未定位、需快照」冲突 | **否**（写成收窄非已修复） | 报告2 §P0-2 三行可复刻链 + `:407`/`:408` 时机差 + W34 日志 `15:22:14`/`15:22:59` + `15:24:01` 同会话对照 = 确定性链；报告6 两把尺子为候选；报告9 限定「两个确定性命题、不唯一断言」 | 写「已收窄为两个确定性命题 + 不同时刻 reconciliation（auto ~50–60k vs 手动 ~124k，两个时刻两个正确答案）+ noop 正确行为/标签混淆」，禁「已修复」「已定位唯一分支」 | ✅ 已由主理人订正（W9-01 注记改 reconciliation 框架，五词 + 50–60k/124k/noop/nothing_to_drop 全在） |
| 4 | H3/`W34-深度审查与挂账分析`：W7 之外单开 F6 行（P1-1） | **否**（不新开行） | 报告2 §P0-2 与 `handoff-w34.md:97` 均明确并入 W9-01；报告9 §四同样「F6 根因读代码、挂 W9-01」 | `W35-02` 补注记不新开行 | ✅ 已正确落地 |
| 5 | H4/路线建议：W34-03 出口三件套（26 处抬头 + 判据 8 + 复跑）争取 W35 关闭 | **否**（抬头不代做、判据 8 不新增） | 用户 2026-11-19 批注案 b「删除无问题」；裁决 #4 定「出生即红判据不新增」；现役计数 31/13 与外审 32/14 为时点差 | `W34-03` 残余项订正为现役计数 31/13 + 无判据覆盖留痕；不逐处加抬头 | ✅ 已由主理人订正（W34-03/W35-03） |
| 6 | H6/`W31-W34审查`：W26-02 进销账优先队列 | **否**（编排建议，不新开行） | 报告6 §五.1 自述「半天」属建议，非用户裁决；本波按 W34 既有 8 行销账照做 | W35-03 只补 8 行本波复核指路；W26-02 维持老号 | ✅ 已正确落地 |
| 7 | 报告9/F6 与真机 A-1：根因定位需动 .kt，转码波才能修 | **否**（不授权转码波） | 与 #1 同因；报告9 §二.2 明确「根因定位需要动 .kt 读代码」⇒ 属 A-2 授权域 | W9-01 只写可复刻命题与红态用例设计输入；不改 `.kt` | ✅ 已正确落地 |
| 8 | 报告2 §5 判据 6 去 `git rev-parse` 硬依赖 | **是**（可执行性修补，非方向） | `w29-closeout-checklist.md:292` 在解压快照 `fatal: not a git repository` 后在错误 cwd 跑；历轮 5 次同型 | W35-03 同车：判据 6 加 `REPO_ROOT` 兜底，无 `.git` 快照 rc=0 且六类必红 RED（W36 订正：实为六类） | ✅ W36 回改（实跑摘录在 checklist W35 加固节） |
| 9 | 报告2 §P0-3：判据 7 只查「有指针」不查本波复核 | **是**（账实订正，非方向） | 8 行 `W34` 命中 0/8 字面不精确（HEAD 下 `W30-01` 已有「W34 销账」注记）——**准确表述是「无本波复核指路」**；判据 7 正常态靠历史 `文件:行` 蒙混 | W35-03：8 行补指路（主理人已落）；判据 7 加必红「删空某 CLOSED 行本波复核指路 ⇒ RED closed without current-wave pointer」，锚定**严格限定 8 个 W34 销账 ID**（W31-02/03 为历史 CLOSED 无 W34 指路，扫全表会假红——主理人已回传修正） | ✅ W36 回改：8 行指路 + 判据 7 必红（checklist W35 加固节双输出实跑） |
| 10 | 报告2 §P0-1：w34 制品断言字段整包删除却虚报 5/5 | **是**（账实订正，非方向） | 现役 `w34-matrix-summary.json` 顶层 28 键、`assertions`/`mutation_cases`/`mutation_selftest` 全缺；`w33-acceptance.md:70/:81` 指向不存在的 §2.5a | W35-01 案 a：补三字段（5/5/dict）+ `K_assertion_presence` 必红 + 两处 §2.5a 改指 §2.5/§2.2 | ✅ W36 回改：制品字段 31 键 + 判据加固（checklist 六类必红实跑） |
| 11 | 2026-11-20 本轮 F6 新证据（自动/手动同日两时刻） | **否**（不新开行） | 与 #3/#4 同因：证据是 W9-01 的红态用例设计输入，不是独立挂账对象 | W35-02 只补账不动 `.kt`；按 W28 案 B 不作 W9-01 红态入口 | ✅ 已正确落地 |

### 2.2 方案席建议（采纳为执行口径）

- W35「先收口、再补主债行、不新开制品/checklist」的排期原则采纳；本波不新建第 6 份 checklist、第 8 份制品。
- F6 四派统一表述必须保留第三派「不同时刻 reconciliation」，否则下一波易读成「已修复」。
- 引用计数以现役实跑 31/13（出现次数口径）为真值源；外审 32/14、W34 快照 26 只作版本时点留痕。
- #9 落地状态区分「8 行指路已落」与「判据 7 必红进行中」，不把「行内已补」写成「判据已加固」。

---

## §三 真机矩阵

> 制品 `evidence/w35-matrix-summary.json`（13 格 / `state_counts={PASS:6, PARTIAL:6, VACUOUS:1}` / 31 键 / `iterations_total=16`）；证据 `evidence/raw/w35/`（34 件本波真机脱敏原始件）。
> 设备 OPPO PDRM00 / Android 13 / serial `<device-serial>`（W36 脱敏订正：8 位十六进制设备标识改占位），默认端口 5037；唤醒三步法通过（screencap 非全黑 mean231 + SCREEN_STATE_ON/AWAKE）。密钥仅经真机端点编辑 UI 录入；本报告、证据、制品均不含密钥全文、完整设备标识与本机绝对路径。
> **真机席跑到 turns 上限（1h08m）中断，34 件证据已全部落盘、制品汇编未完成**；主理人按写入隔离纪律接手：补 F5/F6 证据 JSON、修 F2 虚报引用、补 iter14-16、汇编制品并实跑判据 6（正常态 OK + 六类必红全 RED）。不编造未跑的格。

### 3.1 格明细（13 格，逐格四态 + 证据路径）

| 格 | 面 | 状态 | 关键证据（`evidence/raw/w35/`） |
|---|---|---|---|
| E01 Lightning | 端点 | ✅ PASS | `e01_lightning_strict.json`：正控 strict 逐字 `W35-E01-LIGHT-STRICT`、1.8k/184、无失败横幅；context 1.9K/127K |
| E02 Bolt | 端点 | ✅ PASS | `e02_bolt.json`：strict 逐字 `W35-E02-BOLT-STRICT`、1.4k/14；W34 曾非 strict 判 PARTIAL，本波真机改善判 PASS |
| E03 Nano Omni | 端点 | ✅ PASS | `e03_nano_strict.json`：strict 逐字 `W35-E03-NANO-STRICT`、1.8k/68；端点 ctx 131072 与档位 1M 分轴 |
| E05 super-vl | 端点 | ✅ PASS | `e05_supervl.json`：旧 key HTTP 403 → UI 录入新 key 后 strict 逐字 `W35-E05-SUPERVL-NEWKEY`、context 1.9K/995.9K ⇒ **W34 的模型级 403 结论被证伪，实为密钥授权面** |
| E06 OR dots3 | 端点 | ⚠️ PARTIAL | `e06_or_dots3.json`：新旧 key 均 SocketTimeout（1 retry 后非静默）；设备 ping openrouter.ai 通 196-200ms/33% 丢包 ⇒ INC-w35-timeout 供给侧 |
| E07 OR north | 端点 | ⚠️ PARTIAL | `e07_or_north.json`：同窗口同型 SocketTimeout ⇒ INC-w35-timeout，不冒充 PASS |
| T 档位轴 | 档位 | ⚠️ PARTIAL | `t_tier.json`：32K/256K/512K/1M 四档 readback 全成立 + 32K 软预算 27.9K 实测；1M tokens 满窗真压 `NOT_EXERCISED`（双通道硬限） |
| F1 多模态 | 视觉 | ✅ PASS | `f1_vision_nano.json`：480×240 真图（红方块/绿圆，非 1×1）+ 非空语义题逐字回读（red/green、答案未泄漏题面）+ 98.3% cache 证明图像 token 参与 |
| F2 workspace | 工具+审批 | ✅ PASS | `f2_workspace.json`：审批 exact args `w35probe.txt`/`W35WS-F2-K7M2` → 设备侧 run-as cat + hex + mtime 逐字一致；修正 W34 双层路径 |
| F3 skill | skill | ⚠️ PARTIAL | `f3_skill.json`：@ 菜单列 2 项 + sentinel 逐字 `W35-SKILL-OK3`；但 3 次均未调 skill_load 工具卡（W34 曾 2ms 命中）⇒ 模型遵循差异、非 app bug，卡耗时判据本波未行使 |
| F4 MCP | MCP | ⛔vacuous | `f4_mcp_state.json`：0 server、无外部 https MCP 端点；记 VACUOUS 触发格，不推断「MCP 不可用」 |
| F5 手动压缩 | 压缩 | ⚠️ PARTIAL | `f5_manual_compact.json`：菜单+确认对话框截图成立，但本波 deviceLogTail 未捕获 `manual_compress dropped/kept` 双向判据；不冒充 PASS（W34 已有 applied 硬证据） |
| F6 自动压缩 | 压缩 | ⚠️ PARTIAL | `f6_auto_compact.json`：同会话三段 `noop net=-256` → **auto `dropped=4 kept=10` applied** → `already_compressed`；实证 W9-01 两命题，未持 bubbles 快照不作唯一分支断言 |

### 3.2 本波真机增量（相对 W34）

1. **E05 证伪 W34 结论**：super-vl 403 从「模型级永久拒绝」订正为「旧 key 授权失效、新 key 生效」——用户新 API 直接解锁一格真 PASS。
2. **E02 Bolt 回归 PASS**：模型遵循改善，strict 逐字（W34 为 PARTIAL）。
3. **F6 自动路径首次 captured applied**：`auto_compact dropped=4 kept=10`（本波 device log 原文）；同一自动路径 11 秒内 `net=-256`（17:42:14）→ `applied`（17:42:25），W36 订正：这坐实 W9-01 命题①「日志码确定性语义：`savingChars≡0 ⇔ compression=null`、`net=-256` 是标签语义错配」，但**不坐实「`:407` 时机是自动/手动分裂成因」**——`:407` 在 `onEvent` 前取旧快照是代码事实、未证实为成因，本波不同时刻 reconciliation（auto ~50–60k vs 手动 ~124k）与同一自动路径 11 秒 noop→applied 均表明**决定性变量是体积/试算能否压动**，该命题降为待验证假设（`W9-01`）。
4. **F1 多模态拒绝假绿**：显式排除「答案泄漏题面」的第一问、只认非空语义题，98.3% cache 佐证图像 token 参与。

### 3.3 判据 6 在 w35 制品的实跑（本波不重蹈 W34 零断言虚报）

正常态 `OK feature/evidence invariants`；六类必红全 RED：`k6_fail`→state domain、`k6_nomcp`→mcp field missing、`k6_nocell`→claims cell、`k6_iter`→iterations_total、`k6_ref`→`RED missing ref`、`k6_noassert`→`RED assertions missing`。制品 31 键含 assertions 6 / mutation_cases 6 / mutation_selftest（6=6）。

### 3.4 边界声明（不把推断写成事实）

- 1M tokens 满窗真压 `NOT_EXERCISED`；E05 的 1M 端点只验证 context readback，不做满窗。
- MCP 0 server 记 vacuous，不断言「MCP 不可用」；需外部 https 端点 + Bearer token 才能行使。
- OR 超时 / super-vl 旧 key 403 归 INC（供给侧 / 授权面），不写成 harness 缺陷。
- F6 不判具体 untouched 分支、不写成「已修复」；F5 缺双向 log 证据如实记 PARTIAL。
- 迭代上限：真机席 turns 上限 200 中断于 F6 后（制品汇编前），非触及用户「最大真机迭代 3」；证据 34 件完整落盘。

---

## §四 四闸门

> 实跑时点：2026-11-19/20，均在仓库根 `<workspace-root>/harness-v2` 执行（W36 脱敏订正：本机绝对路径改占位）。
> 四态口径见判据权威 `judgment-criteria.md` §4。日志落在**仓外工作区根**
> `<workspace-root>/_w35_*.log`（不入库，仅供复核；W36 脱敏订正），逐项如下。

| 闸门 | 命令 | 四态 | 关键实跑数字 | 证据路径 |
|---|---|---|---|---|
| G1 编译 | `bash scripts/localbuild.sh`（默认 `:app:assembleDebug`） | ✅ 通过 | `rc=0`，`BUILD SUCCESSFUL in 1m 23s`，270 actionable tasks 全 `UP-TO-DATE`（本波零 `.kt`，与 W34 出包面一致）；出包 `app/build/outputs/apk/debug/app-debug.apk` | `_w35_g1_localbuild.log` |
| G1b deterministic 复核 | 删包重出 ×2（落实「`rc=0` ≠ 出包、UP-TO-DATE 需删包重出」） | ✅ 通过 | 两次重出哈希均 `b41be9cc92ee6f904401f049613eea51777e2f9f25e689e1ae65c549ed2d4aa7`，与原始包完全一致 ⇒ deterministic 成立，非陈旧包 | `_w35_g1_rebuild1.log`、`_w35_g1_rebuild2.log` |
| G2 架构守卫 | `bash scripts/arch-guard.sh`（根目录跑，重定向再读防空输出） | ✅ 通过 | `OK=27  VACUOUS=1  VIOLATION=0  SELF_FAULT=0`，`[arch-guard] result=PASS`，与基线逐项吻合；`VACUOUS=1` 为既定基线、G21 棘轮未增长（非本波新增空转） | `_w35_g2_arch.log`（36 行，非空） |
| G3 selftest | `bash scripts/arch-guard-selftest.sh`（**后台运行**，前台 120s 会 SIGTERM 截断成空输出） | ✅ 通过 | `selftest: 30/30 PASS（269s）`，`rc=0`；临时树变异自证（trap 清理），不污染工作树；含 `VAC-G1b`/`VAC-G21` 两条空转机制自证 | `_w35_g3_selftest.log` |
| G4 fulltest | `eval "$(bash scripts/localbuild.sh --env \| grep '^export ')" && bash scripts/fulltest.sh` | ✅ 通过 | `tests=818 failures=0 errors=0 skipped=0`，83 个 `TEST-*.xml`，覆盖模块数 14（settings include = 14），`rc=0`；与严格 `@Test 818` 基线一致，`tests=0`/残留 XML/句柄锁三道假绿防线均未触发 | `_w35_g4_fulltest.log` |

**四闸门结论：G1/G1b/G2/G3/G4 全部 ✅，无 ⚠️/⛔/⛔vacuous。** 本波零 `.kt`（`find -newermt` 无新增 Kotlin 源），
四闸门结果与 W34 基线逐项一致，具可复现性。G3 因前台超时会截断已按要求后台跑满 269s；
G4 结果目录清除成功（无 Windows 句柄锁残留），故未出现「清不掉拒绝判绿」情形。

---

## §5.6 提交与 CI 回填

### 5.6.1 本波提交

| 项 | 值 |
|---|---|
| 分支 | `main`（已在 `build.yml` / `secret-scan.yml` 的 `on.push.branches` 白名单内，0-run 风险已排除） |
| commit | `8af409071540c94a465f69458b6d63bb6ba168bb`（`feat(w35): 零 .kt 账实收口 + 多模型×上下文档位×功能面真机矩阵`） |
| 提交身份 | `Rickeal-Boss <263587600+Rickeal-Boss@users.noreply.github.com>` |
| 变更规模 | 45 文件 / +1236 −35：6 改（`06-deferred-ledger`、`w33`/`w34` 验收物、`w29-closeout-checklist`、`w34-matrix-summary.json` + 本验收物）+ 新增 `w35-acceptance.md`、`w35-matrix-summary.json`、`evidence/raw/w35/` 38 件 |
| 入暂存纪律 | 全程显式 `git add <路径>`，未用 `git add -A`；25 个历史 stat/EOL 脏 M 与未跟踪 `_w34/` 未入库；`evidence/raw/w35/iterations.jsonl` 被全局 `.gitignore:42:*.jsonl` 误伤，单独 `git add -f` 纳入。W36 订正：原文「serial 仅 8 位」自证句不精确（8 位即完整设备标识），该行 serial 已清零改占位；并立人工闸门——凡被忽略文件 `git add -f` 前必须先过判据 3b-ext 脱敏扫描（device-id/path/pat-meta 三类 0 命中）方可纳入，不依赖 `-f` 绕过脱敏复核 |
| 推送判据 | `git push --dry-run` 先行：`ab5148f..8af4090 main -> main`（RC=0）；实推同结果（RC=0）。PAT 仅 URL inline 一次性使用、未落盘、remote 仍为干净 URL。PAT 长度、校验路径与轮换属用户侧动作，按 W34 先例不在仓内登记（W36 脱敏订正）。 |

### 5.6.2 CI run 四字段复核（公开 API 按 head_sha 查，不采信任何转述）

| 工作流 | run ID | status | conclusion | event | head_sha | 四字段齐备且匹配 | 链接 |
|---|---|---|---|---|---|---|---|
| Build | `44583693258` | `completed` | `success` | `push` | `8af409071540…`（前 12 位匹配本波 commit） | ✅ | https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44583693258 |
| Secret scan | `44583693221` | `completed` | `success` | `push` | `8af409071540…`（前 12 位匹配本波 commit） | ✅ | https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44583693221 |

- **本波非纯文档**：含仓根 `evidence/*.json`（`evidence/w35-matrix-summary.json`、`evidence/w34-matrix-summary.json`），
  避开 `paths-ignore` 黑名单（`docs/**` 与 `**/*.md`），故 Build 五 job + 独立 Secret scan 均正常触发，非 0-run。
- **Build 五 job 明细**（均 `completed/success`，非 vacuous 绿）：Unit tests / Arch guard (26 guards + mutation selftest) /
  Assemble Debug (JDK 21) / Secret scan (gitleaks + trufflehog) / Lint (baseline gate)。
- **推送次数序列（W36 订正，不写「1 次」/「2 次」）**：本波提交序列 **3 次**——① 主体 `8af4090`（触发上表两条 run：
  Build `44583693258` + Secret `44583693221`）；② §5.6 回填 tip `d1c966b`（Secret `44584106741`，Build 0 run）；
  ③ 交接 tip `bb757a2`（Secret `44584268635`，Build 0 run）。②③ 两条 tip 因 `paths-ignore` 命中纯文档（仅
  `docs/**` 与 `*.md`），Build **0 run**（按既定口径 **不写成「构建通过」**），只有独立 Secret scan 仍跑；
  两条 tip run ID 为外审联网实取、仓内此前未登记（W36 据实补记）。

---

## §六 OPEN 挂账（触发信号 + 缓解动作 + 挂账号）

> 权威版 = `docs/06-deferred-ledger.md`（本波落 W35 段 3 行 + 8 行销账证据列补 W34 复核指路 + W9-01 注记；状态列 169 `OPEN` / 33 `CLOSED` / 11 `CARRIED`，全树 213 行）。三行全零 `.kt`，缓解动作已做、出口判据已实跑，按台账 §三.3 待外部审查复核后再销账。

| 挂账号 | 触发信号 | 缓解动作（本波已做 / 待做） | 状态 |
|---|---|---|---|
| W35-01 | 外部审查按 §5.4 去 w34 制品找 5 断言得空；或删 assertions 字段判据 6 仍绿；或按 §2.5a 找原文 404 | 已做（案 a）：w34 制品补 assertions 5 / mutation_cases 5 / mutation_selftest；判据 6 加 `K_assertion_presence` + REPO_ROOT 兜底、六类必红实跑入仓；两处 §2.5a 改指 §2.5/§2.2。出口：四处一致、正常态 OK、删字段 RED、§2.5a 0 命中（缺陷描述关键词除外） | `OPEN` 待外审复核 |
| W35-02 | 按 W9-01 找 W34 压缩证据只读到「复测」；或把 net=-256 读成「收益确实不够」 | 已做：W9-01 行内补 F5/F6 真机证据 + 两确定性命题 + 不同时刻 reconciliation（auto ~50–60k vs 手动 ~124k）+ noop 正确行为/标签混淆；本波 F6 另捕获 auto `dropped=4 kept=10` applied。禁写「已修复」、不作唯一分支断言。仍遵 W28 案 B 不作红态入口 | `OPEN` 待外审复核 |
| W35-03 | 用户问「W30-01 凭什么关」而行内无 W34 复核；或删空某 CLOSED 行本波指路而判据 7 仍 OK | 已做：8 行证据列补「W35 本波复核指路（W34 §2.4+§2.5）」锚点（8/8）；判据 7 加必红分支（锚定严格限定 8 个 W34 销账 ID，W31-02/03 历史 CLOSED 不扫，避免假红），正常态 + 必红实跑入仓；引用计数订正为现役实跑 31 处 / 13 文件；净 OPEN 口径写明（−5 不含 W19-14 结案、含结案 −6） | `OPEN` 待外审复核 |
| W9-01 | 压缩主线未整体关闭（4.0 尺 / 触发线 / 尾部摘要 + 两把尺子标定） | 本波只补注记不动 `.kt`；下波按台账开工会读到 F5/F6 全部真机证据与确定性根因，不再漏 | `OPEN` 顺延（老号） |
| W17-02 | `AppContainer.promptContext()` 三处吞 CancellationException（本轮唯一成立的源码级实质发现，`AppContainer.kt:201/206/207`） | 只登记不修（`.kt` 卫生批，与 syncMcpTools 同源，合并修复需用户裁决第 8 项） | `OPEN` 顺延（老号） |
| W20-02 | 拆分三方 / G10 贴顶 18 波零 `.kt`（feature-chat 3996/4000、ChatViewModel 500/500） | 不动 `LINE_CAPS`；转代码波授权未落地 | `OPEN` 顺延（老号） |
| W20-05 / W9-11 | 1M tokens 满窗真压 `NOT_EXERCISED` | 本波只做四档 readback + 32K 软预算；真压受双通道硬限 | `OPEN` 顺延（老号） |
| W5-03 / W5-05 / W5-06 | MCP 明文 / autoApproveDangerous / MCP sampling | F4 记 VACUOUS，不架外部 server、不消费危险批准 | `OPEN` 顺延（老号） |
| W9-05 / W24-03 | journal / FreezeClock / 恢复卡二选一 | 独立内核硬化波，本波不代做 | `OPEN` 顺延（老号） |

> 三行 `CLOSED` 条件维持「仓内判据实跑输出入账 + 制品与真值源一致 + 证据经外部审查复核」，不写「须真机取证」；真机矩阵本波已实跑但销账条件不以真机为前提。
> 不新开 `W35-04+` 承接旧债；八项用户裁决只留痕不代做（转代码波 / journal / G20 扫描面 / 独立输入面借鉴 / tip run 闭环 / 历史四项 / 案 b 已裁 / `promptContext()` CE 合并修复授权）。

---

## §七 交给外部审查团队的制品清单

| 制品 | 用途 | 四态 |
|---|---|---|
| `docs/analysis/w35-acceptance.md` | 本波验收物（§一边界 / §二八项裁决 / §三真机矩阵 / §四四闸门 / §5.6 CI / §六挂账 / §七清单） | ✅ |
| `docs/handoff-20261120-wave35.md` | W35 交接（tip `bb757a2` 已落笔；W36 订正原「进行中」占位） | ✅ |
| `evidence/w35-matrix-summary.json` | 正式制品：13 格 / 6 PASS + 6 PARTIAL + 1 VACUOUS / 31 键 / `iterations_total=16` / 六类必红实证 | ✅ |
| `evidence/raw/w35/` | 38 件本波真机脱敏证据（截图判据 + deviceLogTail + 逐格 JSON，无密钥/未打码账号） | ✅ |
| `docs/06-deferred-ledger.md` | W35 段 3 行 + 8 行销账补指路 + W9-01 reconciliation 注记 + W34-03 计数订正 | ✅ |
| `docs/notes/w29-closeout-checklist.md` | 判据 6 六类必红（含 K_assertion_presence）+ REPO_ROOT 兜底；判据 7 本波指路必红（锚定 8 行）+ 实跑输出 | ✅ |
| `evidence/w34-matrix-summary.json` | W35-01 补齐断言三字段（5/5/selftest）+ full_context dict；31 键 | ✅ |
| `docs/analysis/w34-acceptance.md`、`docs/analysis/w33-acceptance.md` | W35-01/03 文档订正（§2.2/§3.6/§5.4、两处 §2.5a 改指） | ✅ |

**明确不含**：会话历史 / 任何凭据或密钥全文（仅 masked 掩码点与 secret mtime）/ 端点完整标识（模型只用公开名）/「建议下一链做什么」。
