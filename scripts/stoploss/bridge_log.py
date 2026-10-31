#!/usr/bin/env python3
"""
桥日志的共享读取面（判分器与驱动共用一份实现）。

为什么单独成文件而不是各自 open()：
  同一份「日志怎么读」的规则如果写两遍，两处就会漂移 —— 判分器按 run_id 过滤、
  驱动却按文件名过滤，跑出来的「注入成功」和「判分轮次」就不再是同一批数据。
  所有口径（多文件合并、run 边界、seq 完整性）只在这里定义一次。

三条口径：
  1. **路径可以给多个**：BRIDGE_LOG 支持逗号分隔与 glob。跑分中途重启桥会把前半段
     归档改名，只读当前文件会让轮次假少 —— 把不收敛题反向洗白成「2 轮就收敛」。
  2. **run 边界是显式字段**：每条记录带 run_id。桥重启即换 run_id，历史文件里的
     记录天然被排除，不需要靠「删日志」来保证干净。
  3. **同一文件内 seq 必须唯一**：seq 是进程内计数，重启后从 1 重来。同一文件里出现
     重复 seq 只可能是两个进程同时往一个文件写（或人工拼接），此时数据已不可信，
     必须拒绝出报告而不是挑一条用。
"""

import glob
import json
import os

RUN_SUFFIX = ".run"


class LogCorrupt(Exception):
    """日志结构性损坏（同文件 seq 重复 / 记录不是对象）。判分必须中止。"""


class LogMissing(Exception):
    """一个可读的日志文件都没有。"""


def expand_paths(spec):
    """把 BRIDGE_LOG 的写法展开成真实文件列表。

    支持 "a.jsonl"、 "a.jsonl,b.jsonl"、 "glm_bridge*.jsonl" 及其混合。
    保持书写顺序并对同一文件去重（glob 与显式路径可能重叠）。
    """
    if not spec:
        return []
    out = []
    seen = set()
    for part in str(spec).split(","):
        part = part.strip()
        if not part:
            continue
        # 有通配符才走 glob；否则按字面路径，避免文件名里的 [] 被当成字符集。
        hits = sorted(glob.glob(part)) if any(ch in part for ch in "*?[") else [part]
        for p in hits:
            key = os.path.abspath(p)
            if key in seen or not os.path.isfile(p):
                continue
            seen.add(key)
            out.append(p)
    return out


def load_records(spec):
    """读全部日志并按 (ts, seq) 合并排序；每条打 _src = 来源文件 basename。

    单行 JSON 解析失败只跳过该行（桥在崩溃瞬间可能留下半行），但 seq 重复是
    结构性损坏，直接抛 LogCorrupt —— 半行是「丢了尾巴」，重复 seq 是「两条互相矛盾
    的记录都自称第 N 条」，后者会让轮次与工具调用的归属随机漂移。
    """
    paths = expand_paths(spec)
    if not paths:
        raise LogMissing("no log file matched: %s" % spec)

    out = []
    for path in paths:
        src = os.path.basename(path)
        seen_seq = {}
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            for lineno, line in enumerate(f, 1):
                line = line.strip()
                if not line:
                    continue
                try:
                    rec = json.loads(line)
                except Exception:
                    continue
                if not isinstance(rec, dict):
                    continue
                seq = rec.get("seq")
                if isinstance(seq, int):
                    if seq in seen_seq:
                        raise LogCorrupt(
                            "同一文件内 seq 重复：%s 第 %d 行 seq=%d 与第 %d 行重复"
                            % (src, lineno, seq, seen_seq[seq]))
                    seen_seq[seq] = lineno
                rec["_src"] = src
                out.append(rec)

    # 同一秒内可能有多条（并发请求），seq 是进程内单调计数，做次序键足够。
    out.sort(key=lambda r: (float(r.get("ts") or 0), int(r.get("seq") or 0)))
    return out


def run_id_of(record):
    return record.get("run_id")


def sort_key(record):
    return (float(record.get("ts") or 0), int(record.get("seq") or 0))


def compute_rounds(records):
    """返回 (逻辑轮次, HTTP 请求数, 重试数)。

    轮次口径只在这里定义一次：驱动打印的轮次与判分器计分的轮次必须同源，否则
    「现场看到的 3 轮」和「报告里的 4 轮」会互相打架。

    判据是 messages 长度**严格递增**：同一逻辑轮的网络重试带的是同一份 messages，
    长度不变 ⇒ 不计轮。这个定义不依赖供应商字段，离线可复算。
    """
    ordered = sorted(records, key=sort_key)
    rounds = 0
    prev = -1
    for r in ordered:
        n = len(((r.get("request") or {}).get("messages")) or [])
        if n > prev:
            rounds += 1
            prev = n
    http = len(ordered)
    return rounds, http, max(0, http - rounds)


def filter_run(records, run_id):
    """只留指定 run 的记录。run_id 为空时原样返回（仅供离线复算旧数据）。"""
    if not run_id:
        return list(records)
    return [r for r in records if r.get("run_id") == run_id]


def run_file_for(log_path):
    return log_path + RUN_SUFFIX


def live_spec(log_path):
    """当前日志 + 同目录归档（<LOG_PATH>.<run_id>.jsonl）。

    驱动必须用这个写法读：跑分中途桥崩了重启，前半段被归档改名，只读当前文件
    会让「本轮已发过几条」凭空归零 —— wait_done 会以为题面还没注入。
    """
    return log_path + "," + log_path + "*.jsonl"


def read_run_id(log_path):
    """读桥启动时写下的 run_id；没有则返回 None（说明桥没按新协议起过）。"""
    p = run_file_for(log_path)
    try:
        with open(p, "r", encoding="utf-8") as f:
            return (f.read() or "").strip() or None
    except OSError:
        return None


def write_run_id(log_path, run_id):
    with open(run_file_for(log_path), "w", encoding="utf-8") as f:
        f.write(run_id + "\n")
