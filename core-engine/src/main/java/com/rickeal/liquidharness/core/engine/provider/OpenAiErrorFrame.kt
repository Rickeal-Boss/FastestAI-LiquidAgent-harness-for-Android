package com.rickeal.liquidharness.core.engine.provider

import com.rickeal.liquidharness.core.model.AgentJson
import com.rickeal.liquidharness.core.model.EngineErrorKind
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.net.SseEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * OpenAI 兼容族的**流内错误帧** → [GenerationEvent.Failed]（W24-02）。
 *
 * ## 为什么单独成文件
 *
 * `OpenAiCompatibleAdapter.kt` 已 456/500 行（G10 单文件上限），映射表本身是一个独立的
 * 判定：输入是「一帧 SSE」，输出是「要不要终止本次尝试、终止成哪一类」。放进 adapter
 * 只会把「怎么拼请求」和「怎么判错误帧」揉在同一个文件里。
 *
 * ## 形态（本族实测 / 文献两态）
 *
 * 1. `event: error` + `data: {"error":{...}}`（部分网关、以及 OpenAI 的 error 事件）；
 * 2. 无 `event:` 行，`data: {"error":{"type":"...","code":"...","message":"..."}}`。
 *
 * 判据是**结构化**的：必须 `event == "error"` 或 JSON 顶层有 `error` 键，否则返回 null。
 * 不能拿「payload 里含 error 字样」判 —— 模型的正常正文里也会出现这个词（fail-closed 的方向
 * 是「宁可漏判一帧错误、也不要把正常帧当错误终止」，漏判的后果是走已有的截断/超时路径）。
 *
 * ## 分类（决定重试与否）
 *
 * | 命中 | kind | retryable | 处置 |
 * |---|---|---|---|
 * | `rate` / `429` / `quota` | [EngineErrorKind.RATE_LIMIT] | true | 内核按退避重试（W40：已吐字 ⇒ 整段回滚后重试） |
 * | `overload` / `server` / `5xx` / `internal` | [EngineErrorKind.SERVER] | true | 同上 |
 * | `context` + 超限词 | [EngineErrorKind.CONTEXT_OVERFLOW] | **false** | 内核压缩后重发 |
 * | 其余 | [EngineErrorKind.PROTOCOL] | **false** | fail-closed，直接失败 |
 *
 * 判定顺序固定「超窗 → 限流 → 服务端 → 兜底」：超窗文案里也含 `limit`（`maximum context
 * length`），先判限流会把它误归成可重试，结果是「同长度请求重发两次、两次都超窗」。
 *
 * ## W40：结构化字段优先 + 去掉 payload 全文
 *
 * 旧版把 `payload`（整帧 JSON 原文）拼进 haystack ⇒ 限流文案 `rate limit: too many tokens`
 * 同时含 `token` 与 `limit`，会先命中超窗词表被误判 `CONTEXT_OVERFLOW`（不可重试）——
 * 把「零产出时引擎内可重试」也一并关掉。现版只看 `type`/`code`/`message`/顶层错误字符串
 * 四个字段；`type`/`code` 是供应商的结构化自声明（如 OpenAI 的 `rate_limit_error`），
 * 命中限流/服务端词表**优先**于 message 词表兜底——结构化字段不会混入正文措辞。
 *
 * @param contextLimitHint 从错误体里取真实窗口的端口（由 adapter 注入，复用同一套正则，
 *   避免这里长出第二把尺子）。
 */
internal fun decodeOpenAiErrorFrame(
    event: SseEvent,
    contextLimitHint: (String) -> Int?,
): GenerationEvent.Failed? {
    val payload = event.data.trim()
    val obj = AgentJson.decodeObjectOrNull(payload)
    val errorValue = obj?.get("error")
    val isErrorEvent = event.event.equals("error", ignoreCase = true)
    if (!isErrorEvent && errorValue == null) return null

    val errorObj = errorValue as? JsonObject
    val errorText = (errorValue as? JsonPrimitive)?.takeIf { it.isString }?.content
    // W40：结构化字段优先。type/code 是供应商自声明（rate_limit_error / server_error），
    // 不含正文措辞 ⇒ 命中限流/服务端词表时**先于** message 词表兜底裁决。
    // haystack 只拼四个错误字段、**不含 payload 全文**（含进去会让限流文案里的
    // token/limit 撞上超窗词表，见类 KDoc「W40」段）。
    val typeAndCode = listOfNotNull(
        AgentJson.stringOrNull(errorObj, "type"),
        AgentJson.stringOrNull(errorObj, "code"),
    ).joinToString(" ").lowercase()
    val haystack = listOfNotNull(
        AgentJson.stringOrNull(errorObj, "type"),
        AgentJson.stringOrNull(errorObj, "code"),
        AgentJson.stringOrNull(errorObj, "message"),
        errorText,
    ).joinToString(" ").lowercase()

    val message = AgentJson.stringOrNull(errorObj, "message")
        ?: errorText
        ?: "provider error frame"

    // 结构化优先：type/code 直判限流 / 服务端（不可被 message 措辞改判）。
    if (typeAndCode.contains("rate") || typeAndCode.contains("429") || typeAndCode.contains("quota")) {
        return GenerationEvent.Failed(EngineErrorKind.RATE_LIMIT, message, retryable = true)
    }
    if (typeAndCode.contains("overload") || typeAndCode.contains("server") ||
        typeAndCode.contains("5xx") || typeAndCode.contains("unavailable")
    ) {
        return GenerationEvent.Failed(EngineErrorKind.SERVER, message, retryable = true)
    }
    if (typeAndCode.contains("context")) {
        return GenerationEvent.Failed(
            EngineErrorKind.CONTEXT_OVERFLOW,
            "context window exceeded",
            retryable = false,
            contextLimitHint = contextLimitHint(payload),
        )
    }

    // 词表兜底（message 字段）：超窗 → 限流 → 服务端 → 兜底，顺序理由见类 KDoc。
    return when {
        looksLikeContextOverflowMessage(haystack) -> GenerationEvent.Failed(
            EngineErrorKind.CONTEXT_OVERFLOW,
            "context window exceeded",
            retryable = false,
            contextLimitHint = contextLimitHint(payload),
        )

        haystack.contains("rate") || haystack.contains("429") || haystack.contains("quota") ->
            GenerationEvent.Failed(EngineErrorKind.RATE_LIMIT, message, retryable = true)

        haystack.contains("overload") || haystack.contains("server") ||
            haystack.contains("5xx") || haystack.contains("unavailable") ->
            GenerationEvent.Failed(EngineErrorKind.SERVER, message, retryable = true)

        else -> GenerationEvent.Failed(EngineErrorKind.PROTOCOL, message, retryable = false)
    }
}

/**
 * 「这段错误文案指向上下文超窗」。
 *
 * 与 `RemoteLlmEngine.looksLikeContextOverflow`（HTTP 400 错误体）**共用同一个词表**：
 * 「context/token」与「超限词」必须同时命中。两处都判的代价是重复，收益是
 * 「流内错误帧」与「HTTP 400」不会各自漂移成两套判据 —— 所以词表只在这里定义一次，
 * 引擎侧那一份改为委托本函数。
 */
internal fun looksLikeContextOverflowMessage(lower: String): Boolean {
    val mentionsWindow = lower.contains("context") || lower.contains("token")
    val mentionsOverflow = lower.contains("too long") ||
        lower.contains("maximum") ||
        lower.contains("exceed") ||
        lower.contains("limit") ||
        lower.contains("length")
    return mentionsWindow && mentionsOverflow
}
