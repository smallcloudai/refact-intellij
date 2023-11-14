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

