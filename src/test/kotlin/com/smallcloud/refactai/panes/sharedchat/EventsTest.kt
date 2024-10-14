package com.smallcloud.refactai.panes.sharedchat

import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.ActiveFileToChat
import com.smallcloud.refactai.panes.sharedchat.Events.Editor
import kotlin.test.Test
import org.junit.Assert.*

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
        val expected = """{"type":"config/update","payload":{"features":{"ast":true,"vecdb":false},"themeProps":{"mode":"light","hasBackground":false,"scale":"90%","accentColor":"gray"},"lspPort":8001,"apiKey":"apiKey","addressURL":"http://127.0.0.1;8001","keyBindings":{"completeManual":"foo"},"tabbed":false,"host":"jetbrains"}}"""
        assertEquals(expected, result)
    }

    @Test
    fun parsePasteBackMessage() {
        val message = """{"type":"ide/diffPasteBack","payload":"test"}"""
        val expected = Events.Editor.PasteDiff("test")
        val result = Events.parse(message)
        assertNotNull(result)
        assertEquals(expected.type, result?.type)
        assertEquals(expected.payload, result?.payload)
        assertEquals(expected.content, "test")

    }

    @Test
    fun parseStartAnimation() {
        val message = """{"type":"ide/animateFile/start","payload":"/path/to/file.txt"}"""
        val expected = Events.Animation.Start("/path/to/file.txt")
        val result = Events.parse(message)
        assertNotNull(result)
        assertEquals(expected.type, result?.type)
        assertEquals(expected.payload, result?.payload)
    }

    @Test
    fun parseStopAnimation() {
        val message = """{"type":"ide/animateFile/stop","payload":"/path/to/file.txt"}"""
        val expected = Events.Animation.Stop("/path/to/file.txt")
        val result = Events.parse(message)
        assertNotNull(result)
        assertEquals(expected.type, result?.type)
        assertEquals(expected.payload, result?.payload)
    }

    @Test
    fun parseShowPatch() {
        val message = """{
            "type": "ide/diffPreview",
            "payload": {
                "currentPin": "📍OTHER",
                "allPins": ["📍OTHER", "📍OTHER"],
                "results": [{
                    "file_text": "foo",
                    "file_name_edit": null,
                    "file_name_delete": null,
                    "file_name_add": "path/to/file.txt"
                }],
               state: [{
                    "chunk_id": 0,
                    "applied": false,
                    "can_unapply": false,
                    "success": true,
                    "detail": null
               }]
            }
        }"""
        val expectedPayload = Events.Patch.ShowPayload(
            currentPin = "📍OTHER",
            allPins = listOf("📍OTHER", "📍OTHER"),
            results = listOf(
                Events.Patch.PatchResult(
                    fileText = "foo",
                    fileNameEdit = null,
                    fileNameDelete = null,
                    fileNameAdd = "path/to/file.txt"
                )
            ),
            state = listOf(
                Events.Patch.PatchState(
                    chunkId = 0,
                    applied = false,
                    canUnapply = false,
                    success = true,
                    detail = null
                )
            )
        )
        val expected = Events.Patch.Show(expectedPayload)
        val result = Events.parse(message)
        assertNotNull(result)
        assertEquals(expected.type, result?.type)
        // TODO: class might need a compare method
        // assertEquals(expected.payload, result?.payload)
    }

    @Test
    fun parseApplyPatch() {
        val message = """{
            "type": "ide/writeResultsToFile",
            "payload": [{
                "file_text": "foo",
                "file_name_edit": null,
                "file_name_delete": null,
                "file_name_add": "path/to/file.txt"
            }]
        }"""

        val patchResult = Events.Patch.PatchResult(
            fileText = "foo",
            fileNameEdit = null,
            fileNameDelete = null,
            fileNameAdd = "path/to/file.txt"
        )
        val payload = Events.Patch.ApplyPayload(listOf(patchResult))
        val expected = Events.Patch.Apply(payload)

        val result = Events.parse(message)

        assertNotNull(result)
        assertEquals(expected.type, result?.type)
        // TODO: class might need a compare method
        // assertEquals(expected.payload, result?.payload)
    }
}