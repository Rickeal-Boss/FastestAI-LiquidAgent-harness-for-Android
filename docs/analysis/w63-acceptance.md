# W63 验收物 — 压缩触发线按档位 / MCP 内网透传 / journal 半写行 / AgentRunner 收尾序

> 波号 W63（2026-11-28）｜基线 `6873fc8`｜判定四态：✅ / ⚠️ / ⛔ / ⛔vacuous
> 纪律：先判据后动手；「未触发」≠ 通过；真机结论只来自设备侧落盘与 UI 语义树，不来自截图 OCR 猜测。
> 角色分记：代码实现 = 开发席（柯码成）；闸门原始输出 = CI 席（崔续程）；增量 diff 审查 = 审查席（严质衡）；
> 真机行使与文档收口 = 主理人（祁研深）。

## 一、本波改了什么（逐项）

| # | 项 | 文件 | 行为变化 |
| --- | --- | --- | --- |
| 1 | 压缩触发线按档位 | `core-context/.../BudgetFormula.kt`（新增 `compactTriggerChars`）、`core-agent/.../context/SettledCompressPolicy.kt`（`shouldApply` 必填 `triggerChars`）、`feature-chat/.../ConversationOps.kt` | 32K 档触发线 50 227 → 37 670（严格低于 L3 硬 cap）；128K 档 → 171 417（不再过早压） |
| 2 | MCP 内网开关透传 | `core-mcp/McpTransport.kt`、`McpClient.kt`、`McpModels.kt`、`core-data/NetMcpTransport.kt` | 开关为真时穿过 `UrlGuard` 首闸；`blocked_unsafe_url:<REASON>` 不再被折成 `transport_error` |
| 3 | journal 半写行容错 | `core-agent/.../journal/AgentRunJournal.kt`（`wellFormed`）、`core-data/RunRecoveryStore.kt` | 落盘配对只采信「kind 前缀 + 结尾 `at=<纯数字>`」，两侧同源 |
| 4 | `AgentRunner` 收尾序 | `core-agent/.../AgentRunner.kt` | `runSettled` 抛 CE 时清理与 unlock 必达（清理先、unlock 末，同处嵌套 finally） |
| 5 | **MCP 开关回显**（审查席 P2-1，本波新增） | `feature-settings/.../McpServerListScreen.kt`、`ExtrasViewModel.kt`、`SettingsRoute.kt` | 编辑已有 server 时开关不再被硬写回 `false`（原链路 `McpRow` 无该字段 ⇒ 保存即抹掉持久化的 `true`） |
| 6 | journal runId 形态校验（审查席 P2-2） | `core-agent/.../journal/AgentRunJournal.kt` | `wellFormed` 增加「runId = 32 位小写 hex」；堵住「截断行与下一行合流、尾部 `at=` 来自后一条记录」的假合格 |
| 附 | W51 证据行尾存量 | `evidence/raw/w51/g3_selftest.txt` | 入仓 blob 2 846 B（CRLF）→ 2 806 B（纯 LF）；**提交时** `i/lf w/lf`（提交前工作树为 `i/crlf w/lf`，索引随本次 add 更新）；该文件不在任何 MANIFEST 内 |

## 二、判据与四态

| 格 | 判据 | 实测 | 四态 |
| --- | --- | --- | --- |
| G0 | 修复前基线（32K + 47 145 字符）必为 `below_trigger` | `auto_compact=noop reason=below_trigger` | ✅ 基线成立 |
| G1 | 修复后同内容同档位 ⇒ `auto_compact` **非** `below_trigger` | `auto_compact dropped=26 kept=47 cache_invalidated=false` | ✅ |
| G2 | 128K 档同内容 ⇒ `below_trigger`（触发线随档位，不是整体下调） | `auto_compact=noop reason=below_trigger` | ✅ |
| G3 | JVM 双档位对照（32K 37 670 / 128K 171 417）+ 两处必红变异 | 见 §三 | ✅ |
| G4 | MCP 开关**关** ⇒ `blocked_unsafe_url:PRIVATE_NETWORK` | 设置页结果行逐字一致 | ✅ |
| G5 | MCP 开关**开** ⇒ 非 blocked（过首闸） | `error: transport_error`（无服务监听） | ✅ |
| G5b | MCP 开关**开** ⇒ 成功拉到 `tools/list` | 无私网 https MCP server | ⚠️ 未行使 |
| G6 | 半写行（缺 ` at=`）⇒ 冷启动不弹卡 | 语义树无「继续 / 丢弃 / 未正常结束」 | ✅ |
| G7 | 完整行 ⇒ 冷启动弹卡；「继续」补 settled | 弹卡 + settled 行落盘 | ✅ 正控成立 |
| G8 | 取消 ⇒ `cancelled=true` settled；紧随新 run 被接受且正常结算 | `activeMs=13246` / 下一 run `activeMs=28007` | ✅ |
| G9 | 长回答渲染回归（本波未触碰渲染层，复核无黑块） | 正文可读、无黑块 | ✅ 复核通过 |
| G10 | sink 抛 CE 时 unlock 必达 | 生产 sink 为同步阻塞 IO，不会抛 CE | ⛔vacuous |
| G11 | MCP 开关「保存 → 重开表单 → 再保存」后仍为开（P2-1 回归） | 重开表单开关保持开；两次保存后 `mcp-servers.json` 仍 `true` | 见 §三之二 |
| G12 | 半写行 + **非 hex runId** 与 **32 位 hex runId** 两形态均不弹卡（P2-2 回归） | 见 `e4_journal_half_written.txt` | 见 §三之二 |

## 三、JVM 双档位对照与必红变异（开发席原样输出，主理人复核）

- 正常态：`SettledCompressPolicyTest` 12 tests 全绿。
- 断言锚点：32K（`contextLength=32 000` / `maxOutputTokens=4 096` / floor 1.8）⇒ `compactTriggerChars = 37 670`；
  32K 档 `37 670` ⇒ `below_trigger`，`37 671` ⇒ `applied`；同一 `37 671` 在 128K 档 ⇒ `below_trigger`。
- 变异 A（函数体换回 `50_227`）⇒ `belowTriggerDoesNotApply FAILED`（`AssertionError`），`12 tests completed, 1 failed`。
- 变异 B（`compressThreshold → 1.0f`）⇒ 同一用例 FAILED。
- 两次变异后 `diff -q` 与 pristine 一致（`RESTORED_IDENTICAL`）。
- 原始文本：`evidence/raw/w63/e6_mutation_trigger.txt`。

## 四、账实与守恒

- 改动面：12 个 `.kt`（9 主 + 3 测试）+ 1 个证据行尾修正；零 docs 改动在本提交（文档另提交）。
- `@Test` 增量 = 0（`git diff | grep -c '^+.*@Test'` = 0）；`EXPECTED_TESTS` 维持 904；`EXPECTED_CLAIMS` 15 未变。
- 三顶格对象零触碰：`core-kernel 449/450`、`GenerationRound 400/400`、`feature-chat 3999/4000`；`LINE_CAPS` 未调。
- 台账状态列：**216 行 / 39 CLOSED / 166 OPEN / 11 CARRIED**，段数 37 → 38；本波销账 0 行、新开 0 行。

## 五、明确未做（不写成已满足）

1. **外审 6 行（W34-01/02/03、W35-01/02/03）未销账**：`k6`/`k7` 正常态实跑已复现（见 `g6_ledger_w63.txt` 与交接 §八），
   但缺「五类必红逐类本波复跑入仓」与「`§2.5a` 元引用是否计入 0 命中」的口径裁定 ⇒ 不写已满足。
2. `GlassLayerBudget` 4096 常数改设备能力探针：`core-design` 12565/12600（余 35），本波不动，登记为增强项。
3. `activeRun` 改 `compareAndSet`：观察位（本波清了「新 run 被旧 run 清尾」窗口，未做无锁化）。
4. 前台服务（R5 范围仍 3/4）：未做，R5 不因本波变化。
5. 私网 MCP 端到端连通（`GuardedDns` 第二道闸）：⚠️ 未行使。
6. FreezeClock 冻结扣时：本波零改动、零结论。
