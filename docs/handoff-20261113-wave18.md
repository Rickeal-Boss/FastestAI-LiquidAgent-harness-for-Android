# W18 交接 — 多免费节点模型 × 多档位（直至 1M 读数）× 全功能面真机矩阵（2026-11-13）

> 基线 `17711ec`（W17 收尾，`main`）。上游依据：用户 2026-11-13 指令（读记忆 / 上一波 handoff /
> OPEN 挂账行〔现役仓 `harness-v2/`，非旧仓 `Android-Agent/`〕、列出「做什么 / 明确不做」写入验收物 §1；
> 读众审查人原始报告并裁决分歧、**不改方案大体方向**；外部仓库高价值参考；多个模型测试不同上下文
> 〔直至 1M〕与多模态输入 / 工作空间文件读写 / skill / MCP / 手动与自动上下文压缩；
> 先本地 UI + 功能测试 + adb 调试 → 真机 → 推仓库及 CI 构建；按收敛结论实施唯一目标；
> 零凭据字面量；编译失败按四分类归因；真机 adb + 脱敏截图回灌 + 边测边改；
> 每次推送留可审计 commit 与 run 链接；不 force-push、不 rewrite history；
> 触顶收尾；handoff 不把推断写成事实；本次任务停于交接、交外部团队审查）。
> 判定四态 ✅ / ⚠️ / ⛔ / ⛔vacuous。「未触发」≠「已验证通过」；「测试全绿」≠「口径被验证」；
> `rc=0` ≠ 产物已重生成；PC 侧结论不得写成设备侧可用；1M 读数 ≠ 1M 真压。
> 密钥纪律：端点凭据只在设备侧；本单 / 工具 schema / 工具结果 / 日志 / 截图 / 交接 / 记忆写入零凭据字面量。
> 端点一律以不可逆 8 位指纹出现。**完整版见 `docs/analysis/w18-acceptance.md`；本文件只做交接摘要，冲突时以验收物为准。**
> **本文件不写「建议下一链做什么」。**

---

## 一、本波定位与裁决收敛

W18 = **补铺矩阵波**：把 W17 判 ⛔vacuous 的「多免费节点模型 × 多档位上下文（直至 1M 读数）×
全功能面」矩阵在真机铺满并四态如实取证。不是裁决波、不是拆分波、不是安全波、不是 journal 波。

**收敛结论（唯一目标以此为准，完整版见验收物 §1.2–§1.4）**：

1. **用户指令面优先**（沿用 W14/W15/W16/W17 四波同款裁决，出处 `w17-acceptance.md:98`）：
   外审还债排期不取消、不推翻、整包顺延；「不改方案大体方向」= 不把本波转向外审推荐的第 0 车。
2. **矩阵欠账是本波唯一目标**：四份外审一致点名 W17 只跑一格 ⇒ ⛔vacuous / 用户指令面仍欠；
   报告 C §六.5「矩阵面冻结新增」与用户指令冲突 ⇒ 不采纳。
3. **零 `.kt` 改动**：外审实测三模块余量 feature-chat 3996/4000 余 4、feature-settings 3495/3500 余 5、
   core-context 1646/1650 余 4，`ChatViewModel` 500/500；任何 `.kt` 增量必撞 G10。
   拆分是独立波（W17 号段第 6 行 / W16 号段第 3 行原样 `OPEN`），塞进测试波会改变单波归因 ⇒
   裁决为「不做拆分、也不改 `.kt`」，这是唯一同时满足「不改方向 + 不撞闸门」的解。
4. **众审查人分歧逐条裁决**（验收物 §1.4 十条）：含设备/模型可行性的关键证伪——
   `AGENTS.md` §一明确本仓从 LiquidAgent 分叉后**端侧推理核心全部移除**，模型全在云端 ⇒
   本波「多模型」只指设备侧 `endpoints.json` 的远程端点，知识库 §4 的端侧引擎判据**全部不适用**，
   不照搬为候选模型筛选依据。
5. **两条外审处方回源证伪**（AGENTS.md §二.2）：① 报告 A N2「`ChatBubble` 复制/显示口径分裂、
   一行改 `displayText` 即齐」——复制动作在 `ModelBubble`（MODEL/TOOL 角色），USER 气泡无复制入口 ⇒ 修法是无操作；
   ② 报告 A §2.3「`parallel_` 笔误键 ⇒ 误拒行为仍在 + KDoc 失实」——现役
   `EndpointAdvancedValidation.kt:68` 是完整键 `"parallel_tool_calls"`、`:76` KDoc 已写明白名单仅作持久化约束。
   两条均**不采纳、不修、不立账**（与 W17「发现 A `enable_thinking` 无条件 true」证伪同款纪律）。
6. **§1.4「两案并采」⚠️ 部分成立、不写已定谳**：文档内部口径张力属实 ⇒ 只做「分开记账」的
   口径订正留痕，不改写 W17 历史正文；外审「一个被证实、一个被实测排除」的**因果定性 ⛔ 未能核实**
   （W16 原始夹具原件不在树内，四份外审均自承未复核夹具原件），且 §1.4 引用的
   `SessionImportParser.kt:166/:275` 行号已漂移。
7. **四项用户裁决不代做**：journal 二选一第 13 次顺延（`W18-01` 登记）、「≥3 端点」终局、
   §1.5 占位符口径、`P2-3` 二选一；只留痕，不软化、不关号。
8. **G20 只认台账行首 ID**（`arch-guard.sh:1329/1337` 只扫 `docs/handoff-*.md`）；本波 W18 号段
   在台账 §二-octies 落 12 行真实行（全 `OPEN`、每行触发信号 + 缓解动作 + 证据），老号一律原样保留。

## 二、代码改动

**本波零 `.kt` 改动**（理由见 §一.3）。变更面只有：本波验收物、本波交接、台账 §二-octies 新增 12 行、
以及一份仓根非 docs 路径的脱敏机器可读证据制品（见 §五）。

## 三、本地闸门

| 闸门 | 实跑 | 四态 |
|---|---|---|
| G1 出包 | 基线 `270 up-to-date` ⇒ 出包判据未行使（⛔vacuous）；`python os.remove` 删旧 APK 强制重出包两次，均 `1 executed, 269 up-to-date` ⇒ 68,407,402 B 字节一致（deterministic），mtime 本轮；与 W17 旧值相同属零 `.kt` 改动的预期 | ✅ |
| G2 fulltest | `tests=818 failures=0 errors=0 skipped=0`，14 模块；83 个 `TEST-*.xml` mtime 全本轮；严格 `@Test` 818 ↔ `build.yml:135` | ✅ |
| G3 arch-guard | `PASS ok=27 vacuous=1 violation=0 fault=0`；G19 唯一 VACUOUS；G20 实跑 ID 全在台账（不写死数） | ✅ |
| G4 selftest | `30/30 PASS` | ✅ |

**编译失败四分类全未触发**（四闸门 `rc` 全 0）。如实登记一处环境类异常：G3 出现 MSYS/Cygwin
`fork: retry: Resource temporarily unavailable (errno 11)`，成因为三个后台任务瞬时争抢 MSYS fork 资源；
未改码、未改守卫脚本、未硬凑，最终 `PASS exit=0`。W17 的 `DOS error 1455` 与 Compose 瞬态
`Back-end internal error` 本波均未出现。证据 `_w18/gates_attribution.jsonl`。

## 四、真机功能矩阵

> 见验收物 §3（每格端点指纹 / 档位 / 功能面 / 四态 / 证据路径）与 §4 链内迭代记录。
> **共 9 格：3 ✅ / 1 ⚠️ / 4 ⛔ / 1 ⛔vacuous。**
> **档位读数覆盖 32K / 128K / 512K / 1M**（用户指令「直至 1M」达成）：128K ✅、1M ✅（档位回读
> `当前 1M`、`switch_verified=true`）、512K ⚠️（请求收敛但本格未做回读验证）、32K 多端点探测。
> 多端点共尝试 6 次切换，仅 1 支稳定收敛；其余 5 支免费节点本次均无响应/超时 ⇒ **环境问题，
> 非四分类代码缺陷**，不硬跑、不改码硬凑、不把节点不可用写成应用 bug。
> 功能面四态如实：**工作空间文件读写 ⛔vacuous**（请求收敛但 `file_write` 未调用、workspace 未落盘
> ⇒ 判据未行使）、**skill 端到端 ⛔vacuous**（设备侧 skills 目录为空）、多模态 ⛔（端点未开启图片输入）、
> 手动压缩 ⛔（未发起）、自动压缩 ⛔vacuous（`auto_compact=noop below_trigger`）、MCP ⛔（未逐项确认）。
> **1M 只做档位读数，不是 1M 真压**（`W9-11` 双阻断未解除，W18 号段第 5 行原样 `OPEN`）。

## 五、CI 与提交

> 见验收物 §5.4。本波裁决：同一 push 内新增仓根非 docs 路径的脱敏 `.json` 证据制品 ⇒
> Build 五 job 由该 push 自身真实触发（不靠 `workflow_dispatch`），可附绑定该 commit 的 run 链接；
> 不改 `.kt`、不改 `.github/workflows/*`、不放会打进 APK 的路径（避免破坏 G1 deterministic）。
> 不 force-push、不 rewrite history。

## 六、未做 / 明确不做

逐条带挂账号，见验收物 §5.1（12 条，全部对应台账 §二-octies 的真实 `OPEN` 行）。核心：
journal 二选一仍待用户裁决（第 13 次顺延）；拆分三方整包 / 安全波 / 六行漏号补登 / 1M 真压 /
MCP 明文 / git 历史清理均顺延；外审新提小缺陷与六处账实订正按「只加抬头不改历史正文」挂账；
两条回源证伪不立账。

## 七、挂账状态（唯一真值源 = `docs/06-deferred-ledger.md`）

| ID 段 | 状态 | 本波动 |
|---|---|---|
| W18 号段第 1–12 行 | `OPEN` | 本波新开 §二-octies，每行触发信号 + 缓解动作 + 证据 |
| W14/W15/W16/W17 各段 | `OPEN`（原样保留） | 不删、不改状态、不写已核销、不重判 CLOSED、不并号 |
| W9 段第 5 行（journal） | `OPEN` | 第 13 次顺延由 W18 号段第 1 行登记（两口径并列待裁决）；老行不改 |

## 八、移交外部审查

**提交序列**：`17711ec`（W17 收尾）→ `208c2eb`（docs(w18)：验收物 + 交接 + 台账 W18 段 + 脱敏证据制品，
4 files / +509）。**1 个 commit，不 force-push、不 rewrite history。**

**CI（实跑，event=push）**：
- Build run **completed success**（五 job 全绿）：Assemble Debug / Unit tests / Arch guard / Lint / Secret scan
  `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/43272970379`
- Secret scan run（独立 `secret-scan.yml`，无 paths-ignore）**completed success**，覆盖同一 commit
  `https://github.com/Rickeal-Boss/FastestAI-LiquidAgent-harness-for-Android/actions/runs/43272970496`
- **CI 红次数 0**（上限 2）；**推送次数 1**（上限 3）。触发方式 = 同一 push 含仓根非 docs 路径的
  `evidence/w18-matrix-summary.json` ⇒ Build 由该 push 自身真实触发（不靠 dispatch）。
- 结论取值 = 公开 API check-runs（零凭据），逐项读 `status`/`conclusion`。

**文档回写 diff**：`docs/analysis/w18-acceptance.md`（新）、`docs/handoff-20261113-wave18.md`（本文件，新）、
`docs/06-deferred-ledger.md` §二-octies（+12 行）、`evidence/w18-matrix-summary.json`（新，脱敏机器可读制品）。
历史文档正文一律未改。

**未复核（不写成通过）**：多端点 × 多档位矩阵未铺满全部 5 支端点（仅 1 支稳定收敛，其余免费节点本次无响应
⇒ ⛔ 环境问题）；工作空间文件读写 / skill / 多模态 / 手动压缩 / 自动压缩 / MCP 六项功能面**判据未行使**
（⛔vacuous / ⛔，见验收物 §3.2）；1M 只做档位读数、未写真压。`W18` 号段第 5 行（矩阵欠账）原样 `OPEN`。

**可审查制品清单**（不含会话历史 / 凭据 / 端点标识 / 下一链建议）：见验收物 §5.3。

> 本节不写下一波候选入口。**本波止于交接。**
