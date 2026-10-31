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
  4. 失败要留证据：截图 + uiautomator dump 落到 _w2tools/shots/。
"""

import json
import os
import re
import shlex
import subprocess
import sys
import time

# ADB 路径必须由环境变量给（本仓不假设 _j2env 的位置）；默认取 PATH 里的 adb。
ADB = os.environ.get("ADB", "adb")
PKG = "com.rickeal.liquidharness.debug"
FIXTURE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "fixtures", "w2-tool-prompts.json")
BRIDGE_LOG = os.environ.get("BRIDGE_LOG", os.path.join(os.getcwd(), "glm_bridge.jsonl"))
OUT_DIR = os.getcwd()
SHOTS = os.path.join(OUT_DIR, "shots")
RUN_LOG = os.path.join(OUT_DIR, "run_w2_stoploss.log")

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


def read_records():
    if not os.path.exists(BRIDGE_LOG):
        return []
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


def records_for(prompt):
    return [r for r in read_records() if (r.get("last_user") or "").strip() == prompt.strip()]


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


def wait_done(prompt, pid=""):
    """等到「该题的最后一个请求已终态 + 2.5s 静默」，期间自动处理审批卡。"""
    deadline = time.time() + 300
    last_change = time.time()
    last_count = 0
    last_poll = 0.0
    while time.time() < deadline:
        rs = records_for(prompt)
        if len(rs) != last_count:
            last_count = len(rs)
            last_change = time.time()
        # 只有「已有一批 tool_calls 在等执行」时才可能弹卡，避免每轮都 dump。
        if rs and ((rs[-1].get("response") or {}).get("tool_calls") or []) and time.time() - last_poll > 2.0:
            last_poll = time.time()
            maybe_approve(pid)
        if rs and is_terminal(rs[-1]) and time.time() - last_change >= 2.5:
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


def main():
    with open(FIXTURE, "r", encoding="utf-8") as f:
        fixture = json.load(f)
    prompts = fixture["prompts"]

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

    results = []
    for item in prompts:
        pid, prompt = item["id"], item["prompt"]
        t0 = time.time()
        if not send_prompt(pid, prompt):
            results.append({"id": pid, "inject": False, "error": "inject failed twice"})
            continue

        rs = wait_done(prompt, pid)
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
        log("%s rounds=%d finish=%s calls=%s text=%r %.1fs" % (
            pid, len(rs), resp.get("finish_reason"),
            [(c.get("name"), (c.get("arguments") or "")[:40]) for c in calls],
            (resp.get("text") or "")[:60], elapsed))
        results.append({"id": pid, "inject": True, "rounds": len(rs), "records": rs})

        # 等输入框清空（发送成功的旁证）；不阻塞主流程。
        time.sleep(0.5)

    with open(os.path.join(OUT_DIR, "run_w2_stoploss_raw.json"), "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    log("DONE %d/%d prompts" % (len(results), len(prompts)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
