# W31 交接 — 多模型×多功能面真机矩阵 + 第 0 车零 `.kt` 收口

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，分支 `main`）。
> 台账权威版 = `docs/06-deferred-ledger.md`；本波验收物 = `docs/analysis/w31-acceptance.md`；
> 真机矩阵制品 = `evidence/w31-matrix-summary.json`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」。

> **本交接不引入新挂账号**：全文出现的编号字面量均为台账 §一 已有行首行（W31-01/02/03 本波新落且已落台账；
> W24-02 / W24-03 / W17-02 / W20-02 为既有老号）。未新增任何不在台账行首的复合编号。

---

## 一、本波做了什么（一段话）

第 0 车把 W31 段三行（脱敏回退 / 必红块 WS 依赖 / 收尾契约占位）的缓解动作做完并**全实跑销账**（三行均
`CLOSED`）；随后在真机上完成多模型×多功能面矩阵：三个 NVIDIA 端点全部连通，Lightning 端到端跑通
reasoning + content，并触发完整工具循环（审批闸门 → file_write → workspace 真机落盘）。全程**零 `.kt`**。

---

## 二、第 0 车收口（三行全 `CLOSED`）

| 行 | 出口判据 | 实跑结果 | 四态 |
|---|---|---|---|
| W31-01 脱敏回退 | 全树敏感形态复扫 0 + 判据 3b 正常态 OK + 注入样本 RED | 凭据文件名/序列号 0 命中；3b 正常态 `OK no credential filename`；printf 拼装注入 `RED credential filename hit` | ✅ |
| W31-02 必红块 WS + 判据 1 写死 | 不设 WS 时必红块 rc=0 输出 RED + 判据 1 两段 OK、注入重复号 RED | 判据 2 `RED mutation invariant broken`；判据 5 `RED iteration/state invariant broken`；判据 4 `RED affirmative wording remains`；判据 1 W29/W30 两段 `rows=3` OK、注入重复 `RED duplicate` | ✅ |
| W31-03 收尾契约 + 派生文案 | 占位 0 + handoff 抬头口径一致 + G20 不写死 + W29 §5.3 + 两制品派生文案一致 | 两占位删（余订正注记内历史指称）；抬头改「不引入新挂账号」；§四 G20 改「以实跑为准」；W29 §5.3 删错配；W29 制品四处派生文案同步；W30 制品 inherited_check/glyph mapping/cells_provenance | ✅ |

> 逐条证据路径见验收物 §2。**本波三行销账依据 = 仓内判据实跑输出，非真机取证**（与 W28-03 口径一致）。

---

## 三、真机矩阵（`evidence/w31-matrix-summary.json`）

### 3.1 核心结论
`state_counts = {PASS:3, PARTIAL:2, VACUOUS:4, FAIL:0}`。这是 W29/W30 连续两波全 `VACUOUS` 后
**首次真实行使**。

### 3.2 格明细
| 格 | 面 | 状态 | 证据要点 |
|---|---|---|---|
| E01 Lightning | 连通+端到端+工具循环 | ✅ | 连接 1942ms；PROBE-OK（reasoning+content）；file_write→workspace 落盘；1.9k in / 55 out |
| E02 Bolt | 连通 | ⚠️ | 连接 3418ms；e2e/vision 未行使 |
| E03 Nano Omni | 连通 | ⚠️ | 连接 4214ms；omni 视觉未行使 |
| F2 workspace write | 工具真机落盘 | ✅ | `run-as cat …/workspace_w31tool.txt` → `W31-TOOL-OK` |
| F7 tool loop | 工具循环+审批 | ✅ | tool_calls → 审批闸门 → 允许一次 → 执行 → 终答 |
| F1 / F3 / F5 / F6 | vision / skill / 手动压缩 / 自动压缩 | ⛔vacuous | SAF 选图未自动化 / 未触发 / 无长对话配方 / 上下文 2K 未越阈 |

### 3.3 正控
`positive_control_verified_by = device_execution`（E01 真机端到端 + 工具落盘），证明判据能响、链路能通、
文件能落，不是纸面通过。

### 3.4 环境事件（归 INC，非代码缺陷）
- **INC-wifi-drop**：设备 WiFi 两次假连接（网关 100% 丢包 → `UnknownHostException`）；`svc wifi` 开关修复。
  分类：环境抖动，非 harness 缺陷。
- **INC-key-entry**：密钥 UI 录入首次 403，根因 = tap 光标停字段中间致 DEL 只删左侧、残留拼接成错 key；
  `input text` 对下划线保真（实测 `a_b`→3 字符），非下划线问题。修复 = MOVE_END + 批量 DEL 清空 +
  整串录入 + `ls secrets/*.bin` 校验 mtime。分类：操作流程问题，非 harness 缺陷。

---

## 四、OPEN 挂账（本波不新增行，沿用台账现值）

- **W24-02** 静默断词/重试：本波真机未复现完全静默（连接失败有「网络抖动，正在重试…」提示）；MockWebServer
  红态用例仍为必备，不得用真机替代。挂 `W24-02`。
- **W20-02** 拆分 / **W17-02** skillNames：需 `.kt`，挂既有老号，下一波拆分承接。
- **W24-03** journal 二选一：第 19 次顺延，多值待裁，等用户二选一。挂 `W24-03`。
- 真机矩阵未行使面（F1/F3/F5/F6 + OpenRouter）作为验收物 §3.4 触发信号留痕，**不新开台账行**。

---

## 五、四闸门

| 闸 | 结果 | 备注 |
|---|---|---|
| G1 编译 | ✅ rc=0 | `assembleDebug` 删旧 APK 强制重出包；真机安装 Success |
| G2 arch-guard | 见验收物 §四 | 本地仓库根实跑 |
| G3 selftest | 见验收物 §四 | 零 `.kt` 不回归 |
| G4 fulltest | 见验收物 §四 | `@Test` 计数不变 |

CI 绿以验收物 §5.6 回填的 run 为准。

---

## 六、交给外部审查团队的制品

- `evidence/w31-matrix-summary.json`
- `docs/analysis/w31-acceptance.md`
- `docs/06-deferred-ledger.md` W31 段
- `evidence/w30-matrix-summary.json`（W31 订正后）
- `evidence/w29-matrix-summary.json`（W31 订正后）
- `docs/notes/w29-closeout-checklist.md`（判据修复 + W31 必红实跑摘录）

**不含**：会话历史 / 凭据 / 端点完整标识（一律模型 ID + 打码）/「建议下一链做什么」。

---

## 七、边界声明（不把推断写成事实）

- E02/E03 仅验连通，**不等于**端到端通过；vision/tool 在这两端点未行使，不推断其可用性。
- F6 `below_trigger` 是正确行为，不是「压缩通过」。
- 真机 WiFi 抖动是环境事件，**不推断**为 harness 网络层缺陷；同理密钥录入 403 是操作流程，**不推断**为
  Keystore 或端点保存缺陷。
- 本波零 `.kt`，所有源码级行为均为现役已工作协议，**未新增也未修改任何 Kotlin 代码**。

> 本波止于交接，交由外部团队审查。