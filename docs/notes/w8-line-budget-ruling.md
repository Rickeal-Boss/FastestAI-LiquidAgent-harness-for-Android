# W8 行数预算一次性裁决

> 主理人裁决 · 2026-11-06 · 基线 W8 功能波
> 关联：`docs/notes/w7-line-budget-ruling.md`（W7 先例，**不引用为先例**）

## 裁决

**feature-settings 单模块上限 3400 → 3500（+100）。** `TOTAL_LINE_CAP = 42500` **保持不动**。

## 事实（实跑 `wc -l`）

- W8 功能波给 feature-settings 新增 `EndpointAdvancedConfig.kt`（端点高级配置 UI，用户 W8 需求 ①）+
  EndpointEditScreen / SettingsScreen / SettingsViewModel / SettingsRoute 的 Markdown 与图片闸门接线。
- 已做的合规压缩（**先压缩、后例外**，不是直接调上限）：
  1. 高级配置 14 个开关改**数据驱动**（`ToggleSpec` + `capabilityToggles()`），229 → ~175 行；
  2. `parseExtraBody` / `ReasoningEffort.uiKey` **下沉 core-model**（`EndpointAdvancedValidation.kt`），UI 侧减 ~25 行；
  3. `EndpointEditScreen()` 方法 123 行 → 117（压注释，守 G11 ≤120）；
  4. `EndpointCapabilities.kt` 308 行（> core-model 单文件 300）→ 拆出 `EndpointAdvancedValidation.kt` 后 139 行。
- 即便做完四条，feature-settings 仍 ~3420 > 3400（功能实体行数，非可压缩 boilerplate）。

## 为什么不拆模块

W4 已从 feature-settings 拆出 `:feature-settings:feature-tools`（当时 3451 > 3000）。
再拆需新建 Gradle 模块 + 迁移 MCP / Memory / Skill 页，属**结构性重构**，与「端点高级配置」这一
功能交付无因果关系，且会在功能波中途引入大范围 import / DI 变更风险。按 W7-07 同一判断：
功能预算与拆分分开记账，拆分留给独立的重构波。

## 边界（诚实声明）

- 这是**本波一次性例外**，**不构成先例**；W9 触顶仍须先拆（优先 McpServerEditScreen 377 / McpServerListScreen 353）。
- `TOTAL_LINE_CAP = 42500` 未动；全仓总量仍由 TOTAL 一条管。
- `scripts/arch-guard.sh` 的 `LINE_CAPS` 已同步 3400 → 3500，并在该行上方留了指向本文的注释。
