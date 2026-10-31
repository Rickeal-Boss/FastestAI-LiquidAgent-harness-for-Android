#!/usr/bin/env python3
"""
W2 止损判据机械判分。

真值源 = glm_bridge.jsonl（每个 HTTP 请求一行：完整 request + 解析后的 response）。
不读 UI、不读截图 —— UI 只是展示面，判据必须能复算。

判定口径（与 scripts/fixtures/w2-tool-prompts.json 对齐，并对三处夹具缺口做**显式**归一）：
  1. 千分位：题面写 "30000"，模型答 "30,000" ⇒ 判分前去掉数字间的逗号（两边都去）。
     这不是放宽判据，是修夹具的格式脆弱性；报告里单列 "normalized" 计数。
  2. 期望失败题（T10/T11/T16）：夹具 scoring.pass 写的是 "工具 ok=true"，但那三题的
     $note 明确要求「工具失败且模型不编造」。以 $note 为准，判据 = 工具被调用 +
     回灌内容是错误串 + 最终答复非空 + 无编造标记。
  3. 无工具题（T12/T13/T20）：判据 = 零 tool_calls + 最终答复非空。
"""

import json
import os
import re
import sys

HERE = os.getcwd()
FIXTURE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "fixtures", "w2-tool-prompts.json")
BRIDGE_LOG = os.environ.get("BRIDGE_LOG", os.path.join(HERE, "glm_bridge.jsonl"))
OUT_MD = os.path.join(HERE, "w2-stoploss-report.md")

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
    # 千分位：只在数字之间去逗号，避免破坏普通文本里的逗号。
    s = re.sub(r"(?<=\d),(?=\d{3}\b)", "", s)
    s = re.sub(r"\s+", " ", s)
    return s


def load_records():
    out = []
    with open(BRIDGE_LOG, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                out.append(json.loads(line))
            except Exception:
                continue
    return out


def tool_messages(record):
    msgs = (record.get("request") or {}).get("messages") or []
    return {m.get("tool_call_id"): (m.get("content") or "") for m in msgs if m.get("role") == "tool"}


def args_keys(raw):
    try:
        obj = json.loads(raw or "{}")
        return set(obj.keys()) if isinstance(obj, dict) else set()
    except Exception:
        return set()


def is_error(content):
    low = (content or "").lower()
    return any(p in low for p in ERROR_PATTERNS)


def score_one(item, records):
    pid = item["id"]
    expect = item.get("expect") or {}
    exp_tool = expect.get("tool")
    exp_args = expect.get("args") or []
    exp_final = expect.get("final_contains")
    max_rounds = int(expect.get("max_rounds") or 4)

    if not records:
        return {"id": pid, "status": "inject_fail", "reason": "no request reached bridge"}

    calls = []
    seen_ids = set()
    for r in records:
        for c in (r.get("response") or {}).get("tool_calls") or []:
            cid = c.get("id") or (c.get("name"), c.get("arguments"))
            if cid in seen_ids:
                continue
            seen_ids.add(cid)
            calls.append(c)
        # 第二真值源：App 自己回灌进下一轮请求的 assistant.tool_calls。
        # 桥的 SSE 解析在「末帧用 message 而非 delta」的供应商形态下会漏（实测 T11 seq44：
        # finish_reason=tool_calls 但 delta 里没有调用），而 App 侧一定看得到 ——
        # 判分必须以「App 实际执行了什么」为准，不以桥解析得到什么为准。
        for m in (r.get("request") or {}).get("messages") or []:
            if m.get("role") != "assistant":
                continue
            for c in m.get("tool_calls") or []:
                fn = c.get("function") or {}
                cid = c.get("id")
                if cid in seen_ids:
                    continue
                seen_ids.add(cid)
                calls.append({"id": cid, "name": fn.get("name"), "arguments": fn.get("arguments") or ""})
    final_text = (records[-1].get("response") or {}).get("text") or ""
    rounds = len(records)
    names = [c.get("name") for c in calls]

    result = {
        "id": pid,
        "rounds": rounds,
        "max_rounds": max_rounds,
        "tools": names,
        "final": final_text[:300],
        "normalized": False,
    }

    if rounds > 8:
        result.update(status="fail", category="not_converged", reason="rounds>8")
        return result
    if rounds > max_rounds:
        result.update(status="fail", category="not_converged", reason="rounds>max_rounds(%d)" % max_rounds)
        return result
    if not final_text.strip():
        result.update(status="fail", category="not_converged", reason="empty final answer")
        return result

    if exp_tool is None:
        if calls:
            result.update(status="fail", category="not_called",
                          reason="expected no tool, got " + ",".join(names))
            return result
        result.update(status="pass", category="ok")
        return result

    if exp_tool not in names:
        cat = "tool_name" if calls else "not_called"
        result.update(status="fail", category=cat, reason="expected %s, got %s" % (exp_tool, names))
        return result

    # 参数键：取该工具第一次调用的参数
    first = next(c for c in calls if c.get("name") == exp_tool)
    missing = [k for k in exp_args if k not in args_keys(first.get("arguments"))]
    if missing:
        result.update(status="fail", category="param", reason="missing args " + ",".join(missing))
        return result

    # 工具结果：在「下一份请求」的 tool 消息里按 callId 找
    call_id = first.get("id")
    content = None
    for r in records:
        tm = tool_messages(r)
        if call_id in tm:
            content = tm[call_id]
            break
    result["tool_result"] = (content or "")[:300]

    if pid in EXPECTED_FAILURE:
        if content is None:
            result.update(status="fail", category="param", reason="no tool result echoed")
            return result
        if not is_error(content):
            result.update(status="fail", category="param",
                          reason="expected tool failure, got success-looking output")
            return result
        bad = [m for m in FABRICATION.get(pid, []) if m in norm(final_text)]
        if bad:
            result.update(status="fail", category="param", reason="fabricated: " + ",".join(bad))
            return result
        if pid == "W2-T16" and not re.search(r"not found|no such file|does not exist|doesn't exist|missing", norm(final_text)):
            result.update(status="fail", category="param", reason="did not state file missing")
            return result
        result.update(status="pass", category="ok")
        return result

    if content is not None and is_error(content):
        result.update(status="fail", category="param", reason="tool failed: " + content[:120])
        return result

    if exp_final:
        a, b = norm(final_text), norm(exp_final)
        if b not in a:
            result.update(status="fail", category="param",
                          reason="final missing %r" % exp_final)
            return result
        result["normalized"] = (exp_final not in (final_text or ""))

    result.update(status="pass", category="ok")
    return result


def cache_stats(records, prompts):
    per = []
    for r in records:
        u = (r.get("response") or {}).get("usage") or {}
        p = u.get("prompt_tokens") or 0
        c = (u.get("prompt_tokens_details") or {}).get("cached_tokens") or 0
        per.append((r.get("seq"), r.get("last_user", "")[:40], p, c))
    total_p = sum(x[2] for x in per)
    total_c = sum(x[3] for x in per)
    hits = [x for x in per if x[3] > 0]
    return {
        "requests": len(per),
        "prompt_tokens": total_p,
        "cached_tokens": total_c,
        "hit_rate": (total_c / total_p) if total_p else 0.0,
        "first_hit": hits[0] if hits else None,
        "rows": per,
    }


def main():
    with open(FIXTURE, "r", encoding="utf-8") as f:
        fixture = json.load(f)
    prompts = fixture["prompts"]
    all_records = load_records()
    wanted = {p["prompt"].strip() for p in prompts}
    run_records = [r for r in all_records if (r.get("last_user") or "").strip() in wanted]

    results = []
    for item in prompts:
        rs = [r for r in run_records if (r.get("last_user") or "").strip() == item["prompt"].strip()]
        results.append(score_one(item, rs))

    passed = [r for r in results if r["status"] == "pass"]
    injected = [r for r in results if r["status"] != "inject_fail"]
    hist = {}
    for r in results:
        key = r.get("category") if r["status"] == "fail" else r["status"]
        hist[key] = hist.get(key, 0) + 1

    cache = cache_stats(run_records, prompts)

    lines = []
    lines.append("# W2 止损判据实跑报告（GLM-4.7 / 真机）\n")
    lines.append("| 指标 | 值 |")
    lines.append("|---|---|")
    lines.append("| 题面数 | %d |" % len(prompts))
    lines.append("| 实际发出请求的题 | %d |" % len(injected))
    lines.append("| **通过** | **%d/%d** |" % (len(passed), len(prompts)))
    lines.append("| 判据 1（≥18/20） | %s |" % ("✅ 达标" if len(passed) >= 18 else "❌ 未达标"))
    lines.append("| 判据 2（0 条 8 轮不收敛） | %s |" % ("✅" if hist.get("not_converged", 0) == 0 else "❌ %d 条" % hist.get("not_converged")))
    lines.append("")
    lines.append("## 归因直方图\n")
    lines.append("| 归因 | 条数 |")
    lines.append("|---|---|")
    for k in sorted(hist):
        lines.append("| %s | %d |" % (k, hist[k]))
    lines.append("")
    lines.append("## 逐题\n")
    lines.append("| # | 轮次/上限 | 工具 | 结论 | 归因 | 说明 |")
    lines.append("|---|---|---|---|---|---|")
    for r in results:
        lines.append("| %s | %s/%s | %s | %s | %s | %s |" % (
            r["id"], r.get("rounds", "-"), r.get("max_rounds", "-"),
            ",".join(r.get("tools") or []) or "-",
            "✅" if r["status"] == "pass" else ("⚠️ 未注入" if r["status"] == "inject_fail" else "❌"),
            r.get("category", "-"), (r.get("reason") or "")[:70].replace("|", "/")))
    lines.append("")
    lines.append("## 缓存命中（`prompt_tokens_details.cached_tokens`）\n")
    lines.append("| 指标 | 值 |")
    lines.append("|---|---|")
    lines.append("| 请求数 | %d |" % cache["requests"])
    lines.append("| Σ prompt_tokens | %d |" % cache["prompt_tokens"])
    lines.append("| Σ cached_tokens | %d |" % cache["cached_tokens"])
    lines.append("| 命中率 | %.1f%% |" % (cache["hit_rate"] * 100))
    if cache["first_hit"]:
        lines.append("| 首次命中 | seq=%s, prompt=%s, cached=%s |" % (cache["first_hit"][0], cache["first_hit"][2], cache["first_hit"][3]))
    else:
        lines.append("| 首次命中 | 无（本轮所有请求 cached=0） |")
    lines.append("")
    lines.append("> 实测边界：GLM-4.7 的最小可缓存前缀 ≈ **4096 tokens**、粒度 64 tokens（见 docs/notes/w25-prompt-cache-and-w3-design.md）。")
    lines.append("")

    report = "\n".join(lines)
    with open(OUT_MD, "w", encoding="utf-8") as f:
        f.write(report)
    with open(os.path.join(HERE, "w2-stoploss-score.json"), "w", encoding="utf-8") as f:
        json.dump({"results": results, "cache": cache, "hist": hist}, f, ensure_ascii=False, indent=2)
    print(report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
