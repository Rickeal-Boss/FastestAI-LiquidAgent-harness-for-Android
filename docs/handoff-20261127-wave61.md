# W61 交接 — journal v1 内核落地 / 杀进程恢复卡两路 / FreezeClock 接线

## 一、本波做了什么（一段话）

W60 把用户全量裁决权威落笔并明确 R5 journal = 做、独立波号 W61、单点开波。本波兑现 R5 的**内核层**：新增 `AgentRunJournal`（run 边界事件溯源最小集）、`FreezeClock`（冻结感知时钟，注入式不改 `RunState` 默认口径）、`FileRunJournalStore`（落盘 `run-journal.jsonl`，有界轮转）、`RunRecoveryStore`（**读落盘**配对 started/settled，供杀进程恢复卡）；接线到 `AgentRunner.run()` 生命周期、`AppContainer` 装配与 `AppNavHost` 恢复卡。真机坐实恢复卡两路（发现未结算 → 弹卡 → 继续补 settled / 丢弃清盘）与端点 A 回声回归；FreezeClock 的 113s 长请求不误熔 ✅，但灭屏 60s 期间设备未真正 cgroup 冻结（`frozenMs=0`）⇒ **冻结扣时链未触发**，按 R4 纪律不写成通过。四闸门全绿、904 冻结未破、台账状态零变化、隐私门禁三路全 0。四份 W60 外审的分歧逐条裁决（详见 §二）。

## 二、裁决（四份外审分歧，逐条给值）

### 2.1 P0-1「裁决原文被改写 + 哈希链循环自证」= 驳回（外审基准件非本机用户原件）

外审以 `/data/inputs/我的裁决.txt`（4306 B / `017abe8e…`）为基准，称第 27 行有「是否」「（写『解除 / 保留』）」被删。本波实机复核：用户桌面原件 `<private-home>/Desktop/我的裁决.txt`（本机路径占位） = 4270 B / sha256 `8a618fe9…`（CRLF），LF 归一后 4306→4270 差 36 B 恰为外审基准件多出的两段字面；入档件 `docs/analysis/我的裁决_原文.txt` 按 `.gitattributes` 归一 LF 后与桌面原件**逐字节一致**（第 27 行本就无该字样）。结论：入档哈希链指向真实桌面原件、非循环自证；外审基准件来自 W59 外审转引的另一版，外审无桌面访问权所致。**不回改入档件、不改用户原件**，登记为「两版并存 + 差异来源」注记。W60 三处「源文件哈希 `8a618fe9…`」正确，维持。

### 2.2 P0-2「争议键判 ✅ 超出自定判据」= 部分接受，降为限定态

W60 验收物步 4 判据只含「写入→保存→重开保留 + 恢复原状」（保存链），真机已逐字实证闭环；但步 8 标题写「真机保存链 + 真实请求实证」，其中「请求体探针含 `parallel_tool_calls:false`」**未行使**。裁决：该格 ✅ 仅对**保存链**成立，不覆盖运行时请求体；W61 段登记限定语「✅（保存链闭环；请求体探针未行使，超出步 4 自定判据，不冒充运行时请求实证）」。不回改 W60 验收物历史四态（沿用 W59→W60「注记不回改」先例）。补 `applyCustomProtocol` JVM 用例会破 904 冻结，不采纳。

### 2.3 P1/P2 小项

- **P1-1 隐私门禁 SSID/MAC/端点盲区**：本波 `g7_secretgate_w61.txt` 在规范闸门三路 0 之外**补扫三类**（SSID/MAC/端点 URL/PAT/本机路径）全 0 并留痕；取证直接脱敏落盘，未产生 W60 RUN1 式「先落隐私再删」中间态。按 M3 净 0 不新增判据编号 / G 编号 / CI job，沿用「已知盲区 + 人工补扫留痕」路径。
- **P1-2 W61 波号与 R1 生效 W62 张力**：本波**未依赖 R1 缩域授权**。顶格对象（GenerationRound 400/400、feature-chat 3999/4000、core-kernel 449/450）零触碰；G10 一次触顶（RunState 402 > 400）按 W48 先例精简 KDoc 腾行解决，AppContainer 427 > 400 按「触顶即拆分」拆出 `RunRecoveryStore.kt`。R1 仍按 W62 生效，不提前执行。
- **P1-3 tip run 未回填**：W60 `45962041114` / tip `85293bb` 按降级规则不作 P0、不回改 W60 文档；本波沿用同一口径，在 W61 段注明「W60 ② tip run 已按公开 API 实取，不新建第 3 提交」。
- **P2 小项**：W57 MANIFEST「备注4」重号、L3「对象已完成」、入档抬头、25/27 措辞、L1 语义边界句——均为历史文档注记，本波不回改历史，登记为已知项。

## 三、账本与守恒

- 台账状态列实跑：**216 行 / 39 CLOSED / 166 OPEN / 11 CARRIED**，与 W60 逐值相等；本波零销账、零新开、状态列零变化。
- 段数 35 → 36（本波新增 W61 段）；G20 全文扫描 ID 集合不减（本波不删任何 handoff 里的挂账号）。
- `EXPECTED_TESTS` 维持 904、零新增 `@Test`（journal 的 JVM 行为不写用例，靠真机判据）；`EXPECTED_CLAIMS` 15 未变（journal 写点不是 `AgentLogStore` 形态，不进探针认领面）。
- G19 仍 VACUOUS（1 条）：摘 VACUOUS 需改 `EXPECTED_CLAIMS` 与 `VACUOUS_BASELINE`（冻结判据常量），命中 M3 净 0 + 用户「不要判据升级→空转→重新取证」双红线，**条件触发、本波不做**。

## 四、改动面

| 文件 | 动作 | 说明 |
| --- | --- | --- |
| `core-agent/.../journal/AgentRunJournal.kt` | 新增 | run 边界最小集（run_started / run_settled），固定形态无用户内容，sink 端口不依赖 core-data |
| `core-agent/.../journal/FreezeClock.kt` | 新增 | 冻结扣时：`active = wall − frozen`，下限截零；只服务墙钟 SOFT/HARD，不接首字节看门狗 |
| `core-agent/.../loop/RunState.kt` | 改 | 注入式 `freezeClock`（null 保持原口径，904 零漂移）；新增 `frozenMillis()`；精简冗余 KDoc 腾行至 400/400 |
| `core-agent/.../AgentRunner.kt` | 改 | `journal?.runStarted` / `runSettled`（含 activeMs/frozenMs/tools/cancelled）；精简 KDoc 181/200 |
| `core-data/.../FileRunJournalStore.kt` | 新增 | `run-journal.jsonl` 落盘，256KB / 200 行轮转，CE 原样上抛 |
| `core-data/.../RunRecoveryStore.kt` | 新增 | **读落盘**配对 started/settled（AppContainer 427 > 400 后按触顶拆分） |
| `core-data/.../AppContainer.kt` | 改 | 装配 journal / runRecovery / FreezeClock 工厂；精简 KDoc 391/400 |
| `app/.../AppNavHost.kt` | 改 | `LaunchedEffect` 读盘判未结算 → 恢复卡 AlertDialog（继续/丢弃）；精简 KDoc |
| `app/.../res/values/strings.xml` | 改 | 恢复卡标题/正文/两按钮文案（正文如实写「会话正文尚未持久化」） |
| `evidence/raw/w61/` | 新增 | 13 制品 + MANIFEST + manifest_check，逐件入仓字节实算 |

**行数预算**：core-agent 6178/6500、core-engine 1668/2600、core-data 2806/4500、core-kernel 449/450、GenerationRound 400/400、feature-chat 3999/4000、app 1597/1600（余 3，R1 W62 腾行触发信号）。三顶格对象零触碰。

## 五、四闸门 + 真机

- 闸 1 `assembleDebug` RC=0；闸 2 arch-guard **PASS 27 OK / 1 VACUOUS(G19) / 0 violation / 0 self-fault**；闸 3 selftest **30/30 PASS（313s）**；闸 4 fulltest **tests=904 failures=0 errors=0 skipped=0**。
- 探针形态自检 rc=0（14 锚点 / 15 认领 / 11 反哨兵）；台账符号自检 rc=0（327 kt 文件，21 类 / 65 方法引用可解析）；隐私门禁三路 0 + 补扫三类 0。
- 真机（OPPO PDRM00 / Android 13，唤醒三步法）：
  - 恢复卡：冷启动无 journal 无卡 ✅ → 发消息 run_started 落盘 ✅ → force-stop 后仅 started ✅ → 重启弹卡（**读盘配对**）✅ → 继续补 settled + 卡消失 ✅ → 注入未结算行再弹卡 ✅ → 丢弃清盘 + 卡消失 ✅。
  - FreezeClock：113s 长请求正常完成（`activeMs=113638`，UI 1.9k in / 3.4k out）✅ 不误熔；`frozenMs=0` ⚠️ 冻结扣时链未触发（前台活跃请求持有唤醒锁，设备未真正冻结），按 R4 不写成通过。
  - 端点 A × 1M 档长文回声 ✅（非满窗真压）；密钥留设备，未回灌。
  - 截图 1080×2400 真图，上缘 y205..285 纯色块打码端点标识带，`verify()+IEND+load+尺寸+单色复核` 全通过。

## 六、CI（公开 API 四字段，curl 直连实时数据）

本波提交与 run 号见 §九收尾契约；主体代码提交双 run 全绿为交付判据，回填提交按 `docs/**` paths-ignore 只触发 Secret。最终 tip 交 atom 实取，不硬写 sha。

## 七、明确未做

- W3-05 `journal/FreezeClock` 老号：**不改状态列**（本波实现内核层，销账权在台账段，留 W61 段注记，不自行销账）。
- 冻结扣时真机触发：条件未满足（设备未真正冻结），不制造假场景、不写成通过。
- G19 摘 VACUOUS：改冻结判据常量，命中 M3 净 0，条件触发。
- 会话正文持久化：本仓既有架构不落盘，恢复卡「继续」只清标记不还原正文，卡面已如实告知。
- A2 运行时行为级：加 JVM 用例会破 904 冻结，维持条件触发；请求体探针未行使（P0-2 已限定）。
- R1 / R2 / R3-a：按 W62 / W63 生效，不提前执行；R4 永久不得写成通过。

## 八、OPEN 挂账

活跃主线不变：拆分三方（W20-02）｜压缩四条（W9-01）｜断词红态（W24-02）｜吞 CE 清零｜journal（W9-05 / W24-03，内核层已落地、销账待裁决）｜MCP 明文（W5-03/05/06）｜1M 真压（W20-05 / W9-11）｜思考块渲染（W24-01）。外审 6 行（W34-01/02/03 + W35-01/02/03）仍需 W61 显式界定销账权，本波不代裁。

## 九、收尾契约（三处数字相等）

- 本波计数：**N 提交 / M 条 run**（主体 + 回填，逐号见 §六与 W61 段；最终 tip 不硬写）。
- handoff / 验收物 / 台账 W61 段三处计数相等。
- 交付判据恒锚**唯一代码提交双 run 全绿**；回填提交按降级规则不作 P0。

## 十、交给外部审查团队的制品 + 边界声明

**制品**：本交接、`docs/analysis/w61-acceptance.md`、`docs/w61-dispatch.md`、`docs/06-deferred-ledger.md` 的 W61 段、`evidence/raw/w61/` 全部文件（逐件见 `MANIFEST_w61.txt`）。

**不含**：会话历史 / 凭据 / 未打码账号字段 / 端点标识（型号名、URL、provider 一律「端点 A」占位）/ 设备序列号 / SSID / 本机路径 / 用户私人图片 / 整段 logcat /「建议下一链做什么」。

**边界**：① journal 仅事件溯源最小集 + 冻结时钟接线，**非会话正文持久化**；② 恢复卡两路以读盘配对为判据，「继续接上正文」不适用（卡面如实告知）；③ FreezeClock 冻结扣时链本波未触发，`frozenMs=0` 不写成通过；④ 三顶格对象零触碰，G10 腾行 / 拆分均按先例；⑤ P0-1 外审基准件非本机原件，按实机哈希驳回、两版并存注记；⑥ 本波止于交接，交由外部团队审查。
