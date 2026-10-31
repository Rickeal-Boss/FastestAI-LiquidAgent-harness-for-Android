package com.rickeal.liquidharness.core.data

import android.content.ClipboardManager
import android.content.Context
import com.rickeal.liquidharness.core.agent.AgentRunner
import com.rickeal.liquidharness.core.agent.policy.AgentPolicy
import com.rickeal.liquidharness.core.agent.tool.ToolContext
import com.rickeal.liquidharness.core.agent.tool.ToolRegistry
import com.rickeal.liquidharness.core.agent.tool.registerBuiltins
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

    /** 附件导入（W3 多模态）。解码在 IO 线程内，构造期零工作。 */
    val attachments: AttachmentImporter = AttachmentImporter(appContext)

    /**
     * 长期记忆。文件版（`memory.json`），与端点清单同一个 `dataDir`。
     *
     * 记忆正文在**每个 run 开始时快照一次**进 system（见 `ChatViewModel.runOnce`），
     * run 内不变 —— 这是 prompt cache 前缀稳定的前提。
     */
    val memory: com.rickeal.liquidharness.core.agent.memory.MemoryStore =
        FileMemoryStore(jsonFiles)

    /**
     * 技能目录。来源顺序 = 优先级：**用户目录在前**，内置 assets 在后。
     *
     * 先到先得意味着用户导入的同名技能会覆盖内置版（用户意图优先），
     * 而不是「内置受保护」—— 后者会让用户永远改不掉一个不合适的技能。
     */
    val skills: com.rickeal.liquidharness.core.agent.skill.SkillCatalog by lazy {
        val userDir = File(dataDir, "skills").apply { mkdirs() }
        com.rickeal.liquidharness.core.skills.SkillCatalogImpl(
            listOf(
                com.rickeal.liquidharness.core.skills.FileSkillSource(userDir),
                com.rickeal.liquidharness.core.skills.AssetSkillSource(appContext.assets),
            )
        )
    }

    /** MCP server 配置（不含明文 token）。 */
    val mcpServers: McpServerStore = McpServerStore(jsonFiles)

    /** MCP 工具装配器（传输层复用 `:core-net` 的连接池与代理配置）。 */
    val mcpTools: McpToolSources by lazy { McpToolSources(NetMcpTransport(transport)) }

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
     * 内置工具面（6 个）。**惰性**装配，理由见下方 `getSystemService` 那段。
     *
     * 沙箱根 = `filesDir/harness/workspace`（与 [dataDir] 同源，便于整目录导出 / 清理）。
     *
     * ## 🔴 `getSystemService()` 绝不能出现在字段初始化期
     *
     * 旧仓真实事故（W78 P0）：Service 字段在初始化期调 `getSystemService()` 抛 NPE，
     * 直接把进程打死 —— 因为本类在 `Application.onCreate` 里构造，构造期任何异常都是
     * 启动即崩，而三层闸门全绿（编译过、单测过、lint 过，没有一条判据在 Application
     * 构造期跑过）。所以这里用 `by lazy`：`getSystemService` 只在「第一次真的要跑
     * run」时求值，构造期不碰任何系统服务。
     *
     * 同理沙箱目录的 `mkdirs()` 也放在 lazy 里；它失败不抛（返回值忽略）——
     * 工具自己会在读写时给出明确错误，比在 DI 装配期崩掉可诊断得多。
     */
    private val tools: ToolRegistry by lazy {
        val workspace = File(dataDir, "workspace")
        workspace.mkdirs()
        val toolContext = ToolContext(
            sandboxDir = workspace,
            appContext = appContext,
            clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager,
        )
        // 注册结果在此处无处分支：内置工具装进空注册表，`register` 唯一可失败的路径是
        // 「跨来源同名覆盖」，而那条只存在于 `registerContributed`（内置路径恒为 Ok）。
        // 显式丢弃而不是包一层假处理 —— 让返回值在这里被真正读过一遍再放掉。
        ToolRegistry().also { registry ->
            registerBuiltins(registry, toolContext)
            // W3 新增能力面：记忆三件套 / 技能两件套 / 语音一个。
            // 全部走 `register`（内置来源），因为它们随 APK 编译进来、不经远端。
            com.rickeal.liquidharness.core.agent.tool.tools.memoryTools(memory)
                .forEach { registry.register(it) }
            com.rickeal.liquidharness.core.agent.tool.tools.skillTools(skills)
                .forEach { registry.register(it) }
            // ⚠️ `speak` **默认不注册**（W3 真机实测后关闭）。
            //
            // OPPO PDRM00 的默认 TTS 引擎是 `com.iflytek.speechsuite`：调用 `speak()`
            // 会把工具协程挂住 40s 以上，且**点停止也无法中断**（阻塞调用不在挂起点上）。
            // 一个会挂死 run 的工具比没有这个工具更糟 —— 模型会卡在那里，用户只能杀进程。
            //
            // 保留实现（`speakTools`）与单测：换一台 TTS 正常的设备 / 用户装了 Google TTS
            // 之后，把这一行取消注释即可启用。这是**能力缺口**，不是「已支持」。
            // com.rickeal.liquidharness.core.agent.tool.tools.speakTools(toolContext)
            //     .forEach { registry.register(it) }
        }
    }

    /**
     * 一次 run 的提示词上下文（记忆正文 + 技能一级披露）。
     *
     * ## 为什么是 `suspend` 且在 run 开始前调一次
     *
     * 两者都要读盘（记忆文件 / 技能目录）。放在 `AgentRequest` 构造处读，
     * 保证「一个 run 内 system 字节稳定」（`00-brief.md` A8）——
     * 若每轮重读，技能列表顺序或记忆更新时间一变，整段前缀的 prompt cache 全价重算。
     *
     * ## 失败降级
     *
     * 记忆读失败 ⇒ 空串（不带记忆，但 run 照常）；技能扫失败 ⇒ 空段。
     * 两个都不该让「发消息」这件事失败 —— 它们是增强，不是前置条件。
     */
    suspend fun promptContext(): PromptContext {
        val memoryText = runCatching {
            com.rickeal.liquidharness.core.agent.memory.renderMemoryForPrompt(memory.all())
        }.getOrDefault("")
        // 停用的技能既不进一级披露，也不能被 skill_load 加载（工具层查同一个目录，
        // 所以这里只过滤披露是不够的 —— 见 SkillTools 的 enabled 过滤）。
        val disabled = runCatching { settings.disabledSkills.first() }.getOrDefault(emptySet())
        val skillsSection = runCatching {
            com.rickeal.liquidharness.core.agent.skill.renderSkillsSection(
                skills.list().filterNot { it.name in disabled }
            )
        }.getOrDefault("")
        return PromptContext(memoryText = memoryText, skillsSection = skillsSection)
    }

    /** 提示词上下文快照。字段直接对应 `AgentRequest` 的两个入参。 */
    data class PromptContext(
        val memoryText: String = "",
        val skillsSection: String = "",
    )

    /**
     * 工具注册表的**只读暴露**，供 MCP 同步与设置页诊断使用。
     *
     * 故意不叫 `tools`：内部那个 lazy 字段是装配细节，外部拿到的是同一个实例。
     * 首次求值会做 `mkdirs()` + `getSystemService()`（阻塞），所以调用方必须在 IO 线程
     * （`ChatViewModel.init` 的 `viewModelScope` 是 Main —— 那里已经包了 `runCatching`
     * 但**没有**切线程；W3 的 MCP 同步发生在 `viewModelScope.launch` 里，
     * 首次触碰会落在主线程。这是已知的冷启动抖动点，登记在 `docs/notes/w3-provider-matrix.md`）。
     */
    fun toolRegistry(): ToolRegistry = tools

    /**
     * 内核门面：**按 endpointId 缓存**。
     *
     * 缓存是互斥语义的前提，不是性能优化：`runMutex` 在实例里，
     * 每次 new 就等于每次换一把锁（见类头的事故说明）。
     * `getOrPut` 在 `ConcurrentMap` 上是 putIfAbsent 语义，并发首建也只会有一个赢家。
     *
     * ## 端点不支持工具时给**空注册表**，不是 null
     *
     * 模型看到的工具清单直接来自注册表：`supportsTools=false` 的端点若仍拿到那 6 个工具，
     * 模型会去调一个协议层根本发不出去的调用（adapter 会整段丢掉 tool 字段），
     * 用户看到的是「模型反复说要用工具但什么都没发生」。空注册表 = 工具面不存在。
     *
     * ## 为什么是 `suspend`（W2 外审 §四.1）
     *
     * 首次求值 [tools] 会做 `workspace.mkdirs()` + `appContext.getSystemService()`，
     * 两者都是**阻塞**调用（磁盘 IO / 系统服务握手）。旧签名是普通函数，而调用点是
     * `viewModelScope`（`Main.immediate`）⇒ 首帧主线程上做磁盘 IO，冷启动有掉帧风险。
     * 现在整段包进 `withContext(Dispatchers.IO)`：`getOrPut` 的 lambda 里构造
     * [AgentRunner] 也会读 `tools`，所以它必须一起在 IO 段内。
     *
     * 构造期仍然**不碰**任何系统服务（`tools` 是 `by lazy`，W78 纪律不变）——
     * 变的只是「第一次真的要跑 run 时落在哪个线程」。
     */
    private val runners = ConcurrentHashMap<String, AgentRunner>()

    suspend fun agentRunnerFor(endpoint: RemoteEndpoint): AgentRunner = withContext(Dispatchers.IO) {
        // 窗口上限在**建 runner 时**读一次：改设置对已缓存的 runner 不生效，
        // 换端点或重启后生效。不做实时监听是刻意的 —— 一次 run 中途改预算会让
        // 「这一轮压不压缩」在轮与轮之间跳变，那是最难排查的一类不一致。
        val windowCap = settings.maxContextTokens.first()
        runners.getOrPut(endpoint.id) {
            AgentRunner(
                engine = RemoteEngineBridge(engineFor(endpoint)),
                tools = if (endpoint.supportsTools) tools else ToolRegistry(),
                policy = AgentPolicy(maxContextTokens = windowCap),
            )
        }
    }

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
     * - 写成功但**解不开**（`hasUsable=false`）同样抛错，且**不回滚密文**：
     *   删掉会丢失「为什么解不开」的现场（Keystore 被清 / 密文被截断 / 换机恢复），
     *   而下次保存会原地覆盖。保留一份不可用密文的风险，低于失去唯一的诊断线索。
     * - 清单最后写：`hasSecret` 要按**写完之后**的真实状态回写，否则列表页的
     *   「已保存密钥」标记永远是 false（旧实现的缺陷）。
     *
     * ## `hasSecret` 用 `hasUsable` 而不是 `hasFile`（W2 外审 §四.1）
     *
     * `hasFile` 只判文件存在：写盘成功但**用当前 Keystore 解不开**时它会返回 true，
     * 于是清单写 `hasSecret=true`、UI 显示「已配置」，而每次请求必然 401。
     * 这里改成写完立刻真解密一次：
     * - 解不开 ⇒ 抛 [EndpointSaveError.SECRET_WRITE_FAILED]（「写了但用不了」必须说出来，
     *   比静默 hasSecret=true 诚实）；
     * - 解得开 ⇒ `hasSecret=true`，与「发请求时真的能取到密钥」同源。
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
            val usable = secrets.hasUsable(normalized.id)
            // 写成功却解不开 = Keystore 刚换过密钥 / 密文被截断。此时回写 hasSecret=true
            // 会制造「显示已配置但请求 401」的假象（R11 的触发信号）。
            if (apiKey != null && !usable) {
                throw EndpointSaveException(
                    EndpointSaveError.SECRET_WRITE_FAILED,
                    "api key written but cannot be decrypted",
                )
            }
            usable
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
