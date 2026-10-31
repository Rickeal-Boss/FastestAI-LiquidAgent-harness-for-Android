# 止损台（S2 判据跑分）

W2 的止损判据：**20 条题面、单工具调用成功率 ≥18/20、0 条 8 轮不收敛**。
本目录是跑这套判据的三件套：**转发桥**（取证）+ **真机驱动**（发题）+ **机械判分**。

> 首次实跑结果（2026-10-31，GLM-4.7 / OPPO PDRM00）：**17/20，判据 1 未达标**。
> 结论与归因见 `docs/02-roadmap.md` §二bis 与 `docs/handoff-20261031-wave25.md`。

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
python scripts/stoploss/glm_bridge.py &

# 2) 让 App 指向桥：endpoints.json 里放一条
#    baseUrl=http://127.0.0.1:8765/v1, model=glm-4.7,
#    allowPrivateNetwork=true, hasSecret=false
#    （baseUrl 含 127.0.0.1 ⇒ requiresApiKey=false，不用填 key）

# 3) 发 20 题（每题先点「新对话」；审批卡自动点「允许一次」）
python scripts/stoploss/drive_w2_stoploss.py

# 4) 判分（以桥日志为唯一真值源）
python scripts/stoploss/score_w2_stoploss.py     # 产出 w2-stoploss-report.md
```

断点续跑：`python scripts/stoploss/drive_w2_stoploss.py --start W2-T08`（加 `--no-reset` 保留现场）。

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

## 判分口径

按 `scripts/fixtures/w2-tool-prompts.json` 的机器字段：`expect.tool` / `expect.args` /
`expect.final_contains` / `expect.max_rounds`，归因分类 `param | tool_name | not_called | not_converged`。

⚠️ 已知夹具缺陷（W2.6 修）：`scoring.pass` 的「≤2 轮」与 per-prompt `max_rounds`（最高 6）互斥；
T10/T20 的 `$note` 与 `expect.tool` 互斥。**修完必须原样重跑，不许拿旧数据按新口径重算。**
