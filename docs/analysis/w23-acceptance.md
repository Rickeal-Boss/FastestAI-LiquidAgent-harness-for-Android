# W23 收尾契约验收物 — 四份外审裁决 + 新节点×多上下文×多功能面真机矩阵（2026-11-17）

> 基线 `eeb4cbc`（W22，`main`；本地与远端 HEAD 一致，零 `.kt` 改动）。
> 上游依据：用户 2026-11-17 指令（读记忆 / 上一波 handoff / OPEN 挂账行〔现役仓 `harness-v2/`，
> **非旧仓** `Android-Agent/`〕，列出「做什么 / 明确不做」写入验收物第 1 节；读四份众审查人原始报告并
> **裁决分歧、不改方案大体方向**；用用户给的 API〔用户本单免费节点清单（凭据文件名不落盘）+ openrouter
> `dots-3-note-preview:free` 512K〔用户标注工具+视觉双通，设备实测仅 TEXT，以真机为准〕+ `north-mini-code:free`，其余走 build.nvidia.com〕
> 测多模型 × 不同上下文〔直至 1M〕× 多模态输入 / 工作空间文件读写 / skill / MCP /
> 手动与自动上下文压缩；先本地 UI + 功能测试 + adb 调试 → 真机测试 → 没问题推仓库 + CI 构建；
> 密钥留真机、零凭据字面量；真机 adb + 脱敏截图回灌 + 边测边改链内循环；每次推送留可审计 commit
> 与 run 链接；不 force-push、不 rewrite history；触顶收尾；handoff 不把推断写成事实；
> 收尾契约验收物含 1→5 各步结果〔四态 + 证据路径，CI 绿附 run 链接〕、明确未做〔逐条带挂账号〕、
> OPEN 挂账〔触发信号 + 缓解动作 + 挂账号〕、文档回写 diff、交外部审查制品清单；本次任务止于交接）。
> 判定四态 ✅ / ⚠️ / ⛔ / ⛔vacuous。**「未触发」≠「已验证通过」；「测试全绿」≠「口径被验证」；
> `rc=0` ≠ 产物已重生成；PC 侧结论不得写成设备侧可用；档位读数 ≠ 生效窗口 ≠ 1M 真压；
> 写「X 未发生」前先自证判据会响。**
> 密钥纪律：端点凭据**只在设备侧**；本单 / 工具 schema / 工具结果 / 日志 / 截图 / 交接 / 记忆写入
> **零凭据字面量**；端点一律以不可逆 8 位指纹出现（指纹 = UI 显示名 sha256 前 8 位）。
> 设备序列号、本机绝对路径、凭据文件名、未打码账号字段一律不出现。
> 完整版即本文件；`docs/handoff-20261117-wave23.md` 只做交接摘要，冲突时以本文件为准。
> 本文件不写「建议下一链做什么」。
> 🔴 编号纪律：**交接文档**（`handoff-*.md`）严格零「W 加数字加减号加数字」复合编号字面量——
> G20 守卫只用 `find docs -maxdepth 1 -name handoff-*.md` 扫描交接文档（`arch-guard.sh:1334/1342`），
> **不扫 `docs/analysis/` 验收物**。本文件 §1.3 等处的挂账号是**台账溯源**，合规且必要；
> 故本文件不追求零复合编号，交接摘要侧才用中文段位表述（「上一波段第 3 行」）。

---

## 1. 本波范围：做什么 / 明确不做（执行前钉死）

## §1.1 四份外审报告分歧裁决表（不改方案大体方向 = 用户指令面优先）

> 输入：报告①《harness-w22-review》、报告②《W22 复合波深度审查与 W23 排期裁决》、
> 报告③《W22 审查与挂账分析_20261117》、报告④《FastestAI-LiquidAgent-harness W22 深度审查与
> W23 路线建议_2026-11-17》。四值裁决 = 采纳订正 / 顺延留痕 / 证伪 / 部分采纳。
> 「改方向」列全为「否」——四报告排期主张整包顺延留痕、不取消不推翻不代做；矩阵存续决策包维持
> 台账原样 `OPEN`；四项用户终局裁决（journal 二选一 / ≥3 端点终局 / 占位符口径 / `P2-3`）只留痕不代做。

| # | 议题 | 四报告主张 | 现役回源结果 | 裁决 | 改方向 |
|---|---|---|---|---|---|
| 1 | 排期路线（矩阵触顶→代码波 / 拆分波 / 决策清点先） | 报告①§五「零代码矩阵真的到顶了」；报告②§六「继续零 `.kt` 收口第四车 + 一个用户裁决前置 / 第 1 车代码波」；报告③§五「三项决策清点一次性」；报告④§四「矩阵正式触顶申报 + 主菜拆分缺陷车」 | 用户本单仍给新节点（openrouter 两支 + build.nvidia.com 其余模型）与密钥、明确要矩阵测试；指令面优先出处（上一代波验收物 §1.4）成立；矩阵存续决策包台账原样 `OPEN` | **顺延留痕**：四报告排期主张整包顺延，不取消不推翻不代做；不因「触顶申报」关矩阵、不升格拆分波 | 否 |
| 2 | 第 0 车零 `.kt` 收口 · P0-1 制品可复核性 | 报告②P0-1：上一波制品 schema 退化（无 `schema` 版本 / 无 `cells[]` / 无 `truncated_fields` / 无 `iterations[]` 明细 / 无 `evidence_ref`；断言列 10 条但 `mutation_selftest` 仅抽样 5 条，`F_new_face_must_be_declared` 对当前制品必然绿）；要求恢复并必红样本入仓 | 报告①§二.5/报告④§一.2 反向确认上一波出厂断言升级属实；现役树 `evidence/w22-matrix-summary.json` 为上一波制品，缺陷形态为收尾契约自身产物；修它是收尾义务完成，非改方案方向 | **部分采纳**：本波**新制品**恢复 `schema` + `cells[]` + `truncated_fields` + `iterations[]` 明细 + `evidence_ref` + `attempt_states`，`F_new_face_must_be_declared` 必红样本入仓、`mutation_selftest` 抽样列明且含 F、`B_required_fields` 对 `NA` 三格显式白名单；**历史制品不重出不覆盖**（边界见 §1.1 注 1）；拒绝升格独立制品波、拒绝写死派生数为出口判据 | 否 |
| 3 | 第 0 车零 `.kt` 收口 · P0-2 脱敏回退 | 报告②P0-2：上一波台账证据列含凭据文件名，破本波自设判据；要求去字面量、把凭据文件名形态加回脱敏规则集并用该串本身 positive control、实跑日志入仓 | 主理人自检已确认上一波制品证据列含凭据文件名；上一波验收物抬头明写「凭据文件名不出现」；现役树未重扫字面量（避免把该串写进本稿） | **采纳订正**：作为收尾义务零 `.kt` 并入——本波新制品/新台账行零凭据文件名、脱敏规则集补该形态 + 该串自身 positive control、全树 6 类敏感形态 0 命中；不追改已 `CLOSED` 的收口行语义 | 否 |
| 4 | 第 0 车零 `.kt` 收口 · P1（面数 15/16、收口行原句、推送链接、错数） | 报告②P1-1~P1-4、报告①§三：上一波 §3.1 汇总 16 行口径与 §5.1 步 3「15 格」不自洽；收口行缓解动作原句「全树 0 命中」按字面不成立、排除规则与分类日志在仓外；推送 2 自身 run 链接缺；行数/断言数错数 | 均属上一波自己的收尾契约缺陷，现役可核；修复=收尾义务完成 | **采纳订正**：零 `.kt` 并入本波收尾义务——面数归一（声明面数 = 表行数 = 汇总三项之和）、收口行原句改「缺陷形态 0 命中（注记引用除外）」+ 分类日志入仓、一次纯文档提交补推送 2 run 链接、错数订正；每处带「本波收口修订」标注；只修上一波本体与仓外生成器 | 否 |
| 5 | E1/E2 题面回读定级偏松 | 报告②P1-3：`reply=PONG4`（caveat 自承题面被截）、「未逐字回显题面 token」仍判 ✅，绕过收敛硬判据，应降 ⚠️ 或单列「往返复验」面 | 现役上一波验收物四态与 caveat 如实；历史四态不因外审追改 | **部分采纳**：采「从严定级」方向——本波新格以 `typed_hint` 逐字 + usage 上屏 + 发送键 bounds 为收敛判据，未逐字回显一律 ⚠️ 或单列往返复验面；历史格四态不改、不重判 | 否 |
| 6 | N-1 笔误键（第 5 次被翻出） | 报告③§二「第四次裁决定谳 + 5 分钟工程动作 + 一行测试」；报告①§四.3「第三次同题证伪」；报告④§一.5「第三次翻出第三次证伪」 | 现役树 `EndpointAdvancedValidation.kt:68` 就是完整键、自初始即在；`:114` 白名单拒绝判断；`EndpointCapabilitiesTest.kt:256` allowlist 强制行为测试在案；报告②自列「:68 是完整键」恰反证其「集合不含完整键」前提；报告③承认「自初始即存在」无争议；**用户本单未给期望行为拍板** | **证伪 + 顺延留痕**：证伪「现存 bug / 保存链必拒合法键」前提，拒绝修不存在的 bug、拒绝新增门禁、拒绝改白名单；「+1 行测试」属 `.kt` 且缺用户期望行为裁决，顺延留痕、本波不做；「5 分钟工程动作」以用户拍板为前置，不代裁 | 否 |
| 7 | journal 顺延代数（上一波记第 17 次，本波第 18 次） | 报告①§二「第 17 次」；报告②§五「第 17 次」；报告③§三.3「两代数口径 17/18 并列」；报告④§四.5「18 次前写累计成本注记」 | 上一波台账第 3 行记第 17 次顺延，承接链老号全数原样 `OPEN`；用户本单仍未给二选一裁决 | **顺延留痕（不代裁）**：本波段登记第 18 次顺延、两代数口径并列交用户；老行不改状态不并号；报告④「累计成本注记」以台账行内注记零成本并入，不写成已完成 | 否 |
| 8 | 报告③「三项待用户拍板清点」（N-1 期望行为 / 矩阵存续四选一 / journal 二选一） | 报告③§五.1 要求一次性清点 | 三项均属用户终局裁决域，现役无用户拍板 | **顺延留痕（不代裁）**：只留痕、不软化、不关号；本波不把清点动作当成裁决完成 | 否 |
| 9 | 报告③「定谳不再辩论 = 5 分钟工程动作」 | 报告③§二.4：用户拍板期望行为 + 一行测试 + CI 守卫终结第五次辩论 | 拍板未给、一行测试属 `.kt`，两项前置均不成立；报告③自己也承认四轮「全是字面量考古，没有一次是行为判定」 | **证伪 + 顺延留痕**：证伪「可直接动刀」前提（缺期望行为裁决），需 `.kt` 的本波不做；顺延留痕至用户提供期望行为 | 否 |
| 10 | F5/F6 压缩夹具攻 `dropped>0` | 报告①§三 P1/§四「32K + 20 轮 ×1k 字符短问答夹具是零代码最后一块硬格子」；报告③§三.2「结构性两难」；报告④§四「剩余格全卡外部前置」 | 压缩主线老根因全部 live（`COMPACT_TRIGGER_CHARS=50_227`、`alreadyCompressed` 单指针、`maxReadToolOutputChars=16_000` 几何夹紧；回源已核）；能力本身存在（历史 `dropped=238 kept=64`）；修单指针/放宽上限属 `.kt` | **部分采纳**：采「32K + 新会话逐条短消息夹具」方向，本波零 `.kt` 行使 `dropped>0`；**不把 `dropped>0` / `applied=true` 写成硬出口判据或达标 ✅**；夹具/节点不配合如实四态（⛔vacuous / ⚠️） | 否 |
| 11 | 报告④两页零成本 docs（已证伪处方索引 + 收口修订边界成文） | 报告④§四.2：①「外审已证伪处方索引」按「处方原文/证伪依据 file:line/波次」三列落一页；②「收口修订边界」四条成文（仅最近一波/逐处标注报告编号/回源证据路径/更早冻结） | 证伪结论现只活在当波验收物、无可 grep 索引，重复成本已 ≥ 建索引成本（报告④§一.5）；两轮收口实践样本（上一波→前两波五处、前两波→前三波九处）已齐；两页均零 `.kt` | **采纳订正**：零成本并入本波——索引页与边界页写进本波验收物附注（不新建独立文档，避免改方案大体方向）；索引只登记现役回源的证伪结论、每条带 `file:line`；边界页固定四条；不写「建议下一链做什么」 | 否 |
| 12 | 报告④「老号关闭 + 断言体系入模板」 | 报告④§四.3：关闭模板老号、把出厂断言写进波出厂清单 | 改台账模板须独立成波（台账 §零 / 老号证据列）；现役老号原样 `OPEN` | **顺延留痕**：模板改写独立成波，本波不关号、不改真值源模板；断言体系只在本波制品实践，不写进模板文本 | 否 |
| 13 | G19 摘 VACUOUS / `@Test 818→≥850` / 工具记账观测缝 / 拆分三方 | 报告①§三 P3「最小观测缝」；报告②§六第 1 车「先拆三方再开压缩主线、补测抬 @Test 须改常量」；报告③§五.2「拆分与 F5/F6 同车」；报告④§四.4「主菜一车」 | G10 余量 4/4/5、`ChatViewModel` 500/500、`EXPECTED_TESTS=818`（回源已核）、G19 守空集既定 ⛔vacuous；工具记账需 `.kt`；拆分需动代码与守卫 | **顺延留痕**：四项整包顺延，需 `.kt` 或改守卫/改测试数，本波不做；列 §1.3 明确不做；不抬 `@Test`、不摘 VACUOUS、不放宽行数上限 | 否 |
| 14 | 报告②第 1 / 2 车（拆分 + 代码收格 / 缓存主线 + 安全波 / 取证设施入仓） | 报告②§六第 1、2 车清单 | 全属改方向域，与用户本单矩阵指令冲突 | **顺延留痕**：整包顺延，不取消不推翻；取证设施入仓须先裁「新增仓根非 docs 路径」是否改方向 | 否 |

> 裁决合计 14 条：**采纳订正 3 / 顺延留痕 6 / 证伪 2 / 部分采纳 3**（#6 主判证伪、附顺延留痕）。
> 全部 `改方向 = 否`。
> **本波最硬裁决仍 = N-1 第 5 次证伪**：完整键自初始即在、allowlist 行为测试已在；报告③四轮轨迹表
> 自承「四次全是字面量考古」，恰为本题「采纳外审处方前先 grep 现役树」铁律再 +1 的注脚。
> 注 1（历史制品边界，一次钉死）：上一波段验收物允许最小结构修复（面数归一 / 收口行原句 / 推送链接 /
> 错数），每处带「本波收口修订」标注；历史制品 JSON 与更早各波正文、制品一律不重出不覆盖；台账只新增
> 本波段行 + 被点名老行行内注记（不改状态、不改原句）。
> 注 2（收尾义务非改方向）：#2/#3/#4/#11 修的是「上一波自己的收尾契约缺陷」，属本波收尾义务的完成，
> 不构成方案大体方向变更；据此并入本波（零 `.kt`），不升格独立排期波。

---

## §1.2 做什么（本波判据 · 沿用上一波 12 项框架，端点轴/档位轴措辞按本单新节点更新）

| # | 判据 | 行使方式 | 出处 |
|---|---|---|---|
| 1 | 范围 §1：四报告逐条裁决 + 做什么/不做 | §1.1 裁决表 + §1.2/§1.3 写入本波验收物第 1 节；交接侧台账号转中文段位 | 用户指令 + 四外审 |
| 2 | **端点轴**：新节点 × 多模型（openrouter 的 `dots-3-note-preview:free`〔512K，用户标注视觉双通但设备实测 `inputModalities=['TEXT']`，以真机为准〕与 `north-mini-code:free`，其余走 build.nvidia.com 各模型；沿用上一波段 dots / north / step5 / mistral / gemma / deepseek 观测） | 设备侧 fast-fail 预筛后真往返收敛取证；指纹 = UI 显示名 sha256 前 8 位；走密钥复用双路径（UI 编辑留空保留语义 / 信封按 id 复制二选一）；零搬运、零明文落盘；deepseek 不在设备池即 ⛔vacuous，gemma 限流即 ⛔ 环境侧 | 用户指令 + 报告①③④；端点池前置见 §1.4 |
| 3 | **档位轴**：多上下文（32K / 256K / 512K / 直至 1M **读数**，非真压） | 档位真切换回读 `switch_verified=true`；`dots-3-note-preview:free` 512K 档与 build.nvidia.com 模型的 1M 档分别取读数 + 计量条；**档位读数 ≠ 生效窗口 ≠ 1M 真压**；`hard=` logcat 指纹未捕到如实登记 | 用户指令 + 报告③；真压双阻断见 §1.3 |
| 4 | 多模态输入 | 真图 + `scan_file` 触发媒库 + SAF 选图 chip + 语义问答；先验 `inputModalities`（如 NV-VL 名含 VL 但仅 TEXT⇒图片 chip 灰），换可用 IMAGE 端点（设备池唯一含 IMAGE = mistral `d5022055`，上一波段靠它破零）；`dots-3-note-preview:free` 用户标注双通但实测仅 TEXT⇒先按现状验 chip，灰则在设备侧按用户声明补 IMAGE 元数据后验（非仓内、非平台开关），真图语义逐字命中才算 ✅，否则 ⛔ 环境侧，带 caveat 不写用户标注为已验证 | 用户指令 + 报告④；端点池前置见 §1.4 |
| 5 | 工作空间文件读写 | `file_write`/`file_read` 严格 `ls -1` 差集 + ns mtime + 内容逐字 sentinel + 审批框 exact args + 反背题对照 | 用户指令 + 报告④ |
| 6 | skill 功能面 | 设备侧 `skills/` 资产注入 → 冷启动「来源：file」→ `@name` 点选 → sentinel 逐字 + 横幅 + usage 增量；无 `skill_load` 记账即以 UI 副作用/sentinel 为准并带 caveat | 用户指令 + 报告④ |
| 7 | MCP 现状确认 | 顶栏返回箭头绕系统 BACK → MCP 列表页可达 + 空态截图；导航（✅）与行使（⛔vacuous）分开落账；平台开关 G15b 明文阻断不放开 | 用户指令 + 报告③；平台开关见 §1.4 |
| 8 | 手动 + 自动上下文压缩 | 32K + 新会话逐条短消息夹具；手动 `dropped>0` 双向断言、自动 `applied=true+dropped>0`；评估链实锤保留但不冒充达标；不写硬出口判据，夹具/节点不配合如实四态 | 用户指令 + 报告① P1 / 报告④ |
| 9 | 本地先行 + adb 调试 + 链内迭代 | 先本地 UI/功能测试 + adb 调试；真机「改→编译→真机→再看」链内循环；每轮追加一行证据（action/observation/state/attribution/evidence_ref/caveat） | 用户指令 |
| 10 | 收尾契约：零 `.kt` 账实/制品收口 | 修上一波本体 P0-1（面数归一、五处同步）/ P0-2（脱敏 + 排除规则）/ P1（推送链接、错数）；本波新制品恢复 `schema`/`cells[]`/明细/必红样本；历史制品不重出；两页零成本 docs 并入验收物附注 | 报告①第 0 车 + 报告② P0-1/P0-2 + 报告④§四.2 + 收尾义务先例 |
| 11 | 四闸门 + 推送 + CI | G1–G4 串行实跑后推送；每次 commit + run 链接；脱敏 positive control 先自证会响；凭据 inline 一次性、用后清 remote；不 force-push、不 rewrite history | 用户指令 + 仓库 `AGENTS.md` 七/八 |
| 12 | 脱敏 + 零凭据 + 零复合编号 + 四态纪律（全局） | 端点 8 位指纹；产物零 PAT/真 key/本机路径/设备序列号/未打码账号/凭据文件名；交接文档零复合编号字面量；未触发 ≠ 通过、推断带 caveat；每次落盘前后各扫一遍 | 用户指令 + 守卫自指陷阱 + 报告② P0-2 |

---

## §1.3 明确不做（逐条带挂账号，全部原样 `OPEN`）

> 沿用上一波段「明确不做」18 条挂账号前缀体系；挂账号已对台账真值源（现役 `docs/06-deferred-ledger.md`）
> 逐条核对。本表进 `docs/analysis/`（G20 不扫 analysis），故写完整挂账号；转抄进交接文档时改中文段位。

| # | 不做项 | 挂账号 |
|---|---|---|
| 1 | 拆分三方整包 / 任何 `.kt` 增量 / 调行数与测试数上限 | W20-02（承接 W16-03） |
| 2 | journal / FreezeClock 二选一代裁决（本波段登记第 18 次顺延，两代数口径不统一） | W21-03 → W20-01 → W9-05 |
| 3 | 设备侧 1M 真压（双阻断未解，只做 1M 档位读数） | W20-05 → W9-11 |
| 4 | MCP 明文联调 / 放开明文流量开关 / https 夹具 | W20-06 → W5-03 |
| 5 | git 历史清理 / force-push / rewrite history | W20-07 |
| 6 | 四项用户终局裁决代做（journal / ≥3 端点终局 / 占位符口径 / `P2-3`） | W20-08 |
| 7 | 安全波整包 | W20-04 |
| 8 | 台账同物多名并号 / 台账模板改动（采「紧迫性下降」，不单独排期） | W20-10、W20-16 |
| 9 | 守卫侧修复 / 扫描面扩展 / 别名机制 | W20-17、W20-03 |
| 10 | 取证设施（驱动/脱敏脚本）入仓——本波断言补丁只落仓外 | W20-14 |
| 11 | 加长 system / 音频 / `core-device` 独立波 / 图片预算四条 | W20-11 |
| 12 | 非对话模型（视频生成 / 视频超分）进 chat 端点矩阵 | W21-05 |
| 13 | PC 侧免费节点结论升级为「设备侧可用」 | W20-12 |
| 14 | 历史制品正文重出 / 更早各波正文改写（本波只修上一波本体 + 仓外生成器） | W21-02 → W20-15 |
| 15 | 摘 VACUOUS / 观测缝 / 工具记账上线（需 `.kt`） | W21-06、W20-03 |
| 16 | `@Test 818→≥850` / 改测试数上限（补测触计数） | W20-09、W20-02 |
| 17 | N-1 白名单行为面「修复」——现役完整键已在、测试已在，拒绝对不存在的 bug 动刀、拒绝新增门禁 | W20-09（缺陷清单域，仅登记不修） |
| 18 | 把手动压缩 `dropped>0` / 自动压缩 `applied=true` 写成硬出口判据或达标 ✅ | W21-04 |

---

## §1.4 设备硬件可行性 + 矩阵前置条件表（方案席独特维度）

### 1.4.1 目标设备与已知怪癖（知识库 `harness-android-dev` / `references/real-device-forensics.md`）

- **目标机**：OPPO PDRM00（设备标识 OP4EA7），Android 13（SDK 33，知识库记录，**待本波真机回读核验**，不写成已验证），纯 API Harness（模型云端、无端侧模型，故本波不涉及端侧引擎判据）。
- **灭屏挂起**：`SCREEN_STATE_OFF` / Dozing 下 `pm install-commit` 永久挂起（无输出无退出码），`adb install` 返回 -1。前置：先 `input keyevent KEYCODE_WAKEUP` + `screencap` 非全黑才算存活（AOD 仍可能黑屏）；安装走 `install-create`/`install-write`/`install-commit` 三步法。
- **adb 通道**：一律有线；含路径命令加 `MSYS_NO_PATHCONV=1`；adb 用工作空间绝对路径（`_j2env/sdk/platform-tools/adb.exe`）且指定 server 端口（本会话 `-P` 运行时取值，不写死）；此路径/端口属仓外执行细节，转抄交接文档时以占位表述，不写本机绝对路径。
- **Compose bounds**：`uiautomator dump` 对 Compose 组件 bounds 不可靠，点按判据以 journal / agent log 为准，不作唯一判据；多步 UI 每步落判据、失败即停。
- **日志与题面**：`logcat -s` 漏 native 行 ⇒ 设备侧全量落盘事后 pull；`input text` 非 ASCII 触发设备侧 NPE ⇒ 题面只用 ASCII（空格 `%s`）。
- **二进制读写**：`adb exec-out` 读、push → `/data/local/tmp` → `run-as cp` 写；端点切换走 DataStore（`active_endpoint_id` 等长字节替换 + force-stop + exec-out 读回）。

### 1.4.2 平台开关清单（判据依赖平台行为前先列）

| 平台开关 / 通道 | 现值（现役/知识库） | 对本波判据的影响 |
|---|---|---|
| G15b 明文流量（`usesCleartextTraffic`） | `false`（不为测试放开，老号挂账） | MCP 明文 http 行使判据被平台拒（`transport_error`）⇒ 只能取导航✅ + 行使⛔vacuous；**不放开开关、不造 https 夹具** |
| 档位选择器 + 计量条 | 设备端档位可切、计量条回读（上一波段 512K/256K/1M 已回读） | 1M「读数」依赖档位切换到 1M + 计量条读数；真压双阻断未解，读数不冒充真压 |
| 密钥复用双路径 | UI 编辑留空保留 / 信封按 id 复制 | 新节点注入靠此二选一；零搬运、零 extraHeaders 明文注入；两路都断 ⇒ 新格如实 ⛔vacuous |
| 端点池（设备侧 `endpoints.json`） | 上一波段 6 端点；deepseek 不在池、mistral 唯一 IMAGE、gemma HTTP429 | 端点轴每格先查池；build.nvidia.com 新模型须 fast-fail 预筛后再真收敛 |

### 1.4.3 矩阵前置条件表（前置条件 / 责任方 / 可达性 / 四态预期）

| 前置条件 | 责任方 | 可达性（本波判定） | 四态预期 |
|---|---|---|---|
| 设备唤醒 + adb 三步法安装 + 非全黑 `screencap` | 执行席（本地/真机） | 可达（设备已装 `com.rickeal.liquidharness.debug`）；灭屏未唤醒即永久挂起 ⇒ 硬前置 | 前置项，非矩阵格；不满足则后续全 ⛔vacuous，不记 ✅ |
| 端点池核对：deepseek 不在池 | 用户侧（设备新增端点）/ 执行席（核对） | 当前不可达（无设备侧 deepseek 条目） | E4b 类格 ⛔vacuous（判据未行使），不臆造超时结论为 pass |
| 设备池唯一 IMAGE 端点 mistral `d5022055`（本波勘察实证 `['TEXT','IMAGE']`）+ `dots-3-note-preview:free` 用户标注双通但实测 `['TEXT']` 须先验 | 环境侧（节点存活）+ 执行席（先验） | 条件可达（mistral 为唯一 IMAGE，主多模态格靠它；dots 按用户声明补 IMAGE 元数据属设备侧核验，非仓内/非平台开关） | 主格 mistral 真图语义逐字命中 ⇒ ✅；dots 附加格按现状仅 TEXT ⇒ chip 灰即如实 ⛔/⛔vacuous，补 IMAGE 后语义命中 ⇒ ✅ 带 caveat；抖动 ⇒ ⛔ 环境侧不升级应用 bug |
| build.nvidia.com 其余模型 + openrouter 两支 fast-fail 预筛 | 用户侧（节点供给）+ 执行席（预筛） | 条件可达；限流/无响应/静默归环境侧 | 预筛通且真往返收敛 ⇒ ✅；限流（如 gemma HTTP429）⇒ ⛔ 环境侧；不在池 ⇒ ⛔vacuous |
| 1M 档位读数（非真压） | 执行席 | 可达（档位切换 + 计量条回读） | 回读「当前 1M」+ 计量条 ⇒ ✅（读数格）；真压双阻断未解 ⇒ 真压项不做，不写达成 |
| 工具记账观测缝（skill / `file_list` / 压缩判据） | 需 `.kt`（内核席），本波禁改 | 不可达（零 `.kt`） | 判据以 UI 副作用 / sentinel / 文件系统证据为准并带 caveat；无记账 ⇒ ⚠️，不写裸 ✅；工具记账上线顺延 |
| F5 夹具：32K + 新会话逐条短问答（20 轮 × ~1k 字符） | 执行席 | 零代码可试，但几何夹紧（`maxReadToolOutputChars=16_000` truncate + 32K 软预算 vs L1 硬闸 `50_227`） | `dropped>0` 双向断言成立 ⇒ ✅；返回「无可压缩内容」⇒ ⛔vacuous；夹具/节点不配合如实，不写硬出口判据 |
| MCP 真实行使 | 需平台开关放开或 https 夹具（G15b 现 `false`） | 不可达（无外部 MCP 端点 + 明文阻断） | 导航✅ + 行使 ⛔vacuous 分开落账；不放开开关、不冒充通过 |
| 两页零成本 docs + 收尾契约修复 | 主理人（台账单人落笔）+ 执行席（仓外生成器） | 可达（零 `.kt`） | 从 §1.1 #2/#3/#4/#11 收尾义务并入；schema/cells/必红样本/脱敏 positive control 实跑 ⇒ ✅；历史制品不重出 |

> 已验证（知识库/现役回源）：灭屏挂起、adb 端口与三步法、Compose bounds 不可靠、G15b `false`、
> 三模块余量 4/4/5、`EXPECTED_TESTS=818`、压缩三常量 live、N-1 完整键 live。
> 待验证（本波真机取）：OP4EA7 的 Android SDK 级别回读、build.nvidia.com 新模型可达性、
> `dots-3-note-preview:free` 视觉能力核验（用户标注双通/实测 TEXT-only，以真机为准）、F5 新会话夹具能否攻下 `dropped>0`、1M 计量条读数。
> 环境类 ⛔（限流 / 无响应 / 前置缺失 / 平台阻断）一律不归因应用代码；PC 侧结论不写成设备侧可用。

---

## §1.5 本波定位一句话 + 主理人通报要点

**定位一句话**：W23 = 「四外审裁决 + 新节点×多上下文〔直至 1M 读数〕×多功能面真机矩阵 + 上一波收尾契约缺陷次级收口」复合波的方案裁决阶段——用户指令面优先、零 `.kt`、裁决不改方向，矩阵纵深靠用户补给的新节点推进，历史制品不重出。

**给用户的主理人通报要点（≤5 行）**：
1. 四报告排期主张（触顶申报 / 转代码波 / 拆分波 / 决策清点先）整包顺延留痕、不改方向；矩阵存续与四项终局裁决维持 `OPEN` 不代裁。
2. N-1 笔误键第 5 次证伪：现役完整键自初始即在、allowlist 行为测试已在，拒绝修不存在的 bug、拒绝新增门禁；用户未给期望行为裁决前不动刀。
3. 收尾义务零 `.kt` 并入本波：制品可复核性（本波新制品恢复 schema/必红样本，历史制品不重出）、脱敏回退、面数与推送链接订正、两页零成本 docs；journal 本波登记第 18 次顺延、两代数口径并列交用户。
4. 设备可行性：OP4EA7（PDRM00）灭屏先唤醒 + adb 指定端口 + screencap 非全黑；deepseek 不在池、mistral 唯一 IMAGE、build.nvidia.com 新模型须 fast-fail 预筛；G15b 明文阻断不放开。
5. 判据纪律：档位读数 ≠ 生效窗口 ≠ 1M 真压；F5 夹具采方向但不写 `dropped>0` 硬出口判据；未触发 ≠ 通过、推断带 caveat；需 `.kt` 的拆分/观测缝/测试数整包顺延不做。


---

## 2. 本地闸门（实跑回填）

> 四闸门 G1–G4 由 CI 席**串行**实跑（基线 `eeb4cbc`，工作树零 `.kt`）。证据路径均为仓外
> `_w23/gates_g*.log`（原始）+ `gates_g*.safe.log`（脱敏副本）。派生计数不写死；`@Test` 基线 818。

| 闸门 | 实跑结果 | 四态 | 证据路径 |
|---|---|---|---|
| G1 出包 | `python os.remove` 删包两次（exists True→False 各一次）后强制 `assembleDebug`：RUN A / RUN B 均 BUILD SUCCESSFUL rc=0；`bytes=68407402`（两次相等）、`sha256=ca87a70a…`（两次相等）、mtime 22:01:18.890 / 22:01:40.967（不同）⇒ deterministic；bytes 与上一波段基线一致 = 零 `.kt` 预期 | ✅ | `_w23/gates_g1{,_runa,_runb}.log` + `.safe.log` |
| G2 fulltest | `tests=818 failures=0 errors=0 skipped=0`；83 个 `TEST-*.xml` 逐模块之和 818、覆盖 14/14 模块（settings include=14）；严格 `grep -rnE "^\s*@Test\b"`=818 ↔ `build.yml:135 EXPECTED_TESTS='818'` 双向相等；XML mtime 全本轮 22:03:03–22:03:10；**无** test-results 句柄锁告警 | ✅ | `_w23/gates_g2.log/.safe.log` |
| G3 arch-guard | 仓库根跑：`result=PASS ok=27 vacuous=1 violation=0 fault=0`、rc=0；G10 行数闸 OK（1 条已登记例外）、G12 AgentRunner 194≤200、G16 中文硬编码 0≤基线 0、G20 挂账 OK（ID 数实跑为准，全在台账）、G21 空转棘 ratchet VACUOUS=1≤基线 1；唯一 VACUOUS = G19「日志不拼凭据」（0 日志调用点，既定空转，如实登记） | ✅（含 1 条 ⛔vacuous） | `_w23/gates_g3.log/.safe.log`、`gates_g3_final.log/.safe.log` |
| G4 selftest | `selftest: 30/30 PASS (238s)`；28 个变异自证 case（守卫全族）+ 两条 VACUOUS 机制自证，每例输出可见本次注入证据（防自失败假红） | ✅ | `_w23/gates_g4.log/.safe.log` |

- 最终树守卫复跑（四闸门后）：`result=PASS ok=27 vacuous=1 violation=0 fault=0`，与 G3 一致（`gates_g3_final.safe.log`）。
- 编译失败四分类：本波零 `.kt` ⇒ 四分类（参数错 / 工具名错 / 该调不调 / 调了不收敛）**未触发**；两次 assembleDebug 均 BUILD SUCCESSFUL。G1 唯一 `What went wrong` grep 命中是 `localbuild.sh` 自身提示文本，非真实失败。
- 脱敏自检：检测器先写正向对照（注入 4 类禁用形态全遮蔽为占位，证明机制会响）再扫 0 替换；P1-2 补强后 `redact.py` 15/15、`scan_artifacts.py` 11/11 正控全响（含凭据文件名形态），额外抓出并修复历史 serial 检测器 `\b` 退格符恒不响的既有空转 bug；9 份 `.safe.log` 独立纯子串复扫全 CLEAN。实跑日志：`_w23/privacy_ruleset.safe.log`。
- 环境：代理端口本会话现取（未写死）；全程未 stash / reset --hard；git 树未变（tip `eeb4cbc`、零 `.kt`、仅两份既有未跟踪源码日析）。


---

## 3. 真机功能矩阵（四态 + 证据）

> 证据 = `_w23/evidence/matrix_facts.jsonl`（17 行，全部经出厂断言）+ `switch_facts.jsonl`（4 行切换）
> + `compress_facts.jsonl`（2 行压缩）+ `iterations.jsonl`（12 行）+ `_w23/shots/*_safe.png`（47 张回灌脱敏截图，
> 原始 `*.png` 仅本地）+ `evidence/w23-matrix-summary.json`（仓内脱敏机器可读制品，推送前生成）。
> 端点一律 8 位 name-fp（UI 显示名 sha256 前 8 位）；档位切换定位用「行 name-fp + 当前徽章」，弃 idx 索引。
> 判定四态如实：⛔ 与 ⛔vacuous 写明「行使后失败」或「判据未行使」；免费节点限流/无响应/静默一律归环境侧、
> 非四分类代码缺陷。早期误跑全部在 `attempt_states` 留痕、不抹除。

### 3.1 逐格四态（19 格 / 9 声明面全覆盖）

| 格 | 端点 name-fp | 公开 modelId | 档位(会话) | 功能面 | 四态 | 关键判据 / usage | 证据 |
|---|---|---|---|---|---|---|---|
| E1 | `b995dc45` | dots-3-note-preview:free | 32K | endpoint_axis | ✅ | typed_hint 逐字 + usage 上屏（1.7k in/65 out）+ 发送键 bounds 三者齐，题面 token `W23ALPHA77` 逐字回显 | `shots/it1_*_safe.png` |
| E2 | `b0113b2c` | north-mini-code:free | 32K | endpoint_axis | ✅ | 从严三件套齐，token `W23BRAVO42` 逐字回显；1.2k in/169 out | `shots/it2_*_safe.png` |
| E3 | `a9ae3f9e` | step-5-preview | 32K | endpoint_axis | ✅ | 从严三件套齐，题面 token 逐字回显；1.8k in/84 out | `shots/it3_*_safe.png` |
| E4 | `d5022055` | mistral-large-4-1t-a52b | 32K | endpoint_axis | ✅ | 三件套齐 + plain hello 36s 收敛（1.6k in/509 out，1664 cached 99.1%）；badge=NV-MISTRAL 真切换。早期态：token-echo 题面被长思考拒/超时两次，留痕不抹 | `shots/it4_*_safe.png` |
| E4b | NA | deepseek-v4.1-flash | NA | endpoint_axis | ⛔vacuous | 设备 6 端点池无此 modelId，无端点可设当前，前置缺失；按裁决不擅自新增端点、不臆造超时结论为 pass | 设备 endpoints.json 清单（已脱敏核） |
| E5 | `cfd5fbda` | gemma-4-26b-a4b-it:free | 32K | endpoint_axis | ⛔ | 限流复证：首轮 HTTP400 request rejected，二次 plain hi 复现 HTTP429 rate limited；无可用 usage，环境侧 | `shots/it4_gemma_*_safe.png` |
| T0 | `01e476bd` | nemotron-3.5-super-vl-120b-a12b | 32K | tier_axis | ✅ | 本波 UI 切会话档位 32K，确认框后回读「当前 32K」`switch_verified=true`（档位 = 应用侧会话预算 `maxContextTokens`，非端点 contextLength） | `shots/it4_tier_32K_safe.png` |
| T1 | `01e476bd` | nemotron-3.5-super-vl-120b-a12b | 512K | tier_axis | ✅ | 档位切换确认框后回读「当前 512K」`switch_verified=true` | `shots/it4_tier_512K_safe.png` |
| T2 | `01e476bd` | nemotron-3.5-super-vl-120b-a12b | 256K | tier_axis | ✅ | 回读「当前 256K」`switch_verified=true` | `shots/it4_tier_256K_safe.png` |
| T3 | `01e476bd` | nemotron-3.5-super-vl-120b-a12b | 1M | tier_axis | ✅ | 回读「当前 1M」`switch_verified=true`；1M 计量条「上下文 2K/995.9K」，仅读数不真压 | `shots/it4_tier_1m_safe.png`、`it4_1m_meter_b_safe.png` |
| F1 | `d5022055` | mistral-large-4-1t-a52b | 1M(读数) | multimodal | ✅ | 先验 dots `b995dc45` 图片 chip 禁用（desc=当前端点未开启图片输入，设备 TEXT-only）如实 ⛔；按裁决换设备池唯一 IMAGE 端点 mistral（`inputModalities` 含 IMAGE）chip 可点；真图 `scan_file`+MediaStore 命中→SAF→语义逐字命中「LEFT is red, RIGHT is blue」 | `shots/it5_*_safe.png` |
| F1b | `b995dc45` | dots-3-note-preview:free | 32K | multimodal | ✅（带 caveat） | 用户标注视觉双通 vs 设备实测 TEXT-only：补丁前图片 chip 禁用如实留证；按用户能力声明在设备侧补 IMAGE 后 chip 可点，真图 scan_file+MediaStore→SAF→语义逐字命中「LEFT:Red / RIGHT:Blue」（2.3k in/125 out）；验后恢复 TEXT-only 基线并冷启，不遗留设备改动 | `shots/it5_dots_vision_*_safe.png` |
| F2 | `01e476bd` | nemotron-3.5-super-vl-120b-a12b | 32K | workspace | ✅ | `file_write` 审批框 exact args 逐字 `{"path":"w23_write_test.txt","content":"SENTINEL_W23_7744_END"}`；写后 `ls -1` 差集 +1 / mtime / cat 21 字节逐字 | `shots/it6_*_safe.png` |
| F3 | `01e476bd` | nemotron-3.5-super-vl-120b-a12b | 32K | workspace | ⚠️ | `file_read` 回读 SENTINEL 逐字；`file_list` 列举 workspace 根；反背题对照读不存在文件如实报 file not found；**无工具记账、证据等级=回复内容级，从严判 ⚠️ 不写裸 ✅**；反背题对照只排除背题假绿（否定侧）非工具记账（肯定侧） | `shots/it6_*_safe.png` |
| F4 | `01e476bd` | nemotron-3.5-super-vl-120b-a12b | 32K | skill | ✅ | 技能页 `w22-devprobe` 来源：file（冷启已加载）→ @选择插入 → `skill_load` 审批框 `{"name":"w22-devprobe"}` 逐字 → `[SKILL BODY]` 上屏 → sentinel 逐字；无 `skill_load` 记账带 caveat | `shots/it7_*_safe.png` |
| F5 | NA | dots-3-note-preview:free | 32K | manual_compress | ⛔vacuous | 菜单+确认框+执行三判据全行使（fixture 峰值 27.9K/36.4K「上下文将满」），结果「没有可压缩的内容」dropped=0；早期三种夹具形态留痕 | `shots/it8_compress_dialog3_safe.png`、`compress_facts.jsonl` |
| F6 | NA | dots-3-note-preview:free | 32K | auto_compress | ✅（带 caveat） | 新会话逐条短消息 16 轮越触发线：`auto_compact dropped=19 kept=14 cache_invalidated=false`（dropped=消息数，net=字符净节省，非同一数值），后续 5 行 `already_compressed` 证明压缩结果落账生效；源码 applied=true 唯一路径才落该账 | `compress_facts.jsonl`、`it9_f6_agent_tail.safe.log` |
| F7 | NA | NA（无外部 MCP 端点） | NA | mcp | ⛔vacuous | 导航 ✅：设置→MCP 服务器列表页可达 + 空态「还没有 MCP 服务器」截图；行使 ⛔vacuous：无外部端点 + G15b `usesCleartextTraffic=false` 明文阻断不放开 | `shots/it10_mcp_*_safe.png` |
| L0 | NA | NA（本地 UI） | NA | local_ui | ✅ | 唤醒 `SCREEN_STATE_ON`/Awake + screencap 非全黑（3,307,219 B）；对话/设置/MCP/技能/沙箱页均可导航；从 iter0 真实证据派生、非硬造 | `shots/alive_safe.png`、`iterations.jsonl:iter0` |

> **汇总（19 格口径）：✅14 / ⚠️1（F3 无工具记账从严）/ ⛔1（E5 环境侧）/ ⛔vacuous3（E4b/F5/F7）**；9 声明面 9/9 全覆盖。增补两格：T0（32K 会话档回读闭合四档）与 F1b（dots 视觉声明核验：设备 TEXT-only→按用户声明补 IMAGE 后真图语义逐字命中，带 caveat、验后恢复基线）。
> 本波实质增量：① **F6 自动压缩从上一波段 ⚠️ 转 ✅**——首次真拿到 `auto_compact dropped=19 kept=14` 落账生效
> （审查席第二轮源码双点回源复核成立；带 caveat：单会话 dropped=19、源码无独立 applied=true 字面，以 dropped>0 认定，不读成压缩主线闭环）；② F1 多模态维持 ✅（dots 用户标注双通实测 TEXT-only 以真机为准，换 mistral 唯一
> IMAGE 端点攻语义逐字）；③ F2 workspace 文件系统级 + 反背题对照；④ F4 skill 资产注入端到端。
>判据前置纪律执行：真图 chip 先验、skill 资产格式先回源、压缩夹具先测触发线。

### 3.2 未达格与归因（逐条）

- **E4b deepseek ⛔vacuous**：设备 6 端点池无 deepseek 条目，无法设当前，判据未行使；按裁决不擅自新增端点、不臆造超时结论为 pass。前置缺失，环境类。
- **E5 gemma ⛔**：首轮 HTTP400 request rejected、二次 plain hi 复现 HTTP429 rate limited，节点限流环境侧，复证即止，不归因应用代码。
- **F5 手动压缩 ⛔vacuous**：压缩菜单出现、二次确认、执行三判据全行使（fixture 峰值 27.9K/36.4K），但返回「没有可压缩的内容」dropped=0。根因 = `file_read` 16000 字符上限 truncate 每个工具结果气泡 ⇒ 候选段 `candidateAll` 压缩后 size 不减 ⇒ 无损放弃；32K 软预算与 L1 硬闸几何夹紧。**能力本身存在**（历史 `manual_compress dropped=238 kept=64`），本波夹具未达判据，非应用 bug。修 `alreadyCompressed` 单指针 / 时序属 `.kt`，本波禁改，挂账。
- **F7 MCP ⛔vacuous**：导航判据（页面可达+空态截图 = ✅）与真实行使判据（无外部端点 + G15b 明文阻断不放开 = ⛔vacuous）分开落账，环境类不归因代码。

### 3.3 档位轴与端点轴分开判定

- **端点轴**：以「新 modelId 真往返收敛」为准：E1/E2/E3/E4 四支收敛（dots / north / step5 / mistral），从严三件套行使；E5 gemma 限流 ⛔、E4b deepseek 前置缺失 ⛔vacuous。密钥复用双路径回源成立（编辑留空保留 + 信封 id 键控），零搬运、零明文落盘。
- **档位轴**：512K / 256K / 1M 三档真切换回读（T1/T2/T3，`switch_verified=true`）；1M 只取**读数**（回读「当前 1M」+ 计量条 995.9K），**档位读数 ≠ 生效窗口 ≠ 1M 真压**（真压双阻断未解，挂账）；档位 = 应用侧会话预算档位（`SettingsRepository.maxContextTokens`，`SwitchGuard.kt:69`），与端点 contextLength 声明无关；`hard=` logcat 指纹本波未捕到，如实登记缺失。
- 两轴不互相冒充；不写「用户指令『直至 1M』达成」这类合并表述。


---

## 4. 链内迭代记录（每轮追加一行）

> 证据 = `_w23/evidence/iterations.jsonl`（12 行，iter 0–11 连续无跳号，出厂断言实测通过）+ 逐格事实与截图。
> 本波零 `.kt` ⇒ 每轮「未触发本波编译」；迭代预算 12 轮（主理人钉的执行值），**12/12 用满即收尾**。
> 每轮 7 字段齐全（action/observation/state/attribution/evidence_ref/caveat）。
> 早期误跑留痕不抹除：E2（helper 未切端点误在 dots 跑）、E3（nobtn + 未切端点两次作废）、
> E4（两次 token-echo 无 usage）、E5（400→429 两态）、F5（三种夹具形态）均在 `attempt_states` 列明。

| 轮 | 类型 / 动作 | 观察 | 四态 |
|---|---|---|---|
| 0 | 起步段归入：设备唤醒 + 四闸门 + 端点预筛 | 唤醒验非全黑（3,307,219 B）；G1 双出包 deterministic；G2 严格 818；G3 arch-guard 27/1/0/0；G4 selftest 30/30；6 端点池勘察 + PC 侧错密钥 fast-fail 全 401（PC 侧≠设备侧可用） | pass |
| 1 | E1 dots 32K 收敛复验 | 设 OR-DOTS（`b995dc45`）为当前 + 会话档位 32K；从严三件套齐，题面 token `W23ALPHA77` 逐字回显；1.7k in/65 out | ✅ |
| 2 | E2 north + E3 step5 端点轴真收敛 | 各设当前 + 32K；E2 逐字 `W23BRAVO42`、E3 token 逐字回显；三件套齐 | ✅ |
| 3 | E4 mistral / E5 gemma / E4b deepseek 前置预筛 | mistral 两次 token-echo 过度思考无 usage（早期态留痕），plain hello 36s 收敛；gemma HTTP429 限流；deepseek 不在设备池 ⇒ ⛔vacuous | ⚠️（含环境侧 ⛔ / 前置缺失 vacuous） |
| 4 | T1/T2/T3 档位轴切换回读 + 1M 读数 | 512K→「当前 512K」、256K→「当前 256K」、1M→「当前 1M」三级确认框均确认；1M 计量条「上下文 2K/995.9K」仅读数不真压 | ✅ |
| 5 | F1 多模态全链 | 先验 dots 图片 chip 禁用（TEXT-only）如实 ⛔；按裁决换唯一 IMAGE 端点 mistral，chip 可点；真图 scan_file+MediaStore 命中→SAF→语义逐字命中 | ✅ |
| 6 | F2/F3 workspace write/read/list | `file_write` 审批框 exact args 逐字；写后 ls 差集 + mtime + cat 21 字节逐字；`file_read` 回读 + `file_list` 列举 + 反背题对照 | ✅ |
| 7 | F4 skill 端到端 | 技能页来源：file → @点选 → `skill_load` 审批框逐字 → `[SKILL BODY]` 上屏 → sentinel 逐字 | ✅ |
| 8 | F5 手动压缩 dropped>0 | 32K 新会话逐条短消息 + 单次大 `file_read` 夹具填满 27.9K–36.4K；菜单+确认+执行三判据全行使，结果「没有可压缩的内容」dropped=0 | ⛔vacuous |
| 9 | F6 自动压缩评估链回读 | 逐条短消息 16 轮越触发点：`auto_compact dropped=19 kept=14 cache_invalidated=false`；后续 5 行 `already_compressed` 证明落账生效 | ✅ |
| 10 | F7 MCP 现状 | 设置底栏→MCP 列表页，导航判据（可达+空态截图）与行使判据（无外部端点+G15b 阻断）分开落账 | ⛔vacuous |
| 11 | 收尾覆盖核对 + 脱敏自检 + JSONL 校验 | 19 格覆盖 9 声明面（增补 T0 32K 会话档回读 + F1b dots 视觉附加格）；iter0–11 连续；switch 5 行 / compress 2 行；全 safe log 脱敏复扫 CLEAN；制品 10/10 必红变异全响 | ✅ |

> 编号连续性：iter 0–11 无跳号（出厂断言实测通过）；环境类 ⛔（iter3 gemma/deepseek、iter8 夹具未达、
> iter10 无外部 MCP）逐格如实登记，不冒充通过、不归因应用代码。

---

## 5. 收尾契约

### 5.1 1→5 各步结果（四态 + 证据路径）

| 步 | 结果 | 四态 | 证据路径 |
|---|---|---|---|
| 1 §1 本次任务范围 | 四份外审分歧逐条裁决完成（§1.1 十四条：采纳订正 3 / 顺延留痕 6 / 证伪 2 / 部分采纳 3，改方向全否）；做什么 12 项 / 明确不做 18 条逐条带挂账号；设备可行性 + 矩阵前置表。最硬裁决 = N-1 第 5 次证伪（完整键自初始即在、allowlist 行为测试已在，用户未给期望行为裁决） | ✅ | 本文件 §1.1–§1.4；`_w23/phase1_solution.md`；台账本波段 |
| 2 §2 本地闸门 | G1–G4 串行全实跑全 ✅（G1 双重出包 deterministic / G2 严格 818 对账 / G3 含 1 条既定 ⛔vacuous / G4 selftest 30/30）；脱敏正控先自证会响（15/15 + 11/11），额外抓出历史 serial 检测器恒不响 bug 并修复 | ✅ | `_w23/gates_g1..g4(.safe).log`、`_w23/privacy_ruleset.safe.log` |
| 3 §3 真机功能矩阵 | 12/12 轮预算用满；19 格 ✅14 / ⚠️1（F3）/ ⛔1 / ⛔vacuous3；实质增量 F6 自动压缩 `dropped=19 kept=14`（上波段 ⚠️→本波 ✅，源码回源 + 落账实证，带 caveat）；未达格不冒充通过 | ⚠️（部分格行使后失败 / 未行使，逐格如实） | `_w23/evidence/*.jsonl`、`_w23/shots/*_safe.png`、`evidence/w23-matrix-summary.json` |
| 4 §4 链内迭代 | 12 轮连续无跳号；每轮 7 字段齐全 + 四态 + 归因；环境类 ⛔ 逐格登记不归因代码；早期误跑全部 in `attempt_states` 留痕 | ✅ | `_w23/evidence/iterations.jsonl` |
| 5 §5 推送与 CI | 见 §5.4（每次推送 commit + run 链接、CI 红次数与推送次数） | 见 §5.4 | §5.4 + `git show` |

### 5.2 明确未做（逐条带挂账号）与 OPEN 挂账

**明确未做 18 条**：与 §1.3 一一对应，挂账号见 §1.3 表（老号原样 `OPEN`；本波段新增 6 行：
收口执行行 `CLOSED` + 5 行 `OPEN`，全部在台账本波段有真实行，每行自带触发信号 + 缓解动作 + 证据三件套）。
**本波真机未达成格**（逐条见台账本波段第 4 行）：E4b deepseek 前置缺失 ⛔vacuous、E5 gemma HTTP429 ⛔ 环境侧、
F5 手动压缩 `dropped=0` 未达 ⛔vacuous、F7 无外部 MCP 端点 ⛔vacuous——触发信号与缓解动作已挂账，
节点复活 / 新会话夹具可铺时行使。四项用户终局裁决（journal 第 18 次顺延 / ≥3 端点终局 / 占位符口径 /
`P2-3`）只留痕不代做。

**OPEN 挂账三件套**（本波段新增行，摘要；权威版见 `docs/06-deferred-ledger.md`）：

| 挂账号 | 触发信号 | 缓解动作 |
|---|---|---|
| 本波段第 1 行（收口执行） | 四报告一致判上一波收尾契约结构缺陷 + 制品 schema 退化 | 最小结构修复 + 逐处标注；本波新制品恢复 schema/cells[]/明细/必红样本（历史制品不重出）；出口判据按字面复核成立 |
| 本波段第 2 行（制品缺陷） | 外部审查按上一波制品作历史结论复核 | 生成器断言升级 + 10/10 必红样本落制品本体；历史制品不重出不覆盖 |
| 本波段第 3 行（journal） | 用户未给显式二选一裁决 | 第 18 次顺延登记，两代数口径不统一、交用户拍板；累计成本注记零成本并入 |
| 本波段第 4 行（真机未达格） | 节点复活 / 新会话可分割夹具可铺 | F6 自动压缩本波 ✅（`dropped=19`）；F5 手动压缩需 `.kt`（单指针 + 时序）或放宽 file_read 上限（禁改本波）；E4b 需用户设备新增端点；环境侧 ⛔ 不归因代码 |
| 本波段第 5 行（非对话模型） | 出现把视频生成/超分计进端点收敛 | chat 矩阵只收对话 LLM，不判 ⛔ 不计分母 |
| 本波段第 6 行（取证驱动遗留） | 后续波遇同型 BACK 逃逸 / 日志判据空转 | 维持取证设施不入仓；脱敏正控纪律（先自证会响再判 0）见 §2 |

### 5.3 制品清单（交外部审查团队）

> **不含会话历史、不含凭据、不含端点标识、不含「建议下一链做什么」**。
> 端点一律以不可逆 8 位指纹出现（name-fp）；模型名为公开产品名；证据路径为仓内相对路径或仓外证据目录名，
> 不含本机绝对路径、设备序列号、账号字段、完整端点 URL。

| 制品 | 路径 | 说明 |
|---|---|---|
| 本波验收物 | `docs/analysis/w23-acceptance.md` | 本文件（§1 范围 / §2 闸门 / §3 真机 / §4 迭代 / §5 收尾） |
| 本波交接 | `docs/handoff-20261117-wave23.md` | 摘要，冲突时以验收物为准；严格零复合编号字面量 |
| 挂账台账 | `docs/06-deferred-ledger.md` | 本波段 6 行 + 被点名老行行内注记；老号全数原样保留 |
| 脱敏机器可读证据制品 | `evidence/w23-matrix-summary.json` | 出厂断言 10/10 必红样本落制品本体；`cells_total=19` = 真机 19 格（9 面全覆盖，L0 从 iter0 真实证据派生，增补 T0/F1b）；schema 版本恢复 + cells[] + iterations[] 明细 + evidence_ref + attempt_states + truncated_fields |
| 四闸门日志（仓外，不入库） | `_w23/gates_g*.log` + `.safe.log` | 串行实跑全 PASS；正控 15/15 + 11/11 自证遮蔽机制会响，脱敏副本独立复扫 CLEAN |
| 真机事实与脱敏截图（仓外，不入库） | `_w23/evidence/*.jsonl`（17/12/4/2 行）、`_w23/shots/*_safe.png`（47 张） | 逐格四态、切换回读、压缩触发线原文、SAF 链条；原始 `*.png` 仅本地 |
| 外审分歧裁决工作稿（仓外） | `_w23/phase1_solution.md` | 14 条裁决 + 做什么/不做 + 设备可行性前置表 |
| 审查报告（仓外） | `_w23/phase3_review_static.md` | 静态 + 第二轮补审；含 P0/P1 修复记录与正控自证 |
| 脱敏与扫描脚本（仓外，不入库） | `_w23/redact.py` / `scan_artifacts.py` | 规则集补凭据文件名形态（运行时拼接、脚本本体零该串）；不入仓维持老号口径 |
| 代码 diff | 本波各提交 `git show` | **零 `.kt` 改动**；只有文档 + 台账 + 脱敏证据制品 |
| 隐私自检 | 制品 `privacy` 字段 + `.safe.log` / `*_safe.png` 复核 | 禁用形态 0 命中；正控自证遮蔽机制会响 |

### 5.4 CI（实跑回填）

| 项 | 值 |
|---|---|
| 推送 1 | （推送后回填：commit 哈希、文件清单、dry-run RC、实推 RC、remote 清理复核） |
| Build run | （推送后回填：run id + conclusion + 五 job 结论，公开 API 逐项读，零凭据） |
| Secret scan run（独立） | （推送后回填：同一 push 的独立 run id + conclusion） |
| 最终树守卫复跑 | 本波文档落盘后复跑 `result=PASS ok=27 vacuous=1 violation=0 fault=0`、rc=0（`_w23/gates_g3_final.safe.log`）；G20 `PASS`、`MISSING=[]` |
| 推送 2 | （如需链接闭环：纯文档提交，Build 0 run 属既定口径；否则不适用） |
| CI 红次数 | （推送后回填，上限 2） |
| 推送次数 | （推送后回填，上限 3） |
| 归因 | 无失败案例 ⇒ 四分类未触发；节点限流/前置缺失判环境侧不写成应用 bug；纯文档提交 0 run 属既定口径不写「构建再次通过」 |

> 本波零 `.kt` 改动 ⇒ 「CI 全绿」指 Build 五 job + 独立 Secret scan 结论 success，
> **不等于**真机矩阵全格通过（§3 如实登记 ⛔ / ⛔vacuous 的格）。
> 凭据纪律：run 链接取值仅用公开 API（零凭据）；推送凭据 inline 一次性使用、不落盘、remote 保持干净 URL；
> 不 force-push、不 rewrite history；每次推送留可审计 commit 与 run 链接。

**文档回写 diff**：`docs/analysis/w23-acceptance.md`（新）、`docs/handoff-20261117-wave23.md`（新，零复合编号）、
`docs/06-deferred-ledger.md`（本波段 6 行 + 被点名老行行内注记）、`evidence/w23-matrix-summary.json`（新，
脱敏机器可读制品）。历史各波正文与制品一律未动；两份未跟踪源码日析保持未跟踪不入库（老号口径）；
本波不写「建议下一链做什么」。
