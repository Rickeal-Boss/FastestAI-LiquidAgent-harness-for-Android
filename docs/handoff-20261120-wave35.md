# W35 交接 — 零 `.kt` 账实收口（3 行）+ 多模型×上下文档位×功能面真机矩阵（13 格）

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 台账权威版 = `docs/06-deferred-ledger.md`；本波验收物 = `docs/analysis/w35-acceptance.md`；
> 正式制品 = `evidence/w35-matrix-summary.json`；真机证据 = `evidence/raw/w35/`（38 件）。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。

> **本交接只引用台账 §一已有行首编号**：W35-01/02/03（本波新落、已入台账）、
> W9-01、W17-02、W20-02、W20-05、W9-11、W5-03、W5-05、W5-06、W9-05、W24-03、W34-03、W19-14（既有行首）。
> 未引入任何不在台账行首的复合编号。

---

## 一、本波做了什么（一段话）

六份审查报告的 8 项分歧逐条裁决，**全部维持不改方案大体方向**（转代码波授权维持 `OPEN`）；第 0 车把 W34 自己漏的三处账实收干净：w34 制品补回断言三字段（案 a）+ 判据 6 加 `K_assertion_presence` 与 REPO_ROOT 兜底、判据 7 加本波复核指路必红（锚定 8 行避免假红）、两处 §2.5a 悬空引用订正，`W9-01` 主债行补 F5/F6 全部真机证据与 F6 确定性根因（两命题 + 不同时刻 reconciliation，不写成已修复）；台账落 W35 段 3 行 + 8 行销账补本波指路。真机侧完成 13 格矩阵（**6 PASS / 6 PARTIAL / 1 VACUOUS**），本波两个实质增量：**E05 super-vl 新 key 解 403（证伪 W34 的「模型级永久拒绝」，实为密钥授权面）**、**F6 自动路径首次捕获 `auto_compact dropped=4 kept=10`（applied），坐实 W9-01 确定性命题**。全程零 `.kt`（310 个 `.kt` 零 diff）。

---

## 二、六报告分歧裁决（不改方向；逐条给落点）

见验收物 §二（11 条裁决表 + 2.0 时点差证伪）。要点：
1. **harness-master 数字不自洽**（210/67/204，67+204≠210）→ 不采信，用权威 33/166/11。
2. **转代码波否**（用户未授权，G10 贴顶 18 波）→ 挂 `W20-02`/`W9-01`/`W17-02`。
3. **F6 根因写成收窄而非已修复**：两确定性命题 + 不同时刻 reconciliation（auto ~50–60k vs 手动 ~124k）+ noop 正确行为/标签混淆，禁「已修复」「已定位唯一分支」。
4. **引用计数以现役实跑为准**：31 处 / 13 文件（出现次数口径），外审 32/14 与 W34 快照 26 仅版本时点留痕；不新增判据 8。
5. **判据 6/7 加固**（可执行性 + 本波复核指路）属账实订正，不改机制方向。

---

## 三、第 0 车收口（W35-01/02/03；缓解动作已完成，状态维持 `OPEN` 待外审）

| 行 | 出口判据 | 实跑结果 | 四态 |
|---|---|---|---|
| W35-01 | w34 制品断言字段补回 + 判据 6 K_assertion_presence + §2.5a 订正 | w34 制品 28→31 键（assertions 5 / mutation_cases 5 / mutation_selftest）；判据 6 正常态 OK、六类必红全 RED（含 `RED assertions missing`、`RED missing ref`）；两处 §2.5a 改指（缺陷描述关键词除外全树 0 命中） | ✅ |
| W35-02 | W9-01 注记补 F5/F6 证据 + 确定性根因 | 行含 applied / dropped=9 / net=-256 / :407 / savingChars + 50–60k/124k/noop/nothing_to_drop/不同时刻；F6 本波另捕获 auto dropped=4 applied；不写已修复 | ✅ |
| W35-03 | 8 行销账补本波指路 + 判据 7 必红 + 计数订正 | 8 行锚点 8/8（四 token 同现）；判据 7 正常态 OK + 注入删空 RED（锚定 8 行、W31-02/03 历史行不扫）；计数订正 31/13；净 OPEN 口径写明 | ✅ |

**行内注记（不新开行）**：`W9-01`、`W17-02`、`W24-02` 沿用老号加注记；`W34-03` 补现役计数复核；案 b 已裁不抬头。

---

## 四、真机矩阵（`evidence/w35-matrix-summary.json`）

### 4.1 核心结论
`state_counts = {PASS:6, PARTIAL:6, VACUOUS:1}`（13 格，无零值键）。正控 = NV Lightning / Nano Omni sentinel 严格逐字（`W35-E01-LIGHT-STRICT`、`W35-E03-NANO-STRICT`），设备执行先行。

### 4.2 格明细
| 格 | 面 | 状态 | 证据要点 |
|---|---|---|---|
| E01 Lightning | 端点 | ✅ | 正控 strict 逐字、1.8k/184 |
| E02 Bolt | 端点 | ✅ | strict 逐字 1.4k/14（W34 PARTIAL → 本波改善） |
| E03 Nano Omni | 端点 | ✅ | 正控 strict 逐字、端点 ctx 131072 与档位分轴 |
| E05 super-vl | 端点 | ✅ | 旧 key 403 → 新 key strict 逐字、995.9K 端点 ⇒ 撤销 W34「模型级」结论 |
| E06 OR dots3 | 端点 | ⚠️ | 新旧 key 均 SocketTimeout、设备 ping 通 ⇒ INC-w35-timeout |
| E07 OR north | 端点 | ⚠️ | 同窗口同型超时 ⇒ INC-w35-timeout |
| T 档位 | 32K/256K/512K/1M | ⚠️ | 四档 readback 全成立 + 32K 软预算 27.9K；1M 满窗 NOT_EXERCISED |
| F1 vision | 多模态 | ✅ | 480×240 真图 + 非空语义题逐字 + 98.3% cache（答案未泄漏题面） |
| F2 workspace | 工具+审批 | ✅ | 审批 exact args + 设备侧 hex/mtime 逐字一致 |
| F3 skill | skill | ⚠️ | sentinel 逐字但 3 次未调 skill_load 工具卡；模型遵循差异非 app bug |
| F4 MCP | MCP | ⛔vacuous | 0 server、无外部 https 端点，记触发格 |
| F5 手动压缩 | 压缩 | ⚠️ | 走通菜单+对话框，但缺 dropped/kept 双向 log 判据 |
| F6 自动压缩 | 压缩 | ⚠️ | `net=-256` → **auto dropped=4 kept=10 applied** → already_compressed；坐实 W9-01 命题 |

### 4.3 判据订正与环境事件
- **正控先行**：E01/E03 strict 逐字证明 sentinel 判据会响；F1 用 480×240 真图（非 1×1）+ 排除答案泄漏；压缩用日志 + 序列双向判据。
- **INC-w35-timeout**：OR 免费池 E06/E07 同窗口超时、设备 ping 通、NV 可用 ⇒ 供给侧。
- **INC-w35-supervl-auth**：super-vl 旧 key 403、新 key 生效 ⇒ 授权面，撤销 W34「模型级永久拒绝」判断。
- **制品六类必红实跑**：w35 制品 31 键含 assertions 6 / mutation_cases 6 / mutation_selftest，判据 6 正常态 OK + 六类必红全 RED，不重蹈 W34「零断言虚报 5/5」。

---

## 五、四闸门

| 闸 | 结果 | 备注 |
|---|---|---|
| G1 编译 | ✅ rc=0 | `BUILD SUCCESSFUL in 1m23s`；G1b 删包重出 ×2 sha256 均 `b41be9cc…`，deterministic |
| G2 arch-guard | ✅ PASS | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0` |
| G3 selftest | ✅ 30/30 | 269s，后台跑满（前台会 SIGTERM 截断） |
| G4 fulltest | ✅ 818-0-0 | 14 模块、83 个 TEST-XML |

**CI**：主体 `8af4090` 一次推送；Build run `44583693258` + Secret scan run `44583693221` 均 **success**（四字段 completed/success/push/head_sha `8af40907154…` 匹配）。纯文档回填 tip 按降级规则处理（详见验收物 §5.6）。

---

## 六、OPEN 挂账

- **W35-01/02/03** 三行 `OPEN`：缓解动作已做、出口判据已实跑，按台账 §三.3 待外部审查复核后再销账。
- **W9-01**：行内补 F5/F6 全部真机证据与 F6 确定性根因（不写已修复、遵 W28 案 B 不作红态入口），维持 `OPEN`。
- **W17-02**：`promptContext()` 三处吞 CE 只登记不修（`.kt` 卫生批需用户裁决第 8 项），维持 `OPEN`。
- **W20-02** 拆分三方 / **W20-05**+**W9-11** 1M 真压 / **W5-03**+**W5-05**+**W5-06** MCP / **W9-05**+**W24-03** journal：沿用老号，本波不动。
- **W34-03**：残余项计数订正为现役实跑 31 处 / 13 文件（案 b 维持删除，不逐处抬头、不新增判据 8）。

---

## 七、交给外部审查团队的制品

- `docs/analysis/w35-acceptance.md`（本波验收物）
- `evidence/w35-matrix-summary.json`（正式制品，cells[] 带 state/feature_face/glyph/evidence_ref；31 键含六类必红实证）
- `evidence/raw/w35/`（38 件脱敏真机证据）
- `docs/06-deferred-ledger.md`（W35 段 3 行 + 8 行销账补指路 + W9-01 注记 + W34-03 计数订正）
- `docs/notes/w29-closeout-checklist.md`（判据 6 六类必红 + REPO_ROOT 兜底；判据 7 本波指路必红）
- `evidence/w34-matrix-summary.json`、`docs/analysis/w34-acceptance.md`、`docs/analysis/w33-acceptance.md`（W35-01/03 订正）

**不含**：会话历史 / 凭据 / 端点完整标识（模型 ID 仅公开名）/「建议下一链做什么」。

---

## 八、边界声明（不把推断写成事实）

- 零 `.kt`：310 个 `.kt` 零 diff。
- F6：只记录「net=-256 → auto dropped=4 applied → already_compressed」序列与两个确定性命题，不下缺陷结论、不写成已修复、不作唯一 untouched 分支断言。
- 1M：只做四档 readback 与 32K 软预算实测；1M tokens 满窗真压记 `NOT_EXERCISED`。
- MCP：0 server，`F4_mcp` 记 `VACUOUS`，不推断「MCP 不可用」。
- super-vl 403 / OR 超时：归 INC（授权面 / 供给侧），不推断为 harness 缺陷。
- 真机席跑到 turns 上限（1h08m）中断于制品汇编前：其已落盘 34 件原始证据，主理人据实补编 F5/F6 结构化证据（+2 JSON）与制品、修 F2 虚报引用、补 iter14-16（均标注来源），最终 `evidence/raw/w35/` 共 38 件（13 json + 19 png + 5 txt + 1 jsonl）；不编造未跑的格。

> 本波止于交接，交由外部团队审查。
