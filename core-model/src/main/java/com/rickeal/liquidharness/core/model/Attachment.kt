package com.rickeal.liquidharness.core.model

import kotlinx.serialization.Serializable

/**
 * 附件类型。**只收三类**：图片（进多模态 content）、音频（转写 / 播放）、纯文本（拼进正文）。
 *
 * 刻意没有 `FILE`（任意二进制）：那等于给模型开一条「读任意本地文件」的通道，
 * 而审批卡上只能显示一个文件名 —— 与 `DefaultTools` 里「不提供任意出站通道」同一条纪律。
 */
@Serializable
enum class AttachmentKind {
    IMAGE,
    AUDIO,
    TEXT,
}

/**
 * 一条用户附件。
 *
 * ## 为什么 base64 直接放在契约里（而不是只存 uri）
 *
 * 请求体构造在 `core-engine`，它拿不到 Android `ContentResolver`（模块边界禁止）。
 * 若契约只存 `content://` 或绝对路径，引擎就得反过来依赖 `core-data` 去做解码，
 * 或者内核在发送前塞一个「已解码」的旁路参数 —— 两者都比「在导入时解码一次、
 * 把字节随消息携带」更糟。代价是历史里会留一份 base64；因此：
 *
 * - **导入侧负责压缩**（`AttachmentImporter`：最长边 1568px、JPEG q=85），
 *   保证单张 ≤ [MAX_IMAGE_BYTES]；
 * - 超限的附件在构造时就被拒绝（fail-closed），不靠发送侧再判一次。
 *
 * ## 字段语义
 *
 * - [base64] 空 ⇒ 该附件**不可发送**（只有本地预览），引擎必须整条丢弃而不是发空串；
 * - [localPath] 只用于 UI 缩略图，绝不进请求体（路径是设备信息）；
 * - [textContent] 仅 [AttachmentKind.TEXT] 使用，由导入侧读成 UTF-8（超长按
 *   [MAX_TEXT_CHARS] 截断并留标记）。
 */
@Serializable
data class Attachment(
    val id: String = newId(),
    val kind: AttachmentKind = AttachmentKind.IMAGE,
    val mimeType: String = "image/png",
    val name: String = "",
    val base64: String = "",
    val byteSize: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val localPath: String = "",
    val textContent: String = "",
) {
    /** 能否进请求体。图片必须有字节；文本必须有正文。 */
    val isSendable: Boolean
        get() = when (kind) {
            AttachmentKind.IMAGE -> base64.isNotEmpty() && mimeType.startsWith("image/")
            AttachmentKind.TEXT -> textContent.isNotEmpty()
            // W3 不做音频输入（提供商支持面太窄，且需要转写链路）；留类型但不发送。
            AttachmentKind.AUDIO -> false
        }

    companion object {
        /** 单张图片上限 1.5 MiB（base64 后约 2 MiB）。超过即拒绝导入。 */
        const val MAX_IMAGE_BYTES: Int = 1_536 * 1024

        /** 文本附件上限 32k 字符（约 8k token），超出截断。 */
        const val MAX_TEXT_CHARS: Int = 32_000
    }
}
