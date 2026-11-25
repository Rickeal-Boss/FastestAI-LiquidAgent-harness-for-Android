package com.rickeal.liquidharness

import com.rickeal.liquidharness.core.agent.log.AgentLogStore
import com.rickeal.liquidharness.core.data.AgentLogFileStore
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * W45 落盘最小化（外审 P1-4）：`DiagnosticLogSink` 探针行白名单的行为判据。
 *
 * ## 钉的是什么
 *
 * W44 版镜像把**全部** INFO/WARN/ERROR 落盘 —— 普通 WARN 行（`tool threw`、
 * `unknown tool requested`）会携带用户 prompt 正文 / 工具参数 / 模型输出片段进入
 * `run-as` 可取的持久化文件，隐私面比「内存 ring、重启即消失」大得多。
 * W45 白名单只在落盘收窄；环形缓冲（诊断页）行为不变。W47 P1-1 再把两类动态正文
 * （`run failed:` / `reason=`）从「ASCII + ≤160」收窄为固定诊断码白名单形态，堵住
 * ≤160 的 ASCII 自由文本（英文 prompt 回显 / 邮箱 / 密码）穿透。
 *
 * 三条判据互为方向：
 * - 正向：探针 / 账本行必须落盘（`cached=` 四态是 W12-02 销账的出口判据，不得丢）；
 * - 反向：含用户内容的普通行不得落盘（注入「去掉白名单过滤」⇒ 反向用例 RED）；
 * - 洪水：**先写探针，再灌 600 条普通行**（> 512 队列容量）—— 白名单生效时普通行
 *   不占队列，探针必然落盘；注入「白名单失效」时普通行把探针从队头挤出 ⇒ RED。
 */
class DiagnosticLogSinkTest {

    @Test
    fun probeAndLedgerLinesAreMirrored() {
        // 四态探针（W12-02 出口判据的四类形态）与压缩 / 熔断账本行必须落盘。
        assertTrue(DiagnosticLogSink.isProbeLine("prefix fp=ab12 changed=none prompt=1234 cached=n/a-usage"))
        assertTrue(DiagnosticLogSink.isProbeLine("prefix fp=ab12 changed=none prompt=1300 cached=0(cumulative)"))
        assertTrue(DiagnosticLogSink.isProbeLine("prefix fp=ab12 changed=none prompt=13 cached=4096(cumulative)"))
        assertTrue(DiagnosticLogSink.isProbeLine("prefix fp=ab12 changed=history cached=24(cumulative)"))
        assertTrue(DiagnosticLogSink.isProbeLine("auto_compact=noop reason=below_trigger"))
        assertTrue(DiagnosticLogSink.isProbeLine("auto_compact dropped=9 kept=11 cache_invalidated=false"))
        assertTrue(DiagnosticLogSink.isProbeLine("manual_compress dropped=9 kept=11 cache_invalidated=false"))
        assertTrue(DiagnosticLogSink.isProbeLine("context_overflow_prevented: chars=59064 cap=50227"))
        assertTrue(DiagnosticLogSink.isProbeLine("run cancelled: chars=120 rounds=3"))
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: request rejected (HTTP 400)"))
        assertTrue(DiagnosticLogSink.isProbeLine("context compressed: 30 -> 12 messages (budget=20928 target=12556)"))
        assertTrue(DiagnosticLogSink.isProbeLine("summary truncated: max=1024"))
        assertTrue(DiagnosticLogSink.isProbeLine("layered compress: mode=L1_FALLBACK reason=over-hard-budget"))
        // W49 P2-8：改生产真实写点形态（ContextLimitWriteback:66），原「A -> B」只存在于测试。
        assertTrue(DiagnosticLogSink.isProbeLine("context_limit_writeback_failed endpoint=nv-super-vl verified=131072"))
        // 前后空白不影响前缀判据（写点侧无空白，防御性断言）。
        assertTrue(DiagnosticLogSink.isProbeLine("  cached=4096(cumulative)  "))
    }

    /**
     * W49 P1-4：失败摘要主体从 2 类扩到 13 类固定模板。
     *
     * 动因：W48 的 `evidenceTag` 只在 `request rejected` / `protocol error` 两种主体上
     * 能落盘，限流 / 鉴权 / 服务端 / 超时 / 看门狗 / 重试放弃这些「判序最想诊断」的
     * 失败行被白名单整行拦掉 —— 标签写进了 ring，文件里读不到。
     *
     * 反向断言同样重要：动态正文（异常 message、provider 原文）与「固定主体 + 尾巴」
     * 仍必须拦住。白名单放宽的是**主体枚举**，不是长度或字符集。
     */
    @Test
    fun failureDigestCoversAllFixedDiagnosticBodies() {
        val bodies = listOf(
            "request rejected (HTTP 400)",
            "authentication failed (HTTP 401)",
            "rate limited (HTTP 429)",
            "request timeout (HTTP 408)",
            "server error (HTTP 503)",
            "context window exceeded",
            "timeout: no response from provider within 120000ms",
            "protocol error",
            "giving up after 1 retries",
            "cannot retry after partial output",
            "loop exited without a terminal state",
            "stream failed",
            "uncaught failure",
            "provider error",
            "engine failure",
        )
        bodies.forEach { assertTrue(DiagnosticLogSink.isProbeLine("run failed: $it"), "固定主体应落盘：$it") }
        // W51 P1-2 白侧钉死：第 15 类 engine failure 及其两支证据标签必须落盘可读；
        // 动态 verdict.reason / 原始 message 仍须被拦（此前两支 message 不在主体内 ⇒ 整行被拦、标签读不到）。
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: engine failure [engine:config]"))
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: engine failure [engine:no-events]"))
        assertFalse(DiagnosticLogSink.isProbeLine("run failed: missing API key for endpoint [engine:config]"))
        assertFalse(DiagnosticLogSink.isProbeLine("run failed: endpoint URL rejected: bad scheme [engine:config]"))
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: provider error [frame:protocol-fallback]"))
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: giving up after 1 retries [engine:give-up]"))
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: cannot retry after partial output [engine:partial-output]"))
        assertFalse(DiagnosticLogSink.isProbeLine("run failed: provider error user said hunter2"))
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: rate limited (HTTP 429) [http:rate]"))
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: uncaught failure [engine:uncaught]"))
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: timeout: no response from provider within 60000ms [engine:watchdog]"))
        // 动态正文 / 未登记主体 / 固定主体后缀多余内容 ⇒ 一律拦下。
        assertFalse(DiagnosticLogSink.isProbeLine("run failed: boom at /sdcard/private.log"))
        assertFalse(DiagnosticLogSink.isProbeLine("run failed: giving up after 2 retries: provider said secret"))
        assertFalse(DiagnosticLogSink.isProbeLine("run failed: rate limited (HTTP 429) [http:rate] extra"))
        assertFalse(DiagnosticLogSink.isProbeLine("run failed: stream failed: java.io.IOException: /data/x"))
    }

    @Test
    fun userContentLinesAreNotMirrored() {
        // 普通 WARN 行会携带用户 prompt / 工具参数 / 模型输出片段 —— 白名单必须拦下。
        assertFalse(DiagnosticLogSink.isProbeLine("tool file_read threw: permission denied for /sdcard/secret.txt"))
        assertFalse(DiagnosticLogSink.isProbeLine("unknown tool requested: run_shell"))
        assertFalse(DiagnosticLogSink.isProbeLine("tool blocked by policy: clipboard_get (dangerous)"))
        assertFalse(DiagnosticLogSink.isProbeLine("approval card suppressed after 2 denials: file_write"))
        assertFalse(DiagnosticLogSink.isProbeLine("session import: 120 messages"))
        assertFalse(DiagnosticLogSink.isProbeLine("stream ended without finish_reason; treating as truncated (chars=88)"))
        // 与探针同前缀开头但不是探针的句子不得因子串误入白名单。
        assertFalse(DiagnosticLogSink.isProbeLine("runaway prompt detected (demo)"))
        assertFalse(DiagnosticLogSink.isProbeLine("prefix fp=ab12 changed=none prompt=1234"))
    }

    /**
     * W46 P1-3 必红（正文形态约束）：白名单前缀后拼自由文本 —— 即使前缀命中也不得落盘。
     *
     * `RunTermination.kt:109` 是 `"run failed: " + message`（message 为动态 provider 正文）、
     * `ConversationOps.kt:256` / `LayeredContextCompressor.kt:254` 的 `reason=` 亦动态；
     * provider 400/500 错误体常把用户 prompt 回显进 message。W45 只判 `startsWith(prefix)`
     * 会让这类回显穿透白名单；注入「去掉正文约束」⇒ 本条 RED。
     */
    @Test
    fun dynamicProviderMessageWithUserContentIsNotMirrored() {
        // 前缀命中但正文含非 ASCII 用户内容 / 超长自由文本 ⇒ 拦下。
        assertFalse(DiagnosticLogSink.isProbeLine("run failed: rejected your prompt 我的密码是secret123"))
        assertFalse(DiagnosticLogSink.isProbeLine("run failed: " + "x".repeat(200)))
        assertFalse(DiagnosticLogSink.isProbeLine("manual_compress=noop reason=user wrote " + "y".repeat(200)))
        assertFalse(DiagnosticLogSink.isProbeLine("auto_compact=noop reason=prompt: 帮我写一段很长的中文传记故事"))
        // 固定诊断码形态仍须落盘（防「一刀切全拒」把合法摘要也丢了）。
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: request rejected (HTTP 404)"))
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: protocol error"))
        assertTrue(DiagnosticLogSink.isProbeLine("manual_compress=noop reason=manual compress: nothing to drop"))
        assertTrue(DiagnosticLogSink.isProbeLine("layered compress: mode=L1_FALLBACK reason=over-hard-budget"))
    }

    /**
     * W47 P1-1 必红（外审：短 ASCII 自由文本洞）：W46 的 isSafeFailureDigest /
     * isSafeReasonLine 只判「ASCII + ≤160 字符」，任何 ≤160 的 ASCII 用户内容都能穿透
     * （如英文 prompt 回显）。本条把正文约束收窄为**固定诊断码白名单形态**：只有
     * `(HTTP <num>)` / `protocol error` / 固定 reason 码可落盘，其余 ASCII 自由文本拦下。
     * 注入「保留 W46 ASCII+160 判据」⇒ 本条 RED。
     */
    @Test
    fun shortAsciiFreeTextIsNotMirrored() {
        assertFalse(DiagnosticLogSink.isProbeLine("run failed: your password is hunter2 and email is a@b.com"))
        assertFalse(DiagnosticLogSink.isProbeLine("run failed: please summarize chapter 12 for me"))
        assertFalse(DiagnosticLogSink.isProbeLine("manual_compress=noop reason=user asked to keep it short"))
        assertFalse(DiagnosticLogSink.isProbeLine("auto_compact=noop reason=prompt too long in chapter 12"))
        // 固定诊断码白名单仍须落盘（防一刀切）。
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: request rejected (HTTP 429)"))
        assertTrue(DiagnosticLogSink.isProbeLine("run failed: protocol error"))
        assertTrue(DiagnosticLogSink.isProbeLine("auto_compact=noop reason=below_trigger"))
        assertTrue(DiagnosticLogSink.isProbeLine("manual_compress=noop reason=manual compress: nothing to drop"))
    }

    /**
     * W45 必红（队列满时探针优先保留）：真实落盘 + 真实 512 条 DROP_OLDEST 队列。
     *
     * 先写探针、再灌 600 条普通 WARN（> QUEUE_CAPACITY）。白名单生效时普通行在入队前
     * 被滤掉、探针仍落盘；注入「sink 不过滤、全部入队」⇒ 600 条把先前的探针从队头
     * 挤出 ⇒ 文件里找不到探针 ⇒ 本条 RED。
     */
    @Test
    fun probeLineSurvivesFloodOfUserContentLines() {
        val tmp = Files.createTempDirectory("w45-sink-flood").toFile()
        try {
            val fileStore = AgentLogFileStore(tmp)
            val sink = DiagnosticLogSink(fileStore)
            sink.install()
            try {
                AgentLogStore.clear()
                AgentLogStore.info("prefix fp=abc changed=none prompt=100 cached=n/a-usage")
                repeat(600) { i -> AgentLogStore.warn("tool file_read threw: user payload line $i") }
            } finally {
                AgentLogStore.installFileSink(null)
            }
            runBlocking { delay(600) }
            val lines = runBlocking { fileStore.recentLines(1_000) }
            assertTrue(
                lines.any { it.contains("cached=n/a-usage") },
                "探针行必须先写后仍落盘，不能被 600 条普通行挤出队列：落盘 ${lines.size} 行",
            )
            assertFalse(
                lines.any { it.contains("user payload line") },
                "普通用户内容行不得落盘",
            )
        } finally {
            tmp.deleteRecursively()
        }
    }

    /**
     * W48 P1-1 必红（外审 P1-1 判据不对称）：W47 只给两类动态写点（`run failed` / `reason=`）
     * 加固定形态正则，其余 10 类前缀与 `prefix` 分支仍是 `startsWith` —— 前缀后拼用户正文
     * 穿透落盘（外审等价复刻 9 条）。本条把 9 条自由文本逐条钉为不得落盘：≤160 ASCII /
     * 中文 prompt / 邮箱密码 / 用户 echo。未修版（startsWith）实跑本条 RED，修后 GREEN；
     * 注入「把 `cached=` 放回 startsWith」⇒ 本条 RED。
     */
    @Test
    fun probePrefixWithUserFreeTextIsNotMirrored() {
        assertFalse(DiagnosticLogSink.isProbeLine("cached=我的密码是hunter2 and email a@b.com"))
        assertFalse(DiagnosticLogSink.isProbeLine("context compressed: 用户说帮我写一段中文传记"))
        assertFalse(DiagnosticLogSink.isProbeLine("summary truncated: original prompt was 请总结第12章"))
        assertFalse(DiagnosticLogSink.isProbeLine("run cancelled: user typed 取消任务因为密码是abc123"))
        assertFalse(DiagnosticLogSink.isProbeLine("compress_align=skipped 用户内容 hello"))
        assertFalse(DiagnosticLogSink.isProbeLine("context_overflow_prevented: 用户原始prompt片段"))
        assertFalse(DiagnosticLogSink.isProbeLine("compress=disabled_by_policy 用户原始prompt片段"))
        assertFalse(DiagnosticLogSink.isProbeLine("context_limit_writeback_failed endpoint=user echoed hunter2 email a@b.com verified=1"))
        assertFalse(DiagnosticLogSink.isProbeLine("prefix fp=ab12 changed=none prompt=1234 cached=用户echo内容"))
        // W55 回补的三类新形态反向控（方案席复核钉死）：逐字对生产拼接，只放行整行精确形态。
        // 样本长度均 < MAX_DIGEST_CHARS=160 ⇒ 拦下的原因只能是正则，不是长度闸门。
        // 新版压缩账行：字段顺序调换 / 多一字段 / 结尾无 tokens / 拼用户自由文本一律拦。
        assertFalse(DiagnosticLogSink.isProbeLine("context compressed: 2 -> 1 messages (chars=10, dropped=1, char_budget=100, char_target=50, budget=100, target=50 tokens) 用户echo"))
        assertFalse(DiagnosticLogSink.isProbeLine("context compressed: 2 -> 1 messages (dropped=1, chars=10, char_budget=100, char_target=50, budget=100, target=50 tokens)"))
        assertFalse(DiagnosticLogSink.isProbeLine("context compressed: 2 -> 1 messages (chars=10, dropped=1, char_budget=100, char_target=50, budget=100, target=50, extra=7 tokens)"))
        assertFalse(DiagnosticLogSink.isProbeLine("context compressed: 2 -> 1 messages (chars=10, dropped=1, char_budget=100, char_target=50, budget=100, target=50)"))
        // unreadable：拼尾巴 / file 含斜杠或空格（非 FILE_NAME 字面量）/ backup 非 true|false。
        assertFalse(DiagnosticLogSink.isProbeLine("endpoint_store=unreadable file=endpoints.json backup=true /sdcard/secret.txt"))
        assertFalse(DiagnosticLogSink.isProbeLine("endpoint_store=unreadable file=/sdcard/secret/endpoints.json backup=true"))
        assertFalse(DiagnosticLogSink.isProbeLine("endpoint_store=unreadable file=my endpoints.json backup=true"))
        assertFalse(DiagnosticLogSink.isProbeLine("endpoint_store=unreadable file=endpoints.json backup=yes"))
        // seed_write_failed：拼尾巴 / file 含斜杠或空格。
        assertFalse(DiagnosticLogSink.isProbeLine("endpoint_store=seed_write_failed file=endpoints.json/../../secret"))
        assertFalse(DiagnosticLogSink.isProbeLine("endpoint_store=seed_write_failed file=/sdcard/secret/endpoints.json"))
        assertFalse(DiagnosticLogSink.isProbeLine("endpoint_store=seed_write_failed file=my endpoints.json"))
    }

    /**
     * W48 P1-1 正向（防一刀切）：五类常量模板 + 数字整行形态必须照常落盘 ——
     * `context compressed` 双写点 / `compress_align` / `compress=disabled_by_policy` /
     * `layered compress` 补两形态 / `manual_compress undone`。写点形态逐条对源
     * （ContextCompressor:109,157 / ContextBudget:164,190 / LayeredContextCompressor:161,214 /
     * ConversationOps:281）。
     */
    @Test
    fun fixedFormTemplateLinesStillMirror() {
        assertTrue(
            DiagnosticLogSink.isProbeLine(
                "context compressed: 30 -> 12 messages (budget=20928 target=12556 tokens, dropped=18)",
            ),
        )
        assertTrue(
            DiagnosticLogSink.isProbeLine(
                "context compressed before round 3: 30 -> 12 messages (estimated 40000 > budget 20928)",
            ),
        )
        assertTrue(DiagnosticLogSink.isProbeLine("compress_align=skipped extra=2 cap=450 budget=400"))
        assertTrue(DiagnosticLogSink.isProbeLine("compress=disabled_by_policy prompt=59064 budget=50227"))
        assertTrue(DiagnosticLogSink.isProbeLine("layered compress: l1 could not cut (est=40000 budget=20928)"))
        assertTrue(DiagnosticLogSink.isProbeLine("layered compress: mode=L2 dropped=12 droppedTokens=3456 summaryTokens=789"))
        assertTrue(DiagnosticLogSink.isProbeLine("manual_compress undone cache_invalidated=true"))
        assertTrue(DiagnosticLogSink.isProbeLine("summary truncated: max=1024"))
        assertTrue(DiagnosticLogSink.isProbeLine("run cancelled: chars=120 rounds=3"))
        assertTrue(DiagnosticLogSink.isProbeLine("context_overflow_prevented: chars=59064 cap=50227"))
    }

    /**
     * W55 回补（P0 正向）：`ContextCompressor:135-141` 新版压缩账本整行形态必须落盘。
     *
     * W54 把写点从 `(budget=.. target=.. tokens, dropped=..)` 改成五字段
     * `(chars=.., dropped=.., char_budget=.., char_target=.., budget=.., target=.. tokens)`，
     * W48 登记的旧正则不再命中 ⇒ 真机 `run-as` 里读不到压缩账本（W49/W51/W54 同型第三次复发）。
     * 本条用生产逐字样本钉白，并覆盖 `char_budget`/`char_target` 取 `Int.MAX_VALUE` 的边界。
     */
    @Test
    fun newContextCompressedShapeIsMirrored() {
        // 140 字符：token 与等号间的空格是 gitleaks generic-api-key 的规避写法，逐字保留。
        assertTrue(
            DiagnosticLogSink.isProbeLine(
                "context compressed: 264 -> 77 messages (chars=35338, dropped=70, char_budget=228556, char_target=137133, budget=128000, target=76800 tokens)",
            ),
            "新版 context compressed 五字段账本行必须落盘",
        )
        // 148 字符：targetChars() 在 charBudget==Int.MAX_VALUE 时直接返回，10 位数字须容纳。
        assertTrue(
            DiagnosticLogSink.isProbeLine(
                "context compressed: 264 -> 77 messages (chars=35338, dropped=70, char_budget=2147483647, char_target=2147483647, budget=128000, target=76800 tokens)",
            ),
            "char_budget/char_target=2147483647（10 位）必须落盘",
        )
    }

    /** W55 回补（P0 正向）：`EndpointStore:112-114` 端点清单不可读账行必须落盘。 */
    @Test
    fun endpointStoreUnreadableIsMirrored() {
        assertTrue(DiagnosticLogSink.isProbeLine("endpoint_store=unreadable file=endpoints.json backup=true"))
        assertTrue(DiagnosticLogSink.isProbeLine("endpoint_store=unreadable file=endpoints.json backup=false"))
    }

    /** W55 回补（P0 正向）：`EndpointStore:122` 预置清单播种写失败账行必须落盘。 */
    @Test
    fun endpointStoreSeedWriteFailedIsMirrored() {
        assertTrue(DiagnosticLogSink.isProbeLine("endpoint_store=seed_write_failed file=endpoints.json"))
    }

    /**
     * W55 加固（方案席复核）：三类新形态的**端到端落盘**判据 —— 经真实
     * `AgentLogStore.info/error` → sink 过滤 → `AgentLogFileStore` 写盘 → `recentLines` 读回，
     * 而非只在 [DiagnosticLogSink.isProbeLine] 纯函数层判定。
     *
     * 这是本次 P0 的根因验证：W54 改了生产写点、白名单没跟上 ⇒ 纯函数层永远看不到缺口，
     * 只有「写点真发一条、文件里真能读回」才堵住同型第三次复发。模式复用
     * [probeLineSurvivesFloodOfUserContentLines]（已在实际 CI 稳定运行）。
     */
    @Test
    fun newProbeFormsReachDiskEndToEnd() {
        val tmp = Files.createTempDirectory("w55-sink-e2e").toFile()
        try {
            val fileStore = AgentLogFileStore(tmp)
            val sink = DiagnosticLogSink(fileStore)
            sink.install()
            try {
                AgentLogStore.clear()
                // 三条生产写点形态：ContextCompressor:136 / EndpointStore:113,122。
                AgentLogStore.info(
                    "context compressed: 264 -> 77 messages (chars=35338, dropped=70, char_budget=228556, char_target=137133, budget=128000, target=76800 tokens)",
                )
                AgentLogStore.error("endpoint_store=unreadable file=endpoints.json backup=true")
                AgentLogStore.error("endpoint_store=seed_write_failed file=endpoints.json")
            } finally {
                AgentLogStore.installFileSink(null)
            }
            runBlocking { delay(600) }
            val lines = runBlocking { fileStore.recentLines(1_000) }
            assertTrue(
                lines.any { it.contains("context compressed: 264 -> 77 messages") },
                "新版压缩账行须经 sink 落盘、文件可读回",
            )
            assertTrue(
                lines.any { it.contains("endpoint_store=unreadable file=endpoints.json backup=true") },
                "endpoint unreadable 账行须落盘可读",
            )
            assertTrue(
                lines.any { it.contains("endpoint_store=seed_write_failed file=endpoints.json") },
                "endpoint seed_write_failed 账行须落盘可读",
            )
        } finally {
            tmp.deleteRecursively()
        }
    }
}
