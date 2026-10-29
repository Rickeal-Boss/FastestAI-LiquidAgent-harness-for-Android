# 03 — 核心数据契约（Kotlin 草案）

> 这些是**草案**：W1 落地时以编译通过为准，字段可增不可改语义。
> 所有 `@Serializable` 类的字段**必须带默认值**（旧仓纪律：`ignoreUnknownKeys=true` + `explicitNulls=false` ⇒ 旧 JSON 前后兼容）。
> 所有 `data class` 字段一律可空或带默认值 —— 服务端响应解析不得因缺字段崩。

---

## 一、端点与密钥

```kotlin
package com.rickeal.liquidharness.core.model

/**
 * 供应商传输族。**这是传输概念**，与 UI 上的「预设」是两件事。
 *
 * 为什么不是旧仓的 RemotePreset 六值（OPENAI/DEEPSEEK/OLLAMA/VLLM/SILICONFLOW/CUSTOM）：
 * 那六个里有四个在线上是**同一个协议**（OpenAI Chat Completions），
 * 差异只有 baseUrl + 思考参数风格 + extraHeaders —— 那是配置，不是代码分支。
 * 把它们做成枚举，等于让每个新供应商都要改一次 enum 与所有 when 分支。
 */
@Serializable
enum class ProviderKind {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI,
}

/** 只用于「新建端点时填默认值」的 UI 概念。不参与任何传输逻辑。 */
@Serializable
enum class UiPreset {
    OPENAI, DEEPSEEK, OLLAMA_LOCAL, VLLM, SILICONFLOW, ANTHROPIC, GEMINI, CUSTOM;

    fun seed(): EndpointSeed = when (this) {
        OPENAI       -> EndpointSeed(ProviderKind.OPENAI_COMPATIBLE, "https://api.openai.com/v1", 128_000, 16_384, true, true)
        DEEPSEEK     -> EndpointSeed(ProviderKind.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1", 65_536, 8_192, true, true)
        OLLAMA_LOCAL -> EndpointSeed(ProviderKind.OPENAI_COMPATIBLE, "http://10.0.2.2:11434/v1", 32_768, 4_096, false, true)
        VLLM         -> EndpointSeed(ProviderKind.OPENAI_COMPATIBLE, "http://10.0.2.2:8000/v1", 32_768, 4_096, false, true)
        SILICONFLOW  -> EndpointSeed(ProviderKind.OPENAI_COMPATIBLE, "https://api.siliconflow.cn/v1", 32_768, 4_096, true, true)
        ANTHROPIC    -> EndpointSeed(ProviderKind.ANTHROPIC, "https://api.anthropic.com", 200_000, 8_192, true, true)
        GEMINI       -> EndpointSeed(ProviderKind.GEMINI, "https://generativelanguage.googleapis.com", 1_000_000, 8_192, true, true)
        CUSTOM       -> EndpointSeed(ProviderKind.OPENAI_COMPATIBLE, "", 32_768, 4_096, false, false)
    }
}

data class EndpointSeed(
    val kind: ProviderKind, val baseUrl: String,
    val contextLength: Int, val maxOutputTokens: Int,
    val supportsReasoning: Boolean, val supportsTools: Boolean,
)

/**
 * 一个远程端点。
 *
 * 🔴 **本类不含 apiKey 字段，且永远不许加。**
 * 旧仓存档规格（docs/01-architecture.md:691-776）里 `RemoteEndpoint.apiKey: String` 是
 * 一个 `@Serializable` 字段 ⇒ 它会被序列化进 DataStore 明文文件。
 * 新仓把密钥移到 EndpointSecretStore（Keystore 信封），本类只留 `hasSecret` 布尔。
 * 守卫 G17 机械拦「@Serializable 类出现 apiKey/secret/password 字段」。
 */
@Serializable
data class RemoteEndpoint(
    val id: String = newId(),
    val name: String = "",
    val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val preset: UiPreset = UiPreset.CUSTOM,
    /** 不含 `/chat/completions` 等路径后缀，路径由 adapter 拼。 */
    val baseUrl: String = "",
    val modelId: String = "",
    val contextLength: Int = 32_768,
    val maxOutputTokens: Int = 4_096,
    val supportsTools: Boolean = true,
    val supportsReasoning: Boolean = false,
    /** 思考参数风格。取代旧仓 ThinkingParamStyle 的四个值（语义保留，名字改准）。 */
    val reasoningStyle: ReasoningStyle = ReasoningStyle.NONE,
    val reasoningEffort: String = "medium",
    val extraHeaders: Map<String, String> = emptyMap(),
    /** 允许指向 RFC1918 / loopback（本地 Ollama / 局域网 vLLM）。默认 false = fail-closed。 */
    val allowPrivateNetwork: Boolean = false,
    val hasSecret: Boolean = false,
    val addedAtMillis: Long = System.currentTimeMillis(),
) {
    /** 派生，不持久化。本地/模拟器上的 Ollama 无需 key。 */
    val requiresApiKey: Boolean
        get() = !(baseUrl.contains("11434") || baseUrl.contains("127.0.0.1") || baseUrl.contains("10.0.2.2"))
}

/**
 * 开启思考的线上形态。旧仓叫 ThinkingParamStyle，语义一致；改名是因为
 * 「thinking」是端侧模型的概念（litertlm 的 thinking channel），
 * 远程 API 的正式叫法是 reasoning。同名不同义会造成排查时误判。
 */
@Serializable
enum class ReasoningStyle {
    NONE,
    /** body: enable_thinking = true（Qwen3 / vLLM 部分） */
    ENABLE_THINKING_BOOL,
    /** body: reasoning_effort = "low|medium|high"（OpenAI o 系 / DeepSeek） */
    REASONING_EFFORT,
    /** body: chat_template_kwargs = { enable_thinking: true }（vLLM 另一派） */
    CHAT_TEMPLATE_KWARGS,
    /** Anthropic: thinking = { type: "enabled", budget_tokens: N } */
    ANTHROPIC_THINKING_BUDGET,
    /** Gemini: generationConfig.thinkingConfig.includeThoughts = true */
    GEMINI_INCLUDE_THOUGHTS,
}
```

```kotlin
package com.rickeal.liquidharness.core.data.secret

/**
 * 密钥加解密。接口化的唯一目的：让「密钥存储」在 JVM 单测里可验证
 * （AndroidKeyStore 在 JVM 上不存在）。
 *
 * 实现：AndroidKeyStoreCipher —— AES-256-GCM，密钥别名 "liquidagent.endpoint.v1"，
 * KeyGenParameterSpec:
 *   setBlockModes(GCM) / setEncryptionPaddings(NONE) / setKeySize(256)
 *   setRandomizedEncryptionRequired(true)   // 强制每次随机 IV，防 nonce 复用
 *   setUserAuthenticationRequired(false)    // 不需要锁屏生物识别（否则后台 run 无法解密）
 * 存储格式：base64( iv(12B) || ciphertext )，按 endpointId 存一个文件。
 */
interface SecretCipher {
    fun encrypt(plain: String): String
    fun decrypt(blob: String): String
}

interface EndpointSecretStore {
    fun put(endpointId: String, apiKey: String)
    fun get(endpointId: String): String?
    fun delete(endpointId: String)
    fun has(endpointId: String): Boolean
}
```

---

## 二、工具

```kotlin
package com.rickeal.liquidharness.core.model

@Serializable
enum class ToolParamType { STRING, NUMBER, INTEGER, BOOLEAN, ARRAY, OBJECT }

@Serializable
data class ToolParameter(
    val name: String,
    val type: ToolParamType = ToolParamType.STRING,
    val description: String = "",
    val required: Boolean = true,
    val enumValues: List<String> = emptyList(),
)

/** 能力面：读还是写。与 dangerous（工具属性）正交，勿混。 */
@Serializable
enum class ToolEffect { READ, WRITE }

/**
 * 工具来源。UI 分组、审批默认值、命名冲突策略都按它分支。
 *
 * ⚠️ 刻意**没有** SKILL 值：Skill 不产生工具，只通过 `SkillManifest.allowedTools`
 * 对现有工具面做**交集收窄**（见 §四）。给 Skill 一个 ToolSourceKind 会诱导后人
 * 去实现「技能声明新工具」，那等于把 Skill 从纯文本升级成插件 —— W69-09 判死的
 * 那条路，翻案的唯一支点就是「纯文本、零执行语义」。
 */
@Serializable
enum class ToolSourceKind {
    BUILTIN,      // 内置，可直接用
    MCP,          // 远端，强制审批
    SUBAGENT,     // 子代理工具（ask_actor 等）
}

@Serializable
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val dangerous: Boolean = false,
    val category: String = "general",
    /**
     * 🔴 **默认 WRITE 是刻意的 fail-closed**（照搬旧仓）：
     * 新增工具忘了声明时，在只读档位下会被要求授权，而不是被静默放行。
     */
    val effect: ToolEffect = ToolEffect.WRITE,
    val timeoutMillisOverride: Long? = null,
    val keywords: List<String> = emptyList(),
    /** 新仓新增：来源。MCP 工具的 description 来自远端，是不可信文本。 */
    val source: ToolSourceKind = ToolSourceKind.BUILTIN,
    /** MCP 工具的 server alias；非 MCP 为 null。 */
    val namespace: String? = null,
) {
    fun toPromptLine(): String =
        "- " + name + "：" + description +
            (if (parameters.isEmpty()) "" else " 参数(" + parameters.joinToString(",") { it.name } + ")")
}

@Serializable
data class ToolCall(
    val id: String = newId(),
    val name: String = "",
    val argumentsJson: String = "{}",
    val raw: String = "",
)

/**
 * 工具结果。
 *
 * 新仓相对旧仓**加两个字段**，动机都是 API 路径的新风险：
 *
 * - [displayOutput]：脱敏后的展示文本。旧仓只有 output 一个字段，
 *   而脱敏必须在「上屏」与「回灌模型」两处取不同值 ——
 *   模型让 file_write 写一个 key 到文件是合法需求（执行必须用原文），
 *   但审批卡/气泡里不能显示原文（会进截图与 bug report）。
 *
 * - [indeterminate]：结果不确定（超时/取消后孤儿协程仍可能完成副作用）。
 *   旧仓 W69-13 挂账项，新仓 W3 一并做。模型据此不应假设「没执行」。
 */
@Serializable
data class ToolResult(
    val callId: String = "",
    val name: String = "",
    val ok: Boolean = true,
    val output: String = "",
    val errorMessage: String? = null,
    val elapsedMillis: Long = 0L,
    val truncated: Boolean = false,
    val displayOutput: String? = null,      // null = 与 output 相同
    val indeterminate: Boolean = false,
) {
    /** 给 UI / 日志用。 */
    val shown: String get() = displayOutput ?: output
}
```

```kotlin
package com.rickeal.liquidharness.core.agent.tool

/**
 * 工具执行环境。所有 Android 侧能力从这里注入，便于 JVM 测试。
 * 照搬旧仓 Tool.kt:11-19，去掉 `agentMemory`（改为独立端口注入到工具实现）。
 */
data class ToolContext(
    val sandboxDir: java.io.File,
    val appContext: android.content.Context,
    val clipboard: android.content.ClipboardManager? = null,
    val nowMillis: () -> Long = { System.currentTimeMillis() },
)

interface Tool {
    val spec: ToolSpec
    suspend fun invoke(argumentsJson: String): ToolResult
}

/**
 * 参数级审批。实现必须 **fail-closed**：解析失败返回 true。
 * 纯函数约定：不得有状态、不得做 IO。（照搬旧仓 Tool.kt:33-35）
 */
interface ParamGatedTool : Tool {
    fun requiresConfirmationFor(argumentsJson: String): Boolean
}

/** 本次调用的效果声明。fail-closed：解析失败返回 WRITE。（照搬旧仓 Tool.kt:49-51） */
interface EffectAwareTool : Tool {
    fun effectFor(argumentsJson: String): ToolEffect
}
```

---

## 三、Agent 事件（UI 层契约）

```kotlin
package com.rickeal.liquidharness.core.model

enum class TerminationReason {
    ModelStopped,   // 模型自己停了 —— 正常
    MaxRounds,      // 轮次耗尽
    BreakerTripped, // Harness 主动熔断（墙钟/token/振荡/同参/失败连击/空输出/流式循环）
    GateRejected,   // 引擎闸门拒斥，本次 run 一次都没开始
    Interrupted,    // 宿主/进程中断（**不写入 journal**，「无 settled 行」就是它的判据）
}

/**
 * UI 层事件。与引擎层 GenerationEvent 是两套：
 * AgentEvent 知道「轮次、审批、工具执行」，GenerationEvent 只知道「模型吐了什么」。
 * 旧仓只有一套（GenerationChunk 直接进 AgentRunner），导致引擎细节泄漏到 UI。
 */
sealed interface AgentEvent {
    data class RoundStarted(val round: Int, val maxRounds: Int) : AgentEvent

    data class TextDelta(val text: String) : AgentEvent

    /** 思考通道。**与正文分道**，UI 渲染为独立折叠块。 */
    data class ReasoningDelta(val text: String) : AgentEvent

    data class ToolCallStarted(val call: ToolCall, val spec: ToolSpec) : AgentEvent
    data class ToolResultReceived(val result: ToolResult) : AgentEvent
    data class ToolSkipped(val call: ToolCall, val reason: String) : AgentEvent

    data class MessageCommitted(val message: ChatMessage) : AgentEvent

    /**
     * 用量。**新仓新增**（旧仓没有）：API 路径下 token 等于钱，
     * 且 provider 回报的 usage 是权威值（旧仓 litertlm 从不回报 usage，
     * `accumulator.usage` 是自算的）。
     */
    data class UsageUpdated(val prompt: Int, val completion: Int, val cachedPrompt: Int) : AgentEvent

    /** 技能被加载。**新仓新增**：UI 要显示「本回答用了哪个技能」，否则技能是隐形的。 */
    data class SkillLoaded(val name: String) : AgentEvent

    data class Finished(
        val text: String,
        val rounds: Int,
        val usage: TokenUsage?,
        val terminatedBy: TerminationReason = TerminationReason.ModelStopped,
        val report: BottleneckReport? = null,
    ) : AgentEvent

    data class Failed(
        val message: String,
        val cause: Throwable? = null,
        val report: BottleneckReport? = null,
        val terminatedBy: TerminationReason? = null,
    ) : AgentEvent

    data class Cancelled(val partialText: String) : AgentEvent

    /** 同一 round 内重试（网络重试 / 引擎重建）。UI 必须清空流式缓冲。 */
    data class Retrying(val reason: String) : AgentEvent

    /**
     * 主循环丢弃了本轮已流出的文本（重复截断 / 空输出 nudge）。
     * 与 Retrying 的分工见旧仓 AgentEvents.kt:103-120（那段 KDoc 是踩过 P1 写的，照搬）。
     */
    data class StreamReset(val reason: String) : AgentEvent

    data class ApprovalRequested(val call: ToolCall, val spec: ToolSpec) : AgentEvent
    data class PlanUpdated(val steps: List<PlanStep>) : AgentEvent

    /**
     * MCP 工具清单刷新结果。**新仓新增**：MCP 是远端依赖，
     * 「连不上」必须是一个可见事件而不是静默少几个工具。
     */
    data class ToolSourceUpdated(val sourceId: String, val ok: Boolean, val toolCount: Int, val message: String?) : AgentEvent
}
```

---

## 四、Skill 与 MCP

```kotlin
package com.rickeal.liquidharness.core.model

/**
 * SKILL.md 的 frontmatter 解析结果。
 *
 * 🔴 **frontmatter 必须是扁平 key-value + 字符串列表**，不支持嵌套、不支持多行块标量。
 * 理由：硬约束禁一切第三方库，手写 YAML 解析器只覆盖这个子集。
 * 违反子集的 SKILL.md 在导入时被判为非法并给出具体行号（不是静默忽略）。
 */
@Serializable
data class SkillManifest(
    /** 必填，`[a-z0-9-]{1,64}`，且必须等于目录名（防「同名不同目录」的歧义）。 */
    val name: String = "",
    /** 必填，≤ 500 字符，**单行**。一级披露进 system 的就是这一句。 */
    val description: String = "",
    val version: String? = null,
    val license: String? = null,
    /**
     * 可选。语义是 **交集收窄**，不是新增工具。
     * 空集 = 不限制（沿用当前工具面）；非空 = 本轮工具面取交集。
     * 永远不放宽（对齐 AiCapabilityMode 的「只收紧不放宽」）。
     */
    val allowedTools: Set<String> = emptySet(),
    /** 正文起止行号，用于导入校验报错定位。 */
    val bodyStartLine: Int = 0,
)

enum class SkillSourceKind { BUILTIN, USER_IMPORTED }

data class SkillEntry(
    val manifest: SkillManifest,
    val source: SkillSourceKind,
    /** assets 内路径或 filesDir 绝对路径。 */
    val rootPath: String,
    val enabled: Boolean = true,
    val lastUsedAtMillis: Long = 0L,
)

/** 二级披露的正文。 */
data class SkillBody(val name: String, val markdown: String, val resources: List<String>)
```

```kotlin
package com.rickeal.liquidharness.core.model

/**
 * 一个 MCP server 的配置。
 *
 * ⚠️ `headers` 里可能含凭据（如 `Authorization`）。两条纪律：
 * 1. 本类可以被序列化，但**序列化前必须过 SecretRedactor 检查**：
 *    值命中密钥正则的，改为 `hasSecretHeaders: Set<String>` 记录「哪个 header 有值」，
 *    真值进 Keystore（与 API Key 同一套）。
 * 2. 展示时永远只显示 header 名，不显示值。
 */
@Serializable
data class McpServerConfig(
    val id: String = newId(),
    /** 别名，用于工具命名空间 `mcp__<alias>__<tool>`。`[a-z0-9_]{1,20}`。 */
    val alias: String = "",
    val displayName: String = "",
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val hasSecretHeaders: Set<String> = emptySet(),
    val enabled: Boolean = true,
    /** 允许指向 RFC1918 / loopback。默认 false。 */
    val allowPrivateNetwork: Boolean = false,
    /** 协商结果，持久化以免每次重探。null = 未协商。 */
    val negotiatedEra: McpEra? = null,
    val lastConnectedAtMillis: Long = 0L,
    val lastError: String? = null,
)

@Serializable
enum class McpEra {
    /** 2026-07-28：无状态，无 initialize，无 session id，入口是 server/discover。 */
    STATELESS_2026_07_28,
    /** 2025-11-25：initialize 握手 + Mcp-Session-Id。 */
    LEGACY_2025_11_25,
}

/** tools/list 的缓存。落盘，TTL 24h。 */
@Serializable
data class McpToolCatalog(
    val serverId: String = "",
    val era: McpEra = McpEra.LEGACY_2025_11_25,
    val fetchedAtMillis: Long = 0L,
    val tools: List<McpToolDescriptor> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class McpToolDescriptor(
    val name: String = "",
    /** 远端原文。**不可信文本**：截断到 300 字符、剥控制字符后才进 ToolSpec。 */
    val description: String = "",
    /** 远端给的 JSON Schema 原文。转 ToolParameter 时只取顶层 properties/required。 */
    val inputSchemaJson: String = "{}",
)
```

```kotlin
package com.rickeal.liquidharness.core.agent.approval

enum class ToolApprovalDecision { APPROVED, DENIED }

/**
 * 审批请求。**加 `digest`**（旧仓只有 call + spec）：
 * 审批缓存按参数摘要键控，宿主点「本会话不再询问」时要把摘要一起写进缓存，
 * 否则宿主得自己再算一遍（旧仓就是这样，`ToolApprovalCache.argsDigest` 被两处调用）。
 */
data class ApprovalRequest(
    val call: ToolCall,
    val spec: ToolSpec,
    val argsDigest: String,
    val capabilityMode: String,
    val conversationId: String?,
    /** 展示用：脱敏后的参数摘要（不含密钥原文）。 */
    val displaySummary: String,
)

/** 审批通道。null = 无人在回路 ⇒ 需审批的工具一律拒绝（fail-closed）。 */
interface ToolApprovalHandler {
    suspend fun requestApproval(request: ApprovalRequest): ApprovalDecision
}

sealed interface ApprovalDecision {
    data object Approved : ApprovalDecision
    data object Denied : ApprovalDecision
    /** 允许并且「本会话相同调用不再询问」。 */
    data object ApprovedForSession : ApprovalDecision
    data class DeniedWithReason(val reason: String) : ApprovalDecision
}
```

```kotlin
package com.rickeal.liquidharness.core.agent.policy

/**
 * 一次 run 的策略。全部带默认值（旧仓纪律：重定成本低，但**每个数字都要重新取证**）。
 * 「旧仓值」列在 KDoc 里，便于审计「为什么改」。
 */
@Serializable
data class AgentPolicy(
    /** 旧仓 8。远程侧单轮更快，但工具链路更长 ⇒ 提到 12。【推断，W2 实测校准】 */
    val maxRounds: Int = 12,
    /** 旧仓 15s（端侧工具全是本地 IO）。MCP 工具是远端调用 ⇒ 60s。 */
    val toolTimeoutMillis: Long = 60_000L,
    /** 旧仓统一 4000。远程侧按 effect 分开：读是工作材料，写是回执。 */
    val maxReadToolOutputChars: Int = 16_000,
    val maxWriteToolOutputChars: Int = 2_000,
    val autoApproveDangerous: Boolean = false,
    val compressContext: Boolean = true,
    /** 旧仓 0.75，沿用。但分母换了（见 maxContextTokens）。 */
    val compressThreshold: Float = 0.75f,
    /**
     * 新仓新增：本应用愿意发出的上下文上限。
     * 没有它，1M 窗口的模型会真的发 75 万 token 出去 —— 一次几美元。
     */
    val maxContextTokens: Int = 32_000,
    /** 旧仓 WALL_CLOCK_SOFT_MILLIS = 180s。远程侧一轮更短，但链路更长 ⇒ 沿用。 */
    val wallClockSoftMillis: Long = 180_000L,
    /** 旧仓 WALL_CLOCK_HARD_MILLIS = 300s。远程侧重试会拉长 ⇒ 600s。 */
    val wallClockHardMillis: Long = 600_000L,
    /** 单轮生成超时（冻结感知）。旧仓 generationTimeoutMillis = 300s（端侧 2s/token）。 */
    val generationTimeoutMillis: Long = 120_000L,
    /**
     * 新仓新增：单 run 累计 prompt token 硬上限。
     * API 路径下无上限的 agent loop = 无上限的账单。超限 ⇒ BreakerTripped。
     */
    val maxRunPromptTokens: Int = 400_000,
    /** 新仓新增：子代理递归深度上限。旧仓靠「子 run 继承父 run 墙钟」间接约束。 */
    val maxSubagentDepth: Int = 2,
    /** 新仓新增：工具总数超过此值时自动切 ON_DEMAND 披露（见 A5）。 */
    val disclosureThreshold: Int = 24,
)
```

---

## 五、迁移触发矩阵（A5 的完整版）

**这张表是本仓最重要的纪律文件。** 报告 §二 的原话：「搬过去的判据空转冒充覆盖 —— 空转的判据比没有判据更危险」。

读法：`触发条件` 必须写成**可判定的谓词**；`空命中语义` 说明「条件不成立时是什么状态」—— 不允许留白，因为留白就是「看起来有保护其实没有」。

| # | 机制 | 裁决 | 触发条件（可判定） | 空命中语义 | 验证方式 |
|---|---|---|---|---|---|
| 1 | `Tool` / `ToolSpec` / `ToolEffect` / `ParamGatedTool` / `EffectAwareTool` | 原样搬 | 每次工具调用 | ⛔ 不适用（无条件执行） | 单测 + 真机 |
| 2 | `ToolArgsValidator` | 原样搬 | 模型产出参数与 spec 不符 | 参数合法时零日志 | 单测（4 类坏参数） |
| 3 | `WindowContextCompressor` | 原样搬 | `estTokens(working) > budget` | 未超预算 ⇒ 原样返回（无损） | 单测（3 种切点） |
| 4 | `sanitizeForProvider` | 原样搬 | 每次发请求前 | 已配对 ⇒ 返回同一对象（`changed=false`） | 单测（3 种不配对） |
| 5 | `TokenEstimator` | 原样搬 | 每次预算判断 | 空文本返 0 | 单测（CJK/英文/混排） |
| 6 | `DeltaTracker` | 原样搬 | 每个 content 帧 | 首帧不判定（`cumulative=null`） | 单测（累积式/增量式/重复帧） |
| 7 | `ToolApprovalCache` | 原样搬 | 用户点「本会话相同调用不再询问」 | 未授权 ⇒ `peek` 返 null ⇒ 走人在回路 | 单测（TTL/档位隔离/拒绝不缓存） |
| 8 | `ToolApprovalHandler` | 原样搬 | `spec.dangerous ∨ requiresConfirmation ∨ effectFor==WRITE∧READ_ONLY ∧ ParamGated` | `handler==null` ⇒ **拒绝** | 单测 |
| 9 | `AgentRunJournal` | 原样搬 | 每次 run 开始 / 每轮 / 每条消息 / settled | 写失败 ⇒ 吞异常 + warn，主流程不受影响 | 真机（杀进程） |
| 10 | `RunTokenLedger` | **改造后搬** | 发送侧每次记账 + 收到 usage；门控：`cumulativePrompt > maxRunPromptTokens` | usage 缺失 ⇒ 用 `TokenEstimator` 估算并标注 `estimated=true` | 单测 + 真机 |
| 11 | 墙钟 SOFT (180s) | 改造后搬 | `runnableElapsed > soft && !tripped` | 未到 ⇒ 不 trip，无日志 | 单测（冻结时钟） |
| 12 | 墙钟 HARD (600s) | 改造后搬 | `runnableElapsed > hard` | 未到 ⇒ 继续 | 单测 |
| 13 | 同参重复调用 | 原样搬 | 同一 `(name, canonicalArgs)` 第 N 次 | 首次 ⇒ 放行 | 单测 |
| 14 | 工具失败连击 (4) | 原样搬 | 同一工具连续失败 4 次（含换参） | <4 ⇒ 回灌错误让模型重试 | 单测 |
| 15 | 振荡检测 | 原样搬 | 工具序列出现 A→B→A→B 循环 | 无循环 ⇒ 零开销 | 单测 |
| 16 | 空输出 nudge (3) | 原样搬 | 连续 3 轮零工具调用且零文本 | 有文本 ⇒ 正常收尾 | 单测 |
| 17 | `StreamRepetitionDetector`（句级） | 改造后搬 | 同一句连续出现 2 次 | 无重复 ⇒ 零截断 | 单测 |
| 18 | `StreamRepetitionDetector`（prompt-echo 指纹） | ⛔ **删** | 无（端侧 0.5B–4B 病理） | — | — |
| 19 | `FreezeClock` | 改造后搬 | 灭屏 ≥ 1s（`ScreenStateMonitor`） | 不灭屏 ⇒ 与 `System.nanoTime()` 等价 | ⚠️ **W3 真机待验证**（本地路径已验证，远程路径未验证） |
| 20 | `freezeAwareTimeout` | 改造后搬 | 单轮生成超 `generationTimeoutMillis` 的可运行时长 | 未超 ⇒ 正常 | ⚠️ 同上 |
| 21 | `memory`（write/read/search/delete） | 原样搬 | 模型调用记忆工具 | 未配置 ⇒ 工具不注册（提示词段随之消失） | 单测 + 真机 |
| 22 | `plan`（set/update） | 原样搬 | 模型调用计划工具 | 未配置 ⇒ 不注册 | 单测 |
| 23 | 子代理 | 改造后搬 | 模型调 `ask_actor`；`depth < maxSubagentDepth` | 超深度 ⇒ 拒绝并回灌原因 | 单测 |
| 24 | `ToolDisclosure`（search/call 元工具） | 改造后搬 | `enabledTools().size > disclosureThreshold` **或** 用户手选 | 未超阈值且未手选 ⇒ FULL（清单直进提示词） | 单测 + 真机 |
| 25 | `TextToolProtocol` | ⛔ **删** | — | — | — |
| 26 | `RunThermalGate` / `ThermalGovernor` | ⛔ **删** | 无发热源 | — | — |
| 27 | `history_v2` 四文件 | ⛔ **删** | 旧仓零生产调用点 | — | — |
| 28 | 端侧 `core-model` 资产 | ⛔ **删** | — | — | — |
| 29 | `AiCapabilityMode`（能力档位） | 原样搬 | 用户设 READ_ONLY 且工具 `effect==WRITE` | 默认 `WORKSPACE_WRITE` ⇒ 与无档位行为一致 | 单测 |
| 30 | `ConversationCache` / `ConversationRepository` | 原样搬 | 会话切换 | 无缓存 ⇒ 直接读盘 | 单测 |
| 31 | 诊断卡 `BottleneckReport` | 原样搬 | 任一熔断路径 | 正常结束 ⇒ null | 单测 |
| 32 | `StreamAccumulator` | 原样搬 | 每个流 | 空流 ⇒ 空文本 | 单测 |

### 三条「不留白」的具体写法

**#10 `RunTokenLedger` 的门控**（这是唯一一个「旧仓判死、新仓复活」的机制，必须写清）：
```
旧仓 W73-01 终裁原文：「账本只做读侧投影，门控口径不接」。
新仓改判的依据：API 路径下 provider 回报的 usage 是权威值且免费，
而 TokenEstimator 的 CJK 启发式在代码/中英混排上系统性偏差（旧仓 KDoc 自陈
「折中的方向是系统性低估中文 2~3 倍」）⇒ 「估算失准事故」这个重启前提由构造满足。

门控判据（三处，缺一不可）：
  a) 压缩触发：estTokens(working) > budget  ← 仍用估算（发送前必须判断）
  b) 硬上限：ledger.cumulativePrompt > policy.maxRunPromptTokens ⇒ BreakerTripped
  c) 校准：每轮把 provider 回报的 usage 与估算值比对，
     |估算 - 实际| / 实际 > 0.5 ⇒ 记一条 warn（用于日后校准 CHARS_PER_TOKEN）
```

**#19/#20 `FreezeClock`**（唯一一个「已搬但未在目标路径验证」的机制）：
```
状态：⚠️ 未验证（API 路径）。
已知成立的：本地路径真机验证过（旧仓 W78-12 / W79 / W81 三次）。
未验证的：远程路径下「灭屏 → 冻结 → 解冻 → 流恢复/断开」的完整链路。
W3 判据 4 是本机制的**唯一**验收门：
  灭屏 60s 后解锁 ⇒ run 未被误熔断 且 日志有冻结时长。
若判据 4 失败 ⇒ 本机制降级为「只服务墙钟 SOFT」，HARD 改回普通单调时钟，
并在文档里把这个降级如实申报（不允许悄悄留着不生效的代码）。
```

**#24 `ToolDisclosure` 的自动触发**：
```
旧仓：用户手选 FULL / ON_DEMAND（AgentRequest.disclosureMode）。
新仓：自动 + 手选兜底。自动判据 = enabledTools().size > policy.disclosureThreshold (24)。
  理由：MCP 接入后工具数从 6 涨到 30~150，而主流模型在 20~30 个工具后
        选择准确率明显下降【推断，W5 用 20 条题面实测校准阈值】。
空命中语义：工具数 ≤ 24 且用户未手选 ⇒ FULL（与旧仓默认行为一致）。
```

---

## 六、契约变更纪律

1. **`@Serializable` 类加字段必须带默认值**，否则旧 JSON 反序列化失败。
2. **改字段语义 = 改类名或加新字段**，不原地改。旧仓的 `ThinkingParamStyle` → 新仓 `ReasoningStyle` 就是这个处理（同名不同义会在排查时误判，见 §一 注释）。
3. **删字段前先 `grep` 全仓**（含 test 源集与脚本），旧仓纪律：「改机制/改数字前，全仓 grep 同关键词/同数字的所有形态再改」。
4. **契约层（`core-model`）的任何类都不许出现 `apiKey` / `secret` / `password` 字段名** —— 守卫 G17 机械拦。
5. **每个契约都要有一个「谁消费它」的注释**。没有消费者的字段不写（旧仓 `AgentRequest.deadlineNanos` 就是靠守卫 #13 的 `@wire-owner` 标记才没变成死字段）。
