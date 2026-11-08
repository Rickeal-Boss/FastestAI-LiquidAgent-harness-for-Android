# W9 下一波实施方案 + 多模型免费节点测试矩阵设计（方案席 沈思远，2026-11-06）

> 基准：本地 HEAD `767fd47`（W9-00 已收口，代码提交 `3fcd45e`，handoff 跑后结论已回填 `767fd47`）。
> 工作树干净（`git status --short` = 空）。本席**只产出文档、不修改任何 .kt 源码**（方案席纪律）。
> 所有 `文件:行` 均已回现役工作树 `grep` 复核，并标注「以符号为准」（外审行号已漂移）。
> 结论纪律一律含**判定四态**：✅ 通过 / ⚠️ 部分 / ⛔ 不适用 / ⛔vacuous（判据没被行使）。
> 凡引外审/交接行号，均已回源码 `grep` 复核并标出处（原文/中转）。

---

## 〇、现役源码复核结论（先证伪，再写方案）

所有 4 条 P1 + W9-NEW-1 + 纯视觉三缺口，**全部在 HEAD `767fd47` 仍可复现**（非 W9-00 已修）。逐条以符号复核：

| 项 | 现役落点（grep 复核） | 是否仍 live |
|---|---|---|
| P1-1 CRLF 围栏裁切 | `core-model/.../JsonFormatCheck.kt:74-80`（`stripJsonEnvelope` 围栏循环） | ✅ live：用 `s.lineSequence()`（Kotlin 按 `\n`/`\r\n`/`\r` 切且**剥掉分隔符**），`offset += line.length + 1` 对每个 CRLF 行少算 1（`\r` 已被剥离、+1 只补 `\n`），`endFence` 整体左移 → 截掉合法 JSON 尾部被判 Invalid |
| P1-2 测试连接收完整流 | `core-data/.../EndpointProbe.kt:65-99`（`test()` 的 `takeWhile`+`collect`） | ✅ live：collect 缓冲全部事件至 `Finished` 才停，与顶部 KDoc「不花 token 钱等完整回答」打架；`takeWhile` 实为 no-op 过滤（terminal 前事件全收）；token/延迟较「首事件即取消」回归 |
| P1-3 produceState key 不含能力位 | `app/.../AppNavHost.kt:171-175`（`activeId` + `produceState(activeId, container)`） | ✅ live：key 仅 `activeId`+`container`（后者为稳定引用，等效不变）；同端点编辑仅改 `supportsVision`（`activeId` 不变）→ `produceState` 不重跑 → 闸门不刷新（冷启动因重读快照才过，D7 静止态掩盖） |
| P1-4 明文扫描恒真断言 | `core-data/.../EndpointPlaintextScanTest.kt:28-37`（`knownCredentialMutationIsCaughtByScanner`） | ✅ live：断言只查字面值 `mutated`/`"[{\"id\":\"ep-1\"}]"` 是否含 `markers`，**未对真实落盘产物跑任何扫描函数**，属恒绿自指断言（第二条测试才真的调 `validateAdvanced`） |
| W9-NEW-1 coerceScalar 越界 | `core-engine/.../OpenAiCompatibleAdapter.kt:120-129`（`:127` `raw.toDoubleOrNull() != null -> JsonPrimitive(raw.toDouble())`） | ✅ live：`1e999` → `toDoubleOrNull()`=`+Infinity` → `JsonPrimitive(Double.POSITIVE_INFINITY)` → 严格序列化抛异常 → 整次 run 崩 |
| 缺口1a 附件盲于估算 | `core-model/.../TokenEstimator.kt:56-70`（`estimateMessages` 只遍历 text/toolCalls/toolResults） | ✅ live：不含 `message.attachments` |
| 缺口1b 附件盲于字符闸 | `core-context/.../HardBudgetShrinker.kt:91-107`（`promptChars` 同遍历） | ✅ live：不含 `attachments` → L3 字符硬闸漏算图片 |
| 缺口1c 附件盲于指纹头 | `core-context/.../PromptPrefix.kt:186-200`（`renderHistoryHead` 渲染 text/calls/results） | ✅ live：不含 `attachments` → `measuredCharsPerToken` 被视觉 token 污染、W9-04 cached_tokens 分母漂移 |
| 缺口2 canMerge 不查附件 | `core-context/.../ContextCompressor.kt:321-333`（`canMerge` 只查 role/toolCalls/toolResults/summary） | ✅ live：缺 `attachments.isEmpty()` 守卫 |
| 缺口3 无设备层 | 全仓 `Glob **/core-device/**` = **0 文件** | ✅ 确认：无 `core-device` 模块，属独立大波 |

> `ChatMessage.attachments` 现役存在：`core-model/.../ChatMessage.kt:30`（`val attachments: List<Attachment> = emptyList()`），`:38` `sendableAttachments` getter（`role==USER` 时过滤 `isSendable`）。故缺口2 守卫落点明确、可写。

---

## 一、W9-01 开波前「0 车」收口清单（≤30 行快修同车）

**定位**：4 条 P1 + W9-NEW-1 作为独立「0 车」PR，先于 W9-01 主体合入；不与 W9-01「压缩量纲统一」混编（W9-01 禁拆）。

**为何不插 W9-00b**：
1. W9-00 已收口（HEAD `767fd47`，CHECKLIST 12/12、G1–G6 全绿、CI run `42227092654` 五 job success），台账 §二-bis 已载明「W9-00 是插入半波、不构成 W9-05 顺延」——再开 W9-00b 等于二次插入，违反「单一插入」铁律，且强制重跑 G1–G6 + CI 五 job，代价远大于收益。
2. 4 条 P1 + W9-NEW-1 均属「小而独立、带真假用例」的安全补丁，符合 R2 三门槛（≤30 行 / 不碰压缩结构 / 有独立判据），无需借 W9-00 之名。
3. 拆成 0 车独立提交，使 W9-01 的「压缩量纲统一」保持纯净、可独立验收；0 车改动落在 `core-model`(余 562) / `core-data`(余 ~2112) / `app`(余 242)，均远离触顶阈值，**行数安全**。

### 0 车逐条收口表

| 编号 | 文件:行（以符号为准） | 修补动作 | 可量化验收 | 红态样本（必跑，先红后绿） | 对 EXPECTED_TESTS 影响 |
|---|---|---|---|---|---|
| P1-1 | `JsonFormatCheck.kt:74-80` | 围栏偏移改 CRLF 安全实现：用 `s.indexOf("\n")` 逐行算真实字节偏移（含 `\r`），或直接对 `s.replace("\r\n","\n")` 后再算；保证 `endFence` 落在真围栏字节起点 | 含 `\r\n` 围栏的合法 JSON 经 `checkJsonFormat` 判 `Valid`；CRLF 与 LF 用例断言 `line/column` 一致 | 输入 `data\r\n\`\`\`\r\n{...}` 类 CRLF 样本，旧实现判 Invalid ⇒ 必红 | +1~2 用例（CRLF 围栏 + 行列号一致性） |
| P1-2 | `EndpointProbe.kt:65-99`（重点 `:71-83` `takeWhile`） | 收紧：收到首个**内容证据**（`TextDelta`/`Usage`）且看到 `Finished` 即停，不再缓冲正文；并订正顶部 KDoc「不花 token 钱」措辞（与 P1-5 的 Finished-before-Usage 排序兼容，**不得**重蹈「首事件即取消」的旧 bug，须保留 trailing `Usage` 一格） | 开/关思考序列用例：关思考时正文空仍判红（不再假绿）、开思考时 `rtok` 不计入内容证据 | 构造 `ReasoningDelta+Finished` 无 `TextDelta/Usage` ⇒ 旧逻辑假绿 ⇒ 改后必红 | +2 用例（开/关思考序列） |
| P1-3 | `AppNavHost.kt:171-175` | 闸门改为直接 collect 端点流（或 key 含能力派生）：`val endpointGate by container.activeEndpointFlow().collectAsStateWithLifecycle(null)`（或 `produceState` key 增 `endpointGate.value?.supportsVision`），使同端点改 `supportsVision` 即时刷新 | D6/D7 两态：编辑当前端点仅勾图（同名）→ 返回对话页 attach 可点、禁用原因消失，无需冷启动 | 保持 old key（`activeId` only）→ 同端点勾图不刷新 ⇒ 必红 | 真机两态（无新增 @Test，JVM 不可达 Compose） |
| P1-4 | `EndpointPlaintextScanTest.kt:28-37` | `knownCredentialMutationIsCaughtByScanner` 改为真的序列化变异端点后跑扫描：构造 `RemoteEndpoint(customProtocol=含 sk-/max_tokens)`，经 `validateAdvanced` 放行外的**扫描路径**（序列化→`markers.any{ json.contains(it) }`）断言命中；删除字面值自指断言 | 变异样本序列化后 `markers` 计数 ≥1 且干净串 0 命中；判据自身先验证（已知含凭据样本必红） | 旧字面值自指断言恒绿 ⇒ 改后若扫描器漏抓必红 | 改写 1 用例（不增净计数） |
| W9-NEW-1 | `OpenAiCompatibleAdapter.kt:127` | `coerceScalar` 的 `toDoubleOrNull()` 分支加 `isFinite()` 守卫：`raw.toDoubleOrNull()?.takeIf{it.isFinite()}?.let{JsonPrimitive(it)} ?: JsonPrimitive(raw)` | `extraBody foo=1e999` 不再产 `JsonPrimitive(Infinity)`；序列化不崩 | `1e999` 旧实现产 `JsonPrimitive(Infinity)` ⇒ 序列化抛异常 ⇒ 必红 | +1 用例（`1e999`） |

**0 车合计增量**：源码 ≤30 行（5 处微调）+ 测试 +4~5 条 @Test。主理人独占在最终集成提交一次改 `build.yml:132` 的 `EXPECTED_TESTS`（现值以该行为唯一真值源，本席不碰）。

---

## 二、W9-01 主链路（压缩量纲统一）

依据台账 `W9-01`（4.0 尺四面 / 触发线按档位 / 单指针→尾部摘要前置），结合纯视觉「缺口1 附件盲区」合并实施。

### 2.1 实施要点

1. **量纲四面统一（4.0 尺）**：`TokenEstimator.estimateMessages` / `HardBudgetShrinker.promptChars` / `PromptPrefix.renderHistoryHead` / `ContextCompressor` 四面的「字符↔token↔档位↔缓存」量纲必须同源。现行 `TokenEstimator` 用 CJK≈1tok、ASCII≈4char/tok、+4/消息（`:22-26`）；`HardBudgetShrinker` 量纲是字符（`:85`）；二者必须共用同一换算常量，禁止两处各写一份比例。
2. **触发线按档位**：压缩触发阈值必须随窗口档位（6 档）缩放，复刻 W7 `:core-context` 已落地的档位口径，不得写死单一常量。
3. **单指针→尾部摘要前置（W8-01/W9-03 承接）**：现行 `ManualCompress.kt` 砍头 + `compressedUpTo` 单指针是「压缩×缓存命中互斥」根（台账 W8-01），W9-01 须改为**尾部追加摘要**、不改写已缓存前缀（即红线「别为凑缓存加长 system」）。
4. **缺口1 并入 W9-01（预算面）**：在 `estimateMessages` / `promptChars` 增 `attachments` 遍历——图片**算进预算**但**不算进指纹**（红线明令：图片字节不算进 `PromptPrefix` 指纹、图片必须放尾部）。具体：估算时对 USER `sendableAttachments` 按「每图 ≈ N token（按供应商视觉 token 计费口径，缺省取经验值并标注 `estimated=true`）」计入总量；`renderHistoryHead` 仍只渲染 text/calls/results，**不**追加附件字节（避免污染 `measuredCharsPerToken` 与 cached_tokens 分母）。

### 2.2 验收（含 M8/M9/M10 三 ⛔vacuous 必须拿到证据）

| 判据 | 可执行触发条件 | 空命中语义 | 验收要求 |
|---|---|---|---|
| W9-01 量纲一致 | 单测断言 `estimateMessages` 与 `promptChars/CHARS_PER_TOKEN` 同源换算，对同输入两路数值一致 | 两路各写一份比例 ⇒ 漂移 ⇒ ⛔vacuous | 至少 1 条同源断言 + 档位缩放表 |
| 缺口1 附件进预算 | 构造带 `attachments` 的 USER 消息 → 断言 `estimateMessages`/`promptChars` 增量 > 0 | 不含附件 ⇒ 软预算不因图触发 ⇒ ⛔vacuous | +2 用例（含图/不含图对比） |
| 缺口1 附件不进指纹 | 断言 `renderHistoryHead` 输出**不含**附件字节；同文本不同图 → 指纹字节相同 | 误把图字节写进指纹 → cached_tokens 分母漂移 | +1 用例 |
| **M8 KV cache** | 长会话前缀逐字节稳定 ⇒ 第 N 次 `cached_tokens>0`；改首字节 ⇒ 归零（**双向**） | OR 免费池 `cached_tokens` 恒 0 ⇒ 反向分支不可执行 ⇒ ⛔vacuous（**不算通过**）；NV 79.4% 是唯一正向 | W9-01 必须取到**第一条 `applied=true` + cached_tokens 双向证据**，否则记 ⛔vacuous |
| **M9 手动压缩** | 压缩后历史条数降 + 分隔卡出现 + `manual_compress dropped=N kept=M cache_invalidated=true` 落盘 + 下一请求 `cached_tokens=0`（双向） | `dropped=N`+`cached=0` 双向未触发 ⇒ ⛔vacuous | 必须取到第一条 `applied=true` |
| **M10 自动压缩** | 长会话超阈值 ⇒ `auto_compact applied=true` | 至今零证据（W7 全 noop）⇒ ⛔vacuous；WARN 有闩，0 命中≠通过 | 必须取到第一条 `applied=true`（W9-11 解锁后） |

> 🔴 **M8/M9/M10 三 ⛔vacuous 在本波必须拿到第一条 `applied=true` + cached_tokens 双向证据**，否则如实记 ⛔vacuous，不得写「已验证通过」（W7-03 A6 教训）。

---

## 三、纯视觉缺口并入路线（落点假设 + 回真实树复核）

| 缺口 | 并入波次 | 落点假设（`文件:行`，待回树复核） | 修补动作 | 设备侧可行 |
|---|---|---|---|---|
| 缺口1a 估算盲附件 | W9-01（预算面） | `TokenEstimator.kt:56-70` `estimateMessages` | 增 `for(a in message.sendableAttachments) total += estimateImageTokens(a)`（经验值 + `estimated=true`） | ✅（纯 JVM 估算，无设备依赖） |
| 缺口1b 字符闸盲附件 | W9-02（L3 工具规格字符纳入，同域） | `HardBudgetShrinker.kt:91-107` `promptChars` | 同上，按字符量纲计入图片（取供应商视觉字符当量） | ✅ |
| 缺口1c 指纹头盲附件 | W9-04（三桶归一 cached_tokens 分母） | `PromptPrefix.kt:186-200` `renderHistoryHead` | **保持不渲染附件**（红线）；仅在 `measuredCharsPerToken` 分母侧把视觉 token 单独记，不污染前缀哈希 | ✅ |
| 缺口2 canMerge 不查附件 | W9-01（压缩结构，core-context 非 app 禁区） | `ContextCompressor.kt:321-333` `canMerge` | 末尾增 `if (previous.attachments.isNotEmpty() || next.attachments.isNotEmpty()) return false`（图片 USER + 决策尾 USER 物理分离） | ✅ |
| 缺口3 无设备层 | **不在本波**（独立大波 `core-device`） | 全仓无 `core-device/**`（Glob 0 命中） | 阶段3 独立波；阶段0/1/2 零设备工程 | ⛔（未立项） |

> 阶段路线（纯视觉评估结论）：阶段0（W9 内零设备工程，修附件三盲区 + canMerge + usage 四态/多厂商字段表 + cache_hit_ratio 指标）→ 阶段1（MCP 接外部设备服务，先出 W5-03 cleartext 裁决）→ 阶段2（视觉兜底接 `ModelContextAssembler`）→ 阶段3（`core-device` 独立波）。
> **红线重申**：图片必须放尾部、算进预算但不算进指纹；别为凑缓存加长 system；别把图片字节算进 `PromptPrefix` 指纹。

---

## 四、多模型免费节点测试矩阵

> **通用前置（违反 ⇒ 该项判据整体无效 = 不可判，写「判据无效（前置违反）」而非「模型不识图/不支持」）**：
> - `max_tokens` 给足：基线 ≥512；思考型 ≥1024；tools ≥512；xhigh/1M 档 ≥1500。
> - 判据按 `finish_reason` / `tool_calls` 判，**不按 `content` 判**（reasoning 吃光预算时 `content=None`）。
> - 四元组 `max_tokens/prompt_tokens/completion_tokens/finish_reason` 强制逐请求落盘。
> - **设备侧（OPPO PDRM00/A13，adb `-P 15037`）与 PC 侧（curl 直连）分开记账**；PC 侧 1M 结论**不得**写成设备侧可用。
> - 1M 档 ≥2 次重放才写稳定结论。

### 4.1 端点与模型登记

| 供应商 | 端点 base URL | 模型 | 上下文窗口（实测，待补） | 鉴权 |
|---|---|---|---|---|
| OpenRouter | `https://openrouter.ai/api/v1` | `openrouter/dots-3-note-preview:free` | 512k（标称） | `Authorization: Bearer sk-or-v1-...` |
| OpenRouter | 同上 | `north-mini-code:free` | 待实测 | 同上 |
| NVIDIA | `https://build.nvidia.com` | `nemotron-3.5-super-vl-120b-a12b` | 待实测 | `Authorization: Bearer nvapi-...` |
| NVIDIA | 同上 | `mistral-large-4-1t-a52b` | 待实测 | 同上 |
| NVIDIA | 同上 | `step-5-preview` | 待实测 | 同上 |
| NVIDIA | 同上 | `deepseek-v4.1-flash` | 待实测 | 同上（⛔ 420s 超时，不排入题面） |
| NVIDIA | 同上 | `wan2.2-animate-2-14b` | 视频生成（非 chat） | 同上（矩阵外单列） |
| NVIDIA | 同上 | `vsr` | 待实测（疑似视觉/语音） | 同上 |

### 4.2 能力测试矩阵（每格 = 测试方法 + 预期判据；⛔ = 平台/能力阻断）

| 能力项 | dots-3-note-preview:free | north-mini-code:free | nemotron-vl-120b | mistral-large-4-1t | step-5-preview | deepseek-v4.1-flash | vsr |
|---|---|---|---|---|---|---|---|
| 多模态输入（图） | ✅ 双通（512k） | ⛔（代码模型） | ✅ 200×120 红蓝→"Red Blue"（D7b 已证） | 待实测 | 待实测 | 待实测 | 待实测 |
| 工具调用 | ✅（tools≥512） | 待实测 | ✅（W9-00 M1 已往返） | 待实测 | 待实测 | 待实测 | 待实测 |
| 音频输出 | ⛔（M3 平台阻断 `/v1/audio/speech` 404） | ⛔ | ⛔（NV `supported_voices=null`） | ⛔ | ⛔ | ⛔ | 待实测 |
| 工作空间文件读写 | 由 App 容器提供（M4 已证，与模型无关） | 同 | 同 | 同 | 同 | 同 | 同 |
| 记忆跨进程 | 由 App 容器提供（M5 已证） | 同 | 同 | 同 | 同 | 同 | 同 |
| Skill | 由 App 容器提供（M6 已证） | 同 | 同 | 同 | 同 | 同 | 同 |
| MCP | ⛔ 平台阻断（`usesCleartextTraffic=false`，W5-03，不为测试放开） | 同 | 同 | 同 | 同 | 同 | 同 |
| KV cache 命中 | ⛔vacuous（OR 免费池 `cached_tokens` 恒 0，反向分支不可执行） | 同 | ⚠️ NV 79.4%（唯一正向，W7 A5） | 待实测 | 待实测 | 待实测 | 待实测 |
| cached_tokens 观测 | 四元组落盘，看 `prompt_tokens_details.cached_tokens` | 同 | 同 | 同 | 同 | 同 | 同 |
| 手动压缩 | M9 ⛔vacuous（dropped/cached 双向未触发） | 同 | 同 | 同 | 同 | 同 | 同 |
| 自动压缩 | M10 ⛔vacuous（applied=true 零证据，W9-11 解锁后取） | 同 | 同 | 同 | 同 | 同 | 同 |
| 最大上下文压力（至 1M） | PC 侧 512k 边界（≤512k）；设备侧 ⛔ 双内核阻断（W9-11） | PC 侧待实测；设备 ⛔ | PC 侧待实测（≥2 重放）；设备 ⛔ | 同 | 同 | 同 | 同 |

### 4.3 测试方法要点（按侧）

- **PC 侧**：`curl` 直连端点 base URL，`stream:false`、四元组落盘；1M 档用 `_w7/w7matrix.py` 同款纯 Python 直连（绕过 app 内核），≥2 重放。
- **设备侧**：OPPO A13 经 App（`stream:true`）真机；题面英文 ASCII（`input text` 非 ASCII NPE）；`adb -P 15037`；点按判据以 journal/日志/截图为准。
- **判据无效处置**：凡 `max_tokens` 前置违反（如 32）→ 结论写「判据无效（前置违反）」，不写「模型不识图」；reasoning 吃光预算的 `content=None` 须看 `finish_reason=length` + `completion_tokens` 拆分。

---

## 五、设备硬件可行性维度（方案席专长）

### 5.1 OPPO A13（PDRM00 / ColorOS / Android 13 SDK33）约束

| 维度 | 结论 | 四态 |
|---|---|---|
| 目标设备/OEM | `com.rickeal.liquidharness.debug`；`adb -P 15037`，本波 `devices` ⇒ `<设备序列号> device` 在线 | ✅ 已验证 |
| 安装 | 必须 `adb push` 到设备目录后 `pm install -r`；直接 `adb install` 报 -99；`pm install-commit` 灭屏永久挂起 | ✅ 已验证规则 |
| 唤醒前置 | 安装前 `KEYCODE_WAKEUP` + `exec-out screencap -p` 确认**非全黑**（AOD 仍可能黑） | ⚠️ 每次复验 |
| adb daemon | 每命令后退出 ⇒ `adb reverse` 与后续 input/screencap **同一条 Bash 调用**；设备侧 `-f` 落盘 logcat（PC 缓冲 ~256KiB） | ✅ 已验证 |
| 题面注入 | `input text` 非 ASCII NPE ⇒ 中文题面一律英文 ASCII（空格 `%s`） | ✅ 已验证 |
| 点按判据 | Compose bounds 在 `uiautomator dump` 不可靠 ⇒ 以 journal/应用日志/截图为准；应用商店弹窗抢前台 ⇒ `am force-stop com.heytap.market` | ✅ 已验证 |
| 能力位 | `supportsVision` 现役三处同改（`RemoteLlmEngine.kt:94`）；`supportsReasoning` 三组预置全 false + 无选择器 + UI 只读 ⇒ 思考四态/五档**生产不可达** | ⛔ 思考档位不适用 |
| 平台阻塞（先于代码拒绝） | ① MCP cleartext 被 `AndroidManifest.xml:25 usesCleartextTraffic=false` 拒（W5-03，不为测试放开）；② 设备侧 1M 双内核阻断（W9-11 未解锁） | ⛔ 不适用 |

### 5.2 1M 测试在设备侧的双内核阻断（现状）

- **阻断1**：`PreStepBudget.enforce`（`TurnLoop.kt:49` 之前，`hard≈27904`）→ 单轮硬闸 ~27.9k。
- **阻断2**：`RoundHeadGate`（`:42` 本 run 累计 `promptTokens` > `AgentPolicy.maxRunPromptTokens=400_000`，首轮 0 ⇒ **第 2 轮才熔断**）。
- 结论：设备侧 1M **本波不可达**，仅 PC 侧纯 Python 直连可探 1M；**W9-11 解锁前，任何 1M 结论只限 PC 侧**，不得写成设备侧可用。

### 5.3 纯视觉路线在 Android 14/16/17 无障碍限制下的合规判据

- **合规正路**：视觉兜底经 `ModelContextAssembler` 接入，**图片必须由用户主动提供**（SAF 选择器 / 系统截图共享 / `attachImage` 回传 URI），**不得**走后台 `AccessibilityService` 截图通道。
- **Android 14（U）+ 16/17 限制**：`AccessibilityService.canTakeScreenshot()` 受前台/权限严格约束，后台静默截图在 14+ 被大幅限制；Play 政策亦禁止无告知的屏幕抓取。
- **判据**：纯视觉路线验收必须满足「图片进入链路的唯一入口 = 用户显式选择/共享」（代码侧即 `ChatAttachmentPicker`/`attachImage` 回调），不得新增系统级截图服务；阶段2 视觉兜底接 `ModelContextAssembler` 时，截图源必须来自用户共享而非后台抓取 → 否则 ⛔ 合规不适用。

---

## 六、下一波排序与挂账联动

**强制顺序（禁拆）**：

1. **W9-01（第一件事，禁拆）**：压缩量纲统一 + 缺口1（预算面）+ 缺口2（canMerge）+ 0 车四项快修同 PR 但独立提交。四外审同一缺陷禁拆多条（台账 §二-bis 原话）。
2. **W9-05（必须开波，老化五波）**：journal / FreezeClock（W3-05 承接）。**插 W9-00 不构成第 5 次顺延**（台账 §零）。
3. **W9-10（守卫与口径专项）**：C9/G24 归属唯一；含 `judgeProbe` 双入口语义漂移（外审 P2）、W9-NEW-2/3/4/5/6（保存失败原因粒度/白名单混入测试键/降级 reason 英文上屏/REASONING_ONLY 兼容分支/parseExtraBody 逗号歧义）。
4. **W9-07 / W9-12（行数触顶先定拆分）**：W9-07 三项已改号 W9-00；W9-12 思考四态/五档（feature-settings 余 8，开工前先定拆分，不得调上限）。

### 报告缺陷 → W9-x 挂账映射

| 外审缺陷 | 映射挂账 | 处置波次 |
|---|---|---|
| P1-1 CRLF 围栏 | （W9-00 kotlinx③ 同文件后续） | 0 车 |
| P1-2 测试连接收完整流 + judgeProbe 双入口语义漂移 | 0 车 + W9-10（口径专项） | 0 车 / W9-10 |
| P1-3 produceState key（D7 不敏感） | 0 车（W3-07 已 CLOSED，属响应式补强） | 0 车 |
| P1-4 明文扫描恒真断言 | 0 车（A-3/F-1 已 W9-00 改号） | 0 车 |
| W9-NEW-1 coerceScalar 1e999 | 0 车 | 0 车 |
| REASONING_ONLY 存量配置不可见 / SamplingParams.reasoningEffort 装饰字段 | W9-12 | W9-12 |
| supportsAudioOutput 零消费 / EngineCapabilities 二选一 | W9-06 | W9-06 |
| feature-settings 触顶临界（3492/3500 余 8） | W9-07 / W9-12 行数 | 行数先拆 |
| A-3 未覆盖设备侧注入路径 | W9-11（设备侧 endpoints.json 注入） / W3-07 | W9-11 |
| W9-NEW-2/3/4/5/6 | W9-10 | W9-10 |
| 纯视觉缺口1a/1b/1c | W9-01 / W9-02 / W9-04 | 见 §三 |
| 纯视觉缺口2 canMerge | W9-01 | 见 §三 |
| 纯视觉缺口3 core-device | 独立大波（阶段3） | 不在本波 |

---

## 七、行数闸门提示

> 🔴 **严禁调 `LINE_CAPS` / `TOTAL_LINE_CAP=42500` / `VACUOUS_BASELINE`**；W8 `+100` 例外不构成先例；行数触顶先靠下沉（`core-model` 余 562 / `core-engine` 余 1407 / `core-data` 余 ~2112），不许调上限。

| 模块 | 现役 / 上限 | 余量 | 本波动作 |
|---|---|---|---|
| `feature-settings` | 3492 / 3500 | **余 8** | ⚠️ 触顶临界；W9-07/W9-12 开工前**先定拆分方案**（纯逻辑下沉 `core-model`/`core-engine` + 删假入口净减），不得调上限 |
| `core-model` | 2038 / 2600 | 余 562 | 0 车（P1-1、P1-4、W9-NEW-1 部分）落此，安全 |
| `feature-chat` | 3777 / 3900 | 余 123 | ⚠️ 偏紧；W9-03 尾部摘要前置若落此需先外提新文件 |
| `core-data` | 2388 / 4500 | 余 ~2112 | 0 车（P1-2、P1-4 测试）落此，安全 |
| `app` | 1358 / 1600 | 余 242 | 0 车（P1-3）落此，安全 |
| `core-context` | 1584 / 1650 | 余 66 | 缺口1b/1c/canMerge 落此，余量紧，优先下沉 |

> 历史教训（W6-07）：`wc -l` 对缺尾换行少算 1 行（G10 静默），`core-kernel` 曾因 1 行=10% 余量误差踩线；本波凡触顶模块先 `wc -l` 复核尾换行再定拆分。

---

## 八、历史坑点规避（必规避清单）

1. **证伪优先**：所有外审行号已漂移（如 `JsonFormatCheck.kt:72` 现 `:74-80`、`AppNavHost.kt:168-175` 现 `:171-175`），照抄 = 伪工作量。
2. **VACUOUS 不是通过**：M8/M9/M10 三项必须取到第一条 `applied=true` + cached_tokens 双向证据；OR 免费池 `cached_tokens` 恒 0 ⇒ 反向分支不可执行 ⇒ ⛔vacuous。
3. **判据自身先验证**：A-3 扫描器先跑已知含凭据变异样本证明会红；P1-2 不得重蹈「首事件即取消」旧 bug（须保留 trailing `Usage`）。
4. **新增组件配「被调用」判据**：P1-3 闸门刷新须真机 D6/D7 两态取证（只改 capabilities 不点 UI ⇒ ⛔vacuous）。
5. **平台策略先于代码拒绝**：MCP cleartext、设备侧 1M 双阻断——不为测试放开；PC 侧 1M 结论不得写设备侧可用。
6. **图片红线**：算进预算、放尾部、不算进 `PromptPrefix` 指纹；别为凑缓存加长 system。
7. **行数**：触顶先拆下沉，禁调上限；`feature-settings` 余 8 须先定拆分再开工 W9-07/12。
8. **子 Agent 回报可能虚构**：本席只产文档，所有 `文件:行` 均回树 grep 复核，不采信交接自述。

---

> 本文件为方案文档，只登记与派单，**不判定任何项通过**；四态结论由跑后真机/CI 证据填写。判据先写死，跑后不得改（改则版本 +1 原样重跑）。

---

## 九、免费节点实测结论（主理人 2026-11-06 直连探活，curl/urllib）

> 目的：在写码前先确认哪些免费端点真实可用，供真机/PC 测试选节点。外审「待实测」格据此填充。

### 9.1 实测可用节点（✅）

| 供应商 | 端点 base | 模型 slug（实际） | 探活结果 |
|---|---|---|---|
| OpenRouter | `https://openrouter.ai/api/v1` | `dots-studio/dots-3-note-preview:free` | ✅ chat 200；`max_tokens=1024` 出 `content="\n\nPONG"`；tools 调用返回 `tool_calls`；vision 吃 1×1 退化样本返回词（**禁用 1×1 判据**）；`usage.prompt_tokens_details.cached_tokens` 可观测（免费池恒 0） |
| OpenRouter | 同上 | `cohere/north-mini-code:free` | ✅ chat 200（推理模型，content 被 reasoning 吃光需 ≥1024） |
| NVIDIA | `https://integrate.api.nvidia.com/v1/chat/completions` | `nvidia/nemotron-3.5-super-vl-120b-a12b` | ✅ 出 `PONG`；~300k token 上下文压力测试 `prompt_tokens=300326` 成功（1M 为文档标称上限）；`cached_tokens` 可观测 |
| NVIDIA | 同上 | `mistralai/mistral-large-4-1t-a52b` | ✅ 200（推理模型，content 需 ≥1024） |
| NVIDIA | 同上 | `stepfun-ai/step-5-preview` | ✅ 200，出 `PONG` |

### 9.2 实测不可用节点（⛔）

| 供应商 | 模型 slug（实际） | 结果 |
|---|---|---|
| NVIDIA | `deepseek-ai/deepseek-v4.1-flash` | ⛔ HTTP 000（连接超时，与历史「420s 超时」一致，排除出测试矩阵） |
| NVIDIA | `wan-ai/wan2.2-animate-2-14b` | ⛔ 404（媒体/视频生成模型，非 chat） |
| NVIDIA | `nvidia/vsr` | ⛔ 404（非 chat 模型） |
| NVIDIA | `https://build.nvidia.com/v1/chat/completions` | ⛔ 返回 HTML（那是网页不是 API；正确端点是 `integrate.api.nvidia.com`） |

### 9.3 判据纪律验证（与 §四 前置一致）

- OR 免费推理模型（`dots`/`north`/`mistral`）`max_tokens=32` 时 `content=None`、`finish_reason=length`、`reasoning_tokens` 占满 ⇒ 印证「思考型 `max_tokens≥1024`」前置；判据按 `finish_reason`/`tool_calls` 判不按 `content`。
- NVIDIA `nemotron-vl` 在 `max_tokens=32` 仍出 `content`（reasoning 与 content 并存），但 300k 压力测试 `completion_tokens=48` 时 `content=None`（reasoning 吃光）→ 同样适用前置纪律。
- 所有可用节点 `usage.prompt_tokens_details.cached_tokens` 字段齐备 ⇒ M8 KV cache 观测口径成立（OR 免费池恒 0 ⇒ 反向分支不可执行 ⇒ ⛔vacuous，不得写通过；NV 长会话有正向）。

### 9.4 设备侧测试节点建议

- 真机（OPPO PDRM00/A13，`adb -P 15037`，在线 `<设备序列号>`）优先用 NVIDIA `nemotron-3.5-super-vl`（视觉+工具+长上下文三通，已在 W9-00 D7b 行使）与 OR `dots-3-note-preview:free`（工具+视觉双通 512k）。
- 设备侧 1M 仍受双内核阻断（W9-11 未解锁），PC 侧 1M 结论不得写成设备侧可用。
