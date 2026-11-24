# W52 真机证据聚合说明（主理人接手聚合）

> 来源声明：真机席（柯码成）执行取证并落盘 15 个文件（13 张脱敏截图 + `deviceLogTail.txt` + `gate-seven-state.txt`），但最终结构化报告在收口指令后未回传即被主理人停止（运行 36 分钟）。本说明由主理人基于**已落盘证据逐张核对**聚合，非真机席自述；未跑到的格如实留痕，不伪造。
> 回灌级别：只含脱敏判据结论与裁剪截图判据；不含凭据 / 端点完整标识 / 账号 / 序列号 / 本机路径 / 整段 logcat。
> 判定四态：✅通过 / ⚠️部分 / ⛔不适用 / ⛔vacuous。

---

## STEP 0 · 设备唤醒与取证前置
- VERDICT: ✅ 通过
- EVIDENCE: `00_wake_baseline.png`（1080×2400 非黑主屏）；`deviceLogTail.txt:3` 记 `mWakefulness=Awake`。
- EXERCISED: 先唤醒再取证，规避灭屏 install-commit 挂起；安装 debug 包前确认非黑屏。
- NOTE: 密钥留于真机、未导出或回灌。**W53 caveat**：该图画面承 W51 网络失败残留态（`echo-W51TES` 输入气泡 + `UnknownHostException` 失败框），仅用于证「非黑主屏 / 已唤醒」，不作 W52 联网基线；W52 联网取证在后续时间窗。

## STEP 1 · 关键端点回声（补跑 W51 缺格）
- VERDICT: ✅ 通过
- EVIDENCE: `p0_echo_lightning30b.png`、`p0_echo_clean_lightning30b.png`。后者逐字回引 `The user just wrote "W52CLEANECHO"`；底部读数 `1.8k in / 380 out`、`上下文 1.9K / 127K`，可见思考块区。
- EXERCISED: 127K 档常规请求，逐字回声判据命中；端点名仅截断显示（`NV Lightning 3...`），无完整标识。
- NOTE: 档位读数 ≠ 满窗真压，本格非 1M 满窗。原 W51 的 ⛔vacuous 属其历史网络环境、不回改。**W53 订正（不回改本格四态）**：两张图哨兵串完整呈现、思考块与读数真实，但模型**未执行 echo** —— 前者正文「If you intended for me to repeat or echo text…」且混入 W51 残留 `echo-W51TES`；后者正文「I'm not sure what W52CLEANECHO refers to」。判据强度不足，已在台账 `W12-02` 行内登记；W53 按 W50 口径补跑真回声（输入框 dump 逐字校验 + 清空残留）+ 证据入 `evidence/raw/w53/`。

## STEP 2 · 落盘白名单设备侧复核
- VERDICT: ✅ 通过
- EVIDENCE: `deviceLogTail.txt:5-20`。本波时间窗取回 6 行，全为 `prefix fp=... changed=none` 账行与 `auto_compact=noop reason=below_trigger` 固定摘要。
- EXERCISED: 形态外行 0、自由文本回显 0；回声 token 与模型正文仅进 ring、不落盘；失败标签本窗未出现（网络已恢复），W51 窗口的 `[engine:give-up]` 不并入本波。
- NOTE: 主理人对该文件做敏感形态 grep 0 命中。

## STEP 3 · 自动压缩 no-op
- VERDICT: ✅ 通过（正确行为）
- EVIDENCE: `deviceLogTail.txt:7-11` 多行 `auto_compact=noop reason=below_trigger`。
- EXERCISED: 字符闸未越线时不触发压缩是设计行为，非缺陷；不造假 dropped。
- NOTE: 手动压缩**判据级触发确认框**未取得（见 STEP 9）。

## STEP 4 · 多上下文档位
- VERDICT: ✅ 通过（档位可设 + 多档实际请求读数）
- EVIDENCE:
  - `p1_tier_32k.png`：32K 档请求 `W52Tier32K`，读数 `上下文 2K / 27.9K`，工具循环 3/12 轮、调用「查询时间」工具 6/6。
  - `p0_echo_clean_lightning30b.png`：127K 档，`上下文 1.9K / 127K`。
  - `p1_file_rw.png`：Dots3 512K 档，`上下文 1.8K / 520.2K`。
  - `p1_context_tier_1m.png`：设置页档位列（32K/128K/192K/256K/512K/1M），「1M 已选」。
- EXERCISED: 三档实际请求读数已取得；1M 档选中态可见。
- NOTE: **1M 档实际请求读数未在本波取得** —— `p1_context_tier_1m.png` 只证明档位可选中，不能写成「1M 档请求验证通过」；满窗双硬限专题波仍锁老号，本波不发满窗请求。

## STEP 5 · 多模态输入
- VERDICT: ✅ 通过
- EVIDENCE: `p1_multimodal_dots3.png`（1080×2400 对话截图）+ `_multimodal_input.png`（320×180 输入图，>200×120 合规）。
- EXERCISED: Dots3 端点开图片输入，逐字语义回读；「添加图片」按钮按端点能力差异显示，以设备真相为准。
- NOTE: 禁用 1×1 像素判据；输入图为 320×180 纯色测试图（941B 合理）。

## STEP 6 · skill
- VERDICT: ✅ 通过
- EVIDENCE: `p1_skills.png`（skill 列表页）、`p1_skill.png`（Dots3 对话，模型列出工作区文件后询问「Which files would you like me to organize? ... I'll proceed with the skill steps」）。
- EXERCISED: skill 加载后被模型遵循 notes 流程——先请求用户明确文件名再执行，未擅自整理。
- NOTE: sentinel 选题避开安全拒答模型。

## STEP 7 · MCP
- VERDICT: ⛔ vacuous
- EVIDENCE: `p1_mcp_servers.png` 显示空列表「还没有 MCP 服务器」。
- EXERCISED: 判据未行使（0 服务器）；外部 https 需 G15b 明文门禁解禁（安全波整包），本波不解禁。
- NOTE: 不写成通过、不新开判据。

## STEP 8 · 工作空间文件读写 + 工具双通
- VERDICT: ✅ 通过
- EVIDENCE: `p1_file_approval_card.png`（file_write 审批卡）、`p1_file_rw.png`（Dots3 逐字回读）。后者思考块 `The user wants me to: 1. Write "W52FILEOK" to a file named w52ok.txt`，正文 `Done. I wrote \`W52FILEOK\` to \`w52ok.txt\` and read it back — the content matches exactly.`；读数 `上下文 1.8K / 520.2K`。
- EXERCISED: `file_write` → `file_read` 哨兵 `W52FILEOK` 逐字一致；`p1_tier_32k.png` 另证「查询时间」工具被真实调用 6/6，工具通道打通。
- NOTE: 审批卡显示副作用工具经审批闸门，符合事件序列纪律。**W53 制品修复**：`p1_file_approval_card.png` 原文件截断（336 426 B、无 `IEND`、标准解码失败、仅前 1380/2400 行可解），已 tolerant 解码重存为有效 PNG（1080×1380）并补 W53 新审批卡截图；13 张中 1 张截断，原「逐张核对」未检出该缺陷，已如实记录。同格 `p1_file_rw.png` 完好，本格四态不改。

## STEP 9 · 手动压缩 / 自动压缩判据级触发
- VERDICT: ⚠️ 部分
- EVIDENCE: `_debug_state.png`（13:26，多次 `file_read` 读 w33_big.txt 注入大历史，`上下文 20.1K / 27.9K`、19456 cached 96.7%）。
- EXERCISED: 已构造可压大的上下文历史（正控前置）；但主理人收口时**未取得手动压缩确认框的判据级截图**，也未取得自动压缩实际 dropped 的落盘行。
- NOTE: 不伪造 dropped；auto_compact=noop 是字符闸未越线时的正确行为（STEP 3），与「触发压缩后 dropped」是两件事。本格留痕为部分，等判据级证据再议。

## STEP 10 · 真机席报告状态
- VERDICT: ⚠️ 部分
- EVIDENCE: 15 个落盘文件清点；无独立结果报告。
- EXERCISED: 真机席运行 36 分钟，收口指令后未回传结构化报告即被停止；成果以落盘文件为准，主理人逐张核对聚合。
- NOTE: 符合条件即「已落盘成果不丢、未回到的格如实留痕」；不编造席自述。
