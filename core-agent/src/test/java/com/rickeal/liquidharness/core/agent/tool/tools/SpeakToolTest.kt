package com.rickeal.liquidharness.core.agent.tool.tools

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `speak` 的行为单测（用假引擎，不碰 Android TextToSpeech）。 */
class SpeakToolTest {

    private class FakeEngine(private val outcome: SpeakOutcome = SpeakOutcome.Ok) : SpeechEngine {
        var spoken: String? = null
        var shutdownCalled = false

        override fun speak(text: String): SpeakOutcome {
            spoken = text
            return outcome
        }

        override fun shutdown() {
            shutdownCalled = true
        }
    }

    @Test
    fun speaksNormalizedText() = runBlocking {
        val engine = FakeEngine()
        val tool = speakToolForTest { engine }
        val result = tool.invoke("""{"text":"hello   world\n\nagain"}""")
        assertTrue(result.ok)
        assertEquals("hello world again", engine.spoken)
    }

    @Test
    fun emptyTextIsRejected() = runBlocking {
        val engine = FakeEngine()
        val tool = speakToolForTest { engine }
        val result = tool.invoke("""{"text":"   "}""")
        assertFalse(result.ok)
        assertEquals("text is required", result.errorMessage)
        assertEquals(null, engine.spoken)
    }

    @Test
    fun missingEngineReportsUnavailable() = runBlocking {
        val tool = speakToolForTest { null }
        val result = tool.invoke("""{"text":"hi"}""")
        assertFalse(result.ok)
        assertEquals("tts_unavailable", result.errorMessage)
    }

    @Test
    fun enqueueFailureKeepsRawCode() = runBlocking {
        val tool = speakToolForTest { FakeEngine(SpeakOutcome.EnqueueFailed(-2)) }
        val result = tool.invoke("""{"text":"hi"}""")
        assertFalse(result.ok)
        // 原始状态码必须带出来：真机上「语言包缺失」与「引擎拒绝」要能分开。
        assertEquals("tts_enqueue_failed(-2)", result.errorMessage)
    }

    @Test
    fun languageUnavailableIsReportedSeparately() = runBlocking {
        val tool = speakToolForTest { FakeEngine(SpeakOutcome.LanguageUnavailable(-2)) }
        val result = tool.invoke("""{"text":"hi"}""")
        assertFalse(result.ok)
        assertTrue(result.errorMessage.orEmpty().startsWith("tts_language_unavailable"))
    }

    @Test
    fun longTextIsCapped() {
        val capped = SpeakTool.normalize("a".repeat(SpeakTool.MAX_CHARS + 100))
        assertEquals(SpeakTool.MAX_CHARS, capped.length)
    }

    @Test
    fun whitespaceCollapseKeepsPunctuation() {
        assertEquals("one, two. three!", SpeakTool.normalize("  one,   two.\n three!  "))
    }
}
