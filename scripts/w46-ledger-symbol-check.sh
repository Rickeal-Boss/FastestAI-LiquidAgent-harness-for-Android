#!/usr/bin/env bash
# 台账反引号「测试类 / 方法名」指路自检（W46 P0-1）。
#
# 判据（内容订正级，非新增判据编号 —— 判据冻结下不并入 arch-guard G 编号表）：
#   docs/06-deferred-ledger.md 注记里每个**反引号包裹**、形如测试类名（`XxxTest`）
#   或测试方法名（`lowerCamel` 且带明确「必红 / 用例 / 方法」语境）的符号，
#   必须能在仓内 *.kt（排除 build/.gradle/.kotlin/.git）里 grep 到；否则判红。
#
# 为什么需要：W45 补录把虚构的 `RunTerminationLengthReportTest` /
# `nonProbeLinesAreNotMirroredToSink` 等写进权威真值源，外部按注记 grep 必 0 命中，
# 「逐行留痕」不可复核。W46 已把这些订正为真实符号，并把虚构名去反引号保留为历史时点记录。
#
# 形态收窄（避免误伤真实字段名 —— W46 实跑教训）：
#   - 测试类名：`^[A-Z][A-Za-z0-9]*Test$`（21 个现存指路，全部可复核）。
#   - 测试方法名：只认同一行 60 字符邻域内带「必红|用例|方法|Test.」锚点的 lowerCamel，
#     纯字段名（maxContextTokens / compressedUpTo 等）不认。两族合计即派单卡所指范围。
#
# 用法：bash scripts/w46-ledger-symbol-check.sh [ledger路径]
#   rc=0 全部指路可复核；rc=1 有指路不到（RED）；rc=2 环境故障。
# 必红样本：在注记里注入 `FunNameNeverImplementedTest` ⇒ 本脚本 rc=1。
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 2
LEDGER="${1:-docs/06-deferred-ledger.md}"
[ -f "$LEDGER" ] || { echo "SELF_FAULT ledger not found: $LEDGER"; exit 2; }

python3 - "$LEDGER" <<'PY'
import io, os, re, sys
ledger = sys.argv[1]
lines = io.open(ledger, "r", encoding="utf-8").read().splitlines()
cls, meth = set(), set()
for ln in lines:
    for t in re.findall(r"`([^`\n]+)`", ln):
        if re.fullmatch(r"[A-Z][A-Za-z0-9]*Test", t):
            cls.add(t)
        elif re.fullmatch(r"[a-z][A-Za-z0-9]{11,}", t) and re.search(r"[a-z][A-Z]", t):
            # 只认测试语境内的方法名：同一行出现必红/用例/方法/Test. 锚点
            if re.search(r"必红|用例|方法|Test\.", ln):
                meth.add(t)
syms = sorted(cls | meth)
blob_parts = []
for r, dirs, files in os.walk("."):
    dirs[:] = [d for d in dirs if d not in ("build", ".gradle", ".kotlin", ".git")]
    for f in files:
        if f.endswith(".kt"):
            try:
                blob_parts.append(io.open(os.path.join(r, f), "r", encoding="utf-8", errors="ignore").read())
            except OSError:
                pass
blob = "\n".join(blob_parts)
missing = [s for s in syms if s not in blob]
print("w46-ledger-symbol-check: test-class refs=%d test-method refs=%d kt-files=%d" % (len(cls), len(meth), len(blob_parts)))
if missing:
    for s in missing:
        print("RED ledger symbol not found in any *.kt: %s" % s)
    sys.exit(1)
print("OK all backticked test symbols (with test context) resolvable in *.kt")
PY
