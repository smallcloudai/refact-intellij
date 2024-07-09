package com.smallcloud.refactai.panes.sharedchat

import com.google.gson.*
import com.intellij.util.containers.toArray
import com.smallcloud.refactai.lsp.LSPCapabilities
import com.smallcloud.refactai.lsp.Tool
import com.smallcloud.refactai.lsp.ToolFunction
import com.smallcloud.refactai.lsp.ToolFunctionParameters
import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.ActiveFileToChat
import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.FileInfoPayload
import com.smallcloud.refactai.panes.sharedchat.Events.Chat.RestorePayload
import com.smallcloud.refactai.panes.sharedchat.Events.Chat.RestoreToChat
import com.smallcloud.refactai.panes.sharedchat.Events.Editor
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.*
import java.lang.reflect.Type
import kotlin.test.expect

class EventsTest {
    @Test
    fun parseReadyMessage() {
        val msg = """{
            type: "${EventNames.FromChat.READY.value}",
            payload: {
                id: "foo"
            }
        }""".trimIndent()
        val expected = Events.Ready("foo")
        val result = Events.parse(msg)

        assertEquals(result, expected)
    }


    @Test
    fun parseResponseContextTest() {
        // TODO: move to lsp
        val msg = """{"role":"context_file", "content":"[{\"file_name\":\"/main.py\",\"file_content\":\"foo\",\"line1\":1,\"line2\":15,\"symbol\":\"00000000-0000-0000-0000-000000000000\",\"gradient_type\":-1,\"usefulness\":0.0}]"}"""
        val result = Events.Chat.Response.parse(msg)
        val expected = Events.Chat.Response.UserMessage(Events.Chat.Response.Roles.CONTEXT_FILE,"""[{"file_name":"/main.py","file_content":"foo","line1":1,"line2":15,"symbol":"00000000-0000-0000-0000-000000000000","gradient_type":-1,"usefulness":0.0}]""")
        assertEquals(expected, result)
    }



    @Test
    fun formatResponseContextTest() {
        val message = Events.Chat.Response.UserMessage(Events.Chat.Response.Roles.CONTEXT_FILE,"""[]""")
        val id = "foo";
        val toChat = Events.Chat.Response.formatToChat(message, id)
        val result = Events.stringify(toChat!!)
        val expected = """{"type":"chat_response","payload":{"id":"foo","role":"context_file","content":"[]"}}"""

        assertEquals(result, expected)
    }

    @Test
    fun parseToolCallDeltaTest() {
        val str = """{"choices":[{"delta":{"tool_calls":[{"function":{"arguments":"","name":"definition"},"id":"call_au5VQgF49buKCRBfyTRyPghf","index":0,"type":"function"}]},"finish_reason":null,"index":0,"logprobs":null}],"created":1718027772.963,"id":"chatcmpl-9YZnJdCnhGL92896rXsyPngi7kB3N","model":"gpt-3.5-turbo-1106","object":"chat.completion.chunk","system_fingerprint":"fp_482d920018"}"""

        val res = Events.Chat.Response.parse(str)
        val toChat = Events.Chat.Response.formatToChat(res, "foo")
        val expected = """{"type":"chat_response","payload":{"id":"foo","choices":[{"delta":{"tool_calls":[{"function":{"arguments":"","name":"definition"},"index":0,"type":"function","id":"call_au5VQgF49buKCRBfyTRyPghf"}]},"index":0}],"created":"1718027772.963","model":"gpt-3.5-turbo-1106"}}"""
        val result = Events.stringify(toChat!!)
        assertEquals(expected, result)
    }

    @Test
    fun parseResponseDetail() {
        val str = """{"detail":"JSON problem: invalid type: sequence, expected a string at line 1 column 46"}"""
        val res = Events.Chat.Response.parse(str)
        val toChat = Events.Chat.Response.formatToChat(res, "foo")
        val result = Events.stringify(toChat!!)
        val expected = """{"type":"chat_error_streaming","payload":{"id":"foo","message":"JSON problem: invalid type: sequence, expected a string at line 1 column 46"}}"""
        assertEquals(expected, result)
    }

    @Test
    fun parseAtCommandCompletionMessageTest() {
        val message = """{"type":"chat_request_at_command_completion","payload":{"id":"foo","query":"@","cursor":1,"trigger":"@","number":5}}"""
        val result = Events.parse(message)
        val expected = Events.AtCommands.Completion.Request("foo", "@", 1, 5, "@")
        assertEquals(expected, result)
    }

    @Test
    fun parsePreviewFileRequest() {
        val message = """{"type":"chat_request_preview_files","payload":{"id":"foo","query": "hello"}}"""
        val result = Events.parse(message)
        val expected = Events.AtCommands.Preview.Request("foo", "hello")
        assertEquals(expected, result)
    }

    @Test
    fun parsePreviewResponse() {
        val response = """{"highlight":[],"messages":[{"content":"[]","role":"context_file"}],"model":"deepseek-coder/6.7b/instruct-finetune"}"""
        val result = Events.gson.fromJson(response, Events.AtCommands.Preview.Response::class.java)
        val expected = Events.AtCommands.Preview.Response(arrayOf(ContentFileMessage(emptyArray())))
        assertEquals(expected, result)

        val payload = Events.AtCommands.Preview.PreviewPayload("foo", result.messages)
        val message = Events.stringify(Events.AtCommands.Preview.Receive(payload))
        val json = """{"type":"chat_receive_at_command_preview","payload":{"id":"foo","preview":[["context_file",[]]]}}"""
        assertEquals(json, message)
    }

    @Test
    fun parseAssitantMessage() {
        val str = """{"role":"assistant","content":"Hello there","tool_calls":[]}"""
        val gson = GsonBuilder().registerTypeAdapter(ChatMessage::class.java, ChatMessageDeserializer()).create()
        val result = gson.fromJson<AssistantMessage>(str, AssistantMessage::class.java)
        // val result = Gson().fromJson<AssistantMessage>(str, AssistantMessage::class.java)
        val expected = AssistantMessage("Hello there", emptyArray())
        assertEquals(expected, result)
    }


    @Test
    fun parseSaveChatMessage() {
        val toolStr = """[{"function": {"arguments": "{\"symbol\": \"frog\"}","name": "definition"}, "id": "call", "index": 0,"type": "function"}]"""

        val message = """{"type":"save_chat_to_history","payload":{"id":"foo","messages":[["context_file",[{"file_name":"/main.py","file_content":"hello\n","line1":1,"line2":1,"symbol":"00000000-0000-0000-0000-000000000000","gradient_type":-1,"usefulness":0}]],["user","hello"],["assistant","Hello there", $toolStr]],"title":"","model":""}}"""
        val result = Events.parse(message)
        println("result: $result")
        val messages: ChatMessages = arrayOf(
            ContentFileMessage(arrayOf(ChatContextFile("/main.py","hello\n",1,1,0.0 ))),
            UserMessage("hello"),
            AssistantMessage("Hello there", arrayOf(ToolCall(function = TooCallFunction(arguments = "{\"symbol\": \"frog\"}", name= "definition"), id="call", index = 0, type = "function")))
        )
        val expected = Events.Chat.Save("foo", messages, "", "")
        assertEquals(expected, result)
    }


    @Test
    fun parseCompletionsResponse() {
        val input = """{"completions":["@file","@workspace","@symbols-at","@references","@definition"],"replace":[0,1],"is_cmd_executable":false}"""
        val result = Gson().fromJson(input, CommandCompletionResponse::class.java)
        val expected = CommandCompletionResponse(
            arrayOf("@file", "@workspace", "@symbols-at","@references","@definition"),
            arrayOf(0, 1),
            false
        )

        assertEquals(expected, result)

    }

    @Test
    fun parseCapsRequest() {
        val input = """{"type":"chat_request_caps","payload":{"id":"foo"}}"""
        val result = Events.parse(input)
        val expected = Events.Caps.Request("foo")
        assertEquals(expected, result)
    }

    @Test
    fun stringifyCompletions() {
        val payload = Events.AtCommands.Completion.CompletionPayload("foo", emptyArray(), arrayOf(0, 1), false)
        val input = Events.AtCommands.Completion.Receive(payload)
        val result = Events.stringify(input)
        val expected = """{"type":"chat_receive_at_command_completion","payload":{"id":"foo","completions":[],"replace":[0,1],"is_cmd_executable":false}}"""
        assertEquals(expected, result)
    }


    @Test
    fun stringifyCompletionPreview() {
        val preview = ChatContextFile("test.py", "foo", 0, 1, 100.0)
        val payload = Events.AtCommands.Preview.PreviewPayload("foo", arrayOf(ContentFileMessage(arrayOf(preview))))
        val input = Events.AtCommands.Preview.Receive(payload)
        val result = Events.stringify(input)
        val expected = """{"type":"chat_receive_at_command_preview","payload":{"id":"foo","preview":[["context_file",[{"file_name":"test.py","file_content":"foo","line1":0,"line2":1,"usefulness":100.0}]]]}}"""
        assertEquals(expected, result)
    }


    @Test
    fun formatResponseChoicesTest() {
        val delta = AssistantDelta(ChatRole.ASSISTANT,"hello")
        val choice = Events.Chat.Response.Choice(delta, 0, null)
        val choices = arrayOf(choice)
        val message = Events.Chat.Response.Choices(choices, "0", "refact" )
        val toChat = Events.Chat.Response.formatToChat(message, "foo")
        val result = Events.stringify(toChat!!)
        val expected = """{"type":"chat_response","payload":{"id":"foo","choices":[{"delta":{"role":"assistant","content":"hello"},"index":0}],"created":"0","model":"refact"}}"""

        assertEquals(expected, result)
    }

    @Test
    fun formatResponseDoneTest() {
        val msg = "data: [DONE]"
        val done = Events.Chat.Response.ChatDone(msg)
        val toChat = Events.Chat.Response.formatToChat(done, "foo")
        val result = Events.stringify(toChat!!)
        val expected = """{"type":"chat_done_streaming","payload":{"id":"foo","message":"data: [DONE]"}}"""

        assertEquals(expected, result)

    }

    @Test
    fun formatResponseErrorMessage() {
        val msg = JsonObject()
        msg.addProperty("detail", "test error")
        val err = Events.Chat.Response.ChatError(msg)
        val toChat = Events.Chat.Response.formatToChat(err, "foo")
        val result = Events.stringify(toChat!!)
        val expected = """{"type":"chat_error_streaming","payload":{"id":"foo","message":"test error"}}"""

        assertEquals(expected, result)
    }

    @Test
    fun formatResponseFailedStreamingTest() {

        val e = Throwable("test error")
        val err = Events.Chat.Response.ChatFailedStream(e)
        val toChat = Events.Chat.Response.formatToChat(err, "foo")
        val result = Events.stringify(toChat!!)
        val expected = """{"type":"chat_error_streaming","payload":{"id":"foo","message":"Failed during stream: test error"}}"""
        assertEquals(expected, result)
    }

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
    fun formatRestoreChatToChat() {
        val chatMessages: ChatMessages = arrayOf(
            ContentFileMessage(arrayOf(
                ChatContextFile("/main.py", "hello", 1, 15, 0.0)
            )),
            UserMessage("hello"),
            AssistantMessage("hello")
        )

        val currentChatId = "foo"
        val chat = Events.Chat.Thread("bar", chatMessages, "refact")
        val payload = RestorePayload(currentChatId, chat)
        val event = RestoreToChat(payload)
        val result = Events.stringify(event)

        val expected = """{"type":"restore_chat_from_history","payload":{"id":"foo","chat":{"id":"bar","messages":[["context_file",[{"file_name":"/main.py","file_content":"hello","line1":1,"line2":15,"usefulness":0.0}]],["user","hello"],["assistant","hello"]],"model":"refact","attach_file":false}}}"""

        assertEquals(expected, result)
    }

    @Test
    fun systemPromptsMessage() {
        val prompts: SystemPromptMap = mapOf("default" to SystemPrompt(text="Use backquotes for code blocks.\nPay close attention to indent when editing code blocks: indent must be exactly the same as in the original code block.\n", description=""),)
        val payload = Events.SystemPrompts.SystemPromptsPayload("foo", prompts)
        val message: Events.SystemPrompts.Receive = Events.SystemPrompts.Receive(payload)
        val result = Events.stringify(message)
        val expected = """{"type":"chat_receive_prompts","payload":{"id":"foo","prompts":{"default":{"text":"Use backquotes for code blocks.\nPay close attention to indent when editing code blocks: indent must be exactly the same as in the original code block.\n","description":""}}}}"""
        assertEquals(expected, result)

    }

    @Test
    fun receiveCapsMessage() {
        val caps = LSPCapabilities()
        val message = Events.Caps.Receive("foo", caps)
        val result = Events.stringify(message)
        val expected = """{"type":"receive_caps","payload":{"id":"foo","caps":{"cloud_name":"","code_chat_default_model":"","code_chat_models":{},"code_completion_default_model":"","code_completion_models":{},"endpoint_style":"","endpoint_template":"","running_models":[],"telemetry_basic_dest":"","tokenizer_path_template":"","tokenizer_rewrite_path":{}}}}"""
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

    @Test
    fun formatToolsResponse() {
        val parameters = ToolFunctionParameters(
            properties = mapOf("query" to mapOf("description" to "Single line, paragraph or code sample.", "type" to "string")),
            type = "object",
            required = arrayOf("query")
        )

        val function = ToolFunction(
            description = "Find similar pieces of code using vector database",
            name = "workspace",
            parameters = parameters
        )
        val tool = Tool(function = function, type="function")

        val payload = Events.Tools.ResponsePayload("foo", arrayOf(tool))
        val message = Events.Tools.Resppnse(payload)
        val results = Events.stringify(message)
        val expected = """{"type":"chat_receive_tools_chat","payload":{"id":"foo","tools":[{"function":{"description":"Find similar pieces of code using vector database","name":"workspace","parameters":{"properties":{"query":{"description":"Single line, paragraph or code sample.","type":"string"}},"type":"object","required":["query"]}},"type":"function"}]}}"""
        assertEquals(expected, results)
    }

    @Test
    fun toolCallMessages() {
        val msg = """{"type":"chat_question","payload":{"id":"490d985c-2bbe-4cb1-84b1-d8153a6fb99a","messages":[["user","In this project what is frog for?\n"],["assistant","",[{"function":{"arguments":"{\"query\": \"frog\"}","name":"search_workspace"},"index":0,"type":"function","id":"call_XRzVBmX0vVtuK1Xi34FMmu2m"},{"function":{"arguments":"{\"symbol\": \"frog\"}","name":"definition"},"index":1,"type":"function","id":"call_EO4S6qEuYPiaqJdAZz6Dou15"}]],["tool",{"tool_call_id":"call_XRzVBmX0vVtuK1Xi34FMmu2m","content":"performed vecdb search, results below"}],["tool",{"tool_call_id":"call_EO4S6qEuYPiaqJdAZz6Dou15","content":"Definition of `frog` haven't found by exact name, but found the close result`Frog`. You can call again with one of these other names:\nFrog\nbring_your_own_frog_to_work_day\ndraw_hello_frog\nfont\n"}],["context_file",[{"file_content":"", file_name":"tests/emergency_frog_situation/set_as_avatar.py","line1":1,"line2":32,"usefulness":0},{"file_content":"#","file_name":"/tests/emergency_frog_situation/jump_to_conclusions.py","line1":1,"line2":58,"usefulness":0}]],["assistant","frog",null],["user","Add a new frog\n"]],"title":"","model":"","attach_file":false,"tools":[{"function":{"description":"Find similar pieces of code or text using vector database","name":"search_workspace","parameters":{"properties":{"query":{"description":"Single line, paragraph or code sample.","type":"string"}},"type":"object","required":["query"]}},"type":"function"},{"function":{"description":"Find similar pieces of code using vector database, search scope limited to a single source file.","name":"search_file","parameters":{"properties":{"file_path":{"description":"Path to the file to search.","type":"string"},"query":{"description":"Single line, paragraph or code sample.","type":"string"}},"type":"object","required":["query","file_path"]}},"type":"function"},{"function":{"description":"Read the file, the same as cat shell command, but skeletonizes files that are too large.","name":"file","parameters":{"properties":{"path":{"description":"Either absolute path or preceeding_dirs/file.ext","type":"string"}},"type":"object","required":["path"]}},"type":"function"},{"function":{"description":"Read definition of a symbol in the project using AST","name":"definition","parameters":{"properties":{"symbol":{"description":"The exact name of a function, method, class, type alias. No spaces allowed.","type":"string"}},"type":"object","required":["symbol"]}},"type":"function"},{"function":{"description":"Find usages of a symbol within a project using AST","name":"references","parameters":{"properties":{"symbol":{"description":"The exact name of a function, method, class, type alias. No spaces allowed.","type":"string"}},"type":"object","required":["symbol"]}},"type":"function"}]}}"""

        val result: Events.Chat.AskQuestion = Events.parse(msg) as Events.Chat.AskQuestion
        val tool = result.messages[2]
        println(tool)
        assertEquals(ChatRole.TOOL, tool.role)
        assertEquals("call_XRzVBmX0vVtuK1Xi34FMmu2m", tool.toolCallId)
        assertEquals( "performed vecdb search, results below", tool.content)
    }

}