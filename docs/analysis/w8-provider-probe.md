# W8 · 免费节点请求格式实测底稿（主理人 Phase 0）

> 2026-11-05，PC 侧直连实测。基线 `578f7e4`。所有响应原文留在工作区根
> `_w8_dots_*.json` / `_w8_fmt_*.json` / `_w8_eff_*.json` / `_w8_vision_resp.json` / `_w8_nv_models.json`。
> **本文只记实测事实与设计约束，不写「已验证产品能力」**；设备侧行使另见 W8 真机验收四态表。

---

## 一、模型元数据（OpenRouter `/v1/models`，实测）

| 模型 | context_length | max_completion | modality | supported_parameters |
|---|---|---|---|---|
| `dots-studio/dots-3-note-preview:free` | **512 000** | 460 800 | `text+image->text`（input `[text,image]` / output `[text]`） | `include_reasoning`, `max_tokens`, `reasoning`, `response_format`, `structured_outputs`, `temperature`, `tool_choice`, `tools`, `top_p` |
| `cohere/north-mini-code:free` | **256 000** | 64 000 | `text->text` | `frequency_penalty`, `include_reasoning`, `max_tokens`, `reasoning`, `seed`, `stop`, `temperature`, `tool_choice`, `tools`, `top_k`, `top_p` |

🔴 **`dots` 的 supported_parameters 里没有 `reasoning_effort`**。现役
`core-engine/.../provider/OpenAiCompatibleAdapter.kt:182` 的 `REASONING_EFFORT` 分支发
`"reasoning_effort": "medium"`，对该模型属**发了网关不认的字段**（实测不报 400，但也不改变思考量）。

NVIDIA（`https://integrate.api.nvidia.com/v1`，密钥取自 `<凭据文件：NVIDIA API Key（本地路径已隐去）>`）：
`GET /v1/models` 200、`models_count=85`，密钥有效；W7 已实测
`nvidia/nemotron-3.5-super-vl-120b-a12b` 真实上限 1 000 000 token（含输出），端点写 1 048 576 是超报。

---

## 二、`dots` 请求格式逐项实测（`/v1/chat/completions`，`stream=false`）

| # | 请求体关键字段 | `finish_reason` | `message.content` | `message.reasoning` | `reasoning_tokens` | 判定 |
|---|---|---|---|---|---|---|
| 1 | 仅基础字段，`max_tokens=64` | `stop` | `'\n\nPONG'` | **非空** | 38 | **默认就开思考**，且默认返回思考正文 |
| 2 | `reasoning:{"effort":"low"}` | `stop` | `'\n\nPONG'` | 非空 | 25 | `reasoning` 对象被接受 |
| 3 | `reasoning_effort:"low"`（现役形态） | `stop` | `'\n\nPONG'` | 非空 | 33 | 不报错但看不出效果 ⇒ 疑似被网关忽略，**不能当档位通道** |
| 4 | `tools[]` + `max_tokens=64` | `length` | `None` | 被截断 | 69 | ⚠️ **判据自身失效**：输出预算被 reasoning 吃光 |
| 5 | `tools[]` + `max_tokens=512` + `tool_choice:auto` | **`tool_calls`** | 空白 | 非空 | 17 | ✅ `tool_calls=1`，工具调用真实可用 |
| 6 | `reasoning:{"enabled":false}` | `stop` | **`'PONG'`** | `None` | **0** | ✅ **真能关思考**（连前导换行都消失） |
| 7 | `include_reasoning:false` | `stop` | `'\n\nPONG'` | **`None`** | **42** | ✅ **只隐藏思考文本、不关思考**：仍计费 42 token |
| 8 | `response_format:{"type":"json_object"}` + `reasoning.enabled=false` | `stop` | `'{"answer": 42, "ok": true}'` | `None` | 0 | ✅ 严格合法 JSON，`json.loads` 通过 |
| 9 | 200×120 双色 PNG（左红右蓝）走 `content:[{type:text},{type:image_url}]` | `stop` | **`'red, blue'`** | `None` | 0 | ✅ 视觉输入真实可用（**禁用 1×1 样本**纪律已遵守） |

### 思考强度五档（同一推理题面 `Compute 17*23 step by step`，`max_tokens=600`）

| effort | reasoning_tokens | completion_tokens | finish | 答案 |
|---|---|---|---|---|
| `low` | 71 | 109 | stop | 391 |
| `medium` | 79 | 112 | stop | 391 |
| `high` | 323 | 312 | stop | 391 |
| `xhigh` | **688** | 600 | **`length`（截断）** | 空 |
| `max` | 198 | 300 | stop | 391 |

**结论**：`low / medium / high / xhigh` 是被识别的档位且思考量单调递增；
`max` 落在 high 与 medium 之间、不服从单调性 ⇒ **疑似非法值被网关降级/忽略，不是第五档**。
`xhigh` 在 600 token 预算下被截断 ⇒ 档位对照判据必须给足 `max_tokens`（≥1500）才能比较。

---

## 三、对设计的硬约束（派单口径）

1. **「允许关闭思考」与「仅隐藏思考」是两个语义**：
   `reasoning:{"enabled":false}` = 真关（rtok=0，省钱）；
   `include_reasoning:false` = 只不回传（rtok 照付）。
   UI 上必须分开，不得用一个布尔糊过去。「仅思考模式」是第三态（要求模型只产出思考、不产出正文），
   需单独定义，不要与上面两者混为一谈。
2. **五档 UI（低/中/高/超高/极致）不能 1:1 映射供应商参数**：
   供应商只认 4 档（low/medium/high/xhigh）。设计要么把「极致」映射为 `xhigh` +
   更高输出预算，要么显式登记「本端点不支持该档」并降级；**禁止静默发非法值 `max`**。
3. **协议层必须新增 OpenRouter `reasoning` 对象形态**，否则高级配置里的思考档位在 dots 上完全不生效。
   同时保留现役 `ENABLE_THINKING_BOOL` / `REASONING_EFFORT` / `CHAT_TEMPLATE_KWARGS`
   三种形态给其他供应商，按端点配置选择，不改默认行为。
4. **`max_tokens` 是所有工具/思考判据的前置条件**：W5 铁律「判据自身要先被验证」在本轮再次命中
   （第 4 行）。任何工具调用 / 档位对照 / 视觉判据都先确认输出预算充足。
5. **JSON 检查功能有真实供应商支持**：`response_format:{"type":"json_object"}` 返回严格合法 JSON；
   但「校验返回文本」与「强制模型只回 JSON」是两件事，功能设计需把两者分开并各自给判据
   （还要容忍 ```json 围栏与前后空白）。
6. **音频输出**：W7 已判定 ⛔ 不适用（NV 85 模型 0 音频类、`/v1/audio/speech` 404、
   设备仅 iflytek TTS）。本波仍按「不适用」登记，不编造通过结论；OR 两个模型的
   `output_modalities=[text]` 与 `usage.completion_tokens_details.audio_tokens=0` 是一致的旁证。
7. **usage 三桶**：OR 响应带 `prompt_tokens_details.{cached_tokens, cache_write_tokens, audio_tokens, video_tokens}`
   与 `completion_tokens_details.reasoning_tokens`。W8-02 三桶归一若开波，这两个字段是现成 fixture 来源。

---

## 四、新请求形态下的前缀缓存（2026-11-05 复验）

形态：固定 8 000 字符 system 前缀 + `reasoning:{"enabled":false}`（即本波高级配置会生成的请求体），
`max_tokens=64`，同一请求体连发 3 次，第 4 次只改前缀首字节。响应 `_w8_cache_resp{1..4}.json`。

| 发送 | prompt_tokens | cached_tokens | cache_write_tokens | finish |
|---|---|---|---|---|
| 1 | 1853 | 0 | 0 | stop |
| 2 | 1853 | 0 | 0 | stop |
| 3 | 1853 | 0 | 0 | stop |
| 4（改首字节） | 1853 | 0 | 0 | stop |

**结论**：OR `dots` 免费池在 8KB 前缀 + 关闭思考形态下**四发全 0**，既无命中也无写入，
「第 4 次改首字节 ⇒ 归零」的双向断言**不可执行（⛔ vacuous）**——本来就没命中过，谈不上归零。
与 W7 结论一致：**缓存工程主战场只能是 NV 直连，OR 免费池不承诺命中率**；本波只登记事实表，
不把「分块缓存命中」写成已验证通过。W7 A5 的 NV `21680 cached / 79.4%` 仍是唯一正向证据。

---

## 五、尚待行使（不得写成通过）

- 分块前缀缓存命中的**双向断言**（`cached_tokens>0` 且改首字节归零）——W7-02 已取到 NV 79.4% /
  OR 免费池恒 0 的事实表，本波只做复验与分档记录。
- 1M 上下文在**设备侧**的行使（W7 A7 ⛔ vacuous，会话导入通道未跑）。
- 工作空间文件读写 / 记忆 / Skill / MCP 在**新增高级配置端点**下的端到端行使。
- 上述所有 PC 侧结论迁移到真机需重新取证，本文不构成真机证据。

---

## 六、`stream:true` 对照（App 真实路径，2026-11-05 补跑）

> 起因：方案席指出 §一~§四 全部探针为 `stream:false`，而现役
> `OpenAiCompatibleAdapter.kt:76` **恒发 `stream:true`**、`:103` 只解析 `choices[0].delta`。
> 补跑 9 组，全部带 `stream_options.include_usage=true`（与现役 `:78-79` 一致）。
> 证据链照 W7 先例归档：请求体 `_w8/req-*.json`、SSE 原文 `_w8/resp-*.sse`、
> 解析摘要 `_w8/parse-*.json`、脚本 `_w8/stream_probe.py`（可原样重跑 `gen` / `parse`）。

### 6.1 帧结构（实测 `_w8/resp-basic.sse`）

首帧 `object:"chat.completion.chunk"`，`delta` 同时含：

```json
{"content":"","role":"assistant","reasoning":"The",
 "reasoning_details":[{"type":"reasoning.text","text":"The","format":"unknown","index":0}]}
```

- `delta.reasoning` **有值**；`delta.reasoning_details[].type=reasoning.text` 与其**逐字等长**（basic 141/141，
  eff_high 1709/1709）⇒ **OR 发的是同一份思考的冗余双通道**，不是两个不同内容。
- `delta.reasoning_content` **恒 0**。
- 现役 `:106-108` 是 `reasoning_content ?: reasoning` ⇒ **回落命中 `delta.reasoning`，思考已能进
  `GenerationEvent.ReasoningDelta`**。
- 🔴 **「不读 `reasoning_details` ⇒ 端到端断链」被证伪**：不必为此改 `decodeEvent`。
  按「防御性修正必须如实标注、不造伪工作量」纪律，本波**不改**这一行。
- **usage 帧位订正（审查席 §九 W4 独立复跑 9 组 SSE）**：usage 与 `finish_reason` **同帧**，
  且该帧 `choices` **非空**（9 组 `empty_choice_frames=0`），不是 `choices: []` 的独立末帧。
  现役 `:132-141` 的 `obj["usage"]` 解析独立于 `choices` 分支 ⇒ 两种帧位都能读到 ✅。
  流尾 `data: [DONE]`，现役 `isTerminal` 判它 ✅。

### 6.2 九组流式结果（`_w8/parse-*.json`）

| 组 | finish | 正文 | `delta.reasoning` | rdetails | reasoning_tokens | tool_calls |
|---|---|---|---|---|---|---|
| basic | stop | `'\n\nPONG'` | 141 | 12 条 / 141 | 36 | 0 |
| off（`reasoning.enabled=false`） | stop | **`'PONG'`** | 0 | 0 | **0** | 0 |
| incl（`include_reasoning:false`） | stop | `'\n\nPONG'` | **0（不回传）** | 0 | **32（仍计费）** | 0 |
| eff_low | stop | `'\n\n391'` | 606 | 77 | 158 | 0 |
| eff_high | stop | `'\n\n391'` | 1709 | 193 | 441 | 0 |
| eff_xhigh | stop | `'\n\n391'` | 737 | 92 | 193 | 0 |
| tools（`max_tokens=512`） | **tool_calls** | 空白 | 127 | 9 | 32 | **3 碎片 / name=ping** |
| image（200×120 真图） | stop | **`'red, blue'`** | 0 | 0 | 0 | 0 |
| jsonfmt（`json_object`） | stop | `'{"answer": 42, "ok": true}'` | 0 | 0 | 0 | 0 |

**订正与新增结论**：

1. §二 的「关闭思考 / 仅隐藏思考 / tools / 视觉 / JSON」五组结论在流式路径**同样成立** ⇒ 从
   「⚠️ 非流式部分」升级为 **✅ 流式已取证**（仍不等于真机）。
2. `include_reasoning:false` 在流式下 `delta` 完全不带 reasoning，但 `reasoning_tokens=32` 照付 ⇒
   「隐藏 ≠ 省钱」在 App 路径再证实一次。
3. 🔴 **档位单调性在单次流式样本下不成立**：low 158 / high 441 / xhigh **193**，与非流式
   low 71 / high 323 / xhigh 688 排序不一致。按 W7 纪律「1M/档位结论 ≥2 次重放才写稳定结论」，
   本波**不写**「xhigh 必强于 high」的产品承诺，也不再把 `max` 单次非单调直接定成「非法值」；
   只登记事实表，把「五档真实梯度」标 **⚠️ 部分**，需要 ≥2 轮重放（挂矩阵批次）。
   设计含义不变：供应商可识别值仍是 `low/medium/high/xhigh`，UI 五档必须显式登记降级，不得静默发未验证值。
4. `stream_options.include_usage=true` 与 `[DONE]` 现役都已正确处理，**本波不动 `decodeEvent` 的 usage/终态分支**。
