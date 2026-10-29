#!/usr/bin/env bash
# =============================================================================
# fulltest.sh — 全量 JVM 单测入口（固定带 --continue，禁用 Gradle fail-fast 残缺口径）
#
# 用法：
#   ./scripts/fulltest.sh                  # 跑聚合 task `test`（覆盖 settings 里全部模块）
#   ./scripts/fulltest.sh --task test      # 显式指定 task
#   ./scripts/fulltest.sh <gradle-task>    # 透传任意 task
#   ./scripts/fulltest.sh --summary-only   # 只汇总现有 TEST-*.xml（不重跑、不判失败、恒 exit 0）
#
# 为什么必须有这个脚本（裸 `gradle test` 的四个缺口，每一个都是实测过的假绿）：
#
#   ① 【fail-fast 残缺口径】Gradle 默认第一个失败模块就中止后续模块，于是只报
#      「N tests completed」—— 没跑到的模块一个用例都不计入。看起来「只挂 1 个」，
#      实际有模块根本没跑。故固定 `--continue`。
#
#   ② 【陈旧 XML 假全量】task UP-TO-DATE 时 gradle 不重写结果目录，直接汇总
#      build/test-results 会拿上一轮的 XML 判绿。故先 rm 结果目录，并在末尾断言
#      「最早 TEST-*.xml 的 mtime 不早于本轮启动时刻」。
#
#   ③ 【rm 清不掉】Windows 句柄锁 / 只读 / 权限会让 rm 失败，此时汇总口径不可信。
#      故 rm 失败**收集目录名并判红**，绝不静默吞掉（宁假红不假绿）。
#
#   ④ 【tests=0 判绿】XML 存在但用例数为 0（task 不含测试 / 全部被过滤 / 结果目录
#      是空壳）时，裸命令照样绿 —— 「空跑」被当成「通过」是最坏的假绿。故显式判红。
#
# 判定口径：**不信** gradle 打印的「N tests completed」，改为汇总
# `**/build/test-results/**/TEST-*.xml` 里 testsuite 的 tests / failures / errors /
# skipped 属性（真实用例数）。任一 failures>0 或 errors>0 即判红；gradle 自身非零
# 退出（编译失败 / 配置失败）同样判红。
#
# 退出码：0 = 全绿；1 = 有用例失败 / 口径不可信 / tests=0；2 = 环境缺失；
#         其他 = gradle 原样透传。
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO" || { echo "::error::无法进入仓库根 $REPO" >&2; exit 2; }

TASK="test"
SUMMARY_ONLY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --task)         TASK="${2:-test}"; shift 2 ;;
    --task=*)       TASK="${1#--task=}"; shift ;;
    --summary-only) SUMMARY_ONLY=1; shift ;;
    -h|--help)      sed -n '2,40p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)              TASK="$1"; shift ;;
  esac
done

# ---------------------------------------------------------------------------
# 期望模块数：从 settings 文件**动态**读 include 条目，不写死数字。
# 写死数字是旧仓最典型的陈旧注释来源（每加一个模块就错一次，且错了没人发现）。
# 该值只用于「模块数对不上」的 ::warning::（不改退出码）—— 因为一个模块完全可能
# 合法地没有测试源集（NO-SOURCE），那不该判红。
# ---------------------------------------------------------------------------
expected_mods=0
settings_file=""
for f in settings.gradle.kts settings.gradle; do
  [ -f "$f" ] && { settings_file="$f"; break; }
done
if [ -n "$settings_file" ]; then
  expected_mods="$(grep -hE '^[[:space:]]*include[[:space:]]*\(' "$settings_file" 2>/dev/null \
    | grep -oE '"[^"]+"' | wc -l | tr -d ' ')"
fi

# ---- 跑测试（仅默认模式）----
rm_failed=()
RUN_START_EPOCH=0

if [ "$SUMMARY_ONLY" -eq 0 ]; then
  # 环境前置断言：没有 JDK 也没有 gradle 时，报错要指向**怎么注入环境**，
  # 而不是让人对着 gradle 的 "JAVA_HOME is not set" 猜。
  if [ -z "${JAVA_HOME:-}" ] && ! command -v gradle >/dev/null 2>&1 && [ ! -f ./gradlew ]; then
    echo "::error::既没有 JAVA_HOME / gradle / ./gradlew —— 无法运行单测。"
    echo "[fulltest] 先注入本仓环境："
    echo "[fulltest]   eval \"\$(bash scripts/localbuild.sh --env)\""
    echo "[fulltest]   bash scripts/fulltest.sh"
    exit 2
  fi

  # 解析启动器：仓库 wrapper 优先，其次 PATH 上的 gradle。
  # ⚠️ CI 上 gradlew 可能没有可执行位（Git 模式 100644）⇒ 用 sh 显式解释，
  #    避免 "Permission denied" 被误读成环境问题。
  if [ -x ./gradlew ]; then
    GRADLE_CMD=(./gradlew)
  elif [ -f ./gradlew ]; then
    GRADLE_CMD=(sh ./gradlew)
  elif command -v gradle >/dev/null 2>&1; then
    GRADLE_CMD=(gradle)
  else
    echo "::error::既找不到 ./gradlew 也找不到 gradle —— 无法运行单测" >&2
    exit 2
  fi

  RUN_START_EPOCH="$(date +%s)"   # 必须在 rm 循环之前取
  echo "[fulltest] launcher=${GRADLE_CMD[*]}  task=$TASK  （固定带 --continue，禁用 fail-fast 残缺口径）"
  echo "[fulltest] 期望模块数（settings include 条目）= ${expected_mods}"

  # 清掉上一轮结果，避免陈旧 XML 污染汇总。
  # ⚠️ 必须用进程替换 `< <(find …)` 而不是管道 `| while`：管道的 while 跑在子壳里，
  #    rm_failed 的累积不会回传父壳。
  while IFS= read -r d; do
    [ -z "$d" ] && continue
    rm -rf "$d" 2>/dev/null || rm_failed+=("$d")
  done < <(find . -path ./.git -prune -o -type d -name 'test-results' -print 2>/dev/null)

  # 注意 `--continue` 必须在 task 之后（Gradle 的 task 名后接选项）。
  "${GRADLE_CMD[@]}" --no-daemon --stacktrace "$TASK" --continue
  gradle_rc=$?
else
  gradle_rc=0
  echo "[fulltest] --summary-only：仅汇总现有 TEST-*.xml（不重跑测试、不判失败）"
fi

# ---------------------------------------------------------------------------
# 汇总（默认模式与 --summary-only 共用同一段，杜绝两份实现漂移）
# 结果经全局变量回传：g_total / g_failures / g_errors / g_skipped / g_files / g_mtime_min
# ---------------------------------------------------------------------------
fmt_ts() {
  date -d "@$1" '+%Y-%m-%d %H:%M:%S' 2>/dev/null \
    || date -r "$1" '+%Y-%m-%d %H:%M:%S' 2>/dev/null \
    || echo "epoch $1"
}

summarize_tests() {
  g_total=0; g_failures=0; g_errors=0; g_skipped=0; g_files=0
  g_mtime_min=""
  local -A M_TOTAL=() M_FAIL=() M_ERR=()
  local -a MTIMES=()
  local xml head_tag t f e s mod m nmod mt

  while IFS= read -r xml; do
    [ -z "$xml" ] && continue
    g_files=$((g_files + 1))
    mt="$(stat -c %Y "$xml" 2>/dev/null || stat -f %m "$xml" 2>/dev/null || true)"
    [ -n "$mt" ] && MTIMES+=("$mt")
    head_tag="$(grep -m1 -oE '<testsuite[^>]*>' "$xml" 2>/dev/null || true)"
    t="$(printf '%s' "$head_tag" | grep -oE 'tests="[0-9]+"'    | head -1 | tr -cd '0-9')"
    f="$(printf '%s' "$head_tag" | grep -oE 'failures="[0-9]+"' | head -1 | tr -cd '0-9')"
    e="$(printf '%s' "$head_tag" | grep -oE 'errors="[0-9]+"'   | head -1 | tr -cd '0-9')"
    s="$(printf '%s' "$head_tag" | grep -oE 'skipped="[0-9]+"'  | head -1 | tr -cd '0-9')"
    g_total=$((g_total + ${t:-0}))
    g_failures=$((g_failures + ${f:-0}))
    g_errors=$((g_errors + ${e:-0}))
    g_skipped=$((g_skipped + ${s:-0}))
    mod="$(printf '%s' "$xml" | sed -E 's#^\./##; s#/.*##')"
    M_TOTAL[$mod]=$(( ${M_TOTAL[$mod]:-0} + ${t:-0} ))
    M_FAIL[$mod]=$((  ${M_FAIL[$mod]:-0}  + ${f:-0} ))
    M_ERR[$mod]=$((   ${M_ERR[$mod]:-0}   + ${e:-0} ))
  done < <(find . -path ./.git -prune -o -type f -name 'TEST-*.xml' -print 2>/dev/null)

  echo "-------------------------------------------------------------------"

  # 缺口③：rm 失败 ⇒ 汇总口径不可信，明确报错并拒绝判绿。
  # 这里连 ::warning:: 都不打而直接 ::error:: —— Windows 句柄锁下 rm 失败但 gradle
  # 恰好覆盖写成功时 mtime 是新的，两道防线叠加才够。
  if [ "$SUMMARY_ONLY" -eq 0 ] && [ "${#rm_failed[@]}" -gt 0 ]; then
    echo "[fulltest] ⚠️ 以下 test-results 目录未能清除（Windows 句柄锁 / 只读 / 权限？）："
    printf '[fulltest]   - %s\n' "${rm_failed[@]}"
    echo "[fulltest] 处理办法：关掉占用这些目录的进程（IDE / 杀毒 / 上一轮残留的"
    echo "[fulltest]   GradleDaemon），或直接删除这些目录后重跑。"
    echo "::error::有 ${#rm_failed[@]} 个 test-results 目录未能清除 —— 本轮汇总可能混入陈旧 XML，禁止判绿"
    exit 1
  fi

  if [ "$g_files" -eq 0 ]; then
    echo "[fulltest] 未找到任何 TEST-*.xml —— 用例数无法汇总。"
    echo "::warning::未找到任何 TEST-*.xml —— 用例数无法汇总（可能是测试根本没跑）"
    if [ "$SUMMARY_ONLY" -eq 1 ]; then
      echo "[fulltest] （--summary-only 未执行 gradle，仅汇总现有 XML；请先跑一次默认模式产生结果）"
    else
      echo "[fulltest] gradle 退出码 = ${gradle_rc:-n/a}（多为编译/配置失败，未产生测试结果）"
    fi
  else
    echo "[fulltest] 汇总 $g_files 个 TEST-*.xml（真实用例数口径，非 gradle 的「N tests completed」）："
    echo "[fulltest] tests=$g_total failures=$g_failures errors=$g_errors skipped=$g_skipped"
    if [ "${#M_TOTAL[@]}" -gt 0 ]; then
      for m in $(printf '%s\n' "${!M_TOTAL[@]}" | LC_ALL=C sort); do
        echo "[fulltest]   - ${m}: tests=${M_TOTAL[$m]} failures=${M_FAIL[$m]} errors=${M_ERR[$m]}"
      done
    fi
    if [ "${#MTIMES[@]}" -gt 0 ]; then
      local mt_min mt_max
      mt_min="$(printf '%s\n' "${MTIMES[@]}" | sort -n | head -1)"
      mt_max="$(printf '%s\n' "${MTIMES[@]}" | sort -n | tail -1)"
      g_mtime_min="$mt_min"
      echo "[fulltest] TEST-*.xml mtime 范围：最早 $(fmt_ts "$mt_min")  最晚 $(fmt_ts "$mt_max")"
    fi
    nmod="${#M_TOTAL[@]}"
    echo "[fulltest] 覆盖模块数：$nmod（settings include 条目 = ${expected_mods}）"
    if [ "$SUMMARY_ONLY" -eq 0 ] && [ "$expected_mods" -gt 0 ] && [ "$nmod" -ne "$expected_mods" ]; then
      # 只 warning 不判红：NO-SOURCE 模块（无测试源集）合法地没有 XML。
      echo "::warning::汇总到的模块数 = $nmod，与 settings 的 include 数 $expected_mods 不一致 —— 可能有模块未跑 / 结果目录缺失 / 该模块本就没有测试源集，请人工核对"
    fi
  fi
  echo "-------------------------------------------------------------------"
}

summarize_tests

# --summary-only：纯报告模式，恒 exit 0（不 gate；供 CI 非阻断 summary 步骤复用）
if [ "$SUMMARY_ONLY" -eq 1 ]; then
  exit 0
fi

if [ "$gradle_rc" -ne 0 ]; then
  echo "[fulltest] 失败：gradle 退出码 $gradle_rc（编译/配置/用例失败，详见上方输出）"
  echo "[fulltest] 提示：先 grep -A20 '^\\* What went wrong' 输出 —— 真实失败点在那一行；"
  echo "[fulltest]       （别用不带锚点的 grep：本脚本自身也打印过那句提示，会先命中它）"
  echo "[fulltest]       栈顶往往是插件初始化噪音。若失败项是既有基线（非本波引入），"
  echo "[fulltest]       请与同分支上一个绿 run 的同一 task 对比后再判断。"
  exit "$gradle_rc"
fi
if [ "$g_failures" -gt 0 ] || [ "$g_errors" -gt 0 ]; then
  echo "[fulltest] 失败：failures=$g_failures errors=$g_errors（真实用例失败数）"
  exit 1
fi
if [ "$g_files" -eq 0 ]; then
  echo "[fulltest] 失败：gradle 成功但没有任何 TEST-*.xml（task '$TASK' 可能不含测试，或结果目录被改）"
  exit 1
fi
# 缺口④：tests=0 判红 —— 空跑不等于通过。
if [ "$g_total" -eq 0 ]; then
  echo "[fulltest] 失败：汇总到 tests=0 —— 有 $g_files 个 TEST-*.xml 但没有任何用例被执行"
  echo "[fulltest]       （task 不含测试 / 用例被全部过滤 / 结果目录是空壳）⇒ 空跑不等于通过"
  echo "::error::tests=0 —— 单测门禁未行使，禁止判绿"
  exit 1
fi
# 缺口②：陈旧 XML 断言。汇总里最早的 TEST-*.xml 若早于本轮启动时刻，说明这批结果
# 不是本轮产物 —— 唯一成因是 rm 没清干净而 gradle 又没重跑（UP-TO-DATE）。
# 严格小于（不取容差）：XML 由 gradle 在启动之后写盘，同秒写盘 = 相等 ⇒ 绿（已实测）。
# 若将来遇到 2s 粒度文件系统（FAT/exFAT）误红，再引入容差而不是放宽成「忽略」。
if [ -n "${g_mtime_min:-}" ] && [ "$g_mtime_min" -lt "$RUN_START_EPOCH" ]; then
  echo "[fulltest] 失败：最早 TEST-*.xml mtime（$(fmt_ts "$g_mtime_min")）早于本轮启动时刻（$(fmt_ts "$RUN_START_EPOCH")）"
  echo "[fulltest]       汇总含非本轮产物（rm 未清干净 / gradle 未重跑 UP-TO-DATE）"
  echo "::error::TEST-*.xml 最早 mtime 早于本轮启动时刻 ⇒ 汇总口径混入陈旧结果，禁止判绿"
  exit 1
fi

echo "[fulltest] 通过：tests=$g_total failures=0 errors=0 skipped=$g_skipped（全部用例绿）"
exit 0
