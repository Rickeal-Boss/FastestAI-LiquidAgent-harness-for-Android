package com.rickeal.liquidharness.core.agent.skill

/**
 * 技能一级披露元数据（只有 name + description 进 system）。
 *
 * ## 为什么正文不在这个类里
 *
 * `00-brief.md` A6：技能正文一旦进 system，每次加载技能都会改 system 前缀 ⇒
 * 整条会话的 prompt cache 失效，且长会话里会反复付全价。所以：
 * - 元数据（本类）→ system 的 `[AVAILABLE SKILLS]` 段；
 * - 正文（[SkillBody]）→ 以 `role=USER` 追加到**尾部**，加载一次付一次，不进前缀。
 */
data class SkillMeta(
    /** 技能名（SKILL.md frontmatter `name`）。全局唯一，加载时按它查。 */
    val name: String,
    /** 一句话描述（frontmatter `description`）。进 system，必须短。 */
    val description: String,
    /**
     * 该技能允许使用的工具名（frontmatter `allowed-tools`）。空 = 不限制。
     *
     * ⚠️ 语义是**交集收窄**，不是「新增工具」：技能只能把本轮工具面变小。
     * 反向（技能声明新工具）等于把 Skill 从纯文本升级成插件，已判死。
     */
    val allowedTools: List<String> = emptyList(),
    /** 来源标识（内置 asset / 用户导入），仅用于 UI 分组与诊断。 */
    val source: String = "",
)

/** 技能正文。 */
data class SkillBody(
    val meta: SkillMeta,
    /** 正文（SKILL.md 去掉 frontmatter 的部分）。已做长度上限截断。 */
    val body: String,
)

/**
 * 技能目录端口。
 *
 * 实现落在 `:core-skills`（`FileSkillCatalog`），`core-agent` 只经本接口使用 ——
 * 这样 `core-agent` 不必依赖解析器与文件系统，技能工具因此可以纯 JVM 单测。
 */
interface SkillCatalog {

    /** 全部技能元数据（按 name 排序，**顺序必须稳定**：它进 system 前缀）。 */
    suspend fun list(): List<SkillMeta>

    /** 按名字取正文。null = 不存在（调用方如实回灌给模型，不抛异常）。 */
    suspend fun load(name: String): SkillBody?

    /** 关键字搜索（name + description）。空查询 = 全部。 */
    suspend fun search(query: String): List<SkillMeta>

    companion object {
        /** 单个技能正文上限（字符）。超出截断并留标记 —— 防止一个技能吃掉整个上下文。 */
        const val MAX_BODY_CHARS: Int = 24_000

        /** system 段里最多披露多少个技能（每个约 40-80 token）。 */
        const val MAX_DISCLOSED: Int = 24
    }
}

/**
 * 把技能元数据渲染成 `[AVAILABLE SKILLS]` 段正文（纯函数）。
 *
 * 排序与上限都定在这里，理由同 `renderMemoryForPrompt`：渲染规则只有一处真值源。
 * 每行 `- name: description`，与 `ToolSpec.toPromptLine()` 的形态一致（模型对同构列表更稳）。
 */
fun renderSkillsSection(skills: List<SkillMeta>): String {
    if (skills.isEmpty()) return ""
    return skills
        .sortedBy { it.name }
        .take(SkillCatalog.MAX_DISCLOSED)
        .joinToString("\n") { "- " + it.name + ": " + it.description }
}
