package com.smallcloud.refactai.panes.gptchat.utils

import com.google.gson.Gson
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.smallcloud.refactai.panes.gptchat.structs.ChatGPTRequest

fun makeAttachedFile(editor: Editor): String {
    val file = editor.document.text
    var attached = ""
    runReadAction {
        var pos0 = editor.selectionModel.selectionStart
        var pos1 = editor.selectionModel.selectionEnd
        while (true) {
            val text = file.substring(pos0, pos1)
            if (text.length > 2000) {
                break
            }
            attached = text
            var moved = false
            val logicalPos0 = editor.offsetToLogicalPosition(pos0)
            val logicalPos1 = editor.offsetToLogicalPosition(pos1)
            if (logicalPos0.line > 0) {
                pos0 = editor.logicalPositionToOffset(LogicalPosition(logicalPos0.line - 1, 0))
                moved = true
            }
            if (logicalPos1.line < editor.document.lineCount - 1) {
                pos1 = editor.logicalPositionToOffset(LogicalPosition(logicalPos1.line + 1, 0))
                moved = true
            }
            if (!moved) {
                break
            }
        }
    }
    return attached
}

object MsgBuilder {
    fun build(req: ChatGPTRequest, longthink: String, attachedFile: String? = null): String {
        val conversation = req.conversation
        val messages: MutableList<Map<String, Any>> = mutableListOf()
        if (attachedFile != null) {
            messages.add(
                    mapOf(
                        "role" to "user",
                        "content" to attachedFile
                    )
            )
            messages.add(
                    mapOf(
                        "role" to "assistant",
                        "content" to "Thanks for context, what's your question?"
                    )
            )
        }
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
                "model" to longthink,
                "stream" to true,
                "messages" to messages,
                "parameters" to mapOf(
                    "temperature" to 0.1,
                    "max_new_tokens" to 1000
                )
        )
        val gson = Gson()
        return gson.toJson(msgDict)
    }
}
