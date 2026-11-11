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
| W9-01 | 压缩量纲与切换守卫统一（4.0 尺四面 / 触发线按档位 / 单指针→尾部摘要前置） | W8 | `OPEN` | W9 开波第一件事；**W11 第 0 车补完一处，仍未整体关闭** | 复审41 C3 + w67 P0 + W8深度审查 P0-1/P0-2/P0-4/P0-5/P1-1 + NEW-A/B/C；四外审同一缺陷**禁拆多条**（附外审来源索引）。**W10 已推 `98916eaa`**（S1 量纲同源 / S2 触发线按档位 / S3 尾部摘要前置 / S4 附件进估算面），但本行当时零 diff。**W11 第 0 车补完（外审缺口 1 的最后一处）**：`core-context/.../HardBudgetShrinker.kt` 的 `promptChars()` 补 `sendableAttachments` 遍历（图片按 `ESTIMATED_IMAGE_TOKENS × CHARS_PER_TOKEN_ASCII` 同源折算、不进指纹）+ `:87` KDoc 由失实的「与 estimateMessages 一致」改为逐项同源；`HardBudgetShrinkerTest` 两条用例（删遍历 ⇒ `attachmentsAreCountedOnBothBudgetScales` 必红）。**仍未做**：`ContextCompressor.kt:50/61` 两个 `DEFAULT_*_RATIO` 搬进 `AgentPolicy`（外审报告1 §四末），故保持 `OPEN`。🔴 **标定面耦合（W11 审查席证伪本波初版判断，已修）**：初版让 `GenerationRound` 继续用含图片当量的 `promptChars` 做 chars/token 标定；provider 一张图只计 85–255 视觉 token 时，`measuredCharsPerToken = 4096/视觉token` 会被顶到 `ceiling=6.0`，而 `charCap = hardBudget × cpt` 相对 floor 1.8 **放大 3.3 倍 ⇒ 超窗方向，不是保守方向**。修法 = 新增 `HardBudgetShrinker.calibrationChars()`（只算文本/工具字符，不含图片当量）供标定；预算面与 L3 字符闸仍用 `promptChars`（含图片，宁可多算）；两者共用 `textChars`/`attachmentChars`，不是两把独立的尺子。红态用例 `HardBudgetShrinkerTest.calibrationCharsExcludeTheEstimatedImageAllowance` |
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
| W12-02 | usage 四态 `PromptCacheStatus` 缺生产读者：唯一生产调用点 `StreamAccumulator.cacheStatus` 在 main 零读者（外审 P0-1 剩余面） | W12 | `OPEN` | 给四态一个生产读者 —— 探针事实表 / 诊断日志 / 或 `scripts/` 设备侧缓存探针按四态落盘；与 `W9-07` 探针波或 `W11-05` 分母族归一同车 | W12 外审 `W11第0车深度审查与W12排期裁决.md` §三.2 P0-1；W12 回树 grep 证实（`promptCacheStatus(` 排除 test 仅 `StreamAccumulator.kt:226` 一处，`.cacheStatus` main 零命中）。W12 真机 F7 实测 `buildUsageLabel` 三态正确（首轮 `0 cached, 0.0%`=MISS、二轮 `1664 cached, 96.5%`=HIT），用户可见面未缺口 |

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
| W16-03 | 拆分三方（`core-context` 余 4 / `feature-chat` 余 12 / `feature-settings` 余 8）、四态生产读者、分母族归一、jsonMode × tools、五条 ≤30 行快修 | W16 | `OPEN` | **触发信号**：`core-context` 余量见底或新增 `.kt` 触碰 G10；**缓解动作**：先拆再改，禁调 `LINE_CAPS` / `TOTAL_LINE_CAP` / `VACUOUS_BASELINE` | 外审 §五 第 2 车；参照 `W15-03` / `W14-03`，本波零 `.kt` |
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
| W16-18 | **W16 后补充重测新登记**：导入会话（调试）只真正加载约 1 条 —— 300 条 / 257 kB 夹具经 SAF 导入后，下一请求仅 `1.6k in`，致手动压缩无可压中段（`dropped=0`）；本波未解 | W16 | `OPEN` | **触发信号**：手动压缩 `dropped=0` 且下一请求 `in` 远小于夹具字节；**缓解动作**：单独排查导入链路（SAF 选文件后是否缺确认步 / JSONL 解析 / 会话加载到对话页），须独立成波；**属导入链路疑点，非压缩功能缺陷** | `docs/analysis/w16-matrix-acceptance.md` §5.7 |

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
