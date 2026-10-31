# FastestAI · LiquidHarness for Android

> 一个跑在 Android 上的 **API-first Agent Harness**。模型在云端，能力在本地：
> 工具循环、审批闸门、Skill、MCP、上下文压缩、运行账本全部在设备侧完成。

- **包名**：`com.rickeal.liquidharness`
- **平台**：Android 12+（minSdk 31），Kotlin + Jetpack Compose
- **模型**：任意 OpenAI 兼容端点（OpenAI / DeepSeek / Ollama / vLLM / SiliconFlow / 自建），
  规划中支持 Anthropic 与 Gemini 原生协议
- **不含**：本地推理引擎、模型权重、模型下载器。本仓不跑模型，只驱动模型。

## 为什么另起一个仓

上一代 [LiquidAgent](https://github.com/Rickeal-Boss) 是端侧优先（LiteRT-LM，数据不出设备），
它把「模型在哪跑」和「harness 怎么组织一次任务」这两件事绑在了一起。结果是：

- 引擎版本锁（litertlm 0.17.1）牵制了整个仓的升级节奏；
- 1–4B 模型的指令遵循与工具调用能力成为能力天花板，调优解决不了；
- 为了适配端侧，内核里长出了大量「无网络、低延迟、单会话」的隐含假设。

本仓把这两件事拆开：**harness 是主体，模型是可插拔后端**。端侧积累的判据（断路器、审批、
压缩、账本）只保留在 API 路径下**仍然可触发**的部分，其余删除 —— 空转的判据比没有判据更危险。

## 现在能做什么（截至 W2.5）

- [x] 配置多个 API 端点（OpenAI 兼容），密钥加密落盘
- [x] 流式对话（SSE），首 token 延迟与 tok/s 可见
- [x] 工具循环（内置工具 + 参数级审批）
- [ ] Skill 加载（`SKILL.md` + 三级渐进披露，W4）
- [ ] MCP Client（Streamable HTTP，双 era，W5）

> 勾选状态以 `docs/02-roadmap.md` 为准，本列表只是索引。

## 构建

```bash
./scripts/localbuild.sh                 # :app:assembleDebug
./scripts/localbuild.sh test            # 全量 JVM 单测
./scripts/fulltest.sh                   # 单测 + 汇总 tests/failures/errors/skipped
```

CI 见 `.github/workflows/`：`build.yml` 零密钥可跑；`release.yml` 走 tag + 签名。

## 文档

| 文件 | 内容 |
|---|---|
| `docs/00-brief.md` | 定位、非目标、与上一代的关系、裁决清单 |
| `docs/01-architecture.md` | 模块图、依赖拓扑、关键接口、数据流 |
| `docs/02-roadmap.md` | W1–W8 波次与验收判据 |
| `docs/03-contracts.md` | 核心数据契约 |
| `docs/04-risk-register.md` | 风险登记与缓解 |
| `AGENTS.md` | **给 agent 的作业契约**（构建、边界、纪律） |

## 许可

Apache-2.0。见 `LICENSE` / `NOTICE`。
