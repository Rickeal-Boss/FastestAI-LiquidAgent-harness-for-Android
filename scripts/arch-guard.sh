#!/usr/bin/env bash
# =============================================================================
# arch-guard.sh — LiquidAgent harness-v2 架构守卫（26 条判据，表驱动）
#
# 用法：
#   bash scripts/arch-guard.sh            # 跑全部 26 条判据
#   bash scripts/arch-guard.sh --verbose  # 额外回显每条扫了多少文件
#   bash scripts/arch-guard.sh --help
#
# ⚠️ 必须在**仓库根目录**跑（脚本会自己 cd 到脚本所在目录的上一级，所以从哪调用都
#    可以；但如果你手工复制粘贴单条 grep，务必确认 cwd 是仓库根 —— 在工作区根跑会
#    扫到仓外目录，出现「全红」假象，旧仓踩过）。
#
# -----------------------------------------------------------------------------
# 编号约定
# -----------------------------------------------------------------------------
# 本脚本用 docs/02-roadmap.md §五 的 **G1..G21** 为准（CI 与文档都引用这套编号）。
#   G20（挂账不丢失）与 G21（空转棘轮）是 W6 新增，编号口径仍沿用同一套；
#   它们的判据与自证说明在 docs/06-deferred-ledger.md §零。
# ⚠️ 源码注释里另有一套旧编号 N1/N3/N7（历史遗留，别按它找守卫）：
#      N1 = G1  网络栈收口
#      N3 = G16 中文硬编码棘轮
#      N7 = G6  模块边界
#   输出里这三条会附带 [N-alias: Nx] 标记，便于按源码注释反查。
#
# -----------------------------------------------------------------------------
# 三态纪律（每一条都是旧仓实摔出来的，改之前先读完）
# -----------------------------------------------------------------------------
#   1. 每条判据三种结果：OK / VIOLATION（真违规）/ SELF_FAULT（守卫自身故障，判红）。
#      SELF_FAULT 的典型成因：目标目录被改名、正则写坏、grep 退出码 ≥2。
#      「目录被改名 ⇒ 空守卫永远绿」是旧仓最贵的教训：守卫绿不代表树合规，
#      也可能代表守卫根本没在看。
#
#   1bis. **第四态 VACUOUS（W6 新增）**：判据的**扫描目标集为空**时输出 VACUOUS 而不是 OK。
#      这是 1 号的机器化：目录没改名、grep 也正常，但那条目录里**根本没有它要找的东西**
#      （最典型：G19 要抓「日志拼接凭据」，而 core-engine 一个日志调用点都没有 ⇒ 它守的是空集）。
#      VACUOUS **不判红**（空集不是缺陷），但**必须出现在输出里**，否则「装了烟感但没火」
#      这件事永远不可见。汇总行有 `VACUOUS=n`，并由 G21 做棘轮（只许减不许增）。
#      实现：每个守卫在判 OK 前必须 `scan_declare <本次实际参与判定的对象数>`；
#      **没声明就判 SELF_FAULT** —— 一个不参与空转自检的守卫，等于这条新纪律对它没生效。
#
#   2. **带管道的守卫命令 rc 会被洗白**。`grep ... | grep -vE ...` 的退出码来自
#      后一个 grep，前一个 grep 的 rc=2（文件不存在/正则错）会被吃成 1（无命中）
#      ⇒ 判 OK。本脚本的做法：先 `grep > tmpfile` 捕获 rc，再对**已捕获的文本**
#      做过滤，永不让管道参与判据。
#
#   3. 「必须存在」型断言必须**自己 echo 证据**。`grep -q` 无输出，失败时 rc=1
#      被判成「无命中 = OK」，整条守卫变僵尸规则（旧仓 #7 实测：删掉整行 INTERNET
#      权限，守卫仍全绿）。本脚本的 guard_present 会打印命中行。
#
#   4. **判据钉行首代码形态，不钉裸子串**。裸子串会被自家注释判成违规 ——
#      本仓实测两例：`core-agent/build.gradle.kts` 的注释里写着
#      `implementation(project(":core-net"))`（说明它已删除），裸子串 grep 会判红；
#      `MinSdkSpec` 里含 `ksp`，大小写不敏感的黑名单 grep 也会判红。
#      故所有 absent 型守卫都过滤掉「整行注释」与行尾注释后再判。
#
#   5. 已知例外**显式登记**在 EXCEPTIONS 表里并写理由，只让**新**异常判红。
#      全仓逐条告警 = 没有告警。
#
# -----------------------------------------------------------------------------
# 已知问题 / 踩过的坑（**写新守卫前必读**，否则必重踩）
# -----------------------------------------------------------------------------
#   K1. **markdown 表格不能用 `awk -F'|'` 解析**。markdown 用 `\|` 表示单元格内的
#       字面竖线；`-F'|'` 不认识转义，会把一行多切几段 ⇒ 后面所有列错位。
#       本仓实测后果：G20 读状态列读到「项」列的内容，报「非法状态值」—— 而台账
#       那一行完全合法。**正确手法**（G20 的 ledger_ids / bad_rows 两处已用）：
#           1) 先把 `\|` 换成一个不可能出现的哨兵（本脚本用 "\001"）：
#              `gsub(/\\\|/, "\001", line)`
#           2) 再用 `split(line, f, /\|/)`（**正则字面量**，不是字符串 "|"）
#           3) 列数用 `n != 8` 兜（6 列 + 首尾空段）
#       另：文档里任何「首列是 ID」的多列表格都会被 G20 当成台账行去校验列数 ——
#       写文档时要么用列表，要么别让首列以裸 ID 开头。
#
#   K2. **多行问题块必须用数组传参，不能写 `${var:-}`**。未加引号的 `${var:-}` 会
#       被 bash 词分割，`guard_violation` 收到的是一堆碎片，输出变成
#       「W3-05:」/「非法状态值「」（只允许」/「OPEN」/「/」… —— 排障时完全读不懂。
#       正确手法：`local -a probs=(); while IFS= read -r ln; do probs+=("$ln"); done <<< "$blob"`
#       然后 `guard_violation "$id" "$desc" "${probs[@]}"`。
#
#   K3. **不可复现异常：一次全量跑里 G7/G8/G9 各打印了两遍，OK=26 虚高 3**。
#       时间 2026-11-01 20:47（W6 首次接入 VACUOUS 之后），当次 rc=1。
#       之后 4 次全量跑（含最终验收）**均未复现**，日志比对不出差异，根因未定位。
#       ⚠️ **不许因为「后来几次都正常」就把它当成没发生过**：这类形态下一次出现时，
#       会被误当成新 bug 从头排查。若再现，按这个顺序查：
#         a) `bash -x scripts/arch-guard.sh 2>&1 | grep -cE '^\+ g[789]$'` —— 看函数被调了几次；
#         b) 查 `guard_ok` 是否被同一 id 调了两次（`grep -c 'guard_ok "G7"'` 应为 1）；
#         c) 查是否有第二个进程往同一个输出文件写（当时确有一个并行 agent 在同一工作树
#            跑过守卫；`> file` 是截断写，两个进程交错会互相覆盖但不该产生重复行）。
#       登记它本身就是判据：**不可复现 ≠ 不存在**，与本仓「未触发 ≠ 已验证通过」同源。
#
#   K4. **行数复算禁用 `find … | xargs wc -l | tail -1`** —— 它会**静默少算一半**。
#       机制：`xargs` 按 argv 长度把文件列表切成多批，`wc -l` 对**每批**各打一行
#       `total`；`tail -1` 只留最后一批的 total，前面的批次被无声丢弃，且**不报错**。
#       本仓实测（2026-11-01，W6，13 个模块的 `src/main/**/*.kt`，同一棵树、同一时刻）：
#           find … | xargs wc -l | tail -1        ⇒  18 485   ← 错（只数到最后一批）
#           find … | xargs wc -l | grep -c total  ⇒       2   ← 这就是根因：两批两个 total
#           find … -print0 | xargs -0 cat | wc -l ⇒  34 983   ← 对
#           本脚本 G10 的逐文件累加                ⇒  34 983   ← 对（两者一致）
#       差 16 498 行，而 18 485 看起来完全像一个合法数字。W6 里两个人先后踩
#       （A 流据此报「全仓余量 24 027」，实际余 7 517）。
#       **正确手法（本脚本 G10 用的就是它）**：逐文件累加
#           while IFS= read -r f; do n="$(wc -l < "$f")"; total=$((total+n)); done
#       交叉验证可用 `find … -print0 | xargs -0 cat | wc -l`（cat 拼接后再数，只有一个数）。
#       ⇒ **行数口径三要素，复算时必须三样都对齐**：
#           ① 目录集（`<mod>/src/main`，含不含 test）
#           ② 文件匹配（`-type f -name '*.kt'`）
#           ③ **求和方式**（逐文件累加 / cat 管道；**禁用 `xargs wc -l | tail -1`**）
#       只对齐前两样、第三样用了 `xargs | tail -1`，就会得到一个「差一倍但不报错」的数 ——
#       这是本仓「告警的量级本身就是判据」的反面：**没有告警的错数**。
#       ⚠️ 同一个坑还有一个变体：`xargs` 在**空输入**时仍会执行一次 `wc -l`（读 stdin），
#          输出 `0`，所以「0」既可能是「没有文件」也可能是「find 什么都没找到」，两者
#          在 G10 里的后果不同（前者跳过、后者判红）—— 故 G10 用 `files` 变量判空，
#          不靠 `wc` 的返回值。
#
# -----------------------------------------------------------------------------
# 退出码：0 = 26 条无 VIOLATION 且无 SELF_FAULT（含 VACUOUS 不判红）；
#         1 = 有 VIOLATION 或 SELF_FAULT（两者都判红）
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT" || { echo "::error::arch-guard 无法进入仓库根 $ROOT" >&2; exit 2; }

VERBOSE=0
for a in "$@"; do
  case "$a" in
    --verbose|-v) VERBOSE=1 ;;
    --help|-h)
      # 头部注释自解释：打印第 2 行起、到 `set -uo pipefail` 之前为止。
      # 不用写死的 `sed -n '2,50p'` —— 头部一长就截断（改一次忘一次，必漂）。
      awk 'NR > 1 && /^set -uo pipefail/ { exit } NR > 1 { sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"
      exit 0 ;;
    *) echo "arch-guard: 未知参数 $a（支持 --verbose / --help）" >&2; exit 2 ;;
  esac
done

# =============================================================================
# 常量表
# =============================================================================

# ---- G9：模块清单（必须与 settings.gradle.kts 的 include 数、实际目录数三者相等）
# 新增模块时**先改这里**，G9 会立刻告诉你另外两处是否同步。
MODULES=(
  core-model core-net core-engine core-kernel core-agent core-skills core-mcp
  core-data core-design feature-chat feature-settings feature-tools app
)

# ---- G10：行数闸门（表驱动，来源 docs/01-architecture.md §二 行数预算）
# 格式：<模块目录>|<单文件上限>|<模块总量上限>
# 一个表 + 一个循环，不给每条加一个 check 调用（旧仓 21 条压成 1 条就是为此）。
LINE_CAPS=(
  "core-model|300|2600"
  "core-net|400|1200"
  "core-engine|500|2600"
  # W5：core-agent 实测 6495/6500 触顶 ⇒ 把 policy/log/context 三层下沉到 :core-kernel
  # （同包名搬迁，零 import 改动）。仍是「拆分而不是调大上限」。
  # 1800 而非 1400：只搬 context 时编译红（它反向依赖 policy 与 log），三层合计 1557。
  "core-kernel|400|1800"
  "core-agent|400|6500"
  "core-skills|300|1000"
  "core-mcp|400|1400"
  "core-data|400|4500"
  "core-design|600|12600"
  "feature-chat|500|3500"
  "feature-settings|500|3000"
  # W4 从 :feature-settings 拆出（设置模块 3451 > 3000 触顶 ⇒ 拆分，不是调大上限）。
  # 单文件 600 与 feature-settings 同档：两个页面的原文件都在 300~480 行，
  # 600 是「单个 Screen 文件」的合理上限；2600 是模块预算，不是「先给足再填」。
  "feature-tools|600|2600"
  "app|350|1600"
)
# 全仓 main 源集总量上限（01-architecture.md §二：合计上限 ≈ 42500）
# 42500 = 11 个模块时代的 39900 + :feature-tools 的 2600（W4 新增模块，预算随模块走）。
# ⚠️ 这个数是**预算合计**，不是「可以涨到的水位」：G10 判的是实际行数，
#    触顶的处理永远是拆分，不是调大本常量。
# W5 拆出 :core-kernel 时**没有**调高本常量：那 1198 行是从 core-agent 搬过来的，
# 全仓实际总量没变。给新模块加预算却同时抬高总量上限，等于把「拆分」当成
# 「扩容」用 —— 拆分只该让单个模块变轻，不该让全仓变胖。
TOTAL_LINE_CAP=42500

# ---- G6：模块边界（禁止的 Gradle 依赖边）
# 只钉**文档声明为硬边**的边，不把 01 的拓扑图当白名单用：
#   ① 01「三条不可逆的边」：core-agent ↛ core-net/core-engine、
#      core-design ↛ 任何业务模块、core-model ↛ 框架（后者由 G8 在源码层钉）
#   ② roadmap G6 列出的 core-agent ↛ net/engine/data/design/skills/mcp
#   ③ 结构常识：core-* ↛ feature-*、feature-* ↛ feature-*、任何模块 ↛ app
# 格式：<模块>|<禁止依赖，逗号分隔>
FORBIDDEN_EDGES=(
  "core-model|core-net,core-engine,core-agent,core-skills,core-mcp,core-data,core-design,feature-chat,feature-settings,feature-tools,app"
  "core-net|core-agent,core-engine,core-skills,core-mcp,core-data,core-design,feature-chat,feature-settings,feature-tools,app"
  "core-engine|core-agent,core-skills,core-mcp,core-data,core-design,feature-chat,feature-settings,feature-tools,app"
  # W5 新增 :core-kernel —— 上下文工程只依赖纯契约 :core-model，
  # 不认识引擎/网络/持久化；驱动引擎的 EngineBackedSummarizer 刻意留在 core-agent/loop/（G18）。
  "core-kernel|core-net,core-engine,core-agent,core-skills,core-mcp,core-data,core-design,feature-chat,feature-settings,feature-tools,app"
  "core-agent|core-net,core-engine,core-data,core-design,core-skills,core-mcp,feature-chat,feature-settings,feature-tools,app"
  "core-skills|core-net,core-engine,core-mcp,core-data,core-design,feature-chat,feature-settings,feature-tools,app"
  "core-mcp|core-engine,core-skills,core-data,core-design,feature-chat,feature-settings,feature-tools,app"
  "core-data|core-design,feature-chat,feature-settings,feature-tools,app"
  "core-design|core-model,core-agent,core-net,core-engine,core-skills,core-mcp,core-data,feature-chat,feature-settings,feature-tools,app"
  "feature-chat|core-net,core-engine,core-skills,core-mcp,feature-settings,feature-tools,app"
  "feature-settings|core-net,core-engine,core-skills,core-mcp,feature-chat,feature-tools,app"
  # W4 新增：:feature-tools 与 :feature-settings 同档（feature ↛ feature、↛ app）。
  # 刻意**不**把 core-data 列进来：文件浏览走 java.io / SAF，工具页只读 ToolRegistry，
  # 当前不需要持久化模块。若将来需要，那是「新增一条模块边界」——
  # 同时改本表与 01-architecture.md §一，而不是只在 build.gradle.kts 里加一行依赖。
  "feature-tools|core-net,core-engine,core-skills,core-mcp,feature-chat,feature-settings,app"
  "app|"
)

# ---- G11 / G13 / G16 / G17 阈值
MAX_METHOD_LINES=120
# ---- G12：AgentRunner 门面行数上限（01-architecture.md §二 app 行预算 + roadmap G12）
AGENT_RUNNER_CAP=200
AGENT_RUNNER_PATH="core-agent/src/main/java/com/rickeal/liquidharness/core/agent/AgentRunner.kt"
# ---- G14：lint baseline 冻结值（只许缩，不许涨）
# 当前实测 1 条（首轮 CI 产出）。修掉后把这里改小，**绝不为了变绿而改大**。
LINT_BASELINE_MAX_ISSUES=1
LINT_BASELINE_PATH="app/lint-baseline.xml"
# ---- G16：中文硬编码棘轮基线（01-architecture.md §五：写死 0，只许不增）
CJK_LITERAL_BASELINE=0
# ---- G21：空转（VACUOUS）条数棘轮（W6 新增，与 G14/G16 同款：只许减不许增）
# 期望「扫描目标集为空」的守卫条数上限。首版实测 1 条：
#   G19（日志不拼凭据）—— core-engine/src/main 的日志调用点 = 0 ⇒ 它守的是空集。
# 逐条登记在 docs/06-deferred-ledger.md 的 W6-03 行。
# ⚠️ 修掉一条（例如给 adapter 接了日志让 G19 真开火）就把这里改小，**绝不为了变绿改大**。
#    改大 = 承认「又多了一条没人看的守卫」而不管，这正是本机制要防的事。
VACUOUS_BASELINE=1
APP_MANIFEST="app/src/main/AndroidManifest.xml"
SETTINGS_FILE="settings.gradle.kts"
# ---- G20：挂账单一台账（W6 新增）
# 判据：所有 docs/handoff-*.md 里出现的 W<数字>-<数字> 形态 ID，必须在台账里存在；
#       台账里每条 CLOSED 行的「证据」列必须非空。
LEDGER_PATH="docs/06-deferred-ledger.md"
HANDOFF_GLOB="docs/handoff-*.md"

# ---- 例外登记表（键 = 守卫 ID，值 = 允许的命中行正则）
# 只登记**已知且已论证**的例外，并写清理由；新增例外必须同时写理由。
declare -A EXCEPTIONS=(
  # ---- W2U 登记例外（每条都必须能说出「为什么现在不能修」和「什么时候修」）----
  #
  # G10：LiquidBottomTabs.kt 单文件 1012 行 > 600。
  #   理由：该文件是旧仓 1095 行原样搬入（液态页签是 90 波真机打磨的资产），
  #   拆分需要把 731 行的 LiquidBottomTabs() 按视觉三层外提，属 W2U-2 的活；
  #   本轮已尝试一次并造成编译破损（外提时把 DampedDragAnimation 的回调状态切错），
  #   故先登记、后拆分。**模块总量已按实测校准到 12600，不再上调**。
  ["G10"]='^core-design/src/main/java/com/rickeal/liquidharness/core/design/LiquidBottomTabs\.kt = [0-9]+ 行 > 单文件上限 600'
  #
  # G11：同一个 LiquidBottomTabs() = 731 行 > 120。
  #   与 G10 同源同因，同一波修（W2U-2）。**这是本波唯一的方法体积例外**。
  ["G11"]='^core-design/src/main/java/com/rickeal/liquidharness/core/design/LiquidBottomTabs\.kt:[0-9]+  LiquidBottomTabs\(\)'
  #
  # G18：两个**架构上合法**的引擎调用点。
  #   - RemoteEngineBridge.kt：core-agent 的 LlmEngine 端口 → core-engine 的委托实现，
  #     它的存在正是为了让内核不直接依赖引擎（01-architecture §一 三条不可逆的边）。
  #   - EndpointProbe.kt：设置页「测试连接」，发一条极短请求即取消，不是第二套循环。
  ["G18"]='^core-data/src/main/java/com/rickeal/liquidharness/core/data/(RemoteEngineBridge|EndpointProbe)\.kt:'
)

# =============================================================================
# 基础设施
# =============================================================================

# 源码扫描的公共 grep 参数：排除生成目录 —— build/ 里有 merged manifest、
# 生成源码、KSP 产物，扫进去会出大量假红（旧仓教训）。
#
# ⚠️ 排除目录只在这一个数组里定义一次：GREP_COMMON 与 scan_file_count（VACUOUS 自检的
#    「扫了多少文件」）**都从它派生**。此前两处各写一份，改一处忘另一处会让
#    「grep 扫的面」与「计数扫的面」悄悄分叉 —— 那正是本文件第 4 条纪律要防的形态。
SCAN_EXCLUDE_DIRS=(build .gradle .kotlin .git docs)
GREP_COMMON=(-rnE --binary-files=without-match)
for _excl in "${SCAN_EXCLUDE_DIRS[@]}"; do
  GREP_COMMON+=("--exclude-dir=$_excl")
done
unset _excl
KT_GLOB=(--include=*.kt)
KTS_GLOB=(--include=*.kts --include=*.kt)
# ---- 业务模块的 main 源集（G11 方法体积 / G18 引擎调用点 共用）
# ⚠️ W6 补漏：此前**缺 `core-kernel` 与 `feature-tools`** ⇒ 这两个模块的方法体积与
#    引擎调用点**从未被任何守卫看过**。这不是「新加门禁」，是把已存在的门禁对准它本该
#    覆盖的目录 —— 12 个模块漏 2 个（占 17%），而 core-kernel 恰好是「内核硬化」的落点。
#    漏的原因可复盘：两个模块都是后加的（`feature-tools` W4、`core-kernel` W5），
#    加模块时改的是 `MODULES` / `LINE_CAPS` / G9 三处，**没人回头看 MAIN_PATHS**。
#    ⇒ 纪律：**新增模块时同步检查 MAIN_PATHS / CJK_SCAN_PATHS**（G9 只保证模块数一致，
#       不保证每个模块都被扫）。
MAIN_PATHS=(core-model/src/main core-net/src/main core-engine/src/main core-agent/src/main
            core-kernel/src/main
            core-skills/src/main core-mcp/src/main core-data/src/main core-design/src/main
            feature-chat/src/main feature-settings/src/main feature-tools/src/main
            app/src/main)
# G16 只扫业务模块的 main 源集（app 的文案允许进 strings.xml，且 app 不在棘轮范围）
CJK_SCAN_PATHS=(core-model/src/main core-net/src/main core-engine/src/main core-agent/src/main
                core-skills/src/main core-mcp/src/main core-data/src/main core-design/src/main
                feature-chat/src/main feature-settings/src/main)

OK_COUNT=0
VIOLATION_COUNT=0
FAULT_COUNT=0
VACUOUS_COUNT=0
FAILED_IDS=()
FAULT_IDS=()
VACUOUS_IDS=()

hr() { printf '%s\n' "-------------------------------------------------------------------"; }

# -----------------------------------------------------------------------------
# VACUOUS 自检基础设施（W6）
# -----------------------------------------------------------------------------
# 每个守卫在**得出 OK 结论之前**必须调用 `scan_declare <n>`，n = 本次实际参与判定的
# 对象个数（扫了几个文件 / 比对了几条表项 / 看了几个日志调用点）。
#   n = 0  ⇒ 该守卫对空集判绿 ⇒ 输出 VACUOUS（不判红，但可见），计入 VACUOUS_COUNT
#   n > 0  ⇒ 正常 OK
#   未声明 ⇒ SELF_FAULT（判红）：空转自检没接线，等于这条新纪律对该守卫无效
# SCAN_COUNT 在每个终态（ok/violation/fault）里被取走并复位，所以不存在跨守卫的陈旧值。
SCAN_COUNT=-1
scan_declare() { SCAN_COUNT="${1:-0}"; }
scan_take() {
  local n="$SCAN_COUNT"
  SCAN_COUNT=-1
  printf '%s' "$n"
}

# 数「某个守卫实际会去读的源文件数」——供 absent 型守卫与需要自报规模的守卫共用。
# $1 = 1 表示同时收 *.kts（与 guard_absent 的 --with-kts 对齐）；其余参数 = 扫描路径。
# 排除目录从 SCAN_EXCLUDE_DIRS 派生（与 GREP_COMMON 同源）。
scan_file_count() {
  local with_kts="$1"; shift
  local names=(-name '*.kt') ex=() d p n=0 c
  [ "$with_kts" -eq 1 ] && names+=(-o -name '*.kts')
  for d in "${SCAN_EXCLUDE_DIRS[@]}"; do ex+=(-not -path "*/$d/*"); done
  for p in "$@"; do
    [ -e "$p" ] || continue
    c="$(find "$p" -type f \( "${names[@]}" \) "${ex[@]}" -print 2>/dev/null | wc -l | tr -d ' ')"
    [ -n "$c" ] || c=0
    n=$((n + c))
  done
  printf '%s' "$n"
}

guard_ok() {
  local id="$1" desc="$2" extra="${3:-}"
  local n; n="$(scan_take)"
  if [ "$n" -lt 0 ]; then
    # 不声明 = 该守卫不参与空转自检。判红，理由与「目录被改名」同类：
    # 一个没人验证过「它到底在看什么」的守卫，绿也不构成证据。
    guard_fault "$id" "$desc" \
      "VACUOUS 自检未接线：本守卫判 OK 前没有调用 scan_declare <扫描目标数> —— 空转不可见，故判红"
    return 0
  fi
  if [ "$n" -eq 0 ]; then
    VACUOUS_COUNT=$((VACUOUS_COUNT + 1))
    VACUOUS_IDS+=("$id")
    printf '[%s] %-22s VACUOUS  (扫描目标集为空%s)\n' "$id" "$desc" "${extra:+；$extra}"
    return 0
  fi
  OK_COUNT=$((OK_COUNT + 1))
  printf '[%s] %-22s OK%s\n' "$id" "$desc" "${extra:+  ($extra)}"
}

# 违规输出。**例外在这里过滤**（此前 EXCEPTIONS 只声明不读取 = 装饰表）：
# 命中 EXCEPTIONS[id] 正则的行按「已登记例外」跳过，但**必须回显条数** ——
# 例外要可见，否则它就成了新的静默豁免。
# 违规输出。**例外在这里过滤**（此前 EXCEPTIONS 只声明不读取 = 装饰表）：
# 命中 EXCEPTIONS[id] 正则的行按「已登记例外」跳过，但**必须回显条数** ——
# 例外要可见，否则它就成了新的静默豁免。
#
# ⚠️ 调用方把「⇒ 修法提示」也放在 "$@" 末尾（历史形态），它不是**判据**：
# 若把它算进 kept，例外全被跳过也照样判红（实测踩过）。故按前缀 `⇒ ` 分离：
# 判据行决定红绿，提示行永远回显。
guard_violation() {
  local id="$1" desc="$2"; shift 2
  local pat="${EXCEPTIONS[$id]:-}"
  local kept=() hints=() skipped=0 ln blob
  # ⚠️ 逐行展开：调用点普遍是 `guard_violation ID DESC "$(cat "$raw")"`，
  # 即**一个含多行的实参**。直接 `for ln in "$@"` 会把整块当一个元素，
  # 于是「例外正则命中任意一行」⇒ 整块被跳过 ⇒ 真违规和例外一起消失
  # （W2U 实测：G11/G18 因此对 core-model 的变异完全失明，selftest 报 FAIL）。
  local -a lines=()
  for blob in "$@"; do
    while IFS= read -r ln; do lines+=("$ln"); done <<< "$blob"
  done
  for ln in "${lines[@]}"; do
    case "$ln" in
      '⇒ '*) hints+=("$ln"); continue ;;
    esac
    if [ -n "$pat" ] && printf '%s\n' "$ln" | grep -qE -- "$pat"; then
      skipped=$((skipped + 1))
    else
      kept+=("$ln")
    fi
  done
  if [ "${#kept[@]}" -eq 0 ] && [ "$skipped" -gt 0 ]; then
    # 全部命中都是已登记例外 ⇒ 结论仍是 OK，但**必须走 guard_ok**：
    # 这里此前直接 `OK_COUNT++` 并打印 OK，绕过了 VACUOUS 自检（空集也会被印成 OK）。
    # 让两个 OK 出口收敛到同一个函数，是为了让「扫描目标为空」在这一支也可见。
    guard_ok "$id" "$desc" "已登记例外 $skipped 条，见 EXCEPTIONS 表"
    return 0
  fi
  scan_take > /dev/null   # 判违规时不看空转：违规本身就是「它在看」的证据
  VIOLATION_COUNT=$((VIOLATION_COUNT + 1))
  FAILED_IDS+=("$id")
  printf '[%s] %-22s VIOLATION\n' "$id" "$desc"
  if [ "${#kept[@]}" -gt 0 ]; then printf '%s\n' "${kept[@]}" | sed 's/^/      /'; fi
  if [ "${#hints[@]}" -gt 0 ]; then printf '%s\n' "${hints[@]}" | sed 's/^/      /'; fi
  if [ "$skipped" -gt 0 ]; then printf '      （另有 %d 条已登记例外被跳过）\n' "$skipped"; fi
  return 0
}

guard_fault() {
  local id="$1" desc="$2" reason="$3"
  scan_take > /dev/null   # 复位，避免陈旧值漏进下一个守卫的 guard_ok
  FAULT_COUNT=$((FAULT_COUNT + 1))
  FAULT_IDS+=("$id")
  printf '[%s] %-22s SELF_FAULT\n' "$id" "$desc"
  printf '      %s\n' "$reason"
  printf '      ⇒ 守卫自身故障按**判红**处理（绿不代表合规，可能代表守卫没在看）\n'
}

# 把捕获到的命中行里的「整行注释 / KDoc 行」滤掉（纪律 4：钉代码形态不钉裸子串）
strip_comment_hits() {
  # 输入：grep -rn 输出（file:line:content）；输出：去掉注释行后的文本
  grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|\*|/\*)' || true
}

# absent 型守卫：命中即违规。
# $1=id $2=desc $3=正则  $4=额外过滤（ALLOW 正则，空串表示不过滤）  其余=扫描路径
guard_absent() {
  local id="$1" desc="$2" re="$3" allow="$4"; shift 4
  # 路径位置里可以夹一个 `--with-kts` 开关：把扫描范围从「仅 *.kt」扩到「*.kts + *.kt」。
  # G4a/G5a 的判据钉的是 Gradle 脚本行，此前只收 *.kt ⇒ 从落地起结构性失明、
  # 永远绿（selftest 抓到；这也是「进了 CI 也永远绿」的典型形态）。
  local with_kts=0 arg
  local paths=()
  for arg in "$@"; do
    case "$arg" in
      --with-kts) with_kts=1 ;;
      *) paths+=("$arg") ;;
    esac
  done
  local glob=("${KT_GLOB[@]}")
  if [ "$with_kts" -eq 1 ]; then glob=("${KTS_GLOB[@]}"); fi
  local p
  for p in "${paths[@]}"; do
    if [ ! -e "$p" ]; then
      guard_fault "$id" "$desc" "扫描目标不存在：$p —— 守卫会永远绿，故判红（目录被改名？）"
      return
    fi
  done

  local raw filtered
  # VACUOUS 自检：absent 型守卫的「扫描目标」= 它实际 grep 到的源文件数。
  # 目录存在但里面一个 .kt 都没有时，grep 退出码 1 ⇒ 会被判成「无命中 = OK」，
  # 而它其实什么都没看。这一行让那种形态变成 VACUOUS。
  scan_declare "$(scan_file_count "$with_kts" "${paths[@]}")"
  raw="$(mktemp)"; filtered="$(mktemp)"
  # ⚠️ 关键：grep 的 rc 必须在这里捕获，绝不让管道参与（纪律 2）
  grep "${GREP_COMMON[@]}" "${glob[@]}" "$re" "${paths[@]}" > "$raw" 2>&1
  local rc=$?

  case "$rc" in
    0)
      cp "$raw" "$filtered"
      # 过滤 1：已知例外
      if [ -n "$allow" ]; then
        grep -vE "$allow" "$filtered" > "${filtered}.tmp" 2>/dev/null || true
        mv "${filtered}.tmp" "$filtered"
      fi
      # 过滤 2：注释行（纪律 4）
      strip_comment_hits < "$filtered" > "${filtered}.tmp" 2>/dev/null || true
      mv "${filtered}.tmp" "$filtered"
      if [ -s "$filtered" ]; then
        local n; n="$(wc -l < "$filtered" | tr -d ' ')"
        guard_violation "$id" "$desc" "$(cat "$filtered")"
        guard_violation_note "$n"
      else
        guard_ok "$id" "$desc" "命中均在注释/例外内"
      fi
      ;;
    1)
      guard_ok "$id" "$desc"
      ;;
    *)
      guard_fault "$id" "$desc" "grep 退出码 $rc（正则写坏 / 路径不可读 / 目标被改名）"
      head -n 3 "$raw" 2>/dev/null | sed 's/^/      /'
      ;;
  esac
  rm -f "$raw" "$filtered" "${filtered}.tmp" 2>/dev/null
}

# 给 violation 补一行计数（拆成函数是为了让 guard_absent 的调用点保持一行）
guard_violation_note() {
  printf '      ⇒ 共 %s 处命中（明细见上）\n' "$1"
}

# present 型守卫：必须命中，且**必须自己 echo 证据**（纪律 3）。
# $1=id $2=desc $3=正则 $4=文件
guard_present() {
  local id="$1" desc="$2" re="$3" file="$4"
  if [ ! -f "$file" ]; then
    guard_fault "$id" "$desc" "目标文件不存在：$file"
    return
  fi
  # VACUOUS 自检：present 型守卫的判定对象就是**这一个文件**，文件缺失已由上一行判
  # SELF_FAULT ⇒ 走到这里目标集恒为 1，不存在「扫空集」形态。显式声明而不是省略，
  # 是为了让「本守卫参与空转自检」这件事在代码里可查（省略会被 guard_ok 判红）。
  scan_declare 1
  local raw rc
  raw="$(mktemp)"
  grep -nE "$re" "$file" > "$raw" 2>&1
  rc=$?
  case "$rc" in
    0) guard_ok "$id" "$desc" "$(head -n 1 "$raw" | cut -c1-60)" ;;
    1) guard_violation "$id" "$desc" "缺少必需声明：$re  （文件：$file）" \
         "⇒ 「必须存在」型断言不能靠 grep -q：它无输出，失败会被当成『无命中=OK』而变僵尸规则" ;;
    *) guard_fault "$id" "$desc" "grep 退出码 $rc（$file 不可读？）" ;;
  esac
  rm -f "$raw" 2>/dev/null
}

# =============================================================================
# G1 网络栈收口（[N-alias: N1]）
# =============================================================================
g1() {
  local raw; raw="$(mktemp)"
  local paths=(.) p
  grep "${GREP_COMMON[@]}" "${KT_GLOB[@]}" \
    'okhttp3|java\.net|URL\(' . > "$raw" 2>&1
  local rc=$?
  # VACUOUS 自检：G1 的判定对象是「全仓 .kt 里出现网络栈的那些行」，
  # 目标集 = 被 grep 的源文件数（0 个文件时「无命中」不构成合规证据）。
  scan_declare "$(scan_file_count 0 .)"
  case "$rc" in
    0)
      # 允许：core-net/ 之内（全仓唯一允许发 HTTP 的模块）
      # 允许：core-net/ 之内；以及 **src/test/** —— 测试里的 java.net 是本地假 server
      # 夹具（EndpointProbeTest 用 JDK HttpServer 验「HTML 200 不算通过」），
      # 它不出网、也不是第二套网络栈。判据只该管生产代码。
      strip_comment_hits < "$raw" | grep -vE '^\./core-net/|/src/test/' > "${raw}.f" 2>/dev/null || true
      if [ -s "${raw}.f" ]; then
        guard_violation "G1" "网络栈收口" "$(cat "${raw}.f")" \
          "⇒ 网络栈只允许出现在 core-net/（旧仓守卫 #5 的反向：不是『禁网络』而是『收口』）"
      else
        guard_ok "G1" "网络栈收口" "命中仅在 core-net/ 与注释内"
      fi
      ;;
    1) guard_ok "G1" "网络栈收口" ;;
    *) guard_fault "G1" "网络栈收口" "grep 退出码 $rc" ;;
  esac
  rm -f "$raw" "${raw}.f" 2>/dev/null

  # G1b：传输层不认识供应商 —— core-net 不得引用 core.engine
  guard_absent "G1b" "传输层不认识供应商" \
    'com\.rickeal\.liquidharness\.core\.engine' \
    '' core-net/src/main
}

# =============================================================================
# G2 禁进程执行
# =============================================================================
g2() {
  guard_absent "G2" "禁进程执行" \
    'Runtime\.getRuntime|\.exec\(|ProcessBuilder' '' .
}

# =============================================================================
# G3 禁动态代码加载
# =============================================================================
g3() {
  guard_absent "G3" "禁动态代码加载" \
    'DexClassLoader|PathClassLoader|loadClass' '' .
}

# =============================================================================
# G4 禁依赖黑名单（.kts 与 libs.versions.toml 双扫）
# =============================================================================
g4() {
  # (a) Gradle 脚本：只钉**依赖/插件声明行**，不钉裸子串
  #     —— 裸 `ksp` 会命中 `MinSdkSpec`（大小写不敏感），裸 `room` 会命中注释里的「禁 Room」。
  guard_absent "G4a" "禁依赖黑名单(.kts)" \
    '^[[:space:]]*(implementation|api|compileOnly|runtimeOnly|kapt|ksp|annotationProcessor|testImplementation|androidTestImplementation|classpath|id|alias)[[:space:]]*\(.*(ksp|kapt|room|hilt|dagger|koin|retrofit|coil|sqldelight)' \
    '' . --with-kts

  # (b) 版本目录：只扫非注释行
  if [ ! -f gradle/libs.versions.toml ]; then
    guard_fault "G4b" "禁依赖黑名单(toml)" "gradle/libs.versions.toml 不存在"
    return
  fi
  local raw rc
  raw="$(mktemp)"
  grep -nE '^(room|hilt|dagger|koin|retrofit|coil|sqldelight|ksp|kapt)' gradle/libs.versions.toml > "$raw" 2>&1
  rc=$?
  # VACUOUS 自检：目标集 = 版本目录里被逐行比对的条目数（文件缺失已由上面判 SELF_FAULT）
  scan_declare "$(grep -c '' gradle/libs.versions.toml 2>/dev/null | tr -d ' ')"
  case "$rc" in
    0) guard_violation "G4b" "禁依赖黑名单(toml)" "$(sed 's/^/      /' "$raw")" ;;
    1) guard_ok "G4b" "禁依赖黑名单(toml)" ;;
    *) guard_fault "G4b" "禁依赖黑名单(toml)" "grep 退出码 $rc" ;;
  esac
  rm -f "$raw" 2>/dev/null
}

# =============================================================================
# G5 禁 jitpack
# =============================================================================
g5() {
  guard_absent "G5a" "禁 jitpack 仓库" 'jitpack\.io|maven\("jitpack' '' . --with-kts
  # 「必须存在」型：仓库收口必须显式声明（否则模块级 repositories{} 能悄悄加回来）
  guard_present "G5b" "仓库收口声明" \
    'repositoriesMode\.set\(RepositoriesMode\.FAIL_ON_PROJECT_REPOS\)' "$SETTINGS_FILE"
}

# =============================================================================
# G6 模块边界（[N-alias: N7]）—— 表驱动
# =============================================================================
g6() {
  local bad=0 details=()
  local entry mod forbid dep f deps
  for entry in "${FORBIDDEN_EDGES[@]}"; do
    mod="${entry%%|*}"
    forbid="${entry#*|}"
    f="$mod/build.gradle.kts"
    if [ ! -f "$f" ]; then
      guard_fault "G6" "模块边界" "缺少 $f —— 边界无法校验，判红"
      return
    fi
    [ -z "$forbid" ] && continue
    # 只取**代码行**里的 project(":x")（注释行先剔掉：本仓 core-agent 的注释里
    # 恰好写着 `implementation(project(":core-net"))`，裸子串会误判）
    deps="$(grep -vE '^[[:space:]]*(//|\*|/\*)' "$f" 2>/dev/null \
            | grep -oE 'project\(":[a-z-]+"\)' 2>/dev/null \
            | sed 's/project("://; s/")//' || true)"
    local IFS=','
    for dep in $forbid; do
      if printf '%s\n' "$deps" | grep -qx "$dep"; then
        bad=$((bad + 1))
        details+=("$f -> :$dep  （禁止边：$mod ↛ $dep）")
      fi
    done
    unset IFS
  done
  # VACUOUS 自检：目标集 = 实际比对了多少个模块的禁止边表（表被清空 ⇒ 本守卫失去意义）
  scan_declare "${#FORBIDDEN_EDGES[@]}"
  if [ "$bad" -eq 0 ]; then
    guard_ok "G6" "模块边界" "${#FORBIDDEN_EDGES[@]} 个模块的禁止边均未命中"
  else
    guard_violation "G6" "模块边界" "${details[@]}" \
      "⇒ 硬边来源：01-architecture.md §一「三条不可逆的边」+ roadmap G6"
  fi
}

# =============================================================================
# G7 设计系统零业务
# =============================================================================
g7() {
  guard_absent "G7" "设计系统零业务" \
    'com\.rickeal\.liquidharness\.core\.(model|agent|net|engine|skills|mcp|data)' \
    '' core-design/src/main
}

# =============================================================================
# G8 契约层纯 Kotlin
# =============================================================================
g8() {
  guard_absent "G8" "契约层纯 Kotlin" \
    '^[[:space:]]*import[[:space:]]+(android\.|androidx\.|java\.net\.)' \
    '' core-model/src/main
}

# =============================================================================
# G9 模块数一致
# =============================================================================
g9() {
  if [ ! -f "$SETTINGS_FILE" ]; then
    guard_fault "G9" "模块数一致" "$SETTINGS_FILE 不存在"
    return
  fi
  local includes dirs detail=()
  includes="$(grep -hE '^[[:space:]]*include[[:space:]]*\(' "$SETTINGS_FILE" 2>/dev/null \
              | grep -oE '"[^"]+"' | wc -l | tr -d ' ')"
  # 实际目录数：MODULES 里每个都必须是真目录（缺一个就判红，不做「跳过」）
  dirs=0
  local m
  for m in "${MODULES[@]}"; do
    if [ -d "$m" ] && [ -f "$m/build.gradle.kts" ]; then
      dirs=$((dirs + 1))
    else
      detail+=("模块目录/构建脚本缺失：$m（目录或 build.gradle.kts 不存在）")
    fi
  done
  local arr_len="${#MODULES[@]}"
  # VACUOUS 自检：目标集 = 参与三方比对的模块数
  scan_declare "$arr_len"

  if [ "${#detail[@]}" -gt 0 ]; then
    guard_violation "G9" "模块数一致" "${detail[@]}" \
      "⇒ roadmap §5.2 G9：include 数 == MODULES 数组长度 == 实际目录数"
    return
  fi
  if [ "$includes" -ne "$arr_len" ] || [ "$dirs" -ne "$arr_len" ]; then
    guard_violation "G9" "模块数一致" \
      "settings include = $includes / MODULES 数组 = $arr_len / 实际目录 = $dirs" \
      "⇒ 三者必须相等。新建模块时先改本脚本的 MODULES，再改 settings.gradle.kts"
    return
  fi
  guard_ok "G9" "模块数一致" "include=$includes == 数组=$arr_len == 目录=$dirs"
}

# =============================================================================
# G10 行数闸门（表驱动）
# =============================================================================
# ⚠️ 复算行数时**不要**用 `find … | xargs wc -l | tail -1`：xargs 分批 ⇒ 多个 `total`
#    行 ⇒ `tail -1` 只留最后一批 ⇒ 静默少算一半且不报错（本仓两人先后踩，差 16 498 行）。
#    正确手法就是本函数用的「逐文件 `wc -l < f` 累加」。三要素见头部 K4。
g10() {
  local bad=0 detail=() total_all=0 total_files=0
  local entry mod fcap tcap dir n files f lines mod_total
  for entry in "${LINE_CAPS[@]}"; do
    mod="${entry%%|*}"; entry="${entry#*|}"
    fcap="${entry%%|*}"; tcap="${entry#*|}"
    dir="$mod/src/main"
    # 缺文件/缺目录 ⇒ 判红，不是跳过（旧仓「空守卫永远绿」）
    if [ ! -d "$dir" ]; then
      bad=$((bad + 1))
      detail+=("$dir 不存在 —— 行数闸门对该模块未行使，判红")
      continue
    fi
    files="$(find "$dir" -type f -name '*.kt' 2>/dev/null | sort)"
    if [ -z "$files" ]; then
      # 模块还没有源码：合法（roadmap 允许空目录先就位），但要可见
      [ "$VERBOSE" -eq 1 ] && printf '      [G10] %-16s 0 文件（模块尚未实现，跳过行数检查）\n' "$mod"
      continue
    fi
    mod_total=0
    total_files=$((total_files + $(printf '%s\n' "$files" | wc -l | tr -d ' ')))
    while IFS= read -r f; do
      [ -z "$f" ] && continue
      n="$(wc -l < "$f" 2>/dev/null | tr -d ' ')"
      mod_total=$((mod_total + n))
      if [ "$n" -gt "$fcap" ]; then
        bad=$((bad + 1))
        detail+=("$f = $n 行 > 单文件上限 $fcap（$mod）")
      fi
    done <<< "$files"
    total_all=$((total_all + mod_total))
    if [ "$mod_total" -gt "$tcap" ]; then
      bad=$((bad + 1))
      detail+=("$mod 模块总量 $mod_total 行 > 上限 $tcap")
    fi
    [ "$VERBOSE" -eq 1 ] && printf '      [G10] %-16s 文件数=%-3s 总行=%-6s (上限 %s/%s)\n' \
      "$mod" "$(printf '%s\n' "$files" | wc -l | tr -d ' ')" "$mod_total" "$fcap" "$tcap"
  done
  if [ "$total_all" -gt "$TOTAL_LINE_CAP" ]; then
    bad=$((bad + 1))
    detail+=("全仓 main 源集合计 $total_all 行 > 上限 $TOTAL_LINE_CAP")
  fi
  # VACUOUS 自检：目标集 = 实际被数了行数的 .kt 文件数（全仓 0 个 ⇒ 行数闸门无事可做）
  scan_declare "$total_files"
  if [ "$bad" -eq 0 ]; then
    guard_ok "G10" "行数闸门" "$(printf '%s' "${#LINE_CAPS[@]}") 个模块 / 合计 $total_all 行（上限 $TOTAL_LINE_CAP）"
  else
    guard_violation "G10" "行数闸门" "${detail[@]}" \
      "⇒ 触顶的处理是**拆分**，不是调大上限（01-architecture.md §二）"
  fi
}

# =============================================================================
# G11 方法体积（单方法 ≤ 120 行）
# =============================================================================
g11() {
  local raw; raw="$(mktemp)"
  local p
  for p in "${MAIN_PATHS[@]}"; do
    [ -d "$p" ] || continue
    find "$p" -type f -name '*.kt' 2>/dev/null | while IFS= read -r f; do
      [ -z "$f" ] && continue
      awk -v F="$f" -v CAP="$MAX_METHOD_LINES" '
        function braces(s,   n,i,c) {
          n=0
          for (i=1;i<=length(s);i++) { c=substr(s,i,1); if (c=="{") n++; else if (c=="}") n-- }
          return n
        }
        {
          line=$0; sub(/\r$/,"",line)
          if (inbody) {
            depth += braces(line)
            if (depth <= 0) {
              len = NR - start + 1
              if (len > CAP) printf "%s:%d  %s() = %d 行（上限 %d）\n", F, start, name, len, CAP
              inbody=0; depth=0
            }
            next
          }
          if (line ~ /^[[:space:]]*fun[[:space:]]+/) {
            t=line; sub(/^[[:space:]]*/,"",t)
            name=t; sub(/^fun[[:space:]]+/,"",name); sub(/[({<].*/,"",name)
            if (line ~ /\{[[:space:]]*$/) {
              start=NR; inbody=1; depth=braces(line); if (depth<=0) inbody=0
            } else { pending=1; pstart=NR; pname=name }
          } else if (pending && line ~ /\{[[:space:]]*$/) {
            pending=0; name=pname; start=pstart; inbody=1; depth=braces(line); if (depth<=0) inbody=0
          }
        }' "$f"
    done
  done > "$raw" 2>&1
  # VACUOUS 自检：目标集 = 被逐文件扫过方法体积的 .kt 文件数
  scan_declare "$(scan_file_count 0 "${MAIN_PATHS[@]}")"
  if [ -s "$raw" ]; then
    guard_violation "G11" "方法体积" "$(cat "$raw")" \
      "⇒ 上限 $MAX_METHOD_LINES 行；超限 = 拆方法，不是加注释（roadmap G11）"
  else
    guard_ok "G11" "方法体积" "全部方法 ≤ $MAX_METHOD_LINES 行"
  fi
  rm -f "$raw" 2>/dev/null
}

# =============================================================================
# G12 AgentRunner 门面行数
# =============================================================================
g12() {
  if [ ! -f "$AGENT_RUNNER_PATH" ]; then
    guard_fault "G12" "AgentRunner 门面" "文件不存在：$AGENT_RUNNER_PATH（门面缺失 = 校验未行使，判红）"
    return
  fi
  local n; n="$(wc -l < "$AGENT_RUNNER_PATH" | tr -d ' ')"
  scan_declare 1   # 目标集 = 这一个门面文件（缺失已在上方判 SELF_FAULT）
  if [ "$n" -gt "$AGENT_RUNNER_CAP" ]; then
    guard_violation "G12" "AgentRunner 门面" \
      "$AGENT_RUNNER_PATH = $n 行 > 上限 $AGENT_RUNNER_CAP" \
      "⇒ AgentRunner 只做装配与门面，循环细节归 loop/（旧仓 2900 行前车之鉴）"
  else
    guard_ok "G12" "AgentRunner 门面" "$n 行 ≤ $AGENT_RUNNER_CAP"
  fi
}

# =============================================================================
# G13 测试方法形态（@Test 必须返回 void；反引号名无 JVM 非法字符）
# =============================================================================
g13() {
  local raw; raw="$(mktemp)"
  local tests; tests="$(find . -path ./.git -prune -o -path '*/src/test/*' -name '*.kt' -print 2>/dev/null)"
  if [ -z "$tests" ]; then
    guard_fault "G13" "测试方法形态" "找不到任何 src/test 下的 .kt（扫描面为空，判红）"
    rm -f "$raw"; return
  fi
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    awk -v F="$f" '
      {
        line=$0; sub(/\r$/,"",line)
        t=line; sub(/^[[:space:]]+/,"",t)
        if (t ~ /^@Test([[:space:]]|$)/) { intest=1; next }
        if (intest) {
          if (t ~ /^@/) next                     # 其他注解，继续等 fun
          if (t ~ /^fun[[:space:]]/) {
            # 反引号名里的 JVM 非法字符：. ; [ / ]
            if (t ~ /^fun[[:space:]]+`/) {
              nm=t; sub(/^fun[[:space:]]+`/,"",nm); sub(/`.*/,"",nm)
              if (nm ~ /[.;\[\]\/]/)
                printf "%s:%d 反引号测试名含 JVM 非法字符（. ; [ / ]）: %s\n", F, NR, nm
            }
            # 返回类型：`) : Type` —— JUnit4 要求 void
            if (t ~ /\)[[:space:]]*:[[:space:]]*[A-Za-z_]/)
              printf "%s:%d @Test 方法带返回类型（JUnit4 必须 void，否则整类 initializationError）\n", F, NR
            intest=0
          } else if (t != "") { intest=0 }
        }
      }' "$f"
  done <<< "$tests" > "$raw" 2>&1
  # VACUOUS 自检：目标集 = 被逐行解析的测试文件数（0 个已在上面判 SELF_FAULT，这里再声明一次）
  scan_declare "$(printf '%s\n' "$tests" | wc -l | tr -d ' ')"
  if [ -s "$raw" ]; then
    guard_violation "G13" "测试方法形态" "$(cat "$raw")"
  else
    guard_ok "G13" "测试方法形态" "$(printf '%s\n' "$tests" | wc -l | tr -d ' ') 个测试文件"
  fi
  rm -f "$raw" 2>/dev/null
}

# =============================================================================
# G14 lint baseline 只缩
# =============================================================================
g14() {
  if [ ! -f "$LINT_BASELINE_PATH" ]; then
    guard_fault "G14" "lint baseline 只缩" \
      "$LINT_BASELINE_PATH 不存在 —— 删掉 baseline 不是「缩小」，是让 lint 失去豁免依据，判红"
    return
  fi
  local n; n="$(grep -c '<issue' "$LINT_BASELINE_PATH" 2>/dev/null | tr -d ' ')"
  [ -z "$n" ] && n=0
  # VACUOUS 自检：目标集 = 这一个 baseline 文件。**条目数为 0 是合法终态**（存量清零），
  # 不是「扫空集」—— 棘轮此时仍在做一次真实判定（0 ≤ 冻结值）。故记 1 而不是 n。
  scan_declare 1
  if [ "$n" -gt "$LINT_BASELINE_MAX_ISSUES" ]; then
    guard_violation "G14" "lint baseline 只缩" \
      "$LINT_BASELINE_PATH 有 $n 条 > 冻结值 $LINT_BASELINE_MAX_ISSUES" \
      "⇒ baseline 只许缩（修掉存量后删条目），regen 把新问题冻进 baseline = 僵尸豁免"
  else
    guard_ok "G14" "lint baseline 只缩" "$n 条 ≤ 冻结值 $LINT_BASELINE_MAX_ISSUES"
  fi
}

# =============================================================================
# G15 声明面（INTERNET 权限 + 明文流量关闭 + 禁备份）
# =============================================================================
# G15c 的理由：`android:allowBackup` 默认为 **true**，一旦被谁「顺手」改成 true
# 或整条删掉，Auto Backup 会把应用私有目录整体带走 —— 其中就有
# EndpointSecretStore 的 Keystore 信封密文与端点清单（含 baseUrl）。
# 密文在**同机型**上仍可被同一 Keystore 解开，等于把凭据连同它要访问的地址一起
# 打包给云端/换机通道。这不是推演：旧仓 gallery#1325 的真实事故就是备份恢复了
# 一份本不该离开设备的配置。默认值 true ⇒ 必须显式钉成 false，且必须由守卫看着。
#
# ---- W2 外审 §四.1：G15c 扩成「开关 + 排除面」两段（仍是同一个判据编号） ----
# 只钉 `allowBackup="false"` 是**单点**判据：它是 manifest 里的一个布尔值，
# 一次「顺手打开」就整条失效，而守卫看不出任何中间状态。所以同一个 ID 再钉三件事：
#   ② manifest 必须声明 `android:dataExtractionRules`（把排除面接上系统）；
#   ③ 被引用的规则文件必须存在（属性写了但文件被删 = 声明落空，系统静默全量备份）；
#   ④ 规则文件必须同时含 `harness/secrets` 与 `device-transfer` 两个关键词
#      （前者 = 密钥密文确实被排除；后者 = D2D 段确实写了，只写 cloud-backup 时
#       换机流程仍然会把密文带走）。
# 四条**合并成一次 OK / 一次 VIOLATION**：判据数保持 24 条（CI 与文档都按这个数对齐），
# 且不因为「一处文件缺失连带报三条」把 OK/VIOLATION 计数打乱。
# 语义边界（如实申报）：排除 ≠ 加密。文件仍在应用私有目录，root/调试版可读；
# 这里只回答「系统级备份与换机迁移要不要把它们带走」。
DATA_EXTRACTION_RULES="app/src/main/res/xml/data_extraction_rules.xml"

# G15c 的 present 型探针：stdout 回证据行 / 问题行，rc 0 = 命中 / 1 = 未命中 / 2 = grep 故障。
# 不用 `grep -q`：它无输出，失败会被当成「无命中 = OK」而变成僵尸规则（纪律 3）。
# 也不用临时文件：本机 Git Bash 的 `mktemp` 返回 `C:\...\Temp/tmp.x`（内嵌盘符），
# 安全钩子会拦 `rm`（[SAFE_DELETE_INVALID_PATH]）⇒ 每次守卫漏一个临时文件。
# 命令替换不经过管道，rc 直接来自 grep，纪律 2 的「管道洗白 rc」在这里不适用。
g15c_decl() { # $1=正则 $2=文件
  local out rc
  out="$(grep -nE "$1" "$2" 2>&1)"
  rc=$?
  case "$rc" in
    0) printf '%s\n' "$out" | head -n 1; return 0 ;;
    1) printf '缺少必需声明：%s  （文件：%s）\n' "$1" "$2"; return 1 ;;
    *) return 2 ;;
  esac
}

g15c() {
  local id="G15c" desc="禁备份"
  if [ ! -f "$APP_MANIFEST" ]; then
    guard_fault "$id" "$desc" "目标文件不存在：$APP_MANIFEST"
    return
  fi

  local hits=() probs=() out rc fault=0 kw cnt

  # ① 开关：显式 false（系统默认 true）
  out="$(g15c_decl 'android:allowBackup="false"' "$APP_MANIFEST")"; rc=$?
  if [ "$rc" -eq 0 ]; then hits+=("$out"); elif [ "$rc" -eq 1 ]; then probs+=("$out"); else fault=1; fi

  # ② 接线：manifest 必须把排除面挂上
  out="$(g15c_decl 'android:dataExtractionRules="@xml/data_extraction_rules"' "$APP_MANIFEST")"
  rc=$?
  if [ "$rc" -eq 0 ]; then hits+=("$out"); elif [ "$rc" -eq 1 ]; then probs+=("$out"); else fault=1; fi

  # ③ 规则文件必须存在（属性写了但文件没了 = 声明落空）
  if [ -f "$DATA_EXTRACTION_RULES" ]; then
    hits+=("存在：$DATA_EXTRACTION_RULES")
  else
    probs+=("缺少必需文件：$DATA_EXTRACTION_RULES")
  fi

  # ④ 关键词：密钥密文被排除 + D2D 段真的写了
  if [ -f "$DATA_EXTRACTION_RULES" ]; then
    for kw in 'harness/secrets' 'device-transfer'; do
      cnt="$(grep -cF -- "$kw" "$DATA_EXTRACTION_RULES" 2>/dev/null | tr -d ' ')"
      [ -n "$cnt" ] || cnt=0
      if [ "$cnt" -gt 0 ]; then
        hits+=("$DATA_EXTRACTION_RULES 含关键词 $kw（$cnt 处）")
      else
        probs+=("缺少关键词：$kw  （文件：$DATA_EXTRACTION_RULES）")
      fi
    done
  fi

  if [ "$fault" -eq 1 ]; then
    guard_fault "$id" "$desc" "grep 退出码 ≥2（$APP_MANIFEST 不可读 / 正则写坏）—— 守卫会永远绿，故判红"
  elif [ "${#probs[@]}" -gt 0 ]; then
    guard_violation "$id" "$desc" "$(printf '%s\n' "${probs[@]}")" \
      "⇒ 四条一起判：allowBackup=false 是开关，dataExtractionRules + 规则文件 + 两个关键词是开关被打开时的第二道闸（理由见 $DATA_EXTRACTION_RULES）"
  else
    # VACUOUS 自检：四条子判据的锚是 manifest 这一个文件（规则文件缺失已进 probs）
    scan_declare 1
    guard_ok "$id" "$desc" "$(printf '%s' "${hits[0]}" | cut -c1-60)"
  fi
}

g15() {
  guard_present "G15a" "INTERNET 权限" \
    'android\.permission\.INTERNET' "$APP_MANIFEST"
  guard_present "G15b" "禁明文流量" \
    'android:usesCleartextTraffic="false"' "$APP_MANIFEST"
  g15c
}

# =============================================================================
# G16 中文硬编码棘轮（[N-alias: N3]）
# =============================================================================
# 判据：业务模块 main 源集里「含 CJK 的字符串字面量」数 ≤ 基线（0）。
# ⚠️ 两个必须做对的点：
#   ① 先剥注释：KDoc 与行尾注释里的中文**不计**（01-architecture.md §五 明文规定）。
#      本仓 core-* 的注释几乎全中文，不剥注释这条守卫会立刻报几百条假红。
#   ② 只匹配 **CJK 区**（U+4E00–U+9FFF），不能匹配「任何非 ASCII」：
#      AgentPromptComposer.kt 里有 `—`（em dash），拿 [^\x01-\x7F] 判会误报。
cjk_scan() {
  local f
  for f in $(find "${CJK_SCAN_PATHS[@]}" -type f -name '*.kt' 2>/dev/null | sort); do
    awk -v F="$f" '
      {
        line=$0; sub(/\r$/,"",line)
        t=line; sub(/^[[:space:]]+/,"",t)
        if (t ~ /^(\/\/|\*|\/\*)/) next      # 整行注释 / KDoc
        sub(/\/\/.*/,"",line)                # 行尾注释
        if (line ~ /"/) print F ":" NR ":" line
      }' "$f"
  done
}

g16() {
  local p
  for p in "${CJK_SCAN_PATHS[@]}"; do
    if [ ! -d "$p" ]; then
      guard_fault "G16" "中文硬编码棘轮" "扫描目标不存在：$p"
      return
    fi
  done
  local cand; cand="$(mktemp)"
  cjk_scan > "$cand" 2>&1
  # VACUOUS 自检：目标集 = 含字符串字面量的**候选行数**（剥注释后）。
  # 候选为 0 ⇒ 全仓没有字面量可判，「0 处 ≤ 基线 0」是一句没有信息量的话。
  scan_declare "$(grep -c . "$cand" 2>/dev/null | tr -d ' ')"

  # CJK 判定：优先 grep -P（PCRE，能写 \x{4e00}-\x{9fff}）；
  # 不支持时回落 UTF-8 首字节区间（E4–E9 覆盖 U+4E00–U+9FFF），并显式告知。
  local hits; hits="$(mktemp)"
  if printf 'x\n' | grep -qP '[\x{4e00}-\x{9fff}]' 2>/dev/null; then
    grep -P '[\x{4e00}-\x{9fff}]' "$cand" > "$hits" 2>/dev/null
    [ "$VERBOSE" -eq 1 ] && printf '      [G16] CJK 判定后端：grep -P\n'
  else
    local lo hi
    lo="$(printf '\344')"; hi="$(printf '\351')"
    LC_ALL=C grep -E "[$lo-$hi]" "$cand" > "$hits" 2>/dev/null
    [ "$VERBOSE" -eq 1 ] && printf '      [G16] CJK 判定后端：UTF-8 首字节区间（grep -P 不可用）\n'
  fi
  local rc=$?
  if [ "$rc" -ge 2 ]; then
    guard_fault "G16" "中文硬编码棘轮" "CJK 过滤命令退出码 $rc（后端不可用）"
    rm -f "$cand" "$hits"; return
  fi
  local n; n="$(grep -c . "$hits" 2>/dev/null | tr -d ' ')"
  [ -z "$n" ] && n=0
  if [ "$n" -gt "$CJK_LITERAL_BASELINE" ]; then
    guard_violation "G16" "中文硬编码棘轮" "$(cat "$hits")" \
      "⇒ 含中文字面量 $n 处 > 基线 $CJK_LITERAL_BASELINE（只许不增）" \
      "⇒ 修法：文案移到 strings.xml（由 UI 映射），core-* 只留 ASCII 常量/错误码"
  else
    guard_ok "G16" "中文硬编码棘轮" "$n 处 ≤ 基线 $CJK_LITERAL_BASELINE"
  fi
  rm -f "$cand" "$hits" 2>/dev/null
}

# =============================================================================
# G17 @Serializable 禁密钥字段
# =============================================================================
# 判据：`@Serializable` 注解的 class/data class 的**属性声明**里不得出现
# apiKey / secret / password / token（精确名，大小写不敏感）。
#   · 必须钉 `val NAME:` 声明形态 —— 本仓 RemoteEndpoint.kt 的 KDoc 里
#     反复写着「本类不含 apiKey 字段，且永远不许加」，裸子串会把它判成违规。
#   · 不做名字白名单：`maxOutputTokens` / `promptTokens` 这类计数名天然不等于
#     精确名 `token`，精确匹配即可，白名单只会变成新的漏口。
g17() {
  local dir="core-model/src/main"
  if [ ! -d "$dir" ]; then
    guard_fault "G17" "禁密钥字段" "扫描目标不存在：$dir"
    return
  fi
  local raw; raw="$(mktemp)"
  local f
  for f in $(find "$dir" -type f -name '*.kt' 2>/dev/null | sort); do
    awk -v F="$f" '
      function braces(s,   n,i,c) { n=0; for (i=1;i<=length(s);i++){c=substr(s,i,1); if(c=="{")n++; else if(c=="}")n--} return n }
      function parens(s,   n,i,c) { n=0; for (i=1;i<=length(s);i++){c=substr(s,i,1); if(c=="(")n++; else if(c==")")n--} return n }
      {
        line=$0; sub(/\r$/,"",line)
        t=line; sub(/^[[:space:]]+/,"",t)

        if (pending && t ~ /^(data )?class[[:space:]]/) {
          inclass=1; pending=0
          bdepth=braces(line); pdepth=parens(line)
          hadbody=(bdepth > 0); seenparen=(line ~ /\(/)
          next
        }
        if (inclass) {
          # 属性声明（含主构造器里的 val/var）
          if (match(line, /^[[:space:]]*(val|var)[[:space:]]+[A-Za-z_][A-Za-z0-9_]*[[:space:]]*:/)) {
            p=line; sub(/^[[:space:]]*(val|var)[[:space:]]+/,"",p); sub(/[[:space:]]*:.*/,"",p)
            if (tolower(p) ~ /^(apikey|secret|password|token)$/)
              printf "%s:%d  @Serializable 类内出现密钥字段: %s\n", F, NR, p
          }
          bdepth += braces(line); pdepth += parens(line)
          if (bdepth > 0) hadbody=1
          if (hadbody && bdepth <= 0) { inclass=0; next }
          if (!hadbody && seenparen && pdepth <= 0 && line ~ /\)/) { inclass=0 }
          next
        }
        if (t ~ /^@Serializable/) pending=1
      }' "$f"
  done > "$raw" 2>&1
  # VACUOUS 自检：目标集 = 被逐文件解析 @Serializable 类的 .kt 文件数
  scan_declare "$(scan_file_count 0 "$dir")"
  if [ -s "$raw" ]; then
    guard_violation "G17" "禁密钥字段" "$(cat "$raw")" \
      "⇒ 密钥不进可序列化契约：改由 EndpointSecretStore 现取（03-contracts.md §一）"
  else
    guard_ok "G17" "禁密钥字段"
  fi
  rm -f "$raw" 2>/dev/null
}

# =============================================================================
# G18 引擎调用点收口
# =============================================================================
g18() {
  local raw; raw="$(mktemp)"
  grep "${GREP_COMMON[@]}" "${KT_GLOB[@]}" '\.generate\(' "${MAIN_PATHS[@]}" > "$raw" 2>&1
  local rc=$?
  # VACUOUS 自检：目标集 = 实际命中的引擎调用点行数。
  # 若全仓一个 .generate( 调用点都没有（例如内核改走别的事件通道），
  # 「调用点只在 core-agent/loop/」就成了空话 ⇒ 应报 VACUOUS 而不是 OK。
  scan_declare "$(grep -c . "$raw" 2>/dev/null | tr -d ' ')"
  case "$rc" in
    0)
      # 允许：core-agent 的 loop/ 之内（主循环是唯一该驱动引擎的地方）
      strip_comment_hits < "$raw" \
        | grep -vE '^core-agent/src/main/java/com/rickeal/liquidharness/core/agent/loop/' \
        > "${raw}.f" 2>/dev/null || true
      if [ -s "${raw}.f" ]; then
        guard_violation "G18" "引擎调用点收口" "$(cat "${raw}.f")" \
          "⇒ 只有 core-agent/loop/ 可以驱动引擎（A3：防止第二套循环悄悄长出来）"
      else
        guard_ok "G18" "引擎调用点收口" "调用点仅在 core-agent/loop/"
      fi
      ;;
    1) guard_ok "G18" "引擎调用点收口" ;;
    *) guard_fault "G18" "引擎调用点收口" "grep 退出码 $rc" ;;
  esac
  rm -f "$raw" "${raw}.f" 2>/dev/null
}

# =============================================================================
# G19 日志不得拼接凭据
# =============================================================================
# 背景（来自 OpenShell 的时序思想）：凭据在**出网前**注入 header，所以日志点必须
# 位于 header 装配**之前**；且日志里只允许出现**键名**（`Authorization`），
# 不允许出现**值**。一旦 `log("Authorization=" + secret)` 落进树里，密钥就会进
# logcat / bug report / 崩溃上报 —— 而它同时是一条「用户看不见的外带通道」。
#
# 判据（只扫 core-engine/src/main —— 全仓唯一持有 `SecretProvider` 的模块）：
#   日志调用（`log(` / `logLine(` / `println(` / `Log.v|d|i|w|e(` / `.info|warn|error|debug|trace(`）
#   的同一行里出现凭据标识符，两种形态都抓：
#     ① 字符串模板：`log("auth=$secret")`、`log("${req.apiKey}")`
#     ② 字符串拼接：`log("auth=" + secret)`、`log("k=" + secrets.secretFor(id))`
#
# 三个必须做对的点（都踩过）：
#   · **钉代码形态，不钉裸子串**（纪律 4）：整行注释 / KDoc 先跳过，行尾注释在
#     扫描时按「引号感知」剥掉 —— 裸 grep 会把 `// log("Authorization=" + secret)`
#     这句**讲这个规则的注释本身**判成违规。
#   · **只认值形态**：`log("Authorization")`（只打键名）必须绿。做法是把不含 `$`
#     的纯字面量整段剥掉，剩下的才是「真的被拼进去的标识符」。反过来若直接匹配
#     标识符，会连「说明性文案」一起判红，守卫第一天就没人看。
#   · **`token` 要带词边界**：`max_tokens` / `promptTokens` / `tokens=` 都不是凭据。
#     无边界匹配会让每个记账日志都变红。
#
# ⚠️ 当前 core-engine **没有任何日志调用点**（本判据的 LOG 计数实测为 0）⇒ 本守卫在
#    真实树上是「0 命中 = OK」。它**不为**「让守卫有意义」而去给引擎加日志（那是倒因
#    为果，日志该由真正需要诊断的 adapter 决定）；它的灵敏度由
#    `arch-guard-selftest.sh` 的 `G19` 变异自证保证（注入 `log("Authorization=" + secret)`
#    必判红）。**未来给 core-engine 接日志时，这条守卫自动开始干活。**
#
# 输出：每个日志调用点打一行 `LOG\t<file>:<line>`（用于回显「看过了几个点」，
# 区分「没命中」与「根本没在看」），违规行打 `<file>:<line>  日志拼接凭据: <原文>`。
g19() {
  local id="G19" desc="日志不拼凭据"
  local dir="core-engine/src/main"
  if [ ! -d "$dir" ]; then
    guard_fault "$id" "$desc" "扫描目标不存在：$dir —— 守卫会永远绿，故判红（目录被改名？）"
    return
  fi
  local raw hits
  raw="$(mktemp)"; hits="$(mktemp)"
  local f rc
  for f in $(find "$dir" -type f -name '*.kt' 2>/dev/null | sort); do
    awk -v F="$f" '
      # 引号感知地剥掉行尾注释与「不含插值的纯字面量」：
      # 剩下的 code 里若还有凭据标识符，说明它真的是被拼进日志的参数。
      function strip_line(s,   i, n, c, out, buf, inq, esc, hasdollar) {
        n = length(s); out = ""; buf = ""; inq = 0; esc = 0; hasdollar = 0
        for (i = 1; i <= n; i++) {
          c = substr(s, i, 1)
          if (inq) {
            if (esc) { buf = buf c; esc = 0; continue }
            if (c == "\\") { buf = buf c; esc = 1; continue }
            if (c == "\"") {
              inq = 0
              if (hasdollar) { out = out " " buf " " }   # 模板串保留内容
              buf = ""; hasdollar = 0
              continue
            }
            if (c == "$") hasdollar = 1
            buf = buf c
            continue
          }
          if (c == "\"") { inq = 1; buf = ""; hasdollar = 0; continue }
          if (c == "/" && substr(s, i + 1, 1) == "/") break
          out = out c
        }
        return out
      }
      {
        line = $0; sub(/\r$/, "", line)
        t = line; sub(/^[[:space:]]+/, "", t)
        if (t ~ /^(\/\/|\*|\/\*)/) next        # 整行注释 / KDoc
        code = strip_line(line)
        islog = 0
        if (code ~ /(^|[^A-Za-z0-9_])(log|logLine|logInfo|logWarn|logError|logDebug|println|print)[[:space:]]*\(/) islog = 1
        if (code ~ /(^|[^A-Za-z0-9_])Log\.[vdiwe][[:space:]]*\(/) islog = 1
        if (code ~ /\.(info|warn|error|debug|trace)[[:space:]]*\(/) islog = 1
        if (!islog) next
        printf "LOG\t%s:%d\n", F, NR
        if (code ~ /(^|[^A-Za-z0-9_])(secrets|secret|apiKeys|apiKey|apikey|api_key|apiSecret|Authorization|authorization|Bearer|bearer|password|passwd|credentials|credential|token)([^A-Za-z0-9_]|$)/)
          printf "%s:%d  日志拼接凭据: %s\n", F, NR, t
      }' "$f" >> "$raw" 2>&1
    rc=$?
    if [ "$rc" -ge 2 ]; then
      guard_fault "$id" "$desc" "awk 退出码 $rc（$f 不可读 / 判据程序写坏）—— 守卫会永远绿，故判红"
      rm -f "$raw" "$hits" 2>/dev/null
      return
    fi
  done

  grep -v '^LOG' "$raw" > "$hits" 2>/dev/null
  local nlog nhit
  nlog="$(grep -c '^LOG' "$raw" 2>/dev/null | tr -d ' ')"; [ -n "$nlog" ] || nlog=0
  nhit="$(grep -c . "$hits" 2>/dev/null | tr -d ' ')"; [ -n "$nhit" ] || nhit=0

  if [ "$nhit" -gt 0 ]; then
    guard_violation "$id" "$desc" "$(cat "$hits")" \
      "⇒ 日志只许出现**键名**（如 Authorization），不许出现值：密钥一旦进日志就等于出网" \
      "⇒ 修法：把日志点挪到 header 装配之前，且只打 endpointId / header 名，不打 header 值"
  else
    # VACUOUS 自检：目标集 = **日志调用点个数**（不是文件数）。
    # 这是本机制的第一个真实用户：core-engine 至今零日志调用点 ⇒ 本守卫守的是空集，
    # 它对现役代码判不出任何东西，灵敏度只由 selftest 的变异自证保证。
    scan_declare "$nlog"
    guard_ok "$id" "$desc" "$nlog 个日志调用点，0 处拼接凭据"
  fi
  rm -f "$raw" "$hits" 2>/dev/null
}

# =============================================================================
# G20 挂账不丢失（W6 新增）
# =============================================================================
# 背景：四份交接各自末尾一张挂账表，没有汇总、没有状态字段 ⇒ 27 条里 **7 条静默丢失**
# （占 26%），丢的全是「不产生新代码的收口活」。最贵的 W3-05（journal / FreezeClock）
# 丢了之后，「从能跑到跑不死」这条线被搁置四波，W5 真机又用一个 33.8K vs 32K 的事故
# 重新发现了同一片未覆盖的面。
#
# 判据（三条一起）：
#   ① `docs/handoff-*.md` 里出现的**每一个** `W<数字>-<数字>` 形态 ID，都必须在
#      `docs/06-deferred-ledger.md` 里有一行（开立记录不能凭空消失）；
#   ② 台账里每条 `CLOSED` 行的「证据」列必须非空（关闭必须有可指认的东西）；
#   ③ 台账里每行的「状态」只允许 OPEN / CARRIED / CLOSED 三值。
#
# 三个必须做对的点：
#   · **提取 ID 用全文扫描而不是解析表格**：表格解析对格式极敏感（列数一变就漏），
#     而「ID 出现在 handoff 正文里」本身就该被登记 —— 全文扫描更严，且不会因为
#     某份 handoff 把挂账写成 `### W3-05` 小标题而失明。
#   · **台账 ID 只认表格第一列且形如 W<数字>-<数字>**：正文里提到的编号（例如
#     「见 W5-01」）不算开立，否则删掉表格行仍会被正文蒙过去。
#   · **不许为了变绿删 handoff 里的 ID**：handoff 是历史记录，改它等于篡改开立事实。
g20() {
  local id="G20" desc="挂账不丢失"
  if [ ! -f "$LEDGER_PATH" ]; then
    guard_fault "$id" "$desc" "台账不存在：$LEDGER_PATH —— 挂账没有单一来源，本条无法行使，判红"
    return
  fi
  local files
  files="$(find docs -maxdepth 1 -type f -name 'handoff-*.md' 2>/dev/null | sort)"
  if [ -z "$files" ]; then
    guard_fault "$id" "$desc" "找不到任何 docs/handoff-*.md —— 没有开立记录可对账，判红（改名/搬迁？）"
    return
  fi

  local handoff_ids ledger_ids
  # shellcheck disable=SC2086  # 需要按词展开（多份 handoff）
  handoff_ids="$(grep -ohE 'W[0-9]+-[0-9]+' $files 2>/dev/null | sort -u)"
  # 台账解析：**先保护 `\|`（markdown 表格里的转义竖线）再按 `|` 切**。
  # 踩过的坑：直接用 `awk -F'|'` 时，证据列里写成 `"*Journal*\|*FreezeClock*"` 的
  # 一行会被多切出几段 ⇒ 状态列读到错位内容 ⇒ 报「非法状态值」，而真实状态是合法的。
  # 这不是台账写错，是解析器不认识 markdown 的转义规则。
  ledger_ids="$(awk '
    {
      line = $0
      if (line !~ /^\|/) next
      gsub(/\\\|/, "\001", line)
      n = split(line, f, /\|/)
      v = f[2]
      gsub(/[`[:space:]]/, "", v)
      if (v ~ /^W[0-9]+-[0-9]+$/) print v
    }' "$LEDGER_PATH" | sort -u)"

  local missing="" hid
  while IFS= read -r hid; do
    [ -n "$hid" ] || continue
    printf '%s\n' "$ledger_ids" | grep -qx -- "$hid" || missing="$missing${missing:+ }$hid"
  done <<< "$handoff_ids"

  local bad_rows
  bad_rows="$(awk '
    {
      line = $0
      if (line !~ /^\|/) next
      gsub(/\\\|/, "\001", line)
      n = split(line, f, /\|/)
      idv = f[2]
      gsub(/[`[:space:]]/, "", idv)
      if (idv !~ /^W[0-9]+-[0-9]+$/) next
      # 重复 ID 判红：同一 ID 两行 ⇒ 同一件事有两个状态 ⇒ 谁真谁假不可判。
      # 这不是假想：W6 收口时我与人同时开了一条 W7-04，台账里真出现过两行，
      # 而「不丢」与「不重」是同一个判据的两面 —— 重了等于两行都可能被后来者当成权威。
      if (idv in seen) {
        printf "%s: 台账出现重复 ID（同一 ID 占两行 ⇒ 状态冲突，删掉旧的那行或改号）\n", idv
      }
      seen[idv] = 1
      if (n != 8) {
        printf "%s: 台账行格式错（按 | 切成 %d 段，应为 6 列 + 首尾空段 = 8）—— 若这是文档里别的表，请把首列改成不以 ID 开头\n", idv, n
        next
      }
      st = f[5]
      gsub(/[`[:space:]]/, "", st)
      if (st != "OPEN" && st != "CARRIED" && st != "CLOSED") {
        printf "%s: 非法状态值「%s」（只允许 OPEN / CARRIED / CLOSED）\n", idv, st
        next
      }
      if (st == "CLOSED") {
        e = f[7]
        gsub(/[[:space:]]/, "", e)
        if (e == "" || e == "-" || e == "—")
          printf "%s: CLOSED 行「证据」列为空（关闭必须能指到 文件:行 或提交号）\n", idv
      }
    }' "$LEDGER_PATH")"

  local n_ids
  n_ids="$(printf '%s\n' "$handoff_ids" | grep -c . | tr -d ' ')"
  [ -n "$n_ids" ] || n_ids=0
  # VACUOUS 自检：目标集 = 从 handoff 里提取到的**去重 ID 数**。
  # 为 0 意味着开立记录里一个编号都没有 ⇒ 本条对账无事可做（那本身就是异常，但
  # 它的形态是「空集」而不是「违规」，所以走 VACUOUS 而不是编一个假红）。
  scan_declare "$n_ids"

  # ⚠️ 问题行必须**逐条作为独立实参**传给 guard_violation（它按换行拆行）。
  #    此前写成未加引号的 `${bad_rows:-}` ⇒ 被词分割成一堆碎片，输出里每条问题
  #    被拆成「W3-05:」/「非法状态值「」（只允许」/「OPEN」/「/」… —— 排障时完全读不懂。
  local -a probs=()
  [ -n "$missing" ] && probs+=("台账缺 ID（handoff 里有、台账里没有）：$missing")
  local ln
  while IFS= read -r ln; do
    [ -n "$ln" ] && probs+=("$ln")
  done <<< "$bad_rows"

  if [ "${#probs[@]}" -gt 0 ]; then
    guard_violation "$id" "$desc" "${probs[@]}" \
      "⇒ 挂账唯一来源是 $LEDGER_PATH；新开一条挂账 = 同时加一行（见该文件 §三 纪律 1）"
  else
    guard_ok "$id" "$desc" "$n_ids 个 handoff ID 全在台账内，CLOSED 行证据均非空"
  fi
}

# =============================================================================
# G21 空转棘轮（W6 新增，**meta 守卫**）
# =============================================================================
# 这是唯一一条判「其它守卫的状态」而不是判树的守卫：`VACUOUS` 条数只许减不许增。
# 为什么必须棘轮：VACUOUS 本身不判红，一旦有人新增一条守空集的守卫，「装了烟感但没火」
# 的条数就会从 1 变成 N，而输出里只是一行字 —— 与 G14（lint baseline）/G16（中文棘轮）
# 同款处置：**只许减，不许增**。
# 它的「扫描目标」= 本轮已产出结论的守卫条数（非空由构造保证，所以它自己不可能是 VACUOUS）。
g21() {
  local id="G21" desc="空转棘轮"
  local seen=$((OK_COUNT + VACUOUS_COUNT + VIOLATION_COUNT))
  scan_declare "$seen"
  if [ "$VACUOUS_COUNT" -gt "$VACUOUS_BASELINE" ]; then
    guard_violation "$id" "$desc" \
      "VACUOUS=$VACUOUS_COUNT > 基线 $VACUOUS_BASELINE（${VACUOUS_IDS[*]:-}）" \
      "⇒ 空转条数只许减不许增：每一条 VACUOUS 都是一条「装了烟感但没火」的守卫。" \
      "⇒ 处置：要么给它接上真实的扫描目标（例：给 core-engine 接日志，让 G19 真开火），" \
      "   要么把守卫连同判据一起删掉。**不要靠调大 VACUOUS_BASELINE 变绿**。"
  else
    guard_ok "$id" "$desc" "VACUOUS=$VACUOUS_COUNT ≤ 基线 $VACUOUS_BASELINE（${VACUOUS_IDS[*]:-无}）"
  fi
}

# =============================================================================
# 主流程
# =============================================================================
echo "================ arch-guard（26 条） ================"
echo "root = $ROOT"
hr
g1; g2; g3; g4; g5; g6; g7; g8; g9
g10; g11; g12; g13; g14; g15; g16; g17; g18; g19
g20; g21
hr
printf 'OK=%s  VACUOUS=%s  VIOLATION=%s  SELF_FAULT=%s\n' \
  "$OK_COUNT" "$VACUOUS_COUNT" "$VIOLATION_COUNT" "$FAULT_COUNT"
if [ "${#VACUOUS_IDS[@]}" -gt 0 ]; then
  printf '空转项（VACUOUS，不判红但必须可见）：%s\n' "${VACUOUS_IDS[*]}"
fi
if [ "${#FAILED_IDS[@]}" -gt 0 ]; then
  printf '违规项：%s\n' "${FAILED_IDS[*]}"
fi
if [ "${#FAULT_IDS[@]}" -gt 0 ]; then
  printf '守卫自身故障项：%s\n' "${FAULT_IDS[*]}"
fi

if [ "$VIOLATION_COUNT" -eq 0 ] && [ "$FAULT_COUNT" -eq 0 ]; then
  echo "[arch-guard] result=PASS ok=$OK_COUNT vacuous=$VACUOUS_COUNT violation=0 fault=0"
  exit 0
fi
echo "[arch-guard] result=FAIL ok=$OK_COUNT vacuous=$VACUOUS_COUNT violation=$VIOLATION_COUNT fault=$FAULT_COUNT"
exit 1
