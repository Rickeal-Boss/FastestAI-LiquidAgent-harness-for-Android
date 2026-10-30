package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.model.ToolEffect
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `calculator`：求值器优先级/结合性 + 工具面（spec 与失败分支）。 */
class CalculatorToolTest {

    private val tool = CalculatorTool()

    private fun run(json: String) = runBlocking { tool.invoke(json) }

    @Test
    fun multiplicationBindsTighterThanAddition() {
        assertEquals(7.0, ExpressionEvaluator.evaluate("1+2*3"))
    }

    @Test
    fun parenthesesOverridePrecedence() {
        assertEquals(27.0, ExpressionEvaluator.evaluate("(1+2)*3^2"))
    }

    @Test
    fun powerIsRightAssociative() {
        assertEquals(512.0, ExpressionEvaluator.evaluate("2^3^2"))
    }

    @Test
    fun unaryMinusIsSupported() {
        assertEquals(2.0, ExpressionEvaluator.evaluate("-3+5"))
        assertEquals(-6.0, ExpressionEvaluator.evaluate("-(2*3)"))
    }

    @Test
    fun decimalsAndDivisionWork() {
        assertEquals(3.5, ExpressionEvaluator.evaluate("7/2"))
        assertEquals(3.0, ExpressionEvaluator.evaluate("1.5*2"))
    }

    @Test
    fun toolFormatsIntegralResultsWithoutDecimalPoint() {
        val result = run("{\"expression\":\"1+2*3\"}")
        assertTrue(result.ok)
        assertEquals("7", result.output)
    }

    @Test
    fun toolFormatsFractionalResultsWithDot() {
        val result = run("{\"expression\":\"7/2\"}")
        assertTrue(result.ok)
        assertEquals("3.5", result.output)
    }

    @Test
    fun blankExpressionFails() {
        val result = run("{\"expression\":\"  \"}")
        assertFalse(result.ok)
        assertTrue(result.errorMessage!!.contains("expression"))
    }

    @Test
    fun malformedExpressionFails() {
        val result = run("{\"expression\":\"1+\"}")
        assertFalse(result.ok)
        assertTrue(result.errorMessage!!.contains("cannot evaluate"))
    }

    @Test
    fun specIsReadOnlyAndNamed() {
        assertEquals("calculator", tool.spec.name)
        assertEquals(ToolEffect.READ, tool.spec.effect)
        assertFalse(tool.spec.requiresConfirmation)
    }
}
