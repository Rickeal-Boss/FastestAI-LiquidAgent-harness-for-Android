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
 * ## W43：修 W42 修法自身的两处反噬
 *
 * (a) `too many tokens` 被 W42 列为**无条件**限流强词并置于超窗之前 ⇒ `context length
 * exceeded: too many tokens`（超窗文案报告 token 过多，属常态）被判 RATE_LIMIT
 * 可重试，重发同长度请求两次都超窗。W43 收为**限流语境内强词**：须与显式限流语境
 * （`rate limit` 连写 / `quota`/`throttl`/`429`/`slow down`/`too many requests`）同现才算
 * 限流；E5e `rate limit: too many tokens` 有连写仍保住。**不含裸 `rate`**——E5i
 * `request rate too large: maximum context length exceeded` 是超窗，裸 rate 会重引
 * W42 已修的反向回归。新增 E5k 必红。
 *
 * (b) 窗口词补 `prompt` 后，裸 `prompt`×`limit` 在 HTTP 400 路径把普通 400
 * （`prompt limit reached for this endpoint`）判成 CONTEXT_OVERFLOW（不可重试 + 触发
 * 压缩 + cache 失效）。W43 给 `prompt` 加护栏：`context`/`token` 仍是窗口词，`prompt`
 * 仅在搭配长度语义（`too long`/`maximum`/`exceed`/`length`，**不含**裸 `limit`）时算窗口词。
 * E5j `prompt too long` 仍命中；两条路径共用本函数，单一真值源不漂移。
 *
 * ## W44：强词与超窗组合的证据强度仲裁（外审 P0-2 残留收口）
 *
 * W43 修法是「点状收窄」，两处残留（外审按现役源码逐字复刻 27 样本坐实）：
 *
 * (c) 超窗文案自带强限流词仍误判可重试：`maximum context length exceeded (quota:
 * 100000 tokens)` / `context window exceeded, slow down your requests` / `context
 * length exceeded: rate limit 100000 tokens` —— `rateContext` 六词无条件前置 ⇒ 全判
 * RATE_LIMIT，同长度请求重发两次、两次都超窗（W40 KDoc 点名的形态）。
 *
 * (d) 护栏只加在 `prompt`，`context`/`token` 仍直接配裸 `limit`：HTTP 400
 * `token limit reached: reduce prompt` / `token limit reached for this endpoint`
 * 判 CONTEXT_OVERFLOW（不可重试 + 压缩 + cache 失效），普通配额错误被放大处置。
 *
 * W44 仲裁规则：**结构化证据无条件优先**（`type`/`code` 限流自声明、HTTP 429 状态码
 * ——供应商自声明不受正文措辞影响，本函数判定前已完成）；message-only 路径上，
 * **超窗强组合**（窗口词 × 长度语义，见 looksLikeContextOverflowMessage）命中时
 * **不再被强限流词抢走**——真实限流文案极少自称 `maximum context length exceeded`。
 * 强限流词只在超窗组合未命中时才裁决 RATE_LIMIT（E5e `rate limit: too many tokens`
 * 无超窗强组合：`too many tokens` 不属长度语义，裸 `limit` 降级后也不算超限词 ⇒ 仍落
 * 限流支，保住）。裸 `rate` 弱词与 `server` 弱词仍在超窗之后兜底（E5h/E5i 不回退）。
 * 裸 `limit` 从 `mentionsOverflow` 降级为「需搭配窗口词 + 长度语义」，与 `prompt`
 * 护栏同一把尺：`token limit reached` 不再算超窗（W44 新必红），`maximum context
 * length` / `context length exceeded` 等真实超窗文案不受影响。
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

    // W42：无结构化字段时的判序按「证据强度」重排。W44（证据强度仲裁）：
    // ① 结构化 `type`/`code` 已在上方无条件裁决（供应商自声明，最高优先级）；
    // ② **超窗强组合**（窗口词 × 长度语义）——真实限流文案极少自称
    //   `maximum context length exceeded`，反之超窗文案常自带 quota/slow down 字样；
    // ③ 强限流词（429/quota/throttl/slow down/too many requests/rate limit 连写 +
    //   `too many tokens` 限流语境）——E5e `rate limit: too many tokens` 无超窗强组合
    //   （`too many tokens` 非长度语义）⇒ 仍落此支保住；
    // ④ 服务端弱词；⑤ 裸 `rate` 兜底；⑥ PROTOCOL fail-closed。
    // W44 判序相对 W43 的变化：强限流词从「无条件最前」退到「超窗强组合之后」——
    // 注入「把 quota 放回无条件强词」⇒ 超窗+quota 必红 RED（W44 新增）。
    // 注意不含裸 `rate`：E5i `request rate too large: maximum context length exceeded`
    // 是超窗文案，裸 rate 会重引 W42 已修的反向回归。只认显式限流语境。
    val rateContext = haystack.contains("429") || haystack.contains("quota") ||
        haystack.contains("throttl") || haystack.contains("slow down") ||
        haystack.contains("too many requests") || haystack.contains("rate limit")
    val explicitRateLimit = rateContext ||
        (haystack.contains("too many tokens") && rateContext)
    return when {
        looksLikeContextOverflowMessage(haystack) -> GenerationEvent.Failed(
            EngineErrorKind.CONTEXT_OVERFLOW,
            message,
            retryable = false,
            contextLimitHint = contextLimitHint(payload),
        )

        explicitRateLimit ->
            GenerationEvent.Failed(EngineErrorKind.RATE_LIMIT, message, retryable = true)

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
 * 会被服务端弱词抢走。单一真值源改一处即 HTTP 400 与 SSE 帧同步生效，不长出第二把尺子。
 *
 * W43（反噬 b 护栏）：裸 `prompt` 配 `limit` 是高频普通 400（`prompt limit reached for
 * this endpoint` / `your prompt is malformed, limit 10 requests`），不是超窗。故
 * `context`/`token` 仍直接算窗口词，`prompt` 只在搭配**长度语义**（`too long` /
 * `maximum` / `exceed` / `length`，明确**不含**裸 `limit`）时才算窗口词。E5j
 * `prompt too long` 仍命中；新增必红钉「HTTP 400 裸 prompt limit 不判超窗」。
 *
 * W44（外审 P0-2(d) 残留）：裸 `limit` 从 `mentionsOverflow` **整体降级**——
 * `context`/`token` × 裸 `limit`（`token limit reached: reduce prompt`）同样是高频
 * 普通配额/参数 400，判超窗的代价（不可重试 + 触发压缩 + prompt cache 失效）远大于
 * 失败一次。降级后超限词只认**长度语义**（`too long`/`maximum`/`exceed`/`length`），
 * `prompt` 窗口词护栏维持；真实超窗文案（`maximum context length` / `context length
 * exceeded`）全部不受影响（`length` 本身就是长度语义）。裸 `limit` 命中时不再判超窗，
 * 落限流/配额/PROTOCOL 分类。新增必红：SSE `token limit reached for this endpoint`、
 * HTTP 400 `token limit reached: reduce prompt`（后者在 ContextLimitTest）。
 * 注入「把裸 limit 放回 mentionsOverflow」⇒ 两条必红 RED。
 */
internal fun looksLikeContextOverflowMessage(lower: String): Boolean {
    val mentionsLengthOverflow = lower.contains("too long") ||
        lower.contains("maximum") ||
        lower.contains("exceed") ||
        lower.contains("length")
    // W44：裸 `limit` 降级出超限词表（见 KDoc）——与 `prompt` 护栏同一把尺。
    val mentionsOverflow = mentionsLengthOverflow
    // context/token 直接算窗口词（配长度语义超限词）；prompt 需长度语义护栏。
    val mentionsWindow = lower.contains("context") || lower.contains("token") ||
        (lower.contains("prompt") && mentionsLengthOverflow)
    return mentionsWindow && mentionsOverflow
}
