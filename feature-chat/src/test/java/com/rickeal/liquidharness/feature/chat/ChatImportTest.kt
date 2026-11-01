package com.rickeal.liquidharness.feature.chat

import com.rickeal.liquidharness.core.data.SessionImportParser
import com.rickeal.liquidharness.core.model.ChatMessage
import com.rickeal.liquidharness.core.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话导入的**物化**判据（`docs/notes/w6-plan.md` §四）。
 *
 * ## 为什么判据钉在这里而不是 `ChatViewModel.importConversation`
 *
 * `ChatViewModel` 依赖 `viewModelScope` + `AppContainer`（DataStore / Keystore），
 * 本模块测试源集没有 coroutines-test / Robolectric，JVM 上无法实例化 —— 这条限制
 * `newConversation` 的 KDoc 里已如实登记。所以判据钉在**纯函数**上：
 * `importConversation` 只剩「newConversation() + 调用这个映射」两行，
 * 真正会出错的部分（角色映射、条数、附件）全在这里。
 *
 * 「气泡数 == 消息数」是这里的核心断言：少一条意味着题面被静默截断，
 * 而 1M 长上下文测试正是靠条数与 token 数判定，截断会让测出来的每个数字都不可信。
 */
class ChatImportTest {

    @Test
    fun parsedConversationBecomesSameNumberOfBubbles() {
        val text = """
            {"role":"user","text":"question one"}
            {"role":"assistant","text":"answer one"}
            {"role":"tool","text":"{\"ok\":true}"}
            {"role":"user","text":"question two"}
        """.trimIndent()

        val parsed = SessionImportParser.parse(text, SessionImportParser.Format.JSONL)
        assertTrue(parsed is SessionImportParser.Result.Ok)
        val messages = (parsed as SessionImportParser.Result.Ok).messages
        val bubbles = messages.toChatBubbles()

        assertEquals(3, messages.size)
        assertEquals(messages.size, bubbles.size)
        assertEquals(1, parsed.droppedToolRoles)
        assertEquals(listOf(Role.USER, Role.MODEL, Role.USER), bubbles.map { it.role })
        assertEquals("question one", bubbles[0].text)
        assertEquals("answer one", bubbles[1].text)
    }

    @Test
    fun importedBubblesCarryNoAttachments() {
        val bubbles = listOf(ChatMessage(role = Role.USER, text = "q")).toChatBubbles()
        assertTrue(bubbles.single().attachments.isEmpty())
    }

    @Test
    fun importedBubblesAreNotStreaming() {
        val bubbles = listOf(ChatMessage(role = Role.MODEL, text = "a")).toChatBubbles()
        assertTrue(!bubbles.single().streaming)
        assertTrue(bubbles.single().traces.isEmpty())
    }

    @Test
    fun systemAndToolMessagesAreNotMaterialized() {
        val messages = listOf(
            ChatMessage(role = Role.SYSTEM, text = "sys"),
            ChatMessage(role = Role.USER, text = "q"),
            ChatMessage(role = Role.TOOL, text = "t"),
        )
        val bubbles = messages.toChatBubbles()

        assertEquals(1, bubbles.size)
        assertEquals(Role.USER, bubbles.single().role)
    }

    @Test
    fun importedBubbleIdsAreUnique() {
        // LazyColumn 的 key 就是气泡 id：撞 key 会让列表项错位/复用错内容。
        val messages = (0 until 50).map { ChatMessage(role = Role.USER, text = "m" + it) }
        val bubbles = messages.toChatBubbles()
        assertEquals(50, bubbles.map { it.id }.toSet().size)
    }

    @Test
    fun importedConversationSurvivesRoundTripThroughHistoryMapper() {
        // 导入的气泡必须能原样映射回 history —— 否则「导入 → 发一条消息」就少半段题面。
        val messages = listOf(
            ChatMessage(role = Role.USER, text = "q1"),
            ChatMessage(role = Role.MODEL, text = "a1"),
        )
        val rebuilt = messages.toChatBubbles().toHistoryMessages()
        assertEquals(messages.map { it.text }, rebuilt.map { it.text })
        assertEquals(messages.map { it.role }, rebuilt.map { it.role })
    }
}
