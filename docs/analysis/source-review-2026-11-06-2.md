# LiquidAgent harness-v2 源码日析 — 2026-11-06（第二次同日复核，W9 工作树）

> 只读静态分析 + 改进推演。**全程未改动任何源码/配置文件**，仅在 `docs/analysis/` 新建本文档。
> 分析对象：`<workspace>/harness-v2`（现役仓）。
> ⚠️ 本次同日已存在 `source-review-2026-11-06.md`（首份，当时工作树仅 W8 文档、W7 已提交）；
> 自那次之后**工作树出现了 W9-00 未提交改动**（端点高级配置防线接线 / 思考降级上屏 / 图片闸门响应式 /
> 探针序列判定 / Markdown 扩展等），故本次以 `-2` 后缀新建，避免覆盖首份的 D1/D2 订正与 W7 水位表。

## 本次依据（版本与水位）

- **工作记忆**：`MEMORY.md` + 最新日志 `2026-11-06.md`（W8 已推 main `ace64de`+`e7d7a1e`、CI success）+ 自动化记忆 `automations/9eac1478…/memory.md`。
- **项目文档**：`docs/06-deferred-ledger.md`、`docs/notes/w9-plan.md`、`docs/handoff-20261106-wave9.md`、`docs/notes/w9-feature-matrix-preregistration.md`。
- **架构闸门**：`scripts/arch-guard.sh` 的 `LINE_CAPS`（约 152–193 行），`TOTAL_LINE_CAP=42500` 未动；`VACUOUS_BASELINE=1`。
- **git 状态核实**：`HEAD = e7d7a1e`（W8 已推 main）；工作树含 **W9-00 未提交**改动 —— 19 个 `**/src/main/**` 文件变更（+335/−153）、若干 `*Test.kt`、`docs/`、`build.yml`(M)。**本日分析对象即该 W9 未提交工作树**，与首份同日文档（仅 W7 提交态）存在实质差异。
- **前次文档**：`source-review-2026-11-06.md`（首份，a/b/c 全 0、两处订正 D1/D2、延续 A3/A4/B1/B2/B3/C1–C5）。

### arch-guard 当前行数水位（本日 `find …/src/main/**/*.kt` 逐模块 `wc -l`，全仓现求和）

| 模块 | 实测行 | 上限 | 余量 | 水位 |
|---|---:|---:|---:|---:|
| core-model | 2038 | 2600 | 562 | 78% |
| core-net | 790 | 1200 | 410 | 66% |
| core-engine | 1193 | 2600 | 1407 | 46% |
| core-kernel | 403 | 450 | 47 | **90%** |
| core-context | 1584 | 1650 | 66 | **96%** |
| core-agent | 5326 | 6500 | 1174 | 82% |
| core-skills | 419 | 1000 | 581 | 42% |
| core-mcp | 758 | 1400 | 642 | 54% |
| core-data | 2388 | 4500 | 2112 | 53% |
| core-design | 12450 | 12600 | 150 | **99%** |
| feature-chat | 3777 | 3900 | 123 | **97%** |
| **feature-settings** | **3492** | **3500** | **8** | **99%（最紧）** |
| feature-tools | 947 | 2600 | 1653 | 36% |
| app | 1358 | 1600 | 242 | 85% |
| **TOTAL** | **36923** | **42500** | 5577 | **87%** |

> 相对首份同日文档（W7 口径：core-model 1439 / feature-settings 3229 / feature-chat 3619 / core-design 12439），
> W8+W9 实际把 **feature-settings +263**、**feature-chat +158**、**core-model +599**、**core-design +11** 推高。
> 全仓总量仍守住 42500（87%，满足 W5 铁律 3「不许变胖」），但 **per-module 高位带显著收紧**——这是本日与首份同日文档最大的口径差异。

---

## 一、本次新发现

> 全部来自对 W9-00 未提交工作树的 `git diff` 静态核对（`core-model`/`core-engine`/`core-data`/`feature-chat`/`feature-settings`/`app`）。
> 每条含文件:行号、现象、风险、建议（绝不附代码改动）。

### （a）需要改进的实现

- **A1 — `supportsAudioOutput` 派生属性在 W9 P2-AUDIO 工作后仍零消费**（B3 家族新实例）
  - 文件：`core-model/RemoteEndpoint.kt:206` 声明 `val supportsAudioOutput`；全仓 `grep -rn supportsAudioOutput --include=*.kt .` **仅命中其声明处**，无任何读取点。
  - 现象：W9 P2-AUDIO 选 (a)「输出模态过滤为 TEXT/IMAGE」的落地方式是 `EndpointAdvancedConfig.kt` 里 **硬编码** `Modality.entries.filter { it != Modality.AUDIO }`，而非由 `supportsAudioOutput` / `outputModalities` 派生值驱动。
  - 风险：该项目铁律「组件有/派生有 ≠ 被使用」的同类缺陷在 W9 后仍**未闭环**——`supportsAudioOutput` 是个「看起来有音频能力判定、实际没人读」的装饰字段，会误导后续维护者以为存在音频能力闸门。
  - 建议：二选一 —— ① 让输出模态 toggle 列表由 `endpoint.outputModalities`（或 `supportsAudioOutput`）真正驱动；或 ② 删除该未消费派生值（AUDIO 模态在能力模型里本就无供应商/TTS 通道支撑）。不要保留既硬编码过滤、又声明派生值的双重表述。

- **A2 — `ThinkingMode.REASONING_ONLY` 标签分支在现役调用点下不可达**（残留死分支 / 标签不一致）
  - 文件：`feature-settings/EndpointAdvancedConfig.kt:52`（`ThinkingMode.entries.filter { it != ThinkingMode.REASONING_ONLY }`）+ `:186` `thinkingModeLabel` 的 `else -> R.string.adv_mode_ON`。
  - 现象：P0-4 选 (b) 已从 UI 列表过滤掉 `REASONING_ONLY`（仅为老 `endpoints.json` 反序列化兼容保留枚举值），但 `thinkingModeLabel` 的 `else` 分支仍把 `REASONING_ONLY` 映射到「开思考」标签。
  - 风险：该 `else` 分支在现役调用点（已过滤的 `PickRow` 列表）下**永不触发**；若未来有人误以为 `REASONING_ONLY` 仍可展示，会拿到错误的「ON」文案。属「退役枚举的标签未同步退役」。
  - 建议：把 `thinkingModeLabel` 对 `REASONING_ONLY` 显式 `@Deprecated` 或抽出 `displayThinkingModes()` 扩展彻底排除；或保留枚举值但在 `thinkingModeLabel` 加 KDoc 注明「仅供反序列化兼容、UI 永不展示」。

- **A3 — `judgeProbe` 与 `judgeProbeSequence` 两判定入口并存，旧名语义漂移**（P2，低）
  - 文件：`core-data/EndpointProbe.kt:125` `judgeProbe(terminal, sawContent)` + `:138` `judgeProbeSequence(events)`。
  - 现象：W9 P1-5 把探针判定从「`firstOrNull` 谓词累积」改为「收至终帧 + `judgeProbeSequence` 序列归约」，序列函数内部仍调旧 `judgeProbe`。旧名隐含「Probe 进出」但实收 (终帧, 布尔)，语义已漂移；且 `judgeProbe` 还被 `EndpointProbeTest.kt:36/43/50` 直接消费，重命名会牵动测试。
  - 风险：维护者易混淆「单终帧判定」与「序列判定」两个入口，误用旧函数会漏掉「终帧后同帧 Usage」证据（正是 P1-5 要修的反例）。
  - 建议：将旧 `judgeProbe` 重命名为 `judgeTerminal(terminal, sawContent)` 以区分二者；同步改测试引用。优先级 P2（不阻塞）。

### （b）不合理的功能设计（与架构铁律 / 设备现实冲突处）

- **B1 — `feature-settings` 行数水位已耗尽（余 8 行），后续 W9-07/W9-12 改动将立即触顶**（P1，本日最高优先）
  - 文件/水位：`feature-settings` 实测 **3492 / 3500**，余 **8** 行。W9-00 净增 +10（EndpointAdvancedConfig +16 / EndpointEditScreen −2 / SettingsViewModel −4），满足 `w9-plan.md §五.1` 口径「净增 ≤ 18」，且**未再抬高 `LINE_CAPS`/`TOTAL_LINE_CAP`**（W8 的 +100 一次性例外未被引用，合规）。
  - 风险：绝对余量已耗尽。挂账中的 **W9-07（降级 reason 日志出口）**、**W9-12（思考四态/五档 UI 可达性）** 任何 feature-settings 改动都会立刻撞 3500 红线；而 plan 给的「纯逻辑下沉 `core-model`」策略本波已把 core-model 净 +106（2038，cap 2600 余 562），继续下沉会侵蚀 core-model 余量，形成「拆东墙补西墙」的 per-module 翻版（首份同日文档 B1 的同型复发）。
  - 建议：W9-07/W9-12 开工前**先**制定 feature-settings 的拆分或进一步下沉方案（如把 `AdvancedConfigSection` 的若干 `ToggleSpec` 派生逻辑整组下沉 core-model，或拆出 `feature-settings-advanced` 子模块），不要等到触顶再拆。

- **B2 — `AppNavHost` 视觉闸门改用 `produceState` 直读 `container.activeEndpoint()`**（P2，低，标注非违规）
  - 文件：`app/AppNavHost.kt:164-179`。
  - 现象：P0-3 由 `LaunchedEffect(state.endpointLabel)` 改为 `produceState<RemoteEndpoint?>(initialValue, activeId, container)` 内调 `container.activeEndpoint()`，派生 `visionEnabled`。方向正确（响应端点快照而非显示名，修同名端点只勾图片不重算的 bug）。
  - 风险：**不违反**模块边界/DI 铁律（仍走 `AppContainer`，非散落单例；`app` 本就是顶层模块，依赖 core-data/core-model 合法）。仅提示：`produceState` 第二 key 传 `container`（单例常量）实际恒为常量，重算只靠 `activeId`，属有意设计，但可读性上建议在注释外再确认不会因 `container` 引用扩大重组范围（当前无问题）。

### （c）缺失的功能

- **C1 — `SamplingParams.reasoningEffort` 仍为「未接线、勿用」装饰字段**（B3 家族，P2）
  - 文件：`core-model/Generation.kt:79-80`（KDoc 已订正为「唯一真值源 = `RemoteEndpoint.reasoningEffort`」，字段仍默认 `"medium"`）。
  - 现象：全仓 `grep '\.reasoningEffort'` 排除 endpoint 后**零消费**；`SamplingParams.reasoningEffort` 既不被 `core-agent` 读取，也不被 `OpenAiCompatibleAdapter` 使用。
  - 风险：字段仍存在于数据类、默认值 `"medium"` 会给人「填了就生效」的错觉，但运行时被静默忽略，无编译/守卫拦截。属「声明但未消费」的装饰字段，与 B3 同型。
  - 建议：W9-06 范围内要么在构建 `Generation` 处加 `@Deprecated` + lint/守卫拦截，要么直接删除该字段（届时须同步修 `Generation` 构造与测试）。

---

## 二、与架构铁律的偏离风险清单（本日重新核对，静态全绿处标注 ✅）

| 铁律 | 现状（W9 工作树核对） | 偏离风险 |
|---|---|---|
| TOTAL_LINE_CAP 不许变胖（W5 铁律 3） | 守住（36923/42500=87%）；W9 未抬上限 | ✅ 满足；但 per-module 高位带收紧（B1） |
| 拆分不调上限 | feature-settings 维持 3500（W8 +100 例外未被 W9 引用）；core-model 净 +106 但 cap 未动 | ✅ W9 合规；⚠️ B1：feature-settings 余 8 已触顶临界 |
| 守卫边界是包目录不是意图 | G18 宽匹配 `.generate\(` + `core-data/.../(RemoteEngineBridge\|EndpointProbe)\.kt` 白名单；W9 改 `EndpointProbe` 仍在该白名单内 | ✅ 合规（首份同日文档 D1 已订正，本日复核维持） |
| 组件有测试≠组件被使用 / 新增组件须配被调用判据 | **A-3/F-1 本波已闭环**：`validateAdvanced` 在 `SettingsViewModel.save`（落盘前 BLOCKED）+ `OpenAiCompatibleAdapter.applyCustomProtocol`（运行时兜底）两处真消费，`ADVANCED_INVALID` 枚举在 save/UI/测试三处接线并单测覆盖；`credentialishExtraBodyViolation` 单源复用。但 `supportsAudioOutput`（A1）、`SamplingParams.reasoningEffort`（C1）仍零消费 | ⚠️ A1/C1：B3 家族未全闭环；✅ A-3/F-1 是正面实例 |
| 已登记例外须按期消除 | `LiquidBottomTabs` 1012>600（core-design）例外自 W2U-2 未拆；feature-settings +100（W8）一次性例外已用尽 | ⚠️ B2（首份同日文档，延续） |
| 平台策略先于代码拒绝你 | MCP cleartext 仍被 `usesCleartextTraffic=false` 挡（W5-03）；设备侧 1M 双内核阻断 | ⚠️ 需裁决，非代码可解 |
| DI 只走 AppContainer | 本波 `AppNavHost`/`SettingsViewModel` 均经 `container` 取端点/设置；`OpenAiCompatibleAdapter` 的 `NON_FINITE_SCALARS` 是 companion object 常量（非服务定位）；未见散落 `object` 依赖单例/手动 `new` | ✅ 未见违规 |
| 模块边界（G6/G8） | 本波新增 import 仅向下：`core-engine→core-model`、`feature-settings→core-model`+`core-design`、`app→core-model`+`core-data`+`feature-*`；core-model 未反向引 engine/net/data/feature | ✅ grep 零命中违规边 |
| 禁注解处理器/Retrofit/图片库（G4） | 仅注释禁止，无实际依赖 | ✅ 干净 |
| `@Serializable` 禁密钥字段（G17） | main 源集无新增 `@Serializable` 含 apiKey/secret 字段；`ALLOWED_EXTRA_BODY_KEYS` 等是普通 `Set` 非序列化密钥 | ✅ 干净 |
| `WindowInsets.ime` 不进 `derivedStateOf` 普通 lambda | 本波未改动相关文件，维持 `GlassScaffold`/`ChatScreen` 先取 `val imeInsets` | ✅ 维持 |
| `runCatching` 不吞 `CancellationException`（W84） | `ConversationOps.kt:106/:180` 已按 Q1 把 `runCatching{…}.getOrDefault` 改为 `contextWindowCap(container)`（正确的取消语义），其余调用点仍 `catch (c: CancellationException){throw c}` 前置 | ✅ 合规（Q1 落地） |
| 裸 `assertFailsWith{}` 收尾致 initializationError（W80） | 测试源集无此用法 | ✅ 无风险 |

---

## 三、建议优先级（与是否进 ledger）

- **P0**：无新增（W8/W9 红线项已由开发席在树内施工，尚未集成收口，不在本日静态判定范围）。
- **P1**：
  - **B1** — feature-settings 行数水位 3492/3500（余 8）：W9-07/W9-12 开工前先定拆分/下沉方案，不要触顶再拆。
  - **A1** — `supportsAudioOutput` 零消费：让 AUDIO toggle 由派生值驱动，或删除该装饰字段（B3 家族实例化）。
- **P2**：
  - **A2** — `thinkingModeLabel` 对退役枚举 `REASONING_ONLY` 的 `else` 分支不可达，显式退役或加 KDoc。
  - **A3** — `judgeProbe` 重命名为 `judgeTerminal` 以区分序列判定入口（牵动测试，择机）。
  - **C1** — `SamplingParams.reasoningEffort` 装饰字段：`@Deprecated`+守卫 或 删（W9-06）。
  - **B2（延续）** — `LiquidBottomTabs` 1012>600 例外拆分；**C1–C5（首份同日文档延续）** — journal/FreezeClock(W3-05)、Skill 导入(W3-02)、MCP 真机联调(W5-03)、前缀缓存命中率(W7-02)、DeepSeek `prompt_cache_hit_tokens` TODO。

**是否进 roadmap / deferred-ledger**：本日**新增唯一建议立账项 = B1 的「feature-settings 触顶临界」**（建议作为 W9-07/W9-12 的前置约束写入台账/roadmap，而非新开挂账）；A1/C1 属既有 B3 家族实例化，不重复立账（已计入 B3）。其余为既有挂账状态重申。A-3/F-1 闭环为正面实例，不立账但标注已收口。

---

## 四、未覆盖 / 需真机验证项（不可仅靠静态判定）

- **W9-00 出口判据（D6/D7 及四态举证）**：`supportsVision` 响应式（P0-3）、思考降级 `reason` 上屏（P0-2/P1-1）、`validateAdvanced` 拦截（F-1）、`REASONING_ONLY` 删除（P0-4）、Markdown 扩展（MD-1/MD-2）均**仅静态核对**，真机两态取证（`w9-handoff §三 G5` / `§五.1`）未取；尤其 D6「同名端点只勾图片 ⇒ 返回对话页 attach 可点、禁用原因消失」须真机截图 + 设备侧日志。
- **W7-01 压缩真机重放**：32K 档 + 并行读 5×16KB ⇒ 实发 ≤ `hardContextBudgetTokens=27904`；免费模型该规模 `SocketTimeoutException`，须换 NV 端点。
- **W7-02 前缀缓存命中率**：三供应商实测不一致（nv 0→0→4336，dots/north 恒 0），缓存不可依赖，须真机取数。
- **W7-03 手动压缩 / 会话导入真机验收**：当前仅单测（含 W9 Q2 BOM 剥离 + Q3 空正文计数），真机双向证据未取。
- **W5-03 MCP cleartext**：需 https 夹具或 cleartext 例外**安全裁决**（平台行为，非代码可解）。
- **OEM 差异**：唯一直设备 OPPO PDRM00 / Android 13；华为/HarmonyOS、小米、vivo 的 `usesCleartextTraffic`、Keystore、屏幕密度未验。
- **端侧模型行为**：仅 NV-VL 实测 1M 窗口三通；其余供应商/模型长上下文、视觉、工具行为未系统验证；W9 多模型矩阵预登记各项（1M/记忆/Skill/MCP/KV cache/压缩）本波未行使，须按预登记四态取证，不得写成已验证。

---

## 结论

本日（2026-11-06 第二次同日复核）与首份同日文档的**实质差异 = 工作树出现了 W9-00 未提交改动**。静态核对结论：

1. **架构铁律静态全绿**：DI 只走 AppContainer；模块边界无违规边（G6/G8）；G4 黑名单/G17 密钥字段/`WindowInsets.ime`/`CancellationException` 重抛/`assertFailsWith` 均合规；G18 对 `EndpointProbe` 的引擎调用点白名单收口（D1 维持）。**W9 未引入任何新违规。**
2. **「组件被使用」纪律本波有正面闭环**：W8-W9 的 A-3/F-1「写了防线没人过」已落地——`validateAdvanced` 在保存链（落盘前 BLOCKED）+ 运行时（`applyCustomProtocol` 复用 `credentialishExtraBodyViolation` 单源）两处真消费，`ADVANCED_INVALID` 枚举 save/UI/测试三处接线并单测覆盖。
3. **新发现 a/b/c = 3/2/1**：A1(`supportsAudioOutput` 零消费)、A2(`REASONING_ONLY` 标签死分支)、A3(`judgeProbe` 命名)；B1(**feature-settings 3492/3500 余 8 触顶临界**)、B2(`AppNavHost` produceState 低风险提示)；C1(`SamplingParams.reasoningEffort` 装饰字段)。
4. **最高优先风险 = B1**：全仓总量守住 42500（87%），但 per-module 高位带在 W8+W9 后显著收紧（feature-settings 99%、core-design 99%、feature-chat 97%、core-context 96%），feature-settings 余量仅 8 行，后续 W9-07/W9-12 须「先拆/先下沉」再动手。

**全程只读，未改动任何源码/配置文件、未 `git add`/`commit`/`push`、未执行任何会改动设备的 adb 命令。**

> 与 N 天前结论的演进：相对首份同日文档（2026-11-06）——
> - 行数水位表由 W7 口径（core-model 1439 / feature-settings 3229 / feature-chat 3619）更新为 W8+W9 现测（2038 / 3492 / 3777），揭示 per-module 高位带收紧；
> - 首份同日文档 a/b/c 全 0（当时仅 W7 提交态），本份因 W9 工作树出现而新发现 3/2/1；
> - A-3/F-1「组件被使用」缺口在本波**已闭环**（正面实例，非新缺）；
> - D1（G18 非漏判）、D2（`allowClipboardRead` 仅缺 UI）订正与 B1/B2/C1–C5 延续项维持有效。
