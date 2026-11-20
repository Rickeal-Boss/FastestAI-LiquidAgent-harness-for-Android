package com.rickeal.liquidharness.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W38 — 「吞 CE 反模式清零」的**直接用例**（区别于 W37 的「模式自证」）。
 *
 * W37 的 `AutoCompactDiagnosticsTest.runCatchingSwallowsCancellationSoPromptContextMustNotUseIt`
 * 在测试里复刻 try/catch 形状，只能证明「这个形状会透传 CE」，不能证明生产函数真的这么做。
 * 本测试直接调用 [degradeTo] / [runIgnoringCancellation] / [suspendResultOf] 三个**生产函数**
 * （ExtrasViewModel 的 18 处挂起点全部改走它们），对每一个断言：取消必须原样抛出、
 * 普通异常才降级。这补上了 W17-02 一直缺的「取消透传直接覆盖」。
 */
class SuspendCancellationPassThroughTest {

    @Test
    fun degradeToRethrowsCancellation() = runBlocking {
        val cancelled = CancellationException("job cancelled")
        val thrown = try {
            degradeTo("fallback") { throw cancelled }
            null
        } catch (c: CancellationException) {
            c
        }
        assertSame("degradeTo must rethrow the exact CancellationException", cancelled, thrown)
    }

    @Test
    fun degradeToDegradesOnOrdinaryException() = runBlocking {
        val got = degradeTo("fallback") { throw IllegalStateException("disk error") }
        assertEquals("ordinary exception ⇒ fallback", "fallback", got)
    }

    @Test
    fun runIgnoringCancellationRethrowsCancellation() = runBlocking {
        val cancelled = CancellationException("viewmodel scope cancelled")
        val thrown = try {
            runIgnoringCancellation { throw cancelled }
            null
        } catch (c: CancellationException) {
            c
        }
        assertSame("runIgnoringCancellation must rethrow CancellationException", cancelled, thrown)
    }

    @Test
    fun runIgnoringCancellationSwallowsOrdinaryException() = runBlocking {
        // 不应抛出：写路径普通异常吞掉、保留原状态。
        var reached = false
        runIgnoringCancellation {
            reached = true
            throw IllegalStateException("write failed")
        }
        assertTrue("block was entered", reached)
    }

    @Test
    fun suspendResultOfRethrowsCancellation() = runBlocking {
        val cancelled = CancellationException("probe cancelled")
        val thrown = try {
            suspendResultOf<String> { throw cancelled }
            null
        } catch (c: CancellationException) {
            c
        }
        assertSame("suspendResultOf must rethrow CancellationException", cancelled, thrown)
    }

    @Test
    fun suspendResultOfReturnsFailureForOrdinaryException() = runBlocking {
        val result = suspendResultOf<String> { throw IllegalStateException("probe failed") }
        assertTrue("ordinary exception ⇒ Result.failure", result.isFailure)
        assertEquals("probe failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun suspendResultOfReturnsSuccessValue() = runBlocking {
        val result = suspendResultOf { 42 }
        assertEquals(42, result.getOrNull())
    }
}
