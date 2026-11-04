package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.model.RemoteEndpoint

// W6 从 ContextCompressor.kt 拆出（该文件已触 G10 单文件上限 400 的边）：拆的是「预算算式」
// 这一件事，它与压缩算法是两件事。**没有调大上限**，触顶的处理永远是拆分。

/**
 * 硬预算（token，估算量纲）：`windowEffective − maxOutputTokens`。**不乘水位线。**
 *
 * ```
 * windowEffective = min(endpoint.verifiedContextLength ?: endpoint.contextLength,
 *                       policy.maxContextTokens)
 * hardBudget      = windowEffective − endpoint.maxOutputTokens
 * ```
 *
 * ## 为什么必须与 [contextBudgetTokens] 分开（W5-02）
 *
 * 后者把「水位线 0.75」揉进了预算里，而 `ContextBudget.kt` 又拿 `budget − toolTokens` 当
 * **消息预算**喂压缩器 —— 硬预算若复用同一个数就会**少算 25% 的可用空间**，兜底形同虚设。
 * 软硬两条的分工与动作见 `PreStepBudget` 类头的表（此处不重复）。
 *
 * 三条口径订正（出处见各 policy 字段的 KDoc，防下一波照抄交接文档）：水位线 0.75 是**触发线**
 * 不是压缩目标（目标 0.6）；1 000 000 是**输入 + 输出共享**的上限，必须再减 `maxOutputTokens`；
 * `verifiedContextLength` 优先于声明值。
 *
 * 下限兜底：`windowEffective − maxOutputTokens` 可能 ≤ 0（maxOutputTokens 填得比窗口还大），
 * 那时按窗口一半算 —— 宁可收缩得激进，也不要发出一个必然 400 的请求。
 */
fun hardContextBudgetTokens(endpoint: RemoteEndpoint, policy: AgentPolicy): Int {
    val declared = endpoint.verifiedContextLength ?: endpoint.contextLength
    val window = minOf(declared, policy.maxContextTokens).coerceAtLeast(1)
    val usable = (window - endpoint.maxOutputTokens).let { if (it > 0) it else window / 2 }
    return usable.coerceAtLeast(1)
}

/**
 * 软预算（token，估算量纲）：[hardContextBudgetTokens] × `policy.compressThreshold`。
 *
 * 语义与 W5 之前**逐值相同**（`verifiedContextLength` 为 null 时算式完全一致），
 * 既有调用点与既有单测不受影响。动作 = **丢最老历史**（`WindowContextCompressor`），
 * 代价 = prompt cache 从切点起失效，所以要稀有（`00-brief.md` A8 理由 3）。
 */
fun contextBudgetTokens(endpoint: RemoteEndpoint, policy: AgentPolicy): Int =
    (hardContextBudgetTokens(endpoint, policy) * policy.compressThreshold).toInt().coerceAtLeast(1)

/**
 * 硬预算的**字符**量纲上限：`hardContextBudgetTokens × floor(chars/token)`。
 *
 * 字符量纲与估算器无关：只要「发出的字符数 ≤ 硬预算 × 下界字符/token」，无论对端
 * tokenizer 多激进都超不了窗口。`TokenEstimator` 对代码 / JSON 按 4 字符/token 估、
 * 实测 ≈2.0，所以「估算 16k < 软预算 20 928」与「实际发出 33 800 token」可以同时成立 ——
 * 用有偏的尺子量自己等于没量，这是 W5-01 的核心教训。
 *
 * ⚠️ 本函数用 **floor**（`policy.hardCharsPerToken`，默认 1.8），只在「还没有任何 provider
 * 真值」时才是实际生效的 cap。有真值时走 [charCapOf] + [effectiveCharsPerToken]。
 */
fun hardCharCap(endpoint: RemoteEndpoint, policy: AgentPolicy): Int =
    charCapOf(hardContextBudgetTokens(endpoint, policy), policy.hardCharsPerToken)

/** 字符上限 = token 预算 × chars/token。`coerceAtLeast(1)`：cap 为 0 会把一切判成超限。 */
fun charCapOf(hardBudgetTokens: Int, charsPerToken: Float): Int =
    (hardBudgetTokens * charsPerToken).toInt().coerceAtLeast(1)

/**
 * 实际生效的 chars/token：**floor 与 ceiling 之间的标定值**。
 *
 * ```
 * effective = if (measured <= 0) floor else max(floor, min(ceiling, measured))
 * ```
 *
 * chars/token 是**内容相关**的，不是常数：英文散文实测 **5.73～5.75**
 * （dots-3-note-preview 五档，误差 <0.5%）、代码 / JSON **≈2.0**。固定 1.8 会把散文会话的
 * 可用字符空间砍到 1/3（可感知的信息损失）；而 `TokenEstimator` 的 4 字符/token 对散文
 * 高估 token、对代码低估 token ⇒ M1 是**内容相关偏差**，这也是拿同一个估算器写单测
 * 会永远绿的原因。
 *
 * `measured` 只会偏小（方向安全，推导见 `RunState.measuredCharsPerToken`）；上界由
 * [AgentPolicy.maxCalibratedCharsPerToken] 兜住，否则一次异常回报就能把兜底关掉。
 * `measured <= 0` = 本 run 还没有真值（首轮 / 刚压缩过）⇒ 回落 floor。
 */
fun effectiveCharsPerToken(policy: AgentPolicy, measured: Float): Float {
    if (measured <= 0f) return policy.hardCharsPerToken
    val ceiling = policy.maxCalibratedCharsPerToken.coerceAtLeast(policy.hardCharsPerToken)
    return measured.coerceIn(policy.hardCharsPerToken, ceiling)
}
