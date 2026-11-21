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

## 判据冻结声明（用户 2026-11-20 指令，W37 起生效）

> **定版即冻结**：本 checklist 的判据编号（1–7）、矩阵 schema `harness-matrix-summary/v1`、四态枚举、
> 扫描面口径、字段最小集（34 顶层键）自 W37 起**不再扩张**。用户原话口径：「判据升级→全空转→重新取证」
> 的循环必须停止，既有口径固化、不再逐波追加。
>
> **唯一允许的三类动作**（都属于「让既有判据真会响」，不是新增判据）：
> ① 给既有断言补可跑必红注入块（删 `mutation_cases` / 数量不等 / selftest 无仓内指针）；
> ② 把已声明但无检查逻辑的断言（`K_privacy_no_key` / `K_coverage`）补进 `k6_check.py`；
> ③ 判据 7 默认值从「当波新 `CLOSED` 行」自动推导（消除 `CUR_WAVE=W34` 历史锚定）。
>
> **禁止**：新增判据编号 / 新建 checklist 或矩阵制品 / 新增 CI job / 改四态枚举 / 重出历史制品 /
> 因新发现升级判据再重新取证。缺陷一律「内容订正 + 老号行内注记 + 销账」。
>
> **「内容订正」与「重出历史制品」的边界**（W39 第 0 车补钉，防止把两者混为一谈）：
> **局部内容订正（错数 / 措辞 / 命名 / note 指向）≠ 重出历史制品**；**重出 = 改 `state_counts` /
> `cells` / `assertions` 结论或新增格**。前者照做且必须留 diff，后者一律禁止。
>
> **定版契约（v1，只增不改语义）**：
> - 数据层 `cells[].state ∈ {PASS, PARTIAL, VACUOUS, FAIL}`（大写四值域；`state_counts` **只列非零出现值**，`sum == len(cells)`）；
> - 显示层固定 `PASS→✅ / PARTIAL→⚠️ / VACUOUS→⛔vacuous / FAIL→⛔`；`FAIL` 是合法预留态，出现必须带必红用例、不得静默；
> - `declared_feature_faces` 值域（v1 定版，W38 补钉，防止 W36 8 面→W37 7 面静默换名）：`{endpoint_axis, context_tier, vision, workspace, skill, mcp, compression}`（7 值）；换名/增删须用户显式批准 + 台账单人落笔注记，不得静默漂移；
> - 「未触发」≠「已验证通过」；`VACUOUS` 必须有 `unexercised_reason`，`PARTIAL` 必须写清缺哪一半；
> - 既有 12 条口径固化：正控先行 strict sentinel；写 X 未发生前先自证判据会响（先 RED 再落笔）；
>   `evidence_ref` 100% 仓内存在；制品三字段齐 + 数量相等 + selftest 仓内指针；`CLOSED` 行须带本波复核指路；
>   派生数不写死（以实跑为准）；台账单人落笔 / 6 列无裸管道符 / 号段预检；G1–G4 与 G20 实跑；
>   敏感串拼装不复述完整值、被 `-f` 纳入先过脱敏；收尾契约波内回改主体；推断带 caveat；历史行不冒充本波。

## 判据 1 — 挂账号段唯一性（含 1a 判红分支）

**出口判据来源**：W29 段首行号段自由确认 + 重复号检测。

**正常态（期望 OK）** —— 修 W28「grep 只打印数字、无判红」（W31-02 参数化 `WAVE`，默认 W29、可传 W30/W31；行数期望按各段实际行数）。**W39 第 0 车自洽修订（原版在仓根必失败且锚历史波）**：① `cd harness-v2` 去掉，改 `REPO_ROOT` 兜底（同判据 7 第 396 行写法）——原版在仓根实跑 stderr `bash: cd: harness-v2: No such file or directory` 后仍继续、静默用 `W29`；② 默认 `WAVE` = 台账**最大段号**（`### W<n> 段`），无参数实跑即当波；③ 默认 `EXPECT` = 该段声明的 `本波新开挂账 N`（**与守恒式同源**），解析不到只回显 `RED 缺…声明`，**不回落 3**——原版 `EXPECT=3` 对 W38 段（0 行新开）恒误判 `RED W38 row count != 3`。

```bash
REPO_ROOT="${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$REPO_ROOT" || exit 1
LEDGER=${LEDGER:-docs/06-deferred-ledger.md}
# W39 第 0 车：默认 WAVE = 台账最大段号（无参数实跑即当波，不再锚 W29）
WAVE=${WAVE:-$(grep -oE "^### W[0-9]+ 段" "$LEDGER" | grep -oE "W[0-9]+" | sort -t W -k2 -n | tail -1)}
[ -n "$WAVE" ] || echo "RED 缺 WAVE（台账无 ### W<n> 段）"
# W39 第 0 车：默认 EXPECT = 该段声明的「本波新开挂账 N」（守恒式同源）；解析不到 ⇒ 判红，不回落 3
EXPECT=${EXPECT:-$(awk -v h="### $WAVE 段" '/^### /{f=(substr($0,1,length(h))==h)} f && /本波新开挂账/ {if(match($0,/本波新开挂账[ \t]*[0-9]+/)){s=substr($0,RSTART,RLENGTH);gsub(/[^0-9]/,"",s);print s;exit}}' "$LEDGER")}
[ -n "$EXPECT" ] || echo "RED 缺 $WAVE 段「本波新开挂账 N」声明（不回落默认值）"
n=$(grep -cE "^\| $WAVE-" "$LEDGER"); echo "$WAVE rows = $n"
[ -z "$EXPECT" ] || [ "$n" -eq "$EXPECT" ] || echo "RED $WAVE row count != $EXPECT (got $n)"
for id in $(grep -oE "^\| $WAVE-[0-9]+" "$LEDGER" | sed 's/^| //'); do
  c=$(grep -cE "^\| ${id} " "$LEDGER"); echo "$id -> $c"; [ "$c" -eq 1 ] || echo "RED duplicate/missing: $id"
done
# 守恒式两数回显（W38 P1-3 起；W39 第 0 车与 EXPECT 同源）：只回显、不新增判据编号
seg=$(grep -A3 "^### $WAVE 段" "$LEDGER" | grep "本波新开挂账" | head -1)
echo "守恒式两数回显（$WAVE）：${seg:-RED 缺本波新开/销账声明（判据7将空过 declared=0）}"
```

**必红变异（期望 RED）**：工作区副本追加一条重复行 → `LEDGER=<副本> WAVE=W30 EXPECT=3 bash <上面循环>` 应输出 `RED W30 row count != 3` 与 `RED duplicate/missing`。

> W39 第 0 车注：`EXPECT` 现在默认由段头「本波新开挂账 N」推导，而 **W29/W30 历史段头无该声明** ⇒ 对历史段必须显式传 `EXPECT=3`。**这本身就是负控**：不传 `EXPECT` 跑 `WAVE=W30` 只输出 `RED 缺 W30 段「本波新开挂账 N」声明（不回落默认值）`、不打印行数判红、更不会回落成 3 —— 证明「不回落 3」生效。W29 波已实跑（见 §5.4 证据）；W31 第 0 车对 W30 段演练通过（见文末 W31 摘录）。

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
# K_assertion_presence（W36-02 扩展）：三字段齐 + 数量相等 + selftest 指向仓内可跑输出
for f in ("assertions","mutation_cases","mutation_selftest"):
    if f not in d: errs.append(f+" missing")
if all(isinstance(d.get(f),list) for f in ("assertions","mutation_cases")):
    na=len(d["assertions"])
    if na!=len(d["mutation_cases"]): errs.append("assertion/mutation count mismatch")
    st=d.get("mutation_selftest")
    if isinstance(st,dict):
        if st.get("total")!=na or st.get("rejected")!=na: errs.append("selftest count mismatch")
        note=str(st.get("note") or "")
        if "checklist" not in note and "验收物" not in note: errs.append("selftest note lacks in-repo pointer")
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
# K_privacy_no_key（W37 补齐，判据冻结动作②）：制品全文不得含密钥形态（排除 sha256 误伤）
import re as _re
_raw=json.dumps(d,ensure_ascii=False)
if _re.search(r"sk-[A-Za-z0-9]{16,}|github_pat_[A-Za-z0-9_]{20,}|nvapi-[A-Za-z0-9_-]{20,}",_raw): errs.append("privacy: key-like token")
# K_coverage（W38 P0-1 订正）：格数由 cells_total 自证，不钉死 13（兼容 w32=14 / w37=13）
if not isinstance(d.get("cells_total"),int) or d["cells_total"]<=0 or d["cells_total"]!=len(cells):
    errs.append("coverage cells != cells_total")
for c in cells:
    if not os.path.exists(c["evidence_ref"]): errs.append("missing ref "+c["evidence_ref"])
print("OK feature/evidence invariants" if not errs else "RED "+"; ".join(errs))
PY
ARTIFACT="${ARTIFACT:-evidence/w32-matrix-summary.json}"
python _w34/k6_check.py "$ARTIFACT"
```

**必红变异（期望 RED，十一类；W37 补齐三类断言可执行性必红 + `K_privacy_no_key`/`K_coverage` 自证必红，属冻结声明允许的动作①②）**：工作区副本注入后逐条重跑 `_w34/k6_check.py`（`K_evidence_ref` 必红 = 把某格 `evidence_ref` 指向不存在文件；`K_assertion_presence` 必红 = 删顶层 `assertions` 字段）。**W39 第 0 车命名归一**：注入键 `k6_cov12` 改 `k6_covpop`（原键名带 W37 那版 w36 的 12 格数字，与摘录行 `k6_covpop` 不一致 ⇒ 同一变异两个名字；覆盖断言已随 W38 P0-1 去硬编码归一为 `K_coverage`，键名不再带格数后缀）：

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
      "k6_noassert":lambda x:x.pop("assertions"),
      # W37 三类断言可执行性必红（冻结动作①）
      "k6_nomut":lambda x:x.pop("mutation_cases"),
      "k6_count":lambda x:x["mutation_cases"].pop(),
      "k6_noptr":lambda x:x["mutation_selftest"].__setitem__("note","自报：无仓内指针"),
      # W37 两条新断言自证必红（冻结动作②）
      "k6_privkey":lambda x:x.__setitem__("privacy","sk-"+("A"*24)),
      "k6_covpop":lambda x:x["cells"].pop()}
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

**正常态（期望 OK×2）** —— ① W30 段起（新代数）的 `CLOSED` 行，证据列必须含 `文件:行` / `evidence/…` / CI run 号 / 「实跑」之一；W30 之前的历史行显式登记为已知缺口（不判红，避免把历史形态当新异常）。② **仅** W34 销账的 8 行（`CUR_IDS`，可用环境变量覆盖；锚定范围严格 = `W30-01/W30-02/W30-03/W31-01/W32-01/W33-01/W33-02/W33-03`，不扫全表 `CLOSED`），证据列必须同时含 `本波复核指路` + 本波号 `W34` + `§2.4` + `§2.5`（W34 侧写入的精确锚点为「`W35 本波复核指路**：W34 验收物 §2.4 加 §2.5 实跑摘录在案（8 行同车复核），状态维持 CLOSED`」，连接词是中文「加」、非半角 `+`；本检查只钉锚点语义 token、不假设连接符字面）。未在本波销账的历史行（如 W31-02/W31-03，停在 W33 复核、无 W34 指路）**不在 `CUR_IDS` 内、正常态不得判红**——否则旧判据会把「本来就没有」当「被删空」，必红变异失去区分力。检查逻辑落 `_w34/k7_check.py`（**W37 起默认值自动推导**：`CUR_WAVE` = 台账最大波号、`CUR_IDS` = 该波 `CLOSED` 行、`ANCHOR_SECS` 默认空；显式传参仍可覆盖）：

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
new_wave=re.compile(r"\|\s*W(\d+)\s*\|\s*`?CLOSED`?")   # W36-02：波次无关（W30 及以后），不再枚举具体波号
MIN_WAVE=int(os.environ.get("MIN_WAVE","30"))   # 新代数下界，可用环境变量覆盖
bad=[]
for l in closed:
    m=new_wave.search(l)
    if not m or int(m.group(1))<MIN_WAVE: continue
    c=cells(l)
    if not pat.search(c[-1]): bad.append(c[0])
print("OK closed rows carry runnable pointers (W30+)" if not bad else "RED closed without runnable pointer: "+", ".join(bad))
# W37（判据冻结动作③）：默认从台账段头「本波销账」声明自动推导，不再锚历史波号——
# CUR_WAVE 默认 = 台账最大波号；CUR_IDS 默认 = 最新段「本波销账[:：]…」里列出的 ID
# （W39 第 0 车：冒号可省 + 只在最后一个 `### W<n> 段` 块内取声明，见下方 if not cur_ids）。
# 空命中语义：无该声明 ⇒ 输出 ids=0（合法：本波未销账），不静默冒充「全部通过」。
cur_wave=os.environ.get("CUR_WAVE","")
cur_ids=[x for x in os.environ.get("CUR_IDS","").split(",") if x]
# 锚点段号默认空（自动模式只钉「本波复核指路 + 当波号」）；显式传 ANCHOR_SECS 才做段级加强检查。
anchor_secs=[s for s in os.environ.get("ANCHOR_SECS","").split(",") if s]
if not cur_wave:
    _secs=[int(w) for w in re.findall(r"^### W(\d+) 段",txt,re.M)]
    _ids=[int(w) for w in re.findall(r"^\|\s*W(\d+)-\d+\s*\|",txt,re.M)]
    _all=_secs+_ids
    cur_wave="W%d"%max(_all) if _all else "W0"
if not cur_ids:
    # W39 第 0 车两条容错（原版 `r"本波销账[:：]\s*([^\n]+)"` + 全文 `[-1]` 的双重缺陷）：
    #   ① 冒号可省 —— W38 段头实际写 `**本波新开挂账 0 / 本波销账 1（W17-02）**`（空格无冒号），
    #      原版不匹配 ⇒ `_decls[-1]` 退回 W37 段尾的 W36 三行 ⇒ 输出
    #      `RED closed without current-wave pointer: W36-01, W36-02, W36-03`（本波真销的 W17-02 零覆盖）。
    #   ② 只取最后一个 `### W<n> 段` 块内的声明 —— 防文件尾部无关文本让 `[-1]` 取错。
    _blocks=re.split(r"^### W\d+ 段",txt,flags=re.M)
    _last=_blocks[-1] if len(_blocks)>1 else txt
    _decls=re.findall(r"本波销账\s*[:：]?\s*([^\n]+)",_last)
    if _decls:
        cur_ids=re.findall(r"W\d+-\d+",_decls[-1])
cmap={cells(l)[0]:cells(l) for l in closed}
cur_bad=[]
for rid in cur_ids:
    c=cmap.get(rid)
    if c is None: cur_bad.append(rid+"(not CLOSED)"); continue
    ev=c[-1]
    if not ("本波复核指路" in ev and cur_wave in ev and all(s in ev for s in anchor_secs)): cur_bad.append(rid)
print(("OK closed rows carry current-wave pointers ("+cur_wave+", declared="+str(len(cur_ids))+")") if not cur_bad else "RED closed without current-wave pointer: "+", ".join(cur_bad))
PY
python _w34/k7_check.py
```

**必红变异 A（W33 原版，期望 RED）**：台账副本把 `W31-03` 证据列置空 → 重跑应输出 `RED closed without runnable pointer: W31-03`。

**必红变异 B（W35-03 新增，期望 RED；W39 第 0 车改为「按当前段声明自动取目标行」）**：台账副本把**本波销账声明里的 CLOSED 行**证据列的本波复核指路删空 → 重跑第一条 `OK closed rows carry runnable pointers (W30+)` 仍 OK（历史自带指针还在），第二条必须输出 `RED closed without current-wave pointer: <该行 ID>`。两条同时成立才证明新检查不是旧正则的复述（W39 实测目标 = `W17-02`；该行 `W17 < MIN_WAVE` ⇒ 第一条**结构上**不覆盖它，只有本波指路分支能响）：

```bash
REPO_ROOT="${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$REPO_ROOT" || exit 1
mkdir -p _w34/mut
python - <<'PY'
import io,re
txt=io.open("docs/06-deferred-ledger.md",encoding="utf-8").read()
# 目标行 = 最后一个 `### W<n> 段` 的「本波销账」声明里的 ID（与 k7_check.py 同源，不硬编码波号）
blocks=re.split(r"^### W\d+ 段",txt,flags=re.M)
decls=re.findall(r"本波销账\s*[:：]?\s*([^\n]+)",blocks[-1])
ids=re.findall(r"W\d+-\d+",decls[-1]) if decls else []
out=[];hit=0
for l in txt.splitlines():
    if any(re.match(r"\|\s*"+i+r"\s*\|",l) for i in ids):
        p=l.split("|")
        p[-2]=re.sub(r"\*\*W\d+ 本波复核指路\*\*：.*$","（锚点已删空）",p[-2])
        l="|".join(p); hit+=1
    out.append(l)
io.open("_w34/mut/ledger.k7_nopointer.md","w",encoding="utf-8").write("\n".join(out)+"\n")
print("mut written; rows mutated =",hit,"; targets =",",".join(ids))
PY
LEDGER=_w34/mut/ledger.k7_nopointer.md python _w34/k7_check.py
rm -f _w34/mut/ledger.k7_nopointer.md
```

> 变异有效性：删后该行仍是 6 列、`CLOSED` 态（否则会被 `closed` 过滤掉造成假绿）；W35 波换 `W33-03` 同样注入 ⇒ `RED closed without current-wave pointer: W33-03`，证明非硬编码单行。**占位串不得含 `本波复核指路`**（W39 实测：原占位 `（本波复核指路已删空）` 自带搜索 token「本波复核指路」⇒ 注入后仍命中、判据不响，是**假变异**；已改 `（锚点已删空）` 才真响）。**范围负控（W39 补）**：把同样注入打在**不在 cur_ids 的**历史 CLOSED 行（如 `W30-01`）⇒ 第二条不判红 —— 这是正确语义（「本来就没销」不等于「被删空」），不是漏检。两条判据正常态/必红原文见文末 W35 与 W38 摘录。

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

---

## W36 第 0 车判据加固 — 三处扫描面（零 `.kt`，先实跑后落笔）

> 按外审 W36-01/02 处方：① 判据 3b 扫描面扩「设备标识 / 本机绝对路径 / PAT 长度元信息」三类（原只扫凭据文件名族，serial 与本机路径四类回退全绿）；② 判据 6 `K_assertion_presence` 扩成三字段齐 + 数量相等 + selftest 仓内指针；③ 判据 7 波次参数化（`new_wave` 改波次无关正则 `W\d+` + `MIN_WAVE` 下界、`CUR_WAVE`/`CUR_IDS`/`ANCHOR_SECS` 环境变量覆盖）消除「锚定 W34 历史集合」盲区，不再枚举具体波号（方案席 W36 裁决意见）。全部在现役树仓根实跑，输出原文如下；敏感串按 W31-01 范本拼装摘录，避免判据自身制造正常态命中。

### 判据 3b-ext（正常态 OK + 必红 RED）

```bash
S1="1330""9cc8"      # 8 位十六进制设备标识，两段拼装避免本文件自命中
P1="D:/WB""fil"      # 本机绝对路径形态 1（盘符）
P2="/d/WB""fil"      # 本机绝对路径形态 2（Git Bash 挂载）
M1="长度 ""129"      # PAT 长度元信息
grep -rniE "$S1|$P1|$P2|$M1" docs evidence 2>/dev/null && echo "RED sensitive leak hit" || echo "OK no device-id/path/pat-meta"
```
- 正常态（现役树，六处已清零改占位）：`OK no device-id/path/pat-meta`
- 必红（仓外副本放设备标识字面量）：`RED sensitive leak hit`（命中行含完整标识，按拼装规则摘录：`leak: <段1><段2>`）

### 判据 6 扩展（正常态四制品 OK + 三类必红 RED）

- 正常态：w32/w33/w34/w35 四制品均 `OK feature/evidence invariants`（4/4）。
- 必红（对 w35 副本注入，逐条重跑 `_w34/k6_check.py`）：
  - 删 `mutation_cases` ⇒ `RED mutation_cases missing`
  - 数量不等（mutation_cases 删一条）⇒ `RED assertion/mutation count mismatch`
  - selftest 无仓内指针（note 改自报）⇒ `RED selftest note lacks in-repo pointer`
- 原六类必红（k6_fail/nomcp/nocell/iter/ref/noassert）保持 RED 不变；扩展只新增三类、不改既有语义。

### 判据 7 波次参数化（正常态双 OK + 两类必红 RED）

- 正常态（现役树，默认 W34 销账 8 行）：`OK closed rows carry runnable pointers (W30+)` + `OK closed rows carry current-wave pointers (W34)`。
- 必红 B（仓外副本删 W30-01 本波复核指路，默认 W34）：第一条仍 OK + `RED closed without current-wave pointer: W30-01`（双输出区分）。
- 必红 C（仓外副本把 W35-01/02/03 状态改 `CLOSED` 且不带本波锚点，`CUR_WAVE=W35 CUR_IDS=W35-01,W35-02,W35-03`）：`RED closed without current-wave pointer: W35-01, W35-02, W35-03`——**证明 W35 三行将来销账时受判据 7 约束，波次盲区已消除**（W35 加固前的脚本对同样注入仍输出双 OK）。
- 无效变异排除：必红 C 首版只改了行内字样未真正翻状态列，判据输出 `not CLOSED`（判据未被正确行使）；改用按 `|` 切分精确翻第 5 列状态为 `CLOSED` 后，才得到上面的真 RED。负控：现役树 W35 三行仍 `OPEN`，同样参数化输出 `not CLOSED`（正确，非缺陷）。
- `ANCHOR_SECS` 参数化默认 `§2.4,§2.5`，新波号段可覆盖，避免把段号硬编码成新的锚定集合。

> 边界：以上判据脚本位于仓外工作区 `_w34/`（不入仓），可执行原文已内联于本 checklist 判据 6/7 代码块；`_w36/` 为仓外注入副本，均不入库。改 `CLOSED` 仍由主理人单人落笔、待外审复核，本波 W35 三行与 W36 三行维持 `OPEN`。

## W37 判据冻结补齐实跑输出摘录（2026-11-20）

> 依据：判据冻结声明「唯一允许三类动作」——① 三类断言必红可跑注入块；② `K_privacy_no_key`/`K_coverage` 进 `k6_check.py`（W39 第 0 车命名归一：本条原写带格数后缀的旧名，与冻结声明第 33 行及 W38 P0-1 去硬编码后的断言名不一致，属措辞订正）；③ 判据 7 默认从台账自动推导。
> 脚本自本文件代码块提取后执行，命令均在仓库根；`ARTIFACT=evidence/w36-matrix-summary.json`；输出原文摘录，不含敏感字面量。

**判据 6 正常态**：`OK feature/evidence invariants`

**判据 6 十一类必红（W37 新增五类；其余六类同 W36）**：

```
k6_nomut   => RED mutation_cases missing
k6_count   => RED assertion/mutation count mismatch
k6_noptr   => RED selftest note lacks in-repo pointer
k6_privkey => RED privacy: key-like token
k6_covpop  => RED state sum; attempt keys; coverage cells != cells_total
k6_fail    => RED zero-value keys: ['FAIL']; state domain
k6_nomcp   => RED mcp field missing
k6_nocell  => RED mcp claims cell F4_mcp but cells[] lacks it
k6_iter    => RED iterations_total != len
k6_ref     => RED missing ref evidence/raw/__missing__.json
k6_noassert=> RED assertions missing
```

**判据 7 正常态（默认自动推导，不传 `CUR_WAVE`/`CUR_IDS`/`ANCHOR_SECS`）**：

```
OK closed rows carry runnable pointers (W30+)
OK closed rows carry current-wave pointers (W37, declared=3)
```

**判据 7 必红（注入：`W36-01`/`W36-03` 翻 `CLOSED` 且删空本波指路 + 段头声明销账）**：

```
OK closed rows carry runnable pointers (W30+)
RED closed without current-wave pointer: W36-01, W36-03
```

> 空命中语义：无「本波销账：…」声明时输出 `declared=0` 且不判红（合法：本波未销账），不静默冒充「全部通过」。

## W38 判据内容订正实跑输出摘录（2026-11-20，W39 第 0 车；零 `.kt`）

> 依据：W38 外审 P0——① 判据 7 正则**硬要求冒号**且全文 `_decls[-1]`；② 判据 1 `cd harness-v2` 在仓根必失败、`WAVE` 默认锚 `W29`、`EXPECT` 默认 `3`。
> 全部在现役树**仓根**实跑（本车不跑 gradle、不跑 `scripts/arch-guard.sh`——另一席正在搬 `.kt`，守卫会读到中间态），下文为输出原文。
> 顺序：**先复现缺陷（改前）→ 再改脚本 → 再双态复跑（改后）**，不做事后编造。

### A. 缺陷复现（改前；台账取 `git show HEAD:docs/06-deferred-ledger.md`，即 W38 收尾提交原状）

```text
[旧判据 7 · HEAD 台账]
OK closed rows carry runnable pointers (W30+)
RED closed without current-wave pointer: W36-01, W36-02, W36-03
  ← 本波真销的 W17-02 零覆盖；报的是 W37 段尾那三行。默认「正常态」被误判成红。

[旧判据 7 · 现役树把「本波销账：W17-02」的冒号去掉]
OK closed rows carry runnable pointers (W30+)
RED closed without current-wave pointer: W36-01, W36-02, W36-03
  ← 冒号硬要求实证：无冒号 ⇒ 不匹配 ⇒ `_decls[-1]` 跨波退回 W37 段尾声明。

[旧判据 1 · 原样无参数（在仓根执行）]
bash: line 1: cd: harness-v2: No such file or directory
W29 rows = 3
W29-01 -> 1
W29-02 -> 1
W29-03 -> 1
守恒式两数回显（W29）：RED 缺本波新开/销账声明（判据7将空过 declared=0）
  ← `cd` 失败不中断，静默锚到 W29；本波（W38）完全没被检查。

[旧判据 1 · 只把 WAVE 改 W38，EXPECT 仍默认 3]
W38 rows = 0
RED W38 row count != 3 (got 0)
  ← 段头声明「本波新开挂账 0」，`EXPECT` 回落 3 ⇒ 恒误判红。
```

四态：⛔ 三条缺陷全部现场复现（判据 7 误报红 / 判据 1 stderr + 错波锚定 + EXPECT 回落）。

### B. 改后 — 判据 1（✅ 正常态无 RED + 负控不回落 + 当前波必红真响）

```text
[正常态·无参数·仓根]
W38 rows = 0
守恒式两数回显（W38）：### W38 段（2026-11-20，0 行新开：**本波新开挂账 0 / 本波销账 1（W17-02）**；全面推进 + 判据内容订正 + 真机多模型矩阵）
  ← 无 `RED`、无 `can't cd`；`WAVE` 自动推导为 W38、`EXPECT` 自动取段头 0。

[负控·WAVE=W30 不传 EXPECT（W30 段头无「本波新开挂账」声明）]
RED 缺 W30 段「本波新开挂账 N」声明（不回落默认值）
W30 rows = 3
W30-01 -> 1
W30-02 -> 1
W30-03 -> 1
守恒式两数回显（W30）：RED 缺本波新开/销账声明（判据7将空过 declared=0）
  ← 只报「缺声明」，**没有** `row count != 3` ⇒ 证明未回落 3。

[必红·工作区副本给 W38 加一行假新开 W38-01]
W38 rows = 1
RED W38 row count != 0 (got 1)
W38-01 -> 1
```

### C. 改后 — 判据 7（✅ 正常态双 OK；注入 A 第二条真 RED）

```text
[正常态·现役树·无参数]
OK closed rows carry runnable pointers (W30+)
OK closed rows carry current-wave pointers (W38, declared=1)

[注入 A：删 W17-02 的「W38 本波复核指路」段]
mut written; rows mutated = 1 ; targets = W17-02
OK closed rows carry runnable pointers (W30+)
RED closed without current-wave pointer: W17-02
  ← 第一条仍 OK：W17-02 的 `W17 < MIN_WAVE` 结构上不被旧分支覆盖 ⇒ 新检查不是旧正则复述。
  ← 该行删后仍 6 列、状态 `CLOSED`（cols = 6 / status = `CLOSED`），不会被 `closed` 过滤造成假绿。

[注入 B1：只删「> **本波销账：W17-02**」声明行，段头无冒号的「本波销账 1（W17-02）」仍在]
W38 块内剩余「本波销账」出现次数 = 1
OK closed rows carry runnable pointers (W30+)
OK closed rows carry current-wave pointers (W38, declared=1)
  ← 冒号容错实证：段头那个「本波销账 1（W17-02）」（空格、无冒号）被识别，不退回 W36 三行。

[注入 B2：段头与声明行两处同删（真删）]
OK closed rows carry runnable pointers (W30+)
OK closed rows carry current-wave pointers (W38, declared=0)
  ← `declared=0` 可见且不判红（合法：本波未销账），不静默冒充「全部通过」。

[恢复后复跑]
OK closed rows carry runnable pointers (W30+)
OK closed rows carry current-wave pointers (W38, declared=1)
```

> 变异有效性旁证（本车新发现，已写进判据 7 必红块注记）：**占位串不能含搜索 token**。首版注入把锚点替换成 `（本波复核指路已删空）` —— 占位串自带「本波复核指路」⇒ 判据仍命中、输出双 `OK`，是假变异；改 `（锚点已删空）` 才真响。留痕以防下波照抄。

### D. 改后 — 判据 6（✅ w38 正常态 OK + 十一类必红全 RED；命名归一后无一处弱化）

```text
[正常态]
w38 => OK feature/evidence invariants
w37 => OK feature/evidence invariants
w32 => OK feature/evidence invariants
  ← w37/w32 是历史制品，本车未动（`evidence_ref` 全命中、`state_counts` 未改）。

[十一类必红 · w38 工作区副本 · 注入键已归一 k6_cov12 → k6_covpop]
count       => RED assertion/mutation count mismatch
covpop      => RED state sum; attempt keys; coverage cells != cells_total
fail        => RED zero-value keys: ['FAIL']; state domain
iter        => RED iterations_total != len
noassert    => RED assertions missing
nocell      => RED mcp claims cell F4_mcp but cells[] lacks it; no mcp cell without declaration; state domain; state sum; attempt keys; coverage cells != cells_total
nomcp       => RED mcp field missing
nomut       => RED mutation_cases missing
noptr       => RED selftest note lacks in-repo pointer
privkey     => RED privacy: key-like token
ref         => RED missing ref evidence/raw/__missing__.json
```

> 改前基线（同一台机器、同一批副本、改名之前）逐条输出与上表逐字一致 ⇒ 命名归一（`assertions[].id` / `mutation_cases[].id` 去 `_13` 后缀）+ 三处 note 订正**零弱化**。`k6_check.py` 的六条判定不依赖断言 id 字面量（无键名依赖），故无需同处改动代码。

### E. W38 旧错数 / 旧名 0 命中自证

```text
[行数错数] pat="净 ""−9"; grep -rn "$pat" docs/   → rc=1（0 命中）
  （`core-data` 实测：W36 头 2489 → W37 头 2506 = 净 +17；W37→W38 = 2506 → 2541 = 净 +35。两组别混。）

[旧断言名] pat="K_cov""erage_13"; grep -rn "$pat" docs/ evidence/ scripts/ → 历史语境 10 处（下表逐条登记）+ 本摘录注记自身 1 处 = 11 处，均为「显式保留」
```

| 位置 | 处数 | 保留理由（显式） |
|---|---|---|
| `evidence/w36-matrix-summary.json` | 2 | **冻结历史制品**：W36 当时断言确名带 `_13`；改名 = 改历史制品结论载体，冻结声明禁止「重出历史制品」 |
| `evidence/w37-matrix-summary.json` | 2 | 同上（W37 制品） |
| `docs/analysis/w37-acceptance.md:11/:50` | 2 | W37 **时序记录**（当时按旧名落的判定）；非本波制品，改成新名会把历史写成从未发生的样子 |
| `docs/06-deferred-ledger.md:683/:702` | 2 | 台账**主理人单人落笔域**，本车不动（已在回传里报备，由主理人裁决是否订正） |
| `docs/handoff-20261120-wave38.md:13/:23` | 2 | 交接件**主理人落笔域**，本车不动（同上） |

> 已归一（本车实改、可 diff 复核）：`evidence/w38-matrix-summary.json` 的 `assertions[].id` 与 `mutation_cases[].id`（各 1 处）、checklist 判据 6 必红注入键（`k6_cov12` → `k6_covpop`，与摘录行同名）、checklist 判据 6 必红块标题与 W37 摘录抬头两处措辞、`docs/analysis/w38-acceptance.md` 裁决表第 3 行。

> **冻结边界裁决（用户/主理人 2026-11-20，W39 第 0 车落笔）**：历史制品 / 历史行内表述保留旧名 `K_coverage_13`，属**时点记录，不回改**（改历史行 = 重出历史制品）；上表 10 处一律维持原样。

### F. 本车未做（边界，不冒充）

- 未跑 `scripts/arch-guard.sh`、未跑 gradle、未改任何 `.kt`（另一席在搬文件，守卫会读中间态）。
- 未改台账 `docs/06-deferred-ledger.md`、未改 `docs/handoff-*`（主理人落笔域）。
- 未新增判据编号、未新建 checklist / 矩阵制品、未改四态枚举、未加 CI job、未动 `.github/workflows`。


---

## W40 判据 1 / 6 / 7 双态实跑摘录（2026-11-21，主理人）

> 说明：本段是判据**输出摘录**（可复核原文），不新增判据编号、不改四态枚举、不新建制品。
> 本波销账 0（空销账合法）。判据 7 自身修复：首版 W40 段解释文字含已 CLOSED 老号编号被正则误抓
> ⇒ `declared=1` 假绿，删编号后重跑 `declared=0` 才是本波真值 —— **判据要被验证，不是读出来就采信**。

### 判据 1 — 挂账号段唯一性

```
[正常态] W40 rows = 0
[正常态] 守恒式两数回显（W40）：### W40 段（2026-11-21，0 行新开：**本波新开挂账 0 / 本波销账 0**…）
[负控 WAVE=W30 不传 EXPECT] RED 缺 W30 段「本波新开挂账 N」声明（不回落默认值）
[负控] W30 rows = 3 / W30-01 -> 1 / W30-02 -> 1 / W30-03 -> 1
[负控] 守恒式两数回显（W30）：RED 缺本波新开/销账声明（判据7将空过 declared=0）
```
结论：默认锚当波（W40）、EXPECT 同源推导、不回落 3；负控真响（✅）。

### 判据 6 — 制品面定义 AND 断言 + evidence_ref 可复核

```
[正常态] python _w34/k6_check.py evidence/w39-matrix-summary.json
       → OK feature/evidence invariants
[必红注入] 把 cells[0].state 改成 BOGUS_STATE（副本）后重跑
       → RED state domain
```
结论：正常态 OK、注入必红真响；本波未新建制品（34 键零增删、沿用 W39 制品；✅）。

### 判据 7 — CLOSED 行须带判据实跑输出指路

```
[正常态] python _w34/k7_check.py
       → OK closed rows carry runnable pointers (W30+)
       → OK closed rows carry current-wave pointers (W40, declared=0)
```
结论：本波空销账合法空命中（`declared=0`），不冒充「全部通过」；修复前 `declared=1` 假绿已订正（✅）。

### 守恒式与台账不变量

```
W40 rows = 0（零 new 行）；台账 216 行；状态 38 CLOSED / 167 OPEN / 11 CARRIED（与 W39 一致）
grep -cE '^\| W40-' docs/06-deferred-ledger.md → 0
```

### W41 边界说明（只写口径，不新增判据编号 / 不新建摘录段）

- 「输出摘录段是否算追加」：**不算**。摘录是把既有判据的一次实跑输出写进**本文件已有段**，
  不新增判据编号、不改四态/glyph、不改 `harness-matrix-summary/v1` 34 键、不加 CI job。
  W41 不新建摘录段，只在 W40 段补本说明；后续波的实跑输出写进验收物，不再逐波加段。
- 守恒式两数：自 W41 起**只在派单卡抬头写一次**；历史段的数字是时点记录，不回改。
- **判据 7 的解析规则（写清，便于外部机器复现；规则本身不新增）**：
  ① 候选行 = 台账中所有以 `| W` 开头的表格行；只看第一列形如 `W<数字>-<数字>` 且状态列为 `CLOSED` 的行；
  ② 证据列（最后一列）必须命中 `文件:行` / `evidence/` / `runs?/<数字>` / 十位以上数字 / `实跑` 之一；
  ③ 当前波默认 = 所有 `### W<n> 段` 与行首 `W<n>-` 的最大波号；W41 不新增段 ⇒ 仍锚 W40；
  ④ `CUR_IDS` 默认只在**最后一个** `### W<n> 段` 块内从 `本波销账[:：]?` 的**那一行**抓
     `W\d+-\d+`；解释文字里出现的老号不得落在声明行，否则会把说明文字误抓成销账（W40 假绿的成因）；
  ⑤ 无声明或声明为空 ⇒ `declared=0` 是**合法空销账**，不等于「全部通过」，不回落历史波；
  ⑥ 必红样本（在副本上做）：把任一已 CLOSED 老号写进声明行且证据列不含本波复核指路 ⇒ 必须 `RED`。
- 必红样本仓库位置：判据脚本仍在仓外 `_w34/`，外部复核按上述规则等价实现即可；仓内只固化规则与本段输出。
