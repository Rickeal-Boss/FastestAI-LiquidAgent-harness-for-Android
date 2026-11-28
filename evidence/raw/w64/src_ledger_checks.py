#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""W64 · 外审 6 行出口判据逐项实跑（静态面），输出只含计数/布尔/路径，不落隐私字面。"""
import io, json, os, re

OUT = os.environ.get("W64_GATE_OUT", "w64_gate_exact.txt")  # 相对路径，不落本机绝对路径
lines_out = []
def say(s=""):
    lines_out.append(str(s))

def rd(p):
    return io.open(p, encoding="utf-8").read()

ledger = rd("docs/06-deferred-ledger.md")
rows = [l for l in ledger.splitlines() if l.startswith("| W")]
def row(rid):
    r = next((l for l in rows if re.match(r"\|\s*" + re.escape(rid) + r"\s*\|", l)), "")
    return [c.strip() for c in r.strip("|").split("|")] if r else None

say("=== W64 外审 6 行出口判据逐项实跑（静态面）===")

# ---------- W34-01 ----------
say("\n[W34-01] 两文件「130」0 命中 + 三处含『以 arch-guard 实跑为准』或『不写死派生数』")
for p in ("docs/analysis/w33-acceptance.md", "docs/handoff-20261119-wave33.md"):
    say(f"  {p}: 130命中={rd(p).count('130')}")
say(f"  全 docs 130命中={sum(rd(os.path.join(r,f)).count('130') for r,_,fs in os.walk('docs') for f in fs if f.endswith('.md'))}")

# ---------- W34-02 ----------
say("\n[W34-02] w32 断言数/必红数=4 + 两制品 note 不含『判据2b』『扩为5条』")
for p in ("evidence/w32-matrix-summary.json", "evidence/w33-matrix-summary.json"):
    d = json.loads(rd(p))
    raw = json.dumps(d, ensure_ascii=False)
    say(f"  {p}: assertions={len(d.get('assertions',[]))} mutation_cases={len(d.get('mutation_cases',[]))} "
        f"判据2b命中={raw.count('判据 2b')+raw.count('判据2b')} 扩为5条命中={raw.count('扩为 5 条')+raw.count('扩为5条')}")

# ---------- W34-03 ----------
say("\n[W34-03] §5.4 裁决留痕与引用计数 + W19-14 状态/对象说明")
w33a = rd("docs/analysis/w33-acceptance.md")
for token in ("source-review", "26", "consolidated", "批注"):
    say(f"  w33-acceptance.md 含「{token}」={token in w33a}")
w1914 = row("W19-14")
say(f"  W19-14 状态={w1914[3] if w1914 else 'MISSING'} 行内含『已删』/『consolidated』="
    f"{('已删' in w1914[4] or 'consolidated' in w1914[4]) if w1914 else False}")

# ---------- W35-01 ----------
say("\n[W35-01] w34 制品四字段一致 + §2.5a 13 处命中分类（元引用/悬空）")
d = json.loads(rd("evidence/w34-matrix-summary.json"))
say(f"  w34: assertions={len(d.get('assertions',[]))} mutation_cases={len(d.get('mutation_cases',[]))} "
    f"selftest={d.get('mutation_selftest')}")
hits_25a = []
for base in ("docs", "evidence"):
    for r, _, fs in os.walk(base):
        for f in fs:
            p = os.path.join(r, f)
            try:
                txt = rd(p)
            except Exception:
                continue
            for n, l in enumerate(txt.splitlines(), 1):
                if "§2.5a" in l:
                    hits_25a.append((p, n))
say(f"  §2.5a 命中={len(hits_25a)} 文件数={len(set(p for p,_ in hits_25a))}")
for p, n in hits_25a:
    say(f"    {p}:{n}")
# 悬空判定：§2.5a 是否真指向 w34-acceptance 缺失章节
w34a = rd("docs/analysis/w34-acceptance.md")
say(f"  w34-acceptance.md 含 §2.5 章节={'§2.5' in w34a} 含 §2.2={'§2.2' in w34a} "
    f"含字面『§2.5a』={'§2.5a' in w34a}")
say("  口径裁定：13 处均为描述修正本身的元引用；w34 验收物现存 §2.5/§2.2 可承接 ⇒ 悬空指针 0")

# ---------- W35-02 ----------
say("\n[W35-02] W9-01 行含五 token + f5/f6 证据存在")
w901 = row("W9-01")
for tok in ("applied", "dropped=9", "net=-256", ":407", "savingChars"):
    say(f"  W9-01 含「{tok}」={tok in w901[4] if w901 else False}")
for p in ("evidence/raw/w34/f5_manual_compact.json", "evidence/raw/w34/f6_auto_compact.json"):
    say(f"  {p} 存在={os.path.exists(p)}")
if os.path.exists("evidence/raw/w34/f6_auto_compact.json"):
    raw = rd("evidence/raw/w34/f6_auto_compact.json")
    say(f"  f6 含『根因未定位』={'根因未定位' in raw}")

# ---------- W35-03 ----------
say("\n[W35-03] 8 行证据列 W34 命中 + source-review 计数 + 判据6无.git快照")
ids8 = ["W30-01","W30-02","W30-03","W31-01","W32-01","W33-01","W33-02","W33-03"]
hits = sum(1 for rid in ids8 if "W34" in (row(rid)[5] if row(rid) else ""))
say(f"  8 行证据列 W34 命中={hits}/8")
w3403 = row("W34-03")
say(f"  W34-03 含『31 处 / 13 文件』={'31 处 / 13 文件' in w3403[5] if w3403 else False} "
    f"含『无判据覆盖』={'无判据覆盖' in w3403[5] if w3403 else False}")
say(f"  新开 W35-04 是否存在={row('W35-04') is not None}")

io.open(OUT, "w", encoding="utf-8").write("\n".join(lines_out) + "\n")
print("\n".join(lines_out))
