package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec
import java.util.Locale

/**
 * `calculator`：四则运算 + 幂 + 括号。
 *
 * 纯函数、无 IO、无 `ToolContext` 依赖 —— 模型自己算数会出错，这个工具是最便宜、
 * 最常被调用的一类，也是「工具链真的通了」的最小验证面。
 */
class CalculatorTool : Tool {

    override val spec: ToolSpec = ToolSpec(
        name = "calculator",
        description = "Evaluate a math expression. Supports + - * / ^ and parentheses, e.g. (1+2)*3^2",
        parameters = listOf(
            ToolParameter("expression", ToolParamType.STRING, "expression to evaluate"),
        ),
        category = "utility",
        effect = ToolEffect.READ,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val expression = stringArg(argumentsJson, "expression")
        if (expression.isBlank()) {
            return ToolResult(name = spec.name, ok = false, errorMessage = "missing expression parameter")
        }
        return try {
            ToolResult(name = spec.name, ok = true, output = formatNumber(ExpressionEvaluator.evaluate(expression)))
        } catch (t: Throwable) {
            ToolResult(name = spec.name, ok = false, errorMessage = "cannot evaluate: " + t.message)
        }
    }

    private fun formatNumber(value: Double): String =
        if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            // 必须钉死 Locale.US：默认 Locale 在德语/法语区把小数点输出成逗号（3,3333333333），
            // 模型很可能把逗号读成千分位 —— 后续推理出错且全程无报错。
            "%.10f".format(Locale.US, value).trimEnd('0').trimEnd('.')
        }
}

/**
 * 极简递归下降求值器。刻意不引 JS 引擎 / 表达式库（零新依赖）。
 * 支持：`+ - * / ^`、一元正负、小数、括号。
 */
object ExpressionEvaluator {

    fun evaluate(expression: String): Double {
        val parser = Parser(expression)
        val value = parser.parseExpression()
        parser.skipWhitespace()
        if (!parser.isEnd()) throw IllegalArgumentException("unexpected token at position " + parser.position)
        return value
    }

    private class Parser(private val source: String) {
        var position: Int = 0
            private set

        fun isEnd(): Boolean = position >= source.length

        fun skipWhitespace() {
            while (position < source.length && source[position].isWhitespace()) position++
        }

        private fun peek(): Char? = if (isEnd()) null else source[position]

        private fun expect(char: Char) {
            skipWhitespace()
            if (peek() != char) throw IllegalArgumentException("expected '" + char + "' at position " + position)
            position++
        }

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '+' -> {
                        position++
                        value += parseTerm()
                    }
                    '-' -> {
                        position++
                        value -= parseTerm()
                    }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parsePower()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '*' -> {
                        position++
                        value *= parsePower()
                    }
                    '/' -> {
                        position++
                        value /= parsePower()
                    }
                    else -> return value
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            skipWhitespace()
            if (peek() == '^') {
                position++
                return Math.pow(base, parsePower())
            }
            return base
        }

        private fun parseUnary(): Double {
            skipWhitespace()
            return when (peek()) {
                '+' -> {
                    position++
                    parseUnary()
                }
                '-' -> {
                    position++
                    -parseUnary()
                }
                '(' -> {
                    position++
                    val value = parseExpression()
                    expect(')')
                    value
                }
                else -> parseNumber()
            }
        }

        private fun parseNumber(): Double {
            skipWhitespace()
            val start = position
            while (position < source.length && (source[position].isDigit() || source[position] == '.')) position++
            if (start == position) throw IllegalArgumentException("missing number at position " + position)
            val literal = source.substring(start, position)
            return literal.toDoubleOrNull() ?: throw IllegalArgumentException("invalid number: " + literal)
        }
    }
}
