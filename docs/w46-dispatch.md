# W46 派单卡

**新开 0 / 销账 0**

> 两个数只写在本抬头；保留派单卡文件，交接 / 验收物 / 台账 / checklist 不复述。
> 本轮 2 个 P0 / 4 条 P1 / 3 条 P2 全部走老号行内注记 + 一次内容订正承接，不新开 `W46-` 行。
> 台账段声明只写「本波销账集合 = 0 行」指路，不复述两数。

**冻结（继续适用，W46 零追加）**：34 键矩阵制品、四态/glyph、判据集合与 CI job 零增删；
不新建 schema / 判据编号。「写 X 未发生前先自证判据会响」原样沿用；本波只补既有判据的可复核性与既有测试类里的行为必红。

**全面推进**：第 0 车账实/证据订正与第 1 车代码同车，不独立成「第 0 车波」。
第 1 车完成 P1-1（判序结构性收口 + 双向混淆样本集）、P1-3（白名单正文约束）、P1-2（本地门禁脚本落仓）；
W9-01 RATIO 搬迁经逐文件复核后维持让位（跨三模块重构、判据冻结下不夹带，已诚实留痕、不写成已搬）。新增用例同提交改 `EXPECTED_TESTS`。

**第 0 车硬前置（先于任何代码）**：
1. 台账 4 个虚构符号改真实名 + 弱窗口词规则改合取式 + 「反引号内测试类/方法名必须能 grep 到」机械自检落 `scripts/w46-ledger-symbol-check.sh`（注入虚构符号 RED → 还原 GREEN 实证）。
2. G1–G4 / RED / GREEN 按 W41/W44 口径重出原始脱敏输出（g1 363 行 / g2 35 行 / g3 39 行 / g4 396 行含 14 模块与 TEST-XML mtime；RED 含 AssertionError 行号与 gradle 堆栈）；等价复刻 21 样本脚本与 JVM 参数化测试共用同一份样本 JSON；g0 fixture 改分段拼装形态；制品清单写死为「原始脱敏输出」。
3. 补 tip `e32a7043b67742a11d54045dd6f320c39f4f3cfb` / `bbdd45e3046b065b5e589299145a5ed6d3c4a54d` 完整 SHA、第 4 条 run `44999170747` 四字段（公开 API 实取 `head_sha=e32a7043…` / `completed` / `success` / `2026-11-22T07:39:00Z`）、「实际 4 条 run」统一口径。
4. 本地 3b-ext 三路 + `-f` 注入复刻脚本落 `scripts/w46-local-secret-gate.sh`，台账注明「CI 两路 + 本地人工闸门」组合判据；修 `--ignored` 路数注释（第二路 `--exclude-standard` / 第三路 `--ignored`）。
5. 订正 `OpenAiErrorFrame 286/400`；设备证据 `w45-probe-whitelist.txt` 注明按形态分组重排。W40–W45 历史数字作时点记录不回改。

**判序路线（W46 一次做完，停止逐波打地鼠）**：结构化 `type`/`code` + HTTP status 为唯一权威；message 词表降为分层启发式（强限流语境 / 弱限流词、强长度语义 / 弱 exceed）；双向混淆样本集 21 条（限流侧 8 / 超窗侧 10 / 协议侧 3）落成参数化 JVM 测试 + 仓内可跑复刻脚本，两份共用同一份样本 JSON；补 4 条边界必红（`tokens per minute limit exceeded` / `tpm exceeded` / `maximum token count exceeded (quota: …)` / `prompt too long: rate limit exceeded`）并扩 `mentionsRateLimitContext` 到 `tpm` / `tokens per minute` / `requests per minute` / `per-minute`；E5e/E5h/i/j/k/l/m/n 与 W44/W45 七条一条不回退。**本波起停止逐波加 3 条点状必红**。

**三个「待拍板」裁决（用户本轮授权「裁决分歧及方向 + 全面推进」）**：
1. `W24-01`：本轮指令未给销否字面裁决 ⇒ 维持 `OPEN` 补注记、不代裁销账（派单卡推荐路径）。
2. `W24-02` 两条已知边界（`retry:` 透传 / 可点重试入口）：维持 `CLOSED` 行已知边界，不回开。
3. 矩阵：用户本轮明确「全面推进」到 1M 真压 / 多模型 / 多功能面 ⇒ 按真机回归要点格执行（非 34 键矩阵制品）。

**仍单独裁决、不自动解禁**：1M 双硬限注入、MCP 外部 https（G15b）、journal 二选一、安全波整包、OR 双端点 404 的供给方公告核查、F5 `applied` 分支安全截断点、设备断网后 Bolt/Nano 会话轮补测、凭据轮换（用户侧）。
