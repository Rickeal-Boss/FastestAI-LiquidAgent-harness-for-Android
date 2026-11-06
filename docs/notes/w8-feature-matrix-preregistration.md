# W8 功能矩阵 · 跑前预登记

> **纪律：判据先写死、跑后不得改。** 若确需改判据 ⇒ 版本 +1 并原样重跑，不许边跑边调口径。
> 建立：2026-11-06（主理人，接手 CI 席）。基线 `578f7e4` + W8 功能波改动。
> 事实来源：`docs/analysis/w8-provider-probe.md`（PC 侧 9 组 `stream:true` + 20 余份非流式实测）。

## 一、前置条件（所有判据共享，违反 ⇒ 该项判据无效）

1. **`max_tokens` 必须给足**：实测 64 会被 reasoning 吃光（`finish=length`/`content=None`），
   tools 需 ≥512、xhigh 档比较需 ≥1500。任何「模型没做 X」的结论前先看 `finish_reason`。
2. **App 恒 `stream:true`**：PC 侧非流式结论迁移到 App 须重新取证（PC 探针已补 9 组流式对照）。
3. **强制登记四元组**：`max_tokens / prompt_tokens / completion_tokens / finish_reason`。
4. 1M 档结论需 ≥2 次重放才写稳定结论（W7 纪律）。
5. **设备侧与 PC 侧分开记账**：PC 侧是纯 Python 直连 HTTP、绕过 app 内核。

## 二、端点分组

| 组 | 模型 | 实测上限 | 备注 |
|---|---|---|---|
| OR-FREE-1 | `dots-studio/dots-3-note-preview:free` | 512K / 460800 out | 文本+图片输入；`supported_parameters` **无 `reasoning_effort`** |
| OR-FREE-2 | `cohere/north-mini-code:free` | 256K / 64K out | 文本输入；无 `response_format` |
| NV-1 | `nvidia/nemotron-3.5-super-vl-120b-a12b` | 实测 1,000,000（端点报 1,048,576 超报） | 视觉；直连缓存 79.4% |

## 三、逐项判据（四态）

| 项 | 判据 | 前置 | 状态 |
|---|---|---|---|
| 上下文分档至 1M | 各档 `prompt_tokens` 达标且 `finish=stop`；NV 1M 需 ≥2 次重放 | `max_tokens` 给足 | ⛔ **未行使**（PC 侧 probe 只到 8KB 前缀；设备侧受 `PreStepBudget` 档位闸 27904 + `RoundHeadGate` 400k 双阻断，见 W8-plan §八.1） |
| 多模态输入 | 200×120 双色图 ⇒ 语义判读正确（禁用 1×1） | `max_tokens≥512` | ✅ **PC 流式已取**：dots 返回 `red, blue`（`_w8/parse-image.json`）；设备侧 ⛔ 未行使 |
| 音频输出 | 无供应商 + 无 TTS 通道 | — | ⛔ **不适用**：OR 两模型 `supported_voices=null`；NV 85 模型 0 audio-ish；`/v1/audio/speech` 404 |
| 工作空间文件读写 | file_read/file_write 端到端往返一致 | App 内核 | ⛔ 未行使（本波未跑设备侧工具循环） |
| 记忆 | memory_write → 重启 → memory_read 命中 | App 内核 | ⛔ 未行使 |
| Skill | 内置 Skill 加载 + 触发 | App 内核 | ⛔ 未行使（W3-02 Skill **导入**无入口，只能测内置） |
| MCP | MCP 真机联调 | https 夹具或 cleartext 裁决 | ⛔ **不适用（平台阻塞）**：`AndroidManifest.xml:25 usesCleartextTraffic="false"`（W5-03，不为测试放开） |
| 分块缓存命中 KV cache | 前缀逐字节稳定 ⇒ 第 N 次 `cached_tokens>0`；改首字节 ⇒ 归零（**双向**） | 稳定前缀 | ⛔ **vacuous（OR）**：8KB 前缀四连发 `cached_tokens` 全 0，`cache_write=0` ⇒ 「改首字节归零」不可执行；NV 直连 79.4% 是唯一正向证据（W7 A5） |
| 手动压缩 | dropped=N kept=M + `cache_invalidated=true` + 下一请求 `cached=0`（**双向**） | 长会话 | ⛔ 未行使（A6 同款双向未触发，W7-03 仍 OPEN） |
| 自动压缩 | `auto_compact` applied=true | 长会话 + 超阈值 | ⛔ **vacuous**：`applied=true` 至今零证据（W7 全是 noop）；W8-01 尾部摘要挂 W9-03 |

## 四、dots 请求格式（用户点名，已闭环）

`reasoning` 对象族（`enabled:false` 关思考 rtok=0 / `include_reasoning:false` 仅隐藏仍耗 32 rtok /
`effort` 五档）；`response_format:json_object` 返回严格合法 JSON；tools 在 `max_tokens≥512` 时
`finish=tool_calls`。**现役 `REASONING_EFFORT` 发 `reasoning_effort` 对 dots 无效** ⇒ W8 新增
`ReasoningStyle.OPENROUTER_REASONING_OBJECT`（adapter 消费 `reasoning:{...}`）。

## 五、诚实声明

- 本表**只登记不判定**。⛔ vacuous ≠ 通过；「组件有测试」≠「组件被行使」。
- 未行使项不阻塞 W8 功能波交付（功能本身有单元判据 + 全量编译 + 真机冒烟），
  但**不得**在 handoff / roadmap 里写成「已验证」。
