# W22 收尾契约验收物 — 四份外审裁决 + 多模型×多上下文×多功能面真机矩阵（2026-11-16）

> 基线 `7760632`（W21，`main`；本地与远端 HEAD 一致，零 `.kt` 改动）。
> 上游依据：用户 2026-11-16 指令（读记忆 / 上一波 handoff / OPEN 挂账行〔现役仓 `harness-v2/`，
> **非旧仓** `Android-Agent/`〕，列出「做什么 / 明确不做」写入验收物 §1；读四份众审查人原始报告并
> **裁决分歧、不改方案大体方向**；新 PAT 换发；多支免费节点模型 × 不同上下文〔直至 1M〕×
> 多模态输入 / 工作空间文件读写 / skill / MCP / 手动与自动上下文压缩；先本地 UI + 功能测试 +
> adb 调试 → 真机 → 推仓库及 CI 构建；密钥留真机、零凭据字面量；真机 adb + 脱敏截图回灌 +
> 边测边改链内循环；每次推送留可审计 commit 与 run 链接；不 force-push、不 rewrite history；
> 触顶收尾；handoff 不把推断写成事实；本次任务停于交接、交外部团队审查）。
> 判定四态 ✅ / ⚠️ / ⛔ / ⛔vacuous。**「未触发」≠「已验证通过」；「测试全绿」≠「口径被验证」；
> `rc=0` ≠ 产物已重生成；PC 侧结论不得写成设备侧可用；档位读数 ≠ 生效窗口 ≠ 1M 真压；
> 写「X 未发生」前先自证判据会响。**
> 密钥纪律：端点凭据**只在设备侧**；本单 / 工具 schema / 工具结果 / 日志 / 截图 / 交接 / 记忆写入
> **零凭据字面量**；端点一律以不可逆 8 位指纹出现（指纹 = UI 敏感节点原始串 sha256 前 8 位）。
> 设备序列号、本机绝对路径、凭据文件名、未打码账号字段一律不出现。
> 完整版即本文件；`docs/handoff-20261116-wave22.md` 只做交接摘要，冲突时以本文件为准。
> 本文件不写「建议下一链做什么」。
> 🔴 编号纪律：**交接文档**（`handoff-*.md`）严格零「W 加数字加减号加数字」复合编号字面量——
> G20 守卫只用 `find docs -maxdepth 1 -name handoff-*.md` 扫描交接文档（`arch-guard.sh:1334/1342`），
> **不扫 `docs/analysis/` 验收物**。本文件 §1.3 等处的挂账号是**台账溯源**（25 个号已逐核全在台账真值源），
> 合规且必要；故本文件不追求零复合编号，交接摘要侧才用中文段位表述（「上一波段第 1 行」）。

---

## 1. 本波范围：做什么 / 明确不做（执行前钉死）

### 1.1 四份众审查人报告分歧裁决（不改方案大体方向）

输入（四份原始报告，均为本单用户附）：报告①《W21 复合波深度审查与 W22 排期裁决》、
报告②《W18–W21 审查与挂账分析》、报告③《FastestAI-LiquidAgent-harness W20–21 深度审查与
W22 路线建议》、报告④《harness-w21-review》。逐条裁决如下，**大体方向 = 用户指令面优先
（沿用九波同款矩阵指令，`w17-acceptance.md:98`）不变**；裁决四值 = 采纳订正 / 顺延留痕 /
证伪 / 部分采纳。每条经现役树回源（采纳处方前先 grep，报告查的是它那一版）。

| # | 议题 | 四报告主张 | 现役回源结果 | 裁决 | 改方向 |
|---|---|---|---|---|---|
| 1 | 排期路线 | 报告②决策清点先于代码；报告③矩阵近枯竭、交映射表、应含 `.kt`；报告④ A/B/C 三前置 | 用户本单继续给新节点与凭据、明确要矩阵测试；`w17-acceptance.md:98` 指令面优先出处成立 | 顺延留痕：三报告排期主张整包顺延，不取消不推翻不代做；矩阵存续决策包维持台账原样 `OPEN` | 否 |
| 2 | 收口第三车（零 `.kt` 账实/制品收口） | 报告① W22 先做零 `.kt` 收口第三车、出口判据写死 | 面数三态不自洽、销账字面不成立均现役复算成立，是上一波自己新出的缺陷；修复上一波收尾缺陷=收尾义务完成 | 部分采纳：P0/P1 修复作为并行收尾义务并入本波（只修上一波本体 + 仓外制品生成器）；**拒绝升格独立排期波、拒绝写死出口判据**（派生数一律实跑口径） | 否 |
| 3 | journal 顺延次数与代数口径 | 三报告均记第 16 次、两代数口径并列 | 台账现役第 1 行（承接链）= 第 16 次；本波登记即第 17 次 | 顺延留痕 + 代数口径不代裁：本波段登记第 17 次；两口径交用户拍板，老行不改状态不并号 | 否 |
| 4 | ≥3 端点补给责任 | 报告③撤回「同窗不可达」判词：原始免费池不可达、可达条件是用户持续补给节点 | 上一波验收物实跑 4 支收敛（3 新 + 1 复验），用户本单再给新节点 | 采纳订正：维持「同波 ≥3」口径；把节点补给责任在用户侧写进本波前提；不采「无补给即免测」软化 | 否 |
| 5 | 并行键行为（报告② N-1 第三次陈述） | 报告②称白名单含笔误键、不含完整键 ⇒ 保存链必拒合法键、+1 行测试终结 | `EndpointAdvancedValidation.kt:68` 就是完整键 `"parallel_tool_calls"`，`:114` 放行；`EndpointCapabilitiesTest.kt:255-270` 已有 allowlist 强制行为测试；完整键自初始即在。**其 §2.2 自列「:68 是完整键」恰反证 §四「集合不含完整键」前提** | 证伪 + 口径订正、不新增门禁：拒绝修不存在的 bug；不新增守卫、不改白名单；「+1 行测试」属 `.kt` 改动本波不做，登记后续 | 否 |
| 6 | A/B/C 三前置（报告④） | 报告④ journal 波 / 拆分波 / 矩阵终局；剩余格全依赖 A/B；G19 摘 VACUOUS、`@Test≥850`、工具记账 | G10 余量 4/5/4、`ChatViewModel` 500/500、`EXPECTED_TESTS='818'`、G19 守空集既定 ⛔vacuous（均现役核） | 顺延留痕：A/B/C 整包顺延；需 `.kt`/改守卫/改测试数的项列 §1.3 明确不做；skill 本波按资产注入复验维持判据 | 否 |
| 7 | 制品面数断言（报告① P0-1） | 第 10 面「内核级 L1 硬闸」不在 declared、零机器可读证据，枚举断言拦不住新增面 | 制品 declared 9 面 / 覆盖 9/9 成立，但上一波 §3.2 表 10 行、汇总 5+1+3=9、§5.1 步 3 同错——三态不自洽属实 | 采纳订正（九面方案）：L1 硬闸为压缩面子项移入说明段、明写「压缩面子项不单独计面」，五处同步；仓外生成器补「新增面须登记 declared」断言 + 必红变异自证 | 否 |
| 8 | 销账行自证（报告① P0-2） | 首个 `CLOSED` 行出口判据「grep 全树 0 命中」按字面不成立 | 三条串命中在（注记引用原句 / 判据名自指 / 历史背景语境）；缺陷形态 `G20\s*=\s*112` = 0 命中为真 | 采纳订正、维持 `CLOSED`：判据改「缺陷形态 0 命中 + 排除规则 + 语境分类日志入仓」，立为销账行自证范本规则 | 否 |
| 9 | 手动压缩夹具几何（报告④ P1 / 报告③） | 512K 窗下全装得下⇒无损放弃；32K 被 L1 拦⇒死结；修补=32K 新会话逐条短消息或 128K 中夹具 | `ManualCompress.kt` 候选≥2 才压、`SettledCompressPolicy.kt` 触发/节省常量现役成立 | 采纳方向为矩阵前置：本波用 32K + 新会话逐条短消息夹具行使 `dropped>0`；**不把 `dropped>0` 写成硬出口判据**，夹具/节点不配合即如实四态 | 否 |
| 10 | G20 别名机制（报告② vs 报告③） | 报告② G20 增 `CARRIED` 别名止血自增殖；报告③并号紧迫性下降 | 现役守卫状态仅三态、无别名；同债组已停增（上一波段仅 6 行） | 顺延留痕、取保守值：别名需改守卫+selftest（守卫波域），本波不做；采「紧迫性下降」不单独排期并号；新段只登记真实新增 | 否 |
| 11 | 推送链接/时序/边界/错数（报告① P1） | 推送 3 自身 run「不算缺陷」但跨提交可补；凭据句时点倒置；§1.4 边界不自洽；行数/断言数错数 | 上一波验收物现役可核全属实 | 采纳订正：一次纯文档提交补推送 3 run 链接、删「不算缺陷」；凭据句加后补时点标注；边界同步实际 diff；错数订正——全部零 `.kt` | 否 |
| 12 | N2 自纠撤回（报告②） | 报告②主动撤回 N2（复制/显示口径分裂），行号即断言是误报根因 | 与现役「grep/diff 须核函数归属」纪律一致 | 采纳自纠：不恢复 N2；作为教训入档 | 否 |
| 13 | 多模态/推断定级（报告④ + 报告① P1-6） | 多模态 ⛔ 行使后失败（环境侧）、skill 首探 vacuous 不冒充、file_list ⚠️ 无法归因 | 上一波验收物四态如实、推断带 caveat 现役可核 | 采纳并从严：本波 IMAGE 端点仍抖动即如实 ⛔（环境侧）不升级应用 bug；推断结论一律带 caveat，不写裸 `pass` | 否 |
| 14 | 报告④出口判据草案 | journal 两判据 + G19 摘 VACUOUS、`dropped>0` 至少 1 格、`@Test≥850`、skill_load 实锤、净 OPEN 下降 | `@Test` 818、G19 守空集、工具记账缺失现役成立 | 部分采纳 + 顺延：能零 `.kt` 行使的纳入；需 `.kt` 的整包顺延、不作本波出口判据 | 否 |

> 裁决合计 14 条：采纳订正 7 / 顺延留痕 4 / 证伪 1 / 部分采纳 2。**四报告排期主张整包顺延留痕，
> 不取消不推翻；四项用户终局裁决（journal 二选一 / ≥3 端点终局 / §1.5 占位符口径 / P2-3）只留痕不代做。**
> 本波最硬裁决 = 报告② N-1 现役证伪：其「现存 bug」前提在现役树上不成立（完整键自初始即在、
> allowlist 行为测试已在），且其 §2.2 自列依据恰好推翻 §四结论而未被自身察觉——「采纳外审处方前
> 先 grep 现役树」铁律本波再 +1。

### 1.2 做什么（本波判据 · 12 项）

| # | 判据 | 行使方式 | 出处 |
|---|---|---|---|
| 1 | 范围 §1：四报告逐条裁决 + 做什么/不做 | §1.1 裁决表 + §1.2/§1.3 写入本验收物第 1 节；台账号转中文段位 | 用户指令 + 四外审 |
| 2 | 端点轴新节点（dots / north / NVIDIA 各模型） | 设备侧 fast-fail 预筛后真收敛取证；指纹 UI 串 sha256 前 8 位；走密钥复用双路径 | 用户指令 + 报告①③④ |
| 3 | 档位轴直至 1M（读数，非真压） | 档位真切换回读 `switch_verified=true`；1M 只取读数；读数≠窗口≠真压 | 用户指令 + 报告③ |
| 4 | 多模态输入 | 真图 + scan_file 触发媒库 + SAF 选图 chip + 语义问答；换可用 IMAGE 端点 | 用户指令 + 报告④ |
| 5 | 工作空间文件读写 | `file_write`/`file_read` 严格 `ls -1` 差集 + ns mtime + 内容逐字 + 审批框 + 反背题对照 | 用户指令 + 报告④ |
| 6 | skill 功能面 | 设备侧 `skills/` 资产注入 → 冷启动「来源：file」→ `@name` 点选 → sentinel 逐字 + 横幅 + usage 增量 | 用户指令 + 报告④ |
| 7 | MCP 现状确认 | 顶栏返回箭头绕系统 BACK → MCP 列表页可达 + 空态截图；导航/行使分开落账 | 用户指令 + 报告③ |
| 8 | 手动 + 自动上下文压缩 | 32K + 新会话逐条短消息夹具；手动 `dropped>0` 双向断言、自动 `applied=true+dropped>0`；评估链实锤保留但不冒充达标 | 用户指令 + 报告④ P1 |
| 9 | 本地先行 + adb 调试 + 链内迭代 | 先本地 UI/功能测试 + adb 调试；真机「改→编译→真机→再看」链内循环；每轮追加一行证据 | 用户指令 |
| 10 | 收尾契约：零 `.kt` 账实/制品收口 | 修上一波本体 P0-1（面数九面、五处同步）/ P0-2（销账自证+排除规则）/ P1；仓外生成器补断言 + 必红变异 | 报告①第 0 车 + 收尾义务先例 |
| 11 | 四闸门 + 推送 + CI | G1–G4 串行实跑后推送；每次 commit + run 链接；凭据 inline 一次性、用后清 remote | 用户指令 + AGENTS.md 七/八 |
| 12 | 脱敏 + 零凭据 + 零复合编号 + 四态纪律（全局） | 端点 8 位指纹；产物零 PAT/真 key/本机路径/设备序列号/未打码账号；零复合编号字面量；每次落盘前后各扫一遍 | 用户指令 + 守卫自指陷阱 |

### 1.3 明确不做（逐条带挂账号，全部原样 `OPEN`）

| # | 不做项 | 挂账号 |
|---|---|---|
| 1 | 拆分三方整包 / 任何 `.kt` 增量 / 调行数与测试数上限 | W20-02（承接 W16-03） |
| 2 | journal / FreezeClock 二选一代裁决（本波段登记第 17 次顺延，两代数口径不统一） | W21-03 → W20-01 → W9-05 |
| 3 | 设备侧 1M 真压（双阻断未解，只做 1M 档位读数） | W20-05 → W9-11 |
| 4 | MCP 明文联调 / 放开明文流量开关 / https 夹具 | W20-06 → W5-03 |
| 5 | git 历史清理 / force-push / rewrite history | W20-07 |
| 6 | 四项用户终局裁决代做（journal / ≥3 端点终局 / §1.5 占位符 / P2-3） | W20-08 |
| 7 | 安全波整包 | W20-04 |
| 8 | 台账同物多名并号 / 台账模板改动（采「紧迫性下降」，不单独排期） | W20-10、W20-16 |
| 9 | G20 守卫侧修复 / 扫描面扩展 / G20 别名机制 | W20-17、W20-03 |
| 10 | 取证设施（驱动/脱敏脚本）入仓——本波断言补丁只落仓外 | W20-14 |
| 11 | 加长 system / 音频 / `core-device` 独立波 / 图片预算四条 | W20-11 |
| 12 | 非对话模型（视频生成 / 视频超分）进 chat 端点矩阵 | W21-05 |
| 13 | PC 侧免费节点结论升级为「设备侧可用」 | W20-12 |
| 14 | 上一波制品正文重出 / 历史波正文改写（本波只修上一波本体 + 仓外生成器） | W21-02 → W20-15 |
| 15 | G19 摘 ⛔vacuous / 观测缝 / 工具记账上线（需 `.kt`） | W21-06、W20-03 |
| 16 | `@Test 818→≥850` / 改测试数上限（补测触计数） | W20-09、W20-02 |
| 17 | N-1 白名单行为面「修复」——现役完整键已在、测试已在，拒绝对不存在的 bug 动刀 | W20-09（缺陷清单域，仅登记不修） |
| 18 | 把手动压缩 `dropped>0` / 自动压缩 `applied=true` 写成硬出口判据或达标 ✅ | W21-04 |

### 1.4 历史正文与制品修复边界（一次钉死）

- **上一波验收物**：允许最小结构修复（面数九面五处同步、销账自证措辞、推送链接时点、错数订正），
  每处标注「W22 收口修订」；其余正文不动。
- **上一波制品 JSON**：不重出、不覆盖；缺陷以台账注记登记，修复落在本波新制品与断言升级上。
- **更早各波正文与制品**：一律不动。
- **台账**：只新增本波段行 + 在被点名老行行内追加注记（不改状态、不改原句）。

---

## 2. 本地闸门（实跑回填）

> 四闸门 G1–G4 由 CI 席**串行**实跑（基线 `7760632`，工作树零 `.kt`）。证据路径均为仓外
> `_w22/gates_g*.log`（原始）+ `gates_g*.safe.log`（脱敏副本）。派生计数不写死；`@Test` 基线 818。

| 闸门 | 实跑结果 | 四态 | 证据路径 |
|---|---|---|---|
| G1 出包 | 旧 APK `python os.remove` 删后 RUN A / RUN B 两次强制重出包：`bytes=68407402`（两次相等）、`sha256=ca87a70a…`（两次相等）、mtime 两次不同 ⇒ deterministic；bytes 与上一波基线一致 = 零 `.kt` 预期 | ✅ | `_w22/gates_g1.safe.log`（含 runa/runb 合并 + 删包证据 + 指纹） |
| G2 fulltest | `tests=818 failures=0 errors=0 skipped=0`；严格 `@Test` 818 ↔ `build.yml` 对账双向相等；83 个 `TEST-*.xml` 逐模块之和 818、覆盖 14/14 模块、XML mtime 全本轮；无句柄锁 | ✅ | `_w22/gates_g2.safe.log` |
| G3 arch-guard | `result=PASS ok=27 vacuous=1 violation=0 fault=0`、rc=0；唯一 ⛔vacuous（G19 既定空转守卫）；G20 `PASS`（ID 数实跑输出为准）；G10 行数闸 OK（1 条已登记例外，三模块余量 4/4/5 不变）；G16/G21 基线核对 OK | ✅（含 1 条 ⛔vacuous） | `_w22/gates_g3.safe.log`、`gates_g3_verbose.safe.log`、`gates_g3_final.safe.log` |
| G4 selftest | `selftest: 30/30 PASS (282s)`；28 变异自证 case + 两条 VACUOUS 机制自证全过，每例断言输出可见注入证据 | ✅ | `_w22/gates_g4.safe.log` |

- 最终树守卫复跑（四闸门后）：`result=PASS ok=27 vacuous=1 violation=0 fault=0`，与 G3 一致。
- 编译失败四分类：本波零 `.kt` ⇒ 四分类（参数错 / 工具名错 / 该调不调 / 调了不收敛）**未触发**；两次 assembleDebug 均 BUILD SUCCESSFUL。
- 脱敏自检：检测器 positive control 10/10 规则全响（曾发现 2 条反斜杠规则因转义层级被吞，已用 `chr(92)` 修正后复核）——「写 0 替换前先自证判据会响」纪律落实；8 份 `.safe.log` 经独立纯子串复扫全 CLEAN，关键证据串完整保留。
- 环境：代理端口本会话现取 3328（未写死）；全程未 stash / reset --hard；git 树未变（tip `7760632`、零 `.kt`、仅两份既有未跟踪日析）。

---

## 3. 真机功能矩阵（四态 + 证据）

> 证据 = `_w22/evidence/matrix_facts.jsonl`（16 行：15 格 + E4b 拆分格，全部经出厂断言）+
> `switch_facts.jsonl`（4 行切换）+ `compress_facts.jsonl`（2 行压缩）+ `iterations.jsonl`（12 行）+
> `_w22/shots/*_safe.png`（80 张回灌脱敏截图，原始 `*.png` 仅本地）+ `evidence/w22-matrix-summary.json`
> （仓内脱敏机器可读制品，推送前生成）。
> 端点一律 8 位 name-fp（sha256 显示名前 8 位，与上一波全匹配）；chat-fp 按 baseUrl 级
> （同 baseUrl 不同 modelId 同值，与收敛集重叠属预期，非指纹失效）。端点切换定位用「行 name-fp +
> 当前徽章」，弃 idx 索引（上一波 idx↔行漂移实摔）。四态如实：⛔ 与 ⛔vacuous 写明「行使后失败」
> 或「判据未行使」；免费节点限流/无响应/静默一律归环境侧、非四分类代码缺陷。

### 3.1 逐格四态（15 格 / 16 行含 E4b 拆分）

| 格 | 端点 name-fp | 公开 modelId | 档位 | 功能面 | 四态 | 关键判据 / usage | 证据 |
|---|---|---|---|---|---|---|---|
| E1 | `b995dc45` | dots-3-note-preview:free | 32K | 收敛复验 | ✅ | reply=PONG4；1.6k in/32 out；上下文 1.7K/27.9K | `shots/it1_converged_safe.png`、`it1_tier32_done_safe.png` |
| E2 | `b0113b2c` | north-mini-code:free | 32K | 收敛复验 | ✅ | 1.2k in/508 out；agent log 新行 | `shots/it2_north_resp_safe.png`、`it2_north_setcurrent_safe.png` |
| E3 | `a9ae3f9e` | step-5-preview | 32K | 收敛复验 | ✅ | 1.8k in/43 out；agent log 新行 | `shots/it3_step_resp_safe.png` |
| E4 | `d5022055` | mistral-large-4-1t-a52b | 32K | fast-fail+收敛 | ✅ | 测试连接 4813ms + 真 chat reply 逐字 OK；1.6k in/32 out | `shots/it4_mistral_chat_safe.png`、`it4_mistral_probe_safe.png` |
| E4b | NA | deepseek-v4.1-flash | 32K | fast-fail | ⛔vacuous | 设备 6 端点池无此端点，无法设当前，判据未行使 | 设备 endpoints.json 清单（已脱敏核） |
| E5 | `cfd5fbda` | gemma-4-26b-a4b-it:free | 32K | 不可用复证 | ⛔ | 测试连接失败 `RATE_LIMIT (HTTP 429)`，环境侧 | `shots/it4_gemma_probe_safe.png` |
| T1 | `b995dc45` | dots | 512000 | 切换回读+收敛 | ✅ | 回读「当前 512K」+ 已选；switch_verified | `shots/it5_512k_done_safe.png` |
| T2 | `b995dc45` | dots | 256000 | 切换回读+收敛 | ✅ | 回读「当前 256K」+ 已选 | `shots/it5_256k_done_safe.png` |
| T3 | `01e476bd` | nemotron-3.5-super-vl-120b-a12b | 1048576 | 1M 档位读数 | ✅ | 回读「当前 1M」；计量条「上下文 2K/995.9K」；收敛 reply=HI | `shots/it6_1m_done_safe.png`、`it6_1m_resp_safe.png` |
| F1 | `d5022055` | mistral-large-4-1t-a52b | 1M(读数) | 多模态语义 | ✅ | 全链 scan_file + MediaStore images 命中 + SAF chip + 语义逐字命中「红圆/绿矩/蓝底」；1.7k in/131 out（1600 cached 90.8%） | `shots/it7_selected_safe.png`、`it7_picker_safe.png`、`it7_multimodal_resp_safe.png` |
| F2 | `b995dc45` | dots | 1M(读数) | workspace write/read | ✅ | 文件系统级：ls 差集+1 / ns mtime / 内容逐字 sentinel / 审批框 exact args / 回读逐字 | `shots/it8_write_sent_safe.png`、`it8_read_retry_safe.png`、`f2_baseline.txt`、`f2_after.txt` |
| F3 | `b995dc45` | dots | 1M(读数) | workspace list 计数 | ⚠️ | file_list 列 20 文件+1 目录，模型答 20=fs 真值；无工具记账日志，证据等级=回复内容级 | `shots/it8_list_safe.png` |
| F4 | `b995dc45` | dots | 1M(读数) | skill 端到端 | ✅ | 新注入 file-based skill（305B front-matter）冷启「来源:file」+ @点选 + `[SKILL BODY]` 横幅 + sentinel 逐字；1.8k in/62 out | `shots/it9_skills_cold_safe.png`、`it9_skill_selected_safe.png`、`it9_skill_result_safe.png` |
| F5 | `b995dc45` | dots | 32K | 手动压缩 dropped>0 | ⛔vacuous | 菜单出现+确认+执行三判据全行使，但返回「没有可压缩的内容：历史本来就装得下，或找不到安全的截断点」；无新 manual_compress 行 | `shots/it8_compress_result_safe.png`、`it8_compress_attempt3_safe.png`、`it8_agent_tail.safe.log` |
| F6 | `b995dc45` | dots | 32K | 自动压缩实锤 | ⚠️ | 本波会话 auto_compact 全 below_trigger，未越 `COMPACT_TRIGGER_CHARS=50227`；无 `applied=true+dropped>0`；历史 `insufficient_savings:net=-256` 仅评估链实锤 | `it8_agent_tail.safe.log`、`shots/it8_fixture_ready_safe.png` |
| F7 | NA | NA（无外部 MCP 端点） | NA | MCP 现状 | ⛔vacuous | 导航✅：列表页可达+空态「还没有 MCP 服务器」截图；行使⛔vacuous：无外部端点 + G15b 明文阻断不放开 | `shots/it10_mcp_page_safe.png` |

> **汇总（16 行口径）：✅10 / ⚠️2（F3/F6）/ ⛔1（E5 环境侧）/ ⛔vacuous3（E4b/F5/F7）**；
> E4+E4b 合并计一格时 = 15 功能格口径，两种口径四态合计一致、仅 E4b 拆分与否之别。本波四大实质增量：① **F1 多模态从上一波 ⛔ 转 ✅**——
> NV-VL 虽名含 VL 但设备端点 `inputModalities=['TEXT']`（图片 chip 灰），唯一 IMAGE 端点 mistral
> `d5022055` 本波复活，换它后全链行使+语义逐字命中；② **E4 mistral 节点复活**（上一波
> `node_no_response`→本波测试连接 4813ms+真 chat 收敛，单窗口样本不作稳定性承诺）；③ F4 skill
> 新注入资产端到端 ✅；④ F2 工作空间文件系统级证据。判据前置纪律执行：真图 scan_file+MediaStore
> 命中先行、skill 资产格式先回源、压缩夹具先测触发线。

### 3.2 未达格与归因（逐条）

- **E4b deepseek ⛔vacuous**：设备 6 端点池无 deepseek 条目，无法设当前，判据未行使；上一波 420s 超时结论不臆造为本波 pass。前置缺失，环境类。
- **E5 gemma ⛔**：测试连接 `RATE_LIMIT (HTTP 429)`，节点限流环境侧，复证即止，不归因应用代码。
- **F3 workspace list ⚠️**：计数匹配 fs 真值，但现役无工具记账日志（观测缝需 `.kt` 本波禁改），证据等级止于「回复内容级」，不写裸 ✅。
- **F5 手动压缩 ⛔vacuous**：压缩菜单出现（气泡>4）、二次确认、执行三判据全行使，但返回「没有可压缩的内容」。做过多轮夹具（6 条短问答 / ASCII chunk / CJK 单读 / CJK 双读，计量条 22.8K/27.9K「上下文将满」）。根因 = `file_read` 16000 字符上限 truncate 每个工具结果气泡 ⇒ 候选段 `candidateAll` 压缩后 size 不减 ⇒ 无损放弃；32K 软预算与 L1 硬闸几何夹紧。**能力本身存在**（历史 agent log 有 `manual_compress dropped=238 kept=64`），本波夹具未达判据，非应用 bug。修 `alreadyCompressed` 单指针 / `:407` 时序属 `.kt`，本波禁改，挂账。
- **F6 自动压缩 ⚠️**：本波会话所有 `auto_compact` 行均 `noop reason=below_trigger`，committed 未越触发线；历史 `insufficient_savings:net=-256`（3 行）与 `already_compressed`（1 行）是评估链曾到触发线的实锤，但**不是真压缩达标**，无 `applied=true+dropped>0`。严禁写 ✅，挂账。
- **F7 MCP ⛔vacuous**：导航判据（页面可达+空态截图 = ✅）与真实行使判据（无外部端点 + G15b 明文阻断不放开 = ⛔vacuous）分开落账，环境类不归因代码。

### 3.3 档位轴与端点轴分开判定

- **端点轴**：以「新 modelId 真往返收敛」为准（§3.1）：E1/E2/E3/E4 四支收敛（dots / north / step5 三支 + mistral 复活），E5 gemma 限流 ⛔、E4b deepseek 前置缺失 ⛔vacuous。密钥复用双路径回源成立（编辑留空保留 + 信封 id 键控），零搬运、零明文落盘。
- **档位轴**：512K / 256K / 1M 三档真切换回读（T1/T2/T3，`switch_verified=true`）；1M 只取**读数**（回读「当前 1M」+ 计量条 995.9K），**档位读数 ≠ 生效窗口 ≠ 1M 真压**（真压双阻断未解，挂账）；`hard=` logcat 指纹本波未捕到，如实登记缺失；「≥3 档位组合」口径归终局裁决域，只取证不裁决。
- 两轴不互相冒充；不写「用户指令『直至 1M』达成」这类合并表述。

---

## 4. 链内迭代记录（每轮追加一行）

> 证据 = `_w22/evidence/iterations.jsonl`（12 行，iter 0–11 连续无跳号，出厂断言实测通过）+ 逐格事实与截图。
> 本波零 `.kt` ⇒ 每轮「未触发本波编译」；迭代预算 12 轮（主理人钉的执行值），**12/12 用满即收尾**。
> 每轮 7 字段齐全（action/observation/state/attribution/evidence_ref/caveat）。

| 轮 | 类型 / 动作 | 观察 | 四态 |
|---|---|---|---|
| 0 | 本地先行 + 环境/夹具核对 + 指纹方案验证 | 唤醒设备验非全黑；device tail 落盘；16 格夹具前置逐项核对 | pass |
| 1 | E1 dots 32K 收敛复验 | 上文档位切 32K + 确认框 + 新会话收敛 reply=PONG4；1.6k in/32 out | pass |
| 2 | E2 north + E3 step5 端点轴复验 | 端点管理页行 name-fp 定位设当前；两支真收敛，agent log 新行 | pass |
| 3 | E4/E5 fast-fail 预筛 | mistral 测试连接 4813ms + 真 chat 逐字 OK（复活）；gemma HTTP429 限流；deepseek 设备池无条目 | partial |
| 4 | T1/T2/T3 档位轴切换回读 + 1M 读数 | 512K/256K/1M 三档确认框 + 回读「当前 N」；1M 计量条 995.9K + 收敛 | pass |
| 5 | F1 多模态全链 | 发现 NV-VL 输入模态仅 TEXT，换 IMAGE 端点 mistral；真图 scan_file + MediaStore 命中 + SAF chip + 语义逐字命中 | pass |
| 6 | F2/F3 workspace write/read/list | ls 差集 + ns mtime + 内容逐字 + 审批框 exact args；list 计数匹配但无记账 | pass |
| 7 | F4 skill 端到端 | 新注入 305B file-based skill 冷启「来源:file」+ @点选 + `[SKILL BODY]` + sentinel 逐字 | pass |
| 8 | F5 手动压缩 dropped>0 | 多夹具行使（短问答/ASCII/CJK 单读双读），菜单出现+确认+执行，均「无可压缩内容」 | vacuous |
| 9 | F6 自动压缩评估链回读 | 本波 auto_compact 全 below_trigger，未越触发线；历史 insufficient_savings 实锤保留 | partial |
| 10 | F7 MCP 现状 | 顶栏返回箭头导航到 MCP 列表页 + 空态截图；无外部端点行使未达 | vacuous |
| 11 | 收尾：覆盖核对 + 脱敏自检 + JSONL 校验 | 16 格/12 轮覆盖核对；80 张 safe 截图遮蔽机制自证；四份 JSONL 合法解析 | pass |

> 编号连续性：iter 0–11 无跳号（出厂断言实测通过）；环境类 ⛔（iter3 gemma/deepseek、iter8 夹具未达、
> iter10 无外部 MCP）逐格如实登记，不冒充通过、不归因应用代码。

---

## 5. 收尾契约

### 5.1 1→5 各步结果（四态 + 证据路径）

| 步 | 结果 | 四态 | 证据路径 |
|---|---|---|---|
| 1 §1 本次任务范围 | 四份外审分歧逐条裁决完成（§1.1 十四条：采纳订正 7 / 顺延留痕 4 / 证伪 1 / 部分采纳 2）；做什么 12 项 / 明确不做 18 条逐条带挂账号；修复边界一次钉死（§1.4）。最硬裁决 = 报告② N-1 现役证伪（完整键自初始即在、allowlist 行为测试已在，其 §2.2 自列依据反证 §四结论） | ✅ | 本文件 §1.1–§1.4；台账本波段；`_w22/phase1_solution.md` |
| 2 §2 本地闸门 | G1–G4 串行全实跑全 ✅（G1 双重出包 deterministic / G2 严格 818 对账 / G3 含 1 条既定 ⛔vacuous / G4 selftest 30/30）；脱敏检测器 positive control 10/10 自证会响（转义层级陷阱拦截在前）；最终树复跑见 §5.4 | ✅ | `_w22/gates_g1..g4(.safe).log`、`phase4_gates_summary.md` |
| 3 §3 真机功能矩阵 | 12/12 轮预算用满；15 格 ✅10 / ⚠️2 / ⛔1 / ⛔vacuous3；四大增量（F1 多模态⛔→✅、E4 mistral 复活、F4 skill✅、F2 文件系统级）；未达格不冒充通过（逐格见 §3.1/§3.2 与台账本波段） | ⚠️（部分格行使后失败 / 未行使，逐格如实） | `_w22/evidence/*.jsonl`、`_w22/shots/*_safe.png`、`evidence/w22-matrix-summary.json` |
| 4 §4 链内迭代 | 12 轮连续无跳号（出厂断言实测）；每轮 7 字段齐全 + 四态 + 归因；环境类 ⛔ 逐格登记不归因代码 | ✅ | `_w22/evidence/iterations.jsonl` |
| 5 §5 推送与 CI | 见 §5.4（每次推送 commit + run 链接、CI 红次数与推送次数） | 见 §5.4 | §5.4 + `git show` |

### 5.2 明确未做（逐条带挂账号）与 OPEN 挂账

**明确未做 18 条**：与 §1.3 一一对应，挂账号见 §1.3 表（老号原样 `OPEN`；本波段新增 6 行：
收口执行行 `CLOSED` + 5 行 `OPEN`，全部在台账本波段有真实行，每行自带触发信号 + 缓解动作 +
证据三件套）。**本波真机未达成格**（逐条见台账本波段第 4 行）：E4b deepseek 前置缺失 ⛔vacuous、
E5 gemma HTTP429 ⛔ 环境侧、F3 workspace list ⚠️ 无记账、F5 手动压缩 `dropped>0` 未达、
F6 自动压缩 `applied=true` 未达、F7 无外部 MCP 端点 ⛔vacuous——触发信号与缓解动作已挂账，
节点复活 / 新会话夹具可铺时行使。四项用户终局裁决（journal 第 17 次顺延 / ≥3 端点终局 /
占位符口径 / `P2-3`）只留痕不代做。

**OPEN 挂账三件套**（本波段新增行，摘要；权威版见 `docs/06-deferred-ledger.md`）：

| 挂账号 | 触发信号 | 缓解动作 |
|---|---|---|
| 本波段第 1 行（收口执行） | 四报告一致判上一波收尾契约结构缺陷 | 最小结构修复 + 逐处标注，出口判据按字面复核成立 |
| 本波段第 2 行（制品缺陷） | 外部审查按上一波制品作历史结论复核 | 生成器断言升级 + 必红变异，历史制品不重出 |
| 本波段第 3 行（journal） | 用户未给显式二选一裁决 | 第 17 次顺延登记，两代数口径不统一、交用户拍板 |
| 本波段第 4 行（真机未达格） | 节点复活 / 新会话夹具可铺 | F1 已闭环；F5/F6 需 `.kt` 或放宽 file_read 上限（禁改本波）；E4b 需用户设备新增端点 |
| 本波段第 5 行（非对话模型） | 出现把视频生成/超分计进端点收敛 | chat 矩阵只收对话 LLM，不判 ⛔ 不计分母 |
| 本波段第 6 行（取证驱动遗留） | 后续波遇同型 BACK 逃逸 / 日志判据空转 | 维持取证设施不入仓；判据面改写规则见 §1.2 |

### 5.3 制品清单（交外部审查团队）

> **不含会话历史、不含凭据、不含端点标识、不含「建议下一链做什么」**。
> 端点一律以不可逆 8 位指纹出现（name-fp / chat-fp 双口径）；模型名为公开产品名；证据路径为
> 仓内相对路径或仓外证据目录名，不含本机绝对路径、设备序列号、账号字段、完整端点 URL。

| 制品 | 路径 | 说明 |
|---|---|---|
| 本波验收物 | `docs/analysis/w22-acceptance.md` | 本文件（§1 范围 / §2 闸门 / §3 真机 / §4 迭代 / §5 收尾） |
| 本波交接 | `docs/handoff-20261116-wave22.md` | 摘要，冲突时以验收物为准；严格零复合编号字面量 |
| 挂账台账 | `docs/06-deferred-ledger.md` | 本波段 6 行 + 被点名老行行内注记；老号全数原样保留 |
| 上一波收尾契约收口 | `docs/analysis/w21-acceptance.md` | 最小结构修复（面数九面五处同步 / 销账自证 / 推送链接 / 错数），每处带「W22 收口修订」标注 |
| 脱敏机器可读证据制品 | `evidence/w22-matrix-summary.json` | 出厂断言全过；`cells_total=17` = 真机 16 格（含 E4b 拆分）+ iter0 真实证据派生的 `local_ui` 格 L0（使声明九面 9/9 全覆盖，非硬造）；12 轮 / 零截断 / 隐私 0 命中 |
| 四闸门日志（仓外，不入库） | `_w22/gates_g*.log` + `.safe.log` | 串行实跑全 PASS；positive control 10/10 自证，脱敏副本独立复扫 CLEAN |
| 真机事实与脱敏截图（仓外，不入库） | `_w22/evidence/*.jsonl`（12/16/4/2 行）、`_w22/shots/*_safe.png`（80 张） | 逐格四态、切换回读、压缩触发线原文、SAF 链条 |
| 取证驱动与脱敏脚本（仓外，不入库） | `_w22/*.sh` / `redact.py` / `desensitize.py` | 不入仓维持老号口径 |
| 代码 diff | 本波各提交 `git show` | **零 `.kt` 改动**；只有文档 + 台账 + 脱敏证据制品 |
| 隐私自检 | 制品 `privacy` 字段 + `.safe.log` / `*_safe.png` 复核 | 禁用形态 0 命中；positive control 自证遮蔽机制会响 |

### 5.4 CI（实跑回填）

| 项 | 值 |
|---|---|
| 推送 1 | **（推送后回填：commit hash + files 数 + 仓根脱敏制品入库说明）** |
| Build run | **（推送后回填 run 链接 + 五 job 结论）** |
| Secret scan run（独立） | **（推送后回填）** |
| 最终树守卫复跑 | 本波文档落盘后复跑（19:43）`result=PASS ok=27 vacuous=1 violation=0 fault=0`、rc=0（`_w22/gates_g3_finaltree.log`，纯守卫输出无禁用形态；四闸门阶段的早期复跑见 `gates_g3_final.safe.log`，两处结论一致）；G20 `PASS`（ID 数实跑为准）、`MISSING=[]` |
| 推送 2 / 推送 3 | **（如发生则回填；纯文档推送 Build 0 run 属既定口径）** |
| CI 红次数 | **（推送后回填，上限 2）** |
| 推送次数 | **（推送后回填，上限 3）** |
| 归因 | 无失败案例 ⇒ 四分类未触发；节点限流/无响应/前置缺失判环境侧不写成应用 bug；纯文档提交 0 run 属既定口径不写「构建再次通过」 |

> 本波零 `.kt` 改动 ⇒ 「CI 全绿」指 Build 五 job + 独立 Secret scan 结论 success，
> **不等于**真机矩阵全格通过（§3 如实登记 ⛔ / ⚠️ / ⛔vacuous 的格）。
> 凭据纪律：run 链接取值仅用公开 API（零凭据）；推送凭据 inline 一次性使用、不落盘、remote 保持
> 干净 URL；不 force-push、不 rewrite history；每次推送留可审计 commit 与 run 链接。

**文档回写 diff**：`docs/analysis/w22-acceptance.md`（新）、`docs/handoff-20261116-wave22.md`（新，
零复合编号字面量）、`docs/06-deferred-ledger.md`（本波段 6 行 + 被点名老行行内注记）、
`docs/analysis/w21-acceptance.md`（收口最小修复，每处带「W22 收口修订」标注）、
`evidence/w22-matrix-summary.json`（新，脱敏机器可读制品）。历史各波正文与制品一律未动；
两份未跟踪源码日析保持未跟踪不入库（老号口径）；本波不写「建议下一链做什么」。
