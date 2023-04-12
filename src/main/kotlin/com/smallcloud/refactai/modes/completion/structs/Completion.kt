package com.smallcloud.refactai.modes.completion.structs

import dev.gitlive.difflib.DiffUtils
import dev.gitlive.difflib.patch.Patch


data class Completion(
    val originalText: String,
    val completion: String,
    val currentLinesAreEqual: Boolean,
    val multiline: Boolean,
    val startIndex: Int,
    val firstLineEndIndex: Int,
    val endIndex: Int,
    val createdTs: Long,
    val leftSymbolsToRemove: Int,
    val leftSymbolsToSkip: Int = 0,
    val isFromCache: Boolean = false
) {

    val firstLineEndOfLineIndex: Int
    init {
        val firstNewLine = originalText.substring(startIndex).indexOfFirst { it == '\n' }
        val endOfFile = originalText.length
        firstLineEndOfLineIndex = if (firstNewLine == -1)
            if (endOfFile > startIndex) endOfFile else startIndex
        else startIndex + firstNewLine
    }
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

    fun getForFirstLineEOSPatch(): Patch<Char> {
        return try {
            val originalPiece = originalText.subSequence(startIndex, firstLineEndOfLineIndex)

            DiffUtils.diff(
                    originalPiece.toList(),
                    completion.split('\n').first().toList(),
            )
        } catch (ex: Exception) {
            Patch()
        }
    }
    fun getForFirstLinePatch(): Patch<Char> {
        return try {
            val originalPiece = originalText.subSequence(startIndex, endIndex)

            DiffUtils.diff(
                    originalPiece.toList(),
                    completion.split('\n').first().toList(),
            )
        } catch (ex: Exception) {
            Patch()
        }
    }
}

data class CompletionHash(
    val text: String,
    val offset: Int,
)
