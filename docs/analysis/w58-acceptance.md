# W58 验收物 — W57 证据入仓 / 两假绿加固 / 收尾契约计数订正

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 = W57 最终 tip `6f5e35281226aa19852edb906b210d9e834dff20`（atom 实取）；本波**无新增功能代码**（唯一 `.kt` 改动 = 一处 KDoc 文案订正），判据脚本加固 + 25 件证据入仓 + 账实订正。**最终 tip 以 atom 实取为准**（详见步 5 计数 + atom 委托）。台账权威版 = `docs/06-deferred-ledger.md`；
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 收尾契约「计数 + atom 委托」：本文档声明本波提交/run 计数，最终 tip 与逐号 run 由 atom / Actions 实取。

## 步 1 · 外审分歧裁决（6 份报告）

| # | 主张 | 回源/实测 | 裁决 | 四态 |
|---|---|---|---|---|
| 1 | 三份报告一致：`evidence/raw/w57/` 0 件，四态升级建在仓外证据（P0） | 实测目录不存在（W53=7/W54=5/W55=5/W56=10）；证据源全在仓外可补，非灭失 | 采纳：第一优先级补入仓，四态不回改、加待复核注记 | ⛔ 证据纪律倒退 → ✅ 补入仓 |
| 2 | 收尾契约第 14 次：atom 委托做了、本波计数没做（P1-1） | atom/Actions 实取 W57 = 2 提交（1 代码 `edf0437` + 1 回填 `6f5e3528`）/ 3 run；原文档只列 ① 的 2 run、无计数、无 `45774389062` | 采纳：三处统一补计数 + 回填 run，保留 atom 委托，禁第 3 提交 | ⚠️ → ✅ |
| 3 | A2 形态判据控制流盲区 K（P1-2）：摘 `if (sensitive) REDACTED` 保留字面量仍 rc=0 | 现役脚本 :734-742 只认两具名头字面量；外审副本变异实测 rc=0 假绿、凭据明文落盘 | 采纳：A2 段增控制流形态认领 + K 必红，零新 `@Test` | ⚠️ 判据缺口 → ✅ 加固 |
| 4 | 莫斯线 P0-1 认领静默逃逸（与 #3 不同根因） | 现役脚本 :634-642 首字面量失配即 `continue`；首字面量内字段改名 rc=0、认领数 15→14 藏在 OK 行 | 采纳：认领失败禁静默跳过 + 显式豁免清单 + 认领基数判据 + M 必红 | ⚠️ → ✅ 加固 |
| 5 | A2 单列注记落空：W57 五条注记全是 W56 补录 | 回源台账 `grep Cookie/X-Auth-Token/redactHeaders` 仅历史 W20-09 异物 | 采纳：A2 登记由 W58 补登凭据白名单老号行，不回改 W57 四态 | ⚠️ → ✅ |
| 6 | G5/G6/G7 编号错配、脚本行数 640 vs 702、A4 措辞偏激进、KDoc 公式 | roadmap §五 G5=禁 jitpack/G6=模块边界/G7=零业务；`ManualCompress.kt:125` 旧写 `hardCharCap` 返回值实为 ×ratio | 采纳账实订正；A4 按 P1-1 同规格改「口径厘清+登记待修」；KDoc 1 行订正 | ✅ |
| 7 | A2 行为级用例升第 0 车硬前置 | 需新 `@Test`（破 904）+ 夹具载体，与「904 维持 / 条件触发」执行边界冲突 | 不采纳升格，维持第 1 车条件触发 | ⛔ 不越执行边界 |
| 8 | 用户确认 dots-3 / north 两端点现在都不可用 | 与 W57 设备 HTTP404 + 供给方下线真相一致 | 采纳：停止占格、不计可用数、不推断原因，本波不铺矩阵 | ✅ |

**方向裁决**：本波是**证据与账实收口波**，不做新功能、不铺新矩阵。核心判断——W57 破零改动循环的三件实质事（A2 / 脚本全库化 / 真机矩阵）方向正确，但把最重要的四态升级建在了仓外证据上；本波不是推翻 W57，而是把它补回「外审可复核」状态，并堵住外审实测坐实的两个判据假绿。

## 步 2 · 改动（证据入仓 + 两假绿加固 + 账实订正）

### 2.1 P0：W57 证据入仓 `evidence/raw/w57/`（25 件）
原目录不存在（W53/54/55/56 分别 7/5/5/10 件）。本波从仓外证据源脱敏补入，**24 件制品 + 1 份 MANIFEST**：
- **设备原始探针** `deviceLogTail.txt`：逐字取自设备 `run-as ... cat files/harness/logs/agent-0.log`，8 行与现场 grep `diff` 完全一致（本波 5 行 + 历史对照 3 行按日期标注），含 `2026-11-26T06:45:37.959358Z context compressed before round 1: 501 -> 277`、`auto_compact dropped=220 kept=283`、`context_overflow_prevented chars=48365 cap=27904` 正控。头注 + 末尾 SELF-AUDIT + 形态外 0。
- **闸 1–4 原始输出 + 机械自检 / 台账符号 / 脱敏门禁**：`g1_build` / `g1_det_build1` / `g1_det_build2` / `g2_arch_guard` / `g3_selftest` / `g4_fulltest` / `g4_fulltest_rerun` / `g5_probe` / `g6_ledger` / `g7_secretgate`，路径→`<workspace>`、代理→`<proxy>`、私人临时路径→`<private-temp>`，复扫零残留。
- **确定性出包** `g1_deterministic_build.txt`：APK sha256 `34be7494b0186001a1409c1eb5694964a08707130cd48e333a11c6f0285ae2fc` 现场实算全值（size 68,473,382 / mtime 14:44:03）；**诚实边界**：第 1 轮产物已被覆盖、其哈希为当轮回报补录，旧包 sha256 仅 8 位截断 `ec60a2e3…` 不可复核、不编造；设备当前安装包 size 同旧包但 hash `48f4fe1c…`（更旧安装），账实分离。
- **两假绿变异自证** `g5_mutation_w58.txt`：基线 + K/M/C/F/G + 反假绿，三退出码全 PASS。
- **12 张判据级截图**：逐张 `verify()` + IEND + `load()` + 尺寸（1080×2400）+ 目视；`png_dots3_testconn.png` 含完整 Base URL，首版打码漏上半部、目视发现后按像素重做并二次目视确认；3 张（compact128 / compact500 / file_write_approval）标 ⚠️ 部分（仅 UI 侧状态、无明确账行/审批卡）。
- `MANIFEST.txt`：25 件逐件 sha256（现场实算）+ 摘要 + 脱敏处理 + 四态判据 + 4 条备注。

### 2.2 两假绿加固 `scripts/w55-probe-form-sync.sh`（702→767 行）
- **A2 控制流必红**：返回表达式归一后须含 `if(sensitive)redactedelse` 形态（:734-742）；K 变异（摘控制流、保留 `equals("Cookie")`/`equals("X-Auth-Token")` 字面量）⇒ rc=1。`contains("cookie")` 冒充具名头仍被 `equals(` 断言拦下（反假绿补测 rc=1）。
- **认领逃逸堵漏**：`EXPECTED_CLAIMS=15` 基线常数 + `CLAIM_EXEMPT` 25 条一次性显式豁免（49 个 `AgentLogStore` 写点 = 15 认领 + 25 豁免 + 9 纯变量位）；认领失败不再静默 `continue`，豁免清单外一律判红（:634-642）；认领数波动即判红（:648-653）。M 变异（`compress=disabled_by_policy`→`char_cap=` 首字面量改名）⇒ rc=1（旧版此处假绿 rc=0）。
- **回归**：C（非锚点 `reason=`→`why=`）rc=1、F（同步改名）rc=0 不过拟合、G（放宽正则）rc=1、反假绿 rc=1、还原基线 rc=0。5 个被变异生产 `.kt` 经 `filecmp` 验证与备份**逐字节 IDENTICAL**，全库 `.kt` diff 仅 ManualCompress KDoc 一处。

### 2.3 账实订正（W57 三份文档 + 台账）
- 收尾契约：W57 验收物步 5 / 交接 §六 / 台账 W57 段统一「W57 本波 2 提交（① `edf0437` + ② `6f5e3528`）/ 3 条 run 全绿」，补 `45774389062` + Build 未触发（`docs/**` paths-ignore）；与 W56「4 提交/5 run」历史值分开写。
- 内核账行 ✅ 加「⚠️ 待 W58 入仓复核」注记（四态本身不回改）。
- 制品清单：仓外 `.w57_ci_tmp/` → 仓内 `evidence/raw/w57/`；注明三份核心 W57 文档由回填提交 `6f5e3528` 加入、按最终 tip（atom 实取）取全树。
- 闸门序号去 G 前缀（roadmap §五 G5/G6/G7 另有其义）、W57 脚本行数订正 640→702、A2 单列注记落空订正、A4 措辞规格化、`ManualCompress.kt:125` KDoc 公式订正。
- 台账：新增 `### W58 段` + 5 条老号行内注记（凭据白名单 / 证据可复算性 / 收尾契约 / 台账纪律 / 压缩量纲回归），状态列原样、pipe=7、零新开零销账。

### 2.4 行数与测试基线
`EXPECTED_TESTS=904`（build.yml 未动），strict `^\s*@Test\b` 实测 904。零新增 @Test / 判据编号 / CI job / 挂账号。顶格三对象零触碰（`core-kernel 449/450`、`GenerationRound 400/400`、`feature-chat 3999/4000`）。

## 步 3 · 四闸门（证据仓内可复核）

原始输出见 `evidence/raw/w57/`（步 2.1）。闸门序号「闸 1–4」非 G 编号（roadmap §五 G5/G6/G7 另有其义）。

| 闸 | 结果 | 四态 | 仓内证据 |
|---|---|---|---|
| 闸 1 编译 | rc=0 `BUILD SUCCESSFUL`（1m38s，1 executed / 269 up-to-date）；现场 APK sha256 `34be7494…` 与入仓双哈希逐值一致、坐实当前包即 A2 入包产物，本波 KDoc 不触逻辑故不重做确定性出包 | ✅ | `g1_build.txt` / `g1_deterministic_build.txt` |
| 闸 2 架构守卫 | `result=PASS ok=27 vacuous=1 violation=0 fault=0`，G20 141 ID、G21 棘轮 OK | ✅ | `g2_arch_guard.txt` |
| 闸 3 守卫自测 | rc=0，`30/30 PASS（308s）`，G5a–G23 + VAC-G1b/VAC-G21 变异注入判红成功 | ✅ | `g3_selftest.txt` |
| 闸 4 全量单测 | rc=0，`tests=904 failures=0 errors=0 skipped=0`，14 模块、92 个 TEST-*.xml、mtime 新鲜；后台框架 failed 与脚本 rc=0 矛盾，前台串行重跑交叉验证一致 ⇒ 框架标记工具层噪声 | ✅ | `g4_fulltest.txt` / `g4_fulltest_rerun.txt` |
| 机械自检 | rc=0（14 锚点 + 15 认领 + 11 反哨兵 + A2 控制流 + 两具名头）；767 行，K/M 必红（见 2.2） | ✅ | `g5_probe.txt` / `g5_mutation_w58.txt` |
| 台账符号自检 | rc=0（21 类 / 65 方法 / 323 kt） | ✅ | `g6_ledger.txt` |
| 脱敏门禁 | rc=0，三路全 0（含新增 25 件证据扫描） | ✅ | `g7_secretgate.txt` |

> 七闸门全 ✅，无 ⛔、无 ⛔vacuous（G2 的 vacuous=1 是既有基线 G19，非本波引入）。`tests=904` 三方对账：strict `^\s*@Test\b` = 904 == build.yml `EXPECTED_TESTS='904'` == G4 XML 汇总 904。

## 步 4 · 轻量冒烟（真机，密钥留设备）

本波**不铺新矩阵**（先把 W57 证据补齐）。三格：
- **端点回声** ✅：NV SuperVL 120B 1M，ASCII 哨兵 `PONG-W58-SMOKE` 最终答案正文区逐字命中（已排除仅思考区命中的假绿），读数 `1.8k in / 50 out`；模型名公开、端点完整 URL 不写；临时截图在仓外 `.w58_device_tmp/`。
- **落盘白名单复核** ✅：`run-as ... cat agent-0.log`（非 logcat），日志 550→553 行本波在写；正控先行（非空 550 行、grep 会响）；现役固定形态 6 族在场（`auto_compact=noop` 289 / `auto_compact dropped` 7 / `manual_compress=noop` 7 / `manual_compress dropped=` 7 / `context compressed before round` 1 / `context compressed:` 1），形态外 0（首轮 exclusion 过窄误报 8 行，回源确认属固定族后归零）。
- **OR 两端点** ⛔ 不适用：dots-3 / north 用户已确认现在都不可用、本波不测、停止占格、不推断原因。

## 步 5 · CI（公开 API 四字段，curl 直连实时数据）

提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；PAT 仅 URL inline 一次性使用，remote 保持干净、无凭据落盘。

**本波计数与 run 四字段待推送后 atom/Actions 实取填入**（最终 tip 交 atom、不硬写）。交付判据 = 本波唯一含判据/证据改动的提交双 run 全绿；纯文档回填提交不计判据、计入提交实态。

## 明确未做（逐条带中文段位）

A2 运行时行为级用例（需 `JsonFileStore` 夹具 + 新 `@Test`，凭据白名单段位，第 1 车条件触发）｜A2 真机复测（设备装更旧包，凭据白名单段位）｜手动压缩干净前后对比 / 撤销入口（压缩量纲段位）｜灌满 128K+ 长历史压测（满窗真压冻结组）｜MCP 服务器工具调用（0 服务器 ⛔vacuous，安全收口段位）｜Dots3/North 供给核查（用户已确认不可用、停止占格，端点矩阵段位）｜A4 免审批 / A5 代次门 / A6 双终态（用户单点裁决段位）｜内核行数 / 压缩量纲余项 / 安全波整包 / 满窗真压 / journal 二选一（冻结 5 组，触发只登记）｜冻结项长远裁决书落笔（用户未逐字面裁决，不代裁）｜凭据轮换（用户侧）｜历史缺口不补造。

## OPEN 挂账（触发信号 + 缓解动作 + 中文段位；零新开零销账）

逐条见台账权威版 `### W58 段` 与 5 条老号行内注记。本波零新开零销账、状态列零变化。关键触发信号：
- 证据可复算性段位：任一波 `evidence/raw/wXX/` 缺件 / 标题正文矛盾 ⇒ 先补入仓再谈四态升级。
- 凭据白名单段位：A2 控制流被摘除 / 认领静默逃逸 / 具名头改泛化 ⇒ 对应变异必红；gitleaks 规则升级 `char_budget=` 判红 ⇒ 改形状、不加 allowlist。
- 收尾契约段位：外审按最终 tip 复核四字段 ⇒ 三处数字相等、atom 委托、回填不计判据。
- 台账纪律段位：段声明 / 注记照抄上轮计划措辞 ⇒ 本波实态回填、闸门序号去 G 前缀。

## 交给外部审查团队的制品清单（边界声明）

**制品**：本验收物、`docs/handoff-20261126-wave58.md`、`docs/w58-dispatch.md`、`docs/06-deferred-ledger.md`（`### W58 段` + 5 条老号行内注记）、W57 三份文档的订正、`scripts/w55-probe-form-sync.sh` 加固 diff、`ManualCompress.kt` KDoc diff、**入仓证据 `evidence/raw/w57/` 25 件**（逐件见 `MANIFEST.txt`）。三份核心 W58 文档由回填提交加入，按最终 tip（atom 实取）取全树。

**不含**：会话历史 / 凭据（含 key 文件全文、设备侧加密 key）/ 未打码账号字段 / 端点完整 URL / 设备序列号 / SSID / 本机路径 / 用户私人图片 / 整段 logcat / 「建议下一链做什么」。截图均已脱敏（dots3 连接页 Base URL 已打码并二次目视）。

**边界**：① 本波是证据与账实收口波、不推翻 W57 三件实质事，只补回可复核状态；② W57 四态不回改，内核账行 ✅ 加「待入仓复核」注记，入仓后按 MANIFEST 复核；③ A2 形态判据两个盲区加固是判据层、非现役代码漏洞，行为级仍 ⚠️；④ 确定性出包双哈希中「当前包一侧」可独立复核、第 1 轮与旧包哈希为回报补录不可复核，如实标注不编造；⑤ OR 两端点用户已确认不可用，停止占格不推断原因；⑥ 冻结 5 组零解禁，触发信号出现不等于解禁；⑦ 本波止于交接，交外部团队审查。

> 本波止于交接，交由外部团队审查。
