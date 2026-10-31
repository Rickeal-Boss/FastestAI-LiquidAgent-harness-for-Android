package com.rickeal.liquidharness.core.data

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [EndpointSecretStore] 的 JVM 单测。
 *
 * 用假 cipher（XOR + 前置 IV）而不是 [AndroidKeyStoreCipher]：真 Keystore 需要
 * Android 守护进程，在 JVM 单测里只会得到 `KeyStoreException`。
 * 这里要验的是**存储层的契约**（往返 / 路径逃逸 / 失败返回 false /
 * `hasFile` 与 `hasUsable` 的语义差），不是加密强度。
 *
 * ## 为什么 `hasFile` 与 `hasUsable` 必须各测一条
 *
 * 两者在**正常机型**上永远同真同假 —— 只测正常路径等于没测出差。差异只在
 * 「密文在、密钥不在」（换机 / 恢复备份 / 用户清除凭据）时出现：
 * `hasFile=true` + `hasUsable=false`。这条组合是 R11 的判据，也是
 * `AppContainer.saveEndpoint` 抛 `SECRET_WRITE_FAILED` 的触发条件。
 */
class EndpointSecretStoreTest {

    /**
     * IV 长度与真实现保持一致（12 字节），让「前置 IV」的偏移逻辑同构。
     *
     * [failOnDecrypt] 模拟**换机 / 恢复备份**后 `AndroidKeyStoreCipher` 的必然结局：
     * 文件还在（备份通道把密文搬过去了），但新机的 Keystore 里没有那把密钥，
     * `doFinal` 抛 `AEADBadTagException`。这是 `hasUsable` 存在的唯一理由。
     */
    private class FakeCipher(
        private val failOnEncrypt: Boolean = false,
        private val failOnDecrypt: Boolean = false,
    ) : SecretCipher {

        override fun encrypt(plaintext: String): ByteArray {
            if (failOnEncrypt) throw IllegalStateException("keystore unavailable")
            val body = plaintext.toByteArray(Charsets.UTF_8)
            val masked = ByteArray(body.size) { i -> (body[i].toInt() xor 0x5A).toByte() }
            return ByteArray(IV_LENGTH_BYTES) + masked
        }

        override fun decrypt(payload: ByteArray): String {
            if (failOnDecrypt) throw IllegalStateException("AEADBadTagException: key not found in this keystore")
            require(payload.size > IV_LENGTH_BYTES) { "payload too short" }
            val body = ByteArray(payload.size - IV_LENGTH_BYTES) { i ->
                (payload[i + IV_LENGTH_BYTES].toInt() xor 0x5A).toByte()
            }
            return String(body, Charsets.UTF_8)
        }

        companion object {
            const val IV_LENGTH_BYTES = 12
        }
    }

    private fun newDir(): File = Files.createTempDirectory("endpoint-secrets").toFile()

    /** 往返：写入 → 读回明文 → hasFile/hasUsable → 删除后读不到。 */
    @Test
    fun roundTripAndRemove() {
        val dir = newDir()
        try {
            val store = EndpointSecretStore(dir, FakeCipher())
            assertTrue(store.write("seed-openai", "sk-test-123"))
            assertEquals("sk-test-123", store.read("seed-openai"))
            assertTrue(store.hasFile("seed-openai"))

            assertTrue(store.remove("seed-openai"))
            assertNull(store.read("seed-openai"))
            assertFalse(store.hasFile("seed-openai"))
            assertFalse(store.hasUsable("seed-openai"))
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * 正常路径：密文可解 ⇒ `hasFile` 与 `hasUsable` **都为 true**。
     *
     * 这条看着平凡，但它钉住了 `hasUsable` 不会把「能解开的正常密钥」误判成不可用 ——
     * 否则 `saveEndpoint` 会对每一次正常保存都抛 `SECRET_WRITE_FAILED`。
     */
    @Test
    fun usableSecretReportsBothFlagsTrue() {
        val dir = newDir()
        try {
            val store = EndpointSecretStore(dir, FakeCipher())
            assertTrue(store.write("ep-usable", "sk-live"))
            assertTrue(store.hasFile("ep-usable"))
            assertTrue(store.hasUsable("ep-usable"))
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * 🔴 换机路径：**密文在、密钥不在** ⇒ `hasFile=true` 但 `hasUsable=false`。
     *
     * 用「解密必失败」的 cipher 模拟新机 Keystore 里没有旧密钥（GCM 下就是
     * `AEADBadTagException`）。这条断言就是外审 §四.1「`has()` 说谎」的判据：
     * 只要 `hasUsable` 退化成「只判文件存在」，本用例立刻红。
     */
    @Test
    fun undecryptableCiphertextIsFileButNotUsable() {
        val dir = newDir()
        try {
            val store = EndpointSecretStore(dir, FakeCipher(failOnDecrypt = true))
            assertTrue(store.write("ep-migrated", "sk-old"))
            assertTrue(store.hasFile("ep-migrated"))
            assertFalse(store.hasUsable("ep-migrated"))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 空白密钥等价于删除（避免留一份解不开的空密文）。 */
    @Test
    fun blankSecretDeletesInsteadOfWriting() {
        val dir = newDir()
        try {
            val store = EndpointSecretStore(dir, FakeCipher())
            assertTrue(store.write("ep-1", "sk-1"))
            assertTrue(store.write("ep-1", "   "))
            assertFalse(store.hasFile("ep-1"))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 路径逃逸 id 必须被拒（`../` 会写到 secrets 目录之外）。 */
    @Test
    fun pathTraversalIdIsRejected() {
        val dir = newDir()
        try {
            val store = EndpointSecretStore(dir, FakeCipher())
            try {
                store.write("../escaped", "sk-1")
                fail("expected IllegalArgumentException for path traversal id")
            } catch (e: IllegalArgumentException) {
                assertEquals(true, e.message?.contains("invalid endpoint id") == true)
            }
            assertNull(store.read("../escaped"))
            assertFalse(store.hasFile("../escaped"))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 绝对路径 / 带分隔符的 id 同样被拒（`File(dir, id)` 会直接忽略 dir）。 */
    @Test
    fun absolutePathIdIsRejected() {
        val dir = newDir()
        try {
            val store = EndpointSecretStore(dir, FakeCipher())
            for (id in listOf("/tmp/evil", "C:\\evil", "a/b", "..", "")) {
                try {
                    store.write(id, "sk-1")
                    fail("expected IllegalArgumentException for id=$id")
                } catch (e: IllegalArgumentException) {
                    assertTrue(e.message?.contains("invalid endpoint id") == true)
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 加密/落盘失败必须返回 false（调用方据此抛错，不许静默）。 */
    @Test
    fun writeFailureReturnsFalse() {
        val dir = newDir()
        try {
            val store = EndpointSecretStore(dir, FakeCipher(failOnEncrypt = true))
            assertFalse(store.write("ep-1", "sk-1"))
            assertFalse(store.hasFile("ep-1"))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 密文损坏（换机 / 密钥被清）读回 null，不抛异常。 */
    @Test
    fun corruptPayloadReadsAsNull() {
        val dir = newDir()
        try {
            val store = EndpointSecretStore(dir, FakeCipher())
            assertTrue(store.write("ep-1", "sk-1"))
            File(dir, "ep-1.bin").writeBytes(byteArrayOf(1, 2, 3))
            assertNull(store.read("ep-1"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
