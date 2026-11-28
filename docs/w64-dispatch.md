# W64 派单卡 — journal 合流判据修复 / N-1 定谳真机格 / 外审 6 行销账

> 波号：W64（2026-11-28）｜基线 HEAD `25340a8`｜现役仓 `harness-v2`（包名 `com.rickeal.liquidharness`）
> 用户指令要点：读记忆 + 上波 handoff + OPEN 行；按前波裁决计划推进；**不要「判据升级→全空转→重新取证」的零改动循环**；API nvidia 免费多模型密钥留真机；MCP 内网设备关闭暂不测；先本地构建 + 真机 adb（唤醒三步法），通过后推仓 + CI。
> 本波销账集合 = **6 行（W34-01/02/03 + W35-01/02/03）**——首次条件销账，见台账 W64 段。

## 一、外审分歧裁决（第 0 车，四份报告逐条给值）

### 裁 1 · N-1「争议键真机格」处方 = **部分采纳，改判据对象**
- 外审《W60-W63四波审查》§四.1 处方：「W64 第 1 车把 W60 真机争议键格的键换成 `parallel_tool_calls` 实跑一次——保存链 RED 即实锤、GREEN 撤回主张，一次实跑终结 13 波分歧」。
- **主理人回源证伪该处方的分歧定性**：历次 N-1 的主张从来**不是**「`parallel_tool_calls` 会被保存链误拒」。台账 W54 段（:851）原文是「N-1 `parallel_tool_calls` **定谳用例**」——W54 已在 `EndpointCapabilitiesTest` 落地保存链 JVM 用例（:239-254「`parallel_tool_calls=false` 走『解析 + 校验』这条保存链应能通过」）并真机过；W60 段 P0-2 裁决原文「争议键判 ✅ 超出自定判据 = 降限定态——该格 ✅ 仅对**保存链**成立、不冒充**运行时请求实证**；步 8 标题『真实请求实证』未行使（**请求体探针 `parallel_tool_calls:false` 无 JVM 用例、无设备探针**）」。现役源码亦证：该键在 `ALLOWED_EXTRA_BODY_KEYS`（`EndpointAdvancedValidation.kt:68`），保存链必过、外审「not in ALLOWED ⇒ BLOCKED」推演与现役树相反（对它那一版树的时点差，不指控）。
- **故本波真机格的证明对象改为运行时请求体**：端点开自定义协议 + `extraBody` 写 `parallel_tool_calls=false` 保存后发起一次真实请求，取证设备侧日志中**请求体序列化后含 `"parallel_tool_calls":false`**。判据锚「请求体带键」，不锚「保存链不拒」（后者 W54 已定谳、重测即空转）。
- 取证通道：`OpenAiCompatibleAdapter.applyCustomProtocol`（`core-engine/.../OpenAiCompatibleAdapter.kt:109-116`）将 `extraBody` merge 进请求体；设备侧 agent 日志有请求起点行。若日志不含请求体明文，降级判据 = 米特代理拦截不可行（无端点标识入证）⇒ 用「响应端无 `parallel_tool_calls` 相关错误 + 保存链往返 + `applyCustomProtocol` 控制流 JVM 级已有用例」组合，如实标 ⚠️ 部分。**不伪造「请求体已验」**。

### 裁 2 · wellFormed 合流形态（W63 外审 P1-1）= **收（本波最高优先代码项）**
- 采纳《W62W63深度审查》§三处方：`AgentRunJournal.wellFormed` 加「行内 ` at=` 出现次数 == 1」约束（等价于禁止 kind 之后再次出现 `run_` 合流尾巴；两形态选一，实现取 `count(" at=")==1`——它同时拦「前一条完整只缺 `\n`、后一条整行接在 `at=<digits>` 之后」的两种合流样本，且不破坏 `at=17` 截断宽容）。
- KDoc 限定 2/限定 3 后果表述同车订正（现写「后一条 run_settled 不会因此被整条吞掉」与实证相反：合流行被误判合格 ⇒ 后一条 settled 丢失 ⇒ 多弹一次恢复卡；方向仍 fail-safe，但「不丢现场」的理由要写实）。
- **变异自证**：改后用合流样本（前一条完整 + 无 `\n` + 后一条接尾）注入 `_w64` 工作区副本跑 Python 等价复刻，修前判 True、修后判 False，输出入 `evidence/raw/w64/`。

### 裁 3 · journal JVM 用例形态 = **受控破例 904 → 905（新增一个测试方法）**
- 两外审建议相反（《W63深度审查》§五.1 荐受控破例 904→907；《W62W63深度审查》§九 荐维持 904 改既有用例）。**裁决：走受控破例 +1**。落地时主理人发现「改既有用例形态」存在**不可空转的硬约束**：四态样本要钉「合流行必红」，必须是一个**可独立失败的断言单元**——若塞进既有方法（如 `runEmitsFinishedAndResetsBusyFlag`），该方法的既有语义会被稀释且失败归因混叠（一条挂全部挂）；而「在既有类里新增方法」在 CI 的 `^\s*@Test\b` 口径下就是 +1，任何「不改计数」的写法都只能依赖 KDoc 文字（W63 深度审查 §3.3 已实证 KDoc 内 `@Test` 字样会被裸 grep 误算、被 strict 口径排除 ⇒ 判据不进计数也不进执行）。**两难取真**：判据空转风险 > 冻结字面。故：
  - 新增 `AgentRunnerTest.wellFormedRejectsConflatedAndTruncatedJournalLines`（**一个**方法承载四态断言：完整行 True / `at=17` True / 截断 payload False / 合流行 False×2）；
  - `build.yml` 的 `EXPECTED_TESTS` 同提交对账 904→905，增量注释写明「受控破例」理由与 W60 段 P0-2 裁决的边界区分（那条否决的是「请求体探针」，本条是恢复卡 fail-safe 判据）；
  - M3「冻结净 0」的处置：本波同时不动其余任何测试文件（零删零改），净增 = +1 是**显式声明**而非静默破例；外审两份报告其一（W63 深度审查 §五.1）明确建议「这是安全相关判据，值得一次受控破例」。若外审不认可，回滚仅动 2 文件 + 常量。
- 其余三条样本（`at=17`/截断 payload/完整行）与合流样本同方法承载——它们是同一判据的四态面，拆四个方法会把 +1 变 +4。

### 裁 4 · `RunState.forRequest` 移 try 内（W63 P3-2 残余窗口）= **收（小改）**
- `AgentRunner.kt:99` 的 `state` 构造在 `try` 之前；`forRequest` 内 `newFreezeClock?.invoke()` 是装配期常量调用、异常概率极低，但 W62 已把 `runStarted` 移进 try，此处是同型残余。修法 = `state` 提为可空 `var`，`try` 块内构造 + `state?.let` 或直接把构造移入 try 后用局部 val（`forRequest` 抛异常时 finally 里 `runSettled` 无 state 可写——原行为也写不出，改为 finally 内判空跳过）。**控制流先在纸面走一遍再动手**（防 unlock 链回归）：
  - 现状：`tryLock` → `forRequest`（try 外）→ `try { runStarted … } finally { runSettled(state…) + 清理 + unlock }`。
  - 目标：`tryLock` → `try { val state = forRequest(...); runStarted…; … } finally { 清理 + unlock（runSettled 仅当 state 已构造）}`——需要 state 提到 try 外作可空 var，finally 里 `state?.let { journal?.runSettled(...) }`。这样 `forRequest` 抛异常也必经 finally 解锁，不复活「mutex 永锁」。
- 此项零新增判据（变异面 = W62 已有取消链真机格回归覆盖 unlock；`forRequest` 抛异常路径 JVM 不可注入——FreezeClock 工厂是 `() -> FreezeClock` lambda，可注入抛异常 lambda 进 `AgentRunner` 单测——**但新增用例破 904**，故真机面以「取消链回归 + 冷启动正常」覆盖，异常路径标 ⚠️ 推演级）。

### 裁 5 · 外审 6 行销账（W34-01/02/03 + W35-01/02/03）= **做（本波第二主项）**
- 依 W63 交接 §2.3「审查席立场：依 M1 自主处置」+ 用户本波指令「继续按照前面的裁决计划执行」。W63 阻塞项两条的解法：
  - **① 必红逐类复跑入仓**：k6 十一类必红样本从 `evidence/w32-matrix-summary.json` 生成（`_w34/mut/` 工作区副本），本波在**仓库根**按 `w29-closeout-checklist.md:368-396` heredoc 原样重跑十一类 + k7 判据 7 必红（`RED closed without current-wave pointer`），逐类输出脱敏后入 `evidence/raw/w64/g8_k6_mustred_w64.txt` / `g9_k7_mustred_w64.txt`。**正控先行**：先跑 w32/w33/w34 三制品正常态（已实测 OK×3 + k7 OK×2，输出入 `g8` 头部）。
  - **② §2.5a 元引用口径**：裁定 = **「描述判据修正本身的元引用不计入悬空指针口径；悬空指针 = 指向不存在章节的引用」。** 依据：W35-01 出口判据的语义对象是「外部审查按 §2.5a 找 W34 实跑原文得 404」——W35 当时已把两处真悬空改指 `§2.5 + §2.2`（`w35-acceptance.md:71` 记录），现役 13 处命中全是「描述本次修正」的元引用（台账 W35-01 行自身 / W35 交接 / W35 验收物 / W63 文档 / W36-02 行内），**无一指向不存在的章节**（W34 验收物现存 §2.5 与 §2.2）。口径写进 W35-01 销账注记，并附现役命中清单逐处分类（13 处 → 元引用 13 / 悬空 0）。此裁定属**判据执行口径的解释**而非新判据方向——W35-01 出口判据字面「全树 §2.5a 0 命中」按「悬空指针 0 命中」语义执行，W36-02 出口文字已写「`§2.5a` 注记引用除外」（台账 :670/:688 可溯），故本裁定与既有口径一致、非新裁决域。若外审不认可，6 行回 OPEN 只动状态列两字符——可逆。
  - **销账形态**：6 行状态 `OPEN` → `CLOSED`，行内追加「**W64 复核指路**：判据 6 十一类必红 + 判据 7 必红逐类复跑 `evidence/raw/w64/g8_k6_mustred_w64.txt`/`g9_k7_mustred_w64.txt`；§2.5a 口径 = 悬空指针 0（元引用除外，分类清单见 g10）；正常态 k6×3 + k7×2 OK（g8 头部）」；**不回改历史四态原文**。台账状态列 39 → 45 CLOSED / 166 → 160 OPEN。
- **W35-02 例外**：W35-02 出口判据是 `W9-01` 行含 `applied`+`dropped=9`+`net=-256`+`:407`+`savingChars`（已逐字核对在行内）+ 证据 reading 一致——本波逐项复核后同批销。若某字面缺失则该行不销、如实留 OPEN。

### 裁 6 · 四处计数订正（P1-2）+ 历史时点注记 = **收（纯文档第 0 车）**
- W62 派单卡抬头 `2/4` → 补记「波末实态 5 提交 / 8 条 run（含 2 次纯文档回填）」；W63 验收物补「6 提交 / 7 条 run」计数段；补 W63 tip `25340a8` 的 Secret run `46114493385`（W63 §2.9 已补 W62 的 `46026829837`，本波补 W63 自己的末笔，同型第 N 次收口）；W51 `g3_selftest.txt` 改动补 sha256 前后注记（`cc3cc839…` → `d4f25a91…`，实际值以实跑为准）；G20 142→143 成因一行注记（W62 handoff 引 `W6-08` 老号引用所致）。
- **不新建提交补链**：全部并入本波文档提交。

### 裁 7 · 不做清单（写进交接「明确未做」）
- 前台服务（R5 4/4，需 doze 场景）；`GlassLayerBudget` 设备探针（core-design 余 35）；MCP 私网端到端（设备关闭，G5b ⚠️ 维持）；`activeRun` compareAndSet（观察位）；`COMPACT_TRIGGER_CHARS` 引用数守卫（登记）；浅色主题思考块复测（W24-01 出口，真机面若顺带可做但不设为判据格）；R4/1M 真压（永久不得写成通过）。

## 二、判据（先判据后动手；正控先行）

| 格 | 判据 | 空命中语义（防假绿） |
| --- | --- | --- |
| G1 | 修后 `wellFormed`：合流样本（完整行 + 无换行 + 第二条接尾）判 **False**；`at=17` 判 True；截断 payload 判 False；完整行判 True（Python 复刻 + JVM 既有用例扩断言双证） | 修前同样本判 True（必红正控）；只跑完整行 = 空转 |
| G2 | journal 恢复卡回归：半写行不弹卡 + 完整行弹卡（W63 e4 判据重跑，防本次修复破坏 fail-safe 方向） | 需配正控完整行，否则「没弹卡」可因读盘失败假绿 |
| G3 | N-1 请求体格：自定义协议开 + `parallel_tool_calls=false` 保存 → 真机请求 → 取证请求体含该键（或降级组合如实标 ⚠️） | 锚「请求体带键」而非「保存链不拒」；无请求体明文则降级声明 |
| G4 | 外审 6 行销账：k6 十一类必红逐类 RED 输出入仓 + k7 必红 RED + 正常态 k6×3/k7×2 OK | 逐类 = 每类单独可指认；缺任何一类该行不销 |
| G5 | 台账状态列：39→45 CLOSED / 166→160 OPEN / 总行 216 不变；G20 对 CLOSED 行证据非空复核仍绿 | 状态列 pipe=7；注记内禁半角竖线 |
| G6 | 取消链回归（W63 e5 判据）：取消 settled + 下一 run 正常（forRequest 移 try 不复活互斥锁） | runMutex 永锁症状 = GateRejected，判据须含下一 run run_started 行 |
| G7 | 四闸门 + 探针 + 台账符号 + 隐私门禁全绿（**905** 受控破例，同提交对账） | tests=0 判红；strict @Test = 905 逐字对账 |

## 三、执行顺序

1. 第 0 车：本裁决 + 派单卡落盘（本文档）+ 四处计数订正注记。
2. 第 1 车：代码三件（wellFormed 合流约束 + KDoc / AgentRunnerTest 扩断言 / forRequest 移 try）+ 四闸门。
3. 第 2 车：k6/k7 必红逐类复跑 + §2.5a 分类清单 + 6 行销账台账落笔。
4. 第 3 车：真机（唤醒三步法）：G1 复刻正控在本地已做，真机跑 G2/G3/G6 + 长回答渲染回归 + 顺带 UI 矩阵；脱敏截图 + deviceLogTail。
5. 第 4 车：审查席 diff 审查 + 文档三件 + 提交 + CI 四字段。

## 四、边界

- 三顶格对象零触碰（`core-kernel 449/450`、`GenerationRound 400/400`、`feature-chat 3999/4000`）；`LINE_CAPS`/`TOTAL_LINE_CAP` 不调。
- `EXPECTED_TESTS` 904 → **905**（本波唯一受控破例：journal 合流形态判据四态断言一个方法 + `build.yml` 同提交对账 + 增量注释写明边界区分）；其余测试文件零删零改。
- MCP 私网端到端（G5b）不测（内网设备关闭）；R4/1M 不写通过。
- 本波销账 6 行是**状态列变更波**（39→45/166→160），首次打破「状态列零变化」口径——由「外审 6 行销账」用户指令直接授权，派单卡先行声明，收尾契约如实记录。
