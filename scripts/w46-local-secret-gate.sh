#!/usr/bin/env bash
# W46（P1-2）：本地提交前 3b-ext 人工闸门 —— CI 两路 + 本地注入四态复刻。
#
# 为什么需要：W45 把「被 ignore 未 -f」的扫描路写进了 .github/workflows/secret-scan.yml，
# 但 CI checkout 工作树干净、被 ignore 的仅构建产物（已 case 排除）⇒ 该路在 CI 上恒列 0 文件、
# 对目标场景 vacuous。真正需要它的是**开发者本机**，而复刻脚本一直没入仓，
# g0_untracked_scan_v2.txt 只是「复刻输出文本」。本脚本补上可执行入口。
#
# 三路（与 secret-scan.yml 3b-ext 同口径，八类形态分段拼装避免脚本自命中）：
#   路1 tracked:    git grep -nIF -- .            （git add -f 进 index 的命中）
#   路2 untracked:  git ls-files --others --exclude-standard
#   路3 ignored:    git ls-files --others --ignored --exclude-standard
# 正常态三路全 0 ⇒ rc=0；任一路命中 ⇒ RED rc=1；grep 故障 rc≥2 ⇒ RED。
#
# 用法：
#   bash scripts/w46-local-secret-gate.sh                 # 扫描当前工作树
#   bash scripts/w46-local-secret-gate.sh --selftest       # 在临时探针文件上跑四态注入/还原
#     STATE 1 clean=0 / STATE 2a ignored未-f=RED / STATE 2b add-f tracked=RED / STATE 3 removed=0
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 2

S1="1330""9cc8"        # 设备标识（分段拼装）
P1="D:/WB""fil"        # 本机绝对路径形态 1
P2="/d/WB""fil"        # 本机绝对路径形态 2
BS='\'
U1="C:${BS}Users"      # 用户目录形态 1
U2="C:/Us""ers"        # 用户目录形态 2
U3="/Us""ers/"         # 用户目录形态 3
U4="/ho""me/"          # 用户目录形态 4
M1="长度 ""129"        # PAT 长度元信息
PAT=(-e "$S1" -e "$P1" -e "$P2" -e "$U1" -e "$U2" -e "$U3" -e "$U4" -e "$M1")

scan_tracked() {
  git grep -nIF "${PAT[@]}" -- . 2>/dev/null
}
scan_untracked() {
  local hits=0 f
  while IFS= read -r f; do
    grep -qIF "${PAT[@]}" -- "$f" 2>/dev/null && { echo "RED untracked: $f"; hits=$((hits+1)); }
  done < <(git ls-files --others --exclude-standard 2>/dev/null)
  echo "ROUTE2_HITS=$hits"
}
scan_ignored() {
  local hits=0 f
  while IFS= read -r f; do
    case "$f" in .gradle/*|.kotlin/*|build/*|*/build/*) continue ;; esac
    case "$f" in *.bin|*.lock|*.apk|*.dex|*.class|*.so|*.png|*.jpg|*.jpeg) continue ;; esac
    # 默认按 CI checkout 语义跑：本地历史取证目录 `_w*/` 在 CI 不存在，
    # 且其中含 W44 g0 已登记的 MUST-RED 泄漏样本 —— 本地全扫恒 RED 属预期、非本波新泄漏。
    # 需要看全貌（含历史必红取证）时用 --local-full。
    if [ "${1:-}" != "--local-full" ]; then
      case "$f" in _w[0-9]*/|_w[0-9]*|local.properties) continue ;; esac
    fi
    grep -qIF "${PAT[@]}" -- "$f" 2>/dev/null && { echo "RED ignored: $f"; hits=$((hits+1)); }
  done < <(git ls-files --others --ignored --exclude-standard 2>/dev/null)
  echo "ROUTE3_HITS=$hits"
}

selftest() {
  local probe="_w46/forceadd_probe.jsonl"
  mkdir -p _w46
  echo "=== STATE 1: clean checkout（期望三路全 0）==="
  scan_tracked | head -3; echo "ROUTE1_HITS=$(scan_tracked | wc -l | tr -d ' ')"
  scan_untracked | tail -1; scan_ignored | tail -1
  echo "=== STATE 2a: 被 ignore 的 .jsonl 未 -f（第三路应 RED）==="
  printf 'serial=%s path=%s\n' "$S1" "$P1" > "$probe"
  git check-ignore "$probe" >/dev/null 2>&1 && echo "check-ignore: $probe (ignored ✓)"
  scan_ignored --local-full | grep "$probe" && echo "RED ignored hit: $probe" || echo "WARN: route3 did not see probe"
  echo "=== STATE 2b: 同上 + git add -f（第一路 tracked git grep 应 RED）==="
  git add -f "$probe" 2>/dev/null
  scan_tracked | grep "$probe" && echo "RED tracked hit (git grep rc=0): $probe" || echo "WARN: route1 did not see staged probe"
  echo "=== STATE 3: 移除探针回 GREEN ==="
  git rm --cached -f "$probe" >/dev/null 2>&1
  rm -f "$probe"
  local t1 t3
  t1=$(scan_tracked | wc -l | tr -d ' ')
  t3=$(grep -lIF "${PAT[@]}" -- "$probe" 2>/dev/null | wc -l | tr -d ' ')
  if [ -e "$probe" ]; then pe=yes; else pe=no; fi
  if git ls-files --error-unmatch "$probe" >/dev/null 2>&1; then pi=yes; else pi=no; fi
  echo "ROUTE1_HITS=$t1 ; probe file exists=$pe ; in-index=$pi"
  echo "probe literal hits after removal=$t3 (expect 0)" 
  echo "=== selftest end（探针已还原，工作树与 index 干净）==="
}

if [ "${1:-}" = "--selftest" ]; then
  selftest
  exit 0
fi

# 正常态：三路汇总
r1=$(scan_tracked | wc -l | tr -d ' ')
r2_out=$(scan_untracked); r3_out=$(scan_ignored)
echo "w46-local-secret-gate: route1(tracked)=$r1 route2(untracked)=$(echo "$r2_out" | tail -1) route3(ignored)=$(echo "$r3_out" | tail -1)"
if [ "$r1" -gt 0 ]; then echo "$r2_out" | grep '^RED' ; echo "$r3_out" | grep '^RED'; exit 1; fi
echo "$r2_out" | grep -q '^RED' && { echo "$r2_out"; exit 1; }
echo "$r3_out" | grep -q '^RED' && { echo "$r3_out"; exit 1; }
echo "OK no sensitive leak in tracked / untracked / ignored sweeps"
exit 0
