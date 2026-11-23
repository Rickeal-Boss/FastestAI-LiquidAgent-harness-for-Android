# W50 验收物 — 三问裁决落账 / 标签语义与 digest 兜底 / 门禁整值白名单 / 轻量真机冒烟

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `048d60c386901ad93b060d82e69d5fad3647f533`（W49 tip）。判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`。
> 「未触发」≠「已验证通过」；推断带 caveat、不写成事实。派单卡 = `docs/w50-dispatch.md`。
> 证据根 = `evidence/raw/w50/`。

---

## 步 1 · 分歧裁决（五份审查报告 + 连续第八波三问）

| # | 分歧 / 主张 | 裁决 | 回源依据 |
|---|---|---|---|
| 1 | 三问（思考块老号 / 断词老号两边界 / 矩阵走向） | ✅ 采信审查方裁决，状态零变化 | 五份报告一致：维持 OPEN / 维持 CLOSED 行内 + 触发即开波 / 矩阵形态收官转条件触发专题波；均不销账。本波不重跑 11–13 格全量回归 |
| 2 | 思考块「四态截图 0 件」 | ✅ 有条件采信（订正事实表述） | 仓内确有 W41 四态裁剪图（`evidence/raw/w41/w2401_*_crop.png`），但它们是顺带目视、非判据级四态截图，深色主题未判据级覆盖；销账判据仍未行使，结论（维持 OPEN）不变 |
| 3 | 断词老号两边界收口 | ⛔ 不采纳 | 收口等同回开已关行，需先裁回开与行数；本波只做行内标签语义订正，不写成边界收口 |
| 4 | 收尾契约自指（「此后不再回填」又触发新 run） | ✅ 采信并改根 | 一次性记录 W49 实取 6 条 run；交付判据 = 最终代码 tip 双 run 全绿，文档 tip 按既有降级口径只声明存在，不为补链新建提交 |
| 5 | hostname 证据 [4][5] 名实不符 | ✅ 采信并重出 | 旧证据标 GREEN 却输出 RED 且无 rc；本波五态证据每段带 rc，判据级结论限定为「现役 tracked 路干净」，不写成代码泄漏 |
| 6 | 标签语义漂移 / message-only 摘要缺失 | ✅ 采信并真修 | 两个放弃路径显式标签；provider 原文改固定摘要；三条内核必红先 RED 后 GREEN |
| 7 | 固定主体数「12 vs 13」笔误 | ✅ 采信并订正 | 旧正则实为 13 类；本波为 message-only 支补 `provider error` 后为 **14 类**，两类数字均显式留痕，避免再度脱节 |
| 8 | 审查方另报的分支名截断、尾日志截断等 | ⛔ 不据以改仓 | 关键字节以 `git grep` / `run-as cat` 为准；本波不新增同类改仓动作 |

**冻结口径**：零新 G 编号 / 零新 CI job / 零新判据编号 / 零新挂账行；「写 X 未发生前先自证判据会响」原样沿用，不逐波追加。

---

## 步 2 · 第 0 车（账实与口径）

| 项 | 内容 | 四态 | 证据 |
|---|---|---|---|
| 三问裁决落账 | 12 条老号行内注记：思考块老号维持 OPEN、断词老号维持 CLOSED 不回开、矩阵三条老号转条件触发专题波；状态原样、pipe=7、不回开 | ✅ | `docs/06-deferred-ledger.md` 对应 12 行；W50 派单卡抬头两数 |
| 段声明收敛 | W49 段压到 2 行（内容指路 + 销账集合），W50 段同口径 | ✅ | `docs/06-deferred-ledger.md` 两个段位 |
| 收尾契约改根 | W49 交接与验收物一次性记录 6 条 run，撤销「本条之后不再回填」自指句；W50 起只声明文档 tip 存在、不逐条列号 | ✅ | `docs/handoff-20261123-wave49.md` §七、`docs/analysis/w49-acceptance.md` 步 5 |
| 证据名实 | hostname 五态重出，每段带 rc；结论不越界 | ✅ | `evidence/raw/w50/g0_3bext_hostname_v2.txt` |
| 守恒 | 挂账行 216 / 已关 39 / 开放 166 / 结转 11；零本波新挂账；G20 141 ID 不变 | ✅ | `evidence/raw/w50/g2_archguard.txt` |

---

## 步 3 · 第 1 车（代码 + 判据先行 + 四闸门）

### 3.1 判据先行（必红 → 修复 → 复绿）

| 断言 | 未修版本 | 修复后 |
|---|---|---|
| `retryGiveUpDigestKeepsTagWithoutProviderText`（改写断言） | RED：落盘带上一轮 `[http:rate]` | GREEN：`[engine:give-up]` |
| `partialOutputFailureCarriesExplicitTag`（新增） | RED：半截输出仍带 `[http:rate]` | GREEN：`[engine:partial-output]` |
| `providerRawMessageUsesFixedDigestAndKeepsTag`（新增） | RED：provider 自由文本整行落盘 | GREEN：`run failed: provider error [frame:protocol-fallback]` |
| `failureDigestCoversAllFixedDiagnosticBodies`（扩断言） | RED：`provider error` 不在白名单 | GREEN：14 类固定主体全通过 |

证据：`evidence/raw/w50/g0_mustred_before_fix.txt`、`evidence/raw/w50/g0_green_after_fix.txt`。
范围限定：固定摘要只作用于 `frame:` 前缀（provider 原文支）；HTTP / 看门狗 / stream failed 固定模板支保持原摘要，由同批既有断言钉住。生成文件仍 400/400，未调行数上限。

### 3.2 hostname 整值白名单（CI + 本地同口径）

- 原判据「属性前缀 + 引号后首字符非 `<` 即命中」⇒ 闭合标签尾随正文绕过、空值误报。
- 新判据：只有两个登记占位符豁免，其余一律命中；CI 与本地脚本同口径。
- **接线坑（本波自捕）**：先只替换正则、未把 `grep -E` 改成 `grep -P`，注入在两路**空过**；改为 PCRE 后同一注入两路必红。该中间态如实留痕，不写成「换正则即完成」。
- 五态：tracked GREEN / 绕过注入 RED / 空值 RED / 占位符 GREEN / 移除后门禁 GREEN，见 `evidence/raw/w50/g0_3bext_hostname_v2.txt`。

### 3.3 四闸门

| 闸 | 结果 | 四态 | 证据 |
|---|---|---|---|
| G1 编译 | `assembleDebug` rc=0 | ✅ | `evidence/raw/w50/g1_build.txt` |
| G2 架构守卫 | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`；G20 141 ID 不变 | ✅ | `evidence/raw/w50/g2_archguard.txt` |
| G3 守卫自测 | `30/30 PASS（296s）` | ✅ | `evidence/raw/w50/g3_selftest.txt` |
| G4 全量单测 | `tests=893 failures=0 errors=0 skipped=0`，14 模块 | ✅ | `evidence/raw/w50/g4_fulltest.txt` |
| `EXPECTED_TESTS` | `891 → 893`（同提交对账） | ✅ | `.github/workflows/build.yml` |
| 脱敏门禁 | 本地三路 0 命中；证据入仓后复扫 0 命中 | ✅ | 门禁原始输出与证据目录复扫 |

---

## 步 4 · 真机轻量冒烟（按矩阵收官口径，2 格）

设备先唤醒（`mWakefulness=Awake` + 非黑主屏截图确认）再 `pm install -r -t`；密钥留于真机、未回灌。未跑 11–13 格全量回归。

| 格 | 判据 | 四态 | 证据 |
|---|---|---|---|
| E1 关键端点回声 | `echo-W50LIGHTNING` 逐字回引 + 思考块 + `上下文 1.9K / 127K`；发送前 dump 输入框逐字校验，未再发生吞尾 | ✅ | `evidence/raw/w50/e1_lightning_echo_crop.png`（裁掉端点头与键盘） |
| E2 落盘白名单设备侧复核 | `run-as` 取回本波时间窗 3 行，全部固定形态（prefix / reason 码），零形态外行与自由文本回显 | ✅ | `evidence/raw/w50/deviceLogTail.txt` |
| 放弃 / 半截路径标签 | 真机本波无 429、无半截输出 ⇒ 判据未被行使 | ⛔vacuous | 只由 JVM 必红覆盖，不写成设备侧已验证 |

档位为轻量冒烟的常规请求读数，不是 1M 满窗真压。

---

## 步 5 · CI（公开 API 四字段实取）

最终代码 tip `3e01131bd3795fd4e2b3e1170ae9d980fe1e7b44`：

| run | 对象 | head_sha | status | conclusion | updated_at |
|---|---|---|---|---|---|
| `45203888349` | Build | `3e01131bd3795fd4e2b3e1170ae9d980fe1e7b44` | `completed` | `success` | `2026-11-23T15:21:27Z` |
| `45203889043` | Secret scan | `3e01131bd3795fd4e2b3e1170ae9d980fe1e7b44` | `completed` | `success` | `2026-11-23T15:19:33Z` |

- 链接：Build `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45203888349`；Secret scan `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45203889043`。
- **交付判据 = 最终代码 tip 双 run 全绿**；本波无红态 fix 循环。
- 文档 tip（本交接所在提交）触发的 Secret scan 按既有降级口径只声明存在、不逐条列号；Build 0 run 为 `paths-ignore` 既定口径。

---

## OPEN 挂账（触发信号 / 缓解动作 / 挂账号）

| 挂账号（中文段位） | 触发信号 | 缓解动作 |
|---|---|---|
| 压缩量纲同尺化余项（压缩量纲老号） | 字符闸熔断与无可压内容并存 | 先获内核再分层 / 行数上限单点裁决；裁定前不动内核、不调上限 |
| 内核本体再分层（拆分三方老号） | 内核模块触及行数上限 | 拆分或上限处理须用户裁决；本波零内核改动 |
| 1M 满窗真压双硬限（真压两条老号） | 需要证明满窗行为 | 注入式双硬限与跨触发线历史仍锁单点开波；本波只做常规读数 |
| MCP 外部 https（明文三条老号） | 需要真实外部服务器 | 明文门禁未解禁且服务器为 0 时判据 vacuous，不写成已验证 |
| journal 二选一（journal 两条老号） | 需要落 journal 制品 | 须用户字面二选一；不代裁、不合并销账 |
| 思考块余项四件（思考块渲染老号） | 四态判据级截图齐 + 协作用例升级行为级 | 本波按裁决维持 OPEN，触发后再审议 |
| 断词两条边界（拥堵断流老号） | 真机或 MockWebServer 复现拥堵断流 / 错误帧后无上报 | 触发即单独开波收口；本波不回开已关行 |
| 自动压缩 applied 分支（压缩族老号） | 需要跨越触发线的历史 | 本波只观测到 below_trigger，不造假 dropped |
| North 供给面 / 凭据轮换（用户侧） | 连续 404 / 凭据曾以 URL inline 使用 | 报障摘池与轮换均为用户侧动作，仓内只记事实 |

---

## 明确未做（逐条带挂账号前缀）

压缩量纲同尺化余项（压缩量纲老号）｜内核本体再分层（拆分三方老号）｜1M 满窗真压双硬限（真压两条老号）｜MCP 外部 https（明文三条老号）｜journal 二选一（journal 两条老号）｜思考块余项四件（思考块渲染老号）｜拥堵断流两条边界（拥堵断流老号）｜价格维度（方向采纳未开）｜凭据轮换（用户侧）｜Nano Omni 与 Bolt E4B 会话轮（本波时间窗未行使）｜判据波次盲区（冻结）｜历史缺口不补造。

---

## 边界

① 放弃 / 半截标签的语义由 JVM 必红钉住，真机未触发不写成已验证。② `digest` 只保证落盘可读，ring 仍保留 provider 完整 message。③ hostname 白名单豁免的是两个登记占位符，不是任意以 `<` 开头的值。④ 固定主体数现为 14 类（旧文档 12 为笔误、W49 实为 13、本波 +1）。⑤ 1M 档为常规请求读数，非满窗真压。⑥ 文档 tip 的 Secret run 降级记录，交付看最终代码 tip 双 run。

---

## 交给外部审查团队的制品

- 本验收物、`docs/handoff-20261123-wave50.md`、`docs/w50-dispatch.md`、`docs/06-deferred-ledger.md`（W50 段 + 12 条行内注记）；
- `evidence/raw/w50/`：`g0_mustred_before_fix.txt`、`g0_green_after_fix.txt`、`g0_3bext_hostname_v2.txt`、`g1_build.txt`、`g2_archguard.txt`、`g3_selftest.txt`、`g4_fulltest.txt`、`e1_lightning_echo_crop.png`、`deviceLogTail.txt`；
- 代码主体逐文件 diff：内核生成回合、落盘白名单、两份测试、门禁脚本、两份工作流、测试数常量；
- 不含：会话历史、凭据、端点完整标识与密钥、未打码账号字段、设备序列号、SSID、本机路径、用户私人图片、整段 logcat、任何「建议下一链做什么」。

> 本波止于此，交由外部团队审查。
