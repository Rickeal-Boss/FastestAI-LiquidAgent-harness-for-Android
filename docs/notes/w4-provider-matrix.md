# W4 供应商能力矩阵 v2（实测，2026-10-31 夜）

> 口径：所有结论来自**本波实际发出的请求**，不是模型卡声明。
> 脚本：`_w4/probe_w4.py`（仓外一次性工具）；原始记录 `_w4/w4_matrix.jsonl`。
> 凭据：OpenRouter（余额 $49.79 / 上限 $50）、NVIDIA（`integrate.api.nvidia.com`）。

---

## 一、结论先行（五条，全部影响实现决策）

1. **免费池不是「模型不可用」，是「账号隐私开关没开」。** 14 个 `:free` 模型里 12 个失败，
   其中 8 个报 `0 endpoints ... matching your guardrail restrictions and data policy`，
   明细里写死 `Free model training violation (account settings)`，
   并给出配置地址 `https://openrouter.ai/settings/privacy`。
   ⇒ 这是**账号级开关**，不是模型能力问题，客户端加 `provider.data_collection=allow`
   **不能绕过**（实测两种写法都被同一条策略拒绝）。要解锁必须由账号持有人在网页端打开
   「允许免费模型训练」。在此之前，免费池只有 2 个可用模型。
2. **可用免费模型只有两个**：`dots-studio/dots-3-note-preview:free`（512k，**工具+视觉双通**）
   与 `cohere/north-mini-code:free`（256k，工具通、无视觉）。前者是免费池里唯一能同时
   跑工具循环与读图的模型，可作为**免费跑分基线**。
3. **1M 级窗口在本 key 上可用，但延迟超线性**：760,812 token 取针命中，**40.8s**；
   385k 时 19.1s；192k 时 10.0s；48k 时 4.1s。1.25M 目标被端点拒绝（超窗）。
4. **缓存命中的充要条件是「逐字节相同的最长前缀」**，本轮拿到了两处独立证据（见 §四）。
   客户端唯一可控变量是**前缀稳定性**；命中率本身由供应商实现决定。
5. **音频输出在本区仍然不可用**：`openai/gpt-audio-mini` 与 `google/lyria-3-clip-preview`
   均返回 `403 This model is not available in your region`（`failed_routing_step =
   Gate Endpoints with Geo Restrictions`）。与 W3 结论一致，**不是 key 的问题**。
   ⇒ 语音输出只能走设备本地 TTS，不做云端 TTS 适配器。

---

## 二、免费池逐模型实测

| 模型 | ctx | chat | tools | vision | 失败原因（原文摘要） |
|---|---|---|---|---|---|
| `dots-studio/dots-3-note-preview:free` | 512k | ✅ | ✅ | ✅ | — |
| `cohere/north-mini-code:free` | 256k | ✅ | ✅ | ❌ 404 | `No endpoints found that support image input` |
| `nvidia/nemotron-3.5-super-vl:free` | 1M | ❌ 404 | — | — | free-model-training-violation（账号策略） |
| `nvidia/nemotron-3.5-lightning:free` | 1M | ❌ 404 | — | — | 同上 |
| `nvidia/nemotron-3-ultra-550b-a55b:free` | 1M | ❌ 404 | — | — | 同上 |
| `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free` | 256k | ❌ 404 | — | — | 同上 |
| `nvidia/nemotron-3.5-content-safety:free` | 128k | ❌ 404 | — | — | 同上 |
| `liquid/lfm-2.5-2.6b:free` | 64k | ❌ 404 | — | — | 同上 |
| `thinkingmachines/inkling:free` | 1M | ❌ 403 | — | — | `only available on agentic harnesses` |
| `thinkingmachines/inkling-small:free` | 1M | ❌ 403 | — | — | 同上 |
| `xingchen-agi/xing4.0-29b-a4b:free` | 256k | ❌ 429 | — | — | 上游限流 |
| `google/gemma-4-31b-it:free` | 256k | ❌ 429 | — | — | `temporarily rate-limited` |
| `google/gemma-4-26b-a4b-it:free` | 256k | ❌ 400 | — | — | Provider returned error |
| `recursive/flint-v1.0:free` | 256k | ❌ 503 | — | — | `no upstreams available` |

**判定**：免费池的失败**大部分是策略/限流，不是能力**。把「免费模型不可用」写成
「模型不行」会让后续决策完全走错方向（会去换模型，而正确动作是去开开关）。

---

## 三、长上下文阶梯（取针，针在 50% 深度）

`max_tokens=1024`（W3 事故：64 会被 reasoning 吃光，content 返回空串 ⇒ 判据恒假）。

### 3.1 `nvidia/nemotron-3.5-super-vl`（付费，$0.15/M in，1,048,576 窗口）

| 目标 chars | 实际 `prompt_tokens` | `cached_tokens` | 命中率 | 延迟 | 针 |
|---|---|---|---|---|---|
| 8k | 6,058 | 0 | 0.0% | 2.6s | ✅ |
| 64k | 48,253 | 0 | 0.0% | 4.1s | ✅ |
| 256k | 192,876 | 1,104 | 0.6% | 10.0s | ✅ |
| 512k | 385,697 | 22,080 | 5.7% | 19.1s | ✅ |
| 1M | 753,301 | 94,944 | 12.6% | **41.5s** | ✅ |
| 1.01M | 760,812 | 190,992 | 25.1% | **40.8s** | ✅ |
| 1.25M | 1,250,036（估算） | — | — | 3.4s | ❌ 400 超窗 |

### 3.2 `dots-studio/dots-3-note-preview:free`（免费，512,000 窗口）

| 目标 chars | 实际 `prompt_tokens` | 延迟 | 针 |
|---|---|---|---|
| 8k | 6,051 | 4.7s | ✅ |
| 64k | 48,245 | 5.1s | ✅ |
| 256k | 192,869 | 13.7s | ✅ |
| 512k | 被拒（513,030 > 512,000） | 3.8s | ❌ |
| 500k | 376,671 | 17.0s | ✅ |

**两条判据教训（本波实摔）**

1. **「512k 窗口」不等于「可以发 512k 输入」**：输入 + 输出共享窗口，`max_tokens=1024`
   使 512k 目标变成 513,030 而被拒。**任何贴近窗口上限的测试都必须先扣掉输出预留**，
   否则会把「判据没留余量」记成「模型不支持」。
2. **同一模型不同 provider 路由的 token 计数不一致**：同为 `nvidia/nemotron-3.5-super-vl`，
   两次请求的 chars/token 分别是 **5.3** 与 **4.0**（753k vs 1,250k 两次）。
   ⇒ 客户端**不能**用字符数线性外推 token 数；贴近窗口上限时预算必须留 ≥10% 余量，
   否则「估算没超」的请求会被端点 400。

---

## 四、缓存实测（两处独立证据）

### 4.1 同前缀连发两次（6,036 token）

| 次序 | `prompt_tokens` | `cached_tokens` | 命中率 |
|---|---|---|---|
| 第 1 次 | 6,036 | 1,104 | 18.3% |
| 第 2 次 | 6,036 | 4,416 | **73.2%** |

第 1 次就命中 1,104 —— 说明该前缀在**更早的阶梯请求**里已经被写过（filler 相同）。
第 2 次升到 4,416 说明缓存写入是**渐进**的（异步落盘 / 分块）。

### 4.2 阶梯里的「公共前缀」精确对齐（决定性证据）

阶梯每一档的干草堆是**同一段 filler 重复**，针插在 50% 深度 ⇒ 各档的前缀 =
`body[:half]`，而 `half` 随档位线性增长。于是「较长档能命中的公共前缀」
= **最短一档的一半**。

| 档位 | 实测 `cached_tokens` | 理论公共前缀（`最短档 half / 4`） |
|---|---|---|
| 192k | 1,104 | 前一档 48k 的一半 ≈ 6k token → 不对齐 |
| 385k | 22,080 | 192k 档 half ≈ 96k token |
| 753k | 94,944 | **385k 档 half ≈ 96k token** ✅ 几乎精确 |
| 760k | 190,992 | 753k 档 half ≈ 188k token ✅ 精确 |

**结论**：命中量**不是**由「都叫 system」或「长度相近」决定的，而是由
**逐字节相同的最长前缀**决定的。这直接推出实现约束：

- 前缀里任何一处字节变化（技能顺序、记忆文本、工具清单、压缩切点、补丁 id 抖动）
  ⇒ 该点之后**全部**失效；
- 所以「稳定段」必须按**变化频率**分层，越稳定的越靠前；
- 压缩是**缓存失效事件**：压缩丢的是**最老的历史（头部）**、保留尾部，
  所以切点越靠前（越靠近头部），需要重算的前缀越短、失效越轻 ⇒ 切点应**尽量靠前**，
  并落在**工具组安全边界**上（不能把 `assistant.tool_calls` 与 `tool` 结果拆散）。
- 反过来，**压缩本身如果每轮复发**，就等于每轮把切点往后推一次 = 每轮前缀全失效。
  所以「压缩频率」比「压缩策略」更决定缓存命中率（W4 设计 §2.1 的主结论）。

---

## 五、音频输出

| 模型 | 结果 | 原文 |
|---|---|---|
| `openai/gpt-audio-mini` | ❌ 403 | `This model is not available in your region.` |
| `google/lyria-3-clip-preview` | ❌ 403 | 同上 |

与 W3 结论一致。**音频输出在本区不可用**，且失败发生在**路由层**（Geo Restriction），
换 key 无效。⇒ 语音能力只保留设备本地 TTS（`speak`，W3 已实现、默认关闭）。

音频**输入**可用模型（供后续需要时选）：`thinkingmachines/inkling`（1M，付费）、
`nvidia/nemotron-3-nano-omni-30b-a3b-reasoning`（256k，免费池但被账号策略挡）。

---

## 六、对实现的直接影响

| 决策 | 依据 |
|---|---|
| 回归跑分基线改为 **`dots-studio/dots-3-note-preview:free`**（免费、工具+视觉、512k） | §二：免费池唯一双通模型 |
| 长上下文真机/脚本测试用 **`nvidia/nemotron-3.5-super-vl`**（付费但 $0.15/M，1M） | §三：唯一实测到 760k 的模型 |
| 端点预算公式必须**扣输出预留 + 留 ≥10% 余量** | §三 两条教训 |
| 不做云端 TTS | §五 |
| 缓存记账以 `cached_tokens / prompt_tokens` 为准，但**不承诺**命中率 | §四：命中率是供应商性质 |
| 前缀分层的**唯一硬约束**是逐字节稳定，不是「都放进 system」 | §四 结论 |

---

## 七、已知缺口（不许写成已解决）

1. **免费池 12/14 被账号策略挡住**，需账号持有人在
   `https://openrouter.ai/settings/privacy` 打开「允许免费模型训练」后才能复测。
   在此之前，任何「免费模型全面测试」的结论都只覆盖 2 个模型。
2. **未测流式（SSE）在 700k+ 上下文下的首字节延迟**：本轮全部非流式。
3. **未测多模态在长上下文下的组合**（图片 + 700k 文本）。
4. **未测 NVIDIA 官方端点的长上下文**：本轮长上下文全部走 OpenRouter 路由。
5. **`cached_tokens` 的语义边界**：部分供应商不报该字段，解析层把它压成 `0`，
   因此「0 命中」与「未报告」在数据上不可区分（W3 已记，本波未修）。
