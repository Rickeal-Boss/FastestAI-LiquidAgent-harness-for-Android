# W10 交接 — W9-01 主体（压缩量纲统一 + 尾部摘要前置）+ 多模型免费节点矩阵（2026-11-07）

> 基线 `fc841f8`（W9-01「0 车」快修已推，EXPECTED_TESTS=770）；工作树 18 文件改动 + 新增 `docs/notes/w10-plan.md`（未提交）。
> 派单口径 `docs/notes/w10-plan.md`；上游依据 `docs/notes/w9-wave-plan.md` + 用户新输入（免费节点 key + 至 1M 上下文 + 功能矩阵）。
> 提交身份：`-c user.name="Rickeal-Boss" -c user.email="263587600+Rickeal-Boss@users.noreply.github.com"`（用户 2026-11-07 显式指令，与 AGENTS.md §九一致；知识库 §5 已回写订正）。
> 🔴 **本文件性质 = W10 跑后版（W11 第 0 车回填，2026-11-07 晚）**：审查席 3 项「重要」已处置（2 修 1 顺延）；**已提交 `98916eaa`、已推 `main`、CI run `42365515962` 五 job 全 success**。原「进行中快照」抬头与 §一「推送/CI ⛔ 未做」是**当时事实**，跑后结论见 §九；历史文本保留不抹，按「回填不篡改」纪律处理。
> ⚠️ 引用纪律：本文件所有 `文件:行` 已回现役工作树 grep 复核；「未触发」≠「已验证通过」；四态 `✅/⚠️/⛔/⛔vacuous`。

---

## 一、本波结论（跑后回填，未完成的如实标注）

| 项 | 结论 |
|---|---|
| W9-01 主体 S1..S4 | ✅ 全部落地（主理人代行开发席——开发席 2 次 429 配额耗尽，按知识库 §6 接手） |
| S1 量纲同源 | ✅ `TokenEstimator.CHARS_PER_TOKEN_ASCII` 转 public + 双尺契约 KDoc + `BudgetFormulaTest.hardGateFloorNeverExceedsEstimatorRuler` |
| S2 触发线按档位 | ✅ `BudgetFormulaTest.softBudgetScalesWithWindowTier`（32k→20928 / 128k→92928 钉死） |
| S3 尾部摘要前置 | ✅ `ManualCompress.kt` 重写为「保头锚 + 砍中段 + 尾部追加机械摘要」（`SessionCompression`）；全链路迁移；`cache_invalidated true→false` |
| S4 缺口1a 附件进预算 | ✅ `estimateMessages` 计入 `sendableAttachments`（图 1024 经验值/文本同尺）；`renderHistoryHead` 不进指纹（回归钉） |
| @Test 对账 | ✅ 严格口径 **778** == `build.yml EXPECTED_TESTS`（'770'→'778' 一次改，主理人独占） |
| G1 出包 | ✅ `BUILD SUCCESSFUL in 23s`，`app-debug.apk` = **70,309,520 B** |
| G2 全量单测 | ✅ **tests=778 failures=0 errors=0 skipped=0**（81 XML / mtime 全本轮）；审查修复后**复跑再确认**（XML 聚合 81/778/0/0 + gradle BUILD SUCCESSFUL） |
| G3 架构守卫 | ✅ 复跑 **PASS（OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0）**；R2 实验期一度 G10 爆（3922>3900，见 §四），回退后复跑通过 |
| G4 守卫自证 | ✅ **selftest 30/30 PASS（210s）**，变异注入真判红（G17-G23） |
| 审查席 | ⚠️ 3 重要：R1 已修 / R2 顺延 / R3 已修（详见 §四） |
| 真机 | ⚠️ 装包+冷启动 ✅；M1 往返题面已发但 journal 取证未闭环；M8/M9/M10 设备侧未行使 |
| PC 侧矩阵 | ✅ 见 §三（M8 双向正向证据到手） |
| 推送 / CI | ✅ 跑后回填：已推 `98916eaa`；CI run `42365515962` 五 job 全 success（见 §九）。写本行时为 ⛔ 未做，属当时事实 |

**本波不构成「已验证通过」的项**：真机 M8/M9/M10（未行使）、R2 分隔卡（顺延）、1M 稳定性（PC 侧 nv-vl 1/2 不稳、mistral 超时）。推送 / CI 已于跑后闭环，不再列为未验证项。

---

## 二、改动统计（git diff，18 文件 +413/-133 基线；审查修复后微调）

**core-model**（余 517）
- `TokenEstimator.kt`：`CHARS_PER_TOKEN_ASCII` private→**public**（估算面唯一真值源）+ 双尺契约 KDoc（估算面 4.0 vs 硬闸面 floor 1.8，不变量 `hardCharsPerToken ≤ CHARS_PER_TOKEN_ASCII`）；`estimateMessages` 增 `sendableAttachments` 遍历（TEXT 走 `estimate(textContent)`、其余按 `ESTIMATED_IMAGE_TOKENS=1024`）；`estimateToolSpecs` 不变。
- `TokenEstimatorTest.kt`：+3（`imageAttachmentCountsIntoBudget` 红态不引新常量、`textAttachmentCountsByContent`、`unsendableAndModelAttachmentsDoNotCount`）。

**core-context**（余 62）
- `BudgetFormulaTest.kt`：+2（S1 契约 `hardGateFloorNeverExceedsEstimatorRuler`：floor 1.8≤4.0 且 `charCapOf(20000,1.8)` 字符按估算尺折回 ≤20000；S2 `softBudgetScalesWithWindowTier`）。
- `PromptPrefixTest.kt`：+1（`historyHeadIgnoresAttachmentBytes` 回归钉：同文本不同图 ⇒ `renderHistoryHead` 逐字节相同）。
- `SettledCompressPolicy.kt` / `SwitchGuard.kt`：KDoc 对齐 compression 新语义（R3）。

**core-data**
- `SessionFootprintStore.kt`：KDoc `compressedUpTo`→`compression`。

**feature-chat**（余 27，⚠️ 偏紧）
- `ManualCompress.kt`：**S3 核心**。新增 `SessionCompression(headBubbleCount, droppedFromId, droppedToId, summaryText, droppedBubbleCount, savingTokens)`、`HEAD_ANCHOR_BUBBLES=4`、`SUMMARY_TOKENS_RESERVE=64`；`compressBubbles` 改「头锚 take(4) + 候选压 + 无损校验（保留段必为候选后缀）+ 尾部追加机械摘要 `[context summary] N earlier bubbles compressed (...)`」；`ManualCompressOutcome.compressedUpTo`→`compression`。
- `ChatUiState.kt`：`compressedUpTo`/`compressedSavingTokens` → `compression: SessionCompression?`；`compressedBubbleCount` 由 `[droppedFromId..droppedToId]` 派生。
- `ChatHistoryMapper.kt`：`toHistoryMessages(fromId)` → `toHistoryMessages(compression)`：头锚 subList(0,4) + 尾部 + 尾部追加摘要 SYSTEM；失效压缩区回落全量。
- `ConversationOps.kt`：`compressContext`/`autoCompactOnSettled`/`undoCompress` 迁移；两处落账 `cache_invalidated=true`→**false**（undo 仍 true）；KDoc 对齐（R3）。
- `ChatViewModel.kt`：send 路径 `toHistoryMessages(snapshot.compression)`。
- `ChatMessageList.kt`：参数 `compressedUpTo/compressedSavingTokens`→`compression`；分隔卡计数/省 token 取自 compression。
- `ChatScreen.kt` / `SessionFootprint.kt`：签名迁移。
- `ManualCompressTest.kt`：重写 +2 净增（`headAnchorIsByteStableAfterCompression` 含 `renderHistoryHead` 内容段比对、`summaryDoesNotEchoDroppedBodies`）；预算 50→200（头锚=4 后旧 50 触发 noop，红态实证）。
- `SessionFootprintTest.kt`：`compression` 语义（压缩区排除 + 摘要追加计入）。

---

## 三、PC 侧多模型免费节点矩阵（`_w10/`，关键计数抄入正文）

**端点**：OR `dots-studio/dots-3-note-preview:free`、`cohere/north-mini-code:free`；NV（`https://integrate.api.nvidia.com/v1`，**非 build.nvidia.com**）`nvidia/nemotron-3.5-super-vl-120b-a12b`、`mistralai/mistral-large-4-1t-a52b`、`stepfun-ai/step-5-preview`。排除：`deepseek-ai/deepseek-v4.1-flash`（超时复证）、`wan2.2-animate`/`vsr`（非 chat 404）。

| 项 | 结果 | 四态 |
|---|---|---|
| 判据自证 | nv-vl/dots/north `hit=True finish=stop`（max_tokens≥512 前置） | ✅ |
| chat/tools 通道 | 5 模型 chat+tools 全通 | ✅ |
| vision | nv-vl、dots 双通（200×120 红蓝，禁 1×1） | ✅ |
| **M8 KV cache 双向** | **nv-vl：0→4336→4336→变异首字节 0**；**mistral：0→6208→6208→0** | ✅ 双向正向 |
| M8 step5 | same-2/3=5376，首/变异=None（字段缺失非 0） | ⚠️ 部分 |
| M8 OR 免费池 | dots 仅 same-3 偶发 5120、north 恒 0 ⇒ 反向分支不可执行 | ⛔vacuous |
| 长程阶梯 | nv-vl/mistral 4k–512k 全 ok；step5 4k–128k ok；dots 4k–128k ok、**512k HTTP 400**（免费池拒绝）；north 4k/128k ok、32k vacuous | ⚠️ 部分 |
| **1M 档** | nv-vl 窗口吃满 `ptok≈999k` 但取针 **1/2 不稳**（首放 hit=true、重放 hit=false，均 finish=length）；mistral 1M **900s/1500s 双超时** | ⚠️ / ⛔ |
| cached_tokens 观测 | 四元组 `max_tokens/prompt_tokens/completion_tokens/finish_reason` 逐请求落 `_w10/requests.jsonl` + `raw/` | ✅ |

🔴 **PC 侧结论不得写成设备侧可用**；1M 档「≥2 重放稳定」判据 nv-vl 未满足（1/2）、mistral 未满足（双超时）⇒ 如实 ⚠️/⛔。

---

## 四、审查席结论与处置（严质衡，基线 fc841f8，只审未改）

**总体：需修改（⚠️ 部分）**。已逐条处置：

| 编号 | 问题 | 处置 | 四态 |
|---|---|---|---|
| R1 [重要] | `HEAD_ANCHOR_BUBBLES=2` < `HISTORY_HEAD_SIZE=4` ⇒ 压缩后 `renderHistoryHead`（k=4）非逐字节稳定，缓存命中窗被截 | **已修**：头锚提到 **4**（=k，4 气泡≥4 消息）；测试补 `renderHistoryHead` 内容段比对 | ✅ |
| R2 [重要] | 分隔卡固定置顶，但收起视图=头锚+尾部 ⇒ 卡与压缩区边界错位 | **顺延**：修法（卡插头锚后）把 `ChatMessageList()` 推过 G11 上限 120（137，需拆子组件+下沉）；G10 实验 3922>3900。按「触顶先下沉、UX 顺延」记为下一车待办；已留 KDoc 标注 | ⛔ 顺延（非 vacuous，UX 项） |
| R3 [重要] | `ConversationOps`/`SettledCompressPolicy`/`SettledCompressPolicyTest` KDoc 仍写 `cache_invalidated=true`/「单指针」/「前缀首字节变化」，与代码冲突 | **已修**：三处 KDoc 对齐 compression 新语义（证据链措辞逐字核） | ✅ |
| R4 [建议] | `SettledCompressPolicyTest` 残留 `compressedUpTo` KDoc | 已随 R3 清理 | ✅ |
| R5 [建议] | headAnchor 测试只比 2 条 `.text` 偏弱 | 已加强为 `renderHistoryHead` 内容段比对 | ✅ |
| R6 [建议] | 尾部追加 SYSTEM 摘要在 Anthropic 族可能需顶层 system 字段 | 设计为方案 §二 所定；**真机 M9 待确认**所测端点接受尾置 SYSTEM | ⛔ 待真机确认 |

**红态实证（测试自己抓到的回归）**：头锚 2→4 后 `budget=50` 触发压缩 noop ⇒ 测试红；`renderHistoryHead` 断言首版被 `message.id` 波动误判 ⇒ 改内容段比对。两处均为红态样本先行纪律的实证。

**🔴 新观察项（非 S3 引入，记入下一车）**：`ChatBubble.toHistoryMessages()` 每次重建生成**新随机 `message.id`**，而 `renderHistoryHead` 行含 id ⇒ 本地前缀指纹（`PromptPrefix`）跨重建不稳定。PC 侧 M8 正向用的是稳定内容直连；**设备侧前缀缓存是否受 id 波动影响待真机 M8 复核**。若设备侧 `cached_tokens` 恒 0 而内容稳定，则 id 进请求体是根因（需单独挂账）。

---

## 五、硬约束（下一车必守）

1. **行数**：feature-chat **3873/3900（余 27，⚠️ 偏紧）**、core-context 1588/1650、core-model 2083/2600。🔴 **禁调 `LINE_CAPS`/`TOTAL_LINE_CAP=42500`/`VACUOUS_BASELINE`**；触顶先下沉（core-model 余 517 / core-engine / core-data）。R2 落地前必须先拆 `ChatMessageList` 子组件。
2. **DI 只走 AppContainer**；core-agent 不碰 litertlm；不引入注解处理器/Retrofit/图片库。
3. **许可证**：Apache-2.0；Operit LGPL / Operit2 AGPL ⇒ 零代码搬运。
4. **图片红线**：算进预算、放尾部、**不进 `PromptPrefix` 指纹**；别为凑缓存加长 system。
5. **压缩不变量**：保头锚字节稳定（≥4 消息覆盖 k=4 窗）；摘要只追加尾部；`SettledCompressPolicy` 契约（未压轮次前缀严格单调）保留。
6. **EXPECTED_TESTS 单一写口**：任何 @Test 增删同提交改 `build.yml:132`（现值 778，主理人独占）。
7. **凭据**：PAT 只 URL inline 一次性、绝不落盘；免费节点 key 从用户文件读入内存注入。

---

## 六、环境坑位（本波实证）

- **子 Agent 429**：开发席/方案席 spawn 后 10–18s 即 429 配额耗尽；审查席正常跑完（13m27s）。按知识库 §6：429 ⇒ 主理人接手补完，**不重试 spawn**。
- **沙箱 adb**：daemon 每命令后退出 ⇒ adb 命令串行、同 Bash 调用连发；`MSYS_NO_PATHCONV=1`；`adb -P 15037`（默认 5037 无设备）；`exec-out` 读二进制。
- **真机 OPPO A13/PDRM00**：`adb push` + `pm install -r`（直接 install -99）；launcher = `com.rickeal.liquidharness.MainActivity`（非 `.debug.MainActivity`）；`input text` 空格用 `%s`；题面 ASCII。冷启动 pid 存活 + logcat 无 FATAL 为判据。
- **真机 UI 读屏**：主模型收不了截图 ⇒ 用 **NV-VL 免费节点**做视觉描述（复用矩阵通道，返回元素+坐标）。
- **arch-guard 沙箱**：前台跑偶发 120s SIGTERM 空输出 ⇒ 后台跑重定向文件再读。
- **CRLF**：Write/Edit 在 Windows 可能引入 CRLF；提交前对改动 `.kt` 批量 LF 归一（本波已做 4 文件）。
- **G11 方法体积**：`ChatMessageList()` 上限 120，加压缩视图逻辑极易触顶；拆分需同步考虑 G10 模块余量（仅 27）。

---

## 七、待办（下一车开工指引）

1. **G2/G3 复跑回填**：后台 fulltest + arch-guard 跑后把结论填 §一（预期 tests=778 全绿、arch-guard PASS；R2 已回退）。
2. **提交**：LF 归一 → `git status` 确认无 `_w10/` 取证夹带（`.log` 已 gitignore，`.png/.json` 需确认）→ 细粒度提交（S1/S2/S4 量纲 + S3 尾部摘要 + 矩阵文档，可分 2-3 commit）→ push dry-run → 实推 → CI 五 job 全 success。
3. **台账回填（N2 人工闸门）**：`W9-01` 主体完成度回填；R2 分隔卡顺延、id-波动观察项 **新开台账行**（编号待 N2 拍板，本文件不预铸未登记 W-ID）；`W9-03`（尾部摘要形态）与 W9-01 同车完成，交叉引用。
4. **真机矩阵行使**：M1 多模型往返（journal 取证闭环）；D6/D7 图片闸门回归；M4/M5/M6 回归；**M8 设备侧**（重点复核 id-波动对 cached_tokens 影响）；M9 手动压缩（确认端点接受尾置 SYSTEM，R6）；M10 自动压缩。取不到如实 ⛔vacuous。
5. **R2 分隔卡**：先拆 `ChatMessageList` 子组件（`chatRows` 抽取路线已验证可行但需同步 G10 余量），卡移到头锚之后。
6. **1M 稳定性**：nv-vl 1M 取针不稳、mistral 1M 超时——如需稳定结论，换更长超时窗口或分块重放（免费节点限流下性价比低，可暂记 ⚠️）。

---

## 八、本车未做、明确不做（防误读为漏项）

- MCP 真机联调（`usesCleartextTraffic=false` 平台阻塞）；设备侧 1M（双内核阻断，W9-11 未解锁）；音频输出（NV `/v1/audio/speech` 404 平台阻断）；任何新 arch-guard 守卫或 selftest case（本波零新增）；`feature-settings` 零改动（余 8，W9-07/12 开工前先定拆分）。
- 推送与 CI（本波未 commit；N2 未过，不代做）。

---

## 九、W11 第 0 车跑后回填（2026-11-07 晚，主理人）

> 本节是**跑后订正**，§一/§七 的「未提交 / 未推 / 未取 CI」是写交接当时的真实状态，按
> 「回填不篡改」纪律保留原文并在此给出结论。行号一律锚 `HEAD:98916eaa`。

| 项 | 跑后事实 | 判据 |
|---|---|---|
| 提交 / 推送 | `98916eaa feat(w10)…关 W10 波` 已在 `main` | GitHub API `branches/main` 返回同 SHA，本地 `rev-parse HEAD` 一致（不只看 `origin/main` 本地 ref，它陈旧停在 `767fd47`） |
| CI | run `42365515962` → `completed / success`；前一次 `42249451709` (`fc841f8`) 亦 success | GitHub API `actions/runs`；五 job 全绿 |
| G1 出包 | `BUILD SUCCESSFUL`，`app-debug.apk` 70,309,520 B | W10 §一 |
| G2 全量单测 | `tests=778 failures=0 errors=0 skipped=0`（81 XML） | W10 §一（审查修复后复跑再确认） |
| G3 架构守卫 | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0` | W10 §一 |
| G4 守卫自证 | selftest 30/30 PASS | W10 §一 |
| 真机 M1 / M8 / M9 / M10 | **仍未闭环**：装包冷启 ✅、M1 题面已发但 journal 未闭环，M8/M9/M10 未行使 | 保持 ⚠️ / ⛔vacuous，不因 W10 已推而升级 |
| 台账回填 | W9-01（补完一处仍 `OPEN`）/ W9-03（形态拆清仍 `OPEN`）/ W9-04（W11 关闭）/ W9-05（第 7 次顺延，如实）已落 `06-deferred-ledger.md` | 台账 §二-bis；G20 |
| 新开挂账 | `W11-01` R2 分隔卡 / `W11-02` id 假漂移（W11 已闭）/ `W11-03` `cache_invalidated` 二值过度声明 | 台账 §二-ter |

**本波仍未做（防误读）**：W9-05 journal/FreezeClock 开波；设备侧 1M 真压；MCP 明文联调；
音频输出（W11 探活席复证 `https://integrate.api.nvidia.com/v1/audio/speech` = 404）；
R2 分隔卡；尾置 SYSTEM 摘要的端点接受性（R6）。以上在 W11 handoff 逐项给四态。
