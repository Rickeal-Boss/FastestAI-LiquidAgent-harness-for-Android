# W7+ 上下文分块缓存命中 & 手动/自动压缩 —— 综合方向路线

> 编制日期：2026-11-02 ｜ 性质：规划文档（非源码改动）
> 输入：4 份调研报告（桌面 3 份 + COS 共享 1 份《国产大模型上下文压缩 × 分块缓存策略深度研究报告》）+ 现役仓 `harness-v2` 交接/设计文档 + 源码核实
> 目标：为下一波「实际改动 + 真机测试」提供可逐条验收的作战手册

---

## 0. 资料融合说明

| 来源 | 核心增量 |
|---|---|
| 报告1《深度研究报告》 | U 型张力、dsh 摘要复用主前缀标杆、ZCode cacheHint+microcompact、十条准则 |
| 报告2《五仓源码对比》 | dsh 摘要请求逐字节回放、ZCode microcompact 梯度、Operit 本地前缀计数缓存、改写已缓存区必丢前缀 |
| 报告3《国产 API 调研》 | 15 SKU 缓存计价、usage 字段语义分裂、显式/隐式断点、TTL 路由 |
| **报告4《整合研究报告》(2026-11-02)** | **Tier 0–4 递进框架**；命中率上限公式 `⌊diff÷block⌋×block÷total`；dsh 两阶段触发（先免模型剪枝再摘要）；ZCode/dsh/minimax/Operit 量化默认值；EPIC/MiniPIC「尾部注入≈轻量 PIC」理论背书；cache_hit_ratio 分母口径分歧（minimax 含 write / Octop 不含） |
| 现役仓核实 | 压缩三层已落地（L1/L2/L3）；缓存观测管道 70% 就位（adapter 解析 cached、ContextBudget 前缀指纹含 cached=）；I1 跨轮分裂 / I2 前缀过短 / I3 待核实 |

**关键结论（报告4 §0/§6 提炼）：** 压缩+缓存能提命中率，**当且仅当压缩不破坏「严格前缀单调性」**。命中率上限由「第一个差异点的位置」决定，与改动量无关。五家 harness 已收敛到同一范式：**压缩指令尾部化 + 前缀回放 + stable/dynamic 分段 + 免模型工具清理走尾部 + 命中率进一等指标**。

> 理论内核一句话（报告4 §7）：**把「压缩」从「缩短序列」重新定义为「在不移动稳定前缀的前提下，把低价值 token 换成高价值引用」**。我们的 `system 固定 + 压缩锚定尾部` 设计已被 EPIC/MiniPIC（尾部注入=自研轻量 PIC）理论背书——方向正确，无需回炉。

---

## 1. 现役仓现状审计（指到文件:行，已核实）

### 1.1 已具备（缓存感知基础）
- **压缩三层**：`PreStepBudget.kt` 每步判定；`HardBudgetShrinker.kt` 原地收缩超长 `ToolResult.output` 为占位符、**保 callId、最旧优先**（= 免模型尾部清理，与 dsh「先剪枝后摘要」第一步同构）；`WindowContextCompressor.kt` 切点回退工具组边界 + `TURN_ALIGNED`，水位 0.6。
- **前缀指纹**：`ContextBudget.prepare` 全量估算，system 逐字节不变不变量（SHA-256 单测）；`ContextBudget.observePrefix` 每请求记一行含 `cached=` 累计 + 分段指纹（`SYSTEM/SKILLS/TOOLS/HISTORY`）。
- **缓存解析**：`OpenAiCompatibleAdapter.kt`（~@141-142）已解析 `prompt_tokens_details.cached_tokens → GenerationEvent.Usage(prompt, completion, cached)`；`ReasoningDelta` 流式回传已实现（@108）。
- **手动压缩**：`ChatViewModel.compressContext → ConversationOps`，切点 `compressedUpTo` 经 `bubbles.toHistoryMessages` 生效且可撤销。

### 1.2 缓存命中阻断项
| 阻断 | 现状（核实） | 性质 |
|---|---|---|
| **I2 前缀过短** | 静态前缀（system+6 工具 schema）≈ **700–800 token**；GLM 门槛实测 `(2303,2502]`（粒度 64，`w25-prompt-cache-and-w3-design.md`）⇒ 跨轮/跨题**必然 0 命中** | 量级问题，非 bug；GLM 上缓存 ROI 极低 |
| **I1 跨轮分裂** | `ChatViewModel.send()`（~@278）每次从 UI 气泡**重建 history**，工具轨迹不进持久 history（只走 working 副本）；首个含工具调用轮起重建序列与发出序列分叉 ⇒ 跨轮前缀失效（纯文本轮仍严格可命中） | 真 bug 级阻断 |
| **I3 reasoning 回传** | adapter 已流式 `emit ReasoningDelta`；是否**持久化进 history**（影响跨轮前缀稳定性 + 质量）待核实 | 待核实 |
| **写入延迟** | 多模型实测（W6）：NV nemotron `0→0→4336`（≥2 次才命中）；OR dots/north 恒 0 | 命中率须按「第 N 次请求」观测，非首轮 |
| **主战场** | 现役主用 **NVIDIA nemotron-3.5-super-vl（1M，可缓存，写入延迟≥2 次）**；GLM 前缀太短基本拿不到命中 | 缓存工程 ROI 集中在 **NV 端点** |

---

## 2. 对标分析（我们的设计 vs 5 仓库 + Tier 映射）

| 维度 | dsh | ZCode | minimax | Octop | Operit | **我们** | Tier |
|---|---|---|---|---|---|---|---|
| 压缩形态 | 尾部指令+前缀回放；先剪枝后摘要 | CompactBoundary+ microcompact | shared_prefix 检查点 | 依赖包+旁路 | 窗口记忆重建 | L1 丢历史/L2 摘要/L3 字符；**无 microcompact** | T3 |
| 压缩梯度 | 剪枝→摘要 | microcompact→区域→全量 | turn 切点 | 全量 | 滚动摘要 | 全量丢轮（粗） | T3 |
| 锚定头部/保留尾部 | keep-tail ✅ | 组保留 | keepRecent ✅ | — | — | 砍头保尾 ✅ | T1 |
| 辅助/摘要请求复用主前缀 | ✅ 标杆 | skipCacheWrite | ✅ shared_prefix | — | — | ⚠️ L2 摘要器待核实 | **T3(P0)** |
| append-only | ✅ 事件溯源 | ✅ | ❌ | — | — | ❌ 压缩改写历史 | T2(P2) |
| 工具 schema 稳定 | ✅ | 分区 | run 内 | 拼前 | ✅ 哈希锚 | ✅ run 内快照（I1 破坏） | T1 |
| 组装指纹监控 | 事件溯源 | event log | ✅ SHA-256 指纹 | 账本 | 缓存键 | ⚠️ 有指纹无漂移闸门 | T1 |
| 命中率一等指标 | 可回放 | ✅ +Trend | ✅ TUI | ✅ 轨迹 | ✅ 截断两位 | ⚠️ 有原始值无 UI/指标 | **T0(P0)** |
| 三桶用量归一 | ✅ | ✅ | ✅ | ✅ 含 DS | ✅ 多口径 | ⚠️ OpenAI 系仅 | T0 |
| 数据面出前缀 | 归档(DB) | microcompact | ✅ 文件归档 | ✅ artifact 旁路 | — | ⚠️ 占位符是否字节稳定待核实 | T3 |

**差距焦点（按 ROI 排序）：**
1. **W7-A** 摘要请求复用主前缀（dsh 标杆，cache ROI 最大）
2. **W7-B** I1 跨轮分裂修复（决定跨轮命中能否从 0 起步）
3. **W7-C** 命中率一等指标 + 三桶归一（分母口径见 §5）
4. **W7-D** I2 处置：不为凑缓存注水 system
5. **W8-01** microcompact（细粒度清旧 tool result 优于粗粒度丢轮）
6. **W8-03** 多厂商 usage 字段适配（DeepSeek `prompt_cache_hit_tokens`/Kimi/GLM）

---

## 3. 对照「十条准则」差距清单

| # | 准则 | 现状 | 优先级 |
|---|---|---|---|
| 1 | 前缀逐字节稳定 | ⚠️ system 稳定，I1 跨轮重建破坏 history 前缀 | P0 |
| 2 | append-only 历史 | ❌ 压缩改写历史（system 不变 ✅） | P2（dsh 式成本高，暂不追） |
| 3 | 锚定头部保留尾部 | ✅ 砍头保尾方向对 | — |
| 4 | 辅助请求复用主前缀 | ⚠️ 待核实 L2 摘要器 | **P0** |
| 5 | 工具 schema 稳定 + 延迟注册 | ⚠️ run 内快照，跨 run 重建破坏 | P1 |
| 6 | 断点最少化 | ❌ 无显式断点（隐式，可接受） | P3（仅 Kimi 显式可做） |
| 7 | 压缩梯度（microcompact 优先） | ❌ 全量丢轮 | P1 |
| 8 | provider usage 为真源 | ✅ | — |
| 9 | 触发三件套（水位+idle+溢出） | ⚠️ 缺 idle/overflow 反应式 | P2 |
| 10 | 可审计可恢复 | ⚠️ 日志+撤销，无事件溯源锁 | P2 |

---

## 4. 方向路线（Tier ↔ W7/W8 映射，可逐条验收）

### Tier 0 · 计量先行
- **W7-C 命中率一等指标** ｜ 目标：把 `cacheRead/cacheWrite/fresh` 三桶做成会话级一等指标，进 UI 状态条/诊断卡 ｜ 依赖：adapter 已解析 cached（~@141）｜ 验证：`Usage` 三桶在各端点均被正确填充 ｜ 验收：`cacheHit%` 与原始 usage JSON 同屏展示，无字段丢失。
- **W7-02 前缀缓存命中率真机验证** ｜ 目标：在 NV 端点实测跨轮 `cached_tokens>0` 并测出 NV 真实最小前缀长度 ｜ 依赖：设备 NV-VL 密钥已录入、32K 档 ｜ 验证：≥3 轮同前缀请求，第 3 轮 `cached>0` ｜ 验收：产出「NV 命中阈值 + 当前 ~800 前缀是否够」事实表。
- **W8-03 多厂商 usage 归一** ｜ 目标：兼容 DeepSeek `prompt_cache_hit_tokens`、Kimi 三接口、GLM `cached_tokens`，统一内部 `Usage` ｜ 依赖：W7-C 三桶模型 ｜ 验证：用各厂商 fixture 回放解析 ｜ 验收：单测覆盖 ≥3 家字段语义。

### Tier 1 · 组装冻结
- **W7-B 修复 I1 跨轮分裂** ｜ 目标：确立 history 单一真源（工具轨迹进持久 history 或前缀指纹对「重建序列」豁免），使跨轮前缀稳定 ｜ 依赖：核实 `ChatViewModel.send()`@278 重建逻辑 ｜ 验证：连续 ≥3 轮含工具调用，第 3 轮前缀指纹 `HISTORY` 段与前轮逐字节一致 ｜ 验收：跨轮命中从 0 → 非 0（NV 端点）。
- **W7-D I2 处置决策** ｜ 目标：GLM 前缀 ~800 < 4096 ⇒ **不为凑缓存加长 system**（w25 铁律 + GLM ROI 低）；把 opener 自然内容（记忆/技能 structured 段）稳定化即可，不注水 ｜ 依赖：W7-02 事实表 ｜ 验证：对照「首差异点位置」公式，opener 稳定性审计 ｜ 验收：文档化「不注水」决策 + 自然稳定段清单。
- **W7-E 组装指纹漂移闸门（新增）** ｜ 目标：把现有 `observePrefix` 指纹升级为「漂移闸门」——非预期变更（非追加/非压缩切点）触发告警/拦截 ｜ 借鉴：minimax `assembly-fingerprint`（保空白、工具规范序、排除历史）｜ 验证：注入一个头部随机值 ⇒ 闸门报漂移；尾部变更 ⇒ 不报 ｜ 验收：CI/真机均能在前缀将失效前预警。

### Tier 2 · 追加式历史
- **W8-02 stable/dynamic 分段布局** ｜ 目标：身份/工具/规则在前（stable），日期/记忆检索/动态内容在后（dynamic），为未来显式断点打底 ｜ 依赖：W7-B/W7-D ｜ 验证：分段指纹按 stable/dynamic 分别可识别 ｜ 验收：custom prompt 只跳过 dynamic 段（不撼 stable body）。
- append-only 事件溯源（dsh 式）标记 **P2/暂不做**（成本高、当前 system 不变不变量已兜底）。

### Tier 3 · 缓存感知压缩
- **W7-A 摘要请求复用主前缀（P0）** ｜ 目标：`EngineBackedSummarizer` 逐字节回放 `system+tools+被压区`、指令置尾、带完整 tools（抄 dsh 标杆）｜ 依赖：核实 L2 摘要器当前是否复用 ｜ 验证：摘要请求的前缀指纹与主请求首段逐字节一致 ｜ 验收：压缩触发瞬间缓存不失效（dsh「恰好在对话最大时失去缓存」事故不复现）。
- **W7-01 压缩真机重放** ｜ 目标：换 **NV-VL** 端点（OR 免费模型 5×16KB 超时），判据 `实发 ≤ 27 904`（32K 档硬预算）｜ 依赖：夹具就位 ｜ 验证：`module_{1..5}.kt` 并行读 5×16KB ⇒ 实发 ≤ 27904 ｜ 验收：工具循环 + 用量行正常。
- **W7-03 手动压缩真机验收** ｜ 目标：`cached_tokens=0` 双向断言 + 可逆性 + UI「缓存重建、费用更高」明示 ｜ 依赖：W7-C 指标 ｜ 验证：手动压缩后首轮 `cached=0`（前缀重建，符合预期，双向断言）｜ 验收：撤销后下一轮命中恢复。
- **W8-01 引入 microcompact** ｜ 目标：只清旧 tool result 占位（保 KV 前缀不断裂），优先于全量丢轮 ｜ 默认值（ZCode）：`keepRecentToolResults=5`、触发 `0.9×window + 2000 token buffer`、`minTokenSavings=256`、60min idle ｜ 依赖：W7-A ｜ 验证：`minSavings<256` 时不触发（避免小收益造断点）｜ 验收：清旧结果后前缀指纹不变。
- **W8-04 压缩↔缓存联合判据** ｜ 目标：「压缩后命中率恢复曲线」基准（dsh `session-corpus` 雏形）｜ 依赖：W7-C + W8-01 ｜ 验证：压缩前后命中率差值在阈值内 ｜ 验收：避免「压了没用/压坏了」。
- **W8-05 反应式压缩** ｜ 目标：provider `finish_reason=length`/400 `context limit` 归一化触发（ZCode/dsh 溢出触发）；idle 60min 顺手 microcompact ｜ 依赖：W8-01 ｜ 验证：超长请求被拒后自动恢复 ｜ 验收：溢出重试计数封顶不雪崩。

### Tier 4 · 系统协同
- 选 radix/token 粒度或解耦哈希粒度引擎、缓存感知路由、容量规划——**对 harness 侧基本 N/A**（路由/块粒度由云厂商控制）；仅在选择供应商时偏好带缓存感知路由者（NV、DeepSeek）。记为 `不适用`，不排工作项。

---

## 5. 关键工程参数速查表（直接借用，勿重造轮子）

| 参数 | dsh | ZCode microcompact | minimax | Operit | 我们建议默认 |
|---|---|---|---|---|---|
| 压缩压力阈值 | 0.8×窗口 | — | floor(0.9×窗口) | — | **0.8**（对齐 dsh） |
| 保尾比例 | 0.16 | — | — | — | **0.16** |
| 摘要 maxTokens | 8192 | — | — | — | **8192** |
| keepRecent 工具结果 | — | 5 | — | — | **5** |
| 触发缓冲 | — | 2000 token | — | — | **2000** |
| minSavings | — | 256 | — | — | **256**（省不够不动） |
| idle 触发 | — | 60min | — | — | **60min** |
| 命中率分母 | 三桶 | 含 write | 含 write | 截断两位 | **采用 Octop：`cache_read/(cache_read+fresh)`，write 单独展示**（见下） |

### 命中率口径决策（报告4 §3.2 vs §3.4 分歧）
- minimax：`cacheRead/(fresh+cacheRead+cacheWrite)`（含写，偏保守）
- Octop：`cache_read/(input+cache_read)`（不含写）
- **本案采用 Octop 口径作头条** `cacheHit% = cache_read / (cache_read + fresh_uncached)`，理由：write 是「首次写入付费」，不应稀释「已存在缓存的命中率」；但**三桶原始值始终同屏展示**，不丢信息。UI 展示照 Operit 规范：截断两位小数、不进位到 100%、input=0 显 `-`。

---

## 6. 真机验证方案（给 W7 执行）

**设备现状**：NV-VL 已录入密钥、档位 32K、5×16.3KB 夹具就位（`_w6/ui.py`：`adb -P 15037`、中文不可注入、发送键动态 dump）。

**缓存命中验证（应用「首差异点位置」公式）：**
- 设计探针：让**唯一变化的 token 落在尾部**（最后一条 user 消息内容），system+tools+history 逐字节不变 ⇒ 预期命中率 ≈ `(total - changed)/total`。
- 用 NV 端点发 ≥3 轮同前缀请求（前两轮预热，第 3 轮查 `cached_tokens>0`）。
- 记录 `prompt / cached / prefix_fp / changed` 同行；`changed=none 且 cached>0` 才算命中；`changed≠none` 用前缀指纹定位漂移段（SYSTEM/SKILLS/TOOLS/HISTORY）。

**三态区分（cached=0 时）：**
1. **真没命中**：指纹 `changed=none` 且已 ≥3 轮 ⇒ 前缀太短或厂商未缓存 ⇒ 查 W7-02 事实表（NV 真实阈值）。
2. **前缀太短(I2)**：GLM 上 ~800<4096 ⇒ 不注水，转 NV 主战场。
3. **写入延迟未到**：NV `0→0→4336` ⇒ 发第 4、5 轮再看。

**压缩验证：**
- 32K 档并行读 5×16KB ⇒ 实发 ≤ 27 904（W7-01）。
- 手动压缩后首轮 `cached_tokens=0`（前缀重建，符合预期，双向断言 W7-03）。
- **占位符字节稳定性专项**（W8-01 前置）：同一工具结果跨调用，占位符内容必须字节一致（否则自身成新差异点）；核 `HardBudgetShrinker` 占位符生成是否含随机/时间戳。

---

## 7. 铁律与红线（守住项）

1. **不改写已缓存区**：压缩=「尾部替换 + 高价值引用」，绝不中段改写（dsh 0.16% vs 99.9% 事故）。
2. **不为凑缓存注水 system**：实测反优化（贵 2.5×），GLM ROI 低（w25 铁律）。
3. **摘要请求必复用主前缀 + 带 tools**：省略 tools 会缩短序列破坏对齐（dsh 否决项）。
4. **压缩判据量纲与估算器解耦**：字符硬上限 + 按真值标定（W6 已落地，不回退）。
5. **minSavings 阈值**：省不够 256 token 不动历史（避免小收益造前缀断点）。
6. **主战场策略**：缓存工程 ROI 集中在 **NVIDIA nemotron（1M，可缓存）**；GLM 仅作低优先级验证。

---

## 8. 下一波前置结论

1. **缓存命中工程主战场 = NV nemotron（1M，可缓存）**，非 GLM——GLM 前缀过短且 ROI 低，I2 不值得注水。
2. **观测管道已 70% 就位**（adapter 解析 cached、ContextBudget 前缀指纹含 cached=），差「真机验证 + cache N% 一等指标 + I1 修复」。
3. **最大 ROI 两件事**：L2 摘要请求复用主前缀（W7-A）、I1 跨轮分裂修复（W7-B）——直接决定跨轮命中能否从 0 起步。
4. **Tier 0–4 框架**已把前三份报告的经验收拢成可验收序列；本报告即下一波（W7/W8）交接文档。
