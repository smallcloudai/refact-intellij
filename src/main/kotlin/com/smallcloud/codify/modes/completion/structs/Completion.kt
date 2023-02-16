package com.smallcloud.codify.modes.completion.structs


data class Completion(
    val originalText: String,
    val completion: String,
    val currentLinesAreEqual: Boolean,
    val multiline: Boolean,
    val startIndex: Int,
    val firstLineEndIndex: Int,
    val endIndex: Int,
    val createdTs: Long,
    val isSingleLineComplete: Boolean,
    val leftSymbolsToRemove: Int,
    val leftSymbolsToSkip: Int = 0,
    val isFromCache: Boolean = false
) {
    fun isMakeSense(): Boolean {
        return completion.isNotEmpty()
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
