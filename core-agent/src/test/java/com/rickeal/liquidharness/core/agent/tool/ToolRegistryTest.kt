package com.rickeal.liquidharness.core.agent.tool

import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSourceKind
import com.rickeal.liquidharness.core.model.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 工具注册中心单测。纯 JVM，不触 Android API。
 *
 * 重点覆盖 [ToolRegistry.registerContributed] 的**同名拒绝**：这条是 MCP 接入后
 * 防「名字抢注」的唯一机械防线（旧仓是 warn + 覆盖，等于没有防线）。
 */
class ToolRegistryTest {

    private fun tool(name: String, source: ToolSourceKind = ToolSourceKind.BUILTIN): Tool =
        FakeTool(ToolSpec(name = name, description = "test tool", source = source))

    @Test
    fun registerExposesSpecAndResolvesByName() {
        val registry = ToolRegistry()
        val result = registry.register(tool("file_read"))

        assertTrue(result is ToolRegistry.RegisterResult.Ok)
        assertEquals("file_read", registry.specs().single().name)
        assertEquals("file_read", registry.get("file_read")?.spec?.name)
    }

    @Test
    fun registerContributedRejectsDuplicateName() {
        val registry = ToolRegistry()
        val builtin = tool("file_read")
        registry.register(builtin)

        val result = registry.registerContributed(tool("file_read", ToolSourceKind.MCP))

        assertTrue(result is ToolRegistry.RegisterResult.RejectedDuplicate)
        assertEquals("file_read", result.name)
        // 关键：内置实现**没被顶掉**（旧仓会覆盖，那正是抢注面）。
        assertTrue(registry.get("file_read") === builtin)
        assertEquals(1, registry.size())
    }

    @Test
    fun registerContributedForcesConfirmationGate() {
        val registry = ToolRegistry()
        registry.registerContributed(tool("mcp_echo", ToolSourceKind.MCP))

        val spec = registry.get("mcp_echo")?.spec
        assertEquals(true, spec?.requiresConfirmation)
        assertEquals(ToolSourceKind.MCP, spec?.source)
    }

    @Test
    fun unregisterAlsoClearsDisabledFlag() {
        val registry = ToolRegistry()
        registry.register(tool("calculator"))
        registry.setEnabled("calculator", false)
        assertNull(registry.get("calculator"))

        registry.unregister("calculator")
        registry.register(tool("calculator"))

        // 同名工具重新注册后不得「继承」上一次的禁用状态。
        assertTrue(registry.isEnabled("calculator"))
        assertEquals(1, registry.specs().size)
    }

    @Test
    fun setEnabledHidesToolFromSpecs() {
        val registry = ToolRegistry()
        registry.register(tool("calculator"))
        registry.setEnabled("calculator", false)

        assertFalse(registry.isEnabled("calculator"))
        assertTrue(registry.specs().isEmpty())
        // 禁用只是不可见，不等于注销 —— 诊断页要能列出它。
        assertEquals(1, registry.all().size)
    }

    @Test
    fun unknownNameIsNotEnabled() {
        val registry = ToolRegistry()
        // fail-closed：没注册过的名字按「未启用」处理，不可能被调用。
        assertFalse(registry.isEnabled("not_registered"))
        assertNull(registry.get("not_registered"))
    }

    private class FakeTool(override val spec: ToolSpec) : Tool {
        override suspend fun invoke(argumentsJson: String): ToolResult =
            ToolResult(callId = "call", name = spec.name, ok = true, output = "ok")
    }
}
