package com.smallcloud.refactai.panes.sharedchat

import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.ActiveFileToChat
import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.FileInfoPayload
import com.smallcloud.refactai.panes.sharedchat.Events.Editor
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.*

class EventsTest {

    @Test
    fun formatSnippetToChatTest() {
        val id = "foo"
        val snippet = Events.Editor.Snippet()
        val payload = Editor.SetSnippetPayload(id, snippet)
        val message = Editor.SetSnippetToChat(payload)
        val result = Events.stringify(message)
        val expected = """{"type":"chat_set_selected_snippet","payload":{"id":"foo","snippet":{"language":"","code":"","path":"","basename":""}}}"""
        assertEquals(expected, result)
    }

    @Test
    fun formatActiveFileToChatTest() {
        val file = Events.ActiveFile.FileInfo()
        val id = "foo"
        val payload = FileInfoPayload(id, file)
        val message = ActiveFileToChat(payload)
        val result = Events.stringify(message)
        val expected = """{"type":"chat_active_file_info","payload":{"id":"foo","file":{"name":"","path":"","can_paste":false,"attach":false}}}"""

        assertEquals(expected, result)
    }


    @Test
    fun configMessage() {
        val message = Events.Config.Update(
            "chat-id",
            Events.Config.Features(true, false),
            Events.Config.ThemeProps("light")
        )
        val result = Events.stringify(message)
        val expected = """{"type":"receive_config_update","payload":{"id":"chat-id","features":{"ast":true,"vecdb":false},"themeProps":{"mode":"light","hasBackground":false,"scale":"90%","accentColor":"gray"}}}"""

        assertEquals(expected, result)
    }

}