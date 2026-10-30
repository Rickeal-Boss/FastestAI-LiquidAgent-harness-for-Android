package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.model.ToolEffect
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `datetime`：注入固定时钟后输出必须完全确定（含时区与 epochMillis 同源）。 */
class DateTimeToolTest {

    private val fixedMillis = 0L

    private fun tool() = DateTimeTool { fixedMillis }

    private fun run(json: String) = runBlocking { tool().invoke(json) }

    @Test
    fun formatsEpochWithExplicitZoneAndPattern() {
        val result = run("{\"timeZone\":\"UTC\",\"pattern\":\"yyyy-MM-dd HH:mm:ss\"}")
        assertTrue(result.ok)
        assertEquals("1970-01-01 00:00:00 (timeZone: UTC, epochMillis=0)", result.output)
    }

    @Test
    fun customPatternIsHonored() {
        val result = run("{\"timeZone\":\"UTC\",\"pattern\":\"yyyy/MM/dd\"}")
        assertTrue(result.ok)
        assertTrue(result.output.startsWith("1970/01/01"))
    }

    @Test
    fun invalidZoneFallsBackToSystemZone() {
        val result = run("{\"timeZone\":\"Not/AZone\",\"pattern\":\"yyyy\"}")
        assertTrue(result.ok)
        assertTrue(result.output.contains("epochMillis=0"))
    }

    @Test
    fun invalidPatternFallsBackToDefault() {
        // 未闭合的字符串字面量（尾部单引号）在 ofPattern 里必抛 IllegalArgumentException。
        val result = run("{\"timeZone\":\"UTC\",\"pattern\":\"yyyy-MM-dd '\"}")
        assertTrue(result.ok)
        assertTrue(result.output.contains("1970-01-01 00:00:00"), "got: " + result.output)
    }

    @Test
    fun epochMillisAndFormattedTextComeFromTheSameInstant() {
        val result = run("{\"timeZone\":\"UTC\"}")
        assertTrue(result.ok)
        assertTrue(result.output.contains("1970-01-01 00:00:00"))
        assertTrue(result.output.contains("epochMillis=0"))
    }

    @Test
    fun specIsNamedDatetimeAndReadOnly() {
        val spec = tool().spec
        assertEquals("datetime", spec.name)
        assertEquals(ToolEffect.READ, spec.effect)
    }
}
