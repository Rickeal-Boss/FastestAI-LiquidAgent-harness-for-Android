package com.rickeal.liquidharness.core.model

import kotlinx.serialization.Serializable

/** run 的终止原因。**判据与文案分离**：UI 按这个枚举给文案，不解析字符串。 */
enum class TerminationReason {
    /** 模型自己停了 —— 正常。 */
    ModelStopped,

    /** 轮次耗尽。 */
    MaxRounds,

    /** Harness 主动熔断（墙钟 / token / 振荡 / 同参 / 失败连击 / 空输出 / 流式循环）。 */
    BreakerTripped,

    /** 引擎闸门拒斥，本次 run 一次都没开始。 */
    GateRejected,

    /**
     * 宿主 / 进程中断。
     *
     * ⚠️ **不写入 journal** ——「无 settled 行」就是它的判据。
     * 这条纪律来自旧仓 Wave2 缺陷：进程被杀后若写了 settled，下次进会话
     * `findUnsettled` 就找不到该恢复的 run。
     */
    Interrupted,
}

/** 计划步骤状态。消费者：`feature-chat` 的计划卡片、`core-agent` 的 plan 工具。 */
@Serializable
enum class PlanStepStatus { PENDING, IN_PROGRESS, DONE, SKIPPED }

/**
 * 计划步骤。
 *
 * 定义在 core-model 而不是 core-agent，唯一理由是 [AgentEvent.PlanUpdated] 要引用它，
 * 而 AgentEvent 必须能在契约层独立编译（否则 UI 归约层要依赖内核）。
 * 语义所有权仍在 core-agent 的 plan 模块。
 */
@Serializable
data class PlanStep(
    val id: String = newId(),
    val title: String = "",
    val status: PlanStepStatus = PlanStepStatus.PENDING,
)

/**
 * 诊断卡内容。任一熔断路径填充，正常结束为 null。
 *
 * 字段刻意用 `String` 而不是枚举：诊断卡是**给人看的**，新熔断类型不该要求
 * 改一次契约（旧仓为此改了三版）。`kind` 建议填熔断类型常量名，便于聚合统计。
 */
@Serializable
data class BottleneckReport(
    val kind: String = "",
    val detail: String = "",
    val round: Int = 0,
    val elapsedMillis: Long = 0L,
)

/**
 * UI 层事件。
 *
 * 与引擎层 [GenerationEvent] 是两套：AgentEvent 知道「轮次、审批、工具执行」，
 * GenerationEvent 只知道「模型吐了什么」。
 *
 * ⚠️ 事件序列有三条**既有约束**（AGENTS.md §五.3），改动前必须枚举路径矩阵：
 * 1. [ToolResultReceived] / [ToolSkipped] 在 UI 侧**只更新不新建** ——
 *    提前 return 的分支必须自己保证轨迹创建点（即 [ToolCallStarted] 已发过）。
 * 2. [ApprovalRequested] **无去重** —— 同一调用重复发就会弹两次卡。
 * 3. [Retrying] 与 [StreamReset] 语义不同：前者「重新发请求」，后者「丢弃已流出的文本」。
 *    混用会让 UI 清错缓冲（旧仓 `AgentEvents.kt:103-120` 是踩过 P1 才写的这段）。
 */
sealed interface AgentEvent {
    data class RoundStarted(val round: Int, val maxRounds: Int) : AgentEvent

    data class TextDelta(val text: String) : AgentEvent

    /** 思考通道。**与正文分道**，UI 渲染为独立折叠块。 */
    data class ReasoningDelta(val text: String) : AgentEvent

    data class ToolCallStarted(val call: ToolCall, val spec: ToolSpec) : AgentEvent

    data class ToolResultReceived(val result: ToolResult) : AgentEvent

    data class ToolSkipped(val call: ToolCall, val reason: String) : AgentEvent

    data class MessageCommitted(val message: ChatMessage) : AgentEvent

    /**
     * 用量。**新仓新增**（旧仓没有）：API 路径下 token 等于钱，
     * 且 provider 回报的 usage 是权威值（旧仓 litertlm 从不回报 usage）。
     */
    data class UsageUpdated(val prompt: Int, val completion: Int, val cachedPrompt: Int) : AgentEvent

    /** 技能被加载。**新仓新增**：UI 要显示「本回答用了哪个技能」，否则技能是隐形的。 */
    data class SkillLoaded(val name: String) : AgentEvent

    data class Finished(
        val text: String,
        val rounds: Int,
        val usage: TokenUsage? = null,
        val terminatedBy: TerminationReason = TerminationReason.ModelStopped,
        val report: BottleneckReport? = null,
    ) : AgentEvent

    data class Failed(
        val message: String,
        val cause: Throwable? = null,
        val report: BottleneckReport? = null,
        val terminatedBy: TerminationReason? = null,
    ) : AgentEvent

    data class Cancelled(val partialText: String) : AgentEvent

    /** 同一 round 内重试（网络重试 / 引擎重建）。UI 必须清空流式缓冲。 */
    data class Retrying(val reason: String) : AgentEvent

    /** 主循环丢弃了本轮已流出的文本（重复截断 / 空输出 nudge）。 */
    data class StreamReset(val reason: String) : AgentEvent

    data class ApprovalRequested(val call: ToolCall, val spec: ToolSpec) : AgentEvent

    data class PlanUpdated(val steps: List<PlanStep>) : AgentEvent

    /**
     * MCP 工具清单刷新结果。**新仓新增**：MCP 是远端依赖，
     * 「连不上」必须是一个可见事件，而不是静默少几个工具。
     */
    data class ToolSourceUpdated(
        val sourceId: String,
        val ok: Boolean,
        val toolCount: Int,
        val message: String? = null,
    ) : AgentEvent
}
