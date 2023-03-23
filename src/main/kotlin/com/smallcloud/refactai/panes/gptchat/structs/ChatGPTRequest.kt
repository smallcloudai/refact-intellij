package com.smallcloud.refactai.panes.gptchat.structs

import com.smallcloud.refactai.panes.gptchat.State
import java.net.URI

data class ChatGPTRequest(
        val uri: URI,
        val token: String?,
        val conversation: List<State.QuestionAnswer>,
)