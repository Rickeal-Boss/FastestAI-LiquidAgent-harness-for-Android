package com.rickeal.liquidharness.core.skills

import com.rickeal.liquidharness.core.agent.skill.SkillBody
import com.rickeal.liquidharness.core.agent.skill.SkillCatalog
import com.rickeal.liquidharness.core.agent.skill.SkillMeta
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [SkillCatalog] 的实现：把多个 [SkillSource] 合并成一份**稳定有序**的目录。
 *
 * 两条必须钉死的语义：
 * - **顺序稳定**：`list()` 的返回顺序直接决定 system 前缀里 `[AVAILABLE SKILLS]` 的行序，
 *   顺序一抖，整条会话的 prompt cache 就失效。所以合并后按 name 排序，且排序键唯一（同名已去重）。
 * - **同名先到先得**：来源顺序即优先级。用户导入的技能放在前面就能覆盖内置同名技能，
 *   反过来则内置受保护 —— 这个策略只在这一处定义。
 *
 * 扫描结果缓存在内存里（[cache]）：技能目录在一次会话内几乎不变，每次都读盘会拖慢每轮上下文组装。
 * 用户在设置页导入/删除技能后调 [refresh] 失效即可。
 */
class SkillCatalogImpl(
    private val sources: List<SkillSource>,
) : SkillCatalog {

    /** 首扫串行化：并发进来的 `list()`/`load()` 只真正扫一次盘。 */
    private val mutex = Mutex()

    @Volatile
    private var cache: List<SkillBody>? = null

    override suspend fun list(): List<SkillMeta> = all().map { it.meta }

    override suspend fun load(name: String): SkillBody? = all().firstOrNull { it.meta.name == name }

    override suspend fun search(query: String): List<SkillMeta> {
        val needle = query.trim().lowercase()
        val bodies = all()
        if (needle.isEmpty()) return bodies.map { it.meta }
        return bodies
            .filter { matches(it.meta, needle) }
            .map { it.meta }
    }

    /** 清空缓存：下次访问重新扫描全部来源（导入/删除技能后调用）。 */
    suspend fun refresh() {
        mutex.withLock { cache = null }
    }

    private fun matches(meta: SkillMeta, needle: String): Boolean =
        meta.name.lowercase().contains(needle) || meta.description.lowercase().contains(needle)

    private suspend fun all(): List<SkillBody> {
        cache?.let { return it }
        return mutex.withLock {
            // 双检：等锁期间别的协程可能已经扫完并写入缓存。
            cache ?: scanAll().also { cache = it }
        }
    }

    private suspend fun scanAll(): List<SkillBody> {
        val merged = LinkedHashMap<String, SkillBody>()
        for (source in sources) {
            for (body in source.scan()) {
                val name = body.meta.name
                // 来源以 SkillSource.id 为准：解析器产出的 meta.source 只是占位，
                // 真实的「来自文件 / 来自 assets」只有扫描方知道（UI 分组与诊断要用）。
                if (!merged.containsKey(name)) merged[name] = body.copy(meta = body.meta.copy(source = source.id))
            }
        }
        return merged.values.sortedBy { it.meta.name }
    }
}
