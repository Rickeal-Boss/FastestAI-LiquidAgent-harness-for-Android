# W61 派单卡 — journal v1 内核落地 / 杀进程恢复卡两路 / FreezeClock 接线

> 本卡为 W61 执行派单与封版清单。**唯一裁决权威**仍是台账 W60 段 + 《W60 派单卡》抬头（R5 journal「做」、生效 W60、独立波号 W61、单点开波）。本波不改裁决、只执行；四份外审分歧的裁决见 `docs/handoff-20261127-wave61.md` §二。

## 抬头（执行口径）

| 项 | 值 |
| --- | --- |
| 波号 | W61（R5 独立波，单点开波，不与其余三项矩阵老号合并销账） |
| 主线 | journal v1 内核层：事件溯源最小集 + FreezeClock + 落盘 + 读盘恢复卡 |
| 账实 | 216 行 / 39 CLOSED / 166 OPEN / 11 CARRIED；本波零销账零新开、状态列零变化 |
| 冻结 | 904 用例 + `EXPECTED_CLAIMS` 15 + 三顶格对象零触碰；零新增 `@Test` / G 编号 / CI job / schema |
| 计数 | N 提交 / M 条 run（主体 + 回填，逐号待推送后实取回填；交付判据恒锚唯一代码提交双 run） |

## 派单项与出口

| # | 派单 | 出口判据 | 证据 |
| --- | --- | --- | --- |
| 1 | `AgentRunJournal` 事件溯源最小集 | run_started / run_settled 固定形态，无用户内容；sink 端口不依赖 core-data | `evidence/raw/w61/e0_journal_recovery.txt` |
| 2 | FreezeClock 注入式接线 | null 时 904 零漂移；非空时 `active = wall − frozen` 截零 | `evidence/raw/w61/e1_freeze_clock.txt` |
| 3 | FileRunJournalStore 落盘 | 256KB / 200 行轮转；CE 原样上抛；写失败不影响主流程 | `evidence/raw/w61/e0_journal_recovery.txt` |
| 4 | 读盘恢复卡两路 | 重启读盘配对（非内存）；继续补 settled / 丢弃清盘 | `evidence/raw/w61/e0_journal_recovery.txt` + `png_e0_journal_recovery.png` |
| 5 | 四闸门 + 附属自检 | 904 / arch 27-1-0 / selftest 30/30 / 探针 rc=0 / 台账符号 rc=0 / 隐私三路 0 | `evidence/raw/w61/g1..g7_*.txt` |
| 6 | 外审分歧裁决 | P0-1 驳回 / P0-2 降限定 / P1-1 补扫 / P1-2 说明 / P1-3 降级 / P2 登记 | `docs/handoff-20261127-wave61.md` §二 |

## 守卫与触发信号

| 触发条件 | 处置 |
| --- | --- |
| journal 落盘行含用户内容 / 端点标识 | 立即判红；固定形态只允数字 / 枚举 / uuid / 固定工具名 |
| 恢复卡只读内存 buffer（进程重启即空） | 立即判红；必须读盘配对（本波已修，变异见 e0 step-4） |
| 冻结扣时未触发却写「已验证 / 通过」 | 立即判红（R4 衍生纪律，四态标 ⚠️） |
| G10 触顶（单文件 > 400） | 触顶即拆分 / 精简 KDoc 腾行，禁调 `LINE_CAPS` / `TOTAL_LINE_CAP`（本波两处按先例处理） |
| app 模块行数触 1600 | 腾行信号，R1（W62）缩域授权生效后处理；本波 1597/1600 |
| 判据常量改动（`EXPECTED_TESTS` / `EXPECTED_CLAIMS` / `VACUOUS_BASELINE`） | M3 净 0 判红；本波零改动 |

## 边界声明

本波实现 R5 journal 内核层，**不改裁决、不销账、不改状态列**。不提前执行 R1 / R2 / R3-a；不做会话正文持久化；不制造冻结假场景；不新增判据 / G 编号 / CI job / schema；不回改历史四态；不发版、不产 tag。真机证据只回灌脱敏判据结论与设备侧固定探针行，不含凭据 / 未打码账号字段 / 端点标识 / 序列号 / SSID / 本机路径 / 整段 logcat。本波止于交接，交由外部团队审查。
