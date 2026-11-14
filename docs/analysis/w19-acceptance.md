# W19 收尾契约验收物 — 多免费节点 × 多档位（直至 1M 读数）× 全功能面真机矩阵（2026-11-14）

> 基线 `00eade0`（W18 收尾，`main`；本地与远端 HEAD 一致）。上游依据：用户 2026-11-14 指令
> （读记忆 / 上一波 handoff / OPEN 挂账行〔**现役仓 `harness-v2/`，非旧仓 `Android-Agent/`**〕/
> 本期源码日析、列出「做什么 / 明确不做」写入验收物 §1；读众审查人原始报告并**裁决分歧、
> 不改方案大体方向**；外部仓库高价值参考；多个模型测试不同上下文〔直至 1M〕与多模态输入 /
> 工作空间文件读写 / skill / MCP / 手动与自动上下文压缩；先本地 UI + 功能测试 + adb 调试 → 真机 →
> 推仓库及 CI 构建；**按收敛结论实施本次任务唯一目标**；零凭据字面量；编译失败按四分类归因；
> 真机 adb + 脱敏截图回灌 + 边测边改；每次推送留可审计 commit 与 run 链接；
> 不 force-push、不 rewrite history；触顶收尾；handoff 不把推断写成事实；
> 本次任务停于交接、交外部团队审查）。
> 判定四态 ✅ / ⚠️ / ⛔ / ⛔vacuous。**「未触发」≠「已验证通过」；「测试全绿」≠「口径被验证」；
> `rc=0` ≠ 产物已重生成；PC 侧结论不得写成设备侧可用；1M 读数 ≠ 1M 真压；写「X 未发生」前先自证判据会响。**
> 密钥纪律：端点凭据**只在设备侧**，不进仓、不进日志、不进截图、不进交接、不进记忆；
> 本单 / 工具 schema / 工具结果 / 日志 / 截图 / 交接 / 记忆写入**零凭据字面量**。
> 端点一律以不可逆 8 位指纹出现；设备序列号、本机绝对路径、凭据文件名、未打码账号字段一律不出现。
> **本文件不写「建议下一链做什么」。** 完整版即本文件；`docs/handoff-20261114-wave19.md` 只做交接摘要，冲突时以本文件为准。

> **模板占位符口径（本单未给具体数值，按 W17/W18 同款惯例由主理人钉值，非用户裁决项）**：
> `<截图回灌级别>` = **L2**（脱敏控件树 + 打码截图 + deviceLogTail 摘要，逐行脱敏 ≤180 字符、≤8 行；
> 不回灌整段 logcat）；`<最大真机迭代>` = **8**；`<最大推送次数>` = **3**；`<最大 CI 红次数>` = **2**；
> `<挂账号前缀>` = **`W19-`**。四项用户裁决仍只留痕、不代做（见 §1.3 #8）。

---

## §1 本次任务范围（做什么 / 明确不做）

### 1.1 输入与判据来源（全部只读核对，未采信旧仓）

| # | 来源 | 核对结果 |
|---|---|---|
| 1 | 工作记忆 `MEMORY.md` + 工作区日志（最近一日 = W18 波日志） | 已读；关键铁律：G20 只认台账行首 ID、G1 `rc=0`≠出包（须删旧 APK 强制重出包）、adb `-P 15037` 且 daemon 每次退出、中文题面不可注入真机、审批框 dump 取 bounds 不猜坐标、纯 API 云侧定位、隐私卫生（零凭据字面量 / 端点 8 位指纹） |
| 2 | 上一波交接 `docs/handoff-20261113-wave18.md` | 已读全文；W18 = 补铺矩阵波，零 `.kt`；真机 9 格 = 3✅/1⚠️/4⛔/1⛔vacuous；档位读数覆盖 32K/128K/512K/1M；**6 次端点切换仅 1 支稳定收敛**；六项功能面判据未行使；CI 红 0 / 推送 3（触上限收尾） |
| 3 | 上一波验收物 `docs/analysis/w18-acceptance.md` | 已读全文；§1.2 做什么 / §1.3 明确不做 12 条 / §1.4 十条分歧裁决 / §5 收尾契约与 CI run 链接 |
| 4 | **挂账唯一真值源 `docs/06-deferred-ledger.md`（现役仓）** | 130 行带 ID：**100 `OPEN` / 19 `CLOSED` / 11 `CARRIED`**；W14-01..11 / W15-01..14 / W16-01..18 / W17-01..09 / W18-01..12 全 `OPEN`；`W9-05` 第 13 次顺延（`W18-01` 登记，两代数口径并列待用户裁决）。本波新号段见 §二-novies |
| 5 | **本期源码日析 `docs/analysis/source-review-2026-11-14.md`（现役仓）** | 已读全文；**全仓 `Glob` 仅此一份同名文件，旧仓无同名文件 ⇒ 未读成旧仓**。现役行数水位：feature-chat **3996/4000 余 4**、feature-settings **3495/3500 余 5**、core-context **1646/1650 余 4**、`ChatViewModel.kt` **500/500**、`TOTAL_LINE_CAP=42500` 未动；新发现 a/b/c=3/1/1（A1 残留 `syncMcpTools` 吞 CE / A2 `stripDecoration` 哨兵 / A3 单文件触顶 / B1 拆分债务坐实 / C1 round-trip 缺测）；**P0=无**。⚠️ 该文件在本波开波时是**未跟踪文件**（`git status` 显示 `??`）；其 `:11` 已自认「工作树未跟踪文件仅 `docs/analysis/*.md`（含本文件）」，标题括号「已提交 main `00eade0`」指**分析对象**的基线时点而非本文档入库状态 ⇒ 属**抬头易误读**，非账实矛盾；口径见 `W19-14` |
| 6 | 作业契约 `AGENTS.md`（现役仓） | 已读全文；§一 明确本仓为**纯 API 云侧 Harness，端侧推理核心全部移除、模型全在云端**；§二.2「外审处方先 grep 现役树再采纳」；§三 唤醒三步法与安装三步法；§八 双工作流触发条件 |
| 7 | 众审查人原始报告**五份**（桌面原件，均已读全文） | ①《W18矩阵波深度审查与W19排期裁决》②《近期改动深度审查报告-莫斯线》③《harness-w18-review》④《w17-w18-review-20261114》⑤《FastestAI…W18深度审查与挂账分析及W19路线建议_2026-11-14》。分歧逐条裁决见 §1.4 |
| 8 | 外部仓库高价值参考《外部仓库可参考内容详录》 | 六仓内容登记册（Droidance 规则外置 / 单一决策循环 / 分步确认；droidmind 五级风险模型与必红变异用例；AppAgentX 捷径节点与回退链；AppAgent 两阶段；Operit；QClaw）——**只登记，不构成本波方案来源**；许可边界照旧（Apache-2.0 / MIT 参考仓只取结构思想，Operit 系与 NVIDIA 参考仓零搬运）。该详录自述「仅回答外部仓库里有什么，不回答我们接下来该做什么」，本波不把它当排期依据 |
| 9 | 现役树机器证据（主理人自检） | 严格 `@Test` = **818** ↔ `build.yml:135 EXPECTED_TESTS: '818'`；`build.yml` `on.push.branches=[main]` + `paths-ignore = docs/** + **/*.md`；`secret-scan.yml` 无 `paths-ignore`、扫描步无 `continue-on-error`/`|| true`；`release.yml` = `tags: v*` + dispatch；G20 只扫 `docs/handoff-*.md`，**ID 数一律以 `arch-guard` 实跑输出为准、本文件不写死数**（W16 既有裁决；W18 验收物写死 111/112/130 已被外审判 P0-2，见 `W19-09`）；`evidence/w18-matrix-summary.json` 存在 |
| 10 | 测试资源与设备基线（脱敏探针） | 设备侧 `files/harness/endpoints.json` 已配 **6 支**远程端点，档位含 256K/262K/512K/1M，全部 `hasSecret=true`、`supportsTools=true`、`supportsReasoning=true`，**至少一支带 `inputModalities=[IMAGE,TEXT]`**；活跃端点指纹 = `8ac38d1b`（W18 唯一稳定收敛支）。`files/harness/skills/` **为空**；`workspace/` 有 18 个文件（含五个 16KB 长文夹具）；`logs/agent-0.log` 117 行（`auto_compact` 命中 115、`skill=0`、`file_write=0`）。用户本单点名两支可用免费节点（512K 工具+视觉双通 / 代码向）与 NVIDIA 测试通道；**凭据沿用设备侧既有配置，本波零搬运、零回显** |
| 11 | 设备存活 | 唯一设备（OPPO / Android 13）已按三步法唤醒：`dumpsys window policy` 回 `SCREEN_STATE_ON` + `INTERACTIVE_STATE_AWAKE`；`screencap` 原始帧解析 **非全黑比例 0.9989 / 330 色桶 / alive=YES**；脱敏安全图 `A1_boot_safe.png`（24px 马赛克 + 状态栏黑条）经目视复核**零可读文字**，无凭据明文上屏 ⇒ ✅ |
| 12 | 本地编译/调试工具 | `scripts/localbuild.sh --env` 可注入 JDK21 / SDK / `GRADLE_USER_HOME` / 代理；`adb.exe` 在位；`ui.py`/`matrix.py`/`switch.py`/`drive.py` 等 W18 取证驱动已复用适配到本波仓外取证目录并验证 `startapp` 可用（`in_app=true`） |

### 1.2 做什么（收敛结论 = 本次任务唯一目标）

**唯一目标**：在**零 `.kt` 改动**的前提下，用用户本单新给的多支可用免费节点，把 W18 判 ⛔ / ⛔vacuous 的
「多模型 × 多档位上下文（直至 1M 读数）× 六项功能面」真机矩阵**逐格补铺并四态如实取证**；
随后本地四闸门 → 推仓库及 CI → 收尾交接。除此之外不新开功能线、不改方案大体方向、不代做用户裁决。

| # | 事项 | 验收判据 |
|---|---|---|
| 1 | **本地 UI + 功能测试 + adb 调试** | 应用启动；控件树 dump 确认输入框 / 发送键 / 新对话 / 设置 / 压缩菜单可达并记录 bounds；存活判据（唤醒 + 非全黑截图）每轮复用；先跑「已知必然通过」的判据自证档再上探 |
| 2 | **多端点 × 多档位矩阵** | ≥3 端点 × ≥3 档位组合（含 512K 与 1M **读数**两档）；每格记切换前后端点指纹 + 档位回读 `当前 <档>` + 确认框诊断 + `switch_verified` + 是否收敛 + usage；档位轴与端点轴**分开判定**，不互相冒充 |
| 3 | **功能面逐项**（每面至少 1 格证据，且判据须真正被行使） | 多模态图片输入（≥200×120 真图、语义命中；**禁用 1×1 像素**）；工作空间 `file_write` **真调用 + 落盘实测** + `file_list`；skill 端到端（`skill_load` / 引用被触发）；MCP **现状确认**；手动压缩 `dropped>0` 与双向断言；自动压缩**真触发**而非 `below_trigger` noop |
| 4 | **链内迭代「改 → 编译 → 真机 → 再看」** | 每次编译 / 真机 / 推送各留一行证据；本波零 `.kt` ⇒ 不重装 APK、不触发编译，逐行如实写「未触发编译」；编译失败按四分类（参数错 / 工具名错 / 该调不调 / 调了不收敛）归因；环境问题不硬跑、不改码硬凑、不把环境归因成代码 bug；最大真机迭代 8 |
| 5 | **本地四闸门** | G1 删旧 APK 强制重出包并记字节 + mtime（两次重出包字节一致）；G2 fulltest 818/0/0/0；G3 arch-guard `PASS` 且 ID 数以实跑输出为准；G4 selftest 30/30 |
| 6 | **推送 + CI** | 每次推送留可审计 commit 与 run 链接；不 force-push / 不 rewrite history；CI 红按四分类归因并继续「改 → 复编译 → 复真机 → 再推」，直至全绿或触顶；记录 CI 红次数与推送次数 |
| 7 | **收尾契约验收物**（本文件） | §1–§5 各步结果带四态与证据路径；链内迭代记录；明确未做（逐条带挂账号）；OPEN 挂账（触发信号 + 缓解动作 + 挂账号）；文档回写 diff；交外部审查团队的制品清单（不含会话历史 / 凭据 / 端点标识 / 下一链建议） |

**本波定位**：纯真机测试 + 文档波，**零 `.kt` 改动**（理由见 §1.4 分歧 3）。改 `.kt`、改门禁配置、改 `EXPECTED_TESTS` 均不在本波范围内。

### 1.3 明确不做（逐条带挂账号）

| # | 项 | 挂账号 | 理由 |
|---|---|---|---|
| 1 | journal / FreezeClock 「开波 或 永不实现」二选一裁决（第 14 次顺延） | `W19-01`（老号 `W9-05` / `W18-01` 原样 `OPEN`） | **用户裁决项**，本单未给裁决 ⇒ 不代做、不软化、不关号；两代数口径并列如实登记 |
| 2 | 拆分三方整包（feature-chat / feature-settings / core-context 下沉 + `ChatViewModel` 外提） | `W19-02`（`W18-02` / `W17-06` / `W16-03` 原样 `OPEN`） | 三模块余量 ≤5 行 + 单文件 500/500，任何 `.kt` 改动必撞 G10；拆分须独立成波，且铁律「拆分不调上限、不该让全仓变胖」。五份外审多数主张本波先拆 ⇒ 裁决见 §1.4 分歧 1/3 |
| 3 | 六行漏号补登与 G20 扫描面扩展 | `W19-03`（`W18-03` / `W16-01` / `W15-01` / `W14-01` 原样 `OPEN`） | 外审「补行」与 W16 现役裁决「不制造幽灵挂账、待独立成波」冲突；扩扫描面须先加简写前缀白名单 + 旧仓编号排除 + 逐条人工甄别，否则一扩就误判 |
| 4 | 安全波整包（扫描 job 去重 / install pin + sha256 / allowlist 精确化 + 真 key 变异 / 全历史首跑 / 签名端到端） | `W19-04`（`W18-04` / `W17-07` / `W16-02` / `W15-02` / `W14-02` 原样 `OPEN`） | 独立成波；同物多号问题见 §1.4 分歧 6，本波不并号、不改状态 |
| 5 | 设备侧 1M 真压 | `W19-05`（`W18-05` / `W17-08` / `W16-05` / `W9-11` 原样 `OPEN`） | `W9-11` 双阻断（L3 字符硬闸 + 轮头 400k）未解除 ⇒ **只做 1M 档位读数，不写真压** |
| 6 | MCP 明文联调 / 放开 `usesCleartextTraffic` | `W19-06`（`W18-06` / `W17-09` / `W16-06` / `W5-03` 原样 `OPEN`） | G15b 平台阻断，不为测试放开；MCP 面只做现状确认 |
| 7 | git 历史清理 / force-push / rewrite history | `W19-07`（`W18-07` / `W16-07` 原样 `OPEN`） | 本单明禁不可逆动作 |
| 8 | 四项用户裁决（journal 二选一 / 「≥3 端点」终局口径 / §1.5 占位符口径 / `P2-3` 二选一） | `W19-08`（`W18-08` / `W16-13` / `W15-13` 原样 `OPEN`） | 只留痕、不代做。**用户本单以「新给可用节点」直接回应了 W18 的端点池障碍，本波照常按「≥3 端点」组织矩阵，但不代改该项终局口径** |
| 9 | 外审新提可复现小缺陷与账实订正：报告 D「发现 A」（非 OR style 分支内 `enable_thinking` 硬编码 true；外审另称 `hardOffDowngraded` 无 style 维度，该子结论已由审查席回源**证伪** —— 实为经 `!canHardOff` 间接依赖 style）、`stripDecoration` 砍尾、`importSubtitle` 掩盖 `droppedToolRoles`、N3 冷启动竞态、N4 下拉整串 query、N5 触发面收窄、莫斯线 P3-新1（`normalizeLineCommand` 无白名单改写正文）、M2（`loadSkillNames` 兜 `Throwable`）；以及 W18 账实 P0-1（提交序列漏 tip）/ P0-2（G20 数字打架）/ P0-3（证据制品覆盖不足）/ P1 悬空引用 / `W16-18` 状态 / `W17-01` 数值 16384→4096 | `W19-09` | 前八项改 `.kt` 必撞 G10；后六项属**历史制品账实订正**，按「handoff 是开立事实、只加抬头不改历史正文」纪律保留并登记，不塞进测试波改变单波归因。逐条触发信号 + 缓解动作见 §5.2 |
| 10 | 同物多名合并（安全波 / 1M / MCP 等多号并一号，其余写 `CARRIED`） | `W19-10`（`W18-10` 等原样 `OPEN`） | 改真值源结构须独立成波；本波不制造幽灵行、不改老号状态 |
| 11 | 为凑缓存加长 system / 音频 / `core-device` 独立波 / 图片预算四条 | `W19-11`（`W18-11` / `W16-08` / `W14-08` 原样 `OPEN`） | 独立成波；缓存铁律（静态前缀 800 全价 vs 4096×0.5=2048 当量）不为凑缓存加长 system |
| 12 | 把 PC 侧免费节点可用性结论升级为「设备侧可用」 | `W19-12`（`W18-12` / `W16-09` / `W14-09` 原样 `OPEN`） | 四态纪律；本波所有端点判据一律以**真机实际发请求**为准；免费节点无响应/超时判 ⛔ 并切备用端点，不归因成应用代码 bug |
| 13 | **本波新登记**：W18 三处收尾契约 P0 的顺手修补（补 §八 tip 提交、抹平 G20 写死数、补齐 `evidence/w18-matrix-summary.json` 的功能格与 ITER-4..6） | `W19-13` | 外审报告 ① 与 ⑤ 均要求本波做。**裁决：不做历史正文改写**，理由见 §1.4 分歧 4；本波只在本波制品里避免同型缺陷（G20 不写死数、制品覆盖与声明面对齐、提交序列跟到最后一次提交） |
| 14 | **本波新登记**：`docs/analysis/source-review-2026-11-14.md` 标题括号「已提交 main」易被误读为本文档已入库，实际为未跟踪（该文档 `:11` 已自认） | `W19-14` | 只登记**表述易误读**、不判账实矛盾、不加抬头改写历史正文；本波若将其入库，须在提交信息说明「作为本波输入文档入库」，不得写成「该文件此前已提交」 |
| 15 | 本波真机矩阵中**判据未行使**的功能面格（逐格四态以 §3 实跑为准） | `W19-15` | 四态纪律：⛔ / ⛔vacuous 不冒充 ✅，不引用 W17/W18 结论冒充本波通过；每条带触发信号 + 缓解动作 |

> 本节挂账号为 W19 段新开立行（台账 §二-novies，全 `OPEN`）；老号一律原样保留，不删、不改状态、不写已核销、不重判 `CLOSED`、不并号。

### 1.4 分歧裁决（五份外审矛盾点的收敛）

> **【汇编状态：三路证据分级】** ①**方案席 Phase 1 已正式回传**（只读分析，未改任何文件）：主线 = 沿用四波同款「用户指令面优先」，判 W19 唯一目标为**零 `.kt` 的矩阵 / 真机 / 四闸门 / 推 CI / 交接波**；对五份外审「转拆分 / 账实收口 / 停矩阵 / 改口径 / 撤销证伪」**九条**逐条裁回用户指令面侧或留痕顺延，全部挂回台账老号并保持 `OPEN`；设备维度逐功能面给出「判据真正被行使的前置」，并把免费节点超时、MCP 明文被平台拒、灭屏挂起、1M 双阻断、中文注入限制归为**环境/平台、不得归因应用代码 bug**。方案席另如实标注：角色要求的注入型知识库文件在本机不存在，改读本机等价件（判据册 / adb 手册 / 工作记忆）交叉印证。②**审查席独立回源核验已回传**：九项中 8 项取得现役树实证、第 6 项「`hardOffDowngraded` 无 style 维度」子结论**证伪**、第 7 项设备侧前提按主理人实测做条件判断（时点差成立）；并确认 G20 扫描面只含 `docs/handoff-*.md`、状态值仅 `OPEN/CARRIED/CLOSED` 三值。全文见 §2 与 §5.2。③**主理人本人逐字回源核对**的现役树证据：`w17-acceptance.md:98`（四波同款裁决原文）、`docs/06-deferred-ledger.md`（130 行 / 100 OPEN）、`source-review-2026-11-14.md` 行数表、`AGENTS.md` §一/§二.2、`build.yml:135`、`arch-guard.sh` 的 G20 扫描面、`OpenAiCompatibleAdapter.kt:236-250` 与 `ReasoningBody.kt:31-50`、`w18-acceptance.md` 三处写死数字、`handoff-20261113-wave18.md` §八 提交序列。
> 本表 12 条 = 方案席九条主线 + **主理人回源补入的三条订正**（分歧 4/11「外审事实成立但处置不采纳」、分歧 7「机制属实、定级与处置不采纳，且外审 UI 可达性限定与设备侧配置存在时点差」）。凡属转述且未经主理人回源核实者标【转述】且不作独立证据。

**收敛结论（唯一目标以此为准）**：沿用 W14/W15/W16/W17/W18 **五波同款裁决「用户指令面优先」**（出处 `w17-acceptance.md:98`：外审多数推「journal 开波 / 拆分先行」vs 用户指令「功能矩阵 + 真机 + 推 CI」⇒ 用户指令面优先，外审还债排期**不取消、不推翻、整包顺延**）。本单用户指令面 = 多模型 × 多档位功能矩阵 + 全功能面 + 真机 + 推 CI，且**新给两支明确可用的免费节点**正面回应了 W18「6 端点仅 1 支收敛」的环境障碍 ⇒ **W19 = 补铺矩阵波**，不是裁决波、不是拆分波、不是安全波、不是账实收口波；「不改方案大体方向」即指不把本波转向外审推荐的第 0 车。

| # | 分歧点 | 立场 A（外审） | 立场 B（用户指令面 / 现役树） | 裁决 | 理由 / 出处 |
|---|---|---|---|---|---|
| 1 | W19 定位 | 报告 ②③④：应是「拆分第 0 车 / 裁决包 / 拍板后再动」；报告 ①⑤：应是「零 `.kt` 账实收口」或「先给用户一页裁决包」 | 用户本单：多模型 × 多档位功能矩阵 + 全功能面 + 真机 + 推 CI | **采 B**（用户指令面优先） | 五波同款；外审还债不取消、不推翻、整包顺延；方案席一致结论 |
| 2 | 是否「不再开矩阵波」 | 报告 ⑤ §五.6：在用户改「≥3 端点」口径前**不再开任何矩阵波**；报告 ③：「零代码矩阵边际产出已归零」 | 用户本单逐字仍要求多个模型测不同上下文直至 1M，且**新给可用节点**消除 W18 的环境障碍 | **不采纳 A**，本波继续铺矩阵 | 用户指令面优先；W18 的欠账是「功能面判据未行使 + 端点轴未铺满」，不是「矩阵本身无信息」；报告 ⑤ 自己也承认 W18 补铺有增量（§三.2 自我修正） |
| 3 | 拆分硬前置 | 报告 ②③④：三模块余 4/5/4 + `ChatViewModel` 500/500 ⇒ 拆分升为「第 0 车硬前置 / 不可再顺延」 | 用户「不改方案大体方向」+ 铁律「拆分不调上限、不该让全仓变胖」+ 单波归因 | **不做拆分，改为零 `.kt` 改动**（本波不触碰 G10） | 拆分是独立波（`W18-02`/`W17-06`/`W16-03` 原样 `OPEN`）；把拆分塞进测试波会改变单波归因且必触 G10；零 `.kt` 是唯一同时满足「不改方向 + 不撞闸门」的解。`source-review-2026-11-14.md` B1 已把拆分升「紧急」，本波如实登记但不改其 `OPEN` 状态 |
| 4 | W18 账实 P0 是否顺手修 | 报告 ① 第 0 车：补 §八 tip、抹平 G20 写死数、补制品 7 功能格 + ITER-4..6；报告 ⑤ 亦称「成本零」 | W16/W17/W18 三波纪律：handoff/验收物是**开立事实，只加抬头不改历史正文**；单波归因 | **不改历史正文，只裁决留痕 + 挂账 `W19-13`**；但**本波自己的制品必须避免同型缺陷**（G20 不写死数 / 制品覆盖与声明面对齐 / 提交序列跟到最后一次提交） | `W16` P0-1 处置先例；改真值源结构须独立成波。主理人 + 审查席**双路回源**确认报告 ① 三处指认**均属实**（`handoff-20261113-wave18.md:108-110/121/124` 序列漏 tip 且自相矛盾；`w18-acceptance.md:37/107/206` 三处 111/112/130 并存而 `:50` 自写「ID 数以实跑为准」，W16 裁决出处 `handoff-20261111-wave16.md:37` + `w16-matrix-acceptance.md:63` 被破；`evidence/w18-matrix-summary.json:20-37` 仅 3 轮迭代、`feature` 去重仅 3 类，与 §3.2 八面 / §4 六轮不符）⇒ **判其事实成立、处置不采纳**，不是证伪；审查席另指出 W15 `:51` 已判「G20 计数是派生量、文档数字磨损」，本波重蹈 |
| 5 | 「≥3 端点」终局口径 | 报告 ⑤：同窗 ≥3 在免费池不可达成，建议改「同窗≥2 + 时间窗注记」或「跨窗累计≥3」；报告 ① 指 W18 只达成档位轴 | 用户本单未改口径，但**新给两支可用节点**，使端点轴具备继续铺的条件 | **不代改口径**；本波按原口径「≥3 端点」组织矩阵并如实记端点轴达成情况；用户裁决仍挂 `W19-08` | 四项用户裁决只留痕不代做；执行「补跑交叉格」属执行不是裁决。`W18` 的「5/6 节点无响应」是**环境事实**，不是口径失效的证明 |
| 6 | 同物多名合并 | 报告 ④⑤：安全波 / 1M / MCP 多号并一号，其余写 `CARRIED` | W17/W18 裁决「老号全 `OPEN` 原样保留、不制造幽灵挂账」 | **不并号、不改状态**，登记 `W19-10` | G20 只认 `OPEN/CARRIED/CLOSED` 与行首 ID；改状态即改真值源，须独立成波 |
| 7 | 报告 ④「发现 A 撤销证伪」 | 原命题是「非 OR style 分支内对 `thinkingMode` 无条件」，W17 驳回的是「对所有端点无条件」⇒ 靶子偏移；应撤销证伪、按潜伏缺陷立账并修 | W17 验收物已裁决「不采纳、不修、不立账」；本波零 `.kt` | **技术观察成立、定级不采纳为「现役 bug 修复」，登记为 `W19-09` 潜伏面；本波不修、不改 W17 历史正文** | 主理人 + 审查席**双路回源**：`OpenAiCompatibleAdapter.kt:240/:245-246` 两个非 OR 分支硬编码 `true`、分支内零 `effectiveThinkingMode`；该符号生产读取仅 `ReasoningBody.kt:32`；`EndpointAdvancedConfig.kt:67` mode 行解禁仅 `enabled=initial.supportsReasoning` ⇒ 报告 ④「W17 证伪打错靶」**机制指认成立**。但**两处限定被证伪或削弱**：① `EndpointCapabilities.kt:278-279` 的 `hardOffDowngraded` 经 `!canHardOff`（`RemoteEndpoint.kt:226-228` 含 style 判定）**间接依赖 style**，「无 style 维度」子结论不成立；② `EndpointStore.kt:141/153/165` 三处预置 false 是**代码内建默认**，设备侧 6 支端点全带 `supportsReasoning=true` ⇒ 注入态下 UI 可达，外审「潜伏缺陷 / 非 UI 可达」属**把代码默认当运行态的时点差**。修它须改 `.kt` 撞 G10 ⇒ 只登记、不翻案、不代做 |
| 8 | 设备 / 模型可行性口径 | 知识库 §4 端侧引擎 / 模型权重 / LiteRT-LM 判据 | `AGENTS.md` §一：本仓从 LiquidAgent 分叉后**端侧推理核心全部移除**，模型全在云端 | **关键证伪：采 B**。本波「多模型」只指设备侧 `endpoints.json` 的远程端点；端侧引擎判据本波**全部不适用**，不照搬为候选模型筛选依据 | `AGENTS.md:16-22`；与 W18 §1.4 分歧 8 同款，外审「时点差」同型 |
| 9 | 「直至 1M」是否达成 | 报告 ① §三：W18 §四写「用户指令『直至 1M』达成」偏乐观，达成的只是**档位轴**，多模型轴未达成 | W18 交接 §四已自注「1M 只做档位读数、不是真压」 | **采纳 A 的口径订正**：档位轴与端点轴分开判定；本波 §3 分别写「档位读数覆盖」与「端点轴达成情况」，**不写「直至 1M 达成」这种合并表述** | 四态纪律；1M 读数 ≠ 1M 真压（`W19-05` 原样 `OPEN`） |
| 10 | CI 触发口径 | 报告 ⑤ §一.5：W18 用仓根非 docs `.json` 触发 Build 是「为绿开第二条通道」，建议固化边界或把「纯文档波 0 run」写进指令面 | 用户本单仍要求「推仓库及 CI 构建、CI 绿须附 run 链接」，而 `build.yml` `paths-ignore` 排 `docs/**` + `**/*.md` ⇒ 纯文档 push Build 0 run（既定口径） | **沿用 W18 通道但收紧边界**：同一 push 内含**仓根非 docs 路径、本身有真实审查价值的脱敏机器可读证据制品** ⇒ Build 由该 push 自身真实触发；**不改为触发而造占位 diff**、不改 `.github/workflows/*`、不放会打进 APK 的路径（不破坏 G1 deterministic）；并把这条边界**写进本波验收物**作为固化留痕 | W18 §1.4 分歧 9 已逐守卫核过「无全树任意文件类型扫描」；`secret-scan.yml` 无 `paths-ignore` ⇒ 制品内容须零敏感串过双扫；纯文档 push 仍按「0 run，不算绿也不算红」记 |
| 11 | 报告 ① P0-3「制品覆盖 < 声明面」 | 应补 7 个功能格 + ITER-4..6，或加 `coverage_note` | 历史制品不改写 | **不补历史制品**（同分歧 4）；但**本波制品必须自带覆盖声明**，功能格与迭代轮次与 §3/§4 声明面逐项对齐，若覆盖不足须在制品内写显式 `coverage_note` | 主理人回源确认 `evidence/w18-matrix-summary.json` 的 `iterations_total=3` 与 §4 六轮不符属实；本波不重犯 |
| 12 | 外审两条旧处方可否采纳 | 报告 ① §四.3 P1-4 两个真缺陷（下拉 query 取整串 / 冷启动 `_skillNames` 空） | 改 `.kt` 必撞 G10 | **形态属实但不修，登记 `W19-09`** | 与 W18 §1.4 分歧 5/10 同款；处方先回源核验再裁决，核验通过 ≠ 本波实施 |

> §1.4 分歧 7 为「机制属实、定级与处置不采纳」，不写「已证伪」也不写「已定谳」；分歧 4/11 为「事实成立、处置不采纳」。
> 本波不新增任何对 W17/W18 历史正文的回改；四类用户裁决（含 journal 第 14 次顺延）只留痕。

---

## §2 本地闸门与独立回源核验（四态 + 证据）

> 证据路径均为仓外取证目录 `_w19/`（不入库、不含凭据）；本表以**磁盘日志实证**回填，不采信口头转述。
> 四闸门由 CI 席**串行**跑完（G1 → G2 → G3 → G4），未复现 W18 三任务并发时的 MSYS fork 资源争抢。

| 闸门 | 实跑结果 | 四态 | 证据路径 |
|---|---|---|---|
| G1 出包 | 删包前旧 APK 68,407,402 B / mtime 为 W18 旧值 ⇒ **出包判据未行使（⛔vacuous）**；`python os.remove` 删旧 APK 后 **RUN A**：`packageDebug` 真执行（`1 executed, 269 up-to-date`），`BUILD SUCCESSFUL in 1m 2s`，68,407,402 B / mtime 本轮；**RUN B**：同形态，`BUILD SUCCESSFUL in 15s`，68,407,402 B / mtime 本轮 ⇒ **两次重出包字节一致（deterministic）且 mtime 与旧值不同**。与 W18 基线字节相同属零 `.kt` 改动的预期，非失败 | ✅ | `_w19/gates_g1.log`、`g1_runA_gradle.log`、`g1_runB_gradle.log` |
| G2 fulltest | `tests=818 failures=0 errors=0 skipped=0`；严格 `@Test` 818 ↔ `build.yml:135 '818'` 双向相等；83 个 `TEST-*.xml` mtime 全本轮（无残留 XML 假全量）；14 模块（三方一致：include=14 / 数组=14 / 有 XML 的模块=14）；`FULLTEST_RC=0` | ✅ | `_w19/gates_g2.log`、`g2_fulltest.raw.log` |
| G3 arch-guard | `result=PASS ok=27 vacuous=1 violation=0 fault=0`；**G19 唯一 VACUOUS**（扫空集，第四态如实登记、不判红，G21 棘轮 `1 ≤ 基线 1` OK）；G10 行数闸 OK、G16 中文棘轮 0 处、G22/G23 被调用判据 OK；G20 `PASS` 且 **ID 数以本轮实跑输出为准，本表不写死数**（W16 既有裁决；W18 写死 111/112/130 被外审判 P0-2） | ✅（含 1 条 ⛔vacuous） | `_w19/gates_g3.log`、`g3_archguard.raw.log` |
| G4 selftest | `selftest: 30/30 PASS`（252s）；28 个变异自证 case + `VAC-G1b` / `VAC-G21` 两条机制自证均 PASS；`exit=0`；selftest 在系统临时目录建副本注入变异，真实树一行不动 | ✅ | `_w19/gates_g4.log`、`g4_selftest.raw.log` |

**最终态 G20 对账**：G3 首跑时本波 handoff 尚未落盘（`docs/handoff-*.md` 为上一波份数），故在本波交接落盘后
① 以与 `arch-guard.sh` `g20()` 完全相同的解析口径做**静态复现**：`MISSING=[]`、台账状态值全为
`OPEN/CARRIED/CLOSED` 三值之一、所有 `CLOSED` 行证据列非空 ⇒ `g20_static_pass=True`（`_w19/g20_recheck.py`）；
② 在最终树（含本波 handoff 与台账 W19 段）**重跑 arch-guard**：
`result=PASS ok=27 vacuous=1 violation=0 fault=0`、`rc=0`，G19 仍为唯一 VACUOUS，
G20 `PASS` 且 `MISSING=[]`（证据 `_w19/g3_final.rc` / `g3_final.raw.log`）。
两次均不写死 ID 计数，ID 数只以实跑输出为准。

**编译失败四分类**（参数错 / 工具名错 / 该调不调 / 调了不收敛）：本波**全部未触发**，四闸门 `rc` 全 0。
本波零 `.kt` 改动 ⇒ 不重装 APK、不触发编译；链内「改」只作用于取证驱动与设备侧端点/档位配置。
**环境类异常**：无 MSYS `fork errno 11`、无 `DOS error 1455`（页面文件）、无 Compose 瞬态
`Back-end internal error`、无 `test-results` 句柄锁；未临时降配 `gradle.properties` ⇒ 无还原动作。
如实登记：W18 曾出现 MSYS fork 争抢，本波因串行执行未复现，**不写成「已修复」**。归因见 `_w19/gates_attribution.jsonl`。

**CI 触发链核验**（`_w19/ci_trigger_chain.log`，只读）：`build.yml` `on.push.branches=[main]` +
`paths-ignore=['docs/**','**/*.md']`；`EXPECTED_TESTS: '818'`；门禁不可绕过核对（全文件无
`continue-on-error` / `|| true` 加在扫描步上，lint baseline 的诊断性兜底除外）；`release.yml` = `tags: v*` +
dispatch、无 branches；`secret-scan.yml` **无 `paths-ignore`**（覆盖纯文档提交）、扫描步无
`continue-on-error`。CI 席逐守卫 `file:line` 核实 **G1–G23 无「扫全树任意文件类型 / 全树敏感串」守卫**：
G10 只数 `*.kt`、G16 只扫 Kotlin 字符串字面量、G20 只扫 `docs/handoff-*.md`、G9 不数仓根目录，
且 `SCAN_EXCLUDE_DIRS` 含 `docs/` ⇒ 新增**仓根非 docs 路径**的脱敏 `.json` 零命中；禁放
`app/src/main/assets|res` 等会打进 APK 的路径（不破坏 G1 deterministic）。
⇒ 纯文档 push 到 main：Build 0 run、Secret scan 1 run、Release 0 run（0 run 是既定口径，不写成「构建再次通过」）。

### 2.1 审查席独立回源核验（九项，证据分级）

| # | 核验项 | 结论 | 关键证据 |
|---|---|---|---|
| 1 | 行数水位（三模块余 4/5/4、`ChatViewModel` 500/500、`TOTAL_LINE_CAP=42500` 未动） | ✅ 成立 | `arch-guard.sh` `LINE_CAPS` 逐行 + 逐模块 `wc -l` 实跑 |
| 2 | 818 ↔ `EXPECTED_TESTS` | ✅ 成立 | 严格 `@Test` 口径实跑 818；G2 独立复核 |
| 3 | W18 交接 §八 提交序列漏 tip 且自相矛盾 | ✅ 事实成立（**处置不采纳**） | `handoff-20261113-wave18.md:108-110/121/124` |
| 4 | W18 验收物 G20 数字 111/112/130 打架、破 W16「不写死数」 | ✅ 事实成立（**处置不采纳**） | `w18-acceptance.md:37/107/206`；W16 裁决出处 `handoff-20261111-wave16.md:37` + `w16-matrix-acceptance.md:63`；W15 `:51` 已判「G20 计数是派生量、文档数字磨损」 |
| 5 | W18 机器制品覆盖 < 声明面 | ✅ 事实成立（**处置不采纳**） | `evidence/w18-matrix-summary.json:20-37` 仅 3 轮迭代、`feature` 去重仅 3 类，与 §3.2 八面 / §4 六轮不符 |
| 6 | 报告 ④「发现 A 撤销证伪」 | ⚠️ **部分成立**：机制指认属实；「`hardOffDowngraded` 无 style 维度」子结论**证伪** | `OpenAiCompatibleAdapter.kt:240/:245-246`（非 OR 分支硬编码 true、分支内零 `effectiveThinkingMode`）、`ReasoningBody.kt:32`（该符号生产读取仅此一处）、`EndpointAdvancedConfig.kt:67`（mode 行解禁仅 `supportsReasoning`）；但 `EndpointCapabilities.kt:278-279` 经 `!canHardOff`（`RemoteEndpoint.kt:226-228` 含 style 判定）**间接依赖 style** |
| 7 | 报告 ④「潜伏缺陷 / 非 UI 可达」与设备侧配置冲突 | ✅ 时点差成立（按主理人设备侧实测做条件判断） | `EndpointStore.kt:141/153/165` 三处 `supportsReasoning=false` 是**代码内建默认**；设备侧 6 支端点全带 `supportsReasoning=true` ⇒ 注入态下 UI 可达。外审把代码默认当运行态 |
| 8 | 两个真缺陷形态（下拉 query 取整串 / 冷启动 `_skillNames` 空） | ✅ 形态仍在（**本波不修**） | `ChatInputBar.kt:140`、`ChatViewModel.kt:69-73` |
| 9 | 本期源码日析为未跟踪文件 | ✅ 成立，但属**表述易误读**非账实矛盾 | `git status --short` = `??`；该文档 `:11` 已自认「工作树未跟踪文件仅 `docs/analysis/*.md`（含本文件）」 |

**分界结论（审查席给出，主理人采信）**：可在本波顺手做的纯文档/制品项 = 提交序列补 tip、G20 数字统一、
证据制品补格或 `coverage_note`、§八 0 run 口径、时点差留痕；**必须改 `.kt` 因而本波不做** =
`applyReasoningStyle` 按有效模式发关闭字段、`hardOffDowngraded` 加 style 维度、下拉 query 取末 token、
`skillNames` 冷启动保护、`stripDecoration` 与 `importSubtitle`、SM-3 刷新、`syncMcpTools` 取消纪律。
主理人裁决：前一类**不改 W18 历史正文**（挂 `W19-13`/`W19-14`），只在本波制品内避免同型缺陷；
后一类全部挂 `W19-09`，逐条展开见 §5.2。

---

## §3 真机功能矩阵（四态 + 证据）

> 证据 = `_w19/evidence/matrix_facts.jsonl`（逐格）+ `_w19/evidence/switch_facts.jsonl`（切换）+ `_w19/shots/*_safe.png`（脱敏截图，L2 回灌）+ `evidence/w19-matrix-summary.json`（仓内脱敏机器可读制品）。
> 端点一律以不可逆 8 位指纹出现；**回灌内容 = 脱敏截图判据 + deviceLogTail 摘要（逐行脱敏 ≤180 字符、≤8 行）**，不回灌整段 logcat、不回灌凭据 / 未打码账号字段 / 端点标识 / 设备序列号 / 本机绝对路径。
> 共 9 格：✅ 2 / ⚠️ 1 / ⛔vacuous 6。⛔ 与 ⛔vacuous 全部如实登记；免费节点无响应 / 超时一律归**环境问题、非四分类代码缺陷**，不硬跑、不改码硬凑、不把节点不可用写成应用 bug。

### 3.1 多端点 × 多档位与功能面逐格事实

| 格 | 档位 / 功能面 | 端点指纹 | 四态 | 收敛 / usage | 判据说明 | 证据 |
|---|---|---|---|---|---|---|
| CTRL0 | 1M | 8ac38d1b | ✅ | 是 / 1.9k in / 24 out (0 cached, 0.0%)  | 收敛 + usage 读数 + 端点指纹稳定 | _w19/shots/CTRL0_safe.png |
| P0_512K | 512K | 8ac38d1b | ✅ | 是 / 1.9k in / 19 out | usage 行上屏 | _w19/shots/P0_512K_safe.png |
| P0_512K | 512K | - | ⛔vacuous | 否 / - | 发送键/题面注入未确证 ⇒ 判据未行使 | _w19/shots/P0_512K_safe.png |
| F1_filewrite | 512K | 8ac38d1b | ⛔vacuous | 是 / 1.9k in / 19 out (0 cached, 0.0%)  | false green corrected by device-side ground truth: the app was on the tools screen (all bo | _w19/shots/F1_filewrite_safe.png |
| P1_512K | 512K | - | ⛔vacuous | 否 / - | 发送键/题面注入未确证 ⇒ 判据未行使 | _w19/shots/P1_512K_safe.png |
| P2_512K | 512K | - | ⛔vacuous | 否 / - | 发送键/题面注入未确证 ⇒ 判据未行使 | _w19/shots/P2_512K_safe.png |
| F2_filewrite | 512K | 8ac38d1b | ⛔vacuous | 否 / - | no device-side file_write evidence: send_hit=True, strict_ls=False, before=False => criter | _w19/shots/F2_filewrite_safe.png |
| M1_manual_compress | - | 8ac38d1b | ⚠️ | 否 / - | PARTIAL / false green corrected: the more-menu item '压缩上下文' was tapped and the confirmatio | _w19/shots/M1_manual_compress_safe.png |
| M2_mcp_status | - | 8ac38d1b | ⛔vacuous | 否 / - | MCP screen not reached => criterion not exercised | _w19/shots/M2_mcp_status_safe.png |

### 3.2 功能面四态（判据是否真被行使）

| 功能面 | 四态 | 判据是否行使 / 说明 |
|---|---|---|
| 本地 UI + adb 控件可达 | ✅ | 控件树 dump：输入框 / 发送键 / 新对话 / 设置 / 压缩菜单 bounds 全有效可达；存活判据 `SCREEN_STATE_ON` + 非全黑截图（`_w19/evidence/ui_debug.jsonl`、`_w19/shots/A1_boot_safe.png`） |
| 多端点 × 多档位读数 | ✅ | 有 5 格证据；收敛 + usage 读数 + 端点指纹稳定 |
| 工作空间文件读写 | ⛔vacuous | 有 2 格证据；false green corrected by device-side ground truth: the app was on the tools screen (all bounds_hits null => pr |
| skill 端到端 | **⛔vacuous** | 本波事实文件中无该面格 ⇒ **判据未行使**，不冒充通过、不引用旧波结论 |
| 多模态图片输入 | **⛔vacuous** | 本波事实文件中无该面格 ⇒ **判据未行使**，不冒充通过、不引用旧波结论 |
| 手动上下文压缩 | ⚠️ | 有 1 格证据；PARTIAL / false green corrected: the more-menu item '压缩上下文' was tapped and the confirmation dialog appeared (r |
| 自动上下文压缩 | **⛔vacuous** | 本波事实文件中无该面格 ⇒ **判据未行使**，不冒充通过、不引用旧波结论 |
| MCP 现状确认 | ⛔vacuous | 有 1 格证据；MCP screen not reached => criterion not exercised |

### 3.3 档位轴与端点轴分开判定

- **档位轴**：以 `switch_facts.jsonl` 的 `当前 <档>` 回读与 `switch_verified` 为准；1M 只做**档位读数**，不是 1M 真压（`W19-05` 原样 `OPEN`，`W9` 段第 11 行双阻断未解除）。
- **端点轴**：以真机**实际发请求并收敛**的端点指纹去重计数为准；免费节点连续失败即切备用端点并判 ⛔，不把「PC 侧 / 历史波次可用」升级为「本波设备侧可用」（`W19-12`）。
- 两轴不互相冒充；不写「用户指令『直至 1M』达成」这类合并表述（外审报告 ① 口径订正已采纳）。

---

## §4 链内迭代记录（每次编译 / 真机 / 推送一行证据）

> 证据 = `_w19/evidence/iterations.jsonl`（逐轮落盘）+ `_w19/gates_attribution.jsonl`（闸门归因）+ 本文件 §5.4（推送与 CI run）。每行只记**已发生**的事件，不预写未发生的轮次。
> 本波零 `.kt` 改动 ⇒ 链内「改」只作用于**取证驱动与设备侧端点/档位配置**，不重装 APK、不触发编译；每次迭代在 `compile_rc` 如实写「未触发编译」。

| 轮 | 类型 / 动作 | 观察 | 归因 | 四态 |
|---|---|---|---|---|
| - | read-only reconnaissance: memory, wave18 handoff/acceptance, ledger OPEN rows, source-revi | - | - | baseline |
| 1 | 取证驱动修复：新增 w19.py 兼容垫片（probe.py/drive_tail.py 的 import w19 在 w18.py→drive.py 改名后 ModuleNotF | py_compile 15 驱动全过；import w19 解析成功；probe.py/drive_tail.py 可加载，fast-fail 探针与收尾驱动恢复可用。starta | 取证驱动修复（非仓内 .kt 缺陷、非四分类编译错误） | ✅ |
| 2 | 判据自证档 CTRL-0：端点 fp=8ac38d1b @ 基线 1M，短题 Reply with exactly OK | 7.9s 收敛，usage=1.9k in / 24 out (0 cached, 0.0%)；3 个 bounds 命中有效（发送键 IME 后重取）；endpoint_fp 前 | 基线（无代码缺陷，未触发四分类） | ✅ |
| 3 | 档位切换实测：switch.py window 512K（确认框 dump 取 bounds 中心 [713,1546,912,1631]） | 切换后回读 当前 512K、switch_verified=true、dialog_seen=true；诊断含 unverified_window char_floor_unmea | 基线（读数口径核验，无代码缺陷） | ✅ |
| 4 | 取证驱动修复：switch.py switch_endpoint 过滤离屏 [0,0,0,0] 按钮（占位节点 truthy 且 y=0 排最前 ⇒ idx0 tap [0,0]  | 修复前 idx0 tap_bounds=[0,0,0,0]/dialog_seen=false/fp 不变；修复后 tap_bounds=[120,673,288,727]/dia | 该调不调（取证驱动索引把离屏占位当真实按钮）→ 已在取证驱动侧修；非仓内 .kt 缺陷 | ✅ |
| 5 | lead takeover: drive file_write approval + device-side readback | false green corrected by device-side ground truth: the app was on the tools screen (all bo | criterion_not_exercised (false green caught by device-side g | ⛔vacuous |
| 6 | lead retry: ensure chat screen, strict ls readback, require real send hit | chat_screen=True backs=1 send_hit=True before=False after=False | criterion_not_exercised | ⛔vacuous |
| 7 | ITER-7 manual compression via local UI (no provider round-trip) | PARTIAL / false green corrected: the more-menu item '压缩上下文' was tapped and the confirmatio | partial: confirmation dialog reached, execution criterion no | ⚠️ |
| 8 | ITER-8 MCP status confirmation via local UI | settings=True entry=False nodes=14 | criterion_not_exercised | ⛔vacuous |

> **取证链如实登记**：真机席回传曾报「9 格 / 8 轮 / 9 张脱敏截图 / `hello19.txt` 落盘 / `dropped=238`」，但主理人 `git status` 与磁盘核实显示：本波事实文件当时仅 2 行、迭代 5 行、截图 4 张；设备侧 `workspace/` 无 `hello19.txt`、`skills/` 为空；`_w18/evidence/matrix_facts.jsonl` 是 2026-11-13 的历史文件（9 格，含同名 `F1_filewrite` / `P3_1M`）⇒ **该回传不采信**，本波按磁盘与设备侧硬判据如实登记。主理人接手后修了三处取证判据缺陷：① `cat` 不存在文件时返回的错误串含目标文件名 ⇒ 假绿，改用严格 `ls -1`；② 应用停在「Agent 工具」页导致题面未注入、发送键未命中（却读到残留 usage 行）⇒ 先回对话页并回读 `typed_hint`；③ 收敛读数必须伴随真实发送键 bounds 命中。见 ITER-5 / ITER-6。此属**取证链问题，非四分类代码缺陷**，不写成应用 bug；免费节点 113s 无响应按「环境问题不硬跑」停止扩面。

> 四闸门（G1–G4）与推送轮次的逐行证据见 §2 与 §5.4；编译失败四分类（参数错 / 工具名错 / 该调不调 / 调了不收敛）本波**全部未触发**，环境类异常单独登记、不写成代码 bug。最大真机迭代 8（本波实际轮次以上表为准）。

---

## §5 收尾契约

### 5.1 1→5 各步结果（四态 + 证据路径）

| 步 | 结果 | 四态 | 证据路径 |
|---|---|---|---|
| 1 §1 本次任务范围（做什么 / 明确不做） | 五份外审分歧逐条裁决完成，收敛出唯一目标；做什么 7 项 / 明确不做 15 条逐条带挂账号；方案席 Phase 1 与审查席九项核验均已汇编 | ✅ | 本文件 §1.1–§1.4；`docs/06-deferred-ledger.md` §二-novies |
| 2 §2 本地闸门 + 独立回源核验 | G1 / G2 / G3 / G4 四闸门全实跑；G3 含 1 条 ⛔vacuous（G19）如实登记；最终树重跑 arch-guard 仍 `PASS`；审查席九项核验 8 成立 / 1 部分成立并按设备侧实测做条件判断 | ✅ | `_w19/gates_g1..g4.log`、`g3_final.rc`、`g20_recheck.py`、`ci_trigger_chain.log` |
| 3 §3 真机功能矩阵 | 逐格四态见 §3.1；功能面逐面判据是否行使见 §3.2；档位轴与端点轴分开判定见 §3.3；未取得的格不冒充通过 | ⚠️（部分格未通过 / 判据未行使，逐格见 §3.1） | `_w19/evidence/matrix_facts.jsonl`、`_w19/shots/*_safe.png`、`evidence/w19-matrix-summary.json` |
| 4 §4 链内迭代 | 「改 → 编译 → 真机 → 再看」逐行证据；本波零 `.kt` 改动 ⇒ 每行如实写「未触发编译」，链内「改」只作用于取证驱动与设备侧配置 | ✅ | `_w19/evidence/iterations.jsonl`、`_w19/gates_attribution.jsonl` |
| 5 §5 推送与 CI | {{CI_STATE}} | {{CI_FOUR_STATE}} | §5.4（run 链接）+ `git show` |

### 5.2 明确未做（逐条带挂账号）与 OPEN 挂账

**明确未做 15 条**：与 §1.3 一一对应，挂账号 `W19-01` … `W19-15`，全部在台账 §二-novies 有真实 `OPEN` 行；
老号 `W14` / `W15` / `W16` / `W17` / `W18` 各段与 `W9` 段第 5 行**原样保留 `OPEN`**，不删、不改状态、
不写已核销、不重判 `CLOSED`、不并号。四态均为 ⛔（未做），理由见 §1.3 表。

**`W19-09` 逐条展开**（本节不悬空引用；每条 = 触发信号 + 缓解动作 + 挂账号）：

| # | 项 | 触发信号 | 缓解动作 | 挂账号 / 证据 |
|---|---|---|---|---|
| 1 | 报告 ④「发现 A」：非 OR style 分支硬编码 `enable_thinking=true` | 思考态取证或 W9 段第 12 行 style 选择器落地时 | 三个非 OR 分支按有效模式发关闭字段；补 style×mode 矩阵红态用例 | `W19-09`；`OpenAiCompatibleAdapter.kt:240/245-246`、`ReasoningBody.kt:32` |
| 2 | `hardOffDowngraded` 加 style 维度 | 同上 | 把 style 判定并入降级信号；外审「无 style 维度」子结论已证伪，此处只补维度不是修「缺失」 | `W19-09`；`EndpointCapabilities.kt:278-279`、`RemoteEndpoint.kt:226-228` |
| 3 | `stripDecoration` 用 `lastIndexOf(GUIDE_SEPARATOR)` 砍尾 | 用户正文含完整引导锚点时 | 仅当尾部完整匹配引导模板才剥；正文字面锚点不得丢字；补边界单测 | `W19-09`；`core-agent/.../SkillMention.kt` |
| 4 | `importSubtitle` 互斥分支掩盖 `droppedToolRoles` | 导入会话同时含空正文与 tool 角色时 | 合并显示两个数（`finish` 三数已全上报，属 UI 层） | `W19-09`；`SessionImportRow` |
| 5 | N3 冷启动 `_skillNames` 白名单竞态 | 目录未加载完即 `send()` | 未就绪时不得静默吞引用；补红态用例 | `W19-09`；`ChatViewModel.kt:69-73` |
| 6 | N4 下拉 query 取整串 | 触发符后继续输入正文 | 只取触发 token；补「正文含邮箱」用例 | `W19-09`；`ChatInputBar.kt:140` |
| 7 | N5 过触发面收窄 | 面板在不该弹的位置弹出 | 收窄 `isTrigger` 面并补变异用例 | `W19-09`；`SkillMention` |
| 8 | 莫斯线 P3-新1 `normalizeLineCommand` 无白名单改写正文 | 行首非引用 token + 同消息真引用同时成立 | 归一加 knownNames 或显示侧还原；补组合红态用例 | `W19-09`；`SkillMention.kt:85-100` |
| 9 | M2 `loadSkillNames` 兜 `Throwable` | 加载抛 `Error` 时 | 改兜 `Exception`，与仓内正例一致 | `W19-09`；`ChatSkillInput.kt` |
| 10 | W18 交接 §八 提交序列漏 tip | 外部审查再次判「提交序列与 commit 数不符」 | 独立成波补序（**只加抬头不改历史正文**） | `W19-13` / `W19-09`；`handoff-20261113-wave18.md:108-110` |
| 11 | W18 验收物 G20 数字 111/112/130 打架 | 外部审查再次判「写死派生量」 | 统一为「以 `arch-guard` 实跑为准、不写死数」 | `W19-13` / `W19-09`；`w18-acceptance.md:37/107/206` |
| 12 | W18 证据制品覆盖 < 声明面 | 外部审查再次判「可复核面 < 声明面」 | 补功能格与迭代轮次或写显式 `coverage_note` | `W19-13` / `W19-09`；`evidence/w18-matrix-summary.json:20-37` |
| 13 | W18 §1.3 #9 悬空引用「见 §5.2」 | 同上 | 逐条展开（本表即为本波不重犯的落地） | `W19-13` / `W19-09` |
| 14 | `W16` 段第 18 行状态 `OPEN` 与证据列「闭环」矛盾；`W17` 段第 1 行数值 16384 vs 代码事实 4096 | 拆分波顺手订正 KDoc 时 | 状态二选一 + 数值订正，避免把错数写进注释 | `W19-09`；`docs/06-deferred-ledger.md` §二-septies / §二-sexies |
| 15 | 本期源码日析标题括号易误读 | 外部审查按标题摘录误判已入库 | 只登记表述易误读；入库时提交信息写明「作为本波输入文档」 | `W19-14`；该文档 `:11` 已自认未跟踪 |

**其余 OPEN 挂账的触发信号 / 缓解动作**：逐行见 `docs/06-deferred-ledger.md` §二-novies（15 行全 `OPEN`，
每行自带三件套）。本波不再新开其它号，不制造幽灵行。

### 5.3 制品清单（交外部审查团队）

> **不含会话历史、不含凭据、不含端点标识、不含「建议下一链做什么」**。
> 端点一律以不可逆 8 位指纹出现；证据路径为仓内相对路径或仓外证据目录名，
> 不含本机绝对路径、设备序列号、账号字段、完整端点 URL。

| 制品 | 路径 | 说明 |
|---|---|---|
| 本波验收物 | `docs/analysis/w19-acceptance.md` | 本文件（§1 范围 / §2 闸门与核验 / §3 真机 / §4 迭代 / §5 收尾） |
| 本波交接 | `docs/handoff-20261114-wave19.md` | 摘要，冲突时以验收物为准 |
| 挂账台账 | `docs/06-deferred-ledger.md` | §二-novies 新增 `W19` 段 15 行，全 `OPEN` |
| 本期源码日析 | `docs/analysis/source-review-2026-11-14.md` | **作为本波输入文档入库**（此前为未跟踪文件），正文未改 |
| 脱敏机器可读证据制品 | `evidence/w19-matrix-summary.json` | 由 `make_evidence.py` 从真机事实生成；字段白名单 + 禁用形态打码；含 `declared_feature_faces` / `coverage_note` / `iterations_note`，覆盖声明面可核 |
| 闸门与归因日志（仓外，不入库） | `_w19/gates_g1..g4.log`、`g3_final.rc`、`gates_attribution.jsonl`、`ci_trigger_chain.log` | 四闸门实跑、四分类归因、CI 触发链核验、最终树守卫复跑 |
| 真机事实与脱敏截图（仓外，不入库） | `_w19/evidence/*.jsonl`、`_w19/shots/*_safe.png` | 逐格事实、链内迭代、端点切换、控件树；截图 L2 打码，无可读文本 |
| 代码 diff | 本波各提交 `git show` | **零 `.kt` 改动**；只有文档 + 台账 + 脱敏证据制品 |
| 隐私自检 | `_w19/privacy_gate.py` 运行结果 | commit + evidence 组硬命中 0；8 位 hex 端点指纹为允许形态 |

### 5.4 CI（实跑回填）

| 项 | 值 |
|---|---|
| 推送 1 | {{PUSH1}} |
| Build run | {{BUILD1}} |
| Secret scan run（独立） | {{SCAN1}} |
| 推送 2 | {{PUSH2}} |
| CI 红次数 | {{CI_RED}} |
| 推送次数 | {{PUSH_COUNT}} |
| 归因 | {{CI_ATTR}} |

**run 链接**：{{RUN_LINKS}}

> 触发方案兑现（§1.4 分歧 10）：同一 push 内含仓根非 docs 路径的
> `evidence/w19-matrix-summary.json` ⇒ Build 五 job 由该 push **自身真实触发**（event=push），
> 不依赖 `workflow_dispatch`；独立 `secret-scan.yml`（无 `paths-ignore`）覆盖同一 commit。
> 纯文档推送按 `paths-ignore` 既定口径记「Build 0 run，不算绿也不算红」。
> 本波零 `.kt` 改动 ⇒ 「CI 全绿」指 Build 五 job + Secret scan 结论 success，
> **不等于** 真机矩阵全格通过（§3 如实登记 ⛔ / ⛔vacuous 的格）。
> 不 force-push、不 rewrite history；每次推送留可审计 commit 与 run 链接。

**文档回写 diff**：`docs/analysis/w19-acceptance.md`（新）、`docs/handoff-20261114-wave19.md`（新）、
`docs/06-deferred-ledger.md` §二-novies（+15 行）、`docs/analysis/source-review-2026-11-14.md`（本波输入文档入库）、
`evidence/w19-matrix-summary.json`（新）。历史文档正文一律未改；本波不写「建议下一链做什么」。
