# W6 交接 — 上下文硬预算 + 手动压缩/导入 + 挂账台账（2026-11-01）

> 基线：`90c5f29`（W5 收口）→ `5f95969` + `d460ad1`（本波）
> 相关：`docs/notes/w6-plan.md`（方案）｜`docs/notes/w6-provider-matrix.md`（多模型实测）｜`docs/06-deferred-ledger.md`（挂账唯一台账）

---

## 一、本波结论（先看这段）

| 项 | 结果 |
|---|---|
| 单元测试 | **666 / 0 / 0 / 0**（13 模块；W5 基线 593 → +73）。⚠️ **口径**：其中 **8 条是 `SmokeTest.placeholder`（`assertTrue(true)`）**，真实行为断言 = **658**。本波**新增的 73 条全部是真实断言**，占位一条没增。`EXPECTED_TESTS=666` 是"测试计数"锚，不是"行为断言数"锚 —— 两者都不许漂 |
| 架构守卫 | **26 条判据：OK=25 VACUOUS=1 VIOLATION=0**（新增 G20 挂账不丢失 / G21 空转棘轮） |
| CI | `5f95969` **Build 成功**（含 EXPECTED_TESTS 666 双向对账） |
| 全仓主源集 | 34 983 行 / 42 500（余量 7 517） |
| 真机 | 往返 / 工具循环 / 等待态 / 重试与错误面板 / 状态条分母 / 档位切换 **✅**；**32K 压缩回归未完成（模型侧超时）** |

**一句话**：W5 的 P0（32K 档实发 33 800 token 且零压缩）**根因被订正后修复** ——
不是「判定点在轮头」（`prepare` 每轮都跑），而是「估算器对代码/JSON 低估 ~2×」+
「压缩器在单轮形态下无损放弃」同时成立。修法是**换量纲**（字符硬上限 + 按 provider 真值标定），
不是把判定点搬早。

---

## 二、交付清单

### 2.1 A · 上下文正确性（P0）

| 文件 | 改动 |
|---|---|
| `core-kernel/context/BudgetFormula.kt`（新） | 预算算式拆两个：`hardContextBudgetTokens`（不乘水位线）/ `contextBudgetTokens`（= 硬 × 0.75）。`windowEffective = min(verifiedContextLength ?: contextLength, maxContextTokens)` |
| `core-kernel/context/HardBudgetShrinker.kt`（新） | 原地收缩：只改 `ToolResult.output` 为 `[omitted: hard budget, N chars]`，不删消息、保 `callId`、最旧优先、最后一条完整、不二次包裹 |
| `core-agent/loop/PreStepBudget.kt`（新） | 每步判定：L3 字符（权威）/ L2 token；压不下去 ⇒ `BreakerTripped(context_overflow_prevented)` **不发请求** |
| `core-agent/loop/TurnLoop.kt` | while 体顶部**一行委托** `if (preStep.enforce(ctx)) return Step.Terminated`（覆盖「工具结果提交后」与「首轮超长输入」两个时点） |
| `core-kernel/policy/AgentPolicy.kt` | `hardCharsPerToken = 1.8f`（floor）/ `hardCharsPerTokenCeiling = 6.0f`（ceiling）；按 provider 真值 `max(floor, min(ceiling, measured))` |
| `core-model/RemoteEndpoint.kt` | `verifiedContextLength: Int? = null`（400 学到的真实窗口） |
| `core-engine/provider/OpenAiCompatibleAdapter.kt` | 解析 `maximum context length is (\d+)` → `GenerationEvent.Failed.contextLimitHint` |
| `core-data/AppContainer.kt` | `recordContextLimit` 落盘 + `runners.removeIfIdle`（P2）；`RunnerKey` 增 `compressContext` |

**口径订正（写进 KDoc，防下一波照抄）**：
- 32K 档的**触发**预算是 `(32 000 − 4 096) × 0.75 = 20 928`，不是 19 200；**0.6 是压缩目标，不是触发线**。
- 1 000 000 是**输入+输出共享**上限，`hardContextBudgetTokens` 必须再减 `maxOutputTokens`。

### 2.2 B · 挂账台账与守卫

- `docs/06-deferred-ledger.md`：**唯一台账**，27 条历史 + 4 条本波新开（W6-01…W6-06），
  状态三值 `OPEN`/`CARRIED`/`CLOSED`，`CLOSED` 必须带证据（文件:行 或提交号）。
- **G20 挂账不丢失**：handoff 里的 `W*-NN` 必须在台账内，`CLOSED` 证据非空；selftest 变异自证（删一条 ⇒ 判红）。
- **守卫第四态 `VACUOUS`**：扫描目标集为空时输出 VACUOUS 而不是 OK（首版 1 条 = G19），
  **G21 棘轮**只许减不许增。
- `MAIN_PATHS` 补 `core-kernel` / `feature-tools`（此前 G11/G18 对这两个模块**零覆盖**）。

### 2.3 C · UI

| 项 | 落点 |
|---|---|
| 手动压缩 | `ChatTopBar` 溢出菜单 → 二次确认（**明写「缓存会从头重建、费用更高」**）→ 气泡指针 `compressedUpTo` 切历史 → 分隔卡可撤销 |
| 会话导入（仅 debug） | `SessionImportRow`（SAF）→ `SessionImportParser`（纯函数，JSONL/Markdown）→ 新会话；单条 ≤4 MiB / 总量 ≤8 MiB / 5000 条 |
| 状态条分母 | `contextLimitOf` = **生效硬预算**（修前用 `endpoint.contextLength` ⇒ 1M 端点 + 32K 档位永不报警） |
| 400 写回 | `ContextLimitWriteback`（注入 `record` 便于单测）+ `ChatViewModel` 消费 `AgentEvent.Failed.contextLimitHint` |
| 压缩开关 | 设置页「自动压缩上下文（调试）」→ `SettingsRepository.compressContext` → `RunnerKey` 进键 |

---

## 三、真机验收（三态，OPPO PDRM00 / Android 13）

| # | 判据 | 三态 | 证据 |
|---|---|---|---|
| D1 | 聊天一次往返（OR `dots-3-note-preview:free`） | ✅ | `1.6k in / 50 out (0 cached, 0.0%)`，思考块渲染 |
| D2 | 工具循环（file_list + file_read） | ✅ | 两轮收敛，回复 `DONE`，`1.9k in / 35 out`（工具卡片未逐帧目检，**只证链路**） |
| D3 | 等待态 | ✅ | 「正在等待模型响应… 51s」逐秒上屏 |
| D4 | 重试可见 + 错误面板 | ✅ | 「网络抖动，正在重试…」→「本次请求失败：giving up after 1 retries: SocketTimeoutException」 |
| D5 | 状态条分母 = 硬预算 | ✅ | 32K 档显示 **`上下文 1.9K / 27.9K`**（= 32 000 − 4 096）；修前会显示 512.0K |
| D6 | 窗口档位切换 | ✅ | 1M → 32K，`已选 / 当前 32K` |
| D7 | **32K 档并行读 5 个大文件（W5-01 回归）** | ⛔ **未完成** | 5×16.3 KB 已推入沙箱，请求发出后模型侧 `SocketTimeoutException` 失败（免费模型抖动），**不是代码缺陷，但也未拿到「实发 ≤ 27 904」的真机证据** |
| D8 | 视觉输入 | ⛔ 未走 | 本波时间不足；W5 D7 已 ✅ |
| D9 | 手动压缩 | ⛔ 未走 | 仅单测（`ManualCompressTest`） |
| D10 | 会话导入 + 设备侧长上下文 | ⛔ 未走 | 题面已备（`_w6/import-400k.jsonl` 等），未执行 |
| D11 | MCP 真机联调 | ⛔ 平台阻塞 | `usesCleartextTraffic=false`（G15b 要求）未动 |
| D12 | 音频输出（TTS） | ⛔ 本机不可用 | `pm list packages` 只有 `com.iflytek.speechsuite`，`tts_default_synth` 也指向它 ⇒ W3 的「调 speak() 挂起」不可绕 |

> 🔴 **D7 未完成是本波最大的验收缺口**：W5-01 的修复**只有 JVM 判据（T1–T7）**，
> 真机重放没做成。下一波第一件事应是**换 NVIDIA 端点**重跑这条（OR 免费模型在 5×16 KB 输入下超时）。

---

## 四、多模型矩阵（详见 `docs/notes/w6-provider-matrix.md`）

- **可用 12 个**（NV 9 + OR 2 + 1 仅聊天）；OR 免费池 6 个里 3 个不可用（2 地区限制 + 1 上游 503/429）。
- **nemotron 真实窗口 1M（含输出）复现**：1k→512k 六档取针全中；995k 档 `prompt_tokens=994 620` 但
  `completion=4096` 被 reasoning 吃光、`finish=length` ⇒ **🟡 vacuous**（不是失败，也不是通过）。
- **OR 网关窗口预检 = 字符数 ÷ 4**：dots 声明 512k，2 869 422 字符被算成 721 452 拒掉；
  north 声明 256k，1 672 654 字符被算成 422 260 拒掉 ⇒ **散文在 OR 上只能用到声明窗口的 ~2/3**。
- **字符/token 实测**：散文 5.41–6.53（5 模型 × 多档，误差 <2%）⇒ `TokenEstimator` 的 4.0 对散文保守、
  对代码/JSON 乐观，**偏差是内容相关的**。
- **前缀缓存不可依赖**：`nv-vl` 0→0→4336（写入延迟 ≥2 次）、`dots` 恒 0、`north` 恒 0。
- **「300 s 超时」≠「模型不可用」**：step-5 与 glm-5.3 在 600 s 窗口下都能回（36.9 s / 216.3 s）。

---

## 五、本波四条新铁律

1. 🔴 **多 agent 并行跑 gradle 会产生不可信的红**。本波实测三种假红形态：
   `Daemon compilation failed` / `NoClassDefFoundError` / `Unresolved reference`，
   全部由并发 daemon 与增量缓存损坏引起。**收口必须单人串行**：先确认只有自己的 java 进程 →
   遇 unresolved 先 `-Pkotlin.incremental=false` 复验 → 仍红才清 `build/kotlin` + `build/tmp/kotlin-classes`。
2. 🔴 **判据的量纲要与估算器解耦**。W5 的 33.8K 事故里，用同一个 `TokenEstimator` 写「没超预算」的断言，
   那条断言**永远绿**。字符硬上限 + 按真值标定，是唯一不依赖估算器正确性的判据。
3. 🔴 **「链路都在、只差一跳」是本仓第 4 类空转形态**。本波连清两条：`compressContext` 零写入点、
   `recordContextLimit` 零调用点。判据：**新增字段/方法时必须同时给出它的消费者**。
4. 🔴 **外审的算术也要复核**。本波两份外审各错一处（`28 904` 应为 `27 904`；「模块上限之和 ≤1800」
   不是真约束，真约束是 `TOTAL_LINE_CAP`）。复核方式：**逐文件累加**，不要用 `xargs wc -l`（会分批出多个 total）。
5. 🔴 **行数求和必须两种独立方法对拍**。本波两人各踩一次 `xargs wc -l | tail -1` 的分批截断
   （`16 498 + 18 485 = 34 983`、`17 074 + 17 909 = 34 983`，两批之和恰等于真值 ⇒ 不是漏模块，是只读了末批）。
   **"`grep -c total` 必须 == 1" 在本环境永远为假**（命令行过长 ⇒ `wc` 被迫把 `total` 行也截断，
   出现 `wc: Argument list too long`，任何分段都会出多个 total）⇒ 不能拿它当判据。
   **正确做法**：两种求和方式对拍 + 全仓文件数自证（实测 `209 == 209`）。

---

## 六、挂账

| # | 项 | 备注 |
|---|---|---|
| **W6-01** | **`core-kernel` 1790/1800（余 10 行）拆分** | 本波冻结该模块新增。拆分口径：`contextBudgetTokens`/`hardContextBudgetTokens` 移入 `core-agent`（两者都只被 `core-agent` 消费），目标 core-kernel ≤1200、core-agent ≤5500、两模块上限之和 ≤1900 |
| **W6-02** | `feature-chat` 3459/3500（余 41）/ `feature-settings` 2921/3000（余 79）/ `core-design` 12 439/15 000 | 豁免块，下一波开波前消化 |
| **W6-03** | **`ChatViewModel` 484/500（余 16）冻结** | 任何新增必须先拆出去 |
| W5-01 | 压缩判定点 / 估算器低估 | ✅ **JVM 判据收口（T1–T7）**；⚠️ **真机重放移交 W7-01**（见 D7）⇒ 保留 OPEN，直到真机拿到「实发 ≤ 27 904」 |
| W5-02 | `contextLength` 超报 | ✅ 关闭：`verifiedContextLength` 写回 + 预算扣输出（T4/T6） |
| W5-03 | MCP 真机联调 | ⛔ 仍挂（cleartext 裁决未做） |
| W5-04 | 设备侧 1M | ⛔ 仍挂（导入通道已就绪，题面已备，未执行） |
| W5-05 | `autoApproveDangerous` | ⛔ 推 W7 限期 |
| W5-06 | MCP sampling/roots | ⛔ 推 W7+ |
| W5-07 | 免费池隐私开关 | ⛔ 环境清单 |
| W3-01 | `speak` | ✅ **裁决关闭**：本机仅 iflytek TTS，`speak()` 挂起不可绕；本仓不做云端 TTS |
| W3-05 | journal / FreezeClock | ⛔ 仍挂（本波刻意不开，与 A 流 `RunState` 冲突） |

---

## 七、本波移交 W7（用户裁决：压缩与缓存相关一律下一波）

> 2026-11-02 裁决：**关于上下文压缩与缓存机制的验证，全部排到下一波**。
> 同时裁决：**本机是安全真机，测试密钥可以常驻设备**（下一波不必每次重新录入，端点建一次即可复用）。

| # | 移交内容 | 为什么本波没做完 |
|---|---|---|
| **W7-01** | 上下文压缩真机重放（32K 档 + 并行读 5×16 KB ⇒ 实发 ≤ 27 904） | 请求已发出，**免费模型侧 `SocketTimeoutException`**（`_w6/d12.png`）。夹具已就位：5 个 16.3 KB 文件在设备沙箱 `files/harness/workspace/module_{1..5}.kt`。**下一波必须换 NVIDIA 端点**，OR 免费模型在该输入规模下不可用 |
| **W7-02** | 前缀缓存命中率验证（第 3 次请求 `cached_tokens > 0`） | 本波只做了多模型侧实测（`nv-vl` 0→0→4336、`dots`/`north` 恒 0），**没做本仓前缀稳定性 × 命中率的设备侧对照**。与 W7-01 同批请求即可同时取数 |
| **W7-04** | 删掉 8 条恒绿占位 `SmokeTest.placeholder` | 收口期发现但**刻意不动**（见下）。删完 `EXPECTED_TESTS` 666→658，且要复核"没有哪个模块因此变 `NO-SOURCE`" |
| **W7-05** | `PreStepBudget` 的 sanitize 上界只有论证、没有实测 | W6 裁决「接受 + 写清上界，不改结构」；上界式已写进类头，但无用例验证。与 W7-01 同批真机时顺手取数 |
| **W7-06** | 两条「仅绿」可见性判据 + 正确检查顺序 | ①「release 包看不到导入入口」在本仓**不可执行**（`:app` 只有 debug 变体）；②「关压缩后请求接近窗口」**假绿风险高**（压缩关闭时无任何可观测输出，400 会被读成通过）⇒ 先 grep 出「压缩真的没发生」的独有证据再跑 |
| **W7-03** | 手动压缩 / 会话导入真机验收 | 仅单测（10 + 6 + 19 条）。判据已写进台账，含「`cached_tokens=0` 必须双向断言」与「release 包看不到导入入口」 |

**设备当前状态（下一波可直接接手）**：
- 已安装 W6 包（`com.rickeal.liquidharness.debug`，666 测试同源）。
- 端点：**`NV-VL` 已录入密钥并设为「当前」**（`baseUrl=https://integrate.api.nvidia.com/v1`，
  模型 `nvidia/nemotron-3.5-super-vl-120b-a12b`，声明窗口 1 048 576，连接测试 **671 ms 通过**）；
  `OR-DOTS` 保留（有密钥，免费但慢）。按 2026-11-02 裁决「安全真机，密钥可常驻」，下一波不必重录。
- 窗口档位当前 = **32K**（W7-01 需要它，不必再改）；验证完记得切回 1M。
- 沙箱工作区已有 `module_{1..5}.kt`（5×16.3 KB）、`chunk{1..3}.txt`、`a.txt`/`b.txt`/`notes.txt`/`w5.txt`。
- 下载目录已有导入题面 `import-400k.jsonl`（2.16 MB，≈400k token）与 `import-40turn.jsonl`。
- 驱动脚本：`_w6/ui.py`（dump/tap_text/type_text/shot/logcat），**已封装的坑**：adb 必须 `-P 15037`；`input text` 是追加写；中文无法注入；发送键位置随键盘高度变化（键盘弹起时约 `(942, 1215)`，收起时约 `(930, 1969)`）。

---

## 八、关于那 8 条恒绿占位（如实登记，本波不修）

`grep -rn "assertTrue(true)" --include=SmokeTest.kt` = **8 条**，分布在
`app` / `core-agent` / `core-data` / `core-design` / `core-mcp` / `core-skills` /
`feature-chat` / `feature-settings`，文件是各模块的 `SmokeTest.kt`。

**它们不影响本波任何结论**：本波新增的 73 条全是真实断言，占位一条没增；
`EXPECTED_TESTS=666` 是"测试计数"锚（防"声称跑了 N 条其实没跑"），不是"行为断言数"锚。

**为什么不本波删**：删完要把 `EXPECTED_TESTS` 改 666→658 并重新全量跑 + 重推 + 等 CI，
而收益只是让一个数字更诚实（数字本身已经如实登记）。**风险不对称**：万某模块因此变
`NO-SOURCE`，收口期要重跑一轮完整验证。⇒ 登记为 **W7-04**，与 W7-01 同波做。
**在 W7-04 关闭前，任何地方引用 666 时必须同时写"其中 8 条为占位，真实断言 658"。**

---

## 九、本波方法论净产出：「四次读错对象」

所有「数字/状态对不上」的事故追下去都不是算错，而是**判据读的东西不是你以为的那个东西**：

| 实例 | 你以为判据读的 | 它实际读的 |
|---|---|---|
| `git status` 干净 | 树里没改动 | 只反映**工作树 vs HEAD**；`build/` 产物与未跟踪文件都不算 |
| 测试产物 XML | 当前源码跑过 | 那个**时刻**跑过 ⇒ 必须加「源 mtime < 产物 mtime」，且**被测主体**也要早于产物 |
| `xargs wc -l \| tail -1` | 全仓总行数 | **末批**总行数（本仓 209 文件必切两批，差 16 498 行且不报错） |
| `grep -c total == 1` | 判据的绿态 | 本环境**永不存在**的绿态 ⇒ 恒红，是噪音不是判据 |
| **`tests=666` 全绿** | 666 条**行为判据**通过 | **658 条行为判据 + 8 条 `assertTrue(true)` 常量** |

**前四类与第五类不是同一种病，总结分两档**：

> - **读错对象**（前四类）：**值会露馅** —— `18 485 ≠ 34 983`、XML 比源旧、`total` 出现两次。
>   只要换一个口径就能发现。
> - **读错含义**（第五类）：**值完全正确** —— `EXPECTED_TESTS=666`、实跑 666、双向对账通过、闸门全绿，
>   失真只在「这个数从哪来」。**更危险，因为任何只看值的检查都放行它。**

**直接推论（对 `EXPECTED_TESTS` 闸门）**：它的职责是「`tests=0` 判红 + 新增测试要登记」，
**天然无法**判断「登记的这条是不是判据」⇒ 8 条占位可以永久存在而闸门始终绿。
**想让它有判别力，唯一办法是在源头不给恒绿断言（删占位），加计数判据无效。**

配套两条：
1. **判据必须能红且能绿**。恒红是噪音（教人忽略红），恒绿是注释。
2. **反例锚只给行为判据，不给逐值断言**。行为判据值得写「什么改动会让它红」（T7 的「换回固定 1.8 必红」）；
   而 `assertEquals(27_904, hard)` 的红态**就是值不等**，再写一句「若算式错则红」只是把断言翻成中文。
