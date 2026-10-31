package com.rickeal.liquidharness.core.agent.tool.tools

import android.content.ClipData
import android.content.ClipboardManager
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.EffectAwareTool
import com.rickeal.liquidharness.core.agent.tool.ParamGatedTool
import com.rickeal.liquidharness.core.agent.tool.PolicyGatedTool
import com.rickeal.liquidharness.core.agent.tool.ToolContext
import com.rickeal.liquidharness.core.model.ToolEffect
import com.rickeal.liquidharness.core.model.ToolParameter
import com.rickeal.liquidharness.core.model.ToolParamType
import com.rickeal.liquidharness.core.model.ToolResult
import com.rickeal.liquidharness.core.model.ToolSpec

/**
 * `clipboard`：读 / 写系统剪贴板。**同时实现 [ParamGatedTool]、[EffectAwareTool] 与 [PolicyGatedTool]。**
 *
 * ## 为什么必须按参数判定（W2 判据 #4）
 *
 * 同一个工具两种参数风险完全不同：`get` 无害，`set` 覆写用户剪贴板（可能覆盖正在复制的
 * 密码 / 验证码）。整工具标 `requiresConfirmation` 会连 `get` 一起弹卡 —— 那是噪音，
 * 会训练用户无脑点同意，反而削弱审批的意义。
 *
 * | 参数 | 是否要授权 | 本次效果 |
 * |---|---|---|
 * | `get` | 否 | READ（只读档位下静默放行） |
 * | `set` | 是 | WRITE |
 * | 解析失败 / 未知 action | **是**（fail-closed） | **WRITE**（fail-closed） |
 *
 * ## `get` 不弹卡 ≠ 永远放行（[PolicyGatedTool]）
 *
 * `get` 读到的内容会进模型上下文，等于一条用户看不见的外带通道（提示注入最爱用它）。
 * 所以除了参数级闸门（决定「弹不弹卡」），还要一道策略闸门（决定「跑不跑」）：
 * `AgentPolicy.allowClipboardRead = false` ⇒ `blockedBy` 返回原因码，
 * 执行器在**工具体被调用之前**就产出失败结果 —— 用户点不点同意都改变不了这个结论。
 *
 * 三个判定方法都是纯函数（无状态、无 IO），符合接口契约。
 *
 * 主构造器只收 `ClipboardManager?`：`invoke` 不需要 `Context`（`ClipData.Item.text` 足够
 * 处理纯文本），JVM 单测因此可以直接构造本类并断言判定方法（传 `null` 即「剪贴板
 * 不可用」分支）。
 */
class ClipboardTool internal constructor(
    private val clipboard: ClipboardManager?,
) : ParamGatedTool, EffectAwareTool, PolicyGatedTool {

    constructor(context: ToolContext) : this(context.clipboard)

    override val spec: ToolSpec = ToolSpec(
        name = "clipboard",
        description = "Read or write the system clipboard. action is get or set; set requires text",
        parameters = listOf(
            ToolParameter(
                "action",
                ToolParamType.STRING,
                "get or set",
                enumValues = listOf("get", "set"),
            ),
            ToolParameter("text", ToolParamType.STRING, "text to write when action is set", required = false),
        ),
        category = "system",
        // 静态声明取保守值 WRITE：只有 action 明确解析为 get 时才由 effectFor 降为 READ。
        effect = ToolEffect.WRITE,
    )

    override fun requiresConfirmationFor(argumentsJson: String): Boolean = actionOf(argumentsJson) != Action.GET

    override fun effectFor(argumentsJson: String): ToolEffect =
        if (actionOf(argumentsJson) == Action.GET) ToolEffect.READ else ToolEffect.WRITE

    /**
     * 策略闸门：只有「读」受 `allowClipboardRead` 约束；写侧本来就要审批，不再叠一层。
     * 未知 action 不在这里拦（交给参数校验与 `invoke` 的 fail-closed 分支），
     * 否则「策略关读」会被误报成所有剪贴板调用的原因。
     */
    override fun blockedBy(policy: AgentPolicy, argumentsJson: String): String? =
        if (!policy.allowClipboardRead && actionOf(argumentsJson) == Action.GET) READ_DISABLED else null

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val action = actionOf(argumentsJson)
        // 未知 action 先判：它既不是读也不是写，不该被「剪贴板不可用」这句更模糊的错误盖住。
        if (action == Action.OTHER) return failure("unknown action, expected get or set")
        val manager = clipboard ?: return failure("clipboard unavailable")
        return when (action) {
            Action.SET -> {
                val text = stringArg(argumentsJson, "text")
                manager.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
                ToolResult(
                    name = spec.name,
                    ok = true,
                    output = "wrote " + text.length + " chars to the clipboard",
                    indeterminate = false,
                )
            }
            Action.GET -> {
                val clip = manager.primaryClip
                val text = if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0).text?.toString().orEmpty()
                } else {
                    ""
                }
                ToolResult(name = spec.name, ok = true, output = text.ifBlank { "(clipboard is empty)" })
            }
            Action.OTHER -> failure("unknown action, expected get or set")
        }
    }

    private fun failure(message: String): ToolResult =
        ToolResult(name = spec.name, ok = false, errorMessage = message)

    /** 参数解析：任何解析不出的形态都落到 [Action.OTHER]（调用方按 fail-closed 处理）。 */
    private fun actionOf(argumentsJson: String): Action = when (stringArg(argumentsJson, "action").trim()) {
        "get" -> Action.GET
        "set" -> Action.SET
        else -> Action.OTHER
    }

    private enum class Action { GET, SET, OTHER }

    private companion object {
        /** 剪贴板条目的来源标签（Android 会把它展示给用户，保持与包名同源的短标识）。 */
        const val CLIP_LABEL = "LiquidHarness"

        /** ASCII 原因码（守卫 G16）：用户文案由 UI 按 reason 映射。 */
        const val READ_DISABLED = "clipboard read disabled by policy"
    }
}
