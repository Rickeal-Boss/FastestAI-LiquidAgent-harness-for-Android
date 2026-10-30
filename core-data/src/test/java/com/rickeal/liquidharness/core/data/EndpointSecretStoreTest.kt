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
 * 这里要验的是**存储层的契约**（往返 / 路径逃逸 / 失败返回 false），不是加密强度。
 */
class EndpointSecretStoreTest {

    /** IV 长度与真实现保持一致（12 字节），让「前置 IV」的偏移逻辑同构。 */
    private class FakeCipher(private val failOnEncrypt: Boolean = false) : SecretCipher {

        override fun encrypt(plaintext: String): ByteArray {
            if (failOnEncrypt) throw IllegalStateException("keystore unavailable")
            val body = plaintext.toByteArray(Charsets.UTF_8)
            val masked = ByteArray(body.size) { i -> (body[i].toInt() xor 0x5A).toByte() }
            return ByteArray(IV_LENGTH_BYTES) + masked
        }

        override fun decrypt(payload: ByteArray): String {
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

    /** 往返：写入 → 读回明文 → has → 删除后读不到。 */
    @Test
    fun roundTripAndRemove() {
        val dir = newDir()
        try {
            val store = EndpointSecretStore(dir, FakeCipher())
            assertTrue(store.write("seed-openai", "sk-test-123"))
            assertEquals("sk-test-123", store.read("seed-openai"))
            assertTrue(store.has("seed-openai"))

            assertTrue(store.remove("seed-openai"))
            assertNull(store.read("seed-openai"))
            assertFalse(store.has("seed-openai"))
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
            assertFalse(store.has("ep-1"))
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
            assertFalse(store.has("../escaped"))
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
            assertFalse(store.has("ep-1"))
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
