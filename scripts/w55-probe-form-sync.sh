#!/usr/bin/env bash
# =============================================================================
# w55-probe-form-sync.sh — 生产写点形态 ↔ PROBE_FORMS 机械自检
#
# 用法：
#   bash scripts/w55-probe-form-sync.sh          # 从任意 cwd 直接跑
#
# 为什么需要它（W49/W51/W54 同型第三次复发）：
#   W54 把 ContextCompressor 的压缩账本从 `(budget=.. target=..)` 改成五字段形态，
#   但 DiagnosticLogSink.PROBE_FORMS 没同步登记 ⇒ 该行不再落盘，真机 run-as
#   里读不到压缩账本。此类「改生产写点、忘改白名单」连续复发三次，只靠 review
#   挡不住。本脚本把「必落盘写点样本」做成可跑判据：任一生产账行样本不被
#   PROBE_FORMS 整行命中即 exit 1。
#
# W56 升级（P1-2，堵住旧版假绿）：
#   旧版 7 条 POSITIVE 是脚本内**硬编码**字符串，只从 DiagnosticLogSink.kt 提取
#   正则；把生产写点 `char_budget=` 改成 `char_cap=`（W54 型复发）脚本仍 rc=0，
#   而实际账行落盘=False。本版改为从生产写点源码**现场渲染**样本：解析
#   AgentLogStore 调用的 Kotlin 字符串拼接模板，字符串字面量与字段名原样取、
#   变量位填数字 ⇒ 写点改形状，样本跟着改，PROBE_FORMS 不再命中即 rc=1。
#   提取器同时限定 PROBE_FORMS 区域并剥离 // 注释行 ⇒ 注释化正则也判红。
#
# 判据（与 isProbeLine 同源，不另造一套口径）：
#   ① 正则从 DiagnosticLogSink.kt 的 PROBE_FORMS 区域内提取 `Regex("""...""")`
#      字面量（跳过 // 注释行），python re.fullmatch 对应 Kotlin `it.matches(m)`
#      整行语义；绝不内置「应该有」的正则常量表 —— 删/注释掉生产正则即判红。
#      FAILURE_DIGEST 等拼接式常量不在 raw-string 形态内，本脚本样本不覆盖（标题
#      只写「raw 整行形态」，不写「全量」）。
#   ② 长度闸门、CACHED_LABEL、FILE_NAME 同样现场提取，避免与生产漂移。
#   ③ 反向哨兵：自由文本 / 字段错序 / 多字段 / 缺尾 / 用户路径 / 非 bool 不得穿透。
#
# 退出码：0 = 全部现场渲染样本命中且反向哨兵全拦；1 = 判据破（打印失配样本）。
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
SINK="$REPO/app/src/main/java/com/rickeal/liquidharness/DiagnosticLogSink.kt"
COMP="$REPO/core-context/src/main/java/com/rickeal/liquidharness/core/agent/context/ContextCompressor.kt"
STORE="$REPO/core-data/src/main/java/com/rickeal/liquidharness/core/data/EndpointStore.kt"
BUDGET="$REPO/core-agent/src/main/java/com/rickeal/liquidharness/core/agent/loop/ContextBudget.kt"

for f in "$SINK" "$COMP" "$STORE" "$BUDGET"; do
  if [ ! -f "$f" ]; then
    echo "::error::找不到 $f —— 写点形态同步自检无法运行。"
    exit 1
  fi
done

python3 - "$SINK" "$COMP" "$STORE" "$BUDGET" <<'PY'
import re
import sys

sink_src, comp_src, store_src, budget_src = [
    open(p, encoding="utf-8").read() for p in sys.argv[1:5]
]

# ---------- ① 从 PROBE_FORMS 区域提取正则（跳过 // 注释行） ----------
def strip_line_comments(text):
    out = []
    for line in text.splitlines():
        pos = line.find('//')
        out.append(line if pos < 0 else line[:pos])
    return '\n'.join(out)

def probe_forms_region(src):
    m = re.search(r'PROBE_FORMS\s*:\s*List<Regex>\s*=\s*listOf\s*\(', src)
    if not m:
        return None
    start = m.end()
    depth, i, in_str, esc = 1, m.end(), False, False
    while i < len(src) and depth > 0:
        ch = src[i]
        if in_str:
            if esc:
                esc = False
            elif ch == '\\':
                esc = True
            elif ch == '"':
                in_str = False
        else:
            if ch == '"':
                in_str = True
            elif ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
        i += 1
    return src[start:i - 1]

region = probe_forms_region(sink_src)
if region is None:
    print("::error::DiagnosticLogSink.kt 找不到 PROBE_FORMS 列表，无法提取正则。")
    sys.exit(1)
region = strip_line_comments(region)

m_label = re.search(r'CACHED_LABEL\s*=\s*"""(.*?)"""', sink_src, re.S)
cached = m_label.group(1) if m_label else r'(?:n/a-usage|n/a-fields|\d+\(cumulative\))'
raw_forms = re.findall(r'Regex\("""(.*?)"""\)', region, re.S)
forms = [f.replace('$CACHED_LABEL', cached) for f in raw_forms]
compiled = []
for f in forms:
    try:
        compiled.append(re.compile(f))
    except re.error as exc:
        print(f"::error::PROBE_FORMS 正则无法编译: {f!r} ({exc})")
        sys.exit(1)

m_cap = re.search(r'MAX_DIGEST_CHARS\s*=\s*(\d+)', sink_src)
cap = int(m_cap.group(1)) if m_cap else 160
print(f"[w56-sync] PROBE_FORMS 区域提取 {len(compiled)} 条 raw 整行形态（已跳注释），MAX_DIGEST_CHARS={cap}")

# ---------- ② 从生产写点现场渲染样本 ----------
def split_top_plus(body):
    """按顶层 `+` 切分 Kotlin 拼接表达式（括号/字符串内的 + 不切）。"""
    parts, depth, in_str, esc, start = [], 0, False, False, 0
    for i, ch in enumerate(body):
        if in_str:
            if esc:
                esc = False
            elif ch == '\\':
                esc = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch in '([':
            depth += 1
        elif ch in ')]':
            depth -= 1
        elif ch == '+' and depth == 0:
            parts.append(body[start:i].strip())
            start = i + 1
    parts.append(body[start:].strip())
    return [p for p in parts if p]

def decode_literal(p):
    s = p[1:-1]
    out, i = [], 0
    mapped = {'n': '\n', 't': '\t', 'r': '\r', '"': '"', '\\': '\\'}
    while i < len(s):
        if s[i] == '\\' and i + 1 < len(s):
            out.append(mapped.get(s[i + 1], s[i + 1]))
            i += 2
        else:
            out.append(s[i])
            i += 1
    return ''.join(out)

def render_call(src, anchor, values):
    """定位含 anchor 的 AgentLogStore 调用，现场渲染整行。
    values: {expr 名: 渲染值, '__default__': 变量位默认数字}。"""
    idx = src.find(anchor)
    if idx < 0:
        print(f"::error::生产写点找不到锚点 {anchor!r}，无法现场渲染。")
        sys.exit(1)
    call = src.rfind('AgentLogStore.', 0, idx)
    if call < 0:
        print(f"::error::锚点 {anchor!r} 之前找不到 AgentLogStore 调用。")
        sys.exit(1)
    paren = src.find('(', call)
    depth, end, in_str, esc = 0, paren, False, False
    for i in range(paren, len(src)):
        ch = src[i]
        if in_str:
            if esc:
                esc = False
            elif ch == '\\':
                esc = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                end = i
                break
    body = src[paren + 1:end]
    out = ''
    for p in split_top_plus(body):
        if p.startswith('"') and p.endswith('"') and len(p) >= 2:
            out += decode_literal(p)
        else:
            out += values.get(p, values.get('__default__', '7'))
    return out

D = '7'
comp_line = lambda vals: render_call(comp_src, 'context compressed: ', vals)
POSITIVE = [
    ("ContextCompressor 新版压缩账本（现场渲染）",
     comp_line({'__default__': D})),
    ("ContextCompressor char_budget/char_target=Int.MAX_VALUE（现场渲染）",
     comp_line({'charBudget': '2147483647', 'charTarget': '2147483647', '__default__': D})),
    ("ContextCompressor 短数字形态（现场渲染）",
     comp_line({'__default__': '1'})),
]

m_file = re.search(r'FILE_NAME\s*=\s*"([^"]+)"', store_src)
file_name = m_file.group(1) if m_file else 'endpoints.json'
unreadable = render_call(store_src, 'endpoint_store=unreadable file=', {'FILE_NAME': file_name})
seed_failed = render_call(store_src, 'endpoint_store=seed_write_failed file=', {'FILE_NAME': file_name})
POSITIVE += [
    ("EndpointStore unreadable backup=true（现场渲染）",
     unreadable.replace('backup=' + D, 'backup=true')),
    ("EndpointStore unreadable backup=false（现场渲染）",
     unreadable.replace('backup=' + D, 'backup=false')),
    ("EndpointStore seed_write_failed（现场渲染）", seed_failed),
    ("ContextBudget before round（现场渲染）",
     render_call(budget_src, 'context compressed before round ', {'__default__': D})),
]

# ---------- ③ 反向哨兵（W55 既有 11 条，保持不动） ----------
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

print(f"[w56-sync] OK {len(POSITIVE)} 条现场渲染样本全命中，{len(NEGATIVE)} 条反向哨兵全拦下")
PY
exit $?
