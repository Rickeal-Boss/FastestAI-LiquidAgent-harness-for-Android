# W38 交接 — 判据内容订正 + 全面推进（吞 CE 清零）+ 真机攻破端点轴

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 台账权威版 = `docs/06-deferred-ledger.md`；本波验收物 = `docs/analysis/w38-acceptance.md`；
> 真机制品 = `evidence/w38-matrix-summary.json`；证据 = `evidence/raw/w38/`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 本交接只引用台账 §一已有行首编号，未引入任何不在台账行首的复合编号。

---

## 一、本波做了什么（一段话）

**先裁决分歧、再内容订正、再代码清零、再真机攻端点轴**。第 0 车对四份审查报告的 8 处分歧逐条裁决（全面推进优先、拒新增判据/制品/schema 字段），把 P0-1 `K_coverage_13` 反噬改自洽判定（w32=14/w37=13 双 OK、十一类必红全 RED），给四行老号补 W37 注记，订正 w37-acceptance 六处错数 + 冻结声明 feature_face 值域表 + declared=3 + 判据 1 守恒式两数回显。第 1 车做吞 CE 反模式清零：ExtrasViewModel 18 处 `runCatching` + `ChatViewModel.syncMcpTools` 2 处（本波新发现）全改共享 suspend 安全辅助（三个 helper 提 public 跨模块复用），新增 7 条「取消透传直接用例」（测生产函数本身，区别于 W37 模式自证），销 `W17-02`。真机侧**攻破 W37 卡住的端点轴**：定位 `INC-w37-settings-tap` 根因为「按钮在滚动区外 + 确认框二次点击」（非回归），成功切到 Dots3 512k，E02/T02 转 ✅、F1 多模态输入链路走通、F2 workspace 审批+2 字节回读、缓存 HIT 实证；13 格 `state_counts={PASS:6,PARTIAL:6,VACUOUS:1}`（W37 是 4/8/1）。

---

## 二、四份审查报告分歧裁决（8 条）

| # | 分歧 | 裁决 |
|---|---|---|
| 1 | 全面推进 vs 零代码小件延续 | 采纳全面推进（用户指令 + 报告 B/C/派单卡一致），拒报告 A |
| 2 | 守恒式两数 | 第 0 车 0/0 不注水；第 1 车 W17-02 销账 → 全波 0/1 |
| 3 | `K_coverage_13` 反噬（报告 C P0-1） | 采纳改 `cells_total==len(cells) and >0`；实测 w32=14 格 RED 属实 |
| 4 | 吞 CE 清零 ExtrasViewModel（报告 B P0） | 采纳；另发现 `syncMcpTools` 2 处同源，一并清零 |
| 5 | INC A/B 对照（报告 C P0-2） | 采纳，真机执行 ⇒ 非回归（操作路径问题） |
| 6 | journal / stable_partial 标注 | 拒绝（违反冻结：新增 schema 字段 / 独立裁决域） |
| 7 | G3 selftest 补跑 | 采纳，30/30 PASS（清 W37 欠账） |
| 8 | 豁免程序一句话（报告 A P1） | 采纳为纯文档注记，不新增判据 |

---

## 三、守恒式两数

- 第 0 车：新开 0 / 销账 0（纯内容订正 + 老号注记，不注水）。
- 第 1 车：新开 0 / 销账 1（`W17-02` 满足出口判据）。
- **全波：新开 0 / 销账 1**（`0 ≤ 1` ✅），CLOSED 36→37，W38 段零 `W38-` 新开行。

---

## 四、第 0 车（判据内容订正）

提交 `f1ff95a`。P0-1 去硬编码后 w32(14)/w37(13)/w38(13) 三制品判据 6 全 `OK`、十一类必红全 RED、全树硬编码 13 残留 0；四行老号 `W9-01`/`W17-02`/`W12-02`/`W20-02` 各补 W37 注记维持 OPEN；六处错数回源实测后订正；冻结声明补 7 值 `feature_face` 表；`declared=0→3`；判据 1 加守恒式两数回显（实跑有效）。

---

## 五、第 1 车（全面推进代码波）

提交 `0f2e13e`（syncMcpTools 最终改动在同波，随证据提交）。

1. **吞 CE 清零**：ExtrasViewModel 18 处 + `ChatViewModel.syncMcpTools` 2 处 suspend 调用全部改走 `degradeTo`（读降级）/`runIgnoringCancellation`（写吞普通异常）/`suspendResultOf`（需返回语义）；三个 helper 从 core-data private 提为 public（Kotlin internal 按模块隔离）。
2. **直接用例**：`SuspendCancellationPassThroughTest` 7 个 `@Test`，直接调生产函数断言 CE 原样重抛 + 普通异常降级——这是 W17-02 一直缺的覆盖，比 W37 的模式自证更强。
3. **G10 纪律**：改动全程未调 `LINE_CAPS`、未抬 `TOTAL_LINE_CAP=42500`；ChatViewModel 改后精确回到 500/500、feature-chat 3996/4000（净增 0），未触顶。
4. **EXPECTED_TESTS**：822→829（+7）同提交改 `build.yml` 并对账。
5. **销账**：`W17-02` 翻 `CLOSED`（台账单人落笔 + W38 指路）。

---

## 六、真机矩阵（13 格）

`state_counts={PASS:6, PARTIAL:6, VACUOUS:1}`；34 顶层键 / `K_coverage` 自洽 / `evidence_ref` 全解析 / `iterations_total=13`。

| 格 | W38 | 要点 |
|---|---|---|
| E02_dots3 | ✅ | 端点切换真行使；Dots3 512k 对话正常；缓存 HIT 1536 cached 72.4% |
| T02_512k | ✅ | 档位读数 520.2K 生效（满窗真压 NOT_EXERCISED，判据为读数非真压） |
| F2_workspace | ✅ | file_write 审批链完整 + 设备侧 2 字节回读（4f 4b）+ mtime 本波 |
| F6_auto_compact | ✅ | 新包 `auto_compact=noop reason=below_trigger` 持续落账 |
| F1_vision | ⚠️ | 多模态输入全链路走通；语义部分正确（红方块对/蓝圈答绿），逐字回读判 PARTIAL；未用 1×1 像素 |
| F5_manual_compact | ⚠️ | UI 路径 + 正确 noop 语义；短对话无真实 dropped/kept |
| E01/F3 | ✅ | 沿用 W37 基线，本波未复测（note 已标） |
| E03/E04/T01/T03 | ⚠️ | 沿用 W37 基线，本波未复测；1M 满窗双硬限未解 |
| F4_mcp | ⛔vacuous | 0 server `none_by_design` |

**INC-w37-settings-tap 根因**：`inc_settings_tap_rootcause.json` —— 目标按钮在滚动区外（`[0,0][0,0]`）+ 确认框需二次点击；滚动+点击+确认后切换成功。**非 harness 回归、非设备缺陷**，操作路径问题，走内容订正不新开行。

**回灌级别**：只回灌脱敏截图判据与 `deviceLogTail.txt`（auto_compact 行），不回灌整段 logcat、不回灌凭据 / 未打码账号字段 / 端点完整标识；`evidence/raw/w38/` 隐私扫描 0 命中。

---

## 七、四闸门 + CI

| 闸 | 结果 | 四态 |
|---|---|---|
| G1 编译 | ✅ `BUILD SUCCESSFUL`（含 syncMcpTools 最终改动重编 rc=0） | ✅ |
| G2 arch-guard | ✅ `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0` | ✅ |
| G3 selftest | ✅ 30/30 PASS（补 W37 欠账） | ✅ |
| G4 fulltest | ✅ `tests=829 failures=0 errors=0 skipped=0`（14 模块） | ✅ |
| G20 | ✅ W38 零新开、零 `W38-` 行 | ✅ |
| CI | 待推送后回填 run 链接（见验收物 §5.6）；未回填前不写成 CI 已绿 | ⏳ |

---

## 八、明确未做（逐条带号）

`W20-02`（三方拆分，本波维持触顶水位未拆）｜`W24-02`（断流三处）｜`W24-01`（思考框 4 态）｜`W12-02`（cachedLabel 另一半）｜`W9-01`（命题② + B1 三条 + alreadyCompressed 单指针）｜`W20-05`/`W9-11`（1M 真压）｜`W5-03/05/06`（MCP 外部 https）｜`W9-05`/`W24-03`（journal）｜E03/E04/T01/T03/F3 本波未复测（沿用基线已标注）。

---

## 九、OPEN 挂账

触发信号 / 缓解动作 / 挂账号逐条见 `docs/analysis/w38-acceptance.md` §步 6（9 行，`INC-w37-settings-tap` 已厘清不新开行）。本波销 `W17-02`，其余维持 OPEN，不注水销账。

---

## 十、交给外部审查团队的制品 + 边界声明

**制品**：`docs/analysis/w38-acceptance.md`、本交接、`evidence/w38-matrix-summary.json`、`evidence/raw/w38/`、`docs/06-deferred-ledger.md`（W38 段 + W17-02 销账）、`docs/notes/w29-closeout-checklist.md`（W38 内容订正）、`docs/analysis/w37-acceptance.md`（六处订正）。

**不含**：会话历史 / 凭据 / 端点完整标识（模型名仅公开名）/ 账号字段 / 设备序列号 / 本机路径 / 「建议下一链做什么」。

**边界**：PARTIAL 格的真实原因是「未复测 / 语义部分正确 / 无 dropped」而非能力不足；1M `NOT_EXERCISED`、MCP 0 server `VACUOUS` 不推断为 harness 缺陷；INC 根因是操作路径的推断、证据是滚动+确认切换成功的真机序列；CI 未回填前不构成绿声明。本波不把推断写成事实。

> 本波止于交接，交由外部团队审查。
