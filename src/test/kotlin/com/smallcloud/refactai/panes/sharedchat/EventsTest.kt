package com.smallcloud.refactai.panes.sharedchat

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.*
import com.smallcloud.refactai.panes.sharedchat.Events

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
        val msg = """{"role":"context_file", "content":"[{\"file_name\":\"/main.py\",\"file_content\":\"foo\",\"line1\":1,\"line2\":15,\"symbol\":\"00000000-0000-0000-0000-000000000000\",\"gradient_type\":-1,\"usefulness\":0.0}]"}"""
        val result = Events.Chat.Response.parse(msg)
        val expected = Events.Chat.Response.UserMessage(Events.Chat.Response.Roles.CONTEXT_FILE,"""[{"file_name":"/main.py","file_content":"foo","line1":1,"line2":15,"symbol":"00000000-0000-0000-0000-000000000000","gradient_type":-1,"usefulness":0.0}]""")

        assertEquals(result, expected)
    }

    @Test
    fun formatResponseContextTest() {
        val message = Events.Chat.Response.UserMessage(Events.Chat.Response.Roles.CONTEXT_FILE,"""[]""")
        val id = "foo";
        val toChat = Events.Chat.Response.formatToChat(message, id)
        val result = Gson().toJson(toChat)
        val expected = """{"type":"chat_response","payload":{"id":"foo","role":"context_file","content":"[]"}}"""

        assertEquals(result, expected)
    }

    @Test
    fun foramtResponseChoicesTest() {
        val delta = AssistantDelta("hello")
        val choice = Events.Chat.Response.Choice(delta, 0, null)
        val choices = arrayOf(choice)
        val message = Events.Chat.Response.Choices(choices, "0", "refact" )
        val toChat = Events.Chat.Response.formatToChat(message, "foo")
        val result = Gson().toJson(toChat)
        val expected = """{"type":"chat_response","payload":{"id":"foo","choices":[{"delta":{"role":"assistant","content":"hello"},"index":0}],"created":"0","model":"refact"}}"""

        assertEquals(expected, result)
    }

    @Test
    fun formatResponseDoneTest() {
        val msg = "data: [DONE]"
        val done = Events.Chat.Response.ChatDone(msg)
        println("done: ${done.toString()}")
        val toChat = Events.Chat.Response.formatToChat(done, "foo")
        println("toChat: ${toChat.toString()}")
        val result = Gson().toJson(toChat)
        val expected = """{"type":"chat_done_streaming","payload":{"id":"foo","message":"data: [DONE]"}}"""

        assertEquals(expected, result)

    }

    @Test
    fun formatResponseErrorMessage() {
        val msg = JsonObject()
        msg.addProperty("detail", "test error")
        val err = Events.Chat.Response.ChatError(msg)
        val toChat = Events.Chat.Response.formatToChat(err, "foo")
        val result = Gson().toJson(toChat)
        val expected = """{"type":"chat_error_streaming","payload":{"id":"foo","message":"test error"}}"""

        assertEquals(expected, result)
    }

    @Test
    fun formatResponseFailedStreamingTest() {

        val e = Throwable("test error")
        val err = Events.Chat.Response.ChatFailedStream(e)
        val toChat = Events.Chat.Response.formatToChat(err, "foo")
        val result = Gson().toJson(toChat)
        val expected = """{"type":"chat_error_streaming","payload":{"id":"foo","message":"Failed during stream: java.lang.Throwable: test error"}}"""
        assertEquals(expected, result)
    }

    @Test
    fun formatSnippetToChatTest() {
        val id = "foo"
        val snippet = Events.Editor.Snippet()
        val result = Events.Editor.formatSnippetToChat(id, snippet)
        val expected = """{"type":"chat_set_selected_snippet","payload":{"id":"foo","snippet":{"language":"","code":"","path":"","basename":""}}}"""
        assertEquals(expected, result)
    }


}