package com.smallcloud.refactai.panes.sharedchat

import com.google.gson.Gson
import com.google.gson.GsonBuilder
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

//    @Test
//    fun parseChatMessage() {
//        val msg = """{
//            "type":"chat_question",
//            "payload": {
//                "id":"f6de4753-f6dd-4964-9454-4b3132600f04",
//                "messages": [
//                    ["user","What's this?\n@workspace\n@symbols-at /Users/marc/PycharmProjects/pythonProject2/main.py:341\n@file /Users/marc/PycharmProjects/pythonProject2/main.py:341\n"]
//                ],
//                "title":"",
//                "model":"",
//                "attach_file":false
//            }
//        }""".trimMargin()
//    }

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
}