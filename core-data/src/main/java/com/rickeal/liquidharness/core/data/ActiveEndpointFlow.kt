package com.rickeal.liquidharness.core.data

import com.rickeal.liquidharness.core.model.RemoteEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * 活跃端点的响应式流（W9-01 P1-3）。
 *
 * `activeEndpointId` 变化或端点内容（含 supportsVision）变化都会立即反映，UI 不再依赖
 * 重入才重读 —— 旧 `produceState` 的 key 只含 (activeId, container)，漏掉能力位，编辑当前
 * 端点只勾图片时入口仍禁用。落盘 IO 切到 [Dispatchers.IO]，与上游 collect 线程解耦。
 *
 * 抽到独立文件是因为 [AppContainer] 已逼近单文件行数闸门（arch-guard G10）；新增逻辑走拆分
 * 而非调大上限（[AppContainer] 注释见调用点）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun activeEndpointFlow(
    activeEndpointId: Flow<String?>,
    endpoints: EndpointStore,
): Flow<RemoteEndpoint?> =
    activeEndpointId
        .flatMapLatest { id -> endpoints.observe().map { list -> list.firstOrNull { it.id == id } } }
        .flowOn(Dispatchers.IO)
