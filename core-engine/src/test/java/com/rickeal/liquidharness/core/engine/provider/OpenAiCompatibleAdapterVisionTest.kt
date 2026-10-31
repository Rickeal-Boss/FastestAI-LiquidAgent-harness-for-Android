package com.rickeal.liquidharness.core.engine.provider

import com.rickeal.liquidharness.core.model.Attachment
import com.rickeal.liquidharness.core.model.AttachmentKind
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.GenerationRequest
import com.rickeal.liquidharness.core.model.RemoteEndpoint
import com.rickeal.liquidharness.core.model.Role
import com.rickeal.liquidharness.core.model.SamplingParams
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 多模态请求体单测（W3）。
 *
 * 判据是**线上形态**，不是「能序列化」：`content` 为字符串时端点不会做视觉编码，
 * 模型只会说「我没看到图片」—— 这个缺陷不报错，只能靠断言 content 是数组抓住。
 */
class OpenAiCompatibleAdapterVisionTest {

    private val adapter = OpenAiCompatibleAdapter()

    private val endpoint = RemoteEndpoint(
        id = "e1",
        name = "test",
        baseUrl = "https://example.com/v1",
        modelId = "vision-model",
        contextLength = 128_000,
        maxOutputTokens = 4096,
        supportsTools = true,
    )

    private fun body(messages: List<ChatMessage>) = adapter.buildBody(
        GenerationRequest(
            messages = messages,
            tools = emptyList(),
            sampling = SamplingParams(),
            endpoint = endpoint,
            idempotencyKey = "run-1-round-1",
        )
    )

    private fun image(base64: String = "AAAA") = Attachment(
        kind = AttachmentKind.IMAGE,
        mimeType = "image/jpeg",
        name = "shot.jpg",
        base64 = base64,
        byteSize = 3,
    )

    private fun contentOf(body: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonElement =
        body["messages"]!!.jsonArray[0].jsonObject["content"]!!

    @Test
    fun plainTextMessageKeepsStringContent() {
        val content = contentOf(body(listOf(ChatMessage(role = Role.USER, text = "hi"))))
        assertTrue(content is JsonPrimitive)
        assertEquals("hi", content.jsonPrimitive.content)
    }

    @Test
    fun imageAttachmentProducesContentArray() {
        val content = contentOf(
            body(listOf(ChatMessage(role = Role.USER, text = "what is this", attachments = listOf(image()))))
        )
        assertTrue(content is JsonArray)
        val parts = content.jsonArray
        assertEquals(2, parts.size)
        assertEquals("text", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("image_url", parts[1].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun imageUrlIsDataUriWithMime() {
        val content = contentOf(
            body(listOf(ChatMessage(role = Role.USER, text = "x", attachments = listOf(image("QUJD")))))
        )
        val url = content.jsonArray[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
        assertEquals("data:image/jpeg;base64,QUJD", url)
    }

    @Test
    fun textAttachmentIsInlinedIntoTextPart() {
        val textAttachment = Attachment(
            kind = AttachmentKind.TEXT,
            mimeType = "text/plain",
            name = "notes.md",
            textContent = "# heading",
        )
        val content = contentOf(
            body(listOf(ChatMessage(role = Role.USER, text = "summarize", attachments = listOf(textAttachment))))
        )
        assertTrue(content is JsonArray)
        val text = content.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("[attached file: notes.md]"))
        assertTrue(text.contains("# heading"))
    }

    @Test
    fun unsendableImageIsDropped() {
        // base64 为空 ⇒ isSendable=false ⇒ 整条附件丢弃，content 退回字符串形态。
        val broken = Attachment(kind = AttachmentKind.IMAGE, mimeType = "image/jpeg", base64 = "")
        val content = contentOf(
            body(listOf(ChatMessage(role = Role.USER, text = "hi", attachments = listOf(broken))))
        )
        assertTrue(content is JsonPrimitive)
    }

    @Test
    fun attachmentsOnNonUserRoleAreIgnored() {
        val content = contentOf(
            body(listOf(ChatMessage(role = Role.MODEL, text = "answer", attachments = listOf(image()))))
        )
        assertTrue(content is JsonPrimitive)
    }
}
