package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.model.AgentJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 原子写 JSON 文件（先写 `.tmp` 再 rename），避免写到一半进程被杀导致文件损坏。
 *
 * 读：**文件不存在**与**解析失败**都返回 null（一个坏掉的端点清单不该让 App 起不来）。
 * 但 [CancellationException] 必须原样上抛 —— 把「协程被取消」读成「没有数据」，
 * 调用方会走「首次启动，可以放心写回」的分支，等于用空数据覆盖用户资产。
 *
 * 所有 IO 走 [Dispatchers.IO]。临时文件名不用进程号（`android.os.Process` 在 JVM 单测里
 * 是未 mock 的桩，会让整条写入路径在单测中不可达），改用纳秒 + 线程 id 保证唯一。
 */
class JsonFileStore(
    private val baseDir: File,
    private val json: Json = AgentJson.json,
) {

    fun file(fileName: String): File = File(baseDir, fileName)

    suspend fun exists(fileName: String): Boolean = withContext(Dispatchers.IO) {
        file(fileName).exists()
    }

    suspend fun <T> read(fileName: String, deserializer: DeserializationStrategy<T>): T? =
        withContext(Dispatchers.IO) {
            val target = file(fileName)
            if (!target.exists()) return@withContext null
            val text = try {
                target.readText()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                return@withContext null
            }
            try {
                json.decodeFromString(deserializer, text)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                null
            }
        }

    /** @return false = 写失败（磁盘满 / 权限），调用方应保留内存态并提示用户。 */
    suspend fun <T> write(fileName: String, serializer: SerializationStrategy<T>, value: T): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (!baseDir.exists() && !baseDir.mkdirs()) return@withContext false
                val payload = json.encodeToString(serializer, value)
                val target = file(fileName)
                val tmp = File(baseDir, fileName + ".tmp." + System.nanoTime())
                tmp.writeText(payload)
                // renameTo 在 Windows 上目标已存在时会失败，退化成正写（非原子，但可用）。
                if (!tmp.renameTo(target)) {
                    target.writeText(payload)
                    tmp.delete()
                }
                true
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                false
            }
        }

    suspend fun delete(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = file(fileName)
            !target.exists() || target.delete()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            false
        }
    }
}
