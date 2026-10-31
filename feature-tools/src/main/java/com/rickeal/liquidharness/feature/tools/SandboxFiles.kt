package com.rickeal.liquidharness.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 沙箱文件浏览的**数据层**（状态 / ViewModel / 文件系统读写）。
 *
 * 与 [SandboxFilesScreen] 拆成两个文件只为守 G10（单文件 ≤600 行）与 G11（方法 ≤120 行）：
 * 这一半全是 IO 与状态，那一半全是 Compose，两者没有共享的私有符号。
 */

/** 沙箱目录中的一项。目录的 [sizeBytes] 恒为 0（不递归统计，见 [listDir]）。 */
data class SandboxEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

/** 一次应用内预览。[text] 为 null ⇒ 不是文本 / 读不出来，界面只说明不支持预览。 */
data class SandboxPreview(
    val name: String,
    val text: String?,
    val truncated: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

/** 读 / 删失败的原因码（中文文案由 strings.xml 映射，本模块禁中文字面量）。 */
enum class SandboxError { READ, DELETE }

/** 沙箱浏览状态。[path] 是相对沙箱根的路径段（空 = 根层），不存绝对路径。 */
data class SandboxUiState(
    val path: List<String> = emptyList(),
    val entries: List<SandboxEntry> = emptyList(),
    val loading: Boolean = false,
    val error: SandboxError? = null,
    val preview: SandboxPreview? = null,
)

/** 预览字符上限：够看清是什么文件，又不至于把弹窗撑成阅读器。 */
internal const val PREVIEW_LIMIT_CHARS = 8000

/** 可应用内预览的扩展名。**白名单**：黑名单会把 `.log` / 无扩展名的日志漏出去。 */
private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "json", "jsonl", "csv", "tsv", "xml", "yml", "yaml",
    "kt", "java", "js", "ts", "py", "sh", "html", "css", "log", "ini", "toml", "properties",
)

/**
 * 沙箱浏览的 ViewModel。
 *
 * ## [root] 从哪来
 *
 * 由 `:app` 在组装导航时算好传进来（`AppContainer` 的数据目录下 `workspace/`）。
 * 本模块**刻意不依赖 `:core-data`**（见 `feature-tools/build.gradle.kts` 的模块边界注释），
 * 所以这里只认一个 `File` —— 也就顺带让本类可以在 JVM 单测里用临时目录直接构造。
 *
 * ## 能力边界
 *
 * 只做三件事：列一层目录、读一个文件的前 N 字符、删一项。**不做递归扫描** ——
 * 沙箱里可能有模型写出的深层目录树，递归统计会把「打开这一页」变成全盘遍历。
 *
 * 失败一律折成 [SandboxError] 原因码，不抛给界面：读不到目录时必须能说清「是读不了」
 * 而不是「0 项」（后者会被读成「沙箱被清空了」）。
 */
class SandboxFilesViewModel(private val root: File) : ViewModel() {

    private val _state = MutableStateFlow(SandboxUiState())
    val state: StateFlow<SandboxUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch { load(_state.value.path) }

    fun enter(name: String) = viewModelScope.launch { load(_state.value.path + name) }

    fun up() = viewModelScope.launch {
        val path = _state.value.path
        if (path.isNotEmpty()) load(path.dropLast(1))
    }

    fun onPreview(entry: SandboxEntry) = viewModelScope.launch {
        val file = resolve(_state.value.path, entry.name)
        val preview = ioOrNull { readPreview(file, entry) }
        _state.update { it.copy(preview = preview) }
    }

    fun dismissPreview() {
        _state.update { it.copy(preview = null) }
    }

    fun delete(entry: SandboxEntry) = viewModelScope.launch {
        val removed = ioOrNull { resolve(_state.value.path, entry.name).delete() } == true
        load(_state.value.path, deleteFailed = !removed)
    }

    private fun resolve(path: List<String>, name: String): File =
        File(path.fold(root) { dir, segment -> File(dir, segment) }, name)

    private suspend fun load(path: List<String>, deleteFailed: Boolean = false) {
        _state.update { it.copy(path = path, loading = true, error = null) }
        val dir = path.fold(root) { current, segment -> File(current, segment) }
        val entries = ioOrNull { listDir(root, dir) }
        _state.update {
            it.copy(
                entries = entries.orEmpty(),
                loading = false,
                error = when {
                    entries == null -> SandboxError.READ
                    deleteFailed -> SandboxError.DELETE
                    else -> null
                },
            )
        }
    }

    class Factory(private val root: File) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SandboxFilesViewModel(root) as T
    }
}

/**
 * 列**一层**目录，按「目录在前、名字升序」排序（顺序稳定，用户下次进来位置不变）。
 *
 * 🔒 越界防护：目标目录 canonical 化后必须仍在沙箱根之下，否则当空目录返回。
 * 沙箱里的符号链接可以指向根外（模型写不出来，但用户 `adb push` 可以），浏览面
 * 不该成为走出沙箱的通道。隐藏文件（`.` 开头）不列 —— 与 `file_list` 工具同口径。
 */
private fun listDir(root: File, dir: File): List<SandboxEntry> {
    val canonical = dir.canonicalFile
    if (!canonical.path.startsWith(root.canonicalFile.path)) return emptyList()
    return canonical.listFiles().orEmpty()
        .filterNot { it.name.startsWith(".") }
        .map {
            SandboxEntry(
                name = it.name,
                isDirectory = it.isDirectory,
                sizeBytes = if (it.isDirectory) 0L else it.length(),
                lastModifiedMillis = it.lastModified(),
            )
        }
        .sortedWith(compareByDescending<SandboxEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
}

/** 文本文件白名单判定。目录永远不可预览。 */
internal fun isPreviewable(entry: SandboxEntry): Boolean =
    !entry.isDirectory && entry.name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS

/**
 * 读前 [PREVIEW_LIMIT_CHARS] 个字符（多读 1 个用来判「是否被截断」）。
 *
 * 用 `reader.read(CharArray)` 而不是 `readText()`：后者会把整个文件读进内存 ——
 * 沙箱里一个 50MB 的日志就能把预览变成 OOM。非法 UTF-8 会让 reader 抛异常，
 * 此时按「不是文本」处理，比崩掉诚实。
 */
private fun readPreview(file: File, entry: SandboxEntry): SandboxPreview {
    val meta = SandboxPreview(
        name = entry.name,
        text = null,
        truncated = false,
        sizeBytes = entry.sizeBytes,
        lastModifiedMillis = entry.lastModifiedMillis,
    )
    if (!isPreviewable(entry)) return meta
    val head = runCatching { readPreviewHead(file) }.getOrNull() ?: return meta
    return meta.copy(text = head.first, truncated = head.second)
}

/**
 * 读前 [PREVIEW_LIMIT_CHARS] 个字符，返回「文本 + 是否被截断」。
 *
 * `internal` 而非 private：截断判据是这一屏唯一会**静默出错**的地方（少截一个字符
 * 没人看得出来，多读整个文件就是 OOM），必须能在 JVM 单测里直接钉住。
 */
internal fun readPreviewHead(file: File): Pair<String, Boolean> = file.reader().use { reader ->
    val buffer = CharArray(PREVIEW_LIMIT_CHARS + 1)
    var total = 0
    while (total < buffer.size) {
        val read = reader.read(buffer, total, buffer.size - total)
        if (read <= 0) break
        total += read
    }
    if (total > PREVIEW_LIMIT_CHARS) {
        buffer.concatToString(0, PREVIEW_LIMIT_CHARS) to true
    } else {
        buffer.concatToString(0, total) to false
    }
}

/** 协程内 IO 的失败降级。**必须显式重抛 [CancellationException]**（AGENTS §五.4）。 */
private suspend fun <T> ioOrNull(block: () -> T): T? = try {
    withContext(Dispatchers.IO) { block() }
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    null
}

/** 文件大小。只用 ASCII 单位，中文由 UI 承担（本模块禁中文字面量）。 */
internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> bytes.toString() + " B"
    bytes < 1024L * 1024L -> (bytes / 1024L).toString() + " KB"
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}

internal fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
