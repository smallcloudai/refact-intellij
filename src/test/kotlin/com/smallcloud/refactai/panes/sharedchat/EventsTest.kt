package com.smallcloud.refactai.panes.sharedchat

import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.ActiveFileToChat
import com.smallcloud.refactai.panes.sharedchat.Events.Editor
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.*

class EventsTest {

    @Test
    fun formatSnippetToChatTest() {
        val snippet = Events.Editor.Snippet()
        val message = Editor.SetSnippetToChat(snippet)
        val result = Events.stringify(message)
        val expected = """{"type":"selected_snippet/set","payload":{"language":"","code":"","path":"","basename":""}}"""
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
        val payload = Events.Config.UpdatePayload(
            Events.Config.Features(true, false),
            Events.Config.ThemeProps("light"),
            8001,
            "apiKey",
            addressURL = "http://127.0.0.1;8001",
            Events.Config.KeyBindings("foo"))
        val message = Events.Config.Update(payload)
        val result = Events.stringify(message)
        val expected = """{"type":"config/update","payload":{"features":{"ast":true,"vecdb":false},"themeProps":{"mode":"light","hasBackground":false,"scale":"90%","accentColor":"gray"},"lspPort":8001,"apiKey":"apiKey","addressURL":"http://127.0.0.1;8001","keyBindings":{"completeManual":"foo"},"tabbed":false,"host":"jetbrains","shiftEnterToSubmit":false}}"""
        assertEquals(expected, result)
    }

    @Test
    fun parsePasteBackMessage() {
        val message = """{"type":"ide/diffPasteBack","payload":"test"}"""
        val expected = Events.Editor.Paste("test")
        val result = Events.parse(message)
        assertNotNull(result)
        assertEquals(expected.type, result?.type)
        assertEquals(expected.payload, result?.payload)
        assertEquals(expected.content, "test")

    }

}