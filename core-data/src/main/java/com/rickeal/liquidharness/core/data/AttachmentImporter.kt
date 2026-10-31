package com.rickeal.liquidharness.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.rickeal.liquidharness.core.model.Attachment
import com.rickeal.liquidharness.core.model.AttachmentKind
import com.rickeal.liquidharness.core.model.newId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 把 SAF 选中的 `content://` 导入成可发送的 [Attachment]。
 *
 * ## 为什么解码发生在导入时，而不是发送时
 *
 * 见 `Attachment` 的 KDoc：引擎在 `core-engine`，拿不到 `ContentResolver`。
 * 导入时一次性解码 + 压缩，之后消息就是自包含的。
 *
 * ## 三条必须做的处理
 *
 * 1. **降采样**：直接 `BitmapFactory.decodeStream` 一张 4000×3000 的照片在低端机上
 *    会 OOM（本仓目标设备是 OPPO A13 级）。用 `inJustDecodeBounds` 先量尺寸，
 *    按 [MAX_EDGE] 算 `inSampleSize`，再真解码。
 * 2. **重编码成 JPEG**：原图可能是 HEIC/PNG/WebP，部分端点只认 png/jpeg/webp；
 *    统一转 JPEG q=85 最稳，且体积可控。
 * 3. **超限拒绝**（fail-closed）：转完仍 > [Attachment.MAX_IMAGE_BYTES] ⇒ 继续降质量，
 *    仍超则返回失败原因，**不发半张图**（半张图比没有更糟：模型会基于残缺信息作答）。
 *
 * ## 不做的事
 *
 * 不读 EXIF（GPS / 设备型号是隐私面，且模型用不到）；不写磁盘（历史在内存里，
 * 落盘是会话持久化的职责，W3 不做）。
 */
class AttachmentImporter(private val appContext: Context) {

    /** 结果用 sealed：失败原因是调用方**能分支**的数据（UI 要显示具体原因）。 */
    sealed interface Result {
        data class Ok(val attachment: Attachment) : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun importImage(uri: String, displayName: String = ""): Result = withContext(Dispatchers.IO) {
        try {
            val parsed = Uri.parse(uri)
            val bytes = appContext.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
                ?: return@withContext Result.Failed("cannot_open_uri")
            if (bytes.isEmpty()) return@withContext Result.Failed("empty_file")

            val bounds = measure(bytes) ?: return@withContext Result.Failed("decode_failed")
            val sample = sampleSize(bounds.first, bounds.second)
            val bitmap = decode(bytes, sample) ?: return@withContext Result.Failed("decode_failed")
            try {
                val encoded = encodeJpeg(bitmap)
                if (encoded == null || encoded.size > Attachment.MAX_IMAGE_BYTES) {
                    return@withContext Result.Failed("image_too_large")
                }
                Result.Ok(
                    Attachment(
                        id = newId(),
                        kind = AttachmentKind.IMAGE,
                        mimeType = "image/jpeg",
                        name = displayName.ifBlank { "image.jpg" },
                        base64 = Base64.encodeToString(encoded, Base64.NO_WRAP),
                        byteSize = encoded.size,
                        width = bitmap.width,
                        height = bitmap.height,
                        localPath = cacheCopy(encoded),
                    )
                )
            } finally {
                bitmap.recycle()
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.Failed(t.message ?: "import_failed")
        }
    }

    /** 文本附件：读成 UTF-8，超长截断（带标记）。 */
    suspend fun importText(uri: String, displayName: String = ""): Result = withContext(Dispatchers.IO) {
        try {
            val parsed = Uri.parse(uri)
            val text = appContext.contentResolver.openInputStream(parsed)?.use { input ->
                readUtf8Capped(input, Attachment.MAX_TEXT_CHARS)
            } ?: return@withContext Result.Failed("cannot_open_uri")
            if (text.isBlank()) return@withContext Result.Failed("empty_file")
            Result.Ok(
                Attachment(
                    id = newId(),
                    kind = AttachmentKind.TEXT,
                    mimeType = "text/plain",
                    name = displayName.ifBlank { "attachment.txt" },
                    byteSize = text.length,
                    textContent = text,
                )
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.Failed(t.message ?: "import_failed")
        }
    }

    /**
     * 把重编码后的字节落到缓存目录，返回绝对路径供 UI 缩略图使用。
     *
     * 为什么必须落盘：`GlassImageThumb` 走 `BitmapFactory` 读路径/uri，不认 base64。
     * 落在 `cacheDir` 而不是 `filesDir`：这些文件是可重建的派生数据，
     * 系统清缓存时一起消失是正确行为（历史消息里的缩略图会退化成占位图，不影响发送，
     * 因为请求体用的是内存里的 base64）。
     *
     * 写失败返回空串：缩略图缺失是**降级**，不该让整个导入失败。
     */
    private fun cacheCopy(bytes: ByteArray): String = try {
        val dir = java.io.File(appContext.cacheDir, "attachments")
        if (!dir.exists()) dir.mkdirs()
        val file = java.io.File(dir, "img-" + System.nanoTime() + ".jpg")
        file.writeBytes(bytes)
        file.absolutePath
    } catch (t: Throwable) {
        ""
    }

    private fun measure(bytes: ByteArray): Pair<Int, Int>? {        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return options.outWidth to options.outHeight
    }

    /** 2 的幂降采样，直到最长边 ≤ [MAX_EDGE]。 */
    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / sample > MAX_EDGE) sample *= 2
        return sample
    }

    private fun decode(bytes: ByteArray, sample: Int): Bitmap? = try {
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (t: Throwable) {
        null
    }

    private fun encodeJpeg(bitmap: Bitmap): ByteArray? = try {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    } catch (t: Throwable) {
        null
    }

    private fun readUtf8Capped(input: InputStream, cap: Int): String {
        val buffer = ByteArray(8 * 1024)
        val out = StringBuilder()
        while (out.length < cap) {
            val read = input.read(buffer)
            if (read <= 0) break
            out.append(String(buffer, 0, read, Charsets.UTF_8))
        }
        if (out.length >= cap) out.append("\n[truncated]")
        return out.toString()
    }

    private companion object {
        /** 最长边上限。1568 是主流视觉编码器的有效分辨率上限，再大只是白烧 token。 */
        const val MAX_EDGE = 1_568
        const val JPEG_QUALITY = 85
    }
}
