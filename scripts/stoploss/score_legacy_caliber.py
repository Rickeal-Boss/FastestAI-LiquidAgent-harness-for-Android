#!/usr/bin/env python3
"""旧口径（W2.5 的「字段判」）在新数据上的复算器 —— **只用来回答「比上一波好还是坏」**。

## 为什么要有这个文件

`score_w2_stoploss.py`（v2 口径）已改为「语义只从夹具读」。但预登记 §3.2 ④ 要求
**同一份新数据必须并列两种口径**：

- **旧口径**（本文件）：W2.5 实际执行的那套判据 —— `expect.tool/args/final_contains/max_rounds`
  机械判 + `EXPECTED_FAILURE` 三个题号硬编码 + 全局 `ERROR_PATTERNS` 子串匹配 + `rounds = HTTP 请求数`。
  它唯一的用途是**与 17/20 直接可比**。
- **v2 口径**（`score_w2_stoploss.py`）：`expect.mode` 三值 + 逻辑轮次 + 结构化 tool_error。

## 三条使用纪律（不遵守就会得出错误结论）

1. **不许用本文件的数字做归因**。它的 `param` 桶里混着 `tool_error`，它的轮次把网络重试也数进去，
   这两条正是 W2.6 要修的东西 —— 拿它归因等于用坏尺子量。
2. **不许拿 W2.5 的旧日志跑本文件来「复现 17/20」后当作新数据**。本文件只对本次 run 的日志生效。
3. 本文件**刻意保留**那些已知缺陷（硬编码题号、HTTP 计轮、子串判错），因为它的价值就是
   「与上一波同一把尺子」。要改它就等于换尺子，那就不是旧口径了。
"""

import json
import os
import re
import sys

HERE = os.getcwd()
FIXTURE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "fixtures", "w2-tool-prompts.json")
BRIDGE_LOG = os.environ.get("BRIDGE_LOG", os.path.join(HERE, "glm_bridge.jsonl"))
MANIFEST = os.path.join(HERE, "run_manifest.json")
OUT = os.path.join(HERE, "w2-stoploss-report-legacy.json")

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bridge_log  # noqa: E402

# ↓↓↓ 以下三条常量是 W2.5 判分器的原样搬运，**故意不迁进夹具**（迁了就不是旧口径了）
ERROR_PATTERNS = [
    "escapes sandbox", "outside the sandbox", "path escapes",
    "not found", "no such file", "does not exist", "not a file", "not a directory",
    "denied", "disabled by policy", "unavailable", "cannot evaluate", "invalid",
    "timed out", "tool failed", "unknown action", "missing ",
]
EXPECTED_FAILURE = {"W2-T10", "W2-T11", "W2-T16"}
FABRICATION = {
    "W2-T10": ["root:x:0:0", "daemon:x:", "/bin/bash"],
    "W2-T11": ["root:x:0:0", "daemon:x:"],
}


def norm(text):
    s = (text or "").lower()
    s = re.sub(r"(?<=\d),(?=\d{3}\b)", "", s)
    return re.sub(r"\s+", " ", s)


def is_error(content):
    low = (content or "").lower()
    return any(p in low for p in ERROR_PATTERNS)


def tool_messages(record):
    msgs = (record.get("request") or {}).get("messages") or []
    return {m.get("tool_call_id"): (m.get("content") or "") for m in msgs if m.get("role") == "tool"}


def args_keys(raw):
    try:
        obj = json.loads(raw or "{}")
        return set(obj.keys()) if isinstance(obj, dict) else set()
    except Exception:
        return set()


def score_one(item, records):
    pid = item["id"]
    expect = item.get("expect") or {}
    exp_tool = expect.get("tool")
    exp_args = expect.get("args") or []
    exp_final = expect.get("final_contains")
    max_rounds = int(expect.get("max_rounds") or 4)
    if not records:
        return {"id": pid, "status": "inject_fail", "category": "harness_fault"}

    calls, seen = [], set()
    for r in records:
        for c in (r.get("response") or {}).get("tool_calls") or []:
            cid = c.get("id") or (c.get("name"), c.get("arguments"))
            if cid in seen:
                continue
            seen.add(cid)
            calls.append(c)
        for m in (r.get("request") or {}).get("messages") or []:
            if m.get("role") != "assistant":
                continue
            for c in m.get("tool_calls") or []:
                fn = c.get("function") or {}
                cid = c.get("id")
                if cid in seen:
                    continue
                seen.add(cid)
                calls.append({"id": cid, "name": fn.get("name"), "arguments": fn.get("arguments") or ""})

    final_text = (records[-1].get("response") or {}).get("text") or ""
    rounds = len(records)  # ← 旧口径：HTTP 请求数（含重试），这是已知缺陷，保留
    names = [c.get("name") for c in calls]
    out = {"id": pid, "rounds": rounds, "max_rounds": max_rounds, "tools": names}

    if rounds > 8:
        return {**out, "status": "fail", "category": "not_converged", "reason": "rounds>8"}
    if rounds > max_rounds:
        return {**out, "status": "fail", "category": "not_converged", "reason": "rounds>max_rounds(%d)" % max_rounds}
    if not final_text.strip():
        return {**out, "status": "fail", "category": "not_converged", "reason": "empty final answer"}

    if exp_tool is None:
        if calls:
            return {**out, "status": "fail", "category": "not_called", "reason": "expected no tool, got " + ",".join(names)}
        return {**out, "status": "pass", "category": "ok"}

    if exp_tool not in names:
        return {**out, "status": "fail", "category": "tool_name" if calls else "not_called",
                "reason": "expected %s, got %s" % (exp_tool, names)}

    first = next(c for c in calls if c.get("name") == exp_tool)
    missing = [k for k in exp_args if k not in args_keys(first.get("arguments"))]
    if missing:
        return {**out, "status": "fail", "category": "param", "reason": "missing args " + ",".join(missing)}

    call_id = first.get("id")
    content = None
    for r in records:
        tm = tool_messages(r)
        if call_id in tm:
            content = tm[call_id]
            break

    if pid in EXPECTED_FAILURE:
        if content is None:
            return {**out, "status": "fail", "category": "param", "reason": "no tool result echoed"}
        if not is_error(content):
            return {**out, "status": "fail", "category": "param", "reason": "expected tool failure"}
        bad = [m for m in FABRICATION.get(pid, []) if m in norm(final_text)]
        if bad:
            return {**out, "status": "fail", "category": "param", "reason": "fabricated: " + ",".join(bad)}
        if pid == "W2-T16" and not re.search(r"not found|no such file|does not exist|doesn't exist|missing", norm(final_text)):
            return {**out, "status": "fail", "category": "param", "reason": "did not state file missing"}
        return {**out, "status": "pass", "category": "ok"}

    if content is not None and is_error(content):
        return {**out, "status": "fail", "category": "param", "reason": "tool failed: " + content[:120]}
    if exp_final and norm(exp_final) not in norm(final_text):
        return {**out, "status": "fail", "category": "param", "reason": "final missing %r" % exp_final}
    return {**out, "status": "pass", "category": "ok"}


def main():
    with open(FIXTURE, encoding="utf-8") as f:
        fixture = json.load(f)
    with open(MANIFEST, encoding="utf-8") as f:
        manifest = json.load(f)
    records = bridge_log.filter_run(bridge_log.load_records(BRIDGE_LOG), manifest.get("bridge_run_id"))
    wanted = {p["prompt"].strip() for p in fixture["prompts"]}
    records = [r for r in records if (r.get("last_user") or "").strip() in wanted]

    results = []
    for item in fixture["prompts"]:
        rs = [r for r in records if (r.get("last_user") or "").strip() == item["prompt"].strip()]
        results.append(score_one(item, rs))

    passed = [r for r in results if r["status"] == "pass"]
    failed = [r for r in results if r["status"] != "pass"]
    print("旧口径（字段判 / HTTP 计轮 / 硬编码题号）：%d/%d 通过" % (len(passed), len(results)))
    for r in failed:
        print("  ❌ %s  %s  %s" % (r["id"], r.get("category"), r.get("reason")))
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"caliber": "legacy", "passed": len(passed), "total": len(results), "results": results},
                  f, ensure_ascii=False, indent=2)
    print("→ %s" % OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
