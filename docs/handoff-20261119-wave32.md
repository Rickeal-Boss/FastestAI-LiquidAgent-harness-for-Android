# W32 交接 — 四外审分歧裁决（不改方向）+ 第 0 车零 `.kt` 收口 + 多模型×多功能面真机矩阵

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，分支 `main`）。
> 台账权威版 = `docs/06-deferred-ledger.md`；本波验收物 = `docs/analysis/w32-acceptance.md`；
> 真机矩阵证据 = 工作区 `_w32/evidence/W32_SUMMARY.json`，正式制品 = `evidence/w32-matrix-summary.json`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」。

> **本交接只引用台账 §一已有行首编号**：W32-01/02/03（本波新落、已落台账行首）、
> W31-01、W30-01/02/03、W24-02、W21-04、W20-02、W28-03 均为既有行首行。未引入任何不在台账行首的复合编号。

---

## 一、本波做了什么（一段话）

四份外审报告逐条核实后**全部维持不改方向**（方案席 7 条裁决全 `改方向=否`）；第 0 车把 W32 段三行
的缓解动作做完并实跑——W31-01 的两条双向失效判据从源头真修（3→0 命中、必红 `%%` 哑火改两段拼装真响）、
W31 制品 14→29 键补回 schema 与断言、`FAIL:0` 删除、MCP 显式 NOT_EXERCISED；真机侧完成多模型×多功能面
矩阵（8 PASS / 3 PARTIAL / 3 VACUOUS），OpenRouter 双节点首次真跑、super-vl 1M 端点新增（403 未通）。
全程**零 `.kt`**。

---

## 二、四报告分歧裁决（方案席真实回传，7 条全 `改方向=否`）

| # | 分歧点 | 裁决 | 落地 |
|---|---|---|---|
| 1 | 四报告总体方向 | 否 | 零 `.kt` + 现有载体自洽 |
| 2 | W31-01 回 `OPEN` | 否（回 OPEN 正确） | 台账 W31-01 + W32 复核注记 |
| 3 | `evidence/SCHEMA.md` 是否新建 | 否 | 制品内自包含 schema + 验收物注记，不新建第 6 份制品 |
| 4 | W30-01/02/03 处置 | 否 | 维持 `OPEN` + 行内注记消沉默，不代关 |
| 5 | MCP 面 | 否 | 显式 `mcp: NOT_EXERCISED`，不塞 VACUOUS 格 |
| 6 | `iterations[]` 派生 | 否（非编造） | 逐条 `derived_from` + 防误读条款（事件链重述 ≠ 独立取证计数） |
| 7 | 端点/1M 判据 | 否 | 档位=会话预算 ≠ contextLength；1M 真压单列 ⛔vacuous；多模态禁 1×1 |

> **主理人过程教训**：曾误把成员「进行中」状态当成「已回传裁决」写进文档，已纠正并写入工作日志；
> 本交接所有裁决均取自成员真实回传。

---

## 三、第 0 车收口（W32-01/02/03）

| 行 | 出口判据 | 实跑结果 | 四态 |
|---|---|---|---|
| W32-01 | 3b 判据双向失效源头真修 | 正常态 `grep` 3→0 命中（`OK no credential filename`）；必红 `%%` 哑火改两段拼装 → `RED credential filename hit`（正控 1）；双原文入 checklist `:244-261` | ✅ |
| W32-02 | 制品 schema 退化 | 14→29 键；`FAIL:0` 删；9 格带 `feature_face`；MCP NOT_EXERCISED；断言/必红/自测 3=3=3；`iterations_total=len=6` | ✅ |
| W32-03 | 收尾契约第 11 次 | 「单次推送」→「推送 2 次」；G20 不写死；PAT 句闭环；§5.2 补挂老号；3 处无主语拼接加主语；判据 1 的 W31 段输出入仓 | ✅ |

> 质量审查席 6 项通过 + 1 项 P1（3 处「声称修复零 diff」）已真修、复核 ✅；守卫 G2 实跑 PASS。

---

## 四、真机矩阵（`evidence/w32-matrix-summary.json`）

### 4.1 核心结论
`state_counts = {PASS:8, PARTIAL:3, VACUOUS:3}`（14 格，无零值键）。正控 = Lightning `W32-FINAL-LIGHT-9D4K` 严格逐字。

### 4.2 格明细
| 格 | 面 | 状态 | 证据要点 |
|---|---|---|---|
| E01/E02/E03 | Lightning/Bolt/Nano Omni 端到端 | ✅ | 逐字 sentinel + usage；假阳性行已 strict 复核取代 |
| E05 super-vl 1M | 端点新增 + 端到端 | ⚠️ | 1M 端点新增成功，两次 HTTP403（模型级授权） |
| E06/E07 OR dots3/north | 端到端 | ✅ | 首次录入 OR key 后逐字 PASS（1.7K/520.2K、1.1k/242） |
| F2 workspace / F3 skill / approval_gate | 工具落盘 / skill / 审批闸门 | ✅ | `w32probe.txt` 逐字 / 81.8% cache / 真实点按「允许一次」 |
| F1 vision / T tier / E05 | 视觉语义回读 / 档位 / 1M | ⚠️ | 480×240 真图链路通但语义失败 / sweep+1M readback / 403 |
| F4 MCP / F5 手动压缩 / F6 自动压缩 | 未行使面 | ⛔vacuous | 无外部 server / 未越字符阈 / below_trigger（正确行为） |
| 1M 大上下文真压 | 顶层 NOT_EXERCISED | ⛔vacuous | super-vl 403 + 缺 16 轮配方，触发信号留痕 |

### 4.3 判据订正与环境事件
- **判据假阳性已修正**：早期 `sentinel in UI 全集` 被输入气泡污染 → 收紧 `strict_sentinel`（排除 `Reply_with_*` 与失败横幅）；归因订正列于 `W32_SUMMARY.json.notes`。
- **INC（非 harness 缺陷）**：INC-w32-keyentry（OR hasSecret 无 bin，录入后 PASS，可用性观察挂 `W21-04`）、INC-w32-auth（super-vl 403）、INC-g1-reinstall（主理人 G1 闸门误触真机安装，UI 重置后继测）。

---

## 五、四闸门

| 闸 | 结果 | 备注 |
|---|---|---|
| G1 编译 | ✅ rc=0 | `app-debug.apk` + `app-release.apk`；默认任务含真机安装（见 INC-g1-reinstall） |
| G2 arch-guard | ✅ PASS | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0` |
| G3 selftest | ✅ 30/30 | 256s，后台 |
| G4 fulltest | ✅ 818-0-0 | 14 模块，零 `.kt` 无回归 |

**CI（见验收物 §5.6）**：推送后回填 commit 与 run。

---

## 六、OPEN 挂账（本波不新增行，沿用台账现值）

- **W32-01/02/03** 三行 `OPEN`：缓解动作已做、出口判据已实跑，但按台账 §三.3 待外部审查复核后再销账。
- **W31-01** 本波回 `OPEN`（判据失效），真修后待销账。
- **W30-01/02/03** 维持 `OPEN` + 行内注记消沉默（不代关，出口条件未满足）。
- **W24-02** 静默断词：MockWebServer 红态用例仍必备，不得用真机替代。
- **W20-02** 拆分三方 / **W21-04** 矩阵未达成格（OR keyentry 可用性观察）/ **W24-03** journal 二选一：沿用老号。

---

## 七、交给外部审查团队的制品

- `docs/analysis/w32-acceptance.md`（本波验收物）
- `evidence/w32-matrix-summary.json`（正式制品，cells[] 带 state/feature_face/glyph）
- `docs/06-deferred-ledger.md` W32 段 + W31-01 回 OPEN + W30 三行注记
- `evidence/w31-matrix-summary.json`（W32 订正后：OR key 理由 + W32 复核注记）
- `docs/notes/w29-closeout-checklist.md`（判据 3b 双态原文 + 判据 1 的 W31 段输出）
- 工作区 `_w32/evidence/W32_SUMMARY.json` + `iterations.jsonl` + `matrix-facts.jsonl`（真机判据，脱敏后按路径给出）

**不含**：会话历史 / 凭据 / 端点完整标识（模型 ID + 打码指纹）/「建议下一链做什么」。

---

## 八、边界声明（不把推断写成事实）

- E02 首测 / E05 早期 / tier 早期的假阳性行已由 strict 复核件取代；F1/F4/F5/F6 如实 ⚠️/⛔vacuous，**不推断**端点可用性。
- 1M 档 readback 与计量条是会话预算读数，**不等于** 1M 大上下文真压；后者未达（403 + 字符阈未越）。
- INC-w32-auth / INC-w32-keyentry / INC-g1-reinstall 归环境/操作事件，**不推断**为 harness 网络层或 Keystore 缺陷。
- 本波零 `.kt`；所有源码级行为均为现役已工作协议，未新增也未修改任何 Kotlin 代码。

> 本波止于交接，交由外部团队审查。
