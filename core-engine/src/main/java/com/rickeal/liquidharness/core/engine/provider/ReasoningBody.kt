package com.rickeal.liquidharness.core.engine.provider

import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.ThinkingMode
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * OpenRouter 统一 `reasoning` 对象构造器（W8）。
 *
 * 单独成文件的理由：`OpenAiCompatibleAdapter.applyReasoningStyle` 的 `when` 有 G11 的 120 行上限，
 * 而思考三态 + 降级语义值得写清 KDoc —— 塞进去会触顶。
 *
 * ## 三态的线上形态（主理人 `docs/analysis/w8-provider-probe.md` 流式实测）
 *
 * | 意图 | 发什么 | 实测效果 |
 * |---|---|---|
 * | [ThinkingMode.OFF] | `reasoning: {"enabled": false}` | rtok=0、正文 `PONG` 连前导换行都消失 |
 * | [ThinkingMode.HIDE_ONLY] | `include_reasoning: false` | delta 不带思考，但 `reasoning_tokens` 照付 |
 * | [ThinkingMode.ON] / REASONING_ONLY | `reasoning: {"effort": wire}` | 正常思考 |
 * | [ThinkingMode.FOLLOW_PROVIDER] | 不写任何字段 | dots 默认就开思考 ⇒ **不等于关思考** |
 *
 * 🔴 **`effort` 与 `max_tokens` 绝不共存**于同一 `reasoning` 对象 —— 本构造器只放 `effort`。
 * 🔴 「关闭思考」不可达时**降级成 HIDE_ONLY 并落 `reason`**（`hard_off_unsupported`），
 * 不静默发一个网关不认的字段 —— 静默发未验证值是 W5-01「判据自身先被验证」的反面。
 */
internal fun buildReasoningObject(endpoint: RemoteEndpoint, body: JsonObjectBuilder) {
    when (endpoint.effectiveThinkingMode) {
        ThinkingMode.OFF -> {
            if (endpoint.canHardOff) {
                body.put("reasoning", buildJsonObject { put("enabled", false) })
            } else {
                // 降级：真关不可达（能力位未开 / 未声明 mandatory:false / style 不对）。
                body.put("include_reasoning", false)
            }
        }

        ThinkingMode.HIDE_ONLY -> body.put("include_reasoning", false)

        ThinkingMode.ON, ThinkingMode.REASONING_ONLY -> {
            // REASONING_ONLY 不发服务端开关：它是客户端期望（只展示思考块），
            // 若正文为空不能判成断流 —— 那是 W9-09 的范围。
            val effort = endpoint.resolvedEffort()
            if (effort.wire != null) {
                body.put("reasoning", buildJsonObject { put("effort", effort.wire) })
            }
        }

        ThinkingMode.FOLLOW_PROVIDER -> Unit
    }
}
