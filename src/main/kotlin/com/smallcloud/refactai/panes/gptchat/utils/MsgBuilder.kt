package com.smallcloud.refactai.panes.gptchat.utils

import com.google.gson.Gson
import com.smallcloud.refactai.panes.gptchat.structs.HistoryEntry

object MsgBuilder {
    fun build(conversation: List<HistoryEntry>): String {
        val messages: MutableList<Map<String, Any>> = mutableListOf()
        for (historyEntry in conversation) {
            if (historyEntry.texts.any { it.isError }) continue
            var text = ""
            historyEntry.texts.forEach {
                text += if (it.isCode) {
                    "\n\n```\n${it.rawText}\n```\n"
                } else {
                    it.rawText
                }
            }

            messages.add(
                    mapOf(
                            "role" to if (historyEntry.me) "user" else "assistant",
                            "content" to text
                    )
            )
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