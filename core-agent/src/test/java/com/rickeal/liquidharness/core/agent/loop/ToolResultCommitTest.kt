package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.model.ToolCall
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 结果落库的两条判据：**按 effect 分档截断**（W2 判据 #9）与 **MODEL→TOOL 顺序**。
 *
 * 截断断言必须看 `truncated` 而不只看长度：只截断不标记，模型会以为自己看到了全文，
 * 于是不再续读 —— 那是静默截断，比不截断更糟。
 */
class ToolResultCommitTest {

    private val policy = AgentPolicy()
    private val commit = ToolResultCommit()

    private fun call(name: String = "read") = ToolCall(id = "c1", name = name, argumentsJson = "{}")

    @Test
    fun readOutputIsTruncatedToTheReadLimit() {
        val tool = FakeTool(fakeSpec(name = "read", effect = ToolEffect.READ))
        val huge = ToolResult(name = "read", ok = true, output = "a".repeat(50_000))

        val truncated = commit.truncate(call(), huge, tool, policy)

        assertEquals(policy.maxReadToolOutputChars, truncated.output.length)
        assertEquals(16_000, truncated.output.length)
        assertTrue(truncated.truncated)
    }

    @Test
    fun writeOutputIsTruncatedToTheWriteLimit() {
        val tool = FakeTool(fakeSpec(name = "write", effect = ToolEffect.WRITE))
        val huge = ToolResult(name = "write", ok = true, output = "b".repeat(5_000))

        val truncated = commit.truncate(call("write"), huge, tool, policy)

        assertEquals(policy.maxWriteToolOutputChars, truncated.output.length)
        assertTrue(truncated.truncated)
    }

    @Test
    fun shortOutputIsReturnedUnchanged() {
        val tool = FakeTool(fakeSpec(name = "read", effect = ToolEffect.READ))
        val small = ToolResult(name = "read", ok = true, output = "hello")

        val kept = commit.truncate(call(), small, tool, policy)

        assertEquals("hello", kept.output)
        assertFalse(kept.truncated)
    }
}
