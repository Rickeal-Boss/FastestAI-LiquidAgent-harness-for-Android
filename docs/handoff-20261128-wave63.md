# W63 交接 — R2 触发线按档位 / R3-a MCP 内网透传 / journal 半写行 / AgentRunner 收尾序

## 一、本波做了什么（一段话）

按用户 W60 裁决「R2 / R3-a 于 W63 生效」推进，本波是**真代码波**：① 会话级自动压缩的触发线从固定常数
`COMPACT_TRIGGER_CHARS = 50 227`（恰等于 32K 档 L3 硬字符 cap ⇒ 该档自动压缩**结构上不可达**，越线时已被熔断）
改为按档位计算 `hardCharCap × compressThreshold`（32K 档 37 670、128K 档 171 417），并夹在 `1 .. cap-1`
（`compressThreshold` 是无约束 Float，设成 1.0 会让缺陷原样复活）；② MCP 的 `allowPrivateNetwork` 从「只落盘」
改为**正向透传**到 `HttpTransport.post` 的两道闸（`UrlGuard.check` + `GuardedDns`），同时把
`blocked_unsafe_url:<REASON>` 从 `transport_error` 中分离出来 —— 此前用户在设置页永远只看到「网络不好」；
③ journal 落盘配对只采信「kind 前缀 + 结尾 `at=<纯数字>`」的行（半写行不再进恢复卡），内存侧与落盘侧共用
`AgentRunJournal.wellFormed`（顺带消掉 W62 记的「双侧判据漂移」观察位）；④ `AgentRunner.finally` 改为
「清理 → unlock 同处嵌套 finally」，`runSettled` 抛 CE 时清理与解锁必达。真机八格 + 双档位 JVM 对照 + 两次必红变异
全部成立；台账零销账零新开，状态列 216/39/166/11 逐值不变。**外审 6 行仍未销**，阻塞项写实列在 §八。

## 二、三份外审分歧裁决（逐条给值，不回改 W54–W62 历史四态）

### 2.1 「两枚章」（报告一 §一）：W9-05 / W24-01 本波不出清 = 因出口判据未满足，不是无人盖章

- **W9-05**：R5 范围 = journal 内核层（W61 已落地，W62 补两处缺陷）+ **前台服务接入（未做）** ⇒ 范围 3/4。
  出口判据里的「doze 强杀场景」需要前台服务才能稳定产生，本波零改动此域 ⇒ 维持 `OPEN`，补 W63 行内注记。
- **W24-01**：三条根因已修（双层玻璃 / 零尺寸进 AGSL / 巨纹理预算），但行内出口判据还含
  「**浅色主题下**同一气泡展开后可读」与「`Size(w,1)` 进 lens shader 的 JVM 复现」两条 —— 前者本波未复测、
  后者受 904 冻结（新增 `@Test` 命中 M3）。故维持 `OPEN`，注记写清「症状面已齐、剩余两条未行使」。
- **对报告一判断的回应**：M1 下放的是**派单权**，不是「把未满足的出口判据改写成已满足」的权力。若外审认为
  台账需要「部分销账」态，请先裁「状态列是否允许 `PARTIAL`」—— 现行只允许 `OPEN`/`CLOSED`/`CARRIED`，
  本波不擅自新增状态值（那会让 `G20` 判红且破坏十七波零漂移的口径）。

### 2.2 R5 与前台服务的边界（报告一 §一末）= 归 R5 剩余，不新开 R6

前台服务不是新方向，而是 R5「杀进程可恢复」在生产场景（doze / 厂商清理）下的**前提条件**：没有前台服务，
恢复卡只能覆盖「进程被杀但 journal 已落盘」的窄窗口。本波**未做**，故 R5 保持 3/4，不写成「已全额兑现」，
也不新开编号（避免「一个缺陷拆多条」的老问题）。

### 2.3 外审 6 行（W34-01/02/03 + W35-01/02/03）：可自主，但本波**不做**，阻塞项写实

审查席立场是「依 M1 自主处置、不再等单点批复」。本波实跑复核（`g6_ledger_w63.txt`）：
`k6_check.py` 对 w32/w33/w34 三制品正常态均 `OK feature/evidence invariants`；`k7_check.py` 两条分支均 OK
（`W62, declared=0`）；`「130」` 在两文件 0 命中；w32 制品 `assertions=4 / mutation_cases=4`；w34 制品 `5/5` + selftest 非空。
**未满足项两条**：
① 判据 6 的**五类必红**（含 `k6_ref` 的 `RED missing ref`）本波只跑了正常态，必红未逐类复跑入仓 ⇒ 不写「出口判据满足」；
② W35-01 的出口判据含「全树 `§2.5a` 0 命中」，实跑仍有 3 处命中（台账 W35-01 行自身、W35 交接、W35 验收物）
—— 三处都是**描述本次修正本身**的元引用、非悬空指针，但「0 命中」的字面不成立，需要先裁「元引用是否计入」。
故 6 行维持 `OPEN`，本波零销账。W64 若要做，前置 = ①必红逐类复跑入仓 ②§2.5a 口径裁定。

### 2.4 `.gitattributes` 存量（莫斯线 §二.5、报告二未提）= 收，path-scoped 执行

`evidence/raw/w51/g3_selftest.txt` 实测：入仓 blob 2 846 B / 40 行、40 个 CRLF；**当前**工作树 2 806 B（LF-only）。
机制（审查席回源订正）：`.gitattributes` 声明 `*.txt text eol=lf`，`git check-attr` 亦为 `text: set` / `eol: lf`，
**不是**「被当二进制」；`git add --renormalize .` 之所以空转，是因为 **git 的 eol 归一化只去掉紧邻 `\n` 的
那一个 `\r`，`\r\r\n` 归完仍会留下一个裸 `\r`**（`git show` / 原始字节 2 886 B 即这种 CR CR LF 形态）。
处置：显式 `\r\n → \n`、再删剩余裸 `\r`，得到 2 806 B / 40 行 / CR=0；path-scoped add 后索引 blob 与工作树
逐字节一致，`git ls-files --eol` 由 `i/crlf w/lf` 变 `i/lf w/lf`（本次提交后生效）。
**该文件不在任何 MANIFEST 内**（`grep g3_selftest evidence/raw/w51/MANIFEST_w51.txt` 为空），故不影响历史哈希口径。
全库 `i/crlf` 存量计数因此从 1 → 0。

### 2.5 `GlassLayerBudget` 4096 → 设备能力探针（报告一 §三.3）= 登记，不做

`core-design` 12565/12600（余 35）是本波最强的行数约束。改探针要新增 GL 上下文读取与降级分支，
且无法在 JVM 侧验证（需真机 GL 上下文）⇒ 与「本波四次改动零触碰 core-design」的边界冲突。登记为增强项：
「阈值应来自 `GL_MAX_TEXTURE_SIZE` 实读之半；当前 4096 在纹理上限 4096 的旧 GPU 上偏晚降级」。

### 2.6 压缩触发线按档位（R2，本波主项）= 收，并加两条外审没提的加固

- 方案与 token 侧软预算同尺（`hardContextBudgetTokens × compressThreshold`），只是换量纲；
- **夹上限** `1 .. cap-1`：`compressThreshold` 无约束，设 1.0 时触发线 = cap ⇒ 缺陷复活的通道必须堵死（写进 KDoc 与用例）；
- **可达性不等式写实**：L3 闸比的是 `systemChars + promptChars(body)`，触发线比的是不含 system 的
  `committedChars` ⇒ 32K 档可达的充要条件是 `systemChars < 12 557`。system 变长会重新退化，届时应重估触发线，
  不得宣告「已修」了事（这条写进 `compactTriggerChars` KDoc，防下一波当缺陷重开）。

### 2.7 MCP 内网透传（R3-a）= 收，并补一处外审未提的缺陷

外审（W61 P1-1）只提「正向透传」。回源发现第二层缺陷：`McpClient.invoke` 的 `status == 0` 分支把
`blocked_unsafe_url:<REASON>` 折成 `transport_error` ⇒ W54 想要的「策略拒绝 vs 网络故障」在 UI 上不可分。
本波一并修（原样上抛原因码），这也是本波真机判据能成立的支点。
**判据空命中提示（写进验收物）**：loopback 被 `UrlGuard` **无条件**放行（`:core-net/.../UrlGuard.kt:172-191`），
用它做「内网开关」判据是**假绿**；本波用非 loopback 私网字面量 + https，且判据锚在 `blocked_unsafe_url:PRIVATE_NETWORK`
与 `transport_error` 的**差异**上。

### 2.8 报告二 P3（`runSettled` 位于 unlock 之前）= 收，但采用「清理 → unlock」而非「unlock 前置」

审查席建议「把 `unlock()` 提到 finally 最前，或嵌套 try/finally」。实测推演后取**后者**：unlock 在前会让
另一协程在这两步之间拿锁并 `activeRun.set(新 run)`，随后被本 run 的清尾抹成 null（丢取消句柄 + 忙碌态误报）。
最终形态是嵌套 `try { runSettled } finally { 清理…; unlock() }`，`activeRun` 改 `compareAndSet` 未做（观察位）。

### 2.9 报告二「末提交 run ID 交 atom 实取」= 已实取

W62 第 5 个提交（`6873fc8`）的 Secret run = **`46026829837`**（success，2026-11-27T15:39:21Z），本波补记于此，
不回改 W62 正文（沿用「注记不回改」先例）。

## 三、账本与守恒

- 台账状态列实跑：**216 行 / 39 CLOSED / 166 OPEN / 11 CARRIED**，与 W62 逐值相等；零销账、零新开、状态列零变化。
- 段数 37 → 38（新增 W63 段）；`W9-05` / `W24-01` / `W24-03` 各补 W63 行内注记（竖线仍 7，未破表）。
- `EXPECTED_TESTS` 维持 904（零新增 `@Test`，`git diff | grep -c '^+.*@Test'` = 0）；`EXPECTED_CLAIMS` 15 未变；G19 仍 VACUOUS。

## 四、改动面

| 文件 | 动作 | 说明 |
| --- | --- | --- |
| `core-context/.../context/BudgetFormula.kt` | 改 | 新增 `compactTriggerChars`（`hardCharCap × compressThreshold`，夹 `1..cap-1`），KDoc 写可达性不等式与散文偏早边界 |
| `core-agent/.../context/SettledCompressPolicy.kt` | 改 | `shouldApply` 增**必填** `triggerChars`；`COMPACT_TRIGGER_CHARS` 降为 32K/单测基线（保留不删） |
| `core-agent/.../context/SettledCompressPolicyTest.kt` | 改 | `belowTriggerDoesNotApply` 改**双档位对照**（37 670 / 171 417 四个断言）；其余 5 处调用补实参；**零新增 @Test** |
| `feature-chat/.../chat/ConversationOps.kt` | 改 | 触发线取 `compactTriggerChars`（与 `compressTargets` 共用同一 `policy` 实例）；`nothing_to_drop` 前置判与 `shouldApply` 用同一值 |
| `feature-chat/.../chat/AutoCompactDiagnosticsTest.kt` | 改 | 既有 4 处 `shouldApply` 调用补 `triggerChars`；零新增用例 |
| `core-mcp/.../mcp/McpTransport.kt` | 改 | `post` 增 `allowPrivateNetwork`（端口只透传、不裁决） |
| `core-mcp/.../mcp/McpClient.kt` | 改 | 两处 post 传 `config.allowPrivateNetwork`；`status==0` 分支保留 `blocked_unsafe_url:*` |
| `core-mcp/.../mcp/McpModels.kt` | 改 | `allowPrivateNetwork` KDoc 从「只落盘」订正为「已透传到两道闸」 |
| `core-mcp/.../mcp/McpClientTest.kt` | 改 | `FakeTransport` 补形参（忽略不用）；零新增用例 |
| `core-data/.../NetMcpTransport.kt` | 改 | 形参透传给 `HttpTransport.post` |
| `core-agent/.../journal/AgentRunJournal.kt` | 改 | 新增 `wellFormed`（kind 前缀 + runId 32 位 hex + 结尾 `at=<纯数字>`），两侧配对共用 |
| `core-data/.../RunRecoveryStore.kt` | 改 | 落盘配对改用 `wellFormed` |
| `feature-settings/.../McpServerListScreen.kt` | 改 | `McpRow` 增 `allowPrivateNetwork`（审查席 P2-1：原来该字段不进列表，编辑即被硬写回 `false`） |
| `feature-settings/.../ExtrasViewModel.kt` | 改 | `loadMcp()` 映射 `allowPrivateNetwork` |
| `feature-settings/.../SettingsRoute.kt` | 改 | `allowPrivateNetwork = row.allowPrivateNetwork` |
| `core-agent/.../AgentRunner.kt` | 改 | finally 收尾序（清理 → unlock，嵌套 finally）；KDoc 记采纳的偏离 |
| `evidence/raw/w51/g3_selftest.txt` | 改 | 行尾存量清理：2 846 → 2 806 B 纯 LF（不在 MANIFEST 内） |
| `evidence/raw/w63/` | 新增 | 见 `MANIFEST_w63.txt` |

**行数预算**（实跑）：core-context 1567/1650、core-agent 6233/6500、core-mcp 792/1400、core-data 2813/4500、
feature-chat **3999/4000（零净增）**、core-design 12565/12600（余 35，零触碰）、app 1597/1600（零 diff）、core-kernel 449/450（零触碰）。
`AgentRunner` 191 行（G12 ≤ 200）。`LINE_CAPS` / `TOTAL_LINE_CAP` 未调。

## 五、四闸门 + 真机

- 闸 1 `assembleDebug`：RC=0、`BUILD SUCCESSFUL`；两轮独立实跑 APK sha256 均为 `7de9eaa3…`（确定性出包）。
- 闸 2 arch-guard：`result=PASS ok=27 vacuous=1 violation=0 fault=0`（G19 仍 VACUOUS）。
- 闸 3 selftest：`30/30 PASS`（297–304 s）。
- 闸 4 fulltest：`tests=904 failures=0 errors=0 skipped=0`（14 模块；XML mtime 与实跑同窗）。
- 探针形态自检 rc=0（14 锚点 / 15 认领 / 11 反哨兵）、台账符号自检 rc=0（328 kt / 21 类 / 65 方法）、
  隐私门禁三路 0 + 补扫六类 0（CI 语义；本机默认全扫红仅由 gitignored 构建日志造成）。
- 末次冻结快照 R1 原文：`core-context 1571/1650`、`core-agent 6241/6500`、`core-data 2814/4500`、
  `feature-chat 3999/4000`、`feature-settings 3424/3500`、`core-design 12565/12600`、`app 1597/1600`、`core-kernel 449/450`。
- 真机（OPPO PDRM00 / Android 13 / 1080×2400，唤醒三步法）：

| 格 | 判据 | 实测 | 四态 |
| --- | --- | --- | --- |
| G0 | 修复前 32K 档同内容 | `auto_compact=noop reason=below_trigger` | ✅ 基线 |
| G1 | 修复后同内容同档位 | `auto_compact dropped=26 kept=47 cache_invalidated=false` | ✅ |
| G2 | 128K 档同内容 | `below_trigger`（触发线 171 417，未越线） | ✅ 对照成立 |
| G3 | 双档位 JVM 对照 + 两次必红变异 | 12 tests 绿；变异 A/B 各 1 failed；`RESTORED_IDENTICAL` | ✅ |
| G4 | MCP 开关关 | `error: blocked_unsafe_url:PRIVATE_NETWORK` | ✅ |
| G5 | MCP 开关开 | `error: transport_error`（过首闸、无服务监听） | ✅ |
| G5b | 端到端拉到 `tools/list` | 无私网 https MCP server | ⚠️ 未行使 |
| G6 | 半写行冷启动 | 无恢复卡、无「继续/丢弃」 | ✅ |
| G7 | 完整行冷启动 + 继续 | 弹卡 + 补 settled | ✅ 正控 |
| G8 | 取消 settled + 互斥释放 | `cancelled=true`（13 246 ms）；下一 run 28 007 ms 正常结算 | ✅ |
| G9 | 长回答渲染回归 | 正文可读、无黑块 | ✅ |
| G10 | sink 抛 CE 时 unlock 必达 | 生产 sink 同步阻塞、不抛 CE | ⛔vacuous |
| G11 | MCP 开关「保存 → 重开 → 再保存」往返 | 存 true 重开显示开；存 false 重开显示关；再存 true 仍为 true | ✅ |
| G12 | 半写行 + 合法 hex runId（runId 形态校验后） | 不弹卡；完整 hex 行弹卡并补 settled | ✅（合流形态仅代码级 ⚠️） |

- 截图 3 张 1080×2400 真图：`png_e1_chat_after_cancel` / `png_e2_recovery_card` / `png_e3_mcp_blocked`；
  上缘 y=205..295 打码端点标识带，`png_e3` 额外打码 URL 行；签名 + IEND + `verify()` + load + 尺寸 + 打码带单色复核全通过。

## 六、CI（公开 API 四字段，curl 直连实时数据）

| push | 提交 | 内容 | run | 结论 |
| --- | --- | --- | --- | --- |
| #1 | `b526255` `63a23f6` `e52f2c3`（3 提交） | 代码 16 `.kt` + 证据 19 文件 + 文档三件 + 台账段 | Secret `46113612947` / Build `46113613024` | 双 **success** |
| #2 | `3cbb985` | 回填 CI 四字段 + 证据 UID/GID 脱敏（含 `evidence/raw/w63/*.txt` ⇒ Build 未跳过） | Secret `46114065093` / Build `46114065044` | 双 **success** |
| #3 | `fa07411` | 证据收口（g1/g6 时点错配补记 + MANIFEST 重算 17/17） | Secret `46114170716` / Build `46114170667` | 双 **success**（05:55:22Z） |
| #4 | 本记项提交 | 纯 `docs/**`：计数口径 + 两条 W64 记项 | 仅 Secret | 交 atom 实取 |

**交付判据锚 = `fa07411`（最后一次落码 push，双 run 全绿）**；`e52f2c3` 亦双绿。四次 push 的 run 均按「同 push 内多提交合并为一组」口径（W51 起既定），`head_sha` 已逐一回源。

## 七、明确未做

- **外审 6 行销账**：阻塞项见 §2.3（五类必红未逐类复跑、`§2.5a` 元引用口径未裁）。
- `W9-05` / `W24-01` / `W24-03` 销账：出清条件见 §2.1（前台服务未做 / 浅色主题未复测 / 冻结未解）。
- `GlassLayerBudget` 设备能力探针：`core-design` 余 35，本波不动。
- `activeRun` 改 `compareAndSet`：观察位。
- 私网 MCP 端到端（`GuardedDns` 第二道闸）：⚠️ 未行使，不写成通过。
- 前台服务：未做，R5 仍 3/4。
- FreezeClock：零改动、零结论；`frozenMs` 链本波不主张任何结论。
- R1 腾行/拆包：本波 `feature-chat` 零净增、`app` 零 diff，无落点需要前置拆包 ⇒ 未做（不空转）。
- **三处新逻辑零判据**（审查席 P2-3）：`blocked_unsafe_url` 分支 / `wellFormed` / `triggerChars` 双用 —— 本轮不补判据
  （用户明令不要「判据升级→全空转」+ M3 净 0；主项已有真机 A/B 与 JVM 双变异双红）。条件触发：904 解冻时统一补。
- **`RunState.forRequest` 仍在 try 外**（审查席 P3-2）：`FreezeClock` 是装配期常量，概率极低；修法形态 =
  `state` 提为可空 `var` + `state?.let`。本轮为不改控制流而不动，登记为残余窗口。
- `COMPACT_TRIGGER_CHARS` 已无生产引用但保留（单测基线），无「回归时被误用」的守卫（P3-6）：登记。
- **KDoc 措辞一处仍不精确（审查席 P3-新）**：`AgentRunJournal.wellFormed` 的限定第 2 条写「后一条 `run_settled`
  不会因此被整条吞掉」，严格说是「该收编行不会被误判为合格行」—— 被并进同一物理行的 `run_settled` 丢失是
  不可逆的结构性后果。**本轮不改源码**（改注释会变更 APK 与证据哈希，收益不抵重建成本），登记并如实标注，
  由 W64 与下述判据缺口同批订正。
- **证据措辞一处（CI 席收口时提出）**：`g7_secretgate_w63.txt` 里「MANIFEST 第 13 行」实为**第 13 号条目 / 物理第 24 行**；
  外审按字面索引会看到 g2 那行。只登记不改（证据已冻结、重算 MANIFEST 会再触发一轮链），W64 随下批证据统一。
- **W64 四条 OPEN（CI 席判）**：① `AgentRunJournal.kt` KDoc 合流形态的后果表述不精确；② journal 三条 JVM 用例
  （合流行 / `at=17` / 截断 payload）；③ `AgentRunner.kt:99` 的 `RunState.forRequest` 仍在 try 外；
  ④ `COMPACT_TRIGGER_CHARS` 已无生产引用却无「引用数守卫」，回归时可能被误用。
- **W64 前置（CI 席建议）**：`wellFormed` 需要三条 journal 用例（合流行 / `at=17` / 截断 payload）
  才谈得上「有判据」；受 904 冻结限制本轮不新增，与 P2-3 同一钩子（904 解冻或 R1 解冻时统一补）。

## 八、OPEN 挂账

活跃主线不变：拆分三方（W20-02）｜压缩四条（W9-01，本波消掉「触发线按档位」一条，余 `TokenEstimator` 文案口径与切换守卫两把尺）｜
断词红态（W24-02）｜吞 CE 清零｜journal（W9-05 / W24-03，内核层已落地、范围 3/4、销账待出口判据）｜
MCP 明文（W5-03/05/06，G15b 维持否决）｜1M 真压（W20-05 / W9-11，R4 永久不得写成通过）｜思考块渲染（W24-01）。
外审 6 行（W34-01/02/03 + W35-01/02/03）：可自主但本波不做，阻塞项见 §2.3。
新登记观察位（不入台账，避免新开）：`compressBubbles` 头锚/摘要预留使账行预算小于 cap（已回源解释，非缺陷）。

## 九、收尾契约（三处数字相等）

- 本波计数：**6 提交 / 7 条 run**（push#1 三提交 → 2 run；push#2 / push#3 各一提交 → 各 2 run；push#4 本记项提交 → 1 条 Secret）。
- 交付判据恒锚**双 run 全绿的最后一次落码 push**（`fa07411`）；其后纯文档提交按降级规则只声明、不作 P0、不硬写 run 号。
- handoff / 验收物 / 台账 W63 段三处计数相等（台账 W63 段末条「本波计数」同值）；最终 tip 交 atom 实取。

## 十、交给外部审查团队的制品 + 边界声明

**制品**：本交接、`docs/analysis/w63-acceptance.md`、`docs/w63-dispatch.md`、`docs/06-deferred-ledger.md` 的 W63 段与三行注记、
`evidence/raw/w63/` 全部文件（逐件见 `MANIFEST_w63.txt`）。

**不含**：会话历史 / 凭据 / 未打码账号字段 / 端点标识（型号名、URL、provider 一律「端点 A」占位或打码）/ 设备序列号 /
无线网络标识与网卡地址 / 本机路径 / 用户私人图片 / 整段 logcat /「建议下一链做什么」。

**边界**：① 本波是**触发线口径修正 + 一处安全透传 + 两处内核收口**，不改长回答展示形态、不新增判据、
不动三顶格对象与 `LINE_CAPS`；② 触发线的可达性有前提（`systemChars < 12 557`），写在 KDoc 与验收物里，
不写成无条件可达；③ MCP 只证到「开关透传 + 首闸放行」，端到端连通未行使；④ journal 半写行只验尾部形态，
`at=` 数字被截断的极端形态仍可能进卡（KDoc 已写明）；⑤ 台账零销账，外审 6 行的阻塞项逐条列出；⑥ 本波止于交接，交由外部团队审查。
