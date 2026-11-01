# W5 交接 — 真机解锁 + 观测/护栏收口 + 供应商矩阵实测（2026-11-01）

> 仓库：`FastestAI-LiquidAgent-harness-for-Android`（本地 `harness-v2/`）
> 基线：`13a28ce`（W4 收口）→ 本波
> 相关：`docs/notes/w5-network-triage.md`（网络排查）｜`docs/notes/w5-provider-matrix.md`（供应商实测）

---

## 一、本波结论（先看这段）

| 项 | 结果 |
|---|---|
| 单元测试 | **593 / 0 / 0 / 0**（13 模块；W4 基线 544 → +49） |
| 架构守卫 | **24 / 0 / 0**（23 + G19 日志不拼凭据） |
| 模块数 | 12 → **13**（新增 `:core-kernel`） |
| **W4-01（设备→OpenRouter 不收敛）** | ✅ **关闭 —— 不可复现**（详见 §三） |
| 真机 | 往返 / 工具循环 / 审批 / 视觉 / 记忆 / 缓存命中 / 窗口档位 **全部 ✅** |
| 长上下文 | PC 侧 **994,998 token 取针命中**；设备侧 1M **未行使**（无批量题面通道） |

**一句话**：W4 留下的三条 ⛔ 里，最卡脖子的那条（设备请求不收敛）**被证伪并关闭**——
不是修好了，是**当初的归因就不对**；换一个端点立刻全通。剩下的能力面（观测、护栏、
限长、runner 失效）按计划收口，真机逐条验过。

---

## 二、交付清单

### 2.1 A 组 — 观测与等待态

| 文件 | 改动 |
|---|---|
| `core-agent/loop/GenerationRound.kt` | 首字节看门狗：`generationTimeoutMillis` 从「只有声明」变为**有消费点**；首事件前超时 ⇒ 取消 collect ⇒ `Attempt.Fatal` + `EngineErrorKind.TIMEOUT` |
| `core-agent/policy/AgentPolicy.kt` | KDoc 补「已接线，消费点 GenerationRound」，并写明看门狗与 FreezeClock 的分工（前者防远端不响应，后者防设备冻结误判） |
| `feature-chat/ChatUiState.kt` | `ChatStatus` 新增 `WAITING_FIRST_BYTE` |
| `feature-chat/EventReducer.kt` + `ChatScreen.kt` | run 启动 ⇒ 等待态；首个 `TextDelta`/`ReasoningDelta`/`ToolCallDelta` ⇒ 离开等待态；状态行渲染「正在等待模型响应… Ns」 |
| `core-model/Generation.kt` | `TokenUsage.cachedPromptTokens: Int` → **`Int?`**（null = 供应商未上报） |
| `core-engine/provider/OpenAiCompatibleAdapter.kt` | 去掉 `?: 0`，原样透传 null |
| `feature-chat` 用量行 | 三态渲染：null ⇒「缓存未知」／0 ⇒「未命中」／>0 ⇒ 命中 |

**为什么 `Int?` 而不是加一个 `cachedReported: Boolean`**：两个事实源必然漂移。
`null` 本身就是「没上报」，不需要第二个字段来表达同一件事。

### 2.2 B 组 — 工具结果限长 + 聚合上限 + runner 失效

| 文件 | 改动 |
|---|---|
| `core-agent/tool/ToolResultLimiter.kt`（新） | 出口统一 clamp；**默认 16 000**（见下方口径订正）；工具自身已标 `truncated` 的不覆盖 |
| `core-agent/loop/ToolResultCommit.kt` | 聚合上限 `maxAggregatedToolMessageChars = 64_000`：超限**从最旧**逐条替换为 `[omitted: aggregated limit, N chars]`，**保 callId 配对**，至少保留最后一条完整 |
| `core-data/AppContainer.kt` | runner 缓存加版本号：端点/窗口档位变更 ⇒ 下次 `agentRunnerFor` 重建（在途 run 不受影响） |

> 🔴 **口径订正（写进这里，防止下一波又照抄 5 000）**：roadmap 原建议「单条上限默认 5 000」，
> 实施时发现它比现役 per-tool 上限**更狠**：`file_read` 原本 16 000（尾部带「怎么读下一段」的
> 续读指引，截断后模型拿不到指引），`skill_load` 正文 14 000 会被拦腰砍断。
> 而 W4 交接里「一条满额读类工具结果 ≈4 000 token」对应的正是 **16 000 字符** ⇒ 16 000 才是设计值。
> **改的是规格，不是实现。**

### 2.3 C 组 — 守卫与测试补课

| 项 | 落点 | 判据 |
|---|---|---|
| 审批咽喉点审计 | `core-agent/src/test/.../ApprovalChokepointAuditTest.kt`（新） | 穷举表 10 行；每行断言「卡发了 / handler 被问了一次 / 工具没执行」三件事 |
| `feature-settings` 测试补课（W4-06） | `feature-settings/src/test/`（新 2 个类） | **1 → 20 条**；含窗口档位边界钳制（7999→8000、1 000 001→1 000 000） |
| G19 日志不拼凭据 | `scripts/arch-guard.sh` + selftest | 变异自证：注入 `log("key=$secret")` 判红 |

> ⚠️ **G19 当前是「装在没有火的房间里的烟感」**：`core-engine` 目前**零日志调用点**
> （T5 的 `AttemptObserver` 取证缝还没做），所以它对现役代码判不出任何东西。
> 它靠 selftest 的变异注入证明「真出现拼接时会红」。**不要**把它读成「凭据已经安全了」——
> 它现在守的是一个空集。等 W6 给 adapter 加日志时它才真正开始工作。

**两条如实登记（不许写成「已防护」）**：
- `AgentPolicy.autoApproveDangerous` 全仓 **0 个消费点**。bypass-immune 三条用例当前的语义是
  「**一旦有人把开关接进闸门就立刻红**」的警报，不是「防护已生效」。
- MCP `sampling` / `roots` 在本仓**尚不存在**，穷举表缺这两行；已登记「落地时必须补进表」，
  **没有**为了凑行数造假用例。

### 2.4 D 组 — 供应商矩阵实测（`docs/notes/w5-provider-matrix.md`）

- **可用模型 5 / 6**：OpenRouter 两个 `:free`（dots-3-note / north-mini-code）+ NVIDIA 三个
  （nemotron-3.5-super-vl / mistral-large-4-1t / step-5-preview）。`deepseek-v4.1-flash` 420 s 超时 ⛔。
- **nemotron 可用窗口上限 = 1 000 000 token（含输出）**：994 998 命中；1 000 083 → HTTP 400（9 次复现）。
  ⇒ 端点里写 `contextLength = 1048576` 是**超报**，预算算式应取实际可用值。
- **缓存命中可控部分 = 前缀逐字节稳定**：NV 23.7k 前缀第 2 次 **91.3%**；改首字节后归零。
  设备侧同口径实测 **14 336 cached / 91.5%**。
- **dots-3-note 256k 取针命中** ⇒ 「视觉 + 工具 + 长上下文」三合一成立（同一脚本同一判据）。

### 2.5 模块拆分（G10 触顶的处置）

`core-agent` 实测 **6495 / 6500**，再加一行就判红 ⇒ 按既定纪律**拆分，不调大上限**：

- 新增 `:core-kernel`（1557 行），承载 `policy/` + `log/` + `context/` 三层；
  **包名原样保留**（`core.agent.policy|log|context`）⇒ 20 余处 import 零改动。
- `core-agent` 降到 **4938**，恢复健康余量。
- **第一版只搬 `context/` 时编译直接红**：`ContextCompressor` 反向依赖 `AgentPolicy` 与
  `AgentLogStore`。⇒ 要么成环，要么把这两个也一起下沉。选后者。

---

## 三、W4-01 关闭：归因被证伪（本波最重要的结论）

W4 的结论是「设备→OpenRouter 请求不收敛，**应用侧**连接/流式路径有问题」。
本波用四个对照实验推翻了这个归因：

| # | 实验 | 结果 |
|---|---|---|
| E1 | `ping` 两个域名 | 都通（NVIDIA 110–143 ms；OpenRouter 284 ms） |
| E2 | `toybox nc <ip> 443` | **两边都 `TCP_OK`** |
| E3 | 应用打 NVIDIA + **故意留旧 OpenRouter 密钥** | **HTTP 401** ⇒ 连接/TLS/请求/响应四步全通 |
| E4 | 应用打 OpenRouter + 真实密钥 | **连接正常 1850 ms**；免费模型真实往返成功 |

**E3 是 W4 缺失的那一步**：用「错密钥 + 对域名」把「网络不通」与「鉴权失败」干净分开。
成本一次点击，却直接决定后面要不要动传输层代码。

结论：**同样的应用、同样的域名，现在能通**。W4 的记录不假，但归因降级为
「当时那一个付费模型的上游不可用/排队超时」——按纪律记「无法证伪」，不写成「已证伪」。

---

## 四、真机验收（逐条三态）

设备 OPPO `PDRM00` / Android 13 / 1080×2400；包名 `com.rickeal.liquidharness.debug`。

| # | 判据 | 三态 | 证据 |
|---|---|---|---|
| D1 | 端点连接测试（NVIDIA） | ✅ | 截图：`连接正常（2022 ms）` |
| D2 | 端点连接测试（OpenRouter） | ✅ | 截图：`连接正常（1850 ms）` |
| D3 | 聊天一次往返（NVIDIA） | ✅ | `1.9k in / 29 out`，思考块渲染，`上下文 1.9K / 1048.6K` |
| D4 | 聊天一次往返（OpenRouter 免费模型） | ✅ | `1.5k in / 37 out` |
| D5 | 工具循环（只读多工具） | ✅ | 两轮收敛：列出 3 个文件 + 读到 `hello harness` |
| D6 | 写类工具审批卡 | ✅ | `工具「file_write」请求授权` + 三按钮；批准后文件内容逐字 `hello-w5`（8 字节） |
| D7 | 视觉输入 | ✅ | 2 形状 / 蓝方 / 红圆 / 底部文字 `W5 VISION TEST 7391` 全对 |
| D8 | 长期记忆写入 + 新对话注入 | ✅ | `memory.json` 有记录；新对话直接答 `teal` |
| D9 | 缓存命中上屏 | ✅ | `14 336 cached / 91.5%`（设备侧首次实测） |
| D10 | 窗口档位切换并持久 | ✅ | 32K 档「已选 / 当前 32K」，重启后仍生效 |
| D11 | 压缩水位线触发 | ⛔ **未触发** | 见 §五 第 1 条（**这是本波发现的真缺口**） |
| D12 | 1M 窗口设备侧行使 | ⛔ vacuous | 无批量题面注入通道；PC 侧已行使（994 998 命中） |
| D13 | MCP 真机联调 | ⛔ **平台阻塞** | `usesCleartextTraffic=false`（G15b 要求）未改；本波未触及 |

---

## 五、本波发现的两个真缺口（不是「已解决」，是「已定位」）

### 1. 🔴 压缩只在**轮头**判定 ⇒ 单轮内工具结果暴增可以整体越线

真机实测：窗口设 32K（预算 32 000，水位线 0.6 ⇒ 19 200），但实际发出了 **33 800 token** 的请求，
且**没有触发任何压缩**。原因不是水位线算错，而是：

- 压缩判定发生在**每轮开始前**；
- 一次并行读 5 个大文件，**在同一轮内**追加约 20 000 token 的工具结果；
- 越过水位线之后，那一轮就是**最后一轮**（模型直接给了答案），于是下一次轮头判定永远不会到来。

后果：在一个真正 32K 的模型上，这次请求会直接 400。本波用 1M 模型跑，所以「看起来没事」。

**这不是参数问题，是判定点位置问题。** 下一波（上下文三期）应把判定点下沉到
「工具结果提交之后」或按 dsh 的 pre-step 单点检查重排；并且**必须配一条判据**：
`单轮追加 N 条大工具结果后，下一次请求的 prompt_tokens ≤ 预算`。

### 2. ⚠️ `contextLength` 超报：端点写 1 048 576，模型实际可用 1 000 000（含输出）

预算算式 `min(contextLength, maxContextTokens)` 里，前一项现在是**乐观值**。
在 1M 档位附近会算出一个发不出去的请求。建议：端点探测把实测上限写回 `contextLength`，
或预算算式对「含输出的上限」扣掉 `maxOutputTokens`。

---

## 六、本波四条新铁律

1. 🔴 **「应用连不上」先做「错密钥 + 对域名」这一手**。401 与超时是两个世界：
   前者证明连接/TLS/请求/响应全通，后者才需要往传输层查。W4 花了三轮没定位的问题，
   这一手一次点击就分开了。
2. 🔴 **「看起来独立的包」≠「真的独立」**。只搬 `context/` 时编译立刻红——
   它反向依赖 `policy` 与 `log`。判据只有**一次真实编译**，不是读 import 列表。
3. 🔴 **拆分不调上限，且拆分不该让全仓变胖**。`:core-kernel` 落地时**没有**调高
   `TOTAL_LINE_CAP`：那 1557 行是搬过来的，不是新增的。给新模块加预算同时抬高总量，
   等于把「拆分」当「扩容」用。
4. 🔴 **规格里写的数字要先跟现役常量对一遍再落地**。roadmap 写「单条工具结果默认 5 000」，
   而现役 `file_read` 是 16 000、`skill_load` 是 14 000 ⇒ 照抄会砍掉两个已工作的协议。
   **改实现之前先 grep 现役值**，冲突时以「已工作的协议」为准并订正规格。

---

## 七、挂账

| # | 项 | 备注 |
|---|---|---|
| W5-01 | **压缩判定点下沉**（轮头 → 工具结果提交后 / pre-step 单点） | 见 §五.1，有真机证据（33.8K vs 32K 预算） |
| W5-02 | `contextLength` 超报（1 048 576 vs 实测 1 000 000 含输出） | 见 §五.2；端点探测或预算扣减二选一 |
| W5-03 | MCP 真机联调（承接 W4-02） | 明文 http 仍被 `usesCleartextTraffic=false` 拒；需 https 夹具或 loopback 例外裁决 |
| W5-04 | 设备侧 1M 行使 | 无批量题面注入通道；要做得先有「大 payload 注入」路径 |
| W5-05 | `autoApproveDangerous` 无消费点 | 三条 bypass-immune 用例是警报，不是防护 |
| W5-06 | MCP `sampling` / `roots` 未实现 | 穷举表缺两行，落地时必须补 |
| W5-07 | 免费池 12/14 不可用（承接 W4-04） | 需账号持有人开隐私开关；本波用 NVIDIA 绕开 |
