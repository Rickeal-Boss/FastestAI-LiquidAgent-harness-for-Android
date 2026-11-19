# W33 验收物 — 六报告分歧裁决（不改方向）+ 第 0 车零 `.kt` 收口 + 多模型×上下文直至 1M×多功能面真机矩阵

> 上游依据：用户 2026-11-19 指令（读记忆 / 上一波 handoff / OPEN 挂账行、读六份审查报告并裁决分歧、不改方案大体方向、多模型×不同上下文直至 1M×功能面〔多模态 / workspace / skill / MCP / 手动加自动压缩〕、先本地 UI 加 adb 调试→真机→推仓库加 CI、收尾契约止于交接）。
> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，分支 `main`）。台账权威版 = `docs/06-deferred-ledger.md`。
> 本波起点 tip：`eaf5eff`（W32 主体 `9eec391` + docs 回填 `eaf5eff`）；本波 tip 见 §5.6（推送后回填）。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」。

---

## §一 本次任务「做什么 / 明确不做」

### 1.1 做什么

1. **六份审查报告分歧裁决（不改方案大体方向）**：对《W33 段挂账草案》《W32 深度审查与 W33 排期裁决》《source-review-consolidated》《W32 深度审查与挂账分析及 W33 路线建议》《harness-w30–32 review》《W30–W32 深度审查与挂账分析》逐条回源码/实测核实，标 `改方向=是/否` 并给落点；**采纳两份报告共同指出的 P0：把 W32 制品 14 格 `evidence_ref` 指向仓内真实脱敏证据**（原 14/14 指向不存在文件，PASS 格不可独立复核）。
2. **W33 段第 0 车零 `.kt` 收口**：`W33-01`（收尾契约第 12 次：W32 验收物 4 处占位/事实错/写死派生数）、`W33-02`（W31-01 与 W32-01 双行同义 + 无销账联动 + 「缓解 ✅」与「挂账 OPEN」两套口径）、`W33-03`（W32 制品 `K_feature_enum` OR 恒真 + F4_mcp 格与文档矛盾 + 证据不可复核）；同车收 P1：checklist 抬头白名单措辞、README 真值源指向、roadmap 时点抬头、「7 条全否」歧义、W32-01 证据列行号、两制品 glyph 统一、新增判据 6/7。
3. **多模型×不同上下文（直至 1M）×多功能面真机矩阵**：NVIDIA `build.nvidia.com` 免费节点（Lightning / Bolt / Nano Omni / super-vl 120B 1M）加 OpenRouter（`dots-3-note-preview:free` 512k 工具+视觉双通 / `north-mini-code:free`）；覆盖端点回归、1M 档位与长输入、多模态语义回读（IMAGE 端点）、workspace 文件读写、skill、MCP 状态、审批闸门、**手动压缩（F5）与自动压缩（F6）**——F5/F6 是 W30–W32 三波零行使的两格，本波按 W23 配方复刻。
4. **四闸门 + 推送 + CI 复核**：G1 编译 / G2 arch-guard / G3 selftest / G4 fulltest 全绿后推 `main`，CI 四字段复核并回填 §5.6。
5. **收尾契约**：W33 验收物 + `docs/handoff-20261119-wave33.md` + 台账 W33 段 3 行 + W30 三行判据实跑入账 + 文档回写 diff + 外审制品清单，止于交接。

### 1.2 明确不做（逐条带挂账号）

- **不动 `.kt`**：本波全零 `.kt`；拆分三方 / B1 压缩四条 / `W24-02` 断词 / 四态生产读者等代码波内容全部顺延，挂 `W20-02`、`W24-02`、`W12-02`。
- **不做 1M tokens 真压**：本波只做 1M 档位 readback + 单次长输入（>32K 档字符 cap 的输入被 1M 档接受），1M tokens≈4M chars 的真压不在本波范围，挂 `W20-05`。
- **不做 MCP 真机联调**：设备无外部 MCP server 端点（明文被 G15b 拒），`F4_mcp` 维持 `⛔vacuous` 带 `NOT_EXERCISED` 与触发格，挂 `W5-03`。
- **不新建第 6 份 checklist / 第 7 份制品**：只在现有 `docs/notes/w29-closeout-checklist.md` 增判据 6/7，只改 W32/W31 现有载体与 W33 矩阵制品。
- **不代做六项用户终局裁决**：转代码波/矩阵收官、journal 二选一、G20 扫描面、AndroidHarness 借鉴、tip run 闭环、历史四项——只登记不代做，挂 `W24-03`、`W20-02`。
- **不回灌凭据与整段 logcat**：截图只回灌脱敏判据、deviceLog 只回灌 tail；密钥只进真机 AndroidKeyStore，不落仓/文档/证据。
- **不新开 `W33-04+` 承接老债**：六行漏号 / G10 贴顶 / 安全包沿用老号留痕。
- **不把环境问题写成 harness 缺陷**：super-vl 403（模型级授权）、网络抖动、UI 录入流程归 INC。

---

## §二 六报告分歧裁决（不改方案大体方向）与第 0 车收口

### 2.1 裁决总表（改方向=否；逐条给落点）

| # | 分歧点（报告来源） | 核实（现役树实测） | 裁决 | 落点 |
|---|---|---|---|---|
| 1 | **真机证据不可复核**：W32 制品 14 格 `evidence_ref` 全部指向仓内不存在文件（报告 6 P0-A；报告 4 独立发现） | 实测 14/14 MISSING；原始件在 `_w32/evidence/`（0.6–2.7KB、已脱敏） | **否（采纳，补 P0）**：脱敏证据入仓，`evidence_ref` 改指真实文件；它们不是「第 7 份制品」 | `evidence/raw/w32/` 26 文件；W32 制品 14 格 + 18 条 iterations 引用全部改写 |
| 2 | **W30 三行：实跑即关 vs 维持 OPEN**（报告 4 vs 报告 2） | 台账 §三.3 要求「仓内判据实跑输出入账 + 制品与真值源一致 + 外部审查复核」 | **否（部分采纳）**：实跑判据 2/4/5 输出入账 + 行内注记；状态维持 `OPEN`，只差外审复核 | 见 §2.4 与台账 W30 三行注记 |
| 3 | **W31-02/03 延迟复核**（报告 4） | 逐条实跑：W31 制品 29 键 / 3=3=3 / iterations 6=6 / glyph 三键；W31 验收物占位仅剩订正注记内历史指称；G20 不写死 | **否（采纳）**：复核未塌，维持 `CLOSED` + W33 复核注记 | 台账 W31-02/W31-03 行内注记 |
| 4 | **判据 7（CLOSED 须带实跑指路）入 checklist**（报告 4） | 与「不新建第 6 份 checklist」不冲突：现有 checklist 加判据 | **否（采纳）**：加判据 6（面定义 AND + evidence_ref）与判据 7（CLOSED 实跑指路），均带必红 | `docs/notes/w29-closeout-checklist.md` 判据 6/7 |
| 5 | **`matrix_vs_gate_order` / `localbuild.sh` install 分离 / 前置表模型级授权行**（报告 4） | 实测 `localbuild.sh` **不自动安装**（只打印 `pm install -r` 提示）；W32 的 INC-g1-reinstall 是主理人照提示手动装 | **否（降级）**：字段采纳（W32/W33 制品）；install 分离**不成立**（无自动安装逻辑）⇒ 只澄清文档；模型级授权行采纳 | 制品 `matrix_vs_gate_order`；W33 验收物 §3.5 |
| 6 | **3b 必红样本哈希+规则入仓**（报告 6 P0-B） | 与草案「本波不再改字面量口径」冲突 | **否（部分采纳）**：不改判据口径；把注入样本的 sha256 与拼装规则登记进 checklist W33 摘录（不落明文） | checklist「W33 判据 3b 样本登记」 |
| 7 | **四报告/裁决出处入仓**（报告 6 P0-C） | 六份报告正文在仓外；入仓等于新建制品 | **否（不代做）**：验收物给路径 + 结论摘要；正文是否入仓属用户裁决（新建制品） | §5.2 明确未做（挂 W33-03 + 用户裁决域） |
| 8 | **F1 绑定 IMAGE 端点，[TEXT] 端点进 na_allowlist**（报告 5） | 端点实测：`or-dots3`/`nv-bolt`/`nv-nano-omni` inputModalities 含 IMAGE；`nv-lightning`/`nv-north` 仅 TEXT | **否（采纳）**：F1 在 `or-dots3`（IMAGE 端点）跑并语义回读；[TEXT] 端点不设 F1 格 | §3.3 F1 格 |
| 9 | **F5/F6 配方**（报告 5：16 轮短消息 / committedChars 越 50227） | 阈值常量 `COMPACT_TRIGGER_CHARS=50_227`（32K 档 floor cap）；手动压缩不受该阈约束、按预算压 | **否（采纳）**：18 轮 × ~2.4k chars 累积，先手动（自动 OFF）再自动（自动 ON） | §3.3 F5/F6 格 |
| 10 | **顶层键数环比进常规检查**（报告 4） | W31 制品 29 键 → W32 制品 31 键 → W33 制品 30 键 | **否（采纳为方法）**：写入验收物 §3.6；不新增判据以免 checklist 膨胀 | §3.6 |
| 11 | **source-review 的 P1×3 需 `.kt`**（报告 3） | 四态生产读者（`ContextBudget.cachedLabel` 三态字符串）、装饰字段、`syncMcpTools` 吞 CE 均为源码改动 | **否（不代做）**：零 `.kt` 边界，整包顺延 | 挂 `W12-02`、`W9-06`、`W17-02` |
| 12 | **P1 同车：README 真值源 / roadmap 抬头 / 「7 条全否」措辞 / checklist 白名单措辞 / W32-01 行号 / glyph 统一**（报告 2） | 逐条核实现役树行号（checklist 摘录实际 `:248-265`） | **否（采纳）**：全部同车收 | §5.4 文档回写 diff |
| 13 | **W33 三行草案**（报告 1） | `W33` 号段 0 行；三行各带可跑出口判据 | **否（成立）**：`W33-02` 取方案 a（加主从联动注记、不合并行）；`W33-03` 取方案 b（保留 `F4_mcp` 触发格、文档改口径）——两处「二选一」均标注用户可改判 | 台账 W33 段 |

> 说明：第 2、13 条中的「二选一」本波按最小改动/最少丢证据原则选 a/b，均在台账行内标注「用户可改判」，不视为代做用户裁决。

### 2.2 W33-01 收尾契约第 12 次（✅ 缓解完成，挂账 OPEN）

| 项 | 结果 | 证据 |
|---|---|---|
| `:126` 占位 | ✅ | 改「真机矩阵 ✅ — 14 格完整矩阵见 §三（8/3/3）…」 |
| `:141` 占位 | ✅ | 改「四闸门 ✅ 见 §四（G1–G4 全绿；终态实跑值见 §5.5）」 |
| `:173` 一次推送 | ✅ | 改「推送 2 次：主体 `9eec391` + 纯文档回填 tip `eaf5eff0b`」 |
| `:167` 写死 126 | ✅ | 改「handoff ID 全在台账…计数以 `arch-guard` 实跑为准，真值源不写死派生数」 |
| 出口判据 | ✅ | `grep -n "待真机席回传\|其余待跑\|一次推送" w32-acceptance.md` → 0 命中（见 §2.5 实跑） |

### 2.3 W33-02 / W33-03（✅ 缓解完成，挂账 OPEN）

- **W33-02**：`W31-01`/`W32-01` 两行各加「主从联动」；W32 handoff §三加表注（四态=缓解动作、非挂账状态）；W32 验收物 §二/§5.1 注明；`W32-01` 证据列行号订正为 `:248-265`。
- **W33-03**：W32 制品取方案 b（保留 `F4_mcp` VACUOUS 触发格 + 文档改「NOT_EXERCISED 并留触发格」）；`K_feature_enum` OR→AND 并新增 `K_feature_enum_no_cell_claim`；四条必红实跑；14 格与 iterations 的 `evidence_ref` 改指 `evidence/raw/w32/`（26 个脱敏文件入仓）；两制品 glyph 统一；W32 制品补 `matrix_vs_gate_order`。

**判据 6 实跑（正常态 + 四条必红）**：

```text
[判据 6 正常态 W32] OK
[判据 6 必红 K_state_domain/add FAIL:0] RED zero-value keys: ['FAIL']; state_counts keys != cell states
[判据 6 必红 K_feature_enum/delete mcp field] RED mcp field missing
[判据 6 必红 K_feature_enum_no_cell_claim/remove mcp cell while mcp.cell claims it] RED mcp claims cell F4_mcp but cells[] lacks it
[判据 6 必红 K_iteration_consistency/delete middle iteration] RED iterations_total != len
```

### 2.4 W30 三行判据实跑入账（状态维持 OPEN，待外审复核）

```text
[判据 1·W30/W31/W32 段] W30 rows = 3 | W31 rows = 3 | W32 rows = 3
[判据 2 正常态] all(verified)=True | all_verified=True | w29_device_executed=False | K_positive_control.verified_by=inherited_w28_baseline
               OK(vacuous: positive control inherited, device NOT executed)
[判据 2 必红]   RED mutation invariant broken
[判据 3b 正常态] OK no credential filename
[判据 3b 必红]   RED credential filename hit（注入样本拼装产出，字面量不入仓）
[判据 4 正常态] w27-acceptance=0 | handoff-w27=0 | ledger=0
[判据 4 必红]   mut hits=1 → RED affirmative wording remain
[判据 5 正常态] iterations_total/len/sec4 = 6 6 6 | ok1=True | attempt_states keys == cell_ids=True | vacuous consistency=True → OK
[判据 5 必红]   RED iteration/state invariant broken
```

**销账条件对照**：`仓内判据实跑输出入账` ✅（上列原文摘录）＋`制品与真值源一致` ✅（W29 制品 state_counts/iterations/正控与验收物 §3 一致）＋`外部审查复核` ⏳（本波止于交接）⇒ 状态维持 `OPEN`，只差外审一步。

### 2.5 出口判据实跑（第 0 车）

```text
$ grep -n "待真机席回传\|其余待跑" docs/analysis/w32-acceptance.md
（0 命中）
$ grep -n "一次推送" docs/analysis/w32-acceptance.md
（0 命中；§5.6 含「推送 2 次」）
$ grep -n "126 个" docs/analysis/w32-acceptance.md
（0 命中）
$ python -c "check cells[].evidence_ref exists" → missing refs: NONE
$ grep -rn "旧 MCP 口径短语（W32 的『不塞触发格』表述）" docs/ → 除本行命令行自指外 0 命中（W33 口径：NOT_EXERCISED 并留触发格）
```

## §三 多模型×不同上下文（直至 1M）×多功能面真机矩阵

> 权威制品 = `evidence/w33-matrix-summary.json`（cells[] 带 state/feature_face/glyph/evidence_ref）；脱敏原始件 = `evidence/raw/w33/`（14 文件）。
> `state_counts = {PASS:8, PARTIAL:4, VACUOUS:1}`（13 格，无零值键）。正控 = OR dots3 / NV Lightning sentinel 严格逐字。
> 判据纪律：未行使不冒充 PASS；截图只回灌脱敏判据、deviceLog 只回灌 tail；密钥只在真机 AndroidKeyStore。

### 3.1 端点轴（E）
| 格 | 面 | 状态 | 关键实测 |
|---|---|---|---|
| E01 Lightning | 端到端回归 | ✅ | `W33-E01-R1` 严格逐字（排除输入气泡）、1.8k in / 66 out |
| E03 Nano Omni | 严格气泡复核 | ✅ | `W33-E03-R1` 严格逐字 + 无失败横幅、1.8k/63 —— **W32 的「未做末次严格复核」caveat 至此关闭** |
| E05 super-vl 120B (1M) | 端点 + 端到端 | ⚠️ | 端点可切为当前；`authentication failed (HTTP 403)`（模型级授权，同 key 其它 NV 端点可用，INC-w33-auth） |
| E06 OR dots3 | 端到端回归 | ✅ | `W33-E06-R1` 严格逐字、1.6k/53 |
| E07 OR north | 端到端回归 | ✅ | `W33-E07-R1` 严格逐字、1.1k/106 |

### 3.2 档位轴（T）
- **T ⚠️**：档位 readback = `当前 1M`；DataStore `max_context_tokens=1,000,000`。1M 档字符 cap 实测 `used=463457 limit=400000`（熔断报文，32K 档 `COMPACT_TRIGGER_CHARS=50227` 的约 8 倍量级）⇒ 档位确实按会话预算放大，不等于端点 contextLength。
- **1M tokens 真压 ⛔vacuous（单列）**：需约 4M chars 输入；本波受两条注入通道上限限制（`adb input text` 单次实测仅落 ~333 字符；`file_read` 单次 16k 字符上限）未构造，如实 `NOT_EXERCISED`。

### 3.3 功能面（F）
| 格 | 面 | 状态 | 关键实测 |
|---|---|---|---|
| F1 多模态视觉 | IMAGE 端点语义回读 | ✅ | `or-dots3`（inputModalities 含 IMAGE）480×240 真图（红方/绿圆/W33VISION）：**red square / green circle / W33VISION 三项逐字回读全中**、1.8k/352、cache 84.3% |
| F2 workspace 读写 | 工具真机落盘 | ✅ | `file_write` → 真实点按「允许一次」1 次 → `w33probe.txt` 落盘、设备侧 `cat` 逐字 `W33WS-F2-Q9K6` → `file_read` 复核 |
| F3 skill | @菜单 + skill_load | ✅ | `@notes` 弹出 `notes-organizer`/`pdf-toolkit` → 选中 → `skill_load {"name":"notes-organizer"}` → `[SKILL BODY]` → 采用语义（W33 Sync / Decisions / Action） |
| approval_gate | 审批闸门事件链 | ✅ | tool_calls → 审批卡 → 真实点按「允许一次」→ 执行 → 终答（与 F2 同链） |
| F4 MCP | 外部端点 | ⛔vacuous | MCP 页 4 条标签确认 0 server；`NOT_EXERCISED` + 触发格，不冒充覆盖 |
| F5 手动压缩 | 压缩入口 | ⚠️ | 「更多操作→压缩上下文」入口与确认框全行使；判据是 token 软预算（1M 档约 104k tokens），会话体积未越 ⇒ `noop nothing_to_drop`（UI 明示「历史本来就装得下」），**正确行为非缺陷** |
| F6 自动压缩 | 自动策略 | ⚠️ | 6 次 `auto_compact=noop reason=below_trigger`（noop 也落账，非静默）；4 次 16k 工具结果、上下文 50.3k tokens 的会话仍 below_trigger ⇒ `committedChars` 口径与真机表现存在待核点，**交外审复核，不推断为缺陷** |

### 3.4 判据订正与环境事件
- **正控**：E01/E06 严格逐字证明「注入→响应→UI 回读→日志落痕」判据链会响，之后才采信各格。
- **判据订正**：W32 的 `strict_sentinel` 排除 `Reply_with_*` 前缀输入气泡；本波题面统一用 `Reply_with_exactly_*` 形态，避免用户气泡自命中。
- **INC-w33-empty**：OR dots3 长会话首轮出现 `giving up after 1 retries: empty output` —— harness 重试一次后**可见报错**（非静默），归免费端点抖动。
- **INC-w33-auth**：super-vl 403（模型级授权 ≠ 密钥级授权：同 key 其它 NV 端点可用）。
- **INC-w33-budget**：多次 `file_read` 同一大文件导致 `prompt token budget exceeded: used=463457 limit=400000` —— L3 字符闸门按预期熔断（正确行为）。
- **环境事实**：`adb shell input text` 单次仅落 ~333 字符（15,000 字符注入实测只进 333）；`file_read` 单次 16k 字符上限 —— 两条通道共同限制「>50,227 chars 会话」的可构造性。

### 3.5 前置表补行（模型级授权 ≠ 密钥级授权）
| 前置 | 事实 | 影响 |
|---|---|---|
| 模型级授权 | 同 key 其它 NV 端点可用，`nvidia/nemotron-3.5-super-vl-120b-a12b` 返回 403 | 端点「可添加/可切换」≠「可调用」；不得把 403 写成 harness 网络层缺陷 |

### 3.6 顶层键数环比（报告 4 的方法）
| 制品 | 顶层键数 | 格数 | 备注 |
|---|---|---|---|
| `w31-matrix-summary.json` | 29 | 9 | W32 补 14→29 |
| `w32-matrix-summary.json` | 31 | 14 | W33 补 `matrix_vs_gate_order`，断言扩到 4 条 |
| `w33-matrix-summary.json` | 30 | 13 | 含 `matrix_vs_gate_order` / `iterations_note` / `full_context_1M_stress` / 5 条断言 |

> 键数只作环比观察，不以「键多=好」判质量；W31 的退化教训是**丢键**，不是键少本身。

---

## §四 四闸门

| 闸 | 命令 | 结果 | 证据 |
|---|---|---|---|
| G1 编译 | `bash scripts/localbuild.sh :app:assembleDebug`（删 APK 后重出包） | ✅ rc=0 | `_w33/g1_build.log` / `g1_rebuild2.log`；APK 68,407,402 B、sha256 前缀 `b41be9cc92ee6f90` 与重出包前**逐字节一致**（deterministic）；`localbuild.sh` 不自动安装（仅打印提示），本波未重装 |
| G2 arch-guard | `bash scripts/arch-guard.sh`（仓根） | ✅ PASS | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`；`G20 130 个 handoff ID 全在台账内`（含 W33 段 3 行） |
| G3 selftest | `bash scripts/arch-guard-selftest.sh`（后台） | 见 §5.5 | `_w33/g3_selftest.log` |
| G4 fulltest | `eval env` + `bash scripts/fulltest.sh` | 见 §5.5 | `_w33/g4_fulltest.log` |

---

## §五 收尾契约

### 5.1 各步四态汇总（1→5）

1. **六报告分歧裁决（不改方向）** ✅ — §2.1 十三条逐条核实并给落点；两条「二选一」取 a/b 并标注用户可改判；补两份报告共同的 P0（证据入仓）。
2. **第 0 车零 `.kt` 收口** ✅（缓解维度）— W33-01/02/03 缓解动作实跑（§2.2/§2.3），判据 6 四条必红全 RED；W30 三行判据 2/4/5 实跑入账（§2.4），W31-02/03 复核未塌。**三行/老行挂账状态仍 `OPEN`，销账待外审复核**。
3. **真机矩阵** ✅（8/4/1）— §三；F1 三项语义全中、F2/F3/审批 ✅、E03 严格复核关闭 W32 caveat；F5/F6 ⚠️ 如实（判据行使、未 applied）；F4 ⛔vacuous；1M 真压 ⛔vacuous 单列。
4. **四闸门** ✅ — §四/§5.5（G1 deterministic、G2 27/1/0、G3/G4 见 §5.5）。
5. **推送 + CI** — §5.6（推送后回填）。

### 5.2 明确未做（逐条带挂账号）

- **不动 `.kt`**：拆分三方 / B1 压缩四条 / `W24-02` 断词 / 四态生产读者 / 装饰字段 / `syncMcpTools` 对齐 —— 挂 `W20-02`、`W24-02`、`W12-02`、`W9-06`、`W17-02`。
- **1M tokens 真压**：只做档位与字符 cap 实测，真压未构造 —— 挂 `W20-05`。
- **MCP 真机联调**：无外部 server —— 挂 `W5-03`。
- **六报告正文入仓**：入仓等于新建制品，属用户裁决 —— 挂 `W33-03`（证据列已注明「正文未入仓，仅验收物路径引用」）。
- **F6 `committedChars` 口径待核点**：不推断为缺陷，交外审复核 —— 挂 `W33-03`（`evidence/raw/w33/f6_auto_compact.json` 的 `note` 已写明）。
- **六项用户终局裁决**：转代码波/矩阵收官、journal 二选一、G20 扫描面、AndroidHarness 借鉴、tip run 闭环、历史四项 —— 只登记不代做，挂 `W24-03`、`W20-02`。
- **不新开 `W33-04+`**：六行漏号 / G10 贴顶 / 安全包沿用老号。

### 5.3 OPEN 挂账（触发信号 + 缓解动作 + 挂账号）

本波落 W33 段 3 行全 `OPEN`（§2.2/§2.3 + 台账）：
- **W33-01**：触发 = 收尾契约占位/推送次数/写死派生数复发；缓解 = 已改 4 处；挂 `W33-01`。
- **W33-02**：触发 = 双行同义无联动、状态两套口径；缓解 = 主从联动注记 + 表注 + 行号订正；挂 `W33-02`。
- **W33-03**：触发 = 制品三方不一致 + OR 恒真 + 证据不可复核；缓解 = 方案 b + AND + 四条必红 + 26 文件入仓；挂 `W33-03`。

既有活跃挂账沿用台账现值：`W31-01`（待复核销账）、`W30-01/02/03`（实跑入账、待外审）、`W24-02`、`W21-04`、`W20-02`、`W20-05`、`W12-02`、`W5-03`、`W24-03`。

### 5.4 文档回写 diff

| 文件 | 改动 |
|---|---|
| `docs/analysis/w32-acceptance.md` | 4 处收尾契约订正（矩阵/四闸门指针、推送 2 次、G20 不写死）；§二/§5.1 加「缓解 ✅ ≠ 销账」；MCP 口径改「NOT_EXERCISED 并留触发格」；§2.1 行号 `:248-265`；§5.4 补证据入仓 |
| `docs/handoff-20261119-wave32.md` | §二标题改「不改方向、处方逐条采纳」；§三加表注；MCP 落点改口径；`:44` 行号订正 |
| `docs/notes/w29-closeout-checklist.md` | 判据 6（面定义 AND + evidence_ref）/ 判据 7（CLOSED 实跑指路）新增并实跑；判据 3b 样本哈希登记；W31 抬头「加白名单」措辞订正；覆盖清单同步 |
| `docs/06-deferred-ledger.md` | 新增 W33 段 3 行（全 `OPEN`）；W31-01/W32-01 加主从联动；W30-01/02/03 加「W33 实跑入账」；W31-02/03 加「W33 复核」 |
| `evidence/w32-matrix-summary.json` | MCP 口径（保留触发格 + 文档同步）、`K_feature_enum` AND + 新断言、`evidence_ref` 全改仓内路径、glyph 断言、`matrix_vs_gate_order`、`coverage_note` 跨波说明 |
| `evidence/w31-matrix-summary.json` | `state_glyph_mapping` 与断言统一（删 FAIL→⛔） |
| `evidence/raw/w32/`（新增 26 文件） | W32 真机证据脱敏入仓（序列号/本机路径改占位） |
| `evidence/w33-matrix-summary.json`（新增） | W33 矩阵制品 30 键 / 13 格 |
| `evidence/raw/w33/`（新增 14 文件） | W33 真机证据脱敏入仓 |
| `README.md` / `docs/02-roadmap.md` | 真值源改指台账 + roadmap 加时点抬头 |
| `docs/analysis/w33-acceptance.md` / `docs/handoff-20261119-wave33.md`（新增） | 本波验收物与交接 |
| 删除 `docs/analysis/source-review-2026-11-*.md`（8 份） | 用户确认已整合进 `source-review-consolidated-2026-11-19.md`（仓外），无需保留 |

### 5.5 推送前闸门实跑

- **G1 编译** ✅ rc=0（`_w33/g1_build.log` / `g1_rebuild2.log`）：删 `app-debug.apk` 后重出包，68,407,402 B、sha256 前缀 `b41be9cc92ee6f90` 与删前**逐字节一致**（deterministic）；本波零 `.kt`，未重装 APK。
- **G2 arch-guard** ✅ `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`；G20「130 个 handoff ID 全在台账内、CLOSED 行证据均非空」（含 W33 段 3 行）。
- **G3 selftest** ✅ `30/30 PASS（250s）`（`_w33/g3_selftest.log`）：28 个变异 case + VAC-G1b / VAC-G21 空转自证全 PASS。
- **G4 fulltest** ✅ `tests=818 failures=0 errors=0 skipped=0`（14 模块，`_w33/g4_fulltest.log`），与基线一致（零 `.kt`）。

### 5.7 交给外部审查团队的制品清单

- `docs/analysis/w33-acceptance.md`（本验收物）
- `docs/handoff-20261119-wave33.md`（交接）
- `evidence/w33-matrix-summary.json` + `evidence/raw/w33/`（14 文件）
- `evidence/w32-matrix-summary.json` + `evidence/raw/w32/`（26 文件，修 14/14 MISSING）
- `evidence/w31-matrix-summary.json`（glyph 统一）
- `docs/06-deferred-ledger.md`（W33 段 + W30/W31 注记）
- `docs/notes/w29-closeout-checklist.md`（判据 6/7 + 3b 哈希 + 实跑摘录）
- `docs/analysis/w32-acceptance.md` / `docs/handoff-20261119-wave32.md`（W33 订正）

**不含**：会话历史 / 凭据 / 端点完整标识 /「建议下一链做什么」。
