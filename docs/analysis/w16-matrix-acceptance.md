# W16 验收物 — 多免费模型 × 多上下文档位 × 全功能面真机验收

> 波次：W16（2026-11-11）。基线 `6e695d5`（W15 回填，`main`）。**现役仓 `harness-v2/`**；旧仓 `Android-Agent/` 已收尾，仅作历史参照，本波不读其挂账与交接。
> 判定四态 ✅ / ⚠️ / ⛔ / ⛔vacuous。**「未触发」≠「已验证通过」；「0 命中」≠「通过」；「测试全绿」≠「口径被验证」；PC 侧结论不得写成设备侧可用；1M 读数 ≠ 1M 真压。**
> 本文件是**仓内脱敏验收物**：不写凭据、不写本机路径（用 `<workspace>`）、不写设备序列号、不写端点标识（端点以 `M1..M6` 代号 / 不可逆指纹出现）、不写未打码账号字段、不写会话历史。
> 端点凭据**只在设备侧**；本单 / 工具 schema / 工具结果 / 日志 / 截图 / 交接 / 记忆写入**零凭据字面量**。
> 原始取证（截图 / 日志 / 脚本）留在 `<取证目录>`，按第 5 节制品清单只交付脱敏派生件。
> 本波止于交接，交由外部团队审查；**不写「建议下一链做什么」**。

---

## 一、本次任务「做什么 / 明确不做」（范围收敛与裁决）

### 1.1 读取依据（逐条可回溯）

| 来源 | 读到的事实 | 对本波的约束 |
|---|---|---|
| 项目长期记忆 `MEMORY.md` / 工作区日志 | 现役仓 `harness-v2`；W15 tip `0dd1aee` + `6e695d5`，零 `.kt` 改动、`@Test` 恒 799、ag27 / selftest30；W6 字符硬预算（floor 1.8 / ceiling 6.0）；W11 三判据（图片当量不进 `chars/token` 标定分子、`message.id` 不进 wire、usage 四态 + `cacheHitRatio` 分母）；1M 分母 = `min(档位, 端点声明窗口) − maxOutputTokens`；GLM-4.7 写入门槛；adb `-P 15037` + 唤醒三步法 + `reverse` 与后续命令同条调用 + 审批按钮动态取 bounds；中文题面无法注入真机 | 复用既有判据，不重复造门禁；端点凭据只在设备侧 |
| 上一波交接 `docs/handoff-20261110-wave15.md` | W15 = 同组交叉格补跑 + 外审分歧裁决落账订正波；收敛结论 5 条（用户指令面优先 / 已命中触发信号只做最低限度纯文档缓解 / 四项用户裁决不代做 / 五处事实分歧收敛 / 外审排期整包顺延）；端点维度 ✅、档位维度 ⛔vacuous（128K+ 六格 `window label not found` 触 3 轮止损）、整体 ⚠️；`W15-01..14` 全 `OPEN`；`W9-05` 第 10 次顺延（两口径 10/11 并列） | 老号不删、不写已核销、不重判 `CLOSED`；档位维度是本波待补的判据缺口 |
| 台账 `docs/06-deferred-ledger.md`（**挂账唯一真值源**） | §二-quater `W14-01..11`、§二-quinquies `W15-01..14` 全 `OPEN`，每行带触发信号 + 缓解动作；`W9-05` 老行代数 7 / 证据列 8 / `W14-04` 9 / `W15-04` 10 并存；§一 `CLOSED` 行证据必须非空；编号唯一来源 = §一 表格行 | 老号全 `OPEN` 原样保留；数字订正以实跑值为准；`W9-05` 本波第 11 次顺延（10+1；逐波口径 12 并列待裁决） |
| 四份审查人原始报告（2026-11-10 / 11-11，仓外） | 《W11-W15深度审查与W16路线建议》判五波账实相符、零虚报，建议不再加矩阵波、四项用户裁决先拍板、G1 重取 APK 字节销疑；《W15同组交叉格波深度审查与W16排期裁决》判 W16 不再开新面、第 0 车还债 + 派生件收口；《W12-W15审查与挂账分析》判 OPEN 63 / CLOSED 34、N-1 行为面断层、`secret-scan.yml` 显示假象自纠；《W15最终态复核与W16开工裁决》判零增量（tip `6e695d52f`）、G20 扫描面结构性盲区、订正「独立复算分母」为「仅 1M 档口径未标注」 | 四份一致要求还债排期；与本单「继续跑矩阵 + 推 CI」构成 W14/W15 已裁定过的同一张力；事实分歧逐条裁决，排期整包顺延 |
| 外部仓库参考登记册《外部仓库可参考内容详录.md》 | 六仓内容登记册（Apache-2.0 / MIT / AGPL / 无 LICENSE 混合），可取的结构思想：必红变异用例、代码检查 / 设备行为 / 真实模型业务结果分开报告、范围冻结 | **零代码搬运、只取结构思想**；本波 0 `.kt` ⇒ 仅把「必红变异样本」思想用于 `CTRL-0` 与派生件唯一性断言 |
| 用户本单（2026-11-11） | 唯一目标 = 多个免费模型 × 不同上下文（直至 1M）× 多模态输入 / 工作空间文件读写 / 记忆 / **对话框 skill 引用手动触发** / MCP 等 + 手动与自动上下文压缩；先本地 UI 及功能测试 + adb 调试，真机通过后推仓 + CI；密钥只留真机；读四份外审并裁决分歧、**不改方案大体方向**；按收敛结论实施；真机 adb + 截图回灌 + 边测边改；编译失败四分类；每次推送留可审计 commit 与 run 链接；不 force-push / rewrite；止于交接交外部审查 | **唯一目标以此为准**；不改序、不代做用户裁决 |
| 现役树实跑（主理人自检 + 方案席复核，本波） | 严格 `^\s*@Test\b` = **799** ↔ `build.yml:132 '799'`；`arch-guard.sh:1329` `find docs -maxdepth 1 -name 'handoff-*.md'` + `:1337` `grep -ohE 'W[0-9]+-[0-9]+'` ⇒ G20 **只扫 handoff**；`EndpointAdvancedValidation.kt:68` = `"parallel_tool_calls",`、全树字面 `"parallel_"` **零命中**；`SettingsScreen.kt:315` `presets = [32_000,128_000,192_000,256_000,512_000,1_000_000]`；`ChatContextWindowTest.kt:43/50` 32K=27_904 / 1M=995_904（`contextLength=1_048_576` 声明、`verified=1_000_000` 档位）；`secret-scan.yml:19` = `branches: [main]`；`AndroidManifest.xml:25` `usesCleartextTraffic="false"`；全树 `W16`/`wave16` **零命中** ⇒ 本波为 W16 首波 | 一切派生量以实跑值为准；文档不写死 G20 数字；外审处方先回源核实再采纳 |
| 设备侧自检（主理人只读探测，本波） | 设备在线且已唤醒：`SCREEN_STATE_ON` + `INTERACTIVE_STATE_AWAKE`；`screencap` 判活链路可用；**6 个免费端点已预置**（`M1..M6`，声明窗口含 1M / 512K / 256K / 262144，工具位全开、部分端点含 IMAGE 模态），凭据在设备 `secrets` 内、仓内零凭据；G15b `usesCleartextTraffic="false"` 仍 OK；编译工具（JDK21 / SDK / `localbuild.sh --env` 注入）齐备 | 真机矩阵按既有端点行使，**不重新注入凭据、不为测试放开明文** |

### 1.2 裁决分歧与收敛结论

> 本节 = 方案分析席 Phase 1 只读报告 + 主理人复核汇编。派单核实：`git rev-parse --short HEAD` = `6e695d5`、`git status --short` 仅 3 个未跟踪日析草稿 ⇒ 该席**只读未写仓内文件**；其六项关键论断已由主理人逐条 grep 现役树复核（见 §1.1 最后两行）。

**分歧三面**：

- **事实层五处分歧**：严格 `@Test` 796 / 798 / 799 / 800；G20 实跑 ID 数 74 / 88 / 91 / 109（方案席另得 122）；`W9-05` 顺延代数 7 / 8 / 9 / 10(11)；「≥3 免费端点」✅ / ⚠️；`P3-2a` 笔误键撤销 vs `P2-3` 运行时兜底悬置（并牵出「11 条总裁裁决落空 6 条」与 Android 线「5+1」之争）。
- **排期层张力**：四份外审一致要求「W16 不再开新面 / 不再加矩阵波，按 `W15-01` 执行还债第 0 车」；用户本单延续多模型 × 多上下文矩阵 + 全功能面 + 真机 + 推 CI，并明示**不改方案大体方向**。
- **外审内部互相订正**：分母复算口径被收窄、`secret-scan` 触发分支「显示假象」自纠、G20 扫描面盲区被点破，另有一处外审前提被现役树证伪（见下表第 4 条）。

**收敛结论（本波唯一目标以此为准）**：

1. **顺序沿用 W14 / W15 已两波裁定的「用户指令面优先」**（`handoff-20261109-wave14.md:22-25` / `w15-matrix-acceptance.md §1.2(1)`）：主干照用户唯一目标跑真机矩阵 + 推 CI；外审还债三车（第 0 车还债 + 派生件收口 / 第 1 车把证据接成设计 / 第 2 车拆分先行再动 `.kt`）**不取消、不推翻、不写已核销，整包挂账顺延**。这是「已裁定沿用」，不是本波新裁决，不代做。
2. **老号全 `OPEN` 原样保留**：`W14-01..11` / `W15-01..14` / `W9-05` 及 `W3-05` CARRIED 链不删、不改状态、不重判 `CLOSED`、不写「已核销」；`W9-04` `CLOSED` 维持；`W11-06..10` / `W13-01` 等漏号**不占本波新号**（漏号若执行须补原号，见 §1.4 三条硬约束）。
3. **「不改方案大体方向」守住**：零 `.kt` 改动预期、零新增 arch-guard 守卫、零新增 selftest case、零 CI 配置改动；不动 `EXPECTED_TESTS 799` / `LINE_CAPS` / `TOTAL_LINE_CAP` / `VACUOUS_BASELINE`；图片算进预算但**不进 `PromptPrefix` 指纹**；不为凑缓存加长 system；PC 侧结论不升级为设备侧可用；不 force-push、不 rewrite history。⚠️ 「零 `.kt`」= 不改应用 Kotlin 源码；链内循环的「改」落在**取证驱动 / 档位切换脚本 / 交付工具链**（非 `.kt`，不占迭代轮次），故 G1 只发生一次编译、全程复用其产物。
4. **四项用户裁决不代做、只留痕**：① `W9-05` 开波 / 显式「永不实现」改状态 / 统一代数口径；②「≥3 端点」二选一（接受 ⚠️ 为终局 vs 继续补档位维度）；③ §1.5 占位符口径确认（外审 P1-4 建议开工前由用户确认，本波**不写成用户已确认**）；④ `P2-3` 二选一。本波执行多档位矩阵属「**执行**」不是「裁决」，是否把 ⚠️ 改判 ✅ 不代写。
5. **判据纪律**：判据自身先被验证（known-good 对照格 `CTRL-0`）；「未触发」≠「已验证通过」；「0 命中」≠「通过」；「判据写进方案」≠「判据被执行」；复用旧日志尾标 `stale=true`；**1M 读数 ≠ 1M 真压**；推断一律标【推断】，handoff 不把推断写成事实。
6. **密钥 / 隐私**：端点凭据只在设备侧；所有产出零凭据字面量 / 零绝对路径 / 零序列号 / 零端点标识 / 零未打码账号字段；截图按 L2 级别脱敏回灌。
7. **链内循环与止损**：「改 → 编译 → 真机 → 再看」逐行留证据；失败按四分类（参数错 / 工具名错 / 该调不调 / 调了不收敛）**先排除驱动与环境、后应用**；环境问题不硬跑、不改码硬凑、不把环境归因成代码 bug；触顶如实 ⚠️ 收尾。
8. **推仓 + CI**：每次推送留可审计 commit + run 链接；沿用 W15 已验证手法以**非忽略面产物** `scripts/fixtures/w16-matrix-facts.json` 真实触发 build 五 job；纯 `docs/**` + `*.md` 提交 `0 run`，**不把 0 run 写成「构建再次通过」**；CI 红按四分类复归因 → 复编译 → 复真机 → 再推，直至全绿或触顶。
9. **收尾契约**：1→5 各步四态 + 证据路径（CI 绿附 run 链接）+ 链内迭代记录 + 明确未做（逐条带 `W16-` 号）+ OPEN 挂账（触发信号 + 缓解动作 + 号）+ 文档回写 diff + 脱敏制品清单（不含会话历史 / 凭据 / 端点标识 /「建议下一链做什么」）；**本次任务停于此，交外部团队审查**。

**五处事实分歧逐条裁决**：

| # | 分歧 | 收敛结论（唯一真值） | 一手依据 | 本波处置 |
|---|---|---|---|---|
| 1 | `P2-3` / `P3-2a`：白名单笔误键撤销 vs 运行时兜底悬置；「11 条总裁裁决落空 6 条」与 Android 线「5+1」之争 | 沿用 W15 §1.2(1)：以 README v2 勘误为准，`P3-2a`「白名单笔误键」**撤销**（blob 内容寻址哈希反推定谳：完整键名成立）；`P2-3`「运行时兜底不查白名单、防线不对称」是**独立结构论、维持悬置**；上轮 11 条总裁裁决实际落空 **6 条**，Android 线「5+1」不成立。本波另核：`OpenAiCompatibleAdapter.applyCustomProtocol` 与保存链的白名单排他仍不对称 ⇒ `P2-3` 结构面仍 live | W15 §1.2(1) 裁决；《README线深度审查供件》v2 勘误；现役树 `EndpointAdvancedValidation.kt:68` / `applyCustomProtocol` | 「明确不做」**不把 `P2-3` 写成已撤销**；与 N-1 行为面写成两句（见下表第 4 条）；二选一裁决留给用户 / 外部 |
| 2 | `@Test` 796 / 798 / 799 / 800 | **799**（严格 `^\s*@Test\b`）↔ `build.yml:132` 同源；800 = 宽松 grep 把 KDoc 内反引号 `@Test` 计入的离群值；798 = W11 第 0 车历史快照；`796` /「8 条」是台账 `W9-04` 行失实孤例，W15 已订正为「以 `build.yml:132` / 实跑值为准，不写死数」 | 主理人 + 方案席双路实算 799（本波复核见 §1.1）；W15 订正留痕 | 沿用、不重判、不动 `EXPECTED_TESTS` |
| 3 | `W9-05` 顺延代数 7 / 8 / 9 / 10(11) | 四个波次快照被写进同一真值源的不同行 / 列（§一老行 7 / 证据列 8 / `W14-04` 9 / `W15-04` 10），老行从未更新，**不是四处互斥断言**；按 `W15-04` 现行口径 **W16 = 第 11 次顺延**（逐波重数含 W13 静默顺延则为 12，两口径并列待裁决）；§一 老行 7 / 证据列 8 **不改**（改老行需单独裁决） | 台账 §一 `W3-05` CARRIED 链 + `W14-04` / `W15-04`；`w15-matrix-acceptance.md §1.2(3)` | 状态 `OPEN`、不关号、不软化、不写「永不实现」；本波如实登记第 11 次顺延（两口径并列） |
| 4 | 「≥3 免费端点」✅ vs ⚠️ | **⚠️ 部分达标 / 分维度判**：W15 端点维度 ✅（32K 档三端点同组 converged）、档位维度 ⛔vacuous（128K+ 六格 `window label not found`）、整体 ⚠️。「接受 ⚠️ 为终局」或「继续补档位维度」= **用户裁决，不代做** | `w15-matrix-facts.json` + W15 验收物 §3.4；外审《W15最终态复核》§2.3 复算 32K=27.9K / 128K=123.9K 成立 | 本波按用户唯一目标**执行**多档位矩阵属执行不是裁决；是否把 ⚠️ 改判 ✅ 不代写 |
| 5 | G20 实跑 ID 数 74 / 88 / 91 / 109 / 122 | 皆为**扫描面 × 快照时点**的派生量：只扫 `docs/handoff-*.md` vs 全部 `docs/*.md` vs 台账表格行 vs 递归 `docs/**/*.md`（122 > 109【推断】来自递归子目录 + 3 个未跟踪日析草稿）；`74 + 11(W14-01..11) = 88` 机制自洽 ⇒ **守卫没错，文档数字磨损**。`arch-guard.sh:1329/1337` 实锤 G20 **只扫 handoff** | W15 五处分歧裁决(5)；方案席 `arch-guard.sh:1329/1337` + 三口径实算 | 文档一律写「G20 `PASS`，ID 数以 `arch-guard` 实跑输出为准」，**不写死数、不写 `X → Y` 增量句**；唯一常量只有 `EXPECTED_TESTS` / `TOTAL_LINE_CAP` / `VACUOUS_BASELINE` |

**外审内部互相订正（4 条，第 4 条为本波新增回源证伪）**：

| # | 订正 | 收敛结论 | 处置 |
|---|---|---|---|
| (a) | 《W15最终态复核》§2.3 订正《W15同组交叉格波审查》「独立复算分母判据整体不成立」 | **采纳窄结论**：32K / 128K 两档复算成立且工作正常（128K 读到 27.9K 恰是「正确判出档位没切成功」），**仅 1M 档**因 `endpoint_declared_window=1048576`（声明 2^20）与档位 `1000000`（10^6）双口径未标注而不可独立复算。现役 `ChatContextWindowTest.kt:43/46/50/53` 与 `SettingsScreen.kt:315` 实证两个来源 | 派生件补 `window_tokens` 数值列 + 1M 双口径注释即可；**32K / 128K 复算不重做** |
| (b) | 《W12-W15挂账分析》§三 自纠：`secret-scan.yml:19` 被工具链显示成 `ain]` 是「显示假象」 | **采纳自纠**：主理人 / 方案席 grep 现役 `:19` 确为 `branches: [main]`，字节级 `[m` 被显示层吞掉；文件 / CI / 解析器三方正常。教训入档：显示内容与行为证据冲突 ⇒ 先做字节级对拍再定性 | 纯文档留痕；**不据此开新挂账、不改 CI 配置** |
| (c) | 《W15最终态复核》§2.2：G20 扫描面结构性盲区 | **采纳**：守卫只校验「handoff 提到的 ID 有没有台账行」，**不校验「外审 P0 必落独立台账行」**；`W11-06..10` / `W13-01` 只出现在验收物正文 / 承接行文字里，从不作 handoff 独立 ID ⇒ 连续第五波漏号而 `OK=27 / G20 OK` 全绿是**机制原因，不是守卫算错** | **必须挂账顺延**：补盲区需改守卫脚本 + 先批量补历史悬空行再改守卫（外审明写「不得先改守卫再慢慢补，会撞 G21 / 直接判红」），属新增守卫 + 改真值源结构，与「零新增守卫 / 零 `.kt`」冲突；本波只留痕 |
| (d) | **【本波新增，两份外审矛盾 ⇒ 回源判第三份】**《W12-W15挂账分析》§四 4.1 / §5.2 主张现役 `EndpointAdvancedValidation.kt:68` 仍有 `"parallel_"` 笔误键、建议开 `W16-01` 修 N-1（≤10 行 + 2 判据）；W15 §1.2 已裁定 `P3-2a` 撤销 | 主理人 + 方案席双路回现役树：`:68` = `"parallel_tool_calls",`（完整键），`ALLOWED_EXTRA_BODY_KEYS` 含完整键，全树字面 `"parallel_"` **零命中**，`validateAdvanced` 用 `!in` 精确匹配 ⇒ `parallel_tool_calls=true` **不被误拒**。⇒ **W15 裁决成立；该外审「现役仍有笔误键」前提在现役树不成立【时点差：读的是它那一版 / 旧 blob】**。**不采纳**「开 `W16-01` 修 N-1」处方（前提已消失，改了即为凑行数造假代码）；但 `P2-3` 运行时不 enforcing 白名单排他**仍成立**，维持悬置 | 纯文档订正可吸收：把「N-1 定性撤销」与「`P2-3` 行为面」写成两句、防再被合并叙述覆盖；**不新开 `W16-01` 修 N-1**；`P2-3` 二选一用户裁决不代做 |

**处置分类汇总**：

- **用户裁决、不代做、只留痕（四项）**：`W9-05` / 「≥3 端点」二选一 / §1.5 占位符口径 / `P2-3` 二选一。
- **纯文档订正，可吸收（零 `.kt` / 零新增守卫 / 不改真值源结构）**：799 沿用；G20 不写死数字；`W9-05` 第 11 次顺延两口径留痕；1M 双口径注释 + `window_tokens` 列；(a)(b)(d) 三条订正留痕；顺带吸收《W11-W15》W12-NEW-1「G1 重取并记 APK 字节数销疑」（本波 G1 真实重取即自然销疑，零成本、不改码）；N-1 与 `P2-3` 拆分。
- **必须挂账顺延（整包，`OPEN` 原样）**：外审第 0 / 1 / 2 车全部（六行漏号补登、checklist 三硬规则、G20 盲区、W15 派生件收口、文档零头、`W15-10` 合并设计、拆分三方 + 五条快修 + 安全波收口）。**不取消、不推翻、不写已核销。**

### 1.3 做什么（唯一目标，逐条带出口判据）

> **唯一目标（一句话）**：在设备侧预置的多个免费端点（`M1..M6`）上，以**同一组实验**补跑「≥3 端点 × 多档位（32K / 128K / 512K / 1M 读数）」交叉格，并逐项取全**多模态输入 / 工作空间文件读写 / 记忆 / 对话框 skill 引用手动触发 / MCP / 手动与自动上下文压缩**的**双向判据**；本地四闸门 + adb 调试通过后推仓取 CI 全绿、产收尾契约，**止于交接交外部审查**。

| # | 事项 | 出口判据（含空命中语义） |
|---|---|---|
| A1 | 本地四闸门 + 计数对账 | G1 `:app:assembleDebug` `rc=0` 并**记录 APK 字节数 + mtime**（销 W12-NEW-1 疑点）；G2 `tests=799,0,0,0` 且 `TEST-*.xml` mtime **全为本轮**；G3 `result=PASS ok=27 vacuous=1 violation=0 fault=0`、**G20 以实跑输出为准不写死数**；G4 `30/30`；严格 `@Test` 799 ↔ `build.yml:132`。编译 `rc=0` ⇒ 四分类「**未触发**」，如实写、不虚构归因案例 |
| A2 | 设备侧前置自检 + **判据自身先被验证** | `SCREEN_STATE_ON` + `INTERACTIVE_STATE_AWAKE`；`screencap` 非黑像素比 >2% 且颜色桶 >3（全黑不算存活）；端点预置、凭据仅设备侧、仓内零凭据；G15b `usesCleartextTraffic="false"` 仍 OK；`in_app()` 前置断言。**先跑 known-good 对照格 `CTRL-0`**：有效 bounds / 截图非全黑 / 取到 usage / `in_app()` / 日志尾为**本批新值**，五项全绿才开批；任一不满足即停批并按四分类归因**取证驱动**。对照格只证判据可行，**不是交叉格结论** |
| A3 | **同组交叉格**：≥3 端点 × 多档位（32K / 128K / 512K / 1M 读数） | 同一 `group_id` / `driver_version` / 同一 ASCII 短题面 / 每题前点「新对话」/ 同一采集窗口 / 同一列集；每格落**六列** `window` / `window_tokens` / `endpoint_declared_window` / `failure_class` / `tail_is_fresh` / `group_id`；分母 `min(档位, 端点声明窗口) − maxOutputTokens` **可从派生件独立复算**（审查者不需回源码猜 `32K→32000` 还是 `32768`）；1M 档显式注释 `declared=2^20 / tier=10^6 / effective=min(...)`；`cell` 唯一、`safe_shot` 唯一 |
| A4 | **1M 读数与真压分离** | 回读「当前 1M」+ 声明 1M 端点读到 `995.9K` 类分母 ⇒ **仅证明分母生效**；未灌入接近 1M 量级即如实 ⛔（`W9-11` 双阻断：L3 字符闸 + `RoundHeadGate 400k`），`W14-05` / `W15-05` / `W9-11` **不关号**；不把读数写成真压、不把 PC 侧命中写成设备侧可用 |
| A5 | 全功能面逐项**双向**判据 | 七项各取两向证据：① 多模态输入（真图 200×120、**禁 1×1**、语义命中 + SAF 附件**确被行使**）；② 工作空间文件读写（审批框**动态 bounds** + 写前不存在 → 写后逐字含探针串）；③ 记忆（`snapshot_before` / `snapshot_after` **数值** + 探针 token 匹配）；④ **对话框 skill 引用手动触发**（本波新增面，判据经主理人回源订正：现役 `EventReducer.kt:82` 明写「技能系统 UI 未接」、全仓 `SkillChip` / `@skill` / `skillRef` / `SkillPicker` 零命中 ⇒ **无用户侧引用控件**；唯一可行使路径 = 用户在对话框**手动输入明确要求加载某 skill 的 ASCII 英文题面**（用户侧发起、非模型自主）触发模型调 `skill_load`；判据 = 工具轨迹可观测 + skill body 片段可观测加载 + 请求收敛，另跑「不提 skill」同结构对照批取反向证据；与 W15 的**模型侧** `skill_load` 判据分开写，找不到控件不写成应用功能缺陷）；⑤ MCP（G15b 在前 + 明文 `transport_error` 如实 ⛔blocked）；⑥ 手动压缩（确认框 + `dropped=N>0` + 历史条数下降 + 下一请求 `cached=0`）；⑦ 自动压缩（真触发 `auto_compact dropped=N`；`noop below_trigger` / `already_compressed` **不算**）。未触发写 ⛔ / ⛔vacuous，**不引用 W12 / W14 / W15 结论冒充本波通过** |
| A6 | 链内循环与止损 | 每轮留一行「改 → 编译 → 真机 → 再看」证据；3 轮 / 3 次推送 / 2 次 CI 红触顶即如实 ⚠️ 收尾、写明最后一条 run 与未归因项；**采集批不计入循环轮次**（防 W14 handoff §五「3 轮 / ITER-4」漂移）；取证驱动修复不占轮次 |
| A7 | 推仓 + CI 全绿 | 每次推送 = 一个可审计 commit + 一个 run 链接；Build 五 job 全 `success` + 独立 Secret scan `success`；一次 push 触发面 = 2 workflow / 2 run / 6 job（非忽略面产物真实触发）；CI 红按四分类复归因 → 复编译 → 复真机 → 再推直至全绿或触顶；**不 force-push、不 rewrite history** |
| A8 | 收尾契约 | 1→5 各步四态 + 证据路径（CI 绿附 run 链接）+ 链内迭代记录 + 明确未做（逐条带 `W16-` 号）+ OPEN 挂账（触发信号 + 缓解动作 + 号）+ 文档回写 diff + 脱敏制品清单；【推断】逐处标注；**不写「建议下一链做什么」**；止于交接交外部审查 |

### 1.4 明确不做（逐条带 `W16-` 挂账号前缀）

> 老号 `W14-01..11` / `W15-01..14` / `W9-05` 及 `W3-05` 承接链全 `OPEN` 原样保留，不删、不改状态、不重判 `CLOSED`、不写「已核销」；`W9-04` `CLOSED` 维持。下表 `W16-` 号是**本波范围声明**（告诉外部审查「本波没做什么」），**不是老号承接**：每行显式「参照老号 `Wyy-XX`，该老号保持 `OPEN` 原样」，**不得**写成 `CARRIED` / 承接 / 替代。

| 挂账号 | 明确不做 | 原因 / 参照老号（老号保持 `OPEN`，非承接） |
|---|---|---|
| `W16-01` | 外审第 0 车逐条落号：`W13-01` + `W11-06..10` 六行补登、checklist 三硬规则、G20 盲区、W15 派生件收口、文档零头 | 改真值源结构 + 新增守卫，独立波；参照 `W15-01` / `W14-01` |
| `W16-02` | 安全波收口（消重复扫描 job / 安装脚本 pin + sha256 / allowlist 精确化 + 真 key 仍判红变异 / 全历史首跑 / 签名端到端） | 改 CI 配置，独立波；参照 `W15-02` / `W14-02` |
| `W16-03` | 拆分三方（`core-context` 余 4 / `feature-chat` 余 12 / `feature-settings` 余 8）、四态生产读者、分母族归一、jsonMode × tools、五条 ≤30 行快修 | 需改 `.kt` + 红态用例；参照 `W15-03` / `W14-03` |
| `W16-04` | `W9-05` journal / FreezeClock 开波或改状态（第 11 次顺延，两口径并列） | 用户未显式裁决；不关号、不软化、不代裁决；§一老行 7 / 证据列 8 不改 |
| `W16-05` | 设备侧 1M **真压** | `W9-11` 双阻断；本波只做 1M 读数；参照 `W15-05` / `W14-05` |
| `W16-06` | MCP 明文联调 / 放开 `usesCleartextTraffic` | 平台阻断 ⛔，不为测试放开；参照 `W15-06` / `W14-06` / `W5-03` |
| `W16-07` | git 历史清理 / force-push / rewrite history | 不可逆，须用户二选一；本单明禁 |
| `W16-08` | 音频输出 / `core-device` 独立波 / 图片预算四条 / 为凑缓存加长 system | 图片算进预算但不进 `PromptPrefix` 指纹；参照 `W15-08` / `W14-08` |
| `W16-09` | 把 PC 侧结论升级为「设备侧可用」 | `AGENTS.md` §二.3 四态纪律 |
| `W16-10` | `W15-10` / `W14-10` 合并设计（前置熔断路径压缩试算入口 + `ChatViewModel.kt:395` 结算时机 + `W9-01` 量纲） | 需改 `.kt`；外审 P1-1 已证自动压缩在该路径**结构不可达**，本波只留裁决痕 |
| `W16-11` | 本波仍未取全的功能项冒充「已验收」 | 四态如实；不写 `W14-11` / `W15-11` 已核销 |
| `W16-12` | 改 `.kt` / 新增 arch-guard 守卫 / 动 `EXPECTED_TESTS` / 调 `LINE_CAPS` · `TOTAL_LINE_CAP` · `VACUOUS_BASELINE` / 改 CI 配置 | 单波归因 + G21 棘轮（空集守卫判红） |
| `W16-13` | 代做四项用户裁决（`W9-05` / 「≥3 端点」二选一 / §1.5 占位符口径 / `P2-3` 二选一） | 只留痕 |
| `W16-14` | 文档回写差异：W14 验收物重复 `### 5.5` 标题与 §4.3 占位段、§5.1 步 3 过度声明、W15 §5.5「将包含」未来时 | 历史制品「回填不篡改」保留并记差异；删改须单独裁决 |
| `W16-15` | **【本波新登记】**不重犯 W15 派生件硬伤：`cell` 唯一 / `safe_shot` 唯一 / 补 `window_tokens` 数值列 / 1M 双口径注释 / `memory.backward.snapshot_before` / `tail_is_fresh` 升级为「本批新事件」而非仅时间戳新 | 四项硬伤已由方案席 + 主理人逐条核实（§1.1） |
| `W16-16` | **【本波新登记】**不以 `W16-XX` 承接任何老号或漏号；漏号若执行须**补原号**；老号全 `OPEN` 原样保留 | 见下方三条硬约束 |
| `W16-17` | **【本波新登记】**不把「1M 读数」写成「1M 真压」、不把档位读数失败（驱动 `window label not found`）归因成应用代码 bug、不把「对话框 skill 引用手动触发」简写成 W15 的 `skill_load` 工具调用 | 四分类 + 功能面口径 |

**关于「不要新开 `W16-` 号去承接同一批漏号」——主理人裁决（采纳方案席判断）**：用户逐字要求「明确未做逐条带 `<挂账号前缀>` 挂账号」，W14 / W15 两波已建立「每波带当波前缀」模板，改前缀口径 = 改方案大体方向，不代做；同时采纳两份外审「不换号承接」的实质关切，给 `W16-` 号加三条硬约束：① 定性为**本波范围声明**、每行显式标注参照老号且老号 `OPEN` 原样，不得写 `CARRIED` / 承接 / 替代；② **不得**用 `W16-01..06` 之类新号去登记 `W13-01` / `W11-06..10` 六行漏号（漏号若执行须直接补原号）；③ 明确不做清单与外审第 0 车要求的「六行独立台账行」是**两种不同性质的行**，不可混为一谈。理由：外审反对的落点是「还债义务被假接住」与「台账膨胀」（《W15最终态复核》§2.4 指出 `W15-01/02/03/10` 全是 `W14-01/02/03/10` 同义换号、两波共 25 行挂账而实际技术债未动）；本席实跑证实 G20 **只校验 handoff ID 存在性**，换号不增加任何判据灵敏度，纯属装饰 ⇒ 用显式标注杜绝「用 `W16-` 号冒充已跟踪」的误读。本波另设 `W16-15/16/17` 三项真正属于 W16 的新登记，使前缀不只承载老号同义内容。

### 1.5 占位符收敛取值（**裁决留痕、非既有事实**）

> 本单以 `<…>` 形式给出占位符但**未给字面值**。按链内止损纪律收敛为下列保守值，写在这里供外部审查复核；若与用户本意不符，**以本单原文为准**（本波不擅自放宽）。
> ⚠️ 外审 P1-4 建议「开工前应由用户确认一次口径」—— 本波**不写成用户已确认**，只留痕；占位符口径本身是四项用户裁决之一。

| 占位符 | 收敛取值 | 理由 |
|---|---|---|
| `<截图回灌级别>` | **L2**（沿用 W14 / W15）：每次迭代回灌「脱敏截图 + 控件树文本判据 + `deviceLogTail` 摘要（≤12 行、先脱敏）」；不回灌整段 logcat、不回灌原始截图、不回灌凭据 / 未打码账号字段 / 端点标识 | 满足「回灌脱敏截图判据与 `deviceLogTail`」且守住隐私红线 |
| `<最大真机迭代>` | **3 轮**（沿用）；**采集批不计入循环轮次**，取证驱动修复不占轮次 | 防 W14 handoff §五「3 轮 / ITER-4」漂移。⚠️ **不因 W15 档位未攻克而放宽到 4 轮**：止损判据是硬闸，放宽 = 为凑结果改判据、违反「不改方案大体方向」；W15 档位失败根因是取证驱动滚动漂移，应在 A2 判据自身验证阶段**前移修驱动**，而非加轮次硬跑 |
| `<最大推送次数>` | **3 次**（沿用） | 每次推送 = 一个可审计 commit + 一个 run 链接 |
| `<最大 CI 红次数>` | **2 次**（沿用） | 第 3 次红即止损，写明最后一条 run 与未归因项 |
| `<挂账号前缀>` | **`W16-`**（**唯一调整项**，不得沿用 `W15-`） | 本波「明确未做 / OPEN 挂账」统一编号，台账随波补行；须遵守 §1.4 三条非承接硬约束 |

**结论：四项沿用、仅前缀改 `W16-`，不调整任何数值。**

### 1.6 五步与四态约定

第 2–5 节按下列 5 步各带四态与证据路径；**CI 绿须附 run 链接**：

| 步 | 名称 | 主要证据 |
|---|---|---|
| 1 | 范围收敛与裁决 | 本文件 §1；方案席 Phase 1 只读报告 + 主理人复核（派单核实 `git status` / `HEAD` 证明只读）；`<取证目录>/evidence/` 侦察摘要 |
| 2 | 本地 UI 与功能闸门 | `g1_assemble.log`（含 APK 字节数 + mtime）/ `g2_fulltest.log` / `g3_archguard.log` / `g4_selftest.log` / `gate_env.txt` |
| 3 | 真机多模型 × 多上下文矩阵 + 全功能面 | 脱敏截图判据 + 控件树文本 + `deviceLogTail` 摘要 + W16 矩阵事实表（六列：`window` / `window_tokens` / `endpoint_declared_window` / `failure_class` / `tail_is_fresh` / `group_id`） |
| 4 | 推送与 CI | commit 短哈希 + run 链接 + 链内迭代记录 |
| 5 | 收尾（未做 / 挂账 / 文档回写 / 制品清单） | `git diff` + 台账 diff + 制品清单 |

四态：**✅ 判据被真实行使且通过**｜**⚠️ 部分 / 有局限（须写明局限）**｜**⛔ 未做或平台 / 内核阻断**｜**⛔vacuous 判据未触发、不能算通过**。
补充纪律：「未触发」≠「已验证通过」；「0 命中」≠「通过」；「测试全绿」≠「口径被验证」；「判据写进方案」≠「判据被执行」；`settled=true` ≠ `converged`；复用旧日志尾必须标 `stale=true`，否则判据恒假绿；PC 侧结论不得写成设备侧可用；**1M 读数 ≠ 1M 真压**。

---

## 二、第 2 步：本地 UI 与功能闸门

### 2.1 四闸门四态（唯一入口 `scripts/localbuild.sh`，env 注入；`arch-guard` / `selftest` 后台跑）

| 闸门 | 命令 | rc | 实测 | 四态 | 证据 |
|---|---|---|---|---|---|
| G1 出包 | `:app:assembleDebug` | 0 | 见 §2.2（**判据自身修复**：UP-TO-DATE → 强制重出包） | ✅（出包判据已真实行使） | `<取证目录>/evidence/g1_assemble.log` + `g1_apk_meta.json`（before）+ `g1_apk_meta_rerun.json`（after）+ `_w16_lead/g1_rerun.log` |
| G2 全量单测 | `scripts/fulltest.sh` | 0 | `tests=799 failures=0 errors=0 skipped=0`；覆盖模块 14（settings include=14）；82 个 `TEST-*.xml`，mtime 最早 `12:45:17` / 最晚 `12:45:18`，`xml_all_fresh=true`、`xml_stale_count=0`、`pass=true`；fulltest 未报句柄锁、重试机制未触发 | ✅ | `g2_fulltest.log` + `g2_xml_mtime_check.json` |
| G3 arch-guard | `scripts/arch-guard.sh`（仓库根） | 0 | `result=PASS ok=27 vacuous=1 violation=0 fault=0`；G20 实跑「88 个 handoff ID 全在台账内，CLOSED 行证据均非空」；G19 唯一 VACUOUS | ✅ | `g3_archguard.log` |
| G4 守卫自证 | `scripts/arch-guard-selftest.sh`（batch） | 0 | `selftest: 30/30 PASS（187s）` | ✅ | `g4_selftest.log` |

计数对账：严格 `^\s*@Test\b` = **799** ↔ `build.yml:132 EXPECTED_TESTS: '799'`（主理人 + CI 席双路实跑一致）。

### 2.2 G1 判据自身修复与外审 W12-NEW-1 响应（本波实质动作，**非应用 `.kt` 改动**）

外审《W11-W15 深度审查》W12-NEW-1（MEDIUM）指出：W12 handoff 记 G1 APK `70,309,888 B` 与 W11 完全相同 ⇒ 高度疑似 W12 的 G1 证据是 W11 旧日志复用；缓释是 W12/W13 代码都经 CI `Assemble Debug` 独立编译 success（实质风险为零），**建议下一波 G1 重取并记录 APK 字节数即可销疑，成本为零**。

本波执行与实测：

1. 开发席首轮 G1 `rc=0`，但日志末行 `270 actionable tasks: 270 up-to-date`、`:app:assembleDebug UP-TO-DATE`，APK `70309888 B` / mtime `2026-11-08T03:50:28Z`（**非本轮**）⇒ `rc=0` 但**出包判据未被真实行使**（零 `.kt` 改动 ⇒ Gradle 判定无输入变化、不重打包）。按四态，此时 G1 只能判 ⚠️（编译命令成功但产物未重生成），**不得写成「G1 出包 ✅」**。
2. 主理人回源后执行最小修复（主理人自检已把控制流走通再下发）：`python os.remove` 删 `app-debug.apk`（**未用 `rm -rf`**，规避 Windows 句柄锁）→ 重跑 `:app:assembleDebug` ⇒ 日志 `:app:packageDebug`（无 UP-TO-DATE 标记，真实执行）、`270 actionable tasks: 1 executed, 269 up-to-date`、`BUILD SUCCESSFUL in 40s`、`rc=0`；重取 APK = **`68390522 B` / mtime `2026-11-11T08:22:44Z`（本轮）**。⇒ G1 出包判据**已真实行使**，四态转 ✅。
3. **对 W12-NEW-1 的响应（⚠️ 部分缓解、未定谳）**：`docs/handoff-20261107-wave11.md:27/:131` 与 `docs/handoff-20261108-wave12.md:20/:55` **都记 `70,309,888 B`**；本波强制重出包得 `68,390,522 B`，差 **1,919,366 B（约 2.7%）**。⇒ 已用**可复算动作**取得本轮真实值，把疑点从「疑似旧日志复用（不可证）」推进为「历史两值与本轮值不一致（可复算）」。但**差异归因不唯一**：(a) 构建环境漂移（Gradle / AGP / JDK / 依赖缓存随时间变化，1.9MB 量级更像依赖或资源打包差异而非代码）；(b) W12 handoff 抄了 W11 旧值。**两者本波均无法证伪，故不下定谳**，只如实记录可复算事实；不写成「W12 确认造假」、也不写成「疑点已销」。

> 🔴 **判据纪律**：`rc=0` ≠ 「产物已重生成」。本波若止于首轮 UP-TO-DATE 就判 G1 ✅，等于让「出包」判据恒假绿 —— 与「测试全绿 ≠ 口径被验证」同源。

### 2.3 编译失败四分类（本波**未触发**，如实写）

四分类 = 参数错 / 工具名错 / 该调不调 / 调了不收敛。本波本地闸门 **G1–G4 `rc` 全 0** ⇒ 编译环节四分类**未触发**，无失败案例，**不虚构归因案例**。真机侧四分类见 §3.6（全部归因取证驱动）。

---

## 三、第 3 步：真机多模型 × 多上下文档位 + 全功能面

### 3.1 前置自检与 known-good 对照格 `CTRL-0`（开批硬闸）

| 判据 | 实测 | 结果 |
|---|---|---|
| 唤醒三步法 | `KEYCODE_WAKEUP` → screencap `nonblack_ratio=0.9996` / `distinct_color_buckets=90`（>3）/ `stat_alive=true` / 1080×2400 → `step3_pass=true` | ✅ |
| 唤醒态 | `SCREEN_STATE_ON` + `INTERACTIVE_STATE_AWAKE` + `foreground_is_app` 全 true | ✅ |
| 设备资产 | 端点 6、secrets 6、`usesCleartextTraffic="false"`、`endpoints_preloaded=true` | ✅ |
| 仓内零凭据 | 扫 428 文件、4 命中**全在 `SecretRedactorTest.kt` 测试固件假凭据**（`test_fixture_only=true`） | ✅（`preflight_pass=true`） |
| `in_app()` | 「新对话」节点 + 「添加图片」按钮均在 | ✅ |
| `CTRL-0` 五项 | `bounds_valid` / `shot_not_black` / `usage_line` / `in_app` / `tail_fresh` **全 true** ⇒ `control_pass=true`、`known_good_verified`（严格探针 `probe_hit=true`、`usage=1.6k in / 32 out`） | ✅ 判据自身被验证，**允许开批** |

> ⚠️ 如实缺口：`CTRL-0` 当时 `current_window_label=null`（设置页未滚到档位区），window 用 1M fallback ⇒ `context_match=false`（实测分母 27.9K = 32K 档）。迭代 1 已修 precheck 逐屏回读并复验得 `当前 32K`；**`CTRL-0` 未重跑**，该 mismatch 如实保留、不掩盖。对照格只证判据可行，**不是交叉格结论**。

### 3.2 同组交叉格（`W16-CROSS-G1` / `w16-matrix/1` / 同一 ASCII 短题面 `Reply with exactly OK` / 每题前点新对话 / 同一 60s 窗口 / 同一列集）

B2 批次 `rc=0`，**12/12 格**，4 档 × 3 端点交叉积 12，端点覆盖 3 个不可逆 fp（`8ac38d1b` / `c4c25092` / `29878504`）。四态合计：**converged 4 / supplier 4 / vacuous 4**；`context_match_count=4`、`switch_verified_count=11`、`probe_hit_count=12`、`tail_fresh 5` / `tail_stale 7`。

| cell | 档 / window_tokens | fp / 声明窗口 | 四态 | failure_class | 上下文 / 期望 | cm | swv | fresh |
|---|---|---|---|---|---|---|---|---|
| B2_X01 | 32K / 32000 | `8ac38d1b` / 1048576 | ✅ converged | none | 27.9K / 27.9K | ✅ | ✅ | ✅ |
| B2_X02 | 32K / 32000 | `c4c25092` / 512000 | ⚠️ supplier | no_usage_no_error | — / 27.9K | ❌ | ✅ | ❌ |
| B2_X03 | 32K / 32000 | `29878504` / 262144 | ⛔ vacuous | driver_switch_not_converged | — / 27.9K | ❌ | ✅ | ❌ |
| B2_X04 | 128K / 128000 | `8ac38d1b` / 1048576 | ✅ converged | none | 123.9K / 123.9K | ✅ | ✅ | ✅ |
| B2_X05 | 128K / 128000 | `c4c25092` / 512000 | ⚠️ supplier | no_usage_no_error | — / 123.9K | ❌ | ✅ | ❌ |
| B2_X06 | 128K / 128000 | `29878504` / 262144 | ⛔ vacuous | driver_switch_not_converged | — / 123.9K | ❌ | ✅ | ❌ |
| B2_X07 | 512K / 512000 | `8ac38d1b` / 1048576 | ✅ converged | none | 507.9K / 507.9K | ✅ | ✅ | ✅ |
| B2_X08 | 512K / 512000 | `c4c25092` / 512000 | ⚠️ supplier | supplier_no_settle | — / 507.9K | ❌ | ✅ | ❌ |
| B2_X09 | 512K / 512000 | `29878504` / 262144 | ⛔ vacuous | driver_switch_not_converged | — / 258.0K | ❌ | ✅ | ❌ |
| B2_X10 | 1M / 1000000 | `8ac38d1b` / 1048576 | ✅ converged | none | 995.9K / 995.9K | ✅ | ❌ | ✅ |
| B2_X11 | 1M / 1000000 | `c4c25092` / 512000 | ⚠️ supplier | supplier_no_settle | — / 507.9K | ❌ | ✅ | ❌ |
| B2_X12 | 1M / 1000000 | `29878504` / 262144 | ⛔ vacuous | driver_switch_not_converged | — / 258.0K | ❌ | ✅ | ✅ |

> cm = `context_match`；swv = `switch_verified`；fresh = `tail_is_fresh`。分母复算式 `Y = min(window_tokens, endpoint_declared_window) − maxOutputTokens`。

### 3.3 档位切换维度已被攻克（W15 ⛔vacuous 主攻项）

W15 六格 128K+ 全 ⛔vacuous，根因 `window label not found`。本波在**取证驱动侧**定位并修复，四态 ✅：

- **修复**：`switch_window` 放宽匹配为 `\b label \b`（排除「当前 X」值文本）、x 上限 `400 → 1000`、`ensure_visible` 保留逐屏 dump。
- **根因（归因取证驱动，非应用 bug）**：设置页是 Compose NavHost，重进会恢复上次 back stack（端点管理子页）；旧驱动在端点列表里找档位标签 ⇒ `window label not found`。另 `settings()` 把端点管理子页误判成设置页主列表、`switch_to_fp` 内层 `break` 致 M3/M5 不可达。
- **结果**：B2 12 格 `switch_verified=11`；**严格同格** `switch_verified=true AND context_match=true` 命中 **32K（27.9K）/ 128K（123.9K）/ 512K（507.9K）三档**，满足 §1.3 A3 出口判据「≥2 档显式切换成功并读到 `context_match=true`」。
- ⚠️ **1M 不写成同格通过**：`B2_X10` 的 `context_match=true` 但 `switch_verified=false`（分母 995.9K 复算兜底）⇒ 属跨格拼凑，派生件已增补 `strict_same_cell_tiers=[32K,128K,512K]` 与 `one_m_same_cell_caveat` 防误读。

### 3.4 1M 读数 ≠ 1M 真压（分离判据）

1M 受 `W9-11` 双阻断（L3 字符闸 `cap=50227` 现场 + `RoundHeadGate 400k`），本波**只证分母生效**：`B2_X10` 读到 `995.9K = min(1048576, 1000000) − 4096`。未灌入近 1M 量级内容 ⇒ 1M 真压如实 ⛔，**`W14-05` / `W15-05` / `W9-11` 不关号**。派生件落双口径注释 `declared=2^20(1048576)` / `tier=10^6(1000000)` / `effective=min(...) − maxOutputTokens`；**32K / 128K 复算按外审订正后不重做**。

### 3.5 全功能面七项**双向**判据（同 group_id / 同驱动 / 同列集；每项 `both_states` 留痕）

**合计：blocked 2 / partial 1 / vacuous 4，零 ✅。不引用 W12 / W14 / W15 结论冒充本波通过。**

| # | 功能项 | 四态 | failure_class | 正向证据 | 反向证据 | 局限（如实） |
|---|---|---|---|---|---|---|
| F1 | 多模态输入 | ⛔vacuous | driver_attach_not_exercised | F5 真 | B0 真 | 200×120 上红下蓝真图已生成（**禁 1×1**）、in_app + 视觉端点前置有节点，但 SAF 附件**确被行使**未成立、语义「上红下蓝」未观测 ⇒ 不写通过 |
| F2 | 工作空间文件读写 | ⛔vacuous | criteria_not_exercised | F0 真 | B0 真 | 审批框动态取 bounds 判据已实现，本轮审批框 / 工具轨迹均未出现；写前不存在 → 写后逐字探针串未成立 |
| F3 | 记忆 | ⛔vacuous | criteria_not_exercised | F0 真 | B3 真 | **`snapshot_before` 数值已补**（W15 失因是 `f['backward']={...}` 整体覆盖，本波改 `update()`）；探针 token 换 W16 新值避免旧库假绿；模型回复未确认保存 ⇒ 未行使 |
| F4 | **对话框 skill 引用手动触发** | ⚠️partial | manual_reference_single_direction | F3 真 | B0 真 | 用户侧显式 `@notes-organizer` 引用已发出，但 skill body 可观测加载未成立 ⇒ 单向。**【推断】**现役 `EventReducer` 明示技能系统未接 UI、`AgentEvent.SkillLoaded` 无人 emit、无独立 `@` 引用解析器；判据以「用户显式引用串 + `skill_load` 工具结果 `[SKILL BODY]`」为可观测口径，**不等同于应用内存在引用语法解析器**。与 W15 的**模型侧** `skill_load` 判据分开写，不简写、不冒充 |
| F5 | MCP | ⛔blocked | platform_cleartext_disabled | F2 真 | B1 真 | G15b 实跑 `OK` 在前 + `usesCleartextTraffic="false"` + 源码 `transport_error` 两处 ⇒ 平台阻断，**未为测试放开明文** |
| F6 | 手动压缩 | ⛔vacuous | manual_compress_noop | F0 真 | B1 真 | 确认框 / `dropped=N>0` / 历史条数下降（journal dropped+kept 数值判据，本波新增）/ 下一请求 `cached=0` 四判据均实现，本轮只到 `noop`，不算触发 |
| F7 | 自动压缩 | ⛔blocked | kernel_preflight_budget_block | F0 真 | B2 真 | 真触发 `auto_compact dropped=N` 未见，`noop below_trigger` / `already_compressed` 不算；结算时机 + L3 `PreStepBudget` 前置熔断 ⇒ `structural_unreachable` |

### 3.6 真机侧失败四分类（**全部归因取证驱动，未改任何应用 `.kt`、未摊派成应用 bug**）

- **四闸门 `rc` 全 0** ⇒ 编译侧四分类「**未触发**」（无失败案例，不虚构）。
- **真机失败一律归取证驱动**：
  - `driver_switch_not_converged` 4 格（目标端点 `6ecd5b22` 始终未收敛，实际落到 `29878504`；端点列表 idx 定位漂移，旧 `break` 已在迭代 2 修，该端点仍不可达）⇒「调了不收敛」。
  - 迭代 2 连续 `window label not found` ⇒「该调不调 / 调了不收敛」。
- **供应商侧**：`no_usage_no_error` 2 / `supplier_no_settle` 2 ⇒ 4 格 ⚠️，**不判绿**（`settled=true` ≠ `converged`）。
- **环境 / 平台**：MCP 明文 = `platform_cleartext_disabled`（平台阻断，非驱动非应用）；自动压缩 = `kernel_preflight_budget_block`（内核结构不可达）。
- **未归因项**：链内迭代触顶 3/3 后如实 ⚠️ 收尾——目标端点不可达、功能面多项未行使、1M 仅分母读数、`CTRL-0` 的 1M fallback mismatch，**均未再驱动修复轮次**，如实 ⚠️ 收尾、不硬跑。

---

## 四、第 4 步：链内迭代记录与推送 / CI

### 4.1 链内循环（每次编译 / 真机 / 推送一行证据）

⚠️ **口径**：本波零 `.kt` 改动 ⇒ 编译侧只有一次「出包判据修复」动作，无「改应用代码 → 重新编译」循环；
链内循环以「改取证驱动 / 改档位 / 改会话状态 → 真机 → 再看」为形态。`<最大真机迭代>` = 3 轮，
**采集批不计入迭代轮次**（§1.5）。**真机正式迭代 3 轮，触顶止损**。`_w16/evidence/iterations.jsonl` 共 5 行：
前 3 行为真机迭代（`iter=1..3`），后 2 行同为 `iter=4` 的 G1 判据修复（开发席 `06:39:32Z` 与主理人 `08:22:44Z`
各执行一次强制重出包，两行原始证据均保留、不合并删改）。

| # | 改 | 真机 | 再看（证据） | 结果 |
|---|---|---|---|---|
| ITER-1 | W16 驱动改造（路径 / 常量 / 六列 / 严格探针 fresh）；`switch_window` 放宽匹配、precheck 逐屏回读 | CTRL-0 | 五项判据全绿、`probe_hit=true`、`tail_is_fresh=true`；设置页档位回读由 `[]` 恢复 `["当前 32K"]` | ✅ **判据自身被验证，允许开批**（CTRL-0 未重跑，1M fallback mismatch 如实保留） |
| ITER-2 | `settings()` 端点管理子页不再误判为主列表 + 逐屏滚到档位区；`switch_to_fp` 去 `break`、遍历 `EP_MAP` idx；批次标签保 `cell` 唯一 | B1 首格 X01 起 | X01 实证攻克 32K 显式切档（`switch_verified=true` / `27.9K` / `converged` ✅）后 X02/X03 连续 `window label not found` | ⚠️ 主动止损停批（9m47s）；归因「调了不收敛」，**非应用 bug** |
| ITER-3 | `home()` 改逐次 BACK 到「新对话」；`settings()` tap 后动态取「返回」bounds 退出 NavHost 恢复的子页；`ensure_visible` 免重复回顶 | 复验 128K | `error=null` / `switch_verified=true` / 回读 `当前 128K` / `subpage_exits=1` / `dialog_seen=true` | ✅ **第 2 档（128K）显式切档复验通过**；遗留根因彻底定位并修复在驱动侧 |
| iter=4（判据修复，**不占真机轮次**） | `python os.remove` 删 `app-debug.apk` → `packageDebug` 真实执行 → `assembleDebug rc=0`（开发席 `06:39Z` + 主理人 `08:22Z` 各一次） | 无设备动作 | 两次重出包**字节数完全一致 = `68390522 B`**（mtime 分别 `06:39:32Z` / `08:22:44Z`）；旧产物 `70309888 B` | ✅ G1 出包判据由未行使转**真实行使**；两次同值 ⇒ 出包**确定性（deterministic）** |

**编译侧只有一条**：`G1 rc=0`，全程一次真实出包（其余任务 UP-TO-DATE）。
**零 `.kt` 改动** ⇒ 不存在「改应用代码 → 重新编译」循环，如实写明，不虚构。
**派生件 / 驱动修复不占迭代轮次**：`switch.py` / `w15_matrix.py` / `w15_precheck.py` 均属取证驱动；
主理人另修 `make_fixture.py`（弃用 W15 旧格式草稿：14 格、`window_tokens` 全 None、note 仍写「五列」），
改以 `cross_summary.json` 的同一批 B2 12 格重建仓内派生件，出厂断言见 §5.6。

### 4.2 编译失败四分类

见 §2.3（编译环节一次通过、四分类未触发）与 §3.6（真机侧四分类全部归因取证驱动）。

### 4.3 推送与 CI（⚠️ 首推 Build 判红已归因修复；修复后无绿 Build run 佐证，如实写明）

- **推送 1（commit `f78cb2a`，`6e695d5..f78cb2a main -> main`，`push_rc=0`）**：实测 **2 workflow / 2 run**（符合 CI 席预测）。
  - 独立 Secret scan run `42997718215` = **`success`** ✅
  - Build run `42997718271` = **`failure`** ⛔（**CI 红第 1 次**，未触 `<最大 CI 红次数>` = 2）
    - 五 job：`Assemble Debug` / `Secret scan` / `Lint` / `Unit tests` 四 job **`success`**；**`Arch guard` job `failure`**。
    - **四分类归因（守卫行为正确；非环境、非应用 `.kt` bug、非取证驱动）**：`G20` 判 `VIOLATION`，CI 日志原文
      `台账缺 ID（handoff 里有、台账里没有）：W11-06 W13-01`。根因 = 本波 handoff 引用了 W11 段第 6–10 号与 W13 段第 1 号
      两处**编号跳号**；这两处历史上**从无独立台账行、从无真实挂账内容**（外审《W11-W15 审查》原文即称
      「漏号未预铸…编号纪律与 G20 一致」）。
    - **主理人自检失误（如实记录）**：首次简化复现误用「ID 在台账任意位置出现」判据 ⇒ 误判 `0 缺失`；
      改用 G20 **精确判据**（台账**行首** ID 完全匹配 `^W\d+-\d+$`，见 `arch-guard.sh:1337-1351`）后复现出
      `缺失=[W11-06, W13-01]`，与 CI 一致。
    - **修复（不制造幽灵挂账）**：修正 handoff 两处措辞，不再把跳号写成 ID 字面量（改「W11 段第 6–10 号 / W13 段第 1 号」
      中文表述）；「补登这两处跳号」**仍保留为 `W16-01` 待办**（待独立成波），本波**不编造其挂账内容、不增幽灵 `OPEN` 行**
      （避免外审《W15 最终态复核》§2.4 批评的台账膨胀）。修后按 G20 精确判据本地复现 `缺失=[]` ⇒ 判据应绿。
    - **顺带部分证伪一条外审论断**：外审《W11-W15 审查》称 G20 存在「结构性盲区、查不出漏号」；本次证明 **G20 实际能查出
      「handoff 引用了无独立台账行的编号」**——只是历史上从无人引用跳号，盲区未被触发。此发现已回写本节，供外部审查复核。
- **推送 2（纯文档回填：handoff §八 + 本节 §4.3 / §5）**：`build.yml` 因 `paths-ignore` **0 run**，仅独立 `secret-scan.yml` 触发
  ⇒ **不写成「构建再次通过」**。
- **触发面口径**（CI 席只读核对）：`build.yml` 的 `paths-ignore` 只排除 `docs/**` + `**/*.md`，`scripts/**` 无额外过滤
  ⇒ 本波新增的 `scripts/fixtures/w16-matrix-facts.json` **单个文件即真实触发 build 五 job**；同一次 `push main` 还触发
  独立 `secret-scan.yml`（1 job）⇒ **一次推送 = 2 workflow / 2 run / 6 job**。`release.yml` 是 tag 轨，本波不触发。
- **`.gitleaks.toml` allowlist** 只精确放行两个 `.kt` 测试夹具 + 一个标识符，**不覆盖 `scripts/fixtures/*.json`**
  ⇒ 本波派生件出厂断言含凭据 / 路径 / 序列号 / 端点标识 / 账号字段五类硬扫描，`hard_total=0`（§5.6）。
- **`EXPECTED_TESTS` 对账闸门**（`build.yml:445-525`）：零 `.kt` ⇒ `actual = 799 = expected` ⇒ 通过（推送 1 实跑 `Unit tests` job `success` 佐证）。
- **commit 短哈希与 run 链接**：随交付消息给出，**不在仓内自指回填**（避免无限回填链，W15 同口径）。
- **不 force-push、不 rewrite history**；每次推送留一个可审计 commit 与 run 链接。推送次数 = 2（未触上限 3）；CI 红次数 = 1（未触上限 2）。

---

## 五、第 5 步：收尾契约

### 5.1 「1→5」各步结果（各带四态与证据路径）

| 步 | 名称 | 四态 | 关键结论 | 主要证据路径 |
|---|---|---|---|---|
| 1 | 范围收敛与裁决 | ✅ | 四份外审分歧逐条裁决（§1.2 五处事实分歧 + 4 条互相订正，含本波新增回源证伪一条外审处方）；不改方案大体方向 | 本文件 §1；方案席 Phase 1 只读报告 + 主理人复核（`git status` / `HEAD` 证明只读） |
| 2 | 本地 UI 与功能闸门 | ✅ | G1–G4 `rc` 全 0；**G1 出包判据经强制重出包转真实行使**；`@Test 799 ↔ 799`；arch-guard `OK=27 VACUOUS=1 VIOLATION=0`；selftest `30/30` | `<取证目录>/evidence/g1..g4*.log`、`g1_apk_meta.json`（before）+ `g1_apk_meta_rerun.json`（after） |
| 3 | 真机多模型 × 多上下文 + 全功能面 | ⚠️ | 交叉格 **12/12 格**、四档全覆盖、**档位切换维度攻克**（严格同格 32K / 128K / 512K 三档）；功能面 **零 ✅**（blocked 2 / partial 1 / vacuous 4）；1M 仅分母读数 | `cross_summary.json`、`feature_summary.json`、`matrix_facts.jsonl`、`feature_facts.jsonl`、19 张脱敏 `_safe.png` |
| 4 | 推送与 CI | ⚠️ | 推送 2 次；实测 2 workflow / 2 run。首推 Build 判红（`G20` 查出 handoff 引用两处**编号跳号**）已四分类归因并修复 handoff 措辞；**修复后无一次绿 Build run 佐证**（推送 2 为纯文档 0 run ⇒ Build 判据本轮未再被行使）。独立 Secret scan `success` | 本文件 §4.3；handoff §八 |
| 5 | 收尾（未做 / 挂账 / 回写 / 制品清单） | ✅ | 本节；台账 W16 17 行、handoff 落盘 | `docs/06-deferred-ledger.md` §二-sexies、`docs/handoff-20261111-wave16.md`、`git diff` |

> ⚠️ 第 3 步整体 ⚠️（非 ✅）：档位维度**攻克**是本波实质进展（W15 该维度 ⛔vacuous），但端点维度仅覆盖 3 个 fp（目标 `6ecd5b22` 未收敛）、供应商侧 4 格 ⚠️、功能面多项未行使、1M 仅分母。**不得因档位攻克就把第 3 步写成 ✅**。

### 5.2 链内迭代记录

见 §4.1 表（真机 3 轮触顶 + `iter=4` G1 判据修复两次重出包）。原始逐行证据 `<取证目录>/evidence/iterations.jsonl`（5 行，前 3 真机、后 2 同为 G1 判据修复，均保留不删）。

### 5.3 明确未做（逐条带 `W16-` 挂账号）

> 🔴 **本次任务停于此，交由外部团队审查。** 下列为本波「明确不做 / 顺延」的完整清单；每条带 `W16-` 挂账号，与台账 §二-sexies 一一对应。**不写「建议下一链做什么」**（CI 席与隐私铁律口径）。

| 挂账号 | 明确未做 / 不做 | 本波处置 |
|---|---|---|
| `W16-01` | 外审第 0 车逐条落号（G20 扫描面盲区、`W11-06..10` / `W13-01` 漏号补登、checklist 三硬规则、W15 派生件收口、文档零头） | 顺延；改真值源结构 / 新增守卫属独立波 |
| `W16-02` | 安全波收口（消重复扫描 job / 安装脚本 pin+sha256 / allowlist 精确化 / 全历史首跑 / 签名端到端） | 顺延 |
| `W16-03` | 拆分三方（`core-context` 余 4 / `feature-chat` 余 12 / `feature-settings` 余 8）+ 四态生产读者 + 分母族归一 + jsonMode×tools + 五条 ≤30 行快修 | 顺延；需改 `.kt` + 红态用例 |
| `W16-04` | `W9-05` journal / FreezeClock **第 11 次顺延**（逐波口径 12，两口径并列待裁决） | **用户裁决项，不代做**；只登记顺延 |
| `W16-05` | 设备侧 1M **真压** | ⛔ 未做（`W9-11` 双阻断）；本波只做 1M **读数**，`W14-05` / `W15-05` / `W9-11` 不关号 |
| `W16-06` | MCP 明文联调 / 放开 `usesCleartextTraffic` | ⛔ 不做（平台阻断，不为测试放开明文） |
| `W16-07` | git 历史清理 / force-push / rewrite history | ⛔ 不做（本单明禁不可逆动作） |
| `W16-08` | 音频输出 / `core-device` 独立波 / 图片预算四条 / 为凑缓存加长 system | 不做 |
| `W16-09` | 把 PC 侧结论升级为「设备侧可用」 | ⛔ 不做（`AGENTS.md` §二.3 四态纪律） |
| `W16-10` | 前置熔断路径的压缩试算入口合并设计 + `ChatViewModel.kt:395` 结算时机 + `W9-01` 触发线量纲 | 顺延；需改 `.kt` |
| `W16-11` | 本波未取全的功能项**冒充已验收** | ⛔ 不做：功能面 **零 ✅** 如实落盘，`both_states` 逐项留痕，不引用 W12 / W14 / W15 结论冒充本波通过 |
| `W16-12` | 改 `.kt` / 新增守卫 / 动 `EXPECTED_TESTS` / 调 `LINE_CAPS`·`TOTAL_LINE_CAP`·`VACUOUS_BASELINE` / 改 CI 配置 | ⛔ 未做（本波零 `.kt`、零守卫、零 CI 配置改动） |
| `W16-13` | 代做四项用户裁决（`W9-05` / 「≥3 端点」二选一 / §1.5 占位符口径 / `P2-3` 二选一） | ⛔ 不代做，只留痕 |
| `W16-14` | 文档回写差异（W14 验收物重复 `### 5.5` 标题与 §4.3 占位段、§5.1 步 3 过度声明、W15 §5.5「将包含」未来时） | 保留并记差异；历史制品「回填不篡改」，删改须单独裁决 |
| `W16-15` | 派生件硬伤（`cell` 唯一 / `safe_shot` 唯一 / `window_tokens` 数值列 / 1M 双口径 / `memory.snapshot_before` / `tail_is_fresh` 升级） | **本波已执行缓解**：六列出厂校验 + 五类硬扫描 `hard_total=0`（证据 §5.6）；触发信号（校验失败）**未发生** ⇒ 属防御性登记，保持 OPEN 表持续约束，转 CLOSED 留外部审查确认 |
| `W16-16` | 以 `W16-XX` 承接任何老号或漏号 | ⛔ 不做（非承接硬约束，见 §1.4 三条） |
| `W16-17` | 把「1M 读数」写成「1M 真压」/ 把档位驱动失败归因成应用 bug / 把 skill 手动触发简写成 W15 `skill_load` | ⛔ 不做（§3.3 / §3.4 / §3.5 F4 均已按订正口径落盘） |

### 5.4 OPEN 挂账（触发信号 + 缓解动作 + 挂账号）

台账 `docs/06-deferred-ledger.md` §二-sexies 本波新登记 **17 行全 `OPEN`**（每行自带触发信号 + 缓解动作 + 证据），唯一真值源仍是 §一。下表摘录本波**实质活跃**的 OPEN 项；完整 17 行见台账。

| 挂账号 | 触发信号 | 缓解动作 | 状态 |
|---|---|---|---|
| `W16-01` | G20 只校验「handoff ID 是否在台账」，不校验「外审 P0 必落独立台账行」；`W11-06..10` / `W13-01` 连续漏号 | 补登独立台账行 + 必红变异样本；改扫描面属新增守卫 ⇒ 独立波 | OPEN |
| `W16-02` | 安全波 5 项（重复扫描 job / 安装脚本未 pin / allowlist 粗放 / 无全历史首跑取证 / 签名端到端未验） | 逐项收口 | OPEN |
| `W16-03` | `core-context` 余 4 / `feature-chat` 余 12 / `feature-settings` 余 8；四态零生产读者；分母族未归一；jsonMode×tools | 先拆后改，禁调三个行数常量 | OPEN |
| `W16-04` | `W9-05` 第 11 次顺延（两口径 11/12 并列） | **用户裁决**：开波 / 显式「永不实现」/ 统一代数口径 | OPEN（待用户裁决） |
| `W16-05` | 设备侧 1M 真压（`W9-11` 双阻断） | 先证结构可达，再合并设计 | OPEN |
| `W16-06` | MCP 明文（`usesCleartextTraffic=false`） | 单独出裁决文档，不为测试放开 | OPEN |
| `W16-07` | git 历史含旧身份 / 凭据残留 | 用户二选一后处理；本单明禁 force-push | OPEN（待用户裁决） |
| `W16-08` | 音频输出 / `core-device` / 图片预算四条 / 加长 system | 逐项评估 | OPEN |
| `W16-09` | PC 侧结论误升级为设备侧可用 | 四态纪律拦住 | OPEN（持续约束） |
| `W16-10` | 前置熔断路径缺压缩试算入口；`ChatViewModel.kt:395` 结算时机；`W9-01` 触发线量纲 | 合并设计 + 改 `.kt` | OPEN |
| `W16-11` | 本波功能面零 ✅，存在「未取全项冒充已验收」风险 | 已用 `both_states` 逐项留痕 + 四态如实落盘 | OPEN（持续约束） |
| `W16-12` | 改 `.kt` / 新增守卫 / 动 `EXPECTED_TESTS` / 调三常量 / 改 CI 配置 | 本波零改动；触顶纪律 | OPEN |
| `W16-13` | 四项用户裁决悬置 | 只留痕，不代做 | OPEN（待用户裁决） |
| `W16-14` | 文档回写差异（W14 重复标题与占位段、W15 §5.5 未来时） | 保留记差异，删改须单独裁决 | OPEN |
| `W16-15` | 派生件硬伤复发风险（cell / safe_shot 唯一、window_tokens、1M 双口径、`snapshot_before`、stale 标记） | 六列出厂校验 + 五类硬扫描，本波 `hard_total=0` | OPEN（本波已缓解，未发生触发） |
| `W16-16` | 以 `W16-XX` 承接老号 / 换号承接 | 非承接硬约束 | OPEN（持续约束） |
| `W16-17` | 口径混写（1M 读数=真压 / 驱动失败归因应用 bug / skill 判据简写） | 本波三处均按订正口径落盘 | OPEN（持续约束） |

> 老号 `W14-01..11` / `W15-01..14` 全数保留 `OPEN`，本波不删、不改状态、不写已核销、不重判 `CLOSED`；`W9-05` 第 11 次顺延如实登记（§1.2）。

### 5.5 文档回写 diff

| 文件 | 变更 | 内容 |
|---|---|---|
| `docs/analysis/w16-matrix-acceptance.md` | 新增 | 本文件（验收物，§1–§5） |
| `docs/handoff-20261111-wave16.md` | 新增 | W16 交接文档 |
| `docs/06-deferred-ledger.md` | +30 行 | 新增 §二-sexies（W16-01..17，全 OPEN，每行触发信号 + 缓解动作 + 证据） |
| `scripts/fixtures/w16-matrix-facts.json` | 新增 | W16 脱敏派生件（46113 B；**唯一非 docs / 非 md 文件，真实触发 build 五 job**） |

**零 `.kt` 改动、零新增守卫、零 `EXPECTED_TESTS` 改动、零 CI 配置改动、不调三个行数常量**（§1.4 / `W16-12`）。

### 5.6 交给外部审查团队的制品清单

> 🔴 本清单**不含**：会话历史、任何凭据或密钥形态、端点标识（模型名 / baseUrl）、设备序列号、本机绝对路径、「建议下一链做什么」。原始截图（19 张 `_raw.png`）与完整 logcat **已删除、不入库**；派生件只保留脱敏后字段。

**仓内（随 commit 交付）**：
- `docs/analysis/w16-matrix-acceptance.md` — 验收物（本文件）
- `docs/handoff-20261111-wave16.md` — 交接文档
- `docs/06-deferred-ledger.md` — 台账（含 §二-sexies W16 17 行）
- `scripts/fixtures/w16-matrix-facts.json` — 脱敏派生件（出厂自检 `pass=true`：`cells=12` / `cell_unique` ✅ / `safe_shot_unique` ✅ / `six_columns_all` ✅ / `window_tokens_all_int` ✅ / `one_m_note_present` ✅ / `memory_snapshot_before` ✅ / `prior_batch_isolated` ✅ / 五类硬扫描 `hard_total=0`）

**仓外取证（不进仓，仅供外部团队按需索取，路径以 `<取证目录>` 代指）**：
- 本地闸门：`g1_assemble.log` / `g1_apk_meta.json`（before `70309888B@11-08`）/ `g1_apk_meta_rerun.json`（after `68390522B@11-11`）/ `g2_fulltest.log` / `g2_xml_mtime_check.json` / `g3_archguard.log` / `g4_selftest.log` / `gate_four_way.json`
- 真机矩阵：`precheck.json` / `batch_meta.json` / `cross_summary.json` / `matrix_facts.jsonl` / `switch_facts.jsonl` / `endpoint_map.json` / `window_diag.json`
- 功能面：`feature_facts.jsonl`（7 行）/ `feature_summary.json` / `compress_facts.jsonl`
- 链内迭代：`iterations.jsonl`（5 行）/ `both_states.jsonl`
- 脱敏截图：`shots/` 下 19 张 `*_safe.png`
- 脱敏自检：`redact_selfcheck.json`（36 文件 / `total_hard_hits=0` / `device_serial_hits=0` / `pass=true`）

**可复核判据锚点**（供外部团队独立复算）：严格 `@Test` = 799 ↔ `build.yml:132`；分母式 `min(window_tokens, endpoint_declared_window) − maxOutputTokens`（32K=27904 / 128K=123904 / 512K=507904 / 1M=995904）；G20 以 `arch-guard` 实跑输出为准（本波 88 个 handoff ID）。

**本波止于交接，交由外部团队审查。不写「建议下一链做什么」。**

<!-- W16-SECTION-5-END -->
