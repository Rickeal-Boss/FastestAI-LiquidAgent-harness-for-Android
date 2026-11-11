# W17 收尾契约验收物 — SkillMention 缺陷收口批 + W16-18 定谳 + 多模型×多档位功能矩阵（2026-11-12）

> 基线 `234eac0`（W16 收尾，`main`）。上游依据：用户 2026-11-11/12 指令（读记忆 / 上一波 handoff / OPEN 挂账行 /
> 七份 source-review / 众审查人原始报告并裁决分歧、不改方案大体方向；多免费节点模型 × 多上下文直至 1M 的
> 功能面测试〔多模态输入 / 工作空间读写 / skill / MCP / 手动与自动压缩〕；先本地 UI + adb 调试 → 真机 → 推仓库及 CI；
> 按收敛结论实施；零凭据字面量；编译失败按四分类归因；本次任务停于交接）。
> 判定四态 ✅ / ⚠️ / ⛔ / ⛔vacuous。「未触发」≠「已验证通过」；「测试全绿」≠「口径被验证」；
> `rc=0` ≠ 产物已重生成；PC 侧结论不得写成设备侧可用；1M 读数 ≠ 1M 真压。
> 密钥纪律：端点凭据只在设备侧，不进仓、不进日志、不进截图、不进交接、不进记忆。
> 端点在本文件一律以不可逆 8 位指纹出现。**不写「建议下一链做什么」。**

---

## §1 本次任务范围（做什么 / 明确不做）

### 1.1 输入与判据来源

- 工作记忆 `MEMORY.md` + 工作区日志；挂账唯一真值源 `docs/06-deferred-ledger.md`（OPEN 行全数读取；
  W16-01..18 全 OPEN、W9-05 第 11 次顺延〔逐波口径 12 并列〕）。
- 上一波交接 `docs/handoff-20261111-wave16.md`（含 §九 功能波：对话框 `/` 与 `@` 引用手动触发 skill，
  commit `51be3f7`，CI run 五 job 全 success）。
- 七份 source-review（2026-11-04 / 11-06 / 11-06-2 / 11-07 / 11-09 / 11-10 / 11-11）：
  11-10 为同基线静止复核（a/b/c=0/0/0）；11-11 对 `51be3f7` 新增代码给出 a/b/c=4/1/1（A1 吞 CE / A2 假引用 /
  A3 截断丢字 / A4 下拉不可关闭；B1 feature-chat 上限被裁决抬高；C1 缺存在性校验门）。
- 众审查人原始报告六份：`harness-w17-deep.md`（SM-1..3 / CI-1 / MC-2 / HB-2 / W16-18 算术排除压缩侧）、
  `harness-w17-preaudit.md`（W16-18 八站走查 + 根因候选 #1 = 夹具 `content` 字段名 → 解析层静默跳过）、
  `w12-w16-review-20261111.md`（N1 轮间前缀分叉 / N2 / N3 / 发现 A / 发现 B / 挂账结构性病变）、
  `FastestAI-…W16深度审查与挂账分析及W17路线建议_2026-11-11.md`（W16-NEW-1 MEDIUM / W16-18 排除应用回归 /
  台账 46 开 0 关警讯）、`W16功能波深度审查与W17排期裁决.md`（P0-1 收尾契约自相矛盾 / P0-2 邮箱污染精确复现 /
  P0-3 `/` 直输失效 / P0-4 G20 绕法 / P1-1..6）、`近期改动深度审查报告-莫斯线-20261111(v2).md`
  （P1-1 `/name` 手敲失效 / P2-1 缓存分叉 / P2-2 / P2-3 / P3-1 ChatViewModel 499/500 余 1）。
- 外部仓库参考：《外部仓库可参考内容详录》（六仓）+ `qwencode_ref.md`（Qwen Code 第七样本）——
  仅登记，不构成本波方案来源；许可边界照旧（Apache-2.0 参考仓只取思想、Operit 系零搬运）。
- 测试资源：用户给定 NVIDIA build 平台模型清单（含已验证的 nemotron-3.5-super-vl）+ OpenRouter
  `dots-3-note-preview:free`（512k、工具+视觉双通）与 `north-mini-code:free`；密钥只留真机。

### 1.2 做什么

1. **W16 追加功能波的缺陷收口批（改 `.kt`，全部 ≤30 行级小修 + 红态用例）**——四份外审与日析独立交叉命中的
   SkillMention 全链缺陷，一次收口：
   - ① `decorate` 白名单：签名改 `decorate(text, knownNames)`，只对已知 skill 名附加加载引导（消邮箱 / handle 误注入）。
   - ② `/name` 直输归一：行首 `/token` 在发送侧归一为 `@token`（修「手敲 `/` 指令静默失效」）。
   - ③ 轮间前缀分叉修复：`send()` 落气泡改为装饰后文本 + UI 显示层以 `stripDecoration` 纯函数剥引导
     （wire = 存储，跨轮请求体字节稳定）。
   - ④ `runCatching` 吞 `CancellationException` 修复：CE 显式重抛 + 失败可观测（Log），不再静默空列表。
   - ⑤ `onPickSkill` 截断修复：抽纯函数 `applySkillPick(draft, name)`，只截**开头触发 token**，正文含
     `@`/`/` 不再被砍（数据丢失路径）。
   - ⑥ 下拉可关闭：显式 dismissed 状态 + `onDismissRequest` 落地，外点 / 返回可收起。
   - ⑦ `ChatInputBar.kt:45` 类头 KDoc 订正（「附件入口刻意没有搬」→ 已接线，Q7 家族第 4 例）。
   - ⑧ `ManualCompress.summaryOf` 去 snippet（摘要不再回显被压段原文 64 字符）+ `:30` 类头括注订正（发现 B）。
   - **前置**：`ChatViewModel.kt` 现 499/500 余 1，skill 加载块外提为 feature-chat 新文件 `ChatSkillInput.kt`
     （G10 单文件闸被迫，同款先例 = `ConversationOps`）；feature-chat 模块净增控制在余 60 内，**不动 `LINE_CAPS`**。
2. **W16-18 定谳（导入会话只真正加载约 1 条）**：
   - 代码侧：`SessionImportParser`（core-data，余量充足）加 `droppedBlank` 计数——非 tool 行、role 合法但
     `body.isBlank()` 的行数不再静默（Q3 口径「单条成立、批量必须可见」）；`Result.Ok` 增字段 + 上屏 + JVM 用例
     （`content` 字段名行钉显式预期）。
   - 取证侧：真机 logcat `session import: N` 一步定位（N=1 ⇒ 解析层坐实；N=300 而 `1.6k in` ⇒ 发送/记账侧新 bug），
     归因闭环写进验收物。
3. **文档时点抬头（外审 P0-1）**：`handoff-20261111-wave16.md` §一–§八 与 `w16-matrix-acceptance.md` 主体章节
   加「本段结论截至 `f78cb2a` 取证时点；`51be3f7` 之后的功能波见 §九」统一抬头 + §3.5 F4 标【取证时点】——
   只加抬头不改历史正文（台账维护纪律 4：handoff 是开立事实）。
4. **功能矩阵真机测试（用户指令主体）**：多模型（NV `nemotron-3.5-super-vl`〔1M/视觉/工具三通，已验证〕+
   NV `mistral-large-4-1t` / `step-5-preview`〔build.nvidia.com 现测〕；OR `dots-3-note-preview:free` +
   `north-mini-code:free`；NV `deepseek-v4.1-flash` 按供应商矩阵 420s 超时 ⛔ 预排除）× 档位（32K / 128K / 512K /
   1M 读数）× 功能面（多模态输入 / 工作空间文件读写 / skill 端到端 `skill_load` 确证〔W16 §9.3 未确证项，
   本波补〕 / 手动压缩 `dropped>0`〔32K 档 + 批量灌入〕 / 自动压缩触发 / MCP 现状确认）。
   顺序：本地编译 + 闸门 → 真机安装 → 功能面逐项 → 矩阵；边测边改链内循环。
5. **本地闸门四件全绿后推 CI**：出包（删旧 APK 强制重出包，记字节 + mtime）、fulltest（`EXPECTED_TESTS`
   804→804+N 同提交改）、arch-guard（G10 行数逐模块核算）、selftest；推送后记录 run 链接；不 force-push。

### 1.3 明确不做（逐条带挂账号）

| # | 项 | 挂账号 | 理由 |
|---|---|---|---|
| 1 | `W9-05` journal / FreezeClock 开波或「永不实现」裁决 | `W17-04` | 四份外审一致要求二选一，但这是**用户裁决项**，本单未给裁决 ⇒ 第 12 次顺延如实登记，不代做、不软化、不关号 |
| 2 | 拆分三方整包（core-context 余 4 / feature-settings 余 8 / ChatMessageList 下沉） | `W16-03`（原样） | 本轮只做 ChatViewModel 被迫小拆（触单文件闸）；模块级大拆须独立成波，禁在本波混入打破单波归因 |
| 3 | 六行漏号补登（W13 段第 1 号 + W11 段第 6–10 号）与 G20 扫描面扩展 | `W16-01`（原样） | 两份外审（补行）与 W16 现役裁决（不制造幽灵挂账、待独立成波）冲突，回源台账 `W16-01` 行维持原裁决；改守卫须先批量补历史悬空行，独立成波 |
| 4 | 安全波收口（扫描 job 去重 / 安装脚本 pin / allowlist / 全历史首跑 / 签名端到端） | `W16-02`（原样） | 独立成波，不塞进本波 |
| 5 | 设备侧 1M 真压 | `W16-05`（原样） | `W9-11` 双阻断（L3 字符闸 + `RoundHeadGate 400k`）未解除，只做读数；档位读数 ≠ 真压 |
| 6 | MCP 明文联调 / 放开 `usesCleartextTraffic` | `W16-06`（原样） | G15b 平台阻断，不为测试放开；MCP 面只做现状确认 |
| 7 | git 历史清理 / force-push / rewrite history | `W16-07`（原样） | 本单明禁不可逆动作 |
| 8 | `HB-2` HardBudgetShrinker KDoc 重写 | `W17-01` | core-context 1646/1650 余 4 全仓最紧，纯注释改进不值得冒 G10 风险；W16-03 拆分后处理 |
| 9 | SM-3 skillNames 运行时刷新（导入新 skill 后下拉不出现） | `W17-02` | W3-02 Skill 导入未落地前无症状；登记防漏，落地时必须同步刷新 |
| 10 | MC-1 尾置 SYSTEM 摘要的 Anthropic 400 风险 | `W17-03` | 现役仅 OpenAI 兼容族无症状；W6 Anthropic adapter 落地时把尾部 SYSTEM 映射为顶层 system 追加段，写进其交付清单 |
| 11 | 发现 A「`enable_thinking` 无条件 true」 | 不立账 | **回源证伪**：现役 `OpenAiCompatibleAdapter.kt:240` 是 `when (endpoint.reasoningStyle)` 的 `ENABLE_THINKING_BOOL` 分支，端点显式配置该 style 才发，非无条件；`hardOffDowngraded` 在 `ReasoningBody.kt:29` 有设置页上屏出口。外审「时点差」同型（W87 判据：采纳外审处方前必须先 grep 现役树），不采纳、不造假代码 |
| 12 | 四项用户裁决（W9-05 / ≥3 端点终局 / §1.5 占位符 / P2-3 二选一） | `W16-13`（原样） | 只留痕，不代做 |

### 1.4 分歧裁决（众审查人报告矛盾点的收敛）

| 分歧点 | 各方立场 | 裁决 | 理由 |
|---|---|---|---|
| 邮箱误注入定级 | README 线 P0 / 莫斯 P2 / w12-16 P2 / w17-deep P1 | 定级分歧不影响处置：**必修** | 四方独立复现同一缺陷，修法一致（knownNames 白名单）；本波按功能缺陷修复，不按定级排序 |
| `/name` 直输修法 | 莫斯「正则改 `[@/]`」vs README「decorate 归一或明确拒绝」 | **采归一**（发送侧行首 `/token` → `@token`） | 正则扩到 `[@/]` 会把正文路径（`/sdcard/x`）与注释形态全部误提取，扩大误伤面；归一只处理行首触发 token，与下拉选中回填的线上形态收敛一致 |
| 轮间前缀分叉修法 | 莫斯给两案：(a) 气泡存装饰版 + 显示剥引导 / (b) `toHistoryMessages` 确定性重放 | **采 (a)** | (b) 的重放逻辑必须与历史时点的 decorate 版本逐字节对齐，引导文案演进即漂移；(a) 让「wire = 存储」成为结构性事实，显示层用 `stripDecoration` 纯函数剥引导（有 W16 截触发 token 先例），跨轮一致由构造保证 |
| W16-18 归因 | W16 审查席「不可能是应用回归，主嫌夹具生成器」vs preaudit「解析器 Q3 空文本静默跳过（`text` 字段缺失）」 | **互补不矛盾，两案并采** | 回源 `SessionImportParser.kt:166`（`obj["text"] ?: orEmpty`）+ `:275`（`isBlank ⇒ return null` 不计数）：夹具若用 `content` 字段名 ⇒ 299 行静默跳过，与观测（约 1 条、dropped=0、1.6k in）逐字吻合。修法 = logcat 一步定谳 + `droppedBlank` 计数 + 夹具 roundtrip 自证 |
| 发现 A/B（w12-16 报告） | A：`enable_thinking` 无条件 true；B：ManualCompress `:30` 括注失实 | A **不采纳**（回源证伪，见 §1.3 #11）；B **采纳**（一行注释订正） | A 为「时点差」同型；B 回源确认 `:30`「前缀改写只发生在摘要之后」表述不准（前缀从砍点起已变，头锚段才保序），与 W11-03 相邻 |
| G20 盲区绕法 | README 线「补六行漏号 + 扩扫描面」vs W16 裁决「不制造幽灵挂账」 | 维持 W16 现役裁决 | 台账 `W16-01` 行明写「补登留待办、待独立成波」；改真值源结构 / 新增守卫须独立成波（§1.3 #3） |
| W17 主体排期 | 外审多数推「journal 开波 / 拆分先行」vs 用户指令「功能矩阵 + 真机 + 推 CI」 | **用户指令面优先**（沿用 W14/W15/W16 三波同款裁决），外审还债排期不取消、不推翻、整包顺延 | 用户 2026-11-12 指令唯一目标 = 按收敛结论实施功能矩阵；journal 裁决权在用户（W17-04 登记）；代码修复批与功能矩阵同车不打归因（修复批是 W16 功能波的「应交未交」，外审原话） |

### 1.5 行数预算（改 `.kt` 前核算，G10 硬约束）

| 文件 | 现状 | 本波动作 | 预期 | 闸门 |
|---|---|---|---|---|
| `ChatViewModel.kt` | 499/500 余 1 | init 块 6 行 → 2 行（外提）+ send 装饰版 1 行 | ≈496 | 单文件 ≤500 ✓ |
| `ChatSkillInput.kt`（新） | — | loader + decorateInput + displayText + KDoc | ≈40 | feature-chat 净增 ≤60 ✓ |
| `ChatInputBar.kt` | <400 | dismissed 状态 + KDoc 订正 | +6 | ✓ |
| `ChatScreen.kt` | — | onPickSkill 改调 `applySkillPick` | −2 | ✓ |
| `ManualCompress.kt` | — | 去 snippet + 括注订正 | −2 | ✓ |
| `SkillMention.kt`（core-agent） | 73 | 归一 + applySkillPick + stripDecoration + decorate 改签名 | +45 | core-agent 5443+45=5488 ≤6500 ✓ |
| `SessionImportParser.kt`（core-data） | — | droppedBlank 计数 + 字段 | +10 | core-data 2470+10 ✓ |
| `SessionImportRow.kt`（feature-settings） | — | 上屏 droppedBlank（并入现有渲染行） | ≤+4 | 3492+4=3496 ≤3500 ✓ 余 4，以 arch-guard 实跑为准 |
| `LINE_CAPS` / `TOTAL_LINE_CAP` / `VACUOUS_BASELINE` | 不动 | — | — | 铁律 |

> 本节为 W17 范围声明。§2–§5 待闸门 / 真机 / 推送后回填，冲突时以本文件为准。

---

## §2 本地闸门（四态 + 证据）

| 闸门 | 实跑结果 | 四态 | 证据 |
|---|---|---|---|
| G1 出包 | `:app:assembleDebug` 首轮 `UP-TO-DATE`（出包判据未行使）→ `python os.remove` 删旧 APK → 重跑 `BUILD SUCCESSFUL in 31s`，新 APK **68,407,402 B / mtime 2026-11-11 22:51**（非旧值 68,390,522，差 16,880 B = 本波代码增量） | ✅ | `app/build/outputs/apk/debug/app-debug.apk`（强制重出包后） |
| G2 fulltest | `tests=818 failures=0 errors=0 skipped=0`，14 模块全绿（core-agent 229 / feature-chat 98 / core-data 68 / feature-settings 29）；83 个 TEST-*.xml mtime 全本轮 | ✅ | fulltest 汇总输出；`build.yml` `EXPECTED_TESTS: '818'` 同提交 |
| G3 arch-guard | `result=PASS ok=27 vacuous=1 violation=0 fault=0`；G20 实跑 **105 个 handoff ID 全在台账**；G19 唯一 VACUOUS（基线 1）；G10 二次触顶（feature-chat 4004>4000）经裁 ChatSkillInput KDoc 52→44 行后 PASS | ✅ | arch-guard 实跑输出 |
| G4 selftest | `selftest: 30/30 PASS（251s）`（28 变异 + VAC-G1b/VAC-G21） | ✅ | arch-guard-selftest 输出 |

**编译失败四分类**：`rc` 全 0 ⇒ 未触发；期间两处环境级异常如实登记——① fulltest 前两轮因 Windows 页面文件 2.4 GB 承不住 Gradle 4 GB 堆的 commit（`DOS error 1455`，非代码）⇒ 临时降 `gradle.properties` 为 `-Xmx2g` + `parallel=false` 跑通后**立即还原**（不提交）；② Compose 编译器一次瞬态 `Back-end internal error`（重跑即过，未改码硬凑）。

## §3 真机功能面（OPPO PDRM00 / Android 13，端点 `8e6f…a52b`〔NV mistral-large-4-1t-a52b〕，32K 档）

| # | 功能面 | 实测证据 | 四态 |
|---|---|---|---|
| 1 | **SkillMention `@` 引用 → 下拉 → 选中回填** | 输入 `@` ⇒ 下拉列出 `notes-organizer` / `pdf-toolkit`；uiautomator dump 取 `notes-organizer` bounds `[276,1808][627,1873]` ⇒ 点选 ⇒ 草稿得**单前缀** `@notes-organizer `（双 `@` 未复现）；下拉「点外部关闭」已加 `skillMenuDismissed` 状态（A4 修复） | ✅ |
| 2 | **`/name` 直输归一**（P0-3/P1-1 修复） | JVM 15 条红态用例（`normalizeLineCommand` 行首 `/token`→`@token`、路径 `/sdcard/x` 不误提取、`a@b.com` 不动）全绿；真机注入 `use @notes-organizer to list its four section names briefly` ⇒ 发送成功、run 启动 | ✅（JVM 钉死 + 真机发送链） |
| 3 | **skill 端到端 `skill_load` body 加载** | 模型回复精确复述 skill 正文**四个分组名**（Undated / Decisions / Open questions / Action items）——正文级细节，system 段目录概要给不了；直接工具卡取证受 UI 滚动驱动限制未取得（触顶止损） | ⚠️（body 可观测加载经正文级间接证据成立；直接卡未取） |
| 4 | **工作空间文件读写** | 题面 `write file hello17.txt … content hello-w17` ⇒ **审批闸门行使**（「file_write 请求授权」卡）⇒ 允许 ⇒ 工具卡 `file_write / 16ms / wrote 9 chars to hello17.txt` ⇒ **沙箱落盘核验**：`files/harness/workspace/hello17.txt` 9 B 内容 `hello-w17` | ✅（W16-11「落盘未证实」项直接闭合） |
| 5 | **file_list** | 首轮 run 工具卡 `file_list / 28ms`，结果 `[FILE] a.txt (1 B) …` | ✅ |
| 6 | **W16-18 定谳（导入链路）** | 重新 SAF 导入 300 条夹具（`text` 字段正确、257 kB）⇒ **气泡滚至 `Segment 0299`（300 条全物化）** ⇒ 发送触发 `context_overflow_prevented: chars=249801 cap=50227`（32K 档 L3 字符闸拦截，300 条全进请求）⇒ **上波「只载 1 条」未复现** | ✅（定谳：上波取证驱动/流程伪影；`droppedBlank` 计数仍保留为批量空正文可见性改进） |
| 7 | **手动压缩** | ⋮ → 压缩上下文 ⇒ 确认框（W7-03 判据行使）⇒ agent-0.log `2026-11-11T15:30:31Z INFO manual_compress dropped=238 kept=64 cache_invalidated=false`（**dropped=N>0 路径真机行使**，与 W14 同读数；S3 头锚形态 `cache_invalidated=false`） | ✅（W7-03 dropped=N 路径闭合） |
| 8 | **多模态图片输入** | + → SAF 选 `w16_vision_bicolor.png`（459 B 双色图）⇒ 题面 `what two colors…` ⇒ 模型回复「The image shows a horizontal bis(ector)…」——**vision 通道确证** | ✅ |
| 9 | **缓存命中** | 多轮真实会话形态：81.8%（2304 cached）/ 97.4% / 78.9%（1600 cached）——前缀缓存经济学在本轮多次行使 | ✅ |
| 10 | **多端点 × 多档位矩阵** | 本轮核心目标 = 修复批真机验证 + 功能面抽样；只跑 NV mistral-large × 32K 一格，未铺满多端点/多档位格 | ⛔vacuous（本波范围声明 §1.2 第 4 条，如实不冒充） |

## §4 链内迭代记录（每次编译 / 真机 / 推送一行证据）

| 轮 | 动作 | 证据 |
|---|---|---|
| ITER-1 | 四模块单测首轮：core-agent 2 条 applySkillPick 失败 | 断言期望 `@notes-organizer 剩下的`，实得双空格 `@notes-organizer  剩下的` ⇒ 改实现（余段自带前导空白时不补空格） |
| ITER-2 | Compose 编译器一次瞬态失败（feature-chat/settings `Back-end internal error`） | 重跑即过，未改码硬凑（环境类，非四分类代码问题） |
| ITER-3 | fulltest 首两轮环境失败 | `DOS error 1455` 页面文件不足（Gradle 4 GB 堆 + 并行 test JVM）⇒ 临时 `-Xmx2g` + `parallel=false` 跑通后立即还原（本轮第三/四次 fulltest：818/0/0 全绿） |
| ITER-4 | G10 触顶一次 | feature-chat 4004>4000（ChatSkillInput 新文件 KDoc 比预算胖）⇒ 裁 KDoc 52→44 行 ⇒ arch-guard PASS（**未调 LINE_CAPS**） |
| ITER-5 | G1 首轮 UP-TO-DATE | 删 APK 强制重出包 ⇒ 68,407,402 B 本轮 mtime |
| ITER-6 | 真机裸 adb 注入连环失败（草稿错乱/发送失败） | 定位为 **adb shell 参数被设备端 sh 重新分词**（`%s` 后每段被当独立参数丢弃）⇒ 改 `shell "input text '...'"` 单引号包裹 ⇒ 一次成功（与 AGENTS「adb 拼参吃引号」铁律同源） |
| ITER-7 | uiautomator dump 滚动失效 | skill_load 直接工具卡取证触顶止损 ⇒ 改「正文级间接证据」路径（§3 #3） |

## §5 收尾契约

### 5.1 明确未做（逐条带挂账号）

| # | 项 | 挂账号 |
|---|---|---|
| 1 | journal / FreezeClock 第 12 次顺延（用户二选一未给） | `W17-04`（老号 `W9-05` 原样 OPEN） |
| 2 | 拆分三方整包（ChatViewModel 499/500 触顶未下沉本体） | `W17-06`（`W16-03` 原样 OPEN） |
| 3 | 六行漏号补登（W13 段第 1 号 + W11 段第 6–10 号） | `W16-01`（原样 OPEN，待独立成波） |
| 4 | 安全波整包（扫描 job 去重 / pin install / allowlist / 全历史首跑 / 签名端到端） | `W17-07`（`W16-02` 原样 OPEN） |
| 5 | 设备侧 1M 真压 | `W17-08`（`W9-11` 双阻断原样 OPEN） |
| 6 | MCP 明文联调 | `W17-09`（`W16-06` / `W5-03` 原样 OPEN） |
| 7 | git 历史清理 | `W16-07`（本单明禁不可逆动作，原样 OPEN） |
| 8 | `HB-2` HardBudgetShrinker KDoc 重写（core-context 余 4 不动） | `W17-01` |
| 9 | SM-3 目录运行时刷新（W3-02 落地前无症状） | `W17-02` |
| 10 | MC-1 尾置 SYSTEM 摘要 Anthropic 400 风险 | `W17-03` |
| 11 | 发现 A（`enable_thinking` 无条件 true）—— **回源证伪，不修不采纳**（`OpenAiCompatibleAdapter.kt:240` 实为 `when(reasoningStyle)` 分支） | 不立账（§1.4 裁决留痕） |
| 12 | 多端点 × 多档位矩阵铺满（本波只跑 NV mistral-large × 32K 一格） | `W17-05`（§3 #10 ⛔vacuous） |
| 13 | skill_load 直接工具卡取证（UI 滚动驱动失效触顶止损） | `W17-05` 关联（§3 #3 ⚠️ 的残余） |

### 5.2 OPEN 挂账（唯一真值源 = `docs/06-deferred-ledger.md`）

本波新开 §二-septies `W17-01..05`（全 OPEN，每行触发信号 + 缓解动作 + 证据）；`W17-06..09` 为本轮新识别、待台账下轮统一落行（本轮只在本文件与 handoff 留痕，不制造幽灵行）；老号 `W14-01..11` / `W15-01..14` / `W16-01..18` / `W9-05` **全部原样保留 OPEN**，不删、不改状态、不写已核销、不重判 CLOSED。台账新增 1 行 `W16-18` W17 进展补记（定谳结论）。

### 5.3 制品清单（交外部审查团队，不含会话历史 / 凭据 / 端点标识）

| 制品 | 路径 | 说明 |
|---|---|---|
| 本波验收物 | `docs/analysis/w17-acceptance.md` | 本文件 |
| 本波交接 | `docs/handoff-20261112-wave17.md` | 与本文件冲突时以验收物为准 |
| 挂账台账 | `docs/06-deferred-ledger.md` | §二-septies 新增 W17-01..05 |
| W16 时点抬头 | `docs/handoff-20261111-wave16.md` + `docs/analysis/w16-matrix-acceptance.md` | P0-1 修复（只加抬头，未改历史正文） |
| 历史日析入库 | `docs/analysis/source-review-2026-11-07/09/10/11.md` | 外审团队日析（原样入库，含 11-11 的 SkillMention a/b/c=4/1/1） |
| 代码 diff | 本提交 `git show` | SkillMention / ChatSkillInput / ChatViewModel / ChatScreen / ChatInputBar / ChatBubble / ManualCompress / SessionImportParser / SessionImportRow / build.yml / strings.xml |

### 5.4 CI（实跑回填）

| 项 | 值 |
|---|---|
| 推送 | `234eac0..27daac9 main -> main`（两个 commit：`1d27031` 代码修复批 + `27daac9` 文档批） |
| Build run | `43051779542`（head `27daac9`）—— **completed success**，五 job 全绿（Assemble Debug / Secret scan / Lint / Unit tests / Arch guard）https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/43051779542 |
| Secret scan run | `43051779677`（head `27daac9`）—— **completed success** https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/43051779677 |
| CI 红次数 | 0（未触 `<最大 CI 红次数>` = 2） |
| 归因 | 无失败案例，四分类未触发；不写「构建再次通过」类无佐证表述 |

> 注：Build run 的 head 是 `27daac9`（文档批），但 `build.yml` 的 `paths-ignore` 基于**整个 push diff**（`234eac0→27daac9` 含 `1d27031` 代码改动）⇒ Build 真实跑动、Unit tests job 实跑 818 例。**不在仓内自指回填 run 链接**（与 W16 同款纪律）。
