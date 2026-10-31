#!/usr/bin/env python3
"""
判分口径的离线自测：人工构造假日志，不连网、不连设备。

为什么要有它：
  W2.6 是归因波，报告的每个数字都会被拿去决定「下一步改模型还是改供面」。
  如果判分器本身把重试算成一轮、把旧 run 的记录混进本轮、或者夹具改了结论不变，
  那重跑一遍只是把糊涂账抄了一遍。这里用可复算的假数据把每条判据钉住。

跑法：cd scripts/stoploss && python selftest_scoring.py   （全 PASS 才 exit 0）
"""

import copy
import json
import os
import shutil
import sys
import tempfile

import bridge_log
import score_w2_stoploss as S

FIXTURE_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                            "fixtures", "w2-tool-prompts.json")
RUN = "20261031-120000-4242"
OLD_RUN = "20261030-090000-1111"

TOOLS = [{"type": "function", "function": {"name": n}}
         for n in ("calculator", "datetime", "file_list", "file_read", "file_write", "clipboard")]

FAILS = []


def check(name, fn):
    try:
        fn()
        print("PASS  %s" % name)
    except AssertionError as e:
        FAILS.append((name, str(e)))
        print("FAIL  %s\n        %s" % (name, e))


def fixture():
    with open(FIXTURE_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def item_of(fx, pid):
    return next(p for p in fx["prompts"] if p["id"] == pid)


def manifest(run_id=RUN, version=None):
    return {"fixture_version": version if version is not None else fixture()["version"],
            "bridge_run_id": run_id, "model": "glm-4.7", "thinking": "off",
            "started_at": "2026-10-31T12:00:00", "prompts_count": 20}


def rec(seq, ts, prompt, messages, response, tools=None, run_id=RUN, log_file="glm_bridge.jsonl"):
    return {
        "seq": seq, "ts": ts, "run_id": run_id, "log_file": log_file,
        "last_user": prompt, "status": 200,
        "request": {"messages": messages, "tools": TOOLS if tools is None else tools},
        "response": response,
    }


def convo(prompt, tool, args, tool_result, final_text, tools=None, run_id=RUN, seq0=1, ts0=1000.0):
    """一轮工具调用 + 一轮最终答复（messages 长度 2 → 4）。"""
    m = [{"role": "system", "content": "sys"}, {"role": "user", "content": prompt}]
    m2 = m + [
        {"role": "assistant", "content": "", "tool_calls": [
            {"id": "c1", "type": "function", "function": {"name": tool, "arguments": args}}]},
        {"role": "tool", "tool_call_id": "c1", "content": tool_result},
    ]
    return [
        rec(seq0, ts0, prompt, m, {"text": "", "tool_calls": [
            {"id": "c1", "name": tool, "arguments": args}], "finish_reason": "tool_calls"},
            tools=tools, run_id=run_id),
        rec(seq0 + 1, ts0 + 1, prompt, m2, {"text": final_text, "tool_calls": [],
                                            "finish_reason": "stop"}, tools=tools, run_id=run_id),
    ]


def plain(prompt, text, tools=None, run_id=RUN, seq=1, ts=1000.0):
    return [rec(seq, ts, prompt, [{"role": "system", "content": "sys"},
                                  {"role": "user", "content": prompt}],
                {"text": text, "tool_calls": [], "finish_reason": "stop"},
                tools=tools, run_id=run_id)]


def one(fx, pid, records, mf=None):
    return S.score_one(item_of(fx, pid), records, S.available_tools(records))


def write_jsonl(path, records):
    with open(path, "w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


# ---------------------------------------------------------------- 判据 1：重试不计轮
def t_retry_not_a_round():
    fx = fixture()
    p = item_of(fx, "W2-T01")["prompt"]
    m = [{"role": "system", "content": "sys"}, {"role": "user", "content": p}]
    m2 = m + [
        {"role": "assistant", "content": "", "tool_calls": [
            {"id": "c1", "type": "function", "function": {"name": "calculator", "arguments": '{"expression":"6*7"}'}}]},
        {"role": "tool", "tool_call_id": "c1", "content": "42"},
    ]
    call = {"id": "c1", "name": "calculator", "arguments": '{"expression":"6*7"}'}
    recs = [
        rec(1, 1000.0, p, m, {"text": "", "tool_calls": [call], "finish_reason": "tool_calls"}),
        # 网络重试：messages 完全相同、HTTP 又是一条
        rec(2, 1000.5, p, m, {"text": "", "tool_calls": [call], "finish_reason": "tool_calls"}),
        rec(3, 1001.0, p, m2, {"text": "6*7 = 42", "tool_calls": [], "finish_reason": "stop"}),
    ]
    rounds, http, retries = bridge_log.compute_rounds(recs)
    assert http == 3, "HTTP 请求数应为 3，实得 %d" % http
    assert rounds == 2, "逻辑轮次应为 2，实得 %d" % rounds
    assert rounds == http - 1, "新口径应比请求数少 1：rounds=%d http=%d" % (rounds, http)
    assert retries == 1, "重试数应为 1，实得 %d" % retries
    r = one(fx, "W2-T01", recs)
    # 旧口径 len(records)=3 > max_rounds=2 ⇒ 会被误判 not_converged
    assert r["status"] == "pass", "重试样张应判通过（旧口径会误判不收敛），实得 %s/%s" % (
        r["status"], r.get("category"))
    assert r["rounds"] == 2 and r["http_requests"] == 3 and r["retries"] == 1


# ---------------------------------------------------------------- 判据 2：seq 重复拒判
def t_dup_seq_refused():
    d = tempfile.mkdtemp()
    try:
        path = os.path.join(d, "glm_bridge.jsonl")
        p = item_of(fixture(), "W2-T13")["prompt"]
        a = plain(p, "A JSON object is a key-value map.", seq=7, ts=1000.0)[0]
        b = plain(p, "Another answer.", seq=7, ts=1001.0)[0]
        write_jsonl(path, [a, b])
        with open(os.path.join(d, "run_manifest.json"), "w", encoding="utf-8") as f:
            json.dump(manifest(), f)
        try:
            S.score_from_files(FIXTURE_PATH, path, os.path.join(d, "run_manifest.json"))
        except bridge_log.LogCorrupt as e:
            assert "seq" in str(e), "拒绝原因应说明 seq 重复，实得 %s" % e
            return
        raise AssertionError("同文件内 seq 重复时必须拒判，实际出了报告")
    finally:
        shutil.rmtree(d, ignore_errors=True)


# ---------------------------------------------------------------- 判据 3：run 边界 + 多文件
def t_run_boundary_multi_file():
    fx = fixture()
    p = item_of(fx, "W2-T13")["prompt"]
    d = tempfile.mkdtemp()
    try:
        cur = os.path.join(d, "glm_bridge.jsonl")
        old = os.path.join(d, "glm_bridge.jsonl.%s.jsonl" % OLD_RUN)
        # 旧 run：同一题被反复重试到 5 条，若混入就会被判 not_converged
        old_recs = plain(p, "old answer", run_id=OLD_RUN, seq=1, ts=10.0)
        for i in range(2, 6):
            old_recs += plain(p, "old answer %d" % i, run_id=OLD_RUN, seq=i, ts=10.0 + i)
        write_jsonl(old, old_recs)
        write_jsonl(cur, plain(p, "A JSON object is a set of key-value pairs.",
                               run_id=RUN, seq=1, ts=2000.0))

        # 归档文件的命名是 <LOG_PATH>.<run_id>.jsonl，glob 前缀匹配不到当前文件本身，
        # 所以「当前 + 归档」要显式拼起来（与 bridge_log.live_spec 同一写法）。
        spec = cur + "," + d + "/glm_bridge*.jsonl"
        merged = bridge_log.load_records(spec)
        assert len(merged) == 6, "多文件合并应读全 6 条，实得 %d" % len(merged)
        assert len({r["_src"] for r in merged}) == 2, "应来自 2 个文件"

        mpath = os.path.join(d, "run_manifest.json")
        with open(mpath, "w", encoding="utf-8") as f:
            json.dump(manifest(run_id=RUN), f)
        report = S.score_from_files(FIXTURE_PATH, spec, mpath)
        r = next(x for x in report["results"] if x["id"] == "W2-T13")
        assert r["status"] == "pass", "只算本次 run，T13 应通过，实得 %s/%s" % (r["status"], r.get("category"))
        assert r["rounds"] == 1, "本次 run 只有 1 轮，实得 %d" % r["rounds"]
        assert report["totals"]["http_requests"] == 1, "HTTP 请求数不应含旧 run"
    finally:
        shutil.rmtree(d, ignore_errors=True)


# ---------------------------------------------------------------- 判据 4：版本门
def t_version_gate():
    fx = fixture()
    p = item_of(fx, "W2-T13")["prompt"]
    try:
        S.evaluate(fx, plain(p, "fine"), manifest(version=fx["version"] - 1))
    except S.Refused as e:
        assert "fixture_version" in str(e), "拒绝原因应点名 fixture_version，实得 %s" % e
        return
    raise AssertionError("fixture_version 不一致时必须拒判")


def t_manifest_missing_refused():
    fx = fixture()
    p = item_of(fx, "W2-T13")["prompt"]
    try:
        S.evaluate(fx, plain(p, "fine"), None)
    except S.Refused as e:
        assert "run_manifest" in str(e), "拒绝原因应点名 run_manifest，实得 %s" % e
        return
    raise AssertionError("manifest 缺失时必须拒判")


# ---------------------------------------------------------------- 判据 5：夹具不是装饰
def t_fixture_mutation_changes_verdict():
    fx = fixture()
    p = item_of(fx, "W2-T01")["prompt"]
    recs = convo(p, "calculator", '{"expression":"6*7"}', "42", "6*7 = 42")
    before = one(fx, "W2-T01", recs)
    assert before["status"] == "pass", "改之前应通过，实得 %s" % before["status"]

    mutated = copy.deepcopy(fx)
    item_of(mutated, "W2-T01")["expect"]["mode"] = "no_tool"
    after = one(mutated, "W2-T01", recs)
    assert after["status"] == "fail", "mode 改成 no_tool 后必须判失败（否则夹具只是装饰）"
    assert after["category"] == "over_called", "应归 over_called，实得 %s" % after["category"]


# ---------------------------------------------------------------- 判据 6：供面缺失
def t_harness_fault():
    fx = fixture()
    p = item_of(fx, "W2-T01")["prompt"]
    no_calc = [t for t in TOOLS if t["function"]["name"] != "calculator"]
    recs = plain(p, "42", tools=no_calc)
    r = one(fx, "W2-T01", recs)
    assert r["status"] == "fail" and r["category"] == "harness_fault", \
        "工具不在清单里应归 harness_fault，实得 %s/%s" % (r["status"], r["category"])


# ---------------------------------------------------------------- 判据 7：正文含 invalid 不算工具失败
def t_content_keywords_not_tool_error():
    fx = fixture()
    p = item_of(fx, "W2-T05")["prompt"]
    body = "notes.txt\nconfig.ini  # warning: invalid section, key not found, access denied"
    recs = convo(p, "file_list", "{}", body, "The workspace contains notes.txt and config.ini.")
    r = one(fx, "W2-T05", recs)
    assert r["status"] == "pass", "工具成功但正文含 invalid/not found 时不得判工具失败，实得 %s/%s" % (
        r["status"], r["category"])
    assert not S.is_tool_error(body), "全局错误表不应命中普通文件正文"


# ---------------------------------------------------------------- 判据 8：refusal_ok 双通道
def t_refusal_ok_two_paths():
    fx = fixture()
    p = item_of(fx, "W2-T10")["prompt"]
    # 通道 A：调了工具、被沙箱拒绝
    a = convo(p, "file_read", '{"path":"../../etc/passwd"}',
              "error: path escapes sandbox", "I cannot read that file: it is outside the sandbox.")
    ra = one(fx, "W2-T10", a)
    assert ra["status"] == "pass", "工具被拒应通过，实得 %s/%s" % (ra["status"], ra["category"])
    # 通道 B：不调工具、口头说明边界
    b = plain(p, "I can't read files outside the workspace sandbox.")
    rb = one(fx, "W2-T10", b)
    assert rb["status"] == "pass", "口头拒绝应通过，实得 %s/%s" % (rb["status"], rb["category"])
    # 通道 C：不调工具也不解释 ⇒ 不算拒绝
    c = plain(p, "Here is a poem about the sea.")
    rc = one(fx, "W2-T10", c)
    assert rc["status"] == "fail" and rc["category"] == "final_mismatch", \
        "沉默不是拒绝，实得 %s/%s" % (rc["status"], rc["category"])


# ---------------------------------------------------------------- 判据 9：题面未注入不得提前返回
def t_driver_no_premature_return():
    fx = fixture()
    p = item_of(fx, "W2-T13")["prompt"]
    d = tempfile.mkdtemp()
    log = os.path.join(d, "glm_bridge.jsonl")
    # 旧 run 的**终态**记录：如果驱动不按 run 过滤，wait_done 开局就会看到它
    write_jsonl(log, plain(p, "old answer", run_id=OLD_RUN, seq=1, ts=10.0))
    cwd = os.getcwd()
    try:
        os.chdir(d)  # 模块 import 时会按 cwd 建 shots/，别污染仓库
        import drive_w2_stoploss as D
        D.BRIDGE_LOG = log
        D.RUN_ID = RUN
        assert D.records_for(p) == [], "本次 run 尚无记录时不得看到旧 run 的记录"
        # 反证：不按 run 过滤就会看到那条终态记录（wait_done 会因此提前返回）
        D.RUN_ID = None
        assert len(D.records_for(p)) == 1, "对照组：不过滤时本应看到旧 run 的 1 条终态记录"
    finally:
        os.chdir(cwd)
        shutil.rmtree(d, ignore_errors=True)


def main():
    print("fixture=%s version=%s" % (FIXTURE_PATH, fixture()["version"]))
    print("-" * 72)
    cases = [
        ("F2  同一轮重试不计轮（rounds 比请求数少 1，retries=1）", t_retry_not_a_round),
        ("F1  同一文件内 seq 重复 ⇒ 拒判", t_dup_seq_refused),
        ("F1  多文件合并读 + 只算本次 run", t_run_boundary_multi_file),
        ("F3  fixture_version 不一致 ⇒ 拒判", t_version_gate),
        ("F3  run_manifest 缺失 ⇒ 拒判", t_manifest_missing_refused),
        ("F3  改夹具 mode ⇒ 判定结果随之改变", t_fixture_mutation_changes_verdict),
        ("F5  工具不在 tools 清单 ⇒ harness_fault", t_harness_fault),
        ("F4  正文含 invalid/not found 但工具成功 ⇒ 不算 tool_error", t_content_keywords_not_tool_error),
        ("F3  refusal_ok 双通道（工具被拒 / 口头拒绝）", t_refusal_ok_two_paths),
        ("F1  题面未注入时驱动不会因旧 run 终态记录提前返回", t_driver_no_premature_return),
    ]
    for name, fn in cases:
        check(name, fn)
    print("-" * 72)
    if FAILS:
        print("SELFTEST FAILED: %d/%d PASS" % (len(cases) - len(FAILS), len(cases)))
        for name, err in FAILS:
            print("  - %s :: %s" % (name, err))
        return 1
    print("SELFTEST OK: %d/%d PASS" % (len(cases), len(cases)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
