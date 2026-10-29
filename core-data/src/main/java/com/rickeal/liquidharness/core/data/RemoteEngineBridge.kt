package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.agent.engine.LlmEngine
import com.rickeal.liquidharness.core.engine.RemoteLlmEngine
import com.rickeal.liquidharness.core.model.EngineCapabilities
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import kotlinx.coroutines.flow.Flow

/**
 * 把 `core-engine` 的 [RemoteLlmEngine] 接到 `core-agent` 的 [LlmEngine] 端口上。
 *
 * ## 为什么这个桥必须存在，而且只能放在 `core-data`
 *
 * 依赖方向是 `core-agent` ← `core-engine`（引擎实现内核定义的端口），
 * 但 `core-engine` **不能** import `core-agent`（那就是反向依赖，`01-architecture.md` §一
 * 三条不可逆的边之一）。而 `RemoteLlmEngine` 目前是「签名已逐字对齐、但没 implements」
 * 的过渡态（见其类头 KDoc：W1 集成时再 implements）。
 *
 * 在 `core-engine` 里加 `: LlmEngine` 需要它依赖 `core-agent` —— 破坏模块边界；
 * 让 `AgentRunner` 直接吃 `RemoteLlmEngine` 又需要内核依赖引擎 —— 同样破坏边界。
 * 唯一合法的落点是**装配层**：`core-data` 同时依赖两者，用一个 20 行的委托把
 * 「实现细节」翻译成「端口」。
 *
 * 一旦 `RemoteLlmEngine` 直接 implements 了端口，本类即可删除（调用点改一行）。
 */
class RemoteEngineBridge(
    private val delegate: RemoteLlmEngine,
) : LlmEngine {

    override val endpointId: String get() = delegate.endpointId

    override suspend fun capabilities(): EngineCapabilities = delegate.capabilities()

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = delegate.generate(request)

    override suspend fun stop() = delegate.stop()
}
