package com.rickeal.liquidharness.feature.chat

import android.util.Log
import com.rickeal.liquidharness.core.agent.skill.SkillMention
import com.rickeal.liquidharness.core.data.AppContainer
import kotlin.coroutines.cancellation.CancellationException

/**
 * `ChatViewModel` 的 skill 引用辅助（W17 外提：该文件触 G10 单文件闸，先例 = `ConversationOps`）。
 *
 * 三件事：① 目录加载 —— 取消必须透传（W84），失败 Log 后回空列表（可观测，不再静默，外审 A1/N3）；
 * ② 发送侧装饰 —— `/name` 行首归一 + 白名单过滤（W17 收口 ①②，见 `SkillMention` 类头）；
 * ③ 显示剥引导 —— `send()` 落气泡的是装饰后文本（wire = 存储 ⇒ 跨轮请求体逐字节稳定，
 * 修外审 N1/P2-1 前缀分叉），UI 原文由 [displayText] 剥引导保证。
 */
internal object ChatSkillInput {

    private const val TAG = "ChatSkillInput"

    /**
     * 加载可被 `@`/`/` 引用的 skill 名。`SkillCatalog.list()` 是 suspend（磁盘读取）：
     * 必须先捕 `CancellationException` 原样重抛（照 `ChatContextWindow` 正例），再兜其他异常
     * —— 吞掉 CE 会让 Job 从 cancelled 变 completed。
     */
    suspend fun loadSkillNames(container: AppContainer): List<String> =
        try {
            container.skills.list().map { it.name }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "skill catalog load failed", t)
            emptyList()
        }

    /**
     * 发送侧装饰：`/name` 行首归一 → 只对**已知** skill 名附加引导（`knownNames` 与下拉面板
     * 同一份目录事实源）；未知 `@token`（邮箱 / handle）原样保留在正文，不注入引导。
     */
    fun decorateInput(text: String, knownNames: List<String>): String =
        SkillMention.decorate(text, knownNames.toSet())

    /** 气泡显示：剥掉装饰引导（send 存的是装饰版，见类头第 3 条）。 */
    fun displayText(text: String): String = SkillMention.stripDecoration(text)
}
