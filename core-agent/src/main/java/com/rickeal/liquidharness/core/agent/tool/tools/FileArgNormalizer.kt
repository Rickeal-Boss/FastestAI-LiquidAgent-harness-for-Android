package com.rickeal.liquidharness.core.agent.tool.tools

/**
 * `file_list` 的 path 归一化：省略 / 纯空白 ⇒ `"."`（沙箱根目录）。
 *
 * 为什么不能只靠 `stringArg` 的默认值：它的默认是**空串**，而 [Sandbox.resolve] 对空路径
 * **fail-closed 拒绝**。spec 已把 path 声明为 `required = false` 且描述写「默认根目录」，
 * 所以「省略」必须由本函数落成 `"."`，否则这条被承诺的用法永远失败。
 *
 * 为什么用 `"."` 而不是给 [Sandbox] 开「空路径放行」的口子：`File(base, ".")` 规范化后
 * 就是沙箱根本身，走既有的「相等放行」分支天然通过 —— 给安全护栏开特例会削弱它对
 * **所有**工具的 fail-closed 语义。
 *
 * 独立成 `internal` 顶层函数以便 JVM 单测（工具类需要 `ToolContext`，纯 JVM 测试
 * 不应构造 Android `Context`）。
 */
internal fun fileListPathArg(rawPath: String): String = rawPath.trim().ifEmpty { "." }
