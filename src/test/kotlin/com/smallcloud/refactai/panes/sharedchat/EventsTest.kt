package com.smallcloud.refactai.panes.sharedchat

import kotlin.test.Test
import org.junit.jupiter.api.Assertions.*
import com.smallcloud.refactai.panes.sharedchat.Events

class EventsTest {
    @Test
    fun parseMessage() {
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
}