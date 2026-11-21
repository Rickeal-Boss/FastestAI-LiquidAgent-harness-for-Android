# W40 验收物 — P0-3 断流重试 + 老号代码面推进 + 真机矩阵补格

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 tip `0cb87ee` → 本波主体 `e900f44`（feat）+ 台账判据订正提交（本文件落笔后回填）。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat，不写成事实。
> 回灌级别：脱敏 UI dump 文本判据 + `deviceLogTail`；不回灌整段 logcat / 凭据 / 未打码账号 / 设备序列号 / 本机路径。

---

## 收尾契约 · 1→5 各步结果

### 步 1 · 读记忆 + 上一波 handoff + OPEN 挂账行 ✅

- 证据路径：`docs/handoff-20261120-wave39.md`（W39 全篇）、`docs/06-deferred-ledger.md`（216 行，三份外审 +
  台账 W20-02/W24-02/W12-02/W9-01 行内注记）、四份外部审查报告（仓外 \<审查报告目录\>，
  只读分析、不入库）。
- 现役仓边界确认：只读 `harness-v2/docs/`，未读旧仓 `Android-Agent/docs/`；tip `0cb87ee` 与远端一致、
  工作树干净（仅仓外 `_w3x/` 取证目录 untracked）。
- 四份报告分歧裁决（主理人）：P0-2 采**案 A**（不回开，W24-02 CLOSED 行内收窄③）；
  P0-3 采**置 truncatedStream**（error 帧已吐字走整段回滚重试）；E03 404 归端点侧（非 harness 回归）。

### 步 2 · 第 0 车文档收口 ✅

| 动作 | 结果 | 证据 |
|---|---|---|
| P0-1 W20-02 补 W39 行内注记 | ✅ | `docs/06-deferred-ledger.md` W20-02 行：`W39 行内注记` 命中；三方已腾行数 1477/3978/3417 + ViewModel 447；`LINE_CAPS` 未调；本体再分层仍未做 |
| P0-2 案 A 收窄 | ✅ | W24-02 行 `W40 案 A 注记` 命中；`feature-chat/.../strings.xml` 删「或被中断」；已知边界 2→4 条（retry:/重试入口/error 帧/reasoning） |
| P0-3 口径订正 | ✅ | W24-02 行内「error 帧已吐字后不重试」列为 W40 第 1 车处理；`OpenAiErrorFrame` 代码面见步 3 |
| 判据 1/6/7 双态 | ✅ | 判据 1 正常态 `W40 rows = 0` + 守恒式回显、负控 `WAVE=W30` 不传 EXPECT ⇒ `RED 缺声明…不回落 3`；判据 6 正常态 `OK feature/evidence invariants` + 注入 BOGUS_STATE ⇒ `RED state domain`；判据 7 默认 `OK … declared=0`（本波空销账合法） |
| 守恒式两数 | ✅ | 本波 `新开 0 / 销账 0`；台账 216 行 / CLOSED 38 / OPEN 167 / CARRIED 11 不变；`W40-` 行 0；G20 141 ID |

> 判据 7 自身修复记录（**判据要被验证，不是读出来就采信**）：首版 W40 段写「本波销账：无…不回开 W24-02」，
> 判据正则抓到行内解释文字里的 `W24-02` ⇒ `declared=1` 假绿；删去该编号后重跑 `declared=0` 才是本波真值。

### 步 3 · 第 1 车代码面 + 四闸门 ✅

| 项 | 改动 | 判据 |
|---|---|---|
| P0-3 error 帧回滚重试 | `RemoteLlmEngine.decodeFailure` 分支：`val truncated = state.hasEmittedAny && failure.retryable`；`failure.copy(truncatedStream=truncated)`；零产出不置位 | E5 断言改向 `assertTrue(truncatedStream)`；E5b 零产出 `assertFalse` |
| 错误分类订正 | `OpenAiErrorFrame`：haystack 去 payload 全文；结构化 type/code 优先判 rate/server/context，词表仅兜底 | E5c 限流文案含 `token+limit` ⇒ RATE_LIMIT；E5d 服务端文案含 `context` ⇒ SERVER（两条交叉必红） |
| W12-02 四态读者 | `ContextBudget.cachedLabel` 接 `promptCacheStatus`：`n/a-usage` / `n/a-fields` / 命中量；usageObserved 与 RunState 同源 | 真机 E01 首轮 `0 cached, 0.0%`、F2 skill 轮 `12672 cached, 96.9%` 输出兼容 |
| W9-01 命题②时机 | `ChatViewModel.runOnce` collect：`onEvent(event, generation)` 提前，`autoCompactOnSettled()` 挪其后 ⇒ 快照含最终回答气泡 | G23 唯一调用点仍成立；真机 `auto_compact=noop` 本波日志在案（步 4） |
| W24-02 边界补完 | `RunState.truncateReasoning(length)` + `prepareRetry` 正文/思考一起回滚 | A5 必红变异：删 `truncateReasoning` 调用 ⇒ 最终计数 11≠6 判红 |
| EXPECTED_TESTS | `845 → 849`（E5b/E5c/E5d/A5），同提交对账 | `.github/workflows/build.yml:141`；G4 实跑 tests=849 |

**四闸门（本地实跑）**：

| 闸 | 结果 | 四态 | 证据 |
|---|---|---|---|
| G1 编译 | `localbuild.sh` rc=0，APK 68,825,460 B @ 15:25:07 | ✅ | 本地构建输出 |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`、G20 141 ID、G23 唯一调用点 | ✅ | `_w40_g2_arch.log` / `_w40_g2b.log`（仓外） |
| G3 selftest | `30/30 PASS（262s）` | ✅ | `_w40_g3_selftest.log`（仓外） |
| G4 fulltest | `tests=849 failures=0 errors=0 skipped=0`（14 模块） | ✅ | `_w40_g4_fulltest.log`（仓外） |

行数闸门：core-agent 5895/6500、core-engine 1426/2600、feature-chat 3984/4000、
RemoteLlmEngine 434/500、ContextBudget 383/400；`LINE_CAPS` / `TOTAL_LINE_CAP=42500` 未调。

### 步 4 · 真机矩阵（多模型 × 功能面）⚠️（部分 PASS，端点侧 404 如实标注）

设备 OPPO PDRM00 / Android 13，有线 adb `-P 15037`；三步法装机（先 `KEYCODE_WAKEUP` +
确认 `screenState=SCREEN_STATE_ON`、截图亮度 141.7 非全黑）；冷启 `Status: ok / TotalTime 1356ms`。
端点预检 6 个全在（NV Lightning / Nano Omni / Bolt / OR Dots3 512K / OR North / NV SuperVL 1M），
key 走 Keystore 加密（`endpoints.json` 无明文，隐私纪律成立）。

| 格 | 四态 | 判据与证据 |
|---|---|---|
| E01 NV Lightning echo | ✅ | `Say OK` ⇒ 逐字回复 `OK_W40_ECHO`；`1.8k in / 415 out (0 cached, 0.0%)`、上下文 `1.9K / 127K`；本波 APK 正常链路无回归 |
| E02/E03 Dots3 + North | ⛔ | 两者均 HTTP 404（`本次请求失败：request rejected (HTTP 404)` 上屏、非静默）；与 W39 E03 同型端点侧不可用，不归因 harness |
| F2 workspace 读写 | ✅ | `Use file_write to create /workspace/probe_w40.txt with content W40OK` ⇒ 审批框三选项上屏 → 允许一次 ×2 ⇒ 设备侧 `files/harness/workspace/probe_w40.txt`（15:46，5 B）内容 `W40OK`（hex `57 34 30 4f 4b`）；工具轨迹 `file_write 75ms/17ms` + `wrote 5 chars to probe_w40.txt` + AI 确认，全链闭合 |
| F3 skill @ 菜单 | ✅ | 设备已装 2 个 asset skill（`notes-organizer` / `pdf-toolkit`）；输入框 `@` 弹出两项菜单 ⇒ **推翻 W39 §六 F3 `⛔vacuous`（0 skill）判定**；选 `notes-organizer` 跑通 8/12 轮 + 工具 `浏览目录` + usage `12672 cached, 96.9%`（HIT） |
| F5 手动压缩 | ⚠️ | 更多操作 → 压缩上下文 → 确认框三段文案（缓存重建 / 历史保留 / 撤销）完整 → 点压缩 ⇒ 「没有可压缩的内容：历史本来就装得下，或找不到安全的截断点」；13.4K 远低于触发线，正确行为非缺陷 |
| W9-01 autoCompact 时机 | ✅ | 本波 agent-0.log 新增 `07:37:10` / `07:43:26` UTC 两条 `auto_compact=noop reason=below_trigger`（本地 15:37/15:43），证明顺序调整后评估链在真机真跑 |
| 缓存四态 E2 轮 | ✅ | 首轮 `0 cached, 0.0%`（MISS）→ skill 轮 `12672 cached, 96.9%`（HIT），同一会话内可比；按既定口径不跨端点/跨会话比较 |

**未达 PASS 的格（如实）**：E02/E03 端点侧 404；F5 无安全截断点（13.4K < 触发线，正确 noop）。
**未行使的格**：T01（32K 载体）、T03 满窗真压（1M 端点读数有、真压未注入）、F1 视觉本波未重跑（W39 已 PASS）、
F4 MCP server（0 server）、F6 applied=true 分支（本波无 `nothing_to_drop` 之外的新 applied）。
以上不冒充通过、不沿用历史 PASS 充数。

**回灌级别**：只回灌脱敏 UI dump 文本判据 + `deviceLogTail` / `agent-0.log` 尾部两行；
不回灌整段 logcat、不回灌凭据 / 未打码账号 / 设备序列号 / 本机路径；模型名仅公开名。
**设备侧两坑复核**：静置回锁 ⇒ 每步前 `KEYCODE_WAKEUP` + 亮度核验；连按 BACK 会退出应用到桌面 ⇒
用带返回箭头的页面内导航，不用系统 BACK 连按（本波两次误退桌面后已改用页面导航）。

### 步 5 · 推送 + CI

- 主体提交 `e900f44`（feat，12 文件 +225/−21），身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`。
- 推送 `0cb87ee..e900f44 main -> main`（PAT 以 URL inline 一次性使用，push 后 `git remote set-url` 清除；
  **该 PAT 视为已暴露，需用户在 GitHub Settings 撤销重发**）。
- CI（公开 API 实取，四字段复核）：Build run `44881943856`（`head_sha=e900f4486`、
  `status=completed` / `conclusion=success`）✅；Secret scan run `44881943855`
  `completed/success` ✅。两 run 均绑定本波主体 commit `e900f44`。
  链接：`https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44881943856` /
  `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44881943855`。
  > 本地 G1–G4 全绿与 CI 双 run success 一致；CI 绿以 run 四字段为准，不以本地结果代替。
- `build.yml` on.push.branches=[main]，本提交含 `.kt` 非纯文档 ⇒ Build 必触发（Secret scan 全路径必跑）。

---

## 明确未做（逐条带挂账号前缀）

1. `W12-02` 出口判据未全行使 — 探针事实表 / 设备侧缓存探针按四态落盘未做（本波只接了日志读者）。
2. `W9-01` 其余面 — `ContextCompressor.DEFAULT_*_RATIO` 搬 `AgentPolicy`、B1 其余三条、`alreadyCompressed` 单指针未改。
3. `W20-02` 本体再分层（`ChatViewModel` 再分层 / `ChatScreen` 拆文件）未做，本波只补注记。
4. `W20-05` / `W9-11` 1M 真压双硬限注入方案未做（读数有、真压未行使）。
5. `W5-03/05/06` MCP 外部 https（G15b）未做；F4 真机 0 server。
6. `W9-05` / `W24-03` journal 二选一裁决顺延（不代裁）。
7. E02/E03 端点侧 404 未恢复（供给方问题，需端点侧确认）；F1 视觉本波未重跑（W39 已 PASS，不沿用充数）。
8. `retry:` 纯帧 / `retryMillis` 透传、`Retrying` 可点重试入口 — W24-02 CLOSED 行已知边界，本波未做。

## OPEN 挂账（触发信号 / 缓解动作 / 挂账号）

| 挂账号 | 触发信号 | 缓解动作 | 状态 |
|---|---|---|---|
| `W12-02` | 外部按四态出口判据回查探针落盘 | 接设备侧缓存探针按四态落盘；本波已完成 cachedLabel 读者接线 | OPEN（注记已补） |
| `W9-01` | 开压缩波按旧顺序读台账 | 命题②已落地（注记已补）；余面 B1 三条 + DEFAULT_ratios 搬 AgentPolicy 待代码波 | OPEN（注记已补） |
| `W20-02` | 下波 G10 增量触顶 | 已腾行数 173/22/83 + ViewModel 447；本体再分层待做（注记已补） | OPEN（注记已补） |
| `W20-05` / `W9-11` | 1M 真压验收 | PreStepBudget 27904 + RoundHeadGate 400k 双硬限注入方案，单独裁决 | OPEN（沿用） |
| `W5-03/05/06` | 外部 https MCP 验收 | G15b 明文门禁 + 端点可用性，单独裁决 | OPEN（沿用） |
| `W9-05` / `W24-03` | 用户给 journal 二选一裁决 | 用户拍板后统一代数口径，不代裁 | OPEN（沿用） |
| `W24-02` 已知边界 | 拥堵断流 + error 帧形态复现 | retry: 帧透传 / 可点重试入口 / truncateReasoning（后者本波已补） | CLOSED 行内注记 |

## 交给外部审查团队的制品清单

1. `docs/analysis/w40-acceptance.md`（本文件）
2. `docs/handoff-20261121-wave40.md`（交接文档）
3. `docs/06-deferred-ledger.md`（W40 段 + W20-02/W12-02/W9-01/W24-02 行内注记）
4. `docs/notes/w29-closeout-checklist.md`（判据 1/6/7 双态实跑摘录在 W40 段补录后）
5. `.github/workflows/build.yml`（EXPECTED_TESTS 845→849）
6. 12 文件源码 diff（`e900f44` + 台账判据订正提交）

**不含**：会话历史 / 凭据（含 GitHub PAT）/ 端点完整标识（模型名仅公开名）/ 账号字段 /
设备序列号 / 本机路径 / 「建议下一链做什么」。

**边界声明**：① 本波两个并行子席因 429 配额耗尽中断，主理人按纪律接手补完（G1 编译产物由中断前完成、
真机链与全部代码改动由主理人亲自落地），非「子席产出」冒充；② 真机 E02/E03 404 是端点侧观察，
未查供给方公告；③ F3 skill 可见 PASS 但 @ 菜单选中时文本顺序异常
（`@Summarize:…notes-organizer`）属 W17-02 已销账行的 drv.text() 残余形态，本波不新开行；
④ 本波未新建制品（判据冻结：34 键零增删）；⑤ CI Build run `44881943856` completed/success（查询时点由 in_progress 转 completed 后实取复核）。

> 本波止于交接，交由外部团队审查。
