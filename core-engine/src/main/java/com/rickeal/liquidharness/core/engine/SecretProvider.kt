package com.rickeal.liquidharness.core.engine

/**
 * 密钥读取端口。
 *
 * 为什么不直接传 `String`：`GenerationRequest` 会进 journal、进日志、进内存 dump。
 * 密钥必须在**发请求那一刻**现取，用完即弃（00-brief A7）。
 *
 * 实现方：`core-data` 的 `EndpointSecretStore`（AndroidKeyStore 信封）。
 * JVM 单测里用 `SecretProvider { "test-key" }`。
 */
fun interface SecretProvider {
    /** @return 该端点的明文密钥；未配置返回 null。 */
    fun secretFor(endpointId: String): String?

    companion object {
        /** 无密钥场景（本地 Ollama）。 */
        val None: SecretProvider = SecretProvider { null }
    }
}
