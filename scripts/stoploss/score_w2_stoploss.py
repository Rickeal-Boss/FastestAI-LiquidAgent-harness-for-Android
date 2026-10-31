#!/usr/bin/env python3
"""
W2 止损判据机械判分。

真值源 = 桥日志（每个 HTTP 请求一行：完整 request + 解析后的 response）。
不读 UI、不读截图 —— UI 只是展示面，判据必须能复算。

三条口径（全部成文在 scripts/fixtures/w2-tool-prompts.json 的 $comment 里）：

  1. **轮次 = 逻辑轮**，不是 HTTP 请求数。
     `round = 1 + request.messages 长度单调递增的次数`。
     引擎层 GenerationRound(maxRetries=1) 的网络重试会重新 generate，同一逻辑轮的两次
     请求 messages 完全相同 —— 按请求数计轮会把一次重试算成一轮，凭空造出「不收敛」。
     报告分列 逻辑轮次 / HTTP 请求数 / 重试数。

  2. **判定语义只从夹具读**。判分器不认识任何题号：期望成功/失败、编造标记、
     最终答复必须命中的措辞，全在 `expect.*` 里。v1 那套 EXPECTED_FAILURE 集合 +
     `pid == "W2-T16"` 行内特例是「夹具装饰化」的典型 —— 改夹具不改结论。

  3. **run 边界 + 版本门**。只统计 manifest 里 bridge_run_id 对应的记录；
     manifest 缺失或 fixture_version 与夹具不符 ⇒ 拒绝判分。
     这道门防的是「拿旧日志按新口径翻案」。
"""

import json
import os
import re
import sys

import bridge_log

HERE = os.getcwd()
SCRIPTS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIXTURE = os.path.join(SCRIPTS, "fixtures", "w2-tool-prompts.json")
BRIDGE_LOG = os.environ.get("BRIDGE_LOG", os.path.join(HERE, "glm_bridge.jsonl"))
MANIFEST = os.environ.get("RUN_MANIFEST", os.path.join(HERE, "run_manifest.json"))
OUT_MD = os.path.join(HERE, "w2-stoploss-report.md")
OUT_JSON = os.path.join(HERE, "w2-stoploss-score.json")

# 只放「本仓工具层专属」的措辞：这些串不会出现在被读取的文件正文里。
# v1 的全局表含 "invalid" / "not found" / "denied" / "missing " —— 读一个内容里
# 恰好写着 invalid 的文件，就会被判成「工具执行失败」，且一律归 param 桶。
GLOBAL_TOOL_ERROR_MARKERS = [
    "escapes sandbox",
    "outside the sandbox",
    "path escapes",
    "disabled by policy",
    "tool failed",
    "unknown action",
    "permission denied by harness",
]

MAX_ROUNDS_HARD = 8


class Refused(Exception):
    """版本门 / 完整性门拒绝出报告。"""


def norm(text):
    s = (text or "").lower()
    # 千分位：只在数字之间去逗号，避免破坏普通文本里的逗号。
    s = re.sub(r"(?<=\d),(?=\d{3}\b)", "", s)
    s = re.sub(r"\s+", " ", s)
    return s


def load_fixture(path=FIXTURE):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_manifest(path=MANIFEST):
    if not os.path.exists(path):
        return None
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def check_gates(fixture, manifest):
    """版本门。没有 manifest 就没有「这批日志属于哪次跑分」的证据链。"""
    if manifest is None:
        raise Refused("缺少 run_manifest.json：无法确认这批日志由哪次跑分产生"
                      "（先跑 drive_w2_stoploss.py 生成，或用 RUN_MANIFEST 指定）")
    mv = manifest.get("fixture_version")
    fv = fixture.get("version")
    if mv != fv:
        raise Refused("fixture_version 不一致：manifest=%r，夹具=%r。"
                      "旧日志不能按新口径重算，必须重跑。" % (mv, fv))
    if not manifest.get("bridge_run_id"):
        raise Refused("run_manifest.json 缺少 bridge_run_id：无法把日志切到本次 run")


def compute_rounds(records):
    """轮次口径的唯一实现在 bridge_log（驱动与判分器共用，避免现场/报告打架）。"""
    return bridge_log.compute_rounds(records)


def tool_messages(record):
    msgs = (record.get("request") or {}).get("messages") or []
    return {m.get("tool_call_id"): (m.get("content") or "") for m in msgs if m.get("role") == "tool"}


def args_keys(raw):
    try:
        obj = json.loads(raw or "{}")
        return set(obj.keys()) if isinstance(obj, dict) else set()
    except Exception:
        return set()


def is_tool_error(content, extra_markers=()):
    low = (content or "").lower()
    return any(p in low for p in list(GLOBAL_TOOL_ERROR_MARKERS) + list(extra_markers or ()))


def available_tools(records):
    """本 run 内 request.tools 的工具名并集。

    供面（harness 提供给模型的工具清单）缺失时，模型根本无从调用 —— 这是
    harness_fault，不是模型失败。混进 not_called 会把「我们没给工具」记成模型的账。
    """
    names = set()
    for r in records:
        for t in (r.get("request") or {}).get("tools") or []:
            fn = (t or {}).get("function") or {}
            if fn.get("name"):
                names.add(fn["name"])
    return names


def collect_calls(records):
    """按时间序收集本 run 内出现过的 tool_calls（去重）。

    两个真值源并用：桥解析的 SSE（response.tool_calls），以及 App 自己回灌进下一轮
    请求的 assistant.tool_calls。后者是「App 实际执行了什么」——供应商在末帧用 message
    而非 delta 时桥会漏解析（实测 T11：finish_reason=tool_calls 但 delta 无调用）。
    """
    ordered = sorted(records, key=lambda r: (float(r.get("ts") or 0), int(r.get("seq") or 0)))
    calls = []
    seen = set()
    for r in ordered:
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
    return calls


def _hit_any(text_norm, markers):
    return [m for m in (markers or []) if norm(m) in text_norm]


def _check_fabrication(expect, final_text, result):
    bad = _hit_any(norm(final_text), expect.get("fabrication_markers"))
    if bad:
        result.update(status="fail", category="fabrication", reason="fabricated: " + ",".join(bad))
        return True
    return False


def _check_final_any(expect, final_text, result):
    markers = expect.get("final_any_of")
    if markers and not _hit_any(norm(final_text), markers):
        result.update(status="fail", category="final_mismatch",
                      reason="final missing any of " + ",".join(markers))
        return True
    return False


def _check_final_contains(expect, final_text, result):
    exp_final = expect.get("final_contains")
    if not exp_final:
        return False
    a, b = norm(final_text), norm(exp_final)
    if b not in a:
        result.update(status="fail", category="final_mismatch", reason="final missing %r" % exp_final)
        return True
    result["normalized"] = (exp_final not in (final_text or ""))
    return False


def _tool_result_of(records, call_id):
    ordered = sorted(records, key=lambda r: (float(r.get("ts") or 0), int(r.get("seq") or 0)))
    for r in ordered:
        tm = tool_messages(r)
        if call_id in tm:
            return tm[call_id]
    return None


def score_one(item, records, tools_available=None):
    pid = item["id"]
    expect = item.get("expect") or {}
    mode = expect.get("mode")
    exp_tool = expect.get("tool")
    exp_args = expect.get("args") or []
    max_rounds = int(expect.get("max_rounds") or 4)

    if not mode:
        return {"id": pid, "status": "fail", "category": "fixture_fault",
                "reason": "expect.mode missing (fixture v2 required)"}

    if not records:
        return {"id": pid, "status": "inject_fail", "category": "inject_fail",
                "reason": "no request reached bridge in this run"}

    rounds, http, retries = compute_rounds(records)
    calls = collect_calls(records)
    names = [c.get("name") for c in calls]
    ordered = sorted(records, key=lambda r: (float(r.get("ts") or 0), int(r.get("seq") or 0)))
    final_text = (ordered[-1].get("response") or {}).get("text") or ""

    result = {
        "id": pid,
        "mode": mode,
        "rounds": rounds,
        "http_requests": http,
        "retries": retries,
        "max_rounds": max_rounds,
        "tools": names,
        "final": final_text[:300],
        "normalized": False,
    }

    # 供面缺失优先归因：工具压根没提供给模型时，「没调到」不是模型的选择。
    if mode == "tool_required" and tools_available is not None and exp_tool not in tools_available:
        result.update(status="fail", category="harness_fault",
                      reason="工具 %r 不在 request.tools 清单里（供面缺失）" % exp_tool)
        return result

    if rounds > MAX_ROUNDS_HARD:
        result.update(status="fail", category="not_converged", reason="rounds>%d" % MAX_ROUNDS_HARD)
        return result
    if rounds > max_rounds:
        result.update(status="fail", category="not_converged",
                      reason="rounds>max_rounds(%d)" % max_rounds)
        return result
    if not final_text.strip():
        result.update(status="fail", category="empty_final", reason="empty final answer")
        return result

    if mode == "no_tool":
        if calls:
            result.update(status="fail", category="over_called",
                          reason="expected no tool, got " + ",".join(names))
            return result
        if _check_fabrication(expect, final_text, result):
            return result
        if _check_final_contains(expect, final_text, result):
            return result
        result.update(status="pass", category="ok")
        return result

    if mode == "refusal_ok":
        if not calls:
            # 不调工具也算对，但必须说清「为什么不做」——沉默不是拒绝。
            if not _hit_any(norm(final_text), expect.get("refusal_markers")):
                result.update(status="fail", category="final_mismatch",
                              reason="no tool call and no refusal wording")
                return result
            if _check_fabrication(expect, final_text, result):
                return result
            result["refused"] = "verbal"
            result.update(status="pass", category="ok")
            return result
        if exp_tool not in names:
            result.update(status="fail", category="tool_name",
                          reason="expected %s or verbal refusal, got %s" % (exp_tool, names))
            return result
        first = next(c for c in calls if c.get("name") == exp_tool)
        content = _tool_result_of(records, first.get("id"))
        result["tool_result"] = (content or "")[:300]
        if content is None:
            result.update(status="fail", category="param", reason="no tool result echoed")
            return result
        if expect.get("tool_ok") is False and not is_tool_error(content, expect.get("tool_error_markers")):
            result.update(status="fail", category="tool_not_failed",
                          reason="expected tool failure, got success-looking output")
            return result
        if _check_fabrication(expect, final_text, result):
            return result
        result["refused"] = "tool"
        result.update(status="pass", category="ok")
        return result

    if mode != "tool_required":
        result.update(status="fail", category="fixture_fault", reason="unknown expect.mode=%r" % mode)
        return result

    if exp_tool not in names:
        cat = "tool_name" if calls else "not_called"
        result.update(status="fail", category=cat, reason="expected %s, got %s" % (exp_tool, names))
        return result

    first = next(c for c in calls if c.get("name") == exp_tool)
    missing = [k for k in exp_args if k not in args_keys(first.get("arguments"))]
    if missing:
        result.update(status="fail", category="param", reason="missing args " + ",".join(missing))
        return result

    content = _tool_result_of(records, first.get("id"))
    result["tool_result"] = (content or "")[:300]

    if expect.get("tool_ok") is False:
        if content is None:
            result.update(status="fail", category="param", reason="no tool result echoed")
            return result
        if not is_tool_error(content, expect.get("tool_error_markers")):
            result.update(status="fail", category="tool_not_failed",
                          reason="expected tool failure, got success-looking output")
            return result
        if _check_fabrication(expect, final_text, result):
            return result
        if _check_final_any(expect, final_text, result):
            return result
        if _check_final_contains(expect, final_text, result):
            return result
        result.update(status="pass", category="ok")
        return result

    # 非期望失败题：工具真失败时归 tool_error，不归 param。
    # 参数错是模型的问题，执行失败可能是供面/沙箱的问题 —— 混在一个桶里归因就废了。
    if content is not None and is_tool_error(content, expect.get("tool_error_markers")):
        result.update(status="fail", category="tool_error", reason="tool failed: " + content[:120])
        return result

    if _check_fabrication(expect, final_text, result):
        return result
    if _check_final_any(expect, final_text, result):
        return result
    if _check_final_contains(expect, final_text, result):
        return result

    result.update(status="pass", category="ok")
    return result


def cache_stats(records):
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


def evaluate(fixture, records, manifest):
    """纯函数：夹具 + 已合并日志 + manifest ⇒ 结果字典。版本门在这里。"""
    check_gates(fixture, manifest)
    prompts = fixture["prompts"]
    run_id = manifest.get("bridge_run_id")
    run_records = bridge_log.filter_run(records, run_id)

    wanted = {p["prompt"].strip() for p in prompts}
    run_records = [r for r in run_records if (r.get("last_user") or "").strip() in wanted]

    tools_available = available_tools(run_records)
    results = []
    for item in prompts:
        rs = [r for r in run_records if (r.get("last_user") or "").strip() == item["prompt"].strip()]
        results.append(score_one(item, rs, tools_available))

    passed = [r for r in results if r["status"] == "pass"]
    injected = [r for r in results if r["status"] != "inject_fail"]
    hist = {}
    for r in results:
        key = r.get("category") if r["status"] != "pass" else "ok"
        hist[key] = hist.get(key, 0) + 1

    missing_tools = set()
    for item in prompts:
        e = item.get("expect") or {}
        if e.get("mode") == "tool_required" and e.get("tool") and e["tool"] not in tools_available:
            missing_tools.add(e["tool"])

    return {
        "fixture_version": fixture.get("version"),
        "run_id": run_id,
        "manifest": manifest,
        "results": results,
        "hist": hist,
        "cache": cache_stats(run_records),
        "thinking": thinking_evidence(run_records),
        "totals": {
            "prompts": len(prompts),
            "injected": len(injected),
            "passed": len(passed),
            "rounds": sum(r.get("rounds") or 0 for r in results),
            "http_requests": sum(r.get("http_requests") or 0 for r in results),
            "retries": sum(r.get("retries") or 0 for r in results),
        },
        "harness_missing_tools": sorted(missing_tools),
    }


def thinking_evidence(records):
    """thinking 是否真的开着 —— 用 `completion_tokens_details.reasoning_tokens` 反证。

    为什么需要这条：本仓的端点配置里**没有 thinking 开关**（实测请求体字段只有
    model/messages/stream/stream_options/temperature/top_p/max_tokens/tools/tool_choice），
    所以「thinking = 开」只能锚定成「端点默认行为」。归因波最忌未受控变量，
    因此这里不看 manifest 里的自述，只看响应里有没有 reasoning token：
    有 ⇒ 与 W2.5 实跑状态一致；一次都没有 ⇒ 标注「thinking 未行使」，
    这一轮的 20 题结果不可与 W2.5 直接比较（口径不一致，须在报告里显式说明）。
    """
    total = 0
    with_reasoning = 0
    reasoning_tokens = 0
    for r in records:
        usage = (r.get("response") or {}).get("usage") or {}
        if not usage:
            continue
        total += 1
        rt = (usage.get("completion_tokens_details") or {}).get("reasoning_tokens") or 0
        reasoning_tokens += rt
        if rt > 0:
            with_reasoning += 1
    return {
        "responses_with_usage": total,
        "responses_with_reasoning": with_reasoning,
        "reasoning_tokens": reasoning_tokens,
        "state": "on" if with_reasoning else ("unknown" if not total else "off"),
    }


def score_from_files(fixture_path=FIXTURE, log_spec=BRIDGE_LOG, manifest_path=MANIFEST):
    """生产路径：读夹具 → 版本门 → 读日志（含 seq 完整性）→ 判分。"""
    fixture = load_fixture(fixture_path)
    manifest = load_manifest(manifest_path)
    check_gates(fixture, manifest)
    records = bridge_log.load_records(log_spec)
    return evaluate(fixture, records, manifest)


def render(report):
    t = report["totals"]
    m = report["manifest"] or {}
    hist = report["hist"]
    lines = []
    lines.append("# W2 止损判据实跑报告（GLM-4.7 / 真机）\n")
    lines.append("| 指标 | 值 |")
    lines.append("|---|---|")
    lines.append("| fixture_version | %s |" % report["fixture_version"])
    lines.append("| bridge_run_id | %s |" % report["run_id"])
    lines.append("| model / thinking | %s / %s |" % (m.get("model", "-"), m.get("thinking", "-")))
    lines.append("| apk_sha256 | %s |" % (m.get("apk_sha256") or "-"))
    lines.append("| git_head | %s |" % (m.get("git_head") or "-"))
    lines.append("| started_at | %s |" % (m.get("started_at") or "-"))
    lines.append("| 题面数 | %d |" % t["prompts"])
    lines.append("| 实际发出请求的题 | %d |" % t["injected"])
    lines.append("| **通过** | **%d/%d** |" % (t["passed"], t["prompts"]))
    lines.append("| 判据 1（≥18/20） | %s |" % ("✅ 达标" if t["passed"] >= 18 else "❌ 未达标"))
    lines.append("| 判据 2（0 条 8 轮不收敛） | %s |" % ("✅" if hist.get("not_converged", 0) == 0 else "❌ %d 条" % hist.get("not_converged")))
    th = report.get("thinking") or {}
    lines.append("| thinking 反证 | %s（%d/%d 次响应带 reasoning_tokens，共 %d） |" % (
        {"on": "✅ 与 W2.5 一致（开）", "off": "❌ 未行使（与 W2.5 不可比）"}.get(th.get("state"), "⚠️ 无 usage 帧"),
        th.get("responses_with_reasoning", 0), th.get("responses_with_usage", 0), th.get("reasoning_tokens", 0)))
    lines.append("")
    lines.append("## 轮次口径（三个数分开看）\n")
    lines.append("| 指标 | 值 |")
    lines.append("|---|---|")
    lines.append("| 逻辑轮次 Σ | %d |" % t["rounds"])
    lines.append("| HTTP 请求数 Σ | %d |" % t["http_requests"])
    lines.append("| 网络重试数 Σ | %d |" % t["retries"])
    lines.append("")
    lines.append("> 逻辑轮次 = 1 + `request.messages` 长度单调递增的次数；重试的 messages 与上一请求相同，不计轮。")
    lines.append("")
    lines.append("## 归因直方图\n")
    lines.append("| 归因 | 条数 |")
    lines.append("|---|---|")
    for k in sorted(hist):
        lines.append("| %s | %d |" % (k, hist[k]))
    lines.append("")
    if report["harness_missing_tools"]:
        lines.append("## ⚠️ harness 供面缺失\n")
        lines.append("以下工具在本次 run 的 `request.tools` 里**完全不存在**，相关题目已归 `harness_fault`：")
        lines.append("")
        for name in report["harness_missing_tools"]:
            lines.append("- `%s`" % name)
        lines.append("")
    lines.append("## 逐题\n")
    lines.append("| # | 逻辑轮/上限 | HTTP | 重试 | 模式 | 工具 | 结论 | 归因 | 说明 |")
    lines.append("|---|---|---|---|---|---|---|---|---|")
    for r in report["results"]:
        lines.append("| %s | %s/%s | %s | %s | %s | %s | %s | %s | %s |" % (
            r["id"], r.get("rounds", "-"), r.get("max_rounds", "-"),
            r.get("http_requests", "-"), r.get("retries", "-"),
            r.get("mode", "-"),
            ",".join(r.get("tools") or []) or "-",
            "✅" if r["status"] == "pass" else ("⚠️ 未注入" if r["status"] == "inject_fail" else "❌"),
            r.get("category", "-"), (r.get("reason") or "")[:70].replace("|", "/")))
    lines.append("")
    cache = report["cache"]
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
    return "\n".join(lines)


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    fixture_path, log_spec, manifest_path = FIXTURE, BRIDGE_LOG, MANIFEST
    if "--fixture" in argv:
        fixture_path = argv[argv.index("--fixture") + 1]
    if "--log" in argv:
        log_spec = argv[argv.index("--log") + 1]
    if "--manifest" in argv:
        manifest_path = argv[argv.index("--manifest") + 1]

    try:
        report = score_from_files(fixture_path, log_spec, manifest_path)
    except Refused as e:
        print("REFUSED: %s" % e, file=sys.stderr)
        return 2
    except bridge_log.LogCorrupt as e:
        print("REFUSED: 日志数据损坏 —— %s" % e, file=sys.stderr)
        print("同一文件内 seq 必须唯一；请确认没有两个桥进程写同一个文件，或按 run 拆开日志。", file=sys.stderr)
        return 3
    except bridge_log.LogMissing as e:
        print("REFUSED: %s" % e, file=sys.stderr)
        return 4

    text = render(report)
    with open(OUT_MD, "w", encoding="utf-8") as f:
        f.write(text)
    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
