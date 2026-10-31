package com.rickeal.liquidharness.core.skills

/** 导入被拒的原因。[reason] 是 ASCII 常量（可进日志/埋点），[detail] 给用户看具体条目。 */
data class ImportRejection(val reason: String, val detail: String)

/** 导入校验结论。 */
sealed interface ImportVerdict {
    /** 通过。[entryCount] 与 [bytes] 是解压前的账面值，供调用方二次限流。 */
    data class Ok(val entryCount: Int, val bytes: Long) : ImportVerdict

    /** 拒绝。 */
    data class Rejected(val rejection: ImportRejection) : ImportVerdict
}

/**
 * 用户导入技能包的**校验逻辑**（不解压、不落盘）。
 *
 * 为什么先校验条目清单而不是先解压：解压是不可逆的写操作（会覆盖同名技能目录），
 * 而 zip 的条目名与大小在中央目录里就能读到。先把明显有害的包挡在磁盘之外，
 * 再用解压后的真实字节数做第二道闸（zip 里的 size 字段可被伪造，这里只做粗筛）。
 *
 * 判定顺序 = 严重度顺序，一次只报最严重的一条（用户修完最严重的再看下一条，
 * 比一次吐十条更可操作）。
 */
class SkillImportValidator(
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {

    /**
     * @param entries 条目名与声明大小（zip 中央目录里的值）。
     * @param totalBytes 总字节数，默认取 [entries] 之和。
     */
    fun validate(
        entries: List<Pair<String, Long>>,
        totalBytes: Long = entries.sumOf { it.second },
    ): ImportVerdict {
        if (totalBytes > maxBytes) {
            return reject(REASON_TOO_LARGE, "$totalBytes > $maxBytes")
        }
        if (entries.size > maxEntries) {
            return reject(REASON_TOO_MANY_FILES, "${entries.size} > $maxEntries")
        }
        val executable = entries.firstOrNull { isExecutable(it.first) }
        if (executable != null) {
            return reject(REASON_EXECUTABLE_PAYLOAD, executable.first)
        }
        val escaping = entries.firstOrNull { escapes(it.first) }
        if (escaping != null) {
            return reject(REASON_PATH_ESCAPE, escaping.first)
        }
        if (entries.none { isSkillMd(it.first) }) {
            return reject(REASON_MISSING_SKILL_MD, SKILL_FILE)
        }
        return ImportVerdict.Ok(entries.size, totalBytes)
    }

    private fun reject(reason: String, detail: String): ImportVerdict.Rejected =
        ImportVerdict.Rejected(ImportRejection(reason, detail))

    /** 可执行载荷：技能是纯文本，出现这些后缀说明包里夹带了要跑的东西。 */
    private fun isExecutable(name: String): Boolean {
        val lower = name.lowercase()
        return EXECUTABLE_SUFFIXES.any { lower.endsWith(it) }
    }

    /** 路径逃逸：`..` 任意位置出现，或以 `/` 开头（绝对路径）。 */
    private fun escapes(name: String): Boolean =
        name.contains("..") || name.startsWith("/")

    /** 允许 `SKILL.md` 在包根或任意一层目录下（`pdf-toolkit/SKILL.md` 是常见布局）。 */
    private fun isSkillMd(name: String): Boolean =
        name.substringAfterLast('/').equals(SKILL_FILE, ignoreCase = true)

    companion object {
        const val REASON_TOO_LARGE: String = "too_large"
        const val REASON_TOO_MANY_FILES: String = "too_many_files"
        const val REASON_EXECUTABLE_PAYLOAD: String = "executable_payload"
        const val REASON_PATH_ESCAPE: String = "path_escape"
        const val REASON_MISSING_SKILL_MD: String = "missing_skill_md"

        /** 默认上限 2 MiB：纯文本技能包远超这个体积就说明夹带了别的东西。 */
        const val DEFAULT_MAX_BYTES: Long = 2L * 1024L * 1024L

        /** 默认上限 50 个条目。 */
        const val DEFAULT_MAX_ENTRIES: Int = 50

        private const val SKILL_FILE = "SKILL.md"
        private val EXECUTABLE_SUFFIXES = listOf(".dex", ".jar", ".so", ".apk")
    }
}
