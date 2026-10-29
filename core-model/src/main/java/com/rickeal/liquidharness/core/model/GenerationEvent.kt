package com.rickeal.liquidharness.core.model

import kotlinx.serialization.Serializable

/**
 * 引擎层事件。
 *
 * 与 UI 层的 [AgentEvent] 是**两套**：这一层只说「模型吐了什么」，
 * 不知道轮次、审批、工具执行的存在。旧仓只有一套（`GenerationChunk` 直接进
 * `AgentRunner`），导致引擎细节泄漏到 UI —— 拆开是为了让内核纯 JVM 可测。
 *
 * 消费者：`core-engine`（生产）、`core-agent`（消费）、`FakeEngine`（单测）。
 */
sealed interface GenerationEvent {
    data class TextDelta(val text: String) : GenerationEvent

    /** 思考通道。**与正文分道**，UI 渲染为独立折叠块。 */
    data class ReasoningDelta(val text: String) : GenerationEvent

    /**
     * 工具调用**碎片**。
     *
     * 契约是「增量」：`argumentsFragment` 是新增的一段，不是累积值。
     * [id] 通常只在首片出现，[name] 可能分片到达；装配由
     * `StreamAccumulator` 的 `ToolCallFragmentTracker` 负责。
     */
    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argumentsFragment: String,
    ) : GenerationEvent

    data class Usage(val prompt: Int, val completion: Int, val cachedPrompt: Int) : GenerationEvent

    data class Finished(val reason: FinishReason) : GenerationEvent

    /**
     * [retryable] 是 adapter / engine 的判定，**不是**内核的重试判据：
     * 内核还要叠「是否已 emit 过 TextDelta」与重试次数。
     * 两者分开是因为「可重试」是协议属性，「该不该重试」是运行状态属性。
     */
    data class Failed(
        val kind: EngineErrorKind,
        val message: String,
        val retryable: Boolean,
    ) : GenerationEvent
}

/**
 * 引擎错误分类。分类的唯一目的：决定「重试 / 改配置 / 压缩后重发 / 直接报错」。
 *
 * 文案纪律（沿用旧仓）：`message` 只陈述「发生了什么」，不提内部词（引擎 / 会话 / native / SSE）。
 * 所有 message 都是英文常量，由 UI 层映射为本地化文案（守卫 N3 基线 0）。
 */
enum class EngineErrorKind {
    /** 401/403 —— 不可重试，需用户改配置。 */
    AUTH,

    /** 429 —— 可重试（遵守 `Retry-After`）。 */
    RATE_LIMIT,

    /** 5xx —— 可重试。 */
    SERVER,

    /** 连接失败 / 首字节前超时 —— 可重试。 */
    NETWORK,

    /** SSE 畸形 / JSON 解析失败 —— 不可重试。 */
    PROTOCOL,

    /** 400 且文案命中窗口超限 —— 不可重试，触发压缩后重发一次。 */
    CONTEXT_OVERFLOW,

    /**
     * 本地配置问题（URL 被 `UrlGuard` 拒绝 / 缺 API Key / baseUrl 为空）。
     *
     * ⚠️ 相对 `docs/01-architecture.md` §3.1 的枚举**新增**。原枚举七个值里没有
     * 「发请求之前就被本地拦下」这一类，最接近的 `PROTOCOL`（SSE 畸形）语义相反：
     * PROTOCOL 是「服务端回了看不懂的东西」，CONFIG 是「我们根本没发出去」。
     * 混用会让排查时把配置错误当成服务端故障。
     */
    CONFIG,

    UNKNOWN,
}

/**
 * 一次生成请求。
 *
 * [endpoint] 里**没有密钥**：密钥由 `SecretProvider` 在发请求那一刻现取
 * （00-brief A7）。这不是洁癖 —— 请求对象会进 journal、进日志、进内存 dump，
 * 带着密钥就等于多三处泄漏面。
 */
@Serializable
data class GenerationRequest(
    val messages: List<ChatMessage> = emptyList(),
    val tools: List<ToolSpec> = emptyList(),
    val sampling: SamplingParams = SamplingParams(),
    val endpoint: RemoteEndpoint = RemoteEndpoint(),
    /**
     * 幂等键：同一 run 同一轮的重试共用一个值，便于服务端日志关联。
     * 不复用为缓存键 —— 远程端点没有「同键返回缓存」的契约。
     */
    val idempotencyKey: String = "",
)

/**
 * 引擎能力声明。由 adapter 的静态声明 + 端点配置派生，**不发网络请求**。
 *
 * 为什么不是旧仓的 `EngineCapabilities`：旧仓那份带
 * `supportedBackends` / `supportsSpeculativeDecoding` / `isConcurrentGenerationBusy`，
 * 全是进程内 native 引擎语义，远程实现只能写 no-op ⇒ 空转判据（00-brief A4 理由 1）。
 */
data class EngineCapabilities(
    val supportsTools: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsVision: Boolean = false,
    /** 远程恒 true（无原生通道就不支持工具）。 */
    val nativeToolChannel: Boolean = true,
    val maxContextTokens: Int = 32_768,
    val maxOutputTokens: Int = 4_096,
    val label: String = "",
)
