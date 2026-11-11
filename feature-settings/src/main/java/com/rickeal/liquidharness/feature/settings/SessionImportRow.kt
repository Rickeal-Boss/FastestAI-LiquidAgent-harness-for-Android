package com.rickeal.liquidharness.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rickeal.liquidharness.core.data.SessionImportParser
import com.rickeal.liquidharness.core.design.GlassSettingRow
import com.rickeal.liquidharness.core.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 设置页「导入会话（调试）」行 —— 会话导入通道的**唯一入口**。
 *
 * ## 它解锁什么（这才是它值 P1 的全部理由）
 *
 * 设备侧 1M 长上下文测试（W5-04）此前是 vacuous 的：仓里没有任何把成百上千条消息
 * 塞进一次会话的通道（`docs/notes/w6-plan.md` §四）。没有入口的判据不算判据。
 *
 * ## 为什么「仅 debug 可见」由宿主注入 null 实现，而不是读 `BuildConfig.DEBUG`
 *
 * `:feature-settings` 是**库模块**：AGP 默认不生成它的 `BuildConfig`，而且库自己的
 * `DEBUG` 常量与安装包无关（`SettingsScreen` 类头已就同一件事写过一次，关于版本号）。
 * 所以可见性判据放在 `:app`（它知道真实安装包是否 debuggable），本行只遵守
 * 「回调为 null 就不渲染」这条既有纪律 —— 与技能 / MCP / 沙箱入口同一套。
 *
 * ## 读取与解析都在 IO 段
 *
 * 8 MiB 文件的读取 + 解析放在 `Dispatchers.IO`：放在组合里会直接卡主线程首帧
 * （`AttachmentImporter` 的同一理由）。读取按**上限 + 1 字节**截断，
 * 200 MiB 的文件不会先被读进内存再判超限。
 *
 * ## 成功即跳转
 *
 * 导入成功后由宿主把消息交给对话页并跳过去 —— 「落到对话页看见题面」本身就是成功反馈。
 * 本行仍保留一行「上次结果」副标题：失败时它是唯一的解释来源。
 */
@Composable
internal fun SessionImportRow(
    onImported: (List<ChatMessage>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<ImportStatus?>(null) }
    var reading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        reading = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { readAndParse(context, uri) }
            reading = false
            when (outcome) {
                is ImportOutcome.Parsed -> {
                    status = ImportStatus.Imported(outcome.messages.size, outcome.dropped, outcome.droppedBlank)
                    onImported(outcome.messages)
                }
                is ImportOutcome.Rejected -> status = ImportStatus.Refused(outcome.reason)
                ImportOutcome.Unreadable -> status = ImportStatus.Unreadable
            }
        }
    }

    GlassSettingRow(
        title = stringResource(R.string.settings_import_session),
        subtitle = importSubtitle(status, reading),
        modifier = modifier,
        // 读取期间不可点：第二次选文件会把第一次的协程结果覆盖掉（用户看到的是旧结果）。
        onClick = if (reading) null else ({ picker.launch(arrayOf("*/*")) }),
    )
}

/** 上一次导入的结果。只用于副标题，不进任何业务状态。 */
private sealed interface ImportStatus {
    data class Imported(val count: Int, val dropped: Int, val droppedBlank: Int) : ImportStatus
    data class Refused(val reason: SessionImportParser.Rejection) : ImportStatus
    object Unreadable : ImportStatus
}

/** 解析结果 + 读取失败。与 [ImportStatus] 分开：前者是「一次操作的产出」，后者是「展示态」。 */
private sealed interface ImportOutcome {
    data class Parsed(val messages: List<ChatMessage>, val dropped: Int, val droppedBlank: Int) : ImportOutcome
    data class Rejected(val reason: SessionImportParser.Rejection) : ImportOutcome
    object Unreadable : ImportOutcome
}

@Composable
private fun importSubtitle(status: ImportStatus?, reading: Boolean): String {
    if (reading) return stringResource(R.string.settings_import_session_reading)
    return when (status) {
        null -> stringResource(R.string.settings_import_session_hint)
        is ImportStatus.Imported -> if (status.droppedBlank > 0) {
            // W16-18：空正文行不再静默 —— 「导入成功 (1)」而夹具 300 行的场景必须可见。
            stringResource(R.string.settings_import_session_ok_blank, status.count, status.droppedBlank)
        } else if (status.dropped > 0) {
            stringResource(R.string.settings_import_session_ok_dropped, status.count, status.dropped)
        } else {
            stringResource(R.string.settings_import_session_ok, status.count)
        }
        is ImportStatus.Refused -> rejectionText(status.reason)
        ImportStatus.Unreadable -> stringResource(R.string.settings_import_session_unreadable)
    }
}

/** 拒绝原因 → 上屏文案。每个码一条独立文案：合并成「导入失败」会让用户不知道改什么。 */
@Composable
private fun rejectionText(reason: SessionImportParser.Rejection): String = stringResource(
    when (reason) {
        SessionImportParser.Rejection.TOO_LARGE -> R.string.settings_import_session_too_large
        SessionImportParser.Rejection.TOO_MANY_MESSAGES -> R.string.settings_import_session_too_many
        SessionImportParser.Rejection.MESSAGE_TOO_LONG -> R.string.settings_import_session_too_long
        SessionImportParser.Rejection.EMPTY -> R.string.settings_import_session_empty
        SessionImportParser.Rejection.MALFORMED -> R.string.settings_import_session_malformed
    },
)

/** 读文件 + 解析。**纯 IO**，调用方负责放到 IO 线程。 */
private fun readAndParse(context: Context, uri: Uri): ImportOutcome {
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readAtMost(SessionImportParser.MAX_BYTES + 1)
        }
    }.getOrNull() ?: return ImportOutcome.Unreadable

    val name = displayNameOf(context, uri) ?: uri.lastPathSegment
    return when (val result = SessionImportParser.parse(bytes, SessionImportParser.detectFormat(name))) {
        is SessionImportParser.Result.Ok ->
            ImportOutcome.Parsed(result.messages, result.droppedToolRoles, result.droppedBlank)

        is SessionImportParser.Result.Rejected -> ImportOutcome.Rejected(result.reason)
    }
}

/**
 * SAF 文件名。
 *
 * **必须查 `OpenableColumns.DISPLAY_NAME`**，不能拿 `uri.lastPathSegment` 当文件名：
 * Downloads 的 document id 形如 `msf:1000000123`，没有扩展名 ⇒ 格式判定会一律回落到
 * JSONL，`## user` 的 markdown 文件会被判成一堆坏 JSON 行。
 */
private fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}.getOrNull()

/**
 * 最多读 [limit] 字节。
 *
 * 多读 1 字节的用意：调用方传 `MAX_BYTES + 1`，只要读到第 `MAX_BYTES + 1` 个字节就能
 * 让解析器判「超限」，不必把一个 200 MiB 的文件整个读进内存再报错。
 */
private fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream(minOf(limit, CHUNK_BYTES))
    val buffer = ByteArray(CHUNK_BYTES)
    var total = 0
    while (total < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - total))
        if (read <= 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}

private const val CHUNK_BYTES = 64 * 1024
