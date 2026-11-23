# W49 交接 — 账实回补 / 门禁双键 / evidenceTag 全覆盖 / 真机多模型复核

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 `94bbc53da168b5031f37d8d72dc0a3a11a0fb369`（W48 tip）；台账权威版 = `docs/06-deferred-ledger.md`；
> 本波验收物 = `docs/analysis/w49-acceptance.md`；派单卡 = `docs/w49-dispatch.md`；判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；
> 「未触发」≠「已验证通过」；推断带 caveat、不写成事实。本交接不写复合挂账号，段位用中文描述。

---

## 一、本波做了什么（一段话）

把 W48 闭环后暴露的三层张力逐条收口：**账实**（W48 声称的五处老号注记实测只有 1 处，回补 4 条并把四处文档措辞改回实态，同时撤销「一句 kickoff 即三问总裁决」的表述）、**豁免边界**（门禁哈希白名单从「按内容」改「内容 + 相对路径」双键，补上「同内容放到全新路径」这条被静默豁免的路径，六态实证）、**标签覆盖**（`Attempt.Fatal` 增证据标签与落盘摘要，看门狗 / 重试放弃 / 半截输出 / 兜底四处透传，state 写入改无条件覆盖，落盘白名单主体从 2 类扩到 13 类固定模板，补 4 条 JVM 必红）。另处置一处空转判据（`context limit writeback` 换成真实生产形态）与一处脱敏疏漏（证据文本里的 `hostname` 属性值）。四闸门全绿后做真机多模型复核并在设备上第二次确认证据标签可读，最后推 CI。

## 二、审查分歧裁决

### 2.1 采信执行

P0-1（注记 1/5 账实不符）、P1-2（裁决措辞失实）、P1-3（白名单按内容豁免）、P1-4（标签四类路径丢失）、P1-5（文档 tip run 未补号）、P2-6（`hostname` 属性的用户名字面量）、P2-7（段标题复述两数）、P2-8（空转形态）、P2-9（12 vs 13 格）、P2-10（哈希册可审计性）—— 逐条在验收物步 1.1 给回源实测。

### 2.2 判定为外审误报 / 已订正项（不据此改仓）

- **`branches: ain]`（被列为 P0，称 push main 死链 48 波）**：全历史 32 个提交逐版本读取该行恒为 `branches: [main]`；`git log -S` 查该被吞字面量**零命中**——若真「出生即吞」，W1 提交必命中，零命中恰是反证。W16 验收物早已把同型现象记为「显示假象」。判定为审查侧链路吞掉 `[m` 两字符。
- **`deviceLogTail` 的 `ttp:rejected]`**：仓内实字节为 `[http:rejected]$`，全树 grep 该截断形态 0 命中；本波设备侧 `run-as` 直读同一行完整。
- **W48 证据只有一件**：`evidence/raw/w48/` 实存 19 件（8 张判据截图 + 设备尾 + 闸门四件 + 判据先行五件 + 门禁五态）。
- **W43「幻影制品」**：`docs/analysis/w43-acceptance.md` 的 W44 补记已明确「本波为回归要点波、未产出 34 键 summary、按裁决取方案②不补造」；属已订正项，非新发现。

第 1、2 条同型（ASCII 短串在审查侧传输/显示链路丢失 1–2 字符），本波为第 2 次外部误报，已入注记。

**调度事实**：主理人串行落代码/闸门/真机/文档（真机是独占资源、台账与门禁脚本同文件域，并行会写冲突）；动手前 `git status` 干净；变异与注入验证均用仓内临时文件并由脚本自行清理，未用 git 写命令还原。

## 三、账本与守恒

- 台账回补 4 条（形态正则全覆盖 / 行数预算前置 / 判序范式转换 / 三问未获裁决）与新增 4 条（双键豁免面 / `hostname` 脱敏 / 标签链路 / 段标题与格数）逐行落笔，状态原样、pipe=7、零回开。
- W48 段标题收敛为「本波销账集合 = 0 行」；新增 W49 段声明只写销账集合与计数指路。
- 守恒：挂账行 216 / `CLOSED` 39 / `OPEN` 166 / `CARRIED` 11，与 W44–W48 逐值相等；零本波新挂账行；G20 `141` ID 不变。

## 四、第 0 车（账实与口径）

| 项 | 结果 | 四态 |
|---|---|---|
| 4 条回补注记 + 4 条新增注记 | 台账五处相关行 len 增加、pipe 保持 7、状态未变 | ✅ |
| 三文档措辞订正 | 「五处/5 条」→「实落 1 条 + 回补 4 条」；kickoff 措辞撤销 | ✅ |
| 收尾契约 | W48 文档 tip 的 Secret run 四字段 + 链接补入两份文档，口径「代码提交 2 条 + 文档 tip 1 条（降级）」 | ✅ |
| 脱敏 | 证据文本属性值改占位符；3b-ext 加属性形态判据（tracked + 未 tracked 两路），本地门禁同步 | ✅ |
| 格数 | W48 统一 13 格，W47 12 格作时点记录 | ✅ |
| 闸门证据脱敏（本波自捕） | 闸门原始输出含本机工作区路径与用户临时目录形态，CI 3b-ext 判红一次；改占位符后 tracked 路复扫 0 命中。同类脱敏疏漏第 3 次（不同形态），已入注记 | ✅ |

## 五、第 1 车（代码面）

| 改动 | 文件 | 判据 |
|---|---|---|
| 白名单双键（内容 + 相对路径） | `scripts/fixtures/w46-gate-legacy-hashes.txt`（140 行双键）+ `scripts/w46-local-secret-gate.sh` | 六态：历史在场 GREEN / 新探针 RED / `add -f` RED / 移除 GREEN / CI 语义 GREEN / **同内容新路径 RED**（W48 单键此态为 GREEN） |
| 证据标签全覆盖 | `GenerationRound`（Fatal 增字段 + 四处透传 + 无条件覆盖）/ `RunTermination`（落盘摘要参数）/ `AgentRunner`（兜底与 loop 退出带标签）/ `DiagnosticLogSink`（主体 2→13 类） | 4 条 JVM 必红（陈旧标签 / 看门狗 / 重试放弃 / 兜底剥离动态正文）+ 1 条白名单正反控 |
| 空转形态 | `DiagnosticLogSink.PROBE_FORMS` | 删测试钉形态，换 `context_limit_writeback_failed endpoint=… verified=…`（真实写点）；正反控各一条 |
| 测试数 | `build.yml` | 严格行首计数 891，`EXPECTED_TESTS` 同提交改 891 |

## 六、四闸门 + 真机

| 闸 | 结果 | 四态 |
|---|---|---|
| G1 编译 | `assembleDebug` rc=0 | ✅ |
| G2 arch-guard | `OK=27 VACUOUS=1 VIOLATION=0 SELF_FAULT=0`（G20 141 不变） | ✅ |
| G3 selftest | `30/30 PASS（268s）` | ✅ |
| G4 fulltest | `tests=891 failures=0 errors=0 skipped=0`（14 模块） | ✅ |
| 判据先行 | 双键第六态 RED→GREEN；标签 4 条必红；脱敏判据正控 + 注入 | ✅ |
| CI | 见 §七 | — |

设备 `<device-serial>` 先唤醒（`mWakefulness=Awake` + 非黑主屏）→ `pm install -r -t` → 冷启正常。密钥留于真机、未回灌。

- Lightning 30B / 127K：逐字回引 + 思考块 + `上下文 1.9K / 127K`。✅
- SuperVL 120B / 1M 档：逐字回引 + 思考块 + `上下文 1.9K / 995.9K`（常规请求，非满窗真压）。✅
- 工作空间文件读写：`workspace/` 前缀 → 设备侧 `files/harness/workspace/workspace/…` 逐字节一致；无前缀写读回一致；审批卡「允许一次」路径。✅（口径澄清：`workspace/xxx` 是沙箱子目录，不是根别名）
- 多模态 320×180：红方 / 蓝圆 / 白底三要素齐。✅
- skill：`notes-organizer` 步骤逐条复述 + 末尾询问指向。✅
- OR Dots3：`W49DOTS3` 逐字 + 思考块 + `上下文 1.7K / 520.2K` —— W48 首胜后连续第二次会话级成功。✅
- OR North：会话级 `request rejected (HTTP 404)` 复现。⛔ 不可用
- MCP：0 服务器 ⇒ vacuous。⛔vacuous
- 手动压缩：确认框走完 → 「没有可压缩的内容」。⚠️ 可压内容不足（正确行为）
- 自动压缩：设备落盘 `auto_compact=noop reason=below_trigger` 多次（评估链真跑）；`applied` 未触发。⚠️
- 设备侧标签复核：`run failed: request rejected (HTTP 404) [http:rejected]` 逐字节完整。✅

**回灌级别**：只写脱敏判据结论与 `deviceLogTail` 探针行；不回灌整段 logcat / 凭据 / 端点完整标识 / 序列号 / SSID / 本机路径 / 私人图片。

**本波新坑**：`adb shell input text` 长串会吞尾字符（本波少一个字符，补发 + dump 校验后通过）。纪律：发送前必须 dump 输入框文本逐字校验。

## 七、CI（公开 API 四字段实取）

- 提交身份 `Rickeal-Boss / 263587600+Rickeal-Boss@users.noreply.github.com`；PAT 仅 URL inline 一次性使用，remote URL 保持干净、无凭据落盘。
- **本波代码 2 提交**：`3d9766df0f3dc5635f6adc65693dd067a0a06782`（feat）→ `4b2be946a9f46651202e86794e42b7cff7bfa9fb`（fix：闸门证据脱敏）。首提交的 Secret scan `45182694954` **failure**（3b-ext 抓到闸门日志里的本机路径 / 用户临时目录形态）—— 红态如实记录，不隐藏；fix 后重推。
- **最终 tip `4b2be946a9f46651202e86794e42b7cff7bfa9fb` 双绿**：
  - Build `45185252002`（`head_sha=4b2be946a9f46651202e86794e42b7cff7bfa9fb` / `status=completed` / `conclusion=success` / `updated_at=2026-11-23T13:35:38Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45185252002`）。
  - Secret scan `45185251937`（`head_sha=4b2be946a9f46651202e86794e42b7cff7bfa9fb` / `status=completed` / `conclusion=success` / `updated_at=2026-11-23T13:34:41Z`，链接 `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/45185251937`）。**交付判据 = 最终 tip 双 run 全绿。**
- 本交接所在文档提交为纯文档 tip：Build 0 run（既定 `paths-ignore` 口径），其 Secret run 按 W29 降级仅记录、不作 P0。
- 本地四闸门不代替 CI；CI 绿以最终 tip 的 run 四字段为准。

## 八、明确未做（逐条带中文段位）

压缩量纲同尺化余项（压缩量纲老号，行数前置未裁决）｜内核本体再分层（拆分三方老号，449/450 贴线）｜1M 满窗真压双硬限（真压两条老号）｜MCP 外部 https（明文三条老号）｜journal 二选一（journal 两条老号）｜思考块余项四件（思考块渲染老号）｜拥堵断流两条边界（拥堵断流老号）｜判序两条已知边界（维持已关行内）｜North 供给面核查｜价格维度（方向采纳未开）｜凭据轮换（用户侧）｜Nano Omni 与 Bolt E4B 会话轮（本波时间窗未行使）｜判据 7 波次盲区与协作用例文本形态（判据冻结）｜历史缺口不补造。

## 九、OPEN 挂账

逐条（触发信号 / 缓解动作 / 挂账号段位）见 `docs/analysis/w49-acceptance.md` §「OPEN 挂账」。本波零新开零销账；思考块渲染老号维持 OPEN、判序两条已知边界维持已关行内注记（依长期规约按推荐路径维持，主理人不代裁）。

## 十、交给外部审查团队的制品 + 边界声明

**制品**：本交接、验收物、派单卡、台账（W49 段 + 8 条注记）、`evidence/raw/w49/`（设备尾 + 10 张脱敏判据截图 + 脱敏判据证据 + 路径哈希对照表 + 闸门原始输出）、代码主体逐文件 diff（引擎/内核/落盘/门禁脚本/工作流）。

**不含**：会话历史 / 凭据 / 端点完整标识与密钥 / 未打码账号字段 / 设备序列号 / SSID / 本机路径 / 用户私人图片 / uiautomator 噪声 /「建议下一链做什么」。

**边界**：① 双键白名单只作用于未 tracked 两路，tracked 路永远不豁免；② 落盘白名单放宽的是固定主体枚举，不是长度或字符集，动态正文仍只进 ring；③ `digest` 只保证落盘可读，ring 仍保留完整 message；④ `workspace/xxx` 是沙箱子目录不是根别名（实测行为一致但易误读）；⑤ 1M 档为常规请求非满窗真压；⑥ 外审误报四项不据以改仓，只留证，出现新证据可再议；⑦ CI 绿以最终 tip 的 run 四字段为准。

> 本波止于交接，交由外部团队审查。
