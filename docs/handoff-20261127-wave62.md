# W62 交接 — 长文本黑块修复（玻璃图层预算）/ W61 外审 P1/P2 收口

## 一、本波做了什么（一段话）

用户复报「思考框 / 输出框文字一多就出现渲染错误」，真机复现为**整块纯黑圆角矩形**（截图中气泡下方「复制 / 检查 JSON」与 usage 行仍正常）。本波先取证后动手：在真机上抓 guard 前状态，确认黑块内**语义树里文字节点存在**（uiautomator 可读出全文、bounds 与视口一致），即布局正常、只有绘制失败；再逐层核对玻璃引擎，定位到根因——每个玻璃节点一次绘制最多创建 5 个 `GraphicsLayer`（外阴影 / 内阴影 / 高光 / 背景录制带 RenderEffect / 内容 `Offscreen`），尺寸恒等于节点自身，而模型气泡是**单个 LazyColumn item**，长回答节点实测 `984×61546` px（另一轮 `984×63924`），按全高申请纹理必失败 ⇒ 图层内容未定义 ⇒ 纯黑。修复为新增 `GlassLayerBudget`（单边 4096 px + 面积 4_000_000 px² 双约束）并在五处接预算（`DrawBackdropNode` 的 measure / draw、`ShadowNode`、`InnerShadowNode`、`HighlightNode`），超预算降级为半透明实底（与「关背景模糊」同语义），不黑屏。同波收 W61 外审 P1-2 / P1-3：`AgentRunJournal.record` 的 sink 异常改 CE 原样上抛；`AgentRunner` 把 `runStarted` 移进 try（原位置异常会漏 unlock `runMutex` 并留永久未结算 run），并把 KDoc 里与实现不符的 NonCancellable 表述按实现订正；`FreezeClock` 只订正语义注释（注入源测的是系统 suspend，对 cgroup 冻结无观测能力），不换源、不写「已解决」。四闸门全绿、904 冻结未破、台账状态列零变化。

## 二、W61 外审分歧裁决（逐条给值，不回改 W54–W61 历史四态）

### 2.1 P0-1「台账两条 journal 老号零 W61 行内注记、行内仍写全仓零实现」= 收

`W9-05` / `W24-03` 两行各补 **W62 行内注记**：写清「内核层已落地（`AgentRunJournal` / `FreezeClock` / `FileRunJournalStore` / `RunRecoveryStore` + `AgentRunner` 接线）」「行内『全仓零实现 / 第 7 次顺延』为 W11 时点值」「W62 补两处内核缺陷」「前台服务未做 ⇒ 范围 3/4」。**不销账、不改状态列、不合并、代数不递增**。`W3-05` 属 `CARRIED` 链条（指向 `W9-05`），改动会断链，维持原样。

### 2.2 P0-2「两版《我的裁决.txt》权威歧义 + Q14 语义相反」= 取证权威与裁值权威分离，定版不阻塞

- 仓内入档件 `docs/analysis/我的裁决_原文.txt`（4226 B / sha256 `8f109e99…`，LF）与用户桌面原件（4270 B / `8a618fe9…`，CRLF）在行尾归一后**逐字节一致**，差 44 B = 44 个换行符 ⇒ **取证权威成立**，入档件即桌面原件。
- 外审所称上传版（4306 B / `017abe8e…`）第 27 行为「**是否**按 Q10 的 R1–R4 生效后解除冻结（写『解除 / 保留』）」，即**待填问卷形态**；仓内版为**已填值形态**。两版第 27 行以外相同。
- 裁决：**取证权威 = 入档件 / 桌面原件**；**裁值权威不由本波单方面认定**——W60 段「依 M1 由主理人逐组处置、非用户字面填值」的三处措辞**不回改历史**，本波只登记「两版并存 + 语义差异 + 执行按两版交集」：无论哪版为准，R1（W62 生效）/ R2·R3-a（W63）/ R4（永久不得写成通过）的执行路径相同，故**定版不阻塞任何动作**。Q14 不因本注记变成新的用户字面裁决。

### 2.3 P1-1「零 `@Test` + R5 范围缺前台服务」= 维持冻结 + 如实标范围

新增 `@Test` 须同提交改 `EXPECTED_TESTS`（904），命中 M3「冻结净 0」与用户「不新增判据」字面 ⇒ **本波不加用例**；`W9-05` 范围如实标 **3/4**（前台服务未做），不写「R5 已全额兑现」。渲染修复的判据放在**真机四态 + 必红变异**上（见 §五），不冒充 JVM 回归。

### 2.4 P1-2「复刻吞 Throwable 前提不成立 / KDoc 与实现不符」= 收

- `AgentRunJournal.record`：`catch (_: Throwable)` 前加 `catch (c: CancellationException) { throw c }` —— sink 是 `:app` 侧 `FileRunJournalStore.append`（同步阻塞 IO），取消发生在写盘中途时吞 CE 会让 Job 从 cancelled 变 completed，与 W54 修掉的 10 处同型。
- `AgentRunner` 类 KDoc 原写「NonCancellable 只留给不 emit 的副作用（journal settled 行）」，而 `finally` 里**没有** `withContext(NonCancellable)`。按实现订正为「settled 是同步、不 emit 的副作用，直接写在 finally 里即可，不需要 NonCancellable」——不引入无必要挂起点。

### 2.5 P1-3「`runStarted` 在 try 之外」= 收（优先级上调）

原 `AgentRunner` 在 `:99` 写 `runStarted`、`try` 从 `:115` 才开始：`:100-114`（`TurnContext` 构造 / `launch(LAZY)` / `activeRun.set` / `busyState`）任一抛出都会跳过 `finally` ⇒ **`runMutex` 永不 unlock**（此后每次 run 都 GateRejected）+ 永久未结算 run（每次冷启动误弹恢复卡）。本波把 try 提前到 `runStarted` 之前，finally 覆盖整段。真机实测：停止按钮路径落 `cancelled=true` 的 settled（`activeMs=10303`），随后新 run 被接受（无 GateRejected）⇒ 两条后果都不再成立。

### 2.6 FreezeClock 冻结源语义（报告一 P2 疑难）= 只订正注释 + 降级表述，不换源

源码事实：`wall = System.nanoTime()`（`CLOCK_MONOTONIC`）；注入源 `elapsedRealtimeNanos − uptimeMillis×1e6`（`BOOTTIME − MONOTONIC`）测的是**系统 suspend**。两者语义不同：suspend 下 `nanoTime` **本就不走**（MONOTONIC 不含 suspend），扣减变成重复扣（方向安全但语义重复）；cgroup 进程冻结下两源都照走 ⇒ 扣不到、防护无效。且 **cgroup 冻结无公开时钟可观测量**，换源无解。故本波只把 KDoc 改成如实表述并保留端口，**不写「已解决墙钟误熔」**；W61 e1 的「113s 不误熔」是裸墙钟（113s ≪ HARD 600s）结论，**与本链无关**，不作 FreezeClock 证据。

### 2.7 P2 项处理

- 收：`core-agent` 6183 与实跑 6182 之差 = `CalibrationPolicy.kt` 末行无换行（`wc -l` 不计末行）——本波按实跑值记录，不写成「玄学时点差」。
- 登记不回改：恢复卡 dismiss 不清标记（fail-safe 形态，判定为可接受）、`findUnsettled/recent/size` 暂无消费者、rotate/append/discard 静默失败留痕缺失、`run-journal.jsonl` 不在 `EXPECTED_CLAIMS` 认领面（M3 净 0）、W57 MANIFEST 备注4 重号等历史小项。
- 驳回：报告要求「先腾 app 行」——本波两处 `.kt` 均在 `core-agent` 与 `core-design`，`app` 零 diff，腾行即空转。

## 三、账本与守恒

- 台账状态列实跑：**216 行 / 39 CLOSED / 166 OPEN / 11 CARRIED**，与 W61 逐值相等；本波零销账、零新开、状态列零变化。
- 段数 36 → 37（新增 W62 段）；`W9-05` / `W24-03` 两行补 W62 行内注记（竖线仍 7，未破表）。
- `EXPECTED_TESTS` 维持 904（零新增 `@Test`）；`EXPECTED_CLAIMS` 15 未变；G19 仍 VACUOUS（摘除须改冻结常量，命中 M3，条件触发）。
- 口径注记：台账统计以「`^\| W\d+-\d+` 行 + 状态列 `split('|')[4]`」为准——`W6-08` 一行注记含 9 个半角竖线，按 7 列拆分口径会漏 1 行（本波实跑踩到，已在 `g6_ledger_w62.txt` 写明）。

## 四、改动面

| 文件 | 动作 | 说明 |
| --- | --- | --- |
| `core-design/.../liquid/GlassLayerBudget.kt` | 新增 | 单边 4096 px + 面积 4M px² 双约束；`allows()` 纯函数，零/负尺寸放行 |
| `core-design/.../liquid/DrawBackdropModifier.kt` | 改 | measure：超预算内容层改「瘦」图层（clip + shape，无 `Offscreen`）；draw：超预算跳过背景录制与导出录制，只画底色 + 内容（debug 包一次性 Log.d 留痕） |
| `core-design/.../liquid/shadow/ShadowModifier.kt` | 改 | 超预算早退（阴影层尺寸 = 节点 + radius×4，更高） |
| `core-design/.../liquid/shadow/InnerShadowModifier.kt` | 改 | 超预算早退（Offscreen + RenderEffect） |
| `core-design/.../liquid/highlight/HighlightModifier.kt` | 改 | 超预算早退（只画内容，不画描边高光） |
| `core-agent/.../AgentRunner.kt` | 改 | `runStarted` 移进 try，finally 覆盖原 :100-114 窗口；KDoc 按实现订正 |
| `core-agent/.../journal/AgentRunJournal.kt` | 改 | sink 异常：CE 原样上抛，其余吞（写盘失败不改主流程） |
| `core-agent/.../journal/FreezeClock.kt` | 改 | KDoc 语义订正（suspend vs cgroup 冻结；不宣称已解决） |
| `evidence/raw/w62/` | 新增 | 15 制品 + MANIFEST + manifest_check（15/15 OK） |

**行数预算**（实跑）：core-design **12565/12600（余 35，新顶格信号）**、core-agent 6207/6500、core-data 2811/4500、app 1597/1600（本波零 diff）、feature-chat 3999/4000、core-kernel 449/450。三顶格对象零触碰；`LINE_CAPS` / `TOTAL_LINE_CAP` 未调。

## 五、四闸门 + 真机

- 闸 1 `assembleDebug` RC=0；闸 2 arch-guard **PASS 27 OK / 1 VACUOUS(G19) / 0 violation / 0 self-fault**；闸 3 selftest **30/30 PASS**；闸 4 fulltest **tests=904 failures=0 errors=0 skipped=0**。
- 探针形态自检 rc=0（14 锚点 / 15 认领 / 11 反哨兵）；台账符号自检 rc=0（328 kt / 21 类 / 65 方法）；隐私门禁三路 0 + 补扫六类 0（台账 1 处 W60 历史说明性字样）。
- 真机（OPPO PDRM00 / Android 13 / 1080×2400，唤醒三步法）：

| 格 | 判据 | 实测 | 四态 |
| --- | --- | --- | --- |
| 5.1 | 缺陷复现（修复前） | 同一内容整块纯黑，语义树内文字节点存在且 bounds 与视口一致 | ✅ 已复现 |
| 5.2 | 长回答正文可读（修复后） | 节点 `984×61546`（另一轮 `984×63924`），逐行可读、无黑块，guard 行留痕 | ✅ |
| 5.3 | 思考块展开 | 实底浅色 + 深色可读文字、chevron 翻转，无黑块 | ✅ |
| 5.4 | 短气泡对照 | 无 guard 行 ⇒ 未降级，玻璃质感与文字正常 | ✅ 未降级 |
| 5.5 | 必红变异 | 阈值放大为 `1_048_576` / `Long.MAX_VALUE` 后同一请求 ⇒ 黑块复现 | ✅ 必红 |
| 5.6 | 取消路径 settled | 停止后 `run_settled … cancelled=true`（`activeMs=10303`） | ✅ |
| 5.7 | 取消后互斥锁释放 | 紧随其后新 run 被接受（无 GateRejected、无死锁） | ✅ |
| 5.8 | 设置页玻璃控件 | 卡片 / 开关正常渲染，无降级、无黑块 | ✅ |
| 5.9 | 长请求不误熔 | 34~36s 生成正常结算（`cancelled=false`），与 FreezeClock 无关 | ✅ 仅证不误熔 |
| 5.10 | FreezeClock 扣时链 | `frozenMs=0`（设备未 cgroup 冻结，且源对 cgroup 冻结无观测能力） | ⚠️ 未触发 / 不写通过 |

- 截图 5 张均为 1080×2400 真图，上缘 y=216..278 打码端点标识行；签名 + IEND + `verify()` + load + 尺寸 + 打码带单色复核全通过。

## 六、CI（公开 API 四字段，curl 直连实时数据）

| 提交 | 内容 | run | 结论 |
| --- | --- | --- | --- |
| `0c7b158` | 代码 7 改 + 1 新增 + 证据 17 文件 | Secret `46023332877` | **failure**（3b-ext 命中本波证据 `g7` 的「模式描述行」字面路径） |
| `0c7b158` | 同上 | Build `46023332768` | success |
| `2be32ec` | 仅证据两文件（改写描述 + 重算 MANIFEST） | Secret `46023808244` | success |
| `2be32ec` | 同上 | Build `46023808226` | success |
| `532ad18` | 交接 / 验收物 / 派单卡 / 台账段 / 证据计数 | Secret `46025336301` | success |
| `532ad18` | 同上（改动含 `evidence/raw/w62/*.txt`，未命中 `build.yml` 的 `docs/**`、`**/*.md` 忽略 ⇒ Build 照跑） | Build `46025336627` | success |

**交付判据锚 `532ad18`（tip）双 run 全绿**；`2be32ec` 亦双绿，三次提交的代码面逐字节相同（后两次只改 `evidence/raw/w62` 与 `docs/`）。`0c7b158` 的 Secret 红如实登记：属「判据自身制造正常态命中」——补扫描述行写了路径字面，被 3b-ext 的 `git grep` 命中，非代码或凭据问题；修正方式是把描述改为语义命名（不加 allowlist、不豁免路径），沿用「扫描器对自己的文档也一视同仁」的既有纪律。

## 七、明确未做

- 长回答折叠 / 分页：`feature-chat 3999/4000` 无余量，且非本缺陷根因（网格化渲染代价才是），不做。
- 新增 JVM 用例：破 904 冻结 + 命中「不新增判据」字面，条件触发。
- FreezeClock 换源：cgroup 冻结无公开时钟可观测量，换源无解；保持「已接线、目标场景无观测能力」的如实状态。
- `run-journal.jsonl` 纳入 `EXPECTED_CLAIMS` 认领面：M3 净 0，登记为已知项。
- 恢复卡 dismiss 语义：判定为 fail-safe（下次仍弹卡，不丢 settled），不改。
- R1 缩域腾行：本波 app 零 diff，未腾行（不空转）；core-design 余 35 登记为新顶格信号。
- R2 / R3-a：按 W63 生效，不提前执行；R4 永久不得写成通过。

## 八、OPEN 挂账

活跃主线不变：拆分三方（W20-02）｜压缩四条（W9-01）｜断词红态（W24-02）｜吞 CE 清零｜journal（W9-05 / W24-03，内核层已落地、范围 3/4、销账待裁决）｜MCP 明文（W5-03/05/06）｜1M 真压（W20-05 / W9-11）｜思考块渲染（W24-01，本波修复的是**同一现象的另一条成因**：超长节点图层预算，不替代 W24-01 的双层玻璃 / 零尺寸成因）。外审 6 行（W34-01/02/03 + W35-01/02/03）仍需界定销账权，本波不代裁。

## 九、收尾契约（三处数字相等）

- 本波计数：**3 提交 / 6 条 run**（① `0c7b158` 代码 + 证据：Build 绿、Secret 红（证据自命中）；② `2be32ec` 证据修正：双 run 全绿；③ `532ad18` 交接 + 验收物 + 派单卡 + 台账段 + 证据计数：双 run 全绿 = **交付判据锚**）。
- handoff / 验收物 / 台账 W62 段三处计数相等。
- 交付判据恒锚**双 run 全绿的那一次提交**（`532ad18`，tip），不硬写后续回填 sha；本交接之后若再有纯文档回填提交，按其自身规则只触发 Secret，按 W56 自洽口径由 atom 实取、不并入 P0 计数。

## 十、交给外部审查团队的制品 + 边界声明

**制品**：本交接、`docs/analysis/w62-acceptance.md`、`docs/w62-dispatch.md`、`docs/06-deferred-ledger.md` 的 W62 段与两行注记、`evidence/raw/w62/` 全部文件（逐件见 `MANIFEST_w62.txt`）。

**不含**：会话历史 / 凭据 / 未打码账号字段 / 端点标识（型号名、URL、provider 一律「端点 A」占位或打码）/ 设备序列号 / 无线网络标识 / 本机路径 / 用户私人图片 / 整段 logcat /「建议下一链做什么」。

**边界**：① 本修复是**渲染降级守卫**，不改长回答的展示形态（不折叠、不分页）；② 判据集中在真机四态 + 必红变异，**没有 JVM 用例**（904 冻结）；③ FreezeClock 仅语义订正，未解决 cgroup 冻结观测（无公开源），`frozenMs=0` 不写通过；④ 三顶格对象零触碰、`LINE_CAPS` 未调；⑤ 台账仅补注记，不销账、不改状态列；⑥ 本波止于交接，交由外部团队审查。
