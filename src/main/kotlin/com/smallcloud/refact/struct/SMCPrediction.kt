package com.smallcloud.refact.struct

import com.google.gson.annotations.SerializedName

data class HeadMidTail(
    var head: Int,
    var mid: String,
    val tail: Int
)

data class Choice(
    val index: Int,
    val text: String?,
    val files: Map<String, String>,
    val logprobs: Any,
    @SerializedName("finish_reason") val finishReason: String,
    @SerializedName("files_head_mid_tail") val filesHeadMidTail: Map<String, HeadMidTail>?
)

data class SMCPrediction(
    val id: String,
    @SerializedName("object") val obj: String,
    val status: String?,
    val model: String?,
    val created: Float,
    val uploaded: Float,
    @SerializedName("generated_tokens_n") val generatedTokensN: Int,
    val choices: List<Choice>,
    @SerializedName("highlight_tokens") val highlightTokens: List<List<Float>>,
    @SerializedName("highlight_lines") val highlightLines: List<List<Float>>,
)
