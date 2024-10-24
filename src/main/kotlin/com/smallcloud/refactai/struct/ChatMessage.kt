package com.smallcloud.refactai.struct

import com.google.gson.annotations.SerializedName

data class ChatMessage(val role: String,
                       val content: String,
                       @SerializedName("tool_call_id") val toolCallId: String,
                       val usage: String?)