# 06 — 挂账单一台账（deferred ledger）

> **本文件是挂账的唯一条目来源**。`docs/handoff-*.md` 里的「## 挂账」表是**开立记录**
> （写那一波时的事实），不再是状态来源。判状态、判是否丢失，只认本文件。
>
> 🔴 **编号唯一来源 = 本文件 §一 表格行**：`docs/notes/**` 与任何外审 / 路线草稿里出现的
> `W<数字>-<数字>`、`R-xx` 一律视为**历史输入**，不参与派单、不作为状态来源；判状态、判是否丢失、
> 派活只认本文件 §一 表格行（Q14 / 审查席 §五；G20 扫描面不含 `docs/notes/`，见 `scripts/arch-guard.sh:1327`）。
> **`W8-05` 已被路线草稿占用，禁用为新挂账号**；本波新号一律 `W9-0x`，承接表见 §二-bis。
>
> 建立于 W6（2026-11-01）。建立理由见 §零。

---

## 零、为什么要有这个文件

四份交接（W2.6 / W3 / W4 / W5）各自末尾一张挂账表，**没有汇总、没有状态字段、没有机器判据**。
后果是可量化的：**27 条挂账里 7 条在后续波次的抄写过程中静默丢失（26%）**，且丢的全是
「不产生新代码的收口活」（Skill 导入、Keystore、`supportsVision`、TTS 复验），
而承接下来的恰好都是「有真机阻塞、看起来更硬」的那几条。

最贵的一条是 **W3-05（journal / FreezeClock）**：它丢了之后，roadmap 里「从能跑到跑不死」
这条线被静默搁置四波；W5 真机一跑就用一次 33.8K vs 32K 预算的事故（新开 W5-01）
**重新发现了同一片未覆盖的面**。挂账丢了的代价，是三波后用一个真机事故重新发现同一个面。

对应的机械判据（本波落地，`scripts/arch-guard.sh`）：

| 守卫 | 判据 | 自证 |
|---|---|---|
| **G20** | 所有 `docs/handoff-*.md` 里出现的 `W<数字>-<数字>` 形态 ID，必须在本台账里存在；本台账里每条 `CLOSED` 行的「证据」列必须非空 | selftest：从台账副本删一条 ⇒ 必判红 |
| **G21** | `VACUOUS`（守卫扫到空集）条数 ≤ `VACUOUS_BASELINE`，只许减不许增 | selftest：清空一个模块的源码 ⇒ G1b 转 `VACUOUS` 且 G21 判红 |

---

## 一、台账

**格式固定**（G20 按此解析，改格式必须先改 G20）：

```
| ID | 项 | 开立于 | 状态 | 阻塞/关闭于 | 证据 |
```

**状态只允许三值**：

| 值 | 含义 | 硬要求 |
|---|---|---|
| `OPEN` | 已开立、未关闭、无人承接 | 「阻塞/关闭于」写阻塞物或目标波次 |
| `CARRIED` | 已换号承接（老号不再单独跟踪） | 「阻塞/关闭于」必须写**承接后的新号** |
| `CLOSED` | 已关闭 | 「阻塞/关闭于」写关闭波次；**「证据」必须非空**（`文件:行` 或提交号） |

**证据列的路径缩写约定**：`模块/相对短路径/文件:行` 省略了
`src/main/java/com/rickeal/liquidharness/`（及中间包路径）。`Glob **/File.kt` 可唯一定位。
提交号写成 7 位短哈希。

**回填来源**：`handoff-20261031-wave26.md` §十（5 条）+ `handoff-20261031-wave3.md` §六（8 条）+
`handoff-20261101-wave4.md` §七（7 条）+ `handoff-20261101-wave5.md` §七（7 条）= **27 条**。
`handoff-20261031-wave25.md` **没有挂账表** —— 它的挂项只有 §八.4 一句「W7 挂账：
`allowClipboardRead` 的设置页开关」，**没有编号**。无编号的挂项在抄写中必丢，故本台账补编号
`W6-05` 收进来（原交接正文不动，那是当时的记录）。

| ID | 项 | 开立于 | 状态 | 阻塞/关闭于 | 证据 |
|---|---|---|---|---|---|
| W26-01 | 「run 未结束时再次发送 ⇒ history 为空」 | W2.6 | `CLOSED` | 关闭于 W6 | 结构性双闸已就位：`feature-chat/ChatUiState.kt:189-193`（`canSend` 含 `!isStreaming`）+ `feature-chat/ChatViewModel.kt:256`（`if (!snapshot.canSend) return`）；钉住用例 `feature-chat/.../EventReducerTest.kt:148`（`isStreaming=true ⇒ canSend=false`）。关闭口径 = **结构性拦截 + 用例钉住**：原观测来自测试脚本绕过 UI 直接注入，该路径不再可达 |
| W26-02 | `no_tool` 是否容忍「只读探查」（T20） | W2.6 | `OPEN` | 需裁决；改判据必须登记夹具 v3 并原样重跑 20 题 | `scripts/fixtures/w2-tool-prompts.json:66` 仍是 `"version": 2`；T20 的 `expect.mode` 仍是 `no_tool`（`:223`）⇒ 裁决未做，夹具无 v3 |
| W26-03 | 压缩链路真机行使（批 B2） | W2.6 | `CARRIED` | 承接为 W5-01（W6 A 流） | W5 真机 D11 ⛔ 未触发（`handoff-20261101-wave5.md:134`），且撞出新缺口：实测发出 33 800 token 而预算 32 000、零压缩（同文件 `:142-155`）。本条并入 W5-01，由 A 流用假引擎单测 + 32K 档真机重放一次性收口 |
| W26-04 | I1 语义修复（工具轨迹进 history） | W2.6 | `CLOSED` | 关闭于 W3 | `feature-chat/ChatViewModel.kt:263` `snapshot.bubbles.flatMap { it.toHistoryMessages() }`；实现 `feature-chat/ChatHistoryMapper.kt:38`（**顶层扩展函数**，按类名 grep 会误判为未接线）；回归 8 条 `feature-chat/.../ChatHistoryMapperTest.kt` |
| W26-05 | 缓存命中不稳定（4/41，恒 896） | W2.6 | `CLOSED` | 关闭于 W3/W5 | 口径被证伪：`docs/notes/w3-provider-matrix.md` §五（命中率随供应商而变）+ `docs/notes/w5-provider-matrix.md`（NV 91.3%；设备侧 14 336 cached / 91.5%）。报告里不得再写「命中率随会话变长而上升」 |
| W3-01 | `speak` 真机未验证 | W3 | `OPEN` | 换 TTS 引擎正常的设备复验，或显式裁决「永不启用」 | `core-data/AppContainer.kt:161-169` 仍默认不注册（注释保留「换设备取消注释即可启用」）；实现 `core-agent/.../SpeakTool.kt` 与 7 条单测在位。原「TTS 导致挂死」归因已撤回（摘掉 `speak` 后同题面仍挂） |
| W3-02 | Skill 导入链路（SAF → zip → 校验 → 落盘） | W3 | `OPEN` | 无阻塞，缺实现；W6 C 流列为可选 | `feature-settings/SettingsRoute.kt:206` 仍 `onImport = null`（传 null 让 screen 不渲染按钮）；校验器 `core-skills/SkillImportValidator.kt` 已就绪 |
| W3-03 | MCP 真机联调 | W3 | `CARRIED` | 承接为 W5-03 | 见 W5-03 |
| W3-04 | MCP token 进 Keystore | W3 | `OPEN` | 推 W7（与 `SecretRedactor` 扩容同波） | `core-data/McpServerStore.kt:19-23` 自述「只做前者（不落明文），后者（Keystore 存 MCP token）留待 W7」⇒ 重启需重填 |
| W3-05 | journal / FreezeClock（roadmap 原 W3 内核硬化全部内容） | W3 | `CARRIED` | 承接为 **W9-05**（W8 功能波改期（老化五波，W9-05 必须开波，不得第 5 次顺延））；**尚未开波**；W6 方案刻意不开（与 A 流 `RunState` 改动冲突），建议独立 W6.5 | 全仓**零实现**：按 `Journal` / `FreezeClock` / `Ledger` / `Repetition` 四类 `find -iname` 全空；`grep -rn FreezeClock --include=*.kt` 仅 5 处**注释**，现役行号 `core-agent/loop/GenerationRound.kt:125` / `core-agent/loop/RunState.kt:307` / `core-kernel/.../policy/AgentPolicy.kt:211`（原记 117/121/210/168 已漂，审查席 F1/N5）。**如实表述订正（取代「注释写的是一个不存在的机制」）**：现役注释明写「**本仓尚未落地，挂账 W3-05** —— 全仓零实现，别在别处引用它当成已接机制」⇒ 注释**不误导**，缺的是实现本身；journal 挂账改号见 §二-bis（W9-05），老行不删 |
| W3-06 | 长上下文设备侧行使 | W3 | `CARRIED` | 承接为 W5-04 | 见 W5-04 |
| W3-07 | 逐端点 `supportsVision` | W3 | `CLOSED` | 关闭于 W9-00 | 三处同改（**现役工作树复核，取代已失效的「:92 硬编码 false」旧证据**）：`core-engine/RemoteLlmEngine.kt:94` `supportsVision = endpoint.supportsVision`（能力派生，不再硬编码）；`core-model/RemoteEndpoint.kt:202` `val supportsVision` 由 `Modality.IMAGE in inputModalities` 派生（`:152` KDoc 自述「三处同改」）；`app/AppNavHost.kt:175` `visionEnabled = endpointGate.value?.supportsVision == true` + `:188` `onPickImage = if (visionEnabled) pickImage else null` + `:190` `imageInputLocked = !visionEnabled` 闸门（`false` 时**保留禁用态入口 + 无障碍原因**，不静默隐藏）。⚠️ 行号随 W9-00 P0-3 响应式改造（`LaunchedEffect(state.endpointLabel)` → `produceState(activeId, container)`，`:166-175`）漂移，**以符号为准**、行号仅供定位；三处文件均存在。D6 判据文本订正写进 `docs/handoff-20261106-wave9.md` §五.5 / §六.5（历史 `docs/analysis/w8-solution.md:589` 不篡改）；真机 D2 `content-desc="当前端点未开启图片输入"` 见 `docs/handoff-20261106-wave8.md` §三 D2 |
| W3-08 | 设备→OpenRouter 链路抖动 | W3 | `CLOSED` | 关闭于 W5（归因被证伪） | `handoff-20261101-wave5.md` §三 E1–E4；E3（错密钥 + 对域名 ⇒ HTTP 401）证明连接/TLS/请求/响应四步全通。按纪律记「无法证伪」，**不**写成「已证伪」 |
| W4-01 | 设备→OpenRouter 请求不收敛（承接 W3-08） | W4 | `CLOSED` | 关闭于 W5 | 同 W3-08。`docs/notes/w5-network-triage.md` E1–E4 |
| W4-02 | MCP 真机联调 | W4 | `CARRIED` | 承接为 W5-03 | 见 W5-03 |
| W4-03 | 1M 设备侧行使 | W4 | `CARRIED` | 承接为 W5-04 | 见 W5-04 |
| W4-04 | 免费池 12/14 不可用 | W4 | `CARRIED` | 承接为 W5-07 | 见 W5-07 |
| W4-05 | C 组：首字节看门狗 / 等待态 / cached 三态 | W4 | `CLOSED` | 关闭于 W5 | 看门狗 = `core-agent/loop/GenerationRound.kt:112,165-167`（`generationTimeoutMillis` 的唯一消费点）；等待态 `feature-chat/ChatUiState.kt:61` `WAITING_FIRST_BYTE`；三态 `core-model/Generation.kt:56` `cachedPromptTokens: Int? = null` |
| W4-06 | `feature-settings` 只有 1 条测试 | W4 | `CLOSED` | 关闭于 W5 | 实测 21 条（`grep -rnE '^\s*@Test\b' feature-settings` = 21），含窗口档位边界钳制 |
| W4-07 | 会话级 `maxContextTokens` 对已缓存 runner 不生效 | W4 | `CLOSED` | 关闭于 W5 | `core-data/RunnerCache.kt:21` `RunnerKey(endpoint, maxContextTokens)` ⇒ 档位进键即重建。⚠️ 残留窄窗口（`AppContainer.kt:327` 的 `runners.remove()` 绕过 `keepWhile`），见 W6-04 |
| W5-01 | 压缩判定点下沉（轮头 → 工具结果提交后 / pre-step 单点） | W5 | `CLOSED` | 关闭于 W7（A5 真机重放已行使；W8 回填） | 真机证据 `handoff-20261101-wave5.md` §五.1：32K 档预算 32 000，实发 **33 800 token** 且零压缩；根因订正见 `docs/notes/w6-plan.md` §2.1（判定点位置**不是充分原因**，M1 估算器低估 + M2 压缩器无损放弃同时成立）。**W6 进展**：JVM 判据 T1–T7 全绿（字符量纲 + 标定比值 + 熔断 `generateCount==0`）；W6 时真机重放未完成（免费模型侧 `SocketTimeoutException`），按 2026-11-02 裁决移交 W7-01。**W7 收口证据（`handoff-20261104-wave7.md:82` §四 A5）**：32K 档 **5×16KB 并行读 ⇒ 实发 `27.2k ≤ 27 904`**（余 704）、**`21680 cached / 79.4%`**，`auto_compact=noop` 落账；**真机重放已行使** ⇒ 原「实发 ≤ 27 904」待证判据已取到，本行关闭 |
| W5-02 | `contextLength` 超报（1 048 576 vs 实测 1 000 000 含输出） | W5 | `OPEN` | W6 A 流实施中 | `docs/notes/w5-provider-matrix.md`（1 000 083 → HTTP 400，9 次复现；999 662 + 338 = 1 000 000 顶死）；预算算式 `core-kernel/.../context/ContextCompressor.kt:37-41` 已减 `maxOutputTokens`。**写回链现状（W6 复验，取代此前「零调用点」的判断）**：四跳全接 —— `ChatViewModel` → `ConversationOps` → `core-data/ContextLimitWriteback.offer(...)`（**在 `:core-data`，不是 UI 层**，`record` 是注入式 lambda）→ `AppContainer.recordContextLimit`；判据 = 三跳各有断言 + 第四跳 `ContextLimitWritebackTest` **5 条纯 JVM 用例**（「恰好一次 + 参数正确」/ hint 为 null、0、-1 零调用 / 端点 id 为 null 或空白零调用 / 普通异常静默）+ **第 6 条「`record` 抛 `CancellationException` 原样重抛」已跑验通过**（产物 `tests=6 / failures=0`；三处源 mtime `AppContainer 21:28` / `ContextLimitWriteback 21:44` / 测试 `21:52` 均早于产物 `21:56`）⇒ **四跳全接且全有判据，该子项闭合** |
| W5-03 | MCP 真机联调（承接 W3-03 / W4-02） | W5 | `OPEN` | 平台阻塞：需 https 夹具或 cleartext 例外**裁决**（安全取舍，单独出裁决文档） | `app/src/main/AndroidManifest.xml:25` `usesCleartextTraffic="false"`（G15b 要求，**不为测试放开**）⇒ 明文 http 被平台拒（`error: transport_error`） |
| W5-04 | 设备侧 1M 行使（承接 W3-06 / W4-03） | W5 | `OPEN` | 阻塞：无批量题面注入通道（W6 C 流的「会话导入」是解锁器） | PC 侧 994 998 命中（`docs/notes/w5-provider-matrix.md`）；设备侧最大只跑到 2.5k。`handoff-20261101-wave5.md:135` D12 = ⛔ vacuous |
| W5-05 | `autoApproveDangerous` 无消费点 | W5 | `OPEN` | W7 限期二选一：接线（UI 开关 + 审计账行 + 三条用例转正式消费点）或连字段带用例一起删 | `core-kernel/.../policy/AgentPolicy.kt:115` 仅定义；生产消费点 **0**（复验：`grep -rn autoApproveDangerous --include=*.kt` 只命中定义 + `core-agent/.../ApprovalChokepointAuditTest.kt:241,276,300` 三条警报用例）。⚠️ 那三条是「一旦有人接线就立刻红」的**警报**，不是「防护已生效」 |
| W5-06 | MCP `sampling` / `roots` 未实现 | W5 | `OPEN` | 落地时必须补进 `ApprovalChokepointAuditTest` 穷举表（现缺两行） | `grep -rn "sampling" core-mcp` 与 `grep -rn "roots/list" core-mcp` 均 **0 命中**（全仓只命中 `SamplingParams` 采样参数，与 MCP 协议无关）。没有为凑行数造假用例 |
| W5-07 | 免费池 12/14 不可用（承接 W4-04） | W5 | `OPEN` | **非代码项**：需账号持有人开隐私开关 | `docs/notes/w4-provider-matrix.md`（12 个被 `Free model training violation` 挡住）；W5 用 NVIDIA 绕开 |
| W6-01 | `AgentPolicy.compressContext` 零写入点（默认 true，无人设置） | W6 | `CLOSED` | 关闭于 W7（订正台账，W6 已接线） | **原挂账「零写入点」已被逐跳代码证据推翻**（主理人给的五处行号逐段回树核实，全部命中）：`feature-settings/SettingsViewModel.kt:191-196`（`setCompressContext`：:193 `container.settings.setCompressContext(enabled)` 写盘、:194 回写 state；:158/:164 为 `load()` 读取回显）→ `core-data/SettingsRepository.kt:91`（`val compressContext: Flow<Boolean>`）与 `:95-97`（`setCompressContext` 写 DataStore），其 `:70-76` KDoc 自述「接上它同时清掉这条挂账」→ `core-data/AppContainer.kt:261`（`val compress = settings.compressContext.first()`）、`:266`（`RunnerKey(endpoint, windowCap, compress)`，开关进键即重建 runner，避开 W4-07 同型）、`:272`（`policy = AgentPolicy(maxContextTokens = windowCap, compressContext = compress)` = 真实写入点）→ `feature-settings/SettingsScreen.kt:197-198`（`checked = compressContext` / `onCheckedChange = onSetCompressContext`，仅 debug 变体渲染）→ `feature-settings/SettingsRoute.kt:120`（`onSetCompressContext = if (debugTools) viewModel::setCompressContext else null`）。消费点 `core-agent/loop/ContextBudget.kt:157`（`if (!ctx.policy.compressContext)`）；定义仍在 `core-kernel/.../policy/AgentPolicy.kt:136`。**闭合口径**：这是「声明了没消费者」第 3 例的闭合，靠 UI → VM → Repository → Container → 内核的逐跳代码证据，不是「代码看起来有了」。`SettingsViewModel.kt:189` 过时 KDoc「那一行由主理人接」按裁决由 UI 席订正；`core-agent/loop/ContextBudget.kt:158` 与 `core-agent/.../ContextBudgetTest.kt:402` 仍写「零写入点」的注释一并登记，由内核席顺手订正，本席未越域修改。 |
| W6-02 | `EngineCapabilities` / `capabilities()` 零 main 调用点 | W6 | `OPEN` | 若要拿它当窗口上限通道，必须先接线；否则删（`w6-plan.md` §2.5b 明确不信任它） | `core-agent/engine/LlmEngine.kt:47` 只有接口定义；`core-data/RemoteEngineBridge.kt:33` 是 override 委托、`core-engine/RemoteLlmEngine.kt:89` 是实现；**main 源集零调用点**（只有 5 处测试 override）。这是「声明了没消费者」的第 4 例 |
| W6-03 | 守卫空转清单（`VACUOUS` 基线） | W6 | `OPEN` | 棘轮只许减不许增（`scripts/arch-guard.sh` 的 `VACUOUS_BASELINE`） | 首版 `VACUOUS=1`：**G19**（日志不拼凭据）扫的是空集 —— `grep -rn 'AgentLogStore\.' core-engine/src/main` = **0 命中** ⇒ 它对现役代码判不出任何东西，灵敏度只由 selftest 的变异自证保证。**不要**读成「凭据已经安全了」 |
| W6-04 | `saveEndpoint` 的 `runners.remove()` 绕过 `keepWhile`（W4-07 残留窄窗口） | W6 | `CLOSED` | 关闭于 W6（A 流） | 已改为 `core-data/AppContainer.kt:333` `runners.removeIfIdle(normalized.id) { it.isBusy }`（`recordContextLimit` 同样处理，`:369`）；实现 `core-data/RunnerCache.kt:93` `removeIfIdle`；钉住用例 `core-data/.../RunnerCacheTest.kt:107,118`。⚠️ 残留（有意保留）：`deleteEndpoint` 仍走 `runners.remove(id)`（`:376`）—— 端点已删，在途 run 的引擎指向已删端点属另一类问题。来源：`03-w5-review-and-next.md` §二 P2 |
| W6-05 | `allowClipboardRead` 缺设置页开关（保存后下一次 run 生效） | W2.5 | `OPEN` | 推 W7（与 `ApprovalPolicyScreen` 同波） | 字段有真消费点：`core-agent/.../ClipboardTool.kt:78` `if (!policy.allowClipboardRead && actionOf(...) == Action.GET) READ_DISABLED`；但**全仓无 UI/无 setter 写入它**（`grep -rn allowClipboardRead` 只命中 ClipboardTool + 3 个测试文件）。**原交接 W2.5 §八.4 只写了「W7 挂账」，未给编号 ⇒ 本条是本台账补的编号**（无编号的挂项在抄写中必丢） |
| W6-06 | `arch-guard.sh` 一次全量跑出现 G7/G8/G9 各打印两遍（`OK=26` 虚高 3），**不可复现** | W6 | `OPEN` | 待复现；再现时按 `scripts/arch-guard.sh` 头部 K3 的三步查（函数调用次数 / 同 id 两次 guard_ok / 是否有第二进程写同一输出文件） | 2026-11-01 20:47 一次全量跑实测（W6 首次接入 VACUOUS 之后，当次 rc=1）；之后 4 次全量跑（含最终验收 `OK=25 VACUOUS=1 VIOLATION=0`）**均未复现**，根因未定位。**登记理由：不可复现 ≠ 不存在**，不登记则下次出现会被当成新 bug 从头排查。详见 `arch-guard.sh` 头部 K3 |
| W6-07 | `core-kernel` 行数触顶（1 790 / 1 800，余 10）⇒ 拆分 `context/` 为 `:core-context` | W6 | `CLOSED` | 关闭于 W7（开波第一件事，前置重构）；per-module 上限之和 1800 → 1900（**拆分当时口径**），其中 **+100 为拆分开销**（两模块各自文件头 + KDoc）；`TOTAL_LINE_CAP=42500` **保持不动**；`core-kernel` 450 / `core-context` 1450（拆分当时）。W7 功能增量 +1000 分开记账见 **W7-07**（现役之和 2900，TOTAL_LINE_CAP 仍 42500） | **W6 原记录（保留）**：1 790 / 1 800 余 10，三分目录 `context/` 1 387、`policy/` 235、`log/` 168，峰值到过 1 849；性质 = 预算到期而非 W6 违规；触发条件 = 任何往 `core-kernel` 新增代码的 PR 先拆再加。**W7 处置证据**：`core-kernel/src/main/.../context/` 六支 1 387 行连同六支 JVM 单测整体下沉为 `:core-context`，12 支文件与 HEAD 逐字节 `cmp` 全一致（纯机械搬迁、零逻辑改动）；`settings.gradle.kts:39` 新增 `include(":core-context")`；新建 `core-context/build.gradle.kts:72` 的 `api(project(":core-kernel"))`；`core-agent/build.gradle.kts:47` 加 `api(project(":core-context"))`。拆分后 `core-kernel` 只留 `policy/` 235 + `log/` 168 = **403 行 / 2 文件**，`core-context` **1 387 行 / 6 文件**（逐文件 `wc -l` 实测）。**依赖方向单向无环**：`:core-context → :core-kernel`（`BudgetFormula` 依赖 `AgentPolicy`、两个压缩器依赖 `AgentLogStore`，只搬 context 不带这条边编译立刻红），反向边由 `scripts/arch-guard.sh` 的 G6 `FORBIDDEN_EDGES` 钉住；方向由一次真实编译判定（数字见下）。**三条硬约束全部守住**：① `TOTAL_LINE_CAP` 保持 **42 500 不动**（`scripts/arch-guard.sh:189`），全仓 main 实测仍 **34 983 / 42 500**（逐文件累加与 cat 管道两法对拍一致、209 文件自证）⇒ 拆分未让全仓变胖；② per-module 上限之和 1 800 → **1 900**，采用推荐分法 `core-kernel` 450（403 实占、余 47）+ `core-context` 1 450（1 387 实占、余 63），见 `scripts/arch-guard.sh:165-166`，**其中 +100 是拆分开销，已写进本行与本台账项，提交信息须同步写明（提交由主理人收口）**；③ 未采用「凑 1 800」的分法。**四组闸门原始数字（主理人 S1 单人串行，日志在工作区根 `_w7/`，三个脚本 exit code 全 0）**：`assemble-w7.log` `BUILD SUCCESSFUL in 1m 4s`、270 tasks（66 executed）、`:app:assembleDebug` 出包；`fulltest-w7.log` `tests=666 failures=0 errors=0 skipped=0`（其中 **8 条为 `SmokeTest.placeholder` 恒绿，真实断言 658**）、81 个 `TEST-*.xml`、**14 模块**（`include=14 == 数组=14 == 目录=14`）、新 `:core-context` **51 条**、XML mtime 19:37:02–19:37:14 全为本轮；`archguard-w7.log` `OK=25 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G9 include=14、G10 行数闸门 OK、G20 37 个 handoff ID 全在台账）；`selftest-w7.log` `28/28 PASS (178s)`。**同步项**：`MODULES` / `LINE_CAPS` / `MAIN_PATHS` / `CJK_SCAN_PATHS` / `FORBIDDEN_EDGES` 均 14；`scripts/arch-guard-selftest.sh:333` 的 G9 证据 `$n` 保持现算（加模块未假红），`preflight` 模块清单补到 14 并补回 W6 漏掉的 `core-kernel`；`.github/workflows/build.yml` 无模块矩阵、`EXPECTED_TESTS` 666 不变故未改；`scripts/fulltest.sh` 动态读 include 数无需改。**本波未处置（独立跟踪，W6 当时口径）**：另外三处同源触顶 `feature-chat` 3 459 / 3 500、`feature-settings` 2 921 / 3 000、`core-design` 12 439 / 12 600（W7 已给前两者功能增量上限 3900 / 3400，见 W7-07）。**⚠️ 现役值订正（W7 功能落地后）**：本行全部上限与行数均为**拆分当时快照**，不是现役值 —— 现役 `core-context` 1 584 行 / 8 文件 / 上限 1650、`core-kernel` 450 未变、`feature-chat` 3900、`feature-settings` 3400，per-module 之和 **2900** = 1800 + 100(拆分) + 1000(W7 功能)；`TOTAL_LINE_CAP=42500` **仍未动**（现役全仓 main **35 233 / 42 500**，逐文件累加与 cat 管道两法对拍一致、212 文件自证）；`scripts/arch-guard.sh` 的 LINE_CAPS 现役在 `:171-172` / `:185-186`、TOTAL_LINE_CAP 在 `:202`（本行早先写的 :165-166 / :189 是拆分当时行号，已漂）。完整裁决理由、守卫形态订正与计数口径见 **W7-07**。 |
| W6-08 | G10 的行数口径对「缺尾换行的 `.kt`」静默少算 1 行 | W6 | `OPEN` | 下一波做「可见性断言」（G10 顺带输出 `缺尾换行=N`，**不判红**，与 G19 的 VACUOUS 同一思路）；本波不动 G10 | `wc -l` 数的是**换行符**，故缺尾换行的文件少算 1 行且不告警。**现役不受影响（已复验）**：13 个模块 `src/main` 的 `.kt` 里缺尾换行 = **0 个**，且三种求和在现役树上一致 = **34 983**（逐文件 `wc -l` 累加 / `grep -c ''` 求和 / `find -print0 \| xargs -0 cat \| wc -l`）。影响量级 = 模块内文件数 × 1 行，对 `core-kernel`（余 10）而言 1 行 = 10% 余量误差 ⇒ 下一波补可见性即可。**两态齐全**（绿态 = 现役 0；红态可构造 = 手动去掉尾换行），所以它**可以是真判据**，但代价是给非标准编辑器保存的人一个额外红 ⇒ 先做可见性、不做判红 |
| W7-01 | **上下文压缩真机重放**（承接 W5-01 的真机部分） | W7 | `CLOSED` | 关闭于 W7（A5 真机重放已行使；W8 回填） | 待证判据：32K 档 + 并行读 5×16 KB ⇒ 请求实发 ≤ `hardContextBudgetTokens = 27 904`（或出现 `hard_budget_shrunk` 且最终仍 ≤ 27 904）。W6 已备好夹具：5 个 16.3 KB 文件已推入设备沙箱 `files/harness/workspace/module_{1..5}.kt`；失败现场 `_w6/d12.png`（`SocketTimeoutException`）。**收口证据（`handoff-20261104-wave7.md:82` §四 A5）**：换 NVIDIA 端点后 **32K 档 5×16KB 并行读 ⇒ 实发 `27.2k ≤ 27 904`**、**`21680 cached / 79.4%`**，`auto_compact=noop` 落账；**真机重放已行使**，判据取到，本行关闭 |
| W7-02 | **前缀缓存命中率验证**（承接 W5 缓存结论） | W7 | `OPEN` | 按「取到事实表」验收，**不承诺命中率**；不得 CLOSED（F7 / handoff `:93`） | W6 多模型实测（`docs/notes/w6-provider-matrix.md` §六.3）：`nv-vl` 三连发 `0 → 0 → 4336`（写入延迟 ≥2 次）、`dots` 恒 0、`north` 恒 0 ⇒ **缓存不可依赖**。待证：本仓前缀逐字节稳定时，第 3 次请求 `cached_tokens > 0`；且**不得为凑缓存加长 system**（800 全价 vs 4096×0.5 当量）。**⚠️ 部分：OR 免费池 `0→5120→0→0` / NV 直连真实会话 79.4%（`21680 cached`），不承诺命中率** —— `handoff-20261104-wave7.md:93` 明写「三连发+改首字节无一满足双向断言 ⇒ 按『取到事实表』验收，**不承诺命中率**」，A5 的 79.4% 是真实长会话形态（`:82/:86`），非三连发探针；按 `AGENTS.md` §二.3「未触发≠已验证通过」，**本行保持 `OPEN`**，不得照外审写 `CLOSED`（审查席 F7 订正） |
| W7-03 | **手动压缩 / 会话导入真机验收** | W7 | `OPEN` | 与 W7-01 同波；**A6 手动压缩双向断言未触发，保持 `OPEN`** | 仅单测（`ManualCompressTest` 10 条 / `ChatImportTest` 6 条 / `SessionImportParserTest` 19 条）。待证：① 压缩后历史条数下降 + 分隔卡出现 + `manual_compress dropped=N kept=M cache_invalidated=true` 落盘 + 下一请求 `cached_tokens=0`（**双向**，别只看归零）；② 导入 20 条 ⇒ 20 个气泡；带 `role=tool` 的文件显示丢弃计数；8 MiB+ 被拒且提示具体原因；release 包看不到该入口。**A6（`handoff-20261104-wave7.md:83`）**：确认框 ✅、执行返回**无损放弃**（noop 分支真机行使），但 `dropped=N` 路径与 `cached_tokens=0` **双向未触发** ⇒ 判据未被行使（⛔ vacuous 风险），**不得写 CLOSED** |
| W7-04 | 8 条恒绿占位测试（`SmokeTest.placeholder` = `assertTrue(true)`） | W6 | `CLOSED` | 关闭于 W7（三件同一次提交；CI 席 429 中断，由主理人代笔回写） | **三件齐，全部可复核**：① 8 个 `SmokeTest.kt` 已删（`app`/`core-agent`/`core-data`/`core-design`/`core-mcp`/`core-skills`/`feature-chat`/`feature-settings`，`git status` 对应 8 条 `D`，工作树 `find -name SmokeTest.kt` = 0）；② `.github/workflows/build.yml` `EXPECTED_TESTS: "696"`（= 704 − 8；变更史追加 W7 两档、恒绿段注释改写为已收口，「666 里只有 658 条」二档口径作废）；③ 全量确认 `_w7/s2-fulltest.log`：`tests=696 failures=0 errors=0 skipped=0`、77 个 `TEST-*.xml`、mtime 全为本轮 20:58:09–20:58:20、14 模块 `include=14`、**无模块 NO-SOURCE**（分布：app 7 / core-agent 212 / core-context 73 / core-data 55 / core-design 30 / core-engine 46 / core-kernel 4 / core-mcp 35 / core-model 47 / core-net 39 / core-skills 31 / feature-chat 82 / feature-settings 27 / feature-tools 8）。 |
| W7-05 | `PreStepBudget` 的 sanitize 上界只有论证、没有实测 | W6 | `CARRIED` | 推 W7（与 W7-01 同批真机时顺手取数） | `sanitizeForProvider` 会给「声明了没结果」的调用补合成结果，而 L3 字符判据量的是 `state.working()`（**sanitize 之前**）⇒ 实发字符可略超 `charCap`。上界式已写进 `PreStepBudget` 类头（`实发 ≤ charCap + 未配对数 × 模板长度`），但**没有任何用例验证过这个上界**。W6 裁决「接受 + 写清，不改结构」（改结构要多调一次 `prepare`）。**风险低**：合成结果字符量小、只在压缩丢宿主/调用中断时出现、方向固定（只会多算）。**耦合**：一旦把 `hardCharsPerToken` 调到接近 1.0，余量消失 ⇒ 必须改成 prepare 之后终检 |
| W7-06 | 两条「仅绿」可见性判据 + 正确检查顺序 | W6 | `OPEN` | 推 W7；**顺序必须先确认权限、再看设置项** | ①「release 包看不到「导入会话（调试）」」：实现是 `ApplicationInfo.FLAG_DEBUGGABLE` 注入回调，但 `:app` 只有 debug 变体可装 ⇒ **该判据在本仓不可执行**（W6 未验）；②「关掉压缩开关后请求真的接近窗口」：**假绿风险高** —— `ContextBudget.prepare` 在 `compressContext=false` 时直接返回全量，不写任何可观测输出，同时 provider 的 `context_overflow` 失败也会显示成「context 压缩没生效」的同一个样子。**必须先 grep 出「压缩是否真的没发生」的独有证据再跑**，否则会把 400 读成通过 |
| W7-07 | W7 per-module 上限功能增量 +1000 记账（与 +100 拆分开销分开）+ 待收口守卫与计数 | W7 | `CLOSED` | 关闭于 W7（`handoff-20261104-wave7.md:113` §七「本波关闭：… W7-07（预算记账 + 守卫落地）」；W8 回填） | **裁决理由（主理人 2026-11-04，原样留痕）**：「W6-07 的 1450 是按『搬迁 1387 + 拆分开销』给的，不是按 W7 功能给的；本波增量是真实功能代码，不是把拆分当扩容，而 `TOTAL_LINE_CAP` 才是 W5 铁律 3『不许变胖』的真判据。单文件 500 / 400 上限一律不动（`ChatViewModel` 484/500 余 16 ⇒ 新逻辑必须外提新文件）。」**现役上限（主理人已改 `scripts/arch-guard.sh`，本席不重复改）**：`core-context` 1450 → **1650**（+200，W7 新增 `SwitchGuard.kt`(109) + `SettledCompressPolicy.kt`(88)，现 main **1 584** 行 / 8 文件 < 1650）、`feature-chat` 3500 → **3900**（+400）、`feature-settings` 3000 → **3400**（+400）；per-module 之和 1900 → **2900** = 1800 + 100(拆分) + 1000(W7 功能)；`TOTAL_LINE_CAP=42500` **一律不动**（现役全仓 main **35 233 / 42 500**，两法对拍一致、212 文件自证）。现役行号：LINE_CAPS `scripts/arch-guard.sh:171-172` / `:185-186`、TOTAL_LINE_CAP `:202`。**守卫形态订正（取代本席草案里不成立的 `SwitchGuard(` 构造形态）**：现役是顶层函数 `fun evaluateSwitch(...)`（`core-context/.../SwitchGuard.kt:72`）、`fun shouldEvaluate(event)` / `fun shouldApply(...)`（`SettledCompressPolicy.kt:64,75`），UI 席将落 `ConversationOps.autoCompactOnSettled(...)` ⇒ **G22 钉 `= evaluateSwitch(` 赋值形态**（UI 派单已要求三处统一 `val verdict = evaluateSwitch(...)`）、**G23 钉 `.autoCompactOnSettled(` 调用形态**；都剥注释、不用裸类名（KDoc 里也会出现 ⇒ 假绿），证据串用守卫输出独有措辞、不用 G22/G23 守卫名。**提前写守卫会假红**：现役 `evaluateSwitch` 在 `feature-*` **0 命中** ⇒ 扫空集 ⇒ VACUOUS 1→3 ⇒ G21 棘轮判红；等调用点落盘、主理人通知后再按真实形态写正则 + selftest 变异自证（删调用 ⇒ 判红），预期 `OK=25→27`、`28/28→30/30`，以实跑为准。**W7-04 计数口径更新**：`core-context` 已新增 **22 条** @Test（`SwitchGuardTest` 10 + `SettledCompressPolicyTest` 12；实测该模块现 73 条、全仓严格 @Test **688**），UI 席新增数待报 ⇒ 最终 `EXPECTED_TESTS` = 666 − 8(删 8 个 SmokeTest 占位) + 22 + UI 新增数 = **680 + UI 新增数**（不是 658+）；三件同提交（删占位 / 改常量 / 全量对账且无模块 NO-SOURCE），**现仍不改 `build.yml`**。已验证：`:core-context:testDebugUnitTest :core-data:compileDebugKotlin` BUILD SUCCESSFUL in 32s / rc=0（`_w7/w7-impl-test.log`）。**W8 回填·关闭证据（Q4/N6）**：handoff `:113` 明写 W7-07 本波关闭；G22 守卫实跑 = **2 处 `val verdict = evaluateSwitch(...)` 赋值形态**（`SettingsViewModel.kt:117` save、`:292` guardSwitch）+ **1 处间接调用**（`:237` `blockedEndpointIds(...)` → `SwitchDecision.kt:85`），**不是「3 处赋值」**（审查席 F5）；G23 = 1 处。**Σcaps 三套口径订正（Q4/F3，实跑）**：`awk` 解析 `scripts/arch-guard.sh:152-193` 的 `LINE_CAPS` 之和 = **46000**（外审 43400 漏算 `core-model/300/2600` 这一项；本行「之和 2900」是 W7 当时只算 kernel+context 两模块再混加功能增量的第三套口径）⇒ **现役 Σ=46000，与 `TOTAL_LINE_CAP=42500`（`:204`）解耦，全仓总量只由 TOTAL 管**；per-module 上限之和只管各模块余量、不作总量判据。**不加 Σ≤TOTAL 自检判据**（属 H10 / W9-10 挂账，本波不动 `LINE_CAPS`） |
| W8-01 | 会话级压缩「尾部追加摘要」形态（不改写已缓存前缀） | W7 | `CARRIED` | 承接为 **W9-03**（W8 功能波改期）；W8 首波候选 | handoff-20261104-wave7.md §七；本波用户裁决 (a) 的后续——现结构 = 砍头 + `compressedUpTo` 单指针，是「压缩与缓存命中互斥」的根（`ManualCompress.kt:34-40`） |
| W8-02 | 三桶归一（独立 `cacheWrite` 字段 + 含 write 的 fixture） | W7 | `CARRIED` | 承接为 **W9-04**（W8 功能波改期）；W8（届时 `EventReducerTest` 须补含 write 的用例钉两口径分叉） | handoff §七；OpenAI 兼容族两口径恒等（`cached/prompt` ≡ Octop），见 docs/notes/w7-plan.md §4 |
| W8-03 | `EngineCapabilities` / `autoApproveDangerous` 二选一（接线或连字段带用例删） | W7 | `CARRIED` | 承接为 **W9-06**（W8 功能波改期（台账原文「不许再延期」已被用户功能波重裁 ⇒ 显式改期，见 §二-bis））；W8 首波强制二选一（承接 W6-02 / W5-05，不许再延期） | 台账 W6-02 / W5-05 原行；handoff-20261104-wave7.md §七 |
| W8-04 | 编辑当前端点「放行二次确认框」（明文 key 不进 state 红线下的替代形态） | W7 | `OPEN` | W8 评估 | handoff-20261104-wave7.md §七；本波 `save` 仅 BLOCKED 硬拦（SettingsViewModel 类头纪律），放行直存 |

> **为什么是 42 条而不是外审说的 27 条**（W7 按 §一 表格实际编号行重算）：27 = 四份 handoff 挂账表里的编号条数（G20 逐条核对的那 27 个）。
> 另 **8 条 W6 系列**：W6-01 / W6-02（本波新发现的「声明了没消费者」第 3、4 例）、W6-03（守卫空转清单）、
> W6-04（外审 §二 P2，代码里确实存在但从未进过任何挂账表，本波已由 A 流修掉）、
> W6-05（W2.5 §八.4 的无编号挂项）、W6-06（守卫自身的一次不可复现异常）、
> W6-07（`core-kernel` 行数预算到期 —— 从未进过任何挂账表，靠 G10 判红才被发现）、
> W6-08（G10 对缺尾换行静默少算 1 行）。
> 另 **7 条 W7 系列**：W7-01 / W7-02 / W7-03 —— 由 team-lead 在 W6 收口时开立（真机重放类：
> 上下文压缩 / 前缀缓存命中率 / 手动压缩与会话导入）；W7-04 / W7-05 / W7-06 是 W6 快照后补的（占位测试口径 /
> sanitize 上界 / 仅绿可见性判据）；W7-07 由 CI 席在 W7 开立（per-module 上限功能增量记账 + 待收口守卫与计数）。
> 本文件只登记不判定。27 + 8 + 7 = **42**，与 §一 表格编号行数一致。
> 其中 W6-05 / W6-07 / W6-08 三条正是「没有编号 / 没进表 ⇒ 没人跟踪」的活样本。
>
> **备查（W7，CI 席登记）**：`core-kernel/.../policy/AgentPolicy.kt:141` 的 KDoc 有一条完全限定名链接
> 指向 `com.rickeal.liquidharness.core.agent.context.hardContextBudgetTokens`；该符号已随 `context/`
> 迁入 `:core-context`。KDoc 不参与 Kotlin 编译（四条闸门全绿已证），但将来跑 Dokka 可能断链。
> 属业务源码，本波不改，仅登记备查。

---

## 二-bis、W9 承接表（W8 功能波改期裁决）

> **显式改期裁决（主理人 2026-11-06）**：W8 实际交付的是**用户功能波**（端点高级配置 / 气泡 Markdown /
> 返回 JSON 检查 / 多模型矩阵预登记），四份外审主张的「正确性 / 韧性收口」整体**改期到 W9**。
> 老号在 §一 改 `CARRIED` 并指向本表新号、**不删行**（删行 G20 判红）。这是台账 §零批判的
> 「静默顺延」的**显式版**：改期有编号、有裁决理由、有承接目标，不是「推 Wx」一句话。
> **W8-03 特别留痕**：原行写「不许再延期」，本波因用户功能波重裁 ⇒ 必须写明改期，
> 否则就是第 4 次静默顺延。**W3-05（journal）老化五波 ⇒ W9-05 必须开波，不得第 5 次顺延。**
>
> 🔴 **本车新增两行（CI 席 2026-11-06，已报主理人）**：
> `W9-12` = 裁决 D 新开挂账（思考四态 / 五档生产可达性）。
> `W9-00` = **口径登记行，非挂账**：`scripts/arch-guard.sh` G20 会**全文**扫描 `docs/handoff-*.md`
> 里的 `W<数字>-<数字>` 并逐条要求本台账有对应表格行；W9 handoff §六 的逐字文本
> 「插 W9-00 不构成 W9-05 第 5 次顺延」「Q2 → 已提前至 W9-00」等含本号 ⇒ 不补行 G20 必判红。
> 该行**不计入 §二 统计表、不代表新增挂账、不预写本波结论**（结论跑后由主理人填）。

| ID | 项 | 开立于 | 状态 | 阻塞/关闭于 | 证据 |
|---|---|---|---|---|---|
| W9-01 | 压缩量纲与切换守卫统一（4.0 尺四面 / 触发线按档位 / 单指针→尾部摘要前置） | W8 | `OPEN` | W9 开波第一件事；**W11 第 0 车补完一处，仍未整体关闭** | 复审41 C3 + w67 P0 + W8深度审查 P0-1/P0-2/P0-4/P0-5/P1-1 + NEW-A/B/C；四外审同一缺陷**禁拆多条**（附外审来源索引）。**W10 已推 `98916eaa`**（S1 量纲同源 / S2 触发线按档位 / S3 尾部摘要前置 / S4 附件进估算面），但本行当时零 diff。**W11 第 0 车补完（外审缺口 1 的最后一处）**：`core-context/.../HardBudgetShrinker.kt` 的 `promptChars()` 补 `sendableAttachments` 遍历（图片按 `ESTIMATED_IMAGE_TOKENS × CHARS_PER_TOKEN_ASCII` 同源折算、不进指纹）+ `:87` KDoc 由失实的「与 estimateMessages 一致」改为逐项同源；`HardBudgetShrinkerTest` 两条用例（删遍历 ⇒ `attachmentsAreCountedOnBothBudgetScales` 必红）。**仍未做**：`ContextCompressor.kt:50/61` 两个 `DEFAULT_*_RATIO` 搬进 `AgentPolicy`（外审报告1 §四末），故保持 `OPEN`。🔴 **标定面耦合（W11 审查席证伪本波初版判断，已修）**：初版让 `GenerationRound` 继续用含图片当量的 `promptChars` 做 chars/token 标定；provider 一张图只计 85–255 视觉 token 时，`measuredCharsPerToken = 4096/视觉token` 会被顶到 `ceiling=6.0`，而 `charCap = hardBudget × cpt` 相对 floor 1.8 **放大 3.3 倍 ⇒ 超窗方向，不是保守方向**。修法 = 新增 `HardBudgetShrinker.calibrationChars()`（只算文本/工具字符，不含图片当量）供标定；预算面与 L3 字符闸仍用 `promptChars`（含图片，宁可多算）；两者共用 `textChars`/`attachmentChars`，不是两把独立的尺子。红态用例 `HardBudgetShrinkerTest.calibrationCharsExcludeTheEstimatedImageAllowance`  **W34 行内注记（不新开行）**：W26 与 W33 两次 F6 真机实锤均为「token 维越软预算、字符维 `committedChars` 未越 50227 ⇒ `auto_compact=noop`（正确行为、非缺陷）」；W34 以 32K 档 + 大文件读取复测（结果与口径结论见 W34 验收物 §3）。按 W28 案 B，不作 `W9-01` 红态入口，仅留痕。  **W35 补录（W34 真机证据与 F6 确定性根因；仍遵 W28 案 B 不作 `W9-01` 红态入口）**：W34 F5 手动压缩首次 applied（dropped=9 kept=11、下一轮 usage 30.9k→22.9k in）；F6 自动压缩在约 50–60k 字符时刻评估、判 insufficient_savings:net=-256，手动压缩在约 124k 字符时刻按下并 dropped=9——两者是不同时刻、两个正确答案，非「同快照分裂」（harness-master 的「同快照矛盾」不成立：noop 窗口是正确行为、net=-256 是 savingChars=0 时标签语义错配）。两个确定性命题：① savingChars ≡ 0 等价于试算 outcome.compression=null（untouched 分支返 null），故 net ≡ −256 与该诊断码「收益不够」语义无关，真因是「试算压不动」，应报 nothing_to_drop 一类；② [W36 订正·命题②降级] ChatViewModel.kt:407 在 :408 onEvent(event) 之前取旧快照（少最终回答气泡且 isStreaming=true）是代码事实，但未证实为 W34 自动/手动分裂的成因；决定性变量是会话体积/试算能否压动（W34 不同时刻 reconciliation auto ~50–60k vs 手动 ~124k、W35 同一自动路径 11 秒 17:42:14 net=-256→17:42:25 applied）。手动 compressContext 不经 shouldApply、直接判 outcome.applied；两条路径 compressBubbles 与 budget 同源。W23/W26–W28/W34 全部历史自洽。处方（`.kt` 顺延，本波不动）：修 :407 调用时机、ConversationOps 用 outcome.applied 前置判、untouched 单独报 nothing_to_drop、三条必红 JVM 用例。证据：evidence/raw/w34/f5_manual_compact.json、f6_auto_compact.json。未持有真机气泡快照，不作唯一分支断言、不写成已修复。  **W37 行内注记（内容订正，状态维持 OPEN）**：命题①已落地并真机验证（`nothing_to_drop` 前置判 + `outcome.applied`；F6 跨包三态实测）；命题②（`:407` 时机）、B1 其余三条、`alreadyCompressed` 单指针（`SettledCompressPolicy.kt` 仍传 `snapshot.compression != null`）未动——原「处方（`.kt` 顺延，本波不动）」措辞已过时，以本注记为准。 |
| W9-02 | L3 工具规格字符纳入（`PreStepBudget` 字符闸不含 toolSpecChars） | W8 | `OPEN` | 矩阵工具数 >20 时激活 | 复审41 C4 / C12 例2；现役 `core-agent/loop/PreStepBudget.kt:95-98` |
| W9-03 | 会话级压缩「尾部追加摘要」（不改写已缓存前缀） | W7 | `OPEN` | 承接 W8-01；**会话级形态 W10 已落地，真机 M9 未验收 ⇒ 不关闭** | 台账 §一 W8-01 原行；`feature-chat/ManualCompress.kt:34-40` 单指针是「压缩×缓存互斥」的根。**W10/S3 实装** = 头锚 4 + 砍中段 + 尾部机械 SYSTEM 摘要（`SessionCompression`），已推 `98916eaa`；外审报告1 §四「append-only 不省体积」的批评只适用于**计划态** W9-03，对会话级实现 🔄 已过时（真删中段）。**两套压缩必须分开记**：请求级 L2 在 `core-context/LayeredContextCompressor.kt` + `core-agent/EngineBackedSummarizer.kt`，摘要请求仍是冷请求（不带父前缀、不带 tools），报告1 增量二在 L2 上 **仍 live**，另立 W9-03 子项待 W11 第 1 车。**待证**：真机 M9「尾置 SYSTEM 摘要被端点接受」（W10 审查 R6）+ `cache_invalidated` 二值过度声明（见 W11-03） |
| W9-04 | 三桶归一（独立 `cacheWrite` 字段 + 含 write 的 fixture）→ **W11 扩容为 usage 四态 + 三套厂商字段 + 分母钉死** | W7 | `CLOSED` | 关闭于 W11 第 0 车；W12 复核维持 | 台账 §一 W8-02 原行。**落地证据**：`core-model/Generation.kt` 新增 `PromptCacheStatus` 四态（`HIT/MISS/CACHE_FIELDS_NOT_REPORTED/USAGE_NOT_OBSERVED`）+ `promptCacheStatus()` + `cacheHitRatio()`（分母一次钉死 `prompt + cacheWrite`，不双计 read）+ `TokenUsage.cacheWriteTokens` 字段，原 `TODO(W6)` 已销；`core-engine/.../OpenAiCompatibleAdapter.kt` 归一 OpenAI `prompt_tokens_details.cached_tokens` / Anthropic `cache_read_input_tokens` / DeepSeek 平铺 `prompt_cache_hit_tokens` / Gemini `usageMetadata.cachedContentTokenCount` 及三处 write 字段；`GenerationEvent.Usage` / `AgentEvent.UsageUpdated` / `StreamAccumulator` / `RemoteLlmEngine` / `RunState` 全链路透传，无半迁移；`feature-chat/EventReducer.kt` 的 `buildUsageLabel` 由独立算式 `cached*100/prompt` 改调 `cacheHitRatio`。**用例**：`PromptCacheUsageTest` 9 条、`OpenAiCompatibleAdapterTest` 3 fixture（DeepSeek 平铺 / 三套 write / 末帧缺失）、`StreamAccumulatorTest` 2 条、`EventReducerTest` 含 write 的 `usageLabelKeepsCacheWriteInTheSingleDenominator`（删分母 write ⇒ 必红）。严格 @Test 以 `build.yml:132` / 实跑值为准（778→798→799 演变链），本行不写死数〔W15 订正：原写死数为失实孤例，见 `docs/analysis/w15-matrix-acceptance.md` §1.2 A2〕。⚠️ **W7-02 仍 `OPEN`**：四态只是探针，台账:99 按事实表验收、不承诺命中率，不得连带关闭 | 🔴 **W12 外审 P0-1 复核（维持 `CLOSED`）**：grep 现役树证实四态枚举唯一生产调用点是 `StreamAccumulator.cacheStatus` 且 `.cacheStatus` 在 main **零读者**；但用户可见三态（HIT/MISS/FIELDS_NOT_REPORTED）已由 `buildUsageLabel` 独立覆盖、第四态 `USAGE_NOT_OBSERVED` 在 `UsageUpdated→buildUsageLabel` 路径**结构不可达**（该事件仅在收到 `GenerationEvent.Usage` 时 emit）。故字段/透传/命中率关闭成立；**「给四态一个生产读者」拆出为 `W12-02`（OPEN）**，不阻塞本行
| W9-05 | journal / FreezeClock（**老化五波，W9 必须开波**） | W3 | `OPEN` | 承接 W3-05；**W11 仍未开波 = 第 7 次顺延，如实登记不美化** | 台账 §一 W3-05 原行；全仓零实现；5 处注释已如实改写（F1/N5）。**W11 顺延理由（主理人 2026-11-07 显式裁决，不静默）**：本波用户指令优先「跨免费模型综合功能矩阵 + 真机 + 推 CI」，第 0 车实际投入 W9-01 补完 / W9-04 扩容 / 口径回填 / id 判据修复，均已落代码与用例；journal v1 + FreezeClock + 前台服务 + 恢复卡是**独立内核硬化波**，塞进同一车会破坏单波归因且撞 `core-agent` 行数面。**硬截止**：W12 第 0 车第一件事，先开波再排任何其他功能；若 W12 再顺延，必须由用户显式裁决「永不实现」并改状态，不许继续以「推 Wx」一句话挂着。⚠️ 外审《外部研究对账与W11排期裁决》§四把「W11 必须开波」列为先还债项，本行**未满足**，不写成已完成 | 🔴 **W12 第 8 次顺延（2026-11-08，主理人显式登记）**：用户指令优先「多模型×多上下文功能矩阵 + 推 CI」；三份外审（`W11第0车深度审查与W12排期裁决.md` / `harness-w11-review.md` / `w9-w11-review-20261108.md`）**一致**要求 W12 第 0 车开波，异议如实保留。**仍未开波、未关号、不写「永不实现」**，交用户裁决下波是否强制开波
| W9-06 | `EngineCapabilities` / `autoApproveDangerous` 二选一（**因功能波显式改期**） | W7 | `OPEN` | 承接 W8-03 | 台账 §一 W8-03 原行「不许再延期」已被用户功能波重裁 ⇒ 涉 14 文件、争 `GenerationEvent.kt` + `RemoteLlmEngine.kt`（功能波必改），非 ≤30 行快修（审查席证伪） |
| W9-07 | 安全收口（W3-04 MCP token Keystore / W6-05 剪贴板开关 / R21 脱敏 / G19 脱 VACUOUS） | W8 | `OPEN` | W9 | w67 §一 T4 + §三 P2-3 + W8深度审查 P1-5；G19 现仍 VACUOUS（唯一空转项）。**W9-00 三项改号留痕（主理人 2026-11-06，终版裁决 B）**：「**F-1 → 已提前至 W9-00**」「**A-3 → 已提前至 W9-00**」「**F-3 → 已提前至 W9-00**」，三项一次写完；改号后本行剩余范围不变，按主线排期继续。依据总裁 B10 口径：两边都写 = 都以为对方做，改号不留痕即台账双挂 |
| W9-08 | 400 CONTEXT_OVERFLOW 自愈闭环（收缩重试） | W8 | `OPEN` | W9 | w67 P1「400 自愈」；`verifiedContextLength` 写回已在位，缺失败路径串联 |
| W9-09 | 断流误标 STOP（`ProviderAdapter.kt:192` `textChars==0` 无条件短路） | W8 | `OPEN` | W9 | W8深度审查 NEW-E；非流式响应喂现役解码器 ⇒ 零事件 ⇒ 静默 STOP（第二触发面，主理人实测） |
| W9-10 | 守卫与口径专项（W26-02 裁决 / Σcaps 口径 / G20 第四判据 / G24 / G10 尾换行判红 / schema 白名单守卫测试） | W8 | `OPEN` | W9 | 复审41 C7/C9/C10/C11/C12 + w67 P2 + NEW-G/H/I；每个新守卫必同步 selftest case |
| W9-11 | 设备侧 1M 内核熔断解锁（`PreStepBudget` 档位闸 27904 + `RoundHeadGate` 400k 双阻断） | W8 | `OPEN` | W9 | 主理人 W8 裁决②；1M 结论现只限 PC 侧单次请求窗口（`_w7/w7matrix.py` 纯 Python 直连）。**「Q2 → 已提前至 W9-00（总裁 2026-11-06 落锤）」**：Q2（UTF-8 BOM 剥离）原被当作本行的 1M 导入通道前置（见 `docs/analysis/w8-solution.md:857` 历史记录，不篡改），现改判 W9-00 承接，本行剩余范围不变（仍依赖 W9-01 档位口径；PC 侧 1M 结论不得写成设备侧可用）。如实登记：本行现役文本**无 Q2 字面条目可删**（Q2 从未写进 §二-bis 表格行，只在裁决/分析文档里作为前置关系），故改号以「写入改号留痕文本」落地，不虚构删除动作 |
| W9-12 | 思考四态 / 五档生产可达性（`supportsReasoning` 开关 + `reasoningStyle` 选择器 + `strings_advanced.xml` 11 条死字符串引用） | W9 | `OPEN` | W9（本车不做，见证据） | **来源**：W8 深度审查 v1.1 `NEW-W8-1` / P0-a。**开立理由（裁决 D，必须写清）**：终版裁决 §三 W9-00 出口判据的「4 个 P0」指《W8源码逐行审查与达标核对》§三 的 **P0-1..P0-4，不含此项**；且 `feature-settings` 现 3482/3500 余 18 行、**无行数** ⇒ 本车不做。现役事实：三组预置 `supportsReasoning` 全 false + 无 style 选择器 + UI `enabled=initial.supportsReasoning` 只读（`core-data/EndpointStore.kt:118/130/142`、`feature-settings/EndpointAdvancedConfig.kt:67`；行号随 W9-00 施工漂移，以符号为准）⇒ 思考四态 / 五档**生产不可达**。本车真机由主理人通过**设备侧 `endpoints.json` 注入 `reasoningStyle`** 做**端点可达性**取证；🔴 **UI 不可达的事实须如实登记，不得写「思考四态已验收」**（端点可达 ≠ UI 可达 ≠ 四态验收） |
| W9-00 | 本波口径登记行（序 0 抬头 + 序 1 交接对账与防线接线，插入半波）；**非挂账项、不计入 §二 统计表，仅为满足 G20 的 ID 存在性** | W9 | `OPEN` | 本车收口时由主理人判读，结论跑后填 | **本波口径登记行，非挂账项、不计入 §二 统计表，仅为满足 G20 的 ID 存在性**（主理人裁决 4）：`scripts/arch-guard.sh` G20 全文扫描 `docs/handoff-*.md` 的 `W<数字>-<数字>`，W9 handoff §六 逐字文本含本号 ⇒ 补本行避免判红。见 `docs/handoff-20261106-wave9.md` §五.4 / §六；本行**不改任何老号、不转移编号、不预写结论、状态 `OPEN`、证据列非空** |

> **编号唯一来源仍是 §一 表格行**（§零）。本表是 §一 的**承接索引**：W9-03 / W9-04 / W9-05 / W9-06
> 的老号（W8-01 / W8-02 / W3-05 / W8-03）在 §一 已改 `CARRIED` 并指向本表新号。
> **禁用 `W8-05`**（路线草稿已占用）。

## 二-ter、W11 承接表（W11 第 0 车开立，2026-11-07）

> 与 §二-bis 同口径：**本表行不计入 §二 统计表**（统计表只数 §一 表格行）。
> G20 只校验「handoff 里出现的 `W<数字>-<数字>` 在本台账有行 / `CLOSED` 行证据非空 /
> 状态值合法」，所以 W10 审查席顺延项与 W10 新观察项**必须先在此落号**，再写进 W11 交接文档。
> 编号唯一来源仍是 §一 表格行（§零）；本表是承接索引，不是第二真值源。
> ⚠️ **G20 实测口径（`scripts/arch-guard.sh:1342`）**：它扫的是台账**全文**所有以 `|` 开头、
> 第二列匹配 `W[0-9]+-[0-9]+` 的行 ⇒ §二-ter 的行同样受「ID 存在性 / CLOSED 证据非空 /
> 状态值合法」三项保护（W11 实跑 G20 OK 已证）。但**派单与状态判读**仍按 §零 只认 §一 表格行，
> 本表只是承接索引 —— 纪律比守卫更严，不是冲突。

| ID | 项 | 开立于 | 状态 | 阻塞/关闭于 | 证据 |
|---|---|---|---|---|---|
| W11-01 | R2 分隔卡固定置顶、与「头锚 + 尾部」压缩视图边界错位（W10 审查席 R2 顺延） | W11 | `OPEN` | 先拆 `feature-chat/ChatMessageList` 子组件再移卡；G11 方法上限 120、`feature-chat` 余 27 ⇒ **触顶先下沉，禁调 `LINE_CAPS`** | `docs/handoff-20261107-wave10.md` §四 R2；实验期曾把 G10 推到 3922>3900 后回退。属 UX 项，非 vacuous |
| W11-02 | `ChatHistoryMapper.toHistoryMessages()` 每次重建用 `newId()` ⇒ 本地前缀指纹跨重建假漂移（W10 §四 新观察项） | W11 | `CLOSED` | 关闭于 W11 第 0 车 | **回源判第三份**（两份外审矛盾：开发席 R1 主张给 history 派生稳定 id、审查席 R3/R4 主张指纹去 id）：`core-engine/.../OpenAiCompatibleAdapter.messageObject()` **不写 `message.id`**（只写 role / content / `tool_calls[].id`）⇒ 假漂移的根是「判据含了不进 wire 的字段」，派生稳定 id 属无收益改动。修法 = `core-context/.../PromptPrefix.kt` 的 `renderHistoryHead()` 去掉 `message.id` 段、KDoc 订正；`PromptPrefixTest` 两条互证用例：`historyHeadIgnoresMessageIdThatNeverReachesTheWire`（旧实现必红）+ `historyHeadStillTracksToolCallIdsThatReachTheWire`（call.id 仍进 wire ⇒ 判据仍有灵敏度）。设备侧 `cached_tokens` 是否受其它字段影响仍待真机 M8 |
| W11-03 | `cache_invalidated` 二值过度声明：S3 砍中段后 `ConversationOps.kt:126,186` 落 `false`、`undoCompress():205` 仍落 `true`，且 `SettledCompressPolicy.kt:14-18` 预写「头锚段仍 > 0」 | W11 | `OPEN` | W12 与 W9-03 真机 M9/M8 同车；需改成 `partial / head_anchor_preserved` + 断点位置归因 | `外部研究对账与W11排期裁决.md` §二.1；`docs/handoff-20261107-wave10.md` §四 R6（尾置 SYSTEM 是否被端点接受未验）。**未取真机证据的预写结论不得升级为事实** |
| W11-04 | `jsonMode` 与 `tools` 互斥未做（W8 `JSON-1` 连续两波未核销） | W11 | `OPEN` | W12；修法须先定「工具循环中遇到 `response_format: json_object` 时谁让路」，并补红态用例 | **外审勘误已采纳**：审查席初报「仅 `OpenAiCompatibleAdapterTest:534` 一处」被自己的全量复核证伪 —— 现役是 **`jsonMode` 11 处字样 / 10 行**，集中在 `core-engine/.../OpenAiCompatibleAdapter.kt`（`buildBody` 顶部的两个并列 `if` 分支、KDoc、测试构造点等）。结论未变但更硬：两个分支并列 ⇒ 同时带 tools + jsonMode 的请求体会同时写 `tools` 与 `response_format`，部分端点会 400。本波未改（不塞进单波） |
| W11-05 | `cacheHitRatio` 分母按 provider 族归一 | W11 | `OPEN` | W12 与 W9-04 后续同车；需 adapter 透传「命中量是否已计入 prompt」的能力位 | W9-04 现口径 `cached/(prompt+cacheWrite)` 对 OpenAI 兼容族成立（prompt 已含 read），但 DeepSeek 平铺族 / Anthropic 的 `prompt_tokens` 可能**不含**命中量 ⇒ 原始比值 > 1。本波用 `coerceIn(0.0,1.0)` 作**显示上界**并写 `hitRatioIsClampedWhenProviderPromptExcludesTheRead` 钉住；🔴 KDoc 已明写「1.0 不是 100% 命中事实声明」。审查席另建议「钳制到 1.0 仍会与未上报同形」，本波未采纳为代码，登记在此 |
| W12-01 | 带图轮次跳过 chars/token 标定（外审 P1-2）：`calibrationChars` 只算文本作分子、含视觉 token 的 `prompt_tokens` 作分母 ⇒ 带图轮次 `measuredCharsPerToken` 偏低 ⇒ 下一轮纯文本 `charCap` 偏小过度收缩且跨轮继承 | W12 | `CLOSED` | 关闭于 W12 | `core-agent/.../CalibrationPolicy.kt` 新增 `calibrationCharsForRound`：带图轮次返回 0（`recordReportedPrompt` 只在 `charsSent>0` 时覆盖 cpt）⇒ 沿用上一轮纯文本标定、首轮即带图走默认上界（安全侧）；`GenerationRound` 改调并移除唯一 `HardBudgetShrinker` import；红态用例 `imageBearingRoundSkipsCharsPerTokenCalibration`；`EXPECTED_TESTS` 798→799 同提交一次改。本地闸门 G1–G4 全绿；CI run `42445368887` 五 job 全 success |
| W12-02 | usage 四态 `PromptCacheStatus` 缺生产读者：唯一生产调用点 `StreamAccumulator.cacheStatus` 在 main 零读者（外审 P0-1 剩余面） | W12 | `OPEN` | 给四态一个生产读者 —— 探针事实表 / 诊断日志 / 或 `scripts/` 设备侧缓存探针按四态落盘；与 `W9-07` 探针波或 `W11-05` 分母族归一同车 | W12 外审 `W11第0车深度审查与W12排期裁决.md` §三.2 P0-1；W12 回树 grep 证实（`promptCacheStatus(` 排除 test 仅 `StreamAccumulator.kt:226` 一处，`.cacheStatus` main 零命中）。W12 真机 F7 实测 `buildUsageLabel` 三态正确（首轮 `0 cached, 0.0%`=MISS、二轮 `1664 cached, 96.5%`=HIT），用户可见面未缺口  **W37 行内注记（内容订正，状态维持 OPEN）**：`EventReducer.buildUsageLabel` 已成为 `promptCacheStatus` 四态生产读者（HIT/MISS→命中量+率、`CACHE_FIELDS_NOT_REPORTED`→`(cache n/a)`、`USAGE_NOT_OBSERVED`→空标签），真机 E01 验证输出兼容；`ContextBudget.cachedLabel` 未接、`usageObserved` 口径与 `StreamAccumulator.kt:226` 未归一 ⇒ 整行不销。 |

## 二-quater、W14 挂账表（2026-11-09，矩阵波）

> 与 §二-bis / §二-ter 同口径：**本表行不计入 §二 统计表**（统计表只数 §一 表格行）。
> 本波全部为 `OPEN`；状态只允许 `OPEN` / `CARRIED` / `CLOSED`（G20）。
> **本表每行自带「触发信号 + 缓解动作」**，避免「推 Wx」一句话式静默顺延。
> 唯一真值源仍是 §一 表格行；本表是 W14 的承接索引，不是第二真值源。
> 依据：用户 2026-11-09 指令（多免费模型 × 多上下文矩阵 + 推 CI）与外审
> 《W13隐私加固波深度审查与W14排期裁决》§五 的裁决分歧（收敛结论见
> `docs/analysis/w14-matrix-acceptance.md` §1.2）。

| ID | 项 | 开立于 | 状态 | 阻塞/关闭于 | 证据 |
|---|---|---|---|---|---|
| W14-01 | 外审第 0 车整包未做：W13 隐私波入账、五条漏号补登、账本三处订正、handoff 生成 checklist 三硬规则 | W14 | `OPEN` | **触发信号**：外部审查再次判「本波无 handoff / 无台账行 / 账实不符」；**缓解动作**：先补登历史漏号与订正，再开新面，且每条带必红变异样本 | `docs/analysis/w14-matrix-acceptance.md` §1.2 / §1.4；本波已产 `handoff-20261109-wave14.md` 与本表，但**未补历史漏号**，如实登记 |
| W14-02 | 外审第 1 车安全波收口：消除重复扫描 job、安装脚本 pin 与哈希、allowlist 精确放行、全历史首跑取证、签名端到端 | W14 | `OPEN` | **触发信号**：扫描口径出现两处真值源漂移，或首次 `workflow_dispatch` 全历史扫描结论未取；**缓解动作**：抽 composite action / reusable workflow 两处调用，并补「真格式 key 仍判红」变异用例 | 外审 §五 第 1 车（P1-1 / P1-3 / P1-4 / P1-5 / P1-6）；本波不动 CI 配置 |
| W14-03 | 外审第 2 车：拆分三方、四态生产读者、分母族归一、jsonMode × tools | W14 | `OPEN` | **触发信号**：`core-context` 余量见底或新增 `.kt` 触碰 G10；**缓解动作**：先拆再改，禁调 `LINE_CAPS` / `TOTAL_LINE_CAP` / `VACUOUS_BASELINE` | 外审 §五 第 2 车；本波零 `.kt` 改动 |
| W14-04 | `W9-05` journal / FreezeClock **第 9 次顺延**，用户本单未给「永不实现 / 改状态」显式裁决 | W14 | `OPEN` | **触发信号**：下一波仍以功能矩阵优先，或用户显式裁决；**缓解动作**：要么第 0 车强制开波，要么用户显式改状态列并同步顺延代数；**不关号、不软化、不代裁决** | `docs/analysis/w14-matrix-acceptance.md` §1.2(4)；§一 `W9-05` 行仍写第 8 次，本波如实登记第 9 次但**不改老行代数**（改老行需单独裁决） |
| W14-05 | 设备侧 1M 真压 ⛔ | W14 | `OPEN` | **触发信号**：`W9-11` 的 `PreStepBudget` 档位闸与 `RoundHeadGate` 双阻断被解除；**缓解动作**：解除后先用导入会话通道灌阶梯，PC 侧结论不得写成设备侧可用 | 本波真机：32K 档 `上下文 1.6K / 27.9K`、512K 档 `507.9K`，长会话 249752 字符被 `cap=50227` 前置熔断；外审 §五 同判「如实 ⛔」 |
| W14-06 | MCP 明文联调 ⛔（平台阻断） | W14 | `OPEN` | **触发信号**：出现 https 夹具或 cleartext 例外的安全裁决；**缓解动作**：单独出裁决文档，不为测试放开 `usesCleartextTraffic` | §一 `W5-03`；`app/src/main/AndroidManifest.xml:25` `usesCleartextTraffic="false"`（G15b 本波仍 OK） |
| W14-07 | git 历史清理（`filter-repo` / BFG / 强推）不可逆动作 | W14 | `OPEN` | **触发信号**：用户二选一裁决「清历史 + 凭据轮换」；**缓解动作**：裁决前维持「历史留存」现状并留痕；本单禁止 force-push / rewrite history | 外审 §三.3 P1-5；本波不执行 |
| W14-08 | 音频输出、`core-device` 独立波、图片预算四条、为凑缓存加长 system | W14 | `OPEN` | **触发信号**：用户显式改变「明确不做」清单；**缓解动作**：维持既有方向，图片算进预算但不进 `PromptPrefix` 指纹 | 外审 §五 末「明确不做」；`docs/analysis/w14-matrix-acceptance.md` §1.2(2) |
| W14-09 | PC 侧免费节点可用性结论不得升级为「设备侧可用」 | W14 | `OPEN` | **触发信号**：出现把 PC 侧单次请求窗口结论写进交接正文的措辞；**缓解动作**：四态写 `⛔`，只登记 PC 侧事实表 | `AGENTS.md` §二.3「未触发 ≠ 已验证通过」；W12 交接 §一 已立此纪律 |
| W14-10 | **本波新观察**：长会话导入后 L3 字符硬闸前置熔断（`context_overflow_prevented`），`auto_compact=noop reason=below_trigger` 未介入；与 `W9-08` 的 400 自愈是**不同触发面** | W14 | `OPEN` | **触发信号**：需要「超窗请求由压缩链路救回」的验收；**缓解动作**：先判「字符闸 vs HTTP 400」两条路径再设计自愈，不把两者合并成一条挂账 | `<取证目录>/evidence/matrix_facts.jsonl` C05；`compress_facts.jsonl`；UI 红卡 `chars=249752 cap=50227`；`docs/analysis/w14-matrix-acceptance.md` §3 |
| W14-11 | **功能矩阵未取全项**：多模态真机输入（两次尝试未通过）、`skill_load` 未行使、`file_write` 落盘未证实、自动压缩真触发未取、`W7-03` 分隔卡未取 | W14 | `OPEN` | **触发信号**：需要「多模态 / skill / 文件落盘 / 自动压缩」任一项重新验收；**缓解动作**：先把「判据在应用内」作为前置断言，再按工具链逐项取双向证据；不引用 W12 结论冒充本波通过 | `<取证目录>/evidence/vision_run.log` / `vision_run2.log` / `matrix_facts.jsonl` F1–F3；`docs/analysis/w14-matrix-acceptance.md` §3.6 / §3.7 |

### 二-quinquies（W15 承接索引）

> **本表每行自带「触发信号 + 缓解动作」**，避免「推 Wx」一句话式静默顺延。
> 唯一真值源仍是 §一 表格行；本表是 W15 的承接索引，不是第二真值源。
> 依据：用户 2026-11-09 指令（多免费模型 × 多上下文矩阵 + 推 CI、读四份外审并裁决分歧、
> 不改方案大体方向）与方案席 Phase 1 只读报告；收敛结论见
> `docs/analysis/w15-matrix-acceptance.md` §1.2。
> ⚠️ 老号 `W14-01..11` 全数保留 `OPEN`，本表不删、不改状态、不写「已核销」。
> **审计行**：`W14-01` 的触发信号「外部审查再次判账实不符」在四份 W15 外审中**已命中**；
> 本波执行的最低缓解 = **五处数字订正 + 五处分歧裁决留痕 + W15 派生件自带五列**，
> 属**部分缓解、未核销**；逐条落号仍由 `W15-01` 顺延。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W15-01 | 外审第 0 车逐条落号：`W13-01` 入账、`W11-06..10` 五条漏号补登、handoff checklist 三硬规则 | W15 | `OPEN` | **触发信号**：外部审查再次判账实不符（四份 W15 外审已命中）；**缓解动作**：本波只做「数字订正 + 裁决留痕」的最低缓解，逐条落号须回现役树核 + 必红变异样本，独立成波 | `docs/analysis/w15-matrix-acceptance.md` §1.2 / §5.5；承接 `W14-01`，**未核销** |
| W15-02 | 安全波收口：消重复扫描 job / 安装脚本 pin + sha256 / allowlist 精确化 / 全历史首跑取证 / 签名端到端 | W15 | `OPEN` | **触发信号**：扫描口径出现两处真值源漂移；**缓解动作**：独立成波改 CI 配置，不塞进矩阵波 | 外审《W14矩阵波深度审查与W15排期裁决》§五 第 1 车；承接 `W14-02` |
| W15-03 | 拆分三方 / 四态生产读者 / 分母族归一 / jsonMode × tools | W15 | `OPEN` | **触发信号**：`core-context` 余量见底或新增 `.kt` 触碰 G10；**缓解动作**：先拆再改，禁调 `LINE_CAPS` / `TOTAL_LINE_CAP` / `VACUOUS_BASELINE` | 外审 §五 第 2 车；承接 `W14-03`，本波零 `.kt` |
| W15-04 | `W9-05` journal / FreezeClock **第 10 次顺延**（按 `W14-04` 现行代数 9+1；W13 静默顺延未计入，逐波重数口径为 11，两口径并列待裁决） | W15 | `OPEN` | **触发信号**：下一波仍以功能矩阵优先，或用户显式裁决；**缓解动作**：第 0 车强制开波，或用户显式改状态列并统一顺延代数口径；**不关号、不软化、不代裁决** | `docs/analysis/w15-matrix-acceptance.md` §1.2 A3；§一 老行 7 / 证据列 8 未改（改老行需单独裁决） |
| W15-05 | 设备侧 1M 真压 | W15 | `OPEN` | **触发信号**：`W9-11` 双阻断解除；**缓解动作**：用导入会话灌阶梯；档位可选 ≠ 真压，PC 侧结论不写成设备侧可用 | 承接 `W14-05`；本波只读到分母读数 |
| W15-06 | MCP 明文联调 / 放开 `usesCleartextTraffic` | W15 | `OPEN` | **触发信号**：出现 https 夹具或 cleartext 例外的安全裁决；**缓解动作**：单独出裁决文档，不为测试放开 | `app/src/main/AndroidManifest.xml:25` + G15b；承接 `W14-06` |
| W15-07 | git 历史清理 / force-push / rewrite history | W15 | `OPEN` | **触发信号**：用户二选一裁决「清历史 + 凭据轮换」；**缓解动作**：裁决前维持历史留存并留痕 | 本单明禁不可逆动作；承接 `W14-07` |
| W15-08 | 音频输出 / `core-device` 独立波 / 图片预算四条 / 为凑缓存加长 system | W15 | `OPEN` | **触发信号**：用户显式改变「明确不做」清单；**缓解动作**：维持既有方向，图片算进预算但不进 `PromptPrefix` 指纹 | 承接 `W14-08` |
| W15-09 | 把 PC 侧可用性结论升级为「设备侧可用」 | W15 | `OPEN` | **触发信号**：出现把 PC 侧单次请求窗口结论写进交接正文；**缓解动作**：四态写 ⛔，只登记 PC 侧事实表 | `AGENTS.md` §二.3；承接 `W14-09` |
| W15-10 | `W14-10` 合并设计：前置熔断路径的压缩试算入口 + `ChatViewModel.kt:395` 结算时机 + `W9-01` 触发线量纲 | W15 | `OPEN` | **触发信号**：需要「超窗请求由压缩链路救回」的验收；**缓解动作**：先证自动压缩在前置熔断路径**结构不可达**（非「触发面差异」），再合并设计；需改 `.kt` | 外审 P1-1 逐符号核；承接 `W14-10`，本波只留裁决痕 |
| W15-11 | 本波仍未取全的功能项不得冒充「已验收」 | W15 | `OPEN` | **触发信号**：任一功能项判据未行使；**缓解动作**：四态如实 ⛔ / ⚠️，不引用 W12 结论冒充本波通过 | 承接 `W14-11`，**不写 `W14-11` 已核销** |
| W15-12 | 改 `.kt` / 新增 arch-guard 守卫 / 动 `EXPECTED_TESTS` / 调 `LINE_CAPS` · `TOTAL_LINE_CAP` · `VACUOUS_BASELINE` | W15 | `OPEN` | **触发信号**：单波归因被打破或 G10 触顶；**缓解动作**：先拆再改；G21 棘轮不允许空集守卫判红 | 本波预期零 `.kt` 改动、零新增守卫 |
| W15-13 | 代做四项用户裁决（`W9-05` / 「≥3 端点」二选一 / §1.5 占位符口径 / `P2-3` 二选一） | W15 | `OPEN` | **触发信号**：用户或总裁显式裁决落账；**缓解动作**：本波只留痕；执行「补跑同组交叉格」属执行不是裁决 | `docs/analysis/w15-matrix-acceptance.md` §1.2 / §1.5 |
| W15-14 | 文档回写差异留痕：W14 验收物重复 `### 5.5` 标题与 §4.3 占位段未删 | W15 | `OPEN` | **触发信号**：外部审查再次判「收尾契约有未收敛占位」；**缓解动作**：历史制品「回填不篡改」保留，删改须单独裁决 | `docs/analysis/w14-matrix-acceptance.md` §4.3 / §5.5；本波 §5.5 记差异 |

### 二-sexies（W16 承接索引）

> **本表每行自带「触发信号 + 缓解动作」**，避免「推 Wx」一句话式静默顺延。
> 唯一真值源仍是 §一 表格行；本表是 W16 的承接索引，不是第二真值源，**不计入 §二 统计表**（同 W15 口径）。
> 依据：用户 2026-11-11 指令（多免费模型 × 多上下文直至 1M + 全功能面 + 手动/自动压缩 + 本地/真机 + 推 CI、读四份外审并裁决分歧、不改方案大体方向）与方案席 Phase 1 只读报告；收敛结论见 `docs/analysis/w16-matrix-acceptance.md` §1.2。
> ⚠️ 老号 `W14-01..11` / `W15-01..14` / `W9-05` 全数保留 `OPEN`，本表不删、不改状态、不写「已核销」、不重判 `CLOSED`。
> 🔴 **非承接硬约束**：`W16-XX` 是**本波范围声明**（告诉外部审查「本波没做什么」），不是老号承接；不得写成 `CARRIED` / 承接 / 替代；**不得**用 `W16-` 号登记 `W13-01` / `W11-06..10` 六行漏号（漏号若执行须补原号）。
> **审计行**：`W14-01`「外部审查再次判账实不符」与 `W15-01` 的触发信号在四份 W16 外审中**第三次命中**；本波执行的最低缓解 = **裁决分歧留痕 + 派生件六列 + G1 重取 APK 字节销疑 + 回源证伪 1 条外审处方**，属**部分缓解、未核销**；逐条落号仍由 `W16-01` 顺延。
> 落笔纪律：本表由主理人**单人**落笔（本波无并行流起号），符合 §三.6 防重复编号的实质。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W16-01 | 外审第 0 车逐条落号：`W13-01` + `W11-06..10` 六行补登、checklist 三硬规则、G20 扫描面盲区、W15 派生件收口、文档零头 | W16 | `OPEN` | **触发信号**：外部审查再次判账实不符（四份 W16 外审已命中）；**缓解动作**：须回现役树核 + 每个新守卫带必红变异样本 + 先批量补历史悬空行再改守卫；改真值源结构 / 新增守卫 ⇒ 独立成波，**未核销** | `docs/analysis/w16-matrix-acceptance.md` §1.2(c) / §1.4 / §4.3；参照 `W15-01` / `W14-01`；**W16 首推 Build 判红归因**：CI `G20` 查出 wave16 handoff 引用了本两处跳号（守卫行为正确，非误报），已修 handoff 措辞不再写成 ID 字面量，**补登本两处跳号仍留本号待办、待独立成波**；顺带部分证伪外审「G20 查不出漏号」论断 |
| W16-02 | 安全波收口：消重复扫描 job / 安装脚本 pin + sha256 / allowlist 精确化 + 真 key 仍判红变异 / 全历史首跑 / 签名端到端 | W16 | `OPEN` | **触发信号**：扫描口径出现两处真值源漂移或 allowlist 文件数 ≥3；**缓解动作**：独立成波改 CI 配置，不塞进矩阵波 | 外审《W11-W15深度审查》§四 / §五；参照 `W15-02` / `W14-02` |
| W16-03 | 拆分三方（`core-context` 余 4 / `feature-chat` 余 12 / `feature-settings` 余 8）、四态生产读者、分母族归一、jsonMode × tools、五条 ≤30 行快修 | W16 | `OPEN` | **触发信号**：`core-context` 余量见底或新增 `.kt` 触碰 G10；**缓解动作**：先拆再改，禁调 `LINE_CAPS` / `TOTAL_LINE_CAP` / `VACUOUS_BASELINE` | 外审 §五 第 2 车；参照 `W15-03` / `W14-03`，本波零 `.kt` **2026-11-11 用户显式裁决留痕**：对话框 `/` 指令与 `@` 引用手动触发 skill 功能净增约 47 行 ⇒ `feature-chat` 触 G10，**临时调高上限 3900→4000**（违反「禁调 LINE_CAPS」铁律，用户裁决覆盖，TOTAL_LINE_CAP 42500 不动、不构成先例，见 handoff §9.2）；下沉窗口被挤占 ⇒ 本条更紧迫，下波触顶必须先拆（拆 ChatMessageList/ChatBubble），不得再调上限。 |
| W16-04 | `W9-05` journal / FreezeClock **第 11 次顺延**（按 `W15-04` 现行代数 10+1；W13 静默顺延未计入，逐波重数口径为 12，两口径并列待裁决） | W16 | `OPEN` | **触发信号**：下一波仍以功能矩阵优先，或用户显式裁决；**缓解动作**：强制开波，或用户显式改状态列并统一 7/8/9/10(11)/12 代数口径；**不关号、不软化、不代裁决** | `docs/analysis/w16-matrix-acceptance.md` §1.2(3) / §1.5；§一 老行 7 / 证据列 8 未改（改老行需单独裁决） |
| W16-05 | 设备侧 1M 真压 | W16 | `OPEN` | **触发信号**：`W9-11` 双阻断解除（L3 字符闸 + `RoundHeadGate 400k`）；**缓解动作**：用导入会话灌阶梯；档位读数 ≠ 真压，PC 侧结论不写成设备侧可用 | 参照 `W15-05` / `W14-05`；本波只取 1M **读数** |
| W16-06 | MCP 明文联调 / 放开 `usesCleartextTraffic` | W16 | `OPEN` | **触发信号**：出现 https 夹具或 cleartext 例外的安全裁决；**缓解动作**：单独出裁决文档，不为测试放开 | `app/src/main/AndroidManifest.xml:25` + G15b 仍 OK；参照 `W15-06` / `W14-06` / `W5-03` |
| W16-07 | git 历史清理 / force-push / rewrite history | W16 | `OPEN` | **触发信号**：用户二选一裁决「清历史 + 凭据轮换」；**缓解动作**：裁决前维持历史留存并留痕 | 本单明禁不可逆动作；参照 `W15-07` / `W14-07` |
| W16-08 | 音频输出 / `core-device` 独立波 / 图片预算四条 / 为凑缓存加长 system | W16 | `OPEN` | **触发信号**：用户显式改变「明确不做」清单；**缓解动作**：维持既有方向，图片算进预算但不进 `PromptPrefix` 指纹 | 参照 `W15-08` / `W14-08` |
| W16-09 | 把 PC 侧可用性结论升级为「设备侧可用」 | W16 | `OPEN` | **触发信号**：出现把 PC 侧单次请求窗口结论写进交接正文；**缓解动作**：四态写 ⛔，只登记 PC 侧事实表 | `AGENTS.md` §二.3；参照 `W15-09` / `W14-09` |
| W16-10 | `W15-10` / `W14-10` 合并设计：前置熔断路径压缩试算入口 + `ChatViewModel.kt:395` 结算时机 + `W9-01` 触发线量纲 | W16 | `OPEN` | **触发信号**：需要「超窗请求由压缩链路救回」的验收；**缓解动作**：先证自动压缩在该路径**结构不可达**（非触发面差异），再合并设计；需改 `.kt` | 外审 P1-1 逐符号核 + W15 派生件 `structural_unreachable=true`；参照 `W15-10`，本波只留裁决痕 |
| W16-11 | 本波仍未取全的功能项不得冒充「已验收」 | W16 | `OPEN` | **触发信号**：任一功能项判据未行使（多模态 / 手动压缩 / MCP / 自动压缩）；**缓解动作**：四态如实 ⛔ / ⚠️ / ⛔vacuous，`both_states` 留痕，不引用 W12 / W14 / W15 结论冒充本波通过 | 参照 `W15-11` / `W14-11`，**不写老号已核销** |
| W16-12 | 改 `.kt` / 新增 arch-guard 守卫 / 动 `EXPECTED_TESTS` / 调 `LINE_CAPS` · `TOTAL_LINE_CAP` · `VACUOUS_BASELINE` / 改 CI 配置 | W16 | `OPEN` | **触发信号**：单波归因被打破或 G10 触顶；**缓解动作**：先拆再改；G21 棘轮不允许空集守卫判红 | 本波预期零 `.kt` 改动、零新增守卫、零 CI 配置改动 |
| W16-13 | 代做四项用户裁决（`W9-05` / 「≥3 端点」二选一 / §1.5 占位符口径 / `P2-3` 二选一） | W16 | `OPEN` | **触发信号**：用户或总裁显式裁决落账；**缓解动作**：本波只留痕；执行多档位矩阵属执行不是裁决 | `docs/analysis/w16-matrix-acceptance.md` §1.2(4) / §1.5 |
| W16-14 | 文档回写差异留痕：W14 验收物重复 `### 5.5` 标题与 §4.3 占位段、§5.1 步 3 过度声明、W15 §5.5「将包含」未来时 | W16 | `OPEN` | **触发信号**：外部审查再次判「收尾契约有未收敛占位」；**缓解动作**：历史制品「回填不篡改」保留并记差异；删改须单独裁决 | `docs/analysis/w15-matrix-acceptance.md` §5.5；本波 §5.5 记差异 |
| W16-15 | **本波新登记**：不重犯 W15 派生件硬伤（`cell` 唯一 / `safe_shot` 唯一 / 补 `window_tokens` 数值列 / 1M 双口径注释 / `memory.backward.snapshot_before` / `tail_is_fresh` 升级为本批新事件） | W16 | `OPEN` | **触发信号**：派生件生成时唯一性断言或列齐备校验失败；**缓解动作**：`cell` 与 `safe_shot` 唯一性断言 + 六列出厂校验 + 复用旧尾标 `stale=true`；本波**执行**（不是顺延） | `docs/analysis/w16-matrix-acceptance.md` §1.1 / §1.3 A3；本波派生件自检结论见 §3 |
| W16-16 | **本波新登记**：不以 `W16-XX` 承接任何老号或漏号；漏号若执行须补原号；老号全 `OPEN` 原样保留 | W16 | `OPEN` | **触发信号**：出现「用新号承接旧债」的台账行或交接措辞；**缓解动作**：逐行标注「参照老号、老号 `OPEN` 原样、非承接」；G20 只校验 handoff ID 存在性，换号不增加判据灵敏度 | 外审《W15最终态复核》§2.4 台账膨胀；`docs/analysis/w16-matrix-acceptance.md` §1.4 三条硬约束 |
| W16-17 | **本波新登记**：不把「1M 读数」写成「1M 真压」、不把档位读数驱动失败归因成应用代码 bug、不把「对话框 skill 引用手动触发」简写成 W15 的 `skill_load` 工具调用 | W16 | `OPEN` | **触发信号**：交接 / 事实表出现口径混写；**缓解动作**：按四分类（参数错 / 工具名错 / 该调不调 / 调了不收敛）先排除驱动与环境、后应用；读数与真压分开判四态 | `docs/analysis/w16-matrix-acceptance.md` §1.3 A4 / §1.4；用户本单逐字要点 |
| W16-18 | **W16 后补充重测新登记**：导入会话（调试）只真正加载约 1 条 —— 300 条 / 257 kB 夹具经 SAF 导入后，下一请求仅 `1.6k in`，致手动压缩无可压中段（`dropped=0`）；本波未解 | W16 | `OPEN` | **触发信号**：手动压缩 `dropped=0` 且下一请求 `in` 远小于夹具字节；**缓解动作**：单独排查导入链路（SAF 选文件后是否缺确认步 / JSONL 解析 / 会话加载到对话页），须独立成波；**属导入链路疑点，非压缩功能缺陷** | `docs/analysis/w16-matrix-acceptance.md` §5.7；**W17 定谳（✅ 闭环）**：解析器加 `droppedBlank` 计数 + JVM 用例钉 `content` 字段名行；W16 同款夹具（`text` 字段、300 条、257 kB）重导 ⇒ **气泡滚至 `Segment 0299`（300 条全物化）+ 发送触发 `context_overflow_prevented: chars=249801 cap=50227`（32K 档字符闸，300 条全进请求）** ⇒ 上波「只载 1 条」未复现，判**上波取证驱动/流程伪影**（非应用缺陷、非夹具格式） |

### 二-septies（W17 承接索引）

> **本表每行自带「触发信号 + 缓解动作」**。唯一真值源仍是 §一 表格行；本表是 W17 的承接索引，
> 不是第二真值源，**不计入 §二 统计表**（同 W15/W16 口径）。
> 依据：用户 2026-11-12 指令（SkillMention 收口批 + W16-18 定谳 + 多模型×多档位功能矩阵 + 本地/真机 + 推 CI、
> 读众审查人报告并裁决分歧、不改方案大体方向）与 `docs/analysis/w17-acceptance.md` §1。
> ⚠️ 老号 `W14-01..11` / `W15-01..14` / `W16-01..18` / `W9-05` 全数保留 `OPEN`，本表不删、不改状态、
> 不写「已核销」、不重判 `CLOSED`；`W17-XX` 是**本波范围声明**，不是老号承接。
> 落笔纪律：台账由主理人**单人**落笔（本波无并行流起号）。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W17-01 | `HB-2`：`HardBudgetShrinker.imageChars` KDoc 表述绕（两方向半句说反）重写 | W17 | `OPEN` | **触发信号**：W16-03 拆分 `core-context`（现 1646/1650 余 4 全仓最紧）落地后首个触点；**缓解动作**：拆分后顺手重写一句「图片在字符闸记 16384 字符（=1024 token × 4.0 估算尺），在标定面记 0」；本波不改 core-context 任何行（纯注释改进不值得冒 G10 风险） | `harness-w17-deep.md` §3.3；`core-context/.../HardBudgetShrinker.kt` KDoc |
| W17-02 | `SM-3`：`skillNames` 进程内只载一次，运行时导入/删除 skill 后下拉不刷新（W31 行内注记：W30 §3.3 新面——@ 菜单 8 项上限〔4 初始 + 4 滚动、第 9 项截断〕、ChatViewModel 进程内只载一次 skillNames 不重建、SkillCatalogImpl refresh 只清 catalog 缓存、drv.text() 吞 @ 后续字符；重启进程才进菜单，本行覆盖面由「导入/删除后下拉不刷新」扩至「菜单上限 + 进程内不重建」；修法需 .kt ⇒ 挂 W20-02 拆分波后） | W17 | `CLOSED` | **触发信号**：W3-02 Skill 导入（SAF→zip）落地时；**缓解动作**：导入成功后刷新 `_skillNames` 或改 `flatMapLatest` 目录流；当前导入未实现故无症状，登记防漏（外审 SM-3/N5/P3-3） | `feature-chat/ChatSkillInput.kt`（loader）；W3-02 台账行  **W34 源码审查注记（不新开行）**：2026-11-19《源码级深度审查-W33》取证 `core-data/.../AppContainer.kt` 的 `promptContext()` 三处 `runCatching{}` 包裹 suspend 调用（`memory.all()` / `settings.disabledSkills.first()` / `skills.list()`）会吞 `CancellationException`，与本行 `syncMcpTools` 同源；同仓正例 `ChatSkillInput.loadSkillNames` 已显式重抛。合并为「吞 CE 反模式清零」卫生批（2+3 处 + 1 条 fake store 抛 CE 的 JVM 单测），属 `.kt` 顺延范围，待用户裁决第 8 项。  **W37 行内注记（内容订正，状态维持 OPEN）**：`AppContainer.promptContext()` 三处 `runCatching` 已改 `degradeTo` + `CancellationException` 显式重抛；缺「取消透传」直接用例（仅模式自证 `AutoCompactDiagnosticsTest`）；复核报告新发现 `ExtrasViewModel.kt` 同源 6 处（共 18 处 runCatching）待清零，故整行不销。原「待用户裁决第 8 项」已被「全面推进」授权覆盖。 **W38 销账**：suspend 路径吞 CE 反模式清零——三处 AppContainer（W37）+ ExtrasViewModel 18 处 + ChatViewModel.syncMcpTools 2 处全改共享 suspend 安全辅助（degradeTo/runIgnoringCancellation/suspendResultOf）；新增 `SuspendCancellationPassThroughTest` 7 条**直接用例**（测生产函数 CE 原样重抛 + 普通异常降级，区别于 W37 模式自证）；fulltest 829/0/0、arch-guard 27/1/0、G3 selftest 30/30 实跑绿；出口判据「suspend 路径 runCatching 清零 + CE 重抛直接用例」满足 ⇒ 翻 `CLOSED`（证据列 `docs/analysis/w38-acceptance.md` §步 3、`evidence/raw/w38/`）。 **W38 本波复核指路**：`docs/analysis/w38-acceptance.md` §步 3 七条直接用例实跑摘录 + `_w38/` 判据输出在案（W39 第 0 车补锚点，属内容订正、状态维持 CLOSED）。 |
| W17-03 | `MC-1`：尾置 `Role.SYSTEM` 摘要的协议风险（Anthropic 族 system 是顶层字段，history 里出现 system 可能 400） | W17 | `OPEN` | **触发信号**：Anthropic adapter（W6 遗留面）实现时；**缓解动作**：把尾部 SYSTEM 摘要映射为顶层 system 的追加段（或降级 USER+边界声明）；现役仅 OpenAI 兼容族，无症状 | `harness-w17-deep.md` §2.2；`feature-chat/ManualCompress.kt` `summaryOf` 调用点 |
| W17-04 | `W9-05` journal / FreezeClock **第 12 次顺延**（按 `W16-04` 现行代数 11+1；逐波重数口径 13 并列待裁决） | W17 | `OPEN` | **触发信号**：本波用户指令为功能收口 + 矩阵，未给「强制开波 / 永不实现」显式裁决；**缓解动作**：用户拍板二选一或统一代数口径；**不关号、不软化、不代裁决** | `docs/analysis/w17-acceptance.md` §1.3 #1；四份外审一致要求二选一（w12-16 §五 / W16裁决 §五 / 莫斯 §六 / 路线建议 §六） |
| W17-05 | 功能矩阵真机侧未取全项（多模型×档位×功能面逐格证据以 W17 验收物 §3 实跑为准；未取到的不冒充已验收） | W17 | `OPEN` | **触发信号**：任一功能项判据未行使；**缓解动作**：四态如实 ⛔ / ⚠️ / ⛔vacuous，`both_states` 留痕，不引用旧波结论冒充本波通过；**本轮进展**：file_write 落盘 / file_list / 手动压缩 dropped=238 / 多模态 vision / skill 端到端 ⚠️（正文级间接证据）/ 多端点×多档位矩阵 ⛔vacuous（只跑 NV mistral-large × 32K 一格）如实登记 | `docs/analysis/w17-acceptance.md` §3；承接 `W16-11` 纪律 |
| W17-06 | 拆分三方整包（`ChatViewModel` 499/500 触顶未下沉本体；`core-context` 余 4 / `feature-settings` 余 8） | W17 | `OPEN` | **触发信号**：下波任一 feature-chat / core-context / feature-settings 增量触 G10 前必须先拆；**缓解动作**：`ChatMessageList` / `ChatBubble` 下沉 + W16-03 本体；**本波未拆**（只做了 G10 被迫小裁：裁 ChatSkillInput KDoc 过闸） | `scripts/arch-guard.sh` G10；`W16-03` 原样 OPEN |
| W17-07 | 安全波整包（扫描 job 去重 / pin install / allowlist / 全历史首跑 / 签名端到端） | W17 | `OPEN` | **触发信号**：任一安全相关改动 / 外审 P1 提请；**缓解动作**：单独成波逐项收口；**本波未做**（时间预算给功能收口 + 真机） | `W16-02` / `W14-02` / `W15-02` 原样 OPEN（外审 w12-16 §四清单） |
| W17-08 | 设备侧 1M 真压 | W17 | `OPEN` | **触发信号**：`W9-11` 双阻断解除时；**缓解动作**：1M 档真压取证；**本波未做**（双阻断未解除，只跑 32K 档读数） | `W9-11` 原样 OPEN |
| W17-09 | MCP 明文联调 | W17 | `OPEN` | **触发信号**：平台策略变更或 https 夹具备好；**缓解动作**：不改 `usesCleartextTraffic`；**本波未做** | `W16-06` / `W5-03` 原样 OPEN |

### 二-octies（W18 承接索引）

> **本表每行自带「触发信号 + 缓解动作」**。唯一真值源仍是 §一 表格行；本表是 W18 的承接索引，
> 不是第二真值源，**不计入 §二 统计表**（同 W15/W16/W17 口径）。
> 依据：用户 2026-11-13 指令（读记忆 / 上一波 handoff / OPEN 挂账行 / 众审查人原始报告并裁决分歧、
> **不改方案大体方向**；多免费节点模型 × 多档位〔直至 1M 读数〕× 全功能面真机矩阵；本地 UI + adb →
> 真机 → 推仓库及 CI；按收敛结论实施唯一目标）与 `docs/analysis/w18-acceptance.md` §1。
> ⚠️ 老号 `W14-01..11` / `W15-01..14` / `W16-01..18` / `W17-01..09` / `W9-05` 全数保留 `OPEN`，
> 本表不删、不改状态、不写「已核销」、不重判 `CLOSED`、不并号；W18 号段是**本波范围声明**，不是老号承接。
> 落笔纪律：台账由主理人**单人**落笔（本波虽有多席并行，起号仍集中主理人）。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W18-01 | journal / FreezeClock 二选一裁决 **第 13 次顺延**（两代数口径并列待用户裁决） | W18 | `OPEN` | **触发信号**：本单用户指令为多模型×多档位矩阵，未给「强制开波 / 永不实现」显式裁决；**缓解动作**：用户拍板二选一并统一代数口径；**不关号、不软化、不代裁决** | `docs/analysis/w18-acceptance.md` §1.3 #1；四份外审一致要求二选一（报告 A §五 / B §六 / C §六 / D §三） |
| W18-02 | 拆分三方整包（feature-chat 3996/4000 余 4、feature-settings 3495/3500 余 5、core-context 1646/1650 余 4；`ChatViewModel` 500/500） | W18 | `OPEN` | **触发信号**：下波任一 `.kt` 增量触 G10 前必须先拆；**缓解动作**：`ChatMessageList` / `ChatBubble` 下沉 + 本体拆分，**禁再调 `LINE_CAPS`**；**本波未拆**（裁决改为零 `.kt` 改动以同时满足「不改方向 + 不撞闸门」） | `scripts/arch-guard.sh` G10；`W17-06` / `W16-03` 原样 `OPEN`；`docs/analysis/w18-acceptance.md` §1.4 分歧 3 |
| W18-03 | 六行漏号补登（W13 段第 1 号 + W11 段第 6–10 号）与 G20 扫描面扩展 | W18 | `OPEN` | **触发信号**：外审「补行」与 W16 现役裁决「不制造幽灵挂账」冲突待独立成波；**缓解动作**：若扩扫描面到递归文档树，须先加简写前缀白名单 + 旧仓编号排除 + 逐条人工甄别，否则一扩就误判；**本波不做** | `arch-guard.sh:1329/1337` 现役 G20 只扫 `docs/handoff-*.md`；`W16-01` / `W15-01` / `W14-01` 原样 `OPEN` |
| W18-04 | 安全波整包（扫描 job 去重 / install pin + sha256 / allowlist 精确化 + 真 key 变异 / 全历史首跑 / 签名端到端） | W18 | `OPEN` | **触发信号**：任一安全相关改动或外审 P1 提请；**缓解动作**：独立成波逐项收口；**本波未做**（唯一目标为矩阵） | `W17-07` / `W16-02` / `W15-02` / `W14-02` 原样 `OPEN`（同物四号，本波不并号） |
| W18-05 | 设备侧 1M 真压 | W18 | `OPEN` | **触发信号**：`W9-11` 双阻断（L3 字符硬闸 + 轮头 400k）解除时；**缓解动作**：1M 档真压取证；**本波只做 1M 档位读数，不写真压** | `W17-08` / `W16-05` / `W9-11` 原样 `OPEN`；`docs/notes/w5-provider-matrix.md` §一（1M 档 reasoning 吃光输出 ⇒ vacuous） |
| W18-06 | MCP 明文联调 / 放开 `usesCleartextTraffic` | W18 | `OPEN` | **触发信号**：平台策略变更或 https 夹具备好；**缓解动作**：不改 manifest 开关、不为测试放开；**本波只做 MCP 现状确认** | `W17-09` / `W16-06` / `W5-03` 原样 `OPEN`；G15b 平台阻断 |
| W18-07 | git 历史清理 / force-push / rewrite history | W18 | `OPEN` | **触发信号**：用户显式授权不可逆动作；**缓解动作**：本单明禁，任何推送均不 force-push、不 rewrite history | `W16-07` 原样 `OPEN`；用户 2026-11-13 指令 |
| W18-08 | 四项用户裁决（journal 二选一 / 「≥3 端点」终局 / §1.5 占位符口径 / `P2-3` 二选一） | W18 | `OPEN` | **触发信号**：用户给出显式裁决；**缓解动作**：只留痕、不代做；本波模板占位符按 W17 同款惯例由主理人钉执行值并标注「非用户裁决项」 | `W16-13` / `W15-13` 原样 `OPEN`；`docs/analysis/w18-acceptance.md` 抬头 |
| W18-09 | 外审新提可复现小缺陷（经回源核验后五项）与六处账实订正 | W18 | `OPEN` | **触发信号**：拆分波腾出 G10 余量后；**缓解动作**：`stripDecoration` 仅在完整引导尾命中时剥离 / `importSubtitle` 合并显示 `droppedBlank` 与 `droppedToolRoles` / 冷启动竞态归一本可不依赖白名单 / 下拉 query 取末 token / 过触发面收窄；账实六件按「只加抬头不改历史正文」订正。**外审 N2「复制口径分裂」与报告 A §2.3「笔误键」两条已回源证伪 ⇒ 不列为待修、不立账** | 审查席独立回源；`docs/analysis/w18-acceptance.md` §1.4 分歧 5/10；`SessionImportParser` 行号已漂移，勿照抄旧行号 |
| W18-10 | 同物多名合并（安全波四号并一号、其余写 `CARRIED`；1M 四号同挂 / MCP 四号同挂同型） | W18 | `OPEN` | **触发信号**：台账卫生专项独立成波；**缓解动作**：一行 + 承接链注记，G20 之外加「同主题重复号」人工巡检即可，不必新守卫；**本波不并号、不改状态**（G20 只认行首 ID） | `W17-07` 等原样 `OPEN`；`docs/analysis/w18-acceptance.md` §1.4 分歧 6 |
| W18-11 | 为凑缓存加长 system / 音频 / `core-device` 独立波 / 图片预算四条 | W18 | `OPEN` | **触发信号**：对应能力独立成波；**缓解动作**：逐项评估；GLM 缓存铁律（800 全价 vs 4096×0.5 = 2048 当量）⇒ 不为凑缓存加长 system | `W16-08` / `W14-08` 原样 `OPEN`；`docs/notes/w25-prompt-cache-and-w3-design.md` |
| W18-12 | 把 PC 侧免费节点可用性结论升级为「设备侧可用」 | W18 | `OPEN` | **触发信号**：出现把 PC 侧探测结论写成设备侧通过的文档表述；**缓解动作**：四态纪律，端点判据一律以真机实际发请求为准；免费节点连续失败判 ⛔ 并切备用端点，不归因成应用代码 bug | `W16-09` / `W14-09` 原样 `OPEN`；`docs/analysis/w18-acceptance.md` §1.2 判据 |

---

---

## 二-novies（W19 承接索引）

> **本表每行自带「触发信号 + 缓解动作」**，避免「推 Wx」一句话式静默顺延。
> 唯一真值源仍是 §一 表格行；本表是 W19 的承接索引，不是第二真值源，**不计入 §二 统计表**
> （同 W15/W16/W17/W18 口径）。
> 依据：用户 2026-11-14 指令（多免费节点模型 × 多档位〔直至 1M 读数〕× 全功能面真机矩阵 +
> 本地/真机 + 推 CI；读记忆 / 上一波 handoff / OPEN 挂账行 / 本期源码日析；读众审查人原始报告并
> **裁决分歧、不改方案大体方向**；按收敛结论实施唯一目标）与方案席 Phase 1 只读结论、
> 审查席独立回源核验；收敛结论见 `docs/analysis/w19-acceptance.md` §1.2 / §1.4。
> ⚠️ 老号 `W14-01..11` / `W15-01..14` / `W16-01..18` / `W17-01..09` / `W18-01..12` / `W9-05`
> 全数保留 `OPEN`，本表不删、不改状态、不写「已核销」、不重判 `CLOSED`、不并号、不制造幽灵行；
> `W19-XX` 是**本波范围声明**，不是老号承接，不得写成 `CARRIED` / 替代。
> 落笔纪律：台账由主理人**单人**落笔（本波多席并行，起号仍集中主理人）。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W19-01 | journal / FreezeClock 二选一裁决 **第 14 次顺延**（两代数口径并列待用户裁决） | W19 | `OPEN` | **触发信号**：本单用户指令为多模型×多档位功能矩阵，未给「强制开波 / 永不实现」显式裁决；**缓解动作**：用户拍板二选一并统一 7/8/9/10/11/12/13/14 代数口径；**不关号、不软化、不代裁决** | `docs/analysis/w19-acceptance.md` §1.3 #1 / §1.4 收敛结论；五份外审一致要求二选一；老号 `W18-01` 原样 `OPEN` |
| W19-02 | 拆分三方整包（feature-chat 3996/4000 余 4、feature-settings 3495/3500 余 5、core-context 1646/1650 余 4；`ChatViewModel` 500/500） | W19 | `OPEN` | **触发信号**：下波任一 `.kt` 增量触 G10 前必须先拆；**缓解动作**：`ChatMessageList` / `ChatBubble` 下沉 + 本体拆分，**禁再调 `LINE_CAPS`**；**本波未拆**（裁决零 `.kt` 以同时满足「不改方向 + 不撞闸门」） | `scripts/arch-guard.sh` G10；`W18-02` / `W17-06` / `W16-03` 原样 `OPEN`；`docs/analysis/source-review-2026-11-14.md` B1 已升「紧急」 |
| W19-03 | 六行漏号补登与 G20 扫描面扩展 | W19 | `OPEN` | **触发信号**：外审「补行」与 W16 现役裁决「不制造幽灵挂账」冲突待独立成波；**缓解动作**：若扩扫描面须先加简写前缀白名单 + 旧仓编号排除 + 逐条人工甄别；**本波不做** | `arch-guard.sh` 现役 G20 只扫 `docs/handoff-*.md`（审查席复核 `:1334/:1342`）；`W18-03` / `W16-01` / `W15-01` / `W14-01` 原样 `OPEN` |
| W19-04 | 安全波整包（扫描 job 去重 / install pin + sha256 / allowlist 精确化 + 真 key 变异 / 全历史首跑 / 签名端到端） | W19 | `OPEN` | **触发信号**：任一安全相关改动或外审 P1 提请；**缓解动作**：独立成波逐项收口；**本波未做**（唯一目标为矩阵） | `W18-04` / `W17-07` / `W16-02` / `W15-02` / `W14-02` 原样 `OPEN`（同物多号，本波不并号） |
| W19-05 | 设备侧 1M 真压 | W19 | `OPEN` | **触发信号**：`W9-11` 双阻断（L3 字符硬闸 + 轮头 400k）解除时；**缓解动作**：1M 档真压取证；**本波只做 1M 档位读数，不写真压** | `W18-05` / `W17-08` / `W16-05` / `W9-11` 原样 `OPEN`；`docs/analysis/w19-acceptance.md` §1.4 分歧 9 档位轴/端点轴分开判定 |
| W19-06 | MCP 明文联调 / 放开 `usesCleartextTraffic` | W19 | `OPEN` | **触发信号**：平台策略变更或 https 夹具备好；**缓解动作**：不改 manifest 开关、不为测试放开；**本波只做 MCP 现状确认** | `W18-06` / `W17-09` / `W16-06` / `W5-03` 原样 `OPEN`；G15b 平台阻断 |
| W19-07 | git 历史清理 / force-push / rewrite history | W19 | `OPEN` | **触发信号**：用户显式授权不可逆动作；**缓解动作**：本单明禁，任何推送均不 force-push、不 rewrite history | `W18-07` / `W16-07` 原样 `OPEN`；用户 2026-11-14 指令 |
| W19-08 | 四项用户裁决（journal 二选一 /「≥3 端点」终局 / §1.5 占位符口径 / `P2-3` 二选一） | W19 | `OPEN` | **触发信号**：用户给出显式裁决；**缓解动作**：只留痕、不代做；模板占位符按 W17/W18 惯例由主理人钉执行值并标注「非用户裁决项」。本单用户以「新给可用节点」回应端点池障碍，本波按原口径组织矩阵但**不代改终局口径** | `W18-08` / `W16-13` / `W15-13` 原样 `OPEN`；`docs/analysis/w19-acceptance.md` 抬头 / §1.4 分歧 5 |
| W19-09 | 外审新提可复现小缺陷与账实订正：报告 D「发现 A」潜伏面 / `stripDecoration` 砍尾 / `importSubtitle` 掩盖 `droppedToolRoles` / N3 冷启动竞态 / N4 下拉整串 query / N5 触发面收窄 / 莫斯线 P3-新1 / M2 `loadSkillNames` 兜 `Throwable`；以及 W18 账实 P0-1 提交序列漏 tip、P0-2 G20 数字打架、P0-3 制品覆盖不足、P1 悬空引用、`W16-18` 状态、`W17-01` 数值 | W19 | `OPEN` | **触发信号**：拆分波腾出 G10 余量后（前八项改 `.kt` 必撞 G10）；账实六件属历史正文，按「只加抬头不改正文」须独立成波；**缓解动作**：每个 `.kt` 修法配 `文件:行` + 必红变异样本；机制指认已由审查席双路回源核实，**但「`hardOffDowngraded` 无 style 维度」子结论已证伪**（经 `!canHardOff` 间接依赖 style），外审「非 UI 可达」属把代码默认当运行态的时点差 | 审查席九项核验表；`OpenAiCompatibleAdapter.kt:240/245-246`、`ReasoningBody.kt:32`、`EndpointAdvancedConfig.kt:67`、`EndpointCapabilities.kt:278-279`、`RemoteEndpoint.kt:226-228`、`EndpointStore.kt:141/153/165`、`ChatInputBar.kt:140`、`ChatViewModel.kt:69-73`；`docs/analysis/w19-acceptance.md` §1.4 分歧 4/7/12 |
| W19-10 | 同物多名合并（安全波 / 1M / MCP 等多号并一号，其余写 `CARRIED`） | W19 | `OPEN` | **触发信号**：台账卫生专项独立成波；**缓解动作**：一行 + 承接链注记，并号须保留旧 ID 别名注记以免历史引用判红；**本波不并号、不改状态** | `W18-10` 等原样 `OPEN`；G20 只认行首 ID |
| W19-11 | 为凑缓存加长 system / 音频 / `core-device` 独立波 / 图片预算四条 | W19 | `OPEN` | **触发信号**：对应能力独立成波；**缓解动作**：逐项评估；缓存铁律（静态前缀 800 全价 vs 4096×0.5=2048 当量）⇒ 不为凑缓存加长 system；图片算进预算、不进 `PromptPrefix` 指纹 | `W18-11` / `W16-08` / `W14-08` 原样 `OPEN` |
| W19-12 | 把 PC 侧免费节点可用性结论升级为「设备侧可用」 | W19 | `OPEN` | **触发信号**：出现把 PC 侧探测结论写成设备侧通过的文档表述；**缓解动作**：四态纪律，端点判据一律以真机实际发请求为准；免费节点连续失败判 ⛔ 并切备用端点，不归因成应用代码 bug | `W18-12` / `W16-09` / `W14-09` 原样 `OPEN`；`docs/analysis/w19-acceptance.md` §1.2 判据 |
| W19-13 | **本波新登记**：W18 三处收尾契约 P0 的顺手修补（补交接 §八 tip 提交、抹平验收物 G20 写死数、补齐 `evidence/w18-matrix-summary.json` 功能格与 ITER-4..6） | W19 | `OPEN` | **触发信号**：外部审查再次判「历史制品可复核面 < 声明面」；**缓解动作**：按 W16/W17/W18「handoff 是开立事实、只加抬头不改历史正文」纪律，独立成波处置；**本波只在本波制品内避免同型缺陷**（G20 不写死数 / 制品覆盖与声明面对齐 / 提交序列跟到最后一次提交），不改 W18 正文 | 审查席核验 #3/#4/#5（三处指认均成立）；`docs/analysis/w19-acceptance.md` §1.4 分歧 4/11；`W18-09` 原样 `OPEN` |
| W19-14 | **本波新登记**：`docs/analysis/source-review-2026-11-14.md` 标题括号「已提交 main」易被误读为本文档已入库，实际为未跟踪 | W19 | `CLOSED` | **触发信号**：外部审查按标题括号摘录，误判该日析文档已入库；**缓解动作**：只登记**表述易误读**、不判账实矛盾、不加抬头改写历史正文；本波若将其入库须在提交信息写明「作为本波输入文档入库」 | 审查席核验 #9：`git status --short` = `??`，该文档 `:11` 已自认未跟踪；`docs/analysis/w19-acceptance.md` §1.1 #5 / §1.3 #14  **W34 结案（用户裁决 2026-11-19）**：对象文件 `source-review-2026-11-14.md` 属自动化产物、已按用户裁决于 W33 删除（`docs/analysis/w33-acceptance.md` §5.4；《源码级深度审查-W33》头部批注「删除无问题，本次不再作为问题项」）⇒ 整行描述的「标题括号易误读」问题随对象消失而消失，改 `CLOSED`；删除依据与恢复路径（git 历史）登记于 `W34-03`。 |
| W19-15 | 本波真机矩阵中**判据未行使**的功能面格（逐格四态以验收物 §3 实跑为准） | W19 | `OPEN` | **触发信号**：任一功能面判据未行使（多模态 / 工作空间 / skill / 手动压缩 / 自动压缩 / MCP）；**缓解动作**：四态如实 ⛔ / ⚠️ / ⛔vacuous，`both_states` 留痕，不引用 W17/W18 结论冒充本波通过；设备侧 `skills/` 为空、图片输入需活跃端点开 IMAGE 能力位等前置逐格记明 | `docs/analysis/w19-acceptance.md` §3 / §5.1；`W18` 号段第 5 行原样 `OPEN` |

---

## 二-decies（W20 承接索引）

> **本表每行自带「触发信号 + 缓解动作」**，避免「推 Wx」一句话式静默顺延。
> 唯一真值源仍是 §一 表格行；本表是 W20 的承接索引，不是第二真值源，**不计入 §二 统计表**
> （同 W15/W16/W17/W18/W19 口径）。
> 依据：用户 2026-11-15 指令（读记忆 / 上一波 handoff / OPEN 挂账行〔现役仓，非旧仓〕，
> 列出「做什么 / 明确不做」写入验收物 §1；读三份众审查人原始报告并**裁决分歧、不改方案大体方向**；
> 多个模型测不同上下文〔直至 1M 读数〕与多模态 / 工作空间文件读写 / skill / MCP / 手动与自动压缩；
> 先本地 UI + adb 调试 → 真机 → 推仓库及 CI；按收敛结论实施唯一目标）与方案席 Phase 1 A–F 结论、
> 审查席 12 项独立回源核验；收敛结论见 `docs/analysis/w20-acceptance.md` §1.2 / §1.4。
> ⚠️ 老号 `W14-01..11` / `W15-01..14` / `W16-01..18` / `W17-01..09` / `W18-01..12` /
> `W19-01..15` / `W9-05` 全数保留 `OPEN`，本表不删、不改状态、不写「已核销」、不重判 `CLOSED`、
> 不并号、不制造幽灵行；`W20-XX` 是**本波范围声明**，不是老号承接，不得写成 `CARRIED` / 替代。
> 落笔纪律：台账由主理人**单人**落笔（本波多席并行，起号仍集中主理人）。
> 本波 handoff 严格零 W-ID 字面量（W16 自指陷阱），故本段 20 行对 G20 并集零贡献 ⇒
> **G20 `PASS` 不等于本波新增行已被守卫覆盖**，边界注记见验收物 §2.1。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W20-01 | journal / FreezeClock 二选一裁决 **第 15 次顺延**（两代数口径并列待用户裁决） | W20 | `OPEN` | **触发信号**：本单用户指令为多模型多档位功能矩阵，未给「强制开波 / 永不实现」显式裁决；**缓解动作**：用户拍板二选一并统一代数口径；**不关号、不软化、不代裁决** | `docs/analysis/w20-acceptance.md` §1.3 #1 / §1.4 收敛结论；老号原样 `OPEN` |
| W20-02 | 拆分三方整包（feature-chat 3996/4000 余 4、feature-settings 3495/3500 余 5、core-context 1646/1650 余 4；`ChatViewModel` 500/500） | W20 | `OPEN` | **触发信号**：下波任一 `.kt` 增量触 G10 前必须先拆；**缓解动作**：下沉纯函数与组件 + 本体拆分，**禁再调 `LINE_CAPS`**；**本波未拆**（零 `.kt` 以同时满足不改方向 + 不撞闸门） | `scripts/arch-guard.sh` G10 / `LINE_CAPS:152-200`；审查席 #3 逐模块 `wc -l` 实跑；老号原样 `OPEN`  **W37 行内注记（内容订正，状态维持 OPEN）**：已做 G10 必要的 `AppContainer` 单文件拆分（417→374 / `PromptContextAssembler.kt` 新建；实为 401→374 按验收物订正）；原始三方（feature-chat / feature-settings / core-context）拆分仍未做，余量见 W38 第 1 车 G10 前置；原「本波未拆（零 `.kt`）」措辞已被本注记取代。  **W39 行内注记（内容订正，状态维持 OPEN；W40 第 0 车补号）**：原始三方已腾行数——`core-context` 1649→1477 余 173 / `feature-chat` 3996→3978 余 22 / `feature-settings` 3495→3417 余 83 / `ChatViewModel` 500→447，`LINE_CAPS` 未调、`TOTAL_LINE_CAP` 未抬（W39 第 1 车，证据 `docs/handoff-20261120-wave39.md` §5.1）；**本体再分层（`ChatViewModel` 再分层 / `ChatScreen` 拆文件）仍未做**，`SwitchNotice`/`PendingSwitch` 值对象落 `core-agent` 的层级归属待裁（W39 交接 §十⑤）。 |
| W20-03 | 六行漏号补登与 G20 扫描面扩展 | W20 | `OPEN` | **触发信号**：外审「补行」与现役「不制造幽灵挂账」裁决冲突待独立成波；**缓解动作**：扩扫描面须先加简写前缀白名单 + 旧仓编号排除 + 逐条人工甄别，并同步 selftest case；**本波不做** | `arch-guard.sh:1334/:1342/:1350-1355`（审查席 #2）；老号原样 `OPEN` |
| W20-04 | 安全波整包（扫描 job 去重 / install pin + sha256 / allowlist 精确化 + 真 key 变异 / 全历史首跑 / 签名端到端） | W20 | `OPEN` | **触发信号**：任一安全相关改动或外审 P1 提请；**缓解动作**：独立成波逐项收口；**本波未做**（唯一目标为矩阵） | 老号（七连同号）原样 `OPEN`，本波不并号、不改状态 |
| W20-05 | 设备侧 1M 真压 | W20 | `OPEN` | **触发信号**：`W9` 段第 11 行双阻断（L3 字符硬闸 + 轮头 400k）解除时；**缓解动作**：1M 档真压取证；**本波只做 1M 档位读数，不写真压** | 老号原样 `OPEN`；`docs/analysis/w20-acceptance.md` §1.4 分歧 5 档位轴 / 端点轴分开判定 |
| W20-06 | MCP 明文联调 / 放开 `usesCleartextTraffic` | W20 | `OPEN` | **触发信号**：平台策略变更或 https 夹具备好；**缓解动作**：不改 manifest 开关、不为测试放开；**本波只做 MCP 现状确认** | G15b 平台阻断（`arch-guard.sh` G15b）；老号原样 `OPEN` |
| W20-07 | git 历史清理 / force-push / rewrite history | W20 | `OPEN` | **触发信号**：用户显式授权不可逆动作；**缓解动作**：本单明禁，任何推送均不 force-push、不 rewrite history | 用户 2026-11-15 指令；老号原样 `OPEN` |
| W20-08 | 四项用户裁决（journal 二选一 / 「≥3 端点」终局 / §1.5 占位符口径 / `P2-3` 二选一） | W20 | `OPEN` | **触发信号**：用户给出显式裁决；**缓解动作**：只留痕、不代做；模板占位符按 W17/W18/W19 惯例由主理人钉执行值并标注「非用户裁决项」。本单用户以「再给可用节点」回应端点池障碍，本波按原口径组织矩阵但**不代改终局口径**。（W29 波注记：tip run 闭环方式仍未裁决，按 W28 §1.1 #6 自设规则，自 W29 起不再逐波判 P0、只随交付消息给出 run 链接；tip 回填 run 与 commit 已补进 W28 §5.6。） | 老号原样 `OPEN`；`docs/analysis/w20-acceptance.md` 抬头 / §1.4 分歧 5 |
| W20-09 | 外审新提可复现 `.kt` 小缺陷与账实订正（A1 `syncMcpTools` 吞 CE / A2 `stripDecoration` 哨兵 / C1 配对层 round-trip 缺测 / `W19-09` 前八项 / 旧段状态与数值） | W20 | `OPEN` | **触发信号**：拆分波腾出 G10 余量后；**缓解动作**：每个修法配 `文件:行` + 必红变异样本。精确边界：A2 与 C1 补测不触 G10（只扫 `src/main`），但 C1 会抬严格 `@Test` 计数须同提交改 `build.yml:135` 并对账；A1 落 `ChatViewModel.kt` 才触顶 | 审查席 #10；`ChatViewModel.kt:136/138`、`SkillMention.kt:55/128-129`、`build.yml:448-526`。【W21 收口注记（报告③ M-4）：C1 经审查席 #10 + 莫斯 W20 独立回源——配对层已有 JVM 测试（`SkillMentionTest.kt:134-138` decorate→strip→decorate 幂等），真实缺口 = displayText 显示层链路单测，修复目标随之改写；knownNames 白名单账面映射注记随本注记一并补登（W19 三线共识落地）】 |
| W20-10 | 同物多名合并（安全波 / 1M / MCP 多号并一号，其余写 `CARRIED`） | W20 | `OPEN` | **触发信号**：台账卫生专项独立成波；**缓解动作**：一行 + 承接链注记，并号须保留旧 ID 别名注记以免历史引用判红；**本波不并号、不改状态** | G20 只认行首 ID；老号原样 `OPEN` |
| W20-11 | 为凑缓存加长 system / 音频 / `core-device` 独立波 / 图片预算四条 | W20 | `OPEN` | **触发信号**：对应能力独立成波；**缓解动作**：逐项评估；缓存铁律（静态前缀 800 全价 vs 4096×0.5=2048 当量）不为凑缓存加长 system；图片算进预算、**不进 `PromptPrefix` 指纹**；L3 字符闸 `chars/token` 标定分子绝不含图片当量 | 老号原样 `OPEN`；`docs/analysis/w20-acceptance.md` §1.3 #11 |
| W20-12 | 把 PC 侧免费节点可用性结论升级为「设备侧可用」 | W20 | `OPEN` | **触发信号**：出现把 PC 侧探测结论写成设备侧通过的文档表述；**缓解动作**：四态纪律，端点判据一律以真机实际发请求并收敛为准；免费节点连续失败判 ⛔ 并切备用端点，不归因成应用代码 bug | 老号原样 `OPEN`；`docs/analysis/w20-acceptance.md` §1.2 判据 |
| W20-13 | 矩阵存续决策包 / 五项拍板 / 四问选择器 / 口径终局裁决 | W20 | `OPEN` | **触发信号**：用户显式拍板「继续矩阵 / 端点环境专项 / 转拆分 / 冻结矩阵」；**缓解动作**：只留痕、不代做；三份外审排期主张整包顺延不取消不推翻 | 三份外审原件；`docs/analysis/w20-acceptance.md` §1.4 分歧 1/2 |
| W20-14 | 取证驱动 + `make_evidence.py` + `privacy_gate.py` 入仓（`scripts/evidence/`） | W20 | `OPEN` | **触发信号**：取证基础设施独立成波；**缓解动作**：入仓并补可复现性自检；**本波不入仓**（新增仓根非 docs 路径会改方案大体方向）。本波已做等价自检：34 个驱动 `py_compile` + import 全过、`w20.py` 垫片避免断 import、上波补丁隔离、产物名与基线参数化 | 外审报告① N-新5；`_w20/prep.py` / `_w20/_legacy_patches/`（仓外，不入库） |
| W20-15 | W18 / W19 历史制品正文订正（含 W18 制品「pass 值待复验」抬头、补 tip 提交、抹平 G20 写死数、补功能格与迭代轮次） | W20 | `OPEN` | **触发信号**：外部审查再次判「历史制品可复核面 < 声明面」；**缓解动作**：按四波「只加抬头不改历史正文」纪律独立成波。**本波补登记**：W18 制品两格 `usage_observed` 逐字节相同构成「残留 usage 读取」存疑特征（观察+推断混合，不写成应用行为事实），该数据可信度欠账 W19 未挂；本波只在本波制品内避免同型缺陷 | 审查席 #8；`evidence/w18-matrix-summary.json:115/:126`；老号原样 `OPEN`。【W21 收口注记（报告③ M-1/M-3）：W20 段自身制品亦有同型缺陷——`observation` 字段词中断点截断 ×6/8 且无 ellipsis 标记（预筛失败细节/判据结论/日志线索丢失）、`TIER_1M`/`TIER_512K` 的 `feature` 越界值 `unclassified`（端点 7 格占用 `multi_tier` 面位）、迭代编号跳 3、`attempts=2` 合并抹早期 ⛔vacuous 态、`F1_filewrite` 把【推断·高置信】压成裸 `pass`；制品正文**不重出**，登记与断言升级见 W21 段制品卫生行】 |
| W20-16 | 台账「本段仅登记新增 + 承接链」改模板一字 | W20 | `OPEN` | **触发信号**：台账卫生专项独立成波；**缓解动作**：改真值源模板须独立成波，本波仍逐条登记本波真实新增行、不制造幽灵行、不改老号状态 | 外审报告③ §三；审查席 #11 独立重算 145 行 / 115 `OPEN` / 19 `CLOSED` / 11 `CARRIED` |
| W20-17 | G20 对本波新增挂账的覆盖盲区（守卫侧修复） | W20 | `OPEN` | **触发信号**：出现「G20 PASS 即等于本波新增行已覆盖」的误读；**缓解动作**：独立成波处理（扩扫描面须同步 selftest case）；**本波只在验收物 §2.1 写显式边界注记**：`PASS` + `MISSING=[]` 不等于覆盖新增行、`0 命中 ≠ 通过`，不往 handoff 塞 ID、不改 `arch-guard.sh` | 外审报告② P0-3；审查席 #7 逐 commit 实算（逐 commit 实算恒定，数值留仓外不写入台账——派生量不写死，报告① P0-2 W21 收口修订；⚠️ 本处属 §1.4「行内追加不改原句」纪律的**显式例外**：原句内派生数「（112 恒定）」按 P0-2 订正为口径表述而非追加注记，因派生数不应以任何形态留在真值源——例外归属 W21-01 收口执行行）；方案席 A-6 |
| W20-18 | 「自动回退」归因回源 / 端点侧静默 fallback 查码 | W20 | `OPEN` | **触发信号**：真机取证明确显示切换后活跃端点发生变化；**缓解动作**：先区分「切换操作未生效」与「应用侧静默回退」，若是后者按行为面立账；**本波采外审定级（⚠️ 待归因）、不采查码动作**，静态未定位、不写成应用行为事实 | 外审报告③ §一.2；审查席 #9（`setActiveEndpointId` 全树仅 3 处写入点、`retryOnConnectionFailure(false)`） |
| W20-19 | 本波真机矩阵中**判据未行使**的功能面格（逐格四态以验收物 §3 实跑为准） | W20 | `OPEN` | **触发信号**：任一功能面判据未行使（多模态 / 工作空间 / skill / 手动压缩 / 自动压缩 / MCP）；**缓解动作**：四态如实 ⛔ / ⚠️ / ⛔vacuous，不引用旧波结论冒充本波通过；判据前置（长对话夹具 / 严格 `ls -1` 落盘 / ≥200×120 真图）须先备好 | `docs/analysis/w20-acceptance.md` §3 / §5.1；老号原样 `OPEN` |
| W20-20 | 本期两份未跟踪源码日析的抬头订正与处置口径 | W20 | `OPEN` | **触发信号**：外部审查按标题括号摘录误判该文档已入库；**缓解动作**：只登记表述易误读、不判账实矛盾、不加抬头改写历史正文；本波若将其入库须在提交信息写明「作为本波输入文档入库」，不得写成「此前已提交」 | `git status --short` = `??`；两份文档均自述源码字节级零改动；老号原样 `OPEN` |

### W21 段（2026-11-16，6 行：1 `CLOSED` + 5 `OPEN`）

> 上游依据：用户 2026-11-16 指令（读记忆 / 上一波 handoff / 台账 OPEN 行；三份众审查人报告裁决分歧
> 不改方案大体方向；新 PAT 换发；新节点 × 多档位 × 功能面解锁矩阵；先本地 → 真机 → 推 CI；
> 收尾契约；停于交接）与方案席 Phase 1 回源结论（六题 + 两条全局纠错）；收敛结论见
> `docs/analysis/w21-acceptance.md` §1.1–§1.4。
> ⚠️ 老号全数保留 `OPEN`，本表不删、不改老行状态、不并号、不制造幽灵行；`W21-XX` 是**本波范围声明**。
> 被点名老行（W20-09 / W20-15 / W20-17）已按「行内注记不改原句」口径补登 W21 收口注记（见各老行）。
> 落笔纪律：台账由主理人**单人**落笔。
> 本波 handoff 严格零 W-ID 字面量 ⇒ 本段 6 行对 G20 并集零贡献 ⇒ **G20 `PASS` 不等于本波新增行
> 已被守卫覆盖**（沿用上一波段边界注记口径）。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W21-01 | 上一波收尾契约收口执行（P0-1 制品清单节标题恢复 + §5.4 顺位复位 / P0-2 两处写死派生数改实跑口径 / P0-3 过时占位清除 / P1-1 凭据句订正 / P1-7 补推送 2 哈希与独立扫描 run 链接 / §4 回源补轮 4–8 / §3.1 补 3 格 / §2 编号乱序修复 / 三处计数订正） | W21 | `CLOSED` | **触发信号**：三份外审报告一致判定收尾契约结构缺陷（第 0 车出口判据）；**缓解动作**：最小结构修复，每处带「W21 收口修订」标注，不删历史结论语义；出口判据 grep（写死派生数 / 应轮换句 / 待回填占位）全树 **0 命中** | `docs/analysis/w20-acceptance.md`（带 W21 收口修订标注的 diff）；本台账 W20-15 / W20-09 / W20-17 三处行内注记；`_w21/` 出口 grep 复核记录 | 【W22 收口注记（报告① P0-2）：出口判据「grep 全树 0 命中」按字面不成立——`112 恒定` 1 / `应轮换` 2 / `待回填` 3 命中均为注记引用与判据名自指语境，非缺陷形态；本处订正为「缺陷形态 0 命中 + 排除规则」，维持 `CLOSED`，立为销账行自证范本】
| W21-02 | 上一波制品五缺陷登记（`observation` 词中断点截断 ×6/8 无标记、`feature` 越界值、迭代编号跳 3、`attempts` 合并抹早期态、推断压成裸 `pass`）——制品正文**不重出** | W21 | `OPEN` | **触发信号**：外部审查按上一波制品作历史结论复核时；**缓解动作**：仓外生成器六断言升级已落地并配必红回归（跳号 / 引用串禁截断 / face 枚举 / attempts 留态 / 推断限定全部会响，W20 真实数据回归实测拦截）；历史制品修复待独立成波（老号 W20-15 域），本波不重出不覆盖 | `_w21/make_evidence.py`（断言升级源码）；`_w21/evidence/_regression_*`（回归夹具与拦截实测）；W20-15 行内注记 | 【W22 收口注记（报告① P0-1）：本波制品面数三态不自洽（§3.2 表 10 行 / 汇总 9 / declared 9）由本波新制品按九面方案归一并补「新增面须登记 declared」断言 + 必红变异；历史 W20/W21 制品正文不重出不覆盖】
| W21-03 | journal / FreezeClock 二选一裁决 **第 16 次顺延**（承接上一波段第 1 行，两代数口径仍并列待用户裁决） | W21 | `OPEN` | **触发信号**：用户未给显式二选一裁决；**缓解动作**：用户拍板二选一并统一代数口径；不关号、不软化、不代裁决 | `docs/analysis/w21-acceptance.md` §1.3 #2；老号原样 `OPEN` |
| W21-04 | 本波真机矩阵未达成格清单（`nv2` mistral ⛔ node_no_response 三窗口无信号 / `orvis` gemma ⛔ node_unavailable / 手动压缩 `dropped>0` 未达（两次执行「无可压缩内容」）/ 层 C L1 在 32K 被守卫硬拦（committed 79027 > cap 50227，守卫行使本身为活证）/ `hard=` logcat 指纹未捕获 / `file_list` 计数与真值不符 ⚠️） | W21 | `OPEN` | **触发信号**：节点复活或新会话小窗口夹具可铺时；**缓解动作**：nv2 复活补视觉语义格即闭环（链条已全行使，唯一缺口=节点）；压缩两格需新会话铺夹具（本波预算 12 轮已尽）；环境类 ⛔ 不归因应用代码 | `docs/analysis/w21-acceptance.md` §3 逐格四态；`_w21/evidence/matrix_facts.jsonl` / `compress_facts.jsonl` / `shots/` | 【W22 收口注记：本波 mistral（nv2）节点复活 ⇒ F1 多模态从 ⛔ 升级为 ✅（换 IMAGE 端点后语义逐字命中）；deepseek 仍不在设备端点池判 ⛔vacuous；压缩两格因 file_read 上限几何夹紧仍未达，需 .kt 或放宽上限（本波禁改）】
| W21-05 | 非对话模型（视频生成 / 视频超分两支）不进 chat 端点矩阵 | W21 | `OPEN` | **触发信号**：出现把非对话模型计进端点收敛数的表述；**缓解动作**：chat 矩阵只收对话 LLM；两支与矩阵无关，不判 ⛔ 不计分母 | 用户本单节点清单；`docs/analysis/w21-acceptance.md` §1.3 #12 |
| W21-06 | 仓外取证驱动遗留（`switch.home()` BACK 循环会退出应用——本波以冷启动/单 BACK 替代并新落 `row_switch.py`；agent-0.log 全程零工具记账 ⇒ 工具调用判据一律以 UI 副作用 / sentinel 复述 / 文件系统证据为准） | W21 | `OPEN` | **触发信号**：后续波复用驱动栈遇到同型 BACK 逃逸或日志判据空转时；**缓解动作**：维持取证设施不入仓口径（老号 W20-14 同域）；判据面改写规则已写入本波验收物 §1.2 | `_w21/row_switch.py`（新增仓外驱动）；`docs/analysis/w21-acceptance.md` §1.2 #9；本波 skill/工作空间格判据实录 |


### W22 段（2026-11-16，6 行：1 `CLOSED` + 5 `OPEN`）

> 上游依据：用户 2026-11-16 指令（读记忆 / 上一波 handoff / 台账 OPEN 行；**四份**众审查人报告裁决
> 分歧不改方案大体方向；新 PAT 换发；新节点 × 多上下文〔直至 1M〕× 多模态 / 工作空间 / skill / MCP /
> 手动+自动压缩；先本地 → 真机 → 推 CI；收尾契约；停于交接）与方案席 Phase 1 十四裁决；
> 收敛结论见 `docs/analysis/w22-acceptance.md` §1.1–§1.4。
> ⚠️ 老号全数保留 `OPEN`，本表不删、不改老行状态、不并号、不制造幽灵行；本段编号是**本波范围声明**。
> 被点名老行（W21-01 / W21-02 / W21-04）已按「行内注记不改原句」口径补登 W22 收口注记（见各老行）。
> 落笔纪律：台账由主理人**单人**落笔。
> 本波 handoff 严格零复合编号字面量 ⇒ 本段 6 行对 G20 并集零贡献 ⇒ **G20 `PASS` 不等于本波新增行
> 已被守卫覆盖**（沿用老段边界注记口径）。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W22-01 | 上一波收尾契约收口执行（面数三态九面方案五处同步 / 销账自证措辞 + 排除规则 / 推送链接/时点/错数订正）——每处带「W22 收口修订」标注 | W22 | `CLOSED` | **触发信号**：报告① 判上一波收尾契约 P0/P1 结构缺陷；**缓解动作**：最小结构修复、不删历史结论语义；出口判据按字面复核成立（缺陷形态 0 命中 + 排除规则明确） | `docs/analysis/w21-acceptance.md`（带 W22 收口修订标注的 diff）；本台账 W21-01 / W21-02 / W21-04 三处行内注记；`_w22/` 出口 grep 复核 |
| W22-02 | 上一波制品缺陷登记（面数三态不自洽、销账自证字面不成立等）——制品正文**不重出** | W22 | `OPEN` | **触发信号**：外部审查按上一波制品作历史结论复核时；**缓解动作**：本波新制品出厂断言升级（含「新增面须登记 declared」+ 必红变异），历史制品修复待独立成波（老号 W21-02 / W20-15 域），本波不重出不覆盖 | `_w22/make_evidence_w22.py`（断言源码 + 必红回归）；`evidence/w22-matrix-summary.json`；W21-02 行内注记 | 【W23 收口注记（报告② P0-1）：本波新制品按 8 处 schema 缺陷全修——恢复 schema/cells[]/iterations 明细/evidence_ref/attempt_states/必红样本入本体；历史制品不重出不覆盖】
| W22-03 | journal / FreezeClock 二选一裁决 **第 17 次顺延**（承接上一波段第 3 行，两代数口径仍并列待用户裁决） | W22 | `OPEN` | **触发信号**：用户未给显式二选一裁决；**缓解动作**：用户拍板二选一并统一代数口径；不关号、不软化、不代裁决 | `docs/analysis/w22-acceptance.md` §1.3 #2；老号原样 `OPEN` |
| W22-04 | 本波真机矩阵未达成格清单（E4b deepseek 设备池无端点 ⛔vacuous / E5 gemma HTTP429 ⛔ 环境侧 / F3 workspace list ⚠️ 无记账 / F5 手动压缩 `dropped>0` 未达 / F6 自动压缩 `applied=true` 未达 / F7 无外部 MCP 端点 ⛔vacuous / `hard=` logcat 指纹未捕获） | W22 | `OPEN` | **触发信号**：节点复活 / 新会话可分割夹具可铺时（F1 多模态已因 mistral 复活闭环）；**缓解动作**：F5/F6 需动 `.kt`（单指针 + 时序）或放宽 file_read 上限（本波零 `.kt` 禁改）；E4b 需用户设备侧新增端点；环境类 ⛔ 不归因应用代码 | `docs/analysis/w22-acceptance.md` §3 逐格四态；`_w22/evidence/matrix_facts.jsonl` / `compress_facts.jsonl` / `shots/` | 【W23 收口注记：F6 自动压缩本波从 ⚠️ 转 ✅（auto_compact dropped=19 kept=14 落账，源码双点回源成立，带 caveat 不读成压缩主线闭环）；F5 手动压缩仍 dropped=0 ⛔vacuous（需 .kt 单指针/时序或放宽 file_read 上限，本波禁改）；E4b 仍不在池】
| W22-05 | 非对话模型（`wan2.2-animate` 视频生成 / `nvidia/vsr` 视频超分两支）不进 chat 端点矩阵 | W22 | `OPEN` | **触发信号**：出现把非对话模型计进端点收敛数的表述；**缓解动作**：chat 矩阵只收对话 LLM；两支与矩阵无关，不判 ⛔ 不计分母 | 【W23 收口修订（报告② P0-2）：证据列去凭据文件名（隐私红线，仅证据列表述，状态/触发信号/缓解动作原句未改）】 用户本单模型清单（六支，凭据文件名按隐私纪律不落盘）；`docs/analysis/w22-acceptance.md` §1.3 #12 |
| W22-06 | 取证驱动遗留（本波新落 `devdrv.sh` / `ask_robust.sh` / `fixture_run.sh` / `redact.py`；agent log 全程零工具记账 ⇒ 工具/技能调用判据一律以 UI 副作用 / sentinel 复述 / 文件系统证据为准） | W22 | `OPEN` | **触发信号**：后续波复用驱动栈遇到同型 BACK 逃逸或日志判据空转时；**缓解动作**：维持取证设施不入仓口径（老号 W20-14 / W21-06 同域）；判据面改写规则已写入本波验收物 §1.2 | `_w22/*.sh` / `redact.py`（仓外）；`docs/analysis/w22-acceptance.md` §1.2；本波 skill / 工作空间格判据实录 |



### W23 段（2026-11-17，6 行：1 `CLOSED` + 5 `OPEN`）

> 上游依据：用户 2026-11-17 指令（读记忆 / 上一波 handoff / 台账 OPEN 行；**四份**众审查人报告裁决
> 分歧不改方案大体方向；用户给 API 免费节点清单（凭据文件名不落盘）+ 两支 openrouter 模型 + 公开构建平台其余模型
> × 多上下文〔直至 1M〕× 多模态 / 工作空间 / skill / MCP / 手动+自动压缩；先本地 → 真机 → 推 CI；
> 收尾契约；停于交接）与方案席 Phase 1 十四裁决；收敛结论见 `docs/analysis/w23-acceptance.md` §1.1–§1.4。
> ⚠️ 老号全数保留 `OPEN`，本表不删、不改老行状态、不并号、不制造幽灵行；本段编号是**本波范围声明**。
> 被点名老行（W22-02 / W22-04）已按「行内注记不改原句」口径补登 W23 收口注记（见各老行）。
> 落笔纪律：台账由主理人**单人**落笔。
> 本波 handoff 严格零复合编号字面量 ⇒ 本段 6 行对 G20 并集零贡献 ⇒ **G20 `PASS` 不等于本波新增行
> 已被守卫覆盖**（沿用老段边界注记口径）。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W23-01 | 上一波收尾契约收口执行（制品 schema 退化恢复 / 脱敏回退 / 面数与推送链接订正 / 两页零成本 docs 并入）——每处带「本波收口修订」标注 | W23 | `CLOSED` | **触发信号**：两报告判上一波收尾契约 P0（制品断言无对象、必红样本只在仓外）与 P0-2（脱敏规则集缺凭据文件名形态）；**缓解动作**：本波新制品恢复 schema/cells[]/iterations[] 明细/evidence_ref/attempt_states/必红样本入本体（历史制品不重出），脱敏规则集补形态并正控自证会响；出口判据按字面复核成立（8 处 schema 缺陷全修、正控 15/15+11/11、缺陷形态 0 命中〔注记引用除外〕）〔本波收口修订：原句「全树 0 命中」按字面不成立，沿用 W21-01 销账范本订正为「缺陷形态 0 命中（注记引用除外）」；8 处逐项字段值证据见 w24-acceptance.md §1.1 #4/#5/#6/#7 与 §5.4/§5.5〔本波订正：原指 §2「本地闸门」不含字段值，为悬空引用，改指真实小节〕；分类日志在仓外 _w23/privacy_ruleset.safe.log〕 | `docs/analysis/w23-acceptance.md` §1.1 #2/#3/#4/#11；`_w23/privacy_ruleset.safe.log`；`_w23/phase3_review_static.md` |
| W23-02 | 上一波制品缺陷登记（schema 退化、断言列 10 条但 selftest 仅抽样 5 条、F 新面断言对当前制品必然绿）——制品正文**不重出** | W23 | `OPEN` | **触发信号**：外部审查按上一波制品作历史结论复核时；**缓解动作**：本波新制品 10 条断言每条配必红变异样本落 `mutation_cases[]`（10/10 全 REJECTED 实跑入仓），确定性重跑 sha256 一致；历史制品修复待独立成波（老号 W22-02 / W21-02 / W20-15 域），本波不重出不覆盖 | `_w23/make_evidence_w23.py`（生成器 + 10 变异自证）；`evidence/w23-matrix-summary.json`；W22-02 行内注记 |
| W23-03 | journal / FreezeClock 二选一裁决 **第 18 次顺延**（承接上一波段第 3 行，两代数口径仍并列待用户裁决） | W23 | `OPEN` | **触发信号**：用户未给显式二选一裁决；**缓解动作**：用户拍板二选一并统一代数口径；不关号、不软化、不代裁决；累计成本注记以行内注记零成本并入 | `docs/analysis/w23-acceptance.md` §1.3 #2；老号原样 `OPEN` |
| W23-04 | 本波真机矩阵未达成格清单（F3 workspace ⚠️ 无工具记账从严 / E5 gemma HTTP429 ⛔ 环境侧 / E4b deepseek 设备池无端点 ⛔vacuous / F5 手动压缩 dropped=0 ⛔vacuous / F7 无外部 MCP 端点 ⛔vacuous；19 格含增补 T0 32K 档回读 + F1b dots 视觉核验）。**F6 自动压缩本波转 ✅**（`auto_compact dropped=19 kept=14` 落账生效，源码双点回源成立，带 caveat：单会话证据、源码无独立 applied=true 字面，不读成压缩主线闭环） | W23 | `OPEN` | **触发信号**：节点复活 / 新会话可分割夹具可铺；**缓解动作**：F5 手动压缩需 `.kt`（`alreadyCompressed` 单指针 + 时序）或放宽 file_read 16000 上限（本波零 `.kt` 禁改）；E4b 需用户设备侧新增端点；F5/F7 环境类 ⛔vacuous 不冒充通过、不归因代码 | `docs/analysis/w23-acceptance.md` §3 逐格四态；`_w23/evidence/matrix_facts.jsonl` / `compress_facts.jsonl` / `shots/` |
| W23-05 | 非对话模型（视频生成 / 视频超分等）不进 chat 端点矩阵；用户标注模型能力（如某 openrouter 模型「视觉双通」）与设备实测（inputModalities 仅 TEXT）冲突时以真机为准 | W23 | `OPEN` | **触发信号**：出现把非对话模型计进端点收敛数、或把用户标注能力写成已验证事实；**缓解动作**：chat 矩阵只收对话 LLM；能力声明先验 `inputModalities` / chip 可用性，实测冲突以真机为准并带 caveat、不臆造 | `docs/analysis/w23-acceptance.md` §1.3 #12 / §3 F1；`_w23/evidence/endpoint_survey.md`（已脱敏） |
| W23-06 | 取证驱动遗留（本波 `_w23/devdrv.py` / `ep_probe.py` / `nav.py` / `redact.py` / `scan_artifacts.py`；脱敏正控自证抓出历史 serial 检测器恒不响 bug 并修复；agent log 全程零工具记账 ⇒ 工具/技能调用判据一律以 UI 副作用 / sentinel 复述 / 文件系统证据为准并带 caveat） | W23 | `OPEN` | **触发信号**：后续波复用驱动栈遇同型 BACK 逃逸、日志判据空转、或脱敏规则集完备性被质疑时；**缓解动作**：维持取证设施不入仓口径（老号 W20-14 / W21-06 / W22-06 同域）；判据面改写规则见本波验收物 §1.2；正控先自证会响再判 0 的纪律固化 | `_w23/*.py`（仓外）；`docs/analysis/w23-acceptance.md` §1.2 / §2；`_w23/privacy_ruleset.safe.log` |
### W24 段（2026-11-18，3 行：3 `OPEN`）

> 上游依据：用户 2026-11-18 裁决（四份众审查人报告逐行裁决、不改方案大体方向；新节点×多上下文×多功能面
> 真机矩阵；上一波收尾契约缺陷次级收口）。本段两行 = 上一轮审查两条新发现，原建议 `W23-07`/`W23-08`
> 因现役 W23 已关波（段内只到 06）作废，逐字迁入新一波首两行、保留「← 原建议」追溯注记。
> 收敛结论见 `docs/analysis/w24-acceptance.md` §1.1 #2/#11/#14 与 §3。
> 🔴 两行均为**推导**（外审按 shader 源码 + 测量语义 / SSE 源码静态回放），本波真机未坐实 ⇒ 状态 `OPEN`、
> **不写成已复现事实**；触发信号命中并按必红变异坐实后，按台账 §三.3 改 `CLOSED`。
> 修复需 `.kt` 且必须先拆三方（`feature-chat` 余 4 / `feature-settings` 余 5 / `core-context` 余 4，
> 禁调 `LINE_CAPS`）；本波零 `.kt`，只落笔不修。
> 本段 3 行对 G20 并集零贡献（沿用「G20 `PASS` ≠ 本波新增行已被守卫覆盖」边界注记）。
> 落笔纪律：台账由主理人**单人**落笔；本波 handoff 严格零复合编号字面量。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W24-01 | 深度思考展开框全黑（双层 THIN 玻璃嵌套 + 展开首帧零尺寸进 AGSL + 浅色主题黑底黑字；← 原建议 W23-07） | W24 | `OPEN` | **触发信号**：真机浅色/深色主题 × 收起/展开任一含 `reasoning` 的气泡，展开后出现整块无文字黑面板；或 JVM 侧 `Size(w,1)` / `Size(0,0)` 进 lens shader 复现 NaN；或浅色主题下截图采样「背景亮度 < 阈值 且 文字色同为深族」同时成立。**本波真机实况（不写成复现）**：NV-VL reasoning 气泡在浅色主题收起/展开 3 帧均为浅米底 + 深色可读文字、逐字可读，未现全黑；深色主题未测 ⇒ 保持 `OPEN`，不证伪不销账。**缓解动作**：展开态改无 backdrop 的实底容器（按 `isDark` 取 `glassTintElevated`）+ 文字走 `asOverlayColors()` 恒白族；`AnimatedVisibility` 移出玻璃节点内部，或给容器 `heightIn(min=1.dp)`；`Lens.kt` 加 `size.minDimension < refractionHeight*2` 退纯 blur 守卫、AGSL 全部 `normalize` 加 epsilon；保留玻璃的方案则给 ThinkingBlock 传 `overlayPanel=true`（现役该形参未接线）。**验收判据（带必红变异）**：浅色/深色 × 收起/展开 4 态真机截图，中心像素亮度与文字对比度不得同时低于阈值；`expandVertically` 首帧逐帧截图非纯黑；JVM 补 `Size(0,0)`/`Size(w,1)` 不产生 NaN 的纯函数断言，注入零尺寸样本必须判红。 | `core-design/src/main/java/com/rickeal/liquidharness/core/design/GlassSurface.kt:279-285`（外层气泡 `.liquidGlass(THIN)`）、`:344-422`（ThinkingBlock：内层 `LiquidGlassSurface(THIN)`、`AnimatedVisibility(expandVertically)`、文字用 `onGlassMuted/onGlassSubtle`）；`core-design/src/main/java/com/rickeal/liquidharness/core/design/GlassMaterial.kt:42-59`；`core-design/src/main/java/com/rickeal/liquidharness/core/design/liquid/effects/Lens.kt:66`；`core-design/src/main/java/com/rickeal/liquidharness/core/design/liquid/Shaders.kt:88-104`；`core-design/src/main/java/com/rickeal/liquidharness/core/design/GlassColorScheme.kt:61-68/:98-100/:133-134`；本波真机 `_w24/shots/w2401_expand_f1.png`/`f2`/`f3`/`w2401_collapsed.png`（浅色未复现，仓外取证） |
| W24-02 | 模型拥堵时不重试 + 静默断词（EOF 无终帧仍 `Completed`、SSE error 帧被丢弃、`Truncated` 报告在 UI 被丢；← 原建议 W23-08）（W31 行内注记：W30 真机顺带观察 E02/E03 62.7s 超时、done=false、deviceLog 抓到部分 sentinel、UI「网络抖动，正在重试」——非完全静默、半截内容 + 无终帧 ⇒ 与断词路径相关，按外审处方登记为顺带观察、不写成复现；MockWebServer 红态用例仍为必备，不得用真机替代） | W24 | `CLOSED` | **触发信号**：MockWebServer 返回 `200` + 若干 `delta` 后直接关流 ⇒ 期望 `Failed(retryable=true)` 且请求数 ≥2，现役返回 `AttemptOutcome.Completed(FinishReason.LENGTH)`；或真机遇 429/排队后回答半截、`ChatUiState` 无 `errorMessage`、无「重试本轮」入口；或 SSE `{"error":{...}}` / `retry:` 帧后无任何上报。**本波真机实况（不写成复现）**：dots / north openrouter 免费节点本轮首请求 50–102s 无产出、UI 显示「正在等待模型响应 / 已停止（本轮没有产出内容）」，未见半截回答后静默；未跑 MockWebServer 前端 ⇒ 保持 `OPEN`，限流/无响应归环境侧不归因代码。**缓解动作**：① `runAttempt` 在 EOF 后判 `!state.sawTerminalMarker && state.finishReason == null` ⇒ 返回 `AttemptOutcome.Retryable(NETWORK/SERVER)` 或 emit `Failed(retryable=true)`；② `decodeEvent` 识别 `error` 帧与 `SseEvent.event/retryMillis`，映射 `RATE_LIMIT/SERVER` 并把 `retryMillis` 透传 `backoffMillis`（`HttpTransport.kt:185` 的 `retryOnConnectionFailure=false` **不改回**，重试只走策略层）；③ `EventReducer.reduce` 的 `Finished` 分支消费 `event.report`，`Truncated` 映射成状态区可见提示 + 重试入口（中文文案走 `strings.xml`）；④ 已吐正文/思考不硬重发（避免两段拼接），用现成 `AgentEvent.StreamReset` + `RunState.truncateText` 做显式整段回滚重发，或给可点重试并复用同一 `idempotencyKey`；⑤ 首字节 TIMEOUT 维持不重试，仅在「未吐字 + 429 / `Retry-After` / SSE `retry:` 明确指向排队」时允许 1 次快速重试或切备用端点。**验收判据（带必红变异）**：200+delta+关流 ⇒ `Failed(retryable=true)` 且请求数 ≥2、**不得** `Completed(LENGTH)`；error 帧 ⇒ 被识别为 `RATE_LIMIT/SERVER`；`Finished(report=Truncated)` ⇒ `ChatUiState` 出现可见提示（不是 `status=null`）；首字节超时未吐字 ⇒ 有重试或可点重试入口；真机 429 / 排队两支复现留 `ChatStatus.RETRYING` 截图 + `agent-0.log`。 | `core-net/src/main/java/com/rickeal/liquidharness/core/net/SseEventParser.kt:208-219/:11-16`；`core-engine/src/main/java/com/rickeal/liquidharness/core/engine/RemoteLlmEngine.kt:218-245/:249-261/:206-214/:327-353`；`core-engine/src/main/java/com/rickeal/liquidharness/core/engine/provider/OpenAiCompatibleAdapter.kt:134-195/:197`；`core-engine/src/main/java/com/rickeal/liquidharness/core/engine/provider/ProviderAdapter.kt:189-193`；`core-engine/src/main/java/com/rickeal/liquidharness/core/engine/RetryPolicy.kt:56-68/:77-91`；本波真机 `_w24/shots/e1_dots_reply.png`/`north_wait.png`（限流/无响应观察，仓外取证）  **W34 注记**：W33 的 `giving up after 1 retries: empty output` 是**首字节前空输出**路径（非「吐字后关流」）；设备侧原始 tail 未留存（W34 复核 `/data/local/tmp` 无 W33 代 logcat、`evidence/raw/w33/` 0 命中）⇒ 只作顺带样本、不作机器可复核证据；MockWebServer「200 + 若干 delta 后关流 ⇒ `Failed(retryable=true)` 且请求数 ≥2」红态用例仍必备。  **W39 销账**：三处全修 —— ① `RemoteLlmEngine.runAttempt` 两条断流通路（循环后 EOF / `IOException`+`hasEmittedAny`）改判 `Retryable(NETWORK, truncatedStream=true)`，零事件 200 仍 `Fatal(PROTOCOL)`；② `ProviderAdapter.decodeFailure` 默认 null + OpenAI 兼容实现映射（`event: error` / 顶层 `error`；rate/429、overload/5xx 可重试，其余 fail-closed），命中即 break 且先 `emitFlushed`，终帧后 error 帧忽略；③ `EventReducer.Finished` 读 `report?.kind == TRUNCATED_KIND` ⇒ `ChatStatus.TRUNCATED`，正常结束回落 null（防永久提示）。内核 `prepareRetry` 对 `truncatedStream` 先判预算、再按 `truncateText → StreamReset → Retrying` 顺序整段回滚重试一次；放弃路径不回滚、无产出不发 `StreamReset`、usage 不回滚。红态用例：`RemoteLlmEngineStreamFailureTest` 8 条（E1 在未修版本先跑出 `Completed(LENGTH)`，原文 `_w39/e1-red.log`）、`GenerationRoundTruncationRetryTest` 4 条、`StreamTruncationRetryTest` 2 条（真 HTTP：hits==2、终态唯一且非 LENGTH、文本拼接==第二段）、`EventReducerTest` +2；`EXPECTED_TESTS 829→845`。 **W39 本波复核指路**：`docs/analysis/w39-acceptance.md` §步 2 五项 + `_w39/engine-tests.log`/`agent-tests2.log`/`data-tests2.log`/`chat-tests3.log` 实跑输出在案，状态 CLOSED。  **W40 案 A 注记（用户裁决：不回开；行内收窄原契约③，状态维持 CLOSED）**：③「可见提示 + 重试入口」收窄为「显式 `finishReason=LENGTH` 的提示（W7 时代老债；断流形态走整段回滚重试，重试成功 ⇒ `Finished(ModelStopped)` 且 report==null、两次都断 ⇒ `AgentEvent.Failed` 走 `fail()`，`ChatStatus.TRUNCATED` 在拥堵断流路径**结构不可达**——这是设计结果、不是缺陷）；`strings.xml` 删「或被中断」（TRUNCATED 状态实际只由显式 LENGTH 触发，文案不得暗示断流会产生该状态）；「显式 LENGTH ⇒ TRUNCATED」端到端用例设计（引擎 E4 已钉 Completed(LENGTH)，UI 侧已有 `EventReducerTest` 两条件注入用例，端到端形态随 W40 第 1 车落位）。CLOSED 行已知边界（原契约②⑤残余）：`retry:` 纯帧 / `retryMillis` 零消费（`SseEventParser` 丢弃）；重试入口未做（放弃路径显示失败文案，无「重试本轮」按钮）；error 帧已吐字后不重试 + `truncateReasoning` 未做（W40 第 1 车处理，见 W40 段）。 |
| W24-03 | journal / FreezeClock 二选一裁决 **第 19 次顺延**（承接上一波段第 3 行，两代数口径仍并列待用户裁决；原 W24 交接声称本波段已登记但台账零行，W25 主理人单人补行消除账实不符）（W31-03 行内注记：顺延代数多值并存〔19/20/22 等〕，显式留痕「多值待裁」，以用户二选一裁决收口） | W24 | `OPEN` | **触发信号**：用户未给显式二选一裁决；**缓解动作**：用户拍板二选一并统一代数口径；不关号、不软化、不代裁决；累计成本注记以行内注记零成本并入。第 20 次顺延 W25 波段不新开行、留痕待后续 | `docs/analysis/w24-acceptance.md` §1.1 #12 / §5.2 #2；`docs/analysis/w25-acceptance.md` §1.1 #2/#12、§5.3；老号原样 `OPEN` |

### W27 段（2026-11-18，3 行：3 `OPEN`）

> 上游依据：用户 2026-11-18 指令（克隆最新 main、四报告分歧裁决、挂账分析、下一波修补建议）+ 四份审查报告。
> 落笔前置实跑：本段落笔前 `grep -cE '^\| W27-'` = **0**（号段自由）；W26 段 5 行属 W2.6 段、上轮撞号证伪不重犯。
> G20 无 ID 唯一检查（`arch-guard.sh:1342/1356` 两边 `sort -u` 只做存在性比对）⇒ 靠本段「人工闸门 + 必红变异」兜底，改守卫属用户裁决域。
> 边界：3 行全零 `.kt`、第 0 车纯文档/制品可兑现；需 `.kt` 的拆分三方 / B1 压缩 / 两条用户可见缺陷修复整包顺延（挂老号，不新开行）。
> 六项用户终局裁决只留痕不代做；3 行对 G20 并集零贡献（本波 handoff 严格零复合编号）。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W27-01 | 脱敏回退 + 收尾契约第 8 次 + 跨提交 tip run 缺口收口 | W27 | `OPEN` | **触发信号**：外部审查按上一波验收物作历史结论复核；或开波时全树 tip run 仍 0 命中（连续同型）；或凭据文件名形态再回退到验收物。**本波第 0 车已做（零 `.kt`，逐条指到 `文件:行`）**：① 删上一波验收物「0 命中」格里的凭据文件名字面量、保留「6 链接无 key 字面量」语义；② 跨提交补两条 tip run 至各自 §5.6 推送 2 行 + 文末链接段（公开 API 四字段复核 success）；③ step5「完成」改「闸门✅ + 推送 1 CI / tip run 见 §5.6」；④ G1/G4 模块数 12→14 统一；⑤「五项」改「六项」（3 处）；⑥ §1.2 #11「收口」与 §5.4「只登记」加同义订正注记。⑦ W23-01 注记悬空改指真实小节。⑧ G20 无唯一检查的裁决表述订正为「人工号段预检 + 必红变异」。**仍未闭（故 OPEN）**：tip run 递归死结闭环方式属用户终局裁决域，本波只登记不代裁；「脱敏正控缺失」经回源不成立（外部扫描工具自证 11/11 会响），报告该子项证伪。**出口判据（带必红变异）**：全树凭据文件名形态 0 命中且正控注入该串判红；两条 run 各 ≥1 命中且分别指向各自 §5.6 推送 2 行；注入「删 tip run 行」样本 ⇒ 收口 checklist 判红。 | `docs/analysis/w26-acceptance.md` `:22`/`:46`/`:88`/`:140`/`:152`/`:155`/`:233`/`:243`/`:284`/`:306`/`:315`；`docs/analysis/w25-acceptance.md` `:232`/`:318`/`:339`/`:356`；`docs/analysis/w24-acceptance.md` F2/F6；台账 `W23-01` 注记；公开 API run `44234213197`@`7cc5cb9c`、`44156141650`@`da967be`（status/conclusion/event/head_sha 四字段齐） |
| W27-02 | 制品 schema 反向退化与「空转判据」 | W27 | `OPEN` | **触发信号**：外部审查按仓内唯一制品复核「必红变异」「逐格四态」「面覆盖」；或换机/清目录后仓外生成器不可再生；或下一波把上一波制品当同 schema 解析失败。**本波第 0 车已做（零 `.kt`，只落本波新制品，历史制品不重出）**：本波 `evidence/w27-matrix-summary.json` 补回 8 字段（`assertion_source`/`runner_fingerprint_prefix16`/`na_allowlist`/`truncated_fields(_note)`/`attempt_states`/`coverage_note`/`artifact_discipline_fixes`）、schema 版本串归一；统一 `state_counts` 与 `cells[].state` 枚举域并加钉桥断言；`feature_face` 越界变体收编或登记合法变体并加面枚举断言；`mutation_cases[]` 每条带可重放样本 + 实测输出/退出码/样本哈希，元断言与真机断言分组标注。**出口判据（带必红变异）**：state_counts 键集 = cells[].state 值域（或钉桥断言存在且注入错配判红）；每个 feature_face 值可在 declared 或变体表定位；每条 mutation 的注入样本可在本体重放；注入「把某条 verified 置 false 而 selftest 仍报全绿」样本 ⇒ 出厂断言判红。**未闭（故 OPEN）**：生成器仍在仓外（老号 W20-14），11/11 必红仍须区分规则自证与真机重放，不自报为执行。 | `evidence/w26-matrix-summary.json`（顶层键退化、`state_counts` 双轨、`feature_face` 越界值、`mutation_cases[0].note`）；`evidence/w25-matrix-summary.json`（8 字段先例）；`docs/analysis/w26-acceptance.md` §1.1/§5.5；台账 `W20-14`/`W21-02`/`W22-02` |
| W27-03 | 挂账号段机制 + G20 盲区 + 自动压缩「两把尺子」入账 | W27 | `OPEN` | **触发信号**：下一波开号前未扫全表号段（隔波段撞号风险）；或有人把 `G20 PASS` 读成「新增行已被守卫覆盖 / ID 唯一已检查」；或转代码波时压缩缺陷找不到红态入口。**本波第 0 车已做（零 `.kt`）**：① 落笔前全表行首号段扫描留痕（W27 零占用）；② F6「token 维越预算 30.8K/27.9K、`committedChars` 字符维未越阈值 50227 ⇒ `auto_compact=noop below_trigger`」作为两把尺子真机实锤的观察留本波验收物 §3/§4（**W28 订正：原「写进压缩老号行内注记」系零 diff 虚报，案 B 作废该处方，压缩老号维持原样零 diff、不设红态入口**）；③ 本波真机复跑字符维口径，如实登记。**仍未闭（故 OPEN）**：checklist 人工闸门两条（新开号段必扫全表行首占用 / 验收物挂账号台账须有独立行）尚待外部审查确认可操作；六行漏号与 journal 代数多值并存属用户裁决域，本波不代裁、不并号、不写统一值。**出口判据（带必红变异）**：注入「台账重复 W27-01 行」样本 ⇒ checklist 判红（G20 仍绿、人工兜底须明写）；压缩老号行维持原样零 diff（案 B：below_trigger 为正确行为、非缺陷，不设红态入口）；「token 维越 / 字符维未越」观察留验收物 §3/§4。 | `docs/06-deferred-ledger.md` `:6`/压缩老号行/段头；`scripts/arch-guard.sh:1327-1362`（g20 全文）；`docs/analysis/w26-acceptance.md` §3 F6 / §5.3；本波真机 §3/§4 |

### W28 段（2026-11-18，3 行：3 `OPEN`）

> 上游依据：用户 2026-11-18 指令（克隆最新 main、深度审查上次改动、挂账分析、下一波修补建议、多模型×多上下文×多功能面真机矩阵）；本轮四外审对 `df8b482c5`→`cdaade7a4` 的逐文件复核结论。
> 落笔前置实跑：全表行首编号扫描 `W28` **0 行**（`W26`:5 属 W2.6 别名段、`W27`:3 为本波上一段），号段自由留痕；G20 无 ID 唯一判据 ⇒ 人工号段预检 + 必红变异兜底，改守卫属用户裁决域。
> 边界：3 行全零 `.kt`、纯文档 + 制品可兑现；需 `.kt` 的拆分三方 / B1 压缩结构 / 两缺陷修复整包顺延（挂老号，不新开行）。
> 六项用户终局裁决只留痕不代做；3 行对 G20 并集零贡献（本波 handoff 严格零复合编号）。
> 落笔纪律：台账由主理人**单人**落笔，外部审查草案不直接改真值源。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W28-01 | 「两把尺子」二选一落地（案 B）+ 第 6 次虚报销账 + 收尾契约第 9 次（① W27 三处〔验收物 §1.1 #9 / handoff 压缩老号行 / 台账 W27-03〕共同声称把 F6 实锤「写进 W9-01/W23-04 行内注记作下一代码波红态入口」，实跑 `W9-01`(:147) 仅 W11 标定面「不是两把独立的尺子」、`W23-04`(:458) 对 token/字符/红态零命中 ⇒ 第 6 次零 diff 虚报；② 同一波把该现象判「below_trigger 正确行为、非缺陷」又采纳外审「红态入口」，口径自相矛盾；③ tip `cdaade7` 的 Secret scan `44241199431` success 但全树 0 命中、W27 §5.1 步 5 判 ✅「推送后回填」） | W28 | `OPEN` | **触发信号**：外部审查按 W27 三处「已入账」表述回查台账；或用户问「两把尺子到底是不是缺陷」而文档与台账给相反口径；或开波时 tip run 闭环仍悬而未决。**缓解动作（本波第 0 车已做，零 `.kt`）**：案 B 落地——三处同步删「已写进 / 红态入口」措辞、改为「below_trigger 正确行为、实锤留验收物、不写进压缩老号、不设红态入口」；`W9-01`/`W23-04` 维持 `OPEN` 原样零 diff（四外审同一缺陷禁拆多条）；补 `44241199431`+`cdaade7` 进 W27 §5.6 推送 2 行 + 文末链接段、§5.1 步 5 改口径。**仍未闭（故 OPEN）**：tip run 闭环方式属用户终局裁决域，本波最后一次逐波登记，仍不拍板则下一波降级挂老号不再逐波判 P0。**出口判据（带必红变异）**：全库「写进压缩老号 / 红态入口」措辞 0 命中（W28 订正注记除外）；`W9-01`/`W23-04` 对 F6 两把尺子零 diff；`44241199431`/`cdaade7` 各 ≥1 命中且指向 W27 §5.6；注入「删 tip run 行」或「只改验收物不改台账」样本 ⇒ 收口 checklist 判红（已建 `docs/notes/w28-closeout-checklist.md`，必红变异实跑会响）。 | `docs/06-deferred-ledger.md` `W9-01`(:147)/`W23-04`(:458)/`W27-03`；`docs/analysis/w27-acceptance.md` §1.1 #9/§5.1 步5/§5.6/文末；`docs/handoff-20261118-wave27.md` 压缩老号行；`docs/notes/w28-closeout-checklist.md` 判据3；公开 API run `44241199431`（success@`cdaade7`，四字段复核）。**W30 波行内注记（外审 W30-03 处方）**：本行出口判据「全库『写进压缩老号/红态入口』0 命中」按字面不成立（W29 实测 15 处命中、括号「除外」无清单）；承载载体 = W29 checklist 判据 4（三处白名单 grep + 必红变异），W30 实跑必红输出 `RED affirmative wording remains`；原结论与状态不动，行内注记留痕（台账 §三.6 单人落笔）。 |
| W28-02 | W27 制品出厂断言空转与判据覆盖退化（① W27 制品 `state_counts={'PASS':8,'PARTIAL':0,'VACUOUS':3,'FAIL':0}` 而 `cells[].state` 值域只有 `{'PASS','VACUOUS'}`；`K_state_domain` 必红样本「注入 PARTIAL 零值键」与现役同型却自报 5/5 REJECTED；② W26 11 条断言含 6 条真机面，W27 只剩 5 条且全为 `artifact.self_check` 元断言，真机覆盖 6→0；③ `injected_sample` 自然语言、`sample_hash` 12 hex、`assertion_source` 仓外生成器；④ `na_allowlist=['F7','W24-01','W24-02']` 而 NA 格只 F7、无 `K_na_allowlist` 断言 ⇒ 白名单恒真） | W28 | `OPEN` | **触发信号**：外部审查按仓内唯一制品重放「必红变异」；或把 `mutation_selftest 5/5` 读成真机矩阵被验证；或下一波沿用 W27 schema 时空转判据复发。**缓解动作（本波第 0 车已做，只改本波新制品、历史制品不重出）**：本波 `evidence/w28-matrix-summary.json` 删零值键或改 description「非零键集=值域」并同步必红样本；补回真机面断言或 `coverage_note` 明写零覆盖；`injected_sample` 结构化 `{field,old,new}` + 64 位哈希或登记仓外不可重放；加 `K_na_allowlist` 收幽灵项；`mutation_selftest` 保留「规则自证 ≠ 真机重放」分组标注。**出口判据（带必红变异）**：`state_counts` 非零键集 = `cells[].state` 值域且必红样本不与现役同型；真机面断言数 ≥1 或 `coverage_note` 明写零覆盖；每条 `injected_sample` 结构化；注入「把某条 `verified` 置 false 而 selftest 仍报全绿」或「`na_allowlist` 加不存在格」样本 ⇒ 出厂断言判红（checklist 判据2 实跑会响）。 | `evidence/w28-matrix-summary.json`；`evidence/w27-matrix-summary.json`（空转形态先例）；`docs/notes/w28-closeout-checklist.md` 判据2；台账 `W27-02`/`W20-14`/`W21-02` |
| W28-03 | 挂账出口判据载体落地 + 状态/判据自洽 + 老账发散（① W27 段三行出口判据依赖「收口 checklist 判红」「样本可在本体重放」，但全仓文件名含 checklist 的只有 W4 闸门清单、W27 未新建收口 checklist，生成器在仓外；② `W27-01` 出口判据前两条本波已满足、第三条引用不存在 checklist，行内「本波第 0 车已做①…⑧」其中 ⑧ 虚报 ⇒ 状态与判据不自洽；③ 裁决合计括号列 7 个号却写「中 6 项」；④ `W24-03` 第 21 次顺延台账仍记 19、六行漏号第十一波、G10 贴顶零 `.kt`） | W28 | `OPEN` | **触发信号**：有人把 `G20 PASS` 读成「新增行已被守卫覆盖 / ID 唯一已检查」；或外部审查想执行 W27 三行任一出口判据却找不到可跑命令。**缓解动作（本波第 0 车已做，零 `.kt`）**：新建 `docs/notes/w28-closeout-checklist.md`，三条出口判据逐条对应可跑命令 + 必红变异（判据1 号段唯一性/判据2 制品非空转/判据3 tip run 回填，均已实跑：必红变异输出 RED、正常态 OK）；W27 段「已做 / 尚待确认」措辞冲突如实留痕于本行、原行不动（不声称已订正原行）；裁决合计/代数多值如实留痕不代裁。**仍未闭（故 OPEN）**：六行漏号与 journal 代数属用户裁决域不代裁；checklist 是人工闸门 + 必红变异兜底、非机器守卫，三条挂账改 `CLOSED` 须**仓内判据实跑输出入账 + 制品与真值源一致**后按台账 §三.3 单人落笔（**W30 波按外审 W30-03 处方订正**：删原「须真机取证」错配条件——三条出口判据全是仓内文档/制品命令，与真机无关，按原条件机制性不可销账）。**出口判据（带必红变异）**：仓内存在可被 `grep`/`bash` 执行且输出判红的收口清单，三条出口判据各能指到命令与输出（已建并实跑）；注入「台账重复 W28-01 行」样本 ⇒ 清单判红（G20 仍绿、人工兜底已明写）；注入「只改验收物不改台账」样本 ⇒ 清单判红。 | `docs/notes/w28-closeout-checklist.md`（新建，三判据 + 必红变异实跑）；`docs/06-deferred-ledger.md` W27 段/`W24-03`/`W9-05`/`W20-08`；`docs/analysis/w27-acceptance.md` §5.1/§5.4；本环境实跑 W28 段 0 行 / 挂账号台账独立行全 OK / tip run 2 命中 |


---

## 二、统计（按 §一 表格实际编号行重算，W7）

> 原为「W6 建立时」快照：`CLOSED` 9 / `CARRIED` 6 / `OPEN` 24 / 合计 39。W7 依主理人裁决
> 「以表格实际编号行为准」重算：W6-01、W6-07 关闭，W7-05 / W7-06 为快照后补，W7-07 由 CI 席新开 ⇒
> 下表 **11 / 7 / 24 / 42**。快照数字不再作为状态来源，判状态只认 §一 表格行。

| 状态 | 条数 | 条目 |
|---|---|---|
| `CLOSED` | 16 | W26-01 / W26-04 / W26-05 / W3-08 / W4-01 / W4-05 / W4-06 / W4-07 / W5-01 / W6-01 / W6-04 / W6-07 / W7-01 / W7-04 / W7-07 / **W3-07**（本车同提交回填，证据列改指现役三处同改） |
| `CARRIED` | 11 | W26-03 / W3-03 / W3-05 / W3-06 / W4-02 / W4-03 / W4-04 / W7-05 / W8-01 / W8-02 / W8-03 |
| `OPEN` | 19 | W26-02 / W3-01 / W3-02 / W3-04 / W5-02 / W5-03 / W5-04 / W5-05 / W5-06 / W5-07 / W6-02 / W6-03 / W6-05 / W6-06 / W6-08 / W7-02 / W7-03 / W7-06 / W8-04（本波移出 1 条转 `CLOSED`） |
| **合计** | **46** | §一 表格实际编号行（W9-00 重算）：W3-07 `OPEN`→`CLOSED` 只改状态、不增删行 ⇒ 合计仍 **46**。W9 承接表在 §二-bis，按现役说明**不计入 §一 行数**；本车新开 `W9-12` 与 `W9-00` 口径登记行均在 §二-bis，**均不计入本表** |

> 🔴 **主理人原派单口径冲突，CI 席回树实跑订正（裁决 4，2026-11-06）**：原派单两处数字
> 互相矛盾，均**按原文留痕、不抹掉任一方**：
> - 裁决 D 原文「合计 **46 → 47**、`OPEN` **20 → 21**」；
> - 必须落笔 §2 原文「`CLOSED` **15→16**、`OPEN` **20→20**（W3-07 移出）、**合计仍 46**」。
>
> 主理人裁决 4 明确「我的原派单有误，以本条为准」：§二 统计表**按既有口径只统计 §一 表格行**
> （表头自述「按 §一 表格实际编号行重算」）⇒ **`CLOSED` 16 / `CARRIED` 11 / `OPEN` 19 / `合计` 46**
> （16+11+19=46 校验通过）。CI 席上一版误把 `W9-12` 并入统计表落 47，与「§二-bis 不计入 §一 行数」
> 的既有说明冲突，已按裁决 4 回退；两处原文数字及该次误落均保留在本注，不静默取舍。
>
> **编号行口径说明（数字来自 CI 席实跑，不采信估算）**：
> `grep -cE '^\| W[0-9]+-[0-9]+ \|' docs/06-deferred-ledger.md` = **59 个编号行**；
> 其中 **§一 46 行** / **§二-bis 13 行**（W9-01..11 原有 11 行 ＋ 本车新增 `W9-12` 挂账行 ＋
> `W9-00` 口径登记行）= 59。§二-bis 的 13 行**均不计入 §二 统计表**；
> `W9-00` 为「本波口径登记行，非挂账项、不计入 §二 统计表，仅为满足 G20 的 ID 存在性」。
> G20 只校验「handoff ID 在台账有行 / `CLOSED` 行证据非空 / 状态值合法」，**不校验统计数字**。

**老化最久的三条**（都必须有归宿，不许无限期挂着）：

- **W3-05**（老化 4 波：W3 / W4 / W5 / W2.6）⇒ 归宿 = 独立 W6.5 波（journal v1 + FreezeClock +
  前台服务 + 恢复卡，一个整体交付）。它是「跑不死」这条线的唯一支柱，被「能跑更多东西」
  这条线挤掉了四波。
- **W3-02**（老化 3 波）⇒ W6 C 流可选档；本波不做则**显式裁决「不做」**，不许继续挂着。
- **W3-07**（老化 3 波）⇒ W6 C 流可选档；同上。⚠️ **本车 W9-00 已 `CLOSED`**（三处同改，见 §一 W3-07 行）；此条保留作老化历史留痕，不删不改。

> ⚠️ 本段刻意写成**列表而不是表格**：G20 按「首列是 `W<数字>-<数字>` 且共 6 列」识别台账行，
> 任何以 ID 开头的多列表格都会被它当成台账行去校验列数。要么写成列表，要么首列别放裸 ID。

---

## 三、维护纪律

1. **新开一条挂账 = 同时在本文件加一行**。只写进 handoff 的表 = 下一波必丢（这是本文件存在的全部理由）。
2. **换号承接时**：老行改 `CARRIED` 并在「阻塞/关闭于」写新号，**不删行**。删行会让 G20 判红 ——
   那是**故意**的：丢一条就该红。
3. **`CLOSED` 必须能指到证据**。指不到就写 `OPEN`。「代码看起来有了」不是证据，
   「`文件:行` / 提交号 / 测试输出」才是。
4. **不许为了让 G20 变绿而删 handoff 里的 ID**：handoff 是历史记录，改它等于篡改开立事实。
5. **`VACUOUS` 不是通过**。G19 现在就是 `VACUOUS`（守空集），它出现在输出里是为了
   让「装了烟感但没火」可见；`VACUOUS_BASELINE` 只许减。
6. **本文件只由 CI 流（单人）落笔**。其他流**只在 handoff / 消息里报「要开一条」**，
   由 CI 流统一编号并入表。**为什么**：W6 同一晚**重复编号两次**（`W6-01`、`W7-04`），
   两次都是并行流各按自己的计数起号。
   🔴 **保证强度（如实）**：这条是**约定，不是判据** —— 守卫判不出「谁写的」。
   G20 只能判「引用存在、`CLOSED` 有证据」，判不出「两个 `W6-01` 指两件事」。
   想让它可判，得另加「ID 唯一」判据（W6 收口期已加检查，自证见 `scripts/arch-guard.sh`）。
7. **恒绿断言不是判据**。`assertTrue(true)` 一类的占位**永远不可能红**，它只能证明「test 源集非空」。
   新增占位等于给计数锚注水 —— 本仓 `EXPECTED_TESTS` 因此只有「计数」含义，没有「判据数」含义（见 W7-04）。


### W29 段（2026-11-18，3 行：3 `OPEN`）

> 上游依据：用户 2026-11-18 指令（克隆最新 main、读记忆/上一波 handoff/挂账行、四报告分歧裁决不改方向、多模型×多上下文×多功能面真机矩阵、收尾契约）；外审《W28 深度审查与 W29 排期裁决》对 `733560501`→`f0997580e` 的逐文件复核；《W29 段挂账草案》（3 行，号段预检）。
> 落笔前置实跑：全表行首扫描 `W29` **0 行**（`W26`:5 属 W2.6 别名段、`W27`:3、`W28`:3），号段自由、无撞号（对照 W26 撞号前车）；G20 仅存在性比对无唯一检查 ⇒ 人工号段预检 + 必红变异兜底，改守卫属用户裁决域。
> 边界：3 行全零 `.kt`、纯文档 + 制品第 0 车可兑现；需 `.kt` 的拆分三方 / B1 压缩 / 两缺陷修复整包顺延（挂老号不新开行）；六项用户终局裁决只留痕不代做。
> 3 行对 G20 并集零贡献（本波 handoff 严格零复合编号）；台账由主理人**单人**落笔。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W29-01 | 执行 tip run 降级 + 收尾表占位 + W28-01 出口判据可执行化 + G20 表述回改（① W28 §1.1 #6 自设「W29 起降级为 W20-08 行内注记、不再逐波判 P0」，用户本轮未拍板 ⇒ 条件成立；tip `f099758` 的 Secret scan `44251600099` success 但全树 0 命中；② `w28-acceptance.md` §5.3/§5.4 五处「待」占位未删、`new files 2` 实为 4；③ `W28-01` 出口判据「全库措辞 0 命中」实测 15 处命中、括号「除外」无清单 ⇒ 不可执行；④ `w26-acceptance.md:46`「经 G20 ID 唯一检查」原句仍在） | W29 | `OPEN` | **触发信号**：外部审查按 W28 §5.3/§5.4 作历史结论复核；或开波时 tip run 仍 0 命中却已被宣告「最后一次逐波登记」；或用户问「W28-01 到底销没销账」而出口判据按字面不成立。**缓解动作（本波第 0 车已做，零 .kt）**：`44251600099`+`f099758` 补进 W28 §5.6 推送 2 行与文末链接段、`W20-08` 行注记写「闭环方式未裁决，自本波起不再逐波判 P0、只随交付消息给出」；删五处「待」占位、`new files 2` 改 4；W28-01 出口判据改「按文件白名单三处原句不再含肯定式措辞」+ grep + 必红变异（见 W29 收口 checklist 判据 4）；`w26-acceptance.md:46` 加行内订正注记。**出口判据（带必红变异）**：`44251600099`/`f099758` 各 ≥1 命中且指向 W28 §5.6；`W20-08` 含「不再逐波判 P0」注记；W28 验收物「待落笔/待真机回填/待生成」0 命中；注入「把肯定式措辞写回三处原句」⇒ checklist 判据 4 输出 RED（本波实跑会响）。 | `docs/analysis/w28-acceptance.md`（五处占位已清 / §5.6 补链）；`docs/06-deferred-ledger.md` `W20-08`/`W28-01`；`docs/analysis/w26-acceptance.md:46`；`docs/notes/w29-closeout-checklist.md` 判据4；公开 run `44251600099`（success@`f099758`，四字段复核） |
| W29-02 | 收口 checklist 判据 2 空转 + 三条判据可执行性（① W28 checklist 判据 2 正常态不读 `mutation_cases[].verified`/`mutation_selftest.all_verified`，注入 `verified=false` 仍 OK、必红变异 0 代码块；② 判据 3b 占位符吞错误无 OK/RED；③ 判据 1a 无 `[ -eq 3 ]` 判红；④ W28-01「三处同步」变异全文 0 命中；⑤ 判据 1 用 `/tmp`、判据 3 禁 `/tmp` 口径冲突） | W29 | `OPEN` | **触发信号**：外部审查重放「必红变异」却不响；或把「三条均已实跑」读成判据被验证；或下一波沿用 W28 checklist 作销账依据。**缓解动作（本波第 0 车已做，只新建 W29 载体不改 W28 原件）**：新建 `w29-closeout-checklist.md`——判据 2 正常态加 `all(verified)==all_verified` 且 `K_positive_control.verified_by=='device_execution'`，给可跑必红代码块（工作区副本置 `verified=false` → RED）；判据 1a 补「行数不等于 3 则输出 RED 判定」；判据 3b 改仓内 `grep -rniE`；新增判据 4「三处同步」；路径统一工作区副本不用 `/tmp`。**出口判据（带必红变异）**：注入 `verified=false` ⇒ 判据 2 输出 RED（本波实跑会响，见验收物 §5.4）；注入肯定式措辞回写 ⇒ 判据 4 输出 RED；四条判据各有可跑代码块、无占位符、无吞错误管道。 | `docs/notes/w29-closeout-checklist.md`（判据1/2/3/4 + 可跑必红块）；`evidence/w29-matrix-summary.json`（`mutation_cases[].verified` / `mutation_selftest.all_verified` / `invariant_all_verified`）；台账 `W28-02`/`W28-01`/`W20-14` |
| W29-03 | W28 制品 schema 第三次反向退化 + 迭代数三值 + 边界/编号订正（① `evidence/w28-matrix-summary.json` 丢 `runner_fingerprint_prefix16`/`attempt_states` 逐格/`iterations[]`，而 W28-02 缓解动作只写「P0-2 四项」零登记；② `iterations_total=9`/明细 0/§4 表 11 行/§5.1「11 轮」⇒ 9/0/11 三值；③ §1.3 #14 边界与实际改两份 W27 文件不自洽；④ G1-G4 编号跨波含义变；⑤ `mutation_selftest.note`「5 条自证 + 1 条正控」措辞；⑥ `W24-03` 第 22 次顺延台账仍记 19、六行漏号第十二波） | W29 | `OPEN` | **触发信号**：外部审查按仓内唯一制品复核迭代轮次；或下一波沿用 W28 schema 时 `iterations[]` 解析失败；或用户问「8 字段补回没有」。**缓解动作（本波第 0 车已做，只新建 W29 制品不改 W28 原件）**：`evidence/w29-matrix-summary.json` 补 `iterations[]` 11 条（非连续 id，末条 `closeout` 防删尾假变异）、`iterations_total`=11 与 §4/§5.1 统一、`attempt_states` 逐格 11/11、`runner_fingerprint_prefix16`=null+note（禁编造，指纹随交付消息）；`artifact_discipline_fixes` 登记三项；`mutation_selftest.note` 改「4 条规则自证 + 1 条真机正控」；边界条款重写、G1-G4 加「本波定义」抬头（在 W29 验收物内）。**出口判据（带必红变异）**：`iterations_total == len(iterations[]) == 11`；`attempt_states` 11/11；三字段顶层键可枚举；注入「删 `iterations[]`」或「`iterations_total` 改 9 而 §4 仍 11」样本 ⇒ checklist/出厂断言判红。 | `evidence/w29-matrix-summary.json`（三字段补回/iterations_total=11/attempt_states 11）；`evidence/w28-matrix-summary.json`（退化形态）；`evidence/w27-matrix-summary.json`（3 字段先例）；`docs/analysis/w28-acceptance.md` §4/§5.1；台账 `W28-02`/`W24-03` |

### W30 段（2026-11-19，3 行：3 `OPEN`）

> 上游依据：用户 2026-11-19 指令（读记忆/上一波 handoff/挂账行、五审查报告分歧裁决不改方向、多模型×多上下文×多功能面真机矩阵、先本地 UI + adb 调试 → 真机 → 推仓库 + CI）；五报告（harness-w29-review / W29-深度审查与挂账分析 / W29 路线建议 / W30 段挂账草案 / W29 深度审查与 W30 排期裁决）对 `1c6e404c5`→`f0b12966b` 的复核。
> 落笔前置实跑：全表行首扫描 `W30` **0 行**（`W29`:3、`W28`:3、`W27`:3、`W26`:5 属 W2.6 别名段），号段自由、无撞号；G20 仅存在性比对无唯一检查 ⇒ 人工号段预检 + 必红变异兜底。
> 边界：3 行全零 `.kt`、只改 W29 现有载体（不新建第 3 份 checklist / 第 4 份制品）；转代码波 / 拆分三方 / journal 二选一 / 六项用户终局裁决整包顺延。
> 3 行对 G20 并集零贡献（本波 handoff 严格零复合编号）；台账由主理人**单人**落笔。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W30-01 | W29 制品跨波复制 W28 设备证据（外审最重发现：`evidence/w29-matrix-summary.json` 的 11 格 cells/iterations/正控全是 W28 制品整格复制，`w29_device_executed=False` 却把 `K_positive_control.verified_by` 写 `device_execution` ⇒ 与 W29 交接「11 格全 ⛔vacuous」矛盾；13 字段逐字相同的格 11/11） | W30 | `CLOSED` | **触发信号**：外部审查按仓内唯一机器可读制品复核本波迭代或四态；或把 `verified_by=device_execution` 读成 W29 真机已执行；或下一波沿用 W29 schema 时 `_w28/` 证据被当本波。**缓解动作（本波第 0 车已做，只改 `evidence/w29-matrix-summary.json` 不新建制品）**：`iterations[]` 换 W29 §4 真实 6 轮本地迭代、`iterations_total=6` 与 §4/§5.1 统一；`state_counts`/`cells[].state` 与 §3 统一为 11 `VACUOUS`，W28 基线事实整格移入 `w28_baseline_facts` 不占本波四态；`w29_device_executed=False` 时 `K_positive_control.verified_by` 改 `inherited_w28_baseline` 禁写 `device_execution`；`assertion_source` 改指本波生成器；删「非连续 id 防删尾」过度声明。**出口判据（带必红变异）**：`iterations_total == len(iterations[]) == W29 §4 数据行数 == 6`；`state_counts` 全 VACUOUS 且 `w29_device_executed=False`；`K_positive_control.verified_by != 'device_execution'`；注入「把某条 `_w28/` evidence_ref 写回」或「把正控标回 `device_execution`」⇒ W29 checklist 判据 5 输出 RED（**W30 实跑真响**，见 checklist §本波必红实跑输出摘录）。 | `evidence/w29-matrix-summary.json`（去 W28 化后：`state_counts={'VACUOUS':11}`/`iterations_total=6`/`inherited_w28_baseline`/`w28_baseline_facts`/`w30_p0_repair`）；`evidence/w28-matrix-summary.json`（复制源）；`docs/notes/w29-closeout-checklist.md` 判据 2/5 （W32 行内注记：缓解动作已落地且外审复核修复完整，本行维持 `OPEN` 不关——按 W30-03 销账条件错配教训，关闭须仓内判据实跑输出入账加制品与真值源一致；此处明示未满足的出口条件，不以沉默挂 `OPEN`。不在本波销账。） **W33 实跑入账**：判据 2/5 正常态与必红实跑（判据2 `OK(vacuous)` 加 `RED mutation invariant broken`；判据5 `OK` 加 `RED iteration/state invariant broken`），出口条件「仓内判据实跑输出入账 加 制品与真值源一致」已满足，仅余「外部审查复核」⇒ 维持 `OPEN`，不代关。  **W34 销账**：三条件齐——W33 实跑入账（判据 2/5 正常态加必红）、外审复核（六份 2026-11-19 报告；a4 试金石明确「跑判据 2/4/5 ⇒ 三行 CLOSED 带输出指路」）、W34 复跑（判据 1 四段 rows=3、判据 2 正常态 `OK(vacuous)` 加必红 `RED mutation invariant broken`）⇒ 改 `CLOSED`。  **W35 本波复核指路**：W34 验收物 §2.4 加 §2.5 实跑摘录在案（8 行同车复核），状态维持 `CLOSED`。 |
| W30-02 | W29 checklist 必红侧闭环（外审 P0-2：判据 2 必红块 `${WS}` 写在 `<<'PY'` 单引号 heredoc 内 ⇒ shell 不展开、Python 收到字面量 ⇒ `FileNotFoundError`；判据 3b 改仓内 grep 后仍无注入样本；判据 4 必红变异 0 代码块；W29-03 出口判据无载体——checklist 全文 `iterations`/`attempt_states` 0 命中） | W30 | `CLOSED` | **触发信号**：外部审查重放「必红变异」却因路径不存在报错；或把「四条判据均已实跑」读成判据被验证；或 W29-03 想执行出口判据找不到命令。**缓解动作（本波第 0 车已做，只改 `docs/notes/w29-closeout-checklist.md`）**：判据 2 必红块 `${WS}` 改 `os.path.abspath(os.path.join(os.getcwd(),".."))` 自算（**W30 实测 `export WS="$(pwd -W 或 pwd)"` 在 Git Bash fallback 路径含换行** ⇒ 改 Python 自算，彻底避开 `$WS`）；判据 2 正常态补三态分支（`w29_device_executed=False` ⇒ 正控须 `inherited_w28_baseline`/`not_exercised`，输出 `OK(vacuous)`）；判据 3b 补注入样本（`printf 'nvi%sdia.txt'` 拼装避免本文件自命中）；判据 4 补可跑必红块；新增判据 5（`iterations_total == len(iterations[]) == §4 行数`、`attempt_states` 键集 = `cells[].cell_id` 值域、vacuous 一致性）；新增「本波必红实跑输出摘录」小节。**出口判据（带必红变异）**：判据 2/3b/4/5 各有可跑代码块、无占位符、无吞错误管道；四条必红样本实跑输出 RED 且摘进仓内（**W30 实跑：`RED mutation invariant broken` / `RED credential filename hit` / `RED affirmative wording remains` / `RED iteration/state invariant broken (5/5/6)`**；正常态：判据 2 `OK(vacuous)`、判据 5 `OK (6/6/6)`）。 | `docs/notes/w29-closeout-checklist.md`（判据1-5 + 必红侧全闭环 + 实跑摘录）；`evidence/w29-matrix-summary.json` （W32 行内注记：必红侧闭环动作已落地、W31 实测判据 1/2/4/5 重放真响，本行维持 `OPEN` 不关；出口条件 = 须 checklist 全量必红样本在终态树双态真响且摘录入仓，W31 的 3b 必红哑火已于 W32 真修，不在本波销账。） **W33 实跑入账**：判据 2/3b/4/5 四条必红全部真响（`RED mutation invariant broken` 加 `RED credential filename hit` 加 `RED affirmative wording remains` 加 `RED iteration/state invariant broken`），正常态 `OK`；出口条件已满足，仅余外审复核 ⇒ 维持 `OPEN`。  **W34 销账**：W33 实跑入账（判据 2/3b/4/5 必红全响）；外审 a1 §1 回归实跑确认 3b 正常态 0 命中、必红样本真响；W34 复跑判据 3b/4/5 正常态 OK 加必红 RED ⇒ 改 `CLOSED`。  **W35 本波复核指路**：W34 验收物 §2.4 加 §2.5 实跑摘录在案（8 行同车复核），状态维持 `CLOSED`。 |
| W30-03 | W28-01 出口判据原行零 diff + 三行销账条件错配 + §5.6 head_sha 笔误（外审 P0-3：§1.1 #6 裁决「W28-01 出口判据改成按文件白名单」、W29-01 缓解动作同，实跑 W27↔W29 台账 W28-01/02/03 三行全零 diff ⇒ 第 7 次「声称修复零 diff」；W29 段三行把 `CLOSED` 绑到「须真机取证」而出口判据全是仓内文档命令 ⇒ 机制性不可销账；§5.6 推送 2 head_sha 写 `1c6e404`、实为 `f0b12966b`） | W30 | `CLOSED` | **触发信号**：用户问「W28-01 到底销没销账」而原行仍写 0 命中；或三行因「须真机取证」永远挂 `OPEN`；或外部审查按 §5.6 推送 2 的 head_sha 去查 run 查不到。**缓解动作（本波第 0 车已做，一次纯文档提交）**：`W28-01` 行加行内注记「出口判据由 W29 checklist 判据 4 承载，原『全库 0 命中』按字面不成立（W29 实测 15 处命中）」（原结论不动）；`W28-03` 行删「须真机取证」错配条件，改「仓内判据实跑输出入账 + 制品与真值源一致」；`w29-acceptance.md:252` 推送 2 head_sha 改 `f0b12966b`（含 W30 订正注记、不改结论）。**出口判据（带必红变异）**：`W28-01` 行含「判据 4 承载」注记且不再把 0 命中当出口判据；`W28-03` 行不含「须真机取证」；`w29-acceptance.md:252` 含 `f0b12966b`；注入「只改验收物不改台账」⇒ W29 checklist 判据 4 RED；注入「台账重复 W30-01 行」⇒ 判据 1 RED（G20 仍绿、人工兜底须明写）。 | `docs/06-deferred-ledger.md` `W28-01`/`W28-03`（W30 行内注记）；`docs/analysis/w29-acceptance.md:252`（head_sha 订正）；`docs/notes/w29-closeout-checklist.md` 判据 4 （W32 行内注记：W28-01 注记 / W28-03 错配条件 / §5.6 head_sha 三处动作均有 diff，本行维持 `OPEN` 不关；出口条件 = 外审逐文件复核确认零 diff 复发已消，不在本波销账。） **W33 实跑入账**：W28-01 注记 / W28-03 错配条件 / §5.6 head_sha 三处均有 diff（W33 复核 grep 0 反向命中）；出口条件已满足，仅余外审复核 ⇒ 维持 `OPEN`。  **W34 销账**：W28-01 注记 / W28-03 错配条件 / §5.6 head_sha 三处均有 diff（W33 复核 + W34 判据 1 参数化复跑 W30/W31/W32/W33 四段 rows=3）⇒ 改 `CLOSED`。  **W35 本波复核指路**：W34 验收物 §2.4 加 §2.5 实跑摘录在案（8 行同车复核），状态维持 `CLOSED`。 |

### W31 段（2026-11-19，3 行：2 `CLOSED` + 1 `OPEN`；W31-01 经 W32 复核判据失效已回 `OPEN`）

> 上游依据：用户 2026-11-19 指令（四审查报告分歧裁决不改方向、多模型×多上下文×多功能面真机矩阵、先本地 UI+adb→真机→推仓库+CI、收尾契约止于交接）；外审四报告对 `193f3a75d`→最新 tip 的复核。
> 落笔前置：W31 号段 0 行（W26:5 属 W2.6 段、W27/28/29/30 各 3 行），号段自由；G20 无 ID 唯一检查 ⇒ 人工号段预检 + 必红变异兜底。
> 边界：3 行全零 `.kt`、只改 W30/W29 现有载体；需 `.kt` 的拆分/压缩/缺陷修复挂老号不新开行；六项用户终局裁决只留痕不代做。
> 3 行对 G20 并集零贡献（本波 handoff 不引入新挂账号字面量）。
> 设备 RSA 授权硬前置已于 W30 解除，本段不再以「授权未恢复」为顺延理由；台账由主理人单人落笔。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W31-01 | 脱敏两类回退 ⇒ 判据 3b 正常态恒红 + 制品断言空转 | W31 | `CLOSED` | **触发**：外审按文档原样跑 3b 正常态得 RED；或把 K_cred_filename verified=true / privacy CLEAN 读成脱敏达标。**缓解（第 0 车已做，零 .kt）**：W30 验收物抬头删凭据文件名字面改拼装表述；序列号改占位符；checklist 摘录与 W30 制品 target 改拼装形式；判据 3b 加白名单口径并实跑 OK 摘进 checklist；K_cred_filename invariant 改「按白名单 0 命中」、privacy note 与实际一致。**出口判据**：全树 5 类敏感形态复扫全 0；3b 正常态实跑 OK 且摘进仓内；注入样本仍 RED。 | `docs/analysis/w30-acceptance.md:6/:49/:84`；`docs/handoff-20261118-wave30.md:22`；`docs/notes/w29-closeout-checklist.md:109/:200`；`evidence/w30-matrix-summary.json` K_cred_filename/privacy；W31 验收物 §2 W31 第 0 车出口判据全实跑：全树凭据文件名/序列号 0 命中 + 3b 正常态 `OK no credential filename` + 注入样本 `RED credential filename hit`（真响）；W30 制品断言改「按白名单 0 命中」。销账依据 = 本波验收物 §2.1。 **W32 复核（行内注记，不改原结论）**：本行 `CLOSED` 按字面不成立——3b 正常态全树复扫 3 命中（恒 `RED credential filename hit`）；必红注入块改 `%%` 版后实跑不产出完整字面量（`OK (unexpected)`，必红哑火）；凭据文件名族两字面量被写回验收物结果列与制品 target、设备序列号被写回结果列自我证伪；checklist `:5` 声称加白名单但 `:106` 正常态代码块 W30→W31 精确 diff 零改动。先回 `OPEN`，由 W32 第 0 车真修并重跑两条原文后再按 §三.3 单人落笔销账。 **主从联动（W33 落笔）**：W32-01 出口判据复核通过并改 `CLOSED` 时，本行必须同一提交同步销账；只关一行视为幽灵行，外审判红。  **W34 销账（与 W32-01 同一提交同步销账）**：W32 复核回 `OPEN` 后由 W33 第 0 车真修（3b 正常态 0 命中、注入样本真响、制品 target 改拼装、序列号占位）；外审 a1 独立复扫全树 0 命中；W34 判据 3b 复跑 `OK no credential filename` 加 `RED credential filename hit` ⇒ 改 `CLOSED`。  **W35 本波复核指路**：W34 验收物 §2.4 加 §2.5 实跑摘录在案（8 行同车复核），状态维持 `CLOSED`。 |
| W31-02 | 必红块 WS 依赖 + 判据 1 写死 W29 段 | W31 | `CLOSED` | **触发**：外审重放必红因 KeyError 中断；或注入重复 W30-01 行而判据 1 不响。**缓解（第 0 车已做）**：判据 2/5 读取侧改自算路径（不设 WS 实跑 rc=0）；四条必红 + 3b 正常态 OK + 判据 2/5 正常态 OK 全部实跑摘进 checklist；判据 1 参数化 WAVE/EXPECT（W29/W30 两段正常态实跑、注入重复行演练 RED）。**出口判据**：不设 WS 时必红块 rc=0 且输出 RED；判据 1 两段 OK、注入重复行 RED；摘录不含凭据文件名字面。 | `docs/notes/w29-closeout-checklist.md`（参数化/自算/W31 摘录）；W31 第 0 车实跑输出 W31 第 0 车出口判据全实跑：不设 WS 时判据 2/5 必红 rc=0 输出 `RED mutation invariant broken`/`RED iteration/state invariant broken` + 判据 4 `RED affirmative wording remains` + 判据 1 W29/W30 两段 OK、注入重复 W30-01 `RED duplicate`（真响）。销账依据 = 验收物 §2.2 + checklist §本波 W31 摘录。 **W33 复核**：不设 WS 下判据 2/5 必红 rc=0 输出 RED、判据 3b/4 必红真响、判据 1 W30/W31/W32 三段 rows=3 —— 判据未塌，维持 `CLOSED`。 |
| W31-03 | 收尾契约占位 + handoff 零编号自证虚报 + 派生文案半修 | W31 | `CLOSED` | **触发**：外审读到 §5.1/§5.4 占位；或把 G20 115 读成 ID 唯一已检查；或 W29 四态读到 PASS 6。**缓解（第 0 车已做，零 .kt）**：删两占位加指针注记；handoff 抬头改「不引入新挂账号」；§四 G20 改「以实跑为准不写死」；W29 §5.3 同步删错配条件；W29 制品四处派生文案与 state_counts 一致；W30 制品跨波 target 标 inherited_check + glyph 映射断言 + cells_provenance 订正；W24-03 行内留痕「多值待裁」；W30 §5.2 六项补挂账号。**出口判据**：占位 0 命中；handoff 抬头与正文口径一致；§四不含写死 G20 数字；W29 §5.3 不含错配条件；W29 制品派生文案与 state_counts 一致。 | `docs/analysis/w30-acceptance.md` §5.1/§5.4/§四/§5.2；`docs/handoff-20261118-wave30.md` 抬头；`docs/analysis/w29-acceptance.md:202`；`evidence/w29-matrix-summary.json`；`evidence/w30-matrix-summary.json`；`docs/06-deferred-ledger.md` W24-03 W31 第 0 车出口判据全实跑：W30 两占位删除（仅剩订正注记内历史指称）+ handoff 抬头改「不引入新挂账号」+ §四 G20 不写死 + W29 §5.3 删错配条件 + W29 制品四处派生文案与 VACUOUS 一致 + W30 制品 inherited_check/glyph mapping/cells_provenance。销账依据 = 验收物 §2.3。 **W33 复核**：W31 制品 29 键 / 断言 3=3=3 / iterations 6=6 / glyph 三键；W31 验收物占位仅剩订正注记内历史指称；G20 不写死 —— 判据未塌，维持 `CLOSED`。 |

### W32 段（2026-11-19，3 行：3 `OPEN`；W31-01 回 `OPEN` 后真修再销账）

> 上游依据：用户 2026-11-19 指令（读记忆/上一波 handoff/挂账行、四审查报告分歧裁决不改方向、多模型×多上下文×多功能面真机矩阵、先本地 UI 加 adb 调试→真机→推仓库加 CI、收尾契约止于交接）；四外审《W31 深度审查与 W32 排期裁决》《W32 段挂账草案》《莫斯线 W26–W31 增量审查》《W31 深度审查加挂账分析加 W32 路线建议》对 `453b8b5`→`7b10cc8` 的逐文件复核。
> 落笔前置实跑：全表行首扫描 `W32` **0 行**（`W26`:5 属 W2.6 别名段，`W27`/`W28`/`W29`/`W30`/`W31` 各 3 行）；G20 只做存在性比对、不查 ID 唯一、不查 `CLOSED` 证据真伪 ⇒ 人工号段预检加必红变异加内容复核兜底，改守卫属用户裁决域。
> 边界：3 行全零 `.kt`、**只改 W31/W30/W29 现有载体**（不新建第 5 份 checklist、第 6 份制品，不重出 W28 及更早正文）；需 `.kt` 的拆分三方、B1 压缩、两缺陷修复整包顺延（挂老号不新开行）；六项用户终局裁决只留痕不代做。
> 3 行对 G20 并集零贡献（本波 handoff 只引用台账既有行首号）；台账由主理人单人落笔。设备 RSA 授权已于 W30 解除、设备已唤醒；本段不新开 `W32-04+` 承接同一批老债。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W32-01 | W31-01 标 `CLOSED` 但两条核心出口判据双向失效（① 3b 正常态全树复扫实跑 3 命中 ⇒ 恒 `RED credential filename hit`，而 checklist `:228` 摘录写 `OK no credential filename`；② 必红注入块 `%%` 版实跑产出不含完整字面量 ⇒ `OK (unexpected)` 必红哑火，而摘录写 RED；③ 凭据文件名族两字面量被写进验收物结果列与制品 target、设备序列号被写进结果列自我证伪；④ checklist 抬头 `:5` 声称加白名单口径但 `:106` 正常态代码块 W30→W31 精确 diff 零改动，第 8 次「声称修复零 diff」；⑤ G20 只查 `CLOSED` 行证据非空、不查内容真伪 ⇒ 守卫绿、账实不符） | W32 | `CLOSED` | **触发信号**：外部审查按文档原样跑 3b 正常态得 RED、跑必红得 `OK (unexpected)`；或把验证字段读成脱敏达标；或把 W31-01 `CLOSED` 读成判据已真验证；或下一波继续用注记引用除外软化销账范本。**缓解动作（零 `.kt`，只改 W31 三件载体加 checklist 判据 3b）**：验收物 `:32/:33` 结果列改「凭据文件名族 A/B」「设备序列号（已打码）」等不含字面量表述；制品 target 改拼装或占位、或移出 `docs` 加 `evidence` 扫描面；判据 3b 白名单真落地（`:106` 正常态代码块改：排除注记引用、命令行、必红摘录，或扫描面限定交付物正文）；注入样本用两段拼装实跑产出完整字面量，正常态 `OK no credential filename` 与必红 `RED credential filename hit` 两条输出原文入仓；**W31-01 先回 `OPEN`**，重跑后再按 §三.3 单人落笔销账。**出口判据（带必红变异）**：全树 5 类敏感形态复扫，凭据文件名族 0、设备序列号 0、PAT 0、真 key 0、本机新增路径 0（历史注记除外）；3b 正常态实跑 OK 且入仓；注入样本实跑 RED 且入仓；注入验证字段置 false 而出厂断言仍全绿 ⇒ 判红；注入肯定式措辞回写 ⇒ checklist 判据 4 `RED affirmative wording remains`。 | `docs/analysis/w31-acceptance.md:32/:33`（§2.1）；`evidence/w31-matrix-summary.json:129/:131`；`docs/notes/w29-closeout-checklist.md:106/:111-119/:226-228`；W32 验收物 §2.1 必红实跑摘录；台账 W31-01 行内注记。 **主从联动（W33 落笔）**：本行出口判据复核通过并改 `CLOSED` 时，`W31-01` 必须同一提交同步销账（两行同一缺陷）；只关一行视为幽灵行，外审判红。  **W34 销账（主从联动：与 W31-01 同一提交）**：W33 第 0 车把 3b 双判据真修并两条原文入仓；外审 a1 §0/§1 独立复扫 5 类敏感形态全 0；W34 判据 3b 正常态/必红复跑均真响；验收物结果列与制品 target 已无字面量 ⇒ 改 `CLOSED`。  **W35 本波复核指路**：W34 验收物 §2.4 加 §2.5 实跑摘录在案（8 行同车复核），状态维持 `CLOSED`。 |
| W32-02 | W31 制品 schema 反向退化加 `FAIL:0` 零值键加面定义漂移（① 顶层仅 14 键，相对 W30 的 38 键（报告写 37，以实测为准）丢 `schema`/`cells_total`/`declared_feature_faces`/`iterations[]`/`iterations_total`/`attempt_states`/`assertions`/`mutation_cases`/`mutation_selftest`/`coverage_note`/`cells_provenance`；`cells[].face` 为中文自由文本、机器枚举 `feature_face` 全无；② `state_counts` 含 `FAIL:0` 零值键越出 `cells[].state` 值域、`K_state_domain` 缺失 ⇒ W28-02 空转同型复发；③ W29/W30 的 `F7` = `mcp` ⛔vacuous，W31 `F7` 换义为 tool_loop ✅ ⇒ MCP 面静默消失、无 vacuous 留痕，而 §1.1 #3 仍列 MCP；W30 的 `F4 skill ✅` → W31 `F3 skill ⛔vacuous`，T0 档位轴与 1M 读数整格消失 ⇒ 用户主体判据「直至 1M」本波零证据；④ `baseline_note` 与 handoff §3.1 写「W29/W30 连续两波全 VACUOUS」事实错，W30 实为 3 PASS/2 PARTIAL/3 VACUOUS） | W32 | `OPEN` | **触发信号**：外部审查按仓内唯一机器可读制品复核四态、面覆盖、迭代链却读不到字段；或把 `FAIL:0` 读成四态齐全；或按 §1.1 #3 找 MCP 格找不到；或把 W30 记成全 vacuous 做跨波对比。**缓解动作（零 `.kt`，只改 `evidence/w31-matrix-summary.json` 加两份文档）**：补回 `schema`/`cells_total`/`declared_feature_faces`/`iterations[]`/`iterations_total`/`attempt_states`/`coverage_note`/`cells_provenance`，`cells[].face` 并列补机器枚举 `feature_face`；补 `K_state_domain`（非零键集 = `cells[].state` 值域）与 `K_feature_enum` 两条断言及结构化必红样本；删 `FAIL:0` 零值键；补 MCP 格（`feature_face='mcp'`、`VACUOUS`、`unexercised_reason='无外部端点'`）或显式 `mcp: NOT_EXERCISED`；`baseline_note` 订正为「W29 全 VACUOUS、W30 3 PASS/2 PARTIAL/3 VACUOUS、W31 首次工具链闭环」，handoff §3.1 同句订正。**出口判据（带必红变异）**：`state_counts` 非零键集 = `cells[].state` 值域、零值键 0；断言数 ≥2 且每条带结构化 `injected_sample`；`declared_feature_faces` 含 `mcp` 且有对应格或显式 `NOT_EXERCISED`；`iterations_total == len(iterations[])`；注入加回 `FAIL:0` 或删 MCP 格 ⇒ 出厂断言判红；`baseline_note` 不含「W29/W30 连续两波全 VACUOUS」。 | `evidence/w31-matrix-summary.json`（顶层 14 键与缺失字段、state_counts、cells、privacy）；`evidence/w30-matrix-summary.json`（38 键 schema 参照）；`docs/analysis/w31-acceptance.md:81`；`docs/handoff-20261119-wave31.md:36-37`；W32 验收物 §2.2 判定实跑。 |
| W32-03 | 收尾契约第 11 次加派生量写死加 PAT 未闭环句回归（① 验收物 `:144` 写「含全部 docs 加 evidence 加制品，单次推送即含证据，无需为代码二次推送」，而提交序列是 `453b8b5` 主体加 tip `7b10cc8` 纯文档回填，tip run 按既定降级规则不判 P0 但「单次推送」是事实错，同型第 11 次；② W31-03 刚把 W30 验收物 `:139` 改成「ID 数以 arch-guard 实跑为准、不写死数」，W31 本波验收物 `:98/:139` 与 handoff `:77` 又写死「G20 = 121」，同型第 4 次；③ 验收物 `:148`「PAT 视为暴露须轮换」回归，W24 `:384` 已订正为已闭环动作表述；④ §5.2 F1/F5/F6/OpenRouter 四条写「无新挂账号」而 W30 同型已补老号；⑤ handoff 抬头 `:8-9`「不引入新挂账号」与正文 W31 三行加 `W28-03` 口径不严；⑥ W31 摘录只列判据 1 的 W29/W30 正常态、W31 段无摘录证据；⑦ `W24-03` handoff §四写单值顺延、台账行内注记写多值待裁；⑧ W31-02/03 证据列各一处无主语拼接） | W32 | `OPEN` | **触发信号**：外部审查按 §5.6 判推送次数得 1 而提交序列是 2；或把 G20 121 读成 ID 唯一已检查、新增行已被守卫覆盖；或把 PAT 句读成仓内未闭环动作；或按 §5.2 找挂账号找不到；或用户问 W31 到底推了几次而两份文档给不同答案。**缓解动作（零 `.kt`，一次纯文档提交）**：§5.6 删「单次推送」改「推送 2 次（主体加纯文档回填）」并补 tip 口径注记（不补 tip run 链接、不判 P0）；验收物 §四 `:98/:139` 与 handoff §五 `:77` 的写死数改「以 `arch-guard` 实跑为准、不写死数」；`:148` PAT 句按 W24 `:384` 先例改已闭环表述；§5.2 四条补挂老号（`W14-11`/`W20-05`/`W21-04`/`W24-02`）；handoff 抬头改「不引入不在台账行首的复合编号」并补列 `W28-03`；W31 摘录补判据 1 的 W31 段一行；`W24-03` 两处统一「多值待裁」；两处无主语拼接修掉。**出口判据（带必红变异）**：W31 验收物「单次推送」0 命中、含「推送 2 次」；§四与 handoff §五不含写死 G20 数；「须轮换」0 命中；§5.2 四条各带挂账号；handoff 抬头列全 `W28-03` 与 W31 三行；W31 摘录含判据 1 的 W31 段输出；注入肯定式措辞回写 ⇒ 判据 4 RED；注入台账重复 `W32-01` 行 ⇒ 判据 1 RED（G20 仍绿、人工兜底明写）。 | `docs/analysis/w31-acceptance.md:98/:116-119/:139/:143-148`（§5.6）；`docs/handoff-20261119-wave31.md:8-9/:29/:67/:77`；`docs/notes/w29-closeout-checklist.md:218-220`；台账 `W24-03`。 |

### W33 段（2026-11-19，3 行：3 `OPEN`；先收本波账实，再谈矩阵与代码波）

> 上游依据：用户 2026-11-19 指令（读记忆/上一波 handoff/OPEN 挂账行、读六份审查报告并裁决分歧、不改方案大体方向、多模型×不同上下文直至 1M×功能面真机矩阵、先本地 UI 加 adb 调试→真机→推仓库加 CI、收尾契约止于交接）；六份外审《W33 段挂账草案》《W32 深度审查与 W33 排期裁决》《source-review-consolidated》《W32 深度审查与挂账分析及 W33 路线建议》《harness-w30–32 review》《W30–W32 深度审查与挂账分析》对 `9eec391`→`eaf5eff` 的逐文件复核。
> 落笔前置实跑：全表行首编号扫描 `W33` **0 行**（`W26`:5 属 W2.6 段、`W27`/`W28`/`W29`/`W30`/`W31`/`W32` 各 3 行）；G20 只做存在性比对、不查 ID 唯一、不查 `CLOSED` 证据真伪 ⇒ 人工号段预检加必红变异加内容复核兜底，改守卫属用户裁决域。
> 边界：3 行全零 `.kt`、只改 W32/W31 现有载体（不新建第 6 份 checklist / 第 7 份制品 / 不重出 W30 及更早正文）；需 `.kt` 的拆分三方 / B1 压缩 / 两缺陷修复整包顺延（挂老号，不新开行）。
> 3 行对 G20 并集零贡献（本波 handoff 不引入不在台账行首的复合编号）；tip run 按既定降级规则只留痕、不逐波判 P0。
> 设备 RSA 授权已于 W30 解除；本段不以「授权未恢复 / 矩阵未铺」为顺延理由；台账由主理人单人落笔。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W33-01 | 收尾契约第 12 次：在修第 11 次的同一波里复发（① `w32-acceptance.md:126` 仍写「待真机席回传后回填…进行中」、`:141` 仍写「其余待跑」，而同文件 §三/§四/§5.5 已给全量结果；② `:173` 写「一次推送」而提交序列 `9eec3919a`→`eaf5eff0b` 是 2 次；③ `:167` 写死「126 个 handoff ID」，自带「不写死」括号；④ tip 只回填 §5.6、没回改 §5.1/§5.5——与 W16/W17/W18/W28/W30/W31 同型） | W33 | `CLOSED` | **触发信号**：外审按 §5.1 复核读到「进行中/其余待跑」；或按 §5.6 判推送次数得 1；或把「126」读成 ID 唯一已检查。**缓解动作（零 `.kt`，一次纯文档提交）**：`:126` 改指 §三/§四/§5.5 指针；`:141` 改「四闸门 ✅ 见 §四」；`:173` 改「推送 2 次（主体 `9eec391` 加纯文档回填 tip `eaf5eff0b`）」；`:167` 删「126 个」改「以 `arch-guard` 实跑为准、不写死派生数」；tip run 按降级规则不补链接、不判 P0。**出口判据（带必红变异）**：`w32-acceptance.md` 的「待真机席回传」「其余待跑」「一次推送」0 命中；§5.6 含「推送 2 次」；注入「把占位写回 §5.1」⇒ 人工复核判红（G20 不覆盖，人工兜底明写）。 | `docs/analysis/w32-acceptance.md:126/:141/:167/:173`（W33 改后）；提交序列 `9eec3919a`→`eaf5eff0b`；W33 验收物 §2.1  **W34 销账**：外审 a1 §1 逐行复核「W32 四处全改、grep 0 命中」；W34 本方复查 `w32-acceptance.md` 四占位 0 命中且 §5.6 含「推送 2 次」；本波自己写死 130 的新债由 W34-01 承接（不属本行出口判据）⇒ 改 `CLOSED`。  **W35 本波复核指路**：W34 验收物 §2.4 加 §2.5 实跑摘录在案（8 行同车复核），状态维持 `CLOSED`。 |
| W33-02 | 双行重复挂账加销账联动缺失加状态两套口径（① `W31-01` 与 `W32-01` 描述同一缺陷，但两行都没写「W32-01 关闭时 W31-01 同步销账」⇒ 下一波只关一行会留幽灵行；② handoff §三表格四态列写 ✅、§六又写三行 `OPEN`，两维度未注明；③ G20 不查重复语义、不查「✅ vs `OPEN`」口径） | W33 | `CLOSED` | **触发信号**：用户问「3b 缺陷销没销账」而两行都 `OPEN`；或下一波只关一行；或把 handoff §三 ✅ 读成已闭环。**缓解动作（零 `.kt`，只改台账两行加两份文档）**：`W31-01` 与 `W32-01` 各加「主从联动」注记（**取方案 a：加注记、不合并行**——合并属用户裁决域，本波不代做）；handoff §三加表注「本节四态指缓解动作实跑结果、非挂账状态」；W32 验收物 §二/§5.1 同步注明；`W32-01` 证据列 checklist 行号由 `:244-257` 订正为现役树实测 `:248-265`。**出口判据（带必红变异）**：两行含「主从联动」；handoff/验收物不再用裸 ✅ 表挂账状态；行号指向真实摘录；注入「只关 W32-01 不改 W31-01」⇒ 人工复核判红（G20 不覆盖，人工兜底明写）。 | `docs/06-deferred-ledger.md` `W31-01`/`W32-01`（W33 注记）；`docs/handoff-20261119-wave32.md` §三表注/`:44`；`docs/analysis/w32-acceptance.md` §二/§5.1/§2.1  **W34 销账**：外审 a1 §1 判「全达标，且是真改行不是注记除外」；W34 复查两行含「主从联动」、handoff §三表注在、W32-01 行号 `:248-265` 指向真实摘录 ⇒ 改 `CLOSED`（主从联动的最终销账动作在本波与 W31-01/W32-01 同步完成）。  W34 复核实跑：两行含「主从联动」、handoff §三表注在、W32-01 行号指向真实摘录（见 `docs/analysis/w34-acceptance.md` §2.4）。  **W35 本波复核指路**：W34 验收物 §2.4 加 §2.5 实跑摘录在案（8 行同车复核），状态维持 `CLOSED`。 |
| W33-03 | W32 制品的裁决加文档加判据三方不一致，`K_feature_enum` OR 恒真，且 14 格 `evidence_ref` 全部指向仓内不存在文件（① handoff #5 与验收物 §3.3 写「不塞 VACUOUS 格」而制品有 `F4_mcp` 格；② `K_feature_enum` 为「有格或显式 NOT_EXERCISED」OR 条件 ⇒ 删格/删字段都不响；③ 14 格 `evidence_ref` 与 18 条 `iterations[].evidence_ref` 指向 `evidence/*.json`，仓内不存在——外审实测 14/14 MISSING，PASS 格不可独立复核） | W33 | `CLOSED` | **触发信号**：外审按 #5 找「不塞格」却在制品找到 `F4_mcp`；或重放必红样本发现删格不响；或按 `evidence_ref` 复核 14 格全 MISSING。**缓解动作（零 `.kt`，改 W32 制品加两份文档加证据入仓）**：**取方案 b**（保留 `F4_mcp` VACUOUS 触发格、文档改「NOT_EXERCISED 并留触发格」——格有真机页面证据，删格会丢机器可读溯源）；`K_feature_enum` OR 改 AND 并新增 `K_feature_enum_no_cell_claim`，补「删字段」「删格」两条必红并实跑；14 格与 iterations 的 `evidence_ref` 全部改指 `evidence/raw/w32/`（26 个脱敏文件入仓，序列号与本机路径改占位）；W31/W32 两制品 `state_glyph_mapping` 统一为三键；`coverage_note` 补跨波 MCP 定义差异；W32 制品补 `matrix_vs_gate_order`。**出口判据（带必红变异）**：`K_feature_enum` 不含裸 OR；四条必红实跑 RED（删字段/删格/加 FAIL:0/删中间迭代）；`evidence_ref` 100% 仓内存在；注入「删 `mcp` 字段」或「删 `F4_mcp` 格」⇒ 判据 6 判 RED。 | `evidence/w32-matrix-summary.json`（cells/assertions/mutation_cases/mcp/coverage_note/state_glyph_mapping/matrix_vs_gate_order）；`evidence/raw/w32/`（26 文件）；`evidence/w31-matrix-summary.json`（glyph 统一）；`scripts/../docs/notes/w29-closeout-checklist.md` 判据 6；W33 验收物 §2.3  **W34 销账**：外审 a1 §1 判「主链全达标」（`K_feature_enum` AND、四条必红复刻全 RED、14 格 `evidence_ref` 100% 存在）；W34 判据 6 参数化后对 w32 正常态 `OK` 加五类必红 RED（含新补 `K_evidence_ref` 的 `RED missing ref`）⇒ 改 `CLOSED`；w32 断言数字账实尾巴由 W34-02 承接。  **W35 本波复核指路**：W34 验收物 §2.4 加 §2.5 实跑摘录在案（8 行同车复核），状态维持 `CLOSED`。 |

### W34 段（2026-11-19，3 行：3 `OPEN`；本波自己的账实收口 + 矩阵主攻，零 `.kt`）

> 上游依据：用户 2026-11-19 指令（读记忆/上一波 handoff/OPEN 挂账行、六份审查报告裁决分歧、不改方案大体方向、多模型×不同上下文直至 1M×功能面真机矩阵、先本地 UI 与 adb 调试→真机→推仓库与 CI、收尾契约止于交接）；六份外审《W34 段挂账草案》《W33 深度审查与 W34 排期裁决》《源码级深度审查-W33》《W33-深度审查与挂账分析》《W33 深度审查与挂账分析及 W34 路线建议》《harness-w33-review》对 `c4dc1a0`→`00fe9c5`→`ccd215b7e` 的逐文件复核。
> 落笔前置实跑：全表行首编号扫描 `W34` **0 行**（`W26`:5 属 W2.6 段，`W27`–`W33` 各 3 行）；G20 只做存在性比对、不查 ID 唯一、不查 `CLOSED` 证据真伪 ⇒ 人工号段预检加必红变异加内容复核兜底，改守卫属用户裁决域。
> 边界：3 行全零 `.kt`、只改 W33/W32 现有载体（不新建第 6 份 checklist / 第 7 份制品，不重出 W30 及更早正文）；需 `.kt` 的拆分三方 / B1 压缩 / 两缺陷修复整包顺延（挂老号，不新开行）。
> 3 行对 G20 并集零贡献（本波 handoff 只引用台账既有行首号）；tip run 按既定降级规则只留痕、不逐波判 P0；台账由主理人单人落笔。
> 同车销账（外审复核 + 仓内判据实跑入账）：`W30-01/02/03`（三行，判据 1/2/3b/4/5 全量复跑）、`W31-01`+`W32-01`（主从联动同一提交同步销账）、`W33-01/02/03`（外审 3/3 真修复核）⇒ 共 8 行改 `CLOSED`，净 OPEN −5。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W34-01 | 收尾契约第 13 次：在修第 12 次的同一波里复发（`w33-acceptance.md` §四 G2 与 §5.5、`handoff-20261119-wave33.md` §五 G2 三处写死「130 个 handoff ID」；数字本身正确、错在派生量进真值源——W33-01 刚替 W32 立下「计数以 `arch-guard` 实跑为准、不写死派生数」，同型第 6 次） | W34 | `OPEN` | **触发信号**：外部审查把「130」读成 ID 唯一已检查，或下一波再写死 `131`。**缓解动作（零 `.kt`，已做）**：三处改「handoff ID 全在台账、计数以 `arch-guard` 实跑为准，真值源不写死派生数」；tip SHA 与 tip run 按降级规则仍不补链接、不判 P0。**出口判据**：两文件「130」0 命中；三处含「以 `arch-guard` 实跑为准」或「不写死派生数」；注入「把 130 写回 §四」⇒ 人工复核判红（G20 不覆盖，人工兜底明写）。 | `docs/analysis/w33-acceptance.md` §四/§5.5；`docs/handoff-20261119-wave33.md` §五；本波 grep 两文件「130」= 0；W34 验收物 §2.1 |
| W34-02 | W33 制品断言账实不齐 + 判据 6 只覆盖 w32 + `K_evidence_ref` 无必红输出 + w32 制品 3/4/5 三值打架（W33 制品 5 条断言全 `verified=true` 但 checklist 判据 6 硬编码 w32、`K_evidence_ref` 的「注入不存在 ref ⇒ RED」从未跑过；w32 制品 `assertions` 实 3 条而 `mutation_selftest.note` 写「扩为 5 条」、验收物 §3.6 写「断言扩到 4 条」，note 还指向不存在的「判据 2b」；判据 6 代码块 `cd harness-v2` 在仓根被吞、`../_w33/mut/` 未预建 ⇒ 必红块跑不起来） | W34 | `OPEN` | **触发信号**：外部审查按 W33 制品 note 找「5 条必红」只找到 w32 四条；或注入 `evidence_ref` 指向不存在文件而出厂断言仍全绿；或按判据 6 原样执行得 `FileNotFoundError`。**缓解动作（零 `.kt`，已做）**：判据 6 参数化 `ARTIFACT` + 自算仓根 + `mkdir -p` + 五类必红（含 `k6_ref`）；w32 制品补 `K_feature_enum_no_cell_claim` 进 `assertions`（3→4，与 `mutation_cases` 4=4）、note 改「4 条 / 判据 6」；w33 制品 `mutation_selftest` note 指向 W34 参数化实跑；验收物 §2.3/§3.6 同步。**出口判据（带必红变异）**：判据 6 对 w32 与 w33 双制品正常态 `OK`；五类必红各 RED（含 `RED missing ref`）；w32 断言数=必红数=4；两制品 note 不含「判据 2b」「扩为 5 条」；注入「`verified=false` 而 selftest 仍全绿」⇒ 判据 2 RED。 | `docs/notes/w29-closeout-checklist.md` 判据 6（参数化+五类必红）；`evidence/w32-matrix-summary.json`（assertions 4=4、note）；`evidence/w33-matrix-summary.json`（selftest note）；`docs/analysis/w33-acceptance.md` §2.3/§3.6；W34 验收物 §2.2 |
| W34-03 | 删除 8 份历史 `source-review-2026-11-*.md` 无挂账联动 + 26 处引用悬空 + `W19-14` 失去对象（① W33 仅一行「用户确认已整合进 `source-review-consolidated-2026-11-19.md`（仓外）」、无裁决留痕；② 全仓 26 处引用仍在（`w19-acceptance` 6 / `w20-acceptance` 4 / `w8-solution` 3 / 台账 2 / 4 份 handoff / 4 份验收物 / `w19` 制品 1）；③ `W19-14` 是 `OPEN` 行、整行针对已删文件 ⇒ 幽灵行；④ 与裁决表 #7「四报告出处入仓：否」方向不自洽） | W34 | `OPEN` | **触发信号**：外部审查按 `W19-14` 或 w17/w19 验收物找日析文件得 404；或计数 26 处引用逐条复核全部断链。**缓解动作（零 `.kt`，用户已裁决案 b，已做）**：用户 2026-11-19 在《源码级深度审查-W33》头部批注「历史 `source-review` 属自动化产物，删除无问题」⇒ 维持删除；`W19-14` 改 `CLOSED`（问题随对象消失）+ 行内结案注记；W33 验收物 §5.4 补裁决留痕（用户批注出处 + 26 处引用计数 + `consolidated` 仓外路径 + git 历史恢复路径）；历史验收物逐处抬头**不做**（改 W8–W20 正文违反「不重出更早正文」边界，登记为出口判据残余项）。**出口判据**：§5.4 含裁决留痕与引用计数；`W19-14` 不再指向存在性未说明的对象；注入「删 §5.4 说明且台账无登记」⇒ 人工复核判红（G20 不覆盖）；26 处引用抬头若要做须单开一波并先裁「可改历史正文」。 | `docs/analysis/w33-acceptance.md` §5.4；`docs/06-deferred-ledger.md` `W19-14`；《源码级深度审查-W33》头部批注；本波全仓 `source-review-2026-11-` 计数 26（`grep -rn` 14 文件）；W34 验收物 §2.3  **W35 现役复核**：日析文件引用计数按 docs 的 md 出现次数实测 31 处 / 13 文件；W34 时点快照 26 处与外审 32/14 均为各自版本时点，非现役真值。**W36 补 E05 撤销指针**：W34 验收物三处「模型级授权」断言已被 W35 新 key 单变量替换证伪（旧 key 403、新 key strict 逐字、995.9K 端点回读），撤销声明在 `handoff-20261120-wave35.md` §4.3，跨波按「模型级」检索时以此为准、勿采 W34 旧结论；按案 b 不重出 W34 正文。 |

### W35 段（2026-11-20，3 行：3 `OPEN`；先收本波账实，再把 F6 确定性根因写进压缩主债行）

> 上游依据：用户 2026-11-20 指令（读记忆/上一波 handoff/OPEN 挂账行、六份审查报告裁决分歧、不改方案大体方向、多模型×不同上下文直至 1M×功能面真机矩阵含多模态/工作空间读写/skill/MCP/手动自动压缩、先本地 UI 与 adb 调试→真机→推仓库与 CI、收尾契约止于交接）；六份外审《W35 段挂账草案》《W34 深度审查与 W35 排期裁决》《harness-review-master》《W34-深度审查与挂账分析》《W34 深度审查与挂账分析及 W35 路线建议》《W31-W34 审查与挂账分析》对 `dc3fb14`→`55630d8`→`ab5148f` 的逐文件复核。
> 落笔前置实跑：全表行首编号扫描 `W35` **0 行**（`W26`:5 属 W2.6 段，`W27`–`W34` 各 3 行）；G20 只做存在性比对、不查 ID 唯一、不查 `CLOSED` 证据真伪、不查「制品自带断言数」⇒ 人工号段预检加必红变异加内容复核兜底，改守卫属用户裁决域。
> 边界：3 行全零 `.kt`、只改 W34/W33 现有载体（不新建第 6 份 checklist / 第 8 份制品，不重出 W30 及更早正文）；需 `.kt` 的压缩时机 / 诊断码 / `promptContext()` CE 重抛整包顺延（挂老号，不新开行）。
> 3 行对 G20 并集零贡献（本波 handoff 只引用台账既有行首号）；tip run 按既定降级规则只留痕、不逐波判 P0；台账由主理人单人落笔。
> 设备 RSA 授权已于 W30 解除、W34 矩阵 13 格且 14 件脱敏证据入仓 ⇒ 本段不以「授权未恢复 / 证据不可复核」为顺延理由。
> 口径订正留痕：本轮采用第 4 列状态口径 = 210 行 / 33 `CLOSED` / 166 `OPEN` / 11 `CARRIED`（与 W34 交接逐值相等）；W33→W34 状态变化正好 9 行（8 销账 + `W19-14` 结案）；「净 OPEN −5」按交接口径不含结案、含结案为 −6。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W35-01 | W34 新制品断言字段整包删除但制品清单虚报 5 加 5 加两处悬空引用（① `evidence/w34-matrix-summary.json` 顶层 28 键、`assertions` 0 条、`mutation_cases` 0 条、`mutation_selftest` 为 None，而 W33 制品 30 键含这三项；② `w34-acceptance.md` §5.4 写「13 格 / 5 断言 / 5 必红」、§2.2 写「w33 制品 selftest note 指向 W34 参数化实跑 5=5」；③ 判据 6 参数化 `k6_check.py` 只查面枚举 / mcp / state / iterations / evidence_ref，不检查断言或必红字段是否存在 ⇒ w34 零断言制品正常态仍 OK；④ `w33-acceptance.md` 两处写「见 W34 验收物 §2.5a」，而 W34 验收物无 §2.5a。W34 花一整波修 W33「5 条断言全自报」，本波新制品连字段都没有却对外报 5 加 5） | W35 | `OPEN` | **触发信号**：外部审查按 §5.4 去 w34 制品找 5 条断言得到空数组；或注入「删掉 w34 制品 assertions 字段」而判据 6 仍全绿；或按 §2.5a 找 W34 实跑原文得到 404。**缓解动作（零 `.kt`，纯文档加制品同车）**：取案 a 给 w34 制品补回 `assertions` 5 条 + `mutation_cases` 5 类 + `mutation_selftest`（与 §5.4 对齐，字段结构参照 w33 制品），不写「构建再次通过」；给判据 6 加 `K_assertion_presence` 断言（注入「删 assertions 字段」⇒ `RED assertions missing`）与 REPO_ROOT 兜底，把正常态与必红两条原文入仓；两处 §2.5a 改指 W34 验收物 §2.5 加 §2.2。**出口判据（带必红变异）**：w34 制品断言数 加 必红数 加 §5.4 声明 加 selftest 四处一致；判据 6 对 w34 正常态 OK；注入「删 assertions」⇒ `RED assertions missing`；注入「evidence_ref 指向不存在文件」⇒ `RED missing ref`；全树 §2.5a 0 命中。 | `evidence/w34-matrix-summary.json`（顶层 28 键 / 三字段全空）；`docs/analysis/w34-acceptance.md` §5.4/§2.2/§2.5；`docs/notes/w29-closeout-checklist.md` 判据 6；`docs/analysis/w33-acceptance.md` 两处 §2.5a；本波仓内实跑判据 6 正常态加必红原文 |
| W35-02 | `W9-01` 压缩主债行的 W34 注记缩水加 F6 根因未入账（① handoff §六声称「W9-01 行内新增 W26 加 W33 两次 F6 实锤 + W34 自动加手动不一致」注记，实跑该行 `net=-256` / `insufficient` / `不一致` / `自动` / `手动` / `applied` / `dropped` 全 False，只写「W34 以 32K 档 + 大文件读取复测」⇒ W34 最硬的 F5 首次 `applied=true`（`dropped=9 kept=11`、下一轮 30.9k→22.9k in）与 F6 自动 `net=-256` 而手动 `dropped=9`（W36 订正：不同时刻两个正确答案、非同快照分裂）一条都没进主债行；② 两个确定性命题：`savingChars ≡ 0` 等价于试算 `outcome.compression=null`（`ConversationOps.kt:110-112`、`ManualCompress.kt` untouched 分支返 null、`SessionFootprint.kt` 全量重建），故 `net ≡ −256`、`insufficient_savings` 与「收益不够」语义无关；[W36 订正·命题②降级] `ChatViewModel.kt:407` 在 `onEvent` 之前取旧快照（少最终回答气泡且 isStreaming=true）是代码事实、未证实为分裂成因；决定性变量是体积/试算能否压动（W35 同一自动路径 11 秒 17:42:14 net=-256→17:42:25 applied）。手动 `compressContext()` 不经 shouldApply、直接判 outcome.applied；③ 等价 Python 复刻逐字命中 W34 `deviceLogTail.txt` 的 15:22:14 加 15:22:59 两条 net=-256 加 15:24:01 manual_compress dropped=9 kept=11） | W35 | `OPEN` | **触发信号**：外部审查按 `W9-01` 找 W34 压缩证据只读到「复测（见 §3）」；或下一波仍按旧「两把尺子」顺序开压缩波而漏掉 `:407` 时机；或把 `insufficient_savings:net=-256` 读成「收益确实不够」而非「试算压不动」。**缓解动作（零 `.kt`，只改台账一行加文档）**：`W9-01` 行内逐字补 F5 加 F6 两条真机证据 加 `evidence/raw/w34/f5_manual_compact.json` 加 `f6_auto_compact.json` 路径；把两个确定性命题写进缓解动作（修 `:407` 调用时机 加 `ConversationOps` 用 `outcome.applied` 前置判 加 `untouched` 单独报 `nothing_to_drop` 加三条必红 JVM 用例：a 试算 untouched 不得产出 insufficient_savings、b 自动与手动对同一 bubbles 快照结论一致、c applied 后下一轮 committedChars 真下降）；**仍遵 W28 案 B 不作 `W9-01` 红态入口**，W34 §3.4「根因未定位」改「已收窄为两个确定性命题」但不写成已修复，F6 具体走哪条 untouched 分支不作唯一断言。**出口判据（带必红变异）**：`W9-01` 行含 `applied` 加 `dropped=9` 加 `net=-256` 加 `:407` 加 `savingChars`；注入「删掉注记里 F6 那句」⇒ 人工复核判红（G20 不覆盖，人工兜底明写）；两份 f5/f6 证据 reading 与 `W9-01` 结论一致、不再写「根因未定位」。 | `docs/06-deferred-ledger.md` `W9-01` 行；`evidence/raw/w34/f5_manual_compact.json`、`f6_auto_compact.json`；`docs/analysis/w34-acceptance.md` §3.4；`W35 段挂账草案` §一 W35-02 |
| W35-03 | 8 行销账证据列零本波复核指路 加 判据 7 只查「有指针」加 案 b 残余计数现役 docs 实跑 31 处 / 13 文件（外审时点快照 32/14、方案席复核并列留痕）（① W34 同一提交把 8 行 OPEN→CLOSED，但逐行证据列 W34 命中 0/8，销账依据只活在 `w34-acceptance.md` §2.4 加 §2.5 实跑摘录、没回写进行内；② 判据 7 正常态 OK 靠这些行历史自带的「文件:行」加「实跑」字样，正则只匹配形态、不校验指针是否指向本波复核、不校验 CLOSED 证据内容真伪 ⇒ 与 W21-01、W23-01、W31-01 销账范本同型；③ 用户已裁案 b 维持删除，本波裁决留痕又新增 6 处 `source-review` 引用 ⇒ 全树现役 docs 实跑 31 处 / 13 文件（出现次数口径；外审 32/14 为版本时点），而裁决 #4 明写「判据 8 不新增、避免出生即红」） | W35 | `OPEN` | **触发信号**：用户问「W30-01 凭什么关」而行内找不到 W34 复核；或注入「把某 CLOSED 行本波复核指路删空」而判据 7 仍 OK；或外部审查按 32 处引用逐条复核全部断链却无任何判据留痕。**缓解动作（零 `.kt`，只改台账 8 行加 checklist 加文档）**：8 行证据列各补「W34 §2.4 加 §2.5 实跑复核」指路（同一提交改、不改历史结论）；判据 7 加必红分支「删空某 CLOSED 行本波复核指路 ⇒ `RED closed without current-wave pointer`」，正常态加必红两条原文入仓；`W34-03` 残余项订正为现役 docs 实跑 31 处 / 13 文件，登记案 b 维持删除、无判据覆盖与裁决 #4 理由；「净 OPEN」写明口径（−5 不含 W19-14 结案、含结案为 −6）；W34 制品 `full_context_1M_stress` 补回 dict 加 `iterations_total=17` 写进 §3.6。**出口判据（带必红变异）**：8 行证据列 W34 命中 8/8；判据 7 正常态 OK 加 注入删空后 RED；`W34-03` 残余项含现役计数 31 处 / 13 文件 加「无判据覆盖」；判据 6 在无 `.git` 快照仍 rc=0 且五类必红 RED；不新开 W35-04 加承接同一批老债。 | `docs/06-deferred-ledger.md` 8 行 加 `W34-03` 残余项；`docs/notes/w29-closeout-checklist.md` 判据 7 加 判据 6；`docs/analysis/w34-acceptance.md` §2.3/§3.6；`docs/analysis/w33-acceptance.md` §5.4；本波全树 source-review 计数 加 判据 6 与 7 复刻原文 |


### W36 段（2026-11-20，3 行：3 `OPEN`；先清零脱敏与判据波次盲区，再把收尾契约和 F6 结论同步）

> 上游依据：用户 2026-11-20 指令（克隆最新 main、六份审查报告裁决分歧、不改方案大体方向、多模型×不同上下文直至 1M×功能面真机矩阵、先本地 UI 与 adb 调试→真机→推仓库与 CI、收尾契约止于交接）；本轮外审《W35 深度审查与 W36 排期裁决》《W36 段挂账草案》等对 `8af4090`→`d1c966b`→`bb757a2` 的逐文件复核结论。
> 落笔前置实跑：全表行首编号扫描 `W36` **0 行**（`W26`:5 属 W2.6 段，`W27`–`W35` 各 3 行），号段自由留痕；
> G20 无 ID 唯一判据且不查 `CLOSED` 证据真伪 / 不查本波复核指路 / 不查脱敏新形态 ⇒ 人工号段预检加必红变异加内容复核兜底，改守卫属用户裁决域。
> 边界：3 行全零 `.kt`、**只改 W35/W34 现有载体**（不新建第 6 份 checklist / 制品 / 不重出 W30 及更早正文）；需 `.kt` 的压缩诊断码 / 调用时机 / `promptContext()` CE 重抛整包顺延（挂老号，不新开行）。
> 3 行对 G20 并集零贡献（本波 handoff 只引用台账既有行首号）；tip run 按既定降级规则只留痕不判 P0；台账由主理人单人落笔。
> 外审口径订正留痕：上轮「8 行销账证据列 `W34` 命中 0/8」是 `split("|")[-1]` 取到行尾空串的脚本 bug，用 `[-2]` 复核 W34 快照 8/8 本就含「W34 销账」段；准确缺陷是「无本波复核指路四 token 锚点」，W35-03 已按此补 8/8；上轮「自动加手动同快照分裂 ⇒ `:407` 是唯一差别」过强，W35 不同时刻 reconciliation 与本波 F6 同一自动路径 11 秒 noop→applied 反证，命题②降级。

| ID | 项 | 开立于 | 状态 | 触发信号 / 缓解动作 | 证据 |
|---|---|---|---|---|---|
| W36-01 | 脱敏两类第 4 次回退加判据 3b 扫描面不覆盖加被忽略文件绕过闸门（① `w35-acceptance.md:84` 写回设备序列号（8 位十六进制，W36 脱敏不复述完整值）、`:128` 写回本机绝对路径（盘符工作区根，W36 脱敏不复述完整值）、`:130` 写回本机绝对路径（Git Bash 挂载形态，W36 脱敏不复述完整值）；`evidence/w35-matrix-summary.json:6` 的 `baseline_note` 与 `:71` 的 iter1 判据、`evidence/raw/w35/iterations.jsonl:1` 同样含 serial ⇒ 全树 **serial 4 处 / 本机绝对路径 2 处**，而 W34 树两类均 0 命中；② 制品 `privacy` 仍写 `screenshots desensitized (no key/account fields)`、`w35-acceptance.md:84` 同句写「证据/制品/本报告均不含密钥全文」⇒ 自证句与事实冲突；③ §5.6.1 明写 `iterations.jsonl` 被 `.gitignore:42 *.jsonl` 忽略、用 **`git add -f` 强制纳入**，且自称「内容已核验脱敏：serial 仅 8 位」——该 8 位十六进制串本身就是完整设备序列号，`-f` 绕过忽略规则的同时绕过脱敏复核；④ checklist 判据 3b 正常态只 `grep -rniE` 凭据文件名族，本环境复跑仍 `OK no credential filename`，serial / 本机路径 / PAT 长度元信息全不在扫描面 ⇒ 3b 与 G20 双绿、README「空转的判据比没有判据更危险」换面复发；⑤ CI `44583693221` Secret scan success 说明扫描器规则同样不覆盖这两类，W13 清零成果回退） | W36 | `CLOSED` | **触发信号**：外部审查按 W13 纪律复扫全树得 serial 或本机路径命中；或把制品 `privacy` 读成脱敏达标；或注入「把 serial 写进 `iterations.jsonl` 再用 `-f` 纳入」而判据 3b 仍 `OK`；或下一波再引入被忽略文件不经脱敏直接入库。**缓解动作（零 `.kt`，一次纯文档加制品提交）**：`w35-acceptance.md:84/:128/:130`、`w35-matrix-summary.json:6/:71`、`iterations.jsonl:1` 六处改占位（serial 改 `<device-serial>`、本机路径改 `<workspace-root>`）；制品 `privacy` 改「正文曾含设备标识、已清零」据实口径；判据 3b 扫描面加「设备序列号族 / 本机绝对路径 / PAT 长度元信息」三类并补正常态 `OK` 加注入 `RED` 两条原文入仓；**给 `git add -f` 增设「被忽略文件必先过脱敏」人工闸门**并写进 §5.6.1（不改 `.gitignore` 规则本身）；§5.6.1 删 PAT 长度与校验路径等凭据元信息段（W34 先例：轮换属用户侧动作）。**出口判据（带必红变异）**：全树设备序列号（8 位十六进制）加本机绝对路径（盘符/挂载两种形态）0 命中（W21 历史 `C:\Users\WB` 注记除外，**本行注记引用亦除外**）；3b 正常态 `OK`、注入 serial 或本机路径各得 `RED`；制品 `privacy` 不含「desensitized」绝对化措辞；注入「`-f` 纳入含 serial 的 jsonl」⇒ 人工闸门判红（G20 不覆盖，人工兜底明写）。 | `docs/analysis/w35-acceptance.md:84/:128/:130/:157`；`evidence/w35-matrix-summary.json:6/:71`（`baseline_note`/iter1 判据/`privacy`）；`evidence/raw/w35/iterations.jsonl:1`；`docs/notes/w29-closeout-checklist.md` 判据 3b（只扫凭据文件名族）；`docs/analysis/w35-acceptance.md:156`（`git add -f` 自述）；CI `44583693221`；本环境全树 grep 与 3b 复跑；**W37 本波复核指路**：四份外审报告逐条复核（W36 深度审查裁决 §1、双约束分析 §2.1、harness-w36-review §二）确认脱敏清零与 3b-ext 双态实跑在案；本波判据 6 对 w36 制品正常态 OK 加十一类必红全 RED（checklist W37 实跑段）；`w36-acceptance.md` §5.6.1 三处内容订正已落 diff。 |
| W36-02 | 判据 7 波次盲区加判据 6 只查字段存在（① checklist `:355` `new_wave` 正则只含 W30 至 W34 五段、**不含 W35**、`:362-363` `CUR_WAVE` 默认 `W34` 且 `CUR_IDS` 硬编码 8 个 W34 销账 ID ⇒ 本环境在 `/tmp/w35nogit` 无 `.git` 快照注入「把 `W35-01` 改 `CLOSED` 且不带任何本波锚点」后原样重放，判据 7 仍输出**双 `OK`**：`OK closed rows carry runnable pointers (W30+)` 加 `OK closed rows carry current-wave pointers (W34)` ⇒ W35 花整波加固销账范本，**W35-01/02/03 下一波销账时完全不受约束**，与 G20「锚定历史集合」同型；② 判据 6 的 `K_assertion_presence` 只判 `"assertions" in d`，**不判** `mutation_cases` / `mutation_selftest` / 断言数等于必红数 / `all_verified` 真伪 ⇒ W34 式「补字段但 selftest 自报」仍可过，W35 制品 `mutation_selftest` 仍是 `{total:6,rejected:6,all_verified:true}` 规则自证、note 只指 checklist；③ `mcp` 字段 schema 跨波变化（W34 有 `status`/`evidence_ref`/`reason`/`trigger`，W35 只剩 `cell`/`server_count`/`coverage_note`）而判据 6 不检查；④ W35-01/03 出口判据写「五类必红」「全树 `§2.5a` 0 命中」「8 行证据列 `W34` 命中 8/8」三处弱判据，实跑为**六类**必红、`§2.5a` 16 次 / 7 行（均为缺陷描述语境、按 W21-01 范本属注记引用除外但行内没写豁免）、四 token 锚点 8/8） | W36 | `CLOSED` | **触发信号**：下一波销 W35-01/02/03 时证据列无本波复核指路而判据 7 仍双 `OK`；或注入「删 `mutation_cases` 但留 `assertions`」而判据 6 仍绿；或外部审查按「五类必红 / 0 命中」出口判据复核得不一致。**缓解动作（零 `.kt`，只改 checklist 加台账三行出口列）**：`CUR_WAVE`/`CUR_IDS` 参数化（默认从「本波新 `CLOSED` 行」自动推导，至少覆盖 W35-01/02/03）、`new_wave` 正则加 W35 与 W36 两段；补必红「W35-01 改 `CLOSED` 且无锚点 ⇒ `RED closed without current-wave pointer: W35-01`」并把 W35 段三行将来销账锚点格式预写进 §六；`K_assertion_presence` 扩成「`assertions` 加 `mutation_cases` 加 `mutation_selftest` 三字段齐 + 数量相等 + selftest 指向仓内可跑输出」并补对应必红；三处出口判据改成实跑口径（六类 / `§2.5a` 注记引用除外 / 四 token 锚点 8/8）；引用计数写明「仅 docs 下 .md 为 31 处 / 13 文件，全树含 `evidence/w19-matrix-summary.json` 为 32 处 / 14 文件」。**出口判据（带必红变异）**：判据 7 对「W35-01 改 `CLOSED` 无锚点」输出 `RED`；判据 6 对「删 `mutation_cases`」「数量不等」「selftest 无仓内指路」各输出 `RED`；正常态在**无 `.git` 快照**仍 rc=0 且六类必红 RED；W35-01/03 出口文字与实跑摘录逐值一致。 | `docs/notes/w29-closeout-checklist.md:355/:362-363/:285/:479`（判据 6/7 与覆盖范围）；`docs/06-deferred-ledger.md` `W35-01`/`W35-03` 出口列；`docs/analysis/w35-acceptance.md:54/:76/:183-185`；`evidence/w35-matrix-summary.json`（`mutation_selftest` 自报）；本环境 `/tmp/w35nogit` 无 `.git` 快照判据 6/7 复刻与 W35-01 注入双 `OK` 输出；**W37 本波复核指路**：三类新必红注入块已补并实跑 RED（删 `mutation_cases` / 数量不等 / selftest 无仓内指针）、`K_privacy_no_key` 与 `K_coverage_13` 判定已进 `k6_check.py` 并各带自证必红、判据 7 默认从台账「本波销账」声明自动推导（正常态 declared=0、注入 W36-01/03 翻 CLOSED 无指路 ⇒ `RED closed without current-wave pointer` 实跑）；判据冻结声明已落 checklist。 |
| W36-03 | 收尾契约第 14 次加推送次数少 1 加 F6 结论半迁移加补编证据不可追溯（① `w35-acceptance.md:16-24` §1.1 九项结果列 **6 项「进行中」加 2 项「待跑」**、`:67/:68/:69` 裁决三条「进行中（审查席）」、`:203` §七把 `handoff-20261120-wave35.md` 标「进行中（待 CI 席推送后由主理人落笔）」，而同文件 §三.3/§四/§5.6/§六 与 handoff §三 全判 ✅，且 tip `bb757a2f7` 提交消息正是「新增 W35 交接文档」；② `:170-173` 写「主体推送 1 次加回填第 2 次」= 2 次，实际提交序列 **3 次**（`8af409071`/`d1c966b76`/`bb757a2f70`），`44584106741`/`44584268635` 两条 tip run 不在仓内；§5.6.1「45 文件」是主体 commit 数（API 逐值吻合）、整波 **46 唯一文件**，「6 改」实 5 改且把 `w35-acceptance.md` 重复计数；③ F6 三处仍按旧根因：`W9-01` 命题②「自动加手动分裂来自 `ChatViewModel.kt:407` 在 `onEvent` 之前取旧快照」与同行「同快照矛盾不成立」自相打架、`W35-02`「项」列保留「同快照分裂」、`f6_auto_compact.json` note 写「比 W34『自动 noop vs 手动 applied 分裂』更进一步」，而 §2.1 #3 刚裁不同时刻 reconciliation 且本波 F6 **同一自动路径 11 秒** `17:42:14 net=-256` → `17:42:25 auto dropped=4 kept=10 applied` ⇒ 决定性变量是体积加试算能否压动，不是 `:407`；④ handoff §116 写「34 件真机席原始加主理人补编 2 JSON 加 iter14-16（**均标注来源**）⇒ 38 件」，但 `evidence/raw/w35/` 全树搜「主理人 / 补编 / 据实 / 标注来源」**0 命中**、`iterations.jsonl` iter1–13 紧凑 JSON 与 iter14–16 带空格手写格式无来源字段、34 加 2 等于 36 不等于 38 的另 2 件构成仓内不可机器区分；`f2_workspace.json` note「修正 W34 的双层路径」改历史结论未挂账；⑤ `deviceLogTail.txt` 是 **59 行 logcat**（netd/adbd/screencap）、`auto_compact` 等 4 词 `grep -c` = **0**，F6 真实日志在 `f6_auto_compact_log.txt`，而 `f6_auto_compact.json` 的 `evidence_files` 却列 `deviceLogTail.txt`（W34 同名文件含 36 行压缩日志 ⇒ 跨波同名不同义）；⑥ 功能面 F3 skill 与 F5 手动压缩较 W34 两格 ✅→⚠️，§一/§3.2「本波真机增量」只列 4 条正向未标退步） | W36 | `CLOSED` | **触发信号**：外部审查按 §1.1 读到「进行中 / 待跑」而同文件已给全量结果；或按 §5.6 判推送次数得 2 而提交序列 3；或按 `W35-02` 下一波优先修 `:407` 而 W35 新证据说明该先修 `nothing_to_drop`；或按 handoff「均标注来源」找补编标记得 0 命中；或按 `f6_auto_compact.json` 的 `evidence_files` 打开 `deviceLogTail.txt` 找不到 applied 行。**缓解动作（零 `.kt`，一次纯文档加制品提交）**：§1.1 九项加 §2.1 三条加 §七 handoff 行统一改 ✅ 并加时点抬头；推送 2→**3**、补两条 tip run 链接或按降级规则写明「不补」、§5.6.1 注明「45 文件为主体 commit、整波 46 唯一文件、5 改」；**F6 三处同步**：`W9-01` 命题②改「`:407` 时机差存在（代码事实）、未证实为 W34 差异成因；决定性变量是体积加试算能否压动」并把 17:42:14/17:42:25 两行写进证据列、`W35-02`「项」列删「同快照分裂 / 唯一差别」、`f6_auto_compact.json` note 改「同一自动路径 11 秒 noop→applied、两个体积时刻两个正确答案」；下一波压缩主线优先级随之改（先 `nothing_to_drop` 加 `outcome.applied` 前置判，再 `:407`）；38 件里主理人补编的 4 件**逐件加 `source: 主理人汇编 / 依据 <原始文件>` 字段**、iter14–16 补来源、`f2_workspace.json` 的 W34 订正挂 `W34-03` 行内注记；`f6_auto_compact.json` 的 `evidence_files` 改指 `f6_auto_compact_log.txt`、`deviceLogTail.txt` 加「设备 logcat 尾（非 AgentLogStore）」抬头；§一/§3.2 补「两格因判据更严退 ⚠️、非回归」。**出口判据（带必红变异）**：`w35-acceptance.md` 的「进行中」「待跑」「待 CI 席推送后由主理人落笔」0 命中；§5.6 含「推送 3 次」；`W9-01` 加 `W35-02` 加 `f6_auto_compact.json` 三处不再含「同快照分裂 / 唯一差别」且都含「11 秒」或「体积」；38 件里补编件 `source` 字段可 grep；`f6_auto_compact.json` 的 `evidence_files` 指向的文件含 `dropped=4`；注入「把 §1.1 占位写回」⇒ 人工复核判红（G20 不覆盖，人工兜底明写）；不新开 `W36-04+` 承接同一批老债。 | `docs/analysis/w35-acceptance.md:16-24/:67-69/:105-110/:170-173/:203`；`docs/handoff-20261120-wave35.md:16/:82/:116`；`docs/06-deferred-ledger.md` `W9-01`/`W35-02`/`W34-03`；`evidence/raw/w35/f6_auto_compact.json` 加 `f6_auto_compact_log.txt` 加 `deviceLogTail.txt` 加 `iterations.jsonl` 加 `f2_workspace.json` 加 `f5_manual_compact.json`；三次 commit files API（45/1/1）；本环境 F6 日志逐行与「补编标注」0 命中复核；**W37 本波复核指路**：`w36-acceptance.md` §5.6.1「8 改」→「6 改」、删 PAT 长度元信息句、§5.6.2 推送次数 2→3（补 tip `8ed8304` 第 3 次推送 run `44719951896`）三处 diff 在案。 |

### W37 段（2026-11-20，0 行新开：**本波新开挂账 0 / 本波销账 3**；判据冻结落地 + 全面推进转代码波）

> 上游依据：用户 2026-11-20 指令（读记忆/上一波 handoff/OPEN 行、六份审查报告裁决分歧、**全面推进**转代码波、判据冻结、守恒式 `新开 ≤ 销账`、新 API 多模型×上下文直至 1M 真机矩阵、本地 UI/adb → 真机 → 推仓库与 CI、收尾止于交接）。
> 守恒式两数（轻量版，只在派单填、不进波内部、不新增验证动作）：**本波新开挂账 0 / 本波销账 3**（W36-01/02/03）。基线 W24→W35 净 +1.7 条/波（W36 单波 +3），本波拉回 0。
> 本波销账：W36-01、W36-02、W36-03
> 落笔前置实跑：全表行首编号扫描 `W37` **0 行**（`W26`:5 属 W2.6 段，`W27`–`W36` 各 3 行），号段自由；判据 6 十一类必红全 RED、判据 7 默认自动推导（**本波正常态 declared=3**、`declared=0` 仅为漏写声明的空命中语义示例、注入无指路 RED）原文见 checklist W37 实跑段。
> 边界：本波为**转代码波**（W36「授权未落地」自 W37 起失效），修的是老号（`W9-01`/`W17-02`/`W12-02`/`W20-02`），不新开行；1M 真压（`W20-05`/`W9-11`）、MCP 明文（`W5-03/05/06`）、journal（`W9-05`/`W24-03`）仍需单独裁决或解除外部阻塞，不冒充 ✅。
> 判据冻结：用户 2026-11-20 指令已落 `docs/notes/w29-closeout-checklist.md` 判据冻结声明（v1 定版 + 唯一允许三类动作 + 禁止新增判据/制品/CI job/枚举）；本波只做「让既有判据真会响」的补齐，不新增判据条目。
> 台账由主理人单人落笔；0 行新开对 G20 并集零贡献（本波 handoff 只引用既有行首号）。

### W38 段（2026-11-20，0 行新开：**本波新开挂账 0 / 本波销账 1（W17-02）**；全面推进 + 判据内容订正 + 真机多模型矩阵）

> **本波销账：W17-02**（标准声明行；W39 第 0 车补齐判据 7 可识别的冒号格式，两数不变：新开 0 / 销账 1）

> 上游依据：用户 2026-11-20 指令（四份审查报告分歧裁决、全面推进、判据冻结、守恒式、新 API 多模型×上下文直至 1M 真机矩阵、本地 UI/adb → 真机 → 推仓库与 CI、收尾止于交接）。
> 守恒式两数（派单卡口径）：第 0 车 **新开 0 / 销账 0**（纯内容订正与老号注记，不注水销账）；第 1 车 W17-02 满足出口判据（suspend 路径 runCatching 清零 + CE 重抛直接用例 7 条实跑绿）⇒ **本波合计新开 0 / 销账 1（W17-02）**，`0 ≤ 1` ✅。全波不新增任何 `W38-` 行。
> 第 0 车动作（一次提交）：P0-1 `K_coverage_13` 去硬编码（格数由 `cells_total` 自证，w32=14/w37=13 双 OK、十一类必红全 RED）；P0-3 四行老号（`W9-01`/`W17-02`/`W12-02`/`W20-02`）补 W37 行内注记、状态维持 OPEN；内容订正（冻结声明补 `feature_face` 值域表、`declared=0`→`3`、`w37-acceptance` 六处错数、F6 跨包拼接措辞、判据 1 守恒式两数回显、`W9-01` 补 `alreadyCompressed` 单指针）。
> 第 1 车动作：ExtrasViewModel 同源吞 CE 清零 + G10 拆分前置 + 已备处方（W24-02/W24-01/W17-02/W12-02/W9-01），修完的老号逐条销账、新开恒 0。
> 台账由主理人单人落笔；0 行新开对 G20 并集零贡献。

### W39 段（2026-11-20/21，0 行新开：**本波新开挂账 0 / 本波销账 1（W24-02）**；第 0 车判据链收口 + 三方拆分 + 断流恒等 + 13 格真机）

> **本波销账：W24-02**（标准声明行；两数：新开 0 / 销账 1）

> 上游依据：用户 2026-11-20 指令（读四份 W38 外审报告并裁决分歧、全面推进、判据冻结、守恒式、多模型×上下文直至 1M 真机矩阵、本地 UI/adb → 推仓库与 CI、收尾止于交接）。
> 守恒式两数（派单卡口径）：第 0 车 **新开 0 / 销账 0**（判据链内容订正，不注水）；第 1 车 W24-02 满足出口判据 ⇒ **全波新开 0 / 销账 1**，`0 ≤ 1` ✅。全波不新增任何 `W39-` 行。
> 第 0 车动作：判据 7 正则容错（冒号可省）+ W38 段标准声明行 + `W17-02` 本波复核指路锚点；判据 1 默认 `WAVE` 由台账最大段号推导、`EXPECT` 由「本波新开挂账 N」同源推导、去 `cd harness-v2` 改 `REPO_ROOT`；内容订正（`K_coverage_13`→`K_coverage`、`w37-acceptance` 净 −9→+17、`coverage_note`/F6 note、INC 措辞、冻结声明括号与「局部内容订正 ≠ 重出历史制品」边界）。
> 第 1 车动作：三方拆分（`core-context` 1649→1477、`feature-chat` 3996→3978、`feature-settings` 3495→3417、`ChatViewModel` 500→447；`LINE_CAPS` 未调、`TOTAL_LINE_CAP` 未抬）+ W24-02 断流三处（`EXPECTED_TESTS 829→845`）。
> 真机：13 格全量复测 `state_counts={PASS:6, PARTIAL:4, VACUOUS:2, FAIL:1}`（E03 north 404 实证、F1 视觉转 PASS、T03 1M 读数与连通解除 403、F6 首次触及 `nothing_to_drop`）。
> 台账由主理人单人落笔；0 行新开对 G20 并集零贡献。
