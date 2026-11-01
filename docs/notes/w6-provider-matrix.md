# W6 供应商能力矩阵（多模型实测）

> 口径：每个数字都来自**本轮实际发出的 HTTP 请求**，原始响应落盘可复算。
> 脚本：`_w6/probe.py`（chat/tools/vision）、`_w6/matrix.py`（ladder/cache/chunk）。
> 原始记录：`_w6/probe.json`、`_w6/matrix-*-ladder.json`、`_w6/matrix-*-cache.json`、`_w6/matrix-*-chunk.json`。
> 凭据：只从 `_w5/keys.json`（**不入库**）或环境变量读，本文不含任何密钥。
> 三态：**✅ 实测通过** / **⛔ 实测失败** / **🟡 vacuous**（请求成功但判据不成立，不能当结论用）。

---

## 一、结论先行（四条）

1. **可用的免费模型从 W5 的 5 个扩到 12 个**（NVIDIA 直连 9 + OpenRouter `:free` 2 + 1 个仅聊天）。
   OpenRouter 免费池本轮实际列出 **6 个** `:free`，其中 3 个不可用（2 个地区限制、1 个 429/503）。
2. **NVIDIA nemotron 系列的真实窗口仍是 1M（含输出）**，本轮独立复现：1k→512k 六档全部取针命中；
   995k 档 `prompt_tokens=994 620` + `max_tokens=4096` ⇒ `finish_reason=length`、`content` 空串 ⇒ **🟡 vacuous**（与 W5 同型）。
3. **「300 s 超时」不能判成「模型不可用」**：step-5-preview 与 glm-5.3-flash 在 600 s 窗口下都能回
   （36.9 s / 216.3 s）。**超时是判据参数问题，不是能力结论** —— 与 W5「判据自身要先被验证」同源。
4. **OpenRouter 网关的窗口预检用「字符数 ÷ 4」**，不是真 tokenizer。实测：dots 声明 512k，
   2 869 422 字符（真 tokenizer ≈500k）被网关算成 **721 452** 拒掉；north 声明 256k，
   1 672 654 字符（真 ≈256k）被算成 **422 260** 拒掉。⇒ **散文类内容在 OR 上只能用到声明窗口的 ~2/3**。
5. **前缀缓存不可依赖**：本轮 `nv-vl` 三连发 `0 → 0 → 4336`、`dots` 恒 0、`north` 恒 0。
   W5 曾测得 91.3%（23.7k 前缀），说明**命中率依赖路由与最小前缀长度**，不能作为产品承诺。

---

## 二、模型可用性（chat / tools / vision）

判据：chat = `finish_reason=stop` 且 `content`（或 `reasoning_content`）含 `PONG-4417`；
tools = 返回 `tool_calls` 且工具名 = `read_file`；vision = 200×120 真实图形，断言「蓝色方块 + 红色圆」两项。

| 模型 | 通道 | chat | tools | vision | 备注 |
|---|---|---|---|---|---|
| `nvidia/nemotron-3.5-super-vl-120b-a12b` | NV | ✅ | ✅ | ✅ | 视觉+工具+1M 三合一，本轮主力 |
| `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning` | NV | ✅ | ✅ | ✅ | omni |
| `nvidia/nemotron-3.5-lightning-30b-a3b` | NV | ✅ | ✅ | — | 快 |
| `nvidia/nemotron-3.5-bolt-e4b` | NV | ✅ | ✅ | — | |
| `nvidia/nemotron-3-ultra-550b-a55b` | NV | ✅ | ✅ | — | |
| `mistralai/mistral-large-4-1t-a52b` | NV | ✅ | ✅ | — | |
| `minimaxai/minimax-m3.1-flash` | NV | ✅ | ✅ | — | |
| `meta/llama-3.2-90b-vision-instruct` | NV | ✅ | ⛔ 超时 | ✅ | 工具调用 300 s 未返回 |
| `deepseek-ai/deepseek-v4.1-flash` | NV | ✅ | ⛔ 超时 | — | chat 快、tools 慢（W5 记 420 s 超时，同型） |
| `dots-studio/dots-3-note-preview:free` | OR | ✅ | ✅ | ✅ | 视觉+工具+256k 三合一 |
| `cohere/north-mini-code:free` | OR | ✅ | ✅ | — | 缓存恒 0 |
| `nvidia/nemotron-3-super-120b-a12b` | NV | ⛔ 404 | — | — | 账号未开通 |
| `moonshotai/kimi-k2.6` | NV | ⛔ 404 | — | — | 账号未开通 |
| `stepfun-ai/step-5-preview` | NV | ✅（36.9 s） | — | — | 300 s 窗口超时、600 s 窗口通过 ⇒ **冷启动慢，不是不可用** |
| `z-ai/glm-5.3-flash` | NV | ✅（216.3 s） | — | — | 同上；216 s 首响 ⇒ **不适合交互，只能当批处理** |
| `openai/gpt-oss-20b` | NV | ⛔ 超时 | — | — | 600 s 仍无响应 |
| `google/gemma-4-31b-it` | NV | ⛔ 超时 | — | — | 600 s 仍无响应 |
| `google/gemma-4-31b-it:free` / `gemma-4-26b-a4b-it:free` | OR | ⛔ 400 | — | — | `User location is not supported`（另有一次 429） |
| `recursive/flint-v1.0:free` | OR | ⛔ 503 | — | — | `no upstreams available` |
| `xingchen-agi/xing4.0-29b-a4b:free` | OR | ⛔ 429 | — | — | 上游限流 |

> ⚠️ **首轮探测的判据是 vacuous 的**：`max_tokens=128` 时 3 个 reasoning 模型的 `tool_calls` 与 `content`
> 都是空串（预算全被 reasoning 吃光），被判成 `tools=vacuous`。把预算提到 512 后全部转为 ✅。
> 这与 W5 的「判据自身要先被验证」是同一个坑，记在这里防下一波重犯。

---

## 三、长上下文阶梯（取针，针在 50% 深度）

判据：`MAGENTA-7731` 出现在 `content` **或** `reasoning_content`；`finish_reason=length` 判 **🟡 vacuous**。

### 3.1 `nvidia/nemotron-3.5-super-vl-120b-a12b`（NV 直连，`max_tokens=4096`）

| 目标档 | 实测 `prompt_tokens` | 耗时 | 取针 | 判定 |
|---|---|---|---|---|
| ~1k | 816 | 1.4 s | ✅ | ✅ **判据自证档** |
| 8k | 7 768 | 1.5 s | ✅ | ✅ |
| 32k | 32 016 | 3.9 s | ✅ | ✅ |
| 128k | 127 829 | 9.7 s | ✅ | ✅ |
| 256k | 256 282 | 19.5 s | ✅ | ✅ |
| 512k | 512 626 | 55.0 s | ✅ | ✅ |
| ~995k | 994 620 | 135.7 s | — | 🟡 **vacuous**（`completion=4096` 全被 reasoning 吃光、`finish=length`） |

### 3.2 `dots-studio/dots-3-note-preview:free`（OR）

| 目标档 | 实测 `prompt_tokens` | 耗时 | 取针 | 判定 |
|---|---|---|---|---|
| 1k / 8k / 32k / 128k | 765 / 7 822 / 31 912 / 128 282 | 2.2–4.9 s | ✅ | ✅ |
| 256k | 256 177 | 10.5 s | ✅ | ✅ |
| 500k | — | 2.4 s | — | ⛔ **网关预检拒**（见 §一.3） |

### 3.3 `cohere/north-mini-code:free`（OR）

| 目标档 | 实测 `prompt_tokens` | 耗时 | 取针 | 判定 |
|---|---|---|---|---|
| 1k / 8k / 32k / 128k | 660 / 7 969 / 31 940 / 127 972 | 1.5–6.6 s | ✅ | ✅ |
| 256k | — | 2.3 s | — | ⛔ 网关预检拒（418 164 > 256 000） |

---

## 四、前缀缓存（双向断言）

判据：同前缀第 2/3 次 `cached_tokens > 0`，**且改首字节后必须归零**。

| 模型 | 三连发 `cached_tokens` | 改首字节后 | 判定 |
|---|---|---|---|
| `nv-vl` | 0 → 0 → **4 336** | 0 | 🟡 有缓存但**写入延迟 ≥2 次**，判据不成立 |
| `dots` | 0 → 0 → 0 | 0 | 🟡 未观测到命中（W5 记「第 3 次才命中 5 120」） |
| `north` | 0 → 0 → 0 | 0 | 🟡 恒 0（与 W5 一致） |

**结论**：免费池的缓存命中**不可作为产品承诺**。W5 的 91.3% 是特定前缀 + 特定路由下的一次观测。

---

## 五、分块缓存（3 段递增前缀 8k→16k→32k）

判据（本轮收紧过一次）：`cached_tokens` **单调不减 且 末段严格大于首段**。

| 模型 | 三段 `cached_tokens` | 判定 |
|---|---|---|
| `dots` | 5 120 → 5 120 → 5 120 | 🟡 **partial**：有命中但**不随前缀增长**（命中固定段） |
| `nv-vl` | 4 336 → 0 → 0 | 🟡 前缀一变即归零（未命中累积） |
| `north` | 0 → 0 → 0 | 🟡 |

> ⚠️ **判据自身收紧的记录**：首版判据是「单调不减 + 末段 > 0」，`dots` 的 `5 120→5 120→5 120`
> 会被判成 ✅ —— 那是假阳性（恒定值不证明"随前缀增长"）。已改为要求**末段严格大于首段**，
> 并把 `dots` 降级为 partial。这条改动本身就是本仓「判据先自证」纪律的一次执行。

---

## 六、本轮新增的两条口径（写给下一波）

1. **OpenRouter 的窗口预检 = 字符数 ÷ 4**，与真 tokenizer 无关。散文（≈5.4–6.5 字符/token）在 OR 上
   只能用到声明窗口的 **60%–75%**。要测满声明窗口，只能用高 token 密度的内容（代码/JSON/中文）。
2. **`TokenEstimator` 的 4 字符/token 对散文是保守的、对代码/JSON 是乐观的** —— 偏差是**内容相关**的，
   不是恒定系数。实测散文 5.41–6.53 字符/token（五个模型 × 多档，误差 < 2%）。
   ⇒ 硬预算兜底必须换量纲（字符），并且该量纲要能**按实测标定**，不能用固定 1.8 一刀切。
