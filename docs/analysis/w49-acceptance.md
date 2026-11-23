# W49 验收物 — 账实回补 / 门禁双键 / evidenceTag 全覆盖 / 真机多模型复核

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `94bbc53da168b5031f37d8d72dc0a3a11a0fb369`（W48 tip）。判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`。
> 「未触发」≠「已验证通过」；推断带 caveat、不写成事实。派单卡 = `docs/w49-dispatch.md`。
> 证据根 = `evidence/raw/w49/`。

---

## 步 1 · 分歧裁决（七份外审 + 本波用户指令）

### 1.1 采信并执行（P0-1 / P1-2..5 / P2-6..10）

| # | 外审主张 | 裁决 | 回源实测 |
|---|---|---|---|
| P0-1 | W48 声称「五处老号注记逐行留痕」，实为 1/5 | ✅ 采信 | `git diff 8e1d986 94bbc53 -- docs/06-deferred-ledger.md`：仅 `W36-01` 一条 + W47 段措辞订正 + W48 段新增；`W12-02`/`W9-01`/`W24-02`/`W24-01` 逐字节零 diff |
| P1-2 | 「kickoff 即裁决基准」仓内无更早出处 | ✅ 采信（措辞订正） | 该完整措辞仅见于 W48 自撰三文档；本波用户指令只对**矩阵**给了执行要求（多模型/多档位至 1M/多模态/文件/skill/MCP/压缩），对两老号无字面裁决 ⇒ 按推荐路径维持、不代裁 |
| P1-3 | 哈希白名单按内容而非文件身份豁免 | ✅ 采信 | `is_legacy_fixture()` 原为 `sha256sum \| grep -qxF "$h"`；册头自注「只含哈希、不含路径」；140 行 / 131 唯一 |
| P1-4 | `evidenceTag` 四类路径丢标签或残留 | ✅ 采信 | `GenerationRound:279` `Attempt.Fatal(failed.message, null)` 丢弃 `engine:watchdog`；`:331/:340` 与 `AgentRunner` 兜底无标签；`RunState.evidenceTag` 旧版只在非空时写 |
| P1-5 | W48 文档 tip run 未补号 | ✅ 采信 | 原文只写「按降级仅记录」，无 `45154919970` |
| P2-6 | 证据含 `hostname` 属 non-ASCII 用户名 | ✅ 采信 | `evidence/raw/w48/green_p02_paradigm.txt:106` 实存 |
| P2-7 | W48 段标题复述两数与条目数 | ✅ 采信 | 标题收敛为「本波销账集合 = 0 行」 |
| P2-8 | `context limit writeback: A -> B` 空转 | ✅ 采信 | 生产唯一写点 `ContextLimitWriteback.kt:66` 为下划线形态 |
| P2-9 | W48 真机 12 vs 13 格 | ✅ 采信 | 统一锚验收物 E1–E13 |
| P2-10 | 哈希册可审计性 | ✅ 采信 | 补路径→哈希对照表 + 入册命令 + 140/131 口径 |

### 1.2 判定为**外审误报 / 已订正项**（不据此改仓，逐条留证）

| # | 外审主张 | 判定 | 回源证据 |
|---|---|---|---|
| A | `build.yml`/`secret-scan.yml` 的 `on.push.branches` 为 `ain]` ⇒ push main 零触发 48 波（两波连审 P0） | ⛔ **误报** | 全历史 32 个提交逐版本读取该行恒为 `branches: [main]`（`174a832` 起）；`git log -S 'branches: ain\]'` **零命中恰是反证**（若出生即吞必命中 W1 提交）；W16 验收物 `:70` 早已记录同型「显示假象」结论。两次独立发生于审查侧链路（`[m` 两字符被吞） |
| B | `deviceLogTail.txt:44` 字节为 `ttp:rejected]`（导出链吞 `[h`） | ⛔ **误报** | 仓内实字节 `... request rejected (HTTP 404) [http:rejected]$`；全树 `grep ttp:rejected` 0 命中；W49 设备侧 `run-as` 直读同一形态完整（本波 `deviceLogTail.txt` 末行） |
| C | W48 `evidence/raw/w48/` 仅 `deviceLogTail.txt` 一件，8 格 png 未入仓 | ⛔ **误报** | 该目录实存 8 张判据截图 + `deviceLogTail.txt` + 闸门四件 + 判据先行五件（共 19 件） |
| D | W43 `w43-matrix-summary.json` 是「幻影制品」、从未存在 | ⛔ **已订正项** | `docs/analysis/w43-acceptance.md:141`（W44 补记）明写「本波为回归要点波、未产出 34 键 summary 与逐格 JSON，按外审二选一裁决取方案②，不补造制品」；外审未读到该订正 |

**误报形态入注记**：A/B 同型（ASCII 短串在审查侧传输/显示链路丢失 1–2 字符），已第 2 次出现外部误报；本仓对应处置 = 任何关键字节（分支名、标签、路径）以 `git grep` / `run-as cat` 的**字节**为准，不以转述文本为准。

---

## 步 2 · 第 0 车（账实回补与口径订正）

| 项 | 内容 | 四态 | 证据 |
|---|---|---|---|
| P0-1 回补 4 条注记 | `W12-02`（形态正则全覆盖）/ `W9-01`（449/450 行数前置）/ `W24-02`（判序范式转换）/ `W24-01`（连续第七波未获字面裁决）逐行补入，状态原样、pipe=7、不回开 | ✅ | `docs/06-deferred-ledger.md:147/185/478/480`（每行 len 增加、pipe 保持 7） |
| 新增 4 条 W49 注记 | `W36-01`（双键豁免面）/ `W12-02`（hostname 脱敏）/ `W24-02`（标签链路全覆盖）/ `W9-01`（段标题与格数口径） | ✅ | 同上四行 |
| W48 段标题收敛 | 删「判据冻结下零新开零销账，3 条 P1 / 4 条 P2 全走老号行内注记承接」，只留日期 + 销账集合 | ✅ | `docs/06-deferred-ledger.md` W48 段 |
| W49 段声明 | 新增 `### W49 段`，只写「本波销账集合 = 0 行」+ 销账/新开计数指路派单卡 | ✅ | 台账末段 |
| 三文档措辞订正 | 交接 §二#2 / §三 / §九 / 边界⑥、验收物步 1#2 / 步 2 / 边界⑥、派单卡裁决基准段 | ✅ | 四处「五处/5 条」→「W48 实落 1 条、W49 回补 4 条」；kickoff 措辞撤销 |
| P1-5 收尾契约 | W48 交接 §七 / 验收物步 5 补文档 tip `45154919970` 四字段 + 链接，口径「代码提交 2 条 + 文档 tip 1 条（降级）」 | ✅ | 两份文档同段 |
| P2-9 格数 | 交接 §六 加「本波 13 格，锚验收物 E1–E13；W47 12 格为时点记录」 | ✅ | 交接 §六 |
| P2-6 脱敏 | `green_p02_paradigm.txt:106` → `hostname="<host>"`；3b-ext 加属性形态判据（tracked + 未 tracked 两路同口径）；本地门禁同步 | ✅ | `evidence/raw/w49/g0_3bext_hostname.txt`（正控 rc=1 / 注入 rc=0 / 占位符豁免 / 门禁 RED→GREEN） |
| 闸门证据脱敏（本波自捕） | `g2_archguard.txt` / `g3_selftest.txt` 含本机工作区绝对路径与用户临时目录形态 ⇒ CI 3b-ext 判红；已改 `<workspace-root>` / `<user-home>` / `<rand>`，本地门禁 tracked 路复扫 0 命中 | ✅ | 首次推送 Secret scan `45182694954` failure → 修正后复跑 GREEN |

**台账守恒**：挂账行 216 / `CLOSED` 39 / `OPEN` 166 / `CARRIED` 11（与 W44–W48 逐值相等）；零 `W49-` 挂账行；G20 `141` ID 不变。

---

## 步 3 · 第 1 车（代码 + 判据先行）

### 3.1 P1-3 门禁哈希白名单双键

| 项 | 内容 | 四态 |
|---|---|---|
| 册格式 | `<sha256>  <relative/path>`（两空格），140 行 = 140 文件 / 131 唯一内容 | ✅ |
| 判据 | `is_legacy_fixture(rel)` 同时校验内容哈希与相对路径，同行匹配才豁免 | ✅ |
| 六态（`--selftest`） | ① 历史在场三路 0 GREEN ② 注入 ignored 探针 RED ③ `git add -f` tracked RED ④ 移除 GREEN ⑤ `--ci-semantics` GREEN ⑥ **同内容全新路径 RED**（W48 单键实态此态为 GREEN） | ✅ |
| 可审计性 | `evidence/raw/w49/legacy-fixture-map.txt` 140 行路径→哈希对照；册注释含入册命令与 140/131 口径 | ✅ |

**判据先行**：第六态在 W48 单键实现下**实测 GREEN（被静默豁免）**→ 双键后 RED → 移除后 GREEN。这是本波唯一「先证伪再修」的判据，`dup in book(pair)=0` 与 `RED same-content new path hit` 同行留痕。

### 3.2 P1-4 evidenceTag 链路全覆盖

| 改动 | 文件 | 说明 |
|---|---|---|
| `Attempt.Fatal` 增 `evidenceTag` + `digest` | `GenerationRound` | 看门狗 / 流异常路径不再丢弃标签 |
| 四处透传 | `GenerationRound:104/:272/:281` + `prepareRetry` 两处 | 看门狗、Fatal、流异常、重试放弃、半截输出 |
| 落盘摘要固定化 | `RunTermination.emitFailed(…, digest)` | 动态 provider 正文只进 ring；落盘只写固定模板 |
| 陈旧标签消除 | `GenerationRound` state 写入改**无条件覆盖** | 空串也写 ⇒ 不再残留上一轮标签 |
| 白名单扩主体 | `DiagnosticLogSink.FAILURE_DIGEST` | 2 类 → 13 类固定模板（含 6 类 HTTP、看门狗超时、重试放弃、半截输出、loop 退出、流异常、兜底） |
| 兜底路径 | `AgentRunner` | `engine:uncaught` + `uncaught failure`；`loop exited` 带 `engine:loop-exit` |

**4 条 JVM 必红**（`core-agent/.../EvidenceTagPropagationTest.kt`）：

| 用例 | 钉的缺陷 |
|---|---|
| `taglessFailureDoesNotCarryPreviousRoundTag` | 旧版只在非空写 ⇒ 500 被标成 `[http:rate]` |
| `watchdogFailureCarriesEngineWatchdogTag` | `engine:watchdog` 在转 Fatal 时丢弃 |
| `retryGiveUpDigestKeepsTagWithoutProviderText` | 放弃行拼 provider 正文 ⇒ 整行被白名单拦掉 |
| `uncaughtFailureDigestStripsDynamicText` | 兜底写异常正文 + 显式标签须覆盖 state 旧值 |

**判据先行**：四条用例在旧实现下会响（陈旧标签含 `http:rate`、看门狗行无标签、放弃行含 `429`、兜底行含路径）—— 断言分别为 `assertEquals` 精确行与 `assertFalse` 反向；修复后 `core-agent` 267 用例全绿。

### 3.3 P2-8 空转形态处置

`PROBE_FORMS` 删 `^context limit writeback: \d+ -> \d+$`（生产永不触发），替换为真实写点 `^context_limit_writeback_failed endpoint=[A-Za-z0-9_.:-]+ verified=\d+$`（`ContextLimitWriteback.kt:66`）；KDoc 写点清单同步改真实来源。正反控：真实形态 `assertTrue`、含空格/邮箱的伪造 endpoint `assertFalse`。

### 3.4 测试数与闸门

| 项 | 值 | 四态 |
|---|---|---|
| 严格行首 `@Test` | `891`（886 + 5：app 1 + core-agent 4） | ✅ |
| `EXPECTED_TESTS` | `891`（同提交对账，含 W48/W49 增量注释） | ✅ |
| G1 编译 | `assembleDebug` rc=0 | ✅ |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 141 不变）。本波首次跑出 G10 `GenerationRound 415>400` + G12 `AgentRunner 201>200`，按「拆分/精简不调上限」压回 400 / 197 | ✅ |
| G3 selftest | `30/30 PASS（268s）` | ✅ |
| G4 fulltest | `tests=891 failures=0 errors=0 skipped=0`（14 模块） | ✅ |

---

## 步 4 · 真机矩阵（OPPO PDRM00 / Android 13；唤醒三步法后安装）

设备 `<device-serial>`，`mWakefulness=Awake` + 非黑主屏截图确认后 `pm install -r -t`。密钥留于真机、未回灌。

| 格 | 端点 / 档位 | 判据 | 四态 | 证据 |
|---|---|---|---|---|
| E1 | NV Lightning 30B / 127K | `W49LIGHTNING` 逐字回引 + 思考块 + `上下文 1.9K / 127K` | ✅ | `e1_lightning_echo.png` |
| E2 | NV SuperVL 120B 1M / 1M 档 | `W49SUPERVL` 逐字 + 思考块 + `上下文 1.9K / 995.9K` | ✅ | `e2_supervl_1m_echo.png` |
| E3 | 同 E2，`workspace/` 前缀写 | `file_write workspace/w49ok.txt` → 设备侧 `files/harness/workspace/workspace/w49ok.txt` = `W49FILEOK`（`od -c` 逐字节 9 字符）；审批卡「允许一次」 | ✅ | `e3_file_rw_workspace_prefix.png` |
| E4 | 同 E2，无前缀写 + 读回 | `w49file2.txt` = `W49FILE2`（设备侧 `run-as cat` 一致） | ✅ | `e4_file_rw_noprefix.png` |
| E5 | 同 E2，多模态 320×180 | 「red square on the left and a blue circle on the right, both centered vertically against a plain white background」三要素齐 | ✅ | `e5_multimodal.png` |
| E6 | 同 E2，skill | `notes-organizer` 步骤逐条复述 + 末尾询问指向（`skill_load` 被采用） | ✅ | `e6_skill.png` |
| E7 | OR Dots3（dots-3-note-preview:free）/ 520.2K | `W49DOTS3` 逐字 + 思考块 + `上下文 1.7K / 520.2K` —— W48 首胜后**连续第二次会话级成功** | ✅ | `e7_dots3_session.png` |
| E8 | OR North（north-mini-code:free） | 会话级 `request rejected (HTTP 404)` 复现（W48 设备真相的第二波确认）；落盘行带 `[http:rejected]` | ⛔ 不可用 | `e8_north_404.png` + `deviceLogTail.txt` |
| E9 | MCP | 0 服务器（G15b 外部 https 阻塞），判据未被行使 | ⛔vacuous | 设置页 MCP 项 |
| E10 | 手动压缩（North 会话） | 确认框（缓存重建/较早消息不再发送/可撤销三句）走完 → 「没有可压缩的内容」 | ⚠️ 可压内容不足（正确行为） | `e10_manual_compress_noop.png` |
| E11 | 自动压缩（贯穿 E1–E8） | 设备落盘 `auto_compact=noop reason=below_trigger` 多次（评估链真跑）；`applied` 分支未触发 | ⚠️ | `deviceLogTail.txt` |

**设备侧复核（P1-4 正向）**：`run-as cat files/harness/logs/agent-0.log` 末行
`2026-11-23T12:55:19.622259Z ERROR run failed: request rejected (HTTP 404) [http:rejected]`
—— 逐字节完整，`[http:rejected]` 与源码模板一致，**不是**审查侧转述的 `ttp:rejected]`。

**端点能力差异（复用 W47/W48 观测）**：SuperVL / Dots3 显示「添加图片」；North 显示「当前端点未开启图片输入」。

**回灌级别**：只写脱敏判据结论与 `deviceLogTail` 探针行；不回灌整段 logcat / 凭据 / 端点完整标识 / 序列号 / SSID / 本机路径 / 私人图片。

**本波新坑（真机操作）**：`adb shell input text` 长串会**吞尾字符**（本波 `...W49LIGHTNIN` 少一个 `G`，补一次 `input text 'G'` 后 dump 校验通过）。纪律：**发送前必须 dump 输入框文本逐字校验**，不能以「命令返回 0」当作已输入。

---

## 步 5 · CI（公开 API 四字段实取）

| run | 对象 | head_sha | status | conclusion | updated_at |
|---|---|---|---|---|---|
| `45182694954` | Secret scan（首提交，3b-ext 抓到闸门日志的本机路径形态） | `3d9766df…` | completed | **failure** | 2026-11-23T13:19:12Z |
| `45185252002` | Build（最终 tip） | `4b2be946a9f46651202e86794e42b7cff7bfa9fb` | completed | success | 2026-11-23T13:35:38Z |
| `45185251937` | Secret scan（最终 tip） | `4b2be946a9f46651202e86794e42b7cff7bfa9fb` | completed | success | 2026-11-23T13:34:41Z |

- 红态如实记录：首提交 Secret 判红 → 证据脱敏 fix（`4b2be94`）→ 重推 → 最终 tip 双绿。**交付判据 = 最终 tip 双 run 全绿**。
- 链接：Build `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45185252002`；Secret `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45185251937`。
- 本验收物所在文档提交为纯文档 tip：Build 0 run（`paths-ignore` 既定口径），Secret run 按 W29 降级仅记录。

---

## OPEN 挂账（触发信号 / 缓解动作 / 挂账号）

本波零新开零销账，以下均为既有老号，逐条给触发信号与缓解动作（编号见台账）。

| 挂账号段位 | 触发信号 | 缓解动作 |
|---|---|---|
| 压缩量纲同尺化余项（`W9-01`） | 32K 档出现「字符闸熔断 + 手动压缩 `nothing to drop`」并存 | 须先获用户对内核再分层 / 450 上限的一字裁决；本波未动 kernel |
| 内核本体再分层（`W20-02`） | core-kernel 触及 450 | 拆分或上限处理须用户裁决；本波零 kernel 改动 |
| 1M 满窗真压双硬限（`W20-05` / `W9-11`） | 需要证明满窗行为 | 注入式双硬限仍锁「单独裁决」；本波只做常规请求 + 1M 档读数 |
| MCP 外部 https（`W5-03/05/06`） | 需要真实 MCP 服务器 | G15b 明文流量闸门未解；本波 MCP 判据 vacuous |
| journal 二选一（`W24-03`） | 需要落 journal 制品 | 仍须用户二选一（开波 / 永不开波） |
| 思考块余项四件（`W24-01`） | 玻璃节点/AGSL 边界复现 | 连续第八波未获字面裁决，按推荐路径维持 |
| 判序两条已知边界（`W24-02`） | `retry:` 透传 / 可点重试入口 | 维持 `CLOSED` 行内注记，不回开 |
| 自动压缩 `applied` 分支 | 需要证明压缩真实生效 | 需构造跨越触发线的历史；本波只看评估链（`below_trigger`），不造假 dropped |
| North 供给面 | 连续两波 404 | 建议向供给方报障或摘池；用户侧动作 |
| 凭据轮换 | PAT 曾以 URL inline 使用 | 用户侧动作，仓内只记事实 |

---

## 明确未做（逐条带挂账号段位）

压缩量纲同尺化余项（压缩量纲老号）｜内核本体再分层（拆分三方老号）｜1M 满窗真压双硬限（真压两条老号）｜MCP 外部 https（明文三条老号）｜journal 二选一（journal 两条老号）｜思考块余项四件（思考块渲染老号）｜拥堵断流两条边界（拥堵断流老号）｜判序两条已知边界（维持已关行内）｜价格维度（省钱外审方向，本波不开）｜凭据轮换（用户侧）｜Nano Omni / Bolt E4B 会话轮（本波时间窗未行使，不写成已验证）｜判据 7 波次盲区与协作用例文本形态（判据冻结）｜历史缺口不补造。

---

## 边界

① 双键哈希白名单只覆盖路 2/路 3；tracked 路 1 永不豁免。② 落盘白名单放宽的是**主体枚举**（固定模板），不是长度或字符集；动态正文仍只进 ring。③ `evidenceTag` 的 `digest` 机制只保证「落盘可读」，不保证 ring 与落盘文本一致（ring 保留完整 message）。④ `workspace/xxx` 是沙箱下的子目录，**不是**沙箱根别名（E3 实测）；把它当根写会得到嵌套目录，行为一致但易误读。⑤ 1M 档为常规请求读数，非满窗真压。⑥ 外审误报 A–D 不据以改仓，只留证；如后续出现新证据可再议。⑦ CI 绿以最终 tip 的 run 四字段为准。

---

## 交给外部审查团队的制品

- 本验收物、`docs/handoff-20261123-wave49.md`、`docs/w49-dispatch.md`、`docs/06-deferred-ledger.md`（W49 段 + 8 条注记）；
- `evidence/raw/w49/`：`deviceLogTail.txt`、10 张脱敏判据截图、`g0_3bext_hostname.txt`、`legacy-fixture-map.txt`、G0 六态 / G1–G4 原始输出；
- 代码主体逐文件 diff：`GenerationRound.kt` / `RunTermination.kt` / `AgentRunner.kt` / `DiagnosticLogSink.kt` / `DiagnosticLogSinkTest.kt` / `EvidenceTagPropagationTest.kt` / `build.yml` / `scripts/w46-local-secret-gate.sh` / `scripts/fixtures/w46-gate-legacy-hashes.txt` / `.github/workflows/secret-scan.yml`；
- 不含：会话历史、凭据、端点完整标识与密钥、未打码账号字段、设备序列号、SSID、本机路径、用户私人图片、uiautomator 噪声、任何「建议下一链做什么」。
