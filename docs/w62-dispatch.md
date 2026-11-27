# W62 派单卡 — 长文本黑块修复（玻璃图层预算）/ W61 外审 P1/P2 收口

> 本卡为 W62 执行派单与封版清单。**唯一裁决权威**仍是台账 W60 段 + 《W60 派单卡》抬头（M1–M4 / R1–R5 / L1–L6）；本波不改裁决、只执行，外审分歧的裁决见 `docs/handoff-20261127-wave62.md` §二。

## 抬头（执行口径）

| 项 | 值 |
| --- | --- |
| 波号 | W62（R5 之后的修复波；不新开挂账号、不合并销账） |
| 主线 | ① 用户复报缺陷：思考框 / 输出框文字一多整块纯黑（玻璃图层按节点全高申请纹理失败）；② W61 外审 P1-2 / P1-3 内核收口 + FreezeClock 语义订正 |
| 账实 | 216 行 / 39 CLOSED / 166 OPEN / 11 CARRIED；本波零销账零新开、状态列零变化（仅补两行 W62 行内注记） |
| 冻结 | 904 用例 + `EXPECTED_CLAIMS` 15 + 三顶格对象零触碰；零新增 `@Test` / G 编号 / CI job / schema |
| 行数 | core-design 12565/12600（余 35）、core-agent 6207/6500、app 1597/1600（本波零 diff） |
| 计数 | 2 提交 / 4 条 run（交付判据锚 `2be32ec` 双 run 全绿；`0c7b158` Secret 红 = 证据自命中，见交接 §六） |

## 派单项与出口

| # | 派单 | 出口判据 | 证据 |
| --- | --- | --- | --- |
| 1 | `GlassLayerBudget`：单边 4096 px + 面积 4M px² 双约束 | 纯函数 `allows()`；零/负尺寸放行（首帧交既有 `lensCanvasIsSafe`） | `core-design/.../liquid/GlassLayerBudget.kt` |
| 2 | `DrawBackdropNode` 接预算 | 超预算：measure 内容层去 `Offscreen`、draw 跳过背景录制与导出录制 | `e5_guard_log.txt` |
| 3 | Shadow / InnerShadow / Highlight 接预算 | 超预算早退，不建图层、不画描边高光 | 同上 |
| 4 | 真机判据（四态 + 必红） | 长气泡可读 ✅ / 思考块展开 ✅ / 短气泡对照未降级 ✅ / 变异放大阈值复现黑块 ✅ | `png_e0..e4` |
| 5 | `AgentRunner.runStarted` 移进 try | 取消路径落 `cancelled=true` 的 settled；取消后新 run 被接受（mutex 已释放） | `e6_journal_cancel.txt` |
| 6 | `AgentRunJournal.record` CE 原样上抛 | sink 抛 CE 不被吞（W54 同型） | 代码面 + `g4_fulltest_w62.txt` |
| 7 | FreezeClock 语义订正 | 注释写明「注入源测 suspend，对 cgroup 冻结无观测能力」，不写「已解决」 | `core-agent/.../FreezeClock.kt` |
| 8 | 四闸门 + 附属自检 | 904 / arch 27-1-0 / selftest 30/30 / 探针 rc=0 / 台账符号 rc=0 / 隐私三路 0 | `g1..g7` |

## 守卫与触发信号

| 触发条件 | 处置 |
| --- | --- |
| 玻璃节点尺寸越界仍建离屏层 | 立即判红；必须走 `GlassLayerBudget` 降级（真机黑块） |
| 降级后文字不可读 / 圆角丢失 | 判红；降级只允许「无背景录制 + 半透明实底」，不允许整块丢失 |
| 阈值改动（4096 / 4M） | 必须同时给真机必红变异与依据，禁「拍脑袋调大」 |
| `runStarted` 落在 try 之外 | 判红；异常路径会漏 unlock `runMutex` + 留永久未结算 run |
| sink 吞 CE | 判红（W54 同型）；CE 必须原样上抛 |
| 判据常量改动（`EXPECTED_TESTS` / `EXPECTED_CLAIMS` / `VACUOUS_BASELINE`） | M3 净 0 判红；本波零改动 |
| 证据文本自身命中 3b-ext 形态 | 判红；判据描述不得写路径/序列号字面（本波实测踩中一次，见交接 §六） |

## 边界声明

本波修**渲染降级**与**两处内核收口**，不改裁决、不销账、不改状态列（仅补两行注记）。不做长回答折叠/分页（feature-chat 3999/4000 无余量、亦非缺陷根因）；不新增 `@Test`；不提前执行 R2 / R3-a；R4 永久不得写成通过。真机证据只回灌脱敏判据结论与固定探针行，不含凭据 / 未打码账号字段 / 端点标识 / 序列号 / 无线网络标识 / 本机路径 / 整段 logcat。本波止于交接，交由外部团队审查。
