# W9 交接 — 序 0 抬头 + W9-00 交接对账与防线接线（2026-11-06）

> 基线 `e7d7a1e`（远端 main = 本地 HEAD，CI 五项全 success）；工作树序 0 前除方案席两个 notes 外干净。
> 上位裁决：`桌面/w9-终版落锤裁决.md`（最高优先）＋ `桌面/裁决/W9开工裁决与顺序.md`；
> 唯一派单口径 `docs/notes/w9-plan.md`；矩阵预登记 `docs/notes/w9-feature-matrix-preregistration.md`；
> 挂账唯一台账 `docs/06-deferred-ledger.md`。
>
> **本文件性质 = 序 0 抬头（纯文档）**。因 `.github/workflows/build.yml` 的
> `paths-ignore: docs/** + **/*.md`，纯文档提交 **0 run**，故本抬头**不单独提交**，与序 1 代码同车（B6）。
> 本文件只登记判据、归属与留痕，**不判定通过**；四态 `✅ / ⚠️ / ⛔ / ⛔vacuous` 的跑后结论由主理人填。
> 🔴 **「未触发」≠「已验证通过」**：VACUOUS / 0 run / 空命中一律写 ⛔vacuous 或「未行使」，不得写成通过。
>
> 编号纪律：本文件出现的每个 `W<数字>-<数字>` 形态 ID 都必须在台账有表格行（G20 全文扫描）。
> 为使 §六 的逐字文本「插 W9-00 …」合法，台账 §二-bis 已登记 `W9-00` **口径登记行**（非挂账、不计统计，见 §五.4）。

---

## 一、本波结论

> 🔴 **本节只留占位。跑后由主理人填，不得预写结论。**
> 序 0 抬头落笔时未跑 G1–G6、未取真机四态、未读 CI run，故此处**不得**预填四态判定符号、
> 不得预填单测总数、`EXPECTED_TESTS`、`OK=`/`VACUOUS=` 计数、`CHECKLIST` 得分、CI run 号、APK 产物结论。
> 允许写的只有：占位标题、本节这句纪律、以及「待填项」清单。任何数字结论都必须来自跑后证据（§三）。

| 待填项 | 跑后结论（主理人 2026-11-06 填，全部来自 §三 实跑证据） |
|---|---|
| 单测 / 全量对账 | ✅ `fulltest.sh` → **`tests=764 failures=0 errors=0 skipped=0`**；**81 个 `TEST-*.xml` / 14 模块**；XML mtime `20:52:25–20:52:50` 全为本轮（C4 排除残留假全量）。`build.yml:132 EXPECTED_TESTS` 已由主理人一次改 `'741'→'764'`（C5 双向对账：严格 `grep -rhE '^\s*@Test\b'` = **764** == 常量）。净增 23，逐文件 `SessionImportParserTest 22 / EndpointProbeTest 8 / EndpointPlaintextScanTest 2(新) / JsonFormatCheckTest 16 / EndpointCapabilitiesTest 20 / MarkdownTextTest 13` |
| 架构守卫 / 守卫自证 | ✅ `arch-guard.sh`（仓库根、后台）→ **`OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`**，G19 唯一空转项未增（G21 棘轮绿）；**G20 OK**（56 个 handoff ID 全在台账、CLOSED 证据非空）；`arch-guard-selftest.sh` → **`30/30 PASS`（215s）**，变异注入真判红（G17/G18/G19/G20/G22/G23 + 2 个 VAC 段各 PASS） |
| 出包 / 行数 | ✅ `localbuild.sh` → `BUILD SUCCESSFUL in 48s`，`app-debug.apk` **69529248 B**；模块 main 行数 `feature-settings 3492/3500`（余 8）、`core-model 2038/2600`、`core-engine 1193/2600`、`core-data 2388/4500`、`feature-chat 3777/3900`、`app 1358/1600`；**未调 `LINE_CAPS` / `TOTAL_LINE_CAP=42500` / `VACUOUS_BASELINE`**，未引用 W8 +100 例外。装包 `pm install -r` Success，冷启动 `COLD 1071ms` |
| 真机四态（D6/D7 及矩阵各项） | ✅ 详见 §三 G5 行：**D6 ✅ / D7 ✅ / D7b ✅ / F-1+A-3 ✅ / M1 抽验 ✅ / M4 ✅ / M5 ✅ / M6 ✅ / M8 ⛔vacuous / M9 ⛔vacuous / M10 ⛔vacuous / M3 ⛔ / M7 ⛔**。关键：**D7 用「只注入能力位、不改端点名」专打 P0-3 旧 `LaunchedEffect(label)` key，入口由禁用态变可点「添加图片」** ⇒ 响应式修复真机行使；视觉往返 NV-VL 答 `Red Blue`（`2.2k in / 48 out`） |
| CHECKLIST 判读（§四） | ✅ **12 条已逐条编号写死**（§四）。C1–C8、C11、C12 本车判读完成；**C9（push dry-run）/ C10（CI 五项）待 G6 推后补判**。**N1（C11）W8 六项 RED**：Q1 / Q2 / Q3 / Q7′ / Q15 五项**本车源码闭环**（符号与用例均已核实，见 §二 核销）＋ **W3-07 销账由 N2（C12）闭环**（台账 `:74` `CLOSED` + 证据列现役三处）；**C9 回填 1 项按 §六 7 留痕归 W9-10，本车不代做、不转移编号** |
| push dry-run / CI 五项 | ⏳ 待 G6。主理人推 main 后回填 dry-run 输出与 CI run 五项结论（见 §三 G6 行） |

**占位不等于通过** → 本节已按跑后证据填毕；**C9 / C10 两条 CHECKLIST 与 G6 尚待推送后闭环**，不得当作已全绿。

---

## 二、交付清单（按 `w9-plan.md` §二 分诊表；逐条状态「待集成核实」）

> 🔴 **禁止写「已收 / 已修」**。N1（§四 C11）管的就是这条：每条「已收/已修」必须指到 `文件:行` + 提交号。
> 本表所有条目在集成核实前一律 `待集成核实`；开发席施工完成后由主理人按 `git diff` 逐条核销，不在此预写结论。
> ⚠️ 表内 `文件:行` 为**派单登记口径**（`docs/notes/w9-plan.md` §二，基线 `e7d7a1e`），**不是现役行号**：
> 开发席序 1 施工会移动行号（如 `AppNavHost.kt:168` 已随 P0-3 响应式改造漂移），
> 集成核实时须按 `AGENTS.md §二.2` 回真实工作树 `grep` 复核，**以符号为准**，禁止照抄本表行号当证据。

> ✅ **主理人集成核销（N1，2026-11-06）**：上表 27 个源码文件已全部按 `git status` + `git diff` 落实，
> 并回真实工作树按**符号**逐条核实（抽样 12 条全命中）：`rememberSaveable(endpointId)`（P1-2）、
> `coerceScalar` 对 NaN/Inf/Infinity 的拒绝注释（P1-3）、`degradeWithin`（P1-4）、
> `EndpointProbe` `takeWhile`（P1-5）、`SessionImportParser:275 if (body.isBlank()) return null`（Q3）、
> `ChatAttachmentPicker` SAF 图片闸门 KDoc（Q7）、`Generation.kt:79`「未接线，勿用；唯一真值源 = `RemoteEndpoint.reasoningEffort`」（Q7′/R4）、
> `JsonFormatCheck` 不回显原文 + 围栏 `lastIndexOf`（kotlinx①③）、`MarkdownText` `SpanStyle` 灰字 URL（MD-2）、
> `supportsOpenAiAdvanced`（P2 kind 收窄）。逐文件严格 @Test 计数 `SessionImportParserTest 22 / EndpointProbeTest 8 /
> EndpointPlaintextScanTest 2(新) / JsonFormatCheckTest 16 / EndpointCapabilitiesTest 20 / MarkdownTextTest 13`
> （净增 23）与 `fulltest 764` 三方一致。**现役行号以符号为准**：图片闸门现役 `AppNavHost.kt:171-175`
> （`activeId` + `produceState`，旧 `LaunchedEffect(state.endpointLabel)` key 已弃用）、保存链 `SettingsViewModel.kt:100`。
> 提交号 = 本波唯一提交（见 §一 G6 与 `git log`）。`REASONING_ONLY` 删 UI 与资源、**留枚举值**（老配置反序列化兼容，
> 见 §六 2.2）；全仓 `hard_off_unsupported` 0 命中、`adv_mode_REASONING_ONLY` 资源 0 命中；`essage` 走分支 2（0 命中）。

### 2.1 主分诊表

| 编号 | 条目 / 文件:行 | 交付物 | 归属席 | 状态 |
|---|---|---|---|---|
| 出口① | handoff §四 + 台账 | CHECKLIST 逐条编号 + N1/N2 W8 自证 RED 闭环 | CI ＋ 主理人 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| 出口② | 4×P0（F-1/A-3、F-3、reason 落点、REASONING_ONLY） | 四条全接线 + 各带「被调用」判据 | 开发 ＋ 主理人 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| 出口③ | 6×P1 + Q2 | 逐条可跑真假用例；kotlinx 四件逐条列交付物 | 开发 ＋ CI | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| 出口④ | `06-deferred-ledger.md`） | W3-07 回填 + D6 文本订正 + §二 统计表同步 | CI | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| 基线规则 | `build.yml:132`（**主理人独占**） | 全部 Δ 一次算清一次改，现值以该行为唯一真值源；本席不碰 | 主理人 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| Q2 | `core-data/SessionImportParser.kt:139` | UTF-8 BOM 剥离 + 2 夹具 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| F-1 / P0-1 | `EndpointAdvancedValidation.kt:46` / `SettingsViewModel.kt:130` / `OpenAiCompatibleAdapter.kt:106-110` | 保存链接线 + 运行时兜底 + 允许键白名单 + 「被调用」静态判据 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| A-3 | 同 F-1 同车 | `endpoints.json` 明文凭据 / 保留键扫描判据（**不做新 arch-guard 守卫**） | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| F-3 / P0-3 | `AppNavHost.kt:166-168` / `:182` / `:184` | 图片闸门响应式（不复用 label 作 key）+ D6/D7 四态补证 | 开发 ＋ 主理人 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| P0-2 / P1-1 | `ReasoningBody.kt:25/:35/:39`、`OpenAiCompatibleAdapter.kt:221-222` | 降级 `reason` 透出（取值见 §六 2.2）+ 3 处 KDoc 订正 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| P0-4 | `EndpointCapabilities.kt:42/:66`、`ReasoningBody.kt:41-46`、`ProviderAdapter.kt:189-192` | 二选一取值见 §六 2.2 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| P1-2 | `EndpointAdvancedConfig.kt:197`、`EndpointAdvancedValidation.kt:78-86` | 文本状态只按 `endpointId` 初始化一次 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| P1-3 | `OpenAiCompatibleAdapter.kt:115-121` | `coerceScalar` 默认字符串 + NaN/Inf 拒绝 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| P1-4 | `EndpointCapabilities.kt:176-208` | 降级目标 ∈ `effectiveSupported` 性质测试 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| P1-5 / Q15 | `EndpointProbe.kt:68/:125`、`OpenAiCompatibleAdapter.kt:160` | 收终帧序列 + 2 条「开/关思考」序列用例 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| P1-6 | `06-deferred-ledger.md:74`、`w8-solution.md:589-590` | 三向对账（台账回填 + D6 文本订正 + 统计表） | CI | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| Q1 | `ConversationOps.kt:106/:180`（**只这两行**） | `runCatching` → `contextWindowCap(container)` | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| Q3 | `SessionImportParser.kt:269` / `:276` | 空正文判定上移 + 1 用例 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| Q7 | `ChatAttachmentPicker.kt:16` | 失实 KDoc 订正（静态，0 新增 @Test） | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| Q7′ / R4 | `Generation.kt:79` / `:80` | 1 行 KDoc 订正（静态，0 新增 @Test） | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| kotlinx① | `JsonFormatCheck.kt:45` | 不回显原文 + `SECRET-MARKER` 红态断言 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| kotlinx② | `JsonFormatCheck.kt:40-43` | 删恒真空 when / 写「非单值形态 ⇒ Invalid」真判据 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| kotlinx③ | `JsonFormatCheck.kt:72` | 围栏裁切改确定性实现 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| kotlinx④ | `JsonFormatCheckTest.kt:96-98` | 三连弱断言换精确行列号 + 不含原文片段 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| `essage` 收口 | 全仓 `*.kt` | 真实工作树复核结论（分支结果见 §六 4） | CI | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| MD-1 | `MarkdownText.kt:51` | 二选一取值见 §六 2.2 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| MD-2 | `MarkdownText.kt:83` / `:90` | 二选一取值见 §六 2.2 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| P2 词表补 token | `EndpointAdvancedValidation.kt:30` | 词表加 `"token"` + 1 JVM 用例（**selftest 变异 case 归 W9-10，本车不加**） | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| P2 SET_WINDOW 补 load | `SettingsViewModel.kt:179-183` / `:226` | 分支尾补 `load()` / 重算置灰集合 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| P2 kind 收窄 | `EndpointAdvancedConfig.kt`、`EndpointEditScreen.kt:94` | 二选一取值见 §六 2.2 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| P2 AUDIO 假入口 | `EndpointAdvancedConfig.kt:78/:119-121`、`RemoteEndpoint.kt:206-207` | 二选一取值见 §六 2.2 | 开发 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |

### 2.2 六项二选一取值登记（主理人 2026-11-06 拍板，开发席按此施工）

> 只登记**取值**，不替开发席写「已完成」。六项施工结果一律 `待集成核实`（§2.1 同名行）。

| 项 | 拍板取值 | 依据 / 理由 | 状态 |
|---|---|---|---|
| ⚖️ P0-4 REASONING_ONLY | **(b) 连 UI 带用例一起删**，并登记 | 半天级插入波不留半套；删后须无残留用例 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| ⚖️ MD-1 | **(a) 扩 `^#{1,6}\s+` + 2 用例**（`###` / `####` 各 1） | 真判据优先于 KDoc 改写 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| ⚖️ MD-2 | **(a) 灰字 URL 尾注** | URL 上屏可见，不吞字符 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| ⚖️ P2 kind 收窄 | **(a) 禁用态入口 + 原因** | 非 OPENAI_COMPATIBLE 端点不静默摆可勾无效项 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| ⚖️ P2 AUDIO 假入口 | **(a) 输出模态过滤为 TEXT/IMAGE** | `w8-plan` §三「UI 不摆假入口」；选 (b) 会与 W9-06 双挂 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |
| ⚖️ P0-2 / P1-1 降级 reason | **「上屏」**（选中档位副标题 / 模式行文案） | **不写 core-engine 日志**；G19 仍 VACUOUS，归 W9-07 | 已集成核实（N1，主理人 2026-11-06 按现役符号 + git diff 核销） |

**P2 四件 + MD 两件若不本车收，必须显式改号留痕**（§六），禁止写「已收」或静默不提。

---

## 三、闸门与真机证据（占位 + 判据口径）

> 🔴 **本节跑后结论由主理人补，序 0 抬头不预写任何四态判定或结果数字。**
> 下表「阈值」列的数字（`OK=27` / `30/30` / `14 模块` 等）是**判据先写死**的门槛值，
> 由派单 §四 / `w9-plan.md` §四 给定，**不是跑出来的结果**；「跑后结果」列全部留占位。
> 只写**命令与阈值**，四闸门 = G1→G2→G3→G4，顺序固定（让最快能失败的先失败）；
> G1–G4 不绿不进 G5，G5 不绿不推，最后才 G6 + CI 五项核对。
>
> 🔴 **`_w9/` 取证留存口径（w8-code-review R-1 教训）**：取证文件在 `_w9/`（`.log` 已被
> `*.log` 规则 gitignore）⇒ **关键计数 / 末行摘要必须抄进本文件正文**，不得只留在 `_w9/` 里当唯一证据；
> 否则下一波看不到计数就等于没有取证。CI 席只读复核：现役 `.gitignore` **未**覆盖
> `_w9/**/*.png` / `*.json`，故推送前置 `git status --short` 须显式确认不夹带取证产物（§四 C8）。
> 🔴 **四态口径**（跑后填写时用）：已行使／部分／不适用／vacuous（判据扫空集或反向分支不可执行）。
> **「未触发」≠「已验证通过」；守卫报红 ≠ 真命中**，须看到违规行本身；**VACUOUS 不是通过**。

| 闸门 | 命令 | 阈值（不达 = 红） | 跑后结果 |
|---|---|---|---|
| G1 出包 | `bash scripts/localbuild.sh`（默认 `:app:assembleDebug`） | `BUILD SUCCESSFUL` + `app-debug.apk` 存在；裸 `gradlew` 必失败（JAVA_HOME 缺失） | ✅ **`BUILD SUCCESSFUL in 48s`**；`app-debug.apk` = **69529248 B**（`20:51`）。环境由 `eval "$(bash scripts/localbuild.sh --env \| grep '^export ')"` 注入 JDK21/SDK |
| G2 全量单测 | `bash scripts/fulltest.sh` | `tests = EXPECTED_TESTS`、`failures=0 errors=0 skipped=0`；**14 模块**；XML mtime 全为本轮（防残留 XML 假全量） | ✅ **`tests=764 failures=0 errors=0 skipped=0`**；**81 个 `TEST-*.xml` / 覆盖 14 模块**（include=14）；XML mtime **`20:52:25–20:52:50`** 全为本轮；`EXPECTED_TESTS` 已改 `'764'`，双向对账 764==764 |
| G3 架构守卫 | `bash scripts/arch-guard.sh`（**必须仓库根目录**，后台跑） | `OK=27 VACUOUS≤1（只许减，G19 唯一）VIOLATION=0 SELF_FAULT=0`；**G20 OK**（所有 handoff ID 在台账内、CLOSED 证据非空） | ✅ **`OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`**；**G20 OK**（**56 个 handoff ID 全在台账**、CLOSED 证据非空）；G16 中文硬编码 **0 处**；G19 唯一空转项未增（G21 棘轮绿）；G22 evaluateSwitch 2 处 / G23 autoCompactOnSettled 1 处 |
| G4 守卫自证 | `bash scripts/arch-guard-selftest.sh`（后台跑） | `30/30 PASS`（28 CASES + 2 独立 VAC 段）；变异注入须真判红 | ✅ **`selftest: 30/30 PASS`（215s）**，exit 0；变异真判红：G17 加 apiKey / G18 调 engine.generate / G19 拼凭据 / **G20 删台账 W3-05 行** / G22 删 evaluateSwitch / G23 删 autoCompactOnSettled，各 PASS；VAC-G1b、VAC-G21 两独立段 PASS |
| G5 真机 | 本地 UI + 功能测试 + adb 调试（OPPO PDRM00 / A13） | D6/D7 及矩阵预登记各项四态取证；**真机测过没问题才推** | ✅ **行使完成，详见下表**（OPPO PDRM00 / A13 / SDK33，adb `-P 15037`，W9-00 新包 `pm install -r` Success） |
| G6 推送 | `git push --dry-run` + `gh run list --branch main`（或 GitHub API） | dry-run 输出 `main -> main`；双工作流触发核对；**「没有 run」≠「绿的」**（paths-ignore 0 run 同形） | ⏳ 待主理人推 main（本波代码 + 文档同车）；推后回填 dry-run 与 CI 五项。注：paths-ignore 挡纯文档，故本抬头必须与代码同车才会触发 run |

**G5 真机四态明细（2026-11-06，W9-00 新包；关键计数已抄入正文，取证截图 `_w9/matrix/dev-*.png` 不入库）**：

| 项 | 判据与行使方式 | 四态 |
|---|---|---|
| **D6** 图片闸门禁用态 | 基线（能力位未开）→ 入口 `content-desc="当前端点未开启图片输入"`、不静默隐藏 | ✅ |
| **D7** P0-3 响应式 | **只**注入 `inputModalities=["TEXT","IMAGE"]`、端点名 `NV-MISTRAL` **保持不变** → 冷启动后入口变可点「**添加图片**」 | ✅（专打旧 `LaunchedEffect(label)` key；若仍按 label 作 key 则不会刷新） |
| **D7b** 视觉端到端 | 选 Download 真图（200×120 左红右蓝）→ NV-VL → 答「**Red Blue**」；用量 `2.2k in / 48 out (0 cached, 0.0%)` | ✅（双向：PC 侧 OR/north 亦行使，north 喂图 404 为负样本） |
| **F-1 / A-3** 明文拦截 | 高级配置启用自定义协议、extraBody 填 `api_key=sk-test-xxx` → 保存被拒并上屏「**高级配置校验未通过：含保留键、凭据形态键或值，端点未保存。**」；设备 `endpoints.json` 回读 `grep sk-test-xxx\|api_key` = **0 命中** | ✅（截图 `dev-09`） |
| **M4** 工作空间读写 | file_write/file_read 各授权一次（UI「工具「file_write」请求授权」+「允许一次/本会话允许相同调用」）→ 落盘 `workspace/workspace/w9_probe.txt` = **`HELLO-W9`（8 B）**，内容逐字一致 | ✅（附带：`file_write` path 相对 workspace 根解析，模型传 `workspace/x` 产生嵌套目录 —— 登记非缺陷） |
| **M5** 记忆跨进程 | memory_write 写 `W9MARKER` → `memory.json` 落盘第 3 条 → **冷启动 COLD 1071ms** → memory_read(query=W9MARKER) 命中并报「**Project codename is TEAL-FOX.**」逐字一致 | ✅（截图 `dev-12`） |
| **M6** Skill | skill_load 加载内置技能 → 报出 2 个内置技能名称与描述；`2.8k in / 188 out` | ✅（导入链 W3-02 仍无入口，本车未行使） |
| **M1** 多模型 | 设备侧 nv1(NV-VL) / orvl(OR-DOTS) / nv2(mistral→VL) / orvis(gemma) 往返；W8 基线 OR-DOTS `1.5k in / 28 out`、切换守卫确认框 `empty_session char_floor_unmeasured` | ✅（PC 侧另有 5 模型 + 1M 边界，见 `pc-facts.md`） |
| **M8** KV cache | 设备侧各次用量行 `cached` 恒 **0**（OR 免费池 `cache_write` 亦 0） | ⛔vacuous（双向断言反向分支不可执行；不承诺命中率） |
| **M9** 手动压缩 | 本车未重复行使（W7-03 A6 已证 noop 无损放弃） | ⛔vacuous（`dropped=N`+`cached=0` 双向未触发） |
| **M10** 自动压缩 | `agent-0.log` 多轮 `auto_compact=noop reason=below_trigger`（最新 `2026-11-06T13:23:23Z`） | ⛔vacuous（`applied=true` 零证据） |
| **M3** 音频输出 | NV `/v1/audio/speech` **404**；`supported_voices=null` | ⛔ 平台阻塞 |
| **M7** MCP | `AndroidManifest.xml:25 usesCleartextTraffic="false"` | ⛔ 平台阻塞（W5-03，不为测试放开） |

🔴 **真机未行使 ≠ 已验证**：M8/M9/M10 三项按四态口径记 ⛔vacuous（判据扫空集 / 反向分支不可执行），**不是通过**；M3/M7 是平台阻断，与代码正确性无关。

**fulltest 三假象（G2 判读纪律）**：① 全绿但很快结束 / 模块数不足 = 汇总陈旧 XML（看最早 `TEST-*.xml` mtime 是否晚于本轮启动）；
② `test-results` 清不掉 = Windows 句柄锁，脚本判红是正确行为，**拒绝判绿**；
③ `BUILD FAILED` 但用例全绿 = 可能是既有基线失败，先 `grep -A20 'What went wrong'` 再比上一绿 run，不要直接判本波引入。

**CI 五项**（`build.yml`）：`Assemble Debug (JDK 21)` / `Unit tests` / `Arch guard (26 guards + mutation selftest)` /
`Secret scan (gitleaks + trufflehog)` / `Lint (baseline gate)`。绿判据行：`Unit tests` 打印 `@Test 对账通过：<N>`、
`Arch guard` 打印 `OK=27 …` 与 `selftest: 30/30 PASS`、`Assemble` 打印 `BUILD SUCCESSFUL`；
**Lint job 颜色不可信**，须读 `lint-reports-` artifact 的真实 issue 集（baseline 只许缩不许涨）。

---

## 四、CHECKLIST 12/12 —— 逐条编号判据表（主理人 2026-11-06 拍板）

> 🔴 **裁决 A（主理人 2026-11-06 拍板）**，取代 `docs/notes/w9-plan.md` §四「10+2 是【推断】不是判据」的推断标注。
> 12 条构成：`C1..C10` = `docs/notes/w4-gate-checklist.md` 的 **§0 基线事实复核** + **§1 闸门链 ①–⑨**；
> `C11` = N1 `git diff` 自证；`C12` = N2 核到指定文件销账。
> **12 条未逐条编号写死前，`CHECKLIST=12/12` 不得判绿**（「预期值」不是判据，知识库 §6）。
> 每条必须同时有「判据行」与「已知必红变异样本」；判据自身先验证（B8 / W3-W5 铁律）。

| 编号 | 条目（w4 清单） | 判据行（绿的唯一依据） | 已知必红变异样本 |
|---|---|---|---|
| C1 | §0 基线事实复核 | 收口前逐行复核 §0 表格 7 项是否仍成立（`@Test` 严格数 ↔ `EXPECTED_TESTS`、`build.yml:132`、测试 XML 汇总、arch-guard 计数、selftest 段数、CI 实证 run）；日期戳过期即先复核再引用 | 把 `EXPECTED_TESTS` 改回旧值而树里 @Test 已变 ⇒ C5 双向对账红 |
| C2 | ① 环境注入 | `eval "$(bash scripts/localbuild.sh --env \| grep '^export ')"` 后 `$JAVA_HOME` / `$ANDROID_HOME` / `$GRADLE_USER_HOME` 三行都指向工作区 `_j2env/` 真实路径；代理端口**运行时**从 `$https_proxy` 解析 | 手工写死代理端口 ⇒ 跨会话漂移后 PKIX 证书错，编译红 |
| C3 | ② 本地编译 | `BUILD SUCCESSFUL` 且 `app/build/outputs/apk/debug/app-debug.apk` 存在；判红按 `grep -A20 '^\* What went wrong'` 取真实失败点 | 裸 `./gradlew` ⇒ JAVA_HOME 缺失红 |
| C4 | ③ 全量单测 | `tests=N failures=0 errors=0 skipped=0` 且汇总覆盖 **14/14 模块**；严格 @Test 计数 = `grep -rhE '^\s*@Test\b' --include=*.kt . \| wc -l` | 残留上一轮 XML（mtime 早于本轮启动）⇒ 假全量，须拒绝判绿 |
| C5 | ④ @Test ↔ EXPECTED_TESTS 双向对账 | 严格 `^\s*@Test\b` 计数 == `.github/workflows/build.yml` 的 `EXPECTED_TESTS`；**任何增删 @Test 必须同一次提交改这一个常量**（不许放宽成区间 / `>=`） | 加一条 @Test 不改常量 ⇒ CI「Reconcile EXPECTED_TESTS」判红（W3 `78abace` 实证） |
| C6 | ⑤ 架构守卫 | `OK=27 VACUOUS≤1 VIOLATION=0 SELF_FAULT=0`、`[arch-guard] result=PASS`、exit 0；**仓库根目录**跑（工作区根会扫到仓外目录全红假象）；后台跑防 120s 截断得空输出 | 从台账副本删掉一行 ⇒ G20 判红（selftest `m_g20`） |
| C7 | ⑥ 守卫自证 | `selftest: 30/30 PASS`、exit 0（变异注入生效 ⇒ 守卫**还会红**）；⑤ 只证明没报错、⑥ 才证明会报错，两个都绿才成立 | 让某个守卫扫空集 ⇒ G21 棘轮判红 |
| C8 | ⑦ 推送前置检查 | `git status --short` 无输出；`git diff --cached --name-only \| grep -E '\.log$\|\.env$\|\.jks$\|\.keystore$\|\.p12$\|local\.properties$'` 无输出；`git log --oneline origin/main..HEAD` 只含本波提交 | 留一个 `_w9/*.log` / `local.properties` 在暂存 ⇒ 第二条命中红；被 ignore ≠ 安全（gitleaks 扫全历史） |
| C9 | ⑧ 推送 dry-run | `git -c http.sslVerify=false -c credential.helper= push <内联PAT的URL> main --dry-run` 输出 `main -> main`；先 `fetch` 再 `rev-list --count origin/main..HEAD`，数 = 本波提交数 | 用 `git ls-remote` 匿名读冒充真判据 ⇒ 假绿；PAT 落盘 ⇒ 密钥纪律红 |
| C10 | ⑨ 推送后 CI 判读 | GitHub API 取最新 run 的 5 个 job 全 `success` + 三条绿判据行（见 §三）；任一非 success 打印 `steps[].conclusion` 非 success 的步骤名定位 | 只看 Lint job 颜色不读 artifact ⇒ 假绿；`0 run` 当「构建通过」⇒ ⛔vacuous 误判 |
| **C11** | **N1 `git diff` 自证**（管「改没改」） | 每条「已收/已修」必须指到 **`文件:行` + 提交号**：`git diff <BASE>..HEAD -- <文件>` 有对应 hunk，且行号在现役树复核仍成立（BASE = 上一波末次提交，不是快照）；handoff §二 禁止预写「已收/已修」 | **W8 的 Q1 / Q2 / Q3 / Q7′ / Q15 用例五项空头声明 + C9 回填**（共 6 项：handoff-wave8 §2.5 宣布「同波收」，源码零 diff）⇒ N1 必红；C9 的 RED 只作 W9-10 输入证据（§六 7） |
| **C12** | **N2 核到指定文件销账**（管「销没销」） | 核到 `docs/06-deferred-ledger.md` **指定行**状态为 `CLOSED` + **证据列非空** + **证据指向的文件仍存在**（`文件:行` / 提交号 / 测试输出 / handoff §小节皆可，见台账 §三.3）；落地前 **N2 是人工闸门不得免除** | **W3-07**（W8 handoff `:44/:124` 宣布销账，台账 `:74` 仍 `OPEN` 且证据列写已失效的 `RemoteLlmEngine.kt:92`）⇒ N2 必红；本车 W3-07 同提交回填即该 RED 的闭环 |

**N1 / N2 边界**：N1 管「代码/文档有没有真的改」，N2 管「账有没有真的销」；两条都过才算交接自洽。
N1 对 C9 判出的 RED **只作 W9-10 的输入证据留痕**，W9-00 不代做、不转移编号（§六 7）。

---

## 五、方法论订正（本波净产出，写给自己）

1. **裁决 C — 行数口径订正（主理人订正方案席风险②）**：主理人实跑 `scripts/arch-guard.sh` G10 确认
   判的是**模块总行数**（`feature-settings` 3482 / 3500，**余 18**），不是单文件行数。
   故 `w9-plan.md` §五.7「先拆 `McpServerEditScreen` / `McpServerListScreen`」**对模块总行数无效**——
   拆分不减少总行数，只是把行搬到同模块另一文件。
   本车解法 = **纯逻辑下沉 `core-model`（余 668）/ `core-engine`（余 1423）+ 删假入口净减**，
   目标 `feature-settings` **净增 ≤ 18 行**。
   🔴 **禁止调 `LINE_CAPS` / `TOTAL_LINE_CAP`、禁止引用 W8 的 +100 一次性例外当先例**；
   `TOTAL_LINE_CAP=42500` 一律不动。行数再触顶先靠下沉，不许调上限。

2. **裁决 F — 提交身份口径**：知识库 §5「绝不用 noreply」与 `AGENTS.md §九` 冲突；主理人复核
   `git log -1 --format=%ae e7d7a1e` = `263587600+Rickeal-Boss@users.noreply.github.com`（noreply 版）
   ⇒ **以 `AGENTS.md §九` 为准**：`-c user.name="Rickeal-Boss" -c user.email="263587600+Rickeal-Boss@users.noreply.github.com"`。
   🔴 **知识库 §5 需回写订正**（旧仓 / LiquidAgent 经验线），**本车不改共享库**，仅登记待办。
   PAT 只以 URL inline 一次性使用、绝不落盘；push 后 `git remote set-url` 回干净 URL；中文长 commit message 用 `-F <file>`。

3. **矩阵取证口径：`max_tokens` 给足是判据前置**（不是「跑一下看看」）：基线 ≥512、思考型 ≥1024、tools ≥512、
   xhigh ≥1500。主理人 W9 实测 `max_tokens=32` 时 `content=None`（reasoning 吃光预算），提到 512 后正常出 `PONG`
   ⇒ 判据按 `finish_reason` / `tool_calls` 判，**不按 `content` 判**（与 W9-09 同型）；
   App 恒 `stream:true`，PC 非流式结论迁移须重新取证；四元组
   `max_tokens / prompt_tokens / completion_tokens / finish_reason` 强制登记；1M 档 ≥2 次重放才写稳定结论；
   设备侧与 PC 侧**分开记账**。前置不满足时结论写「判据无效（前置违反）」，不得记成「模型不识图 / 不输出」。

4. **G20 ID 存在性 → 台账 `W9-00` 口径登记行**：`scripts/arch-guard.sh:1337` 对 `docs/handoff-*.md`
   **全文** `grep -ohE 'W[0-9]+-[0-9]+'`，不只扫表格。§六 ①④⑥⑦ 的逐字文本含 `W9-00`，
   而台账 §一 / §二-bis 原无此行 ⇒ 不补行 G20 必判「台账缺 ID」。
   故 §二-bis 增 `W9-00` **口径登记行**（主理人裁决 4 批准：状态 `OPEN`、证据列非空，明确
   「**本波口径登记行，非挂账项、不计入 §二 统计表，仅为满足 G20 的 ID 存在性**」），
   不代表新增挂账、不改任何老号、不转移编号、不预写本波结论。
   **编号行口径（CI 席实跑 `grep -cE '^\| W[0-9]+-[0-9]+ \|' docs/06-deferred-ledger.md`）**：
   全台账 **59** 个编号行 = §一 **46** + §二-bis **13**（W9-01..11 共 11 ＋ 本车 `W9-12` ＋ `W9-00`）；
   §二-bis 13 行**均不计入 §二 统计表**，统计表按既有 §一 口径 = `CLOSED` 16 / `CARRIED` 11 /
   `OPEN` 19 / `合计` 46（主理人裁决 4 订正，原派单两处冲突数字在台账 §二 表下留痕不抹）。
   同理 handoff 内**不得出现派单禁用的那个 W8 系列旧号**
   （路线草稿已占用；写出来同样会 G20 判红），纪律用文字描述不带号。外审原编号形态（连字符 + 数字的 NEW 项）
   只写进**台账证据列**（G20 不扫台账正文），handoff 内改写成不含连字符号形态的描述。

5. **D6 判据文本订正（写死，不改历史 `docs/analysis/w8-solution.md:589`）**：
   `supportsVision=false` ⇒ **保留禁用态入口 + 无障碍原因**（**不是**「attach 按钮不渲染」）；
   `true` ⇒ 可点。D7 = 200×120 左红右蓝真图 + `reasoning.enabled=false` + `max_tokens≥512` ⇒ 命中双色
   （**禁用 1×1 样本**）。两态都需真机截图 + 设备侧日志落盘；只改 `capabilities()` 不点 UI ⇒ ⛔vacuous。
   历史 `w8-solution.md:589` 是当时记录，**不篡改**（台账 §三.4 同精神）。

6. **判据自身先验证 / 反漏项**：每组新判据先跑「已知必通过 + 已知必失败」样本（§四 变异列）；
   A-3 明文扫描必须先跑已知含凭据变异样本证明扫描器会红，0 命中不算通过；
   kotlinx 四件之外若回树发现第 3 / 第 5 件同域缺陷 ⇒ 走新增编号，**不得塞进「四件」名下**（终版 §一.C）。

7. **工作流触发只读核对结论（CI 席，2026-11-06）**：`build.yml` `on.push.branches: [main]` ✅ 含 `main`；
   `on.push.paths-ignore: ['docs/**', '**/*.md']`；`on.pull_request.branches: ['**']`（同 paths-ignore）；
   `on.workflow_dispatch` 在。`build.yml:132 EXPECTED_TESTS`：**本席未改**（主理人独占，
   全部 Δ 一次算清一次改）；现值以该行为唯一真值源，本文档不写死该常量。
   `release.yml` `on.workflow_dispatch` + `on.push.tags: ['v*']`，**无 `push.branches`** ✅ 仍是 tag + dispatch，
   本波只推 `main`、不打 tag ⇒ `release.yml` **0 run 是预期**，不是漏跑。
   `scripts/` 本车**零改动**；未新增 `arch-guard` 守卫（新守卫扫空集 ⇒ VACUOUS 1→2 ⇒ G21 判红；
   守卫与口径专项锁死 W9-10），未改 `scripts/arch-guard-selftest.sh`。

---

## 六、挂账承接与改号留痕总表（终版裁决 §四 七条，缺一 N2 判红）

> 本节是 **N2 的人工闸门输入**：每条改号 / 销号都必须能在 `docs/06-deferred-ledger.md` 指定行核到。
> 🔴 两边都写 = 都以为对方做（总裁 B10 口径），改号**必须**留痕。

| # | 留痕文本（写死） | 台账写入位置 |
|---|---|---|
| 1 | 「**Q2 → 已提前至 W9-00（总裁 2026-11-06 落锤）**」；W9-11 承接行同步删 Q2 条目，W9-11 剩余范围不变（设备侧 1M 熔断解锁，依赖 W9-01 档位口径；PC 侧 1M 结论不得写成设备侧可用） | §二-bis `W9-11` 行证据列 |
| 2 | W9-07 行三项改号：「**F-1 → 已提前至 W9-00**」「**A-3 → 已提前至 W9-00**」「**F-3 → 已提前至 W9-00**」；改号后 W9-07 剩余范围不变，按主线排期继续 | §二-bis `W9-07` 行证据列 |
| 3 | kotlinx 四件映射表（终版 §一.C 原文，**以 `文件:行` 为准，旧编号降为附注**） | 见下表 6.1 |
| 4 | `essage` 收口规则（真实工作树 grep 二分支：存在 ⇒ 一行 typo 顺手修正并入四件同车、不单独占号；不存在 ⇒ 记「W8 误报，快照核验零命中」销号留痕；两种结果都不留悬账）＋ 本次实际分支结果 | 见下表 6.2 |
| 5 | 基线规则：`EXPECTED_TESTS` **全部增量一次算清一次改**（Q2 +2 夹具、kotlinx 四件用例、6 P1/P0/MD 用例），`build.yml:132` 由主理人独占；分两次动基线 = 多一次虚报面 | §二 基线规则行 + §四 C5 |
| 6 | 「**插 W9-00 不构成 W9-05 第 5 次顺延**」（台账 §零 / §二-bis 原话，**逐字**） | 本节 + §七 + §二-bis `W9-05` 行 |
| 7 | 「**C9/G24 归属唯一 = W9-10；N1 对 C9 判出的 RED 只作输入证据留痕，W9-00 不代做、不转移编号**」；W9-10 落地前 **N2 人工闸门不得免除**，落地后 N2 降为复核但仍跑一次变异确认自动判据真的会红 | 本节 + §二-bis `W9-10` 行 |

### 6.1 kotlinx 四件映射表（终版 §一.C 原文，文件:行为准）

| 裁决 `文件:行`（准） | 旧编号（附注） | 快照核验结论 |
|---|---|---|
| `JsonFormatCheck.kt:45` | kotlinx 回显透传 | `e.message.take(120)` 直接透传 kotlinx 异常文本（含 `JSON input:` 原文段），KDoc `:21-24`「不回显原文」失实 |
| `JsonFormatCheck.kt:40-43` | F-2 恒真空 when | `when(element){JsonNull->Valid; else->Valid}` 两分支同值，注释声称「再挡一层」实为零挡 |
| `JsonFormatCheck.kt:72` | J8 围栏 / BOM 方向 | `s.lastIndexOf("```")` 即围栏裁切行 |
| `JsonFormatCheckTest.kt:96-98` | `essage` 笔误 / 恒绿断言 | 实为 `invalidReportsLineAndColumn` 三连弱断言（`line>=1` / `column>=1` / `message.isNotEmpty()`），无论实现多错都绿；字面 `essage` 在快照全仓零命中 |

四件排期 = **方案 A（随 W9-00 同车）**，理由：四件全在 `core-model` 同一文件 + 同一测试类，本车本就要同提交改
`EXPECTED_TESTS` 并给每条补可跑真假用例，K4 正好被出口判据覆盖，边际成本最低；选 A 后 **W9-10 不得再列这四件**。

### 6.2 `essage` 收口：规则 + 本次实际分支结果

- 规则（终版 §一.C）：真实工作树 grep 一次 → 存在则顺手修正并入四件同车；不存在则按 N2 口径销号留痕。
- **本次实际 = 分支 2**：主理人在真实工作树 `grep -rn "essage" --include=*.kt` 判定为**W8 误报**；
  CI 席复核 `grep -rnw "essage" --include=*.kt .` = **0 命中**（词边界口径），
  `grep -rn "essage" --include=*.kt .` = 982 行命中但**全是 `ChatMessage` / `errorMessage` / `e.message` /
  `messages` 等正常词的子串**，没有任何独立 `essage` 字面量 ⇒ **「W8 误报，快照核验零命中」销号留痕**。
- 处置：**不新增 typo 修正、不占号、不留悬账**；快照限定见终版 §五（W41 沙箱快照非真实树，本车已补真实树复核）。

### 6.3 裁决 B 六项二选一取值（已在 §二 2.2 登记，不重复写「已完成」）

P0-4 = **(b) 连 UI 带用例删**｜MD-1 = **(a) 扩 `^#{1,6}\s+` + 2 用例**｜MD-2 = **(a) 灰字 URL 尾注**｜
P2 kind = **(a) 禁用态入口 + 原因**｜P2 AUDIO = **(a) 过滤 TEXT/IMAGE**｜
P0-2 / P1-1 降级 reason = **「上屏」**（不写 core-engine 日志；G19 仍 VACUOUS 归 W9-07）。

### 6.4 裁决 D — 新开挂账 `W9-12`（§二-bis 新增行）

- 项 = 「思考四态 / 五档生产可达性（`supportsReasoning` 开关 + `reasoningStyle` 选择器 +
  `strings_advanced.xml` 11 条死字符串引用）」；开立于 W9，状态 `OPEN`；
  来源 = W8 深度审查 v1.1 的 NEW 第 1 项 / P0-a（原编号见台账 §二-bis 该行证据列）。
- **理由（必须写清）**：终版裁决 §三 W9-00 出口判据的「4 个 P0」指《W8源码逐行审查与达标核对》§三 的
  **P0-1..P0-4**，**不含**此项；且 `feature-settings` 无行数（余 18，见 §五.1）⇒ **本车不做**。
- 本车真机由主理人通过**设备侧 `endpoints.json` 注入 `reasoningStyle`** 做**端点可达性**取证；
  但三组预置 `supportsReasoning` 全 false + 无 style 选择器 + UI 只读 ⇒ **UI 不可达**的事实须如实登记，
  🔴 **不得写「思考四态已验收」**。端点可达 ≠ UI 可达 ≠ 四态验收。

### 6.5 裁决 E / F / C + D6 文本订正落笔位置

- 裁决 E（`essage` 收口分支 2）：§六 6.2 已落笔，台账不新增行、不留悬账。
- 裁决 F（身份口径 + 知识库 §5 回写订正）：§五.2 已落笔（本车不改共享库）。
- 裁决 C（行数口径订正，主理人订正方案席风险②）：§五.1 已落笔。
- **D6 判据文本订正**（写死，不改历史 `docs/analysis/w8-solution.md:589`）：
  `supportsVision=false` ⇒ **保留禁用态入口 + 无障碍原因**（**不是**「attach 按钮不渲染」）；
  `true` ⇒ 可点。D7 = 200×120 左红右蓝真图 + `reasoning.enabled=false` + `max_tokens≥512` ⇒ 命中双色
  （**禁用 1×1 样本**）；两态都需真机截图 + 设备侧日志落盘；只改 `capabilities()` 不点 UI ⇒ ⛔vacuous。
  并入 §五.5 与台账 §一 `W3-07` 行证据列（P1-6 三向对账）。

### 6.6 留到下一车的显式留痕（四要素齐：编号 + 裁决理由 + 承接目标 + 写入位置）

| 编号 | 裁决理由 | 承接目标 | 写入位置 |
|---|---|---|---|
| **W9-01**（压缩量纲统一） | 强制执行序 2「正确性主线第一件事」；四外审同一缺陷**禁拆多条**；插 W9-00 是插入半波，不改变其地位 | 下一车（序 2）开波第一件事，按 W8-plan §八.1 D9 四边界预登记 | 本表 + §七 + 台账 §二-bis `W9-01` 行（仍 `OPEN`，不删不改） |
| **W9-05**（journal / FreezeClock） | 强制执行序 3「必须开波」；老化五波，**第 5 次顺延 = 台账 §零 批判的病理复发** | 下一车必须开波，不得再顺延 | 本表第 6 条逐字文本 + §七 + 台账 §二-bis `W9-05` 行（仍 `OPEN`） |
| **W9-10**（守卫与口径专项） | 裁决①锁死；含 C9/G24、G20 第四判据、G10 尾换行判红、schema 白名单守卫测试；每个新守卫必同步 selftest case | 序 4 自动判据收口；落地前 N2 人工闸门不得免除 | 本表第 7 条 + §七 + 台账 §二-bis `W9-10` 行（仍 `OPEN`） |
| W9-07 / W9-11 | 终版 §一.A/B 三项 / 一项改号，剩余范围不变、按主线排期继续 | 跟随主线（序 5） | 台账 §二-bis `W9-07` / `W9-11` 行证据列（本表 1、2） |

**老号 `CARRIED` 不删行**（删行 G20 判红）；`docs/notes/` 内 `W*-*` 是历史输入，不参与派单、不作为状态来源。

---

## 七、下一车指引

1. **W9-01 仍是正确性主线第一件事**：四外审同一缺陷**禁拆多条**；W9-00 是插入半波，不改变它的地位，
   也不许把它拆散。起手读：本文件 → `w9-plan.md` §八 → 台账 §二-bis → `w8-review-triage.md` §九。
2. **W9-05 必须开波**：journal / FreezeClock 老化五波，第 5 次顺延即病理复发；
   **插 W9-00 不构成 W9-05 第 5 次顺延**（§六 第 6 条逐字）。
3. **W9-10 守卫与口径专项**：C9/G24 归属唯一；roadmap 回填天然与 `arch-guard.sh` 代码同车（满足 B6）；
   每新增守卫必须同步 selftest 变异 case，且先确认调用点已落盘再写正则（提前写会扫空集 ⇒ G21 判红）。
4. **行数触顶先靠下沉，不许调上限**：优先纯逻辑下沉 `core-model`（余 668）/ `core-engine`（余 1423）
   + 删假入口净减；`LINE_CAPS` / `TOTAL_LINE_CAP` / `VACUOUS_BASELINE` 一律不动；
   W8 的 +100 一次性例外**不构成先例**（§五.1 主理人订正方案席风险②）。
5. **基线纪律**：`EXPECTED_TESTS` 现值以 `build.yml:132` 为唯一真值源（主理人在最终集成提交一次算清一次改）；
   任何增删 @Test 必须同提交改该常量，且新分支第一件事核对 `on.push.branches` 含该分支
   （不在列表 ⇒ 推送 0 run，看起来和「构建通过」一模一样）。
6. **本车未做、明确不做的**（防被误读成漏项）：MCP 真机联调（`usesCleartextTraffic=false` 平台阻塞）、
   设备侧 1M 解锁（双内核阻断）、W9-01/02/03/04/06/07/08/09/11 的任何实现、任何压缩结构改动、
   任何新 `arch-guard` 守卫或 selftest 变异 case。阻塞项按矩阵预登记如实标 ⛔ / ⛔vacuous。
