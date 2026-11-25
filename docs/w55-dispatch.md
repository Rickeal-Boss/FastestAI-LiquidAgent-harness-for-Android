# W55 派单卡 — 落盘形态回补 + 收尾契约/账实订正 + 多模型真机冒烟

> 现役仓 `harness-v2/`（`Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android`，`main`）。
> 基线 = W54 tip `63f3ff945f0fab93055d11b97776d1c66aa834c9`。
> 判定四态 `✅ / ⚠️ / ⛔ / ⛔vacuous`；「未触发」≠「已验证通过」；推断带 caveat、不写成事实。本卡不写复合挂账号，段位用中文描述。

**新开 0 / 销账 0**

> 两个数只写在本抬头；交接 / 验收物 / 台账 / checklist 不复述。
> 台账权威版 = `docs/06-deferred-ledger.md`（`### W55 段` + 五条老号行内注记）。

## 一、外审分歧裁决（6 份报告的 5 项）

| # | 分歧 | 裁决 | 依据 |
|---|---|---|---|
| 1 | N-1 键同一性（`W53-W54审查` 称误拒仍在） | **不采纳，维持 W54 定谳成立** | 现役白名单第 68 行 = `"parallel_tool_calls"`、用例键同为 `parallel_tool_calls`、`validateAdvanced` 通过；该报告自曝键名被渲染链吞字为其证据假象 |
| 2 | gitleaks 误报治理（建议加 allowlist/exclude） | **不采纳，维持改形状** | 派单冻结 schema 零增删；allowlist 扩大免检面；但措辞精确化 = 不为生产日志拼接新增 allowlist/exclude，保留既有精确标识符与夹具 allowlist |
| 3 | A4 记忆注入 / A5 代次门 / A6 双终态「漏登记三条」 | **转 OPEN 触发信号，不纳入本波代码** | 不在第0车白名单；A5/A6 受 feature-chat 余 1 行 + 文件域双限；纳入即违反冻结零追加 |
| 4 | 第0车 P0 落盘形态漏登记（3 份报告一致） | **采信补回** | 已回源证实新版账行 140 字符不被旧正则匹配、两条 `endpoint_store` 未登记 |
| 5 | 收尾契约第 11 次 / 账实订正 / P2 五条 | **采信，波内文档订正，严禁第 5 提交** | 派单卡第0车明确 |

## 二、波内范围（第 0 车，正向白名单）

- `app`：`DiagnosticLogSink.kt`（PROBE_FORMS +3 条整行正则）、`DiagnosticLogSinkTest.kt`（3 正向 + 1 端到端 + 反向断言）
- `.github/workflows/build.yml`：仅 `EXPECTED_TESTS` 900→904 + 变更史注释
- `.github/workflows/secret-scan.yml`：既有 step 后追加一个 selfcheck step（零新 job / G 编号）
- `scripts/w55-probe-form-sync.sh`（新增）：从源码现场提取正则 + 必落盘样本逐条整行匹配，删正则必红
- 文档订正：`docs/handoff-20261125-wave54.md`、`docs/analysis/w54-acceptance.md`、`docs/w54-dispatch.md`（8+ 处口径）
- 台账：`### W55 段` + 五条老号行内注记（状态原样、pipe=7、零回开、零新开 `W55-` 行）

**冻结（继续适用，零追加）**：判据集合、G 编号、CI job、schema 零增删；`LINE_CAPS` / `TOTAL_LINE_CAP` 不动；不碰 `core-kernel` / `GenerationRound.kt`（仅 `ContextCompressor.kt` 注释订正，写点字符串未动）。

## 三、出口判据

1. 四闸门全绿（G1 rc=0 / G2 `violation=0` / G3 自测 / G4 `tests=904 failures=0`）。
2. `EXPECTED_TESTS` 与严格 `@Test` 双向相等（900 → 904）。
3. P0 三条形态：删正则必红（JVM `18 tests / 3 failed` + 脚本 rc=1）/ 加回变绿，端到端落盘用例真行使，反向样本全拦。
4. 真机轻量冒烟：关键端点回声 1 格 + 落盘白名单设备侧复核 1 格；密钥留设备、不回灌。
5. CI（W56 订正，按实态）：最后代码提交 `8307c7e` Build 绿（`45561430401`）+ ① 的 Secret 判红-修复链如实给号（`45561430501` FAILURE → ② `45562594649` success）+ 最终 tip `19e4aa5f` Secret 绿（`45563018742`），公开 API 四字段实取。③ 纯文档回填提交按 W50 降级口径不计入交付判据、计入提交实态。

## 四、第 1 车（条件触发，不自动解禁）

| 项 | 触发 / 前置 | 动作 |
|---|---|---|
| G19 扫描面扩容 | 需先设计 12 处日志拼接整改 + VACUOUS 基线重定 | 守卫重设计波 |
| PNG 二进制门禁 | 需接既有脱敏 step | 完整性 + 元数据检查接入 |
| `McpServerEditScreen` URL 校验 | 与 `UrlGuard` 对齐 | 保存期给可行动提示 |
| A4 / A5 / A6 三条 P1 | 用户单点开波 + A5/A6 需先腾 feature-chat 行 | 单独开波，不合并销账 |
| 自动压缩触发线按档位化 | 压缩量纲老号余项 | `COMPACT_TRIGGER_CHARS` 档位化 |

## 五、触发信号监控

| 监控项 | 触发即动作 |
|---|---|
| 落盘白名单：任一生产写点改形状 | 同提交改 `PROBE_FORMS` + 必红 + 机械自检停红即阻断 |
| 断词老号：`retry:` 帧透传 / 可点重试入口 | 真机或 MockWebServer 前端复现 ⇒ 下一波单独开波 |
| 思考块老号：四态截图 / 玻璃节点余项 | 判据级截图齐 + 协作用例升级行为级 ⇒ 重新审议销否 |

## 六、仍单独裁决、不自动解禁

内核再分层 / 449-450 上限处理、压缩量纲同尺化余项、安全波整包、1M 满窗真压双硬限、MCP 外部 https + G15b 明文闸门解禁、journal 二选一、North 与 Dots3 的 404 供给方公告核查、凭据轮换（用户侧）。价格维度继续「方向采纳、本波不开」。
