package com.rickeal.liquidharness.core.model

import java.util.UUID

/**
 * 稳定 id 生成入口。
 *
 * 为什么集中在一个函数里而不是各处 `UUID.randomUUID()`：
 * 1. 落库的 id 形态要统一（无连字符，便于作为文件名 / 日志字段）；
 * 2. 单测需要一处可替换的接缝（`@Serializable` 的默认参数值都指向这里）。
 *
 * 注意：这里**不保证**单调递增，也不是内容寻址 id。
 * 需要「同一内容派生出同一 id」的地方（压缩补丁消息）另算，见 core-agent。
 */
fun newId(): String = UUID.randomUUID().toString().replace("-", "")
