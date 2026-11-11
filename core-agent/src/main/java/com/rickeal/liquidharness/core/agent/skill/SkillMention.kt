package com.rickeal.liquidharness.core.agent.skill

/**
 * 对话框「`/` 指令」与「`@` 引用」的解析（W16 后用户追加：手动触发 skill）。
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
 * 故本对象只做两件无副作用的事：**解析**输入里的引用、**把引用规范化成一句明确的加载引导**
 * 附加在用户正文之后。真正的正文加载仍由模型经现役 `skill_load` 工具完成（TOOL 角色、
 * 边界声明、14000 截断全部沿用现役实现，零复制）。
 *
 * `@name` 与 `/name` 在解析层等价：`/` 是「指令面板」的触发前缀，`@` 是「引用」的触发前缀，
 * 两者选中的都是同一个 skill，发出的都是同一种引用。
 */
object SkillMention {

    /** 指令面板触发前缀（「添加指令」）。 */
    const val COMMAND_PREFIX = '/'

    /** 引用触发前缀（「引用 skill」）。 */
    const val MENTION_PREFIX = '@'

    /**
     * 引用形态：前缀后紧跟 skill 名（字母 / 数字 / 点 / 下划线 / 连字符）。
     * 名字字符集与 `SkillCatalog` 的 skill 目录名一致，避免把普通句子里的 `@` 当引用。
     */
    private val MENTION = Regex("@([A-Za-z0-9._\\-]+)")

    /**
     * 输入里被引用的 skill 名，去重且保序。
     *
     * 只认 `@name`（`/name` 在 UI 层选中时会被改写成 `@name` 再发送，见 [decorate]），
     * 这样「指令」与「引用」在发送侧收敛为一种线上形态，模型只需认一种记号。
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
     * 把用户正文里的 `@name` 引用规范化，并在尾部附加一句明确的加载引导。
     *
     * 引导文案**刻意用英文**：它进请求体（G16 中文硬编码棘轮基线 0，UI 文案才走 `strings.xml`）。
     * 用户可见的气泡仍显示**原始输入**（去引导后的正文由调用方保证，见调用侧）。
     */
    fun decorate(text: String): String {
        val names = extractMentions(text)
        if (names.isEmpty()) return text
        val guide = names.joinToString(", ") { "@$it" }
        return text.trimEnd() +
            "\n\n(Referenced skill(s): $guide. Load each with skill_load and follow it " +
            "for this task before answering.)"
    }
}
