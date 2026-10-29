package com.rickeal.liquidharness.core.agent.loop

import com.rickeal.liquidharness.core.agent.AgentRequest
import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.model.EngineCapabilities
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 预置脚本引擎。**内核单测的唯一引擎替身**。
 *
 * 这是「新仓可以端到端验证」的直接证据（`00-brief.md` A3 理由 3）：旧仓判死
 * 「核心状态机不可端到端验证」的根因是 native 引擎无法 fake；新仓引擎边界是一个
 * `Flow<GenerationEvent>`，纯 JVM 就能把整条循环跑完，不需要设备、不需要网络。
 *
 * @param scripts 第 n 次 `generate` 依次 emit 的脚本。次数不够时按空脚本处理
 *   （= 空流，用于构造「空输出」路径）。
 */
class FakeEngine(
    override val endpointId: String = "fake-endpoint",
    private val scripts: List<List<GenerationEvent>>,
) : LlmEngine {

    /** `generate` 被调用（= 被 collect）的次数。重试判据靠它断言。 */
    var generateCount: Int = 0
        private set

    /** 最近一次请求，便于断言 system 段与工具面。 */
    var lastRequest: GenerationRequest? = null
        private set

    override suspend fun capabilities(): EngineCapabilities = EngineCapabilities(
        supportsTools = false,
        supportsReasoning = false,
        supportsVision = false,
        maxContextTokens = 32_768,
        maxOutputTokens = 4_096,
        label = "fake",
    )

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        lastRequest = request
        val script = scripts.getOrElse(generateCount) { emptyList() }
        generateCount += 1
        for (event in script) emit(event)
    }

    override suspend fun stop() = Unit

    companion object {
        fun textOnly(text: String): List<GenerationEvent> = listOf(
            GenerationEvent.TextDelta(text),
            GenerationEvent.Finished(com.rickeal.liquidharness.core.model.FinishReason.STOP),
        )
    }
}

/** 测试用的最小请求。 */
fun testRequest(userInput: String = "hello"): AgentRequest =
    AgentRequest(endpoint = RemoteEndpoint(id = "ep-1", name = "fake"), userInput = userInput)
