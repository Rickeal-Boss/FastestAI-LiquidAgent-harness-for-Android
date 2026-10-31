package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.agent.memory.MemoryStore
import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec

/**
 * 长期记忆三件套：`memory_write` / `memory_search` / `memory_delete`。
 *
 * ## 为什么记忆是**工具**而不是「每轮自动检索」
 *
 * 自动检索要在每轮请求前插一次嵌入或关键词匹配，且结果必须塞进 system（因为
 * `ContextBudget` 只改 working）—— 那就破坏了「system 每 run 字节稳定」
 * （`00-brief.md` A8）：记忆一变，整段前缀的 prompt cache 全价重算。
 * 做成工具后，记忆正文在 run 开始时**快照一次**进 system，run 内不变；
 * 模型自己决定要不要检索、要不要写。
 *
 * ## 写入为什么必须有上限
 *
 * `MemoryStore.MAX_CONTENT_CHARS` = 2000 字符/条、`MAX_PROMPT_CHARS` = 6000 总量。
 * 没有上限时，模型会把整段对话复述进记忆，下一轮 system 直接吃掉几千 token，
 * 而用户完全看不见钱花在哪。
 *
 * ## 效果声明（fail-closed）
 *
 * 写 / 删是 WRITE（只读档位下要授权），搜索是 READ。默认值本来就是 WRITE，
 * 这里**显式**写出来，避免有人日后改默认值时静默放宽。
 */
class MemoryWriteTool(private val store: MemoryStore) : Tool {

    override val spec: ToolSpec = ToolSpec(
        name = "memory_write",
        description = "Save a long-term memory entry; upsert by title (same title overwrites). " +
            "Use it for durable user preferences, project facts and decisions, not for logs.",
        parameters = listOf(
            ToolParameter("title", ToolParamType.STRING, "short unique title, e.g. 'user prefers metric units'"),
            ToolParameter("content", ToolParamType.STRING, "the fact to remember, one or two sentences"),
        ),
        category = "memory",
        effect = ToolEffect.WRITE,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val title = stringArg(argumentsJson, "title").trim()
        if (title.isEmpty()) return failure("title is required")
        val content = stringArg(argumentsJson, "content").trim()
        if (content.isEmpty()) return failure("content is required")
        val capped = if (content.length > MemoryStore.MAX_CONTENT_CHARS) {
            content.substring(0, MemoryStore.MAX_CONTENT_CHARS) + TRUNCATED_MARKER
        } else {
            content
        }
        return try {
            val entry = store.upsert(title, capped)
            ToolResult(
                name = spec.name,
                ok = true,
                output = "saved memory: " + entry.title + " (" + capped.length + " chars)",
            )
        } catch (t: Throwable) {
            failure(t.message ?: "memory write failed")
        }
    }

    private fun failure(message: String): ToolResult =
        ToolResult(name = spec.name, ok = false, errorMessage = message)

    private companion object {
        const val TRUNCATED_MARKER = " [truncated]"
    }
}

/** 关键字检索。返回按更新时间降序的条目，每行 `title: content`。 */
class MemorySearchTool(private val store: MemoryStore) : Tool {

    override val spec: ToolSpec = ToolSpec(
        name = "memory_search",
        description = "Search long-term memories by keyword; empty query lists the most recent entries",
        parameters = listOf(
            ToolParameter("query", ToolParamType.STRING, "keyword; empty = list recent", required = false),
            ToolParameter("limit", ToolParamType.INTEGER, "max entries, default 10", required = false),
        ),
        category = "memory",
        effect = ToolEffect.READ,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val query = stringArg(argumentsJson, "query")
        val limit = intArg(argumentsJson, "limit", DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return try {
            val hits = store.search(query, limit)
            if (hits.isEmpty()) {
                ToolResult(name = spec.name, ok = true, output = "no memory matched")
            } else {
                ToolResult(
                    name = spec.name,
                    ok = true,
                    output = hits.joinToString("\n") { it.toPromptLine() },
                )
            }
        } catch (t: Throwable) {
            ToolResult(name = spec.name, ok = false, errorMessage = t.message ?: "memory search failed")
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 10
        const val MAX_LIMIT = 50
    }
}

/** 按标题删除。不存在时**如实回灌**（ok=false），不假装成功。 */
class MemoryDeleteTool(private val store: MemoryStore) : Tool {

    override val spec: ToolSpec = ToolSpec(
        name = "memory_delete",
        description = "Delete a long-term memory entry by its exact title",
        parameters = listOf(
            ToolParameter("title", ToolParamType.STRING, "title of the entry to delete"),
        ),
        category = "memory",
        effect = ToolEffect.WRITE,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val title = stringArg(argumentsJson, "title").trim()
        if (title.isEmpty()) {
            return ToolResult(name = spec.name, ok = false, errorMessage = "title is required")
        }
        return try {
            val removed = store.delete(title)
            if (removed) {
                ToolResult(name = spec.name, ok = true, output = "deleted memory: " + title)
            } else {
                ToolResult(name = spec.name, ok = false, errorMessage = "no memory titled: " + title)
            }
        } catch (t: Throwable) {
            ToolResult(name = spec.name, ok = false, errorMessage = t.message ?: "memory delete failed")
        }
    }
}

/** 装配三个记忆工具。顺序固定（提示词与 UI 的顺序必须稳定）。 */
fun memoryTools(store: MemoryStore): List<Tool> = listOf(
    MemoryWriteTool(store),
    MemorySearchTool(store),
    MemoryDeleteTool(store),
)
