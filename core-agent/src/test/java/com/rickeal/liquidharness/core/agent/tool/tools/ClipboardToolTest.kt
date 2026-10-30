package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.agent.tool.EffectAwareTool
import com.rickeal.liquidharness.core.agent.tool.ParamGatedTool
import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.model.ToolEffect
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `clipboard` 的参数级闸门（W2 判据 #4 原文）。
 *
 * 用 `ClipboardTool(null)` 构造（剪贴板不可用分支）：两个判定方法是纯函数，不需要
 * Android API；`invoke` 的失败分支也一并钉住，避免「没剪贴板就崩」。
 */
class ClipboardToolTest {

    private val tool = ClipboardTool(null)

    private val setJson = "{\"action\":\"set\",\"text\":\"secret\"}"
    private val getJson = "{\"action\":\"get\"}"

    @Test
    fun setRequiresConfirmation() {
        assertTrue(tool.requiresConfirmationFor(setJson))
    }

    @Test
    fun getDoesNotRequireConfirmation() {
        assertFalse(tool.requiresConfirmationFor(getJson))
    }

    @Test
    fun malformedArgumentsFailClosed() {
        assertTrue(tool.requiresConfirmationFor("not a json"))
        assertTrue(tool.requiresConfirmationFor("{}"))
        assertTrue(tool.requiresConfirmationFor("{\"action\":null}"))
    }

    @Test
    fun unknownActionFailsClosed() {
        assertTrue(tool.requiresConfirmationFor("{\"action\":\"delete\"}"))
    }

    @Test
    fun effectIsReadForGetAndWriteForSet() {
        assertEquals(ToolEffect.READ, tool.effectFor(getJson))
        assertEquals(ToolEffect.WRITE, tool.effectFor(setJson))
    }

    @Test
    fun effectFailsClosedOnMalformedArguments() {
        assertEquals(ToolEffect.WRITE, tool.effectFor("not a json"))
        assertEquals(ToolEffect.WRITE, tool.effectFor("{}"))
        assertEquals(ToolEffect.WRITE, tool.effectFor("{\"action\":\"delete\"}"))
    }

    @Test
    fun staticSpecEffectIsTheConservativeWrite() {
        assertEquals(ToolEffect.WRITE, tool.spec.effect)
        assertEquals("clipboard", tool.spec.name)
        assertFalse(tool.spec.requiresConfirmation, "confirmation must be decided per argument, not statically")
    }

    @Test
    fun implementsBothParameterGates() {
        val asTool: Tool = tool
        assertTrue(asTool is ParamGatedTool)
        assertTrue(asTool is EffectAwareTool)
    }

    @Test
    fun invokeWithoutClipboardFailsGracefully() {
        val result = runBlocking { tool.invoke(setJson) }
        assertFalse(result.ok)
        assertTrue(result.errorMessage!!.contains("unavailable"))
    }

    @Test
    fun unknownActionFailsAtInvoke() {
        val result = runBlocking { tool.invoke("{\"action\":\"delete\"}") }
        assertFalse(result.ok)
        assertTrue(result.errorMessage!!.contains("unknown action"))
    }
}
