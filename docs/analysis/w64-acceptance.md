# W64 验收物 — 外审 6 行条件销账 / journal 合流判据 / N-1 请求体定谳 / forRequest 残余窗口

> 基线 HEAD `25340a8`（W63 tip）｜现役仓 `harness-v2`｜包名 `com.rickeal.liquidharness`
> 证据逐件见 `evidence/raw/w64/MANIFEST_w64.txt`（18/18 OK / BAD=0 / MISS=0）。
> 本波 = **首次条件销账波**：外审 6 行 `OPEN` → `CLOSED`，状态列 39→45 / 166→160 / 216 不变。

## 步 1 · 外审分歧裁决（四份报告，逐条给值）

| # | 分歧 | 裁决 | 状态 |
|---|---|---|---|
| 1 | N-1「换键重跑保存链」处方（《W60-W63四波审查》§四.1） | **部分采纳，改判据对象**：历次主张不是保存链误拒（W54 定谳用例 + 现役白名单 `EndpointAdvancedValidation.kt:68` 已证），W60 步 8 未行使的是「运行时请求实证」；本波真机锚请求体，不重测保存链 | ✅ |
| 2 | wellFormed 合流形态（W63 外审 P1-1，《W62W63深度审查》§三） | **收**：加 ` at=` 恰好一次；KDoc 限定 2 改为「后一条 settled 会丢、方向 fail-safe」 | ✅ |
| 3 | journal 用例 904 维持 vs 904→907（两报告相反） | **受控破例 +1（905）**：判据空转风险 > 冻结字面；一个方法承载四态断言，build.yml 同提交对账 + 增量注释 | ✅ |
| 4 | `RunState.forRequest` 仍在 try 外（W63 P3-2） | **收（小改）**：移进 try、state 可空、finally 判空补 settled；unlock 必达不变 | ✅ |
| 5 | 外审 6 行销账（两报告） | **做**：判据 6 十一类必红逐类复跑 + §2.5a 口径裁定 + 判据 7 双分支；6 行 CLOSED | ✅ |
| 6 | 四处计数订正（P1-2） | **收（W64 时点注记，不回改历史正文）**：见步 6 | ✅ |

## 步 2 · 代码改动面（4 文件 + 1 配置）

| 文件 | 动作 | 说明 |
|---|---|---|
| `core-agent/.../journal/AgentRunJournal.kt` | 改 | `wellFormed` 加 ` at=` 恰好一次；KDoc 四条限定（合流形态两种截断、KDoc 后果写实） |
| `core-agent/.../AgentRunner.kt` | 改 | `RunState.forRequest` 移进 try，state 可空，finally 判空补 settled；199 行 ≤200 |
| `core-agent/src/test/.../AgentRunnerTest.kt` | 改 | 新增一个测试方法四态断言（完整行 True / `at=17` True / 截断 payload False / 合流行 False×2） |
| `.github/workflows/build.yml` | 改 | `EXPECTED_TESTS` 904→905 + 增量注释写明受控破例与 W60 段 P0-2 边界区分 |
| `docs/06-deferred-ledger.md` | 改 | 外审 6 行销账 + W64 段 + W34-03/W35-02 两处行内注记 |

## 步 3 · 判据先行与正控（必红变异）

| 判据 | 正控先行 | 必红实跑 | 结论 |
|---|---|---|---|
| wellFormed 合流约束 | Python 8 样本自洽 OK（`e5`）；JVM 四态断言绿 | **删 ` at=` 约束** ⇒ `wellFormedRejectsConflatedAndTruncatedJournalLines` 唯一 1 failed；还原后 sha256 逐字节一致 | ✅ 判据会响 |
| 判据 6（外审 6 行前置） | w32/w33/w34 三制品正常态 `OK feature/evidence invariants` ×3 | 十一类必红逐类 RED（11/11，含 `RED missing ref` / `RED assertions missing`） | ✅ 全类可指认 |
| 判据 7（销账指路） | 正常态 `OK ... (W64, declared=6)` | 6 行锚点删空 ⇒ `RED closed without current-wave pointer`（6/6）+ 第一行 `RED closed without runnable pointer`（3/3） | ✅ 双分支真响 |
| 恢复卡 | 完整 hex runId 冷启动弹卡 + 点「继续」补 settled | 合流行冷启动不弹卡（与 Python S6/S7 一致） | ✅ 双向成立 |

## 步 4 · 真机矩阵（<device> / Android 13 / 1080×2400，唤醒三步法，有线 adb）

| 格 | 判据 | 实测 | 四态 |
|---|---|---|---|
| G1 | 完整未结算行冷启动弹恢复卡 + 继续补 settled | 弹「上次运行被中断」+ 继续/丢弃；点继续后 journal 追加 `run_settled abcdef…` | ✅ |
| G2 | 合流行（缺 `\n`、后行接在 `at=` 尾）冷启动**不弹卡** | 落盘同一物理行含两个 ` at=`；冷启动只有「开始一段对话」，无恢复卡 | ✅（真机坐实 W63 缺口） |
| G3 | N-1：`parallel_tool_calls=false` 保存链往返 + 请求未受阻 | 重开逐字保留；设当前发 `ping` ⇒ 「Pong!」1.8k in/30 out；run 正常结算 | ⚠️ 部分（wire 请求体明文不落日志，不写成逐字验过） |
| G4 | 主流程（forRequest 移 try 后） | hello/长请求/ping 三对 `run_started→run_settled(cancelled=false)` 完整，无 GateRejected | ✅ |
| G5 | 取消链 | 历史取消 settled 样本 + JVM `cancelEmitsCancelledWithPartialText` 绿；本波未新触发取消 | ⚠️ 部分（分层如实） |
| G6 | 环境恢复 | 协议关闭、extraBody 清空、切回原 SuperVL 1M 端点；journal 由「丢弃」清盘 | ✅ |

**截图**：2 张 1080×2400 真图，上缘端点标识带打码（单色 `(30,30,34)`），签名/IEND/verify/load/尺寸/单色带逐张复核全通过。

## 步 5 · 四闸门 + 探针 + 台账符号 + 隐私门禁

| 项 | 实跑值 | 四态 |
|---|---|---|
| 闸 1 `assembleDebug` | RC=0；两轮独立出包 sha256 同为 `91d73d87…` | ✅ |
| 闸 2 `arch-guard.sh` | `result=PASS ok=27 vacuous=1 violation=0 fault=0`（G19 VACUOUS） | ✅ |
| 闸 3 `arch-guard-selftest.sh` | 30/30 PASS（281s） | ✅ |
| 闸 4 `fulltest.sh` | **tests=905 failures=0 errors=0 skipped=0**（14 模块） | ✅ |
| 探针 | rc=0（14 锚点 / 15 认领 / 11 反哨兵） | ✅ |
| 台账符号 | rc=0（328 kt / 22 类 / 66 方法，+1 = 新增方法） | ✅ |
| 隐私门禁 | `--ci-semantics` 三路 0 命中（route2 曾命中本波脚本内本机绝对路径，已改写为相对路径后转绿） | ✅ |

## 步 6 · 计数订正（四处，W64 时点注记，不回改历史正文）

| 项 | W64 注记 |
|---|---|
| W62 派单卡抬头 `2/4` | 波末实态 **5 提交 / 8 条 run**（含 2 次纯文档回填），抬头为计划值；三处相等以波末实态为准 |
| W63 验收物缺计数 | W63 实态 **6 提交 / 7 条 run**；交付判据锚 `fa07411`（Secret `46114170716` + Build `46114170667` 双绿） |
| W63 末笔 Secret | `25340a8` 的 Secret run **`46114493385`**（W63 §2.9 已补 W62 的 `46026829837`，本波补 W63 自己末笔，同型收口） |
| W51 `g3_selftest.txt` 行尾归一 | 2 846 → 2 806 B（纯 CRLF→LF，内容逐字节一致）；改动前后 sha256 见 `evidence/raw/w62/` 说明；本波不在任何 MANIFEST 内、不新建 MANIFEST（M3 净 0） |
| G20 142→143 | W62 handoff 引 `W6-08` 老号引用所致，全在台账、守卫绿，非新开 |

## 步 7 · CI（公开 API 四字段，curl 直连实时数据）

交 atom 实取。交付判据恒锚**最后一次落码 push 的双 run 全绿**；其后纯文档提交按降级规则只声明、不作 P0、不硬写 run 号。

## 步 8 · 收尾契约

- 本波计数：交 atom 实取（待推送后回填）。
- handoff / 验收物 / 台账 W64 段三处计数相等；最终 tip 交 atom 实取，不硬写 sha。
- 交付判据恒锚唯一代码提交双 run。

## 步 9 · 交给外部审查团队的制品 + 边界声明

**制品**：`docs/handoff-20261128-wave64.md`、本验收物、`docs/w64-dispatch.md`、`docs/06-deferred-ledger.md` 的 W64 段与 6 行销账注记、`evidence/raw/w64/` 全部文件（逐件见 `MANIFEST_w64.txt`）。

**不含**：会话历史 / 凭据 / 未打码账号字段 / 端点标识（型号名、URL、provider 一律占位符）/ 设备序列号 / 本机路径 / 用户私人图片 / 整段 logcat /「建议下一链做什么」。

**边界**：① 外审 6 行销账的判据 6/7 全部实跑会响、非自证；② `§2.5a` 口径 = 悬空指针 0（元引用 16 处不计入），可逆（外审不认可则 6 行回 OPEN 只动状态列）；③ N-1 只证保存链 + 请求未受阻，wire 请求体未逐字验；④ 合流形态真机坐实的是「不弹卡」，「后一条 settled 丢失」的结构性后果在 KDoc 写实、不写成已恢复；⑤ R4/1M 真压不写通过、MCP 端到端未行使、前台服务未做（R5 3/4）；⑥ 904→905 是受控破例、非静默；⑦ 本波止于交接，交由外部团队审查。
