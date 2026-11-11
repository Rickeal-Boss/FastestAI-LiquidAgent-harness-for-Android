# LiquidAgent harness-v2 源码日析 — 2026-11-11（已提交 main `234eac0`，W16 收尾 + 对话框 skill 手动触发功能波）

> 只读静态分析 + 改进推演。**全程未改动任何源码/配置文件**，仅在 `docs/analysis/` 新建本文档。
> 分析对象：`<workspace>/harness-v2`（现役仓，已提交 `main`，`HEAD = 234eac0`）。

## 本次依据（版本与水位）

- **工作记忆**：`MEMORY.md`（W6 硬预算 / W9-04 四态判据 / W11 三判据 / W12 判据 / 隐私卫生铁律）+ `2026-11-11.md`（W16 波次：档位切换攻克、G1 出包判据、G20 判红归因修复、推 CI；外加 11-11 追加「对话框 `/` 指令与 `@` 引用手动触发 skill」功能波，commit `51be3f7`+`234eac0`）。
- **项目文档**：`docs/06-deferred-ledger.md`、`docs/02-roadmap.md`、`docs/handoff-20261111-wave16.md`；`docs/analysis/w16-matrix-acceptance.md`（11-11 更新）。
- **自动化记忆**：`automations/9eac1478…/memory.md`（前 6 份：11-04/11-06/11-06-2/11-07/11-09/11-10；11-10 为同基线静止复核，a/b/c=0/0/0）。
- **架构闸门**：`scripts/arch-guard.sh` 的 `LINE_CAPS`（152–200 行）。本波 `feature-chat` 上限经**用户显式裁决** `3900→4000`（违反「禁调 LINE_CAPS」铁律，已留痕：`TOTAL_LINE_CAP 42500` 不动、不构成先例、下波仍须先下沉 W16-03）；`EXPECTED_TESTS` 799→804。
- **git 状态核实**：`HEAD = 234eac0`。自 11-10（`b0a6e44`）以来 `src/main` 唯一改动来自 `51be3f7`（feat(chat)，+73 main / +50 test）+ `234eac0`（docs 补记）。工作树未跟踪文件仅 `docs/analysis/*.md`（含本文件）。
- **前次文档**：`source-review-2026-11-10.md`（a/b/c=0/0/0；B-1 = core-context 1646/1650 余 4 临界）。

### arch-guard 当前行数水位（本日逐模块 `wc -l`，已提交 HEAD `234eac0`）

| 模块 | 实测行 | 上限 | 余量 | 水位 | 备注 |
|---|---:|---:|---:|---:|---|
| core-model | 2170 | 2600 | 430 | 83% | 与 11-10 持平 |
| core-net | 790 | 1200 | 410 | 66% | 与 11-10 持平 |
| core-engine | 1207 | 2600 | 1393 | 46% | 与 11-10 持平 |
| core-kernel | 403 | 450 | 47 | 90% | 与 11-10 持平 |
| **core-context** | **1646** | **1650** | **4** | **99.8%** | 🔴 临界，**B-1 延续**（非本波新恶化） |
| core-agent | 5443 | 6500 | 1057 | 83% | ⚠️ 本波 +73（SkillMention.kt） |
| core-skills | 419 | 1000 | 581 | 42% | 与 11-10 持平 |
| core-mcp | 758 | 1400 | 642 | 54% | 与 11-10 持平 |
| core-data | 2470 | 4500 | 2030 | 55% | 与 11-10 持平 |
| core-design | 12450 | 12600 | 150 | 99% | ⚠️ `LiquidBottomTabs` 1012>600 例外延续 |
| **feature-chat** | **3940** | **4000** | **60** | **98.5%** | 🔴 本波 +52，且上限被裁决 3900→4000（见 B-1 演进） |
| **feature-settings** | **3492** | **3500** | **8** | **99.8%** | 🔴 与 11-10 持平 |
| feature-tools | 947 | 2600 | 1653 | 36% | 与 11-10 持平 |
| app | 1359 | 1600 | 241 | 85% | 本波 +4 |
| **TOTAL** | **≈37494** | **42500** | 5006 | **88.2%** | 较 11-10（+129：core-agent+73/feature-chat+52/app+4） |

> 结论：本波 `51be3f7` 给 `src/main` 净增约 129 行（全仓 87.9%→88.2%）。`TOTAL_LINE_CAP=42500` 守住，但 **feature-chat 由「余 12 + 上限 3900」变为「余 60 + 上限 4000」**——上限被裁决抬高而非模块下沉，债务更尖锐（详见 B-1）。

---

## 一、本次新发现

> 本日对 `HEAD = 234eac0` 做了与 11-10 相同范围的逐模块 `wc -l` + 全仓 Grep 静态核对，并**专门审读了 `51be3f7` 新增/改动的 6 个 `.kt` 文件**（`SkillMention.kt` 全量 + `ChatInputBar.kt`/`ChatScreen.kt`/`ChatViewModel.kt`/`AppNavHost.kt` 的 diff）。源码对象已变化 ⇒ 本日**有新观察**。

### （a）需要改进的实现

- **A1 — `runCatching` 包裹 suspend 的 `skills.list()`，会吞掉 `CancellationException` 且错误静默下沉**
  - 位置：`feature-chat/.../ChatViewModel.kt` 的 `init` 块（约 L69–L76）：`viewModelScope.launch { _skillNames.value = runCatching { container.skills.list() }.getOrDefault(emptyList()).map { it.name } }`。
  - 现象：`SkillCatalog.list()` 是 **`suspend fun`**（`core-agent/.../skill/SkillCatalog.kt:45`，实现 `SkillCatalogImpl.list()` 亦 suspend，疑似磁盘/DataStore 读取）。`runCatching` 捕获 `Throwable`，**包含 `CancellationException`**——ViewModel 在加载途中被清除导致的取消会被当成「成功拿到空列表」，而非向上传播。
  - 风险：① 取消被吞，违背 W84「`runCatching` 不吞 `CancellationException`」纪律（本处是 suspend 调用，非纯阻塞 IO，故非「形式对齐」豁免情形）；② `getOrDefault(emptyList())` 让**任何 I/O 失败都静默变成空列表、无任何日志** ⇒ 技能目录加载一旦回归，下拉面板永久不出现、功能静默死亡，且无 trace 可查。
  - 建议（文字描述，非改动）：在 `runCatching` 内对 `CancellationException` 显式 `throw` 重抛；对非取消类异常 `log` 后回退空列表（或显式暴露加载失败态），避免「失败即静默空」。

- **A2 — `decorate()` 对任意 `@token` 误触发，向无关消息注入假的 skill 加载引导**
  - 位置：`core-agent/.../skill/SkillMention.kt:37`（`MENTION = Regex("@([A-Za-z0-9._\\-]+)")`）+ `:65` `decorate()`；调用点 `ChatViewModel.kt:355`（`userInput = SkillMention.decorate(text)`，每次发送必过）。
  - 现象：`extractMentions` 的正则把**任何** `@字母/数字/点/下划线/连字符` 串（含邮箱 `a@b.com`、用户名 `@user`、Markdown）都当 skill 引用提取；`decorate()` 不校验该名是否真实存在于 `SkillCatalog`，一律在末尾追加英文「Load each with skill_load …」引导。
  - 风险：用户消息里含邮箱/手柄（如「联系 foo@bar.com」）会被注入假的 `@bar.com` 加载引导，模型可能去 `skill_load` 一个不存在的 skill，浪费一轮或产生混淆。测试仅覆盖「普通句子无引用」与「@notes-organizer」，无邮箱/用户名负例。
  - 建议：装饰前先以已知 skill 目录做一次**存在性过滤**（只装饰真实存在的 skill 名），或对明显像邮箱/域名的串跳过；先把是否「真命中目录」作为 `decorate` 的前置判据。

- **A3 — `onPickSkill` 用 `maxOf(lastIndexOf('@'), lastIndexOf('/'))` 截断，可能静默丢字**
  - 位置：`feature-chat/.../ChatScreen.kt`（`ChatBottomArea` 内 `onPickSkill`，约 L304–L310）。
  - 现象：选中下拉项时，逻辑取输入中**最后一个** `@` 或 `/` 的位置 `cut`，`base = cur.take(cut)`，再拼接 `@name`。但下拉面板只在输入**以** `/` 或 `@` **开头**时才弹出（`isTrigger` 只看开头）。
  - 风险：若用户输入以 `@foo` 开头、随后正文又含一个 `/`（如路径 `@foo 看 /sdcard/x`），`cut` 会落到最后的 `/`，`base` 直接砍掉 `/sdcard/x` 及之后全部内容 ⇒ **用户已输入文本被静默截断丢失**。该路径与「面板锚定在开头」的语义不一致。
  - 建议：截断应基于「触发面板时的开头触发 token」位置，而非整串最后一个 `@`/`/`；或仅在确认触发 token 位于行首时移除该前缀，绝不对正文里后续出现的 `/` 或 `@` 做切割。

- **A4 — 引用下拉 `DropdownMenu(expanded = true, onDismissRequest = {})` 无法被外部点击关闭**
  - 位置：`feature-chat/.../ChatInputBar.kt` 约 L131–L141（`if (onPickSkill != null && SkillMention.isTrigger(draft)) { … DropdownMenu(expanded = true, onDismissRequest = {}) … }`）。
  - 现象：只要 `draft` 以 `/` 或 `@` 开头且能匹配到 skill，`expanded` 恒为 `true`，且 `onDismissRequest = {}` 使点击面板以外区域**不会关闭**菜单。
  - 风险：用户一旦输入触发前缀且有匹配项，下拉就「粘」在屏幕上，只能选一项或删掉触发符才能消去，体验上把输入框卡住；若匹配项恰好非空但用户想放弃引用，无退出路径。
  - 建议：用真正的可见状态驱动 `expanded`（而非恒 `true`），`onDismissRequest` 置为关闭该状态；并在过滤结果为空时自动收起。

### （b）不合理的功能设计

- **B1 — feature-chat 上限被用户裁决「调高」而非拆分，债务更尖锐（演进自 11-09 的 B-1）**
  - 位置：`scripts/arch-guard.sh:191`（`feature-chat|500|4000`）+ `arch-guard.sh:186-190` 注释（W16 裁决留痕）+ `docs/handoff-20261111-wave16.md` §九.2。
  - 现象：本波对话框 skill 触发净增约 55 行 UI 接线，`feature-chat` 由 3888/3900（余 12）触顶 ⇒ **用户显式裁决把上限 3900→4000**，现 3940/4000（余 60）。
  - 风险：① 这正踩「拆分当扩容」铁律（W5 铁律 3 的反面），与现役「禁调 LINE_CAPS」纪律直接冲突（仅因用户裁决覆盖而合法，但注释自承「不构成先例、下波仍须先下沉 W16-03」）；② 余量从「12（上限未动）」变成「60（上限被抬）」表面宽松、实则**地基更虚**——下次任何 chat UI 增量又会立刻顶到 4000，届时再无「调高」余地、只能紧急拆分；③ W16-03（拆分 feature-chat）仍 `OPEN`，下沉窗口被本裁决挤占、更紧迫。
  - 建议：把 W16-03 拆 feature-chat 列为下个聊天功能波的前置硬约束；**下一波严禁再次调高上限**，优先把 `ChatInputBar`/`ChatScreen`/`ChatBottomArea` 的纯函数外提或下沉到有余量的模块（core-agent/core-skills 仍有千行级余量）。

### （c）缺失的功能

- **C1 — 缺少「@引用名 → SkillCatalog 真实存在」的校验门（导致 A2 假阳性）**
  - 现象：`SkillMention` 目前是完全无状态的「正则提取 + 英文引导拼接」，**没有任何一处拿提取到的名字去 `SkillCatalog.list()` 做存在性校验**。这与现役「组件有测试≠组件被使用」纪律形成对照——`SkillMention` 本身已被 3 处调用（消费链存在，正面实例），但其「装饰内容正确性」缺少一道事实校验。
  - 风险：见 A2。本质上是「装饰动作」与「skill 目录事实源」之间缺一道门。
  - 建议（文字描述）：在装饰/面板决策处引入一道「引用名是否在已知 skill 集合内」的判定；不在集合内则不装饰、面板也据真实匹配过滤（与 A2 同源，列为缺失能力而非纯实现 bug）。

> **本日新发现 a/b/c = 4 / 1 / 1**（A1–A4、B1、C1）。均为对 `51be3f7` 新增代码的静态推演，未改任何源码。

---

## 二、与架构铁律的偏离风险清单（本日重新核对）

| 铁律 | 现状（HEAD `234eac0` 核对） | 偏离风险 |
|---|---|---|
| TOTAL_LINE_CAP 不许变胖（W5 铁律 3） | 守住（≈37494/42500=88.2%）；本波未改 `TOTAL_LINE_CAP` | ✅ 满足；⚠️ feature-chat 上限被裁决抬至 4000（B1，用户覆盖，不构成先例） |
| 拆分不调上限 | 本波 `feature-chat` 因触顶**调高上限**而非下沉（B1） | 🔴 违反「禁调 LINE_CAPS」铁律，仅用户裁决覆盖；W16-03 下沉仍 OPEN |
| 组件有测试≠组件被使用 / 新增组件须配被调用判据 | `SkillMention` 被 3 处真实调用（`ChatViewModel.decorate` / `ChatInputBar.isTrigger` / `ChatBottomArea.onPickSkill`），`SkillMentionTest` 5 条断言已覆盖提取/触发/装饰 | ✅ 正面实例（与 W12-02 四态零读者相反） |
| 已登记例外须按期消除 | `LiquidBottomTabs` 1012>600（core-design）例外自 W2U-2 未拆 | ⚠️ 延续 |
| 平台策略先于代码拒绝你 | MCP cleartext 仍被 `usesCleartextTraffic=false` 挡（W5-03）；设备侧 1M 双内核阻断（W9-11 未解锁） | ⚠️ 需裁决，非代码可解 |
| DI 只走 AppContainer | `SkillMention` 是**无状态、零依赖 `object`**（仅 regex+字符串，无任何 import、无 injected 字段），与现役 `HardBudgetShrinker`/`EventReducer`/`TokenEstimator` 等同属「无状态工具持有者」范畴，非服务定位单例；`ChatViewModel` 经 `container.skills` 取目录（DI 合规） | ✅ 未见违规 |
| 模块边界（G6/G8） | `SkillMention.kt` 无任何 import（不引 engine/net/data/design）；`core-agent`→`core-agent.skill` 包内引用合规；core-model 反向边 grep 0 命中 | ✅ 干净 |
| 禁注解处理器/Retrofit/图片库（G4） | grep Retrofit/Hilt/Koin/Room/Coil/Dagger/Glide/Picasso = 仅注释，0 真实 import/调用 | ✅ 干净 |
| `@Serializable` 禁密钥字段（G17） | 本波无新增 `@Serializable` 数据类带密钥字段 | ✅ 干净 |
| `WindowInsets.ime` 不进 `derivedStateOf` 普通 lambda | 本波未触碰；`GlassScaffold.kt:129`/`ChatScreen.kt:111` 仍先取 `val imeInsets = WindowInsets.ime` | ✅ 维持 |
| `runCatching` 不吞 `CancellationException`（W84） | **本波 `ChatViewModel.init` 用 `runCatching { container.skills.list() }` 包裹 suspend 调用**（A1） | 🔴 新偏离风险：suspend 路径下会吞 CE + 错误静默 |
| 裸 `assertFailsWith{}` 收尾致 initializationError（W80） | 测试源集 `grep assertFailsWith` = 0 命中 | ✅ 无风险 |
| 不引入图片库 / core-agent 不碰端侧引擎细节 | `SkillMention` 纯文本解析，未引 litertlm/引擎 | ✅ 合规 |
| G16 中文硬编码棘轮（请求体零中文） | `decorate()` 引导文案**刻意英文**（KDoc 自注），未向请求体注入中文 ⇒ 不破坏前缀缓存稳定 | ✅ 与 G16 一致 |

---

## 三、建议优先级（与是否进 ledger）

- **P0**：无（无构建阻断 / 无安全泄漏 / 无数据破坏性已发路径；A3 为偶发边界丢字，非必现主路径）。
- **P1**：
  - **A1（runCatching 吞 CE + 错误静默）**——suspend 路径违反 W84，且失败不可观测。建议进 **roadmap/W16-03 同波修**，或单独立账 `W16-19`（技能目录加载错误可观测 + CE 重抛）。
  - **A3（onPickSkill 截断丢字）**——真实数据丢失路径，建议在 W16-03 同波修（小改动、高风险）。
  - **B1（feature-chat 上限被裁决抬升）**——已留痕、W16-03 `OPEN`；本波仅**重申更尖锐**，不重开号，但应作为「下个 chat 波前置约束」写入台账。
- **P2**：
  - **A2 + C1（decorate 假阳性 / 缺存在性校验门）**——健壮性改进，建议进 ledger 作为 `W16-20`（skill 引用存在性校验）或并入 W16-03。
  - **A4（下拉不可外部关闭）**——UX 体验，建议进 ledger 低优先。
  - **延续**——core-context 1646/1650（余 4，B-1 临界，W11-03 前置约束）；feature-settings 3492/3500（余 8）；`LiquidBottomTabs` 例外；W12-02 四态零读者；W9-05（第 11 顺延）；W11-03/W11-05。

**是否进 roadmap / deferred-ledger**：本波**新增立账建议** A1→`W16-19`、A2/C1→`W16-20`（均为候选，待人工裁决）；B1/W16-03 状态重申不重开号。其余均沿用既有挂账。

---

## 四、未覆盖 / 需真机验证项（不可仅靠静态判定）

- **skill_load 实际完成未被确证（11-11 工作记忆 §八）**：真机输入 `@`/`/` 已验证下拉列出 `notes-organizer`/`pdf-toolkit`、选中回填、发送出轮（1/12），但 **`skill_load` 是否真正完成未确认**（mistral 该轮 >59s 未收敛 + 取证工具限制：`matrix.texts()` 过滤空节点读不到 draft、`tail` 日志滞后）。静态只能确认「装饰引导已进请求体」，无法确认模型是否据此调用 `skill_load`——本功能端到端效果依赖未验证的模型行为，须补真机取证后再断言「手动触发」成立。
- **A1 的 CE/错误静默**：只有取消或 `skills.list()` 真实失败时才暴露；静态无法触发，须真机/单测注入失败路径验证「失败可见」。
- **A3 丢字**：仅在「开头 @ + 正文含 /」组合下触发，静态可证、真机罕见，但一旦命中即数据丢失，建议单测构造该串验证。
- **OEM 差异**：唯一直设备 OPPO PDRM00 / Android 13；华为/HarmonyOS、小米、vivo 的 `usesCleartextTraffic`、Keystore、屏幕密度未验。
- **端侧模型行为**：仅 NV-VL 实测 1M 窗口三通；设备侧 1M 受 RoundHeadGate 400k 阻断（W9-11 未解锁）。
- **W12-02 四态消费端缺失**：本波未新增四态读者，`StreamAccumulator.cacheStatus` 在 main 仍零读者，四态观测仍半落地。

---

## 结论

本日（2026-11-11）分析对象相对 **2026-11-10**（`b0a6e44`）**源码已变化**：`HEAD = 234eac0`，自上次以来 `src/main` 唯一改动来自 feat 提交 `51be3f7`（对话框 `/` 指令与 `@` 引用手动触发 skill），并配套 docs 提交 `234eac0`。本日逐模块行数水位表已重算（全仓 ≈37494/42500=88.2%，较 11-10 +129）；重点变化是 **feature-chat 3940/4000（上限被裁决抬升）**、core-agent 5443（新增 `SkillMention.kt`）、app 1359。

静态核对结论：

1. **本日新发现 a/b/c = 4 / 1 / 1**：A1 `runCatching` 包裹 suspend `skills.list()`（吞 CE + 错误静默，W84 偏离）、A2 `decorate()` 对任意 `@token`（含邮箱）误注入加载引导、A3 `onPickSkill` 用末位 `lastIndexOf` 截断可能静默丢字、A4 下拉 `onDismissRequest={}` 无法外部关闭；B1 feature-chat 上限被裁决抬高而非拆分的债务更尖锐；C1 缺 skill 引用存在性校验门。全部为对 `51be3f7` 新增代码的推演，未改任何源码。
2. **架构铁律静态基本绿**：DI 只走 AppContainer（`SkillMention` 为无状态零依赖 `object`，与现役工具持有者同范畴，非服务定位单例）；模块边界无违规边（G6/G8）；G4 黑名单/G17 密钥字段/`WindowInsets.ime`/`assertFailsWith` 均合规；`decorate` 英文引导与 G16 中文棘轮一致。唯一**新偏离**是 A1 的 `runCatching` 吞 CE（suspend 路径）。
3. **正面实例**：`SkillMention` 被 3 处真实调用且 `SkillMentionTest` 5 条断言覆盖——与 W12-02「组件有测试≠被使用」形成对照，说明本波新增组件接线良好。
4. **延续预警（非新立账）**：core-context 1646/1650（余 4，B-1 临界）、feature-settings 3492/3500（余 8）、`LiquidBottomTabs` 例外、W12-02 四态零读者、`W9-05`(第 11 顺延)/`W11-03`/`W11-05` 均维持未解。

**全程只读，未改动任何源码/配置文件、未 `git add`/`commit`/`push`、未执行任何会改动设备的 adb 命令。**

> 与 N 天前结论的演进：相对前次（2026-11-10，a/b/c=0/0/0 静止基线）——
> - **源码对象已变**（新增 `51be3f7` 对话框 skill 触发功能波），本日**从「无新增」转为「新发现 4/1/1」**；
> - 行数水位：feature-chat 由「3888/3900 余12」变为「3940/4000 余60」——**上限被裁决抬高而非拆分**，B-1 预警演进为更尖锐的 B1 债务；全仓 87.9%→88.2%；
> - 新增铁律偏离 A1（runCatching 吞 CE，suspend 路径）；其余静态判据与 11-10 一致维持；
> - 真机侧「skill_load 实际完成」仍未被确证，端到端效果待补取证。
