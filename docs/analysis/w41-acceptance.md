# W41 验收物 — 限流分类补全 / 四态 usage 同源 / 思考框实底 + 真机矩阵入仓

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `c4fc2cf7f8d6a7b0ef1dab84eebf9202fb25c60d`（W40 收尾订正）→ 代码主体 `93808be874c1fb6dcc64a7023154c88f9fbeb367`（feat，本波唯一代码提交）→ 证据 `757b28db3fc3f234118339c07f99726d36e4e6d5` → tip `3637057617f4c22207412c36dda0033866398412`（W42 补录完整 40 位 SHA）。
> 守恒式两数只写在派单卡抬头；本文件不复述。判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」。
> 回灌级别：脱敏 UI dump 文本、思考框裁剪截图、`deviceLogTail`；不回灌整段 logcat / 凭据 / 未打码账号 / 设备序列号 / 本机路径。

---

## 收尾契约 · 1→5 各步结果

### 步 1 · 读记忆 + 上一波 handoff + OPEN 行 ✅

- 证据路径：`docs/handoff-20261121-wave40.md`（W40 全篇）、`docs/06-deferred-ledger.md`（216 行，权威版）、
  七份外部审查报告（会话附件，只读分析、不入库）、`docs/analysis/w40-acceptance.md`。
- 现役仓边界：只读 `harness-v2/docs/`，未读旧仓；基线 `c4fc2cf` 与远端一致；工作树仅有历史仓外取证目录 untracked。
- 分歧裁决（主理人，按用户「全面推进」指令）：
  ① 三份报告要求「新开重试入口体验债」不采纳 —— W40 已裁决 `W24-02` 案 A 不回开，剩余两条维持 CLOSED 行已知边界；
  ② 1M 真压双硬限注入 / MCP 外部 https / journal 二选一继续不自动解禁，本波只做**测试与观察**，不做机制改造；
  ③ 真机制品连续两波缺席按最高优先修 —— 本波 `evidence/w41-matrix-summary.json`（34 键冻结）+ `evidence/raw/w41/` 入仓；
  ④ P0-2 采「无结构化字段时限流/服务端措辞先于超窗词表」+ 数字 `code` 兜底；P0-3 采「真实 usage 到达标志」而非累计 token 反推。
- 裁决写成事实前的回源：三条错误帧必红样本在**未修版本**实跑 RED（断言逐字见步 3），估算 usage 必红样本临时回退修复后实跑 RED；未以报告措辞替代现役源码。

### 步 2 · 第 0 车账实订正 ✅

| 动作 | 结果 | 证据路径 |
|---|---|---|
| `W24-02` 行尾「未做」矛盾句删除、显式 LENGTH 端到端用例如实未落位 | ✅ | `docs/06-deferred-ledger.md` W24-02 行 `W41 行内注记`；W40 段边界 4→2 同步 |
| `W12-02`「同源归一」过度声明作废、改记真实口径 | ✅ | 同上 W12-02 行 `W41 行内注记` |
| `W9-01` 命题②落地但余面未做；`W20-02` 余量收窄 | ✅ | 同上两行 `W41 行内注记` |
| W40 收尾契约三处（tip 时点 / 2 次推送 / 15 唯一文件）+ PAT 表述冲突 | ✅ | `docs/handoff-20261121-wave40.md`、`docs/analysis/w40-acceptance.md` |
| A5 注释 11→12、E5b 断言收紧、`ConversationOps` KDoc 按现役 `EventReducer.finish` 实态订正、清死 import | ✅ | 对应 `.kt` diff（`93808be`） |
| 判据 7 解析规则写进 checklist 现有段（不新增编号、不新建摘录段） | ✅ | `docs/notes/w29-closeout-checklist.md` W41 边界说明；两数只在派单卡抬头 |
| 判据 1/6/7 正控 + 负控 | ✅ | `evidence/raw/w41/k1_ok.txt`、`k1_negative.txt`、`k6_ok.txt`、`k6_red.txt`、`k7_ok.txt`、`k7_negative.txt` |

### 步 3 · 第 1 车代码面 + 四闸门 ✅

| 项 | 改动 | 判据 / 证据 |
|---|---|---|
| P0-2 message-only 限流 | 无 `type`/`code` 时先判限流/服务端措辞，再判超窗词表；数字 `code` 用 `JsonPrimitive.content` 兜底；超窗保留 provider 原文 | E5e/f/g 三条**修复前 RED** → 修复后 GREEN：`evidence/raw/w41/red_engine_message_only.txt` / `green_targeted.txt` |
| P0-3 四态 usage | `RunState.usageObservedThisRound`（真实 usage 置位 / 估算不置 / 轮次重置）；`ContextBudget.cachedLabel` 不再拿累计 token 反推 | 必红样本临时回退修复实跑 RED → 修复后 GREEN：`evidence/raw/w41/red_usage_estimated.txt` / `green_targeted.txt` |
| P1-1 E5b 空转断言 | `hits>=1` → `assertEquals(3,hits)` | `shouldRetry` 恒 false 变异实跑 RED（hits=1）：`evidence/raw/w41/red_e5b_retry_mutant.txt` |
| W24-01 思考框 | ThinkingBlock 去双层 THIN backdrop 改主题实底容器；Lens 零尺寸画布守卫 | 纯函数 `Size(0,0)` / NaN 必红；四态真机裁剪图见步 4 |
| 代码卫生 | A5 注释、KDoc、死 import | 同提交 diff |

`EXPECTED_TESTS 849→856`（+7：引擎 3 / 预算 2 / 镜头纯函数 2），同提交改 `.github/workflows/build.yml`。

| 闸 | 结果 | 四态 | 证据 |
|---|---|---|---|
| G1 编译 | `localbuild.sh` rc=0，APK 产出 | ✅ | `evidence/raw/w41/g1_build.txt` |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`；G20 141 ID | ✅ | `evidence/raw/w41/g2_archguard.txt` |
| G3 selftest | `30/30 PASS（264s）` | ✅ | `evidence/raw/w41/g3_selftest.txt` |
| G4 fulltest | `tests=856 failures=0 errors=0 skipped=0`（14 模块） | ✅ | `evidence/raw/w41/g4_fulltest.txt` |

行数闸门未触顶，`LINE_CAPS` / `TOTAL_LINE_CAP=42500` 未调；`GlassSurface.kt` 587/600 未增行。

### 步 4 · 真机矩阵（多模型 × 功能面）⚠️（部分 PASS，缺格如实标注）

设备 OPPO PDRM00 / Android 13，有线 adb `-P 15037`；先唤醒 + 亮度 231 非黑，再 `install-create/write/commit` 三步法装机，冷启 `Status: ok / TotalTime 1330ms`。密钥仍留于真机 Keystore 加密存储，未落仓。

制品：`evidence/w41-matrix-summary.json`（34 键冻结、14 格）+ `evidence/raw/w41/`（逐格 JSON、四态裁剪图、`deviceLogTail.txt`）。

| 格 | 四态 | 判据与证据路径 |
|---|---|---|
| E01 Lightning echo | ✅ | `OK_W41_ECHO` 逐字命中，`1.8k in / 73 out`；`evidence/raw/w41/e01_lightning.json` |
| E02 Dots3 | ⛔ | HTTP 404；同日公开模型清单查无该 ID ⇒ 端点侧；`evidence/raw/w41/e02_dots3.json` / `public_model_probe.json` |
| E03 North | ⛔vacuous | 本波未切换复测；公开清单亦查无该 ID，不冒充通过；`evidence/raw/w41/e03_north.json` |
| E04 SuperVL 1M | ✅ | 档位回读 `995.9K`；同会话缓存 `64.7%→69.0%→80.2%→89.5%`；`evidence/raw/w41/e04_supervl_1m.json` |
| F1 视觉输入 | ⛔vacuous | 图片输入开关已开启，但系统选择器出现敏感「错误报告」条目，拒绝授权、未发图；不沿用 W39 PASS；`evidence/raw/w41/f1_vision.json` |
| F2 workspace 写 | ✅ | 审批框 + `probe_w41.txt` 落盘 `W41OK`（hex `57 34 31 4f 4b`）；`evidence/raw/w41/f2_workspace.json` |
| F3 skill @ 菜单 | ✅ | 两技能可见、选中后运行并列出技能说明；`evidence/raw/w41/f3_skill.json` |
| F4 MCP | ⛔vacuous | server 数 0；明文 URL 仅得泛化 `transport_error`，不足以归因 G15b；`evidence/raw/w41/f4_mcp.json` |
| F5 手动压缩 | ⚠️ | 确认框三段文案走完，执行后 `nothing_to_drop`；applied 未行使；`evidence/raw/w41/f5_manual_compact.json` |
| F6 自动压缩 | ⚠️ | `agent-0.log` 新增 `11:02:27` / `11:03:40` / `11:07:27` UTC `auto_compact=noop reason=nothing_to_drop`；applied 未行使；`evidence/raw/w41/f6_auto_compact.json` / `deviceLogTail.txt` |
| T01/T02 档位 | ⛔vacuous | 32K / 512K 档位本波未单独回读；`evidence/raw/w41/t01_32k.json` / `t02_512k.json` |
| T03 1M 真压 | ⚠️ | 1M 档位回读 + 真请求至 `106.6K`，随后 `used=439211 limit=400000` 安全熔断；双硬限注入未做；`evidence/raw/w41/t03_1m.json` |
| W24-01 思考块 | ✅ | 浅/深 × 收起/展开 4 态裁剪图，浅底亮度 ~220 / 深底 ~46，文字可辨、无整块黑面板；`evidence/raw/w41/w2401_*_crop.png` / `w2401_thinking_block.json` |

判据 6 正控 `OK feature/evidence invariants`（14 格 / `cells_total=14` / `missing_refs=0`）+ 注入 `BOGUS_STATE` ⇒ `RED state domain; state sum`；判据 1/7 输出见步 2 证据路径。

### 步 5 · 推送 + CI ✅

- 代码主体提交 `93808be`，身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`。
- 推送 `c4fc2cf..93808be main -> main`（`git push --dry-run` 真判据通过；一次性推送凭据只走 URL inline、推后清 remote；凭据轮换属用户侧，不在仓内闭环）。
- CI（公开 API 四字段复核）：Build run `44898126012`（`head_sha=93808be874c…`、
  `status=completed` / `conclusion=success`）✅；Secret scan run `44898125963`（`completed/success`）✅。
  链接：`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44898126012` /
  `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44898125963`。
- 证据/验收物提交 `757b28d`（纯文档 + 证据文件）推送后也触发了 Build run `44898562000`
  （`head_sha=757b28db…`、`status=completed` / `conclusion=success`）与 Secret scan run `44898561966`
  （`completed/success`）；本次证据目录不在 `paths-ignore` 名单内，故「纯文档 Build 0 run」的旧假设**不适用**。
  链接：`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44898562000` /
  `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44898561966`。
  > 四条 run 均为四字段实取复核；本地 G1–G4 全绿与 CI success 一致，CI 绿以 run 为准。
- **W42 补漏（本波实取复核）**：波前基线订正提交 `c4fc2cf7f8d6a7b0ef1dab84eebf9202fb25c60d` → Secret scan run `44890089061`
  （`head_sha=c4fc2cf7f8…`、`status=completed` / `conclusion=success`）✅；
  本波 tip 提交 `3637057617f4c22207412c36dda0033866398412` → Secret scan run `44898959485`
  （`head_sha=3637057617f4…`、`status=completed` / `conclusion=success`）✅。
  链接：`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44890089061` /
  `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44898959485`。
  > 六条 run 均四字段实取复核（本波三次推送共 5 条 + 波前订正 1 条）；W41 正文「四条」为落笔时点记录，此处补齐、不回改历史数字。

---

## 明确未做（逐条带挂账号前缀）

1. `W12-02` 出口判据未全行使 — 探针事实表 / 设备侧缓存探针按四态落盘未做；本波只验证日志口径必红与真机命中读数。
2. `W9-01` 余面 — `DEFAULT_*_RATIO` 搬 `AgentPolicy`、B1 其余三条、`alreadyCompressed` 单指针未改。
3. `W20-02` 本体再分层（`ChatViewModel` / `ChatScreen` 拆文件）未做；`SwitchNotice` 层级归属仍待裁。
4. `W20-05` / `W9-11` 1M 真压双硬限注入（PreStepBudget 400k + RoundHeadGate）未做；本波只到安全熔断。
5. `W5-03/05/06` MCP 外部 https（G15b）未做；本波 server 数 0，明文测试未构成门禁通过证据。
6. `W9-05` / `W24-03` journal 二选一裁决未拍板，不代裁。
7. `W24-02` 剩余两条边界（`retry:` 纯帧 / `retryMillis` 透传、可点重试入口）维持 CLOSED 行已知边界，未回开。
8. 显式 `finishReason=LENGTH` ⇒ `TRUNCATED` 端到端用例未落位；F1 多模态输入、F4 MCP server、T01/T02 档位回读本波未行使。
9. NV Nano Omni / NV Bolt 双端点本波未切换复测；F5/F6 `applied=true` 分支未造出安全截断点。

## OPEN 挂账（触发信号 / 缓解动作 / 挂账号）

| 挂账号 | 触发信号 | 缓解动作 | 状态 |
|---|---|---|---|
| `W12-02` | 外部按四态出口判据回查探针落盘；估算 token 被写成已观测 | 已完成真实 usage 标志与必红样本；仍需设备侧探针按四态落盘 | OPEN（注记已补） |
| `W9-01` | 按旧顺序读台账或改压缩比例 | 命题②不重做；单指针 / DEFAULT_ratios / B1 余面待代码 | OPEN（注记已补） |
| `W20-02` | 下一次 `.kt` 增量触 G10 | 已腾行数不变、余量收窄；本体再分层与层级归属待裁 | OPEN（注记已补） |
| `W20-05` / `W9-11` | 1M 满窗真压验收 | 双硬限注入方案保持独立裁决；本波熔断读数留档 | OPEN（沿用） |
| `W5-03/05/06` | 外部 https MCP 验收 | G15b 明文门禁 + 端点可用性，单独裁决 | OPEN（沿用） |
| `W9-05` / `W24-03` | 用户给 journal 二选一裁决 | 用户拍板后统一代数口径，不代裁 | OPEN（沿用） |
| `W24-02` 已知边界 | 拥堵断流 / SSE `retry:` 帧复现 | 两条体验边界维持 CLOSED 行内注记，不回开 | CLOSED 行内注记 |

## 交给外部审查团队的制品清单

1. `docs/analysis/w41-acceptance.md`（本文件）
2. `docs/handoff-20261121-wave41.md`（交接文档）
3. `docs/06-deferred-ledger.md`（四行 W41 行内注记 + W40 段订正）
4. `docs/notes/w29-closeout-checklist.md`（判据 7 解析规则与摘录边界说明）
5. `evidence/w41-matrix-summary.json`（34 键冻结）+ `evidence/raw/w41/`（逐格 JSON、脱敏裁剪图、判据实跑输出、设备日志尾）
6. `.github/workflows/build.yml`（EXPECTED_TESTS 849→856）
7. 代码主体 `93808be` 的逐文件 diff（**16 文件 = 15 改 + 1 新**，含 1 个新增纯函数测试文件；W42 订正原「15 个…含 1 新增」字面少 1）

**不含**：会话历史 / 凭据（含一次性推送凭据）/ 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / 本机路径 / 「建议下一链做什么」。

## 边界声明

- ① 真机 F3 技能运行回答列出技能说明，未单独证明 `skill_load` 工具被调到（日志出口未覆盖该词），不写成工具调用 PASS。
- ② E02/E03 的 404 与公开模型清单查无 ID 是同日观察，未查供给方公告；不归因 harness。
- ③ F1 系统文件选择器暴露敏感错误报告条目，本波拒绝授权后判 vacuous；W39 视觉 PASS 不跨波沿用。
- ④ 判据 7 负控观察：把 `W24-02` 注入销账声明行仍 OK（该行证据列同时含历史「本波复核指路」与当波号字符串），改注入 `W26-01` 才 RED；说明现役判据只查两者并存、未校验指路归属当波。本波不新增判据编号、不改口径，如实交外审。
- ⑤ 本波未新建 schema / checklist / CI job；`harness-matrix-summary/v1` 34 键零增删，四态 glyph 与判据集合不变。
- ⑥ CI Build run 以四字段复核为准；本地 G1–G4 全绿不代替 CI 判绿。

> 本波止于交接，交由外部团队审查。
