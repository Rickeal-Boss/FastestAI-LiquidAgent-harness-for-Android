package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.AgentRequest
import com.rickeal.liquidharness.core.agent.approval.ToolApprovalHandler
import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.PolicyGatedTool
import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.model.AgentEvent
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec

/**
 * 工具循环测试的公共夹具：**可计数**的假工具 + 可直接构造的 [TurnContext]。
 *
 * 为什么要计数：审批闸门的所有判据都是「该工具**没有被调用**」。
 * 只断言事件（`ToolSkipped` / `ToolResultReceived`）是不够的 —— 一个先执行后补事件
 * 的实现能骗过事件断言，却骗不过调用计数。
 */
class FakeTool(
    override val spec: ToolSpec,
    private val behavior: (suspend (String) -> ToolResult)? = null,
) : Tool {

    /** `invoke` 被真正调用的次数（拒绝 / 跳过 / 校验失败都必须保持 0）。 */
    var invocations: Int = 0
        private set

    override suspend fun invoke(argumentsJson: String): ToolResult {
        invocations += 1
        return behavior?.invoke(argumentsJson)
            ?: ToolResult(name = spec.name, ok = true, output = "ok")
    }
}

/**
 * 策略闸门的假工具：`blockedBy` 由测试注入的纯函数决定。
 *
 * 为什么要它：`FakeTool` 只实现 `Tool`，断言不到「策略拦下 ⇒ 工具体一次都没被调用」——
 * 而「没被调用」正是策略闸门的唯一判据（先执行后补一条错误结果能骗过事件断言）。
 */
class FakePolicyGatedTool(
    override val spec: ToolSpec,
    private val gate: (AgentPolicy) -> String?,
) : PolicyGatedTool {

    var invocations: Int = 0
        private set

    override fun blockedBy(policy: AgentPolicy, argumentsJson: String): String? = gate(policy)

    override suspend fun invoke(argumentsJson: String): ToolResult {
        invocations += 1
        return ToolResult(name = spec.name, ok = true, output = "ok")
    }
}

/** 造一个 spec。默认 READ 效果（写档位 2000 字符会干扰截断类断言）。 */fun fakeSpec(
    name: String = "fake",
    dangerous: Boolean = false,
    requiresConfirmation: Boolean = false,
    effect: ToolEffect = ToolEffect.READ,
    parameters: List<ToolParameter> = emptyList(),
): ToolSpec = ToolSpec(
    name = name,
    description = "fake tool for tests",
    parameters = parameters,
    dangerous = dangerous,
    requiresConfirmation = requiresConfirmation,
    effect = effect,
)

/**
 * 直接构造一个 [TurnContext]。`emit` 收进 [events]，于是整条事件序列可断言。
 *
 * 不经过 `AgentRunner` 的用例用它（省掉 channelFlow / 取消收尾的噪声）；
 * 需要验证「取消如何收尾」的用例必须走 `AgentRunner.run`。
 */
fun testContext(
    events: MutableList<AgentEvent>,
    registered: List<Tool> = emptyList(),
    approval: ToolApprovalHandler? = null,
    policy: AgentPolicy = AgentPolicy(),
    engine: LlmEngine = FakeEngine(scripts = emptyList()),
    request: AgentRequest = testRequest(),
    state: RunState = RunState.forRequest(request),
    emitOverride: (suspend (AgentEvent) -> Unit)? = null,
): TurnContext {
    val registry = ToolRegistry()
    registered.forEach { registry.register(it) }
    return TurnContext(
        request = request,
        policy = policy,
        engine = engine,
        tools = registry,
        state = state,
        approval = approval,
        emit = emitOverride ?: { event -> events += event },
    )
}
