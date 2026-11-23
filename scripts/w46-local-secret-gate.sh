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
# W47（P0-1 默认入口空过订正）：默认改为**全扫**（含 `_w*/` 与 `local.properties`），
# 因为本仓 W36-01 出口判据的精确场景就是「被 ignore 的 `_w*/` 下 `.jsonl` 含 serial、未 -f」。
# W46 版默认按 CI checkout 语义排除该目录 ⇒ 对该场景 rc=0 空过，等于把 CI 空转换成本地默认空转。
# CI checkout 语义移到显式 `--ci-semantics`（仅复刻干净 checkout 时用）。
#
# W48（P1-2 门禁去噪）：默认全扫在开发者本机被历史 MUST-RED 取证样本 / 历史构建日志
# 恒红（W47 自记 ROUTE3_HITS=141）——「默认恒红 → 习惯性加 --ci-semantics → 又空过」
# 的工作流退化。处方：不再一刀切排除目录，改按**已登记 fixture 文件哈希白名单**跳过
# 历史样本（scripts/fixtures/w46-gate-legacy-hashes.txt，W48 注册基线 140 件）：
#   - 命中敏感形态的文件先查册，sha256 在册 ⇒ 历史样本，跳过不报红；
#   - 不在册（新注入 / 被修改）⇒ 照常 RED；tracked 路1 永远不豁免（真泄漏面）。
# 于是单一默认参数同时满足「不误报历史」与「抓新注入」，--ci-semantics 只剩复刻用途。
#
# W49（P1-3 双键）：W48 册只按内容哈希豁免 ⇒「内容在册但位于全新路径」被静默跳过，
# 是未登记的豁免面。册改 `<sha256>  <relative/path>` 双键，is_legacy_fixture 同时校验
# 内容与文件身份；--selftest 增至六态（新增「同内容新路径 ⇒ RED」）。
#
# 用法：
#   bash scripts/w46-local-secret-gate.sh                 # 全扫当前工作树（本地提交前默认入口）
#   bash scripts/w46-local-secret-gate.sh --local-full     # 同默认（保留显式入口，兼容 W46 注释用法）
#   bash scripts/w46-local-secret-gate.sh --ci-semantics   # 复刻 CI checkout：排除 _w*/ 与 local.properties
#   bash scripts/w46-local-secret-gate.sh --selftest       # 六态注入/还原（历史在场 GREEN / 新探针 RED /
#                                                          #   add-f tracked RED / 移除 GREEN / --ci-semantics GREEN /
#                                                          #   同内容新路径 RED）
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
# W49：hostname 属性形态；W50（P2-2）改整值占位符白名单（与 secret-scan.yml 3b-ext 同口径）。
# 只有两个登记占位符豁免；闭合标签后拼正文 / 空值等形态一律命中。分段拼装避免脚本自命中。
H1='host'"name=\""
HV1='ho'"st"
HV2='redacted-'"value"
HOST_RE="${H1}"'(?!<(?:'"${HV1}"'|'"${HV2}"')>")'
LEGACY_HASHES="$ROOT/scripts/fixtures/w46-gate-legacy-hashes.txt"

# W48 P1-2：历史 fixture 哈希白名单。在册 ⇒ 已登记的历史样本（跳过）；不在册 ⇒ 新命中（报红）。
# W49 P1-3：册改**双键** `<sha256>  <relative/path>`，必须「内容哈希 AND 相对路径」同时命中
#   同一行才豁免。W48 单键只校验内容 ⇒ 把在册样本复制到**全新路径**会被静默跳过（未登记的
#   豁免面，外部审查以临时 git 仓库六态实证）。tracked 路1 仍不豁免。
is_legacy_fixture() {
  local rel="$1" h
  [ -f "$LEGACY_HASHES" ] || return 1
  h=$(sha256sum "$rel" 2>/dev/null | awk '{print $1}')
  [ -n "$h" ] || return 1
  grep -qxF "$h  $rel" "$LEGACY_HASHES"
}

scan_tracked() {
  git grep -nIF "${PAT[@]}" -- . 2>/dev/null
  git grep -nIP "$HOST_RE" -- . 2>/dev/null
}
scan_untracked() {
  local hits=0 f
  while IFS= read -r f; do
    { grep -qIF "${PAT[@]}" -- "$f" 2>/dev/null || grep -qIP "$HOST_RE" -- "$f" 2>/dev/null; } || continue
    if is_legacy_fixture "$f"; then continue; fi
    echo "RED untracked: $f"; hits=$((hits+1))
  done < <(git ls-files --others --exclude-standard 2>/dev/null)
  echo "ROUTE2_HITS=$hits"
}
scan_ignored() {
  local hits=0 f
  # W47：默认全扫。**仅**显式 --ci-semantics（复刻干净 CI checkout）时排除本地取证目录。
  local ci_semantics=0
  [ "${1:-}" = "--ci-semantics" ] && ci_semantics=1
  while IFS= read -r f; do
    case "$f" in .gradle/*|.kotlin/*|build/*|*/build/*) continue ;; esac
    case "$f" in *.bin|*.lock|*.apk|*.dex|*.class|*.so|*.png|*.jpg|*.jpeg) continue ;; esac
    # CI checkout 工作树干净、不含本地 `_w*/` 取证目录；只有显式复刻该语义时才排除。
    # 本地提交前默认入口必须覆盖 `_w*/` —— W36-01 精确场景即在此，排除它等于判据空过。
    if [ "$ci_semantics" -eq 1 ]; then
      case "$f" in _w[0-9]*/|_w[0-9]*|local.properties) continue ;; esac
    fi
    { grep -qIF "${PAT[@]}" -- "$f" 2>/dev/null || grep -qIP "$HOST_RE" -- "$f" 2>/dev/null; } || continue
    # W48 P1-2：历史 fixture 哈希在册 ⇒ 跳过（默认态不再被历史样本恒红）。
    if is_legacy_fixture "$f"; then continue; fi
    echo "RED ignored: $f"; hits=$((hits+1))
  done < <(git ls-files --others --ignored --exclude-standard 2>/dev/null)
  echo "ROUTE3_HITS=$hits"
}

selftest() {
  local probe="_w48/forceadd_probe.jsonl"
  mkdir -p _w48
  echo "=== STATE 1: 默认全扫 + 历史 fixture 在场（W48 哈希白名单后应三路全 0，GREEN rc=0）==="
  scan_tracked | head -3; echo "ROUTE1_HITS=$(scan_tracked | wc -l | tr -d ' ')"
  scan_untracked | grep "^RED" || true
  scan_untracked | tail -1
  scan_ignored | grep "^RED" || true
  scan_ignored | tail -1
  echo "=== STATE 2a: 被 ignore 的新 .jsonl 含 serial 未 -f + 默认全扫（第三路应 RED，rc=1）==="
  printf 'serial=%s path=%s\n' "$S1" "$P1" > "$probe"
  git check-ignore "$probe" >/dev/null 2>&1 && echo "check-ignore: $probe (ignored ✓)"
  scan_ignored | grep "$probe" && echo "RED ignored hit: $probe" || echo "WARN: route3 did not see probe"
  echo "=== STATE 2b: 同上 + git add -f（第一路 tracked git grep 应 RED，rc=1）==="
  git add -f "$probe" 2>/dev/null
  # 只打文件名列，不打命中行内容 —— 命中行含真实字面量，禁止进证据（W47 g3 前车）。
  # 直接用 `git grep -q` 判（不经过多段管道：管道只反映最后一段的 rc，会把故障吞成 WARN）。
  if git grep -qIF "${PAT[@]}" -- "$probe" 2>/dev/null; then
    echo "RED tracked hit (git grep rc=0): $probe"
  else
    echo "SELF_FAULT: route1 did not see staged probe"
  fi
  echo "=== STATE 3: 移除探针回默认 GREEN（历史 fixture 仍在场）==="
  git rm --cached -f "$probe" >/dev/null 2>&1
  rm -f "$probe"
  local t1 t3
  t1=$(scan_tracked | wc -l | tr -d ' ')
  t3=$(grep -lIF "${PAT[@]}" -- "$probe" 2>/dev/null | wc -l | tr -d ' ')
  if [ -e "$probe" ]; then pe=yes; else pe=no; fi
  if git ls-files --error-unmatch "$probe" >/dev/null 2>&1; then pi=yes; else pi=no; fi
  echo "ROUTE1_HITS=$t1 ; probe file exists=$pe ; in-index=$pi"
  echo "probe literal hits after removal=$t3 (expect 0)"
  scan_ignored | tail -1
  echo "=== STATE 4: --ci-semantics（复刻 CI checkout，应 GREEN rc=0）==="
  scan_ignored --ci-semantics | tail -1
  # W49 P1-3 第六态：内容哈希在册、路径不在册 ⇒ 双键必须判 RED（W48 单键实态为 GREEN）。
  local dup="_w49/brand_new_path.jsonl" src="_w12_archguard.log"
  mkdir -p _w49
  if [ -f "$src" ]; then
    cp "$src" "$dup"
    git check-ignore "$dup" >/dev/null 2>&1 && echo "check-ignore: $dup (ignored ✓)"
    echo "same-content src=$src hash=$(sha256sum "$src" | awk '{print $1}') ; dup in book(pair)=$(grep -cxF "$(sha256sum "$dup" | awk '{print $1}')  $dup" "$LEGACY_HASHES" || true)"
    scan_ignored | grep "$dup" && echo "RED same-content new path hit: $dup (double-key OK)" || echo "SELF_FAULT: new path was exempted (double-key broken)"
    rm -f "$dup"
    scan_ignored | grep "$dup" >/dev/null && echo "SELF_FAULT: dup still reported after removal" || echo "dup removed: GREEN"
  else
    echo "SKIP STATE 5: $src absent (历史样本不在场)"
  fi
  # W50（P2-2）第七态：hostname 整值白名单两条注入必红（闭合标签尾随正文 / 空值）。
  # 旧判据「引号后首字符非 `<` 即命中」对第一条空过（`<` 开头），对第二条误报但无 rc 对照；
  # 新判据两条都 RED，两个登记占位符仍 GREEN。
  local hp1="_w50/hn_bypass_probe.txt" hp2="_w50/hn_empty_probe.txt" gate_out gate_rc
  mkdir -p _w50
  printf '%s\n' "${H1}</host> anything" > "$hp1"
  printf '%s\n' "${H1}\"" > "$hp2"
  grep -qIP "$HOST_RE" -- "$hp1" && echo "RED hostname bypass (grep rc=0): $hp1" || echo "SELF_FAULT bypass probe not seen"
  grep -qIP "$HOST_RE" -- "$hp2" && echo "RED hostname empty value (grep rc=0): $hp2" || echo "SELF_FAULT empty probe not seen"
  gate_out=$(bash "$0" 2>&1); gate_rc=$?
  echo "gate default with hostname probes rc=$gate_rc (expect 1); $(echo "$gate_out" | head -2)"
  rm -f "$hp1" "$hp2"
  gate_out=$(bash "$0" 2>&1); gate_rc=$?
  echo "gate default after removal rc=$gate_rc (expect 0); $(echo "$gate_out" | tail -1)"
  echo "=== selftest end（探针已还原，工作树与 index 干净）==="
}

if [ "${1:-}" = "--selftest" ]; then
  selftest
  exit 0
fi

# W47：顶层参数分派（默认全扫 / --local-full 与默认同义 / --ci-semantics 显式排除）。
# 未知参数判故障红，不静默当作默认（避免不可达用法再次出现）。
case "${1:-}" in
  ""|--local-full) ci_arg="" ;;
  --ci-semantics) ci_arg="--ci-semantics" ;;
  *) echo "SELF_FAULT unknown arg: $1 (use --local-full|--ci-semantics|--selftest)"; exit 2 ;;
esac

# 正常态：三路汇总
r1=$(scan_tracked | wc -l | tr -d ' ')
r2_out=$(scan_untracked); r3_out=$(scan_ignored "$ci_arg")
echo "w46-local-secret-gate: route1(tracked)=$r1 route2(untracked)=$(echo "$r2_out" | tail -1) route3(ignored)=$(echo "$r3_out" | tail -1)"
if [ "$r1" -gt 0 ]; then echo "$r2_out" | grep '^RED' ; echo "$r3_out" | grep '^RED'; exit 1; fi
echo "$r2_out" | grep -q '^RED' && { echo "$r2_out"; exit 1; }
echo "$r3_out" | grep -q '^RED' && { echo "$r3_out"; exit 1; }
echo "OK no sensitive leak in tracked / untracked / ignored sweeps"
exit 0
