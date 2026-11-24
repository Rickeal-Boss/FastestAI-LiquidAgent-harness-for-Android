# W54 派单卡 — 首个真代码波（P0 双修 + P1 安全四件 + N-1 定谳）

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 = W53 tip `033723607c5138be123ff5ac4e465a0449935772`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat。
> **新开挂账号 = 0 / 销账 = 0**（两数只在本抬头一份）。

## 一、为什么本波开代码

W45–W53 连续九波零代码、零销账；四份外审（`FastestAI-W53整合深度审查报告`、`全局代码逐行审查-非UI审美问题清单`、`W53-深度审查与新功能波次预判`、`harness-w53-review`）在两点上一致：

1. W53 的账实与证据纪律无可挑剔，但体系在「推进」上停滞；
2. 真正卡住用户的不是 166 行 OPEN，而是两条**已实现、链路没接上**的 P0：端点清单读失败被当成首启（静默覆盖用户配置）、压缩按 token 落刀而硬闸按字符裁决（压完立刻熔断）。

用户本波指令为「全面推进」，且提供多模型端点与真机密钥（密钥留设备、未回灌）⇒ 以「不改行数上限、不碰 `core-kernel` 449/450 与 `GenerationRound` 400/400」为前提开代码波，回答外审提出的「总闸」：**不靠调上限，靠把修复放进有余量的模块**。

## 二、波内范围（正向白名单，文件域）

- `core-data`：`EndpointStore.kt` / `JsonFileStore.kt` / `McpServerStore.kt` / `FileMemoryStore.kt` / `NetMcpTransport.kt`
- `core-context`：`ContextCompressor.kt` / `LayeredContextCompressor.kt` / `BudgetFormula.kt`
- `core-agent`：`loop/ContextBudget.kt` / `tool/tools/*`（9 处 catch）
- `core-net`：`HttpTransport.kt`
- `core-mcp`：`McpClient.kt`
- `feature-chat`：`ManualCompress.kt` / `ConversationOps.kt`（模块总量 3985/4000，只许净增 ≤15 行）
- `feature-tools`：`SandboxFiles.kt`
- `.github/workflows/build.yml`（仅 `EXPECTED_TESTS` 常量 + 变更史注释）
- 测试：`EndpointStoreTest` / `ContextCompressorTest` / `ManualCompressTest` / `EndpointCapabilitiesTest`

**禁止**：`core-kernel` 任何 `.kt`、`GenerationRound.kt`、`LINE_CAPS` / `TOTAL_LINE_CAP`、G 编号、CI job、schema。

## 三、出口判据

1. 四闸门全绿（G1 rc=0 / G2 `violation=0` / G3 30/30 / G4 `tests=900 failures=0`）。
2. `EXPECTED_TESTS` 与严格 `@Test` 双向相等（895 → 900）。
3. 五条 P0/P1 判据各自有 JVM 用例或真机对照，且**对照组会响**（未压缩时同一历史仍熔断）。
4. 真机：回声 / 视觉 / 文件读写审批 / skill / MCP（vacuous）/ 手动压缩 / 自动压缩 / 1M 读数。
5. CI：内容提交双 run 四字段实取。
