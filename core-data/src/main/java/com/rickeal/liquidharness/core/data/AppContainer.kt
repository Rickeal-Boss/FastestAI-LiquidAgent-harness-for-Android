package com.rickeal.liquidharness.core.data

import android.content.Context
import com.rickeal.liquidharness.core.agent.AgentRunner
import com.rickeal.liquidharness.core.engine.RemoteLlmEngine
import com.rickeal.liquidharness.core.engine.SecretProvider
import com.rickeal.liquidharness.core.engine.provider.OpenAiCompatibleAdapter
import com.rickeal.liquidharness.core.model.ProviderKind
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.net.HttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

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
 * ## 为什么引擎是「按端点工厂」而不是单例
 *
 * 端点可编辑（换模型、换 baseUrl）。把 `RemoteLlmEngine` 做成单例就得在
 * `generate()` 里反复读配置，而 `stop()` 需要定位「当前在途流」——
 * 换端点后旧实例的 `activeStream` 仍然有效，会造成「点了停止却停不掉」。
 * 工厂语义明确：一次 run 一个引擎，run 结束即弃。
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

    /** 内核门面：一次 run 一个实例（内含 run 互斥锁，跨 run 复用没有意义）。 */
    fun agentRunnerFor(endpoint: RemoteEndpoint): AgentRunner =
        AgentRunner(RemoteEngineBridge(engineFor(endpoint)))

    suspend fun activeEndpoint(): RemoteEndpoint? {
        val id = settings.activeEndpointId.first() ?: return null
        return endpoints.get(id)
    }

    /** 保存端点 + 密钥，并保证「首次保存的端点自动成为活跃端点」。 */
    suspend fun saveEndpoint(endpoint: RemoteEndpoint, apiKey: String?): List<RemoteEndpoint> {
        val saved = endpoints.upsert(endpoint)
        if (apiKey != null) {
            withContext(Dispatchers.IO) { secrets.write(endpoint.id, apiKey) }
        }
        if (settings.activeEndpointId.first() == null) {
            settings.setActiveEndpointId(endpoint.id)
        }
        return saved
    }

    /** 删端点必须**同时删密钥**：否则 Keystore 里留下一份无主密钥（`01-architecture` §十一）。 */
    suspend fun deleteEndpoint(id: String): List<RemoteEndpoint> {
        val remaining = endpoints.remove(id)
        withContext(Dispatchers.IO) { secrets.remove(id) }
        if (settings.activeEndpointId.first() == id) {
            settings.setActiveEndpointId(remaining.firstOrNull()?.id)
        }
        return remaining
    }
}
