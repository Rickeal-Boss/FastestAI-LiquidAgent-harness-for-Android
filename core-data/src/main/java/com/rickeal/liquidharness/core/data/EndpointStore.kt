package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.model.ProviderKind
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.UiPreset
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer

/**
 * 端点清单的 CRUD。持久化是一份 JSON 数组（`endpoints.json`），**不含密钥** ——
 * 密钥在 [EndpointSecretStore]，清单里只有 `hasSecret: Boolean`。
 *
 * ## 预置策略（为什么「只种一次」）
 *
 * 首次启动（文件不存在）写入三组预置端点，方便用户开箱即用。但**文件一旦存在就
 * 绝不补种**：用户删掉 Ollama 是因为他不想要，不是因为他没配置好。
 * 「每次启动补齐预置」会让删除操作永远无效，这是最容易被写成 bug 的一条。
 *
 * ## 为什么用固定 id 而不是 `newId()`
 *
 * 预置端点的 id 参与密钥文件名（`secrets/<id>.bin`）与「当前活跃端点」的持久化。
 * 随机 id 每次首启都不同，等于把「活跃端点」指向一个已经不存在的 id。
 */
class EndpointStore(
    private val files: JsonFileStore,
    private val seeds: List<RemoteEndpoint> = defaultSeeds(),
) {

    /** 串行化读改写：两个并发的 upsert 各写一次文件会丢掉其中一次修改。 */
    private val mutex = Mutex()

    suspend fun list(): List<RemoteEndpoint> = mutex.withLock { loadOrSeed() }

    suspend fun get(id: String): RemoteEndpoint? = list().firstOrNull { it.id == id }

    /** 同 id 覆盖，否则追加。返回写后的完整清单（按写入顺序）。 */
    suspend fun upsert(endpoint: RemoteEndpoint): List<RemoteEndpoint> = mutex.withLock {
        val current = loadOrSeed()
        val next = if (current.any { it.id == endpoint.id }) {
            current.map { if (it.id == endpoint.id) endpoint else it }
        } else {
            current + endpoint
        }
        persist(next)
        next
    }

    suspend fun remove(id: String): List<RemoteEndpoint> = mutex.withLock {
        val next = loadOrSeed().filterNot { it.id == id }
        persist(next)
        next
    }

    /**
     * 写回**实测**窗口（W5-02 的学习式写回，唯一数据来源是真实运行的 400）。
     *
     * ## 三条约束
     *
     * 1. **只接受正数**：`maximum context length is 0` 之类的报文是网关噪声，
     *    写进去会让预算算式变成 0 ⇒ 下一次 run 必然误熔断。
     * 2. **端点不存在就什么都不做**（返回 null）：调用方可能是在「删端点」之后收到
     *    迟到的失败事件，这里不许把已删的端点复活（不 `upsert`，只 `map`）。
     * 3. **值没变就不落盘**：这个写回会在每次超窗失败后发生，无脑重写文件是白 IO，
     *    而且会让 `endpoints.json` 的 mtime 抖动（诊断时看不出「配置到底改过没有」）。
     *
     * @return 写后的完整清单；`null` = 参数非法 / 端点不存在（**未写盘**）。
     */
    suspend fun recordVerifiedContextLength(id: String, verified: Int): List<RemoteEndpoint>? =
        mutex.withLock {
            if (verified <= 0) return@withLock null
            val current = loadOrSeed()
            val target = current.firstOrNull { it.id == id } ?: return@withLock null
            if (target.verifiedContextLength == verified) return@withLock current
            val next = current.map {
                if (it.id == id) it.copy(verifiedContextLength = verified) else it
            }
            persist(next)
            next
        }

    /** 读文件；不存在则写入预置并返回预置（只发生一次）。 */
    private suspend fun loadOrSeed(): List<RemoteEndpoint> {
        val stored = files.read(FILE_NAME, ListSerializer(RemoteEndpoint.serializer()))
        if (stored != null) return stored
        persist(seeds)
        return seeds
    }

    private suspend fun persist(endpoints: List<RemoteEndpoint>) {
        files.write(FILE_NAME, ListSerializer(RemoteEndpoint.serializer()), endpoints)
    }

    companion object {
        const val FILE_NAME: String = "endpoints.json"

        /** 预置 id 是稳定契约（密钥文件名 / 活跃端点引用），不要改成随机值。 */
        const val SEED_OPENAI: String = "seed-openai"
        const val SEED_DEEPSEEK: String = "seed-deepseek"
        const val SEED_OLLAMA: String = "seed-ollama"

        /**
         * 三组预置。`allowPrivateNetwork` 只对 Ollama 打开 ——
         * 它是本机 / 局域网地址（模拟器访问宿主机是 `10.0.2.2`），
         * 其余两家是公网 https，保持 fail-closed。
         */
        fun defaultSeeds(): List<RemoteEndpoint> = listOf(
            RemoteEndpoint(
                id = SEED_OPENAI,
                name = "OpenAI",
                kind = ProviderKind.OPENAI_COMPATIBLE,
                preset = UiPreset.OPENAI,
                baseUrl = "https://api.openai.com/v1",
                modelId = "gpt-4o-mini",
                contextLength = 128_000,
                maxOutputTokens = 16_384,
                supportsTools = true,
                supportsReasoning = false,
            ),
            RemoteEndpoint(
                id = SEED_DEEPSEEK,
                name = "DeepSeek",
                kind = ProviderKind.OPENAI_COMPATIBLE,
                preset = UiPreset.DEEPSEEK,
                baseUrl = "https://api.deepseek.com/v1",
                modelId = "deepseek-chat",
                contextLength = 65_536,
                maxOutputTokens = 8_192,
                supportsTools = true,
                supportsReasoning = false,
            ),
            RemoteEndpoint(
                id = SEED_OLLAMA,
                name = "Ollama (local)",
                kind = ProviderKind.OPENAI_COMPATIBLE,
                preset = UiPreset.OLLAMA_LOCAL,
                baseUrl = "http://10.0.2.2:11434/v1",
                modelId = "llama3.1",
                contextLength = 32_768,
                maxOutputTokens = 4_096,
                supportsTools = false,
                supportsReasoning = false,
                allowPrivateNetwork = true,
            ),
        )
    }
}
