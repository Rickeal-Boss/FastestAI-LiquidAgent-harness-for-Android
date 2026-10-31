#!/usr/bin/env python3
"""
BigModel (GLM) 转发桥 —— W2 止损 20 题跑分的取证面。

设备 → adb reverse tcp:8765 → 本进程 → https://open.bigmodel.cn/api/paas/v4/chat/completions

为什么需要桥而不是让 App 直连：
  1. 跑分判据要看「每一轮的完整请求 + 完整响应」（tool_calls 名称/参数/轮次/usage），
     这些只存在于 HTTP 层；UI 截图与日志都给不出可机械判定的证据。
  2. 真实 Key 只在宿主进程的环境变量里，不进 APK、不进设备、不落盘。
  3. 顺带拿到 `prompt_tokens_details.cached_tokens`，用于上下文缓存命中率取证。

日志：每请求一行 JSON（BRIDGE_LOG，默认 glm_bridge.jsonl）。
  {run_id, log_file, seq, ts, path, model, last_user, status, duration_ms, first_byte_ms,
   request: {...原始 body...},
   response: {text, reasoning, tool_calls:[{id,name,arguments}], finish_reason, usage, sse_lines}}

run 边界为什么是**字段**而不是「靠操作者记得清空日志」：
  桥重启后 seq 从 1 重来，判分器若按 last_user 全量过滤，上一轮的记录会混进来，
  表现为「轮次虚高 / 不收敛假阳性」，镜像面是「旧尾条非终态 ⇒ 驱动傻等超时」。
  把 run_id 写进每条记录，判分器与驱动按它过滤，历史文件留不留都不影响结论；
  归档只是让人类翻日志方便，不再是正确性的前提。

同一文件内 seq 必须唯一：同一 run 里出现重复 seq 说明有两个进程在写同一个文件
（或人工拼接），此时记录归属不可信，判分器会拒绝出报告。
"""

import json
import os
import re
import ssl
import sys
import threading
import time
from http.client import HTTPSConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import bridge_log

UPSTREAM_HOST = os.environ.get("BRIDGE_UPSTREAM_HOST", "open.bigmodel.cn")
UPSTREAM_PORT = int(os.environ.get("BRIDGE_UPSTREAM_PORT", "443"))
UPSTREAM_PATH = os.environ.get("BRIDGE_UPSTREAM_PATH", "/api/paas/v4/chat/completions")
PROXY_HOST = os.environ.get("BRIDGE_PROXY_HOST", "127.0.0.1")


def _proxy_port():
    """代理端口**每个会话都会漂移**（实测 9079 → 1799 → 13516 → 6130 → 4558）。

    优先 BRIDGE_PROXY_PORT；否则从 https_proxy / http_proxy 现取；都没有 = 直连（0）。
    写死端口是本仓踩过的坑：桥报 ConnectionRefused(10061) 而设备侧只看到 502。
    """
    explicit = os.environ.get("BRIDGE_PROXY_PORT")
    if explicit:
        return int(explicit)
    for key in ("https_proxy", "HTTPS_PROXY", "http_proxy", "HTTP_PROXY"):
        v = os.environ.get(key) or ""
        m = re.search(r":(\d+)\s*$", v)
        if m:
            return int(m.group(1))
    return 0


PROXY_PORT = _proxy_port()
LISTEN_PORT = int(os.environ.get("BRIDGE_PORT", "8765"))
LOG_PATH = os.environ.get("BRIDGE_LOG", os.path.join(os.getcwd(), "glm_bridge.jsonl"))
API_KEY = os.environ.get("BIGMODEL_KEY", "")

_lock = threading.Lock()
_seq = [0]

# 进程级 run 标识：秒级时间戳 + pid。同一秒重启两次的概率极低，pid 再兜一层；
# 需要复现某次跑分时可显式 BRIDGE_RUN_ID 覆盖。
RUN_ID = os.environ.get("BRIDGE_RUN_ID") or (time.strftime("%Y%m%d-%H%M%S") + "-" + str(os.getpid()))
LOG_FILE = os.path.basename(LOG_PATH)


def archive_previous_log():
    """启动时把已存在的旧日志改名保留（不覆盖、不删除）。

    为什么要留：跑分中断后重跑，前一段的取证是唯一能解释「那几题为什么没收敛」的
    东西；删掉它等于把事故现场一起删了。改名后仍能被 BRIDGE_LOG=glm_bridge*.jsonl
    合并读到。
    """
    try:
        if not os.path.exists(LOG_PATH) or os.path.getsize(LOG_PATH) == 0:
            return None
    except OSError:
        return None
    target = "%s.%s.jsonl" % (LOG_PATH, RUN_ID)
    if os.path.exists(target):
        target = "%s.%s.%d.jsonl" % (LOG_PATH, RUN_ID, int(time.time() * 1000) % 100000)
    os.replace(LOG_PATH, target)
    return target


def log_record(record):
    with _lock:
        _seq[0] += 1
        record["seq"] = _seq[0]
        record["run_id"] = RUN_ID
        record["log_file"] = LOG_FILE
        with open(LOG_PATH, "a", encoding="utf-8") as f:
            f.write(json.dumps(record, ensure_ascii=False) + "\n")


def last_user_text(body):
    msgs = body.get("messages") or []
    for m in reversed(msgs):
        if m.get("role") == "user":
            c = m.get("content")
            if isinstance(c, str):
                return c
            if isinstance(c, list):
                return " ".join(p.get("text", "") for p in c if isinstance(p, dict))
    return ""


class Parsed:
    def __init__(self):
        self.text = []
        self.reasoning = []
        self.tool_calls = {}
        self.finish_reason = None
        self.usage = None
        self.sse_lines = 0
        self.error = None

    def feed(self, raw: bytes):
        for line in raw.decode("utf-8", "replace").split("\n"):
            line = line.strip()
            if not line.startswith("data:"):
                continue
            payload = line[5:].strip()
            if not payload or payload == "[DONE]":
                continue
            self.sse_lines += 1
            try:
                obj = json.loads(payload)
            except Exception:
                continue
            if isinstance(obj.get("error"), dict) and self.error is None:
                self.error = obj["error"].get("message") or json.dumps(obj["error"], ensure_ascii=False)
            for choice in obj.get("choices") or []:
                delta = choice.get("delta") or {}
                if delta.get("content"):
                    self.text.append(delta["content"])
                if delta.get("reasoning_content"):
                    self.reasoning.append(delta["reasoning_content"])
                for tc in delta.get("tool_calls") or []:
                    idx = tc.get("index", 0)
                    slot = self.tool_calls.setdefault(idx, {"id": None, "name": None, "arguments": ""})
                    if tc.get("id"):
                        slot["id"] = tc["id"]
                    fn = tc.get("function") or {}
                    if fn.get("name"):
                        slot["name"] = fn["name"]
                    if fn.get("arguments"):
                        slot["arguments"] += fn["arguments"]
                if choice.get("finish_reason"):
                    self.finish_reason = choice["finish_reason"]
            if obj.get("usage"):
                self.usage = obj["usage"]

    def summary(self):
        return {
            "text": "".join(self.text),
            "reasoning": "".join(self.reasoning),
            "tool_calls": [self.tool_calls[k] for k in sorted(self.tool_calls)],
            "finish_reason": self.finish_reason,
            "usage": self.usage,
            "sse_lines": self.sse_lines,
            "error": self.error,
        }


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"

    def log_message(self, fmt, *args):
        return

    def _json(self, status, obj):
        raw = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        # 端点探针 / 工具清单的兜底：给一个合法 JSON，避免「200 但不是流」的误判路径干扰跑分。
        if self.path.rstrip("/").endswith("/models"):
            self._json(200, {"object": "list", "data": [{"id": "glm-4.7", "object": "model"}]})
            return
        self._json(404, {"error": {"message": "not found"}})

    def do_POST(self):
        started = time.time()
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw.decode("utf-8"))
        except Exception:
            self._json(400, {"error": {"message": "invalid json"}})
            return

        last_user = last_user_text(body)
        record = {
            "ts": time.time(),
            "path": self.path,
            "model": body.get("model"),
            "last_user": last_user,
            "message_count": len(body.get("messages") or []),
            "tool_count": len(body.get("tools") or []),
            "request": body,
            "response": None,
            "status": None,
        }

        if not API_KEY:
            record["status"] = 500
            record["response"] = {"error": "BIGMODEL_KEY not set"}
            log_record(record)
            self._json(500, {"error": {"message": "bridge: BIGMODEL_KEY not set"}})
            return

        headers = {
            "Content-Type": "application/json; charset=utf-8",
            "Accept": "text/event-stream",
            "Authorization": "Bearer " + API_KEY,
        }

        conn = None
        try:
            ctx = ssl._create_unverified_context()
            if PROXY_HOST and PROXY_PORT:
                conn = HTTPSConnection(PROXY_HOST, PROXY_PORT, context=ctx, timeout=300)
                conn.set_tunnel(UPSTREAM_HOST, UPSTREAM_PORT)
            else:
                conn = HTTPSConnection(UPSTREAM_HOST, UPSTREAM_PORT, context=ctx, timeout=300)
            conn.request("POST", UPSTREAM_PATH, body=raw, headers=headers)
            resp = conn.getresponse()
            record["status"] = resp.status

            if resp.status != 200:
                detail = resp.read()
                record["response"] = {"error_body": detail.decode("utf-8", "replace")[:4000]}
                record["duration_ms"] = int((time.time() - started) * 1000)
                log_record(record)
                self.send_response(resp.status)
                self.send_header("Content-Type", resp.getheader("Content-Type", "application/json"))
                self.send_header("Content-Length", str(len(detail)))
                self.end_headers()
                self.wfile.write(detail)
                return

            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "close")
            self.end_headers()

            parsed = Parsed()
            first_byte = None
            while True:
                chunk = resp.read1(4096)
                if not chunk:
                    break
                if first_byte is None:
                    first_byte = int((time.time() - started) * 1000)
                parsed.feed(chunk)
                try:
                    self.wfile.write(chunk)
                    self.wfile.flush()
                except OSError:
                    # Windows 上是 ConnectionAbortedError(10053)，POSIX 上是 BrokenPipe/Reset。
                    # 客户端提前断开（curl | head / 用户点停止）不是桥的错误：保留已解析部分照常记账。
                    record["client_disconnected"] = True
                    break

            record["first_byte_ms"] = first_byte
            record["duration_ms"] = int((time.time() - started) * 1000)
            record["response"] = parsed.summary()
            log_record(record)
        except Exception as e:
            record["status"] = record.get("status") or 502
            if record.get("response") is None:
                record["response"] = {"error": repr(e)}
            record["duration_ms"] = int((time.time() - started) * 1000)
            log_record(record)
            try:
                self._json(502, {"error": {"message": "bridge failure: " + repr(e)}})
            except Exception:
                pass
        finally:
            if conn is not None:
                try:
                    conn.close()
                except Exception:
                    pass


if __name__ == "__main__":
    archived = archive_previous_log()
    if archived:
        print("archived previous log -> %s" % archived, flush=True)
    bridge_log.write_run_id(LOG_PATH, RUN_ID)
    print("run_id=%s log=%s" % (RUN_ID, LOG_PATH), flush=True)
    print("bridge listening on 127.0.0.1:%d -> %s%s (proxy=%s:%d)" % (
        LISTEN_PORT, UPSTREAM_HOST, UPSTREAM_PATH, PROXY_HOST, PROXY_PORT), flush=True)
    if not API_KEY:
        print("WARNING: BIGMODEL_KEY is empty; all requests will fail", flush=True)
    ThreadingHTTPServer(("127.0.0.1", LISTEN_PORT), Handler).serve_forever()
