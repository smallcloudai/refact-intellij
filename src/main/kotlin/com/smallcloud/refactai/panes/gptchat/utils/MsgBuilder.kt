package com.smallcloud.refactai.panes.gptchat.utils

import com.google.gson.Gson
import com.smallcloud.refactai.panes.gptchat.State

object MsgBuilder {
    fun build(conversation: List<State.QuestionAnswer>): String {
        val messages: MutableList<Map<String, Any>> = mutableListOf()
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
                "stream" to true,
                "messages" to messages
        )
        val gson = Gson()
        return gson.toJson(msgDict)
    }
}