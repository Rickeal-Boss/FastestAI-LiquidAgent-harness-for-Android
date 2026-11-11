# W17 交接 — SkillMention 缺陷收口批 + W16-18 droppedBlank 定谳 + 多模型×多档位功能矩阵（2026-11-12）

> 基线 `234eac0`（W16 收尾，`main`）。
> 上游依据：用户 2026-11-12 指令（读记忆 / 上一波 handoff / OPEN 挂账行 / 七份 source-review /
> 众审查人原始报告并裁决分歧、不改方案大体方向；多免费节点模型 × 多上下文直至 1M 功能面测试；
> 先本地 UI + adb 调试 → 真机 → 推仓库及 CI；按收敛结论实施；零凭据字面量；编译失败按四分类归因；
> 本次任务停于交接）。
> 判定四态 ✅ / ⚠️ / ⛔ / ⛔vacuous。**「未触发」≠「已验证通过」；「测试全绿」≠「口径被验证」；
> `rc=0` ≠ 产物已重生成；PC 侧结论不得写成设备侧可用；1M 读数 ≠ 1M 真压。**
> 密钥纪律：端点凭据**只在设备侧**，不进仓、不进日志、不进截图、不进交接、不进记忆；
> 本单 / 工具 schema / 工具结果 / 日志 / 截图 / 交接 / 记忆写入**零凭据字面量**。
> 完整版见 `docs/analysis/w17-acceptance.md`；本文件只做交接摘要，**冲突时以验收物为准**。

---

## 一、本波定位与裁决收敛

W17 = **W16 追加功能波的缺陷收口批（四份外审与日析独立交叉命中，一次收口）+ W16-18 代码侧定谳 +
多模型×多档位功能矩阵真机测试**。不是 journal 波、不是拆分整包、不是安全收口。

**收敛结论（唯一目标以此为准，完整版见验收物 §1.2–§1.4）**：

1. **用户指令面优先**（沿用 W14/W15/W16 三波同款裁决）：外审还债排期不取消、不推翻、整包顺延。
2. **众审查人分歧逐条裁决**（验收物 §1.4）：邮箱误注入定级分歧不影响处置（必修）；`/name` 直输修法
   采**归一**（正则扩 `[@/]` 会误提取正文路径）；轮间前缀分叉修法采**气泡存装饰版 + 显示剥引导**
   （重放方案对引导文案版本敏感）；W16-18 两外审归因**互补不矛盾**（夹具 `content` 字段名 →
   解析器 Q3 空文本静默跳过），两案并采。
3. **一条外审发现回源证伪**：w12-16 审查报告「发现 A：`enable_thinking` 无条件 true」——现役
   `OpenAiCompatibleAdapter.kt:240` 是 `when (endpoint.reasoningStyle)` 的 `ENABLE_THINKING_BOOL`
   分支，端点显式配置该 style 才发，非无条件；不采纳、不修、裁决留痕（与 W16 证伪外审
   「`parallel_` 笔误键」同款纪律）。
4. **四项用户裁决不代做**（W9-05 / ≥3 端点 / §1.5 占位符 / P2-3）——`W9-05` 第 12 次顺延如实登记（W17-04）。

## 二、代码改动

本波为**改 `.kt` 的功能收口波**。变更面：

| 文件 | 性质 | 说明 |
|---|---|---|
| `core-agent/.../skill/SkillMention.kt` | 改 | `decorate` 白名单签名（只装饰已知 skill 名）；新增 `normalizeLineCommand`（行首 `/token` → `@token`）、`applySkillPick`（只截开头触发 token）、`stripDecoration`（剥引导）；`GUIDE_SEPARATOR` 常量同源 decorate/strip |
| `feature-chat/ChatSkillInput.kt` | 新 | 自 ChatViewModel 外提（499/500 触 G10 单文件闸，先例 `ConversationOps`）：目录加载（CE 重抛 + 失败 Log 可观测）/ 发送侧装饰 / 显示剥引导 |
| `feature-chat/ChatViewModel.kt` | 改 | init 外提为 `ChatSkillInput.loadSkillNames`；`send()` 落气泡与上 wire 用**同一份装饰后文本**（wire = 存储，跨轮请求体逐字节稳定，外审 N1/P2-1）；`runOnce` 透传不二次装饰 |
| `feature-chat/ChatScreen.kt` | 改 | `onPickSkill` 改调 `SkillMention.applySkillPick`（正文含邮箱时不再被 `lastIndexOf` 砍掉，外审 A3/P1-1） |
| `feature-chat/ChatInputBar.kt` | 改 | 下拉显式 `skillMenuDismissed` 状态 + `onDismissRequest` 落地（外点可关闭，外审 A4/SM-1）；类头 KDoc 订正「附件入口已接线」（CI-1，Q7 家族第 4 例） |
| `feature-chat/ChatBubble.kt` | 改 | USER 气泡显示走 `ChatSkillInput.displayText`（剥引导还原原文；MODEL/SYSTEM 不动） |
| `feature-chat/ManualCompress.kt` | 改 | `summaryOf` 去 snippet（摘要不回显被压段原文片段，外审 MC-2：片段可能是密钥形态或注入指令）；类头 `:30` 括注订正（发现 B） |
| `core-data/SessionImportParser.kt` | 改 | `Result.Ok` 增 `droppedBlank` 字段：JSONL 空正文行与 markdown 空段**计数后跳过**（不再静默，W16-18 定谳）；类头「两条不静默」改「三条」 |
| `feature-settings/SessionImportRow.kt` | 改 | 上屏 `droppedBlank`（新文案 `settings_import_session_ok_blank`）；feat-settings 余 8 行约束下净增 ≤2 |
| `.github/workflows/build.yml` | 改 | `EXPECTED_TESTS` 804→818（SkillMentionTest +11 / SessionImportParserTest +3），同提交一次改 |
| `docs/06-deferred-ledger.md` | +6 行 | §二-septies `W17-01..05`（全 OPEN，触发信号 + 缓解动作）+ `W16-18` 行补 W17 进展 |
| `docs/handoff-20261111-wave16.md` / `docs/analysis/w16-matrix-acceptance.md` | 抬头 | 外审 P0-1：主体章节加「结论截至 `f78cb2a` 时点；`51be3f7` 之后见 §九」时点限定（只加抬头不改历史正文） |
| `docs/analysis/w17-acceptance.md` | 新 | 本波验收物（§1 范围 / §2 闸门 / §3 真机 / §4 迭代 / §5 收尾） |
| `docs/handoff-20261112-wave17.md` | 新 | 本文件 |

**行数纪律**：`LINE_CAPS` / `TOTAL_LINE_CAP` / `VACUOUS_BASELINE` 全部不动（与 W16 的「裁决抬 cap」不同源，
本波触顶处置 = 裁 `ChatSkillInput.kt` KDoc 从 52 → 44 行，arch-guard G10 复跑 PASS）。
`ChatViewModel.kt` 500/500（触线但合法，本波外提后**零净增**）。

## 三、本地闸门

| 闸门 | 实跑 | 四态 |
|---|---|---|
| G1 出包 | 首轮 UP-TO-DATE ⇒ 删旧 APK 强制重出包 ⇒ `68,407,402 B`（mtime 本轮，非旧值 68,390,522） | ✅ |
| G2 fulltest | `tests=818 failures=0 errors=0 skipped=0`，14 模块；`EXPECTED_TESTS '818'` 同提交 | ✅ |
| G3 arch-guard | `PASS ok=27 vacuous=1 violation=0`；G20 105 ID 全在台账；G10 一次触顶经裁 ChatSkillInput KDoc 后 PASS（**未调 LINE_CAPS**） | ✅ |
| G4 selftest | `30/30 PASS（251s）` | ✅ |

环境级两处如实登记（非四分类代码问题）：fulltest 前两轮 Windows 页面文件 2.4 GB 承不住 Gradle 4 GB 堆 commit（`DOS 1455`）⇒ 临时降配跑通后立即还原；Compose 编译器一次瞬态 back-end error 重跑即过。

## 四、真机功能矩阵（NV mistral-large × 32K 一格；多端点/档位矩阵未铺满 = ⛔vacuous）

| 功能面 | 结果 | 四态 |
|---|---|---|
| `@` 引用 → 下拉 → 选中回填（单前缀、A4 关闭） | 真机 dump 取 bounds 点选，草稿 `@notes-organizer ` | ✅ |
| `/name` 归一（P0-3/P1-1） | JVM 15 条红态全绿 + 真机发送链 | ✅ |
| skill 端到端 skill_load body 加载 | 模型精确复述 skill 正文四分组名（Undated / Decisions / Open questions / Action items）；直接工具卡取证受 UI 滚动驱动限制未取 | ⚠️ |
| 工作空间 file_write 落盘 | 审批卡行使 ⇒ `wrote 9 chars` ⇒ 沙箱核验 9 B 内容正确（W16-11 落盘未证实项闭合） | ✅ |
| file_list | 工具卡 `file_list / 28ms` | ✅ |
| W16-18 导入定谳 | 300 条夹具重导 ⇒ 气泡滚至 `Segment 0299` ⇒ 发送触发 `context_overflow_prevented: chars=249801 cap=50227` ⇒ 上波「只载 1 条」未复现（定谳：取证驱动/流程伪影） | ✅ |
| 手动压缩 | 确认框（W7-03 判据行使）⇒ `manual_compress dropped=238 kept=64 cache_invalidated=false`（dropped=N 路径闭合，与 W14 同读数） | ✅ |
| 多模态图片输入 | SAF 选 459 B 双色图 ⇒ 模型回复「The image shows a horizontal bis(ector)…」vision 通 | ✅ |
| 缓存命中 | 81.8% / 97.4% / 78.9% 多轮 | ✅ |
| 多端点 × 多档位矩阵 | 只跑一格 | ⛔vacuous |

## 五、链内迭代记录

| 轮 | 事件 |
|---|---|
| 1 | 四模块单测 2 条 applySkillPick 失败（期望单空格、实得双空格）⇒ 改实现（余段自带前导空白时不补） |
| 2 | Compose 编译器一次瞬态 back-end error ⇒ 重跑即过，不改码硬凑 |
| 3 | fulltest 前两轮 `DOS 1455` 页面文件不足 ⇒ 临时 `-Xmx2g`+`parallel=false` 跑通后立即还原 |
| 4 | G10 一次触顶（feature-chat 4004>4000）⇒ 裁新文件 KDoc 52→44 行 ⇒ PASS |
| 5 | G1 首轮 UP-TO-DATE ⇒ 删 APK 强制重出包 |
| 6 | 真机裸 adb 注入连环失败 ⇒ 定位 `adb shell` 设备端 sh 重新分词（`%s` 后每段被当独立参数丢弃）⇒ 改单引号包裹一次成功（与 AGENTS「adb 拼参吃引号」铁律同源） |
| 7 | skill_load 直接卡取证受 UI 滚动驱动限制 ⇒ 触顶止损改正文级间接证据路径 |

## 六、未做 / 明确不做

逐条带挂账号，见验收物 §1.3（12 条）与 §5.1（13 条，含本轮新识别 W17-06..09）。核心：journal 二选一仍待用户裁决（W17-04 登记，
第 12 次顺延）；拆分三方整包 / 安全波 / 六行漏号补登 / 1M 真压 / MCP 明文 / git 历史清理均顺延；
`HB-2` 因 core-context 余 4 未动（W17-01）；`SM-3` 目录刷新登记防漏（W17-02）；`MC-1` 尾置 SYSTEM
Anthropic 风险登记（W17-03）；发现 A 回源证伪不立账。

## 七、挂账状态（唯一真值源 = `docs/06-deferred-ledger.md`）

| ID 段 | 状态 | 本波动 |
|---|---|---|
| `W17-01..05` | `OPEN` | 本波新开 §二-septies，每行触发信号 + 缓解动作 + 证据 |
| `W16-01..18` | `OPEN`（原样保留） | 仅 `W16-18` 证据列补 W17 定谳进展（300 条全物化 + `chars=249801` 熔断 ⇒ 上波「只载 1 条」未复现，判取证伪影），不改状态 |
| `W14-01..11` / `W15-01..14` | `OPEN`（原样保留） | 不删、不改状态、不写已核销 |
| `W9-05` | `OPEN` | 第 12 次顺延由 `W17-04` 登记（两口径并列待裁决）；老行不改 |

## 八、移交外部审查

（待回填：CI run 链接 / 提交序列 / 可审查制品清单）

**本节不写下一波候选入口。本波止于交接。**
