package com.rickeal.liquidharness.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 全仓唯一的 JSON 配置入口。
 *
 * 为什么集中：`ignoreUnknownKeys` 与 `explicitNulls` 是**前后兼容的唯一支点** ——
 * 供应商加字段、我们自己加字段，都不能让已落盘的 JSON 反序列化失败。
 * 各处自己 `Json { }` 就会出现「A 处能读 B 处不能读」的排查地狱。
 *
 * 参数逐条有理由：
 * - `ignoreUnknownKeys = true`：供应商响应 / 旧版落盘文件里多出来的字段一律忽略；
 * - `explicitNulls = false`：null 字段不写出（省体积，且避免 `{"x":null}` 覆盖默认值）；
 * - `encodeDefaults = true`：落盘时把默认值也写出来 —— 默认值将来会改，
 *   不写出的话「当时用的是哪个默认值」就无法还原；
 * - `isLenient = true` / `coerceInputValues = true`：部分兼容端点会返回
 *   未加引号的裸字符串或 `null` 混进非空字段，宽松解析比崩溃好。
 *
 * ⚠️ 本对象**不用于** HTTP 请求体构造：三族 body 结构差异太大，
 * 由 `core-engine` 的 adapter 用 `buildJsonObject` 手搭（不用 @Serializable 中介类，
 * 否则会变成一堆 nullable 字段）。
 */
object AgentJson {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    /** 解析失败返回 null。用于「可能是心跳 / 可能是垃圾」的场景，不用于必须成功的场景。 */
    inline fun <reified T> decodeOrNull(text: String): T? =
        runCatching { json.decodeFromString<T>(text) }.getOrNull()

    /** 取顶层对象；非对象（数组 / 标量 / 坏 JSON）返回 null。 */
    fun decodeObjectOrNull(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    /** 取字符串字段；缺失或类型不符返回 null。 */
    fun stringOrNull(obj: JsonObject?, key: String): String? =
        (obj?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** 取整数字段；缺失 / 非数字返回 null。兼容 `12` 与 `"12"` 两种形态。 */
    fun intOrNull(obj: JsonObject?, key: String): Int? =
        (obj?.get(key) as? JsonPrimitive)?.content?.toIntOrNull()
}
