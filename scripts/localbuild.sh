#!/usr/bin/env bash
# =============================================================================
# localbuild.sh — LiquidAgent harness-v2 本地 Android 编译入口
#
# 用法：
#   ./scripts/localbuild.sh                        # 默认跑 :app:assembleDebug
#   ./scripts/localbuild.sh :app:assembleRelease   # 任意 gradle task
#   ./scripts/localbuild.sh test --continue        # 参数原样透传给 gradle
#   ./scripts/localbuild.sh --env                  # 只打印 export 行（供 eval 注入）
#   ./scripts/localbuild.sh --shell                # 进入已配好环境的 bash
#   ./scripts/localbuild.sh --help
#
# 零系统污染承诺：不写 C 盘、不改系统 PATH / 注册表 / 用户环境变量。全部通过
# **per-invocation** 环境变量注入（JAVA_HOME / ANDROID_HOME / ANDROID_SDK_ROOT /
# GRADLE_USER_HOME），退出即失效。
#
# 环境来源（工作区自带，路径可用 LOCALBUILD_J2ENV 覆盖）：
#   _j2env/jdk/*/       Temurin JDK 21（cacerts 已导入沙箱 MITM CA）
#   _j2env/sdk/         Android SDK（cmdline-tools / platforms / build-tools）
#   _j2env/gradle-home/ GRADLE_USER_HOME（Gradle 发行版 + 依赖缓存 + 阿里云镜像 init）
#
# 三个已知坑（都已在下面处理，别"优化"掉）：
#
#   1. 【JVM 不读 http_proxy 环境变量】只设 http_proxy/https_proxy 对 Gradle 无效，
#      wrapper 直连会报 `PKIX path building failed`（沙箱把域名用本地 CA 重签了）。
#      必须把代理显式传给 JVM：-Dhttp.proxyHost / -Dhttp.proxyPort（https 同理）。
#
#   2. 【代理端口会漂移】会话重连后沙箱代理端口会变（曾 9079 → 1799 → 13516）。
#      所以端口**运行时**从 $https_proxy 解析，绝不写死；解析不出时回落 1799。
#
#   3. 【不要写 local.properties】AGP 在没有 local.properties 时会用
#      ANDROID_HOME / ANDROID_SDK_ROOT 定位 SDK —— 这里注入 env 就够了，
#      不往仓库里写任何文件（local.properties 虽被 .gitignore 忽略，但会跨仓
#      携带错误的 sdk.dir，换机器就报「SDK location not found」）。
#
# 退出码：gradle 的退出码原样透传（0 = 成功）。
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"

# 工作区根：默认取仓库的上一级（本仓 clone 在 <workspace>/harness-v2 下）。
# 若目录结构变了，用 LOCALBUILD_J2ENV 直接指到 _j2env 绝对路径。
WORKSPACE_DEFAULT="$(cd "$REPO/.." && pwd)"
J2ENV="${LOCALBUILD_J2ENV:-$WORKSPACE_DEFAULT/_j2env}"

DEFAULT_TASK=":app:assembleDebug"
MODE="run"          # run | env | shell | help
GRADLE_ARGS=()

for arg in "$@"; do
  case "$arg" in
    --help|-h)  MODE="help" ;;
    --env)      MODE="env" ;;
    --shell)    MODE="shell" ;;
    *)          GRADLE_ARGS+=("$arg") ;;
  esac
done

if [ "$MODE" = "help" ]; then
  sed -n '2,40p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 0
fi

# ---------------------------------------------------------------------------
# 1. 工具链定位
# ---------------------------------------------------------------------------
# JDK 目录名带版本号（jdk-21.0.x+y），自动探测；多个时取第一个。
JDK_DIR=""
if [ -d "$J2ENV/jdk" ]; then
  JDK_DIR="$(ls -d "$J2ENV"/jdk/*/ 2>/dev/null | head -n 1)"
  JDK_DIR="${JDK_DIR%/}"
fi

if [ -z "$JDK_DIR" ] || [ ! -x "$JDK_DIR/bin/java" ]; then
  # 回落：外部已注入 JAVA_HOME 时不再报错（例如 CI 或自备 JDK 的环境）
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    JDK_DIR="$JAVA_HOME"
    echo "[localbuild] ⚠️ 未在 $J2ENV/jdk 找到 JDK，沿用已注入的 JAVA_HOME=$JAVA_HOME"
  else
    echo "[localbuild] ERROR: 未找到 JDK" >&2
    echo "[localbuild]   期望路径：$J2ENV/jdk/<jdk-21.x>/bin/java" >&2
    echo "[localbuild]   覆盖方式：LOCALBUILD_J2ENV=/path/to/_j2env $0 ..." >&2
    exit 1
  fi
fi

if [ ! -d "$J2ENV/sdk" ]; then
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}" ]; then
    echo "[localbuild] ⚠️ 未在 $J2ENV/sdk 找到 Android SDK，沿用已注入的 ANDROID_HOME=$ANDROID_HOME"
  else
    echo "[localbuild] ERROR: 未找到 Android SDK：$J2ENV/sdk 不存在，且 ANDROID_HOME 未注入" >&2
    echo "[localbuild]   重建步骤见工作区 _ci-tools/LOCALBUILD.md「重建环境」" >&2
    exit 1
  fi
fi

export JAVA_HOME="$JDK_DIR"
export ANDROID_HOME="${ANDROID_HOME:-$J2ENV/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$J2ENV/gradle-home}"
export PATH="$JAVA_HOME/bin:$PATH"

# ---------------------------------------------------------------------------
# 2. 沙箱 MITM 代理（见文件头坑 1 / 坑 2）
# ---------------------------------------------------------------------------
PROXY_URL="${https_proxy:-${HTTPS_PROXY:-${http_proxy:-${HTTP_PROXY:-http://127.0.0.1:1799}}}}"
PROXY_HOSTPORT="${PROXY_URL#*://}"       # 去 scheme
PROXY_HOSTPORT="${PROXY_HOSTPORT%%/*}"   # 去 path
PROXY_HOST="${PROXY_HOSTPORT%%:*}"
PROXY_PORT="${PROXY_HOSTPORT##*:}"
case "$PROXY_PORT" in
  ''|*[!0-9]*) PROXY_PORT=1799 ;;        # 解析不出端口 ⇒ 回落
esac
[ -z "$PROXY_HOST" ] && PROXY_HOST="127.0.0.1"

PROXY_OPTS="-Dhttp.proxyHost=$PROXY_HOST -Dhttp.proxyPort=$PROXY_PORT"
PROXY_OPTS="$PROXY_OPTS -Dhttps.proxyHost=$PROXY_HOST -Dhttps.proxyPort=$PROXY_PORT"
# 本地回环不走代理，否则 gradle 连自己的 daemon / 本地镜像会被代理搅乱
PROXY_OPTS="$PROXY_OPTS -Dhttp.nonProxyHosts=localhost|127.0.0.1"

export GRADLE_OPTS="$PROXY_OPTS ${GRADLE_OPTS:-}"

# ---------------------------------------------------------------------------
# 3. 回显诊断（每次都打，出错时第一眼能看出环境不对）
# ---------------------------------------------------------------------------
echo "[localbuild] repo            = $REPO"
echo "[localbuild] JAVA_HOME       = $JAVA_HOME"
echo "[localbuild] ANDROID_HOME    = $ANDROID_HOME"
echo "[localbuild] GRADLE_USER_HOME= $GRADLE_USER_HOME"
echo "[localbuild] proxy           = $PROXY_HOST:$PROXY_PORT (from https_proxy)"
echo "[localbuild] java -version   = $(java -version 2>&1 | head -n 1)"

# --env：只打印 export 行，供 `eval "$(bash scripts/localbuild.sh --env)"` 使用
# （scripts/fulltest.sh 需要同一套环境；用它就不用重复实现一遍注入逻辑）
if [ "$MODE" = "env" ]; then
  printf "export JAVA_HOME='%s'\n" "$JAVA_HOME"
  printf "export ANDROID_HOME='%s'\n" "$ANDROID_HOME"
  printf "export ANDROID_SDK_ROOT='%s'\n" "$ANDROID_SDK_ROOT"
  printf "export GRADLE_USER_HOME='%s'\n" "$GRADLE_USER_HOME"
  printf "export GRADLE_OPTS='%s'\n" "$GRADLE_OPTS"
  exit 0
fi

cd "$REPO" || { echo "[localbuild] ERROR: 无法进入 $REPO" >&2; exit 1; }

if [ "$MODE" = "shell" ]; then
  echo "[localbuild] 进入已配好环境的 bash（exit 退出，不影响系统环境）"
  exec bash
fi

# ---------------------------------------------------------------------------
# 4. 解析 gradle 启动器
# ---------------------------------------------------------------------------
# 仓库 wrapper 优先。Git 检出时若 gradlew 没有可执行位（模式 100644），
# 直接 `./gradlew` 会 Permission denied —— 用 sh 显式解释一次即可。
if [ -x ./gradlew ]; then
  GRADLE_CMD=(./gradlew)
elif [ -f ./gradlew ]; then
  echo "[localbuild] ⚠️ ./gradlew 无可执行位，改用 sh 解释执行"
  GRADLE_CMD=(sh ./gradlew)
elif command -v gradle >/dev/null 2>&1; then
  echo "[localbuild] ⚠️ 无 gradlew，使用 PATH 上的 gradle"
  GRADLE_CMD=(gradle)
else
  echo "[localbuild] ERROR: 既没有 ./gradlew 也没有 PATH 上的 gradle" >&2
  exit 1
fi

# 用户显式给了 --daemon 就不再加 --no-daemon（两者同现 gradle 会直接报错）。
# 默认 --no-daemon：本仓踩过「孤儿 daemon 持锁导致 test-results 清不掉」的坑。
DAEMON_FLAG="--no-daemon"
for a in ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"}; do
  case "$a" in
    --daemon) DAEMON_FLAG="" ;;
  esac
done

if [ "${#GRADLE_ARGS[@]}" -eq 0 ]; then
  GRADLE_ARGS=("$DEFAULT_TASK")
fi

echo "[localbuild] launcher        = ${GRADLE_CMD[*]}  task=${GRADLE_ARGS[*]}"
echo "[localbuild] 开始构建（失败先看输出里的 '^\* What went wrong' 段）"
echo "-------------------------------------------------------------------"

"${GRADLE_CMD[@]}" $DAEMON_FLAG --stacktrace ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"}
rc=$?

echo "-------------------------------------------------------------------"
if [ "$rc" -eq 0 ]; then
  echo "[localbuild] 成功 (rc=0)"
  # 顺手把产物位置打出来，省一次 find
  apk_list="$(find . -path ./.git -prune -o -type f -name '*.apk' -print 2>/dev/null | head -n 5 || true)"
  if [ -n "$apk_list" ]; then
    printf '[localbuild] 产物：%s\n' $apk_list
    # 真机安装提示：包名**从 APK 里读**，不在这里写死 —— 写死就会和
    # app/build.gradle.kts 的 applicationId 漂移（本仓硬约束是
    # com.rickeal.liquidharness，debug 带 .debug 后缀，便于与旧 App 同机共存）。
    first_apk="$(printf '%s\n' "$apk_list" | head -n 1)"
    aapt2_bin="$(ls -d "$ANDROID_HOME"/build-tools/*/aapt2* 2>/dev/null | sort -V | tail -n 1 || true)"
    pkg=""
    if [ -n "$aapt2_bin" ] && [ -x "$aapt2_bin" ]; then
      pkg="$("$aapt2_bin" dump packagename "$first_apk" 2>/dev/null | tr -d '\r' | head -n 1)"
    fi
    if [ -n "$pkg" ]; then
      echo "[localbuild] 真机安装（applicationId = $pkg）："
      echo "[localbuild]   MSYS_NO_PATHCONV=1 adb push \"$first_apk\" /data/local/tmp/app.apk"
      echo "[localbuild]   MSYS_NO_PATHCONV=1 adb shell pm install -r /data/local/tmp/app.apk"
      echo "[localbuild]   MSYS_NO_PATHCONV=1 adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1"
    fi
  fi
else
  echo "[localbuild] 失败 (rc=$rc)"
  echo "[localbuild] 排障顺序：① grep -A20 '^\\* What went wrong' 日志 —— 真实失败点在那一行"
  echo "[localbuild]           （别用不带锚点的 grep：本脚本自身也打印过那句提示，会先命中它）"
  echo "[localbuild]           ② 确认失败是否既有基线；③ 确认 JAVA_HOME/ANDROID_HOME 与上面回显一致。"
fi
exit "$rc"
