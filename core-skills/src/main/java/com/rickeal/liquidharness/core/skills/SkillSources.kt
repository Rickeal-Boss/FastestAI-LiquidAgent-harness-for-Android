package com.rickeal.liquidharness.core.skills

import android.content.res.AssetManager
import com.rickeal.liquidharness.core.agent.skill.SkillBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 技能来源。
 *
 * 一个来源 = 一个可扫描的集合（内置 assets / 用户目录）。来源之间互相独立：
 * 某个来源整块读失败只让该来源返回空列表，不影响其他来源（技能是**增强**，
 * 不能因为一个坏目录让会话起不来）。
 */
interface SkillSource {
    /** 来源标识，写进 SkillMeta.source，仅用于 UI 分组与诊断。 */
    val id: String

    /** 扫描并解析全部技能；坏文件逐个跳过。 */
    suspend fun scan(): List<SkillBody>
}

/**
 * 文件系统来源：扫描 `root/<skill>/SKILL.md` 与 `root/<skill>.md`（**一层，不递归**）。
 *
 * 不递归是刻意的：递归扫描会让用户误放一个 `node_modules` 就拖死主线程，
 * 且会让「导入包」的目录结构（见 [SkillImportValidator]）失去可预期性。
 */
class FileSkillSource(
    private val root: File,
    override val id: String = SOURCE_FILE,
) : SkillSource {

    override suspend fun scan(): List<SkillBody> = withContext(Dispatchers.IO) {
        try {
            readAll()
        } catch (c: CancellationException) {
            // 铁律：取消必须原样上抛，否则上层会把它当成「扫描结果为空」继续跑。
            throw c
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readAll(): List<SkillBody> {
        if (!root.isDirectory) return emptyList()
        val children = root.listFiles() ?: return emptyList()
        val dirs = children.filter { it.isDirectory }.sortedBy { it.name }
        val files = children.filter { it.isFile && it.name.endsWith(MD_SUFFIX) }.sortedBy { it.name }

        val out = mutableListOf<SkillBody>()
        for (dir in dirs) {
            readFile(File(dir, SKILL_FILE))?.let { out += it }
        }
        for (file in files) {
            readFile(file)?.let { out += it }
        }
        return out
    }

    /** 单个文件坏掉（不可读 / 解析失败）只返回 null，绝不向上抛。 */
    private fun readFile(file: File): SkillBody? {
        if (!file.isFile) return null
        val raw = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
        return when (val result = SkillParser.parse(raw, id)) {
            is ParseResult.Ok -> result.body
            is ParseResult.Invalid -> null
        }
    }

    companion object {
        /** 默认来源标识。 */
        const val SOURCE_FILE: String = "file"

        private const val SKILL_FILE = "SKILL.md"
        private const val MD_SUFFIX = ".md"
    }
}

/**
 * APK 内置来源：读 `assets/<prefix>/<skill>/SKILL.md`（以及 `assets/<prefix>/<name>.md`）。
 *
 * assets 是只读的，且 `AssetManager.list()` 只给一层，所以这里手动拼 `SKILL.md` 路径。
 * 该来源不参与 JVM 单测（需要真实 AssetManager），由设备/集成测试覆盖。
 */
class AssetSkillSource(
    private val assets: AssetManager,
    private val prefix: String = DEFAULT_PREFIX,
    override val id: String = SOURCE_ASSET,
) : SkillSource {

    override suspend fun scan(): List<SkillBody> = withContext(Dispatchers.IO) {
        try {
            readAll()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readAll(): List<SkillBody> {
        val entries = assets.list(prefix)?.sorted() ?: return emptyList()
        val out = mutableListOf<SkillBody>()
        for (entry in entries) {
            val path = if (entry.endsWith(MD_SUFFIX)) "$prefix/$entry" else "$prefix/$entry/$SKILL_FILE"
            readAsset(path)?.let { out += it }
        }
        return out
    }

    private fun readAsset(path: String): SkillBody? {
        val raw = try {
            assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            return null
        }
        return when (val result = SkillParser.parse(raw, id)) {
            is ParseResult.Ok -> result.body
            is ParseResult.Invalid -> null
        }
    }

    companion object {
        /** 默认来源标识。 */
        const val SOURCE_ASSET: String = "asset"

        private const val DEFAULT_PREFIX = "skills"
        private const val SKILL_FILE = "SKILL.md"
        private const val MD_SUFFIX = ".md"
    }
}
