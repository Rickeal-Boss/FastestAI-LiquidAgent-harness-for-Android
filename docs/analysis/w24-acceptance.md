# W24 收尾契约验收物 — 四份外审裁决 + 新节点×多上下文×多功能面真机矩阵 + 两条用户可见缺陷挂账落笔（2026-11-18）

> 基线 `9558d5a`（W23 回填，`main`；本地与远端 HEAD 一致，零 `.kt` 改动起点）。
> 上游依据：用户 2026-11-18 指令（读记忆 / 上一波 handoff / OPEN 挂账行〔现役仓 `harness-v2/`，
> **非旧仓** `Android-Agent/`〕，列出「做什么 / 明确不做」写入验收物第 1 节；读四份众审查人原始报告并
> **裁决分歧、不改方案大体方向**；用户给的 API〔用户本单 <凭据文件清单> 免费节点清单〔凭据文件名不落盘〕
> + openrouter `dots-3-note-preview:free` 512K〔工具+视觉声明，以真机为准〕+ `north-mini-code:free`，其余走 build.nvidia.com〕
> 测多模型 × 不同上下文〔直至 1M〕× 多模态输入 / 工作空间文件读写 / skill / MCP / 手动与自动上下文压缩；
> 先本地 UI + 功能测试 + adb 调试 → 真机测试 → 没问题推仓库 + CI 构建；设备先唤醒三步法、adb 用工作空间绝对路径、
> screencap 非全黑才算存活、uiautomator dump 对 Compose bounds 不作唯一判据；按截图回灌级别回灌脱敏截图判据与
> deviceLogTail，不回灌整段 logcat、不回灌凭据/未打码账号；依据真机结果边测边改形成「改→编译→真机→再看」
> 链内循环，每次迭代追加一行证据；密钥留真机；每次推送留可审计 commit 与 run 链接；不 force-push、不 rewrite
> history；handoff 不把推断写成事实；收尾契约验收物含 1→5 各步结果〔四态 + 证据路径，CI 绿附 run 链接〕、
> 明确未做〔逐条带挂账号〕、OPEN 挂账〔触发信号 + 缓解动作 + 挂账号〕、文档回写 diff、交外部审查制品清单
> 〔不含会话历史 / 凭据 / 端点标识 / 建议下一链〕；本次任务止于交接）。
> 判定四态 ✅ / ⚠️ / ⛔ / ⛔vacuous。**「未触发」≠「已验证通过」；「测试全绿」≠「口径被验证」；
> `rc=0` ≠ 产物已重生成；PC 侧结论不得写成设备侧可用；档位读数 ≠ 生效窗口 ≠ 1M 真压；
> 写「X 未发生」前先自证判据会响；推断带 caveat，不写成事实。**
> 密钥纪律：端点凭据**只在设备侧**；本单 / 工具 schema / 工具结果 / 日志 / 截图 / 交接 / 记忆写入
> **零凭据字面量**；端点一律以不可逆 8 位指纹出现（指纹 = UI 显示名 sha256 前 8 位）。
> 设备序列号、本机绝对路径、凭据文件名、未打码账号字段一律不出现。
> 完整版即本文件；`docs/handoff-20261118-wave24.md` 只做交接摘要，冲突时以本文件为准。
> 本文件不写「建议下一链做什么」。
> 🔴 编号纪律：交接文档严格零「W 加数字加减号加数字」复合编号字面量（G20 只扫 `docs/handoff-*.md`，
> 不扫 `docs/analysis/`）；本文件 §1.3 等处的挂账号是台账溯源，写完整号合规必要。
> 本波新增台账行由主理人**单人**落笔（台账 §三.6），外部审查草案不直接改真值源。

---

## 1. 本波范围：做什么 / 明确不做（执行前钉死）

### §1.1 四份外审报告分歧裁决表（不改方案大体方向 = 用户指令面优先）

> 输入四报告：报告①《W23 审查与挂账分析_20261118》、报告②《FastestAI-LiquidAgent-harness W23 深度审查
> 与挂账分析及 W24 路线建议_2026-11-18》、报告③《W24 挂账两行（待 CI 流落笔）》、报告④《W23 深度审查与
> W24 排期裁决_2026-11-18》。四值裁决 = 采纳 / 采纳订正 / 顺延留痕 / 证伪 / 部分采纳。
> 「改方向」列全为「否」——四报告排期主张（转代码波 / 拆分 / 决策清点先 / 触顶申报）整包顺延留痕、
> 不取消不推翻不代做；用户本单仍给 API 与新节点、明确要矩阵测试 ⇒ 指令面优先，矩阵存续决策包维持 `OPEN`；
> 四项用户终局裁决（journal 二选一 / ≥3 端点终局 / 占位符口径 / 并行键期望行为）只留痕不代做。
> 唯一「采纳并落地」的新动作 = 报告③④共同裁决的**两条用户可见缺陷挂账落笔（W24-01/W24-02，状态 `OPEN`）**
> ——这是台账落笔义务、非代码方向变更，且两行修复需先拆三方 ⇒ 本波只落笔不修。

| # | 议题 | 四报告主张 | 现役回源结果（本波核对） | 裁决 | 改方向 |
|---|---|---|---|---|---|
| 1 | 排期路线（矩阵触顶→转代码波 / 拆分 / 决策清点先 / 触顶申报） | 报告①§五「三项拍板零成本」；报告②§四「三选项弹药、代码波打包第七次递」；报告④§五「第 0/1/2 车」 | 用户本单仍给 <凭据文件清单> + 两支 openrouter 模型与密钥、明确要矩阵测试；指令面优先出处（上波验收物 §1.4）成立；矩阵存续决策包台账原样 `OPEN` | **顺延留痕**：四报告排期主张整包顺延，不转代码波、不升格拆分波、不关矩阵 | 否 |
| 2 | **报告③④ W24 段两行落笔**（上一轮两条新发现改挂新一波首两行） | 报告③全文草案：`W24-01` 思考框全黑 / `W24-02` 拥堵静默断词，原建议 `W23-07/08` 作废、逐字迁入、保留追溯注记、`OPEN`；报告④§4 明确「用户本轮裁决的执行……由 CI 流统一编号入表」 | 现役台账只到 W23 段、`W23-07/08` 不存在（段内到 06）；两行源码路径全 live（思考框 `GlassSurface.kt`/`Lens.kt`/`Shaders.kt`/`GlassColorScheme.kt`，断词 `SseEventParser.kt`/`RemoteLlmEngine.kt`/`OpenAiCompatibleAdapter.kt`/`RetryPolicy.kt` 本波 find 实证）；台账 §三.6 规定单人落笔 | **采纳并落地**：由主理人单人把两行逐字落 `docs/06-deferred-ledger.md` 的 W24 段首两行，状态 `OPEN`，带「← 原建议」追溯注记与必红验收判据；不新开 `W24-03+` 承接同一批老债；修复需拆三方 ⇒ 本波只落笔、不修 `.kt` | 否 |
| 3 | P0-1 上一波推送 2 自身 run 链接缺 | 报告④§3 P0-1：tip `9558d5a` 有 Secret scan run `44123791935`（success）但仓内零处引用；一次纯文档提交补 §5.4；W21 `7760632` 有先例 | 现役 `w23-acceptance.md` §5.4 文末仅推送 1 两条链接、推送 2 行自承「跨提交可补」；`grep 44123791935` 在验收物零命中；run 存在性须本波公开 API 复核后再回填，不采信转述 | **采纳订正**：作为收尾义务零 `.kt` 并入——公开 API 复核 `44123791935` 存在且 success 后，回填 W23 §5.4 推送 2 行 + 文末链接段 + handoff §八；若 API 不可达则如实留痕不臆造 | 否 |
| 4 | P0-2 `W23-01` 销账范本「全树 0 命中」回退 | 报告④§3 P0-2：该措辞按字面不成立，应按 `W21-01` 订正为「缺陷形态 0 命中（注记引用除外）」+ 排除规则/分类日志入仓；「8 处全修」打折（`truncated_fields` 顶层 `[]`、`na_allowlist` 3 vs 6） | 台账 line 455 仍写「全树 0 命中」（本波 grep 实证）；W21-01 同款订正先例在案；分类日志 `_w23/privacy_ruleset.safe.log` 在仓外 | **采纳订正**：收尾义务零 `.kt`——`W23-01` 证据列原句「全树 0 命中」改为「缺陷形态 0 命中（注记引用除外）」，状态/`触发信号`/`缓解动作`原句不动，行内注记说明订正；「8 处全修」按 `字段名:值` 逐处给出、空壳字段不计数（见 #5/#7） | 否 |
| 5 | P1-1 B 断言白名单不完备（`na_allowlist` 3 vs 6 个 NA 格） | 报告④§3 P1-1：必红样本只注入「清空首格」，未注入「新增未白名单 NA 格」 | 现役制品 `na_allowlist=['E4b','F7','L0']`，`endpoint_name_fp='NA'` 的格 6 个（多出 F5/F6/T0）；本波 python 解析实证 | **部分采纳**：本波**新制品**把 6 个 NA 格显式登记齐、B 断言加一条「未白名单 NA 格」必红样本；不改历史制品（不重出不覆盖）；不因此把 G20 判绿口径写死 | 否 |
| 6 | P1-2 必红样本结果自报 / runner 不可复核 | 报告④§3 P1-2：生成器在仓外、`mutation_cases[].result` 自报，建议加 `assertion_source`/`runner_sha256` | 现役制品无 `assertion_source`/`runner_sha256`（本波 python 实证）；取证设施不入仓是老号 `W20-14` 口径 | **部分采纳**：本波新制品加 `assertion_source`（生成器仓外路径占位）与 `runner_sha256`（生成器文件 sha256），使外部能核「哪个脚本哪版判红」；生成器本体仍不入仓（维持老号口径，不制造改方向） | 否 |
| 7 | P1-3 `truncated_fields` 空壳 | 报告④§3 P1-3：顶层 `[]`、0 格有该字段，与 §5.3 承诺冲突 | 现役制品 `truncated_fields=[]`（本波 python 实证） | **采纳订正**：本波新制品据实填写被截断字段清单（或明确声明零截断并给出依据），不留空壳；历史制品不重出 | 否 |
| 8 | P1-4 F6 的 5 行 `already_compressed` 是缺陷实锤非 ✅ 支撑 | 报告④§3 P1-4：`SettledCompressPolicy.kt:82` `alreadyCompressed`（`ChatUiState.compression != null`）单指针让长会话压缩一次后永久 noop，应并入缓解动作而非写进 ✅ 支撑 | 上波验收物 §3 F6 把 `already_compressed` 5 行写成「落账生效证据」；单指针源码路径 live | **采纳订正**：本波验收物 §3 引用 F6 时把 `already_compressed` 单指针明确列为**缺陷实锤**、登记进本波挂账缓解动作，不再作为「压缩主线闭环」支撑；不改 W23 历史制品正文 | 否 |
| 9 | P1-5 G20 盲区与六行漏号（`W13-01` + `W11-06..10`） | 报告④§3 P1-5：112 ID 六波不变、W23 新增 6 行零覆盖；扩扫描面前须先批量补六行 | 现役 G20 扫 `docs/handoff-*.md`、六漏号在台账外；老号 `W15-01`/`W16-01` 聚合行未拆 | **顺延留痕（用户裁决域）**：扩扫描面 / 拆六行属守卫 + 台账真值源改动，本波不扩、不制造幽灵行；G20 按实跑口径不写死数 | 否 |
| 10 | P1-6 E5 HTTP400 归因偏松 | 报告④§3 P1-6：`RemoteLlmEngine.kt:327-353` 400 除超窗关键词外一律 `PROTOCOL`（不可重试），429 才 `RATE_LIMIT`；上波把 400+429 整包写「限流复证·环境侧」 | 源码路径 live；上波验收物 §3 E5 记「首轮 HTTP400 request rejected、二次 429」 | **采纳订正**：本波真机若复现，400→`PROTOCOL` 与 429→`RATE_LIMIT` 单列分开归因，不整包写限流；不复现则如实登记未触发，不臆造 | 否 |
| 11 | N-1 笔误键并行期望行为（第 6 次被翻出） | 报告①§二收敛 / 报告②§一「第 5 次现役证伪」 | 现役完整键 `parallel_tool_calls` 自初始即在、allowlist 强制行为测试在案；用户本单未给期望行为拍板 | **证伪 + 顺延留痕**：证伪「现存 bug / 保存链必拒合法键」前提，拒绝修不存在的 bug；「+1 行测试」属 `.kt` 且缺用户期望行为裁决，顺延留痕、本波不做 | 否 |
| 12 | journal 顺延代数（上波第 18 次，本波第 19 次） | 报告④§3「`W9-05` 多值并列、代数口径统一待裁决」 | 老行 `W9-05` 仍写「第 7 次」、各波段写 9/10/…/18；用户仍未给二选一 | **顺延留痕（不代裁）**：本波段登记第 19 次顺延、两代数口径并列交用户；老行不改状态不并号；累计成本注记以行内注记零成本并入〔本波收口修订：该第 19 次顺延的台账行原缺失，已由 W25 主理人单人补入 W24 段末，消除验收物声称与台账零行的账实不符〕 | 否 |
| 13 | 四项用户终局裁决清点（journal / ≥3 端点 / 占位符 / 并行键） | 报告①§五.1「三项拍板零成本」；报告②§四「三选一问用户」 | 四项均属用户终局裁决域，现役无用户拍板 | **顺延留痕（不代裁）**：只留痕、不软化、不关号；本波不把清点动作当成裁决完成 | 否 |
| 14 | 拆分三方 / 安全波 / 取证设施入仓 / 加长 system / G19 摘 VACUOUS / `@Test≥850` | 报告②④第 1/2 车清单 | G10 余量 4/4/5、`ChatViewModel` 500/500、`EXPECTED_TESTS=818`、G19 守空集既定 ⛔vacuous；修复两条用户可见缺陷需拆三方先行 | **顺延留痕**：整包顺延，需 `.kt` 或改守卫/改测试数，本波不做；列 §1.3 明确不做；不抬 `@Test`、不摘 VACUOUS、不放宽行数上限 | 否 |

> 裁决合计 14 条：**采纳并落地 1（#2）/ 采纳订正 4（#3/#4/#7/#8，#10 并入真机行使）/ 部分采纳 2（#5/#6）/
> 顺延留痕 6（#1/#9/#12/#13/#14 + #10 视真机决定）/ 证伪 1（#11）**。全部 `改方向 = 否`。
> **本波唯一新增落地动作 = 两行台账落笔（`W24-01`/`W24-02`，`OPEN`）**，系报告③④共同裁决的用户指令面动作，
> 属台账义务非代码方向；其余外审排期主张整包顺延。
> 注 1（收尾义务非改方向）：#3/#4 修的是「上一波自己的收尾契约缺陷」，属本波收尾义务完成，不构成方案方向变更；
> 与上一波段「修上一波本体 + 老行行内注记」先例一致，不升格独立排期波。
> 注 2（历史制品边界）：#5/#6/#7 只作用于**本波新制品**；历史制品 JSON 与更早各波正文一律不重出不覆盖。
> 注 3（推断不写成事实）：#2 两行缺陷的「全黑 / 断词」是外审按源码 + 测量语义**推导**，本波落笔写明触发信号与
> 必红验收判据、状态 `OPEN`，**不写成已复现事实**；本波真机若行使则四态如实回填，未行使即 `OPEN` 留痕。

---

### §1.2 做什么（本波判据 · 沿用上一波 12 项框架 + 两行落笔新增项）

| # | 判据 | 行使方式 | 出处 |
|---|---|---|---|
| 1 | 范围 §1：四报告逐条裁决 + 做什么/不做 + 两行落笔 | §1.1 裁决表 + §1.2/§1.3 写入本波验收物第 1 节；两行台账由主理人单人落笔；交接侧挂账号转中文段位 | 用户指令 + 四外审 |
| 2 | **端点轴**：新节点 × 多模型（`dots-3-note-preview:free` 512K〔用户标注工具+视觉双通，以真机 `inputModalities` 为准〕、`north-mini-code:free`，其余走 build.nvidia.com；沿用上一波段 dots / north / step5 / mistral / gemma / nemotron 六端点池观测） | 设备侧 fast-fail 预筛后真往返收敛取证；指纹 = UI 显示名 sha256 前 8 位；密钥复用双路径（UI 编辑留空保留 / 信封按 id 复制二选一）；零搬运、零明文落盘；不在池即 ⛔vacuous，限流即 ⛔ 环境侧 | 用户指令 + 报告①③④；端点池前置见 §1.4 |
| 3 | **档位轴**：多上下文（32K / 256K / 512K / 直至 1M **读数**，非真压） | 档位真切换回读 `switch_verified=true`；`dots` 512K 档与 build.nvidia.com 模型 1M 档分别取读数 + 计量条；**档位读数 ≠ 生效窗口 ≠ 1M 真压** | 用户指令 + 报告③；真压双阻断见 §1.3 |
| 4 | 多模态输入 | 真图 + `scan_file` 触发媒库 + SAF 选图 chip + 语义问答；先验 `inputModalities`（TEXT-only 端点 chip 灰如实 ⛔）；换设备池含 IMAGE 端点攻语义逐字命中；不写用户标注为已验证 | 用户指令 + 报告④；端点池前置见 §1.4 |
| 5 | 工作空间文件读写 | `file_write`/`file_read` 严格 `ls -1` 差集 + ns mtime + 内容逐字 sentinel + 审批框 exact args + 反背题对照 | 用户指令 + 报告④ |
| 6 | skill 功能面 | 设备侧 `skills/` 资产注入 → 冷启动「来源：file」→ `@name` 点选 → sentinel 逐字 + 横幅 + usage 增量；无 `skill_load` 记账即以 UI 副作用/sentinel 为准并带 caveat | 用户指令 + 报告④ |
| 7 | MCP 现状确认 | 顶栏返回箭头绕系统 BACK → MCP 列表页可达 + 空态截图；导航（✅）与行使（⛔vacuous）分开落账；平台开关 G15b 明文阻断不放开 | 用户指令 + 报告③；平台开关见 §1.4 |
| 8 | 手动 + 自动上下文压缩 | 32K + 新会话逐条短消息夹具；手动 `dropped>0` 双向断言、自动 `applied=true+dropped>0`；**`already_compressed` 单指针按缺陷实锤登记（#8），不写成压缩主线闭环**；不写硬出口判据，夹具/节点不配合如实四态 | 用户指令 + 报告①④ |
| 9 | **两条用户可见缺陷真机行使（可选，前置决定四态）** | `W24-01` 思考框：浅色/深色 × 收起/展开 reasoning 气泡逐帧截图 + 中心像素亮度/文字对比度；`W24-02` 断词：MockWebServer 类前端不可得时以真机 gemma 限流/排队态观察 `ChatUiState` 是否有 error 提示与重试入口。判据前置不满足即 `OPEN` 留痕、不写复现事实；触发则四态如实回填 | 报告③④；源码路径本波 find 实证 live |
| 10 | 本地先行 + adb 调试 + 链内迭代 | 先本地 UI/功能测试 + adb 调试；真机「改→编译→真机→再看」链内循环；每轮追加一行证据（action/observation/state/attribution/evidence_ref/caveat） | 用户指令 |
| 11 | 收尾契约：上一波 P0/P1 收口 + 新制品字段 | 修 W23 收尾契约 P0-1（补推送 2 run 链接）/ P0-2（「全树 0 命中」注记订正）；本波新制品加 `assertion_source`/`runner_sha256`、NA 白名单补齐、`truncated_fields` 据实、B 断言新增未白名单必红样本；历史制品不重出 | 报告④第 0 车 + 收尾义务先例 |
| 12 | 四闸门 + 推送 + CI | G1–G4 串行实跑后推送；每次 commit + run 链接；脱敏 positive control 先自证会响；凭据 inline 一次性、用后清 remote；不 force-push、不 rewrite history | 用户指令 + 仓库 `AGENTS.md` |
| 13 | 脱敏 + 零凭据 + 零复合编号 + 四态纪律（全局） | 端点 8 位指纹；产物零 PAT/真 key/本机路径/设备序列号/未打码账号/凭据文件名；交接文档零复合编号字面量；未触发 ≠ 通过、推断带 caveat；每次落盘前后各扫一遍 | 用户指令 + 守卫自指陷阱 + 报告② P0-2 |

---

### §1.3 明确不做（逐条带挂账号，全部原样 `OPEN`）

> 沿用波段「明确不做」挂账号前缀体系；挂账号已对台账真值源（现役 `docs/06-deferred-ledger.md`）逐条核对。
> 本表进 `docs/analysis/`（G20 不扫 analysis），故写完整挂账号；转抄进交接文档时改中文段位。

| # | 不做项 | 挂账号 |
|---|---|---|
| 1 | 拆分三方整包 / 任何 `.kt` 增量 / 调行数与测试数上限（含修 `W24-01/02` 两缺陷所需的 `feature-chat` 余量腾挪） | W20-02（承接 W16-03）、W24-01、W24-02 |
| 2 | journal / FreezeClock 二选一代裁决（本波段登记第 19 次顺延，两代数口径不统一） | W24-03 → W21-03 → W20-01 → W9-05 |
| 3 | 设备侧 1M 真压（双阻断未解，只做 1M 档位读数） | W20-05 → W9-11 |
| 4 | MCP 明文联调 / 放开明文流量开关 / https 夹具 | W20-06 → W5-03 |
| 5 | git 历史清理 / force-push / rewrite history | W20-07 |
| 6 | 四项用户终局裁决代做（journal / ≥3 端点终局 / 占位符口径 / `P2-3` 并行键期望行为） | W20-08 |
| 7 | 安全波整包 | W20-04 |
| 8 | 台账同物多名并号 / 台账模板改动（采「紧迫性下降」，不单独排期） | W20-10、W20-16 |
| 9 | 守卫侧修复 / G20 扫描面扩展 / 六行漏号补齐 / 别名机制 | W20-17、W20-03 |
| 10 | 取证设施（驱动/脱敏脚本）入仓——本波断言补丁只落仓外 | W20-14 |
| 11 | 加长 system / 音频 / `core-device` 独立波 / 图片预算四条 | W20-11 |
| 12 | 非对话模型（视频生成 / 视频超分）进 chat 端点矩阵 | W21-05 |
| 13 | PC 侧免费节点结论升级为「设备侧可用」 | W20-12 |
| 14 | 历史制品正文重出 / 更早各波正文改写（本波只修上一波本体注记 + 仓外生成器） | W21-02 → W20-15 |
| 15 | 摘 VACUOUS / 观测缝 / 工具记账上线（需 `.kt`） | W21-06、W20-03 |
| 16 | `@Test 818→≥850` / 改测试数上限（补测触计数） | W20-09、W20-02 |
| 17 | N-1 白名单行为面「修复」——现役完整键已在、测试已在，拒绝对不存在的 bug 动刀、拒绝新增门禁 | W20-09（缺陷清单域，仅登记不修） |
| 18 | 把手动压缩 `dropped>0` / 自动压缩 `applied=true` 写成硬出口判据或达标 ✅（含把 `already_compressed` 写成压缩主线闭环证据） | W21-04 |
| 19 | 把两条用户可见缺陷（`W24-01/02`）的推导写成「已复现事实」——本波只落笔触发信号与必红判据，真机未行使即 `OPEN` | W24-01、W24-02 |

---

### §1.4 设备硬件可行性 + 矩阵前置条件表（方案席独特维度）

#### 1.4.1 目标设备与已知怪癖

- **目标机**：OPPO PDRM00，Android 13（SDK 33，知识库记录，本波真机回读核验，不写成已验证）；纯 API Harness（模型云端、无端侧模型，本波不涉及端侧引擎判据）。
- **灭屏挂起**：`SCREEN_STATE_OFF` / Dozing 下 `pm install-commit` 永久挂起；前置先 `KEYCODE_WAKEUP` + `screencap` 非全黑才算存活。**本波起步实测**：`dumpsys power` 从 `Dozing`/`SCREEN_STATE_OFF` 唤醒到 `Awake`/`SCREEN_STATE_ON`，`exec-out screencap` 176,831 B、PNG 头 `89 50 4e 47` 正确、屏幕显示对话页 ⇒ 存活成立（证据 `_w24/shots/alive_raw.png`）。
- **adb 通道**：一律有线；含路径命令加 `MSYS_NO_PATHCONV=1`；adb 用工作空间绝对路径并指定 server 端口 `-P 15037`；起步实测 daemon 每条命令后重启 ⇒ screencap 与读取放同一条调用。
- **Compose bounds**：`uiautomator dump` 对 Compose 组件 bounds 不可靠，点按判据以 journal / agent log / 回读为准，不作唯一判据。
- **日志与题面**：`logcat -s` 漏 native 行 ⇒ 设备侧全量落盘事后 pull；`input text` 非 ASCII 触发 NPE ⇒ 题面只用 ASCII。
- **二进制读写**：`adb exec-out` 读、push → `/data/local/tmp` → `run-as cp` 写；端点切换走 DataStore（force-stop + exec-out 读回）。

#### 1.4.2 端点池勘察（本波脱敏实证，起步 iter0）

设备侧 `files/harness/endpoints.json` = 4,001 B / 6 端点，`files/harness/secrets/` 6 个 blob id（内容不读、不落盘）。端点指纹（UI 显示名 sha256 前 8 位）：

| name-fp | 公开 modelId | baseUrl host | inputModalities（待逐格回读） |
|---|---|---|---|
| `01e476bd` | nvidia/nemotron-3.5-super-vl-120b-a12b | integrate.api.nvidia.com/v1 | 待核 |
| `b995dc45` | dots-studio/dots-3-note-preview:free | openrouter.ai/api/v1/chat/completions | 待核（用户标注双通） |
| `d5022055` | mistralai/mistral-large-4-1t-a52b | integrate.api.nvidia.com/v1 | 历史实测含 IMAGE |
| `a9ae3f9e` | stepfun-ai/step-5-preview | integrate.api.nvidia.com/v1 | 待核 |
| `b0113b2c` | cohere/north-mini-code:free | openrouter.ai/api/v1/chat/completions | 待核 |
| `cfd5fbda` | google/gemma-4-26b-a4b-it:free | openrouter.ai/api/v1/chat/completions | 待核（历史 HTTP429） |

> 用户本单给的 <凭据文件清单> 清单〔凭据文件名不落盘〕与两支 openrouter 模型：两支 openrouter（`dots-3-note-preview:free` = `b995dc45`、`north-mini-code:free` = `b0113b2c`）**已在设备池**；build.nvidia.com 其余模型须 fast-fail 预筛后再真收敛，不在池即 ⛔vacuous，不擅自新增端点。

#### 1.4.3 平台开关清单（判据依赖平台行为前先列）

| 平台开关 / 通道 | 现值（现役/知识库） | 对本波判据的影响 |
|---|---|---|
| G15b 明文流量 | `false`（不为测试放开，老号挂账） | MCP 明文行使判据被平台拒 ⇒ 导航✅ + 行使⛔vacuous；不放开、不造 https 夹具 |
| 档位选择器 + 计量条 | 设备端档位可切、计量条回读 | 1M「读数」依赖档位切换 + 计量条回读；真压双阻断未解 |
| 密钥复用双路径 | UI 编辑留空保留 / 信封按 id 复制 | 新节点注入靠此二选一；两路都断 ⇒ 新格如实 ⛔vacuous |
| 端点池（设备侧） | 本波勘察 6 端点 | 端点轴每格先查池；build.nvidia.com 新模型 fast-fail 预筛后再收敛 |

#### 1.4.4 矩阵前置条件表（前置 / 责任方 / 可达性 / 四态预期）

| 前置条件 | 责任方 | 可达性（本波判定） | 四态预期 |
|---|---|---|---|
| 设备唤醒 + adb 三步法 + 非全黑 screencap | 执行席 | **已达成**（iter0 实测 Awake/非全黑） | 硬前置项，非矩阵格 |
| 端点池核对：deepseek 不在池、gemma 曾限流 | 用户侧/执行席 | 条件可达；限流归环境侧 | 不在池 ⛔vacuous、限流 ⛔ 环境侧，不臆造 pass |
| 多模态 IMAGE 端点（历史 mistral 含 IMAGE） | 环境侧（节点存活）+ 执行席（先验 chip） | 条件可达 | 真图语义逐字命中 ⇒ ✅；chip 灰 ⇒ ⛔/⛔vacuous 如实 |
| build.nvidia.com 其余模型 + 两支 openrouter fast-fail 预筛 | 用户侧 + 执行席 | 条件可达 | 收敛 ⇒ ✅；限流 ⇒ ⛔ 环境侧；不在池 ⇒ ⛔vacuous |
| 1M 档位读数（非真压） | 执行席 | 可达（档位切换 + 计量条回读） | 回读 + 计量条 ⇒ ✅（读数格）；真压不做 |
| 工具记账观测缝（skill / file_list / 压缩判据） | 需 `.kt`，本波禁改 | 不可达 | 以 UI 副作用 / sentinel / 文件系统证据为准并带 caveat；无记账 ⇒ ⚠️ |
| F5 夹具：32K + 新会话逐条短问答 | 执行席 | 零代码可试，几何夹紧 | `dropped>0` ⇒ ✅；「无可压缩内容」⇒ ⛔vacuous；不写硬出口 |
| MCP 真实行使 | 需平台开关放开或 https 夹具 | 不可达 | 导航✅ + 行使⛔vacuous 分开落账 |
| 两条缺陷复现判据 | 执行席 | 条件可达（需 reasoning 气泡 / 限流态） | 前置不满足 ⇒ `OPEN` 留痕不写事实；行使则四态如实 |
| 收尾契约 P0/P1 修复 + 两行落笔 | 主理人单人 + 执行席（仓外生成器） | 可达（零 `.kt`） | 公开 API 复核 run + 注记订正 + 新制品字段实跑 ⇒ ✅ |

> 已验证（本波 iter0）：设备唤醒、adb 端口/三步法、6 端点池、密钥 blob 6 个、两条缺陷源码路径 live、
> 台账只到 W23 段、P0-2 措辞仍在、制品 P1 字段空壳。
> 待验证（本波真机取）：Android SDK 级别回读、各端点 `inputModalities`、build.nvidia.com 模型可达性、
> 1M 计量条读数、两条缺陷真机判据、档位切换回读。
> 环境类 ⛔（限流 / 无响应 / 前置缺失 / 平台阻断）一律不归因应用代码；PC 侧结论不写成设备侧可用。

---

### §1.5 本波定位一句话 + 主理人通报要点

**定位一句话**：W24 = 「四外审裁决 + 新节点×多上下文〔直至 1M 读数〕×多功能面真机矩阵 + 上一波收尾契约缺陷次级收口 + 两条用户可见缺陷挂账落笔」复合波的方案裁决阶段——用户指令面优先、零 `.kt`、裁决不改方向，唯一新增落地动作是台账落笔义务。

**给用户的主理人通报要点（≤5 行）**：
1. 四报告排期主张（转代码波 / 拆分 / 决策清点先 / 触顶申报）整包顺延留痕、不改方向；矩阵存续与四项终局裁决维持 `OPEN` 不代裁。
2. 采纳报告③④共同裁决：两条用户可见缺陷（思考框全黑 / 拥堵静默断词）落 W24 段首两行 `OPEN`，带必红判据；只落笔、不写成已复现事实、不修 `.kt`（修复需先拆三方）。
3. 收尾义务零 `.kt`：补推送 2 run 链接（公开 API 复核后回填）、`W23-01`「全树 0 命中」注记订正；本波新制品补 `assertion_source`/`runner_sha256`/NA 白名单/`truncated_fields` 据实，历史制品不重出。
4. N-1 笔误键第 6 次证伪、journal 第 19 次顺延、P1-4 `already_compressed` 单指针登记为缺陷实锤而非 ✅ 支撑。
5. 判据纪律：档位读数 ≠ 生效窗口 ≠ 1M 真压；未触发 ≠ 通过、推断带 caveat；需 `.kt` 的拆分/观测缝/测试数整包顺延。

---

## 2. 本地闸门结果（四态 + 证据路径）

> 由 CI 工作流席后台串行实跑（全程无 adb / git，仓内只读），日志落仓外 `_w24/gates_*.log`。
> 代理端口本波实时值 3046（从 `https_proxy` 现取，不写死）；G4 后台跑避 120s SIGTERM。

| 闸门 | 四态 | 关键实值 | 证据路径 |
|---|---|---|---|
| G1 出包 | ✅ | python 删旧 APK 后两次 `assembleDebug` 均 rc=0 / BUILD SUCCESSFUL；pre-X MISSING → post-X 存在 = 真重建；bytes=68407402、sha256 `ca87a70a…6513` 两次一致、mtime 递增 ⇒ deterministic 成立 | `_w24/gates_g1{,_runa,_runb}.log` + `.safe.log` |
| G2 fulltest | ✅ | 严格 `grep -rnE "^\s*@Test\b"` = **818** = build.yml `EXPECTED_TESTS`；rc=0，tests=818 / failures=0 / errors=0 / skipped=0；83 个 XML mtime 全本轮、覆盖 14 模块；无「test-results 未能清除」句柄锁 | `_w24/gates_g2.log` + `.safe.log` |
| G3 arch-guard | ✅ | 仓库根 `bash scripts/arch-guard.sh` rc=0，result=PASS ok=27 vacuous=1 violation=0 fault=0（G19 守空集 VACUOUS 在基线内，同 W23） | `_w24/gates_g3.log` + `.safe.log` |
| G4 selftest | ✅ | `arch-guard-selftest.sh` 后台 218s rc=0，**30/30 PASS**（含 VAC-G1b / VAC-G21 空转机制自证） | `_w24/gates_g4.log` + `.safe.log` |
| 脱敏正控 | ✅ | 正控 5/5 敏感形态全响（PAT / ghp / 设备序列号 / 本机路径 / 凭据文件名）；临时检测器一次暴露 \b 退格符恒不响坑（r'\b133'+'09'+r'cc8\b' 末段非 raw），改 raw 全字串后全响；仓外正式扫描器 `self_audit` 15/15 全响后才采信 0 命中 | `_w24/scan_w24.py`；最终扫描结果见 §5.5 |
| 最终树守卫复跑 | ✅ | 文档落盘后复跑 arch-guard 实得 `PASS ok=27 vacuous=1 violation=0 fault=0`、G20 OK（112 handoff ID 全在台账、CLOSED 证据非空）、rc=0；L1 硬输入上限未触（本波新增 2 文件 + 1 老波最小修复，docs 下 md 78 个、本波验收物 368 行 / 交接 105 行，均远低于软阈值；不写死未实测的全仓计数） | `_w24/gates_g3_final.safe.log`（推送前实跑确认） |

> 「测试全绿」≠「口径被验证」：G1–G4 证明构建 / 测试 / 守卫基线健康，不等于真机矩阵全格通过
> （§3 如实登记 ⛔ / ⛔vacuous / ⚠️ 的格），也不等于两条新缺陷已复现。

---

## 3. 真机矩阵逐格结果（19 格式 · 本波 11 格 + 两条缺陷格）

> 端点一律 8 位指纹；档位 = 应用侧会话预算（与端点 contextLength 声明解耦）；
> 判据以逐字回读 / usage 上屏 / UI 副作用 / 工具参数为准，无工具记账的格从严 ⚠️ 并带 caveat。
> 机器可读逐格明细见 `evidence/w24-matrix-summary.json`（cells[] + iterations[] + attempt_states +
> evidence_ref + 11 条断言必红变异）。

| 格 | 功能面 | 端点指纹 / 档位 | 四态 | 关键判据与证据 |
|---|---|---|---|---|
| L0 | local_ui | NA / NA | ✅ | 设备从 Dozing/OFF 唤醒到 Awake/ON、screencap 176,831B PNG 头正确、非全黑；`_w24/shots/alive_raw.png` |
| E2 | endpoint_axis | 01e476bd (NV-VL) / 32K | ✅ | token `W24BRAVO42` 逐字 + usage `2.4k in / 730 out` + 发送键 live bounds；`_w24/shots/e2_nvvl2.png`。caveat：首个带冒号题面显示截断失败、导航修正后收敛 |
| E1 | endpoint_axis | b995dc45 (dots) / 32K | ⛔ | 首请求 50–118s 无 usage 无回复、stop 后「本轮没有产出内容」；`_w24/shots/e1_dots_reply.png`。环境侧（免费节点慢/限流），不归因代码；用户标注双通≠本机本轮可用 |
| E3 | endpoint_axis | b0113b2c (north) / 32K | ⛔ | 首请求 59s 仍等待、stop；`_w24/shots/north_wait.png`。环境侧；用户标注可用≠本机本轮可用 |
| T1 | tier_axis | 01e476bd / 1M | ✅ | 档位回读「当前 1M」、上下文条 `2.5K / 995.9K`、token `W24DELTA77` 逐字、usage `2.4k in / 435 out`；`_w24/shots/e5_1m_readout.png`。caveat：**档位读数 ≠ 1M 真压**（输入仅 2.5K） |
| F1 | multimodal | d5022055 (mistral) / 1M | ✅ | mistral `添加图片` chip → SAF 选真图 → 回复逐字 `W24 VISION 737` + `RED FRAME TEST`、usage `2.2k in / 75 out`；`_w24/shots/mm_semantic_reply.png`。NV-VL 同按钮为「当前端点未开启图片输入」⇒ 以真机 inputModalities 为准 |
| F2 | workspace | 01e476bd / 1M | ⚠️ | 审批框 exact args `{"path":"w24sentinel.txt","content":"W24SENTINEL9182"}` 完整、工具调用成立；但 workspace `ls` 无该文件、落盘副作用未坐实；read 轮 done + tool 参数；`_w24/shots/f2_after_allow.png`。从严 ⚠️ 同 W23（工具记账观测缝需 `.kt`）〔本波订正：「同 W23」仅同为回复级/⚠️ 取证等级，不抹平 W23 F2 与 W24 F2 各自格；工具记账需 `.kt` 仍 `OPEN`〕 |
| F4 | skill | 01e476bd / 1M | ✅ | 冷启动注入 skill → skill 页「来源：file」可见 → @ 点选 → 工具 `{"name":…}` + `[SKILL BODY]` → 回复逐字 `SENTINEL-W22-Q7X3M`、usage `2.6k in / 154 out`；`_w24/shots/skill_sentinel_reply.png`。caveat：本波新注入 file skill 在 skill 页可见但 @ 菜单未含（需额外注册），逐字用已在 @ 的历史资产验 |
| F7 | mcp | NA / NA | ⛔vacuous | 设置→MCP 服务器页可达、「远程工具来源 / 还没有 MCP 服务器 / 空态」；`_w24/shots/mcp_page.png`。导航 ✅ ≠ 行使 ✅；无外部端点 + 平台明文开关阻断，不放开明文、不造 https 夹具 |
| W24-01 | multimodal（缺陷推导） | 01e476bd / 32K | ⚠️（未复现） | 浅色主题 reasoning 气泡收起/展开 3 帧均浅米底 + 深色可读文字、逐字可读、未现全黑；`_w24/shots/w2401_expand_f1.png`。深色主题未测 ⇒ 保持 `OPEN`、不证伪不销账；像素坐标多次校准后以视觉判据为准 |
| W24-02 | endpoint_axis（缺陷推导） | b995dc45 / 32K | ⛔vacuous | dots/north 50–102s 无产出、UI 等待/已停止、未见半截静默；无 MockWebServer 前端；`_w24/shots/north_wait.png`。限流/无响应归环境侧、保持 `OPEN`、不写成复现事实 |
| F6 | auto_compress | 01e476bd / 32K | ⛔vacuous（夹具夹紧） | 32K 档连续短问答累积，agent log 全程 `auto_compact=noop reason=below_trigger`，未达 `dropped>0`；`_w24/evidence/compress_probe.json`。夹具几何夹紧，同 W23；不写硬出口判据，不改判据凑绿。（明细见 §4 收敛后回填）〔本波订正：「同 W23」仅同为 ⛔vacuous 四态，不抹平 W23 F6 曾 ✅ `dropped=19` 的历史格；本波不改写任何历史判级〕 |

> **本波未设格**：manual_compress（手动压缩，W23 判 ⛔vacuous；本波零 `.kt` 且夹具几何夹紧，无独立证据 ⇒
> 在 `coverage_note` 说明从历史推断、不硬造格）；`declared_feature_faces` 仍 declared 9 面、
> `feature_faces_with_cells` 按实测面列，不冒充全覆盖。
> 声明 9 面全覆盖的断言按「有格的面」计，缺格面在 coverage_note 如实留痕（不制造幽灵格）。

---

## 4. 链内迭代记录（改 → 编译 → 真机 → 再看；每轮一行证据）

> 明细机器可读见 `_w24/evidence/iterations.jsonl`（11 行，action / observation / state / attribution /
> evidence_ref / caveat 齐全），制品 `evidence/w24-matrix-summary.json` 的 `iterations[]` 同源。
> 下表为逐轮摘要；四态与证据路径不写死在交接文档（交接 §四指向本节）。

| iter | 类型 | action | observation（摘要） | state | evidence_ref |
|---|---|---|---|---|---|
| 1 | E1 dots 首试 | 带冒号题面发送 | 题面气泡显示截断为 "Say"、50–102s 无 usage、stop 后无产出 | attempt_failed | `_w24/shots/e1_dots_reply.png` |
| 2 | E2 NV-VL 收敛 | 导航修正 + 纯 ASCII token | token 逐字 + usage 2.4k/730；reasoning 气泡展开浅底可读 | endpoint_pass | `_w24/shots/e2_nvvl2.png` |
| 3 | W24-01 取证 | 思考框收起/展开 3 帧 | 浅色主题全 3 帧浅米底深色字、未现全黑 | not_reproduced_light | `_w24/shots/w2401_expand_f1.png` |
| 4 | E3 north 首试 | 纯 token 发送 | 59s 仍等待、无 usage、stop | attempt_failed_env | `_w24/shots/north_wait.png` |
| 5 | T1 1M 读数 | 切 1M + 发送 | 回读当前 1M、上下文 2.5K/995.9K、token 逐字 | tier_readout_pass | `_w24/shots/e5_1m_readout.png` |
| 6 | F1 多模态前置 | 探 + 按钮 content-desc | NV-VL「当前端点未开启图片输入」、mistral「添加图片」 | image_disabled_on_endpoint | `_w24/shots/mm_plus_menu.png` |
| 7 | F1 多模态语义 | mistral 添加真图 + 语义问答 | 回复逐字 W24 VISION 737 + RED FRAME TEST | pass | `_w24/shots/mm_semantic_reply.png` |
| 8 | F2 工作空间 | file_write + file_read + 审批 | 审批 exact args 完整、工具调用成立、落盘副作用未坐实 | partial_warn | `_w24/shots/f2_after_allow.png` |
| 9 | F4 skill | 冷启动 + @ 点选 + 逐字 | [SKILL BODY] 注入 + 逐字 SENTINEL-W22-Q7X3M | pass_with_caveat | `_w24/shots/skill_sentinel_reply.png` |
| 10 | F7 MCP 导航 | 设置→MCP 页 | 空态可达、行使 vacuous | navigation_pass_exercise_vacuous | `_w24/shots/mcp_page.png` |
| 11 | F6 自动压缩 | 32K 档 12 轮短问答 | agent log 全程 below_trigger、无 dropped>0 | vacuous_fixture_clamped | `_w24/evidence/compress_probe.json` |

**迭代中真实发生的「改 → 再测」链内修正**（非改代码，均为驱动 / 题面 / 夹具层）：

| 轮次 | 观察 | 改法（仓外驱动 / 题面） | 再测结果 |
|---|---|---|---|
| iter1 → iter2 | 带冒号题面显示截断、非对话页输入落空 | ① `ask.py` 强制 `nav.to_chat()` 后再 newchat；② 题面去冒号、纯 ASCII token | NV-VL 8.6s 收敛、token 逐字 |
| iter6 → iter7 | NV-VL `+` 为「当前端点未开启图片输入」 | 切含 IMAGE 的 mistral 端点攻多模态 | 语义逐字命中、真图 chip 成立 |
| iter8 判据 | 题面气泡显示截断疑丢参数 | 以审批框 exact args 为准（参数完整），不臆测截断；副作用另行核验 | 落盘未坐实 ⇒ 从严 ⚠️，不虚报 ✅ |

> 每次迭代追加一行证据，无事后补写；未收敛的早期态保留在 `attempt_states`，不与最终态合并。
> 最大真机迭代未触及硬顶即收敛主要判据；剩余 ⛔ / ⛔vacuous 格为环境 / 前置 / 夹具类，非判据失败可强攻。

---

## 5. 收尾契约（步 5 结果 / 明确未做 / OPEN 挂账 / 回写 diff / 制品清单 / CI）

### §5.1 1→5 各步结果一览（四态 + 证据路径）

| 步 | 内容 | 四态 | 证据路径 |
|---|---|---|---|
| 步 1 | 范围钉死：四外审 14 条裁决 + 做什么 13 项 + 明确不做 19 条 + 设备可行性 / 前置表 + 两行落笔 | ✅ | 本文件 §1.1–§1.5；台账 W24 段两行 |
| 步 2 | 本地 UI + 功能测试 + adb 调试：四闸门全绿 + 输入链预检（发送键 live bounds + 三步法唤醒） | ✅ | 本文件 §2；`_w24/gates_*.log`；`_w24/shots/preflight.png` |
| 步 3 | 真机功能矩阵：12 格（pass5 / fail2 / partial2 / vacuous3）+ 两条缺陷推导格留痕 | ✅（行使层有 ⛔/⚠️ 如实登记） | 本文件 §3；`evidence/w24-matrix-summary.json`；`_w24/shots/` |
| 步 4 | 链内迭代 11 轮，每轮一行证据，含 3 处真实「改→再测」修正 | ✅ | 本文件 §4；`_w24/evidence/iterations.jsonl` |
| 步 5 | 收尾契约：P0/P1 收口 + 台账落笔 + 制品 + 脱敏 + 推送 CI | ✅（CI run 链接见 §5.4，推送前复跑守卫见 §2） | 本文件 §5.1–§5.5 |

> 步 3 的 ✅ 指「矩阵按判据行使并如实四态登记」这一动作完成，不等于所有格通过；
> fail / vacuous / partial 格在 §3 逐格列明，不写成成功。

### §5.2 明确未做（逐条带挂账号 · 与 §1.3 同源）

| # | 不做项 | 挂账号 |
|---|---|---|
| 1 | 拆分三方整包 / 任何 `.kt` 增量 / 调行数与测试数上限（含修两条新缺陷所需余量腾挪） | W20-02（承接 W16-03）、W24-01、W24-02 |
| 2 | journal / FreezeClock 二选一代裁决（本波段登记第 19 次顺延、两代数口径不统一） | W24-03 → W21-03 → W20-01 → W9-05 |
| 3 | 设备侧 1M 真压（双阻断未解，只做 1M 档位读数） | W20-05 → W9-11 |
| 4 | MCP 明文联调 / 放开明文流量开关 / https 夹具 | W20-06 → W5-03 |
| 5 | git 历史清理 / force-push / rewrite history | W20-07 |
| 6 | 四项用户终局裁决代做 | W20-08 |
| 7 | 安全波整包 | W20-04 |
| 8 | 台账同物多名并号 / 台账模板改动 | W20-10、W20-16 |
| 9 | 守卫侧修复 / G20 扫描面扩展 / 六行漏号补齐 / 别名机制 | W20-17、W20-03 |
| 10 | 取证设施（驱动/脱敏脚本）入仓 | W20-14 |
| 11 | 加长 system / 音频 / `core-device` 独立波 / 图片预算四条 | W20-11 |
| 12 | 非对话模型进 chat 端点矩阵 | W21-05 |
| 13 | PC 侧免费节点结论升级为「设备侧可用」 | W20-12 |
| 14 | 历史制品正文重出 / 更早各波正文改写 | W21-02 → W20-15 |
| 15 | 摘 VACUOUS / 观测缝 / 工具记账上线（需 `.kt`） | W21-06、W20-03 |
| 16 | `@Test 818→≥850` / 改测试数上限 | W20-09、W20-02 |
| 17 | N-1 白名单行为面「修复」（第 6 次证伪，拒绝对不存在的 bug 动刀） | W20-09（缺陷清单域） |
| 18 | 把手动/自动压缩 `dropped>0` / `applied=true` 写成硬出口判据或达标 ✅（含把 `already_compressed` 写成压缩主线闭环证据） | W21-04 |
| 19 | 把两条新缺陷推导写成「已复现事实」 | W24-01、W24-02 |

### §5.3 OPEN 挂账（触发信号 + 缓解动作 + 挂账号 · 唯一真值源 = `docs/06-deferred-ledger.md`）

| 挂账号 | 状态 | 触发信号 | 缓解动作 | 本波证据 |
|---|---|---|---|---|
| W24-01 | `OPEN` | 浅/深色 × 收起/展开含 reasoning 气泡出现整块黑面板，或 JVM 零尺寸进 lens shader 得 NaN | 展开态改实底容器 + 文字恒白族；`AnimatedVisibility` 移出玻璃节点或 `heightIn(min=1.dp)`；Lens 零尺寸退纯 blur + AGSL normalize 加 epsilon；`overlayPanel=true` 接线 | 本波浅色 3 帧未复现，深色未测；源码路径 live；台账行带必红判据 |
| W24-02 | `OPEN` | 200+delta 关流仍 `Completed(LENGTH)`、429/排队后半截无提示、SSE error 帧无上报 | EOF 无终帧改 Retryable；error/retry 帧映射 RATE_LIMIT/SERVER；Truncated 转可见提示 + 重试入口；回滚同 idempotencyKey；首字节 TIMEOUT 维持不重试 | 本波 dots/north 无产出归环境侧、未跑 MockWebServer；台账行带必红判据 |
| W23-03 → W9-05（journal） | `OPEN` | 用户未给显式二选一裁决 | 用户拍板并统一代数；不关号不代裁；本波登记第 19 次顺延 | §1.1 #12；台账老行原样 |
| W23-04（真机矩阵未达格） | `OPEN` | 节点复活 / 新夹具可铺 | F5 需 `.kt` 或放宽 file_read；F7 需外部端点；⛔vacuous 不冒充通过 | §3 F6/F7 行；本波复证 below_trigger |
| W20-14（取证设施入仓） | `OPEN` | 换机 / 清目录致仓外证据不可再生 | 取证设施入仓与拆分波同车（老号口径）；本波断言补丁仍落仓外 | §2 制品生成器路径；`make_evidence_w24.py` 仓外 |
| W20-09（N-1 笔误键） | `OPEN`（证伪域） | 再有人提「白名单笔误键致保存链拒合法键」 | 引索引行证伪（完整键自初始即在 + 行为测试已在）；不修不存在的 bug、不加门禁 | §1.1 #11；本波第 6 次证伪 |

> 完整挂账（含更早各段）以台账为准；本表只列本波涉及 / 新开的行。台账只新增本波段两行 + 被点名老行
> 行内注记，不删、不改状态、不写已核销、不并号。

### §5.4 文档回写 diff

| 文件 | 变更 | 性质 |
|---|---|---|
| `docs/analysis/w24-acceptance.md` | 新建：§1 裁决/做什么/不做/前置、§2 闸门、§3 矩阵、§4 迭代、§5 收尾契约 | 本波新增 |
| `docs/handoff-20261118-wave24.md` | 新建：交接摘要（零复合编号、不写推断为事实、不写下一链） | 本波新增 |
| `docs/06-deferred-ledger.md` | 新增 W24 段两行（`OPEN`）；W23-01 证据列「全树 0 命中」订正为「缺陷形态 0 命中（注记引用除外）」+ 行内注记 | 本波新增段 + 老行行内注记（状态不变） |
| `docs/analysis/w23-acceptance.md` | §5.4 推送 2 行 + 文末链接段补 `44123791935`（公开 API 复核 success） | 最小结构修复（收尾义务） |
| `evidence/w24-matrix-summary.json` | 新建：schema v1 + 12 cells + 11 iterations + 11 断言必红变异 + P1 修复字段 | 本波新增（仓根非 docs） |
| `docs/analysis/w23-acceptance.md`（Earlier） | 其余更早各波正文 / 制品 / W23 历史 matrix JSON 一律不动 | 不回写 |

### §5.5 脱敏 / 零凭据结果 + 交给外部审查团队的制品清单

**脱敏与零凭据（先正控后 0 命中）**：

| 项 | 四态 | 证据 |
|---|---|---|
| 敏感形态正控自证 | ✅ | 临时检测器 5/5（PAT / ghp / 设备序列号 / 本机路径 / 凭据文件名）；仓外正式扫描器 `self_audit` **11/11** 全响后才采信 0 命中 |
| 制品零命中 | ✅ | `scan_w24.py` 扫 9 份文本制品（验收物 / 交接 / 台账 / 矩阵 JSON / 证据 JSON+JSONL）= **CLEAN，0 命中**；正控 11/11 后判 |
| 端点标识 | ✅ | 制品中端点一律 8 位不可逆指纹（sha256(UI 显示名)[:8]），零 URL / 零 modelId 真值以外的凭据 |
| 截图 / 日志 | ✅ | 原始 `*_raw.png` 本地仓外；回灌证据仅 `_w24/shots/*.png`（不含凭据 / 未打码账号）与 deviceLogTail（tail-only 脱敏），不回灌整段 logcat |
| 凭据落盘 | ✅ | 密钥只留设备侧 blob（6 个 id，内容不读不落盘）；仓库与文档零凭据字面量；PAT inline 一次性、用后清 remote |

**交给外部审查团队的制品清单**（不含会话历史 / 凭据 / 端点标识 / 建议下一链）：

| 制品 | 路径 | 内容 |
|---|---|---|
| 验收物（完整版） | `docs/analysis/w24-acceptance.md` | §1–§5，1→5 各步结果 + 四态 + 证据路径 + CI run 链接（§5.6） |
| 交接摘要 | `docs/handoff-20261118-wave24.md` | 定位 / 裁决收敛 / 未做 / 挂账 / 制品清单；零复合编号、不写推断为事实、不写下一链 |
| 挂账台账 | `docs/06-deferred-ledger.md` | W24 段两行（触发 + 缓解 + 必红判据）+ 老行注记；真值源 |
| 机器可读矩阵制品 | `evidence/w24-matrix-summary.json` | schema v1 + 12 cells + 11 iterations + 11 断言必红变异 + `assertion_source`/`runner_sha256`/NA 白名单/truncated note |
| 脱敏截图（证据引用） | `_w24/shots/*.png`（仓外，路径作为证据引用列在 §3/§4） | 逐格判据截图；原始 raw 本地、回灌脱敏；外部按同路径取阅需执行席导出 |
| 迭代明细 JSONL | `_w24/evidence/iterations.jsonl`（仓外，内容已并入制品 `iterations[]`） | 11 轮 action/observation/state/attribution/evidence_ref/caveat |

> 制品清单刻意不含：会话历史、任何凭据或凭据文件名、端点 URL / 真 key、「建议下一链做什么」。
> 仓外取证目录（`_w24/`）按老号 `W20-14` 口径不入仓（换机不可再生风险如实挂账）；制品 JSON 本体
> 已含断言来源与运行器指纹，外部可核「哪个脚本哪版判的红」。

### §5.6 CI / 提交序列（唯一真值源）

| 项 | 值 |
|---|---|
| 推送 1 | `9558d5a..c73c3cf HEAD -> main`（commit `c73c3cf`，5 files：本波验收物 + 交接 + 台账 W24 两行与 W23-01 注记 + W23 §5.4 最小修复 + 仓根非 docs 制品 `evidence/w24-matrix-summary.json`〔12 格〕）；暂存判据逐文件 add，两份历史未跟踪源码日析全程 `??` 未入库；dry-run RC=0 → 实推 RC=0，inline PAT URL 一次性使用、`git remote -v` 复核干净 URL 0 命中；noreply 身份（全仓一致）；不 force-push、不 rewrite history |
| Build run | run `44143562756` **completed success**（event=push，由仓根非 docs 制品自身真实触发；五 job 经公开 API 逐项复核全 success：Lint (baseline gate) / Assemble Debug (JDK 21) / Unit tests / Arch guard (26 guards + mutation selftest) / Secret scan (gitleaks + trufflehog)） |
| Secret scan run（独立） | 同一 push 的独立 `secret-scan.yml` run `44143562664` **completed success**（无 `paths-ignore`，覆盖同一 commit） |
| 最终树守卫复跑 | 推送前文档落盘后复跑 `result=PASS ok=27 vacuous=1 violation=0 fault=0`、rc=0；G20 OK（112 handoff ID 全在台账、CLOSED 行证据非空）、MISSING=`[]`（`_w24/gates_g3_final.safe.log`） |
| CI 红次数 | **0**（上限 2，未触） |
| 推送 2 | 本提交（纯文档回填 §5.6 真实 commit 与 CI run 链接 + 交接章节引用订正）：Build **0 run**（`paths-ignore` 既定口径，判据未行使，不算绿也不算红）；独立 Secret scan 覆盖同一 commit，公开 API 复核 run `44144142295`（name=Secret scan / status=completed / conclusion=success / event=push / head_sha=`846301c`）〔本波收口修订：W25 跨提交回填真实 run 链接，不改「本波不再第三次推送以避免自指循环」裁决〕 |
| 推送次数 | **2**（上限 3，未触顶；推送 1 含制品触发 Build 五 job 真跑、推送 2 纯文档回填链接） |
| 归因 | 无构建失败 ⇒ 四分类未触发；免费节点限流/无响应、档位夹具几何夹紧、无外部 MCP 端点判环境侧/前置缺失，不写成应用 bug；推送 1 含制品 ⇒ Build 有真绿 run；推送 2 纯文档 ⇒ Build 0 run 属既定口径，不写「构建再次通过」 |

**run 链接**（公开 API 逐项读 status / conclusion，零凭据）：
推送 1 Build：https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44143562756
推送 1 Secret scan（独立）：https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44143562664
推送 2 Secret scan（独立）：https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44144142295〔本波收口修订：W25 跨提交回填，公开 API 复核 success@846301c〕

> 本波零 `.kt` 改动 ⇒ 「CI 全绿」指 Build 五 job + 独立 Secret scan 结论 success，
> **不等于**真机矩阵全格通过（§3 如实登记 fail / vacuous / partial 的格），也不等于两条新缺陷已复现。
> PAT inline 一次性使用后 remote 已复核干净〔本波收口修订：原句「须轮换」属未闭环命令式，按报告甲 P1-5 订正为已闭环动作表述；密钥轮换属用户侧密钥管理动作，不在仓内写未完成命令〕。
