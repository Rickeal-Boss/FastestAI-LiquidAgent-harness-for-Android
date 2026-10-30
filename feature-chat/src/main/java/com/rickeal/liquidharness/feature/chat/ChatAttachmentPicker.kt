package com.rickeal.liquidharness.feature.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 附件选择（图片 / 音频 / 文本）。
 *
 * ⚠️ **W6 接线，当前 main 源集里没有调用点**：本仓 `ChatMessage` 还没有 attachments
 * 字段、`ChatUiState` 也没有附件通道，`ChatInputBar` 因此不渲染「+」入口。
 * 保留整个文件是为了让 W6 只改「ViewModel 收下 uri → 读成文本 / 图片」这一段，
 * 不必再从旧仓搬一次 SAF 胶水。
 *
 * 用系统 `OpenDocument`（SAF）而不是自己写文件浏览器：
 *  - 零权限弹窗（READ_MEDIA_IMAGES 只在直接读 MediaStore 时才需要）；
 *  - 返回的 `content://` 用 `takePersistableUriPermission` 拿到长期读权限，
 *    会话落盘后重开 App 仍然可读；
 *  - 缩略图由 `:core-design` 的 `GlassImageThumb` 采样解码（不引 Coil）。
 */
@Composable
fun rememberImagePicker(onPicked: (uri: String, name: String) -> Unit): () -> Unit =
    rememberDocumentPicker(mimeTypes = arrayOf("image/*"), onPicked = onPicked)

@Composable
fun rememberAudioPicker(onPicked: (uri: String, name: String) -> Unit): () -> Unit =
    rememberDocumentPicker(mimeTypes = arrayOf("audio/*"), onPicked = onPicked)

/**
 * 文本 / Markdown 文件选择。
 *
 * MIME 只给 `text/plain` + `text/markdown`：**不加** `application/octet-stream`，
 * 那等于放开所有二进制文件，与「只收 txt / md」的需求相反。
 */
@Composable
fun rememberFilePicker(onPicked: (uri: String, name: String) -> Unit): () -> Unit =
    rememberDocumentPicker(
        mimeTypes = arrayOf("text/plain", "text/markdown"),
        onPicked = onPicked,
    )

@Composable
private fun rememberDocumentPicker(
    mimeTypes: Array<String>,
    onPicked: (uri: String, name: String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            grantPersistable(context, uri)
            onPicked(uri.toString(), displayName(context, uri))
        }
    }
    // mimeTypes 是数组：用内容拼 key，避免每次重组都重建 lambda 触发多余的 launch。
    return remember(launcher, mimeTypes.contentToString()) {
        { launcher.launch(mimeTypes) }
    }
}

private fun grantPersistable(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

/**
 * 取展示名。
 *
 * SAF 的 document URI（`content://…/document/1000010056`）拿 `lastPathSegment` 只会得到
 * `document:1000010056` —— 真机上附件 chip 就是一串数字。先问 provider 的
 * `DISPLAY_NAME`，查不到再回退路径末段。
 *
 * 回退值刻意是空串而不是某个中文占位：调用方（W6 的 chip）自己决定怎么兜底，
 * 本文件保持无文案（守卫 G16）。
 */
private fun displayName(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        val name = cursor.getString(index)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
    }
    return uri.lastPathSegment.orEmpty().substringAfterLast('/')
}
