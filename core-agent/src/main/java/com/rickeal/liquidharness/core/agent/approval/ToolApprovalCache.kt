package com.rickeal.liquidharness.core.agent.approval

import com.rickeal.liquidharness.core.model.AgentJson
import com.rickeal.liquidharness.core.model.ToolCall
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 会话级审批缓存：「同工具 + 同参数指纹」放行过一次后不再弹卡
 * （对应 [ToolApprovalDecision.AllowForSession]）。
 *
 * ## 只有一条写入口
 *
 * 唯一的写入路径是 [allowForSession]，它**只应由宿主在用户点了「本会话相同调用不再询问」
 * 时调用**。没有 `deny(...)`：拒绝永不缓存（fail-closed）—— 把拒绝记进缓存会让
 * 「用户误点一次拒绝」变成「本会话这个工具彻底不可用」，且模型换参重试也救不回来。
 *
 * ## 指纹为什么是 `name + "|" + sha256(规范化参数)`
 *
 * - **含工具名**：`file_read` 与 `file_write` 的 `{"path":"a"}` 是两种权限，不能互认；
 * - **规范化后再哈希**：`{ "path" : "a" }` 与 `{"path":"a"}` 是同一次调用，键序/空白差异
 *   不该让用户重弹一次卡；
 * - **哈希而不是原文**：`file_write` 的 `content` 可达 256K 字符，128 条原文键最坏
 *   占几十 MB；摘要定长 64 字符。解析失败退回原文哈希（摘要失败不能变成审批失败面）。
 *
 * ## 有界策略：满了**拒绝新增**，不淘汰旧条目
 *
 * 上限 [DEFAULT_MAX_ENTRIES]。超限时新授权不再入缓存（用户下一次同样的调用会再被问一次），
 * 而不是 LRU 淘汰。理由：淘汰是**静默撤销**一条用户已经做出的授权 —— 模型下一步原样
 * 重试时会突然重新弹卡，用户无法理解为什么「刚点过又问了」；拒绝新增的方向则是
 * fail-closed（多问一次，绝不多放一次）。且 128 条不同指纹已远超一个会话的正常工作集，
 * 真到这个量级说明模型在乱试，重新征求授权才是对的。
 *
 * ## 线程模型
 *
 * 写入方是 `ToolApprovalFlow`（在 run 协程内，多个会话的 run 可并发），读取方是同一批
 * 协程。读路径走 [ConcurrentHashMap] 无锁；写路径用 `synchronized` 把「查大小 + 插入」
 * 做成原子 —— 否则并发写能同时通过上限检查，突破 [maxEntries]。
 */
class ToolApprovalCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {

    private val allowed = ConcurrentHashMap.newKeySet<String>()
    private val writeLock = Any()

    /**
     * 记住「本会话内这个调用不再询问」。
     *
     * 缓存已满时**静默不记**（见类头「有界策略」）；这不是失败，只是回到「每次都问」的
     * 保守行为，故不返回错误也不记日志 —— 每次超限都写一条日志会把诊断环刷满。
     */
    fun allowForSession(call: ToolCall) {
        if (allowed.size >= maxEntries) return
        synchronized(writeLock) {
            if (allowed.size >= maxEntries) return
            allowed.add(fingerprint(call))
        }
    }

    /** 这个调用是否已被本会话放行。未授权 / 指纹不同一律 false（调用方继续走人在回路）。 */
    fun isAllowed(call: ToolCall): Boolean = allowed.contains(fingerprint(call))

    /** 会话结束 / 用户撤销授权时清空。 */
    fun clear() {
        allowed.clear()
    }

    /** 当前缓存条数（诊断与单测用）。 */
    fun size(): Int = allowed.size

    companion object {
        /** 缓存条数上限。理由见类头「有界策略」。 */
        const val DEFAULT_MAX_ENTRIES: Int = 128

        /** 调用指纹。见类头「指纹为什么是 …」。 */
        fun fingerprint(call: ToolCall): String = call.name + "|" + sha256Hex(normalize(call.argumentsJson))

        fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        /** 解析成功则规范化重编码（排序键、去空白）；失败退回原文。 */
        private fun normalize(argumentsJson: String): String = try {
            canonicalize(AgentJson.json.parseToJsonElement(argumentsJson)).toString()
        } catch (t: Throwable) {
            argumentsJson
        }

        /**
         * 键序归一：`kotlinx` 的 `JsonObject.toString()` **保留插入顺序**，只做 parse→toString
         * 消除不了 `{"a":1,"b":2}` 与 `{"b":2,"a":1}` 的差异 —— 那样模型换个键序就能
         * 让「同一次调用」变成需要重新授权的新调用（用户会看到同一张卡反复弹）。
         */
        private fun canonicalize(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { it.key to canonicalize(it.value) })
            is JsonArray -> JsonArray(element.map { canonicalize(it) })
            else -> element
        }
    }
}
