# W3 交接 — 能力补齐波：多模态 / 记忆 / Skill / MCP / 上下文工程（2026-10-31）

> 仓库：`FastestAI-LiquidAgent-harness-for-Android`（本地 `harness-v2/`）
> 基线：`dae57ea`（W2.6 收口）→ 本波在工作树上完成
> 供应商实测数据：`docs/notes/w3-provider-matrix.md`

---

## 一、本波结论（先看这段）

| 项 | 结果 |
|---|---|
| 单元测试 | **471 / 0 / 0 / 0**（11 模块，W2.6 基线 365 → +106） |
| 架构守卫 | **23 / 0 / 0**（G1 曾在 core-mcp 判红，已修） |
| 真机构建 | 安装成功，冷启 1.29s，工具循环 / 思考块 / 用量行 / 审批 / 记忆 / 技能 / 视觉全部实测通过 |
| 长上下文 | **815k token 单请求成功且取针命中**（详见供应商矩阵 §五） |
| 明确未做 | `speak`（真机挂死，默认关闭）、Skill 导入、MCP 真机联调、journal/FreezeClock |

---

## 二、交付清单（按能力）

### 2.1 多模态输入（图片）

| 文件 | 作用 |
|---|---|
| `core-model/Attachment.kt`（新） | `Attachment(kind, mimeType, base64, localPath, textContent)` + `isSendable` + 上限常量 |
| `core-model/ChatMessage.kt` | 新增 `attachments`，`isEmpty` / `sendableAttachments` 同步 |
| `core-engine/OpenAiCompatibleAdapter.kt` | `contentElement()`：有附件时 `content` 变数组（`text` + `image_url` data URI）；无附件时**逐字节等于旧行为** |
| `core-data/AttachmentImporter.kt`（新） | SAF `content://` → 降采样（最长边 1568）→ JPEG q85 → base64 + cacheDir 缩略图 |
| `feature-chat/ChatInputBar.kt` | 「+」入口 + 附件缩略条（可删） |
| `feature-chat/ChatBubble.kt` | 用户气泡渲染附件缩略图 |

**真机证据**：图内文字 `HARNESS-42` 被正确读出；用量行 `2.5k in`（图片 token 已计入）。

### 2.2 长期记忆

| 文件 | 作用 |
|---|---|
| `core-agent/memory/MemoryStore.kt`（新） | 端口 + `MemoryEntry` + `renderMemoryForPrompt`（纯函数，总量 6000 字符封顶） |
| `core-agent/tool/tools/MemoryTools.kt`（新） | `memory_write` / `memory_search` / `memory_delete`（按 title 规范化 upsert） |
| `core-data/FileMemoryStore.kt`（新） | `memory.json`，Mutex 串行读改写 |
| `feature-settings/MemoryScreen.kt`（新） | 列表 / 展开 / 删除 |
| `ChatViewModel.runOnce` | run 开始时 `promptContext()` 快照一次进 system |

**真机证据**：`memory_write` 落盘 `memory.json`；**新对话**问「你记得我的偏好吗」直接答出 `metric`，未调工具 ⇒ 记忆确实进了 system。

### 2.3 Skill 系统

| 文件 | 作用 |
|---|---|
| `core-skills/SkillParser.kt` | 手写 frontmatter（name / description / allowed-tools），`ParseResult` 三态 |
| `core-skills/SkillSources.kt` | `FileSkillSource`（用户目录，一层）+ `AssetSkillSource`（内置） |
| `core-skills/SkillCatalogImpl.kt` | 同名先到先得、按 name 排序（顺序进 system，必须稳定）、缓存 + `refresh()` |
| `core-skills/SkillImportValidator.kt` | 5 类拒绝（体积 / 数量 / 可执行载荷 / 路径逃逸 / 缺 SKILL.md） |
| `core-agent/skill/SkillCatalog.kt`（新） | 端口 + `renderSkillsSection`（最多 24 条） |
| `core-agent/tool/tools/SkillTools.kt`（新） | `skill_list` / `skill_load` |
| `feature-settings/SkillListScreen.kt` | 列表 + 启停（持久化到 `disabledSkills`） |
| `assets/skills/{pdf-toolkit,notes-organizer}/SKILL.md` | 内置示例，英文正文，含「不得跳过审批」声明 |

**真机证据**：`skill_list` 返回两个内置技能名；技能页显示来源 `asset` 与允许工具。

### 2.4 MCP Client

| 文件 | 作用 |
|---|---|
| `core-mcp/JsonRpc.kt` | 帧构造 / 解析 / `extractPayloads`（json 单对象、批量数组、SSE 多帧） |
| `core-mcp/McpTransport.kt` | **只有端口**（实现移出，见下） |
| `core-mcp/McpSchema.kt` | 命名空间化 `mcp__<alias>__<tool>`、描述剥控制字符、schema→ToolParameter |
| `core-mcp/McpClient.kt` | `server/discover` → 回落 `initialize` + `notifications/initialized`；`tools/list` 分页；`tools/call` |
| `core-agent/tool/ToolSource.kt`（新） | 可整体下线的工具来源端口 |
| `core-data/NetMcpTransport.kt`（新） | 复用 `:core-net` 的 `HttpTransport`（连接池 / DNS 闸 / 代理与 LLM 请求同源） |
| `core-data/McpToolSources.kt`（新） | 全量重建注册（`registerContributed`：同名拒绝 + 强制审批） |
| `core-data/McpServerStore.kt`（新） | `mcp-servers.json`，落盘前脱敏 Authorization |
| `feature-settings/McpServer{List,Edit}Screen.kt` | 列表（状态 chip / 缓存时间）+ 编辑（测试连接 / 保存） |

**⚠️ 未做真机联调**：没有可用的公网 MCP server（且本仓不引第三方 SDK），
设备侧只验证了列表/编辑页渲染。协议层由 36 条单测（FakeTransport）覆盖。
这条必须如实记成「协议已实现、生态联调未做」。

### 2.5 上下文工程（I1 修复 + 缓存口径）

| 项 | 落地 |
|---|---|
| **I1（W26-04）** | `ChatHistoryMapper.kt`（新）：MODEL 气泡的轨迹重建为 `assistant(tool_calls)` + 逐条 `tool(result)`；SKIPPED 不重建（否则会被补成假失败） |
| 回归钉 | `ChatHistoryMapperTest` 8 条（含「失败轨迹保留错误」「多调用一一对应」） |
| 缓存口径 | `docs/notes/w3-provider-matrix.md` §五：**命中率随供应商而变**（NVIDIA 端点 64k/256k 命中 >97%），修正 W26-05 的「恒 896 不可控」表述 |

### 2.6 语音输出（交付但默认关闭）

`core-agent/tool/tools/SpeakTool.kt`：本地 TTS，端口 `SpeechEngine`（可 JVM 测），
状态码细分 `tts_language_unavailable` / `tts_enqueue_failed(code)` / `tts_timeout`。

**为什么默认不注册**：真机上调用 `speak` 的那一轮长时间无产出，**且点停止也没有立刻收敛**。
但**归因必须如实**：把 `speak` 从注册表摘掉后，同样题面**仍然**挂住 ⇒
「TTS 导致挂死」这个结论**不成立**，真因是设备到 OpenRouter 的链路抖动 + 重试期间无可见反馈
（见 §五 第 1 条）。因此 `speak` 默认关闭的理由改为：
**未经真机验证的 OEM TTS 路径不默认暴露**（该设备默认引擎是 `com.iflytek.speechsuite`，
初始化 + 语言包状态未知），而不是「已证实会挂死」。

实现与 7 条单测保留；换 TTS 正常的设备取消 `AppContainer` 里那行注释即可启用。

---

## 三、真机验收（本波实测）

| 步骤 | 判据 | 结果 |
|---|---|---|
| 安装 / 冷启 | `am start -W` | `COLD`，`TotalTime=1292ms` |
| 工具循环 | 截图 + 轨迹卡 | `calculator {"expression":"37*89"}` → `3293`，绿勾 2ms |
| 思考块 | 截图 | 折叠卡有内容，与正文分道 |
| 用量行 | 截图 | `2.0k in / 13 out (0 cached, 0.0%)` + `上下文 2K / 1048.6K` |
| 记忆写入 | 设备文件 | `memory.json` 内容逐字一致 |
| 记忆注入 | 新对话提问 | 未调工具直接答 `metric` |
| 技能 | `skill_list` | 返回 `notes-organizer` / `pdf-toolkit` |
| 视觉 | 附件 + 提问 | 答出 `HARNESS-42` |
| 设置页 | 三个新入口 | 技能 / MCP / 记忆全部渲染，无遮挡 |
| `speak` | 工具调用 | 该轮无产出且停止不即时收敛；**关闭后同题面仍复现** ⇒ 归因不成立，默认关闭（见 2.6） |
| 重试可见性 | 真机 | 修复前：三个点永远跳、无任何提示；修复后：`重试中` 状态行（本波新增） |

---

## 四、本轮 vacuous（不许写成通过）

| 项 | 为什么 |
|---|---|
| MCP 真机联调 | 无公网 server；只跑了协议单测与 UI 渲染 |
| Skill 导入（SAF → zip → 校验 → 落盘） | 未接线，`onImport = null`，按钮不渲染（不做假按钮） |
| 逐端点 `supportsVision` | 未进 `RemoteEndpoint`；端不支持时由服务端 404/400 暴露 |
| 长上下文**设备侧** | 只在 PC 侧脚本验证（815k 命中）；设备上最大只跑到 2.5k |
| journal / FreezeClock（roadmap W3 内核硬化） | 本波**未做**，被能力补齐挤占；下一波第一优先 |

---

## 五、两条新铁律

1. 🔴 **判据自身要先被验证**：长上下文首轮 9/9 全 False，真因是 `max_tokens=64` 被 reasoning
   吃光、`content` 返回空串。若只看汇总数字，会得出「模型没有长上下文能力」这个**相反**结论。
   ⇒ 任何「命中/不命中」类判据，必须先跑一个**已知必然通过**的样本（这里是 8k）。
2. 🔴 **「关闭可疑组件后仍复现」= 归因不成立**：本轮先把长时间无产出归因到 TTS，
   把 `speak` 摘掉后同题面**仍然**挂住 ⇒ 真因是网络重试。**排除法的证据是「关掉它就好了」，
   不是「它看起来最可疑」**。同时留下的产品缺口是真实的：重试期间 UI 必须有可见状态
   （已修：`ChatStatus.RETRYING` / `CANCELLED_EMPTY`）。
3. 🔴 **工具的外部 I/O 必须有常量级最坏返回时间**：`speak` 的引擎调用改为专用 Looper 线程 +
   5s 硬超时；任何「可能无限期阻塞」的工具都不许进默认工具面。

---

## 六、挂账

| # | 项 | 备注 |
|---|---|---|
| W3-01 | `speak` 真机未验证 | 已默认关闭；需换 TTS 引擎正常的设备复验（原「挂死」归因已撤回） |
| W3-08 | 设备→OpenRouter 链路抖动 | 真机复现：同模型 PC 侧 2.9s，设备侧连续重试不收敛；需换网络/换端点复验 |
| W3-02 | Skill 导入链路 | 校验器已就绪，缺 SAF + 解压落盘 |
| W3-03 | MCP 真机联调 | 需要可用的公网 server 或自建夹具（夹具不进 APK） |
| W3-04 | MCP token 进 Keystore | 现在落盘脱敏 ⇒ 重启需重填；W7 与 SecretRedactor 一起做 |
| W3-05 | journal / FreezeClock | roadmap W3 内核硬化，未启动 |
| W3-06 | 长上下文设备侧行使 | 需把大文件读入工具面（`file_read` 分页 + 1M 端点） |
| W3-07 | 逐端点 `supportsVision` | 端点编辑页加开关 + 探测 |
