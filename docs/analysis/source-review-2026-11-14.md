# LiquidAgent harness-v2 源码日析 — 2026-11-14（已提交 main `00eade0`，W17 缺陷收口批 + W18 验收回填）

> 只读静态分析 + 改进推演。**全程未改动任何源码/配置文件**，仅在 `docs/analysis/` 新建本文档。
> 分析对象：`<workspace>/harness-v2`（现役仓，已提交 `main`，`HEAD = 00eade0`）。

## 本次依据（版本与水位）

- **工作记忆**：`MEMORY.md`（W6 硬预算 / W11 三判据 / W12 判据 / 隐私卫生铁律）+ `automations/9eac1478…/memory.md`（前 7 份：11-04/11-06/11-06-2/11-07/11-09/11-10/11-11；11-11 为 `234eac0`，a/b/c=4/1/1）。
- **项目文档**：`docs/06-deferred-ledger.md`、`docs/02-roadmap.md`、`docs/handoff-20261111-wave16.md`、`docs/analysis/w17-acceptance.md`、`docs/analysis/w18-acceptance.md`。
- **架构闸门**：`scripts/arch-guard.sh` 的 `LINE_CAPS`（152–200 行）。`feature-chat|500|4000`、`TOTAL_LINE_CAP=42500` 均未动。
- **git 状态核实**：`HEAD = 00eade0`（W18 验收回填 docs）。自 11-11（`234eac0`）以来 `src/main` 唯一改动来自 `1d27031`（`feat(w17): SkillMention 缺陷收口批 + droppedBlank 导入可见性`，W17 代码修复批），W18 三提交均为 docs。**工作树未跟踪文件仅 `docs/analysis/*.md`（含本文件）**。
- **前次文档**：`source-review-2026-11-11.md`（a/b/c=4/1/1；A1–A4/C1 全部针对 `51be3f7` 新增代码）。

### arch-guard 当前行数水位（本日逐模块 `wc -l`，已提交 HEAD `00eade0`）

| 模块 | 实测行 | 上限 | 余量 | 水位 | 备注 |
|---|---:|---:|---:|---:|---|
| core-model | 2170 | 2600 | 430 | 83% | 与 11-11 持平 |
| core-net | 790 | 1200 | 410 | 66% | 与 11-11 持平 |
| core-engine | 1207 | 2600 | 1393 | 46% | 与 11-11 持平 |
| core-kernel | 403 | 450 | 47 | 90% | 与 11-11 持平 |
| **core-context** | **1646** | **1650** | **4** | **99.8%** | 🔴 临界，**B-1 延续**（非本日新恶化） |
| core-agent | 5521 | 6500 | 979 | 85% | ⚠️ 本波 +78（SkillMention.kt 重写 +108 净增） |
| core-skills | 419 | 1000 | 581 | 42% | 与 11-11 持平 |
| core-mcp | 758 | 1400 | 642 | 54% | 与 11-11 持平 |
| core-data | 2489 | 4500 | 2011 | 55% | ⚠️ +19（SessionImportParser droppedBlank） |
| core-design | 12450 | 12600 | 150 | 99% | ⚠️ `LiquidBottomTabs` 1012>600 例外延续 |
| **feature-chat** | **3996** | **4000** | **4** | **99.9%** | 🔴 本波 +56（ChatSkillInput.kt 外提 +44 + 其他），**已裁 KDoc 过闸**（见 B1） |
| **feature-settings** | **3495** | **3500** | **5** | **99.9%** | 🔴 与 11-11 持平（余 5） |
| feature-tools | 947 | 2600 | 1653 | 36% | 与 11-11 持平 |
| app | 1359 | 1600 | 241 | 85% | 与 11-11 持平 |
| **TOTAL** | **≈37650** | **42500** | 4850 | **88.6%** | 守住；较 11-11（37494） +156 |

> 结论：本波 `1d27031` 给 `src/main` 净增约 156 行（全仓 88.2%→88.6%）。`TOTAL_LINE_CAP=42500` 守住，但 **feature-chat 由 3940/4000（余 60）变为 3996/4000（余 4）**——距上波「裁决抬上限」仅三周，已把 KDoc 裁掉才过 G10，债务坐实（详见 B1）。`ChatViewModel.kt` 单文件 **500/500** 触顶（见 A3）。

---

## 一、本次新发现

> 本日对 `HEAD = 00eade0` 做了与 11-11 相同范围的逐模块 `wc -l` + 全仓 Grep 静态核对，并**逐文件审读了 W17 改动的 9 个 `.kt`**（`SkillMention.kt` 全量重写、`ChatSkillInput.kt` 新增、`ChatViewModel.kt`/`ChatInputBar.kt`/`ChatBubble.kt`/`ChatScreen.kt`/`ManualCompress.kt`/`SessionImportParser.kt`/`SessionImportRow.kt` 的 diff）。**源码对象已变化（W17 修复批）⇒ 本日有新观察；且 11-11 的全部发现已被干净收口（见下方「正面验证」）。**

### （a）需要改进的实现

- **A1（新/延续）— `syncMcpTools` 仍用 `runCatching` 包裹 suspend 调用，与已修的 A1 同源（W84 反模式）**
  - 位置：`feature-chat/.../ChatViewModel.kt:136`（`runCatching { container.mcpServers.list() }`）+ `:138`（`runCatching { container.mcpTools.refresh(server, registry) }`），两处均包裹 `suspend` 调用，跑在 `viewModelScope.launch` 内。
  - 现象：本波已把 init 里 `skills.list()` 的 `runCatching` 改成「`catch (c: CancellationException) { throw c }` + 其他异常 Log 回空」的 W84 正例（`ChatSkillInput.loadSkillNames`，见下方正面验证）。但 `syncMcpTools` 这两处**未同步收紧**，仍会吞掉 `CancellationException`——ViewModel 在 MCP 清单同步途中被清除时，该 init 协程会「假完成」而非取消。
  - 风险：低风险（MCP 同步是 best-effort，失败不影响对话；且为 init 一次性 fire-and-forget），但属**已修 A1 的残留面**，与「W84 纪律」不一致，应一并闭环。
  - 建议（文字描述）：对齐 `loadSkillNames` 的写法——对 `CancellationException` 显式重抛；其余异常 Log 后忽略（本就是 best-effort）。不引入新行为，只消除反模式。

- **A2（新）— `stripDecoration` 用固定字符串锚点 `lastIndexOf(GUIDE_SEPARATOR)` 剥离引导，存在误截断脆弱性**
  - 位置：`core-agent/.../skill/SkillMention.kt:55`（`GUIDE_SEPARATOR = "\n\n(Referenced skill(s): "`，装饰与剥离共用常量）+ `:128-131`（`stripDecoration` 以 `lastIndexOf` 该串为截断点）。调用侧 `ChatBubble.kt:78`（`ChatSkillInput.displayText`）。
  - 现象：剥离逻辑依赖「引导串在用户正文里绝不会出现」这一假设。`displayText` 只在 `ChatBubbleRow` 对用户气泡调用，一旦用户真实正文恰好包含字面量 `\n\n(Referenced skill(s): `，该处之后的内容会被当作「引导」整段砍掉。
  - 风险：极低概率（用户手写该精确英文字面串罕见），但属**纯字符串哨兵**类设计脆弱性，与本项目反复出现的「字符串锚点/硬编码标记」风险（如 W11-03）同源；且剥离锚点是「展示正确性」的隐性依赖。
  - 建议：引导剥离改用结构化标记（如带随机/版本前缀的哨兵，或把引导存为气泡的**独立字段**而非拼进 `text`），让 `displayText` 不再依赖正文不含特定子串；至少应补一条「正文含锚点子串」的单测固化该边界。

- **A3（新）— `ChatViewModel.kt` 单文件 **500/500** 触顶，任何 chat 改动无 headroom**
  - 位置：`feature-chat/.../ChatViewModel.kt`（全文 500 行；单文件上限 500，见 `arch-guard.sh:191`）。
  - 现象：11-11 记录 `ChatViewModel` 为 488/500（注明「只许加一行委托」）。本波把 skill 逻辑**外提**到 `ChatSkillInput.kt`（按「触 G10 单文件闸」处置，先例 `ConversationOps`），但 `ChatViewModel.kt` 自身仍回落到 **500/500**——说明本波改动里 ViewModel 又长了约 12 行（KDoc/接线），外提并未给它腾出空间。
  - 风险：单文件闸与模块闸（feature-chat 3996/4000）**同时吃紧**：下一个任何聊天功能改动只要碰 ViewModel，就必须再外提一个文件，否则立刻触顶；而外提又继续吃模块余量（现仅 4 行）。与 B1 叠加，chat 模块已无缓冲。
  - 建议：把 `ChatViewModel` 中与流式刷新/审批无关的次要职责（如 `labelOf`、`contextLimitOf` 等纯函数）继续外提，使其回到 ~480 行安全区；该动作与 W16-03 拆分 feature-chat 同波进行。

### （b）不合理的功能设计

- **B1（演进/预言成真）— feature-chat 3996/4000 临界 + 已裁 KDoc 过闸，证明 11-11 的「拆分当扩容」预警已实现**
  - 位置：`scripts/arch-guard.sh:191`（`feature-chat|500|4000`）+ 提交 `1d27031` 自承「G10 一次触顶裁 KDoc 后过，未调 LINE_CAPS」+ `docs/handoff-20261111-wave16.md` §九.2（W16 用户裁决上限 3900→4000）。
  - 现象：11-11 我明确预警——「feature-chat 上限被裁决抬到 4000 后，下次任何 chat UI 增量又会立刻顶到 4000，届时再无『调高』余地、只能紧急拆分」。本波（W17）仅新增 `ChatSkillInput.kt`（+44）等，feature-chat 即由 3940 涨到 **3996/4000**，且 commit 自承已裁 KDoc 才过闸。
  - 风险：① 这是「把拆分当扩容」裁决（W16 用户覆盖，B1 在 11-11 已立账）付出的**真实代价**，不是假设；② 模块余量从「60（上限被抬）」缩到「4（上限未动、靠裁文档）」——地基比 11-11 更虚；③ `ChatViewModel` 单文件也触顶（A3），双重闸吃紧；④ W16-03（拆分 feature-chat）仍 `OPEN`，原本是「下波前置」，现在已升为**紧急前置**——再有任何 chat 需求，毫无腾挪空间。
  - 建议：把 W16-03 拆 feature-chat 从「建议」升为「下个聊天功能波强制前置硬约束」；**严禁再次调高 `LINE_CAPS`**；优先把 `ChatInputBar`/`ChatScreen`/`ChatBubble` 的纯函数/常量下沉到有余量的模块（core-agent/core-skills 仍有千行级余量），而非继续堆在 feature-chat。

### （c）缺失的功能

- **C1（新）— `decorate → displayText` round-trip 缺专门单测，且「消费侧剥离」无显式约定/守卫**
  - 现象：W17 把「发送存装饰版（wire=存储）、显示剥引导」作为 N1 修复落地（`ChatSkillInput.decorateInput`/`displayText` 配对，`ChatBubble.kt:78` 仅显示侧调用）。但：① `ChatSkillInput` 这对配对**没有专门的 JVM 单测**（W17 新增的 `+11` 全在 `SkillMentionTest`，覆盖 `SkillMention` 自身；`ChatSkillInput` 的 decorate/strip 配对未被直接断言）；② `bubble.text` 现在是**装饰态**（含英文引导），任何**非显示**消费者（未来的「复制用户消息」、对话导出、以新消息重发、云端备份）若直接读 `bubble.text` 都会把引导漏出去而非用户原文。当前代码 USER 气泡**无复制键**（仅 MODEL 有，`ChatBubble.kt:142-147`），故暂无泄漏，但缺少一道「`bubble.text` 是装饰态、非显示消费前必须 `displayText` 剥引导」的显式约定或守卫。
  - 风险：功能演进时极易踩「复制/导出把引导带出去」的坑（与 G16 中文棘轮、出口脱敏同一类「隐形约定」风险）。
  - 建议（文字描述）：① 补一条 `ChatSkillInput` 的 `decorate → displayText` round-trip 单测（装饰后存、显示还原 == 原文，且多次装饰不双重引导）；② 在 `ChatBubble`/导出入口处明确「读取用户原文走 `displayText`」的约定，或把引导存为气泡独立字段彻底消除 `text` 歧义（与 A2 同源解法）。

> **本日新发现 a/b/c = 3 / 1 / 1**（A1 残留 / A2 哨兵脆弱 / A3 单文件触顶；B1 债务坐实；C1 round-trip 缺测 + 消费侧约定）。全部为对 W17 收口批的后续推演，未改任何源码。

---

## 二、与架构铁律的偏离风险清单（本日重新核对）

| 铁律 | 现状（HEAD `00eade0` 核对） | 偏离风险 |
|---|---|---|
| TOTAL_LINE_CAP 不许变胖（W5 铁律 3） | 守住（≈37650/42500=88.6%）；本波未改 `TOTAL_LINE_CAP` | ✅ 满足 |
| 拆分不调上限 | feature-chat 3996/4000 已裁 KDoc 过闸；W16 裁决上限未再调，但余量耗尽 | 🔴 B1：债务坐实，W16-03 下沉紧急 |
| 组件有测试≠组件被使用 / 新增组件须配被调用判据 | `SkillMention` 仍被 3 处真实调用（`ChatViewModel`/`ChatInputBar`/`ChatScreen.applySkillPick`）；`ChatSkillInput` 被 `ChatViewModel`/`ChatBubble` 调用（使用链存在） | ✅ 正面实例（对照 W12-02） |
| DI 只走 AppContainer | `ChatSkillInput` 是**无状态 `internal object`**（零字段，仅函数，与 `SkillMention`/`HardBudgetShrinker` 同范畴）；`loadSkillNames(container)` 由调用方注入 `AppContainer`；`ChatViewModel` 经 `container` 取一切 | ✅ 未见违规 |
| 模块边界（G6/G8） | feature-chat → core-agent(`SkillMention`)/core-data(`AppContainer`)/core-model/core-design 均为 feature→core 合法边；`SkillMention.kt` 零 import（不引 engine/net/litertlm/data/design） | ✅ 干净 |
| 禁注解处理器/Retrofit/图片库（G4） | grep Retrofit/Hilt/Koin/Room/Coil/Dagger/Glide/Picasso = 仅注释，0 真实 import/调用 | ✅ 干净 |
| `@Serializable` 禁密钥字段（G17） | 本波无新增带密钥字段的 `@Serializable` 数据类 | ✅ 干净 |
| `WindowInsets.ime` 不进 `derivedStateOf` 普通 lambda | 本波未触碰；既有 `val imeInsets = WindowInsets.ime` 取法维持 | ✅ 维持 |
| `runCatching` 不吞 `CancellationException`（W84） | **已修**：`ChatSkillInput.loadSkillNames` 对 CE 显式重抛（A1 收口）；但 `ChatViewModel.syncMcpTools`（`:136`/`:138`）仍包裹 suspend 调用未收紧（A1 残留） | ⚠️ 新偏离（低风险，best-effort init） |
| 裸 `assertFailsWith{}` 收尾致 initializationError（W80） | 全仓测试源集 grep `assertFailsWith` = 0 命中 | ✅ 无风险 |
| 不引入图片库 / core-agent 不碰端侧引擎细节 | `SkillMention`/`ChatSkillInput` 纯文本/日志，未引 litertlm/引擎 | ✅ 合规 |
| G16 中文硬编码棘轮（请求体零中文） | `decorate` 引导文案**刻意英文**（类头自注），未向请求体注入中文 ⇒ 不破坏前缀缓存稳定；`stripDecoration` 显示侧还原 | ✅ 与 G16 一致 |

### 正面验证（W17 收口质量，逐项核对代码确认）

1. **A1（11-11）已闭环**：`ChatSkillInput.loadSkillNames`（`ChatSkillInput.kt:25-33`）对 `CancellationException` 显式 `throw c`，其余异常 `Log.w` 后回 `emptyList()`——取消透传 + 失败可观测，与 W84 一致。
2. **A2/C1（11-11）已闭环**：`SkillMention.decorate(text, knownNames)` 先 `extractMentions` 再 `.filter { it in knownNames }`（`:143-145`），只对已知 skill 名附加引导；邮箱/`@Override` 等任意 `@token` 不再误注入。白名单与下拉面板同一份 `skillNames` 事实源。
3. **A3（11-11）已闭环**：`SkillMention.applySkillPick`（`:102-120`）只截**开头**触发 token，余下正文原样保留（含正文里的 `@`/`/` 不再丢字）。`ChatScreen.kt:309` 调用点正确。
4. **A4（11-11）已闭环**：`ChatInputBar.kt:87-88,142-145` 用 `skillMenuDismissed` 状态 + `LaunchedEffect(draft)` 重置 + `onDismissRequest = { skillMenuDismissed = true }`，点外部可关闭、继续输入重新弹出。
5. **无二次装饰路径**：`send()`（`ChatViewModel.kt:292,301`）仅对当前 draft 调一次 `decorateInput`；`runOnce` 透传 `decoratedText` 不再装饰（`:355-356` 注释「不得二次装饰」）；history 由已装饰气泡重建，下一轮不再过 `decorate` ⇒ 引导不会叠加。
6. **`droppedBlank` 计数一致无重复**：`SessionImportParser` JSONL 段（`:183-186`）于 `append` 之前拦截空正文并 `droppedBlank++`，markdown 段（`:225`）在 `flush()` 内 `droppedBlank++` 后 `append` 对空段 `return null`（`:293`）⇒ 两路径均不重复计数，`SessionImportRow` 正确上屏（`:107-114`）。
7. **`ChatScreen` 接线干净**：`applySkillPick` 走 `SkillMention`（core-agent，JVM 可测），UI 仅传 name（`:308-309`），未把解析逻辑塞进 Composable。

---

## 三、建议优先级（与是否进 ledger）

- **P0**：无（无构建阻断 / 无安全泄漏 / 无数据破坏性已发路径；A2 为极罕见边界误截，非必现主路径）。
- **P1**：
  - **B1（feature-chat 3996/4000 + ChatViewModel 500/500 双重触顶，W16-03 拆分紧急）**——下个聊天功能波**强制前置硬约束**，严禁再调 `LINE_CAPS`；优先下沉纯函数/常量到 core-agent/core-skills。
  - **A3（ChatViewModel 500/500）**——随 B1 同波解决（再外提纯函数）。
  - **A1 残留（syncMcpTools `runCatching` 吞 CE）**——低风险，随 W16-19 或本波顺手收口（与已修 A1 对齐写法）。
- **P2**：
  - **A2（stripDecoration 哨兵脆弱）**——建议结构化标记/独立字段，低优先，补边界单测。
  - **C1（decorate/displayText round-trip 缺测 + 消费侧剥离约定）**——补单测 + 固化「非显示消费走 `displayText`」约定。
  - **延续**——core-context 1646/1650（余 4，B-1 临界，W11-03 前置约束）；feature-settings 3495/3500（余 5）；`LiquidBottomTabs` 例外；W12-02 四态零读者；W9-05（顺延）/W11-03/W11-05。

**是否进 roadmap / deferred-ledger**：本波**新增立账建议**——A1 残留可并入既有 `W16-19`（技能目录加载错误可观测 + CE）或单独立账；A2/C1 建议进 ledger 低优先（结构化引导标记 + round-trip 测）。**B1/W16-03 状态重申并由「建议」升「紧急」，不重开号**。其余均沿用既有挂账。

---

## 四、未覆盖 / 需真机验证项（不可仅靠静态判定）

- **wire=storage 引导持久化的端到端效果**：真机需确认引用消息**重开后显示正确**（剥引导还原原文）、且模型多轮看到该英文引导句不退化。静态只能确认代码路径（`send` 存装饰、`ChatBubble` 显示剥），无法确认多轮重建后 UI/模型行为。
- **skill_load 实际完成**：W17 真机验收 commit 称「@ 引用端到端」通过，但 11-11 遗留的「模型是否**据此真正调用** `skill_load`」仍需对话级证据（非仅「引导进了请求体」）。
- **A2 哨兵误截断**：仅当用户输入含字面量 `\n\n(Referenced skill(s): ` 才触发，真机几乎不现，建议单测构造该串验证。
- **OEM 差异**：唯一直设备 OPPO PDRM00 / Android 13；华为/HarmonyOS、小米、vivo 的 `usesCleartextTraffic`、Keystore、屏幕密度未验。
- **端侧模型行为**：仅 NV-VL 实测 1M 窗口三通；设备侧 1M 受 RoundHeadGate 400k 阻断（W9-11 未解锁）。
- **W12-02 四态消费端缺失**：本波未新增四态读者，`StreamAccumulator.cacheStatus` 在 main 仍零读者，四态观测仍半落地。

---

## 结论

本日（2026-11-14）分析对象相对 **2026-11-11**（`234eac0`）**源码已变化**：`HEAD = 00eade0`，自上次以来 `src/main` 唯一改动来自 `feat(w17)` 修复批 `1d27031`（SkillMention 缺陷收口 + droppedBlank 导入可见性），W18 三提交均为验收回填 docs。本日逐模块行数水位表已重算（全仓 ≈37650/42500=88.6%，较 11-11 +156）；关键变化是 **feature-chat 3996/4000（余 4，已裁 KDoc 过闸）**、**`ChatViewModel.kt` 500/500 单文件触顶**、core-agent 5521、core-data 2489。

静态核对结论：

1. **11-11 的全部发现已被 W17 干净收口**：A1（runCatching 吞 CE）→ `ChatSkillInput.loadSkillNames` 显式重抛 CE + Log；A2/C1（decorate 任意 @token 误注入）→ 白名单 `knownNames` 过滤；A3（onPickSkill 截断丢字）→ 只截开头触发 token；A4（下拉不可外部关闭）→ `skillMenuDismissed` 状态。逐条核对代码确认闭环，且**无二次装饰路径**、`droppedBlank` 计数一致、UI 接线合规。
2. **本日新发现 a/b/c = 3 / 1 / 1**：A1 残留（`syncMcpTools` 的 `runCatching` 仍包裹 suspend 调用，W84 反模式低风险残留）、A2（`stripDecoration` 字符串哨兵脆弱）、A3（`ChatViewModel` 500/500 单文件触顶）；B1（feature-chat 3996/4000 + 已裁 KDoc 过闸，**证明 11-11「拆分当扩容」预警成真**，W16-03 拆分由建议升紧急）；C1（decorate/displayText round-trip 缺专门单测 + 「消费侧剥离」无显式约定）。全部为对 W17 收口批的后续推演，未改任何源码。
3. **架构铁律静态基本绿**：DI 只走 AppContainer（`ChatSkillInput` 为无状态 `internal object`，`container` 由调用方注入）；模块边界无违规边（G6/G8）；G4 黑名单/G17 密钥字段/`WindowInsets.ime`/`assertFailsWith` 均合规；`decorate` 英文引导与 G16 中文棘轮一致。唯一**新偏离**是 A1 在 `syncMcpTools` 处的低风险残留。
4. **延续预警（非新立账）**：core-context 1646/1650（余 4，B-1 临界）、feature-settings 3495/3500（余 5）、`LiquidBottomTabs` 例外、W12-02 四态零读者、`W9-05`(顺延)/`W11-03`/`W11-05` 均维持未解。

**全程只读，未改动任何源码/配置文件、未 `git add`/`commit`/`push`、未执行任何会改动设备的 adb 命令。**

> 与 N 天前结论的演进：相对前次（2026-11-11，a/b/c=4/1/1，源码为 `51be3f7` 新增代码）——
> - **源码对象已变**（W17 修复批 `1d27031` 收口了上轮全部 A1–A4/C1）⇒ 本日从「复核上轮新发现」转为「确认收口 + 报告收口衍生/遗留」；
> - 行数水位：feature-chat 由「3940/4000 余 60（上限被裁决抬）」变为「3996/4000 余 4（上限未动、靠裁 KDoc 过闸）」——**B1 预言成真**；`ChatViewModel` 由 488/500 回到 500/500（单文件触顶）；全仓 88.2%→88.6%；
> - 新铁律偏离 = A1 在 `syncMcpTools` 的残留（低风险）；其余静态判据与 11-11 一致维持；
> - 真机侧「skill_load 实际完成」「wire=storage 引导持久化多轮效果」仍待补取证。
