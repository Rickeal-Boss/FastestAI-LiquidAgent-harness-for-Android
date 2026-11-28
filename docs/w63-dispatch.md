# W63 派单卡 — R2 压缩触发线按档位 / R3-a MCP 内网透传 / journal 半写行 / AgentRunner 收尾序

> 波号：W63（2026-11-28）｜基线 HEAD `6873fc8`｜现役仓 `harness-v2`（包名 `com.rickeal.liquidharness`）
> 用户指令要点：读记忆 + W61/W62 handoff + OPEN 行；按既有裁决计划推进（R2 / R3-a 于 W63 生效）；**不要「判据升级→全空转→重新取证」的零改动循环**；先本地 UI/功能 + 真机 adb 验证，通过后推仓与 CI。
> 本波销账集合 = 0 行（见台账 W63 段；外审 6 行未销，阻塞项逐条列于交接 §八）。

## 一、范围（本波真正改动的四件事）

| # | 项 | 裁决来源 | 文件域 |
| --- | --- | --- | --- |
| 1 | **R2 生效**：会话级自动压缩**触发线按档位化**（旧 `COMPACT_TRIGGER_CHARS=50 227` 恰等于 32K 档 L3 硬 cap ⇒ 该档自动压缩结构上不可达） | 用户 W60 裁决 R2「B 类即派，生效 W63」 | `core-context` / `core-agent` / `feature-chat` |
| 2 | **R3-a 生效**：MCP `allowPrivateNetwork` **正向透传**到 `HttpTransport.post` 的两道闸；并把 `blocked_unsafe_url:<REASON>` 从 `transport_error` 中分离出来 | 用户 W60 裁决 R3「安全波拆两半，a 类可编码进 B 类，生效 W63」+ W61 外审 P1-1 老项 | `core-mcp` / `core-data` |
| 3 | journal **半写行容错**（P2-2）：落盘配对只采信「kind 前缀 + 结尾 `at=<纯数字>`」的行 | W62 外审 P2-2（莫斯线） | `core-agent` / `core-data` |
| 4 | `AgentRunner.finally` **收尾序**（P3）：`runSettled` 抛 CE 时清理与 unlock 必达 | W62 外审 P3（玻璃线 §2.3） | `core-agent` |

**明确不做**（写入交接「明确未做」）：外审 6 行销账（阻塞项见交接）、`GlassLayerBudget` 4096 改设备能力探针（core-design 余 35 行，本波不动）、`activeRun` 改 `compareAndSet`（观察位）、前台服务（R5 范围 3/4 不变）、新增 `@Test`（M3 净 0）。

## 二、判据（先判据后动手，先跑已知必过样本）

| 格 | 判据 | 空命中语义（防假绿） |
| --- | --- | --- |
| G1 | 32K 档 + 47 145 字符历史 + 短请求，终态后日志出现 `auto_compact dropped=…`（**不是** `below_trigger`） | 只看「有 auto_compact 行」会被 `noop/below_trigger` 冒充 ⇒ 必须钉 reason 前缀 |
| G2 | 同一内容在**旧构建**上必为 `below_trigger`（修复前基线，已入仓） | 两次跑同一构建会双绿 ⇒ 必须锚不同 APK |
| G3 | 128K 档 + 同一内容 ⇒ `below_trigger`（触发线随档位，不是全局下调） | 必须先「新对话」清掉 `compression != null`，否则会命中 `already_compressed`（本波实测踩到） |
| G4 | MCP 私网 + 开关**关** ⇒ 设置页 `error: blocked_unsafe_url:PRIVATE_NETWORK` | loopback 会被无条件放行 ⇒ 判据对 loopback 是**假绿**，必须用非 loopback 私网字面量 |
| G5 | 同一配置开关**开** ⇒ `error: transport_error` / `timeout`（非 blocked） | 成功连通需真私网 MCP server ⇒ 本波无，正控标 ⚠️ 未行使 |
| G6 | 落盘注入缺 `at=` 的半截行 ⇒ 冷启动**不弹**恢复卡 | 「没看到卡」可因读盘失败而假绿 ⇒ 必须配 G7 正控 |
| G7 | 落盘注入完整 `run_started … at=<digits>` ⇒ 冷启动**弹**卡；点「继续」补 settled | 需用**未被 settle 过的新 runId**，否则命中已结算集合（本波实测踩到） |
| G8 | 取消后 `run_settled cancelled=true`，紧随其后新 run 被接受（无 GateRejected） | 只看「新 run 开始」不够 ⇒ 需 settled 行 + 下一 run 的 `run_started` 两条 |

## 三、执行顺序

1. 第 0 车：方案席裁决三份外审分歧 + 定 W63 范围（先于动手）。
2. 第 1 车：四处代码实现（开发席）+ 全量编译 + 五模块单测。
3. 第 2 车：真机八格（主理人行使；先唤醒三步法；截图脱敏后入证据）。
4. 第 3 车：四闸门 + 探针/台账符号/隐私门禁（CI 席产原始输出）。
5. 第 4 车：审查席增量 diff 审查（口径/行数/边界/假绿）。
6. 第 5 车：文档三件（交接 / 验收物 / 台账段）+ 提交 + CI 四字段回源。

## 四、边界

- 三顶格对象零触碰（`core-kernel 449/450`、`GenerationRound 400/400`、`feature-chat 3999/4000`）；`LINE_CAPS` / `TOTAL_LINE_CAP` 未调。
- 零新增 `@Test`（`EXPECTED_TESTS` 维持 904）；M3「冻结净 0」。
- R4 未触发：`frozenMs` 链本波不主张任何结论。
