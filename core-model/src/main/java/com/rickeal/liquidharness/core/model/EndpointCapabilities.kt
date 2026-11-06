package com.rickeal.liquidharness.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 端点的输入 / 输出模态集合（用户 W8 需求里的「输入」「输出」按**模态**解释）。
 *
 * 为什么不是价格、不是 token 上限：OR `/v1/models` 的 `input_modalities` / `output_modalities`
 * 是机器可验证的模态轴，而价格本仓无消费者、token 上限已有 `contextLength` / `maxOutputTokens`
 * （重复即 W5-01「第二把尺」）。
 *
 * 不收 `VIDEO`：OpenRouter 的 usage 里有 `video_tokens`，但本波没有视频输入通道，
 * 收进来就是一个「声明了没消费者」的死枚举值。
 *
 * 空集合语义见各端点字段的 KDoc。
 */
@Serializable
enum class Modality {
    TEXT,
    IMAGE,
    AUDIO,
}

/**
 * 用户的**思考意图**。单枚举承载，刻意不用三个布尔描述同一件事 —— 那会出现
 * 「OFF 且 REASONING_ONLY」这种自相矛盾的状态。
 *
 * 与两个轴的关系（三者是不同的轴，不是同一件事的三个说法）：
 * - `RemoteEndpoint.supportsReasoning` = 端点**能力位**（能否思考）；
 * - `RemoteEndpoint.allowDisableThinking` = 端点**能力声明**（能否真关，来自 OR `reasoning.mandatory` 取反）；
 * - 本枚举 = 用户**意图**。`effectiveThinkingMode` 把能力位与意图合流。
 *
 * 线上形态与计费（主理人 `docs/analysis/w8-provider-probe.md` 实测）：
 *
 * | 态 | 线上 | 计费 |
 * |---|---|---|
 * | [OFF] | `reasoning: {"enabled": false}` | 省 rtok（实测 0） |
 * | [HIDE_ONLY] | `include_reasoning: false` | **仍付**（实测 32/42 rtok） |
 * | [ON] | `reasoning: {"effort": wire}` | 正常 |
 * | [FOLLOW_PROVIDER] | 不写任何字段 | dots 默认就开思考 ⇒ **不等于关思考** |
 * | [REASONING_ONLY] | 不发服务端开关 | 客户端期望：只产出思考、正文可空 |
 *
 * 🔴 「允许关闭思考」与「仅隐藏思考」是两个语义，UI 文案必须写清 HIDE_ONLY **仍计费**。
 */
@Serializable
enum class ThinkingMode {
    /** 真关思考（`reasoning: {"enabled": false}`，实测 rtok=0、正文精确不带前导换行）。 */
    OFF,

    /** 不发任何思考开关，跟随服务端默认。 */
    FOLLOW_PROVIDER,

    /** 只隐藏思考文本，**不关、不省**（`include_reasoning: false`，仍计费 rtok）。 */
    HIDE_ONLY,

    /** 正常思考，思考 + 正文都回传。 */
    ON,

    /**
     * **仅思考模式**：要求模型只产出思考、正文可能为空。
     *
     * 没有供应商原生的「只思考」开关，它是**客户端期望 + 渲染策略**：把 thinking 块当主答案展示，
     * 且**不把「正文为空但有思考」判为断流 / 空输出**（关联断流误标 STOP，挂账 W9-09）。
     */
    REASONING_ONLY,
}

/**
 * 五档思考强度（用户需求：低 / 中 / 高 / 超高 / 极致）。
 *
 * 🔴 [EXTREME] 是 **UI 档位**，wire 值**永不落 `"max"`**：实测 `effort:"max"` 的
 * `reasoning_tokens=198` 落在 high(323) 与 medium(79) 之间，不服从单调性，
 * 且 OpenRouter 官方口径 `xhigh` 与 `max` 同为 ~95% 配额 ⇒ 即便网关接受也不构成第五个预算。
 * 发未验证的非法值属「静默降级」，必须走 [normalizeEffort] 显式降级并落 `reason`。
 *
 * `@SerialName` 小写与现役 wire 值 `"medium"` 对齐（`RemoteEndpoint.reasoningEffort`）。
 */
@Serializable
enum class ReasoningEffort {
    @SerialName("low") LOW,

    @SerialName("medium") MEDIUM,

    @SerialName("high") HIGH,

    @SerialName("xhigh") XHIGH,

    EXTREME,
    ;

    /** 五档 UI 展示顺序（低 → 极致）。 */
    companion object {
        val DISPLAY_ORDER: List<ReasoningEffort> = listOf(LOW, MEDIUM, HIGH, XHIGH, EXTREME)

        /** 按 wire 值反查；`"extreme"` 不是 wire 值（永不落网），返回 null。 */
        fun fromWire(wire: String?): ReasoningEffort? = when (wire?.lowercase()) {
            "low" -> LOW
            "medium" -> MEDIUM
            "high" -> HIGH
            "xhigh" -> XHIGH
            else -> null
        }
    }
}

/** 端点「自定义协议」的族内覆写（用户需求里的「自定义协议」）。
 *
 * 架构裁决：OpenAI 兼容族里 4/6 个供应商在线上是**同一协议**，差异只有
 * baseUrl + 思考参数风格 + extraHeaders —— 那是配置，不是代码分支。
 * 所以自定义协议 = 在现有请求体上**覆写少量键**，不新增协议族。
 */
@Serializable
data class CustomProtocol(
    /** 关闭时 `extraBody` 不参与请求体（默认 false = fail-closed，不静默改请求）。 */
    val enabled: Boolean = false,
    /**
     * 需要覆写 / 追加的顶层 body 键值。
     *
     * 🔴 值必须是**原始类型**（string / number / boolean）—— 复杂 JSON 请用 extraHeaders 或等价配置。
     * 键白名单与凭据语义由 [validateAdvanced] 钉死；**禁止覆盖保留键**（见 [RESERVED_BODY_KEYS]）。
     */
    val extraBody: Map<String, String> = emptyMap(),
)

/**
 * 档位归一结果。`wire == null` 表示「本 style 不发 effort 类字段」。
 *
 * 硬不变量（有反例锚的单测）：`wire != "max"`；`reason != null` 时必须能被 UI / 日志消费，
 * **不许静默降级** —— 静默发未验证的档位正是 W5-01「判据自身先被验证」的反面。
 */
data class ResolvedEffort(
    val wire: String?,
    val degradedFrom: ReasoningEffort? = null,
    val reason: String? = null,
)

/** [XHIGH] 需要的最小输出预算。实测 `effort:"xhigh"` 单次可吃 688 rtok，预算不足会被 `finish=length` 截断。 */
private const val XHIGH_MIN_OUTPUT_TOKENS: Int = 1500

/**
 * 把「UI 五档 + 端点支持的档位集合 + style + 输出预算」归一成可下发的 wire 值。
 *
 * 归一规则（顺序即优先级）：
 * 1. style 不承载 effort ⇒ 直接返回 `wire = null`，不发字段；
 * 2. `desired` 解析成 [ReasoningEffort]；解析不出 ⇒ 回落 MEDIUM 并落 `reason`；
 * 3. [ReasoningEffort.EXTREME] 永不落 `max`：supported 含 XHIGH 且预算足够 ⇒ 降 `xhigh`，
 *    否则降 `high`，两种情况都落 `reason`；
 * 4. desired 未被端点声明支持（supported 非空但不含 desired）⇒ 就近降一档并落 `reason`；
 * 5. desired 是 XHIGH 但预算不足 ⇒ 降 `high` 并落 `reason = output_budget_insufficient`。
 *
 * @param supported 端点声明的支持档位；**空集 = 未声明**（不是「全支持」），按已实测四档处理。
 */
fun normalizeEffort(
    desired: String,
    supported: Set<ReasoningEffort>,
    style: ReasoningStyle,
    maxOut: Int,
): ResolvedEffort {
    if (!style.carriesEffort()) return ResolvedEffort(wire = null)

    val desiredEffort = ReasoningEffort.fromWire(desired)
        ?: ReasoningEffort.entries.firstOrNull { it.name.equals(desired, ignoreCase = true) }
        ?: ReasoningEffort.EXTREME.takeIf { desired.equals("extreme", ignoreCase = true) }
        ?: return ResolvedEffort(
            wire = ReasoningEffort.MEDIUM.wireValue(),
            degradedFrom = null,
            reason = "effort_unrecognized_fell_back_medium:" + desired,
        )

    // 端点未声明支持档位 ⇒ 按「已实测四档」处理，但不标 unsupported（别把「未声明」读成「不支持」）。
    val effectiveSupported: Set<ReasoningEffort> = supported.ifEmpty {
        setOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH, ReasoningEffort.XHIGH)
    }

    if (desiredEffort == ReasoningEffort.EXTREME) {
        return if (ReasoningEffort.XHIGH in effectiveSupported && maxOut >= XHIGH_MIN_OUTPUT_TOKENS) {
            ResolvedEffort("xhigh", ReasoningEffort.EXTREME, "unsupported_effort_max_ignored")
        } else {
            val why = if (maxOut < XHIGH_MIN_OUTPUT_TOKENS) {
                "output_budget_insufficient:" + maxOut
            } else {
                "unsupported_effort_max_ignored"
            }
            ResolvedEffort("high", ReasoningEffort.EXTREME, why)
        }
    }

    if (desiredEffort !in effectiveSupported) {
        val fallback = lowerNeighbor(desiredEffort, effectiveSupported)
            ?: return ResolvedEffort(
                wire = desiredEffort.wireValue(),
                degradedFrom = null,
                reason = "effort_unsupported_no_lower:" + desiredEffort.name,
            )
        return ResolvedEffort(
            wire = fallback.wireValue(),
            degradedFrom = desiredEffort,
            reason = "effort_unsupported:" + desiredEffort.name + "->" + fallback.name,
        )
    }

    if (desiredEffort == ReasoningEffort.XHIGH && maxOut < XHIGH_MIN_OUTPUT_TOKENS) {
        return ResolvedEffort(
            wire = ReasoningEffort.HIGH.wireValue(),
            degradedFrom = ReasoningEffort.XHIGH,
            reason = "output_budget_insufficient:" + maxOut,
        )
    }

    return ResolvedEffort(wire = desiredEffort.wireValue())
}

/** 就近往低找一档；`LOW` 之下没有了 ⇒ null。 */
private fun lowerNeighbor(
    desired: ReasoningEffort,
    supported: Set<ReasoningEffort>,
): ReasoningEffort? {
    val order = ReasoningEffort.DISPLAY_ORDER
    val idx = order.indexOf(desired)
    if (idx <= 0) return null
    for (i in idx - 1 downTo 0) {
        if (order[i] in supported) return order[i]
    }
    return null
}

/** [ReasoningEffort] 的 wire 值。[EXTREME] 在线上没有合法形态，永远不该被直接下发。 */
private fun ReasoningEffort.wireValue(): String = when (this) {
    ReasoningEffort.LOW -> "low"
    ReasoningEffort.MEDIUM -> "medium"
    ReasoningEffort.HIGH -> "high"
    ReasoningEffort.XHIGH -> "xhigh"
    ReasoningEffort.EXTREME -> "xhigh" // 只经 normalizeEffort 降级后到达，永不发 "max"
}

/** [ReasoningStyle] 是否承载「档位」语义（只有新协议族对象与 o 系 style 有档位概念）。 */
fun ReasoningStyle.carriesEffort(): Boolean = this == ReasoningStyle.OPENROUTER_REASONING_OBJECT ||
    this == ReasoningStyle.REASONING_EFFORT
