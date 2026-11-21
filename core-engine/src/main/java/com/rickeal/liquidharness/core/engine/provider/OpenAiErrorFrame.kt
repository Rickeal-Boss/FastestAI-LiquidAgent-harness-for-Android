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
 * 判定顺序按证据强度分两支（W41 订正）：有结构化 `type`/`code` 时「限流 → 服务端 →
 * 超窗 → 兜底」——供应商自声明比正文措辞可信；只有 `message` 时同样先判限流/服务端
 * 措辞，再判超窗词表（`rate limit: too many tokens` 的 token×limit 会误命中超窗）。
 *
 * ## W41：message-only 路径把限流/服务端判在超窗之前 + 数字 `code` 兜底
 *
 * W40 只去掉了 payload 全文，但 `token`×`limit` 本来就常出现在供应商的 `message` 里：
 * `rate limit: too many tokens` 仍会先命中超窗词表被误判 `CONTEXT_OVERFLOW`（不可重试），
 * 把限流变成静默失败。`type`/`code` 缺失时没有结构化证据可用，因此**无结构化字段**
 * 的路径改为「限流措辞 → 服务端措辞 → 超窗词表 → 兜底」；有结构化字段时仍沿用
 * 「限流 → 服务端 → 超窗」的可信自声明优先顺序。数字 `code: 429` 用 `JsonPrimitive.content`
 * 兜底为字符串，不再因 `stringOrNull` 只能读字符串而跌回词表判 PROTOCOL。
 * 超窗分支保留供应商原始 `message`（可诊断文案不再被硬替换）。
 *
 * ## W42：message-only 判序的反向回归订正
 *
 * W41 顺序（限流/服务端措辞全在超窗之前）引入反向形态：超窗文案里常自带
 * `server`/`rate` 字样（`server cannot process: maximum context length exceeded`），
 * 会被判 SERVER/RATE_LIMIT **可重试** ⇒ 同长度请求重发两次、两次都超窗（W40 KDoc
 * 曾点名该风险）。W42 按证据强度重排：**显式限流措辞**（`rate limit` 连写 /
 * `too many tokens` / 强限流词 429/quota/throttl/slow down/too many requests）最前
 * ——限流按 token 计、文案天然带 token/limit，`rate limit: too many tokens`（E5e）
 * 只有这一支能救回来；**超窗强组合**提到服务端/裸 rate 弱词之前；裸 `rate`/`server`
 * 退到超窗组合之后兜底。E5e（限流不误判超窗）与 E5c/E5d（结构化优先）不受影响；
 * 新增反向必红 E5h/E5i/E5j：超窗文案含 server/rate 字样仍判 CONTEXT_OVERFLOW。
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
    // W41：数字型 code（429 / 503）在 OpenAI 兼容端点很常见，stringOrNull 只认字符串
    // ⇒ 不兜底会跌回 message 词表、甚至判 PROTOCOL（不可重试）。JsonPrimitive.content
    // 对字符串与数字都成立，重复值对分类无影响。
    val codeText = (errorObj?.get("code") as? JsonPrimitive)?.content
    // 结构化字段优先。type/code 是供应商自声明（rate_limit_error / server_error），
    // 不含正文措辞 ⇒ 命中限流/服务端词表时**先于** message 词表兜底裁决。
    // haystack 只拼四个错误字段、**不含 payload 全文**（含进去会让限流文案里的
    // token/limit 撞上超窗词表，见类 KDoc「W40」段）。
    val typeAndCode = listOfNotNull(
        AgentJson.stringOrNull(errorObj, "type"),
        AgentJson.stringOrNull(errorObj, "code"),
        codeText,
    ).joinToString(" ").lowercase()
    val haystack = listOfNotNull(
        AgentJson.stringOrNull(errorObj, "type"),
        AgentJson.stringOrNull(errorObj, "code"),
        codeText,
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
            message,
            retryable = false,
            contextLimitHint = contextLimitHint(payload),
        )
    }

    // W42：无结构化字段时的判序按「证据强度」重排。判序冲突点先说清楚：
    // `rate limit: too many tokens`（E5e 样本）同时命中「限流语境」与超窗词表
    // （token×limit）——限流按 token 计，文案天然带 token/limit 字样；而真实
    // 超窗文案极少自称 `rate limit`。因此「显式限流措辞」（rate limit 连写 /
    // too many tokens / 强限流词）优先于超窗组合；超窗组合优先于裸 server/rate
    // 弱词（W41 反向回归形态：`server cannot process: maximum context length
    // exceeded` 不得因 `server` 字样被判可重试）。
    // 判序：① 限流强词（429/quota/throttl/slow down/too many requests）+ 显式
    // 限流措辞（rate limit 连写 / too many tokens）；② 超窗强组合（窗口词 × 超限
    // 词，见 looksLikeContextOverflowMessage）；③ 服务端弱词；④ 裸 `rate` 兜底；
    // ⑤ PROTOCOL fail-closed。
    return when {
        haystack.contains("429") || haystack.contains("quota") ||
            haystack.contains("throttl") || haystack.contains("slow down") ||
            haystack.contains("too many requests") ||
            haystack.contains("rate limit") || haystack.contains("too many tokens") ->
            GenerationEvent.Failed(EngineErrorKind.RATE_LIMIT, message, retryable = true)

        looksLikeContextOverflowMessage(haystack) -> GenerationEvent.Failed(
            EngineErrorKind.CONTEXT_OVERFLOW,
            message,
            retryable = false,
            contextLimitHint = contextLimitHint(payload),
        )

        haystack.contains("overload") || haystack.contains("server") ||
            haystack.contains("5xx") || haystack.contains("unavailable") ->
            GenerationEvent.Failed(EngineErrorKind.SERVER, message, retryable = true)

        haystack.contains("rate") ->
            GenerationEvent.Failed(EngineErrorKind.RATE_LIMIT, message, retryable = true)

        else -> GenerationEvent.Failed(EngineErrorKind.PROTOCOL, message, retryable = false)
    }
}

/**
 * 「这段错误文案指向上下文超窗」。
 *
 * 与 `RemoteLlmEngine.looksLikeContextOverflow`（HTTP 400 错误体）**共用同一个词表**：
 * 「窗口词」与「超限词」必须同时命中。两处都判的代价是重复，收益是
 * 「流内错误帧」与「HTTP 400」不会各自漂移成两套判据 —— 所以词表只在这里定义一次，
 * 引擎侧那一份改为委托本函数。
 *
 * W42：窗口词从 `context|token` 扩到含 `prompt` —— 供应商超窗文案常用
 * `prompt too long, server rejected`（外审 P1-1 反向样本 E5j），原词表判不出超窗、
 * 会被服务端弱词抢走。加 `prompt` 后仍要求**同时**命中超限词，误判面窄；单一真值源
 * 改一处即 HTTP 400 与 SSE 帧同步生效，不长出第二把尺子。
 */
internal fun looksLikeContextOverflowMessage(lower: String): Boolean {
    val mentionsWindow = lower.contains("context") || lower.contains("token") ||
        lower.contains("prompt")
    val mentionsOverflow = lower.contains("too long") ||
        lower.contains("maximum") ||
        lower.contains("exceed") ||
        lower.contains("limit") ||
        lower.contains("length")
    return mentionsWindow && mentionsOverflow
}
