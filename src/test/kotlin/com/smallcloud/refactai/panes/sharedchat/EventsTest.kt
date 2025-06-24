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
        val expected = """{"type":"config/update","payload":{"features":{"ast":true,"vecdb":false,"images":true,"statistics":true,"knowledge":false},"themeProps":{"appearance":"light","hasBackground":false,"scale":"90%","accentColor":"gray"},"lspPort":8001,"apiKey":"apiKey","addressURL":"http://127.0.0.1;8001","keyBindings":{"completeManual":"foo"},"tabbed":false,"host":"jetbrains"}}"""
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

    @Test
    fun parseAnimationStart() {
        val message = """{"type": "ide/animateFile/start", "payload": "path/to/file.txt"}"""
        val expected = Events.Animation.Start("path/to/file.txt")
        val result = Events.parse(message)
        assertNotNull(result)
        assertEquals(expected.type, result?.type)
        assertEquals(expected.payload, result?.payload)
    }

    @Test
    fun parseAnimationStop() {
        val message = """{"type": "ide/animateFile/stop", "payload": "path/to/file.txt"}"""
        val expected = Events.Animation.Stop("path/to/file.txt")
        val result = Events.parse(message)
        assertNotNull(result)
        assertEquals(expected.type, result?.type)
        assertEquals(expected.payload, result?.payload)
    }

    @Test
    fun parseIdeActionToolCall() {
        val message = """{
    "type": "ide/toolEdit",
    "payload": {
        "toolCall": {
            "id": "test_tool_call_id",
            "function": {
                "arguments": {
                    "path": "refact/refact-agent/engine/tests/emergency_frog_situation/frog.py",
                    "old_str": "old string",
                    "replacement": "new string",
                    "multiple": false
                },
                "name": "update_textdoc"
            },
            "type": "function",
            "index": 0
        },
        "edit": {
            "file_before": "old string\n",
            "file_after": "new string\n",
            "chunks": [
                {
                    "file_name": "refact/refact-agent/engine/tests/emergency_frog_situation/frog.py",
                    "file_action": "edit",
                    "line1": 32,
                    "line2": 32,
                    "lines_remove": "old string\n",
                    "lines_add": "new string\n",
                    "file_name_rename": null,
                    "application_details": ""
                }
            ]
        },
        "chatId": "test_chat_id"
    }
}"""
        val result = Events.parse(message)
        val oldStr = "old string"
        val newStr = "new string"
        val path = "refact/refact-agent/engine/tests/emergency_frog_situation/frog.py"
        val toolCallArgs = TextDocToolCall.UpdateTextDocToolCall.Function.Arguments(path, oldStr, newStr, false)
        val toolCallFn =  TextDocToolCall.UpdateTextDocToolCall.Function("update_textdoc", toolCallArgs)
        val toolCall = TextDocToolCall.UpdateTextDocToolCall("test_tool_call_id", toolCallFn)
        val chunks = listOf(
            DiffChunk(path, "edit", 32, 32, oldStr + "\n", newStr + "\n")
        )

        val edit = ToolEditResult(oldStr+"\n", newStr+"\n", chunks)
        val payload = Events.IdeAction.ToolCallPayload(toolCall, "test_chat_id", edit)
        val expected = Events.IdeAction.ToolCall(payload)
        assertEquals(expected, result)

        
    }

    @Test
    fun stringifyToolCallResponse() {
        val chatId = "test_chat_id"
        val toolCallId = "test_tool_call_id"
        val payload = Events.IdeAction.ToolCallResponsePayload(toolCallId, chatId, true)
        val event = Events.IdeAction.ToolCallResponse(payload)
        val result = Events.stringify(event)
        val expected = """{"type":"ide/toolEditResponse","payload":{"toolCallId":"test_tool_call_id","chatId":"test_chat_id","accepted":true}}"""
        assertEquals(expected, result)
    }

    @Test
    fun parseToolEditUpdateTextDocType() {
        val msg = """{
            |"type":"ide/toolEdit",
            |"payload":{
                |"toolCall":{
                    |"id":"call_8jGDwPC19TSojpzQWVi82Hpd",
                    |"function":{
                        |"arguments":{"path":"/Users/marc/Projects/refact-lsp/tests/emergency_frog_situation/frog.py","old_str":"class Frog:","replacement":"class Frog:\n    def swim(self, dx, dy, pond_width, pond_height):\n        self.x += dx\n        self.y += dy\n        self.x = np.clip(self.x, 0, pond_width)\n        self.y = np.clip(self.y, 0, pond_height)","multiple":false},
                        |"name":"update_textdoc"
                    |},
                    |"type":"function",
                    |"index":0
                |},
                |"edit":{
                    |"file_before":"foo\n",
                    |"file_after":"bar\n",
                    |"chunks":[{"file_name":"/Users/marc/Projects/refact-lsp/tests/emergency_frog_situation/frog.py","file_action":"edit","line1":6,"line2":6,"lines_remove":"","lines_add":"    def swim(self, dx, dy, pond_width, pond_height):\n        self.x += dx\n        self.y += dy\n        self.x = np.clip(self.x, 0, pond_width)\n        self.y = np.clip(self.y, 0, pond_height)\n","file_name_rename":null,"application_details":""}]
                |},
                |"chatId":"9c374827-dc8f-4b01-9a06-d4ea6e35a228"
            |}
        |}""".trimMargin()

        val result = Events.parse(msg) as Events.IdeAction.ToolCall;

        assertEquals(result.payload.edit.fileAfter, "bar\n")
    }

    @Test
    fun parseOpenFileMessage() {
        val message = """{"type":"ide/openFile","payload":{"file_path":"/home/mitya/.config/refact/customization.yaml","line":10}}"""
        val result = Events.parse(message)
        assertNotNull(result)
        assertTrue(result is Events.OpenFile)
        val openFileEvent = result as Events.OpenFile
        assertEquals("/home/mitya/.config/refact/customization.yaml", openFileEvent.payload.filePath)
        assertEquals(10, openFileEvent.payload.line)
    }

    @Test
    fun parseOpenFileMessageWithoutLine() {
        val message = """{"type":"ide/openFile","payload":{"file_path":"/home/mitya/.config/refact/customization.yaml"}}"""
        val result = Events.parse(message)
        assertNotNull(result)
        assertTrue(result is Events.OpenFile)
        val openFileEvent = result as Events.OpenFile
        assertEquals("/home/mitya/.config/refact/customization.yaml", openFileEvent.payload.filePath)
        assertNull(openFileEvent.payload.line)
    }

    @Test
    fun formatCurrentProjectPayload() {
        val message = Events.CurrentProject.SetCurrentProject("foo")
        val result = Events.stringify(message)
        val expected = """{"type":"currentProjectInfo/setCurrentProjectInfo","payload":{"name":"foo"}}"""
        assertEquals(expected, result)

    }
}