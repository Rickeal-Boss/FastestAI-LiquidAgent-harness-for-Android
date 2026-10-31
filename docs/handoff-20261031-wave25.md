# W2.5 交接 — S2 止损实跑 + 外审 T 系列收口（2026-10-31）

> 仓库：`FastestAI-LiquidAgent-harness-for-Android`（本地 `harness-v2/`）
> 基线：`37465b4`（W2 收口）→ 本波在工作树上完成，提交见文末
> 权威判据：`docs/02-roadmap.md` §二bis（W2.5）；风险登记：`docs/04-risk-register.md`

---

## 一、本波结论（先看这段）

| 项 | 结果 |
|---|---|
| **判据 1（单工具成功率 ≥18/20）** | **17/20 ❌ 未达标** |
| **判据 2（0 条 8 轮不收敛）** | **0 条 ✅**（最长 4 轮） |
| **W3 是否启动** | **否**（按 `02-roadmap.md` §二 红线：判据 1+2 同时成立才继续） |
| 代码交付 | 外审 T-1..T-6 + D2D 迁移 + `has()` 拆分 + M1 缓存可观测 + CI 测试数 361 |
| 本地闸门 | fulltest **361/0/0/0（11 模块）**；arch-guard **23/0/0**；selftest **24/24**；lint 通过 |
| 真机验收 | T-4 三步全过（首次弹卡 → 同会话同参不弹 → 新对话后重弹）；T-3 卡片链路正常 |

**失败三题**（证据在跑分桥日志，逐条可复算）：

| 题 | 期望 | 实际 | 归因 |
|---|---|---|---|
| W2-T10 | 调 `file_read`（路径逃逸被沙箱拒） | 未调工具，直接以「path traversal」拒绝 | `not_called` |
| W2-T11 | 调 `file_read`（绝对路径被拒） | `file_read` 被拒 → `file_list({})` → `file_list(相对路径)` → 答复，4 轮（上限 3） | `tool_name` + `not_converged` |
| W2-T20 | 不调任何工具，说明没有删除能力 | 调 `file_list({})` 后说明「没有删除工具」 | `not_called` |

**归因判断**：三条全部是模型的工具选择 / 拒答策略，**没有一条是 harness 执行错误**。
所有 tool result 都正确（沙箱拒绝、文件读写、剪贴板内容都符合预期），事件序列与配对完整。

---

## 二、跑分方法（复现所需的一切）

| 项 | 值 |
|---|---|
| 模型 | GLM-4.7（BigModel / 智谱），`maxOutputTokens=8192`，thinking 默认开 |
| 链路 | App → `adb reverse tcp:8765` → 宿主 Python 转发桥 → `open.bigmodel.cn/api/paas/v4/chat/completions` |
| 设备 | OPPO PDRM00 / Android 13 / SDK 33（`13309cc8`） |
| APK | `sha256=1de01ef2cbed85e1fe4e38a8ba75456f…`，HEAD `37465b4`，工作树干净 |
| 端点配置 | `baseUrl=http://127.0.0.1:8765/v1`、`model=glm-4.7`、`allowPrivateNetwork=true`、`hasSecret=false`（`requiresApiKey` 对 127.0.0.1 为 false） |
| 协议 | **每题先点「新对话」**；审批卡由驱动点「允许一次」 |
| 取证 | 桥 JSONL：每请求一行，含完整 request + 解析后的 response（tool_calls / finish_reason / usage） |
| 判分 | 按 fixture 的 `expect.tool/args/final_contains/max_rounds` 机械判定；`last_user` 与题面逐字比对（注入自证） |

**为什么每题新对话**：首轮 attempt 输入框残留把 T08+T09 拼成一条题面（`glm_bridge_attempt1.jsonl` 留档）。
换新对话后题面可逐字自证；代价是跨 run 缓存不可观测、压缩链路未行使（记 `vacuous`）。

---

## 三、夹具缺陷（W2.6 必须先修）

1. `scoring.pass` 写「≤2 轮内合法 tool_calls」，per-prompt `max_rounds` 却最高 6（T15/T17/T19）—— 互斥。
2. T10/T20 的 `$note` 判据（「没拿到内容且没编造」/「不许假装删了」）与机器字段 `expect.tool` 互斥：
   按 note 判可算 19/20，按字段判 17/20。**本轮按字段判（更保守），不按 note 重新解释结果。**
3. 判据 2 的「0 条触发 `MaxRounds`」（policy=12）与「0 条 8 轮不收敛」是两个阈值。

**修法**：给 fixture 加 `expect.mode`（`tool_required` / `no_tool` / `refusal_ok`），把「期望路径」
与「通过判据」分开；**改完必须原样重跑 20 题**，不许拿本轮数据按新口径重算。

---

## 四、缓存实测（与初版结论不同，以 `02-roadmap.md` §二bis 为准）

| prompt_tokens | 第 2 次 cached |
|---|---|
| 574 / 586 / 1691 / 1923 / 2292 / **2303** | 0 |
| **2502** | **2432** |
| 4111 / 6426 / 7352 | 4096 / 6400 / 7296 |
| 真机 928（会话内第 2 轮） | **896** |

- **写入门槛在 (2303, 2502]**；**命中不受门槛约束**（928→896 是命中更长条目的前缀）；
- 粒度 64；20 题整体 Σcached/Σprompt = **5.5%**（1792/32657），首次命中 seq=42；
- **禁止为凑缓存加长 system 提示词**（800 全价 vs 4096×0.5 = 2048 当量，反而贵 2.5 倍）。

---

## 五、本轮 vacuous（不许写成通过）

- **I1 跨轮 history 分叉**：每题新对话 ⇒ `history` 恒空 ⇒ 未行使；
- **T-4 的 UI 侧在跑分里未行使**（跑分用「允许一次」），已由独立真机验收补上（见 §六）；
- **压缩链路**：单题上下文远低于 32K ⇒ 0 次压缩 ⇒ 单测 ✅ / 真机 ⛔。

---

## 六、真机验收（新包，2026-10-31 20:1x）

| 步骤 | 动作 | 结果 |
|---|---|---|
| 1 | 同会话发「写 cache-probe.txt」 | **弹卡** → 点「本会话允许相同调用」→ `file_write{path,content}` 执行 |
| 2 | **同会话**再发同一题面（模型参数完全相同） | **不弹卡**，直接执行（cache 命中） |
| 3 | 点「新对话」后再发同一题面 | **重新弹卡**（`newConversation()` 清了 cache） |

证据：`_w2tools/verify_t4_result.json`；桥日志三条 `file_write` 参数逐字相同。
这一步同时验证了 **T-3（单写者）**：审批卡仍然正常出现、点击生效、轨迹与结果回填完整。

---

## 七、环境教训（会再遇到）

1. 🔴 **沙箱代理端口跨会话漂移**（昨日 6130 → 今日 4558）。桥的上游 `ConnectionRefused(10061)` 就是这个；
   启动桥前必须 `env | grep https_proxy` 现取端口，别用默认值。
2. 🔴 **`adb reverse` 随 daemon 回收失效**：映射必须在「点发送」前一刻重建，否则设备侧只报
   `ConnectException`、服务端零日志。
3. 🔴 **`run-as pkg sh -c '...'` 的引号会被 adb 拼参吃掉**：必须把整条命令作为**一个** argv 传给 adb，
   否则 `sh -c` 只吃 `rm`，删除静默不执行（本轮 T05 因此看到上一波遗留的 `notes.txt`）。
4. 🔴 **输入框残留会改题面**：`input text` 追加写入，发送失败后残留会与下一题拼接（T08+T09 事故）。
   协议改为每题「新对话」+ 失败重启应用重试一次。
5. **GLM-4.7 偶发不发 `finish_reason` / usage 帧**（实测 T02/T04/T14/T18/T19）：正文完整、`[DONE]` 到达。
   引擎的 `terminationReason` 走 `sawTerminalMarker ⇒ STOP`，**用户无感**；但判分器不能把
   「finish_reason 缺失」当未结束（否则每题挂到超时）。
6. **审批卡自动放行**：`file_write` / `clipboard set` 会弹卡；跑分驱动必须扮演在场用户点「允许一次」，
   否则 5 分钟后按拒绝处理，失败会被误记成模型问题。

---

## 八、下一步（W2.6 归因波，不是 W3）

1. **修 fixture 口径**（`expect.mode`），原样重跑 20 题；重跑前不改任何提示词/阈值（改了与第一次不可比）。
2. 若仍 <18/20，按失败类别走：
   - `not_called` 聚集 ⇒ 查 `TOOL_GUARDRAILS` 与 `tool_choice` 语义；
   - `tool_name` 聚集 ⇒ 工具名可发现性（原生通道下提示词里没有工具清单，是 A5 的已知代价）；
   - `not_converged` 聚集 ⇒ **先补「本轮文本区间 + reasoning 回传」再重跑**，否则归因不可信。
3. **W3 若启动，先做三笔前置**（都不是一行改动）：
   - `AgentRunner` 194/200 行，加 journal 参数必超 200 ⇒ 先拆门面；
   - `AttemptObserver` 取证缝（engine + adapter 各加 `log: (String) -> Unit`，装配点 **2 处**：
     `AppContainer` 与 `EndpointProbe`；探针 key 改 `probe:` 前缀以排除计数污染）；
   - `AgentLogStore` 的 INFO 入口 `removeFirst()` 会顶掉 WARN/ERROR（与类内 KDoc 相反），
     attempt 日志上线前必须先修。
4. **W7 挂账**：`allowClipboardRead` 的设置页开关（保存后下一次 run 生效，policy 是 run 级快照）。

---

## 九、提交与 CI

- 本波提交：见 `git log --oneline -1`（提交信息含「W2.5」）。
- `build.yml` 的 `EXPECTED_TESTS` 已同步 **361**；CI 触发条件未变（push main + PR + dispatch）。
- 若 CI 红：先看 `Reconcile EXPECTED_TESTS` job 的 `actual` 与本地是否一致（本地 fulltest 以
  `TEST-*.xml` 计，CI 以 `grep -rnE '^\s*@Test\b'` 计，两者口径不同但本仓已对齐过）。
