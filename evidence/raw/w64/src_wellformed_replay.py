#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""W64 · wellFormed 合流形态判据：Python 逐字等价复刻（外审 P1-1 样本）。

口径：与现役 core-agent/.../AgentRunJournal.kt::wellFormed 逐字等价——
  field(line,"runId")  = indexOf("runId=") 后到首个空格
  runId 形态           = 32 位小写 hex
  lastIndexOf(" at=")  = 尾部；tail 非空且全数字
  W64 新增             = line.split(" at=") 计数必须恰好 1

正控先行：完整行 / at=17 / 截断 payload 三样本与 W63 e4 判据同向，先跑证明判据会响。
必红正控：合流样本在 W63 旧判据（无 at= 计数约束）下 True ⇒ 证明约束真正改变判定。
只输出计数与布尔，不落任何账号/端点/路径字面。
"""

ID = "110eda9612345678abcdef9012345678"


def field(line: str, key: str):
    needle = key + "="
    at = line.find(needle)
    if at < 0:
        return None
    start = at + len(needle)
    end = start
    while end < len(line) and line[end] != " ":
        end += 1
    if end == start:
        return None
    value = line[start:end]
    return value if not any(ch.isspace() for ch in value) else None


def is_hex32(value: str) -> bool:
    return len(value) == 32 and all(ch in "0123456789abcdef" for ch in value)


def well_w63(line: str, kind: str) -> bool:
    """W63 旧判据（无 at= 计数约束）—— 必红正控用。"""
    if not line.startswith(kind + " "):
        return False
    rid = field(line, "runId")
    if rid is None or not is_hex32(rid):
        return False
    at = line.rfind(" at=")
    if at < 0:
        return False
    tail = line[at + 4:]
    return bool(tail) and all(ch.isdigit() for ch in tail)


def well_w64(line: str, kind: str) -> bool:
    """W64 现役判据：加 at= 恰好一次。"""
    if not well_w63(line, kind):
        return False
    return line.count(" at=") == 1


started = f"run_started runId={ID} inputChars=12 at=1795783954214"
settled = f"run_settled runId={ID} activeMs=103 cancelled=false at=1795783999000"
conflated = started + f"run_settled runId={'f' * 32} activeMs=99 at=1795783999000"
conflated_space = started + f" run_settled runId={'f' * 32} activeMs=99 at=1795783999000"
truncated_id = "run_started runId=110eda96 inputChars=12 at=1795783954214"
truncated_payload = f"run_settled runId={ID} activeMs=103 cancelled="
at17 = f"run_started runId={ID} inputChars=12 at=17"

samples = [
    ("S1 完整 run_started（正控）", started, "run_started"),
    ("S2 完整 run_settled（正控）", settled, "run_settled"),
    ("S3 at=17 截断（限定 1 宽容）", at17, "run_started"),
    ("S4 runId 截断成 8 hex", truncated_id, "run_started"),
    ("S5 尾部 payload 截断（缺 at=）", truncated_payload, "run_settled"),
    ("S6 合流：前完整缺 \\n 后整行接 at= 尾", conflated, "run_started"),
    ("S7 合流：同上但 at= 后有空格", conflated_space, "run_started"),
    ("S8 合流行对 settled 侧", conflated, "run_settled"),
]

print("=== W64 wellFormed Python 逐字等价复刻 ===")
print("样本 | W63旧判据 | W64现役判据 | 期望(W64)")
expected = {
    "S1": True, "S2": True, "S3": True, "S4": False,
    "S5": False, "S6": False, "S7": False, "S8": False,
}
fail = 0
for label, line, kind in samples:
    old = well_w63(line, kind)
    new = well_w64(line, kind)
    key = label[:2]
    exp = expected[key]
    ok = new == exp
    if not ok:
        fail += 1
    print(f"{label} | {old} | {new} | {exp} | {'OK' if ok else 'BAD'}")
print(f"\nW64 判据自洽: {'OK' if fail == 0 else 'BAD(' + str(fail) + ')'}")
print(f"必红正控 S6/S7 在 W63 旧判据下 = "
      f"{well_w63(conflated, 'run_started')}/{well_w63(conflated_space, 'run_started')} "
      f"(必须 True，证明 W64 约束真正改变判定)")
