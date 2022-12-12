package com.smallcloud.codify.struct

import com.google.gson.annotations.SerializedName

data class Choice(
    val index: Int,
    val text: String,
    val files: Map<String, String>,
    val logprobs: Any,
    val finishReason: String
)

data class SMCPrediction(
    val id: String,
    @SerializedName("object") val obj: String,
    val status: String,
    val created: Float,
    val uploaded: Float,
    val generatedTokensN: Int,
    val choices: List<Choice>,
    val highlightTokens: List<String>,
    val highlightLines: List<String>,
)
