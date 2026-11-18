# W30 验收物 — 设备授权恢复 + 五报告分歧裁决 + 多模型×多功能面真机矩阵 + W29 三 P0 第 0 车收口（2026-11-19）

> **基线**：`f0b12966b`（W29 回填，`main`）。**零 `.kt` 起点**。
> **上游**：用户 2026-11-19 指令（读记忆/上一波 handoff/挂账行、五审查报告分歧裁决不改方向、多模型×多上下文×多功能面真机矩阵〔多模态/工作空间/skill/MCP〕+ 手动/自动压缩、先本地 UI + adb 调试 → 真机 → 推仓库 + CI）。
> **判定四态**：✅ / ⚠️ / ⛔ / ⛔vacuous。**「未触发」≠「已验证通过」**；读数 ≠ 生效窗口 ≠ 真压；推断带 caveat。
> **密钥纪律**：端点凭据只在设备侧；本文件不含完整 API key / 端点完整标识（一律 8 位指纹）；密钥文件名 `nvidia.txt` 仅出现在 grep 命令行中（不落文档正文）。
> **本文件严格零「W 加数字加减号加数字」复合编号字面量**（守卫全文扫描此类连续子串，台账侧才认行首）。**不写「建议下一链做什么」。**

---

## 一、做什么 / 明确不做（用户指令第 1 节）

### §1.1 做什么

1. 读记忆与上一波 handoff（W29）；读台账 OPEN 行；确认 W30 号段 0 占用。
2. **五审查报告分歧裁决**：harness-w29-review / W29-深度审查与挂账分析 / W29 深度审查·挂账分析·W30 路线建议 / W30 段挂账草案 / W29 深度审查与 W30 排期裁决，逐条裁决**不改方案大体方向**。
3. **第 0 车零 `.kt` 收口**（外审三 P0 全部成立，经实跑核验）：W29 制品去 W28 化、W29 checklist 必红侧闭环、W28-01/W28-03 台账行内注记、W29 验收物 §5.6 head_sha 笔误订正。
4. **真机矩阵**（设备 RSA 授权已恢复，与 W29 unauthorized 形态对比）：E01/E02/E03 端点轴、F2 workspace、F4 skill、F6 自动压缩、F7 MCP、T0 档位。
5. 四闸门（G1 编译 / G2 守卫 / G3 自测 / G4 全测）+ 推送 2 次（主体 + 纯文档回填）+ CI run 回填。
6. 收尾契约：明确未做 / OPEN 挂账 / 文档回写 diff / 交外部审查制品清单。

### §1.2 明确不做（逐条带挂账号）

| 项 | 理由 | 挂账号 |
|---|---|---|
| 转 `.kt` 代码波（拆分三方 / B1 压缩 / W24-01 思考框 / W24-02 断词） | 用户指令「不改方案大体方向」 | W9-01 / W12-02 / W17-01 / W23-04 / W24-01 / W24-02 / W16-03 |
| journal 第 19 次顺延代数多值 | 只留痕不代裁二选一 | W24-03 → W21-03 → W20-01 → W9-05 |
| 六项用户终局裁决（≥3 端点 / 占位符 / 并行键 / 借鉴输入面 / tip run 闭环 / git 历史清理） | 用户裁决域不代做 | W20-05 / W20-06 / W20-07 / W20-08 / W20-16 |
| 守卫扫描面扩展 / 六行漏号 | 扩面前须先批量补 `W13-01`+`W11-06..10` | W20-03 / W20-17 / W15-01 / W16-01 |
| MCP 明文流量阻断放开 | 平台策略 | W5-03 → W20-06 |
| 1M 真压 | 时间预算 + 字符维未越阈值 | W20-05 → W9-11 |
| F1 多模态 / F5 手动压缩 / T0 四档 sweep 完整跑 | 时间预算（F1 需真图 + mistral 端点 + SAF 选图；T0 档位切换慢；F5 需安全截断点） | 本波追加，见 §5.2 |
| 取证设施入仓 / 测试数上限调整 / 台账瘦身 | 用户裁决域 | W20-14 / W20-09 |
| 安全波（secret-scan 双跑消重 / 安装脚本 pin / allowlist 变异用例） | 第 2 车事项 | W20-04 |

### §1.3 五审查报告分歧裁决（全部 `改方向 = 否`）

| # | 来源报告 | 主张 | 裁决 | 依据 |
|---|---|---|---|---|
| 1 | W29-深度审查与挂账分析 P0-1 | 制品 `verified_by=device_execution` 是自证矛盾 | **采纳** | W30 第 0 车已改 `inherited_w28_baseline` |
| 2 | 同上 P0-2 | 必红变异块 `${WS}` heredoc 不展开 | **采纳** | W30 实测 FileNotFoundError 复现；改 Python 自算路径 |
| 3 | 同上 P0-3 | 四报告仓内无独立文件 | **部分采纳** | 本波把报告名列入 §1.3；不把报告正文入仓（脱敏成本高） |
| 4 | 同上 P1 | 开代码债偿还波（A1 runCatching / A2 stripDecoration / A3 ChatViewModel 500/500 / B1 feature-chat 3996/4000） | **否，顺延** | 用户指令不改方向；行数闸门贴顶拆分是独立波 |
| 5 | W29 路线建议 §一 | 跨波证据污染（W29 制品 = W28 整格复制 13 字段） | **采纳** | W30 实测 11 格 13 字段全相同；已去 W28 化 |
| 6 | W29 排期裁决 §3 P0-1 | 同 #5 | **采纳** | 同上 |
| 7 | W29 排期裁决 §3 P0-3 | W28-01 原行零 diff 第 7 次 | **采纳** | W30 已加行内注记 |
| 8 | W30 段挂账草案 3 行 | 落 W30-01/02/03 | **采纳（号段 0 占用已实测）** | 本波落笔 |
| 9 | W30 段挂账草案 §5.5 | W29 段三行 CLOSED 条件改仓内判据 | **采纳** | 本波改 W28-03（W29 段原行未含「须真机取证」字样，处方对象是 W28-03） |
| 10 | harness-w29-review §一 | 设备 RSA 授权解锁全部格 | **已发生** | W30 adb 复查 `13309cc8 device`（非 unauthorized） |
| 11 | harness-w29-review §5.2 | 台账 OPEN 189 膨胀、冻结元债开新号 | **否，留痕** | 用户裁决域；本波按处方仍落 3 行新号 |
| 12 | harness-w29-review §4 P1 | W28-01「三处同步」变异零命中 | **采纳** | W30 判据 4 必红实跑真响 |

---

## 二、第 0 车零 `.kt` 收口

| 动作 | 文件 | 结果 |
|---|---|---|
| W29 制品去 W28 化 | `evidence/w29-matrix-summary.json` | `state_counts={'VACUOUS':11}`、`iterations_total=6`、正控 `inherited_w28_baseline`、`w28_baseline_facts` 独立字段、`w30_p0_repair` 注记 |
| W29 checklist 必红侧闭环 | `docs/notes/w29-closeout-checklist.md` | 判据 2 三态分支 + Python 自算路径、判据 3b 注入样本、判据 4 可跑必红块、新增判据 5、实跑输出摘录 |
| W28-01 行内注记 | `docs/06-deferred-ledger.md` | 加「出口判据由 W29 checklist 判据 4 承载」注记（原结论不动） |
| W28-03 删错配条件 | `docs/06-deferred-ledger.md` | 删「须真机取证」改「仓内判据实跑输出入账 + 制品与真值源一致」 |
| W29 验收物 §5.6 笔误 | `docs/analysis/w29-acceptance.md:252` | `1c6e404` → `f0b12966b`（推送 2 head_sha 订正） |
| W30 段落笔 | `docs/06-deferred-ledger.md` | 3 行 `OPEN`（W30-01/02/03，号段 0 占用已实测） |
| W30 制品新建 | `evidence/w30-matrix-summary.json` | 8 cells / 9 iterations / 5 必红全真响；`cells_provenance` 声明全部来自 `_w30/evidence/*.json` 本波真机，无跨波复制 |

### §2.1 W29 checklist 必红侧 W30 实跑输出

| 判据 | 必红样本 | 实跑输出 |
|---|---|---|
| 2 | `mutation_cases[0].verified=False` → 副本 | `RED mutation invariant broken` |
| 3b | `printf 'nvi%sdia.txt'` 副本 | `RED credential filename hit` |
| 4 | w27-acceptance 副本写回「已写进压缩老号作红态入口」 | `RED affirmative wording remains` |
| 5 | 删 `iterations[2]` 副本 | `RED iteration/state invariant broken (5/5/6)` |
| 2 正常态 | — | `OK(vacuous)` |
| 5 正常态 | — | `OK (6/6/6)` |

四条必红真响 + 两条正常态 OK，摘进 checklist §本波必红实跑输出摘录（消除 W29「声称实跑未实跑」虚报）。

---

## 三、真机矩阵（8 格 · PASS3 / PARTIAL2 / VACUOUS3）

> 每格四态 + 证据路径 + 未行使原因。设备授权已恢复（`adb devices` → `13309cc8 device`），**非 W29 unauthorized 形态**。

| cell | 面 | 端点指纹 | 档位 | 四态 | 关键判据 | 证据 |
|---|---|---|---|---|---|---|
| E01 | 端点轴·正控 | 01e476bd (NV-VL) | 1M | ✅ | `W30ALPHA7` 逐字、6.7s、4.5k in / 28 out、上下文 4.5K / 995.9K | `_w30/evidence/e01b.json` |
| E02 | 端点轴·用户点名 | b995dc45 (OR-DOTS) | 512K | ⚠️ | 62.7s 超时 `done=false`；deviceLog 抓到 `W30BRAVO9`（部分到达） | `_w30/evidence/e02.json` |
| E03 | 端点轴·用户点名 | b0113b2c (OR-NORTH) | 256K | ⚠️ | 62.7s 超时；UI「网络抖动，正在重试」 | `_w30/evidence/e03.json` |
| F2 | workspace | 01e476bd (NV-VL) | 251.9K | ✅ | 审批 1 次 → `w30_write.txt` 落盘 → verbatim `W30WS777` + 8 字节 hex | `_w30/evidence/f2_w30.json` |
| F4 | skill | 01e476bd (NV-VL) | 1M | ✅ | `@w30-probe` 菜单点选 → `skill_load` 工具卡 → `SENTINEL-W30-M4P8Q` 逐字、11.2s、92.5% cache hit | `_w30/evidence/f4_skill.json` |
| F6 | 自动压缩 | NA | NA | ⛔vacuous | 本波短对话 committedChars 未越 50227 阈值；deviceLog 全 `auto_compact=noop reason=below_trigger` | `_w30/evidence/e01b.json#deviceLogTail_compact` |
| F7 | MCP | NA | NA | ⛔vacuous | MCP 页导航可达、空态「还没有 MCP 服务器」、无外部端点 | `_w30/evidence/f7_mcp.json` |
| T0 | 档位 sweep | NA | NA | ⛔vacuous | 本波未单独跑四档 sweep（E01 1M badge `上下文 4.5K / 995.9K` 间接证据，非档位 sweep 通过） | `_w30/evidence/e01b.json#labels_tail` |

### §3.1 端点池 inputModalities（沿用上波设备真相，本波未复测）

| 端点指纹 | 名称 | inputModalities | 备注 |
|---|---|---|---|
| 01e476bd | NV-VL | `[TEXT]` | 本波主力 |
| b995dc45 | OR-DOTS | `[TEXT]` | **用户声明「工具+视觉双通」继续被证伪视觉面** |
| d5022055 | NV-MISTRAL | `[TEXT,IMAGE]` | 唯一含 IMAGE |
| a9ae3f9e | NV-STEP | `[TEXT]` | 未行使 |
| b0113b2c | OR-NORTH | `[TEXT]` | 用户点名 |
| cfd5fbda | OR-GEMMA | `[TEXT]` | 未行使 |

### §3.2 链内循环（9 轮 · 改 → 编译 → 真机 → 再看）

| 轮 | action | observation | 四态 | 归因 |
|---|---|---|---|---|
| iter1 | E01 首跑 | labels/dump 全空、截图 0 字节 | ⛔ | app 不在前台（`mCurrentFocus=Launcher`） |
| iter2 | E01 重跑 | `W30ALPHA7` 逐字 | ✅ | app 冷启后链路通 |
| iter3 | F4 首跑 | `@w30-probe` 不在 @ 菜单（只列 4 项） | ⛔ | 菜单 8 项上限 + `ChatViewModel` init 一次性加载 |
| iter4 | F4 重跑 | sentinel 逐字 + skill_load 卡 + 92.5% cache | ✅ | 移出 w21-probe + 重启进程后进菜单 |
| iter5 | E02 | 62.7s 超时、deviceLog 抓到 sentinel | ⚠️ | 免费节点慢（W26/W27 已知） |
| iter6 | E03 | 62.7s 超时、UI「网络抖动」 | ⚠️ | 免费节点抖动 |
| iter7 | F2 首跑（mistral） | `ls_diff=[]`、文件未落盘 | ⛔ | mistral 对 file_write 题面安全拒答/超时（W28 同型） |
| iter8 | F2 重跑（NV-VL） | verbatim + hex + 审批 1 次 | ✅ | 切 NV-VL 后链内修复 |
| iter9 | F7 MCP | 导航可达、空态 | ⛔vacuous | 无外部端点 |

### §3.3 F4 skill 注册链诊断（可复用）

1. **`FileSkillSource`**（`core-skills/.../SkillSources.kt:26`）实时扫 `root/<skill>/SKILL.md` **一层不递归**；`.off` 后缀目录里的 SKILL.md 仍被读（解释 w21-probe.off 残留）。
2. **`SkillCatalogImpl`**（`SkillCatalogImpl.kt:44`）有 `cache`，`refresh()` 清缓存——但刷新只清 catalog 缓存。
3. **`ChatViewModel.init`**（`ChatViewModel.kt:72`）一次性加载 `_skillNames`（`ChatSkillInput.loadSkillNames(container)`）——刷新后**不重建**，需重启进程才能更新 @ 菜单。
4. **@ 菜单 8 项上限**（W24 已发现，本波实测：4 初始 + 4 滚动）；第 9 项被截断。
5. **`drv.text()` 遇 @ 菜单吞后续字符**（本波实测：`input text "@w30-probe follow..."` 只落地 `@`）——配方：先打 `@` → 菜单点选 → 补题面。

---

## 四、本地闸门

> 四闸门实跑值与证据路径见 §5（推送前终态树复跑）。

| 闸门 | 期望 | 实跑 |
|---|---|---|
| G1 编译 | rc=0 | ✅ rc=0，270 actionable tasks: 270 up-to-date（本波零 `.kt`，产物与基线一致） |
| G2 arch-guard | PASS ok=27 vacuous=1 violation=0 | ✅ PASS ok=27 vacuous=1 violation=0 fault=0；G20 OK（115 handoff ID 全在台账、CLOSED 行证据非空）；首跑 G20 VIOLATION（W30-02 行 `$(pwd -W \|\| pwd)` 字面 `\|\|` 破列 + W28-01 行末尾管道符丢失）→ 改中文「或」+ 补末尾管道符后 PASS |
| G3 selftest | 30/30 | ✅ 30/30 PASS（234s；后台跑，前台 120s 被 SIGTERM 截断为已知坑） |
| G4 fulltest | 818 tests / 0 failures / 0 errors | ✅ tests=818 failures=0 errors=0 skipped=0（全部用例绿）；14 模块全覆盖；首跑 rc=1 为 `JAVA_HOME` 未设置（fulltest.sh 不注入 env，与 localbuild.sh 不同）→ `eval "$(bash scripts/localbuild.sh --env \| grep '^export ')"` 后重跑 rc=0 |

**严格 `@Test` 行首计数**：818（不变，本波零 `.kt`）。

---

## 五、收尾契约

### §5.1 四闸门实跑

待回填（推送前补）。

### §5.2 明确未做（逐条带挂账号）

见 §1.2 表格。**本波追加**：F1 多模态（需真图 + NV-MISTRAL 端点 + SAF 选图，时间预算内未行使）、F5 手动压缩（需 16 轮长对话配方，未行使）、T0 档位完整 sweep（档位切换慢，未行使）、E02/E03 完整真机往返（免费节点慢/抖动，仅部分到达）、F6 自动压缩真压（字符维未越阈值，below_trigger 是正确行为）。

### §5.3 OPEN 挂账（唯一真值源 = `docs/06-deferred-ledger.md`）

| ID 段 | 状态 | 本波动 |
|---|---|---|
| W30 段三行（制品去 W28 化 / checklist 必红侧 / W28-01 零 diff） | `OPEN` | 本波第 0 车缓解动作已做、判据必红实跑真响已摘进仓内；销账留待外审复核 |
| W29 段三行 | `OPEN`（原样） | 不改状态、不重出；W30 第 0 车缓解动作对应本段三行 |
| 更早各段 | `OPEN`（原样保留） | 不删、不改状态、不重判、不并号 |

### §5.4 文档回写 diff

待推送后回填 commit 与 CI run 链接。

### §5.5 交外部审查制品清单（不含会话历史 / 凭据 / 端点标识 / 下一链建议）

- `docs/analysis/w30-acceptance.md`（本文件）
- `docs/handoff-20261118-wave30.md`（交接）
- `docs/06-deferred-ledger.md`（台账 W30 段 3 行 + W28-01/W28-03 行内注记）
- `docs/notes/w29-closeout-checklist.md`（W30 第 0 车修订版：判据 1-5 + 必红侧全闭环 + 实跑输出摘录）
- `evidence/w30-matrix-summary.json`（本波制品：8 cells / 9 iterations / 5 必红）
- `evidence/w29-matrix-summary.json`（W29 制品去 W28 化后）

### §5.6 推送与 CI run 链接

| 项 | 值 |
|---|---|
| 推送 1 commit | `193f3a7`（7 files：本波验收物 / 交接 / W30 制品 / W29 制品去 W28 化 / W29 checklist 修订 / 台账 W30 段 3 行 + W28-01/W28-03 行内注记 / W29 验收物 §5.6 笔误订正）；两份历史源码日析全程 `??` 未入库；dry-run RC=0 → 实推 RC=0；PAT inline URL 一次性使用、`git remote get-url origin` 复核干净 URL（PAT 未落盘 remote）；noreply 身份；不 force-push、不 rewrite history |
| Build run（推送 1） | run `44352338394` **completed success**（event=push，由仓根非 docs 制品 `evidence/w30-matrix-summary.json` + `evidence/w29-matrix-summary.json` 自身真实触发；head_sha `193f3a7`；公开 API 四字段复核 status/conclusion/event/head_sha = completed/success/push/193f3a7） |
| Secret scan run（推送 1，独立） | run `44352338309` **completed success**（无 `paths-ignore`，覆盖同一 commit `193f3a7`） |
| 推送 2 | 本提交（纯文档回填 §5.6 真实 commit 与 CI run 链接）：Build **0 run**（`paths-ignore` 既定口径，判据未行使，不算绿也不算红）；独立 Secret scan 覆盖同一 commit（本推送）；本波不再第三次推送以避免自指回填循环 |
| 推送次数 | **2**（推送 1 含仓根非 docs 制品触发 Build 五 job 真跑 + 独立 Secret scan；推送 2 纯文档回填链接） |
| CI 红次数 | **0** |
| 归因 | 四闸门全绿（编译/守卫/自测/全测）；真机矩阵 8 格（PASS 3 / PARTIAL 2 / VACUOUS 3，设备授权已恢复）；G2 首跑 G20 VIOLATION 为 W30-02 行 `\|\|` 破列 + W28-01 行末尾管道符丢失，改中文「或」+ 补管道符后 PASS；G4 首跑 rc=1 为 `JAVA_HOME` 未设置（fulltest.sh 不注入 env）→ eval 环境后 818/0/0 全绿；推送 1 含仓根非 docs 制品 ⇒ Build 由该 push 自身真实触发；纯文档推送 ⇒ Build 0 run 属既定口径，不写「构建再次通过」 |

**run 链接**（公开 API 逐项读 status / conclusion / event / head_sha，零凭据）：

- 推送 1 Build：https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44352338394
- 推送 1 独立 Secret scan：https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44352338309

> 本波零 `.kt` 改动 ⇒ 「CI 全绿」指 Build 五 job + 独立 Secret scan 结论 success，**不等于**真机矩阵通过（本波 F6/F7/T0 ⛔vacuous、E02/E03 ⚠️），也不等于两条用户可见缺陷已复现。

---

> 本波止于交接。**不写「建议下一链做什么」。**
