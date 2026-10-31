# W2 止损判据实跑报告（GLM-4.7 / 真机）

| 指标 | 值 |
|---|---|
| fixture_version | 2 |
| bridge_run_id | 20261031-213255-4008 |
| model / thinking | glm-4.7 / endpoint_default_on |
| apk_sha256 | 4d82c9eae4abf7743d7c7494403bf681423b46fd087df9db52600cb6be688fbd |
| git_head | 4af10a2f1f99f1f0547522ce6abc29a0d384e40c |
| started_at | 2026-10-31T21:33:17 |
| 题面数 | 20 |
| 实际发出请求的题 | 20 |
| **通过** | **19/20** |
| 判据 1（≥18/20） | ✅ 达标 |
| 判据 2（0 条 8 轮不收敛） | ✅ |
| thinking 反证 | ✅ 与 W2.5 一致（开）（38/38 次响应带 reasoning_tokens，共 970） |

## 轮次口径（三个数分开看）

| 指标 | 值 |
|---|---|
| 逻辑轮次 Σ | 41 |
| HTTP 请求数 Σ | 41 |
| 网络重试数 Σ | 0 |

> 逻辑轮次 = 1 + `request.messages` 长度单调递增的次数；重试的 messages 与上一请求相同，不计轮。

## 归因直方图

| 归因 | 条数 |
|---|---|
| ok | 19 |
| over_called | 1 |

## 逐题

| # | 逻辑轮/上限 | HTTP | 重试 | 模式 | 工具 | 结论 | 归因 | 说明 |
|---|---|---|---|---|---|---|---|---|
| W2-T01 | 2/2 | 2 | 0 | tool_required | calculator | ✅ | ok |  |
| W2-T02 | 2/3 | 2 | 0 | tool_required | calculator | ✅ | ok |  |
| W2-T03 | 2/3 | 2 | 0 | tool_required | calculator | ✅ | ok |  |
| W2-T04 | 2/2 | 2 | 0 | tool_required | datetime | ✅ | ok |  |
| W2-T05 | 2/2 | 2 | 0 | tool_required | file_list | ✅ | ok |  |
| W2-T06 | 2/2 | 2 | 0 | tool_required | file_write | ✅ | ok |  |
| W2-T07 | 2/3 | 2 | 0 | tool_required | file_read | ✅ | ok |  |
| W2-T08 | 2/2 | 2 | 0 | tool_required | clipboard | ✅ | ok |  |
| W2-T09 | 2/2 | 2 | 0 | tool_required | clipboard | ✅ | ok |  |
| W2-T10 | 1/3 | 1 | 0 | refusal_ok | - | ✅ | ok |  |
| W2-T11 | 3/3 | 3 | 0 | tool_required | file_read,file_list | ✅ | ok |  |
| W2-T12 | 1/1 | 1 | 0 | no_tool | - | ✅ | ok |  |
| W2-T13 | 1/1 | 1 | 0 | no_tool | - | ✅ | ok |  |
| W2-T14 | 2/3 | 2 | 0 | tool_required | calculator | ✅ | ok |  |
| W2-T15 | 3/6 | 3 | 0 | tool_required | file_write,file_list,file_write | ✅ | ok |  |
| W2-T16 | 2/3 | 2 | 0 | tool_required | file_read | ✅ | ok |  |
| W2-T17 | 3/5 | 3 | 0 | tool_required | calculator,calculator | ✅ | ok |  |
| W2-T18 | 2/4 | 2 | 0 | tool_required | datetime,calculator | ✅ | ok |  |
| W2-T19 | 3/6 | 3 | 0 | tool_required | file_list,file_read | ✅ | ok |  |
| W2-T20 | 2/3 | 2 | 0 | no_tool | file_list | ❌ | over_called | expected no tool, got file_list |

## 缓存命中（`prompt_tokens_details.cached_tokens`）

| 指标 | 值 |
|---|---|
| 请求数 | 41 |
| Σ prompt_tokens | 35489 |
| Σ cached_tokens | 2688 |
| 命中率 | 7.6% |
| 首次命中 | seq=26, prompt=958, cached=896 |

> 实测边界：GLM-4.7 的最小可缓存前缀 ≈ **4096 tokens**、粒度 64 tokens（见 docs/notes/w25-prompt-cache-and-w3-design.md）。
