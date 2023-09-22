package com.smallcloud.refactai.struct

import com.google.gson.annotations.SerializedName

data class SMCStreamingPeace(
    val choices: List<StreamingChoice>,
    val created: Float,
    val model: String,
)


data class StreamingChoice(
    val index: Int,
    @SerializedName("code_completion_delta") val delta: String,
    @SerializedName("finish_reason") val finishReason: String?,
)

