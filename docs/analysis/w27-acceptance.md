# W27 收尾契约验收物 — 四份审查报告分歧裁决（含号段自由确认）+ 多模型×多上下文×多功能面真机矩阵 + W26 收尾契约第 0 车证据留痕（2026-11-18）

> 基线 `7cc5cb9`（W26 回填，`main`；本地与远端 HEAD 一致，零 `.kt` 改动起点）。
> 上游依据：用户 2026-11-18 指令（读记忆 / 上一波 handoff / OPEN 挂账行〔现役仓 `harness-v2/`，
> **非旧仓** `Android-Agent/`〕，列出「做什么 / 明确不做」写入验收物第 1 节；读四份审查人报告
> 〔`W27段挂账草案.md`、`W26深度审查与W27排期裁决.md`、
> `FastestAI-LiquidAgent-harness_W26深度审查与挂账分析及W27路线建议_2026-11-18.md`、
> `harness-w2326-review.md`〕并 **裁决分歧、不改方案大体方向**；用户给的 API〔6 链接清单 + 两支 openrouter 模型〕
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
> 密钥纪律：端点凭据**只在设备侧**（本波设备 `files/harness/secrets/` 六枚密文已在位，不注入新凭据、不输出 key 字段）；
> 本单 / 工具 schema / 工具结果 / 日志 / 截图 / 交接 / 记忆写入 **零凭据字面量**；端点一律以不可逆 8 位指纹出现
> （指纹 = UI 显示名 sha256 前 8 位）。设备序列号、本机绝对路径、凭据文件名、未打码账号字段一律不出现。
> 完整版即本文件；`docs/handoff-20261118-wave27.md` 只做交接摘要，冲突时以本文件为准。
> 本文件不写「建议下一链做什么」。
> 🔴 编号纪律：交接文档严格零「W 加数字加减号加数字」复合编号字面量（G20 只扫 `docs/handoff-*.md`，
> 不扫 `docs/analysis/`）；本文件 §1.3 等处的挂账号是台账溯源，写完整号合规必要。
> 台账纪律（§三.6）：新增台账行须由主理人**单人**落笔，外部审查草案不直接改真值源；本波号段自由已实跑确认。

---

## 1. 本波范围：做什么 / 明确不做（执行前钉死）

### §1.1 四份审查报告分歧裁决表（不改方案大体方向 = 用户指令面优先）

> 输入四报告：报告甲《W27 段挂账草案》（3 行零 `.kt`）、报告乙《W26 深度审查 · 挂账分析 · W27 排期裁决》（4 P0 + 8 P1）、
> 报告丙《W26 深度审查与挂账分析及 W27 路线建议》（暗物质账 / 撞号 / W24-02 优先）、报告丁《W23–W26 四波深度审查》（矩阵收官 / 两条缺陷精确化）。
> 五值裁决 = 采纳 / 采纳订正 / 顺延留痕 / 证伪 / 部分采纳。
> **十处分歧全部 `改方向 = 否`**：用户本单指令面优先，继续矩阵波；四报告「转代码波 / 矩阵无第 27 波理由 / 授权开 `.kt` 否则写永不实现」
> 主张整包顺延留痕、**不取消不推翻**；矩阵存续决策包维持 `OPEN`。采纳任何外审处方前均回现役树核状态
> （旧仓 W87 一次证伪 4 条全是时点差；W26 撞号证伪已先例）。

| # | 分歧议题 | 四报告立场 | 现役回源（本波核对） | 裁决 | 改方向 |
|---|---|---|---|---|---|
| 1 | 总路线：转代码波 / 接受零代码终态 vs 继续矩阵波 | 报告丙 §六「矩阵没有第 27 波存在的理由」；报告丁 §六「要么授权开 `.kt`，要么写永不实现」；报告甲只登记零 `.kt` 3 行；报告乙 §5 第 0 车零 `.kt` 收口 | 用户本单仍给 API + 6 链接清单 + 两支 openrouter 模型、明确要矩阵测试；`w26-acceptance.md:45` 指令面优先先例成立；矩阵存续决策包台账原样 `OPEN` | **顺延留痕**：继续矩阵波，不转代码波、不升格拆分波、不关矩阵、不写「永不实现」；四报告排期主张整包顺延 | 否 |
| 2 | W27 挂账 3 行落笔 vs 不落笔 | 报告甲给可直接贴入 3 行 + 号段扫描；报告乙 §5 第 0 车第 7 条同主张；报告丙 §三主张补登暗物质账；报告丁 §五.2 主张转代码波顺手落笔 | **本波实跑**：台账 `docs/06-deferred-ledger.md` 全表行首扫描 `W27` **0 行**（`W25` 0 行、`W26` 5 行属 W2.6 段），外审草案已先扫号段，无上波撞号；G20 仅存在性比对（`arch-guard.sh:1342/1356` 两边 `sort -u`）无唯一检测 | **采纳落笔（主理人单人）**：3 行零 `.kt` 可兑现、各带必红变异，号段自由已确认；六项用户终局裁决只留痕不代做；G20 机器唯一性属用户裁决域，人工闸门 + 必红变异兜底 | 否 |
| 3 | tip run 递归死结闭环 | 报告乙 P0-3 / §5：跨提交补两条 + 交用户终局裁决；报告甲 W27 段首行②同 | 本波公开 API 复核：`44234213197` success@`7cc5cb9c`、`44156141650` success@`da967be`，四字段齐；全树两 ID 在各自 §5.6 均 0 命中 | **采纳登记 + 跨提交补**：经公开 API 复核后补 W26/W25 §5.6 推送 2 行；闭环方式（随交付消息给出 / 改 CI 通道）属用户终局裁决域、**不代裁、不宣称闭环** | 否 |
| 4 | 凭据文件名回退（隐私红线） | 报告乙 P0-1：删字面量 + 加回脱敏正控；报告甲 W27 段首行①同 | `w26-acceptance.md:284` 在「0 命中」格写回凭据文件名，违反同文件抬头 `:22` 禁令；全树 1 命中即此，W22 同型第三次回退 | **采纳隐私红线最小修复**：删字面量保「6 链接无 key 字面量」语义、加行内注记、加回脱敏正控（必红样本）；不重出历史制品全文、不改状态/结论/判级 | 否 |
| 5 | 制品 schema 反向退化 | 报告乙 P0-4：补回 8 字段 + 统一枚举 + K_feature_enum + mutation 可重放；报告甲 W27 段次行同 | `evidence/w26-matrix-summary.json` 丢 `assertion_source`/`runner_fingerprint_prefix16` 等 8 字段、`schema` 串降级、`state_counts` emoji 键 vs `cells[].state` 双轨无钉桥断言、`feature_face` 两个越界值无 `K_feature_enum` | **采纳（仅本波新制品）**：本波制品以 W25 完整字段为先例补齐、统一枚举域、加钉桥断言与 `K_feature_enum`、mutation 带可重放样本；**不重出 W26 历史制品**（历史冻结） | 否 |
| 6 | G20 无 ID 唯一检查 | 报告乙 P0-2：加 checklist 人工闸门 + 必红变异；报告丙 §一.2 / 报告丁 §五：机器化唯一预检 | `arch-guard.sh:1342/1356` 两边 `sort -u` 后只做存在性比对，重复号被抹平；台账 `:540` 亦写需另加判据 | **采纳人工闸门 + 不改守卫**：checklist 两条（新号段必扫全表行首占用 / 验收物挂账号台账须有独立行）各配必红变异；改守卫属用户裁决域 | 否 |
| 7 | W24-01/02 缺陷优先级与修复时机 | 报告丙 §五.2 主张优先修 W24-02；报告丁 §二主张数据完整性级最高；报告甲/乙整包顺延需 `.kt` | 两缺陷源码锚点仍 live；`EXPECTED_TESTS=818`、`LINE_CAPS`/`TOTAL_LINE_CAP` 全在位 ⇒ 修复确需 `.kt` + 拆三方（G10 余 4/5/4） | **顺延留痕、维持 `OPEN`**：优先级 argument 登记（`W24-02` 数据完整性 > `W24-01` 渲染 UI），不修 `.kt`、不写成已复现 | 否 |
| 8 | 收尾契约第 8 次 / §1.2「收口」与 §5.4「只登记」矛盾 | 报告乙 P1-2/P1-3；报告甲 W27 段首行③④ | W26 §1.2 #11 写「收口」、§1.1 #3/§5.4 写「只登记」；§5.1 步 5 写「完成」但 tip run 缺 | **采纳口径订正（仅本波自洽）**：本波判据表写「收口 = 指到 `文件:行` + 公开 API 复核 + 最小行内修复」，不重出历史正文；数字口径本波统一（六项终局裁决） | 否 |
| 9 | F6「两把尺子」真机实锤入账 | 报告乙 P1-7；报告甲 W27 段末行⑤；报告丁 §三 | W26 §3 F6 token 维越预算 30.8K/27.9K、`committedChars` 字符维未越阈值 50227 ⇒ `auto_compact=noop below_trigger` 正确行为，只留验收物未挂老号缓解动作 | **采纳（本波 W28 订正为案 B）**：`below_trigger` 是字符维触发线的正确行为、非缺陷；原「写进 W9-01/W23-04 行内注记作为下一代码波红态入口」经本波回源为**零 diff 虚报**（W28 §1.1 #3），该处方作废、措辞删除，`W9-01`/`W23-04` 维持 `OPEN` 原样零 diff；实锤留本波验收物 §3/§4，不设代码波红态入口 | 否 |
| 10 | journal 代数口径 | 报告乙「第 21 次顺延」；报告丁 §一「18→19→20」；报告甲 W27 段末行④ | 台账 `W24-03` 仍记 19、W26 验收物写 20/21 两值并存 | **只留痕、不代裁二选一**：代数多值并存如实登记，属用户终局裁决域；本波不自造统一值 | 否 |

> 裁决合计 10 条：**采纳/采纳订正 6（#2/#3/#4/#5/#6/#8/#9 中 6 项）/ 顺延留痕 3（#1/#7/#10）/ 部分采纳 1（#9 行内注记不入账机器）**。全部 `改方向 = 否`。
> **本波唯一新增落地动作 = 号段自由下的 W27 段 3 行台账主理人单人落笔 + 第 0 车零 `.kt` 最小结构修复 + 矩阵新证据**（非代码方向变更）。
> 注：#2 号段自由与 #1 继续矩阵同属指令面优先；#4 隐私红线是本波唯一改上一波正文的特批（仅删字面量 + 行内注记，不重出）。

### §1.2 做什么（本波判据 · 执行前钉死）

| # | 判据 | 行使方式 | 出处 |
|---|---|---|---|
| 1 | 范围 §1：四报告逐条裁决 + 做什么/不做写入本波验收物第 1 节；交接侧挂账号转中文段位 | §1.1 裁决表 + §1.2/§1.3 写入本波验收物第 1 节 | 用户指令 + 四报告 |
| 2 | **第 0 车零 `.kt` 收口**：删隐私字面量 + 正控；跨提交补两条 tip run；最小行内修复（W23-01 注记 / W24 §3 两处 / W25 §3 抹平 / 数字口径 / G1-G4 模块数）；W26 制品字段由本波新制品补齐 | 逐条指到 `文件:行`；公开 API 复核 run 四字段齐；不重出历史制品正文 | 报告乙 §5 第 0 车 + 报告甲 W27 段 |
| 3 | **W27 段 3 行台账落笔（主理人单人）**：号段自由已实跑确认，零 `.kt`、各带必红变异、可对账出口 | 段头 + 3 行 `OPEN`；落笔前 `grep -cE '^\| W27-'` = 0 留痕；注入重复号样本 checklist 判红 | 报告甲草案 + 报告乙 §5 + 台账 §三.6 |
| 4 | **端点轴**：多模型（openrouter `dots-3-note-preview:free`、`north-mini-code:free` 两支用户点名；其余走 6 链接清单〔build.nvidia.com〕） | 设备侧 fast-fail 预筛后真往返收敛取证；指纹 = UI 显示名 sha256 前 8 位；多模态以真机 `inputModalities` 为准，不写用户标注为已验证 | 用户指令；端点池见 §1.4 |
| 5 | **档位轴**：多上下文（32K / 256K / 512K / 1M **读数**，非真压） | 档位真切换回读；1M 档读数 + 计量条；**档位读数 ≠ 生效窗口 ≠ 1M 真压** | 用户指令 |
| 6 | 多模态输入 | 真图 + SAF 选图 chip + 语义问答；先验 `inputModalities`（无 IMAGE 端点 chip 灰如实 ⛔vacuous） | 用户指令 |
| 7 | 工作空间文件读写 | `file_write`/`file_read` 严格 `ls -1` 差集 + mtime + 内容逐字 sentinel + 审批 exact args + 反背题对照 | 用户指令 |
| 8 | skill 功能面 | 设备侧 `SKILL.md` 注入 → 冷启动「来源：file」→ `@name` 点选 → sentinel 逐字；无工具记账以 UI 副作用为准并带 caveat | 用户指令 |
| 9 | MCP 现状确认 | 顶栏返回箭头绕系统 BACK → MCP 列表页可达 + 空态截图；导航（✅）与行使（⛔vacuous）分开落账；平台明文阻断不放开 | 用户指令 |
| 10 | 手动 + 自动上下文压缩 | 32K + 新会话夹具；手动 `dropped>0`、自动 `applied=true+dropped>0`；F6 字符维口径本波复跑；`already_compressed` 单指针按缺陷登记不写成主线闭环 | 用户指令 + 报告乙 P1-7 |
| 11 | 本地先行 + adb 调试 + 链内迭代 | 先本地 UI/功能测试 + adb 调试；真机「改→编译→真机→再看」链内循环；每轮追加一行证据（action/observation/state/attribution/evidence_ref/caveat） | 用户指令 |
| 12 | 四闸门 + 推送 + CI | G1–G4 实跑后推送；每次 commit + run 链接；脱敏正控先自证会响；凭据 inline 一次性、用后清 remote；不 force-push、不 rewrite history | 用户指令 + `AGENTS.md` |
| 13 | 脱敏 + 零凭据 + 零复合编号 + 四态纪律（全局） | 端点 8 位指纹；产物零 PAT/真 key/本机路径/设备序列号/未打码账号/凭据文件名；交接文档零复合编号字面量；未触发 ≠ 通过、推断带 caveat | 用户指令 + 守卫自指陷阱 |

### §1.3 明确不做（逐条带挂账号，全部原样 `OPEN`）

| # | 不做项 | 挂账号 |
|---|---|---|
| 1 | 拆分三方整包 / 任何 `.kt` 增量 / 调行数与测试数上限（含修 `W24-01/02` 两缺陷所需 `feature-chat` 余量腾挪） | W20-02、W24-01、W24-02 |
| 2 | journal / FreezeClock 二选一代裁决（第 21 次顺延只留痕、不新开行；两代数口径不统一，二选一仍待用户） | W24-03 → W21-03 → W20-01 → W9-05 |
| 3 | 设备侧 1M 真压（双阻断未解，只做 1M 档位读数） | W20-05 → W9-11 |
| 4 | MCP 明文联调 / 放开明文流量开关 / https 夹具 | W20-06 → W5-03 |
| 5 | git 历史清理 / force-push / rewrite history | W20-07 |
| 6 | 六项用户终局裁决代做（journal / ≥3 端点 / 占位符口径 / 并行键期望行为 / tip run 闭环方式 / 借鉴独立输入面） | W20-08 |
| 7 | 安全波整包 | W20-04 |
| 8 | 台账同物多名并号 / 台账模板改动 | W20-10、W20-16 |
| 9 | 守卫侧修复 / G20 扫描面扩展 / 六行漏号补齐 / 幽灵号双断言 K/L 入守卫 | W20-17、W20-03 |
| 10 | 取证设施（驱动/脱敏脚本）入仓——本波断言补丁只落仓外 | W20-14 |
| 11 | 加长 system / 音频 / `core-device` 独立波 / 图片预算四条 | W20-11 |
| 12 | 非对话模型（视频生成 / 视频超分 / 图片超分）进 chat 端点矩阵 | W21-05 |
| 13 | PC 侧免费节点结论升级为「设备侧可用」 | W20-12 |
| 14 | 历史制品正文重出（含 W26 制品 8 字段回填只落本波新制品）/ 更早各波正文改写 | W21-02 → W20-15、W27-02 |
| 15 | 摘 VACUOUS / 观测缝 / 工具记账上线（需 `.kt`） | W21-06、W20-03 |
| 16 | `@Test 818→≥850` / 改测试数上限 | W20-09、W20-02 |
| 17 | N-1 白名单行为面「修复」——现役完整键已在、测试已在，拒绝对不存在的 bug 动刀 | W20-09 |
| 18 | 把手动压缩 `dropped>0` / 自动压缩 `applied=true` 写成硬出口判据或达标 ✅ | W21-04 |
| 19 | 把两条用户可见缺陷推导写成「已复现事实」 | W24-01、W24-02 |
| 20 | 转代码波 / 拆分三方 / B1 压缩结构落地（属用户裁决域，整包顺延留痕） | W9-01、W12-02、W17-01、W23-04 |
| 21 | 把 W23 F6 ✅ / F2 ✅ 文件系统级与 W24/W25 的「同 W23」「回复级/⚠️」抹平 | W23-04 |
| 22 | G20 扫描面扩域（含「← 原建议」注释形态进比对域）——先批量补六行漏号，扩面前置未满足 | W20-17 |
| 23 | 本波制品 11/11 必红变异冒充「真机重放」——规则定义自证与真机执行分开标注（沿用 W26 诚实口径） | W27-02 |

### §1.4 设备可行性 + 端点池前置（方案席独特维度）

#### 1.4.1 目标设备与已知怪癖

- **目标机**：OPPO PDRM00，Android 13（SDK 33，ColorOS）；纯 API Harness（模型云端、无端侧模型）。
- **唤醒三步法（本波主理人实测通过）**：`KEYCODE_WAKEUP` → `KEYCODE(224)` → `SCREEN_STATE_ON` → `screencap` 保存（466397 B，人工判读状态栏 / 气泡 / 思考框 / 输入栏可见，非纯黑）⇒ 存活判据 ✅；`pm install-commit` 在灭屏会永久挂起，故必须先唤醒。证据 `_w27_screen0.png`（仓外，脱敏见 §5.5）。
- **adb 通道**：一律有线、`-P 15037`、工作空间绝对路径；含路径命令 `MSYS_NO_PATHCONV=1`；daemon 每条命令后重启 ⇒ 关键读回放同一条调用；读设备二进制用 `adb exec-out`。
- **Compose bounds**：`uiautomator dump` 对 Compose bounds 不作唯一判据，点按以 UI 回读 / 截图 / 日志尾为准；发送键用 live bounds。
- **题面与日志**：`input text` 非 ASCII 触发 NPE、且是追加写 ⇒ 题面只用 ASCII（空格 `%s`）、每题先点「新对话」；回灌只取脱敏判据与 `deviceLogTail`，不回灌整段 logcat。
- **密钥纪律实测**：设备 `files/harness/secrets/` 六枚密文（`.bin`）已在位、`hasSecret=true`，本波不注入新凭据、不读 key 明文字段；`endpoints.json` 仅以 8 位指纹形态落验收物。

#### 1.4.2 端点池勘察（本波脱敏实测）

设备侧端点池 6 支（指纹 = UI 显示名 sha256 前 8 位，本波 `hashlib.sha256(name)` 逐支复算与 W26 指纹一致）：

| name-fp | 公开 modelId | 来源 | contextLength | inputModalities | 本波角色 |
|---|---|---|---|---|---|
| `01e476bd` | nvidia/nemotron-3.5-super-vl-120b-a12b | build.nvidia.com | 1048576 | `[TEXT]` | 当前端点；端点轴/档位轴主力 |
| `b995dc45` | dots-studio/dots-3-note-preview:free | openrouter（用户标注 512K 双通） | 512000 | `[TEXT]` | 用户点名；**设备真相 TEXT-only**，多模态声明不背书 |
| `d5022055` | mistralai/mistral-large-4-1t-a52b | build.nvidia.com | 1048576 | `[TEXT, IMAGE]` | 唯一含 IMAGE 端点；多模态真图语义面 |
| `a9ae3f9e` | stepfun-ai/step-5-preview | build.nvidia.com | 1048576 | `[TEXT]` | 端点轴备选 |
| `b0113b2c` | cohere/north-mini-code:free | openrouter | 256000 | `[TEXT]` | 用户点名；真往返可达性验 |
| `cfd5fbda` | google/gemma-4-26b-a4b-it:free | openrouter | 262144 | `[TEXT]` | 历史 HTTP 429，fast-fail 预筛 |

> 清单中 `wan2.2-animate` / `vsr` 为非对话模型，明确不做（挂账号 W21-05）；`deepseek-v4.1-flash` 历史 420s 超时，只做 fast-fail 预筛、如实登记 ⛔。密钥全程只在设备侧复用端点 id，不注入新凭据、不输出 key 字段。

#### 1.4.3 前置表现势化

**本波可做集 = 前置表之子集**：档位读数（非真压）/ 端点池 6 支交集 / 无外部 MCP 端点 / 自动压缩夹具几何 ⇒ 本波增量 = 号段自由下的台账落笔 + 第 0 车隐私与 run 最小修复 + 多功能面按判据重行使。vacuous 格按环境侧登记、不冒充 pass。

### §1.5 本波定位一句话 + 主理人通报要点

**定位一句话**：W27 = 「四报告分歧裁决（含号段自由确认）+ 多模型×多上下文〔直至 1M 读数〕×多功能面真机矩阵 + W26 收尾契约第 0 车零 `.kt` 最小修复」复合波——用户指令面优先、零 `.kt`、裁决不改方向，唯一新增台账事实 = W27 段 3 行主理人单人落笔。

**给用户的主理人通报要点（≤5 行）**：
1. 四报告「矩阵无第 27 波理由 / 转代码波 / 授权开 `.kt` 否则写永不实现」主张整包顺延留痕、不改方向；矩阵存续与六项终局裁决维持 `OPEN` 不代裁。
2. 十处分歧逐条裁决全 `改方向=否`；号段自由（W27 全表 0 行）已实跑确认，与上波撞号证伪不同 —— 本波据草案落 3 行台账（主理人单人）。
3. 两条 tip run 经公开 API 复核 success（四字段齐），跨提交补 W26/W25 §5.6；闭环方式属用户裁决域，只登记不代裁。
4. 隐私字面量（凭据文件名）1 命中在上一波「0 命中」格，本波按红线删字面量 + 加回脱敏正控；本波新制品补齐 W26 退化的 8 字段。
5. 本地四闸门全绿，真机矩阵按四态如实落账，档位读数 ≠ 1M 真压；本波止于交接。

---

## 2. 本地闸门结果（四态 + 证据路径）

| 闸门 | 四态 | 实跑结果 | 证据路径（仓外） |
|---|---|---|---|
| G1 全量 JVM 单测（fulltest） | ✅ | `tests=818 failures=0 errors=0 skipped=0`（14 模块）；严格行首 `@Test` = 818 与 `build.yml:135 EXPECTED_TESTS='818'` 一致（宽松 grep 819 是 KDoc 一处 `@Test` 字样，不采用） | `_w27/g4_fulltest.log` |
| G2 arch-guard | ✅ | `result=PASS ok=27 vacuous=1 violation=0 fault=0`；G20=112 handoff ID 全在台账、CLOSED 行证据非空；G15b `usesCleartextTraffic="false"` OK | `_w27/g2_arch.log` |
| G3 arch-guard-selftest | ✅ | `30/30 PASS`（224s），含 G20 必红变异「删台账行」会红、VAC-G1b/VAC-G21 空转机制自证 | `_w27/g3_selftest.log` |
| G4 fulltest 汇总（tests=818） | ✅ | 与 G1 同为 `818/0/0/0`，14 模块全绿（G1/G4 同值口径） | `_w27/g4_fulltest.log` |

> 四闸门实跑前置；零 `.kt` 改动故与 W26 基线一致。注意：本波 G2/G3 是在**第 0 车文档修复与台账落笔之后**复跑（终态守卫），其中 G20 的 112 ID 未含本波新增 W27 行（handoff 严格零复合编号，3 行对 G20 并集零贡献，见 §1.1 #2）。
> 脱敏正控：外部扫描工具 `_w23/scan_artifacts.py` `SELF-AUDIT PASS 11/11 detectors fired (incl. cred_filename)`，正控会响后才采信 0 命中；本波制品生成脚本内置 11 形态正控 + artifact blob 扫描，均 CLEAN（见 §5.5）。
> `rc=0` ≠ 产物已重生成的铁律已守（本波无 APK 出包需求，纯文档 + 制品波）。

## 3. 真机矩阵逐格结果（本波 11 格：✅8 / ⛔vacuous3）

> 端点轴与档位轴分开判定；档位读数 ≠ 生效窗口 ≠ 1M 真压；功能面判据前置先备，前置不满足即 ⛔vacuous 不冒充通过。
> 端点一律 8 位指纹（sha256 显示名前 8 位），逐支复算一致；密钥全程只在设备侧，不注入、不输出。

| 格 | 面 | 端点-fp | 档位 | 四态 | 关键判据（脱敏） | 证据路径 |
|---|---|---|---|---|---|---|
| E01 | endpoint_axis | `01e476bd` | 默认 27.9K | ✅ | 正控逐字 `W27ALPHA7`、done=true+usage_changed=true、6.7s、`auto_compact=noop below_trigger` | `_w27/evidence/iter1_e01.json`、`iter1_log.json` |
| E02 | endpoint_axis | `b995dc45` | 512K 读数 | ✅ | 用户点名 OR-DOTS 逐字 `W27BRAVO9`、11.0s、live bounds 处理位移输入行 | `_w27/evidence/iter2_e02.json` |
| E03 | endpoint_axis | `b0113b2c` | 256K 读数 | ✅ | 用户点名 OR-NORTH 逐字 `W27CHARLIE3`、11.0s、2.8k/324 | `_w27/evidence/iter3_e03.json` |
| T0 | tier_axis | `01e476bd` | 32K/256K/512K/1M | ✅ | 六档选项实见、四档 badge 逐档回读；档位读数≠真压 | `_w27/evidence/iter4_tiers_final.json`、`iter4_tier1M.json`、`iter10_tier32k.json` |
| F1 | multimodal | `d5022055` | 1M 读数 | ✅ | `添加图片` chip（唯一 TEXT+IMAGE 端点）、800×400 真图 SAF、逐字 `W27-VISION-747`、39.9s | `_w27/evidence/iter5_multimodal.json`、`shots/iter5_attached.png` |
| F2 | workspace | `01e476bd` | 1M 读数 | ✅ | `file_write` 审批 exact args、`ls -1` 差集 +1、mtime + 8 字节 hex、`file_read` 逐字 `W27WS777` | `_w27/evidence/iter6_ws_write.json`、`iter7_ws_read.json` |
| F4 | skill | `01e476bd` | 1M 读数 | ✅ | `w27-probe` 技能页来源：file、@菜单滚动可见、`skill_load {"name":"w27-probe"} 2ms`、`[SKILL BODY]`、逐字 `SENTINEL-W27-K8M4P` | `_w27/evidence/iter8_skill_sentinel.json`、`iter8_skills_page.json` |
| F7 | mcp | NA | NA | ⛔vacuous | MCP 服务器页可达 + 空态「还没有 MCP 服务器」；导航 ✅ 行使 ⛔（无外部端点） | `_w27/evidence/iter9_mcp.json` |
| F5 | manual_compress | `01e476bd` | 32K | ⛔vacuous | 「压缩上下文」可达、确认对话框后「没有可压缩的内容」、log 无 `dropped>0` | `_w27/evidence/iter10_manual_compress_done.json` |
| F6 | auto_compress | `01e476bd` | 32K | ⛔vacuous | token 维涨至 14.2K/27.9K 越软预算方向，全程 `auto_compact=noop below_trigger`，字符维未越 50227 ⇒ 不触发（两把尺子复现） | `_w27/evidence/iter10_compress_log.json`、`iter11_bigread.json` |
| T1 | context_meter | `01e476bd` | 14.2K 读数 | ✅ | 大 `file_read` 后上下文 14K→14.2K/27.9K、13008 cached 91.4%、档位读数非真压 | `_w27/evidence/iter11_bigread.json` |

> **本波实质增量**：① 两支用户点名 openrouter 模型第二次逐支真往返收敛（`W27BRAVO9`/`W27CHARLIE3`）；② 档位四档 badge 逐档回读；③ 新注入 `w27-probe` skill 在技能页可见且 @ 菜单滚动可点选、`skill_load` 2ms 硬证据 + sentinel 逐字；④ workspace 文件系统级落盘（ls 差集 + mtime + 8 字节 hex）；⑤ 真图语义逐字 `W27-VISION-747`；⑥ **F6 两把尺子真机复现**（token 维越软预算但字符维未越 ⇒ `below_trigger` 正确行为，非缺陷）。
> **未达格如实**：F7/F5/F6 ⛔vacuous，均为外部前置 / 环境侧 / 夹具几何未越，不冒充 pass、不改写历史结论。设备真相 `inputModalities`：NV-VL/OR-DOTS/NV-STEP/OR-NORTH/OR-GEMMA 均 `[TEXT]`，仅 NV-MISTRAL `[TEXT,IMAGE]` —— 用户「OR-DOTS 视觉双通」声明以真机为准、不写成已验证。

## 4. 链内迭代记录（改 → 编译 → 真机 → 再看；每轮一行证据）

| 轮 | action | observation | 四态 | attribution | evidence_ref |
|---|---|---|---|---|---|
| iter0 | 唤醒三步法 + 端点池勘察 | SCREEN_STATE_ON + screencap 非全黑；6 支指纹复算一致 | ✅ | device | `_w27_screen0.png`、`iter4_tiers_final.json` |
| iter1 | NV-VL 正控 sentinel | `W27ALPHA7` 逐字、done+usage_changed、6.7s | ✅ | app | `iter1_e01.json` |
| iter2 | 切 OR-DOTS + sentinel | `W27BRAVO9` 逐字、11.0s、live bounds 处理位移 | ✅ | app | `iter2_e02.json` |
| iter3 | 切 OR-NORTH + sentinel | `W27CHARLIE3` 逐字、11.0s | ✅ | app | `iter3_e03.json` |
| iter4 | 档位轴四档回读 | 当前 256K/512K/1M/32K 逐档实读 | ✅ | app | `iter4_tiers_final.json` |
| iter5 | 多模态真图语义 | 首轮空格未编码截断为 What 仍读出 `W27-VISION-747` | ✅（带 caveat） | app | `iter5_multimodal.json`、`iter5_multimodal_v2.json`（newchat 清附件负控） |
| iter6 | file_write workspace | ls 差集 +1、mtime + hex `W27WS777` | ✅ | app | `iter6_ws_write.json` |
| iter7 | file_read 回读 | `W27WS777` 逐字、本会话授权继承 | ✅ | app | `iter7_ws_read.json` |
| iter8 | skill w27-probe | `skill_load 2ms` + `SENTINEL-W27-K8M4P` | ✅ | app | `iter8_skill_sentinel.json` |
| iter9 | MCP 页导航 + 空态 | 「还没有 MCP 服务器」 | ✅（导航）/ ⛔vacuous（行使） | env | `iter9_mcp.json` |
| iter10 | 32K 压缩夹具 4 轮 + 手动压缩 | `auto_compact=noop below_trigger`；手动「没有可压缩的内容」 | ⛔vacuous | app | `iter10_compress_log.json`、`iter10_manual_compress_done.json` |
| iter11 | 大文件读长上下文 | 14.2K/27.9K、13008 cached 91.4%、仍 below_trigger | ✅（长上下文行使） | app | `iter11_bigread.json` |

> 链内「改→真机→再看」实证：iter2 输入行位移 → 改用 live bounds；iter5 空格未编码 → v2 用 `%s`（并发现 newchat 清附件负控）；iter6 done 判据被审批弹窗误判 → 改点「允许一次」后逐字回读；iter4 档位 set_tier 慢致 SIGTERM → 改 live bounds 直点 + 确认按钮取 cy 最大者。`iter3` 首个 tail 切换超时 SIGTERM 但 ask 主结果已落盘、未编造。全部 12 轮逐条留痕，无触及最大真机迭代上限的僵局。

## 5. 收尾契约

### §5.1 1→5 各步结果一览（四态 + 证据路径）

| 步 | 内容 | 四态 | 证据路径 |
|---|---|---|---|
| 步 1 | 范围裁决（四报告 10 条，全 `改方向=否`）+ 做什么/不做写入 §1 | ✅ | 本文件 §1.1–§1.5 |
| 步 2 | 本地先行 + adb 调试 + 唤醒三步法 + 非全黑 screencap | ✅ | `_w27_screen0.png`、§1.4.1 |
| 步 3 | 真机矩阵 11 格按四态如实落账（✅8 / ⛔vacuous3）+ 链内迭代 12 轮 | ⚠️（8 ✅ / 3 ⛔vacuous，逐格如实；非全格通过） | §3、§4、`evidence/w27-matrix-summary.json` |
| 步 4 | 收尾契约：第 0 车零 `.kt` 最小修复 + W27 段 3 行主理人单人落笔 | ✅ | §5.4；`git diff` 见 §5.4 |
| 步 5 | 四闸门 ✅ + 推送 + CI（run 链接见 §5.6） | ✅（闸门 ✅ / 推送 1 CI ✅ / tip run 见 §5.6；本波 W28 据公开 API 四字段复核回填） | §2、`_w27/g4_fulltest.log`、`g2_arch.log`、`g3_selftest.log`、§5.6 |

### §5.2 明确未做（逐条带挂账号 · 与 §1.3 同源）

| # | 不做项 | 挂账号 |
|---|---|---|
| 1 | 拆分三方整包 / 任何 `.kt` 增量 / 调行数与测试数上限 | W20-02、W24-01、W24-02 |
| 2 | journal / FreezeClock 二选一代裁决（第 21 次顺延只留痕） | W24-03 → W21-03 → W20-01 → W9-05 |
| 3 | 设备侧 1M 真压（只做 1M 档位读数） | W20-05 → W9-11 |
| 4 | MCP 明文联调 / 放开明文流量开关 | W20-06 → W5-03 |
| 5 | git 历史清理 / force-push / rewrite history | W20-07 |
| 6 | 六项用户终局裁决代做 | W20-08 |
| 7 | 安全波整包 | W20-04 |
| 8 | 台账并号 / 模板改动 | W20-10、W20-16 |
| 9 | 守卫侧修复 / G20 扫描面扩展 / 六行漏号补齐 | W20-17、W20-03 |
| 10 | 取证设施入仓（驱动/脱敏脚本/制品生成器只在仓外） | W20-14 |
| 11 | 加长 system / 音频 / `core-device` 独立波 / 图片预算四条 | W20-11 |
| 12 | 非对话模型（视频/超分）进 chat 端点矩阵 | W21-05 |
| 13 | PC 侧免费节点结论升级为「设备侧可用」 | W20-12 |
| 14 | 历史制品正文重出（W26 制品 8 字段只落本波新制品） | W21-02 → W20-15、W27-02 |
| 15 | 摘 VACUOUS / 观测缝 / 工具记账上线（需 `.kt`） | W21-06、W20-03 |
| 16 | `@Test 818→≥850` / 改测试数上限 | W20-09、W20-02 |
| 17 | N-1 白名单行为面「修复」（现役完整键已在、测试已在） | W20-09 |
| 18 | 手动/自动压缩 `dropped>0`/`applied=true` 写成硬出口或达标 ✅ | W21-04 |
| 19 | 把两条用户可见缺陷推导写成「已复现事实」 | W24-01、W24-02 |
| 20 | 转代码波 / 拆分三方 / B1 压缩结构落地 | W9-01、W12-02、W17-01、W23-04 |
| 21 | 把 W23 F6/F2 ✅ 与 W24/W25「同 W23」「回复级/⚠️」抹平 | W23-04 |
| 22 | G20 扫描面扩域（先批量补六行漏号） | W20-17 |
| 23 | 本波必红变异冒充「真机重放」 | W27-02 |

### §5.3 OPEN 挂账（触发信号 + 缓解动作 + 挂账号 · 唯一真值源 = `docs/06-deferred-ledger.md`）

| ID 段 | 本波状态 | 触发信号 / 缓解动作摘要 | 本波动 |
|---|---|---|---|
| W27 段 3 行（收尾 / 制品 / 挂账口径） | `OPEN`（主理人单人落笔） | 逐字见台账 W27 段段头 + 3 行；各带必红变异出口判据；对 G20 并集零贡献 | 新增 3 行，号段自由已实跑确认 |
| W24 段首两行（思考框 / 断词） | `OPEN`（原样） | 本波真机未复现即不写事实；`.kt` 修复整包顺延 | 不改状态 |
| W24 段第 3 行（journal 顺延） | `OPEN`（原样） | 代数多值并存如实留痕，不代裁二选一、不写统一值 | 不新开行 |
| 压缩老号（两把尺子） | `OPEN`（原样） | F6 本波复现 token 维越/字符维未越，不触发为正确行为；本波 W28 案 B 订正：**未写进行内注记、不设下一代码波红态入口**（原措辞系零 diff 虚报，见 W28 §1.1 #3），实锤留验收物 §3/§4 | 不新开号 |
| 更早各段 / 历史销账行 | `OPEN` / `CLOSED`（原样） | 不删、不改状态、不并号、不重判 | 零改动 |

### §5.4 文档回写 diff

| 文件 | 变更 | 性质 |
|---|---|---|
| `docs/analysis/w27-acceptance.md` | 新增：§1 裁决/做什么/不做/前置 + §2 闸门 + §3 矩阵 + §4 迭代 + §5 收尾 | 本波新文件 |
| `docs/handoff-20261118-wave27.md` | 新增：交接摘要（零复合编号、不写推断为事实、不写下一链） | 本波新文件 |
| `docs/06-deferred-ledger.md` | 新增 W27 段 3 行（主理人单人）；W23-01 注记「§2」悬空改指真实小节（状态/触发/缓解原句不动） | 新增段 + 老行行内注记 |
| `docs/analysis/w26-acceptance.md` | 最小修复：删隐私文件名字面量（保语义）、step5「完成」订正、推送 2 tip run `44234213197`@`7cc5cb9c` 回填 + 链接、G4 模块数 12→14、「五项」→「六项」（3 处）、§1.2 #11 收口同义注记、build run「逐项复核」加跨时点 caveat | 最小行内修复（收尾义务），不重出全文、不改状态/结论/判级 |
| `docs/analysis/w25-acceptance.md` | 最小修复：推送 2 tip run `44156141650`@`da967be` 回填 + 链接、§3 抹平订正注记、4 处迭代数口径统一（iterations[]=10 vs §4 12 行） | 最小行内修复 |
| `docs/analysis/w24-acceptance.md` | F2/F6 两行「同 W23」加订正注记（不抹平历史格） | 最小行内修复 |
| `evidence/w27-matrix-summary.json` | 新建：schema 归一 + 11 cells（8✅/3vacuous）+ 12 iterations + 5 断言各配可重放变异 + 8 字段补齐 + 枚举桥 + K_feature_enum | 本波新增（仓根非 docs，触发 Build） |
| 两份历史 `source-review-*.md` | 未跟踪文件，保持 `??` 不入库 | 沿用既定口径 |

### §5.5 脱敏 / 零凭据结果 + 交给外部审查团队的制品清单

**脱敏正控自证**：外部扫描工具 `_w23/scan_artifacts.py` 运行 `SELF-AUDIT PASS 11/11 detectors fired (incl. cred_filename)` 后才采信 0 命中；本波制品生成器内置 11 形态 forbidden 列表 + artifact blob 扫描，`privacy.result=CLEAN`。全树复扫：PAT / 真 key / 设备序列号 / 本机绝对路径 / 未打码账号 / 凭据文件名 **0 命中**（上一波唯一 1 处凭据文件名字面量已在本波删除，见 §5.4）。端点一律 sha256-prefix8；设备密钥只在设备侧密文，不注入不输出。

**交给外部审查团队的制品清单**（不含会话历史 / 凭据 / 端点标识 / 不含「建议下一链做什么」）：

1. `docs/analysis/w27-acceptance.md` — 本波验收物（§1–§5 全文）
2. `docs/handoff-20261118-wave27.md` — 本波交接摘要
3. `evidence/w27-matrix-summary.json` — 机器可读矩阵制品（11 cells / 12 iterations / 5 断言必红变异 + 8 字段 + 枚举桥 + K_feature_enum）
4. `docs/06-deferred-ledger.md` — 挂账真值源 W27 段 3 行 diff + 老行注记
5. 本地闸门日志摘要（`_w27/g4_fulltest.log`、`g2_arch.log`、`g3_selftest.log` 的脱敏结论段；原始日志与截图在仓外取证设施，不入库）

> 以上均不含：会话历史 / 凭据字面量 / 端点完整标识（仅指纹）/ 对下一链的建议。原始截图与 JSONL 取证在仓外 `evidence_ref` 指向路径，供审查团队按需索取，不随交付物入库。

### §5.6 CI / 提交序列（唯一真值源）

| 项 | 值 |
|---|---|
| 推送 1 | `7cc5cb9..df8b482 HEAD -> main`（commit `df8b482`，7 files：本波验收物 + 交接 + 台账 W27 段 + 三份历史验收物最小修复 + 仓根非 docs 制品 `evidence/w27-matrix-summary.json`〔11 格〕）；逐文件 add，两份历史未跟踪源码日析全程 `??` 未入库；dry-run RC=0 → 实推 RC=0，PAT inline URL 一次性使用、`git remote -v` 复核干净 URL 0 命中；noreply 身份；不 force-push、不 rewrite history |
| Build run | run `44240854225` **completed success**（event=push，由仓根非 docs 制品自身真实触发；6 checks 经公开 API 逐项复核全 success：Assemble Debug (JDK 21) / Lint (baseline gate) / Secret scan (gitleaks + trufflehog) / Arch guard (26 guards + mutation selftest) / Unit tests；head_sha `df8b482`） |
| Secret scan run（独立） | 同一 push 的独立 `secret-scan.yml` run `44240854286` **completed success**（无 `paths-ignore`，覆盖同一 commit） |
| 最终树守卫复跑 | 推送前文档与制品落盘后复跑 `result=PASS ok=27 vacuous=1 violation=0 fault=0`、rc=0；G20 OK（112 handoff ID 全在台账、`CLOSED` 行证据非空）；本波 handoff 零复合编号，W27 段 3 行对 G20 并集零贡献 |
| CI 红次数 | **0**（上限 2，未触） |
| 推送 2 | 本提交（纯文档回填 §5.6 真实 commit 与 CI run 链接）：Build **0 run**（`paths-ignore` 既定口径，判据未行使，不算绿也不算红）；独立 Secret scan `44241199431` success@`cdaade7`（本波 W28 经公开 API 四字段复核：completed/success/push/head_sha=cdaade7/Secret scan）覆盖同一 commit；本波不再第三次推送以避免自指循环 |
| 推送次数 | **2**（上限 3，未触；推送 1 含制品触发 Build 五 job 真跑 + 独立 Secret scan，推送 2 纯文档回填链接） |
| 归因 | 无构建失败 ⇒ 四分类未触发；环境类（无外部 MCP / 夹具字符维未越 / 平台明文阻断 / 用户视觉声明被设备证伪）不写成应用 bug；推送 1 含制品 ⇒ Build 有真绿 run；推送 2 纯文档 ⇒ Build 0 run 属既定口径，不写「构建再次通过」 |

**run 链接**（公开 API 逐项读 status / conclusion / event / head_sha，零凭据）：

- 推送 1 Build：https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44240854225
- 推送 1 独立 Secret scan：https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44240854286
- 推送 2 tip Secret scan（本波 W28 回填，四字段 API 复核 success@`cdaade7`）：https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44241199431

> 本波零 `.kt` 改动 ⇒ 「CI 全绿」指 Build 五 job + 独立 Secret scan 结论 success，**不等于**真机矩阵全格通过（§3 如实登记 8 ✅ / 3 ⛔vacuous），也不等于两条用户可见缺陷已复现。
> PAT inline 一次性使用后 remote 已复核干净；PAT 已使用一次即视为暴露（轮换为用户侧密钥管理动作，不在仓内写未闭环命令）。

---

> **本波止于交接**，不写「建议下一链做什么」。

<!-- APPEND_POINT -->
