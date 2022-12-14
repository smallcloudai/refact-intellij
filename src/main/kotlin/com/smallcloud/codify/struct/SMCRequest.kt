package com.smallcloud.codify.struct

import com.google.gson.annotations.SerializedName

data class SMCRequestBody(
    var sources: Map<String, String>,
    var intent: String,
    @SerializedName("function") var functionName: String,
    @SerializedName("cursor_file") var cursorFile: String,
    var cursor0: Int,
    var cursor1: Int,
    @SerializedName("max_tokens") var maxTokens: Int,
    @SerializedName("max_edits") var maxEdits: Int,
    @SerializedName("stop") var stopTokens: List<String>,
    var temperature: Float = 0.8f,
    var client: String = "",
    var model: String = ""
)

data class SMCRequest(
    var url: String,
    var body: SMCRequestBody,
    var token: String
)
