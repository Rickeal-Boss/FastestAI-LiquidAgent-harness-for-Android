#!/usr/bin/env bash
# =============================================================================
# w55-probe-form-sync.sh — 生产 AgentLogStore 写点形态 ↔ PROBE_FORMS 机械自检
#
# 用法：
#   bash scripts/w55-probe-form-sync.sh          # 从任意 cwd 直接跑
#
# 为什么需要它（W49/W51/W54 同型第三次复发）：
#   W54 把 ContextCompressor 的压缩账本从 `(budget=.. target=..)` 改成五字段形态，
#   但 DiagnosticLogSink.PROBE_FORMS 没同步登记 ⇒ 该行不再落盘，真机 run-as
#   里读不到压缩账本。此类「改生产写点、忘改白名单」连续复发三次，只靠 review
#   挡不住。本脚本把「必落盘写点样本」做成可跑判据：样本逐字来自
#   ContextCompressor:136 / EndpointStore:113,122 / ContextBudget:193，任一
#   样本不被 PROBE_FORMS 整行命中即 exit 1。
#
# 判据怎么复刻（与 isProbeLine 同源，不另造一套口径）：
#   ① 正则**从 DiagnosticLogSink.kt 源码现场提取** `Regex("""...""")` 字面量，
#      再 python re.fullmatch —— 对应 Kotlin 的 `it.matches(m)` 整行语义。
#      绝不内置一份「应该有的正则」常量表：那样删掉生产正则也判不出红。
#   ② 长度闸门同样现场提取 MAX_DIGEST_CHARS，超长样本直接判不落盘。
#   ③ 反向哨兵：自由文本 / 路径尾巴不得被任何一条正则命中，防止有人把整行
#      正则放宽成 `^context compressed.*$` 之类的前缀匹配（假绿）。
#
# 退出码：0 = 全部必落盘样本命中且反向哨兵全拦；1 = 判据破（打印失配样本）。
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
SINK="$REPO/app/src/main/java/com/rickeal/liquidharness/DiagnosticLogSink.kt"

if [ ! -f "$SINK" ]; then
  echo "::error::找不到 $SINK —— PROBE_FORMS 同步自检无法运行。"
  exit 1
fi

python3 - "$SINK" <<'PY'
import re
import sys

src = open(sys.argv[1], encoding="utf-8").read()

# CACHED_LABEL 插值：现场取值，避免这里与生产常量漂移。
m_label = re.search(r'CACHED_LABEL\s*=\s*"""(.*?)"""', src, re.S)
cached = m_label.group(1) if m_label else r'(?:n/a-usage|n/a-fields|\d+\(cumulative\))'

# PROBE_FORMS 里所有 raw-string 整行形态（拼接式常量如 FAILURE_DIGEST 不覆盖本脚本样本）。
raw_forms = re.findall(r'Regex\("""(.*?)"""\)', src, re.S)
forms = [f.replace('$CACHED_LABEL', cached) for f in raw_forms]
compiled = []
for f in forms:
    try:
        compiled.append(re.compile(f))
    except re.error as exc:
        print(f"::error::PROBE_FORMS 正则无法编译: {f!r} ({exc})")
        sys.exit(1)

m_cap = re.search(r'MAX_DIGEST_CHARS\s*=\s*(\d+)', src)
cap = int(m_cap.group(1)) if m_cap else 160
print(f"[w55-sync] 提取 {len(compiled)} 条整行形态，MAX_DIGEST_CHARS={cap}")

# 必落盘生产写点样本（逐字来自源码，非臆造）。
POSITIVE = [
    ("ContextCompressor:136 新版压缩账本",
     "context compressed: 264 -> 77 messages (chars=35338, dropped=70, char_budget=228556, char_target=137133, budget=128000, target=76800 tokens)"),
    ("ContextCompressor:136 char_budget/char_target=Int.MAX_VALUE",
     "context compressed: 264 -> 77 messages (chars=35338, dropped=70, char_budget=2147483647, char_target=2147483647, budget=128000, target=76800 tokens)"),
    ("ContextCompressor:136 短数字形态",
     "context compressed: 2 -> 1 messages (chars=10, dropped=1, char_budget=100, char_target=50, budget=100, target=50 tokens)"),
    ("EndpointStore:113 unreadable backup=true",
     "endpoint_store=unreadable file=endpoints.json backup=true"),
    ("EndpointStore:113 unreadable backup=false",
     "endpoint_store=unreadable file=endpoints.json backup=false"),
    ("EndpointStore:122 seed_write_failed",
     "endpoint_store=seed_write_failed file=endpoints.json"),
    ("ContextBudget:193 before round",
     "context compressed before round 3: 30 -> 12 messages (estimated 40000 > budget 20928)"),
]

# 反向哨兵：自由文本 / 字段错序 / 多字段 / 缺尾 / 用户路径 / 非 bool（长度均 < cap，必须靠正则拦下）。
NEGATIVE = [
    ("压缩形态拼用户自由文本",
     "context compressed: 2 -> 1 messages (chars=10, dropped=1, char_budget=100, char_target=50, budget=100, target=50 tokens) 用户echo"),
    ("压缩形态字段顺序调换",
     "context compressed: 2 -> 1 messages (dropped=1, chars=10, char_budget=100, char_target=50, budget=100, target=50 tokens)"),
    ("压缩形态多一字段",
     "context compressed: 2 -> 1 messages (chars=10, dropped=1, char_budget=100, char_target=50, budget=100, target=50, extra=7 tokens)"),
    ("压缩形态结尾无 tokens",
     "context compressed: 2 -> 1 messages (chars=10, dropped=1, char_budget=100, char_target=50, budget=100, target=50)"),
    ("unreadable 拼路径尾巴",
     "endpoint_store=unreadable file=endpoints.json backup=true /sdcard/secret.txt"),
    ("unreadable file 含斜杠",
     "endpoint_store=unreadable file=/sdcard/secret/endpoints.json backup=true"),
    ("unreadable file 含空格",
     "endpoint_store=unreadable file=my endpoints.json backup=true"),
    ("unreadable backup 非 bool",
     "endpoint_store=unreadable file=endpoints.json backup=yes"),
    ("seed_write_failed 拼路径尾巴",
     "endpoint_store=seed_write_failed file=endpoints.json/../../secret"),
    ("seed_write_failed file 含斜杠",
     "endpoint_store=seed_write_failed file=/sdcard/secret/endpoints.json"),
    ("seed_write_failed file 含空格",
     "endpoint_store=seed_write_failed file=my endpoints.json"),
]


def mirrored(line):
    m = line.strip()
    return len(m) <= cap and any(rx.fullmatch(m) for rx in compiled)


bad = []
for name, line in POSITIVE:
    if not mirrored(line):
        bad.append(("必落盘却漏落盘", name, line))
for name, line in NEGATIVE:
    if mirrored(line):
        bad.append(("自由文本却穿透落盘", name, line))

if bad:
    for why, name, line in bad:
        print(f"RED  {why} | {name} | {line}")
    print(f"::error::PROBE_FORMS 同步自检判红：{len(bad)} 条失配")
    sys.exit(1)

print(f"[w55-sync] OK {len(POSITIVE)} 条必落盘样本全命中，{len(NEGATIVE)} 条反向哨兵全拦下")
PY
exit $?
