package com.rickeal.liquidharness.core.agent.engine

import com.rickeal.liquidharness.core.model.EngineCapabilities
import com.rickeal.liquidharness.core.model.GenerationEvent
import com.rickeal.liquidharness.core.model.GenerationRequest
import kotlinx.coroutines.flow.Flow

/**
 * 一次生成的引擎端口。**全仓唯一引擎接口**：`core-engine` 的 `RemoteLlmEngine` 实现它，
 * `core-agent` 只认识这个接口 —— 内核因此不依赖 `core-net` / `core-engine`，
 * 可纯 JVM 单测（`FakeEngine` 返回预置 `Flow<GenerationEvent>` 即可）。
 *
 * ## 与 `docs/01-architecture.md` §3.1 的出入（有意为之，已申报）
 *
 * 文档把 [GenerationEvent] / [GenerationRequest] / [EngineCapabilities] 一起画在
 * `core-agent` 的 `engine/LlmEngine.kt` 里。实际落点是 **`core-model`**
 * （`core-model/.../GenerationEvent.kt`），本文件只留端口。理由：
 *
 * 1. 这四个类型是**跨模块契约**：`core-engine` 产出事件、`core-agent` 消费事件、
 *    `core-model` 的 `FinishReason` / `TokenUsage` 已经被它们引用。放在 `core-agent`
 *    会让 `core-engine` 反向依赖内核模块 —— 正是 `01-architecture.md` §一
 *    「三条不可逆的边」里禁止的方向。
 * 2. 放在 `core-model` 后，`core-agent` 的依赖面只有 `core-model` 一个，
 *    「内核不知道 HTTP」这条守卫（N7）不需要任何例外。
 *
 * ## 为什么没有 load / unload / close / evict / sessionDiagnostics / contextVersion
 *
 * 旧仓 `EngineContract.kt:222-275` 的 8 个成员里，那 6 个描述的是「进程内 native 引擎」的
 * 加载、释放与 KV cache 生命周期。远程引擎没有可加载之物，实现只能写成空函数 ——
 * 而空函数就是「空转的判据」（`00-brief.md` A4 理由 1：空转的判据比没有判据更危险）。
 * 尤其 `contextVersion` 的旧契约原文是「变化时必须关闭旧 Conversation 并全量重建 KV cache」，
 * 远程侧无 KV cache ⇒ 重新定义后恰好等于「无契约」（HTTP 无状态，每次都全量发）。
 * 结论是删掉，而不是留成 no-op。
 */
interface LlmEngine {

    /**
     * 端点标识（= `RemoteEndpoint.id`）。只用于日志与 UI 归属，**不参与路由** ——
     * 路由在 adapter 内由 `endpoint.kind` 决定。
     */
    val endpointId: String

    /**
     * 能力声明。由 adapter 的静态声明 + 端点配置派生，**不发网络请求**
     * （否则每轮生成前多一次往返，且离线时能力查询会失败）。
     */
    suspend fun capabilities(): EngineCapabilities

    /**
     * 冷流：`collect` 才开始发请求（不 collect 不花钱）。
     *
     * 取消语义（硬契约，四条都必须在实现侧成立）：
     *  1. 取消 `collect` ⇒ 立刻关闭底层连接（不允许僵尸流继续占着连接池）。
     *  2. 已 emit 的 `GenerationEvent.TextDelta` 由调用方保留 —— `Cancelled(partialText)` 要用。
     *  3. 已完整收到但尚未执行的 `ToolCall` **不得**被执行（执行侧另有 `checkActive()` 兜底）。
     *  4. 取消是幂等的；对已结束的流再取消是 no-op。
     */
    fun generate(request: GenerationRequest): Flow<GenerationEvent>

    /**
     * 主动停止。等价于取消当前流；无在途流时 no-op。
     *
     * 为什么需要它而不只靠 `collect` 取消：SSE 是冷流，但底层 `readLine()` 是阻塞调用 ——
     * 只取消协程不会让阻塞读立刻返回，要等 readTimeout（旧仓实测：用户点停止毫无反应）。
     * 实现必须主动 `disconnect()` / `call.cancel()`（`AGENTS.md` §五.5）。
     */
    suspend fun stop()
}
