# W8 交接 — 端点高级配置 + 气泡 Markdown + 返回 JSON 检查 + 多模型矩阵预登记（2026-11-06）

> 基线 `578f7e4`（W7 收口）→ 本波（提交号见 git log）
> 唯一派单口径：`docs/notes/w8-plan.md`（主理人定稿）。分析证据：`docs/analysis/w8-solution.md`（方案席 1055 行）、
> `docs/analysis/w8-review-triage.md`（审查席 597 行）、`docs/analysis/w8-provider-probe.md`（主理人实测）。
> 挂账唯一台账 `docs/06-deferred-ledger.md`（46 行；W9 承接表见 §二-bis）。

---

## 一、本波结论（先看这段）

| 项 | 结果 |
|---|---|
| 单元测试 | **741 / 0 / 0 / 0**（14 模块；W7 基线 696 → 净增 45，全部行为断言）。`EXPECTED_TESTS` 696 → **741** 同提交改 |
| 架构守卫 | **OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0**（G19 仍唯一 VACUOUS；未新增守卫、未新增 VACUOUS） |
| 守卫自证 | `arch-guard-selftest` **30/30 PASS**（改 `LINE_CAPS` 后重跑，未破坏自证） |
| 出包 | `assembleDebug` **BUILD SUCCESSFUL**，`:app:assembleDebug` 出 `app-debug.apk` |
| 行数 | per-module：feature-settings 3400 → **3500**（+100 一次性例外，裁决 `docs/notes/w8-line-budget-ruling.md`）；`TOTAL_LINE_CAP=42500` **未动** |
| 真机 | **冒烟通过**（OPPO PDRM00 / SDK 33）：安装 Success、启动无崩溃、三项新功能 UI 全部真机可达（见 §四） |
| 模块 | 仍 14 个（未新增 Gradle 模块） |

**一句话**：W8 是**用户功能波** —— 端点「高级配置」（工具/图片/思考四态/五档强度/自定义协议/JSON 模式/输入输出模态）、
气泡 **Markdown 渲染开关**、模型返回 **JSON 格式检查** 三件套全部落地并真机冒烟；四份外审主张的
「正确性 / 韧性收口」整体**显式改期到 W9**（台账 §二-bis 承接表，老号 `CARRIED` 不删行）。

---

## 二、交付清单（用户 W8 需求逐项）

### 2.1 端点高级配置（需求 ①）
- `core-model/RemoteEndpoint.kt`：新增 `inputModalities` / `outputModalities` / `thinkingMode` /
  `allowDisableThinking` / `supportedEfforts` / `customProtocol` / `jsonMode`（**全带默认值**，老 `endpoints.json` 缺键可读）；
  `ReasoningStyle` 扩第 7 值 `OPENROUTER_REASONING_OBJECT`；派生 `supportsVision` / `canHardOff` / `effectiveThinkingMode`。
- `core-model/EndpointCapabilities.kt`（新）：`Modality` / `ThinkingMode`（**单枚举**消解三布尔互斥）/
  `ReasoningEffort`（五档，`EXTREME` 永不下发 `max`）/ `normalizeEffort`（档位归一 + 显式降级落 `reason`）。
- `core-model/EndpointAdvancedValidation.kt`（新）：`RESERVED_BODY_KEYS` + `validateAdvanced`（G17 查不到 Map 内容，
  这是唯一防线）+ `parseExtraBody` / `ReasoningEffort.uiKey`。
- `feature-settings/EndpointAdvancedConfig.kt`（新）：高级配置 UI，**数据驱动**（`ToggleSpec`）+
  五档用 `GlassSettingRow` 列表（**刻意不用 `GlassSegmented`**：Compose bounds 在 uiautomator 不可靠）。
- `core-engine/ReasoningBody.kt`（新）+ `OpenAiCompatibleAdapter`：按 `thinkingMode` 发
  `reasoning:{"enabled":false}` / `{"effort":wire}` / `include_reasoning:false`；`jsonMode` 发
  `response_format:{"type":"json_object"}`；自定义协议最后 merge（保留键不可覆盖）。
- **图片能力三处同改**：字段派生 + `RemoteLlmEngine.capabilities()` + `AppNavHost` 闸门
  （未启用时**保留禁用态入口** + 无障碍原因，不静默隐藏）→ 销台账 `W3-07`。

### 2.2 气泡 Markdown 渲染（需求 ②）
- `feature-chat/MarkdownText.kt`（新）：`bubbleText(text, renderMarkdown)` + `parseMarkdown`，
  **零新依赖**（`AnnotatedString` + `SpanStyle`）；支持子集写进 KDoc 即判据口径。
- `core-design/GlassSurface.kt`：`GlassBubble` 加 `annotatedText`（**仅 1 参**，非流式分支生效，流式保持纯文本）。
- 开关 `SettingsRepository.markdownRender`（默认 **false** = 现役行为零回归），**单一真值源 = DataStore**，
  `AppNavHost` collect 作参数下传，**不进 `ChatUiState` / `ChatViewModel`**（后者余 13 行 + G23 钉死调用点）。

### 2.3 模型返回 JSON 格式检查（需求 ③）
- `core-model/JsonFormatCheck.kt`（新）：**独立严格 `Json`**（`isLenient=false`）——
  🔴 复用 `AgentJson.json` 会让「格式检查」**假绿**（它接受尾逗号 / 单引号 / 未加引号键）。
- 落点 = **气泡动作键**（与复制并列），否决「工具」落点（会新增 `.generate(` 触 G18）与全局逐条自动检查。
- 错误**只给行列号，不回显原文片段**（上屏风险 > 落盘风险）。

### 2.4 多模型矩阵（需求 ⑥）
- `docs/notes/w8-feature-matrix-preregistration.md`（新）：**跑前预登记**，判据先写死后不得改。
- PC 侧已实测闭环：`dots` 请求格式（`reasoning` 对象 / `enabled:false` 真关思考 / `include_reasoning` 仅隐藏仍计费 /
  五档梯度 / `response_format` / tools 需 `max_tokens≥512` / 视觉 200×120 真图），证据 `_w8/req-*.json` + `resp-*.sse` + `stream_probe.py`。

### 2.5 同波收的快修（外审低成本重合项）
- Q15 `EndpointProbe` 假绿/假红：预算 8→256 + 谓词改 `TextDelta||Usage`（`ReasoningDelta` 单独不算通过）+ 2 用例。
- Q1 `runCatching×2` → `contextWindowCap(container)`（取消语义）；Q2 UTF-8 BOM 剥离 + 2 用例；
  Q3 空正文先于条数上限 + 1 用例；Q7/Q7′ 三处「注释与实现打架」订正。
- Q11 adapter 逐键白名单断言；Q14 编号唯一源（台账头部声明 + 路线草稿的 W8 系列编号统一改名为 `R-xx`，
  使 `docs/notes/` 内不再出现与台账撞号的编号）。

---

## 三、闸门与真机证据

| 闸门 | 命令 | 结果 |
|---|---|---|
| 出包 | `localbuild.sh assembleDebug` | BUILD SUCCESSFUL（日志 `_w8/build_all4.log`） |
| 全量单测 | `scripts/fulltest.sh` | `tests=741 failures=0 errors=0 skipped=0`（日志 `_w8/fulltest2.log`） |
| 架构守卫 | `scripts/arch-guard.sh` | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（`_w8/archguard2.log`） |
| 守卫自证 | `scripts/arch-guard-selftest.sh` | **30/30 PASS**（`_w8/selftest.log`） |
| CI 基线 | `build.yml:132` | `EXPECTED_TESTS: '741'` ↔ 严格 `@Test` 741 双向对账 |

**真机冒烟（OPPO PDRM00 / Android 13 / SDK 33，`com.rickeal.liquidharness.debug`）**：

| # | 判据 | 四态 | 证据 |
|---|---|---|---|
| D1 | APK 可装可跑、无崩溃 | ✅ | `install -r` Success；启动 dump 出 `LiquidHarness` 首页（`_w8/dev_01_home.png`） |
| D2 | 图片闸门禁用态入口 + 原因 | ✅ | 首屏 `content-desc="当前端点未开启图片输入"`（未启用不静默隐藏） |
| D3 | 设置页 Markdown 开关 | ✅ | `气泡 Markdown 渲染` + `关闭时按纯文本显示（现役行为）`（`_w8/dev_03_settings_scrolled.png`） |
| D4 | 端点高级配置入口 + 展开 | ✅ | 编辑页 `高级配置` → 展开 `工具调用` / `图片输入` / `JSON 模式`（`_w8/dev_04_advanced.png`） |
| D5 | 思考四态 + 允许关闭思考 + 档位 | ✅ | `关思考` / `跟随服务端` / `仅隐藏思考（仍计费）` / `仅思考` + `允许关闭思考` + 档位 `低`…（`_w8/u11.xml`） |

---

## 四、多模型矩阵：已做与未行使（诚实四态）

**已行使（PC 侧）**：`dots` 请求格式九项（`docs/analysis/w8-provider-probe.md` §六，9 组 `stream:true`）。
**⛔ 未行使（不得写成已验证）**：设备侧 1M（双内核阻断，见 W9-11）、记忆 / Skill / 工作空间读写（本波未跑设备侧工具循环）、
MCP（`usesCleartextTraffic=false` 平台阻塞，W5-03）、KV cache 分块命中（OR 免费池 `cached_tokens` 恒 0 ⇒ 双向断言 vacuous；
NV 79.4% 是唯一正向证据）、手动/自动压缩 `applied=true`（至今零证据）。逐项四态见矩阵预登记文件。

---

## 五、方法论净产出（写给自己）

1. 🔴 **非流式探针 ≠ App 路径**：第一批 20+ 份探针全 `stream:false`，而 adapter 恒 `stream:true`。
   补跑 9 组流式后**结论反转**（`delta.reasoning` 与 `reasoning_details[].text` 是冗余双通道，现役回落命中）⇒
   **不改 `decodeEvent`、不造伪工作量**。教训：输出侧看着像坏，先查输入侧喂了什么（知识库 §4）。
2. 🔴 **外审「不许再延期」被重裁时必须留痕**：W8-03 原行「不许再延期」因用户功能波改期 ⇒
   台账 §二-bis 显式写改期理由 + 新号，否则就是第 4 次静默顺延。
3. 🔴 **触顶先压缩、后例外，且例外要有裁决文档**：feature-settings 先做四条合规压缩（数据驱动 /
   下沉 core-model / 拆文件 / 压注释），仍超 20 行 ⇒ 才走 +100 一次性例外并出 `w8-line-budget-ruling.md`。
4. 🔴 **JSON 校验不能复用 lenient `Json`**：`AgentJson.json` 的 `isLenient=true` 会让「格式检查」整个功能假绿
   ⇒ 必须独立严格实例。这是「组件写完 ≠ 组件生效」的又一形态。
5. 🔴 **两席并行必须文件域独占 + 都不改 `build.yml`**：A 席（端点/协议）/ B 席（会话显示）零冲突；
   `EXPECTED_TESTS` 由主理人最终一次性同步，避免并发改同一文件 + CI 双向对账中间态红。
6. 🔴 **成员 429 全灭 ⇒ 主理人接手补完，不重试 spawn**：本轮 A/B/CI/方案四席全因配额中断，
   主理人据已落盘的方案/审查文档自行完成实现 + 收口（磁盘实测为准，不采信完成通报）。

---

## 六、挂账承接（详见台账 §二-bis）

- **本波关闭**：`W3-07`（supportsVision 三处同改）；`W5-01` / `W7-01` / `W7-04` / `W7-07` 回填 CLOSED（CI 席收口）。
- **显式改期（老号 `CARRIED`，新号见台账 §二-bis）**：`W8-01`→`W9-03`、`W8-02`→`W9-04`、`W8-03`→`W9-06`、`W3-05`→`W9-05`。
- **W9 新开**：`W9-01` 压缩量纲统一 / `W9-02` L3 工具规格 / `W9-07` 安全收口 / `W9-08` 400 自愈 /
  `W9-09` 断流 STOP / `W9-10` 守卫与口径专项 / `W9-11` 设备侧 1M 内核熔断解锁。
- **维持**：`W5-03` MCP cleartext 平台阻塞；`W8-04` 编辑当前端点二次确认框（本波不动，仍 OPEN）。

---

## 七、W9 开工指引

1. 起手读：本文件 → `docs/notes/w8-plan.md` §八（挂账 H1–H11 + 流式裁决）→ 台账 §二-bis（W9 承接表）→
   `docs/analysis/w8-review-triage.md` §九（外审证伪与订正）。
2. **W9-01（压缩量纲统一）开波第一件事**：四外审同一缺陷，禁拆多条；先按 W8-plan §八.1 的 D9 四边界预登记。
3. **W9-05（journal）必须开波**：老化五波，第 5 次顺延 = 台账 §零 批判的病理复发。
4. 行数再触顶：**先拆**（优先 `McpServerEditScreen` 377 / `McpServerListScreen` 353），W8 的 +100 例外**不得引用为先例**。
5. `EXPECTED_TESTS` 现为 741；新增/删除测试必须同提交改 `build.yml:132`。
