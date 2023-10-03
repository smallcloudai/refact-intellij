package com.smallcloud.refactai.modes.completion.structs


data class Completion(
    val originalText: String,
    var completion: String = "",
    val multiline: Boolean,
    val offset: Int,
    val createdTs: Long,
    val isFromCache: Boolean = false,
    var snippetTelemetryId: Int? = null
) {
    fun updateCompletion(text: String) {
        completion += text
    }
}
