package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.model.CustomProtocol
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.validateAdvanced
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A-3 / F-1：`endpoints.json` 明文凭据 / 保留键的 **JVM 判据**（W9-00）。
 *
 * 不做新 arch-guard 守卫（新守卫扫空集 ⇒ VACUOUS 1→2 ⇒ G21 判红）。
 * 判据自身先验证：① 已知含凭据变异样本必红（0 命中不算通过）；
 * ② 保存链校验通过后的干净端点序列化 ⇒ 0 命中。真机侧另由主理人补证。
 */
class EndpointPlaintextScanTest {

    /** 明文凭据前缀 + 保留键，与 G17 / 保存链同款。 */
    private val markers = listOf("sk-", "max_tokens")

    private fun serializeAsEndpointsJson(endpoint: RemoteEndpoint): String =
        Json.encodeToString(ListSerializer(RemoteEndpoint.serializer()), listOf(endpoint))

    @Test
    fun knownCredentialMutationIsCaughtByScanner() {
        // 变异样本：强行序列化一份含明文凭据 / 保留键的端点（绕过保存链校验），
        // 扫描器必须命中**真实序列化产物** —— 而非手写字面量（避免恒真断言，AGENTS.md §二.3）。
        // 若序列化路径改动导致 extraBody 丢失，本条会立即红（不再「怎么写都绿」）。
        val leaked = RemoteEndpoint(
            id = "ep-leak",
            customProtocol = CustomProtocol(
                enabled = true,
                extraBody = mapOf("foo" to "sk-live-leak", "max_tokens" to "1"),
            ),
        )
        val json = serializeAsEndpointsJson(leaked)
        assertEquals(2, markers.count { json.contains(it) }, "泄漏端点序列化必须同时命中两个标记，json=" + json)
        assertTrue(json.contains("sk-live-leak"))
        // 反向样本：干净端点序列化不得误报。
        val clean = RemoteEndpoint(id = "ep-clean")
        assertFalse(markers.any { serializeAsEndpointsJson(clean).contains(it) })
    }

    @Test
    fun validatedEndpointSerializesWithoutPlaintextCredentials() {
        // 干净样本：先过保存链（非空即 BLOCKED 不写盘），再读回序列化结果扫明文。
        val clean = RemoteEndpoint(
            id = "ep-clean",
            customProtocol = CustomProtocol(enabled = true, extraBody = mapOf("top_k" to "40")),
        )
        assertTrue(validateAdvanced(clean).isEmpty(), "保存链必须放行干净配置")
        val json = serializeAsEndpointsJson(clean)
        assertEquals(0, markers.count { json.contains(it) }, "json=" + json)

        // 坏 draft：保存链拦下，根本不会走到写盘；强行序列化则必被扫描器抓到。
        val bad = RemoteEndpoint(
            id = "ep-bad",
            customProtocol = CustomProtocol(enabled = true, extraBody = mapOf("foo" to "sk-live-leak")),
        )
        assertTrue(validateAdvanced(bad).isNotEmpty(), "保存链必须 BLOCKED")
        assertTrue(markers.any { serializeAsEndpointsJson(bad).contains(it) })
    }
}
