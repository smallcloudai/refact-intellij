package com.smallcloud.codify.panes.gptchat.utils

import com.intellij.openapi.util.text.StringUtil

object StringUtil : StringUtil() {
    fun appendMe(question: String, code: String): String {
        var ret = "> ![](https://intellij-icons.jetbrains.design/icons/AllIcons/general/user.svg) Me\n\n$question\n"
        if (code.isNotEmpty()) {
            ret += "```\n$code\n```"
        }
        ret += "\n\n"
        return ret
    }

    fun appendQuestion(response: String): String {
        return "\n\n> ![](https://intellij-icons.jetbrains.design/icons/AllIcons/general/balloonInformation.svg) ChatGPT\n\n$response\n\n"
    }
}