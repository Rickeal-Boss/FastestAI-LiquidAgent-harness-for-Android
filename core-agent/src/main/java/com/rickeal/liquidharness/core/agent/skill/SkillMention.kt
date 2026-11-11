package com.rickeal.liquidharness.core.agent.skill

/**
 * 对话框「`/` 指令」与「`@` 引用」的解析（W16 后用户追加：手动触发 skill；W17 缺陷收口批）。
 *
 * ## 为什么走「引导模型调 `skill_load`」而不是应用直接注入正文
 *
 * 用户在输入框敲 `@name` / `/name` 选中一个 skill 后，「手动触发」有三种实现，逐条对过红线：
 *
 * 1. **应用把正文塞进一条 USER 消息** —— 踩 `SkillTools` 的 KDoc 红线：USER 角色会伪造用户发言，
 *    让 skill 正文里的越权句子获得用户级权重。手动触发虽是用户主动，但仍不该把整段操作指引
 *    提升为用户指令。
 * 2. **应用伪造一条 TOOL 配对（MODEL.toolCalls + TOOL.toolResults）** —— 踩 `AGENTS.md` §五.3
 *    「改事件序列必须先枚举路径矩阵」，且在历史里插入一条模型并未真正发起的 tool_call，
 *    会污染对话账本（运行账本与 UI 气泡出现「模型没做过的事」）。
 * 3. **应用碰 system 段** —— 破坏「system 字节稳定 ⇒ 前缀缓存命中延续」这条真正的不变量。
 *
 * 故本对象只做无副作用的纯文本处理：**解析**引用、**归一** `/name` 直输形态、**生成**加载引导、
 * **剥离**引导（显示侧）。真正的正文加载仍由模型经现役 `skill_load` 工具完成（TOOL 角色、
 * 边界声明、14000 截断全部沿用现役实现，零复制）。
 *
 * ## W17 三个收口（外审 W16-NEW-1 / P0-3 / N1）
 *
 * 1. **白名单**：`decorate` 只对**已知 skill 名**附加引导 —— 正文里的邮箱（`a@b.com`）、
 *    `@Override` 等任意 `@token` 不再被误当成引用（装饰与面板共用同一份目录事实源）。
 * 2. **`/name` 直输归一**：`extractMentions` 只认 `@`，用户不点下拉、手敲 `/name` 发送时原本
 *    静默失效（引导不附加、无任何报错）。[normalizeLineCommand] 把**行首**的 `/token` 归一为
 *    `@token` —— 刻意不做「正则扩到 `[@/]`」：那会把正文路径（`/sdcard/x`）与注释形态全部
 *    误提取，扩大误伤面（W17 裁决，见验收物 §1.4）。
 * 3. **轮间字节稳定**：发送侧存的是**装饰后**文本（wire = 存储，跨轮请求体逐字节稳定，
 *    不再有「当轮带引导、下一轮 history 从气泡重建时引导消失」的前缀分叉）；气泡**显示**
 *    侧用 [stripDecoration] 剥掉引导还原原文。引导锚点 [GUIDE_SEPARATOR] 与
 *    [decorate] 是同一个常量 —— 改引导文案必须两处同源演进。
 *
 * `@name` 与 `/name` 在发送侧收敛为一种线上形态（都是 `@name`），模型只需认一种记号。
 */
object SkillMention {

    /** 指令面板触发前缀（「添加指令」）。 */
    const val COMMAND_PREFIX = '/'

    /** 引用触发前缀（「引用 skill」）。 */
    const val MENTION_PREFIX = '@'

    /**
     * 引用形态：前缀后紧跟 skill 名（字母 / 数字 / 点 / 下划线 / 连字符）。
     * 名字字符集与 `SkillCatalog` 的 skill 目录名一致。
     */
    private val MENTION = Regex("@([A-Za-z0-9._\\-]+)")

    /** skill 名字符集（归一时校验 token 是否像一个名字，路径 `/sdcard/x` 的 `x` 后有内容不算）。 */
    private val NAME_CHARS = Regex("[A-Za-z0-9._\\-]+")

    /** 引导句锚点：[decorate] 生成、[stripDecoration] 剥离，两处共用同一常量。 */
    private const val GUIDE_SEPARATOR = "\n\n(Referenced skill(s): "

    /**
     * 输入里被引用的 skill 名，去重且保序。
     *
     * 只认 `@name`（全文任意位置 —— 下拉选中回填后的引用就在正文尾部；`/name` 直输由
     * [normalizeLineCommand] 在装饰前归一，见 [decorate]）。
     */
    fun extractMentions(text: String): List<String> =
        MENTION.findAll(text).map { it.groupValues[1] }.distinct().toList()

    /**
     * 输入是否应以 skill 面板响应：以 `/` 或 `@` 开头（可带一个空格）。
     *
     * 只看**开头**而非全文 —— 面板是「用户刚开始敲一个引用」时的辅助，正文中间出现 `@`
     * 不该反复弹面板。
     */
    fun isTrigger(text: String): Boolean {
        val t = text.trimStart()
        return t.startsWith(COMMAND_PREFIX) || t.startsWith(MENTION_PREFIX)
    }

    /**
     * 把**行首**（允许前导空白）的 `/token` 归一为 `@token`；其余位置一律不动。
     *
     * 只处理开头这一处，与 [isTrigger] 的面板语义对齐：面板只因开头触发符弹出，归一也
     * 只因开头触发符发生 —— 两条路径不出现「面板没给选项、引导却照发」的错位。
     * token 必须整体匹配 skill 名字符集且后面是空白/结尾，`/sdcard/x` 这类路径不满足
     * （`x` 后还有内容时 token 截断为 `sdcard/x` 中的一段，字符集不含 `/` ⇒ 不匹配 ⇒ 原样）。
     */
    fun normalizeLineCommand(text: String): String {
        val lead = text.takeWhile { it == ' ' || it == '\t' }
        val rest = text.drop(lead.length)
        if (!rest.startsWith(COMMAND_PREFIX)) return text
        val token = rest.drop(1).takeWhile { !it.isWhitespace() }
        if (token.isEmpty() || !NAME_CHARS.matches(token)) return text
        return lead + MENTION_PREFIX + token + rest.drop(1 + token.length)
    }

    /**
     * 下拉选中一个 skill 后的回填：**只截开头的触发 token**，其余正文原样保留。
     *
     * 修复外审 A3/P1-1：原实现用 `maxOf(lastIndexOf('@'), lastIndexOf('/'))` 取全文末位，
     * 正文含邮箱（`@not 请发到 foo@bar.com`）时会把用户已输入文本从邮箱的 `@` 处砍掉
     * （静默丢字）。面板只因**开头**触发符弹出（[isTrigger]），所以要截的也只是开头那一个
     * token；输入不在触发形态时保守地追加到尾部。
     */
    fun applySkillPick(draft: String, name: String): String {
        val lead = draft.takeWhile { it == ' ' || it == '\t' }
        val rest = draft.drop(lead.length)
        val startsTrigger = rest.startsWith(COMMAND_PREFIX) || rest.startsWith(MENTION_PREFIX)
        if (!startsTrigger) {
            val base = draft.trimEnd()
            return if (base.isEmpty()) "@$name " else "$base @$name "
        }
        val token = rest.drop(1).takeWhile { !it.isWhitespace() }
        val remainder = rest.drop(1 + token.length)
        // 余下正文自带前导空白时不再补空格（否则「@name  请发到」双空格）；
        // 触发 token 后没有正文时留一个尾空格（沿用现役「选中后可继续打字」形态）。
        val tail = when {
            remainder.isEmpty() -> " "
            remainder.first().isWhitespace() -> remainder
            else -> " $remainder"
        }
        return lead + MENTION_PREFIX + name + tail
    }

    /**
     * 气泡**显示**侧剥离引导句：装饰版文本还原为用户原文。
     *
     * 用 [lastIndexOf] 而非 indexOf：正文若恰好含锚点字样，尾部的才是引导。
     * 无引导（未装饰 / 模型 / 系统气泡）原样返回。
     */
    fun stripDecoration(text: String): String {
        val idx = text.lastIndexOf(GUIDE_SEPARATOR)
        return if (idx >= 0) text.take(idx) else text
    }

    /**
     * 把用户正文里的引用规范化，并在尾部附加一句明确的加载引导。
     *
     * @param knownNames 已知 skill 名（来自 `SkillCatalog`，UI 层持有）。**白名单**：
     *    只对名单内的引用附加引导；名单外的 `@token`（邮箱、handle、`@Override`）原样
     *    保留在正文，不注入任何引导 —— 模型不会去 `skill_load` 一个不存在的名字。
     *
     * 引导文案**刻意用英文**：它进请求体（G16 中文硬编码棘轮基线 0，UI 文案才走 `strings.xml`）。
     * 用户可见的气泡显示剥离引导后的原文（`stripDecoration`，调用侧负责）。
     */
    fun decorate(text: String, knownNames: Set<String>): String {
        val normalized = normalizeLineCommand(text)
        val names = extractMentions(normalized).filter { it in knownNames }
        if (names.isEmpty()) return text
        val guide = names.joinToString(", ") { "@$it" }
        return normalized.trimEnd() + GUIDE_SEPARATOR + guide + ". Load each with skill_load " +
            "and follow it for this task before answering.)"
    }
}
