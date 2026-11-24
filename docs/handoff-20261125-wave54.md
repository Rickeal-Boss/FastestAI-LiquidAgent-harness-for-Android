# W54 交接 — 首个真代码波：P0 双修 / P1 安全四件 / N-1 定谳 / 真机多模型与压缩对照

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `033723607c5138be123ff5ac4e465a0449935772`（W53 回填提交）；台账权威版 = `docs/06-deferred-ledger.md`；
> 本波验收物 = `docs/analysis/w54-acceptance.md`；派单卡 = `docs/w54-dispatch.md`；判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；
> 「未触发」≠「已验证通过」；推断带 caveat、不写成事实。本交接不写复合挂账号，段位用中文描述。

---

## 一、本波做了什么（一段话）

W45 以来连续九波零代码、零销账之后的第一波真代码：先对四份外审逐条回源裁决（采信 8 项、不采 3 项、1 项定谳），再把两条会造成**用户配置静默覆盖**与**压缩后仍发不出请求**的 P0 修掉 —— 端点清单「解析失败」不再被当成首启（原文件一字节不动 + 另存备份），写盘失败不再被吞（`SAVE_FAILED` 上屏）；压缩器新增字符维目标、与 `PreStepBudget` 共用同一 cap。同车补 P1 安全四件（`UrlGuard` 下沉 `HttpTransport.post()` 兜底、沙箱 canonical、9 处工具取消重抛、`McpClient` 原子 id 与握手互斥）与 N-1 定谳用例。承接 W53 外审七条口径订正。四闸门全绿（`tests=900`），真机做多模型 / 多档位 / 手动与自动压缩对照，最后推 CI。

## 二、裁决

### 2.1 采信并执行

P0-1（端点清单读写失败链）、P0-2（压缩字符量纲同尺）、P1-1（MCP 路径 URL 闸）、P1-2（沙箱越界）、P1-3（取消重抛）、P1-4（MCP 并发状态）、N-1 定谳、W53 七条口径订正。

### 2.2 不据此改仓（登记到老号行内）

G19 扫描面扩容（扩面即判红 12 处日志拼接，属守卫重设计）、PNG 二进制自动脱敏门禁（需接既有 step）、P2 十条（与本波 P0 无依赖，避免摊薄）。三项全部挂到安全收口 / 脱敏门禁 / 压缩量纲老号行内，**零新开编号**。

### 2.3 方向裁决（回答外审的「总闸」）

外审判断「不裁决行数就不能开代码波」。本波证明还有第三条路：**把修复放进有余量的模块** —— `core-kernel` 零改动、`GenerationRound.kt` 400/400 原样、`LINE_CAPS` 未调；`feature-chat` 净增 14 行（3999/4000）、`ContextBudget` 一度 404 后靠抽 `promptCharCap` 收敛到 398。等裁决的是「内核再分层」，不是「所有代码」。

**调度事实**：主理人串行落代码 / 文档 / 闸门 / 真机 / 提交；真机为单设备独占，未并行派单；工作树动手前干净；未用 stash / reset / clean 还原；台账单人落笔。

## 三、账本与守恒

- 同一段内落 `### W54 段`（内容指路 + 计数指路），不补上一波。
- 两条老号行内注记（压缩量纲 / 安全收口），状态原样、pipe=7、零回开。
- 挂账行 216 / 已关 39 / 开放 166 / 结转 11，与历波逐值相等；零本波新挂账行；G20 141 ID 不变。
- 本波销账集合 = 0 行。

## 四、第 1 车（代码面）

| 改动 | 文件 | 判据 | 四态 |
|---|---|---|---|
| P0-1 读失败不补种 + 写失败上抛 | `EndpointStore` / `JsonFileStore`（新增 `backup`）/ `McpServerStore` / `FileMemoryStore` | 两条新用例（坏 JSON 不重种、写失败抛 `SAVE_FAILED`） | ✅ |
| P0-2 压缩字符维 | `ContextCompressor` / `LayeredContextCompressor` / `BudgetFormula` / `ContextBudget` / `ManualCompress` / `ConversationOps` | 两条新用例 + 真机同会话对照 | ✅ |
| P1-1 MCP URL 闸 | `HttpTransport`（兜底 `UrlGuard`）/ `NetMcpTransport`（区分拒绝） | 编译 + LLM 链路真机不退化；MCP 0 服务器 ⇒ vacuous | ✅ / ⛔vacuous |
| P1-2 沙箱 canonical | `SandboxFiles` | 与工具侧同型 | ✅ |
| P1-3 取消重抛 | 7 个工具文件 9 处 | 编译 + 全量单测 | ✅ |
| P1-4 MCP 并发 | `McpClient` | 编译 + 既有 MCP 用例 | ✅ |
| N-1 定谳 | `EndpointCapabilitiesTest` | 新用例通过 ⇒「保存误拒」与现役代码不符 | ✅ |

`EXPECTED_TESTS` 895 → **900**（`build.yml` 常量 + 变更史注释同提交，严格行首口径实测 900）。

## 五、四闸门 + 真机

| 闸 | 结果 | 四态 |
|---|---|---|
| G1 编译 | rc=0；`BUILD SUCCESSFUL`（`:app:assembleDebug`） | ✅ |
| G2 架构守卫 | `PASS ok=27 vacuous=1 violation=0 fault=0`；G19 仍 VACUOUS；G20 141 ID | ✅ |
| G3 守卫自测 | `30/30 PASS（307s）` | ✅ |
| G4 全量单测 | `tests=900 failures=0 errors=0 skipped=0`（14 模块） | ✅ |

真机（设备先唤醒，密钥留设备未回灌，逐格见验收物步 4）：

- 回声 Lightning 30B `echo-W54ECHO` 逐字 ✅；多模态 Bolt E4B 红方块 + 蓝圆 ✅；文件读写审批 `W54FILEOK`（设备侧 cat 一致）✅；skill 两个内置 ✅；MCP 0 服务器 ⛔vacuous；
- **手动压缩判据级**：264K 字符历史 → `manual_compress dropped=70 kept=77` → 下一请求 `prompt=35338` 正常发出（W53 同型场景是 `chars=226442 cap=223027` 熔断）✅；
- **未压缩对照**：同一历史直接发送 ⇒ `context_overflow_prevented: chars=262626 cap=228556`（判据会响）✅；
- **自动压缩首次 applied**：192K 字符历史 → `auto_compact dropped=30 kept=83` ✅；
- 1M 档 SuperVL `1.9K / 995.9K`（常规读数，非满窗真压）✅；Nano Omni 文本轮 ✅；
- North 与 Dots3 均 `request rejected (HTTP 404)` ⛔（外部供给，W53 时 Dots3 可用）；
- 设备 Wi-Fi 掉线一次（`Active default network: none`），应用给出 `giving up after 1 retries` 失败卡，重连后重跑通过 ⚠️（环境事件）。

**回灌级别**：只写脱敏判据结论、`deviceLogTail` 30 行固定形态探针（形态外 0）与 14 张截图；不回灌整段 logcat / 凭据 / 端点完整标识与密钥 / 账号字段 / 序列号 / SSID / 本机路径 / 用户私人图片。

## 六、CI（公开 API 四字段）

- 提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；PAT 仅 URL inline 一次性使用，remote URL 保持干净、无凭据落盘。
- **实际结构 4 提交**（原计划 2 个）：内容 `bba8dcf` → 误报修复 `89ac235` → CI 四字段回填 `a1b111e` → 文档字面量修正（回填提交的 Secret scan 被同一误报字面量二次触发：根因说明里把该字面量原样抄进了 Markdown；已改为不含该形态的描述）。
- **首个提交 `bba8dcf` 判红并已定位**：Build `45449971703` / Secret scan `45449971272` 均 failure，逐 job 实取后确认 Assemble / Unit tests / Lint / Arch guard 全 success，唯一失败是 **gitleaks** 命中 `ContextCompressor.kt:136` 的日志拼接（`tokens` 字面量后紧跟字符预算字段赋值，`generic-api-key` 误报）。
- **修法**：改日志形状（字符维字段前置、token 计数移到句尾且不带等号），**不加 allowlist**；修复提交后本地四闸门 + 脱敏门禁重跑通过。
- **交付判据 = 修复提交 `89ac235` 双 run 全绿**：Build `45451754250`（`head_sha=89ac2353bb4e631d7c27c5a56bfa2689015b0d2b` / `completed` / `success` / `2026-11-24T17:46:16Z`，`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45451754250`）；Secret scan `45451754200`（同 sha / `completed` / `success` / `2026-11-24T17:44:02Z`，`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45451754200`）。
- 回填提交为纯文档，其 run 按既有降级口径只声明存在、不逐条列号。本地四闸门不代替 CI；CI 绿以公开 API 四字段为准。

本地四闸门不代替 CI；CI 绿以公开 API 四字段为准。

## 七、明确未做（逐条带中文段位）

G19 扫描面扩容（安全收口老号）｜PNG 二进制门禁（脱敏门禁老号）｜MCP token Keystore 信封（安全收口老号 / W7 待办）｜自动压缩触发线按档位化（压缩量纲老号）｜`errorMessage` 口径与切换守卫两把尺（压缩量纲老号余项）｜192K 与 256K 档读数｜1M 满窗真压双硬限（真压两条老号）｜MCP 外部 https（明文三条老号，0 服务器 vacuous）｜journal 二选一（journal 两条老号）｜内核再分层与 449/450 上限处理（拆分三方老号）｜North 与 Dots3 的 404 供给方核查（外部依赖）｜凭据轮换（用户侧）｜历史缺口不补造。

## 八、OPEN 挂账

逐条（触发信号 / 缓解动作 / 中文段位）见验收物 §「OPEN 挂账」与台账权威版。本波零新开零销账；状态列零变化。

## 九、交给外部审查团队的制品 + 边界声明

**制品**：本交接、验收物、派单卡、台账（`### W54 段` + 两条老号行内注记）、`evidence/raw/w54/`（g1–g4、`deviceLogTail.txt`、14 张脱敏截图）、W53 七条口径订正、代码 diff（31 文件 / +406 −40）。

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / 整段 logcat /「建议下一链做什么」。

**边界**：① 13 个 `.kt` 生产文件有 diff，`core-kernel` 与 `GenerationRound.kt` 零改动、`LINE_CAPS` 未调；② P0-2 的真机证明是**同会话对照**（压缩后通过 / 不压缩熔断），不是满窗真压；③ MCP 与 1M 满窗仍 vacuous / 常规读数；④ North、Dots3 的 404 是设备真相，不写成已修复；⑤ 设备 Wi-Fi 掉线一次已如实记录并排除在应用缺陷之外；⑥ `G19` 仍 `VACUOUS`，本波不靠它证明「日志不拼凭据」。

> 本波止于交接，交由外部团队审查。
