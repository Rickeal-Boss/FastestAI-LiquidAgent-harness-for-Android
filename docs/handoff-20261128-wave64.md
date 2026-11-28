# W64 交接 — 外审 6 行条件销账 / journal 合流判据 / N-1 请求体定谳 / forRequest 残余窗口

## 一、本波做了什么（一段话）

按用户「继续按裁决计划全面推进、不要零改动循环」推进，本波是**首次条件销账波 + 真代码波**：① 外审 6 行（`W34-01/02/03 + W35-01/02/03`）从 `OPEN` 转 `CLOSED`——前置两条全部满足：判据 6 对 w32/w33/w34 三制品正常态 OK + 十一类必红逐类 RED(11/11)、`§2.5a` 口径裁定为「悬空指针 0（元引用 16 处不计入）」，判据 7 正常态 `declared=6` + 删指路必红双分支 RED；② `AgentRunJournal.wellFormed` 加「` at=` 恰好一次」合流形态约束，修掉 W63 外审 P1-1——前一条完整缺 `\n`、后一条接在 `at=<digits>` 之后时旧判据误判合格、后一条 settled 丢失，KDoc 后果表述同车订正为 fail-safe 写实；③ `AgentRunner.run` 把 `RunState.forRequest` 移进 `try`（W63 P3-2 残余窗口），state 可空、finally 判空补 settled，unlock 必达不变；④ `AgentRunnerTest` 新增一个测试方法承载合流四态断言，`EXPECTED_TESTS` 904→905 **受控破例**（同提交对账、增量注释写明与 W60 段 P0-2 的边界区分）。真机六格 + 双变异 + 三制品十一类必红逐类全绿；台账 **216 行 / 45 CLOSED / 160 OPEN / 11 CARRIED**（+6/-6），段数 38 → 39。

## 二、三份外审分歧裁决（逐条给值，不回改 W54–W63 历史四态）

### 2.1 N-1 争议键处方 = **部分采纳，改判据对象**
- 外审《W60-W63四波审查》§四.1 建议「把 W60 真机争议键格的键换成 `parallel_tool_calls` 实跑一次，保存链 RED 即实锤、GREEN 撤回主张」。
- **回源证伪该处方的分歧定性**：历次主张不是「该键会被保存链误拒」。W54 已落定谳用例（`EndpointCapabilitiesTest:239-254`「`parallel_tool_calls=false` 走解析+校验保存链应能通过」），现役源码该键在 `ALLOWED_EXTRA_BODY_KEYS`（`EndpointAdvancedValidation.kt:68`）、不在保留键表也不在凭据词表 ⇒ 保存链必过，外审「not in ALLOWED ⇒ BLOCKED」与现役树相反（时点差，不指控）。W60 段 P0-2 裁决原文明确「该格 ✅ 仅对**保存链**成立、不冒充**运行时请求实证**；请求体探针 `parallel_tool_calls:false` 无 JVM 用例、无设备探针」。
- 故本波真机格锚**运行时请求体**，不锚保存链（重测即空转）。实测：Lightning 端点开自定义协议写 `parallel_tool_calls=false` → 保存重开逐字保留 → 设为当前发 `ping` → 「Pong!」1.8k in/30 out、run 正常结算。**但请求体序列化明文不落设备日志（隐私纪律）** ⇒ 本格 **⚠️ 部分**：只证保存链往返 + 请求未受阻，不写成 wire 上逐字验过。

### 2.2 wellFormed 合流形态 = **收（本波最高优先代码项）**
- 采纳《W62W63深度审查》§三处方。W63 旧判据 `field("runId")` 读第一个 runId（前一条完整 32 hex）、`lastIndexOf(" at=")` 取后一条尾巴 ⇒ 合流行误判合格 `run_started`，且不以 `run_settled ` 开头 ⇒ 后一条 settled 丢失、下次多弹卡。
- W64 加「` at=` 恰好一次」约束（第二个 ` at=` ⇒ 不合格）。KDoc 四条限定写实：合流两形态、`at=17` 宽容、截断 payload、方向 fail-safe——**明确写出「被并进同一物理行的记录不可逆丢失」**，订正 W63 限定 2「不会被整条吞掉」的相反表述。
- 双证：Python 逐字等价复刻 8 样本自洽 OK、合流两形态 W63 旧判据 True/W64 False；JVM 删约束变异 ⇒ 唯一 1 failed，还原后 sha256 逐字节一致；真机注入合流行冷启动**不弹卡**（W63 缺口的真机坐实）。

### 2.3 journal 用例 904 维持 vs 受控破例 = **+1（905）**
- 两外审建议相反（《W63深度审查》§五.1 荐受控破例；《W62W63深度审查》§九 荐维持 904 改既有用例）。裁决取**判据空转风险 > 冻结字面**：四态样本要钉「合流行必红」必须是可独立失败的断言单元；塞进既有方法会稀释语义、失败归因混叠。「在既有类新增方法」在 CI strict 口径下就是 +1，任何「不改计数」的写法只能靠 KDoc 文字（那个口径不执行判据）。
- 落点：`AgentRunnerTest.wellFormedRejectsConflatedAndTruncatedJournalLines` 一个方法承载四态；`build.yml` 904→905 同提交对账 + 增量注释写明「W60 段 P0-2 否决的是请求体探针、本条是恢复卡 fail-safe 判据」的边界区分。M3 净 0 的处置 = 显式声明唯一破例、其余测试文件零删零改。

### 2.4 `RunState.forRequest` 残余窗口 = **收**
- W63 §七登记「装配期常量、概率极低」的残余窗口。W64 把构造移进 `try`、state 提为可空 `var`、finally 内 `state?.let` 补 settled。控制流纸面走查：异常 ⇒ state=null ⇒ 跳过 runSettled、清理与 unlock 仍必达；顺序仍是「清理先于 unlock」，不引入 W63 否决的反序竞态。
- 四态：本波真机三对 run 完整结算 ✅；异常注入路径 ⚠️ 推演级（无法真机注入、无行为级用例）。

### 2.5 外审 6 行销账 = **做（本波第二主项）**
- 依 W63 交接 §2.3「审查席立场：依 M1 自主处置」+ 用户本波「继续按裁决计划执行」。
- **前置①**：判据 6 三制品正常态 OK ×3 + 十一类必红逐类 RED 11/11（含 `RED missing ref`、`RED assertions missing`）入 `evidence/raw/w64/g8_k6_k7_mustred_w64.txt`。
- **前置②**：`§2.5a` 口径裁定 = **悬空指针 0 命中**，现役 16 处（`docs` 6 文件）全为描述修正本身的元引用；W34 验收物现存 §2.5/§2.2 可承接，非悬空。与 W36-02 行「§2.5a 注记引用除外」既有口径一致。此裁定属**判据执行口径的解释**，非新裁决域；若外审不认可，6 行回 `OPEN` 只动状态列两字符——可逆。
- **销账形态**：6 行 `OPEN`→`CLOSED`、证据列追加 `**W64 本波复核指路**`（k7 判据要求该字面，一次真 RED 后修正）；不回改历史原始缺陷描述、不新开编号、代数不递增。W34-03 补「无判据覆盖」字面（W35-03 出口判据要求），W35-02 补 f6「根因未定位」为 W34 时点值的说明（历史证据不回改）。

### 2.6 四处计数订正 = **收（W64 时点注记，不回改历史正文）**
W62 派单卡抬头实态 5 提交/8 run、W63 验收物补 6 提交/7 run、补 W63 tip `25340a8` 的 Secret `46114493385`、W51 `g3_selftest.txt` 行尾归一前后哈希、G20 142→143 成因——逐条见验收物步 6。不新建第 3 提交。

## 三、账本与守恒

- 台账状态列实跑：**216 行 / 45 CLOSED / 160 OPEN / 11 CARRIED**（W63 为 216/39/166/11，+6/-6），段数 38 → 39（新增 W64 段）。
- 6 行销账是**状态列变更波**，打破连续多波「零变化」口径——由外审 6 行销账的用户指令直接授权，派单卡先行声明、收尾契约如实记录。
- `EXPECTED_TESTS` 904 → **905**（唯一受控破例）；`EXPECTED_CLAIMS` 15 未变；G20 仍 143 ID 全在台账（本波未新增引用老号）。

## 四、改动面

| 文件 | 动作 | 说明 |
|---|---|---|
| `core-agent/.../journal/AgentRunJournal.kt` | 改 | `wellFormed` 加 ` at=` 恰好一次；KDoc 四条限定 |
| `core-agent/.../AgentRunner.kt` | 改 | `forRequest` 移 try、state 可空、finally 判空补 settled；199 行 ≤200 |
| `core-agent/src/test/.../AgentRunnerTest.kt` | 改 | 新增一测试方法一方法承载四态断言 |
| `.github/workflows/build.yml` | 改 | `EXPECTED_TESTS` 904→905 + 增量注释 |
| `docs/06-deferred-ledger.md` | 改 | 6 行销账 + W64 段 + W34-03/W35-02 注记 |
| `docs/w64-dispatch.md` | 新增 | W64 派单卡（六条裁决 + 判据 + 边界） |
| `docs/analysis/w64-acceptance.md` | 新增 | W64 验收物 |
| `evidence/raw/w64/` | 新增 | 18 件制品 + MANIFEST + manifest_check |

**行数预算**（实跑）：core-agent 6243/6500、core-context 1571/1650、core-data 2814/4500、feature-chat 3999/4000、feature-settings 3424/3500、core-design 12565/12600、app 1597/1600、core-kernel 449/450。三顶格零触碰；`LINE_CAPS`/`TOTAL_LINE_CAP` 未调；`AgentRunner` 199 行（G12 ≤200）。

## 五、四闸门 + 真机

- 闸 1 `assembleDebug`：RC=0；删 APK 两轮独立实跑 sha256 同为 `91d73d87…`（确定性出包）。
- 闸 2 `arch-guard`：`result=PASS ok=27 vacuous=1 violation=0 fault=0`（G19 VACUOUS）。
- 闸 3 `selftest`：**30/30 PASS**（281 s）。
- 闸 4 `fulltest`：**tests=905 failures=0 errors=0 skipped=0**（14 模块）。
- 探针 rc=0（14 锚点/15 认领/11 反哨兵）；台账符号 rc=0（328 kt/22 类/66 方法）；隐私门禁 `--ci-semantics` 三路 0 命中。

真机（<device> / Android 13 / 1080×2400，唤醒三步法、有线 adb）：

| 格 | 判据 | 实测 | 四态 |
|---|---|---|---|
| G1 | 完整未结算行冷启动弹卡 + 继续补 settled | 弹「上次运行被中断」；点继续后追加 `run_settled abcdef…` | ✅ |
| G2 | 合流行冷启动不弹卡 | 同一物理行两个 ` at=`；冷启动无恢复卡 | ✅ |
| G3 | N-1 保存链 + 请求未受阻 | 重开逐字保留；`ping`⇒`Pong!` 1.8k in/30 out | ⚠️ 部分（wire 未逐字验） |
| G4 | 主流程（forRequest 移 try 后） | 三对 started→settled(cancelled=false) 完整，无 GateRejected | ✅ |
| G5 | 取消链 | 历史样本 + JVM 用例绿；本波未新触发取消 | ⚠️ 部分 |
| G6 | 环境恢复 | 协议关、extraBody 清、切回原端点、journal 清盘 | ✅ |

截图 2 张 1080×2400 真图：上缘端点带打码单色复核、签名/IEND/verify/load/尺寸逐张全过。

## 六、CI（公开 API 四字段，curl 直连实时数据）

| run | workflow | status | conclusion | head_sha |
|---|---|---|---|---|
| `46132161797` | Secret scan | completed | **success** | `5090512` |
| `46132161838` | Build | completed | **success** | `5090512` |

交付判据恒锚 `5090512` 双 run 全绿；其后纯文档提交按降级规则只声明、不作 P0、不硬写 run 号。

## 七、明确未做

- 前台服务（R5 仍 3/4，doze 强杀场景未覆盖）；`GlassLayerBudget` 设备能力探针（core-design 余 35）；MCP 私网端到端（内网设备关闭，G5b ⚠️ 维持）；`activeRun` compareAndSet（观察位）；`COMPACT_TRIGGER_CHARS` 引用数守卫（登记）；R4/1M 真压（永久不得写成通过）。
- N-1 wire 请求体逐字验证：请求体不落日志（隐私纪律），未做、不伪造。
- 本波取消动作未新触发：历史样本 + JVM 覆盖，异常注入路径推演级。
- W24-01 浅色主题判据级复测、冻结净 0 的其余测试面：未动。
- 904→905 之外零新增 `@Test`；三顶格对象零触碰。

## 八、OPEN 挂账

活跃主线不变：拆分三方（W20-02）｜压缩四条（W9-01，W63 消「触发线按档位」一条，余 `TokenEstimator` 文案口径/切换守卫两把尺/可达性不等式三件）｜断词红态（W24-02）｜吞 CE 清零｜journal（W9-05/W24-03，范围 3/4、前台服务未做）｜MCP 明文（W5-03/05/06，G15b 维持否决）｜1M 真压（R4 永久不得写成通过）｜思考块渲染（W24-01）。

外审 6 行本波销账；两版《我的裁决》定版仍留一句（Q14 字面值未闭环，不阻塞任何动作，未代裁）。

## 九、收尾契约（三处数字相等）

**本波计数（三处相等）**：**1 提交 / 2 条 run** —— 主体 `5090512`（代码 4 文件 + 证据 18 件 + 台账 + 文档三件）一次 push 触发 Secret `46132161797` / Build `46132161838` 双 **success**（2026-11-28T09:48–09:49Z）。交付判据恒锚该提交双 run 全绿；本回填为纯 `docs/**`，Build 按 paths-ignore 不触发，按 W29 降级规则只声明、不作 P0。最终 tip 交 atom 实取，不硬写 sha。
- 交付判据恒锚双 run 全绿的最后一次落码 push；最终 tip 交 atom 实取、不硬写 sha。
- handoff / 验收物 / 台账 W64 段三处计数相等（atom 回填后一致）。

## 十、交给外部审查团队的制品 + 边界声明

**制品**：本交接、`docs/analysis/w64-acceptance.md`、`docs/w64-dispatch.md`、`docs/06-deferred-ledger.md` 的 W64 段与 6 行销账注记、`evidence/raw/w64/` 全部文件（逐件见 `MANIFEST_w64.txt`）。

**不含**：会话历史 / 凭据 / 未打码账号字段 / 端点标识（型号名/URL/provider 一律占位符）/ 设备序列号 / 无线网络标识与网卡地址 / 本机路径 / 用户私人图片 / 整段 logcat /「建议下一链做什么」。

**边界**：① 6 行销账的判据 6/7 全部实跑会响、非自证；② `§2.5a` 口径 = 悬空指针 0（元引用 16 处不计入），可逆；③ N-1 只证保存链 + 请求未受阻，wire 未逐字验；④ 合流形态真机坐实「不弹卡」，后一条 settled 丢失的结构性后果写实于 KDoc；⑤ R4/1M 不写通过、MCP 端到端未行使、前台服务未做；⑥ 904→905 受控破例显式声明；⑦ 本波止于交接，交由外部团队审查。
