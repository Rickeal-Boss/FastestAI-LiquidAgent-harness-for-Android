# W28 收尾契约验收物 — 四份审查报告分歧裁决（P0-1 二选一）+ 多模型×多上下文×多功能面真机矩阵 + W27 收尾契约账实收口（2026-11-18）

> 基线 `cdaade7`（W27 回填，`main`；本地与远端 HEAD 一致，零 `.kt` 改动起点）。
> 上游依据：用户 2026-11-18 指令（读记忆 / 上一波 handoff / OPEN 挂账行〔现役仓 `harness-v2/`，
> **非旧仓** `Android-Agent/`〕，列出「做什么 / 明确不做」写入验收物第 1 节；读四份审查人报告
> 〔`W28段挂账草案.md`、`W27深度审查与W28排期裁决.md`、`harness-w27-review.md`、
> `FastestAI-LiquidAgent-harness_W27深度审查与挂账分析及W28路线建议_2026-11-18.md`〕并
> **裁决分歧、不改方案大体方向**；用户给的 API〔6 条 build.nvidia.com 模型链接清单 + openrouter 两支模型〕
> 测多模型 × 不同上下文〔直至 1M 读数〕× 多模态输入 / 工作空间文件读写 / skill / MCP / 手动与自动上下文压缩；
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
> 密钥纪律：端点凭据**只在设备侧**（设备 `files/harness/secrets/` 六枚密文已在位，不注入新凭据、不输出 key 字段）；
> 本单 / 工具 schema / 工具结果 / 日志 / 截图 / 交接 / 记忆写入 **零凭据字面量**；端点一律以不可逆 8 位指纹出现
> （指纹 = UI 显示名 sha256 前 8 位）。设备序列号、本机绝对路径、凭据文件名、未打码账号字段一律不出现。
> 完整版即本文件；`docs/handoff-20261118-wave28.md` 只做交接摘要，冲突时以本文件为准。
> 本文件不写「建议下一链做什么」。
> 🔴 编号纪律：交接文档严格零「W 加数字加减号加数字」复合编号字面量（G20 只扫 `docs/handoff-*.md`，
> 不扫 `docs/analysis/`）；本文件 §1.3 等处的挂账号是台账溯源，写完整号合规必要。
> 台账纪律（§三.6）：新增台账行须由主理人**单人**落笔，外部审查草案不直接改真值源；本波号段自由已实跑确认（`W28` 段 0 占用）。

---

## 1. 本波范围：做什么 / 明确不做（执行前钉死）

### §1.1 四份审查报告分歧裁决表（不改方案大体方向 = 用户指令面优先）

> 输入四报告：报告甲《W28 段挂账草案》（3 行零 `.kt`，号段已预检）、报告乙《W27 深度审查 · 挂账分析 · W28 排期裁决》（3 P0 + 8 P1 + 第 0 车 7 步）、
> 报告丙《W27 深度审查 —— 零代码矩阵稳态维持 + 两把尺子真机复现》（F5 配方回退 / W24-01 深色 / journal 21 次）、报告丁《LiquidAgent harness W27 深度审查 + 挂账分析 + W28 路线建议》（断言 M / journal 断档 / 矩阵整车 vs 小票）。
> 五值裁决 = 采纳 / 采纳订正 / 顺延留痕 / 证伪 / 部分采纳。
> **全部裁决 `改方向 = 否`**：用户本单指令面优先，继续矩阵波；四报告「转代码波 / 开 `.kt` 否则写永不实现 / 矩阵收官 / 整车换小票」主张整包顺延留痕、
> **不取消不推翻**；矩阵存续决策包维持 `OPEN`。采纳任何外审处方前均回现役树核状态（旧仓 W87 一次证伪 4 条全是时点差；W26 撞号证伪已先例）。

| # | 分歧议题 | 四报告立场 | 现役回源（本波实跑核对） | 裁决 | 改方向 |
|---|---|---|---|---|---|
| 1 | 总路线：转代码波 / 矩阵收官 vs 继续矩阵波 | 报告丙 §六「第九次建议开 .kt 或开终局」；报告丁 §五「吸收率 vs 冻结率剪刀差」；报告甲/乙第 0 车零 `.kt` 收口 | 用户本单仍给 API + 6 条模型链接清单 + openrouter 两支、明确要矩阵测试；`w27-acceptance.md` 指令面优先先例成立；矩阵存续决策包台账原样 `OPEN` | **顺延留痕**：继续矩阵波，不转代码波、不升格拆分波、不关矩阵、不写「永不实现」；四报告排期主张整包顺延 | 否 |
| 2 | W28 段 3 行挂账落笔 vs 不落笔 | 报告甲给可直接贴入 3 行 + 号段扫描；报告乙 §5 第 0 车第 7 条同主张；报告丙/丁整包顺延需 `.kt` 项不在草案 | 本波实跑台账全表行首扫描 `W28` **0 行**（`W27` 3 行已占、`W26` 5 行属 W2.6 别名段），号段自由、无撞号；G20 仅存在性比对（`arch-guard.sh` 两边 `sort -u`）无唯一检测 | **采纳落笔（主理人单人）**：3 行零 `.kt` 可兑现、各带必红变异，号段自由已确认；六项用户终局裁决只留痕不代做；G20 机器唯一性属用户裁决域，人工闸门 + 必红变异兜底 | 否 |
| 3 | **P0-1「两把尺子」入账零落点 + 定性二选一**（本波最关键） | 报告乙 P0-1 强制二选一：(A) 承认缺陷→写进 `W9-01`/`W23-04` 行内注记、撤回「正确行为非缺陷」定性、保持 `OPEN`；(B) 承认正确行为→外审处方作废、删「红态入口」措辞。报告甲 W28-01 同；报告丙/丁认为定性正确但入账虚报 | 本波实跑：台账 `W9-01`(:147) 仅 W11 标定面「**不是两把独立的尺子**」一句（与 F6 压缩触发无关）、`W23-04`(:458) 对 token/字符/红态 **零命中**；全库「两把尺子」仅在 W27 自身三处〔`w27-acceptance.md:52/:180/:249`、`handoff:38/:77`、台账 `W27-03`〕+ 制品两处命中——**三处「已写进老号」第 6 次零 diff 虚报坐实**；W26/W27 两波真机证明 `below_trigger` 是字符维触发线的正确行为 | **采纳案 B（选 B：正确行为、非缺陷）**：外审「写进老号作红态入口」处方作废；三处〔`w27-acceptance.md` §1.1 #9、handoff 压缩老号行、台账 `W27-03`〕同步删「已写进 / 红态入口」措辞，改为「token 维越预算、字符维未越阈值 ⇒ 不触发为正确行为，实锤留验收物 §3/§4，不写进压缩老号、不设代码波红态入口」；`W9-01`/`W23-04` 维持 `OPEN` 原样、零 diff（四外审同一缺陷禁拆多条）。案 B 使「声称已入账」的账实不符自然销账，无需补写 | 否 |
| 4 | P0-2 制品出厂断言空转 + 覆盖退化 | 报告乙 P0-2：`state_counts` 零值键 vs `cells[].state` 值域、必红样本与现役同型却自报 5/5 REJECTED、断言 11→5 全元断言真机覆盖 6→0、`injected_sample` 自然语言、`na_allowlist` 幽灵项 | 现役 `evidence/w27-matrix-summary.json` 实跑：`state_counts={'PASS':8,'PARTIAL':0,'VACUOUS':3,'FAIL':0}` 而 cell 值域 `{'PASS','VACUOUS'}`，`K_state_domain` 的必红样本「注入 PARTIAL 零值键」与现役同型；`na_allowlist=['F7','W24-01','W24-02']` 而 NA 格只 F7 | **采纳订正（只改本波新制品，历史制品不重出）**：本波 `evidence/w28-matrix-summary.json` 删零值键或改 description「非零键集=值域」并同步必红样本；补回真机面断言或 `coverage_note` 明写零覆盖；`injected_sample` 结构化 `{field,old,new}` + 64 位哈希或登记仓外不可重放；加 `K_na_allowlist` 收幽灵项；`mutation_selftest` 保留「规则自证 ≠ 真机重放」分组标注 | 否 |
| 5 | P0-3 出口判据无仓内载体 | 报告乙 P0-3：checklist 不存在、生成器仓外；报告甲 W28-03 同；报告丁 §一.2 主张断言 M 入生成器 | 本波实跑 `find docs -iname '*checklist*'` 只 `docs/notes/w4-gate-checklist.md`（W4 闸门清单、与收口无关），W27 三行出口判据依赖的收口 checklist 确无载体；G20 112 ID 连续多波不变 | **采纳（纯文档载体）**：新建 `docs/notes/w28-closeout-checklist.md`，三条出口判据逐条对应可跑命令 + 必红变异；断言 M 入生成器属仓外取证设施（老号 W20-14），本波只登记不代做；W27 三行「已做 / 尚待确认」措辞冲突如实留痕于本行、原行不动（不声称已订正原行） | 否 |
| 6 | 收尾契约第 9 次 / tip run 闭环 | 报告乙 P1-1：本波第 0 车补 `44241199431`，若裁决不落地 W29 起挂 `W20-08` 不再逐波判 P0 | tip `cdaade7` 的 Secret scan `44241199431` 公开 API 复核 success，但全树 0 命中（W27 §5.1 步 5 却判 ✅「推送后回填」） | **采纳登记 + 降级预告**：本波补 `44241199431`@`cdaade7` 进 W27 §5.6 + 文末链接段、§5.1 步 5 改「闸门 ✅ / 推送 1 CI ✅ / tip run 见 §5.6」；闭环方式属用户终局裁决域不代裁；**若 W28 仍不拍板，W29 起降级为 `W20-08` 行内注记、不再逐波判 P0**（本波为最后一次逐波登记） | 否 |
| 7 | G20 无 ID 唯一检查 | 报告乙 P0-2/#6：加 checklist 人工闸门 + 必红变异；报告丙/丁：机器化唯一预检（断言 M） | `arch-guard.sh` g20 两边 `sort -u` 后只做存在性比对，重复号被抹平 | **采纳人工闸门 + 不改守卫**：checklist 两条（新号段必扫全表行首占用 / 验收物挂账号台账须有独立行）各配必红变异；改守卫 / 扩扫描面属用户裁决域，先批量补六行漏号再谈 | 否 |
| 8 | 手动压缩 F5 配方回退（报告丙 P1） | 报告丙 §三：W27 用 4 轮短 + 16KB 大 read 总量 ~18k < 50227，建议复用 W23 的 16 轮短消息配方攻 `dropped>0`；报告甲/乙维持 ⛔vacuous、不写硬出口 | W23 `dropped=19` 是历史运行、非本波；本波零 `.kt`、16 轮短消息是零代码夹具可试 | **部分采纳（真机行使）**：本波真机按报告丙试一次 16 轮短消息配方（32K 档），`dropped>0` 即 ✅ 二次行使、仍 0 则 ⛔vacuous 如实记；**不把 `dropped>0` 写成硬出口判据或达标门槛**（沿用老号纪律） | 否 |
| 9 | W24-01 深色主题 / W24-02 限流零代码可攻面（报告丙 §四） | 报告丙 §四：W24-01 深色 4 态截图、W24-02 dots/north 限流复现均零代码 | 两缺陷源码锚点仍 live；`EXPECTED_TESTS=818`、`LINE_CAPS`/`TOTAL_LINE_CAP` 全在位 ⇒ 真修复确需 `.kt` + 拆三方 | **顺延留痕、维持 `OPEN`**：本波若真机顺带行使深色主题/限流观察则录为证据，但**不写成已复现事实**；不修 `.kt`、优先级 argument 登记（`W24-02` 数据完整性 > `W24-01` 渲染 UI） | 否 |
| 10 | journal 代数口径 / 断言 M 入生成器（用户裁决域） | 报告乙 P1-7「第 21 次顺延」；报告丁 §一「19 连断档两波、W28 恢复登记带断档注记」；报告丙 §二 P2「三套代数并存」 | 台账 `W24-03` 仍记 19、W26/W27 验收物 20/21 两值并存；断言 M 老号 `W20-14` 生成器仓外 | **只留痕、不代裁二选一、不新开行**：代数多值并存如实登记，属用户终局裁决域；本波不自造统一值、不恢复登记断档（待用户拍板）；断言 M 入生成器属仓外设施登记 | 否 |

> 裁决合计 10 条：**采纳/采纳订正 6（#2/#3/#4/#5/#6/#7）/ 部分采纳 1（#8）/ 顺延留痕 3（#1/#9/#10）**。全部 `改方向 = 否`。
> **本波唯一新增落地动作 = W28 段 3 行台账主理人单人落笔 + P0-1 选 B 三处同步 + 第 0 车零 `.kt` 最小结构修复（制品/checklist/隐私字面量）+ 矩阵新证据**（非代码方向变更）。
> 注：#3 是本波最重要的裁决——选 B 基于「`below_trigger` 是字符维触发线的正确行为」这一 W26/W27 双波真机事实，不是基于「不方便补写行内注记」；四外审同一缺陷禁拆多条，故 `W9-01`/`W23-04` 维持原样、不因本波新增红态入口。

### §1.2 做什么（本波判据 · 执行前钉死）

| # | 判据 | 行使方式 | 出处 |
|---|---|---|---|
| 1 | 范围 §1：四报告逐条裁决 + 做什么/不做写入本波验收物第 1 节；P0-1 二选一显式选 B；交接侧挂账号转中文段位 | §1.1 裁决表 + §1.2/§1.3 写入本波验收物第 1 节 | 用户指令 + 四报告 |
| 2 | **第 0 车零 `.kt` 收口**：P0-1 三处同步（删「已写进/红态入口」虚报）+ P0-2 本波新制品修空转判据 + P0-3 新建收口 checklist + 补 W27 tip run `44241199431` | 逐条指到 `文件:行`；公开 API 复核 run 四字段齐；不重出历史制品正文与更早各波结论 | 报告乙 §5 第 0 车 + 报告甲 W28 段 |
| 3 | **W28 段 3 行台账落笔（主理人单人）**：号段自由已实跑确认（`W28` 0 行），零 `.kt`、各带必红变异、可对账出口 | 段头 + 3 行 `OPEN`；落笔前 `grep -cE '^\| W28-'` = 0 留痕；注入重复号样本 checklist 判红 | 报告甲草案 + 报告乙 §5 + 台账 §三.6 |
| 4 | **端点轴**：多模型（openrouter `dots-3-note-preview:free`、`north-mini-code:free` 两支用户点名；其余走用户本单 6 条 build.nvidia.com 模型链接清单） | 设备侧 fast-fail 预筛后真往返收敛取证；指纹 = UI 显示名 sha256 前 8 位；多模态以真机 `inputModalities` 为准，不写用户标注为已验证 | 用户指令；端点池见 §1.4 |
| 5 | **档位轴**：多上下文（32K / 256K / 512K / 1M **读数**，非真压） | 档位真切换回读；1M 档读数 + 计量条；**档位读数 ≠ 生效窗口 ≠ 1M 真压** | 用户指令 |
| 6 | 多模态输入 | 真图 + SAF 选图 chip + 语义问答；先验 `inputModalities`（无 IMAGE 端点 chip 灰如实 ⛔vacuous） | 用户指令 |
| 7 | 工作空间文件读写 | `file_write`/`file_read` 严格 `ls -1` 差集 + mtime + 内容逐字 sentinel + 审批 exact args + 反背题对照 | 用户指令 |
| 8 | skill 功能面 | 设备侧 `SKILL.md` 注入 → 冷启动「来源：file」→ `@name` 点选 → sentinel 逐字；无工具记账以 UI 副作用为准并带 caveat | 用户指令 |
| 9 | MCP 现状确认 | 顶栏返回箭头绕系统 BACK → MCP 列表页可达 + 空态截图；导航（✅）与行使（⛔vacuous）分开落账；平台明文阻断不放开 | 用户指令 |
| 10 | 手动 + 自动上下文压缩 | 32K + 新会话夹具；手动试 W23 16 轮短消息配方攻 `dropped>0`（不成即 ⛔vacuous）；F6 两把尺子口径本波复跑（选 B：正确行为非缺陷、不设红态入口） | 用户指令 + 报告丙 P1 + 报告乙 P0-1 |
| 11 | 本地先行 + adb 调试 + 链内迭代 | 先本地 UI/功能测试 + adb 调试；真机「改→编译→真机→再看」链内循环；每轮追加一行证据（action/observation/state/attribution/evidence_ref/caveat） | 用户指令 |
| 12 | 四闸门 + 推送 + CI | G1–G4 实跑后推送；每次 commit + run 链接；脱敏正控先自证会响；凭据 inline 一次性、用后清 remote；不 force-push、不 rewrite history | 用户指令 + `AGENTS.md` |
| 13 | 脱敏 + 零凭据 + 零复合编号 + 四态纪律（全局） | 端点 8 位指纹；产物零 PAT/真 key/本机路径/设备序列号/未打码账号/凭据文件名；交接文档零复合编号字面量；未触发 ≠ 通过、推断带 caveat | 用户指令 + 守卫自指陷阱 |

### §1.3 明确不做（逐条带挂账号，全部原样 `OPEN`）

| # | 不做项 | 挂账号 |
|---|---|---|
| 1 | 拆分三方整包 / 任何 `.kt` 增量 / 调行数与测试数上限（含修 `W24-01/02` 两缺陷所需 `feature-chat` 余量腾挪） | W20-02、W24-01、W24-02 |
| 2 | journal / FreezeClock 二选一代裁决（代数多值并存只留痕、不新开行、不恢复断档登记、二选一仍待用户） | W24-03 → W21-03 → W20-01 → W9-05 |
| 3 | 设备侧 1M 真压（双阻断未解，只做 1M 档位读数） | W20-05 → W9-11 |
| 4 | MCP 明文联调 / 放开明文流量开关 / https 夹具 | W20-06 → W5-03 |
| 5 | git 历史清理 / force-push / rewrite history | W20-07 |
| 6 | 六项用户终局裁决代做（journal / ≥3 端点 / 占位符口径 / 并行键期望行为 / tip run 闭环方式 / 借鉴独立输入面） | W20-08 |
| 7 | 安全波整包 | W20-04 |
| 8 | 台账同物多名并号 / 台账模板改动 | W20-10、W20-16 |
| 9 | 守卫侧修复 / G20 扫描面扩展 / 六行漏号补齐 / 幽灵号双断言 K/L 入守卫 | W20-17、W20-03 |
| 10 | 取证设施（驱动/脱敏脚本/断言 M 生成器）入仓——本波断言补丁只落仓外 | W20-14 |
| 11 | 加长 system / 音频 / `core-device` 独立波 / 图片预算四条 | W20-11 |
| 12 | 非对话模型（视频生成 / 视频超分 / 图片超分）进 chat 端点矩阵 | W21-05 |
| 13 | PC 侧免费节点结论升级为「设备侧可用」 | W20-12 |
| 14 | 历史制品正文重出（含 W27 制品断言回填只落本波新制品）/ 更早各波正文改写（P0-1 仅在 W27 自身三处删虚报措辞，不重出 W26/W25 正文） | W21-02 → W20-15、W27-02 |
| 15 | 摘 VACUOUS / 观测缝 / 工具记账上线（需 `.kt`） | W21-06、W20-03 |
| 16 | `@Test 818→≥850` / 改测试数上限 | W20-09、W20-02 |
| 17 | N-1 白名单行为面「修复」——现役完整键已在、测试已在，拒绝对不存在的 bug 动刀 | W20-09 |
| 18 | 把手动压缩 `dropped>0` / 自动压缩 `applied=true` 写成硬出口判据或达标 ✅（本波即使试 16 轮配方成功也只记行使、不设门槛） | W21-04 |
| 19 | 把两条用户可见缺陷推导写成「已复现事实」（本波深色主题/限流若顺带观察只录证据、不坐实） | W24-01、W24-02 |
| 20 | 转代码波 / 拆分三方 / B1 压缩结构落地（属用户裁决域，整包顺延留痕） | W9-01、W12-02、W17-01、W23-04 |
| 21 | 把 W23 F6 ✅ / F2 ✅ 文件系统级与 W24/W25 的「同 W23」「回复级/⚠️」抹平 | W23-04 |
| 22 | G20 扫描面扩域（含「← 原建议」注释形态进比对域）——先批量补六行漏号，扩面前置未满足 | W20-17 |
| 23 | 本波制品 11/11 必红变异冒充「真机重放」——规则定义自证与真机执行分开标注（沿用 W26/W27 诚实口径） | W27-02 |
| 24 | **P0-1 选 A（补写老号行内注记 / 撤回「非缺陷」定性 / 把 F6 设为下一代码波红态入口）**——本波明确选 B，案 A 作废不执行 | W9-01、W23-04、W27-03 |

### §1.4 设备可行性 + 端点池前置（方案席独特维度）

#### 1.4.1 目标设备与已知怪癖

- **目标机**：OPPO PDRM00，Android 13（SDK 33，ColorOS）；纯 API Harness（模型云端、无端侧模型）。
- **唤醒三步法（本波主理人实测通过）**：`KEYCODE(224)` → `KEYCODE_WAKEUP` → `dumpsys window policy` 读 `SCREEN_STATE_ON` + `INTERACTIVE_STATE_AWAKE` → `exec-out screencap -p` 保存（`_w28/screen0.png`，429625 B，人工判读状态栏 / 端点 badge / 思考框 / 输入栏可见，非纯黑）⇒ 存活判据 ✅。`pm install-commit` 在灭屏会永久挂起，故必须先唤醒。
- **adb 通道**：一律有线、`-P 15037`、工作空间绝对路径；含路径命令 `MSYS_NO_PATHCONV=1`；daemon 每条命令后重启 ⇒ 关键读回放同一条调用；读设备二进制用 `adb exec-out`。
- **Compose bounds**：`uiautomator dump` 对 Compose bounds 不作唯一判据，点按以 UI 回读 / 截图 / agent log 为准；发送键用 live bounds。
- **题面与日志**：`input text` 非 ASCII 触发 NPE、且是追加写 ⇒ 题面只用 ASCII（空格 `%s`）、每题先点「新对话」；回灌只取脱敏判据与 `deviceLogTail`，不回灌整段 logcat。
- **密钥纪律实测**：设备 `files/harness/secrets/` 六枚密文（`.bin`）已在位，本波不注入新凭据、不读 key 明文字段；`endpoints.json` 仅以 8 位指纹形态落验收物。

#### 1.4.2 端点池勘察（本波脱敏实测）

设备侧端点池 6 支（指纹 = UI 显示名 sha256 前 8 位，本波 `hashlib.sha256(name)` 逐支复算与 W26/W27 指纹一致）：

| name-fp | 公开 modelId | 来源 | contextLength | inputModalities | 本波角色 |
|---|---|---|---|---|---|
| `01e476bd` | nvidia/nemotron-3.5-super-vl-120b-a12b | build.nvidia.com | 1048576 | `[TEXT]` | 当前端点；端点轴/档位轴/压缩主力 |
| `b995dc45` | dots-studio/dots-3-note-preview:free | openrouter（用户标注 512K 双通） | 512000 | `[TEXT]` | 用户点名；**设备真相 TEXT-only**，多模态声明不背书 |
| `d5022055` | mistralai/mistral-large-4-1t-a52b | build.nvidia.com | 1048576 | `[TEXT, IMAGE]` | 唯一含 IMAGE 端点；多模态真图语义面 |
| `a9ae3f9e` | stepfun-ai/step-5-preview | build.nvidia.com | 1048576 | `[TEXT]` | 端点轴备选 |
| `b0113b2c` | cohere/north-mini-code:free | openrouter | 256000 | `[TEXT]` | 用户点名；真往返可达性验 |
| `cfd5fbda` | google/gemma-4-26b-a4b-it:free | openrouter | 262144 | `[TEXT]` | 历史 HTTP 429，fast-fail 预筛 |

> 清单中 `wan2.2-animate` / `vsr` 为非对话模型，明确不做（挂账号 W21-05）；`deepseek-v4.1-flash` 历史 420s 超时，只做 fast-fail 预筛、如实登记 ⛔。密钥全程只在设备侧复用端点 id，不注入新凭据、不输出 key 字段。

#### 1.4.3 前置表现势化

- 档位 32K/256K/512K/1M 是**会话预算档位**（`SwitchGuard`），不是端点 `contextLength`；读数 ≠ 生效窗口 ≠ 1M 真压。
- 多模态只需一个含 IMAGE 端点（NV-MISTRAL）；dots/north 双通道声明被设备真相证伪，不写成「已验证双通」。
- 压缩触发线走 `committedChars` 字符维（阈值 50227），与 token 维软预算是两把尺子；P0-1 选 B 已定性为正确行为、非缺陷、非红态入口。

### §1.5 本波定位一句话 + 主理人通报要点

**定位**：W28 = 「四份审查报告分歧裁决（P0-1 显式选 B + 号段自由确认）+ 新节点×多上下文×多功能面真机矩阵 + W27 收尾契约第 0 车账实收口（制品/checklist/虚报措辞/run 回填）」复合波；零 `.kt`、不改方向、止于交接。

**通报要点**：① 用户指令面优先继续矩阵波，四报告排期主张整包顺延留痕、不取消不推翻；② P0-1 选 B（below_trigger 正确行为、非缺陷），三处「已写进老号」第 6 次虚报通过删措辞自然销账、不补写、不设红态入口；③ W28 号段 0 占用实跑确认，3 行台账主理人单人落笔；④ 制品断言与收口 checklist 只落本波新制品/新文档，历史制品不重出；⑤ tip run 闭环本波最后一次逐波登记，仍不拍板则 W29 降级挂老号。

---

## 2. 本地闸门结果（四态 + 证据路径）

| 闸门 | 四态 | 实跑结果 | 证据路径（仓外） |
|---|---|---|---|
| G1 编译（assembleDebug） | ✅ | `rc=0`，产物 `app-debug.apk`；删包重出两次 SHA256 完全一致（`ca87a70a…6513`）⇒ **确定性实证**（守住「rc=0 ≠ 出包」铁律） | `_w28/g1_build.log`、`g1_rebuild.log` |
| G2 arch-guard | ✅ | `result=PASS ok=27 vacuous=1 violation=0 fault=0`；G20=112 handoff ID 全在台账、CLOSED 行证据非空；严格行首 `@Test` = **818** = `build.yml:135 EXPECTED_TESTS` | `_w28/g2_arch.log` |
| G3 arch-guard-selftest | ✅ | `selftest: 30/30 PASS（259s）`，含 G20 必红变异「删台账行」会红、VAC-G1b/VAC-G21 空转机制自证；rc=0 | `_w28/g3_selftest.log` |
| G4 fulltest 汇总 | ✅ | `tests=818 failures=0 errors=0 skipped=0`（14 模块全绿，83 个 TEST-*.xml mtime 全为本轮 13:36:38–13:36:40，非残留） | `_w28/g4_fulltest.log` |

> 四闸门实跑前置；零 `.kt` 改动故与 W27 基线一致。`rc=0` ≠ 产物已重出的铁律本波以删包重出 + SHA256 比对实证守住（重出前 mtime 停在 01:19 的旧包，重出后 13:36 且字节一致）。
> 脱敏正控：外部扫描工具 `scan_artifacts.py` 正控先自证会响（`SELF-AUDIT PASS 11/11 incl. cred_filename` 沿用 W23 已验证形态），正控会响后才采信全树 0 命中（见 §5.5）。
> G2/G3 在**第 0 车文档修复与台账落笔之后**还会复跑终态守卫一次（见 §5.6），本表为修复前的基线闸门。
> **终态守卫复跑（台账落笔后）**：`result=PASS ok=27 vacuous=1 violation=0 fault=0`、G20 112 ID 全在台账（证据 `_w28/g2_finaltree.log`）⇒ W28 段 3 行对 G20 并集零贡献，落笔后仍绿。

---

## 3. 真机矩阵逐格结果（本波 11 格：✅6 / ⛔vacuous5）

> 端点轴与档位轴分开判定；档位读数 ≠ 生效窗口 ≠ 1M 真压；功能面判据前置先备，前置不满足即 ⛔vacuous 不冒充通过。
> 端点一律 8 位指纹（sha256 显示名前 8 位），逐支复算与 W26/W27 一致；密钥全程只在设备侧，不注入、不输出。
> 端点轴与档位轴分开判定；功能面判据前置先备，前置不满足即 ⛔vacuous 不冒充通过。逐格四态与证据路径如上表；

| 格 | 面 | 端点-fp | 档位 | 四态 | 关键判据（脱敏） | 证据路径 |
|---|---|---|---|---|---|---|
| E01 | endpoint_axis | `01e476bd` | 默认 | ✅ | 正控逐字 `W28ALPHA7`、done=true+usage_changed=true、6.7s、4.4k in/33 out | `evidence/iter1_e01.json` |
| E02 | endpoint_axis | `b995dc45` | 512K读数 | ✅ | 用户点名 OR-DOTS 逐字 `W28BRAVO9`、SWITCH=true、链内修正：切端点前补 wake（原冷启动报 no eplist） | `evidence/iter2_e02.json` |
| E03 | endpoint_axis | `b0113b2c` | 256K读数 | ✅ | 用户点名 OR-NORTH 逐字 `W28CHARLIE3`、SWITCH=true、done+usage_changed | `evidence/iter3_e03.json` |
| T0 | tier_axis | `01e476bd` | 32K/256K/512K/1M | ✅ | 四档 badge 逐档回读；档位=会话软预算（SwitchGuard）非端点 contextLength | `evidence/iter4_tiers_w28.json` |
| F2 | workspace | `d5022055` | 默认 | ✅ | `file_write` 审批 exact args + 审批点 1 次、`ls -1` 差集 +1 `w28_write.txt`、content 8 字节 hex `57 32 38 57 53 37 37 37`=`W28WS777`、`file_read` 回读 verbatim | `evidence/f2_workspace.json` |
| T1 | context_meter | `d5022055` | 1M读数 | ✅ | 1M 档计量条 上下文 3.4K / 995.9K；档位读数非真压 | `evidence/f6_t1_compress.json` |
| F4 | skill | `d5022055` | 默认 | ⛔vacuous | w28-probe 设备侧注入确认（cat 验证 name+SENTINEL）、技能页可达但 w28-probe 未在可见 viewport、mistral 安全拒答（revealing secrets）未调 skill_load、NV-VL 重试两次取证超时 | `evidence/f4_skill_sentinel.json`、`evidence/f4_skillspage.json` |
| F7 | mcp | NA | NA | ⛔vacuous | MCP 服务器页可达 + 空态「还没有 MCP 服务器」、导航 ✅ 与行使分离 | `evidence/f7_mcp.json` |
| F6 | auto_compress | `d5022055` | 多档 | ⛔vacuous | 325 条 `auto_compact=noop reason=below_trigger`（全 below_trigger）、字符维未越 50227 ⇒ 案 B 正确行为非缺陷 | `evidence/f6_t1_compress.json` |
| F1 | multimodal | `d5022055` | 1M读数 | ⛔vacuous | 唯一含 IMAGE 端点（NV-MISTRAL）已切 + 800×400 双色真图已备，本轮未完成 SAF 选图取证 | `evidence/iter5_pre_attach.png` |
| F5 | manual_compress | `01e476bd` | 32K | ⛔vacuous | 本轮未行使（W23 16 轮配方未跑，额度/时间未足） | — |

> **本波实质增量（对比 W27）**：① 两支用户点名 openrouter 模型逐支真往返收敛（`W28BRAVO9`/`W28CHARLIE3`）；② 档位四档 badge 逐档回读；③ workspace 文件系统级落盘（ls 差集 +1 + 8 字节 hex + file_read verbatim，审批 exact args 完整）；④ 1M 档计量条读数 995.9K；⑤ **F6 两把尺子案 B 复跑**：325 条 `auto_compact=noop below_trigger` 全 below_trigger，坐实字符维未越阈值的正确行为定性。**未达格如实**：F4（mistral 安全拒答 + 取证超时）、F1（SAF 取证未完成）、F5（未行使）、F7/F6 环境前置，均 ⛔vacuous 不冒充。设备真相 `inputModalities`：仅 NV-MISTRAL `[TEXT,IMAGE]`，其余含用户点名 dots/north 均 `[TEXT]`。

## 4. 链内迭代记录（改 → 编译 → 真机 → 再看；11 轮）

> 链内「改→真机→再看」实证：iter2 冷启动切端点报 no eplist ⇒ 改夹具补 `drv.wake()` 后 SWITCH 成功；iter6 真机席 429 中断 ⇒ 主理人按铁律接手补完 iter7-收尾（不重试 spawn）。全部 11 轮逐条留痕，未触及 `iteration_ceiling=16`。本波真机由开发席（iter1-6）+ 主理人接手（iter7-收尾）接力完成。

| 轮 | action | observation | 四态 | attribution | evidence_ref |
|---|---|---|---|---|---|
| iter1 | E01 正控 NV-VL sentinel | `W28ALPHA7` 逐字、done+usage_changed、6.7s | ✅ | app | `_w28/evidence/iter1_e01.json` |
| iter2 | 夹具修正（链内「改」实证）：切端点前补 `drv.wake()`、落盘前 URL/key 脱敏、修 NameError | 原冷启动切端点报 no eplist ⇒ 修正后 SWITCH 成功 | ⚠️（夹具修正轮） | app | `_w28/iterations.jsonl` iter2 |
| iter3 | E02 切 OR-DOTS + sentinel | `W28BRAVO9` 逐字、SWITCH=true | ✅ | app | `_w28/evidence/iter2_e02.json` |
| iter4 | E03 切 OR-NORTH + sentinel | `W28CHARLIE3` 逐字 | ✅ | app | `_w28/evidence/iter3_e03.json` |
| iter5 | T0 档位轴四档回读 | 32K/256K/512K/1M badge 逐档实读 | ✅ | app | `_w28/evidence/iter4_tiers_w28.json` |
| iter6 | F1 多模态准备（切 mistral + 备 800×400 双色图） | 唯一含 IMAGE 端点就位、pre_attach 截图；**真机席此处 429 中断** | ⛔vacuous | env | `_w28/shots/iter5_pre_attach.png` |
| iter7 | F2 workspace（主理人接手补完） | ls 差集 +1、hex `W28WS777`、file_read verbatim、审批 1 次 | ✅ | app | `_w28/evidence/f2_workspace.json` |
| iter8 | F7 MCP 导航 + F4 技能页 | MCP 空态可达；技能页可达但 w28-probe 未在 viewport | ⛔vacuous | env | `_w28/evidence/f7_mcp.json`、`f4_skillspage.json` |
| iter9 | F4 skill sentinel（mistral） | mistral 安全拒答 revealing secrets、未调 skill_load | ⛔vacuous | env | `_w28/evidence/f4_skill_sentinel.json` |
| iter10 | F4 重试（NV-VL）×2 | 切端点成功但取证超时 SIGTERM（设备响应慢 + 额度紧张） | ⛔vacuous | env | `_w28/iterations.jsonl` |
| 收尾 | F6 压缩 log + T1 档位读数固化 | 325 条 below_trigger；1M 档 995.9K 读数 | ⛔vacuous(F6)/✅(T1) | app/env | `_w28/evidence/f6_t1_compress.json` |

---

## 5. 收尾契约

### §5.1 1→5 各步结果一览（四态 + 证据路径）

| 步 | 内容 | 四态 | 证据路径 |
|---|---|---|---|
| 步 1 | 范围裁决（四报告 10 条，全 `改方向=否`，P0-1 选 B）+ 做什么/不做写入 §1 | ✅ | 本文件 §1.1–§1.5 |
| 步 2 | 本地先行 + 编译确定性实证 + adb 调试 + 唤醒三步法 + 非全黑 screencap | ✅ | `_w28/g1_build.log`、`g1_rebuild.log`、`__screen0.png`、§1.4.1 |
| 步 3 | 真机矩阵 11 格按四态如实落账（✅6 / ⛔vacuous5）+ 链内迭代 11 轮（含真机席 429 中断后主理人接手补完） | ⚠️（6 ✅ / 5 ⛔vacuous，逐格如实；非全格通过） | §3、§4、`evidence/w28-matrix-summary.json` |
| 步 4 | 收尾契约：第 0 车零 `.kt` 最小修复（P0-1 三处同步 + 制品 + checklist + tip run）+ W28 段 3 行主理人单人落笔 | ✅ | §5.4；`git diff` 见 §5.4 |
| 步 5 | 四闸门 ✅ + 推送 + CI（run 链接见 §5.6） | ✅（闸门 ✅ / 推送 1 Build+Secret scan 全 success、run 链接见 §5.6） | §2、`_w28/g4_fulltest.log`、`g2_arch.log`、`g3_selftest.log`、§5.6 |

### §5.2 明确未做（逐条带挂账号 · 与 §1.3 同源）

> 完整 24 条见 §1.3。摘要：零 `.kt` 增量 / 拆分三方 / 两缺陷修复（W20-02、W24-01、W24-02）；journal 二选一与代数多值不代裁（W24-03 → W21-03 → W20-01 → W9-05）；1M 真压（W20-05 → W9-11）；MCP 明文（W20-06 → W5-03）；git 历史清理 / force-push（W20-07）；六项用户终局裁决（W20-08）；安全波（W20-04）；并号与台账模板（W20-10、W20-16）；守卫侧修复与扫描面扩展 / 六行漏号（W20-17、W20-03）；取证设施入仓（W20-14）；加长 system / 音频 / 图片预算四条（W20-11）；非对话模型进 chat（W21-05）；PC 结论升级设备可用（W20-12）；历史制品重出（W21-02 → W20-15、W27-02）；观测缝 / 工具记账（W21-06）；测试数上限（W20-09）；N-1 白名单「修复」（W20-09）；压缩硬出口判据（W21-04）；缺陷推导写成复现事实（W24-01、W24-02）；转代码波 / B1 压缩结构（W9-01、W12-02、W17-01、W23-04）；抹平历史格（W23-04）；G20 扩域（W20-17）；必红变异冒充真机重放（W27-02）；**P0-1 案 A（补写老号 / 撤回非缺陷定性 / 设红态入口，本波选 B 作废）**（W9-01、W23-04、W27-03）。

### §5.3 OPEN 挂账（触发信号 + 缓解动作 + 挂账号 · 唯一真值源 = `docs/06-deferred-ledger.md`）

| ID 段 | 状态 | 触发信号 / 缓解动作 |
|---|---|---|
| 本波段三行（裁决落地 / 制品空转 / 出口载体） | `OPEN` | 号段自由已确认、三行各带必红变异出口；触发信号与缓解动作详见台账段（待落笔，草案见 §5.4）。对机器守卫并集零贡献 |
| 更早波段三行（收尾 / 制品 / 挂账口径） | `OPEN` | 本波 P0-1 选 B 删虚报措辞 + 补 tip run；状态原样不销账；tip run 闭环方式待用户裁决 |
| 更早波段首两行（思考框 / 断词） | `OPEN` | 两缺陷源码锚点 live；本波若真机顺带观察只录证据、不写成复现事实；优先级 argument 维持 |
| 更早波段第 3 行（journal） | `OPEN` | 代数多值并存，用户未给二选一裁决，不新开行、不代裁 |
| 压缩老号（两把尺子） | `OPEN` | 案 B：below_trigger 正确行为、不写进压缩老号、不设红态入口；四外审同一缺陷禁拆多条 |
| 更早各段 / 历史销账行 | `OPEN` / `CLOSED`（不变） | 不删、不改状态、不写已核销、不重判、不并号 |

### §5.4 文档回写 diff

> 本波**零 `.kt`**。变更面 = 本波新文件 2 + 第 0 车最小行内修复 3 文件 + 台账新增段 + 制品 1（后两项待真机结果与落笔后补）。

| 文件 | 变更 | 性质 |
|---|---|---|
| `docs/analysis/w28-acceptance.md` | 新增：§1 裁决/做什么/不做/前置 + §2 闸门 + §5 收尾（§3/§4 待真机回填） | 本波新文件 |
| `docs/handoff-20261118-wave28.md` | 新增：交接摘要（零复合编号、不写推断为事实、不写下一链） | 本波新文件 |
| `docs/notes/w28-closeout-checklist.md` | 新增：三条出口判据的可跑命令 + 必红变异（判据1/2/3 均已实跑：正常态 OK、变异 RED）+ 人工兜底声明 | 本波新文件（P0-3 载体） |
| `docs/analysis/w27-acceptance.md` | 最小行内修复（本波实际 4 处）：§1.1 #9 与 §5.3 压缩老号行删「已写进 / 红态入口」改案 B 口径（P0-1 选 B 同步）；§5.1 步 5 改「推送后回填」口径；§5.6 推送 2 行补 `44241199431`@`cdaade7` + 文末链接段 | 最小行内修复（收尾义务），不重出全文、不改状态/结论/判级。凭据文件名字面量全树 0 命中（W27 波已删，本波未再引入） |
| `docs/handoff-20261118-wave27.md` | 最小行内修复：压缩老号行删「写行内注记作红态入口」改案 B 口径 | 最小行内修复 |
| `docs/06-deferred-ledger.md` | `W27-03` 缓解动作与出口判据删「写进压缩老号行内注记 / 红态引用」改案 B 口径（P0-1 同步）；新增 W28 段 3 行（主理人单人、待落笔） | 老行行内注记 + 新增段 |
| `evidence/w28-matrix-summary.json` | 新建：本波矩阵制品（修 P0-2 空转判据 + 8 字段 + 真机面断言 / coverage_note + 结构化注入样本），待真机结果生成 | 本波新增（仓根非 docs，触发 Build） |
| 两份历史 `source-review-*.md` | 未跟踪文件，保持 `??` 不入库 | 沿用既定口径 |

> 隐私处置：本波新建四文件初稿经交付物隐私自查（`_w28/privacy_scan.py`）抓出两处字面量（凭据文件名 / 本机绝对路径），已按占位符约定改为语义描述 / `<workspace>` / `<取证设施>`；正控 `SELF-AUDIT 10/10 detectors fired` 会响后才采信 `PRIVACY_RESULT=CLEAN`。
> 收尾补扫（含本波制品 `evidence/w28-matrix-summary.json` + handoff）：`SELF-AUDIT PASS 10/10` → `PRIVACY_RESULT=CLEAN: 0 hit across 7 files`。端点一律 8 位指纹；设备密钥只在设备侧密文，不注入不输出；真机截图存仓外 `shots/`（含端点 badge，不入库、不回灌整段）。

### §5.5 脱敏 / 零凭据结果 + 交给外部审查团队的制品清单

**脱敏正控自证**：交付物隐私自查工具运行 `SELF-AUDIT PASS 10/10 detectors fired` 后才采信 0 命中；本波 7 个将交付文件（含制品 JSON）全树复扫 PAT / 真 key / 设备序列号 / 本机绝对路径 / 未打码账号 / 凭据文件名 **0 命中**。端点一律 sha256-prefix8；端点凭据只在设备侧（六枚密文），本波不注入新凭据、不读 key 明文字段。

**交给外部审查团队的制品清单**（不含会话历史 / 凭据 / 端点标识 / 不含「建议下一链做什么」）：

1. `docs/analysis/w28-acceptance.md` — 本波验收物（§1–§5 全文）
2. `docs/handoff-20261118-wave28.md` — 本波交接摘要（零复合编号）
3. `evidence/w28-matrix-summary.json` — 机器可读矩阵制品（11 cells：✅6/⛔vacuous5；P0-2 四项修复：非零键集==值域、结构化注入样本+64hash、`na_cell_ids` 收幽灵项、`coverage_note` 明写真机覆盖 + 自证分组标注）
4. `docs/notes/w28-closeout-checklist.md` — P0-3 出口判据仓内载体（三条判据各配可跑命令 + 必红变异，均已实跑会响）
5. `docs/06-deferred-ledger.md` — 挂账真值源 W28 段 3 行 diff + W27-03 行内注记（P0-1 案 B 同步）
6. 本地闸门日志摘要（`_w28/g4_fulltest.log`、`g2_arch.log`、`g3_selftest.log`、`g2_finaltree.log` 的脱敏结论段；原始日志与截图在仓外取证设施，不入库）

> 以上均不含：会话历史 / 凭据字面量 / 端点完整标识（仅指纹）/ 对下一链的建议。原始截图与 JSONL 取证在仓外 `_w28/`，供审查团队按需索取，不随交付物入库。
> **诚实边界**：真机矩阵 ✅6/⛔vacuous5 **非全格通过**；F4/F1/F5 未完成取证如实 ⛔vacuous；档位读数 ≠ 1M 真压；F6 below_trigger 为案 B 正确行为、非缺陷。

### §5.6 CI / 提交序列（唯一真值源）

| 项 | 值 |
|---|---|
| 推送 1 | `cdaade7..7335605 HEAD -> main`（commit `7335605`，7 files：本波验收物 + 交接 + 台账 W28 段 + W27 收尾第 0 车最小修复 + 仓根非 docs 制品 `evidence/w28-matrix-summary.json`〔11 格〕+ 收口 checklist）；逐文件 add，两份历史源码日析全程 `??` 未入库；dry-run RC=0 → 实推 RC=0；PAT inline URL 一次性使用、`git remote get-url origin` 复核干净 URL（PAT 未落盘 remote）；noreply 身份；不 force-push、不 rewrite history |
| Build run（推送 1） | run `44251098599` **completed success**（event=push，由仓根非 docs 制品自身真实触发；五 job：Assemble Debug (JDK 21) / Lint / Secret scan / Arch guard / Unit tests；head_sha `7335605`） |
| Secret scan run（推送 1，独立） | run `44251098669` **completed success**（无 `paths-ignore`，覆盖同一 commit `7335605`） |
| 公开 API 四字段复核 | 两 run 各读 status/conclusion/event/head_sha：均 `completed`/`success`/`push`/`7335605`（零凭据字面量，仅四字段） |
| 最终树守卫复跑 | 推送前文档与制品落盘后复跑 `result=PASS ok=27 vacuous=1 violation=0 fault=0`、rc=0；G20 OK（112 handoff ID 全在台账、CLOSED 行证据非空）；本波 handoff 零复合编号，W28 段 3 行对 G20 并集零贡献 |
| CI 红次数 | **0** |
| 推送 2 | 本提交（纯文档回填 §5.6 与 §5.1 步 5 真实 commit 与 CI run 链接）：Build **0 run**（`paths-ignore` 既定口径，判据未行使，不算绿也不算红）；独立 Secret scan 覆盖同一 commit；本波不再第三次推送以避免自指循环 |
| 推送次数 | **2**（推送 1 含制品触发 Build 五 job 真跑 + 独立 Secret scan；推送 2 纯文档回填链接） |
| 归因 | 无构建失败 ⇒ 四分类未触发；环境类（F4 mistral 安全拒答 / F1 SAF 取证未完成 / F5 未行使 / F7 无外部 MCP / F6 字符维未越 / 平台明文阻断 / 用户视觉声明被设备证伪）不写成应用 bug；推送 1 含制品 ⇒ Build 有真绿 run；推送 2 纯文档 ⇒ Build 0 run 属既定口径，不写「构建再次通过」 |

**run 链接**（公开 API 逐项读 status / conclusion / event / head_sha，零凭据）：

- 推送 1 Build：https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44251098599
- 推送 1 独立 Secret scan：https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44251098669

> 本波零 `.kt` 改动 ⇒ 「CI 全绿」指 Build 五 job + 独立 Secret scan 结论 success，**不等于**真机矩阵全格通过（§3 如实登记 ✅6 / ⛔vacuous5），也不等于两条用户可见缺陷已复现。
> PAT inline 一次性使用后 remote 已复核干净；PAT 已使用一次即视为暴露（轮换为用户侧密钥管理动作，不在仓内写未闭环命令）。

---

> **本波止于交接**，不写「建议下一链做什么」。

<!-- APPEND_POINT -->
