package com.rickeal.liquidharness.core.agent

import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.SamplingParams

/**
 * 一次 run 的输入。**不可变**：run 期间任何状态变化都进 `RunState`，不进这里
 * （否则「这次 run 到底用的是什么参数」在事后无法回答）。
 *
 * 由 `feature-chat` 的 `RunLauncher` 组装（`01-architecture.md` §五 步骤 1）。
 *
 * 🔴 **不含 API Key**：密钥在 `EndpointSecretStore` 里，由 `core-engine` 在发请求那一刻
 * 现取（`00-brief.md` A7）。本对象会进日志与 journal，带密钥就多两处泄漏面。
 */
data class AgentRequest(

    /** 会话 id。null = 无会话归属的一次性 run（子代理 / 单测）。 */
    val conversationId: String? = null,

    /**
     * 已有历史（不含本次 [userInput]）。
     *
     * 语义是「只读输入」：`TurnLoop` 把它复制成工作副本后**不再回读**它 ——
     * 压缩、工具结果回灌都只改工作副本。这样调用方持有的列表不会被内核改写。
     */
    val history: List<ChatMessage> = emptyList(),

    /** 本次用户输入。会被追加到工作副本尾部（绝不在中段插入，`00-brief.md` A8）。 */
    val userInput: String = "",

    /** 端点配置（含 `kind` / `modelId` / 窗口声明）。**不含密钥**。 */
    val endpoint: RemoteEndpoint,

    /** 采样参数。默认值来自 `SamplingParams`，端点侧可再钳。 */
    val sampling: SamplingParams = SamplingParams(),

    /** 用户自定义的系统指令（设置页里的「自定义人格」）。空串 = 不出现该段。 */
    val systemInstruction: String = "",

    /**
     * 长期记忆正文（W3 接线）。null / 空串 = 不出现该段。
     *
     * ⚠️ 它会**进 system**，因此必须在整个 run 内字节稳定，否则 prompt cache 失效
     * （`00-brief.md` A8）。这也是为什么记忆的读写都必须走「尾部追加」，不能改这里。
     */
    val memoryText: String? = null,

    /**
     * 技能一级披露段（tier-1 元数据：name + description）。
     *
     * W1 **恒传空串**（技能系统是 W4）。留这个入口是为了让「正文永不进 system」
     * 这条纪律（`00-brief.md` A6）从第一天就有明确的落点：
     * 只有元数据能走这里，正文走 user 角色注入。
     */
    val skillsSection: String = "",

    /**
     * 子代理递归深度。0 = 用户直接发起的 run。
     *
     * 旧仓靠「子 run 继承父 run 墙钟」间接约束递归，新仓改显式 depth 预算
     * （`01-architecture.md` §6.3）：`depth >= policy.maxSubagentDepth` ⇒ 子代理工具拒绝。
     */
    val depth: Int = 0,
)
