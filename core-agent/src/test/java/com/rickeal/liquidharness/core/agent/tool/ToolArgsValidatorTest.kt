package com.rickeal.liquidharness.core.agent.tool

import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 参数校验器真值表。锁住「宁可放过，不可死锁」的边界，以及「错误必须能定向修复」：
 * [ToolArgsValidator.Result.Invalid.reason] 必须带参数名 + 原始参数片段。
 */
class ToolArgsValidatorTest {

    private fun spec(vararg params: ToolParameter): ToolSpec =
        ToolSpec(name = "t", description = "test", parameters = params.toList())

    private fun reasonOf(spec: ToolSpec, json: String): String {
        val result = ToolArgsValidator.validate(spec, json)
        assertTrue(result is ToolArgsValidator.Result.Invalid, "expected Invalid for: " + json)
        return result.reason
    }

    @Test
    fun validArgumentsReturnParsedObject() {
        val result = ToolArgsValidator.validate(spec(ToolParameter("path")), "{\"path\":\"a.txt\"}")
        assertTrue(result is ToolArgsValidator.Result.Valid)
        assertEquals("a.txt", result.arguments["path"].toString().trim('"'))
    }

    @Test
    fun toolWithoutDeclaredParametersAcceptsAnything() {
        assertTrue(ToolArgsValidator.validate(spec(), "{\"x\":1}") is ToolArgsValidator.Result.Valid)
        assertTrue(ToolArgsValidator.validate(spec(), "not json at all") is ToolArgsValidator.Result.Valid)
    }

    @Test
    fun undeclaredExtraKeysAreIgnored() {
        val result = ToolArgsValidator.validate(
            spec(ToolParameter("path")),
            "{\"path\":\"a\",\"note\":\"explain\",\"extra\":123}",
        )
        assertTrue(result is ToolArgsValidator.Result.Valid)
    }

    @Test
    fun optionalParameterMayBeMissing() {
        val s = spec(ToolParameter("required_arg"), ToolParameter("opt", required = false))
        assertTrue(ToolArgsValidator.validate(s, "{\"required_arg\":\"v\"}") is ToolArgsValidator.Result.Valid)
    }

    @Test
    fun requiredParameterMissingIsInvalid() {
        val reason = reasonOf(spec(ToolParameter("path")), "{}")
        assertTrue(reason.contains("\"path\""))
        assertTrue(reason.contains("required"))
        assertTrue(reason.contains("missing"))
    }

    @Test
    fun explicitNullIsTreatedAsMissing() {
        val reason = reasonOf(spec(ToolParameter("path")), "{\"path\":null}")
        assertTrue(reason.contains("required"))
    }

    @Test
    fun emptyArgumentsStringIsTreatedAsEmptyObject() {
        val reason = reasonOf(spec(ToolParameter("path")), "   ")
        assertTrue(reason.contains("missing"))
    }

    @Test
    fun stringParameterRejectsNumber() {
        val reason = reasonOf(spec(ToolParameter("name", ToolParamType.STRING)), "{\"name\":123}")
        assertTrue(reason.contains("expects string"))
        assertTrue(reason.contains("got number"))
    }

    @Test
    fun integerRejectsFloat() {
        val reason = reasonOf(spec(ToolParameter("n", ToolParamType.INTEGER)), "{\"n\":5.5}")
        assertTrue(reason.contains("expects integer"))
    }

    @Test
    fun integerAcceptsNumericString() {
        // 与工具侧 intArg 的宽容解析同口径：校验层不得比实现更严格。
        assertTrue(
            ToolArgsValidator.validate(spec(ToolParameter("n", ToolParamType.INTEGER)), "{\"n\":\"42\"}") is
                ToolArgsValidator.Result.Valid,
        )
        val reason = reasonOf(spec(ToolParameter("n", ToolParamType.INTEGER)), "{\"n\":\"5.5\"}")
        assertTrue(reason.contains("expects integer"))
    }

    @Test
    fun numberAcceptsIntegerFloatAndNumericString() {
        val s = spec(ToolParameter("n", ToolParamType.NUMBER))
        assertTrue(ToolArgsValidator.validate(s, "{\"n\":5}") is ToolArgsValidator.Result.Valid)
        assertTrue(ToolArgsValidator.validate(s, "{\"n\":1.5}") is ToolArgsValidator.Result.Valid)
        assertTrue(ToolArgsValidator.validate(s, "{\"n\":\"5\"}") is ToolArgsValidator.Result.Valid)
        assertTrue(reasonOf(s, "{\"n\":\"abc\"}").contains("expects number"))
    }

    @Test
    fun booleanRejectsStringLiteral() {
        val reason = reasonOf(spec(ToolParameter("flag", ToolParamType.BOOLEAN)), "{\"flag\":\"true\"}")
        assertTrue(reason.contains("expects boolean"))
        assertTrue(reason.contains("got string"))
    }

    @Test
    fun arrayAndObjectAreNotInterchangeable() {
        assertTrue(
            reasonOf(spec(ToolParameter("items", ToolParamType.ARRAY)), "{\"items\":{}}")
                .contains("expects array"),
        )
        assertTrue(
            reasonOf(spec(ToolParameter("cfg", ToolParamType.OBJECT)), "{\"cfg\":[]}")
                .contains("expects object"),
        )
    }

    @Test
    fun enumOutOfRangeIsInvalid() {
        val s = spec(ToolParameter("mode", ToolParamType.STRING, enumValues = listOf("a", "b")))
        assertTrue(ToolArgsValidator.validate(s, "{\"mode\":\"a\"}") is ToolArgsValidator.Result.Valid)
        val reason = reasonOf(s, "{\"mode\":\"c\"}")
        assertTrue(reason.contains("one of [a, b]"))
    }

    @Test
    fun malformedJsonReportsTopLevelProblemWithRawSnippet() {
        val reason = reasonOf(spec(ToolParameter("path")), "{\"path\": \"unterminated")
        assertTrue(reason.contains("not valid JSON"))
        assertTrue(reason.contains("raw arguments"))
        assertTrue(reason.contains("unterminated"))
    }

    @Test
    fun rootThatIsNotAnObjectIsInvalid() {
        val reason = reasonOf(spec(ToolParameter("path")), "[1,2,3]")
        assertTrue(reason.contains("must be a JSON object"))
        assertTrue(reason.contains("got array"))
    }

    @Test
    fun reasonCarriesRawArgumentsForModelSelfRepair() {
        val reason = reasonOf(spec(ToolParameter("path")), "{\"path\": 42}")
        assertTrue(reason.contains("\"path\": 42"))
        assertTrue(reason.contains("call the same tool again"))
    }
}
