package com.rickeal.liquidharness.core.agent.tool.tools

import com.rickeal.liquidharness.core.agent.tool.Tool
import com.rickeal.liquidharness.core.agent.tool.ToolContext
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * `datetime`：当前日期与时间。
 *
 * ⚠️ 工具名由旧仓的 `current_time` 改为 `datetime`：新仓 `feature-chat` 的
 * `ToolTraceGroup` 已按 `datetime` 建了名字映射（`chat_tool_datetime`），改名会让
 * UI 回退成裸标识上屏。
 *
 * 时间源走 `ToolContext.nowMillis` 注入（单测冻结时间，绝不直连 `System.currentTimeMillis()`）。
 * 主构造器只收时钟函数：`invoke` 不需要 Android `Context`，收窄后 JVM 可直接构造。
 */
class DateTimeTool internal constructor(private val nowMillis: () -> Long) : Tool {

    constructor(context: ToolContext) : this(context.nowMillis)

    override val spec: ToolSpec = ToolSpec(
        name = "datetime",
        description = "Get the current date and time. Optional timeZone (e.g. Asia/Shanghai) and " +
            "pattern (e.g. yyyy-MM-dd HH:mm:ss)",
        parameters = listOf(
            ToolParameter("timeZone", ToolParamType.STRING, "zone id, defaults to the system zone", required = false),
            ToolParameter("pattern", ToolParamType.STRING, "format pattern, defaults to yyyy-MM-dd HH:mm:ss", required = false),
        ),
        category = "utility",
        effect = ToolEffect.READ,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val zoneId = runCatching { ZoneId.of(stringArg(argumentsJson, "timeZone")) }
            .getOrElse { ZoneId.systemDefault() }
        val pattern = stringArg(argumentsJson, "pattern").ifBlank { DEFAULT_PATTERN }
        val formatter = runCatching { DateTimeFormatter.ofPattern(pattern) }
            .getOrElse { DateTimeFormatter.ofPattern(DEFAULT_PATTERN) }
        // 单次采样：formatted 与 epochMillis 必须来自**同一个** Instant —— 两处各自取时钟
        // 会产出「12:00:00（epochMillis=…001）」这种自相矛盾的输出，注入固定时钟时必漂移。
        val instant = Instant.ofEpochMilli(nowMillis())
        val formatted = formatter.withZone(zoneId).format(instant)
        return ToolResult(
            name = spec.name,
            ok = true,
            output = formatted + " (timeZone: " + zoneId + ", epochMillis=" + instant.toEpochMilli() + ")",
        )
    }

    private companion object {
        const val DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss"
    }
}
