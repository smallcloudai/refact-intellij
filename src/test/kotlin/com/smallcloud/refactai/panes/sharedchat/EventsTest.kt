package com.smallcloud.refactai.panes.sharedchat

import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.ActiveFileToChat
import com.smallcloud.refactai.panes.sharedchat.Events.Editor
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.*

class EventsTest {

    @Test
    fun formatSnippetToChatTest() {
        val snippet = Events.Editor.Snippet()
        val payload = Editor.SetSnippetPayload(snippet)
        val message = Editor.SetSnippetToChat(payload)
        val result = Events.stringify(message)
        val expected = """{"type":"selected_snippet/set","payload":{"snippet":{"language":"","code":"","path":"","basename":""}}}"""
        assertEquals(expected, result)
    }

    @Test
    fun formatActiveFileToChatTest() {
        val file = Events.ActiveFile.FileInfo()
        val message = ActiveFileToChat(file)
        val result = Events.stringify(message)
        val expected = """{"type":"activeFile/setFileInfo","payload":{"name":"","path":"","can_paste":false}}"""

        assertEquals(expected, result)
    }


    @Test
    fun configMessage() {
        val message = Events.Config.Update(
            Events.Config.Features(true, false),
            Events.Config.ThemeProps("light"),
            8001,
            "apiKey"
        )
        val result = Events.stringify(message)
        val expected = """{"type":"config/update","payload":{"features":{"ast":true,"vecdb":false},"themeProps":{"mode":"light","hasBackground":false,"scale":"90%","accentColor":"gray"},"lspPort":8001,"apiKey":"apiKey"}}"""

        assertEquals(expected, result)
    }

}