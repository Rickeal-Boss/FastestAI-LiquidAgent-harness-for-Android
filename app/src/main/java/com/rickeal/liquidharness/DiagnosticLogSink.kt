package com.rickeal.liquidharness

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.data.AgentLogFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 把 `AgentLogStore` 的记录**按探针行白名单**镜像到落盘日志（W44 建立镜像，W45 落盘最小化）。
 *
 * ## 为什么需要它（W12-02 的原始缺口）
 *
 * `cached=` 四态探针是 INFO 级，只进进程内 200 条环形缓冲 —— 设备侧
 * `files/harness/logs/agent-*.log` 里没有它，「探针按四态落盘」这条出口判据一直未行使：
 * 真机只能间接验证 UI 四态，重启后现场消失。这份镜像让 `cached=n/a-usage` /
 * `cached=0(cumulative)` / `cached=N(cumulative)` 真正落到设备文件上，可用
 * `run-as <pkg>` 取出复核。
 *
 * ## W45 落盘最小化（外审 P1-4：隐私面收窄）
 *
 * W44 版把 `record` 的**全部** INFO/WARN/ERROR 镜像落盘；`sanitize()` 只认凭据形态，
 * 用户 prompt 正文 / 工具参数 / 模型输出片段会随普通 WARN 行（`tool threw`、
 * `unknown tool requested` 等）进入 `run-as` 可取的持久化文件 —— 隐私面从
 * 「内存 ring 200 条、重启即消失」扩到持久化文件。W45 起 sink 只镜像**探针 / 账本行**
 * （[isProbeLine] 白名单），非探针行仍进环形缓冲（诊断页可见）、不落盘。白名单只认
 * **固定诊断前缀**，这些行的正文由常量模板 + 数字 / ASCII 诊断码构成，不含用户内容。
 *
 * ## 架构位置（为什么在 :app 而不是 core-kernel 里直接写文件）
 *
 * `:core-kernel` 不得依赖 `:core-data`（模块边界），所以端口开在
 * `AgentLogStore.installFileSink`，由本类在进程启动时装配。**单一出口**保证
 * 端口语义（锁外调用、异常不影响缓冲）只有一处实现。
 *
 * ## 三条纪律
 *
 * 1. **不阻塞调用方**：sink 只做 `trySend`，真正的 IO 在单个写协程里串行执行
 *    —— 模型流/工具循环线程不得因为日志磁盘慢而卡住。
 * 2. **有界**：队列 512 条 DROP_OLDEST；文件侧 256KB × 3 轮转（`AgentLogFileStore`
 *    自身的上限），总量不随会话时长增长。**探针行优先**：非探针行在入队前就被
 *    白名单滤掉、不占队列容量，高频普通 WARN 不会把 `cached=` 探针挤出队列。
 * 3. **失败只丢一行**：队列满 / 写盘失败都不影响主流程（`AgentLogFileStore.append`
 *    内部已吞 IO 异常且放行取消）。
 */
class DiagnosticLogSink(private val store: AgentLogFileStore) {

    private val queue = Channel<Pair<String, String>>(
        capacity = QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installed = false

    /** 幂等安装：重复调用不会起第二个写协程。 */
    fun install() {
        if (installed) return
        installed = true
        scope.launch {
            for ((level, message) in queue) {
                store.append(level, message)
            }
        }
        AgentLogStore.installFileSink { level, message ->
            // W45 落盘最小化：非探针行只进环形缓冲（诊断页可见），不落盘、不占队列。
            if (!isProbeLine(message)) return@installFileSink
            // DROP_OLDEST 下 trySend 只在通道已关闭时失败 ⇒ 忽略返回值即可。
            queue.trySend(level.name to message)
        }
    }

    companion object {
        /** 512 条 ≈ 一次长 run 的探针行量级；满了丢最旧，不扩内存。 */
        const val QUEUE_CAPACITY: Int = 512

        /**
         * 探针 / 账本行白名单（W45 落盘最小化；W48 P1-1 形态正则全覆盖）。
         *
         * W45–W47 是**前缀**白名单（12 类 `PROBE_PREFIXES`），W47 只给 `run failed` /
         * `reason=` 两类动态写点加固定形态正则，其余 10 类与 `prefix` 分支仍是
         * `startsWith` ⇒ 前缀后拼用户正文可穿透落盘（外审 P1-1 判据不对称，9 条样本
         * 等价复刻坐实）。W48 起**全部写点按整行固定形态正则**匹配（常量模板 + 数字 /
         * 固定诊断码），自由文本一律不落盘；漏登记只损失落盘、不损失 ring（方向安全）。
         *
         * 形态与生产写点一一对应（改写点先改这里，否则该行不再落盘）：
         * - `cached=` 标签值域 ← `ContextBudget.cachedLabel`（n/a-usage / n/a-fields / N(cumulative)）；
         * - `auto_compact`/`manual_compress` reason 码与 applied/undone 账本 ← `ConversationOps` 写点；
         * - `layered compress` 三形态 ← `LayeredContextCompressor:161,214,254`（W47 过窄正则曾把
         *   `l1 could not cut` / `mode=L2` 两条合法账本拦成不落盘，W48 补回）；
         * - `compress_align=skipped` ← `ContextCompressor:157`；`compress=disabled_by_policy` ← `ContextBudget:164`；
         * - `context_overflow_prevented` ← `PreStepBudget:160`；`run cancelled` ← `RunTermination:130`；
         * - `run failed` 固定摘要（可选 ` [证据标签]` 后缀，W48 判序范式诊断用）← `RunTermination:109`
         *   与 `AgentRunner:192`（动态 provider 正文不落盘，只进 ring）；
         * - `context compressed` 双写点 ← `ContextCompressor:109` / `ContextBudget:190`；
         * - `summary truncated` ← `LayeredContextCompressor:211`；
         * - `context limit writeback` ← 测试钉形态（`A -> B`）；
         * - `prefix fp=…` 账行 ← `ContextBudget:278`（fp 为 hex、changed 为段名或 none、cached 为标签值域）。
         */
        /** `cached=` 标签值域（与 `ContextBudget.cachedLabel` 同源；改一处须同步另一处）。 */
        private const val CACHED_LABEL = """(?:n/a-usage|n/a-fields|\d+\(cumulative\))"""

        /** 摘要行长度上限：诊断码 + 数字远小于此；用户整段 prompt 必然超。 */
        private const val MAX_DIGEST_CHARS = 160

        /**
         * 固定失败摘要：`run failed: request rejected (HTTP 400)` / `run failed: protocol error`，
         * 可选 ` [证据标签]` 后缀（W48 判序范式：`Failed.evidenceTag` 随失败行落盘供真机诊断，
         * 标签为 `[a-z0-9:_.-]+` 诊断码形态，不含自由文本）。
         *
         * W46 只判「ASCII + ≤160 字符」，任何 ≤160 的 ASCII 自由文本（英文 prompt 回显、
         * 邮箱、密码）都能穿透 —— 外审 P1-1 的短 ASCII 洞。W47 改为**固定形态整行匹配**；
         * 动态 provider message 仍在进程内 ring（诊断页可见），只是不进 `run-as` 可取的
         * 持久化文件。
         */
        private val FAILURE_DIGEST = Regex(
            """^run failed: (request rejected \(HTTP \d{3}\)|protocol error)( \[[a-z0-9:_.-]+\])?$"""
        )
        /** `auto_compact=noop reason=<固定码>` / `manual_compress=noop reason=<固定码>`。 */
        private val REASON_LINE = Regex(
            """^(auto_compact|manual_compress)=noop reason=[a-z][a-z0-9_-]*(:\w+=[-\d]+)?$"""
        )
        /** `layered compress: mode=<CODE> reason=<固定码>`（含 manual compress: nothing to drop 短语形态）。 */
        private val REASON_CODE_LINE = Regex(
            """^(layered compress: mode=[A-Z0-9_]+ reason=[a-z][a-z0-9_-]*|manual_compress=noop reason=manual compress: nothing to drop)$"""
        )
        /** `auto_compact dropped=N kept=N cache_invalidated=<bool>` 账本行（applied 分支）。 */
        private val REASON_APPLIED = Regex(
            """^auto_compact dropped=\d+ kept=\d+ cache_invalidated=(true|false)$"""
        )

        private val PROBE_FORMS: List<Regex> = listOf(
            // cached= 四态标签独立行（防御性形态；生产常态走 prefix 账行）。
            Regex("""^cached=$CACHED_LABEL$"""),
            // auto_compact / manual_compress noop reason 码（固定码，可带 :key=int 后缀）。
            REASON_LINE,
            // auto_compact applied 账本行。
            REASON_APPLIED,
            // manual_compress applied / undone 账本行。
            Regex("""^manual_compress dropped=\d+ kept=\d+ cache_invalidated=(?:true|false)$"""),
            Regex("""^manual_compress undone cache_invalidated=true$"""),
            // layered compress：mode=CODE reason=码 / nothing to drop / l1 could not cut / L2 账本。
            REASON_CODE_LINE,
            Regex("""^layered compress: l1 could not cut \(est=\d+ budget=\d+\)$"""),
            Regex("""^layered compress: mode=L2 dropped=\d+ droppedTokens=\d+ summaryTokens=\d+$"""),
            // compress_align / compress=disabled_by_policy / context_overflow_prevented / run cancelled。
            Regex("""^compress_align=skipped extra=\d+ cap=\d+ budget=\d+$"""),
            Regex("""^compress=disabled_by_policy prompt=\d+ budget=\d+$"""),
            Regex("""^context_overflow_prevented: chars=\d+ cap=\d+$"""),
            Regex("""^run cancelled: chars=\d+ rounds=\d+$"""),
            // run failed 固定摘要（动态正文不落盘）。
            FAILURE_DIGEST,
            // context compressed 双写点（ContextCompressor 带 tokens/dropped 尾巴、ContextBudget 无）。
            Regex("""^context compressed: \d+ -> \d+ messages \(budget=\d+ target=\d+(?: tokens, dropped=\d+)?\)$"""),
            Regex("""^context compressed before round \d+: \d+ -> \d+ messages \(estimated \d+ > budget \d+\)$"""),
            // summary truncated / context limit writeback。
            Regex("""^summary truncated: max=\d+$"""),
            Regex("""^context limit writeback: \d+ -> \d+$"""),
            // prefix 账行（fp hex、changed 段名或 none、cached 标签值域；prompt 段可缺省）。
            Regex("""^prefix fp=[0-9a-f]+ changed=[A-Za-z_,]+ prompt=\d+ cached=$CACHED_LABEL$"""),
            Regex("""^prefix fp=[0-9a-f]+ changed=[A-Za-z_,]+ cached=$CACHED_LABEL$"""),
        )

        /**
         * 落盘白名单判据（internal 供 JVM 单测直接钉行为）。
         *
         * W48 P1-1 起按 [PROBE_FORMS] **整行固定形态**匹配，不再有 startsWith 兜底：
         * 前缀后拼用户自由文本（中文 prompt / 邮箱密码 / ≤160 ASCII / 用户 echo）一律
         * 不落盘；固定模板 + 数字的账本行照常落盘。
         */
        internal fun isProbeLine(message: String): Boolean {
            val m = message.trim()
            if (m.length > MAX_DIGEST_CHARS) return false
            return PROBE_FORMS.any { it.matches(m) }
        }
    }
}
