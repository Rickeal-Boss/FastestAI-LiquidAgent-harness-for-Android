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
    val supportsTools: Boolean = true,
    val supportsReasoning: Boolean = false,
    val reasoningStyle: ReasoningStyle = ReasoningStyle.NONE,
    val reasoningEffort: String = "medium",
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
}
