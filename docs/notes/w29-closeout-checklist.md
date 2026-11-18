# W29 收口 checklist — W28 checklist 空转判据的机制性修复（2026-11-18）

> 存在理由：W28 新建的 `w28-closeout-checklist.md` 判据 2 自身空转（正常态只读 `state_counts` 与 `assertions[].description`，不读 `mutation_cases[].verified` / `mutation_selftest.all_verified`，注入 `verified=false` 仍输出 OK）、判据 3b 含占位符吞错误、判据 1a 无判红分支、判据 1/3 `/tmp` 口径冲突，且 W28-01「三处同步」变异零载体。本文件按外审 P0-1/P0-2 处方建立 W29 版载体。
> 纪律：本 checklist 是**人工闸门 + 必红变异**兜底，不是机器守卫 —— `arch-guard.sh` g20 只做存在性比对（两边 `sort -u` 抹平重复号），无 ID 唯一检查；改守卫 / 扩扫描面属用户裁决域（本波只留痕不代做）。
> 路径变量：下列命令中的  = 工作区根目录（运行时按实际设置，本文件不落本机绝对路径）。
> 🔴 **路径口径统一**：本文件所有变异副本一律用**工作区真实路径** `${WS}/_w29/mut/`，不用 `/tmp`（Git Bash 的 `/tmp` 与 Windows Python 不通会 `FileNotFoundError`）。
> 判定四态：每条命令期望「正常态输出 `OK`，注入变异样本后输出 `RED`」；**只跑正常态全 OK 不算判据被验证**，必须自证变异样本会响。

---

## 使用方式

在仓库根（`<workspace>/harness-v2`）执行下列命令（Git Bash）。每条判据分「正常态」与「必红变异」两步；变异在工作区副本上做，验完还原，不改真值源。

---

## 判据 1 — 挂账号段唯一性（含 1a 判红分支）

**出口判据来源**：W29 段首行号段自由确认 + 重复号检测。

**正常态（期望 OK）** —— 修 W28「grep 只打印数字、无判红」：

```bash
cd harness-v2
LEDGER=${LEDGER:-docs/06-deferred-ledger.md}
n=$(grep -cE '^\| W29-' "$LEDGER"); echo "W29 rows = $n"; [ "$n" -eq 3 ] || echo "RED W29 row count != 3 (got $n)"
for id in $(grep -oE '^\| W29-[0-9]+' "$LEDGER" | sed 's/^| //'); do
  c=$(grep -cE "^\| ${id} " "$LEDGER"); echo "$id -> $c"; [ "$c" -eq 1 ] || echo "RED duplicate/missing: $id"
done
```

**必红变异（期望 RED）**：工作区副本追加一条重复行 → `LEDGER=<副本> bash <上面循环>` 应输出 `RED duplicate`。验完删副本。本波已实跑（见 §5.4 证据）。

---

## 判据 2 — 制品出厂断言非空转（修 W28 空转：读 verified 不变量）

**出口判据来源**：W29-02「注入 `verified=false` 样本 ⇒ 判据 2 输出 RED」。

**正常态（期望 OK）** —— 修 W28：现在**读 `mutation_cases[].verified` 与 `mutation_selftest.all_verified` 的一致性**，并校验真机正控来源：

```bash
cd harness-v2
python - <<'PY'
import json, io
d = json.load(io.open("evidence/w29-matrix-summary.json", encoding="utf-8"))
all_v = all(m.get("verified") is True for m in d["mutation_cases"])
ok1 = (all_v == d["mutation_selftest"]["all_verified"])
pc = next((m for m in d["mutation_cases"] if m["id"]=="K_positive_control"), None)
ok2 = bool(pc) and pc.get("verified_by") == "device_execution"
print("all(verified) =", all_v, "| all_verified =", d["mutation_selftest"]["all_verified"])
print("K_positive_control.verified_by =", pc.get("verified_by") if pc else None)
print("OK" if (ok1 and ok2) else "RED mutation invariant broken")
PY
```

**必红变异（期望 RED）** —— 修 W28「0 个代码块」：

```bash
cd harness-v2
MUT="${WS}/_w29/mut"
mkdir -p "$MUT"
python - <<'PY'
import json, io
src = "evidence/w29-matrix-summary.json"
dst = "${WS}/_w29/mut/w29-matrix-summary.mut.json"
d = json.load(io.open(src, encoding="utf-8"))
d["mutation_cases"][0]["verified"] = False   # 注入: verified=false 但 all_verified 仍 True
io.open(dst, "w", encoding="utf-8").write(json.dumps(d, ensure_ascii=False))
PY
python - <<'PY'
import json, io
d = json.load(io.open("${WS}/_w29/mut/w29-matrix-summary.mut.json", encoding="utf-8"))
all_v = all(m.get("verified") is True for m in d["mutation_cases"])
print("RED mutation invariant broken" if all_v != d["mutation_selftest"]["all_verified"] else "OK (unexpected)")
PY
```

注入 `verified=false` 后应输出 `RED mutation invariant broken`。验完删副本。本波已实跑（见 §5.4 证据）。

---

## 判据 3 — tip run 闭环回填（修 3b 占位符吞错误）

**正常态（期望 OK）**：

```bash
cd harness-v2
for tok in 44251600099 f099758; do
  n=$(grep -c "$tok" docs/analysis/w28-acceptance.md)
  echo "$tok -> $n"; [ "$n" -ge 1 ] || echo "RED tip token missing: $tok"
done
# 3b 改为仓内可跑: 凭据文件名族全树 grep (正控先自证会响, 见 §5.5)
grep -rniE 'openrouter\.txt|nvidia\.txt' docs evidence 2>/dev/null && echo "RED credential filename hit" || echo "OK no credential filename"
```

**必红变异（期望 RED）**：工作区副本删净 `44251600099` 字面量 → 重跑 3a 应输出 `RED tip token missing`。验完删副本。

---

## 判据 4 — 三处同步（新增，覆盖 W28-01「只改验收物不改台账」）

**出口判据来源**：W29-01「注入『只改验收物不改台账』样本 ⇒ checklist 判红」。W28 checklist 全文此变异 0 命中，本判据补齐。

**正常态（期望 OK）** —— 三处（验收物 / handoff / 台账）关于「已写进压缩老号作红态入口」的肯定式措辞应按文件白名单清零：

```bash
cd harness-v2
# 白名单三处原句: w27-acceptance / handoff-w27 / 台账 W27-03
for f in docs/analysis/w27-acceptance.md docs/handoff-20261118-wave27.md docs/06-deferred-ledger.md; do
  n=$(grep -cE '已写进.{0,12}压缩老号|作(为)?.{0,4}红态入口' "$f")
  echo "$f -> $n"; [ "$n" -eq 0 ] || echo "RED affirmative wording remains: $f"
done
```

**必红变异（期望 RED）** —— 工作区副本把肯定式措辞写回 `w27-acceptance.md` 副本 → 重跑应输出 `RED affirmative wording remains`。验完删副本。

---

## 本 checklist 覆盖 / 不覆盖（诚实标注）

- **覆盖**：判据 1（1a 判红分支）/ 2（verified 不变量 + 可跑必红代码块）/ 3（3b 仓内 grep）/ 4（三处同步）的仓内可执行命令 + 必红变异 + 人工兜底声明。
- **不覆盖**：① `arch-guard.sh` 的 ID 唯一性（用户裁决域）；② 真机重放（制品 verified 多为规则自证，真机判据在验收物 §3/§4）；③ 仓外生成器断言 M（老号 W20-14）；④ 脱敏正控工具在仓外取证设施（按截图回灌级别只回灌判据，不回灌整段日志）。
- **四态**：正常态命令全 OK 仅说明「载体存在且当前数据自洽」，不等于挂账 `CLOSED`；改 `CLOSED` 须真机取证入账后按台账 §三.3 由主理人单人落笔。
