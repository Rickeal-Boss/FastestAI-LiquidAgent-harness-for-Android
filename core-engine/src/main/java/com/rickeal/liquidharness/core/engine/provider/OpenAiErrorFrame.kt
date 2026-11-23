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
 * ## W45：窗口词强弱分级（外审 P0-1 镜像反噬收口）
 *
 * W44 仲裁修好了「超窗文案含限流强词」一侧（W43 残留），但把超窗强组合**无条件**
 * 前置又引入**镜像反噬**：`mentionsWindow` 里的 `token` 与 `mentionsLengthOverflow` 里的
 * `exceed`/`length` 在**限流**文案里同样是高频词（限流恰好按 token 计、文案天然带 exceed）——
 * `token rate limit exceeded` / `you have exceeded your token quota` /
 * `too many tokens: rate limit exceeded` 全被判 `CONTEXT_OVERFLOW`（不可重试 + 触发压缩 +
 * cache 失效），方向与代价同 W40 KDoc 点名形态（把限流错放大成最贵的处置）。
 *
 * W45 分级：窗口词按证据强度分两级。
 * - **强窗口词** `context` / `prompt`（限流文案极少自称 context）：× 长度语义 ⇒ 无条件
 *   先于强限流词，与 W44 行为一致（E5h/E5i/E5j/E5l/E5m 全部不回退）。
 * - **弱窗口词** `token`（限流按 token 计）：长度语义**且无限流语境**才判超窗——
 *   强长度语义（`maximum`/`too long`/`length`）或弱 `exceed` 都须先过「无限流语境」
 *   这一关。于是 `token rate limit exceeded` 与死角样本 `rate limit: maximum 100
 *   tokens per minute`（限流文案带 maximum）都落回限流处置；无 rate limit 语境的
 *   `token limit reached: maximum 100000 tokens`（真超窗）仍判超窗；裸
 *   `token limit reached` 落 PROTOCOL（W44 必红③不回退）。
 * 三条新必红（SSE `token rate limit exceeded` / SSE `too many tokens: rate limit
 * exceeded` / HTTP400 `token rate limit exceeded`）未修版实跑 RED；注入「把 token 升回
 * 无条件窗口词」⇒ RED。
 *
 * ## W48：判据范式转换（词表封版，不再扩自然语言词表）
 *
 * W42→W47 五轮「点状补词 → 镜像反噬」打完后，W47 已承诺 W48 起改判据范式。本段是范式
 * 本体，此后**再出反例只改结构化映射与证据标签、不改词表**：
 *
 * 1. **结构化 `type`/`code` 与 HTTP status 是唯一权威**（供应商自声明）：命中即裁决，
 *    message 措辞不得改判（E5c/E5d 口径，注入「回退到词表优先」⇒ 必红 RED）。
 * 2. **message 只做兜底**：无结构化字段时走既有分层启发式（词表**冻结**，逐字沿用
 *    W46/W47 形态，25 样本 + C11–C14 全部在新范式下保持原判级）。
 * 3. **每次裁决携带证据标签** [GenerationEvent.Failed.evidenceTag]：结构化支
 *    `frame:structured-rate|server|context`；message 兜底支 `frame:overflow-wording` /
 *    `frame:rate-wording` / `frame:server-wording` / `frame:rate-weak-wording`；fail-closed
 *    `frame:protocol-fallback`。HTTP 路径同口径（`RemoteLlmEngine.classifyHttpFailure`：
 *    `http:auth|rate|timeout|overflow|server|rejected`）。标签是固定诊断码形态，随
 *    `run failed:` 行落盘供真机诊断，不含用户内容。
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
        return GenerationEvent.Failed(
            EngineErrorKind.RATE_LIMIT, message, retryable = true, evidenceTag = "frame:structured-rate",
        )
    }
    if (typeAndCode.contains("overload") || typeAndCode.contains("server") ||
        typeAndCode.contains("5xx") || typeAndCode.contains("unavailable")
    ) {
        return GenerationEvent.Failed(
            EngineErrorKind.SERVER, message, retryable = true, evidenceTag = "frame:structured-server",
        )
    }
    if (typeAndCode.contains("context")) {
        return GenerationEvent.Failed(
            EngineErrorKind.CONTEXT_OVERFLOW,
            message,
            retryable = false,
            contextLimitHint = contextLimitHint(payload),
            evidenceTag = "frame:structured-context",
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
    // W46（P1-1 结构性收口）：逐字复刻的 21 样本里，W45 强弱分级仍有 5 向边界
    // （R04 tokens per minute / R05 tpm / R08 prompt too long+rate limit / C07 maximum token
    // count + quota / C08 token length + slow down）—— 与 W42→W45 四轮「点状补词→镜像反噬」
    // 同型。W46 停止逐波打地鼠，把 message 词表降为**分层启发式**：
    //   ① 结构化 type/code + HTTP 429 仍是唯一权威（上方已裁决，不受本段影响）；
    //   ② 限流语境扩到时间/速率词（tpm/tokens per minute/requests per minute/per-minute）；
    //   ③ 区分**强限流语境**（能压过窗口词×长度语义）与弱限流词（quota/slow down）；
    //   ④ 长度语义分强（maximum/too long/length）与弱（exceed）—— 强窗口词配弱 exceed
    //      也算超窗（保 C10/E5j），但强限流语境可压过它（修 R08）。
    // 双向混淆样本集 + 单测参数化 + 本仓复刻脚本共用 scripts/fixtures/ 同一份 JSON。
    val rateContext = mentionsRateLimitContext(haystack)
    return when {
        looksLikeContextOverflowMessage(haystack) -> GenerationEvent.Failed(
            EngineErrorKind.CONTEXT_OVERFLOW,
            message,
            retryable = false,
            contextLimitHint = contextLimitHint(payload),
            evidenceTag = "frame:overflow-wording",
        )

        rateContext ->
            GenerationEvent.Failed(
                EngineErrorKind.RATE_LIMIT, message, retryable = true, evidenceTag = "frame:rate-wording",
            )

        haystack.contains("overload") || haystack.contains("server") ||
            haystack.contains("5xx") || haystack.contains("unavailable") ->
            GenerationEvent.Failed(
                EngineErrorKind.SERVER, message, retryable = true, evidenceTag = "frame:server-wording",
            )

        haystack.contains("rate") ->
            GenerationEvent.Failed(
                EngineErrorKind.RATE_LIMIT, message, retryable = true, evidenceTag = "frame:rate-weak-wording",
            )

        else -> GenerationEvent.Failed(
            EngineErrorKind.PROTOCOL, message, retryable = false, evidenceTag = "frame:protocol-fallback",
        )
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
 *
 * W45（窗口词强弱分级，外审 P0-1 镜像反噬）：W44 把超窗组合无条件前置后，
 * 限流文案（按 token 计、天然带 `exceed`/`length`）反向被吃进超窗。分级规则：
 * - 强窗口词 `context`/`prompt`（须长度语义，W43/W44 护栏不变）× 长度语义 ⇒ 超窗；
 * - 弱窗口词 `token` 须长度语义**且无限流语境**（强长度语义 `maximum`/`too long`/`length`
 *   与弱 `exceed` 同过这一关）—— `token rate limit exceeded` 落回限流处置，
 *   `token limit reached: maximum 100000 tokens`（无 rate limit 语境的真超窗）仍判超窗。
 * `rateContextWords` 是 `rateContext` 的同一把限流语境尺（提取为顶层函数
 * [mentionsRateLimitContext]，SSE 帧与 HTTP 400 同源），保持单一真值源不漂移。
 */
internal fun looksLikeContextOverflowMessage(lower: String): Boolean {
    // W46 分层：长度语义分强弱 —— 强 maximum/too long/length、弱 exceed。
    val strongLength = lower.contains("too long") || lower.contains("maximum") ||
        lower.contains("length")
    val weakLength = lower.contains("exceed")
    val anyLength = strongLength || weakLength
    // 窗口词：context 直接算；prompt 需长度语义护栏（W43，裸 prompt limit 不算）。
    // W47 拆为 contextWindow / promptWindow（让位语义不同，见下方判定）。
    val tokenWindow = lower.contains("token")
    val strongRate = mentionsStrongRateLimit(haystack = lower)

    // W47（P0-2 判序最后一轮）：context 恢复**无条件**强窗口词；强限流语境让位只留给
    // prompt。W46 让 context 也在强限流语境下让位，但真超窗文案天然自带 rate limit /
    // tpm / 429 字样（`maximum context length exceeded (rate limit: 100000 tokens)`），
    // 被误判 RATE_LIMIT ⇒ 同长度请求重发两次（W40 KDoc 点名的代价）。`context` 自称
    // 超窗窗口是比限流词更强的证据；真正需要让位的只有 `prompt`（`prompt too long:
    // rate limit exceeded` 语义偏限流，保 R08）。W48 起不再扩词表，改判据范式。
    val contextWindow = lower.contains("context")
    val promptWindow = lower.contains("prompt") && anyLength
    // ① context × 任意长度语义（含弱 exceed）：无条件判超窗，强限流语境不让位
    //    （E5h/i/l、C01-C06 与 4 条扩展边界不回退）。
    if (contextWindow && anyLength) return true
    // ② prompt × 任意长度语义：强限流语境下让位（保 R08），否则判超窗（E5j/C03）。
    if (promptWindow && !strongRate) return true
    // ③ 弱窗口词 token × 强长度语义（maximum/too long/length）：
    //    无强限流语境即判超窗，quota / slow down 这类**弱**限流词不让位
    //    （修 C07 `maximum token count exceeded (quota: …)` / C08 `token length exceeded:
    //    slow down your requests`，即 W44 两条必红的弱窗口词变体）。
    if (tokenWindow && strongLength && !strongRate) return true
    // ③ 弱窗口词 token × 弱长度语义（仅 exceed）：须无任何限流语境
    //    （保 C10 `token budget exceeded` 判超窗、W45 死角 token quota 落限流）。
    return tokenWindow && weakLength && !mentionsRateLimitContext(lower)
}

/**
 * 「显式限流语境」词表（W45 提取为单一真值源，W46 扩到时间/速率词）。
 *
 * 与 SSE 帧判定用的 `rateContext` 完全同源 —— 改一处即两路同步。
 * W46 扩表：`tpm` / `tokens per minute` / `requests per minute` / `per-minute`，
 * 修 R04 `tokens per minute limit exceeded` / R05 `tpm exceeded: token budget`
 * 被 `token`×`exceed` 误判超窗（外审 P1-1 双向混淆样本，未修版实跑 RED）。
 */
internal fun mentionsRateLimitContext(lower: String): Boolean =
    lower.contains("429") || lower.contains("quota") ||
        lower.contains("throttl") || lower.contains("slow down") ||
        lower.contains("too many requests") || lower.contains("rate limit") ||
        lower.contains("tpm") || lower.contains("tokens per minute") ||
        lower.contains("requests per minute") || lower.contains("per-minute")

/**
 * 「强限流语境」——证据强度高于「窗口词 × 长度语义」的那一档（W46 新增）。
 *
 * 只有这些词能压过 ① 强窗口词分支与 ② 弱 token × 强长度分支；`quota` / `slow down`
 * 是弱词（限流文案常报告 used quota、建议 slow down，超窗文案也常自带），不足以让位。
 * 单一真值源 [mentionsRateLimitContext] 的子集，两处不各自另起一套词表。
 */
internal fun mentionsStrongRateLimit(haystack: String): Boolean =
    haystack.contains("429") || haystack.contains("rate limit") ||
        haystack.contains("throttl") || haystack.contains("too many requests") ||
        haystack.contains("tpm") || haystack.contains("tokens per minute") ||
        haystack.contains("requests per minute") || haystack.contains("per-minute")
