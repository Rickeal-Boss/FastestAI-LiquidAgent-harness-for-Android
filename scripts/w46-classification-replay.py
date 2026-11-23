#!/usr/bin/env python3
"""W46 判序双向混淆样本等价复刻（判据先行自证用）。

- --legacy：逐字复刻现役 W45 的 OpenAiErrorFrame.kt 判序（未修版本）⇒ 外审 5 条边界必须 RED。
- --w46   ：复刻 W46 结构性收口后的判序（结构化权威 + 分层词表）⇒ 21 样本全 GREEN。

两份共用 scripts/fixtures/w46-error-classification-samples.json，与 JVM 参数化测试同一份样本集。
rc=0 全部符合 expected；rc=1 有偏差（RED）；rc=2 环境/参数故障。
"""
import io
import json
import os
import sys

FIXTURE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures", "w46-error-classification-samples.json")

# ---- 现役 W45 判序（逐字复刻 OpenAiErrorFrame.kt:283-286 / :258-274 / :199-218）----
def w45_rate_context(low):
    return any(x in low for x in ["429", "quota", "throttl", "slow down", "too many requests", "rate limit"])

def w45_looks_overflow(low):
    len_ovf = any(x in low for x in ["too long", "maximum", "exceed", "length"])
    strong = ("context" in low) or ("prompt" in low and len_ovf)
    if strong and len_ovf:
        return True
    return ("token" in low) and len_ovf and not w45_rate_context(low)

def classify_w45(msg):
    low = msg.lower()
    if w45_looks_overflow(low):
        return "CONTEXT_OVERFLOW"
    if w45_rate_context(low):
        return "RATE_LIMIT"
    if any(x in low for x in ["overload", "server", "5xx", "unavailable"]):
        return "SERVER"
    if "rate" in low:
        return "RATE_LIMIT"
    return "PROTOCOL"

# ---- W46 结构性收口判序（结构化权威保留；message 词表分层为启发式）----
# 单一真值源：显式限流语境 = W45 六词 + 4 个时间/速率词（派单卡扩表）
RATE_CTX = ["429", "quota", "throttl", "slow down", "too many requests", "rate limit",
            "tpm", "tokens per minute", "requests per minute", "per-minute"]
# 强限流语境：能压过「窗口词 × 长度语义」的证据（区别于 quota/slow down 这类弱词）
STRONG_RATE = ["429", "rate limit", "throttl", "too many requests",
               "tpm", "tokens per minute", "requests per minute", "per-minute"]
# 强长度语义：maximum / too long / length（exceed 单独为弱长度语义）
STRONG_LEN = ["too long", "maximum", "length"]
WEAK_LEN = ["exceed"]

def w46_rate_context(low):
    return any(x in low for x in RATE_CTX)

def w46_strong_rate(low):
    return any(x in low for x in STRONG_RATE)

def w46_looks_overflow(low):
    strong_len = any(x in low for x in STRONG_LEN)
    weak_len = any(x in low for x in WEAK_LEN)
    any_len = strong_len or weak_len
    token_window = "token" in low
    # W47（P0-2 最后一轮）：context 恢复无条件强窗口词（真超窗文案自带 rate limit/tpm/429，
    # 不能让位）；让位只留给 prompt（prompt too long + 强限流语境 ⇒ RATE_LIMIT，保 R08）。
    # (1) context × 任意长度语义：无条件超窗
    if "context" in low and any_len:
        return True
    # (2) prompt × 任意长度语义：仅强限流语境让位
    if "prompt" in low and any_len and not w46_strong_rate(low):
        return True
    # (3) 弱窗口词 token × 强长度语义，且无强限流语境（含 quota/slow down 弱词也不让位）
    if token_window and strong_len and not w46_strong_rate(low):
        return True
    # (4) 弱窗口词 token × 弱长度语义（仅 exceed），且无任何限流语境
    if token_window and weak_len and not w46_rate_context(low):
        return True
    return False

def classify_w46(msg):
    low = msg.lower()
    if w46_looks_overflow(low):
        return "CONTEXT_OVERFLOW"
    if w46_rate_context(low):
        return "RATE_LIMIT"
    if any(x in low for x in ["overload", "server", "5xx", "unavailable"]):
        return "SERVER"
    if "rate" in low:
        return "RATE_LIMIT"
    return "PROTOCOL"

def main():
    if "--legacy" not in sys.argv and "--w46" not in sys.argv:
        print("usage: w46-classification-replay.py --legacy|--w46", file=sys.stderr)
        return 2
    fn = classify_w45 if "--legacy" in sys.argv else classify_w46
    data = json.load(io.open(FIXTURE, "r", encoding="utf-8"))
    bad = []
    print("mode=%s samples=%d" % ("legacy" if fn is classify_w45 else "w46", len(data["samples"])))
    for s in data["samples"]:
        got = fn(s["message"])
        ok = got == s["expected"]
        print("  %-3s %-4s %-22s %-16s exp=%-16s %s" % (
            "OK" if ok else "RED", s["id"], s["side"], got, s["expected"], s["message"]))
        if not ok:
            bad.append(s["id"])
    if bad:
        print("RED mismatches (%d): %s" % (len(bad), ",".join(bad)))
        return 1
    print("OK all %d samples match expected" % len(data["samples"]))
    return 0

if __name__ == "__main__":
    sys.exit(main())
