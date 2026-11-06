# W9 第一车 · W9-00 唯一派单与验收口径（方案席，2026-11-06）

> **本文件是 W9 第一车唯一派单口径**（裁决序 0→1）。基准：远端 main = 本地 HEAD = `e7d7a1e`，
> 工作树干净（本席 `git rev-parse` / `git status --short` 复核）。本文件所有 `文件:行` **均已回真实工作树
> grep/sed 复核**，不采信外审行号（外审查的是它那一版；漂移处已标注）。
> 上位裁决：`桌面/w9-终版落锤裁决.md`（最高优先）＋ `桌面/裁决/W9开工裁决与顺序.md`。
> 台账唯一来源 `docs/06-deferred-ledger.md`；矩阵预登记 `docs/notes/w9-feature-matrix-preregistration.md`。
> **本席只写两个新 notes 文件，不改任何源码/台账/handoff/scripts/.github/build.yml。**

**路径缩写**：`模块/文件:行` 省略 `src/main/java/com/rickeal/liquidharness/`。
**编号唯一口径**：派单里的 `P0-1..P0-4 / P1-1..P1-6` 一律指 **桌面《W8源码逐行审查与达标核对.md》§三**
（主理人认定最完整）；⚠️ 另两份外审的 `P0-1` 指 **不同东西**（W5-W8 逐行审查 P0-1 = JsonFormatCheck
回显原文；w8-code-review P1-1 = 快修四项虚报、P1-2 = validateAdvanced 空转）——**不得串号**，见 §二.0。

---

## 一、范围裁决

**本车 = 序 0 抬头（纯文档，随车不单独占波）＋ 序 1 `W9-00 交接对账与防线接线`（插入半波，半天级）
＋ 真机功能矩阵验收（按预登记行使四态取证）＋ 推 CI**。用户诉求（免费 API 多模型/多上下文直至 1M、
多模态/音频/工作空间/记忆/Skill/MCP/KV cache/手动·自动压缩）本车**只做「按跑前预登记行使 + 四态取证」**，
不为凑绿改代码，阻塞项如实标 ⛔（见矩阵预登记）。**先本地 UI + 功能测试 + adb 真机，通过才推仓库及 CI。**

| 做什么 | 依据 |
|---|---|
| 4 条出口判据全绿、CHECKLIST 12/12、`essage` 二分支收口、kotlinx 四件逐条列交付物 | 终版裁决 §三/§一.C |
| Q1/Q2/Q3/Q7/Q7′/R4/Q15 补真（W8 自证 RED 闭环），F-1/A-3/F-3 改号留痕 | 总裁裁决 §二裁决③、终版 §一.A/B |
| W9-07 行三项改号、Q2→W9-00、W9-11 删 Q2 条目、W3-07 同提交回填 | 终版 §一.A/B、§四 |
| D6/D7 四态补证（真机）、`endpoints.json` 明文/保留键扫描判据 | 终版 §三.2（F-3/A-3） |

**明确不做什么（缺一即越界）**：
- 不碰 `ConversationOps.kt` / `SettledCompressPolicy.kt` / `SwitchGuard.kt` / `ChatViewModel.kt` 的**压缩结构**
  （R2 三门槛：≤30 行 / 不碰压缩结构 / 有独立判据）。⚠️ Q1 只改 `ConversationOps.kt:106/:180` 两行的
  `runCatching`→`contextWindowCap(container)`（取消语义），**不是**压缩结构，允许改，但只许这两行。
- 不新增 `arch-guard` 守卫（新守卫扫空集 ⇒ VACUOUS 1→2 ⇒ G21 判红；且守卫专项锁死 W9-10）。
- 不动 `TOTAL_LINE_CAP=42500` / `VACUOUS_BASELINE=1` / `LINE_CAPS` / `EXCEPTIONS`；不引用 W8 的 +100 例外。
- 不做 W9-01/02/03/04/06/07/08/09/11 的任何实现；不做 MCP 真机联调（平台阻塞）；不解锁设备侧 1M。

**留到下一车的显式留痕（不是静默顺延，四要素齐：编号 + 裁决理由 + 承接目标 + 写入位置）**：

| 项 | 承接与依据 | 留痕文本（写进 handoff §六，CI 席落笔，缺一 N2 判红） |
|---|---|---|
| **W9-01** 压缩量纲统一 | 强制执行序 2「正确性主线第一件事」，四外审同一缺陷**禁拆多条**；台账 §二-bis 仍 `OPEN` | 「W9-01 仍为正确性主线第一件事；插 W9-00 不改变其地位，也不许拆散」 |
| **W9-05** journal/FreezeClock | 强制执行序 3「必须开波」；老化五波 | 「**插 W9-00 不构成 W9-05 第 5 次顺延**」（台账 §零/§二-bis 原话，必须逐字写） |
| **W9-10** 守卫与口径专项 | 裁决①锁死；含 C9/G24、G20 第四判据 | 「C9/G24 归属唯一 = W9-10；N1 对 C9 判出的 RED 只作输入证据留痕，W9-00 不代做、不转移编号」；落地前 **N2 人工闸门不得免除** |
| W9-07 / W9-11 | 终版 §一.A/B | W9-07 行三项改号文本；W9-11 承接行删 Q2 条目改写「Q2 → 已提前至 W9-00（总裁 2026-11-06 落锤）」 |

**老号 `CARRIED` 不删行**（删行 G20 判红）；禁用 `W8-05`；`docs/notes/` 内 `W*-*` 是历史输入，不参与派单。

---

## 二、W9-00 条目分诊表

### 2.0 编号归并（防两席做同一件事 / 防重复计数）

- **F-1 ≡ P0-1**（`validateAdvanced` 生产接线）；**A-3 = P0-1 的扫描判据子项**（`endpoints.json` 明文凭据/保留键，二者同车一次做完）。
- **F-3 ≡ P0-3**（图片闸门响应式）＋ D6/D7 四态补证（真机）。
- **P0-2 + P1-1 + 外审 P2-2（OFF 降级无 reason）= 同一条链「思考降级 `reason` 全链路落点」**（终版 §三.2 第 3 项）。
- **R4 ≡ Q7′**（`Generation.kt:79` 一行 KDoc，w8-plan §八.4 明写「并入 Q7′」）⇒ 只做一次。
- **Q15 代码已半落地**（预算 `:60` 256 ✅、谓词 ✅），本车只补**用例**；`P1-5` 是 Q15 用例的序列级加深，合并。
- **kotlinx 四件**：以终版 §一.C 映射表 `文件:行` 为准，旧编号降附注；`JsonFormatCheck.kt:45` 与 W5-W8 审查 P0-1 同件。
  反漏项：四件之外若回树发现第 3/第 5 件同域缺陷 ⇒ 走新增编号，**不得塞进「四件」名下**（终版 §一.C）。

### 2.1 主分诊表（⚖️ = 二选一，凡「二选一」须在交付物里写明选了哪一支及理由）

| 编号 | 文件:行（现役 `e7d7a1e` 复核） | 动作 | 可跑真假用例（真→绿 / 假→红） | 验收方式 | 席 |
|---|---|---|---|---|---|
| 出口① CHECKLIST | handoff §六 + 台账 | 12/12；N1/N2 的 W8 自证 RED 逐条闭环（补做或改号留痕，**禁止写「已收」**） | 变异：删一条「已修」的 diff 证据 ⇒ N1 必红；把 W3-07 改回 OPEN ⇒ N2 必红 | 真判据（非空集自证） | CI＋主理人 |
| 出口② 4×P0 | 见下 F-1/A-3/F-3/P0-2/P0-4 各行 | 4 个 P0 全接线 | 每条各带「被调用」判据（§五.4） | JVM＋真机 | 开发＋主理人 |
| 出口③ 6×P1+Q2 | 见下各行 | 逐条带可跑真假用例；kotlinx 四件逐条列交付物；`EXPECTED_TESTS` 同提交改；VACUOUS 只许减 | 每条真/假两样本先自验 | JVM＋CI 对账 | 开发＋CI |
| 出口④ 台账 | `06-deferred-ledger.md:74` | W3-07 同提交回填 `CLOSED` + 证据列改指现役 `RemoteLlmEngine.kt:94`；D6 判据文本订正；§二统计表同步 | 真：G20 OK 且该行 CLOSED 证据非空；假：证据列仍写 `:92 硬编码 false` ⇒ 必红 | `bash scripts/arch-guard.sh`（G20） | CI |
| 基线规则 | `build.yml:132` `'741'` | 全部 Δ（Q2 +2 夹具、kotlinx 四件、6 P1、P0、MD 用例）**一次算清一次改** | 真：严格 `@Test` 计数 == `EXPECTED_TESTS`；假：先改基线后加测试 ⇒ 中间态必红 | `grep -rhE '^\s*@Test\b' --include=*.kt . \| wc -l` | **主理人独占** |
| **Q2** | `core-data/SessionImportParser.kt:139`（⚠️ 在 core-data **不是** feature-chat） | UTF-8 BOM 剥离 + 2 夹具，EXPECTED_TESTS 696→698 口径并入本车 Δ | 真：带 BOM 的 JSONL 首行解析成功；假：不剥离 ⇒ 整份 MALFORMED（先跑旧实现必红再修） | JVM（core-data） | 开发 |
| **F-1 / P0-1** | `core-model/EndpointAdvancedValidation.kt:46`（定义，生产零调用）；`feature-settings/SettingsViewModel.kt:130` `container.saveEndpoint`；`core-engine/OpenAiCompatibleAdapter.kt:106-110` | ①保存链落盘前 `validateAdvanced(draft)` 非空即 BLOCKED 上屏；②`applyCustomProtocol` 运行时复用同表兜底（含**值**的凭据形态扫描）；③补允许键白名单 | 真：合法 extraBody 保存成功；假：`extraBody foo=sk-xxxx` ⇒ 保存被拒、不写盘、不进请求体 | 2 JVM（保存拦截/落盘无明文）＋1 buildBody 断言＋**「被调用」静态判据** | 开发 |
| **A-3** | 同上（F-1 同车） | `endpoints.json` 明文凭据/保留键扫描判据 | 真：扫描器对已知含 `sk-`/保留键的样本**必红**（判据自身先验证）；假：干净配置 ⇒ 0 命中 | JVM＋本地 UI（保存后读回），**不做新 arch-guard 守卫** | 开发 |
| **F-3 / P0-3** | `app/AppNavHost.kt:166-168` `LaunchedEffect(state.endpointLabel)`；`:182/:184` | collect `activeEndpoint()` Flow 或 key≥`endpointId+supportsVision`，不复用显示名 | 真：**同名端点**只勾图片 ⇒ 返回对话页 attach 可点、禁用原因消失；假：保持 label 作 key ⇒ 必红 | Compose/真机 D6/D7；判据文本订正写进本文件 §四 + handoff §六（**不改历史 w8-solution.md:589**） | 开发＋主理人 |
| **P0-2 / P1-1** | `core-engine/ReasoningBody.kt:25/:35/:39`（`hard_off_unsupported` 仅注释）；`OpenAiCompatibleAdapter.kt:221-222` 只取 `effort.wire`；`feature-settings/EndpointAdvancedConfig.kt:65-71` | 降级 `reason` 透出到 UI（选中档位副标题 / 模式行文案）或结构化日志；订正 3 处打架 KDoc | 真：不可达关思考 ⇒ 用户可见「已降级为仅隐藏，仍计费」；假：静默降级 ⇒ 必红 | 1 JVM＋1 真机两态；日志只打键名/reason 不拼值（G19 规则） | 开发 |
| **⚖️ P0-4** | `core-model/EndpointCapabilities.kt:42/:66`；`ReasoningBody.kt:41-46`（与 ON 合并）；`ProviderAdapter.kt:189-192` | **二选一**：(a) 作为终帧裁决/渲染参数真正下传 + V2 单测 + 真机两态；或 (b) 从 UI 移除该档**连 UI 带用例一起删**并登记 | 真(a)：空正文有思考不判断流；真(b)：UI 无该档且无残留用例；假：留着假开关 ⇒ 必红 | JVM＋真机（选 a 时）／静态 grep（选 b 时） | 开发 |
| **P1-2** | `feature-settings/EndpointAdvancedConfig.kt:197` `rememberSaveable(current)`；`EndpointAdvancedValidation.kt:78-86` `parseExtraBody` | 文本状态只按 `endpointId` 初始化一次（不以派生 Map 作 key）；hint 与按 `,` 切分口径统一 | 真：输入 `a=1, b=2` ⇒ 两键都在且光标不跳；假：Map 作 key ⇒ 吞 `, ` 后缀必红 | JVM/Compose 状态判据 | 开发 |
| **P1-3** | `core-engine/OpenAiCompatibleAdapter.kt:115-121` `coerceScalar` | 默认字符串，仅显式前缀/白名单键转换；`NaN/Inf/Infinity` 一律拒绝 | 真：普通值原样；假：`extraBody foo=NaN` ⇒ `buildBody` 抛异常必红（反例用例） | JVM | 开发 |
| **P1-4** | `core-model/EndpointCapabilities.kt:176-186`（EXTREME）、`:189-200`、`:203-208` | 降级目标必须 ∈ `effectiveSupported`，否则继续向低找或 `wire=null`+reason | 真：`supported={LOW},desired=extreme,maxOut=8000` ⇒ 不发 `high`；假：照现役发 `high` ⇒ 性质测试必红 | JVM 性质测试 | 开发 |
| **P1-5 / Q15** | `core-data/EndpointProbe.kt:68` `firstOrNull`（Finished 处终止上游）；`:125` `judgeProbe`；`OpenAiCompatibleAdapter.kt:160` Finished 先于 `:171` Usage | 改用 `takeWhile`/显式循环收到终帧，或 usage 帧先于 Finished emit；补 2 条「开/关思考」序列用例 | 真：`TextDelta` ⇒ 通过；假：`ReasoningDelta+Finished` 无 TextDelta/Usage ⇒ 失败（现役会假红） | 2 JVM 事件序列用例（core-data，本波 W8 零改动） | 开发 |
| **P1-6** | `06-deferred-ledger.md:74`；`docs/analysis/w8-solution.md:589-590`（D6 文本） | 三向对账：台账 W3-07 回填 + D6 文本订正（保留禁用态入口，**不是**「attach 不渲染」）+ §二统计表同步 | 真：G20 OK + handoff 宣布关闭 ⇄ 台账 CLOSED 一致；假：仅改 handoff 不改台账 ⇒ 必红 | arch-guard G20＋N2 人工闸门 | CI |
| **Q1** | `feature-chat/ConversationOps.kt:106/:180` | 两行 `runCatching { settings.maxContextTokens.first() }` → `contextWindowCap(container)`（只这两行） | 真：取消时 `CancellationException` 原样重抛；假：吞掉 ⇒ Job completed 必红 | JVM；`grep -c runCatching` 该文件只应剩 1（JSONL） | 开发 |
| **Q3** | `core-data/SessionImportParser.kt:269`（条数上限）先于 `:276` `body.isBlank()` | 把 `:276` 上移到 `:269` 之前 + 1 用例 | 真：构造 5000 条 + 第 5001 条**空**正文 ⇒ 不返回 `TOO_MANY_MESSAGES`；假：现役顺序 ⇒ 必红 | JVM | 开发 |
| **Q7** | `feature-chat/ChatAttachmentPicker.kt:16` | 订正「当前 main 源集没有调用点 / ChatMessage 还没有 attachments」失实 KDoc | 真：`grep -rn attachments --include=*.kt` 有生产消费点；假：注释与实现打架 ⇒ 静态判据必红 | 静态 grep（0 新增 @Test） | 开发 |
| **Q7′ / R4** | `core-model/Generation.kt:79`（KDoc）/:80 `SamplingParams.reasoningEffort` | 1 行 KDoc 改「未接线，勿用；唯一真值源 = `RemoteEndpoint.reasoningEffort`」；字段本身挂 W9-06 不动 | 真：KDoc 不再声称「仅当 REASONING_EFFORT 时生效」；假：残留 ⇒ 静态判据必红 | 静态 grep（0 新增 @Test） | 开发 |
| **kotlinx①** | `core-model/JsonFormatCheck.kt:45` `take(120)` | 截 `JSON input:` 之前描述段或全自构造，不回显原文 | 真：样本含 `SECRET-MARKER` ⇒ `!invalid.message.contains("SECRET-MARKER")`；假：现役必红 | JVM 红态断言 | 开发 |
| **kotlinx②** | `JsonFormatCheck.kt:40-43`（`:41` `JsonNull->Valid; else->Valid`） | 删恒真空 when 或写「非单值形态 ⇒ Invalid」真判据 | 真/假两态齐全（现役无论实现多错都绿） | JVM | 开发 |
| **kotlinx③** | `JsonFormatCheck.kt:72` `lastIndexOf("```")` | 围栏裁切改「最后一个围栏」语义的确定性实现 | 真：字符串值内含 ``` 不被误裁；假：现役截断 ⇒ 必红 | JVM | 开发 |
| **kotlinx④** | `core-model/src/test/JsonFormatCheckTest.kt:96-98` | 三连弱断言（`line/column>=1`、`isNotEmpty`）换成精确行列号＋不含原文片段 | 真：正确 offset 通过；假：故意算错行列号 ⇒ 必红 | JVM | 开发 |
| **`essage` 收口** | 全仓 `*.kt` | 真实工作树 `grep -rnw "essage" --include=*.kt .` = **0**（本席复核，与主理人一致）⇒ 走终版 §一.C **分支 2**：记「W8 误报，快照核验零命中」销号留痕 | 变异：造一个 `essage` 字面 ⇒ 分支 1 顺手修正 | 静态 grep + handoff §六 留痕 | CI |
| **MD-1 ⚖️** | `feature-chat/MarkdownText.kt:51` `# `/`## ` | (a) 扩 `^#{1,6}\s+` + `###`/`####` 各 1 用例；或 (b) KDoc 写「仅一二级」+ `###` 原样断言 | 真：`### 标题` 渲染去 `#`；假：原样显 `###` ⇒ 必红 | JVM | 开发 |
| **MD-2 ⚖️** | `MarkdownText.kt:83` `append(anchor)` / `:90` INLINE_PATTERN | (a) 锚文本后追加灰色 URL 尾注；或 (b) KDoc 把「不吞字符」限定为「非支持子集」 | 真(a)：URL 上屏可见；真(b)：KDoc 与实现一致；假：吞 URL 无说明 ⇒ 必红 | JVM＋真机截图（选 a） | 开发 |
| **P2 词表补 token** | `core-model/EndpointAdvancedValidation.kt:30`（词表缺 `token`，G17 `arch-guard.sh:1138` 有） | 词表加 `"token"`；配 1 JVM 用例 + **selftest 变异 case** | 真：`access_token`/`session_token` 命中；假：词表漏 `token` ⇒ 变异必红 | JVM＋`arch-guard-selftest.sh`（⚠️ 跨席，见 §三） | 开发＋CI |
| **P2 SET_WINDOW 补 load** | `feature-settings/SettingsViewModel.kt:179-183`（分支尾无 `load()`；`:226` 定义） | SET_WINDOW 分支尾补 `load()`（或单独重算 `blockedEndpointIds`） | 真：1M→32K 降档后列表置灰集合重算；假：沿用旧窗口口径 ⇒ 必红 | JVM/状态判据 | 开发 |
| **P2 kind 收窄 ⚖️** | `feature-settings/EndpointAdvancedConfig.kt`；`EndpointEditScreen.kt:94`（kind 透传） | (a) 非 OPENAI_COMPATIBLE 显示禁用态入口+原因；或 (b) 按 kind 过滤 toggle 列表 | 真：ANTHROPIC/GEMINI 端点上 jsonMode/customProtocol 不静默；假：摆着可勾却无效 ⇒ 必红 | Compose/静态 | 开发 |
| **P2 AUDIO 假入口 ⚖️** | `EndpointAdvancedConfig.kt:78` `Modality.entries`（含 AUDIO）/`:119-121`；`core-model/RemoteEndpoint.kt:206-207` `supportsAudioOutput` 零消费 | (a) 输出模态过滤为 TEXT/IMAGE（w8-plan §三「UI 不摆假入口」）；或 (b) **显式改号留痕**归 W9-06 二选一 | 真(a)：UI 无 AUDIO 开关；假：摆假开关 ⇒ 必红；选 b 须在交付物写明改号文本 | Compose/静态＋留痕 | 开发（选 b 时 CI） |

### 2.2 二选一裁决清单（本车开工前主理人一次性拍板，写进交付物）

⚖️ `P0-4 REASONING_ONLY`（接线 / 连 UI 带用例删）｜⚖️ `MD-1`（扩正则 / KDoc 改写）｜
⚖️ `MD-2`（灰字尾注 / KDoc 改写）｜⚖️ `P2 kind`（禁用态入口 / 过滤列表）｜
⚖️ `P2 AUDIO`（过滤 / 改号归 W9-06）｜⚖️ `P0-2/P1-1 reason`（上屏 / 结构化日志）。
**默认建议**（不代替裁决）：P0-4 选 (b) 删（半天级插入波，不留半套）；MD-1 选 (a)；MD-2 选 (a)；
kind 选 (a)；AUDIO 选 (a)（否则与 W9-06 双挂，B10 判红）；reason 选上屏（core-engine 仍 G19 VACUOUS，
日志出口留给 W9-07）。**P2 四件 + MD 两件若不本车收，必须显式改号留痕，禁止写「已收」或静默不提。**

---

## 三、文件域切分（4 路写者独占，禁止两席改同一文件）

| # | 写者 | **独占**文件域 | 禁止 |
|---|---|---|---|
| 1 | **开发席**（android-developer） | `core-model/`、`core-engine/`、`core-data/`、`feature-settings/`、`feature-chat/`、`app/` 的 `src/main`+`src/test` | 不碰 `ConversationOps.kt` 压缩结构、不动任何 docs/scripts/.github |
| 2 | **CI 席**（ci-workflow-specialist） | `docs/06-deferred-ledger.md`、`docs/handoff-*`、`scripts/`、`.github/`（含 build.yml **除 `:132` 一行外**） | 不改 `build.yml:132`；不改源码；不新增 arch-guard 守卫 |
| 3 | **方案席**（本席） | **仅** `docs/notes/w9-plan.md`、`docs/notes/w9-feature-matrix-preregistration.md` | 不改任何其它文件（含历史 `docs/analysis/w8-solution.md`） |
| 4 | **主理人**（team-lead，串行最后落笔） | `build.yml:132 EXPECTED_TESTS` **单行**、集成提交、adb 真机 D6/D7、push/CI 核对 | 不提前改基线（见下） |
| — | 审查席（code-quality-reviewer） | **只读**，不改任何文件 | 回报可能虚构，一律以 `git status` + 严格 @Test 口径核实 |

**共享禁区（无人可写，需主理人裁决）**：`AppContainer.kt`、`ChatViewModel.kt`（余 13 行 + G23 钉死）、
`ConversationOps.kt`（除 Q1 两行）、`SettledCompressPolicy.kt`、`SwitchGuard.kt`、`LINE_CAPS`/`TOTAL_LINE_CAP`/
`VACUOUS_BASELINE`/`EXCEPTIONS`。

**跨席协作点（必须消息对齐，双方都不改对方文件）**：
1. **P2 词表补 token**：开发席改 `core-model/EndpointAdvancedValidation.kt:30`；CI 席在
   `scripts/arch-guard-selftest.sh` 加同谓词变异 case。两席约定同一触发串后各落各的，主理人集成时核对两处都在。
2. **`EXPECTED_TESTS` 基线只动一次**：各席只**报**新增/删除严格 `@Test` 数，主理人在最终集成提交里
   一次算清一次改 `build.yml:132`（`741 → 741+Δ`）。分两次动基线 = 多一次虚报面（终版 §一.A / §三.5）。
3. **D6 判据文本订正**：正确文本写死在本文件 §四 + handoff §六（CI 席），**不改历史 `w8-solution.md:589`**
   （历史记录不篡改，与台账 §三.4 同精神）。
4. **序 0 抬头是纯文档**：`build.yml` 的 `paths-ignore` 让纯文档提交 **0 run** ⇒ 必须与代码同车（B6）。

---

## 四、出口判据 + 四闸门（不绿不许进 W9-01）

**终版裁决 §三 4 条（原文口径，Q2 并入后）**：
1. `CHECKLIST=12/12`，N1/N2 的 W8 自证 RED 逐条闭环（补做或显式改号留痕，**禁止写「已收」**）。
2. 4 个 P0 全接线：`validateAdvanced()` 生产调用点 + `endpoints.json` 明文凭据/保留键扫描判据（F-1/A-3）；
   图片闸门响应式 + D6/D7 四态补证（F-3）；思考降级 `reason` 全链路落点；`REASONING_ONLY` 接线或连 UI 带用例删。
3. 6 个 P1 + **Q2** 逐条带可跑真假用例；`SessionImportParser.kt:139` BOM 剥离 + 2 夹具；kotlinx 四件
   （映射表为准）逐条列交付物清单，禁止「JSON 检查细节已修」模糊话；`EXPECTED_TESTS` 同提交改；
   `arch-guard` 的 `VACUOUS` 只许减。
4. 台账 `W3-07` 同提交回填 + D6 判据文本订正；§二 统计表同步。
5. **基线规则**：`EXPECTED_TESTS` 全部增量（Q2 +2 夹具、kotlinx 四件用例、6 P1/P0/MD 用例）
   一次算清一次改。

**CHECKLIST 12/12 的构成（如实标注，判据自身先验证）**：⚠️ 本席在两份裁决与仓内 `docs/` 中
**未找到逐条列出 12 项的显式清单**。最合理【推断】= 既有 **10 条 handoff 生成 checklist + 序 0 新增
N1/N2 两条**（总裁裁决 §二裁决③「两条 checklist 增补」），即 `10 + 2 = 12`。**这是推断不是判据**：
CI 席在序 0 落笔时须把 **12 条逐条编号写死**（含 N1 = `git diff` 自证「已收/已修指到 文件:行+提交号」、
N2 = 核到台账指定行 `CLOSED`+证据列非空+证据指向文件仍存在），并把构成回写本文件；
在 12 条未逐条列全前，`CHECKLIST=12/12` **不得判绿**（「预期值」不是判据，知识库 §6）。

**D6 判据文本订正（写死）**：`supportsVision=false` ⇒ **保留禁用态入口 + 无障碍原因**（不是「attach 按钮不渲染」）；
`true` ⇒ 可点。D7 = 200×120 左红右蓝真图 + `reasoning.enabled=false` + `max_tokens≥512` ⇒ 命中双色
（**禁用 1×1 样本**，W7 §二）。两态均需真机截图 + 设备侧日志落盘。

**四闸门（命令 / 阈值）**：

| 闸门 | 命令 | 阈值（不达 = 红） |
|---|---|---|
| G1 出包 | `./scripts/localbuild.sh`（默认 `:app:assembleDebug`） | `BUILD SUCCESSFUL`，产出 `app-debug.apk`；裸 `gradlew` 会失败（JAVA_HOME 缺失） |
| G2 全量单测 | `./scripts/fulltest.sh` | `tests = EXPECTED_TESTS`、`failures=0 errors=0 skipped=0`；**14 模块**、XML mtime 全为本轮（防残留 XML 假全量）；严格 `@Test` 计数用 `grep -rhE '^\s*@Test\b' --include=*.kt . \| wc -l` |
| G3 架构守卫 | `bash scripts/arch-guard.sh`（**仓库根目录跑**） | `OK=27 VACUOUS≤1（只许减，G19 唯一 VACUOUS）VIOLATION=0 SELF_FAULT=0`；G20 OK |
| G4 守卫自证 | `bash scripts/arch-guard-selftest.sh` | `30/30 PASS`（= 28 CASES + 2 独立 VAC 段）；**守卫报红 ≠ 真命中**，须看到违规行本身 |
| G5 真机 | 本地 UI + 功能测试 + adb 调试 | D6/D7（及矩阵预登记各项）四态取证；**用户要求：真机测过没问题才推** |
| G6 推送 | `git push --dry-run` + `gh run list --branch main` | dry-run 成功；双工作流触发核对；**「没有 run」≠「绿的」**（paths-ignore 0 run 同形） |

**顺序**：G1→G2→G3→G4 不绿不进 G5；G5 不绿不推；最后才 G6 + CI 五项核对。

---

## 五、风险、设备硬件可行性与历史坑点

### 5.1 设备硬件可行性结论（方案席维度，端侧行为最终发生在具体设备上）

| 维度 | 结论 | 状态 |
|---|---|---|
| 目标设备/OEM | **OPPO PDRM00 / ColorOS / Android 13（SDK 33）**，`com.rickeal.liquidharness.debug` | 已验证（AGENTS.md §三、handoff-wave8 §三） |
| adb 通道 | `D:/WBfil/2026-09-18-18-27-37/_j2env/sdk/platform-tools/adb.exe`，**必须 `-P 15037`**；本席实跑 `devices` ⇒ `13309cc8 device` 在线 | ✅ 已验证 |
| 安装前唤醒 | 灭屏 `pm install-commit` **永久挂起**（ColorOS 冻安装服务）；须 `KEYCODE_WAKEUP` + `exec-out screencap -p` 截图**非全黑**（AOD 仍可能黑）。`dumpsys window policy` 曾报 `screenState=SCREEN_STATE_TURNING_ON` ⇒ 必须确认非 AOD | 已验证规则 / **本车待确认** |
| 题面注入 | `input text` 非 ASCII 设备侧 NPE ⇒ **中文题面一律英文 ASCII**（空格用 `%s`）；本波所有题面英文 | 已验证（知识库 §2） |
| adb daemon | 每条命令后 daemon 退出 ⇒ **`adb reverse` 与后续 input/screencap 必须在同一条 Bash 调用里**；设备侧 `-f` 落盘取证（PC logcat 缓冲仅 ~256KiB） | 已验证（知识库 §2） |
| 点按判据 | Compose bounds 在 `uiautomator dump` **不可靠** ⇒ 点按/切换以 journal、应用日志、截图为准；键盘弹出后发送键上移，每次按截图重定位；应用商店弹窗抢前台 ⇒ `am force-stop com.heytap.market`；周期性 `input keyevent 224` 保屏 | 已验证（知识库 §2） |
| 能力位 | 图片闸门 `supportsVision` 现役三处同改（`RemoteLlmEngine.kt:94`）；**`supportsReasoning` 三组预置全 false + 无 style 选择器 + UI `enabled=initial.supportsReasoning` 只读 ⇒ 思考四态/五档生产不可达**（NEW-W8-1）⇒ 真机 D 判据里凡涉思考档位的项 **⛔ 不适用，不得写成已验证** | 已验证（本席 grep `EndpointStore.kt:118/130/142`、`EndpointAdvancedConfig.kt:61`） |
| 平台阻塞（先于代码拒绝你） | ① MCP 明文 http 被 `AndroidManifest.xml:25 usesCleartextTraffic="false"` 拒（W5-03，裁决**不为测试放开**）；② 设备侧 1M 双阻断 = `PreStepBudget.enforce`（`TurnLoop.kt:49` 之前，`hard≈27904`）+ `RoundHeadGate`（`:42` 本 run 累计 `promptTokens` > `AgentPolicy.maxRunPromptTokens=400_000`，首轮 0 ⇒ **第 2 轮才熔断**）；③ PC 侧 1M 是纯 Python 直连、绕过 app 内核 ⇒ **不得写成设备侧可用** | 已验证（本席 grep） |

### 5.2 判据纪律：写进方案的判据必须给「可执行触发条件 + 空命中语义」

「判据写进方案」≠「判据被执行」（知识库 §6）；WARN 判据**有闩**（每 run 只报一条），**0 命中 ≠ 通过**。

| 判据 | 可执行触发条件 | 空命中语义 |
|---|---|---|
| A-3 明文扫描 | 保存一个 `extraBody` 含 `sk-`/保留键的端点 → 读回 `endpoints.json` → grep 明文 | 0 命中**先跑已知含凭据变异样本**证明扫描器会红；无变异样本 ⇒ ⛔vacuous，不算通过 |
| F-1 `validateAdvanced` 被调用 | 静态 `grep -rn "validateAdvanced(" ` 必须命中 `SettingsViewModel` 保存链（非仅定义/测试/KDoc） | 只命中定义+测试 ⇒ 组件空转（W8 实地），⛔ 不适用 |
| `reason` 全链路落点 | 构造不可达关思考 / EXTREME 降级 → 断言 UI 文案或结构化日志出现 reason | 只有 `ResolvedEffort` 对象里带 reason ⇒ 零消费，⛔vacuous |
| D6/D7 | 同名端点勾图片 → 返回对话页 → attach 可点；真图两色往返 | 只改 `capabilities()` 不点 UI ⇒ ⛔vacuous（w8-solution.md:589 自述） |
| KV cache 分块命中 | 前缀逐字节稳定 ⇒ 第 N 次 `cached_tokens>0`；改首字节 ⇒ 归零（**双向**） | OR 免费池 `cached_tokens` 恒 0 ⇒ 反向分支不可执行 ⇒ ⛔vacuous；NV 79.4% 是唯一正向证据 |
| 自动压缩 | 长会话超阈值 ⇒ `auto_compact applied=true` | 至今零证据（W7 全 noop）⇒ ⛔vacuous；且 WARN 有闩，0 命中不算通过 |

### 5.3 风险与「需规避」清单（引用本仓铁律）

1. 🔴 **证伪优先**（AGENTS.md §二.2）：本表所有行号已回 `e7d7a1e` 复核；已发现的**外审行号漂移**：
   `SettingsRoute.kt` `onImport = null` 外审记 `:206`、现役 **`:231`**；`RemoteLlmEngine.kt` 台账证据列
   仍写 `:92 硬编码 false`、现役 **`:94` = `endpoint.supportsVision`**；`EndpointAdvancedConfig.kt:196-209`
   → 现役 `:197`；`AppNavHost.kt:166-169` → 现役 `:166-168`。**照抄外审行号 = 伪工作量。**
2. 🔴 **判据自身先验证**：每组新判据先跑「已知必通过 + 已知必失败」样本（W3/W5 铁律），
   尤其 kotlinx① 的 `SECRET-MARKER` 红态、A-3 的变异样本。
3. 🔴 **VACUOUS 不是通过**（AGENTS.md §二.3 / 台账 §三.5）：G19 守空集；「测试全绿」≠「口径被验证」；
   不得为变绿调大 `VACUOUS_BASELINE`（`arch-guard.sh:1434`）。
4. 🔴 **新增组件必须配「被调用」判据**：`validateAdvanced` 接线、`reason` 落点、`supportsVision` 响应式
   三处都要有「谁行使」的静态/动态判据，否则复现 W8「写了防线没人过」。
5. 🔴 **守卫边界是包目录**：G17 只扫 `core-model/src/main`（`arch-guard.sh:1111`），新 DTO 一律放
   `core-model`，命名避开 `apiKey/secret/password/token` 精确名（`:1138`）；A-3 **不得**做成新守卫。
6. 🔴 **平台策略先于代码拒绝你**：MCP cleartext、设备侧 1M 双阻断——**不得为测试放开**（见 §5.1）。
7. 🔴 **行数触顶先拆不许调上限**：`feature-settings` 现 **3482/3500 余 18**（本席 `wc -l` 复核），
   本车 P0/P1/P2 大量落在 feature-settings ⇒ **开波第一件事先拆 `McpServerEditScreen.kt`(377) /
   `McpServerListScreen.kt`(353)**；W8 `+100` 例外（`w8-line-budget-ruling.md`）**不构成先例**；
   `TOTAL_LINE_CAP=42500` 不动。
8. 🔴 **子 Agent ≤4 路并行**（7 路实测集中 429，知识库 §6）；**成员回报可能虚构产出**（实摔 2 次）⇒
   派单后主理人先 `git status` + 严格 @Test 口径 + 文件 mtime 核实，再采信完成通报。
9. 🔴 **提交/凭据**：**email 口径冲突已实证裁定**：AGENTS.md §九
   = `263587600+Rickeal-Boss@users.noreply.github.com`，知识库 §5「绝不用 noreply」= `15992567646@139.com`；
   本席 `git log -1 --format=%ae e7d7a1e` = **noreply 版** ⇒ 本车以 AGENTS.md §九为准（知识库 §5 条目
   属旧仓/LiquidAgent 经验线，回写知识库时须订正，此处不擅改共享库）；
   PAT 只 URL inline 一次性使用、绝不落盘；`git push --dry-run` 才是真判据；
   `-c http.sslVerify=false` + `GIT_TERMINAL_PROMPT=0` + `credential.helper=`。
10. 🔴 **矩阵取证口径**：`max_tokens` 给足是前置（**基线 ≥512、思考型 ≥1024**，tools ≥512、xhigh ≥1500）——
    本波主理人实测 5 模型 `max_tokens=32` 时 `content=None`（reasoning 吃光预算）、提到 512 后正常出 `PONG`
    ⇒ 详见矩阵预登记 §一.1/§3.1；**判据按 `finish_reason`/`tool_calls` 判、不按 `content` 判**（W9-09 同型）；
    App 恒 `stream:true`，PC 非流式结论迁移须重新取证；四元组 `max_tokens/prompt_tokens/completion_tokens/finish_reason` 强制登记；
    1M 档 ≥2 次重放才写稳定结论；设备侧与 PC 侧**分开记账**。
11. 🔴 **环境坑**：沙箱代理端口**跨会话漂移**（本席现取 `https_proxy=http://127.0.0.1:6935`，
    启动前 `env \| grep https_proxy` **现取**，不写死）；curl 外网需 `--ssl-no-revoke`；
    API 凭据只读注入，**绝不写进任何文档/日志/请求体回显**；`deepseek-v4.1-flash` 420s 超时坑 ⛔ 不排入题面。

---

**本文件只登记与派单，不判定通过**。四态 `✅ / ⚠️ / ⛔ / ⛔vacuous` 的跑后结论写进 handoff 与矩阵验收记录，
不得写进预登记文件。判据先写死，跑后不得改（改则版本 +1 原样重跑）。
