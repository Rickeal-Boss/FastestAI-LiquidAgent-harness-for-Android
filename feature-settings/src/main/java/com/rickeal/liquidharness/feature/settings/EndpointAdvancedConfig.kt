package com.rickeal.liquidharness.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rickeal.liquidharness.core.design.GlassSettingRow
import com.rickeal.liquidharness.core.design.GlassSwitch
import com.rickeal.liquidharness.core.design.GlassTextField
import com.rickeal.liquidharness.core.design.LocalGlassColors
import com.rickeal.liquidharness.core.model.Modality
import com.rickeal.liquidharness.core.model.ReasoningEffort
import com.rickeal.liquidharness.core.model.ReasoningStyle
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.ThinkingMode
import com.rickeal.liquidharness.core.model.carriesEffort
import com.rickeal.liquidharness.core.model.hardOffDowngraded
import com.rickeal.liquidharness.core.model.parseExtraBody
import com.rickeal.liquidharness.core.model.supportsOpenAiAdvanced
import com.rickeal.liquidharness.core.model.uiKey

/**
 * 端点高级配置区（W8）。开关项走**数据驱动**（[ToggleSpec] + [RenderToggle]）——
 * 14 个开关若各写 boilerplate 会把 feature-settings 顶过 G10 上限。
 * 五档与六档窗口同用 [GlassSettingRow] 列表：选中态与降级原因都能被真机取证。
 * `ReasoningEffort.EXTREME` 只是 UI 档位，线上由 `normalizeEffort` 降级（永不 `max`）。
 */
@Composable
fun AdvancedConfigSection(initial: RemoteEndpoint, onChange: (RemoteEndpoint) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    GlassSettingRow(
        title = stringResource(R.string.adv_section),
        onClick = { open = !open },
        trailing = {
            Text(
                text = stringResource(if (open) R.string.adv_collapse else R.string.adv_expand),
                style = MaterialTheme.typography.labelSmall,
                color = LocalGlassColors.current.onGlassSubtle,
            )
        },
    )
    if (!open) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupLabel(stringResource(R.string.adv_group_capabilities))
        capabilityToggles(initial).forEach { spec -> RenderToggle(initial, onChange, spec) }
        GroupLabel(stringResource(R.string.adv_group_thinking))
        // W9-00 P0-2/P1-1：降级 reason 必须上屏（判定逻辑在 core-model，这里只渲染）。
        val hardOffDowngraded = initial.hardOffDowngraded
        val effortReason = initial.resolvedEffort().reason
        ThinkingMode.entries.filter { it != ThinkingMode.REASONING_ONLY }.forEach { mode ->
            PickRow(
                title = thinkingModeLabel(mode),
                subtitle = when {
                    mode == ThinkingMode.HIDE_ONLY -> stringResource(R.string.adv_thinking_mode_sub)
                    mode == ThinkingMode.OFF && hardOffDowngraded -> stringResource(R.string.adv_hard_off_downgraded)
                    else -> null
                },
                selected = mode == initial.thinkingMode,
                enabled = initial.supportsReasoning,
            ) { onChange(initial.copy(thinkingMode = mode)) }
        }
        RenderToggle(initial, onChange, hardOffToggle(initial))
        ReasoningEffort.DISPLAY_ORDER.forEach { effort ->
            PickRow(
                title = effortLabel(effort),
                subtitle = if (effort.uiKey == initial.reasoningEffort.lowercase() && effortReason != null)
                    stringResource(R.string.adv_effort_downgraded, effortReason) else null,
                selected = effort.uiKey == initial.reasoningEffort.lowercase(),
                enabled = initial.reasoningStyle.carriesEffort(),
            ) { onChange(initial.copy(reasoningEffort = effort.uiKey)) }
        }
        GroupLabel(stringResource(R.string.adv_supported_efforts))
        ReasoningEffort.DISPLAY_ORDER.forEach { effort ->
            RenderToggle(initial, onChange, supportedEffortToggle(initial, effort))
        }
        GroupLabel(stringResource(R.string.adv_group_io))
        // W9-00 P2：AUDIO 无供应商 / 无 TTS 通道，输出模态只留 TEXT/IMAGE，不摆假入口。
        Modality.entries.filter { it != Modality.AUDIO }.forEach { modality ->
            RenderToggle(initial, onChange, outputModalityToggle(initial, modality))
        }
        GroupLabel(stringResource(R.string.adv_group_custom))
        RenderToggle(initial, onChange, customProtocolToggle(initial))
        if (initial.customProtocol.enabled) {
            ExtraBodyField(initial.id, initial.customProtocol.extraBody) { next ->
                onChange(initial.copy(customProtocol = initial.customProtocol.copy(extraBody = next)))
            }
        }
    }
}

/** 一个布尔开关项：`apply` 在 [RemoteEndpoint] 上返回改完的新实例（调用方统一 onChange）。 */
private class ToggleSpec(
    val titleRes: Int,
    val subRes: Int?,
    val checked: Boolean,
    val enabled: Boolean = true,
    /** 禁用时展示的原因文案（禁用态入口 + 无障碍原因，不静默隐藏）。 */
    val blockedSubRes: Int? = null,
    val apply: RemoteEndpoint.(Boolean) -> RemoteEndpoint,
)

private fun capabilityToggles(e: RemoteEndpoint): List<ToggleSpec> = listOf(
    ToggleSpec(R.string.adv_tools, R.string.adv_tools_sub, e.supportsTools) { copy(supportsTools = it) },
    ToggleSpec(
        R.string.adv_image_input,
        R.string.adv_image_input_sub,
        Modality.IMAGE in e.inputModalities,
    ) { v -> copy(inputModalities = if (v) inputModalities + Modality.IMAGE else inputModalities - Modality.IMAGE) },
    ToggleSpec(R.string.adv_json_mode, R.string.adv_json_mode_sub, e.jsonMode, e.supportsOpenAiAdvanced, R.string.adv_kind_unsupported) { copy(jsonMode = it) },
)

private fun hardOffToggle(e: RemoteEndpoint): ToggleSpec =
    ToggleSpec(R.string.adv_allow_hard_off, R.string.adv_allow_hard_off_sub, e.allowDisableThinking) {
        copy(allowDisableThinking = it)
    }

private fun supportedEffortToggle(e: RemoteEndpoint, effort: ReasoningEffort): ToggleSpec =
    ToggleSpec(effortLabelRes(effort), R.string.adv_supported_efforts_sub, effort in e.supportedEfforts) { v ->
        copy(supportedEfforts = if (v) supportedEfforts + effort else supportedEfforts - effort)
    }

private fun outputModalityToggle(e: RemoteEndpoint, modality: Modality): ToggleSpec =
    ToggleSpec(modalityLabelRes(modality), null, modality in e.outputModalities) { v ->
        copy(outputModalities = if (v) outputModalities + modality else outputModalities - modality)
    }

private fun customProtocolToggle(e: RemoteEndpoint): ToggleSpec =
    ToggleSpec(R.string.adv_custom_enabled, null, e.customProtocol.enabled, e.supportsOpenAiAdvanced, R.string.adv_kind_unsupported) { v ->
        copy(customProtocol = customProtocol.copy(enabled = v))
    }

@Composable
private fun RenderToggle(initial: RemoteEndpoint, onChange: (RemoteEndpoint) -> Unit, spec: ToggleSpec) {
    ToggleRow(
        title = stringResource(spec.titleRes),
        subtitle = spec.subRes?.let { stringResource(it) },
        checked = spec.checked,
        enabled = spec.enabled,
        blockedSub = spec.blockedSubRes?.let { stringResource(it) },
    ) { onChange(spec.apply(initial, it)) }
}

/** 禁用时**保留入口**并展示原因（不静默隐藏、也不静默无效）—— W9-00 P2 kind 收窄。 */
@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean, blockedSub: String?, onToggle: (Boolean) -> Unit) {
    GlassSettingRow(
        title = title,
        subtitle = if (!enabled) blockedSub ?: subtitle else subtitle,
        enabled = enabled,
        onClick = { onToggle(!checked) },
        trailing = { GlassSwitch(checked = checked, enabled = enabled, onCheckedChange = onToggle) },
    )
}

@Composable
private fun PickRow(title: String, subtitle: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    GlassSettingRow(
        title = title,
        // 降级原因优先于「已选」：选中档位也要能看到为什么被降级。
        subtitle = subtitle ?: if (selected) stringResource(R.string.adv_selected) else null,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalGlassColors.current.onGlassSubtle,
    )
}

@Composable
private fun thinkingModeLabel(mode: ThinkingMode): String = stringResource(
    when (mode) {
        ThinkingMode.OFF -> R.string.adv_mode_OFF
        ThinkingMode.FOLLOW_PROVIDER -> R.string.adv_mode_FOLLOW_PROVIDER
        ThinkingMode.HIDE_ONLY -> R.string.adv_mode_HIDE_ONLY
        else -> R.string.adv_mode_ON
    },
)

@Composable
private fun effortLabel(effort: ReasoningEffort): String = stringResource(effortLabelRes(effort))

private fun effortLabelRes(effort: ReasoningEffort): Int = when (effort) {
    ReasoningEffort.LOW -> R.string.adv_effort_LOW
    ReasoningEffort.MEDIUM -> R.string.adv_effort_MEDIUM
    ReasoningEffort.HIGH -> R.string.adv_effort_HIGH
    ReasoningEffort.XHIGH -> R.string.adv_effort_XHIGH
    ReasoningEffort.EXTREME -> R.string.adv_effort_EXTREME
}

private fun modalityLabelRes(modality: Modality): Int = when (modality) {
    Modality.TEXT -> R.string.adv_modality_TEXT
    Modality.IMAGE -> R.string.adv_modality_IMAGE
    Modality.AUDIO -> R.string.adv_modality_AUDIO
}

/** 文本状态只按 endpointId 初始化一次：以派生 Map 作 key 会在每次 onValueChange 重组时吞掉输入（P1-2）。 */
@Composable
private fun ExtraBodyField(endpointId: String, current: Map<String, String>, onChange: (Map<String, String>) -> Unit) {
    var text by rememberSaveable(endpointId) {
        mutableStateOf(current.entries.joinToString(", ") { "${it.key}=${it.value}" })
    }
    GlassTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(parseExtraBody(it))
        },
        placeholder = stringResource(R.string.adv_custom_hint),
        singleLine = true,
        modifier = Modifier,
    )
}
