# W8 Phase 1 · 外审结论回树核实判定清单

> 审查席：严质衡（代码改动质量审查）。**只读研究**：未修改任何源码/配置，未 `git add`/`commit`/`push`，未跑任何会改动设备的 adb 命令。仅在 `docs/analysis/` 新建本文档。
> 对象：现役仓 `D:/WBfil/2026-09-18-18-27-37/harness-v2`，`git rev-parse --short HEAD` = **`578f7e4`**，`git status --short` = `?? docs/analysis/`（工作树干净，仅本目录未跟踪）。
> 依据：`AGENTS.md` §二.2「采纳任何处方前先 grep 现役树核实」；知识库 §6「外审引用须带出处、行号前必须回原文 grep + `wc -l` 反查它说的是哪个修订」。
> 判定四态：**✅ 属实（可采纳）** / **⚠️ 部分属实** / **⛔ 已过时或证伪** / **⛔ vacuous（判据未被行使，不算通过）**。
> 处置口径：**同波收**（≤30 行、不与功能波争文件域、有明确判据）/ **挂账**（下一波）/ **不做**（不成立或已被用户新需求覆盖）。
> 本波用户实际需求 = **W8 功能波**（① 端点高级配置 ② 会话框 markdown 显示为文本选项 ③ 模型返回 JSON 格式检查 ④ 旧仓 UI 复用 ⑤ 本地 UI/功能 + adb 真机通过才推仓库与 CI ⑥ 免费节点多模型矩阵测试 ⑦ `dots-3-note-preview:free` 请求格式），**不是**外审四份报告默认的「正确性收口波」。所有优先级判定以此为第一前提。

---

## 〇、总判定、证伪清单与复跑索引

### 0.1 审查结论

**结论：主理人裁决方向 = ✅ 通过（可采纳），但须按本清单做 4 处订正。**

理由：四份外审（复审41 / W67 / W2.6–W7 / W8深度审查）全部以 `578f7e4` 为基线、且**四份都把 W8 默认成「正确性收口波 / 打磨发布前波」**（W8深度审查 §0 明写「W8 不建议直接进打磨与发布，建议先开正确性收口波」；W67 §五「W8 主线 A 正确性收尾」；复审41 §二「W8-01 同波 4.0 尺四面统一」）。用户本波明确要的是**功能波**，其 ①⑥⑦ 会直接改动 `core-model/RemoteEndpoint.kt`、`core-model/GenerationEvent.kt`、`core-engine/provider/OpenAiCompatibleAdapter.kt`、`core-engine/RemoteLlmEngine.kt`、`feature-settings/EndpointEditScreen.kt`、`feature-chat/ChatBubble.kt` —— 与 C3/C4/W8-01/W8-02/W3-05 的文件域**高度重叠**。把重型正确性重构塞进同波，必然在 `feature-chat`（现役 3619/3900，余 281 行）、`core-design`（12439/12600，余 161 行）、`ChatViewModel.kt`（487/500，余 13 行）三处同时触 G10 行数闸门。故「以功能为主波 + 同波只收低成本重合项 + 重型挂账」方向正确。

4 处订正（详见 §三/§四/§七）：
1. **W8-03 二选一不能当 ≤30 行快修**（外审/主理人快修批都列了它）：实为 14 个文件、跨 7 个 main 源集与 7 个测试类，且直接碰 `core-model/GenerationEvent.kt` + `core-engine/RemoteLlmEngine.kt` —— 正是功能波要改的文件。应挂账并显式改号，别做第 4 次「不许再延期」的顺延。
2. **supportsVision / NEW-D 不是「顺手解决」而是功能波主体**：现役 adapter **已经在发 `image_url` part**（`OpenAiCompatibleAdapter.kt:292-310`），却把 `supportsVision` 硬编码成 `false`。这不是「加个字段」而是「能力声明与实现打架」+「无闸门」，必须在 ① 里一并收口并销 W3-07。
3. **C6.a 的「收敛为单真值源」被夸大**：除两处 `runCatching` 外，`AppContainer.kt:265`、`SettingsViewModel.kt:229` 也在裸取同一 Flow；修两行仍留两份拷贝。且「加 runCatching 守卫」需要新增 selftest case，不是 ≤30 行。
4. **台账/roadmap 回填不能单独提交**：`build.yml` 的 `paths-ignore` 会让纯文档提交 **0 run**（`AGENTS.md` §八 / 知识库 §5），必须与代码提交同车；且台账 §二 统计表本身与表格行状态自相矛盾（见 §0.3 新发现 N1），回填时一并修。

**守卫影响总览**：本波不改 `TOTAL_LINE_CAP=42500`（`arch-guard.sh:204`），不改 `VACUOUS_BASELINE=1`（`:256`），不改 `LINE_CAPS`（`:152-193`）。但新增字段/新 UI/新单测会同时触碰 **G17 / G22 / G23 / G10 / EXPECTED_TESTS / VACUOUS_BASELINE** 六处，逐条见 §六。**凡新增守卫必同步 selftest case**（现役 `CASES` 28 条，`arch-guard-selftest.sh:105-139`），本波建议不新增守卫，只做「重跑 + 同步常量」。

### 0.2 证伪清单（本任务最高价值产出）

> 凡「外审说存在但 grep 不到」或「外审说缺失但现役已有」，逐条钉死。

| # | 来源 | 外审主张 | 回树核实（可复跑） | 判定 |
|---|---|---|---|---|
| F1 | W2.6–W7 **NEW-F** | `GenerationRound.kt:117/121`、`RunState.kt:210`、`AgentPolicy.kt:168` 等 **5 处注释引用了不存在的机制，对后来者主动误导**；建议「或先把五处注释改成『未实现，见 W3-05』」 | **现役注释已明写未实现**：`GenerationRound.kt:125-129`「⚠️ 冻结感知时钟（旧文档里叫 `FreezeClock`）**本仓尚未落地，挂账 W3-05** —— 全仓零实现。别在别处引用它当成『已接的机制』」；`RunState.kt:305-310`「**尚未落地**，挂账 W3-05 —— 全仓零实现，别在别处引用它当成已接机制」；`AgentPolicy.kt:211-214`「**尚未落地，挂账 W3-05**，全仓零实现…这里如实登记，不假装已经处理」；`AgentRunner.kt:57-62`、`GenerationRoundTest.kt:38` 同款。行号也已漂（117/121/210/168 → 125/307/211）。`find . -iname '*Journal*' -o -iname '*FreezeClock*'` = 空（journal 实现仍缺，此半属实） | **⛔ 证伪**（误导性注释/**改注释处方**已不成立；「journal 未开波」✅ 属实）。**处置：不做**改注释 |
| F2 | 复审41 **C11.2** | KDoc 笔误三处：`RunState` "easuredCharsPerToken"（掉 m）、`RunnerCache` 两处 "axContextTokens]" | 精确形态全仓 0 命中：`grep -rn "easuredCharsPerToken" --include=*.kt . \| grep -v "measuredCharsPerToken"` → 空；`grep -rn "\[axContextTokens\]" --include=*.kt .` → 空。现役 `RunnerCache.kt:18/:27` 均为正确的 `[maxContextTokens]`；`RunState.kt:126/267/283` 均为 `measuredCharsPerToken` | **⛔ 证伪**（外审查的是旧版树）。**处置：不做** |
| F3 | **W67 §一 / §三 P2** | 「现役 per-module 上限之和 = 450+1650+6500+1000+1200+2600+1400+4500+12600+3900+3400+2600+1600 = **43 400 > TOTAL 42 500**，缺口 **900**」 | 实跑 `awk` 解析 `scripts/arch-guard.sh:152-193` 的 `LINE_CAPS`：**Σcaps = 46 000**，缺口 **3 500**。报告列了 13 项，**漏了 `core-model|300|2600`**（`:153`）。逐项：2600+1200+2600+450+1650+6500+1000+1400+4500+12600+3900+3400+2600+1600 = 46000。`TOTAL_LINE_CAP=42500` @ `:204` | **⛔ 证伪（数值错）**；但「Σ > TOTAL 且无自检」✅ 属实且**比外审说的更严重**（缺口 3500 非 900）。另：台账 W7-07/W6-07 又写「per-module 之和 2900」，是只算 kernel+context 两模块（450+1650=2100）再混加 +1000 功能增量的**第三套口径**，须统一 |
| F4 | 复审41 §〇 | 「实跑一致：arch-guard `OK=27 VACUOUS=1 VIOLATION=0`、**selftest 30/30**（三线各自独立实跑）」；台账 W7-07「预期 28/28→30/30」 | 守卫实跑一致 ✅（`OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`，见 E2）。但 selftest：`grep -cE '^\s*"[A-Za-z0-9_]+\|' scripts/arch-guard-selftest.sh` = **28**；`grep -cE '^m_[a-z0-9_]+\(\)'` = **28**；`arch-guard-selftest.sh:93` 自述「覆盖口径 = arch-guard.sh 实际产出的**全部 28 个 ID**」；CASES 数组 `:105-139` 恰 28 行（含 G22/G23）。末尾另有 G21「VACUOUS 机制自证」独立段（`:100-103`），**不在 CASES 内** | **⛔ 证伪（30/30）**：现役严格口径 **28/28**。「30」是把独立自证段算进去的口径，不是 case 数。**处置：不做**，但本波任何「30 个 case」措辞须改 28 |
| F5 | W2.6–W7 **§3.2** | 「`evaluateSwitch` 在 feature-settings **三处赋值形态**被 G22 钉住」 | 实跑 G22 = **2 处**（`SettingsViewModel.kt:117` save、`:292` guardSwitch，`arch-guard.sh:1450-1456`）；第 3 处是 `SettingsViewModel.kt:237` 的 `blockedEndpointIds(...)` **间接调用** → `SwitchDecision.kt:85` 内部逐端点判。**W8深度审查 §2 自己写的是「两处赋值形态…G22 实跑 = 2」（正确）**，两份外审互相矛盾 | **⛔ 证伪（数字）**；「存在性判据」✅ 属实 |
| F6 | W2.6–W7 **§二#8** | 「UI 可挂 4 张图但图片实际能否进请求 **[未取证]**」 | 现役可直接证：`ChatMessage.kt:30` 有 `attachments`、`:37-38` 有 `sendableAttachments`；`AppNavHost.kt:156` `rememberImagePicker{...}` → `:170 onPickImage = pickImage` → `ChatViewModel.kt:168 attachImage` / `:274` / `:344 userAttachments` → `OpenAiCompatibleAdapter.kt:279-317 contentElement` 发 `image_url` base64 part。**已接线** | **⚠️ 已过时**（不是缺陷，是外审环境未覆盖）；并入 NEW-D 一并处理 |
| F7 | **W67 §三 P1** | 「按 `_w7/device-acceptance.md` 四态回填台账：W5-01/W7-01 **CLOSED**；**W7-02 按修正后的结论 CLOSED**」 | 前三条回填滞后 ✅ 属实（台账 `:78/:93/:94/:95` 全 `OPEN`；`find . -name device-acceptance.md` = 空，真机四态表确实只在仓外）。但 **W7-02 不能 CLOSED**：`handoff-20261104-wave7.md:93`「三连发+改首字节…**无一满足双向断言 ⇒ W7-02 按『取到事实表』验收，不承诺命中率**」；台账 W7-02 待证判据是「第 3 次 `cached_tokens>0`」（`:94`），而 A5 的 `21680 cached/79.4%` 是**真实长会话**形态（`:82/:86`），非三连发探针 | **⚠️ 部分属实**：W5-01/W7-01 可 CLOSED；**W7-02 只能回填「⚠️ 部分·事实表已取」**，不得写 CLOSED（否则违反 AGENTS.md §二.3「未触发≠已验证」） |
| F8 | **C4 / 复审41** | 「`toolSpecsSnapshot` run 内快照**现成，零新机制**」 | 快照确实存在，但是 `ContextBudget.kt:125` 的 **`private var`**，`:209-218 toolSpecsFor` 私有；`PreStepBudget.kt:96` 现役直接 `ctx.tools.specs()`。要喂给 L3 字符判据需改可见性/传参，不是零改动 | **⚠️ 部分属实** |
| F9 | **NEW-G** | 「W6-05 `allowClipboardRead` … 死字段专项清剿」 | 该字段**有真消费点** `ClipboardTool.kt:78`（`if (!policy.allowClipboardRead && actionOf(...) == Action.GET) READ_DISABLED`），缺的是 UI/setter，不是「死字段」。其余三项（`autoApproveDangerous` 仅 `AgentPolicy.kt:115`、`capabilities()` 仅接口/实现/委托 4 处、`SkillImportValidator` 仅 KDoc 引用）✅ 属实 | **⚠️ 部分属实**（措辞不准，处置不变） |
| F10 | 复审41 **C12 例1 / W7-04** | 「`MemoryTools.kt:30` 虚构 KDoc 已清」「占位测试清零」 | `grep -nE "maxConcurrency\|reentrant" MemoryTools.kt` = 空 ✅；`grep -rn "assertTrue(true)" --include=*.kt .` = 空 ✅；`EXPECTED_TESTS=696` = 严格 @Test 696 ✅ | ✅ 属实（报告自述「已清」，非证伪） |

### 0.3 本席新发现（四份外审均未记载）

| # | 发现 | 证据 | 影响 |
|---|---|---|---|
| **N1** | **台账 §二 统计表与 §一 表格行状态自相矛盾**：表格 `W7-04` 行 = `CLOSED`（`:96`），但 §二 统计表（`:132-134`）`CLOSED` 11 条**不含** W7-04、`OPEN` 24 条**含** W7-04。逐行实数 = **CLOSED 12 / CARRIED 7 / OPEN 23 / 合计 42** | `sed -n '96p;130,136p' docs/06-deferred-ledger.md`；`grep -n '^\| W7-04' …` | 正是 C12/D8 想加的「台账自洽判据」的**第一个真实命中**，而 **G20 判不出**（G20 只查 ① ID 存在 ② CLOSED 证据非空 ③ 状态值合法 ④ 重复 ID，`arch-guard.sh:1300-1417`）。回填时必须一并修统计表 |
| **N2** | **「注释与实现打架」第 5/6 例（C12 专册漏收）**：`ChatAttachmentPicker.kt:16-19` KDoc 写「**当前 main 源集里没有调用点**：本仓 `ChatMessage` 还没有 attachments 字段、`ChatUiState` 也没有附件通道，`ChatInputBar` 因此不渲染『+』入口」；`ChatInputBar.kt:42-44` 同款。实际：`AppNavHost.kt:156/170` 已接 `rememberImagePicker`、`ChatInputBar.kt:72` `if (onPickImage != null)` **已渲染** `+`、`ChatMessage.kt:30` **已有** attachments、`ChatViewModel.kt:168` 已有 `attachImage` | 上述 5 处 `文件:行` | 功能波 ① 的图片输入/④ 的旧仓 UI 复用正好会读到这两段 KDoc ⇒ **主动误导**。≤6 行 KDoc 改写，同波可收（§三 Q7） |
| **N3** | **W8-05 编号已被路线草稿占用**：`docs/notes/w7-context-cache-roadmap.md:108` 有 `W8-05 反应式压缩`；C4 建议「新开 **W8-05**」会造成**第三重撞车**；外审 P1-3 只点出 W8-01..04 撞车 | `grep -n "W8-05" docs/notes/w7-context-cache-roadmap.md` | 挂账编号建议须避开 W8-05（见 §五） |
| **N4** | **G20 扫描面根本不含 `docs/notes/`**：`arch-guard.sh:1327` `files="$(find docs -maxdepth 1 -type f -name 'handoff-*.md')"`。路线草稿里 W8-01..05 全部**不在** G20 对账域；handoff W7 §七（`:114`）里的 W8-01..04 与台账同义，故 G20 全绿也**不能**证明没撞车 | `arch-guard.sh:1327-1331`；`grep -oE 'W8-[0-9]+' docs/notes/w7-context-cache-roadmap.md \| sort -u` → `W8-01 W8-02 W8-03 W8-04 W8-05` | P1-3「G20 判不出编号撞车」✅ 属实，**且比它说的更弱**：连 notes/ 都没扫 |
| **N5** | **台账/裁决文档的行号与数值漂移**：`W3-07`（`:69`）写 `core-model/GenerationEvent.kt:136` 的 `supportsVision` 默认 false，实际在 **`:142`**；`W3-05`（`:67`）写 `GenerationRound.kt:117,121` / `RunState.kt:210` / `AgentPolicy.kt:168`，实际 **125 / 307 / 211**（F1）；`docs/notes/w7-line-budget-ruling.md:61` 写「`ChatViewModel` **484/500（余 16）**」，而 E6 实跑为 **487/500（余 13）**；`SettingsRoute.kt` 的 `onImport = null` 外审写 `:206`，实际 **`:225`** | `cat -n core-model/.../GenerationEvent.kt \| sed -n '139,143p'`；`wc -l feature-chat/.../ChatViewModel.kt` → 487；`grep -n "onImport = null" feature-settings/.../SettingsRoute.kt` → 225 | 引用纪律（知识库 §6）命中：不改不影响功能，但回填时必须顺手钉正，否则下一波继续扑空；**余量数字以 E6 为准，别照抄裁决文档** |
| **N6** | **台账 W7-07 状态滞后于 handoff**：台账 `:99` W7-07 = `OPEN`，而 `handoff-20261104-wave7.md:113` §七「**本波关闭**：… W7-07（预算记账 + 守卫落地）」 | 两处对读 | C10.a 的「状态滞后」不是孤例，回填时须三向对账（台账 ↔ handoff ↔ 代码） |
| **N7** | **功能波 ① 的 `dots` 请求格式证据在仓内已有底稿**：`docs/analysis/w8-provider-probe.md`（主理人 Phase 0，本次会话产物）`:26-49` 实测 `reasoning:{"effort":"low"}` 被接受、`reasoning_effort:"low"` 疑似被忽略、`reasoning:{"enabled":false}` 真关思考、`include_reasoning:false` 只隐藏、五档只认 `low/medium/high/xhigh`（`max` 疑非法）、`response_format:{"type":"json_object"}` 返回严格 JSON、图片 `content` 数组真实可用 | `w8-provider-probe.md:31-49` | 用户 ⑦③① 的硬约束来源；四份外审**均未覆盖**（它们早于本波探针）。判据：新增 `ReasoningStyle.OPENROUTER_REASONING_OBJECT` 形态，且**不得**把「极致」静默发成非法值 `max` |

### 0.4 复跑命令索引（下文表中 `E#` 均指此）

```
E1  cd D:/WBfil/2026-09-18-18-27-37/harness-v2 && git rev-parse --short HEAD && git status --short
    → 578f7e4  /  ?? docs/analysis/
E2  bash scripts/arch-guard.sh | tail -8
    → OK=27  VACUOUS=1  VIOLATION=0  SELF_FAULT=0
      [G19] VACUOUS (0 个日志调用点)  [G21] OK (VACUOUS=1 ≤ 基线 1)
      [G22] OK (= 2 处)  [G23] OK (= 1 处，shouldEvaluate = 1 处)
E3  grep -rnE '^\s*@Test\b' --include=*.kt . | wc -l          → 696
    grep -n "EXPECTED_TESTS" .github/workflows/build.yml | head -2 → :132  EXPECTED_TESTS: '696'
    grep -rn "assertTrue(true)" --include=*.kt .               → 0 命中
E4  awk '/^LINE_CAPS=\(/,/^\)/ {if($0~/"/ && $0!~/^#/ && $0~/\|/){split($0,a,"\"");split(a[2],b,"|");s+=b[3]}} END{print s}' scripts/arch-guard.sh
    → 46000     ; grep -n "TOTAL_LINE_CAP=" scripts/arch-guard.sh → :204 = 42500
E5  grep -cE '^\s*"[A-Za-z0-9_]+\|' scripts/arch-guard-selftest.sh → 28
    grep -cE '^m_[a-z0-9_]+\(\)' scripts/arch-guard-selftest.sh     → 28
E6  for m in <14 模块>; do find $m/src/main -name '*.kt' -print0 | xargs -0 cat | wc -l; done
    → core-model 1439 / core-net 790 / core-engine 1081 / core-kernel 403 / core-context 1584
      core-agent 5326 / core-skills 419 / core-mcp 758 / core-data 2329 / core-design 12439
      feature-chat 3619 / feature-settings 3229 / feature-tools 947 / app 1338
      ; wc -l feature-chat/.../ChatViewModel.kt → 487（单文件上限 500，余 13）
E7  grep -rn "supportsVision" --include=*.kt .
    → core-engine/RemoteLlmEngine.kt:92（=false）/ core-model/GenerationEvent.kt:142（默认 false）
      / core-agent/src/test/.../FakeEngine.kt:38   （共 3 命中，RemoteEndpoint 无字段）
E8  cat -n core-model/.../RemoteEndpoint.kt | sed -n '96,137p'   → 15 个字段，无 supportsVision
    grep -nE 'label =|mutableStateOf|onValueChange' feature-settings/.../EndpointEditScreen.kt
    → 338 行；仅 name/baseUrl/modelId/contextLength/allowPrivateNetwork/apiKey 6 格；零高级配置
E9  sed -n '178,186p;279,317p' core-engine/.../OpenAiCompatibleAdapter.kt
    → applyReasoningStyle 仅 enable_thinking / reasoning_effort / chat_template_kwargs
      contentElement 已发 image_url base64 part（:292-310）
E10 grep -nE 'markdown|Markdown|AnnotatedString|RichText' feature-chat/.../ChatBubble.kt → 0 命中
    (ChatBubble.kt 187 行，:123 注释「旧仓的第二枚『复制为 Markdown』…成了装饰」)
E11 sed -n '394,395p' feature-chat/.../ChatViewModel.kt   → :394 分流先于 :395 onEvent
    sed -n '96,137p' feature-chat/.../ConversationOps.kt  → 快照/试算/落账全流程
    cat -n core-context/.../SettledCompressPolicy.kt      → :46/:49 常量、:81 alreadyCompressed
E12 sed -n '96,136p' docs/06-deferred-ledger.md            → W8-01..04 唯一台账定义
    grep -nE 'W8-0|下一波.*交接|历史输入' docs/notes/w7-context-cache-roadmap.md
    → :64/:65/:91/:99/:106/:107/:108 W8-01..05，:172 自称「下一波（W7/W8）交接文档」
    sed -n '11,21p' docs/02-roadmap.md                      → 总览「截至 W4」，W6/W7/W8 全「⛔ 未开始」
```

---

## 一、逐条判定表（四份外审，不漏条）

> 列：`来源编号 | 一句话主张 | 回树核实结果 | 判定 | 处置 | 理由`。`E#` 见 §0.4；行号均为 `578f7e4` 现役树。

### 1.1 复审 41 · 终版合订（C 系 C1–C13，含子项）

| 来源编号 | 一句话主张 | 回树核实结果（文件:行 / E#） | 判定 | 处置 | 理由 |
|---|---|---|---|---|---|
| **C1** | G20 对账域只有 handoff↔台账，外审合卷→台账「入账」机制不存在；A14/A15/M2 余件/W5.5 预登记在 42 条里零条目 | `grep -nE "外审承接\|C[1-5]\|A14\|A15\|预登记\|复审\|W5\.5" docs/06-deferred-ledger.md` → 0 命中（仅 `:78` W5-01 证据列含 "M2"）；G20 三条判据 + 重复 ID @ `arch-guard.sh:1308-1312,1367-1390`；`docs/notes/w26-preregistration.md` 是唯一预登记（旧周期） | ✅ 属实 | **挂账（机制，随下一波正确性波）** | 机制成立且是「总闸」，但**加新判据必同步 selftest case**（知识库 §5），现役 28 case；本波加守卫会与功能波争 `arch-guard.sh` 且需变异自证，不属 ≤30 行。建议本波只把 C 系条目**以文档形式**列入 handoff W8，不承诺机制落地 |
| **C2** | W3.5（W3-05 journal/FreezeClock）第三轮跳过，台账仍 OPEN；W8-01 真机验收裸奔 | 台账 `W3-05` @ `:67` = `OPEN`「尚未开波…建议独立 W6.5」；`handoff-20261104-wave7.md:116` §七「维持：W3-05 journal/FreezeClock 建议独立波」；`find . -iname '*Journal*' -o -iname '*FreezeClock*'` = 空（F1） | ✅ 属实 | **挂账（独立波）** | 用户本波是功能波；W3-05 会碰 `core-agent`/`core-kernel`/`core-context`/`feature-chat`/`app`，与功能波全面撞车。编号见 §四 H5 |
| **C3** | 4.0 估算尺「四面」各自为政，需三件套统一（含 D9 四边界、estimator_floor、systemText 扣减、W8-01 双件） | 面1/2：`SettledCompressPolicy.kt:41-49` `COMPACT_TRIGGER_CHARS=50_227`/`MIN_SAVINGS_CHARS=460` 自述 32K 档导出、`ConversationOps.kt:104-119` 试算走 `contextBudgetTokens` + `ManualCompress.kt:99-112` `TokenEstimator.estimateMessages`；面3：`SwitchGuard.kt:39` KDoc「本波不新增会话级持久化标定值」、`evaluateSwitch` @ `:72-109` floor 分支，`SettingsViewModel.kt:117/:292`、`SwitchDecision.kt:85` 全传 `0f`；面4：`ContextBudget.kt:147-165 prepare` 用 `estimatePrompt` vs 软预算 `contextBudgetTokens`；`BudgetFormula.kt:83 effectiveCharsPerToken` 现成；`SessionFootprintStore.kt:25-26` 进程级非持久化 | ✅ 属实（**F8 部分订正**：`toolSpecsSnapshot` 非「现成零改动」） | **挂账（H1）** | 四面全落在 `feature-chat/ConversationOps.kt`、`ChatViewModel.kt`、`feature-settings/SettingsViewModel.kt`、`core-context/*`、`core-agent/loop/ContextBudget.kt` —— 与功能波 ①②③ 的 `feature-chat` 文件域正面冲突，且 `feature-chat` 仅余 281 行。D9 边界①（进程级不持久化）现役已守（`SessionFootprintStore.kt:25-26`）；D9 边界②（`store` 不进 TurnLoop 取数路径）实跑 `grep -rn sessionFootprint core-agent/src/main` = 0 命中，**边界成立**，延后期间无需缓解 |
| **C4** | L3 字符判据漏工具规格序列化字符；L1 扣了工具、L3 漏；`HardBudgetShrinker` 物理上无法收缩 specs | `PreStepBudget.kt:95-98` `chars = systemChars + HardBudgetShrinker.promptChars(body)`（**不含 toolSpecChars**），`:96` `toolTokens = TokenEstimator.estimateToolSpecs(ctx.tools.specs())`、`:98` `estimated = estimate(ctx, body) + toolTokens`（token 闸含）；`ContextBudget.kt:302-310` `fingerprintFor` 四段 `SYSTEM/SKILLS/TOOLS_SCHEMA/HISTORY`；`ContextBudget.kt:125` `private var toolSpecsSnapshot`（F8） | ✅ 属实（W2.6–W7 §二#3「M2 已修」指 **token 闸**，与 C4 的**字符闸**不是同一把尺，两份报告不矛盾） | **挂账（H2）** | 会碰 `core-agent/loop/PreStepBudget.kt`+`ContextBudget.kt`、`core-context/HardBudgetShrinker.kt`+2 枚单测，**不直接争功能波文件域**，但属重型正确性重构 + 需 `toolSpecChars` 新口径；功能波 ⑥ 引入 MCP 多工具会**放大**此缺口 ⇒ 延后期间需临时缓解（§四） |
| **C5** | M2 余件：`MemoryTools` 三处 ToolSpec 补 `requiresConfirmation=true` + 审计穷举表加 memory 两行；归宿 W8-03 | `MemoryTools.kt:35/:80/:119` 三处 `ToolSpec(` 全无 `requiresConfirmation`（`grep -n requiresConfirmation MemoryTools.kt` = 0）；`FileWriteTool.kt:49` 有 `= true`；`ApprovalChokepointAuditTest.kt:108-160` `askSourceTable()` 7 行 AskRow（`:121/126/131/136/141/146/152`），`:170 assertTrue(rows.size >= 6)`，全文件无 memory 行 | ✅ 属实 | **挂账（随 W8-03 改号后同波，见 §四 H6）** | 本身 ≤10 行、文件域 `core-agent` 不与功能波冲突；但 C5 明确「归宿 W8-03 审批面二选一波」，而 W8-03 本波挂账（见下）。**可选**：若主理人愿拆，可把 C5 单独同波收（§三 Q8），但需同提交改 `EXPECTED_TESTS` |
| **C6.a** | `ConversationOps.kt:106/:180` 两处 `runCatching{...first()}` 吞 `CancellationException`；「零新代码」替换为 `contextWindowCap(container)` 即收敛单真值源 | `ConversationOps.kt:106/:180` 确为 `runCatching { container.settings.maxContextTokens.first() }.getOrDefault(AgentPolicy().maxContextTokens)`；`ChatContextWindow.kt:18-31` KDoc 逐字点名反例 + 正例 `contextWindowCap`（`internal suspend fun`，同包 `feature.chat` ⇒ 可直调）；`ContextLimitWriteback.kt:55-57` 同款纪律。**但** `grep -rn "maxContextTokens.first()" --include=*.kt .` 另有 `AppContainer.kt:265`、`SettingsViewModel.kt:229` 两处裸取数 | ⚠️ 部分属实（「三份拷贝/单真值源」夸大；两处反例与正例属实） | **同波收（Q1），守卫候选挂账** | 改 2 行 ∈ ≤30 行、文件 `ConversationOps.kt` 不在功能波主改文件（markdown 在 `ChatBubble.kt`）；「加 runCatching 守卫 + selftest 变异自证」不是 ≤30 行 ⇒ 单独挂账 |
| **C6.b** | `SessionImportParser` UTF-8 BOM 盲区：`bytes.toString(UTF_8)` + `raw.trim()` 不剥 `\uFEFF` | `SessionImportParser.kt:139` `return parse(bytes.toString(Charsets.UTF_8), format)` 无 BOM 剥离；`:156` `val line = raw.trim()`；全文件 `grep -ni BOM` = 0。Kotlin `String.trim()` 不剥 U+FEFF（格式字符非 whitespace） | ✅ 属实 | **同波收（Q2）** | 1 行 + 1 个 BOM 夹具样本 ∈ ≤30 行；文件 `core-data/SessionImportParser.kt` 与功能波零冲突；判据明确（见 §三） |
| **C7** | G20 第二死角：「推 Wx 预约到期自动验尸」；现症 W6-05「推 W7」仍悬空、W5-06「推 W7+」同型 | G20 实跑仅 3 判据（`arch-guard.sh:1308-1312`），`grep -nE "推 W\|验尸\|到期\|第四" arch-guard.sh` = 0；台账 `W6-05` @ `:89` = `OPEN`「推 W7（与 ApprovalPolicyScreen 同波）」、`:66` W3-04「推 W7」、`:97` W7-05 `CARRIED`「推 W7」；`handoff-20261104-wave7.md:113-116` §七四个清单均未提 W6-05/W5-06。**但** `W5-06` @ `:83` 阻塞列写的是「落地时必须补进 `ApprovalChokepointAuditTest` 穷举表（现缺两行）」，**无「推 W7+」字面** | ⚠️ 部分属实（现症成立；W5-06 引文不精确） | **挂账** | 加第四判据 = 新守卫 + 必同步 selftest case + 存量两条改 `CARRIED`/入账，>30 行且需变异自证；本波不动 `arch-guard.sh` |
| **C8** | A14 闸门（X4 开波前思考 id 共设计）+ W7-02 结论分标注 + X4 判据 6「@Test ≥ 380」恒绿需修 | `docs/02-roadmap.md:811` 确为 `6. \`@Test\` ≥ 380。`（现基线 696，恒绿）；`grep -rn "A14\|思考回传\|interleaved" docs/02-roadmap.md docs/notes/*.md docs/handoff-20261104-wave7.md` 无 A14 字样；`handoff-20261104-wave7.md:86` A5 附带只写「NV 直连高命中」，**未**按「无思考回传形态 / 对 interleaved thinking 不可迁移」分标注 | ✅ 属实 | **拆分**：`X4 判据 6 修正` → **同波收（Q6，纯文档 1 行）**；`A14 闸门 + W7-02 分标注` → **挂账（随 X4 波）** | 判据 6 是纯文档、零冲突、可复跑（`grep` 即证）；A14 属下一波多供应商开波前置，本波 ① 的 `ReasoningStyle` 扩展不等于 X4 开波，别混 |
| **C9** | roadmap 总览 W6 行仍「⛔ 未开始」与 `feat(w6)` 脱节；总览「截至 W4」滞后两波；建议加 G24 | `docs/02-roadmap.md:11`「状态（**截至 W4**…）」、`:19/:20/:21` W6/W7/W8 全「⛔ **未开始**」；`:697`「现状（**截至 W6 开波**）」、`:702-704` X4/X5/X6「⛔ 未开始」；`grep -nE "G24\|G25" scripts/arch-guard.sh scripts/arch-guard-selftest.sh` = 0（G24 不存在） | ✅ 属实 | **拆分**：`roadmap/总览回填` → **同波收（Q4）**；`G24 新增守卫` → **挂账** | 回填是纯文档但必须与代码提交同车（0 run 陷阱，§0.1 订正 4）；G24 是新守卫需 selftest case，>30 行 |
| **C10.a** | W7-07 台账状态滞后（2v3 已定谳）；`:237` 改赋值形态使 G22 正则完备；`SwitchDecision.kt:16` KDoc 消除矛盾 | 台账 `:99` W7-07 = `OPEN`，handoff `:113` 已宣「本波关闭」（N6）；`SettingsViewModel.kt:237` 仍 `blockedIds = blockedEndpointIds(...)`（间接调用，**未改**）；G22 实跑 = 2 处（`:117/:292`）；`SwitchDecision.kt:11-12` KDoc「三处写入点统一写 `val verdict = evaluateSwitch(...)`」与实况（2 赋值 + 1 间接）打架（报告写 `:16` 是 API Key 段，**行号漂**） | ✅ 属实（行号订正：矛盾 KDoc 在 `:11-12`） | **挂账（随 G22 升级波）** | 改 `:237` 会动 `SettingsViewModel.kt`（功能波可能碰 save 链），且改形态必须同步 `m_g22` selftest 变异自证，不属 ≤30 行 |
| **C10.b** | `compressBubbles` `systemText=null` 扣减缺口，并 C3 配套（W8-01 同车） | `ManualCompress.kt:90` `systemText: String? = null`、`:99` `budgetTokens - systemTokens(systemText)`、`:146-148` `systemTokens(null) = 0`；`ConversationOps.kt:111` 调 `compressBubbles(snapshot.bubbles, budget)` **不传 systemText** | ✅ 属实 | **挂账（随 C3/H1）** | 与 C3 同车，不单独动 |
| **C10.c** | 进程重启后 footprint=0 ⇒ `empty_session` VACUOUS 放行切换，属观察项不升级缺陷 | `SessionFootprintStore.kt:25-26`「进程级、非持久化…判 `empty_session` ⇒ `VACUOUS`」；`SwitchGuard.kt:101-102` `if (committedChars <= 0) … "empty_session"` | ✅ 属实 | **不做（记录在案）** | 与 D9 边界① 同义，fail-vacuous 是设计；功能波不改 |
| **C10.d** | noop 落账 WARN 淹没真告警 → C11 | `ConversationOps.kt:122` `AgentLogStore.warn(line)` vs `:232` `container.logs.append("INFO", line)` | ✅ 属实（级别不一致，见 P2-1） | **并 C11.3** | 同波可选（Q5） |
| **C10.e** | 硬收缩 `replaceWorking` 后锚点不失效 ⇒ 下一轮高估多压破缓存 | `PreStepBudget.kt:144` 只 `ctx.state.replaceWorking(result.messages)`；`RunState.kt:188-191 replaceWorking` **不**调 `invalidatePromptAnchor()`；而压缩路径 `ContextBudget.kt:183/186` **两件都调**；`RunState.kt:280-284 invalidatePromptAnchor` 会清 `measuredCharsPerToken` | ✅ 属实（本席逐行确认，外审正确） | **挂账（随 C3/H1）** | 属正确性重构；但**成本仅 1 行 + 1 用例**，若主理人愿扩快修批可同波收（§三 Q9 备选） |
| **C11.1** | `HardBudgetShrinker.promptChars` 漏 `toolCalls[].id`，与 `SessionFootprint.messageChars` 口径不一致 | `HardBudgetShrinker.kt:91-107` 循环 `call.name.length + call.argumentsJson.length`（**无 `call.id`**）；`SessionFootprint.kt:50-57` `messageChars` 含 `call.id.length` | ✅ 属实 | **挂账（随 C4/H2）** | 与 C4 同一「工具规格字符」口径族，一并修避免两把尺 |
| **C11.2** | KDoc 笔误三处 | **F2 → 0 命中，已修** | ⛔ **证伪** | **不做** | 外审查的是旧版树 |
| **C11.3** | `autoCompactOnSettled` noop 落账 WARN→info | `ConversationOps.kt:122` `AgentLogStore.warn`，`:232` 落盘 `INFO` | ✅ 属实 | **同波收（Q5，可选）** | 1 行；与 Q1 同文件，须串行单人落笔 |
| **C11.4** | `SessionImportParser.append` 上限判定先于 isBlank ⇒ 第 5001 条空消息误触发 TOO_MANY_MESSAGES | `SessionImportParser.kt:257-276`：`if (body.length > MAX_MESSAGE_CHARS)` → `if (messages.size >= MAX_MESSAGES) return TOO_MANY_MESSAGES`（`:269-274`）→ **`if (body.isBlank()) return null`（`:276`）** | ✅ 属实（顺序精确命中） | **同波收（Q3，可选）** | 对调两行 ∈ ≤30 行；与 Q2 同文件 `core-data`，零冲突 |
| **C11.5** | 硬收缩后锚点不失效（同 C10.e） | 同 C10.e | ✅ 属实 | **挂账 / 备选 Q9** | 同 C10.e |
| **C12 例1** | `MemoryTools.kt:30` 虚构 KDoc（maxConcurrency/reentrant）已清 | `grep -nE "maxConcurrency\|reentrant" MemoryTools.kt` = 0 | ✅ 属实（报告自述已清） | **不做** | 已收口 |
| **C12 例2** | `ContextBudget.kt:302` fingerprint 四段 vs 同文件 L3 硬预算两段，文件内自相矛盾 | `ContextBudget.kt:302-310` `fingerprintFor` KDoc 列 `SYSTEM/SKILLS/TOOLS_SCHEMA/HISTORY`（实际函数在 `:306`）；`PreStepBudget.kt:95-98` 字符闸只数 `systemChars + promptChars(body)` | ✅ 属实（行号 302→306 微漂） | **挂账（随 C4/H2）** | 随 C4 修 L3 时自然消除 |
| **C12 例3** | `SwitchGuard.kt:39`「本波不新增会话级持久化标定值」将过时，预收进册 | `SwitchGuard.kt:39` 逐字命中；`docs/notes/w7-plan.md:166` 同表述「本波**不**新增会话级持久化标定值」 | ✅ 属实 | **挂账（随 C3/H1 扩列时同步改写）** | 本波不动 `SwitchGuard.kt`，无需改；写入 H1 交付物清单防第五例 |
| **C12 例4** | `SwitchDecision.kt:16` KDoc「统一写赋值形态、G22 扫这个形状」 vs `:237` 间接调用 | 矛盾 KDoc 实为 `SwitchDecision.kt:11-12`（`:16` 是 API Key 段）；`:237` = `blockedEndpointIds(...)` 间接调用；G22 实跑 2 处 | ⚠️ 部分属实（**行号错**，矛盾成立） | **挂账（随 C10.a）** | 同 C10.a |
| **C12 动作①** | 改判据必同步改 KDoc；W8 扩列交付物显式列「待同步 KDoc」 | 现役无此机制（`grep -rn "待同步.*KDoc" .` = 0） | ✅ 属实（缺口） | **挂账（写进 H1 交付物清单）** | 纯纪律，不需要本波代码 |
| **C12 动作②** | 外审引用须带模块全路径 | `ChatContextWindow.kt` 存在于 `feature-chat/.../ChatContextWindow.kt`（§四出处之争定谳）；本报告已全用模块全路径 | ✅ 属实 | **不做（本席已执行）** | 纪律，非代码 |
| **C12 自洽判据（D8）** | 台账 OPEN 行证据列出现「闭合/已关闭」判红；现症 W5-02 写「该子项闭合」、W7-01 OPEN 但 handoff 已宣重放通过 | 台账 `:79` W5-02 = `OPEN`，证据列末尾「⇒ **四跳全接且全有判据，该子项闭合**」；`:93` W7-01 = `OPEN` 而 handoff `:82` A5 = ✅；**G20 awk（`:1358-1390`）不检查证据列措辞**；另 N1 统计表自相矛盾 | ✅ 属实（且 N1 是更强的第一个真实命中） | **挂账（随 C7/G20 升级波）** | 新判据需 selftest case；本波只做 Q4 的文档回填（含修 N1） |
| **C13.1** | A15 修订版：回落双口径 + 触发计数 + 行使层四态，跑前预登记 | `LayeredContextCompressor.kt` 现役（需核 `:254` WARN 行）；`docs/` 下预登记文件仅 `w26-preregistration.md`（旧周期）；本波无 W8-01 预登记 | ✅ 属实（缺位） | **挂账（随 H1 开波前）** | W8-01 本波挂账 ⇒ 预登记随之前移；但建议**改造复用**：本波 ⑥ 免费节点矩阵也需要「跑前定死判据」（§三 Q10 建议） |
| **C13.2** | 术语规范：W8 文档层 L1/L2/L3 专指 PreStepBudget 三层，与 LayeredContextCompressor 原 L1/L2 映射 | `PreStepBudget.kt` 三层与 `LayeredContextCompressor.kt` 两套 L1/L2 并存（文件均在） | ✅ 属实 | **挂账（随 H1）** | 文档纪律 |
| **C13.3** | A5 证据链补钉：真机复放摘录 `hard_budget chars= cap= cpt= hard= estimated=` 日志行 | `PreStepBudget.kt:102-105` 该日志行已存在（`AgentLogStore.info("hard_budget chars=…")`）；handoff A5（`:82`）只给了 `27.2k/21680/79.4%`，未逐轮摘录日志行 | ✅ 属实（日志行在、摘录缺） | **挂账（随 H1 真机复放）** | 需真机，本波 ⑥ 矩阵机时应优先给功能验收 |

#### 1.1b 复审 41 §〇 承接核销表（14 条，外审自标「核销表」，一并核实）

| 合卷项 | 回树核实 | 判定 | 处置 |
|---|---|---|---|
| 执行序② 判定点下沉 | `PreStepBudget` @ `TurnLoop.kt:60` 挂轮头；W6 换量纲（字符权威刀）在 `PreStepBudget.kt:88-127` | ✅ 属实（被订正替代） | 不做 |
| 执行序⑤ MemoryTools 虚构 KDoc | F10 / C12 例1：0 命中 | ✅ 属实（已清） | 不做 |
| 执行序③ W8-03 限期 | 台账 `:102` W8-03 `OPEN`「W8 首波强制二选一…不许再延期」 | ✅ 属实 | **挂账改号**（§四 H6） |
| 执行序①④ W5.5 跑前预登记 | `docs/notes/w26-preregistration.md` 是唯一预登记（W2.6 周期），无 W8 预登记 | ✅ 属实（未落实） | 挂账（C13 / Q10） |
| A2/A6 压缩债 | 台账 `:78/:79` W5-01/W5-02 = `OPEN`；`handoff-20261104-wave7.md:82` A5 ✅ 已给真机数字 | ⚠️ 部分属实（**台账未 CLOSED**，报告写「台账 W5-01/W5-02 CLOSED」**证伪**） | **同波收 Q4**（回填，见 F7） |
| A4 基线 | `build.yml:132 EXPECTED_TESTS='696'`；`:445-523` 双向对账步骤在位 | ✅ 属实 | 不做 |
| A8+A13 | 台账 42 行全带状态/证据；G20/G21 在 `arch-guard.sh:1300/:1419`；但 **A13 单向**（外审→台账不可见）= C1 | ✅ 属实 | 挂账（C1） |
| A3 key 可得性 | `handoff-20261104-wave7.md:82` `27.2k ≤ 27904` + `21680 cached/79.4%` | ✅ 属实 | 不做 |
| A5 G19 转实守 | E2：G19 仍 VACUOUS（0 日志调用点）；台账 `:87` W6-03 `OPEN` | ✅ 属实 | 挂账（H7） |
| A7 预埋 | 台账 `:102` W8-03 已开；`:83` W5-06 `OPEN` | ✅ 属实 | 挂账 |
| A14 思考 id 共设计 | 全仓 `grep -rn "A14"`（docs/roadmap/notes/handoff）= 0；`ReasoningStyle.ANTHROPIC_THINKING_BUDGET`/`GEMINI_INCLUDE_THOUGHTS` 在 `RemoteEndpoint.kt:77-81` 只是枚举值，`OpenAiCompatibleAdapter.kt:185-186` 明确 `-> Unit` 不写 | ✅ 属实（悬空） | 挂账（C8） |
| A15 L2 回落率双口径 | 无预登记、无行使层单列（`ConversationOps.kt:120-124` 只落 noop reason） | ✅ 属实 | 挂账（C13.1） |
| M2 修法 1/3 | F10 已清 1/3；余 2 件见 C5 | ✅ 属实 | 挂账（C5） |
| W3.5 重排不删 | 台账 `:67` W3-05 `OPEN`，第三轮跳过 | ✅ 属实 | 挂账（C2/H5） |

#### 1.1c 复审 41 §二 W8 行动单（8 行，路线级）

| 时点 | 外审动作 | 回树核实 | 判定 | 处置 |
|---|---|---|---|---|
| 总裁裁决 | ①C1 入账 ②C2 波号 ③波序确认 | 三项在报告中仍标 OPEN（`:192-194`），无裁决落地痕迹 | ✅ 属实（未裁） | **本波由用户功能波重裁**：C1/C2 挂账，波序以功能波为准 |
| W8 开波前 | 快修批（runCatching×2 + BOM）+ roadmap 回填 + G24 + C1 入账 | C6.a/C6.b/C9 属实；G24/C1 机制需新守卫 | ⚠️ 部分属实 | **Q1/Q2/Q4 同波收**；G24、C1 入账挂账 |
| W8-01 跑前预登记 | A15 + 术语映射 + A5 日志补钉 | `docs/` 无 W8 预登记 | ✅ 属实（缺位） | 挂账（随 H1） |
| W8-01 同波 | 4.0 尺四面统一（C3） | C3 属实（§1.1） | ✅ 属实 | **挂账 H1**（功能波不碰） |
| W8-03 | M2 余件（C5） | C5 属实 | ✅ 属实 | 挂账（随 H6 改号） |
| W8-05（建议新开） | L3 工具规格（C4） | **N3：W8-05 已被路线草稿占用** | ⚠️ 部分属实（编号不可用） | 挂账 H2，改号建议见 §五 |
| W8 各波守卫面 | G20 验尸 + G24' + G25 + runCatching 判据 + G24 变异 | 现役仅 G1–G23（E2 OK=27）；G24/G25 = 0 命中 | ✅ 属实（均未落地） | 挂账（本波不新增守卫） |
| X4 开波前 / 顺手修 | C8 闸门 / P3 五条 | C8 属实；C11.2 证伪、C11.1/3/4/5 属实 | ⚠️ 部分属实 | C8 判据 6 → Q6；其余见 §1.1 |

### 1.2 `harness-w67-review.md`

| 来源编号 | 一句话主张 | 回树核实 | 判定 | 处置 | 理由 |
|---|---|---|---|---|---|
| §一 T1 W5-01 三层修补 | W6 换量纲落地且更彻底 | `PreStepBudget.kt:88-127` 字符闸 + token 闸 + `trip`；`TurnLoop.kt:60` 挂轮头 | ✅ 属实 | 不做 | 已交付 |
| §一 T2 W5-02 扣减+手填 | 400 写回四跳全接 | `ContextLimitWriteback.kt`、`AppContainer.recordContextLimit`、`OpenAiCompatibleAdapter.kt:159-164 contextLimitHint`、台账 `:79` 六条 JVM 用例 | ✅ 属实 | 不做 | 已交付（但台账状态仍 OPEN ⇒ Q4） |
| §一 T3 挂账总表+G20 | 42 条全量回填 + G20 钉住 + 查出 26% 丢失 | 台账 `:56-103` 42 行；`arch-guard.sh:1300` G20；`:13-19` 26% 自述 | ✅ 属实 | 不做 | 已交付 |
| §一 T4 AttemptObserver 取证缝 | 仍未做：core-engine 零日志、G19 VACUOUS | E2 G19 VACUOUS；`grep -rn "AttemptObserver" --include=*.kt .` 仅 `GenerationRound.kt:292` **KDoc 引用**（无组件无实现）；台账 42 条**无独立条目**（`grep -n AttemptObserver docs/06-deferred-ledger.md` = 0） | ✅ 属实 | **挂账（H7）** | 见 §四；本波 ⑦ 若给 core-engine 接诊断日志会**顺带**让 G19 脱 VACUOUS，须先按 G19 规则只打键名/header 名（§六） |
| §一 T5 T4 estimate/usage 对账 | 升级为 `RunState.recordReportedPrompt` 标定闭环 | `RunState.kt:257-268` `measuredCharsPerToken = charsSent/prompt`；`PreStepBudget.kt:90` 消费 | ✅ 属实 | 不做 | 已交付 |
| §一 T6 T7 schema 白名单守卫测试 | 仍未钉：`OpenAiCompatibleAdapterTest` 无逐键断言 | `OpenAiCompatibleAdapter.kt:319-329 toolObject` 白名单构造；`grep -nE "白名单\|只含\|jsonObject.keys" OpenAiCompatibleAdapterTest.kt` = 0（`:108-110` 只断言 `type=function` 与读 `function` 对象） | ✅ 属实 | **挂账（随 W8-02/H4）** | 功能波 ⑦ 会改 adapter 请求体 ⇒ **这是本波最该顺手补的钉子**，但补单测会 +@Test ⇒ 必须同提交改 `EXPECTED_TESTS`（§六）。建议并入 ⑦ 的 adapter 改动单测里（§三 Q11 备选） |
| §一 T7 Σcaps ≤ TOTAL 自检 | 未做且缺口扩大 | **F3：Σ=46000 非 43400**；`grep -nE "Σ\|sum.*cap\|自检" arch-guard.sh` = 0（无自检） | ⚠️ 部分属实（**数值证伪**，缺口存在且更大） | **挂账** | 二选一裁决（加自检 / 显式解耦）属口径决策，需写进 `w7-line-budget-ruling.md`，本波不动 `LINE_CAPS` |
| §一 T8 RunnerKey W7 联动 | `RunnerKey(endpoint, windowCap, compress)` 正确进键 | `RunnerCache.kt:21-32` RunnerKey 含整个 `RemoteEndpoint` + `windowCap` + `compress`；`:73/:106` `ConcurrentHashMap.compute`；`:104 removeIfIdle` | ✅ 属实 | 不做 | 已交付。**注意**：功能波给 `RemoteEndpoint` 加 10+ 字段 ⇒ RunnerKey 自动包含 ⇒ 改高级配置即重建 runner（符合预期，但 ⑥ 矩阵测试要留意「配置一变缓存前缀重建」） |
| §二 亮点 1 | L3 字符判据 KDoc「用 token 写断言永远绿」 | `PreStepBudget.kt` 类头 + `BudgetFormula.kt:51-57` | ✅ 属实 | 不做 | 点名防误改 |
| §二 亮点 2 | SwitchGuard 零自写算式 | `SwitchGuard.kt:78-80` 直调 `hardContextBudgetTokens`/`charCapOf` | ✅ 属实 | 不做 | 防误改：**功能波不要给 SwitchGuard 加第二把尺** |
| §二 亮点 3 | A5 真机重放一次收三条 | `handoff-20261104-wave7.md:82/86` | ✅ 属实 | 不做 | 但台账未回填 ⇒ Q4 |
| §二 亮点 4 | 「读错对象 vs 读错含义」分档，8 条占位已删 | E3 `assertTrue(true)` = 0 | ✅ 属实 | 不做 | 防误改 |
| §二 亮点 5 | 行数裁决诚实声明调高上限 | `docs/notes/w7-line-budget-ruling.md:53-62`；`docs/02-roadmap.md:7` | ✅ 属实 | 不做 | 防误改：本波触顶**先拆不调上限** |
| **§三 P0** | `SettledCompressPolicy` 触发线档位解耦：1M 档「过度压缩灭缓存」结构性风险 | `SettledCompressPolicy.kt:46` `50_227`（`:41` 自述 32K 档导出）、`:75-80 shouldApply` **无 target/window 参数**、`:64-65 shouldEvaluate` 只看事件；`ConversationOps.kt:108` 试算走 `contextBudgetTokens` 软预算 | ✅ 属实 | **挂账（H1，与 C3/P0-2 合并）** | 与 C3 面1/2、W8深度审查 P0-2 是**同一缺陷的三份表述**，下一波合并成一件，勿拆三条 |
| **§三 P1-a** | 标定滞后窗：400 自愈闭环（CONTEXT_OVERFLOW 从 Fatal 改收缩重试） | `RetryPolicy.kt:50-64` 明确 `CONTEXT_OVERFLOW` **不重试**（`:64` 列入黑名单）；`RemoteLlmEngine.kt:184 AttemptOutcome.Fatal`、`:211`、`:339`、`:342 contextLimitHint` 已解析；`HardBudgetShrinker` 已上线；`verifiedContextLength` 写回已上线。**三者未在失败路径串起来** | ✅ 属实 | **挂账（H8）** | 需改引擎失败路径 + 新单测，>30 行且碰 `core-engine/RemoteLlmEngine.kt`（功能波 ⑦ 可能碰同文件）⇒ 不同波 |
| **§三 P1-b** | 台账状态滞后：W5-01/W7-01/02/03 真机证据未回填；`_w7/device-acceptance.md` 不在仓 | 台账 `:78/:93/:94/:95` 全 `OPEN`；`find . -name device-acceptance.md` = 空；handoff `:82-84` 四态表在仓内、**驱动记录在仓外**（`:74`） | ✅ 属实（**F7 订正**：W7-02 不得 CLOSED） | **同波收 Q4** | 纯文档、零代码冲突；注意 0 run 陷阱 + N1 统计表自相矛盾 |
| **§三 P2-1** | schema 白名单守卫测试二次落空 | 同 §一 T6 | ✅ 属实 | 挂账 / Q11 备选 | 同上 |
| **§三 P2-2** | Σcaps 口径二选一裁决 | F3 | ⚠️ 部分属实 | 挂账 | 同上 |
| **§三 P2-3** | AttemptObserver 取证缝第三次落空，无动作条目 | 同 §一 T4 | ✅ 属实 | 挂账（H7） | 同上 |
| §三 观察 1 | SwitchGuard floor 误拦是安全侧，可接受不修 | `SwitchGuard.kt:85-87` floor/ceiling 二档 + `SettingsViewModel.kt:285-288` KDoc「刻意的」 | ✅ 属实 | 不做 | 与 C3 面3 冲突：C3 要修，观察项说不修 ⇒ **下一波 H1 内一并裁决**，本波不动 |
| §三 观察 2 | SessionFootprintStore 重启窗口窄，可接受 | `SessionFootprintStore.kt:25-26` | ✅ 属实 | 不做 | 同 C10.c |
| §三 观察 3 | ManualCompress 在 UI 层是权宜，W8-01 设计写清「压缩逻辑最终住址」 | `ManualCompress.kt:27-40` KDoc + `ConversationOps.kt:139-147` | ✅ 属实 | 挂账（写进 H3 方案） | 设计文档动作，非代码 |
| §三 观察 4 | A3 BLOCKED 真机未行使，可用导入大会话触发 | `handoff-20261104-wave7.md:81` A3 = ⚠️ 部分；`SwitchGuardTest` 10 条 JVM | ✅ 属实 | 挂账（随 H1 真机） | 本波 ⑥ 矩阵不做 BLOCKED 注入 |
| §三 观察 5 | W6-06 / W6-08 登记规范，无需动作 | 台账 `:90/:92` | ✅ 属实 | 不做（**但 NEW-I 要求 W6-08 的「可见性断言」落地**，见 §1.3） | 两份外审对 W6-08 处置不一致，按 NEW-I 取「顺手做可见性」 |
| §四.1 | 台账体系是本轮最大净产出（42 条/三值/G20/G21/唯一性/CI 单人） | 台账 + `arch-guard.sh` | ✅ 属实 | 不做 | — |
| §四.2 | 状态核对 CLOSED 11 / CARRIED 7 / OPEN 24，抽查 8 条证据可指到文件:行 | **N1：实际逐行 CLOSED 12 / CARRIED 7 / OPEN 23**；§二 统计表写 11/7/24 | ⚠️ 部分属实（**统计表错**） | **同波收 Q4**（修统计表） | 外审照抄了台账的错统计表，未逐行重数 |
| §四.3 | 两个体系级残留：进度回填滞后 + W6-03 减法路径无动作条目 | 台账 `:87` W6-03 只记现象；`:78/:93/:94` 滞后 | ✅ 属实 | Q4 + H7 | — |
| §四.4 | 老化三条归宿：W3-05 独立波应排 W8 主位；W3-02/W3-07 二选一显式裁决 | 台账 `:137-143` 老化三条；`:64` W3-02 `onImport = null`（实际 `SettingsRoute.kt:225`，行号漂）；`:69` W3-07 OPEN | ✅ 属实 | **W3-07 由功能波 ① 顺手销账**；W3-02 挂账；W3-05 挂账 H5 | W3-07 = supportsVision，正是 ① 的「图片输入」配置项 ⇒ **本波可 CLOSED**（§二） |
| §五 主线 A 1–4 | W8 主线 A 正确性收尾 1-2 天：台账回填 / P0 档位解耦 / 400 闭环 / W8-03 二选一 | Q4 属实；P0/H8/H6 见上 | ✅ 属实（技术） | **Q4 同波收**，其余挂账 | **路线级证伪**：用户本波是功能波，不是正确性收尾波；优先级整体后移 |
| §五 主线 B | W3-05 journal 独立波 + AttemptObserver 取证缝并入 | 同 C2/H5/H7 | ✅ 属实 | 挂账 H5/H7 | 同上 |
| §五 支线 1–4 | W8-02 三桶归一前补 schema 白名单钉子 / W7-03 收尾 / W5-03 裁决 / Σcaps 二选一 | 台账 `:101` W8-02；`:95` W7-03；`:80` W5-03；F3 | ✅ 属实 | 挂账 H4 / H8 / — / — | W8-02 与功能波 ⑦ 都改 adapter ⇒ 冲突 |
| §五 验收 1 | 1M 档 100+ 轮 ⇒ `auto_compact=noop reason=below_trigger` | 现役 `SettledCompressPolicy.kt:82` `committedChars <= 50_227` ⇒ `below_trigger` | ✅ 属实（判据可跑） | 挂账（随 H1） | 需真机 |
| §五 验收 2 | 注入 400 CONTEXT_OVERFLOW ⇒ 收缩重试成功 | 现役无此能力（H8） | ✅ 属实（缺位） | 挂账 H8 | 需假 server |
| §五 验收 3 | 台账 W5-01/W7-01/W7-02 CLOSED 且证据列含 A5 数字 | **F7：W7-02 不得 CLOSED** | ⚠️ 部分属实 | Q4 按 F7 订正 | 见 F7 |
| §五 验收 4 | G19 摘掉 VACUOUS | E2 G19 VACUOUS | ✅ 属实（缺位） | 挂账 H7 | 本波 ⑦ 若接日志可顺带，但需按 G19 规则 |
| §五 验收 5 | journal 杀进程重启 / FreezeClock 灭屏不误熔 | 全仓零实现 | ✅ 属实（缺位） | 挂账 H5 | 需真机 |
| §五 验收 6 | `@Test` 只增不减（**696 → ≥740**）；守卫 28 条全绿 | E3 = 696；E2 OK=27（+1 VACUOUS = 28 守卫输出）；E5 selftest 28 case | ✅ 属实（基线） | **本波改为 696 → 696+N**（N = 本波新增 @Test 数，须同提交对账）；**不承诺 ≥740** | 功能波新增测试数由实现决定，「≥740」是外审给正确性波设的目标，不适用 |

### 1.3 `FastestAI-LiquidAgent-harness_W2.6-W7深度审查与W8路线修补建议_2026-11-05.md`

#### 1.3a §二 第一波问题复核 1–9

| # | 主张 | 回树核实 | 判定 | 处置 |
|---|---|---|---|---|
| 1 | `AppContainer` getOrPut 竞态已修（`ConcurrentHashMap.compute`） | `RunnerCache.kt:73/:106` `entries.compute`；`getOrBuild` @ `:68`、`removeIfIdle` @ `:104` | ✅ 属实 | 不做 |
| 2 | `AgentLogStore.dropOneFor` 改进（先找 INFO victim，无 INFO 且来者 INFO 返回 false） | `AgentLogStore.kt:62-64/:89-99`（`:98 if (incoming.level == Level.INFO) return false`） | ✅ 属实 | 不做 |
| 3 | M2 tool schema token 不入预算已修 | `PreStepBudget.kt:96` `toolTokens = TokenEstimator.estimateToolSpecs(...)`、`:98` token 闸含 | ✅ 属实（**仅 token 闸**；C4 的字符闸仍漏 ⇒ 不矛盾） | 不做（C4 另挂账） |
| 4 | M3 前缀指纹已实现并接线 | `PromptPrefix.kt:113 fingerprintOf`；`ContextBudget.kt:128/137` `PrefixFingerprint`/`lastPrefixFingerprint`；`ContextBudgetTest.kt:124-204` | ✅ 属实 | 不做 |
| 5 | W26-04/I1 跨轮 history 丢工具轨迹已修 | `ChatHistoryMapper.kt` + `ChatViewModel.kt:280` `compressedUpTo` | ✅ 属实 | 不做 |
| 6 | W5-01 轮头单点 `preStep.enforce` 结构落地，真机判据未验 | `TurnLoop.kt:60`；台账 `:93` W7-01 `OPEN`（但 handoff A5 ✅ 已给数字 ⇒ **Q4 可 CLOSED**） | ⚠️ 部分属实（真机证据已在 handoff，只是台账未回填） | Q4 |
| 7 | 断流误标 STOP **未修** | `ProviderAdapter.kt:189-194 terminationReason`；`:192 if (state.textChars == 0) return FinishReason.STOP`（全树唯一命中） | ✅ 属实 | **挂账（H9，可与 NEW-E 合并）** |
| 8 | supportsVision 硬编码未修 | E7；`RemoteEndpoint.kt` 无字段；W3-07 `OPEN` | ✅ 属实 | **同波收（功能波 ① 主体，见 §二）** |
| 9 | probe saved-secret fallback 部分验证 | `EndpointProbe.kt:52` `SecretProvider { if (apiKey.isNullOrBlank()) null else apiKey }`；`SettingsViewModel.kt` 传参链存在 | ✅ 属实（外审自标 [部分验证]） | 挂账（低优先，功能波 ① 会改 `EndpointEditScreen` 可顺带核） |

#### 1.3b §三 W7 新代码审查要点 3.1–3.6

| # | 主张 | 回树核实 | 判定 | 处置 |
|---|---|---|---|---|
| 3.1 | 自动压缩链三点正确（只 Finished&&ModelStopped / 不判 isStreaming / 唯一调用点 G23） | `SettledCompressPolicy.kt:64-65`；`ConversationOps.kt:98-101` KDoc 记 W7 真机实摔；`ChatViewModel.kt:394` 唯一调用（E2 G23=1） | ✅ 属实 | 不做（防误改：功能波不得新增 `.autoCompactOnSettled(` 调用点） |
| 3.2 | `evaluateSwitch` 三处赋值形态被 G22 钉住 | **F5：实为 2 赋值 + 1 间接** | ⛔ 证伪（数字） | 不做（W8深度审查 §2 的「2 处」为准） |
| 3.3 | 双闸 + `[omitted: hard budget]` 幂等 + 缩不完 trip；字符闸不含 tool schema | `PreStepBudget.kt:95-98/:122-127`；`HardBudgetShrinker` 占位符；推断项与 C4 一致 | ✅ 属实 | 挂账（C4/H2） |
| 3.4 | MCP 传输复用 `HttpTransport` ⇒ UrlGuard+GuardedDns 生效；`McpSchema.kt:99` 远端工具一律 `requiresConfirmation=true`；`McpServerStore.kt:13-23` token 未进 Keystore | 文件均在；台账 `:66` W3-04 `OPEN` | ✅ 属实 | 挂账（W3-04/H7） |
| 3.5 | `FileMemoryStore` normalizeTitle 仅去重键；`SkillImportValidator` 无入口（W3-02，`SettingsRoute.kt:206 onImport=null`）；附件上限 4 | `SettingsRoute.kt:225` `onImport = null`（**行号漂 206→225**）；`ChatViewModel.kt:485 MAX_ATTACHMENTS=4`；`SkillImportValidator` 全仓仅 KDoc 引用 | ✅ 属实（行号订正） | 挂账（W3-02） |
| 3.6 | `VACUOUS_BASELINE=1`、G22/G23 就位、release.yml 四 secret；696/27/1/30/35233/2900 为 [文档声明未复跑] | E2 = 27/1/0/0 ✅；E3 = 696 ✅；**E5 = 28 case 非 30**（F4）；**E4 Σcaps=46000 非 2900**（F3）；全仓 main 逐模块求和 = 35 695（E6），非 35 233（台账 `:91` 旧值） | ⚠️ 部分属实（外审自标未复跑，本席已复跑并订正 3 个数字） | 不做（数字以 E2–E6 为准） |

#### 1.3c §四 NEW-A ~ NEW-I（本轮增量，主编号条目）

| 编号 | 一句话主张 | 回树核实 | 判定 | 处置 | 理由 |
|---|---|---|---|---|---|
| **NEW-A** | 会话自动压缩每对话至多生效一次：`alreadyCompressed = snapshot.compressedUpTo != null`，首次压缩后永久 noop | `ConversationOps.kt:118` `snapshot.compressedUpTo != null`；`SettledCompressPolicy.kt:81` `if (alreadyCompressed) return CompactDecision(false, "already_compressed")`；`ChatUiState.kt:208` 单指针；`ManualCompress.kt:87-121` 同一指针。**订正**：`ConversationOps.kt:209-214 undoCompress()` 会把指针清回 null，故非「永久」，但自动路径自身确无第二次 | ✅ 属实（措辞微调） | **挂账（H1/W8-01 尾部摘要自然覆盖）** | 与 C3/P0-3 同源；「不要半迁移」正确，功能波不动 |
| **NEW-B** | `committedChars <= 50_227` ⇒ 恰好 50 227 不触发，与台账「触发体积」差一个边界 | `SettledCompressPolicy.kt:82` `if (committedChars <= COMPACT_TRIGGER_CHARS) return … "below_trigger"` | ✅ 属实 | **挂账（随 H1，注释/`<` 一并裁决）** | 1 行注释但属 H1 同一文件且 H1 会重写触发线，避免半迁移 |
| **NEW-C** | `50_227`/`460` 是 32K 档导出值，档位变化不缩放 | `SettledCompressPolicy.kt:41-44/:46/:49` 自述「32K 档 floor 字符 cap `(32 000 − 4 096) × 1.8`」「256 token × floor 1.8」 | ✅ 属实 | **挂账（H1）** | 与 W67 P0 / P0-2 / C3 面1/2 同一缺陷 |
| **NEW-D** | `supportsVision=false` + UI 允许挂图 = 用户可见性诚实问题；最小改法 = 端点加字段 + 设置页开关 + 未启用禁用图片入口 | E7（3 命中，端点无字段）；`EndpointEditScreen.kt` 零高级配置（E8）；`AppNavHost.kt:156/170` + `ChatInputBar.kt:72` 图片入口**已接线且无 capability 闸门**；`OpenAiCompatibleAdapter.kt:292-310` **实际在发 image_url part**；`ChatViewModel.kt:485 MAX_ATTACHMENTS=4` | ✅ 属实，**且比外审更严重**：不是「可见性」而是「能力声明与实现打架」 | **同波收 = 功能波 ① 主体**（见 §二 R1） | 用户 ① 明列「图片输入」高级配置 ⇒ 本波必须做；须同时销台账 W3-07（`:69`） |
| **NEW-E** | 断流误标 STOP：空正文 + 无 [DONE] + 无工具碎片 ⇒ 应 `stream_incomplete` 走 RetryPolicy/ERROR | `ProviderAdapter.kt:189-194`（§二#7 同源）；`sawTerminalMarker` @ `:191`；`textChars == 0` @ `:192`；KDoc `:7` 自述「空回答是『模型不想说』」 | ✅ 属实 | **挂账（H9）**，或 **Q12 备选同波** | 3 行 + 1 假引擎单测 ∈ ≤30 行、文件 `core-engine/provider/ProviderAdapter.kt` 与功能波 ⑦ 改的 `OpenAiCompatibleAdapter.kt` 不同文件；但会改 `finish_reason` 语义，与 ③ JSON 检查/⑥ 矩阵结果耦合 ⇒ 建议挂账，避免功能波判据被污染 |
| **NEW-F** | journal/FreezeClock 欠五波 + 五处注释引用不存在机制主动误导 | **F1：注释已改写为「尚未落地，挂账 W3-05」**；`find` 零实现 ✅ 属实 | ⛔ **证伪（注释/改注释处方）**；journal 缺位 ✅ 属实 | **不做改注释**；journal 挂账 H5 | 见 F1；台账 `:67` 证据列仍写「注释写的是一个不存在的机制」⇒ Q4 回填时顺手订正（N5） |
| **NEW-G** | 「声明了没消费者」家族 ≥5，建议 W8 死字段专项清剿 + G24 候选守卫 | `autoApproveDangerous` 仅 `AgentPolicy.kt:115` + `SettingsRepository.kt:75` KDoc；`capabilities()` 仅 `LlmEngine.kt:47`/`RemoteEngineBridge.kt:33`/`RemoteLlmEngine.kt:89`（KDoc `:42`）；`allowClipboardRead` 有 `ClipboardTool.kt:78` 真消费但无 UI（F9）；`SkillImportValidator` 无入口；`SettingsRoute.kt:225 onImport=null` | ⚠️ 部分属实（F9 措辞） | **挂账（H6 二选一 + W3-02）** | 清剿会碰 `core-model/GenerationEvent.kt`（`EngineCapabilities` 定义处）+ `core-engine/RemoteLlmEngine.kt` —— **正是功能波要改的文件** ⇒ 不同波 |
| **NEW-H** | W26-02（T20 `no_tool` 容忍只读探查）未裁决：夹具 v2、T20 no_tool、scorer 已支持 mode 分派、19/20 唯一失败 T20 over_called | `scripts/fixtures/w2-tool-prompts.json:66` `"version": 2`；`:223` T20 `"mode": "no_tool"`；`score_w2_stoploss.py:207-210` `mode = expect.get("mode")`；`docs/notes/w26-run-report.md:13` 19/20、`:58` T20 `over_called` | ✅ 属实（行号精确命中） | **挂账** | 成本 ≈半天但需**原样重跑 20 题真机/LLM**，与本波 ⑥ 免费节点矩阵**争测试机时与免费额度**；且改夹具版本须登记 v3。不做「顺手」 |
| **NEW-I** | G10 尾换行「可见性断言」未落地（W6-08 承诺），一行 shell | `grep -nE "缺尾换行\|尾换行\|NO_EOL\|eol" scripts/arch-guard.sh` = **0 命中**；台账 `:92` W6-08 `OPEN`「下一波做可见性断言（G10 顺带输出 `缺尾换行=N`，不判红）」 | ✅ 属实 | **同波收（Q13，可选）** | 1 行 shell、只加输出不判红 ⇒ **不构成新守卫，无需 selftest case**；不碰功能波文件域。须与 Q4 一样搭代码提交车 |

#### 1.3d §五 W8 路线 1–12 + 明确不做 5 条

| # | 主张 | 回树核实 | 判定 | 处置 |
|---|---|---|---|---|
| P0-1 | 真机验收包四合一（W7-01/02/03/05），须先换 NVIDIA 端点 | `handoff-20261104-wave7.md:82-84` A5/A6/A7 已有部分结果；台账 `:93-95/:97` 仍 OPEN | ⚠️ 部分属实（A5 已取数） | Q4 回填 + 挂账（H1/H3 真机复放）；本波 ⑥ 矩阵优先功能验收 |
| P0-2 | W3-05 journal 开波 + 把 5 处误导性注释同步兑现或改写 | **F1 注释已改写**；journal 零实现 | ⛔ 部分证伪 / 缺位属实 | 挂账 H5（**去掉「改注释」交付物**） |
| P0-3 | W8-01 压缩格式切换，同时收 NEW-A/NEW-C | 台账 `:100` W8-01 `OPEN` | ✅ 属实 | 挂账 H3 |
| P1-4 | 断流 STOP 修复（NEW-E） | 同 NEW-E | ✅ 属实 | H9 / Q12 |
| P1-5 | supportsVision 字段 + 禁用图片入口 + probe 传参断言 | 同 NEW-D | ✅ 属实 | **功能波 ① 主体**（§二 R1） |
| P1-6 | MCP token 进 Keystore 复用 `EndpointSecretStore`，顺带 G19 脱 VACUOUS | 台账 `:66` W3-04；E2 G19 VACUOUS | ✅ 属实 | 挂账 H7（与 AttemptObserver 合并） |
| P1-7 | 死字段清剿专项 + G24 候选守卫评估 | 同 NEW-G | ✅ 属实 | 挂账 H6 |
| P1-8 | W26-02 裁决 + 夹具 v3 重跑 | 同 NEW-H | ✅ 属实 | 挂账 |
| P2-9 | G10 尾换行可见性断言 | 同 NEW-I | ✅ 属实 | Q13 |
| P2-10 | char 闸并入 tool specs 字符常数项 | 同 C4 | ✅ 属实 | 挂账 H2 |
| P2-11 | W7-08 文档勘误 + W7-10 发布门禁三项裁决 | `docs/` 中这两编号未见独立条目（台账仅到 W7-07） | ⚠️ 部分属实（编号在台账中不存在） | 挂账 / 不做（发布门禁不属于功能波） |
| P2-12 | 触发判据 `<` 边界注释（NEW-B） | 同 NEW-B | ✅ 属实 | 挂账 H1 |
| 不做 1 | 不接 `EngineCapabilities` 当窗口上限通道 | `docs/notes/w6-plan.md §2.5b` 裁决；台账 `:86` W6-02 | ✅ 属实 | **不做（背书）**；功能波 ① 若加「输入/输出」字段，别复用 `EngineCapabilities` 当第二把尺 |
| 不做 2 | 不为凑缓存加长 system | `handoff-20261104-wave7.md:91`、台账 `:94` | ✅ 属实 | 不做（背书） |
| 不做 3 | 不开 `usesCleartextTraffic` 迁就 MCP | `app/src/main/AndroidManifest.xml:25`；台账 `:80` W5-03 | ✅ 属实 | 不做（背书） |
| 不做 4 | 不动 `TOTAL_LINE_CAP=42500` | `arch-guard.sh:204` | ✅ 属实 | **不做（背书，写入本波硬约束）** |
| 不做 5 | 不在 W8-01 前做 W8-02 缓存归因日志 | 台账 `:101` | ✅ 属实 | 不做（背书） |

### 1.4 `W8深度审查与下一波修补建议.md`

#### 1.4a §0/§2 交付核对（8 行）

| 项 | 回树核实 | 判定 | 处置 |
|---|---|---|---|
| 模块 13→14，`settings.gradle.kts` include=14，G9 三方一致 | E2 G9 通过（E2 汇总 OK=27）；`settings.gradle.kts` include 14 | ✅ 属实 | 不做 |
| `:core-context` 8 文件 1584/1650；`core-kernel` 403/450 | E6 精确一致 | ✅ 属实 | 不做（**功能波注意**：core-context 余 66 行） |
| 六档窗口 32K/128K/192K/256K/512K/1M | `SettingsScreen.kt:292`（外审） | ✅ 属实 | 不做 |
| `SwitchGuard` 被调用：save `:117` + guardSwitch `:292` 两处赋值；列表预计算 `SwitchDecision.kt:85`；G22=2 | E2 G22=2；`SettingsViewModel.kt:117/:292`；`:237` → `SwitchDecision.kt:85` | ✅ 属实（**比 W2.6–W7 §3.2 准确**） | 不做 |
| `autoCompactOnSettled` 唯一调用点 `ChatViewModel.kt:394`，G23=1 | E2 G23=1 | ✅ 属实 | 不做 |
| 会话体积单一写者 `SessionFootprint.kt:67-76` | `SessionFootprint.kt:28-38 observeSessionFootprint` 唯一写者订阅；`SessionFootprintStore.kt:30` KDoc | ✅ 属实（行号漂 67→28） | 不做 |
| 新增 22 条策略单测（SwitchGuardTest 10 + SettledCompressPolicyTest 12，全纯函数） | `grep -cE '^\s*@Test\b'` → 10 / 12 | ✅ 属实 | 不做 |
| **自动压缩链路集成测试 0 条**：`feature-chat/src/test` 8 个文件无一覆盖 | `grep -rln "autoCompactOnSettled" feature-chat/src/test` = 空；`find feature-chat/src/test -name '*.kt' \| wc -l` = **7**（非 8） | ✅ 属实（文件数订正 8→7） | 挂账（随 H1 补集成用例，见 §六 EXPECTED_TESTS） |
| §0 结论「结构面是真的，机制面还没被真机行使」/ 四态表 A3⚠️ A4✅ A6⚠️ A7⛔vacuous | `handoff-20261104-wave7.md:79-84` 逐行一致 | ✅ 属实 | **Q4 按四态回填台账** |

#### 1.4b §3 P0-1 ~ P0-5

| 编号 | 一句话主张 | 回树核实 | 判定 | 处置 | 理由 |
|---|---|---|---|---|---|
| **P0-1** | 自动压缩取「定稿前」快照：`ChatViewModel.kt:394` 分流先于 `:395 onEvent`，`EventReducer.finish :199-232` 才追加 MODEL 气泡 | `ChatViewModel.kt:394` `if (shouldEvaluate(event)) ops.autoCompactOnSettled()` / `:395 onEvent(event, generation)`；`EventReducer.kt:62` `is Finished -> finish(...)`；`EventReducer.kt:199-232 finish` 追加气泡；`ConversationOps.kt:97` `val snapshot = state.value`（同步取）、`:103 scope.launch`。**行号精确命中** | ✅ 属实 | **挂账（H1）** | 需改 collect 顺序 + 补 JVM 用例（喂 Finished 断言 bubbles.size+1），>30 行且碰 `ChatViewModel.kt`（487/500，余 13 行）⇒ 与功能波 ② 的 `feature-chat` 改动抢行数 |
| **P0-2** | 触发线与试算两把尺，`applied=true` 近乎不可达；两处 `256` 混淆 | `SettledCompressPolicy.kt:46/:75-87` 字符判据；`ConversationOps.kt:108` `contextBudgetTokens` token 软预算；`ManualCompress.kt:99-112` `TokenEstimator.estimateMessages`；`AUTO_COMPACT_COST_CHARS=256`（`ConversationOps.kt:24`，**字符**）vs `w7-context-cache-roadmap.md:124 minSavings=256`（**token**）——**两个 256 确实并存** | ✅ 属实 | **挂账（H1，与 C3/W67 P0/NEW-C 合并成一件）** | 四份外审同一缺陷，下一波**只开一个条目**（H1），勿重复立账 |
| **P0-3** | 单指针 + `alreadyCompressed`：长会话只能压一次 | `SettledCompressPolicy.kt:81`；`ChatUiState.kt:208`；`ChatHistoryMapper.kt:93-98`；`ManualCompress.kt:87-121` | ✅ 属实 | **挂账（H3 = 台账 W8-01）** | 与 NEW-A 同源；「尾部追加结构化摘要」是形态重构，会碰 `feature-chat` 多文件 + `core-context/StructuredSummary.kt` ⇒ 与功能波 ② 冲突 |
| **P0-4** | 切换守卫只量 history 漏静态前缀；标定档永不生效（`0f` 恒 floor） | `SessionFootprint.kt:46-58` 只 sum bubbles；`SwitchGuard.kt:85` `committedChars > cap` **无 staticChars 减法**；`SettingsViewModel.kt:117/:292`、`SwitchDecision.kt:85` 全传 `0f`；`SwitchGuard.kt:104` `verifiedContextLength == null ⇒ VACUOUS`（实际在 `:104-106`，行号微漂）；`RunState.kt:263-268` measured 现值 | ✅ 属实 | **挂账（H1 子项）** | 需 `staticChars` 单一真值源 + 会话级 measured 扩列 + 交叉用例，重型；C3 D9 四边界已论证消费边界（D9② 实跑 `sessionFootprint` 在 `core-agent/src/main` = 0 命中，边界成立） |
| **P0-5** | 切换守卫有绕路：`delete(id)` 无守卫、`AppContainer.deleteEndpoint` 自动切活跃不过 evaluateSwitch；settled 评估现读新端点 | `SettingsViewModel.kt:148-153 delete` 无守卫；`AppContainer.kt:388-395` `:393 settings.setActiveEndpointId(remaining.firstOrNull()?.id)`；`SettingsViewModel.kt:115` save 条件；`ChatViewModel.kt:329/334` run 开始捕获 endpoint/runner；`ConversationOps.kt:105 container.activeEndpoint()` + `:106` 现读 `maxContextTokens` | ✅ 属实 | **挂账（H1 子项）** | 需「唯一写口 `applyActiveSwitch`」+ run 级快照传递 + 3 条用例，重型且碰 `core-data/AppContainer.kt` + `feature-settings/SettingsViewModel.kt` |

#### 1.4c §3 P1-1 ~ P1-6

| 编号 | 一句话主张 | 回树核实 | 判定 | 处置 | 理由 |
|---|---|---|---|---|---|
| **P1-1** | 自动压缩协程无会话代次守卫（与 `flushLoop` 同型竞态） | `ConversationOps.kt:97 snapshot` → `:103 scope.launch` → `:130-135 state.update`，**无 generation 校验**；`ChatViewModel.kt:235-258 newConversation` 用 `runGeneration` 挡迟到回调；`collect` 的 `if (generation == runGeneration)`（`:387`）只护住分流那一刻，`autoCompactOnSettled()` 内部 launch 在其外 | ✅ 属实 | **挂账（H1）** | 注入 `generation: () -> Int` + JVM 用例，>30 行且同 `ConversationOps.kt`（Q1 会改该文件 ⇒ 须串行单人落笔） |
| **P1-2** | G22/G23 是形态守卫，W8 重构必然假红且 G22 覆盖粒度不足；selftest 三处变异需补 | `arch-guard.sh:1449-1464` G22 仅要求 `val x = evaluateSwitch(` **≥1**；`:1471-1491` G23 要求 `.autoCompactOnSettled(` **恰好 1** + `shouldEvaluate(` ≥1；`arch-guard-selftest.sh:506-516 m_g22/m_g23` 变异均为「**删掉全部**调用行」；`VACUOUS_BASELINE=1` @ `:256` | ✅ 属实 | **挂账（H1 开波第一件事）**，但**本波必须重跑 G22/G23 + m_g22/m_g23**（§六） | 功能波若改 `SettingsViewModel.kt`/`ChatViewModel.kt` 形态，G23 会立刻判红而 G21 棘轮把合法重构一起卡住 ⇒ 本波方案中钉死「不新增 `.autoCompactOnSettled(` 调用点、不改 `val x = evaluateSwitch(` 形态」 |
| **P1-3** | W8 编号在台账与 `w7-context-cache-roadmap.md` 同名不同义 | **见 §五**：`docs/06-deferred-ledger.md:100-103` vs `docs/notes/w7-context-cache-roadmap.md:64/65/91/99/106/107/108`；`handoff-20261104-wave7.md:5` 确标「路线草稿，**历史输入**」；`:172` 自称「下一波（W7/W8）交接文档」；G20 扫描面不含 `docs/notes/`（N4） | ✅ 属实（**且 W8-05 也撞**，N3） | **同波收（Q14：文档改名/加唯一编号源声明）** | 纯文档、零代码冲突、可复跑；本波开波前必须做，否则派单必错 |
| **P1-4** | roadmap 总览停在 W4；台账 42 行 OPEN 24 条、W7-01/02/03/05/06 未回填 | `docs/02-roadmap.md:11/19-21/697`；台账 `:93-95/:97/:98` OPEN | ✅ 属实（**OPEN 实数 23 非 24**，N1） | **同波收 Q4**（含 N1 订正） | 同 C9 |
| **P1-5** | 安全收口连续被挤掉，G19 长期 VACUOUS；R21 三条剪贴板断言 / W3-04 / W6-05 / W5-05 / X5 | E2 G19 VACUOUS；`docs/04-risk-register.md:345`「W7：**40 条脱敏断言**…至少 **3 条针对剪贴板**」；`grep -rnE "clipboard.*redact\|redact.*clipboard\|shown.*clipboard" --include=*.kt */src/test` = **0 命中**（三条断言未落地）；台账 `:66/:89/:82` OPEN | ✅ 属实 | **挂账（H7 安全收口小波）** | 需独立波；本波 ⑦ 若给 `core-engine` 接日志，**只能打键名/header 名**（G19 规则 :1208-1228），不得拼值，否则 G19 从 VACUOUS 转真红 |
| **P1-6** | `confirmSwitch` 的 SET_WINDOW 分支不 `load()`，`blockedIds` 不刷新；`refreshEndpoint` 只在 `LaunchedEffect(Unit)` | `SettingsViewModel.kt:179-184` SET_WINDOW 只写 settings + `_state.update`（无 `load()`）；`:237 blockedIds = blockedEndpointIds(...)` 只在 `load()`；`AppNavHost.kt:142 LaunchedEffect(Unit) { viewModel.refreshEndpoint() }` | ✅ 属实 | **挂账** | 1 行 `load()` + 1 用例，但碰 `SettingsViewModel.kt`；不属用户功能域，按「同波只收重合项」不收 |

#### 1.4d §3 P2-1 ~ P2-4

| # | 主张 | 回树核实 | 判定 | 处置 |
|---|---|---|---|---|
| **P2-1** | noop 落账级别不一致：内存 `warn`、落盘 `INFO`；`offerContextLimit` 与 `persist` 各自 launch 顺序无保证 | `ConversationOps.kt:122 AgentLogStore.warn` vs `:232 container.logs.append("INFO", line)`；`:66 offerContextLimit` 与 `:230 persist` 各 `scope.launch` | ✅ 属实 | **Q5/Q3 同波可选**（级别 1 行）；「合并串行账」属重构 ⇒ 挂账 H1 |
| **P2-2** | 行数余量已被 W7 例外吃掉：`core-context 1584/1650`（余 66）、`feature-chat 3619/3900`（余 281）、`feature-settings 3229/3400`（余 171）、`ChatViewModel 487/500`（余 13）；新逻辑外提新文件，不得再调 cap | **E6 全部精确命中**（外审数字对）；`docs/notes/w7-line-budget-ruling.md:55-62`；`arch-guard.sh:152-193` LINE_CAPS | ✅ 属实 | **写入本波硬约束**（§六 G10）：高级配置 UI 必须外提新文件（如 `EndpointAdvancedConfig.kt`），`ChatViewModel.kt` 只许加一行委托 |
| **P2-3** | 缓存结论按端点分组不足：A5 79.4% 与「免费池不可依赖」并存，台账 W7-02 仍写「缓存不可依赖」 | 台账 `:94`「⇒ **缓存不可依赖**」；`handoff-20261104-wave7.md:86`「需修正为『免费池（OR）不可依赖，NV 直连在真实会话形态下高命中』」；`:93`「W7-02 按『取到事实表』验收，**不承诺命中率**」；`docs/notes/w7-provider-matrix.md:41/:243` 仍写「免费池缓存命中不可作为产品承诺」 | ✅ 属实 | **同波收 Q4**（回填时按端点分组 + 保留「不承诺命中率」）；**不得 CLOSED**（F7） |
| **P2-4** | `shouldApply` 无「评估次数」可观测，只落 noop reason | `ConversationOps.kt:120-124` 只 `auto_compact=noop reason=…` + `persist`；无 `evaluated=1` / `committed` / `cap` / `saving` 数值 | ✅ 属实 | **挂账（H1 / C13.1 触发计数）** | 日志可观测性，随压缩重构同车 |

#### 1.4e §4 路线 S0–S3 + §5 验收判据 1–10（路线级）

| 条目 | 回树核实 | 判定 | 处置 |
|---|---|---|---|
| **S0**（对账止血：三向对账 / 修 P0-1+P1-1 / 升级 G22+G23 / 出口 arch-guard+selftest+EXPECTED_TESTS） | Q4/Q14 属实；P0-1/P1-1 属实；G22/G23 升级属实 | ✅ 属实（技术） | **拆分**：Q4+Q14+「重跑守卫/对账常量」**同波收**；P0-1/P1-1/G22 升级 **挂账 H1**。路线本身被用户功能波取代 |
| **S1**（让自动压缩真的能 applied，拿 `applied=true` + `cache_invalidated=true` + 下一请求 `cached_tokens=0`） | 现役 `applied=true` 零真机证据（`handoff-20261104-wave7.md:82` A4/A5 全 noop） | ✅ 属实（缺位） | 挂账 H1（**⛔ vacuous 风险**：拿不到就标 vacuous，不许写成已交付） |
| **S2**（尾部追加摘要 + 顺带收 P0-4/P0-5） | 台账 `:100` W8-01 OPEN | ✅ 属实 | 挂账 H3/H1 |
| **S3**（W8-03 二选一 / W8-02 三桶归一 / P1-5 安全 / P1-6+P2 收口 / 之后才进 X6） | 均属实 | ✅ 属实 | 挂账 H6/H4/H7 |
| §5 判据 1–10 | 1/2 需真机 applied（缺）；3/4/5/6/7 需新 JVM 用例（缺）；8 G19 VACUOUS（E2）；9 E2+E3 已绿；10 台账↔handoff↔roadmap 三向不一致（N1/N6 + `docs/02-roadmap.md:11`） | ⚠️ 部分属实（9 已绿，10 未做） | **判据 9、10 本波承接**（§六）；其余挂账 |
| §6 证据索引 14 行 | 抽查 `ChatViewModel.kt:394-395`、`ConversationOps.kt:96-137`、`SettledCompressPolicy.kt:46/49/75-87`、`ManualCompress.kt:99-112`、`HardBudgetShrinker.kt:91-107`、`BudgetFormula.kt:59-64`、`SwitchGuard.kt:72-108`、`SessionFootprint.kt:46-76`、`AppContainer.kt:388-395`、`SettingsScreen.kt:292`、`arch-guard.sh:1448-1491`、台账 `:100-103`、handoff `:71-86`、ruling `:53-62` —— **全部命中或微漂**（`SwitchGuard` 实际 `:72-109`、`SessionFootprint` 实际 `:28-38`+`:46-58`） | ✅ 属实（个别行号漂移） | 不做（本清单已按现役行号重钉） |

---

## 二、与用户新需求重合 / 被加重的项

### 2.1 会被功能波**顺手解决**（应写进本波交付物并销账）

| 外审项 | 用户需求 | 重合点 | 现役证据 | 判定 |
|---|---|---|---|---|
| **NEW-D / §二#8 / W3-07 / NEW-G 部分** | ① 端点高级配置含「**图片输入**」 | 端点加 `supportsVision` 字段（默认 false）+ 设置页开关 + 未启用时禁用图片入口；**同时必须修 `RemoteLlmEngine.kt:92` 硬编码**，否则 UI 开关无意义 | E7/E8/E9；`AppNavHost.kt:156/170` 图片入口已接线；`OpenAiCompatibleAdapter.kt:292-310` **已在发 image_url** | ✅ **重合且加重**：不只是「可见性诚实」，是能力声明与实现打架。本波须一并销台账 `W3-07`（`:69`，顺带订正 `GenerationEvent.kt:136→:142` 行号，N5） |
| **⑦ `dots` 请求格式 / `ReasoningStyle` 扩展** | ① 「思考模式 / 仅思考模式 / 允许关闭思考 / 默认思考强度 / 支持的思考强度 / 自定义协议」 | `RemoteEndpoint.kt:64-82` 已有 `ReasoningStyle` 四态 + `reasoningEffort`；`OpenAiCompatibleAdapter.kt:178-186` 只发 3 种形态。`docs/analysis/w8-provider-probe.md:31-49` 实测 dots 只认 `reasoning` 对象（`{"effort":...}` / `{"enabled":false}`），`reasoning_effort` 疑被忽略 | `applyReasoningStyle` @ `:90` 唯一调用点 | ✅ **重合**：需新增第 5 种 `ReasoningStyle` 形态 + 端点字段；**硬约束**：五档 UI 不能 1:1 映射（只认 low/medium/high/xhigh，「极致」须显式降级登记，禁静默发 `max`）；「允许关闭思考」与「仅隐藏思考」必须两个语义分开 |
| **③ JSON 格式检查** | ③ + ⑦ | `w8-provider-probe.md:39` `response_format:{"type":"json_object"}` 返回严格合法 JSON；但「校验返回文本」≠「强制只回 JSON」，须两判据 | 现役 `OpenAiCompatibleAdapter.kt:75-91` 请求体无 `response_format` | ✅ **重合**：属 adapter 请求体扩展，与 ⑦ 同一文件同一提交 |
| **② markdown 显示为文本选项** | ② | `ChatBubble.kt` 全文件无 markdown 渲染（E10），`:123` 注释「旧仓第二枚『复制为 Markdown』…成了装饰」；`core-design` 无 markdown 依赖（G4 禁图片库/注解处理器，**不得引入 markdown 解析库**） | E10 | ✅ **重合**：纯 UI 开关（渲染 / 源码文本二选一），落 `ChatBubble.kt` + 一个 state 字段；注意 `core-design` 12439/12600 余 161 行（E6） |
| **④ 旧仓 UI 复用** | ④ | `core-design` 已有 60+ 玻璃组件；但 `LiquidBottomTabs.kt` 1012 行 > 单文件 600 是**已登记例外**（`arch-guard.sh:267-276`），`core-design` 余 161 行 | `docs/analysis/source-review-2026-11-04.md:84-88` | ✅ **重合但高风险**：复用即触 G10 ⇒ 只能「复用现有组件、不新增文件」，新增 UI 一律放 `feature-settings`/`feature-chat` 新文件 |
| **W67 §一 T7 / W67 §三 P2-1 / W8深度审查 S3** | ⑦ + ① | schema 白名单逐键断言 / adapter 改动前置钉子 | `OpenAiCompatibleAdapter.kt:319-329 toolObject`；`OpenAiCompatibleAdapterTest.kt:108-110` 无逐键断言 | ✅ **重合**：本波改 adapter 请求体，**最该顺手补的钉子**（§三 Q11）；补了 +@Test ⇒ 同提交改 `EXPECTED_TESTS` |
| **C1 / C7 / C9 / P1-3 / P1-4 / §四 N1** | ⑤（推仓库 + CI 前的对账） | 台账/roadmap/handoff 三向一致是「推仓库前」的天然出口 | 台账 `:96` vs `:134`（N1）；`:99` vs handoff `:113`（N6）；roadmap `:11` 停在 W4 | ✅ **重合**：Q4/Q6/Q14 同波收，作为 ⑤ 的 S0 出口 |
| **W3-07 老化裁决（W67 §四.4）** | ① | 「W3-02 / W3-07 二选一显式裁决，别再滚」 | 台账 `:143` | ✅ **重合**：W3-07 本波 CLOSED；W3-02 仍挂账并显式登记「本波不做」 |

### 2.2 会被功能波**加重**（必须在方案里预留，不能无视）

| 外审项 | 加重机制 | 现役证据 | 临时缓解 |
|---|---|---|---|
| **C4 / C12 例2 / §3.3 推断 / P2-10 / NEW-G** —— L3 工具规格字符盲区 | ① 加「工具调用」配置 + ⑥ 免费节点矩阵要测 MCP/Skill/工具 ⇒ 工具数从现役 4~5 个涨到 20~30 个；`PreStepBudget.kt:95-98` 字符闸**不含 toolSpecChars**，而 `ContextBudget.kt:302-310` fingerprint 却数 `TOOLS_SCHEMA` 段 ⇒ 双尺分叉随工具数线性放大 | `PreStepBudget.kt:95-98`；`ToolSpec` 在 `core-model` | 挂账 H2 期间：① ⑥ 矩阵测试**限制单端点工具数 ≤ 现役量级**并在报告登记「工具规格放大场景未覆盖（⛔ vacuous）」；② 不新增任何「工具规格字符」第二把尺（避免 C3 教训） |
| **P1-2 / C10.a / F5** —— G22/G23 形态守卫 | ① 高级配置可能改 `SettingsViewModel.save()` 链；② markdown/JSON 检查可能改 `ChatViewModel.collect` 形态 ⇒ G23「恰好 1 处」立刻判红，G21 棘轮把合法重构一起卡住 | `arch-guard.sh:1471-1491`；E2 G23=1；`arch-guard-selftest.sh:516-525 m_g23` | 本波方案硬约束：**不新增 `.autoCompactOnSettled(` 调用点、不改 `val x = evaluateSwitch(` 赋值形态**；每次提交前重跑 `bash scripts/arch-guard.sh`（E2）+ `--only G22,G23`（§六） |
| **P1-6 / P0-5 / C10.a** —— 切换绕路 + SET_WINDOW 不刷新 | ① 加 10+ 端点字段后 `save()`/端点编辑更频繁；`SettingsViewModel.kt:148-153 delete` 无守卫、`:179-184` SET_WINDOW 不 `load()` ⇒ 新字段下「改了配置但 `blockedIds`/顶栏不更新」更明显 | `SettingsViewModel.kt:148-153/179-184/237`；`AppContainer.kt:388-395` | 本波不改结构；在 ⑤ 本地 UI/功能测试里**显式加一条用例**：「编辑高级配置保存后重进设置页，字段回显正确」，把 P1-6 的表象纳入功能验收 |
| **G17 密钥字段** | ① 新增 `extraHeaders`-类字段 / 自定义协议可能带凭据名；`RemoteEndpoint.kt:87-91` KDoc 明写「本类不含 apiKey 字段且永远不许加」 | `arch-guard.sh:1102-1156` G17 精确名 `apiKey/secret/password/token` | 新增字段命名避开精确名；**`@Serializable` 新类必须进 G17 扫描面**（它只扫 `core-model/src/main`，E9 请求体相关新 DTO 若放别处会漏） |
| **NEW-G 死字段家族 ≥5** | ① 新增 10+ 端点字段 ⇒ 若只有 UI 写入无生产消费，立刻新增 10 个「声明了没消费者」 | `EndpointEditScreen.kt:83-107` draft 只写 6 字段；`RemoteEndpoint.kt:96-137` 15 字段 | **每个新字段必须给「被调用」判据**（adapter 消费点 / 单测断言），否则不进 `RemoteEndpoint`。这是 W3 铁律，本波最易踩 |
| **W67 §一 T8 RunnerKey 联动** | ① 给 `RemoteEndpoint` 加字段 ⇒ `RunnerCache.kt:21-32 RunnerKey` 含整个 `RemoteEndpoint` ⇒ 改任一高级配置即重建 runner、前缀缓存重建 | `RunnerCache.kt:97-100` KDoc 自述「配置一变，key 就不等」 | ⑥ 矩阵测试记录 `cache_invalidated` 时**区分「因改配置」还是「因压缩」**，避免把 RunnerKey 重建读成压缩 bug |
| **P2-2 / G10 行数** | ① 10+ 字段全塞 `EndpointEditScreen.kt`（现 338 行，单文件上限 500）+ ④ 复用 UI 触碰 `core-design`（余 161） | E6；`arch-guard.sh:267-276` LiquidBottomTabs 例外 | 开波前先外提 `EndpointAdvancedConfig.kt` 等新文件（§六 G10），**不许调 `LINE_CAPS`/`TOTAL_LINE_CAP`** |

---

## 三、「同波可收」快修批清单（≤30 行 / 不争文件域 / 有明确判据）

> 主理人点名需逐条核实的 4 项：**C6.a / C6.b / 台账·roadmap 回填 / W8-03 二选一** —— 前三项属实可收，**第 4 项证伪（不是快修）**，见 §3.2。
> 所有条目共享一条硬约束：**纯文档项不得单独提交**（`build.yml` `paths-ignore` ⇒ 0 run，`AGENTS.md` §八），必须与代码提交同车。

### 3.1 可收清单

| Q# | 项 | 具体文件与行号 | 修法（一句话） | 怎么证明修好了（判据） |
|---|---|---|---|---|
| **Q1** | **C6.a `runCatching`×2** | `feature-chat/src/main/java/com/rickeal/liquidharness/feature/chat/ConversationOps.kt:106`、`:180` | 两行各替换为 `val windowCap = contextWindowCap(container)`（`ChatContextWindow.kt:24-31` 现成 `internal suspend fun`，同模块同包直调；调用点在 `scope.launch` 内，suspend 合法） | ① `grep -n "runCatching" ConversationOps.kt` → 仅剩 `:34`（JSONL 解析，非挂起点）与 `:226` KDoc；② `grep -n "contextWindowCap(container)" ConversationOps.kt` → 2 命中；③ 复用 `ChatContextWindowTest`（现役）或新增 1 条「取消时 `CancellationException` 原样重抛」用例（**若新增 @Test ⇒ 同提交改 `EXPECTED_TESTS`**）；④ 重跑 E2（`VIOLATION=0`）+ E3。**订正**：不承诺「收敛为单真值源」——`AppContainer.kt:265`、`SettingsViewModel.kt:229` 仍裸取数，登记为观察项 |
| **Q2** | **C6.b UTF-8 BOM** | `core-data/src/main/java/com/rickeal/liquidharness/core/data/SessionImportParser.kt:139`（`bytes.toString(Charsets.UTF_8)`）；夹具 `core-data/src/test/.../SessionImportParserTest.kt` | 字节入口先剥 `EF BB BF` 三字节再解码（1 行）；测试夹具加 1 个带 BOM 的 JSONL + 1 个带 BOM 的 markdown 样本 | 新增 2 条用例：BOM+JSONL 首行 `role` 可解析、BOM+markdown 首个 `## user` 不丢首段；`bash scripts/localbuild.sh :core-data:testDebugUnitTest` 通过；**同提交把 `EXPECTED_TESTS` 696 → 698**（E3 口径） |
| **Q3** | **C11.4 `append` 顺序（可选）** | `SessionImportParser.kt:269-276`（`TOO_MANY_MESSAGES` 判定 :269-274 先于 `body.isBlank()` :276） | 把 `:276 if (body.isBlank()) return null` 上移到 `messages.size >= MAX_MESSAGES` 之前 | 1 条用例：构造 5000 条消息 + 第 5001 条**空**正文 ⇒ 不返回 `TOO_MANY_MESSAGES`；同样计入 `EXPECTED_TESTS` |
| **Q4** | **台账 / roadmap / handoff 三向回填（含 N1/N6/F7 订正）** | `docs/06-deferred-ledger.md:78,79,93,94,95,96,99,132-134,67,69`；`docs/02-roadmap.md:11,19-21,697,702-704,811`；`docs/handoff-20261104-wave7.md:113` | ① `W5-01`/`W7-01` 按 A5（`27.2k ≤ 27904` + `21680 cached/79.4%`，handoff `:82`）改 `CLOSED` 并写证据；② **`W7-02` 只回填「⚠️ 部分·事实表已取，不承诺命中率」（handoff `:93`），不得 CLOSED**（F7）；③ `W7-03` 保持 `OPEN`（A6 ⚠️ 双向未触发）；④ 修 §二 统计表为 **CLOSED 12 / CARRIED 7 / OPEN 23**（N1），并把 `W7-04` 从 OPEN 列表移除；⑤ `W7-07` 按 handoff `:113` 改 `CLOSED`（N6）；⑥ roadmap 总览去掉「截至 W4」、W6/W7 回填实绩、X 线现状更新到 W7；⑦ 顺手订正 `W3-07` 证据列 `GenerationEvent.kt:136→:142`、`W3-05` 注释行号 117/121/210/168→125/307/211 及「注释写的是一个不存在的机制」措辞（N5/F1） | ① `bash scripts/arch-guard.sh` 中 **G20 仍 OK**（改状态/证据不破坏 6 列格式、不动 handoff 里的 ID）；② `awk` 逐行重数台账 = 12/7/23/42 与 §二 统计表一致；③ `grep -n "截至 W4\|⛔ \*\*未开始\*\*" docs/02-roadmap.md` 中 W6/W7 行 0 命中；④ **与代码提交同车**（避免 0 run） |
| **Q5** | **C10.d / C11.3 noop 落账级别（可选）** | `ConversationOps.kt:122`（`AgentLogStore.warn`）vs `:232`（落盘 `"INFO"`）；`:121` 组装的 `line` | `:122` 改 `AgentLogStore.info(line)`（noop 是常态路径，落账与级别两件事） | `grep -n "AgentLogStore.warn(line)" ConversationOps.kt` = 0；真机/日志中 `auto_compact=noop` 与落盘行级别一致（都 INFO）；**不改 `:185` 手动压缩的 `warn`**（那是「用户点了按钮什么都没发生」，应留 WARN） |
| **Q6** | **C8 X4 判据 6 恒绿修正（纯文档 1 行）** | `docs/02-roadmap.md:811` `6. \`@Test\` ≥ 380。` | 改为「`EXPECTED_TESTS` 双向对账通过（E3）+ 本波净增 `@Test` ≥ N」 | `grep -n "@Test.*380" docs/02-roadmap.md` = 0；E3 仍 696 = `build.yml:132`；与 Q4 同车提交 |
| **Q7** | **【本席新发现 N2】注释与实现打架第 5/6 例** | `feature-chat/.../ChatAttachmentPicker.kt:16-19`；`feature-chat/.../ChatInputBar.kt:42-44` | 改写 KDoc：删除「当前 main 源集里没有调用点 / `ChatMessage` 没有 attachments / `ChatInputBar` 不渲染 + 入口」，改为「已由 `AppNavHost.kt:156/170` 接线，`ChatInputBar.kt:72` 按 `onPickImage != null` 渲染」 | `grep -rn "没有调用点\|没有 attachments 字段\|不渲染「+」\|刻意没有搬" feature-chat/src/main` = 0；`grep -rn "onPickImage" ChatInputBar.kt AppNavHost.kt` 仍命中；纯注释、**0 新增 @Test**、不触 G10 行数闸门（改写不增行） |
| **Q13** | **NEW-I / W6-08 G10 尾换行可见性（可选）** | `scripts/arch-guard.sh:744-794 g10()`（`guard_ok "G10"` 在 `:794`） | 在 `:794` 的 detail 串追加 `缺尾换行=<N>`（只输出、不判红；`N` 用 `tail -c1` 逐文件统计） | E2 重跑：输出含 `缺尾换行=0`（现役 0 个，与台账 `:92` 一致）且 `OK=27 VACUOUS=1 VIOLATION=0` 不变；构造 1 个缺尾换行夹具文件 ⇒ 输出 `缺尾换行=1` 但 **G10 仍 OK**（证明「可见性 ≠ 判红」）。**不构成新守卫 ⇒ 无需新增 selftest case** |
| **Q14** | **P1-3 编号唯一源声明（纯文档）** | `docs/notes/w7-context-cache-roadmap.md:64,65,91,99,106,107,108,172`；`docs/06-deferred-ledger.md:3-4,100-103`；`docs/handoff-20261104-wave7.md`（W8 交接新增段） | 把路线草稿的 `W8-01..05` 全部改名 `R-01..R-05`（或并入台账新号），`:172` 去掉「下一波（W7/W8）交接文档」；台账头部加一行「**编号唯一来源 = 本文件；`docs/notes/` 内 `W*-*` 一律视为历史输入，不参与派单**」；W8 handoff 写一行「编号以台账为准」 | `grep -oE 'W8-[0-9]+' docs/notes/w7-context-cache-roadmap.md` = 0；`grep -oE '^\| W8-[0-9]+' docs/06-deferred-ledger.md` 只有 W8-01..04；E2 的 G20 仍 OK（G20 不扫 notes/，见 N4）。**详见 §五** |
| **Q10** | **【建议新增】本波 ⑥ 矩阵「跑前预登记」** | 新建 `docs/notes/w8-feature-matrix-preregistration.md`（`docs/analysis/` 之外、与 `w26-preregistration.md` 同目录） | 在 ⑥ 免费节点多模型矩阵开跑前，定死：上下文至 1M / 多模态 / 音频输出 / 工作空间读写 / 记忆 / Skill / MCP / KV cache 命中 / 手动·自动压缩 各项的**四态判据 + 端点分组 + `max_tokens` 前置条件**（`w8-provider-probe.md:46-49` 已证「max_tokens 是判据自身前置条件」） | 文件 mtime 早于首次矩阵日志；跑后不得改判据（改则版本 +1 并重跑）；复用 C13「跑前预登记」纪律，**不占额外代码** |
| **Q11** | **【建议新增】adapter 逐键白名单断言（随 ⑦ 同车）** | `core-engine/src/test/.../OpenAiCompatibleAdapterTest.kt:108-110`；`OpenAiCompatibleAdapter.kt:319-329 toolObject`、`:75-91` 请求体 | 在 ⑦ 改请求体的同一个提交里，补 2 条断言：`body.keys` 恰好等于白名单集合、`toolObject` 的 `function` 子对象只含 `name/description/parameters` | `grep -nE "白名单\|jsonObject.keys\|toSet()" OpenAiCompatibleAdapterTest.kt` 命中；**同提交 `EXPECTED_TESTS` 696 → 696+N**；`bash scripts/localbuild.sh :core-engine:testDebugUnitTest` 通过。补上后 W67 §一 T6 的钉子落地 |
| **Q12** | **【备选】NEW-E 断流 STOP（3 行 + 1 用例）** | `core-engine/.../provider/ProviderAdapter.kt:189-194` | `:192` 改为 `if (state.textChars == 0 && !state.sawTerminalMarker && state.toolDeltaCount == 0) return/记 stream_incomplete`（按现役 `AdapterStreamState` 字段取名） | 1 条假引擎用例：断流无 `[DONE]`、无正文、无工具碎片 ⇒ 不产生 `ModelStopped`；**风险**：改 `finish_reason` 语义会影响 ③ JSON 检查与 ⑥ 矩阵的结果判读 ⇒ **默认挂账 H9**，仅当主理人愿承担联调成本才同波收 |

### 3.2 主理人点名项中被**证伪 / 不收**的

| 项 | 核实结论 | 判定 | 处置 |
|---|---|---|---|
| **W8-03 二选一（EngineCapabilities / autoApproveDangerous 接线或连字段带用例删）** | **不是 ≤30 行快修**：`grep -rln "autoApproveDangerous\|capabilities()\|EngineCapabilities" --include=*.kt .` = **14 个文件**，其中 **7 个 main**（`core-agent/engine/LlmEngine.kt:47`、`core-data/RemoteEngineBridge.kt:33`、`core-data/ContextLimitWriteback.kt`、`core-data/SettingsRepository.kt:75`、`core-engine/RemoteLlmEngine.kt:89`、`core-kernel/AgentPolicy.kt:115`、`core-model/GenerationEvent.kt:139-148`）+ **7 个测试**（5 处 `capabilities()` override、`ApprovalChokepointAuditTest.kt:241/276/300` 三条警报用例、`FakeEngine.kt:38`）。「连字段带用例删」需同步删接口方法、实现、委托、数据类、3 条警报用例、5 处 test override，且**必改 `EXPECTED_TESTS`**。更关键：`core-model/GenerationEvent.kt` 与 `core-engine/RemoteLlmEngine.kt` **正是功能波 ① 要改的文件** ⇒ 直接争文件域 | ⛔ **证伪（作为快修）**；作为「挂账项」✅ 属实（台账 `:102` `OPEN`「W8 首波强制二选一，**不许再延期**」） | **挂账 H6 并显式改号**。台账文字「不许再延期」+ handoff `:114`「限期」已被用户功能波重裁 ⇒ **必须在新 handoff 里写一行裁决**：「W8-03 因用户功能波改期至下一波，新号 W9-0x」，否则就是第 4 次顺延且无人记录（正是 C1 批判的病理）。**不要**在本波偷偷做半套 |
| **C5（M2 余件）** | 属实且 ≤10 行（§1.1 C5），但外审指定「归宿 W8-03 审批面二选一波」 | ✅ 属实 | **默认随 H6 挂账**；若主理人愿单独拆出，可列 Q8 同波收（3 行 `requiresConfirmation` + 审计表 2 行 + 同提交改 `EXPECTED_TESTS`），文件域 `core-agent` 与功能波零冲突 |
| **「台账/roadmap 回填」** | 属实（C9/P1-4/§三 P1-b/C12 D8 四份同指） | ✅ 属实 | **Q4 同波收**，但按 F7/N1/N6 三处订正回填，且必须搭代码车 |

### 3.3 同波收批次的统一出口判据

```
E2  bash scripts/arch-guard.sh | tail -8      → OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0（守卫数值不得变）
E3  grep -rnE '^\s*@Test\b' --include=*.kt . | wc -l  ==  build.yml:132 EXPECTED_TESTS（同提交同步）
E5  bash scripts/arch-guard-selftest.sh --list | wc -l → 28（本波不新增守卫，case 数不变）
Q4  G20 OK；台账逐行 12/7/23/42 与统计表一致；roadmap 无「截至 W4」的 W6/W7 行
Q1  grep -c "runCatching" ConversationOps.kt → 1（仅 JSONL）+ contextWindowCap(container) = 2
Q13 arch-guard 输出含「缺尾换行=N」且 G10 仍 OK
Q14 grep -oE 'W8-[0-9]+' docs/notes/w7-context-cache-roadmap.md → 0
⑤   本地 UI/功能测试 + adb 真机通过后才 push；`git push --dry-run` 为真判据（知识库 §5）
```

---

## 四、重型重构挂账清单

> 统一原则：**用户功能波为主波**；下列项全部延后到「W9 正确性/韧性波」（建议波号，需主理人裁决）。每条给出：会碰哪些文件 / 是否与功能波冲突 / 挂账编号建议 / 延后期间风险与临时缓解。

| H# | 项（合并同源外审条目） | 会碰的文件（现役路径） | 与功能波冲突？ | 挂账编号建议 | 延后期间风险 / 临时缓解 |
|---|---|---|---|---|---|
| **H1** | **压缩量纲与切换守卫统一**：C3 四面 / W67 P0 / W8深度审查 P0-1、P0-2、P0-4、P0-5、P1-1、P2-1、P2-4 / NEW-A、B、C / C10.b、C10.e、C11.1、C11.5 / C13.1-3 / C12 例3 / P1-2 的 G22/G23 升级 | `feature-chat/ConversationOps.kt`、`ChatViewModel.kt`、`ChatUiState.kt`、`ChatHistoryMapper.kt`、`SessionFootprint.kt`、`ManualCompress.kt`；`core-context/SettledCompressPolicy.kt`、`SwitchGuard.kt`、`BudgetFormula.kt`、`HardBudgetShrinker.kt`；`core-agent/loop/ContextBudget.kt`、`PreStepBudget.kt`、`RunState.kt`；`core-data/SessionFootprintStore.kt`、`AppContainer.kt`；`feature-settings/SettingsViewModel.kt`、`SwitchDecision.kt`；`scripts/arch-guard.sh` + `arch-guard-selftest.sh`（G22/G23 语义化 + 3 个新 case） | **是，严重**：`feature-chat`（余 281 行）、`ChatViewModel.kt`（余 13 行）、`feature-settings`（余 171 行）全是功能波 ①② 的主战场 | **W9-01**（单一条目；四份外审的同一缺陷**禁止拆成多条**，附「外审来源索引」子表） | 风险：`applied=true` 至今零真机证据（⛔ **vacuous**），1M 档缓存工程在默认配置下不可能命中；`floor 1.8` 误拦 256k/1M 会话。**缓解**：① ⑥ 矩阵测试把「自动压缩」标 **⛔ vacuous / 只记 noop**，不写成已交付（AGENTS.md §二.3）；② 不改 `COMPACT_TRIGGER_CHARS`、不改 `SwitchGuard` 判定式（防半迁移，NEW-A 明确警告）；③ C3 D9② 实跑 `grep -rn sessionFootprint core-agent/src/main` = 0，**旧标定值不会进硬预算**，延后期间无超发风险；④ Q4 回填时把 W5-01/W7-01 按 A5 ✅ 结清，避免重复安排真机 |
| **H2** | **L3 工具规格字符纳入**：C4 / C11.1 / C12 例2 / W2.6–W7 §3.3 / P2-10 / NEW-G（工具面） | `core-agent/loop/PreStepBudget.kt:95-98,122`、`ContextBudget.kt:125,209-218,302-310`；`core-context/HardBudgetShrinker.kt:91-107`；`PreStepBudgetTest` 新增 2 条用例 | **否**（`core-agent`/`core-context` 不在功能波主改域），但**被功能波加重**（§2.2） | **W9-02**（**不要**用 W8-05，见 N3） | 风险：⑥ 矩阵若接 20~30 个 MCP 工具，specs JSON 字符不进字符闸 ⇒ 400 或 L3 漏拦（现役只有 4~5 个内置工具所以未暴露）。**缓解**：⑥ 限制单端点工具数并在报告登记「多工具规格场景 ⛔ vacuous」；`W5-03` MCP cleartext 裁决仍 `OPEN`（台账 `:80`），本波不做 MCP 真机联调 |
| **H3** | **W8-01 尾部追加摘要（形态重构）** + P0-3 / NEW-A / W67 §五主线 B 的「压缩逻辑最终住址」 | `feature-chat/ManualCompress.kt`、`ChatUiState.kt:208`、`ChatHistoryMapper.kt:93-98`、`ConversationOps.kt`、`SessionFootprint.kt`；`core-context/StructuredSummary.kt`；新增集成用例 | **是，严重**：`feature-chat` 全模块 + 单指针改双指针会动 ② 的 bubble 渲染链 | **W9-03**（承接台账 `W8-01`，改号并写 `CARRIED`） | 风险：长会话只能压一次，与「上下文至 1M」的 ⑥ 目标正面冲突（⑥ 会造长会话）。**缓解**：⑥ 用「新会话 + 导入题面」而非「单会话跑 1M 轮」；明确把 1M 结论标为「单次请求窗口」而非「跨 run 会话体积」；不承诺自动压缩 exercised |
| **H4** | **W8-02 三桶归一 + schema 白名单钉子** | `core-model/Generation.kt`（`cachedPromptTokens`）、`core-engine/provider/OpenAiCompatibleAdapter.kt:75-91`（usage 解析）、`feature-chat/EventReducer.kt`、`OpenAiCompatibleAdapterTest.kt` | **是**：adapter 正是 ⑦/③ 要改的同一文件（`:90 applyReasoningStyle`、请求体 `:75-91`） | **W9-04**（承接台账 `W8-02`） | 风险：三桶未归一 ⇒ ⑥ 的「KV cache 命中」判据仍读 `cached/prompt` 两口径恒等（`OpenAI` 兼容族），**不能**据此写稳定命中率结论。**缓解**：⑥ 按端点分组、强制记 `max_tokens/prompt_tokens/completion_tokens/finish_reason`（handoff `:92` 已定），1M 档 ≥2 次重放才写结论；**Q11 的白名单断言本波先补**（前置钉子，不与 H4 冲突） |
| **H5** | **W3-05 journal / FreezeClock 独立波** + C2 / NEW-F（实现部分）/ W67 §五主线 B / W8深度审查 P0-2 路线 | `core-agent`、`core-kernel`、`core-context`、`feature-chat`、`app` 全模块新增；5 处 KDoc 已改写（F1）**不需再改** | **是，全面** | **W9-05**（承接台账 `W3-05`，`CARRIED` 换号；**或按 C2 建议独立成 W8.5/W9 前置波，由主理人裁**） | 风险：闪断/灭屏恢复机制缺位，压缩写坏上下文后无 journal 恢复。**缓解**：本波 ⑤ adb 真机不做杀进程恢复判据，明确标 ⛔ 不适用；`docs/notes/w7-plan.md:166` 与 `SwitchGuard.kt:39` 的「不新增会话级标定值」表述保持原样（H1 扩列时再改，C12 例3） |
| **H6** | **W8-03 二选一死字段清剿** + C5 / NEW-G / §四.4 W3-02 | 14 个文件（§3.2），含 `core-model/GenerationEvent.kt`、`core-engine/RemoteLlmEngine.kt`、`core-agent/engine/LlmEngine.kt`、`core-kernel/AgentPolicy.kt:115`、`ApprovalChokepointAuditTest.kt`；`feature-settings/SettingsRoute.kt:225`（W3-02 `onImport`） | **是**：`GenerationEvent.kt` + `RemoteLlmEngine.kt` = 功能波 ① 必改 | **W9-06**（承接台账 `W8-03`，**新 handoff 必须写「因用户功能波改期」的显式裁决**，见 §3.2） | 风险：`autoApproveDangerous` 3 条警报用例是「一旦有人接线就立刻红」（台账 `:82`），本波 ① 新增端点字段**不得**复用该字段；W3-02 Skill 导入无入口，⑥ 的「Skill」矩阵项只能测**内置 Skill**。缓解：⑥ 报告登记「MCP sampling/roots、Skill 导入 ⛔ 不适用」 |
| **H7** | **安全收口小波**：P1-5 / W67 §一 T4、§三 P2-3（AttemptObserver 取证缝）/ W3-04 MCP token Keystore / W6-05 剪贴板 UI / R21 三条脱敏断言 / X5 / A5 G19 脱 VACUOUS | `core-engine/src/main`（加只打键名的诊断日志）、`core-data/McpServerStore.kt`、`core-data/EndpointSecretStore.kt`、`core-model/SecretRedactor.kt`、`feature-settings`（开关）、`docs/04-risk-register.md:345` 三条断言的测试 | **部分**：若本波 ⑦ 给 `core-engine` 接请求体诊断日志，会与 G19 交互 | **W9-07** | 风险：G19 长期 VACUOUS（E2），「装了烟感但没火」。**缓解**：本波 ⑦ 若接日志，**严格遵守 G19 三规则**（`arch-guard.sh:1208-1228`：只打键名/header 名、不拼值、`token` 带词边界）⇒ G19 从 VACUOUS 转 OK（VACUOUS 1→0，**只许减，允许**）；**绝不为变绿调大 `VACUOUS_BASELINE`**（AGENTS.md §七 / `arch-guard.sh:1434`） |
| **H8** | **400 CONTEXT_OVERFLOW 自愈闭环** | `core-engine/RemoteLlmEngine.kt:184-211`、`RetryPolicy.kt:50-64`、`core-context/HardBudgetShrinker.kt`、`core-data/ContextLimitWriteback.kt` + 新单测 | **是**（`RemoteLlmEngine.kt` = 功能波 ① 必改） | **W9-08** | 风险：标定滞后窗（measured 3.5 → 实际 2.5）两层同时放行 ⇒ 400。缓解：⑥ 矩阵若遇 `CONTEXT_OVERFLOW`，按现役 `RetryPolicy` 不重试的既定行为记录，**不**临时改引擎；`verifiedContextLength` 写回已在位，400 后下次生效 |
| **H9** | **断流误标 STOP** | `core-engine/provider/ProviderAdapter.kt:189-194` + 1 条假引擎单测 | **否**（不同文件），但改 `finish_reason` 语义影响 ③/⑥ 判读 | **W9-09**（或 Q12 备选同波） | 风险：空正文被当正常 `ModelStopped` ⇒ 不进错误账、照常跑 `auto_compact`。缓解：⑥ 矩阵把「空正文 + 无 [DONE]」单列一栏标 ⚠️，不与正常 stop 混算 |
| **H10** | **W26-02 裁决 + 夹具 v3 重跑 / Σcaps 口径二选一 / G20 第四判据（C7）/ G24（C9）/ 台账自洽判据（C12 D8）/ G10 尾换行判红** | `scripts/fixtures/w2-tool-prompts.json:66,223`、`scripts/stoploss/score_w2_stoploss.py:207-210`、`docs/notes/w26-run-report.md`；`scripts/arch-guard.sh` + `arch-guard-selftest.sh`（**每个新守卫必同步 case**）；`docs/notes/w7-line-budget-ruling.md` | 否（脚本/文档域） | **W9-10**（合并为「守卫与口径专项波」） | 风险：新守卫若扫描面为空 ⇒ VACUOUS 1→2 > `VACUOUS_BASELINE=1` ⇒ **G21 判红**（`arch-guard.sh:1430`）。缓解：本波**不新增任何守卫**（Q13 只加输出不判红），把守卫专项整体推到 H10 并配齐 selftest 变异自证；Σcaps 三套口径（43400 / 46000 / 2900）在 Q4 回填时统一钉成 E4 的 **46000** 并写明「与 TOTAL 解耦，总量只由 TOTAL 管」或「加自检」二选一留 H10 裁决 |

**挂账编号建议汇总**：`W9-01`（压缩量纲统一）/ `W9-02`（L3 工具规格）/ `W9-03`（尾部摘要，承接 W8-01）/ `W9-04`（三桶归一，承接 W8-02）/ `W9-05`（journal，承接 W3-05）/ `W9-06`（死字段二选一，承接 W8-03）/ `W9-07`（安全收口）/ `W9-08`（400 自愈）/ `W9-09`（断流 STOP）/ `W9-10`（守卫与口径专项）。**W8-04（二次确认框）本波不动，保留 `OPEN`**（台账 `:103`）。

---

## 五、编号撞车核实（P1-3）与「唯一编号源」建议

### 5.1 核实结论：**✅ 属实，且比外审说的更严重**

| 编号 | `docs/06-deferred-ledger.md`（唯一台账，`:100-103`） | `docs/notes/w7-context-cache-roadmap.md`（历史输入） | 是否同义 |
|---|---|---|---|
| **W8-01** | 会话级压缩「**尾部追加摘要**」形态（`:100`） | **microcompact**（`:64`、`:106`「只清旧 tool result 占位…`minTokenSavings=256`」） | ❌ 不同义 |
| **W8-02** | **三桶归一**（独立 `cacheWrite` 字段，`:101`） | **stable/dynamic 分段布局**（`:99`） | ❌ 不同义 |
| **W8-03** | **EngineCapabilities / autoApproveDangerous 二选一**（`:102`） | **多厂商 usage 字段适配**（`:65`、`:91` DeepSeek `prompt_cache_hit_tokens`/Kimi/GLM） | ❌ 不同义 |
| **W8-04** | 编辑当前端点「**放行二次确认框**」（`:103`） | **压缩↔缓存联合判据**（`:107`） | ❌ 不同义 |
| **W8-05** | **台账无此号** | **反应式压缩**（`:108`，`finish_reason=length`/400 归一化触发） | ❌ **外审 P1-3 漏报的第三个撞车源**；且 C4 恰好建议「新开 **W8-05**」（复审41 §二行动单）⇒ 会造成第 3 重撞车（N3） |

补充核实：
- `handoff-20261104-wave7.md:5` 确把路线草稿标为「**四报告融合的路线草稿，历史输入**」✅（P1-3 引文属实）；但 `w7-context-cache-roadmap.md:172` 仍自称「**本报告即下一波（W7/W8）交接文档**」✅（P1-3 引文属实），两份自述互相打架。
- 路线草稿里的 `W7-01`（`:104` 压缩真机重放）、`W7-03`（`:105` 手动压缩真机验收）与台账 `W7-01`/`W7-03`（`:93/:95`）**语义一致**，只有 `W8-0x` 全撞。
- **G20 判不出，且根本没扫**：`arch-guard.sh:1327` `files="$(find docs -maxdepth 1 -type f -name 'handoff-*.md')"` ⇒ 扫描面**只有 `docs/` 顶层的 handoff**，不含 `docs/notes/`（N4）。handoff `:114` §七里的 `W8-01..04` 与台账同义，所以 G20 全绿**不能**证明没有撞车。G20 的三条判据（`arch-guard.sh:1308-1312`）也确实没有「同号多义」项。
- C1 的「外审承接表」若按 P1-3 建议入账，须注意 G20 会把任何「首列是 `W*-*` 且 6 列」的表格当台账行校验列数（台账 `:145-146` 已警告）⇒ **承接表首列不要放裸 `W8-0x`**。

### 5.2 「唯一编号源」建议（Q14 落地形态）

1. **唯一编号源 = `docs/06-deferred-ledger.md`**，在其头部（`:3-4` 之后）加一行：
   > 「`docs/notes/**` 与任何外审/路线草稿里出现的 `W<数字>-<数字>`、及 `R-xx`，一律视为**历史输入**，不参与派单、不作为状态来源；判状态、判是否丢失、派活只认本文件 §一 表格行。」
2. **路线草稿改名**：`w7-context-cache-roadmap.md` 的 `W8-01..05` → `R-01..R-05`（`:64/65/91/99/106/107/108`），`W7-A..E` 可保留（字母后缀不与台账数字号冲突），`:172` 删掉「下一波（W7/W8）交接文档」改为「历史路线输入（编号已废弃，见台账）」。
3. **W8 handoff 写一行**：「本波编号以 `docs/06-deferred-ledger.md` 为准；`W8-01..04` 因用户功能波整体改期，新号 `W9-0x`，见 §四 H1–H10」——**同时履行 C1 的入账语义与 H6 的「不许再延期」显式裁决**。
4. **机械判据（挂账 H10，不在本波加）**：给 G20 加「同号多义」**告警项（只告警不判红）**，并把扫描面从 `docs/handoff-*.md` 扩到 `docs/notes/*.md` 做「`W*-*` 是否指向台账不同项」的比对。**注意**：扩扫描面会新增守卫 ⇒ 必同步 selftest case（`arch-guard-selftest.sh` 现 28 条，E5），且新扫描面若为空会抬 VACUOUS ⇒ G21 判红。故 **H10 必须成波**，本波只做文档改名。
5. **命名避坑**：所有新挂账号用 `W9-0x`（§四），**禁用 `W8-05`**（N3）；`W3-05`/`W8-01..03` 换号时老行改 `CARRIED` 并写新号，**不删行**（台账 §三.2，删行 G20 判红）。

---

## 六、本波新增判据清单（用户新功能上线后必须同步）

> 基线（E2/E3/E4/E5）：`arch-guard OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`；严格 `@Test` = **696** = `build.yml:132`；`VACUOUS_BASELINE=1`（G19）；`LINE_CAPS` Σ=46000 / `TOTAL_LINE_CAP=42500`；selftest `CASES` = **28**。
> 通用纪律：**每新增守卫必同步 selftest case**（知识库 §5）；**任何新增/删除 `@Test` 必须同一次提交改 `EXPECTED_TESTS`**（`build.yml:99-132`、`:445-523` 双向对账）；**守卫报红 ≠ 守卫真命中**（自测网按守卫名字符串匹配，守卫自身故障也含守卫名 ⇒ case 假 PASS，须断言输出能看到违规行本身）。

| 判据 | 现役位置与机制 | 本波会怎么动它 | **不补会怎样** | 必做动作（可复跑） |
|---|---|---|---|---|
| **G17 · `@Serializable` 禁密钥字段** | `arch-guard.sh:1102-1156`；只扫 `core-model/src/main`，精确名 `apiKey/secret/password/token`（大小写不敏感），钉 `val NAME:` 声明形态；selftest `m_g17` @ `arch-guard-selftest.sh:133` | ① 给 `RemoteEndpoint.kt:96-137` 加 10+ 字段；② 可能新增 `@Serializable` 配置类（思考强度枚举/自定义协议 DTO）；③ `extraHeaders: Map<String,String>` 里若塞 `Authorization` 值，**G17 只查属性名、查不到 map 内容** | ① 新 DTO 若放在 `core-model` 之外（`core-engine`/`feature-settings`）⇒ **完全逃出扫描面**，密钥字段可能明文进 DataStore；② 字段若精确命名 `token`/`secret` ⇒ G17 **正确判红**，CI 卡住；③ `extraHeaders` 值泄漏 G17 判不出（需人工判据） | 所有新可序列化配置类**一律放 `core-model/src/main`**；命名避开 4 个精确名；`grep -rn "@Serializable" core-model/src/main` 逐个确认新类进扫描面；重跑 E2 看 `[G17] OK`；新增一条「高级配置保存后 `endpoints.json` 不含 apiKey/secret 明文」的本地 UI 判据（⑤） |
| **G22 / G23 · 「被调用」形态守卫** | `arch-guard.sh:1449-1464`（G22：`val x = evaluateSwitch(` ≥1，实跑 = 2）/ `:1471-1491`（G23：`.autoCompactOnSettled(` 恰好 1 + `shouldEvaluate(` ≥1，实跑 = 1）；selftest `m_g22`/`m_g23` @ `:506/:516`（变异 = 删**全部**调用行） | ① 高级配置可能改 `SettingsViewModel.kt:106-141 save()` 链；② ② markdown/③ JSON 检查可能改 `ChatViewModel.kt:385-397 collect` 形态；③ 若把 `ChatViewModel.kt:394` 拆成多终态/表达式形态 ⇒ G23 立刻判红 | ① **G23 假红**：合法重构成多终态即 `calls≠1` ⇒ `VIOLATION`，而 G21 棘轮（`VACUOUS≤1`）把合法重构一起卡住（P1-2）；② **G22 假绿**：只删 `setActive` 的 guardSwitch，`save` 那处还在 ⇒ `n=1 ≥1` 仍绿，`m_g22` 删全部才红 ⇒ 判不出「某个写入点被摘掉」；③ 新增「高级配置写入点」若绕过 `evaluateSwitch`，守卫完全无感 | **本波硬约束**：不新增 `.autoCompactOnSettled(` 调用点、不改 `val x = evaluateSwitch(` 赋值形态、不新增「活跃端点/档位生效」写口。每次提交前 E2 + `bash scripts/arch-guard-selftest.sh --only G22,G23`（须能看到违规行本身，不只 PASS）；若确需重构 ⇒ 按 P1-2 升级为「逐写入点变异自证」并**同步新增 3 个 selftest case**（`selftest` 28 → 31），这已属 H1 不是本波 |
| **`EXPECTED_TESTS` / 严格 `@Test` 计数** | `build.yml:132` = `'696'`；`:445-523` Reconcile 步骤（严格 `^\s*@Test\b`，排除 build/.gradle/.kotlin/.git，逐文件 Δ 归因）；E3 实跑 = 696 | ② markdown 渲染开关、③ JSON 检查、① 高级配置表单、`ReasoningStyle` 新形态、Q2 BOM 夹具、Q11 白名单断言、Q1 取消用例 —— **每一项都会 +@Test** | **CI 直接判红**（`:521/:523` 双向对账）；更隐蔽的是**中间态红**：先删测试 / 先改基线，两者不同提交 ⇒ 基线 696 / 实跑 688 或反之，必红（W7-04 同型事故，台账 `:96`） | **同一次提交**改 `build.yml:132`；用 E3 严格口径复跑（**不要**用宽松 `grep -c "@Test"`，会多算 KDoc 引用）；JUnit4 方法返回 `Unit`、方法名禁 `. ; [ /`（知识库 §5）；跑 `bash scripts/fulltest.sh` 汇总 `TEST-*.xml`（绿跑不打印用例数，不能只看 console） |
| **G10 · 行数闸门 / 单文件 500·600** | `arch-guard.sh:152-193 LINE_CAPS`、`:204 TOTAL_LINE_CAP=42500`、`:267-276 EXCEPTIONS`（`LiquidBottomTabs.kt` 1012>600 已登记例外）、`:744-794 g10()`；E6 实跑余量：`feature-chat 3619/3900`（281）、`feature-settings 3229/3400`（171）、`core-design 12439/12600`（161）、`core-context 1584/1650`（66）、`ChatViewModel.kt 487/500`（13） | ① 10+ 高级配置字段若全塞 `EndpointEditScreen.kt`（现 338 行）→ 逼近 500 单文件上限；④ 旧仓 UI 复用若往 `core-design` 加文件 → 余 161 行瞬间触顶；② markdown 若往 `ChatViewModel.kt` 加逻辑 → 余 13 行 | **G10 判红**（正确行为），按 W7 裁决「触顶 = 先拆、不是调上限」（`docs/notes/w7-line-budget-ruling.md:53-62`、`handoff-20261104-wave7.md:122`）；若中途被迫拆文件，会打乱 ⑤「本地测试通过才推」的节奏；若有人图省事调 `LINE_CAPS` ⇒ 违反 W7「一次性例外不构成先例」 | 开波前**先外提新文件**：`feature-settings/EndpointAdvancedConfig.kt`（表单）、`feature-chat/MarkdownText.kt`（② 渲染，若需）、`core-model/EndpointCapabilities.kt`（新 DTO）；`ChatViewModel.kt` 只许加一行委托；`core-design` **只复用不新增**；每提交跑 E2 看 `[G10] OK`；**不动** `TOTAL_LINE_CAP`/`LINE_CAPS`/`EXCEPTIONS`（Q13 只加输出不判红） |
| **`VACUOUS_BASELINE` / G19 / G21 棘轮** | `arch-guard.sh:256 VACUOUS_BASELINE=1`；G19 @ `:1190-1228` 扫 `core-engine/src/main` 日志调用点（E2 = **0 命中 ⇒ VACUOUS**）；G21 @ `:1419-1437`「只许减不许增」，`:1434` 明写「**不要靠调大 VACUOUS_BASELINE 变绿**」；E2 `VACUOUS=1` | ⑦ `dots` 请求格式排查极想给 `core-engine` 加请求体诊断日志；① 新增 `ReasoningStyle` 形态也可能触发日志；若本波新增任何守卫且扫描面为空 ⇒ VACUOUS 1→2 | ① 加了日志但拼了 `Authorization=<值>` / `apiKey` ⇒ **G19 从 VACUOUS 转真红**（这是好事，但会卡 CI）；② 为让 G19「脱 VACUOUS」而调大基线或缩扫描面 ⇒ 违反 `AGENTS.md` §七「不许用放宽判据换绿」；③ 新守卫扫空集 ⇒ VACUOUS 2 > 1 ⇒ **G21 判红**；④ 反之，若接了合规日志，G19 转 OK、VACUOUS 1→0（**允许，只许减**） | 若 ⑦ 接日志，**只打端点 id / header 名 / 请求体键名，绝不拼值**，`token` 带词边界（`arch-guard.sh:1217-1221`）；接完重跑 E2 预期 `OK=28 VACUOUS=0`（**不得**为了凑数改 `:256`）；**本波不新增任何守卫**（Q13 只加 G10 输出，不算新守卫、无需 selftest case）；任何新守卫一律推 H10 并配齐变异自证 |
| **（附加）`@Test` 分布 / `selftest` case 数** | E5 = 28 case，`:93` 自述「覆盖口径 = 全部 28 个 ID」 | 本波不新增守卫 ⇒ 保持 28 | 若有人照外审写「30/30」并据此派活，会去找 2 个不存在的 case（F4）；若新增守卫却不加 case ⇒ 违反知识库 §5「每新增守卫必同步 selftest case」 | `bash scripts/arch-guard-selftest.sh --list \| wc -l` = 28；文档一律写 **28/28** 或「28 个 case + 1 个独立 VACUOUS 机制自证段」 |

---

## 七、裁决方向独立复核、复盘与限制声明

### 7.1 对主理人拟裁决方向的独立复核

| 裁决点 | 本席结论 | 理由（回树） |
|---|---|---|
| **以用户功能为主波** | **✅ 通过（置信度 ✅）** | 四份外审全部默认 W8 = 正确性收口/发布前波（W8深度审查 §0、W67 §五、复审41 §二），与用户明确需求冲突；功能波 ①⑦③ 必改 `RemoteEndpoint.kt`/`GenerationEvent.kt`/`OpenAiCompatibleAdapter.kt`/`EndpointEditScreen.kt`/`ChatBubble.kt`，与重型重构抢 `feature-chat`（余 281）、`ChatViewModel.kt`（余 13）、`core-design`（余 161）三处 G10 余量（E6/P2-2） |
| **同波只收低成本且与功能域重合的项** | **✅ 通过，但需按 §三收紧** | 可收 = Q1/Q2/Q4/Q6/Q7/Q13/Q14 + Q10/Q11（建议新增）+ 可选的 Q3/Q5/Q8/Q12。**必须从快修批里剔除 W8-03 二选一**（§3.2 证伪：14 文件、争 `GenerationEvent.kt`/`RemoteLlmEngine.kt`）；C5 默认随 H6 挂账 |
| **重型正确性重构挂账下一波** | **✅ 通过（置信度 ✅）**，并补 2 条 | C3 四面统一、C4 L3 工具规格、W8-01 尾部摘要、W8-02 三桶归一、W3-05 journal 全部挂账（§四 H1–H5），文件域冲突成立。补充：① **P0-1/P1-1/P0-4/P0-5/P1-2/W67 P1 400 自愈** 也属同族重型，须一并挂账（外审把它们排在 S0/S1，若只挂 5 项会漏）；② **H6（W8-03）必须在新 handoff 写显式改期裁决**，否则构成 C1 批判的「第 4 次静默顺延」 |
| **避免与功能波撞 `feature-chat`/`core-context` 文件域** | **⚠️ 部分成立，需订正** | `feature-chat` 确实冲突；但 **C4（L3 工具规格）只碰 `core-agent`/`core-context`，不碰 `feature-chat`** —— 它挂账的真正理由是「重型 + 被 ⑥ 多工具矩阵加重」，不是文件域冲突（§2.2）。另外 **H4（W8-02）与 ⑦ 撞的是 `core-engine/provider/OpenAiCompatibleAdapter.kt`，不是 `core-context`**。**Q11 的白名单钉子建议本波同车补**（前置钉子，不冲突） |
| **Phase 0 六条已核实结论** | **✅ 全部复核通过（置信度 ✅）**，并补 1 条 | ① `RemoteEndpoint.kt` 有 `supportsTools:128`/`supportsReasoning:129`/`reasoningStyle:130`/`reasoningEffort:131`/`extraHeaders:132`/`kind:100`，**确无 `supportsVision`**（E7/E8）；② `RemoteLlmEngine.kt:92 supportsVision = false`、`GenerationEvent.kt:142` 默认 false ✅；③ `EndpointEditScreen.kt` 只 6 个基础字段、零高级配置 ✅（E8）；④ `OpenAiCompatibleAdapter.kt:178-186` 只发 3 种思考形态 ✅（E9），且 `docs/analysis/w8-provider-probe.md:31-49` 实测 dots 只认 `reasoning` 对象（N7）；⑤ `ChatBubble.kt` 无 markdown 渲染 ✅（E10）；⑥ 台账 42 条、handoff A5 有 `27.2k ≤ 27904`+`21680 cached/79.4%`（`:82`），但 W5-01/W7-01/W7-02 仍 `OPEN`（`:78/:93/:94`）✅ **「进度回填滞后」属实**（Q4 收）。**补充**：W7-02 **不得** CLOSED（F7），台账 §二 统计表自相矛盾（N1），W7-07 状态也滞后（N6） |

### 7.2 复盘：可借鉴的过往实践

1. **「外审查的是它那一版的树」再次批量命中**：旧仓 W87 一次证伪 4 条、W88 同型；本波一次证伪 **F1/F2/F4/F5** 四条（含 2 条**数字**证伪 F3/F4）+ 2 条过时（F6、§四.4 行号漂）+ 1 条措辞夸大（F8）。**最典型**：NEW-F 花整段论证「5 处注释主动误导」，而现役注释逐字写着「本仓尚未落地，挂账 W3-05，别在别处引用它当成已接机制」——若照外审派单去「改注释」，会制造一次纯浪费提交。**纪律有效，`grep` 必须逐字读而非只看命中数**。
2. **「组件有测试 ≠ 组件被使用」的逆面**：本波最易踩的是 ① 新增 10+ 端点字段只有 UI 写入、无 adapter 消费 ⇒ 立刻新增 10 个「声明了没消费者」（NEW-G 家族）。**每个新字段必须给被调用判据**（§2.2）。
3. **「守卫扫空集 = VACUOUS 不是通过」**：E2 实跑 G19 VACUOUS（0 日志调用点）。本波 ⑦ 若接日志让 G19 转 OK，是**减少** VACUOUS（允许）；但**绝不能**为了「安全收口看起来完成」去调基线。同理 §5 判据 9「守卫 28 条全绿」里的 1 条是 vacuous，不能写成 28 条通过。
4. **「守卫报红 ≠ 守卫真命中」**：P1-2 指出的 `m_g22/m_g23` 变异是「删全部调用行」，本波若为此放宽判据换绿，会重蹈知识库 §5 的 `assert_red` 假 PASS 陷阱。重跑 selftest 时**必须能看到违规行本身**。
5. **「判据写进方案 ≠ 判据被执行」**：四份外审合计给出 40+ 条「W8 验收判据」，但现役 `applied=true` 零真机证据、`feature-chat` 集成测试 0 条（§1.4a）。本波 ⑤ 的「本地 UI/功能 + adb 真机通过才推」应把**功能判据**与**正确性判据**分开写，别把 ⛔ vacuous 项混进「通过」清单。
6. **「记忆 ≠ 交付物」**：本清单若只停在 `docs/analysis/`，下一波仍会重读四份外审再踩一遍。**建议主理人把 §0.2 证伪清单、§三 Q1–Q14、§四 H1–H10 编号、§五唯一编号源声明，同步写进 W8 handoff / 台账**（Q4/Q14 同车），否则等于没沉淀（知识库 §6）。
7. **数字口径三套并存**（Σcaps 43400/46000/2900、selftest 30/28、@Test 696/≥740、OPEN 24/23）：外审互相抄、台账统计表自己错（N1）。**本波起所有计数一律以 E2–E6 复跑输出为准**，文档引用标「实测/复跑」而非「外审称」。

### 7.3 限制声明（如实标注）

- 本清单为**静态只读核实**：`git status` 确认工作树干净（仅 `docs/analysis/` 未跟踪），**未执行 Gradle/全量 JVM 单测/真机/CI**（本席环境无 Android SDK/OPPO PDRM00）。E2（arch-guard，纯 shell）、E3（严格 grep 计数）、E4/E5（awk/grep）、E6（`wc -l`）均为**本席本地实跑**，输出可直接复跑；`696 = EXPECTED_TESTS` 是**静态对账**，不等于「全量单测通过」。
- 凡「真机行为 / `applied=true` / 缓存命中 / 1M 档 / KV cache」结论，一律标 **⛔ 需本波 ⑤⑥ 取证**，不冒充已验证（`AGENTS.md` §二.3）。
- `docs/analysis/w8-provider-probe.md` 是**本次会话主理人 Phase 0 产物**（mtime 2026-11-05 15:12，未跟踪），属**中转证据**；其 `dots` 实测结论若要写进产品承诺，须按 `w8-provider-probe.md:46-49` 的 `max_tokens` 前置条件**原样重放 ≥2 次**（`handoff-20261104-wave7.md:92` 已定「须重放 ≥2 次才写稳定结论」）。
- 四份外审的行号基准均自称 `578f7e4`，与现役 HEAD 一致；本清单所有 `文件:行` 均指 `578f7e4`。发现的行号/数值漂移（F1、N5：`GenerationEvent.kt:136→142`、`SettingsRoute.kt:206→225`、`SessionFootprint.kt:67→28`、`SwitchDecision.kt:16→11-12`、`feature-chat/src/test` 8→7 文件、`w7-line-budget-ruling.md:61` 的 `ChatViewModel 484/500` → 实跑 **487/500**）已逐条订正。C13.1 引用的 `LayeredContextCompressor.kt:254` `AgentLogStore.warn("layered compress: mode=L1_FALLBACK reason=" …)` 已逐字复核命中。
- **本报告不修改任何源码**；Q/H 编号均为**建议**，波号（`W9-0x`）与「W3-05 是否独立成 W8.5」仍待主理人裁决（复审41 §五三项 OPEN 中的 C2 波号项）。

---

---

## 八、复核增补：方案席（沈思远）回赠 4 条新发现的独立回树核实

> 来源：`solution-strategist` 消息（方案稿 `docs/analysis/w8-solution.md`，499 行，只读）。按 `AGENTS.md` §二.2 与知识库 §6「描述/报告都可能不成立，只作线索，动手前回源码核验」，四条全部独立实跑复核。本节为 §一–§七 的**增补**，不推翻既有判定；凡订正处标明对 §三/§四/§六 的净影响。

### 8.1 逐条判定

| # | 方案席主张 | 回树核实（文件:行 / 复跑输出） | 判定 | 对本文的净影响 |
|---|---|---|---|---|
| **R1** | 设备侧 1M 被内核硬熔断：`AgentPolicy.kt:228 maxRunPromptTokens = 400_000` + `RoundHeadGate.kt:42` 在 400k 处 Trip；`AppContainer.agentRunnerFor` 未覆盖该值 ⇒ 单个 ~995k prompt **请求根本发不出去**；W7 PC 侧 1M 双通过是 `_w7/w7matrix.py` 直连 curl 绕过 app 内核 | 源码三处**全部命中**：`core-kernel/.../AgentPolicy.kt:228`（`val maxRunPromptTokens: Int = 400_000`，KDoc `:223-227`「单 run 累计 prompt token 硬上限…超限 ⇒ `TerminationReason.BreakerTripped`」）；`core-agent/loop/RoundHeadGate.kt:42-46`（`if (state.promptTokens > policy.maxRunPromptTokens) … GateVerdict.Trip`）；`core-data/AppContainer.kt:265-281` `AgentPolicy(maxContextTokens = windowCap, compressContext = compress)` **确未传** `maxRunPromptTokens`。`_w7/w7matrix.py:3-14` 明写「纯事实采集，不改仓内源码、不跑 gradle」，复用 `_w6/matrix.py`/`probe.py`，密钥从 `NVAPI_KEY`/`OPENROUTER_KEY` 环境变量读 ⇒ **PC 侧直连 HTTP、绕过 app 内核成立**。**但两处订正**：① **时序**：`core-agent/loop/TurnLoop.kt:58-78` 顺序为 `preStep.enforce`（`:60`）→ `gate.check`（`:62`）→ `beginRound`（`:75`）→ `generation.run`（`:78`）；`gate.check` 判的 `state.promptTokens` 是**本 run 累计**（`RunState.kt:77` KDoc、`:237 promptTokens += prompt`），**首轮 = 0 ⇒ 首轮请求会真实发出**，usage 回报后才累加、**第 2 轮**轮头才 Trip ⇒「请求根本发不出去」**不成立**。② **更早的一道闸被漏**：`preStep.enforce` 在 `gate.check` **之前**，其 `hardContextBudgetTokens(endpoint, policy)` 用 `policy.maxContextTokens = windowCap`；W7 真机 `settings.preferences_pb` 实测 `max_context_tokens = 32 000`（`docs/notes/w7-provider-matrix.md:293`，分母 `27.9K`）⇒ `hard ≈ 27 904`，995k 上下文会在发请求**前**被 `PreStepBudget.kt:86/111/123/159-164` 收缩或 `trip`（`context_overflow_prevented` + `emitBreaker`）。**这才是设备侧第一因** | ⚠️ **部分属实**：源码命中、结论（1M 判据须标 ⛔/⚠️）✅；**因果链过强且漏一层**。另：`_w6/import-1m.jsonl` 实跑 `bytes=5221133` / `lines=30` / `BOM=False`（首 3 字节 `{"r`）✅，但 `max_line_chars` 我实跑为 **180067**（`splitlines()` 不含换行），方案席写 **180068** ⇒ 差 1，属含/不含换行的口径差，非缺陷 | **§四 H1/H3 补第三层**：设备侧 1M 有**两条独立阻断** —— (a) 档位仍 32K ⇒ `PreStepBudget` 轮前收缩/trip；(b) 切到 1M 且首轮真发 995k ⇒ 第 2 轮起 `RoundHeadGate` 累计 400k 熔断。(c) 单指针压缩（H3）。**⑥ 的 1M 判据必须标 ⛔ vacuous 并写明「两条独立阻断」，不得只归 `maxRunPromptTokens`**。**不采纳**方案席「不改 core-agent 策略」之外的因果表述 |
| **R2** | Phase 0 实测与现役解码路径不同构：全部 `_w8_*` 响应 `stream=false` / `chat.completion` / `choices[0].message`；现役 `OpenAiCompatibleAdapter.kt:76` 恒发 `stream=true`、`:79 include_usage=true`、`:103` **只读 `choices[0].delta`** ⇒ 「思考如何进 `ReasoningDelta`」流式路径零证据；与 §六 G19「⑦ 接日志」是两件事 | 源码命中：`:76 put("stream", true)`、`:79 put("stream_options", …include_usage…)`、`:103 val delta = choice?.get("delta") as? JsonObject`、`:104 if (delta != null)`、`:106-108 reasoning_content`/`reasoning` → `ReasoningDelta`；**全文件 `grep -nE '"message"'` = 0 命中 ⇒ 零非流式兜底**。仓外实跑（`D:/WBfil/2026-09-18-18-27-37/`）：21 个带 `object` 的 `_w8_*` 响应中 **20 个 `chat.completion`**（另 1 个 `list` = models 列表），`choices[0]` 键 = `['index','logprobs','finish_reason','native_finish_reason','message']`（**无 delta**）；4 个 `_w8_cache_req*.json` 请求体 `stream: False`。**措辞订正**：响应体**没有 `stream` 字段**（Python `d.get('stream') = None`），非流式由 `object=chat.completion` + `message` 判定；`stream=false` 是**请求体**字段。**加重（交叉 H9/NEW-E）**：把 Phase0 响应喂给现役 `decodeEvent` ⇒ `delta == null` ⇒ `out` 空 ⇒ `ProviderAdapter.kt:192 if (state.textChars == 0) return FinishReason.STOP` ⇒ **零事件被静默标成正常停止**，与 `EndpointProbe.kt:30-34` KDoc 描述的「HTTP 200 HTML 错误页 → 空流 = STOP」同型，是 NEW-E 短路的**第二个触发面**。另：`usage` 末帧解析在 `OpenAiCompatibleAdapter.kt:132-141`（`obj["usage"]`，独立于 `choices`）⇒ 流式 usage **有代码、零实测**，须与「思考零证据」分开写 | ✅ **属实**（两处措辞订正 + 一处加重）。与 §六 G19 确为两件事 ✅：G19 是**安全日志/凭据拼接**守卫（`arch-guard.sh:1190-1228`），不是解码证据，不混 | **§六 新增一条本波判据**：`decodeEvent` 改动必须以「**流式对照实测**」为出口，不补 = §3.2 的 `ReasoningDelta` 判据 ⛔ vacuous（且会把非流式 200 读成正常 stop）。**§四 H9 补**：断流 STOP 修复须同时覆盖「非流式/无 delta 响应」形态，单测加一条 `choices[0].message` 帧 ⇒ 不得产生 `ModelStopped`。方案席 §7.3 T1 建议补 `delta.reasoning_details[].text`（现役 `:106-107` 不认）✅ 背书。**🔴 本行「判定 ✅ 属实」、本净影响栏的「§六 新增流式对照出口判据」与「背书补 reasoning_details 兼容」三处已由 §九 事后订正撤回/改判（R2 → ⚠️ 部分属实），一律以 §九 为准** |
| **R3** | `EndpointProbe.kt:57` 固定 `maxTokens=8`，dots 默认开思考 ⇒ `judgeProbe:115-119` 把 `ReasoningDelta` 算内容证据而**假绿**，或 `enabled:false` 正文为空而**假红**；建议 probe 与 UI 共用「输出预算 ≥64」常量 | 全部命中并可加重：`core-data/.../EndpointProbe.kt:57` `SamplingParams(maxTokens = 8, temperature = 0.0)`；`:65-71 firstOrNull { }` 谓词 `isContent = TextDelta \|\| ReasoningDelta \|\| Usage`，`:69 sawContent = true`；`judgeProbe` @ `:115-119`（`:117 sawContent -> null` 判绿、`:118 else -> EMPTY_STREAM_DETAIL` 判红）。**关键加重**：`firstOrNull` 在**第一个匹配事件就取消上游**（KDoc `:25-26`）⇒ probe **永远看不到完整回答**，判据本就是「收到任一内容事件」；因此**只把 8 改成 ≥64 仍留假绿**（`ReasoningDelta` 单独即可 `:117` 判绿）。Phase0 佐证：`w8-provider-probe.md:30` `max_tokens=64` 才得 `'\n\nPONG'`，`:33` `max_tokens=64` + tools ⇒ `length`「判据自身失效」，`:34` `max_tokens=512` 才 `tool_calls` 真实可用 | ✅ **属实且修法需收紧** | **§三 新增 Q15（同波收，非挂账）**：EndpointProbe 判据自证。`core-data/EndpointProbe.kt:57` 预算 8 → ≥64（与 UI 共用常量）、`:66-68` 谓词改为 `TextDelta \|\| Usage`（`ReasoningDelta` **单独不算通过**）+ `EndpointProbeTest.kt` 补 2 条用例（纯 `ReasoningDelta` ⇒ 不判绿 / `maxTokens≥64` 样本得 `TextDelta` ⇒ 判绿）。约 20 行、文件域 `core-data` 与功能波零冲突，且**与功能域重合**（① 高级配置改 `ReasoningStyle` 后「测试连接」按钮必须仍可信）⇒ 应同波收，不是 T10 挂账 |
| **R4** | `Generation.kt:80` `SamplingParams.reasoningEffort` 全仓零生产消费点（只有 `RemoteEndpoint.kt:131` 那份被 `OpenAiCompatibleAdapter.kt:182` 消费）⇒ NEW-G 家族第 6 例，标死字段挂账、禁止引入第 3 个 effort 字段 | 命中：`core-model/.../Generation.kt:80 val reasoningEffort: String = "medium"`，其 KDoc `:79` 写「**仅当端点 `reasoningStyle == REASONING_EFFORT` 时生效**」。全仓 `grep -rn "reasoningEffort" --include=*.kt .` 仅 6 处：`Generation.kt:68`(KDoc)、`:80`(定义)、`RemoteEndpoint.kt:131`(定义)、`OpenAiCompatibleAdapter.kt:182`(`body.put("reasoning_effort", endpoint.reasoningEffort)`)、`OpenAiCompatibleAdapterTest.kt:148/152`(测的是 `RemoteEndpoint.copy(reasoningEffort=…)`)。⇒ `SamplingParams.reasoningEffort` **零生产消费点 ✅**，且其 KDoc **声称生效但无消费者** —— 这是 C12 专册「注释与实现打架」的**第 7 例**（我 §0.3 N2 只收到第 5/6 例）。G17 边界佐证：`SamplingParams` 是 `core-model/src/main` 下的 `@Serializable` 类（`arch-guard.sh:1111` G17 扫描面内），但字段名不含 `apiKey/secret/password/token` ⇒ **G17 判不出语义死字段** | ✅ **属实**，并补 C12 第 7 例 | **§三 Q7 扩为 Q7'**：同波顺手改 `Generation.kt:79` KDoc 为「未接线，勿用；唯一真值源 = `RemoteEndpoint.reasoningEffort`」(1 行，并入 Q7 的注释改写批，0 新增 @Test)。**§四 H6/W9-06 清剿清单**明确列入 `SamplingParams.reasoningEffort`（第 6 例）。**背书**方案席「禁止新增第 3 个 effort 字段」：本波 ① 的「默认思考强度 / 支持的思考强度」必须复用 `RemoteEndpoint.kt:131` 单真值源，不得在 `SamplingParams` 或新 DTO 里再开一份（W5-01「第二把尺」同型） |

### 8.2 复跑命令（本节全部判定可复现）

```
R1  grep -rn "maxRunPromptTokens" --include=*.kt .                     → 8 命中，含 AgentPolicy.kt:228 = 400_000
    sed -n '58,78p' core-agent/.../TurnLoop.kt                          → preStep.enforce(:60) 先于 gate.check(:62)
    sed -n '228,248p' core-agent/.../RunState.kt                        → promptTokens += prompt（本 run 累计，首轮 0）
    sed -n '265,281p' core-data/.../AppContainer.kt                     → AgentPolicy(maxContextTokens, compressContext) 未覆盖
    python -c "b=open('_w6/import-1m.jsonl','rb').read(); ..."          → 5221133 B / 30 行 / BOM=False / max_line=180067
    grep -n "max_context_tokens" docs/notes/w7-provider-matrix.md        → :293 = 32 000
R2  sed -n '70,115p;125,145p' core-engine/.../OpenAiCompatibleAdapter.kt → :76 stream=true / :79 include_usage / :103 只读 delta / :132-141 usage 末帧
    python -c "…glob('_w8_*resp*.json')…"                                → 20 × chat.completion + 1 × list；choices[0] 键含 message 不含 delta
    python -c "…glob('_w8_*req*.json')…"                                 → 4 × stream=False
R3  cat -n core-data/.../EndpointProbe.kt                                → :57 maxTokens=8；:65-71 谓词含 ReasoningDelta；:115-119 judgeProbe
    grep -nE "max_tokens=64|max_tokens=512" docs/analysis/w8-provider-probe.md → :30/:33/:34
R4  grep -rn "reasoningEffort" --include=*.kt .                          → 6 处，仅 RemoteEndpoint.kt:131 被 :182 消费
```

### 8.3 结论

四条**无一条证伪**（方案席这批外审回赠质量高于四份原始外审）：R2/R3/R4 ✅ 属实，R1 ⚠️ 部分属实（源码与最终结论对，因果链需重写）。**净影响合计**：§三 新增 **Q15**（同波收，EndpointProbe 判据自证）、Q7 扩为 **Q7'**（+ C12 第 7 例 KDoc）；§四 H1/H3/H6/H9 各补一层；§六 新增「流式解码对照」出口判据。**没有一条改变 §0.1 的总判定或 §一 的逐条判定表** —— 主理人裁决方向、6 条证伪、W8-03 快修证伪、编号撞车结论全部维持。

---

## 九、事后订正（**优先于 §八.1 R2 与本文此前所有对应表述**）

> 输入：方案席 `docs/analysis/w8-solution.md` **§12 事后订正**（953 行）、主理人 `docs/notes/w8-plan.md` **§二/§八**、`docs/analysis/w8-provider-probe.md` **§六**（9 组 `stream:true` 实测）与仓外 `_w8/` 原始资产。
> 纪律：`AGENTS.md` §二.2 证伪优先、知识库 §6「引用外部结论前回源码/实测核验」。本席**独立复跑** 9 组 SSE 与全部引用行号，不照抄任何中转文档。
> **§九 优先于 §八.1 R2；§八.1 R1/R3/R4、§一–§七 全部维持。**

### 9.1 撤回清单（我 §八.1 R2 中被证伪的两处推断）

| # | 我 §八.1 R2 原表述 | 独立核实（可复跑） | 判定 | 处置 |
|---|---|---|---|---|
| **W1** | 「方案席 §7.3 T1 建议补 `delta.reasoning_details[].text`（现役 `:106-107` 不认）✅ 背书」；并据此把「流式路径思考进 `ReasoningDelta` 零证据」列为待补实测缺口 | 主理人 9 组 `stream:true` 实测资产真实存在：`_w8/req-*.json` ×9（实跑 **全部 `stream=True` + `stream_options={"include_usage":true}`**）、`resp-*.sse` ×9、`parse-*.json` ×9、`stream_probe.py`。独立解析 `_w8/resp-basic.sse`：17 个 data 帧、含 `[DONE]`；**首帧 `delta` 键 = `['content','reasoning','reasoning_details','role']`**，`reasoning='The'`(3) 与 `reasoning_details[0].text='The'`(3) **逐字等长**，`reasoning_content` 键不存在（恒 0）。9 组汇总全部 `DONE=True`、`last_usage=True`；`off`/`incl`/`image`/`jsonfmt` 四组首帧无 reasoning（与关思考/仅隐藏/视觉/JSON 形态一致）。⇒ 现役 `OpenAiCompatibleAdapter.kt:106-108` 的 `reasoning_content ?: reasoning` **回落命中 `delta.reasoning`，思考本就能进 `GenerationEvent.ReasoningDelta`**；OR 的 `reasoning_details` 是**同一份思考的冗余双通道**，若再加解析会造成重复 `ReasoningDelta`（伪工作量） | ⛔ **证伪（推断）**；R2 的**事实部分**（第一批 20 份探针全非流式、adapter 恒 `stream=true`、`:103` 只读 delta、零 `message` 兜底）✅ **仍属实** | **撤回「补 `reasoning_details` 兼容」背书 + 撤回「流式零证据」**。R2 判定由「✅ 属实」改为 **⚠️ 部分属实**。与 `w8-solution.md §12.1 C1`、`w8-plan.md §二`、`w8-plan.md §八.2` 一致：**本波不改 `decodeEvent`** |
| **W2** | R2 净影响栏「**§六 新增一条本波判据**：`decodeEvent` 改动必须以『流式对照实测』为出口，不补 = §3.2 的 `ReasoningDelta` 判据 ⛔ vacuous」 | 该判据的**前提（没有流式证据）已由 9 组实测消除**；`w8-plan.md §八.2` 明确「已闭合并改判…不改 `decodeEvent`…审查席 §六 出口判据前提已消除；PC 侧 9 组流式响应已真实行使 `delta.reasoning → ReasoningDelta` 的取值路径，真机 D 判据再验一次端到端」 | ⛔ **撤回（前提消除）** | **删去这条新增判据**；§六 仍维持原六项（G17/G22/G23/EXPECTED_TESTS/G10/VACUOUS_BASELINE）。改为登记：**真机端到端 `ReasoningDelta` 判据 = 验收项，不是新增守卫、不是新增代码改动** |

### 9.2 不撤回、且被主理人采纳登记的部分

- **R2 的加重（交叉 H9/NEW-E）保持成立**：把非流式响应喂现役 `decodeEvent` ⇒ `delta == null` ⇒ `out` 空 ⇒ `ProviderAdapter.kt:192 if (state.textChars == 0) return FinishReason.STOP` ⇒ **零事件被静默标成正常停止**。`w8-plan.md §八.2` 已把它登记为 **W9-09（断流 STOP）的证据补充，本波不修**。⇒ §四 H9 的「单测加 `choices[0].message` 帧 ⇒ 不得产生 `ModelStopped`」建议**仍挂账 W9-09**，不进本波。
- **R1 / R3 / R4 全部维持**：`w8-plan.md §八.1/§八.3/§八.4` 逐条采纳（1M 两条独立阻断、Q15 同波收、R4 KDoc 顺手修 + 字段挂 W9-06）。
- §一–§七 的 6 条证伪、W8-03 快修证伪、W9-0x 编号方案、§六 六项判据**全部维持**。

### 9.3 本席独立发现的两处事实订正（给方案席 / 主理人）

| # | 对方表述 | 回树核实 | 判定 | 建议 |
|---|---|---|---|---|
| **W3** | 方案席回赠：`core-design/GlassSegmented.kt:33` 用 `clearAndSetSemantics` ⇒ uiautomator dump 读不到分段项文字/bounds，五档强度选择器若用它真机判据会 ⛔vacuous | **① 行号错**：`:33` 是 `LocalLayoutDirection` import、`:34` 才是 `clearAndSetSemantics` import，**使用点在 `:366`**（`.clearAndSetSemantics {}`）。**② 因果错**：`:362-369` 该修饰符挂在**隐形回显行**（`alpha(0f)` + `layerBackdrop` 玻璃折射层），注释逐字「回显行不参与无障碍 / 触摸（**真实交互在可见行与胶囊**）」；清除 echo 层语义是**刻意的正确做法**（否则无障碍树出现重复文字节点）。**可见分段项**在 `core-design/GlassSegmentedItem.kt:70-90`：`Modifier.clickable(role = Role.Button, onClick)` + `Text(text = text)` ⇒ **有可点击语义 + 文本语义**，并非读不到文字 | ⛔ **证伪（行号 + 因果）**；但**规避方案本身 ✅ 应采纳** | 仍改用 `GlassSettingRow` 列表（`SettingsScreen.kt:291-311 ContextWindowRows` 同款已验证通道：`GlassSettingRow.kt:96-103` 有 `Text(title)`/`Text(subtitle)`，`trailing = { ValueText(...) }` 给选中态可读文本），但**理由改写**为：① 知识库 §2「uiautomator dump 对 Compose **bounds** 不可靠 ⇒ 点按判据以 journal/应用日志为准」；② 复用六档窗口已在用的已验证通道；③ 五档需「选中态 + 降级原因」可读文本。**不要**写成「`GlassSegmented` 清了语义所以读不到」。此条应进 `w8-plan.md §六 V9` 的真机判据口径 |
| **W4** | `w8-provider-probe.md §六.1`（主理人）与 `w8-solution.md §12.1 C1`：usage 在 **`choices: []` 的独立末帧**到达 | 独立复跑 9 组 SSE：**`empty_choice_frames = []`（9 组全部没有任何 `choices:[]` 帧）**；`usage_frames` 均为**最后一帧**，而该帧 `choices` **有 1 个元素**（含 `finish_reason=stop` + `delta.content=""`）。例 `resp-basic.sse` 末帧：`choices=[{index:0, delta:{content:'',role:'assistant'}, finish_reason:'stop', …}]` 与 `usage={prompt_tokens:19, …}` **同帧** | ⚠️ **事实描述错**（结论不变） | 改为「usage 与 `finish_reason` **同帧**、此时 `choices` 非空，但现役 `OpenAiCompatibleAdapter.kt:132-141` 的 `obj["usage"]` 解析**独立于 `choices` 分支**（在 `if (delta != null) {...}` 之外），故两种帧位都能读到 ✅ ⇒ **不改 `decodeEvent` 的结论维持**」。`w8-provider-probe.md §六.1` 与 `w8-solution.md §12.1` 相应行请订正 |

### 9.4 引用精度订正（知识库 §6 引用纪律）

方案席 `w8-solution.md §12.2` 两处引用本文的章节号不成立，正式转交接资产前请改准：
- 「1M 夹具最长行 … 依据 `w8-review-triage.md §八.5`」⇒ **本文无 §八.5**；实际在 **§8.1 表格 R1 行**（`180067`）。
- 「probe 修法 … `w8-review-triage.md §八.3`」⇒ probe 完整修法在 **§8.1 表格 R3 行的「净影响」栏**（Q15：预算 ≥64 + 谓词 `TextDelta||Usage` + 2 条 JVM 用例）；§8.3 只是结论段。
- `w8-plan.md §八` 引用「`w8-review-triage.md` §八」整体 ✅ 无误。

### 9.5 复盘

- **「证据缺口 ≠ 实现有洞」**：我 §八.1 R2 与方案席 §3.3 都把「第一批探针全是非流式」这个**取证缺口**直接外推成「现役解码链路断链」。主理人补跑 9 组 `stream:true` 即证伪。这正是知识库 §4「输出侧看着像坏，先查输入侧喂了什么」与 §6「判据自身先被验证」的现场。**审查席自己也会犯这个错** ⇒ 已按 `AGENTS.md` §二.2 把证伪写回文档，不只留在聊天里。
- **「引用外部结论前回源码/实测」仍有净产出**：本轮独立复跑虽确认了 9 组实测结论，仍查出 **W3 行号+因果双错**、**W4 usage 帧位描述错**两处。⇒ 中转文档（含主理人探针底稿、方案席 §12）一律只作线索。
- **净影响合计**：§八.1 R2 → ⚠️ 部分属实（撤回 2 条推断、保留非流式→STOP 加重）；§六 **不新增**「流式对照」判据；新增 W3/W4 两处事实订正。**§0.1 总判定、§一 逐条判定表、6 条证伪、W8-03 快修证伪、W9-0x 编号方案全部维持。**

### 9.6 本节复跑命令

```
W1/W2  python: glob('_w8/req-*.json') → 9 × (stream=True, stream_options={'include_usage':true})
       python: '_w8/resp-basic.sse' → 17 data 帧 / [DONE] / 首帧 delta.keys=['content','reasoning','reasoning_details','role']
               reasoning='The'(3) == reasoning_details[0].text='The'(3)；reasoning_content 键不存在
       python: 9 × resp-*.sse → DONE=True、last_usage=True、last_choices=1、empty_choice_frames=[]
W3     grep -n "clearAndSetSemantics" core-design/.../GlassSegmented.kt   → :34 import / :366 使用
       sed -n '362,369p' …                                                 → 「回显行不参与无障碍/触摸」
       grep -nE "clickable|Text\(|Role.Button" core-design/.../GlassSegmentedItem.kt → :74/:77/:89
W4     python: 9 组 empty_choice_frames=[] / usage_frames=[last] / 末帧 choices=1（含 finish_reason）
```

*严质衡 · LiquidAgent 专家团审查席 · W8 Phase 1 外审回树核实（含 §八 方案席回赠复核 + §九 事后订正）· 基线 `578f7e4` · 只读交付*
