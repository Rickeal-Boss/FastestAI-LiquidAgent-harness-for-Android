package com.rickeal.liquidharness.core.agent.context

import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.ToolSpec
import java.security.MessageDigest

/**
 * 前缀段的身份。**顺序即线上顺序**，与 [com.rickeal.liquidharness.core.agent.prompt.AgentPromptComposer]
 * 的段序一一对应。
 *
 * ## 为什么只有四段（而不是 design 草案里的九段）
 *
 * 草案把 system 拆成六个子段（人格 / 护栏 / 技能 / 记忆 / 记忆维护 / 停止条件），目的是给
 * 将来的 Anthropic `cache_control` 断点留落点。但本波**明确不做断点标记**，而指纹只需要
 * 「哪一段变了」这一个粒度。段拆得越细，账里同一次变化会同时命中好几段（改一句人格会
 * 同时命中 SYSTEM），噪音换不来分辨力。
 *
 * 故本波只留四段，且**技能段必须单独可定位**（[SKILLS]）：技能元数据是唯一一个
 * 「不进工具面但会改前缀」的输入，与 SYSTEM 混成一段就无法回答「是技能变了还是人格变了」。
 * W6 做 Anthropic 渲染时再按子段拆，届时 `AgentPromptComposer.sectionsTyped` 是唯一改动点。
 */
enum class PrefixSegmentKind {
    /** system 提示词里除技能段以外的全部字节（人格 / 护栏 / 记忆 / 停止条件）。 */
    SYSTEM,

    /** 技能一级披露段。**与 SYSTEM 有重叠**（它物理上也在 system 里），单列是为了可分辨。 */
    SKILLS,

    /** 工具声明。线上是 `body.tools`，不在 `messages` 里，但同属「前缀」。 */
    TOOLS_SCHEMA,

    /** 历史头部（前 [HISTORY_HEAD_SIZE] 条）。只取头部，理由见 [fingerprintOf]。 */
    HISTORY,
}

/**
 * 一段的稳定性契约。**这不是注释，是测试断言的依据**（`PromptPrefixTest` 逐档钉住）。
 *
 * - [BUILD_STABLE]：构建期常量，跨 run 都不变（人格 / 护栏 / 停止条件）。
 * - [RUN_STABLE]：run 内快照，跨 run 可变（工具面）。
 * - [APPEND_ONLY]：只允许尾部追加；**头部变化即视为前缀失效**（历史）。
 * - [EVERY_ROUND]：每轮必然变。**不参与指纹** —— 算它只会把「正常追加」误报成「前缀被改」。
 */
enum class SegmentStability { BUILD_STABLE, RUN_STABLE, APPEND_ONLY, EVERY_ROUND }

/** 一段前缀。`text` 是参与哈希的**规范化渲染**，不要求逐字节等于线上 JSON（见 [renderToolSchema]）。 */
data class PrefixSegment(
    val kind: PrefixSegmentKind,
    val stability: SegmentStability,
    val text: String,
)

/**
 * 分段指纹。每个 hash 取 SHA-256 的**前 12 位十六进制**（48 bit）。
 *
 * 48 bit 而不是全量：账行要能一眼读完，而「同一条 run 内两次算出的前缀相同」这个判据
 * 在 48 bit 下的碰撞概率可以忽略（生日界约 2^-24 量级，远低于「用户手工改一次提示词」的量级）。
 */
data class PrefixFingerprint(
    val combined: String,
    val perSegment: Map<PrefixSegmentKind, String>,
) {
    /**
     * 与上一次相比变了的段。**空 = 逐字节相同**。
     *
     * `previous == null`（本 run 第一次算）⇒ 返回空：没有「上一次」可比，报「变了」只会
     * 让每个 run 的第一轮都刷一条假 WARN。
     */
    fun changedFrom(previous: PrefixFingerprint?): List<PrefixSegmentKind> {
        if (previous == null) return emptyList()
        val changed = ArrayList<PrefixSegmentKind>(2)
        for (kind in PrefixSegmentKind.entries) {
            if (perSegment[kind] != previous.perSegment[kind]) changed.add(kind)
        }
        return changed
    }

    /** 账行用的一行标签：`none` 或逗号分隔的段名（ASCII）。 */
    fun changedLabel(previous: PrefixFingerprint?): String {
        val changed = changedFrom(previous)
        return if (changed.isEmpty()) "none" else changed.joinToString(",") { it.name }
    }
}

/** 历史段的头部条数。4 = design §5 风险 3 给出的 K 值。 */
const val HISTORY_HEAD_SIZE: Int = 4

/**
 * 纯函数：把若干段折成一个指纹。**零 IO、零协程、确定性**（同输入同字节）。
 *
 * ## 只对稳定段算，`EVERY_ROUND` 一律剔除
 *
 * 设计约束（design §3.A.2 / §5 风险 3）：指纹要回答「前缀被**谁**改了」，不是「这一轮
 * 新增了什么」。把本轮新增（VOLATILE_TAIL 类）算进去，每次工具结果回灌都会报「前缀变了」，
 * 那个信号就永久为真、等于没有。
 *
 * ## 为什么 HISTORY 只取头部 K 条，不取「前 K + 末 1」
 *
 * design §5 风险 3 的升级建议是「前 K 条 + 末 1 条的 hash」。**本实现只取前 K 条**，
 * 理由是可证伪的：末 1 条每轮都在位移（追加一条，上一轮的末条就变成倒数第二条），
 * 于是「前 K + 末 1」在**每一次正常追加**后都会变 ⇒ 直接与判据 A3（同 run 内追加一条
 * 工具结果后指纹必须不变）冲突。§3.A.2 自己也写了同一句话：「它每轮都在追加，必然变；
 * 算它只会把正常追加误报成前缀被改」。
 *
 * 代价（如实登记）：历史**中段内容**变化（条数不变、头部不变）检测不到。这需要全量
 * hash 整段历史，而那是 O(历史) 的每轮开销（W2.5 §4.3 已判死）。真正会改历史中段的
 * 只有 `sanitizeForProvider` 的补丁消息，它用的是稳定派生 id，且会改条数/头部时被
 * HISTORY 段捕获。
 *
 * @param segments 段的顺序无关（内部按 [PrefixSegmentKind] 声明序归一）；
 *   同 kind 出现多次时**后者生效**（调用方不该重复，这里只做确定性兜底）。
 */
fun fingerprintOf(segments: List<PrefixSegment>): PrefixFingerprint {
    val byKind = LinkedHashMap<PrefixSegmentKind, PrefixSegment>(segments.size)
    for (segment in segments) byKind[segment.kind] = segment

    val perSegment = LinkedHashMap<PrefixSegmentKind, String>(byKind.size)
    val combined = StringBuilder()
    for (kind in PrefixSegmentKind.entries) {
        val segment = byKind[kind] ?: continue
        // EVERY_ROUND 不参与：见上「只对稳定段算」。
        if (segment.stability == SegmentStability.EVERY_ROUND) continue
        // 空段 = 该段本轮不存在（例如没有技能）。空串不参与，否则「有没有这一段」不可分辨。
        if (segment.text.isEmpty()) continue
        val hash = sha256Prefix(segment.text)
        perSegment[kind] = hash
        combined.append(kind.name).append('=').append(hash).append('\n')
    }
    return PrefixFingerprint(sha256Prefix(combined.toString()), perSegment)
}

/** 造 SYSTEM 段。 */
fun systemSegment(text: String): PrefixSegment =
    PrefixSegment(PrefixSegmentKind.SYSTEM, SegmentStability.BUILD_STABLE, text)

/** 造 SKILLS 段。 */
fun skillsSegment(text: String): PrefixSegment =
    PrefixSegment(PrefixSegmentKind.SKILLS, SegmentStability.BUILD_STABLE, text)

/** 造 TOOLS_SCHEMA 段（**run 内快照**，见 `ContextBudget` 的 `toolSpecsFor`）。 */
fun toolSchemaSegment(specs: List<ToolSpec>): PrefixSegment =
    PrefixSegment(PrefixSegmentKind.TOOLS_SCHEMA, SegmentStability.RUN_STABLE, renderToolSchema(specs))

/** 造 HISTORY 段（前 [HISTORY_HEAD_SIZE] 条）。 */
fun historySegment(messages: List<ChatMessage>): PrefixSegment =
    PrefixSegment(PrefixSegmentKind.HISTORY, SegmentStability.APPEND_ONLY, renderHistoryHead(messages))

/**
 * 工具声明的**规范化渲染**，只用于哈希与诊断，**不是线上报文**。
 *
 * 为什么不复用 `core-engine` 的 schema 编码器：`core-agent` 不许依赖 `core-engine`
 * （模块边界，`AGENTS.md` §四）。而指纹只需要「任何会影响工具面的字段变化 ⇒ 哈希变」，
 * 不需要与线上 JSON 逐字节一致 —— 需要一致的是「同一份 spec 每次渲染出同一串字节」，
 * 这一点由确定性拼接保证。
 *
 * 覆盖的字段就是「会让模型行为不同」的那些：name / description / 必填与危险标志 /
 * 每个参数的 name / 类型 / 是否必填 / 描述 / 枚举值。刻意**不**含 `category`、
 * `keywords`、`timeoutMillisOverride`、`source`、`namespace` —— 它们不进模型可见的
 * 工具面，纳入只会制造噪音（改一个 UI 分组名不该报「前缀变了」）。
 */
fun renderToolSchema(specs: List<ToolSpec>): String {
    if (specs.isEmpty()) return ""
    val sb = StringBuilder()
    for (spec in specs) {
        sb.append("tool ").append(spec.name).append('|')
        sb.append(spec.description).append('|')
        sb.append(if (spec.requiresConfirmation) '1' else '0')
        sb.append(if (spec.dangerous) '1' else '0')
        sb.append('|')
        for (parameter in spec.parameters) {
            sb.append(parameter.name).append(':')
            sb.append(parameter.type.jsonName).append(':')
            sb.append(if (parameter.required) '1' else '0').append(':')
            sb.append(parameter.description).append(':')
            sb.append(parameter.enumValues.joinToString(",")).append(';')
        }
        sb.append('\n')
    }
    return sb.toString()
}

/**
 * 历史头部的规范化渲染。含 id：id 变化同样让前缀字节变化（`sanitizeForProvider` 的
 * 补丁 id 之所以要稳定派生，就是这个理由）。
 */
fun renderHistoryHead(messages: List<ChatMessage>, k: Int = HISTORY_HEAD_SIZE): String {
    if (messages.isEmpty() || k <= 0) return ""
    val sb = StringBuilder()
    for (message in messages.take(k)) {
        sb.append(message.role.name).append(':').append(message.id).append(':').append(message.text)
        for (call in message.toolCalls) {
            sb.append("|call:").append(call.id).append(',').append(call.name).append(',').append(call.argumentsJson)
        }
        for (result in message.toolResults) {
            sb.append("|result:").append(result.callId).append(',').append(result.output)
        }
        sb.append('\n')
    }
    return sb.toString()
}

private const val HASH_HEX_CHARS = 12

private val HEX = "0123456789abcdef".toCharArray()

/** SHA-256 前 [HASH_HEX_CHARS] 位十六进制。 */
private fun sha256Prefix(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    val sb = StringBuilder(HASH_HEX_CHARS)
    var index = 0
    while (sb.length < HASH_HEX_CHARS) {
        val value = digest[index].toInt() and 0xFF
        sb.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        index += 1
    }
    return sb.toString()
}
