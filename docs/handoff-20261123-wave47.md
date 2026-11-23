# W47 交接 — 门禁默认全扫 / 判据接闸门 / 判序最后一轮 / 白名单写点收窄 / W9-01 拆分 / 真机多模型回归

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `9d05a64b996002b6006a230e4eb3a6ea1525c524`（上一波 tip）；台账权威版 = `docs/06-deferred-ledger.md`；
> 本波验收物 = `docs/analysis/w47-acceptance.md`；派单卡 = `docs/w47-dispatch.md`；判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；
> 「未触发」≠「已验证通过」；推断带 caveat、不写成事实。本交接不写复合挂账号，段位用中文描述。

---

## 一、本波做了什么（一段话）

裁决八份 W46 审查的方向（含互相打架的数字回源勘定），第 0 车把本地门禁默认改全扫（`--ci-semantics` 显式保留 CI 语义）、把 W46 建的两个判据脚本接进既有 CI step（零新 G 编号）、订正台账三处失实声明与 W46 收尾契约（补真 tip 与第 4 条 run 四字段）。第 1 车做判序**最后一轮调整**（`context` 恢复无条件强窗口词、样本 21→25、注入必红 double 实证）、白名单正文约束收窄（ASCII+160 → 固定诊断码白名单正则、短 ASCII 洞必红）、`W9-01` 带行数预算的真拆分（两个压缩常量搬入 `AgentPolicy` 唯一真值源、G10 触顶时精简 KDoc 不调上限）。`EXPECTED_TESTS 881→882`。四闸门全绿后装机做多模型/多上下文直至 1M/多模态/文件读写/skill/MCP/压缩回归，推 CI。

## 二、审查分歧裁决（8 条方向）

| # | 分歧 | 裁决 |
|---|---|---|
| 1 | 门禁默认空过（深度审查 P0-1） | 采 默认全扫 + 显式 `--ci-semantics`；selftest 逐 STATE 注明参数。 |
| 2 | 判序超窗侧强限流词盲区（深度审查 P0-2） | 采 最后一轮词表调整：context 无条件、让位只留 prompt，21→25；W48 起改范式。 |
| 3 | 白名单短 ASCII 洞（深度审查 P1-1） | 采 形态判据收窄为固定诊断码白名单正则，未修版必红 RED。 |
| 4 | 三脚本零闸门接入（深度审查 P1-2） | 采 接进既有 CI step（symbol-check → secret-scan、replay → build），零新 G 编号。 |
| 5 | 收尾契约第 7 次（深度审查 P1-3） | 采 补真 tip + 第 4 条 run 四字段（API 实取），删自指措辞。 |
| 6 | 台账两处失实（深度审查 P1-1 台账项） | 采 三处行内注记订正，历史变量名去反引号防假绿。 |
| 7 | 省钱外审「价格维度」 | 方向采纳、清单重写、本波不开：先还两个 P0，判据冻结下不夹带跨模块新维度。 |
| 8 | W9-01 连续两波让位 | 采 带行数预算真拆分（常量搬 AgentPolicy），不动 ContextBudget 承重文件。 |

**数字回源勘定**：报告间打架的台账三数（采 216/39/166/11）、OpenAiErrorFrame 行数（采 316/400）、ContextBudget 路径（采 core-agent/loop/ 395/400）均以现役树实测为准，不采转述。

**调度事实**：主理人按纪律串行落代码、闸门、真机与文档（429 前科与「改→编译→真机→再看」高度耦合链内循环决定的调度形态，不冒充子席产出）。动手前 `git status` 干净、无并发写入方；变异验证用仓外 `cp` 备份精确还原，未用 git 写命令恢复。

## 三、账本与守恒

- 台账三处老号补 W47 内容订正（状态按原样），零新开挂账行；新增 W47 段声明只写「本波销账集合 = 0 行」指路。
- 守恒式两数只在派单卡抬头一份；交接/验收物/台账不复述。G20 ID 数不变（arch-guard 141 ID）。
- 本波代码真修（门禁/判序/白名单/W9-01 拆分）均在对应老号逐行留痕。

## 四、第 0 车（账实 / 门禁 / 闸门）

- 门禁：`scripts/w46-local-secret-gate.sh` 默认全扫（排除移 `--ci-semantics`）、顶层补 `--local-full`、未知参数判故障红；selftest STATE 1 走 `--ci-semantics`、STATE 2a/2b 走默认，逐 STATE 注明参数。判据先行：注入被 ignore 的 `_w47/*.jsonl`（含 serial）前默认 rc=0 空过，修后默认 RED rc=1、`--ci-semantics` GREEN rc=0、注入 `git add -f` tracked RED、移除回 GREEN。
- 接闸门：`secret-scan.yml` 加 symbol-check step、`build.yml` 加 replay `--w46` step；各注入必红实证（虚构测试类名 RED / 改坏样本 expected RED），零新 G 编号 / 零新 job。
- 台账订正：`W36-01`「默认入口空过」、`W24-02` 分层三分支+末轮描述、`W12-02` 形态判据+已知边界+W47 收窄；历史变量名去反引号；符号自检回正常态 20 类/59 方法/322 `.kt`。
- 收尾契约：W46 handoff §七补真 tip `9d05a64b996002b6006a230e4eb3a6ea1525c524` 与 Secret run `45014733519` 四字段（`head_sha=9d05a64b…` / `status=completed` / `conclusion=success` / `updated_at=2026-11-22T10:55:23Z`，公开 API 实取），统一「实际 4 条 run」，删自指措辞。

## 五、第 1 车（代码面）

| 改动 | 文件 | 判据 |
|---|---|---|
| 判序最后一轮：`context` 无条件强窗口词、让位只留 `prompt` | `core-engine/.../OpenAiErrorFrame.kt` | 4 条扩展边界未修版全 RED → 修后 GREEN；JVM 注入旧逻辑 2 failed；E5e/h/i/j/k/l/m/n 不回退 |
| 样本集 21→25（C11–C14）| 样本 JSON + replay.py + ConfusionTest | 三处共用同一 JSON；legacy 5 RED / --w46 25 全绿 |
| 白名单收窄（固定诊断码白名单正则）| `app/.../DiagnosticLogSink.kt` + 测试 | 未修版 `shortAsciiFreeTextIsNotMirrored` 1 failed RED → 修后 4 用例全绿；正向固定摘要仍落盘 |
| W9-01 带预算拆分 | `AgentPolicy.kt` + `ContextCompressor.kt` + 3 消费方 import | 唯一真值源迁 AgentPolicy（242/400）；G10 曾 461>450，精简 KDoc 后转绿、上限未调；行为等价 |
| 测试数对账 | `build.yml` | `881→882`（app +1），`EXPECTED_TESTS: '882'` |

## 六、四闸门 + 真机

| 闸 | 结果 | 四态 |
|---|---|---|
| G1 编译 | `assembleDebug` rc=0、APK 70.6 MB | ✅ |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 ID 不变） | ✅ |
| G3 selftest | `30/30 PASS（554s）` | ✅ |
| G4 fulltest | `tests=882 failures=0 errors=0 skipped=0`（14 模块） | ✅ |
| 判据先行 | 门禁空过 RED→GREEN；判序旧逻辑 replay 4 RED + JVM 2 failed→GREEN；白名单短 ASCII 1 failed→GREEN | ✅ |
| CI | 见 §七（推送后回填） | — |

设备先唤醒（`mWakefulness=Awake` + 非黑主屏截图）→ 冷启正常。密钥留于真机，未回灌。

- NV SuperVL 120B 1M：`W47ECHO` 逐字回引 + 思考块；1.8k in / 360 out。✅
- NV Lightning 30B：切换确认框 + `W47LIGHTNING30B` 逐字回引 + 思考块；1.8k in / 960 out。✅
- 工作空间： `file_write` 审批 → 允许一次 → 回读 `W47FILEOK`；设备侧 `cat` 一致。✅
- 多模态（320×180 红矩形+蓝圆真图）：逐字回读白底 + 左红矩形 + 右蓝圆 + 不重叠。✅
- skill：`skill_load 5/5`，列 workspace 文件、识别测试数据、询问指向、承诺遵循流程先出草稿再写。✅
- MCP：0 服务器 ⇒ vacuous；外部 https 受 G15b 阻塞。⛔vacuous
- 手动压缩：确认框走完，日志 `manual_compress=noop reason=manual compress: nothing to drop`（可压内容不足，正确行为）。⚠️
- 落盘白名单（本波收窄的真机复核）：本波时间窗 24 条探针行全部固定诊断码形态、零形态外行。✅
- 端点切换守卫：Lightning / SuperVL / 1M 档切换均弹确认框 + 诊断串。✅
- OR Dots3（用户第三次声明可用）：本波进端点编辑页执行测试连接，但会话级 404 未取得可靠证据（toast 未落盘、logcat 无 app HTTP 细节）⇒ 如实 vacuous，沿用历史连续五波 404、未查公告。⛔vacuous
- 1M 档常规请求：切 1M 档后 `W47ONEMODE` 逐字回引，`上下文 1.9K / 995.9K`；非满窗真压。✅
- 自动压缩 `applied` 分支：只观察 `below_trigger`，未行使。⚠️

**回灌级别**：只写脱敏判据结论与 deviceLogTail 探针行；不回灌整段 logcat / 凭据 / 端点完整标识 / 序列号 / SSID / 路径 / 私人图片。题面一律 ASCII 无空格或 `%s` 还原空格（连续 `%s` 吞词坑已规避）。

## 七、CI（公开 API 四字段实取）

- 提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；PAT 仅 URL inline 一次性使用，remote URL 保持干净、无凭据落盘。
- **实际 4 条 run（代码两提交各 2，tip 双绿）**：
  - 主体提交 `4fc96e183461503eca94d03b9a0ca33680f069e0`：Build `45124200338`（`head_sha=4fc96e183461503eca94d03b9a0ca33680f069e0` / `status=completed` / `conclusion=success` / `updated_at=2026-11-23T06:30:07Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45124200338`）；Secret scan `45124200326`（`head_sha=4fc96e183461503eca94d03b9a0ca33680f069e0` / `status=completed` / `conclusion=failure` / `updated_at=2026-11-23T06:27:32Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45124200326`）—— **3b-ext 命中 G3 selftest 证据里未脱敏的用户目录形态，已由下一提交修正**。主体 Build 绿、Secret 红如实记录、不掩盖。
  - 脱敏修正提交 `6e8751f7e200d47007e0c99d032534bfa2d46ace`（本波最终代码 tip）：Build `45125222158`（`head_sha=6e8751f7e200d47007e0c99d032534bfa2d46ace` / `status=completed` / `conclusion=success` / `updated_at=2026-11-23T06:37:30Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45125222158`）；Secret scan `45125222248`（`head_sha=6e8751f7e200d47007e0c99d032534bfa2d46ace` / `status=completed` / `conclusion=success` / `updated_at=2026-11-23T06:36:39Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45125222248`）。**最终代码 tip 双 run 全绿**，是交付判据。
- 本交接所在文档提交为纯文档 tip：Build 0 run（既定 `paths-ignore` 口径），其 Secret run 按 W29 降级规则（补链 + 挂老号、不作 P0 判据）在回填后由公开 API 实取记录；本句不再触发进一步回填。
- 本地四闸门不代替 CI；CI 绿以最终 tip 的 run 四字段为准。

## 八、明确未做（逐条带中文段位，编号见验收物）

压缩触发线与字符闸同尺化（压缩量纲老号，常量已拆、同尺化余项维持让位）｜内核本体再分层（内核再分层老号）｜1M 满窗真压双硬限（1M 真压两条老号）｜MCP 外部 https（MCP 明文三条老号）｜journal 二选一（journal 二选一两条老号）｜思考块余项四件（思考块渲染老号）｜拥堵断流两条边界（拥堵断流老号）｜OR Dots3 本波证据 vacuous + 公告未查｜自动压缩 applied 与 Bolt/Nano 会话轮｜价格维度（省钱外审，方向采纳本波未开）｜凭据轮换（用户侧）｜判据 7 波次盲区与协作用例文本形态（判据冻结）｜历史缺口不补造。

逐条触发信号与缓解动作见验收物「OPEN 挂账」表。

## 九、OPEN 挂账

逐条（触发信号 / 缓解动作 / 挂账号）见 `docs/analysis/w47-acceptance.md` §「OPEN 挂账」表。本波零新开零销账；思考块渲染老号维持 OPEN、两条已知边界维持已关行内注记。

## 十、交给外部审查团队的制品 + 边界声明

**制品**：`docs/analysis/w47-acceptance.md`、本交接、`docs/w47-dispatch.md`（派单卡）、`docs/06-deferred-ledger.md`（三处 W47 注记 + W47 段声明）、`docs/analysis/w46-acceptance.md` 与上一波交接（收尾订正）、`.github/workflows/secret-scan.yml`（symbol-check step）、`.github/workflows/build.yml`（replay step + `EXPECTED_TESTS 882`）、`evidence/raw/w47/`（G1–G4 原始脱敏输出 + 判据先行 RED/GREEN + 8 张脱敏判据截图 + deviceLogTail）、代码主体逐文件 diff、`scripts/w46-*`（门禁默认全扫）。

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / uiautomator 噪声 /「建议下一链做什么」。

**边界**：① 判序最后一轮只改 context/prompt 让位语义、样本 21→25，W48 起改范式不再扩词表；② 白名单收窄是形态白名单非语义判断，新码漏登记只损失落盘不损失 ring，动态 provider message 不进持久化文件；③ 门禁默认全扫命中本地历史 MUST-RED 取证目录属预期，`--ci-semantics` 才是 CI 语义；④ E6 MCP / E10 Dots3 / E12 applied 四态如实 vacuous/⚠️，E10 未取得会话级 404 证据、不写成已复测也不写成可用；⑤ 1M 档为常规请求非满窗真压；⑥ 三待拍板未获用户字面裁决，代裁未发生；⑦ CI 绿以 run 四字段为准，tip 文档 run 按 W29 降级只记录不作 P0。

> 本波止于交接，交由外部团队审查。
