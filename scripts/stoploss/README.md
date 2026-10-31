# 止损台（S2 判据跑分）

W2 的止损判据：**20 条题面、单工具调用成功率 ≥18/20、0 条 8 轮不收敛**。
本目录是跑这套判据的四件套：**转发桥**（取证）+ **真机驱动**（发题）+ **机械判分** + **离线自测**。

> 首次实跑结果（2026-10-31，GLM-4.7 / OPPO PDRM00）：**17/20，判据 1 未达标**。
> 结论与归因见 `docs/02-roadmap.md` §二bis 与 `docs/handoff-20261031-wave25.md`。
> **那份数据是 v1 口径（rounds = HTTP 请求数）产出的，不能按 v2 口径重算** —— 判分器有版本门挡这件事。

## 为什么需要桥

判据要看**每一轮的完整请求与响应**（tool_calls 名称/参数/轮次/usage），这些只存在于 HTTP 层；
UI 截图与日志都给不出可机械判定的证据。桥把设备流量转发到真实端点，同时落 JSONL：

```
设备 → adb reverse tcp:8765 → glm_bridge.py → https://open.bigmodel.cn/api/paas/v4/chat/completions
```

API Key 只在宿主进程的环境变量里：**不进 APK、不进设备、不落盘**。

## 跑一次

```bash
# 0) 依赖：python3、adb（在 PATH 或显式给 ADB）
export ADB=/path/to/platform-tools/adb
export BIGMODEL_KEY='<your-key>'          # 绝不写进任何文件

# 1) 起桥（自动从 https_proxy 取端口；也可显式 BRIDGE_PROXY_PORT=xxxx）
#    启动会打印 run_id 并写 <BRIDGE_LOG>.run；已有旧日志会被改名归档而不是覆盖
python scripts/stoploss/glm_bridge.py &

# 2) 让 App 指向桥：endpoints.json 里放一条
#    baseUrl=http://127.0.0.1:8765/v1, model=glm-4.7,
#    allowPrivateNetwork=true, hasSecret=false
#    （baseUrl 含 127.0.0.1 ⇒ requiresApiKey=false，不用填 key）

# 3) 发 20 题（每题先点「新对话」；审批卡自动点「允许一次」）
#    开局读 <BRIDGE_LOG>.run 取 run_id，起跑时写 run_manifest.json
python scripts/stoploss/drive_w2_stoploss.py

# 4) 判分（以桥日志为唯一真值源；manifest 缺失或版本不符会拒判）
python scripts/stoploss/score_w2_stoploss.py     # 产出 w2-stoploss-report.md
```

断点续跑：`python scripts/stoploss/drive_w2_stoploss.py --start W2-T08`（加 `--no-reset` 保留现场）。

离线自测（不连网、不连设备，改判分器后必跑）：

```bash
cd scripts/stoploss && python selftest_scoring.py    # 全 PASS 才 exit 0
```

## 四条必须知道的坑（都从真实事故里长出来）

1. **`adb reverse` 随 daemon 回收失效**：映射必须在「点发送」前一刻重建；否则设备侧只报
   `ConnectException`，服务端一条日志都没有（会被误判成模型没调工具）。
2. **输入框残留会改题面**：`input text` 是追加写入，发送失败后的残留会与下一题拼接
   （实测把 T08+T09 拼成一条）。所以每题先点「新对话」，失败则重启应用重试一次。
3. **审批卡必须有人点**：`file_write` / `clipboard set` 会弹卡，5 分钟没人点就按拒绝处理，
   失败会被误记成模型问题。驱动扮演在场用户，只点最保守的「允许一次」（不用「本会话允许」，
   避免把审批缓存也一起测掉）。
4. **供应商可能不发 `finish_reason`**：GLM-4.7 实测偶发（正文完整、`[DONE]` 到达）。
   引擎侧 `sawTerminalMarker ⇒ STOP` 用户无感，但判分器不能把「finish_reason 缺失」当未结束。

## run 边界：run_id + 归档 + 多文件读

桥重启后 `seq` 从 1 重来。若判分器按 `last_user` 全量过滤，上一轮的记录会混进来：

- 判分侧：轮次虚高 ⇒ `not_converged` 假阳性；
- 驱动侧：旧日志尾条若非终态 ⇒ `wait_done` 傻等 300s；若恰是终态 ⇒ 开局即满足，题面没注入就返回。

修法是**把 run 边界做成字段**，而不是靠操作者记得清空日志：

| 机制 | 行为 |
|---|---|
| `run_id` | 桥启动时生成 `YYYYmmdd-HHMMSS-<pid>`（可 `BRIDGE_RUN_ID` 覆盖），写进**每条** record |
| `<BRIDGE_LOG>.run` | 桥启动时写入当前 run_id；驱动开局读它，只认这个 run |
| 归档 | 启动时若 `<BRIDGE_LOG>` 已存在且非空，改名为 `<BRIDGE_LOG>.<run_id>.jsonl`，**不覆盖不删除** |
| `log_file` | 每条 record 记当前日志文件名，配合 `seq` 构成复合键 |
| `BRIDGE_LOG` 多值 | 支持逗号分隔与 glob，如 `glm_bridge.jsonl,glm_bridge*.jsonl`；`bridge_log.load_records()` 合并后按 `(ts, seq)` 排序，并给每条打 `_src` |

驱动与判分器都通过 `bridge_log.py` 读日志（口径只实现一次）：驱动用 `live_spec()`（当前文件 + 归档），
判分器用 `BRIDGE_LOG` 全量合并。

**同一文件内 `seq` 重复 = 数据损坏**：只可能是两个桥进程写同一文件或人工拼接。判分器此时
**拒绝出报告**（exit 3），不挑一条用。

## 判分口径（fixture v2）

判定语义**全部**来自 `scripts/fixtures/w2-tool-prompts.json`，判分器不认识任何题号。
v1 那套 `EXPECTED_FAILURE = {T10,T11,T16}` + `pid == "W2-T16"` 行内正则是「夹具装饰化」的
典型：改夹具不改结论。v2 的字段与三值 `expect.mode` 见夹具 `$comment`。

### 轮次 = 逻辑轮，不是 HTTP 请求数

```
round = 1 + 该 run 内该题 request.messages 长度单调递增的次数（含最终答复轮）
```

引擎层 `GenerationRound(maxRetries=1)` 的网络重试会**重新 generate**，同一逻辑轮的两次请求
`messages` 数组完全相同 ⇒ 不计轮。报告分列三个数：

| 指标 | 含义 |
|---|---|
| 逻辑轮次 | 判据用这个（对比 `expect.max_rounds`） |
| HTTP 请求数 | 桥日志里该题的记录条数 |
| 重试数 | HTTP − 逻辑轮次 |

旧口径 `rounds = len(records)` 把一次重试算成一轮，T01 这种 `max_rounds=2` 的题会凭空「不收敛」。

### 归因分类法

`ok / inject_fail / not_called / over_called / tool_name / param / tool_error /
tool_not_failed / fabrication / final_mismatch / empty_final / not_converged / harness_fault`

两条拆分值得单独记：

- `not_called`（该调没调）与 `over_called`（不该调却调）**分开统计**：前者是模型不肯用工具，
  后者是模型乱用工具，修法完全相反。
- `tool_error`（工具执行失败）与 `param`（参数错）**分开统计**：参数错是模型的问题，执行失败
  可能是供面/沙箱的问题。工具失败判定只用**本仓专属措辞**（`escapes sandbox` / `disabled by
  policy` 一类）；`invalid` / `not found` / `denied` 这种会出现在被读文件正文里的通用词**不进**
  全局表 —— 否则「成功读到一个写着 invalid 的文件」会被判成工具失败。
- `harness_fault`：本题期望的工具根本不在 `request.tools` 清单里。这是供面缺失，不是模型失败；
  报告里单独列一节「harness 供面缺失」。

### 版本门

报告头写 `fixture_version`。判分器读 `run_manifest.json`（驱动起跑时生成）：

```json
{ "fixture_version": 2, "apk_sha256": "...", "git_head": "...", "model": "glm-4.7",
  "thinking": "off", "bridge_run_id": "20261031-120000-4242",
  "started_at": "2026-10-31T12:00:00", "prompts_count": 20 }
```

`fixture_version` 与夹具不一致、或 manifest 缺失 ⇒ **拒绝判分**（exit 2，中文原因）。
这是防止「旧日志按新口径翻案」的机械化闸门：口径变了就必须重跑，不许重算。

## 退出码

| 码 | 含义 |
|---|---|
| 0 | 正常出报告 |
| 2 | 版本门拒绝（manifest 缺失 / fixture_version 不符 / 缺 bridge_run_id） |
| 3 | 日志数据损坏（同一文件内 seq 重复） |
| 4 | 找不到任何日志文件 |
