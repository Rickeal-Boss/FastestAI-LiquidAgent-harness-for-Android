# W38 验收物 — 判据内容订正 + 全面推进（吞 CE 清零）+ 真机多模型矩阵攻破端点轴

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 本验收物只引用台账 §一已有行首编号，不引入任何不在台账行首的复合编号。

## 抬头：三条规约持续 + 守恒式两数

> **判据冻结**（用户 2026-11-20 指令，W37 起生效）：矩阵 schema `harness-matrix-summary/v1`、判据编号 1–7、四态枚举、34 顶层键定版，不再扩张。W38 全程未新增判据编号 / checklist / 矩阵制品 schema 字段 / CI job / 枚举 / 重出历史制品；P0-1 的 `K_coverage` 去硬编码属冻结声明允许的「让既有判据真会响」。
> **全面推进**：第 0 车判据内容订正 + 第 1 车吞 CE 清零代码波 + 真机多模型矩阵攻破端点轴。
> **守恒式**：第 0 车 `新开 0 / 销账 0`（不注水）；第 1 车修完真债后按实际销账数更新（见 §步 3）。

```
第 0 车新开挂账：0   第 0 车销账：0
第 1 车新开挂账：0   第 1 车销账：见 §步 3
```

---

## 步 1 — 四份审查报告分歧裁决 ✅

证据：`docs/analysis/w38-acceptance.md` 本节（裁决理由见交接 §二）；四份报告原文在会话外，本仓不入库。

| # | 分歧 | 裁决 | 四态 |
|---|---|---|---|
| 1 | 全面推进 vs 零代码小件延续 | 采纳全面推进（用户指令 + 报告 B/C/派单卡一致），拒报告 A | ✅ |
| 2 | 守恒式两数 | 第 0 车 0/0 不注水，第 1 车修完真债改 0/N | ✅ |
| 3 | `K_coverage_13` 反噬（报告 C P0-1） | 采纳改 `cells_total==len(cells) and >0`；实测 w32=14 格 RED 属实 | ✅ |
| 4 | 吞 CE 清零 ExtrasViewModel（报告 B P0） | 采纳；实测 18 处 runCatching 属实 | ✅ |
| 5 | INC-w37-settings-tap A/B（报告 C P0-2） | 采纳，真机端执行；结果 = 非回归（操作路径问题） | ✅ |
| 6 | journal / stable_partial 标注 | 拒绝（违反冻结：新增 schema 字段 / 独立裁决域） | ⛔不适用 |
| 7 | G3 selftest 补跑 | 采纳，30/30 PASS | ✅ |
| 8 | 豁免程序一句话（报告 A P1） | 采纳为纯文档注记，不新增判据 | ✅ |

---

## 步 2 — 第 0 车判据内容订正 ✅

证据：`docs/notes/w29-closeout-checklist.md`、`docs/06-deferred-ledger.md`（W37/W38 段）、`docs/analysis/w37-acceptance.md`、`evidence/w37-matrix-summary.json`。

| 动作 | 实跑 / 复核 | 四态 |
|---|---|---|
| P0-1 `K_coverage` 去硬编码 13 | 改前 `python k6_check.py evidence/w32-matrix-summary.json` → `RED coverage cells != 13`；改后 w32(14 格) / w37(13 格) / w38(13 格) 全 `OK feature/evidence invariants`；十一类必红全 RED（含 `k6_covpop` ⇒ `RED … coverage cells != cells_total`）；全树残留硬编码 13 判定 0 命中 | ✅ |
| P0-3 四行老号 W37 注记 | `W9-01`/`W17-02`/`W12-02`/`W20-02` 各含「W37 行内注记」、状态维持 `OPEN`；实测 `grep -c` = 1/1/1/1 | ✅ |
| 内容订正（六处错数 + declared + F6 措辞 + feature_face 值域 + 判据1回显） | `.kt 7→6`、证据 `14→18`、`417→374`、`3996→3992→3996 未变`、`2515→2506`、`13 件→17 件` 全部回源实测后落笔；旧错数 0 命中；`declared=0→3`；冻结声明补 7 值 feature_face 表；判据 1 回显「本波新开/销账」两数实跑有效 | ✅ |
| 守恒式第 0 车 | W38 段头 `新开 0 / 销账 0`，0 行 `W38-` 新开 | ✅ |

**注**：本步所有改动在第 0 车提交 `f1ff95a`（纯文档 + 制品内容订正）；仓外 `_w34/k6_check.py` 的可执行原文已内联在 checklist 判据 6 代码块，不入版本库（冻结声明既定口径）。

---

## 步 3 — 第 1 车全面推进代码波 ✅

证据：`core-data/.../PromptContextAssembler.kt`、`feature-settings/.../ExtrasViewModel.kt`、`feature-chat/.../ChatViewModel.kt`、`core-data/src/test/.../SuspendCancellationPassThroughTest.kt`、`.github/workflows/build.yml`（EXPECTED_TESTS 829）、提交 `0f2e13e`。

| 项 | 实跑 / 复核 | 四态 |
|---|---|---|
| ExtrasViewModel 18 处吞 CE 清零 | 全部 `runCatching` → `degradeTo`/`runIgnoringCancellation`/`suspendResultOf`；`grep -c runCatching` = 0 | ✅ |
| W17-02 另一半 syncMcpTools（复核报告之外的新发现） | `ChatViewModel.syncMcpTools()` 2 处 suspend 调用改共享安全辅助；净行数 0（ChatViewModel 500/500、feature-chat 3996/4000，G10 未触顶） | ✅ |
| 三个 helper 提升 public 跨模块复用 | `degradeTo`/`runIgnoringCancellation`/`suspendResultOf` 从 core-data private 提为 public（Kotlin internal 按模块隔离铁律），三模块编译通过 | ✅ |
| 取消透传**直接用例**（区别于 W37 模式自证） | 新建 `SuspendCancellationPassThroughTest` 7 个 `@Test`，直接调用三个生产函数断言 CE 原样重抛 + 普通异常降级；G13 @Test 形态首轮违规（显式 `: Unit`）→ 去掉后 arch-guard `OK=27 VACUOUS=1 VIOLATION=0` | ✅ |
| EXPECTED_TESTS 对账 | 严格行首 `@Test` 822→829（+7），同提交改 `build.yml '829'`；fulltest `tests=829 failures=0 errors=0 skipped=0`（14 模块） | ✅ |
| 守恒式第 1 车销账 | W17-02 满足出口判据（suspend 路径 runCatching 清零 + CE 重抛直接用例 7 条）⇒ 翻 `CLOSED`；全波 `新开 0 / 销账 1`，CLOSED 36→37 | ✅ |

---

## 步 4 — 真机多模型矩阵（13 格）⚠️

证据：`evidence/w38-matrix-summary.json`（34 顶层键 / `state_counts={PASS:6,PARTIAL:6,VACUOUS:1}`）、`evidence/raw/w38/`（脱敏）。

| 格 | W37 | W38 | 本波证据 / 四态 |
|---|---|---|---|
| E01_lightning | ✅ | ✅ | 沿用 W37 基线，本波未复测（note 已标） |
| E02_dots3 | ⚠️ | ✅ | `e02_dots3.json`：端点切换真行使、Dots3 512k 对话正常、缓存 HIT（1536 cached 72.4%） |
| E03_north | ⚠️ | ⚠️ | 本波未复测，沿用 W37 基线（note 已标） |
| E04_nv_alt | ⚠️ | ⚠️ | 同上 |
| T01_32k | ⚠️ | ⚠️ | 本波未复测 32K 档；档位读数 ≠ 真压，不冒充 |
| T02_512k | ⚠️ | ✅ | `t02_512k.json`：Dots3 档位读数 520.2K 生效；满窗真压 NOT_EXERCISED，判据为档位读数 |
| T03_1m | ⚠️ | ⚠️ | 1M 满窗双通道硬限未解，`NOT_EXERCISED` |
| F1_vision | ⚠️ | ⚠️ | `f1_vision.json`：多模态输入链路全通（添加图片→模型收到→视觉描述输出）；语义部分正确（红方块对/蓝圈答绿），逐字回读判 PARTIAL；未用 1×1 像素 |
| F2_workspace | ✅ | ✅ | `f2_workspace_write.json`：file_write 审批链完整 + 设备侧 2 字节回读（`4f 4b`=OK）+ mtime 本波 |
| F3_skill | ✅ | ✅ | 沿用 W37 基线，本波未复测；Dots3 模型对工具调用语义不服从（非 app bug），本波未强测 |
| F4_mcp | ⛔vacuous | ⛔vacuous | `none_by_design`（0 server），不冒充 |
| F5_manual_compact | ⚠️ | ⚠️ | `f5_manual_compact.json`：UI 路径 + 正确 noop 语义验证；短对话无真实 dropped/kept，不冒充 PASS |
| F6_auto_compact | ✅ | ✅ | `f6_auto_compact.json` + `deviceLogTail.txt`：新包 14:xxZ 段 `auto_compact=noop reason=below_trigger` 持续工作 |

**INC-w37-settings-tap 根因定位（⛔→根因厘清）**：`inc_settings_tap_rootcause.json` —— 目标「设为当前」按钮在滚动区外（`bounds [0,0][0,0]`）+ 确认框需二次点击。滚动列表后坐标进入视口、点击、确认即成功切换；新包设置页完全正常。**结论：非 harness 回归、非设备缺陷，是操作路径问题**；走内容订正，不新开挂账行。

**判据可执行性**：判据 6 正常态对 w32(14)/w37(13)/w38(13) 全 `OK`；十一类必红对 W38 制品全 RED（`_w38/mut/`，仓外）。`evidence_ref` 全仓内存在、`iterations_total=len(iterations)=13`、`K_coverage` 自洽判定生效。

**回灌口径**：只回灌脱敏截图判据与 `deviceLogTail.txt`（auto_compact 行）；不回灌整段 logcat、不回灌凭据 / 未打码账号字段 / 端点完整标识（模型名仅公开名）。`evidence/raw/w38/` 隐私扫描 0 命中。

---

## 步 5 — 四闸门 + G20 + CI

证据：`_w38/{archguard3,selftest,fulltest2}.log`（仓外日志，本验收物记结论）。

| 闸门 | 结果 | 四态 |
|---|---|---|
| G1 编译 | ✅ `BUILD SUCCESSFUL`（`:app:compileDebugKotlin`，含 syncMcpTools 最终改动重编 rc=0） | ✅ |
| G2 arch-guard | ✅ `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G13 首轮因显式 `: Unit` 违规→修正后恢复基线） | ✅ |
| G3 selftest | ✅ **30/30 PASS**（补跑 W37 欠账，239s；含 G13/VAC 机制自证） | ✅ |
| G4 fulltest | ✅ `tests=829 failures=0 errors=0 skipped=0`（14 模块） | ✅ |
| G20 挂账号 | ✅ W38 段 0 行新开、`W38-` 编号 0 命中；handoff 只引用既有行首号 | ✅ |
| CI | 见 §5.6（推送后回填 run 链接；CI 绿须附 run 链接） | ⏳ 待推送 |

---

## 明确未做（逐条带挂账号前缀）

- `W20-02` 拆分三方：本波未拆 feature-chat / feature-settings / core-context；仅维持 G10 水位（feature-chat 3996/4000、ChatViewModel 500/500 贴顶）。下一波任一功能增量触顶前必须先拆。
- `W24-02` 断流三处（RemoteLlmEngine EOF 无 `[DONE]` ⇒ Completed、decodeEvent error 帧、EventReducer report）：本波未动，需 MockWebServer 红态判据。
- `W24-01` 思考框（双层玻璃 / AGSL 零尺寸）：本波真机仅目视确认 UI 存在，未改代码、未做 4 态截图判据。
- `W12-02` 另一半 `ContextBudget.cachedLabel` 接四态、`usageObserved` 口径归一：本波未接。
- `W9-01` 命题②（`:407` 时机）、B1 其余三条、`alreadyCompressed` 单指针：本波未动。
- `W20-05`/`W9-11` 1M 满窗真压：双通道硬限未解，`NOT_EXERCISED`，不冒充 ✅。
- `W5-03/05/06` MCP 外部 https（G15b）：0 server `none_by_design`，本波未开外部 server。
- `W9-05`/`W24-03` journal：独立裁决域，本波未开波。
- T01 32K 档、E03 north、E04 nv_alt、F3 skill 的重测：本波未复测，沿用 W37 基线并如实标注，不冒充本波实测。

---

## 步 6 — OPEN 挂账（触发信号 + 缓解动作 + 挂账号）

> 本波销 `W17-02`（见步 3）。以下未销，逐条给触发信号 / 缓解动作 / 挂账号。

| 挂账号 | 触发信号 | 缓解动作 | 状态 |
|---|---|---|---|
| `W20-02` | 下一波任一功能 `.kt` 增量触 G10（feature-chat 3996/4000、ChatViewModel 500/500 已贴顶） | 先拆三方腾行数下沉纯函数，禁调 `LINE_CAPS` / 抬 `TOTAL_LINE_CAP` | OPEN |
| `W24-02` | 流式响应 EOF 无 `[DONE]` 仍落 Completed / 半截回答被当完整 | MockWebServer「200 + delta 后关流」红态：请求数≥2 且非 `Completed(LENGTH)`；EOF→`Failed(retryable)`、error 帧识别、EventReducer 消费 report | OPEN |
| `W24-01` | 思考框展开态双层玻璃 / AGSL 零尺寸崩溃 | 展开态不套第二层玻璃或 `overlayPanel=true`；`Size(0,0)` 纯函数断言 + 浅深×收展 4 态截图 | OPEN |
| `W12-02` | `ContextBudget.cachedLabel` 仍非四态读者 / `usageObserved` 与 `StreamAccumulator.kt:226` 口径分叉 | `cachedLabel` 接 `promptCacheStatus` 四态；`usageObserved` 归一 | OPEN |
| `W9-01` | `:407` 调用时机 / B1 三条 / `alreadyCompressed` 单指针（`snapshot.compression != null`）导致已压缩长会话永久 already_compressed | 命题②与单指针合并设计；W35 11s 序列 + W36 4× `net=-256` + W37 `nothing_to_drop` 回归钉 | OPEN |
| `W20-05`/`W9-11` | 1M 满窗真压需求出现 | 解除 adb input ~333 字符 + file_read 16k/次双通道硬限后做真压注入；当前 `NOT_EXERCISED` 不冒充 | OPEN（外部阻塞） |
| `W5-03/05/06` | 需接外部 https MCP server | G15b（禁明文流量）合规路径 + 审批链验证；当前 0 server `none_by_design` | OPEN（独立裁决域） |
| `W9-05`/`W24-03` | journal 恢复 / 工具记账需求拍板 | 独立内核硬化波，用户终局裁决后开波 | OPEN（待裁决） |
| `INC-w37-settings-tap` | 已根因定位（按钮在滚动区外 + 确认框），非回归非设备缺陷 | 不新开挂账行；操作路径已在本波真机走通，经验证据入 `evidence/raw/w38/inc_settings_tap_rootcause.json` | 已厘清（不新开行） |

---

## §5.6 提交与 CI

**提交序列**（本波 3 次推送序列，按实际执行回填）：
1. 第 0 车 `f1ff95a`（纯文档 + 制品内容订正）。
2. 第 1 车代码波 `0f2e13e`。
3. 证据 + 验收物 + 交接（本提交）。

> CI run 链接：待推送后回填。按 W28 降级规则，纯文档 tip 的 Build 0 run 不写成构建通过；主体提交的 Build / Secret scan 四字段须实取 run 链接后填入本节，未取到前本节判据为 ⏳ 待验证，不冒充绿。

---

## 交给外部审查团队的制品清单

- `docs/analysis/w38-acceptance.md`（本验收物）
- `docs/handoff-20261120-wave38.md`（W38 交接）
- `evidence/w38-matrix-summary.json`（13 格矩阵，34 顶层键，`K_coverage` 自洽判定）
- `evidence/raw/w38/`（脱敏：deviceLogTail.txt + 6 个 JSON 判据证据 + f2/f5 辅助）
- `docs/06-deferred-ledger.md`（W38 段 + W17-02 销账注记）
- `docs/notes/w29-closeout-checklist.md`（判据冻结声明 + W38 内容订正 + W38 实跑摘录）
- `docs/analysis/w37-acceptance.md`（六处错数订正）

**不含**：会话历史 / 凭据 / 端点完整标识（模型名仅公开名）/ 账号字段 / 设备序列号 / 本机路径 / 「建议下一链做什么」。

---

## 限定（不把推断写成事实）

- Dots3 对 skill 工具调用「语义不服从」是模型行为，未做正控对照排除题面问题，故 F3 沿用 W37 基线、不写成「Dots3 工具能力缺失」。
- F1 vision 的「蓝圈答绿」是逐字回读的 PARTIAL 事实；不推断为图像缩放/编码 bug（未做多图交叉）。
- 1M 满窗、MCP 外部 server、32K 档、north/nv-alt 端点本波未复测，沿用基线并已逐格标注，不冒充本波实测。
- 手动压缩无本波真实 `dropped/kept`（短对话正确 noop）；不把历史行冒充本波。
- INC-w37-settings-tap 根因是「操作路径」的推断，证据是滚动+确认后切换成功的真机序列；不写成代码 bug 也不写成设备缺陷。
- CI 四字段待推送实取；本节在回填前不构成「CI 已绿」声明。
