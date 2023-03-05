package com.smallcloud.codify.panes.gptchat.utils

import com.google.gson.Gson
import com.smallcloud.codify.panes.gptchat.State
import com.smallcloud.codify.panes.gptchat.State.Companion.instance as State

object MsgBuilder {
    fun build(conversation: List<State.QuestionAnswer>, doStream: Boolean): String {
        val messages: MutableList<Map<String, Any>> = mutableListOf(
                mapOf(
                        "role" to "system",
                        "content" to "You are a helpful assistant."
                )
        )
        for (questionAnswer in conversation) {
            messages.add(
                    mapOf(
                            "role" to "user",
                            "content" to if (questionAnswer.code.isEmpty()) questionAnswer.question else
                                questionAnswer.question + "\n" + questionAnswer.code
                    )
            )
            if (questionAnswer.answer.isNotEmpty()) {
                messages.add(
                        mapOf(
                                "role" to "assistant",
                                "content" to questionAnswer.answer
                        )
                )
            }
        }


        val msgDict = mutableMapOf(
                "model" to "gpt-3.5-turbo",
                "stream" to doStream,
                "max_tokens" to 1000,
                "messages" to messages
        )

        if (!State.conversationId.isNullOrEmpty()) {
            msgDict["conversation_id"] = State.conversationId!!
        }
        val gson = Gson()
        return gson.toJson(msgDict)
    }
}