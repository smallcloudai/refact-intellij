package com.smallcloud.codify.struct

import com.google.gson.annotations.SerializedName

data class Choice(
        val index: Int,
        val text: String,
        val files: Map<String, String>,
        val logprobs: Any,
        val finish_reason: String
)

data class SMCPrediction(
        val id: String,
        @SerializedName("object") val obj: String,
        val status: String,
        val created: Float,
        val uploaded: Float,
        val generated_tokens_n: Int,
        val choices: List<Choice>,
        val highlight_tokens: List<String>,
        val highlight_lines: List<String>,
)