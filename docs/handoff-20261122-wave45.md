# W45 交接 — 判序镜像反噬收口 / 落盘最小化 / 门禁双路 / 真机多模型回归

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `6e4a7f048f0c74245d02c3ce2a869ff1cfec4a54`（上一波 tip）；台账权威版 = `docs/06-deferred-ledger.md`；
> 本波验收物 = `docs/analysis/w45-acceptance.md`；派单卡 = `docs/w45-dispatch.md`；判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；
> 「未触发」≠「已验证通过」；推断带 caveat、不写成事实。本交接不写复合挂账号，段位用中文描述。

---

## 一、本波做了什么（一段话）

裁决五份 W44 审查的方向，按**窗口词强弱分级**修掉 W44 证据强度仲裁引入的镜像反噬（限流文案含 `token`+`exceed` 被判超窗）：强窗口词 `context`/`prompt` 仍无条件优先、弱窗口词 `token` 须长度语义且无限流语境；3 条新必红先在等价复刻 19 样本与 JVM 变异实跑 RED（7 failed），修复后全绿。第 0 车把落盘隐私面收窄为探针行白名单（12 类固定前缀）、3b-ext 补扫改双路（`--ignored`）并四态重出 g0 证据、补 W44 收尾契约（真 tip / 两文档提交 SHA / 第 4 条 run / W29 降级规则原文）、真删 `explicitRateLimit` 冗余项。第 1 车另补 LENGTH 端到端诊断卡判据。最后装机做多模型/多上下文/多模态/文件读写/skill/MCP/压缩/落盘白名单回归，推 CI。

## 二、审查分歧裁决（6 条方向）

| # | 分歧 | 裁决 |
|---|---|---|
| 1 | 判序 P0-1 镜像反噬 | 采 **P0 本波真修**：窗口词强弱分级 + 3 条新必红（未修版 JVM 实跑 RED）。 |
| 2 | 3b-ext 精确场景空过 | 采 **双路补扫**：`--exclude-standard` + `--ignored --exclude-standard`，g0 四态实证。 |
| 3 | 落盘隐私面扩大 | 采 **白名单最小化**：12 类探针前缀，非探针行不落盘，洪水必红。 |
| 4 | 收尾契约第 5 次复发 | 采 **订正入仓**：真 tip / 两 SHA / 第 4 条 run 四字段 / W29 降级规则原文。 |
| 5 | `explicitRateLimit` 冗余项 | 采 **真删**（`A || (B&&A)=A`），订正 W44 失实措辞。 |
| 6 | RATIO 搬迁 / 单指针 / 三待拍板 | 采 **让位 + 不代裁**：维持 `压缩量纲老号` OPEN、`思考块渲染老号` OPEN、`拥堵断流老号` CLOSED 行内；LENGTH 用例本波补。 |

**调度事实**：本波未派并行子席（429 前科按纪律处理），主理人亲自落代码、闸门、真机与文档，不冒充子席产出。动手前 `git status` 干净、无并发写入方；变异验证用仓外 `cp` 备份精确还原，未用 git 写命令。

## 三、账本与守恒

- 台账：五行老号补 W45 内容订正（状态按原样），零新开 `W45-` 行；新增 `### W45 段` 声明只写「本波销账集合 = 0 行」指路。
- 守恒式两数只在派单卡抬头一份；交接/验收物/台账不复述。`G20` 仍是 141 ID。
- 本波代码真修（判序分级、白名单、门禁双路）均已在对应老号逐行留痕。

## 四、第 0 车（账实 / 隐私 / 门禁）

- W44 验收物与交接：抬头补真 tip `6e4a7f0…` 与两文档提交 `327b314aa9…` 完整 SHA；步 5 / §七补第 4 条 run `44936219342` 四字段与「实际 4 条 run」；W29 降级规则原文（补链 + 挂老号、降级≠不记录）写进交接。
- 五行老号：W45 补录分别落在 `拥堵断流老号`（镜像反噬）、`usage 四态探针老号`（落盘最小化 + ×9/×3 实取订正 + 组合判据）、`脱敏门禁老号`（门禁双路）、`压缩量纲老号`（RATIO 让位、未动）、`思考块渲染老号`（维持）；两行 W43 未来断言加「（W44 已修/已销，本句作废）」。pipe 数自检均 7。
- 隐私门禁：3b-ext 在两路 untracked 扫描之外补 `--ignored --exclude-standard` 路（排除构建产物二进制，不排除取证目录与 `*.jsonl`）；g0 四态实测在临时干净 clone 中复刻 CI checkout 语义，含 12 个历史泄漏文件必红清单（`evidence/raw/w45/g0_untracked_scan_v2.txt`）。

## 五、第 1 车（代码面）

| 改动 | 文件 | 判据 |
|---|---|---|
| 窗口词强弱分级（`context`/`prompt` 强、`token` 弱须无限流语境） | `core-engine/.../provider/OpenAiErrorFrame.kt` | 3 条新必红（SSE `token rate limit exceeded` / SSE `too many tokens: rate limit exceeded` / HTTP400 同形态）|
| `mentionsRateLimitContext` 单一真值源 + `explicitRateLimit` 冗余项真删 | 同上 | E5e/h/i/j/k/l/m/n 与 W44 四条不回退 |
| 落盘探针行白名单（12 类固定前缀 + `prefix ... cached=` 兜底） | `app/.../DiagnosticLogSink.kt` | 正向 15 / 反向 8 / 洪水 600 条必红 |
| LENGTH 端到端诊断卡 | `core-agent/.../AgentRunnerTest.kt` | `Finished(LENGTH)` ⇒ `report.kind=Truncated`、正文保留 |
| 测试数对账 | `build.yml` | `871→878`（+3 engine / +3 app / +1 core-agent） |

行数：`LINE_CAPS`/`TOTAL_LINE_CAP` 未调；`OpenAiErrorFrame` 291/400。

## 六、四闸门 + 真机

| 闸 | 结果 | 四态 |
|---|---|---|
| G1 编译 | `assembleDebug` rc=0、APK 68M | ✅ |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 141 ID） | ✅ |
| G3 selftest | `30/30`（290s） | ✅ |
| G4 fulltest | `tests=878 failures=0 errors=0 skipped=0`（14 模块） | ✅ |
| 判据先行 | 判序变异 28 tests/7 failed→GREEN；落盘变异 3 tests/1 failed→GREEN | ✅ |
| CI | 见 §七（推送后回填） | — |

设备先唤醒（`mWakefulness=Awake` + 非黑主屏）→ 装机 Success → 冷启正常。密钥留于真机，未回灌。

- NV SuperVL 1M：测试连接 `1048 ms`；`W45SUPERVL1M` 逐字回声 + 思考块。✅
- 工作空间：`file_write` 审批 → 允许一次 → `wrote 7 chars` → `file_read` → `W45FILE`；设备侧 `cat` 复核一致。✅
- 多模态（320×180 红矩形 + 蓝圆）：回复逐字「Red rectangle ... left side」+「Blue circle ... right side」。✅
- skill：`LoadSkillNotesOrganizer` ⇒ 遵循流程、列 workspace 文件、要求只收集点名笔记。✅
- MCP：0 服务器 ⇒ vacuous。⛔vacuous
- 手动压缩：确认框走完，日志 `manual_compress=noop ... nothing to drop`（会话体积小）。⚠️
- **落盘白名单（本波改动）**：`run-as` 取回日志，本波时间窗全部探针形态、非探针 0 行；历史残留按时间窗分离。✅
- 端点切换守卫：每次「设为当前」弹确认框 + 诊断串（`empty_session` / `unverified_window` 等）。✅
- OR Dots3（用户再次声明可用）：会话请求 HTTP 404（连续第四波设备真相）。⛔
- 自动压缩 `applied` 分支：本会话短、只观察 `below_trigger`，未行使。⚠️

**回灌级别**：只写脱敏判据结论与 `deviceLogTail` 关键字；不回灌整段 logcat / 凭据 / 端点标识 / 序列号 / SSID / 路径 / 私人图片。题面一律英文无空格单词（本机 `input text` 连续 `%s` 会被吞、已规避）。

## 七、推送 + CI

<!-- CI-PENDING: 推送后回填 commit SHA / run 四字段 / 链接 -->

## 八、明确未做（逐条带中文段位，编号见验收物）

RATIO 搬 policy / 单指针 / 同尺化（压缩量纲老号）｜内核本体再分层（内核再分层老号）｜1M 真压双硬限（1M 真压两条老号）｜MCP 外部 https（MCP 明文三条老号）｜journal 二选一（journal 二选一两条老号）｜思考块余项四件（思考块渲染老号）｜拥堵断流两条边界（拥堵断流老号）｜OR Dots3 404 供给方公告核查｜自动压缩 applied 与 Bolt 会话轮｜凭据轮换（用户侧）｜判据 7 波次盲区与协作用例文本形态（判据冻结）｜历史缺口不补造。

逐条触发信号与缓解动作见验收物「OPEN 挂账」表。

## 九、OPEN 挂账

逐条（触发信号 / 缓解动作 / 挂账号）见 `docs/analysis/w45-acceptance.md` §「OPEN 挂账」表。本波零新开零销账；`思考块渲染老号` 维持 OPEN，两条已知边界维持已关行内注记。

## 十、交给外部审查团队的制品 + 边界声明

**制品**：`docs/analysis/w45-acceptance.md`、本交接、`docs/w45-dispatch.md`（派单卡）、`docs/06-deferred-ledger.md`（五行注记 + W45 段声明 + W44 未来断言作废）、`docs/analysis/w44-acceptance.md` 与上一波交接（收尾订正）、`.github/workflows/secret-scan.yml`（双路）、`.github/workflows/build.yml`（测试数）、`evidence/raw/w45/`（脱敏原始输出 + 六张判据截图）、代码主体逐文件 diff。

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / uiautomator 噪声 /「建议下一链做什么」。

**边界**：① 判序以 19 样本等价复刻 + JVM 变异双证据为准，强窗口词文案仍无条件判超窗；② 弱 `token` 规则「长度语义且无限流语境」是比外审处方更保守的一档（`rate limit: maximum 100 tokens per minute` 落回限流），19 样本全绿为依据；③ 白名单只认固定前缀，新增探针行须同步，漏了只损失落盘不损失 ring；④ 四态探针按组合判据成立，两态端点未上报属行为边界；⑤ OR Dots3 404 为设备真相连续第四波，未查公告；⑥ RATIO 未做是主动让位（依赖与行数上限），三待拍板维持现状不代裁；⑦ CI 绿以 run 四字段为准，tip 文档 run 降级补链、不作 P0。

> 本波止于交接，交由外部团队审查。
