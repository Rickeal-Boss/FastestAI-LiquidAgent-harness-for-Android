# W55 验收物 — 落盘形态回补 + 收尾契约/账实订正 + 多模型真机轻量冒烟

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 = W54 tip `63f3ff945f0fab93055d11b97776d1c66aa834c9`。判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`。
> 「未触发」≠「已验证通过」；推断带 caveat、不写成事实。本验收物不写复合挂账号，段位用中文描述。
> 守恒 216 行 / 39 `CLOSED` / 166 `OPEN` / 11 `CARRIED`（逐值相等）。两数只写在派单卡抬头一份。

---

## 步 1 · 外审分歧裁决与方向

回源方式：全部处方先在现役树 `grep` / 实读再裁决；报告查的是它那一版的树，已存在 ≠ 待修。

| # | 分歧 / 主张 | 裁决 | 四态 | 依据 |
|---|---|---|---|---|
| 1 | N-1 键同一性：一份报告称「定谳用例键 `parallel_`、误拒仍在」 | **不采纳，维持 W54 定谳成立** | ✅ | 现役 `EndpointAdvancedValidation.kt:68` 白名单 = `"parallel_tool_calls"`（完整键）、`EndpointCapabilitiesTest.kt:248` 用例键同为 `parallel_tool_calls`、`validateAdvanced` 通过；该报告 §六自曝「python 输出键名被渲染链吞字为链路假象」——核心证据自认失真；另三份报告与三处历波 handoff 支撑成立 |
| 2 | gitleaks 误报治理：建议切 allowlist / 对台账 `--exclude` | **不采纳，维持改形状** | ✅ | 派单冻结 schema 零增删；`--exclude` 违反 secret-scan 门禁不 ignore 路径；措辞精确化 = 不为生产日志拼接新增 allowlist/exclude，保留既有精确标识符与夹具 allowlist |
| 3 | A4 记忆注入 / A5 代次门 / A6 双终态「漏登记三条 P1」 | **转 OPEN 触发信号，不纳入本波代码** | ⚠️ | 不在第0车白名单；A5/A6 受 feature-chat 3999/4000 余 1 行 + 文件域双限；纳入即违反冻结零追加。三条已写进台账账实行内注记 |
| 4 | 第0车 P0：W54 改写点形态后漏登记 3 条落盘形态（三份报告一致） | **采信补回** | ✅ | 回源证实：新版账行 140 字符不被旧正则匹配、`endpoint_store` 两条未登记；`PROBE_FORMS` 确实缺这三条 |
| 5 | 收尾契约第 11 次 / 账实订正 / P2 五条 | **采信，波内文档订正，严禁第 5 提交** | ✅ | 派单卡第0车第 4/5/6 条明确；波内完成、历史四态不回改 |

**方案席独立复核**：四项 ✅ 同意、一项（gitleaks）措辞保留，无判错项。判据可执行性经自检发现两处风险并已消解（见步 2）。

---

## 步 2 · 代码（第 0 车）

### 2.1 P0 落盘形态回补

| 改动 | 文件 | 判据 / 自证 | 四态 |
|---|---|---|---|
| PROBE_FORMS 补 3 条整行固定形态正则（新版 `context compressed` 五字段 / `endpoint_store=unreadable` / `endpoint_store=seed_write_failed`），**无 `startsWith` 兜底** | `app/.../DiagnosticLogSink.kt` | 每条 `^...$` 整行锚定 + `\d+` + 固定字面量；旧形态正则保留兼容 before 变体 | ✅ |
| 3 条正向必红 @Test + 1 条端到端落盘用例 + 反向断言并入既有方法 | `app/.../DiagnosticLogSinkTest.kt` | **变异自证实跑**：未修版（只改测试）`18 tests completed, 3 failed`（三条新正向全 RED、反向不误红）；加回正则 ⇒ `BUILD SUCCESSFUL` 18/18 | ✅ |
| 机械自检脚本从源码**现场提取**正则（非内置应有表）、python `re.fullmatch` 复刻 Kotlin `Regex.matches` 整行语义，含 7 条必落盘样本 + 11 条反向哨兵 | `scripts/w55-probe-form-sync.sh`（新增） | 正确实现 rc=0（7 正全命中、11 反全拦）；删新版压缩正则 ⇒ 3 条压缩样本 RED / rc=1；删 endpoint 两条 ⇒ rc=1；恢复 ⇒ rc=0 | ✅ |
| 脚本接入既有 secret-scan job（零新 job / G 编号 / schema） | `.github/workflows/secret-scan.yml` | 台账符号自检 step 之后追加一个 step，`if [ ! -f ]` 守卫有 | ✅ |

**样本同源自检（方案席风险消解）**：3 条正向样本逐字对照生产拼接 `ContextCompressor.kt:135-141` 与 `EndpointStore.kt:112-122`；含 `char_budget/char_target = 2147483647`（Int.MAX_VALUE，10 位）极值样本；反向样本（字段顺序调换、多字段、结尾无 tokens、拼用户自由文本）在正确实现下全 `assertFalse`。账行长度 136-148 字符 < `MAX_DIGEST_CHARS=160`，未被长度闸提前拦。

**端到端落盘用例**：仿 `probeLineSurvivesFloodOfUserContentLines`，直接 `AgentLogStore.info(新版账行)` → `fileStore.recentLines()` 断言该行真落盘——证明「判据相对生产拼接响」而非仅对样本响。此加固使严格 `@Test` +4（3 正向 + 1 端到端），`EXPECTED_TESTS` 900 → **904**（同提交对账）。

### 2.2 收尾契约 / 账实订正（波内，严禁第 5 提交）

| # | 订正 | 位置 | 四态 |
|---|---|---|---|
| 1 | 补最终 tip `63f3ff94` Secret `45453331670` success + Build 未触发；③ `a1b111e` Secret `45452852010` FAILURE 如实给号，改「只声明存在」为逐号列出 | `handoff-20261125-wave54.md` §六、`w54-acceptance.md` 步 5 | ✅ |
| 2 | 派单卡出口判据 5 改「最后代码提交 `89ac235` 双绿 + 最终 tip Secret 绿」 | `w54-dispatch.md` | ✅ |
| 3 | 「13 个 `.kt` 生产」→「21 生产 + 4 测试 = 25」；「31 文件 / +406 −40」注明是 feat 提交 diff | handoff 边界①、验收物边界① + 两处制品清单 | ✅ |
| 4 | 双 workflow（build.yml + secret-scan.yml）各跑一次 gitleaks 归因补全 | `w54-acceptance.md` 步 1 | ✅ |
| 5 | 取消重抛「9 处」→「11 处 `catch(Throwable)` / 10 处重抛（`SpeakTool` 1 处例外）」 | handoff 概述 + 表格、验收物步1/2.2、w54 派单卡 | ✅ |
| 6 | `ContextCompressor.kt` 注释里的误报字面量形态改写（改注释、不加 allowlist，写点字符串未动） | `core-context/.../ContextCompressor.kt:134` | ✅ |
| 7 | 三张压缩截图名实、g0 证据缺位、两数纪律复述各补一行注记 | `w54-acceptance.md` 步 3/4、抬头 | ✅ |

全部内容订正，**零 `.kt` 逻辑、零新挂账号、不回改历史四态、不为补链新建第 5 提交**。

### 2.3 行数与测试基线

| 项 | 实测 | 四态 |
|---|---|---|
| `EXPECTED_TESTS` | 900 → **904**（严格行首 `^\s*@Test\b` 实测 904，双向相等） | ✅ |
| `DiagnosticLogSink.kt` / 测试 | 218 / 288 行（单文件上限 600） | ✅ |
| `ContextCompressor.kt`（注释 +1） | 363 行 < 400；core-context 模块 1535 < 1650 | ✅ |
| `core-kernel` / `GenerationRound.kt` / `LINE_CAPS` | 零改动 | ✅ |

---

## 步 3 · 四闸门（原始输出入仓）

| 闸 | 结果 | 四态 | 证据 |
|---|---|---|---|
| G1 编译 | `localbuild.sh :app:assembleDebug` rc=0；`BUILD SUCCESSFUL` | ✅ | `evidence/raw/w55/g1_build.txt` |
| G2 架构守卫 | `result=PASS ok=27 vacuous=1 violation=0 fault=0`（G19 仍 vacuous，G20 141 ID） | ✅ | `evidence/raw/w55/g2_archguard.txt` |
| G3 守卫自测 | `30/30 PASS（354s）`（VAC-G1b / VAC-G21 自证 PASS） | ✅ | `evidence/raw/w55/g3_selftest.txt` |
| G4 全量单测 | `tests=904 failures=0 errors=0 skipped=0`（14 模块） | ✅ | `evidence/raw/w55/g4_fulltest.txt` |
| 落盘形态机械自检 | `提取 18 条整行形态，MAX_DIGEST_CHARS=160` / `OK 7 条必落盘样本全命中，11 条反向哨兵全拦下`，rc=0 | ✅ | 主理人实跑（脚本 `scripts/w55-probe-form-sync.sh`） |
| 脱敏门禁 | 三路全树 0 命中；新增文本 / 截图脱敏复核；`deviceLogTail` 30 行探针形态外以机械自检口径核对 | ✅ | `evidence/raw/w55/deviceLogTail.txt` |

> 执行纪律：G2 曾因两个进程并发写同一文件被截断，已按写隔离冻结重复进程、串行重跑一次取干净输出（rc=0）。

---

## 步 4 · 真机（轻量冒烟，密钥留设备）

设备先唤醒（`mWakefulness=Awake`）；端点能力以设备真相为准；密钥留于真机、未回灌。端点矩阵（脱敏，仅 id/model/ctx）：nvidia Lightning 30B / Nano Omni 30B（视觉）/ Bolt E4B（视觉）/ SuperVL 120B（1M）；openrouter dots-3-note-preview:free（512K，工具+视觉）/ north-mini-code:free。

| 格 | 结论 | 四态 | 证据 |
|---|---|---|---|
| **回声（Nano Omni 30B / 128K）** | 输入框 dump 逐字 `repeat this token exactly and nothing else: ECHO-W55OK`；最终答案逐字 `ECHO-W55OK`；`1.9k in / 96 out (1792 cached, 92.5%)`、`上下文 1.9K / 127K`。三件套（输入框 dump + 最终答案 + 像素目视）全过 | ✅ | `p0_echo_nano.png` |
| **skill** | 技能页列出 `notes-organizer` / `pdf-toolkit`（来源 asset） | ✅ | `p1_skills.png` |
| **手动压缩 UI 路径** | 「更多操作 → 压缩上下文」确认框逐字弹出；确认后会话回空态 ⇒ 压缩路径确实行使（非 vacuous） | ✅ | 主理人 adb dump 逐字 |
| **新版 `context compressed` 账行落盘（真机）** | 本次压缩会话仅 1 轮，未产生丢弃切点 ⇒ 设备日志未出现新版账行；判据主力由 JVM 端到端落盘用例 + 脚本变异自证覆盖，真机侧此格 `⛔vacuous`（未触发丢弃路径，不写成已验证、也不编造） | ⛔vacuous | `deviceLogTail.txt`（仅见 manual_compress/auto_compact 历史行，无新版 context compressed） |
| **多模态 / 文件读写审批 / MCP / 多端点 1M 与 512K 读数** | 本波按派单卡轻量冒烟，未铺全量回归；列为明确未做 | ⚠️ | 见下「明确未做」 |

**回灌级别**：只写脱敏判据结论、`deviceLogTail` 固定形态探针（30 行）与 2 张脱敏截图；不回灌整段 logcat / 凭据 / 端点完整标识与密钥 / 账号字段 / 序列号 / SSID / 本机路径 / 用户私人图片。

---

## 步 5 · CI（公开 API 四字段）

提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；PAT 仅 URL inline 一次性使用，remote 保持干净。

> 本节待最终推送后回填：最后代码提交双 run 四字段 + 最终 tip Secret 四字段 + run 链接。

---

## 明确未做（逐条带中文段位）

- 多模态真机输入（本次仅轻量冒烟，附真图 × Bolt/Nano 未铺；功能矩阵老号）。
- 工作空间文件读写 + 审批闸门真机格（本次未铺；功能矩阵老号）。
- MCP 服务器真机格（0 服务器 ⇒ vacuous；明文三条老号 / 安全收口老号）。
- 多端点 1M / 512K / 404 读数（SuperVL 1M、dots3 512K、north 404 本次未逐格取；矩阵形态收官转条件触发专题波）。
- 真机触发 `context compressed` 新版账行落盘（需 264K 长历史 + 实际丢弃切点；本次会话历史不足，判据由 JVM 端到端 + 脚本变异覆盖）。
- G19 扫描面扩容（安全收口老号）、PNG 二进制自动门禁（脱敏门禁老号）、`McpServerEditScreen` 保存期 URL 校验（安全收口老号 / 第1车）。
- A4 记忆注入 / A5 代次门 / A6 双终态（转 OPEN 触发信号，见台账账实注记；用户单点开波，A5/A6 需先腾 feature-chat 行）。
- 自动压缩触发线按档位化、`errorMessage` 口径、切换守卫两把尺（压缩量纲老号余项）。
- 1M 满窗真压双硬限、MCP 外部 https、journal 二选一、内核再分层与 449/450、North/Dots3 的 404 供给方核查、凭据轮换。
- 历史缺口不补造、不回改 W54 历史四态。

## OPEN 挂账（触发信号 + 缓解动作 + 段位；零新开零销账）

| 段位 | 触发信号 | 缓解动作 | 本波状态 |
|---|---|---|---|
| 落盘最小化老号 | 任一生产写点改形状而 `PROBE_FORMS` 未同步 | 本波补 3 条形态 + 必红 + 机械自检接闸；删正则即判红 | OPEN，行内注记 |
| 收尾契约老号 | 外部审查按最终 tip 复核 CI 四字段 | 本波补最终 tip Secret 四字段 + 判红号 + 出口判据改锚最后代码提交 | OPEN，行内注记 |
| 压缩量纲老号 | 压缩后仍熔断 / 自动压缩长期只 noop | W54 已修同尺；本波补回账行形态；余项等用户单点开波 | OPEN，行内注记 |
| 安全收口老号 | 真实外部 MCP + 明文解禁 / G19 恒 vacuous / 双 workflow gitleaks 归因 | 本波补 gitleaks 归因与 allowlist 措辞；MCP token Keystore/G19 扩面待开波 | OPEN，行内注记 |
| 台账纪律老号 | 文档声明数与实测哈希不符 / 同批 P1 漏登记 | 本波账实订正 + A4/A5/A6 转触发信号写档 | OPEN，行内注记 |
| 脱敏门禁老号 | 新增二进制证据入仓 | 逐张 verify + IEND + 目视；自动门禁待接 step | OPEN |
| 真压两条 / 明文三条 / journal 两条 / 拆分三方 / 拥堵断流老号 | 各自触发信号见台账权威版 | 不代裁、不合并销账 | OPEN |

## 交给外部审查团队的制品清单（边界声明）

**制品**：本验收物、W55 交接、`docs/w55-dispatch.md`、台账（`### W55 段` + 五条老号行内注记）、`evidence/raw/w55/`（g1–g4 原始输出、`deviceLogTail.txt`、2 张脱敏截图）、代码 diff、`scripts/w55-probe-form-sync.sh`、W54 三份文档订正。

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / 整段 logcat /「建议下一链做什么」。

**边界**：① P0 是「该落盘的行不落盘」（方向安全，漏登记只损失落盘、不损失 ring），非数据外泄；② 真机新版账行格 vacuous、判据主力是 JVM 端到端 + 脚本变异，不写成真机已验证；③ 回声判据三件套全过、裸哨兵澄清问句不算回声（W52 教训）；④ W54 历史四态全部维持、不为补链新建第 5 提交；⑤ 多模态/文件/MCP/多端点读数本波未铺，如实列明确未做；⑥ 本波止于交接，交外部团队审查。
