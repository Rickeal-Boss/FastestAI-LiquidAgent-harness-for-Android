# W7 交接 — 长程上下文多模型矩阵 + 切换守卫 + 会话级自动压缩 + :core-context 拆分（2026-11-04）

> 基线：`c67f37d`（W6 收口）→ 本波（提交号见 git log）
> 相关：`docs/notes/w7-plan.md`（作战方案 521 行）｜`docs/notes/w7-provider-matrix.md`（多模型实测 377 行）｜
> `docs/notes/w7-line-budget-ruling.md`（行数预算一次性裁决）｜`docs/notes/w7-context-cache-roadmap.md`（四报告融合的路线草稿，历史输入）｜
> 挂账唯一台账 `docs/06-deferred-ledger.md`（42 行）

---

## 一、本波结论（先看这段）

| 项 | 结果 |
|---|---|
| 单元测试 | **696 / 0 / 0 / 0**（14 模块；W6 基线 666 → +38 新增 − 8 删占位）。**696 条全部是行为断言**（8 条恒绿占位已随 W7-04 删除，二档口径作废） |
| 架构守卫 | **28 条判据：OK=27 VACUOUS=1 VIOLATION=0**（新增 G22 切换守卫被调用 / G23 自动压缩唯一调用点；G19 仍 VACUOUS） |
| 守卫自证 | `arch-guard-selftest` **30/30 PASS**（+G22/G23 两个变异 case：删调用 ⇒ 必红） |
| 全仓 main | 约 **35 233 / 42 500**（212 文件；`TOTAL_LINE_CAP` 未动）。per-module 上限之和 1800→**2900** = +100 拆分开销（W6-07）+ **+1000 W7 功能预算**（一次性例外，见裁决文档） |
| 真机 | 见 §四（验收执行中，四态表以 `_w7/device-acceptance.md` 为准） |
| 模块 | 13 → **14**（新增 `:core-context`，W6-07 关闭） |

**一句话**：W7 把「上下文容量治理」做成了三件互相咬合的事 ——
**换模型前先对账**（`SwitchGuard`：装不下禁止切换、压缩到范围内可重判放行、切 1M 装得下放行）、
**正常结束才评估压缩**（`SettledCompressPolicy`：触发时点与收益阈值分离，未压轮次保住严格前缀 ⇒ 缓存命中才可能非零）、
**会话体积单一真源**（`SessionFootprintStore` 进程级字符数通道，绕开 feature-* 禁互依）。
多模型侧拿到 **1M 档两个真实通过实例**（NV nemotron 与 mistral 各一，均 `finish=stop` 取针命中），并证实
**免费池前缀缓存不可依赖**（三连发/分块无一满足双向断言）。

---

## 二、交付清单

### 2.1 `:core-context` 拆分（W6-07 关闭）
- `core-kernel/context/` 六支源码 + 六支单测整体下沉 `:core-context`（12 支与 HEAD 逐字节 `cmp` 一致，零逻辑改动）；
- 依赖方向单向 `:core-context → :core-kernel`（context 反向依赖 policy/log），G6 禁止边钉住，`core-agent` 以 `api` 转发；
- 守卫九处同步（`settings.gradle.kts` / `MODULES` / `LINE_CAPS` / `MAIN_PATHS` / `CJK_SCAN_PATHS` / `FORBIDDEN_EDGES` / G9 fixture / selftest preflight / 注释）。

### 2.2 内核策略（主理人落地，:core-context）
| 文件 | 内容 |
|---|---|
| `SwitchGuard.kt` | `evaluateSwitch(committedChars, target, maxContextTokens, calibratedCharsPerToken)`。判定式**直接调现役** `hardContextBudgetTokens`（verified 优先、减 `target.maxOutputTokens`、`window/2` 兜底）→ `effectiveCharsPerToken`（floor 1.8 / 标定 ≤6.0 二档）→ `charCapOf`，零自写算式；window 用 `maxOutputTokens=0` 影子端点取回。四态：BLOCKED（装不下，**优先于兜底分支**）/ VACUOUS（空会话 / 窗口未验证 / 兜底）/ ALLOWED。用户裁决：320k 会话 vs 256k 模型 ⇒ 禁止；压缩到范围内重判放行；切 1M 装得下放行 |
| `SettledCompressPolicy.kt` | `shouldEvaluate`：仅 `Finished(ModelStopped)`（`Finished` 与 `Cancelled` 共用 `finish`，**绝不挂 finally**）。`shouldApply`：字符量纲 `COMPACT_TRIGGER_CHARS=50_227`（=32K 档 floor 字符 cap）+ `MIN_SAVINGS_CHARS=460`（256 token × 1.8）；`alreadyCompressed` ⇒ `reason=already_compressed` 不静默 |
| 单测 ×4 | 22 条（SwitchGuardTest 10 + SettledCompressPolicyTest 12），含 floor/ceiling 两条反例锚、交叉态 BLOCKED 优先、`window/2` 兜底可达 |

### 2.3 会话体积通道与 UI 接线
| 落点 | 内容 |
|---|---|
| `core-data/SessionFootprintStore.kt` + `AppContainer` 一行装配 | 进程级字符数快照；**唯一写者** `ChatViewModel`（经 `ConversationOps.observeFootprint` 订阅 `bubbles×compressedUpTo` distinct），`feature-settings` 只读。进程被杀回 0 ⇒ 守卫 `empty_session` VACUOUS 不硬绿 |
| `feature-chat/SessionFootprint.kt` | `committedCharsOf(bubbles, compressedUpTo)` 按 `toHistoryMessages` 重建后求和（正文 + toolCalls + toolResults；附件恒 0 与线上口径一致，KDoc 写明） |
| `ConversationOps.autoCompactOnSettled` | 先 `compressBubbles` 试算（无副作用）→ 字符差 → `shouldApply` → applied 才落 `compressedUpTo` 并记 `auto_compact dropped=N kept=M cache_invalidated=true`；noop 落 `reason` **不静默**；`compressContext=false` ⇒ 两套自动压缩都停、手动压缩仍可用 |
| `SettingsViewModel` | 三形态接线：`setActive`/`setMaxContextTokens` 全守卫（BLOCKED 原因卡不写盘 + 其余二次确认 `confirmSwitch`）；`save` 仅 BLOCKED 硬拦（明文 key 不进 state ⇒ 放行二次确认框裁掉，见 §六-3）；`load()` 预计算 `blockedIds` 供列表置灰 |
| `SwitchDecision.kt` / `SwitchDialogs.kt` / `SettingsRoute` ×3 挂载 / `EndpointListScreen` 置灰 | 纯函数判定层（JVM 可测）+ BLOCKED 原因卡与确认框（文案复用 `chat_compress_warning` 语义）+ 被挡端点按钮「装不下」（点击仍走守卫弹原因卡，不禁用） |

### 2.4 守卫与 CI（W7-04 / W7-07 关闭）
- **G22**：`val x = evaluateSwitch(` 赋值形态 ≥1（剥注释；实摔两次：裸符号会被 KDoc 假绿、`grep -r` 不带 `-n` 时「路径：行号：」剥 admittedly 落空）。
- **G23**：`.autoCompactOnSettled(` 恰好 1 处 + `shouldEvaluate(` ≥1（剥注释；KDoc 提及曾造成 3 处假违规）。
- **selftest** 30/30（变异函数必须定义在执行循环**之前** —— bash 顺序执行，插文件尾 = `command not found`）。
- **W7-04**：8 个 `SmokeTest.kt` 删除 + `EXPECTED_TESTS` 666→**696**（=704−8）+ 注释同步；全量 696/0/0/0、无模块 NO-SOURCE。
- 台账：W6-07 / W6-01 / W7-04 / W7-07（新开，功能预算记账 + 守卫形态订正）。

---

## 三、行数预算：一次性例外（详见 `docs/notes/w7-line-budget-ruling.md`）

`core-context` 1450→**1650**、`feature-chat` 3500→**3900**、`feature-settings` 3000→**3400**；
`TOTAL_LINE_CAP=42500` 未动；+100 拆分开销与 +1000 W7 功能**分开记账**。
裁决文档如实登记：本波**确实调高了 per-module cap**（「总量没涨」不能证明「没调上限」—— 审查席 ⑦-F），
并逐条回应三条替代路径（新模块 / 拆单文件不减总量 / 迁 `:core-agent`）；**不构成先例，W8 起触顶仍须先拆**。

---

## 四、真机验收（A1–A7 四态表，OPPO PDRM00 / nv1(NV-VL) / 32K 档）

> 证据：`_w7/accept-*.png` + `files/harness/logs/agent-0.log` 摘录（正文内）+ `_w7/accept_a*.py` 驱动脚本。
> 驱动坑新增两条：对话框标题与按钮同名时 `find` 会点到标题（W7-07 之外的新坑，驱动脚本一律用「最后一个匹配节点」）；`input text` 单次注入 ~3800 字符即截断（长文本需追加写，且 uiautomator dump 的 text 属性 ~4000 字符封顶）。

| # | 判据 | 四态 | 证据 |
|---|---|---|---|
| A1 | NV 端到端往返 | ✅ | 回复 `PONG-4417`（含思考块）；`1.9k in / 32 out (0 cached, 0.0%)`；分母 `1.9K / 27.9K` 硬预算正确 |
| A2 | 切换确认框（nv→orvl） | ✅ | 对话框：「切换端点或窗口会让提示前缀缓存从头重建，下一轮缓存命中为 0、费用更高」+ VACUOUS 附加「目标窗口尚未经实测验证 `unverified_window char_floor_unmeasured`」；**取消 ⇒ `active_endpoint_id` 读回仍 nv1**；**确认 ⇒ 清写 orvl**；再切回 nv1（现场恢复） |
| A3 | BLOCKED 硬拦 | ⚠️ 部分 | 档位 UI 仅 32K/128K/192K/256K/512K/1M 六档（**W7 用户追加 192K/256K**），无 8K ⇒ 注入 >50k 字符的触发路径成本过高未行使；BLOCKED 分支由 `SwitchGuardTest` 10 条 JVM 判据钉住，A2 已真机行使同一 `evaluateSwitch → SwitchDialogs` 链路 |
| A4 | 自动压缩落账 | ✅（修复后） | `auto_compact=noop reason=below_trigger` 落盘（短会话低于 50227 触发线，noop 是预期且**不静默**）。⚠️ 首跑抓到真 bug：`autoCompactOnSettled` 判 `isStreaming` 早退（`Finished` 时刻 streaming 未清），连 noop 账都不落 ⇒ 已修（KDoc 记录），重编译重装复验通过 |
| A5 | **W7-01** 32K 压缩重放 | ✅ | 5×16KB 并行读场景：最终 **27.2k in ≤ 27904**（余 704）、`上下文 27.3K / 27.9K` 触发「上下文将满」预警；**21680 cached（79.4% 命中）**；`auto_compact=noop` 落账。27.2k prompt 即「5 文件读入」硬证据（新会话题面仅 160 字符） |
| A6 | W7-03 手动压缩双向 | ⚠️ 部分 | 确认框（代价文案）✅；执行返回**无损放弃**（「历史本来就装得下」——估算器把 27.3K 真实上下文估在软预算 20928 内，W6「估算器对代码低估」活案例；noop 分支真机行使）；`dropped=N` 路径与 `cached_tokens=0` 双向未触发 |
| A7 | W5-04 1M 导入行使 | ⛔ vacuous | SAF 系统文件选择器驱动未行使（题面 `import-1m.jsonl` 5 221 133 B 已在设备 Download，通道 W6 已验） |

**A5 附带（W7-02 真机事实）**：`21680 cached / 79.4%` —— 本仓前缀逐字节稳定时，NV nemotron 在真实长会话上的命中率远高于 W6 三连发探针的悲观结论（`0→0→4336`）；「免费池缓存不可依赖」需修正为「**免费池（OR）不可依赖，NV 直连在真实会话形态下高命中**」。 |

---

## 五、多模型矩阵硬结论（详见 `docs/notes/w7-provider-matrix.md`）

1. **1M 档两个真实通过**：nv-vl 995k `ptok=994620 / ctok=187 / finish=stop / 命中` 110.57s；mistral 七档全 ✅、995k 238.69s。**同参数 W6 曾 vacuous（reasoning 吃满输出）⇒ 须重放 ≥2 次才写稳定结论**，判据必须同时登记 `max_tokens=4096 / prompt_tokens / completion_tokens / finish_reason`。
2. **免费池缓存不可依赖**：三连发+改首字节 nv-vl `0→0→4336→0`、dots `0→5120→0→0`、north 恒 0；分块 nv-vl `4336→0→0`、dots `5120→0→5120`(partial) —— 无一满足双向断言 ⇒ W7-02 按「取到事实表」验收，不承诺命中率。
3. OR 网关窗口预检 = **字符数 ÷ 4**（dots 512k/995k 400 拒、算术精确对上；north 256k+ 全拒）；north 128k vacuous。
4. `ds-v41` 600s 复验仍超时 ⇒ 本环境不可用；`step5` tools 502 为瞬时故障（复验 ✅）。
5. 音频输出 ⛔ 不适用（NV 85 模型 0 音频类、`/v1/audio/speech` 404、设备仅 iflytek TTS ⇒ W3-01 维持）。
6. 设备前提：`nv1` 密钥 UI 录入 + 设为当前 + tiny chat 往返自证；`import-1m.jsonl` 5 221 133 B 已推设备。

---

## 六、本波方法论净产出（六条，写给自己）

1. 🔴 **「组件写完 ≠ 组件被用上」在本波第 5 次出现**：`SessionFootprintStore` 落地时零写入点（W6 铁律 3 同型）。守卫 G22/G23 在调用点落盘**前**写必扫空集 ⇒ VACUOUS 棘轮假红 —— 「先落地调用点，再写守卫」。
2. 🔴 **守卫计数必须剥注释，且 `grep -r` 必须带 `-n`**：KDoc 提及方法名 = 3 处假违规；无行号时「`路径:行号:`」sed 剥离整体落空（管道看似在工作，实则在剥空气）。两处都写进 G22/G23 注释。
3. 🔴 **bash 函数定义必须在执行循环之前**：变异函数插文件尾 = `command not found` + 「变异未能注入」双假象。
4. 🔴 **改计数常量必须同一次提交同步全部引用**（审查席 A0：`EXPECTED_TESTS=666` vs 实跑 688 ⇒ CI 必红）；改 per-module 上限必须同步台账/构建脚本注释/方案/提交信息，否则「改了一处留了六处」（A2）。
5. 🔴 **「总量没涨」≠「没调上限」**：`TOTAL_LINE_CAP` 只约束总量；调 per-module cap 必须出显式裁决文档并声明不构成先例（⑦-F 读错对象）。
6. 🔴 **并行写入要指定唯一写者**：两个 agent 同时写 `SwitchGuard.kt` 互相覆盖 3 次；主理人接手后按文件域独占收敛。续派成员时把「最终口径」压缩成**一条**消息下发（时序差使旧消息失效）。

## 七、挂账与移交 W8

- **本波关闭**：W6-07（拆分）、W6-01（`compressContext` 全链接线订正）、W7-04（占位删除）、W7-07（预算记账 + 守卫落地）。
- **本波推进**：W7-01/02/03/05/06 → 见 §四 验收结果；W5-04（1M 设备侧）随 A7。
- **新开**：W8-01 会话级压缩的「尾部追加摘要」形态（本波裁 (a) 的后续）；W8-02 三桶归一（`cache_write` 独立字段，届时必须补含 write 的 fixture）；W8-03 `EngineCapabilities` / `autoApproveDangerous` 二选一（接线或删，原 W6-02/W5-05 限期）；W8-04 编辑当前端点「放行二次确认框」（密钥不进 state 红线下缓议，台账已开立）。
- **维持**：W5-03 MCP cleartext 平台阻塞；W3-01 speak 裁决关闭；W3-05 journal/FreezeClock 建议独立波。

## 八、下一波开工指引

1. 起手读：本文件 → `docs/notes/w7-line-budget-ruling.md` → 台账 W7-07 → `docs/notes/w7-plan.md` §10。
2. 真机复放 W7-01/02 时直接复用 `_w7/device-acceptance.md` 的驱动记录与 `_w6/ui.py`。
3. 缓存工程主战场 = NV nemotron（1M、可缓存、写入延迟 ≥2 次）；GLM/免费池不作承诺。
4. 行数再触顶：**先拆**（优先 `:core-agent` 余量方向或真新模块），W7 的一次性例外不得引用。
