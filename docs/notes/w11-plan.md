# W11 计划 — 跨免费模型综合功能矩阵（接 W10 收尾，2026-11-07）

> 主理人（祁研深）亲自产出。来源依据：`docs/handoff-20261107-wave10.md`（W10 进行中快照）+ `docs/notes/w10-plan.md`（端点/施工单/矩阵分派）+ 用户 2026-11-07 新输入（免费节点 key + 至 1M 上下文 + 功能矩阵：多模态输入/音频输出/工作空间文件读写/记忆/skill/MCP/KVcache 命中/手动自动压缩）。
> 判定四态：✅ 通过 / ⚠️ 部分 / ⛔ 不适用 / ⛔vacuous（判据没被行使）。**「未触发」≠「已验证通过」；PC 侧结论不得写成设备侧可用；1M 档≥2 重放稳定。**
> 方案席（沈思远）本波 429 配额耗尽失败，按知识库 §6 由主理人接手。
>
> 🔄 **W11 第 0 车订正（2026-11-07 晚，主理人）**：本计划写于 W10 交接的「未提交」快照，
> 与 `HEAD:98916eaa` 的既成事实有三处冲突，已逐条订正（原文保留在 §七 风险留痕）：
> ① §五.1「提交→推送→CI」改为**核对已推 CI**；② F8/F9 预写 ✅ 收回为「待行使」；
> ③ §六 C 组误把 `feature-chat` 余 27 写成 `core-context`。另 launcher 以真机实测为准，
> 不照抄两处互相矛盾的文档。所有行号锚 `HEAD:98916eaa`，工作树已漂移，定位以符号为准。

---

## 一、W11 定位与目标

W11 = **W10 收尾** + **跨免费模型综合功能矩阵验证**。

1. **W10 收尾（并入 W11 前半）**：G2/G3 复跑回填 → 提交（LF 归一、确认无 `_w10/` 夹带）→ 推送 → CI 五 job 全 success → 设备侧矩阵补完（M1/D6/D7/M4/M5/M6/M8/M9/M10 + M3/M7 阻断复核）。
2. **W11 综合矩阵（后半）**：用户点名的 9 类功能，在全部可用免费模型上做设备侧真机行使（含至 1M 长上下文），取四态证据。

---

## 二、端点与模型分配（唯一真值源，key 不落盘）

凭据纪律：key 只从文件读入内存注入（`<凭据文件：OpenRouter API Key（本地路径已隐去）>`、`<凭据文件：NVIDIA API Key（本地路径已隐去）>`）；任何文档/产物不落 key 字面量。

| 供应商 | base URL | 模型 slug | 能力 | W11 用途 |
|---|---|---|---|---|
| OpenRouter | `https://openrouter.ai/api/v1` | `dots-studio/dots-3-note-preview:free` | 工具+视觉双通，标称 512k | 多模态/工具/KVcache 主测；512k 封顶（>512k OR 拒绝 400） |
| OpenRouter | 同上 | `cohere/north-mini-code:free` | 推理型（content≥1024） | 代码/推理往返；缓存反向分支恒 0 ⇒ vacuous |
| NVIDIA | `https://integrate.api.nvidia.com/v1` | `nvidia/nemotron-3.5-super-vl-120b-a12b` | 视觉+工具+1M | **正向前哨**：KVcache 双向、1M 窗口、设备视觉描述替代主模型收截图 |
| NVIDIA | 同上 | `mistralai/mistral-large-4-1t-a52b` | 推理型，1M | KVcache/M8 正向；1M 超时（PC 侧已实证） |
| NVIDIA | 同上 | `stepfun-ai/step-5-preview` | 文本 | 冷启慢（600s 窗）；M8 same-2/3 字段缺失非 0 |
| NVIDIA | 同上 | `deepseek-ai/deepseek-v4.1-flash` | — | ⛔ 排除（HTTP 000 超时） |
| NVIDIA | 同上 | `wan-ai/wan2.2-animate-2-14b` / `nvidia/vsr` | 非 chat | ⛔ 排除（404） |

🔴 **端点纠偏（用户原话订正）**：用户称「其余通过 build.nvidia.com 测试」——`build.nvidia.com` 是网页非 API；NV API 一律走 `https://integrate.api.nvidia.com/v1/chat/completions`。

---

## 三、功能测试矩阵（用户点名功能逐条）

前置纪律（违反 ⇒ 该项判据整体无效）：`max_tokens` 基线 ≥512、思考型 ≥1024、xhigh/1M 档 ≥1500；判据按 `finish_reason`/`tool_calls` 不按 `content`；四元组 `max_tokens/prompt_tokens/completion_tokens/finish_reason` 逐请求落盘；PC 侧与设备侧分开记账。

| # | 功能 | 设备侧行使方式 | 判据 | 预期四态 |
|---|---|---|---|---|
| F1 多模态输入 | 视觉 | D7b 视觉往返（200×120 红蓝真图，max_tokens≥512）；禁用 1×1 | 答「Red Blue」；用量行记视觉 token | nv-vl/dots ✅；north/mistral/step5 ⛔（非 VL） |
| F2 音频输出 | NV `/v1/audio/speech` | W10 判 404/平台阻断 ⇒ **W11 复核一次**：试 `integrate.api.nvidia.com/v1/audio/speech` 与公开 TTS 路径 | 200+音频字节 或 404/supported_voices=null | ⛔ 平台阻断（如实；不重复纠缠） |
| F3 工作空间文件读写 | file_write/file_read | 授权一次；写固定串→读回逐字一致 | 落盘内容逐字一致 + journal 记 file_write/file_read | ✅（沿用 W9-00 判据） |
| F4 记忆 | memory_write→冷启动→memory_read | 写一条→杀进程→重开→问→命中 | 冷启动后回答引用所写内容 | ✅ |
| F5 skill | skill_load | 报内置技能列表（两例内置） | journal 记 skill_load + 技能名 | ✅ |
| F6 MCP | — | `usesCleartextTraffic=false` 不放开 | ⛔ 平台阻断（W5-03）；列不适用 | ⛔ 不适用 |
| F7 KV cache 命中 | M8 | 长会话稳定前缀 ⇒ `cached_tokens>0`；改首字节 ⇒ 归零（双向） | 首条正向 + 反向归零都取到才算 ✅；OR 恒 0 ⇒ vacuous | nv-vl/mistral ✅；OR ⛔vacuous |
| F8 手动压缩 | M9 | S3 后：压缩⇒尾部追加摘要 + `dropped=N` 落盘 + 前缀字节稳定（缓存不失效） | 首条 `applied=true` + 前缀 diff 空 | **待行使**（原预写 ✅ 已收回；设备侧 M9 至今未取到证据，`cache_invalidated` 二值声明另记 W11-03） |
| F9 自动压缩 | M10 | 档位调最小 + 长会话轮次灌到阈值 ⇒ `auto_compact applied=true` | 首条 applied=true；0 命中≠通过 | **待行使**（原预写 ✅ 已收回；「未触发」≠「已验证通过」） |
| F10 长上下文阶梯 | ladder | 4k/32k/128k/512k/1M 各档重放 | 各档 hit + 四元组 | 设备侧 1M ⛔（RoundHeadGate 400k 阻断=W9-11 未解锁）；PC 侧 nv-vl 1M 不稳/ mistral 超时 ⇒ ⚠️ |
| F11 分块装配 | chunk | 分块边界不丢针 | 分块后首字节命中 | nv-vl/dots ✅ |

---

## 四、设备硬件可行性（OPPO A13 PDRM00 / ColorOS / SDK33）

- `adb -P 15037`（默认 5037 无设备）；`MSYS_NO_PATHCONV=1`；daemon 每命令后退出 ⇒ **命令串行、同 Bash 调用连发**；`exec-out` 读二进制。
- `adb push` + `pm install -r`（直接 install 报 -99）。**W11 真机实测结论**：`cmd package resolve-activity --brief com.rickeal.liquidharness.debug` ⇒ `com.rickeal.liquidharness.debug/com.rickeal.liquidharness.MainActivity`，且 `am start -W -n` 冷启 `LaunchState=COLD / TotalTime=1360ms` 成功 ⇒ **W10 handoff §六正确，本计划原写 `.debug.MainActivity` 错误**，冲突已由实测裁决。
- 题面 ASCII（`input text` 非 ASCII NPE）；每题先点「新对话」防 `input text` 追加写改题面。
- uiautomator bounds 不可信 ⇒ 判据以 **journal / logcat / 截图**为准；主模型收不了截图 ⇒ 用 **NV-VL 免费节点**做视觉描述（复用 F1 通道）。
- 免费节点限速/排队（429/5xx）⇒ 设备侧逐端点串行 + 失败重放一次再定性；NV 冷启慢（step5 600s 窗）。
- 单设备串行约束：多模型测试必须逐端点跑，不得并行抢设备。

---

## 五、执行序列

1. **W10 收尾（已闭环，改为核对而非重做）**：`98916eaa` 已推 `main`，CI run `42365515962` 五 job 全 success；G2 `tests=778/0/0/0`、G3 `OK=27 VACUOUS=1 VIOLATION=0`、G4 selftest 30/30 均在 W10 跑完。**W11 不再重复提交/推送 W10 内容**，只做：交接口径回填（本文件 + `handoff-20261107-wave10.md` §九）+ 台账 §二-bis 四行回填 + `EXPECTED_TESTS` 由 W11 新增用例一次性改（778 → **798**，主理人独占；严格 @Test 实跑 798 双向对账）。
2. **设备侧矩阵前置**：装包 + 冷启动 + 注入全部免费端点（endpoints.json）+ 逐端点登记往返（M1）。
3. **W11 矩阵**：F1→F3/F4/F5→F7→F8/F9→F10/F11；F2/F6 阻断复核单列。每个功能取首条正向证据 + 反向（若适用）落 journal/`_w11/raw/`。
4. **台账回填（W11 已落，结论订正）**：W9-01 补完一处仍 `OPEN` / W9-03 形态拆清仍 `OPEN` / W9-04 关闭 / W9-05 **第 7 次顺延如实登记（未开波）**；R2 → `W11-01`、id 假漂移 → `W11-02`（已闭）、`cache_invalidated` → `W11-03`，另开 `W11-04`（jsonMode × tools）/ `W11-05`（分母按族归一）。**W9-05「W11 必须开波」的先还债项未满足**，理由与 W12 硬截止写在台账 §二-bis，本计划不美化。

---

## 六、分波子任务切分（android-developer 文件域，避免 git 冲突）

- **A 组（收尾提交）**：仅 `git` 操作 + `build.yml` 数字；不碰业务代码。
- **B 组（设备矩阵脚本）**：`_w11/` 取证夹（`.py`/`.jsonl`/`.png`），新增目录不触现存模块。
- **C 组（R2 分隔卡，若开工）**：先拆 `ChatMessageList` 子组件（`chatRows` 抽取），再移动分隔卡；余量吃紧的是 **`feature-chat` 3873/3900 余 27**（原文误写 `core-context`，已订正；`core-context` 为 1588/1650 余 62）— **触顶先下沉，禁调 `LINE_CAPS`**。
- D 组（W9-05 journal/FreezeClock）：独立模块，本波末或顺延。

🔴 派单后 `git status` 核实磁盘产出再采信；子 Agent ≤4 路并行；429 ⇒ 主理人接手不重试。

---

## 七、风险与历史坑

1. **429 配额**：本波方案席已实证耗尽；审查席正常。再遇 ⇒ 主理人接手。
2. **OR 免费池 `cached_tokens` 恒 0** ⇒ F7 反向分支 vacuous，不得写通过。
3. **免费模型可用性时变** ⇒ 每阶段先探活再排题；失败样本存 raw。
4. **判据写≠执行**：矩阵每格跑后回填四态，禁预写结论。
5. **S3 压缩高风险面**：保留「未压轮次前缀严格单调」，压缩只追加不改写。
6. **子 Agent 回报虚构** ⇒ `git status` + 严格 @Test 口径核实。
7. **设备侧 1M 阻断**（RoundHeadGate 400k）、**MCP 明文阻断**、**音频 404 阻断**：如实 ⛔，不纠缠。
8. **CRLF**：Windows Write/Edit 可能引入；提交前对改动 .kt 批量 LF 归一。

---

## 八、本波明确不做（防误读为漏项）

- 新增 arch-guard 守卫 / selftest case（W9-10 不开；新守卫扫空集 ⇒ G21 红）。
- 设备侧 1M 真压（双内核阻断，W9-11 未解锁）。
- MCP 真机联调（平台阻塞）。
- 音频输出曲线方案（平台阻断，只复核一次）。
- `feature-settings` 零改动（余 8，W9-07/12 开工前先定拆分）。
