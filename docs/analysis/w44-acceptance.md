# W44 验收物 — 判序证据强度仲裁 / 落盘镜像销账 / 真机多模型回归

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `522040ae9b50d44d36bae3470ed6d9daedf7411f`（W43 tip）。
> 台账权威版 = `docs/06-deferred-ledger.md`；交接 = `docs/handoff-20261122-wave44.md`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。
> 回灌级别：只回灌脱敏判据文本与 `deviceLogTail` 结论；不回灌整段 logcat / 凭据 / 端点标识 / 序列号 / 本机路径 / 用户私人图片。

---

## 步 1 · 读记忆 / 交接 / 挂账 + 五份审查分歧裁决

- 读本仓 `docs/handoff-20261122-wave43.md`、`docs/06-deferred-ledger.md`（W12-02/W24-01/W24-02/W36-01 四行）、用户提供的五份 W43 审查/草案/派单卡；未读旧仓 `Android-Agent/docs/`。动手前 `git status` 仅有历史 `_wXX/` 取证目录 untracked（本波已用 `.gitignore` 收敛，见步 2）。
- 五份报告中，对「W43 判序残留是否 P0」一致（审查与排期裁决称 P0 残留、路线建议称编码器进维护态），对「零销账」一致（两份报告要求本轮至少 1 行实质销账），对「真机 9 格证据形态」一致（要求二选一写死）。

**裁决（方向）**：

| # | 分歧 | 裁决与依据 |
|---|---|---|
| 1 | 判序残留定级与修法 | 采 **P0 本波真修**：按外审处方做**证据强度仲裁**（超窗强组合优先于 message 强词，结构化 `type`/`code` 与 HTTP 429 仍无条件优先）+ **裸 `limit` 降级**（须配长度语义）。4 条新必红在未修代码上实跑 RED，非推断。 |
| 2 | 零销账 | 采 **本轮销 1 行（W12-02）**：按出口判据第三条「设备侧缓存探针按四态落盘」真实落地（落盘镜像端口 + 必红 + 设备文件证据），不采「把判据改成已达成」的降级路径。 |
| 3 | 真机 9 格证据形态（W43 P1-2） | 采 **方案②写死**：W43 为回归要点波、非矩阵波，9 格是脱敏文字结论、不冒充 34 键制品；不补造历史制品。 |
| 4 | 3b-ext 未 tracked 兜底（W36-01） | 采 **方案一（补扫）**：`secret-scan.yml` 在 tracked `git grep` 后增未 tracked 工作树逐文件扫描；同时 `.gitignore` 收敛历史取证目录，`git add -f` 场景本身消失。 |
| 5 | 收尾契约连续复发（W43 P1-1） | 采 **订正入仓**：真 tip `522040ae…`、`41b3716c…` 完整 SHA、第 4 条 run `44923022977` 四字段、「3 次推送 3 提交 / 4 条 run」。 |
| 6 | 派单卡「不新建 W44 段」 | **偏离并留痕**：派单卡按 `0/0` 起草；本轮发生 1 行实质销账，按台账机制补 `### W44 段` 标准声明行（否则判据 7 无法从声明推导本波销账集合）。守恒式 `新开 0 / 销账 1`，`0 ≤ 1`。 |

## 步 2 · 第 0 车（文档账实 / 隐私 / 兜底）

| 项 | 结果 | 四态 | 证据路径 |
|---|---|---|---|
| 四行老号 W43 行内注记 | `W24-02` / `W12-02` / `W24-01` / `W36-01` 各补 W43 补录（内容订正，状态按原样） | ✅ | `docs/06-deferred-ledger.md` |
| 收尾契约订正 | `w43-acceptance.md` 抬头改真 tip + 波内三提交 SHA；步 5 改「3 次推送 3 提交 / 4 条 run」并补 `44923022977`；handoff 抬头/§六/§十 同步 | ✅ | `docs/analysis/w43-acceptance.md`、`docs/handoff-20261122-wave43.md` |
| 真机 9 格形态写死 | 交接 §十 边界⑦ + 验收物边界⑨：回归要点波、非矩阵波、不补造制品；删「34 键矩阵零增删」无对象表述 | ✅ | 同上 |
| 3b-ext 未 tracked 补扫 | `secret-scan.yml` 3b-ext 步新增 `git ls-files --others --exclude-standard` 逐文件扫描（同一组八类形态）；`.gitignore` 增 `_w[0-9]*/` 等规则 | ✅ | `.github/workflows/secret-scan.yml`、`.gitignore` |
| 3b-ext 双态实测（本地） | 正常态 `GREEN untracked-clean`；注入含 serial 的未 tracked 文件 ⇒ `RED untracked leak hit`；移除后回 GREEN | ✅ | `evidence/raw/w44/g0_untracked_scan.txt` |
| 行内注记 pipe 自检 | 四行 `|` 计数 = 7；`has CRLF=False` | ✅ | 本环境 python 自检输出 |

## 步 3 · 第 1 车（代码 + 四闸门）

| 改动 | 文件 | 判据 / 证据 |
|---|---|---|
| 判序证据强度仲裁：超窗强组合优先于 message 强词；结构化 `type`/`code`/HTTP 429 仍无条件优先 | `core-engine/.../provider/OpenAiErrorFrame.kt` | 4 条新必红未修版 RED → 修复 GREEN |
| 裸 `limit` 从 `mentionsOverflow` 降级（`context`/`token` × `limit` 须长度语义） | 同上 | SSE 裸 `token limit reached` / HTTP400 同形态双必红 |
| `explicitRateLimit` 冗余合取项简化（外审 P3，行为不变） | 同上 | 全量 E5 样本不回退 |
| run 级累计窗口 KDoc 边界（`n/a-fields` 只表示整个 run 从未收到缓存字段） | `core-model/.../Generation.kt`、`core-agent/.../ContextBudget.kt` | 纯文档口径，不改语义 |
| W12-02 落盘镜像端口（`installFileSink`，锁外调用、异常不影响缓冲） | `core-kernel/.../log/AgentLogStore.kt` | 2 条必红（INFO 转交 / 异常不破记录） |
| app 装配（512 条 DROP_OLDEST 队列 + 单写协程 + 256KB×3 轮转） | `app/.../DiagnosticLogSink.kt`、`LiquidHarnessApplication.kt` | 设备侧 `cached=` 落盘实证 |
| 测试数对账 | `.github/workflows/build.yml` | `EXPECTED_TESTS 865→871`（+4 engine / +2 core-kernel） |

**判据先行实证**：
- 判序 4 条：未修版本 `3 failed`（`RemoteLlmEngineStreamFailureTest.kt:357/369/388`）+ `1 failed`（`RemoteLlmEngineContextLimitTest`），修后 `BUILD SUCCESSFUL`。
- 落盘镜像 2 条：变异「sink 只转 WARN/ERROR」⇒ `6 tests completed, 1 failed`（`fileSinkReceivesInfoProbeLines`）；还原后 GREEN。

| 闸 | 结果 | 四态 | 证据 |
|---|---|---|---|
| G1 编译 | `localbuild.sh :app:assembleDebug` rc=0、APK 67M | ✅ | `evidence/raw/w44/g1_build.txt` |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 141 ID；含 W12-02 CLOSED 行） | ✅ | `evidence/raw/w44/g2_archguard.txt` |
| G3 selftest | `30/30`（见日志尾） | ✅ | `evidence/raw/w44/g3_selftest.txt` |
| G4 fulltest | `tests=871 failures=0 errors=0 skipped=0`（14 模块） | ✅ | `evidence/raw/w44/g4_fulltest.txt` |
| 行数闸门 | `LINE_CAPS`/`TOTAL_LINE_CAP` 未调；新增文件均在模块/单文件上限内 | ✅ | G2 输出（G10/G11/G12） |

## 步 4 · 真机（多模型 / 多上下文 / 功能入口）⚠️

设备先唤醒（`SCREEN_STATE_ON` + 非黑截图确认）→ `adb install -r -t` 装机 → 冷启 `Status: ok / COLD / 1355ms`。密钥留于真机 Keystore，未回灌。

| 格 | 四态 | 判据与证据 |
|---|---|---|
| E1 NV SuperVL 1M | ✅ | `w44OK-SUPERVL` 逐字回声 + 思考块；`1.8k in / 28 out (0 cached)`、`上下文 1.9K / 995.9K`（1M 档位回读） |
| E2 NV Lightning 127K | ✅ | 切端点确认框（缓存重建 + `unverified_window` 诊断）后 `w44OK-LIGHTNING` 逐字回声；`1.8k in / 97 out`、`/127K` |
| E3 NV Bolt 131K | ⚠️ | 端点编辑页「测试连接」`连接正常（3970 ms）`；未做会话轮（见「明确未做」） |
| E4 多模态（SuperVL） | ✅ | 自生成 320×180（红矩形 + 蓝圆）push 到 Download 并经媒体扫描后选中；回复「The rectangle is red, and the shape to its right is a blue circle.」逐字正确 |
| F1 工作空间文件读写 | ✅ | 审批卡（`file_write` 参数正确）→ 允许一次 → `wrote 7 chars to w44probe.txt` → `file_read` 返回 `W44FILE` → 回复 `W44FILE`；设备侧 `cat` 复核文件内容一致 |
| F2 skill | ✅ | `skill_load {"name":"notes-organizer"}` 卡 + `[SKILL BODY]` 注入；回复逐字第 6 步含哨兵 `(inferred)`（与 `SKILL.md` 原文一致） |
| F3 MCP | ⛔vacuous | `设置 → MCP 服务器` = 「还没有 MCP 服务器」（0 个）；外部 https 仍受 `W5-03/05/06` 阻塞，本波未配置服务器 |
| F4 手动压缩 | ⚠️ | 确认框（缓存重建 + 「较早消息不再发给模型」）走完；日志 `manual_compress=noop reason=manual compress: nothing to drop` |
| F5 自动压缩（32K 档 + 长历史） | ⚠️ | 首请求被 L3 硬字符闸熔断：`context_overflow_prevented: chars=59064 cap=50227`（设计行为、UI 可见「已被安全熔断」）；`applied` 分支未行使 |
| E5 OR Dots3（512K，用户声明可用） | ⛔ | 端点编辑页「测试连接」`连接失败: PROTOCOL: request rejected (HTTP 404)`（连续第三波设备真相） |
| E6 OR North | ⛔ | 统一切换后会话请求 `本次请求失败: request rejected (HTTP 404)`（UI 可见失败，非静默） |
| 端点切换守卫 | ✅ | 每次「设为当前」弹确认框并给诊断串（`unverified_window` / `empty_session` 等） |
| 设备网络 | ⚠️ | 02:00:11 起 Wi-Fi `NETWORK_DISCONNECTION_EVENT`（reasonCode 3）、`ip route` 无默认路由 ⇒ 后续联网判据（F5 applied、E3 会话轮）未行使 |

**W12-02 设备侧四态探针（销账证据）**：`run-as` 取回 `files/harness/logs/agent-0.log`，`cached=n/a-usage`（USAGE_NOT_OBSERVED）×3、`cached=0(cumulative)`（MISS，同一 run 内 file_write→file_read 多请求）×2。脱敏件：`evidence/raw/w44/w12-02-device-probe.txt`。`HIT` / `n/a-fields` 本波端点未上报该形态，未观察到（边界如实）。

## 步 5 · 推送 + CI ✅

- 提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；单一代码提交 `1c3cd5a5ccaef7195a1fb41ea48a69fe12abb178`（29 文件：11 源码/测试 + 2 workflow + `.gitignore`/`.gitattributes` + 6 文档 + 11 件证据）；PAT 仅 URL inline 一次性使用，未新增 remote、无凭据落盘。
- **1 次推送 1 提交 → 2 条 run**（`head_sha=1c3cd5a5ccaef7195a1fb41ea48a69fe12abb178`，公开 API 实取）：
  - Build run `44935473646`：`status=completed` / `conclusion=success`、`updated_at=2026-11-21T18:28:31Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44935473646`（Unit tests 871 对账 + Arch guard + Assemble Debug + Lint）。
  - Secret scan run `44935473364`：`status=completed` / `conclusion=success`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/44935473364`（gitleaks / trufflehog / 3b-ext 含未 tracked 补扫步）。
- **本文件的 CI 回填提交为纯文档 tip**：按既定降级规则（tip 文档提交的 run 不作 P0 判据），其触发的 Secret run 不预设 run 号、不冒充代码提交的验证；代码提交的两条 run 四字段才是本波 CI 判据。Build 对纯文档提交 0 run（`paths-ignore` 既定口径，非构建失败）。
- 本地四闸门不代替 CI；CI 绿以 run 四字段为准。

---

## 明确未做（逐条带挂账号）

1. `W12-02` 已销账，但 `HIT` / `n/a-fields` 两态未在真机观察（端点未上报该形态）——不构成新债，边界写在行内注记。
2. `W9-01` 余面（`DEFAULT_*_RATIO` 搬 `AgentPolicy`、B1 其余三条、`alreadyCompressed` 单指针）未改；本波只新增 32K 档「熔断 + 无可压」观察。
3. `W20-02` 本体再分层未做；`feature-chat` / `RunState` 水位未动（本波未改两处）。
4. `W20-05` / `W9-11` 1M 真压双硬限注入未做；本波只做档位回读 `995.9K` 与 1M 档常规请求。
5. `W5-03/05/06` MCP 外部 https（G15b）未做；F3 vacuous。
6. `W9-05` / `W24-03` journal 二选一未拍板，不代裁。
7. `W24-02` 两条已知边界（`retry:` 纯帧透传 / 可点重试入口）维持；显式 `finishReason=LENGTH ⇒ TRUNCATED` 端到端用例仍未落位。
8. `W24-01` 余项四件（`overlayPanel=true` 接线、`asOverlayColors()` 恒白族、`AnimatedVisibility` 移出玻璃节点或 `heightIn(min=1.dp)`、AGSL `normalize` epsilon）未动，维持 OPEN。
9. OR dots3 / north 第三波 HTTP 404（端点侧）；用户声明「可用」再被设备真相证伪，未查供给方公告。
10. 自动压缩 `applied` 分支、Bolt 会话轮未行使（设备 Wi-Fi 断开、无默认路由）；不以推断代替证据。
11. 凭据轮换：本波 PAT 以 URL inline 推送一次即视为暴露，轮换属用户侧动作，仓内无法闭环。
12. `W37` 判据 7 波次盲区（负控只查并存、不校验归属）与 `W24-01` 协作用例文本形态未修（判据冻结）。
13. 历史缺口：W42/W40 缺原始日志作时点记录，不补造。

## OPEN 挂账（触发信号 / 缓解动作 / 挂账号）

| 挂账号 | 触发信号 | 缓解动作 | 状态 |
|---|---|---|---|
| `W12-02` | —（已销账） | 落盘镜像 + 双必红 + 设备侧四态证据；`HIT`/`n/a-fields` 待端点上报形态 | **CLOSED（W44）** |
| `W9-01` | 32K 档长历史被字符闸熔断且手动压缩 `nothing to drop` | 压缩触发与字符闸同尺化评估；`DEFAULT_*_RATIO` 搬 `AgentPolicy`；单指针 | OPEN（W44 观察） |
| `W20-02` | 下一次 `.kt` 增量触 G10 | 本体再分层与层级归属待裁 | OPEN（沿用） |
| `W20-05` / `W9-11` | 1M 满窗真压验收 | 双硬限注入方案保持独立裁决 | OPEN（沿用） |
| `W5-03/05/06` | 外部 https MCP 验收 | G15b 明文门禁 + 端点可用性，单独裁决 | OPEN（沿用） |
| `W9-05` / `W24-03` | 用户给 journal 二选一裁决 | 用户拍板后统一代数口径，不代裁 | OPEN（沿用） |
| `W24-01` | 展开态仍现黑面板 / 零尺寸进 AGSL 复现 | 余项四件待做；协作用例文本形态、判别力有限 | OPEN（沿用） |
| `W24-02` 已知边界 | 拥堵断流 / SSE `retry:` 帧复现 | 两条体验边界维持 CLOSED 行内注记，不回开 | CLOSED 行内注记 |
| `W36-01` | 未 tracked 文件含敏感形态 | 3b-ext 增未 tracked 补扫 + `.gitignore` 兜底（本波落地） | CLOSED（W44 补全出口判据） |

## 交给外部审查团队的制品清单

1. `docs/analysis/w44-acceptance.md`（本文件）
2. `docs/handoff-20261122-wave44.md`（交接）
3. `docs/06-deferred-ledger.md`（四行 W43 注记 + W12-02 销账 + W44 段声明 + `W9-01` 观察）
4. `docs/analysis/w43-acceptance.md`、`docs/handoff-20261122-wave43.md`（收尾契约订正）
5. `.github/workflows/secret-scan.yml`（3b-ext 未 tracked 补扫）、`.github/workflows/build.yml`（`EXPECTED_TESTS 865→871`）、`.gitignore`
6. `evidence/raw/w44/`：`g0_untracked_scan.txt`、`g1_build.txt`、`g2_archguard.txt`、`g3_selftest.txt`、`g4_fulltest.txt`、`red_p02_sse.txt`、`red_p02_http400.txt`、`green_p02_engine.txt`、`red_w1202_sink_mutant.txt`、`green_w1202_sink.txt`、`w12-02-device-probe.txt`
7. 代码逐文件 diff：`OpenAiErrorFrame.kt`（仲裁 + 降级 + KDoc）、`Generation.kt`/`ContextBudget.kt`（KDoc 边界）、`AgentLogStore.kt`（镜像端口）、`DiagnosticLogSink.kt`（装配）、两既有测试类 + `AgentLogStoreTest`（6 条新增判据）

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / uiautomator 噪声 / 「建议下一链做什么」。

## 边界声明

- ① 判序 4 条新必红与镜像 2 条新必红均先在未修/变异版本实跑 RED，再修复 GREEN；不是文档自述。
- ② 仲裁方向以真样本为准：`rate limit: too many tokens`（E5e）仍判限流，因为它不含长度语义、不会被超窗强组合吃掉；外审若按「所有超窗文案含 rate 即超窗」复核会与 E5e 冲突，此处以样本集为准。
- ③ W12-02 销账按出口判据第三条落地；两态未观察属端点行为、非代码缺口，已在行内注记写死。
- ④ 真机 404 与用户声明矛盾时以设备真相为准；本波两次独立路径（编辑页测试连接 / 会话请求）同结论。
- ⑤ 设备断网后未以推断代替证据；`F5 applied`、`E3 会话轮` 记 vacuous/未行使。
- ⑥ 守恒式两数只在派单抬头与本验收物一处；台账零 `W44-` 行、`G20` 141 ID 不变。
- ⑦ CI 绿以 run 四字段为准，本地四闸门不代替 CI。

> 本波止于交接，交由外部团队审查。
