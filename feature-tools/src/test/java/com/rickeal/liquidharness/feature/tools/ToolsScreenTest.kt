package com.rickeal.liquidharness.feature.tools

import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSourceKind
import com.rickeal.liquidharness.core.model.ToolSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具清单映射的单测：钉住「**数据来自真实注册表**」这条口径。
 *
 * 这一屏最容易出的错不是排版，而是「清单写死 / 少读一个标志」—— 用户看到的档位
 * 与实际闸门不一致，等于给了错误的授权预期。所以这里直接喂一个真 [ToolRegistry]，
 * 断言 [toolRows] 的每个字段都与 `ToolSpec` 逐项一致。
 */
class ToolsScreenTest {

    @Test
    fun toolRowsMirrorRegistryOrderAndFlags() {
        val registry = ToolRegistry()
        registry.register(
            FakeTool(
                ToolSpec(
                    name = "file_read",
                    description = "read a file",
                    category = "file",
                    effect = ToolEffect.READ,
                )
            )
        )
        registry.register(
            FakeTool(
                ToolSpec(
                    name = "file_write",
                    description = "write a file",
                    category = "file",
                    effect = ToolEffect.WRITE,
                    requiresConfirmation = true,
                    dangerous = true,
                )
            )
        )
        registry.setEnabled("file_write", false)

        val rows = toolRows(registry)

        // 顺序必须与 registry.all() 同源（按名字排序），UI 不自己再排一次。
        assertEquals(listOf("file_read", "file_write"), rows.map { it.name })
        val read = rows.first { it.name == "file_read" }
        assertEquals(ToolEffect.READ, read.effect)
        assertFalse(read.requiresConfirmation)
        assertFalse(read.dangerous)
        assertTrue(read.enabled)
        assertEquals("file", read.category)
        val write = rows.first { it.name == "file_write" }
        assertEquals(ToolEffect.WRITE, write.effect)
        assertTrue(write.requiresConfirmation)
        assertTrue(write.dangerous)
        assertFalse(write.enabled)
    }

    @Test
    fun contributedToolShowsForcedApproval() {
        val registry = ToolRegistry()
        // 第三方来源的 requiresConfirmation 由注册表强制置 true（同名拒绝 + 强制审批）。
        // UI 若显示 false，用户会以为它免审批 —— 这正是本用例要钉的错。
        registry.registerContributed(
            FakeTool(
                ToolSpec(
                    name = "mcp__demo__search",
                    description = "remote search",
                    category = "mcp",
                    effect = ToolEffect.READ,
                    requiresConfirmation = false,
                    source = ToolSourceKind.MCP,
                )
            )
        )

        val row = toolRows(registry).single()

        assertTrue(row.requiresConfirmation)
        assertEquals(ToolSourceKind.MCP, row.source)
        assertEquals(ToolEffect.READ, row.effect)
    }

    @Test
    fun emptyRegistryProducesNoRows() {
        assertTrue(toolRows(ToolRegistry()).isEmpty())
    }

    /** `invoke` 在本组用例里永远不会被调到 —— 它不属于映射逻辑。 */
    private class FakeTool(override val spec: ToolSpec) : Tool {
        override suspend fun invoke(argumentsJson: String): ToolResult = ToolResult(ok = true)
    }
}
