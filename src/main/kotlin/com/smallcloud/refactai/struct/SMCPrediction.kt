package com.smallcloud.refactai.struct

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class SMCStreamingPeace(
    val choices: List<StreamingChoice>,
    val created: Double,
    val model: String,
    @SerializedName("snippet_telemetry_id") val snippetTelemetryId: Int? = null,
    val cached: Boolean = false,
    @Expose
    var requestId: String = ""
)


data class StreamingChoice(
    val index: Int,
    @SerializedName("code_completion") val delta: String,
    @SerializedName("finish_reason") val finishReason: String?,
)

data class HeadMidTail(
    var head: Int,
    var mid: String,
    val tail: Int
)

@Deprecated("Will be removed in next release")
data class Choice(
    val index: Int,
    val text: String?,
    val files: Map<String, String>,
    val logprobs: Any,
    @SerializedName("finish_reason") val finishReason: String,
    @SerializedName("files_head_mid_tail") val filesHeadMidTail: Map<String, HeadMidTail>?
)

@Deprecated("Will be removed in next release")
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