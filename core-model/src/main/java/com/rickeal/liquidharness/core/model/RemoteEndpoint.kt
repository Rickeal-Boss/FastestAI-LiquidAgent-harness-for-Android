package com.rickeal.liquidharness.core.model

import kotlinx.serialization.Serializable

/**
 * 供应商传输族。**这是传输概念**，与 UI 上的「预设」是两件事。
 *
 * 为什么不是旧仓的六值 `RemotePreset`（OPENAI/DEEPSEEK/OLLAMA/VLLM/SILICONFLOW/CUSTOM）：
 * 那六个里有四个在线上是**同一个协议**（OpenAI Chat Completions），
 * 差异只有 baseUrl + 思考参数风格 + extraHeaders —— 那是配置，不是代码分支。
 * 做成枚举等于让每个新供应商都要改一次 enum 与所有 when 分支。
 *
 * 三族的**结构性**差异（不是配置差异）见 `docs/01-architecture.md` §3.2 表：
 * system 位置 / 工具声明形态 / 工具调用是否碎片 / 思考通道 / usage 位置 / SSE 事件名 / 结束标记。
 */
@Serializable
enum class ProviderKind {
    /** OpenAI / DeepSeek / Ollama / vLLM / SiliconFlow / 自定义。 */
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI,
}

/**
 * 只用于「新建端点时填默认值」的 UI 概念。**不参与任何传输逻辑。**
 *
 * 把它和 [ProviderKind] 分开，是因为旧仓 `RemoteEndpoint.preset` 一个字段同时承担
 * 两种语义：既决定 URL 拼接（传输），又决定设置页显示哪几个输入框（UI）。
 * 结果加一个供应商要动传输代码，改一次 UI 文案又要担心影响请求。
 */
@Serializable
enum class UiPreset {
    OPENAI, DEEPSEEK, OLLAMA_LOCAL, VLLM, SILICONFLOW, ANTHROPIC, GEMINI, CUSTOM;

    fun seed(): EndpointSeed = when (this) {
        OPENAI -> EndpointSeed(ProviderKind.OPENAI_COMPATIBLE, "https://api.openai.com/v1", 128_000, 16_384, true, true)
        DEEPSEEK -> EndpointSeed(ProviderKind.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1", 65_536, 8_192, true, true)
        OLLAMA_LOCAL -> EndpointSeed(ProviderKind.OPENAI_COMPATIBLE, "http://10.0.2.2:11434/v1", 32_768, 4_096, false, true)
        VLLM -> EndpointSeed(ProviderKind.OPENAI_COMPATIBLE, "http://10.0.2.2:8000/v1", 32_768, 4_096, false, true)
        SILICONFLOW -> EndpointSeed(ProviderKind.OPENAI_COMPATIBLE, "https://api.siliconflow.cn/v1", 32_768, 4_096, true, true)
        ANTHROPIC -> EndpointSeed(ProviderKind.ANTHROPIC, "https://api.anthropic.com", 200_000, 8_192, true, true)
        GEMINI -> EndpointSeed(ProviderKind.GEMINI, "https://generativelanguage.googleapis.com", 1_000_000, 8_192, true, true)
        CUSTOM -> EndpointSeed(ProviderKind.OPENAI_COMPATIBLE, "", 32_768, 4_096, false, false)
    }
}

/** 新建端点时的默认值载体。不持久化，只在设置页「选预设」那一刻使用。 */
data class EndpointSeed(
    val kind: ProviderKind,
    val baseUrl: String,
    val contextLength: Int,
    val maxOutputTokens: Int,
    val supportsReasoning: Boolean,
    val supportsTools: Boolean,
)

/**
 * 开启思考的线上形态。
 *
 * 旧仓叫 `ThinkingParamStyle`，语义一致；改名是因为「thinking」是端侧模型的概念
 * （litertlm 的 thinking channel），远程 API 的正式叫法是 reasoning。
 * 同名不同义会在排查时误判，所以按「改字段语义 = 改类名」的纪律处理。
 */
@Serializable
enum class ReasoningStyle {
    NONE,

    /** body: `enable_thinking = true`（Qwen3 / vLLM 部分）。 */
    ENABLE_THINKING_BOOL,

    /** body: `reasoning_effort = "low|medium|high"`（OpenAI o 系 / DeepSeek）。 */
    REASONING_EFFORT,

    /** body: `chat_template_kwargs = { enable_thinking: true }`（vLLM 另一派）。 */
    CHAT_TEMPLATE_KWARGS,

    /** Anthropic: `thinking = { type: "enabled", budget_tokens: N }`。W2 由 AnthropicAdapter 消费。 */
    ANTHROPIC_THINKING_BUDGET,

    /** Gemini: `generationConfig.thinkingConfig.includeThoughts = true`。W2 由 GeminiAdapter 消费。 */
    GEMINI_INCLUDE_THOUGHTS,

    /**
     * OpenRouter 统一 `reasoning` 对象（W8 新增）。
     *
     * 线上形态由 `thinkingMode` 决定：`{"effort": wire}` / `{"enabled": false}` / `{"exclude": true}`。
     * 🔴 dots 的 `supported_parameters` **无 `reasoning_effort`** ⇒ 现役 [REASONING_EFFORT]
     * 发的是网关不认的字段，本值是唯一正确的通道（主理人 `w8-provider-probe.md` 实测）。
     * `effort` 与 `max_tokens` **绝不共存**于同一 reasoning 对象。
     */
    OPENROUTER_REASONING_OBJECT,
}

/**
 * 一个远程端点。
 *
 * 🔴 **本类不含 apiKey 字段，且永远不许加。**
 * 旧仓存档规格里 `RemoteEndpoint.apiKey: String` 是一个 `@Serializable` 字段
 * ⇒ 它会被序列化进 DataStore 明文文件。新仓把密钥移到 `EndpointSecretStore`
 * （Keystore 信封），本类只留 [hasSecret] 布尔。守卫 G17 机械拦
 * 「@Serializable 类出现 apiKey/secret/password 字段」。
 *
 * 消费者：`core-engine`（URL / 请求体 / 能力声明）、`feature-settings`（编辑页）、
 * `core-data`（持久化）。
 */
@Serializable
data class RemoteEndpoint(
    val id: String = newId(),
    val name: String = "",
    val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val preset: UiPreset = UiPreset.CUSTOM,
    /** **不含** `/chat/completions` 等路径后缀，路径由 adapter 拼（00-brief A4）。 */
    val baseUrl: String = "",
    val modelId: String = "",
    val contextLength: Int = 32_768,
    val maxOutputTokens: Int = 4_096,
    /**
     * **实测**窗口（由真实运行的 400 学到）。`null` = 从未观测到，用 [contextLength]。
     *
     * ## 为什么它是 `Int?` 而不是又一个「能力声明」
     *
     * [contextLength] 是用户手填的声明值 —— 免费池上经常填大（写 1M、实际 256k），
     * 于是预算算得过大、请求必然 400。本字段只在端点**真的报过一次超窗**之后才有值，
     * 来源是错误体里的 `maximum context length is N`（`OpenAiCompatibleAdapter` 解析
     * → `GenerationEvent.Failed.contextLimitHint` → `AppContainer.recordContextLimit`）。
     *
     * 刻意**不做**「探窗口」请求：那要故意发一个超窗请求（1M ≈ 115 s + 一次计费），
     * 在免费池上还会撞 429。学习式写回是唯一可接受的通道。
     *
     * ## 纪律
     *
     * - 默认 `null` ⇒ 反序列化兼容（老 `endpoints.json` 里没有这个键也能读）。
     * - 字段名不含 `token` / `key` / `secret` / `password`，守卫 G17 不受影响。
     * - 消费点唯一：`hardContextBudgetTokens` 里的
     *   `min(verifiedContextLength ?: contextLength, policy.maxContextTokens)`。
     */
    val verifiedContextLength: Int? = null,
    val supportsTools: Boolean = true,
    val supportsReasoning: Boolean = false,
    val reasoningStyle: ReasoningStyle = ReasoningStyle.NONE,
    /**
     * 默认思考档位的**唯一真值源**（被 `OpenAiCompatibleAdapter.applyReasoningStyle` 消费）。
     *
     * 🔴 **禁止新增第三个 effort 字段**：`Generation.kt:80` 的 `SamplingParams.reasoningEffort`
     * 全仓零生产消费点（挂账 W9-06），`reasoningEffort` 只认本字段。档位 UI 值经
     * `normalizeEffort` 归一，[ReasoningEffort.EXTREME] 永不直接落进这里。
     */
    val reasoningEffort: String = "medium",
    /**
     * 输入模态集合。空集不合法（至少含 TEXT），默认 `setOf(TEXT)` = fail-closed。
     *
     * [supportsVision] 是它的派生值，**三处同改**：本字段 + `RemoteLlmEngine.capabilities()`
     * + `AppNavHost` 的图片入口闸门；任一处漏掉就是「声明了没消费者」的下一例。
     */
    val inputModalities: Set<Modality> = setOf(Modality.TEXT),
    /** 输出模态集合。音频输出本波恒不可达（NV 0 音频类 + `/v1/audio/speech` 404 + 设备仅 iflytek TTS），UI 不摆假入口。 */
    val outputModalities: Set<Modality> = setOf(Modality.TEXT),
    /**
     * 用户思考意图。默认 [ThinkingMode.FOLLOW_PROVIDER]（不发任何开关）保持现役行为、老用户零回归。
     *
     * 注意 [ThinkingMode.OFF] 只在 [allowDisableThinking] 为真且 [reasoningStyle] 是
     * [ReasoningStyle.OPENROUTER_REASONING_OBJECT] 时可达，否则由 adapter 降级
     * [ThinkingMode.HIDE_ONLY]；降级由 `hardOffDowngraded` 派生信号上屏，不落结构化日志。
     */
    val thinkingMode: ThinkingMode = ThinkingMode.FOLLOW_PROVIDER,
    /** 端点声明「允许真关思考」。来自 OR `reasoning.mandatory` 取反；dots / north 实测均为 `mandatory:false`。 */
    val allowDisableThinking: Boolean = false,
    /**
     * 端点声明的支持档位。**空集 = 未声明**（不是「全支持」），[normalizeEffort] 按已实测四档处理。
     */
    val supportedEfforts: Set<ReasoningEffort> = emptySet(),
    /** 自定义协议（族内覆写少量 body 键）。键白名单与凭据语义由 `validateAdvanced` 钉死。 */
    val customProtocol: CustomProtocol = CustomProtocol(),
    /**
     * 强制 JSON 模式（请求侧发 `response_format: {"type": "json_object"}`）。
     *
     * 🔴 与「校验返回文本」是两件事：校验落在 `core-model/JsonFormatCheck.kt`（气泡动作键），
     * 本字段只管**请求体**。north 的 `supported_parameters` 无 `response_format` ⇒ 对它必须显式降级标 ⚠️。
     */
    val jsonMode: Boolean = false,
    val extraHeaders: Map<String, String> = emptyMap(),
    /** 允许指向 RFC1918 / loopback（本地 Ollama / 局域网 vLLM）。默认 false = fail-closed。 */
    val allowPrivateNetwork: Boolean = false,
    val hasSecret: Boolean = false,
    val addedAtMillis: Long = System.currentTimeMillis(),
) {
    /**
     * 派生，不持久化。本地 / 模拟器上的 Ollama 无需 key。
     *
     * 注意这是**启发式**，不是安全判据：它只用于「要不要拦下空 key」，
     * 真正的网络边界是 `core-net` 的 `UrlGuard`。
     */
    val requiresApiKey: Boolean
        get() = !(baseUrl.contains("11434") || baseUrl.contains("127.0.0.1") || baseUrl.contains("10.0.2.2"))

    /**
     * 派生，不持久化：图片输入能力 = 输入模态含 IMAGE。
     *
     * 默认 `false`（fail-closed）会让既有端点的图片入口消失 ⇒ UI 侧必须保留**禁用态入口 + 原因提示**，
     * 不做静默隐藏（W7 真机视觉 ✅ 的现状不能回归）。
     */
    val supportsVision: Boolean
        get() = Modality.IMAGE in inputModalities

    /** 派生：音频输出能力。本波恒 `false`（无供应商 + 无 TTS 通道），UI 已过滤该假入口。 */
    val supportsAudioOutput: Boolean
        get() = Modality.AUDIO in outputModalities

    /**
     * 派生：把能力位与用户意图合流。端点不能思考 ⇒ 强制 [ThinkingMode.OFF] 的**语义**无效，
     * 回落 [ThinkingMode.FOLLOW_PROVIDER]（不发开关，让服务端自己决定）。
     */
    val effectiveThinkingMode: ThinkingMode
        get() = if (!supportsReasoning && thinkingMode != ThinkingMode.FOLLOW_PROVIDER) {
            ThinkingMode.FOLLOW_PROVIDER
        } else {
            thinkingMode
        }

    /**
     * 派生：「允许真关思考」是否**可达**。三条件齐备才为真，否则 `OFF` 意图会被 adapter
     * 降级成 [ThinkingMode.HIDE_ONLY]（不静默发无效字段）；**不落 `reason` 结构化日志**，
     * 降级事实由 `hardOffDowngraded` 上屏透出（W9-00，日志出口归 W9-07）。
     */
    val canHardOff: Boolean
        get() = supportsReasoning &&
            allowDisableThinking &&
            reasoningStyle == ReasoningStyle.OPENROUTER_REASONING_OBJECT

    /**
     * 派生：归一后的思考档位 wire 值（可能带降级）。`null` = 本 style 不发 effort 字段。
     * 消费点唯一：`ReasoningBody` / `OpenAiCompatibleAdapter.applyReasoningStyle`。
     */
    fun resolvedEffort(): ResolvedEffort = normalizeEffort(
        desired = reasoningEffort,
        supported = supportedEfforts,
        style = reasoningStyle,
        maxOut = maxOutputTokens,
    )
}
