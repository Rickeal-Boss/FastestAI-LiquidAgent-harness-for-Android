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

/** 普通异常 ⇒ [fallback]；取消**重抛**（§五.4：`runCatching` 会吞 `CancellationException`）。 */
private suspend fun <T> degradeTo(fallback: T, block: suspend () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    fallback
}

/** 提示词上下文快照。字段直接对应 `AgentRequest` 的两个入参。 */
data class PromptContext(
    val memoryText: String = "",
    val skillsSection: String = "",
)
