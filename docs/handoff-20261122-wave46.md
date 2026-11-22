# W46 交接 — 判序结构性收口 / 白名单正文约束 / 门禁本地闸门 / 真机多模型回归

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `e32a7043b67742a11d54045dd6f320c39f4f3cfb`（上一波 tip）；台账权威版 = `docs/06-deferred-ledger.md`；
> 本波验收物 = `docs/analysis/w46-acceptance.md`；派单卡 = `docs/w46-dispatch.md`；判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；
> 「未触发」≠「已验证通过」；推断带 caveat、不写成事实。本交接不写复合挂账号，段位用中文描述。

---

## 一、本波做了什么（一段话）

裁决七份 W45 审查的方向，第 0 车把权威真值源的 4 个虚构符号逐一订正为真实制品并给台账加机械指路自检（注入虚构符号 RED → 还原 GREEN）、重出 G1–G4 原始脱敏输出、补 tip 两完整 SHA 与第 4 条 run 四字段统一「实际 4 条 run」、补本地门禁脚本三路四态实证。第 1 车做判序**结构性收口**（停止逐波打地鼠）：21 条双向混淆样本共用 JSON + JVM 参数化 + 可跑复刻脚本，legacy 实跑 5 条 RED、W46 全绿、JVM 注入 W45 旧逻辑 2 failed；白名单加正文形态约束（动态 provider message 不落盘，变异 RED）；`EXPECTED_TESTS 878→881`。四闸门全绿后装机做多模型/多上下文/多模态/文件读写/skill/MCP/压缩/落盘白名单回归，推 CI。

## 二、审查分歧裁决（6 条方向）

| # | 分歧 | 裁决 |
|---|---|---|
| 1 | 台账 4 虚构符号 P0-1 | 采 **真符号订正 + 机械自检**：虚构名去反引号保留历史引文，新增仓内可跑 `scripts/w46-ledger-symbol-check.sh`。 |
| 2 | 证据链退摘要卡 + tip run 漏记 | 采 **原始脱敏输出重出 + 补链**：G1–G4 原始输出（363/35/39/396 行），tip SHA + run `44999170747` 四字段实取。 |
| 3 | 判序第五轮打地鼠 P1-1 | 采 **结构性收口**：分层启发式 + 21 条双向混淆样本共用 JSON，一次做完，本波起停止逐波加 3 条点状必红。 |
| 4 | 3b-ext 第三路 CI vacuous P1-2 | 采 **本地人工闸门入口**：三路脚本 + 四态实证，台账注明「CI 两路 + 本地人工闸门」组合判据。 |
| 5 | 白名单正文自由文本洞 P1-3 | 采 **正文形态约束**：`run failed:` / `reason=` 类只认 ASCII 诊断码 + 长度上限；反向必红变异 RED。 |
| 6 | W9-01 让位 / 三待拍板 | 采 **评估后诚实让位 + 授权内裁决**：W9-01 跨三模块重构维持让位不写成已搬；W24-01 维持 OPEN 不代裁销账、W24-02 维持 CLOSED 行内边界、矩阵按用户指令全面推进。 |

**调度事实**：主理人亲自串行落代码、闸门、真机与文档（429 前科按纪律处理），不冒充子席产出。动手前 `git status` 干净、无并发写入方；变异验证用仓外 `cp` 备份精确还原，未用 git 写命令。

## 三、账本与守恒

- 台账五行老号补 W46 内容订正（状态按原样），零新开挂账行；新增 W46 段声明只写「本波销账集合 = 0 行」指路。
- 守恒式两数只在派单卡抬头一份；交接/验收物/台账不复述。G20 ID 数不变。
- 本波代码真修（判序分层、白名单正文、门禁脚本）均在对应老号逐行留痕。

## 四、第 0 车（账实 / 证据 / 门禁）

- 台账指路：4 个虚构符号订正为真实符号（所属类错记一并订正），虚构名去反引号保留为历史引文；弱窗口词规则改合取式；`scripts/w46-ledger-symbol-check.sh` 注入虚构测试类名 ⇒ RED、还原 ⇒ GREEN。
- 收尾契约：W45 验收物/交接补 tip `e32a7043b67742a11d54045dd6f320c39f4f3cfb` / `bbdd45e3046b065b5e589299145a5ed6d3c4a54d` 完整 SHA，第 4 条 run `44999170747`（`head_sha=e32a7043…` / `completed` / `success` / `updated_at=2026-11-22T07:39:00Z`，公开 API 实取）四字段；「实际 4 条 run」统一口径。
- 证据重出：G1 363 行 / G2 35 行 / G3 39 行 / G4 396 行（14 模块 + TEST-XML mtime）；RED 证据含 `AssertionError` 行号与 gradle 堆栈，非手写卡；制品清单写死为「原始脱敏输出」。
- 门禁：`scripts/w46-local-secret-gate.sh` 三路（tracked `git grep` + `--exclude-standard` + `--ignored --exclude-standard`）四态实证（正常 0 / ignored 命中 / add-f tracked 命中 / 移除 0）；`secret-scan.yml` 路数注释订正（第二路 `--exclude-standard` / 第三路 `--ignored`）。

## 五、第 1 车（代码面）

| 改动 | 文件 | 判据 |
|---|---|---|
| 判序分层启发式（强/弱限流语境、强/弱长度语义）+ 限流词表扩 4 词 | `core-engine/.../OpenAiErrorFrame.kt` | 21 样本 legacy 5 RED、W46 全绿；JVM 注入 W45 旧逻辑 2 failed |
| 双向混淆样本集（限流 8 / 超窗 10 / 协议 3）| `scripts/fixtures/w46-error-classification-samples.json` + `scripts/w46-classification-replay.py` + `OpenAiErrorFrameConfusionTest.kt` | 三处共用同一 JSON；E5e/h/i/j/k/l/m/n 与 W44/W45 七条不回退 |
| 白名单正文形态约束（ASCII + 160 上限）| `app/.../DiagnosticLogSink.kt` + 测试 | 动态 provider message 不落盘；变异 `1 failed`（AssertionError :76）、还原 GREEN |
| 测试数对账 | `build.yml` | `878→881`（+2 engine / +1 app） |

行数：`LINE_CAPS`/`TOTAL_LINE_CAP` 未调。W9-01 RATIO 搬迁经逐文件复核后维持让位（跨三模块重构 + `ContextBudget` 395/400 贴近 G10），不写成已搬。

## 六、四闸门 + 真机

| 闸 | 结果 | 四态 |
|---|---|---|
| G1 编译 | `assembleDebug` rc=0、APK 70.6 MB | ✅ |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 ID 不变） | ✅ |
| G3 selftest | `30/30`（289s） | ✅ |
| G4 fulltest | `tests=881 failures=0 errors=0 skipped=0`（14 模块） | ✅ |
| 判据先行 | 判序 JVM 变异 2/2 failed → GREEN；落盘变异 1 failed → GREEN；台账注入 RED → GREEN | ✅ |
| CI | 见 §七（推送后回填） | — |

设备先唤醒（`mWakefulness=Awake` + 非黑主屏截图）→ 装机 Success → 冷启正常。密钥留于真机，未回灌。

- NV SuperVL 120B 1M：`W46SUPERVL1M` 逐字回声 + 思考块（1.8k in / 115 out）。✅
- NV Lightning 30B：`W46LIGHTNING30B` 逐字回声（8.4k in / 44 out）；端点切换确认框 + 诊断串。✅
- 工作空间：`file_write` 审批 → 允许一次 → `file_read` → `W46OKThenReadItBack`；设备侧 `cat` 复核一致。✅
- 多模态（320×180 红矩形+蓝圆真图）：模型逐字回读蓝圆 `#0000FF` 右侧居中 + 红矩形 + 无渐变白底平涂。✅
- skill：遵循 notes-organizer 流程、列 workspace 文件、识别未更新笔记、询问是否写清单；4336 cached / 54.8%。✅
- MCP：0 服务器 ⇒ vacuous；外部 https 受既有阻塞项限制。⛔vacuous
- 手动压缩：确认框走完，日志 `manual_compress=noop reason=manual compress: nothing to drop`（会话可压内容不足，正确行为）。⚠️
- **落盘白名单（本波改动）**：`run-as` 取回日志，本波时间窗（10:14Z+）非白名单 0 行；404 错误的 `run failed` 动态 provider message 未落盘；固定摘要形态正确落盘。✅
- OR Dots3（用户再次声明可用）：会话请求 `request rejected (HTTP 404)`（连续第五波设备真相）。⛔
- 自动压缩 `applied` 分支：本会话短、只观察 `below_trigger` / `nothing_to_drop`，未行使。⚠️

**回灌级别**：只写脱敏判据结论与 deviceLogTail 探针行；不回灌整段 logcat / 凭据 / 端点完整标识 / 序列号 / SSID / 路径 / 私人图片。题面一律英文无空格单词（本机 `input text` 连续 `%s` 会被吞，已规避）。

## 七、推送 + CI

待推送后回填。

## 八、明确未做（逐条带中文段位，编号见验收物）

RATIO 搬 policy / 单指针 / 同尺化（压缩量纲老号，复核后维持让位）｜内核本体再分层（内核再分层老号）｜1M 真压双硬限（1M 真压两条老号）｜MCP 外部 https（MCP 明文三条老号）｜journal 二选一（journal 二选一两条老号）｜思考块余项四件（思考块渲染老号）｜拥堵断流两条边界（拥堵断流老号）｜OR Dots3 404 供给方公告核查｜自动压缩 applied 与 Bolt/Nano 会话轮｜凭据轮换（用户侧）｜判据 7 波次盲区与协作用例文本形态（判据冻结）｜历史缺口不补造。

逐条触发信号与缓解动作见验收物「OPEN 挂账」表。

## 九、OPEN 挂账

逐条（触发信号 / 缓解动作 / 挂账号）见 `docs/analysis/w46-acceptance.md` §「OPEN 挂账」表。本波零新开零销账；思考块渲染老号维持 OPEN，两条已知边界维持已关行内注记。

## 十、交给外部审查团队的制品 + 边界声明

**制品**：`docs/analysis/w46-acceptance.md`、本交接、`docs/w46-dispatch.md`（派单卡）、`docs/06-deferred-ledger.md`（五行注记 + W46 段声明）、`docs/analysis/w45-acceptance.md` 与上一波交接（收尾订正）、`.github/workflows/secret-scan.yml`（路数注释）、`.github/workflows/build.yml`（测试数）、`evidence/raw/w46/`（原始脱敏输出 + 8 张判据截图）、代码主体逐文件 diff、`scripts/w46-*` 与样本 JSON。

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / uiautomator 噪声 /「建议下一链做什么」。

**边界**：① 判序以 21 样本双向混淆集 + JVM 变异双证据为准，强限流语境才能压过窗口词组合，quota/slow down 属弱词；② 白名单正文约束是 ASCII + 长度形态判据、非语义判断，ASCII 短自由文本仍可能落盘，不写成「完全不含用户内容」；③ 本地门禁第三路按 CI checkout 语义排除 `_w*/` 与 `local.properties`，本地全扫恒 RED 属预期；④ E6 MCP / E11 applied 分支未行使，四态如实 vacuous / ⚠️；⑤ OR Dots3 404 为设备真相连续第五波，未查公告；⑥ W9-01 未做是复核后诚实让位，不写成「已搬」，三待拍板按用户授权范围内派单卡推荐路径裁决、销否字面裁决不代裁；⑦ CI 绿以 run 四字段为准，tip 文档 run 降级补链、不作 P0。

> 本波止于交接，交由外部团队审查。
