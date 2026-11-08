# W9 第一车 · 真机功能矩阵 · 跑前预登记（方案席，2026-11-06）

> **纪律：判据先写死、跑后不得改。** 若确需改判据 ⇒ 版本 +1（v2）并**原样重跑**，
> 不许边跑边调口径，不许把「未触发/0 命中/vacuous」写成「已验证通过」。
> 建立：2026-11-06（方案席沈思远）。基准：远端 main = 本地 HEAD = `e7d7a1e`，工作树干净。
> 派单口径 `docs/notes/w9-plan.md`；上位裁决 `桌面/w9-终版落锤裁决.md` + `桌面/裁决/W9开工裁决与顺序.md`。
> 先例：`docs/notes/w8-feature-matrix-preregistration.md`（本文件沿用其四态与分组口径，新增「判据触发条件/空命中语义」列）。
> **本文件只登记判据与已知阻塞，不判定任何项通过**（结论写进 handoff 与本波验收记录）。
>
> **v1.1（2026-11-06，主理人两批 PC 侧实测并入）**：本次**只补事实、实证化前置条件、登记已知坑**，
> §三 的**判据文本 / 四态 / 阈值一条未改** ⇒ 不构成「跑后改判据」，不触发版本 +1 与重跑。
> 事实来源 = 主理人本波真实 key 实测（PC 侧 `stream:false` 最小往返 + `max_tokens=512` 复跑 + 视觉/工具/KV 探针），
> 全部标 **PC 侧事实**，**不得**写成设备侧可用或结论性通过；仍须在真机按 §三 判据重新行使取证。

## 〇、四态口径与总纪律

| 符号 | 含义 | 硬要求 |
|---|---|---|
| ✅ | 通过 | 判据被**真实行使**且取证链完整；`文件:行` / 日志 / 截图 / 响应四元组可指认 |
| ⚠️ | 部分 | 只有一侧/单模型/PC 侧成立，或只取到事实表不承诺结论；须写明缺哪一侧 |
| ⛔ | 不适用 | 平台/供应商/能力位**根本阻断**，判据不可执行；须写明阻断物 |
| ⛔vacuous | 判据没被行使 | 判据存在但本轮路径未走过、或被双阻断吞掉、或双向断言一侧恒 0；**0 命中 ≠ 通过** |

🔴 **三铁律**：
1. 「未触发」≠「已验证通过」；「测试全绿」≠「口径被验证」（AGENTS.md §二.3）。
2. **判据自身先验证**：每组判据先跑「已知必通过 + 已知必失败」样本；只跑绿样本 = 恒绿断言。
3. **WARN 判据有闩**（每 run 只报一条，如 nativeToolsMismatch）⇒ 0 命中 ≠ 通过，
   须回源码确认触发条件覆盖本轮场景，否则记 ⛔vacuous（知识库 §4）。

---

## 一、共享前置条件（违反 ⇒ 该项判据整体无效，不是「失败」而是「不可判」）

1. 🔴 **`max_tokens` 必须给足（每条判据共享，违反 ⇒ 该项判据整体无效 = 不可判，不是「失败」）**：
   **基线 ≥512；思考型模型建议 ≥1024**；tools 需 ≥512、xhigh 档比较需 ≥1500、1M 档比较需 ≥1500。
   - **本波实测（主理人，PC 侧 `stream:false`, `max_tokens=32`, 题面 `Reply with exactly: PONG`）**：5 模型
     全部 HTTP 200，但 OR dots / OR north / NV mistral-large-4-1t 三模型 `content=None` —— `completion_tokens=32`
     被 `reasoning_tokens`（34 / 33 / 吃满）**吃光**；NV nemotron-vl 与 NV step-5-preview 仍出 `PONG`。
     ⇒ 只看 `content` / 汇总会得出「模型不回复 / 该端点坏了」的**相反结论**。
   - **复跑证实前置条件成立**：`max_tokens=512` 后 OR dots → `'\n\nPONG'`、OR north → `'PONG'`、NV vl → `'PONG'`
     ⇒ `content=None` **纯属预算被 reasoning 吃光**，不是能力缺陷（W3「判据自身先验证」现场复现）。
   - **任何「模型没做 X」的结论前先看 `finish_reason` + `completion_tokens`/`reasoning_tokens` 拆分**，
     并按 §三 各项的「空命中语义」处理；不得把 `finish=length` 读成「功能不支持」。
2. **App 恒 `stream:true`**（`OpenAiCompatibleAdapter.kt:77` `put("stream", true)` + `:80` `stream_options.include_usage`）；
   PC 侧非流式结论迁移到 App 须**重新取证**（W8 曾因 20+ 份非流式探针结论反转）。
3. **强制登记四元组**：`max_tokens / prompt_tokens / completion_tokens / finish_reason`；
   按端点分组逐请求落盘，禁止只记结论不记数字。
4. **1M 档结论需 ≥2 次重放**才写稳定结论（W7 纪律）；单次样本不支持单调性/上限结论。
5. **设备侧与 PC 侧分开记账**：PC 侧是纯 Python/HTTP 直连、绕过 app 内核
   （`_w7/w7matrix.py` 同款）⇒ 不得把 PC 侧 1M 写成设备侧可用。
6. **真机题面一律英文 ASCII**：`input text` 非 ASCII 设备侧 NPE ⇒ 中文题面无法注入，
   空格用 `%s`（知识库 §2）；本表所有设备侧题面用英文。
7. **adb 通道**：`<workspace>/_j2env/sdk/platform-tools/adb.exe -P 15037`；
   本席预登记时实跑 `devices` ⇒ `<设备序列号> device` 在线（OPPO PDRM00 / Android 13 / SDK 33）。
   **主理人已唤醒确认：`SCREEN_STATE_ON` + `Awake`，设备 `<设备序列号>`**（本波真机前置已满足，但
   每次安装前仍须按下方判据复验，设备状态会随会话变化）。
   安装前必须 `KEYCODE_WAKEUP` + `exec-out screencap -p` **确认截图非全黑**（灭屏 `pm install-commit`
   永久挂起、AOD 仍可能黑）；`adb reverse` 与后续 input/screencap **必须在同一条 Bash 调用里**
   （daemon 每条命令后退出）；读二进制一律 `exec-out`；设备侧 `-f` 落盘 logcat（PC 缓冲仅 ~256KiB）。
8. **点按判据以 journal/应用日志/截图为准**：Compose bounds 在 `uiautomator dump` 不可靠；
   键盘弹出后发送键上移 ⇒ 每次按截图重定位；应用商店弹窗抢前台 ⇒ `am force-stop com.heytap.market`；
   周期性 `input keyevent 224` 保屏。
9. **凭据只以只读方式从环境注入**，绝不写进任何文档/日志/截图上屏/请求体回显
   （AGENTS.md §六）；curl 访问外网需 `--ssl-no-revoke`；沙箱代理端口**跨会话漂移** ⇒ 启动前
   `env | grep https_proxy` **现取**（本席预登记时现值 `http://127.0.0.1:6935`，不写死），
   用错端口 gradle/curl 静默走坏代理。`deepseek-v4.1-flash` 有 420s 超时坑 ⇒ ⛔ 不排入题面。
10. **端点配置固定**：`RemoteEndpoint` 新增 10+ 字段后 `RunnerKey(endpoint, …)` 含整个端点
    ⇒ 改任一高级配置即**重建 runner**，压缩×缓存验收必须固定配置（W9-01 前置提醒）。
11. **先本地 UI + 功能测试 + adb 调试，真机测过没问题再推仓库及 CI 构建**（用户本波硬要求）；
    矩阵验收在 W9-00 出口判据与四闸门之后、推送之前行使。
12. **取证文件落盘**：`_w9/matrix/`（`_w8/` 同款，被 gitignore 排除、不在仓库内 ⇒ 外部复核者
    只能验 3/4 闸门，w8-code-review R-1 教训）⇒ **关键日志计数/末行摘要须抄进 handoff 正文**，
    否则等于没沉淀（知识库 §6「记忆 ≠ 交付物」）。
13. 🔴 **判据按 `finish_reason` / `tool_calls` 判，不按 `content` 判**（本波工具调用实测）：
    `get_weather` 工具往返时 OR dots 与 NV vl 的 `content` **都是空串**，但
    `finish_reason=tool_calls` 且 `tool_calls[0].function.arguments` 含 `city=Paris` ⇒ 工具/多模态/多上下文
    各项判据必须以**结构化字段**为准；按 `content` 判会得出「空输出」的相反结论，与
    `W9-09` 断流误标 STOP（`ProviderAdapter.kt:192` `textChars==0 ⇒ STOP`）同型。
14. 🔴 **精度纪律（W7）**：1M 档与任何「上限」结论 ≥2 次重放；单次样本（如 `reasoning_tokens` 五档
    流式 `low 158 / high 441 / xhigh 193` 非单调）**不支持单调性 / 上限结论**，只登记事实表。

---

## 二、端点分组（用户点名 + 已实测，跑前定死）

| 组 | 模型 | 上限 / 能力 | 备注 |
|---|---|---|---|
| OR-FREE-1 | `dots-studio/dots-3-note-preview:free` | **本波 `/v1/models` 实测 context_length=512000**，`input=[text,image]` / `output=[text]`，prompt/completion **均 0 价**；工具 + 视觉双通 | `supported_parameters` 无 `reasoning_effort`；`reasoning` 对象族（W8 9 组 `stream:true` 已闭环）；思考型，`max_tokens` 须给足（§一.1） |
| OR-FREE-2 | `cohere/north-mini-code:free` | **本波实测 context_length=256000**，纯 `text` 输入 | 无 `response_format`；可作「不支持档」负样本；同为思考型（32 token 时 reasoning 33 吃光） |
| OR-FREE-3 | `google/gemma-4-26b-a4b-it:free` | 262144，`text+image` | **本波新增（主理人）**：视觉备选，仅登记未实测；⚠️ 与端侧旧仓 `gemma-4-E2B/E4B-it-gpu` 引擎初始化 NOT_FOUND **不是同一模型**，勿套用「已知不可用清单」（知识库 §4） |
| NV-1 | `nvidia/nemotron-3.5-super-vl-120b-a12b` | **实测 1,000,000**（含输出/视觉/工具三通；端点报 1,048,576 属超报） | 直连缓存 79.4% 是**唯一正向证据**；`contextLength` 超报挂 W5-02；本波 32/512 token 两档均出 `PONG` |
| NV-2 | `mistralai/mistral-large-4-1t-a52b`（完整 ID，本波补） | 待测，按 build.nvidia.com 模型页登记 | 经 `https://build.nvidia.com` 测试；上限须实跑登记，不采信页宣；32 token 时 content 是**英文思考串**、非 PONG |
| NV-3 | `stepfun-ai/step-5-preview`（完整 ID，本波补） | 待测，同上 | 同上；32 token 时 `content='PONG'` ✅ |
| ⛔ | `deepseek-v4.1-flash` | — | 420s 超时坑，**不排入题面** |
| 音频 | OR 两模型 `supported_voices=null`；NV `/v1/models` **本波共 84 个模型**、0 audio-ish；`/v1/audio/speech` 404 | 无供应商 + 无 TTS 通道 | 见 §三「音频输出」⛔ 不适用。⚠️ W8 预登记写「NV 85 模型」，本波实测 **84** ⇒ 以本波为准，属模型池时点变化，不回改 W8 历史文件 |

**本波模型池核对（主理人实测，只登记）**：NV `/v1/models` 共 **84** 个模型，用户点名的
`nemotron-3.5-super-vl` / `mistral-large-4-1t` / `step-5-preview` / `deepseek-v4.1-flash` **全部在列**
（在列 ≠ 可用：`deepseek-v4.1-flash` 仍因 420s 超时坑 ⛔）。端点**上限一律实跑登记**，不采信页宣。

**能力位现实（本席回树核验）**：`EndpointStore.kt:118/130/142` 三组预置 `supportsReasoning = false`、
无 `reasoningStyle` 选择器、UI `EndpointAdvancedConfig.kt:61` `enabled = initial.supportsReasoning` 只读
⇒ **思考四态/五档生产不可达（NEW-W8-1）**。凡涉思考档位的矩阵项，本车按 ⛔ 不适用登记；
W9-00 只接 `REASONING_ONLY` 二选一与 reason 落点，**不承诺四态/五档真机可达**。

---

## 三、逐项判据（用户清单 10 项，每项：判据 / 触发条件 / 空命中语义 / 四态 / 取证 / 前置 / 已知阻塞）

> 表格列：**项 | 判据 | 可执行触发条件 | 空命中语义（0 命中怎么办） | 预登记四态 | 取证文件 | 已知阻塞**。
> 「预登记四态」= **跑前**依据已知事实的登记，不是跑后结论；跑后另记。
> 🔴 **§一 全部 14 条是下表每一行判据的共享前置条件**（尤其 §一.1 `max_tokens ≥512 / 思考型 ≥1024`、
> §一.13 按 `finish_reason`/`tool_calls` 判、§一.14 ≥2 次重放）⇒ 任一违反，该项**判据无效（不可判）**，
> 既不记 ✅ 也不记 ⛔，先补前置再重跑；不得把「前置没给足」读成「模型/功能不支持」。

| 项 | 判据（写死） | 可执行触发条件 | 空命中语义 | 预登记四态 | 取证文件 | 已知阻塞 |
|---|---|---|---|---|---|---|
| **① 多模型多上下文直至 1M** | 每个端点分组各档（8K/32K/128K/512K/1M）请求 `prompt_tokens` 达标且 `finish=stop`；登记四元组；NV 1M ≥2 次重放；**设备侧与 PC 侧分列** | PC：纯 HTTP 直连构造该档前缀；设备：须有批量题面注入通道（会话导入） | 0 命中（如档位请求全 400/timeout）⇒ 记「该档未行使」⛔vacuous，**不是「不支持」**；不得反推设备可用 | **⚠️ 部分（仅 PC 侧）** / 设备侧 **⛔ 平台阻塞** | `_w9/matrix/ctx-<group>-<tier>-{req,resp}.json` + `_w9/matrix/ctx-summary.md` | 设备侧双阻断：`PreStepBudget.enforce`（`TurnLoop.kt:49` 之前，32K 档 `hard≈27904`，995k 发请求前被收缩/trip）+ `RoundHeadGate`（`RoundHeadGate.kt:42` 判本 run 累计 `promptTokens`，`AgentPolicy.maxRunPromptTokens=400_000`，首轮 0 ⇒ **第 2 轮才熔断**）+ `compressedUpTo` 单指针；W9-11 承接，**本车不动内核**；`W5-02` 超报 |
| **② 多模态输入** | 200×120 左红右蓝双色真图 ⇒ 语义判读命中 `red, blue`；**禁用 1×1 样本**；App 路径端到端（选图→content `image_url`→回答） | 端点 `supportsVision=true`（`RemoteLlmEngine.kt:94`）+ `max_tokens≥512` + `reasoning.enabled=false` | 只发请求不验语义 / 只 PC 侧取到 `red, blue` ⇒ 设备侧 ⛔vacuous；`finish=length` 且无正文 ⇒ 判据无效（前置违反），不得记「模型不识图」 | **⚠️ 部分**（PC 流式 W8 已取 `_w8/parse-image.json`；设备侧 D7 待补证） | `_w9/matrix/vision-200x120.png` + `vision-{req,resp}.json` + `_w9/matrix/dev-D7.png` | 图片闸门 `AppNavHost.kt:166-168` 以 label 为 effect key ⇒ 同名端点启用后不刷新（P0-3/F-3，W9-00 修）；SAF「图片」collection 不索引 Pictures（知识库 §2）；Compose bounds 不可点 ⇒ 以 journal/截图为准。**v1.1 事实**：本波 PC 侧 200×120 真图 OR dots → `Red`、NV vl → `Red`（`prompt_tokens` 58 / 306）——只取到**单色**语义，双色 `red, blue` 判据未被完整行使 ⇒ 四态仍 ⚠️ 部分，**不得升级 ✅**（见 §3.1） |
| **③ 音频输出** | 设备侧 `speak`/TTS 链路可听输出 或 `/v1/audio/speech` 返回 200 且可播 | 需供应商 voice + 本仓 TTS 通道 | 端点无 voice / 404 ⇒ 判据**不可执行**，记 ⛔ 不适用；不得记「已验证不支持音频」以外的功能结论 | **⛔ 不适用（平台/供应商）** | `_w9/matrix/audio-probe.json`（登记 404/无 voice 事实） | OR 两模型 `supported_voices=null`；NV 85 模型 0 audio-ish；`/v1/audio/speech` 404；`W3-01 speak` 真机未验证、`AppContainer.kt` 默认不注册 TTS（台账 W3-01）；W8-plan §三「UI 不摆假入口」，AUDIO 输出模态假入口属 W9-00 P2 二选一 |
| **④ 工作空间文件读写** | `file_read`/`file_write` 端到端往返一致（写入内容读回逐字节相等）+ 审批闸门留痕 | App 内核工具循环 + 设备沙箱 `files/harness/workspace/` 可写 + 审批放行 | 只有单测、未跑设备侧工具循环 ⇒ ⛔vacuous（W8 交接 §四原话）；工具被审批拦下且无账行 ⇒ 未行使 | **⛔ 未行使（本车待跑）** | `_w9/matrix/ws-{read,write}.jsonl` + 设备侧 `run_*.jsonl` + 截图 | W6 夹具 `5×16KB` 已推入设备沙箱（W7 A5 用）；多步 UI 自动化不可靠 ⇒ 每步落判据；审批框遮挡输入框致发送键位移 |
| **⑤ 记忆** | `memory_write` → **重启 App** → `memory_read` 命中（跨进程持久，非同会话内存） | `FileMemoryStore`/`MemoryStore` 落盘 + 冷启动后读回 | 只在同会话内 read 命中 ⇒ 未证明持久，⛔vacuous；无 write 就 read ⇒ 判据未行使 | **⛔ 未行使（本车待跑）** | `_w9/matrix/memory-{write,restart,read}.jsonl` + 冷启动前后截图 | 中文题面无法注入 ⇒ title/content 用英文 ASCII；`MemoryStore.normalizeTitle` 归一化可能让不同 key 命中同一条（`FileMemoryStore.kt:45`）⇒ 判据须用唯一 title |
| **⑥ Skill** | 内置 Skill 加载 + 触发；`skill_load` 后正文以 `role=USER` 追加且 **system 段 SHA-256 不变**（`02-roadmap.md:733` 硬断言） | App 内核 + 内置 Skill 目录可读 + 题面能触发加载 | 只列目录不触发 ⇒ ⛔vacuous；system 段变了却仍记通过 ⇒ 判据被绕过 | **⚠️ 部分（仅内置）** | `_w9/matrix/skill-{load,history}.jsonl` + system 段 SHA-256 前后值 | `W3-02` Skill **导入**无入口（`SettingsRoute.kt:231 onImport = null`，外审记 :206 已漂移）⇒ 导入链路 ⛔ 不适用，本车只能测内置；MCP sampling/roots 未实现（W5-06） |
| **⑦ MCP** | MCP 真机联调（server 列出 → tool 调用 → 结果回 App） | 需 https 夹具 或 cleartext 例外裁决 | 明文 http 被平台拒（`transport_error`）⇒ ⛔ 平台阻塞；**不得**为测试放开 | **⛔ 不适用（平台阻塞）** | `_w9/matrix/mcp-block.log`（登记平台拒绝事实） | `app/src/main/AndroidManifest.xml:25` `usesCleartextTraffic="false"`（G15b 要求，**裁决「不为测试放开」**，W5-03 / W3-03 / W4-02 承接）；W9-02 等 MCP 真机激活在后续车 |
| **⑧ KV cache 分块命中** | 前缀逐字节稳定 ⇒ 第 N 次 `cached_tokens>0`；**改首字节 ⇒ 归零**（双向）；四段指纹 `SYSTEM/SKILLS/TOOLS_SCHEMA/HISTORY`（`core-context/PromptPrefix.kt`）分块归因 | 稳定前缀 ≥2 次重放；端点固定（RunnerKey 不重建）；`stream_options.include_usage=true` | OR 免费池 8KB 前缀四连发 `cached_tokens` 全 0、本波再证 **`cached_tokens`/`cache_write_tokens` 恒 0** ⇒ **「改首字节归零」不可执行** ⇒ ⛔vacuous；NV 直连 79.4%（`21680 cached`，W7 A5）是唯一正向证据；WARN 判据有闩 ⇒ 0 命中不算通过 | **⚠️ 部分 / OR 侧 ⛔vacuous**（不变） | `_w9/matrix/kv-{group}-seq{N}.json`（四元组 + cached_tokens + 指纹） | `W7-02` 保持 `OPEN`（三连发+改首字节无一满足双向断言 ⇒ 按「取到事实表」验收，**不承诺命中率**）；`W9-04` 三桶归一未做 ⇒ 两口径恒等不得写稳定命中率；不得为凑缓存加长 system（800 全价 vs 4096×0.5 当量）。**v1.1 事实**：NV vl 用 **1103-token 稳定 system 前缀三连发 ⇒ `0/0/0`**，短前缀不触发写入（历史 GLM 门槛 2303–2502 token）；**待证项：前缀长度 ≥4096 token 再测一轮**（见 §3.1），仍**不得据此写 CLOSED / 命中率承诺**（W7-02 明禁） |
| **⑨ 手动压缩** | 压缩后历史条数下降 + 分隔卡出现 + `manual_compress dropped=N kept=M cache_invalidated=true` 落盘 + **下一请求 `cached_tokens=0`（双向，别只看归零）**；导入 20 条 ⇒ 20 气泡；8 MiB+ 被拒且提示具体原因；release 包看不到入口 | 长会话 + 用户点确认 + debug 变体 | 确认框 ✅ 但执行返回**无损放弃**（noop 分支）⇒ `dropped=N` 与 `cached=0` 双向未触发 ⇒ ⛔vacuous（W7 A6 实证）；不得写 `CLOSED` | **⛔vacuous（W7-03 仍 OPEN）** | `_w9/matrix/manual-compress.jsonl` + 前后气泡截图 + 下一请求 usage | `ManualCompress.kt:34-40` 单指针「砍头 + compressedUpTo」是压缩×缓存互斥的根（W9-03 承接）；`W7-06` release 入口判据在本仓 `:app` 只有 debug 变体 ⇒ 不可执行 |
| **⑩ 自动压缩** | 长会话超阈值 ⇒ `auto_compact applied=true`（且 `shouldApply` 三级门放行） | 触发线 `SettledCompressPolicy.kt:46` 固定 50_227、不随档位；须超阈值 + 1M 档 100+ 轮 | `applied=true` 至今**零证据**（W7 全是 noop）⇒ ⛔vacuous；WARN 判据有闩、每 run 只报一条 ⇒ 0 命中 ≠ 通过，须回源码确认触发条件覆盖本轮场景；上一轮失败会污染下一轮（残留 + 历史体积叠加）⇒ A/B 结论须排除前次污染 | **⛔vacuous** | `_w9/matrix/auto-compact.jsonl` + 设备侧 `run_*.jsonl` | `W9-01`（压缩量纲统一，四外审同一缺陷禁拆）**本车不做**；`W8-01` 尾部摘要挂 `W9-03`；`W9-05` journal 未开波 ⇒ 无恢复卡；`applied=true` 零真机证据（triage §7.2.5 点名「判据写进方案 ≠ 判据被执行」） |

### 3.1 v1.1 PC 侧事实补录（只登记，不改任何项的判据/四态）

> 来源：主理人 2026-11-06 真实 key 两批 PC 侧实测。**全部是 `stream:false` + 纯 HTTP 直连、绕过 app 内核**
> （§一.5）⇒ 只作前置条件实证与已知坑，**不得**作为 §三 任何项的 ✅ 结论，也不得迁移成设备侧可用。

| # | 探针 | 事实（原样登记） | 对判据的作用 |
|---|---|---|---|
| B1 | 5 模型最小 chat 往返，`stream:false`, `max_tokens=32`, `Reply with exactly: PONG` | 5/5 HTTP 200：OR dots / OR north / NV mistral-large-4-1t 三者 `content=None`（`completion_tokens=32` 被 `reasoning_tokens` 34 / 33 / 吃满吃光）；NV vl `content='PONG'` ✅、NV step-5-preview `'PONG'` ✅ | **§一.1 前置条件实证**：预算太小 ⇒ 结论反转，W3「判据自身先验证」现场复现 |
| B2 | 同上题面，`max_tokens=512` 复跑 | OR dots `'\n\nPONG'`、OR north `'PONG'`、NV vl `'PONG'` ⇒ 证实 B1 的 `content=None` **纯属 reasoning 吃光预算**，非能力缺陷 | 判据前置条件**成立**；不构成任何项通过 |
| B3 | 视觉 200×120 左红右蓝真图 | OR dots → `Red`、NV vl → `Red`；`prompt_tokens` = 58 / 306 | ② 只取到**单色**语义 ⇒ 双色判据未完整行使，四态维持 ⚠️ 部分 |
| B4 | 工具 `get_weather` | OR dots 与 NV vl 均 `content` 空串但 `finish_reason=tool_calls`、`tool_calls` 含 `city=Paris` | **§一.13**：判据按 `finish_reason`/`tool_calls` 判，不按 `content` 判（与 W9-09 同型） |
| B5 | KV cache：NV vl **1103-token** 稳定 system 前缀三连发 | `cached_tokens = 0 / 0 / 0` ⇒ 短前缀不触发写入（历史 GLM 门槛 2303–2502 token） | ⑧ 新增**待证项**：前缀长度 **≥4096 token 再测一轮**；不改变 ⛔vacuous / ⚠️ 部分标注 |
| B6 | KV cache：OR 免费池 | `cached_tokens` / `cache_write_tokens` **恒 0** | ⑧ 双向断言一侧不可执行 ⇒ **⛔vacuous 风险**登记（原已标，本波再证）；**不写 CLOSED / 命中率承诺**（W7-02 禁止） |

🔴 **B5/B6 合读纪律**：`0/0/0` 是「短前缀 + 免费池」两个具体条件下的**事实表**，
既不能推出「缓存永不命中」，也不能把 W7 A5 的 NV 79.4%（**真机长会话形态**，非三连发探针）
套成稳定命中率结论。`W7-02` 保持 `OPEN`；`≥4096 token` 复测是本车**待证项**，跑后按 §三 四态另记。

---

## 四、显式「⛔ 平台阻塞 / ⛔vacuous」清单（**不得写成已验证**）

| 类别 | 项 | 原因 | 禁止写法 |
|---|---|---|---|
| ⛔ 平台阻塞 | **⑦ MCP** | `usesCleartextTraffic="false"`，裁决不为测试放开 | 「MCP 已联调 / 已验证可用」 |
| ⛔ 平台阻塞 | **① 设备侧 1M** | `PreStepBudget` 档位闸 27904 + `RoundHeadGate` 400k 双阻断（W9-11） | 「设备侧 1M 可用」（PC 侧结论不得迁移） |
| ⛔ 不适用 | **③ 音频输出** | 无供应商 voice + 无 TTS 通道 + `/v1/audio/speech` 404 | 「音频输出通过 / 已测」 |
| ⛔ 不适用 | **⑥ Skill 导入** | `W3-02` 无入口，只测内置 | 「Skill 全链路已验证」 |
| ⛔ 不适用 | 思考四态/五档 | `supportsReasoning` 恒 false + 无 style 选择器（NEW-W8-1） | 「D5 四态可用」（W8 交接 D5 ✅ 属证据强度虚标） |
| ⛔vacuous | **⑧ KV cache（OR 侧）** | `cached_tokens` **与 `cache_write_tokens` 恒 0**（v1.1 再证）⇒ 双向断言一侧不可执行 | 「缓存命中率已验证 / 不命中」（NV 79.4% 只是单一正向证据，不承诺命中率）、「W7-02 CLOSED」 |
| ⛔vacuous | **⑧ KV cache（NV 短前缀）** | 1103-token 稳定前缀三连发 `0/0/0`；短前缀不触发写入 | 「NV 缓存不工作」（历史 GLM 门槛 2303–2502 token；`≥4096` 复测是**待证项**，见 §3.1 B5） |
| ⛔vacuous | **⑨ 手动压缩** | `dropped=N` + `cached=0` 双向未触发，noop 分支真机行使 | 「手动压缩已验收 / W7-03 CLOSED」 |
| ⛔vacuous | **⑩ 自动压缩** | `applied=true` 零证据 | 「自动压缩生效 / 已行使」 |
| ⛔ 未行使 | **④⑤ 工作空间 / 记忆** | W8 未跑设备侧工具循环；本车待跑 | 「已验证」 |
| ⚠️ 部分 | **② 多模态** | PC 侧流式 W8 已取双色；v1.1 PC 侧 200×120 真图两模型只答单色 `Red`；设备侧 D6/D7 待补证 | 「多模态端到端已验证」「双色判据已通过」 |
| ⚠️ 部分 | **工具调用（B4）** | PC 侧 `finish_reason=tool_calls` + `city=Paris` 成立，但 `content` 为空串；设备侧未行使 | 「工具调用端到端已验证」；按 `content` 判 ⇒ 「空输出」的相反结论（W9-09 同型） |

**四态纪律**：跑后若某项未行使，维持上表登记并写「未行使原因」，**不得**因为「功能本身有单元判据 +
全量编译 + 真机冒烟」就把它升级成 ✅（W8 预登记 §五诚实声明原话）。
🔴 **PC 侧事实不得升级为设备侧通过**：§3.1 B1–B6 全部是 `stream:false` 纯 HTTP 直连、绕过 app 内核；
`max_tokens=512` 复跑只证明**前置条件成立**，不证明任何模型能力/App 路径/设备侧行为（§一.2/§一.5）。

---

## 五、PC 侧已闭环事实（可引用，不需重跑；迁移到 App 仍须重新取证）

> 本节是 **W8 周期**已闭环的流式事实；**W9 本波 v1.1 补录的两批实测见 §3.1**（B1–B6），
> 二者均为 PC 侧，合并阅读时不得互相套用（W8 是 `stream:true`，本波是 `stream:false`）。

W8 9 组 `stream:true` + `stream_options.include_usage=true`（`docs/analysis/w8-provider-probe.md` §六）：
`reasoning` 对象族（`enabled:false` 关思考 rtok=0 / `include_reasoning:false` 仅隐藏仍耗 32 rtok /
`effort` 五档单次样本**不支持单调性**，⚠️ 部分 / `response_format:json_object` 严格合法 JSON /
tools 在 `max_tokens≥512` 时 `finish=tool_calls` / 视觉 `red, blue`）；首帧 `delta.reasoning` 与
`reasoning_details[].text` 逐字等长 = 冗余双通道 ⇒ **不改 `decodeEvent`、不造伪工作量**。
W7 A5 真机 32K 档 `5×16KB` 并行读 ⇒ 实发 `27.2k ≤ 27904`、`21680 cached / 79.4%`、
`auto_compact=noop` 落账（这是**真机长会话形态**，不是三连发探针）。

---

## 六、诚实声明

- 本表**只登记不判定**；⛔vacuous ≠ 通过；⛔ 不适用 ≠ 失败；「组件有测试」≠「组件被行使」。
- 用户 10 项清单本车只做「按预登记行使 + 四态取证」，**不为凑绿改代码**；W9-01（量纲统一）、
  W9-05（journal）、W9-10（守卫专项）、W9-11（1M 解锁）留下一车，留痕口径见 `w9-plan.md` §一。
- 判据改动 ⇒ 版本 +1 且原样重跑；跑后结论写进 handoff 与验收记录，**不回改本表**。
  **v1.1 是事实补录不是判据改动**：§三 判据/四态/阈值零变更，故不触发重跑；若后续真要改判据 ⇒ 升 v2 原样重跑。
- 所有取证文件在 `_w9/matrix/`（gitignore 排除、仓外）⇒ 关键计数/末行摘要必须抄进 handoff 正文，
  否则外部复核者只能验 3/4 闸门（w8-code-review R-1）。§3.1 的 B1–B6 探针产物同样须落 `_w9/matrix/`。
- **PC 侧事实（§3.1 / §五）与设备侧结论严格分开**：`stream:false` 最小往返、512 复跑、视觉/工具/KV 探针
  全部绕过 app 内核，只用于实证前置条件与登记已知坑；任何「设备侧可用 / 已验证」仍须按 §三 判据真机行使。
- 设备侧能力位、平台阻塞、能力位替换非并集（`source == USER ? persisted : heuristic`，知识库 §4）
  均以**真机实测**为最终判据；本文件预登记结论与真机冲突时，**以真机为准并在 v2 记录差异**。
- **本波补录待证项（不阻塞本车交付，跑后另记）**：KV cache 前缀长度 **≥4096 token** 复测一轮（§3.1 B5）；
  OR-FREE-3 `google/gemma-4-26b-a4b-it:free` 视觉备选实测；NV-2/NV-3 完整 ID 的上限实跑登记。
