# W47 派单卡

**新开 0 / 销账 0**

> 两个数只写在本抬头；保留派单卡文件，交接 / 验收物 / 台账 / checklist 不复述。
> 本轮 2 个 P0 / 4 条 P1 / 3 条 P2 全部走老号行内注记 + 一次内容订正承接，不新开 `W47-` 行。
> 台账段声明只写「本波销账集合 = 0 行」指路，不复述两数。

**冻结（继续适用，W47 零追加）**：34 键矩阵制品、四态/glyph、判据集合与 CI job 零增删；
不新建 schema / 判据编号 / G 编号。「写 X 未发生前先自证判据会响」原样沿用；本波只把 W46 新建的判据**接进既有 step**（不新增编号），并补既有测试类里的行为必红。

**全面推进**：第 0 车账实/闸门订正与第 1 车代码同车，不独立成「第 0 车波」。
第 1 车完成 P0-2（判序最后一轮调整 + 样本集扩到 25 条）、P1-1（白名单写点收窄 + ≤160 ASCII 反向必红）；
按行数预算收 `W9-01` 常量拆分（连续两波让位后不再整包让位）。新增用例同提交改 `EXPECTED_TESTS`。

**第 0 车硬前置（先于任何代码）**
1. **门禁默认全扫**：`scripts/w46-local-secret-gate.sh` 默认不再排除 `_w[0-9]*/` / `local.properties`（排除移到显式 `--ci-semantics`），顶层补 `--local-full` 入口；`g0_scan_v2.txt` 重出全扫态；台账 `W36-01` 的 W46 补录订正「默认入口空过」，修好前不写「组合判据已落地 / CLOSED 收口」。必红：被 ignore 的 `_w47/*.jsonl` 含 serial ⇒ 默认 RED；selftest STATE 逐条注明参数。
2. **三个脚本接闸门**：`secret-scan.yml` 加一步跑 `w46-ledger-symbol-check.sh`、`build.yml` 加一步跑 `w46-classification-replay.py --w46`（零新 G 编号），各给注入必红；`selftest` 输出作为该 step 判据证据。
3. **台账两处失实订正**：`W24-02` 的「现役为 `token && mentionsLengthOverflow && !mentionsRateLimitContext` 三条件合取」改为 W46 分层 + W47 末轮描述；`W12-02` 的「provider message 不再拼接落盘」改为「ASCII+160 形态判据、写点仍拼接 message、短 ASCII 属已知边界」+ W47 收窄；落笔自检补「注记中变量名须能在现役 `.kt` 实现 grep 到（排除 KDoc 历史引述）」。
4. **收尾契约**：补真 tip `9d05a64b996002b6006a230e4eb3a6ea1525c524` 与第 4 条 run `45014733519` 四字段/链接，统一「实际 4 条 run（代码 2 + 文档 2，tip 文档 run 按 W29 降级仅记录、不作 P0）」，删「不因本段自指再第四次推送」这类会被提交本身证伪的措辞。
5. **P2 三项**：「五行 W46 注记」改「四行 + `W24-02` 一处就地订正」；符号自检数字写正常态实测值（本环境 20 类 / 59 方法 / 322 `.kt`）；g0 证据注明参数切换、deviceLogTail 补动态正文被拦证据。W40–W46 历史数字作时点记录不回改。

**判序路线（W47 最后一轮词表调整）**：`context` 恢复无条件强窗口词（配任意长度语义即超窗），「强限流语境让位」只留给 `prompt`；`token` 仍按强/弱长度语义 × 强/弱限流语境三分支。处方已在本环境逐字复刻验证：**25 样本全绿**（R08 仍限流、C05/C06 弱词超窗不回退）、4 条含 `context` 的扩展边界回 `CONTEXT_OVERFLOW`（HTTP400 同步）、`prompt too long (requests per minute cap)` 保持 RATE_LIMIT。把 4 条超窗侧强限流词样本加进 `scripts/fixtures/w46-error-classification-samples.json`（21→25），JVM 参数化与 `replay.py --legacy|--w46` 共用同一份 JSON；注入「`context` 也让位」⇒ replay 4 RED + JVM 2 failed；E5e/E5h/i/j/k/l/m/n 与 W44/W45/W46 既有必红一条不回退。**W48 起不再扩词表**：再出反例即改判据范式（结构化 `type`/`code` + HTTP status 唯一权威、message 只做兜底且 `Failed` 带命中证据标签供真机诊断）。

**三待拍板（连续第五波未获用户字面裁决，本波仍未代裁）**：
1. `W24-01`：维持 `OPEN` 补注记（推荐）——E1/E2 思考块正常显示为顺带观察，不作销账依据。
2. `W24-02` 两条已知边界（`retry:` 透传 / 可点重试入口）：维持 `CLOSED` 行内边界，不回开。
3. 矩阵：按用户「全面推进」做回归要点格（非 34 键矩阵制品）；E10 OR Dots3 本波未取得会话级 404 证据，如实 vacuous、不代裁供给状态。

**仍单独裁决、不自动解禁**：1M 双硬限注入（W9-11）、MCP 外部 https（G15b）、journal 二选一、安全波整包、OR 双端点 404 的供给方公告核查、F5 `applied` 分支安全截断点、设备断网后 Bolt/Nano 会话轮补测、价格维度（省钱外审）、凭据轮换（用户侧）。
