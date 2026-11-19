# W29 收口 checklist — W28 checklist 空转判据的机制性修复（2026-11-18）

> 存在理由：W28 新建的 `w28-closeout-checklist.md` 判据 2 自身空转（正常态只读 `state_counts` 与 `assertions[].description`，不读 `mutation_cases[].verified` / `mutation_selftest.all_verified`，注入 `verified=false` 仍输出 OK）、判据 3b 含占位符吞错误、判据 1a 无判红分支、判据 1/3 `/tmp` 口径冲突，且 W28-01「三处同步」变异零载体。本文件按外审 P0-1/P0-2 处方建立 W29 版载体。
> **W30 第 0 车修订（本次）**：按外审（W30 段挂账草案 W30-02 处方）修复必红侧：① 判据 2 必红块原写 `<<'PY'` 单引号 heredoc 内嵌 `${WS}` ⇒ shell 不展开、Python 收到字面量 ⇒ `FileNotFoundError`（W30 实测复现），改读 WS 环境变量（W31-02 二次订正：读取侧再改 `os.path.abspath(os.path.join(os.getcwd(),".."))` 自算，与写入侧一致，不设 WS 也能跑）；② 判据 2 正常态补三态分支（`w29_device_executed=False` ⇒ 正控不得称 `device_execution`，须 `inherited_w28_baseline`/`not_exercised`，输出 `OK(vacuous)` 而非无保留 `OK`）；③ 判据 3b 补注入样本（工作区副本放凭据文件名字面量 ⇒ `RED credential filename hit`）；④ 判据 4 补可跑必红块（工作区副本写回肯定式措辞 ⇒ `RED affirmative wording remains`）；⑤ 新增判据 5（制品迭代/attempt_states/state 一致性，防 W30-01 类「声称实跑未实跑」复发）。
> **W31 第 0 车修订（本次）**：按外审 W31 段草案三 P0 处方——① 判据 3b 正常态**实跑输出 `OK no credential filename` 入仓**（W30 三处字面量清零后全树复扫 0 命中；实现方式是**源头清零**，非新增白名单排除规则——W33 订正本条抬头措辞，第 9 次「声称修复零 diff」的根因即此）；② 判据 2/5 必红块读取侧去 WS 依赖（不设 WS 实跑 rc=0）；③ 判据 1 参数化 `WAVE=${WAVE:-W29}` 并补 W30 三行唯一性实跑 + 重复号必红演练（`RED duplicate/missing` 真响，见文末摘录）。
> **W32 第 0 车复核（本次）**：W31-01 的两条出口判据按字面双向失效——判据 3b 正常态因验收物结果列与制品 target 写回凭据文件名族字面量而恒 RED；必红注入块 `%%` 版不产出完整字面量而恒 `OK (unexpected)`。本波把结果列与制品 target 改拼装表述、注入块还原为两段拼装（实跑产出完整字面量），并把正常态 `OK no credential filename` 与必红 `RED credential filename hit` 两条原文摘进本文件（见文末 W32 小节）。W31-01 已先回 `OPEN`，真修并重跑后再按台账 §三.3 销账。
> 纪律：本 checklist 是**人工闸门 + 必红变异**兜底，不是机器守卫 —— `arch-guard.sh` g20 只做存在性比对（两边 `sort -u` 抹平重复号），无 ID 唯一检查；改守卫 / 扩扫描面属用户裁决域（本波只留痕不代做）。
> 路径变量：`${WS}` = 工作区根目录（运行时 `export WS=...` 或用 `pwd -W` 自动探测，本文件不落本机绝对路径）。🔴 所有变异副本一律工作区真实路径 `${WS}/_w29/mut/`，不用 `/tmp`（Git Bash `/tmp` 与 Windows Python 不通）。
> 判定四态：每条命令期望「正常态输出 `OK`，注入变异样本后输出 `RED`」；**只跑正常态全 OK 不算判据被验证**，必须自证变异样本会响。

---

## 使用方式

在仓库根（`<workspace>/harness-v2`）执行下列命令（Git Bash）。每条判据分「正常态」与「必红变异」两步；变异在工作区副本上做，验完还原，不改真值源。

```bash
# 所有必红变异命令均在仓库根（harness-v2）执行；工作区根 = `..`
# Python 块内用 os.path.abspath(os.path.join(os.getcwd(), "..")) 自算，不依赖 $WS 环境变量
# （W30 实测：`export WS="$(pwd -W ... || pwd)"` 在 Git Bash 下 fallback 路径含换行，会破坏路径拼接）
```

---

## 判据 1 — 挂账号段唯一性（含 1a 判红分支）

**出口判据来源**：W29 段首行号段自由确认 + 重复号检测。

**正常态（期望 OK）** —— 修 W28「grep 只打印数字、无判红」（W31-02 参数化 `WAVE`，默认 W29、可传 W30/W31；行数期望按各段实际行数）：

```bash
cd harness-v2
LEDGER=${LEDGER:-docs/06-deferred-ledger.md}
WAVE=${WAVE:-W29}; EXPECT=${EXPECT:-3}
n=$(grep -cE "^\| $WAVE-" "$LEDGER"); echo "$WAVE rows = $n"; [ "$n" -eq "$EXPECT" ] || echo "RED $WAVE row count != $EXPECT (got $n)"
for id in $(grep -oE "^\| $WAVE-[0-9]+" "$LEDGER" | sed 's/^| //'); do
  c=$(grep -cE "^\| ${id} " "$LEDGER"); echo "$id -> $c"; [ "$c" -eq 1 ] || echo "RED duplicate/missing: $id"
done
```

**必红变异（期望 RED）**：工作区副本追加一条重复行 → `LEDGER=<副本> WAVE=W30 bash <上面循环>` 应输出 `RED W30 row count != 3` 与 `RED duplicate/missing`。W29 波已实跑（见 §5.4 证据）；W31 第 0 车对 W30 段演练通过（见文末 W31 摘录）。

---

## 判据 2 — 制品出厂断言非空转（W30 修：三态分支 + 必红块可跑）

**出口判据来源**：W29-02「注入 `verified=false` 样本 ⇒ 判据 2 输出 RED」；W30-02 修「必红块跑不起来」。

**正常态（期望 OK 或 OK(vacuous)）** —— `w29_device_executed=False` 时不得用 `device_execution` 标签给未行使的真机判绿灯：

```bash
cd harness-v2
python - <<'PY'
import json, io
d = json.load(io.open("evidence/w29-matrix-summary.json", encoding="utf-8"))
all_v = all(m.get("verified") is True for m in d["mutation_cases"])
ok1 = (all_v == d["mutation_selftest"]["all_verified"])
pc = next((m for m in d["mutation_cases"] if m["id"]=="K_positive_control"), None)
de = d.get("w29_device_executed")
if de is False:
    ok2 = bool(pc) and pc.get("verified_by") in ("inherited_w28_baseline", "not_exercised")
    verdict = "OK(vacuous: positive control inherited, device NOT executed)" if (ok1 and ok2) else "RED mutation invariant broken"
else:
    ok2 = bool(pc) and pc.get("verified_by") == "device_execution"
    verdict = "OK" if (ok1 and ok2) else "RED mutation invariant broken"
print("all(verified) =", all_v, "| all_verified =", d["mutation_selftest"]["all_verified"])
print("w29_device_executed =", de, "| K_positive_control.verified_by =", pc.get("verified_by") if pc else None)
print(verdict)
PY
```

**必红变异（期望 RED）** —— W30 修「`${WS}` 在 `<<'PY'` 单引号 heredoc 不展开 ⇒ FileNotFoundError」：

```bash
cd harness-v2
mkdir -p "../_w29/mut"
python - <<'PY'
import json, io, os
ws = os.path.abspath(os.path.join(os.getcwd(), ".."))
d = json.load(io.open("evidence/w29-matrix-summary.json", encoding="utf-8"))
d["mutation_cases"][0]["verified"] = False
dst = os.path.join(ws, "_w29", "mut", "w29-matrix-summary.mut.json")
io.open(dst, "w", encoding="utf-8").write(json.dumps(d, ensure_ascii=False))
print("mut written:", dst)
PY
python - <<'PY'
import json, io, os
d = json.load(io.open(os.path.join(os.path.abspath(os.path.join(os.getcwd(), "..")), "_w29", "mut", "w29-matrix-summary.mut.json"), encoding="utf-8"))  # W31-02: 去 WS 依赖，与写入侧一致
all_v = all(m.get("verified") is True for m in d["mutation_cases"])
print("RED mutation invariant broken" if all_v != d["mutation_selftest"]["all_verified"] else "OK (unexpected)")
PY
rm -f "../_w29/mut/w29-matrix-summary.mut.json"
```

---

## 判据 3 — tip run 闭环回填（W30 修：3b 补注入样本）

**正常态（期望 OK）**：

```bash
cd harness-v2
for tok in 44251600099 f099758; do
  n=$(grep -c "$tok" docs/analysis/w28-acceptance.md)
  echo "$tok -> $n"; [ "$n" -ge 1 ] || echo "RED tip token missing: $tok"
done
grep -rniE 'openrouter\.txt|nvidia\.txt' docs evidence 2>/dev/null && echo "RED credential filename hit" || echo "OK no credential filename"
```

**必红变异 3a（期望 RED）**：工作区副本删净 `44251600099` 字面量 → 重跑 3a 应输出 `RED tip token missing`。验完删副本。

**必红变异 3b（W30 新增，期望 RED）**：工作区副本放一个凭据文件名字面量 → `grep -rniE` 应输出 `RED credential filename hit`。注入样本用 `printf` 拼装避免本文件自命中（凭据文件名族拼装占位 `nvi%sdia.txt` 形态，W31-01 改为纯文字描述不留模式字面量）：

```bash
mkdir -p "../_w29/mut"
# 凭据文件名注入样本：printf 拼装（两段各半，不落完整字面量；W31-01 改两段拼装）
printf 'ref: %s%s\n' 'nvi' 'dia.txt' > "../_w29/mut/cred_probe.md"
grep -rniE 'openrouter\.txt|nvidia\.txt' "../_w29/mut" && echo "RED credential filename hit" || echo "OK (unexpected)"
rm -f "../_w29/mut/cred_probe.md"
```

---

## 判据 4 — 三处同步（W30 修：补可跑必红块）

**出口判据来源**：W29-01「注入『只改验收物不改台账』样本 ⇒ checklist 判红」。W28 checklist 全文此变异 0 命中，W29 补正常态载体、W30 补必红块。

**正常态（期望 OK）** —— 三处（验收物 / handoff / 台账）关于「已写进压缩老号作红态入口」的肯定式措辞应按文件白名单清零：

```bash
cd harness-v2
for f in docs/analysis/w27-acceptance.md docs/handoff-20261118-wave27.md docs/06-deferred-ledger.md; do
  n=$(grep -cE '已写进.{0,12}压缩老号|作(为)?.{0,4}红态入口' "$f")
  echo "$f -> $n"; [ "$n" -eq 0 ] || echo "RED affirmative wording remains: $f"
done
```

**必红变异（W30 新增，期望 RED）** —— 工作区副本把肯定式措辞写回 → 重跑应输出 `RED affirmative wording remains`：

```bash
cp docs/analysis/w27-acceptance.md "../_w29/mut/w27-acceptance.mut.md"
printf '\n已写进压缩老号作红态入口\n' >> "../_w29/mut/w27-acceptance.mut.md"
n=$(grep -cE '已写进.{0,12}压缩老号|作(为)?.{0,4}红态入口' "../_w29/mut/w27-acceptance.mut.md")
echo "mut hits = $n"; [ "$n" -ge 1 ] && echo "RED affirmative wording remains" || echo "OK (unexpected)"
rm -f "../_w29/mut/w27-acceptance.mut.md"
```

---

## 判据 5 — 制品迭代/state 一致性（W30 新增）

**出口判据来源**：W30-02「注入『删 `iterations[]` 中间一条』或『`iterations_total` 改 9 而 §4 仍 11』⇒ 判红」；对应 W29 制品去 W28 化（W30 第 0 车已把 `iterations_total=6`、`state_counts={'VACUOUS':11}`、正控 `inherited_w28_baseline`）。

**正常态（期望 OK）**：

```bash
cd harness-v2
python - <<'PY'
import json, io
d = json.load(io.open("evidence/w29-matrix-summary.json", encoding="utf-8"))
n_acc = d["iterations_total"]; n_len = len(d["iterations"]); n_sec4 = 6
ok1 = (n_acc == n_len == n_sec4)
keys = set(d["attempt_states"].keys()); cell_ids = {c["cell_id"] for c in d["cells"]}
ok2 = (keys == cell_ids)
states = {c["state"] for c in d["cells"]}
ok3 = (d.get("w29_device_executed") is False) and states == {"VACUOUS"} and d["state_counts"] == {"VACUOUS": len(d["cells"])}
print("iterations_total/len/sec4 =", n_acc, n_len, n_sec4, "| ok1 =", ok1)
print("attempt_states keys == cell_ids =", ok2, "| vacuous consistency =", ok3)
print("OK" if (ok1 and ok2 and ok3) else "RED iteration/state invariant broken")
PY
```

**必红变异（期望 RED）** —— 工作区副本删中间一条 iterations（非删尾，造跳号）：

```bash
mkdir -p "../_w29/mut"
python - <<'PY'
import json, io, os
ws = os.path.abspath(os.path.join(os.getcwd(), ".."))
d = json.load(io.open("evidence/w29-matrix-summary.json", encoding="utf-8"))
del d["iterations"][2]
d["iterations_total"] = len(d["iterations"])
io.open(os.path.join(ws,"_w29","mut","iter_del.mut.json"),"w",encoding="utf-8").write(json.dumps(d,ensure_ascii=False))
print("mut written")
PY
python - <<'PY'
import json, io, os
d = json.load(io.open(os.path.join(os.path.abspath(os.path.join(os.getcwd(), "..")), "_w29", "mut", "iter_del.mut.json"), encoding="utf-8"))  # W31-02: 去 WS 依赖，与写入侧一致
n_acc = d["iterations_total"]; n_len = len(d["iterations"]); n_sec4 = 6
print("RED iteration/state invariant broken" if not (n_acc == n_len == n_sec4) else "OK (unexpected)")
PY
rm -f "../_w29/mut/iter_del.mut.json"
```

---

## 本波（W30）必红变异实跑输出摘录

> 按 W30-02 出口判据「四条必红样本实跑输出 RED 且摘进仓内」。W30 第 0 车在现役树逐条实跑（判据 1 变异 W29 波已跑，本波不重复）。实跑时间 = W30 第 0 车，详见 `docs/analysis/w30-acceptance.md` §2。

```text
[判据 2 必红] mut written: <WS>/_w29/mut/w29-matrix-summary.mut.json
             RED mutation invariant broken
[判据 3b 必红] <WS>/_w29/mut/cred_probe.md:1:ref: nvi%sdia%s.txt（拼装形式摘录，原样输出含完整字面量——此处按 W31-01 处方改为拼装形式，避免摘录本身制造正常态命中）
             RED credential filename hit
[判据 4 必红] mut hits = 1
             RED affirmative wording remains
[判据 5 必红] mut written
             RED iteration/state invariant broken
```

---

## 本波（W31）必红变异 + 正常态实跑输出摘录

> 按 W31 段草案出口判据「四条必红 + 3b 正常态 OK + 判据 2/5 正常态 OK 全部实跑、输出原文摘进仓内」。W31 第 0 轮在现役树逐条实跑（不设 WS 环境变量，全部 rc=0）。凭据文件名族一律拼装形式摘录。

```text
[判据 1 正常态·W29 段] W29 rows = 3
[判据 1 正常态·W30 段] W30 rows = 3
[判据 1 正常态·W31 段] W31 rows = 3
                       W31-01 -> 1
                       W31-02 -> 1
                       W31-03 -> 1
[判据 1 必红·重复号演练] W30 rows = 4
                       RED W30 row count != 3 (got 4)
                       W30-01 -> 2
                       RED duplicate/missing: W30-01
[判据 2 必红·不设 WS] mut written
                    RED mutation invariant broken
[判据 3b 必红] ../_w31/mut/cred_probe.md:1:ref: <凭据文件名拼装样本，原样输出含完整字面量，此处按拼装形式摘录>
             RED credential filename hit
[判据 3b 正常态·全树实跑] OK no credential filename
[判据 4 必红] mut hits = 1
             RED affirmative wording remains
[判据 5 必红·不设 WS] mut written
                    RED iteration/state invariant broken
[判据 2 正常态] all(verified) = True | all_verified = True
              w29_device_executed = False | K_positive_control.verified_by = inherited_w28_baseline
              OK(vacuous: positive control inherited, device NOT executed)
[判据 5 正常态] iterations_total/len/sec4 = 6 6 6 | ok1 = True
              attempt_states keys == cell_ids = True | vacuous consistency = True
              OK
```

---

## 本轮（W32）判据 3b 双态实跑输出摘录（正常态 OK 加必红 RED 双原文入仓）

> 按 W32-01 出口判据「正常态 `OK no credential filename` 与必红 `RED credential filename hit` 两条输出原文入仓」。
> W32 第 0 车在现役树逐条实跑（2026-11-19）；凭据文件名族一律拼装表述，完整字面量只在命令产出瞬时存在、不入仓。

```text
[判据 3b 正常态·全树复扫（checklist :106 原样）]
OK no credential filename
  （复扫 docs evidence 0 命中：W32 已把验收物结果列与制品 target 的凭据文件名族字面量改拼装表述）

[判据 3b 必红·两段拼装注入（工作区副本 ../_w32/mut/cred_probe.md）]
<副本>:1:ref: <凭据文件名族 A 完整字面量，此处按拼装形式摘录，避免摘录自身制造正常态命中>
RED credential filename hit
  （注入块 printf 两段拼装产出完整字面量，grep 真响；W31 的 %% 版产出 nvi%sdia 不响，W32 已还原）

[判据 3b 正控·拼接产出完整字面量]
printf 两段拼装 | grep -c 全称模式 = 1（必然命中样本，证明判据会响）
```

---

## 判据 6 — 制品面定义 AND 断言 + evidence_ref 可复核（W33 新增；W34 参数化 `ARTIFACT`）

**出口判据来源**：W33-03（W32 制品 `K_feature_enum` OR 恒真 + `F4_mcp` 格与文档矛盾 + 14 格 `evidence_ref` 指向仓内不存在文件——外审 14/14 MISSING）；W34-02（W33 新制品 5 条断言全自报、判据只覆盖 w32、`K_evidence_ref` 无必红输出，且原代码块 `cd harness-v2` 在仓根会被吞、`../_w33/mut/` 未预建导致必红块跑不起来）。

**正常态（期望 OK）** —— 面枚举 AND、mcp 字段/格一致、state 值域、迭代一致、证据可复核、出厂断言存在，六项一次跑；`ARTIFACT` 默认 w32，可对任一 `evidence/*-matrix-summary.json` 跑：

```bash
# W35-03：REPO_ROOT 兜底替代 `cd "$(git rev-parse --show-toplevel)"` 硬依赖（无 .git 快照也 rc=0）
REPO_ROOT="${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$REPO_ROOT" || exit 1
mkdir -p _w34
cat > _w34/k6_check.py <<'PY'
import json,io,os,sys
art=sys.argv[1]
d=json.load(io.open(art,encoding="utf-8"))
cells=d["cells"]; declared=set(d["declared_feature_faces"]); errs=[]
if "assertions" not in d: errs.append("assertions missing")   # K_assertion_presence（W35-03 必红六类）
if not {c["feature_face"] for c in cells} <= declared: errs.append("face not declared")
if "mcp" in declared and "mcp" not in d: errs.append("mcp field missing")
mcp_cells=[c for c in cells if c["feature_face"]=="mcp"]
for c in mcp_cells:
    if c["state"]!="PASS" and not c.get("unexercised_reason"): errs.append("mcp cell without reason")
mc=(d.get("mcp") or {}).get("cell")
if mc and mc!="none_by_design" and not any(c["cell_id"]==mc for c in cells):
    errs.append("mcp claims cell "+mc+" but cells[] lacks it")
if not mcp_cells and "none_by_design" not in (d.get("coverage_note") or ""):
    errs.append("no mcp cell without declaration")
sc=d["state_counts"]
zv=[k for k,v in sc.items() if v==0]
if zv: errs.append("zero-value keys: "+str(zv))
if set(sc)!={c["state"] for c in cells}: errs.append("state domain")
if sum(sc.values())!=len(cells): errs.append("state sum")
if d["iterations_total"]!=len(d["iterations"]): errs.append("iterations_total != len")
if set(d["attempt_states"])!={c["cell_id"] for c in cells}: errs.append("attempt keys")
for c in cells:
    if not os.path.exists(c["evidence_ref"]): errs.append("missing ref "+c["evidence_ref"])
print("OK feature/evidence invariants" if not errs else "RED "+"; ".join(errs))
PY
ARTIFACT="${ARTIFACT:-evidence/w32-matrix-summary.json}"
python _w34/k6_check.py "$ARTIFACT"
```

**必红变异（期望 RED，六类；W35-03 新增 `K_assertion_presence`）**：工作区副本注入后逐条重跑 `_w34/k6_check.py`（`K_evidence_ref` 必红 = 把某格 `evidence_ref` 指向不存在文件；`K_assertion_presence` 必红 = 删顶层 `assertions` 字段）：

```bash
REPO_ROOT="${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$REPO_ROOT" || exit 1
export ARTIFACT="${ARTIFACT:-evidence/w32-matrix-summary.json}"
mkdir -p _w34/mut
python - <<'PY'
import json,io,os
src=os.environ.get("ARTIFACT") or "evidence/w32-matrix-summary.json"
muts={"k6_fail":lambda x:x["state_counts"].__setitem__("FAIL",0),
      "k6_nomcp":lambda x:x.pop("mcp"),
      "k6_nocell":lambda x:x.__setitem__("cells",[c for c in x["cells"] if c["cell_id"]!=(x.get("mcp") or {}).get("cell")]),
      "k6_iter":lambda x:x["iterations"].pop(len(x["iterations"])//2),
      "k6_ref":lambda x:x["cells"][0].__setitem__("evidence_ref","evidence/raw/__missing__.json"),
      "k6_noassert":lambda x:x.pop("assertions")}
for name,fn in muts.items():
    x=json.load(io.open(src,encoding="utf-8")); fn(x)
    io.open("_w34/mut/"+os.path.basename(src)+"."+name+".json","w",encoding="utf-8").write(json.dumps(x,ensure_ascii=False))
print("mutations written")
PY
for f in _w34/mut/*.json; do echo "== $f"; python _w34/k6_check.py "$f"; done
```

> W33 实跑输出（2026-11-19，原 w32 块）：正常态 `OK`；四条变异分别 `RED zero-value keys: ['FAIL']`、`RED mcp field missing`、`RED mcp claims cell F4_mcp but cells[] lacks it`、`RED iterations_total != len`（见 `_w33/k6_output.txt` 与 W33 验收物 §2.3）。
> W34 实跑输出（2026-11-19，参数化后对 w32 与 w33 双制品）：正常态 `OK feature/evidence invariants` ×2；`k6_ref` 首次真响 `RED missing ref evidence/raw/__missing__.json`（W33 制品 `K_evidence_ref` 此前无任何仓内必红输出）；其余四类同 W33。

## 判据 7 — CLOSED 行须带判据实跑输出指路（W33 新增；W35-03 加本波复核指路必红）

**出口判据来源**：W31-01 回开事故——销账证据是「人抄的机器结果」而非实跑输出；外审建议「CLOSED 行必须带判据实跑输出指路（文件加时间戳）」以防回开复发。W35-03 补：W34 同一提交把 8 行 `OPEN`→`CLOSED`，但原正则只查「证据列有任意指针形态」，不校验指针是否指向**本波复核** ⇒ 把本波复核指路删空后旧判据仍 OK（注入即假绿）；本波改为双输出。

**正常态（期望 OK×2）** —— ① W30 段起（新代数）的 `CLOSED` 行，证据列必须含 `文件:行` / `evidence/…` / CI run 号 / 「实跑」之一；W30 之前的历史行显式登记为已知缺口（不判红，避免把历史形态当新异常）。② **仅** W34 销账的 8 行（`CUR_IDS`，可用环境变量覆盖；锚定范围严格 = `W30-01/W30-02/W30-03/W31-01/W32-01/W33-01/W33-02/W33-03`，不扫全表 `CLOSED`），证据列必须同时含 `本波复核指路` + 本波号 `W34` + `§2.4` + `§2.5`（W34 侧写入的精确锚点为「`W35 本波复核指路**：W34 验收物 §2.4 加 §2.5 实跑摘录在案（8 行同车复核），状态维持 CLOSED`」，连接词是中文「加」、非半角 `+`；本检查只钉锚点语义 token、不假设连接符字面）。未在本波销账的历史行（如 W31-02/W31-03，停在 W33 复核、无 W34 指路）**不在 `CUR_IDS` 内、正常态不得判红**——否则旧判据会把「本来就没有」当「被删空」，必红变异失去区分力。检查逻辑落 `_w34/k7_check.py`：

```bash
REPO_ROOT="${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$REPO_ROOT" || exit 1
mkdir -p _w34
cat > _w34/k7_check.py <<'PY'
import io,re,os
txt=io.open(os.environ.get("LEDGER","docs/06-deferred-ledger.md"),encoding="utf-8").read()
rows=[l for l in txt.splitlines() if l.startswith("| W")]
cells=lambda l:[c.strip() for c in l.strip("|").split("|")]
closed=[l for l in rows if re.search(r"\|\s*`?CLOSED`?\s*\|",l)]
pat=re.compile(r"([\w./-]+\.(kt|md|json|yml|yaml|sh):\d+)|(evidence/)|(runs?/\d+)|\b\d{10,}\b|实跑")
new_wave=re.compile(r"\|\s*W(30|31|32|33|34)\s*\|\s*`?CLOSED`?")
bad=[]
for l in closed:
    if not new_wave.search(l): continue
    c=cells(l)
    if not pat.search(c[-1]): bad.append(c[0])
print("OK closed rows carry runnable pointers (W30+)" if not bad else "RED closed without runnable pointer: "+", ".join(bad))
cur_wave=os.environ.get("CUR_WAVE","W34")
cur_ids=[x for x in os.environ.get("CUR_IDS","W30-01,W30-02,W30-03,W31-01,W32-01,W33-01,W33-02,W33-03").split(",") if x]
cmap={cells(l)[0]:cells(l) for l in closed}
cur_bad=[]
for rid in cur_ids:
    c=cmap.get(rid)
    if c is None: cur_bad.append(rid+"(not CLOSED)"); continue
    ev=c[-1]
    if not ("本波复核指路" in ev and cur_wave in ev and "§2.4" in ev and "§2.5" in ev): cur_bad.append(rid)
print("OK closed rows carry current-wave pointers ("+cur_wave+")" if not cur_bad else "RED closed without current-wave pointer: "+", ".join(cur_bad))
PY
python _w34/k7_check.py
```

**必红变异 A（W33 原版，期望 RED）**：台账副本把 `W31-03` 证据列置空 → 重跑应输出 `RED closed without runnable pointer: W31-03`。

**必红变异 B（W35-03 新增，期望 RED）**：台账副本把某 CLOSED 行（示例 `W30-01`）证据列的本波复核指路删空 → 重跑第一条 `OK closed rows carry runnable pointers (W30+)` 仍 OK（历史自带指针还在），第二条必须输出 `RED closed without current-wave pointer: W30-01`。两条同时成立才证明新检查不是旧正则的复述：

```bash
REPO_ROOT="${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$REPO_ROOT" || exit 1
mkdir -p _w34/mut
python - <<'PY'
import io,re
txt=io.open("docs/06-deferred-ledger.md",encoding="utf-8").read()
out=[];hit=0
for l in txt.splitlines():
    if re.match(r"\|\s*W30-01\s*\|",l):
        p=l.split("|")
        p[-2]=re.sub(r"\*\*W35 本波复核指路\*\*：.*$","（本波复核指路已删空）",p[-2])
        l="|".join(p); hit+=1
    out.append(l)
io.open("_w34/mut/ledger.k7_nopointer.md","w",encoding="utf-8").write("\n".join(out)+"\n")
print("mut written; rows mutated =",hit)
PY
LEDGER=_w34/mut/ledger.k7_nopointer.md python _w34/k7_check.py
rm -f _w34/mut/ledger.k7_nopointer.md
```

> 变异有效性：删后该行仍是 6 列、`CLOSED` 态（否则会被 `closed` 过滤掉造成假绿）；换 `W33-03` 同样注入 ⇒ `RED closed without current-wave pointer: W33-03`，证明非硬编码单行。两条判据正常态/必红原文见文末 W35 摘录。

---

## W33 判据 3b 样本登记（哈希 + 拼装规则，不落明文）

> 按外审 P0-B「必红样本以哈希+规则入仓」：完整字面量不入仓，只登记可复现的拼装规则与产出哈希。
> 拼装规则 = `printf 'ref: %s%s\n' <段1> <段2>`（段1 取凭据文件名族 A 前三字符，段2 取其扩展名段；见判据 3b 必红块）。
> 样本产出 `sha256 = ac715262aade40df4890dc1bf3255827cf401bd1e75043091c8045162311ea9a`（样本长度 16 字节，含行尾换行）。
> 复核方式：本地按规则拼装 → `sha256sum` 对比；再 `grep -rniE` 扫该副本 → 必须输出 `RED credential filename hit`。W33 实跑已通过（正常态 `OK no credential filename`、必红 `RED credential filename hit`）。

---

---

## W34 判据实跑摘录（2026-11-19，仓根执行；参数化后）

> 命令均在仓库根（`harness-v2`）执行，不再依赖 `cd harness-v2`；判据 6 使用 `_w34/k6_check.py`（见上）。

```text
[判据 1] W30 rows = 3 | W31 rows = 3 | W32 rows = 3 | W33 rows = 3；W3x 段无重复/缺失号
[判据 2 正常态] all(verified)=True | all_verified=True | w29_device_executed=False
               OK(vacuous: positive control inherited, device NOT executed)
[判据 2 必红]   RED mutation invariant broken
[判据 3b 正常态] OK no credential filename
[判据 3b 必红]   ../_w29/mut/cred_probe.md:1:ref: <凭据文件名族 A> → RED credential filename hit
[判据 4 正常态]  w27-acceptance=0 | handoff-w27=0 | ledger=0
[判据 4 必红]    mut hits = 1 → RED affirmative wording remains
[判据 5 正常态]  iterations_total/len/sec4 = 6 6 6 | ok1=True | attempt keys==cell_ids=True | vacuous consistency=True → OK
[判据 5 必红]    RED iteration/state invariant broken
[判据 6 正常态]  w32 → OK feature/evidence invariants；w33 → OK feature/evidence invariants；w34 → OK feature/evidence invariants
[判据 6 必红 w32] k6_fail → RED zero-value keys: ['FAIL']; state domain
                  k6_nomcp → RED mcp field missing
                  k6_nocell → RED mcp claims cell F4_mcp but cells[] lacks it; no mcp cell without declaration; state sum; attempt keys
                  k6_iter → RED iterations_total != len
                  k6_ref  → RED missing ref evidence/raw/__missing__.json
[判据 6 必红 w33] 同上五类全 RED（k6_ref 为 K_evidence_ref 首次仓内必红实证）
[判据 7 正常态]   OK closed rows carry runnable pointers (W30+)（本波销账 8 行后复跑）
```

> 判据 6 原 W33 版的两处不可执行根因（`cd harness-v2` 在仓根被吞、`../_w33/mut/` 未预建）已修；`ARTIFACT` 可指向任一 `evidence/*-matrix-summary.json`，W34 起新制品默认同样可查。

---

## W35 判据 6/7 加固实跑摘录（2026-11-19，零 `.kt`；先实跑后落笔）

> 按 W35-03 出口判据「判据 6 在无 `.git` 快照仍 rc=0 且必红 RED；判据 7 正常态 OK 加注入删空后 RED」。全部在现役树仓根实跑，输出原文如下；`K_assertion_presence` 注入前先确认 w33 正常态 OK，注入后只出单条 `RED assertions missing`（唯一错误、非连带 state/attempt 报错）才落笔写「会响」。

```text
[判据 6 正常态·w33 有 assertions] OK feature/evidence invariants
[判据 6 必红 K_assertion_presence·w33 删 assertions 字段]
                                RED assertions missing
[判据 6 正常态·w34（W35 补回三字段后）] OK feature/evidence invariants
[判据 6 必红·w34 六类]
  k6_fail     → RED zero-value keys: ['FAIL']; state domain
  k6_nomcp    → RED mcp field missing
  k6_nocell   → RED mcp claims cell F4_mcp but cells[] lacks it; no mcp cell without declaration; state domain; state sum; attempt keys
  k6_iter     → RED iterations_total != len
  k6_ref      → RED missing ref evidence/raw/__missing__.json
  k6_noassert → RED assertions missing
[判据 6·无 .git 快照 REPO_ROOT 兜底] REPO_ROOT=<无 .git 快照目录>；git rev-parse 失败 ⇒ 退回 pwd；
                                   正常态 rc=0 `OK feature/evidence invariants`；
                                   删 assertions 变异 rc=0 `RED assertions missing`
[判据 7 正常态] OK closed rows carry runnable pointers (W30+)
                OK closed rows carry current-wave pointers (W34)
[判据 7 必红 B·副本删空 W30-01 本波复核指路]
                OK closed rows carry runnable pointers (W30+)   ← 旧正则仍绿，证明新检查非复述
                RED closed without current-wave pointer: W30-01
[判据 7 必红 B·换 W33-03 复核非硬编码] RED closed without current-wave pointer: W33-03
[判据 7 范围负控·W31-02 历史 CLOSED 行（本就无锚点、不在 CUR_IDS）]
                OK closed rows carry runnable pointers (W30+)
                OK closed rows carry current-wave pointers (W34)   ← 不得判红，否则「本来就没有」被当「被删空」
```

> 变异有效性：判据 7 删指路后该行仍是 6 列且 `CLOSED`（否则会被 `closed` 过滤造成假绿——即「删末尾仍连续」式无效变异已排除）；锚点字面量按现役树 grep 为 `**W35 本波复核指路**：W34 验收物 §2.4 加 §2.5 实跑摘录在案（8 行同车复核），状态维持 CLOSED`（中文「加」，非半角 `+`），必红注入以 `\*\*W35 本波复核指路\*\*：` 前缀整段删空，不误删前一段 `W34 销账`；范围严格 = 8 个 `CUR_IDS`，W31-02/03 历史行不参与（已实跑负控）。判据 6 的 `K_assertion_presence` 对补字段前的现役 w34（28 键）同检查亦输出 `RED assertions missing`，是该缺陷的真实正控，不是设计出来的恒绿式。仓内逐条原始输出另见 `_w34/w35_k6_run.txt`、`_w34/w35_k7_run.txt`（工作区副本，不入仓）。

## 本 checklist 覆盖 / 不覆盖（诚实标注）

- **覆盖**：判据 1（1a 判红分支）/ 2（verified 不变量 + 三态分支 + 可跑必红块）/ 3（3a tip token + 3b 仓内 grep + 注入样本）/ 4（三处同步 + 可跑必红块）/ 5（制品迭代/attempt_states/state 一致性 + 必红块）/ 6（面定义 AND + evidence_ref 可复核 + 出厂断言存在 `K_assertion_presence`，W33 新增 / W35 加固为六类必红 + `REPO_ROOT` 兜底）/ 7（CLOSED 行实跑指路 + 本波复核指路必红，W33 新增 / W35 加固）。
- **不覆盖**：① `arch-guard.sh` 的 ID 唯一性（用户裁决域）；② 真机重放（制品 verified 多为规则自证，真机判据在验收物 §3/§4）；③ 仓外生成器断言 M（老号 W20-14）；④ 脱敏正控工具在仓外取证设施（按截图回灌级别只回灌判据，不回灌整段日志）。
- **四态**：正常态命令全 OK 仅说明「载体存在且当前数据自洽」，不等于挂账 `CLOSED`；改 `CLOSED` 须判据实跑输出入账 + 制品与真值源一致（W30 已按外审 W30-03 处方删除「须真机取证」错配条件，由主理人单人落笔）。
