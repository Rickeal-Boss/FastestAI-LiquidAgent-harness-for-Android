# W28 收口 checklist — 挂账出口判据的仓内可执行载体（2026-11-18）

> 存在理由：W27 段三行出口判据写了「注入样本 ⇒ 收口 checklist 判红」，但全仓无此载体（唯一 `w4-gate-checklist.md` 是 W4 闸门清单、与收口无关），判据悬空。本文件按外审 P0-3 处方建立，**三条出口判据逐条对应仓内可跑命令 + 输出判红样例**。
> 纪律：本 checklist 是**人工闸门 + 必红变异**兜底，不是机器守卫 —— `arch-guard.sh` 的 g20 只做 handoff ID 存在性比对（两边 `sort -u` 抹平重复号），无 ID 唯一检查、不覆盖本清单；改守卫 / 扩扫描面属用户裁决域（本波只留痕不代做）。
> 判定四态：本清单每条命令的期望是「正常态输出 `OK`，注入变异样本后输出 `RED`」；**只跑正常态全 OK 不算判据被验证**，必须自证变异样本会响。

---

## 使用方式

在仓库根（`<workspace>/harness-v2`，本波主理人工作目录）执行下列命令（Git Bash）。每条判据分「正常态」与「必红变异」两步；变异在**台账/制品副本**上做，验完还原，不改真值源。

---

## 判据 1 — 挂账号段唯一性 + 验收物挂账号有独立台账行

**出口判据来源**：W27 段首行「注入『删 tip run 行』样本 ⇒ 收口 checklist 判红」；W27 段末行「注入『台账重复行』样本 ⇒ checklist 判红」。

**正常态（期望 OK）** —— `LEDGER` 默认指真值源：

```bash
cd harness-v2
LEDGER=${LEDGER:-docs/06-deferred-ledger.md}
# 1a. 本波段号段占用预检（本波 W28 落笔后应恰为 3 行）
grep -cE '^\| W28-' "$LEDGER"
# 1b. 每个 W28 行号在台账恰出现一次（无重复号）
for id in $(grep -oE '^\| W28-[0-9]+' "$LEDGER" | sed 's/^| //'); do
  n=$(grep -cE "^\| ${id} " "$LEDGER")
  echo "$id -> $n"; [ "$n" -eq 1 ] || echo "RED duplicate/missing: $id"
done
# 1c. 本波验收物 §1.3 出现的挂账号在台账有独立行
for id in $(grep -oE 'W[0-9]+-[0-9]+' docs/analysis/w28-acceptance.md | sort -u); do
  grep -qE "^\| ${id} " "$LEDGER" || echo "RED no-ledger-row: $id"
done
```

**必红变异（期望 RED，全程不碰真值源）**：

```bash
cp docs/06-deferred-ledger.md /tmp/ledger.mut
# 追加一条重复 W28-01 行（从已有首行复制）
grep -m1 '^| W28-01 ' docs/06-deferred-ledger.md >> /tmp/ledger.mut
LEDGER=/tmp/ledger.mut bash -c 'LEDGER=$LEDGER; for id in $(grep -oE "^\| W28-[0-9]+" "$LEDGER" | sed "s/^| //"); do n=$(grep -cE "^\| ${id} " "$LEDGER"); [ "$n" -eq 1 ] || echo "RED duplicate: $id($n)"; done'
```

应输出 `RED duplicate: W28-01(2)`；此变异下 `arch-guard.sh` g20 仍应 PASS（它按 `sort -u` 抹平重复号，证明机器守卫不覆盖、人工兜底必要）。验完 `rm /tmp/ledger.mut`。

---

## 判据 2 — 制品出厂断言非空转（`state_counts` 非零键集 = `cells[].state` 值域）

**出口判据来源**：W27 段次行「注入『把某条 verified 置 false 而 selftest 仍报全绿』样本 ⇒ 出厂断言判红」。

**正常态（期望 OK）**：

```bash
cd harness-v2
python - <<'PY'
import json, io
d = json.loads(io.open("evidence/w28-matrix-summary.json", encoding="utf-8").read())
nonzero = {k for k, v in d["state_counts"].items() if v > 0}
domain = {c["state"] for c in d["cells"]}
print("nonzero keys =", sorted(nonzero))
print("cell state domain =", sorted(domain))
print("OK" if nonzero == domain else "RED state domain mismatch")
# 必红样本不得与现役同型：description 里的注入键不能在 state_counts 以 0 值存在
desc = " ".join(a.get("description", "") for a in d.get("assertions", []))
for k in ("PARTIAL", "FAIL"):
    if k in desc and d["state_counts"].get(k, -1) == 0:
        print("RED vacuous mutation sample (zero-valued key in description):", k)
PY
```

**必红变异（期望 RED）**：在制品副本里把某条 `mutation_cases[i].verified` 置 `false` 但保留 `mutation_selftest.all_verified=true` → 重跑应输出 `RED`（若生成器 selftest 仍报全绿，说明断言只查字段存在、未真跑）。另：把 `state_counts` 加一个零值键写进断言 description → 应输出 `RED vacuous mutation sample`。验完还原。

---

## 判据 3 — tip run 闭环回填 + 收尾契约步 5 自洽

**出口判据来源**：W27 段首行出口判据前两条（凭据文件名 0 命中、两条 run 各 ≥1 命中指向各自 §5.6）+ 收尾契约第 9 次。

**正常态（期望 OK）**：

```bash
cd harness-v2
# 3a. tip run 与 commit 在 W27 §5.6 与文末链接段各 ≥1 命中
for tok in 44241199431 cdaade7; do
  n=$(grep -c "$tok" docs/analysis/w27-acceptance.md)
  echo "$tok -> $n"; [ "$n" -ge 1 ] || echo "RED tip run missing: $tok"
done
# 3b. 凭据文件名形态全树 0 命中（注记引用除外，由 _w23/scan_artifacts.py 正控先自证会响）
python <取证设施>/scan_artifacts.py 2>/dev/null | tail -3   # 脱敏正控工具在仓外取证设施, 按需向主理人索取
```

**必红变异（期望 RED，全程不碰真值源）**：复制验收物到工作区副本（**不要用 `/tmp`** —— Git Bash 的 `/tmp` 与 Windows Python 不通会 `FileNotFoundError`），把 `44241199431` 的**全部字面量删净**（本波回填后该串在验收物出现 2 处：§5.6 推送 2 行的 inline 注记 + 文末链接段 URL，**只删一处不响**）→ 重跑 3a 应输出 `RED tip run missing`；验完删除副本。本波已实跑：删净后计数 0 ⇒ 判据会响；真值源计数 2 ⇒ 正常态 OK。

---

## 本 checklist 覆盖 / 不覆盖（诚实标注）

- **覆盖**：三条出口判据的仓内可执行命令 + 必红变异 + 人工兜底声明。
- **不覆盖**：① `arch-guard.sh` 的 ID 唯一性（属用户裁决域，机器守卫不判重号）；② 真机重放（`evidence` 的 `verified` 多为规则自证，真机判据在验收物 §3/§4 文档层）；③ 仓外生成器断言 M（`W20-14`，取证设施不入库）。
- **四态**：本 checklist 正常态命令全 OK 仅说明「载体存在且当前数据自洽」，不等于三条挂账已 `CLOSED`；状态改 `CLOSED` 须真机取证入账后按台账 §三.3 由主理人单人落笔。
