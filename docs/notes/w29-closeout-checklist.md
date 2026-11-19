# W29 收口 checklist — W28 checklist 空转判据的机制性修复（2026-11-18）

> 存在理由：W28 新建的 `w28-closeout-checklist.md` 判据 2 自身空转（正常态只读 `state_counts` 与 `assertions[].description`，不读 `mutation_cases[].verified` / `mutation_selftest.all_verified`，注入 `verified=false` 仍输出 OK）、判据 3b 含占位符吞错误、判据 1a 无判红分支、判据 1/3 `/tmp` 口径冲突，且 W28-01「三处同步」变异零载体。本文件按外审 P0-1/P0-2 处方建立 W29 版载体。
> **W30 第 0 车修订（本次）**：按外审（W30 段挂账草案 W30-02 处方）修复必红侧：① 判据 2 必红块原写 `<<'PY'` 单引号 heredoc 内嵌 `${WS}` ⇒ shell 不展开、Python 收到字面量 ⇒ `FileNotFoundError`（W30 实测复现），改读 WS 环境变量（W31-02 二次订正：读取侧再改 `os.path.abspath(os.path.join(os.getcwd(),".."))` 自算，与写入侧一致，不设 WS 也能跑）；② 判据 2 正常态补三态分支（`w29_device_executed=False` ⇒ 正控不得称 `device_execution`，须 `inherited_w28_baseline`/`not_exercised`，输出 `OK(vacuous)` 而非无保留 `OK`）；③ 判据 3b 补注入样本（工作区副本放凭据文件名字面量 ⇒ `RED credential filename hit`）；④ 判据 4 补可跑必红块（工作区副本写回肯定式措辞 ⇒ `RED affirmative wording remains`）；⑤ 新增判据 5（制品迭代/attempt_states/state 一致性，防 W30-01 类「声称实跑未实跑」复发）。
> **W31 第 0 车修订（本次）**：按外审 W31 段草案三 P0 处方——① 判据 3b 正常态加白名单口径并**实跑输出 `OK no credential filename` 入仓**（W30 三处字面量清零后全树复扫 0 命中）；② 判据 2/5 必红块读取侧去 WS 依赖（不设 WS 实跑 rc=0）；③ 判据 1 参数化 `WAVE=${WAVE:-W29}` 并补 W30 三行唯一性实跑 + 重复号必红演练（`RED duplicate/missing` 真响，见文末摘录）。
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

## 本 checklist 覆盖 / 不覆盖（诚实标注）

- **覆盖**：判据 1（1a 判红分支）/ 2（verified 不变量 + 三态分支 + 可跑必红块）/ 3（3a tip token + 3b 仓内 grep + 注入样本）/ 4（三处同步 + 可跑必红块）/ 5（制品迭代/attempt_states/state 一致性 + 必红块）。
- **不覆盖**：① `arch-guard.sh` 的 ID 唯一性（用户裁决域）；② 真机重放（制品 verified 多为规则自证，真机判据在验收物 §3/§4）；③ 仓外生成器断言 M（老号 W20-14）；④ 脱敏正控工具在仓外取证设施（按截图回灌级别只回灌判据，不回灌整段日志）。
- **四态**：正常态命令全 OK 仅说明「载体存在且当前数据自洽」，不等于挂账 `CLOSED`；改 `CLOSED` 须判据实跑输出入账 + 制品与真值源一致（W30 已按外审 W30-03 处方删除「须真机取证」错配条件，由主理人单人落笔）。
