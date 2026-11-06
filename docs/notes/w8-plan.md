# W8 作战方案 · 主理人裁决（2026-11-05）

> 基线 `578f7e4`（W7）。输入：`docs/analysis/w8-provider-probe.md`（主理人实测）、
> `docs/analysis/w8-solution.md`（方案席）、`docs/analysis/w8-review-triage.md`（审查席）。
> **本文是派单与验收的唯一口径**；三份分析文档是证据与推演，不是执行口径。
> 编号唯一来源 = `docs/06-deferred-ledger.md`；`docs/notes/` 里的 `W*-*` 历史输入与 `R-xx` 不参与派单。

---

## 一、波次裁决

**用户功能波为主波**，S0 对账批同波，四份外审主张的重型正确性/韧性重构整体挂账 W9。

| 批次 | 内容 | 执行者 |
|---|---|---|
| S0 | 台账/roadmap 回填、编号唯一源、矩阵跑前预登记、G10 尾换行可见性 | CI 席（文档/脚本域，不提交） |
| A | 端点高级配置 + 协议层 + 图片能力闸门 + probe 输出预算 | 开发席 A |
| B | Markdown 显示选项 + JSON 格式检查 + 设置项/参数链 | 开发席 B |
| 集成 | Q1/Q2/Q7 快修、`EXPECTED_TESTS` 一次性同步、四闸门、真机、推送、handoff | 主理人单人串行 |

**挂账（W9-0x，CI 席已入台账）**：W9-01 压缩量纲统一 / W9-02 L3 工具规格 / W9-03 尾部摘要（承接 W8-01）/
W9-04 三桶归一（承接 W8-02）/ W9-05 journal（承接 W3-05，**老化五波，不得第 5 次顺延**）/
W9-06 死字段二选一（承接 W8-03，**必须写「因用户功能波改期」显式裁决**）/ W9-07 安全收口 /
W9-08 400 自愈 / W9-09 断流 STOP / W9-10 守卫与口径专项 / **W9-11 设备侧 1M 内核熔断解锁**。
`W8-04` 保持 OPEN，本波不动。**禁用 `W8-05`**（路线草稿已占用）。

### 四个裁决落锤

1. **W8-03 不是快修**：审查席证伪——14 个文件、且争 `core-model/GenerationEvent.kt` 与
   `core-engine/RemoteLlmEngine.kt`（功能波必改）⇒ 挂 W9-06，不允许半套接线。
2. **1M 设备侧 ⛔ 不适用**：内核 `maxRunPromptTokens=400_000` + `RoundHeadGate` 熔断是硬阻断，
   本波不动 `core-agent` 策略。1M 结论**只限 PC 侧单次请求窗口**，矩阵报告不得写「设备侧 1M 可用」。
3. **流式断链点证伪**（本波最重要的方法论产出）：见 §二。
4. **W9-0x 编号采用**；journal 不独立开 W8.5，本波功能+真机+推送已饱和。

---

## 二、🔴 流式对照：结论与担心相反

方案席指出第一批 20+ 份探针全为 `stream:false`，而现役 `OpenAiCompatibleAdapter.kt:76` 恒
`stream:true`。主理人补跑 9 组 `stream:true` + `stream_options.include_usage=true`
（证据 `_w8/req-*.json` / `resp-*.sse` / `parse-*.json` / `stream_probe.py`，回写 `w8-provider-probe.md` §六）：

- 首帧 `delta` 同时含 `reasoning:"The"` 与 `reasoning_details:[{type:"reasoning.text",text:"The"}]`，
  **逐字等长**（basic 141/141、eff_high 1709/1709）⇒ 同一份思考的冗余双通道；
  `delta.reasoning_content` 恒 0。
- 现役 `:106-108` = `reasoning_content ?: reasoning` ⇒ **回落命中 `delta.reasoning`，思考本就能进
  `GenerationEvent.ReasoningDelta`**。⇒ **本波不改 `decodeEvent` 的思考/usage/终态分支**，
  也不新增 `reasoning_details` 兼容（同帧双发会造成重复 `ReasoningDelta`，属伪工作量）。
- usage 与 `finish_reason` **同帧且 `choices` 非空**（审查席 §九 W4 复跑 9 组 `empty_choice_frames=0`；
  现役 `:132-141` 的 `obj["usage"]` 解析独立于 choices 分支，两种帧位都能读）；流尾 `data: [DONE]`（`isTerminal` 已判）。
- 流式复证：关思考 `rtok=0 / content='PONG'`；`include_reasoning:false` 不回传但 `rtok=32` 照付；
  tools `finish=tool_calls`/3 碎片/name=ping；视觉 `red, blue`；`json_object` 严格合法 JSON。

**档位口径订正**：流式 `low 158 / high 441 / xhigh 193` reasoning_tokens 与非流式
`71 / 323 / 688` 排序不一致 ⇒ 单次样本不支持单调性结论。按 W7「≥2 次重放才写稳定结论」，
「五档真实梯度」标 **⚠️ 部分**，`max` 不直接判非法值，只登记事实表。设计含义不变：
wire 可识别值仍是 `low/medium/high/xhigh`，UI 第五档必须**显式降级并落 reason**，禁止静默发未验证值。

---

## 三、数据模型最终口径（方案席 §二，主理人采纳）

新增/复用一律放 `core-model/src/main`（G17 扫描面内），**全部字段带默认值**（老 `endpoints.json` 缺键可读）：

- 复用：`supportsTools`、`supportsReasoning`（能力位）、`reasoningEffort: String="medium"`（**唯一默认档真值源**，
  禁止新增第 3 个 effort 字段；`Generation.kt:80` 的 `SamplingParams.reasoningEffort` 是死字段，挂 W9-06 不动）、
  `kind`/`preset`/`baseUrl`/`extraHeaders`/`reasoningStyle`。
- 新枚举/DTO：`Modality{TEXT,IMAGE,AUDIO}`、`ThinkingMode{OFF,FOLLOW_PROVIDER,HIDE_ONLY,ON,REASONING_ONLY}`
  （**单枚举**消解三布尔互斥）、`ReasoningEffort{LOW,MEDIUM,HIGH,XHIGH,EXTREME}`（`@SerialName` 小写）、
  `CustomProtocol(enabled, extraBody: Map<String,String>)`。
- 新字段：`inputModalities`/`outputModalities`（默认 `setOf(TEXT)`）、`thinkingMode`（默认 `FOLLOW_PROVIDER`）、
  `allowDisableThinking`（能力约束）、`supportedEfforts`（**空集 = 未声明，不是全支持**）、`customProtocol`、`jsonMode`。
- 派生（不持久化）：`supportsVision get() = IMAGE in inputModalities`、`canHardOff`、
  `effectiveThinkingMode`（`supportsReasoning=false` ⇒ 强制 OFF）。
- `ReasoningStyle` **扩第 7 值 `OPENROUTER_REASONING_OBJECT`**；老 JSON 兼容，`when` 分支与
  `OpenAiCompatibleAdapterTest.kt:172-174` 的「不发字段样式」列表必须同步。
- 纯函数 `normalizeEffort(desired, supported, style, maxOut): ResolvedEffort` + `validateAdvanced()`
  落 `core-model/EndpointCapabilities.kt`；硬不变量 `wire != "max"`、降级必落 `reason` 且可被 UI/日志消费。
- **G17 红线**：命名避开 `apiKey/secret/password/token`；`extraBody`/`extraHeaders` 是 Map，
  G17 查不到值 ⇒ 键白名单由 `validateAdvanced()` 钉死，并补一条「保存后 `endpoints.json` 无明文凭据」判据。

**「输入/输出」裁决 = 模态集合**（OR `/v1/models` 的 `input_modalities`/`output_modalities` 可机器验证），
不是价格、不是 token 上限（后者已有 `contextLength`/`maxOutputTokens`，重复即第二把尺）。
音频输出本波恒不出现，UI 不摆假入口。

---

## 四、文件域（唯一写者，防 W7 双写覆盖实摔）

| 席位 | 独占文件 |
|---|---|
| **A** | `core-model/RemoteEndpoint.kt`、`core-model/EndpointCapabilities.kt`(新)；`core-engine/provider/OpenAiCompatibleAdapter.kt`、`core-engine/RemoteLlmEngine.kt`、`core-engine/ReasoningBody.kt`(新)、`core-engine/src/test/.../OpenAiCompatibleAdapterTest.kt`；`core-data/EndpointStore.kt`、`EndpointProbe.kt`；`feature-settings/EndpointEditScreen.kt`、`EndpointAdvancedConfig.kt`(新)、`res/values/strings_advanced.xml`(新) |
| **B** | `core-model/JsonFormatCheck.kt`(新)；`core-design/GlassSurface.kt`（**仅加 1 参 ≤10 行**，不新增文件）；`feature-chat/ChatBubble.kt`、`ChatMessageList.kt`、`ChatScreen.kt`、`MarkdownText.kt`(新)、`res/values/strings.xml`、`src/test/**`；`core-data/SettingsRepository.kt`（`markdownRender` 键）；`feature-settings/SettingsViewModel.kt`、`SettingsScreen.kt`、`SettingsRoute.kt`；`app/ui/AppNavHost.kt` |
| **共享禁区（主理人串行）** | `AppContainer.kt`、`ChatViewModel.kt`(487/500)、`ConversationOps.kt`、`scripts/arch-guard*.sh`、`.github/workflows/build.yml`、`docs/**`、版本矩阵、`LINE_CAPS`/`TOTAL_LINE_CAP`/`VACUOUS_BASELINE`/`EXCEPTIONS` |

🔴 **`JsonFormatCheck.kt` 判归 B**（方案席留的二义在此消除），A 不写。
🔴 **两席都不改 `build.yml`**：各自报告新增严格 `@Test` 数，主理人在最终集成提交里一次性同步
`EXPECTED_TESTS`（避免并发改同一文件 + CI 双向对账中间态红）。
🔴 **不新增守卫**（新守卫扫空集 ⇒ VACUOUS 1→2 ⇒ G21 判红）；不动 `LINE_CAPS`；触顶先拆。

---

## 五、实现要点与硬约束

1. **A · 协议层**：`buildBody` 加 `response_format`（`jsonMode` 驱动）、`include_reasoning`（仅 `HIDE_ONLY`）、
   `extraBody` 白名单 merge（**不得覆盖** `model/messages/stream/stream_options/max_tokens/tools`）；
   `applyReasoningStyle` 新分支发 `reasoning:{"enabled":false}` / `{"effort":wire}` / `{"exclude":true}`，
   **`effort` 与 `max_tokens` 绝不共存**；抽 `buildReasoningObject()` 到 `ReasoningBody.kt` 保 G11 ≤120 行。
   **不改 `decodeEvent`**（§二）、**不改 `contentElement`**（已正确发 image_url，只补回归用例）。
2. **A · 图片能力三处同改**：派生 `supportsVision` + `RemoteLlmEngine.kt:92` `capabilities()` +
   `AppNavHost.kt:156/170` 闸门（后者由 B 落，A 在回传里给出精确改法由主理人转交）。
   默认 false 会让既有端点图片入口消失 ⇒ **保留禁用态入口 + 原因提示**，不静默隐藏；真机判据覆盖启用前后各一次视觉往返。
   台账 `W3-07` 本波 CLOSED。
3. **A · probe**：`EndpointProbe.kt:57` 的 `maxTokens=8` 会被默认 reasoning 吃光 ⇒ 假绿/假红。
   提到 ≥256 并按 `thinkingMode` 处理，配一条判据（G18 例外已登记，不新增 `.generate(`）。
4. **A · Q11 同车**：`OpenAiCompatibleAdapterTest.kt` 补 2 条逐键白名单断言（`body.keys` 恰等于白名单、
   `toolObject.function` 只含 `name/description/parameters`）+ `response_format` 增减两态。
5. **B · Markdown**：**零新依赖**，`AnnotatedString`+`SpanStyle` 自绘轻量子集
   （`#`/`##`、`**粗体**`、行内代码、围栏代码块、`- ` 列表、链接只显锚文本）；不支持语法原样按纯文本显示。
   全局开关 `SettingsRepository.markdownRender` 默认 **false**（老用户零回归）；状态**不进 `ChatUiState`/`ChatViewModel`**，
   在 `AppNavHost.ChatRoute` 用 `collectAsStateWithLifecycle` 读并作参数下传。
   `GlassBubble` 加 `annotatedText: AnnotatedString? = null`，非流式分支 `Text(annotatedText ?: AnnotatedString(text))`；
   **流式分支保持纯文本 + 光标**，只在定稿气泡渲染（真机判据必须等 finish 后截图）。
   判据三层：纯函数 `parseMarkdown`（先跑已知必然通过样本）、「被调用」`bubbleText(` 形态断言、真机两态截图 + 日志。
   ⚠️ Compose span 在 `uiautomator dump` 不可见 ⇒ 点按/切换一律以 journal/日志/截图为准。
6. **B · JSON 检查**：两路分开。A 路 = 气泡动作键「检查 JSON」，`JsonCheckResult.Valid/Invalid(line,col)`，
   错误**只给行列号不回显原文片段**；容忍 ```json 围栏、空白、BOM，`allowFence=false` 时带围栏必判 Invalid（两态齐全）。
   **不能复用 `AgentJson.json`**（`isLenient=true` 会把非法 JSON 判绿）⇒ 独立严格 `Json` 实例 + `parseToJsonElement`。
   B 路 = 端点 `jsonMode` 驱动 `response_format`（A 席落请求体）。否决「工具」落点（G18）与「全局逐条自动检查」落点。
7. **判据自身先验证**：两席每新增一组判据，先跑一个已知必然通过/必然失败的样本（W3/W5 铁律）。
   JUnit4 方法返回 `Unit`、名禁 `. ; [ /`；裸 `assertFailsWith{}` 收尾会整类 initializationError。
8. **G16**：中文文案全进 `feature-*/res/values/strings*.xml`；`core-*` 只用英文常量。

---

## 六、验收判据（四态，出口逐条可跑真假）

| # | 判据 | 阈值 / 方式 |
|---|---|---|
| V1 | 老配置兼容 | 手写只含旧 15 键的 JSON → `decodeFromString` → 全部新字段为默认值 |
| V2 | 思考三态 | `OFF` 发 `enabled:false`；`HIDE_ONLY` 发 `include_reasoning:false`；`ON` 发 `effort`；三者不共存；`REASONING_ONLY` 不把空正文判断流 |
| V3 | 档位降级 | `EXTREME` 永不发 `max`，降级必落 `reason`；`wire != "max"` 硬不变量有反例锚 |
| V4 | 请求体白名单 | `body.keys` 恰等于白名单；`jsonMode=false` 不发 `response_format`；`extraBody` 不得覆盖保留键 |
| V5 | 图片能力 | 三处同改证据 + 未启用时禁用态入口可见 + 启用前后各一次视觉往返 |
| V6 | Markdown | 纯函数两态、`bubbleText(` 被调用、真机两态截图像素差 + 日志 |
| V7 | JSON 检查 | `{"a":1}` Valid / `{"a":}` Invalid 先绿；围栏两态；错误不回显原文 |
| V8 | 四闸门 | `assembleDebug` BUILD SUCCESSFUL；`fulltest` 严格 @Test 全绿且与 `EXPECTED_TESTS` 同提交一致；`selftest` 28/28；`arch-guard OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（**VACUOUS 只许减**） |
| V9 | 真机 | W8 D1–D10 逐条四态；新增高级配置保存后重进设置页字段回显正确；`endpoints.json` 无明文凭据 |
| V10 | 推送 | `git push --dry-run` 为真判据；PAT 仅 URL inline 一次性使用、绝不落盘；双工作流触发核对 |

**矩阵取证口径**：跑前预登记（`docs/notes/w8-feature-matrix-preregistration.md`）定死后不得改判据；
`max_tokens` 给足是判据前置条件；按端点分组登记 `max_tokens/prompt_tokens/completion_tokens/finish_reason`；
OR 免费池缓存 ⛔ 不可依赖（8KB 前缀四连发全 0，改首字节「归零」⛔ vacuous）；NV 79.4% 是唯一正向证据；
自动压缩 `applied=true` 零证据 ⇒ 只记 noop 并标 ⛔ vacuous；MCP 受 cleartext 阻塞 ⛔ 不适用；Skill 仅内置。

---

## 七、集成顺序（主理人单人串行）

1. `git status` 核实 A/B/S0 实际产出（成员回报不作数）；合并 A/B 的 `EXPECTED_TESTS` 增量，一次性改 `build.yml`。
2. 串行落 Q1（`ConversationOps.kt:106/180` → `contextWindowCap(container)`）、Q2（BOM 夹具 +2 用例）、Q7（附件 KDoc 订正）。
3. 四闸门；不绿不进真机。
4. adb 真机（唤醒 → 安装 → 端点注入 → D1–D10）；不绿不推送。
5. 提交 + push main + CI 核对；收口 W8 handoff（含 W9 改期裁决、W3-05 不得第 5 次顺延）。

---

## 八、采纳审查席补充交付（§八，覆盖前文对应表述）

审查席对方案席回赠的 4 条做了独立回树核实（`w8-review-triage.md` §八，零证伪）。以下**优先于前文**：

1. **设备侧 1M 的因果重写（覆盖 §一.2 与矩阵预登记）**：不是「请求根本发不出去」，而是**两条独立阻断**：
   ① `PreStepBudget.enforce`（`TurnLoop.kt:60`，在 gate 之前）；W7 真机档位 = 32 000 ⇒ `hard≈27 904` ⇒
   995k 在**发请求前**被收缩/trip（档位相关）；② `RoundHeadGate.check`（`TurnLoop.kt:58-78`）判的是
   **本 run 累计 `promptTokens`（首轮 0）** ⇒ 首轮会真实发出、**第 2 轮才熔断**，`AgentPolicy.kt:228
   maxRunPromptTokens=400_000` 不是首轮硬闸；③ 叠加 `compressedUpTo` 单指针压缩。
   `_w7/w7matrix.py:3-14` 确认 PC 侧是纯 Python 直连 HTTP、绕过 app 内核 ⇒ 1M 结论仍只限 PC 侧。
   矩阵报告必须写明两条阻断，不得只归 `maxRunPromptTokens`。
2. **「流式解码零证据」已闭合并改判（覆盖方案席 §3.1/§3.2 与审查席 §六出口判据）**：主理人已补
   9 组 `stream:true` 实测（§二）。结论是**现役链路本就通**，故 **不改 `decodeEvent`**；
   「`ReasoningDelta` 判据 ⛔ vacuous」的前提（没有流式证据）已消除——PC 侧 9 组流式响应已真实行使
   `delta.reasoning → ReasoningDelta` 的取值路径，真机 D 判据再验一次端到端。**不制造伪工作量**。
   审查席指出的「把非流式响应喂现役解码器 ⇒ 零事件 ⇒ `ProviderAdapter.kt:192` 静默标 STOP」是
   NEW-E/H9 的第二触发面，登记为 W9-09 的证据补充，本波不修。
3. **Q15 同波收（覆盖 §五.3 的简版 probe 修法）**：`EndpointProbe.kt:57 maxTokens=8` +
   `judgeProbe:115-119` 把 `ReasoningDelta` 算内容证据 ⇒ dots 默认开思考时「测试连接」**假绿**、
   `enabled:false` 时正文空 ⇒ **假红**；且 `:65 firstOrNull{}` 第一个事件就取消上游 ⇒
   **只把预算提到 ≥64 仍留假绿**。完整修法 = 预算 8→**≥64** + 谓词改 **`TextDelta || Usage`**
   （`ReasoningDelta` 单独不算通过）+ **2 条 JVM 用例**（开思考/关思考两态），约 20 行，
   `core-data` 域与功能波零冲突且与 ① 重合 ⇒ 归开发席 A 同波收。
4. **R4 顺手修（覆盖 §七.2）**：`Generation.kt:80 SamplingParams.reasoningEffort` 全仓零生产消费点，
   其 `:79` KDoc 却写「仅当 REASONING_EFFORT 时生效」⇒ C12 注释与实现打架**第 7 例**。
   本波顺手改这 1 行 KDoc（并入 Q7'），**字段本身挂 W9-06 不动**；再次背书「禁止新增第 3 个
   effort 字段」，`reasoningEffort` 单真值源只在 `RemoteEndpoint.kt:131`。
5. 夹具实测备查：`_w6/import-1m.jsonl` = `5 221 133 B / 30 行 / 无 BOM / 最长行 180 067 字符`
   （方案席写 180 068 是含换行口径差 1）；Q2 BOM 仍修（Windows 侧重新生成时会带 BOM）。
6. 审查席原 6 条证伪、W8-03 快修证伪、W9-0x 编号方案、§六 六项判据**全部维持**。


7. **审查席 §九 两条精度订正（措辞改、结论不改）**：
   - **W4 usage 帧位**：§二 已改「usage 与 `finish_reason` 同帧且 `choices` 非空」，
     `w8-provider-probe.md` §六.1 同步订正；9 组 SSE 独立复跑 `empty_choice_frames=0`。
     **不改 `decodeEvent` 的结论维持**。
   - **W3 `GlassSegmented` 因果证伪**：方案席 F21 引 `:33` 是 `LocalLayoutDirection` import、
     `:34` 是 `clearAndSetSemantics` import，**使用点在 `:366`** 且挂在 `alpha(0f)` 的隐形回显行；
     可见项 `GlassSegmentedItem.kt:74/77/89` 有 `clickable(role=Role.Button)` + `Text(text)` ⇒ **有语义**，
     「uiautomator 读不到分段项文字」不成立。但**五档仍改用 `GlassSettingRow` + `GlassSwitch`**，
     理由换为正面的三条：① Compose bounds 在 uiautomator 不可靠（知识库 §2）；② 复用六档窗口已验证通道；
     ③ 五档需要可读的选中态与降级 `reason` 展示。此条并入 §六 V9。
   - 方案席 `w8-solution.md` §12.1 的同一处帧位表述与引用精度（§八.5 / §八.3 节号）由方案席自行订正。
