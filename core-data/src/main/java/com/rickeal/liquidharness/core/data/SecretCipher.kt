package com.rickeal.liquidharness.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 对称信封的抽象。**唯一实现 [AndroidKeyStoreCipher] 依赖 Android 框架**，
 * 所以单测用 fake 替身（`XorCipher` 之类）验证 [EndpointSecretStore] 的往返，
 * 不把 AndroidKeyStore 拖进 JVM 单测（它需要真实 keystore 守护进程）。
 */
interface SecretCipher {

    /** @return IV 前置的密文（`iv || ciphertext`）。同一明文每次结果都不同。 */
    fun encrypt(plaintext: String): ByteArray

    fun decrypt(payload: ByteArray): String
}

/**
 * AndroidKeyStore 信封：AES-256-GCM，密钥不出 TEE/StrongBox（取决于设备）。
 *
 * 参数逐条有理由：
 * - **GCM**：带认证标签，密文被截断/篡改时 `doFinal` 抛 `AEADBadTagException`，
 *   而不是像 CBC 那样静默解出垃圾明文（垃圾明文会被当成 API Key 发出去）；
 * - **`setRandomizedEncryptionRequired(true)`**：强制每次加密由系统生成新 IV。
 *   关掉它就必须自己管 IV，而「复用 IV」在 GCM 下是灾难性的（可恢复明文）；
 * - **IV 前置**：GCM 的 IV 不需要保密，但解密方必须拿到同一个值。放密文头部
 *   比另开一个文件简单，且天然与密文同生命周期（删密文即删 IV）。
 *
 * 密钥别名固定：改名等于让所有老用户的密钥失联（旧密文解不开、又不会报错到用户面上，
 * 只表现为「API Key 无效」）。真要轮换必须写迁移代码。
 */
class AndroidKeyStoreCipher(
    private val alias: String = DEFAULT_ALIAS,
) : SecretCipher {

    override fun encrypt(plaintext: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // ENCRYPT_MODE 且不传 IV ⇒ 由 KeyStore 生成随机 IV（randomizedEncryptionRequired）
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return cipher.iv + ciphertext
    }

    override fun decrypt(payload: ByteArray): String {
        require(payload.size > IV_LENGTH_BYTES) { "secret payload too short" }
        val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /** 取已有密钥；没有则生成。`KeyStore` 是进程级单例，取一次很便宜。 */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS: String = "liquidharness.endpoint.secrets"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val TAG_LENGTH_BITS = 128

        /** GCM 推荐 96 bit IV；KeyStore 在 API 31 上生成的就是 12 字节。 */
        private const val IV_LENGTH_BYTES = 12
    }
}
