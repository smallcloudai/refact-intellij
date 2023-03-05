package com.smallcloud.codify.panes.gptchat.structs

import com.smallcloud.codify.panes.gptchat.State
import java.net.URL

data class ChatGPTRequest(
        val url: URL,
        val token: String?,
        val conversation: List<State.QuestionAnswer>,
)