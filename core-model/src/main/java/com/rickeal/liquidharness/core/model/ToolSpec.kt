package com.rickeal.liquidharness.core.model

import kotlinx.serialization.Serializable

/** JSON Schema 的基础类型子集。够用即可，不追求覆盖 OpenAPI 全量。 */
@Serializable
enum class ToolParamType {
    STRING, NUMBER, INTEGER, BOOLEAN, ARRAY, OBJECT;

    /** 线上 schema 的类型名（`string` / `integer` / …）。 */
    val jsonName: String
        get() = when (this) {
            STRING -> "string"
            NUMBER -> "number"
            INTEGER -> "integer"
            BOOLEAN -> "boolean"
            ARRAY -> "array"
            OBJECT -> "object"
        }
}

/** 一个工具参数。 */
@Serializable
data class ToolParameter(
    val name: String,
    val type: ToolParamType = ToolParamType.STRING,
    val description: String = "",
    val required: Boolean = true,
    val enumValues: List<String> = emptyList(),
)

/**
 * 能力面：读还是写。与 [ToolSpec.dangerous]（工具属性）**正交**，勿混。
 *
 * 读 = 产出工作材料（可截断到 16000 字符）；写 = 产出回执（截断到 2000 字符）。
 * 见 `AgentPolicy.maxReadToolOutputChars` / `maxWriteToolOutputChars`。
 */
@Serializable
enum class ToolEffect { READ, WRITE }

/**
 * 工具来源。UI 分组、审批默认值、命名冲突策略都按它分支。
 *
 * ⚠️ 刻意**没有** SKILL 值：Skill 不产生工具，只通过 `SkillManifest.allowedTools`
 * 对现有工具面做**交集收窄**。给 Skill 一个 ToolSourceKind 会诱导后人去实现
 * 「技能声明新工具」，那等于把 Skill 从纯文本升级成插件 —— W69-09 判死的那条路，
 * 翻案的唯一支点就是「纯文本、零执行语义」。
 */
@Serializable
enum class ToolSourceKind {
    /** 内置，可直接用。 */
    BUILTIN,

    /** 远端 MCP 工具，强制审批。 */
    MCP,

    /** 子代理工具（ask_actor 等）。 */
    SUBAGENT,
}

/**
 * 一个可被模型调用的工具声明。
 *
 * 这个类同时是**请求体构造的输入**（`core-engine` 编码成三族 schema）与
 * **提示词文本的输入**（FULL 披露时 `toPromptLine()` 逐行拼进 system）。
 */
@Serializable
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val dangerous: Boolean = false,
    val category: String = "general",
    /**
     * 🔴 默认 WRITE 是刻意的 fail-closed（照搬旧仓）：
     * 新增工具忘了声明时，在只读档位下会被要求授权，而不是被静默放行。
     */
    val effect: ToolEffect = ToolEffect.WRITE,
    val timeoutMillisOverride: Long? = null,
    val keywords: List<String> = emptyList(),
    /** MCP 工具的 description 来自远端，是**不可信文本**（需截断 + 剥控制字符后才进提示词）。 */
    val source: ToolSourceKind = ToolSourceKind.BUILTIN,
    /** MCP 工具的 server alias；非 MCP 为 null。 */
    val namespace: String? = null,
) {
    /**
     * FULL 披露档位下进 system 的一行。
     *
     * ⚠️ 与 `03-contracts.md` §二 的草案**措辞有出入**：草案写的是中文冒号与「参数(…)」，
     * 本实现改为纯 ASCII。理由：守卫 N3（中文硬编码棘轮）基线为 0，
     * `core-<module>/src/main` 出现中文**字符串字面量**即判红。提示词语言对模型行为的影响
     * 远小于「守卫第一天就破线」，且工具描述本身来自各工具实现（英文），保持同语种更一致。
     */
    fun toPromptLine(): String {
        val params = if (parameters.isEmpty()) {
            ""
        } else {
            " params(" + parameters.joinToString(",") { it.name } + ")"
        }
        return "- " + name + ": " + description + params
    }
}
