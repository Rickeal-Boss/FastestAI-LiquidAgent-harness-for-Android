# W51 验收物 — 审查五处分歧裁决 / 台账段回补 / engine:* 残余标签面 / 白侧判据补强 / 真机网络 vacuous

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `e274e5cfb4b28bc9ddf36903a1b40ff0c7d64b92`（上一波文档 tip）。判定四态 `✅ / ⛔ / ⛔vacuous / ⚠️`。
> 「未触发」≠「已验证通过」；推断带 caveat、不写成事实。本验收物不写复合挂账号，段位用中文描述。

---

## 步 1 · 五份审查报告的五处分歧裁决

| # | 分歧 / 主张 | 裁决 | 回源依据 |
|---|---|---|---|
| 1 | 追认代裁机制（审查方再提「认/不认」） | ⛔ 不采纳 | 追认必要性建立在「授权原文不在仓内」的错误前提上——原文就在连续第八波三问裁决书正文；用户本轮字面「裁决分歧及方向」是行使裁决权，不是请求追认。硬塞追认等于把已收口三问重开成待拍板项 |
| 2 | journal 终局上裁决清单（第二次请求） | ⛔ 不采纳 | 「不代裁」与「加入清单」字面都求用户二选一，但派单卡是被动解禁（用户给字面才动），审查方反复推上待拍板位实质是变相代裁。本波用户字面未含 journal，5 份报告无一给出二选一决定 ⇒ 加清单只造空待拍板项 |
| 3 | 主线改小件批（B-4 / B-6 / MockWebServer 资产化，出口 @Test≥900、CLOSED≥45） | ⛔ 不采纳 | 与派单卡 0/0 和判据冻结冲突：≥900 要再加 5+ 条、与 895 实际增量对账冲突；≥45 要销 6 笔、与零销账冲突；审查方自己另一份写「不应为销账而销账」又设 ≥45 出口、自相矛盾；B-4「第九次」是历史挂账元债、非本波新增、无触发信号 |
| 4 | 打破六波零销账（真修并销账） | ⛔ 不采纳 | 该主张前置卡死自身：真修前置是内核 449/450 的用户单点裁决，本波用户未给；报告自己写「零销账不违约、是治理观察项」；待销判据全未行使，强销=把未验证写成已验证 |
| 5 | 用户真机指令覆盖判据冻结与轻量冒烟 | ⚠️ 分层调和 | 用户显式要求落在**证据采集层**（多模型 / 多上下文 / 多模态 / 文件读写 / skill / 压缩）；派单卡冻结与轻量冒烟落在**判据与销账口径层**。改方向＝否：强制判据集仍＝冒烟 2 格 + 判据先行；临时造判据违反「既有口径固化、不逐波追加」 |

**裁决总述**：五处全部「改方向＝否」，权威派单卡方向维持。收敛型注记波：1 P0 + 1 P1 + 4 P2 全走老号行内注记，状态列零变化、0 新开 / 0 销账。

---

## 步 2 · 第 0 车（账实与口径，不改代码）

| 项 | 结果 | 四态 | 证据 |
|---|---|---|---|
| P0-1 台账段回补 | 回补 `### W50 段` 2 行声明（内容指路 + 计数指路），台账 828 → 833 行 | ✅ | `docs/06-deferred-ledger.md` 段标题序列 |
| 12 条老号行内注记 | 逐行追加、状态列原样、pipe=7、零回开 | ✅ | 台账 216 挂账行、W51 注记 12 条；pipe≠7 仅历史 W6-08（存量豁免） |
| P2-3 深色图表述 | 验收物步 1 #2 与思考块老号注记改「深浅两态均有顺带裁剪图、但未做判据级断言/复现」 | ✅ | `docs/analysis/w50-acceptance.md` 步 1 #2、台账注记 |
| P2-4 增量注释 | build.yml 补 W50 / W51 两条增量注释；W50 归因核 `git show 98ec440` 为 core-agent 2 | ✅ | `.github/workflows/build.yml` 常量上方（W50/W51 两行） |
| P2-5 注释同步 | secret-scan hostname 段 W49 原句保留 + W50 订正行（已改 PCRE -P） | ✅ | `.github/workflows/secret-scan.yml` hostname 段 |
| P2-6 限定说明 | 上一波 handoff §七补「首提交 run 按并发合并口径不单列」 | ✅ | `docs/handoff-20261123-wave50.md` §七 |
| 守恒与两数 | 216 挂账行守恒、状态零变化、状态列 CLOSED/OPEN/CARRIED 不变；两数只派单卡抬头 | ✅ | 台账逐行自检、G20 141 ID 不变 |

---

## 步 3 · 第 1 车（代码 + 判据先行 + 四闸门）

### 3.1 判据先行（必红 → 修复 → 复绿）

| 断言 | 未修版本 | 修复后 | 四态 |
|---|---|---|---|
| `engineConfigFailureUsesFixedDigestAndKeepsTag`（新增） | RED：落盘为原文 `run failed: missing API key for endpoint [engine:config]` | GREEN：`run failed: engine failure [engine:config]` | ✅ |
| `engineNoEventsFailureUsesFixedDigestAndKeepsTag`（新增） | RED：落盘为原文 `stream ended without any event…` | GREEN：`run failed: engine failure [engine:no-events]` | ✅ |
| 白侧 `failureDigestCoversAllFixedDiagnosticBodies`（扩） | 变异 RED：删正则第 15 类 engine failure ⇒ 1 failed | 还原 GREEN：bodies 15 类 + 2 正控 + 2 反向 | ✅ |

证据：`evidence/raw/w51/g0_mustred_engine_tags.txt`（2 条 ComparisonFailure、行号 309/343）、`g0_green_engine_tags.txt`（两测试类各 8/0、正则对接 15 主体）、`g0_white_mutant_red.txt`（白侧变异必红）。

**判据缺口修复（审查席发现、主理人落笔）**：开发席的 2 条必红走 `captureSink()`（installFileSink lambda）旁路、绕过 `isProbeLine`，只验摘要文本；白侧 `DiagnosticLogSinkTest` 本波零 diff、bodies 停 14 类 ⇒ 删掉正则第 15 类 CI 也全绿。据审查缺口把该既有用例扩为 15 类 + `engine:config`/`engine:no-events` 两支带标签正控 + 2 条动态正文反向，**不新增 @Test、EXPECTED_TESTS 保持 895**；变异必红自证判据真响。

### 3.2 代码改动

| 改动 | 文件 | 核验 |
|---|---|---|
| `engine:*` 残余标签面 | 生成回合 `:282` | 单行扩展：`frame:`→provider error；`engine:config`/`engine:no-events`→engine failure；其余 digest=null 原样。仍 400/400、core-kernel 零改动 |
| 固定主体 15 类 | 落盘白名单 + KDoc | FAILURE_DIGEST 加第 15 类 `engine failure`、KDoc 14→15 同步；动态正文不落盘 |
| 测试数 | 构建工作流 | 严格 `893 → 895`（同提交对账、实测 @Test 895） |

### 3.3 四闸门

| 闸 | 结果 | 四态 | 证据 |
|---|---|---|---|
| G1 编译 | rc=0 | ✅ | `evidence/raw/w51/g1_build.txt` |
| G2 架构守卫 | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G19 空转项可见、G20 141 不变） | ✅ | `evidence/raw/w51/g2_archguard.txt` |
| G3 守卫自测 | `30/30 PASS（253s）` | ✅ | `evidence/raw/w51/g3_selftest.txt` |
| G4 全量单测 | `tests=895 failures=0 errors=0 skipped=0`（14 模块） | ✅ | `evidence/raw/w51/g4_fulltest.txt` |
| 脱敏门禁 | tracked / untracked / ignored 三路 0 命中 | ✅ | 提交前 `w46-local-secret-gate.sh` 实跑 |

---

## 步 4 · 真机（按矩阵收官轻量冒烟 + 用户功能面证据采集）

设备先唤醒（`mWakefulness=Awake`、非黑屏）后安装 debug 包；密钥留于真机、未回灌。

| 格 | 判据 | 结果 | 四态 | 证据 |
|---|---|---|---|---|
| 落盘白名单设备侧复核 | `run-as` 取回本波时间窗探针行，固定形态、零形态外行 | 3 行：`prefix` 账行 ×2 + `run failed: giving up after 1 retries [engine:give-up]` ×1 | ✅ | `evidence/raw/w51/deviceLogTail.txt` |
| 放弃路径证据标签 | `[engine:give-up]` 落盘可读 | 本波首次真机落盘可读（由网络失败触发） | ✅ | 同上 |
| 关键端点回声 | 逐字回声 + 思考块 + 上下文读数 | 设备 Wi-Fi 断开（DISCONNECTED、IP null、扫描 0 AP、ping 不可达），端点请求 `network error: UnknownHostException` | ⛔vacuous | 联网判据未行使、不伪造通过 |
| 多模型 / 多上下文 / 多模态 / 文件读写 / skill / 压缩 | 各自既有判据 | 同上网络不可达 ⇒ 全部未行使 | ⛔vacuous | 用户侧网络环境、仓内无法闭环 |
| 本波 P1-2 目标 `engine:config`/`engine:no-events` 真机路径 | 设备侧落盘可读 | 真机未触发这两类失败，由 JVM 必红覆盖 | ⛔vacuous | 不写成设备侧已验证 |
| MCP 外部 https / 1M 满窗真压 | 各自出口判据 | 未开专题波、0 服务器、未注入双硬限 | ⛔vacuous | 条件触发专题波不动 |

**回灌级别**：只写脱敏判据结论、固定形态探针行与 deviceLogTail；不回灌整段 logcat / 凭据 / 端点标识 / SSID / 序列号 / 本机路径 / 用户正文。

---

## 步 5 · CI（公开 API 四字段实取）

- 提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；PAT 仅 URL inline 一次性使用，remote URL 保持干净、无凭据落盘。
- **最终代码 tip `4b2e0376a26d6fd3970100eb9ecaad665615f339` 双 run 全绿**：
  - Build `45313166010`（`head_sha=4b2e0376a26d6fd3970100eb9ecaad665615f339` / `status=completed` / `conclusion=success` / `updated_at=2026-11-24T02:34:58Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45313166010`）。
  - Secret scan `45313166024`（`head_sha=4b2e0376a26d6fd3970100eb9ecaad665615f339` / `status=completed` / `conclusion=success` / `updated_at=2026-11-24T02:33:04Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45313166024`）。**交付判据 = 最终代码 tip 双 run 全绿。**
- 文档 tip（本验收物所在提交）触发的 Secret scan 按既有降级口径只声明存在、不逐条列号；Build 0 run 为 `paths-ignore` 既定口径。不为补链新建提交。
- 本地四闸门不代替 CI；CI 绿以公开 API 四字段为准。

---

## OPEN 挂账（触发信号 / 缓解动作 / 中文段位）

| 挂账号（中文段位） | 触发信号 | 缓解动作 |
|---|---|---|
| 压缩量纲同尺化余项（压缩量纲老号） | 字符闸熔断与无可压内容并存 | 先获内核再分层 / 行数上限单点裁决；裁定前不动内核、不调上限 |
| 内核本体再分层（拆分三方老号） | 内核模块触及行数上限（当前 449/450） | 拆分或上限处理须用户单点裁决；本波零内核改动 |
| 1M 满窗真压双硬限（真压两条老号） | 需要证明满窗行为 | 注入式双硬限 + 跨触发线历史仍锁单点开波；本波无网络、更未真压 |
| MCP 外部 https（明文三条老号） | 需要真实外部服务器 | 0 服务器 ⇒ 判据 vacuous，不写成已验证 |
| journal 二选一（journal 两条老号） | 用户字面二选一（开波 / 永不开波） | 不代裁、不反复上待拍板清单、不合并销账 |
| 思考块余项四件（思考块渲染老号） | 四态判据级截图齐 + 协作用例升级行为级 | 按裁决维持 OPEN、触发后再审议；顺带裁剪图不算 |
| 断词两条边界（拥堵断流老号） | 真机或 MockWebServer 复现拥堵断流 / 错误帧后无上报 | 触发即单独开波收口；本波不回开已关行 |
| 自动压缩 applied 分支（压缩族老号） | 需要跨越触发线的历史 + 网络 | 本波网络不可达，未观察到 applied；不造假 dropped |
| 设备网络 / 供给面 / 凭据轮换（用户侧） | 设备 Wi-Fi 断开、扫描 0 AP；连续 404；凭据曾 URL inline | 联网判据 vacuous、报障摘池与轮换均为用户侧动作，仓内只记事实 |

---

## 明确未做（逐条带挂账号前缀）

压缩量纲同尺化余项（压缩量纲老号）｜内核本体再分层（拆分三方老号）｜1M 满窗真压双硬限（真压两条老号）｜MCP 外部 https（明文三条老号）｜journal 二选一（journal 两条老号）｜思考块余项四件（思考块渲染老号）｜拥堵断流两条边界（拥堵断流老号）｜价格维度（方向采纳未开）｜凭据轮换（用户侧）｜追认代裁机制（审查方请求、已否决）｜主线小件批 B-4/B-6（已否决、历史挂账元债）｜打破零销账（前置未满足、已否决）｜多模型 / 多上下文 / 多模态 / 文件读写 / skill / 压缩真机面（设备网络不可达、vacuous）｜判据波次盲区（冻结）｜历史缺口不补造。

---

## 边界

① P1-2 两支失败的语义由 JVM 必红钉住，真机未触发不写成已验证。② `digest` 只保证落盘可读，ring 仍保留 provider 完整 message。③ 固定失败主体现为 15 类（上一波 14、再前 13，旧文档 12 是笔误）。④ 白侧第 15 类有 committed 判据（变异必红 → 还原 GREEN），不依赖 Python 复刻。⑤ 轻量冒烟的档位是常规请求、且本波网络不可达，不是满窗真压。⑥ 放弃路径 `[engine:give-up]` 真机落盘由网络失败触发，非 P1-2 目标路径。⑦ 文档 tip 的 Secret run 降级记录，交付看最终代码 tip 双 run。

---

## 交给外部审查团队的制品

- 本验收物、`docs/handoff-20261124-wave51.md`、`docs/w51-dispatch.md`、`docs/06-deferred-ledger.md`（回补段 + 12 条行内注记）；
- `evidence/raw/w51/`：`g0_mustred_engine_tags.txt`、`g0_green_engine_tags.txt`、`g0_white_mutant_red.txt`、`g1_build.txt`、`g2_archguard.txt`、`g3_selftest.txt`、`g4_fulltest.txt`、`deviceLogTail.txt`；
- 代码主体逐文件 diff：生成回合、落盘白名单、两份测试、两份工作流、测试数常量；
- 不含：会话历史、凭据、端点完整标识与密钥、未打码账号字段、设备序列号、SSID、本机路径、用户私人图片、整段 logcat、任何「建议下一链做什么」。

> 本波止于交接，交由外部团队审查。
