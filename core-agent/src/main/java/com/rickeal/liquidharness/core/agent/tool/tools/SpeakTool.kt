package com.rickeal.liquidharness.core.agent.tool.tools

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.agent.tool.ToolContext
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 语音输出引擎端口。
 *
 * 抽出来的唯一理由是**可测性**：`TextToSpeech` 是 Android 类，JVM 单测里造不出来。
 * 有了这个端口，`SpeakTool` 的「空文本拒绝 / 超长截断 / 引擎不可用」三条分支可以纯 JVM 测。
 */
interface SpeechEngine {
    /**
     * 入队朗读。返回**原始状态码**，不是 Boolean：
     * `TextToSpeech.speak()` 只有 SUCCESS/ERROR 两态，而失败原因（语言包缺失 /
     * 引擎未就绪 / 队列满）对用户是完全不同的处置 —— 压成 Boolean 就只能报
     * 一个笼统的 `tts_enqueue_failed`（真机实测 iFlytek 引擎正是这样，见 W3 记录）。
     */
    fun speak(text: String): SpeakOutcome

    /** 释放底层引擎。幂等。 */
    fun shutdown()
}

/** 朗读结果。`Code` 保留原始状态码，便于真机日志定位。 */
sealed interface SpeakOutcome {
    data object Ok : SpeakOutcome
    /** 语言包缺失 / 不支持：设备 TTS 引擎装不了当前语言。 */
    data class LanguageUnavailable(val code: Int) : SpeakOutcome
    /** 引擎拒绝入队。`code` = `TextToSpeech.speak` 的返回值。 */
    data class EnqueueFailed(val code: Int) : SpeakOutcome
}

/**
 * `speak`：把文本读出来（设备本地 TTS）。
 *
 * ## 为什么用**本地** TTS 而不是让模型调一个语音合成 API
 *
 * 1. 零密钥、零成本、零网络 —— 语音输出是「锦上添花」能力，不该引入新的失败面
 *    （API 路径下每个外部依赖都是一个新的 401/429 来源）。
 * 2. 提供商侧的音频输出（如 `google/lyria-*`）是**音乐生成**，不是 TTS，不能读任意文本。
 *    这一点已在 W3 的供应商能力矩阵里实测并记档（`docs/notes/w3-provider-matrix.md`）。
 *
 * ## 效果声明
 *
 * `effect = WRITE`：朗读是**对外发声**的副作用（旁边有人会听到），在只读档位下应当
 * 被要求授权。`dangerous = false`（可逆、无数据外发）。
 */
class SpeakTool internal constructor(
    private val engineFactory: () -> SpeechEngine?,
) : Tool {

    constructor(context: ToolContext) : this({ AndroidSpeechEngine(context.appContext) })

    override val spec: ToolSpec = ToolSpec(
        name = "speak",
        description = "Speak the given text aloud with the device text-to-speech engine",
        parameters = listOf(
            ToolParameter("text", ToolParamType.STRING, "plain text to speak, max " + MAX_CHARS + " characters"),
        ),
        category = "audio",
        effect = ToolEffect.WRITE,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val raw = stringArg(argumentsJson, "text")
        val text = normalize(raw)
        if (text.isEmpty()) {
            return ToolResult(name = spec.name, ok = false, errorMessage = "text is required")
        }
        val engine = engineFactory()
            ?: return ToolResult(name = spec.name, ok = false, errorMessage = "tts_unavailable")
        return try {
            when (val outcome = engine.speak(text)) {
                is SpeakOutcome.Ok ->
                    ToolResult(name = spec.name, ok = true, output = "speaking " + text.length + " characters")
                is SpeakOutcome.LanguageUnavailable ->
                    ToolResult(name = spec.name, ok = false, errorMessage = "tts_language_unavailable(" + outcome.code + ")")
                is SpeakOutcome.EnqueueFailed ->
                    ToolResult(name = spec.name, ok = false, errorMessage = "tts_enqueue_failed(" + outcome.code + ")")
            }
        } catch (t: Throwable) {
            ToolResult(name = spec.name, ok = false, errorMessage = t.message ?: "tts_failed")
        }
    }

    companion object {
        /** 单次朗读上限（字符）。再长用户也听不完，且 TTS 队列会积压。 */
        const val MAX_CHARS = 1_200

        /**
         * 规范化：折叠空白 + 截断。纯函数（JVM 可测）。
         *
         * 保留标点与换行语义（TTS 用标点断句），只把连续空白折成一个空格。
         */
        fun normalize(raw: String): String {
            val collapsed = raw.trim().replace(Regex("\\s+"), " ")
            if (collapsed.length <= MAX_CHARS) return collapsed
            return collapsed.substring(0, MAX_CHARS)
        }
    }
}

/**
 * Android `TextToSpeech` 的最小封装。
 *
 * ## 初始化是异步的，必须等
 *
 * `TextToSpeech(context, listener)` 立刻返回，但 `speak()` 在 `onInit` 之前调用会返回
 * `ERROR`。所以这里用 `CountDownLatch` 等最多 [INIT_TIMEOUT_MS]，超时视为不可用。
 *
 * ## 为什么实例**不复用**、每次 invoke 新建
 *
 * `TextToSpeech` 持有系统服务绑定，长期持有会在应用退后台后被 OEM 冻结牵连
 * （本仓旧仓实测：ColorOS 灭屏 ~10s 进程冻结，绑定会一起失效并静默）。每次朗读新建、
 * 读完 `shutdown()`，代价是 ~100ms 初始化，换来「不会静默变哑」。
 */
internal class AndroidSpeechEngine(private val appContext: Context) : SpeechEngine {

    /**
     * 专用 Looper 线程。
     *
     * ## 为什么必须有自己的 Looper（真机实测）
     *
     * OPPO PDRM00 的默认 TTS 引擎是 `com.iflytek.speechsuite`。在**没有 Looper** 的
     * IO 线程上构造 `TextToSpeech` 时，`bindService` 的内部回调无处投递，
     * 真机上表现为 `speak()` 调用把 run 挂住 **>50s**（应用本身仍可响应，
     * 是工具协程卡死）。给引擎一个自带 Looper 的线程后，绑定与回调都在该线程上完成。
     *
     * ## 为什么还要硬超时
     *
     * 即便线程模型正确，第三方引擎仍可能因为授权 / 网络检查卡住。语音是**可选的副作用**，
     * 不该让整条 run 陪它等 —— 每次调用最多等 [CALL_TIMEOUT_MS]，超时返回
     * `EnqueueFailed(TIMEOUT_CODE)`，run 继续。
     */
    private val thread: HandlerThread by lazy {
        HandlerThread("harness-tts").apply { start() }
    }

    private val handler: Handler by lazy { Handler(thread.looper) }

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var languageBroken: Boolean = false

    @Volatile
    private var languageCode: Int = 0

    override fun speak(text: String): SpeakOutcome {
        val latch = CountDownLatch(1)
        var outcome: SpeakOutcome = SpeakOutcome.EnqueueFailed(TIMEOUT_CODE)
        handler.post {
            outcome = try {
                speakOnLooper(text)
            } catch (t: Throwable) {
                SpeakOutcome.EnqueueFailed(ERROR_CODE)
            } finally {
                latch.countDown()
            }
        }
        val finished = runCatching { latch.await(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            .getOrDefault(false)
        return if (finished) outcome else SpeakOutcome.EnqueueFailed(TIMEOUT_CODE)
    }

    /** 只在 [thread] 的 Looper 上执行（因此可以直接用阻塞等待）。 */
    private fun speakOnLooper(text: String): SpeakOutcome {
        val engine = ensureReady() ?: return SpeakOutcome.EnqueueFailed(ERROR_CODE)
        if (languageBroken) return SpeakOutcome.LanguageUnavailable(languageCode)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "harness-" + UUID.randomUUID())
        return if (result == TextToSpeech.SUCCESS) SpeakOutcome.Ok else SpeakOutcome.EnqueueFailed(result)
    }

    private fun ensureReady(): TextToSpeech? {
        tts?.let { return it }
        val latch = CountDownLatch(1)
        var ok = false
        val created = TextToSpeech(appContext) { status ->
            ok = status == TextToSpeech.SUCCESS
            latch.countDown()
        }
        val finished = runCatching { latch.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            .getOrDefault(false)
        if (!finished || !ok) {
            runCatching { created.shutdown() }
            return null
        }
        // 语言：先用系统默认，失败再退 en-US。
        //
        // ⚠️ 真机教训：`setLanguage` 失败后直接 `speak` 会返回 ERROR，而错误码被压成
        // `tts_enqueue_failed` 后完全指向错误的方向 —— 看起来像引擎坏了，实际是语言包没装。
        val primary = runCatching { created.setLanguage(Locale.getDefault()) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        languageCode = primary
        if (primary == TextToSpeech.LANG_MISSING_DATA || primary == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = runCatching { created.setLanguage(Locale.US) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            languageCode = fallback
            languageBroken = fallback == TextToSpeech.LANG_MISSING_DATA ||
                fallback == TextToSpeech.LANG_NOT_SUPPORTED
        }
        tts = created
        return created
    }

    override fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        languageBroken = false
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 4_000L

        /** 单次 speak 的硬上限。语音不值得让 run 等更久。 */
        const val CALL_TIMEOUT_MS = 5_000L

        /** 自定义状态码：超时（不是 TextToSpeech 的返回值）。 */
        const val TIMEOUT_CODE = -3
        const val ERROR_CODE = -1
    }
}

/** 装配（1 个）。单独成函数是为了与 `memoryTools` / `skillTools` 的装配形态一致。 */
fun speakTools(context: ToolContext): List<Tool> = listOf(SpeakTool(context))

/** 供单测直接构造（绕开 Android Context）。 */
internal fun speakToolForTest(engineFactory: () -> SpeechEngine?): Tool = SpeakTool(engineFactory)
