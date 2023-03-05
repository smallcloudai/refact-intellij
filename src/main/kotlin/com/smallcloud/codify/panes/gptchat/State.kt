package com.smallcloud.codify.panes.gptchat

import com.smallcloud.codify.panes.gptchat.utils.StringUtil

class State {
    data class QuestionAnswer(val question: String, var answer: String = "", var code: String = "")

    var conversationId: String? = null
    private val conversations_: MutableList<QuestionAnswer> = mutableListOf()

//    init {
//        accessToken = HttpConnection.instance["https://chat.openai.com/api/auth/session"]
//    }

    val conversations: List<QuestionAnswer>
        get() = conversations_.toList()

    fun pushQuestion(question: String) {
        conversations_.add(QuestionAnswer(question))
    }

    fun pushCode(code: String) {
        conversations_.last().code += code
    }

    fun pushAnswer(answer: String) {
        conversations_.last().answer += answer
    }

    fun buildConversations(): String? {
        if (conversations.isEmpty()) return null
        val sb = StringBuilder()
        for (qa in conversations) {
            sb.append(StringUtil.appendMe(qa.question, qa.code))
            if (qa.answer.isNotEmpty()) {
                sb.append(StringUtil.appendQuestion(qa.answer))
            }
        }
        return sb.toString()
    }

    companion object {
        val instance = State()
    }
}