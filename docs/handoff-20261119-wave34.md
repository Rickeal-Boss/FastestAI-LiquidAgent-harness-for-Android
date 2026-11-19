# W34 交接 — 零 `.kt` 账实收口（3 行 + 8 行销账）+ 多模型×上下文档位×功能面真机矩阵（13 格）

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 台账权威版 = `docs/06-deferred-ledger.md`；本波验收物 = `docs/analysis/w34-acceptance.md`；
> 正式制品 = `evidence/w34-matrix-summary.json`；真机证据 = `evidence/raw/w34/`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」。

> **本交接只引用台账 §一 已有行首编号**：W34-01/02/03（本波新落、已入台账）、
> W30-01/02/03、W31-01、W32-01、W33-01/02/03、W19-14（本波销账/结案）、
> W9-01、W17-02、W24-02、W24-03、W20-02、W20-05、W21-04、W5-03、W5-05、W5-06、W9-05、W9-11、W12-02（既有行首）。
> 未引入任何不在台账行首的复合编号。

---

## 一、本波做了什么（一段话）

六份外审报告的 8 项分歧逐条裁决，**全部维持不改方案大体方向**：第 0 车把「收尾契约第 13 次（三处写死 130）」「W33 制品断言账实 + 判据 6 参数化 + `K_evidence_ref` 首次必红」「删除 8 份 `source-review` 的处置（用户已裁案 b）」三类账实收干净，并一次性销账 8 行（W30 三行 + W31/W32 主从对 + W33 三行，净 OPEN −5）；真机侧完成 13 格矩阵：E01/E03/F2/F3 严格逐字或设备侧逐字，**F5 手动压缩首次 applied（dropped=9，下一轮请求真实缩短 8k tokens）**，F6 自动压缩触发线已越但判 `insufficient_savings:net=-256`（与手动同快照结论不一致，如实交外审），OR 免费池同窗口双超时、super-vl 仍 403 均归 INC。全程零 `.kt`。

---

## 二、六报告分歧裁决（不改方向；逐条给落点）

| # | 分歧点 | 裁决 | 落地 |
|---|---|---|---|
| 1 | 收尾契约第 13 次三处「130」 | 否（采纳，落 W34-01） | 三处改「以 `arch-guard` 实跑为准」 |
| 2 | 制品断言 5 条全自报 + 判据 6 只覆盖 w32 + w32 三值打架 | 否（采纳，落 W34-02） | 判据 6 参数化 + 五类必红 + w32 assertions 4=4 |
| 3 | 删除 8 份 `source-review` 处置二选一 | 否（用户已裁案 b：维持删除） | `W19-14` → `CLOSED` + §5.4 留痕（落 W34-03） |
| 4 | 判据 8（docs 死链存活性检查） | 否（不新增，避免「出生即红」判据） | 转 W34-03 出口判据残余项 |
| 5 | run-as 直写会话库注入（a4 主张） | 否（前提不成立：journal 未实现、会话不持久化） | 改用「32K 档 + 6 轮 × 16k 工具结果」真机构造 |
| 6 | `promptContext()` 三处吞 CE 卫生批（源码审查） | 否（不改方向：`.kt` 顺延） | `W17-02` 行内注记 + 用户裁决第 8 项 |
| 7 | 开代码债偿还波（a6 主张） | 否（需用户授权转代码波） | 维持顺延，挂 W20-02 / W9-01 / W12-02 |
| 8 | W30 三行/W33 三行销账（a4/a6 主张「24 这个数字动不动」） | 否（采纳：外审复核 + 实跑入账齐） | 8 行改 `CLOSED`（主从联动同一提交） |

---

## 三、第 0 车收口（W34-01/02/03）

> 本节「四态」列指**缓解动作实跑结果**，非挂账状态；三行仍 `OPEN`，销账待外部审查复核（见 §六）。

| 行 | 出口判据 | 实跑结果 | 四态 |
|---|---|---|---|
| W34-01 | 收尾契约第 13 次 | 两文件「130」0 命中；三处含「以 `arch-guard` 实跑为准 / 不写死派生数」 | ✅ |
| W34-02 | 制品断言账实 + 判据 6 可执行 | 判据 6 对 w32/w33/w34 正常态均 `OK`；五类必红全 RED；w32 `assertions`=4=`mutation_cases` | ✅ |
| W34-03 | 删除处置结案 | `W19-14` `CLOSED` + §5.4 裁决留痕（用户批注出处 / 26 处引用 / 仓外整合件 / git 恢复路径） | ✅（26 处抬头为残余项） |

**同车销账 8 行**：`W30-01/02/03`、`W31-01`+`W32-01`（主从联动）、`W33-01/02/03`。台账 210 行 / 33 `CLOSED` / 166 `OPEN` / 11 `CARRIED`。

---

## 四、真机矩阵（`evidence/w34-matrix-summary.json`）

### 4.1 核心结论
`state_counts = {PASS:5, PARTIAL:7, VACUOUS:1}`（13 格，无零值键）。正控 = NV Lightning / Nano Omni sentinel 严格逐字。
端点轴 6 格 + 档位轴 T + 功能面 6 格：E01/E03 ✅、E02/E05/E06/E07 ⚠️、T ⚠️、F1 ⚠️、F2/F3/F5 ✅、F4 ⛔vacuous、F6 ⚠️。

### 4.2 格明细
| 格 | 面 | 状态 | 证据要点 |
|---|---|---|---|
| E01 Lightning | 端点 | ✅ | `W34-E01-R1` 逐字、1.8k/69（新 key 录入后首测） |
| E02 Bolt | 端点 | ⚠️ | 连通 + usage 1.4k/6，回复非 strict |
| E03 Nano Omni | 端点 | ✅ | `W34-E03-R1` 逐字、1.8k/112 |
| E05 super-vl 1M | 端点 | ⚠️ | 旧 key 立即 403、新 key ~80s 后仍 403（模型级） |
| E06 OR dots3 | 端点 | ⚠️ | 两次 120s 超时；设备 ping 通 219ms |
| E07 OR north | 端点 | ⚠️ | 同窗口 120s 超时 |
| T 档位 | 32K↔1M | ⚠️ | 两次 readback 均成立；32K 软预算 27.9K 实测；1M 真压 NOT_EXERCISED |
| F1 vision | 多模态 | ⚠️ | 480×240 真图附件链路成立；dots3 SocketTimeout |
| F2 workspace | 工具+审批 | ✅ | 允许一次 → `wrote 13 chars` → 设备侧逐字 `W34WS-F2-Q7K2` |
| F3 skill | skill | ✅ | skill_load 2ms → `W34-SKILL-OK` 逐字 |
| F4 MCP | MCP | ⛔vacuous | 0 server，保留触发格 |
| F5 手动压缩 | 压缩 | ✅ | `dropped=9 kept=11`；下一轮 30.9k→22.9k in |
| F6 自动压缩 | 压缩 | ⚠️ | 触发已越（`net=-256`）；同快照手动可压 ⇒ 结论不一致待核 |

### 4.3 判据订正与环境事件
- **判据订正**：F1 用 480×240 真图（非 1×1）；F2 用设备侧 `run-as` 逐字复核；压缩用「日志 dropped/kept + 下一轮 usage」双向判据。
- **INC-w34-timeout**：OR 免费池同窗口超时（E06/E07/F1），设备侧 ping 通、NV 可用 ⇒ 供给侧。
- **INC-w34-auth**：super-vl 模型级 403（新旧 key 同结论）。
- **INC-w34-autokv**：F6 自动 `net=-256` vs 手动 `dropped=9` 同快照不一致；根因未定位，交外审。
- **环境事实**：`adb input text` 单次 ~333 字符仍成立；32K 档下 6 轮 × ~16k 工具结果即越软预算并进入压缩判定。

---

## 五、四闸门

| 闸 | 结果 | 备注 |
|---|---|---|
| G1 编译 | ✅ rc=0 | 删 APK 重出包 68,407,402 B、sha256 前缀 `b41be9cc92ee6f90` 与 W33 一致（零 `.kt` ⇒ deterministic） |
| G2 arch-guard | ✅ PASS | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`；两次（第 0 车前 / 台账改后） |
| G3 selftest | ✅ 30/30 | 230s，后台跑 |
| G4 fulltest | ✅ 818-0-0 | 14 模块 |

**CI**：推送 1 次，主体 `dc3fb14`；Build run `44559513390` 与 Secret scan run `44559513521` 均 **success**（四字段复核 completed / success / push / 全 40 位 SHA 匹配；Build 五 job 全绿）。tip 纯文档回填按既定降级规则不逐波判 P0、不补链接。

## 六、OPEN 挂账

- **W34-01/02/03** 三行 `OPEN`：缓解动作已做、出口判据已实跑，按台账 §三.3 待外部审查复核后再销账。
- **W30-01/02/03 / W31-01 / W32-01 / W33-01/02/03**：本波改 `CLOSED`（外审复核 + 实跑入账，主从联动同提交）；`W19-14` 因对象删除结案 `CLOSED`。
- **W9-01**：行内新增「W26/W33 两次 F6 实锤 + W34 自动/手动不一致」注记，维持 `OPEN`；**W17-02**：行内新增 `promptContext()` 三处吞 CE（源码审查）注记，维持 `OPEN`；**W24-02**：行内新增「empty output = 首字节前空输出 + tail 未留存」注记。
- **W20-02** 拆分三方 / **W20-05** 1M 真压 / **W21-04** 矩阵未达成格 / **W24-03** journal 二选一 / **W5-03** MCP 明文 / **W5-05** `autoApproveDangerous` / **W5-06** MCP sampling / **W9-05** journal / **W9-11** 设备侧 1M / **W12-02** 四态生产读者：沿用老号，本波不动。

---

## 七、交给外部审查团队的制品

- `docs/analysis/w34-acceptance.md`（本波验收物）
- `evidence/w34-matrix-summary.json`（正式制品，cells[] 带 state/feature_face/glyph/evidence_ref）
- `evidence/raw/w34/`（14 件脱敏真机证据）
- `docs/06-deferred-ledger.md`（W34 段 3 行 + 8 行销账 + `W19-14` 结案 + 3 处行内注记）
- `docs/notes/w29-closeout-checklist.md`（判据 6 参数化 + 五类必红 + W34 摘录）
- `docs/analysis/w33-acceptance.md`、`docs/handoff-20261119-wave33.md`（W34 订正）
- `evidence/w32-matrix-summary.json`、`evidence/w33-matrix-summary.json`（W34 账实订正）

**不含**：会话历史 / 凭据 / 端点完整标识（模型 ID 仅公开模型名）/「建议下一链做什么」。

---

## 八、边界声明（不把推断写成事实）

- 零 `.kt`：310 个 `.kt` 零 diff。
- F6：只记录「自动 net=-256 / 手动 dropped=9」事实与候选方向，不下缺陷结论。
- 1M：只做档位 readback 与 32K 软预算实测；1M tokens 真压不在本波，记 `NOT_EXERCISED`。
- MCP：0 server，`F4_mcp` 记 `VACUOUS`，不推断「MCP 不可用」为 harness 缺陷。
- super-vl 403 / OR 超时：归 INC，不推断为 harness 缺陷。

> 本波止于交接，交由外部团队审查。
