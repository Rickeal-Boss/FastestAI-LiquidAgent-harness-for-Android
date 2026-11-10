# W12 交接 — 跨免费模型 × 跨上下文功能矩阵 + P1-2 标定收口（2026-11-08）

> 基线 `d2761e9`（W11 关波 docs 回填，主体 `504ed40`，CI `42389047452` 五 job 全 success）。
> 上游依据：桌面三份外审 `W11第0车深度审查与W12排期裁决.md`（报告 B）/ `harness-w11-review.md`（报告 C）/
> `w9-w11-review-20261108.md`（报告 A）；用户 2026-11-08 指令（多模型×多上下文功能矩阵 + 免费节点 + 推 CI）。
> 判定四态 ✅ / ⚠️ / ⛔ / ⛔vacuous。**「未触发」≠「已验证通过」；PC 侧结论不得写成设备侧可用。**
> 提交身份 `-c user.name="Rickeal-Boss" -c user.email="263587600+Rickeal-Boss@users.noreply.github.com"`；
> PAT 只以 URL inline 一次性使用、绝不落盘；免费节点 key 只在设备侧（`hasSecret:true`），不回显。

---

## 一、本波定位与结论

W12 = **功能矩阵验证波**（不是重做内核）。用户指令优先「多模型×多上下文（直至 1M）功能矩阵 + 免费节点 + 推 CI」，
**未开 journal 波**（W9-05 第 8 次顺延，如实登记，见 §六）。唯一代码改动 = 外审 P1-2 标定收口（服务 F1 多模态 + 触发 CI）。

| 项 | 结论 | 四态 |
|---|---|---|
| P1-2 带图轮次跳过标定 | 新增 `CalibrationPolicy.calibrationCharsForRound` + `GenerationRound` 改调 + 红态用例 + `EXPECTED_TESTS` 798→799 | ✅ `W12-01` CLOSED |
| 本地闸门 | G1 APK 70,309,888 B / G2 `799/0/0/0` / G3 `PASS ok=27 vacuous=1 violation=0` / G4 `30/30` | ✅ |
| 推送 / CI | `53d1465` 已推 `main`；CI run **`42445368887`** `completed/success`，**5 job 全 success** | ✅ |
| 真机 F1 多模态 | 200×120 红蓝真图 → 答「Top is red, bottom is blue」 | ✅ **设备侧首次行使**（W11 为 ⛔） |
| 真机 F3 / F4 / F5 | 文件读写闭环（含审批硬闸门）/ 记忆 write→冷启→read / skill_load notes-organizer | ✅ / ✅ / ✅ |
| 真机 F7 KV cache | 首轮 `0 cached` → 二轮 `1664 cached, 96.5%` | ✅ 正向；⚠️ 反向探针未取 ⇒ `W7-02` 仍 OPEN |
| 真机 F8 / F9 | 手动压缩确认框+执行（短会话 no-op）/ 自动压缩 `noop below_trigger` | ⚠️ / ⚠️ |
| W9-05 journal | **第 8 次顺延（未开波）**，交用户裁决 | ⛔ |
| 多模型覆盖 | 本会话功能矩阵主要在 **NV-MISTRAL 单模型**；端点切换 UI 未行使；跨模型 round-trip 属 W11 | ⚠️ 局限如实登记 |

## 二、三报告裁决（逐条回现役树，详见工作区 `_w12/W12-验收物.md` §1.2）

- **W9-04 / P0-1（报告 B 判「关闭过早」vs 报告 C 表扬）**：grep 证实四态唯一生产调用点 `StreamAccumulator.cacheStatus`
  在 main **零读者**（报告 B 字面成立）；但用户可见三态已由 `buildUsageLabel` 独立覆盖、第四态 `USAGE_NOT_OBSERVED`
  在 `UsageUpdated→buildUsageLabel` 路径**结构不可达**。**裁决**：`W9-04` 维持 `CLOSED`（G20 只允许
  `OPEN/CARRIED/CLOSED`），剩余面拆出 **`W12-02`（OPEN）= 给四态一个生产读者**。
- **P1-2（报告 B 独有）**：采纳并本波修（`core-agent` 余 1159，避开 `core-context` 余 4）。
- **P1-1 write 双计（报告 B）**：结论采纳，本波不修（需跨族能力位；F7 看原始 `cached_tokens` 绝对值不受分母影响），并入 `W11-05`。
- **W9-05（三报告一致要求开波）**：用户指令优先矩阵，**第 8 次顺延**，异议如实保留，交用户裁决。
- **悬账（报告 B/A）**：`W3-02`/`W7-05`/`W7-06`/`W8-04` 四条本波只登记归宿，不实现；G20 不新增守卫（新守卫扫空集 ⇒ G21 红）。

## 三、代码改动（4 文件，commit `53d1465`）

| 文件 | 改动 | 红态 |
|---|---|---|
| `core-agent/.../CalibrationPolicy.kt`（新） | `calibrationCharsForRound`：带图轮次返回 0 ⇒ 跳过 chars/token 标定 | 删守卫 ⇒ 新用例必红 |
| `core-agent/.../GenerationRound.kt` | 改调 + 移除唯一 `HardBudgetShrinker` import（392→391 行） | — |
| `core-agent/.../GenerationRoundTest.kt` | +1 `imageBearingRoundSkipsCharsPerTokenCalibration` | @Test 799 |
| `.github/workflows/build.yml` | `EXPECTED_TESTS` 798→799 | 严格 grep 双向对账 |

与 W11「标定放大 3.3 倍」对偶：W11 修高估方向，本波修低估方向。

## 四、本地闸门 + CI

| 闸门 | 结果 |
|---|---|
| G1 | ✅ APK `70,309,888 B` |
| G2 | ✅ `tests=799 failures=0 errors=0 skipped=0` |
| G3 | ✅ `result=PASS ok=27 vacuous=1 violation=0 fault=0`（G20 `PASS`，ID 数以 `arch-guard` 实跑输出为准〔W15 订正：原数为 W12 时点快照，非现役常量〕；台账新增后 66 行复跑仍 PASS） |
| G4 | ✅ `30/30 PASS（205s）` |
| push | `d2761e9..53d1465 main -> main`（`--dry-run` 先验连通；首次 URL 缺 password 段失败，改 `user:PAT` 形式成功） |
| CI | run **`42445368887`** = `completed/success`；Unit tests / Assemble Debug / Arch guard / Lint / Secret scan 全 success |

## 五、真机功能矩阵（OPPO PDRM00 / SDK33，`adb -P 15037`，主测 NV-MISTRAL）

| # | 功能 | 证据 | 四态 |
|---|---|---|---|
| F1 | 多模态 | 200×120 红蓝图 →「Top is red, bottom is blue」；`1.6k in / 69 out (1600 cached, 95.9%)` | ✅ |
| F2 | 音频 | 沿用 W11（NV `/v1/audio/speech` 404） | ⛔ 平台阻断 |
| F3 | 文件读写 | 审批框 `file_write {"path":"probe.txt","content":"HELLO"}` → 本会话允许 → `wrote 5 chars` → `file_read ✅31ms HELLO` → `run-as cat`=HELLO | ✅ |
| F4 | 记忆 | `memory.json` 增 favorite-color → force-stop 冷启 → 答「teal」 | ✅ |
| F5 | skill | `skill_load {"name":"notes-organizer"}` ✅26ms + `[SKILL BODY]` 安全前言 | ✅ |
| F6 | MCP | `usesCleartextTraffic=false` + `W5-03` | ⛔ 平台阻断 |
| F7 | KV cache | `0 cached, 0.0%` → `1664 cached, 96.5%` | ✅ 正向；⚠️ 反向未取 |
| F8 | 手动压缩 | 确认框（含缓存重建警告）→ 压缩 →「没有可压缩的内容」（短会话 no-op） | ⚠️ 主体 ⛔vacuous |
| F9 | 自动压缩 | `auto_compact=noop reason=below_trigger`（多轮一致） | ⚠️ 未触发 |
| F10 | 长上下文 | 未灌阶梯（上下文显示 `1.7K / 507.9K`） | ⛔ 未行使（`W9-11` 阻断） |
| F11 | 分块装配 | 未行使 | ⛔vacuous |

**环境坑（本波实证）**：审批对话框按钮 y 坐标靠猜会落空（`本会话允许相同调用` 实测 bounds 中心 (540,1350)，
猜 (540,1128) 落在两行之间）⇒ **对话框按钮一律 uiautomator dump 取 bounds 再点**。`pm install -r` 前先
`KEYCODE_WAKEUP`+`input swipe` 解锁（灭屏会挂）。`run-as ... sh -c` 的引号会被 adb 吃 ⇒ 谨慎构造。

## 六、未做 / 明确不做（防误读为漏项）

- **W9-05 journal / FreezeClock 第 8 次顺延**：用户指令优先矩阵 + 推 CI；三份外审一致要求开波，异议已在台账保留。
  **交用户裁决下波是否强制开波**；不关号、不写「永不实现」。
- **多模型×功能交叉格**：本会话未行使端点切换 UI（切换器需进 Settings，未展开以控范围）；跨模型 round-trip 属 W11。
  5 个免费端点已预置设备侧待用。
- **F7 反向探针 / F8 真压缩 `dropped=N` / F10 长上下文阶梯**：分别需改稳定前缀首字节 / 长会话 / 灌阶梯题面，本波未取。
- 四态接 `buildUsageLabel`（`W12-02`）、`W11-01/03/04/05`、`W9-10` 守卫专项、`feature-settings` 拆分、
  `prompt_cache_key`：全部未做，台账有号有落点。
- 设备侧 1M 真压（`W9-11`）、MCP 明文联调（`W5-03`+G15b）、音频输出（404）：平台/内核阻断，如实 ⛔。
- 本波**零新增 arch-guard 守卫 / 零新增 selftest case**（`W9-10` 未开）。

## 七、挂账状态（唯一真值源 = `docs/06-deferred-ledger.md`）

| ID | 状态 | 本波动 |
|---|---|---|
| `W12-01` | **`CLOSED`** | P1-2 标定收口（新文件 + 红态用例 + EXPECTED_TESTS 799） |
| `W12-02` | `OPEN` | 四态生产读者（外审 P0-1 剩余面，本波新开） |
| `W9-04` | `CLOSED`（维持） | 加 W12 复核注：字段/透传/命中率关闭成立，四态读者拆 `W12-02` |
| `W9-05` | `OPEN` | **第 8 次顺延**，交用户裁决 |
| `W7-02` | `OPEN` | 设备侧正向已取，反向探针未取 |
| `W11-01/03/04/05` | `OPEN` | 未动，落点已登记 |

## 八、下一波（W13）候选入口（按台账顺序，不预写结论）

W9-05 journal（若用户裁决强制）/ `W12-02` 四态读者 / 拆分三方（`core-context` 余 4、`feature-chat` 余 12、
`feature-settings` 余 8）/ `W11-04` jsonMode×tools / `W11-05`+`W12-02` 分母族归一 / 真机 M8 双向探针 + M9 真压缩。
**禁调 `LINE_CAPS` / `TOTAL_LINE_CAP`；`EXPECTED_TESTS` 单一写口；取证摘要抄入 handoff 正文。**