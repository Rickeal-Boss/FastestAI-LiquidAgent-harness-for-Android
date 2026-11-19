# W32 验收物 — 四外审分歧裁决（不改方向）+ W32 段第 0 车零 `.kt` 收口 + 多模型×多功能面真机矩阵

> 上游依据：用户 2026-11-19 指令（读记忆/上一波 handoff/挂账行、四审查报告分歧裁决不改方向、多模型×多上下文×多功能面真机矩阵、先本地 UI 加 adb 调试→真机→推仓库加 CI、收尾契约止于交接）。
> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，分支 `main`）。台账权威版 = `docs/06-deferred-ledger.md`。
> 本波起点 tip：`7b10cc8`（W31 主体 `453b8b5` + docs 回填 `7b10cc8`）；本波 tip 见 §5.6（推送后回填 commit 与 CI run）。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」。

---

## §一 本次任务「做什么 / 明确不做」

### 1.1 做什么
1. **四份外审报告分歧裁决**（莫斯线 W26–W31 增量 / W32 段挂账草案 / W31 深度审查与 W32 排期裁决 / W31 深度审查加挂账分析加 W32 路线建议），**不改方案大体方向**，逐条 grep 现役树核实事实后标 `改方向=是/否`。
2. **W32 段第 0 车零 `.kt` 收口**：台账 W32-01/02/03 三行（W31-01 双判据失效 / W31 制品 schema 退化 / 收尾契约第 11 次）缓解动作 + 必红变异实跑，W31-01 先回 `OPEN` 再真修销账。
3. **多模型×多功能面真机矩阵**：本地 UI 加 adb 调试 → 真机 → NVIDIA 免费节点（Lightning / Bolt / Nano Omni / `nvidia/nemotron-3.5-super-vl-120b-a12b` 1M）加 OpenRouter（`dots-3-note-preview:free` 512k 工具+视觉双通 / `north-mini-code:free`），覆盖端点连通 / 档位直至 1M / 多模态输入 / workspace 读写 / skill / MCP / 手动加自动上下文压缩 / 审批闸门。
4. **四闸门 + 推送 + CI 复核 + 收尾契约**：G1 编译 / G2 arch-guard / G3 selftest / G4 fulltest 全绿后推 `main`，CI 四字段复核，止于交接。

### 1.2 明确不做（逐条带挂账号）
- **不动 `.kt`**：本波全零 `.kt`（工具循环 / 审批 / 压缩 / 端点全部现役已工作协议）；拆分三方与 B1 压缩整包顺延，挂 `W20-02`。
- **不新建第 5 份 checklist / 第 6 份制品**：只改 W31/W30/W29 现有载体 + 本波验收物与矩阵证据（工作区 `_w32/`）。
- **不为「未行使」编造通过**：F1/F3/F4/F5/F6 等未行使面一律记 `⛔vacuous` 并带触发信号，不冒充 PASS。
- **不把环境问题写成 harness 缺陷**：WiFi 抖动 / 密钥 UI 录入流程归 INC，不挂账到代码。
- **不回灌凭据与整段 logcat**：截图只回灌脱敏判据、deviceLog 只回灌 tail；密钥只进真机 AndroidKeyStore，不落仓 / 文档 / 证据。
- **不代做六项用户终局裁决**：转代码波 / 矩阵收官、journal 二选一、G20 扫描面、借鉴独立输入面、tip run 闭环、历史四项——只登记不代做，挂 `W24-03`（journal）、`W20-02`（拆分）。
- **不新开 `W32-04+` 承接老债**：六行漏号 / G10 贴顶 / 安全包沿用老号留痕（见台账 §一既有行）。

---

## §二 第 0 车零 `.kt` 收口结果（W32-01/02/03）

> **口径（W33 补注）**：本节四态指**缓解动作实跑结果**，不是挂账状态；三行仍 `OPEN`，销账待外部审查复核（见 §5.3）。W30 三行同理。

> 四报告一致认定起点：W31-01 的 `CLOSED` 按字面不成立（判据双向失效）。主理人采纳前先 grep 现役树复跑，
> 全部 P0 成立后再落笔；W31-01 先回 `OPEN`，真修后按台账 §三.3 单人落笔销账。

### 2.1 W32-01 脱敏判据双向失效 — ✅（真修完成，出口判据实跑）
| 项 | 结果 | 实跑证据原文 |
|---|---|---|
| 3b 正常态从 3 命中→0 | ✅ | 起点 `grep -rniE 'openrouter\.txt\|nvidia\.txt' docs evidence` 实跑 3 命中（验收物结果列 + 制品 target）；改拼装后复跑输出 `OK no credential filename`（0 命中） |
| 必红注入 `%%` 哑火→真响 | ✅ | 起点 `printf 'ref: nvi%%sdia.txt\n' ''` 产出 `nvi%sdia` 不含字面量（必红 0 命中）；改两段拼装 `printf 'ref: %s%s\n' 'nvi' 'dia.txt'` 实跑产出完整字面量、grep 输出 `RED credential filename hit`（正控 1 命中） |
| 设备序列号字面回写 | ✅ | 起点验收物 `:33` 1 命中；改「设备序列号（已打码）」后 `grep <设备序列号（已打码）> docs evidence` 0 命中 |
| 双原文入仓 | ✅ | `docs/notes/w29-closeout-checklist.md:248-265`（正常 `OK` + 必红 `RED` 两条原文；行号以现役树实测为准，W33 订正原 `:244-257`） |
| 隐私新增行扫描 | ✅ | `git diff -U0 \| grep '^+' \| grep -iE <凭据模式>` 新增行 0 命中 |

### 2.2 W32-02 W31 制品 schema 退化 — ✅（14→29 键）
| 项 | 结果 | 证据 |
|---|---|---|
| 补回字段 | ✅ | `schema`/`cells_total`/`declared_feature_faces`/`iterations[]`/`iterations_total`/`attempt_states`/`coverage_note`/`cells_provenance` 全在；9 格补 `feature_face` 机器枚举 |
| 删 `FAIL:0` 零值键 | ✅ | `state_counts = {PASS:3, PARTIAL:2, VACUOUS:4}`，`K_state_domain` 实跑「非零键集 == cells 值域」通过 |
| 断言/必红对齐 | ✅ | `K_state_domain`/`K_feature_enum`/`K_iteration_consistency` 三条断言 + 三条结构化必红样本，`mutation_selftest total=3 rejected=3` |
| MCP 面 | ✅ | 显式 `mcp: NOT_EXERCISED`（无外部 server），消除「§1.1 声称覆盖 MCP 而制品无格」；方案席确认口径 |
| 迭代链不编造 | ✅ | `iterations[]` 6 条由 W31 验收物 §3 事件链派生、逐条标 `derived_from`；`iterations_total == len(iterations[]) == 6`、`attempt_states` 键集 == cell_id 值域 |
| baseline 代数口误 | ✅ | 订正为「W29 全 VACUOUS、W30 3 PASS/2 PARTIAL/3 VACUOUS、W31 首次工具链闭环」；handoff §3.1 同句 |

### 2.3 W32-03 收尾契约事实错 — ✅（零 .kt 一次纯文档）
| 项 | 结果 | 证据 |
|---|---|---|
| 「单次推送」事实错 | ✅ | 验收物 `:144` 改「主体 `453b8b5` + 纯文档回填 tip `7b10cc8` 推送 2 次」；tip run 按降级规则只留痕不判 P0、不补链接 |
| G20 写死数 | ✅ | 验收物 `:98/:139`、handoff `:77` 改「以 `arch-guard` 实跑为准、不写死数」；handoff 中复合编号全在台账行首（sanity 0 漏） |
| PAT「须轮换」句 | ✅ | 改「已闭环动作；轮换属用户侧动作」，对齐 W24 `:384` 先例 |
| §5.2 四条补挂账号 | ✅ | F1→`W14-11`、F5/F6→`W21-04`、1M→`W20-05`、OpenRouter→`W21-04`（老号均台账存在 1 行） |
| handoff 抬头 / W24-03 | ✅ | 补列 `W28-03`；`W24-03` 统一「多值待裁」 |
| W30 三行消沉默 | ✅ | 三行加 W32 行内注记（明示未满足出口条件），状态维持 `OPEN` 不改——方案席裁决：关旧行须出口判据真满足，本波不代关 |

> 质量审查席独立复核结论见 §5.1；判据真响 vs 空转以审查席实跑输出为准。

---

## §三 多模型×多功能面真机矩阵（`_w32/matrix-facts.jsonl`）

> 权威证据 `_w32/evidence/W32_SUMMARY.json`（汇总层）+ `evidence/w32-matrix-summary.json`（制品层，cells[] 带 state/feature_face/glyph）。
> 判据纪律：未行使一律 `⛔vacuous` 带触发信号，不冒充 PASS；正控先行；截图只回灌脱敏判据、deviceLog 只回灌 tail。
> `state_counts = {PASS:8, PARTIAL:3, VACUOUS:3}`，无 `FAIL:0` 零值键（W28-02/W31 空转键纪律）。

### 3.1 端点轴（E）
| 格 | 面 | 状态 | 关键实测 |
|---|---|---|---|
| E01 Lightning | 端到端正控 | ✅ | `W32-FINAL-LIGHT-9D4K` 严格逐字（排除输入气泡假阳性）、思考+输出气泡、1.8k in/73 out、无失败横幅 |
| E02 Bolt | 端到端 | ✅ | `W32-PROBE-BOLT-8R1T` 逐字、header 确认、1.4k in/6 out（`e2e_bolt2.json` 为准，首测为 Nano 回已订正） |
| E03 Nano Omni | 端到端 | ✅ | 1.8k in/126 out、reasoning 型、header 当时确认；⚠️ caveat=未做末次严格气泡复核，但 usage 变化型硬证据充分 |
| E05 super-vl 120B (1M) | 端点新增 + 端到端 | ⚠️ | 端点新增成功（`contextLength=1048576`、密钥 14:15 落盘），两次端到端 `HTTP 403 authentication failed`（模型级授权 INC-w32-auth，不冒充 PASS） |
| E06 OR dots3 | 端到端 | ✅ | 录 `or-dots3.bin`(14:57) → `W32-ORDOTS-R2` 逐字、1.6k/48、`上下文 1.7K/520.2K` |
| E07 OR north | 端到端 | ✅ | 录 `or-north.bin`(14:59) → `W32-ORNORTH-7M3P` 逐字、1.1k/242 |

### 3.2 档位轴（T）
- **T tier ⚠️**：档位 sweep 选项 32K/128K/192K/256K/512K/1M；1M 档 readback=`当前 1M`；Lightning@1M 档计量条 `上下文 1.9K / 127K`。**档位=会话输入预算 ≠ 端点 contextLength**（super-vl 端点 1M、dots3 端点 512K），未编造 995.9K。
- **1M 大上下文真压 ⛔vacuous**：单列声明（`full_context_1M_stress: NOT_EXERCISED`），触发信号 = super-vl 两次 403 + 短对话字符维未越阈（缺 16 轮配方）；均非 harness 缺陷。

### 3.3 功能面（F）
| 格 | 面 | 状态 | 关键实测 |
|---|---|---|---|
| F1 多模态视觉 | 真图 SAF + 语义回读 | ⚠️ | 480×240 真图（RED 矩形/GREEN 圆/W32VISION 文字，非 1×1）→ SAF 选图 → 附加 → 发送链路全通；Bolt 回 `This is the final answer`(6 out) 语义回读失败，不冒充 PASS |
| F2 workspace 读写 | 工具真机落盘 | ✅ | `file_write` → 真实点按「允许一次」1 次 → `w32probe.txt` 落盘逐字 `W32WS-F2-7K3` → `file_read` 复核 |
| F3 skill | Skill 加载 | ✅ | `@notes-organizer` 菜单触发、模型采用 skill 语义、81.8% cache hit（skill 判据=采用语义，sentinel 逐字不适用） |
| approval_gate | 审批闸门事件链 | ✅ | tool_calls → 审批 → 真实点按「允许一次」→ 执行 → 终答 |
| F4 MCP | 外部端点 | ⛔vacuous | 设备显示「还没有 MCP 服务器」；制品顶层显式 `mcp: NOT_EXERCISED` **并留 `F4_mcp` VACUOUS 触发格**（格只登记未行使，不冒充覆盖；W33 口径统一，删格/删字段两条必红） |
| F5 手动压缩 | 手动压缩 | ⛔vacuous | 「更多操作→压缩上下文」入口可点（正控），短对话未越 committedChars 阈；触发 = 需 16 轮长对话 |
| F6 自动压缩 | 自动压缩 | ⛔vacuous | 9 条 `auto_compact=noop reason=below_trigger`，字符维未越阈 = 正确行为非缺陷，未造假 dropped/kept |

### 3.4 正控与判据订正
- **正控**：Lightning `W32-FINAL-LIGHT-9D4K` 严格逐字回读（无失败横幅）+ 初轮证明「注入→响应→UI 回读→日志落痕」判据链会响，之后才采信各格。
- **判据缺陷已修正**：早期用 `sentinel in UI 全集` 被用户输入气泡（题面含 sentinel 子串）污染成假阳性；收紧为 `strict_sentinel`（排除 `Reply_with_*` 输入气泡与「请求失败/HTTP4xx」横幅，只看纯 token 输出气泡 + usage）。归因订正：`e2e_bolt.json` 首测实为 Nano 回（由 `e2e_bolt2.json` header 确认 Bolt 取代）；`nano_final.json` 因切换失败实为 OR North（从 E03 移除归 E07）。`W32_SUMMARY.json.notes` 列明全部以哪条证据为准。
- **计数订正**：汇总原 `PASS=7` 漏计 `approval_gate`，实为 8；8+3+3=14=cells 数。

### 3.5 环境事件（归 INC，非 harness 缺陷）
- **INC-w32-keyentry**：OR 两端点标 `hasSecret` 但 bin 缺失 → `missing API key for endpoint`（前端无前置校验/明确提示，登记为可用性观察，挂老号 `W21-04` 矩阵未达成格，不新开行）；按 W31 流程录入后 PASS。
- **INC-w32-auth**：super-vl 120B 两次 `HTTP 403 authentication failed`（模型级授权，同 key 其它 NV 端点可用）。
- **INC-g1-reinstall**：主理人跑 G1 编译闸门时 `localbuild.sh` 默认任务含真机安装（`pm install -r` + `monkey`），重置了真机 UI 状态；本波零 `.kt`、APK 字节与 W31 一致，真机席重开对话续测，已完成证据保留。非 harness 缺陷。

---

## §四 四闸门

| 闸 | 命令 | 结果 | 证据 |
|---|---|---|---|
| G1 编译 | `bash scripts/localbuild.sh`（本波零 `.kt`） | ✅ rc=0 | `app-debug.apk` + `app-release.apk` 出包；⚠️ 默认任务含真机安装（`pm install -r` + `monkey`），已如实通报真机席记 INC-g1-reinstall（环境事件，非 harness 缺陷） |
| G2 arch-guard | `bash scripts/arch-guard.sh`（仓根） | ✅ PASS | 质量审查席实跑 `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`；W31-01 回 `OPEN` 后 CLOSED 行证据非空仍绿 |
| G3 selftest | `arch-guard-selftest.sh`（后台 256s） | ✅ 30/30 | `_w32/g3_selftest.log`；VAC-G1b / VAC-G21 空转机制自证 PASS |
| G4 fulltest | `fulltest.sh`（eval env 后） | ✅ 818-0-0 | `_w32/g4_fulltest.log`；14 模块全覆盖、`failure=0 error=0 skipped=0`，与基线一致（零 `.kt`） |

---

## §五 收尾契约

### 5.1 各步四态汇总
1. **四外审分歧裁决** ✅ — 方案席沈思远真实回传：7 条全部「不改方向」、处方逐条采纳（详见本节下方裁决表）；我据其裁决补两缺口（断言/必红 3=3=3、iterations_note 防误读条款）。**过程教训**：主理人曾误把「进行中」当「已回传」，已纠正并写入日志；本条以成员真实 `SendMessage` 回传为准。
2. **第 0 车收口** ✅（缓解维度）— W32-01/02/03 三行缓解动作实跑（§二）；质量审查席严质衡 6 项通过 + 1 项 P1（3 处声称未落地）已真修，复核 ✅；守卫 G2 实跑 PASS。**注意「缓解 ✅」≠「销账」**：三行挂账状态仍 `OPEN`（见 §5.3），销账待外部审查复核。
3. **真机矩阵** ✅ — 14 格完整矩阵见 §三（8 PASS / 3 PARTIAL / 3 VACUOUS），正控与判据订正见 §3.4，环境事件见 §3.5；终态计数以 `evidence/w32-matrix-summary.json` 与 §3.1/§3.3 为准。

**方案席裁决表（原文回传：7 条「不改方向」、处方逐条采纳）**：

| # | 分歧点 | 裁决 | 落地 |
|---|---|---|---|
| 1 | 四报告总体方向 | 否（维持零 `.kt` + 现有载体收口） | §二 |
| 2 | W31-01 回 `OPEN` | 否（不改方向；回 OPEN 正确，CLOSED 不成立） | 台账 W31-01 |
| 3 | `evidence/SCHEMA.md` 是否新建 | 否（本波不新建，与「不新建第 6 份制品」冲突；制品内自包含 + 验收物注记） | 制品 29 键 |
| 4 | W30 三行处置 | 否（维持 `OPEN` + 行内注记消沉默，不代关） | 台账 W30-01/02/03 |
| 5 | MCP 面表述 | 否（显式 `mcp: NOT_EXERCISED` **并留 VACUOUS 触发格**；W33 统一口径） | 制品 mcp 字段 + `F4_mcp` 格 |
| 6 | `iterations[]` 派生 | 否（标 `derived_from` 不算编造；**须补防误读条款**：`iterations[].state` 是事件链重述、非独立取证计数，档位/1M/vision 以 cells[] + 真机日志为准） | 制品 iterations_note |
| 7 | 端点/1M 判据空命中语义 | 否（档位=会话预算≠contextLength；1M 只报实测读数；多模态禁 1×1） | §三 |

> 方案席两处缺口已按铁律核实后闭合：①「空 schema_note」在终态树不存在（只有 `schema` 字符串），系其中间态快照；② 断言 3 vs 必红 2 已补为 3=3=3。
4. **四闸门** ✅ 见 §四（G1–G4 全绿；终态实跑值见 §5.5）。
5. **推送 + CI** 见 §5.6。

### 5.2 明确未做（逐条带挂账号）
- 六项用户终局裁决（转代码波/矩阵收官、journal 二选一、G20 扫描面、借鉴独立输入面、tip run 闭环、历史四项）— 只登记不代做，挂 `W24-03`、`W20-02`（见台账既有行）。
- 需 `.kt` 的拆分三方 / B1 压缩 / 两缺陷修复 — 整包顺延，挂 `W20-02`。
- 1M 真压（非档位读数）/ MCP 明文 / git 历史清理 — 维持不代做。

### 5.3 OPEN 挂账（触发信号 + 缓解动作 + 挂账号）
本波落 W32 段 3 行全 `OPEN`；既有活跃挂账沿用台账现值：
- **W32-01**：触发 = 3b 判据双向失效复发；缓解 = 源头删字面量 + 两段拼装必红 + 双原文入仓（已做，待重新销账）；挂 `W32-01`。
- **W32-02**：触发 = 制品 schema 反向退化 + `FAIL:0` 零值键；缓解 = 14→29 键 + 断言/必红 3=3=3（已做）；挂 `W32-02`。
- **W32-03**：触发 = 收尾契约第 11 次 + 派生量写死；缓解 = 事实订正已做；挂 `W32-03`。
- **W24-02 / W20-02 / W17-02 / W24-03** 老号沿用，行内注记见台账。

### 5.4 文档回写 diff
本波回写清单（详见 §5.6 commit）：
- `docs/06-deferred-ledger.md`：W31-01 回 `OPEN` + W32 复核注记 + W32 段 3 行 + W30 三行消沉默注记 + W31 段头订正。
- `docs/analysis/w31-acceptance.md`：脱敏拼装化 + §5.6 事实订正 + G20 不写死 + PAT 闭环 + §5.2 补挂老号。
- `docs/handoff-20261119-wave31.md`：baseline 订正 + G20 不写死 + 抬头口径订正 + W24-03 多值待裁。
- `docs/notes/w29-closeout-checklist.md`：必红块 `%%`→两段拼装 + 双态摘录 + 判据 1 的 W31 段输出。
- `evidence/w31-matrix-summary.json`：14→29 键 + 删 `FAIL:0` + feature_face + MCP NOT_EXERCISED + 3 断言。
- 新增 `docs/analysis/w32-acceptance.md`；真机证据在工作区 `_w32/`（不入库）。
- **W33 补**：14 格 `evidence_ref` 全部改指仓内 `evidence/raw/w32/`（脱敏原始件，26 个文件入仓；设备序列号与本机路径已替换为占位），外部审查可逐格复核。

### 5.5 推送前闸门实跑
- **G1 编译** ✅ rc=0（`_w32/g1_build.log`）；默认任务含真机安装，见 §3.5 INC-g1-reinstall。
- **G2 arch-guard** ✅ 终态仓根实跑 `_w32/g2_final.log`：`OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`、`result=PASS`；G20「handoff ID 全在台账内、CLOSED 行证据均非空」（W32 三行 + handoff 引用老号全在行首；**计数以 `arch-guard` 实跑为准，真值源不写死派生数**）。
- **G3 selftest** ✅ 30/30（256s，`_w32/g3_selftest.log`）。
- **G4 fulltest** ✅ `tests=818 failures=0 errors=0 skipped=0`（14 模块，`_w32/g4_fulltest.log`）。
- 本波全零 `.kt`，未触碰行数闸门/模块边界/守卫脚本 ⇒ tap 口径与 selftest 基线不变。

### 5.6 commit 与 CI run
- **推送 2 次**：主体 `9eec391`（含 docs + evidence + 制品，推送 1）+ 纯文档回填 tip `eaf5eff0b`（推送 2，回填本节 commit 与 CI run 链接；tip run 按既定降级规则只留痕、不逐波判 P0、不补链接）。
- **CI（head_sha = `9eec391`，四字段复核 completed / success / push / `9eec391`）**：
  - **Build** run `44499935283`：completed / **success** — <https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44499935283>
  - **Secret scan** run `44499935313`：completed / **success** — <https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44499935313>
- CI 红 0；PAT inline 用后 `git remote set-url` 清干净（已闭环动作；轮换属用户侧动作，不在仓内写未完成命令）。工作树仅剩 2 份历史未跟踪审查笔记（沿用既定口径不入库）。
- **tip（本提交，纯文档回填）**：本提交回填 §5.6 主体 commit 与两条 CI run 链接；按既定降级规则 tip run 只留痕、不逐波判 P0、不在仓内补链接，tip SHA 与 Secret scan run 号随交付消息给出（不第三次推送以避免自指回填循环，沿用 W29/W30/W31 惯例）。

### 5.7 交给外部审查团队的制品清单
- `docs/analysis/w32-acceptance.md`（本验收物）
- `docs/06-deferred-ledger.md` W32 段
- `evidence/w31-matrix-summary.json`（W32 订正后）
- `docs/notes/w29-closeout-checklist.md`（判据 3b 双态原文 + W31 段输出）
- `_w32/matrix-facts.jsonl`（真机矩阵证据，脱敏后随制品清单给出路径；不入库）
- **不含**：会话历史 / 凭据 / 端点完整标识（一律模型 ID + 打码）/「建议下一链做什么」。

> 本波止于交接，交由外部团队审查。
