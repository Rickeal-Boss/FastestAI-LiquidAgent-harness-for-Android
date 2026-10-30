package com.rickeal.liquidharness.core.data

import android.content.Context
import com.rickeal.liquidharness.core.agent.AgentRunner
import com.rickeal.liquidharness.core.engine.RemoteLlmEngine
import com.rickeal.liquidharness.core.engine.SecretProvider
import com.rickeal.liquidharness.core.engine.provider.OpenAiCompatibleAdapter
import com.rickeal.liquidharness.core.model.ProviderKind
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.net.HttpTransport
import com.rickeal.liquidharness.core.net.UnsafeUrlException
import com.rickeal.liquidharness.core.net.UrlGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 手写 DI 的唯一装配点（禁 Hilt / Koin）。
 *
 * ## 为什么这个类必须保持短
 *
 * 旧仓 `AppContainer` 实测 861 行，最后变成「什么都装」的上帝对象 —— 加一个能力就要
 * 动它一次，而它被所有 UI 依赖，等于每次改动都全量重编译 + 全量回归。
 * 本类只做三件事：**持有**（repository / store 单例）、**装配**（引擎与内核）、
 * **生命周期**（`applicationContext` 一个引用）。任何业务判断都不许进来。
 *
 * ## 为什么引擎按端点造、而 runner 必须按 endpointId 缓存
 *
 * 端点可编辑（换模型、换 baseUrl）。把 `RemoteLlmEngine` 做成单例就得在
 * `generate()` 里反复读配置，而 `stop()` 需要定位「当前在途流」——
 * 换端点后旧实例的 `activeStream` 仍然有效，会造成「点了停止却停不掉」。
 * 所以引擎保持「按端点造」。
 *
 * **但 runner 不能每次 new**：`AgentRunner.runMutex` 是**实例字段**，每次调用都造
 * 一个新实例等于每次发一把新锁 —— 连发两次就是两条并发 run，互斥形同不存在。
 * 旧实现正是每次 new（而单测复用同一个实例，所以一直绿：典型的「判据空转」）。
 * 现在按 endpointId 缓存，配置变更时由 [saveEndpoint] / [deleteEndpoint] 显式失效，
 * 否则旧 runner 的引擎仍指向旧 baseUrl / modelId。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** 全部私有数据集中在 `filesDir/harness/` 一层，便于导出与清理。 */
    private val dataDir: File = File(appContext.filesDir, "harness")

    val jsonFiles: JsonFileStore = JsonFileStore(dataDir)

    val endpoints: EndpointStore = EndpointStore(jsonFiles)

    val secrets: EndpointSecretStore = EndpointSecretStore(
        dir = File(dataDir, "secrets"),
        cipher = AndroidKeyStoreCipher(),
    )

    val settings: SettingsRepository = SettingsRepository.create(appContext)

    val logs: AgentLogFileStore = AgentLogFileStore(File(dataDir, "logs"))

    private val transport: HttpTransport = HttpTransport()

    /** 引擎侧在发请求那一刻现取密钥（`00-brief` A7），故这里只是端口的薄封装。 */
    private val secretProvider: SecretProvider = secrets.asSecretProvider()

    val probe: EndpointProbe = EndpointProbe(transport)

    /**
     * 按端点造引擎。W1 只有 OpenAI 兼容族 —— 另外两族的 adapter 是 W2，
     * 现在放行会得到一个「能建端点但发不出请求」的假象，故 fail-fast。
     */
    fun engineFor(endpoint: RemoteEndpoint): RemoteLlmEngine {
        require(endpoint.kind == ProviderKind.OPENAI_COMPATIBLE) {
            "provider not supported in this build: " + endpoint.kind.name
        }
        return RemoteLlmEngine(
            endpoint = endpoint,
            adapter = OpenAiCompatibleAdapter(),
            transport = transport,
            secrets = secretProvider,
        )
    }

    /**
     * 内核门面：**按 endpointId 缓存**。
     *
     * 缓存是互斥语义的前提，不是性能优化：`runMutex` 在实例里，
     * 每次 new 就等于每次换一把锁（见类头的事故说明）。
     * `getOrPut` 在 `ConcurrentMap` 上是 putIfAbsent 语义，并发首建也只会有一个赢家。
     */
    private val runners = ConcurrentHashMap<String, AgentRunner>()

    fun agentRunnerFor(endpoint: RemoteEndpoint): AgentRunner =
        runners.getOrPut(endpoint.id) { AgentRunner(RemoteEngineBridge(engineFor(endpoint))) }

    suspend fun activeEndpoint(): RemoteEndpoint? {
        val id = settings.activeEndpointId.first() ?: return null
        return endpoints.get(id)
    }

    /**
     * 保存端点 + 密钥，并保证「首次保存的端点自动成为活跃端点」。
     *
     * 顺序是有讲究的：**先判 URL → 再落密钥 → 最后写清单**。
     * - 先判 URL：不安全的目标不许进清单（否则它会成为「已保存但一发请求就被拦」的僵尸端点）；
     * - 密钥写失败必须抛错：Keystore 异常时静默返回会让用户以为 key 存上了，
     *   之后每次请求都 401，排查方向完全错（`04-risk-register.md` R11）；
     * - 清单最后写：`hasSecret` 要按**写完之后**的真实状态回写，否则列表页的
     *   「已保存密钥」标记永远是 false（旧实现的缺陷）。
     */
    suspend fun saveEndpoint(endpoint: RemoteEndpoint, apiKey: String?): List<RemoteEndpoint> {
        val safeUrl = try {
            UrlGuard.requireAllowed(endpoint.baseUrl, endpoint.allowPrivateNetwork)
        } catch (e: UnsafeUrlException) {
            // 只把原因码带出去，不把 core-net 的类型泄漏到 UI 层。
            throw EndpointSaveException(EndpointSaveError.UNSAFE_URL, e.message ?: e.reason.name)
        }
        val normalized = endpoint.copy(baseUrl = safeUrl)

        val hasSecret = withContext(Dispatchers.IO) {
            if (apiKey != null && !secrets.write(normalized.id, apiKey)) {
                throw EndpointSaveException(
                    EndpointSaveError.SECRET_WRITE_FAILED,
                    "failed to persist api key",
                )
            }
            secrets.has(normalized.id)
        }

        val saved = endpoints.upsert(normalized.copy(hasSecret = hasSecret))
        // 配置变了 ⇒ 旧 runner 的引擎还指着旧 baseUrl / modelId，必须失效重建。
        // 放在 upsert 之后：保存失败时不该丢掉仍与磁盘一致的那个实例。
        runners.remove(normalized.id)
        if (settings.activeEndpointId.first() == null) {
            settings.setActiveEndpointId(normalized.id)
        }
        return saved
    }

    /** 删端点必须**同时删密钥**：否则 Keystore 里留下一份无主密钥（`01-architecture` §十一）。 */
    suspend fun deleteEndpoint(id: String): List<RemoteEndpoint> {
        val remaining = endpoints.remove(id)
        withContext(Dispatchers.IO) { secrets.remove(id) }
        runners.remove(id)
        if (settings.activeEndpointId.first() == id) {
            settings.setActiveEndpointId(remaining.firstOrNull()?.id)
        }
        return remaining
    }
}
