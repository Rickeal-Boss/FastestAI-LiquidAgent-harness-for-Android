# W31 验收物 — 多模型×多功能面真机矩阵 + 第 0 车零 `.kt` 收口

> 上游依据：用户 2026-11-19 指令（四审查报告分歧裁决不改方向、多模型×多上下文×多功能面真机矩阵、先本地 UI+adb 调试→真机→推仓库+CI、收尾契约止于交接）。
> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，分支 `main`）。台账权威版 = `docs/06-deferred-ledger.md`。
> 本波 tip：见 §5.6（推送后回填 commit 与 CI run）。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」。

---

## §一 本次任务「做什么 / 明确不做」

### 1.1 做什么
1. **四外审报告分歧裁决**（W31 段挂账草案 / W30 深度审查与 W31 排期裁决 / W30 深度审查+挂账分析+W31 路线建议 / W24-W30 逐行审查+挂账分析），**不改方案大体方向**，逐条 grep 现役树核实事实，裁决分歧。
2. **第 0 车零 `.kt` 收口**：台账 W31 段三行（脱敏回退 / 必红块 WS 依赖 / 收尾契约占位）缓解动作 + 必红变异实跑。
3. **多模型×多功能面真机矩阵**：本地 UI + adb 调试 → 真机 → 多 NVIDIA 模型 × 多功能面（多模态输入 / workspace 读写 / skill / MCP / 手动+自动压缩）行使。
4. **四闸门 + 推送 + CI 复核 + 收尾契约**。

### 1.2 明确不做
- **不引入新挂账号字面量进 handoff**（W30 G20 112→115 教训，本波 handoff 复用既有老号或纯中文段位表述）。
- **不为「未行使」编造通过**：F1/F3/F5/F6 未行使一律记 `⛔vacuous` 并带触发信号，不冒充 PASS。
- **不新建第 4 份 checklist / 第 5 份制品**：只改 W30/W29 现有载体 + 新增本波验收物与制品。
- **不把环境问题写成 harness 缺陷**：WiFi 掉线 / 密钥 UI 录入流程归 INC，不挂账到代码。
- **不动 `.kt`**：本波全零 `.kt`（工具循环 / 审批 / 压缩 / 端点全部现役已工作协议）。

---

## §二 第 0 车零 `.kt` 收口结果（W31-01/02/03）

### 2.1 W31-01 脱敏回退收口 — ✅
| 项 | 结果 | 证据 |
|---|---|---|
| 全树凭据文件名字面清零 | ✅ | `grep nvidia.txt/openrouter.txt docs evidence` → 0 |
| 设备序列号字面清零 | ✅ | `grep 13309cc8 docs evidence` → 0 |
| 判据 3b 正常态实跑 | ✅ | `grep -rniE openrouter\.txt\|nvidia\.txt docs evidence` → `OK no credential filename`（摘进 checklist §本波 W31） |
| 3b 必红注入样本 | ✅ | printf 拼装样本 → `RED credential filename hit`（真响） |
| W30 制品 K_cred_filename 断言 | ✅ | target 改「按白名单 0 命中」、privacy note 与实跑一致 |

### 2.2 W31-02 必红块 WS 依赖 + 判据 1 参数化 — ✅
| 项 | 结果 | 证据 |
|---|---|---|
| 判据 2/5 必红块去 `os.environ["WS"]` | ✅ | 改 `os.path.abspath(os.path.join(os.getcwd(),".."))`；不设 WS 实跑 rc=0 |
| 判据 2 必红 | ✅ | 不设 WS → `RED mutation invariant broken` |
| 判据 5 必红 | ✅ | 不设 WS → `RED iteration/state invariant broken` |
| 判据 4 必红 | ✅ | `RED affirmative wording remains` |
| 判据 1 参数化 | ✅ | WAVE/EXPECT 变量；W29+W30 两段正常态 `rows=3` OK；注入重复 W30-01 → `RED duplicate: W30-01`（真响） |

### 2.3 W31-03 收尾契约 + 派生文案 — ✅
| 项 | 结果 | 证据 |
|---|---|---|
| W30 验收物两占位删除 | ✅ | `grep 待回填\|待推送后回填` 仅剩订正注记内历史指称（出口判据允许） |
| handoff 抬头口径 | ✅ | 改「本交接不引入新挂账号」 |
| §四 G20 数字不写死 | ✅ | 改「以实跑为准不写死数」 |
| W29 §5.3 错配条件 | ✅ | 删「须真机取证」加 W31-03 订正注记 |
| W29 制品四处派生文案 | ✅ | coverage_note/mutation_selftest.note/cells_provenance/privacy 与 `state_counts={VACUOUS:11}` 一致 |
| W30 制品跨波 target | ✅ | 4 条标 `inherited_check` + `state_glyph_mapping`（PARTIAL→⚠️）断言 + cells_provenance 订正 |
| W24-03/W24-02/W17-02 行内注记 | ✅ | 台账三行加 W31 行内注记 |

---

## §三 多模型×多功能面真机矩阵（`evidence/w31-matrix-summary.json`）

### 3.1 环境与前置
- 构建：`assembleDebug` **删旧 APK 强制重出包**（避免 W11/W12 的 UP-TO-DATE 假出包）；卸旧装新（签名不匹配需 `pm uninstall` 先）。
- 设备：RSA 授权已恢复；**screencap 已知返回 0 字节**（OPPO/OnePlus 图形服务），取证全走 `uiautomator dump` 文本。
- 网络：设备走热点；**两次假连接**（网关 100% 丢包 → `UnknownHostException`），`svc wifi disable/enable` 修复（INC-wifi-drop）。
- 密钥：三 NVIDIA 端点经 UI 录入，存 `files/harness/secrets/*.bin`（AndroidKeyStore）。**首次 403 根因 = tap 光标停字段中间致 DEL 不全**，修复流程后 `连接正常`（INC-key-entry）。

### 3.2 四态矩阵
| 格 | 面 | 状态 | 关键证据 |
|---|---|---|---|
| E01 Lightning | 连通+端到端+工具循环 | ✅ | 连接 1942ms；PROBE-OK（reasoning+content）；file_write→workspace 落盘；1.9k in/55 out |
| E02 Bolt | 连通 | ⚠️ | 连接 3418ms；e2e/vision 未行使 |
| E03 Nano Omni | 连通 | ⚠️ | 连接 4214ms；omni 视觉未行使 |
| F2 workspace write | 工具真机落盘 | ✅ | `run-as cat …/workspace_w31tool.txt` → `W31-TOOL-OK` |
| F7 tool loop | 工具循环+审批 | ✅ | tool_calls→审批闸门→允许一次→执行→终答 |
| F1 vision | 多模态输入 | ⛔vacuous | SAF 选图未自动化 |
| F3 skill | Skill 加载 | ⛔vacuous | 未触发 |
| F5 manual compress | 手动压缩 | ⛔vacuous | 无 16 轮配方 |
| F6 auto compress | 自动压缩 | ⛔vacuous | 2K/27.9K 未越阈（below_trigger 正确） |

`state_counts = {PASS:3, PARTIAL:2, VACUOUS:4, FAIL:0}`（`FAIL:0` 显式零）。

### 3.3 正控（positive control）
`positive_control_verified_by = device_execution`：E01 真机端到端 + 工具落盘是本波正控，证明「判据能响、链路能通、文件能落」，不是纸面通过。

### 3.4 未行使项触发信号（诚实留痕）
- **F1 vision**：下次行使需 push 测试图到设备 + 自动化 SAF（DocumentsUI）选图 + Nano Omni/Bolt 为当前端点。触发信号：需要验证视觉输入链路时。
- **F5/F6 压缩**：需 16 轮长对话配方越字符阈值。触发信号：长上下文压缩验收。
- **OpenRouter**：本环境无 openrouter 凭据文件，端点池已建 `or-dots3`/`or-north` 但 `hasSecret=false`，未行使。触发信号：用户提供 openrouter key。

---

## §四 四闸门

| 闸 | 命令 | 结果 | 证据 |
|---|---|---|---|
| G1 编译 | `assembleDebug`（删旧 APK 重出） | ✅ rc=0 | 新 APK 字节；真机安装 Success |
| G2 arch-guard | `bash scripts/arch-guard.sh` | ✅ PASS | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`；**G20 = 121 个 handoff ID 全在台账、CLOSED 行证据均非空** |
| G3 selftest | arch-guard-selftest | ✅ 30/30 | 235s；VACUOUS 机制自证含 G20 判红 |
| G4 fulltest | `fulltest.sh` | ✅ 818-0-0 | `tests=818 failures=0 errors=0 skipped=0`（14 模块全覆盖） |

> 说明：本波全零 `.kt`，G3/G4 口径与上三波一致（818 `@Test` / selftest 量级），只验不回归。CI 绿以 §5.6 run 为准。

---

## §五 收尾契约

### 5.1 各步四态汇总
1. **外审裁决** ✅ — 四报告分歧裁决不改方向，逐条 grep 现役树核实（见 §2 + 台账 W31 段上游依据）。
2. **第 0 车收口** ✅ — W31-01/02/03 三行缓解动作 + 必红变异全真响（§2）。
3. **真机矩阵** ✅(核心)/⚠️(部分)/⛔vacuous(未行使) — 见 §3。
4. **四闸门** 见 §四（G1✅，G2-G4 推送前实跑）。
5. **推送+CI** 见 §5.6。

### 5.2 明确未做（逐条带挂账号）
- F1 多模态视觉输入未行使 — ⛔vacuous，无新挂账号（属多功能面矩阵内部格，留 §3.4 触发信号）。
- F5/F6 压缩未行使 — ⛔vacuous，同上。
- OpenRouter dots-3 / north-mini 未行使 — 无凭据，不新开挂账（端点池已备）。
- **B1 压缩三方 / SM-3 skillNames / 静默断词** 需 `.kt` 修复 — 挂既有老号（W20-02 拆分波 / W17-02 / W24-02），本波不新开行。

### 5.3 OPEN 挂账（触发信号 + 缓解动作 + 挂账号）
本波**不新增 OPEN 台账行**。既有活跃挂账沿用台账现值：
- **W24-02 静默断词/重试**：触发信号 = 模型拥堵时无可见反馈；缓解 = MockWebServer 红态用例（需 `.kt`）；本波真机未复现（连接失败有「网络抖动，正在重试…」提示，非完全静默）。
- **W20-02 拆分 / W17-02 skillNames**：触发信号 = 下一波拆分；缓解 = 按 harness-v2 预算链。
- **W24-03 journal 二选一**：第 19 次顺延，多值待裁，等用户二选一。

### 5.4 文档回写 diff
见 §5.6 commit。本波文档回写清单：
- `docs/06-deferred-ledger.md`：新增 W31 段 3 行 + W24-03/W24-02/W17-02 行内注记。
- `docs/analysis/w30-acceptance.md`：占位删除 + G20 去写死 + §5.2 补挂账号。
- `docs/analysis/w29-acceptance.md`：§5.3 删错配条件。
- `docs/handoff-20261118-wave30.md`：抬头口径订正。
- `docs/notes/w29-closeout-checklist.md`：判据 1 参数化 / 判据 2/5 去 WS / W31 摘录小节。
- `evidence/w29-matrix-summary.json`：四处派生文案同步。
- `evidence/w30-matrix-summary.json`：inherited_check / glyph mapping / cells_provenance。
- 新增 `evidence/w31-matrix-summary.json` + 本验收物。

### 5.5 推送前闸门实跑
- **G2 arch-guard**：`result=PASS ok=27 vacuous=1 violation=0 fault=0`；G20「121 个 handoff ID 全在台账内，CLOSED 行证据均非空」。
- **G3 selftest**：`selftest: 30/30 PASS`（235s；VACUOUS 机制自证含 G20「从台账副本删一行」判红）。
- **G4 fulltest**：`tests=818 failures=0 errors=0 skipped=0`（全部用例绿，14 模块；与基线 818 完全一致，零 `.kt` 无回归）。

### 5.6 commit 与 CI run
- **W31 commit**：`453b8b5f5853d69d324a6645f089639a14b19532`（含全部 docs + evidence + 制品，单次推送即含证据，无需为代码二次推送）。
- **CI（head_sha = `453b8b5`，四字段复核 completed / success / push / `453b8b5`）**：
  - **Build** run `44460198699`（run_number 46）：completed / **success** — <https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44460198699>
  - **Secret scan** run `44460198625`（run_number 42）：completed / **success** — <https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44460198625>
- CI 红 0；PAT inline 用后 `git remote set-url` 清干净（PAT 视为暴露须轮换）。工作树仅剩 2 份历史未跟踪审查笔记（沿用既定口径不入库）。

### 5.7 交给外部审查团队的制品清单
- `evidence/w31-matrix-summary.json`（本波真机矩阵机器可读制品，四态 + 证据 + 未行使原因 + 隐私扫描）
- `docs/analysis/w31-acceptance.md`（本验收物）
- `docs/06-deferred-ledger.md` W31 段（三行 + 行内注记）
- `evidence/w30-matrix-summary.json`（W31 订正后）
- `evidence/w29-matrix-summary.json`（W31 订正后）
- `docs/notes/w29-closeout-checklist.md`（判据修复 + W31 必红实跑摘录）
- **不含**：会话历史 / 凭据 / 端点完整标识（一律模型 ID + 8 位指纹）/「建议下一链做什么」。

> 本波止于交接，交由外部团队审查。