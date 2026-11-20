# W37 验收物 — 判据冻结落地 + 守恒式首波达标 + 全面推进代码波（压缩主线/CE 重抛/四态读者/新模型真机矩阵）

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 本验收物只引用台账 §一已有行首编号，不引入任何不在台账行首的复合编号。

## 抬头：判据冻结 + 守恒式两数（用户 2026-11-20 指令）

> **判据冻结**：矩阵 schema 定版为 `harness-matrix-summary/v1` —— 数据层 `cells[].state ∈ {PASS, PARTIAL, VACUOUS, FAIL}`（大写四值域，`state_counts` 只列非零出现值、`sum==len(cells)`）；显示层固定 `PASS→✅ / PARTIAL→⚠️ / VACUOUS→⛔vacuous / FAIL→⛔`；34 顶层键为 v1 最小集，`assertions` / `mutation_cases` / `k6_check.py` 检查项钉成同一 6 条集合。既有 12 条口径固化，不再逐波追加；W37 起不新增判据条目、不新建 checklist/制品/CI job、不改枚举、不重出历史制品；缺陷一律走内容订正 + 老号行内注记 + 销账。冻结声明落 `docs/notes/w29-closeout-checklist.md`（判据 1 之前）。
>
> 冻结窗口内只做三个动作（属「让既有判据真会响」）：① 三类新必红补可跑注入块（删 `mutation_cases` / 数量不等 / selftest 无指针）；② `K_privacy_no_key` / `K_coverage_13` 判定补进 `k6_check.py`；③ 判据 7 默认从「本波销账」声明自动推导（消除历史波号锚定）。

```
本波新开挂账：0
本波销账：3        （W36-01、W36-02、W36-03）
```

校验：`0 ≤ 3` ✅。基线 W24→W35 净 +1.7 条/波（W36 单波 `新开 3 / 销账 0` 净 +3）；本波拉到 0。

---

## §一 本波做了什么（一段话）

**先收口、再转代码**：第 0 车把用户的「判据冻结」与「守恒式」两条指令一次性落成条款——冻结声明进 checklist、判据 6 补齐三类断言必红与两条新断言自证（十一类必红实跑全 RED）、判据 7 默认从台账「本波销账」声明自动推导（正常态 `declared=3`、注入无指路真 RED）、W36 三处内容订正（`8 改`→`6 改`、删 PAT 长度元信息、推送 2→3）、销 `W36-01/02/03` 三行。第 1 车按修订优先级做三件内核修复：① `nothing_to_drop` 诊断码（`ConversationOps` 增加 `outcome.applied` 前置判，空转不再误标 `insufficient_savings:net=-256`；`SettledCompressPolicy` 语义注释同步）；② `AppContainer.promptContext()` 三处 `runCatching` 改显式重抛 `CancellationException`；③ `EventReducer.buildUsageLabel` 改为消费 `promptCacheStatus` 四态（消除「四态无生产读者」）。真机侧用新 API（OR dots-3-note-preview:free 512k / north-mini-code:free + NVIDIA 免费池）跑 13 格矩阵，重点验证压缩诊断码语义与新模型工具/视觉面。四闸门 + CI 见 §五/§5.6。

---

## §二 五份 W36 外审报告的分歧裁决

> 输入：《W37 派单卡》《W36 深度审查与 W37 全面推进裁决》《harness-w36-review》《W36-深度审查与双约束合规性分析》《FastestAI-LiquidAgent-harness_W36 深度审查与挂账分析及 W37 路线建议》。
> 裁决原则：证据优先（谁能给出实跑复刻谁优先）、证伪优先（采纳前 grep 现役树）、冻结优先（新增判据能力一律拒绝）。

| # | 分歧 | 两种主张 | 裁决 | 依据 |
|---|---|---|---|---|
| 1 | `W36-02` 能否销 | 报告5：实体动作同波已完成、够格关；裁决报告：⛔不可销（三类必红无可执行块、判据 7 默认不覆盖） | **采纳「不可销」→ 本波补齐后可销** | 裁决报告给了实跑复刻（注入后默认参数仍双 OK）；报告5 未做该复测。本波已补十一类必红 + 判据 7 自动推导（实跑原文入 checklist） |
| 2 | 销账池大小 | 报告4：销 `W35-01/02/03` + `W36-01/02`（最多 5）；派单卡：销 2~3 | **销 3 行（`W36-01/02/03`）** | 派单卡口径 + 补齐后扩到 3；W35 三行本波未逐条复核出口判据，不注水（用户「不必追求每波净关」） |
| 3 | 冻结声明落点 | 报告2：台账 §零；裁决报告：`docs/notes/` | **落 checklist 判据 1 之前 + W37 段抬头引用** | 台账 §零 是「为什么要有这个文件」的历史说明，塞契约会破坏原意；不新建文件 |
| 4 | 判据 7 默认推导实现 | 报告4：默认从「当波新 CLOSED 行」自动推导；裁决报告 (a) 同向 | **从台账段头「本波销账」声明 + 最大波号推导** | 无 `.git` 快照时无法静态区分「本波新翻 CLOSED」；段头声明与守恒式两数同源，天然绑定；空命中输出 `declared=0` 可见 |
| 5 | F3 skill 判据口径 | 报告2 上轮建议改判据（采用信号）；W36 裁决 #6 不改 | **不改** | 改判据 = 放宽 vacuous 风险；冻结窗口禁止改口径 |
| 6 | 判据 3b-ext 进 CI / arch-guard | 报告2 P2：机制性堵第 5 次回退 | **本波不采纳** | 「改守卫属用户裁决域」+ 冻结禁止新增 CI job；如实留痕，不新开行 |
| 7 | journal 终结声明 | 报告5 §四.2：一段话落地 | **本波不做** | journal 属独立内核硬化波，边界声明已列；不冒充 ✅ |
| 8 | `W34-03` 加 docs 存活性检查 | 报告5：判据 6 修订版纳入 | **不采纳** | 新增判据能力，违反冻结；`W34-03` 维持 `OPEN`，下波按既有口径处置 |

---

## §三 第 0 车：判据冻结落地 + 内容订正 + 销账（3 行）

### 3.1 冻结声明（落 `docs/notes/w29-closeout-checklist.md` 判据 1 之前）

v1 定版契约：数据层四值域 / 显示层 glyph 固定 / 34 顶层键最小集 / 12 条既有口径固化；禁止新增判据编号、checklist、制品、CI job、枚举变更；缺陷一律内容订正 + 老号行内注记 + 销账。**只允许三类动作**（让既有判据真会响）：① 三类断言必红可跑注入块；② `K_privacy_no_key`/`K_coverage_13` 进 `k6_check.py`；③ 判据 7 默认值自动推导。

### 3.2 判据可执行性补齐（先 RED 再落笔，全部实跑）

| 项 | 实跑输出 | 四态 |
|---|---|---|
| 判据 6 正常态（w36 制品） | `OK feature/evidence invariants` | ✅ |
| 判据 6 十一类必红（新增 `k6_nomut` / `k6_count` / `k6_noptr` / `k6_privkey` / `k6_cov12`） | 全部 `RED`（逐条原文见 checklist「W37 判据冻结补齐实跑输出摘录」） | ✅ |
| 判据 7 正常态（默认自动推导，不传参） | `OK … (W37, declared=3)` | ✅ |
| 判据 7 必红（注入 W36-01/03 翻 CLOSED 无指路） | `RED closed without current-wave pointer: W36-01, W36-03` | ✅ |
| 判据 6 对 w37 新制品正常态 | `OK feature/evidence invariants` | ✅ |

判据 7 推导实现：`CUR_WAVE` 默认 = 台账最大波号（段落标题与行首 ID 取大）；`CUR_IDS` 默认 = 最新「本波销账：…」声明里的 ID；`ANCHOR_SECS` 默认空（显式传参才做段级加强）。空命中输出 `declared=0`（合法：本波未销账），不静默冒充通过。

### 3.3 内容订正（w36 自身三处）

1. `w36-acceptance.md` §5.6.1「47 文件：8 改」→「6 改」（主体 6 改 + 41 新 = 47）。
2. 删 PAT 长度元信息句（保留「PAT 元信息不入仓」纪律表述）。
3. §5.6.2 推送次数 2 → 3：补 tip `8ed8304` 第 3 次推送 run `44719951896`（Secret scan success、Build 0 run）。
4. 台账 `W36-01` 出口判据补「本行注记引用亦除外」豁免句（P1-1 收口）。

### 3.4 销账（3 行，均带 W37 本波复核指路）

| 行 | 出口判据 | 本波证据 |
|---|---|---|
| W36-01 | 脱敏三类 0 命中 + 3b-ext 双态实跑 + 人工闸门 + P1-1 豁免句 | 外审三份报告独立复核清零；本波判据 6 十一类必红实跑；`w36-acceptance.md` 三处订正 diff |
| W36-02 | 判据 7 波次盲区消除 + 判据 6 三字段一致性 + 三类必红可跑 | 本波补齐三类必红注入块（实跑 RED）、判据 7 默认自动推导（`declared=3` / 注入 RED）、两新断言进 `k6_check.py` |
| W36-03 | 收尾契约补完 + 推送次数订正 + F6 结论同步 | 三处内容订正落 diff（推送 2→3、`8 改`→`6 改`、删 PAT 长度句） |

---

## §四 第 1 车：三件内核修复（全部老号，不新开行）

| # | 落点 | 改动 | 验证 |
|---|---|---|---|
| 1 | `ConversationOps.kt` | 试算后前置判：**越线**（`committedChars > COMPACT_TRIGGER_CHARS`）+ 未压缩 + `!outcome.applied` ⇒ `auto_compact=noop reason=nothing_to_drop`；`insufficient_savings` 只留给「已产出压缩区但净收益不足」；未越线仍走 `below_trigger` | 3 条新 JVM 用例 + 真机日志三态序列（见 §五 F6） |
| 2 | `SettledCompressPolicy.kt` | `shouldApply` KDoc 补诊断码分工（净 +3 行，逻辑零变） | 编译 + 既有 6 条纯函数用例 |
| 3 | `AppContainer.kt` | `promptContext()` 三处 `runCatching` → `try/catch` 显式重抛 `CancellationException`；抽 `degradeTo` 泛型辅助 | 模式自证用例（见下「限定」）+ 编译 |
| 4 | `EventReducer.kt` | `buildUsageLabel` 改为消费 `promptCacheStatus` 四态（HIT/MISS/CACHE_FIELDS_NOT_REPORTED/USAGE_NOT_OBSERVED），输出文字向后兼容 | 既有 `EventReducerTest` 全绿；真机 `(0 cached, 0.0%)` 与旧版一致 |
| 5 | `PromptContextAssembler.kt`（新文件） | G10 拆分：`promptContext()` 实现 + `degradeTo` + 顶层 `PromptContext` 迁出（`AppContainer.kt` 401→374 行） | `arch-guard` G10 OK（`VIOLATION=0`） |

**行数闸门（G10）**：`feature-chat` 3996 未变（余 4）、`core-data` 2506（拆分后净 −9，未触顶）、`core-context` 1646→1649（余 1）；`TOTAL_LINE_CAP=42500` 未动，`LINE_CAPS` 未调。

**@Test**：818 → **822**（+4，`AutoCompactDiagnosticsTest`），`build.yml EXPECTED_TESTS='822'` 同提交改。

---

## §五 真机矩阵（13 格，`evidence/w37-matrix-summary.json`）

`state_counts = {PASS:4, PARTIAL:8, VACUOUS:1}`（13 格，v1 定版 schema，34 顶层键，`assertions=6=mutation_cases=6`，`evidence_ref` 0 MISSING）。

| 格 | 面 | 状态 | 证据要点 |
|---|---|---|---|
| E01 Lightning | 端点（正控） | ✅ | 新包 strict 逐字 `W37-E01-LIGHT`；1.8k in / 52 out；`(0 cached, 0.0%)` 与旧版兼容 |
| E02 dots3 | 端点 | ⚠️ | 未行使：端点切换受阻（见 INC-w37-settings-tap），密钥在设备但无法激活 |
| E03 north | 端点 | ⚠️ | 同上 |
| E04 nv-alt | 端点 | ⚠️ | 同上 |
| T01 32K | 档位 | ⚠️ | 仅 127K 档 readback（`上下文 2.4K / 127K`），四档未逐档 |
| T02 512K | 档位 | ⚠️ | 未切档 |
| T03 1M | 档位 | ⚠️ | readback 未行使；满窗 `NOT_EXERCISED`（双通道硬限） |
| F1 vision | 多模态 | ⚠️ | 当前端点无图片输入（UI 明示）；未用 1×1 像素替代 |
| F2 workspace | 工具+审批 | ✅ | 审批卡 exact args `{"path":"w37.txt","content":"OK"}`；设备侧 2 字节 `O K`、mtime 本波 |
| F3 skill | skill | ✅ | `skill_list`(24ms) + `skill_load`(29ms) 真工具卡 + SKILL BODY；W35/W36 连续 PARTIAL 本波修复 |
| F4 MCP | MCP | ⛔vacuous | `none_by_design`（0 server）；不推断不可用 |
| F5 手动压缩 | 压缩 | ⚠️ | UI 路径走通（⋮→压缩上下文），无 dropped/kept 回读 |
| F6 自动压缩 | 压缩 | ✅ | 同一日志跨包序列：旧包 2×`insufficient_savings:net=-256` → 新包 `nothing_to_drop` → 修正版 `below_trigger`（越线前判生效）；每轮 Finished 后自动评估落账 |

### 5.1 本波最强的两条证据

1. **F6 三态序列**（同设备同日志文件）：`net=-256`（缺陷）→ `nothing_to_drop`（修复生效）→ `below_trigger`（**跨会话/跨包取证拼接**：`nothing_to_drop` 之后同会话 `committedChars` 只增不减，出现 `below_trigger` 必然换了会话或清了历史，不作为「修正版」直接证据）。`nothing_to_drop` 前置判生效是「改 → 编译 → 真机 → 再看」链内循环的可核产物。
2. **F3 从 PARTIAL 转 PASS**：W35/W36 连续两波「模型声称加载但无工具卡」，本波拿到 `skill_list` + `skill_load` 两张真卡与参数，证明工具链与判据口径一致。

### 5.2 环境事件（不归因 harness）

- **INC-w37-settings-tap**：设置页卡片点击无响应（底部 tab 正常）。本波未改任何 UI 代码；`DataStore` 手工切 `active_endpoint_id` 被应用侧拒绝（重建为「未配置端点」后已恢复原值）。**未判定为回归**，登记待查。
- **INC-w37-adb-input**：`adb input text` 含字面空格只注入首词；改用全 `%s` 分段注入（可复用题面注入技巧）。
- **INC-w37-uiautomator**：连续 dump 触发 `UiAutomationService already registered`（工具侧崩溃，非 app）。

---

## §5.6 提交与 CI

| 项 | 值 |
|---|---|
| 分支 | `main`（在 `build.yml` / `secret-scan.yml` 的 `on.push.branches` 白名单内，0-run 风险已排除） |
| commit | `6dcfe7252297676d9c2176016da1b1b5ece29904`（`feat(w37): 判据冻结落地 + 守恒式 0/3 + 压缩诊断码/CE 重抛/四态读者 + 13 格真机矩阵`） |
| 提交身份 | `Rickeal-Boss <263587600+Rickeal-Boss@users.noreply.github.com>` |
| 变更规模 | 30 文件：+1209 / −87（`.kt` 6 个含新测试与拆分文件；文档 5；证据 18；CI 1；台账/checklist 2） |
| 推送判据 | `git push --dry-run` 先行 `8ed8304..6dcfe72`（RC=0）；实推同结果。PAT 仅 URL inline 一次性使用、未落盘、remote 仍为干净 URL；不登记 PAT 元信息 |

| 工作流 | run ID | status | conclusion | event | head_sha | 四字段匹配 | 链接 |
|---|---|---|---|---|---|---|---|
| Build | `44738019517` | `completed` | `success` | `push` | `6dcfe725…`（12 位匹配） | ✅ | https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44738019517 |
| Secret scan | `44738019499` | `completed` | `success` | `push` | `6dcfe725…`（12 位匹配） | ✅ | https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44738019499 |

- **Build 五 job**：Unit tests（822）/ Arch guard / Assemble Debug (JDK 21) / Secret scan (gitleaks + trufflehog) / Lint（baseline gate），run 级 success；逐 job 未展开（API 权限面限制）。
- **推送次数序列**：主体 `6dcfe72` **第 1 次推送**触发上表两条 run；本 §5.6 回填构成纯文档 tip **第 2 次推送**——按 `paths-ignore` 降级规则 Build 0 run，**不写成「构建通过」**。

---

## §六 OPEN 挂账（本波未销，逐条带号）

| 挂账号 | 状态 | 本波处置 |
|---|---|---|
| `W20-02` 拆分三方 | `OPEN` | 只做 G10 必要的 `AppContainer` 单文件拆分（401→374）；feature-settings 四屏抽公共表单与 core-context 下沉未做（本波功能改动不涉及 settings；core-context 靠净零改动静止在 1649/1650） |
| `W9-01` 压缩主线 | `OPEN` | 命题①（`nothing_to_drop` 诊断码 + `outcome.applied` 前置判）**本波已落地并真机验证**；命题②（`:407` 时机）仍为第二优先级未动；B1 其余三条未行使。整行不销（未整体关闭） |
| `W17-02` 吞 CE | `OPEN` | 三处已改显式重抛；缺「取消透传」直接用例（无 Robolectric，无法构造 AppContainer 依赖），只有模式自证用例 ⇒ 不销 |
| `W12-02` 四态读者 | `OPEN` | `buildUsageLabel` 已成为生产读者；但 `ContextBudget.cachedLabel` 仍只到日志层，四态读者未覆盖第二处 ⇒ 不销 |
| `W24-01` 思考框 / `W24-02` 断流 | `OPEN` | 本波未动；真机新增「设置页卡片点击无响应」记入同族待查（不新开行） |
| `W35-01/02/03` | `OPEN` | 缓解动作在 W36 已完成，本波未逐条复核出口判据，不注水销账（守恒式只要求 `新开 ≤ 销账`） |
| `W34-01/02/03` | `OPEN` | 同上；`W34-03` 的 docs 存活性检查属新增判据能力，冻结窗口不采纳 |
| `W20-05`/`W9-11` 1M 真压 | `OPEN` | 维持外部硬阻塞（adb input + file_read 双限），不冒充 ✅ |
| `W5-03/05/06` MCP 明文 | `OPEN` | 需外部 https server + G15b，本波不触碰 |
| `W9-05`/`W24-03` journal | `OPEN` | 独立内核硬化波，不因「全面推进」自动开波 |

**明确未做（逐条带号）**：`W20-02`（只拆 1/3）｜`W9-01`（命题②与 B1 三条）｜`W17-02`（取消透传直接用例）｜`W12-02`（`cachedLabel` 读者）｜`W24-01`/`W24-02`（整包）｜`W20-05`/`W9-11`（1M 满窗）｜`W5-03/05/06`（MCP 外部 https）｜`W9-05`/`W24-03`（journal）。

---

## §七 交给外部审查团队的制品清单

- `docs/analysis/w37-acceptance.md`（本验收物）
- `docs/handoff-20261120-wave37.md`（本波交接）
- `evidence/w37-matrix-summary.json`（13 格矩阵，v1 定版 schema）
- `evidence/raw/w37/`（17 件脱敏证据：4 PNG + 12 JSON + 1 deviceLogTail）
- `docs/06-deferred-ledger.md`（W37 段：`本波新开 0 / 本波销账 3` + 三行 CLOSED + W36-01 豁免句）
- `docs/notes/w29-closeout-checklist.md`（判据冻结声明 + 十一类必红 + 判据 7 自动推导 + W37 实跑摘录）
- `docs/analysis/w36-acceptance.md`（三处内容订正）

**不含**：会话历史 / 凭据 / 端点完整标识（模型 ID 仅公开名）/「建议下一链做什么」。

---

## 限定（不把推断写成事实）

- 真机矩阵中 E02/E03/E04/F1/T01–T03/F5 记 PARTIAL 的**真实原因**是「未行使」而非「能力不足」；`INC-w37-settings-tap` 未定位根因，**不写成 harness 回归、也不写成设备缺陷**。
- F5 无本波 `dropped/kept` 双向日志，历史 W35 applied 行不冒充本波。
- 1M 满窗 `NOT_EXERCISED`（`adb input ~333 字符` + `file_read 16k/次` 双硬限），不推断为 harness 缺陷。
- 取消透传只有模式自证用例（同文件内示范 `runCatching` 吞 CE、`try/catch` 重抛），**无直接注入用例**（无 Robolectric）；`W17-02` 因此不销。
- 判据 7 的「本波销账」声明解析对**人工漏写声明**无强制力（漏写 ⇒ `declared=0` 空过）；这是冻结窗口内接受的空命中语义，已写进输出。
- 本环境未重跑 `arch-guard-selftest`（30 用例，数十分钟）；本波未改守卫脚本，只改 checklist 内嵌检查器。
