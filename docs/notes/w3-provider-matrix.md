# W3 供应商能力矩阵（实测，2026-10-31）

> 口径：所有结论都来自本波**实际发出的请求**，不是模型卡页面上的声明。
> 脚本：`_w27/probe_capability.py`（仓外，一次性工具）；原始记录 `_w27/capability_matrix.jsonl`。
> 探测项：`chat`（一次非流式成功）/ `tools`（响应出现 `tool_calls[0].function.name`）/
> `vision`（图内文字 `HARNESS-42` 被正确读出）/ `stream` / `cache` / `longctx`（后三项只对短名单跑）。

---

## 一、结论先行（三条，全部影响实现决策）

1. **OpenRouter 免费池不可用作跑分基线**：9 个 `:free` 模型里 4 个直接 429（上游限流）、
   1 个 503（`no upstreams available`），能用的只有 3 个，且都不支持视觉。
   ⇒ **harness 的回归跑分不能依赖免费池**，否则「失败」与「上游限流」无法区分。
2. **`nvidia/nemotron-3.5-super-vl` 是唯一实测通过的视觉+工具双能力模型**（经 OpenRouter，付费但极便宜）。
   NVIDIA 官方端点上的同一模型**读图失败**（返回文本但未识别图内文字）⇒ 视觉能力**与接入端点绑定**，
   不能按模型名声明。
3. **音频输出在本区不可用**：`google/lyria-3-{clip,pro}-preview` 返回
   `403 This model is not available in your region`；`heygen-voice-1-turbo` 在 OpenRouter 上**不存在**；
   `thinkingmachines/inkling` 是音频**输入**（不是输出）。
   ⇒ W3 的语音能力改为**设备本地 TTS**（`speak` 工具），不引入云端语音依赖。

---

## 二、逐模型实测

| 模型 | 端点 | chat | tools | vision | 备注 |
|---|---|---|---|---|---|
| `recursive/flint-v1.0:free` | OR | ❌ 503 | ❌ | ❌ | `no upstreams available` |
| `xingchen-agi/xing4.0-29b-a4b:free` | OR | ❌ 429 | ❌ | ❌ | 上游限流 |
| `dots-studio/dots-3-note-preview:free` | OR | ✅ | ✅ | ❌ 404 | 无支持图片的 endpoint |
| `cohere/north-mini-code:free` | OR | ✅ | ✅ | ❌ 404 | 同上 |
| `google/gemma-4-26b-a4b-it:free` | OR | ❌ 429 | ❌ | ❌ | 上游限流 |
| `google/gemma-4-31b-it:free` | OR | ❌ 429 | ❌ | ❌ | 上游限流 |
| `nvidia/nemotron-3.5-super-vl` | OR | ✅ | ✅ | ✅ | **唯一视觉+工具双通** |
| `thinkingmachines/inkling` | OR | ✅ | ✅ | ✅ | 音频**输入**模型，付费 |
| `nvidia/nemotron-3.5-super-vl-120b-a12b` | NV | ✅ | ✅ | ❌ | 同模型，视觉未生效 |
| `nvidia/nemotron-3-super-120b-a12b` | NV | ❌ 404 | — | — | 账号无该 function |
| `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning` | NV | ✅ | ✅ | ❌ | — |
| `nvidia/nemotron-3.5-lightning-30b-a3b` | NV | ✅ | ✅ | ❌ 400 | `multimodal processing is not enabled`（服务端开关） |
| `mistralai/mistral-large-4-1t-a52b` | NV | ✅ 2.1s | ✅ | ❌ | 响应含 `prompt_tokens_details.cached_tokens` |
| `stepfun-ai/step-5-preview` | NV | ❌ 超时 | — | — | 120s 无响应 |
| `deepseek-ai/deepseek-v4.1-flash` | NV | ❌ 超时 | — | — | 120s 无响应（curl 实测） |
| `z-ai/glm-5.3-flash` | NV | ❌ 超时 | — | — | 同上 |

`google/lyria-3-clip-preview` / `lyria-3-pro-preview`：元数据声明 `output_modalities: [text, audio]`、
价格为 0，但**本区 403**。

---

## 三、对实现的直接影响

| 决策 | 依据 |
|---|---|
| 视觉能力必须**逐端点**声明（`RemoteEndpoint` 现有 `supportsTools` 的同级开关），不能按模型名推断 | §二 第 2 条：同名模型在不同端点行为不同 |
| 回归跑分继续用 GLM-4.7（BigModel）作为基线，不用免费池 | §一 第 1 条 |
| 语音输出用本地 TTS，不做云端 TTS 适配器 | §一 第 3 条 |
| `cached_tokens` 字段在 NVIDIA 端点也存在，缓存记账不需要按供应商分叉 | mistral 实测响应 |

---

## 四、已知缺口（不许写成已解决）

1. **MCP token 未进 Keystore**：`McpServerStore` 落盘前把 `Authorization` 脱敏成 `***`，
   因此**重启后 token 需要重填**。真正的做法是复用 `EndpointSecretStore` 的信封（key = `mcp:<id>`），
   排到 W7（与 `SecretRedactor` 扩容一起做）。
2. **`supportsVision` 未进 `RemoteEndpoint`**：本波的视觉路径是「有附件就发 content 数组」，
   端不支持时由服务端 400/404 暴露，UI 只显示错误。
   逐端点开关需要改端点编辑页 + 探测逻辑，排到 W4。
3. **长上下文 / 流式 / 缓存三项只跑了短名单**（时间预算），
   `longctx` 在 `nvidia/nemotron-3.5-super-vl` 上通过（12k 干草堆取针），其余未跑。

---

## 五、长程上下文压测（同一模型，逐尺寸单针）

脚本 `_w27/longctx.py`；针放在 50% 深度，题目明确「只有一根针」。
模型 `nvidia/nemotron-3.5-super-vl`（OpenRouter 端点），`max_tokens=1024`。

| 目标规模 | 实际 `prompt_tokens` | `cached_tokens` | 命中率 | 延迟 | 针码 |
|---|---|---|---|---|---|
| 8k | 6,976 | 5,520 | 79.1% | 4.7s | ✅ |
| 64k | 56,475 | 55,200 | 97.7% | 3.2s | ✅ |
| 256k | 228,243 | 226,320 | 99.2% | 5.5s | ✅ |
| 900k（实测 815k） | 815,017 | 26,496 | 3.3% | **57.1s** | ✅ |

**四条结论**

1. **815k token 单次请求成功，且针码被正确取出** —— 1M 级窗口在本仓可用。
2. **延迟在 815k 处超线性跳变**（5.5s → 57.1s）。不是线性外推能预测的，
   所以「窗口够大」≠「体验可接受」：长上下文要走异步 + 进度反馈，不能同步等。
3. **长前缀缓存命中率极高**（64k/256k 均 >97%）。这与 W2.6 在 GLM-4.7 上观察到的
   「命中恒 896、不可控」**不是同一个现象**：命中率取决于**供应商是否实现了前缀缓存**，
   不取决于客户端。`W26-05` 的措辞应改为「命中率随供应商而变，客户端只能保证前缀稳定」。
4. **815k 那次命中只有 3.3%**：因为每次尺寸的干草堆内容不同，前缀不共享。
   ⇒ 缓存命中的前提是**逐字节相同的前缀**，不是「都叫 system」。

### 一次测量事故（必须留痕）

首轮用 `max_tokens=64` 跑 9 个组合，结果 **9/9 全 False**。原因不是模型读不到针，
而是**推理模型把 64 个 token 全花在 reasoning 上，`content` 返回空字符串** ——
判据 `needle in text` 因此恒假。改成 1024 后 4/4 全 True。
这是「判据本身没被验证」的典型：如果只看汇总数字，会得出「该模型没有长上下文能力」
这个完全相反的结论。
