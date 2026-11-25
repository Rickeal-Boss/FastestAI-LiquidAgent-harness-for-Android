# W56 验收物 — 收尾契约/机械自检订正 + 多模型真机矩阵

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 = W55 最终 tip `19e4aa5f426d4ad444393ec8d5e2273cd499089d`；本波代码提交 `bde9054cc77e1c7ab1536eb3baad67fa05fe568a`；最终 tip 以 atom 实取为准（见步 5 自洽口径）。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。本验收物不写复合挂账号，段位用中文描述。
> 挂账守恒 216 行 / 39 `CLOSED` / 166 `OPEN` / 11 `CARRIED`（逐值相等）。两数只写在派单卡抬头一份。

---

## 步 1 · 外审分歧裁决与方向

回源方式：全部处方先在现役树 `grep` / 实读再裁决；报告查的是它那一版的树，已存在 ≠ 待修。

| # | 分歧 / 主张 | 裁决 | 四态 | 依据 |
|---|---|---|---|---|
| 1 | 冻结项长远裁决书（元裁决 M1–M4 + R1–R5 + L1–L6）要求本波落笔 | **不代用户落笔，转触发信号登记** | ⚠️ | 该文件自述「落笔前不生效」，且 R5 journal 二选一须用户给字面值、M1–M4/L1–L6 多处替用户做方向裁决；用户本轮未逐字面裁决。按「不代用户裁决」写入派单卡条件触发专题波，不新开挂账号、不改状态列 |
| 2 | `allowPrivateNetwork` 正向透传缺口（上一波报告 P1-1，本波未修未登记） | **降级为口径订正：回源证伪「未透传」主张** | ✅ | 现役树回源：`HttpTransport.post(url, headers, body, allowPrivateNetwork=false)` 第四参存在（`:124/131/135`），`RemoteLlmEngine:147/205/212` 已透传；MCP 侧 `McpTransport.post` 端口（`:25-31`）刻意只有四参、`NetMcpTransport:42` 调 `http.post(url, headers, body)` 走 fail-closed 默认 false，MCP server 配置虽有 `allowPrivateNetwork` 字段（`McpModels.kt:25`）但未接进传输调用——即「LLM 端点正向开关有效、MCP 端正向开关未接」两个不同面，报告把前者漏读成全缺；因 MCP 0 服务器 vacuous，修法转安全收口老号第 1 车，本波登记不写码 |
| 3 | probe-form-sync 样本集扩到 FAILURE_DIGEST 固定主体 | **采纳收窄版，不并入本波** | ⚠️ | W52 已把「枚举覆盖为当前全集、未来新增 tag 须同步」登记为已知边界，15 类主体当前全在正则里、无已知漏登记；且 FAILURE_DIGEST 是拼接式常量不是 raw-string 形态。本波升级的渲染架构可作为后续载体，但不扩样本范围（冻结零追加），转安全收口老号行内注记 |
| 4 | 跨文件契约表落 AGENTS.md | **不采纳，降级为已有注记** | ✅ | `DiagnosticLogSink.kt:87-105` KDoc「形态与生产写点一一对应」已是一张逐写点对照表，再加 AGENTS.md 表是重复条款、违反「冻结的冻结」 |
| 5 | W35-02「README 已销 vs 权威台账 OPEN」 | **回源证伪 README 侧主张** | ✅ | 现役台账 `06-deferred-ledger.md:671` W35-02 状态列 `OPEN`，README 全文无「W35-02 已销」表述（`grep` 0 命中）；四波连审报告引用的「README 线订正」在其那一版树，本波树不存在，不据此改台账 |
| 6 | feature-chat 3999/4000 余 1 行、冻结书实测 4018/4000 | **回源取实跑值，订正冻结书口径** | ✅ | 本波 python 逐文件累加实测 `feature-chat/src/main = 3999`、`core-kernel = 449`、`GenerationRound.kt = 400`，与派单卡/历波文档一致；冻结书「4018/451/401」为另一时点/口径，按 M4「实跑为准」纪律不采信文档写死值。本波零触碰三个顶格对象 |
| 7 | 真机矩阵收缩趋势 vs 用户本轮明确要求铺矩阵 | **采纳用户指令，本波铺开** | ✅ | 用户本轮指令优先于派单卡默认轻量冒烟；派单卡第 1 车改为「用户指令铺开」，真机格见步 4 |

**方案席 429 中断，由主理人接手裁决**（配额纪律：不重试 spawn）。七条逐条回源，证据见上表文件:行。

---

## 步 2 · 代码（第 0 车）

### 2.1 机械自检现场渲染升级

| 改动 | 文件 | 判据 / 自证 | 四态 |
|---|---|---|---|
| 正则提取限定 `PROBE_FORMS` 列表区域并剥离行注释（旧版全文件 `re.findall` 会吃注释里的字面量） | `scripts/w55-probe-form-sync.sh` | 注释化正则变异：提取数 18→17、3 条样本 RED、rc=1 | ✅ |
| 7 条 POSITIVE 从生产拼接模板现场渲染（解析 `AgentLogStore` 调用的 Kotlin 字符串拼接、字面量与字段名原样取、变量位填数） | 同上 | 改写点形状变异（`char_budget=`→`char_cap=`）：渲染样本含 `char_cap=`、3 条 RED、rc=1（旧版此变异假绿 rc=0） | ✅ |
| `CACHED_LABEL` / `MAX_DIGEST_CHARS` / `FILE_NAME` 现场提取 | 同上 | 三常量与生产漂移即判红 | ✅ |
| 11 条反向哨兵保持 | 同上 | 全拦、无误穿透 | ✅ |
| 真删正则能力回归 | 同上 | 删新版正则 ⇒ 3 条 RED、rc=1；还原 ⇒ rc=0 | ✅ |

**变异自证实测表**（仓库外备份 + 精确字符串替换还原，零 git 写命令）：

| 变异 | 脚本 rc | 输出 | 四态 |
|---|---|---|---|
| 基线（现役树） | 0 | 18 条 raw 形态、7 正全命中、11 反全拦 | ✅ |
| A：生产写点 `char_budget=`→`char_cap=` | **1** | 3 条渲染样本含 `char_cap=` 不被命中 | ✅ 必红成立 |
| B：注释化新版压缩正则 | **1** | 提取数 18→17、3 条 RED | ✅ 必红成立 |
| C：真删正则整行 | **1** | 3 条 RED | ✅ 既有能力回归 |
| 还原后复跑 | 0 | 同基线 | ✅ |

**端到端 JVM 用例同源化裁决**：`newProbeFormsReachDiskEndToEnd` 保持逐字同形账行 + 真落盘断言，不做 JVM 内源码解析（会引入工作目录依赖、且测试基线冻结 904）。判据主力为现场渲染脚本 + JVM 真落盘用例，两者样本同形；此裁决为口径订正，不新增用例、不动 `EXPECTED_TESTS`。

### 2.2 收尾契约 / 账实订正（波内，无补链提交）

| # | 订正 | 位置 | 四态 |
|---|---|---|---|
| 1 | 「实际 2 提交」→「3 提交（含 1 个纯文档回填提交）」；最终 tip `c8e38fd`→`19e4aa5f` | W55 交接 §六、验收物步 5 | ✅ |
| 2 | 补 ③ 的 Secret `45563018742` success + Build 未触发；删「不新建提交」自指句 | 同上 | ✅ |
| 3 | 派单卡出口判据 5 改锚「最后代码提交 Build 绿 + ① 判红-修复链如实给号 + 最终 tip Secret 绿」 | `docs/w55-dispatch.md` | ✅ |
| 4 | 步 2.3「218 / 288 行」→「218 / 345 行」（实测 218/345） | `w55-acceptance.md` | ✅ |
| 5 | 台账安全收口老号行「9 处工具捕获」→「11 处 / 10 处重抛（另 1 处回调例外）」 | `06-deferred-ledger.md` | ✅ |
| 6 | 30 行探针头注明「24 上一波留存 + 6 本波新增」+ 末尾自检行 | `evidence/raw/w55/deviceLogTail.txt` | ✅ |
| 7 | g0 脱敏门禁三路原始输出入仓（补 W54/W55 已登记的证据缺位） | `evidence/raw/w56/g0_secret_gate.txt` | ✅ |

全部内容订正 + 一个脚本升级，**零 `.kt` 逻辑改动、零新挂账号、不回改历史四态、无补链提交**。

### 2.3 行数与测试基线

| 项 | 实测 | 四态 |
|---|---|---|
| `EXPECTED_TESTS` | 维持 **904**（严格行首 `^\s*@Test\b` 双向相等） | ✅ |
| `scripts/w55-probe-form-sync.sh` | 128→263 行（脚本无 G10 行数闸门） | ✅ |
| `DiagnosticLogSink.kt` / 测试 | 218 / 345 行（单文件上限 600） | ✅ |
| `feature-chat` / `core-kernel` / `GenerationRound.kt` | 3999 / 449 / 400（本波零触碰） | ✅ |

---

## 步 3 · 四闸门（原始输出入仓）

| 闸 | 结果 | 四态 | 证据 |
|---|---|---|---|
| G1 编译 | `localbuild.sh :app:assembleDebug` rc=0 | ✅ | `evidence/raw/w56/g1_build.txt` |
| G2 架构守卫 | `result=PASS ok=27 vacuous=1 violation=0 fault=0`（G19 vacuous、G20 141 ID） | ✅ | `evidence/raw/w56/g2_archguard.txt` |
| G3 守卫自测 | `30/30 PASS（301s）` | ✅ | `evidence/raw/w56/g3_selftest.txt` |
| G4 全量单测 | `tests=904 failures=0 errors=0 skipped=0`（14 模块） | ✅ | `evidence/raw/w56/g4_fulltest.txt` |
| 落盘形态机械自检（升级版） | 提取 18 条 raw 形态（已跳注释）/ 7 条现场渲染样本全命中 / 11 反全拦，rc=0 | ✅ | `evidence/raw/w56/g_probe_sync.txt` |
| 台账符号自检 | 21 类 / 64 方法 / 323 kt，rc=0（本波五条新注记未触发误判） | ✅ | 主理人实跑 |
| 脱敏门禁 | 三路全树 0 命中，rc=0（入仓输出已按占位符脱敏） | ✅ | `evidence/raw/w56/g0_secret_gate.txt` |

> 执行纪律：G0 首跑因证据命令回显含本机路径形态判红（3 处），按 W49 纪律改占位符 `<workspace-root>` / `<user-home>` 后复跑 0 命中；未加 allowlist、未 ignore 路径。

---

## 步 4 · 真机（多模型矩阵，密钥留设备）

设备先唤醒（`mWakefulness=Awake`）；端点能力以设备真相为准；密钥留于真机、未回灌。端点池（脱敏，仅 id/model/ctx）：nvidia Lightning 30B / Nano Omni 30B / Bolt E4B / SuperVL 120B（1M）；openrouter dots-3-note-preview:free / north-mini-code:free。

| 格 | 结论 | 四态 | 证据 |
|---|---|---|---|
| **回声（Nano Omni 30B / 128K）** | 输入框 dump 逐字 `Repeat this token exactly and nothing else: ECHO-W56NANO`；最终答案逐字 `ECHO-W56NANO`；`1.8k in / 77 out`、`上下文 1.9K / 127K`。三件套（dump + 答案 + 像素目视）全过 | ✅ | `p0_echo_nano.png` |
| **skill** | 技能页列出 `notes-organizer` / `pdf-toolkit`（来源 asset） | ✅ | `p1_skills.png` |
| **手动压缩 UI 路径** | 「更多操作 → 压缩上下文」确认框逐字弹出（含缓存重建提示），路径行使；本次为验证 UI 未确认压缩 | ✅ | `p2_compress_dialog.png` |
| **自动压缩 applied（长历史）** | 导入 183,905 字符 / 250 条 JSONL，发消息触发正常结束 ⇒ 设备侧落盘 `auto_compact dropped=60 kept=193 cache_invalidated=false` ×2、`prefix prompt=46196`，最终读数 `39.2k in / 188 out`、`上下文 39.3K / 123.9K`。丢弃切点真实行使、非 noop | ✅ | `deviceLogTail.txt`、`agent-0.log` 探针 |
| **openrouter 两免费端点** | Dots3 与 North 均返回 `本次请求失败：request rejected (HTTP 400)`，设备侧落盘 `run failed: request rejected (HTTP 400) [http:rejected]`。W54 时两者 404，本波 400——端点在用但请求被拒；用户声明「可用、工具+视觉双通」未被本次设备真相证实，记设备真相、不推断供给方原因 | ⚠️ | `deviceLogTail.txt`、UI dump |
| **多模态真图输入** | 320×180 双色测试图（左红方 + 右蓝圆 + W56VIS）已生成并 push 设备；SAF 图片通道本环境「本周」tab 报「暂时无法加载内容」、下载 tab 未索引新图 ⇒ 未成行；W53/W54 已验证过 Bolt 视觉，本波不编造 ✅ | ⚠️ | 工作区测试图（不入仓）；设备 SAF 现象 |
| **MCP 服务器** | 0 服务器 ⇒ 判据 vacuous，不推断「MCP 不可用」 | ⛔vacuous | 端点池未配置 MCP server |
| **新版 `context compressed` 五字段账行（内核每轮压缩路径）** | 本波长历史走会话级 auto_compact（dropped=60），内核 `ContextCompressor` 每轮压缩未触发（prompt 估算 token 低于 128K 档软预算）⇒ 设备侧未出现该行；判据主力仍为 JVM 端到端 + 现场渲染脚本，维持 W55 原四态 | ⛔vacuous | `deviceLogTail.txt`（无该形态行） |

**回灌级别**：只写脱敏判据结论、本波 10 行设备侧日志探针与 3 张脱敏截图；不回灌整段 logcat / 凭据 / 端点完整标识与密钥 / 账号字段 / 序列号 / SSID / 本机路径 / 用户私人图片。

---

## 步 5 · CI（公开 API 四字段）

提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；PAT 仅 URL inline 一次性使用，remote 保持干净。

**提交实态（自洽口径，不再随文档回填递归）**：唯一代码提交 `bde9054cc77e1c7ab1536eb3baad67fa05fe568a`；其后 `ab02d9b`（交接与验收物回填）、`5990e6b`（实态订正）为**纯文档回填提交**，按既有降级口径不计入交付判据、但计入提交实态。**最终 tip 不在本文档硬写 sha，以 atom 实取为准**；其后任何纯文档回填的 run 只声明存在、不逐条列号、不作交付判据（W50 口径）。

| 提交 | run | workflow | status | conclusion | updated_at |
|---|---|---|---|---|---|
| 代码 `bde9054` | `45614820018` | Secret scan | completed | **success** | 2026-11-25T12:33:25Z |
| 代码 `bde9054` | `45614820314` | Build | completed | **success** | 2026-11-25T12:34:12Z |
| 回填 `ab02d9b` | `45615559262` | Secret scan | completed | **success** | 2026-11-25T12:37:54Z |
| 回填 `5990e6b` | `45616128316` | Secret scan | completed | **success** | 2026-11-25T12:41:25Z |

回填提交 Build 按 `docs/**` paths-ignore 未触发（既定口径，显式声明）；代码字节自 `bde9054` 后未变，Build 绿继承。**交付判据 = 唯一代码提交 `bde9054` 双 run 全绿**（Build `45614820314` + Secret `45614820018`），公开 API 四字段实取。链接：`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45614820314`（Build）、`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45614820018`（Secret）。回填运行号只作事实记录、不作交付判据（显式声明，非漏记）。本地四闸门不代替 CI；CI 绿以公开 API 四字段为准。

---

## 明确未做（逐条带中文段位）

- 多模态真图语义回读（SAF 图片通道本环境不可达；功能矩阵老号）。
- 工作空间文件读写 + 审批闸门真机格（本波未铺；功能矩阵老号）。
- MCP 服务器真机格（0 服务器 ⇒ vacuous；明文三条 / 安全收口老号）。
- 多端点逐档位 1M / 512K 读数（本波只取 Nano 128K 回声与 SuperVL 1M 切换确认、long-history 档位；功能矩阵老号）。
- 真机触发内核新版 `context compressed` 五字段账行（本波触发的是会话级 auto_compact applied；内核路径仍 vacuous，见步 4）。
- G19 扫描面扩容（安全收口老号）、PNG 二进制自动门禁（脱敏门禁老号）、`McpServerEditScreen` 保存期 URL 校验（安全收口老号 / 第 1 车）。
- A4 记忆注入 / A5 代次门 / A6 双终态（转 OPEN 触发信号，见台账账实注记；用户单点开波，顶格文件先腾行）。
- `allowPrivateNetwork` MCP 正向透传（本波回源确认 LLM 侧已透传、MCP 侧未接；转安全收口老号第 1 车）。
- 自动压缩触发线按档位化、`errorMessage` 口径、切换守卫两把尺（压缩量纲老号余项）。
- 冻结项长远裁决书 M1–M4 / R1–R5 / L1–L6 落笔（用户未逐字面裁决，不代裁；转条件触发专题波）。
- 1M 满窗真压双硬限、MCP 外部 https、journal 二选一、内核再分层与顶格上限、North/Dots3 的 404→400 供给方核查、凭据轮换。
- 历史缺口不补造、不回改 W54 / W55 历史四态。

## OPEN 挂账（触发信号 + 缓解动作 + 段位；零新开零销账）

| 段位 | 触发信号 | 缓解动作 | 本波状态 |
|---|---|---|---|
| 落盘最小化老号 | 任一生产写点改形状 / 正则被注释而 `PROBE_FORMS` 未同步 | 机械自检升级为现场渲染 + 三条变异必红；删/注释正则即判红 | OPEN，行内注记 |
| 收尾契约老号 | 外审按最终 tip 复核运行四字段 / 自指句 | W55 3 提交 + 4 run、W56 1 代码提交 + 2 回填提交 + 6 run 按实态订正；最终 tip 交 atom 实取、不硬写 sha，判据锚唯一代码提交双 run | OPEN，行内注记 |
| 压缩量纲老号 | 压缩后仍熔断 / 自动压缩长期只 noop | 本波真机 applied 坐实会话级；触发线档位化等余项等用户单点开波 | OPEN，行内注记 |
| 安全收口老号 | 真实外部 MCP + 明文解禁 / G19 恒 vacuous / `allowPrivateNetwork` MCP 正向 / 双 workflow gitleaks 归因 | 本波回源厘清正向开关两面；FAILURE_DIGEST 样本扩张转后续载体；MCP token Keystore 等待开波 | OPEN，行内注记 |
| 台账纪律老号 | 文档声明数与实测哈希不符 / 同批 P1 漏登记 / 冻结书替用户裁决 | 行数账实 288→345、9→11/10 处；冻结书不代落笔、转触发信号 | OPEN，行内注记 |
| 脱敏门禁老号 | 新增二进制证据入仓 / 证据回显本机路径 | 逐张 verify + IEND + 目视；g0 三路原始输出入仓；入仓前门禁复扫 | OPEN，行内注记 |
| 真压两条 / 明文三条 / journal 两条 / 拆分三方 / 拥堵断流老号 | 各自触发信号见台账权威版 | 不代裁、不合并销账 | OPEN |

## 交给外部审查团队的制品清单（边界声明）

**制品**：本验收物、W56 交接、`docs/w56-dispatch.md`、台账（`### W56 段` + 五条老号行内注记）、`evidence/raw/w56/`（g1–g4 原始输出、升级版自检输出、g0 门禁、`deviceLogTail.txt`、3 张脱敏截图）、W55 三份文档订正、`scripts/w55-probe-form-sync.sh` 升级 diff。

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / 整段 logcat /「建议下一链做什么」。

**边界**：① 机械自检旧版确有「改写点形状 / 注释化正则」两型假绿，本波三条变异实跑坐实修复；② 端到端 JVM 用例同源化以同形注记固化，不在 JVM 内解析源码（工作目录依赖），测试基线维持 904；③ 真机 openrouter 两端点为 HTTP 400 设备真相，不推断供给方原因、不写成「可用」；④ 新版 `context compressed` 五字段账行内核路径仍 vacuous，会话级 auto_compact applied 为独立新增证据，两者不混同；⑤ 多模态 SAF 通道本环境不可达，如实标未复测；⑥ 冻结项长远裁决书不代用户落笔；⑦ 本波止于交接，交外部团队审查。
