package com.smallcloud.refactai.struct

import com.google.gson.annotations.SerializedName

data class SMCStreamingPeace(
    val choices: List<StreamingChoice>,
    val created: Float,
    val model: String,
    @SerializedName("snippet_telemetry_id") val snippetTelemetryId: Int? = null,
    val cached: Boolean = false
)


data class StreamingChoice(
    val index: Int,
    @SerializedName("code_completion") val delta: String,
    @SerializedName("finish_reason") val finishReason: String?,
)

