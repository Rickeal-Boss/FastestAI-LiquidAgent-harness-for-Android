package com.rickeal.liquidharness.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 400 超窗学到的真实窗口写回（W6 收口）的判据。
 *
 * ## 为什么判据钉在这个类上
 *
 * 主判据是「`Failed(hint=1_000_000)` ⇒ `recordContextLimit` 被调用**一次**且参数正确」。
 * `ChatViewModel` 在 JVM 上无法实例化（`viewModelScope` + `AppContainer`），
 * 所以写回逻辑抽成 [ContextLimitWriteback]：它只依赖一个注入的
 * `suspend (String, Int) -> Unit`，单测用一个记账 lambda 就能精确断言「几次、什么参数」。
 * 这条判据**不是**「看起来调了」—— 断言的是调用次数与逐参数相等。
 */
class ContextLimitWritebackTest {

    private class Recorder {
        val calls = mutableListOf<Pair<String, Int>>()
        val sink: suspend (String, Int) -> Unit = { id, verified -> calls.add(id to verified) }
    }

    @Test
    fun writesBackHintOnceWithExactParams() {
        val recorder = Recorder()
        runBlocking { ContextLimitWriteback(recorder.sink).offer("ep-1", 1_000_000) }

        assertEquals(1, recorder.calls.size)
        assertEquals("ep-1" to 1_000_000, recorder.calls.single())
    }

    /**
     * `emitBreaker` 的 `context_overflow_prevented` **不带** hint（本地拦住、请求没发出去，
     * 没有服务端真值）⇒ 必须一次都不写。这条用例防的是「为了别的地方方便而猜一个值」。
     */
    @Test
    fun skipsWhenHintIsMissing() {
        val recorder = Recorder()
        runBlocking { ContextLimitWriteback(recorder.sink).offer("ep-1", null) }

        assertTrue(recorder.calls.isEmpty())
    }

    /** 拿不到端点 id（run 期间端点被删）⇒ 什么都不做，不抛。 */
    @Test
    fun skipsWhenEndpointIdIsMissing() {
        val recorder = Recorder()
        runBlocking { ContextLimitWriteback(recorder.sink).offer(null, 1_000_000) }
        runBlocking { ContextLimitWriteback(recorder.sink).offer("  ", 1_000_000) }

        assertTrue(recorder.calls.isEmpty())
    }

    /** 网关噪声（`<= 0`）不许写进预算。 */
    @Test
    fun skipsNonPositiveHint() {
        val recorder = Recorder()
        runBlocking { ContextLimitWriteback(recorder.sink).offer("ep-1", 0) }
        runBlocking { ContextLimitWriteback(recorder.sink).offer("ep-1", -1) }

        assertTrue(recorder.calls.isEmpty())
    }

    /**
     * 写回失败**静默**：它不该把「这次请求为什么失败」的错误面板换成「记账失败」。
     * 断言的是「不抛」——调用方在 `viewModelScope.launch` 里，抛出去会崩进程。
     */
    @Test
    fun writeFailureIsSwallowed() {
        var attempts = 0
        val failing: suspend (String, Int) -> Unit = { _, _ ->
            attempts++
            throw IllegalStateException("disk full")
        }

        runBlocking { ContextLimitWriteback(failing).offer("ep-1", 1_000_000) }

        assertEquals(1, attempts)
    }

    /**
     * `record` 抛 `CancellationException` ⇒ **必须原样重抛**（不是静默吞掉）。
     *
     * ## 这条钉的是哪一行
     *
     * `offer` 里那句 `catch (cancelled: CancellationException) { throw cancelled }` 前置。
     * 没有它，下一个人一定会把整个 try/catch「简化」成 `runCatching { record(...) }` ——
     * 那是本仓明令禁止的形态：吞掉取消会让 Job 从 cancelled 变 completed
     * （`AGENTS.md` §五.4），表现为「用户点了停止，但 run 看起来正常结束」。
     *
     * 断言用**引用同一性**（`thrown === cancelled`）而不是「是个 CancellationException」：
     * 后者会被 `runBlocking` 自己抛出的取消异常蒙混过关，前者才能证明原对象被透传。
     */
    @Test
    fun cancellationIsRethrownNotSwallowed() {
        val cancelled = CancellationException("run cancelled")
        val record: suspend (String, Int) -> Unit = { _, _ -> throw cancelled }

        var thrown: Throwable? = null
        try {
            runBlocking { ContextLimitWriteback(record).offer("ep-1", 1_000_000) }
        } catch (c: CancellationException) {
            thrown = c
        }

        assertTrue(
            "CancellationException must propagate unchanged, not be swallowed",
            thrown === cancelled,
        )
    }
}
