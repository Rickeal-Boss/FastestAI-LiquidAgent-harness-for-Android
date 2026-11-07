# W10 派单口径 — W9-01 主体 + 多模型免费节点测试矩阵（主理人代行方案席，2026-11-07）

> ⚠️ **来源标注**：方案席子 Agent spawn 后 18s 即 429 配额耗尽失败；按知识库 §6 铁律
> 「429 配额耗尽 ⇒ 主理人接手补完，不重试 spawn」，本文件由主理人（祁研深）亲自产出，
> 所有 `文件:行` 均回现役工作树（HEAD=`fc841f8`）grep 复核，以符号为准。
> 上游依据：`docs/notes/w9-wave-plan.md`（方案席 2026-11-06，§二/§四/§五/§九 全部有效，
> 本文件不重复其内容，只做派单收口）+ 用户 2026-11-07 新输入（免费节点 key + 至 1M 上下文 + 功能矩阵）。
> 判定四态：✅ 通过 / ⚠️ 部分 / ⛔ 不适用 / ⛔vacuous（判据没被行使）。**「未触发」≠「已验证通过」**。

---

## 一、端点登记复核表（矩阵唯一真值源）

凭据纪律：key 只从环境变量读（调用方从用户文件读入内存注入），**任何文档/产物不落 key 字面量**，落盘前 scrub。

| 供应商 | base URL | 模型 slug（实际） | 能力标记 | 矩阵状态 |
|---|---|---|---|---|
| OpenRouter | `https://openrouter.ai/api/v1` | `dots-studio/dots-3-note-preview:free` | 工具+视觉双通，标称 512k | ✅ 排入（§九.1 已探活） |
| OpenRouter | 同上 | `cohere/north-mini-code:free` | 推理型（content 需 ≥1024） | ✅ 排入（§九.1 已探活） |
| NVIDIA | `https://integrate.api.nvidia.com/v1` | `nvidia/nemotron-3.5-super-vl-120b-a12b` | 视觉+工具+长上下文（标称 1M） | ✅ 排入（D7b 已往返） |
| NVIDIA | 同上 | `mistralai/mistral-large-4-1t-a52b` | 推理型，标称 1M | ✅ 排入 |
| NVIDIA | 同上 | `stepfun-ai/step-5-preview` | 文本 | ✅ 排入（冷启动慢，600s 窗） |
| NVIDIA | 同上 | `deepseek-ai/deepseek-v4.1-flash` | — | ⛔ 排除（HTTP 000 超时，§九.2；本波仅复核一次） |
| NVIDIA | 同上 | `wan-ai/wan2.2-animate-2-14b` | 视频生成，非 chat | ⛔ 排除（404，矩阵外单列，本波不测） |
| NVIDIA | 同上 | `nvidia/vsr` | 视频超分，非 chat | ⛔ 排除（404，本波不测） |

🔴 **端点纠偏（用户输入订正）**：用户称「其余的通过 build.nvidia.com 测试」——`build.nvidia.com` 是网页非 API
（§九.2 实测返回 HTML）；API 一律走 `https://integrate.api.nvidia.com/v1/chat/completions`。

## 二、W9-01 主体施工单（开发席 Phase 2 直接输入；禁拆）

> 0 车（P1-1..P1-4 / W9-NEW-1 / 缺口2 canMerge）已在 `fc841f8` 收口，不在本单。
> 红态样本先行：每条先写「已知必红」用例再施工；EXPECTED_TESTS 增量只登记，常量由主理人独占一次改。

| # | 项 | 现役落点（grep 复核，以符号为准） | 施工动作 | 真假用例 | 预估 Δ@Test |
|---|---|---|---|---|---|
| S1 | 量纲同源 | `core-model/.../TokenEstimator.kt:23`（`CHARS_PER_TOKEN_ASCII=4.0`）、`:26`（`PER_MESSAGE_OVERHEAD=4`）；`core-agent/.../AgentPolicy.kt:168`（`hardCharsPerToken=1.8f`）；`core-context/.../BudgetFormula.kt:60`（`charCapOf`） | 在 core-model 出 **public 换算常量与注释契约**（估算面 4.0 char/tok 与硬闸面 floor 1.8 的分工写死：硬闸必须 floor 保守、估算面必须近似真实；core-context 已依赖 core-model，引用不越模块边界）；禁两处各写一份比例而无同源注释 | 单测断言：同一混合文本经 `TokenEstimator.estimate` 与 `charCapOf` 反推的 token 数关系满足契约（估算值 ≥ 硬闸换算值，即硬闸恒保守）；比例常量同源性用「同符号引用」静态断言 | +2 |
| S2 | 触发线按档位复核 | `AgentPolicy.kt:154`（`compressThreshold=0.75f`）；`BudgetFormula.kt:46`（软预算=hard×threshold）；`feature-chat/.../ChatContextWindow.kt:24`（`contextWindowCap` 读 `settings.maxContextTokens` 档位） | **复核结论**：触发线已随档位缩放（hard 由端点窗口∩档位决定，threshold 为政策常量）——本项为**补钉非改造**：加单测把「档位变 ⇒ 触发线变」钉死，防回归 | 两个不同 `maxContextTokens` 档位 ⇒ `softBudgetTokens` 不同且比例正确 | +2 |
| S3 | 尾部摘要前置 | `feature-chat/.../ManualCompress.kt:87`（`compressBubbles`）、`:114`（`compressedUpTo` 单指针）、`:158`（`manualCompressor()`→`WindowContextCompressor`）；`feature-chat/.../ConversationOps.kt:96`（`autoCompactOnSettled`）、`:109`（applied=true 才落 compressedUpTo）；`core-context/.../SettledCompressPolicy.kt:16` | 压缩产物落点从「砍头+单指针」改为**尾部追加摘要消息**（摘要作为新 SYSTEM/摘要角色消息 append 到历史尾部，被压段落移出 history 但**不改写已发前缀**），保持前缀字节稳定 ⇒ 缓存可命中。🔴 红线：别为凑缓存加长 system；前缀单调性不破 | ①压缩后 `renderHistoryHead` 前缀字节与压缩前一致（缓存稳定判据）；②`manual_compress dropped=N kept=M cache_invalidated=false`（新语义：尾部追加不失效缓存）落盘；③摘要消息在尾部且含被压段落要点 | +3 |
| S4 | 缺口1a 附件进预算不进指纹 | `TokenEstimator.kt:56-70`（`estimateMessages` 无 attachments 遍历，已复核）；`ChatMessage.kt`（`attachments`/`sendableAttachments` 现役存在） | `estimateMessages` 增 `sendableAttachments` 遍历：每图按经验值 N token 计入（缺省 1024，标注 `estimated=true`，供应商视觉 token 口径留 KDoc）；`renderHistoryHead`（`PromptPrefix.kt:186-200`，已复核无附件遍历）**保持不渲染附件字节**（红线） | ①含图 USER 消息估算增量 >0；②同文本不同图 ⇒ `renderHistoryHead` 输出字节相同（指纹不受图影响）；③`HardBudgetShrinker.promptChars` 本波**不动**（缺口1b 归 W9-02，防范围蔓延） | +2 |

**行数闸门预检**（禁调 `LINE_CAPS`/`TOTAL_LINE_CAP=42500`/`VACUOUS_BASELINE`；W8 +100 例外不构成先例）：

| 模块 | 现役/上限（W9-00 口径） | 余量 | 本波落点与处置 |
|---|---|---|---|
| core-model | 2038/2600 | 余 562 | S1 常量+S4 估算落此，安全 |
| core-context | 1584/1650 | **余 66** | ⚠️ S3 策略改动尽量不动本模块行数；必须新增时纯逻辑下沉 core-model；改动后 `wc -l` 复核尾换行（W6-07 教训） |
| feature-chat | 3777/3900 | 余 123 | ⚠️ S3 主战场（ManualCompress/ConversationOps）；预计净增 ≤40，**先外提新文件**（如 `TailSummaryCompressor.kt`）再动存量 |
| feature-settings | 3492/3500 | 余 8 | 🔴 本波**零改动**（W9-07/12 开工前先定拆分，不在本波） |

## 三、PC 侧 / 设备侧矩阵分派表

> 前置纪律（违反 ⇒ 该项判据整体无效，写「判据无效（前置违反）」）：`max_tokens` 基线 ≥512、思考型 ≥1024、xhigh/1M 档 ≥1500；
> 判据按 `finish_reason`/`tool_calls` 判不按 `content`；四元组 `max_tokens/prompt_tokens/completion_tokens/finish_reason` 逐请求落盘；
> PC 侧与设备侧**分开记账**；PC 侧 1M 结论不得写成设备侧可用；1M 档 ≥2 重放。

### 3.1 PC 侧（主理人亲自，`_w10/w10matrix.py` 复用 `_w6` harness + `_w7` 观测层模式）

| 阶段 | 内容 | 覆盖模型 | 判据 |
|---|---|---|---|
| selfcheck | 1k 取针判据自证（max_tokens≥512，finish≠length） | nv-vl / dots / north | hit=True 且非 length 截断 |
| probe | chat / tools / vision 三通道（vision 仅 VL 标记；200×120 左红右蓝真图，**禁 1×1**） | 全部 5 模型 | chat=200；tools 出 `tool_calls`；vision 答「Red Blue」 |
| cache | KV cache：稳定前缀第 N 次 `cached_tokens>0`；改首字节 ⇒ 归零（**双向**） | 5 模型 | NV 取正向（§九：NV 79.4% 历史正向）；OR 免费池恒 0 ⇒ 如实记 ⛔vacuous |
| ladder | 长程阶梯：4k/32k/128k/512k（dots 封顶 512k）；nv-vl 与 mistral 加 1M 档 ≥2 重放 | 5 模型分档 | 各档 hit + 四元组；失败档如实记录 |
| chunk | 分块装配（_w6 chunk 模式） | nv-vl / dots | 分块边界不丢针 |
| summary | 汇总落 `_w10/summary.json`，关键计数抄进 handoff 正文 | — | — |

### 3.2 设备侧（主理人亲自，OPPO PDRM00 `13309cc8`，adb `-P 15037`，`MSYS_NO_PATHCONV=1`）

| 项 | 行使方式 | 判据 | 预期四态 |
|---|---|---|---|
| M1 多模型 | 设备侧 `endpoints.json` 注入新端点（or-north / nv-mistral / nv-step5；nv-vl、or-dots 为 W9 存量）+ 冷启动登记 + 逐端点往返 | 各端点一次真实往返（journal + 用量行） | 待行使 |
| D6/D7 回归 | 图片闸门禁用态/响应式 + D7b 视觉往返（200×120 红蓝，max_tokens≥512） | 同 W9-00 判据 | 待行使 |
| M4/M5/M6 回归 | file_write/file_read 授权一次；memory_write→冷启动→memory_read 命中；skill_load 报内置技能 | 落盘内容逐字一致 | 待行使 |
| M8 KV cache | 长会话前缀稳定 ⇒ `cached_tokens>0`；改首字节 ⇒ 归零（双向） | **首条正向证据**；OR 端点恒 0 记 ⛔vacuous | 待行使 |
| M9 手动压缩 | S3 施工后：压缩 ⇒ 摘要尾部追加 + `dropped=N` 落盘 + 前缀字节稳定（缓存**不**失效） | 首条 `applied=true` + 前缀 diff 为空 | 待行使（依赖 S3） |
| M10 自动压缩 | 长会话超阈值 ⇒ `auto_compact applied=true`（`ConversationOps.kt:109` 语义） | 首条 `applied=true`；取不到记 ⛔vacuous | 待行使（依赖 S3） |
| M3 音频输出 | NV `/v1/audio/speech` 复核一次 | 404/`supported_voices=null` ⇒ ⛔ 平台阻断 | 阻断复核 |
| M7 MCP | `usesCleartextTraffic="false"` 不放开 | ⛔ 平台阻断（W5-03） | 阻断复核 |
| 设备侧 1M | 双内核阻断（`PreStepBudget`/`RoundHeadGate maxRunPromptTokens=400_000`，AgentPolicy.kt:228） | ⛔ W9-11 未解锁，不行使 | ⛔ 不适用 |

## 四、M8/M9/M10 取证据可执行触发条件（首条 applied=true 工程化）

1. **M9（手动）**：构造 ≥30 条历史 + 触发手动压缩 ⇒ 断言：journal 落 `manual_compress dropped=N kept=M` + 摘要消息在尾部 + `renderHistoryHead(压缩前历史头 k) == renderHistoryHead(压缩后保留段头 k)`。
2. **M10（自动）**：档位调最小 + 长会话脚本轮次灌到阈值 ⇒ `auto_compact applied=true` 首次落盘（WARN 闩注意：每 run 只报一条，0 命中 ≠ 通过）。
3. **M8（缓存）**：M9/M10 之后**同一会话**再发一轮 ⇒ `cached_tokens>0`（前缀未改写 ⇒ 命中）；改首字节 ⇒ 归零。双向都取到才算 ✅；NV 端点为正向前哨。

## 五、设备硬件可行性维度（主理人复核，继承 w9-wave-plan §五）

- OPPO A13（PDRM00/ColorOS/SDK33）：`adb push` + `pm install -r`（直接 install 报 -99）；安装前 `KEYCODE_WAKEUP` + screencap 非全黑；adb daemon 每命令后退出 ⇒ 设备侧 `logcat -f` 落盘、命令串行；题面 ASCII（`input text` 非 ASCII NPE）；uiautomator bounds 不可信 ⇒ 判据以 journal/日志/截图为准；`am force-stop com.heytap.market` 防抢前台。
- 端点注入通道：设备侧 `endpoints.json` 编辑 + 冷启动自动登记（W9 已行使）；datastore 直改须同步 protobuf 长度前缀、改完读回核验。
- 卸载重装前保全 `/sdcard/Android/data/<pkg>/files`（模型 12GB）；新旧签名不符才卸载。
- 风险：免费节点限速/排队（429/5xx）⇒ 设备侧逐端点串行 + 失败重放一次再定性；NV 冷启动慢（step5 历史 600s 窗）。

## 六、风险与历史坑（本波新增 + 继承）

1. **429 配额风险（本波实证）**：子 Agent ≤4 路并行；再遇 429 主理人接手补完不重试 spawn。
2. OR 免费池 `cached_tokens` 恒 0 ⇒ M8 反向分支不可执行 ⇒ ⛔vacuous，不得写通过。
3. 免费模型可用性时变 ⇒ 每阶段先探活再排题；失败样本保存 raw 落 `_w10/raw/`。
4. 「判据写进方案」≠「判据被执行」：矩阵每格跑后回填四态，禁预写结论。
5. S3 改压缩结构 = 高风险面：必须保留「未压轮次前缀严格单调」（SettledCompressPolicy 契约），压缩只追加不改写。
6. 子 Agent 回报可能虚构 ⇒ 派单后 `git status` + 严格 @Test 口径核实磁盘产出再采信。

## 七、分波排序与挂账联动

- **本波（W10）= W9-01 主体（S1..S4）+ 矩阵行使（PC 侧全程 + 设备侧 M1/D6/D7/M4/M5/M6/M8/M9/M10 + M3/M7 阻断复核）+ 推送 CI**。
- **W9-05（journal/FreezeClock，老化五波）本波不开**：用户指令聚焦模型矩阵与 W9-01；顺延理由如实登记，台账 §二-bis W9-05 行证据列回填「W10 聚焦 W9-01+矩阵，W9-05 顺延至 W11 必须开波（第 6 次顺延=病理复发，主理人 2026-11-07 登记）」。
- **W9-10（守卫与口径专项）不开**：本波不新增 arch-guard 守卫（新守卫扫空集 ⇒ G21 判红）。
- 本波 handoff = `docs/handoff-20261107-wave10.md`（跑后回填四态，禁预写）。
