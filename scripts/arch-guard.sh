#!/usr/bin/env bash
# =============================================================================
# arch-guard.sh — LiquidAgent harness-v2 架构守卫（18 条，表驱动）
#
# 用法：
#   bash scripts/arch-guard.sh            # 跑全部 18 条
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
# 本脚本用 docs/02-roadmap.md §五 的 **G1..G18** 为准（CI 与文档都引用这套编号）。
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
# 退出码：0 = 18 条全 OK；1 = 有 VIOLATION 或 SELF_FAULT（两者都判红）
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT" || { echo "::error::arch-guard 无法进入仓库根 $ROOT" >&2; exit 2; }

VERBOSE=0
for a in "$@"; do
  case "$a" in
    --verbose|-v) VERBOSE=1 ;;
    --help|-h) sed -n '2,50p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "arch-guard: 未知参数 $a（支持 --verbose / --help）" >&2; exit 2 ;;
  esac
done

# =============================================================================
# 常量表
# =============================================================================

# ---- G9：模块清单（必须与 settings.gradle.kts 的 include 数、实际目录数三者相等）
# 新增模块时**先改这里**，G9 会立刻告诉你另外两处是否同步。
MODULES=(
  core-model core-net core-engine core-agent core-skills core-mcp
  core-data core-design feature-chat feature-settings app
)

# ---- G10：行数闸门（表驱动，来源 docs/01-architecture.md §二 行数预算）
# 格式：<模块目录>|<单文件上限>|<模块总量上限>
# 一个表 + 一个循环，不给每条加一个 check 调用（旧仓 21 条压成 1 条就是为此）。
LINE_CAPS=(
  "core-model|300|2600"
  "core-net|400|1200"
  "core-engine|500|2600"
  "core-agent|400|6500"
  "core-skills|300|1000"
  "core-mcp|400|1400"
  "core-data|400|4500"
  "core-design|600|12000"
  "feature-chat|500|3500"
  "feature-settings|500|3000"
  "app|350|1600"
)
# 全仓 main 源集总量上限（01-architecture.md §二：合计上限 ≈ 39900）
TOTAL_LINE_CAP=39900

# ---- G6：模块边界（禁止的 Gradle 依赖边）
# 只钉**文档声明为硬边**的边，不把 01 的拓扑图当白名单用：
#   ① 01「三条不可逆的边」：core-agent ↛ core-net/core-engine、
#      core-design ↛ 任何业务模块、core-model ↛ 框架（后者由 G8 在源码层钉）
#   ② roadmap G6 列出的 core-agent ↛ net/engine/data/design/skills/mcp
#   ③ 结构常识：core-* ↛ feature-*、feature-* ↛ feature-*、任何模块 ↛ app
# 格式：<模块>|<禁止依赖，逗号分隔>
FORBIDDEN_EDGES=(
  "core-model|core-net,core-engine,core-agent,core-skills,core-mcp,core-data,core-design,feature-chat,feature-settings,app"
  "core-net|core-agent,core-engine,core-skills,core-mcp,core-data,core-design,feature-chat,feature-settings,app"
  "core-engine|core-agent,core-skills,core-mcp,core-data,core-design,feature-chat,feature-settings,app"
  "core-agent|core-net,core-engine,core-data,core-design,core-skills,core-mcp,feature-chat,feature-settings,app"
  "core-skills|core-net,core-engine,core-mcp,core-data,core-design,feature-chat,feature-settings,app"
  "core-mcp|core-engine,core-skills,core-data,core-design,feature-chat,feature-settings,app"
  "core-data|core-design,feature-chat,feature-settings,app"
  "core-design|core-model,core-agent,core-net,core-engine,core-skills,core-mcp,core-data,feature-chat,feature-settings,app"
  "feature-chat|core-net,core-engine,core-skills,core-mcp,core-data,feature-settings,app"
  "feature-settings|core-net,core-engine,core-skills,core-mcp,feature-chat,app"
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
APP_MANIFEST="app/src/main/AndroidManifest.xml"
SETTINGS_FILE="settings.gradle.kts"

# ---- 例外登记表（键 = 守卫 ID，值 = 允许的命中行正则）
# 只登记**已知且已论证**的例外，并写清理由；新增例外必须同时写理由。
declare -A EXCEPTIONS=(
  # 目前无例外。示例（别照抄，用完就删）：
  # ["G2"]='^\./core-data/src/main/.*/LegacyShell\.kt:[0-9]+:.*已登记：'"
)

# =============================================================================
# 基础设施
# =============================================================================

# 源码扫描的公共 grep 参数：排除生成目录 —— build/ 里有 merged manifest、
# 生成源码、KSP 产物，扫进去会出大量假红（旧仓教训）。
GREP_COMMON=(
  -rnE
  --binary-files=without-match
  --exclude-dir=build
  --exclude-dir=.gradle
  --exclude-dir=.kotlin
  --exclude-dir=.git
  --exclude-dir=docs
)
KT_GLOB=(--include=*.kt)
KTS_GLOB=(--include=*.kts --include=*.kt)
MAIN_PATHS=(core-model/src/main core-net/src/main core-engine/src/main core-agent/src/main
            core-skills/src/main core-mcp/src/main core-data/src/main core-design/src/main
            feature-chat/src/main feature-settings/src/main app/src/main)
# G16 只扫业务模块的 main 源集（app 的文案允许进 strings.xml，且 app 不在棘轮范围）
CJK_SCAN_PATHS=(core-model/src/main core-net/src/main core-engine/src/main core-agent/src/main
                core-skills/src/main core-mcp/src/main core-data/src/main core-design/src/main
                feature-chat/src/main feature-settings/src/main)

OK_COUNT=0
VIOLATION_COUNT=0
FAULT_COUNT=0
FAILED_IDS=()
FAULT_IDS=()

hr() { printf '%s\n' "-------------------------------------------------------------------"; }

guard_ok() {
  local id="$1" desc="$2" extra="${3:-}"
  OK_COUNT=$((OK_COUNT + 1))
  printf '[%s] %-22s OK%s\n' "$id" "$desc" "${extra:+  ($extra)}"
}

guard_violation() {
  local id="$1" desc="$2"; shift 2
  VIOLATION_COUNT=$((VIOLATION_COUNT + 1))
  FAILED_IDS+=("$id")
  printf '[%s] %-22s VIOLATION\n' "$id" "$desc"
  printf '%s\n' "$@" | sed 's/^/      /'
}

guard_fault() {
  local id="$1" desc="$2" reason="$3"
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
  local paths=("$@")
  local p
  for p in "${paths[@]}"; do
    if [ ! -e "$p" ]; then
      guard_fault "$id" "$desc" "扫描目标不存在：$p —— 守卫会永远绿，故判红（目录被改名？）"
      return
    fi
  done

  local raw filtered
  raw="$(mktemp)"; filtered="$(mktemp)"
  # ⚠️ 关键：grep 的 rc 必须在这里捕获，绝不让管道参与（纪律 2）
  grep "${GREP_COMMON[@]}" "${KT_GLOB[@]}" "$re" "${paths[@]}" > "$raw" 2>&1
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
  case "$rc" in
    0)
      # 允许：core-net/ 之内（全仓唯一允许发 HTTP 的模块）
      strip_comment_hits < "$raw" | grep -vE '^\./core-net/' > "${raw}.f" 2>/dev/null || true
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
    '' .

  # (b) 版本目录：只扫非注释行
  if [ ! -f gradle/libs.versions.toml ]; then
    guard_fault "G4b" "禁依赖黑名单(toml)" "gradle/libs.versions.toml 不存在"
    return
  fi
  local raw rc
  raw="$(mktemp)"
  grep -nE '^(room|hilt|dagger|koin|retrofit|coil|sqldelight|ksp|kapt)' gradle/libs.versions.toml > "$raw" 2>&1
  rc=$?
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
  guard_absent "G5a" "禁 jitpack 仓库" 'jitpack\.io|maven\("jitpack' '' .
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
g10() {
  local bad=0 detail=() total_all=0
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
  if [ "$n" -gt "$LINT_BASELINE_MAX_ISSUES" ]; then
    guard_violation "G14" "lint baseline 只缩" \
      "$LINT_BASELINE_PATH 有 $n 条 > 冻结值 $LINT_BASELINE_MAX_ISSUES" \
      "⇒ baseline 只许缩（修掉存量后删条目），regen 把新问题冻进 baseline = 僵尸豁免"
  else
    guard_ok "G14" "lint baseline 只缩" "$n 条 ≤ 冻结值 $LINT_BASELINE_MAX_ISSUES"
  fi
}

# =============================================================================
# G15 声明面（INTERNET 权限 + 明文流量关闭）
# =============================================================================
g15() {
  guard_present "G15a" "INTERNET 权限" \
    'android\.permission\.INTERNET' "$APP_MANIFEST"
  guard_present "G15b" "禁明文流量" \
    'android:usesCleartextTraffic="false"' "$APP_MANIFEST"
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
  case "$rc" in
    0)
      # 允许：core-agent 的 loop/ 之内（主循环是唯一该驱动引擎的地方）
      strip_comment_hits < "$raw" \
        | grep -vE '^\./core-agent/src/main/java/com/rickeal/liquidharness/core/agent/loop/' \
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
# 主流程
# =============================================================================
echo "================ arch-guard（18 条） ================"
echo "root = $ROOT"
hr
g1; g2; g3; g4; g5; g6; g7; g8; g9
g10; g11; g12; g13; g14; g15; g16; g17; g18
hr
printf 'OK=%s  VIOLATION=%s  SELF_FAULT=%s\n' "$OK_COUNT" "$VIOLATION_COUNT" "$FAULT_COUNT"
if [ "${#FAILED_IDS[@]}" -gt 0 ]; then
  printf '违规项：%s\n' "${FAILED_IDS[*]}"
fi
if [ "${#FAULT_IDS[@]}" -gt 0 ]; then
  printf '守卫自身故障项：%s\n' "${FAULT_IDS[*]}"
fi

if [ "$VIOLATION_COUNT" -eq 0 ] && [ "$FAULT_COUNT" -eq 0 ]; then
  echo "[arch-guard] result=PASS ok=$OK_COUNT violation=0 fault=0"
  exit 0
fi
echo "[arch-guard] result=FAIL ok=$OK_COUNT violation=$VIOLATION_COUNT fault=$FAULT_COUNT"
exit 1
