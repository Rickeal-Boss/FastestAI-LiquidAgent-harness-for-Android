package com.rickeal.liquidharness.core.net

import java.io.InputStream

/**
 * 一帧 SSE。
 *
 * [data] 已按规范把多行 `data:` 用 `\n` 拼好。**它已经是解码后的字符串** ——
 * 解码时机是「整帧字节齐全之后」，这是本类存在的全部理由。
 */
data class SseEvent(
    val data: String,
    val event: String? = null,
    val id: String? = null,
    val retryMillis: Long? = null,
)

/**
 * SSE 字节级分帧器。
 *
 * ## 为什么必须字节级
 *
 * 一个汉字在 UTF-8 里是 3 字节。TCP chunk 边界是任意的，所以
 * 「中」「文」之间被切开是**常态而不是异常**。若用 `BufferedReader.readLine()`
 * 逐行解码，被切开的半个字符会被解码器替换成 U+FFFD（`�`）且**不可恢复** ——
 * 上层看到的是「模型输出了乱码」，而实际是传输层缺陷。
 * 因此本类的顺序是：**先按字节切帧 → 整帧齐全 → 再一次性 UTF-8 解码**。
 *
 * ## 支持的分帧形态
 *
 * - `\n\n`、`\r\n\r\n`、`\r\n\n`、`\n\r\n`（混合换行）、`\r\r`；
 * - 帧内字段行 `field: value`，冒号后**一个**可选空格按规范剥掉；
 * - `data:` 多行 ⇒ 用 `\n` 拼接（不是空格，也不是直接相连）；
 * - `:` 开头的行是注释 / 心跳，整行丢弃；
 * - 未知字段忽略（规范要求）；
 * - 流末尾没有空行时，[finish] 把残留字节当最后一帧处理。
 *
 * ## `[DONE]` 之后
 *
 * [closeOnDone] 为 true（默认）时，收到 `data: [DONE]` 后立即置 [isTerminated]，
 * **之后喂进来的任何字节都被丢弃且不抛异常**。现实动因：部分代理在 `[DONE]`
 * 之后仍会发 keep-alive 或半截 JSON，若继续解析会把垃圾当事件。
 *
 * 非线程安全：一个实例只服务一条流，在同一个线程上 feed。
 */
class SseEventParser(private val closeOnDone: Boolean = true) {

    private var buf: ByteArray = ByteArray(INITIAL_CAPACITY)
    private var size: Int = 0

    var isTerminated: Boolean = false
        private set

    /**
     * 喂入一段字节，返回**因此凑齐**的所有完整帧。
     * @param length 默认整块；允许调用方复用大缓冲区只喂有效长度。
     */
    fun feed(chunk: ByteArray, offset: Int = 0, length: Int = chunk.size): List<SseEvent> {
        if (isTerminated || length <= 0) return emptyList()
        append(chunk, offset, length)
        val out = ArrayList<SseEvent>(4)
        while (true) {
            val frame = extractFrame() ?: break
            val event = decodeFrame(frame) ?: continue
            out.add(event)
            if (closeOnDone && event.data.trim() == DONE_MARKER) {
                isTerminated = true
                size = 0
                break
            }
        }
        return out
    }

    /**
     * 流结束时调用：把残留字节当最后一帧（服务端没发结尾空行的情况）。
     * 已终止或缓冲为空 ⇒ 返回空列表。
     */
    fun finish(): List<SseEvent> {
        if (isTerminated || size == 0) return emptyList()
        val frame = buf.copyOfRange(0, size)
        size = 0
        val event = decodeFrame(frame) ?: return emptyList()
        val out = ArrayList<SseEvent>(1)
        out.add(event)
        if (closeOnDone && event.data.trim() == DONE_MARKER) isTerminated = true
        return out
    }

    // ---------------------------------------------------------------- 分帧

    /**
     * 从缓冲头部切出一帧。
     *
     * 扫到「空行」即一帧结束：空行 = 当前行长度为 0，即换行符紧跟在上一行的换行符之后。
     * 帧内容不含任何行尾换行符。
     *
     * @return 帧字节；缓冲里还没有完整帧（或尾部是孤立的 `\r`，可能是 `\r\n` 的前半）返回 null。
     */
    private fun extractFrame(): ByteArray? {
        var lineStart = 0
        var prevTerminatorLength = 0
        var i = 0
        while (i < size) {
            val terminatorLength: Int = when (buf[i].toInt()) {
                CR -> {
                    // 孤立 \r 在缓冲末尾：无法判断是不是 \r\n 的前半，等下一块。
                    if (i + 1 >= size) return null
                    if (buf[i + 1].toInt() == LF) 2 else 1
                }
                LF -> 1
                else -> {
                    i++
                    continue
                }
            }
            if (i == lineStart) {
                // 空行 ⇒ 帧边界。帧尾要退掉上一行的换行符。
                val frameEnd = (lineStart - prevTerminatorLength).coerceAtLeast(0)
                val frame = buf.copyOfRange(0, frameEnd)
                consume(i + terminatorLength)
                return frame
            }
            prevTerminatorLength = terminatorLength
            i += terminatorLength
            lineStart = i
        }
        return null
    }

    // ---------------------------------------------------------------- 解码

    private fun decodeFrame(frame: ByteArray): SseEvent? {
        if (frame.isEmpty()) return null
        // 🔴 整帧齐全之后才解码 —— 这是跨 chunk UTF-8 不被截断的唯一保证点。
        val text = String(frame, Charsets.UTF_8)
        var eventName: String? = null
        var id: String? = null
        var retryMillis: Long? = null
        val data = StringBuilder()
        var hasData = false
        for (line in text.split("\r\n", "\n", "\r")) {
            if (line.isEmpty()) continue
            if (line[0] == ':') continue // 注释 / 心跳
            val colon = line.indexOf(':')
            val field: String
            var value: String
            if (colon < 0) {
                field = line
                value = ""
            } else {
                field = line.substring(0, colon)
                value = line.substring(colon + 1)
                if (value.startsWith(" ")) value = value.substring(1)
            }
            when (field) {
                "data" -> {
                    if (hasData) data.append('\n')
                    data.append(value)
                    hasData = true
                }
                "event" -> eventName = value
                // 规范：id 里含 NUL 时忽略该字段。
                "id" -> if (!value.contains('\u0000')) id = value
                "retry" -> retryMillis = value.trim().toLongOrNull()
                else -> Unit
            }
        }
        if (!hasData) return null
        return SseEvent(data = data.toString(), event = eventName, id = id, retryMillis = retryMillis)
    }

    // ---------------------------------------------------------------- 缓冲

    private fun append(chunk: ByteArray, offset: Int, length: Int) {
        if (size + length > buf.size) {
            var capacity = buf.size
            while (capacity < size + length) capacity = capacity shl 1
            buf = buf.copyOf(capacity)
        }
        System.arraycopy(chunk, offset, buf, size, length)
        size += length
    }

    private fun consume(count: Int) {
        if (count <= 0) return
        if (count >= size) {
            size = 0
            return
        }
        System.arraycopy(buf, count, buf, 0, size - count)
        size -= count
    }

    companion object {
        const val DONE_MARKER: String = "[DONE]"

        private const val INITIAL_CAPACITY = 8 * 1024
        private const val CR = 0x0D
        private const val LF = 0x0A

        /**
         * 便捷入口：从一个输入流持续解析。
         *
         * **不关闭** [input] —— 流的生命周期归调用方（`SseStream.close()`）。
         * 序列在 EOF 或 `[DONE]` 时结束；若最后没有空行，用 [finish] 补一帧。
         */
        fun parse(input: InputStream, closeOnDone: Boolean = true): Sequence<SseEvent> = sequence {
            val parser = SseEventParser(closeOnDone)
            val chunk = ByteArray(8 * 1024)
            while (!parser.isTerminated) {
                val read = input.read(chunk)
                if (read < 0) break
                for (event in parser.feed(chunk, 0, read)) yield(event)
            }
            if (!parser.isTerminated) {
                for (event in parser.finish()) yield(event)
            }
        }
    }
}
