package com.rickeal.liquidharness.core.skills

import com.rickeal.liquidharness.core.agent.skill.SkillBody
import com.rickeal.liquidharness.core.agent.skill.SkillCatalog
import com.rickeal.liquidharness.core.agent.skill.SkillMeta

/**
 * SKILL.md 解析结果。
 *
 * 用 sealed interface 而不是抛异常/返回 null：坏文件是**常态**（用户手写、导入包残缺），
 * 调用方需要区分「跳过这个文件」与「整个目录读失败」，异常通道做不到后者不吞前者。
 */
sealed interface ParseResult {
    /** 解析成功。 */
    data class Ok(val body: SkillBody) : ParseResult

    /** 解析失败。[reasonCode] 是 ASCII 常量，可直接进日志与 UI 诊断。 */
    data class Invalid(val reasonCode: String) : ParseResult
}

/**
 * SKILL.md 解析器（纯函数：无 IO、无协程、无全局状态，因此可纯 JVM 单测）。
 *
 * 格式刻意限制成 Claude-Agent-Skills 的最小子集 —— 手写 frontmatter 解析，**不引 YAML 库**：
 * 技能是纯文本，YAML 的锚点/多行折叠/类型推断全是我们不需要的攻击面（一个畸形 YAML
 * 就能让解析器栈溢出或吃满内存）。这里只认 `key: value` 单行。
 *
 * ```
 * ---
 * name: pdf-toolkit
 * description: Extract text and tables from PDF files.
 * allowed-tools: file_read, file_list
 * ---
 * # 正文 markdown ...
 * ```
 */
object SkillParser {

    /** 缺 frontmatter（没有起始 `---`，或没有闭合 `---`）。 */
    const val REASON_MISSING_FRONTMATTER: String = "missing_frontmatter"

    /** 有 frontmatter 但没有 `name`（或为空）。 */
    const val REASON_MISSING_NAME: String = "missing_name"

    /** `name` 不满足 `[a-z0-9-]{1,64}`。 */
    const val REASON_INVALID_NAME: String = "invalid_name"

    /** 正文被截断时追加的 ASCII 标记（模型据此知道自己看到的是残篇）。 */
    const val TRUNCATION_MARK: String = "\n\n[truncated]"

    private const val DELIMITER = "---"
    private const val KEY_NAME = "name"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_ALLOWED_TOOLS = "allowed-tools"

    /** 技能名是**加载键**，必须是 URL/文件名安全的短标识，不许有空格与路径分隔符。 */
    private val NAME_PATTERN = Regex("[a-z0-9-]{1,64}")

    /**
     * 解析一份 SKILL.md 全文。
     *
     * @param raw 文件原文（CRLF 会被归一化）。
     * @param source 来源标识，原样写进 [SkillMeta.source]，仅供 UI 分组与诊断。
     */
    fun parse(raw: String, source: String = ""): ParseResult {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')

        if (lines.firstOrNull()?.trim() != DELIMITER) {
            return ParseResult.Invalid(REASON_MISSING_FRONTMATTER)
        }
        val close = (1 until lines.size).firstOrNull { lines[it].trim() == DELIMITER }
            ?: return ParseResult.Invalid(REASON_MISSING_FRONTMATTER)

        var name = ""
        var description = ""
        val tools = mutableListOf<String>()

        for (i in 1 until close) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val colon = line.indexOf(':')
            // 无冒号或冒号在行首 ⇒ 不是 `key: value`，直接忽略（宽进严出，坏行不放大成坏文件）。
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim().lowercase()
            val value = unquote(line.substring(colon + 1).trim())
            when (key) {
                KEY_NAME -> name = value
                KEY_DESCRIPTION -> description = value
                KEY_ALLOWED_TOOLS -> tools += splitTools(value)
            }
        }

        if (name.isEmpty()) return ParseResult.Invalid(REASON_MISSING_NAME)
        if (!NAME_PATTERN.matches(name)) return ParseResult.Invalid(REASON_INVALID_NAME)

        val rawBody = lines.subList(close + 1, lines.size).joinToString("\n").trim('\n')
        val body = if (rawBody.length > SkillCatalog.MAX_BODY_CHARS) {
            rawBody.take(SkillCatalog.MAX_BODY_CHARS) + TRUNCATION_MARK
        } else {
            rawBody
        }
        return ParseResult.Ok(SkillBody(SkillMeta(name, description, tools.distinct(), source), body))
    }

    /** `allowed-tools` 允许逗号或空格（含 Tab）分隔，两种写法在真实技能库里都很常见。 */
    private fun splitTools(value: String): List<String> =
        value.split(',', ' ', '\t').map { it.trim() }.filter { it.isNotEmpty() }

    /** 去掉成对的首尾引号（`name: "pdf"` 与 `name: pdf` 等价）。 */
    private fun unquote(value: String): String {
        if (value.length < 2) return value
        val first = value.first()
        if (first != '"' && first != '\'') return value
        if (value.last() != first) return value
        return value.substring(1, value.length - 1)
    }
}
