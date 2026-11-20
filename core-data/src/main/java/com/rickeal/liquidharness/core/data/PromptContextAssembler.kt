package com.rickeal.liquidharness.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * 一次 run 的提示词上下文（记忆正文 + 技能一级披露）。
 *
 * `suspend` 且每 run 只调一次：两者都读盘，放在 `AgentRequest` 构造处读才能保证
 * system 字节稳定（`00-brief.md` A8）；每轮重读会让技能顺序 / 记忆时间戳变动。
 * 失败降级 ⇒ 空串 / 空段（增强不是前置条件）；取消**不降级**，见 [degradeTo]。
 *
 * W37 拆分：本函数与 [PromptContext] 自 `AppContainer.kt` 迁出（G10 单文件上限 400 行，
 * 原文件 417 行触顶）。行为零变更；`AppContainer.promptContext()` 保留为一行门面。
 */
internal suspend fun AppContainer.assemblePromptContext(): PromptContext {
    val memoryText = degradeTo("") {
        com.rickeal.liquidharness.core.agent.memory.renderMemoryForPrompt(memory.all())
    }
    // 停用技能既不进一级披露，也不能被 skill_load 加载（工具层查同一个目录）。
    val disabled = degradeTo(emptySet()) { settings.disabledSkills.first() }
    val skillsSection = degradeTo("") {
        com.rickeal.liquidharness.core.agent.skill.renderSkillsSection(
            skills.list().filterNot { it.name in disabled }
        )
    }
    return PromptContext(memoryText = memoryText, skillsSection = skillsSection)
}

/**
 * 普通异常 ⇒ [fallback]；取消**重抛**（§五.4：`runCatching` 会吞 `CancellationException`）。
 *
 * W38 提升为 public：feature-settings 的 `ExtrasViewModel` 同源挂起点复用本辅助，
 * 统一「吞 CE 反模式清零」（W17-02 / W36 复核报告 P0）。`internal` 按模块隔离，
 * 跨模块复用必须 public。
 */
public suspend fun <T> degradeTo(fallback: T, block: suspend () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    fallback
}

/**
 * suspend 写路径：普通异常吞掉、取消重抛 —— 供 ViewModel 内 `viewModelScope.launch` 的
 * 写操作复用（fail 时保留原状态、error 由调用方设）。与 [degradeTo] 同源，避免用普通
 * `runCatching {}` 包 suspend 调用导致 `CancellationException` 被吞、job 从 cancelled
 * 变 completed（AGENTS §五.4 / W17-02）。block 返回值被丢弃（写操作多为 Unit/计数）。
 */
public suspend fun runIgnoringCancellation(block: suspend () -> Unit) {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // 写路径失败：保留原状态，error 提示由调用方设置
    }
}

/**
 * `runCatching` 的 suspend 安全版：普通异常 ⇒ `Result.failure`，取消**重抛**。
 * 供需要保留 `getOrNull()` / `isSuccess` / `getOrDefault()` 返回语义的挂起点复用，
 * 替代会吞 `CancellationException` 的 `runCatching {}`（W17-02）。
 */
public suspend fun <T> suspendResultOf(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (e: Exception) {
    Result.failure(e)
}

/** 提示词上下文快照。字段直接对应 `AgentRequest` 的两个入参。 */
data class PromptContext(
    val memoryText: String = "",
    val skillsSection: String = "",
)
