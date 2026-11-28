package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.policy.DEFAULT_COMPRESS_TARGET_RATIO
import com.rickeal.liquidharness.core.model.RemoteEndpoint

// W6 从 ContextCompressor.kt 拆出（该文件已触 G10 单文件上限 400 的边）：拆的是「预算算式」
// 这一件事，它与压缩算法是两件事。**没有调大上限**，触顶的处理永远是拆分。
// W39：`contextLimitOf` 从 feature-chat 的 `ChatContextWindow.kt` 并入本文件 —— 它是
// `hardContextBudgetTokens` 的纯消费者（状态条分母），与其余预算算式同一层。读档位的那一半
// （`contextWindowCap`）留在 :core-data（那里持有 AppContainer / SettingsRepository）。

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

/**
 * 状态条（`ChatContextMeter`）的**分母**：生效硬预算（token），不是端点声明窗口。
 *
 * ## 为什么必须是硬预算（W6 修的真实缺陷）
 *
 * 修前用 `endpoint.contextLength`（声明值）：端点 1M + 档位 32K 时占用率永远到不了 4%
 * ⇒ **状态条永不报警**，而内核其实已在 20 928 token 处压缩。这与 `ChatContextMeter`
 * 类头「一变色就精确对应压缩已经开始」正面矛盾；根因是档位在 settings、窗口在 endpoint。
 *
 * ## 为什么换成硬预算在数学上恰好对
 *
 * [contextBudgetTokens] `= hard × compressThreshold`，meter 的阈值也是 `compressThreshold`
 * ⇒ `used / hard >= 0.75` ⟺ `used >= softBudget` = **压缩触发点本身**。换别的分母只是巧合对齐。
 * 白拿的副作用：[hardContextBudgetTokens] 内部已是 `verifiedContextLength ?: contextLength`，
 * 400 学到的真实窗口写回后分母自动跟着变，不需要刷新代码。
 *
 * @param maxContextTokens 档位（由 `:core-data` 的 `contextWindowCap` 从设置读出）。
 *   端点未配置时返回 0（meter 不渲染；0 是「还不知道」，不是「已满」）。
 */
fun contextLimitOf(endpoint: RemoteEndpoint?, maxContextTokens: Int): Int =
    if (endpoint == null) {
        0
    } else {
        hardContextBudgetTokens(endpoint, AgentPolicy(maxContextTokens = maxContextTokens))
    }

/**
 * 生效的字符上限（**与 `PreStepBudget` 逐字同源**）：`hard × effectiveCharsPerToken`。
 *
 * W54 · P0-2：压缩器与硬闸必须吃同一个数，否则「压缩 applied」与「下一轮仍熔断」并存。
 * `measured <= 0`（首轮 / 刚压缩过）⇒ 回落 floor，与 `PreStepBudget` 完全一致。
 */
fun promptCharCap(endpoint: RemoteEndpoint, policy: AgentPolicy, measured: Float): Int =
    charCapOf(hardContextBudgetTokens(endpoint, policy), effectiveCharsPerToken(policy, measured))

/**
 * UI 侧（手动 / 自动压缩）的两个量纲预算：token 软预算 + 字符**水位线**。
 *
 * UI 拿不到内核的 `measuredCharsPerToken`，所以字符侧用 **floor** —— 那不是近似，
 * 而正是「压缩后 `measured` 置 0」那一刻 `PreStepBudget` 会采用的 cap。
 * 再乘 [DEFAULT_COMPRESS_TARGET_RATIO] 得到水位线：压缩目标与 token 侧（`× 0.6`）同构，
 * 同时给 system 段与 sanitize 补丁留出余量（W53 现场「压完仍超 3415 字符」的成因）。
 */
fun compressTargets(endpoint: RemoteEndpoint, policy: AgentPolicy): Pair<Int, Int> =
    contextBudgetTokens(endpoint, policy) to
        (hardCharCap(endpoint, policy) * DEFAULT_COMPRESS_TARGET_RATIO).toInt().coerceAtLeast(1)

/**
 * 会话级自动压缩的**触发线**（字符，按档位）：[hardCharCap] × `policy.compressThreshold`，
 * 并**夹在 `1 .. cap-1`**。
 *
 * ## 三条定尺理由
 * 1. **与 token 侧软预算同尺**：[contextBudgetTokens] 也是
 *    `hardContextBudgetTokens × compressThreshold` —— 同一条水位线，只是换了量纲
 *    （token / 字符），所以「内核开始丢最老历史」与「会话级自动压缩评估通过」落在同一水位。
 * 2. **cap ≥ 2 时严格低于 [hardCharCap]**（由夹上限保证）：同因子 < 1 ⇒ 触发**先于** L3
 *    字符熔断。W63 修的真实缺陷是旧常数 `50 227`（= 32K 档的 cap 本身）⇒ 32K 档
 *    `committedChars > 50 227` 只在**已超 cap** 时成立，而那一步先被熔断 ⇒ 自动压缩
 *    结构上不可达。`cap = 1` 时不存在「严格低于 cap」的正整数取值，取 `1`
 *    （恒越线，唯一合理行为）。
 *    夹上限是为**将来开放** `policy.compressThreshold` 写点防退化：该字段目前全仓
 *    **无生产写点**（只有默认值与测试覆写，`AgentPolicy.kt:154`）；一旦有人把它设成 1.0，
 *    触发线会等于 cap，缺陷原样复活。
 * 3. **随档位放大**：128K / 1M 档的触发线按同一比例升高 ⇒ 不会再在 ~50k 字符就压
 *    （旧常数在高档位触发过早，白付前缀缓存断点）。
 *
 * ## 两个边界（写实，防下一波当缺陷重开）
 * - **可达性不等式（充分条件，保守）**：L3 闸比的是 `systemChars + promptChars(body)`
 *   （`core-agent/.../loop/PreStepBudget.kt:97/:110`），本触发线比的是**不含 system** 的
 *   `committedChars` ⇒ `systemChars < hardCharCap − trigger = 12 557` 是 32K 档可达的
 *   **充分条件**（不是充要）：它还没扣下一次请求的新输入 `inputChars`，实际余量更小。
 *   system 段变长（或工具声明并入 system）会让 32K 档重新退化为「结构不可达」，
 *   届时应重新评估触发线，而不是宣告已修。
 * - **散文会话偏早**：UI 侧只能用 floor 1.8（拿不到内核 `measured`，与 [compressTargets]
 *   的字符侧同源），散文实测 chars/token ≈ 5.75 ⇒ 触发相对偏早，方向保守，不是缺陷。
 */
fun compactTriggerChars(endpoint: RemoteEndpoint, policy: AgentPolicy): Int {
    val cap = hardCharCap(endpoint, policy)
    val trigger = (cap * policy.compressThreshold).toInt()
    // cap 最小为 1 ⇒ `cap - 1` 可能为 0，`coerceIn(1, 0)` 会抛 IllegalArgumentException。
    return trigger.coerceIn(1, (cap - 1).coerceAtLeast(1))
}
