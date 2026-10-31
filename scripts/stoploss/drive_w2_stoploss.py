#!/usr/bin/env python3
"""
W2 止损判据 20 题真机驱动。

链路：App(真机) → adb reverse 8765 → glm_bridge.py → BigModel GLM-4.7
判据来源：scripts/fixtures/w2-tool-prompts.json（题面 + 机械判定 + 归因分类）

设计要点（每条都对应一次真机事故）：
  1. **reverse 每轮重建**：本环境 adb daemon 会随宿主调用被回收，映射随之失效，
     设备侧报 ConnectException 而服务端一条日志都没有。发题前重放一次是唯一可靠判据。
  2. **题面注入必须自证**：桥日志里的 `last_user` 与 fixture 逐字比对，不一致立刻判该题
     「注入失败」——不许把「题面错了」算成模型失败。
  3. **完成判据看桥不看 UI**：最后一个请求无 tool_calls 且 finish_reason ∈ {stop,length}
     且此后 2.5s 无新请求。UI 的「正在输入」只是展示面。
  4. **只认本次 run**：桥启动时把 run_id 写到 `<BRIDGE_LOG>.run`，驱动开局读它并按它过滤。
     旧日志的尾条若是非终态，不按 run 过滤会让 wait_done 开局即满足（题面没注入就返回）
     或反过来傻等 300s —— 两种都是「上一轮的尾巴」在污染本轮。
  5. 失败要留证据：截图 + uiautomator dump 落到 _w2tools/shots/。
"""

import json
import os
import re
import shlex
import subprocess
import sys
import time

import bridge_log

# ADB 路径必须由环境变量给（本仓不假设 _j2env 的位置）；默认取 PATH 里的 adb。
ADB = os.environ.get("ADB", "adb")
PKG = "com.rickeal.liquidharness.debug"
FIXTURE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "fixtures", "w2-tool-prompts.json")
BRIDGE_LOG = os.environ.get("BRIDGE_LOG", os.path.join(os.getcwd(), "glm_bridge.jsonl"))
OUT_DIR = os.getcwd()
SHOTS = os.path.join(OUT_DIR, "shots")
RUN_LOG = os.path.join(OUT_DIR, "run_w2_stoploss.log")
MANIFEST = os.path.join(OUT_DIR, "run_manifest.json")

os.makedirs(SHOTS, exist_ok=True)


def log(msg):
    line = "[%s] %s" % (time.strftime("%H:%M:%S"), msg)
    print(line, flush=True)
    with open(RUN_LOG, "a", encoding="utf-8") as f:
        f.write(line + "\n")


def adb(*args, timeout=60, check=False):
    cmd = [ADB] + list(args)
    env = dict(os.environ)
    env["MSYS_NO_PATHCONV"] = "1"
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, env=env)
    if check and p.returncode != 0:
        raise RuntimeError("adb failed: %s\n%s\n%s" % (args, p.stdout, p.stderr))
    return p.stdout


def ui_dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=60)
    return adb("shell", "cat", "/sdcard/ui.xml", timeout=60)


def bounds_of(xml, predicate):
    for m in re.finditer(r"<node[^>]*>", xml):
        tag = m.group(0)
        if not predicate(tag):
            continue
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def find_input(xml):
    return bounds_of(xml, lambda t: 'class="android.widget.EditText"' in t)


def find_send(xml):
    return bounds_of(xml, lambda t: 'content-desc="发送"' in t)


def tap(x, y):
    adb("shell", "input", "tap", str(x), str(y), timeout=30)


def input_text(text):
    # adb shell 把 argv 拼成一条命令交给设备 sh；shlex.quote 保证题面原样到达 input。
    adb("shell", "input", "text", shlex.quote(text), timeout=60)


def reverse():
    try:
        adb("reverse", "tcp:8765", "tcp:8765", timeout=30)
    except Exception as e:
        log("WARN reverse failed: %r" % (e,))


RUN_ID = None  # 由 main() 从 <BRIDGE_LOG>.run 读取后填；None 时不做 run 过滤（离线复算用）


def read_records():
    try:
        return bridge_log.load_records(bridge_log.live_spec(BRIDGE_LOG))
    except bridge_log.LogCorrupt as e:
        # 数据已不可信（两个进程写同一文件 / 人工拼接）：继续跑只会产出无法归因的日志。
        log("FATAL log corrupt: %s" % e)
        raise
    except bridge_log.LogMissing:
        return []


def records_for(prompt):
    rs = bridge_log.filter_run(read_records(), RUN_ID)
    return [r for r in rs if (r.get("last_user") or "").strip() == prompt.strip()]


def is_terminal(record):
    if record.get("status") != 200:
        return True
    resp = record.get("response") or {}
    if resp.get("error"):
        return True
    calls = resp.get("tool_calls") or []
    if calls:
        return False
    finish = resp.get("finish_reason")
    if finish in ("stop", "length", "content_filter"):
        return True
    # 供应商怪癖（2026-10-30 实测 T04 第二轮）：GLM-4.7 偶发在 [DONE] 前不发
    # finish_reason / usage 帧（38 个 SSE 帧、正文完整）。此时「无工具调用 + 正文非空」
    # 就是这一轮的结束判据 —— 不许因此把整题挂到超时。
    return bool((resp.get("text") or "").strip())


def maybe_approve(pid):
    """审批卡自动放行（「允许一次」）。

    为什么必须有：T06/T08/T15 会触发 file_write / clipboard(set) 的审批卡；
    不点它会等满 `approvalTimeoutMillis`(5min) 后按拒绝处理 ⇒ 题面语义被改变
    （测的就不是「模型会不会写文件」而是「用户没在场」）。跑分脚本扮演「在场的用户」，
    且只点最保守的「允许一次」——不用「本会话允许」，避免把审批缓存也一起测掉。
    """
    try:
        xml = ui_dump()
    except Exception:
        return False
    if "请求授权" not in xml:
        return False
    pos = bounds_of(xml, lambda t: 'text="允许一次"' in t)
    if not pos:
        return False
    tap(*pos)
    log("%s APPROVED once at %s" % (pid, pos))
    return True


def wait_done(prompt, pid="", baseline=0):
    """等到「该题在本 run 内的请求数 > baseline，且最后一个请求已终态 + 2.5s 静默」。

    baseline 为什么不能是 0：本题在同一 run 内被重发（注入失败重试）时，上一次的
    终态记录会让 last_count 立刻「有变化且终态」，题面还没注入就返回。以开跑时刻的
    条数为基线，等于把「本次发送」和「历史残留」切开。
    """
    deadline = time.time() + 300
    last_change = time.time()
    last_count = baseline
    last_poll = 0.0
    while time.time() < deadline:
        rs = records_for(prompt)
        if len(rs) != last_count:
            last_count = len(rs)
            last_change = time.time()
        # 只有「已有一批 tool_calls 在等执行」时才可能弹卡，避免每轮都 dump。
        if len(rs) > baseline and ((rs[-1].get("response") or {}).get("tool_calls") or []) and time.time() - last_poll > 2.0:
            last_poll = time.time()
            maybe_approve(pid)
        if len(rs) > baseline and is_terminal(rs[-1]) and time.time() - last_change >= 2.5:
            return rs
        time.sleep(0.8)
    return records_for(prompt)


def screenshot(name):
    try:
        path = os.path.join(SHOTS, name + ".png")
        with open(path, "wb") as f:
            env = dict(os.environ)
            env["MSYS_NO_PATHCONV"] = "1"
            p = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True, timeout=60, env=env)
            f.write(p.stdout)
    except Exception as e:
        log("WARN screenshot failed: %r" % (e,))


def restart_app():
    adb("shell", "am", "force-stop", PKG, timeout=30)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP", timeout=30)
    adb("shell", "am", "start", "-W", "-n", PKG + "/com.rickeal.liquidharness.MainActivity", timeout=90)
    time.sleep(3)


def clear_workspace():
    # ⚠️ 必须把整条命令作为**一个** argv 传给 adb：拆成多个参数时 adb 用空格拼回，
    # 设备 sh 看到的是 `sh -c rm -rf ...`（-c 只吃 "rm"），删除静默不执行。
    adb("shell", "run-as %s sh -c 'rm -rf files/harness/workspace/*'" % PKG, timeout=60)


def ensure_idle(pid):
    """确保不在流式状态（发送键 = 发送，不是停止）。超时则重启应用。"""
    deadline = time.time() + 120
    while time.time() < deadline:
        try:
            xml = ui_dump()
        except Exception:
            return
        if 'content-desc="停止"' not in xml:
            return
        time.sleep(2)
    log("%s WARN still streaming after 120s; restarting app" % pid)
    restart_app()


def send_prompt(pid, prompt):
    """注入并发送一题。每题先点「新对话」——这是本协议的关键判据：

    为什么必须换新对话：
      1. 输入框残留会把上一题的文字拼进本题（实测 T08+T09 被拼成一条），题面就变了；
      2. 单会话连问 20 题时，历史里上一题的答复会改变本题的上下文（不是单工具成功率该测的东西）；
      3. 夹具的跨题依赖只落在**设备状态**（T08→T09 剪贴板、T06→T07 / T15→T19 工作区文件），
         换对话不影响它们。
    代价：跨 run 的 prompt cache 在本轮不可观测（每题前缀 ≈ 800 token < 4096 门槛）——
    这是有意识的取舍，缓存结论由独立的前缀实验给出。
    """
    for attempt in (1, 2):
        ensure_idle(pid)
        try:
            xml = ui_dump()
            new_chat = bounds_of(xml, lambda t: 'content-desc="新对话"' in t)
            if new_chat:
                tap(*new_chat)
                time.sleep(1.0)
            xml = ui_dump()
            inp = find_input(xml)
            if not inp:
                raise RuntimeError("no input box")
            tap(*inp)
            time.sleep(0.6)
            input_text(prompt)
            time.sleep(0.8)
            # reverse 必须在「点发送」前一刻重建：本环境 adb daemon 会随宿主调用被回收，
            # 映射随之失效，而设备侧只报 ConnectException、服务端一条日志都没有。
            reverse()
            xml = ui_dump()
            send = find_send(xml)
            if not send:
                raise RuntimeError("no send button")
            tap(*send)
            return True
        except Exception as e:
            log("%s INJECT_FAIL attempt=%d %r" % (pid, attempt, e))
            screenshot("%s_inject_fail_%d" % (pid, attempt))
            if attempt == 1:
                log("%s restarting app for a clean retry" % pid)
                restart_app()
    return False


def apk_sha256():
    """设备上安装的 APK 的 sha256 —— 判分报告要能指认「跑的是哪个包」。"""
    try:
        out = adb("shell", "pm", "path", PKG, timeout=30)
        path = ""
        for line in out.splitlines():
            if line.startswith("package:"):
                path = line.split(":", 1)[1].strip()
                break
        if not path:
            return ""
        digest = adb("shell", "sha256sum", path, timeout=60)
        return (digest.split() or [""])[0]
    except Exception as e:
        log("WARN apk sha256 failed: %r" % (e,))
        return ""


def git_head():
    try:
        p = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True, timeout=20,
                           cwd=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
        return (p.stdout or "").strip() if p.returncode == 0 else ""
    except Exception:
        return ""


def write_manifest(fixture_version, prompts_count, bridge_run_id, started_at):
    """跑分清单：判分器的版本门靠它。

    apk_sha256 / git_head 让「同一份报告」能被指认到具体产物；model / thinking 记的是
    操作者声明的配置（真值在 endpoints.json 里，宿主读不到设备侧那份，所以允许 env 覆盖）。

    ⚠️ thinking 这一项**本仓没有开关可钉**：实测请求体只有
    model/messages/stream/stream_options/temperature/top_p/max_tokens/tools/tool_choice，
    没有任何 thinking 字段 ⇒ 它的真值是「端点默认行为」。所以默认值写
    `endpoint_default_on`（= W2.5 实跑状态），而不是写成 `off` 假装关掉了；
    是否真的开着由判分器的 `thinking_evidence()` 用 reasoning_tokens 反证。
    """
    manifest = {
        "fixture_version": fixture_version,
        "apk_sha256": apk_sha256(),
        "git_head": git_head(),
        "model": os.environ.get("RUN_MODEL", "glm-4.7"),
        "thinking": os.environ.get("RUN_THINKING", "endpoint_default_on"),
        "bridge_run_id": bridge_run_id,
        "started_at": started_at,
        "prompts_count": prompts_count,
    }
    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    log("MANIFEST %s" % json.dumps(manifest, ensure_ascii=False))
    return manifest


def main():
    global RUN_ID
    with open(FIXTURE, "r", encoding="utf-8") as f:
        fixture = json.load(f)
    prompts = fixture["prompts"]

    # run 边界来自桥：没有 .run 文件说明桥不是按新协议起的（或根本没起）。
    # 这时开跑，日志会缺 run_id，判分器会拒绝出报告 —— 不如现在就说清楚。
    RUN_ID = bridge_log.read_run_id(BRIDGE_LOG)
    if not RUN_ID:
        log("FATAL 找不到 %s —— 先启动 glm_bridge.py（它会写 run_id）再发题"
            % bridge_log.run_file_for(BRIDGE_LOG))
        return 2
    log("bridge run_id=%s" % RUN_ID)

    # --start W2-T05 --no-reset：断点续跑。绝不重置会话/工作区（T06/T07、T08/T09 依赖
    # 前面题留下的状态），否则续跑会改变题面语义。
    argv = sys.argv[1:]
    start_id = None
    if "--start" in argv:
        start_id = argv[argv.index("--start") + 1]
    resume = "--no-reset" in argv
    if start_id:
        prompts = [p for p in prompts if p["id"] >= start_id]

    if not resume:
        # 起跑前清空对话与工作区：T05/T06/T07/T19 依赖「工作区初始为空」的语义。
        clear_workspace()
        restart_app()

    xml = ui_dump()
    pos = find_input(xml)
    if not pos:
        log("FATAL: 找不到输入框，UI dump 前 800 字符：\n" + xml[:800])
        return 2
    log("输入框坐标 %s" % (pos,))

    write_manifest(fixture.get("version"), len(prompts), RUN_ID,
                   time.strftime("%Y-%m-%dT%H:%M:%S"))

    results = []
    for item in prompts:
        pid, prompt = item["id"], item["prompt"]
        t0 = time.time()
        # 基线必须在发送**之前**取：本题若因注入失败被重发，上一次的记录会让
        # wait_done 立刻看到「有记录且终态」，题面还没到模型就返回。
        baseline = len(records_for(prompt))
        if not send_prompt(pid, prompt):
            results.append({"id": pid, "inject": False, "error": "inject failed twice"})
            continue

        rs = wait_done(prompt, pid, baseline)
        rs = rs[baseline:]
        injected = bool(rs) and (rs[0].get("last_user") or "").strip() == prompt.strip()
        elapsed = round(time.time() - t0, 1)
        if not rs:
            log("%s NO_REQUEST after %ss (题面可能未注入)" % (pid, elapsed))
            screenshot(pid + "_no_request")
            results.append({"id": pid, "inject": False, "error": "no request"})
            continue
        if not injected:
            log("%s INJECT_MISMATCH bridge_saw=%r" % (pid, rs[0].get("last_user")))
            results.append({"id": pid, "inject": False, "error": "mismatch", "seen": rs[0].get("last_user")})
            continue

        last = rs[-1]
        resp = last.get("response") or {}
        calls = resp.get("tool_calls") or []
        rounds, http, retries = bridge_log.compute_rounds(rs)
        log("%s rounds=%d(http=%d,retry=%d) finish=%s calls=%s text=%r %.1fs" % (
            pid, rounds, http, retries, resp.get("finish_reason"),
            [(c.get("name"), (c.get("arguments") or "")[:40]) for c in calls],
            (resp.get("text") or "")[:60], elapsed))
        results.append({"id": pid, "inject": True, "rounds": rounds,
                        "http_requests": http, "retries": retries, "records": rs})

        # 等输入框清空（发送成功的旁证）；不阻塞主流程。
        time.sleep(0.5)

    with open(os.path.join(OUT_DIR, "run_w2_stoploss_raw.json"), "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    log("DONE %d/%d prompts" % (len(results), len(prompts)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
