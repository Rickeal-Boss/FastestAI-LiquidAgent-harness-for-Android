# W37 交接 — 判据冻结落地 + 守恒式首波达标（0/3）+ 三件内核修复 + 新 API 13 格真机矩阵

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 台账权威版 = `docs/06-deferred-ledger.md`；本波验收物 = `docs/analysis/w37-acceptance.md`；
> 真机制品 = `evidence/w37-matrix-summary.json`；证据 = `evidence/raw/w37/`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 本交接只引用台账 §一已有行首编号，未引入任何不在台账行首的复合编号。

---

## 一、本波做了什么（一段话）

**先收口、再转代码、再真机**。第 0 车把用户两条指令一次性落成条款：判据冻结声明进 checklist（v1 定版 + 唯一允许三类动作 + 禁止新增判据/制品/CI job）、判据 6 补齐三类断言必红与两条新断言自证（十一类必红实跑全 RED）、判据 7 默认从台账「本波销账」声明自动推导（正常态 `declared=3`、注入无指路真 RED）、`w36-acceptance.md` 三处内容订正、销 `W36-01/02/03` 三行。第 1 车做三件内核修复：`nothing_to_drop` 诊断码（越线 + 试算未压动 + 未压缩 ⇒ 单独归因，`insufficient_savings` 只留给「已产出压缩区但净收益不足」）、`AppContainer.promptContext()` 三处 `runCatching` 改显式重抛 CE、`EventReducer.buildUsageLabel` 消费 `promptCacheStatus` 四态（`cachedLabel` 未覆盖，见 §七）。G10 触发后按铁律拆分而非调上限：`AppContainer` 417→374（`PromptContextAssembler.kt`）。真机 13 格：4 PASS / 8 PARTIAL / 1 VACUOUS，核心证据是 F6 三态日志序列与 F3 从 PARTIAL 转 PASS。

---

## 二、五份 W36 外审报告的分歧裁决（8 条，摘要）

| # | 分歧 | 裁决 |
|---|---|---|
| 1 | `W36-02` 能否销 | 采纳「不可销」→ 本波补齐三项后可销（报告5 未做默认参数复测） |
| 2 | 销账池大小 | 销 3 行（`W36-01/02/03`），不把 `W35-*`/`W34-03` 混销注水 |
| 3 | 冻结声明落点 | 落 checklist 判据 1 之前（不新建文件、不动台账 §零 历史说明） |
| 4 | 判据 7 推导实现 | 从台账段头「本波销账」声明 + 最大波号推导（无 git 快照也可跑） |
| 5 | F3 判据口径 | 不改（改判据 = 放宽 vacuous 风险） |
| 6 | 判据 3b-ext 进 CI/arch-guard | 本波不采纳（改守卫属用户裁决域 + 冻结禁新增 CI job） |
| 7 | journal 终结声明 | 本波不做（独立内核硬化波） |
| 8 | `W34-03` 加 docs 存活性检查 | 不采纳（新增判据能力，违反冻结） |

---

## 三、第 0 车（冻结 + 判据可执行性 + 订正 + 销账 3）

| 项 | 实跑 | 四态 |
|---|---|---|
| 判据 6 正常态（w36 制品 / w37 制品） | `OK feature/evidence invariants` ×2 | ✅ |
| 判据 6 十一类必红 | 全 `RED`（含三类新必红 + 两新断言自证） | ✅ |
| 判据 7 正常态（默认自动推导） | `OK … (W37, declared=3)` | ✅ |
| 判据 7 必红（注入无指路） | `RED closed without current-wave pointer: W36-01, W36-03` | ✅ |
| 内容订正三处 | `8 改`→`6 改`、删 PAT 长度句、推送 2→3 | ✅ |
| 销账三行 | `W36-01/02/03` 翻 `CLOSED` + W37 本波复核指路 | ✅ |

**守恒式**：`本波新开挂账 0 / 本波销账 3`，`0 ≤ 3` ✅。

---

## 四、第 1 车（三件内核修复，全部老号）

1. **压缩诊断码**（`ConversationOps.kt`）：`committedChars > COMPACT_TRIGGER_CHARS && compression == null && !outcome.applied` ⇒ `nothing_to_drop`；未越线仍 `below_trigger`；已压缩仍 `already_compressed`。三条新 JVM 用例（`AutoCompactDiagnosticsTest`，4 个 `@Test`）。
2. **CE 重抛**（`AppContainer.kt`）：三处 `runCatching` → `try/catch(CancellationException){throw}` + `degradeTo` 辅助。
3. **四态读者**（`EventReducer.kt`）：`buildUsageLabel` 消费 `promptCacheStatus` 四态；输出文字向后兼容（真机 `(0 cached, 0.0%)` 与旧版一致）。
4. **G10 拆分**（`PromptContextAssembler.kt`）：`AppContainer` 417→374，`VIOLATION=0`。

`@Test` 818 → **822**（`build.yml EXPECTED_TESTS='822'` 同提交）。

---

## 五、真机矩阵（13 格）

`state_counts = {PASS:4, PARTIAL:8, VACUOUS:1}`；34 顶层键 / `assertions=6=mutation_cases=6` / `evidence_ref` 0 MISSING。

| 格 | 状态 | 要点 |
|---|---|---|
| E01 Lightning（正控） | ✅ | strict 逐字 `W37-E01-LIGHT`；1.8k/52；四态输出兼容 |
| E02 dots3 / E03 north / E04 nv-alt | ⚠️ | 未行使：端点切换受阻（`INC-w37-settings-tap`），密钥在设备但无法激活 |
| T01 32K / T02 512K / T03 1M | ⚠️ | 仅 127K 档 readback；1M 满窗 `NOT_EXERCISED`（双通道硬限） |
| F1 vision | ⚠️ | 当前端点无图片输入（UI 明示）；未用 1×1 像素替代 |
| F2 workspace | ✅ | 审批 exact args `{"path":"w37.txt","content":"OK"}`；设备侧 `O K` 2 字节、mtime 本波 |
| F3 skill | ✅ | `skill_list`(24ms) + `skill_load`(29ms) 真工具卡 + SKILL BODY |
| F4 MCP | ⛔vacuous | `none_by_design`（0 server） |
| F5 手动压缩 | ⚠️ | UI 路径走通，无 dropped/kept 回读 |
| F6 自动压缩 | ✅ | 跨包三态：`net=-256` → `nothing_to_drop` → `below_trigger` |

**环境事件（不归因 harness）**：`INC-w37-settings-tap`（设置页卡片点击无响应，底部 tab 正常；DataStore 手工切换被拒；本波未改 UI 代码，未判定为回归）｜`INC-w37-adb-input`（`input text` 含字面空格只注入首词，改全 `%s`）｜`INC-w37-uiautomator`（连续 dump 触发工具侧崩溃）。

---

## 六、四闸门 + CI

| 闸 | 结果 |
|---|---|
| G1 编译 | ✅ `BUILD SUCCESSFUL`（含 `--rerun-tasks` 全量重编；首轮遭遇 Kotlin 增量缓存损坏，清理后通过） |
| G2 arch-guard | ✅ `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0` |
| G3 selftest | ⚠️ 未重跑（本波未改守卫脚本，只改 checklist 内嵌检查器；30 用例数十分钟） |
| G4 fulltest | ✅ `tests=822 failures=0 errors=0 skipped=0`（14 模块） |

**CI**：（提交后回填四字段与推送序列）

---

## 七、OPEN 挂账（本波未销，逐条带号）

- `W20-02` 拆分三方：只做 G10 必要的 `AppContainer` 单文件拆分；settings 四屏与 core-context 下沉未做。
- `W9-01` 压缩主线：命题①（诊断码 + `outcome.applied` 前置判）已落地并真机验证；命题②（`:407` 时机）与 B1 其余三条未动 ⇒ 整行不销。
- `W17-02` 吞 CE：三处已改；缺「取消透传」直接用例（无 Robolectric），只有模式自证 ⇒ 不销。
- `W12-02` 四态读者：`buildUsageLabel` 已接；`ContextBudget.cachedLabel` 未接 ⇒ 不销。
- `W24-01` / `W24-02`：本波未动；真机新增「设置页卡片点击无响应」记入同族待查（不新开行）。
- `W35-01/02/03`、`W34-01/02/03`：缓解动作已在 W36 完成，本波未逐条复核出口判据，不注水销账。
- `W20-05`/`W9-11`（1M 满窗）、`W5-03/05/06`（MCP 外部 https）、`W9-05`/`W24-03`（journal）：外部阻塞/独立裁决域，不冒充 ✅。

**明确未做（逐条带号）**：`W20-02`｜`W9-01`（部分）｜`W17-02`｜`W12-02`｜`W24-01`/`W24-02`｜`W20-05`/`W9-11`｜`W5-03/05/06`｜`W9-05`/`W24-03`。

---

## 八、交给外部审查团队的制品 + 边界声明

**制品**：`docs/analysis/w37-acceptance.md`、本交接、`evidence/w37-matrix-summary.json`、`evidence/raw/w37/`（13 件脱敏）、台账 W37 段、`docs/notes/w29-closeout-checklist.md`（冻结声明 + 十一类必红 + 判据 7 自动推导 + W37 实跑摘录）、`docs/analysis/w36-acceptance.md`（三处订正）。

**不含**：会话历史 / 凭据 / 端点完整标识（模型 ID 仅公开名）/「建议下一链做什么」。

**边界**：
- PARTIAL 格的真实原因是「未行使」而非「能力不足」；`INC-w37-settings-tap` 未定位根因，不写成回归也不写成设备缺陷。
- F5 无本波 `dropped/kept`，历史行不冒充。
- 1M 满窗 `NOT_EXERCISED`；MCP 0 server `VACUOUS`；均不推断为 harness 缺陷。
- 取消透传只有模式自证用例，`W17-02` 因此不销。
- 判据 7 对人工漏写「本波销账」声明的空过（`declared=0`）是冻结窗口内接受的空命中语义。
- 本环境未重跑 `arch-guard-selftest`（未改守卫脚本）。

> 本波止于交接，交由外部团队审查。
