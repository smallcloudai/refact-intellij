package com.smallcloud.codify.modes.completion.structs


data class Completion(
    val originalText: String,
    val visualizedCompletion: String,
    val realCompletion: String,
    val currentLinesAreEqual: Boolean,
    val multiline: Boolean,
    val startIndex: Int,
    val visualizedEndIndex: Int,
    val realCompletionIndex: Int,
    val createdTs: Long,
    val isSingleLineComplete: Boolean,
    val isFromCache: Boolean = false
) {
    fun isMakeSense(): Boolean {
        return realCompletion.isNotEmpty()
    }

    fun symbolsBeforeLeftCursorReversed(): String {
        val reversedText = originalText
            .substring(0, startIndex)
            .reversed()
        val idx = reversedText.indexOfFirst { it == '\n' } + 1
        if (idx == -1) return ""
        return reversedText.substring(0, idx)
    }
}

data class CompletionHash(
    val text: String,
    val offset: Int,
)
