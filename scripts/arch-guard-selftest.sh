#!/usr/bin/env bash
# =============================================================================
# arch-guard-selftest.sh — 架构守卫的「变异自证」（mutation self-proof）
#
# 为什么必须有这个脚本：
#   `arch-guard.sh` exit 0 只证明「它没报错」，不证明「它还会报错」。守卫最危险的
#   失效形态不是崩溃，而是**永远绿**：目录被改名、正则写坏、判据钉错位置，输出都是
#   一片 OK。旧仓 93 个 case 跑了 52 分钟，等于没人跑 —— 所以本脚本的第一设计目标
#   是**快到会被跑**，第二目标才是覆盖。
#
# 判据（每个 case 都要三条同时成立，缺一即 FAIL）：
#   ① 变异真的注入了（注入点不存在 ⇒ 变异未生效，判 FAIL，不判「跳过」）；
#   ② 目标守卫从 OK 变成 VIOLATION/SELF_FAULT（红）；
#   ③ 该守卫的**输出区段里出现本次注入的证据**（文件:行 或 注入的行内容）。
#      ⚠️ ③ 是必须的：只看「ID 变红」无法区分「红的正是这次变异」和「它本来就红」。
#      本仓在 8186db9 时点 G1/G6/G11/G16/G18 在真实树上就是红的（树正在被并行修），
#      没有 ③ 的话这些 case 会变成恒真的装饰。
#      Verified as of 2026-10-30（baeb574 之后）：真实树 23 条全绿，但这不改变 ③ 的
#      必要性 —— 守卫「今天红」只是巧合证据，「注入后必须红」才是判据。
#
# -----------------------------------------------------------------------------
# 与 docs/02-roadmap.md §5.2 纪律 5 的关系
# -----------------------------------------------------------------------------
#   「每条守卫必须有 selftest case，且 selftest 要包含变异自证」——本脚本就是它。
#   本脚本判的是「守卫会不会红」，**不判「树合不合规」**：后者由 arch-guard.sh 本体
#   判（CI 里两条串行跑）。所以真实树当前有存量违规时，本脚本照样应当全绿 —— 那
#   恰好证明守卫的灵敏度独立于树的现状。
#
# -----------------------------------------------------------------------------
# 两种执行模式
# -----------------------------------------------------------------------------
#   --batch（默认）  一次性把全部变异注入同一棵树，跑 **1 次** arch-guard，再逐条
#                    断言「该守卫的区段里有本次注入的证据」。变异之间互不干扰（每
#                    个变异落在不同文件/不同守卫的判据域），证据串按守卫区段隔离，
#                    归因强度与逐条跑等价，但耗时是 1/N。
#                    理由：本仓在 Windows/Git Bash 上一次 arch-guard 实测 50~220s
#                    （进程创建开销），23 条逐条跑 = 20 分钟起步 ⇒ 必然没人跑。
#   --per-case      每条变异一棵独立临时树 + 一次 arch-guard（23 次）。最严格，适合
#                    夜间/本地深度验证。CI 用默认 batch。
#
#   其他参数：
#     --only ID[,ID...]  只跑指定 case（本地调试用，例：--only G15b,G9）
#     --keep             保留临时树（排查证据串不匹配时用，会打印路径）
#     --list             列出全部 case 后退出
#
# -----------------------------------------------------------------------------
# 实现纪律
# -----------------------------------------------------------------------------
#   * set -uo pipefail：本仓已踩过「管道把 grep 的 rc=2 洗成 1」的坑，故**不**用 -e，
#     所有判定显式看返回值；管道一律先落盘再判。
#   * 副本用 tar 管道（排除 build/.gradle/.git/.kotlin），实测 0.6s/份；`cp -a` 在
#     Git Bash 上要 3s/份。
#   * arch-guard.sh 自己会 cd 到脚本上一级，所以副本里 `bash scripts/arch-guard.sh`
#     即可，不依赖调用方 cwd。
#   * 只用 bash + grep + awk + find + sed + tar，runner 上都有。
#
# 退出码：0 = 全部 case PASS；1 = 有 FAIL；2 = 环境/前置不满足（副本建不起来等）
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
GUARD="scripts/arch-guard.sh"
APP_MANIFEST="app/src/main/AndroidManifest.xml"
LINT_BASELINE="app/lint-baseline.xml"
AGENT_RUNNER="core-agent/src/main/java/com/rickeal/liquidharness/core/agent/AgentRunner.kt"

MODE="batch"
KEEP=0
ONLY=""
LIST_ONLY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --per-case) MODE="per-case"; shift ;;
    --batch)    MODE="batch"; shift ;;
    --only)     ONLY="${2:-}"; shift 2 ;;
    --only=*)   ONLY="${1#--only=}"; shift ;;
    --keep)     KEEP=1; shift ;;
    --list)     LIST_ONLY=1; shift ;;
    -h|--help)  sed -n '2,60p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "arch-guard-selftest: 未知参数 $1（--per-case / --batch / --only / --keep / --list）" >&2; exit 2 ;;
  esac
done

START_EPOCH="$(date +%s)"

# =============================================================================
# case 表：id|一句话说明|变异函数|期望证据（空 = 由变异函数动态写入 $EV_DIR/<id>）
#
# 覆盖口径 = arch-guard.sh 实际产出的**全部 23 个 ID**：
#   G1 G1b G2 G3 G4a G4b G5a G5b G6 G7 G8 G9 G10 G11 G12 G13 G14 G15a G15b G15c G16 G17 G18
#   （G1/G4/G5/G15 各拆了 a/b/c 子判据 —— 子判据是独立的断言，必须各自自证，
#     否则拆出来的那一半会变成没人看的分支。故「G 编号」数 ≠ 判据数。）
#   ⚠️ G15c 有**两条 case**（同一 ID，两种变异形态）：① 把 allowBackup 改成 true；
#      ② 删掉 dataExtractionRules 属性（W2 外审 §四.1 加的「排除面」那一半）。
#      判据编号仍是 23 个，case 数 24 —— 判据数与 case 数本来就不同源。
# =============================================================================
CASES=(
  "G1|网络栈收口：core-model 里 import okhttp3（只许在 core-net）|m_g1|__SelftestG1.kt:3:import okhttp3.OkHttpClient"
  "G1b|传输层不认识供应商：core-net 引用 core.engine|m_g1b|__SelftestG1b.kt:3:import com.rickeal.liquidharness.core.engine.LlmEngine"
  "G2|禁进程执行：core-model 里 Runtime.getRuntime()|m_g2|__SelftestG2.kt:3:val selftestProc = Runtime.getRuntime()"
  "G3|禁动态代码加载：core-model 里 DexClassLoader|m_g3|__SelftestG3.kt:3:val selftestLoader = DexClassLoader("
  "G4a|禁依赖黑名单(.kts)：core-model 加 retrofit 依赖|m_g4a|retrofit2:retrofit"
  "G4b|禁依赖黑名单(toml)：版本目录加 retrofit 条目|m_g4b|retrofit = \"2.11.0\""
  "G5a|禁 jitpack：settings 里加 jitpack 仓库|m_g5a|jitpack.io"
  "G5b|仓库收口声明：删掉 repositoriesMode.FAIL_ON_PROJECT_REPOS|m_g5b|缺少必需声明：repositoriesMode"
  "G6|模块边界：core-model 依赖 :core-net（禁止边）|m_g6|core-model/build.gradle.kts -> :core-net"
  "G7|设计系统零业务：core-design 引用 core.model|m_g7|__SelftestG7.kt:3:import com.rickeal.liquidharness.core.model.GenerationRequest"
  "G8|契约层纯 Kotlin：core-model 里 import android.content|m_g8|__SelftestG8.kt:3:import android.content.Context"
  "G9|模块数一致：settings 多一个 include(:ghost-module)|m_g9|settings include = 12"
  "G10|行数闸门：core-model 放一个 400 行文件（上限 300）|m_g10|__SelftestG10.kt = 400 行 > 单文件上限 300"
  "G11|方法体积：core-model 放一个 130 行方法（上限 120）|m_g11|__SelftestG11.kt:3  selftestBig() = 130 行"
  "G12|AgentRunner 门面：把 AgentRunner.kt 撑到 210 行（上限 200）|m_g12|"
  "G13|测试方法形态：@Test 方法带返回类型 Int|m_g13|JUnit4 必须 void"
  "G14|lint baseline 只缩：baseline 里再加一条 issue|m_g14|"
  "G15a|INTERNET 权限：从 manifest 删掉该权限|m_g15a|缺少必需声明：android\\.permission\\.INTERNET"
  "G15b|禁明文流量：从 manifest 删掉 usesCleartextTraffic=false|m_g15b|缺少必需声明：android:usesCleartextTraffic=\"false\""
  "G15c|禁备份：把 manifest 的 allowBackup=false 改成 true|m_g15c|缺少必需声明：android:allowBackup=\"false\"  （文件：app/src/main/AndroidManifest.xml）"
  "G15c|禁备份排除面：从 manifest 删掉 dataExtractionRules 属性|m_g15c_rules|缺少必需声明：android:dataExtractionRules=\"@xml/data_extraction_rules\""
  "G16|中文硬编码棘轮：core-model 里写死一句中文文案|m_g16|__SelftestG16.kt:3:val selftestCjk = \"自证中文字面量\""
  "G17|禁密钥字段：@Serializable 类里加 val apiKey|m_g17|__SelftestG17.kt:7  @Serializable 类内出现密钥字段: apiKey"
  "G18|引擎调用点收口：core-model 里调 engine.generate(|m_g18|__SelftestG18.kt:3:fun selftestDrive(engine: Any) { engine.generate(1) }"
)

if [ "$LIST_ONLY" -eq 1 ]; then
  printf '%s\n' "${CASES[@]}" | awk -F'|' '{printf "%-5s %s\n", $1, $2}'
  exit 0
fi

# =============================================================================
# 临时目录与清理
# =============================================================================
# 临时目录位置：默认系统临时目录。可用 SELFTEST_TMPDIR 指到别的盘
# （Windows/Git Bash 上系统临时目录在 C:，每次新建文件都要过 Defender，实测比同盘
#  慢一个数量级；CI 上无差别，不用设）。
WORK="$(mktemp -d "${SELFTEST_TMPDIR:-${TMPDIR:-/tmp}}/arch-guard-selftest.XXXXXX" 2>/dev/null \
        || mktemp -d 2>/dev/null)"
[ -d "$WORK" ] || { echo "::error::无法创建临时目录" >&2; exit 2; }
EV_DIR="$WORK/ev"
mkdir -p "$EV_DIR"

cleanup() {
  if [ "$KEEP" -eq 1 ]; then
    echo "[selftest] --keep：临时树保留在 $WORK"
  else
    rm -rf "$WORK" 2>/dev/null
  fi
}
trap cleanup EXIT

# =============================================================================
# 基础设施
# =============================================================================
PASS=0
FAIL=0
FAIL_LINES=()
NORMALIZED=()

pass_case() { PASS=$((PASS + 1)); printf 'PASS  %-5s %s\n' "$1" "$2"; }
fail_case() {
  FAIL=$((FAIL + 1))
  printf 'FAIL  %-5s %s\n' "$1" "$2"
  printf '            ⇒ %s\n' "$3"
  FAIL_LINES+=("$1")
}

# 复制源码树（排除生成目录）。tar 管道比 cp -a 快 5 倍（Git Bash 实测）。
copy_tree() { # $1 = 目标目录
  local dest="$1"
  ( cd "$REPO" && tar -cf - \
      --exclude=./.git --exclude=./.gradle --exclude=./.kotlin \
      --exclude=./build --exclude='./*/build' . 2>/dev/null ) \
    | ( cd "$dest" && tar -xf - ) 2>/dev/null
  [ -f "$dest/settings.gradle.kts" ] && [ -f "$dest/$GUARD" ]
}

# 跑一次守卫，把输出落到 $2，回显退出码
run_guard() { # $1 = 树 $2 = 输出文件
  ( cd "$1" && bash "$GUARD" ) > "$2" 2>&1
  echo $?
}

# 取某条守卫的**输出区段**（从 `[ID] ...` 头行到下一个守卫头行之前）。
# 只在这个区段里找证据 ⇒ 证据不可能来自别的守卫的报错。
guard_section() { # $1 = id $2 = 输出文件
  awk -v id="[$1]" '
    index($0, id) == 1 { f=1; next }
    /^\[[A-Za-z0-9]+\]/ { if (f) exit }
    f' "$2"
}

guard_state() { # $1 = id $2 = 输出文件 → OK / RED / MISSING
  awk -v id="[$1]" '
    index($0, id) == 1 {
      if ($0 ~ /VIOLATION|SELF_FAULT/) print "RED"; else print "OK"; exit
    }' "$2"
}

# ---------------------------------------------------------------------------
# 变异原语
# ---------------------------------------------------------------------------
mk_file() { # $1 = 树根 $2 = 相对路径，内容走 stdin
  local abs="$1/$2"
  mkdir -p "$(dirname "$abs")" || return 1
  cat > "$abs" || return 1
}

append_line() { # $1 = 树 $2 = 相对路径 $3 = 行
  printf '%s\n' "$3" >> "$1/$2"
}

# 删掉匹配行；返回 1 = 一行都没删（= 注入点不存在，多为「基线本来就红」）
# 不落中间文件：Windows 上某些环境的安全钩子会拦 rm（"embedded drive prefix"），
# 中间文件留在树里会被守卫扫到，宁可用变量中转。
drop_line() { # $1 = 树 $2 = 相对路径 $3 = ERE
  local abs="$1/$2" n content
  [ -f "$abs" ] || return 1
  n="$(grep -cE "$3" "$abs" 2>/dev/null | tr -d ' ')"
  [ -n "$n" ] || n=0
  [ "$n" -gt 0 ] || return 1
  content="$(grep -vE "$3" "$abs" 2>/dev/null)" || return 1
  printf '%s\n' "$content" > "$abs" || return 1
  return 0
}

ev() { printf '%s' "$2" > "$EV_DIR/$1"; }

# =============================================================================
# 变异实现（每个函数：$1 = 树根；返回非 0 = 变异没注入成功）
# =============================================================================
m_g1() {
  mk_file "$1" "core-model/src/main/java/com/rickeal/liquidharness/core/model/__SelftestG1.kt" <<'EOF'
package com.rickeal.liquidharness.core.model

import okhttp3.OkHttpClient

internal class SelftestG1(val client: OkHttpClient)
EOF
}

m_g1b() {
  mk_file "$1" "core-net/src/main/java/com/rickeal/liquidharness/core/net/__SelftestG1b.kt" <<'EOF'
package com.rickeal.liquidharness.core.net

import com.rickeal.liquidharness.core.engine.LlmEngine

internal class SelftestG1b(val engine: LlmEngine?)
EOF
}

m_g2() {
  mk_file "$1" "core-model/src/main/java/com/rickeal/liquidharness/core/model/__SelftestG2.kt" <<'EOF'
package com.rickeal.liquidharness.core.model

val selftestProc = Runtime.getRuntime()
EOF
}

m_g3() {
  mk_file "$1" "core-model/src/main/java/com/rickeal/liquidharness/core/model/__SelftestG3.kt" <<'EOF'
package com.rickeal.liquidharness.core.model

val selftestLoader = DexClassLoader("/tmp/x.apk", null, null, null)
EOF
}

m_g4a() {
  append_line "$1" "core-model/build.gradle.kts" \
    'implementation("com.squareup.retrofit2:retrofit:2.11.0")'
}

m_g4b() {
  append_line "$1" "gradle/libs.versions.toml" 'retrofit = "2.11.0"'
}

m_g5a() {
  append_line "$1" "settings.gradle.kts" 'maven { url = uri("https://jitpack.io") }'
}

m_g5b() {
  drop_line "$1" "settings.gradle.kts" 'repositoriesMode\.set\(RepositoriesMode\.FAIL_ON_PROJECT_REPOS\)'
}

m_g6() {
  append_line "$1" "core-model/build.gradle.kts" 'implementation(project(":core-net"))'
}

m_g7() {
  mk_file "$1" "core-design/src/main/java/com/rickeal/liquidharness/core/design/__SelftestG7.kt" <<'EOF'
package com.rickeal.liquidharness.core.design

import com.rickeal.liquidharness.core.model.GenerationRequest

internal fun selftestG7(request: GenerationRequest?) = request?.toString()
EOF
}

m_g8() {
  mk_file "$1" "core-model/src/main/java/com/rickeal/liquidharness/core/model/__SelftestG8.kt" <<'EOF'
package com.rickeal.liquidharness.core.model

import android.content.Context

internal fun selftestG8(context: Context?) = context?.packageName
EOF
}

m_g9() {
  append_line "$1" "settings.gradle.kts" 'include(":ghost-module")'
}

m_g10() {
  local f="core-model/src/main/java/com/rickeal/liquidharness/core/model/__SelftestG10.kt"
  local abs="$1/$f" i
  mkdir -p "$(dirname "$abs")" || return 1
  {
    printf 'package com.rickeal.liquidharness.core.model\n'
    i=1
    while [ "$i" -le 399 ]; do
      printf 'internal val selftestPad%03d = %d\n' "$i" "$i"
      i=$((i + 1))
    done
  } > "$abs" || return 1
  [ "$(wc -l < "$abs" | tr -d ' ')" = "400" ] || return 1
  ev G10 "__SelftestG10.kt = 400 行 > 单文件上限 300"
}

m_g11() {
  local f="core-model/src/main/java/com/rickeal/liquidharness/core/model/__SelftestG11.kt"
  local abs="$1/$f" i
  mkdir -p "$(dirname "$abs")" || return 1
  {
    printf 'package com.rickeal.liquidharness.core.model\n\n'
    printf 'fun selftestBig(): Int {\n'
    printf '    var acc = 0\n'
    i=1
    while [ "$i" -le 126 ]; do
      printf '    acc += %d\n' "$i"
      i=$((i + 1))
    done
    printf '    return acc\n'
    printf '}\n'
  } > "$abs" || return 1
  # 期望：fun 在第 3 行，收尾 `}` 在第 132 行 ⇒ 132 - 3 + 1 = 130 行
  [ "$(wc -l < "$abs" | tr -d ' ')" = "132" ] || return 1
  ev G11 "__SelftestG11.kt:3  selftestBig() = 130 行"
}

m_g12() {
  local abs="$1/$AGENT_RUNNER" n pad i
  [ -f "$abs" ] || return 1
  n="$(wc -l < "$abs" | tr -d ' ')"
  pad=$((210 - n))
  [ "$pad" -gt 0 ] || return 1          # 已超 210 ⇒ 基线本来就红，无法归因
  i=1
  while [ "$i" -le "$pad" ]; do
    printf '// selftest pad %d\n' "$i" >> "$abs"
    i=$((i + 1))
  done
  ev G12 "AgentRunner.kt = 210 行 > 上限 200"
}

m_g13() {
  mk_file "$1" "core-model/src/test/java/com/rickeal/liquidharness/core/model/__SelftestG13Test.kt" <<'EOF'
package com.rickeal.liquidharness.core.model

import org.junit.Test

class SelftestG13Test {
    @Test
    fun selftestReturnsInt(): Int {
        return 1
    }
}
EOF
}

m_g14() {
  local abs="$1/$LINT_BASELINE" n content
  [ -f "$abs" ] || return 1
  n="$(grep -c '<issue' "$abs" 2>/dev/null | tr -d ' ')"
  [ -n "$n" ] || n=0
  content="$(awk '{ if (!done && $0 ~ /<\/issues>/) { print "    <issue id=\"SelftestInjected\" severity=\"Error\" />"; done=1 } print }' "$abs")" || return 1
  printf '%s\n' "$content" > "$abs" || return 1
  ev G14 "有 $((n + 1)) 条 > 冻结值 1"
}

m_g15a() {
  drop_line "$1" "$APP_MANIFEST" 'android\.permission\.INTERNET'
}

m_g15b() {
  drop_line "$1" "$APP_MANIFEST" 'android:usesCleartextTraffic="false"'
}

# G15c 的变异必须是**改值**而不是删行：allowBackup 的默认值就是 true，
# 「删掉这一行」与「写成 true」对系统是同一个后果，但对 grep 型守卫是两种形态。
# 这里选「改 true」—— 它同时覆盖了「守卫是否钉的是 false 这个值」，
# 而「删行」已被 G15a/G15b 两个 case 证过同类形态。
m_g15c() {
  local abs="$1/$APP_MANIFEST" content
  [ -f "$abs" ] || return 1
  grep -q 'android:allowBackup="false"' "$abs" 2>/dev/null || return 1
  content="$(sed 's/android:allowBackup="false"/android:allowBackup="true"/' "$abs")" || return 1
  printf '%s\n' "$content" > "$abs" || return 1
  # 变异必须真的生效（三条判据的第 ① 条），否则红/绿都无从归因
  grep -q 'android:allowBackup="true"' "$abs" 2>/dev/null || return 1
  return 0
}

# G15c 的第二形态（W2 外审 §四.1）：**删掉 dataExtractionRules 属性**。
# 与 m_g15c 分开的理由：两种变异改的是不同判据段（开关 vs 排除面接线），
# 用同一个函数做不了「只删属性、保留 allowBackup=false」的隔离。
# 删行而不是改值：该属性的正确形态只有一种（指向本仓的规则文件），
# 改值（指向别的 xml）与删除对守卫是同一后果 —— 都算「声明落空」，
# 而删行是更常见的真实形态（有人重写 <application> 时漏掉）。
m_g15c_rules() {
  drop_line "$1" "$APP_MANIFEST" 'android:dataExtractionRules="@xml/data_extraction_rules"'
}

m_g16() {
  mk_file "$1" "core-model/src/main/java/com/rickeal/liquidharness/core/model/__SelftestG16.kt" <<'EOF'
package com.rickeal.liquidharness.core.model

val selftestCjk = "自证中文字面量"
EOF
}

m_g17() {
  mk_file "$1" "core-model/src/main/java/com/rickeal/liquidharness/core/model/__SelftestG17.kt" <<'EOF'
package com.rickeal.liquidharness.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SelftestG17(val ok: Int) {
    val apiKey: String = ""
}
EOF
}

m_g18() {
  mk_file "$1" "core-model/src/main/java/com/rickeal/liquidharness/core/model/__SelftestG18.kt" <<'EOF'
package com.rickeal.liquidharness.core.model

fun selftestDrive(engine: Any) { engine.generate(1) }
EOF
}

# =============================================================================
# fixture 归一化
# =============================================================================
# G15b 是「必须存在」型断言，它的变异只能是**删掉**该属性 ⇒ 只有基线本来有该属性
# 时才能归因。本仓真实 manifest 目前**缺** usesCleartextTraffic（arch-guard 对真实
# 树判红，属真实缺陷，不在本脚本的职责范围）。为了让这条 case 有可归因的基线，副本
# 里先补上该属性 —— 这是**测试夹具**的合法准备，不是给真实树开后门：
#   · 只改临时副本，真实树一行不动；
#   · 补完立刻校验（补不上就 FAIL，不静默跳过）；
#   · 补过的 case 在末尾 summary 里显式列出（fixture-normalized）。
normalize_fixture() { # $1 = 树
  local m="$1/$APP_MANIFEST" content
  [ -f "$m" ] || return 1
  if grep -q 'android:usesCleartextTraffic="false"' "$m" 2>/dev/null; then
    return 0
  fi
  content="$(awk '
    !done && /<application([[:space:]>]|$)/ {
      sub(/<application/, "<application\n        android:usesCleartextTraffic=\"false\"")
      done=1
    }
    { print }' "$m")" || return 1
  printf '%s\n' "$content" > "$m" || return 1
  grep -q 'android:usesCleartextTraffic="false"' "$m" 2>/dev/null || return 1
  case " ${NORMALIZED[*]:-} " in
    *" G15b "*) : ;;
    *) NORMALIZED+=("G15b") ;;
  esac
  return 0
}

# =============================================================================
# 前置断言：副本结构不完整时，守卫会 SELF_FAULT/空转，自证全部失去意义
# =============================================================================
preflight() { # $1 = 树
  local t="$1" m miss=()
  for m in core-model core-net core-engine core-agent core-skills core-mcp \
           core-data core-design feature-chat feature-settings app; do
    [ -f "$t/$m/build.gradle.kts" ] || miss+=("$m/build.gradle.kts")
  done
  for m in settings.gradle.kts gradle/libs.versions.toml "$APP_MANIFEST" "$LINT_BASELINE" "$AGENT_RUNNER"; do
    [ -f "$t/$m" ] || miss+=("$m")
  done
  [ "$(find "$t" -path '*/src/test/*' -name '*.kt' 2>/dev/null | wc -l | tr -d ' ')" -gt 0 ] \
    || miss+=("src/test 下没有 .kt")
  if [ "${#miss[@]}" -gt 0 ]; then
    echo "::error::副本结构不完整，缺：${miss[*]}"
    return 1
  fi
  return 0
}

# =============================================================================
# 单条断言
# =============================================================================
assert_case() { # $1=id $2=desc $3=静态证据 $4=输出文件
  local id="$1" desc="$2" evid="$3" out="$4"
  local state sec dyn
  dyn="$(cat "$EV_DIR/$id" 2>/dev/null || true)"
  [ -n "$dyn" ] && evid="$dyn"

  state="$(guard_state "$id" "$out")"
  if [ "$state" != "RED" ]; then
    fail_case "$id" "$desc" "注入后该守卫仍未判红（state=${state:-MISSING}）—— 守卫对这类违规不敏感"
    return
  fi
  if [ -z "$evid" ]; then
    fail_case "$id" "$desc" "没有登记期望证据串（case 表或变异函数漏写）"
    return
  fi
  sec="$(guard_section "$id" "$out")"
  if printf '%s\n' "$sec" | grep -qF -- "$evid"; then
    pass_case "$id" "$desc"
  else
    fail_case "$id" "$desc" "判红了，但输出区段里没有本次注入的证据『$evid』—— 红的原因不是这次变异"
    # 把守卫自己的输出贴出来（截断 12 行）：证据串不匹配时，缺的是「守卫没看」
    # 还是「守卫看的形态与注入形态不一致」，只能靠这段原文判断。
    printf '%s\n' "$sec" | head -n 12 | sed 's/^/              | /'
  fi
}

# =============================================================================
# 主流程
# =============================================================================
echo "================ arch-guard-selftest（${#CASES[@]} 个变异自证 case） ================"
echo "repo = $REPO"
echo "mode = $MODE   work = $WORK"
echo "-------------------------------------------------------------------"

case_selected() { # $1 = id
  [ -z "$ONLY" ] && return 0
  case ",$ONLY," in
    *",$1,"*) return 0 ;;
    *) return 1 ;;
  esac
}

if [ "$MODE" = "batch" ]; then
  T="$WORK/tree"
  mkdir -p "$T" || { echo "::error::无法创建 $T" >&2; exit 2; }
  if ! copy_tree "$T"; then
    echo "::error::源码树复制失败（tar 管道）—— 自证无法进行" >&2
    exit 2
  fi
  if ! preflight "$T"; then exit 2; fi
  if ! normalize_fixture "$T"; then
    echo "::error::fixture 归一化失败（$APP_MANIFEST 里补不上 usesCleartextTraffic）" >&2
    exit 2
  fi

  echo "[selftest] 注入变异（batch：全部落在同一棵树，各变异落在不同判据域）"
  MUT_OK=()   # 与 CASES 同序：ok / mut / skip
  i=0
  for entry in "${CASES[@]}"; do
    id="${entry%%|*}"; rest="${entry#*|}"
    desc="${rest%%|*}"; rest="${rest#*|}"
    fn="${rest%%|*}"
    if case_selected "$id"; then
      if "$fn" "$T"; then
        MUT_OK+=("ok")
      else
        MUT_OK+=("mut")
        fail_case "$id" "$desc" "变异未能注入（注入点不存在 ⇒ 基线本来就红，无法归因）"
      fi
    else
      MUT_OK+=("skip")
    fi
    i=$((i + 1))
  done

  OUT="$WORK/guard.out"
  rc="$(run_guard "$T" "$OUT")"
  echo "[selftest] arch-guard 退出码 = $rc（预期非 0：注入了 20+ 处违规）"
  if [ ! -s "$OUT" ]; then
    echo "::error::arch-guard 没有产生任何输出" >&2
    exit 2
  fi

  i=0
  for entry in "${CASES[@]}"; do
    id="${entry%%|*}"; rest="${entry#*|}"
    desc="${rest%%|*}"; rest="${rest#*|}"
    fn="${rest%%|*}"; evid="${rest#*|}"
    if case_selected "$id" && [ "${MUT_OK[$i]:-}" = "ok" ]; then
      assert_case "$id" "$desc" "$evid" "$OUT"
    fi
    i=$((i + 1))
  done
else
  # ---- 逐条模式：一条变异一棵树，一次 arch-guard ----
  i=0
  for entry in "${CASES[@]}"; do
    id="${entry%%|*}"; rest="${entry#*|}"
    desc="${rest%%|*}"; rest="${rest#*|}"
    fn="${rest%%|*}"; evid="${rest#*|}"
    case_selected "$id" || { i=$((i + 1)); continue; }
    T="$WORK/tree-$id"
    mkdir -p "$T" || { fail_case "$id" "$desc" "无法创建临时树"; i=$((i + 1)); continue; }
    if ! copy_tree "$T" || ! preflight "$T" || ! normalize_fixture "$T"; then
      fail_case "$id" "$desc" "临时树准备失败（复制/前置/归一化）"
      i=$((i + 1)); continue
    fi
    if ! "$fn" "$T"; then
      fail_case "$id" "$desc" "变异未能注入（注入点不存在 ⇒ 基线本来就红，无法归因）"
      i=$((i + 1)); continue
    fi
    OUT="$WORK/guard-$id.out"
    run_guard "$T" "$OUT" > /dev/null
    assert_case "$id" "$desc" "$evid" "$OUT"
    [ "$KEEP" -eq 1 ] || rm -rf "$T" 2>/dev/null
    i=$((i + 1))
  done
fi

# =============================================================================
# 汇总
# =============================================================================
TOTAL=$((PASS + FAIL))
echo "-------------------------------------------------------------------"
if [ "${#NORMALIZED[@]}" -gt 0 ]; then
  echo "[selftest] fixture-normalized：${NORMALIZED[*]}（真实树缺该声明，副本里补上后才可归因；arch-guard 对真实树仍判红）"
fi
ELAPSED=$(( $(date +%s) - START_EPOCH ))
if [ "$FAIL" -eq 0 ]; then
  echo "selftest: $PASS/$TOTAL PASS  （${ELAPSED}s）"
  exit 0
fi
echo "selftest: $PASS/$TOTAL PASS  （${ELAPSED}s）  FAIL: ${FAIL_LINES[*]}"
exit 1
