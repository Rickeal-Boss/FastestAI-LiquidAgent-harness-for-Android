# 06 — 挂账单一台账（deferred ledger）

> **本文件是挂账的唯一条目来源**。`docs/handoff-*.md` 里的「## 挂账」表是**开立记录**
> （写那一波时的事实），不再是状态来源。判状态、判是否丢失，只认本文件。
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
| W3-05 | journal / FreezeClock（roadmap 原 W3 内核硬化全部内容） | W3 | `OPEN` | **尚未开波**；W6 方案刻意不开（与 A 流 `RunState` 改动冲突），建议独立 W6.5 | 全仓**零实现**：按 `Journal` / `FreezeClock` / `Ledger` / `Repetition` 四类 `find -iname` 全空；`grep -rn FreezeClock --include=*.kt` 仅 5 处**注释**（`core-agent/loop/GenerationRound.kt:117,121`、`core-agent/loop/RunState.kt:210`、`core-kernel/.../policy/AgentPolicy.kt:168`、`core-agent/.../GenerationRoundTest.kt:38`）⇒ 注释写的是一个不存在的机制 |
| W3-06 | 长上下文设备侧行使 | W3 | `CARRIED` | 承接为 W5-04 | 见 W5-04 |
| W3-07 | 逐端点 `supportsVision` | W3 | `OPEN` | W6 C 流列为可选（端点编辑页加开关 + 探测） | `core-engine/RemoteLlmEngine.kt:92` `supportsVision = false` **硬编码**，`core-model/GenerationEvent.kt:136` 的字段默认值也是 false，端点无字段可覆盖 ⇒ 视觉能力由服务端 400/404 暴露 |
| W3-08 | 设备→OpenRouter 链路抖动 | W3 | `CLOSED` | 关闭于 W5（归因被证伪） | `handoff-20261101-wave5.md` §三 E1–E4；E3（错密钥 + 对域名 ⇒ HTTP 401）证明连接/TLS/请求/响应四步全通。按纪律记「无法证伪」，**不**写成「已证伪」 |
| W4-01 | 设备→OpenRouter 请求不收敛（承接 W3-08） | W4 | `CLOSED` | 关闭于 W5 | 同 W3-08。`docs/notes/w5-network-triage.md` E1–E4 |
| W4-02 | MCP 真机联调 | W4 | `CARRIED` | 承接为 W5-03 | 见 W5-03 |
| W4-03 | 1M 设备侧行使 | W4 | `CARRIED` | 承接为 W5-04 | 见 W5-04 |
| W4-04 | 免费池 12/14 不可用 | W4 | `CARRIED` | 承接为 W5-07 | 见 W5-07 |
| W4-05 | C 组：首字节看门狗 / 等待态 / cached 三态 | W4 | `CLOSED` | 关闭于 W5 | 看门狗 = `core-agent/loop/GenerationRound.kt:112,165-167`（`generationTimeoutMillis` 的唯一消费点）；等待态 `feature-chat/ChatUiState.kt:61` `WAITING_FIRST_BYTE`；三态 `core-model/Generation.kt:56` `cachedPromptTokens: Int? = null` |
| W4-06 | `feature-settings` 只有 1 条测试 | W4 | `CLOSED` | 关闭于 W5 | 实测 21 条（`grep -rnE '^\s*@Test\b' feature-settings` = 21），含窗口档位边界钳制 |
| W4-07 | 会话级 `maxContextTokens` 对已缓存 runner 不生效 | W4 | `CLOSED` | 关闭于 W5 | `core-data/RunnerCache.kt:21` `RunnerKey(endpoint, maxContextTokens)` ⇒ 档位进键即重建。⚠️ 残留窄窗口（`AppContainer.kt:327` 的 `runners.remove()` 绕过 `keepWhile`），见 W6-04 |
| W5-01 | 压缩判定点下沉（轮头 → 工具结果提交后 / pre-step 单点） | W5 | `OPEN` | W6 A 流实施中 | 真机证据 `handoff-20261101-wave5.md` §五.1：32K 档预算 32 000，实发 **33 800 token** 且零压缩；根因订正见 `docs/notes/w6-plan.md` §2.1（判定点位置**不是充分原因**，M1 估算器低估 + M2 压缩器无损放弃同时成立）。**W6 进展**：JVM 判据 T1–T7 全绿（字符量纲 + 标定比值 + 熔断 `generateCount==0`）；**真机重放未完成** —— 32K 档 5×16 KB 并行读的请求在免费模型侧 `SocketTimeoutException` 失败，未拿到「实发 ≤ 27 904」的真机证据。按 2026-11-02 裁决**移交 W7-01** |
| W5-02 | `contextLength` 超报（1 048 576 vs 实测 1 000 000 含输出） | W5 | `OPEN` | W6 A 流实施中 | `docs/notes/w5-provider-matrix.md`（1 000 083 → HTTP 400，9 次复现；999 662 + 338 = 1 000 000 顶死）；预算算式 `core-kernel/.../context/ContextCompressor.kt:37-41` 已减 `maxOutputTokens`。**写回链现状（W6 复验，取代此前「零调用点」的判断）**：四跳全接 —— `ChatViewModel` → `ConversationOps` → `core-data/ContextLimitWriteback.offer(...)`（**在 `:core-data`，不是 UI 层**，`record` 是注入式 lambda）→ `AppContainer.recordContextLimit`；判据 = 三跳各有断言 + 第四跳 `ContextLimitWritebackTest` **5 条纯 JVM 用例**（「恰好一次 + 参数正确」/ hint 为 null、0、-1 零调用 / 端点 id 为 null 或空白零调用 / 普通异常静默）+ **第 6 条「`record` 抛 `CancellationException` 原样重抛」已跑验通过**（产物 `tests=6 / failures=0`；三处源 mtime `AppContainer 21:28` / `ContextLimitWriteback 21:44` / 测试 `21:52` 均早于产物 `21:56`）⇒ **四跳全接且全有判据，该子项闭合** |
| W5-03 | MCP 真机联调（承接 W3-03 / W4-02） | W5 | `OPEN` | 平台阻塞：需 https 夹具或 cleartext 例外**裁决**（安全取舍，单独出裁决文档） | `app/src/main/AndroidManifest.xml:25` `usesCleartextTraffic="false"`（G15b 要求，**不为测试放开**）⇒ 明文 http 被平台拒（`error: transport_error`） |
| W5-04 | 设备侧 1M 行使（承接 W3-06 / W4-03） | W5 | `OPEN` | 阻塞：无批量题面注入通道（W6 C 流的「会话导入」是解锁器） | PC 侧 994 998 命中（`docs/notes/w5-provider-matrix.md`）；设备侧最大只跑到 2.5k。`handoff-20261101-wave5.md:135` D12 = ⛔ vacuous |
| W5-05 | `autoApproveDangerous` 无消费点 | W5 | `OPEN` | W7 限期二选一：接线（UI 开关 + 审计账行 + 三条用例转正式消费点）或连字段带用例一起删 | `core-kernel/.../policy/AgentPolicy.kt:115` 仅定义；生产消费点 **0**（复验：`grep -rn autoApproveDangerous --include=*.kt` 只命中定义 + `core-agent/.../ApprovalChokepointAuditTest.kt:241,276,300` 三条警报用例）。⚠️ 那三条是「一旦有人接线就立刻红」的**警报**，不是「防护已生效」 |
| W5-06 | MCP `sampling` / `roots` 未实现 | W5 | `OPEN` | 落地时必须补进 `ApprovalChokepointAuditTest` 穷举表（现缺两行） | `grep -rn "sampling" core-mcp` 与 `grep -rn "roots/list" core-mcp` 均 **0 命中**（全仓只命中 `SamplingParams` 采样参数，与 MCP 协议无关）。没有为凑行数造假用例 |
| W5-07 | 免费池 12/14 不可用（承接 W4-04） | W5 | `OPEN` | **非代码项**：需账号持有人开隐私开关 | `docs/notes/w4-provider-matrix.md`（12 个被 `Free model training violation` 挡住）；W5 用 NVIDIA 绕开 |
| W6-01 | `AgentPolicy.compressContext` 零写入点（默认 true，无人设置） | W6 | `OPEN` | W6 A 流加一行 WARN（关闭时打 `compress=disabled_by_policy`） | 定义 `core-kernel/.../policy/AgentPolicy.kt:136`；唯一读点 `core-agent/loop/ContextBudget.kt:157`；全仓**零写入点**（无 UI、无 preset）⇒ 一旦有人接进设置页并默认关，**全部压缩静默消失且无任何日志**（`w6-plan.md` §2.1 M4）。这是「声明了没消费者」的第 3 例 |
| W6-02 | `EngineCapabilities` / `capabilities()` 零 main 调用点 | W6 | `OPEN` | 若要拿它当窗口上限通道，必须先接线；否则删（`w6-plan.md` §2.5b 明确不信任它） | `core-agent/engine/LlmEngine.kt:47` 只有接口定义；`core-data/RemoteEngineBridge.kt:33` 是 override 委托、`core-engine/RemoteLlmEngine.kt:89` 是实现；**main 源集零调用点**（只有 5 处测试 override）。这是「声明了没消费者」的第 4 例 |
| W6-03 | 守卫空转清单（`VACUOUS` 基线） | W6 | `OPEN` | 棘轮只许减不许增（`scripts/arch-guard.sh` 的 `VACUOUS_BASELINE`） | 首版 `VACUOUS=1`：**G19**（日志不拼凭据）扫的是空集 —— `grep -rn 'AgentLogStore\.' core-engine/src/main` = **0 命中** ⇒ 它对现役代码判不出任何东西，灵敏度只由 selftest 的变异自证保证。**不要**读成「凭据已经安全了」 |
| W6-04 | `saveEndpoint` 的 `runners.remove()` 绕过 `keepWhile`（W4-07 残留窄窗口） | W6 | `CLOSED` | 关闭于 W6（A 流） | 已改为 `core-data/AppContainer.kt:333` `runners.removeIfIdle(normalized.id) { it.isBusy }`（`recordContextLimit` 同样处理，`:369`）；实现 `core-data/RunnerCache.kt:93` `removeIfIdle`；钉住用例 `core-data/.../RunnerCacheTest.kt:107,118`。⚠️ 残留（有意保留）：`deleteEndpoint` 仍走 `runners.remove(id)`（`:376`）—— 端点已删，在途 run 的引擎指向已删端点属另一类问题。来源：`03-w5-review-and-next.md` §二 P2 |
| W6-05 | `allowClipboardRead` 缺设置页开关（保存后下一次 run 生效） | W2.5 | `OPEN` | 推 W7（与 `ApprovalPolicyScreen` 同波） | 字段有真消费点：`core-agent/.../ClipboardTool.kt:78` `if (!policy.allowClipboardRead && actionOf(...) == Action.GET) READ_DISABLED`；但**全仓无 UI/无 setter 写入它**（`grep -rn allowClipboardRead` 只命中 ClipboardTool + 3 个测试文件）。**原交接 W2.5 §八.4 只写了「W7 挂账」，未给编号 ⇒ 本条是本台账补的编号**（无编号的挂项在抄写中必丢） |
| W6-06 | `arch-guard.sh` 一次全量跑出现 G7/G8/G9 各打印两遍（`OK=26` 虚高 3），**不可复现** | W6 | `OPEN` | 待复现；再现时按 `scripts/arch-guard.sh` 头部 K3 的三步查（函数调用次数 / 同 id 两次 guard_ok / 是否有第二进程写同一输出文件） | 2026-11-01 20:47 一次全量跑实测（W6 首次接入 VACUOUS 之后，当次 rc=1）；之后 4 次全量跑（含最终验收 `OK=25 VACUOUS=1 VIOLATION=0`）**均未复现**，根因未定位。**登记理由：不可复现 ≠ 不存在**，不登记则下次出现会被当成新 bug 从头排查。详见 `arch-guard.sh` 头部 K3 |
| W6-07 | `core-kernel` 行数触顶（1 790 / 1 800，余 10）⇒ 拆分 `context/` 为 `:core-context` | W6 | `OPEN` | 下一波第一件事拆；本波只冻结 + 登记 | 现量（守卫口径，`LINE_CAPS` 表 + 逐文件 `wc -l` 累加）：`core-kernel` **1 790 / 1 800**（余 **10**）；三分目录 `context/` **1 387**、`policy/` **235**、`log/` **168**。峰值到过 1 849（W6 加 T7 那阵子），已由 A 流压回 1 790（压掉的是 W6 新增的重复 KDoc，三条判据锚一个没删）。**性质 = 预算到期，不是 W6 违规**：1 800 是 W5 拆分时按「搬过去的 1 557 行 + 余量」给的，被 W6 正常用掉。**处置方向**：`core-kernel/context/` 整体下沉为 `:core-context`，`core-kernel` 只留 `policy/` + `log/`。**三条硬约束（防下一波把它当扩容）**：① `TOTAL_LINE_CAP` **保持 42 500 不动** —— 这才是 W5 铁律 3「不许变胖」的真判据（实测全仓 main = **34 983 / 42 500**，余 **7 517**）；② per-module 上限之和 1 800 → **1 900**，**其中 +100 是拆分开销**（两个模块各自的文件头 + KDoc），必须同时写进台账项与提交信息；③ **禁止「凑 1 800」的分法** —— 那会给 `core-context` 留 13 行，等于把死结从一处搬到另一处。两种合理分法（各自余 45~65 行，之和都是 1 900）：`core-kernel` 上限 450（235+168=403，余 47）+ `core-context` 上限 1 450（1 387，余 63）；或 `core-kernel` 上限 300（仅 policy 235，余 65）+ `core-context` 上限 1 600（1 387+168=1 555，余 45）。**触发条件**：任何往 `core-kernel` 新增代码的 PR ⇒ 先拆再加。**同一病的另外三处（本波只登记不处置，数字为守卫口径实测）**：`feature-chat` 3 459 / 3 500（余 **41**）、`feature-settings` 2 921 / 3 000（余 **79**）、`core-design` 12 439 / 12 600（余 **161** —— 它同时是**全仓最大模块**：占全仓 35.6%、是 `core-agent` 5 326 的 2.3 倍 ⇒ 下一波若要找「哪里该瘦」，收益远大于 `core-kernel` 那 10 行余量） |
| W6-08 | G10 的行数口径对「缺尾换行的 `.kt`」静默少算 1 行 | W6 | `OPEN` | 下一波做「可见性断言」（G10 顺带输出 `缺尾换行=N`，**不判红**，与 G19 的 VACUOUS 同一思路）；本波不动 G10 | `wc -l` 数的是**换行符**，故缺尾换行的文件少算 1 行且不告警。**现役不受影响（已复验）**：13 个模块 `src/main` 的 `.kt` 里缺尾换行 = **0 个**，且三种求和在现役树上一致 = **34 983**（逐文件 `wc -l` 累加 / `grep -c ''` 求和 / `find -print0 \| xargs -0 cat \| wc -l`）。影响量级 = 模块内文件数 × 1 行，对 `core-kernel`（余 10）而言 1 行 = 10% 余量误差 ⇒ 下一波补可见性即可。**两态齐全**（绿态 = 现役 0；红态可构造 = 手动去掉尾换行），所以它**可以是真判据**，但代价是给非标准编辑器保存的人一个额外红 ⇒ 先做可见性、不做判红 |
| W7-01 | **上下文压缩真机重放**（承接 W5-01 的真机部分） | W7 | `OPEN` | 下一波第一件事；阻塞已解除（端点可常驻密钥，见下） | 待证判据：32K 档 + 并行读 5×16 KB ⇒ 请求实发 ≤ `hardContextBudgetTokens = 27 904`（或出现 `hard_budget_shrunk` 且最终仍 ≤ 27 904）。W6 已备好夹具：5 个 16.3 KB 文件已推入设备沙箱 `files/harness/workspace/module_{1..5}.kt`；失败现场 `_w6/d12.png`（`SocketTimeoutException`）。**下一波必须先换掉免费模型**（OR `dots-3-note-preview:free` 在该输入规模下超时），NVIDIA 端点已核实可用 |
| W7-02 | **前缀缓存命中率验证**（承接 W5 缓存结论） | W7 | `OPEN` | 与 W7-01 同波（同一批请求可同时取数） | W6 多模型实测（`docs/notes/w6-provider-matrix.md` §六.3）：`nv-vl` 三连发 `0 → 0 → 4336`（写入延迟 ≥2 次）、`dots` 恒 0、`north` 恒 0 ⇒ **缓存不可依赖**。待证：本仓前缀逐字节稳定时，第 3 次请求 `cached_tokens > 0`；且**不得为凑缓存加长 system**（800 全价 vs 4096×0.5 当量） |
| W7-03 | **手动压缩 / 会话导入真机验收** | W7 | `OPEN` | 与 W7-01 同波 | 仅单测（`ManualCompressTest` 10 条 / `ChatImportTest` 6 条 / `SessionImportParserTest` 19 条）。待证：① 压缩后历史条数下降 + 分隔卡出现 + `manual_compress dropped=N kept=M cache_invalidated=true` 落盘 + 下一请求 `cached_tokens=0`（**双向**，别只看归零）；② 导入 20 条 ⇒ 20 个气泡；带 `role=tool` 的文件显示丢弃计数；8 MiB+ 被拒且提示具体原因；release 包看不到该入口 |
| W7-04 | 8 条模块级恒绿占位（`assertTrue(true)`）的处置：删掉 or 保留并写清口径 | W7 | `OPEN` | 下一波裁；**删则必须与 `EXPECTED_TESTS` 同一次提交** | 全仓 `@Test` = **666**，其中 **8 条恒绿占位**：`app` / `core-agent` / `core-data` / `core-design` / `core-mcp` / `core-skills` / `feature-chat` / `feature-settings` 各一个 `SmokeTest.kt`，每个恰好 1 个 `@Test` + 唯一断言 `assertTrue(true)`（全仓 `assertTrue(true)` 计数 = **8**，逐文件复验）。**行为判据实为 658 条**。它们永远不可能红，按本仓「仅绿 = 注释」不构成判据，历史作用只是让模块 test 源集非空（避免 NO-SOURCE）。**本波决定只改口径不改文件**（W1 存量、非 W6 回归；收口期动 8 个文件 + 一处 CI 基线，一旦并发构建很难区分「删多了」与「daemon 抖动」）。口径已写进 `.github/workflows/build.yml` 的 `EXPECTED_TESTS` 注释。**若删**：① 删 8 个 `SmokeTest.kt`；② `EXPECTED_TESTS` 666 → 658；③ 跑全量确认 `658 / 0 / 0` 且无模块 NO-SOURCE —— **三件必须同一次提交**（先删文件 ⇒ 666/658 红；先改基线 ⇒ 658/666 红） |
| W7-04 | 8 条恒绿占位测试（`SmokeTest.placeholder` = `assertTrue(true)`） | W6 | `OPEN` | W7 与 W7-01 同波删；删后 `EXPECTED_TESTS` 666→658 | `grep -rn "assertTrue(true)" --include=SmokeTest.kt` = **8**（`app`/`core-agent`/`core-data`/`core-design`/`core-mcp`/`core-skills`/`feature-chat`/`feature-settings`）。**本波刻意不删**：删完需改 `EXPECTED_TESTS` 并重跑全量 + 重推 + 等 CI，而收益只是数字更诚实（已如实登记）；风险是某模块可能因此变 `NO-SOURCE`。**口径约束**：关闭前任何引用 666 处必须同时写"其中 8 条为占位，真实断言 658" |

> **为什么是 38 条而不是外审说的 27 条**：27 = 四份 handoff 挂账表里的编号条数（G20 逐条核对的那 27 个）。
> 另 **8 条 W6 系列**：W6-01 / W6-02（本波新发现的「声明了没消费者」第 3、4 例）、W6-03（守卫空转清单）、
> W6-04（外审 §二 P2，代码里确实存在但从未进过任何挂账表，本波已由 A 流修掉）、
> W6-05（W2.5 §八.4 的无编号挂项）、W6-06（守卫自身的一次不可复现异常）、
> W6-07（`core-kernel` 行数预算到期 —— 从未进过任何挂账表，靠 G10 判红才被发现）、
> W6-08（G10 对缺尾换行静默少算 1 行）。
> 另 **3 条 W7 系列**：W7-01 / W7-02 / W7-03 —— 由 team-lead 在 W6 收口时开立（真机重放类：
> 上下文压缩 / 前缀缓存命中率 / 手动压缩与会话导入），本文件只登记不判定。
> 其中 W6-05 / W6-07 / W6-08 三条正是「没有编号 / 没进表 ⇒ 没人跟踪」的活样本。

---

## 二、统计（W6 建立时）

| 状态 | 条数 | 条目 |
|---|---|---|
| `CLOSED` | 9 | W26-01 / W26-04 / W26-05 / W3-08 / W4-01 / W4-05 / W4-06 / W4-07 / W6-04 |
| `CARRIED` | 6 | W26-03 / W3-03 / W3-06 / W4-02 / W4-03 / W4-04 |
| `OPEN` | 24 | W26-02 / W3-01 / W3-02 / W3-04 / W3-05 / W3-07 / W5-01 / W5-02 / W5-03 / W5-04 / W5-05 / W5-06 / W5-07 / W6-01 / W6-02 / W6-03 / W6-05 / W6-06 / W6-07 / W6-08 / W7-01 / W7-02 / W7-03 / W7-04 |
| **合计** | **39** | 四份 handoff 的 27 条 + W6 新开 8 条 + W7 开立 4 条 |

**老化最久的三条**（都必须有归宿，不许无限期挂着）：

- **W3-05**（老化 4 波：W3 / W4 / W5 / W2.6）⇒ 归宿 = 独立 W6.5 波（journal v1 + FreezeClock +
  前台服务 + 恢复卡，一个整体交付）。它是「跑不死」这条线的唯一支柱，被「能跑更多东西」
  这条线挤掉了四波。
- **W3-02**（老化 3 波）⇒ W6 C 流可选档；本波不做则**显式裁决「不做」**，不许继续挂着。
- **W3-07**（老化 3 波）⇒ W6 C 流可选档；同上。

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
