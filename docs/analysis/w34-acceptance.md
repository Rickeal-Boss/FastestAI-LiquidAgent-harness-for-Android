# W34 验收物 — 零 `.kt` 账实收口（3 行）+ 多模型×上下文档位×功能面真机矩阵（13 格）

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 起点 tip `ccd215b7e`（W33）；本波提交见 §5.6。零 `.kt`（310 个 `.kt` 零 diff）。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」。
> 台账权威版 = `docs/06-deferred-ledger.md`；本波制品 = `evidence/w34-matrix-summary.json`；证据 = `evidence/raw/w34/`。

---

## §一 做什么 / 明确不做（本波边界，先于一切）

### 1.1 做什么（按用户本单指令与六份外审裁决）

| # | 做什么 | 落点 | 结果 |
|---|---|---|---|
| 1 | 读记忆 + W33 handoff + OPEN 挂账行（只读现役仓 `harness-v2/docs/`） | 本文件 §二/§三 | ✅ |
| 2 | 六份外审报告逐条裁决、**不改方案大体方向** | §二（8 项分歧 + 采纳/不改） | ✅ |
| 3 | 收尾契约第 13 次（三处写死「130」）清零 | `w33-acceptance.md` §四/§5.5、`handoff-w33.md` §五（W34-01） | ✅ |
| 4 | 制品断言账实对齐 + 判据 6 参数化 + `K_evidence_ref` 必红首次跑通 | checklist 判据 6、w32/w33 制品（W34-02） | ✅ |
| 5 | 删除 8 份 `source-review` 的处置结案（用户已裁案 b）+ 幽灵行清理 | `W19-14` → `CLOSED`、§5.4 裁决留痕（W34-03） | ✅ |
| 6 | 台账销账（外审复核 + 实跑入账）：W30 三行 + W31/W32 主从对 + W33 三行 | 台账 8 行 → `CLOSED`（净 OPEN −5） | ✅ |
| 7 | 多模型 × 不同上下文（32K↔1M）× 功能面（多模态/文件读写/skill/MCP/手动自动压缩）真机矩阵 | §三（13 格） | ✅ 5 ✅ / 7 ⚠️ / 1 ⛔vacuous |
| 8 | 本地闸门 G1–G4 + 推送 + CI 四字段复核 | §四/§5.6 | 见各节 |
| 9 | 验收物 + handoff + 制品 + 证据入仓，止于交接 | 本文件 + `handoff-20261119-wave34.md` | ✅ |

### 1.2 明确不做（逐条带挂账号）

| 不做 | 理由 | 挂账号 |
|---|---|---|
| 不做转代码波：拆分三方、B1 压缩、`ChatViewModel` 拆分 | `.kt` 顺延边界；用户本波未授权转代码波 | `W20-02`、`W9-01`、`W12-02` |
| 不修 `AppContainer.promptContext()` 三处吞 CE（源码审查唯一实质发现） | 属 `.kt` 卫生批；与 `syncMcpTools` 同源，合并修复需用户裁决（第 8 项） | `W17-02` |
| 不改 `LINE_CAPS` / `TOTAL_LINE_CAP` / `EXPECTED_TESTS=818` | 拆分不调上限、拆分不当扩容 | `W20-02` |
| 不新建第 6 份 checklist / 第 7 份制品 / 不重出 W30 及更早正文 | 载体复用纪律 | `W34-02`（只改现有） |
| 不恢复 8 份 `source-review`、不给 26 处引用逐处加抬头 | 用户已裁案 b；批量改 W8–W20 正文违反边界 | `W34-03`（残余项登记） |
| 不做 1M tokens 真压（`full_context_1M_stress`） | `adb input` 单次 ~333 字符 + `file_read` 16k/次双通道硬限制 | `W20-05` |
| 不架外部 MCP server（F4 维持 VACUOUS） | 需 https 端点（G15b 禁明文 http），本波时间盒内未做 | `W5-03` |
| 不做 journal / FreezeClock（会话不持久化老债） | 独立内核硬化波 | `W9-05`、`W24-03` |
| 不代做 tip run 逐波判 P0 | 既定降级规则（`ccd215b7e`/`44524371613` 0 命中是预期） | 台账 W33 段 §5.6 声明 |
| 不判「自动压缩 net=-256」为 harness 缺陷 | 根因未定位（自动/手动同快照结论不一致），交外审裁决 | `W9-01`（行内注记） |
| 不引入 MCP sampling/roots、不消费 `autoApproveDangerous`、不做设备侧 1M | 老债边界 | `W5-06`、`W5-05`、`W9-11` |

---

## §二 第 0 车收口（W34-01/02/03；缓解动作已完成，状态维持 `OPEN` 待外审）

### 2.1 W34-01 收尾契约第 13 次（三处「130」）

| 项 | 实跑 | 证据 |
|---|---|---|
| `w33-acceptance.md` §四 G2 | 改「handoff ID 全在台账内（计数以 `arch-guard` 实跑为准，真值源不写死派生数）」 | grep「130」= 0 |
| `w33-acceptance.md` §5.5 | 同上口径 | grep「130」= 0 |
| `handoff-20261119-wave33.md` §五 G2 | 改「G20 handoff ID 全在台账（计数以 `arch-guard` 实跑为准）」 | grep「130」= 0 |

```text
$ grep -c "130" docs/analysis/w33-acceptance.md docs/handoff-20261119-wave33.md
0
0
```

### 2.2 W34-02 制品断言账实 + 判据 6 可执行化

- **判据 6 参数化**：`ARTIFACT` 默认 `evidence/w32-matrix-summary.json`，可指向任一制品；原 `cd "$(git rev-parse --show-toplevel)"` 替代 `cd harness-v2`，W35-03 再加 `REPO_ROOT="${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"` 兜底（无 `.git` 快照也 rc=0）；`mkdir -p _w34/mut`；检查逻辑落 `_w34/k6_check.py`（仓内文档块可原样跑）。
- **必红（W34 五类；W35-03 加第六类）**：`k6_fail` / `k6_nomcp` / `k6_nocell` / `k6_iter` / `k6_ref`；W35-03 增 `k6_noassert`（删 `assertions` ⇒ `RED assertions missing`）。`k6_ref` 为 `K_evidence_ref` 的**首次仓内必红输出**（此前 `verified=true` 全自报）。
- **w32 制品**：`assertions` 由 3 补到 4（新增 `K_feature_enum_no_cell_claim`），与 `mutation_cases` 4=4；`mutation_selftest.note` 改「4 条 / 判据 6」，删「扩为 5 条」「判据 2b」。
- **w33 制品**：`mutation_selftest.note` 指向 W34 参数化实跑（5=5）。
- **验收物 §2.3/§3.6**：此处指 `w33-acceptance.md` 的两节——补 `RED missing ref` 原文；键数表补 `iterations_total`（W31 6 / W32 18 / W33 10，W33 注释「随取证粒度变化，非回归」）。本波制品自身 `iterations_total=17` 与三字段补回记录见本文件 §3.6。

```text
[正常态] w32 → OK feature/evidence invariants
         w33 → OK feature/evidence invariants
         w34 → OK feature/evidence invariants
[必红 w32] RED zero-value keys: ['FAIL']; state domain
           RED mcp field missing
           RED mcp claims cell F4_mcp but cells[] lacks it; no mcp cell without declaration; state sum; attempt keys
           RED iterations_total != len
           RED missing ref evidence/raw/__missing__.json
[必红 w33] 同上五类全 RED（k6_ref 首次真响）
```

### 2.3 W34-03 删除 8 份 `source-review` 的处置（用户已裁案 b）

- 裁决留痕：用户 2026-11-19 在《源码级深度审查-W33》头部批注「历史 `source-review-*.md` 属自动化产物，删除无问题」⇒ **维持删除**。
- `W19-14`（整行针对已删文件的 `OPEN` 行）→ `CLOSED` + 行内结案注记，幽灵行消除。
- `w33-acceptance.md` §5.4 补：裁决出处、26 处引用 / 14 文件计数、`consolidated` 仓外路径、git 历史恢复路径（删除提交 `c4dc1a0`）。
- 残余：26 处引用**未逐处加抬头**（边界见 §1.2），登记为本行出口判据残余项。

### 2.4 台账销账（8 行，同一提交；主从联动同步）

| 行 | 销账依据 | 复核来源 |
|---|---|---|
| `W30-01` / `W30-02` / `W30-03` | W33 实跑入账 + 本波判据 1/2/3b/4/5 复跑全绿（正常态 + 必红） | 六份外审 + 本波 §2.5 |
| `W31-01` + `W32-01` | 3b 双判据真修 + 全树 5 类敏感形态 0 命中 + 本波 3b 双态复跑 | 外审独立复扫 + 本波 §2.5 |
| `W33-01` / `W33-02` / `W33-03` | 外审逐行复核「3/3 真修、全达标」；本波判据 6/7 复跑 | 外审 a1 §1 + 本波 §2.5 |

### 2.5 判据实跑（仓根；含必红）

```text
[判据 1] W30 rows = 3 | W31 rows = 3 | W32 rows = 3 | W33 rows = 3；无重复/缺失号
[判据 2 正常态] OK(vacuous: positive control inherited, device NOT executed)
[判据 2 必红]   RED mutation invariant broken
[判据 3b 正常态] OK no credential filename
[判据 3b 必红]   RED credential filename hit（拼装样本，字面量不入仓）
[判据 4 正常态]  w27-acceptance=0 | handoff-w27=0 | ledger=0
[判据 4 必红]    mut hits = 1 → RED affirmative wording remains
[判据 5 正常态]  6 6 6 | ok1=True | attempt keys==cell_ids=True → OK
[判据 5 必红]    RED iteration/state invariant broken
[判据 6]        见 §2.2；W35-03 加 `K_assertion_presence`（删 `assertions` ⇒ `RED assertions missing`）+ `REPO_ROOT` 兜底
[判据 7 正常态]  OK closed rows carry runnable pointers (W30+)
                OK closed rows carry current-wave pointers (W34)（8 行销账后复跑）
[判据 7 必红·W35] 副本删空 W30-01 本波复核指路 ⇒ 第一行仍 OK（旧正则不响）+ `RED closed without current-wave pointer: W30-01`；原文见 `docs/notes/w29-closeout-checklist.md` W35 摘录
```

> 判据 1 的 W33 段 rows=3 与 W34 段 rows=3 均确认；台账总行 210（33 `CLOSED` / 166 `OPEN` / 11 `CARRIED`），净 OPEN −5。

---

## §三 真机矩阵（`evidence/w34-matrix-summary.json`，13 格）

> `state_counts = {PASS:5, PARTIAL:7, VACUOUS:1}`，无零值键。正控 = NV Lightning / Nano Omni sentinel 严格逐字（排除输入气泡与错误横幅）。
> 设备：OPPO PDRM00 / Android 13，有线 adb（serial 已按惯例保留在证据外）；新 NVIDIA key 经真机端点编辑界面录入（回显 70 掩码点，E01 首测通过证明录入成功）。

### 3.1 端点轴（6 格）

| 格 | 状态 | 关键实测 |
|---|---|---|
| E01 Lightning | ✅ | `W34-E01-R1` 严格逐字；1.8k in / 69 out |
| E02 Bolt | ⚠️ | 连通 + usage 1.4k/6；回复「This is the final answer」非 strict ⇒ 不冒充 PASS |
| E03 Nano Omni | ✅ | `W34-E03-R1` 逐字；1.8k in / 112 out；无失败横幅 |
| E05 super-vl 1M | ⚠️ | 旧 key 立即 403；新 key 等待约 80s 后仍 403（模型级授权，INC-w34-auth） |
| E06 OR dots3 | ⚠️ | 两次 120s 超时；`ping openrouter.ai` 0% 丢包 / 219ms ⇒ 供给侧 |
| E07 OR north | ⚠️ | 同窗口 120s 超时（重试后终态） |

### 3.2 档位轴（1 格）

| 格 | 状态 | 关键实测 |
|---|---|---|
| T | ⚠️ | 32K→1M 两次切换均 readback（`当前 32K` / `当前 1M`）；32K 档软预算 27.9K tokens 实测（30.9K/27.9K 越线）；1M tokens 真压 `NOT_EXERCISED`；512K 第三数据点未行使 |

### 3.3 功能面（6 格）

| 格 | 状态 | 关键实测 |
|---|---|---|
| F1 vision | ⚠️ | 480×240 真图（红方块/绿圆/W34VISION）附件链路成立（添加图片→DocumentsUI→chip）；dots3 `SocketTimeoutException`，重试一次后可见报错（INC-w34-timeout） |
| F2 workspace + 审批 | ✅ | `file_write` 请求授权 → 允许一次 → `wrote 13 chars` → 设备侧 `run-as cat` 逐字 `W34WS-F2-Q7K2` |
| F3 skill | ✅ | `@` 菜单（notes-organizer / pdf-toolkit）→ skill_load（2ms）→ `W34-SKILL-OK` 逐字 |
| F4 MCP | ⛔vacuous | MCP 页「还没有 MCP 服务器」（0 server），保留触发格 |
| F5 手动压缩 | ✅ | 确认框 → `manual_compress dropped=9 kept=11 cache_invalidated=false`；**下一轮请求 30.9k→22.9k in** ⇒ 真压缩 |
| F6 自动压缩 | ⚠️ | R1–R4 `below_trigger`；R5–R6 `insufficient_savings:net=-256`（触发线已越）；压缩后 `already_compressed`。**同一快照手动路径可 dropped=9** ⇒ 自动/手动结论不一致，根因待核（INC-w34-autokv） |

### 3.4 环境事件（INC，不写成 harness 缺陷）

| ID | 范围 | 事实 |
|---|---|---|
| INC-w34-timeout | E06/E07/F1 | OR 免费池同窗口 120s 超时；设备 ping openrouter.ai 通、NV 端点同窗口可用 |
| INC-w34-auth | E05 | super-vl 120B 模型级 403（新旧 key 同结论） |
| INC-w34-autokv | F6 | 自动压缩 `net=-256` 与手动 `dropped=9` 对同一快照结论不一致；根因未定位，交外审裁决 |

### 3.6 顶层键数环比与迭代计数（报告 4 的方法；W35-03 补回本波三字段）

| 制品 | 顶层键数 | 格数 | 备注 |
|---|---|---|---|
| `w31-matrix-summary.json` | 29 | 9 | W32 补 14→29；`iterations_total=6` |
| `w32-matrix-summary.json` | 31 | 14 | W33 补 `matrix_vs_gate_order`；W34 补 `K_feature_enum_no_cell_claim` 进 assertions（断言=必红=4）；`iterations_total=18` |
| `w33-matrix-summary.json` | 30 | 13 | 含 `matrix_vs_gate_order` / `iterations_note` / `full_context_1M_stress` / 5 条断言；`iterations_total=10`（W32 18 → W33 10，迭代链随取证粒度变化，非回归） |
| `w34-matrix-summary.json` | 31 | 13 | W35-03 补回 `assertions` / `mutation_cases` / `mutation_selftest`（28→31，5 断言 / 5 必红，与 §5.4 对齐）；`iterations_total=17`；`full_context_1M_stress` 由纯字符串补回 `{status,reason}` dict |

> 键数只作环比观察，不以「键多=好」判质量；W31 的退化教训是**丢键**，不是键少本身。本波制品原 28 键缺三字段、§5.4 却写「5 断言 / 5 必红」属空口，W35-03 按 `w33` 同 schema 补回后才账实一致；判据 6 对补回后 w34 正常态 `OK feature/evidence invariants`、六类必红全 RED（含 `RED assertions missing`），原文见 `docs/notes/w29-closeout-checklist.md` W35 摘录。

---

## §四 四闸门

| 闸 | 命令 | 结果 | 证据 |
|---|---|---|---|
| G1 编译 | `bash scripts/localbuild.sh :app:assembleDebug`（删 APK 后重出包） | ✅ rc=0 | `_w34/g1_build.log`；APK 68,407,402 B、sha256 前缀 `b41be9cc92ee6f90` 与 W33 记录**逐字节一致**（零 `.kt` ⇒ deterministic） |
| G2 arch-guard | `bash scripts/arch-guard.sh`（仓根，两次：第 0 车前 / 台账改后） | ✅ PASS | `_w34/g2_arch_guard.log` / `g2b_arch_guard.log`：`OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`；G20「handoff ID 全在台账内、CLOSED 行证据均非空」 |
| G3 selftest | `bash scripts/arch-guard-selftest.sh`（后台） | ✅ 30/30 | `_w34/g3_selftest.log`（230s；28 变异 + VAC-G1b/VAC-G21） |
| G4 fulltest | `eval env` + `bash scripts/fulltest.sh` | ✅ 818-0-0 | `_w34/g4_fulltest.log`（14 模块；tests=818 failures=0 errors=0 skipped=0） |

---

## §五 收尾契约

### 5.1 明确未做

见 §1.2（逐条带挂账号）。

### 5.2 OPEN 挂账（新增 3 行；全部 `OPEN` 待外审复核）

| 行 | 触发信号 | 缓解动作 | 出口 |
|---|---|---|---|
| W34-01 | 外审把「130」读成 ID 唯一已检查 / 下一波再写死 `131` | 三处改「以 `arch-guard` 实跑为准」 | 两文件「130」0 命中（已达成） |
| W34-02 | 按 W33 制品 note 找 5 条必红只找到 w32 四条 / 注入不存在 ref 仍全绿 / 判据 6 原样执行 `FileNotFoundError` | 参数化 + 五类必红 + w32 断言 4=4 + note 订正 | 双制品正常态 OK + 五类必红 RED（已达成） |
| W34-03 | 按 `W19-14` 或验收物找日析文件 404 | 用户裁案 b + `W19-14` 结案 + §5.4 留痕 | 裁决留痕在仓 + 幽灵行消除（已达成）；26 处抬头为残余项 |

### 5.3 文档回写 diff（本波）

| 文件 | 变更 |
|---|---|
| `docs/analysis/w33-acceptance.md` | §四/§5.5 去「130」；§2.3 补 `RED missing ref`；§3.6 补 iterations；§5.4 补裁决留痕；§5.5 三处 INC-w33-empty 补「tail 未留存」 |
| `docs/handoff-20261119-wave33.md` | §五 G2 去「130」；§4.3/§六 INC-w33-empty 补「tail 未留存」 |
| `docs/notes/w29-closeout-checklist.md` | 判据 6 参数化 + 五类必红（`K_evidence_ref` 首次）；W34 判据实跑摘录 |
| `evidence/w32-matrix-summary.json` | `assertions` 3→4（补 `K_feature_enum_no_cell_claim`）；note 改「4 条 / 判据 6」 |
| `evidence/w33-matrix-summary.json` | `mutation_selftest.note` 指向 W34 参数化实跑 |
| `docs/06-deferred-ledger.md` | 新增 W34 段 3 行；8 行销账（W30 三行 + W31/W32 主从对 + W33 三行）；`W19-14` → `CLOSED`；`W9-01`/`W17-02`/`W24-02` 行内注记 |
| `evidence/w34-matrix-summary.json` + `evidence/raw/w34/`（14 件） | 本波制品与真机证据（脱敏入仓） |
| `docs/analysis/w34-acceptance.md` + `docs/handoff-20261119-wave34.md` | 本波验收物与交接 |

### 5.4 交给外部审查团队的制品

- `docs/analysis/w34-acceptance.md`（本文件）
- `docs/handoff-20261119-wave34.md`
- `evidence/w34-matrix-summary.json`（13 格 / 5 断言 / 5 必红 / `evidence_ref` 全存在）
- `evidence/raw/w34/`（14 件脱敏真机证据）
- `docs/06-deferred-ledger.md`（W34 段 + 8 行销账 + 3 处行内注记）
- `docs/notes/w29-closeout-checklist.md`（判据 6 参数化 + W34 摘录）
- `docs/analysis/w33-acceptance.md`、`docs/handoff-20261119-wave33.md`（W34 订正）
- `evidence/w32-matrix-summary.json`、`evidence/w33-matrix-summary.json`（W34 账实订正）

**不含**：会话历史 / 凭据 / 端点完整标识（模型 ID 仅在端点轴格中以公开模型名出现，不含 key）/「建议下一链做什么」。

### 5.5 边界声明（不把推断写成事实）

- 零 `.kt`：310 个 `.kt` 零 diff；所有源码级行为均为现役已工作协议，本波未新增也未修改任何 Kotlin 代码。
- F6 `net=-256` 与手动 `dropped=9` 的不一致：只记录事实与候选方向，**不下缺陷结论**（交外审）。
- E05 403 / OR 超时：归 INC（模型级授权 / 免费池拥塞），不推断为 harness 缺陷。
- 1M：只有档位 readback 与 32K 软预算实测；1M tokens 真压不在本波，记 `NOT_EXERCISED`。

### 5.6 commit 与 CI run

- **推送 3 次**：主体 `dc3fb14`（W34 全部改动，22 文件：7 文档 + 3 制品 + 14 件证据；改了 `evidence/**` ⇒ Build 五 job + Secret scan 双触发）→ 纯文档回填 `55630d8`（§5.6 CI 链接）→ 本条口径订正（纯文档，SHA 随交付消息给出）。两次纯文档 tip 按既定降级规则不逐波判 P0、不补链接；Build 对纯文档 0 run 是既定口径，不写成「构建通过」。
- **CI（head_sha = `dc3fb1445556e050ffe3a17e1190e58b20e7ab6f`，四字段复核 completed / success / push / 全 40 位匹配）**：
  - **Build** run `44559513390`：completed / **success** — <https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44559513390>（五 job：Assemble Debug ✅ / Lint ✅ / Arch guard ✅ / Unit tests ✅ / Secret scan ✅）
  - **Secret scan** run `44559513521`：completed / **success** — <https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44559513521>（gitleaks ✅ + trufflehog ✅）
- 本波**首次推送即双绿**（无红→绿链）；`.gitleaks.toml` / `secret-scan.yml` / `build.yml` 三份配置零改动，未放行任何扫描步骤。
- PAT 仅以 URL inline 一次性使用、未落盘；`origin` 保持干净 URL。轮换属用户侧动作（按先例不在仓内写未完成命令）。
