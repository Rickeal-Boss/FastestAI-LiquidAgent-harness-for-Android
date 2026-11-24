# W54 验收物 — 首个真代码波（P0 双修 / P1 安全四件 / N-1 定谳 / 真机多模型与压缩对照）

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 = W53 tip `033723607c5138be123ff5ac4e465a0449935772`。判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`。
> 「未触发」≠「已验证通过」；推断带 caveat、不写成事实。本验收物不写复合挂账号，段位用中文描述。
> 本波**新开 0 / 销账 0**；守恒 216 行 / 39 `CLOSED` / 166 `OPEN` / 11 `CARRIED`（逐值相等）。

---

## 步 1 · 四份外审的分歧裁决与方向

回源方式：全部处方先在现役树 `grep` / 实读，再决定采信或降级；报告查的是它那一版的树，**已存在 ≠ 待修**。

| # | 分歧 / 主张 | 裁决 | 四态 | 依据 |
|---|---|---|---|---|
| 1 | **P0-1**：`EndpointStore.loadOrSeed()` 把「解析失败」当「首次启动」，静默覆盖用户全部端点 | **采信并修** | ✅ | 实读 `EndpointStore.kt:91` 只判 `read != null`，`JsonFileStore.read()` 对「文件不存在」与「坏 JSON」都返 null；类头第 21 行明写「文件一旦存在就绝不补种」。同链 `persist()` 丢弃 `write()` 返回值（`JsonFileStore.kt:64` 注释要求调用方处理 false） |
| 2 | **P0-2**：压缩按 token 落刀、硬闸按字符裁决 ⇒「压完仍熔断」 | **采信并修** | ✅ | 实读 `ContextCompressor.compress(messages, budgetTokens)` 无字符维；`PreStepBudget` 的 `charCap = hard × effectiveCpt` 是权威判据。W53 现场 `chars=226442 cap=223027` 复现 |
| 3 | **P1-1**：MCP 路径无 URL 字面量闸（`GuardedDns` 对字面量 IP 早退） | **采信，但改落点** | ✅ | 报告建议「补到 `NetMcpTransport`」；本波按同一报告的 §6.3 采纳**下沉到 `HttpTransport.post()` 兜底**（一处覆盖所有调用方，LLM 链路幂等）。MCP token Keystore 不在本波（涉 W7 待办） |
| 4 | **P1-2**：`SandboxFiles.resolve()` 无 canonical、`listDir` 裸 `startsWith` | **采信并修** | ✅ | `SandboxPath.kt:50` 工具侧已是 `canonicalFile + separator` 边界并注释警告裸前缀；UI 侧同型补齐 |
| 5 | **P1-3**：9 处工具 `catch (Throwable)` 吞掉取消与 Error | **采信并修** | ✅ | 逐处实读；`ToolCallExecutor:181` / `AgentRunner:160` 已正确前置重抛，工具层是唯一缺口。`SpeakTool` 的 HandlerThread 回调**不**重抛（非挂起点，重抛只会打崩 TTS 线程） |
| 6 | **P1-4**：`McpClient` 的 `requestId` / `connected` / `era` 非线程安全 | **采信，窄修** | ✅ | `requestId` 改 `AtomicLong`、`connected` 加 `@Volatile`、`connect()` 加 `Mutex`；`era`/`sessionId` 的读写都在握手临界区内，未再加锁 |
| 7 | **P1-5**：G19 只扫 `core-engine`（0 个日志调用点）⇒ 恒 `VACUOUS` | **不采（登记）** | ⚠️ | 扩扫描面会立刻把 12 处拼接判红，属「守卫重新设计」而非本轮修复；且 G21 棘轮把 vacuous=1 合法化。本波如实保留 `VACUOUS`，挂到安全收口老号行内 |
| 8 | **P1-6**：15 张截图完全不进自动脱敏门禁 | **不采（登记）** | ⚠️ | 需要「二进制元数据检查」接进既有门禁 step，非 `.kt` 修复；本波仍以「入仓前逐张 `verify()` + IEND + 像素级目视」兜底 |
| 9 | **P2 十条**（`AgentLogFileStore` 无锁 / 错误体全读 / 生产 `!!` / 版本号固定 / NSC 与 UI 校验不一致等） | **不采（本波）** | ⚠️ | 逐条与 P0 无依赖；避免把代码波摊成二十项 |
| 10 | **N-1 `parallel_tool_calls` 保存误拒**（六波审查第 9 次承，台账无行） | **本波定谳** | ✅ | 现役树实读：键在白名单、不在保留键、`false` 不命中凭据值形态、解析链路完整、零测试引用。补一条 JVM 用例 → **通过** ⇒「保存误拒」与现役代码不符（若真机仍失败，根因在 UI 层丢值） |
| 11 | 外审「总闸 = 用户裁决 W20-02（core-kernel 449/450）才能开代码波」 | **不适用本波** | ✅ | 本波全部改动落在有余量的模块（core-data / core-context / core-agent / core-net / core-mcp / feature-tools / feature-chat 净增 ≤15 行），**未碰 core-kernel、未调任何 `LINE_CAPS`**。方向裁决：先做「不需要行数裁决」的 P0/P1，而不是等裁决 |
| 12 | 承接 W53 外审七条口径缺口（边界段判据 / 注记 6→13 / 89→24 行样本 / `g0` 时序 / 方法学披露 / 回填提交形态） | **采信并订正** | ✅ | 全部为 `.md` / `.txt` 内容订正，历史四态不回改；见步 2 表 |

**裁决总述**：采信 8 项（P0 两条 + P1 四条 + N-1 + 口径订正）；不采 3 项（G19 扫描面 / PNG 门禁 / P2 十条），全部挂到既有老号行内、零新开编号。

---

## 步 2 · 代码（第 1 车）

### 2.1 P0 双修

| 改动 | 文件 | 判据 / 自证 | 四态 |
|---|---|---|---|
| **P0-1a** 读失败不再补种：`loadOrSeed()` 先判 `files.exists()`；存在但解析失败 ⇒ 原文件一字节不动 + 另存 `.corrupt` 备份 + 返回内存镜像或空清单，**绝不写** | `core-data/.../EndpointStore.kt`、`JsonFileStore.kt`（新增 `backup()`） | 新用例 `unreadableFileIsKeptAndNotReseeded`：坏 JSON ⇒ 返回空清单、原文件逐字节保留、`.corrupt` 备份同内容 | ✅ |
| **P0-1b** 写失败必须上抛：`persist()` 判 `write()` 返回值，false ⇒ `EndpointSaveException(SAVE_FAILED)`（继承 `IOException`，`SettingsViewModel` 既有两条 catch 都能接住）；`McpServerStore` / `FileMemoryStore` 同型改抛 `IOException` | 同上 + `McpServerStore.kt` / `FileMemoryStore.kt` | 新用例 `writeFailureSurfacesAsSaveFailed`：基目录指向普通文件（跨平台确定失败）⇒ `upsert` 抛 `SAVE_FAILED` | ✅ |
| **P0-2** 压缩与硬闸同尺：`ContextCompressor.compress` 增加 `charBudget` 维，`WindowContextCompressor` 同时按 token 与字符贪心落刀（字符目标 = 水位线，装得下最新一条是硬底线）；`LayeredContextCompressor` 透传；内核 L1 用 `promptCharCap`（与 `PreStepBudget` 同一 `effectiveCharsPerToken`），UI 手动 / 自动压缩用 `compressTargets`（floor cap × 0.6） | `core-context/.../ContextCompressor.kt`、`LayeredContextCompressor.kt`、`BudgetFormula.kt`；`core-agent/.../loop/ContextBudget.kt`；`feature-chat/.../ManualCompress.kt`、`ConversationOps.kt` | 新用例 `charBudgetCutsEvenWhenTokenBudgetIsGenerous`（token 宽松 + 字符严格 ⇒ 必须按字符丢历史且保留后缀）、`charBudgetForcesCompressionWithoutTokenPressure`（1M token 预算 + 20k 字符 ⇒ `applied`）；真机同会话对照见步 4 | ✅ |

**量纲设计要点（避免「两把尺」再次分裂）**：`charBudget` 的语义是**目标**而不是硬上限；水位线只由调用方决定 —— 内核 L1 传硬 cap（它自己已有 token 水位线），UI 传 `0.6 × cap`（给 system 段与 sanitize 补丁留余量）。在压缩器内部再乘一次 0.6 会让 UI 路径压到 0.36×cap（本波实测到并已改掉）。

### 2.2 P1 安全四件 + N-1

| 改动 | 文件 | 判据 | 四态 |
|---|---|---|---|
| **P1-1** `UrlGuard.requireAllowed(url, allowPrivateNetwork)` 下沉到 `HttpTransport.post()`；`NetMcpTransport` 单独识别 `UnsafeUrlException` → `blocked_unsafe_url:<reason>` | `core-net/.../HttpTransport.kt`、`core-data/.../NetMcpTransport.kt` | 编译期 + 既有 `UrlGuard` 用例不退化；真机 LLM 链路未受影响（回声明、视觉、1M 全绿）。MCP 通道本身 **0 服务器 ⇒ vacuous** | ✅ / ⛔vacuous |
| **P1-2** `SandboxFiles.resolve` 改 `canonicalFile` + `base + separator` 边界，越界返回 null；`listDir` 补分隔符边界 | `feature-tools/.../SandboxFiles.kt` | 与工具侧 `SandboxPath` 同型；真机 workspace 浏览未受影响 | ✅ |
| **P1-3** 7 个工具文件 9 处 `catch (Throwable)` 前置 `catch (c: CancellationException) { throw c }`；`SpeakTool` 的 HandlerThread 回调保持原样并写明理由 | `core-agent/.../tool/tools/*` | 编译期 + 全量单测；契约与 `AGENTS §五.4` 对齐 | ✅ |
| **P1-4** `McpClient`：`requestId` → `AtomicLong`、`connected` → `@Volatile`、`connect()` 加 `Mutex` | `core-mcp/.../McpClient.kt` | 编译期 + 既有 MCP 用例全绿 | ✅ |
| **N-1 定谳** 新增用例 `parallelToolCallsPassesCombinedSaveChain` | `core-model/.../EndpointCapabilitiesTest.kt` | `parseExtraBody("parallel_tool_calls=false")` → `validateAdvanced(...)` 为空 ⇒ **通过**，「保存误拒」与现役代码不符 | ✅ |

### 2.3 承接 W53 外审的七条口径订正

| # | 订正 | 位置 | 四态 |
|---|---|---|---|
| 1 | 边界段交付判据与 §七 同步为「内容提交双 run 全绿」 | `docs/handoff-20261124-wave53.md` | ✅ |
| 2 | 制品清单老号注记「6 条」→「13 条」 | `docs/analysis/w53-acceptance.md` | ✅ |
| 3 | 「89 行逐行复核」→「窗口 89 行、入仓 24 行判据样本」 | 同上 | ✅ |
| 4 | 步 4 注明 `g0_secret_gate.txt` 由回填提交补入 | 同上 | ✅ |
| 5 | 回填提交「纯文档」→「纯文档 + 1 个门禁证据文本」 | 同上 | ✅ |
| 6 | W53 门禁证据补同型方法学披露 + `HN_GREP_RC` 辅助量说明 | `evidence/raw/w53/gate-seven-state.txt` | ✅ |
| 7 | `g1_build.txt` 的 MSYS `xargs` 告警加注记（不改原始输出） | `evidence/raw/w53/g1_build.txt` | ✅ |

全部为内容订正，**零 `.kt`、零新挂账号、不回改历史四态**。

### 2.4 行数与测试基线

| 项 | 实测 | 四态 |
|---|---|---|
| `feature-chat` 模块总量 | 3985 → **3999** / 4000（净增 14 行，未调上限） | ✅ |
| `ContextBudget.kt` | 一度到 404/400，改为把算式抽到 `BudgetFormula.promptCharCap` 后 **398** / 400 | ✅ |
| `core-kernel` / `GenerationRound.kt` | **零改动**（449/450、400/400 原样） | ✅ |
| `EXPECTED_TESTS` | 895 → **900**（`build.yml` 常量 + 变更史注释同提交） | ✅ |

---

## 步 3 · 四闸门（原始输出入仓）

| 闸 | 结果 | 四态 | 证据 |
|---|---|---|---|
| G1 编译 | `localbuild.sh :app:assembleDebug` rc=0；`BUILD SUCCESSFUL in 1m 14s` | ✅ | `evidence/raw/w54/g1_build.txt` |
| G2 架构守卫 | `result=PASS ok=27 vacuous=1 violation=0 fault=0`（G19 仍 vacuous，G20 141 ID） | ✅ | `evidence/raw/w54/g2_archguard.txt` |
| G3 守卫自测 | `30/30 PASS（307s）`（VAC-G1b / VAC-G21 自证 PASS） | ✅ | `evidence/raw/w54/g3_selftest.txt` |
| G4 全量单测 | `tests=900 failures=0 errors=0 skipped=0`（14 模块） | ✅ | `evidence/raw/w54/g4_fulltest.txt` |
| 脱敏门禁 | 本波新增文本与 14 张 PNG 入仓前逐件复核；`deviceLogTail` 形态外行 0 | ✅ | `evidence/raw/w54/deviceLogTail.txt` 末尾自检行 |

---

## 步 4 · 真机（多模型 / 多档位 / 压缩对照 / 功能面）

设备先唤醒（`mWakefulness=Awake`）；密钥留于真机、未回灌；端点能力以设备真相为准。

| 格 | 结论 | 四态 | 证据 |
|---|---|---|---|
| **回声（Lightning 30B / 128K）** | 输入框 dump 逐字 `repeat this token exactly and nothing else: echo-W54ECHO`；最终答案 `echo-W54ECHO`；`1.8k in / 251 out`、`上下文 1.9K / 127K` | ✅ | `p0_echo_lightning.png` |
| **多模态（Bolt E4B / 128K）** | 附 320×200 图（红方块 + 蓝圆）→ `The image contains two shapes: a red square on the left and a blue circle on the right.`；`1.6k in / 27 out` | ✅ | `p0_vision_bolt.png` |
| **文件读写 + 审批闸门** | `file_write` 授权卡 → 允许一次 → `wrote 9 chars to w54.txt` → `file_read` 回读 `W54FILEOK`；设备侧 `cat` 一致（9 B） | ✅ | `p1_file_approval.png`、`p1_file_rw.png` |
| **skill** | 技能页列出 `notes-organizer` / `pdf-toolkit`（来源 asset，允许工具清单可见） | ✅ | `p1_skills.png` |
| **MCP** | 「还没有 MCP 服务器」⇒ 判据未行使（P1-1 的 MCP 分支同 vacuous） | ⛔vacuous | `p1_mcp_empty.png` |
| **手动压缩（判据级）** | 264K 字符历史：确认框 → applied `manual_compress dropped=70 kept=77 cache_invalidated=false`；随后同一历史请求 `prompt=35338` **正常发出、无熔断** | ✅ | `p2_manual_compress_applied.png`、`p2_after_compress_run.png`、`deviceLogTail.txt` |
| **未压缩对照（判据会响）** | 同一 264K 历史不压缩直接发送 ⇒ `context_overflow_prevented: chars=262626 cap=228556` | ✅ | `p2_control_trip.png`、`deviceLogTail.txt` |
| **自动压缩（首次 applied）** | 192K 字符历史发送后评估：`auto_compact dropped=30 kept=83 cache_invalidated=false`（W53 只有 `below_trigger` / `nothing_to_drop`） | ✅ | `p2_auto_compact_applied.png`、`deviceLogTail.txt` |
| **1M 档（SuperVL 120B / 1M）** | 回声 `echo-W541M` 逐字；`1.8k in / 36 out`、`上下文 1.9K / 995.9K`（常规读数，非满窗真压） | ✅ | `p3_supervl_1m_echo.png` |
| **Nano Omni 30B / 128K** | 文本轮 `:W54NANO`；`1.8k in / 148 out` | ✅ | `p3_nano_text.png` |
| **North Mini Code Free** | `request rejected (HTTP 404)`（与 W53 同；用户声明可用，设备真相仍 404） | ⛔ | `p3_north_404.png` |
| **Dots3 Note Free（512K 端点）** | `request rejected (HTTP 404)`（W53 该端点可用，本波两轮均 404） | ⛔ | `p3_dots3_404.png` |
| **设备网络事件（诚实记录）** | 首次回声时设备 Wi-Fi 掉线（`Active default network: none`）⇒ 应用给出 `giving up after 1 retries` 失败卡；`svc wifi` 重连后重跑通过。属环境事件、非应用缺陷 | ⚠️ | `deviceLogTail.txt` 16:51:14 行 |

**回灌级别**：只写脱敏判据结论、`deviceLogTail` 固定形态探针行（30 行，形态外 0）与 14 张截图；不回灌整段 logcat / 凭据 / 端点完整标识与密钥 / 账号字段 / 序列号 / SSID / 本机路径 / 用户私人图片。

---

## 步 5 · CI（公开 API 四字段）

提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；PAT 仅 URL inline 一次性使用，remote 保持干净。

**结构（推送前声明）**：2 个提交 —— ① 内容提交（代码 + 测试 + 台账 + 三份文档 + 证据）；② CI 四字段回填提交（纯文档）。

<!-- CI-FIELDS -->

本地四闸门不代替 CI；CI 绿以公开 API 四字段为准。

---

## 明确未做（逐条带中文段位）

- G19 扫描面扩容（安全收口老号）：扩面会立刻把 12 处日志拼接判红，属守卫重设计，本波只登记。
- PNG 二进制自动脱敏门禁（脱敏门禁老号）：需把元数据检查接进既有 step，非 `.kt` 修复。
- MCP token 走 Keystore 信封（安全收口老号 / W7 待办）：本波只补 URL 闸，未动凭据落盘形态。
- 自动压缩触发线按档位化（压缩量纲老号）：`COMPACT_TRIGGER_CHARS=50227` 仍是固定 32K 档常量；本波修的是**压缩目标**与字符闸同尺。
- `TokenEstimator` 与硬闸的 `errorMessage` 口径、切换守卫 calibrated 率与 L3 floor 两把尺（压缩量纲老号余项）。
- 192K / 256K 档实际读数（本波取 128K / 512K 端点自然上限 / 1M）。
- 1M 满窗真压双硬限（真压两条老号）：本波仍只取常规读数 `1.9K / 995.9K`。
- MCP 外部 https（明文三条老号）：0 服务器 ⇒ vacuous。
- journal 二选一（journal 两条老号，需用户字面）。
- 内核再分层 / 450 上限处理（拆分三方老号，需用户单点裁决）。
- North 与 Dots3 的 404 供给方核查（外部依赖，设备真相已如实记录）。
- 凭据轮换（用户侧动作）。
- 历史缺口不补造。

## OPEN 挂账（触发信号 + 缓解动作 + 段位；零新开零销账）

| 段位 | 触发信号 | 缓解动作 | 本波状态 |
|---|---|---|---|
| 压缩量纲老号 | 压缩后仍熔断 / 自动压缩长期只 noop | 本波已修「压缩目标与字符闸同尺」；余项（触发线按档位、`errorMessage` 口径、切换守卫两把尺）等用户单点开波 | OPEN，行内注记 |
| 安全收口老号 | 真实外部 MCP 服务器 + 明文门禁解禁 / G19 恒 vacuous | 本波已补 URL 闸、沙箱 canonical、取消重抛、MCP 并发；MCP token Keystore 与 G19 扩面待开波 | OPEN，行内注记 |
| 脱敏门禁老号 | 新增二进制证据入仓 | 本波仍逐张 `verify()` + IEND + 目视；自动门禁待接 step | OPEN |
| 真压两条老号 | 双硬限注入 + 跨触发线历史 + 安全截断点 | 用户单独开波；1M 常规读数不算满窗 | OPEN |
| 明文三条老号 | 真实 MCP 服务器 + G15b 解禁 | 安全波整包；0 服务器 ⇒ vacuous | OPEN |
| journal 两条老号 | 用户字面二选一 | 不代裁、不合并销账 | OPEN |
| 思考块渲染老号 | 四态判据级截图齐 + 协作用例升级行为级 | 重新审议销否 | OPEN |
| 拆分三方老号 | 内核再分层或 449/450 触顶 | 用户单点裁决；本波零触碰 | OPEN |
| 拥堵断流老号 | 真机或 MockWebServer 前端复现 | 下一波单独开波收口 | OPEN |

## 交给外部审查团队的制品清单（边界声明）

**制品**：本验收物、W54 交接、`docs/w54-dispatch.md`、台账（`### W54 段` + 两条老号行内注记）、`evidence/raw/w54/`（g1–g4 原始输出、`deviceLogTail.txt`、14 张脱敏截图）、W53 七条口径订正、代码 diff（31 文件 / +406 −40）。

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / 整段 logcat /「建议下一链做什么」。

**边界**：① 本波首次开代码，13 个 `.kt` 生产文件有 diff，`core-kernel` 与 `GenerationRound.kt` 零改动、`LINE_CAPS` 未调；② P0-2 的真机证明是**同会话对照**（压缩后通过 / 不压缩熔断），不是满窗真压；③ MCP 与 1M 满窗仍 vacuous / 常规读数；④ North、Dots3 的 404 是设备真相，不写成已修复；⑤ 设备 Wi-Fi 掉线一次，已如实记录并排除在应用缺陷之外；⑥ 本波止于交接，交外部团队审查。
