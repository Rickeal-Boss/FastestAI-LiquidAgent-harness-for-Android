package com.rickeal.liquidharness.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 模型返回 JSON 校验的纯函数判据。
 *
 * 🔴 本文件最关键的一组是「lenient 会判绿、严格必须判红」—— `AgentJson.json` 是
 * `isLenient = true`，若复用它则「格式检查」整个功能假绿。所以尾逗号 / 单引号 /
 * 未加引号键这三条**必须红**，它们就是这个功能存在的理由。
 *
 * 两态齐全（AGENTS.md §二.3）：`allowFence` 的真 / 假各有一条用例，证明围栏容忍是
 * 真实开关而非恒真。
 */
class JsonFormatCheckTest {

    // ---- 判据自身先验证的两条锚 ----

    @Test
    fun validObjectAnchor() {
        assertEquals(JsonCheckResult.Valid, checkJsonFormat("{\"a\":1}"))
    }

    @Test
    fun invalidObjectAnchor() {
        assertTrue(checkJsonFormat("{\"a\":}") is JsonCheckResult.Invalid, "缺值必须判非法")
    }

    // ---- lenient 会放行、严格必须拒 ----

    @Test
    fun trailingCommaRejected() {
        assertTrue(checkJsonFormat("{\"a\":1,}") is JsonCheckResult.Invalid, "尾逗号：AgentJson 会判绿")
    }

    @Test
    fun unquotedKeyRejected() {
        assertTrue(checkJsonFormat("{a:1}") is JsonCheckResult.Invalid, "未加引号键：AgentJson 会判绿")
    }

    @Test
    fun singleQuotedValueRejected() {
        assertTrue(checkJsonFormat("{'a':1}") is JsonCheckResult.Invalid, "单引号：AgentJson 会判绿")
    }

    @Test
    fun truncatedInputRejected() {
        assertTrue(checkJsonFormat("{\"a\": [1, 2") is JsonCheckResult.Invalid)
    }

    @Test
    fun emptyAndBlankRejected() {
        assertTrue(checkJsonFormat("") is JsonCheckResult.Invalid)
        assertTrue(checkJsonFormat("   \n  ") is JsonCheckResult.Invalid)
    }

    // ---- 合法形态 ----

    @Test
    fun arrayScalarNullAndNestedValid() {
        assertEquals(JsonCheckResult.Valid, checkJsonFormat("[1,2,3]"))
        assertEquals(JsonCheckResult.Valid, checkJsonFormat("42"))
        assertEquals(JsonCheckResult.Valid, checkJsonFormat("\"text\""))
        assertEquals(JsonCheckResult.Valid, checkJsonFormat("null"))
        assertEquals(JsonCheckResult.Valid, checkJsonFormat("{\"a\":{\"b\":[1,{\"c\":true}]}}"))
    }

    @Test
    fun bomAndSurroundingWhitespaceTolerated() {
        assertEquals(JsonCheckResult.Valid, checkJsonFormat("\uFEFF  {\"a\":1}  "))
    }

    // ---- 围栏两态（真实开关，非恒真） ----

    @Test
    fun fenceToleratedWhenAllowed() {
        assertEquals(JsonCheckResult.Valid, checkJsonFormat("```json\n{\"a\":1}\n```"))
        assertEquals(JsonCheckResult.Valid, checkJsonFormat("```\n{\"a\":1}\n```"), "无语言标签也容忍")
    }

    @Test
    fun fenceRejectedWhenNotAllowed() {
        val result = checkJsonFormat("```json\n{\"a\":1}\n```", allowFence = false)
        assertTrue(result is JsonCheckResult.Invalid, "allowFence=false 时带围栏必须判非法")
    }

    // ---- kotlinx 四件（W9-00）：红态样本先证明旧实现必红 ----

    @Test
    fun invalidMessageNeverEchoesInputFragment() {
        // kotlinx① 红态样本：尾逗号必被严格实例拒绝，且原文含 SECRET-MARKER；
        // 旧实现 take(120) 会把 `JSON input: {...}` 整段回显上屏。
        // 判据自身先验证：模拟 kotlinx 的 `JSON input:` 变异样本必须能被这条断言抓到，
        // 否则「不含 SECRET-MARKER」就是恒绿断言。
        val kotlinxRaw = "Unexpected JSON token at offset 20: Trailing comma JSON input: {\"a\": \"SECRET-MARKER\",}"
        assertTrue(kotlinxRaw.contains("SECRET-MARKER"))
        val result = checkJsonFormat("{\"a\": \"SECRET-MARKER\",}")
        assertTrue(result is JsonCheckResult.Invalid)
        val invalid = result as JsonCheckResult.Invalid
        assertFalse(invalid.message.contains("SECRET-MARKER"), "message=" + invalid.message)
        assertFalse(invalid.message.contains("JSON input:"), "message=" + invalid.message)
        assertTrue(invalid.message.isNotEmpty())
    }

    @Test
    fun strictParserRejectsTrailingValuesWithoutVacuousWhen() {
        // kotlinx② 两样本：严格实例自身拒绝「非单个值」；恒真空 when 从未挡过这一层。
        assertEquals(JsonCheckResult.Valid, checkJsonFormat("{\"a\":1}"))
        assertTrue(checkJsonFormat("{\"a\":1} {\"b\":2}") is JsonCheckResult.Invalid)
    }

    @Test
    fun fenceInsideStringValueIsNotMistakenForEndFence() {
        // kotlinx③ 红态样本：旧实现 lastIndexOf("```") 截到字符串值里的围栏 ⇒ 误裁。
        assertEquals(
            JsonCheckResult.Valid,
            checkJsonFormat("```json\n{\"a\": \"x ``` y\"}\n```"),
        )
    }

    // ---- 错误定位：精确行列号（kotlinx④）+ 不含原文片段 ----

    @Test
    fun invalidReportsLineAndColumn() {
        val result = checkJsonFormat("{\n  \"a\": 1,\n  \"b\":\n}")
        assertTrue(result is JsonCheckResult.Invalid)
        val invalid = result as JsonCheckResult.Invalid
        assertEquals(4, invalid.line, "line=${invalid.line}")
        assertEquals(1, invalid.column, "column=${invalid.column}")
        assertFalse(invalid.message.contains("\"b\""), "message 不得回显原文片段")
    }

    @Test
    fun multilineValidReportsNoError() {
        assertEquals(JsonCheckResult.Valid, checkJsonFormat("{\n  \"a\": 1\n}"))
    }
}
