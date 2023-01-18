package com.smallcloud.codify.struct

import com.google.gson.annotations.SerializedName

data class Choice(
    val index: Int,
    val text: String,
    val files: Map<String, String>,
    val logprobs: Any,
    @SerializedName("finish_reason") val finishReason: String
)

data class SMCPrediction(
    val id: String,
    @SerializedName("object") val obj: String,
    val status: String?,
    val model: String?,
    val created: Float,
    val uploaded: Float,
    @SerializedName("generated_tokens_n") val generatedTokensN: Int,
    val choices: List<Choice>?,
    @SerializedName("highlight_tokens") val highlightTokens: List<List<Float>>,
    @SerializedName("highlight_lines") val highlightLines: List<List<Float>>,
)
