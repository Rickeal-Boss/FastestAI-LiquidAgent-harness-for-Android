package com.rickeal.liquidharness.core.data

import java.io.IOException

/**
 * 保存端点失败的分类。
 *
 * 为什么要有这个类型：`UrlRejectReason` 在 `core-net`，而 `core-net` 是 `core-data` 的
 * `implementation` 依赖（不向上暴露类型）。UI 层要按原因给不同文案，又拿不到 core-net 的
 * 枚举 —— 所以由 `core-data` 把「失败类别」翻译成本模块自己的稳定契约。
 * 这是**错误码**，不是文案：文案由 UI 从 `strings.xml` 映射（守卫 G16 基线 0）。
 */
enum class EndpointSaveError {
    /** 出网目标被 `UrlGuard` 拒绝（内网 / 元数据 / 明文 http / 带凭据的 URL）。 */
    UNSAFE_URL,

    /** 密钥加密落盘失败（Keystore 异常 / 磁盘不可写）。**不能静默**，否则用户以为存上了。 */
    SECRET_WRITE_FAILED,

    /** 其它 IO 失败（清单写盘失败等）。 */
    SAVE_FAILED,
}

/**
 * 保存端点失败。继承 [IOException] 是因为绝大多数失败源是 IO；
 * 调用方按 [error] 分类，**不要**解析 [message]（那是给日志的英文诊断串）。
 */
class EndpointSaveException(
    val error: EndpointSaveError,
    message: String,
) : IOException(message)
