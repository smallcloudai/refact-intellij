package com.smallcloud.refactai.codecompletion


data class Completion(
    val originalText: String,
    var completion: String = "",
    val multiline: Boolean,
    val offset: Int,
    val createdTs: Double = -1.0,
    val isFromCache: Boolean = false,
    var snippetTelemetryId: Int? = null
) {
    fun updateCompletion(text: String) {
        completion += text
    }
}
