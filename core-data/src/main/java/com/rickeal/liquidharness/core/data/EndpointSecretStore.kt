package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.engine.SecretProvider
import java.io.File

/**
 * 每个端点一个密钥文件：`<dir>/<endpointId>.bin`，内容 = `SecretCipher` 产出的
 * `iv || ciphertext`。**清单 JSON 里永远没有明文**（`RemoteEndpoint.hasSecret` 只是布尔）。
 *
 * ## 为什么这些方法是阻塞的（不是 suspend）
 *
 * 唯一的读取方是 `SecretProvider.secretFor()` —— 它是**非 suspend** 的接口，
 * 且由引擎在 `Dispatchers.IO` 上调用（发请求那一刻现取）。文件只有几百字节，
 * 同步读一次比在接口里塞 `runBlocking` 干净得多。
 * UI 侧的写入方自己包 `withContext(Dispatchers.IO)`。
 *
 * ## 路径安全
 *
 * endpointId 来自清单文件（可能被用户/同步工具改坏）。直接拼路径会有
 * `../../` 逃逸风险，因此先过 [ID_PATTERN]；非法 id 一律拒绝（读返回 null，写抛错）。
 */
class EndpointSecretStore(
    private val dir: File,
    private val cipher: SecretCipher,
) {

    private val lock = Any()

    /** @return true = 已落盘。空 / 空白密钥等价于删除（避免留一个解不开的空密文）。 */
    fun write(endpointId: String, secret: String?): Boolean = synchronized(lock) {
        require(ID_PATTERN.matches(endpointId)) { "invalid endpoint id" }
        if (secret.isNullOrBlank()) return@synchronized remove(endpointId)
        return@synchronized try {
            if (!dir.exists() && !dir.mkdirs()) return@synchronized false
            fileFor(endpointId).writeBytes(cipher.encrypt(secret))
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** 未配置 / 文件损坏 / 解密失败都返回 null（调用方按「没有密钥」处理）。 */
    fun read(endpointId: String): String? = synchronized(lock) {
        if (!ID_PATTERN.matches(endpointId)) return@synchronized null
        val file = fileFor(endpointId)
        if (!file.exists()) return@synchronized null
        try {
            cipher.decrypt(file.readBytes())
        } catch (t: Throwable) {
            // 密钥被系统清掉（换机 / 清除凭据 / 恢复备份）时旧密文必然解不开 ——
            // 这是**预期路径**，不是异常：用户重填一次即可。
            null
        }
    }

    /**
     * **文件在不在**。UI 用（列表页的「已保存密钥」标记），**不解密**。
     *
     * 为什么不直接给 UI 用 [hasUsable]：解密要过 AndroidKeyStore，冷启动首次取密钥
     * 可能几百毫秒（`04-risk-register.md` R11 的触发信号之一），放在列表渲染路径上
     * 会把首屏拖慢。UI 只需要「有没有东西」这个弱判据。
     *
     * ⚠️ 换机 / 恢复备份后它会是 `true` 而密文根本解不开（Keystore 密钥不随迁）。
     * 所以**不要**拿它回答「能不能用」—— 那个问题归 [hasUsable]。
     */
    fun hasFile(endpointId: String): Boolean = synchronized(lock) {
        ID_PATTERN.matches(endpointId) && fileFor(endpointId).exists()
    }

    /**
     * **真的能用**：文件存在**且**能用当前 Keystore 解开。
     *
     * 这是「已配置密钥」的诚实判据。旧实现只有一个 `has()`（只判文件存在），换机后
     * 返回 `true` 但发请求必然 401 —— 用户看到「显示已配置」却每次失败，排查方向完全错
     * （`04-risk-register.md` R11 的第 2 条缓解动作）。
     *
     * 🔴 **调用方必须在 IO 线程**：本函数会走一次 Keystore 解密（文件几百字节，
     * 但首次取密钥有系统调用开销）。`AppContainer.saveEndpoint` 已包
     * `withContext(Dispatchers.IO)`。
     */
    fun hasUsable(endpointId: String): Boolean = synchronized(lock) {
        if (!ID_PATTERN.matches(endpointId)) return@synchronized false
        val file = fileFor(endpointId)
        if (!file.exists()) return@synchronized false
        try {
            cipher.decrypt(file.readBytes())
            true
        } catch (t: Throwable) {
            // 与 read() 同一口径：解不开 = 没有密钥（预期路径，不是异常）。
            false
        }
    }

    fun remove(endpointId: String): Boolean = synchronized(lock) {
        if (!ID_PATTERN.matches(endpointId)) return@synchronized false
        val file = fileFor(endpointId)
        try {
            !file.exists() || file.delete()
        } catch (t: Throwable) {
            false
        }
    }

    /** 给 `core-engine` 用的端口实现。引擎在 IO 线程调用，故这里同步读是安全的。 */
    fun asSecretProvider(): SecretProvider = SecretProvider { endpointId -> read(endpointId) }

    private fun fileFor(endpointId: String): File = File(dir, "$endpointId.bin")

    companion object {
        /** 只允许清单里的 id 形态（`newId()` 产出 32 位十六进制，预置 id 带连字符）。 */
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
