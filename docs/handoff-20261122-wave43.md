# W43 交接 — 判序反噬双修复 / 四态口径收口 / 3b-ext 全树加固 + 真机多模型回归

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `2e69dae333f6ed5ec5bd62600f75a2014fe50c1e`（W42 tip）→ 本波 tip `522040ae9b50d44d36bae3470ed6d9daedf7411f`（W44 回填订正）；台账权威版 = `docs/06-deferred-ledger.md`；
> 本波验收物 = `docs/analysis/w43-acceptance.md`；判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；
> 「未触发」≠「已验证通过」；推断带 caveat、不写成事实。守恒式两数只在派单卡抬头写一次。

---

## 一、本波做了什么（一段话）

裁决用户提供的 5 份 W42 审查分歧（核心：判序反噬该定 P2 后续还是 P0 本波必修），回源现役树实测确认两个反噬样本真 RED 后，按 P0 本波真修：`too many tokens` 收为限流语境内强词、`prompt` 加长度语义护栏；顺带统一四态 run 级累计口径并订正失实 KDoc。第 0 车把 W42 缺失的四闸门与必红原始产物按 W41 脱敏口径补入仓、把 3b-ext 从两目录扩到全树无豁免并补四类用户目录形态。跑四闸门全绿、编译装机、真机多模型回归（SuperVL/Lightning/OR 双端点 + 端点切换缓存失效提示），最后推 CI。守恒式 `0/0`、判据冻结、台账三数不变。

## 二、审查分歧裁决（6 条）

| # | 分歧 | 裁决 |
|---|---|---|
| 1 | 判序反噬定级：报告 A「P2 后续」vs 报告 C/草案「P0 必修」 | 采 **P0 本波必修**：逐字复刻 + 真 JUnit 未修版本实测 `2 failed`，非时点差。 |
| 2 | 反噬 a 修法 | `too many tokens` 收为限流语境内强词；**不含裸 `rate`**（保住 E5i）。 |
| 3 | 反噬 b 修法 | `prompt` 加长度语义护栏（不含裸 limit）；HTTP400 与 SSE 帧共用单一真值源。 |
| 4 | P1-3 四态口径 | 采 run 级累计口径 + KDoc 订正，**不采**草案的 per-request 快照（避免语义反复横跳）。 |
| 5 | 3b-ext 加固 | 泛化四类用户目录形态 + 全树无豁免 + 显式判 grep rc；三处命中全部脱敏。 |
| 6 | 三件待拍板 | 保守承接、不代裁：W24-01 维持 OPEN、W24-02 维持 CLOSED 边界、LENGTH 未落位；1M 双硬限/MCP https/journal 不解禁。 |

**调度事实**：本波未派并行子席（前波 429 前科按纪律处理）；主理人亲自落代码、闸门、真机与文档，不冒充子席产出。派单前 `git status` 仅历史 `_wXX/` 取证目录 untracked。

## 三、账本与守恒

- 台账零新增 `W43-` 行、状态字段零改动、三数不变、G20 141 ID 不变；`docs/06-deferred-ledger.md` 本波唯一改动是 W36-01 行一处历史字面量脱敏（pipe 数 7 自检）。
- 守恒式 `新开 0 / 销账 0`：连续第四波零销账；本波代码缺陷真修但对应行原始出口判据（探针落盘）仍未行使，保持 OPEN 是诚实的。
- 两数只在派单卡抬头；W42 及更早历史数字作时点记录不回改。

## 四、第 0 车（文档账实 / 隐私）

- P0-1 证据回补：W42 缺 G1–G4 / RED / GREEN 原始产物；本波实跑并按 W41 口径脱敏补入 `evidence/raw/w43/`（7 件）。W42/W40 历史缺口作时点记录，不伪造历史日志。
- P1-2 3b-ext 加固：扫描面从 `docs evidence` 扩到 `git grep` 全树 tracked（无豁免），补 `<盘符>\<用户名目录>`/`<盘符>/<用户名目录>`/`/<用户名目录>/`/`/<家目录>/` 四类形态，显式判 grep rc（故障不再被吞成 OK），分段拼装避免 workflow 自命中。正常态 rc=1 + 八类注入 rc=0 本环境实测；CI step #6 `completed/success`。
- P1-2 脱敏闭环：`g3_selftest.txt:3` 真残留 + w21 验收物 / W36-01 台账两处历史豁免字面量全部脱敏；全树 tracked 六类形态 0 命中。
- P1-4：build.yml 补 W42 的 `+6` 注释行。
- **裁决留痕**：3b-ext 加固属既有判据扫描面完善，非新增判据编号（判据冻结）。

## 五、第 1 车（代码面）

| 改动 | 文件 | 判据 |
|---|---|---|
| P0-2 反噬 a：too many tokens 限流语境内强词 | `OpenAiErrorFrame.kt` | E5k 必红（未修 RED→修复 GREEN）；E5e 保住 |
| P0-2 反噬 b：prompt 长度语义护栏 | `OpenAiErrorFrame.kt` | HTTP400 prompt limit 必红（未修 RED→GREEN）；E5j 保住 |
| P1-3 四态 run 级累计口径 + KDoc 订正 | `RunState.kt`、`Generation.kt`、`ContextBudgetTest.kt` | 新增累计口径钉用例；失实注释订正 |
| 测试数对账 | `build.yml` | `EXPECTED_TESTS 862→865`（+3：engine 2 / core-agent 1） |

`LINE_CAPS` / `TOTAL_LINE_CAP` 未调；G10/G11/G12 全绿；RunState 383/400（+3 KDoc）、OpenAiErrorFrame 209/400、Generation 150/300。

## 六、四闸门 + CI

| 闸 | 结果 | 四态 |
|---|---|---|
| G1 编译 | `localbuild.sh :app:assembleDebug` rc=0、APK 68M | ✅ |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 141 ID，空转项 G19） | ✅ |
| G3 selftest | `30/30 PASS（296s）` | ✅ |
| G4 fulltest | `tests=865 failures=0 errors=0 skipped=0`（14 模块） | ✅ |
| 判据先行 | 两条新必红未修版本 `2 failed`、修复后 GREEN；3b-ext 正常态 rc=1 + 八类注入 rc=0 | ✅ |
| Secret scan CI | run `44922539697` `completed/success`，gitleaks/trufflehog/**3b-ext（全树无豁免，step #6）** 逐 step success | ✅ |
| Build CI | run `44922539579` `completed/success`（Unit tests 865 对账 + Arch guard 26 判据 + Assemble Debug + Lint 全绿） | ✅ |
| 文档提交 CI | 另两笔纯文档提交各触发 Secret run `44922927954`（`41b3716`）与 `44923022977`（`522040a`，真 tip）均 `completed/success`；Build 0 run = `paths-ignore` 既定口径（W44 回填订正） | ✅ |

## 七、真机回归要点

设备先唤醒（`SCREEN_STATE_ON`、亮度 138 非黑）+ `adb install -r -t` 装机；冷启 `Status: ok / COLD / TotalTime 1319ms`。密钥留于真机 Keystore。

- ✅ SuperVL 1M 端点正常回复 + 档位回读 `995.9K`；端点切换确认框（缓存从头重建提示）正常。
- ⚠️ Lightning 端点通但模型未逐字回显 LIGHTOK（模型行为波动）；多模态测试图 push 到 Download 成功但未被媒体索引收录、未做逐字回读（W42 F1 已 PASS、本波无视觉代码改动）。
- ⛔ OR dots3 / north 真机复测仍 HTTP 404（端点侧，用户声明「可用」被设备真相证伪）。
- ⚠️ 短对话未触发 auto_compact（below_trigger），F5/F6 applied 分支未行使。

**回灌级别**：只回灌脱敏判据文本与 deviceLogTail 结论；不回灌整段 logcat / 凭据 / 未打码账号 / 设备序列号 / 本机路径 / 用户私人图片 / uiautomator 噪声。题面一律英文 ASCII。

## 八、明确未做（逐条带号）

`W12-02`（探针按四态落盘、`cached=` 值不落文件）｜`W9-01`（DEFAULT_ratios / B1 余面 / 单指针）｜`W20-02`（本体再分层）｜`W20-05`/`W9-11`（1M 双硬限注入，本波只档位回读）｜`W5-03/05/06`（MCP 外部 https）｜`W9-05`/`W24-03`（journal 二选一）｜`W24-02`（`retry:` 透传 / 可点重试入口，维持 CLOSED 边界）｜显式 LENGTH 端到端用例、F5/F6 applied 分支、OR 双端点 404、多模态逐字回读、凭据轮换（用户侧）、W42/W40 历史缺口。
逐条触发信号与缓解动作见验收物 §「OPEN 挂账」。

## 九、OPEN 挂账

逐条（触发信号 / 缓解动作 / 挂账号）见 `docs/analysis/w43-acceptance.md` §「OPEN 挂账」表。零销账、零新开，不注水；台账三数不变。

## 十、交给外部审查团队的制品 + 边界声明

**制品**：`docs/analysis/w43-acceptance.md`、本交接、`docs/06-deferred-ledger.md`（W36-01 脱敏）、`.github/workflows/secret-scan.yml`（3b-ext 全树加固）、`.github/workflows/build.yml`（EXPECTED_TESTS 862→865）、`evidence/raw/w43/`（7 件脱敏原始日志）、代码主体逐文件 diff。

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / 本机路径 / 用户私人图片 / uiautomator 噪声 /「建议下一链做什么」。

**边界**：① 反噬 a 修法刻意不含裸 `rate`，保住 E5i（以真样本为准）；② P1-3 采 run 级累计口径，非快照；③ 3b-ext 本地 `git add -f` 新文件不命中是 index 缓存坑，CI 全 tracked 不受影响（W44 订正：已补未 tracked 工作树扫描兜底，见 `secret-scan.yml` W44 段）；④ 判据冻结（3b-ext 是扫描面加固、3 条新增是既有测试类行为判据）；⑤ OR 双端点 404 与用户声明矛盾，以设备真相为准、未查供给方公告；⑥ CI 绿以 run 四字段为准，本地四闸门不代替 CI；⑦ **本波为回归要点波、非矩阵波**：真机 9 格为脱敏文字结论、未产出 34 键矩阵制品与逐格 JSON，W42 制品不跨波沿用（W44 按 P1-2 二选一裁决写死此形态）；⑧ W43 真机多模型回归未派并行子席（429 前科按纪律处理），主理人亲自执行、不冒充子席产出。

> 本波止于交接，交由外部团队审查。
