package com.rickeal.liquidharness.core.engine.provider

import com.rickeal.liquidharness.core.model.EngineErrorKind
import com.rickeal.liquidharness.core.net.SseEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * W46 P1-1 判序结构性收口 —— 双向混淆样本集的参数化必红（判据冻结下的既有判据内容订正）。
 *
 * ## 样本集与复刻脚本共用同一份 JSON
 *
 * `scripts/fixtures/w46-error-classification-samples.json`（21 条：限流侧各带超窗特征词、
 * 超窗侧各带限流特征词、协议侧裸 limit）。`scripts/w46-classification-replay.py --legacy`
 * 在未修版本实跑 5 条 RED（R04/R05/R08/C07/C08），`--w46` 目标判序 21/21 GREEN。
 * 本类让 JVM 走同一个 [decodeOpenAiErrorFrame] 真路径，钉住 SSE 帧判序不回退。
 *
 * E5e/E5h/i/j/k/l/m/n 与 W44/W45 七条既有必红类不回退 —— 它们留在原测试类里独立守。
 *
 * ## fixture 路径
 *
 * Gradle 测试工作目录 = 模块根（core-engine）；W24-01 曾因 `java.io.File("src/main/…")`
 * 依赖工作目录而 flaky。这里先试仓根相对路径，再试模块根相对路径，两者皆无则 fail-fast
 * （不静默跳过 —— 空命中不是通过）。
 */
class OpenAiErrorFrameConfusionTest {

    private fun loadFixture(): JsonArray {
        // 从工作目录向上遍历找仓根（含 scripts/fixtures）—— 不假设 Gradle 测试 cwd 是模块根
        // 还是仓根（W24-01 曾因写死 `src/main/…` 相对路径 flaky）。
        var dir = File(".").absoluteFile
        var found: File? = null
        repeat(6) {
            val candidate = File(dir, "scripts/fixtures/w46-error-classification-samples.json")
            if (candidate.isFile) {
                found = candidate
                return@repeat
            }
            dir = dir.parentFile ?: return@repeat
        }
        val file = found ?: error("w46 fixture not found by upward search from ${File(".").absolutePath}")
        val obj = Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
        return obj["samples"]?.jsonArray ?: error("fixture has no 'samples' array")
    }

    @Test
    fun bidirectionalConfusionSamplesAreClassifiedToEvidenceAuthority() {
        val samples = loadFixture()
        assertEquals(25, samples.size, "样本集条数与对账不一致（与复刻脚本共用 JSON；W47 判序最后一轮 21→25）")
        val failed = mutableListOf<String>()
        samples.forEach { el ->
            val o: JsonObject = el.jsonObject
            val id = o["id"]!!.jsonPrimitive.content
            val message = o["message"]!!.jsonPrimitive.content
            val expected = runCatching { EngineErrorKind.valueOf(o["expected"]!!.jsonPrimitive.content) }
                .getOrElse { error("bad expected in sample $id") }
            val payload = """{"error":{"message":"$message"}}"""
            val event = SseEvent(data = payload)
            val result = decodeOpenAiErrorFrame(event) { null }
            if (result == null || result.kind != expected) {
                failed.add("$id: expected=$expected got=${result?.kind} message=$message")
            }
            // W48 判序范式：message 兜底支的裁决必须带 `frame:*` 证据标签（供真机诊断）。
            if (result != null && !result.evidenceTag.startsWith("frame:")) {
                failed.add("$id: evidenceTag missing frame: prefix got=${result.evidenceTag}")
            }
        }
        assertTrue(failed.isEmpty(), "双向混淆样本判序偏差：\n${failed.joinToString("\n")}")
    }

    /**
     * W48 判序范式必红：结构化 `type` 是唯一权威，message 词表只是兜底 ——
     * 结构化自声明与超窗/限流措辞**冲突**时必须按结构化裁决，并携带 `frame:structured-*`
     * 证据标签。注入「回退到词表优先」（message 判序提到结构化之前）⇒ 本条 RED
     * （例：`type=rate_limit_error` + `maximum context length exceeded` 被词表判成
     * CONTEXT_OVERFLOW）。词表自 W48 冻结，此后反例只改结构化映射与标签。
     */
    @Test
    fun structuredTypeOutranksOverflowWordingWithEvidenceTag() {
        val cases = listOf(
            Triple(
                """{"error":{"type":"rate_limit_error","message":"maximum context length exceeded"}}""",
                EngineErrorKind.RATE_LIMIT,
                "frame:structured-rate",
            ),
            Triple(
                """{"error":{"type":"server_error","message":"context length exceeded"}}""",
                EngineErrorKind.SERVER,
                "frame:structured-server",
            ),
            Triple(
                """{"error":{"type":"context_length_exceeded","message":"rate limit: too many tokens"}}""",
                EngineErrorKind.CONTEXT_OVERFLOW,
                "frame:structured-context",
            ),
        )
        cases.forEach { (payload, expectedKind, expectedTag) ->
            val result = decodeOpenAiErrorFrame(SseEvent(data = payload)) { null }
            assertEquals(expectedKind, result?.kind, "结构化权威不得被 message 词表改判：$payload")
            assertEquals(expectedTag, result?.evidenceTag, "证据标签必须指到结构化支：$payload")
        }
    }

    @Test
    fun w46NewBoundarySamplesMustNotRegress() {
        // 4 条派单卡指定新必红 + 外审 P1-1 表格的 C08，逐条具名钉住（失败时能指到具体样本）。
        val cases = listOf(
            "tokens per minute limit exceeded" to EngineErrorKind.RATE_LIMIT,
            "tpm exceeded: token budget" to EngineErrorKind.RATE_LIMIT,
            "maximum token count exceeded (quota: 100000)" to EngineErrorKind.CONTEXT_OVERFLOW,
            "token length exceeded: slow down your requests" to EngineErrorKind.CONTEXT_OVERFLOW,
            "prompt too long: rate limit exceeded" to EngineErrorKind.RATE_LIMIT,
            // W47（P0-2 判序最后一轮）：context 恢复无条件强窗口词，4 条真超窗自带强限流词
            // 不得回退成 RATE_LIMIT；prompt 让位语义保留（第 5 条偏限流仍判 RATE_LIMIT）。
            "maximum context length exceeded (rate limit: 100000 tokens)" to EngineErrorKind.CONTEXT_OVERFLOW,
            "context length exceeded: rate limit 100000 tokens" to EngineErrorKind.CONTEXT_OVERFLOW,
            "maximum context length exceeded, tpm exceeded" to EngineErrorKind.CONTEXT_OVERFLOW,
            "context length exceeded: 429" to EngineErrorKind.CONTEXT_OVERFLOW,
            "prompt too long (requests per minute cap)" to EngineErrorKind.RATE_LIMIT,
        )
        cases.forEach { (message, expected) ->
            val result = decodeOpenAiErrorFrame(
                SseEvent(data = """{"error":{"message":"$message"}}""")
            ) { null }
            assertEquals(expected, result?.kind, "W46 边界必红：$message")
        }
    }
}
