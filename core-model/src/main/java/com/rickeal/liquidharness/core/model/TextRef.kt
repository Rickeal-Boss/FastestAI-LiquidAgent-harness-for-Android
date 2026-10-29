package com.rickeal.liquidharness.core.model

/**
 * 用户可见文案的**引用**，不是文案本身。
 *
 * `core-*` 模块不持有字符串资源（纯 Kotlin，没有 `Context`），所以任何需要上屏的
 * 文案都只能返回这个密封类型，由 `:app` 解析成真实字符串
 * （`docs/01-architecture.md` §十）。
 *
 * 两个变体的边界是硬的：
 * - [Res]：**我们写的**文案（错误提示、按钮、状态）。必须进 `strings.xml`，
 *   否则守卫 N3（中文硬编码棘轮，基线 0）会判红。
 * - [Raw]：**别人写的**文本（模型产出、用户输入、MCP 远端工具描述）。
 *   这些不能进 `strings.xml`（是运行时数据），但它们**不可信** ——
 *   上屏前必须过 [SecretRedactor]，且不得被当作富文本解析。
 */
sealed interface TextRef {
    /**
     * @param id `R.string.*` 的整型 id。契约层不引 `androidx.annotation`，
     *   所以这里只有裸 Int，没有 `@StringRes`。
     * @param args 格式化参数（`%1$s` 等），按 `String.format` 的顺序。
     */
    data class Res(val id: Int, val args: List<Any> = emptyList()) : TextRef

    /** 运行时文本。**只允许**用于模型产出与用户输入。 */
    data class Raw(val text: String) : TextRef

    companion object {
        /** 便捷构造：无参文案。 */
        fun res(id: Int): TextRef = Res(id, emptyList())

        /** 便捷构造：带参文案。 */
        fun res(id: Int, vararg args: Any): TextRef = Res(id, args.toList())
    }
}
