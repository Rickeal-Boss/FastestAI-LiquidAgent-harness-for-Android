package com.rickeal.liquidharness.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
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

    // ---- 错误定位（只给行列号，不回显原文） ----

    @Test
    fun invalidReportsLineAndColumn() {
        val result = checkJsonFormat("{\n  \"a\": 1,\n  \"b\":\n}")
        assertTrue(result is JsonCheckResult.Invalid)
        val invalid = result as JsonCheckResult.Invalid
        assertTrue(invalid.line >= 1, "line=${invalid.line}")
        assertTrue(invalid.column >= 1, "column=${invalid.column}")
        assertTrue(invalid.message.isNotEmpty())
    }

    @Test
    fun multilineValidReportsNoError() {
        assertEquals(JsonCheckResult.Valid, checkJsonFormat("{\n  \"a\": 1\n}"))
    }
}
