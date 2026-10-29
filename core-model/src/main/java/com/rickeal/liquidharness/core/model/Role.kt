package com.rickeal.liquidharness.core.model

import kotlinx.serialization.Serializable

/**
 * 会话消息角色。
 *
 * 刻意**不带**线上名字（`"assistant"` / `"tool"`）：线上名字是供应商概念，
 * 三家不一致（Anthropic 用 `assistant` 但工具结果是 `user` + `tool_result` block，
 * Gemini 用 `model` 且工具结果是 `functionResponse` part）。
 * 映射由 `core-engine` 的 `ProviderAdapter` 负责，契约层不掺传输细节。
 *
 * 消费者：`ChatMessage`、`core-engine` 的请求体构造、`feature-chat` 的气泡渲染。
 */
@Serializable
enum class Role {
    /** 系统提示词。每 run 渲染一次，run 内字节不变（00-brief A8）。 */
    SYSTEM,

    /** 用户输入，以及以 user 角色注入的技能正文 / 提醒（00-brief A6）。 */
    USER,

    /** 模型输出（含工具调用声明）。 */
    MODEL,

    /** 工具执行结果回灌。 */
    TOOL,
}
