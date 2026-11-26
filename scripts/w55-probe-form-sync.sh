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
#   变量位填数 ⇒ 写点改形状，样本跟着改，PROBE_FORMS 不再命中即 rc=1。
#
# W57 升级（P1-2，堵住 G/H 两型残余缺口）：
#   G 型假绿：旧版 POSITIVE 只锚 4 处写点，非锚点写点（ConversationOps 的
#     `manual_compress=noop reason=` 等）改形状仍 rc=0。本版两层：① 锚点白名单
#     扩到三类非锚点写点；② 全库 os.walk 扫所有 src/main/*.kt 的
#     `AgentLogStore.<method>(` 固定形态拼接，按 regex 派生 tag 认领并渲染最小
#     命中样本。判据与 isProbeLine 同源 —— 正则只从 PROBE_FORMS 提取，绝不内置
#     「应该有」的正则表。
#   H 型假红：旧版 `strip_line_comments()` 用 `line.find('//')` 不分字符串内外，
#     合法正则里的 `//`（URL/pattern）被切断 ⇒ 误报「正则无法编译」。本版改为
#     引号状态机 + 转义处理，只切字符串字面量外的 `//`。
#
# 判据（与 isProbeLine 同源，不另造一套口径）：
#   ① 正则从 DiagnosticLogSink.kt 的 PROBE_FORMS 区域内提取 `Regex("""...""")`
#      内联字面量（18 条 raw 整行形态），并按名解析该列表引用的
#      REASON_LINE / REASON_CODE_LINE / REASON_APPLIED 三条命名常量 Regex
#      定义；python re.fullmatch 对应 Kotlin `it.matches(m)` 整行语义。
#   ② 长度闸门、CACHED_LABEL、FILE_NAME 同样现场提取，避免与生产漂移。
#   ③ 反向哨兵：自由文本 / 字段错序 / 多字段 / 缺尾 / 用户路径 / 非 bool 不得穿透。
#   ④ 正控先行：锚点写点 + 全库认领写点必渲染出命中样本；任一失配即 rc=1。
#   ⑤ A2 静态形态判据（W57 立、W58 补控制流，零新 @Test）：redactHeaders
#      （private、无 JVM 用例）按花括号配平抽函数体，体内必须同时含
#      equals("Cookie") / equals("X-Auth-Token") 两个具名头形态（ignoreCase；不认
#      contains 泛化形态以免掩盖删名变异），且返回表达式必须是
#      `if (sensitive) REDACTED else value` 脱敏控制流分支；删具名头、摘控制流或
#      函数体抽空均 rc=1，不以 0 命中冒充绿。
#   ⑦ W59 A2 第三层加固（K2/K3 假绿，外审 §3.2 坐实）：A2 判据先剥 // 注释再归一，
#      且要求 if(sensitive)redactedelsevalue 是函数体归一后的**收尾**（最后返回表达式）——
#      K2（控制流串写在注释里）/K3（also{} 包裹、返回值被丢弃）均 rc=1。
#
#   ⑥ W58 认领逃逸堵漏（P0-1）：全库固定形态写点首字面量失配不再静默 continue ——
#      除 CLAIM_EXEMPT 一次性显式豁免外一律判红；认领数对 EXPECTED_CLAIMS 基线，
#      波动即判红并要求归因。
#
# 不覆盖（诚实边界，写全而非只举一例）：
#   - FAILURE_DIGEST 是拼接式常量、无单一 raw-string 字面量，不在 raw 形态样本内；
#   - REASON_LINE / REASON_CODE_LINE / REASON_APPLIED 三条命名常量：仅按名解析其
#     Regex 字面量供写点样本命中，脚本**不反推**这些常量的定义本体作必红样本
#     （其正则文本被改坏时只从写点侧间接判红）；
#   以上四类均不在「18 条 raw 形态反推样本」的保护范围内。标题只写「raw 整行
#   形态 + 写点认领」，不写「全量」。
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

python3 - "$SINK" "$COMP" "$STORE" "$BUDGET" "$REPO" <<'PY'
import os
import re
import sys

sink_src, comp_src, store_src, budget_src = [
    open(p, encoding="utf-8").read() for p in sys.argv[1:5]
]
repo = sys.argv[5]

# ---------- ① 从 PROBE_FORMS 区域提取正则（字符串感知剥注释；H 修复） ----------
def strip_line_comments(text):
    """只切字符串字面量**外**的 `//`：引号状态机 + 转义处理。
    W57 H 型：旧版 line.find('//') 会把正则里的 http:// 切断，误报无法编译。"""
    out = []
    for line in text.splitlines():
        in_str, esc, cut = False, False, len(line)
        i = 0
        while i < len(line):
            ch = line[i]
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
                elif ch == '/' and i + 1 < len(line) and line[i + 1] == '/':
                    cut = i
                    break
            i += 1
        out.append(line[:cut])
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

# 18 条 inline raw 整行形态（保留 W56 提取口径）
raw_forms = re.findall(r'Regex\("""(.*?)"""\)', region, re.S)
forms = [f.replace('$CACHED_LABEL', cached) for f in raw_forms]

# PROBE_FORMS 按名引用的 3 条命名常量 Regex（G 型覆盖 rely on these）
NAMED = ["REASON_LINE", "REASON_CODE_LINE", "REASON_APPLIED"]
named_forms = []
for nm in NAMED:
    if re.search(r'\b' + nm + r'\b', region):
        mm = re.search(r'\b' + nm + r'\s*=\s*Regex\(\s*"""(.*?)"""\s*\)', sink_src, re.S)
        if not mm:
            print(f"::error::PROBE_FORMS 引用 {nm} 但找不到其 Regex 定义，无法认领写点。")
            sys.exit(1)
        named_forms.append(mm.group(1).replace('$CACHED_LABEL', cached))

all_forms = forms + named_forms
compiled = []
for f in all_forms:
    try:
        compiled.append(re.compile(f))
    except re.error as exc:
        print(f"::error::PROBE_FORMS 正则无法编译: {f!r} ({exc})")
        sys.exit(1)

m_cap = re.search(r'MAX_DIGEST_CHARS\s*=\s*(\d+)', sink_src)
cap = int(m_cap.group(1)) if m_cap else 160
print(f"[w59-sync] PROBE_FORMS 区域提取 {len(raw_forms)} 条 raw 整行形态（已字符串感知跳注释）"
      f" + {len(named_forms)} 条命名常量形态，MAX_DIGEST_CHARS={cap}")

# ---------- ② 现场渲染工具（保留 render_call；新增通用 render_body / render_at） ----------
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
    if p.startswith('"""') and p.endswith('"""') and len(p) >= 6:
        return p[3:-3]
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

def is_literal(p):
    return (p.startswith('"""') and p.endswith('"""') and len(p) >= 6) or \
           (p.startswith('"') and p.endswith('"') and len(p) >= 2 and not p.startswith('"""'))

def render_call(src, anchor, values):
    """定位含 anchor 的 AgentLogStore 调用，现场渲染整行（W56 原机制，保留）。"""
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
    return render_body(src[paren + 1:end], values)

def render_body(body, values):
    out = ''
    for p in split_top_plus(body):
        if is_literal(p):
            out += decode_literal(p)
        else:
            out += values.get(p, values.get('__default__', '7'))
    return out

def find_quote_open(src, idx):
    i = idx - 1
    while i >= 0:
        if src[i] == '"':
            bs, j = 0, i - 1
            while j >= 0 and src[j] == '\\':
                bs += 1
                j -= 1
            if bs % 2 == 0:
                return i
        i -= 1
    return -1

def find_quote_close(src, start):
    triple = src[start:start + 3] == '"""'
    i = start + (3 if triple else 1)
    while i < len(src):
        if triple:
            if src[i:i + 3] == '"""':
                return i + 2
            i += 1
        else:
            if src[i] == '\\':
                i += 2
                continue
            if src[i] == '"':
                return i
            i += 1
    return -1

def expr_around(src, idx):
    """取 idx 所在字符串字面量所属的整条拼接表达式（同时覆盖
    `AgentLogStore.warn("..." + v)` 与 `val line = "..." + v; AgentLogStore.warn(line)`）。"""
    qo = find_quote_open(src, idx)
    if qo < 0:
        return None
    qc = find_quote_close(src, qo)
    if qc < 0:
        return None
    i, depth, bstart = qo - 1, 0, 0
    while i >= 0:
        ch = src[i]
        if ch in ')]':
            depth += 1
        elif ch in '([':
            if depth == 0:
                bstart = i + 1
                break
            depth -= 1
        elif depth == 0 and ch in '=;,{\n':
            bstart = i + 1
            break
        i -= 1
    i, depth, bend = qc + 1, 0, len(src)
    in_str, esc, triple = False, False, False
    while i < len(src):
        ch = src[i]
        if in_str:
            if triple:
                if src[i:i + 3] == '"""':
                    in_str, triple = False, False
                    i += 3
                    continue
            else:
                if esc:
                    esc = False
                elif ch == '\\':
                    esc = True
                    i += 1
                    continue
                elif ch == '"':
                    in_str = False
            i += 1
            continue
        if src[i:i + 3] == '"""':
            in_str, triple = True, True
            i += 3
            continue
        if ch == '"':
            in_str = True
            i += 1
            continue
        if ch in '([':
            depth += 1
        elif ch in ')]':
            if depth == 0:
                bend = i
                break
            depth -= 1
        elif depth == 0 and ch in ';,':
            bend = i
            break
        elif ch == '\n':
            seg = src[src.rfind('\n', 0, i) + 1:i].rstrip()
            if not seg.endswith('+'):
                bend = i
                break
        i += 1
    return src[bstart:bend]

def render_at(src, anchor, values):
    # 跳过 KDoc/注释里的字面量出现：只认「同一行、锚点之前有未闭合引号」的那次出现。
    idx, found = -1, False
    pos = 0
    while True:
        hit = src.find(anchor, pos)
        if hit < 0:
            break
        ls = src.rfind('\n', 0, hit) + 1
        if '"' in src[ls:hit]:
            idx, found = hit, True
            break
        pos = hit + 1
    if not found:
        print(f"::error::生产写点找不到锚点 {anchor!r} 的字符串字面量出现，无法现场渲染。")
        sys.exit(1)
    body = expr_around(src, idx)
    if body is None:
        print(f"::error::锚点 {anchor!r} 周边找不到字符串拼接表达式。")
        sys.exit(1)
    return render_body(body, values)

# ---------- ③ 锚点白名单 POSITIVE（W56 原 7 条 + W57 三类非锚点写点） ----------
D = '7'
comp_line = lambda vals: render_call(comp_src, 'context compressed: ', vals)
POSITIVE = [
    ("ContextCompressor 新版压缩账本（现场渲染）", comp_line({'__default__': D})),
    ("ContextCompressor char_budget/char_target=Int.MAX_VALUE（现场渲染）",
     comp_line({'charBudget': '2147483647', 'charTarget': '2147483647', '__default__': D})),
    ("ContextCompressor 短数字形态（现场渲染）", comp_line({'__default__': '1'})),
]
m_file = re.search(r'FILE_NAME\s*=\s*"([^"]+)"', store_src)
file_name = m_file.group(1) if m_file else 'endpoints.json'
unreadable = render_call(store_src, 'endpoint_store=unreadable file=', {'FILE_NAME': file_name})
seed_failed = render_call(store_src, 'endpoint_store=seed_write_failed file=', {'FILE_NAME': file_name})
POSITIVE += [
    ("EndpointStore unreadable backup=true（现场渲染）", unreadable.replace('backup=' + D, 'backup=true')),
    ("EndpointStore unreadable backup=false（现场渲染）", unreadable.replace('backup=' + D, 'backup=false')),
    ("EndpointStore seed_write_failed（现场渲染）", seed_failed),
    ("ContextBudget before round（现场渲染）",
     render_call(budget_src, 'context compressed before round ', {'__default__': D})),
]
# W57 三类非锚点写点（locator 是源码里的字符串前缀，不是「应该有」的正则）
conv = open(os.path.join(repo, 'feature-chat/src/main/java/com/rickeal/liquidharness/feature/chat/ConversationOps.kt'),
            encoding='utf-8').read()
layer = open(os.path.join(repo, 'core-context/src/main/java/com/rickeal/liquidharness/core/agent/context/LayeredContextCompressor.kt'),
             encoding='utf-8').read()
# 锚点只取「稳定前缀」，不含会被同步改名的 token（如 reason= / dropped=）——
# 这样写点改形状 ⇒ 渲染样本失配判红，写点与正则同步改名 ⇒ 样本仍命中保持绿。
POSITIVE += [
    ("ConversationOps manual_compress=noop reason=（非锚点，现场渲染）",
     render_at(conv, 'manual_compress=noop', {'outcome.reason': 'x', '__default__': 'x'})),
    ("ConversationOps auto_compact=noop（非锚点，现场渲染）",
     render_at(conv, 'auto_compact=noop', {'decision.reason': 'x', '__default__': 'x'})),
    ("ConversationOps auto_compact applied（非锚点，现场渲染）",
     render_at(conv, 'auto_compact dropped=', {'__default__': D})),
    ("ConversationOps manual_compress applied（非锚点，现场渲染）",
     render_at(conv, 'manual_compress dropped=', {'__default__': D})),
    ("LayeredContextCompressor L1_FALLBACK（非锚点，现场渲染）",
     render_at(layer, 'layered compress: mode=L1_FALLBACK', {'reason': 'x', '__default__': 'x'})),
    ("LayeredContextCompressor L2（非锚点，现场渲染）",
     render_at(layer, 'layered compress: mode=L2', {'__default__': D})),
    ("LayeredContextCompressor l1 could not cut（非锚点，现场渲染）",
     render_at(layer, 'layered compress: l1 could not cut', {'__default__': D})),
]

# ---------- ④ 全库兜底扫描：os.walk 认领所有固定形态写点并渲染最小命中样本 ----------
def paren_body(src, open_idx):
    depth, in_str, esc = 0, False, False
    for i in range(open_idx, len(src)):
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
                return src[open_idx + 1:i]
    return None

def split_alt(s):
    """按顶层 `|` 拆分支（忽略嵌套组与转义里的 |）。"""
    parts, depth, buf, i = [], 0, '', 0
    while i < len(s):
        c = s[i]
        if c == '\\':
            buf += s[i:i + 2]
            i += 2
            continue
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
        elif c == '|' and depth == 0:
            parts.append(buf)
            buf = ''
            i += 1
            continue
        buf += c
        i += 1
    parts.append(buf)
    return parts

def leading_runs(s, i=0, buf=''):
    """从正则前导抽出所有可能的字面 run（展开开头/组内的 `|` 分支，遇元字符/转义停）。"""
    res = set()
    while i < len(s):
        c = s[i]
        if c == '(':
            depth, j = 1, i + 1
            while j < len(s) and depth > 0:
                if s[j] == '\\':
                    j += 2
                    continue
                if s[j] == '(':
                    depth += 1
                elif s[j] == ')':
                    depth -= 1
                j += 1
            inner = s[i + 1:j - 1]
            for br in split_alt(inner):
                for br_run in leading_runs(br):
                    res |= leading_runs(s, j, buf + br_run)
            return res
        if c in '\\^$*+?.[{':
            if len(buf) >= 6:
                res.add(buf)
            return res
        if c == '|':
            if len(buf) >= 6:
                res.add(buf)
            return res
        buf += c
        i += 1
    if len(buf) >= 6:
        res.add(buf)
    return res

CLAIM_ANCHORS = set()
for f in all_forms:
    CLAIM_ANCHORS |= leading_runs(f.lstrip('^'))

# ---------- W58 认领逃逸堵漏（P0-1）：显式基线 + 认领失败必红 ----------
# 认领基线与 EXPECTED_TESTS 同型：写成常数，波动即判红并要求归因，不允许认领数
# 只藏在 OK 行文本里被静默吞掉（外审 M 型：首字面量改名 15→14 一度 rc=0 假绿）。
EXPECTED_CLAIMS = 15

# 一次性豁免清单：这些写点当前不是 PROBE_FORMS 账行（自由文本诊断日志 / 不入落盘
# 白名单的固定形态），首字面量失配不算认领逃逸。按「写点首个字符串字面量」逐条显式
# 登记；任何写点首字面量改动（含新增写点）都会使豁免失配 ⇒ 判红，须重新登记并归因。
# 新增豁免必须在此显式登记，禁止把失配当「自由文本」静默 continue。
CLAIM_EXEMPT = {
    # 固定形态但不入 PROBE_FORMS 白名单（诊断/非取证账行，当前设计即不落盘）
    "compress_skipped=not_compressible prompt=",
    "prefix changed segments=",
    "hard_budget chars=",
    "hard_budget_shrunk dimension=",
    "context_limit_learned endpoint=",
    # 自由文本诊断日志（动态正文 / 工具名 / 路径，天然不可整行固定形态匹配）
    "first byte timeout: no event within ",
    " (attempt=",
    "stream ended without finish_reason; treating as truncated (chars=",
    "wall clock soft limit reached: elapsed=",
    "run failed: ",
    "approval satisfied by session cache: ",
    "approval card suppressed after ",
    "approval card could not be delivered within ",
    "approval timed out after ",
    "tool call refused without approval channel: ",
    "unknown tool requested: ",
    "tool blocked by policy: ",
    "tool ",
    "aggregated tool message capped: ",
    "provider reported no usage; estimated prompt tokens: ",
    "builtin tool overwrote a tool from another source: ",
    "refused to register contributed tool ",
    "tool output truncated: ",
    "missing tool results patched: ",
    "session import: ",
}

def candidates_for(tail):
    t = tail[-56:]
    pri = []
    if 'reason=' in t:
        pri += ['x', 'below_trigger', 'over-hard-budget']
    if 'backup=' in t or 'cache_invalidated=' in t:
        pri += ['true', 'false']
    if 'file=' in t:
        pri += [file_name]
    if 'fp=' in t:
        pri += ['abc123']
    if 'changed=' in t:
        pri += ['none']
    if 'cached=' in t:
        pri += ['n/a-usage']
    if 'endpoint=' in t:
        pri += ['x']
    pri += ['7', '1', 'x', 'true', 'false', file_name, 'abc123', 'none', 'n/a-usage', 'L1_FALLBACK']
    seen, out = set(), []
    for v in pri:
        if v not in seen:
            seen.add(v)
            out.append(v)
    return out[:8]

def fill_parts(parts):
    """为拼接表达式的变量位求一组填充值，使整行命中 PROBE_FORMS；命中则返回样本，否则 None。"""
    vars_idx = [i for i, p in enumerate(parts) if not is_literal(p)]
    if not vars_idx:
        rendered = render_body(' + '.join(parts), {})
        return rendered if mirrored(rendered) else None
    combos = [0] * len(vars_idx)
    cands = [candidates_for(parts[i - 1] if i > 0 else '') for i in vars_idx]
    tries = 0
    while tries < 20000:
        tries += 1
        values = {parts[vars_idx[k]]: cands[k][combos[k]] for k in range(len(vars_idx))}
        rendered = render_body(' + '.join(parts), values)
        if mirrored(rendered):
            return rendered
        k = len(vars_idx) - 1
        while k >= 0:
            combos[k] += 1
            if combos[k] < len(cands[k]):
                break
            combos[k] = 0
            k -= 1
        if k < 0:
            return None
    return None

def mirrored(line):
    m = line.strip()
    return len(m) <= cap and any(rx.fullmatch(m) for rx in compiled)

discovered = []
for root, dirs, files in os.walk(repo):
    dirs[:] = [d for d in dirs if d not in ('build', '.gradle', '.git', '.kotlin')]
    norm = root.replace('\\', '/')
    if '/src/main/' not in norm and not norm.endswith('/src/main'):
        continue
    for fname in files:
        if not fname.endswith('.kt'):
            continue
        path = os.path.join(root, fname)
        try:
            src = open(path, encoding='utf-8').read()
        except OSError:
            continue
        for m in re.finditer(r'AgentLogStore\.(info|warn|error|debug)\s*\(', src):
            body = paren_body(src, m.end() - 1)
            if not body:
                continue
            parts = [p for p in split_top_plus(body) if p]
            if not parts or not any(is_literal(p) for p in parts):
                continue  # 跳过纯变量位
            first = next((p for p in parts if is_literal(p)), '')
            first_lit = decode_literal(first) if is_literal(first) else ''
            # 认领判据：写点首个字面量必须是「正则前导 run 的前缀」或反之
            # （前缀长度 ≥ 6）。`hard_budget chars=` / `compress_skipped=` 虽含
            # `chars=` / `compress` 子串，但不是任何 run 的前缀关系，不误认领。
            def prefix_claim(a, b, n=6):
                return len(a) >= n and len(b) >= n and (a.startswith(b) or b.startswith(a))
            if not any(prefix_claim(first_lit, run) for run in CLAIM_ANCHORS):
                # W58：不再静默 continue。首字面量失配 = 该固定形态写点掉出认领集，
                # 除非它已在 CLAIM_EXEMPT 一次性豁免清单里显式登记，否则一律判红。
                if first_lit in CLAIM_EXEMPT:
                    continue
                print(f"RED  全库写点首字面量失配、未在豁免清单登记 | {os.path.relpath(path, repo)} | {first_lit!r}")
                print("::error::全库 AgentLogStore 固定形态写点首字面量不被 PROBE_FORMS 认领且未显式豁免（认领逃逸）。")
                sys.exit(1)
            sample = fill_parts(parts)
            if sample is None:
                print(f"RED  全库写点无法渲染命中样本 | {os.path.relpath(path, repo)} | {first_lit!r}")
                print("::error::全库 AgentLogStore 固定形态写点未被 PROBE_FORMS 认领（写点形状漂移）。")
                sys.exit(1)
            discovered.append((os.path.relpath(path, repo), sample))

# W58：认领基数显式基线判据。任一首字面量改名/写点增删导致认领数波动即判红并要求归因。
if len(discovered) != EXPECTED_CLAIMS:
    print(f"RED  全库认领写点数 {len(discovered)} != 基线 {EXPECTED_CLAIMS}（波动须归因：新增写点登记豁免/基线，或首字面量漂移）")
    print("::error::全库 AgentLogStore 认领写点数与显式基线不符。")
    sys.exit(1)

# ---------- ⑤ 反向哨兵（W55 既有 11 条，保持不动） ----------
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

# ---------- ⑥ A2 静态形态判据：redactHeaders 脱敏面（零新 @Test） ----------
# 方案席 caveat：全仓无 McpServerStoreTest、redactHeaders 为 private ⇒ 不走 JVM
# 新测试。改由本脚本做静态形态判据：定位函数体（花括号配平、跳字符串/注释），
# 体内必须同时含 Cookie / X-Auth-Token 两类字面量（ignoreCase）；删任一即 rc=1。
A2_STORE = os.path.join(repo, 'core-data/src/main/java/com/rickeal/liquidharness/core/data/McpServerStore.kt')
a2_bad = []
if not os.path.isfile(A2_STORE):
    a2_bad.append("找不到 McpServerStore.kt")
else:
    a2_src = open(A2_STORE, encoding='utf-8').read()
    mi = a2_src.find('private fun redactHeaders')
    if mi < 0:
        a2_bad.append("找不到 redactHeaders 函数")
    else:
        # 花括号配平抽函数体：跳过字符串字面量与 // 行注释里的括号
        ob = a2_src.find('{', mi)
        depth, end, in_str, esc, in_comment = 0, -1, False, False, False
        i = ob
        while i < len(a2_src):
            ch = a2_src[i]
            if in_comment:
                if ch == '\n':
                    in_comment = False
            elif in_str:
                if esc:
                    esc = False
                elif ch == '\\':
                    esc = True
                elif ch == '"':
                    in_str = False
            else:
                if ch == '"':
                    in_str = True
                elif ch == '/' and i + 1 < len(a2_src) and a2_src[i + 1] == '/':
                    in_comment = True
                elif ch == '{':
                    depth += 1
                elif ch == '}':
                    depth -= 1
                    if depth == 0:
                        end = i
                        break
            i += 1
        body = a2_src[ob + 1:end] if end > ob else ''
        # 空命中语义防线：函数体抽取失败（空体）⇒ 判红，不以「0 命中」冒充通过
        if not body.strip():
            a2_bad.append("redactHeaders 函数体抽取为空")
        else:
            # W59 第三层加固（K2/K3 假绿）：先剥 // 行注释再归一。
            # K2 把控制流串写在 `value // if (sensitive) REDACTED else value` 注释里
            #   ⇒ 旧版对含注释的函数体查子串仍命中 ⇒ rc=0 假绿。
            # K3 把控制流包进 `value.also { if (sensitive) REDACTED else value }`
            #   ⇒ 控制流在语法里但不是 mapValues lambda 的返回值（被 also 丢弃）⇒ 同样假绿。
            # 修法（外审 §3.2 处方，零新 @Test）：
            #   ① 剥注释用与 PROBE_FORMS 提取同款的字符串感知状态机（字符串内 // 不剥）；
            #   ② 归一函数体去空白后，要求以 `if(sensitive)redactedelsevalue` **收尾**
            #      —— mapValues 的 lambda 尾表达式即返回值，控制流必须是函数体最后一个
            #      返回表达式；串写在注释里（剥后消失）或被 also{} 包裹（收尾变成
            #      `}`）都判红。
            stripped_lines = []
            for bl in body.split('\n'):
                j, in_s, esc2 = 0, False, False
                out_seg = ''
                while j < len(bl):
                    ch = bl[j]
                    if in_s:
                        if esc2: esc2 = False
                        elif ch == '\\': esc2 = True
                        elif ch == '"': in_s = False
                        out_seg += ch
                    else:
                        if ch == '"':
                            in_s = True; out_seg += ch
                        elif ch == '/' and j + 1 < len(bl) and bl[j + 1] == '/':
                            break  # 行注释起点：截断本行
                        else:
                            out_seg += ch
                    j += 1
                stripped_lines.append(out_seg)
            body_nocomment = '\n'.join(stripped_lines)
            low = body_nocomment.lower()
            # 认领具名头「equals("Cookie") / equals("X-Auth-Token")」形态，而非泛化的
            # contains("cookie") —— 后者会掩盖具名头被删的变异（假绿）。两类都必在。
            for lit in ('equals("cookie"', 'equals("x-auth-token"'):
                if lit not in low:
                    a2_bad.append(f"redactHeaders 体内缺 {lit} 具名头形态")
            # W58 A2 控制流必红（P1-2，外审变异 K 坐实）：仅凭 val sensitive 里的两个
            # 具名头字面量在场还不够。外审把返回表达式 `if (sensitive) REDACTED else value`
            # 改成 `value // 控制流被摘除`（字面量段原地保留）⇒ 凭据明文落盘，旧判据
            # rc=0 误报「A2 两类齐备」。故要求：返回表达式中 sensitive 与 REDACTED 同时
            # 出现且构成 if/else 条件 branches（忽略空白/换行，容忍常量名大小写不敏感）。
            # 仍只认控制流形态：泛化 contains("cookie") 冒充具名头不予放行（防假绿不变）。
            cf = re.sub(r'\s+', '', low)
            # W59：控制流必须是函数体最后一个返回表达式（归一 body 以
            # if(sensitive)redactedelsevalue 收尾）。仅「子串在场」挡不住 K2（注释里）/K3（also 丢弃）。
            if not cf.endswith('if(sensitive)redactedelsevalue'):
                a2_bad.append("redactHeaders 返回表达式缺 if (sensitive) REDACTED else value 脱敏控制流分支（须为函数体最后返回表达式）")

bad = []
for name, line in POSITIVE:
    if not mirrored(line):
        bad.append(("必落盘却漏落盘", name, line))
for name, line in NEGATIVE:
    if mirrored(line):
        bad.append(("自由文本却穿透落盘", name, line))
for item in a2_bad:
    bad.append(("A2 凭据脱敏形态", item, ""))

if bad:
    for why, name, line in bad:
        print(f"RED  {why} | {name} | {line}")
    print(f"::error::PROBE_FORMS 同步自检判红：{len(bad)} 条失配")
    sys.exit(1)

print(f"[w59-sync] OK {len(POSITIVE)} 条锚点现场渲染样本命中，"
      f"{len(discovered)} 条全库认领写点命中，{len(NEGATIVE)} 条反向哨兵全拦下，"
      f"A2 脱敏形态 equals(Cookie)/equals(X-Auth-Token) 两类齐备 + 控制流为最后返回表达式")
PY
exit $?
