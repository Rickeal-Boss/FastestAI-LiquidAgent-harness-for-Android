package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 会话导入解析器：**纯函数、零 Android、零 IO**。
 *
 * ## 它存在的唯一理由：让「批量题面」有一个入口
 *
 * 设备侧 1M 长上下文测试（W5-04）此前是 **vacuous** 的：仓里没有任何把成百上千条
 * 消息塞进一次会话的通道，只能在 PC 侧用脚本打端点。没有入口的判据不算判据
 * （`AGENTS.md` §二.3）。本类把「一段文本 → `List<ChatMessage>`」做成可 JVM 单测的
 * 纯函数，IO 与文件选择留在 UI 层（`feature-settings` 的 `SessionImportRow`）。
 *
 * ## 两种格式，JSONL 为主
 *
 * - **JSONL**（主）：每行一个 `{"role":"user"|"assistant","text":"..."}`。选它做主力是
 *   因为**可脚本生成** —— 990k token 的干草堆必须由脚本产出，手写 markdown 会引入
 *   分段歧义。
 * - **markdown**（备）：`## user` / `## assistant` 分段，段落正文到下一个小标题为止。
 *
 * ## 三条硬约束（每一条都是踩过 400 的形态）
 *
 * 1. **只接受 `user` / `assistant`**。`role=tool` 一律丢弃并**计数上报**
 *    （[Result.Ok.droppedToolRoles]）：导入的是「题面」，不是「工具往返」。手写 JSONL
 *    无法保证 `tool_call_id` 与声明配对，塞进 history 要么被 `sanitizeForProvider`
 *    洗成「结果缺失」，要么直接 400。
 * 2. **上限**：单文件 ≤ [MAX_BYTES]、消息 ≤ [MAX_MESSAGES] 条、单条 ≤ [MAX_MESSAGE_CHARS]
 *    字符。超限**拒绝并给具体原因**（`detail` 里带实测值与上限），不静默截断 ——
 *    截断会造出一份「看起来导入成功、实际少了半道题」的题面。
 *    单条上限刻意放到 4 Mi 字符（W6 追加口径）：990k token 的干草堆 ≈ 4M 字符，
 *    按旧的 20 万字符上限要拆成 20+ 条消息，而**拆消息会改变题面形态**
 *    （每条消息都带协议开销，且模型对「一段长文本」与「20 段」的处理不同）。
 *    因此解析器同时支持**单条巨型消息**与**多条中等消息**两种形态，不做任何自动切分。
 * 3. **附件一律不带**：`ChatMessage.attachments` 恒空（历史图片不回灌，
 *    `ChatHistoryMapper` 同一口径）。
 *
 * ## 三条「不静默」的口径
 *
 * - `role` 既不是 user / assistant / tool 的（例如 `system`）⇒ **整份拒绝**，不是丢弃。
 *   `tool` 有明确的可丢弃理由（见上），而 `system` 被静默丢掉会**改变模型行为**且用户
 *   看不见；把 system 拼进 history 又等于让导入通道改写提示词。两条都不接受，所以拒绝。
 * - `text` 为空的消息被**跳过且计数**（[Result.Ok.droppedBlank]，W16-18 定谳后改）：
 *   单条消息物化出来是一张空气泡，「没有内容」对单条成立；但批量题面场景下 299 行
 *   text 缺失/空白却报「导入成功 (1)」就是丢内容 —— 原口径「跳过且不计数」的辩护
 *   对批量不成立，空正文必须可见。
 */
object SessionImportParser {

    /** 单文件字节上限（8 MiB）。取 8 MiB 而不是更大：解析后全部驻留内存，两倍开销。 */
    const val MAX_BYTES: Int = 8 * 1024 * 1024

    /** 消息条数上限。5 000 条 × 平均 1k 字符已远超任何单次窗口。 */
    const val MAX_MESSAGES: Int = 5_000

    /**
     * 单条消息字符上限（**4 Mi 字符** = 4 194 304）。W6 追加口径，原值 200 000。
     *
     * 为什么放到 4 Mi：990k token 的干草堆 ≈ 4M 字符，按 20 万要拆 20+ 条消息，
     * 而拆消息会改变题面形态（协议开销 + 模型对「一段长文本」与「20 段」处理不同）。
     * 量纲是 **`String.length`（UTF-16 单元）**，不是字节：文件总量上限
     * [MAX_BYTES] = 8 MiB 已经把真实的字节规模兜住，两条上限互补而不是重复。
     * 纯 CJK 文件 8 MiB ≈ 2.8 Mi 字符 ⇒ 永远先撞文件上限，这是刻意的（字节才是账单与内存）。
     */
    const val MAX_MESSAGE_CHARS: Int = 4 * 1024 * 1024

    /** 支持的输入格式。 */
    enum class Format { JSONL, MARKDOWN }

    /**
     * 拒绝原因码。**纯 ASCII 常量**（守卫 G16）：上屏文案由 `feature-settings` 按码映射，
     * 本模块不认识语言。
     */
    enum class Rejection {
        /** 文件字节数超 [MAX_BYTES]。 */
        TOO_LARGE,

        /** 消息条数超 [MAX_MESSAGES]。 */
        TOO_MANY_MESSAGES,

        /** 单条消息字符数超 [MAX_MESSAGE_CHARS]。 */
        MESSAGE_TOO_LONG,

        /** 解析后没有任何可导入的消息（空文件 / 全是 tool 行 / 全是空正文）。 */
        EMPTY,

        /** 语法不合法（坏 JSON、缺 role、未知 role、行内字段类型不对）。 */
        MALFORMED,
    }

    /** 解析结果。 */
    sealed interface Result {
        /**
         * @param messages 只含 [Role.USER] / [Role.MODEL]，顺序与文件一致，附件恒空。
         * @param droppedToolRoles 被丢弃的 `role=tool` 行数（**必须上报**，否则用户会以为
         *   「导入的文件完整进来了」）。
         * @param droppedBlank 被丢弃的**空正文**行/段数（role 合法但 text 缺失或空白 —— W16-18
         *   定谳：Q3 口径「没有内容不是丢内容」对单条成立，对批量题面不成立；夹具若用
         *   `content` 字段名，299 行会静默跳过且 dropped=0。**必须上报**，数出来的静默才不是静默）。
         */
        data class Ok(
            val messages: List<ChatMessage>,
            val droppedToolRoles: Int,
            val droppedBlank: Int = 0,
        ) : Result

        /** @param detail 具体原因（ASCII，含实测值与上限），直接进日志/诊断。 */
        data class Rejected(
            val reason: Rejection,
            val detail: String,
        ) : Result
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 按文件名猜格式。**默认 JSONL**：它是主格式，未知扩展名时按主格式试更可能成功。
     *
     * 只看后缀，不看内容嗅探 —— 嗅探会把「第一行恰好不是 JSON」的合法文件判成 markdown，
     * 然后用户在错误格式上看到一条「未知 role」的报错，排查方向全错。
     */
    fun detectFormat(fileName: String?): Format {
        val lower = fileName.orEmpty().lowercase()
        return when {
            lower.endsWith(".md") || lower.endsWith(".markdown") -> Format.MARKDOWN
            else -> Format.JSONL
        }
    }

    /**
     * 字节入口。**先判字节上限再解码** —— 8 MiB 的 UTF-8 解码本身就要一份等长内存，
     * 先把 200 MiB 的文件解成 String 再判大小，等于用一个必然的 OOM 代替一条错误提示。
     */
    fun parse(bytes: ByteArray, format: Format): Result {
        if (bytes.size > MAX_BYTES) {
            return Result.Rejected(
                Rejection.TOO_LARGE,
                "file bytes=" + bytes.size + " cap=" + MAX_BYTES,
            )
        }
        // Q2：UTF-8 BOM（EF BB BF）解码后是 U+FEFF，留在首行会让第一行 JSON 直接 MALFORMED。
        return parse(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"), format)
    }

    /** 文本入口。同样剥 BOM：它不只来自字节解码（导入通道也可能直接喂 String）。 */
    fun parse(text: String, format: Format): Result {
        val clean = text.removePrefix("\uFEFF")
        return when (format) {
            Format.JSONL -> parseJsonl(clean)
            Format.MARKDOWN -> parseMarkdown(clean)
        }
    }

    // ------------------------------------------------------------------ JSONL

    private fun parseJsonl(text: String): Result {
        val messages = ArrayList<ChatMessage>()
        var dropped = 0
        var droppedBlank = 0
        var lineNo = 0
        for (raw in text.lineSequence()) {
            lineNo++
            val line = raw.trim()
            if (line.isEmpty()) continue
            val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
                ?: return malformed("line " + lineNo + ": not a json object")
            val role = (obj["role"] as? JsonPrimitive)?.content?.trim()?.lowercase()
                ?: return malformed("line " + lineNo + ": missing string field role")
            val body = (obj["text"] as? JsonPrimitive)?.content.orEmpty()
            // 先处理「丢弃并计数」这一档，再用 if 链判合法角色：`when` 里放 continue
            // 依赖 Kotlin 1.4+ 的语法放宽，写成 if 链在阅读上也没有歧义。
            if (role == "tool") {
                dropped++
                continue
            }
            // W16-18：空正文行**计数后跳过**（不再静默）—— 批量题面场景里 299 行 text
            // 缺失/空白却报「导入成功 (1)」就是丢内容。调用 [append] 前判定，避免它的
            // Q3 空分支把 JSONL 的空行吞成静默。
            if (body.isBlank()) {
                droppedBlank++
                continue
            }
            // 见类头「两条不静默的口径」：未知 role 整份拒绝。
            val roleValue = if (role == "user") {
                Role.USER
            } else if (role == "assistant") {
                Role.MODEL
            } else {
                return malformed("line " + lineNo + ": unsupported role " + role)
            }
            val error = append(messages, roleValue, body, "line " + lineNo)
            if (error != null) return error
        }
        return finish(messages, dropped, droppedBlank)
    }

    // --------------------------------------------------------------- markdown

    /**
     * `## user` / `## assistant` 分段。
     *
     * 首个小标题之前的内容（标题、说明、空行）**整段忽略**：备格式是给人手写的，
     * 前面写一段「这是 1M 长上下文测试题面」的说明是正常用法，不是错误。
     * 小标题必须是 `## ` + 角色名（两个井号 + 一个空格），`###` 与 `#` 都不算 ——
     * 否则正文里的三级标题会被当成新的分段，把一条消息劈成两半。
     */
    private fun parseMarkdown(text: String): Result {
        val messages = ArrayList<ChatMessage>()
        var dropped = 0
        var droppedBlank = 0
        var role: Role? = null
        val body = StringBuilder()
        var headingLine = 0

        fun flush() {
            val current = role ?: return
            // 逐行拼进来时每行尾部都补了 '\n'：收口时去掉段尾换行（段**内**空行保留），
            // 否则每条消息都以一个换行结尾，断言与请求体里都是噪声。
            val text0 = body.toString().trimEnd()
            // 与 JSONL 同口径：空段计数后跳过（不静默），见 parseJsonl 的 W16-18 注。
            if (text0.isBlank()) droppedBlank++
            val error = append(messages, current, text0, "section at line " + headingLine)
            if (error != null) throw SectionRejected(error)
            body.setLength(0)
        }

        try {
            var lineNo = 0
            for (raw in text.lineSequence()) {
                lineNo++
                val line = raw.trimEnd()
                val heading = headingRoleOf(line)
                if (heading != null) {
                    // 先把上一段收口，再切角色：`headingLine` 因此始终指向**本段**的小标题。
                    flush()
                    if (heading == "tool") {
                        dropped++
                        role = null
                    } else if (heading == "user") {
                        role = Role.USER
                    } else if (heading == "assistant") {
                        role = Role.MODEL
                    } else {
                        return malformed("line " + lineNo + ": unsupported role " + heading)
                    }
                    headingLine = lineNo
                    continue
                }
                if (role != null) body.append(line).append('\n')
            }
            // 末段必须在 try 内收口：`flush()` 会以异常抛出「超限」这一档拒绝。
            flush()
        } catch (rejected: SectionRejected) {
            return rejected.result
        }
        return finish(messages, dropped, droppedBlank)
    }

    /** 把一行判成小标题角色（小写）；不是小标题返回 null。 */
    private fun headingRoleOf(line: String): String? {
        if (!line.startsWith("## ")) return null
        return line.substring(3).trim().lowercase().ifEmpty { null }
    }

    /** `flush()` 里的提前返回：用异常把「拒绝」从嵌套函数里带出来，避免可变标志位。 */
    private class SectionRejected(val result: Result.Rejected) : Exception()

    // ------------------------------------------------------------------ 共用

    /**
     * 追加一条消息，同时做两条上限判定。
     *
     * @return 非 null = 应当整份拒绝。
     */
    private fun append(
        messages: MutableList<ChatMessage>,
        role: Role,
        body: String,
        where: String,
    ): Result.Rejected? {
        if (body.length > MAX_MESSAGE_CHARS) {
            return Result.Rejected(
                Rejection.MESSAGE_TOO_LONG,
                where + ": chars=" + body.length + " cap=" + MAX_MESSAGE_CHARS,
            )
        }
        // Q3：空正文先跳过（见类头）—— 5000 条之后的空行不该被算成「第 5001 条」
        // 而误报 TOO_MANY_MESSAGES；条数上限只对**真正会物化的消息**计数。
        if (body.isBlank()) return null
        if (messages.size >= MAX_MESSAGES) {
            return Result.Rejected(
                Rejection.TOO_MANY_MESSAGES,
                where + ": count>" + MAX_MESSAGES,
            )
        }
        messages.add(ChatMessage(role = role, text = body))
        return null
    }

    private fun finish(messages: List<ChatMessage>, dropped: Int, droppedBlank: Int): Result =
        if (messages.isEmpty()) {
            Result.Rejected(
                Rejection.EMPTY,
                "no user/assistant messages (dropped tool roles: " + dropped +
                    ", dropped blank: " + droppedBlank + ")",
            )
        } else {
            Result.Ok(messages, dropped, droppedBlank)
        }

    private fun malformed(detail: String): Result = Result.Rejected(Rejection.MALFORMED, detail)
}
