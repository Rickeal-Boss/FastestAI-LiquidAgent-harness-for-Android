# W36 交接 — 六报告裁决（不改方向）+ 零 `.kt` 账实收口（W36-01/02/03）+ 新 API 多模型真机矩阵

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 台账权威版 = `docs/06-deferred-ledger.md`；本波验收物 = `docs/analysis/w36-acceptance.md`；
> 真机制品 = `evidence/w36-matrix-summary.json`；证据 = `evidence/raw/w36/`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 本交接只引用台账 §一已有行首编号，未引入任何不在台账行首的复合编号。

---

## 一、本波做了什么（一段话）

六份 W35 审查报告的 8 项分歧逐条裁决，**全部维持不改方案大体方向**（转代码波授权维持 `OPEN`）；第 0 车把 W35 自己漏的三处账实收干净：脱敏两类第 4 次回退清零（serial / 本机路径 / PAT 元信息六处改占位、制品 privacy 据实化、`git add -f` 人工闸门）、判据 3b/6/7 加固（扫描面扩三类、三字段一致性、波次无关参数化，三类必红实跑）、收尾契约第 14 次补完（九项/三条/§七统一 ✅、推送 2→3）、F6 结论三处同步（命题①=日志码确定性语义、命题②降级）。台账落 W36 段 3 行 + W9-01 命题②降级 + W35-02 项列订正。真机侧用新 API 测多模型 × 上下文档位 × 功能面矩阵（结果见 §四）。全程零 `.kt`（310 个 `.kt` 零 diff）。

---

## 二、六报告分歧裁决（不改方向；逐条给落点）

见验收物 §二（10 条裁决表 + 2.0 时点差证伪）。要点：
1. **三份报告数字不自洽**（报告2 的 70 CLOSED/209 OPEN、报告5 的 171/35/13）→ 不采信，用权威 33/169/11（落笔后 33/172/11，合计 216）。
2. **转代码波否**（用户未授权，G10 贴顶 19 波）→ 挂 `W20-02`/`W9-01`/`W17-02`/`W12-02`。
3. **F6 命题②降级**：`:407` 取旧快照是代码事实、未证实为分裂成因；决定性变量是体积/试算能否压动；「根因」保留给未定位 causal。
4. **判据 7 波次无关化**：方案席指出「加 35|36」仍是枚举式锚定 ⇒ 改 `W\d+` + `MIN_WAVE`，不再枚举波号。
5. **判据 8 / 守卫 CI 化 / SCHEMA.md 不新增**（用户不建制品 + 改守卫属用户裁决域）。
6. **F3 skill 判据不改口径**（改判据属放宽 vacuous 风险）；真机侧用采用信号题面复测。

---

## 三、第 0 车收口（W36-01/02/03；缓解动作已完成，状态维持 `OPEN` 待外审）

| 行 | 出口判据 | 实跑结果 | 四态 |
|---|---|---|---|
| W36-01 | 脱敏两类清零 + 判据 3b-ext 三类扫描面 + 人工闸门 | 全树设备标识/本机路径/PAT 元信息 0 命中（源头清零、无豁免规则）；判据 3b-ext 正常态 `OK no device-id/path/pat-meta`、注入 `RED sensitive leak hit`；制品 privacy 改据实口径；`git add -f` 人工闸门入 §5.6.1 | ✅ |
| W36-02 | 判据 7 波次盲区消除 + 判据 6 三字段一致性 | 判据 7 波次无关 `W\d+`+`MIN_WAVE`，正常态双 OK、必红 B `RED …W30-01`、必红 C（W35 三行改 CLOSED 无锚点）`RED …W35-01/02/03`；判据 6 四制品 OK、三类必红（nomut/count/noref）全 RED；无效变异 `not CLOSED` 已排除 | ✅ |
| W36-03 | 收尾契约补完 + F6 结论同步 | w35 验收物九项/三条/§七统一 ✅、推送 2→3、45 文件/5 改订正；W9-01 两处命题②降级、W35-02 项列删旧断言、f6 note 与 §3.2 同步 | ✅ |

**行内注记（不新开行）**：`W9-01`、`W35-02`、`W34-03` 沿用老号加注记；八项用户裁决只留痕不代做。

---

## 四、真机矩阵（`evidence/w36-matrix-summary.json`）

### 4.1 核心结论
`state_counts = {PASS:7, PARTIAL:5, VACUOUS:1}`（13 格，无零值键）。正控 = NV Lightning strict 逐字（`W36-E01-LIGHT-STRICT`），设备执行先行、每题先点「新对话」排除前污染。制品 34 顶层键 / `iterations_total=15` 实 15 / `assertions=6` = `mutation_cases=6` / `evidence_ref` 0 MISSING；证据 38 件（13 json + 23 张 1080×2400 PNG + deviceLogTail + iterations.jsonl）。

### 4.2 格明细
| 格 | 面 | 状态 | 证据要点 |
|---|---|---|---|
| E01 Lightning | 端点 | ✅ | 正控 strict 逐字 1.8k/183 |
| E02 Bolt | 端点 | ✅ | attempt2 改题面 strict 逐字、97.5% cache（W34/W35 判据差异本波修复） |
| E03 Nano Omni | 端点 | ✅ | strict 逐字、含思考块 |
| E05 super-vl | 端点 | ✅ | 新 key strict 逐字、无 403（W35 已录入）；1.9K/27.9K 为 32K 档软预算非满窗 |
| E06 OR dots3 | 端点 | ✅ | strict 逐字，W35 超时本波恢复（ping 25% 丢包但可达） |
| E07 OR north | 端点 | ⚠️ | 旧/新 key 均 404「request rejected」⇒ INC-w36-north-404 供给侧；同新 key Dots3 成功 |
| T 档位 | 32K/256K/512K/1M | ⚠️ | 四档 readback + 1M 档 readback 995.9K；1M 满窗 NOT_EXERCISED（双硬限） |
| F1 vision | 多模态 | ✅ | 480×240 真图（非 1×1）+ 题面不含答案词 + Nano 逐字「Red」 |
| F2 workspace | 工具+审批 | ✅ | 审批 exact args + 设备侧 cat/hex/mtime 双向一致 |
| F3 skill | skill | ⚠️ | sentinel 逐字但无 skill_load 工具卡；模型遵循差异非 app bug，判据口径不改 |
| F4 MCP | MCP | ⛔vacuous | 0 server、无外部 https 端点，记触发格不推断不可用 |
| F5 手动压缩 | 压缩 | ⚠️ | 对话框走通但本波无 dropped/kept 双向 log（历史 W35 行不冒充） |
| F6 自动压缩 | 压缩 | ⚠️ | 新增 4× `net=-256` 实证标签语义错配；applied/already_compressed 本波未再行使 |

### 4.3 判据订正与环境事件
- **主理人自检补 schema**：制品缺 `attempt_states`/`feature_faces_with_cells`/`coverage_note` 三键 + selftest note 无仓内指针，判据 6 正确报 RED；补齐（从现役 cells 派生、不编造）+ 补指针后正常态 OK、三类必红全 RED。
- **INC-w36-north-404**：North 旧/新 key 均 404、同新 key Dots3 成功 ⇒ 模型路由供给侧，非 harness 缺陷。
- **INC-w36-or-loss**：OR ping 25% 丢包/207ms，只作环境记录。

### 4.4 边界声明
1M 满窗真压 `NOT_EXERCISED`（`adb input ~333 字符` + `file_read 16k/次` 双硬限）；F4 0 server 记 VACUOUS；F3/F5/F6 未行使判据如实记 PARTIAL、历史 W35 行不冒充；密钥仅 UI 录入、含编辑页截图未入库。无 429 / turns 上限中断 / 设备掉线。

---

## 五、四闸门

| 闸 | 结果 | 备注 |
|---|---|---|
| G1 编译 | ✅ rc=0 | `BUILD SUCCESSFUL in 1m 52s`；出包 sha256 `b41be9cc92ee6f904401f049613eea51777e2f9f25e689e1ae65c549ed2d4aa7`，与 W35 基线一致（零 `.kt` deterministic） |
| G2 arch-guard | ✅ PASS | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0` |
| G3 selftest | ✅ 30/30 | 255s，后台跑满（前台会 SIGTERM 截断） |
| G4 fulltest | ✅ 818-0-0 | 14 模块 |

**CI**：见 §5.6（待推送后回填 run 链接与四字段）。

---

## 六、OPEN 挂账

- **W36-01/02/03** 三行 `OPEN`：缓解动作已做、出口判据已实跑，按台账 §三.3 待外部审查复核后再销账。
- **W9-01**：命题②降级（`:407` 代码事实但未证实成因）、命题①保留（日志码确定性语义）；下波按修订优先级先 `nothing_to_drop` + `outcome.applied` 前置判，再 `:407`；维持 `OPEN`。
- **W17-02**：`promptContext()` 三处吞 CE 只登记不修（`.kt` 卫生批需用户裁决第 8 项），维持 `OPEN`。
- **W20-02** 拆分三方 / **W20-05**+**W9-11** 1M 真压 / **W5-03**+**W5-05**+**W5-06** MCP / **W9-05**+**W24-03** journal：沿用老号，本波不动。
- **W34-03**：计数 31/13（docs md）与 32/14（全树）为口径差；案 b 维持删除、不逐处抬头、不新增判据 8。
- 八项用户裁决只留痕不代做（转代码波 / journal / G20 扫描面 / 独立输入面借鉴 / tip run 闭环 / 历史四项 / 案 b 已裁 / `promptContext()` CE 合并修复授权）。

---

## 七、交给外部审查团队的制品

- `docs/analysis/w36-acceptance.md`（本波验收物）
- `evidence/w36-matrix-summary.json`（正式制品）
- `evidence/raw/w36/`（本波真机脱敏证据）
- `docs/06-deferred-ledger.md`（W36 段 3 行 + W9-01 命题②降级 + W35-02 项列订正）
- `docs/notes/w29-closeout-checklist.md`（判据 3b-ext / 6 三字段 / 7 波次无关加固 + 实跑输出）
- `docs/analysis/w35-acceptance.md`、`evidence/w35-matrix-summary.json`、`evidence/raw/w35/iterations.jsonl`、`evidence/raw/w35/f6_auto_compact.json`（W36-01/02/03 订正）

**不含**：会话历史 / 凭据 / 端点完整标识（模型 ID 仅公开名）/「建议下一链做什么」。

---

## 八、边界声明（不把推断写成事实）

- 零 `.kt`：310 个 `.kt` 零 diff。
- F6：命题①是日志码确定性语义、命题②降级为待验证假设；未持有真机气泡快照、不唯一断言具体 `untouched` 分支、不写成已修复。
- 1M：若受双通道硬限记 `NOT_EXERCISED`，不推断为 harness 缺陷。
- MCP：0 server 记 `VACUOUS`，不推断「MCP 不可用」。
- 脱敏：本波新写行不复述完整设备标识/本机路径/PAT 元信息；旧历史注记（W21 范本）按案保留。
- 真机矩阵结果、CI run 以回填为准，不预写不推断。

> 本波止于交接，交由外部团队审查。
