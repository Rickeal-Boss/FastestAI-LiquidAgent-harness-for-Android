# W57 验收物 — A2 实质修复 / 机械自检全库升级 / 多模型真机矩阵

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `1685ec3057142c5ee9c877d09ea0ec3b5a124ff8`（W56 最终 tip，atom 实取）；本波**唯一代码提交** `edf043739cf94049dc375056a9054be23d760d64`；**最终 tip 以 atom 实取为准**（纯文档回填提交不计入交付判据、但计入提交实态，详见步 5）。台账权威版 = `docs/06-deferred-ledger.md`；
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 收尾契约采用「计数 + atom 委托」：本文档只声明本波提交/run 计数，最终 tip 与逐号 run 由 atom / Actions 实取。

## 步 1 · 外审分歧裁决与方向

7 份外审报告逐条裁决（方案席全文 + 主理人回源定谳），核心分歧：

| # | 主张 | 回源/实测 | 裁决 | 四态 |
|---|---|---|---|---|
| 1 | N-1 第 11 次：`parallel_tool_calls` 误拒、三选一补丁 | 现役 `EndpointAdvancedValidation.kt:68` = 完整键在 `ALLOWED_EXTRA_BODY_KEYS`；`EndpointCapabilitiesTest.kt:248` `parallelToolCallsPassesCombinedSaveChain` 已通过；外审查的是旧树 | 不采纳，终止第 12 次空转 | ⛔ 主张证伪 |
| 2 | P1-1 `allowPrivateNetwork` 标「✅回源证伪」=稻草人证伪 | LLM 侧第 4 参已透传（`RemoteLlmEngine`/`HttpTransport:135`）；MCP 侧 `McpModels.kt:25` 字段未接 `McpTransport.post`，审查人从头只主张 MCP 侧；同一文档三处「待修」与一处「✅证伪」并存 | 降级为「⚠️ 口径厘清 + 登记待修」，措辞统一 handoff/验收物，不写功能码（安全波冻结） | ⚠️ |
| 3 | P1-2 机械自检只锚 4 处写点 | `scripts/w55-probe-form-sync.sh` 旧版只读 4 文件；非锚点写点改形状 rc=0 假绿 | 采纳脚本升级（见步 2） | ✅（升级后） |
| 4 | 二次审查：3 条命名常量形态不受保护 | 脚本旧版只匹配连写 `Regex("""…""")`，`REASON_*` 多行写法提取不到 | 并入 #3，全库自动发现覆盖 | ⚠️→✅ |
| 5 | A2 凭据白名单：`redactHeaders` 只拦 Authorization/key | 现役 `McpServerStore.kt:71-78` 逐字确认，Cookie/X-Auth-Token 明文落盘 | 采纳，**本波唯一落地的功能修复**（见步 2） | ✅ 形态 / ⚠️ 运行时 |
| 6 | A4 记忆注入、P1-1 MCP 正向透传 | A4 现役 `AgentPromptComposer.sectionsTyped:148` 已 SYSTEM 段注入（注入面存在），残余「免审批」=安全放宽、须用户单点裁决；P1-1 属安全波整包冻结 | 不落地；转用户单点裁决 / 触发信号 | ⚠️ 口径厘清 + 登记待修 / 冻结 |
| 7 | 冻结 5 组、W35-02、FAILURE_DIGEST 扩样本、跨文件契约表 | 冻结 5 组零触碰；W35-02 台账 `OPEN`（外审第三次查错树）；后两项既有防线已钉住 | 维持冻结 / 不采纳重复建议 | ✅ |

**方向裁决**：用户明确要求不重复「判据升级→全空转→重新取证」的零改动循环。三条候选中仅 A2 满足全部约束——纯 fail-closed 单调收窄、`core-data` 有余量、不碰顶格三对象、不属冻结 5 组、不增 `@Test`（904 天然维持）。A4/P1-1 不落地（理由见上）。

## 步 2 · 代码（第 0 车 + A2）

### 2.1 A2 凭据白名单扩展（唯一功能代码）
`core-data/.../McpServerStore.kt:71-79` `redactHeaders` 脱敏面单调收窄：原仅 `Authorization`/含 `key`，新增 `Cookie`、`X-Auth-Token` 具名头（`equals` ignoreCase）+ 含 `token`/`secret`/`cookie` 泛化词（`contains` ignoreCase），命中一律 `REDACTED`（`***` 常量未改）。不动 `upsert`/`list`、不碰 G15b/G19。**84→90 行**（单文件 cap 400、core-data 模块 2656/4500）。四态：形态判据 ✅ / 运行时行为 ⚠️（见 2.4）。

### 2.2 机械自检全库升级（P1-2）
`scripts/w55-probe-form-sync.sh`（263→702 行实测，脚本无 G10 行数闸门；W57 验收物原写 640、W58 按磁盘实测订正）：
- 保留 18 条 raw 形态提取 + 11 条 NEGATIVE + `render_call` 现场渲染；
- 新增按名解析 3 条命名常量 Regex（`REASON_LINE`/`REASON_CODE_LINE`/`REASON_APPLIED`）；
- `strip_line_comments` 改引号状态机 + 转义，只切字符串外 `//`（H 型假红修复）；
- 两层 POSITIVE：14 条锚点样本（含 `manual_compress=noop`/`auto_compact=noop`/`layered compress` 三类非锚点写点）+ 15 条全库 `os.walk` 认领写点；
- 头注释「不覆盖」补全 4 类诚实边界（3 命名常量 + FAILURE_DIGEST 拼接式）。

变异自证（仓外备份 + 精确替换 + cp 还原，零 git 写命令；每条先确认变异有效）：基线 rc=0；非锚点 `reason=`→`why=` ⇒ rc=1（复刻 `isProbeLine` 落盘 True→False）；注释化/真删正则 ⇒ rc=1；写点与正则同步 `char_budget=`→`char_cap=` ⇒ rc=0（不过拟合）；放宽 `^context compressed.*$` ⇒ rc=1（NEGATIVE 拦下）；正则含 `//` ⇒ rc=0 可编译。全部符合预期。

### 2.3 收尾契约 / 账实订正（第 0 车，零回改历史四态）
- 收尾契约：W56 三份文档统一为 4 提交（1 代码 `bde9054` + 3 回填 `ab02d9b`/`5990e6b`/`1685ec3`）/ 5 条 run 全绿，补 ④ `1685ec3` Secret `45616685625`（updated_at 公开 API 实取 `12:44:54Z`，非推算值），「6 run」→「5 run」，计数 + atom 委托口径；未新建第 5 提交。
- P1-1 四态订正为 ⚠️ 两面厘清 + 登记待修（见步 1#2）。
- 证据订正：自动压缩附带读数标注「设备侧 adb dump、未入仓」、删 `agent-0.log` 仓内指向；派单卡第 1 车实态回填；`char_budget=` 11 处登记 gitleaks 规则升级触发信号（改形状、不加 allowlist）；台账「五条注记」与 6 段位名对齐；g0 措辞改「三路 selftest 汇总」。
- 台账：`### W57 段`（本波销账集合 = 0 行）+ 5 条老号行内注记（`W27-01`/`W12-02`/`W36-01`/`W28-01`/`W9-01`），状态列原样、pipe=7、零新开零销账。**⚠️ 账实订正（W58）：W57 五条注记原文均为 W56 补录、并无 A2 单列（台账全文当时无 `Cookie`/`X-Auth-Token`/`redactHeaders`）；A2 单列注记由 W58 补登在凭据白名单老号行，本波唯一功能修复的台账登记以 W58 为准，不回改 W57 历史四态。**

### 2.4 A2 判据载体 caveat（四态拆分理由）
全仓无 `McpServerStoreTest`、`redactHeaders` 为 private ⇒ A2 判据走脚本静态形态检查（`redactHeaders` 体内须同时含 `equals("Cookie")`/`equals("X-Auth-Token")` 具名头形态，只认具名头、不认 `contains("cookie")` 泛化串——否则删具名头后泛化串仍在会假绿）。三退出码：基线 rc=0 / 删 `equals("Cookie")` ⇒ rc=1 / 还原 rc=0。
**判据只坐实形态层，不证明运行时 header 映射行为**（无行为级用例）⇒ 形态判据 ✅、运行时行为 ⚠️。空命中语义：形态检查不覆盖两类则不得判 ✅。

**⚠️ W58 订正——W57 形态判据的两个盲区（外审变异 K / 莫斯线实测坐实，均非现役代码漏洞）**：① 原判据只认两个具名头字面量在场，**不认 `if (sensitive) REDACTED` 脱敏控制流**——摘除控制流、保留字面量后脚本仍 rc=0 而凭据明文落盘；② 全库认领只按写点首字面量前缀认领，首字面量内字段改名即静默掉出认领集、rc=0 假绿。W58 零新 `@Test` 补法：A2 段增控制流形态认领 + K 必红，全库认领失败禁静默跳过 + 认领基数显式判据 + M 必红；三退出码入档。行为级夹具用例仍转第 1 车。**W57 原四态不回改；上述加固是 W58 内容，判据最终形态与三退出码以 W58 入仓输出为准。**

### 2.5 行数与测试基线
`EXPECTED_TESTS=904`（build.yml 未动），严格 `^\s*@Test\b` 实测 904，双向相等。零新增 G 编号 / CI job / @Test / 挂账号。顶格三对象零触碰（`core-kernel 449/450`、`GenerationRound 400/400`、`feature-chat 3999/4000`）。

## 步 3 · 四闸门（原始输出入仓）

原始输出证据目录（仓内）：`evidence/raw/w57/`（W58 补入仓，脱敏后逐件与制品清单对齐；下表证据列均为仓内文件名）。编译/守卫/单测/门禁原始输出不含凭据，撤到仓外无脱敏理由；真机截图与设备侧探针按历波「脱敏 + 固定形态」惯例入仓。

> 闸门序号口径：下表「闸 1–4」为本波闸门序号（编译 / 架构守卫 / 守卫自测 / 全量单测），**非 G 编号**；roadmap §五权威表中 G5=禁 jitpack、G6=模块边界、G7=设计系统零业务，机械自检 / 台账符号自检 / 脱敏门禁是闸门别称、不带 G 前缀。

| 闸 | 结果 | 四态 | 证据（仓内 `evidence/raw/w57/`） |
|---|---|---|---|
| 闸 1 编译 | rc=0 `BUILD SUCCESSFUL`；确定性出包两次 sha256 `34be7494…` 相等、新 APK mtime `14:43/14:44` 晚于 A2 源码 `14:32:50`，坐实 A2 入包 | ✅ | `g1_det_build1.txt`/`g1_det_build2.txt` |
| 闸 2 架构守卫 | `result=PASS ok=27 vacuous=1 violation=0 fault=0`，G20=141 ID、G21 棘轮 OK | ✅ | `g2_arch_guard.txt` |
| 闸 3 守卫自测 | `30/30 PASS（416s）`，rc=0，含 VACUOUS 机制自证 | ✅ | `g3_selftest.txt` |
| 闸 4 全量单测 | `tests=904 failures=0 errors=0 skipped=0`，14 模块，mtime 新鲜 | ✅ | `g4_fulltest.txt`/`g4_fulltest_rerun.txt` |
| 机械自检 | rc=0（14 锚点 + 15 全库 + 11 反哨兵 + A2 两类齐备） | ✅ | `g5_probe.txt` |
| 台账符号自检 | rc=0（21 类 / 64 方法 / 323 kt） | ✅ | `g6_ledger.txt` |
| 脱敏门禁 | rc=0，三路全 0 命中 | ✅ | `g7_secretgate.txt` |

- **G1 语义边界**：首轮 270 up-to-date 的 BUILD SUCCESSFUL 不能证明 A2 入包（APK mtime 早于 .kt）；经删包重编双哈希坐实后才判 ✅。「编译链无错」≠「目标改动入包」。
- **G4 框架噪声**：首轮后台框架 `Status=failed` 与脚本 `FULLTEST_RC=0` + 完整日志矛盾；串行前台交叉验证（长 timeout）rc=0、904/0/0/0、逐项一致 ⇒ 框架标记定性为工具层噪声，非脚本真实失败。
- **账实分离**：确定性出包产物含 A2；真机设备当前安装包为 A2 前旧包，A2 运行时行为本波未真机复测（MCP 0 服务器 vacuous，运行时行为本就 ⚠️）。

## 步 4 · 真机（多模型矩阵，密钥留设备）

设备先唤醒（Dozing→Awake 三步法）；密钥留设备、只核对末 4 位/哈希（加密存储 `or-north.bin` 无法静态比对，如实标「未能证实一致/不一致」，改请求实测间接验证）；判据通道 = `run-as $PKG cat files/harness/logs/agent-0.log`（非 logcat）。**回灌级别**：只写脱敏判据结论 + 设备侧探针关键行（≤200 字），不回灌整段 logcat / 凭据 / 未打码账号字段 / 序列号 / SSID / 本机路径 / 端点完整 URL。

| 格 | 结论 | 四态 |
|---|---|---|
| 端点可用性 | NV SuperVL 120B 1M / Nano Omni 30B / Lightning 30B 哨兵逐字命中（`PONG-W57-*`）；OR dots-3 / north 均 HTTP404（**用户 W58 已明确两端点现在都不可用**；north 供给方 11-20 起无 provider，按「停止占格、不计入可用模型数」处理，404→400→无provider 仅作事实记录不推断原因） | 3×✅ / 2×⛔（端点失效，非 app bug） |
| 上下文至 1M | 生效窗口 = min(全局档位, 端点 contextLength)；SuperVL(1M)+1M 档 → `1.9K/995.9K`、`PONG-W57-1MFULL` 逐字命中；Lightning(128K)+1M 档 → `1.9K/127K`（封顶）。验证档位/容量切换 + 短哨兵，**未灌满 128K 长历史压测稳定性** | ✅（档位切换） |
| 自动压缩 + 内核账行 | `prefix prompt=100370` → 内核 `context compressed before round 1: 501 -> 277 (estimated 100370 > budget 92928)` → 会话级 `auto_compact dropped=220 kept=283`。原始行带 `2026-11-26T06:45:37.959358Z` 时间戳、相邻行连续、本轮首次出现 ⇒ **内核每轮压缩路径从 ⛔vacuous 升级为 ✅**。正控：32K 档报 `context_overflow_prevented: chars=48365 cap=27904` 熔断未压缩。**⚠️ 待 W58 入仓复核：W57 原始探针仅存设备侧/报告文字转述，`evidence/raw/w57/` 当时 0 件；四态本身不因证据缺位回改，外审按仓内制品复核前维持本注记。W58 已按设备 `run-as` 直读补入仓** | ✅（双写 applied + 正控）/ ⚠️ 待入仓复核 |
| 手动压缩 | 确认框逐字（三段文案 + 取消/压缩按钮）与知识库一致；点压缩后未捕获干净的「前→后读数下降」对比（会话刚经自动压缩、可压余量小），未确认撤销入口 | ⚠️ 部分 |
| 多模态 | 480×240 真图（红圆/绿方/蓝倒三角 + W57），SAF 通道选入，语义逐字回读「red circle / green square / blue downward-pointing triangle」完全一致；`prefix prompt=2109` | ✅ |
| 工作空间文件读写 | `file_read`→「W42OK」逐字一致（免审批）；`file_write` 审批框逐字（工具名/参数/授权记忆文案 + 三按钮），允许一次后落盘 `files/harness/workspace/workspace/w57write.txt` 实读 10 字节 `W57WRITEOK` | ✅（读免审批/写审批+落盘） |
| skill | `notes-organizer` 触发 `skill_list`（6/6），思考与最终答案正确复述工作流；读数 `8.0k in/402 out (4336 cached, 53.6%)` 前缀缓存命中实证 | ✅ |
| MCP | 0 服务器（设置页「还没有 MCP 服务器」），工具调用判据未行使 | ⛔vacuous（非失败） |

## 步 5 · CI（公开 API 四字段，curl 直连实时数据）

提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；PAT 仅 URL inline 一次性使用，remote URL 保持干净、无凭据落盘。

**本波计数：W57 全波 2 提交（① 代码 `edf0437` + ② 回填 `6f5e3528`）/ 3 条 run 全绿**（W56 的 4 提交 / 5 run 是历史订正值，与本波计数分开写、不混同）。唯一代码提交 `edf043739cf94049dc375056a9054be23d760d64`（`edf0437`）；回填提交 `6f5e35281226aa19852edb906b210d9e834dff20`（`6f5e3528`）不计入交付判据但计入提交实态。**最终 tip 以 atom 实取为准**（本波不硬写终态 tip），不为补链新建提交。

| 提交 | run | workflow | status | conclusion | updated_at |
|---|---|---|---|---|---|
| ① 代码 `edf0437` | `45773097967` | Build | completed | **success** | 2026-11-26T07:10:34Z |
| ① 代码 `edf0437` | `45773097970` | Secret scan | completed | **success** | 2026-11-26T07:08:43Z |
| ② 回填 `6f5e3528` | `45774389062` | Secret scan | completed | **success** | 2026-11-26T07:19:23Z |
| ② 回填 `6f5e3528` | — | Build | 未触发（`docs/**` paths-ignore） | — | — |

**交付判据 = 唯一代码提交 `edf0437` 双 run 全绿**：
`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45773097967`（Build success）、
`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45773097970`（Secret success）。
两 run 均 event=push、head_sha=`edf043739cf9…`，与本次提交一致。注：`head_sha` 过滤查询对新 push 偶有 0 结果延迟，已用无过滤 runs 列表交叉确认；WebFetch 15 分钟缓存会返回旧快照，四字段以 curl 直连实时数据为准。

## 明确未做（逐条带中文段位）

多模态以外 SAF 通道恢复后的补充语义格 / 手动压缩干净前后对比与撤销入口（压缩量纲段位）｜工作空间文件读写审批的参数级+会话级「本会话允许」记忆验证（台账纪律段位）｜真机灌满 128K+ 长历史压测窗口稳定性（1M 满窗真压双硬限冻结组）｜MCP 服务器工具调用（0 服务器 vacuous，安全收口段位）｜Dots3/North 供给方核查（404→400→无provider，端点矩阵段位，记真相不推断原因）｜A2 运行时 header 映射行为级用例（无 `McpServerStoreTest`、`redactHeaders` private，凭据白名单段位，运行时行为 ⚠️）｜A2 真机复测（设备装 A2 前旧包，凭据白名单段位）｜A4 免审批 / A5 代次门 / A6 双终态（用户单点裁决段位）｜内核行数 / 压缩量纲余项 / 安全波整包 / 1M 满窗双硬限 / journal 二选一（冻结 5 组，触发信号出现只登记提请裁决）｜冻结项长远裁决书 M1-M4/R1-R5/L1-L6 落笔（用户未逐字面裁决，不代裁）｜凭据轮换（用户侧）｜历史缺口不补造。

## OPEN 挂账（触发信号 + 缓解动作 + 中文段位；零新开零销账）

逐条见台账权威版 `docs/06-deferred-ledger.md` 的 `### W57 段` 与五条老号行内注记。本波零新开零销账；状态列零变化。关键触发信号：
- 收尾契约段位：外审按最终 tip 复核 run 四字段 ⇒ 三处数字相等、atom 委托、回填不计判据。
- 落盘最小化段位：任一生产写点改形状（含非锚点）⇒ 全库形态自检停红即阻断；新增拼接式常量须手工登记。
- 凭据白名单段位：gitleaks 规则升级把 `char_budget=` 等样本判红 ⇒ 改形状、不加 allowlist/`--exclude`；A2 形态检查不覆盖两类具名头 ⇒ 不得判 ✅。
- 压缩量纲段位：`retry:` 帧透传 / 手动压缩前后对比可复现 ⇒ 条件触发专题波。
- 端点矩阵段位：Dots3/North 供给恢复或再次漂移 ⇒ 记设备真相、停止占格、提请用户侧核查。

## 交给外部审查团队的制品清单（边界声明）

**制品**：本验收物、`docs/handoff-20261126-wave57.md`、`docs/w57-dispatch.md`、`docs/06-deferred-ledger.md`（`### W57 段` + 五条老号行内注记）、W56 三份文档的订正、`core-data/.../McpServerStore.kt` 的 A2 diff、`scripts/w55-probe-form-sync.sh` 升级 diff、**入仓证据 `evidence/raw/w57/`**（W58 补入：闸 1–4 原始输出 + 机械自检 / 台账符号 / 脱敏门禁输出 + 确定性出包双哈希日志 + 变异自证输出 + 设备侧固定形态探针 + 脱敏截图，逐件见 `evidence/raw/w57/MANIFEST.txt`）。上述三份核心 W57 文档由回填提交 `6f5e3528` 加入（代码提交 `edf0437` 不含），按最终 tip（atom 实取）取全树。

**不含**：会话历史 / 凭据（含 key 文件全文、设备侧加密 key）/ 未打码账号字段 / 端点完整 URL / 设备序列号 / SSID / 本机路径 / 用户私人图片 / 整段 logcat / 截图二进制 /「建议下一链做什么」。

**边界**：① N-1 外审主张证伪（完整键 + 通过用例在现役树），不重复第 12 次空转；② A2 只坐实形态层、运行时行为 ⚠️，不冒充端到端验证；③ 机械自检全库升级只防「已登记写点被改形状 / 正则被注释删除」，防不了「全新写点从未登记」；④ 收尾契约用 atom 委托终止递归、最终 tip 以 atom 实取为准；⑤ 内核每轮压缩账行本轮首次真机坐实（从 vacuous 升 ✅），与会话级 auto_compact 是两套机制、不混同；⑥ OR 两免费端点 HTTP404 与 north 供给方下线是设备/供给真相，用户 W58 已确认两端点现在都不可用，不推断原因、不计入可用模型数；⑦ 确定性出包产物含 A2、设备安装包为旧包，A2 运行时未真机复测；⑧ 手动压缩 ⚠️、MCP ⛔vacuous、1M 未灌满压测均如实标注；⑨ 冻结 5 组零解禁；⑩ **W57 四态升级曾建在仓外证据上（P0），W58 已脱敏补入仓，入仓前给内核账行 ✅ 加「⚠️ 待入仓复核」注记、四态本身不回改**；⑪ A2 形态判据的两个盲区（控制流摘除 / 认领静默逃逸）由 W58 加固，非现役代码漏洞；⑫ 本波止于交接，交外部团队审查。

> 本波止于交接，交由外部团队审查。
