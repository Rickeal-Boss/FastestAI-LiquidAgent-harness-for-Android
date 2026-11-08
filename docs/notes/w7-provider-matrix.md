# W7 供应商能力矩阵（多模型长程上下文 + 设备基线实测）

> 口径：每个数字都来自**本轮实际发出的 HTTP 请求**，原始响应全量落 `_w7/raw/`
> （**78 份**：71 份 `req-*.json` 完整响应 + 4 份端点/连通性探测产物 +
> 3 份 step5 总时限 curl 阶梯响应），逐条索引 `_w7/requests.jsonl`
> （**71 行**，含 HTTP 码 / `prompt_tokens` / `completion_tokens` / `cached_tokens` /
> `finish_reason` / 耗时 / 错误串 / raw 文件名），可复算；
> curl 三档的判据另见 `_w7/step5-ladder-curl.json`，全量汇总见 `_w7/summary.json`。
> 脚本：`_w7/w7matrix.py` —— **复用** `_w6/matrix.py`（ladder/cache/chunk + `haystack` 题面 +
> `hit`/`parse` 判据）与 `_w6/probe.py`（chat/tools/vision，200×120 真图 + 双要素断言），
> 未重造判据；W7 只新增模型登记、原始响应落盘观测层与跑批编排。
> 凭据：只从本地 `<凭据文件>`（OpenRouter / NVIDIA API Key，本地路径已隐去）读入内存注入环境变量，
> 本文与 `_w7/` 全部产物不含任何密钥（落盘前 scrub 正则二次脱敏）。
> 四态沿用 W6：**✅ 实测通过** / **⚠️ partial** / **⛔ 失败或不适用** /
> **🟡 vacuous**（请求成功但判据未被行使，既非通过也非失败）。

---

## 一、结论先行

1. **8 个待测 slug：5 个 chat 可用**（`nv-vl` / `dots` / `north` / `mistral` / `step5`），
   **1 个 ⛔ 本环境不可用**（`deepseek-v4.1-flash`：首轮 300 s 超时，**600 s 窗口复验仍
   600.24 s `TimeoutError`、无 HTTP 响应**），**2 个 ⛔ 不适用**
   （`wan2.2-animate` / `nvidia/vsr`，均 404 `page not found`，且不在 NV `/v1/models` 列表）。
2. **1M 档本轮实测通过**：`nv-vl` 995k 档 `prompt_tokens=994 620`、`finish_reason=stop`、
   `content=MAGENTA-7731`、`completion_tokens=187`、110.57 s、HTTP 200 ⇒ ✅。
   **与 W6 同档的 🟡 vacuous 不同**（W6：`completion=4096` 全被 reasoning 吃光、`finish=length`、
   `content` 空）。同一次题面（固定种子 ⇒ `chars=5 378 151`、`ptok=994 620` 完全一致）跨波次
   得到相反观测态 ⇒ **995k 档的「vacuous / 通过」是单次观测态，不是能力上限定论**。
   本波另有 `mistral` 995k 档 ✅（`ptok=994 601`、`finish=stop`、`ctok=104`、238.69 s）
   ⇒ **两个 1M 档通过实例**，但两者都不构成「缓存/稳定性承诺」。
3. **OR 网关「字符数 ÷ 4」预检再次被精确复现**：`dots` 512k 档 `chars=2 938 357`，
   `/4 = 734 589.25`，网关错误串原文写 `734590 of text input`；995k 档 `chars=5 710 019`
   ⇒ 错误串 `1427506 of text input`。**是网关预检拒，不是模型窗口不够**，两档 HTTP 400、
   3.55 s / 4.28 s 秒拒。
4. **长程阶梯全程零缓存命中**：15 个成功档位的 `cached_tokens` 全为 `0`。A 阶段跑批在
   A4 处因平台 429 中断，缓存/分块一度未行使；**配额恢复后已补跑完毕**：
   **没有一个模型通过完整的双向断言** —— `nv-vl` `0→0→4336→0`（写入延迟 ≥2 次，
   与 W6 完全一致）、`dots` `0→5120→0→0`（脚本判 ok，但同前缀第 3 次回落 0 ⇒ 命中不稳定）、
   `north` 恒 0；分块缓存 `nv-vl` `4336→0→0`、`dots` `5120→0→5120` ⚠️ partial、
   `north` 恒 0 ⇒ **免费池缓存命中不可作为产品承诺**（§五/§六）。
5. **音频输出 ⛔ 不适用 / 不可执行**（见 §七），设备侧仅 iflytek TTS ⇒ W3-01 裁决维持。
6. **设备基线与 W6 交接 §七 有两处冲突**（活跃端点、`import-1m.jsonl`），见 §八/§九；
   **本波已把设备前提补齐**（§八补充）：`nv1` 密钥录入 + 设为当前 + 连接测试通过、
   1M 题面推入、32K 档位保持 ⇒ **W7-01 / W5-04 可直接接手真机重放**。
7. **「超时」本波三次都是判据参数问题**：ds-v41 复验两次超时（300 s / 600 s）才定性
   本环境不可用；step5 的 tools 502 与 900 s 挂起分别被 600 s / 总时限复验翻案为 ✅。
   只有 `wan2.2-animate` / `nvidia/vsr` 的 404 与「不在 `/v1/models`」是确定的能力不适用。

---

## 二、判据自证（先自证再上探）

W6 首轮用 `max_tokens=128` 被 reasoning 吃光 ⇒ `tool_calls`/`content` 全空 ⇒ 整轮 vacuous。
本波显式把通道请求 `max_tokens` 提到 **512**（`_w7/w7matrix.py:pb_guarded`），阶梯用 4096。

| 自证项 | 实测 | 判定 |
|---|---|---|
| tiny chat（NV `nv-vl`） | HTTP 200，`finish=stop`，`content=PONG-4417`，1.70 s | ✅ |
| tiny chat（OR `dots`） | HTTP 200，`finish=stop`，`content=PONG-4417`，4.73 s | ✅ |
| 1k 取针 `nv-vl` | HTTP 200，`ptok=816`/`ctok=90`，`finish=stop`，hit=True，2.76 s | ✅ |
| 1k 取针 `dots` | HTTP 200，`ptok=765`/`ctok=111`，`finish=stop`，hit=True，1.96 s | ✅ |
| 1k 取针 `north` | HTTP 200，`ptok=660`/`ctok=98`，`finish=stop`，hit=True，1.54 s | ✅ |

`selfcheck.json: all_ok=true` ⇒ 判据有效，才上探长档。
⚠️ 附带事实：NV 响应回显 `model=private/nvidia/nemotron-3.5-super-120b-a12b`，
与请求 slug `nvidia/nemotron-3.5-super-vl-120b-a12b` 不同（网关路由到私有部署），如实登记。

**视觉判据**：禁用 1×1 像素样本（W5 实摔：mistral 在 1×1 上答 Black），本波沿用 W6 的
200×120 真图（左蓝色方块 / 右红色圆）+ **双要素断言**（形状 × 颜色同时命中）。

---

## 三、模型可用性（chat / tools / vision）

判据：chat = `finish_reason=stop` 且 `content` 含 `PONG-4417`；tools = 返回 `tool_calls`
且工具名 `read_file`；vision = 真图双要素。原始响应见 `_w7/probe.json` 与
`_w7/requests.jsonl` 第 4–18 行。

| 模型 | 通道 | chat | tools | vision | HTTP / 耗时 / 错误串 |
|---|---|---|---|---|---|
| `nvidia/nemotron-3.5-super-vl-120b-a12b` | NV | ✅ | ✅ | ✅ `square blue; circle red` | 200 / 1.11 s · 200 / 1.30 s · 200 / 12.66 s |
| `dots-studio/dots-3-note-preview:free` | OR | ✅ | ✅ | ✅ `rectangle blue; circle red` | 200 / 4.41 s · 200 / 2.69 s · 200 / 8.98 s |
| `cohere/north-mini-code:free` | OR | ✅ | ✅ | —（本波未测，W6 记 —） | 200 / 2.48 s · 200 / 4.26 s |
| `mistralai/mistral-large-4-1t-a52b` | NV | ✅ | ✅ | —（未测） | 200 / 1.92 s · 200 / **42.21 s**（tools 冷启动偏慢） |
| `stepfun-ai/step-5-preview` | NV | ✅ | ✅（复验） | —（未测） | chat 200 / 1.43 s（600 s 复验 1.73 s `PONG-4417`）；首轮 tools **502** `Bad Gateway`（0.48 s），**600 s 复验 200 / 7.65 s 且 `tool_calls` 工具名正确** ⇒ 502 是瞬时上游故障，不是能力缺失 |
| `deepseek-ai/deepseek-v4.1-flash` | NV | ⛔ 本环境不可用 | — | — | 首轮 **300.208 s** `TimeoutError`；**600 s 窗口复验 600.24 s 仍 `TimeoutError`、`http=null`** ⇒ 两次复验后定性 |
| `wan-ai/wan2.2-animate-2-14b` | NV | ⛔ **不适用** | — | — | **404** `404 page not found`（0.36 s） |
| `nvidia/vsr` | NV | ⛔ **不适用** | — | — | **404** `404 page not found`（0.34 s） |

> `wan2.2-animate` / `nvidia/vsr` 是图像/视频模型，不在 NV `/v1/models` 的 85 个 chat 模型
> 列表里（该列表只命中 `nvidia/nemotron-3.5-super-vl-120b-a12b`），**不硬套 chat 判据**，
> 只保留 404 原文作为「不适用」证据。
> `deepseek-v4.1-flash` 已按口径用 **600 s 窗口复验**：`_w7/slowchat.json` 记
> `secs=600.24`、`http=null`、`TimeoutError: The read operation timed out` ⇒ 两次（300 s / 600 s）
> 均无响应，**定性为「本环境 / 本轮不可用」**。不是脚本 bug：同一 `slowchat` 函数对 step5
> 在 1.73 s 返回 200、`content=PONG-4417`。这与 W6「300 s 超时 ≠ 模型不可用」不矛盾 ——
> 那条约束的是**不能只凭 300 s 定性**，本波已补足 600 s 窗口这一必要动作，结论才成立。
> `step5` 的 tools 由首轮 502（0.48 s，`Upstream request failed`）复验为 ✅（7.65 s、
> `tool_calls` 工具名 `read_file` 正确）⇒ **单次 502 同样不能定性为能力缺失**。

---

## 四、长程阶梯（取针，针在 50% 深度）

判据：`MAGENTA-7731` 出现在 `content` **或** `reasoning_content`；
`finish_reason=length` 且 `content` 空 ⇒ **🟡 vacuous**。题面由
`_w6/matrix.py:haystack(SEED=20260918, target, chars)` 复算。

### 4.1 `nv-vl`（NV 直连，`max_tokens=4096`）—— 7 档全跑完

| 目标档 | `chars` | `prompt_tokens` | `completion_tokens` | `cached_tokens` | `finish_reason` | 耗时 | HTTP | 取针 | 判定 |
|---|---|---|---|---|---|---|---|---|---|
| 1k | 4 262 | 816 | 91 | 0 | stop | 1.01 s | 200 | ✅ | ✅ 自证档 |
| 8k | 41 973 | 7 768 | 90 | 0 | stop | 2.38 s | 200 | ✅ | ✅ |
| 32k | 173 119 | 32 016 | 87 | 0 | stop | 2.80 s | 200 | ✅ | ✅ |
| 128k | 692 349 | 127 829 | 117 | 0 | stop | 11.07 s | 200 | ✅ | ✅ |
| 256k | 1 386 768 | 256 282 | 152 | 0 | stop | 20.82 s | 200 | ✅ | ✅ |
| 512k | 2 770 716 | 512 626 | 139 | 0 | stop | 43.79 s | 200 | ✅ | ✅ |
| **~995k** | **5 378 151** | **994 620** | **187** | **0** | **stop** | **110.57 s** | **200** | ✅ | **✅** |

`chars_per_token` 实测 5.223 → 5.407（散文），与 W6 的 5.41–6.53 同量级。

**1M 档结论**：本轮 ✅ 通过，`prompt_tokens=994 620` + `max_tokens=4096` 未被网关拒，
`finish=stop` 且答案命中。但 W6 同档是 🟡 vacuous（同 `ptok`、同题面、同 `max_tokens`），
差别只在输出侧 reasoning 消耗（W6 吃满 4096，W7 只用 187）⇒ **该档位结论依赖单次观测，
不可作为产品承诺**；这与 W6「300 s 超时 ≠ 不可用」是同一类判据纪律。

### 4.2 `dots`（OR，声明 512k）

| 目标档 | `chars` | `prompt_tokens` | `ctok` | `cached` | `finish` | 耗时 | HTTP | 判定 |
|---|---|---|---|---|---|---|---|---|
| 1k | 4 262 | 765 | 450 | 0 | stop | 1.96 s | 200 | ✅ |
| 8k | 44 831 | 7 822 | 114 | 0 | stop | 1.87 s | 200 | ✅ |
| 32k | 183 578 | 31 912 | 89 | 0 | stop | 4.33 s | 200 | ✅ |
| 128k | 736 547 | 128 282 | 115 | 0 | stop | 6.41 s | 200 | ✅ |
| 256k | 1 470 061 | 256 177 | 103 | 0 | stop | 11.24 s | 200 | ✅ |
| 512k | 2 938 357 | — | — | — | — | 3.55 s | **400** | ⛔ **网关预检拒** |
| ~995k | 5 710 019 | — | — | — | — | 4.28 s | **400** | ⛔ **网关预检拒** |

400 错误串原文（512k 档）：
`This endpoint's maximum context length is 512000 tokens. However, you requested about
738686 tokens (734590 of text input, 4096 in the output).`

算术核对：`2 938 357 ÷ 4 = 734 589.25` ⇒ 网关报 `734590`，**精确吻合字符数 ÷ 4**。
995k 档报 `1427506 of text input`（`5 710 019 ÷ 4 = 1 427 504.75`）。
⇒ 散文在 OR 上只能用到声明窗口的 ~2/3（W6 结论复现）；**被拒原因是网关预检按字符数估算，
不是模型真实窗口不够**。

### 4.3 `north`（OR，声明 256k）—— 429 中断后已补跑完 7 档

| 目标档 | `chars` | `prompt_tokens` | `ctok` | `cached` | `finish` | 耗时 | HTTP | 判定 |
|---|---|---|---|---|---|---|---|---|
| 1k | 4 262 | 660 | 115 | 0 | stop | 1.46 s | 200 | ✅ |
| 8k | 51 877 | 7 969 | 130 | 0 | stop | 2.16 s | 200 | ✅ |
| 32k | 208 574 | 31 940 | 167 | 0 | stop | 4.07 s | 200 | ✅ |
| 128k | 836 028 | 127 972 | 4 097 | 0 | **length** | 8.29 s | 200 | **🟡 vacuous**（`content=""`、hit=False，输出预算被吃光） |
| 256k | 1 672 654 | — | — | — | — | 2.67 s | **400** | ⛔ **网关预检拒** |
| 512k | 3 345 121 | — | — | — | — | 2.79 s | **400** | ⛔ **网关预检拒** |
| ~995k | 6 500 418 | — | — | — | — | 4.19 s | **400** | ⛔ **网关预检拒** |

400 错误串原文（256k 档）：
`This endpoint's maximum context length is 256000 tokens. However, you requested about
422260 tokens (418164 of text input, 4096 in the output).`

算术核对：`1 672 654 ÷ 4 = 418 163.5` ⇒ 网关报 `418164`；512k 档 `3 345 121 ÷ 4 = 836 280.25`
⇒ 报 `836281`；995k 档 `6 500 418 ÷ 4 = 1 625 104.5` ⇒ 报 `1625105`。
**三档全部精确吻合「字符数 ÷ 4」**，与 dots §4.2 同源 ⇒ 再次证明是预检拒而非窗口不足。

> ⚠️ 128k 档是 W7 新增的 vacuous 实例：`finish_reason=length` + `content=""` + hit=False。
> 按口径**既非通过也非失败**，不得记成「取针失败」。

### 4.4 `mistral`（NV，声明 1M）—— 已补跑阶梯

| 目标档 | `chars` | `prompt_tokens` | `ctok` | `cached` | `finish` | 耗时 | HTTP | 取针 |
|---|---|---|---|---|---|---|---|---|
| 1k | 4 262 | 846 | 67 | 0 | stop | 1.53 s | 200 | ✅ |
| 8k | 40 508 | 7 814 | 63 | 0 | stop | 2.15 s | 200 | ✅ |
| 32k | 166 065 | 31 992 | 70 | 0 | stop | 35.59 s | 200 | ✅ |
| 128k | 664 694 | 128 006 | 61 | 0 | stop | 44.96 s | 200 | ✅ |
| 256k | 1 329 501 | 256 082 | 62 | 0 | stop | 37.11 s | 200 | ✅ |
| 512k | 2 658 320 | 512 679 | 62 | 0 | stop | 73.23 s | 200 | ✅ |
| **~995k** | **5 159 459** | **994 601** | **104** | **0** | **stop** | **238.69 s** | **200** | ✅ |

七档全部 `finish=stop`、`content=MAGENTA-7731`、`cached=0` ⇒ **`mistral` 的 1M 窗口
本波独立实测通过**（与 `nv-vl` 并列第二个 1M 实例；995k 档耗时 238.69 s，比 nv-vl 的
110.57 s 慢一倍，仍远低于 600 s 窗口）。
`chars_per_token` 5.038 → 5.193（散文偏代码侧比 nv-vl 的 5.41 更"密"）。
⚠️ 耗时不单调（32k 35.59 s、128k 44.96 s、256k 37.11 s），是冷启动 / 路由抖动，
不是随长度单调增长 ⇒ **不要把耗时当窗口能力的判据**。

### 4.5 `step5`（NV）—— 用总时限 curl 复验阶梯，三档全 ✅

跑批 B 的 B8 在 `mx.post` 下卡了 **900.2 s** 才以 `TimeoutError` 失败
（`_w7/matrix-step5-ladder.json` 仅 1 条 `status=fail`、`http=null`）。
改用 **`curl --max-time 90`（总时限）** 重发同样的 `haystack` 题面，三档全部通过：

| 目标档 | `chars` | `prompt_tokens` | `ctok` | `cached` | `finish` | 耗时 | HTTP | 取针 |
|---|---|---|---|---|---|---|---|---|
| 1k | 4 474 | 772 | 46 | 0 | stop | **12.58 s** | 200 | ✅ |
| 8k | 40 183 | 6 710 | 45 | 0 | stop | 2.13 s | 200 | ✅ |
| 32k | 166 187 | 27 620 | 52 | 0 | stop | 54.50 s | 200 | ✅ |

三档 `content=MAGENTA-7731`、`cached=0`。证据：`_w7/step5-ladder-curl.json` +
`_w7/raw/step5-ladder-{1k,8k,32k}.json`（题面由 `_w6/matrix.py:haystack` 复算）。

> 🔴 **判据纪律（本波新增）**：`urllib.request.urlopen(timeout=N)` 的 `timeout` 是
> **每次 read 的超时，不是请求总时限**；服务端偶发长挂起时，单个请求可以拖到 900 s
> 才失败。同一模型、同一题面换 `curl --max-time` 后 12.58 s 就 200 ⇒
> **「900 s 超时」依然是客户端 timeout 语义 + 单次抖动，不能定性为模型不可用**。
> 这是 W6「300 s 超时 ≠ 不可用」在 W7 的第三次同型复现（另两次见 §三 ds-v41 / step5）。

### 4.6 `ds-v41` 的阶梯状态

**两次超时（300 s / 600 s）后未再上探长档**，本波不给出该模型的窗口数字，
如实登记「未执行」（不是通过、也不是取针失败）。其 chat 判定见 §三。

---

## 五、前缀缓存（双向断言）—— 已补跑

判据：同前缀连发 3 次记 `cached_tokens`，第 2/3 次须 `>0`；**再把首字节改掉确认归零**。
W6 基线：`nv-vl` `0→0→4336`、`dots` 恒 0、`north` 恒 0。
本波题面固定 8k 前缀（`haystack(8000×4)`），`max_tokens=64`，每次间隔 1 s。
原始数据：`_w7/matrix-{nv-vl,dots,north}-cache.json`。

| 模型 | 阶段 | `prompt_tokens` | `cached_tokens` | 耗时 | HTTP |
|---|---|---|---|---|---|
| `nv-vl` | same-1 / same-2 / same-3 | 5 968 / 5 968 / 5 968 | **0 / 0 / 4 336** | 1.87 / 6.96 / 10.05 s | 200 |
| `nv-vl` | 改首字节 | 5 968 | **0** | 1.51 s | 200 |
| `dots` | same-1 / same-2 / same-3 | 5 604 / 5 604 / 5 604 | **0 / 5 120 / 0** | 1.96 / 2.31 / 2.93 s | 200 |
| `dots` | 改首字节 | 5 606 | **0** | 1.84 s | 200 |
| `north` | same-1 / same-2 / same-3 | 4 937 ×3 | **0 / 0 / 0** | 1.45 / 1.82 / 1.59 s | 200 |
| `north` | 改首字节 | 4 938 | **0** | 1.51 s | 200 |

| 模型 | 脚本 verdict | 本波定性 |
|---|---|---|
| `nv-vl` | vacuous | 🟡 **vacuous**：反向断言（改首字节归零）成立，但正向「第 2 次即命中」不成立 —— 缓存写入延迟 ≥2 次，**与 W6 的 `0→0→4336` 完全一致** |
| `dots` | ok | ⚠️ **不写成通过**：脚本判据只看第 2 次命中 + 改首字节归零，但**同一前缀第 3 次又回落到 0** ⇒ 命中不稳定，不构成可依赖的缓存 |
| `north` | vacuous | 🟡 **vacuous**：恒 0（与 W6 一致） |

**结论**：三模型无一同时满足「稳定命中 + 改首字节归零」的完整双向断言；
W5 曾测得 91.3% 命中率是特定前缀 + 特定路由下的一次观测，**免费池缓存不可作为产品承诺**。

## 六、分块缓存（8k→16k→32k 递增前缀）—— 已补跑

判据（W6 收紧过一次）：`cached_tokens` **单调不减 且 末段严格大于首段**；
恒定值 `5120→5120→5120` 是 ⚠️ partial，不是通过。
原始数据：`_w7/matrix-{nv-vl,dots,north}-chunk.json`（`max_tokens=64`，段间 1 s）。

| 模型 | 第 1 段 `ptok` / `cached` / 耗时 | 第 2 段 | 第 3 段 | verdict |
|---|---|---|---|---|
| `nv-vl` | 5 968 / **4 336** / 1.01 s | 11 920 / **0** / 2.82 s | 23 830 / **0** / 2.66 s | 🟡 **vacuous**：前缀一变即归零，未命中累积 |
| `dots` | 5 604 / **5 120** / 2.12 s | 11 195 / **0** / 2.17 s | 22 350 / **5 120** / 3.66 s | ⚠️ **partial**：`5120→0→5120` 不单调，且末段 = 首段（命中固定段，不随前缀增长） |
| `north` | 4 937 / **0** / 1.53 s | 9 874 / **0** / 4.48 s | 19 715 / **0** / 3.91 s | 🟡 **vacuous**：恒 0，末段不严格大于首段 |

三段 `prompt_tokens` 均近似翻倍（如 nv-vl `5 968→11 920→23 830`），证明**递增前缀确实被行使**，
不是空跑；但三个模型都没有出现 `cached_tokens` 随前缀增长而累积的形态 ⇒
**分块缓存判据本波无一通过**，与 W6 结论一致。

---

## 七、音频输出能力

| 探测 | 实测 | 判定 |
|---|---|---|
| NV `GET /v1/models` | HTTP 200，85 个模型，`audio/tts/speech/voice/asr/whisper/fastpitch/magpie` 关键词命中 **0 个** | ⛔ 无 audio/TTS 类模型 |
| 两个非 chat slug 是否在列表 | `wan-ai/wan2.2-animate-2-14b`、`nvidia/vsr` **均不在**；只有 `nvidia/nemotron-3.5-super-vl-120b-a12b` 在 | ⛔ 非 chat 兼容 |
| `POST /v1/audio/speech`（最小 body） | HTTP **404** `404 page not found` | ⛔ 端点不存在 |
| 设备 `pm list packages` TTS 引擎 | 仅 `com.iflytek.speechsuite`（另有 oplus 语音唤醒/助手，非 TTS 引擎） | — |
| `settings get secure tts_default_synth` | `com.iflytek.speechsuite` | — |

**裁决：⛔ 音频输出本波不可执行 / 不适用**。NV 这两个 slug 不提供 audio/TTS 端点，
平台无 `/v1/audio/speech`；设备侧仅 iflytek TTS ⇒ **W3-01「speak 裁决关闭、本机仅 iflytek」
仍成立**，本仓不做云端 TTS。证据：`_w7/audio-capability.json`、
`_w7/raw/nv-models.json`、`_w7/raw/audio-speech-probe.json`。

---

## 八、设备基线（adb 只读实测）

采集方式：`adb -P 15037`（默认 5037 被占用）；二进制一律 `exec-out` 读回；
含设备绝对路径的命令加 `MSYS_NO_PATHCONV=1`（否则 Git Bash 会把 `/sdcard/...` 误转成
`<本机目录>/.../PortableGit/...`，第一轮就踩过，已重测）。本波**不装包、不唤醒**。
证据：`_w7/device-baseline.json`。

| 项 | 实测 | 判定 |
|---|---|---|
| 设备在线 | `<设备序列号> device`，`PDRM00`，Android 13 | ✅ |
| 已装 debug 包 | `package:com.rickeal.liquidharness.debug` | ✅ |
| 活跃端点 | `settings.preferences_pb` → `active_endpoint_id = orvl` ⇒ **OR-DOTS**（`https://openrouter.ai/api/v1`，`dots-studio/dots-3-note-preview:free`，512 000） | ⚠️ **与交接不符** |
| 端点配置 | `endpoints.json`：`nv1`/NV-VL（1 048 576，`hasSecret=**False**`）、`orvl`/OR-DOTS（512 000，`hasSecret=True`）；`secrets/` 只有 `orvl.bin`(101 B)，**无 `nv1.bin`** | ⚠️ **与交接不符** |
| 窗口档位 | `settings.preferences_pb` 的 `max_context_tokens` varint `80 fa 01` = **32 000** | ✅ 与交接一致（32K） |
| 沙箱夹具 | `files/harness/workspace/module_1..5.kt` = 16 289 / 16 261 / 16 303 / 16 276 / 16 294 B（≈16.3 KB）✅；`chunk1..5.txt` 各 18 392 B（**实测 5 个**，交接只写 1..3）；另有 a/b/notes/w5.txt | ✅ |
| 下载目录导入题面 | 实测 `import-400k.jsonl`(2 160 583 B)、`import-40turn.jsonl`(29 189 B)；**`import-1m.jsonl` 不在设备**（本地 `_w6/` 有 5 221 133 B 副本） | ⚠️ 缺 1 个 |
| TTS | 见 §七 | ✅ 仅 iflytek |

> **§八 补充 · 设备前提准备后的状态变更**（本波第 3 步，证据 `_w7/device-prep.json`）：
> 上表是**本波初始基线**（采集时 `active_endpoint_id=orvl`）。随后按主理人派单，经应用 UI
> 端点编辑页给 `nv1` 录入 NVIDIA 密钥并设为当前，状态已变为：
> - `nv1 hasSecret=True`；`files/harness/secrets/nv1.bin` 存在（**98 B**，应用 Keystore
>   派生 + AES-GCM 信封写入，**未伪造**；`orvl.bin` 101 B）。
> - `settings.preferences_pb` 58 B → **57 B**，`active_endpoint_id` 由 `orvl` → **`nv1`**
>   （NV-VL，`https://integrate.api.nvidia.com/v1`，`nvidia/nemotron-3.5-super-vl-120b-a12b`，1 048 576）。
> - 连接测试（设备侧真实 tiny chat 往返）：回复 **`PONG-4417`**、有「思考过程」、状态条
>   **`1.9k in / 36 out (0 cached, 0.0%)`**、分母 **`27.9K`**（= 32 000 − 4 096）
>   ⇒ 密钥注入完整（缺字符会 401）、NV 端点真可用、32K 档位生效。
>   ⚠️ **journal 口径如实登记**：设备侧 `run-as find .` 未发现任何 journal/run 文件，
>   logcat 中应用只打渲染日志、无 HTTP 业务日志 ⇒ 本次连接判据是**应用状态条的 usage 数字
>   ＋回复内容＋顶栏端点**（应用内部解析值，非 uiautomator bounds 推断），
>   **未拿到 journal 文件证据**，不夸大为「journal 已验证」。
> - `/sdcard/Download/import-1m.jsonl` 已推入，本地/设备**均 5 221 133 B**
>   （`run-as` 读公共目录 Permission denied，按知识库预期，改用 shell `ls -l` 等价核实）。
> - `max_context_tokens` 仍 **32 000**（32K 未变）；`module_1..5.kt` / `chunk1..5.txt` 仍在沙箱。
> - 键盘弹起后发送键实测 **(942, 1356)**，与 W6 交接写的 (942, 1215) 不同 ⇒ 每步必须重新 dump。
> - 截图纪律：只在录入密钥**之前**截过一张无密钥的 `device-wake.png`；注入与编辑期间未截图，
>   注入后的 UI dump 只在管道内解析且 scrub、原始 XML 未落盘。

---

## 九、与 W6 交接文档的冲突登记（证伪优先）

`docs/handoff-20261101-wave6.md` §七 写「端点：**NV-VL 已录入密钥并设为『当前』**」，
**本波实测双重不成立**：

1. `active_endpoint_id` 实际是 `orvl`（OR-DOTS），不是 `nv1`；
2. `nv1` 的 `hasSecret=False`，`secrets/` 目录里没有 `nv1.bin` ⇒ NV-VL 端点配置存在但**未录入密钥**。

⇒ **直接影响 W7-01**（32K 档真机重放要求「换 NVIDIA 端点」）：照交接直接跑，请求仍会走
OR-DOTS；必须先经应用 UI 端点编辑页录入密钥并切换当前端点（不得伪造 Keystore AES-GCM
信封）。此项已回传主理人，由方案席订正判据；**本席已完成设备前提准备**（§八补充）：
`nv1` 密钥已录入、已设为当前、设备侧连接测试通过、1M 题面已推入、32K 档位保持
⇒ **W7-01 可直接接手真机重放**。交接文档本身的订正由主理人在 W7 handoff 完成，本席不改。

另两处小偏差：`chunk` 实测 5 个（交接写 3 个）；`import-1m.jsonl` 设备侧缺失（交接写
「下载目录已有导入题面」时只点名 400k 与 40turn，未点 1m，按实测补登记）。

---

## 十、口径与纪律（写给下一波）

1. **OR 的窗口预检 = 字符数 ÷ 4**，与真 tokenizer 无关。本波用 400 错误串里的
   `734590 of text input` 与 `chars=2 938 357` 精确对上（`÷4 = 734 589.25`），
   比 W6 的推断更硬。散文（5.4–6.5 字符/token）在 OR 上只能用声明窗口 ~2/3。
2. **「995k 档 vacuous」不是能力上限**：同一 `ptok=994 620` 的题面，W6 因 reasoning
   吃满 4096 输出预算而 vacuous，W7 `ctok=187` 就 `finish=stop` 命中。⇒ 长档判据必须
   同时记 `finish_reason` 与 `completion_tokens`，只记「有没有答案」会误判。
3. **阶梯里 `cached_tokens` 恒 0 不能替代缓存三连发判据**：命中依赖路由与最小前缀长度，
   且必须做「改首字节归零」的反向断言。
4. 判据先自证：`max_tokens≥512` + 1k 取针档 hit=True 才允许上探（本波已执行）。
5. 所有结论以 journal / 原始响应为准，不信任 UI 表象与交接文档自述（§九 即一例）。
6. 🔴 **`urllib.urlopen(timeout=N)` 是「每次 read 超时」，不是请求总时限**。本波 step5
   1k 档在它下面拖到 900.2 s 才失败，换 `curl --max-time 90` 同一题面 12.58 s 就 200。
   ⇒ 长请求必须显式用**总时限**复验；「超时」先查判据参数的量纲，再谈模型能力。
7. **W6 收紧过的分块缓存判据本波再次生效**：`dots` 的 `5120→0→5120` 若按旧判据
   （单调不减 + 末段 >0）会被误判 ✅，按「末段严格大于首段」才正确降级 ⚠️ partial。

---

## 十一、本波未完成项与原因（如实登记，不许写成通过）

| # | 项 | 状态 | 结果 / 原因 |
|---|---|---|---|
| 1 | 前缀缓存三连发 + 改首字节归零 | ✅ 已补跑 | §五：nv-vl `0→0→4336→0` vacuous、dots `0→5120→0→0` ⚠️ 不稳定、north 恒 0 vacuous |
| 2 | 分块缓存 8k→16k→32k | ✅ 已补跑 | §六：nv-vl `4336→0→0` vacuous、dots `5120→0→5120` partial、north `0→0→0` vacuous，无一通过 |
| 3 | `north` 128k/256k/512k/995k 阶梯 | ✅ 已补跑 | §4.3：128k 🟡 vacuous（`finish=length`、`content=""`）；256k/512k/995k 网关预检拒（400） |
| 4 | `mistral` / `step5` 长程阶梯 | ✅ 已完成 | §4.4 mistral 七档（含 995k ✅）；§4.5 step5 1k/8k/32k 三档 ✅（总时限 curl 复验） |
| 5 | `deepseek-v4.1-flash` 600 s 窗口复验 | ✅ 已完成 | `slowchat.json`：600.24 s 仍 `TimeoutError`、`http=null` ⇒ 定性本环境不可用 |
| 6 | `step5` tools 502 复验 | ✅ 已完成 | `slowtools.json`：600 s 窗口 200 / 7.65 s、`tool_calls_ok=True` ⇒ 502 是瞬时上游故障 |
| 7 | nv1 端点密钥录入 + 切换当前 + 连接测试 | ✅ 已完成 | 见 §八补充 与 `_w7/device-prep.json` |
| 8 | `import-1m.jsonl` 推入设备并读回核字节 | ✅ 已完成 | 本地/设备均 5 221 133 B |
| 9 | 音频输出能力 | ✅ 已裁决为不可执行 | 端点/设备两侧均无可执行路径（§七），**「不可执行」本身是结论** |
| 10 | `ds-v41` 长程阶梯 | ⛔ 未执行 | 两次 chat 超时（300 s / 600 s）后未上探长档，如实登记，不写成通过 |

> 1–9 项均已完成并回填本文；仅第 10 项按口径如实登记为「未执行」。
> B8 在 `mx.post` 下 900.2 s 超时的那次失败也保留在 `_w7/matrix-step5-ladder.json`
> 与 `requests.jsonl` 里，作为 §4.5 的对照证据，不删除、不美化。
